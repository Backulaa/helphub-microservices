package com.helphub.identity.auth.service;

import com.helphub.identity.auth.dto.*;
import com.helphub.identity.common.exception.BusinessException;
import com.helphub.identity.common.util.EmailNormalizer;
import com.helphub.identity.mfa.dto.MfaChallengeResponse;
import com.helphub.identity.mfa.entity.MfaChallenge;
import com.helphub.identity.mfa.entity.MfaChallengePurpose;
import com.helphub.identity.mfa.service.MfaOtpService;
import com.helphub.identity.session.entity.AuthenticationLevel;
import com.helphub.identity.session.entity.LoginSession;
import com.helphub.identity.session.entity.RefreshToken;

import com.helphub.identity.session.repository.LoginSessionRepository;
import com.helphub.identity.session.repository.RefreshTokenRepository;

import com.helphub.identity.session.service.RefreshTokenGenerator;

import com.helphub.identity.user.entity.AccountStatus;
import com.helphub.identity.user.entity.MfaMethod;
import com.helphub.identity.user.entity.User;
import com.helphub.identity.user.repository.UserRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;

import org.springframework.security.crypto.password.PasswordEncoder;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
public class SessionAuthService {

    private final UserRepository userRepository;
    private final LoginSessionRepository sessionRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final RefreshTokenGenerator refreshTokenGenerator;
    private final MfaOtpService mfaOtpService;
    private final long sessionDays;

