package kr.boothrock.api.announcement.service;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.boothrock.api.announcement.dto.CreateAnnouncementRequest;
import kr.boothrock.api.announcement.dto.UpdateAnnouncementRequest;
import kr.boothrock.api.announcement.repository.AnnouncementRepository;
import kr.boothrock.api.announcement.repository.AnnouncementRepository.Readers;
import kr.boothrock.api.common.repository.SqlStore;
import kr.boothrock.api.common.service.EventAccess;
import kr.boothrock.api.common.service.Rules;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("local & !deploy")
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class AnnouncementService {
    private static final Readers MANAGER = new Readers(true, false, false);
    private static final Readers PUBLIC = new Readers(false, false, false);

    private final AnnouncementRepository announcements;
    private final EventAccess access;

    public AnnouncementService(AnnouncementRepository announcements, EventAccess access) {
        this.announcements = announcements;
        this.access = access;
    }

    public Map<String, Object> list(UUID eventId, UUID actor, String view, String q, String audience,
                                    int page, int size) {
        Rules.enumValue(view, "READ", "MANAGE");
        Readers readers;
        if ("MANAGE".equals(view)) {
            access.requireManager(eventId, actor);
            readers = MANAGER;
        } else {
            Readers participant = readers(eventId, actor);
            readers = participant.manager() ? new Readers(false, true, true) : participant;
        }
        return page(eventId, readers, q, audience, page, size);
    }

    public Map<String, Object> detail(UUID eventId, UUID id, UUID actor) {
        return announcements.detail(eventId, id, readers(eventId, actor));
    }

    public List<Map<String, Object>> recent(UUID eventId, UUID actor) {
        return announcements.recent(eventId, readers(eventId, actor));
    }

    public Map<String, Object> publicList(UUID eventId, String q, String audience, int page, int size) {
        access.requirePublic(eventId);
        return page(eventId, PUBLIC, q, audience, page, size);
    }

    public Map<String, Object> publicDetail(UUID eventId, UUID id) {
        access.requirePublic(eventId);
        return announcements.detail(eventId, id, PUBLIC);
    }

    @Transactional
    public Map<String, Object> create(UUID eventId, UUID actor, CreateAnnouncementRequest request) {
        access.lockEvent(eventId);
        Map<String, Object> event = access.requireManager(eventId, actor);
        List<String> audiences = validate(eventId, request.title(), request.body(), request.imageAssetId(),
                request.audiences(), request.publicationStatus());
        UUID id = UUID.randomUUID();
        announcements.create(id, eventId, actor, Rules.text(request.title()), Rules.text(request.body()),
                request.imageAssetId(), Boolean.TRUE.equals(request.isUrgent()), request.publicationStatus(),
                publishedAt(request.publicationStatus()));
        announcements.replaceAudiences(id, audiences);
        announcements.audit(eventId, SqlStore.uuid(event, "organizationId"), actor, id, "ANNOUNCEMENT_CREATED");
        return announcements.detail(eventId, id, MANAGER);
    }

    @Transactional
    public Map<String, Object> update(UUID eventId, UUID id, UUID actor, UpdateAnnouncementRequest request) {
        access.lockEvent(eventId);
        Map<String, Object> event = access.requireManager(eventId, actor);
        Rules.revision(request.revision(), announcements.detail(eventId, id, MANAGER));
        List<String> audiences = validate(eventId, request.title(), request.body(), request.imageAssetId(),
                request.audiences(), request.publicationStatus());
        announcements.update(eventId, id, Rules.text(request.title()), Rules.text(request.body()),
                request.imageAssetId(), Boolean.TRUE.equals(request.isUrgent()), request.publicationStatus(),
                publishedAt(request.publicationStatus()));
        announcements.replaceAudiences(id, audiences);
        announcements.audit(eventId, SqlStore.uuid(event, "organizationId"), actor, id, "ANNOUNCEMENT_UPDATED");
        return announcements.detail(eventId, id, MANAGER);
    }

    @Transactional
    public void delete(UUID eventId, UUID id, UUID actor, long revision) {
        Rules.input(revision >= 0, "Revision must not be negative.");
        access.lockEvent(eventId);
        Map<String, Object> event = access.requireManager(eventId, actor);
        Rules.revision(revision, announcements.detail(eventId, id, MANAGER));
        announcements.delete(eventId, id);
        announcements.audit(eventId, SqlStore.uuid(event, "organizationId"), actor, id, "ANNOUNCEMENT_DELETED");
    }

    private Readers readers(UUID eventId, UUID actor) {
        access.requireParticipant(eventId, actor);
        if (access.isManager(eventId, actor)) {
            return MANAGER;
        }
        return new Readers(false, access.isStaff(eventId, actor), access.isOperator(eventId, actor));
    }

    private Map<String, Object> page(UUID eventId, Readers readers, String q, String audience,
                                     int page, int size) {
        Rules.page(page, size);
        if (audience != null) {
            Rules.enumValue(audience, "STAFF", "OPERATORS", "PUBLIC");
        }
        return announcements.page(eventId, readers, Rules.like(q), Rules.text(audience), page, size);
    }

    private List<String> validate(UUID eventId, String title, String body, UUID imageAssetId,
                                  List<String> requestedAudiences, String status) {
        Rules.enumValue(status, "DRAFT", "PUBLISHED");
        List<String> audiences = requestedAudiences == null ? List.of() : requestedAudiences;
        for (String audience : audiences) {
            Rules.enumValue(audience, "STAFF", "OPERATORS", "PUBLIC");
        }
        Rules.input(new HashSet<>(audiences).size() == audiences.size(), "Audiences must be distinct.");
        if ("PUBLISHED".equals(status)) {
            Rules.input(!Rules.text(title).isBlank() && !Rules.text(body).isBlank() && !audiences.isEmpty(),
                    "Publishing requires a title, body, and at least one audience.");
        }
        announcements.requireImage(eventId, imageAssetId);
        return List.copyOf(audiences);
    }

    private Instant publishedAt(String status) {
        return "PUBLISHED".equals(status) ? Instant.now() : null;
    }
}
