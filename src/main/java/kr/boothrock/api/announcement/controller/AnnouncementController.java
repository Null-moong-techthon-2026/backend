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
@Profile("local & !deploy")
@RequestMapping("/api/events/{eventId}/announcements")
@Tag(name = "Announcements", description = "Event announcements and audience-aware participant reading")
public class AnnouncementController {
    private final AnnouncementService announcements;

    public AnnouncementController(AnnouncementService announcements) {
        this.announcements = announcements;
    }

    @GetMapping
    @Operation(summary = "List announcements for a participant or manager", description = "READ is the default. MANAGE requires manager access. Audience filters only narrow authorized results.")
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
    @Operation(summary = "Read an announcement visible to the current participant")
    public Map<String, Object> detail(@PathVariable UUID eventId, @PathVariable UUID announcementId,
                                      @AuthenticationPrincipal AccountPrincipal actor) {
        return announcements.detail(eventId, announcementId, actor.getAccountId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a draft or published announcement", description = "Managers only. The author and publication time are assigned by the server.")
    public Map<String, Object> create(@PathVariable UUID eventId,
            @AuthenticationPrincipal AccountPrincipal actor, @Valid @RequestBody CreateAnnouncementRequest request) {
        return announcements.create(eventId, actor.getAccountId(), request);
    }

    @PutMapping("/{announcementId}")
    @Operation(summary = "Replace or publish an announcement using its current revision", description = "Managers only. Updates preserve the first publication time.")
    public Map<String, Object> update(@PathVariable UUID eventId, @PathVariable UUID announcementId,
            @AuthenticationPrincipal AccountPrincipal actor, @Valid @RequestBody UpdateAnnouncementRequest request) {
        return announcements.update(eventId, announcementId, actor.getAccountId(), request);
    }

    @DeleteMapping("/{announcementId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete an announcement using its current revision", description = "Managers only. Deleted announcements cannot be restored.")
    public void delete(@PathVariable UUID eventId, @PathVariable UUID announcementId,
                       @AuthenticationPrincipal AccountPrincipal actor, @RequestParam long revision) {
        announcements.delete(eventId, announcementId, actor.getAccountId(), revision);
    }
}
