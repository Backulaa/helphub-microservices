package com.helphub.identity.mfa.repository;

import com.helphub.identity.mfa.entity.MfaChallenge;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}