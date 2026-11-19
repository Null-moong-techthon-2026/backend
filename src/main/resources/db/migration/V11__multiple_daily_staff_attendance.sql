-- Keep every existing timestamp; give each work interval its own identity.
ALTER TABLE app.staff_attendance ADD COLUMN id uuid NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE app.staff_attendance DROP CONSTRAINT staff_attendance_pkey;
ALTER TABLE app.staff_attendance ADD PRIMARY KEY (id);
-- uq_staff_attendance_open still prevents two simultaneously open intervals per member.
CREATE INDEX ix_staff_attendance_member_date ON app.staff_attendance(member_id,attendance_date,clock_in DESC,id DESC);
