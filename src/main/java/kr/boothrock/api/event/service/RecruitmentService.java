package kr.boothrock.api.event.service;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import kr.boothrock.api.common.repository.SqlStore;
import kr.boothrock.api.common.service.ApiException;
import kr.boothrock.api.common.service.EventAccess;
import kr.boothrock.api.common.service.Rules;
import kr.boothrock.api.event.dto.EventRequests;
import kr.boothrock.api.event.repository.EventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly=true)
public class RecruitmentService {
    private final SqlStore db;
    private final EventAccess access;
    private final EventRepository events;
    private final EventService eventService;
    public RecruitmentService(SqlStore db,EventAccess access,EventRepository events,EventService eventService) {
        this.db=db; this.access=access; this.events=events; this.eventService=eventService;
    }
    public Map<String,Object> get(UUID eventId,UUID actor) {
        access.requireManager(eventId,actor);
        return withStatus(events.recruitment(eventId));
    }
    @Transactional
    public Map<String,Object> save(UUID eventId,UUID actor,EventRequests.Recruitment request) {
        access.requireManager(eventId,actor); access.lockEvent(eventId);
        Map<String,Object> existing=events.recruitment(eventId);
        Rules.revision(request.revision(),existing);
        Rules.enumValue(request.publicationStatus(),"DRAFT","PUBLISHED");
        validateCategories(request.allowedCategoryCodes());
        Rules.input(!"PUBLISHED".equals(request.publicationStatus())||(request.closesAt()!=null&&!request.allowedCategoryCodes().isEmpty()),
                "Published recruitment requires a closing time and categories.");
        var names=new HashSet<String>(); var ids=new HashSet<UUID>();
        for(var document:request.documentRequirements()) {
            Rules.input(names.add(document.name().trim()),"Document names must be unique.");
            validateCategories(document.applicableCategoryCodes());
            if(document.id()!=null) {
                Rules.input(ids.add(document.id()),"Document IDs must be unique.");
                db.one("SELECT id FROM app.recruitment_document_requirements WHERE id=? AND event_id=?",document.id(),eventId);
            }
        }
        boolean locked=db.count("SELECT count(*) FROM app.booth_applications WHERE event_id=?",eventId)>0;
        if(locked) {
            Rules.require(new HashSet<>((List<?>)existing.get("allowedCategoryCodes")).equals(new HashSet<>(request.allowedCategoryCodes()))
                            &&Objects.equals(existing.get("participationFeeKrw"),request.participationFeeKrw())
                            &&sameDocuments(events.documents(eventId),request.documentRequirements()),
                    "RECRUITMENT_TERMS_LOCKED","Categories, fee and documents cannot change after the first application.");
        } else {
            db.update("DELETE FROM app.recruitment_document_requirements WHERE event_id=?",eventId);
            for(var document:request.documentRequirements()) db.update("""
                    INSERT INTO app.recruitment_document_requirements(id,event_id,name,is_required,applicable_category_codes,sort_order)
                    VALUES (?,?,?,?,?,?)
                    """,document.id()==null?UUID.randomUUID():document.id(),eventId,document.name().trim(),document.isRequired(),
                    document.applicableCategoryCodes().toArray(String[]::new),document.sortOrder());
        }
        db.update("""
                UPDATE app.event_recruitments SET introduction=?,publication_status=?,closes_at=?,target_booth_count=?,
                    allowed_category_codes=?,participation_fee_krw=?,fee_note=?,revision=revision+1,updated_at=now() WHERE event_id=?
                """,request.introduction(),request.publicationStatus(),request.closesAt(),request.targetBoothCount(),
                request.allowedCategoryCodes().toArray(String[]::new),request.participationFeeKrw(),request.feeNote(),eventId);
        events.audit(eventId,actor,"RECRUITMENT_UPDATED","EVENT_RECRUITMENT",eventId);
        return get(eventId,actor);
    }
    public Map<String,Object> publicRecruitment(UUID eventId) {
        access.requirePublic(eventId);
        Map<String,Object> recruitment=events.recruitment(eventId);
        if(!"PUBLISHED".equals(recruitment.get("publicationStatus")))throw ApiException.notFound();
        return publicData(eventService.publicEvent(eventId),recruitment);
    }
    public Map<String,Object> invitationRecruitment(UUID eventId) {
        Map<String,Object> event=db.one("""
                SELECT e.id,e.name,e.venue,e.starts_at,e.ends_at,e.description,e.poster_asset_id,o.name AS organization_name
                FROM app.events e JOIN app.organizations o ON o.id=e.organization_id
                WHERE e.id=? AND e.publication_status<>'ARCHIVED' AND o.organization_status='ACTIVE'
                """,eventId);
        // An invitation may expose a draft event's text, but not its private media URL.
        event.remove("posterAssetId");
        return publicData(event,events.recruitment(eventId));
    }
    private Map<String,Object> publicData(Map<String,Object> event,Map<String,Object> row) {
        Map<String,Object> result=withStatus(row);
        result.remove("revision");
        result.put("event",event);
        return result;
    }
    private Map<String,Object> withStatus(Map<String,Object> row) {
        String status="DRAFT";
        if("PUBLISHED".equals(row.get("publicationStatus"))) {
            Instant closing=(Instant)row.get("closesAt");
            status=closing!=null&&closing.isAfter(Instant.now())?"OPEN":"CLOSED";
        }
        row.put("recruitmentStatus",status);
        return row;
    }
    private void validateCategories(List<String> values) {
        values.forEach(Rules::category);
        Rules.input(new HashSet<>(values).size()==values.size(),"Categories must not repeat.");
    }
    private boolean sameDocuments(List<Map<String,Object>> existing,List<EventRequests.Document> requested) {
        if(existing.size()!=requested.size())return false;
        for(var document:requested) {
            Map<String,Object> row=existing.stream().filter(item->Objects.equals(item.get("id"),document.id())).findFirst().orElse(null);
            if(row==null||!Objects.equals(row.get("name"),document.name().trim())||!Objects.equals(row.get("isRequired"),document.isRequired())
                    ||!Objects.equals(row.get("sortOrder"),document.sortOrder())
                    ||!new HashSet<>((List<?>)row.get("applicableCategoryCodes")).equals(new HashSet<>(document.applicableCategoryCodes())))return false;
        }
        return true;
    }
}
