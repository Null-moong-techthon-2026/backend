package kr.boothrock.api.booth.document;

import java.util.Map;
import java.util.UUID;
import kr.boothrock.api.booth.repository.ApplicationRepository;
import kr.boothrock.api.common.repository.SqlStore;
import kr.boothrock.api.common.service.*;
import kr.boothrock.api.map.storage.MediaStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional(readOnly=true)
public class ApplicationDocumentService {
    private static final Logger log=LoggerFactory.getLogger(ApplicationDocumentService.class);
    private final SqlStore db;
    private final ApplicationRepository applications;
    private final EventAccess access;
    private final MediaStorage storage;
    public ApplicationDocumentService(SqlStore db,ApplicationRepository applications,EventAccess access,MediaStorage storage) {
        this.db=db;this.applications=applications;this.access=access;this.storage=storage;
    }
    @Transactional
    public Map<String,Object> upload(UUID event,UUID application,UUID actor,UUID requirement,Long revision,MultipartFile file) {
        access.requireAccount(actor);access.lockEvent(event);applications.recruitment(event);
        var app=applications.application(event,application,true);
        if(!actor.equals(SqlStore.uuid(app,"applicantAccountId")))throw ApiException.notFound();
        Rules.require("PENDING".equals(app.get("reviewStatus")),"INVALID_STATE","Only pending applications accept files");
        var check=applications.document(event,application,requirement);
        if(revision!=null) {Rules.input(revision>=0,"revision must be nonnegative");Rules.revision(revision,check);}
        var validated=kr.boothrock.api.map.service.UploadedFileValidator.read(file);
        byte[] bytes=validated.bytes();String mime=validated.mimeType(),fileName=validated.fileName();
        UUID id=UUID.randomUUID();String key=id+(mime.equals("application/pdf")?".pdf":mime.equals("image/png")?".png":".jpg");
        String bucket=storage.writeBucket();
        var old=db.optional("select bucket,object_key from app.application_documents where application_id=? and requirement_id=?",application,requirement);
        storage.put(key,mime,bytes);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if(status==STATUS_ROLLED_BACK)remove(bucket,key);
                else if(status==STATUS_COMMITTED&&old!=null)remove((String)old.get("bucket"),(String)old.get("objectKey"));
            }
        });
        db.update("delete from app.application_documents where application_id=? and requirement_id=?",application,requirement);
        db.update("""
            insert into app.application_documents(id,event_id,application_id,requirement_id,uploaded_by,file_name,mime_type,size_bytes,bucket,object_key)
            values (?,?,?,?,?,?,?,?,?,?)
            """,id,event,application,requirement,actor,fileName,mime,bytes.length,bucket,key);
        db.update("""
            update app.application_document_checks set check_status='SUBMITTED',checked_by=null,checked_at=null,review_note=null,
                revision=revision+1,updated_at=now() where application_id=? and requirement_id=?
            """,application,requirement);
        db.update("""
            insert into app.audit_logs(id,organization_id,event_id,actor_account_id,actor_type,action,target_type,target_id)
            select ?,organization_id,id,?,'ACCOUNT','APPLICATION_DOCUMENT_UPLOADED','APPLICATION_DOCUMENT',? from app.events where id=?
            """,UUID.randomUUID(),actor,id,event);
        return db.one("""
            select d.id as document_id,d.file_name,d.requirement_id,d.uploaded_at,d.mime_type,d.size_bytes,c.revision,c.check_status
            from app.application_documents d join app.application_document_checks c on c.application_id=d.application_id and c.requirement_id=d.requirement_id
            where d.id=?
            """,id);
    }
    private void remove(String bucket,String key) {
        try {storage.delete(bucket,key);}catch(RuntimeException e){log.warn("Application document cleanup failed for object {}",key);}
    }
    private Map<String,Object> readable(UUID event,UUID application,UUID actor,UUID document) {
        access.requireAccount(actor);applications.recruitment(event);
        var app=applications.application(event,application,false);
        if(!actor.equals(SqlStore.uuid(app,"applicantAccountId"))&&!access.isManager(event,actor))throw ApiException.notFound();
        return db.one("select * from app.application_documents where id=? and event_id=? and application_id=?",document,event,application);
    }
    public Map<String,Object> readUrl(UUID event,UUID application,UUID actor,UUID document) {
        readable(event,application,actor,document);
        return Rules.result("readUrl","/api/events/"+event+"/applications/"+application+"/documents/"+document+"/content","expiresAt",null);
    }
    public Content content(UUID event,UUID application,UUID actor,UUID document) {
        var d=readable(event,application,actor,document);
        return new Content((String)d.get("fileName"),(String)d.get("mimeType"),storage.read((String)d.get("bucket"),(String)d.get("objectKey"),((Number)d.get("sizeBytes")).longValue()));
    }
    public record Content(String fileName,String mimeType,byte[] bytes) {}
}
