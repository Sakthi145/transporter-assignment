package com.freightfox.model;

import java.util.List;

/**
 * The outcome of a transporter selection.
 *
 * @param assignments            one assignment per lane, sorted by lane ID
 * @param selectedTransporterIds distinct transporters actually used, sorted ascending
 * @param totalCost              sum of all assignment costs
 */
public record AssignmentResult(List<LaneAssignment> assignments, List<Long> selectedTransporterIds, long totalCost) {

    public AssignmentResult {
        assignments = List.copyOf(assignments);
        selectedTransporterIds = List.copyOf(selectedTransporterIds);
    }
}
