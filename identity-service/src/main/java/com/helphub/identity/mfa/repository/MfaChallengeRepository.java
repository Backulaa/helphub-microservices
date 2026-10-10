package com.helphub.identity.mfa.repository;

import com.helphub.identity.mfa.entity.MfaChallenge;
import com.helphub.identity.mfa.entity.MfaChallengePurpose;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface MfaChallengeRepository
                extends JpaRepository<MfaChallenge, UUID> {

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("""
                        SELECT m
                        FROM MfaChallenge m
                        WHERE m.id = :challengeId
                        """)
        Optional<MfaChallenge> findByIdForUpdate(
                        @Param("challengeId") UUID challengeId);

        Optional<MfaChallenge> findFirstByUser_IdAndPurposeOrderByCreatedAtDesc(
                        UUID userId,
                        MfaChallengePurpose purpose);

        long countByUser_IdAndPurposeAndCreatedAtAfter(
                        UUID userId,
                        MfaChallengePurpose purpose,
                        OffsetDateTime createdAt);
}