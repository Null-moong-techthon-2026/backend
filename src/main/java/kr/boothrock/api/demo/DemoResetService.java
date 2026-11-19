package kr.boothrock.api.demo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.UUID;
import kr.boothrock.api.common.service.ApiException;
import kr.boothrock.api.common.service.EventAccess;
import kr.boothrock.api.common.service.Rules;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static kr.boothrock.api.demo.DemoResetController.*;

@Service
public class DemoResetService {
    private final JdbcTemplate db;
    private final EventAccess access;
    private final boolean enabled;
    private final String restoreSql;
    public DemoResetService(JdbcTemplate db,EventAccess access,
            @Value("${boothrock.demo.reset-enabled:false}") boolean enabled)throws IOException{
        this.db=db;this.access=access;this.enabled=enabled;
        this.restoreSql=new ClassPathResource("demo/restore-20261111.sql").getContentAsString(StandardCharsets.UTF_8);
    }
    @Transactional(timeout=35)
    public Response reset(UUID actor,Request request){
        access.requirePlatformAdmin(actor);
        if(!enabled)throw new ApiException(403,"DEMO_RESET_DISABLED","Demo reset is disabled on this server.");
        Rules.input(EVENT_ID.equals(request.eventId()),"Only the designated demo event can be restored.");
        Rules.input("RESET_DEMO".equals(request.confirmation()),"Explicit demo reset confirmation is required.");
        if(!Boolean.TRUE.equals(db.queryForObject("select pg_try_advisory_xact_lock(1852401000,20261111)",Boolean.class)))
            throw new ApiException(409,"DEMO_RESET_BUSY","Another reset is running. Retry with the same requestId.");
        var previous=db.queryForList("select action,actor_account_id,target_id,occurred_at from app.audit_logs where id=?",request.requestId());
        if(!previous.isEmpty()){
            var row=previous.getFirst();
            Rules.require("DEMO_RESET".equals(row.get("action"))&&actor.equals(row.get("actor_account_id"))&&EVENT_ID.equals(row.get("target_id")),
                    "REQUEST_ID_CONFLICT","Use a new requestId for a different operation.");
            return new Response(request.requestId(),EVENT_ID,((Timestamp)row.get("occurred_at")).toInstant(),true);
        }
        if(db.queryForObject("select to_regclass('demoday_20261111.baseline')::text",String.class)==null)
            throw new ApiException(409,"DEMO_NOT_READY","The demo baseline has not been prepared.");
        db.execute((ConnectionCallback<Void>) connection->{
            try(var statement=connection.createStatement()){
                statement.setQueryTimeout(30);
                boolean result=statement.execute(restoreSql);
                while(result||statement.getUpdateCount()!=-1){
                    if(result)try(var rows=statement.getResultSet()){while(rows.next()){ /* drain */ }}
                    result=statement.getMoreResults();
                }
            }catch(SQLException error){
                String state=error.getSQLState();
                if("55P03".equals(state)||"57014".equals(state)||"40P01".equals(state))
                    throw new ApiException(409,"DEMO_RESET_BUSY","Data is being edited. Pause editing and retry with the same requestId.");
                if("P0001".equals(state)||(state!=null&&state.startsWith("23")))
                    throw new ApiException(409,"DEMO_RESET_UNSAFE","Demo data is linked to outside data or the baseline is inconsistent. Nothing was restored.");
                if("42P01".equals(state))throw new ApiException(409,"DEMO_NOT_READY","The demo baseline is incomplete.");
                throw new ApiException(500,"DEMO_RESET_FAILED","Reset failed and was rolled back.");
            }
            return null;
        });
        // Account table remains locked by the restore until commit; recheck a concurrent revocation.
        access.requirePlatformAdmin(actor);
        var at=db.queryForObject("""
                insert into app.audit_logs(id,organization_id,event_id,actor_account_id,actor_type,action,target_type,target_id)
                values (?,'d3e02026-1111-4000-8000-000000000100',?,?,'ACCOUNT','DEMO_RESET','EVENT',?) returning occurred_at
                """,Timestamp.class,request.requestId(),EVENT_ID,actor,EVENT_ID);
        return new Response(request.requestId(),EVENT_ID,at.toInstant(),true);
    }
}
