package kr.boothrock.api.booth.service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import kr.boothrock.api.booth.dto.BoothFilter;
import kr.boothrock.api.booth.dto.BoothRequests.CreateBooth;
import kr.boothrock.api.booth.dto.BoothRequests.CreateItem;
import kr.boothrock.api.booth.dto.BoothRequests.OperationStatus;
import kr.boothrock.api.booth.dto.BoothRequests.PatchBooth;
import kr.boothrock.api.booth.dto.BoothRequests.PatchItem;
import kr.boothrock.api.booth.repository.BoothRepository;
import kr.boothrock.api.common.service.ApiException;
import kr.boothrock.api.common.service.EventAccess;
import kr.boothrock.api.common.service.Rules;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class BoothService {
    private final BoothRepository booths;
    private final EventAccess access;

    public BoothService(BoothRepository booths, EventAccess access) {
        this.booths = booths;
        this.access = access;
    }

    @Transactional
    public Map<String, Object> create(UUID eventId, UUID actor, CreateBooth request) {
        access.requireManager(eventId, actor);
        access.lockEvent(eventId);
        access.requireManager(eventId, actor);
        Rules.category(request.categoryCode());
        UUID id = booths.create(eventId, null, booths.allocateCode(eventId, request.boothCode()),
                request.name(), request.categoryCode(), request.description());
        return detailResult(eventId, id, false);
    }

    public Map<String, Object> list(UUID eventId, UUID actor, BoothFilter filter) {
        access.requireStaff(eventId, actor);
        filter.validate();
        if (filter.floorPlanId() != null) {
            access.requireManager(eventId, actor);
            booths.requireFloorPlan(eventId, filter.floorPlanId());
        }
        return booths.list(eventId, filter, false);
    }

    public Map<String, Object> operations(UUID eventId, UUID actor, BoothFilter filter) {
        access.requireStaff(eventId, actor);
        filter.validate();
        Rules.input(filter.floorPlanId() == null, "Operations always uses the published map");
        return Rules.result("summary", booths.summary(eventId), "booths", booths.list(eventId, filter, false), "asOf", Instant.now());
    }

    public Map<String, Object> detail(UUID eventId, UUID actor, UUID boothId) {
        access.requireAccount(actor);
        if (access.isStaff(eventId, actor)) access.requireStaff(eventId, actor);
        else access.requireBooth(eventId, boothId, actor);
        return detailResult(eventId, boothId, false);
    }

    public Map<String, Object> publicList(UUID eventId, BoothFilter filter) {
        access.requirePublic(eventId);
        filter.validate();
        Rules.input(filter.floorPlanId() == null && filter.source() == null && filter.visibilityStatus() == null,
                "Public booth filters cannot select a draft map or internal booth fields");
        return booths.list(eventId, filter, true);
    }

    public Map<String, Object> publicDetail(UUID eventId, UUID boothId) {
        access.requirePublic(eventId);
        return detailResult(eventId, boothId, true);
    }

    private Map<String, Object> detailResult(UUID eventId, UUID boothId, boolean publicOnly) {
        Map<String, Object> result = new LinkedHashMap<>(booths.detail(eventId, boothId, publicOnly));
        result.put("items", booths.items(boothId, publicOnly));
        result.put("mapPlacement", booths.placement(eventId, boothId));
        return result;
    }

    @Transactional
    public Map<String, Object> patch(UUID eventId, UUID actor, UUID boothId, PatchBooth request) {
        Map<String, Object> booth = lockForWrite(eventId, boothId, actor);
        if (request.visibilityStatus() != null && !access.isManager(eventId, actor)) throw ApiException.forbidden();
        Rules.revision(request.revision(), booth);
        if (request.categoryCode() != null) Rules.category(request.categoryCode());
        if (request.visibilityStatus() != null) Rules.enumValue(request.visibilityStatus(), "HIDDEN", "PUBLIC", "ARCHIVED");
        booths.patch(boothId, request);
        return detailResult(eventId, boothId, false);
    }

    @Transactional
    public Map<String, Object> operationStatus(UUID eventId, UUID actor, UUID boothId, OperationStatus request) {
        Rules.revision(request.revision(), lockForWrite(eventId, boothId, actor));
        Rules.enumValue(request.operationStatus(), "PREPARING", "OPEN", "SOLD_OUT", "CLOSED");
        booths.operationStatus(boothId, request.operationStatus());
        return detailResult(eventId, boothId, false);
    }

    @Transactional
    public Map<String, Object> createItem(UUID eventId, UUID actor, UUID boothId, CreateItem request) {
        lockForWrite(eventId, boothId, actor);
        if (request.stockStatus() != null) stock(request.stockStatus());
        Rules.require(booths.itemCount(boothId) < 100, "INVALID_STATE", "A booth can have at most 100 items");
        UUID id = booths.createItem(boothId, request);
        return booths.item(boothId, id);
    }

    @Transactional
    public Map<String, Object> patchItem(UUID eventId, UUID actor, UUID boothId, UUID itemId, PatchItem request) {
        lockForWrite(eventId, boothId, actor);
        Rules.revision(request.revision(), booths.item(boothId, itemId));
        Rules.input(request.isPriceValid(), "priceKrw must be null or a nonnegative integer");
        if (request.stockStatus() != null) stock(request.stockStatus());
        booths.patchItem(boothId, itemId, request);
        return booths.item(boothId, itemId);
    }

    @Transactional
    public void deleteItem(UUID eventId, UUID actor, UUID boothId, UUID itemId, long revision) {
        Rules.input(revision >= 0, "revision must be nonnegative");
        lockForWrite(eventId, boothId, actor);
        Rules.revision(revision, booths.item(boothId, itemId));
        booths.deleteItem(boothId, itemId);
    }

    public Map<String, Object> mine(UUID actor, int page, int size) {
        access.requireAccount(actor);
        Rules.page(page, size);
        return booths.mine(actor, page, size);
    }

    private Map<String, Object> lockForWrite(UUID eventId, UUID boothId, UUID actor) {
        access.requireBooth(eventId, boothId, actor);
        access.lockEvent(eventId);
        return access.requireBooth(eventId, boothId, actor);
    }

    private static void stock(String value) { Rules.enumValue(value, "UNLIMITED", "AVAILABLE", "LOW", "SOLD_OUT"); }
}
