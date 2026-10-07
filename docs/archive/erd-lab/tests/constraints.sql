-- Run with psql -v ON_ERROR_STOP=1 after schema.sql in a fresh review database.
-- Every fixture and helper below is rolled back; this is not demo seed data.
BEGIN;
SET LOCAL search_path = erd, pg_catalog;

DO $$
BEGIN
    IF current_database() <> 'boothrock_erd' THEN
        RAISE EXCEPTION 'Run only in boothrock_erd';
    END IF;
END;
$$;

CREATE FUNCTION pg_temp.test_id(n integer) RETURNS uuid LANGUAGE sql IMMUTABLE AS $$
    SELECT ('00000000-0000-0000-0000-' || lpad(n::text, 12, '0'))::uuid;
$$;

CREATE FUNCTION pg_temp.expect_violation(statement text, expected_state text)
RETURNS void LANGUAGE plpgsql AS $fn$
DECLARE
    actual_state text;
BEGIN
    BEGIN
        EXECUTE statement;
    EXCEPTION WHEN OTHERS THEN
        GET STACKED DIAGNOSTICS actual_state = RETURNED_SQLSTATE;
        IF actual_state <> expected_state THEN
            RAISE EXCEPTION 'Expected SQLSTATE %, got % for %', expected_state, actual_state, statement;
        END IF;
        RETURN;
    END;
    RAISE EXCEPTION 'Expected SQLSTATE %, but statement succeeded: %', expected_state, statement;
END;
$fn$;

INSERT INTO accounts (id, nickname, phone_number, email) VALUES
    (pg_temp.test_id(1), 'Owner A', '01000000000', 'owner-a@example.com'),
    (pg_temp.test_id(2), 'Owner B', '01000000001', 'owner-b@example.com'),
    (pg_temp.test_id(3), 'Operator', '01000000002', 'operator@example.com');
INSERT INTO organizations (id, name, created_by) VALUES
    (pg_temp.test_id(101), 'Organization A', pg_temp.test_id(1)),
    (pg_temp.test_id(102), 'Organization B', pg_temp.test_id(2));
INSERT INTO organization_memberships (organization_id, account_id, role) VALUES
    (pg_temp.test_id(101), pg_temp.test_id(1), 'OWNER'),
    (pg_temp.test_id(102), pg_temp.test_id(2), 'OWNER');
INSERT INTO events (id, organization_id, created_by, name) VALUES
    (pg_temp.test_id(201), pg_temp.test_id(101), pg_temp.test_id(1), 'Event A'),
    (pg_temp.test_id(202), pg_temp.test_id(102), pg_temp.test_id(2), 'Event B');
INSERT INTO event_recruitments (event_id) VALUES (pg_temp.test_id(201)), (pg_temp.test_id(202));
UPDATE event_recruitments SET publication_status = 'PUBLISHED', closes_at = now() + interval '7 days',
    allowed_category_codes = ARRAY['FOOD', 'BEVERAGE'] WHERE event_id = pg_temp.test_id(201);
INSERT INTO recruitment_document_requirements (id, event_id, name) VALUES
    (pg_temp.test_id(301), pg_temp.test_id(201), 'Operating plan'),
    (pg_temp.test_id(302), pg_temp.test_id(202), 'Operating plan');
INSERT INTO booth_profiles (id, owner_account_id, name, category_code) VALUES
    (pg_temp.test_id(401), pg_temp.test_id(3), 'Test booth', 'FOOD');
INSERT INTO booth_applications (id, event_id, applicant_account_id, booth_profile_id,
    booth_name_snapshot, category_code_snapshot, contact_name_snapshot, contact_email_snapshot, contact_phone_snapshot)
SELECT pg_temp.test_id(n), pg_temp.test_id(n - 300), pg_temp.test_id(3), pg_temp.test_id(401),
    'Original booth name', 'FOOD', 'Contact', 'contact@example.com', '01000000002'
FROM generate_series(501, 502) AS n;
UPDATE booth_applications SET review_status = 'APPROVED', reviewed_by = pg_temp.test_id(1), reviewed_at = now()
    WHERE id = pg_temp.test_id(501);
