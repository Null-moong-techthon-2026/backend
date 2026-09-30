-- Disposable design review schema, not an application/Flyway migration.
-- No seed data. Business authorization and state transitions are NOT implemented.
BEGIN;

DO $$
BEGIN
    IF current_database() <> 'boothrock_erd' THEN
        RAISE EXCEPTION 'Apply this draft only to boothrock_erd';
    END IF;
END;
$$;

CREATE SCHEMA erd;
REVOKE ALL ON SCHEMA erd FROM PUBLIC;
COMMENT ON SCHEMA erd IS 'ERD review only, 2026-09-30. Not the app schema.';
SET LOCAL search_path = erd, pg_catalog;

-- 1. Identity, organization ownership, and event delegation.
CREATE TABLE accounts (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    display_name text NOT NULL CHECK (btrim(display_name) <> ''),
    status text NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DEACTIVATED')),
    email text,
    email_verified_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_verified_email_present CHECK (
        email_verified_at IS NULL OR (email IS NOT NULL AND btrim(email) <> '')
    )
);

CREATE TABLE local_credentials (
    account_id uuid PRIMARY KEY REFERENCES accounts(id) ON DELETE RESTRICT,
    login_id text NOT NULL UNIQUE CHECK (login_id ~ '^[a-z0-9_]{4,30}$'),
    password_hash text NOT NULL CHECK (btrim(password_hash) <> ''),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE organizations (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name text NOT NULL CHECK (btrim(name) <> ''),
    status text NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'RECOVERY_REQUIRED', 'ARCHIVED')),
    contact_email text,
    created_by uuid NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE organization_memberships (
    organization_id uuid NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
    account_id uuid NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    role text NOT NULL CHECK (role IN ('OWNER', 'MEMBER')),
    status text NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'REVOKED')),
    joined_at timestamptz NOT NULL DEFAULT now(),
    revoked_at timestamptz,
    PRIMARY KEY (organization_id, account_id),
    CONSTRAINT ck_org_membership_revoked CHECK (
        (status = 'ACTIVE' AND revoked_at IS NULL)
        OR (status = 'REVOKED' AND revoked_at IS NOT NULL)
    )
);
CREATE INDEX ix_org_memberships_account ON organization_memberships (account_id, status);

CREATE TABLE events (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
    created_by uuid NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    name text NOT NULL CHECK (btrim(name) <> ''),
    venue text,
    starts_at timestamptz,
    ends_at timestamptz,
    publication_status text NOT NULL DEFAULT 'DRAFT'
        CHECK (publication_status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (id, organization_id),
    CONSTRAINT ck_event_period CHECK (starts_at < ends_at),
    CONSTRAINT ck_published_event_complete CHECK (
        publication_status <> 'PUBLISHED' OR (
            venue IS NOT NULL AND btrim(venue) <> ''
            AND starts_at IS NOT NULL AND ends_at IS NOT NULL
        )
    )
);
CREATE INDEX ix_events_organization ON events (organization_id);

CREATE TABLE event_memberships (
    event_id uuid NOT NULL,
    account_id uuid NOT NULL,
    organization_id uuid NOT NULL,
    role text NOT NULL CHECK (role IN ('MANAGER', 'STAFF')),
    status text NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'REVOKED')),
    created_at timestamptz NOT NULL DEFAULT now(),
    revoked_at timestamptz,
    PRIMARY KEY (event_id, account_id),
    CONSTRAINT fk_event_member_event FOREIGN KEY (event_id, organization_id)
        REFERENCES events(id, organization_id) ON DELETE RESTRICT,
    CONSTRAINT fk_event_member_organization FOREIGN KEY (organization_id, account_id)
        REFERENCES organization_memberships(organization_id, account_id) ON DELETE RESTRICT,
    CONSTRAINT ck_event_membership_revoked CHECK (
        (status = 'ACTIVE' AND revoked_at IS NULL)
        OR (status = 'REVOKED' AND revoked_at IS NOT NULL)
    )
);
CREATE INDEX ix_event_memberships_account ON event_memberships (account_id, status);