    public SessionAuthService(
            UserRepository userRepository,
            LoginSessionRepository sessionRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenService jwtTokenService,
            RefreshTokenGenerator refreshTokenGenerator,
            MfaOtpService mfaOtpService,
            @Value("${security.jwt.session-days:14}") long sessionDays) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.refreshTokenGenerator = refreshTokenGenerator;
        this.mfaOtpService = mfaOtpService;
        this.sessionDays = sessionDays;
    }

    // ==========================================
    // Login
    // ==========================================

    @Transactional
    public LoginResult login(LoginRequest request) {

        String email = EmailNormalizer.normalize(
                request.email());

        User user = userRepository
                .findByEmail(email)
                .orElse(null);

        if (user == null
                || !passwordEncoder.matches(
                        request.password(),
                        user.getPasswordHash())) {
            throw new BusinessException(
                    HttpStatus.UNAUTHORIZED,
                    "INVALID_CREDENTIALS",
                    "Invalid email or password");
        }

        if (user.getStatus() != AccountStatus.ACTIVE) {
            throw new BusinessException(
                    HttpStatus.FORBIDDEN,
                    "ACCOUNT_NOT_ACTIVE",
                    "Account is not allowed to log in");
        }

        if (!user.isEmailVerified()) {
            throw new BusinessException(
                    HttpStatus.FORBIDDEN,
                    "EMAIL_NOT_VERIFIED",
                    "Email verification is required");
        }

        // Never bypass MFA after password verification.
        if (user.isMfaEnabled()) {

            User lockedUser = userRepository
                    .findByIdForUpdate(user.getId())
                    .orElseThrow();

            MfaChallenge challenge = mfaOtpService.issue(
                    lockedUser,
                    null,
                    MfaChallengePurpose.LOGIN);

            MfaChallengeResponse challengeResponse = new MfaChallengeResponse(
                    challenge.getId(),
                    "EMAIL_OTP",
                    mfaOtpService.getOtpExpiresInSeconds());

            return new LoginResult.RequiresMfa(
                    challengeResponse);
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        LoginSession session = new LoginSession(
                user,
                AuthenticationLevel.PASSWORD,
                now,
                now.plusDays(sessionDays),
                user.getSecurityVersion());

        session = sessionRepository.saveAndFlush(session);

        user.setLastLoginAt(now);

        return new LoginResult.Authenticated(
                createTokenPair(user, session, now));
    }

    // ==========================================
    // Refresh Token Rotation
    // ==========================================

    @Transactional
    public IssuedTokens refresh(String rawRefreshToken) {

        String tokenHash = refreshTokenGenerator.hash(rawRefreshToken);

        RefreshToken oldToken = refreshTokenRepository
                .findByTokenHashForUpdate(tokenHash)
                .orElse(null);

        if (oldToken == null) {
            return null;
        }

        LoginSession session = sessionRepository
                .findByIdForUpdate(
                        oldToken.getSession().getId())
                .orElse(null);

        if (session == null) {
            return null;
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        // Reuse detection:
        // The token has already been rotated.
        if (oldToken.getRevokedAt() != null
                && oldToken.getReplacedByTokenId() != null) {

            session.setRevokedAt(now);

            // Do not throw here: the revocation
            // must be committed to PostgreSQL.
            return null;
        }

        if (oldToken.getRevokedAt() != null
                || session.getRevokedAt() != null
                || !session.getExpiresAt().isAfter(now)
                || !oldToken.getExpiresAt().isAfter(now)) {
            return null;
        }

        User user = oldToken.getUser();

        if (!session.getUser().getId().equals(user.getId())) {
            return null;
        }

        if (user.getStatus() != AccountStatus.ACTIVE
                || !user.isEmailVerified()) {
            return null;
        }

        if (user.getSecurityVersion() != session.getSecurityVersion()) {
            return null;
        }

        if (user.isMfaEnabled()
                && session.getAuthenticationLevel() != AuthenticationLevel.MFA) {
            return null;
        }

        // Invalidate the current Refresh Token.
        oldToken.setRevokedAt(now);
        oldToken.setLastUsedAt(now);

        String nextRawToken = refreshTokenGenerator.generate();

        RefreshToken nextToken = new RefreshToken(
                user,
                session,
                refreshTokenGenerator.hash(nextRawToken),
                session.getExpiresAt());

        nextToken = refreshTokenRepository.saveAndFlush(
                nextToken);

        oldToken.setReplacedByTokenId(nextToken.getId());

        session.setLastUsedAt(now);

        String accessToken = jwtTokenService.generateAccessToken(
                user,
                session);

        return new IssuedTokens(
                accessToken,
                nextRawToken,
                jwtTokenService.getAccessTokenExpiresIn(),
                session.getId(),
                session.getExpiresAt());
    }

    // ==========================================
    // Logout Current Session
    // ==========================================

    @Transactional
    public void logout(UUID userId, UUID sessionId) {

        LoginSession session = sessionRepository
                .findByIdForUpdate(sessionId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.UNAUTHORIZED,
                        "INVALID_SESSION",
                        "Invalid login session"));

        if (!session.getUser().getId().equals(userId)) {
            throw new BusinessException(
                    HttpStatus.FORBIDDEN,
                    "SESSION_ACCESS_DENIED",
                    "Session does not belong to this user");
        }

        if (session.getRevokedAt() == null) {
            session.setRevokedAt(
                    OffsetDateTime.now(ZoneOffset.UTC));
        }
    }

    // ==========================================
    // Current User
    // ==========================================

    @Transactional(readOnly = true)
    public CurrentUserResponse getCurrentUser(UUID userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND,
                        "USER_NOT_FOUND",
                        "User not found"));

        return new CurrentUserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole().getCode(),
                user.isEmailVerified(),
                user.isMfaEnabled());
    }

    // ==========================================
    // Shared Token Pair Creation
    // ==========================================

    private IssuedTokens createTokenPair(
            User user,
            LoginSession session,
            OffsetDateTime now) {
        String rawRefreshToken = refreshTokenGenerator.generate();

        RefreshToken refreshToken = new RefreshToken(
                user,
                session,
                refreshTokenGenerator.hash(rawRefreshToken),
                session.getExpiresAt());

        refreshTokenRepository.save(refreshToken);

        String accessToken = jwtTokenService.generateAccessToken(
                user,
                session);

        return new IssuedTokens(
                accessToken,
                rawRefreshToken,
                jwtTokenService.getAccessTokenExpiresIn(),
                session.getId(),
                session.getExpiresAt());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public IssuedTokens issueAfterMfa(User user) {

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        LoginSession session = new LoginSession(
                user,
                AuthenticationLevel.MFA,
                now,
                now.plusDays(sessionDays),
                user.getSecurityVersion());

        session.setMfaMethod(MfaMethod.EMAIL_OTP);
        session.setMfaAuthenticatedAt(now);

        session = sessionRepository.saveAndFlush(session);

        user.setLastLoginAt(now);

        return createTokenPair(user, session, now);
    }
}