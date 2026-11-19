package kr.boothrock.api.booth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import kr.boothrock.api.auth.AccountPrincipal;
import kr.boothrock.api.booth.dto.BoothRequests.CreateProfile;
import kr.boothrock.api.booth.dto.BoothRequests.PatchProfile;
import kr.boothrock.api.booth.service.ProfileService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("(local | staging) & !deploy")
@RequestMapping("/api/me/booth-profiles")
@Tag(name = "부스 프로필")
public class BoothProfileController {
    private final ProfileService profiles;

    public BoothProfileController(ProfileService profiles) {
        this.profiles = profiles;
    }

    @GetMapping
    @Operation(summary = "내 부스 프로필 목록 조회")
    public Map<String, Object> list(@AuthenticationPrincipal AccountPrincipal actor,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return profiles.list(actor.getAccountId(), page, size);
    }

    @GetMapping("/{profileId}")
    @Operation(summary = "내 부스 프로필 상세 조회")
    public Map<String, Object> detail(@AuthenticationPrincipal AccountPrincipal actor, @PathVariable UUID profileId) {
        return profiles.detail(actor.getAccountId(), profileId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "내 부스 프로필 생성")
    public Map<String, Object> create(@AuthenticationPrincipal AccountPrincipal actor,
            @Valid @RequestBody CreateProfile request) {
        return profiles.create(actor.getAccountId(), request);
    }

    @PatchMapping("/{profileId}")
    @Operation(summary = "내 부스 프로필 수정")
    public Map<String, Object> patch(@AuthenticationPrincipal AccountPrincipal actor, @PathVariable UUID profileId,
            @Valid @RequestBody PatchProfile request) {
        return profiles.patch(actor.getAccountId(), profileId, request);
    }

    @DeleteMapping("/{profileId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "사용하지 않는 부스 프로필 삭제")
    public void delete(@AuthenticationPrincipal AccountPrincipal actor, @PathVariable UUID profileId,
            @RequestParam long revision) {
        profiles.delete(actor.getAccountId(), profileId, revision);
    }
}
