package kr.boothrock.api.staff.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Explicit OpenAPI shapes for map-based staff responses. */
public final class StaffResponses {
    private StaffResponses() {}
    @Schema(name="StaffDutyResponse")
    public record Duty(UUID id,String zone,String name,long revision) {}
    @Schema(name="StaffDutySummary")
    public record DutySummary(UUID id,String zone,String name) {}
    @Schema(name="StaffShiftMember")
    public record Member(UUID memberId,String name,String role,String phone,String status) {}
    @Schema(name="StaffShiftResponse")
    public record Shift(UUID id,String date,UUID dutyId,String start,String end,List<String> roles,
            long revision,String zone,DutySummary duty,List<Member> members) {}
    @Schema(name="StaffAttendanceEntry")
    public record AttendanceEntry(UUID attendanceId,String date,Instant in,Instant out,Long workedMinutes) {}
    @Schema(name="StaffAttendanceState")
    public record AttendanceState(UUID attendanceId,String date,Instant in,Instant out) {}
    @Schema(name="StaffAttendancePage")
    public record AttendancePage(List<AttendanceEntry> content,int page,int size,long totalElements,long totalPages,
            long totalMinutes,String today,AttendanceState todayAttendance,List<AttendanceState> todayAttendances,AttendanceState openAttendance,
            boolean canClockIn,boolean canClockOut) {}
    @Schema(name="OrganizerStaffAttendanceEntry")
    public record OrganizerAttendanceEntry(UUID attendanceId,UUID memberId,String name,String role,String date,
            Instant in,Instant out,long workedMinutes) {}
    @Schema(name="OrganizerStaffAttendancePage")
    public record OrganizerAttendancePage(List<OrganizerAttendanceEntry> content,int page,int size,
            long totalElements,long totalPages) {}

}
