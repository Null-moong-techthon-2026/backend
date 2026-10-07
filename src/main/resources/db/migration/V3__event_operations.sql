-- Event operations and wireframe APIs. Existing identity data is preserved.
SET LOCAL search_path = app, pg_catalog;

CREATE TABLE events (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
    created_by uuid NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    name text NOT NULL CHECK (btrim(name) <> ''),
    venue text,
    description text NOT NULL DEFAULT '',
    poster_asset_id uuid,
    starts_at timestamptz,
    ends_at timestamptz,
    publication_status text NOT NULL DEFAULT 'DRAFT'
        CHECK (publication_status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    revision bigint NOT NULL DEFAULT 0 CHECK (revision >= 0),
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
    membership_status text NOT NULL DEFAULT 'ACTIVE' CHECK (membership_status IN ('ACTIVE', 'REVOKED')),
    created_at timestamptz NOT NULL DEFAULT now(),
    revoked_at timestamptz,
    PRIMARY KEY (event_id, account_id),
    CONSTRAINT fk_event_member_event FOREIGN KEY (event_id, organization_id)
        REFERENCES events(id, organization_id) ON DELETE RESTRICT,
    CONSTRAINT fk_event_member_organization FOREIGN KEY (organization_id, account_id)
        REFERENCES organization_memberships(organization_id, account_id) ON DELETE RESTRICT,
    CONSTRAINT ck_event_membership_revoked CHECK (
        (membership_status = 'ACTIVE' AND revoked_at IS NULL)
        OR (membership_status = 'REVOKED' AND revoked_at IS NOT NULL)
    )
);
CREATE INDEX ix_event_memberships_account ON event_memberships (account_id, membership_status);

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

-- Recruitment conditions are separate from event publication and running dates.
CREATE TABLE event_recruitments (
    event_id uuid PRIMARY KEY REFERENCES events(id) ON DELETE RESTRICT,
    introduction text NOT NULL DEFAULT '',
    publication_status text NOT NULL DEFAULT 'DRAFT'
        CHECK (publication_status IN ('DRAFT', 'PUBLISHED')),
    closes_at timestamptz,
    target_booth_count integer CHECK (target_booth_count > 0),
    allowed_category_codes text[] NOT NULL DEFAULT '{}',
    participation_fee_krw bigint CHECK (participation_fee_krw >= 0),
    fee_note text NOT NULL DEFAULT '',
    revision bigint NOT NULL DEFAULT 0 CHECK (revision >= 0),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_recruitment_categories CHECK (
        CASE WHEN coalesce(array_ndims(allowed_category_codes), 1) <> 1 THEN false ELSE
            allowed_category_codes <@ ARRAY['FOOD', 'BEVERAGE', 'EXPERIENCE', 'GAME', 'GOODS', 'OTHER']::text[]
            AND array_position(allowed_category_codes, NULL) IS NULL
        END
    ),
    CONSTRAINT ck_published_recruitment_complete CHECK (
        publication_status <> 'PUBLISHED'
        OR (closes_at IS NOT NULL AND cardinality(allowed_category_codes) > 0)
    )
);

CREATE TABLE recruitment_document_requirements (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id uuid NOT NULL REFERENCES event_recruitments(event_id) ON DELETE RESTRICT,
    name text NOT NULL CHECK (btrim(name) <> ''),
    is_required boolean NOT NULL DEFAULT true,
    applicable_category_codes text[] NOT NULL DEFAULT '{}',
    sort_order integer NOT NULL DEFAULT 0 CHECK (sort_order >= 0),
    UNIQUE (id, event_id),
    UNIQUE (event_id, name),
    CONSTRAINT ck_document_categories CHECK (
        CASE WHEN coalesce(array_ndims(applicable_category_codes), 1) <> 1 THEN false ELSE
            applicable_category_codes <@ ARRAY['FOOD', 'BEVERAGE', 'EXPERIENCE', 'GAME', 'GOODS', 'OTHER']::text[]
            AND array_position(applicable_category_codes, NULL) IS NULL
        END
    )
);

CREATE TABLE event_application_invites (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id uuid NOT NULL REFERENCES event_recruitments(event_id) ON DELETE RESTRICT,
    token_hash text NOT NULL UNIQUE CHECK (token_hash ~ '^[0-9a-f]{64}$'),
    created_by uuid NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    created_at timestamptz NOT NULL DEFAULT now(),
    expires_at timestamptz NOT NULL,
    revoked_at timestamptz,
    CONSTRAINT ck_application_invite_period CHECK (
        expires_at > created_at AND (revoked_at IS NULL OR revoked_at >= created_at)
    )
);
CREATE UNIQUE INDEX ux_one_unrevoked_application_invite ON event_application_invites (event_id)
    WHERE revoked_at IS NULL;

-- 2. Reusable booth profiles, applications, and event-specific operation.
CREATE TABLE booth_profiles (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_account_id uuid NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    name text NOT NULL CHECK (btrim(name) <> ''),
    category_code text NOT NULL
        CHECK (category_code IN ('FOOD', 'BEVERAGE', 'EXPERIENCE', 'GAME', 'GOODS', 'OTHER')),
    introduction text NOT NULL DEFAULT '',
    revision bigint NOT NULL DEFAULT 0 CHECK (revision >= 0),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE booth_applications (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id uuid NOT NULL REFERENCES events(id) ON DELETE RESTRICT,
    applicant_account_id uuid NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    booth_profile_id uuid NOT NULL REFERENCES booth_profiles(id) ON DELETE RESTRICT,
    booth_name_snapshot text NOT NULL CHECK (btrim(booth_name_snapshot) <> ''),
    category_code_snapshot text NOT NULL
        CHECK (category_code_snapshot IN ('FOOD', 'BEVERAGE', 'EXPERIENCE', 'GAME', 'GOODS', 'OTHER')),
    introduction_snapshot text NOT NULL DEFAULT '',
    contact_name_snapshot text NOT NULL CHECK (btrim(contact_name_snapshot) <> ''),
    contact_email_snapshot text NOT NULL CHECK (btrim(contact_email_snapshot) <> ''),
    contact_phone_snapshot text NOT NULL CHECK (contact_phone_snapshot ~ '^[+]?[0-9]{8,15}$'),
    review_status text NOT NULL DEFAULT 'PENDING'
        CHECK (review_status IN ('PENDING', 'APPROVED', 'REJECTED')),
    review_note text,
    rejection_reason text,
    reviewed_by uuid REFERENCES accounts(id) ON DELETE RESTRICT,
    reviewed_at timestamptz,
    submitted_at timestamptz NOT NULL DEFAULT now(),
    revision bigint NOT NULL DEFAULT 0 CHECK (revision >= 0),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (id, event_id),
    CONSTRAINT ck_application_review CHECK (
        (review_status = 'PENDING' AND reviewed_by IS NULL AND reviewed_at IS NULL)
        OR (review_status IN ('APPROVED', 'REJECTED') AND reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL)
    ),
    CONSTRAINT ck_application_rejection_reason CHECK (
        (review_status = 'REJECTED' AND rejection_reason IS NOT NULL AND btrim(rejection_reason) <> '')
        OR (review_status <> 'REJECTED' AND rejection_reason IS NULL)
    )
);
CREATE INDEX ix_applications_event_status ON booth_applications (event_id, review_status, submitted_at DESC, id);
CREATE INDEX ix_applications_applicant ON booth_applications (applicant_account_id, submitted_at DESC, id);
CREATE UNIQUE INDEX ux_one_current_booth_application ON booth_applications (event_id, booth_profile_id)
    WHERE review_status IN ('PENDING', 'APPROVED');

CREATE TABLE application_document_checks (
    application_id uuid NOT NULL,
    requirement_id uuid NOT NULL,
    event_id uuid NOT NULL,
    check_status text NOT NULL DEFAULT 'NOT_SUBMITTED'
        CHECK (check_status IN ('NOT_SUBMITTED', 'SUBMITTED', 'VERIFIED', 'NEEDS_CORRECTION')),
    checked_by uuid REFERENCES accounts(id) ON DELETE RESTRICT,
    checked_at timestamptz,
    review_note text,
    revision bigint NOT NULL DEFAULT 0 CHECK (revision >= 0),
    updated_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (application_id, requirement_id),
    CONSTRAINT fk_document_check_application FOREIGN KEY (application_id, event_id)
        REFERENCES booth_applications(id, event_id) ON DELETE RESTRICT,
    CONSTRAINT fk_document_check_requirement FOREIGN KEY (requirement_id, event_id)
        REFERENCES recruitment_document_requirements(id, event_id) ON DELETE RESTRICT,
    CONSTRAINT ck_document_review CHECK (
        (check_status IN ('NOT_SUBMITTED', 'SUBMITTED') AND checked_by IS NULL AND checked_at IS NULL)
        OR (check_status IN ('VERIFIED', 'NEEDS_CORRECTION') AND checked_by IS NOT NULL AND checked_at IS NOT NULL)
    )
);

CREATE TABLE event_booths (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id uuid NOT NULL REFERENCES events(id) ON DELETE RESTRICT,
    application_id uuid UNIQUE,
    source text NOT NULL CHECK (source IN ('APPLICATION', 'MANUAL')),
    booth_code text NOT NULL CHECK (
        btrim(booth_code) <> '' AND booth_code = upper(btrim(booth_code)) AND length(booth_code) <= 20
    ),
    name text NOT NULL CHECK (btrim(name) <> ''),
    category_code text NOT NULL
        CHECK (category_code IN ('FOOD', 'BEVERAGE', 'EXPERIENCE', 'GAME', 'GOODS', 'OTHER')),
    description text NOT NULL DEFAULT '',
    operation_status text NOT NULL DEFAULT 'PREPARING'
        CHECK (operation_status IN ('PREPARING', 'OPEN', 'SOLD_OUT', 'CLOSED')),
    visibility_status text NOT NULL DEFAULT 'HIDDEN'
        CHECK (visibility_status IN ('HIDDEN', 'PUBLIC', 'ARCHIVED')),
    revision bigint NOT NULL DEFAULT 0 CHECK (revision >= 0),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (id, event_id),
    UNIQUE (event_id, booth_code),
    CONSTRAINT fk_booth_application FOREIGN KEY (application_id, event_id)
        REFERENCES booth_applications(id, event_id) ON DELETE RESTRICT,
    CONSTRAINT ck_booth_source CHECK (
        (source = 'APPLICATION' AND application_id IS NOT NULL)
        OR (source = 'MANUAL' AND application_id IS NULL)
    )
);
CREATE INDEX ix_booths_event_category ON event_booths (event_id, category_code);
CREATE INDEX ix_booths_event_operation ON event_booths (event_id, operation_status);

CREATE TABLE booth_memberships (
    event_booth_id uuid NOT NULL REFERENCES event_booths(id) ON DELETE RESTRICT,
    account_id uuid NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    membership_status text NOT NULL DEFAULT 'ACTIVE' CHECK (membership_status IN ('ACTIVE', 'REVOKED')),
    created_at timestamptz NOT NULL DEFAULT now(),
    revoked_at timestamptz,
    PRIMARY KEY (event_booth_id, account_id),
    CONSTRAINT ck_booth_membership_revoked CHECK (
        (membership_status = 'ACTIVE' AND revoked_at IS NULL)
        OR (membership_status = 'REVOKED' AND revoked_at IS NOT NULL)
    )
);
CREATE INDEX ix_booth_memberships_account ON booth_memberships (account_id, membership_status);

CREATE TABLE booth_items (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    event_booth_id uuid NOT NULL REFERENCES event_booths(id) ON DELETE RESTRICT,
    name text NOT NULL CHECK (btrim(name) <> ''),
    description text,
    price_krw bigint CHECK (price_krw >= 0),
    stock_status text NOT NULL DEFAULT 'AVAILABLE'
        CHECK (stock_status IN ('UNLIMITED', 'AVAILABLE', 'LOW', 'SOLD_OUT')),
    sort_order integer NOT NULL DEFAULT 0 CHECK (sort_order >= 0),
    revision bigint NOT NULL DEFAULT 0 CHECK (revision >= 0),
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
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (bucket, object_key),
    UNIQUE (id, event_id)
);

ALTER TABLE events ADD CONSTRAINT fk_event_poster FOREIGN KEY (poster_asset_id, id)
    REFERENCES media_assets(id, event_id) ON DELETE RESTRICT;

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
CREATE UNIQUE INDEX ux_one_draft_map ON floor_plans (event_id)
    WHERE publication_status = 'DRAFT';

CREATE TABLE map_pins (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id uuid NOT NULL,
    floor_plan_id uuid NOT NULL,
    event_booth_id uuid,
    pin_type text NOT NULL CHECK (pin_type IN ('BOOTH', 'TOILET', 'INFO', 'MEDICAL', 'OTHER_FACILITY')),
    label text NOT NULL CHECK (btrim(label) <> ''),
    x_ratio numeric(8,7) NOT NULL CHECK (x_ratio BETWEEN 0 AND 1),
    y_ratio numeric(8,7) NOT NULL CHECK (y_ratio BETWEEN 0 AND 1),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT fk_pin_map FOREIGN KEY (floor_plan_id, event_id)
        REFERENCES floor_plans(id, event_id) ON DELETE RESTRICT,
    CONSTRAINT fk_pin_booth FOREIGN KEY (event_booth_id, event_id)
        REFERENCES event_booths(id, event_id) ON DELETE RESTRICT,
    CONSTRAINT ck_facility_without_booth CHECK (pin_type = 'BOOTH' OR event_booth_id IS NULL)
);
CREATE UNIQUE INDEX ux_pin_booth_per_map ON map_pins (floor_plan_id, event_booth_id)
    WHERE event_booth_id IS NOT NULL;
CREATE INDEX ix_map_pins_floor_plan ON map_pins (floor_plan_id);

-- Supporting event notices, separate from private application information.
CREATE TABLE announcements (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id uuid NOT NULL REFERENCES events(id) ON DELETE RESTRICT,
    created_by uuid NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    title text NOT NULL DEFAULT '',
    body text NOT NULL DEFAULT '',
    image_asset_id uuid,
    is_urgent boolean NOT NULL DEFAULT false,
    publication_status text NOT NULL DEFAULT 'DRAFT'
        CHECK (publication_status IN ('DRAFT', 'PUBLISHED', 'DELETED')),
    published_at timestamptz,
    revision bigint NOT NULL DEFAULT 0 CHECK (revision >= 0),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT fk_announcement_image FOREIGN KEY (image_asset_id, event_id)
        REFERENCES media_assets(id, event_id) ON DELETE RESTRICT,
    CONSTRAINT ck_published_notice_complete CHECK (
        publication_status <> 'PUBLISHED'
        OR (published_at IS NOT NULL AND btrim(title) <> '' AND btrim(body) <> '')
    )
);
CREATE INDEX ix_announcements_event_publication ON announcements (event_id, publication_status, is_urgent DESC, published_at DESC);

CREATE TABLE announcement_audiences (
    announcement_id uuid NOT NULL REFERENCES announcements(id) ON DELETE RESTRICT,
    audience text NOT NULL CHECK (audience IN ('PUBLIC', 'OPERATORS', 'STAFF')),
    PRIMARY KEY (announcement_id, audience)
);
