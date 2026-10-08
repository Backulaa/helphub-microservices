package com.helphub.identity.rolechange.repository;

import com.helphub.identity.rolechange.entity.RoleChangeRequest;
import com.helphub.identity.rolechange.entity.RoleRequestStatus;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

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
}