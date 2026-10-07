package kr.boothrock.api;

import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("integration")
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class FoundationIntegrationTest {
    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    MockMvc mvc;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    Flyway flyway;

    @Test
    void migrationUsesThePrivateSchemaAndCanBeRunAgain() {
        assertThat(jdbc.queryForObject(
                "select count(*) from app.flyway_schema_history where version = '1' and success",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select to_regclass('public.flyway_schema_history')", String.class))
                .isNull();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        assertThat(jdbc.queryForObject(
                "select count(*) from app.flyway_schema_history where version = '2' and success",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select count(*) from app.flyway_schema_history where version = '3' and success",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select to_regclass('app.accounts')", String.class))
                .isNotNull();
    }

    @Test
    void databaseHealthIsUpWithoutLeakingDetails() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
        mvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void signupAndExplicitResetWorkWithoutAutomaticDemoData() throws Exception {
        assertThat(jdbc.queryForObject("select count(*) from app.accounts", Integer.class)).isZero();
        mvc.perform(get("/api/dev/auth-data"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.database").value(postgres.getDatabaseName()))
                .andExpect(jsonPath("$.schema").value("app"))
                .andExpect(jsonPath("$.accounts").value(0));
        mvc.perform(post("/api/auth/sign-up").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                            {"loginId":"manual_reset_test","password":"DevOnly!2026",
                             "nickname":"Test organizer","phoneNumber":"01000000001",
                             "email":"manual@example.com","onboardingType":"ORGANIZER"}
                            """))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/dev/auth-data"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accounts").value(1))
                .andExpect(jsonPath("$.credentials").value(1))
                .andExpect(jsonPath("$.organizations").value(0))
                .andExpect(jsonPath("$.memberships").value(0));
        UUID accountId = jdbc.queryForObject(
                "select account_id from app.local_credentials where login_id = 'manual_reset_test'", UUID.class);
        UUID organizationId = UUID.randomUUID();
        jdbc.update("insert into app.organizations (id, name, created_by) values (?, ?, ?)",
                organizationId, "Reset fixture", accountId);
        jdbc.update("""
                insert into app.organization_memberships (organization_id, account_id, role)
                values (?, ?, 'OWNER')
                """, organizationId, accountId);
        mvc.perform(delete("/api/dev/auth-data").param("confirmation", "DELETE_LOCAL_DATA"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/dev/auth-data").with(csrf()).param("confirmation", "wrong"))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("select count(*) from app.accounts", Integer.class)).isEqualTo(1);
        Integer migrations = jdbc.queryForObject("select count(*) from app.flyway_schema_history", Integer.class);
        mvc.perform(delete("/api/dev/auth-data").with(csrf()).param("confirmation", "DELETE_LOCAL_DATA"))
                .andExpect(status().isNoContent());
        for (String table : new String[]{"accounts", "local_credentials", "organizations", "organization_memberships"}) {
            assertThat(jdbc.queryForObject("select count(*) from app." + table, Integer.class)).isZero();
        }
        assertThat(jdbc.queryForObject("select count(*) from app.flyway_schema_history", Integer.class))
                .isEqualTo(migrations);
        mvc.perform(get("/api/dev/auth-data"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accounts").value(0));
    }

    @Test
    void openApiDescribesLocalBusinessRoutes() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Boothrock API"))
                .andExpect(jsonPath("$.paths['/api/system/status'].get").exists())
                .andExpect(jsonPath("$.paths['/api/auth/login'].post").exists())
                .andExpect(jsonPath("$.paths['/api/auth/logout'].post").exists())
                .andExpect(jsonPath("$.paths['/api/auth/sign-up'].post").exists())
                .andExpect(jsonPath("$.paths['/api/auth/sign-up'].post.requestBody.content['application/json'].examples").isMap())
                .andExpect(jsonPath("$.paths['/api/auth/login'].post.requestBody.content['application/json'].examples").isMap())
                .andExpect(jsonPath("$.paths['/api/dev/auth-data'].delete").exists())
                .andExpect(jsonPath("$.paths['/api/dev/auth-data'].get").exists())
                .andExpect(jsonPath("$.paths['/api/me'].get").exists())
                .andExpect(jsonPath("$.paths['/api/events/{eventId}/dashboard'].get").exists())
                .andExpect(jsonPath("$.paths['/api/events/{eventId}/applications'].get").exists())
                .andExpect(jsonPath("$.paths['/api/events/{eventId}/applications/{applicationId}/decision'].post").exists())
                .andExpect(jsonPath("$.paths['/api/events/{eventId}/booths'].get").exists())
                .andExpect(jsonPath("$.paths['/api/events/{eventId}/operations'].get").exists())
                .andExpect(jsonPath("$.paths['/api/events/{eventId}/floor-plans'].post").exists())
                .andExpect(jsonPath("$.paths['/api/public/events/{eventId}/booths'].get").exists())
                .andExpect(jsonPath("$.paths['/api/public/events/{eventId}/map'].get").exists())
                .andExpect(jsonPath("$.paths['/api/events/{eventId}/applications'].post.parameters[?(@.name=='X-CSRF-TOKEN')].required").value(true))
                .andExpect(jsonPath("$.paths['/api/events/{eventId}/floor-plans'].post.parameters[?(@.name=='X-CSRF-TOKEN')].required").value(true))
                .andExpect(jsonPath("$.paths['/api/events/{eventId}/announcements'].post.parameters[?(@.name=='X-CSRF-TOKEN')].required").value(true));
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }
}
