package kr.boothrock.api.map.service;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import kr.boothrock.api.common.repository.SqlStore;
import kr.boothrock.api.common.service.ApiException;
import kr.boothrock.api.common.service.EventAccess;
import kr.boothrock.api.common.service.Rules;
import kr.boothrock.api.map.dto.CreateFloorPlanRequest;
import kr.boothrock.api.map.dto.MapPinRequest;
import kr.boothrock.api.map.dto.ReplaceMapPinsRequest;
import kr.boothrock.api.map.repository.MapRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MapService {
    private final MapRepository maps;
    private final MediaService media;
    private final EventAccess access;

    public MapService(MapRepository maps, MediaService media, EventAccess access) {
        this.maps = maps;
        this.media = media;
        this.access = access;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Map<String, Object> editor(UUID eventId, UUID accountId) {
        access.requireManager(eventId, accountId);
        return Rules.result("publishedMap", summary(maps.byStatus(eventId, "PUBLISHED")),
                "draftMap", draftResponse(maps.byStatus(eventId, "DRAFT")));
    }

    @Transactional
    public Map<String, Object> create(UUID eventId, UUID accountId, CreateFloorPlanRequest request) {
        lockManager(eventId, accountId);
        Rules.input((request.mediaAssetId() == null) != (request.sourceFloorPlanId() == null),
                "Provide exactly one mediaAssetId or sourceFloorPlanId.");
        Rules.require(maps.byStatus(eventId, "DRAFT") == null, "INVALID_STATE", "This event already has a draft map.");
        UUID assetId = request.mediaAssetId();
        List<Map<String, Object>> sourcePins = List.of();
        if (request.sourceFloorPlanId() != null) {
            Map<String, Object> source = maps.plan(eventId, request.sourceFloorPlanId());
            Rules.require("PUBLISHED".equals(source.get("publicationStatus")), "INVALID_STATE",
                    "Only the published map can be cloned.");
            assetId = SqlStore.uuid(source, "mediaAssetId");
            sourcePins = maps.pins(request.sourceFloorPlanId());
        }
        media.requireAsset(eventId, assetId);
        UUID id = UUID.randomUUID();
        maps.create(id, eventId, assetId);
        for (Map<String, Object> pin : sourcePins) {
            maps.insertPin(eventId, id, new MapPinRequest(UUID.randomUUID(), (String) pin.get("pinType"),
                    (String) pin.get("label"), (BigDecimal) pin.get("xRatio"), (BigDecimal) pin.get("yRatio"),
                    SqlStore.uuid(pin, "eventBoothId")));
        }
        maps.audit(eventId, accountId, "MAP_DRAFT_CREATED", id);
        return draftResponse(maps.plan(eventId, id));
    }

    @Transactional
    public Map<String, Object> replacePins(UUID eventId, UUID floorPlanId, UUID accountId,
            ReplaceMapPinsRequest request) {
        lockManager(eventId, accountId);
        requireDraft(eventId, floorPlanId, request.revision());
        validatePins(eventId, floorPlanId, request.pins());
        // Replace in one transaction so swapping two booth assignments never violates a transient unique key.
        maps.clearPins(floorPlanId);
        for (MapPinRequest pin : request.pins()) maps.insertPin(eventId, floorPlanId, pin);
        maps.bumpRevision(floorPlanId);
        maps.audit(eventId, accountId, "MAP_PINS_REPLACED", floorPlanId);
        return draftResponse(maps.plan(eventId, floorPlanId));
    }

    @Transactional
    public void delete(UUID eventId, UUID floorPlanId, UUID accountId, long revision) {
        lockManager(eventId, accountId);
        Rules.input(revision >= 0, "Revision must not be negative.");
        requireDraft(eventId, floorPlanId, revision);
        maps.delete(floorPlanId);
        maps.audit(eventId, accountId, "MAP_DRAFT_DELETED", floorPlanId);
    }

    @Transactional
    public Map<String, Object> publish(UUID eventId, UUID floorPlanId, UUID accountId, long revision) {
        lockManager(eventId, accountId);
        requireDraft(eventId, floorPlanId, revision);
        maps.publish(eventId, floorPlanId);
        maps.audit(eventId, accountId, "MAP_PUBLISHED", floorPlanId);
        return draftResponse(maps.plan(eventId, floorPlanId));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Map<String, Object> publicMap(UUID eventId) {
        access.requirePublic(eventId);
        Map<String, Object> plan = maps.byStatus(eventId, "PUBLISHED");
        if (plan == null) throw new ApiException(404, "MAP_NOT_PUBLISHED", "This event has no published map.");
        UUID id = SqlStore.uuid(plan, "floorPlanId");
        return Rules.result("floorPlanId", id, "versionNo", plan.get("versionNo"),
                "revision", plan.get("revision"), "image", image(plan),
                "pins", maps.publicPins(id).stream().map(this::publicPin).toList());
    }

    private void lockManager(UUID eventId, UUID accountId) {
        access.lockEvent(eventId);
        access.requireManager(eventId, accountId);
    }

    private void requireDraft(UUID eventId, UUID floorPlanId, long revision) {
        Map<String, Object> plan = maps.plan(eventId, floorPlanId);
        Rules.revision(revision, plan);
        Rules.require("DRAFT".equals(plan.get("publicationStatus")), "INVALID_STATE", "Only draft maps may change.");
    }

    private void validatePins(UUID eventId, UUID floorPlanId, List<MapPinRequest> pins) {
        Set<UUID> ids = new HashSet<>();
        Set<UUID> boothIds = new HashSet<>();
        for (MapPinRequest pin : pins) {
            Rules.input(ids.add(pin.id()), "Pin IDs must be unique.");
            Rules.input("BOOTH".equals(pin.pinType()) || pin.eventBoothId() == null,
                    "Facility pins cannot have a booth assignment.");
            if (pin.eventBoothId() != null) {
                Rules.input(boothIds.add(pin.eventBoothId()), "A booth may be assigned to only one pin per map.");
            }
        }
        for (Map<String, Object> pin : maps.existingPins(ids)) {
            if (!eventId.equals(SqlStore.uuid(pin, "eventId"))
                    || !floorPlanId.equals(SqlStore.uuid(pin, "floorPlanId"))) throw ApiException.notFound();
        }
        if (maps.matchingBooths(eventId, boothIds) != boothIds.size()) throw ApiException.notFound();
    }

    private Map<String, Object> summary(Map<String, Object> plan) {
        if (plan == null) return null;
        return Rules.result("floorPlanId", plan.get("floorPlanId"), "versionNo", plan.get("versionNo"),
                "publicationStatus", plan.get("publicationStatus"), "revision", plan.get("revision"),
                "image", image(plan), "createdAt", plan.get("createdAt"), "updatedAt", plan.get("updatedAt"));
    }

    private Map<String, Object> draftResponse(Map<String, Object> plan) {
        Map<String, Object> response = summary(plan);
        if (response != null) response.put("pins", maps.pins(SqlStore.uuid(plan, "floorPlanId")));
        return response;
    }

    private Map<String, Object> image(Map<String, Object> plan) {
        return media.description(media.requireAsset(SqlStore.uuid(plan, "eventId"), SqlStore.uuid(plan, "mediaAssetId")));
    }

    private Map<String, Object> publicPin(Map<String, Object> pin) {
        Map<String, Object> result = Rules.result("id", pin.get("id"), "pinType", pin.get("pinType"),
                "label", pin.get("label"), "xRatio", pin.get("xRatio"), "yRatio", pin.get("yRatio"));
        if ("BOOTH".equals(pin.get("pinType"))) {
            result.putAll(Rules.result("eventBoothId", pin.get("eventBoothId"), "boothCode", pin.get("boothCode"),
                    "boothName", pin.get("boothName"), "operationStatus", pin.get("operationStatus")));
        }
        return result;
    }
}
