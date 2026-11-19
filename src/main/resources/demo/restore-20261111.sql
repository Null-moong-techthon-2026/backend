-- Transaction and advisory lock are owned by DemoResetService.
-- Restore ONLY the dedicated demo event; accounts/passwords/ADMIN roles and other events stay intact.
SET LOCAL lock_timeout='5s';
SET LOCAL statement_timeout='30s';
LOCK TABLE app.accounts, app.local_credentials, app.organizations, app.organization_memberships, app.event_memberships, app.events, app.booth_profiles, app.event_recruitments, app.recruitment_document_requirements, app.recruitment_questions, app.event_application_invites, app.booth_applications, app.application_document_checks, app.application_documents, app.event_booths, app.booth_memberships, app.booth_items, app.media_assets, app.booth_photos, app.floor_plans, app.map_pins, app.announcements, app.announcement_audiences, app.announcement_attachments, app.announcement_attachment_links, app.staff_invitations, app.staff_duties, app.staff_members, app.staff_shifts, app.staff_shift_members, app.staff_attendance IN SHARE ROW EXCLUSIVE MODE;
DO $guard$
BEGIN
 IF (SELECT count(*) FROM app.flyway_schema_history WHERE success AND version='11')<>1 THEN RAISE EXCEPTION 'V11 required'; END IF;
 IF NOT EXISTS(SELECT 1 FROM app.events e JOIN app.organizations o ON o.id=e.organization_id WHERE e.id='d3e02026-1111-4000-8000-000000000200'::uuid AND o.id='d3e02026-1111-4000-8000-000000000100' AND o.contact_email='fixture-20261111-v1@demo.invalid') THEN RAISE EXCEPTION 'Dedicated demo marker mismatch'; END IF;
 IF (SELECT count(*) FROM app.accounts a JOIN app.local_credentials c ON c.account_id=a.id WHERE a.id IN (SELECT ('d3e02026-1111-4000-8000-'||lpad(n::text,12,'0'))::uuid FROM generate_series(1,11) n))<>11 THEN RAISE EXCEPTION 'Dedicated account set mismatch'; END IF;
 IF EXISTS(SELECT 1 FROM app.staff_members WHERE event_id='d3e02026-1111-4000-8000-000000000200'::uuid AND account_id NOT IN (SELECT ('d3e02026-1111-4000-8000-'||lpad(n::text,12,'0'))::uuid FROM generate_series(1,11) n))
 OR EXISTS(SELECT 1 FROM app.booth_applications WHERE event_id='d3e02026-1111-4000-8000-000000000200'::uuid AND applicant_account_id NOT IN (SELECT ('d3e02026-1111-4000-8000-'||lpad(n::text,12,'0'))::uuid FROM generate_series(1,11) n))
 OR EXISTS(SELECT 1 FROM app.booth_memberships WHERE event_booth_id IN(SELECT id FROM app.event_booths WHERE event_id='d3e02026-1111-4000-8000-000000000200'::uuid) AND account_id NOT IN (SELECT ('d3e02026-1111-4000-8000-'||lpad(n::text,12,'0'))::uuid FROM generate_series(1,11) n))
 OR EXISTS(SELECT 1 FROM app.booth_applications WHERE event_id<>'d3e02026-1111-4000-8000-000000000200'::uuid AND booth_profile_id IN(SELECT id FROM app.booth_profiles WHERE owner_account_id IN (SELECT ('d3e02026-1111-4000-8000-'||lpad(n::text,12,'0'))::uuid FROM generate_series(1,11) n)))
 THEN RAISE EXCEPTION 'Outside participant/profile reference: automatic reset refused'; END IF;
END $guard$;
DO $check$ BEGIN
 IF (SELECT count(*) FROM demoday_20261111.baseline)<>26 THEN RAISE EXCEPTION 'Incomplete baseline'; END IF;
 IF (SELECT jsonb_array_length(rows) FROM demoday_20261111.baseline WHERE table_name='events')<>1 THEN RAISE EXCEPTION 'Missing event baseline'; END IF;
