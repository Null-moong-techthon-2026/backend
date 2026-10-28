package kr.boothrock.api.auth.dto;

import java.util.UUID;

/** platformRole is USER, or ADMIN for platform administrators (set by hand in the database). */
public record AccountResponse(UUID id, String nickname, String phoneNumber, String email, String platformRole) {}
