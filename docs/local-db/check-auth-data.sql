-- Run again after signup or deletion to refresh the displayed result.
-- Docker publishes container port 5432 on host port 5433 by default.
SELECT current_database() AS database_name, current_user AS database_user,
    current_setting('transaction_isolation') AS transaction_isolation,
    inet_server_port() AS container_port, clock_timestamp() AS checked_at;

SELECT
    a.id AS account_id,
    c.login_id,
    a.nickname,
    a.phone_number,
    a.email,
    a.account_status,
    o.name AS organization_name,
    m.role AS organization_role,
    m.membership_status,
    a.created_at
FROM app.accounts a
JOIN app.local_credentials c ON c.account_id = a.id
LEFT JOIN app.organization_memberships m ON m.account_id = a.id
LEFT JOIN app.organizations o ON o.id = m.organization_id
ORDER BY a.created_at DESC, a.id, o.id;

SELECT 'accounts' AS table_name, count(*) AS row_count FROM app.accounts
UNION ALL SELECT 'local_credentials', count(*) FROM app.local_credentials
UNION ALL SELECT 'organizations', count(*) FROM app.organizations
UNION ALL SELECT 'organization_memberships', count(*) FROM app.organization_memberships;
