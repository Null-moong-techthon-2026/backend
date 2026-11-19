-- Preserve token hashes, IDs and application/event data. Repair existing deadline mismatches.
-- LEAST is intentionally not used to extend expired/revoked invitations.
UPDATE app.event_application_invites i
SET expires_at=CASE WHEN r.closes_at>i.created_at THEN r.closes_at ELSE i.expires_at END,
    revoked_at=CASE WHEN r.closes_at<=i.created_at THEN greatest(clock_timestamp(),i.created_at) ELSE i.revoked_at END
FROM app.event_recruitments r
WHERE r.event_id=i.event_id AND i.revoked_at IS NULL AND r.closes_at IS NOT NULL
  AND i.expires_at>r.closes_at;
