package kr.boothrock.api.booth;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import kr.boothrock.api.auth.AccountPrincipal;
import kr.boothrock.api.support.ApiIntegrationSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BusinessWorkflowIntegrationTest extends ApiIntegrationSupport {

    @Test
    void organizerOperatorAndVisitorCanCompleteTheSixScreenFlow() throws Exception {
        AccountPrincipal owner = account("workflow_owner");
        AccountPrincipal operator = account("workflow_operator");
        UUID organizationId = id(write(post("/api/dev/organizations"), owner,
                body("name", "Festival committee", "contactEmail", "owner@example.com"))
                .andExpect(status().isCreated()).andReturn(), "$.id");
        UUID eventId = id(write(post("/api/organizations/" + organizationId + "/events"), owner,
                body("name", "Campus festival", "venue", "Main square",
                        "startsAt", Instant.now().plusSeconds(86400 * 7),
                        "endsAt", Instant.now().plusSeconds(86400 * 8), "description", "Festival introduction"))
                .andExpect(status().isCreated()).andReturn(), "$.id");
        String eventPath = "/api/events/" + eventId;
        mvc.perform(get(eventPath).with(user(owner))).andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Campus festival"))
                .andExpect(jsonPath("$.organization.name").value("Festival committee"));
        write(patch(eventPath), owner, body("revision", 0, "publicationStatus", "PUBLISHED"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.revision").value(1));

        MvcResult recruitment = write(put(eventPath + "/recruitment"), owner,
                body("revision", 0, "introduction", "Apply for a booth", "publicationStatus", "PUBLISHED",
                        "closesAt", Instant.now().plusSeconds(86400 * 3), "targetBoothCount", 20,
                        "allowedCategoryCodes", List.of("FOOD"), "participationFeeKrw", 50000,
                        "feeNote", "Pay after approval",
                        "documentRequirements", List.of(Map.of("name", "Operating plan", "isRequired", true,
                                "applicableCategoryCodes", List.of(), "sortOrder", 0))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.recruitmentStatus").value("OPEN"))
                .andReturn();
        UUID requirementId = id(recruitment, "$.documentRequirements[0].id");
        mvc.perform(get("/api/public/events/" + eventId + "/recruitment"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.participationFeeKrw").value(50000));

        MvcResult profile = write(post("/api/me/booth-profiles"), operator,
                body("name", "Moonlight Snacks", "categoryCode", "FOOD", "introduction", "Fresh snacks"))
                .andExpect(status().isCreated()).andReturn();
        UUID profileId = id(profile, "$.id");
        mvc.perform(get("/api/me/booth-profiles").with(user(operator)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        MvcResult submitted = write(post(eventPath + "/applications"), operator,
                body("boothProfileId", profileId, "applicantName", "Operator Kim",
                        "applicantPhone", "01012345678", "applicantEmail", "operator@example.com"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.reviewStatus").value("PENDING"))
                .andReturn();
        UUID applicationId = id(submitted, "$.applicationId");
        String applicationPath = eventPath + "/applications/" + applicationId;
        mvc.perform(get(eventPath + "/applications").with(user(owner)).param("size", "7"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].applicantName").value("Operator Kim"));
        mvc.perform(get(applicationPath).with(user(owner))).andExpect(status().isOk())
                .andExpect(jsonPath("$.documents[0].checkStatus").value("NOT_SUBMITTED"));
        mvc.perform(get("/api/me/applications/" + applicationId).with(user(operator)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.reviewNote").doesNotExist());
        write(post(applicationPath + "/decision"), owner,
                body("revision", 0, "decision", "APPROVED", "reviewNote", "Ready"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DOCUMENTS_NOT_VERIFIED"));
        write(patch(applicationPath + "/document-checks/" + requirementId), owner,
                body("revision", 0, "checkStatus", "VERIFIED", "reviewNote", "Checked offline"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.revision").value(1));
        String decision = body("revision", 0, "decision", "APPROVED", "reviewNote", "Ready");
        MvcResult approval = write(post(applicationPath + "/decision"), owner, decision)
                .andExpect(status().isOk()).andExpect(jsonPath("$.boothCode").value("B001"))
                .andReturn();
        UUID boothId = id(approval, "$.eventBoothId");
        write(post(applicationPath + "/decision"), owner, decision).andExpect(status().isOk())
                .andExpect(jsonPath("$.eventBoothId").value(boothId.toString()));
        mvc.perform(get("/api/me/booths").with(user(operator)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get(eventPath + "/applications/summary").with(user(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.approved").value(1));

        String boothPath = eventPath + "/booths/" + boothId;
        write(patch(boothPath), owner, body("revision", 0, "visibilityStatus", "PUBLIC"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.revision").value(1));
        write(patch(boothPath + "/operation-status"), operator, body("revision", 1, "operationStatus", "OPEN"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.operationStatus").value("OPEN"));
        MvcResult item = write(post(boothPath + "/items"), operator,
                body("name", "Snack cup", "description", "One serving", "priceKrw", 4000,
                        "stockStatus", "LOW", "sortOrder", 0))
                .andExpect(status().isCreated()).andReturn();
        UUID itemId = id(item, "$.id");
        mvc.perform(get(eventPath + "/operations").with(user(owner)).param("operationStatus", "OPEN"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.summary.open").value(1))
                .andExpect(jsonPath("$.booths.size").value(8))
                .andExpect(jsonPath("$.booths.totalElements").value(1))
                .andExpect(jsonPath("$.booths.content[0].stockSummary").value("LOW"));
        mvc.perform(get(boothPath).with(user(operator))).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(itemId.toString()));

        UUID imageId = upload(eventId, owner);
        MvcResult draft = write(post(eventPath + "/floor-plans"), owner, body("mediaAssetId", imageId))
                .andExpect(status().isCreated()).andReturn();
        UUID floorPlanId = id(draft, "$.floorPlanId");
        UUID boothPinId = UUID.randomUUID();
        UUID facilityPinId = UUID.randomUUID();
        write(put(eventPath + "/floor-plans/" + floorPlanId), owner,
                body("revision", 0, "pins", List.of(
                        Map.of("id", boothPinId, "pinType", "BOOTH", "label", "B001", "xRatio", 0.25,
                                "yRatio", 0.4, "eventBoothId", boothId),
                        Map.of("id", facilityPinId, "pinType", "TOILET", "label", "Restroom", "xRatio", 0.8,
                                "yRatio", 0.2))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.revision").value(1));
        mvc.perform(get("/api/public/events/" + eventId + "/map"))
                .andExpect(status().isNotFound());
        write(post(eventPath + "/floor-plans/" + floorPlanId + "/publication"), owner, body("revision", 1))
                .andExpect(status().isOk());
        mvc.perform(get("/api/public/events/" + eventId + "/map"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.pins.length()").value(2));
        mvc.perform(get("/api/public/events/" + eventId + "/booths/" + boothId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.mapPlacement.xRatio").value(0.25))
                .andExpect(jsonPath("$.items[0].stockStatus").value("LOW"))
                .andExpect(jsonPath("$.source").doesNotExist());

        write(post(eventPath + "/announcements"), owner,
                body("title", "Festival opens", "body", "Welcome", "audiences", List.of("PUBLIC"),
                        "isUrgent", true, "publicationStatus", "PUBLISHED"))
                .andExpect(status().isCreated());
        mvc.perform(get(eventPath + "/dashboard").with(user(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.boothSummary.open").value(1))
                .andExpect(jsonPath("$.recentAnnouncements[0].title").value("Festival opens"));
        mvc.perform(get("/api/public/events/" + eventId + "/announcements"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void ownershipAndDraftPlacementRemainPrivate() throws Exception {
        AccountPrincipal owner = account("scope_owner");
        AccountPrincipal outsider = account("scope_outsider");
        UUID eventId = event(owner);
        UUID otherEvent = event(outsider);
        jdbc.update("UPDATE app.events SET publication_status='PUBLISHED' WHERE id=?", eventId);
        String eventPath = "/api/events/" + eventId;
        MvcResult booth = write(post(eventPath + "/booths"), owner,
                body("name", "Test food", "categoryCode", "FOOD", "boothCode", "A1"))
                .andExpect(status().isCreated()).andReturn();
        UUID boothId = id(booth, "$.eventBoothId");
        write(patch(eventPath + "/booths/" + boothId), owner,
                body("revision", 0, "visibilityStatus", "PUBLIC")).andExpect(status().isOk());
        mvc.perform(get(eventPath + "/applications").with(user(outsider))).andExpect(status().isNotFound());
        mvc.perform(get(eventPath + "/booths").with(user(outsider))).andExpect(status().isNotFound());
        mvc.perform(get(eventPath + "/dashboard").with(user(outsider))).andExpect(status().isNotFound());
        mvc.perform(get("/api/public/events/" + otherEvent + "/booths/" + boothId))
                .andExpect(status().isNotFound());

        UUID imageId = upload(eventId, owner);
        UUID draftId = id(write(post(eventPath + "/floor-plans"), owner, body("mediaAssetId", imageId))
                .andExpect(status().isCreated()).andReturn(), "$.floorPlanId");
        write(put(eventPath + "/floor-plans/" + draftId), owner,
                body("revision", 0, "pins", List.of(Map.of("id", UUID.randomUUID(), "pinType", "BOOTH",
                        "label", "A1", "xRatio", 0.3, "yRatio", 0.4, "eventBoothId", boothId))))
                .andExpect(status().isOk());
        mvc.perform(get(eventPath + "/booths").with(user(owner)).param("floorPlanId", draftId.toString())
                        .param("placementStatus", "ASSIGNED"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/public/events/" + eventId + "/booths"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].placementStatus").value("UNASSIGNED"));
        mvc.perform(get("/api/public/events/" + eventId + "/booths")
                        .param("floorPlanId", draftId.toString()))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/public/events/" + eventId + "/booths/" + boothId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.mapPlacement").doesNotExist());
    }

    @Test
    void localAuthResetRefusesToBreakBusinessForeignKeys() throws Exception {
        AccountPrincipal owner = account("reset_owner");
        UUID eventId = event(owner);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/dev/auth-data").with(csrf()).param("confirmation", "DELETE_LOCAL_DATA"))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM app.events WHERE id=?", Long.class, eventId))
                .isEqualTo(1);
    }

    @Test
    void menuUpdatesAndReviewPermissionsAreCheckedAtTheHttpBoundary() throws Exception {
        AccountPrincipal owner = account("menu_owner");
        AccountPrincipal outsider = account("menu_outsider");
        UUID eventId = event(owner);
        String eventPath = "/api/events/" + eventId;
        UUID boothId = id(write(post(eventPath + "/booths"), owner,
                body("name", "Coffee", "categoryCode", "BEVERAGE"))
                .andExpect(status().isCreated()).andReturn(), "$.eventBoothId");
        String boothPath = eventPath + "/booths/" + boothId;
        mvc.perform(get(eventPath + "/booths").with(user(owner)).param("q", "Coffee")
                        .param("categoryCode", "BEVERAGE").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get(eventPath + "/booths").with(user(owner)).param("size", "0"))
                .andExpect(status().isBadRequest());
        write(post(eventPath + "/booths"), owner,
                body("name", "Duplicate code", "categoryCode", "BEVERAGE", "boothCode", "B001"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("BOOTH_CODE_TAKEN"));
        write(patch(boothPath + "/operation-status"), outsider,
                body("revision", 0, "operationStatus", "OPEN"))
                .andExpect(status().isNotFound());
        write(patch(boothPath + "/operation-status"), owner,
                body("revision", 3, "operationStatus", "OPEN"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("REVISION_CONFLICT"));
        MvcResult item = write(post(boothPath + "/items"), owner,
                body("name", "Iced coffee", "priceKrw", 3500, "stockStatus", "LOW", "sortOrder", 1))
                .andExpect(status().isCreated()).andReturn();
        UUID itemId = id(item, "$.id");
        write(patch(boothPath + "/items/" + itemId), owner,
                body("revision", 0, "priceKrw", null, "stockStatus", "SOLD_OUT"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.priceKrw").doesNotExist())
                .andExpect(jsonPath("$.stockStatus").value("SOLD_OUT"));
        mvc.perform(get(boothPath).with(user(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.stockSummary").value("SOLD_OUT"));
        mvc.perform(delete(boothPath + "/items/" + itemId).with(user(owner)).with(csrf())
                        .param("revision", "0"))
                .andExpect(status().isConflict());
        mvc.perform(delete(boothPath + "/items/" + itemId).with(user(owner)).with(csrf())
                        .param("revision", "1"))
                .andExpect(status().isNoContent());
        mvc.perform(get(boothPath).with(user(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.stockSummary").value("NOT_TRACKED"))
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void invitationCanOpenADraftEventWithoutMakingItsRecruitmentPublic() throws Exception {
        AccountPrincipal owner = account("invite_owner");
        AccountPrincipal operator = account("invite_operator");
        UUID eventId = event(owner);
        String eventPath = "/api/events/" + eventId;
        Instant close = Instant.now().plusSeconds(86400 * 3);
        write(put(eventPath + "/recruitment"), owner,
                body("revision", 0, "introduction", "Invite only", "publicationStatus", "PUBLISHED",
                        "closesAt", close, "targetBoothCount", 4, "allowedCategoryCodes", List.of("FOOD"),
                        "participationFeeKrw", 0, "feeNote", "", "documentRequirements", List.of()))
                .andExpect(status().isOk());
        mvc.perform(get("/api/public/events/" + eventId + "/recruitment"))
                .andExpect(status().isNotFound());
        MvcResult invite = write(post(eventPath + "/application-invitation"), owner,
                body("expiresAt", close.minusSeconds(60)))
                .andExpect(status().isCreated()).andReturn();
        String code = json(invite, "$.code");
        assertThat(code).hasSizeGreaterThan(20);
        mvc.perform(post("/api/application-invitations/resolve").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body("code", code)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.event.id").value(eventId.toString()));
        UUID profileId = id(write(post("/api/me/booth-profiles"), operator,
                body("name", "Invitee", "categoryCode", "FOOD"))
                .andExpect(status().isCreated()).andReturn(), "$.id");
        String application = body("boothProfileId", profileId, "applicantName", "Invitee",
                "applicantPhone", "01012345678", "applicantEmail", "invitee@example.com");
        write(post(eventPath + "/applications"), operator, application)
                .andExpect(status().isNotFound());
        write(post(eventPath + "/applications"), operator,
                body("boothProfileId", profileId, "applicantName", "Invitee",
                        "applicantPhone", "01012345678", "applicantEmail", "invitee@example.com",
                        "invitationCode", code)).andExpect(status().isCreated());
        write(post(eventPath + "/applications"), operator,
                body("boothProfileId", profileId, "applicantName", "Invitee",
                        "applicantPhone", "01012345678", "applicantEmail", "invitee@example.com",
                        "invitationCode", code)).andExpect(status().isConflict());
        mvc.perform(delete(eventPath + "/application-invitation").with(user(owner)).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/application-invitations/resolve").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body("code", code)))
                .andExpect(status().isNotFound());
    }

    @Test
    void announcementImagesAreReadableOnlyByTheirCurrentAudience() throws Exception {
        AccountPrincipal owner = account("media_owner");
        AccountPrincipal staff = account("media_staff");
        AccountPrincipal operator = account("media_operator");
        UUID eventId = event(owner);
        jdbc.update("UPDATE app.events SET publication_status='PUBLISHED' WHERE id=?", eventId);
        UUID organizationId = jdbc.queryForObject("SELECT organization_id FROM app.events WHERE id=?", UUID.class, eventId);
        jdbc.update("INSERT INTO app.organization_memberships(organization_id,account_id,role) VALUES (?,?,'MEMBER')",
                organizationId, staff.getAccountId());
        jdbc.update("INSERT INTO app.event_memberships(event_id,account_id,organization_id,role) VALUES (?,?,?,'STAFF')",
                eventId, staff.getAccountId(), organizationId);
        UUID boothId = id(write(post("/api/events/" + eventId + "/booths"), owner,
                body("name", "Operator booth", "categoryCode", "FOOD"))
                .andExpect(status().isCreated()).andReturn(), "$.eventBoothId");
        jdbc.update("INSERT INTO app.booth_memberships(event_booth_id,account_id) VALUES (?,?)",
                boothId, operator.getAccountId());

        UUID staffImage = upload(eventId, owner);
        String staffImagePath = "/api/media-assets/" + staffImage + "/content";
        mvc.perform(get(staffImagePath).with(user(staff))).andExpect(status().isNotFound());
        write(post("/api/events/" + eventId + "/announcements"), owner,
                body("title", "Staff notice", "body", "Internal", "imageAssetId", staffImage,
                        "audiences", List.of("STAFF"), "publicationStatus", "PUBLISHED"))
                .andExpect(status().isCreated());
        mvc.perform(get(staffImagePath).with(user(staff))).andExpect(status().isOk());
        mvc.perform(get(staffImagePath).with(user(operator))).andExpect(status().isNotFound());
        mvc.perform(get(staffImagePath)).andExpect(status().isNotFound());

        UUID operatorImage = upload(eventId, owner);
        String operatorImagePath = "/api/media-assets/" + operatorImage + "/content";
        write(post("/api/events/" + eventId + "/announcements"), owner,
                body("title", "Operator notice", "body", "Internal", "imageAssetId", operatorImage,
                        "audiences", List.of("OPERATORS"), "publicationStatus", "PUBLISHED"))
                .andExpect(status().isCreated());
        mvc.perform(get(operatorImagePath).with(user(operator))).andExpect(status().isOk());
        mvc.perform(get(operatorImagePath).with(user(staff))).andExpect(status().isNotFound());

        UUID publicImage = upload(eventId, owner);
        String publicImagePath = "/api/media-assets/" + publicImage + "/content";
        write(post("/api/events/" + eventId + "/announcements"), owner,
                body("title", "Public notice", "body", "Everyone", "imageAssetId", publicImage,
                        "audiences", List.of("PUBLIC"), "publicationStatus", "PUBLISHED"))
                .andExpect(status().isCreated());
        mvc.perform(get(publicImagePath)).andExpect(status().isOk());

        UUID draftImage = upload(eventId, owner);
        String draftImagePath = "/api/media-assets/" + draftImage + "/content";
        write(post("/api/events/" + eventId + "/announcements"), owner,
                body("title", "Draft notice", "body", "Later", "imageAssetId", draftImage,
                        "audiences", List.of("STAFF"), "publicationStatus", "DRAFT"))
                .andExpect(status().isCreated());
        mvc.perform(get(draftImagePath).with(user(staff))).andExpect(status().isNotFound());
    }

    @Test
    void rejectedApplicationKeepsItsHistoryAndAllowsAnewSubmission() throws Exception {
        AccountPrincipal owner = account("reject_owner");
        AccountPrincipal operator = account("reject_operator");
        UUID eventId = event(owner);
        jdbc.update("UPDATE app.events SET publication_status='PUBLISHED' WHERE id=?", eventId);
        String eventPath = "/api/events/" + eventId;
        write(put(eventPath + "/recruitment"), owner,
                body("revision", 0, "introduction", "Open", "publicationStatus", "PUBLISHED",
                        "closesAt", Instant.now().plusSeconds(86400), "targetBoothCount", 10,
                        "allowedCategoryCodes", List.of("FOOD"), "participationFeeKrw", 0,
                        "feeNote", "", "documentRequirements", List.of())).andExpect(status().isOk());
        UUID profileId = id(write(post("/api/me/booth-profiles"), operator,
                body("name", "Rejected booth", "categoryCode", "FOOD"))
                .andExpect(status().isCreated()).andReturn(), "$.id");
        String apply = body("boothProfileId", profileId, "applicantName", "Operator",
                "applicantPhone", "01012345678", "applicantEmail", "operator@example.com");
        UUID first = id(write(post(eventPath + "/applications"), operator, apply)
                .andExpect(status().isCreated()).andReturn(), "$.applicationId");
        String firstPath = eventPath + "/applications/" + first;
        write(patch(firstPath + "/review-note"), owner,
                body("revision", 0, "reviewNote", "Private review"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.revision").value(1));
        write(post(firstPath + "/decision"), owner,
                body("revision", 1, "decision", "REJECTED", "rejectionReason", "Missing details"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.reviewStatus").value("REJECTED"));
        mvc.perform(get("/api/me/applications/" + first).with(user(operator)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.rejectionReason").value("Missing details"))
                .andExpect(jsonPath("$.reviewNote").doesNotExist());
        UUID second = id(write(post(eventPath + "/applications"), operator, apply)
                .andExpect(status().isCreated()).andReturn(), "$.applicationId");
        assertThat(second).isNotEqualTo(first);
        mvc.perform(get(eventPath + "/applications/summary").with(user(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.pending").value(1)).andExpect(jsonPath("$.rejected").value(1));
        mvc.perform(get(eventPath + "/applications").with(user(owner)).param("reviewStatus", "REJECTED")
                        .param("q", "Rejected").param("size", "7"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].applicationId").value(first.toString()));
    }

    @Test
    void clonedDraftDoesNotReplaceThePublishedMapUntilPublication() throws Exception {
        AccountPrincipal owner = account("map_owner");
        UUID eventId = event(owner);
        jdbc.update("UPDATE app.events SET publication_status='PUBLISHED' WHERE id=?", eventId);
        String eventPath = "/api/events/" + eventId;
        UUID imageId = upload(eventId, owner);
        UUID first = id(write(post(eventPath + "/floor-plans"), owner, body("mediaAssetId", imageId))
                .andExpect(status().isCreated()).andReturn(), "$.floorPlanId");
        UUID originalPin = UUID.randomUUID();
        write(put(eventPath + "/floor-plans/" + first), owner,
                body("revision", 0, "pins", List.of(Map.of("id", originalPin, "pinType", "INFO",
                        "label", "Info", "xRatio", 0.2, "yRatio", 0.3))))
                .andExpect(status().isOk());
        write(post(eventPath + "/floor-plans/" + first + "/publication"), owner, body("revision", 1))
                .andExpect(status().isOk());
        UUID second = id(write(post(eventPath + "/floor-plans"), owner, body("sourceFloorPlanId", first))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.pins[0].xRatio").value(0.2))
                .andReturn(), "$.floorPlanId");
        mvc.perform(get(eventPath + "/map-editor").with(user(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.publishedMap.floorPlanId").value(first.toString()))
                .andExpect(jsonPath("$.draftMap.floorPlanId").value(second.toString()));
        mvc.perform(get("/api/public/events/" + eventId + "/map"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.floorPlanId").value(first.toString()))
                .andExpect(jsonPath("$.pins[0].xRatio").value(0.2));
        write(put(eventPath + "/floor-plans/" + second), owner,
                body("revision", 0, "pins", List.of(Map.of("id", UUID.randomUUID(), "pinType", "INFO",
                        "label", "Moved info", "xRatio", 0.7, "yRatio", 0.8))))
                .andExpect(status().isOk());
        write(put(eventPath + "/floor-plans/" + second), owner,
                body("revision", 0, "pins", List.of()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("REVISION_CONFLICT"));
        mvc.perform(get("/api/public/events/" + eventId + "/map"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.pins[0].label").value("Info"));
        write(post(eventPath + "/floor-plans/" + second + "/publication"), owner, body("revision", 1))
                .andExpect(status().isOk());
        mvc.perform(get("/api/public/events/" + eventId + "/map"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.floorPlanId").value(second.toString()))
                .andExpect(jsonPath("$.pins[0].label").value("Moved info"));
    }

    private org.springframework.test.web.servlet.ResultActions write(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
            AccountPrincipal actor, String payload) throws Exception {
        return mvc.perform(request.with(user(actor)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(payload));
    }

    private UUID upload(UUID eventId, AccountPrincipal owner) throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        MockMultipartFile file = new MockMultipartFile("file", "floor.png", "image/png", output.toByteArray());
        MvcResult result = mvc.perform(multipart("/api/events/" + eventId + "/media-assets")
                        .file(file).with(user(owner)).with(csrf()))
                .andExpect(status().isCreated()).andReturn();
        return id(result, "$.mediaAssetId");
    }

    private UUID id(MvcResult result, String path) throws Exception {
        return UUID.fromString(json(result, path));
    }
}
