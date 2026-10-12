package com.helphub.identity.auth.service;

import com.helphub.identity.auth.dto.ChangePasswordRequest;
import com.helphub.identity.auth.dto.RegisterRequest;
import com.helphub.identity.auth.dto.RegisterResponse;
import com.helphub.identity.auth.dto.ResetPasswordRequest;
import com.helphub.identity.common.exception.BusinessException;
import com.helphub.identity.common.util.EmailNormalizer;
import com.helphub.identity.infrastructure.email.PasswordChangedEmailEvent;
import com.helphub.identity.otp.service.EmailOtpService;
import com.helphub.identity.otp.service.PasswordResetOtpService;
import com.helphub.identity.role.entity.Role;
import com.helphub.identity.role.repository.RoleRepository;
import com.helphub.identity.session.repository.LoginSessionRepository;
import com.helphub.identity.session.repository.RefreshTokenRepository;
import com.helphub.identity.user.entity.AccountStatus;
import com.helphub.identity.user.entity.User;
import com.helphub.identity.user.repository.UserRepository;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Set;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {

        private static final Set<String> SELF_REGISTER_ROLES = Set.of("REQUESTER", "VOLUNTEER");

        private final UserRepository userRepository;
        private final RoleRepository roleRepository;
        private final EmailOtpService emailOtpService;
        private final PasswordEncoder passwordEncoder;
        private final PasswordPolicy passwordPolicy;
        private final PasswordResetOtpService passwordResetOtpService;
        private final LoginSessionRepository loginSessionRepository;
        private final RefreshTokenRepository refreshTokenRepository;
        private final ApplicationEventPublisher eventPublisher;

        public AuthService(
                        UserRepository userRepository,
                        RoleRepository roleRepository,
                        EmailOtpService emailOtpService,
                        PasswordEncoder passwordEncoder,
                        PasswordResetOtpService passwordResetOtpService,
                        LoginSessionRepository loginSessionRepository,
                        RefreshTokenRepository refreshTokenRepository,
                        ApplicationEventPublisher eventPublisher,
                        PasswordPolicy passwordPolicy) {
                this.userRepository = userRepository;
                this.roleRepository = roleRepository;
                this.emailOtpService = emailOtpService;
                this.passwordEncoder = passwordEncoder;
                this.passwordPolicy = passwordPolicy;
                this.passwordResetOtpService = passwordResetOtpService;
                this.loginSessionRepository = loginSessionRepository;
                this.refreshTokenRepository = refreshTokenRepository;
                this.eventPublisher = eventPublisher;
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
        public boolean verifyEmailOtp(String rawEmail, String providedOtp) {

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

        @Transactional
        public void forgotPassword(String email) {

                // 1. Normalize email.
                String normalizedEmail = EmailNormalizer.normalize(email);

                // 2. Look up account.
                Optional<User> existingUser = userRepository.findByEmail(normalizedEmail);

                // Do not reveal whether the account exists.
                if (existingUser.isEmpty()) {
                        return;
                }

                // 3. Lock user to serialize OTP issuance.
                Optional<User> lockedUser = userRepository.findByIdForUpdate(
                                existingUser.get().getId());

                if (lockedUser.isEmpty()) {
                        return;
                }

                User user = lockedUser.get();

                // 4. Only eligible accounts receive an OTP.
                if (user.getStatus() != AccountStatus.ACTIVE
                                || !user.isEmailVerified()) {
                        return;
                }

                // 5. Issue PASSWORD_RESET OTP.
                // The OTP service handles cooldown, hourly limits
                // and revocation of earlier reset OTPs.
                passwordResetOtpService.issue(user);

                // 6. Do not modify password or login sessions.
                // Do not issue Access/Refresh tokens.
        }

        @Transactional
        public boolean resetPassword(ResetPasswordRequest request) {

                // 1. Validate new password first
                // An invalid new password must not consume OTP.
                passwordPolicy.validate(request.newPassword());

                // 2. Normalize email
                String normalizedEmail = EmailNormalizer.normalize(request.email());

                // 3. Find and lock User
                Optional<User> existingUser = userRepository.findByEmail(normalizedEmail);

                if (existingUser.isEmpty()) {
                        return false;
                }

                Optional<User> lockedUser = userRepository.findByIdForUpdate(
                                existingUser.get().getId());

                if (lockedUser.isEmpty()) {
                        return false;
                }

                User user = lockedUser.get();

                // 4. Recheck account eligibility
                if (user.getStatus() != AccountStatus.ACTIVE
                                || !user.isEmailVerified()) {
                        return false;
                }

                // 5. Verify and consume reset OTP
                boolean verified = passwordResetOtpService.verifyAndConsume(
                                user,
                                request.otp());

                if (!verified) {
                        // Return without throwing so failed attempt
                        // counters remain committed.
                        return false;
                }

                // 6. Hash and replace password
                String newPasswordHash = passwordEncoder.encode(request.newPassword());

                user.setPasswordHash(newPasswordHash);

                // 7. Increase security version
                user.setSecurityVersion(
                                user.getSecurityVersion() + 1);

                OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

                // 8. Revoke all login sessions
                loginSessionRepository.revokeAllActiveSessions(
                                user.getId(),
                                now);

                // 9. Revoke all active refresh tokens
                refreshTokenRepository.revokeAllActiveTokensByUser(
                                user.getId(),
                                now);

                // 10. Revoke remaining reset OTPs
                passwordResetOtpService.revokeRemaining(user);

                // 11. Publish security notification
                eventPublisher.publishEvent(
                                new PasswordChangedEmailEvent(
                                                user.getEmail(),
                                                now));

                // 12. No automatic authentication

                // Do not create LoginSession.
                // Do not issue AT or RT.
                // Do not modify MFA fields.

                return true;
        }

        @Transactional
        public void changePassword(UUID userId, ChangePasswordRequest request) {

                // ==========================================
                // 1. Find and lock authenticated user
                // ==========================================

                User user = userRepository
                                .findByIdForUpdate(userId)
                                .orElseThrow(() -> new BusinessException(
                                                HttpStatus.UNAUTHORIZED,
                                                "INVALID_SESSION",
                                                "The current session is no longer valid"));

                // ==========================================
                // 2. Validate account status
                // ==========================================

                if (user.getStatus() != AccountStatus.ACTIVE
                                || !user.isEmailVerified()) {

                        throw new BusinessException(
                                        HttpStatus.FORBIDDEN,
                                        "ACCOUNT_NOT_ELIGIBLE",
                                        "Account is not eligible to change password");
                }

                // ==========================================
                // 3. Verify current password
                // ==========================================

                boolean currentPasswordMatched = passwordEncoder.matches(
                                request.currentPassword(),
                                user.getPasswordHash());

                if (!currentPasswordMatched) {

                        throw new BusinessException(
                                        HttpStatus.BAD_REQUEST,
                                        "INVALID_CURRENT_PASSWORD",
                                        "Current password is incorrect");
                }

                // ==========================================
                // 4. Validate new password policy
                // ==========================================

                passwordPolicy.validate(request.newPassword());

                // ==========================================
                // 5. Prevent password reuse
                // ==========================================

                if (passwordEncoder.matches(
                                request.newPassword(),
                                user.getPasswordHash())) {

                        throw new BusinessException(
                                        HttpStatus.BAD_REQUEST,
                                        "PASSWORD_REUSE_NOT_ALLOWED",
                                        "New password must be different from current password");
                }

                // ==========================================
                // 6. Hash and update password
                // ==========================================

                String newPasswordHash = passwordEncoder.encode(request.newPassword());

                user.setPasswordHash(newPasswordHash);

                // ==========================================
                // 7. Invalidate previously issued ATs
                // ==========================================

                user.setSecurityVersion(
                                user.getSecurityVersion() + 1);

                OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

                // ==========================================
                // 8. Revoke all login sessions
                // ==========================================

                loginSessionRepository.revokeAllActiveSessions(
                                user.getId(),
                                now);

                // ==========================================
                // 9. Revoke all refresh tokens
                // ==========================================

                refreshTokenRepository.revokeAllActiveTokensByUser(
                                user.getId(),
                                now);

                // ==========================================
                // 10. Publish password change notification
                // ==========================================

                eventPublisher.publishEvent(
                                new PasswordChangedEmailEvent(
                                                user.getEmail(),
                                                now));

                // ==========================================
                // 11. No new authentication tokens
                // ==========================================

                // Do not issue new Access Token.
                // Do not issue new Refresh Token.
                // Do not modify MFA settings.
                // User must log in again.
        }
}