package kr.boothrock.api.map.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.Map;
import java.util.UUID;
import kr.boothrock.api.auth.AccountPrincipal;
import kr.boothrock.api.map.dto.CreateFloorPlanRequest;
import kr.boothrock.api.map.dto.PublishMapRequest;
import kr.boothrock.api.map.dto.ReplaceMapPinsRequest;
import kr.boothrock.api.map.service.MapService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("local & !deploy")
@RequestMapping("/api/events/{eventId}")
@Tag(name = "Map Editor")
public class MapController {
    private final MapService maps;

    public MapController(MapService maps) {
        this.maps = maps;
    }

    @GetMapping("/map-editor")
    @Operation(summary = "Read the published map summary and editable draft")
    public Map<String, Object> editor(@PathVariable UUID eventId,
            @AuthenticationPrincipal AccountPrincipal principal) {
        return maps.editor(eventId, principal.getAccountId());
    }

    @PostMapping("/floor-plans")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a draft from an uploaded image or the event's published map")
    public Map<String, Object> create(@PathVariable UUID eventId,
            @AuthenticationPrincipal AccountPrincipal principal, @Valid @RequestBody CreateFloorPlanRequest request) {
        return maps.create(eventId, principal.getAccountId(), request);
    }

    @PutMapping("/floor-plans/{floorPlanId}")
    @Operation(summary = "Replace the draft's complete pin set", description = "At most 500 pins; omitted pins are deleted.")
    public Map<String, Object> replacePins(@PathVariable UUID eventId, @PathVariable UUID floorPlanId,
            @AuthenticationPrincipal AccountPrincipal principal, @Valid @RequestBody ReplaceMapPinsRequest request) {
        return maps.replacePins(eventId, floorPlanId, principal.getAccountId(), request);
    }

    @DeleteMapping("/floor-plans/{floorPlanId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a draft and its pins")
    public void delete(@PathVariable UUID eventId, @PathVariable UUID floorPlanId,
            @AuthenticationPrincipal AccountPrincipal principal,
            @RequestParam @PositiveOrZero @Parameter(example = "0") long revision) {
        maps.delete(eventId, floorPlanId, principal.getAccountId(), revision);
    }

    @PostMapping("/floor-plans/{floorPlanId}/publication")
    @Operation(summary = "Publish the draft and archive the previous published map")
    public Map<String, Object> publish(@PathVariable UUID eventId, @PathVariable UUID floorPlanId,
            @AuthenticationPrincipal AccountPrincipal principal, @Valid @RequestBody PublishMapRequest request) {
        return maps.publish(eventId, floorPlanId, principal.getAccountId(), request.revision());
    }
}
