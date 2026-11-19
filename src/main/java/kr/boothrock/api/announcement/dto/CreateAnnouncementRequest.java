package kr.boothrock.api.announcement.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

@Schema(description = "공지 생성 정보입니다. 임시 저장할 때는 제목·본문·공지 대상을 비워둘 수 있습니다.")
public record CreateAnnouncementRequest(
        @Size(max = 200)
        @Schema(description = "일반 텍스트 제목입니다. 게시할 때는 필수입니다.", example = "기상 악화에 따른 운영 안내")
        String title,
        @Size(max = 10000)
        @Schema(description = "일반 텍스트 본문입니다. 게시할 때는 필수입니다.", example = "야외 부스는 오후 6시에 운영을 종료합니다.")
        String body,
        @Schema(description = "같은 행사에 업로드한 이미지의 ID입니다.", example = "7b1b8c9e-2e60-4cf8-a125-d9ac87a12919")
        UUID imageAssetId,
        @Size(max = 3)
        @Schema(description = "공지 대상 목록입니다. STAFF: 스태프, OPERATORS: 부스 운영자, PUBLIC: 일반 방문객. 게시할 때는 하나 이상 선택합니다.", example = "[\"STAFF\",\"OPERATORS\",\"PUBLIC\"]")
        List<@NotNull @Pattern(regexp = "STAFF|OPERATORS|PUBLIC") String> audiences,
        @Schema(description = "긴급 공지 여부입니다. 일반 공지보다 먼저 표시합니다.", example = "true", defaultValue = "false")
        Boolean isUrgent,
        @NotBlank @Pattern(regexp = "DRAFT|PUBLISHED")
        @Schema(description = "DRAFT: 임시 저장, PUBLISHED: 즉시 게시", example = "DRAFT", allowableValues = {"DRAFT", "PUBLISHED"})
        String publicationStatus,
        @Size(max=5)
        @Schema(description="같은 행사에 업로드한 첨부 ID, 최대5개. 생략 시 수정에서 기존 목록 유지, []로 연결 해제.")
        List<@NotNull UUID> attachmentIds
) {}
