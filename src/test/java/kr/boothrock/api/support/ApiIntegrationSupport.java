package kr.boothrock.api.support;

import com.jayway.jsonpath.JsonPath;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import java.nio.file.Path;
import kr.boothrock.api.auth.AccountPrincipal;
import kr.boothrock.api.common.service.Rules;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
public abstract class ApiIntegrationSupport {
    protected static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");
    private static final Path mediaDirectory = Path.of(System.getProperty("java.io.tmpdir"),
            "boothrock-test-media-" + UUID.randomUUID());
    static { postgres.start(); }
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("boothrock.media.directory", mediaDirectory::toString);
    }
    @Autowired protected MockMvc mvc;
    @Autowired protected JdbcTemplate jdbc;

    protected AccountPrincipal account(String prefix) {
        UUID id = UUID.randomUUID();
        String login = "test_" + id.toString().replace("-", "").substring(0, 20);
        jdbc.update("INSERT INTO app.accounts(id,nickname,phone_number,email) VALUES (?,?,?,?)",
                id, prefix, "01000000000", login + "@example.com");
        jdbc.update("INSERT INTO app.local_credentials(account_id,login_id,password_hash) VALUES (?,?,?)", id, login, "{noop}unused");
        return new AccountPrincipal(id, login, "unused", prefix);
    }
    protected UUID event(AccountPrincipal owner) {
        UUID organization = UUID.randomUUID();
        UUID event = UUID.randomUUID();
        jdbc.update("INSERT INTO app.organizations(id,name,created_by) VALUES (?,?,?)", organization, "Test organizer", owner.getAccountId());
        jdbc.update("INSERT INTO app.organization_memberships(organization_id,account_id,role) VALUES (?,?,'OWNER')", organization, owner.getAccountId());
        jdbc.update("""
                INSERT INTO app.events(id,organization_id,created_by,name,venue,starts_at,ends_at)
                VALUES (?,?,?,'Test festival','University square',?,?)
                """, event, organization, owner.getAccountId(), Timestamp.from(Instant.now().plusSeconds(86400)),
                Timestamp.from(Instant.now().plusSeconds(172800)));
        jdbc.update("INSERT INTO app.event_recruitments(event_id) VALUES (?)", event);
        return event;
    }
    protected String json(MvcResult result, String path) throws Exception {
        Object value = JsonPath.read(result.getResponse().getContentAsString(), path);
        return value == null ? null : value.toString();
    }
    protected static String body(Object... pairs) { return JsonMapper.builder().build().writeValueAsString(Rules.result(pairs)); }
}
