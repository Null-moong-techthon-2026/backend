package kr.boothrock.api.eventapplication.service;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.boothrock.api.common.repository.SqlStore;
import kr.boothrock.api.common.service.EventAccess;
import kr.boothrock.api.common.service.Rules;
import kr.boothrock.api.event.repository.EventRepository;
import kr.boothrock.api.eventapplication.dto.EventApplicationRequests.Create;
import kr.boothrock.api.eventapplication.dto.EventApplicationRequests.Decision;
import kr.boothrock.api.eventapplication.repository.EventApplicationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Event-opening applications. An organizer applies; a platform administrator approves or rejects.
 * Approving opens the event in the same transaction: it creates the organization (the applicant is
 * its OWNER) and a DRAFT event with its empty recruitment, so the applicant can manage it at once.
 */
@Service
@Transactional(readOnly = true)
public class EventApplicationService {
    /** Event dates are whole days in Korea: the start date at 00:00, the end date until 23:59:59. */
    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    private static final List<String> AGREEMENTS = List.of("terms", "privacy", "outsourcing", "billing", "refund");

    private final EventApplicationRepository applications;
    private final EventRepository events;
    private final EventAccess access;

    public EventApplicationService(EventApplicationRepository applications, EventRepository events, EventAccess access) {
        this.applications = applications;
        this.events = events;
        this.access = access;
    }

    // ---- applicant ----

    @Transactional
    public Map<String, Object> submit(UUID actor, Create request) {
        access.requireAccount(actor);
        validate(request);
        Rules.require(!applications.hasPending(actor), "DUPLICATE_APPLICATION",
                "An application of this account is already waiting for review.");
        UUID id = applications.insert(actor, request, request.agreements().toArray(String[]::new));
        return applications.ownedBy(actor, id);
    }

    public Map<String, Object> mine(UUID actor, int page, int size) {
        access.requireAccount(actor);
        Rules.page(page, size);
        return applications.mine(actor, page, size);
    }

    private void validate(Create r) {
        Rules.input(!r.endDate().isBefore(r.startDate()), "endDate must not be before startDate.");
        String digits = r.businessNumber() == null ? "" : r.businessNumber().trim();
        if ("COMPANY".equals(r.orgType())) {
            Rules.input(digits.matches("[0-9]{10}"), "A business registration number of 10 digits is required for companies.");
        } else {
            Rules.input(digits.isEmpty() || digits.matches("[0-9]{10}"), "The business registration number must be 10 digits.");
        }
        if ("STUDENT".equals(r.orgType()) || "PUBLIC".equals(r.orgType())) {
            Rules.input(r.affiliation() != null && !r.affiliation().isBlank(), "The school or institution name is required.");
        }
        var given = new HashSet<>(r.agreements());
        Rules.input(given.size() == r.agreements().size() && given.equals(new HashSet<>(AGREEMENTS)),
                "Every agreement is required: " + AGREEMENTS);
    }

    // ---- administrator ----

    public Map<String, Object> list(UUID actor, String status, int page, int size) {
        access.requireAdmin(actor);
        Rules.page(page, size);
        if (status != null) Rules.enumValue(status, "PENDING", "APPROVED", "REJECTED");
        Map<String, Object> result = applications.list(status, page, size);
        result.put("summary", applications.summary());
        return result;
    }

    public Map<String, Object> detail(UUID actor, UUID id) {
        access.requireAdmin(actor);
        return applications.one(id);
    }

    @Transactional
    public Map<String, Object> decide(UUID actor, UUID id, Decision request) {
        access.requireAdmin(actor);
        Map<String, Object> application = applications.lock(id);
        String current = (String) application.get("status");
        if (!"PENDING".equals(current)) {
            // Sending the same decision again just returns the result; a different one is refused.
            Rules.require(request.decision().equals(current), "INVALID_STATE", "This application already has a different decision.");
            return application;
        }
        Rules.revision(request.revision(), application);
        if ("REJECTED".equals(request.decision())) {
            String reason = request.rejectionReason() == null ? "" : request.rejectionReason().trim();
            Rules.input(!reason.isEmpty(), "A rejection reason is required.");
            applications.reject(id, actor, reason);
        } else {
            Rules.input(request.rejectionReason() == null || request.rejectionReason().isBlank(),
                    "An approved application cannot have a rejection reason.");
            UUID applicant = SqlStore.uuid(application, "applicantAccountId");
            UUID organization = UUID.randomUUID();
            UUID event = UUID.randomUUID();
            java.time.LocalDate start = java.time.LocalDate.parse((String) application.get("startDate"));
            java.time.LocalDate end = java.time.LocalDate.parse((String) application.get("endDate"));
            applications.createOrganization(organization, (String) application.get("orgName"),
                    (String) application.get("orgEmail"), applicant);
            applications.createEvent(event, organization, applicant, (String) application.get("eventName"),
                    (String) application.get("venue"), start.atStartOfDay(KOREA).toInstant(),
                    end.atTime(LocalTime.of(23, 59, 59)).atZone(KOREA).toInstant());
            events.audit(event, actor, "EVENT_OPENED_FROM_APPLICATION", "EVENT", event);
            applications.approve(id, actor, organization, event);
        }
        return applications.one(id);
    }
}
