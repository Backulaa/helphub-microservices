-- =====================================================
-- HelpHub Identity Service
-- Development User Seed Data
-- =====================================================
-- Creates one demo user for each role if missing.
--
-- Execution:
--   1. Hibernate creates/updates database schema.
--   2. seed-roles.sql inserts missing roles.
--   3. DevUserSeedConfig checks existing users.
--   4. This script runs only when a role has no user.
--
-- Existing users are never modified or deleted.
-- Development environment ONLY.
-- =====================================================

WITH demo_users (
    role_code,
    email,
    full_name
) AS (
    VALUES
        (
            'ADMIN',
            'admin.demo@helphub.local',
            'HelpHub Admin'
        ),
        (
            'COLLABORATOR',
            'collaborator.demo@helphub.local',
            'HelpHub Collaborator'
        ),
        (
            'VOLUNTEER',
            'volunteer.demo@helphub.local',
            'HelpHub Volunteer'
        ),
        (
            'REQUESTER',
            'requester.demo@helphub.local',
            'HelpHub Requester'
        )
)

INSERT INTO users (
    id,
    email,
    password_hash,
    full_name,
    role_id,
    status,
    is_email_verified,
    email_verified_at,
    is_mfa_enabled,
    security_version,
    version,
    created_at,
    updated_at
)

SELECT
    gen_random_uuid(),
    d.email,

    -- BCrypt cost 12.
    -- Development password:
    -- HelpHubDemo#2026Secure!
    '$2y$12$WcN2L.kTc1VXQnhgcjadjeOnUcyY2IA9UO5O63/zJec/H.bvgXK5O',

    d.full_name,
    r.id,
    'ACTIVE',
    TRUE,
    CURRENT_TIMESTAMP,
    FALSE,
    0,
    0,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP

FROM demo_users d

INNER JOIN roles r
    ON r.code = d.role_code

WHERE r.is_active = TRUE

    -- Do not create another user if the role
    -- already has at least one user.
    AND NOT EXISTS (
        SELECT 1
        FROM users u
        WHERE u.role_id = r.id
    )

-- Prevent duplicate email on repeated execution.
ON CONFLICT (email) DO NOTHING;