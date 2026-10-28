package kr.boothrock.api.event.service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import kr.boothrock.api.announcement.service.AnnouncementService;
import kr.boothrock.api.booth.repository.BoothRepository;
import kr.boothrock.api.common.service.EventAccess;
import kr.boothrock.api.common.service.Rules;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("local & !deploy")
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class DashboardService {
    private final EventAccess access;
    private final BoothRepository booths;
    private final AnnouncementService announcements;

    public DashboardService(EventAccess access, BoothRepository booths, AnnouncementService announcements) {
        this.access = access;
        this.booths = booths;
        this.announcements = announcements;
    }

    public Map<String, Object> get(UUID eventId, UUID actor) {
        access.requireStaff(eventId, actor);
        return Rules.result("boothSummary", booths.summary(eventId),
                "recentAnnouncements", announcements.recent(eventId, actor), "asOf", Instant.now());
    }
}
