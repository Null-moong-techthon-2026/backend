package kr.boothrock.api.auth.dto;

import java.util.UUID;

public record AccountResponse(UUID id, String nickname, String phoneNumber, String email,
        @io.swagger.v3.oas.annotations.media.Schema(description="화면 분기용 역할. ADMIN은 DB 지정 플랫폼 관리자입니다.", allowableValues={"ADMIN","ORGANIZER","OPERATOR","STAFF"}) String role) {}
