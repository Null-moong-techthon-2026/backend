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
        @Schema(description = "Complete pin set. Omitted pins are removed; an empty array clears the map.")
        List<@NotNull @Valid MapPinRequest> pins) {
}
