package kr.boothrock.api.event.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import io.swagger.v3.oas.annotations.media.Schema;
import kr.boothrock.api.auth.AccountPrincipal;
import kr.boothrock.api.event.service.EventOpeningService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("(local | staging) & !deploy")
@Tag(name="행사 개설", description="내 행사 개설 신청·상태 조회 및 플랫폼 관리자 신청 심사")
public class EventOpeningController {
    @Schema(name="EventOpeningCreate")
    public record Opening(@NotBlank @Size(max=200) String orgName, @Size(max=30) String orgType,
            @NotBlank @Size(max=100) String eventName, @NotNull Instant startsAt, @NotNull Instant endsAt,
            @NotBlank @Size(max=200) String venue) {}
    @Schema(name="EventOpeningDecision")
    public record OpeningDecision(@NotBlank String decision,@Size(max=2000) String rejectionReason,
            @NotNull @PositiveOrZero Long revision) {}
    private final EventOpeningService service;
    public EventOpeningController(EventOpeningService service) { this.service=service; }
    @PostMapping("/api/event-openings")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary="행사 개설 신청", description="PENDING 신청만 저장합니다. 조직과 행사는 플랫폼 ADMIN 승인 시 생성됩니다. 응답 id는 신청 ID입니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="201",content=@io.swagger.v3.oas.annotations.media.Content(schema=@Schema(implementation=kr.boothrock.api.event.dto.EventOpeningResponses.Created.class)))
    public Map<String,Object> open(@AuthenticationPrincipal AccountPrincipal actor, @Valid @RequestBody Opening request) {
        return service.open(actor.getAccountId(),request);
    }
    @GetMapping("/api/me/event-openings") @Operation(summary="내 행사 개설 신청과 상태")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="200",content=@io.swagger.v3.oas.annotations.media.Content(schema=@Schema(implementation=kr.boothrock.api.event.dto.EventOpeningResponses.MyPage.class)))
    public Map<String,Object> mine(@AuthenticationPrincipal AccountPrincipal actor,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return service.mine(actor.getAccountId(),page,size);
    }
    @GetMapping("/api/admin/event-openings") @Operation(summary="플랫폼 관리자 개설 신청 목록")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="200",content=@io.swagger.v3.oas.annotations.media.Content(schema=@Schema(implementation=kr.boothrock.api.event.dto.EventOpeningResponses.AdminPage.class)))
    public Map<String,Object> list(@AuthenticationPrincipal AccountPrincipal actor,@RequestParam(required=false) String status,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return service.list(actor.getAccountId(),status,page,size);
    }
    @GetMapping("/api/admin/event-openings/{id}") @Operation(summary="플랫폼 관리자 개설 신청 상세")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="200",content=@io.swagger.v3.oas.annotations.media.Content(schema=@Schema(implementation=kr.boothrock.api.event.dto.EventOpeningResponses.AdminApplication.class)))
    public Map<String,Object> detail(@PathVariable UUID id,@AuthenticationPrincipal AccountPrincipal actor) {return service.detail(actor.getAccountId(),id);}
    @PostMapping("/api/admin/event-openings/{id}/decision") @Operation(summary="행사 개설 승인·반려")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="200",content=@io.swagger.v3.oas.annotations.media.Content(schema=@Schema(implementation=kr.boothrock.api.event.dto.EventOpeningResponses.AdminApplication.class)))
    public Map<String,Object> decide(@PathVariable UUID id,@AuthenticationPrincipal AccountPrincipal actor,@Valid @RequestBody OpeningDecision r) {return service.decide(actor.getAccountId(),id,r);}
}
