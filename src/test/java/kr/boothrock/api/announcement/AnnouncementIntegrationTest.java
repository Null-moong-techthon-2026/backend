package kr.boothrock.api.announcement;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.boothrock.api.announcement.service.AnnouncementService;
import kr.boothrock.api.auth.AccountPrincipal;
import kr.boothrock.api.support.ApiIntegrationSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AnnouncementIntegrationTest extends ApiIntegrationSupport {
    @Autowired
    AnnouncementService announcements;

    private AccountPrincipal owner;
    private UUID eventId;

    @BeforeEach
    void setUp() throws Exception {
        owner = account("notice_owner");
        eventId = event(owner);
        jdbc.update("""
                UPDATE app.events SET publication_status = 'PUBLISHED', venue = 'Main field',
                    starts_at = now(), ends_at = now() + interval '1 day' WHERE id = ?
                """, eventId);
    }

    @Test
    void publicRoutesRequirePublishedEventAndPublishedPublicAudienceEvenForManagers() throws Exception {
        UUID publicId = notice("Public notice", "PUBLISHED", false, "PUBLIC");
        UUID mixedId = notice("Mixed notice", "PUBLISHED", false, "PUBLIC", "STAFF");
        UUID staffId = notice("Staff only", "PUBLISHED", false, "STAFF");
        UUID operatorId = notice("Operators only", "PUBLISHED", false, "OPERATORS");
        UUID draftId = notice("Draft", "DRAFT", true, "PUBLIC");

        mvc.perform(get(publicPath())).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[*].id", containsInAnyOrder(publicId.toString(), mixedId.toString())))
                .andExpect(jsonPath("$.content[0].body").doesNotExist())
                .andExpect(jsonPath("$.content[0].createdBy").doesNotExist());
        mvc.perform(get(publicPath() + "/" + publicId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.body").value("Announcement body"))
                .andExpect(jsonPath("$.createdBy").doesNotExist())
                .andExpect(jsonPath("$.actorAccountId").doesNotExist())
                .andExpect(jsonPath("$.bucket").doesNotExist())
                .andExpect(jsonPath("$.objectKey").doesNotExist());
        for (UUID hiddenId : List.of(staffId, operatorId, draftId)) {
            mvc.perform(get(publicPath() + "/" + hiddenId)).andExpect(status().isNotFound());
            mvc.perform(get(publicPath() + "/" + hiddenId).with(user(owner)))
                    .andExpect(status().isNotFound());
        }
        mvc.perform(get(publicPath()).with(user(owner)).param("view", "MANAGE").param("audience", "STAFF"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(mixedId.toString()));
        mvc.perform(get(publicPath()).param("audience", "OPERATORS"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty());

        for (String eventStatus : List.of("DRAFT", "ARCHIVED")) {
            jdbc.update("UPDATE app.events SET publication_status = ? WHERE id = ?", eventStatus, eventId);
            mvc.perform(get(publicPath())).andExpect(status().isNotFound());
            mvc.perform(get(publicPath() + "/" + publicId)).andExpect(status().isNotFound());
        }
    }

    @Test
    void participantAudiencesAreAUnionAndAudienceFiltersNeverGrantAccess() throws Exception {
        AccountPrincipal staff = staff("notice_staff");
        AccountPrincipal operator = operator("notice_operator");
        AccountPrincipal both = staff("notice_both");
        addBoothMembership(eventId, both);
        UUID otherEvent = event(owner);
        addStaffMembership(otherEvent, operator);
        addBoothMembership(otherEvent, staff);
        UUID staffId = notice("Staff", "PUBLISHED", false, "STAFF");
        UUID operatorId = notice("Operators", "PUBLISHED", false, "OPERATORS");
        UUID publicId = notice("Public", "PUBLISHED", false, "PUBLIC");
        UUID sharedId = notice("Shared", "PUBLISHED", false, "STAFF", "OPERATORS");
        UUID draftId = notice("Draft", "DRAFT", false, "PUBLIC", "STAFF", "OPERATORS");

        mvc.perform(get(path()).with(user(staff))).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", containsInAnyOrder(
                        staffId.toString(), publicId.toString(), sharedId.toString())));
        mvc.perform(get(path()).with(user(operator))).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", containsInAnyOrder(
                        operatorId.toString(), publicId.toString(), sharedId.toString())));
        mvc.perform(get(path()).with(user(both))).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4));
        mvc.perform(get(path()).with(user(operator)).param("audience", "STAFF"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(sharedId.toString()));
        mvc.perform(get(path()).with(user(staff)).param("audience", "OPERATORS"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(sharedId.toString()));
        mvc.perform(get(path() + "/" + staffId).with(user(operator)).param("audience", "STAFF"))
                .andExpect(status().isNotFound());
        mvc.perform(get(path() + "/" + operatorId).with(user(staff))).andExpect(status().isNotFound());
        mvc.perform(get(path() + "/" + sharedId).with(user(both))).andExpect(status().isOk());
        for (AccountPrincipal reader : List.of(staff, operator, both)) {
            mvc.perform(get(path() + "/" + draftId).with(user(reader))).andExpect(status().isNotFound());
            mvc.perform(get(path()).with(user(reader)).param("view", "MANAGE"))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(get(path()).with(user(owner)).param("view", "READ"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(4));
        mvc.perform(get(path()).with(user(owner)).param("view", "MANAGE"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(5));
        mvc.perform(get(path() + "/" + draftId).with(user(owner))).andExpect(status().isOk());
    }

    @Test
    void readRequiresParticipationAndWritesRequireManagerAndCsrf() throws Exception {
        AccountPrincipal outsider = account("notice_outsider");
        AccountPrincipal staff = staff("notice_staff");
        AccountPrincipal operator = operator("notice_operator");
        UUID id = notice("Public", "PUBLISHED", false, "PUBLIC");
        String payload = fields(0L, "Changed", "PUBLISHED", false, null, List.of("PUBLIC"));

        mvc.perform(get(path())).andExpect(status().isUnauthorized());
        mvc.perform(get(path()).with(user(outsider))).andExpect(status().isNotFound());
        mvc.perform(get(path() + "/" + id).with(user(outsider))).andExpect(status().isNotFound());
        for (AccountPrincipal reader : List.of(staff, operator)) {
            create(reader, body("publicationStatus", "DRAFT")).andExpect(status().isForbidden());
            mvc.perform(put(path() + "/" + id).with(user(reader)).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(payload))
                    .andExpect(status().isForbidden());
            mvc.perform(delete(path() + "/" + id).with(user(reader)).with(csrf()).param("revision", "0"))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(post(path()).with(user(owner)).contentType(MediaType.APPLICATION_JSON)
                        .content(body("publicationStatus", "DRAFT"))).andExpect(status().isForbidden());
        create(owner, body("publicationStatus", "DRAFT", "accountId", outsider.getAccountId()))
                .andExpect(status().isBadRequest());
        create(owner, body("publicationStatus", "DRAFT", "createdBy", outsider.getAccountId()))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT created_by FROM app.announcements WHERE id = ?", UUID.class, id))
                .isEqualTo(owner.getAccountId());
    }

    @Test
    void emptyDraftCanBePublishedAndFirstPublicationTimeSurvivesEditsAndRepublishing() throws Exception {
        MvcResult draft = create(owner, body("publicationStatus", "DRAFT"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.title").value(""))
                .andExpect(jsonPath("$.body").value(""))
                .andExpect(jsonPath("$.audiences").isEmpty())
                .andExpect(jsonPath("$.revision").value(0))
                .andExpect(jsonPath("$.publishedAt").doesNotExist()).andReturn();
        UUID id = UUID.fromString(json(draft, "$.id"));
        for (String invalid : List.of(
                body("title", " ", "body", "Body", "audiences", List.of("PUBLIC"), "publicationStatus", "PUBLISHED"),
                body("title", "Title", "body", "\n\t", "audiences", List.of("PUBLIC"), "publicationStatus", "PUBLISHED"),
                body("title", "Title", "body", "Body", "audiences", List.of(), "publicationStatus", "PUBLISHED"))) {
            create(owner, invalid).andExpect(status().isBadRequest());
        }
        mvc.perform(put(path() + "/" + id).with(user(owner)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("revision", 0, "publicationStatus", "PUBLISHED")))
                .andExpect(status().isBadRequest());
        MvcResult published = update(id, fields(0L, "Published", "PUBLISHED", false, null, List.of("PUBLIC")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.revision").value(1))
                .andExpect(jsonPath("$.publishedAt").isNotEmpty()).andReturn();
        String firstPublishedAt = json(published, "$.publishedAt");
        update(id, fields(1L, "Edited", "PUBLISHED", true, null, List.of("STAFF")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.revision").value(2))
                .andExpect(jsonPath("$.publishedAt").value(firstPublishedAt));
        mvc.perform(get(publicPath() + "/" + id)).andExpect(status().isNotFound());
        update(id, body("revision", 2, "publicationStatus", "DRAFT"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.audiences").isEmpty())
                .andExpect(jsonPath("$.publishedAt").value(firstPublishedAt));
        update(id, fields(3L, "Published again", "PUBLISHED", false, null, List.of("PUBLIC")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.revision").value(4))
                .andExpect(jsonPath("$.publishedAt").value(firstPublishedAt));
        mvc.perform(get(publicPath() + "/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Published again"));
    }

    @Test
    void crossEventImagesAndAnnouncementIdsAreRejectedWithoutChangingTheAnnouncement() throws Exception {
        UUID otherEvent = event(owner);
        UUID foreignImage = image(otherEvent);
        UUID ownImage = image(eventId);
        UUID id = notice("Original", "PUBLISHED", false, "PUBLIC");
        for (UUID invalidImage : List.of(foreignImage, UUID.randomUUID())) {
            create(owner, body("imageAssetId", invalidImage, "publicationStatus", "DRAFT"))
                    .andExpect(status().isNotFound());
            update(id, fields(0L, "Changed", "PUBLISHED", true, invalidImage, List.of("STAFF")))
                    .andExpect(status().isNotFound());
        }
        mvc.perform(get(path() + "/" + id).with(user(owner))).andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Original"))
                .andExpect(jsonPath("$.revision").value(0))
                .andExpect(jsonPath("$.audiences[0]").value("PUBLIC"));
        update(id, fields(0L, "With image", "PUBLISHED", false, ownImage, List.of("PUBLIC")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.imageAssetId").value(ownImage.toString()))
                .andExpect(jsonPath("$.imageUrl").value("/api/media-assets/" + ownImage + "/content"))
                .andExpect(jsonPath("$.bucket").doesNotExist()).andExpect(jsonPath("$.objectKey").doesNotExist());
        String foreignPath = "/api/events/" + otherEvent + "/announcements/" + id;
        mvc.perform(get(foreignPath).with(user(owner))).andExpect(status().isNotFound());
        mvc.perform(put(foreignPath).with(user(owner)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(fields(1L, "Wrong event", "DRAFT", false, null, List.of())))
                .andExpect(status().isNotFound());
        mvc.perform(delete(foreignPath).with(user(owner)).with(csrf()).param("revision", "1"))
                .andExpect(status().isNotFound());
        update(id, fields(1L, "Image removed", "PUBLISHED", false, null, List.of("PUBLIC")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.imageAssetId").doesNotExist())
                .andExpect(jsonPath("$.imageUrl").doesNotExist());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM app.announcements WHERE event_id = ?", Long.class, eventId))
                .isEqualTo(1L);
    }

    @Test
    void staleWritesConflictAndDeletionIsPermanentWithMinimalAuditData() throws Exception {
        UUID id = notice("Original", "PUBLISHED", false, "PUBLIC");
        update(id, fields(0L, "Urgent", "PUBLISHED", true, null, List.of("PUBLIC")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.revision").value(1))
                .andExpect(jsonPath("$.isUrgent").value(true));
        update(id, fields(0L, "Stale", "PUBLISHED", false, null, List.of("STAFF")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("REVISION_CONFLICT"));
        mvc.perform(delete(path() + "/" + id).with(user(owner)).with(csrf()).param("revision", "0"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("REVISION_CONFLICT"));
        mvc.perform(get(publicPath() + "/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Urgent"));
        mvc.perform(delete(path() + "/" + id).with(user(owner)).with(csrf()).param("revision", "1"))
                .andExpect(status().isNoContent());
        mvc.perform(get(path() + "/" + id).with(user(owner))).andExpect(status().isNotFound());
        mvc.perform(get(publicPath() + "/" + id)).andExpect(status().isNotFound());
        mvc.perform(get(path()).with(user(owner)).param("view", "MANAGE"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get(publicPath())).andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty());
        update(id, fields(2L, "Restore", "PUBLISHED", false, null, List.of("PUBLIC")))
                .andExpect(status().isNotFound());
        mvc.perform(delete(path() + "/" + id).with(user(owner)).with(csrf()).param("revision", "2"))
                .andExpect(status().isNotFound());
        assertThat(announcements.recent(eventId, owner.getAccountId())).isEmpty();
        assertThat(jdbc.queryForObject("SELECT revision FROM app.announcements WHERE id = ?", Long.class, id))
                .isEqualTo(2L);
        assertThat(jdbc.queryForList("""
                SELECT action FROM app.audit_logs WHERE target_id = ? ORDER BY occurred_at, id
                """, String.class, id)).containsExactly("ANNOUNCEMENT_CREATED", "ANNOUNCEMENT_UPDATED", "ANNOUNCEMENT_DELETED");
        assertThat(jdbc.queryForList("""
                SELECT allowed_changes::text FROM app.audit_logs WHERE target_id = ?
                """, String.class, id)).containsOnly("{}");
    }

    @Test
    void urgentOrderingUsesPublicationTimeThenIdAndRecentReturnsOnlyThreeVisiblePublishedNotices() throws Exception {
        AccountPrincipal staff = staff("notice_staff");
        AccountPrincipal operator = operator("notice_operator");
        UUID urgent = notice("Old urgent", "PUBLISHED", true, "PUBLIC");
        UUID newest = notice("Newest public", "PUBLISHED", false, "PUBLIC");
        UUID staffOnly = notice("Staff", "PUBLISHED", false, "STAFF");
        UUID operatorOnly = notice("Operators", "PUBLISHED", false, "OPERATORS");
        UUID tied = notice("Tied public", "PUBLISHED", false, "PUBLIC");
        UUID draft = notice("Urgent draft", "DRAFT", true, "PUBLIC");
        time(urgent, "2026-10-01T01:00:00Z");
        time(newest, "2026-10-01T05:00:00Z");
        time(tied, "2026-10-01T05:00:00Z");
        time(staffOnly, "2026-10-01T06:00:00Z");
        time(operatorOnly, "2026-10-01T07:00:00Z");
        jdbc.update("UPDATE app.announcements SET created_at = ? WHERE id = ?",
                Timestamp.from(Instant.parse("2026-10-02T00:00:00Z")), draft);
        UUID largerId = newest.toString().compareTo(tied.toString()) > 0 ? newest : tied;
        UUID smallerId = largerId.equals(newest) ? tied : newest;

        mvc.perform(get(publicPath())).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(urgent.toString()))
                .andExpect(jsonPath("$.content[1].id").value(largerId.toString()))
                .andExpect(jsonPath("$.content[2].id").value(smallerId.toString()));
        mvc.perform(get(path()).with(user(owner)).param("view", "MANAGE"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(draft.toString()));
        assertThat(ids(announcements.recent(eventId, owner.getAccountId())))
                .containsExactly(urgent, operatorOnly, staffOnly);
        assertThat(ids(announcements.recent(eventId, staff.getAccountId())))
                .containsExactly(urgent, staffOnly, largerId);
        assertThat(ids(announcements.recent(eventId, operator.getAccountId())))
                .containsExactly(urgent, operatorOnly, largerId);
        mvc.perform(get(path()).with(user(staff)).param("size", "3")).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(urgent.toString()))
                .andExpect(jsonPath("$.content[1].id").value(staffOnly.toString()))
                .andExpect(jsonPath("$.content[2].id").value(largerId.toString()));
    }

    @Test
    void literalSearchPaginationAndInvalidRequestFieldsFollowTheContract() throws Exception {
        UUID literal = notice("100%_ready\\now", "PUBLISHED", false, "PUBLIC");
        notice("100 percent ready now", "PUBLISHED", false, "PUBLIC");
        for (String query : List.of("%", "_", "\\", "100%_ready\\now")) {
            mvc.perform(get(path()).with(user(owner)).param("q", query))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].id").value(literal.toString()));
        }
        mvc.perform(get(publicPath()).param("page", "20").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.totalPages").value(2));
        for (String[] invalid : List.of(new String[]{"page", "-1"}, new String[]{"size", "0"},
                new String[]{"size", "101"}, new String[]{"q", "x".repeat(101)},
                new String[]{"audience", "MANAGERS"}, new String[]{"view", "ADMIN"})) {
            mvc.perform(get(path()).with(user(owner)).param(invalid[0], invalid[1]))
                    .andExpect(status().isBadRequest());
        }
        for (String invalid : List.of(
                body("publicationStatus", "DELETED"),
                body("publicationStatus", "DRAFT", "title", "x".repeat(201)),
                body("publicationStatus", "DRAFT", "body", "x".repeat(10001)),
                body("publicationStatus", "DRAFT", "audiences", List.of("MANAGERS")),
                body("publicationStatus", "DRAFT", "audiences", List.of("PUBLIC", "PUBLIC")),
                body("publicationStatus", "DRAFT", "revision", 0),
                body("publicationStatus", "DRAFT", "publishedAt", "2026-01-01T00:00:00Z"))) {
            create(owner, invalid).andExpect(status().isBadRequest());
        }
        update(literal, body("publicationStatus", "DRAFT")).andExpect(status().isBadRequest());
        update(literal, body("revision", -1, "publicationStatus", "DRAFT")).andExpect(status().isBadRequest());
        mvc.perform(delete(path() + "/" + literal).with(user(owner)).with(csrf()))
                .andExpect(status().isBadRequest());
        mvc.perform(delete(path() + "/" + literal).with(user(owner)).with(csrf()).param("revision", "-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void losingStaffMembershipDoesNotRetainStaffAudienceThroughOperatorAccess() throws Exception {
        AccountPrincipal both = staff("notice_both");
        addBoothMembership(eventId, both);
        UUID staffId = notice("Staff only", "PUBLISHED", false, "STAFF");
        UUID operatorId = notice("Operator only", "PUBLISHED", false, "OPERATORS");
        jdbc.update("""
                UPDATE app.organization_memberships SET membership_status = 'REVOKED', revoked_at = now()
                WHERE organization_id = (SELECT organization_id FROM app.events WHERE id = ?) AND account_id = ?
                """, eventId, both.getAccountId());
        mvc.perform(get(path()).with(user(both))).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(operatorId.toString()));
        mvc.perform(get(path() + "/" + staffId).with(user(both))).andExpect(status().isNotFound());
        assertThat(ids(announcements.recent(eventId, both.getAccountId()))).containsExactly(operatorId);
        jdbc.update("""
                UPDATE app.booth_memberships SET membership_status = 'REVOKED', revoked_at = now() WHERE account_id = ?
                """, both.getAccountId());
        mvc.perform(get(path()).with(user(both))).andExpect(status().isNotFound());
    }

    private AccountPrincipal staff(String prefix) throws Exception {
        AccountPrincipal principal = account(prefix);
        addStaffMembership(eventId, principal);
        return principal;
    }

    private void addStaffMembership(UUID staffEventId, AccountPrincipal principal) {
        UUID organizationId = jdbc.queryForObject("SELECT organization_id FROM app.events WHERE id = ?", UUID.class, staffEventId);
        jdbc.update("INSERT INTO app.organization_memberships (organization_id, account_id, role) VALUES (?, ?, 'MEMBER')",
                organizationId, principal.getAccountId());
        jdbc.update("""
                INSERT INTO app.event_memberships (event_id, organization_id, account_id, role) VALUES (?, ?, ?, 'STAFF')
                """, staffEventId, organizationId, principal.getAccountId());
    }

    private AccountPrincipal operator(String prefix) throws Exception {
        AccountPrincipal principal = account(prefix);
        addBoothMembership(eventId, principal);
        return principal;
    }

    private void addBoothMembership(UUID boothEventId, AccountPrincipal principal) {
        UUID booth = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO app.event_booths (id, event_id, source, booth_code, name, category_code)
                VALUES (?, ?, 'MANUAL', ?, 'Test booth', 'FOOD')
                """, booth, boothEventId, booth.toString().substring(0, 8).toUpperCase());
        jdbc.update("INSERT INTO app.booth_memberships (event_booth_id, account_id) VALUES (?, ?)",
                booth, principal.getAccountId());
    }

    private UUID image(UUID imageEventId) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO app.media_assets
                    (id, event_id, uploaded_by, bucket, object_key, mime_type, size_bytes, width_px, height_px)
                VALUES (?, ?, ?, 'test-private', ?, 'image/png', 10, 1, 1)
                """, id, imageEventId, owner.getAccountId(), id.toString());
        return id;
    }

    private UUID notice(String title, String publicationStatus, boolean urgent, String... audiences) throws Exception {
        MvcResult result = create(owner, body("title", title, "body", "Announcement body", "audiences", List.of(audiences),
                "isUrgent", urgent, "publicationStatus", publicationStatus)).andExpect(status().isCreated()).andReturn();
        return UUID.fromString(json(result, "$.id"));
    }

    private String fields(long revision, String title, String publicationStatus, boolean urgent, UUID image, List<String> audiences) {
        return body("revision", revision, "title", title, "body", "Announcement body", "audiences", audiences,
                "isUrgent", urgent, "imageAssetId", image, "publicationStatus", publicationStatus);
    }

    private ResultActions create(AccountPrincipal principal, String payload) throws Exception {
        return mvc.perform(post(path()).with(user(principal)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(payload));
    }

    private ResultActions update(UUID id, String payload) throws Exception {
        return mvc.perform(put(path() + "/" + id).with(user(owner)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(payload));
    }

    private void time(UUID id, String instant) {
        jdbc.update("UPDATE app.announcements SET published_at = ? WHERE id = ?", Timestamp.from(Instant.parse(instant)), id);
    }

    private List<UUID> ids(List<Map<String, Object>> rows) {
        return rows.stream().map(row -> UUID.fromString(row.get("id").toString())).toList();
    }

    private String path() {
        return "/api/events/" + eventId + "/announcements";
    }

    private String publicPath() {
        return "/api/public/events/" + eventId + "/announcements";
    }
}
