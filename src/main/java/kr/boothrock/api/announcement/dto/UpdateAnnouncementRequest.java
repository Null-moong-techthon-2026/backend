package kr.boothrock.api.announcement.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

@Schema(description = "현재 revision을 사용해 공지 정보를 전체 교체합니다.")
public record UpdateAnnouncementRequest(
        @NotNull @PositiveOrZero
        @Schema(description = "직전 조회에서 받은 수정 버전입니다. 동시 수정 충돌 확인에 사용합니다.", example = "2")
        Long revision,
        @Size(max = 200)
        @Schema(description = "일반 텍스트 제목입니다. 게시할 때는 필수입니다.", example = "기상 악화에 따른 운영 안내")
        String title,
        @Size(max = 10000)
        @Schema(description = "일반 텍스트 본문입니다. 게시할 때는 필수입니다.", example = "야외 부스는 오후 6시에 운영을 종료합니다.")
        String body,
        @Schema(description = "같은 행사에 업로드한 이미지 ID입니다. null을 보내면 첨부 이미지를 제거합니다.", example = "7b1b8c9e-2e60-4cf8-a125-d9ac87a12919")
        UUID imageAssetId,
        @Size(max = 3)
        @Schema(description = "변경할 공지 대상 목록입니다. STAFF: 스태프, OPERATORS: 부스 운영자, PUBLIC: 일반 방문객. 임시 저장할 때는 비워둘 수 있습니다.", example = "[\"STAFF\",\"OPERATORS\"]")
        List<@NotNull @Pattern(regexp = "STAFF|OPERATORS|PUBLIC") String> audiences,
        @Schema(description = "긴급 공지 여부입니다.", example = "true", defaultValue = "false")
        Boolean isUrgent,
        @NotBlank @Pattern(regexp = "DRAFT|PUBLISHED")
        @Schema(description = "게시된 공지를 저장하면 변경 내용이 즉시 반영됩니다. DRAFT: 임시 저장, PUBLISHED: 게시", example = "PUBLISHED", allowableValues = {"DRAFT", "PUBLISHED"})
        String publicationStatus,
        @Size(max=5)
        @Schema(description="같은 행사에 업로드한 첨부 ID, 최대5개. 생략 시 수정에서 기존 목록 유지, []로 연결 해제.")
        List<@NotNull UUID> attachmentIds
) {}