INSERT INTO application_document_checks (application_id, requirement_id, event_id, check_status, checked_by, checked_at)
VALUES (pg_temp.test_id(501), pg_temp.test_id(301), pg_temp.test_id(201), 'VERIFIED', pg_temp.test_id(1), now());
INSERT INTO event_booths (id, event_id, source, booth_code, name, category_code) VALUES
    (pg_temp.test_id(601), pg_temp.test_id(201), 'MANUAL', 'B001', 'Manual A', 'FOOD'),
    (pg_temp.test_id(602), pg_temp.test_id(202), 'MANUAL', 'B001', 'Manual B', 'FOOD');
INSERT INTO event_booths (id, event_id, application_id, source, booth_code, name, category_code)
VALUES (pg_temp.test_id(603), pg_temp.test_id(201), pg_temp.test_id(501), 'APPLICATION', 'B002', 'Approved', 'FOOD');
INSERT INTO booth_memberships (event_booth_id, account_id) VALUES (pg_temp.test_id(603), pg_temp.test_id(3));
INSERT INTO booth_items (event_booth_id, name, stock_status) VALUES (pg_temp.test_id(603), 'Item', 'LOW');
INSERT INTO media_assets (id, event_id, uploaded_by, bucket, object_key, mime_type, size_bytes, width_px, height_px) VALUES
    (pg_temp.test_id(701), pg_temp.test_id(201), pg_temp.test_id(1), 'private', 'event-a/map.png', 'image/png', 1000, 800, 600),
    (pg_temp.test_id(702), pg_temp.test_id(202), pg_temp.test_id(2), 'private', 'event-b/map.png', 'image/png', 1000, 800, 600);
UPDATE events SET poster_asset_id = pg_temp.test_id(701) WHERE id = pg_temp.test_id(201);
INSERT INTO floor_plans (id, event_id, media_asset_id, version_no, publication_status) VALUES
    (pg_temp.test_id(801), pg_temp.test_id(201), pg_temp.test_id(701), 1, 'DRAFT'),
    (pg_temp.test_id(802), pg_temp.test_id(202), pg_temp.test_id(702), 1, 'DRAFT'),
    (pg_temp.test_id(803), pg_temp.test_id(201), pg_temp.test_id(701), 2, 'PUBLISHED');
INSERT INTO map_pins (id, event_id, floor_plan_id, event_booth_id, pin_type, label, x_ratio, y_ratio) VALUES
    (pg_temp.test_id(901), pg_temp.test_id(201), pg_temp.test_id(801), pg_temp.test_id(601), 'BOOTH', '1', 0.25, 0.4),
    (pg_temp.test_id(902), pg_temp.test_id(201), pg_temp.test_id(803), pg_temp.test_id(601), 'BOOTH', '1', 0.25, 0.4),
    (pg_temp.test_id(903), pg_temp.test_id(201), pg_temp.test_id(801), NULL, 'OTHER_FACILITY', 'Exit', 0, 1);
INSERT INTO announcements (id, event_id, created_by) VALUES (pg_temp.test_id(1001), pg_temp.test_id(201), pg_temp.test_id(1));
UPDATE announcements SET title = 'Notice', body = 'Text', publication_status = 'PUBLISHED', published_at = now(),
    image_asset_id = pg_temp.test_id(701) WHERE id = pg_temp.test_id(1001);
INSERT INTO announcement_audiences VALUES (pg_temp.test_id(1001), 'PUBLIC');
INSERT INTO event_application_invites (event_id, token_hash, created_by, expires_at)
VALUES (pg_temp.test_id(201), repeat('a', 64), pg_temp.test_id(1), now() + interval '1 day');

