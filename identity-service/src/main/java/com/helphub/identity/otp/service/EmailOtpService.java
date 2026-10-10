package com.helphub.identity.otp.service;

import com.helphub.identity.infrastructure.email.OtpEmailEvent;
import com.helphub.identity.otp.entity.EmailOtp;
import com.helphub.identity.otp.entity.OtpPurpose;
import com.helphub.identity.otp.repository.EmailOtpRepository;
import com.helphub.identity.user.entity.User;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class EmailOtpService {

    private static final int OTP_EXPIRY_MINUTES = 5;
    private static final int RESEND_COOLDOWN_SECONDS = 60;
    private static final int MAX_SENDS_PER_HOUR = 5;
    private static final int MAX_ATTEMPTS_PER_OTP = 5;
    private static final int MAX_ATTEMPTS_PER_HOUR = 10;

    private final EmailOtpRepository otpRepository;
    private final OtpHasher otpHasher;
    private final ApplicationEventPublisher eventPublisher;

    private final SecureRandom secureRandom = new SecureRandom();

    public EmailOtpService(
            EmailOtpRepository otpRepository,
            OtpHasher otpHasher,
            ApplicationEventPublisher eventPublisher) {
        this.otpRepository = otpRepository;
        this.otpHasher = otpHasher;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean issueVerificationOtp(User user) {

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        OtpPurpose purpose = OtpPurpose.EMAIL_VERIFICATION;

        UUID userId = user.getId();

        // 1. Check total OTP issuance within one hour.
        long sentCount = otpRepository.countByUser_IdAndPurposeAndCreatedAtAfter(
                userId,
                purpose,
                now.minusHours(1));

        if (sentCount >= MAX_SENDS_PER_HOUR) {
            return false;
        }

        // 2. Load the latest OTP.
        Optional<EmailOtp> latest = otpRepository
                .findFirstByUser_IdAndPurposeOrderByCreatedAtDesc(
                        userId,
                        purpose);

        // 3. Enforce resend cooldown.
        if (latest.isPresent()) {

            EmailOtp previous = latest.get();

            if (previous.getCreatedAt().isAfter(
                    now.minusSeconds(
                            RESEND_COOLDOWN_SECONDS))) {
                return false;
            }

            // 4. Invalidate the previous unused OTP.
            if (previous.getVerifiedAt() == null
                    && previous.getRevokedAt() == null) {

                previous.setRevokedAt(now);
            }
        }

        // 5. Generate a cryptographically random OTP.
        String otp = String.format(
                Locale.ROOT,
                "%06d",
                secureRandom.nextInt(1_000_000));

        UUID challengeId = UUID.randomUUID();

        // 6. Hash OTP using HMAC-SHA256.
        String otpHash = otpHasher.hash(
                userId,
                purpose,
                challengeId,
                otp);

        // 7. Persist OTP.
        EmailOtp newOtp = new EmailOtp(
                challengeId,
                user,
                otpHash,
                purpose,
                now.plusMinutes(OTP_EXPIRY_MINUTES));

        otpRepository.save(newOtp);

        // 8. Request email delivery after transaction commit.
        eventPublisher.publishEvent(
                new OtpEmailEvent(
                        user.getEmail(),
                        otp,
                        newOtp.getExpiresAt()));

        return true;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean verifyEmailOtp(
            User user,
            String providedOtp) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        UUID userId = user.getId();

        OtpPurpose purpose = OtpPurpose.EMAIL_VERIFICATION;

        // 1. Load the most recently issued OTP.
        Optional<EmailOtp> optionalOtp = otpRepository
                .findFirstByUser_IdAndPurposeOrderByCreatedAtDesc(
                        userId,
                        purpose);

        if (optionalOtp.isEmpty()) {
            return false;
        }

        EmailOtp otp = optionalOtp.get();

        // 2. Validate state and expiry.
        if (otp.getVerifiedAt() != null
                || otp.getRevokedAt() != null
                || !otp.getExpiresAt().isAfter(now)
                || otp.getAttemptCount() >= MAX_ATTEMPTS_PER_OTP) {

            return false;
        }

        // 3. Account-wide failed-attempt limit.
        Long totalAttempts = otpRepository.countFailedAttempts(
                userId,
                purpose,
                now.minusHours(1));

        if (totalAttempts >= MAX_ATTEMPTS_PER_HOUR) {
            return false;
        }

        // 4. Verify OTP in constant time.
        boolean matches = otpHasher.matches(
                otp.getOtpHash(),
                userId,
                purpose,
                otp.getChallengeId(),
                providedOtp);

        if (!matches) {

            int attempts = otp.getAttemptCount() + 1;

            otp.setAttemptCount(attempts);

            if (attempts >= MAX_ATTEMPTS_PER_OTP) {
                otp.setRevokedAt(now);
            }

            // Returning false allows the transaction
            // to commit the failed attempt counter.
            return false;
        }

        // 5. Consume OTP.
        otp.setVerifiedAt(now);

        // 6. Verify email ownership.
        user.setEmailVerified(true);
        user.setEmailVerifiedAt(now);

        return true;
    }
}