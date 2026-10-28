package com.example.claims.api;

import com.example.claims.domain.InvalidStateTransitionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.*;

/** One consistent error shape: {timestamp, status, error | errors}. Never leaks stack traces or SQL. */
@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String,Object>> validation(MethodArgumentNotValidException ex) {
        List<String> errors = ex.getBindingResult().getFieldErrors().stream()
            .map(e -> e.getField() + ": " + e.getDefaultMessage()).toList();
        return ResponseEntity.badRequest().body(Map.of("timestamp", Instant.now().toString(), "status", 400, "errors", errors));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String,Object>> status(ResponseStatusException ex) {
        return body(ex.getStatusCode(), ex.getReason() == null ? "Request failed" : ex.getReason());
    }

    @ExceptionHandler(InvalidStateTransitionException.class)
    public ResponseEntity<Map<String,Object>> transition(InvalidStateTransitionException ex) {
        return body(HttpStatus.CONFLICT, ex.getMessage());
    }

    /** Two people updated the same claim at once; the loser reloads and retries. */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<Map<String,Object>> optimisticLock(ObjectOptimisticLockingFailureException ex) {
        return body(HttpStatus.CONFLICT, "The claim was modified by someone else. Reload it and retry.");
    }

    /** Unique-constraint races (same claimant email / staff email / claim number submitted concurrently). */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String,Object>> integrity(DataIntegrityViolationException ex) {
        String cause = ex.getMostSpecificCause().getMessage();
        log.warn("Data integrity violation: {}", cause);
        if (hasCauseContaining(ex, "uk_claims_submission_fingerprint"))
            return body(HttpStatus.CONFLICT, "A claim with the same claimant email and details already exists.");
        return body(HttpStatus.CONFLICT, "The request conflicts with existing data. Please retry.");
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Map<String,Object>> badInput(Exception ex) {
        return body(HttpStatus.BAD_REQUEST, "Malformed request: check JSON syntax, enum values and parameter types");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String,Object>> unexpected(Exception ex) {
        // Spring's own web errors (404 unknown path, 405 wrong method, missing parameter...) keep their status.
        if (ex instanceof ErrorResponse er) return body(er.getStatusCode(), er.getBody().getDetail());
        log.error("Unexpected error", ex);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error");
    }

    private static ResponseEntity<Map<String,Object>> body(HttpStatusCode code, String message) {
        return ResponseEntity.status(code).body(Map.of("timestamp", Instant.now().toString(),
            "status", code.value(), "error", message));
    }

    private static boolean hasCauseContaining(Throwable ex, String text) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause.getMessage() != null && cause.getMessage().contains(text)) return true;
        }
        return false;
    }
}
