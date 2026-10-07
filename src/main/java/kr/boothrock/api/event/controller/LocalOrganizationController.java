package kr.boothrock.api.event.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import kr.boothrock.api.auth.AccountPrincipal;
import kr.boothrock.api.event.dto.EventRequests;
import kr.boothrock.api.event.service.EventService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("local & !deploy")
@Tag(name="로컬 테스트")
public class LocalOrganizationController {
    private final EventService events;
    public LocalOrganizationController(EventService events) { this.events=events; }
    @PostMapping("/api/dev/organizations") @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary="로컬 테스트 조직 만들기",description="로그인한 계정에 새 테스트 조직의 OWNER 소속을 부여합니다. 실제 기관 인증이나 기존 조직 가입 기능이 아닙니다. 배포 프로필에서는 사용할 수 없습니다.")
    @Parameter(name="X-CSRF-TOKEN",in=ParameterIn.HEADER,required=true)
    public Map<String,Object> create(@AuthenticationPrincipal AccountPrincipal actor,@Valid @RequestBody EventRequests.LocalOrganization request) {
        return events.createLocalOrganization(actor.getAccountId(),request);
    }
}
