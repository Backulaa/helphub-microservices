package com.helphub.identity.mfa.service;

import com.helphub.identity.auth.dto.IssuedTokens;
import com.helphub.identity.auth.service.SessionAuthService;

import com.helphub.identity.common.exception.BusinessException;

import com.helphub.identity.mfa.dto.MfaStatusResponse;
import com.helphub.identity.mfa.entity.MfaChallenge;
import com.helphub.identity.mfa.entity.MfaChallengePurpose;
import com.helphub.identity.mfa.repository.MfaChallengeRepository;

import com.helphub.identity.session.entity.AuthenticationLevel;
import com.helphub.identity.session.entity.LoginSession;
import com.helphub.identity.session.repository.LoginSessionRepository;

import com.helphub.identity.user.entity.AccountStatus;
import com.helphub.identity.user.entity.MfaMethod;
import com.helphub.identity.user.entity.User;
import com.helphub.identity.user.repository.UserRepository;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
public class MfaService {

        private final UserRepository userRepository;
        private final LoginSessionRepository sessionRepository;
        private final MfaChallengeRepository challengeRepository;

        private final PasswordEncoder passwordEncoder;
        private final MfaOtpService mfaOtpService;
        private final SessionAuthService sessionAuthService;

        public MfaService(
                        UserRepository userRepository,
                        LoginSessionRepository sessionRepository,
                        MfaChallengeRepository challengeRepository,
                        PasswordEncoder passwordEncoder,
                        MfaOtpService mfaOtpService,
                        SessionAuthService sessionAuthService) {
                this.userRepository = userRepository;
                this.sessionRepository = sessionRepository;
                this.challengeRepository = challengeRepository;
                this.passwordEncoder = passwordEncoder;
                this.mfaOtpService = mfaOtpService;
                this.sessionAuthService = sessionAuthService;
        }

        // ==========================================
        // Get MFA Status
        // ==========================================

        @Transactional(readOnly = true)
        public MfaStatusResponse getMfaStatus(UUID userId) {

                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new BusinessException(
                                                HttpStatus.NOT_FOUND,
                                                "USER_NOT_FOUND",
                                                "User not found"));

