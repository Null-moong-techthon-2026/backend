-- Flyway creates the app schema before applying this migration.
REVOKE ALL ON SCHEMA app FROM PUBLIC;
COMMENT ON SCHEMA app IS 'Private application schema managed by Flyway';

