package com.freightfox.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * Response envelope used for simple success messages and for every error.
 *
 * @param status    {@code success} or {@code error}
 * @param message   human-readable message
 * @param timestamp time the error occurred (errors only)
 * @param errors    field-level messages (errors only, omitted when empty)
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiResponse(String status, String message, Instant timestamp, List<String> errors) {

    public static final String SUCCESS = "success";
    public static final String ERROR = "error";

    public static ApiResponse success(String message) {
        return new ApiResponse(SUCCESS, message, null, null);
    }

    public static ApiResponse error(String message) {
        return error(message, List.of());
    }

    public static ApiResponse error(String message, List<String> errors) {
        return new ApiResponse(ERROR, message, Instant.now(), List.copyOf(errors));
    }
}
