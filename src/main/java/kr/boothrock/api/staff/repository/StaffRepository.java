package kr.boothrock.api.staff.repository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.boothrock.api.common.repository.SqlStore;
import kr.boothrock.api.common.service.Rules;
import org.springframework.stereotype.Repository;

@Repository
public class StaffRepository {
    private static final String FIELDS="""
        m.id as member_id,m.event_id,m.account_id,a.nickname as name,a.phone_number as phone,a.email,
        m.affiliation,m.role,m.status,m.memo,m.duty_id,m.revision,d.zone,d.name as duty_name
        """;
    private static final String FROM="""
        from app.staff_members m join app.accounts a on a.id=m.account_id
        left join app.staff_duties d on d.id=m.duty_id and d.event_id=m.event_id
        """;
    private final SqlStore db;
    public StaffRepository(SqlStore db) { this.db=db; }
    public Map<String,Object> member(UUID event,UUID id) {
        return present(db.one("select "+FIELDS+FROM+" where m.event_id=? and m.id=?",event,id));
    }
    public Map<String,Object> mine(UUID event,UUID actor) {
        return present(db.one("select "+FIELDS+FROM+" where m.event_id=? and m.account_id=?",event,actor));
    }
    public Map<String,Object> roster(UUID event,String role,String status,String q,int page,int size) {
        String where=" where m.event_id=?";
        List<Object> args=new ArrayList<>();args.add(event);
        if(role!=null) {where+=" and m.role=?";args.add(role);}
        if(status!=null) {where+=" and m.status=?";args.add(status);}
        if(q!=null) {where+=" and (a.nickname ilike ? escape '\\' or a.phone_number ilike ? escape '\\')";
            args.add(Rules.like(q));args.add(Rules.like(q));}
        Map<String,Object> result=db.page("select "+FIELDS+FROM+where+" order by m.created_at,m.id",
            "select count(*) "+FROM+where,page,size,args.toArray());
        @SuppressWarnings("unchecked") List<Map<String,Object>> rows=(List<Map<String,Object>>)result.get("content");
        rows.forEach(this::present);
        result.put("summary",db.one("""
            select count(*) as total,count(*) filter(where role='ADMIN') as admin,
            count(*) filter(where role='STAFF') as staff,count(*) filter(where role='LABOR') as labor,
            count(*) filter(where role='VOLUNTEER') as volunteer,count(*) filter(where status='PENDING') as pending
            from app.staff_members where event_id=?
            """,event));
        return result;
    }
    public Map<String,Object> present(Map<String,Object> row) {
        row.put("id",row.get("memberId"));
        row.put("duty",row.get("dutyId")==null?null:Rules.result("id",row.get("dutyId"),"zone",row.get("zone"),"name",row.get("dutyName")));
        row.remove("zone");row.remove("dutyName");return row;
    }
    public List<Map<String,Object>> shifts(UUID event,LocalDate date,UUID member) {
        List<Object> args=new ArrayList<>(List.of(event,date));
        String extra="";
        if(member!=null) {extra=" and exists(select 1 from app.staff_shift_members sm where sm.shift_id=s.id and sm.member_id=?)";args.add(member);}
        var rows=db.list("""
            select s.id,s.shift_date::text as date,s.duty_id,s.starts_at::text as start,s.ends_at::text as end,
            s.roles,s.revision,coalesce(s.duty_zone_snapshot,d.zone) as zone,
            coalesce(s.duty_name_snapshot,d.name) as duty_name from app.staff_shifts s
            join app.staff_duties d on d.id=s.duty_id where s.event_id=? and s.shift_date=?
            """+extra+" order by s.starts_at,s.id",args.toArray());
        if(rows.isEmpty())return rows;
        // One batch for the entire day's assignments; no per-shift contact queries.
        var assignments=db.list("""
            select sm.shift_id,m.id as member_id,a.nickname as name,
            case when (s.shift_date+s.ends_at) at time zone 'Asia/Seoul' <= now()
                 then coalesce(sm.role_at_assignment,m.role) else m.role end as role,
            a.phone_number as phone,m.status
            from app.staff_shift_members sm join app.staff_shifts s on s.id=sm.shift_id
            join app.staff_members m on m.id=sm.member_id join app.accounts a on a.id=m.account_id
            where s.event_id=? and s.shift_date=? and m.status not in ('PENDING','REJECTED') and a.account_status='ACTIVE'
            order by a.nickname,m.id
            """,event,date);
        for(var row:rows) {
            row.put("duty",Rules.result("id",row.get("dutyId"),"zone",row.get("zone"),"name",row.get("dutyName")));
            row.put("members",assignments.stream().filter(a->a.get("shiftId").equals(row.get("id")))
                .map(a->Rules.result("memberId",a.get("memberId"),"name",a.get("name"),"role",a.get("role"),"phone",a.get("phone"),"status",a.get("status"))).toList());
            row.remove("dutyName");
        }
        return rows;
    }
}
