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
@Profile("local & !deploy")
@RequestMapping("/api")
@Tag(name = "Media")
public class MediaController {
    private final MediaService media;

    public MediaController(MediaService media) {
        this.media = media;
    }

    @PostMapping(value = "/events/{eventId}/media-assets", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a PNG or JPEG for an event", description = "Maximum 10 MiB and 16 million pixels. Returns a permission-checked local content URL.")
    public ResponseEntity<Map<String, Object>> upload(@PathVariable UUID eventId,
            @AuthenticationPrincipal AccountPrincipal principal,
            @RequestPart("file") @Parameter(schema = @Schema(type = "string", format = "binary")) MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(media.upload(eventId, principal.getAccountId(), file));
    }

    @GetMapping("/media-assets/{assetId}/content")
    @Operation(summary = "Read an image as its event manager or through a current public reference")
    public ResponseEntity<byte[]> content(@PathVariable UUID assetId,
            @AuthenticationPrincipal AccountPrincipal principal) {
        var content = media.content(assetId, principal == null ? null : principal.getAccountId());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(content.mimeType()))
                .contentLength(content.bytes().length).body(content.bytes());
    }
}
