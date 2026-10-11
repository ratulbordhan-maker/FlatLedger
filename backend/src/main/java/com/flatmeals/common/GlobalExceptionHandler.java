package com.flatmeals.common;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Turns exceptions thrown anywhere in a controller or service into clean
 * JSON error responses (see ApiError), with the right HTTP status code.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** @Valid failed on a request body, e.g. a blank name. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        ApiError body = new ApiError(400, "Bad Request", "Please fix the highlighted fields",
                fieldErrors, Instant.now());
        return ResponseEntity.badRequest().body(body);
    }

    /** The request body is missing or is not valid JSON. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex) {
        return build(HttpStatus.BAD_REQUEST, "Request body is missing or is not valid JSON");
    }

    /** A URL value has the wrong type, e.g. /api/members/abc or ?status=SLEEPING */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return build(HttpStatus.BAD_REQUEST, "Invalid value for '" + ex.getName() + "'");
    }

    /** Business rule violations thrown by our own code. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(NotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiError> handleConflict(ConflictException ex) {
        return build(HttpStatus.CONFLICT, ex.getMessage());
    }

    /**
     * The database refused a change because it breaks a rule (e.g. two people
     * saving the same meal cell at the same moment). Ask the user to retry.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException ex) {
        return build(HttpStatus.CONFLICT, "This change clashes with existing data. Refresh and try again.");
    }

    /** Logged in, but not allowed (e.g. a member calling a manager-only endpoint). */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex) {
        return build(HttpStatus.FORBIDDEN, "You do not have permission to do this");
    }

    /** Errors we throw with an explicit status, e.g. 401 for a wrong password. */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> handleResponseStatus(ResponseStatusException ex) {
        HttpStatusCode code = ex.getStatusCode();
        HttpStatus status = HttpStatus.resolve(code.value());
        String error = (status != null) ? status.getReasonPhrase() : "Error";
        return ResponseEntity.status(code)
                .body(ApiError.of(code.value(), error, ex.getReason()));
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .body(ApiError.of(status.value(), status.getReasonPhrase(), message));
    }
}
