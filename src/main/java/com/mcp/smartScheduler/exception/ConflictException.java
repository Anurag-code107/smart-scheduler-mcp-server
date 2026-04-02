package com.mcp.smartScheduler.exception;

/**
 * Thrown when a business rule conflict is detected (e.g. scheduling overlap,
 * duplicate email registration).
 * Maps to HTTP 409 Conflict.
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}