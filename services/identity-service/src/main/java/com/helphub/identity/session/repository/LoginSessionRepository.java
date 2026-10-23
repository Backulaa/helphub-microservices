package com.helphub.identity.session.repository;

import com.helphub.identity.session.entity.LoginSession;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LoginSessionRepository
                extends JpaRepository<LoginSession, UUID> {

        List<LoginSession> findByUser_IdAndRevokedAtIsNull(
                        UUID userId);

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("""
                        SELECT s
                        FROM LoginSession s
                        WHERE s.id = :sessionId
                        """)
        Optional<LoginSession> findByIdForUpdate(
                        @Param("sessionId") UUID sessionId);

        @Modifying
        @Query("""
                        UPDATE LoginSession s
                        SET s.revokedAt = :now
                        WHERE s.user.id = :userId
                          AND s.revokedAt IS NULL
                        """)
        int revokeAllActiveSessions(
                        @Param("userId") UUID userId,
                        @Param("now") OffsetDateTime now);
}