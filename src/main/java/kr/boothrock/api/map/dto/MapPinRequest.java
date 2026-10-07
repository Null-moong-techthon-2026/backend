package kr.boothrock.api.map.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public record MapPinRequest(
        @NotNull @Schema(example = "89efc9a8-46d2-47fa-afca-cd40fc934b04") UUID id,
        @NotNull @Pattern(regexp = "BOOTH|TOILET|INFO|MEDICAL|OTHER_FACILITY")
        @Schema(example = "BOOTH") String pinType,
        @NotBlank @Size(max = 100) @Schema(example = "B001") String label,
        @NotNull @DecimalMin("0") @DecimalMax("1") @Schema(example = "0.25") BigDecimal xRatio,
        @NotNull @DecimalMin("0") @DecimalMax("1") @Schema(example = "0.4") BigDecimal yRatio,
        @Schema(example = "c98a019f-d73e-43d4-a2d6-1c03d1a0f00e") UUID eventBoothId) {
}
