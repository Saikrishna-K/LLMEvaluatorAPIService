package com.example.evaluator.api;

import com.example.evaluator.dto.ErrorResponse;
import com.example.evaluator.exception.PrimaryLlmException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Primary LLM timeout → 504 Gateway Timeout; other primary errors → 502 Bad Gateway. */
    @ExceptionHandler(PrimaryLlmException.class)
    public ResponseEntity<ErrorResponse> handlePrimaryLlmException(
            PrimaryLlmException ex, HttpServletRequest request) {

        if (ex.isTimeout()) {
            log.warn("Primary LLM timed out for {}", request.getRequestURI());
            return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                    .body(new ErrorResponse("GATEWAY_TIMEOUT", "Primary LLM did not respond in time", null));
        }
        log.error("Primary LLM error for {}: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ErrorResponse("BAD_GATEWAY", "Primary LLM returned an error", null));
    }

    /** Bean validation failures → 400 with a joined field error message. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("VALIDATION_ERROR", details, null));
    }

    /** Catch-all for unexpected exceptions. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception for {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred", null));
    }
}
