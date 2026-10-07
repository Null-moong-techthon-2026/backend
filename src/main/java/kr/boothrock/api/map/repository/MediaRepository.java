package kr.boothrock.api.map.repository;

import java.util.Map;
import java.util.UUID;
import kr.boothrock.api.common.repository.SqlStore;
import org.springframework.stereotype.Repository;

@Repository
public class MediaRepository {
    private final SqlStore sql;

    public MediaRepository(SqlStore sql) {
        this.sql = sql;
    }

    public Map<String, Object> asset(UUID id) {
        return sql.one("""
                select id, event_id, bucket, object_key, mime_type, size_bytes, width_px, height_px
                from app.media_assets where id = ?
                """, id);
    }

    public Map<String, Object> asset(UUID eventId, UUID id) {
        return sql.one("""
                select id, event_id, bucket, object_key, mime_type, size_bytes, width_px, height_px
                from app.media_assets where event_id = ? and id = ?
                """, eventId, id);
    }

    public boolean hasPublicReference(UUID assetId) {
        return sql.count("""
                select count(*) from app.media_assets m
                join app.events e on e.id = m.event_id
                join app.organizations o on o.id = e.organization_id
                where m.id = ? and e.publication_status = 'PUBLISHED' and o.organization_status = 'ACTIVE' and (
                    e.poster_asset_id = m.id
                    or exists (select 1 from app.floor_plans f
                        where f.event_id = e.id and f.media_asset_id = m.id
                        and f.publication_status = 'PUBLISHED')
                    or exists (select 1 from app.announcements a
                        join app.announcement_audiences aa on aa.announcement_id = a.id
                        where a.event_id = e.id and a.image_asset_id = m.id
                        and a.publication_status = 'PUBLISHED' and aa.audience = 'PUBLIC'))
                """, assetId) > 0;
    }

    public boolean hasInternalAnnouncementReference(UUID assetId, UUID eventId, boolean staff, boolean operator) {
        if (!staff && !operator) return false;
        return sql.count("""
                select count(*) from app.announcements a
                join app.announcement_audiences aa on aa.announcement_id = a.id
                where a.event_id = ? and a.image_asset_id = ? and a.publication_status = 'PUBLISHED'
                    and ((aa.audience = 'STAFF' and ?) or (aa.audience = 'OPERATORS' and ?))
                """, eventId, assetId, staff, operator) > 0;
    }

    public void insert(UUID id, UUID eventId, UUID accountId, String key,
            String mimeType, long size, int width, int height) {
        sql.update("""
                insert into app.media_assets
                    (id, event_id, uploaded_by, bucket, object_key, mime_type, size_bytes, width_px, height_px)
                values (?, ?, ?, 'local', ?, ?, ?, ?, ?)
                """, id, eventId, accountId, key, mimeType, size, width, height);
        sql.update("""
                insert into app.audit_logs
                    (id, organization_id, event_id, actor_account_id, actor_type, action, target_type, target_id)
                select ?, organization_id, id, ?, 'ACCOUNT', 'MEDIA_UPLOADED', 'MEDIA_ASSET', ?
                from app.events where id = ?
                """, UUID.randomUUID(), accountId, id, eventId);
    }
}
