package com.helphub.identity.otp.service;

import com.helphub.identity.infrastructure.email.PasswordResetEmailEvent;

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
public class PasswordResetOtpService {

        private static final OtpPurpose PURPOSE = OtpPurpose.PASSWORD_RESET;

        private static final int OTP_TTL_SECONDS = 300;
        private static final int RESEND_COOLDOWN_SECONDS = 60;
        private static final int MAX_ISSUES_PER_HOUR = 5;
        private static final int MAX_ATTEMPTS_PER_OTP = 5;
        private static final int MAX_FAILED_ATTEMPTS_PER_HOUR = 10;

        private final EmailOtpRepository otpRepository;
        private final OtpHasher otpHasher;
        private final ApplicationEventPublisher eventPublisher;

        private final SecureRandom random = new SecureRandom();

        public PasswordResetOtpService(
                        EmailOtpRepository otpRepository,
                        OtpHasher otpHasher,
                        ApplicationEventPublisher eventPublisher) {
                this.otpRepository = otpRepository;
                this.otpHasher = otpHasher;
                this.eventPublisher = eventPublisher;
        }

        // ==========================================
        // Issue Password Reset OTP
        // ==========================================

        @Transactional(propagation = Propagation.MANDATORY)
        public boolean issue(User user) {

                OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

                // 1. Check hourly issuance limit.
                long issuedCount = otpRepository
                                .countByUser_IdAndPurposeAndCreatedAtAfter(
                                                user.getId(),
                                                PURPOSE,
                                                now.minusHours(1));

                if (issuedCount >= MAX_ISSUES_PER_HOUR) {
                        return false;
                }

                // 2. Load latest OTP.
                Optional<EmailOtp> latest = otpRepository
                                .findFirstByUser_IdAndPurposeOrderByCreatedAtDesc(
                                                user.getId(),
                                                PURPOSE);

                // 3. Enforce 60-second cooldown.
                if (latest.isPresent()) {

                        EmailOtp previous = latest.get();

                        if (previous.getCreatedAt() != null
                                        && previous.getCreatedAt().isAfter(
                                                        now.minusSeconds(RESEND_COOLDOWN_SECONDS))) {
                                return false;
                        }
                }

                // 4. Revoke previous unconsumed reset OTPs.
                otpRepository.revokeAllUnconsumedByUserAndPurpose(
                                user.getId(),
                                PURPOSE,
                                now);

                // 5. Generate a secure six-digit OTP.
                String rawOtp = String.format(
                                Locale.ROOT,
                                "%06d",
                                random.nextInt(1_000_000));

                UUID challengeId = UUID.randomUUID();

                OffsetDateTime expiresAt = now.plusSeconds(OTP_TTL_SECONDS);

                // 6. Hash OTP with account, purpose and challenge.
                String otpHash = otpHasher.hash(
                                user.getId(),
                                PURPOSE,
                                challengeId,
                                rawOtp);

                // 7. Persist only the OTP hash.
                EmailOtp otp = new EmailOtp(
                                challengeId,
                                user,
                                otpHash,
                                PURPOSE,
                                expiresAt);

                otpRepository.save(otp);

                // 8. Send only after successful transaction commit.
                eventPublisher.publishEvent(
                                new PasswordResetEmailEvent(
                                                user.getEmail(),
                                                rawOtp,
                                                expiresAt));

                return true;
        }

        // ==========================================
        // Verify and Consume Password Reset OTP
        // ==========================================

        @Transactional(propagation = Propagation.MANDATORY)
        public boolean verifyAndConsume(
                        User user,
                        String submittedOtp) {
                OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

                // 1. Account-level attempt limit.
                long failedAttempts = otpRepository
                                .countFailedAttempts(
                                                user.getId(),
                                                PURPOSE,
                                                now.minusHours(1));

                if (failedAttempts >= MAX_FAILED_ATTEMPTS_PER_HOUR) {
                        return false;
                }

                // 2. Only the newest reset OTP can be used.
                Optional<EmailOtp> latest = otpRepository
                                .findFirstByUser_IdAndPurposeOrderByCreatedAtDesc(
                                                user.getId(),
                                                PURPOSE);

                if (latest.isEmpty()) {
                        return false;
                }

                EmailOtp otp = latest.get();

                // 3. Check current OTP state.
                if (otp.getVerifiedAt() != null
                                || otp.getRevokedAt() != null
                                || !otp.getExpiresAt().isAfter(now)
                                || otp.getAttemptCount() >= MAX_ATTEMPTS_PER_OTP) {
                        return false;
                }

                // 4. Verify HMAC.
                boolean matched = otpHasher.matches(
                                otp.getOtpHash(),
                                user.getId(),
                                PURPOSE,
                                otp.getChallengeId(),
                                submittedOtp);

                // 5. Record failed attempt without throwing.
                if (!matched) {

                        int attempts = otp.getAttemptCount() + 1;

                        otp.setAttemptCount(attempts);

                        if (attempts >= MAX_ATTEMPTS_PER_OTP) {
                                otp.setRevokedAt(now);
                        }

                        return false;
                }

                // 6. Consume OTP exactly once.
                otp.setVerifiedAt(now);

                return true;
        }

        // ==========================================
        // Revoke Remaining Reset OTPs
        // ==========================================

        @Transactional(propagation = Propagation.MANDATORY)
        public void revokeRemaining(User user) {

                otpRepository.revokeAllUnconsumedByUserAndPurpose(
                                user.getId(),
                                PURPOSE,
                                OffsetDateTime.now(ZoneOffset.UTC));
        }
}