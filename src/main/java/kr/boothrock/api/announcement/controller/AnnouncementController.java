package kr.boothrock.api.announcement.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import kr.boothrock.api.announcement.dto.CreateAnnouncementRequest;
import kr.boothrock.api.announcement.dto.UpdateAnnouncementRequest;
import kr.boothrock.api.announcement.service.AnnouncementService;
import kr.boothrock.api.auth.AccountPrincipal;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("(local | staging) & !deploy")
@RequestMapping("/api/events/{eventId}/announcements")
@Tag(name = "공지사항", description = "행사 공지 작성·관리와 대상별 공지 조회")
public class AnnouncementController {
    private final AnnouncementService announcements;

    public AnnouncementController(AnnouncementService announcements) {
        this.announcements = announcements;
    }

    @GetMapping
    @Operation(summary = "참여자·관리자 공지 목록 조회", description = "기본 조회 방식은 READ입니다. MANAGE는 행사 관리자만 사용할 수 있습니다. 대상 필터는 조회 권한이 있는 공지 안에서만 결과를 좁힙니다.")
    public Map<String, Object> list(@PathVariable UUID eventId,
            @AuthenticationPrincipal AccountPrincipal actor,
            @RequestParam(defaultValue = "READ") String view,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String audience) {
        return announcements.list(eventId, actor.getAccountId(), view, q, audience, page, size);
    }

    @GetMapping("/{announcementId}")
    @Operation(summary = "내가 볼 수 있는 공지 상세 조회")
    public Map<String, Object> detail(@PathVariable UUID eventId, @PathVariable UUID announcementId,
                                      @AuthenticationPrincipal AccountPrincipal actor) {
        return announcements.detail(eventId, announcementId, actor.getAccountId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "공지 임시 저장·게시", description = "행사 관리자만 사용할 수 있습니다. 작성자와 게시 시각은 서버가 지정합니다.")
    public Map<String, Object> create(@PathVariable UUID eventId,
            @AuthenticationPrincipal AccountPrincipal actor, @Valid @RequestBody CreateAnnouncementRequest request) {
        return announcements.create(eventId, actor.getAccountId(), request);
    }

    @PutMapping("/{announcementId}")
    @Operation(summary = "공지 수정·게시", description = "행사 관리자만 사용할 수 있습니다. 현재 revision을 보내야 하며 최초 게시 시각은 유지됩니다.")
    public Map<String, Object> update(@PathVariable UUID eventId, @PathVariable UUID announcementId,
            @AuthenticationPrincipal AccountPrincipal actor, @Valid @RequestBody UpdateAnnouncementRequest request) {
        return announcements.update(eventId, announcementId, actor.getAccountId(), request);
    }

    @DeleteMapping("/{announcementId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "공지 삭제", description = "행사 관리자만 사용할 수 있습니다. 현재 revision을 보내야 하며 삭제한 공지는 복구할 수 없습니다.")
    public void delete(@PathVariable UUID eventId, @PathVariable UUID announcementId,
                       @AuthenticationPrincipal AccountPrincipal actor, @RequestParam long revision) {
        announcements.delete(eventId, announcementId, actor.getAccountId(), revision);
    }
}
