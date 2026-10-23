package com.helphub.identity.rolechange.entity;

import com.helphub.identity.common.persistence.AuditableEntity;
import com.helphub.identity.role.entity.Role;
import com.helphub.identity.user.entity.User;

import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "role_change_requests", indexes = {
        @Index(name = "idx_role_requests_user_status", columnList = "user_id,status"),
        @Index(name = "idx_role_requests_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoleChangeRequest extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_role_requests_user"))
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "current_role_id", nullable = false, foreignKey = @ForeignKey(name = "fk_role_requests_current_role"))
    private Role currentRole;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requested_role_id", nullable = false, foreignKey = @ForeignKey(name = "fk_role_requests_requested_role"))
    private Role requestedRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RoleRequestStatus status = RoleRequestStatus.PENDING;

    @Size(max = 1000, message = "Reason must not exceed 1000 characters")
    @Column(name = "reason", columnDefinition = "TEXT", length = 1000)
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by", foreignKey = @ForeignKey(name = "fk_role_requests_reviewer"))
    private User reviewedBy;

    @Size(max = 1000, message = "Review note must not exceed 1000 characters")
    @Column(name = "review_note", columnDefinition = "TEXT", length = 1000)
    private String reviewNote;

    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;

    @Column(name = "cancelled_at")
    private OffsetDateTime cancelledAt;

    @Version
    @Column(name = "version")
    private Long version;

    public static RoleChangeRequest createPending(
            User user,
            Role requestedRole,
            String reason) {

        Objects.requireNonNull(user, "User is required");
        Objects.requireNonNull(requestedRole, "Requested role is required");

        Role currentRole = Objects.requireNonNull(
                user.getRole(),
                "Current role is required");

        RoleChangeRequest request = new RoleChangeRequest();

        request.user = user;
        request.currentRole = currentRole;
        request.requestedRole = requestedRole;
        request.status = RoleRequestStatus.PENDING;
        request.reason = reason;

        request.reviewedBy = null;
        request.reviewNote = null;
        request.reviewedAt = null;
        request.cancelledAt = null;

        return request;
    }
}