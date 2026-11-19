package kr.boothrock.api.system;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "서버 상태", description = "서버 실행 상태 확인")
public class SystemStatusController {
    @GetMapping("/api/system/status")
    @Operation(summary = "서버 상태 조회", description = "서비스 식별 정보만 반환합니다. DB 연결 상태는 내부 상태 점검에서 별도로 확인합니다.")
    public SystemStatus status() {
        return new SystemStatus("boothrock-api", "foundation");
    }

    public record SystemStatus(String service, String stage) {
    }
}
