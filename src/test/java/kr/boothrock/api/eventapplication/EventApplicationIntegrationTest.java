package kr.boothrock.api.eventapplication;

import java.util.Map;
import java.util.UUID;
import kr.boothrock.api.auth.AccountPrincipal;
import kr.boothrock.api.common.service.Rules;
import kr.boothrock.api.support.ApiIntegrationSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EventApplicationIntegrationTest extends ApiIntegrationSupport {
    private AccountPrincipal applicant;
    private AccountPrincipal admin;

    @BeforeEach
    void setUp() {
        applicant = account("applicant");
        admin = account("admin");
        jdbc.update("UPDATE app.accounts SET platform_role = 'ADMIN' WHERE id = ?", admin.getAccountId());
    }

    private static Map<String, Object> form() {
        return new java.util.LinkedHashMap<>(Rules.result(
                "orgName", "Inha Student Council", "orgType", "STUDENT", "orgEmail", "council@example.com",
                "businessNumber", "", "affiliation", "Inha University", "representative", "", "website", "",
                "contactName", "Kim", "contactEmail", "kim@example.com", "contactPhone", "01012345678",
                "contactRole", "Planner", "eventName", "Spring Festival", "eventKind", "UNIVERSITY",
                "startDate", "2026-12-01", "endDate", "2026-12-03", "venue", "Main field", "boothCount", 30,
                "handover", true, "paymentPlan", "PREPAID", "paymentMethod", "CARD", "receiptType", "NONE",
                "agreements", java.util.List.of("terms", "privacy", "outsourcing", "billing", "refund")));
    }

    private ResultActions submit(AccountPrincipal who, Map<String, Object> form) throws Exception {
        return mvc.perform(post("/api/event-applications").with(user(who)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(JsonMapper.builder().build().writeValueAsString(form)));
    }

    private ResultActions decide(AccountPrincipal who, String id, Object... pairs) throws Exception {
        return mvc.perform(post("/api/admin/event-applications/" + id + "/decision").with(user(who)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body(pairs)));
    }

    @Test
    void anApplicantSubmitsAndSeesItPendingWhileOthersAndVisitorsCannot() throws Exception {
        mvc.perform(post("/api/event-applications").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        String id = json(submit(applicant, form()).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.startDate").value("2026-12-01"))
                .andExpect(jsonPath("$.boothCount").value(30))
                .andExpect(jsonPath("$.agreements.length()").value(5))
                .andExpect(jsonPath("$.createdEventId").doesNotExist())
                .andReturn(), "$.id");
        mvc.perform(get("/api/me/event-applications").with(user(applicant))).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(id))
                .andExpect(jsonPath("$.content[0].eventName").value("Spring Festival"));
        mvc.perform(get("/api/me/event-applications").with(user(admin))).andExpect(jsonPath("$.totalElements").value(0));
        // one waiting application at a time
        submit(applicant, form()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_APPLICATION"));
    }

    @Test
    void theFormRulesAreCheckedOnTheServer() throws Exception {
        var company = form();
        company.put("orgType", "COMPANY");
        submit(applicant, company).andExpect(status().isBadRequest()); // business number missing
        company.put("businessNumber", "1234567890");
        submit(account("company"), company).andExpect(status().isCreated());

        var noAffiliation = form();
        noAffiliation.put("affiliation", " ");
        submit(applicant, noAffiliation).andExpect(status().isBadRequest());

        var backwards = form();
        backwards.put("endDate", "2026-11-30");
        submit(applicant, backwards).andExpect(status().isBadRequest());

        var missingAgreement = form();
        missingAgreement.put("agreements", java.util.List.of("terms", "privacy"));
        submit(applicant, missingAgreement).andExpect(status().isBadRequest());

        var badKind = form();
        badKind.put("eventKind", "CONCERT");
        submit(applicant, badKind).andExpect(status().isBadRequest());

        var unknownField = form();
        unknownField.put("status", "APPROVED");
        submit(applicant, unknownField).andExpect(status().isBadRequest()); // the applicant cannot choose the status
    }

    @Test
    void onlyAdministratorsListAndDecide() throws Exception {
        String id = json(submit(applicant, form()).andReturn(), "$.id");
        for (AccountPrincipal other : new AccountPrincipal[] {applicant, account("someone")}) {
            mvc.perform(get("/api/admin/event-applications").with(user(other))).andExpect(status().isForbidden());
            mvc.perform(get("/api/admin/event-applications/" + id).with(user(other))).andExpect(status().isForbidden());
            decide(other, id, "revision", 0, "decision", "APPROVED").andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/admin/event-applications")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/event-applications").with(user(admin)).param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.pending").isNumber())
                .andExpect(jsonPath("$.content[?(@.id=='" + id + "')].applicantNickname").value("applicant"));
        mvc.perform(get("/api/admin/event-applications").with(user(admin)).param("status", "NOPE")).andExpect(status().isBadRequest());
        // /api/me tells the front end who is an administrator
        mvc.perform(get("/api/me").with(user(admin))).andExpect(jsonPath("$.platformRole").value("ADMIN"));
        mvc.perform(get("/api/me").with(user(applicant))).andExpect(jsonPath("$.platformRole").value("USER"));
    }

    @Test
    void approvingOpensTheEventForTheApplicantInOneStep() throws Exception {
        String id = json(submit(applicant, form()).andReturn(), "$.id");
        // a stale revision is refused and nothing is created
        decide(admin, id, "revision", 5, "decision", "APPROVED").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REVISION_CONFLICT"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM app.events WHERE created_by = ?", Long.class, applicant.getAccountId())).isZero();

        decide(admin, id, "revision", 0, "decision", "APPROVED").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.createdEventId").isNotEmpty());
        UUID eventId = jdbc.queryForObject("SELECT created_event_id FROM app.event_applications WHERE id = ?::uuid", UUID.class, id);

        Map<String, Object> event = jdbc.queryForMap("""
                SELECT e.name, e.venue, e.publication_status, to_char(e.starts_at AT TIME ZONE 'Asia/Seoul', 'YYYY-MM-DD HH24:MI:SS') AS starts,
                       to_char(e.ends_at AT TIME ZONE 'Asia/Seoul', 'YYYY-MM-DD HH24:MI:SS') AS ends, o.name AS org, m.role
                FROM app.events e JOIN app.organizations o ON o.id = e.organization_id
                JOIN app.organization_memberships m ON m.organization_id = o.id AND m.account_id = ?
                WHERE e.id = ?
                """, applicant.getAccountId(), eventId);
        assertThat(event).containsEntry("name", "Spring Festival").containsEntry("venue", "Main field")
                .containsEntry("publication_status", "DRAFT").containsEntry("org", "Inha Student Council")
                .containsEntry("role", "OWNER").containsEntry("starts", "2026-12-01 00:00:00").containsEntry("ends", "2026-12-03 23:59:59");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM app.event_recruitments WHERE event_id = ?", Long.class, eventId)).isEqualTo(1);

        // the applicant now manages the event through the normal API
        mvc.perform(get("/api/me/events").with(user(applicant))).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(eventId.toString()))
                .andExpect(jsonPath("$.content[0].name").value("Spring Festival"));
        mvc.perform(get("/api/events/" + eventId).with(user(applicant))).andExpect(status().isOk())
                .andExpect(jsonPath("$.capabilities[0]").value("MANAGE_EVENT"));
        mvc.perform(get("/api/me/event-applications").with(user(applicant)))
                .andExpect(jsonPath("$.content[0].status").value("APPROVED"))
                .andExpect(jsonPath("$.content[0].createdEventId").value(eventId.toString()));

        // deciding again: the same decision returns the result without opening a second event, another one is refused
        decide(admin, id, "revision", 0, "decision", "APPROVED").andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM app.events WHERE created_by = ?", Long.class, applicant.getAccountId())).isEqualTo(1);
        decide(admin, id, "revision", 1, "decision", "REJECTED", "rejectionReason", "no").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));

        // an approved applicant may apply again later (a second event)
        submit(applicant, form()).andExpect(status().isCreated());
    }

    @Test
    void rejectingNeedsAReasonAndLetsTheApplicantApplyAgain() throws Exception {
        String id = json(submit(applicant, form()).andReturn(), "$.id");
        decide(admin, id, "revision", 0, "decision", "REJECTED").andExpect(status().isBadRequest());
        decide(admin, id, "revision", 0, "decision", "APPROVED", "rejectionReason", "x").andExpect(status().isBadRequest());
        decide(admin, id, "revision", 0, "decision", "MAYBE").andExpect(status().isBadRequest());
        decide(admin, id, "revision", 0, "decision", "REJECTED", "rejectionReason", "Send proof of the organization.")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM app.events WHERE created_by = ?", Long.class, applicant.getAccountId())).isZero();
        mvc.perform(get("/api/me/event-applications").with(user(applicant)))
                .andExpect(jsonPath("$.content[0].rejectionReason").value("Send proof of the organization."));
        submit(applicant, form()).andExpect(status().isCreated());
        mvc.perform(get("/api/me/event-applications").with(user(applicant))).andExpect(jsonPath("$.totalElements").value(2));
    }
}
