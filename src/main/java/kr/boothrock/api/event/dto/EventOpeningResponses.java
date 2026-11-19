package kr.boothrock.api.event.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Explicit response schemas for the existing map-based JSON contracts. */
public final class EventOpeningResponses {
    private EventOpeningResponses() {}
    @Schema(name="EventOpeningCreated")
    public record Created(@Schema(description="행사 ID가 아닌 개설 신청 ID") UUID id,
            @Schema(allowableValues={"PENDING"}) String status,Instant createdAt,long revision) {}
    @Schema(name="EventOpeningApplicant")
    public record Applicant(UUID id,String nickname,String email,String phoneNumber) {}
    @Schema(name="EventOpeningApplication")
    public static class Application {
        public UUID id;
        public UUID applicantAccountId;
        public String orgName;
        @Schema(allowableValues={"COMPANY","STUDENT","PUBLIC","NONPROFIT","OTHER"}) public String orgType;
        public String eventName;
        public Instant startsAt;
        public Instant endsAt;
        public String venue;
        @Schema(allowableValues={"PENDING","APPROVED","REJECTED"}) public String status;
        public Instant createdAt;
        @Schema(nullable=true) public Instant reviewedAt;
        @Schema(nullable=true) public UUID reviewedBy;
        @Schema(nullable=true) public String rejectionReason;
        @Schema(nullable=true,description="승인 후 생성된 행사 ID, 승인 전 null") public UUID eventId;
        @Schema(nullable=true,description="승인 후 생성된 조직 ID, 승인 전 null") public UUID organizationId;
        public long revision;
    }
    @Schema(name="AdminEventOpeningApplication")
    public static class AdminApplication extends Application {
        public Applicant applicant;
    }
    @Schema(name="MyEventOpeningPage")
    public record MyPage(List<Application> content,int page,int size,long totalElements,long totalPages) {}
    @Schema(name="AdminEventOpeningPage")
    public record AdminPage(List<AdminApplication> content,int page,int size,long totalElements,long totalPages) {}
}
