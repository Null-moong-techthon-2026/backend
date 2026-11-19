package kr.boothrock.api.booth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Response documentation mirrors the actual application detail fields; bank fields are private. */
public final class ApplicationResponses {
    private ApplicationResponses() {}
    public record Booth(String name,String categoryCode,String introduction) {}
    public record Applicant(String name,String phoneNumber,String email) {}
    public record Answer(UUID questionId,String question,boolean isRequired,String answer) {}
    @Schema(name="BoothApplicationDocument")
    public record Document(UUID requirementId,String name,boolean isRequired,
            @Schema(allowableValues={"NOT_SUBMITTED","SUBMITTED","VERIFIED","NEEDS_CORRECTION"}) String checkStatus,
            @Schema(description="파일 업로드/교체 및 document-checks에 보내는 해당 서류 확인 항목의 수정 버전. 신청 revision과 별개입니다.") long revision,
            UUID documentId,String fileName,Instant uploadedAt,String mimeType,Long sizeBytes,
            String reviewNote,Instant checkedAt) {}
    @Schema(name="BoothApplicationDetail")
    public record Detail(UUID applicationId,UUID eventId,long revision,Instant submittedAt,
            @Schema(allowableValues={"PENDING","APPROVED","REJECTED","CANCELLED"}) String reviewStatus,
            Booth booth,Applicant applicant,List<Document> documents,String rejectionReason,Instant reviewedAt,
            UUID eventBoothId,String boothCode,Instant cancelledAt,UUID cancelledBy,String cancelReason,
            String affiliation,String bankName,String accountNumber,String accountHolder,String saleItems,
            String requiredFacilities,List<Answer> additionalAnswers,
            @Schema(description="주최자 조회에만 포함됩니다.") String reviewNote) {}
}
