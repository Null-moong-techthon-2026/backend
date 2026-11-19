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
@Profile("(local | staging) & !deploy")
@RequestMapping("/api/events/{eventId}")
@Tag(name = "지도 제작")
public class MapController {
    private final MapService maps;

    public MapController(MapService maps) {
        this.maps = maps;
    }

    @GetMapping("/map-editor")
    @Operation(summary = "공개 지도 요약·편집 중인 지도 조회")
    public Map<String, Object> editor(@PathVariable UUID eventId,
            @AuthenticationPrincipal AccountPrincipal principal) {
        return maps.editor(eventId, principal.getAccountId());
    }

    @PostMapping("/floor-plans")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "업로드 이미지·기존 공개 지도로 편집 초안 생성")
    public Map<String, Object> create(@PathVariable UUID eventId,
            @AuthenticationPrincipal AccountPrincipal principal, @Valid @RequestBody CreateFloorPlanRequest request) {
        return maps.create(eventId, principal.getAccountId(), request);
    }

    @PutMapping("/floor-plans/{floorPlanId}")
    @Operation(summary = "편집 중인 지도의 핀 전체 저장", description = "핀은 최대 500개까지 저장할 수 있습니다. 기존 핀 중 요청에 없는 핀은 삭제됩니다.")
    public Map<String, Object> replacePins(@PathVariable UUID eventId, @PathVariable UUID floorPlanId,
            @AuthenticationPrincipal AccountPrincipal principal, @Valid @RequestBody ReplaceMapPinsRequest request) {
        return maps.replacePins(eventId, floorPlanId, principal.getAccountId(), request);
    }

    @DeleteMapping("/floor-plans/{floorPlanId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "편집 중인 지도·핀 삭제")
    public void delete(@PathVariable UUID eventId, @PathVariable UUID floorPlanId,
            @AuthenticationPrincipal AccountPrincipal principal,
            @RequestParam @PositiveOrZero @Parameter(example = "0") long revision) {
        maps.delete(eventId, floorPlanId, principal.getAccountId(), revision);
    }

    @PostMapping("/floor-plans/{floorPlanId}/publication")
    @Operation(summary = "지도 게시·기존 공개 지도 보관")
    public Map<String, Object> publish(@PathVariable UUID eventId, @PathVariable UUID floorPlanId,
            @AuthenticationPrincipal AccountPrincipal principal, @Valid @RequestBody PublishMapRequest request) {
        return maps.publish(eventId, floorPlanId, principal.getAccountId(), request.revision());
    }
}
