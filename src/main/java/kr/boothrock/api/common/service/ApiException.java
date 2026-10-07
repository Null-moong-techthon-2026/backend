package kr.boothrock.api.common.service;

public class ApiException extends RuntimeException {
    private final int status;
    private final String code;
    public ApiException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }
    public int status() { return status; }
    public String code() { return code; }
    public static ApiException notFound() { return new ApiException(404, "RESOURCE_NOT_FOUND", "Resource not found."); }
    public static ApiException forbidden() { return new ApiException(403, "FORBIDDEN", "You cannot perform this action."); }
}
