package com.helphub.identity.user.repository;

import com.helphub.identity.user.entity.AccountStatus;
import com.helphub.identity.user.entity.User;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository
                extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

        Optional<User> findByEmail(String email);

        boolean existsByEmail(String email);

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("""
                        SELECT u
                        FROM User u
                        WHERE u.id = :userId
                        """)
        Optional<User> findByIdForUpdate(
                        @Param("userId") UUID userId);

        @Override
        @EntityGraph(attributePaths = "role")
        Page<User> findAll(
                        Specification<User> spec,
                        Pageable pageable);

        @Query("""
                        SELECT COUNT(u)
                        FROM User u
                        WHERE u.role.code = 'ADMIN'
                          AND u.role.active = true
                          AND u.status = :status
                          AND u.emailVerified = true
                        """)
        long countEligibleAdmins(
                        @Param("status") AccountStatus status);
}