-- LOCAL TEST DB ONLY: deletes all account and organization rows.
-- Run the entire script. Tables and Flyway history are preserved.
BEGIN;
DELETE FROM app.organization_memberships;
DELETE FROM app.organizations;
DELETE FROM app.local_credentials;
DELETE FROM app.accounts;
COMMIT;
