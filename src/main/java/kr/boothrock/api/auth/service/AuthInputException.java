package kr.boothrock.api.auth.service;

public class AuthInputException extends RuntimeException {
    private final String code;

    public AuthInputException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
