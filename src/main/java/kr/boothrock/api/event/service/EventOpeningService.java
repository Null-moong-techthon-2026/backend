package kr.boothrock.api.event.service;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import kr.boothrock.api.common.repository.SqlStore;
import kr.boothrock.api.common.service.EventAccess;
import kr.boothrock.api.common.service.Rules;
import kr.boothrock.api.event.controller.EventOpeningController.Opening;
import kr.boothrock.api.event.controller.EventOpeningController.OpeningDecision;
import kr.boothrock.api.event.dto.EventRequests;
import kr.boothrock.api.event.repository.EventOpeningRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class EventOpeningService {
    private final SqlStore db;
    private final EventAccess access;
    private final EventService events;
    private final EventOpeningRepository openings;
    public EventOpeningService(SqlStore db,EventAccess access,EventService events,EventOpeningRepository openings) {
        this.db=db;this.access=access;this.events=events;this.openings=openings;
    }
    @Transactional
    public Map<String,Object> open(UUID actor,Opening r) {
        access.requireAccount(actor);
        Rules.input(r.endsAt().isAfter(r.startsAt()),"endsAt must follow startsAt");
        String type=r.orgType()==null?"OTHER":r.orgType();
        Rules.enumValue(type,"COMPANY","STUDENT","PUBLIC","NONPROFIT","OTHER");
        db.one("select id from app.accounts where id=? for update",actor);
        Rules.require(db.count("select count(*) from app.event_applications where applicant_account_id=? and status='PENDING'",actor)==0,
            "DUPLICATE_APPLICATION","An opening application is already pending");
        UUID id=UUID.randomUUID();
        db.update("""
            insert into app.event_applications(id,applicant_account_id,org_name,org_type,event_name,venue,starts_at,ends_at,starts_on,ends_on)
            values (?,?,?,?,?,?,?,?,?,?)
            """,id,actor,r.orgName().trim(),type,r.eventName().trim(),r.venue().trim(),r.startsAt(),r.endsAt(),
            r.startsAt().atZone(ZoneId.of("Asia/Seoul")).toLocalDate(),r.endsAt().atZone(ZoneId.of("Asia/Seoul")).toLocalDate());
        var row=openings.one(id,false);
        return Rules.result("id",id,"status",row.get("status"),"createdAt",row.get("createdAt"),"revision",row.get("revision"));
    }
    public Map<String,Object> mine(UUID actor,int page,int size) {
        access.requireAccount(actor);return openings.list(actor,null,page,size);
    }
    public Map<String,Object> list(UUID actor,String status,int page,int size) {
        access.requirePlatformAdmin(actor);if(status!=null)Rules.enumValue(status,"PENDING","APPROVED","REJECTED");
        var result=openings.list(null,status,page,size);
        for(Object item:(java.util.List<?>)result.get("content")) {
            @SuppressWarnings("unchecked") var row=(Map<String,Object>)item;
            row.put("applicant",db.one("select id,nickname,email,phone_number from app.accounts where id=?",row.get("applicantAccountId")));
        }
        return result;
    }
    public Map<String,Object> detail(UUID actor,UUID id) {
        access.requirePlatformAdmin(actor);return detailResult(id);
    }
    private Map<String,Object> detailResult(UUID id) {
        var row=openings.one(id,false);
        row.put("applicant",db.one("select id,nickname,email,phone_number from app.accounts where id=?",row.get("applicantAccountId")));
        return row;
    }
    @Transactional
    public Map<String,Object> decide(UUID actor,UUID id,OpeningDecision r) {
        access.requirePlatformAdmin(actor);
        var row=openings.one(id,true);
        Rules.require("PENDING".equals(row.get("status")),"INVALID_STATE","This opening application is already decided");
        Rules.revision(r.revision(),row);Rules.enumValue(r.decision(),"APPROVED","REJECTED");
        UUID event=null,org=null;
        if(r.decision().equals("REJECTED")) {
            Rules.input(r.rejectionReason()!=null&&!r.rejectionReason().isBlank(),"A rejection reason is required");
        } else {
            Rules.input(r.rejectionReason()==null||r.rejectionReason().isBlank(),"An approval cannot have a rejection reason");
            UUID applicant=SqlStore.uuid(row,"applicantAccountId");
            db.one("select id from app.accounts where id=? for update",applicant);
            Rules.require(db.count("select count(*) from app.accounts where id=? and account_status='ACTIVE'",applicant)==1,
                "INVALID_STATE","The applicant is no longer active");
            org=UUID.randomUUID();
            db.update("insert into app.organizations(id,name,org_type,created_by) values (?,?,?,?)",org,row.get("orgName"),row.get("orgType"),applicant);
            db.update("insert into app.organization_memberships(organization_id,account_id,role) values (?,?,'OWNER')",org,applicant);
            var created=events.create(org,applicant,new EventRequests.Create((String)row.get("eventName"),(String)row.get("venue"),
                (Instant)row.get("startsAt"),(Instant)row.get("endsAt"),null));
            event=SqlStore.uuid(created,"id");
            db.update("""
                insert into app.audit_logs(id,organization_id,event_id,actor_account_id,actor_type,action,target_type,target_id)
                values (?,?,?,?,'ACCOUNT','EVENT_OPENING_APPROVED','EVENT_APPLICATION',?)
                """,UUID.randomUUID(),org,event,actor,id);
        }
        db.update("""
            update app.event_applications set status=?,reviewed_at=now(),reviewed_by=?,rejection_reason=?,
                created_organization_id=?,created_event_id=?,revision=revision+1,updated_at=now() where id=?
            """,r.decision(),actor,r.decision().equals("REJECTED")?r.rejectionReason().trim():"",org,event,id);
        return detailResult(id);
    }
}