CREATE TABLE organization_invitations (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
    target_account_id uuid NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    invited_by uuid NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    invited_role text NOT NULL CHECK (invited_role IN ('OWNER', 'MEMBER')),
    token_hash text NOT NULL UNIQUE CHECK (token_hash ~ '^[0-9a-f]{64}$'),
    status text NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'ACCEPTED', 'REVOKED')),
    created_at timestamptz NOT NULL DEFAULT now(),
    expires_at timestamptz NOT NULL,
    accepted_at timestamptz,
    revoked_at timestamptz,
    CONSTRAINT ck_org_invitation_expiry CHECK (expires_at > created_at),
    CONSTRAINT ck_org_invitation_state CHECK (
        (status = 'PENDING' AND accepted_at IS NULL AND revoked_at IS NULL)
        OR (status = 'ACCEPTED' AND accepted_at IS NOT NULL AND revoked_at IS NULL)
        OR (status = 'REVOKED' AND accepted_at IS NULL AND revoked_at IS NOT NULL)
    )
);

CREATE TABLE audit_logs (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid REFERENCES organizations(id) ON DELETE RESTRICT,
    event_id uuid,
    actor_account_id uuid REFERENCES accounts(id) ON DELETE RESTRICT,
    actor_type text NOT NULL CHECK (actor_type IN ('ACCOUNT', 'SYSTEM')),
    action text NOT NULL CHECK (btrim(action) <> ''),
    target_type text NOT NULL CHECK (btrim(target_type) <> ''),
    target_id uuid NOT NULL,
    occurred_at timestamptz NOT NULL DEFAULT now(),
    allowed_changes jsonb NOT NULL DEFAULT '{}'::jsonb CHECK (jsonb_typeof(allowed_changes) = 'object'),
    CONSTRAINT fk_audit_event FOREIGN KEY (event_id, organization_id)
        REFERENCES events(id, organization_id) ON DELETE RESTRICT,
    CONSTRAINT ck_audit_event_organization CHECK (event_id IS NULL OR organization_id IS NOT NULL),
    CONSTRAINT ck_audit_actor CHECK (
        (actor_type = 'ACCOUNT' AND actor_account_id IS NOT NULL)
        OR (actor_type = 'SYSTEM' AND actor_account_id IS NULL)
    )
);

-- 2. Reusable booth profiles, applications, and event-specific operation.
CREATE TABLE booth_profiles (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_account_id uuid NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    name text NOT NULL CHECK (btrim(name) <> ''),
    category text NOT NULL CHECK (btrim(category) <> ''),
    introduction text NOT NULL DEFAULT '',
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE booth_applications (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id uuid NOT NULL REFERENCES events(id) ON DELETE RESTRICT,
    applicant_account_id uuid NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    booth_profile_id uuid NOT NULL REFERENCES booth_profiles(id) ON DELETE RESTRICT,
    booth_name_snapshot text NOT NULL CHECK (btrim(booth_name_snapshot) <> ''),
    category_snapshot text NOT NULL CHECK (btrim(category_snapshot) <> ''),
    introduction_snapshot text NOT NULL DEFAULT '',
    contact_name_snapshot text,
    contact_email_snapshot text,
    contact_phone_snapshot text,
    status text NOT NULL DEFAULT 'SUBMITTED' CHECK (status IN ('SUBMITTED', 'APPROVED', 'REJECTED')),
    review_reason text,
    reviewed_by uuid REFERENCES accounts(id) ON DELETE RESTRICT,
    reviewed_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (id, event_id),
    CONSTRAINT ck_application_review CHECK (
        (status = 'SUBMITTED' AND reviewed_by IS NULL AND reviewed_at IS NULL)
        OR (status IN ('APPROVED', 'REJECTED') AND reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL)
    )
);
CREATE INDEX ix_applications_event_status ON booth_applications (event_id, status, created_at);

CREATE TABLE event_booths (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id uuid NOT NULL REFERENCES events(id) ON DELETE RESTRICT,
    application_id uuid UNIQUE,
    source text NOT NULL CHECK (source IN ('APPLICATION', 'MANUAL')),
    display_code text CHECK (display_code IS NULL OR btrim(display_code) <> ''),
    name text NOT NULL CHECK (btrim(name) <> ''),
    category text NOT NULL CHECK (btrim(category) <> ''),
    description text NOT NULL DEFAULT '',
    operation_status text NOT NULL DEFAULT 'PREPARING'
        CHECK (operation_status IN ('PREPARING', 'OPEN', 'SOLD_OUT', 'CLOSED')),
    visibility_status text NOT NULL DEFAULT 'HIDDEN'
        CHECK (visibility_status IN ('HIDDEN', 'PUBLIC', 'ARCHIVED')),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (id, event_id),
    UNIQUE (event_id, display_code),
    CONSTRAINT fk_booth_application FOREIGN KEY (application_id, event_id)
        REFERENCES booth_applications(id, event_id) ON DELETE RESTRICT,
    CONSTRAINT ck_booth_source CHECK (
        (source = 'APPLICATION' AND application_id IS NOT NULL)
        OR (source = 'MANUAL' AND application_id IS NULL)
    )
);
CREATE INDEX ix_booths_event_category ON event_booths (event_id, category);

CREATE TABLE booth_memberships (
    event_booth_id uuid NOT NULL REFERENCES event_booths(id) ON DELETE RESTRICT,
    account_id uuid NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    role text NOT NULL DEFAULT 'OPERATOR' CHECK (role = 'OPERATOR'),
    status text NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'REVOKED')),
    created_at timestamptz NOT NULL DEFAULT now(),
    revoked_at timestamptz,
    PRIMARY KEY (event_booth_id, account_id),
    CONSTRAINT ck_booth_membership_revoked CHECK (
        (status = 'ACTIVE' AND revoked_at IS NULL)
        OR (status = 'REVOKED' AND revoked_at IS NOT NULL)
    )
);
CREATE INDEX ix_booth_memberships_account ON booth_memberships (account_id, status);

