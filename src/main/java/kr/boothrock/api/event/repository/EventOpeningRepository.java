package kr.boothrock.api.event.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.boothrock.api.common.repository.SqlStore;
import org.springframework.stereotype.Repository;

@Repository
public class EventOpeningRepository {
    private static final String FIELDS="""
        r.id,r.applicant_account_id,r.org_name,r.org_type,r.event_name,r.starts_at,r.ends_at,r.venue,r.status,
        r.submitted_at as created_at,r.reviewed_at,r.reviewed_by,nullif(r.rejection_reason,'') as rejection_reason,
        r.created_event_id as event_id,r.created_organization_id as organization_id,r.revision
        """;
    private final SqlStore db;
    public EventOpeningRepository(SqlStore db) {this.db=db;}
    public Map<String,Object> one(UUID id,boolean lock) {
        return db.one("select "+FIELDS+" from app.event_applications r where r.id=?"+(lock?" for update":""),id);
    }
    public Map<String,Object> list(UUID applicant,String status,int page,int size) {
        List<Object> args=new ArrayList<>();String where=" where 1=1";
        if(applicant!=null){where+=" and r.applicant_account_id=?";args.add(applicant);}
        if(status!=null){where+=" and r.status=?";args.add(status);}
        return db.page("select "+FIELDS+" from app.event_applications r"+where+" order by r.submitted_at desc,r.id desc",
            "select count(*) from app.event_applications r"+where,page,size,args.toArray());
    }
}
