package kr.boothrock.api.booth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import kr.boothrock.api.auth.AccountPrincipal;
import kr.boothrock.api.booth.dto.BoothRequests.Apply;
import kr.boothrock.api.booth.dto.BoothRequests.Decision;
import kr.boothrock.api.booth.dto.BoothRequests.DocumentCheck;
import kr.boothrock.api.booth.dto.BoothRequests.ReviewNote;
import kr.boothrock.api.booth.service.ApplicationService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("local & !deploy")
@RequestMapping("/api")
@Tag(name = "Booth applications")
public class BoothApplicationController {
    private final ApplicationService applications;

    public BoothApplicationController(ApplicationService applications) {
        this.applications = applications;
    }

    @PostMapping("/events/{eventId}/applications")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Apply to an event with a booth profile")
    public Map<String, Object> apply(@PathVariable UUID eventId, @AuthenticationPrincipal AccountPrincipal actor,
            @Valid @RequestBody Apply request) {
        return applications.apply(eventId, actor.getAccountId(), request);
    }

    @GetMapping("/me/applications")
    @Operation(summary = "List my applications")
    public Map<String, Object> mine(@AuthenticationPrincipal AccountPrincipal actor,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return applications.mine(actor.getAccountId(), page, size);
    }

    @GetMapping("/me/applications/{applicationId}")
    @Operation(summary = "Read my application and document checks")
    public Map<String, Object> mineDetail(@AuthenticationPrincipal AccountPrincipal actor,
            @PathVariable UUID applicationId) {
        return applications.mine(actor.getAccountId(), applicationId);
    }

    @GetMapping("/events/{eventId}/applications")
    @Operation(summary = "List event applications for review")
    public Map<String, Object> list(@PathVariable UUID eventId, @AuthenticationPrincipal AccountPrincipal actor,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "7") int size,
            @RequestParam(required = false) String q, @RequestParam(required = false) String categoryCode,
            @RequestParam(required = false) String reviewStatus,
            @RequestParam(defaultValue = "submittedAt,desc") String sort) {
        return applications.list(eventId, actor.getAccountId(), page, size, q, categoryCode, reviewStatus, sort);
    }

    @GetMapping("/events/{eventId}/applications/summary")
    @Operation(summary = "Count applications by review status")
    public Map<String, Object> summary(@PathVariable UUID eventId, @AuthenticationPrincipal AccountPrincipal actor) {
        return applications.summary(eventId, actor.getAccountId());
    }

    @GetMapping("/events/{eventId}/applications/{applicationId}")
    @Operation(summary = "Read an application, contact details, and document checks")
    public Map<String, Object> detail(@PathVariable UUID eventId, @PathVariable UUID applicationId,
            @AuthenticationPrincipal AccountPrincipal actor) {
        return applications.detail(eventId, actor.getAccountId(), applicationId);
    }

    @PatchMapping("/events/{eventId}/applications/{applicationId}/review-note")
    @Operation(summary = "Update an internal review note")
    public Map<String, Object> reviewNote(@PathVariable UUID eventId, @PathVariable UUID applicationId,
            @AuthenticationPrincipal AccountPrincipal actor, @Valid @RequestBody ReviewNote request) {
        return applications.reviewNote(eventId, actor.getAccountId(), applicationId, request);
    }

    @PatchMapping("/events/{eventId}/applications/{applicationId}/document-checks/{requirementId}")
    @Operation(summary = "Confirm an externally submitted document")
    public Map<String, Object> documentCheck(@PathVariable UUID eventId, @PathVariable UUID applicationId,
            @PathVariable UUID requirementId, @AuthenticationPrincipal AccountPrincipal actor,
            @Valid @RequestBody DocumentCheck request) {
        return applications.documentCheck(eventId, actor.getAccountId(), applicationId, requirementId, request);
    }

    @PostMapping("/events/{eventId}/applications/{applicationId}/decision")
    @Operation(summary = "Approve or reject a pending application")
    public Map<String, Object> decide(@PathVariable UUID eventId, @PathVariable UUID applicationId,
            @AuthenticationPrincipal AccountPrincipal actor, @Valid @RequestBody Decision request) {
        return applications.decide(eventId, actor.getAccountId(), applicationId, request);
    }
}
