-- =============================================
-- HelpHub IdentityService
-- Default Role Seed Data
-- =============================================
-- Runs automatically on application startup.
-- Creates missing roles and preserves existing ones.
-- =============================================

INSERT INTO roles (
    id,
    code,
    name,
    description,
    is_active,
    created_at,
    updated_at
)
VALUES
(
    gen_random_uuid(),
    'ADMIN',
    'Administrator',
    'System administrator with full administrative privileges.',
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
),
(
    gen_random_uuid(),
    'COLLABORATOR',
    'Collaborator',
    'Community collaborator responsible for coordinating support activities.',
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
),
(
    gen_random_uuid(),
    'VOLUNTEER',
    'Volunteer',
    'Volunteer who provides assistance to people in need.',
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
),
(
    gen_random_uuid(),
    'REQUESTER',
    'Requester',
    'User who creates support requests and receives community assistance.',
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)

ON CONFLICT (code) DO NOTHING;