package com.helphub.identity.otp.repository;

import com.helphub.identity.otp.entity.EmailOtp;
import com.helphub.identity.otp.entity.OtpPurpose;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface EmailOtpRepository
                extends JpaRepository<EmailOtp, UUID> {

        Optional<EmailOtp> findByChallengeId(
                        UUID challengeId);

        Optional<EmailOtp> findFirstByUser_IdAndPurposeAndRevokedAtIsNullAndVerifiedAtIsNullOrderByCreatedAtDesc(
                        UUID userId,
                        OtpPurpose purpose);

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("""
                        SELECT o
                        FROM EmailOtp o
                        WHERE o.challengeId = :challengeId
                        """)
        Optional<EmailOtp> findByChallengeIdForUpdate(
                        @Param("challengeId") UUID challengeId);

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        Optional<EmailOtp> findFirstByUser_IdAndPurposeOrderByCreatedAtDesc(
                        UUID userId,
                        OtpPurpose purpose);

        long countByUser_IdAndPurposeAndCreatedAtAfter(
                        UUID userId,
                        OtpPurpose purpose,
                        OffsetDateTime since);

        @Query("""
                        SELECT COALESCE(SUM(o.attemptCount), 0)
                        FROM EmailOtp o
                        WHERE o.user.id = :userId
                          AND o.purpose = :purpose
                          AND o.createdAt > :since
                        """)
        Long countFailedAttempts(
                        @Param("userId") UUID userId,
                        @Param("purpose") OtpPurpose purpose,
                        @Param("since") OffsetDateTime since);
}