package kr.boothrock.api.booth.document;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import kr.boothrock.api.auth.AccountPrincipal;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@Profile("(local | staging) & !deploy")
@RequestMapping("/api/events/{eventId}/applications/{applicationId}/documents")
@Tag(name="부스 신청 서류")
public class ApplicationDocumentController {
    @io.swagger.v3.oas.annotations.media.Schema(name="ApplicationDocumentUpload")
    public record UploadResponse(UUID documentId,String fileName,UUID requirementId,java.time.Instant uploadedAt,
            String mimeType,long sizeBytes,long revision,String checkStatus) {}
    @io.swagger.v3.oas.annotations.media.Schema(name="ApplicationDocumentReadUrl")
    public record ReadUrlResponse(String readUrl,
            @io.swagger.v3.oas.annotations.media.Schema(nullable=true,description="현재 세션 인증 content URL을 사용하므로 null. 매 요청 권한을 검사합니다.") java.time.Instant expiresAt) {}
    private final ApplicationDocumentService service;
    public ApplicationDocumentController(ApplicationDocumentService service){this.service=service;}
    @PostMapping(consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary="내 부스 신청 서류 업로드·교체",description="PDF/PNG/JPEG 최대10MiB. PENDING 신청만 허용하며 교체 시 확인 상태를 SUBMITTED로 되돌립니다. revision은 해당 documents 항목의 값입니다.")
    @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public ResponseEntity<UploadResponse> upload(@PathVariable UUID eventId,@PathVariable UUID applicationId,
            @AuthenticationPrincipal AccountPrincipal actor,@RequestPart("file") MultipartFile file,
            @RequestParam UUID requirementId,@RequestParam(required=false) Long revision) {
        var result=service.upload(eventId,applicationId,actor.getAccountId(),requirementId,revision,file);
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(new UploadResponse(
            (UUID)result.get("documentId"),(String)result.get("fileName"),(UUID)result.get("requirementId"),
            (java.time.Instant)result.get("uploadedAt"),(String)result.get("mimeType"),((Number)result.get("sizeBytes")).longValue(),
            ((Number)result.get("revision")).longValue(),(String)result.get("checkStatus")));

    }
    @GetMapping("/{documentId}") @Operation(summary="신청자·행사 관리자 서류 읽기 주소")
    public ResponseEntity<ReadUrlResponse> url(@PathVariable UUID eventId,@PathVariable UUID applicationId,@PathVariable UUID documentId,
            @AuthenticationPrincipal AccountPrincipal actor) {
        var result=service.readUrl(eventId,applicationId,actor.getAccountId(),documentId);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new ReadUrlResponse((String)result.get("readUrl"),(java.time.Instant)result.get("expiresAt")));
    }
    @GetMapping("/{documentId}/content") @Operation(summary="권한을 검사한 실제 서류 조회")
    public ResponseEntity<byte[]> content(@PathVariable UUID eventId,@PathVariable UUID applicationId,@PathVariable UUID documentId,
            @AuthenticationPrincipal AccountPrincipal actor) {
        var file=service.content(eventId,applicationId,actor.getAccountId(),documentId);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff")
            .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.inline().filename(file.fileName(),StandardCharsets.UTF_8).build().toString())
            .contentType(MediaType.parseMediaType(file.mimeType())).contentLength(file.bytes().length).body(file.bytes());
    }
}
