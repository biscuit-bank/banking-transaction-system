package com.depi.bank.api;

import com.depi.bank.exception.BankException;
import com.depi.bank.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@RestControllerAdvice(basePackages = "com.depi.bank.api")
public class GlobalExceptionHandler {

    private final Clock clock;

    public GlobalExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(BankException.class)
    public ResponseEntity<ApiErrorResponse> handleBankException(BankException ex, HttpServletRequest request) {
        ErrorCode code = ex.getErrorCode();
        ApiErrorResponse body = new ApiErrorResponse(
                Instant.now(clock),
                code.getHttpStatus().value(),
                code.name(),
                ex.getMessage(),
                request.getRequestURI(),
                List.of()
        );
        return ResponseEntity.status(code.getHttpStatus()).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .toList();
        ErrorCode code = ErrorCode.VALIDATION_ERROR;
        ApiErrorResponse body = new ApiErrorResponse(
                Instant.now(clock),
                code.getHttpStatus().value(),
                code.name(),
                code.getDefaultMessage(),
                request.getRequestURI(),
                details
        );
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        ErrorCode code = ErrorCode.ACCESS_DENIED;
        ApiErrorResponse body = new ApiErrorResponse(
                Instant.now(clock),
                code.getHttpStatus().value(),
                code.name(),
                code.getDefaultMessage(),
                request.getRequestURI(),
                List.of()
        );
        return ResponseEntity.status(code.getHttpStatus()).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        ApiErrorResponse body = new ApiErrorResponse(
                Instant.now(clock),
                500,
                "INTERNAL_SERVER_ERROR",
                "An unexpected error occurred. No partial changes were committed.",
                request.getRequestURI(),
                List.of()
        );
        return ResponseEntity.internalServerError().body(body);
    }
}