package com.freightfox.model;

/**
 * The price a transporter charges to serve one lane.
 *
 * @param laneId identifier of the quoted lane
 * @param quote  positive price for serving the lane
 */
public record LaneQuote(long laneId, long quote) {
}
