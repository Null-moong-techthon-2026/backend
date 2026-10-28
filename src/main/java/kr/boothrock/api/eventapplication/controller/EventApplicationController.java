package kr.boothrock.api.eventapplication.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import kr.boothrock.api.auth.AccountPrincipal;
import kr.boothrock.api.eventapplication.dto.EventApplicationRequests.Create;
import kr.boothrock.api.eventapplication.dto.EventApplicationRequests.Decision;
import kr.boothrock.api.eventapplication.service.EventApplicationService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "행사 개설 신청", description = "주최자가 신청하고 플랫폼 관리자가 승인·반려합니다. 승인하면 행사가 열립니다.")
public class EventApplicationController {
    private final EventApplicationService service;

    public EventApplicationController(EventApplicationService service) { this.service = service; }

    @PostMapping("/event-applications")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "행사 개설 신청", description = "검토 중인 신청이 이미 있으면 409 DUPLICATE_APPLICATION입니다.")
    public Map<String, Object> create(@AuthenticationPrincipal AccountPrincipal actor, @Valid @RequestBody Create request) {
        return service.submit(actor.getAccountId(), request);
    }

    @GetMapping("/me/event-applications")
    @Operation(summary = "내 행사 개설 신청 목록", description = "최신순. 반려 사유가 있으면 함께 내려갑니다.")
    public Map<String, Object> mine(@AuthenticationPrincipal AccountPrincipal actor,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.mine(actor.getAccountId(), page, size);
    }

    @GetMapping("/admin/event-applications")
    @Operation(summary = "[관리자] 신청 목록", description = "status(PENDING/APPROVED/REJECTED)로 거를 수 있고 건수 요약(summary)이 함께 옵니다.")
    public Map<String, Object> list(@AuthenticationPrincipal AccountPrincipal actor,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.list(actor.getAccountId(), status, page, size);
    }

    @GetMapping("/admin/event-applications/{id}")
    @Operation(summary = "[관리자] 신청 상세")
    public Map<String, Object> detail(@AuthenticationPrincipal AccountPrincipal actor, @PathVariable UUID id) {
        return service.detail(actor.getAccountId(), id);
    }

    @PostMapping("/admin/event-applications/{id}/decision")
    @Operation(summary = "[관리자] 승인 또는 반려",
            description = "승인하면 신청자를 OWNER로 하는 단체와 행사(초안)가 만들어집니다. 반려는 사유가 필요합니다. 결정은 되돌릴 수 없습니다.")
    public Map<String, Object> decide(@AuthenticationPrincipal AccountPrincipal actor, @PathVariable UUID id,
            @Valid @RequestBody Decision request) {
        return service.decide(actor.getAccountId(), id, request);
    }
}
