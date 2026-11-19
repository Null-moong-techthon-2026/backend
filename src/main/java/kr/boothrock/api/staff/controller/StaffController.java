package kr.boothrock.api.staff.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import kr.boothrock.api.staff.dto.StaffResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.boothrock.api.auth.AccountPrincipal;
import kr.boothrock.api.staff.dto.StaffRequests.*;
import kr.boothrock.api.staff.service.StaffService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.http.CacheControl;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("(local | staging) & !deploy")
@RequestMapping("/api")
@Tag(name="스태프 참여·근무")
public class StaffController {
    private final StaffService service;
    public StaffController(StaffService service) { this.service=service; }
    @PostMapping("/events/{eventId}/staff-invitation") @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary="스태프 초대 발급·재발급",description="만료는 발급 시점의 행사 endsAt과 발급 시각+14일 중 빠른 정확한 시각입니다(자정으로 보정하지 않음). 코드와 링크 토큰은 대소문자를 구분하며 기존 초대는 폐기합니다.")
    public Map<String,Object> issue(@PathVariable UUID eventId,@AuthenticationPrincipal AccountPrincipal actor) {return service.issue(eventId,actor.getAccountId());}
    @GetMapping("/events/{eventId}/staff-invitation") @Operation(summary="스태프 초대 코드 재조회")
    public Map<String,Object> invitation(@PathVariable UUID eventId,@AuthenticationPrincipal AccountPrincipal actor) {return service.getInvitation(eventId,actor.getAccountId());}
    @DeleteMapping("/events/{eventId}/staff-invitation") @ResponseStatus(HttpStatus.NO_CONTENT) @Operation(summary="스태프 초대 폐기")
    public void revoke(@PathVariable UUID eventId,@AuthenticationPrincipal AccountPrincipal actor) {service.revoke(eventId,actor.getAccountId());}
    @PostMapping("/staff-invitations/resolve") @Operation(summary="코드 또는 링크 토큰으로 행사 확인",description="발급된 code 또는 링크 token 원문을 그대로 보냅니다. 대소문자를 구분하며 잘못된 값·만료·폐기는 404입니다.")
    public Map<String,Object> resolve(@AuthenticationPrincipal AccountPrincipal actor,@Valid @RequestBody Resolve r) {return service.resolve(actor.getAccountId(),r.code());}
    @GetMapping("/staff-invitations/{code}/poster") @Operation(summary="유효한 초대로 행사 포스터 확인")
    public ResponseEntity<byte[]> poster(@PathVariable String code,@AuthenticationPrincipal AccountPrincipal actor) {
        var image=service.poster(actor.getAccountId(),code);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff")
            .contentType(MediaType.parseMediaType(image.mimeType())).body(image.bytes());
    }
    @PostMapping("/events/{eventId}/staff-applications") @ResponseStatus(HttpStatus.CREATED) @Operation(summary="스태프 참여 신청")
    public Map<String,Object> apply(@PathVariable UUID eventId,@AuthenticationPrincipal AccountPrincipal actor,@Valid @RequestBody Apply r) {return service.apply(eventId,actor.getAccountId(),r);}
    @GetMapping("/me/staff-memberships") @Operation(summary="내 스태프 참여·승인 상태")
    public List<Map<String,Object>> memberships(@AuthenticationPrincipal AccountPrincipal actor) {return service.memberships(actor.getAccountId());}
    @GetMapping("/events/{eventId}/staff") @Operation(summary="스태프 명단과 요약")
    public Map<String,Object> roster(@PathVariable UUID eventId,@AuthenticationPrincipal AccountPrincipal actor,
            @RequestParam(required=false) String role,@RequestParam(required=false) String status,@RequestParam(required=false) String q,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {return service.roster(eventId,actor.getAccountId(),role,status,q,page,size);}
    @PatchMapping("/events/{eventId}/staff/{memberId}") @Operation(summary="스태프 역할·상태·업무·메모 수정",description="revision 필수. WORKING/BREAK는 미퇴근 출근 기록이 있을 때만, WAITING/ABSENT는 미퇴근 기록이 없을 때만 가능합니다. 충돌은 409 ATTENDANCE_CONFLICT이며 상태 변경으로 출퇴근 시각을 만들지 않습니다. 과거 배정은 보존하고 진행 중·미래 배정의 허용 역할만 검사합니다.")
    public Map<String,Object> patch(@PathVariable UUID eventId,@PathVariable UUID memberId,@AuthenticationPrincipal AccountPrincipal actor,@Valid @RequestBody PatchMember r) {return service.patch(eventId,actor.getAccountId(),memberId,r);}
    @PostMapping("/events/{eventId}/staff/{memberId}/decision") @Operation(summary="참여 승인·거절")
    public Map<String,Object> decide(@PathVariable UUID eventId,@PathVariable UUID memberId,@AuthenticationPrincipal AccountPrincipal actor,@Valid @RequestBody Decision r) {return service.decide(eventId,actor.getAccountId(),memberId,r);}
    @GetMapping("/events/{eventId}/staff-duties") @Operation(summary="행사 스태프 업무 목록")
    public List<Map<String,Object>> duties(@PathVariable UUID eventId,@AuthenticationPrincipal AccountPrincipal actor) {return service.duties(eventId,actor.getAccountId());}
    @PostMapping("/events/{eventId}/staff-duties") @ResponseStatus(HttpStatus.CREATED) @Operation(summary="스태프 업무 등록")
    public Map<String,Object> duty(@PathVariable UUID eventId,@AuthenticationPrincipal AccountPrincipal actor,@Valid @RequestBody Duty r) {return service.createDuty(eventId,actor.getAccountId(),r);}
    @GetMapping("/events/{eventId}/staff-shifts") @Operation(summary="날짜별 근무표")
    public List<Map<String,Object>> shifts(@PathVariable UUID eventId,@AuthenticationPrincipal AccountPrincipal actor,@RequestParam LocalDate date) {return service.shifts(eventId,actor.getAccountId(),date);}
    @PostMapping("/events/{eventId}/staff-shifts") @ResponseStatus(HttpStatus.CREATED) @Operation(summary="근무 등록",description="date는 Asia/Seoul 기준 행사 시작일~종료일 안이어야 하며 start<end인 같은 날 근무입니다. 날짜/시간/역할 형식 오류는 400입니다.")
    public Map<String,Object> shift(@PathVariable UUID eventId,@AuthenticationPrincipal AccountPrincipal actor,@Valid @RequestBody Shift r) {return service.createShift(eventId,actor.getAccountId(),r);}
    @PutMapping("/events/{eventId}/staff-shifts/{shiftId}/members") @Operation(summary="근무 배정 인원 전체 교체",description="종료된 근무는 409 SHIFT_ENDED로 변경 불가. 승인된 동일 행사 인원만 배정하며 역할 충돌 ROLE_NOT_ALLOWED, 시간 중복 SHIFT_OVERLAP은 409입니다. revision 전송 권장.")
    public Map<String,Object> assign(@PathVariable UUID eventId,@PathVariable UUID shiftId,@AuthenticationPrincipal AccountPrincipal actor,@Valid @RequestBody Members r) {return service.assign(eventId,actor.getAccountId(),shiftId,r);}
    @PutMapping("/events/{eventId}/staff-duties/{dutyId}") @Operation(summary="스태프 업무 이름·구역 수정",description="revision 필수. 시작 전 근무에만 새 이름을 반영하고 과거 근무의 이름은 보존합니다.")
    @ApiResponse(responseCode="200",content=@Content(schema=@Schema(implementation=StaffResponses.Duty.class)))
    public Map<String,Object> updateDuty(@PathVariable UUID eventId,@PathVariable UUID dutyId,@AuthenticationPrincipal AccountPrincipal actor,@Valid @RequestBody UpdateDuty r) {return service.updateDuty(eventId,actor.getAccountId(),dutyId,r);}
    @DeleteMapping("/events/{eventId}/staff-duties/{dutyId}") @ResponseStatus(HttpStatus.NO_CONTENT) @Operation(summary="사용하지 않는 스태프 업무 삭제",description="revision 필수. 소속 인원 또는 근무 이력이 참조하는 업무는 DUTY_IN_USE로 거부합니다.")
    public void deleteDuty(@PathVariable UUID eventId,@PathVariable UUID dutyId,@AuthenticationPrincipal AccountPrincipal actor,@RequestParam long revision) {service.deleteDuty(eventId,actor.getAccountId(),dutyId,revision);}
    @PutMapping("/events/{eventId}/staff-shifts/{shiftId}") @Operation(summary="시작 전 근무표 수정",description="전체 입력과 revision 필수. 배정 인원을 유지하며 역할·시간 중복을 재검증합니다.")
    @ApiResponse(responseCode="200",content=@Content(schema=@Schema(implementation=StaffResponses.Shift.class)))
    public Map<String,Object> updateShift(@PathVariable UUID eventId,@PathVariable UUID shiftId,@AuthenticationPrincipal AccountPrincipal actor,@Valid @RequestBody UpdateShift r) {return service.updateShift(eventId,actor.getAccountId(),shiftId,r);}
    @DeleteMapping("/events/{eventId}/staff-shifts/{shiftId}") @ResponseStatus(HttpStatus.NO_CONTENT) @Operation(summary="시작 전 근무표 삭제",description="revision 필수. 시작된 근무는 409 SHIFT_ALREADY_STARTED. 시작 전 근무를 삭제하면 연결된 배정도 해제하며 출퇴근 기록은 보존합니다.")
    public void deleteShift(@PathVariable UUID eventId,@PathVariable UUID shiftId,@AuthenticationPrincipal AccountPrincipal actor,@RequestParam long revision) {service.deleteShift(eventId,actor.getAccountId(),shiftId,revision);}
    @GetMapping("/events/{eventId}/staff-attendance")
    @Operation(summary="주최자용 날짜별 스태프 출퇴근표",description="행사 관리자 전용 표준 페이지입니다. date는 한국 기준 출근 날짜이며 memberId 필터는 선택입니다. 같은 날 여러 출퇴근 구간은 별도 행이며 attendanceId로 구분합니다. 실제 기록이 있는 인원만 반환하고 미출근자는 명단과 비교합니다. role/name은 현재 계정·명단 값, 시각은 UTC ISO 8601, workedMinutes는 구간 경과 분(미퇴근은 현재까지)입니다. 지각·급여를 자동 판정하지 않습니다.")
    @ApiResponse(responseCode="200",content=@Content(schema=@Schema(implementation=StaffResponses.OrganizerAttendancePage.class)))
    public Map<String,Object> organizerAttendance(@PathVariable UUID eventId,@AuthenticationPrincipal AccountPrincipal actor,
            @RequestParam LocalDate date,@RequestParam(required=false) UUID memberId,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return service.organizerAttendance(eventId,actor.getAccountId(),date,memberId,page,size);
    }
    @GetMapping("/events/{eventId}/staff-live") @Operation(summary="스태프 실시간 상태")
    public Map<String,Object> live(@PathVariable UUID eventId,@AuthenticationPrincipal AccountPrincipal actor,@RequestParam(required=false) String zone,@RequestParam(required=false) UUID dutyId) {return service.live(eventId,actor.getAccountId(),zone,dutyId);}
    @GetMapping("/events/{eventId}/staff/me/schedule") @Operation(summary="내 일정과 함께 근무하는 사람")
    public List<Map<String,Object>> schedule(@PathVariable UUID eventId,@AuthenticationPrincipal AccountPrincipal actor,@RequestParam LocalDate date) {return service.schedule(eventId,actor.getAccountId(),date);}
    @PostMapping("/events/{eventId}/staff/me/clock-in") @Operation(summary="서버 시각으로 출근",description="퇴근 후 같은 날 재출근 가능. 동시에 열린 출근 구간은 한 개만 허용하며 중복은 409 ALREADY_CLOCKED_IN 또는 OPEN_ATTENDANCE. attendanceId는 각 근무 구간의 ID입니다.")
    @ApiResponse(responseCode="200",content=@Content(schema=@Schema(implementation=StaffResponses.AttendanceState.class)))
    public Map<String,Object> clockIn(@PathVariable UUID eventId,@AuthenticationPrincipal AccountPrincipal actor) {return service.clock(eventId,actor.getAccountId(),true);}
    @PostMapping("/events/{eventId}/staff/me/clock-out") @Operation(summary="서버 시각으로 퇴근",description="현재 열려 있는 한 구간만 닫습니다. 자정 이후에도 원래 출근 날짜를 유지하며 미출근/중복 퇴근은 409 NOT_CLOCKED_IN입니다.")
    @ApiResponse(responseCode="200",content=@Content(schema=@Schema(implementation=StaffResponses.AttendanceState.class)))
    public Map<String,Object> clockOut(@PathVariable UUID eventId,@AuthenticationPrincipal AccountPrincipal actor) {return service.clock(eventId,actor.getAccountId(),false);}
    @GetMapping("/events/{eventId}/staff/me/attendance") @Operation(summary="내 출퇴근 기록·근무 분 수",description="구간별 표준 페이지. 같은 날짜에 여러 행이 있을 수 있으며 attendanceId로 구분합니다. totalMinutes는 전체 경과 분 합계(휴게 공제 아님), today는 한국 날짜. todayAttendance는 오늘의 최신 한 구간, todayAttendances는 오늘 전체, openAttendance는 날짜와 무관한 미퇴근 구간입니다. 버튼은 canClockIn/canClockOut을 사용합니다.")
    @ApiResponse(responseCode="200",content=@Content(schema=@Schema(implementation=StaffResponses.AttendancePage.class)))
    public Map<String,Object> attendance(@PathVariable UUID eventId,@AuthenticationPrincipal AccountPrincipal actor,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {return service.attendance(eventId,actor.getAccountId(),page,size);}
}
