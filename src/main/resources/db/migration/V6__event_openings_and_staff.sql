ALTER TABLE app.accounts ADD COLUMN onboarding_type varchar(20) NOT NULL DEFAULT 'OPERATOR'
    CHECK (onboarding_type IN ('ORGANIZER','OPERATOR','STAFF'));
-- Old signup types were not persisted. Only infer organizer for an existing active owner.
UPDATE app.accounts a SET onboarding_type='ORGANIZER' WHERE EXISTS
    (SELECT 1 FROM app.organization_memberships m WHERE m.account_id=a.id AND m.role='OWNER' AND m.membership_status='ACTIVE');
ALTER TABLE app.organizations ADD COLUMN org_type varchar(30);

CREATE TABLE app.staff_invitations (
    id uuid PRIMARY KEY, event_id uuid NOT NULL REFERENCES app.events(id),
    code varchar(40) NOT NULL UNIQUE, link_token varchar(100) NOT NULL UNIQUE,
    expires_at timestamptz NOT NULL, revoked_at timestamptz, created_at timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_staff_invitation_active ON app.staff_invitations(event_id) WHERE revoked_at IS NULL;
CREATE TABLE app.staff_duties (
    id uuid PRIMARY KEY, event_id uuid NOT NULL REFERENCES app.events(id),
    zone varchar(100) NOT NULL CHECK (btrim(zone)<>''), name varchar(100) NOT NULL CHECK (btrim(name)<>''),
    UNIQUE(id,event_id), UNIQUE(event_id,zone,name)
);
CREATE TABLE app.staff_members (
    id uuid PRIMARY KEY, event_id uuid NOT NULL REFERENCES app.events(id),
    account_id uuid NOT NULL REFERENCES app.accounts(id),
    role varchar(20) NOT NULL CHECK (role IN ('STAFF','ADMIN','LABOR','VOLUNTEER')),
    status varchar(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','WAITING','WORKING','BREAK','ABSENT','REJECTED')),
    affiliation varchar(200) NOT NULL DEFAULT '', memo varchar(2000) NOT NULL DEFAULT '', duty_id uuid,
    revision bigint NOT NULL DEFAULT 0 CHECK (revision>=0), created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE(event_id,account_id), UNIQUE(id,event_id),
    FOREIGN KEY (duty_id,event_id) REFERENCES app.staff_duties(id,event_id)
);
CREATE INDEX ix_staff_members_account ON app.staff_members(account_id,event_id);
CREATE TABLE app.staff_shifts (
    id uuid PRIMARY KEY, event_id uuid NOT NULL REFERENCES app.events(id),
    shift_date date NOT NULL, duty_id uuid NOT NULL, starts_at time NOT NULL, ends_at time NOT NULL,
    roles text[] NOT NULL CHECK (cardinality(roles)>0 AND roles <@ ARRAY['STAFF','ADMIN','LABOR','VOLUNTEER']::text[]),
    revision bigint NOT NULL DEFAULT 0 CHECK(revision>=0), UNIQUE(id,event_id),
    CHECK (ends_at>starts_at), FOREIGN KEY(duty_id,event_id) REFERENCES app.staff_duties(id,event_id)
);
CREATE INDEX ix_staff_shifts_day ON app.staff_shifts(event_id,shift_date,starts_at);
CREATE TABLE app.staff_shift_members (
    event_id uuid NOT NULL, shift_id uuid NOT NULL, member_id uuid NOT NULL,
    PRIMARY KEY(shift_id,member_id),
    FOREIGN KEY(shift_id,event_id) REFERENCES app.staff_shifts(id,event_id),
    FOREIGN KEY(member_id,event_id) REFERENCES app.staff_members(id,event_id)
);
CREATE INDEX ix_staff_shift_members_member ON app.staff_shift_members(member_id);
CREATE TABLE app.staff_attendance (
    member_id uuid NOT NULL REFERENCES app.staff_members(id), attendance_date date NOT NULL,
    clock_in timestamptz NOT NULL, clock_out timestamptz,
    PRIMARY KEY(member_id,attendance_date), CHECK(clock_out IS NULL OR clock_out>=clock_in)
);
CREATE UNIQUE INDEX uq_staff_attendance_open ON app.staff_attendance(member_id) WHERE clock_out IS NULL;

-- Existing actual event managers appear in the roster; the roster ADMIN label itself grants no management permission.
INSERT INTO app.staff_members(id,event_id,account_id,role,status)
SELECT gen_random_uuid(),e.id,m.account_id,'ADMIN','WAITING'
FROM app.events e JOIN app.organization_memberships m ON m.organization_id=e.organization_id
JOIN app.accounts a ON a.id=m.account_id
WHERE m.membership_status='ACTIVE' AND a.account_status='ACTIVE' AND (m.role='OWNER' OR EXISTS
    (SELECT 1 FROM app.event_memberships em WHERE em.event_id=e.id AND em.account_id=m.account_id AND em.role='MANAGER' AND em.membership_status='ACTIVE'))
ON CONFLICT(event_id,account_id) DO NOTHING;
