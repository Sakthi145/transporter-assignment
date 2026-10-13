package com.freightfox.exception;

import java.util.List;

/**
 * Thrown when submitted data violates a business rule that bean validation cannot express. Mapped to HTTP 400.
 */
public class InvalidInputException extends RuntimeException {

    private final List<String> errors;

    public InvalidInputException(String message) {
        this(message, List.of());
    }

    public InvalidInputException(String message, List<String> errors) {
        super(message);
        this.errors = List.copyOf(errors);
    }

    public List<String> getErrors() {
        return errors;
    }
}
