package kr.boothrock.api.booth.dto;

import java.util.UUID;
import kr.boothrock.api.common.service.Rules;

public record BoothFilter(int page, int size, String q, String categoryCode,
        String operationStatus, String stockSummary, String placementStatus,
        String source, String visibilityStatus, String sort, UUID floorPlanId) {
    public void validate() {
        Rules.page(page, size);
        Rules.like(q);
        if (categoryCode != null) Rules.category(categoryCode);
        if (operationStatus != null) Rules.enumValue(operationStatus, "PREPARING", "OPEN", "SOLD_OUT", "CLOSED");
        if (stockSummary != null) Rules.enumValue(stockSummary, "NOT_TRACKED", "SOLD_OUT", "UNLIMITED", "LOW", "AVAILABLE");
        if (placementStatus != null) Rules.enumValue(placementStatus, "ASSIGNED", "UNASSIGNED");
        if (source != null) Rules.enumValue(source, "APPLICATION", "MANUAL");
        if (visibilityStatus != null) Rules.enumValue(visibilityStatus, "HIDDEN", "PUBLIC", "ARCHIVED");
        Rules.enumValue(sort, "boothCode,asc", "boothCode,desc");
    }
}