END $check$;
CREATE TEMP TABLE demo_generation(value bigint) ON COMMIT DROP;
INSERT INTO demo_generation VALUES((extract(epoch FROM clock_timestamp())*1000000)::bigint);
INSERT INTO demoday_20261111.detached_files(bucket,object_key) SELECT bucket,object_key FROM app.media_assets WHERE event_id='d3e02026-1111-4000-8000-000000000200'::uuid AND id::text NOT IN (SELECT x->>'id' FROM demoday_20261111.baseline b,LATERAL jsonb_array_elements(b.rows) x WHERE table_name='media_assets') ON CONFLICT DO NOTHING;
INSERT INTO demoday_20261111.detached_files(bucket,object_key) SELECT bucket,object_key FROM app.application_documents WHERE event_id='d3e02026-1111-4000-8000-000000000200'::uuid AND id::text NOT IN (SELECT x->>'id' FROM demoday_20261111.baseline b,LATERAL jsonb_array_elements(b.rows) x WHERE table_name='application_documents') ON CONFLICT DO NOTHING;
INSERT INTO demoday_20261111.detached_files(bucket,object_key) SELECT bucket,object_key FROM app.announcement_attachments WHERE event_id='d3e02026-1111-4000-8000-000000000200'::uuid AND id::text NOT IN (SELECT x->>'id' FROM demoday_20261111.baseline b,LATERAL jsonb_array_elements(b.rows) x WHERE table_name='announcement_attachments') ON CONFLICT DO NOTHING;
UPDATE app.events SET poster_asset_id=NULL WHERE id='d3e02026-1111-4000-8000-000000000200'::uuid;
DELETE FROM app.staff_attendance WHERE member_id IN (SELECT id FROM app.staff_members WHERE event_id='d3e02026-1111-4000-8000-000000000200'::uuid);
DELETE FROM app.staff_shift_members WHERE event_id='d3e02026-1111-4000-8000-000000000200'::uuid;
DELETE FROM app.staff_shifts WHERE event_id='d3e02026-1111-4000-8000-000000000200'::uuid;
DELETE FROM app.staff_members WHERE event_id='d3e02026-1111-4000-8000-000000000200'::uuid;
DELETE FROM app.staff_duties WHERE event_id='d3e02026-1111-4000-8000-000000000200'::uuid;
DELETE FROM app.staff_invitations WHERE event_id='d3e02026-1111-4000-8000-000000000200'::uuid;
DELETE FROM app.announcement_attachment_links WHERE event_id='d3e02026-1111-4000-8000-000000000200'::uuid;
DELETE FROM app.announcement_attachments WHERE event_id='d3e02026-1111-4000-8000-000000000200'::uuid;
DELETE FROM app.announcement_audiences WHERE announcement_id IN (SELECT id FROM app.announcements WHERE event_id='d3e02026-1111-4000-8000-000000000200'::uuid);
DELETE FROM app.announcements WHERE event_id='d3e02026-1111-4000-8000-000000000200'::uuid;
DELETE FROM app.map_pins WHERE event_id='d3e02026-1111-4000-8000-000000000200'::uuid;
DELETE FROM app.floor_plans WHERE event_id='d3e02026-1111-4000-8000-000000000200'::uuid;
DELETE FROM app.booth_photos WHERE event_id='d3e02026-1111-4000-8000-000000000200'::uuid;
DELETE FROM app.media_assets WHERE event_id='d3e02026-1111-4000-8000-000000000200'::uuid;
DELETE FROM app.booth_items WHERE event_booth_id IN (SELECT id FROM app.event_booths WHERE event_id='d3e02026-1111-4000-8000-000000000200'::uuid);
DELETE FROM app.booth_memberships WHERE event_booth_id IN (SELECT id FROM app.event_booths WHERE event_id='d3e02026-1111-4000-8000-000000000200'::uuid);
DELETE FROM app.event_booths WHERE event_id='d3e02026-1111-4000-8000-000000000200'::uuid;
DELETE FROM app.application_documents WHERE event_id='d3e02026-1111-4000-8000-000000000200'::uuid;
DELETE FROM app.application_document_checks WHERE event_id='d3e02026-1111-4000-8000-000000000200'::uuid;
DELETE FROM app.booth_applications WHERE event_id='d3e02026-1111-4000-8000-000000000200'::uuid;
DELETE FROM app.event_application_invites WHERE event_id='d3e02026-1111-4000-8000-000000000200'::uuid;
DELETE FROM app.recruitment_questions WHERE event_id='d3e02026-1111-4000-8000-000000000200'::uuid;
DELETE FROM app.recruitment_document_requirements WHERE event_id='d3e02026-1111-4000-8000-000000000200'::uuid;
DELETE FROM app.event_recruitments WHERE event_id='d3e02026-1111-4000-8000-000000000200'::uuid;
DELETE FROM app.booth_profiles WHERE owner_account_id IN (SELECT ('d3e02026-1111-4000-8000-'||lpad(n::text,12,'0'))::uuid FROM generate_series(1,11) n);
INSERT INTO app.booth_profiles SELECT (jsonb_populate_record(NULL::app.booth_profiles,CASE WHEN x ? 'revision' THEN x || jsonb_build_object('revision',(SELECT value FROM demo_generation)) ELSE x END)).* FROM demoday_20261111.baseline b,LATERAL jsonb_array_elements(b.rows) x WHERE table_name='booth_profiles';
INSERT INTO app.event_recruitments SELECT (jsonb_populate_record(NULL::app.event_recruitments,CASE WHEN x ? 'revision' THEN x || jsonb_build_object('revision',(SELECT value FROM demo_generation)) ELSE x END)).* FROM demoday_20261111.baseline b,LATERAL jsonb_array_elements(b.rows) x WHERE table_name='event_recruitments';
INSERT INTO app.recruitment_document_requirements SELECT (jsonb_populate_record(NULL::app.recruitment_document_requirements,CASE WHEN x ? 'revision' THEN x || jsonb_build_object('revision',(SELECT value FROM demo_generation)) ELSE x END)).* FROM demoday_20261111.baseline b,LATERAL jsonb_array_elements(b.rows) x WHERE table_name='recruitment_document_requirements';
INSERT INTO app.recruitment_questions SELECT (jsonb_populate_record(NULL::app.recruitment_questions,CASE WHEN x ? 'revision' THEN x || jsonb_build_object('revision',(SELECT value FROM demo_generation)) ELSE x END)).* FROM demoday_20261111.baseline b,LATERAL jsonb_array_elements(b.rows) x WHERE table_name='recruitment_questions';
INSERT INTO app.event_application_invites SELECT (jsonb_populate_record(NULL::app.event_application_invites,CASE WHEN x ? 'revision' THEN x || jsonb_build_object('revision',(SELECT value FROM demo_generation)) ELSE x END)).* FROM demoday_20261111.baseline b,LATERAL jsonb_array_elements(b.rows) x WHERE table_name='event_application_invites';
INSERT INTO app.booth_applications SELECT (jsonb_populate_record(NULL::app.booth_applications,CASE WHEN x ? 'revision' THEN x || jsonb_build_object('revision',(SELECT value FROM demo_generation)) ELSE x END)).* FROM demoday_20261111.baseline b,LATERAL jsonb_array_elements(b.rows) x WHERE table_name='booth_applications';
INSERT INTO app.application_document_checks SELECT (jsonb_populate_record(NULL::app.application_document_checks,CASE WHEN x ? 'revision' THEN x || jsonb_build_object('revision',(SELECT value FROM demo_generation)) ELSE x END)).* FROM demoday_20261111.baseline b,LATERAL jsonb_array_elements(b.rows) x WHERE table_name='application_document_checks';
INSERT INTO app.application_documents SELECT (jsonb_populate_record(NULL::app.application_documents,CASE WHEN x ? 'revision' THEN x || jsonb_build_object('revision',(SELECT value FROM demo_generation)) ELSE x END)).* FROM demoday_20261111.baseline b,LATERAL jsonb_array_elements(b.rows) x WHERE table_name='application_documents';
INSERT INTO app.event_booths SELECT (jsonb_populate_record(NULL::app.event_booths,CASE WHEN x ? 'revision' THEN x || jsonb_build_object('revision',(SELECT value FROM demo_generation)) ELSE x END)).* FROM demoday_20261111.baseline b,LATERAL jsonb_array_elements(b.rows) x WHERE table_name='event_booths';
INSERT INTO app.booth_memberships SELECT (jsonb_populate_record(NULL::app.booth_memberships,CASE WHEN x ? 'revision' THEN x || jsonb_build_object('revision',(SELECT value FROM demo_generation)) ELSE x END)).* FROM demoday_20261111.baseline b,LATERAL jsonb_array_elements(b.rows) x WHERE table_name='booth_memberships';
INSERT INTO app.booth_items SELECT (jsonb_populate_record(NULL::app.booth_items,CASE WHEN x ? 'revision' THEN x || jsonb_build_object('revision',(SELECT value FROM demo_generation)) ELSE x END)).* FROM demoday_20261111.baseline b,LATERAL jsonb_array_elements(b.rows) x WHERE table_name='booth_items';
INSERT INTO app.media_assets SELECT (jsonb_populate_record(NULL::app.media_assets,CASE WHEN x ? 'revision' THEN x || jsonb_build_object('revision',(SELECT value FROM demo_generation)) ELSE x END)).* FROM demoday_20261111.baseline b,LATERAL jsonb_array_elements(b.rows) x WHERE table_name='media_assets';
INSERT INTO app.booth_photos SELECT (jsonb_populate_record(NULL::app.booth_photos,CASE WHEN x ? 'revision' THEN x || jsonb_build_object('revision',(SELECT value FROM demo_generation)) ELSE x END)).* FROM demoday_20261111.baseline b,LATERAL jsonb_array_elements(b.rows) x WHERE table_name='booth_photos';
INSERT INTO app.floor_plans SELECT (jsonb_populate_record(NULL::app.floor_plans,CASE WHEN x ? 'revision' THEN x || jsonb_build_object('revision',(SELECT value FROM demo_generation)) ELSE x END)).* FROM demoday_20261111.baseline b,LATERAL jsonb_array_elements(b.rows) x WHERE table_name='floor_plans';
INSERT INTO app.map_pins SELECT (jsonb_populate_record(NULL::app.map_pins,CASE WHEN x ? 'revision' THEN x || jsonb_build_object('revision',(SELECT value FROM demo_generation)) ELSE x END)).* FROM demoday_20261111.baseline b,LATERAL jsonb_array_elements(b.rows) x WHERE table_name='map_pins';
INSERT INTO app.announcements SELECT (jsonb_populate_record(NULL::app.announcements,CASE WHEN x ? 'revision' THEN x || jsonb_build_object('revision',(SELECT value FROM demo_generation)) ELSE x END)).* FROM demoday_20261111.baseline b,LATERAL jsonb_array_elements(b.rows) x WHERE table_name='announcements';
INSERT INTO app.announcement_audiences SELECT (jsonb_populate_record(NULL::app.announcement_audiences,CASE WHEN x ? 'revision' THEN x || jsonb_build_object('revision',(SELECT value FROM demo_generation)) ELSE x END)).* FROM demoday_20261111.baseline b,LATERAL jsonb_array_elements(b.rows) x WHERE table_name='announcement_audiences';
INSERT INTO app.announcement_attachments SELECT (jsonb_populate_record(NULL::app.announcement_attachments,CASE WHEN x ? 'revision' THEN x || jsonb_build_object('revision',(SELECT value FROM demo_generation)) ELSE x END)).* FROM demoday_20261111.baseline b,LATERAL jsonb_array_elements(b.rows) x WHERE table_name='announcement_attachments';
INSERT INTO app.announcement_attachment_links SELECT (jsonb_populate_record(NULL::app.announcement_attachment_links,CASE WHEN x ? 'revision' THEN x || jsonb_build_object('revision',(SELECT value FROM demo_generation)) ELSE x END)).* FROM demoday_20261111.baseline b,LATERAL jsonb_array_elements(b.rows) x WHERE table_name='announcement_attachment_links';
INSERT INTO app.staff_invitations SELECT (jsonb_populate_record(NULL::app.staff_invitations,CASE WHEN x ? 'revision' THEN x || jsonb_build_object('revision',(SELECT value FROM demo_generation)) ELSE x END)).* FROM demoday_20261111.baseline b,LATERAL jsonb_array_elements(b.rows) x WHERE table_name='staff_invitations';
INSERT INTO app.staff_duties SELECT (jsonb_populate_record(NULL::app.staff_duties,CASE WHEN x ? 'revision' THEN x || jsonb_build_object('revision',(SELECT value FROM demo_generation)) ELSE x END)).* FROM demoday_20261111.baseline b,LATERAL jsonb_array_elements(b.rows) x WHERE table_name='staff_duties';
INSERT INTO app.staff_members SELECT (jsonb_populate_record(NULL::app.staff_members,CASE WHEN x ? 'revision' THEN x || jsonb_build_object('revision',(SELECT value FROM demo_generation)) ELSE x END)).* FROM demoday_20261111.baseline b,LATERAL jsonb_array_elements(b.rows) x WHERE table_name='staff_members';
INSERT INTO app.staff_shifts SELECT (jsonb_populate_record(NULL::app.staff_shifts,CASE WHEN x ? 'revision' THEN x || jsonb_build_object('revision',(SELECT value FROM demo_generation)) ELSE x END)).* FROM demoday_20261111.baseline b,LATERAL jsonb_array_elements(b.rows) x WHERE table_name='staff_shifts';
INSERT INTO app.staff_shift_members SELECT (jsonb_populate_record(NULL::app.staff_shift_members,CASE WHEN x ? 'revision' THEN x || jsonb_build_object('revision',(SELECT value FROM demo_generation)) ELSE x END)).* FROM demoday_20261111.baseline b,LATERAL jsonb_array_elements(b.rows) x WHERE table_name='staff_shift_members';
INSERT INTO app.staff_attendance SELECT (jsonb_populate_record(NULL::app.staff_attendance,CASE WHEN x ? 'revision' THEN x || jsonb_build_object('revision',(SELECT value FROM demo_generation)) ELSE x END)).* FROM demoday_20261111.baseline b,LATERAL jsonb_array_elements(b.rows) x WHERE table_name='staff_attendance';
DO $event$
DECLARE cols text;
BEGIN
 SELECT string_agg(quote_ident(column_name),',' ORDER BY ordinal_position) INTO cols FROM information_schema.columns WHERE table_schema='app' AND table_name='events' AND column_name NOT IN ('id','organization_id','created_by');
 EXECUTE format('UPDATE app.events SET (%s)=(SELECT %s FROM jsonb_populate_record(NULL::app.events,(SELECT rows->0 || jsonb_build_object(''revision'',(SELECT value FROM demo_generation)) FROM demoday_20261111.baseline WHERE table_name=''events''))) WHERE id=''d3e02026-1111-4000-8000-000000000200''',cols,cols);
END $event$;
-- Refresh open demo intervals to the current KST day; do not fabricate self-clock-in for the presentation worker.
UPDATE app.staff_attendance SET attendance_date=(now() AT TIME ZONE 'Asia/Seoul')::date,clock_in=greatest(date_trunc('day',now() AT TIME ZONE 'Asia/Seoul') AT TIME ZONE 'Asia/Seoul',now()-interval '1 hour')
 WHERE member_id IN ('d3e02026-1111-4000-8000-000000001007','d3e02026-1111-4000-8000-000000001008') AND clock_out IS NULL;
INSERT INTO demoday_20261111.reset_log DEFAULT VALUES;
