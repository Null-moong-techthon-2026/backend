package kr.boothrock.api.booth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import java.util.List;
import tools.jackson.databind.JsonNode;

public final class BoothRequests {
    private BoothRequests() {}

    public record CreateProfile(
            @NotBlank @Size(max = 100) @Schema(example = "달빛 떡볶이") String name,
            @NotBlank @Schema(example = "FOOD") String categoryCode,
            @Size(max = 10000) @Schema(example = "따뜻한 떡볶이와 간식을 판매하는 부스입니다.") String introduction) {}

    public record PatchProfile(
            @NotNull @PositiveOrZero @Schema(example = "0") Long revision,
            @Size(max = 100) @Pattern(regexp = "(?s).*\\S.*") @Schema(example = "달빛 떡볶이") String name,
            @Schema(example = "FOOD") String categoryCode,
            @Size(max = 10000) @Schema(example = "따뜻한 떡볶이와 간식을 판매하는 부스입니다.") String introduction) {}

    @Schema(name="BoothApplicationApply")
    public record Apply(
            @NotNull @Schema(example = "ed4a94ad-ad61-4896-b565-c4089a7aa385") UUID boothProfileId,
            @NotBlank @Size(max = 100) @Schema(example = "김민서") String applicantName,
            @NotBlank @Pattern(regexp = "^[+]?[0-9]{8,15}$") @Schema(example = "01012345678") String applicantPhone,
            @NotBlank @Email @Size(max = 254) @Schema(example = "contact@example.com") String applicantEmail,
            @Size(min = 1, max = 512) @Schema(description = "초대가 필요한 행사에서 발급받은 초대 코드 원문입니다.", example = "invitation-code") String invitationCode,
            @Size(max=200) String affiliation,
            @Size(max=100) String bankName, @Size(max=100) String accountNumber, @Size(max=100) String accountHolder,
            @Size(max=5000) String saleItems, @Size(max=5000) String requiredFacilities,
            @Size(max=30) List<@NotNull @Valid AdditionalAnswer> additionalAnswers) {}
    public record AdditionalAnswer(@NotNull UUID questionId, @NotNull @Size(max=5000) String answer) {}
    public record Cancellation(@NotNull @PositiveOrZero Long revision, @Size(max=2000) String cancelReason) {}

    public record ReviewNote(
            @NotNull @PositiveOrZero @Schema(example = "0") Long revision,
            @NotNull @Size(max = 2000) @Schema(example = "제출 서류와 운영 계획 검토를 완료했습니다.") String reviewNote) {}

    public record DocumentCheck(
            @NotNull @PositiveOrZero @Schema(example = "0") Long revision,
            @NotBlank @Schema(example = "VERIFIED") String checkStatus,
            @Size(max = 2000) @Schema(example = "이메일로 제출된 서류를 확인했습니다.") String reviewNote) {}

    @Schema(name="BoothApplicationDecision")
    public record Decision(
            @NotNull @PositiveOrZero @Schema(example = "0") Long revision,
            @NotBlank @Schema(example = "APPROVED") String decision,
            @Size(max = 20) @Pattern(regexp = "\\S(?:.*\\S)?") @Schema(example = "B001") String boothCode,
            @Size(max = 2000) @Schema(example = "안전 관리 내용을 보완해 주세요.") String rejectionReason,
            @Size(max = 2000) @Schema(example = "운영 계획과 필수 서류 확인을 완료했습니다.") String reviewNote) {}

    public record CreateBooth(
            @NotBlank @Size(max = 100) @Schema(example = "축제 안내 부스") String name,
            @NotBlank @Schema(example = "OTHER") String categoryCode,
            @Size(max = 10000) @Schema(example = "행사장 위치와 프로그램을 안내합니다.") String description,
            @Size(max = 20) @Pattern(regexp = "\\S(?:.*\\S)?") @Schema(example = "B001") String boothCode) {}

    public record PatchBooth(
            @NotNull @PositiveOrZero @Schema(example = "0") Long revision,
            @Size(max = 100) @Pattern(regexp = "(?s).*\\S.*") @Schema(example = "달빛 떡볶이") String name,
            @Schema(example = "FOOD") String categoryCode,
            @Size(max = 10000) @Schema(example = "떡볶이와 어묵을 판매합니다.") String description,
            @Schema(description = "행사 관리자만 변경할 수 있는 공개 상태입니다.", example = "PUBLIC") String visibilityStatus) {}

    public record ReplacePhotos(
            @NotNull @PositiveOrZero Long revision,
            @NotNull @Size(max = 10) @Schema(description = "최대 10장. 첫 사진이 대표 사진이며 빈 배열로 모두 해제합니다.")
            List<@NotNull UUID> mediaAssetIds) {}

    public record OperationStatus(
            @NotNull @PositiveOrZero @Schema(example = "0") Long revision,
            @NotBlank @Schema(example = "OPEN") String operationStatus) {}

    public record CreateItem(
            @NotBlank @Size(max = 100) @Schema(example = "컵 떡볶이") String name,
            @Size(max = 10000) @Schema(example = "매콤한 떡볶이 1인분입니다.") String description,
            @PositiveOrZero @Schema(description = "원 단위 가격입니다. null은 가격 미정, 0은 무료입니다.", example = "4000") Long priceKrw,
            @Schema(example = "AVAILABLE") String stockStatus,
            @PositiveOrZero @Schema(example = "0") Integer sortOrder) {}

    public record PatchItem(
            @NotNull @PositiveOrZero @Schema(example = "0") Long revision,
            @Size(max = 100) @Pattern(regexp = "(?s).*\\S.*") @Schema(example = "컵 떡볶이") String name,
            @Size(max = 10000) @Schema(example = "매콤한 떡볶이 1인분입니다.") String description,
            @Schema(type = "integer", description = "원 단위 가격입니다. 생략하면 기존 가격 유지, null은 가격 미정, 0은 무료로 변경합니다.", example = "4000") JsonNode priceKrw,
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
