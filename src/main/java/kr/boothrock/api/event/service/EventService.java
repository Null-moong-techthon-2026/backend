package kr.boothrock.api.event.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.boothrock.api.common.repository.SqlStore;
import kr.boothrock.api.common.service.EventAccess;
import kr.boothrock.api.common.service.Rules;
import kr.boothrock.api.event.dto.EventRequests;
import kr.boothrock.api.event.repository.EventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly=true)
public class EventService {
    private final SqlStore db;
    private final EventRepository events;
    private final EventAccess access;
    public EventService(SqlStore db, EventRepository events, EventAccess access) {
        this.db=db; this.events=events; this.access=access;
    }
    @Transactional
    public Map<String,Object> createLocalOrganization(UUID actor, EventRequests.LocalOrganization request) {
        access.requireAccount(actor);
        UUID id=UUID.randomUUID();
        db.update("INSERT INTO app.organizations(id,name,contact_email,created_by) VALUES (?,?,?,?)",
                id, request.name().trim(), request.contactEmail(), actor);
        db.update("INSERT INTO app.organization_memberships(organization_id,account_id,role) VALUES (?,?,'OWNER')", id,actor);
        return Rules.result("id",id,"name",request.name().trim(),"role","OWNER","localTestOnly",true);
    }
    public List<Map<String,Object>> organizations(UUID actor) {
        access.requireAccount(actor);
        return db.list("""
                SELECT o.id,o.name,m.role FROM app.organizations o JOIN app.organization_memberships m ON m.organization_id=o.id
                WHERE m.account_id=? AND m.membership_status='ACTIVE' AND o.organization_status='ACTIVE' ORDER BY o.name,o.id
                """,actor);
    }
    @Transactional
    public Map<String,Object> create(UUID organizationId, UUID actor, EventRequests.Create request) {
        access.requireOrganizationOwner(organizationId,actor);
        period(request.startsAt(),request.endsAt());
        UUID id=UUID.randomUUID();
        db.update("""
                INSERT INTO app.events(id,organization_id,created_by,name,venue,starts_at,ends_at,description)
                VALUES (?,?,?,?,?,?,?,?)
                """,id,organizationId,actor,request.name().trim(),request.venue(),request.startsAt(),request.endsAt(),Rules.text(request.description()));
        db.update("INSERT INTO app.event_recruitments(event_id) VALUES (?)",id);
        events.audit(id,actor,"EVENT_CREATED","EVENT",id);
        return get(id,actor);
    }
    public Map<String,Object> get(UUID eventId, UUID actor) {
        access.requireParticipant(eventId,actor);
        Map<String,Object> result=present(events.event(eventId),true);
        result.put("capabilities",access.isManager(eventId,actor)
                ? List.of("MANAGE_EVENT","REVIEW_APPLICATIONS","EDIT_MAP","MANAGE_ANNOUNCEMENTS")
                : access.isStaff(eventId,actor) ? List.of("READ_OPERATIONS") : List.of("MANAGE_OWN_BOOTH"));
        return result;
    }
    public Map<String,Object> publicEvent(UUID eventId) {
        access.requirePublic(eventId);
        return present(events.event(eventId),false);
    }
    @Transactional
    public Map<String,Object> update(UUID eventId, UUID actor, EventRequests.Update request) {
        access.requireManager(eventId,actor);
        access.lockEvent(eventId);
        Map<String,Object> row=events.event(eventId);
        Rules.revision(request.revision(),row);
        String name=request.name()==null?(String)row.get("name"):request.name().trim();
        Rules.input(!name.isBlank(),"Event name cannot be blank.");
        String venue=request.venue()==null?(String)row.get("venue"):request.venue();
        Instant start=request.startsAt()==null?(Instant)row.get("startsAt"):request.startsAt();
        Instant end=request.endsAt()==null?(Instant)row.get("endsAt"):request.endsAt();
        String publication=request.publicationStatus()==null?(String)row.get("publicationStatus"):request.publicationStatus();
        Rules.enumValue(publication,"DRAFT","PUBLISHED","ARCHIVED");
        period(start,end);
        Rules.input(!"PUBLISHED".equals(publication)||(venue!=null&&!venue.isBlank()&&start!=null&&end!=null),
                "Published events require venue and both dates.");
        UUID poster=request.posterAssetId()==null?SqlStore.uuid(row,"posterAssetId"):request.posterAssetId();
        if(poster!=null) db.one("SELECT id FROM app.media_assets WHERE id=? AND event_id=?",poster,eventId);
        db.update("""
                UPDATE app.events SET name=?,venue=?,starts_at=?,ends_at=?,description=?,poster_asset_id=?,
                    publication_status=?,revision=revision+1,updated_at=now() WHERE id=?
                """,name,venue,start,end,request.description()==null?row.get("description"):request.description(),poster,publication,eventId);
        events.audit(eventId,actor,"EVENT_UPDATED","EVENT",eventId);
        return get(eventId,actor);
    }
    public Map<String,Object> mine(UUID actor,String participation,int page,int size) {
        access.requireAccount(actor); Rules.page(page,size); Rules.enumValue(participation,"ORGANIZER","OPERATOR");
        String ownership="ORGANIZER".equals(participation)?"""
                EXISTS (SELECT 1 FROM app.organization_memberships om WHERE om.organization_id=e.organization_id
                    AND om.account_id=? AND om.membership_status='ACTIVE' AND (om.role='OWNER' OR EXISTS (
                    SELECT 1 FROM app.event_memberships em WHERE em.event_id=e.id AND em.account_id=om.account_id AND em.membership_status='ACTIVE')))
                """:"""
                EXISTS (SELECT 1 FROM app.event_booths b JOIN app.booth_memberships bm ON bm.event_booth_id=b.id
                    WHERE b.event_id=e.id AND b.visibility_status<>'ARCHIVED' AND bm.account_id=? AND bm.membership_status='ACTIVE')
                """;
        String from=" FROM app.events e JOIN app.organizations o ON o.id=e.organization_id WHERE o.organization_status='ACTIVE' AND "+ownership;
        Map<String,Object> result=db.page("SELECT e.id,e.name,e.venue,e.starts_at,e.ends_at,e.description,e.poster_asset_id,e.publication_status,e.revision,e.organization_id,o.name AS organization_name"+from+" ORDER BY e.created_at DESC,e.id DESC",
                "SELECT count(*)"+from,page,size,actor);
        result.put("content",presentList(result.get("content"),true));
        return result;
    }
    public Map<String,Object> publicEvents(String status,int page,int size) {
        Rules.page(page,size);
        String filter="";
        if(status!=null) {
            Rules.enumValue(status,"OPEN","CLOSED");
            filter="OPEN".equals(status)?" AND r.closes_at>now()":" AND r.closes_at<=now()";
        }
        String from="""
                FROM app.events e JOIN app.organizations o ON o.id=e.organization_id
                JOIN app.event_recruitments r ON r.event_id=e.id
                WHERE e.publication_status='PUBLISHED' AND r.publication_status='PUBLISHED' AND o.organization_status='ACTIVE'
                """+filter;
        Map<String,Object> result=db.page("SELECT e.id,e.name,e.venue,e.starts_at,e.ends_at,e.description,e.poster_asset_id,e.publication_status,e.organization_id,o.name AS organization_name,r.closes_at"+" "+from+" ORDER BY e.starts_at,e.id",
                "SELECT count(*) "+from,page,size);
        result.put("content",presentList(result.get("content"),false));
        return result;
    }
    private List<Map<String,Object>> presentList(Object rows,boolean internal) {
        List<Map<String,Object>> result=new ArrayList<>();
        for(Object row:(List<?>)rows) {
            @SuppressWarnings("unchecked") Map<String,Object> data=(Map<String,Object>)row;
            result.add(present(data,internal));
        }
        return result;
    }
    private Map<String,Object> present(Map<String,Object> row,boolean internal) {
        Instant now=Instant.now(),start=(Instant)row.get("startsAt"),end=(Instant)row.get("endsAt");
        String schedule=start==null||end==null?"UNSCHEDULED":now.isBefore(start)?"UPCOMING":now.isBefore(end)?"ONGOING":"ENDED";
        Map<String,Object> result=Rules.result("id",row.get("id"),"name",row.get("name"),
                "organization",Rules.result("id",row.get("organizationId"),"name",row.get("organizationName")),
                "venue",row.get("venue"),"startsAt",start,"endsAt",end,"scheduleStatus",schedule,
                "description",row.get("description"),"posterUrl",row.get("posterAssetId")==null?null:"/api/media-assets/"+row.get("posterAssetId")+"/content");
        if(internal) { result.put("revision",row.get("revision")); result.put("publicationStatus",row.get("publicationStatus")); result.put("posterAssetId",row.get("posterAssetId")); }
        if(row.containsKey("closesAt")) {
            result.put("closesAt",row.get("closesAt"));
            result.put("recruitmentStatus",((Instant)row.get("closesAt")).isAfter(now)?"OPEN":"CLOSED");
        }
        return result;
    }
    private void period(Instant start,Instant end) { Rules.input(start==null||end==null||start.isBefore(end),"startsAt must be before endsAt."); }
}
