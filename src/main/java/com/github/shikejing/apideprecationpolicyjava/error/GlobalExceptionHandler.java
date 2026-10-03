package com.github.shikejing.apideprecationpolicyjava.error;

import com.github.shikejing.apideprecationpolicyjava.version.InvalidVersionTransitionException;
import com.github.shikejing.apideprecationpolicyjava.version.VersionNotFoundException;
import com.github.shikejing.apideprecationpolicyjava.version.VersionRetiredException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Maps version lifecycle errors to machine-readable HTTP responses.
 * Retired versions (410 VERSION_RETIRED) are deliberately distinguished from
 * unknown versions (404 VERSION_NOT_FOUND).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(VersionNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(VersionNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, "VERSION_NOT_FOUND", ex.getMessage(), ex.getVersion());
    }

    @ExceptionHandler(VersionRetiredException.class)
    public ResponseEntity<Map<String, Object>> handleRetired(VersionRetiredException ex) {
        return build(HttpStatus.GONE, "VERSION_RETIRED", ex.getMessage(), ex.getVersion());
    }

    @ExceptionHandler(InvalidVersionTransitionException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidTransition(InvalidVersionTransitionException ex) {
        return build(HttpStatus.CONFLICT, "VERSION_INVALID_TRANSITION", ex.getMessage(), null);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        return build(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", ex.getMessage(), null);
    }

    private ResponseEntity<Map<String, Object>> build(HttpStatus status, String error,
                                                       String message, String version) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", error);
        body.put("message", message);
        body.put("version", version);
        body.put("timestamp", Instant.now().toString());
        return ResponseEntity.status(status).body(body);
    }
}
