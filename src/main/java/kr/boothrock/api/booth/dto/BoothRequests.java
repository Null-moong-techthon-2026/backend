package kr.boothrock.api.booth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

public final class BoothRequests {
    private BoothRequests() {}

    public record CreateProfile(
            @NotBlank @Size(max = 100) @Schema(example = "Moonlight Snacks") String name,
            @NotBlank @Schema(example = "FOOD") String categoryCode,
            @Size(max = 10000) @Schema(example = "Fresh festival snacks") String introduction) {}

    public record PatchProfile(
            @NotNull @PositiveOrZero @Schema(example = "0") Long revision,
            @Size(max = 100) @Pattern(regexp = "(?s).*\\S.*") @Schema(example = "Moonlight Snacks") String name,
            @Schema(example = "FOOD") String categoryCode,
            @Size(max = 10000) @Schema(example = "Fresh festival snacks") String introduction) {}

    public record Apply(
            @NotNull @Schema(example = "ed4a94ad-ad61-4896-b565-c4089a7aa385") UUID boothProfileId,
            @NotBlank @Size(max = 100) @Schema(example = "Festival Contact") String applicantName,
            @NotBlank @Pattern(regexp = "^[+]?[0-9]{8,15}$") @Schema(example = "01012345678") String applicantPhone,
            @NotBlank @Email @Size(max = 254) @Schema(example = "contact@example.com") String applicantEmail,
            @Size(min = 1, max = 512) @Schema(description = "Raw invitation code when required", example = "invitation-code") String invitationCode) {}

    public record ReviewNote(
            @NotNull @PositiveOrZero @Schema(example = "0") Long revision,
            @NotNull @Size(max = 2000) @Schema(example = "Review complete") String reviewNote) {}

    public record DocumentCheck(
            @NotNull @PositiveOrZero @Schema(example = "0") Long revision,
            @NotBlank @Schema(example = "VERIFIED") String checkStatus,
            @Size(max = 2000) @Schema(example = "External submission checked") String reviewNote) {}

    public record Decision(
            @NotNull @PositiveOrZero @Schema(example = "0") Long revision,
            @NotBlank @Schema(example = "APPROVED") String decision,
            @Size(max = 20) @Pattern(regexp = "\\S(?:.*\\S)?") @Schema(example = "B001") String boothCode,
            @Size(max = 2000) @Schema(example = "Please revise the operating plan") String rejectionReason,
            @Size(max = 2000) @Schema(example = "Ready for operation") String reviewNote) {}

    public record CreateBooth(
            @NotBlank @Size(max = 100) @Schema(example = "Festival Information") String name,
            @NotBlank @Schema(example = "OTHER") String categoryCode,
            @Size(max = 10000) @Schema(example = "Event information desk") String description,
            @Size(max = 20) @Pattern(regexp = "\\S(?:.*\\S)?") @Schema(example = "B001") String boothCode) {}

    public record PatchBooth(
            @NotNull @PositiveOrZero @Schema(example = "0") Long revision,
            @Size(max = 100) @Pattern(regexp = "(?s).*\\S.*") @Schema(example = "Moonlight Snacks") String name,
            @Schema(example = "FOOD") String categoryCode,
            @Size(max = 10000) @Schema(example = "Fresh snacks") String description,
            @Schema(description = "Manager only", example = "PUBLIC") String visibilityStatus) {}

    public record OperationStatus(
            @NotNull @PositiveOrZero @Schema(example = "0") Long revision,
            @NotBlank @Schema(example = "OPEN") String operationStatus) {}

    public record CreateItem(
            @NotBlank @Size(max = 100) @Schema(example = "Rice Cake Cup") String name,
            @Size(max = 10000) @Schema(example = "One serving") String description,
            @PositiveOrZero @Schema(description = "Null means unset; zero means free", example = "4000") Long priceKrw,
            @Schema(example = "AVAILABLE") String stockStatus,
            @PositiveOrZero @Schema(example = "0") Integer sortOrder) {}

    public record PatchItem(
            @NotNull @PositiveOrZero @Schema(example = "0") Long revision,
            @Size(max = 100) @Pattern(regexp = "(?s).*\\S.*") @Schema(example = "Rice Cake Cup") String name,
            @Size(max = 10000) @Schema(example = "One serving") String description,
            @Schema(type = "integer", description = "Omit to retain; null clears the price; zero is free", example = "4000") JsonNode priceKrw,
            @Schema(example = "LOW") String stockStatus,
            @PositiveOrZero @Schema(example = "1") Integer sortOrder) {
        @AssertTrue(message = "priceKrw must be null or a nonnegative integer")
        @Schema(hidden = true)
        public boolean isPriceValid() {
            return priceKrw == null || priceKrw.isNull()
                    || (priceKrw.isIntegralNumber() && priceKrw.canConvertToLong() && priceKrw.longValue() >= 0);
        }
    }
}
