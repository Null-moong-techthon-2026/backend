package kr.boothrock.api.announcement.file;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import kr.boothrock.api.auth.AccountPrincipal;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@Profile("(local | staging) & !deploy")
@Tag(name="공지 첨부 파일")
public class AnnouncementFileController {
    private final AnnouncementFileService files;
    public AnnouncementFileController(AnnouncementFileService files){this.files=files;}
    @PostMapping(value="/api/events/{eventId}/announcement-attachments",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary="공지 첨부 업로드",description="행사 관리자만 PDF/PNG/JPEG 최대10MiB 업로드. 반환 attachmentId를 공지의 attachmentIds에 연결합니다. 최대5개.")
    @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public ResponseEntity<AnnouncementFileService.Attachment> upload(@PathVariable UUID eventId,@AuthenticationPrincipal AccountPrincipal actor,@RequestPart("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(files.upload(eventId,actor.getAccountId(),file));
    }
    @GetMapping("/api/public/events/{eventId}/announcement-attachments/{attachmentId}/content")
    @Operation(summary="공지 대상 권한을 검사한 첨부 열람",description="공개 행사 PUBLIC 게시 공지만 익명 열람 가능. 내부 공지는 로그인·대상 권한이 필요합니다. 미연결 파일은 행사 관리자만 조회합니다.")
    public ResponseEntity<byte[]> content(@PathVariable UUID eventId,@PathVariable UUID attachmentId,@AuthenticationPrincipal AccountPrincipal actor) {
        var f=files.content(eventId,attachmentId,actor==null?null:actor.getAccountId());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff")
            .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.inline().filename(f.fileName(),StandardCharsets.UTF_8).build().toString())
            .contentType(MediaType.parseMediaType(f.mimeType())).contentLength(f.bytes().length).body(f.bytes());
    }
}
