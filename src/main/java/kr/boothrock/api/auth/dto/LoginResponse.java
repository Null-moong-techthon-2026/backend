package kr.boothrock.api.auth.dto;

import java.util.UUID;

public record LoginResponse(UUID id, String nickname) {}
