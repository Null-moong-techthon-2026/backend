package kr.boothrock.api.event.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import kr.boothrock.api.auth.AccountPrincipal;
import kr.boothrock.api.event.dto.EventRequests;
import kr.boothrock.api.event.service.RecruitmentService;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("local & !deploy")
@RequestMapping("/api")
@Tag(name="부스 모집")
public class RecruitmentController {
    private final RecruitmentService service;
    public RecruitmentController(RecruitmentService service) { this.service=service; }
    @GetMapping("/events/{eventId}/recruitment") @Operation(summary="모집 공고 편집 정보")
    public Map<String,Object> get(@PathVariable UUID eventId,@AuthenticationPrincipal AccountPrincipal actor) { return service.get(eventId,actor.getAccountId()); }
    @PutMapping("/events/{eventId}/recruitment") @Operation(summary="모집 공고 저장·게시",description="전체 폼과 revision을 보냅니다. 첫 신청 이후 카테고리·참가비·필요 서류는 고정됩니다.")
    @Parameter(name="X-CSRF-TOKEN",in=ParameterIn.HEADER,required=true)
    public Map<String,Object> save(@PathVariable UUID eventId,@AuthenticationPrincipal AccountPrincipal actor,@Valid @RequestBody EventRequests.Recruitment request) { return service.save(eventId,actor.getAccountId(),request); }
    @GetMapping("/public/events/{eventId}/recruitment") @Operation(summary="공개 모집 공고")
    public Map<String,Object> publicRecruitment(@PathVariable UUID eventId) { return service.publicRecruitment(eventId); }
}
