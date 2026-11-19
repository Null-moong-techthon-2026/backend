package kr.boothrock.api.staff.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

public final class StaffRequests {
    private StaffRequests() {}
    @Schema(name="StaffInvitationResolve")
    public record Resolve(@NotBlank @Size(max=100) String code) {
        @Override public String toString() { return "Resolve[REDACTED]"; }
    }
    @Schema(name="StaffApplicationApply")
    public record Apply(@NotBlank @Size(max=100) String code, @NotBlank String role, @Size(max=200) String affiliation) {
        @Override public String toString() { return "Apply[REDACTED]"; }
    }
    public record PatchMember(@NotNull @PositiveOrZero Long revision, String role, String status,
            JsonNode dutyId, @Size(max=2000) String memo) {}
    @Schema(name="StaffApplicationDecision")
    public record Decision(@NotBlank String decision, @PositiveOrZero Long revision) {}
    public record Duty(@NotBlank @Size(max=100) String zone,@NotBlank @Size(max=100) String name) {}
    public record UpdateDuty(@NotNull @PositiveOrZero Long revision,
            @NotBlank @Size(max=100) String zone,@NotBlank @Size(max=100) String name) {}
    public record UpdateShift(@NotNull @PositiveOrZero Long revision,
            @NotNull LocalDate date,@NotNull UUID dutyId,@NotNull LocalTime start,@NotNull LocalTime end,
            @NotNull @Size(min=1,max=4) List<@NotBlank String> roles) {}
    public record Shift(@NotNull LocalDate date,@NotNull UUID dutyId,@NotNull LocalTime start,@NotNull LocalTime end,
            @NotNull @Size(min=1,max=4) List<@NotBlank String> roles) {}
    public record Members(@NotNull @Size(max=100) List<@NotNull UUID> memberIds,@PositiveOrZero Long revision) {}
}
