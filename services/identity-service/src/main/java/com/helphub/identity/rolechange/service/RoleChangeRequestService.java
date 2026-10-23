
package com.helphub.identity.rolechange.service;

import com.helphub.identity.common.exception.BusinessException;

import com.helphub.identity.role.entity.Role;
import com.helphub.identity.role.repository.RoleRepository;

import com.helphub.identity.rolechange.dto.CreateRoleChangeRequestRequest;
import com.helphub.identity.rolechange.dto.RoleChangeRequestResponse;
import com.helphub.identity.rolechange.entity.RoleChangeRequest;
import com.helphub.identity.rolechange.entity.RoleRequestStatus;
import com.helphub.identity.rolechange.repository.RoleChangeRequestRepository;

import com.helphub.identity.user.entity.AccountStatus;
import com.helphub.identity.user.entity.User;
import com.helphub.identity.user.repository.UserRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class RoleChangeRequestService {

    private static final int MAX_REASON_LENGTH = 1000;

    private static final Map<String, String> ALLOWED_UPGRADES = Map.of(
            "VOLUNTEER", "COLLABORATOR",
            "COLLABORATOR", "ADMIN");

    private final RoleChangeRequestRepository requestRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    public RoleChangeRequestService(
            RoleChangeRequestRepository requestRepository,
            UserRepository userRepository,
            RoleRepository roleRepository) {

        this.requestRepository = requestRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
    }

    @Transactional
    public RoleChangeRequestResponse createRoleChangeRequest(
            UUID authenticatedUserId,
            CreateRoleChangeRequestRequest request) {

        // 1. Validate request.
        if (request == null
                || request.requestedRole() == null
                || request.requestedRole().isBlank()) {

            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_REQUESTED_ROLE",
                    "Requested role is required");
        }

        String requestedRoleCode = request.requestedRole()
                .trim()
                .toUpperCase(Locale.ROOT);

        // 2. Normalize and validate optional reason.
        String reason = normalizeReason(request.reason());

        // 3. Lock authenticated User.
        User user = userRepository
                .findByIdForUpdate(authenticatedUserId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.UNAUTHORIZED,
                        "INVALID_SESSION",
                        "The current session is no longer valid"));

        // 4. Validate account eligibility.
        if (user.getStatus() != AccountStatus.ACTIVE
                || !user.isEmailVerified()) {

            throw new BusinessException(
                    HttpStatus.FORBIDDEN,
                    "ACCOUNT_NOT_ELIGIBLE",
                    "Account is not eligible to request a role change");
        }

        // 5. Validate current role.
        Role currentRole = user.getRole();

        if (currentRole == null || !currentRole.isActive()) {
            throw new BusinessException(
                    HttpStatus.FORBIDDEN,
                    "CURRENT_ROLE_UNAVAILABLE",
                    "Current role is not eligible for role upgrade");
        }

        String currentRoleCode = currentRole.getCode();

        // 6. Validate allowed role transition.
        String allowedNextRole = ALLOWED_UPGRADES.get(
                currentRoleCode);

        if (allowedNextRole == null
                || !allowedNextRole.equals(requestedRoleCode)) {

            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_ROLE_TRANSITION",
                    "Requested role transition is not allowed");
        }

        // 7. Ensure there is no existing PENDING request.
        boolean hasPendingRequest = requestRepository.existsByUser_IdAndStatus(
                user.getId(),
                RoleRequestStatus.PENDING);

        if (hasPendingRequest) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "PENDING_ROLE_REQUEST_EXISTS",
                    "You already have a pending role change request");
        }

        // 8. Load and validate requested Role.
        Role requestedRole = roleRepository
                .findByCode(requestedRoleCode)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.BAD_REQUEST,
                        "REQUESTED_ROLE_NOT_FOUND",
                        "Requested role is unavailable"));

        if (!requestedRole.isActive()) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "REQUESTED_ROLE_INACTIVE",
                    "Requested role is unavailable");
        }

        // 9. Create PENDING role change request.
        RoleChangeRequest roleChangeRequest = RoleChangeRequest.createPending(
                user,
                requestedRole,
                reason);

        // 10. Persist and flush to check DB constraints.
        RoleChangeRequest savedRequest = requestRepository.saveAndFlush(
                roleChangeRequest);

        // 11. Return response.
        return toResponse(savedRequest);
    }

    private String normalizeReason(String rawReason) {

        if (rawReason == null) {
            return null;
        }

        if (rawReason.length() > MAX_REASON_LENGTH) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_REQUEST_REASON",
                    "Reason must not exceed 1000 characters");
        }

        String reason = rawReason.strip();

        if (reason.isEmpty()) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_REQUEST_REASON",
                    "Reason must not be blank when provided");
        }

        return reason;
    }

    private RoleChangeRequestResponse toResponse(
            RoleChangeRequest request) {

        return new RoleChangeRequestResponse(
                request.getId(),
                request.getCurrentRole().getCode(),
                request.getRequestedRole().getCode(),
                request.getStatus(),
                request.getReason(),
                request.getReviewNote(),
                request.getCreatedAt(),
                request.getReviewedAt(),
                request.getCancelledAt());
    }
}
