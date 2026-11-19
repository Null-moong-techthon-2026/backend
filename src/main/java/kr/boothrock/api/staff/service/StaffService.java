package kr.boothrock.api.staff.service;

import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import kr.boothrock.api.common.repository.SqlStore;
import kr.boothrock.api.common.service.*;
import kr.boothrock.api.staff.dto.StaffRequests.*;
import kr.boothrock.api.staff.repository.StaffRepository;
import kr.boothrock.api.map.service.MediaService;
import kr.boothrock.api.map.storage.MediaStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class StaffService {
    private static final ZoneId ZONE=ZoneId.of("Asia/Seoul");
    private static final SecureRandom RANDOM=new SecureRandom();
    private final SqlStore db;
    private final EventAccess access;
    private final StaffRepository staff;
    private final String inviteBase;
    private final MediaService media;
    private final MediaStorage storage;
    public StaffService(SqlStore db,EventAccess access,StaffRepository staff, MediaService media, MediaStorage storage,
            @Value("${boothrock.frontend.staff-invite-base-url:http://localhost:5173/invite/staff}") String inviteBase) {
        this.db=db;this.access=access;this.staff=staff;this.media=media;this.storage=storage;this.inviteBase=inviteBase.replaceAll("/+$","");
    }
    private Map<String,Object> event(UUID event) {
        return db.one("""
            select e.* from app.events e join app.organizations o on o.id=e.organization_id
            where e.id=? and e.publication_status<>'ARCHIVED' and o.organization_status='ACTIVE'
            """,event);
    }
    private void manager(UUID event,UUID actor,boolean lock) {
        access.requireManager(event,actor);
        if(lock) {access.lockEvent(event);access.requireManager(event,actor);}
        event(event);
    }
    private static void role(String role) {Rules.enumValue(role,"STAFF","ADMIN","LABOR","VOLUNTEER");}
    private static boolean approved(Map<String,Object> m) {return !List.of("PENDING","REJECTED").contains(m.get("status"));}
    private Map<String,Object> mine(UUID event,UUID actor,boolean lock) {
        access.requireAccount(actor);
        if(lock)access.lockEvent(event);
        event(event);
        var m=staff.mine(event,actor);
        if(!approved(m))throw ApiException.forbidden();
        return m;
    }
    private Map<String,Object> invitation(UUID event) {
        return db.one("select * from app.staff_invitations where event_id=? and revoked_at is null and expires_at>now()",event);
    }
    private Map<String,Object> inviteResult(Map<String,Object> row) {
        return Rules.result("code",row.get("code"),"inviteUrl",inviteBase+"/"+row.get("linkToken"),"expiresAt",row.get("expiresAt"));
    }
    @Transactional
    public Map<String,Object> issue(UUID event,UUID actor) {
        manager(event,actor,true);
        Instant expiry=Instant.now().plus(Duration.ofDays(14));
        Instant end=(Instant)event(event).get("endsAt");
        if(end!=null&&end.isBefore(expiry))expiry=end;
        Rules.require(expiry.isAfter(Instant.now()),"EVENT_ENDED","The event has ended");
        db.update("update app.staff_invitations set revoked_at=now() where event_id=? and revoked_at is null",event);
        byte[] bytes=new byte[10];RANDOM.nextBytes(bytes);
        String code="BL-"+HexFormat.of().withUpperCase().formatHex(bytes);
        bytes=new byte[24];RANDOM.nextBytes(bytes);
        String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        db.update("insert into app.staff_invitations(id,event_id,code,link_token,expires_at) values (?,?,?,?,?)",UUID.randomUUID(),event,code,token,expiry);
        return inviteResult(invitation(event));
    }
    public Map<String,Object> getInvitation(UUID event,UUID actor) {manager(event,actor,false);return inviteResult(invitation(event));}
    @Transactional
    public void revoke(UUID event,UUID actor) {manager(event,actor,true);db.update("update app.staff_invitations set revoked_at=now() where event_id=? and revoked_at is null",event);}
    private Map<String,Object> resolveRow(String code) {
        var row=db.one("select * from app.staff_invitations where (code=? or link_token=?) and revoked_at is null and expires_at>now()",code,code);
        event(SqlStore.uuid(row,"eventId"));return row;
    }
    public Map<String,Object> resolve(UUID actor,String code) {
        access.requireAccount(actor);
        UUID id=SqlStore.uuid(resolveRow(code),"eventId");var e=event(id);
        return Rules.result("id",id,"eventId",id,"name",e.get("name"),"startsAt",e.get("startsAt"),"endsAt",e.get("endsAt"),"venue",e.get("venue"),
            "posterUrl",e.get("posterAssetId")==null?null:"/api/staff-invitations/"+code+"/poster");
    }
    public MediaService.MediaContent poster(UUID actor,String code) {
        access.requireAccount(actor);
        UUID event=SqlStore.uuid(resolveRow(code),"eventId");
        UUID asset=SqlStore.uuid(event(event),"posterAssetId");
        if(asset==null)throw ApiException.notFound();
        var m=media.requireAsset(event,asset);
        return new MediaService.MediaContent((String)m.get("mimeType"),storage.read((String)m.get("bucket"),
            (String)m.get("objectKey"),((Number)m.get("sizeBytes")).longValue()));
    }
    @Transactional
    public Map<String,Object> apply(UUID event,UUID actor,Apply r) {
        access.requireAccount(actor);access.lockEvent(event);event(event);
        Rules.enumValue(r.role(),"STAFF","LABOR","VOLUNTEER");
        if(!event.equals(SqlStore.uuid(resolveRow(r.code()),"eventId")))throw ApiException.notFound();
        Rules.require(db.count("select count(*) from app.staff_members where event_id=? and account_id=?",event,actor)==0,"DUPLICATE_APPLICATION","Already applied to this event");
        UUID id=UUID.randomUUID();
        db.update("insert into app.staff_members(id,event_id,account_id,role,affiliation) values (?,?,?,?,?)",id,event,actor,r.role(),Rules.text(r.affiliation()));
        return own(staff.member(event,id));
    }
    private Map<String,Object> own(Map<String,Object> m) {m.remove("memo");return m;}
    public List<Map<String,Object>> memberships(UUID actor) {
        access.requireAccount(actor);
        return db.list("""
            select m.event_id,m.id as member_id,m.role,m.status,m.affiliation,m.duty_id,d.zone,d.name as duty_name,e.name as event_name
            from app.staff_members m join app.events e on e.id=m.event_id join app.organizations o on o.id=e.organization_id
            left join app.staff_duties d on d.id=m.duty_id
            where m.account_id=? and e.publication_status<>'ARCHIVED' and o.organization_status='ACTIVE' order by m.created_at desc,m.id
            """,actor).stream().map(row->{row.put("duty",row.get("dutyId")==null?null:Rules.result("id",row.get("dutyId"),"zone",row.get("zone"),"name",row.get("dutyName")));row.remove("zone");row.remove("dutyName");return row;}).toList();
    }
    public Map<String,Object> roster(UUID event,UUID actor,String role,String status,String q,int page,int size) {
        manager(event,actor,false);if(role!=null)role(role);if(status!=null)Rules.enumValue(status,"PENDING","REJECTED","WAITING","WORKING","BREAK","ABSENT");
        return staff.roster(event,role,status,q,page,size);
    }
    @Transactional
    public Map<String,Object> decide(UUID event,UUID actor,UUID member,Decision r) {
        manager(event,actor,true);Rules.enumValue(r.decision(),"APPROVED","REJECTED");
        var m=staff.member(event,member);
        String target=r.decision().equals("APPROVED")?"WAITING":"REJECTED";
        if(!m.get("status").equals("PENDING")) {
            Rules.require(target.equals(m.get("status")),"INVALID_STATE","Application already decided");return m;
        }
        if(r.revision()!=null)Rules.revision(r.revision(),m);
        db.update("update app.staff_members set status=?,revision=revision+1,updated_at=now() where id=?",target,member);
        return staff.member(event,member);
    }
    @Transactional
    public Map<String,Object> patch(UUID event,UUID actor,UUID member,PatchMember r) {
        manager(event,actor,true);var m=staff.member(event,member);Rules.revision(r.revision(),m);
        if(r.role()!=null) {
            role(r.role());
            Rules.require(db.count("select count(*) from app.staff_shift_members sm join app.staff_shifts s on s.id=sm.shift_id where sm.member_id=? and (s.shift_date+s.ends_at) at time zone 'Asia/Seoul' > now() and not (?=any(s.roles))",member,r.role())==0,
                "ROLE_NOT_ALLOWED","Remove incompatible current or future shift assignments before changing role");
            db.update("update app.staff_shift_members set role_at_assignment=? where member_id=? and role_at_assignment is null",m.get("role"),member);
            db.update("""
                update app.staff_shift_members sm set role_at_assignment=? from app.staff_shifts s
                where sm.shift_id=s.id and sm.member_id=? and (s.shift_date+s.starts_at) at time zone 'Asia/Seoul'>now()
                """,r.role(),member);
        }
        if(r.status()!=null) {
            Rules.enumValue(r.status(),"WAITING","WORKING","BREAK","ABSENT");
            Rules.require(approved(m),"APPROVAL_REQUIRED","Use the decision endpoint first");
            boolean open=db.count("select count(*) from app.staff_attendance where member_id=? and clock_out is null",member)>0;
            boolean onDuty=Set.of("WORKING","BREAK").contains(r.status());
            Rules.require(open==onDuty,"ATTENDANCE_CONFLICT",open
                ? "Clock out before changing to WAITING or ABSENT."
                : "Clock in before changing to WORKING or BREAK.");
        }
        UUID duty=null;
        if(r.dutyId()!=null&&!r.dutyId().isNull()) {
            Rules.input(r.dutyId().isString(),"dutyId must be a UUID or null");
            try {duty=UUID.fromString(r.dutyId().asString());}catch(IllegalArgumentException e){throw new ApiException(400,"VALIDATION_ERROR","Invalid dutyId");}
            duty(event,duty);
        }
        db.update("""
            update app.staff_members set role=coalesce(?,role),status=coalesce(?,status),memo=coalesce(?,memo),
            duty_id=case when ? then ?::uuid else duty_id end,revision=revision+1,updated_at=now() where id=?
            """,r.role(),r.status(),r.memo(),r.dutyId()!=null,duty,member);
        return staff.member(event,member);
    }
    private Map<String,Object> duty(UUID event,UUID id) {return db.one("select id,zone,name,revision from app.staff_duties where event_id=? and id=?",event,id);}
    public List<Map<String,Object>> duties(UUID event,UUID actor) {manager(event,actor,false);return db.list("select id,zone,name,revision from app.staff_duties where event_id=? order by zone,name,id",event);}
    @Transactional
    public Map<String,Object> createDuty(UUID event,UUID actor,Duty r) {
        manager(event,actor,true);
        Rules.require(db.count("select count(*) from app.staff_duties where event_id=? and zone=? and name=?",event,r.zone().trim(),r.name().trim())==0,"DUPLICATE_DUTY","Duty already exists");
        UUID id=UUID.randomUUID();db.update("insert into app.staff_duties(id,event_id,zone,name) values (?,?,?,?)",id,event,r.zone().trim(),r.name().trim());return duty(event,id);
    }
    @Transactional
    public Map<String,Object> updateDuty(UUID event,UUID actor,UUID id,UpdateDuty r) {
        manager(event,actor,true);Rules.revision(r.revision(),duty(event,id));
        Rules.require(db.count("select count(*) from app.staff_duties where event_id=? and zone=? and name=? and id<>?",event,r.zone().trim(),r.name().trim(),id)==0,
            "DUPLICATE_DUTY","Duty already exists");
        // Freeze missing legacy names before renaming; only unstarted shifts follow the new label.
        db.update("""
            update app.staff_shifts s set duty_zone_snapshot=coalesce(s.duty_zone_snapshot,d.zone),
            duty_name_snapshot=coalesce(s.duty_name_snapshot,d.name) from app.staff_duties d
            where s.duty_id=d.id and s.duty_id=? and s.event_id=?
            """,id,event);
        db.update("update app.staff_duties set zone=?,name=?,revision=revision+1 where id=?",r.zone().trim(),r.name().trim(),id);
        db.update("""
            update app.staff_shifts set duty_zone_snapshot=?,duty_name_snapshot=?,revision=revision+1
            where duty_id=? and event_id=? and (shift_date+starts_at) at time zone 'Asia/Seoul'>now()
            """,r.zone().trim(),r.name().trim(),id,event);
        return duty(event,id);
    }
    @Transactional
    public void deleteDuty(UUID event,UUID actor,UUID id,long revision) {
        manager(event,actor,true);Rules.input(revision>=0,"revision must be nonnegative");Rules.revision(revision,duty(event,id));
        Rules.require(db.count("select count(*) from app.staff_shifts where duty_id=?",id)==0
                &&db.count("select count(*) from app.staff_members where duty_id=?",id)==0,
            "DUTY_IN_USE","A duty referenced by members or shift history cannot be deleted.");
        db.update("delete from app.staff_duties where id=?",id);
    }
    public List<Map<String,Object>> shifts(UUID event,UUID actor,LocalDate date) {manager(event,actor,false);return staff.shifts(event,date,null);}
    private Map<String,Object> shift(UUID event,UUID id) {
        var row=db.one("select id,shift_date::text as date,roles,revision from app.staff_shifts where event_id=? and id=?",event,id);
        return staff.shifts(event,LocalDate.parse((String)row.get("date")),null).stream().filter(s->s.get("id").equals(id)).findFirst().orElseThrow(ApiException::notFound);
    }
    @Transactional
    public Map<String,Object> createShift(UUID event,UUID actor,Shift r) {
        manager(event,actor,true);var duty=validateShift(event,r.date(),r.dutyId(),r.start(),r.end(),r.roles());
        UUID id=UUID.randomUUID();db.update("""
            insert into app.staff_shifts(id,event_id,shift_date,duty_id,starts_at,ends_at,roles,duty_zone_snapshot,duty_name_snapshot)
            values (?,?,?,?,?,?,?::text[],?,?)
            """,id,event,r.date(),r.dutyId(),r.start(),r.end(),"{"+String.join(",",r.roles())+"}",duty.get("zone"),duty.get("name"));return shift(event,id);
    }
    private Map<String,Object> validateShift(UUID event,LocalDate date,UUID duty,LocalTime start,LocalTime end,List<String> roles) {
        var d=duty(event,duty);
        Rules.input(end.isAfter(start),"A shift must finish later on the same date");
        Rules.input(new HashSet<>(roles).size()==roles.size(),"roles must be unique");roles.forEach(StaffService::role);
        var e=event(event);Instant eventStart=(Instant)e.get("startsAt"),eventEnd=(Instant)e.get("endsAt");
        Rules.input((eventStart==null||!date.isBefore(eventStart.atZone(ZONE).toLocalDate()))
            &&(eventEnd==null||!date.isAfter(eventEnd.atZone(ZONE).toLocalDate())),"Shift date must be within the event dates");
        return d;
    }
    private void requireUnstarted(Map<String,Object> s) {
        Instant start=LocalDate.parse((String)s.get("date")).atTime(LocalTime.parse((String)s.get("start"))).atZone(ZONE).toInstant();
        Rules.require(start.isAfter(Instant.now()),"SHIFT_ALREADY_STARTED","Started shifts are retained as history.");
    }
    @Transactional
    public Map<String,Object> updateShift(UUID event,UUID actor,UUID id,UpdateShift r) {
        manager(event,actor,true);var s=shift(event,id);Rules.revision(r.revision(),s);requireUnstarted(s);
        var d=validateShift(event,r.date(),r.dutyId(),r.start(),r.end(),r.roles());
        Rules.input(r.date().atTime(r.start()).atZone(ZONE).toInstant().isAfter(Instant.now()),"Updated shift must start in the future");
        var assigned=db.list("select member_id from app.staff_shift_members where shift_id=?",id);
        for(var row:assigned) {
            UUID member=SqlStore.uuid(row,"memberId");var m=staff.member(event,member);
            Rules.require(r.roles().contains(m.get("role")),"ROLE_NOT_ALLOWED","Assigned member role is not allowed in this shift");
            Rules.require(db.count("""
                select count(*) from app.staff_shift_members sm join app.staff_shifts s on s.id=sm.shift_id
                where sm.member_id=? and s.id<>? and s.shift_date=? and s.starts_at<? and s.ends_at>?
                """,member,id,r.date(),r.end(),r.start())==0,"SHIFT_OVERLAP","Member already has an overlapping shift");
        }
        db.update("""
            update app.staff_shifts set shift_date=?,duty_id=?,starts_at=?,ends_at=?,roles=?::text[],
            duty_zone_snapshot=?,duty_name_snapshot=?,revision=revision+1 where id=?
            """,r.date(),r.dutyId(),r.start(),r.end(),"{"+String.join(",",r.roles())+"}",d.get("zone"),d.get("name"),id);
        return shift(event,id);
    }
    @Transactional
    public void deleteShift(UUID event,UUID actor,UUID id,long revision) {
        manager(event,actor,true);Rules.input(revision>=0,"revision must be nonnegative");
        var s=shift(event,id);Rules.revision(revision,s);requireUnstarted(s);
        db.update("delete from app.staff_shift_members where shift_id=?",id);
        db.update("delete from app.staff_shifts where id=?",id);
    }
    @Transactional
    public Map<String,Object> assign(UUID event,UUID actor,UUID shift,Members r) {
        manager(event,actor,true);var s=shift(event,shift);if(r.revision()!=null)Rules.revision(r.revision(),s);
        Rules.require(LocalDate.parse((String)s.get("date")).atTime(LocalTime.parse((String)s.get("end"))).atZone(ZONE).toInstant().isAfter(Instant.now()),
            "SHIFT_ENDED","Finished shift assignments are retained as history.");
        Rules.input(new HashSet<>(r.memberIds()).size()==r.memberIds().size(),"memberIds must be unique");
        @SuppressWarnings("unchecked") List<String> roles=(List<String>)s.get("roles");
        for(UUID id:r.memberIds()) {
            var m=staff.member(event,id);Rules.require(approved(m),"APPROVAL_REQUIRED","Only approved members can be assigned");
            Rules.require(roles.contains(m.get("role")),"ROLE_NOT_ALLOWED","Member role is not allowed in this shift");
            access.requireAccount(SqlStore.uuid(m,"accountId"));
            Rules.require(db.count("""
                select count(*) from app.staff_shift_members sm join app.staff_shifts other on other.id=sm.shift_id
                join app.staff_shifts current on current.id=? where sm.member_id=? and other.id<>current.id
                and other.shift_date=current.shift_date and other.starts_at<current.ends_at and other.ends_at>current.starts_at
                """,shift,id)==0,"SHIFT_OVERLAP","Member already has an overlapping shift");
        }
        db.update("delete from app.staff_shift_members where shift_id=?",shift);
        for(UUID id:r.memberIds())db.update("insert into app.staff_shift_members(event_id,shift_id,member_id,role_at_assignment) select ?,?,id,role from app.staff_members where id=?",event,shift,id);
        db.update("update app.staff_shifts set revision=revision+1 where id=?",shift);return shift(event,shift);
    }
    public List<Map<String,Object>> schedule(UUID event,UUID actor,LocalDate date) {var m=mine(event,actor,false);return staff.shifts(event,date,SqlStore.uuid(m,"memberId"));}
    @Transactional
    public Map<String,Object> clock(UUID event,UUID actor,boolean in) {
        var m=mine(event,actor,true);UUID member=SqlStore.uuid(m,"memberId");Instant now=Instant.now();
        LocalDate today=now.atZone(ZONE).toLocalDate();
        var open=db.optional("select id,attendance_date::text as date from app.staff_attendance where member_id=? and clock_out is null",member);
        UUID attendance;
        if(in) {
            Rules.require(open==null,open!=null&&today.toString().equals(open.get("date"))?"ALREADY_CLOCKED_IN":"OPEN_ATTENDANCE",
                "Clock out of the open attendance before clocking in again.");
            attendance=UUID.randomUUID();
            db.update("insert into app.staff_attendance(id,member_id,attendance_date,clock_in) values (?,?,?,?)",attendance,member,today,now);
        } else {
            Rules.require(open!=null,"NOT_CLOCKED_IN","No open attendance");attendance=SqlStore.uuid(open,"id");
            db.update("update app.staff_attendance set clock_out=? where id=?",now,attendance);
        }
        db.update("update app.staff_members set status=?,revision=revision+1,updated_at=now() where id=?",in?"WORKING":"WAITING",member);
        return db.one("select id as attendance_id,attendance_date::text as date,clock_in as \"in\",clock_out as \"out\" from app.staff_attendance where id=?",attendance);
    }
    public Map<String,Object> attendance(UUID event,UUID actor,int page,int size) {
        UUID member=SqlStore.uuid(mine(event,actor,false),"memberId");
        var result=db.page("""
            select id as attendance_id,attendance_date::text as date,clock_in as "in",clock_out as "out",
            floor(extract(epoch from (coalesce(clock_out,now())-clock_in))/60)::bigint as worked_minutes
            from app.staff_attendance where member_id=? order by clock_in desc,id desc
            ""","select count(*) from app.staff_attendance where member_id=?",page,size,member);
        result.put("totalMinutes",db.count("select coalesce(sum(floor(extract(epoch from (coalesce(clock_out,now())-clock_in))/60)),0)::bigint from app.staff_attendance where member_id=?",member));
        var open=db.optional("select id as attendance_id,attendance_date::text as date,clock_in as \"in\",clock_out as \"out\" from app.staff_attendance where member_id=? and clock_out is null",member);
        LocalDate today=LocalDate.now(ZONE);
        var todayRows=db.list("select id as attendance_id,attendance_date::text as date,clock_in as \"in\",clock_out as \"out\" from app.staff_attendance where member_id=? and attendance_date=? order by clock_in desc,id desc",member,today);
        result.put("today",today.toString());result.put("todayAttendance",todayRows.isEmpty()?null:todayRows.getFirst());
        result.put("todayAttendances",todayRows);result.put("openAttendance",open);
        result.put("canClockIn",open==null);result.put("canClockOut",open!=null);
        return result;
    }
    public Map<String,Object> organizerAttendance(UUID event,UUID actor,LocalDate date,UUID member,int page,int size) {
        manager(event,actor,false);Rules.page(page,size);
        if(member!=null)staff.member(event,member);
        String from="""
            from app.staff_attendance a join app.staff_members m on m.id=a.member_id
            join app.accounts account on account.id=m.account_id where m.event_id=? and a.attendance_date=?
            """;
        var args=new ArrayList<Object>();args.add(event);args.add(date);
        if(member!=null){from+=" and m.id=?";args.add(member);}
        return db.page("""
            select a.id as attendance_id,m.id as member_id,account.nickname as name,m.role,
            a.attendance_date::text as date,a.clock_in as "in",a.clock_out as "out",
            floor(extract(epoch from (coalesce(a.clock_out,now())-a.clock_in))/60)::bigint as worked_minutes
            """+from+" order by a.clock_in,m.id,a.id","select count(*) "+from,page,size,args.toArray());
    }
    public Map<String,Object> live(UUID event,UUID actor,String zone,UUID dutyId) {
        manager(event,actor,false);if(dutyId!=null)duty(event,dutyId);
        Rules.input(zone==null||zone.length()<=100,"zone too long");
        LocalDate today=LocalDate.now(ZONE);LocalTime now=LocalTime.now(ZONE);
        var members=db.list("""
            select m.id as member_id,a.nickname as name,m.role,a.phone_number as phone,m.status,m.duty_id,d.zone,d.name as duty_name
            from app.staff_members m join app.accounts a on a.id=m.account_id left join app.staff_duties d on d.id=m.duty_id
            where m.event_id=? and m.status not in ('PENDING','REJECTED') and a.account_status='ACTIVE' order by a.nickname,m.id
            """,event);
        var current=staff.shifts(event,today,null).stream().filter(s->!now.isBefore(LocalTime.parse((String)s.get("start")))&&now.isBefore(LocalTime.parse((String)s.get("end")))).toList();
        Map<Object,Map<String,Object>> shiftByMember=new HashMap<>();
        for(var s:current) {
            @SuppressWarnings("unchecked") var assigned=(List<Map<String,Object>>)s.get("members");
            assigned.forEach(m->shiftByMember.put(m.get("memberId"),s));
        }
        for(var m:members) {
            var s=shiftByMember.get(m.get("memberId"));
            if(s!=null) {m.put("dutyId",s.get("dutyId"));m.put("zone",s.get("zone"));m.put("start",s.get("start"));m.put("end",s.get("end"));
                @SuppressWarnings("unchecked") var d=(Map<String,Object>)s.get("duty");m.put("dutyName",d.get("name"));}
        }
        members=members.stream().filter(m->(zone==null||zone.equals(m.get("zone")))&&(dutyId==null||dutyId.equals(m.get("dutyId")))).toList();
        Map<String,Object> summary=new LinkedHashMap<>();
        for(String status:List.of("WORKING","BREAK","ABSENT","WAITING"))summary.put(status.toLowerCase(Locale.ROOT),members.stream().filter(m->status.equals(m.get("status"))).count());
        Map<Object,Map<String,Object>> groups=new LinkedHashMap<>();
        List<Map<String,Object>> unassigned=new ArrayList<>();
        for(var m:members) {
            var person=Rules.result("memberId",m.get("memberId"),"name",m.get("name"),"role",m.get("role"),"phone",m.get("phone"),"status",m.get("status"));
            if(m.get("dutyId")==null) {unassigned.add(person);continue;}
            var group=groups.computeIfAbsent(m.get("dutyId"),key->Rules.result("dutyId",key,"name",m.get("dutyName"),"zone",m.get("zone"),"start",m.get("start"),"end",m.get("end"),"members",new ArrayList<Map<String,Object>>()));
            if("WORKING".equals(m.get("status"))) {
                @SuppressWarnings("unchecked") var list=(List<Map<String,Object>>)group.get("members");list.add(person);
            }
        }
        for(var g:groups.values()) {g.put("workingCount",((List<?>)g.get("members")).size());}
        return Rules.result("summary",summary,"duties",new ArrayList<>(groups.values()),"unassigned",unassigned,"asOf",Instant.now());
    }
}
