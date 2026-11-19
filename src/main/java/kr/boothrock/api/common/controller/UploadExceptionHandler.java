package kr.boothrock.api.common.controller;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/** Multipart parsing can fail before a controller is selected: do not scope this advice by package. */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class UploadExceptionHandler {
    public record UploadError(String code,String message) {}

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.PAYLOAD_TOO_LARGE)
    public ResponseEntity<UploadError> tooLarge() {
        return ResponseEntity.status(413).body(new UploadError("FILE_TOO_LARGE","Maximum file size is 10 MiB; maximum multipart request size is 11 MiB."));
    }
}
