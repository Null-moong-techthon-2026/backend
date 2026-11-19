-- Add history snapshots without deleting or changing existing attendance/assignment rows.
ALTER TABLE app.staff_duties ADD COLUMN revision bigint NOT NULL DEFAULT 0 CHECK (revision>=0);
ALTER TABLE app.staff_shifts ADD COLUMN duty_zone_snapshot varchar(100);
ALTER TABLE app.staff_shifts ADD COLUMN duty_name_snapshot varchar(100);
ALTER TABLE app.staff_shift_members ADD COLUMN role_at_assignment varchar(20)
    CHECK (role_at_assignment IN ('STAFF','ADMIN','LABOR','VOLUNTEER'));
-- Legacy history has no previous snapshots: use the currently recorded values, not invented historical values.
UPDATE app.staff_shifts s SET duty_zone_snapshot=d.zone,duty_name_snapshot=d.name
    FROM app.staff_duties d WHERE d.id=s.duty_id AND d.event_id=s.event_id;
UPDATE app.staff_shift_members sm SET role_at_assignment=m.role
    FROM app.staff_members m WHERE m.id=sm.member_id AND m.event_id=sm.event_id;
