package kr.boothrock.api.event.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.boothrock.api.auth.AccountPrincipal;
import kr.boothrock.api.event.dto.EventRequests;
import kr.boothrock.api.event.service.DashboardService;
import kr.boothrock.api.event.service.EventService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("local & !deploy")
@RequestMapping("/api")
@Tag(name="행사",description="행사 선택, 공통 네비게이션 및 행사 정보")
public class EventController {
    private final EventService events;
    private final DashboardService dashboard;
    public EventController(EventService events, DashboardService dashboard) {
        this.events=events;
        this.dashboard=dashboard;
    }
    @GetMapping("/me/organizations") @Operation(summary="내 조직 목록")
    public List<Map<String,Object>> organizations(@AuthenticationPrincipal AccountPrincipal actor) { return events.organizations(actor.getAccountId()); }
    @PostMapping("/organizations/{organizationId}/events") @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary="행사 생성",description="현재 조직 OWNER만 행사와 빈 모집 공고를 생성합니다.")
    @Parameter(name="X-CSRF-TOKEN",in=ParameterIn.HEADER,required=true)
    public Map<String,Object> create(@PathVariable UUID organizationId,@AuthenticationPrincipal AccountPrincipal actor,@Valid @RequestBody EventRequests.Create request) {
        return events.create(organizationId,actor.getAccountId(),request);
    }
    @GetMapping("/me/events") @Operation(summary="내 행사 목록")
    public Map<String,Object> mine(@AuthenticationPrincipal AccountPrincipal actor,
            @RequestParam(defaultValue="ORGANIZER") String participation,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return events.mine(actor.getAccountId(),participation,page,size);
    }
    @GetMapping("/events/{eventId}") @Operation(summary="행사 공통 정보",description="네비게이션과 대시보드에서 함께 사용합니다.")
    public Map<String,Object> event(@PathVariable UUID eventId,@AuthenticationPrincipal AccountPrincipal actor) { return events.get(eventId,actor.getAccountId()); }
    @GetMapping("/events/{eventId}/dashboard") @Operation(summary="대시보드 부스 요약과 최근 공지")
    public Map<String,Object> dashboard(@PathVariable UUID eventId,@AuthenticationPrincipal AccountPrincipal actor) {
        return dashboard.get(eventId,actor.getAccountId());
    }
    @PatchMapping("/events/{eventId}") @Operation(summary="행사 정보 수정",description="revision을 포함합니다. null 또는 생략한 필드는 유지합니다.")
    @Parameter(name="X-CSRF-TOKEN",in=ParameterIn.HEADER,required=true)
    public Map<String,Object> update(@PathVariable UUID eventId,@AuthenticationPrincipal AccountPrincipal actor,@Valid @RequestBody EventRequests.Update request) {
        return events.update(eventId,actor.getAccountId(),request);
    }
    @GetMapping("/public/events") @Operation(summary="공개 모집 행사 목록")
    public Map<String,Object> publicEvents(@RequestParam(required=false) String recruitmentStatus,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return events.publicEvents(recruitmentStatus,page,size);
    }
    @GetMapping("/public/events/{eventId}") @Operation(summary="방문객 행사 정보")
    public Map<String,Object> publicEvent(@PathVariable UUID eventId) { return events.publicEvent(eventId); }
}
