package kr.boothrock.api.map.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import java.util.UUID;
import kr.boothrock.api.map.service.MapService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("(local | staging) & !deploy")
@RequestMapping("/api/public/events/{eventId}/map")
@Tag(name = "방문객 지도 조회")
public class PublicMapController {
    private final MapService maps;

    public PublicMapController(MapService maps) {
        this.maps = maps;
    }

    @GetMapping
    @Operation(summary = "공개 지도·부스·편의시설 핀 조회")
    public ResponseEntity<Map<String, Object>> map(@PathVariable UUID eventId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(maps.publicMap(eventId));
    }
}
