package com.helphub.identity.auth.service;

import com.helphub.identity.auth.dto.RegisterRequest;
import com.helphub.identity.auth.dto.RegisterResponse;

import com.helphub.identity.common.exception.BusinessException;
import com.helphub.identity.common.util.EmailNormalizer;

import com.helphub.identity.otp.service.EmailOtpService;

import com.helphub.identity.role.entity.Role;
import com.helphub.identity.role.repository.RoleRepository;

import com.helphub.identity.user.entity.AccountStatus;
import com.helphub.identity.user.entity.User;
import com.helphub.identity.user.repository.UserRepository;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Set;

@Service
public class AuthService {

    private static final Set<String> SELF_REGISTER_ROLES = Set.of("REQUESTER", "VOLUNTEER");

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final EmailOtpService emailOtpService;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;

    public AuthService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            EmailOtpService emailOtpService,
            PasswordEncoder passwordEncoder,
            PasswordPolicy passwordPolicy) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.emailOtpService = emailOtpService;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {

        // 1. Normalize email.
        String email = EmailNormalizer.normalize(
                request.email());

        // 2. Validate password policy.
        passwordPolicy.validate(request.password());

        // 3. Validate self-registration role.
        String roleCode = request.roleCode()
                .trim()
                .toUpperCase(Locale.ROOT);

        if (!SELF_REGISTER_ROLES.contains(roleCode)) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_REGISTRATION_ROLE",
                    "Role is not allowed for self-registration");
        }

        // 4. Check duplicate email.
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "EMAIL_ALREADY_EXISTS",
                    "Email is already registered");
        }

        // 5. Load and validate Role.
        Role role = roleRepository
                .findByCode(roleCode)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.BAD_REQUEST,
                        "ROLE_NOT_FOUND",
                        "Registration role is unavailable"));

        if (!role.isActive()) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "ROLE_INACTIVE",
                    "Registration role is unavailable");
        }

        // 6. Hash password.
        String passwordHash = passwordEncoder.encode(request.password());

        // 7. Create User.
        User user = new User(
                email,
                passwordHash,
                request.fullName().trim(),
                role);

        user.setPhoneNumber(request.phoneNumber());

        // 8. Persist User and obtain its UUID.
        User savedUser = userRepository.saveAndFlush(user);

        // 9. Issue initial verification OTP.
        boolean issued = emailOtpService.issueVerificationOtp(savedUser);

        if (!issued) {
            throw new IllegalStateException(
                    "Unable to issue initial verification OTP");
        }

        // 10. Return response without tokens.
        return new RegisterResponse(
                savedUser.getId(),
                savedUser.getEmail(),
                savedUser.isEmailVerified());
    }

    @Transactional
    public void resendEmailOtp(String rawEmail) {

        String email = EmailNormalizer.normalize(rawEmail);

        // 1. Do not reveal whether email exists.
        User user = userRepository
                .findByEmail(email)
                .orElse(null);

        if (user == null) {
            return;
        }

        // 2. Lock User to serialize OTP operations.
        User lockedUser = userRepository
                .findByIdForUpdate(user.getId())
                .orElse(null);

        if (lockedUser == null) {
            return;
        }

        // 3. Check eligibility.
        if (lockedUser.getStatus() != AccountStatus.ACTIVE
                || lockedUser.isEmailVerified()) {
            return;
        }

        // 4. Respect cooldown and issuance limits.
        // The API intentionally returns the same response
        // whether issuance happens or not.
        emailOtpService.issueVerificationOtp(lockedUser);
    }

    @Transactional
    public boolean verifyEmailOtp(
            String rawEmail,
            String providedOtp) {

        String email = EmailNormalizer.normalize(rawEmail);

        User user = userRepository
                .findByEmail(email)
                .orElse(null);

        if (user == null) {
            return false;
        }

        // Lock before checking mutable security state.
        User lockedUser = userRepository
                .findByIdForUpdate(user.getId())
                .orElse(null);

        if (lockedUser == null) {
            return false;
        }

        if (lockedUser.getStatus() != AccountStatus.ACTIVE
                || lockedUser.isEmailVerified()) {
            return false;
        }

        // EmailOtpService updates the User and OTP
        // inside this same transaction.
        return emailOtpService.verifyEmailOtp(
                lockedUser,
                providedOtp);
    }
}