CREATE TABLE booth_items (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    event_booth_id uuid NOT NULL REFERENCES event_booths(id) ON DELETE RESTRICT,
    name text NOT NULL CHECK (btrim(name) <> ''),
    description text,
    price_krw bigint CHECK (price_krw >= 0),
    stock_status text NOT NULL DEFAULT 'AVAILABLE'
        CHECK (stock_status IN ('UNLIMITED', 'AVAILABLE', 'LOW', 'SOLD_OUT')),
    sort_order integer NOT NULL DEFAULT 0 CHECK (sort_order >= 0),
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_items_booth ON booth_items (event_booth_id, sort_order);

-- 3. Image metadata, floor-plan versions, and normalized pin positions.
CREATE TABLE media_assets (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id uuid NOT NULL REFERENCES events(id) ON DELETE RESTRICT,
    uploaded_by uuid NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    bucket text NOT NULL CHECK (btrim(bucket) <> ''),
    object_key text NOT NULL CHECK (btrim(object_key) <> ''),
    mime_type text NOT NULL CHECK (mime_type IN ('image/png', 'image/jpeg')),
    size_bytes bigint NOT NULL CHECK (size_bytes > 0),
    width_px integer NOT NULL CHECK (width_px > 0),
    height_px integer NOT NULL CHECK (height_px > 0),
    visibility text NOT NULL DEFAULT 'PRIVATE' CHECK (visibility IN ('PRIVATE', 'PUBLIC')),
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (bucket, object_key),
    UNIQUE (id, event_id)
);

CREATE TABLE floor_plans (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id uuid NOT NULL REFERENCES events(id) ON DELETE RESTRICT,
    media_asset_id uuid NOT NULL,
    version_no integer NOT NULL CHECK (version_no > 0),
    publication_status text NOT NULL DEFAULT 'DRAFT'
        CHECK (publication_status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    revision bigint NOT NULL DEFAULT 0 CHECK (revision >= 0),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (id, event_id),
    UNIQUE (event_id, version_no),
    CONSTRAINT fk_floor_plan_media FOREIGN KEY (media_asset_id, event_id)
        REFERENCES media_assets(id, event_id) ON DELETE RESTRICT
);
CREATE UNIQUE INDEX ux_one_published_map ON floor_plans (event_id)
    WHERE publication_status = 'PUBLISHED';

CREATE TABLE map_pins (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id uuid NOT NULL,
    floor_plan_id uuid NOT NULL,
    event_booth_id uuid,
    kind text NOT NULL CHECK (kind IN ('BOOTH', 'TOILET', 'INFO', 'MEDICAL')),
    label text NOT NULL CHECK (btrim(label) <> ''),
    x_ratio numeric(8,7) NOT NULL CHECK (x_ratio BETWEEN 0 AND 1),
    y_ratio numeric(8,7) NOT NULL CHECK (y_ratio BETWEEN 0 AND 1),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT fk_pin_map FOREIGN KEY (floor_plan_id, event_id)
        REFERENCES floor_plans(id, event_id) ON DELETE RESTRICT,
    CONSTRAINT fk_pin_booth FOREIGN KEY (event_booth_id, event_id)
        REFERENCES event_booths(id, event_id) ON DELETE RESTRICT,
    CONSTRAINT ck_facility_without_booth CHECK (kind = 'BOOTH' OR event_booth_id IS NULL)
);
CREATE UNIQUE INDEX ux_pin_booth_per_map ON map_pins (floor_plan_id, event_booth_id)
    WHERE event_booth_id IS NOT NULL;

-- Supporting event notices, separate from private application information.
CREATE TABLE announcements (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id uuid NOT NULL REFERENCES events(id) ON DELETE RESTRICT,
    created_by uuid NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    title text NOT NULL CHECK (btrim(title) <> ''),
    body text NOT NULL DEFAULT '',
    is_urgent boolean NOT NULL DEFAULT false,
    publication_status text NOT NULL DEFAULT 'DRAFT'
        CHECK (publication_status IN ('DRAFT', 'PUBLISHED', 'DELETED')),
    published_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_published_notice_time CHECK (publication_status <> 'PUBLISHED' OR published_at IS NOT NULL)
);

CREATE TABLE announcement_audiences (
    announcement_id uuid NOT NULL REFERENCES announcements(id) ON DELETE RESTRICT,
    audience text NOT NULL CHECK (audience IN ('PUBLIC', 'OPERATORS', 'STAFF')),
    PRIMARY KEY (announcement_id, audience)
);

COMMENT ON TABLE accounts IS '1 Identity: a person, not a global organizer/operator role.';
COMMENT ON TABLE local_credentials IS '1 Identity: draft username/password storage; no login API exists.';
COMMENT ON TABLE organizations IS '1 Ownership: persistent organizer; created_by is audit only.';
COMMENT ON TABLE organization_memberships IS '1 Ownership: multiple owners; last-owner protection requires a service transaction.';
COMMENT ON TABLE organization_invitations IS '1 Ownership: account-bound invitation, not a booth application invitation.';
COMMENT ON TABLE events IS '1 Event: belongs to an organization, not to its creator email.';
COMMENT ON TABLE event_memberships IS '1 Event: optional event-specific delegation to an organization member.';
COMMENT ON TABLE audit_logs IS 'Supporting audit: no password/token/contact payloads. Append-only service policy is not implemented.';
COMMENT ON TABLE booth_profiles IS '2 Booth: reusable introduction owned by an account.';
COMMENT ON TABLE booth_applications IS '2 Booth: application-time snapshot; contact fields are private.';
COMMENT ON TABLE event_booths IS '2 Booth: event-specific operation; MANUAL allows no application/operator account.';
COMMENT ON TABLE booth_memberships IS '2 Booth: external operators need not join the organizer organization.';
COMMENT ON TABLE booth_items IS '2 Booth: manual stock states, not automatic quantity tracking.';
COMMENT ON TABLE media_assets IS '3 Map/media: file metadata only. No image upload or Storage connection exists.';
COMMENT ON TABLE floor_plans IS '3 Map: versioned images, at most one published version per event.';
COMMENT ON TABLE map_pins IS '3 Map: normalized image coordinates and an optional booth in the same event.';
COMMENT ON TABLE announcements IS 'Supporting notice: audience access requires server-side filtering.';
COMMENT ON TABLE announcement_audiences IS 'Supporting notice: PUBLIC, OPERATORS, STAFF; at least one on publish is a service rule.';
COMMENT ON COLUMN booth_items.price_krw IS 'NULL means unset; zero means free.';
COMMENT ON COLUMN map_pins.x_ratio IS 'Horizontal position in the image, from 0 to 1; not a screen pixel or longitude.';
COMMENT ON COLUMN map_pins.y_ratio IS 'Vertical position in the image, from 0 to 1; not a screen pixel or latitude.';
COMMENT ON COLUMN audit_logs.target_id IS 'Polymorphic audit reference, not a database FK.';

COMMIT;
