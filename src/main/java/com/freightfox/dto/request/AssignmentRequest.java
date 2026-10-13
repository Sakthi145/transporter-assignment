package com.freightfox.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Request body of {@code POST /api/v1/transporters/assignment}.
 *
 * @param maxTransporters maximum number of distinct transporters that may be selected
 */
public record AssignmentRequest(
        @Schema(example = "3", minimum = "1")
        @NotNull(message = "maxTransporters is required")
        @Min(value = 1, message = "maxTransporters must be at least 1")
        Integer maxTransporters) {
}
