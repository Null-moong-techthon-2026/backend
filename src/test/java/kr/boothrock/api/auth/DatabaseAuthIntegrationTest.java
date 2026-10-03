package kr.boothrock.api.auth;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("integration")
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class DatabaseAuthIntegrationTest {
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

    @Test
    void operatorSignupIsStoredAndLoginUsesDatabaseSession() throws Exception {
        mvc.perform(get("/api/auth/login-id-availability").param("loginId", "operator_1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.available").value(true));
        MvcResult signUp = signup("operator_1", "OPERATOR", null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.organizationId").doesNotExist())
                .andReturn();
        String accountId = json(signUp, "$.accountId");

        mvc.perform(get("/api/auth/login-id-availability").param("loginId", "operator_1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.available").value(false));
        assertThat(jdbc.queryForObject(
                "select count(*) from app.organization_memberships where account_id = ?", Integer.class,
                UUID.fromString(accountId))).isZero();
        String passwordHash = jdbc.queryForObject(
                "select password_hash from app.local_credentials where account_id = ?", String.class,
                UUID.fromString(accountId));
        assertThat(passwordHash).startsWith("{bcrypt}").doesNotContain("TestPass!2026");

        MvcResult login = mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(loginBody("operator_1", "TestPass!2026")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(accountId))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andReturn();
        var session = (MockHttpSession) login.getRequest().getSession(false);
        assertThat(session).isNotNull();
        mvc.perform(get("/api/me").session(session).param("accountId", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(accountId))
                .andExpect(jsonPath("$.email").value("operator_1@example.com"))
                .andExpect(jsonPath("$.phoneNumber").value("01012345678"));

        MvcResult csrfResult = mvc.perform(get("/api/auth/csrf").session(session))
                .andExpect(status().isOk()).andReturn();
        mvc.perform(post("/api/auth/logout").session(session)
                        .header("X-CSRF-TOKEN", json(csrfResult, "$.token")))
                .andExpect(status().isNoContent());
        assertThat(session.isInvalid()).isTrue();
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void organizerSignupCreatesOnlyAPersonalAccount() throws Exception {
        MvcResult signUp = signup("organizer_1", "ORGANIZER", null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.organizationId").doesNotExist()).andReturn();
        UUID accountId = UUID.fromString(json(signUp, "$.accountId"));
        assertThat(jdbc.queryForObject(
                "select count(*) from app.organizations where created_by = ?", Integer.class, accountId))
                .isZero();
        assertThat(jdbc.queryForObject(
                "select count(*) from app.organization_memberships where account_id = ?", Integer.class,
                accountId)).isZero();
        assertThat(jdbc.queryForObject(
                "select email_verified_at from app.accounts where id = ?", Object.class, accountId))
                .isNull();
    }

    @Test
    void signupCannotClaimAnExistingOrganizationByNameOrRole() throws Exception {
        MvcResult existingOwner = signup("existing_owner", "OPERATOR", null)
                .andExpect(status().isCreated()).andReturn();
        UUID ownerId = UUID.fromString(json(existingOwner, "$.accountId"));
        UUID organizationId = UUID.randomUUID();
        jdbc.update("insert into app.organizations (id, name, created_by) values (?, ?, ?)",
                organizationId, "Student Council", ownerId);
        jdbc.update("""
                insert into app.organization_memberships
                    (organization_id, account_id, role) values (?, ?, 'OWNER')
                """, organizationId, ownerId);
        Integer before = jdbc.queryForObject("select count(*) from app.accounts", Integer.class);
        signup("forged_name", "ORGANIZER", "Student Council")
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/sign-up").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"onboardingType":"ORGANIZER","loginId":"forged_role",
                             "password":"TestPass!2026","nickname":"Test member",
                             "phoneNumber":"01012345678","email":"claim@example.com",
                             "organizationId":"%s","role":"OWNER"}
                            """.formatted(organizationId)))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("select count(*) from app.accounts", Integer.class)).isEqualTo(before);
        assertThat(jdbc.queryForObject("""
                select account_id from app.organization_memberships
                where organization_id = ? and role = 'OWNER'
                """, UUID.class, organizationId)).isEqualTo(ownerId);
        assertThat(jdbc.queryForObject("select count(*) from app.organizations where name = ?",
                Integer.class, "Student Council")).isEqualTo(1);
    }

    @Test
    void duplicateLoginIdIsRejectedWithoutOrphanedAccount() throws Exception {
        signup("duplicate_1", "OPERATOR", null).andExpect(status().isCreated());
        Integer before = jdbc.queryForObject("select count(*) from app.accounts", Integer.class);
        signup("duplicate_1", "ORGANIZER", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LOGIN_ID_TAKEN"));
        assertThat(jdbc.queryForObject("select count(*) from app.accounts", Integer.class))
                .isEqualTo(before);
        assertThat(jdbc.queryForObject(
                "select count(*) from app.organizations where name = 'Duplicate org'", Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject(
                "select count(*) from pg_constraint where conname = 'uq_local_credentials_login_id'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void invalidSignupDoesNotWriteAndInvalidCredentialsLookTheSame() throws Exception {
        Integer before = jdbc.queryForObject("select count(*) from app.accounts", Integer.class);
        signup("bad_input_1", "INVALID", null)
                .andExpect(status().isBadRequest());
        signup("bad_input_2", "OPERATOR", "Unexpected org")
                .andExpect(status().isBadRequest());
        signup("UPPER", "OPERATOR", null)
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("select count(*) from app.accounts", Integer.class))
                .isEqualTo(before);
        for (String loginId : new String[]{"operator_1", "missing_1"}) {
            mvc.perform(post("/api/auth/login").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginBody(loginId, "wrong-pass")))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        }
        mvc.perform(get("/api/auth/login-id-availability").param("loginId", "BAD"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void csrfIsRequiredAndPreviousVersionPathsStayClosed() throws Exception {
        mvc.perform(post("/api/auth/sign-up").contentType(MediaType.APPLICATION_JSON)
                        .content(signupBody("no_csrf", "OPERATOR", null)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("operator_1", "TestPass!2026")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/system/status")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
    }

    private org.springframework.test.web.servlet.ResultActions signup(
            String loginId, String onboardingType, String organizationName) throws Exception {
        return mvc.perform(post("/api/auth/sign-up").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(signupBody(loginId, onboardingType, organizationName)));
    }

    private String signupBody(String loginId, String onboardingType, String organizationName) {
        String organizationField = organizationName == null ? ""
                : ",\"organizationName\":\"" + organizationName + "\"";
        return """
                {"loginId":"%s","password":"TestPass!2026","nickname":"Test member",
                "phoneNumber":"01012345678","email":"%s@example.com",
                "onboardingType":"%s"%s}
                """.formatted(loginId, loginId, onboardingType, organizationField);
    }

    private String loginBody(String loginId, String password) {
        return "{\"loginId\":\"" + loginId + "\",\"password\":\"" + password + "\"}";
    }

    private String json(MvcResult result, String path) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), path);
    }
}
