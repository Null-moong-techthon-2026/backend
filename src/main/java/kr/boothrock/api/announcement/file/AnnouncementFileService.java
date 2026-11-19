package kr.boothrock.api.announcement.file;

import java.time.Instant;
import java.util.*;
import kr.boothrock.api.common.repository.SqlStore;
import kr.boothrock.api.common.service.*;
import kr.boothrock.api.map.service.UploadedFileValidator;
import kr.boothrock.api.map.storage.MediaStorage;
import kr.boothrock.api.announcement.repository.AnnouncementRepository;
import kr.boothrock.api.announcement.repository.AnnouncementRepository.Readers;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional(readOnly=true)
public class AnnouncementFileService {
    public record Attachment(UUID attachmentId,String fileName,String mimeType,long sizeBytes,Instant uploadedAt,String readUrl) {}
    public record Content(String fileName,String mimeType,byte[] bytes) {}
    private final SqlStore db;
    private final EventAccess access;
    private final MediaStorage storage;
    private final AnnouncementRepository announcements;
    public AnnouncementFileService(SqlStore db,EventAccess access,MediaStorage storage,AnnouncementRepository announcements) {
        this.db=db;this.access=access;this.storage=storage;this.announcements=announcements;
    }
    public static String readUrl(UUID event,UUID id) {return "/api/public/events/"+event+"/announcement-attachments/"+id+"/content";}
    @Transactional
    public Attachment upload(UUID event,UUID actor,MultipartFile file) {
        access.requireManager(event,actor);
        var valid=UploadedFileValidator.read(file);
        access.lockEvent(event);access.requireManager(event,actor);
        UUID id=UUID.randomUUID();String key=id+(valid.mimeType().equals("application/pdf")?".pdf":valid.mimeType().equals("image/png")?".png":".jpg");
        String bucket=storage.writeBucket();storage.put(key,valid.mimeType(),valid.bytes());
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if(status==STATUS_ROLLED_BACK)try {storage.delete(bucket,key);}catch(RuntimeException e){org.slf4j.LoggerFactory.getLogger(AnnouncementFileService.class).warn("Announcement file rollback cleanup failed for {}",id);}
            }
        });
        db.update("insert into app.announcement_attachments(id,event_id,uploaded_by,file_name,mime_type,size_bytes,bucket,object_key) values (?,?,?,?,?,?,?,?)",
            id,event,actor,valid.fileName(),valid.mimeType(),valid.bytes().length,bucket,key);
        var row=db.one("select uploaded_at from app.announcement_attachments where id=?",id);
        return new Attachment(id,valid.fileName(),valid.mimeType(),valid.bytes().length,(Instant)row.get("uploadedAt"),readUrl(event,id));
    }
    public void validate(UUID event,List<UUID> ids) {
        if(ids==null)return;
        Rules.input(ids.size()<=5&&new HashSet<>(ids).size()==ids.size(),"At most five distinct attachments per announcement");
        for(UUID id:ids)db.one("select id from app.announcement_attachments where id=? and event_id=?",id,event);
    }
    public void link(UUID event,UUID announcement,List<UUID> ids) {
        if(ids==null)return; // An old client omitting attachments must not erase them.
        validate(event,ids);
        db.update("delete from app.announcement_attachment_links where announcement_id=?",announcement);
        for(int i=0;i<ids.size();i++)db.update("insert into app.announcement_attachment_links(event_id,announcement_id,attachment_id,sort_order) values (?,?,?,?)",event,announcement,ids.get(i),i);
    }
    public Content content(UUID event,UUID id,UUID actor) {
        var file=db.one("select * from app.announcement_attachments where id=? and event_id=?",id,event);
        boolean manager=false,staff=false,operator=false;
        if(actor!=null) {
            access.requireAccount(actor);manager=access.isManager(event,actor);
            staff=access.isStaff(event,actor);operator=access.isOperator(event,actor);
        }
        if(!manager) {
            if(!staff&&!operator)access.requirePublic(event);
            boolean allowed=false;
            for(var link:db.list("select announcement_id from app.announcement_attachment_links where attachment_id=? and event_id=?",id,event)) {
                try {announcements.detail(event,(UUID)link.get("announcementId"),new Readers(false,staff,operator));allowed=true;break;}
                catch(ApiException e){if(e.status()!=404)throw e;}
            }
            if(!allowed)throw ApiException.notFound();
        }
        return new Content((String)file.get("fileName"),(String)file.get("mimeType"),storage.read((String)file.get("bucket"),(String)file.get("objectKey"),((Number)file.get("sizeBytes")).longValue()));
    }
}
