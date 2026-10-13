package com.freightfox.repository;

import com.freightfox.model.TransporterDataset;

import java.util.Optional;

/**
 * Stores the most recently submitted dataset.
 */
public interface TransporterDataRepository {

    /**
     * Replaces any previously stored dataset.
     */
    void save(TransporterDataset dataset);

    /**
     * Returns the stored dataset, or empty if nothing has been submitted yet.
     */
    Optional<TransporterDataset> findDataset();
}
