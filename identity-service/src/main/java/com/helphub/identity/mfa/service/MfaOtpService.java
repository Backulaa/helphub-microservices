package com.helphub.identity.mfa.service;

import com.helphub.identity.common.exception.BusinessException;

import com.helphub.identity.infrastructure.email.OtpEmailEvent;

import com.helphub.identity.mfa.entity.MfaChallenge;
import com.helphub.identity.mfa.entity.MfaChallengePurpose;
import com.helphub.identity.mfa.repository.MfaChallengeRepository;

import com.helphub.identity.otp.entity.EmailOtp;
import com.helphub.identity.otp.entity.OtpPurpose;
import com.helphub.identity.otp.repository.EmailOtpRepository;
import com.helphub.identity.otp.service.OtpHasher;

import com.helphub.identity.session.entity.LoginSession;
import com.helphub.identity.user.entity.User;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Optional;

@Service
public class MfaOtpService {

    private static final int OTP_TTL_SECONDS = 300;
    private static final int MAX_ATTEMPTS = 5;
    private static final int MAX_ISSUES_PER_HOUR = 5;
    private static final int RESEND_COOLDOWN_SECONDS = 60;

    private final MfaChallengeRepository challengeRepository;
    private final EmailOtpRepository emailOtpRepository;
    private final OtpHasher otpHasher;
    private final ApplicationEventPublisher eventPublisher;

    private final SecureRandom random = new SecureRandom();

    public MfaOtpService(
            MfaChallengeRepository challengeRepository,
            EmailOtpRepository emailOtpRepository,
            OtpHasher otpHasher,
            ApplicationEventPublisher eventPublisher) {
        this.challengeRepository = challengeRepository;
        this.emailOtpRepository = emailOtpRepository;
        this.otpHasher = otpHasher;
        this.eventPublisher = eventPublisher;
    }

    // ==========================================
    // Issue MFA Challenge + Email OTP
    // ==========================================

    @Transactional(propagation = Propagation.MANDATORY)
    public MfaChallenge issue(
            User user,
            LoginSession session,
            MfaChallengePurpose purpose) {

        // ==========================================
        // 1. Validate input and challenge context
        // ==========================================

        if (user == null || purpose == null) {
            throw new IllegalArgumentException(
                    "User and MFA challenge purpose are required");
        }

        // LOGIN challenge must not have an authenticated session.
        // SETUP / DISABLE challenges must belong to an existing session.
        if (purpose == MfaChallengePurpose.LOGIN) {

            if (session != null) {
                throw new IllegalArgumentException(
                        "MFA LOGIN challenge must not have a login session");
            }

        } else {

            if (session == null) {
                throw new IllegalArgumentException(
                        "MFA SETUP/DISABLE requires an authenticated session");
            }

            if (!session.getUser().getId().equals(user.getId())) {
                throw new IllegalArgumentException(
                        "Login session does not belong to the user");
            }
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        // ==========================================
        // 2. Check hourly issuance limit
        // ==========================================

        long issuedLastHour = challengeRepository
                .countByUser_IdAndPurposeAndCreatedAtAfter(
                        user.getId(),
                        purpose,
                        now.minusHours(1));

        if (issuedLastHour >= MAX_ISSUES_PER_HOUR) {
            throw new BusinessException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "MFA_RATE_LIMITED",
                    "Too many MFA verification requests. Please try again later.");
        }

        // ==========================================
        // 3. Retrieve the latest MFA challenge
        // ==========================================

        Optional<MfaChallenge> latestChallenge = challengeRepository
                .findFirstByUser_IdAndPurposeOrderByCreatedAtDesc(
                        user.getId(),
                        purpose);

        // ==========================================
        // 4. Enforce resend cooldown
        // ==========================================

        if (latestChallenge.isPresent()) {

            MfaChallenge previous = latestChallenge.get();

            OffsetDateTime lastIssuedAt = previous.getCreatedAt();

            if (lastIssuedAt != null
                    && lastIssuedAt.isAfter(
                            now.minusSeconds(RESEND_COOLDOWN_SECONDS))) {

                throw new BusinessException(
                        HttpStatus.TOO_MANY_REQUESTS,
                        "MFA_COOLDOWN",
                        "Please wait before requesting another verification code.");
            }
        }

        // ==========================================
        // 5. Revoke previous active challenge and OTP
        // ==========================================

        latestChallenge.ifPresent(previous -> {

            // A completed or revoked challenge cannot be active.
            if (previous.getCompletedAt() != null
                    || previous.getRevokedAt() != null) {
                return;
            }

            // Revoke the previous challenge.
            previous.setRevokedAt(now);

            // Revoke the OTP associated with the previous challenge.
            emailOtpRepository
                    .findByChallengeIdForUpdate(previous.getId())
                    .ifPresent(oldOtp -> {

                        if (oldOtp.getVerifiedAt() == null
                                && oldOtp.getRevokedAt() == null) {

                            oldOtp.setRevokedAt(now);
                        }
                    });
        });

        // ==========================================
        // 6. Calculate OTP expiration
        // ==========================================

        OffsetDateTime expiresAt = now.plusSeconds(OTP_TTL_SECONDS);

        // ==========================================
        // 7. Create MFA challenge
        // ==========================================

        MfaChallenge challenge = new MfaChallenge(
                user,
                session,
                purpose,
                expiresAt);

        // Flush to ensure ID and audit fields are available.
        challenge = challengeRepository.saveAndFlush(challenge);

        // ==========================================
        // 8. Generate secure 6-digit OTP
        // ==========================================

        String otp = String.format(
                Locale.ROOT,
                "%06d",
                random.nextInt(1_000_000));

        // ==========================================
        // 9. Map MFA purpose to OTP purpose
        // ==========================================

        OtpPurpose otpPurpose = mapPurpose(purpose);

        // ==========================================
        // 10. Hash OTP using HMAC-SHA256
        // ==========================================

        String otpHash = otpHasher.hash(
                user.getId(),
                otpPurpose,
                challenge.getId(),
                otp);

        // ==========================================
        // 11. Persist Email OTP
        // ==========================================

        EmailOtp emailOtp = new EmailOtp(
                challenge.getId(),
                user,
                otpHash,
                otpPurpose,
                expiresAt);

        emailOtpRepository.save(emailOtp);

        // ==========================================
        // 12. Publish email event
        // ==========================================

        // OtpEmailListener handles this event AFTER_COMMIT.
        // Do not log or persist the raw OTP.
        eventPublisher.publishEvent(
                new OtpEmailEvent(
                        user.getEmail(),
                        otp,
                        expiresAt));

        // ==========================================
        // 13. Return challenge metadata
        // ==========================================

        return challenge;
    }

