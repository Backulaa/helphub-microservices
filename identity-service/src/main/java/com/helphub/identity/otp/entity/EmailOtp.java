package com.helphub.identity.otp.entity;

import com.helphub.identity.common.persistence.AuditableEntity;
import com.helphub.identity.user.entity.User;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "email_otps", uniqueConstraints = {
        @UniqueConstraint(name = "uk_email_otps_challenge", columnNames = "challenge_id")
}, indexes = {
        @Index(name = "idx_email_otps_user_purpose", columnList = "user_id,purpose"),
        @Index(name = "idx_email_otps_expires", columnList = "expires_at")
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmailOtp extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "challenge_id", nullable = false, updatable = false)
    private UUID challengeId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_email_otps_user"))
    private User user;

    @Column(name = "otp_hash", nullable = false, length = 64)
    private String otpHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 30)
    private OtpPurpose purpose;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "verified_at")
    private OffsetDateTime verifiedAt;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 0;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    @Version
    @Column(name = "version")
    private Long version;
}