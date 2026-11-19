package kr.boothrock.api.booth.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.boothrock.api.booth.dto.BoothRequests.Apply;
import kr.boothrock.api.booth.dto.BoothRequests.Decision;
import kr.boothrock.api.booth.dto.BoothRequests.DocumentCheck;
import kr.boothrock.api.common.repository.SqlStore;
import kr.boothrock.api.common.service.Rules;
import org.springframework.stereotype.Repository;

@Repository
public class ApplicationRepository {
    private static final String APPLICATION_FIELDS = """
            a.id as application_id, a.event_id, a.applicant_account_id, a.booth_profile_id,
            a.booth_name_snapshot, a.category_code_snapshot, a.introduction_snapshot,
            a.contact_name_snapshot, a.contact_email_snapshot, a.contact_phone_snapshot,
            a.review_status, a.review_note, a.rejection_reason, a.reviewed_at, a.submitted_at, a.revision,
            a.cancelled_at,a.cancelled_by,a.cancel_reason,a.affiliation,a.bank_name,a.account_number,a.account_holder,
            a.sale_items,a.required_facilities,a.additional_answers::text as additional_answers_json
            """;
    private final SqlStore sql;

    public ApplicationRepository(SqlStore sql) { this.sql = sql; }

    public Map<String, Object> recruitment(UUID eventId) {
        return sql.one("""
                select e.id, e.organization_id, e.publication_status as event_publication_status,
                    r.publication_status, r.closes_at, r.allowed_category_codes, e.ends_at
                from app.events e join app.event_recruitments r on r.event_id = e.id
                    join app.organizations o on o.id = e.organization_id and o.organization_status = 'ACTIVE'
                where e.id = ?
                """, eventId);
    }

    public boolean validInvitation(UUID eventId, String hash) {
        return sql.count("""
                select count(*) from app.event_application_invites i
                    join app.event_recruitments r on r.event_id = i.event_id
                    join app.events e on e.id=i.event_id
                    join app.organizations o on o.id=e.organization_id
                where i.event_id = ? and i.token_hash = ? and i.revoked_at is null
                    and least(i.expires_at,r.closes_at)>clock_timestamp() and r.closes_at>clock_timestamp()
                    and r.publication_status='PUBLISHED' and e.publication_status<>'ARCHIVED'
                    and o.organization_status='ACTIVE' and (e.ends_at is null or e.ends_at>clock_timestamp())
                """, eventId, hash) > 0;
    }

    public boolean currentApplication(UUID eventId, UUID profileId) {
        return sql.count("""
                select count(*) from app.booth_applications where event_id = ? and booth_profile_id = ?
                    and review_status in ('PENDING', 'APPROVED')
                """, eventId, profileId) > 0;
    }

