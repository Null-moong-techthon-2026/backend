package kr.boothrock.api.event.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import kr.boothrock.api.common.repository.SqlStore;
import kr.boothrock.api.common.service.ApiException;
import kr.boothrock.api.common.service.EventAccess;
import kr.boothrock.api.common.service.Rules;
import kr.boothrock.api.event.repository.EventRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly=true)
public class ApplicationInvitationService {
    private final SqlStore db;
    private final EventAccess access;
    private final RecruitmentService recruitments;
    private final EventRepository events;
    private final String inviteBaseUrl;
    private final SecureRandom random=new SecureRandom();
    public ApplicationInvitationService(SqlStore db,EventAccess access,RecruitmentService recruitments,
            EventRepository events,@Value("${boothrock.frontend.invite-base-url:http://127.0.0.1:5173/invite}") String inviteBaseUrl) {
        this.db=db; this.access=access; this.recruitments=recruitments; this.events=events; this.inviteBaseUrl=inviteBaseUrl;
    }
    public Map<String,Object> get(UUID eventId,UUID actor) {
        access.requireManager(eventId,actor);
        Map<String,Object> row=db.optional("""
                SELECT i.id,i.created_at,i.expires_at,
                       (i.expires_at>now() AND r.publication_status='PUBLISHED' AND r.closes_at>now()
                           AND e.publication_status<>'ARCHIVED') AS active
                FROM app.event_application_invites i JOIN app.event_recruitments r ON r.event_id=i.event_id
                JOIN app.events e ON e.id=i.event_id WHERE i.event_id=? AND i.revoked_at IS NULL
                """,eventId);
        return Rules.result("invitation",row);
    }
    @Transactional
    public Map<String,Object> create(UUID eventId,UUID actor,Instant expiresAt) {
        access.requireManager(eventId,actor); access.lockEvent(eventId);
        Map<String,Object> terms=db.one("""
                SELECT r.closes_at,r.publication_status,e.publication_status AS event_publication
                FROM app.event_recruitments r JOIN app.events e ON e.id=r.event_id WHERE r.event_id=?
                """,eventId);
        Rules.require("PUBLISHED".equals(terms.get("publicationStatus"))&&!"ARCHIVED".equals(terms.get("eventPublication")),
                "RECRUITMENT_CLOSED","Publish recruitment before issuing an invitation.");
        Instant closes=(Instant)terms.get("closesAt");
        Rules.input(expiresAt.isAfter(Instant.now())&&closes!=null&&!expiresAt.isAfter(closes),"Invitation must expire in the future and no later than recruitment closes.");
        db.update("UPDATE app.event_application_invites SET revoked_at=now() WHERE event_id=? AND revoked_at IS NULL",eventId);
        byte[] bytes=new byte[24]; random.nextBytes(bytes);
        String code=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        UUID id=UUID.randomUUID();
        db.update("INSERT INTO app.event_application_invites(id,event_id,token_hash,created_by,expires_at) VALUES (?,?,?,?,?)",id,eventId,hash(code),actor,expiresAt);
        events.audit(eventId,actor,"APPLICATION_INVITE_ISSUED","APPLICATION_INVITE",id);
        return Rules.result("id",id,"code",code,"inviteUrl",inviteBaseUrl+"/"+code,"expiresAt",expiresAt);
    }
    @Transactional
    public void revoke(UUID eventId,UUID actor) {
        access.requireManager(eventId,actor); access.lockEvent(eventId);
        db.update("UPDATE app.event_application_invites SET revoked_at=now() WHERE event_id=? AND revoked_at IS NULL",eventId);
    }
    public Map<String,Object> resolve(String code) {
        Map<String,Object> invite=db.optional("""
                SELECT i.event_id FROM app.event_application_invites i
                JOIN app.event_recruitments r ON r.event_id=i.event_id JOIN app.events e ON e.id=i.event_id
                JOIN app.organizations o ON o.id=e.organization_id
                WHERE i.token_hash=? AND i.revoked_at IS NULL AND i.expires_at>now()
                  AND r.publication_status='PUBLISHED' AND r.closes_at>now()
                  AND e.publication_status<>'ARCHIVED' AND o.organization_status='ACTIVE'
                """,hash(code));
        if(invite==null)throw ApiException.notFound();
        return recruitments.invitationRecruitment(SqlStore.uuid(invite,"eventId"));
    }
    private String hash(String code) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(code.getBytes(StandardCharsets.UTF_8))); }
        catch(NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
