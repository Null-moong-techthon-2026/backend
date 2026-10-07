package kr.boothrock.api.map.repository;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.boothrock.api.common.repository.SqlStore;
import kr.boothrock.api.map.dto.MapPinRequest;
import org.springframework.stereotype.Repository;

@Repository
public class MapRepository {
    private static final String MAP_COLUMNS = """
            select id as floor_plan_id, event_id, media_asset_id, version_no,
                   publication_status, revision, created_at, updated_at from app.floor_plans
            """;
    private final SqlStore sql;

    public MapRepository(SqlStore sql) {
        this.sql = sql;
    }

    public Map<String, Object> byStatus(UUID eventId, String status) {
        return sql.optional(MAP_COLUMNS + " where event_id = ? and publication_status = ?", eventId, status);
    }

    public Map<String, Object> plan(UUID eventId, UUID id) {
        return sql.one(MAP_COLUMNS + " where event_id = ? and id = ?", eventId, id);
    }

    public List<Map<String, Object>> pins(UUID floorPlanId) {
        return sql.list("""
                select id, pin_type, label, x_ratio, y_ratio, event_booth_id
                from app.map_pins where floor_plan_id = ? order by id
                """, floorPlanId);
    }

    public List<Map<String, Object>> publicPins(UUID floorPlanId) {
        return sql.list("""
                select p.id, p.pin_type, p.label, p.x_ratio, p.y_ratio,
                       p.event_booth_id, b.booth_code, b.name as booth_name, b.operation_status
                from app.map_pins p
                left join app.event_booths b on b.id = p.event_booth_id and b.event_id = p.event_id
                where p.floor_plan_id = ?
                  and (p.pin_type <> 'BOOTH' or b.visibility_status = 'PUBLIC') order by p.id
                """, floorPlanId);
    }

    public List<Map<String, Object>> existingPins(Collection<UUID> ids) {
        if (ids.isEmpty()) return List.of();
        return sql.list("select id, floor_plan_id, event_id from app.map_pins where id in ("
                + placeholders(ids.size()) + ")", ids.toArray());
    }

    public long matchingBooths(UUID eventId, Collection<UUID> ids) {
        if (ids.isEmpty()) return 0;
        Object[] args = new Object[ids.size() + 1];
        args[0] = eventId;
        int i = 1;
        for (UUID id : ids) args[i++] = id;
        return sql.count("select count(*) from app.event_booths where event_id = ? and id in ("
                + placeholders(ids.size()) + ")", args);
    }

    public void create(UUID id, UUID eventId, UUID assetId) {
        sql.update("""
                insert into app.floor_plans (id, event_id, media_asset_id, version_no)
                select ?, ?, ?, coalesce(max(version_no), 0) + 1 from app.floor_plans where event_id = ?
                """, id, eventId, assetId, eventId);
    }

    public void insertPin(UUID eventId, UUID floorPlanId, MapPinRequest pin) {
        sql.update("""
                insert into app.map_pins (id, event_id, floor_plan_id, event_booth_id, pin_type, label, x_ratio, y_ratio)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                """, pin.id(), eventId, floorPlanId, pin.eventBoothId(), pin.pinType(),
                pin.label(), pin.xRatio(), pin.yRatio());
    }

    public void clearPins(UUID floorPlanId) {
        sql.update("delete from app.map_pins where floor_plan_id = ?", floorPlanId);
    }

    public void bumpRevision(UUID floorPlanId) {
        sql.update("update app.floor_plans set revision = revision + 1, updated_at = now() where id = ?", floorPlanId);
    }

    public void delete(UUID floorPlanId) {
        clearPins(floorPlanId);
        sql.update("delete from app.floor_plans where id = ?", floorPlanId);
    }

    public void publish(UUID eventId, UUID floorPlanId) {
        sql.update("""
                update app.floor_plans set publication_status = 'ARCHIVED', revision = revision + 1, updated_at = now()
                where event_id = ? and publication_status = 'PUBLISHED'
                """, eventId);
        sql.update("""
                update app.floor_plans set publication_status = 'PUBLISHED', revision = revision + 1, updated_at = now()
                where id = ?
                """, floorPlanId);
    }

    public void audit(UUID eventId, UUID accountId, String action, UUID floorPlanId) {
        sql.update("""
                insert into app.audit_logs
                    (id, organization_id, event_id, actor_account_id, actor_type, action, target_type, target_id)
                select ?, organization_id, id, ?, 'ACCOUNT', ?, 'FLOOR_PLAN', ? from app.events where id = ?
                """, UUID.randomUUID(), accountId, action, floorPlanId, eventId);
    }

    private static String placeholders(int size) {
        return String.join(",", Collections.nCopies(size, "?"));
    }
}
