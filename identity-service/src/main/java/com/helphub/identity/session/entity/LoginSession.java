package com.helphub.identity.session.entity;

import com.helphub.identity.common.persistence.AuditableEntity;
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
@Table(name = "login_sessions", indexes = {
        @Index(name = "idx_sessions_user", columnList = "user_id"),
        @Index(name = "idx_sessions_expires", columnList = "expires_at")
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LoginSession extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_sessions_user"))
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "authentication_level", nullable = false, length = 20)
    private AuthenticationLevel authenticationLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "mfa_method", length = 30)
    private MfaMethod mfaMethod;

    @Column(name = "authenticated_at", nullable = false)
    private OffsetDateTime authenticatedAt;

    @Column(name = "mfa_authenticated_at")
    private OffsetDateTime mfaAuthenticatedAt;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    @Column(name = "last_used_at")
    private OffsetDateTime lastUsedAt;

    @Column(name = "security_version", nullable = false)
    private long securityVersion;

    @Column(name = "device_name", length = 255)
    private String deviceName;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;

    @Version
    @Column(name = "version")
    private Long version;
}