DO $test$
BEGIN
    -- Required contact fields, category codes, and cross-organization membership.
    PERFORM pg_temp.expect_violation($sql$INSERT INTO accounts (nickname, phone_number) VALUES ('No email', '01000000000')$sql$, '23502');
    PERFORM pg_temp.expect_violation($sql$UPDATE accounts SET phone_number = 'not-a-number' WHERE id = pg_temp.test_id(1)$sql$, '23514');
    PERFORM pg_temp.expect_violation($sql$UPDATE booth_profiles SET category_code = 'UNDEFINED' WHERE id = pg_temp.test_id(401)$sql$, '23514');
    PERFORM pg_temp.expect_violation($sql$INSERT INTO event_memberships (event_id, account_id, organization_id, role) VALUES (pg_temp.test_id(201), pg_temp.test_id(2), pg_temp.test_id(102), 'MANAGER')$sql$, '23503');
    PERFORM pg_temp.expect_violation($sql$UPDATE organization_memberships SET membership_status = 'REVOKED' WHERE organization_id = pg_temp.test_id(101)$sql$, '23514');

    -- Recruitment publication and bounded category sets.
    PERFORM pg_temp.expect_violation($sql$UPDATE event_recruitments SET publication_status = 'PUBLISHED' WHERE event_id = pg_temp.test_id(202)$sql$, '23514');
    PERFORM pg_temp.expect_violation($sql$UPDATE event_recruitments SET allowed_category_codes = ARRAY['UNKNOWN'] WHERE event_id = pg_temp.test_id(201)$sql$, '23514');
    PERFORM pg_temp.expect_violation($sql$UPDATE event_recruitments SET allowed_category_codes = ARRAY['FOOD', NULL] WHERE event_id = pg_temp.test_id(201)$sql$, '23514');
    PERFORM pg_temp.expect_violation($sql$UPDATE event_recruitments SET allowed_category_codes = ARRAY[['FOOD'], ['BEVERAGE']] WHERE event_id = pg_temp.test_id(201)$sql$, '23514');
    PERFORM pg_temp.expect_violation($sql$UPDATE event_recruitments SET participation_fee_krw = -1 WHERE event_id = pg_temp.test_id(201)$sql$, '23514');
    PERFORM pg_temp.expect_violation($sql$INSERT INTO event_application_invites (event_id, token_hash, created_by, expires_at) VALUES (pg_temp.test_id(201), repeat('b', 64), pg_temp.test_id(1), now() + interval '1 day')$sql$, '23505');

    -- Review state, rejection explanation, and duplicate current application.
    PERFORM pg_temp.expect_violation($sql$UPDATE booth_applications SET review_status = 'APPROVED' WHERE id = pg_temp.test_id(502)$sql$, '23514');
    PERFORM pg_temp.expect_violation($sql$UPDATE booth_applications SET review_status = 'REJECTED', reviewed_by = pg_temp.test_id(2), reviewed_at = now() WHERE id = pg_temp.test_id(502)$sql$, '23514');
    PERFORM pg_temp.expect_violation($sql$INSERT INTO booth_applications (event_id, applicant_account_id, booth_profile_id, booth_name_snapshot, category_code_snapshot, contact_name_snapshot, contact_email_snapshot, contact_phone_snapshot) VALUES (pg_temp.test_id(201), pg_temp.test_id(3), pg_temp.test_id(401), 'Duplicate', 'FOOD', 'Contact', 'c@example.com', '01000000000')$sql$, '23505');
    PERFORM pg_temp.expect_violation($sql$INSERT INTO application_document_checks (application_id, requirement_id, event_id) VALUES (pg_temp.test_id(501), pg_temp.test_id(302), pg_temp.test_id(201))$sql$, '23503');
    PERFORM pg_temp.expect_violation($sql$UPDATE application_document_checks SET checked_by = NULL WHERE application_id = pg_temp.test_id(501)$sql$, '23514');

    -- Booth identities, origin, and same-event relationships.
    PERFORM pg_temp.expect_violation($sql$INSERT INTO event_booths (event_id, source, booth_code, name, category_code) VALUES (pg_temp.test_id(201), 'MANUAL', 'B001', 'Duplicate', 'FOOD')$sql$, '23505');
    PERFORM pg_temp.expect_violation($sql$INSERT INTO event_booths (event_id, source, booth_code, name, category_code) VALUES (pg_temp.test_id(201), 'APPLICATION', 'B003', 'Missing application', 'FOOD')$sql$, '23514');
    PERFORM pg_temp.expect_violation($sql$INSERT INTO event_booths (event_id, application_id, source, booth_code, name, category_code) VALUES (pg_temp.test_id(201), pg_temp.test_id(502), 'APPLICATION', 'B003', 'Wrong event', 'FOOD')$sql$, '23503');
    PERFORM pg_temp.expect_violation($sql$INSERT INTO event_booths (event_id, application_id, source, booth_code, name, category_code) VALUES (pg_temp.test_id(201), pg_temp.test_id(501), 'APPLICATION', 'B003', 'Duplicate approval', 'FOOD')$sql$, '23505');
    PERFORM pg_temp.expect_violation($sql$UPDATE booth_items SET stock_status = 'UNKNOWN' WHERE event_booth_id = pg_temp.test_id(603)$sql$, '23514');

    -- Image ownership, map editions, coordinates, and unique assignments.
    PERFORM pg_temp.expect_violation($sql$UPDATE events SET poster_asset_id = pg_temp.test_id(702) WHERE id = pg_temp.test_id(201)$sql$, '23503');
    PERFORM pg_temp.expect_violation($sql$INSERT INTO floor_plans (event_id, media_asset_id, version_no, publication_status) VALUES (pg_temp.test_id(201), pg_temp.test_id(702), 3, 'ARCHIVED')$sql$, '23503');
    PERFORM pg_temp.expect_violation($sql$INSERT INTO floor_plans (event_id, media_asset_id, version_no) VALUES (pg_temp.test_id(201), pg_temp.test_id(701), 3)$sql$, '23505');
    PERFORM pg_temp.expect_violation($sql$INSERT INTO floor_plans (event_id, media_asset_id, version_no, publication_status) VALUES (pg_temp.test_id(201), pg_temp.test_id(701), 3, 'PUBLISHED')$sql$, '23505');
    PERFORM pg_temp.expect_violation($sql$UPDATE map_pins SET x_ratio = 1.01 WHERE id = pg_temp.test_id(901)$sql$, '23514');
    PERFORM pg_temp.expect_violation($sql$UPDATE map_pins SET pin_type = 'TOILET' WHERE id = pg_temp.test_id(901)$sql$, '23514');
    PERFORM pg_temp.expect_violation($sql$UPDATE map_pins SET event_booth_id = pg_temp.test_id(602) WHERE id = pg_temp.test_id(901)$sql$, '23503');
    PERFORM pg_temp.expect_violation($sql$UPDATE map_pins SET floor_plan_id = pg_temp.test_id(802) WHERE id = pg_temp.test_id(901)$sql$, '23503');
    PERFORM pg_temp.expect_violation($sql$INSERT INTO map_pins (event_id, floor_plan_id, event_booth_id, pin_type, label, x_ratio, y_ratio) VALUES (pg_temp.test_id(201), pg_temp.test_id(801), pg_temp.test_id(601), 'BOOTH', 'duplicate', 0.5, 0.5)$sql$, '23505');

    -- Notice images and complete published content.
    PERFORM pg_temp.expect_violation($sql$UPDATE announcements SET image_asset_id = pg_temp.test_id(702) WHERE id = pg_temp.test_id(1001)$sql$, '23503');
    PERFORM pg_temp.expect_violation($sql$UPDATE announcements SET body = '' WHERE id = pg_temp.test_id(1001)$sql$, '23514');
    PERFORM pg_temp.expect_violation($sql$UPDATE announcements SET revision = -1 WHERE id = pg_temp.test_id(1001)$sql$, '23514');

    -- A rejected application permits a fresh submission without erasing history.
    UPDATE booth_applications SET review_status = 'REJECTED', rejection_reason = 'Revise the plan',
        reviewed_by = pg_temp.test_id(2), reviewed_at = now() WHERE id = pg_temp.test_id(502);
    INSERT INTO booth_applications (event_id, applicant_account_id, booth_profile_id, booth_name_snapshot,
        category_code_snapshot, contact_name_snapshot, contact_email_snapshot, contact_phone_snapshot)
    VALUES (pg_temp.test_id(202), pg_temp.test_id(3), pg_temp.test_id(401), 'Resubmission',
        'FOOD', 'Contact', 'c@example.com', '01000000000');
    UPDATE booth_profiles SET name = 'New profile name' WHERE id = pg_temp.test_id(401);
    IF (SELECT booth_name_snapshot FROM booth_applications WHERE id = pg_temp.test_id(501)) <> 'Original booth name' THEN
        RAISE EXCEPTION 'Application snapshot changed with profile';
    END IF;
    RAISE NOTICE 'PASS: all expected constraint violations and valid draft/assignment/resubmission cases';
END;
$test$;

ROLLBACK;

DO $empty$
DECLARE
    relation record;
    remaining_rows bigint;
    checked_tables integer := 0;
BEGIN
    FOR relation IN SELECT tablename FROM pg_tables WHERE schemaname = 'erd' LOOP
        EXECUTE format('SELECT count(*) FROM erd.%I', relation.tablename) INTO remaining_rows;
        IF remaining_rows <> 0 THEN
            RAISE EXCEPTION 'Expected a fresh empty DB after rollback; % has % rows', relation.tablename, remaining_rows;
        END IF;
        checked_tables := checked_tables + 1;
    END LOOP;
    RAISE NOTICE 'PASS: all % ERD tables contain zero rows after rollback', checked_tables;
END;
$empty$;
