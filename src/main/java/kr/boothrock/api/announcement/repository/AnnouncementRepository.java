package kr.boothrock.api.announcement.repository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.boothrock.api.common.repository.SqlStore;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("local & !deploy")
public class AnnouncementRepository {
    public record Readers(boolean manager, boolean staff, boolean operator) {}

    private static final String SUMMARY = """
            a.id, a.event_id, a.title, a.is_urgent, a.publication_status,
            a.published_at, a.created_at, a.updated_at, a.revision,
            ARRAY(SELECT r.audience FROM app.announcement_audiences r
                  WHERE r.announcement_id = a.id ORDER BY r.audience) AS audiences
            """;
    private static final String DETAIL = SUMMARY + """
            , a.body, a.image_asset_id,
            CASE WHEN a.image_asset_id IS NOT NULL
                THEN '/api/media-assets/' || a.image_asset_id || '/content' END AS image_url
            """;
    private static final String VISIBLE = """
            FROM app.announcements a
            WHERE a.event_id = ? AND a.publication_status <> 'DELETED'
              AND (? OR a.publication_status = 'PUBLISHED')
              AND (? OR EXISTS (
                  SELECT 1 FROM app.announcement_audiences reader
                  WHERE reader.announcement_id = a.id
                    AND (reader.audience = 'PUBLIC'
                         OR (reader.audience = 'STAFF' AND ?)
                         OR (reader.audience = 'OPERATORS' AND ?))))
            """;
    private static final String FILTERS = """
              AND a.title ILIKE ? ESCAPE '\\'
              AND (? = '' OR EXISTS (
                  SELECT 1 FROM app.announcement_audiences selected
                  WHERE selected.announcement_id = a.id AND selected.audience = ?))
            """;
    private static final String ORDER = """
            ORDER BY a.is_urgent DESC, COALESCE(a.published_at, a.created_at) DESC, a.id DESC
            """;

    private final SqlStore sql;

    public AnnouncementRepository(SqlStore sql) {
        this.sql = sql;
    }

    public Map<String, Object> page(UUID eventId, Readers readers, String query, String audience,
                                    int page, int size) {
        return sql.page("SELECT " + SUMMARY + VISIBLE + FILTERS + ORDER,
                "SELECT count(*) " + VISIBLE + FILTERS, page, size,
                eventId, readers.manager(), readers.manager(), readers.staff(), readers.operator(),
                query, audience, audience);
    }

    public Map<String, Object> detail(UUID eventId, UUID announcementId, Readers readers) {
        return sql.one("SELECT " + DETAIL + VISIBLE + " AND a.id = ?",
                eventId, readers.manager(), readers.manager(), readers.staff(), readers.operator(), announcementId);
    }

    public List<Map<String, Object>> recent(UUID eventId, Readers readers) {
        return sql.list("SELECT " + SUMMARY + VISIBLE + ORDER + " LIMIT 3",
                eventId, false, readers.manager(), readers.staff(), readers.operator());
    }

    public void requireImage(UUID eventId, UUID imageAssetId) {
        if (imageAssetId != null) {
            sql.one("SELECT id FROM app.media_assets WHERE event_id = ? AND id = ?", eventId, imageAssetId);
        }
    }

    public void create(UUID id, UUID eventId, UUID actor, String title, String body, UUID imageAssetId,
                       boolean urgent, String publicationStatus, Instant publishedAt) {
        sql.update("""
                INSERT INTO app.announcements
                    (id, event_id, created_by, title, body, image_asset_id, is_urgent, publication_status, published_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, eventId, actor, title, body, imageAssetId, urgent, publicationStatus, publishedAt);
    }

    public void update(UUID eventId, UUID id, String title, String body, UUID imageAssetId,
                       boolean urgent, String publicationStatus, Instant publishedAt) {
        sql.update("""
                UPDATE app.announcements
                SET title = ?, body = ?, image_asset_id = ?, is_urgent = ?, publication_status = ?,
                    published_at = COALESCE(published_at, ?), revision = revision + 1, updated_at = now()
                WHERE event_id = ? AND id = ?
                """, title, body, imageAssetId, urgent, publicationStatus, publishedAt, eventId, id);
    }

    public void replaceAudiences(UUID id, List<String> audiences) {
        sql.update("DELETE FROM app.announcement_audiences WHERE announcement_id = ?", id);
        for (String audience : audiences) {
            sql.update("INSERT INTO app.announcement_audiences (announcement_id, audience) VALUES (?, ?)",
                    id, audience);
        }
    }

    public void delete(UUID eventId, UUID id) {
        sql.update("""
                UPDATE app.announcements
                SET publication_status = 'DELETED', revision = revision + 1, updated_at = now()
                WHERE event_id = ? AND id = ?
                """, eventId, id);
    }

    public void audit(UUID eventId, UUID organizationId, UUID actor, UUID id, String action) {
        sql.update("""
                INSERT INTO app.audit_logs
                    (id, event_id, organization_id, actor_account_id, actor_type, action, target_type, target_id)
                VALUES (?, ?, ?, ?, 'ACCOUNT', ?, 'ANNOUNCEMENT', ?)
                """, UUID.randomUUID(), eventId, organizationId, actor, action, id);
    }
}
