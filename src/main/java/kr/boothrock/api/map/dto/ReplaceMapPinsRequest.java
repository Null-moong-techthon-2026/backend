package kr.boothrock.api.map.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ReplaceMapPinsRequest(
        @NotNull @PositiveOrZero @Schema(example = "3") Long revision,
        @NotNull @Size(max = 500)
        @Schema(description = "저장할 핀 전체 목록입니다. 요청에 없는 기존 핀은 삭제되며 빈 배열을 보내면 모든 핀이 삭제됩니다.")
        List<@NotNull @Valid MapPinRequest> pins) {
}
