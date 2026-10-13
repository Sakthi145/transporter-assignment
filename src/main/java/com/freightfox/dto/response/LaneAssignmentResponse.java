package com.freightfox.dto.response;

/**
 * The transporter assigned to one lane.
 *
 * @param laneId        lane identifier
 * @param transporterId assigned transporter
 * @param cost          quote of the assigned transporter for this lane
 */
public record LaneAssignmentResponse(long laneId, long transporterId, long cost) {
}
