package kr.boothrock.api.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @NotNull OnboardingType onboardingType,
        @NotBlank @Pattern(regexp = "[a-z0-9_]{4,30}") String loginId,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank @Size(max = 100) String nickname,
        @NotBlank @Pattern(regexp = "[+]?[0-9]{8,15}") String phoneNumber,
        @NotBlank @Email @Size(max = 254) String email) {
    public enum OnboardingType { ORGANIZER, OPERATOR }

    @Override
    public String toString() {
        return "SignupRequest[credentials=REDACTED]";
    }
}
