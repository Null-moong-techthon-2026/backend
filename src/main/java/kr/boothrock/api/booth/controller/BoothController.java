package kr.boothrock.api.booth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import kr.boothrock.api.auth.AccountPrincipal;
import kr.boothrock.api.booth.dto.BoothQuery;
import kr.boothrock.api.booth.dto.BoothRequests.CreateBooth;
import kr.boothrock.api.booth.dto.BoothRequests.CreateItem;
import kr.boothrock.api.booth.dto.BoothRequests.OperationStatus;
import kr.boothrock.api.booth.dto.BoothRequests.PatchBooth;
import kr.boothrock.api.booth.dto.BoothRequests.PatchItem;
import kr.boothrock.api.booth.service.BoothService;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("local & !deploy")
@RequestMapping("/api")
@Tag(name = "Booths and operations")
public class BoothController {
    private final BoothService booths;

    public BoothController(BoothService booths) {
        this.booths = booths;
    }

    @GetMapping("/events/{eventId}/booths")
    @Operation(summary = "List approved and manually created booths")
    public Map<String, Object> list(@PathVariable UUID eventId, @AuthenticationPrincipal AccountPrincipal actor,
            @ParameterObject @ModelAttribute BoothQuery query) {
        return booths.list(eventId, actor.getAccountId(), query.filter());
    }

    @GetMapping("/events/{eventId}/operations")
    @Operation(summary = "Read operation counts and a filtered booth page")
    public Map<String, Object> operations(@PathVariable UUID eventId, @AuthenticationPrincipal AccountPrincipal actor,
            @ParameterObject @ModelAttribute BoothQuery query) {
        return booths.operations(eventId, actor.getAccountId(), query.filter(8));
    }

    @GetMapping("/events/{eventId}/booths/{boothId}")
    @Operation(summary = "Read booth details and published map placement")
    public Map<String, Object> detail(@PathVariable UUID eventId, @PathVariable UUID boothId,
            @AuthenticationPrincipal AccountPrincipal actor) {
        return booths.detail(eventId, actor.getAccountId(), boothId);
    }

    @PostMapping("/events/{eventId}/booths")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a booth directly as an event manager")
    public Map<String, Object> create(@PathVariable UUID eventId, @AuthenticationPrincipal AccountPrincipal actor,
            @Valid @RequestBody CreateBooth request) {
        return booths.create(eventId, actor.getAccountId(), request);
    }

    @PatchMapping("/events/{eventId}/booths/{boothId}")
    @Operation(summary = "Update booth information and manager-only visibility")
    public Map<String, Object> patch(@PathVariable UUID eventId, @PathVariable UUID boothId,
            @AuthenticationPrincipal AccountPrincipal actor, @Valid @RequestBody PatchBooth request) {
        return booths.patch(eventId, actor.getAccountId(), boothId, request);
    }

    @PatchMapping("/events/{eventId}/booths/{boothId}/operation-status")
    @Operation(summary = "Change a booth's operating state")
    public Map<String, Object> operationStatus(@PathVariable UUID eventId, @PathVariable UUID boothId,
            @AuthenticationPrincipal AccountPrincipal actor, @Valid @RequestBody OperationStatus request) {
        return booths.operationStatus(eventId, actor.getAccountId(), boothId, request);
    }

    @PostMapping("/events/{eventId}/booths/{boothId}/items")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a menu item or product")
    public Map<String, Object> createItem(@PathVariable UUID eventId, @PathVariable UUID boothId,
            @AuthenticationPrincipal AccountPrincipal actor, @Valid @RequestBody CreateItem request) {
        return booths.createItem(eventId, actor.getAccountId(), boothId, request);
    }

    @PatchMapping("/events/{eventId}/booths/{boothId}/items/{itemId}")
    @Operation(summary = "Update a menu item, price, or stock state")
    public Map<String, Object> patchItem(@PathVariable UUID eventId, @PathVariable UUID boothId,
            @PathVariable UUID itemId, @AuthenticationPrincipal AccountPrincipal actor,
            @Valid @RequestBody PatchItem request) {
        return booths.patchItem(eventId, actor.getAccountId(), boothId, itemId, request);
    }

    @DeleteMapping("/events/{eventId}/booths/{boothId}/items/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove a menu item")
    public void deleteItem(@PathVariable UUID eventId, @PathVariable UUID boothId, @PathVariable UUID itemId,
            @AuthenticationPrincipal AccountPrincipal actor, @RequestParam long revision) {
        booths.deleteItem(eventId, actor.getAccountId(), boothId, itemId, revision);
    }

    @GetMapping("/me/booths")
    @Operation(summary = "List booths that I operate")
    public Map<String, Object> mine(@AuthenticationPrincipal AccountPrincipal actor,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return booths.mine(actor.getAccountId(), page, size);
    }
}
