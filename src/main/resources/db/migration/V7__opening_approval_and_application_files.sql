-- Preserve deployed V1..V6 and all existing events/demo data.
ALTER TABLE app.event_applications ADD COLUMN starts_at timestamptz, ADD COLUMN ends_at timestamptz;
UPDATE app.event_applications SET starts_at=starts_on::timestamp AT TIME ZONE 'Asia/Seoul',
    ends_at=(ends_on::timestamp + interval '23 hours 59 minutes 59 seconds') AT TIME ZONE 'Asia/Seoul';
ALTER TABLE app.event_applications ALTER COLUMN starts_at SET NOT NULL, ALTER COLUMN ends_at SET NOT NULL;
ALTER TABLE app.event_applications ADD CONSTRAINT ck_opening_instants CHECK(ends_at>starts_at);
-- The current opening form does not collect these legacy fields. Do not fabricate agreements/payment details.
ALTER TABLE app.event_applications
    ALTER COLUMN org_email DROP NOT NULL,
    ALTER COLUMN contact_name DROP NOT NULL, ALTER COLUMN contact_email DROP NOT NULL,
    ALTER COLUMN contact_phone DROP NOT NULL, ALTER COLUMN contact_role DROP NOT NULL,
    ALTER COLUMN event_kind DROP NOT NULL, ALTER COLUMN expected_booth_count DROP NOT NULL,
    ALTER COLUMN payment_plan DROP NOT NULL, ALTER COLUMN payment_method DROP NOT NULL,
    ALTER COLUMN receipt_type DROP NOT NULL, ALTER COLUMN agreed_terms DROP NOT NULL;

CREATE TABLE app.application_documents (
    id uuid PRIMARY KEY,
    event_id uuid NOT NULL,
    application_id uuid NOT NULL,
    requirement_id uuid NOT NULL,
    uploaded_by uuid NOT NULL REFERENCES app.accounts(id) ON DELETE RESTRICT,
    file_name varchar(255) NOT NULL CHECK(btrim(file_name)<>''),
    mime_type varchar(100) NOT NULL CHECK(mime_type IN ('application/pdf','image/png','image/jpeg')),
    size_bytes bigint NOT NULL CHECK(size_bytes BETWEEN 1 AND 10485760),
    bucket text NOT NULL CHECK(btrim(bucket)<>''), object_key text NOT NULL CHECK(btrim(object_key)<>''),
    uploaded_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE(application_id,requirement_id), UNIQUE(bucket,object_key),
    FOREIGN KEY(application_id,event_id) REFERENCES app.booth_applications(id,event_id) ON DELETE RESTRICT,
    FOREIGN KEY(requirement_id,event_id) REFERENCES app.recruitment_document_requirements(id,event_id) ON DELETE RESTRICT,
    FOREIGN KEY(application_id,requirement_id) REFERENCES app.application_document_checks(application_id,requirement_id) ON DELETE RESTRICT
);
CREATE INDEX ix_application_documents_event ON app.application_documents(event_id);
