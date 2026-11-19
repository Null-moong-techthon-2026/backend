package kr.boothrock.api.map.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import java.util.UUID;
import kr.boothrock.api.auth.AccountPrincipal;
import kr.boothrock.api.map.service.MediaService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@Profile("(local | staging) & !deploy")
@RequestMapping("/api")
@Tag(name = "이미지 파일")
public class MediaController {
    private final MediaService media;

    public MediaController(MediaService media) {
        this.media = media;
    }

    @PostMapping(value = "/events/{eventId}/media-assets", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "행사 이미지 업로드", description = "PNG·JPEG 이미지 파일을 업로드합니다. 최대 10 MiB·1,600만 화소까지 허용하며, 조회 권한을 확인하는 파일 주소를 반환합니다.")
    @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public ResponseEntity<Map<String, Object>> upload(@PathVariable UUID eventId,
            @AuthenticationPrincipal AccountPrincipal principal,
            @RequestPart("file") @Parameter(schema = @Schema(type = "string", format = "binary")) MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(media.upload(eventId, principal.getAccountId(), file));
    }

    @PostMapping(value = "/events/{eventId}/booths/{boothId}/media-assets", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "내 부스 사진 업로드", description = "관리자 또는 해당 부스 운영자가 업로드합니다. 반환 ID를 부스 사진 목록에 저장하면 공개 상태에 따라 노출됩니다.")
    @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public ResponseEntity<Map<String, Object>> uploadBooth(@PathVariable UUID eventId, @PathVariable UUID boothId,
            @AuthenticationPrincipal AccountPrincipal principal,
            @RequestPart("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(media.uploadBooth(eventId, boothId, principal.getAccountId(), file));
    }

    @GetMapping("/media-assets/{assetId}/content")
    @Operation(summary = "행사 관리자·공개 화면의 이미지 조회")
    public ResponseEntity<byte[]> content(@PathVariable UUID assetId,
            @AuthenticationPrincipal AccountPrincipal principal) {
        var content = media.content(assetId, principal == null ? null : principal.getAccountId());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(content.mimeType()))
                .contentLength(content.bytes().length).body(content.bytes());
    }
}
