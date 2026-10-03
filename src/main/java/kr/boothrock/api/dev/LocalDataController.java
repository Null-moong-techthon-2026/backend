package kr.boothrock.api.dev;

import kr.boothrock.api.auth.repository.AccountRepository;
import kr.boothrock.api.auth.repository.LocalCredentialRepository;
import kr.boothrock.api.auth.repository.OrganizationMembershipRepository;
import kr.boothrock.api.auth.repository.OrganizationRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@Profile("local & !deploy")
@RequestMapping("/api/dev")
@Tag(name = "로컬 테스트")
public class LocalDataController {
    private final OrganizationMembershipRepository memberships;
    private final OrganizationRepository organizations;
    private final LocalCredentialRepository credentials;
    private final AccountRepository accounts;
    private final JdbcTemplate jdbc;

    public LocalDataController(OrganizationMembershipRepository memberships,
            OrganizationRepository organizations, LocalCredentialRepository credentials,
            AccountRepository accounts, JdbcTemplate jdbc) {
        this.memberships = memberships;
        this.organizations = organizations;
        this.credentials = credentials;
        this.accounts = accounts;
        this.jdbc = jdbc;
    }

    @GetMapping("/auth-data")
    @Transactional(readOnly = true)
    @Operation(summary = "현재 DB 데이터 개수 조회",
            description = "현재 앱이 연결한 DB에서 계정·조직 개수를 다시 조회합니다. DBeaver 결과와 비교할 수 있습니다.")
    public ResponseEntity<AuthDataSummary> summary() {
        AuthDataSummary summary = jdbc.queryForObject("""
                SELECT current_database() AS database_name,
                    (SELECT count(*) FROM app.accounts) AS accounts,
                    (SELECT count(*) FROM app.local_credentials) AS credentials,
                    (SELECT count(*) FROM app.organizations) AS organizations,
                    (SELECT count(*) FROM app.organization_memberships) AS memberships
                """, (row, number) -> new AuthDataSummary(row.getString("database_name"), "app",
                        row.getLong("accounts"), row.getLong("credentials"),
                        row.getLong("organizations"), row.getLong("memberships"), Instant.now()));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(summary);
    }

    public record AuthDataSummary(String database, String schema, long accounts, long credentials,
            long organizations, long memberships, Instant checkedAt) {}

    @DeleteMapping("/auth-data")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    @Parameter(name = "X-CSRF-TOKEN", in = ParameterIn.HEADER, required = true,
            description = "GET /api/auth/csrf 응답의 token")
    @Operation(summary = "로컬 인증 데이터 전체 삭제",
            description = "local 프로필에서만 사용합니다. 계정·로그인 정보·조직·조직 소속을 삭제하고 테이블 구조와 migration 기록은 유지합니다.")
    public void deleteAuthData(
            @Parameter(description = "반드시 DELETE_LOCAL_DATA 입력", example = "DELETE_LOCAL_DATA")
            @RequestParam String confirmation) {
        if (!"DELETE_LOCAL_DATA".equals(confirmation)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "confirmation must be DELETE_LOCAL_DATA");
        }
        memberships.deleteAllInBatch();
        organizations.deleteAllInBatch();
        credentials.deleteAllInBatch();
        accounts.deleteAllInBatch();
    }
}
