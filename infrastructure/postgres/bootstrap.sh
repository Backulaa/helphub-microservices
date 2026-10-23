#!/bin/sh
set -eu

echo "Starting HELPhub database bootstrap..."

export PGHOST=postgres
export PGPORT=5432
export PGUSER="$POSTGRES_ADMIN_USER"
export PGPASSWORD="$POSTGRES_ADMIN_PASSWORD"
export PGDATABASE=postgres

create_database() {
    db_name="$1"
    db_user="$2"
    db_password="$3"

    echo "Checking database: $db_name"

    psql \
        -X \
        -v ON_ERROR_STOP=1 \
        -v db_name="$db_name" \
        -v db_user="$db_user" \
        -v db_password="$db_password" <<'SQL'

-- Create login role if missing.
SELECT format(
    'CREATE ROLE %I LOGIN PASSWORD %L',
    :'db_user',
    :'db_password'
)
WHERE NOT EXISTS (
    SELECT 1 FROM pg_roles
    WHERE rolname = :'db_user'
)
\gexec

-- Create database if missing.
SELECT format(
    'CREATE DATABASE %I OWNER %I',
    :'db_name',
    :'db_user'
)
WHERE NOT EXISTS (
    SELECT 1 FROM pg_database
    WHERE datname = :'db_name'
)
\gexec

-- Restrict general database connections.
SELECT format(
    'REVOKE CONNECT ON DATABASE %I FROM PUBLIC',
    :'db_name'
)
\gexec

-- Permit the service account to connect.
SELECT format(
    'GRANT CONNECT ON DATABASE %I TO %I',
    :'db_name',
    :'db_user'
)
\gexec

SQL

    echo "Database ready: $db_name"
}

# Identity
create_database \
    "$IDENTITY_DB_NAME" \
    "$IDENTITY_DB_USER" \
    "$IDENTITY_DB_PASSWORD"

# Support
create_database \
    "$SUPPORT_DB_NAME" \
    "$SUPPORT_DB_USER" \
    "$SUPPORT_DB_PASSWORD"

# Media
create_database \
    "$MEDIA_DB_NAME" \
    "$MEDIA_DB_USER" \
    "$MEDIA_DB_PASSWORD"

echo "HELPhub database bootstrap completed."