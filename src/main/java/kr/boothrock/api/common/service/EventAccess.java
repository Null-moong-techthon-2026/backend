package kr.boothrock.api.common.service;

import java.util.Map;
import java.util.UUID;
import kr.boothrock.api.common.repository.SqlStore;
import org.springframework.stereotype.Service;

@Service
public class EventAccess {
    private final SqlStore db;
    public EventAccess(SqlStore db) { this.db = db; }
    public void requireAccount(UUID accountId) {
        if (accountId == null) throw new ApiException(401, "AUTHENTICATION_REQUIRED", "Login is required.");
        if (db.count("SELECT count(*) FROM app.accounts WHERE id=? AND account_status='ACTIVE'", accountId) == 0)
            throw ApiException.forbidden();
    }
    public void requireOrganizationOwner(UUID organizationId, UUID accountId) {
        requireAccount(accountId);
        if (db.count("""
                SELECT count(*) FROM app.organization_memberships m JOIN app.organizations o ON o.id=m.organization_id
                WHERE m.organization_id=? AND m.account_id=? AND m.role='OWNER'
                  AND m.membership_status='ACTIVE' AND o.organization_status='ACTIVE'
                """, organizationId, accountId) == 0) throw ApiException.notFound();
    }
    private String organizerRole(UUID eventId, UUID accountId) {
        if (accountId == null) return null;
        Map<String, Object> row = db.optional("""
                SELECT CASE WHEN om.role='OWNER' THEN 'MANAGER' ELSE em.role END AS role
                FROM app.events e JOIN app.organizations o ON o.id=e.organization_id
                JOIN app.organization_memberships om ON om.organization_id=o.id AND om.account_id=? AND om.membership_status='ACTIVE'
                JOIN app.accounts a ON a.id=om.account_id AND a.account_status='ACTIVE'
                LEFT JOIN app.event_memberships em ON em.event_id=e.id AND em.account_id=om.account_id AND em.membership_status='ACTIVE'
                WHERE e.id=? AND o.organization_status='ACTIVE'
                """, accountId, eventId);
        return row == null ? null : (String) row.get("role");
    }
    public boolean isManager(UUID eventId, UUID accountId) { return "MANAGER".equals(organizerRole(eventId, accountId)); }
    public boolean isStaff(UUID eventId, UUID accountId) { return organizerRole(eventId, accountId) != null; }
    public boolean isOperator(UUID eventId, UUID accountId) {
        return accountId != null && db.count("""
                SELECT count(*) FROM app.booth_memberships bm JOIN app.event_booths b ON b.id=bm.event_booth_id
                JOIN app.accounts a ON a.id=bm.account_id AND a.account_status='ACTIVE'
                JOIN app.events e ON e.id=b.event_id JOIN app.organizations o ON o.id=e.organization_id
                WHERE b.event_id=? AND bm.account_id=? AND bm.membership_status='ACTIVE'
                  AND b.visibility_status <> 'ARCHIVED' AND o.organization_status='ACTIVE'
                """, eventId, accountId) > 0;
    }
    public Map<String, Object> requireParticipant(UUID eventId, UUID accountId) {
        requireAccount(accountId);
        if (!isStaff(eventId, accountId) && !isOperator(eventId, accountId)) throw ApiException.notFound();
        return db.one("SELECT * FROM app.events WHERE id=?", eventId);
    }
    public Map<String, Object> requireStaff(UUID eventId, UUID accountId) {
        Map<String, Object> event = requireParticipant(eventId, accountId);
        if (!isStaff(eventId, accountId)) throw ApiException.forbidden();
        return event;
    }
    public Map<String, Object> requireManager(UUID eventId, UUID accountId) {
        Map<String, Object> event = requireParticipant(eventId, accountId);
        if (!isManager(eventId, accountId)) throw ApiException.forbidden();
        return event;
    }
    public Map<String, Object> requirePublic(UUID eventId) {
        return db.one("""
                SELECT e.* FROM app.events e JOIN app.organizations o ON o.id=e.organization_id
                WHERE e.id=? AND e.publication_status='PUBLISHED' AND o.organization_status='ACTIVE'
                """, eventId);
    }
    public Map<String, Object> requireBooth(UUID eventId, UUID boothId, UUID accountId) {
        requireAccount(accountId);
        Map<String, Object> booth = db.one("SELECT * FROM app.event_booths WHERE id=? AND event_id=?", boothId, eventId);
        if (!isManager(eventId, accountId) && !(isOperator(eventId, accountId) && db.count("""
                SELECT count(*) FROM app.booth_memberships WHERE event_booth_id=? AND account_id=? AND membership_status='ACTIVE'
                """, boothId, accountId) > 0)) throw ApiException.notFound();
        return booth;
    }
    public void lockEvent(UUID eventId) { db.one("SELECT id FROM app.events WHERE id=? FOR UPDATE", eventId); }
}
