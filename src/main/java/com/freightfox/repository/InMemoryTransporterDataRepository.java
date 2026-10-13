package com.freightfox.repository;

import com.freightfox.model.TransporterDataset;
import org.springframework.stereotype.Repository;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe in-memory repository holding a single immutable dataset snapshot.
 * The model records copy their lists into unmodifiable collections, so a stored snapshot
 * cannot be changed by the caller afterwards and can be shared safely between threads.
 */
@Repository
public class InMemoryTransporterDataRepository implements TransporterDataRepository {

    private final AtomicReference<TransporterDataset> current = new AtomicReference<>();

    @Override
    public void save(TransporterDataset dataset) {
        Objects.requireNonNull(dataset, "dataset must not be null");
        current.set(new TransporterDataset(dataset.lanes(), dataset.transporters()));
    }

    @Override
    public Optional<TransporterDataset> findDataset() {
        return Optional.ofNullable(current.get());
    }
}
