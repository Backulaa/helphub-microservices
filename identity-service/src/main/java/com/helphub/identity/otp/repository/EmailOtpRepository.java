package com.helphub.identity.otp.repository;

import com.helphub.identity.otp.entity.EmailOtp;
import com.helphub.identity.otp.entity.OtpPurpose;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}