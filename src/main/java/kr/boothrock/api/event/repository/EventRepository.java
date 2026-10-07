package kr.boothrock.api.event.repository;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.boothrock.api.common.repository.SqlStore;
import org.springframework.stereotype.Repository;

@Repository
public class EventRepository {
    private final SqlStore db;
    public EventRepository(SqlStore db) { this.db = db; }
    public Map<String,Object> event(UUID eventId) {
        return db.one("""
                SELECT e.id,e.name,e.venue,e.starts_at,e.ends_at,e.publication_status,e.description,
                       e.poster_asset_id,e.revision,e.organization_id,o.name AS organization_name
                FROM app.events e JOIN app.organizations o ON o.id=e.organization_id WHERE e.id=?
                """, eventId);
    }
    public Map<String,Object> recruitment(UUID eventId) {
        Map<String,Object> result = db.one("""
                SELECT event_id, introduction, publication_status, closes_at, target_booth_count,
                       allowed_category_codes, participation_fee_krw, fee_note, revision
                FROM app.event_recruitments WHERE event_id=?
                """, eventId);
        result.put("documentRequirements", documents(eventId));
        return result;
    }
    public List<Map<String,Object>> documents(UUID eventId) {
        return db.list("""
                SELECT id,name,is_required,applicable_category_codes,sort_order
                FROM app.recruitment_document_requirements WHERE event_id=? ORDER BY sort_order,id
                """, eventId);
    }
    public void audit(UUID eventId, UUID actor, String action, String targetType, UUID targetId) {
        db.update("""
                INSERT INTO app.audit_logs(id,organization_id,event_id,actor_account_id,actor_type,action,target_type,target_id)
                SELECT ?,organization_id,id,?,'ACCOUNT',?,?,? FROM app.events WHERE id=?
                """, UUID.randomUUID(), actor, action, targetType, targetId, eventId);
    }
}
