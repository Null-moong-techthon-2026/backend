-- Event-opening applications ("행사 개설 신청") and the platform administrator role.
SET LOCAL search_path = app, pg_catalog;

-- A platform administrator reviews applications. There is no sign-up path to ADMIN: an operator
-- sets it by hand, e.g.  UPDATE app.accounts SET platform_role = 'ADMIN' WHERE id = '...';
ALTER TABLE accounts
    ADD COLUMN platform_role text NOT NULL DEFAULT 'USER'
        CHECK (platform_role IN ('USER', 'ADMIN'));

CREATE TABLE event_applications (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    applicant_account_id uuid NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    status text NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),

    -- organization
    org_name text NOT NULL CHECK (btrim(org_name) <> ''),
    org_type text NOT NULL CHECK (org_type IN ('COMPANY', 'STUDENT', 'PUBLIC', 'NONPROFIT', 'OTHER')),
    org_email text NOT NULL CHECK (btrim(org_email) <> ''),
    business_number text NOT NULL DEFAULT '',
    affiliation text NOT NULL DEFAULT '',
    representative text NOT NULL DEFAULT '',
    website text NOT NULL DEFAULT '',

    -- contact person
    contact_name text NOT NULL CHECK (btrim(contact_name) <> ''),
    contact_email text NOT NULL CHECK (btrim(contact_email) <> ''),
    contact_phone text NOT NULL CHECK (contact_phone ~ '^[+]?[0-9]{8,15}$'),
    contact_role text NOT NULL CHECK (btrim(contact_role) <> ''),

    -- event
    event_name text NOT NULL CHECK (btrim(event_name) <> ''),
    event_kind text NOT NULL CHECK (event_kind IN ('UNIVERSITY', 'FLEA', 'POPUP', 'EXPO', 'LOCAL', 'OTHER')),
    starts_on date NOT NULL,
    ends_on date NOT NULL,
    venue text NOT NULL CHECK (btrim(venue) <> ''),
    expected_booth_count integer NOT NULL CHECK (expected_booth_count >= 1),
    handover boolean NOT NULL DEFAULT false,

    -- fee and agreements (payment itself is not processed here)
    payment_plan text NOT NULL CHECK (payment_plan IN ('PREPAID', 'BASE_PLUS_SLOT')),
    payment_method text NOT NULL CHECK (payment_method IN ('CARD', 'TRANSFER', 'OTHER')),
    receipt_type text NOT NULL CHECK (receipt_type IN ('TAX_INVOICE', 'CASH_RECEIPT', 'NONE')),
    agreed_terms text[] NOT NULL,

    -- review
    submitted_at timestamptz NOT NULL DEFAULT now(),
    reviewed_at timestamptz,
    reviewed_by uuid REFERENCES accounts(id) ON DELETE RESTRICT,
    rejection_reason text NOT NULL DEFAULT '',
    created_organization_id uuid REFERENCES organizations(id) ON DELETE RESTRICT,
    created_event_id uuid REFERENCES events(id) ON DELETE RESTRICT,
    revision bigint NOT NULL DEFAULT 0 CHECK (revision >= 0),
    updated_at timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT ck_event_application_period CHECK (ends_on >= starts_on),
    CONSTRAINT ck_event_application_review CHECK (
        (status = 'PENDING' AND reviewed_at IS NULL AND reviewed_by IS NULL)
        OR (status <> 'PENDING' AND reviewed_at IS NOT NULL AND reviewed_by IS NOT NULL)
    ),
    CONSTRAINT ck_event_application_approved CHECK (
        status <> 'APPROVED' OR (created_organization_id IS NOT NULL AND created_event_id IS NOT NULL)
    ),
    CONSTRAINT ck_event_application_rejected CHECK (
        status <> 'REJECTED' OR btrim(rejection_reason) <> ''
    )
);

-- An account can have only one application waiting for review at a time.
CREATE UNIQUE INDEX uq_event_applications_one_pending
    ON event_applications (applicant_account_id) WHERE status = 'PENDING';
CREATE INDEX ix_event_applications_status ON event_applications (status, submitted_at DESC);
CREATE INDEX ix_event_applications_applicant ON event_applications (applicant_account_id, submitted_at DESC);
