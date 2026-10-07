package kr.boothrock.api.announcement.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

@Schema(description = "Announcement fields. Drafts may have empty text and audiences.")
public record CreateAnnouncementRequest(
        @Size(max = 200)
        @Schema(description = "Plain-text title; required when publishing", example = "Weather advisory")
        String title,
        @Size(max = 10000)
        @Schema(description = "Plain-text body; required when publishing", example = "Outdoor booths close at 18:00.")
        String body,
        @Schema(description = "An uploaded image belonging to this event", example = "7b1b8c9e-2e60-4cf8-a125-d9ac87a12919")
        UUID imageAssetId,
        @Size(max = 3)
        @Schema(description = "Union of reader audiences; at least one when publishing", example = "[\"STAFF\",\"OPERATORS\",\"PUBLIC\"]")
        List<@NotNull @Pattern(regexp = "STAFF|OPERATORS|PUBLIC") String> audiences,
        @Schema(description = "Place this announcement before ordinary announcements", example = "true", defaultValue = "false")
        Boolean isUrgent,
        @NotBlank @Pattern(regexp = "DRAFT|PUBLISHED")
        @Schema(description = "Save a draft or publish immediately", example = "DRAFT", allowableValues = {"DRAFT", "PUBLISHED"})
        String publicationStatus
) {}
