package kr.boothrock.api.map.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record PublishMapRequest(
        @NotNull @PositiveOrZero @Schema(example = "3") Long revision) {
}
