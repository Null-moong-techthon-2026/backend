package kr.boothrock.api.system;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SystemStatusController {
    @GetMapping("/api/system/status")
    public SystemStatus status() {
        return new SystemStatus("boothrock-api", "foundation");
    }

    public record SystemStatus(String service, String stage) {
    }
}