                return new MfaStatusResponse(
                                user.isMfaEnabled(),
                                user.getMfaMethod(),
                                user.getMfaEnabledAt());
        }

        // ==========================================
        // Start Setup or Disable
        // ==========================================

        @Transactional
        public MfaChallenge start(
                        UUID userId,
                        UUID sessionId,
                        String currentPassword,
                        MfaChallengePurpose purpose) {
                if (purpose == MfaChallengePurpose.LOGIN) {
                        throw new IllegalArgumentException(
                                        "LOGIN challenge is started during password login");
                }

                User user = getLockedUser(userId);

                if (user.getStatus() != AccountStatus.ACTIVE
                                || !user.isEmailVerified()) {
                        throw new BusinessException(
                                        HttpStatus.FORBIDDEN,
                                        "ACCOUNT_NOT_ELIGIBLE",
                                        "Account is not eligible for MFA changes");
                }

                LoginSession session = getLockedSession(
                                userId,
                                sessionId);

                if (!passwordEncoder.matches(
                                currentPassword,
                                user.getPasswordHash())) {
                        throw new BusinessException(
                                        HttpStatus.UNAUTHORIZED,
                                        "INVALID_CREDENTIALS",
                                        "Current password is incorrect");
                }

                if (purpose == MfaChallengePurpose.SETUP
                                && user.isMfaEnabled()) {
                        throw new BusinessException(
                                        HttpStatus.CONFLICT,
                                        "MFA_ALREADY_ENABLED",
                                        "MFA is already enabled");
                }

                if (purpose == MfaChallengePurpose.DISABLE) {

                        if (!user.isMfaEnabled()) {
                                throw new BusinessException(
                                                HttpStatus.CONFLICT,
                                                "MFA_NOT_ENABLED",
                                                "MFA is not enabled");
                        }

                        if (session.getAuthenticationLevel() != AuthenticationLevel.MFA) {
                                throw new BusinessException(
                                                HttpStatus.FORBIDDEN,
                                                "MFA_AUTHENTICATION_REQUIRED",
                                                "A fully MFA-authenticated session is required");
                        }
                }

                return mfaOtpService.issue(
                                user,
                                session,
                                purpose);
        }

        // ==========================================
        // Confirm Setup or Disable
        // ==========================================

        @Transactional
        public boolean confirm(
                        UUID userId,
                        UUID sessionId,
                        UUID challengeId,
                        String otp,
                        MfaChallengePurpose purpose) {
                if (purpose == MfaChallengePurpose.LOGIN) {
                        return false;
                }

                User user = getLockedUser(userId);

                LoginSession session = getLockedSession(
                                userId,
                                sessionId);

                MfaChallenge challenge = challengeRepository
                                .findByIdForUpdate(challengeId)
                                .orElse(null);

                if (challenge == null
                                || !challenge.getUser().getId().equals(userId)
                                || challenge.getPurpose() != purpose
                                || challenge.getSession() == null
                                || !challenge.getSession().getId()
                                                .equals(sessionId)
                                || challenge.getSecurityVersion() != user.getSecurityVersion()
                                || user.getStatus() != AccountStatus.ACTIVE
                                || !user.isEmailVerified()) {
                        return false;
                }

                if (purpose == MfaChallengePurpose.SETUP
                                && user.isMfaEnabled()) {
                        return false;
                }

                if (purpose == MfaChallengePurpose.DISABLE
                                && (!user.isMfaEnabled()
                                                || session.getAuthenticationLevel() != AuthenticationLevel.MFA)) {
                        return false;
                }

                // A wrong OTP must NOT cause transaction rollback.
                if (!mfaOtpService.verify(challenge, otp)) {
                        return false;
                }

                OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

                if (purpose == MfaChallengePurpose.SETUP) {

                        user.setMfaEnabled(true);
                        user.setMfaMethod(MfaMethod.EMAIL_OTP);
                        user.setMfaEnabledAt(now);

                } else {

                        user.setMfaEnabled(false);
                        user.setMfaMethod(null);
                        user.setMfaEnabledAt(null);
                }

                // Invalidate tokens issued under older
                // authentication requirements.
                user.setSecurityVersion(
                                user.getSecurityVersion() + 1);

                sessionRepository.revokeAllActiveSessions(
                                userId,
                                now);

                return true;
        }

        // ==========================================
        // Verify MFA Login
        // ==========================================

        @Transactional
        public IssuedTokens verifyLogin(
                        UUID challengeId,
                        String otp) {
                MfaChallenge challenge = challengeRepository
                                .findByIdForUpdate(challengeId)
                                .orElse(null);

                if (challenge == null
                                || challenge.getPurpose() != MfaChallengePurpose.LOGIN
                                || challenge.getSession() != null) {
                        return null;
                }

                User user = getLockedUser(
                                challenge.getUser().getId());

                if (user.getStatus() != AccountStatus.ACTIVE
                                || !user.isEmailVerified()
                                || !user.isMfaEnabled()
                                || user.getMfaMethod() != MfaMethod.EMAIL_OTP
                                || challenge.getSecurityVersion() != user.getSecurityVersion()) {
                        return null;
                }

                if (!mfaOtpService.verify(challenge, otp)) {
                        return null;
                }

                // Only now may the backend create a login session
                // and issue Access + Refresh tokens.
                return sessionAuthService.issueAfterMfa(user);
        }

        // ==========================================
        // Private Helpers
        // ==========================================

        private User getLockedUser(UUID userId) {
                return userRepository.findByIdForUpdate(userId)
                                .orElseThrow(() -> new BusinessException(
                                                HttpStatus.NOT_FOUND,
                                                "USER_NOT_FOUND",
                                                "User not found"));
        }

        private LoginSession getLockedSession(
                        UUID userId,
                        UUID sessionId) {
                LoginSession session = sessionRepository
                                .findByIdForUpdate(sessionId)
                                .orElseThrow(() -> new BusinessException(
                                                HttpStatus.UNAUTHORIZED,
                                                "INVALID_SESSION",
                                                "Invalid login session"));

                OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

                if (!session.getUser().getId().equals(userId)
                                || session.getRevokedAt() != null
                                || !session.getExpiresAt().isAfter(now)) {
                        throw new BusinessException(
                                        HttpStatus.UNAUTHORIZED,
                                        "INVALID_SESSION",
                                        "Invalid or expired login session");
                }

                return session;
        }
}