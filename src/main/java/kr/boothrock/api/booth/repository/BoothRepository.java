package kr.boothrock.api.booth.repository;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import kr.boothrock.api.booth.dto.BoothFilter;
import kr.boothrock.api.booth.dto.BoothRequests.CreateItem;
import kr.boothrock.api.booth.dto.BoothRequests.PatchBooth;
import kr.boothrock.api.booth.dto.BoothRequests.PatchItem;
import kr.boothrock.api.common.repository.SqlStore;
import kr.boothrock.api.common.service.Rules;
import org.springframework.stereotype.Repository;

@Repository
public class BoothRepository {
    private static final String STOCK_JOIN = """
            left join lateral (
                select count(*) as item_count, bool_and(i.stock_status = 'SOLD_OUT') as all_sold_out,
                    bool_and(i.stock_status = 'UNLIMITED') as all_unlimited,
                    bool_or(i.stock_status in ('LOW', 'SOLD_OUT')) as any_low,
                    max(i.updated_at) as updated_at from app.booth_items i where i.event_booth_id = b.id
            ) stock on true
            """;
    private static final String STOCK_FIELDS = """
            case when stock.item_count = 0 then 'NOT_TRACKED'
                when stock.all_sold_out then 'SOLD_OUT' when stock.all_unlimited then 'UNLIMITED'
                when stock.any_low then 'LOW' else 'AVAILABLE' end as stock_summary,
            greatest(b.updated_at, stock.updated_at) as last_changed_at
            """;
    private static final String LIST_FIELDS = """
            event_booth_id, booth_code, name, category_code, operation_status,
            stock_summary, placement_status, last_changed_at
            """;
    private static final String ITEM_FIELDS = "id, name, description, price_krw, stock_status, sort_order";
    private final SqlStore sql;

    public BoothRepository(SqlStore sql) { this.sql = sql; }

    public String allocateCode(UUID eventId, String requested) {
        if (requested != null) {
            Rules.input(!requested.isBlank() && requested.length() <= 20
                    && requested.equals(requested.trim().toUpperCase(Locale.ROOT)), "boothCode must be uppercase and trimmed (max 20)");
            Rules.require(sql.count("select count(*) from app.event_booths where event_id = ? and booth_code = ?", eventId, requested) == 0,
                    "BOOTH_CODE_TAKEN", "This booth code is already in use");
            return requested;
        }
        Set<String> codes = new HashSet<>();
        sql.list("select booth_code from app.event_booths where event_id = ?", eventId)
                .forEach(row -> codes.add((String) row.get("boothCode")));
        for (int number = 1; ; number++) {
            String candidate = String.format(Locale.ROOT, "B%03d", number);
            if (!codes.contains(candidate)) return candidate;
        }
    }

    public UUID create(UUID eventId, UUID applicationId, String code, String name, String category, String description) {
        UUID id = UUID.randomUUID();
        sql.update("""
                insert into app.event_booths (id, event_id, application_id, source, booth_code, name, category_code, description)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                """, id, eventId, applicationId, applicationId == null ? "MANUAL" : "APPLICATION", code, name, category, Rules.text(description));
        return id;
    }

    public void requireFloorPlan(UUID eventId, UUID floorPlanId) {
        sql.one("select id from app.floor_plans where id = ? and event_id = ?", floorPlanId, eventId);
    }

    public Map<String, Object> list(UUID eventId, BoothFilter filter, boolean publicOnly) {
        List<Object> args = new ArrayList<>();
        String placement = """
                exists (select 1 from app.map_pins p join app.floor_plans f on f.id = p.floor_plan_id
                    and f.event_id = p.event_id where p.event_id = b.event_id and p.event_booth_id = b.id
                """;
        if (filter.floorPlanId() == null) {
            placement += " and f.publication_status = 'PUBLISHED')";
        } else {
            placement += " and f.id = ?)";
            args.add(filter.floorPlanId());
        }
        String base = """
                with booth_rows as (select b.id as event_booth_id, b.booth_code, b.name, b.category_code,
                    b.operation_status, b.visibility_status, b.source, b.revision,
                """ + STOCK_FIELDS + ", case when " + placement + " then 'ASSIGNED' else 'UNASSIGNED' end as placement_status"
                + " from app.event_booths b " + STOCK_JOIN + " where b.event_id = ?) ";
        args.add(eventId);
        StringBuilder where = new StringBuilder(" where 1 = 1");
        if (publicOnly) where.append(" and visibility_status = 'PUBLIC'");
        else if (filter.visibilityStatus() == null) where.append(" and visibility_status <> 'ARCHIVED'");
        else condition(where, args, "visibility_status", filter.visibilityStatus());
        if (filter.q() != null) { where.append(" and name ilike ? escape '\\'"); args.add(Rules.like(filter.q())); }
        condition(where, args, "category_code", filter.categoryCode());
        condition(where, args, "operation_status", filter.operationStatus());
        condition(where, args, "stock_summary", filter.stockSummary());
        condition(where, args, "placement_status", filter.placementStatus());
        condition(where, args, "source", filter.source());
        String direction = filter.sort().equals("boothCode,desc") ? "desc" : "asc";
        return sql.page(base + "select " + LIST_FIELDS + (publicOnly ? "" : ", source, visibility_status, revision")
                        + " from booth_rows" + where + " order by booth_code " + direction + ", event_booth_id " + direction,
                base + "select count(*) from booth_rows" + where, filter.page(), filter.size(), args.toArray());
    }

    private static void condition(StringBuilder where, List<Object> args, String column, String value) {
        if (value != null) { where.append(" and ").append(column).append(" = ?"); args.add(value); }
    }

