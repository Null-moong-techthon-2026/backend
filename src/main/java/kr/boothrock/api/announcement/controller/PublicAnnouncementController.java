package kr.boothrock.api.announcement.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import java.util.UUID;
import kr.boothrock.api.announcement.service.AnnouncementService;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("(local | staging) & !deploy")
@RequestMapping("/api/public/events/{eventId}/announcements")
@Tag(name = "방문객 공지사항", description = "공개된 행사의 일반 방문객 대상 게시 공지")
public class PublicAnnouncementController {
    private final AnnouncementService announcements;

    public PublicAnnouncementController(AnnouncementService announcements) {
        this.announcements = announcements;
    }

    @GetMapping
    @Operation(summary = "방문객 공지 목록 조회", description = "공개된 행사에 게시된 PUBLIC 대상 공지만 조회합니다. 로그인한 관리자도 이 API에서는 같은 공개 범위만 볼 수 있습니다.")
    public Map<String, Object> list(@PathVariable UUID eventId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String audience) {
        return announcements.publicList(eventId, q, audience, page, size);
    }

    @GetMapping("/{announcementId}")
    @Operation(summary = "방문객 공지 상세 조회")
    public Map<String, Object> detail(@PathVariable UUID eventId, @PathVariable UUID announcementId) {
        return announcements.publicDetail(eventId, announcementId);
    }
}
