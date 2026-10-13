package com.freightfox.strategy;

import com.freightfox.exception.InfeasibleAssignmentException;
import com.freightfox.model.AssignmentResult;
import com.freightfox.model.TransporterDataset;

/**
 * Chooses which transporters serve which lanes.
 *
 * <p>Implementations must cover every lane using only real quotes, use at most {@code maxTransporters}
 * distinct transporters, and minimise total cost. This abstraction allows the exact exhaustive solver to be
 * replaced by, for example, an ILP solver or a heuristic when inputs grow too large to enumerate.
 */
public interface TransporterSelectionStrategy {

    /**
     * Selects transporters for all lanes of the dataset.
     *
     * @param dataset         lanes and transporter quotes
     * @param maxTransporters maximum number of distinct transporters, at least 1
     * @return assignments sorted by lane ID, the transporters used (ascending) and the total cost
     * @throws InfeasibleAssignmentException if the lanes cannot be covered within the limit
     */
    AssignmentResult selectTransporters(TransporterDataset dataset, int maxTransporters);
}
