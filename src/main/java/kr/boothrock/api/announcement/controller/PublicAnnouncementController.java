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
@Profile("local & !deploy")
@RequestMapping("/api/public/events/{eventId}/announcements")
@Tag(name = "Public announcements", description = "Published PUBLIC announcements of published events")
public class PublicAnnouncementController {
    private final AnnouncementService announcements;

    public PublicAnnouncementController(AnnouncementService announcements) {
        this.announcements = announcements;
    }

    @GetMapping
    @Operation(summary = "List published public announcements", description = "Only published events and PUBLIC-audience announcements are visible, including for signed-in managers.")
    public Map<String, Object> list(@PathVariable UUID eventId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String audience) {
        return announcements.publicList(eventId, q, audience, page, size);
    }

    @GetMapping("/{announcementId}")
    @Operation(summary = "Read a published public announcement")
    public Map<String, Object> detail(@PathVariable UUID eventId, @PathVariable UUID announcementId) {
        return announcements.publicDetail(eventId, announcementId);
    }
}
