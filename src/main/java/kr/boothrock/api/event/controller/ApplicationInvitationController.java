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
import kr.boothrock.api.event.service.ApplicationInvitationService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("local & !deploy")
@RequestMapping("/api")
@Tag(name="부스 신청 초대")
public class ApplicationInvitationController {
    private final ApplicationInvitationService service;
    public ApplicationInvitationController(ApplicationInvitationService service) { this.service=service; }
    @GetMapping("/events/{eventId}/application-invitation") @Operation(summary="현재 초대 상태 조회",description="코드 원문은 발급 직후에만 제공됩니다.")
    public Map<String,Object> get(@PathVariable UUID eventId,@AuthenticationPrincipal AccountPrincipal actor) { return service.get(eventId,actor.getAccountId()); }
    @PostMapping("/events/{eventId}/application-invitation") @Operation(summary="초대 코드 발급·재발급",description="기존 코드는 즉시 폐기됩니다. 링크는 프론트 신청 화면에서 처리합니다.")
    @Parameter(name="X-CSRF-TOKEN",in=ParameterIn.HEADER,required=true)
    public ResponseEntity<?> create(@PathVariable UUID eventId,@AuthenticationPrincipal AccountPrincipal actor,@Valid @RequestBody EventRequests.Invitation request) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(service.create(eventId,actor.getAccountId(),request.expiresAt()));
    }
    @DeleteMapping("/events/{eventId}/application-invitation") @ResponseStatus(HttpStatus.NO_CONTENT) @Operation(summary="현재 초대 폐기")
    @Parameter(name="X-CSRF-TOKEN",in=ParameterIn.HEADER,required=true)
    public void revoke(@PathVariable UUID eventId,@AuthenticationPrincipal AccountPrincipal actor) { service.revoke(eventId,actor.getAccountId()); }
    @PostMapping("/application-invitations/resolve") @Operation(summary="초대 코드로 모집 정보 확인",description="유효한 코드로 신청 정보만 조회합니다. 신청 또는 승인 처리는 별도입니다.")
    @Parameter(name="X-CSRF-TOKEN",in=ParameterIn.HEADER,required=true)
    public ResponseEntity<?> resolve(@Valid @RequestBody EventRequests.Resolve request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.resolve(request.code()));
    }
}
