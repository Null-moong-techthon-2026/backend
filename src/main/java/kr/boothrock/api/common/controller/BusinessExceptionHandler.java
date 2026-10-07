package kr.boothrock.api.common.controller;

import java.util.List;
import java.util.UUID;
import kr.boothrock.api.common.service.ApiException;
import kr.boothrock.api.common.service.Rules;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice(basePackages = {"kr.boothrock.api.event", "kr.boothrock.api.booth", "kr.boothrock.api.map", "kr.boothrock.api.announcement"})
public class BusinessExceptionHandler {
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<?> business(ApiException error) {
        return error(error.status(), error.code(), error.getMessage(), List.of());
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> validation(MethodArgumentNotValidException error) {
        var fields = error.getBindingResult().getFieldErrors().stream()
                .map(field -> Rules.result("field", field.getField(), "message", field.getDefaultMessage())).toList();
        return error(400, "VALIDATION_ERROR", "Check the input.", fields);
    }
    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class, HandlerMethodValidationException.class})
    public ResponseEntity<?> invalidInput(Exception ignored) {
        return error(400, "VALIDATION_ERROR", "Check the input.", List.of());
    }
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<?> tooLarge() { return error(413, "FILE_TOO_LARGE", "Maximum image size is 10 MiB.", List.of()); }
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<?> constraint(DataIntegrityViolationException ignored) {
        return error(409, "INVALID_STATE", "The requested change conflicts with existing data.", List.of());
    }
    private ResponseEntity<?> error(int status, String code, String message, List<?> fields) {
        return ResponseEntity.status(status).body(Rules.result("code", code, "message", message,
                "fieldErrors", fields, "requestId", UUID.randomUUID().toString()));
    }
}