    public Map<String, Object> summary(UUID eventId) {
        return sql.one("""
                select count(*) as total, count(*) filter (where operation_status = 'PREPARING') as preparing,
                    count(*) filter (where operation_status = 'OPEN') as open,
                    count(*) filter (where operation_status = 'SOLD_OUT') as sold_out,
                    count(*) filter (where operation_status = 'CLOSED') as closed
                from app.event_booths where event_id = ? and visibility_status <> 'ARCHIVED'
                """, eventId);
    }

    public Map<String, Object> detail(UUID eventId, UUID boothId, boolean publicOnly) {
        return sql.one("""
                select b.id as event_booth_id, b.event_id, b.booth_code, b.name, b.category_code,
                    b.description, b.operation_status,
                """ + STOCK_FIELDS + (publicOnly ? "" : ", b.source, b.visibility_status, b.revision")
                + " from app.event_booths b " + STOCK_JOIN + " where b.event_id = ? and b.id = ?"
                + (publicOnly ? " and b.visibility_status = 'PUBLIC'" : ""), eventId, boothId);
    }

    public Map<String, Object> placement(UUID eventId, UUID boothId) {
        return sql.optional("""
                select f.id as floor_plan_id, f.version_no, f.revision,
                    '/api/media-assets/' || m.id || '/content' as image_url, m.width_px, m.height_px,
                    p.id as pin_id, p.label, p.x_ratio, p.y_ratio
                from app.floor_plans f join app.media_assets m on m.id = f.media_asset_id and m.event_id = f.event_id
                    join app.map_pins p on p.floor_plan_id = f.id and p.event_id = f.event_id
                where f.event_id = ? and f.publication_status = 'PUBLISHED' and p.event_booth_id = ?
                """, eventId, boothId);
    }

    public void patch(UUID boothId, PatchBooth request) {
        sql.update("""
                update app.event_booths set name = coalesce(?, name), category_code = coalesce(?, category_code),
                    description = coalesce(?, description), visibility_status = coalesce(?, visibility_status),
                    revision = revision + 1, updated_at = now() where id = ?
                """, request.name(), request.categoryCode(), request.description(), request.visibilityStatus(), boothId);
    }

    public void operationStatus(UUID boothId, String status) {
        sql.update("update app.event_booths set operation_status = ?, revision = revision + 1, updated_at = now() where id = ?", status, boothId);
    }

    public List<Map<String, Object>> items(UUID boothId, boolean publicOnly) {
        return sql.list("select " + ITEM_FIELDS + (publicOnly ? "" : ", revision, updated_at")
                + " from app.booth_items where event_booth_id = ? order by sort_order, id", boothId);
    }

    public long itemCount(UUID boothId) { return sql.count("select count(*) from app.booth_items where event_booth_id = ?", boothId); }

    public Map<String, Object> item(UUID boothId, UUID itemId) {
        return sql.one("select " + ITEM_FIELDS + ", revision, updated_at from app.booth_items where event_booth_id = ? and id = ?", boothId, itemId);
    }

    public UUID createItem(UUID boothId, CreateItem request) {
        UUID id = UUID.randomUUID();
        sql.update("""
                insert into app.booth_items (id, event_booth_id, name, description, price_krw, stock_status, sort_order)
                values (?, ?, ?, ?, ?, ?, ?)
                """, id, boothId, request.name(), request.description(), request.priceKrw(),
                request.stockStatus() == null ? "AVAILABLE" : request.stockStatus(), request.sortOrder() == null ? 0 : request.sortOrder());
        touch(boothId);
        return id;
    }

    public void patchItem(UUID boothId, UUID itemId, PatchItem request) {
        Long price = request.priceKrw() == null || request.priceKrw().isNull() ? null : request.priceKrw().longValue();
        sql.update("""
                update app.booth_items set name = coalesce(?, name), description = coalesce(?, description),
                    price_krw = case when ? then ?::bigint else price_krw end,
                    stock_status = coalesce(?, stock_status), sort_order = coalesce(?, sort_order),
                    revision = revision + 1, updated_at = now() where id = ? and event_booth_id = ?
                """, request.name(), request.description(), request.priceKrw() != null, price,
                request.stockStatus(), request.sortOrder(), itemId, boothId);
        touch(boothId);
    }

    public void deleteItem(UUID boothId, UUID itemId) {
        sql.update("delete from app.booth_items where event_booth_id = ? and id = ?", boothId, itemId);
        touch(boothId);
    }

    private void touch(UUID boothId) {
        sql.update("update app.event_booths set updated_at = now(), revision = revision + 1 where id = ?", boothId);
    }

    public Map<String, Object> mine(UUID accountId, int page, int size) {
        String from = """
                from app.booth_memberships bm join app.event_booths b on b.id = bm.event_booth_id
                    join app.events e on e.id = b.event_id
                    join app.organizations o on o.id = e.organization_id
                where bm.account_id = ? and bm.membership_status = 'ACTIVE' and b.visibility_status <> 'ARCHIVED'
                    and o.organization_status = 'ACTIVE' and e.publication_status <> 'ARCHIVED'
                """;
        return sql.page("""
                select b.id as event_booth_id, b.booth_code, b.name, b.category_code, b.operation_status,
                    b.visibility_status, b.revision, e.id as event_id, e.name as event_name, e.venue, e.starts_at, e.ends_at
                """ + from + " order by e.created_at desc, e.id desc, b.booth_code, b.id",
                "select count(*) " + from, page, size, accountId);
    }
}
