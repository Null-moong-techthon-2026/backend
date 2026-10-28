package kr.boothrock.api.eventapplication.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

public final class EventApplicationRequests {
    private EventApplicationRequests() {}

    /** The whole application form. Conditional rules (business number, affiliation) are checked in the service. */
    public record Create(
            @NotBlank @Size(max = 100) @Schema(example = "Inha University Student Council") String orgName,
            @NotBlank @Pattern(regexp = "COMPANY|STUDENT|PUBLIC|NONPROFIT|OTHER") @Schema(example = "STUDENT") String orgType,
            @NotBlank @Email @Size(max = 254) String orgEmail,
            @Size(max = 20) @Schema(description = "Digits only; required for COMPANY", example = "1234567890") String businessNumber,
            @Size(max = 100) @Schema(description = "Required for STUDENT and PUBLIC") String affiliation,
            @Size(max = 100) String representative,
            @Size(max = 300) String website,
            @NotBlank @Size(max = 100) String contactName,
            @NotBlank @Email @Size(max = 254) String contactEmail,
            @NotBlank @Pattern(regexp = "^[+]?[0-9]{8,15}$") @Schema(example = "01012345678") String contactPhone,
            @NotBlank @Size(max = 100) String contactRole,
            @NotBlank @Size(max = 100) String eventName,
            @NotBlank @Pattern(regexp = "UNIVERSITY|FLEA|POPUP|EXPO|LOCAL|OTHER") String eventKind,
            @NotNull @Schema(example = "2026-12-01") LocalDate startDate,
            @NotNull @Schema(example = "2026-12-03") LocalDate endDate,
            @NotBlank @Size(max = 200) String venue,
            @NotNull @Min(1) @Max(100000) Integer boothCount,
            @NotNull Boolean handover,
            @NotBlank @Pattern(regexp = "PREPAID|BASE_PLUS_SLOT") String paymentPlan,
            @NotBlank @Pattern(regexp = "CARD|TRANSFER|OTHER") String paymentMethod,
            @NotBlank @Pattern(regexp = "TAX_INVOICE|CASH_RECEIPT|NONE") String receiptType,
            @NotNull @Size(max = 10) @Schema(description = "Every agreement must be present: terms, privacy, outsourcing, billing, refund",
                    example = "[\"terms\",\"privacy\",\"outsourcing\",\"billing\",\"refund\"]") List<@NotBlank String> agreements) {}

    public record Decision(
            @NotNull @PositiveOrZero @Schema(example = "0") Long revision,
            @NotBlank @Pattern(regexp = "APPROVED|REJECTED") String decision,
            @Size(max = 500) @Schema(description = "Required when rejecting; shown to the applicant") String rejectionReason) {}
}
