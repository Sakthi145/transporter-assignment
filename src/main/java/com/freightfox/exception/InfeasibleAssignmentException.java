package com.freightfox.exception;

/**
 * Thrown when the lanes cannot be covered within the transporter limit. Mapped to HTTP 422.
 */
public class InfeasibleAssignmentException extends RuntimeException {

    public InfeasibleAssignmentException(String message) {
        super(message);
    }

    /**
     * Creates the exception for a limit below the smallest number of transporters able to cover all lanes.
     */
    public static InfeasibleAssignmentException belowMinimum(int maxTransporters, int minimumRequired) {
        String noun = maxTransporters == 1 ? "transporter" : "transporters";
        return new InfeasibleAssignmentException("Cannot cover all lanes with %d %s (minimum required: %d)"
                .formatted(maxTransporters, noun, minimumRequired));
    }
}
