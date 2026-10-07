package kr.boothrock.api.event.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class EventRequests {
    private EventRequests() {}
    public record LocalOrganization(@NotBlank @Size(max=100) @Schema(example="Local festival team") String name,
            @Email @Size(max=254) @Schema(example="organizer@example.com") String contactEmail) {}
    public record Create(@NotBlank @Size(max=100) @Schema(example="Campus Festival 2026") String name,
            @Size(max=200) @Schema(example="University square") String venue,
            @Schema(example="2026-11-10T00:00:00Z") Instant startsAt,
            @Schema(example="2026-11-11T12:00:00Z") Instant endsAt,
            @Size(max=10000) @Schema(example="Food, experiences and student booths.") String description) {}
    public record Update(@NotNull @PositiveOrZero @Schema(example="0") Long revision,
            @Size(min=1,max=100) String name, @Size(max=200) String venue,
            Instant startsAt, Instant endsAt, @Size(max=10000) String description, UUID posterAssetId,
            @Schema(allowableValues={"DRAFT","PUBLISHED","ARCHIVED"}) String publicationStatus) {}
    public record Document(UUID id, @NotBlank @Size(max=100) @Schema(example="Operation plan") String name,
            @NotNull Boolean isRequired, @NotNull @Size(max=6) List<@NotBlank String> applicableCategoryCodes,
            @NotNull @Min(0) Integer sortOrder) {}
    public record Recruitment(@NotNull @PositiveOrZero Long revision,
            @NotNull @Size(max=10000) String introduction,
            @NotBlank @Schema(allowableValues={"DRAFT","PUBLISHED"}) String publicationStatus,
            Instant closesAt, @Positive Integer targetBoothCount,
            @NotNull @Size(max=6) List<@NotBlank String> allowedCategoryCodes,
            @PositiveOrZero Long participationFeeKrw, @NotNull @Size(max=2000) String feeNote,
            @NotNull @Size(max=30) List<@NotNull @Valid Document> documentRequirements) {}
    public record Invitation(@NotNull Instant expiresAt) {}
    public record Resolve(@NotBlank @Size(min=20,max=100) String code) {
        @Override public String toString() { return "Resolve[code=REDACTED]"; }
    }
}
