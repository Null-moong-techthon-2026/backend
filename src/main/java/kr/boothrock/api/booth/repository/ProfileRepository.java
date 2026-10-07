package kr.boothrock.api.booth.repository;

import java.util.Map;
import java.util.UUID;
import kr.boothrock.api.booth.dto.BoothRequests.CreateProfile;
import kr.boothrock.api.booth.dto.BoothRequests.PatchProfile;
import kr.boothrock.api.common.repository.SqlStore;
import kr.boothrock.api.common.service.Rules;
import org.springframework.stereotype.Repository;

@Repository
public class ProfileRepository {
    private static final String FIELDS = "id, name, category_code, introduction, revision, created_at, updated_at";
    private final SqlStore sql;

    public ProfileRepository(SqlStore sql) { this.sql = sql; }

    public Map<String, Object> list(UUID owner, int page, int size) {
        return sql.page("select " + FIELDS + " from app.booth_profiles where owner_account_id = ? order by created_at desc, id desc",
                "select count(*) from app.booth_profiles where owner_account_id = ?", page, size, owner);
    }

    public Map<String, Object> owned(UUID id, UUID owner, boolean lock) {
        return sql.one("select " + FIELDS + " from app.booth_profiles where id = ? and owner_account_id = ?"
                + (lock ? " for update" : ""), id, owner);
    }

    public UUID create(UUID owner, CreateProfile request) {
        UUID id = UUID.randomUUID();
        sql.update("insert into app.booth_profiles (id, owner_account_id, name, category_code, introduction) values (?, ?, ?, ?, ?)",
                id, owner, request.name(), request.categoryCode(), Rules.text(request.introduction()));
        return id;
    }

    public void patch(UUID id, PatchProfile request) {
        sql.update("""
                update app.booth_profiles set name = coalesce(?, name), category_code = coalesce(?, category_code),
                    introduction = coalesce(?, introduction), revision = revision + 1, updated_at = now() where id = ?
                """, request.name(), request.categoryCode(), request.introduction(), id);
    }

    public boolean hasApplications(UUID id) {
        return sql.count("select count(*) from app.booth_applications where booth_profile_id = ?", id) > 0;
    }

    public void delete(UUID id) { sql.update("delete from app.booth_profiles where id = ?", id); }
}
