package com.example.evaluator.exception;

/**
 * Thrown when the primary LLM call fails (network error, timeout, non-2xx response).
 * The boolean timeout flag lets the exception handler map to 504 vs 502.
 */
public class PrimaryLlmException extends RuntimeException {

    private final boolean timeout;

    public PrimaryLlmException(String message, Throwable cause, boolean timeout) {
        super(message, cause);
        this.timeout = timeout;
    }

    public boolean isTimeout() {
        return timeout;
    }
}
