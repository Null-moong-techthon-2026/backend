package kr.boothrock.api.map.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import java.util.UUID;

@Schema(description = "같은 행사의 업로드 이미지와 기존 공개 지도 중 하나만 선택합니다.")
public record CreateFloorPlanRequest(
        @Schema(example = "dca40bdb-5782-4c70-8fab-68658bcb5dbb") UUID mediaAssetId,
        @Schema(example = "ac45097e-f281-44b9-af2b-a2726c9e8901") UUID sourceFloorPlanId) {
    @AssertTrue(message = "Provide exactly one mediaAssetId or sourceFloorPlanId.")
    @Schema(hidden = true)
    public boolean isSourceSelected() {
        return (mediaAssetId == null) != (sourceFloorPlanId == null);
    }
}
