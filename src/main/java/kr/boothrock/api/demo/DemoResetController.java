package kr.boothrock.api.demo;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import kr.boothrock.api.auth.AccountPrincipal;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.UUID;

@RestController
@Profile("(local | staging) & !deploy")
@Tag(name="시연 데이터 관리")
public class DemoResetController {
    public static final UUID EVENT_ID=UUID.fromString("d3e02026-1111-4000-8000-000000000200");
    @Schema(name="DemoResetRequest")
    public record Request(@NotNull UUID requestId,
            @NotNull @Schema(example="d3e02026-1111-4000-8000-000000000200") UUID eventId,
            @NotNull @Pattern(regexp="RESET_DEMO") String confirmation) {}
    @Schema(name="DemoResetResponse")
    public record Response(UUID requestId,UUID eventId,Instant restoredAt,boolean refreshRequired) {}
    private final DemoResetService service;
    public DemoResetController(DemoResetService service){this.service=service;}
    @PostMapping("/api/admin/demo/reset")
    @Operation(summary="시연 데이터를 저장된 기준 상태로 복원",description="ACTIVE 플랫폼 ADMIN 및 CSRF 필수. 서버 DEMO_RESET_ENABLED=true일 때만 실행. 지정된 11/11 시연 행사만 복원하며 계정·비밀번호·행사ID·기준 S3파일은 보존합니다. requestId는 클릭마다 UUID를 만들고 네트워크 재시도에는 같은 값을 재사용하세요. 같은 관리자·requestId의 완료 요청은 재실행하지 않습니다. 실행 중 409 DEMO_RESET_BUSY, 미준비 409 DEMO_NOT_READY, 보호 조건 위반 409 DEMO_RESET_UNSAFE. 성공 후 모든 시연 기기에서 재조회하세요.")
    public Response reset(@AuthenticationPrincipal AccountPrincipal actor,@Valid @RequestBody Request request){
        return service.reset(actor.getAccountId(),request);
    }
}
