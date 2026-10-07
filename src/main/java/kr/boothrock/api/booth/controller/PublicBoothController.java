package kr.boothrock.api.booth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import java.util.UUID;
import kr.boothrock.api.booth.dto.BoothQuery;
import kr.boothrock.api.booth.service.BoothService;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("local & !deploy")
@RequestMapping("/api/public/events/{eventId}/booths")
@Tag(name = "Public booths")
public class PublicBoothController {
    private final BoothService booths;

    public PublicBoothController(BoothService booths) {
        this.booths = booths;
    }

    @GetMapping
    @Operation(summary = "List visible booths of a published event")
    public Map<String, Object> list(@PathVariable UUID eventId, @ParameterObject @ModelAttribute BoothQuery query) {
        return booths.publicList(eventId, query.filter());
    }

    @GetMapping("/{boothId}")
    @Operation(summary = "Read a public booth's description, items, and published map placement")
    public Map<String, Object> detail(@PathVariable UUID eventId, @PathVariable UUID boothId) {
        return booths.publicDetail(eventId, boothId);
    }
}
