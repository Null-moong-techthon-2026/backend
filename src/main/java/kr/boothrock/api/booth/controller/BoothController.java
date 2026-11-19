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
import kr.boothrock.api.booth.dto.BoothRequests.ReplacePhotos;
import kr.boothrock.api.booth.service.BoothService;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("(local | staging) & !deploy")
@RequestMapping("/api")
@Tag(name = "부스 관리·운영")
public class BoothController {
    private final BoothService booths;

    public BoothController(BoothService booths) {
        this.booths = booths;
    }

    @GetMapping("/events/{eventId}/booths")
    @Operation(summary = "승인·직접 등록 부스 목록 조회")
    public Map<String, Object> list(@PathVariable UUID eventId, @AuthenticationPrincipal AccountPrincipal actor,
            @ParameterObject @ModelAttribute BoothQuery query) {
        return booths.list(eventId, actor.getAccountId(), query.filter());
    }

    @GetMapping("/events/{eventId}/operations")
    @Operation(summary = "운영 상태 요약·부스 목록 조회")
    public Map<String, Object> operations(@PathVariable UUID eventId, @AuthenticationPrincipal AccountPrincipal actor,
            @ParameterObject @ModelAttribute BoothQuery query) {
        return booths.operations(eventId, actor.getAccountId(), query.filter(8));
    }

    @GetMapping("/events/{eventId}/booths/{boothId}")
    @Operation(summary = "부스 상세·공개 지도 배치 조회")
    public Map<String, Object> detail(@PathVariable UUID eventId, @PathVariable UUID boothId,
            @AuthenticationPrincipal AccountPrincipal actor) {
        return booths.detail(eventId, actor.getAccountId(), boothId);
    }

    @PostMapping("/events/{eventId}/booths")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "주최자 부스 직접 등록")
    public Map<String, Object> create(@PathVariable UUID eventId, @AuthenticationPrincipal AccountPrincipal actor,
            @Valid @RequestBody CreateBooth request) {
        return booths.create(eventId, actor.getAccountId(), request);
    }

    @PatchMapping("/events/{eventId}/booths/{boothId}")
    @Operation(summary = "부스 정보·공개 상태 수정")
    public Map<String, Object> patch(@PathVariable UUID eventId, @PathVariable UUID boothId,
            @AuthenticationPrincipal AccountPrincipal actor, @Valid @RequestBody PatchBooth request) {
        return booths.patch(eventId, actor.getAccountId(), boothId, request);
    }

    @PutMapping("/events/{eventId}/booths/{boothId}/photos")
    @Operation(summary = "부스 사진·대표 사진 저장", description = "업로드한 사진 ID를 표시 순서대로 최대 10개 저장합니다. 첫 사진이 대표 사진입니다. revision 충돌을 검사합니다.")
    public Map<String, Object> replacePhotos(@PathVariable UUID eventId, @PathVariable UUID boothId,
            @AuthenticationPrincipal AccountPrincipal actor, @Valid @RequestBody ReplacePhotos request) {
        return booths.replacePhotos(eventId, actor.getAccountId(), boothId, request);
    }

    @PatchMapping("/events/{eventId}/booths/{boothId}/operation-status")
    @Operation(summary = "부스 운영 상태 변경")
    public Map<String, Object> operationStatus(@PathVariable UUID eventId, @PathVariable UUID boothId,
            @AuthenticationPrincipal AccountPrincipal actor, @Valid @RequestBody OperationStatus request) {
        return booths.operationStatus(eventId, actor.getAccountId(), boothId, request);
    }

    @PostMapping("/events/{eventId}/booths/{boothId}/items")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "메뉴·상품 등록")
    public Map<String, Object> createItem(@PathVariable UUID eventId, @PathVariable UUID boothId,
            @AuthenticationPrincipal AccountPrincipal actor, @Valid @RequestBody CreateItem request) {
        return booths.createItem(eventId, actor.getAccountId(), boothId, request);
    }

    @PatchMapping("/events/{eventId}/booths/{boothId}/items/{itemId}")
    @Operation(summary = "메뉴·상품 정보·가격·재고 상태 수정")
    public Map<String, Object> patchItem(@PathVariable UUID eventId, @PathVariable UUID boothId,
            @PathVariable UUID itemId, @AuthenticationPrincipal AccountPrincipal actor,
            @Valid @RequestBody PatchItem request) {
        return booths.patchItem(eventId, actor.getAccountId(), boothId, itemId, request);
    }

    @DeleteMapping("/events/{eventId}/booths/{boothId}/items/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "메뉴·상품 삭제")
    public void deleteItem(@PathVariable UUID eventId, @PathVariable UUID boothId, @PathVariable UUID itemId,
            @AuthenticationPrincipal AccountPrincipal actor, @RequestParam long revision) {
        booths.deleteItem(eventId, actor.getAccountId(), boothId, itemId, revision);
    }

    @GetMapping("/me/booths")
    @Operation(summary = "내가 운영하는 부스 목록 조회")
    public Map<String, Object> mine(@AuthenticationPrincipal AccountPrincipal actor,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return booths.mine(actor.getAccountId(), page, size);
    }
}
