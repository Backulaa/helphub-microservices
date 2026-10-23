package com.helphub.identity.rolechange.repository;

import com.helphub.identity.rolechange.entity.RoleChangeRequest;
import com.helphub.identity.rolechange.entity.RoleRequestStatus;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface RoleChangeRequestRepository
                extends JpaRepository<RoleChangeRequest, UUID> {

        boolean existsByUser_IdAndStatus(
                        UUID userId,
                        RoleRequestStatus status);

        Page<RoleChangeRequest> findByUser_Id(
                        UUID userId,
                        Pageable pageable);

        Page<RoleChangeRequest> findByStatus(
                        RoleRequestStatus status,
                        Pageable pageable);

        Page<RoleChangeRequest> findByUser_IdAndStatus(
                        UUID userId,
                        RoleRequestStatus status,
                        Pageable pageable);

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("""
                        SELECT r
                        FROM RoleChangeRequest r
                        WHERE r.id = :requestId
                          AND r.user.id = :userId
                        """)
        Optional<RoleChangeRequest> findOwnedRequestForUpdate(
                        @Param("requestId") UUID requestId,
                        @Param("userId") UUID userId);

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("""
                        SELECT r
                        FROM RoleChangeRequest r
                        WHERE r.id = :requestId
                        """)
        Optional<RoleChangeRequest> findByIdForUpdate(
                        @Param("requestId") UUID requestId);
}