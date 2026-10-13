package com.freightfox.exception;

/**
 * Thrown when an assignment is requested before any input data was submitted. Mapped to HTTP 409.
 */
public class NoInputDataException extends RuntimeException {

    public NoInputDataException() {
        super("No input data submitted. Call /api/v1/transporters/input first.");
    }
}
