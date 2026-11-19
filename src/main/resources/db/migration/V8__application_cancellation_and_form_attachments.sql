-- Additive upgrade; keep all existing applications, memberships and demo records.
ALTER TABLE app.booth_applications DROP CONSTRAINT booth_applications_review_status_check;
ALTER TABLE app.booth_applications DROP CONSTRAINT ck_application_review;
ALTER TABLE app.booth_applications
    ADD COLUMN cancelled_at timestamptz,
    ADD COLUMN cancelled_by uuid REFERENCES app.accounts(id) ON DELETE RESTRICT,
    ADD COLUMN cancel_reason varchar(2000),
    ADD COLUMN affiliation varchar(200),
    ADD COLUMN bank_name varchar(100),
    ADD COLUMN account_number varchar(100),
    ADD COLUMN account_holder varchar(100),
    ADD COLUMN sale_items varchar(5000),
    ADD COLUMN required_facilities varchar(5000),
    ADD COLUMN additional_answers jsonb NOT NULL DEFAULT '[]'::jsonb,
    ADD CONSTRAINT ck_application_status CHECK(review_status IN ('PENDING','APPROVED','REJECTED','CANCELLED')),
    ADD CONSTRAINT ck_application_review CHECK(
        (review_status='PENDING' AND reviewed_by IS NULL AND reviewed_at IS NULL)
        OR (review_status IN ('APPROVED','REJECTED','CANCELLED') AND reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL)),
    ADD CONSTRAINT ck_application_cancellation CHECK(
        (review_status='CANCELLED' AND cancelled_at IS NOT NULL AND cancelled_by IS NOT NULL)
        OR (review_status<>'CANCELLED' AND cancelled_at IS NULL AND cancelled_by IS NULL AND cancel_reason IS NULL)),
    ADD CONSTRAINT ck_application_answers_array CHECK(jsonb_typeof(additional_answers)='array');

ALTER TABLE app.event_recruitments ADD COLUMN contact_phone varchar(30);
CREATE TABLE app.recruitment_questions (
    id uuid PRIMARY KEY,
    event_id uuid NOT NULL REFERENCES app.events(id) ON DELETE RESTRICT,
    question varchar(1000) NOT NULL CHECK(btrim(question)<>''),
    is_required boolean NOT NULL,
    sort_order integer NOT NULL CHECK(sort_order>=0),
    UNIQUE(id,event_id)
);
CREATE INDEX ix_recruitment_questions_event ON app.recruitment_questions(event_id,sort_order,id);

CREATE TABLE app.announcement_attachments (
    id uuid PRIMARY KEY,
    event_id uuid NOT NULL REFERENCES app.events(id) ON DELETE RESTRICT,
    uploaded_by uuid NOT NULL REFERENCES app.accounts(id) ON DELETE RESTRICT,
    file_name varchar(255) NOT NULL CHECK(btrim(file_name)<>''),
    mime_type varchar(100) NOT NULL CHECK(mime_type IN ('application/pdf','image/png','image/jpeg')),
    size_bytes bigint NOT NULL CHECK(size_bytes BETWEEN 1 AND 10485760),
    bucket text NOT NULL CHECK(btrim(bucket)<>''),
    object_key text NOT NULL CHECK(btrim(object_key)<>''),
    uploaded_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE(id,event_id), UNIQUE(bucket,object_key)
);
ALTER TABLE app.announcements ADD CONSTRAINT uq_announcements_id_event UNIQUE(id,event_id);
CREATE TABLE app.announcement_attachment_links (
    announcement_id uuid NOT NULL,
    attachment_id uuid NOT NULL,
    event_id uuid NOT NULL,
    sort_order integer NOT NULL CHECK(sort_order BETWEEN 0 AND 4),
    PRIMARY KEY(announcement_id,attachment_id),
    UNIQUE(announcement_id,sort_order),
    FOREIGN KEY(announcement_id,event_id) REFERENCES app.announcements(id,event_id) ON DELETE RESTRICT,
    FOREIGN KEY(attachment_id,event_id) REFERENCES app.announcement_attachments(id,event_id) ON DELETE RESTRICT
);
CREATE INDEX ix_attachment_links_asset ON app.announcement_attachment_links(attachment_id);
