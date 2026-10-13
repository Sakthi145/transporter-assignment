package com.freightfox.model;

/**
 * The transporter chosen for a lane and the price it charges.
 *
 * @param laneId        assigned lane
 * @param transporterId transporter serving the lane
 * @param cost          quote of that transporter for the lane
 */
public record LaneAssignment(long laneId, long transporterId, long cost) {
}
