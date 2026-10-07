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
@Profile("local & !deploy")
@RequestMapping("/api/me/booth-profiles")
@Tag(name = "Booth profiles")
public class BoothProfileController {
    private final ProfileService profiles;

    public BoothProfileController(ProfileService profiles) {
        this.profiles = profiles;
    }

    @GetMapping
    @Operation(summary = "List my reusable booth profiles")
    public Map<String, Object> list(@AuthenticationPrincipal AccountPrincipal actor,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return profiles.list(actor.getAccountId(), page, size);
    }

    @GetMapping("/{profileId}")
    @Operation(summary = "Read my booth profile")
    public Map<String, Object> detail(@AuthenticationPrincipal AccountPrincipal actor, @PathVariable UUID profileId) {
        return profiles.detail(actor.getAccountId(), profileId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create my booth profile")
    public Map<String, Object> create(@AuthenticationPrincipal AccountPrincipal actor,
            @Valid @RequestBody CreateProfile request) {
        return profiles.create(actor.getAccountId(), request);
    }

    @PatchMapping("/{profileId}")
    @Operation(summary = "Update my booth profile")
    public Map<String, Object> patch(@AuthenticationPrincipal AccountPrincipal actor, @PathVariable UUID profileId,
            @Valid @RequestBody PatchProfile request) {
        return profiles.patch(actor.getAccountId(), profileId, request);
    }

    @DeleteMapping("/{profileId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete an unused booth profile")
    public void delete(@AuthenticationPrincipal AccountPrincipal actor, @PathVariable UUID profileId,
            @RequestParam long revision) {
        profiles.delete(actor.getAccountId(), profileId, revision);
    }
}
