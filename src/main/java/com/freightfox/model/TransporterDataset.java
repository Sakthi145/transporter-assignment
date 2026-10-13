package com.freightfox.model;

import java.util.List;

/**
 * An immutable snapshot of all submitted lanes and transporters.
 *
 * @param lanes        lanes that must be covered
 * @param transporters transporters with their quotes
 */
public record TransporterDataset(List<Lane> lanes, List<Transporter> transporters) {

    public TransporterDataset {
        lanes = List.copyOf(lanes);
        transporters = List.copyOf(transporters);
    }
}
