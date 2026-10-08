package com.helphub.identity.user.entity;

import com.helphub.identity.common.persistence.AuditableEntity;
import com.helphub.identity.role.entity.Role;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "users", uniqueConstraints = {
        @UniqueConstraint(name = "uk_users_email", columnNames = "email")
}, indexes = {
        @Index(name = "idx_users_role_id", columnList = "role_id"),
        @Index(name = "idx_users_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", length = 30)
    private Gender gender;

    @Column(name = "avatar_url", columnDefinition = "TEXT")
    private String avatarUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private AccountStatus status = AccountStatus.ACTIVE;

    @Column(name = "is_email_verified", nullable = false)
    private boolean emailVerified = false;

    @Column(name = "email_verified_at")
    private OffsetDateTime emailVerifiedAt;

    @Column(name = "is_mfa_enabled", nullable = false)
    private boolean mfaEnabled = false;

    @Column(name = "mfa_enabled_at")
    private OffsetDateTime mfaEnabledAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "mfa_method", length = 30)
    private MfaMethod mfaMethod;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false, foreignKey = @ForeignKey(name = "fk_users_role"))
    private Role role;

    @Column(name = "last_login_at")
    private OffsetDateTime lastLoginAt;

    @Column(name = "security_version", nullable = false)
    private long securityVersion = 0L;

    @Version
    @Column(name = "version")
    private Long version;

    public User(
            String email,
            String passwordHash,
            String fullName,
            Role role) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.role = role;

        this.status = AccountStatus.ACTIVE;
        this.emailVerified = false;
        this.mfaEnabled = false;
        this.securityVersion = 0L;
    }
}