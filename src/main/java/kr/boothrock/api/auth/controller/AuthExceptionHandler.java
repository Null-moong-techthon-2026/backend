package kr.boothrock.api.auth.controller;

import java.sql.SQLException;
import kr.boothrock.api.auth.service.AuthInputException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = AuthController.class)
public class AuthExceptionHandler {
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<AuthError> invalidCredentials() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new AuthError("INVALID_CREDENTIALS", "Invalid login ID or password."));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<AuthError> invalidInput() {
        return ResponseEntity.badRequest().body(new AuthError("VALIDATION_ERROR", "Check the input."));
    }

    @ExceptionHandler(AuthInputException.class)
    public ResponseEntity<AuthError> inputOrConflict(AuthInputException error) {
        HttpStatus status = switch (error.getCode()) {
            case "LOGIN_ID_TAKEN" -> HttpStatus.CONFLICT;
            case "ACCOUNT_UNAVAILABLE" -> HttpStatus.FORBIDDEN;
            default -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status).body(new AuthError(error.getCode(), error.getMessage()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<AuthError> concurrentDuplicate(DataIntegrityViolationException error) {
        Throwable cause = error.getMostSpecificCause();
        if (cause instanceof SQLException sql && "23505".equals(sql.getSQLState())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new AuthError("LOGIN_ID_TAKEN", "Login ID is already used."));
        }
        throw error;
    }

    public record AuthError(String code, String message) {}
}
