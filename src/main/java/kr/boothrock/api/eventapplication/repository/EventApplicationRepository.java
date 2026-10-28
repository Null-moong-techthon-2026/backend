package kr.boothrock.api.eventapplication.repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.boothrock.api.common.repository.SqlStore;
import kr.boothrock.api.eventapplication.dto.EventApplicationRequests.Create;
import org.springframework.stereotype.Repository;

@Repository
public class EventApplicationRepository {
    private static final String FIELDS = """
            a.id, a.status, a.revision, a.org_name, a.org_type, a.org_email, a.business_number, a.affiliation,
            a.representative, a.website, a.contact_name, a.contact_email, a.contact_phone, a.contact_role,
            a.event_name, a.event_kind, to_char(a.starts_on, 'YYYY-MM-DD') AS start_date,
            to_char(a.ends_on, 'YYYY-MM-DD') AS end_date, a.venue, a.expected_booth_count AS booth_count,
            a.handover, a.payment_plan, a.payment_method, a.receipt_type, a.agreed_terms AS agreements,
            a.submitted_at, a.reviewed_at, a.rejection_reason, a.created_event_id,
            a.applicant_account_id, c.nickname AS applicant_nickname
            """;
    private static final String FROM = " FROM app.event_applications a JOIN app.accounts c ON c.id = a.applicant_account_id";
    private static final String ORDER = " ORDER BY a.submitted_at DESC, a.id DESC";

    private final SqlStore sql;

    public EventApplicationRepository(SqlStore sql) { this.sql = sql; }

    public boolean hasPending(UUID applicant) {
        return sql.count("SELECT count(*) FROM app.event_applications WHERE applicant_account_id = ? AND status = 'PENDING'", applicant) > 0;
    }

    public UUID insert(UUID applicant, Create r, String[] agreements) {
        UUID id = UUID.randomUUID();
        sql.update("""
                INSERT INTO app.event_applications (id, applicant_account_id, org_name, org_type, org_email,
                    business_number, affiliation, representative, website, contact_name, contact_email, contact_phone,
                    contact_role, event_name, event_kind, starts_on, ends_on, venue, expected_booth_count, handover,
                    payment_plan, payment_method, receipt_type, agreed_terms)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, applicant, r.orgName().trim(), r.orgType(), r.orgEmail().trim(),
                blank(r.businessNumber()), blank(r.affiliation()), blank(r.representative()), blank(r.website()),
                r.contactName().trim(), r.contactEmail().trim(), r.contactPhone(), r.contactRole().trim(),
                r.eventName().trim(), r.eventKind(), r.startDate(), r.endDate(), r.venue().trim(), r.boothCount(),
                r.handover(), r.paymentPlan(), r.paymentMethod(), r.receiptType(), agreements);
        return id;
    }

    private static String blank(String value) { return value == null ? "" : value.trim(); }

    /** One application of the applicant. Not found (404) for someone else's. */
    public Map<String, Object> ownedBy(UUID applicant, UUID id) {
        return sql.one("SELECT " + FIELDS + FROM + " WHERE a.id = ? AND a.applicant_account_id = ?", id, applicant);
    }

    public Map<String, Object> mine(UUID applicant, int page, int size) {
        return sql.page("SELECT " + FIELDS + FROM + " WHERE a.applicant_account_id = ?" + ORDER,
                "SELECT count(*) FROM app.event_applications a WHERE a.applicant_account_id = ?", page, size, applicant);
    }

    public Map<String, Object> list(String status, int page, int size) {
        List<Object> args = new ArrayList<>();
        String where = "";
        if (status != null) { where = " WHERE a.status = ?"; args.add(status); }
        return sql.page("SELECT " + FIELDS + FROM + where + ORDER,
                "SELECT count(*) FROM app.event_applications a" + where, page, size, args.toArray());
    }

    public Map<String, Object> summary() {
        return sql.one("""
                SELECT count(*) AS total, count(*) FILTER (WHERE status = 'PENDING') AS pending,
                    count(*) FILTER (WHERE status = 'APPROVED') AS approved,
                    count(*) FILTER (WHERE status = 'REJECTED') AS rejected
                FROM app.event_applications
                """);
    }

    public Map<String, Object> one(UUID id) {
        return sql.one("SELECT " + FIELDS + FROM + " WHERE a.id = ?", id);
    }

    /** Reads the application and locks it for the rest of the transaction. */
    public Map<String, Object> lock(UUID id) {
        return sql.one("SELECT " + FIELDS + FROM + " WHERE a.id = ? FOR UPDATE OF a", id);
    }

    public void createOrganization(UUID id, String name, String email, UUID owner) {
        sql.update("INSERT INTO app.organizations (id, name, contact_email, created_by) VALUES (?, ?, ?, ?)", id, name, email, owner);
        sql.update("INSERT INTO app.organization_memberships (organization_id, account_id, role) VALUES (?, ?, 'OWNER')", id, owner);
    }

    public void createEvent(UUID id, UUID organization, UUID owner, String name, String venue, Instant startsAt, Instant endsAt) {
        sql.update("""
                INSERT INTO app.events (id, organization_id, created_by, name, venue, starts_at, ends_at, description)
                VALUES (?, ?, ?, ?, ?, ?, ?, '')
                """, id, organization, owner, name, venue, startsAt, endsAt);
        sql.update("INSERT INTO app.event_recruitments (event_id) VALUES (?)", id);
    }

    public void approve(UUID id, UUID admin, UUID organization, UUID event) {
        sql.update("""
                UPDATE app.event_applications SET status = 'APPROVED', reviewed_at = now(), reviewed_by = ?,
                    created_organization_id = ?, created_event_id = ?, revision = revision + 1, updated_at = now()
                WHERE id = ?
                """, admin, organization, event, id);
    }

    public void reject(UUID id, UUID admin, String reason) {
        sql.update("""
                UPDATE app.event_applications SET status = 'REJECTED', reviewed_at = now(), reviewed_by = ?,
                    rejection_reason = ?, revision = revision + 1, updated_at = now()
                WHERE id = ?
                """, admin, reason, id);
    }
}
