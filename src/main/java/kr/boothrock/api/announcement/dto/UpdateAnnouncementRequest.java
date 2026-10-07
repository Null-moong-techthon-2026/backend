package kr.boothrock.api.announcement.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

@Schema(description = "Replace all announcement fields using the current revision.")
public record UpdateAnnouncementRequest(
        @NotNull @PositiveOrZero
        @Schema(description = "Current revision returned by the server", example = "2")
        Long revision,
        @Size(max = 200)
        @Schema(description = "Plain-text title; required when publishing", example = "Weather advisory")
        String title,
        @Size(max = 10000)
        @Schema(description = "Plain-text body; required when publishing", example = "Outdoor booths close at 18:00.")
        String body,
        @Schema(description = "Same-event image ID, or null to remove the image", example = "7b1b8c9e-2e60-4cf8-a125-d9ac87a12919")
        UUID imageAssetId,
        @Size(max = 3)
        @Schema(description = "Replacement audience union; may be empty for a draft", example = "[\"STAFF\",\"OPERATORS\"]")
        List<@NotNull @Pattern(regexp = "STAFF|OPERATORS|PUBLIC") String> audiences,
        @Schema(description = "Whether the announcement is urgent", example = "true", defaultValue = "false")
        Boolean isUrgent,
        @NotBlank @Pattern(regexp = "DRAFT|PUBLISHED")
        @Schema(description = "Saving a published announcement takes effect immediately", example = "PUBLISHED", allowableValues = {"DRAFT", "PUBLISHED"})
        String publicationStatus
) {}
