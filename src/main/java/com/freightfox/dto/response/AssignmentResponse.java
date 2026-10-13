package com.freightfox.dto.response;

import java.util.List;

/**
 * Response of {@code POST /api/v1/transporters/assignment}.
 *
 * @param status               always {@code success}
 * @param totalCost            sum of all assignment costs
 * @param assignments          one entry per lane, sorted by lane ID
 * @param selectedTransporters distinct transporters actually used, sorted ascending
 */
public record AssignmentResponse(String status, long totalCost, List<LaneAssignmentResponse> assignments,
                                 List<Long> selectedTransporters) {
}
