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
@Profile("(local | staging) & !deploy")
@RequestMapping("/api")
@Tag(name = "부스 신청·심사")
public class BoothApplicationController {
    private final ApplicationService applications;

    public BoothApplicationController(ApplicationService applications) {
        this.applications = applications;
    }

    @PostMapping("/events/{eventId}/applications")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "부스 프로필로 행사 참여 신청")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="201",content=@io.swagger.v3.oas.annotations.media.Content(schema=@io.swagger.v3.oas.annotations.media.Schema(implementation=kr.boothrock.api.booth.dto.ApplicationResponses.Detail.class)))
    public Map<String, Object> apply(@PathVariable UUID eventId, @AuthenticationPrincipal AccountPrincipal actor,
            @Valid @RequestBody Apply request) {
        return applications.apply(eventId, actor.getAccountId(), request);
    }

    @GetMapping("/me/applications")
    @Operation(summary = "내 참여 신청 목록 조회")
    public Map<String, Object> mine(@AuthenticationPrincipal AccountPrincipal actor,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return applications.mine(actor.getAccountId(), page, size);
    }

    @GetMapping("/me/applications/{applicationId}")
    @Operation(summary = "내 신청 상세·서류 확인 상태 조회")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="200",content=@io.swagger.v3.oas.annotations.media.Content(schema=@io.swagger.v3.oas.annotations.media.Schema(implementation=kr.boothrock.api.booth.dto.ApplicationResponses.Detail.class)))
    public Map<String, Object> mineDetail(@AuthenticationPrincipal AccountPrincipal actor,
            @PathVariable UUID applicationId) {
        return applications.mine(actor.getAccountId(), applicationId);
    }

    @GetMapping("/events/{eventId}/applications")
    @Operation(summary = "심사용 부스 신청 목록 조회")
    public Map<String, Object> list(@PathVariable UUID eventId, @AuthenticationPrincipal AccountPrincipal actor,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "7") int size,
            @RequestParam(required = false) String q, @RequestParam(required = false) String categoryCode,
            @RequestParam(required = false) String reviewStatus,
            @RequestParam(defaultValue = "submittedAt,desc") String sort) {
        return applications.list(eventId, actor.getAccountId(), page, size, q, categoryCode, reviewStatus, sort);
    }

    @GetMapping("/events/{eventId}/applications/summary")
    @Operation(summary = "심사 상태별 신청 수 조회")
    public Map<String, Object> summary(@PathVariable UUID eventId, @AuthenticationPrincipal AccountPrincipal actor) {
        return applications.summary(eventId, actor.getAccountId());
    }

    @GetMapping("/events/{eventId}/applications/{applicationId}")
    @Operation(summary = "신청 상세·연락처·서류 확인 상태 조회")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="200",content=@io.swagger.v3.oas.annotations.media.Content(schema=@io.swagger.v3.oas.annotations.media.Schema(implementation=kr.boothrock.api.booth.dto.ApplicationResponses.Detail.class)))
    public Map<String, Object> detail(@PathVariable UUID eventId, @PathVariable UUID applicationId,
            @AuthenticationPrincipal AccountPrincipal actor) {
        return applications.detail(eventId, actor.getAccountId(), applicationId);
    }

    @PatchMapping("/events/{eventId}/applications/{applicationId}/review-note")
    @Operation(summary = "관리자 검토 메모 수정")
    public Map<String, Object> reviewNote(@PathVariable UUID eventId, @PathVariable UUID applicationId,
            @AuthenticationPrincipal AccountPrincipal actor, @Valid @RequestBody ReviewNote request) {
        return applications.reviewNote(eventId, actor.getAccountId(), applicationId, request);
    }

    @PatchMapping("/events/{eventId}/applications/{applicationId}/document-checks/{requirementId}")
    @Operation(summary = "외부 제출 서류 확인 상태 변경")
    public Map<String, Object> documentCheck(@PathVariable UUID eventId, @PathVariable UUID applicationId,
            @PathVariable UUID requirementId, @AuthenticationPrincipal AccountPrincipal actor,
            @Valid @RequestBody DocumentCheck request) {
        return applications.documentCheck(eventId, actor.getAccountId(), applicationId, requirementId, request);
    }

    @PostMapping("/events/{eventId}/applications/{applicationId}/cancellation")
    @Operation(summary="승인된 부스 신청 취소",description="행사 관리자만 가능. 신청 기록을 보존하고 부스를 보관 처리하며 운영 권한을 회수합니다. 복구 API는 없습니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="200",content=@io.swagger.v3.oas.annotations.media.Content(schema=@io.swagger.v3.oas.annotations.media.Schema(implementation=kr.boothrock.api.booth.dto.ApplicationResponses.Detail.class)))
    public Map<String,Object> cancel(@PathVariable UUID eventId,@PathVariable UUID applicationId,
            @AuthenticationPrincipal AccountPrincipal actor,@Valid @RequestBody kr.boothrock.api.booth.dto.BoothRequests.Cancellation request) {
        return applications.cancel(eventId,actor.getAccountId(),applicationId,request);
    }

    @PostMapping("/events/{eventId}/applications/{applicationId}/decision")
    @Operation(summary = "검토 중인 신청 승인·반려")
    public Map<String, Object> decide(@PathVariable UUID eventId, @PathVariable UUID applicationId,
            @AuthenticationPrincipal AccountPrincipal actor, @Valid @RequestBody Decision request) {
        return applications.decide(eventId, actor.getAccountId(), applicationId, request);
    }
}
