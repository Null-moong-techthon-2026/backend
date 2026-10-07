package kr.boothrock.api.booth.dto;

import java.util.UUID;

public class BoothQuery {
    private int page = 0;
    private Integer size;
    private String q;
    private String categoryCode;
    private String operationStatus;
    private String stockSummary;
    private String placementStatus;
    private String source;
    private String visibilityStatus;
    private String sort = "boothCode,asc";
    private UUID floorPlanId;

    public BoothFilter filter() {
        return filter(20);
    }

    public BoothFilter filter(int defaultSize) {
        return new BoothFilter(page, size == null ? defaultSize : size, q, categoryCode, operationStatus, stockSummary,
                placementStatus, source, visibilityStatus, sort, floorPlanId);
    }

    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }
    public Integer getSize() { return size; }
    public void setSize(Integer size) { this.size = size; }
    public String getQ() { return q; }
    public void setQ(String q) { this.q = q; }
    public String getCategoryCode() { return categoryCode; }
    public void setCategoryCode(String categoryCode) { this.categoryCode = categoryCode; }
    public String getOperationStatus() { return operationStatus; }
    public void setOperationStatus(String operationStatus) { this.operationStatus = operationStatus; }
    public String getStockSummary() { return stockSummary; }
    public void setStockSummary(String stockSummary) { this.stockSummary = stockSummary; }
    public String getPlacementStatus() { return placementStatus; }
    public void setPlacementStatus(String placementStatus) { this.placementStatus = placementStatus; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getVisibilityStatus() { return visibilityStatus; }
    public void setVisibilityStatus(String visibilityStatus) { this.visibilityStatus = visibilityStatus; }
    public String getSort() { return sort; }
    public void setSort(String sort) { this.sort = sort; }
    public UUID getFloorPlanId() { return floorPlanId; }
    public void setFloorPlanId(UUID floorPlanId) { this.floorPlanId = floorPlanId; }
}