    public UUID create(UUID eventId, UUID applicant, Apply request, Map<String, Object> profile) {
        UUID id = UUID.randomUUID();
        sql.update("""
                insert into app.booth_applications (id, event_id, applicant_account_id, booth_profile_id,
                    booth_name_snapshot, category_code_snapshot, introduction_snapshot,
                    contact_name_snapshot, contact_email_snapshot, contact_phone_snapshot)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, eventId, applicant, request.boothProfileId(), profile.get("name"), profile.get("categoryCode"),
                profile.get("introduction"), request.applicantName(), request.applicantEmail(), request.applicantPhone());
        sql.update("""
                insert into app.application_document_checks (application_id, requirement_id, event_id)
                select ?, r.id, r.event_id from app.recruitment_document_requirements r
                where r.event_id = ? and (cardinality(r.applicable_category_codes) = 0
                    or ? = any(r.applicable_category_codes))
                """, id, eventId, profile.get("categoryCode"));
        validateAndSaveExtras(eventId,id,request);
        return id;
    }
    private void validateAndSaveExtras(UUID event,UUID id,Apply r) {
        boolean hasBank=r.bankName()!=null&&!r.bankName().isBlank(),hasNumber=r.accountNumber()!=null&&!r.accountNumber().isBlank(),hasHolder=r.accountHolder()!=null&&!r.accountHolder().isBlank();
        Rules.input((hasBank&&hasNumber&&hasHolder)||(!hasBank&&!hasNumber&&!hasHolder),"Supply all three bank fields together, or leave all blank");
        var questions=sql.list("select id,question,is_required from app.recruitment_questions where event_id=? order by sort_order,id",event);
        var answers=new java.util.HashMap<UUID,String>();
        if(r.additionalAnswers()!=null)for(var a:r.additionalAnswers()) {
            Rules.input(!answers.containsKey(a.questionId()),"Answer question IDs must be unique");
            Rules.input(questions.stream().anyMatch(q->q.get("id").equals(a.questionId())),"Unknown question for this recruitment");
            answers.put(a.questionId(),a.answer().trim());
        }
        var snapshots=new ArrayList<Map<String,Object>>();
        for(var q:questions) {
            String answer=answers.getOrDefault((UUID)q.get("id"),"");
            Rules.input(!Boolean.TRUE.equals(q.get("isRequired"))||!answer.isBlank(),"Answer every required question");
            snapshots.add(Rules.result("questionId",q.get("id"),"question",q.get("question"),"isRequired",q.get("isRequired"),"answer",answer));
        }
        sql.update("update app.booth_applications set affiliation=?,bank_name=?,account_number=?,account_holder=?,sale_items=?,required_facilities=?,additional_answers=?::jsonb where id=?",
            r.affiliation(),r.bankName(),r.accountNumber(),r.accountHolder(),r.saleItems(),r.requiredFacilities(),tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(snapshots),id);
    }
    public static Object answers(Map<String,Object> row) {
        return tools.jackson.databind.json.JsonMapper.builder().build().readValue((String)row.get("additionalAnswersJson"),java.util.List.class);
    }

    public Map<String, Object> application(UUID eventId, UUID id, boolean lock) {
        return sql.one("select " + APPLICATION_FIELDS + " from app.booth_applications a where a.event_id = ? and a.id = ?"
                + (lock ? " for update" : ""), eventId, id);
    }

    public Map<String, Object> owned(UUID applicant, UUID id) {
        return sql.one("select " + APPLICATION_FIELDS + " from app.booth_applications a where a.applicant_account_id = ? and a.id = ?",
                applicant, id);
    }

    public Map<String, Object> booth(UUID applicationId) {
        return sql.optional("select id as event_booth_id, booth_code from app.event_booths where application_id = ?", applicationId);
    }

    public List<Map<String, Object>> documents(UUID eventId, UUID applicationId, boolean manager) {
        return sql.list("""
                select r.id as requirement_id, r.name, r.is_required, c.check_status, c.revision,
                    d.id as document_id,d.file_name,d.uploaded_at,d.mime_type,d.size_bytes
                """ + (manager ? ", c.review_note, c.checked_at " : " ") + """
                from app.application_document_checks c
                    join app.recruitment_document_requirements r on r.id = c.requirement_id and r.event_id = c.event_id
                left join app.application_documents d on d.application_id=c.application_id and d.requirement_id=c.requirement_id
                where c.event_id = ? and c.application_id = ? order by r.sort_order, r.id
                """, eventId, applicationId);
    }

    public Map<String, Object> list(UUID eventId, int page, int size, String q, String category, String status, String sort) {
        StringBuilder where = new StringBuilder(" where a.event_id = ?");
        List<Object> args = new ArrayList<>(List.of(eventId));
        if (q != null) { where.append(" and a.booth_name_snapshot ilike ? escape '\\'"); args.add(Rules.like(q)); }
        if (category != null) { where.append(" and a.category_code_snapshot = ?"); args.add(category); }
        if (status != null) { where.append(" and a.review_status = ?"); args.add(status); }
        String direction = sort.equals("submittedAt,asc") ? "asc" : "desc";
        return sql.page("""
                select a.id as application_id, a.booth_name_snapshot as booth_name, a.submitted_at,
                    a.category_code_snapshot as category_code, a.contact_name_snapshot as applicant_name,
                    a.review_status, b.id as event_booth_id, b.booth_code
                from app.booth_applications a left join app.event_booths b on b.application_id = a.id
                """ + where + " order by a.submitted_at " + direction + ", a.id " + direction,
                "select count(*) from app.booth_applications a" + where, page, size, args.toArray());
    }

    public Map<String, Object> mine(UUID applicant, int page, int size) {
        return sql.page("""
                select a.id as application_id, e.id as event_id, e.name as event_name,
                    a.booth_name_snapshot as booth_name, a.category_code_snapshot as category_code,
                    a.review_status, a.submitted_at, a.rejection_reason, a.revision, a.cancelled_at,a.cancel_reason,
                    b.id as event_booth_id, b.booth_code
                from app.booth_applications a join app.events e on e.id = a.event_id
                    left join app.event_booths b on b.application_id = a.id
                where a.applicant_account_id = ? order by a.submitted_at desc, a.id desc
                """, "select count(*) from app.booth_applications where applicant_account_id = ?", page, size, applicant);
    }

    public Map<String, Object> summary(UUID eventId) {
        return sql.one("""
                select count(*) as total, count(*) filter (where review_status = 'PENDING') as pending,
                    count(*) filter (where review_status = 'APPROVED') as approved,
                    count(*) filter (where review_status = 'REJECTED') as rejected, count(*) filter (where review_status = 'CANCELLED') as cancelled
                from app.booth_applications where event_id = ?
                """, eventId);
    }

    public void reviewNote(UUID id, String note) {
        sql.update("update app.booth_applications set review_note = ?, revision = revision + 1, updated_at = now() where id = ?", note, id);
    }

    public Map<String, Object> document(UUID eventId, UUID id, UUID requirementId) {
        return sql.one("""
                select requirement_id, check_status, revision from app.application_document_checks
                where event_id = ? and application_id = ? and requirement_id = ? for update
                """, eventId, id, requirementId);
    }

    public void documentCheck(UUID id, UUID requirementId, UUID actor, DocumentCheck request) {
        boolean reviewed = List.of("VERIFIED", "NEEDS_CORRECTION").contains(request.checkStatus());
        sql.update("""
                update app.application_document_checks set check_status = ?, review_note = ?, checked_by = ?,
                    checked_at = case when ? then now() else null end, revision = revision + 1, updated_at = now()
                where application_id = ? and requirement_id = ?
                """, request.checkStatus(), request.reviewNote(), reviewed ? actor : null, reviewed, id, requirementId);
    }

    public boolean requiredDocumentsVerified(UUID eventId, Map<String, Object> application) {
        return sql.count("""
                select count(*) from app.recruitment_document_requirements r
                left join app.application_document_checks c on c.requirement_id = r.id and c.application_id = ?
                where r.event_id = ? and r.is_required and (cardinality(r.applicable_category_codes) = 0
                    or ? = any(r.applicable_category_codes)) and c.check_status is distinct from 'VERIFIED'
                """, application.get("applicationId"), eventId, application.get("categoryCodeSnapshot")) == 0;
    }

    public void decide(UUID id, UUID actor, Decision request) {
        sql.update("""
                update app.booth_applications set review_status = ?, rejection_reason = ?,
                    review_note = coalesce(?, review_note), reviewed_by = ?, reviewed_at = now(),
                    revision = revision + 1, updated_at = now() where id = ?
                """, request.decision(), request.rejectionReason(), request.reviewNote(), actor, id);
    }

    public void membership(UUID boothId, UUID applicant) {
        sql.update("insert into app.booth_memberships (event_booth_id, account_id) values (?, ?)", boothId, applicant);
    }

    public void decisionAudit(UUID eventId, UUID actor, UUID applicationId, String requestHash, long revision) {
        sql.update("""
                insert into app.audit_logs (id, organization_id, event_id, actor_account_id, actor_type,
                    action, target_type, target_id, allowed_changes)
                select ?, organization_id, id, ?, 'ACCOUNT', 'APPLICATION_DECIDED', 'BOOTH_APPLICATION', ?,
                    jsonb_build_object('requestHash', ?::text, 'revision', ?::bigint) from app.events where id = ?
                """, UUID.randomUUID(), actor, applicationId, requestHash, revision, eventId);
    }

    public void cancel(UUID event,UUID id,UUID actor,String reason) {
        sql.update("update app.booth_applications set review_status='CANCELLED',cancelled_at=now(),cancelled_by=?,cancel_reason=?,revision=revision+1,updated_at=now() where id=?",actor,reason,id);
        sql.update("update app.event_booths set visibility_status='ARCHIVED',operation_status='CLOSED',revision=revision+1,updated_at=now() where event_id=? and application_id=?",event,id);
        sql.update("update app.booth_memberships set membership_status='REVOKED',revoked_at=now() where event_booth_id in (select id from app.event_booths where application_id=?) and membership_status='ACTIVE'",id);
        sql.update("""
            insert into app.audit_logs(id,organization_id,event_id,actor_account_id,actor_type,action,target_type,target_id)
            select ?,organization_id,id,?,'ACCOUNT','BOOTH_APPLICATION_CANCELLED','BOOTH_APPLICATION',? from app.events where id=?
            """,UUID.randomUUID(),actor,id,event);
    }
    public Map<String, Object> decisionAudit(UUID eventId, UUID applicationId) {
        return sql.optional("""
                select allowed_changes ->> 'requestHash' as request_hash,
                    (allowed_changes ->> 'revision')::bigint as revision
                from app.audit_logs where event_id = ? and target_id = ? and target_type = 'BOOTH_APPLICATION'
                    and action = 'APPLICATION_DECIDED' order by occurred_at, id limit 1
                """, eventId, applicationId);
    }
}
