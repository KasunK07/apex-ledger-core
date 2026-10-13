package com.ledger.api.error;

import com.ledger.api.dto.response.ApiError;
import com.ledger.api.dto.response.FieldViolation;
import com.ledger.domain.exception.LedgerException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Map<String, HttpStatus> STATUSES = Map.ofEntries(
            Map.entry("INSUFFICIENT_FUNDS", HttpStatus.UNPROCESSABLE_ENTITY),
            Map.entry("IDEMPOTENCY_CONFLICT", HttpStatus.CONFLICT),
            Map.entry("IDEMPOTENCY_KEY_REQUIRED", HttpStatus.BAD_REQUEST),
            Map.entry("ACCOUNT_FROZEN", HttpStatus.CONFLICT),
            Map.entry("ACCOUNT_CLOSED", HttpStatus.CONFLICT),
            Map.entry("ACCOUNT_NOT_FOUND", HttpStatus.NOT_FOUND),
            Map.entry("CUSTOMER_NOT_FOUND", HttpStatus.NOT_FOUND),
            Map.entry("CUSTOMER_ALREADY_EXISTS", HttpStatus.CONFLICT),
            Map.entry("CUSTOMER_SUSPENDED", HttpStatus.CONFLICT),
            Map.entry("CUSTOMER_KYC_PENDING", HttpStatus.CONFLICT),
            Map.entry("CURRENCY_MISMATCH", HttpStatus.UNPROCESSABLE_ENTITY),
            Map.entry("UNSUPPORTED_CURRENCY", HttpStatus.UNPROCESSABLE_ENTITY),
            Map.entry("SAME_ACCOUNT_TRANSFER", HttpStatus.UNPROCESSABLE_ENTITY),
            Map.entry("TREASURY_PROTECTED", HttpStatus.UNPROCESSABLE_ENTITY),
            Map.entry("INVALID_AMOUNT", HttpStatus.BAD_REQUEST),
            Map.entry("INVALID_REQUEST", HttpStatus.BAD_REQUEST)
    );

    private final ApiErrorWriter errors;

    public GlobalExceptionHandler(ApiErrorWriter errors) {
        this.errors = errors;
    }

    @ExceptionHandler(LedgerException.class)
    public ResponseEntity<ApiError> handleLedger(LedgerException ex, HttpServletRequest request) {
        HttpStatus status = STATUSES.getOrDefault(ex.getCode(), HttpStatus.BAD_REQUEST);
        return respond(status, ex.getCode(), ex.getMessage(), request, List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleBody(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<FieldViolation> violations = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldViolation(error.getField(), error.getDefaultMessage()))
                .toList();
        return respond(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed", request, violations);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiError> handleMethod(HandlerMethodValidationException ex, HttpServletRequest request) {
        List<FieldViolation> violations = ex.getParameterValidationResults().stream()
                .flatMap(result -> {
                    String name = result.getMethodParameter().getParameterName();
                    String field = name == null ? "request" : name;
                    return result.getResolvableErrors().stream()
                            .map(error -> new FieldViolation(field, error.getDefaultMessage()));
                })
                .toList();
        return respond(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed", request, violations);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiError> handleMissingHeader(MissingRequestHeaderException ex, HttpServletRequest request) {
        String code = "Idempotency-Key".equalsIgnoreCase(ex.getHeaderName())
                ? "IDEMPOTENCY_KEY_REQUIRED"
                : "VALIDATION_FAILED";
        return respond(HttpStatus.BAD_REQUEST, code, ex.getMessage(), request, List.of());
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiError> handleUnreadable(Exception ex, HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "Request body or path parameter is invalid", request, List.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        String detail = mostSpecificMessage(ex);
        if (detail.contains("uq_customers_email")) {
            return respond(HttpStatus.CONFLICT, "CUSTOMER_ALREADY_EXISTS", "A customer with that email already exists", request, List.of());
        }
        if (detail.contains("accounts_balance_non_negative")) {
            return respond(HttpStatus.UNPROCESSABLE_ENTITY, "INSUFFICIENT_FUNDS", "Posting would make a balance negative", request, List.of());
        }
        if (detail.contains("uq_idempotency_key")) {
            return respond(HttpStatus.SERVICE_UNAVAILABLE, "LEDGER_BUSY", "Retry the same Idempotency-Key", request, List.of());
        }
        log.warn("Unhandled data integrity failure on {}: {}", request.getRequestURI(), detail);
        return respond(HttpStatus.CONFLICT, "DATA_CONFLICT", "The ledger rejected the write", request, List.of());
    }

    @ExceptionHandler(ConcurrencyFailureException.class)
    public ResponseEntity<ApiError> handleBusy(ConcurrencyFailureException ex, HttpServletRequest request) {
        return respond(HttpStatus.SERVICE_UNAVAILABLE, "LEDGER_BUSY", "The ledger is busy. Retry the same Idempotency-Key", request, List.of());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleMissing(NoResourceFoundException ex, HttpServletRequest request) {
        return respond(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource was not found", request, List.of());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        return respond(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "HTTP method is not supported", request, List.of());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiError> handleMediaType(HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        return respond(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", "Content-Type is not supported", request, List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled failure on {}", request.getRequestURI(), ex);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "The ledger could not complete the request", request, List.of());
    }

    private ResponseEntity<ApiError> respond(HttpStatus status, String code, String message, HttpServletRequest request,
                                             List<FieldViolation> violations) {
        return ResponseEntity.status(status)
                .body(errors.body(status, code, message, request.getRequestURI(), violations));
    }

    private String mostSpecificMessage(Throwable ex) {
        Throwable cursor = ex;
        String message = "";
        while (cursor != null) {
            if (cursor.getMessage() != null) {
                message = cursor.getMessage();
            }
            cursor = cursor.getCause();
        }
        return message;
    }
}
