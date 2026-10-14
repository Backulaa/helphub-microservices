package com.helphub.identity.user.service;

import com.helphub.identity.common.exception.BusinessException;

import com.helphub.identity.role.entity.Role;
import com.helphub.identity.role.repository.RoleRepository;

import com.helphub.identity.session.repository.LoginSessionRepository;
import com.helphub.identity.session.repository.RefreshTokenRepository;

import com.helphub.identity.user.dto.*;
import com.helphub.identity.user.entity.*;
import com.helphub.identity.user.repository.UserRepository;

import jakarta.persistence.criteria.Predicate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;

import java.util.*;

@Service
public class UserService {

    private static final Set<String> PROFILE_FIELDS = Set.of(
            "fullName",
            "phoneNumber",
            "dateOfBirth",
            "gender");

    // Keep the same phone format as RegisterRequest.
    private static final String PHONE_REGEX = "^\\+?[0-9]{9,15}$";

    private static final Map<String, Set<String>> ALLOWED_ROLE_CHANGES = Map.of(

            "VOLUNTEER",
            Set.of("COLLABORATOR", "ADMIN"),

            "COLLABORATOR",
            Set.of("VOLUNTEER", "ADMIN"),

            "ADMIN",
            Set.of("COLLABORATOR", "VOLUNTEER"));

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    private final LoginSessionRepository sessionRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    public UserService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            LoginSessionRepository sessionRepository,
            RefreshTokenRepository refreshTokenRepository) {

        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.sessionRepository = sessionRepository;
        this.refreshTokenRepository = refreshTokenRepository;
    }

    // ==================================================
    // 1. GET MY PROFILE
    // ==================================================

    @Transactional(readOnly = true)
    public MyProfileResponse getMyProfile(Jwt jwt) {

        User user = requireCurrentUser(jwt);

        return toMyProfile(user);
    }

    // ==================================================
    // 2. UPDATE MY PROFILE
    // ==================================================

    @Transactional
    public MyProfileResponse updateMyProfile(
            Jwt jwt,
            Map<String, Object> patch) {

        User user = requireCurrentUser(jwt);

        if (patch == null) {
            throw badRequest(
                    "INVALID_REQUEST_BODY",
                    "Request body is required");
        }

        // Reject unknown or protected fields.
        for (String field : patch.keySet()) {
            if (!PROFILE_FIELDS.contains(field)) {
                throw badRequest(
                        "INVALID_PROFILE_FIELD",
                        "Field is not allowed: " + field);
            }
        }

        // Validate all fields before modifying the entity.
        String fullName = user.getFullName();
        String phoneNumber = user.getPhoneNumber();
        LocalDate dateOfBirth = user.getDateOfBirth();
        Gender gender = user.getGender();

        if (patch.containsKey("fullName")) {

            Object value = patch.get("fullName");

            if (!(value instanceof String name)) {
                throw badRequest(
                        "INVALID_FULL_NAME",
                        "Full name must be a string");
            }

            fullName = name.trim();

            if (fullName.isEmpty()
                    || fullName.length() > 50) {

                throw badRequest(
                        "INVALID_FULL_NAME",
                        "Full name must contain 1 to 50 characters");
            }
        }

        if (patch.containsKey("phoneNumber")) {

            Object value = patch.get("phoneNumber");

            if (value == null) {
                phoneNumber = null;

            } else if (value instanceof String phone) {

                phoneNumber = phone.trim();

                if (!phoneNumber.matches(PHONE_REGEX)
                        || phoneNumber.length() > 20) {

                    throw badRequest(
                            "INVALID_PHONE_NUMBER",
                            "Phone number has an invalid format");
                }

            } else {
                throw badRequest(
                        "INVALID_PHONE_NUMBER",
                        "Phone number must be a string or null");
            }
        }

        if (patch.containsKey("dateOfBirth")) {

            Object value = patch.get("dateOfBirth");

            if (value == null) {
                dateOfBirth = null;

            } else if (value instanceof String date) {

                // Require ISO yyyy-MM-dd.
                if (!date.matches("\\d{4}-\\d{2}-\\d{2}")) {
                    throw badRequest(
                            "INVALID_DATE_OF_BIRTH",
                            "Date of birth must use yyyy-MM-dd");
                }

                try {
                    dateOfBirth = LocalDate.parse(date);
                } catch (DateTimeParseException ex) {
                    throw badRequest(
                            "INVALID_DATE_OF_BIRTH",
                            "Date of birth is invalid");
                }

                if (dateOfBirth.isAfter(LocalDate.now(ZoneOffset.UTC))) {
                    throw badRequest(
                            "INVALID_DATE_OF_BIRTH",
                            "Date of birth cannot be in the future");
                }

            } else {
                throw badRequest(
                        "INVALID_DATE_OF_BIRTH",
                        "Date of birth must be a string or null");
            }
        }

        if (patch.containsKey("gender")) {

            Object value = patch.get("gender");

            if (value == null) {
                gender = null;

            } else if (value instanceof String text) {

                try {
                    gender = Gender.valueOf(text);
                } catch (IllegalArgumentException ex) {
                    throw badRequest(
                            "INVALID_GENDER",
                            "Unsupported gender value");
                }

            } else {
                throw badRequest(
                        "INVALID_GENDER",
                        "Gender must be a string or null");
            }
        }

        // Compare normalized values.
        boolean changed = !Objects.equals(user.getFullName(), fullName)
                || !Objects.equals(
                        user.getPhoneNumber(), phoneNumber)
                || !Objects.equals(
                        user.getDateOfBirth(), dateOfBirth)
                || user.getGender() != gender;

        // No-op: no UPDATE, no version increment.
        if (!changed) {
            return toMyProfile(user);
        }

        user.setFullName(fullName);
        user.setPhoneNumber(phoneNumber);
        user.setDateOfBirth(dateOfBirth);
        user.setGender(gender);

        // @Version detects concurrent updates.
        userRepository.saveAndFlush(user);

        // securityVersion remains unchanged.
        return toMyProfile(user);
    }

    // ==================================================
    // 3. GET PUBLIC USER PROFILE
    // ==================================================

    @Transactional(readOnly = true)
    public PublicUserProfileResponse getPublicUserProfile(
            Jwt jwt,
            UUID targetUserId) {

        User target = userRepository.findById(targetUserId)
                .orElseThrow(UserService::userNotFound);

        if (target.getStatus() != AccountStatus.ACTIVE
                || !target.isEmailVerified()) {

            // Avoid disclosing the exact account status.
            throw userNotFound();
        }

        return toPublicProfile(target);
    }

    // ==================================================
    // 4. GET USER BY ID - ADMIN
    // ==================================================

    @Transactional(readOnly = true)
    public AdminUserResponse getUserById(
            Jwt jwt,
            UUID targetUserId) {

        requireAdmin(jwt);

        User target = userRepository.findById(targetUserId)
                .orElseThrow(UserService::userNotFound);

        return toAdminProfile(target);
    }

    // ==================================================
    // 5. SEARCH USERS - ADMIN
    // ==================================================

    @Transactional(readOnly = true)
    public UserPageResponse searchUsers(
            Jwt jwt,
            String keyword,
            String roleCode,
            AccountStatus status,
            Boolean emailVerified,
            Gender gender,
            AgeGroup ageGroup,
            int page,
            int size,
            String sort) {

        requireAdmin(jwt);

        // Pagination validation.
        if (page < 0 || size < 1 || size > 50) {
            throw badRequest(
                    "INVALID_PAGINATION",
                    "Page must be >= 0 and size must be 1 to 50");
        }

        // Allow only createdAt ASC/DESC.
        String normalizedSort = sort == null
                ? "createdAt,desc"
                : sort.trim();

        if (!normalizedSort.equals("createdAt,asc")
                && !normalizedSort.equals("createdAt,desc")) {

            throw badRequest(
                    "INVALID_SORT",
                    "Only createdAt,asc or createdAt,desc is supported");
        }

        String normalizedKeyword = keyword == null ? null : keyword.trim();

        if (normalizedKeyword != null
                && normalizedKeyword.length() > 100) {

            throw badRequest(
                    "INVALID_KEYWORD",
                    "Keyword must not exceed 100 characters");
        }

        if (normalizedKeyword != null
                && normalizedKeyword.isEmpty()) {

            normalizedKeyword = null;
        }

        if (roleCode != null) {

            if (!Set.of(
                    "REQUESTER",
                    "VOLUNTEER",
                    "COLLABORATOR",
                    "ADMIN").contains(roleCode)) {

                throw badRequest(
                        "INVALID_ROLE",
                        "Unsupported role code");
            }
        }

        // Effective-final values for Specification.
        final String searchKeyword = normalizedKeyword;
        final String filterRole = roleCode;

        Specification<User> specification = (root, query, cb) -> {

            List<Predicate> predicates = new ArrayList<>();

            // Search full name, email and exact UUID.
            if (searchKeyword != null) {

                String escaped = searchKeyword
                        .toLowerCase(Locale.ROOT)
                        .replace("\\", "\\\\")
                        .replace("%", "\\%")
                        .replace("_", "\\_");

                String pattern = "%" + escaped + "%";

                Predicate namePredicate = cb.like(
                        cb.lower(root.get("fullName")),
                        pattern,
                        '\\');

                Predicate emailPredicate = cb.like(
                        cb.lower(root.get("email")),
                        pattern,
                        '\\');

                List<Predicate> keywordPredicates = new ArrayList<>();

                keywordPredicates.add(namePredicate);
                keywordPredicates.add(emailPredicate);

                try {
                    UUID searchedId = UUID.fromString(searchKeyword);

                    keywordPredicates.add(
                            cb.equal(
                                    root.get("id"),
                                    searchedId));

                } catch (IllegalArgumentException ignored) {
                    // Keyword is not a UUID.
                }

                predicates.add(cb.or(
                        keywordPredicates.toArray(
                                new Predicate[0])));
            }

            if (filterRole != null) {
                predicates.add(cb.equal(
                        root.get("role").get("code"),
                        filterRole));
            }

            if (status != null) {
                predicates.add(cb.equal(
                        root.get("status"),
                        status));
            }

            if (emailVerified != null) {
                predicates.add(cb.equal(
                        root.get("emailVerified"),
                        emailVerified));
            }

            if (gender != null) {
                predicates.add(cb.equal(
                        root.get("gender"),
                        gender));
            }

            if (ageGroup != null) {

                LocalDate today = LocalDate.now(ZoneOffset.UTC);

                LocalDate latestBirthDate = today.minusYears(
                        ageGroup.getMinAge());

                predicates.add(
                        cb.lessThanOrEqualTo(
                                root.get("dateOfBirth"),
                                latestBirthDate));

                if (ageGroup.getMaxAge() != null) {

                    LocalDate earliestBirthDate = today.minusYears(
                            ageGroup.getMaxAge() + 1L)
                            .plusDays(1);

                    predicates.add(
                            cb.greaterThanOrEqualTo(
                                    root.get("dateOfBirth"),
                                    earliestBirthDate));
                }
            }

            return cb.and(
                    predicates.toArray(new Predicate[0]));
        };

        boolean ascending = normalizedSort.endsWith(",asc");

        Sort.Direction direction = ascending
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;

        // Stable ordering using ID as a tie-breaker.
        Sort sorting = Sort.by(
                new Sort.Order(direction, "createdAt"),
                new Sort.Order(Sort.Direction.DESC, "id"));

        PageRequest pageable = PageRequest.of(page, size, sorting);

        Page<User> result = userRepository.findAll(specification, pageable);

        List<UserListItemResponse> content = result.getContent()
                .stream()
                .map(this::toUserListItem)
                .toList();

        return new UserPageResponse(
                content,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                normalizedSort);
    }

    // ==================================================
    // 6. CHANGE USER ROLE - ADMIN
    // ==================================================

    @Transactional
    public AdminUserResponse changeUserRole(
            Jwt jwt,
            UUID targetUserId,
            ChangeUserRoleRequest request) {

        // Serialize sensitive ADMIN changes first.
        lockAdminRole();

        User actor = requireAdmin(jwt);

        if (actor.getId().equals(targetUserId)) {
            throw forbidden(
                    "ADMIN_SELF_MODIFICATION",
                    "Admin cannot change their own role");
        }

        User target = userRepository
                .findByIdForUpdate(targetUserId)
                .orElseThrow(UserService::userNotFound);

        if (target.getStatus() != AccountStatus.ACTIVE
                || !target.isEmailVerified()) {

            throw conflict(
                    "TARGET_NOT_ELIGIBLE",
                    "Target account must be active and email verified");
        }

        String newRoleCode = request.roleCode();

        Role newRole = roleRepository
                .findByCode(newRoleCode)
                .orElseThrow(() -> badRequest(
                        "INVALID_ROLE",
                        "Role does not exist"));

        if (!newRole.isActive()) {
            throw conflict(
                    "ROLE_INACTIVE",
                    "Target role is inactive");
        }

        String currentRoleCode = target.getRole().getCode();

        // No-op.
        if (currentRoleCode.equals(newRoleCode)) {
            return toAdminProfile(target);
        }

        // Validate role transition matrix.
        Set<String> allowed = ALLOWED_ROLE_CHANGES.getOrDefault(
                currentRoleCode,
                Set.of());

        if (!allowed.contains(newRoleCode)) {
            throw conflict(
                    "ROLE_TRANSITION_NOT_ALLOWED",
                    "Role transition is not allowed");
        }

        // Prevent removing the final eligible ADMIN.
        if (currentRoleCode.equals("ADMIN")) {
            assertMoreThanOneAdmin();
        }

        target.setRole(newRole);

        // Invalidate all older access tokens.
        target.setSecurityVersion(
                target.getSecurityVersion() + 1);

        // Flush User update within the transaction.
        userRepository.saveAndFlush(target);

        revokeAllSessions(target.getId());

        // The transaction commits after method execution.
        return toAdminProfile(target);
    }

    // ==================================================
    // 7. CHANGE USER STATUS - ADMIN
    // ==================================================

    @Transactional
    public AdminUserResponse changeUserStatus(
            Jwt jwt,
            UUID targetUserId,
            ChangeUserStatusRequest request) {

        lockAdminRole();

        User actor = requireAdmin(jwt);

        if (actor.getId().equals(targetUserId)) {
            throw forbidden(
                    "ADMIN_SELF_MODIFICATION",
                    "Admin cannot change their own status");
        }

        User target = userRepository
                .findByIdForUpdate(targetUserId)
                .orElseThrow(UserService::userNotFound);

        AccountStatus currentStatus = target.getStatus();

        AccountStatus newStatus = request.status();

        // No-op.
        if (currentStatus == newStatus) {
            return toAdminProfile(target);
        }

        // DISABLED -> SUSPENDED is forbidden.
        if (currentStatus == AccountStatus.DISABLED
                && newStatus == AccountStatus.SUSPENDED) {

            throw conflict(
                    "STATUS_TRANSITION_NOT_ALLOWED",
                    "Disabled account cannot transition to suspended");
        }

        // Protect final ADMIN when removing eligibility.
        if (target.getRole().getCode().equals("ADMIN")
                && currentStatus == AccountStatus.ACTIVE
                && newStatus != AccountStatus.ACTIVE
                && target.isEmailVerified()) {

            assertMoreThanOneAdmin();
        }

        target.setStatus(newStatus);

        // All actual transitions invalidate old tokens,
        // including reactivation.
        target.setSecurityVersion(
                target.getSecurityVersion() + 1);

        userRepository.saveAndFlush(target);

        revokeAllSessions(target.getId());

        // Email verification, role, password and MFA
        // remain unchanged.
        return toAdminProfile(target);
    }

    // ==================================================
    // SECURITY HELPERS
    // ==================================================

    private User requireCurrentUser(Jwt jwt) {

        if (jwt == null) {
            throw unauthorized();
        }

        UUID userId;

        try {
            userId = UUID.fromString(jwt.getSubject());
        } catch (RuntimeException ex) {
            throw unauthorized();
        }

        User user = userRepository.findById(userId)
                .orElseThrow(UserService::unauthorized);

        if (user.getStatus() != AccountStatus.ACTIVE
                || !user.isEmailVerified()) {

            throw unauthorized();
        }

        Object claim = jwt.getClaim("sv");

        if (!(claim instanceof Number version)
                || user.getSecurityVersion() != version.longValue()) {

            throw unauthorized();
        }

        return user;
    }

    private User requireAdmin(Jwt jwt) {

        User actor = requireCurrentUser(jwt);

        if (!"ADMIN".equals(actor.getRole().getCode())
                || !actor.getRole().isActive()) {

            throw forbidden(
                    "ACCESS_DENIED",
                    "Current ADMIN permission is required");
        }

        return actor;
    }

    private void lockAdminRole() {

        roleRepository.lockAdminRole()
                .orElseThrow(() -> conflict(
                        "ADMIN_ROLE_NOT_CONFIGURED",
                        "ADMIN role is not configured"));
    }

    private void assertMoreThanOneAdmin() {

        long eligibleAdmins = userRepository.countEligibleAdmins(
                AccountStatus.ACTIVE);

        if (eligibleAdmins <= 1) {
            throw conflict(
                    "LAST_ADMIN_PROTECTED",
                    "Cannot remove the last eligible administrator");
        }
    }

    private void revokeAllSessions(UUID userId) {

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        refreshTokenRepository
                .revokeAllActiveTokensByUser(
                        userId,
                        now);

        sessionRepository
                .revokeAllActiveSessions(
                        userId,
                        now);
    }

    // ==================================================
    // RESPONSE MAPPERS
    // ==================================================

    private MyProfileResponse toMyProfile(User user) {

        return new MyProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getPhoneNumber(),
                user.getDateOfBirth(),
                user.getGender(),
                user.getAvatarMediaId(),
                user.getRole().getCode(),
                user.getStatus(),
                user.isEmailVerified(),
                user.isMfaEnabled(),
                user.getMfaMethod(),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                user.getLastLoginAt());
    }

    private PublicUserProfileResponse toPublicProfile(
            User user) {

        return new PublicUserProfileResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getPhoneNumber(),
                user.getDateOfBirth(),
                user.getGender(),
                user.getAvatarMediaId(),
                user.getRole().getCode(),
                user.getCreatedAt(),
                user.getLastLoginAt());
    }

    private AdminUserResponse toAdminProfile(User user) {

        return new AdminUserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getPhoneNumber(),
                user.getDateOfBirth(),
                user.getGender(),
                user.getAvatarMediaId(),
                user.getRole().getCode(),
                user.getStatus(),
                user.isEmailVerified(),
                user.isMfaEnabled(),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                user.getLastLoginAt());
    }

    private UserListItemResponse toUserListItem(User user) {

        return new UserListItemResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getRole().getCode(),
                user.getStatus(),
                user.isEmailVerified(),
                user.getCreatedAt());
    }

    // ==================================================
    // ERROR HELPERS
    // ==================================================

    private static BusinessException badRequest(
            String code,
            String message) {

        return new BusinessException(
                HttpStatus.BAD_REQUEST,
                code,
                message);
    }

    private static BusinessException unauthorized() {

        return new BusinessException(
                HttpStatus.UNAUTHORIZED,
                "UNAUTHORIZED",
                "Authentication is required or token is invalid");
    }

    private static BusinessException forbidden(
            String code,
            String message) {

        return new BusinessException(
                HttpStatus.FORBIDDEN,
                code,
                message);
    }

    private static BusinessException conflict(
            String code,
            String message) {

        return new BusinessException(
                HttpStatus.CONFLICT,
                code,
                message);
    }

    private static BusinessException userNotFound() {

        return new BusinessException(
                HttpStatus.NOT_FOUND,
                "USER_NOT_FOUND",
                "User not found");
    }
}