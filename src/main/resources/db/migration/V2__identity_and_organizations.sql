CREATE TABLE app.accounts (
    id uuid PRIMARY KEY,
    nickname varchar(100) NOT NULL CHECK (btrim(nickname) <> ''),
    account_status varchar(20) NOT NULL DEFAULT 'ACTIVE'
        CHECK (account_status IN ('ACTIVE', 'SUSPENDED', 'DEACTIVATED')),
    phone_number varchar(16) NOT NULL CHECK (phone_number ~ '^[+]?[0-9]{8,15}$'),
    email varchar(254) NOT NULL CHECK (btrim(email) <> ''),
    email_verified_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE app.local_credentials (
    account_id uuid PRIMARY KEY REFERENCES app.accounts(id) ON DELETE RESTRICT,
    login_id varchar(30) NOT NULL,
    password_hash varchar(255) NOT NULL CHECK (btrim(password_hash) <> ''),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_local_credentials_login_id UNIQUE (login_id),
    CONSTRAINT ck_local_credentials_login_id CHECK (login_id ~ '^[a-z0-9_]{4,30}$')
);

CREATE TABLE app.organizations (
    id uuid PRIMARY KEY,
    name varchar(200) NOT NULL CHECK (btrim(name) <> ''),
    organization_status varchar(20) NOT NULL DEFAULT 'ACTIVE'
        CHECK (organization_status IN ('ACTIVE', 'RECOVERY_REQUIRED', 'ARCHIVED')),
    contact_email varchar(254),
    created_by uuid NOT NULL REFERENCES app.accounts(id) ON DELETE RESTRICT,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE app.organization_memberships (
    organization_id uuid NOT NULL REFERENCES app.organizations(id) ON DELETE RESTRICT,
    account_id uuid NOT NULL REFERENCES app.accounts(id) ON DELETE RESTRICT,
    role varchar(20) NOT NULL CHECK (role IN ('OWNER', 'MEMBER')),
    membership_status varchar(20) NOT NULL DEFAULT 'ACTIVE'
        CHECK (membership_status IN ('ACTIVE', 'REVOKED')),
    joined_at timestamptz NOT NULL DEFAULT now(),
    revoked_at timestamptz,
    PRIMARY KEY (organization_id, account_id),
    CONSTRAINT ck_org_membership_revoked CHECK (
        (membership_status = 'ACTIVE' AND revoked_at IS NULL)
        OR (membership_status = 'REVOKED' AND revoked_at IS NOT NULL)
    )
);

CREATE INDEX ix_org_memberships_account
    ON app.organization_memberships (account_id, membership_status);
