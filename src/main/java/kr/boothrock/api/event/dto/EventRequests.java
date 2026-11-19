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
    public record LocalOrganization(@NotBlank @Size(max=100) @Schema(example="비룡제 준비위원회") String name,
            @Email @Size(max=254) @Schema(example="organizer@example.com") String contactEmail) {}
    public record Create(@NotBlank @Size(max=100) @Schema(example="비룡제 2026") String name,
            @Size(max=200) @Schema(example="인하대학교 대운동장") String venue,
            @Schema(example="2026-11-10T00:00:00Z") Instant startsAt,
            @Schema(example="2026-11-11T12:00:00Z") Instant endsAt,
            @Size(max=10000) @Schema(example="먹거리·체험·학생 부스가 함께하는 교내 축제입니다.") String description) {}
    public record Update(@NotNull @PositiveOrZero @Schema(example="0") Long revision,
            @Size(min=1,max=100) String name, @Size(max=200) String venue,
            Instant startsAt, Instant endsAt, @Size(max=10000) String description,
            @Schema(type="string", format="uuid", nullable=true, description="생략하면 유지, null이면 포스터 연결 해제") tools.jackson.databind.JsonNode posterAssetId,
            @Schema(allowableValues={"DRAFT","PUBLISHED","ARCHIVED"}) String publicationStatus) {}
    public record Document(UUID id, @NotBlank @Size(max=100) @Schema(example="운영 계획서") String name,
            @NotNull Boolean isRequired, @NotNull @Size(max=6) List<@NotBlank String> applicableCategoryCodes,
            @NotNull @Min(0) Integer sortOrder) {}
    public record Question(UUID id, @NotBlank @Size(max=1000) String question,
            @NotNull Boolean isRequired, @NotNull @Min(0) Integer sortOrder) {}
    public record Recruitment(@NotNull @PositiveOrZero Long revision,
            @NotNull @Size(max=10000) String introduction,
            @NotBlank @Schema(allowableValues={"DRAFT","PUBLISHED"}) String publicationStatus,
            Instant closesAt, @Positive Integer targetBoothCount,
            @NotNull @Size(max=6) List<@NotBlank String> allowedCategoryCodes,
            @PositiveOrZero Long participationFeeKrw, @NotNull @Size(max=2000) String feeNote,
            @NotNull @Size(max=30) List<@NotNull @Valid Document> documentRequirements,
            @Size(max=30) @jakarta.validation.constraints.Pattern(regexp="[+0-9() -]*") String contactPhone,
            @Size(max=30) List<@NotNull @Valid Question> additionalQuestions) {}
    public record Invitation(@NotNull Instant expiresAt) {}
    @Schema(name="BoothInvitationResolve")
    public record Resolve(@NotBlank @Size(min=20,max=100) String code) {
        @Override public String toString() { return "Resolve[code=REDACTED]"; }
    }
}
