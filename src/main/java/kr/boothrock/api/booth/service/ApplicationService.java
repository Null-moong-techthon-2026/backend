package kr.boothrock.api.booth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.boothrock.api.booth.dto.BoothRequests.Apply;
import kr.boothrock.api.booth.dto.BoothRequests.Decision;
import kr.boothrock.api.booth.dto.BoothRequests.DocumentCheck;
import kr.boothrock.api.booth.dto.BoothRequests.ReviewNote;
import kr.boothrock.api.booth.repository.ApplicationRepository;
import kr.boothrock.api.booth.repository.BoothRepository;
import kr.boothrock.api.booth.repository.ProfileRepository;
import kr.boothrock.api.common.repository.SqlStore;
import kr.boothrock.api.common.service.ApiException;
import kr.boothrock.api.common.service.EventAccess;
import kr.boothrock.api.common.service.Rules;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class ApplicationService {
    private final ApplicationRepository applications;
    private final ProfileRepository profiles;
    private final BoothRepository booths;
    private final EventAccess access;

    public ApplicationService(ApplicationRepository applications, ProfileRepository profiles,
            BoothRepository booths, EventAccess access) {
        this.applications = applications;
        this.profiles = profiles;
        this.booths = booths;
        this.access = access;
    }

    @Transactional
    public Map<String, Object> apply(UUID eventId, UUID actor, Apply request) {
        access.requireAccount(actor);
        access.lockEvent(eventId);
        Map<String, Object> recruitment = applications.recruitment(eventId);
        if (request.invitationCode() != null) {
            if (!applications.validInvitation(eventId, sha256(request.invitationCode()))) throw ApiException.notFound();
        } else if (!"PUBLISHED".equals(recruitment.get("eventPublicationStatus"))) {
            throw ApiException.notFound();
        }
        Rules.require(!"ARCHIVED".equals(recruitment.get("eventPublicationStatus"))
                && "PUBLISHED".equals(recruitment.get("publicationStatus"))
                && recruitment.get("closesAt") instanceof Instant closes && closes.isAfter(Instant.now()),
                "RECRUITMENT_CLOSED", "Recruitment is not open");
        Map<String, Object> profile = profiles.owned(request.boothProfileId(), actor, true);
        Rules.input(((List<?>) recruitment.get("allowedCategoryCodes")).contains(profile.get("categoryCode")),
                "The profile category is not accepted by this recruitment");
        Rules.require(!applications.currentApplication(eventId, request.boothProfileId()),
                "DUPLICATE_APPLICATION", "A pending or approved application already exists for this profile");
        UUID id = applications.create(eventId, actor, request, profile);
        return details(applications.application(eventId, id, false), false);
    }

    public Map<String, Object> mine(UUID actor, int page, int size) {
        access.requireAccount(actor);
        Rules.page(page, size);
        return applications.mine(actor, page, size);
    }

    public Map<String, Object> mine(UUID actor, UUID applicationId) {
        access.requireAccount(actor);
        return details(applications.owned(actor, applicationId), false);
    }

    public Map<String, Object> list(UUID eventId, UUID actor, int page, int size, String q,
            String category, String status, String sort) {
        access.requireManager(eventId, actor);
        Rules.page(page, size);
        Rules.like(q);
        if (category != null) Rules.category(category);
        if (status != null) Rules.enumValue(status, "PENDING", "APPROVED", "REJECTED");
        Rules.enumValue(sort, "submittedAt,asc", "submittedAt,desc");
        return applications.list(eventId, page, size, q, category, status, sort);
    }

    public Map<String, Object> summary(UUID eventId, UUID actor) {
        access.requireManager(eventId, actor);
        return applications.summary(eventId);
    }

    public Map<String, Object> detail(UUID eventId, UUID actor, UUID applicationId) {
        access.requireManager(eventId, actor);
        return details(applications.application(eventId, applicationId, false), true);
    }

    private Map<String, Object> details(Map<String, Object> row, boolean manager) {
        UUID id = SqlStore.uuid(row, "applicationId");
        UUID eventId = SqlStore.uuid(row, "eventId");
        Map<String, Object> booth = applications.booth(id);
        Map<String, Object> result = Rules.result("applicationId", id, "eventId", eventId,
                "revision", row.get("revision"), "submittedAt", row.get("submittedAt"), "reviewStatus", row.get("reviewStatus"),
                "booth", Rules.result("name", row.get("boothNameSnapshot"), "categoryCode", row.get("categoryCodeSnapshot"),
                        "introduction", row.get("introductionSnapshot")),
                "applicant", Rules.result("name", row.get("contactNameSnapshot"), "phoneNumber", row.get("contactPhoneSnapshot"),
                        "email", row.get("contactEmailSnapshot")),
                "documents", applications.documents(eventId, id, manager), "rejectionReason", row.get("rejectionReason"),
                "reviewedAt", row.get("reviewedAt"), "eventBoothId", booth == null ? null : booth.get("eventBoothId"),
                "boothCode", booth == null ? null : booth.get("boothCode"));
        if (manager) result.put("reviewNote", row.get("reviewNote"));
        return result;
    }

    @Transactional
    public Map<String, Object> reviewNote(UUID eventId, UUID actor, UUID id, ReviewNote request) {
        lockForReview(eventId, actor);
        Rules.revision(request.revision(), applications.application(eventId, id, true));
        applications.reviewNote(id, request.reviewNote());
        return Rules.result("applicationId", id, "revision", request.revision() + 1);
    }

    @Transactional
    public Map<String, Object> documentCheck(UUID eventId, UUID actor, UUID id, UUID requirementId, DocumentCheck request) {
        lockForReview(eventId, actor);
        Map<String, Object> application = applications.application(eventId, id, true);
        Rules.require("PENDING".equals(application.get("reviewStatus")), "INVALID_STATE", "Only pending applications can change document checks");
        Rules.enumValue(request.checkStatus(), "NOT_SUBMITTED", "SUBMITTED", "VERIFIED", "NEEDS_CORRECTION");
        Rules.revision(request.revision(), applications.document(eventId, id, requirementId));
        applications.documentCheck(id, requirementId, actor, request);
        return applications.document(eventId, id, requirementId);
    }

    @Transactional
    public Map<String, Object> decide(UUID eventId, UUID actor, UUID id, Decision request) {
        lockForReview(eventId, actor);
        Map<String, Object> application = applications.application(eventId, id, true);
        Rules.enumValue(request.decision(), "APPROVED", "REJECTED");
        String fingerprint = decisionFingerprint(request);
        // Persist a hash of the original parameters: later note edits must not change retry identity.
        if (!"PENDING".equals(application.get("reviewStatus"))) {
            Map<String, Object> audit = applications.decisionAudit(eventId, id);
            Rules.require(request.decision().equals(application.get("reviewStatus")) && audit != null
                    && fingerprint.equals(audit.get("requestHash")), "INVALID_STATE", "This application already has a different decision");
            return decisionResult(application, SqlStore.revision(audit));
        }
        Rules.revision(request.revision(), application);
        if (request.decision().equals("REJECTED")) {
            Rules.input(request.rejectionReason() != null && !request.rejectionReason().isBlank(), "A rejection reason is required");
            Rules.input(request.boothCode() == null, "A rejected application cannot have a booth code");
        } else {
            Rules.input(request.rejectionReason() == null, "An approved application cannot have a rejection reason");
            Rules.require(applications.requiredDocumentsVerified(eventId, application), "DOCUMENTS_NOT_VERIFIED", "Verify all required documents before approval");
            UUID boothId = booths.create(eventId, id, booths.allocateCode(eventId, request.boothCode()),
                    (String) application.get("boothNameSnapshot"), (String) application.get("categoryCodeSnapshot"),
                    (String) application.get("introductionSnapshot"));
            applications.membership(boothId, SqlStore.uuid(application, "applicantAccountId"));
        }
        applications.decide(id, actor, request);
        long revision = SqlStore.revision(application) + 1;
        applications.decisionAudit(eventId, actor, id, fingerprint, revision);
        return decisionResult(applications.application(eventId, id, false), revision);
    }

    private Map<String, Object> decisionResult(Map<String, Object> application, long revision) {
        Map<String, Object> booth = applications.booth(SqlStore.uuid(application, "applicationId"));
        return Rules.result("applicationId", application.get("applicationId"), "reviewStatus", application.get("reviewStatus"),
                "eventBoothId", booth == null ? null : booth.get("eventBoothId"),
                "boothCode", booth == null ? null : booth.get("boothCode"), "revision", revision);
    }

    private void lockForReview(UUID eventId, UUID actor) {
        access.requireManager(eventId, actor);
        access.lockEvent(eventId);
        access.requireManager(eventId, actor);
    }

    private static String decisionFingerprint(Decision request) {
        StringBuilder value = new StringBuilder();
        for (String field : new String[]{request.decision(), request.boothCode(), request.rejectionReason(), request.reviewNote()}) {
            value.append(field == null ? -1 : field.length()).append(':');
            if (field != null) value.append(field);
        }
        return sha256(value.toString());
    }

    private static String sha256(String raw) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