    // ==========================================
    // Consume OTP exactly once
    // ==========================================

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean verify(
            MfaChallenge challenge,
            String submittedOtp) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        if (challenge.getCompletedAt() != null
                || challenge.getRevokedAt() != null
                || !challenge.getExpiresAt().isAfter(now)
                || challenge.getAttemptCount() >= MAX_ATTEMPTS) {
            return false;
        }

        EmailOtp emailOtp = emailOtpRepository
                .findByChallengeIdForUpdate(challenge.getId())
                .orElse(null);

        if (emailOtp == null
                || emailOtp.getVerifiedAt() != null
                || emailOtp.getRevokedAt() != null
                || !emailOtp.getExpiresAt().isAfter(now)) {
            return false;
        }

        boolean valid = otpHasher.matches(
                emailOtp.getOtpHash(),
                challenge.getUser().getId(),
                mapPurpose(challenge.getPurpose()),
                challenge.getId(),
                submittedOtp);

        if (!valid) {

            int attempts = challenge.getAttemptCount() + 1;

            challenge.setAttemptCount(attempts);
            emailOtp.setAttemptCount(
                    emailOtp.getAttemptCount() + 1);

            if (attempts >= MAX_ATTEMPTS) {
                challenge.setRevokedAt(now);
                emailOtp.setRevokedAt(now);
            }

            // Return false instead of throwing:
            // the failed-attempt counters must commit.
            return false;
        }

        challenge.setCompletedAt(now);
        emailOtp.setVerifiedAt(now);

        return true;
    }

    public long getOtpExpiresInSeconds() {
        return OTP_TTL_SECONDS;
    }

    private OtpPurpose mapPurpose(
            MfaChallengePurpose purpose) {
        return switch (purpose) {
            case LOGIN -> OtpPurpose.MFA_LOGIN;
            case SETUP -> OtpPurpose.MFA_SETUP;
            case DISABLE -> OtpPurpose.MFA_DISABLE;
        };
    }
}