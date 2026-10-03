package kr.boothrock.api.auth.dto;

import java.util.UUID;

public record AccountResponse(UUID id, String nickname, String phoneNumber, String email) {}
