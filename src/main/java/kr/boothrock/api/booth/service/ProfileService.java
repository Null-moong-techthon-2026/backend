package kr.boothrock.api.booth.service;

import java.util.Map;
import java.util.UUID;
import kr.boothrock.api.booth.dto.BoothRequests.CreateProfile;
import kr.boothrock.api.booth.dto.BoothRequests.PatchProfile;
import kr.boothrock.api.booth.repository.ProfileRepository;
import kr.boothrock.api.common.service.EventAccess;
import kr.boothrock.api.common.service.Rules;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class ProfileService {
    private final ProfileRepository profiles;
    private final EventAccess access;

    public ProfileService(ProfileRepository profiles, EventAccess access) {
        this.profiles = profiles;
        this.access = access;
    }

    public Map<String, Object> list(UUID owner, int page, int size) {
        access.requireAccount(owner);
        Rules.page(page, size);
        return profiles.list(owner, page, size);
    }

    public Map<String, Object> detail(UUID owner, UUID id) {
        access.requireAccount(owner);
        return profiles.owned(id, owner, false);
    }

    @Transactional
    public Map<String, Object> create(UUID owner, CreateProfile request) {
        access.requireAccount(owner);
        Rules.category(request.categoryCode());
        return profiles.owned(profiles.create(owner, request), owner, false);
    }

    @Transactional
    public Map<String, Object> patch(UUID owner, UUID id, PatchProfile request) {
        access.requireAccount(owner);
        Rules.revision(request.revision(), profiles.owned(id, owner, true));
        if (request.categoryCode() != null) Rules.category(request.categoryCode());
        profiles.patch(id, request);
        return profiles.owned(id, owner, false);
    }

    @Transactional
    public void delete(UUID owner, UUID id, long revision) {
        access.requireAccount(owner);
        Rules.input(revision >= 0, "revision must be nonnegative");
        Rules.revision(revision, profiles.owned(id, owner, true));
        Rules.require(!profiles.hasApplications(id), "INVALID_STATE", "A profile with application history cannot be deleted");
        profiles.delete(id);
    }
}
