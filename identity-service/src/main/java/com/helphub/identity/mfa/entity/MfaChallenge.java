package com.helphub.identity.mfa.entity;

import com.helphub.identity.common.persistence.AuditableEntity;
import com.helphub.identity.session.entity.LoginSession;
import com.helphub.identity.user.entity.MfaMethod;
import com.helphub.identity.user.entity.User;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "mfa_challenges", indexes = {
        @Index(name = "idx_mfa_challenges_user", columnList = "user_id"),
        @Index(name = "idx_mfa_challenges_expires", columnList = "expires_at")
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MfaChallenge extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_mfa_challenges_user"))
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", foreignKey = @ForeignKey(name = "fk_mfa_challenges_session"))
    private LoginSession session;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 20)
    private MfaChallengePurpose purpose;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false, length = 30)
    private MfaMethod method = MfaMethod.EMAIL_OTP;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 0;

    @Column(name = "security_version", nullable = false)
    private long securityVersion;

    @Version
    @Column(name = "version")
    private Long version;
}