package com.forensics.platform.exception;

import jakarta.persistence.PersistenceException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Maps exceptions to consistent JSON errors. Messages never include stack traces, SQL,
 * evidence content, credentials or token values.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BadCredentialsException.class)
    ResponseEntity<Map<String, Object>> badCredentials() {
        return respond(HttpStatus.UNAUTHORIZED, "Invalid username or password");
    }

    @ExceptionHandler(DisabledException.class)
    ResponseEntity<Map<String, Object>> disabled() {
        return respond(HttpStatus.UNAUTHORIZED, "Account is disabled");
    }

    @ExceptionHandler(AccountLockedException.class)
    ResponseEntity<Map<String, Object>> locked(AccountLockedException ex) {
        return ResponseEntity.status(423).body(error(423, "Locked", ex.getMessage()));
    }

    @ExceptionHandler(RefreshTokenException.class)
    ResponseEntity<Map<String, Object>> refreshToken(RefreshTokenException ex) {
        return respond(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    // Method-security denials (@PreAuthorize) surface here; without this they became HTTP 500.
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<Map<String, Object>> accessDenied() {
        return respond(HttpStatus.FORBIDDEN, "Insufficient permission");
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<Map<String, Object>> authentication() {
        return respond(HttpStatus.UNAUTHORIZED, "Missing, invalid, or expired authentication");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, Object>> validation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        item -> item.getField(),
                        item -> item.getDefaultMessage() == null ? "Invalid value" : item.getDefaultMessage(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        Map<String, Object> body = error(HttpStatus.BAD_REQUEST, "Validation failed");
        body.put("fields", fieldErrors);
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<Map<String, Object>> constraintViolation(ConstraintViolationException ex) {
        Map<String, String> fieldErrors = ex.getConstraintViolations().stream()
                .collect(Collectors.toMap(
                        item -> String.valueOf(item.getPropertyPath()),
                        item -> item.getMessage(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        Map<String, Object> body = error(HttpStatus.BAD_REQUEST, "Validation failed");
        body.put("fields", fieldErrors);
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<Map<String, Object>> typeMismatch(MethodArgumentTypeMismatchException ex) {
        return respond(HttpStatus.BAD_REQUEST, "Invalid value for parameter '" + ex.getName() + "'");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<Map<String, Object>> missingParameter(MissingServletRequestParameterException ex) {
        return respond(HttpStatus.BAD_REQUEST, "Missing required parameter '" + ex.getParameterName() + "'");
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    ResponseEntity<Map<String, Object>> missingPart(MissingServletRequestPartException ex) {
        return respond(HttpStatus.BAD_REQUEST, "Missing required upload part '" + ex.getRequestPartName() + "'");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Map<String, Object>> unreadable() {
        return respond(HttpStatus.BAD_REQUEST, "Request body is missing or malformed");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<Map<String, Object>> mediaType() {
        return respond(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported content type");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<Map<String, Object>> method() {
        return respond(HttpStatus.METHOD_NOT_ALLOWED, "HTTP method not supported for this endpoint");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<Map<String, Object>> noResource() {
        return respond(HttpStatus.NOT_FOUND, "Endpoint not found");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<Map<String, Object>> uploadTooLarge() {
        return respond(HttpStatus.PAYLOAD_TOO_LARGE, "The uploaded file exceeds the maximum allowed size");
    }

    @ExceptionHandler(MultipartException.class)
    ResponseEntity<Map<String, Object>> multipart() {
        return respond(HttpStatus.BAD_REQUEST, "The upload could not be read. Send the file as multipart/form-data.");
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<Map<String, Object>> responseStatus(ResponseStatusException ex) {
        int status = ex.getStatusCode().value();
        String reason = ex.getReason() == null ? "Request failed" : ex.getReason();
        HttpStatus resolved = HttpStatus.resolve(status);
        return ResponseEntity.status(status).body(error(status, resolved == null ? String.valueOf(status) : resolved.getReasonPhrase(), reason));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Map<String, Object>> integrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getClass().getSimpleName());
        return respond(HttpStatus.CONFLICT, "The request conflicts with existing records");
    }

    @ExceptionHandler({DataAccessException.class, PersistenceException.class})
    ResponseEntity<Map<String, Object>> dataAccess(Exception ex) {
        log.error("Database error while handling request", ex);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "A database error occurred while processing the request");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, Object>> fallback(Exception ex) {
        log.error("Unhandled server error while handling request", ex);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
    }

    private ResponseEntity<Map<String, Object>> respond(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(error(status, message));
    }

    private Map<String, Object> error(HttpStatus status, String message) {
        return error(status.value(), status.getReasonPhrase(), message);
    }

    private Map<String, Object> error(int status, String error, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status);
        body.put("error", error);
        body.put("message", message);
        return body;
    }
}
