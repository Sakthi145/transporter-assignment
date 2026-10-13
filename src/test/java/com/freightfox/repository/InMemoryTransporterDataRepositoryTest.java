package com.freightfox.repository;

import com.freightfox.model.Lane;
import com.freightfox.model.LaneQuote;
import com.freightfox.model.Transporter;
import com.freightfox.model.TransporterDataset;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryTransporterDataRepositoryTest {

    private final InMemoryTransporterDataRepository repository = new InMemoryTransporterDataRepository();

    private static TransporterDataset dataset(long laneId) {
        return new TransporterDataset(
                List.of(new Lane(laneId, "A", "B")),
                List.of(new Transporter(1, "T1", List.of(new LaneQuote(laneId, 100)))));
    }

    @Test
    void isEmptyBeforeAnythingIsSaved() {
        assertThat(repository.findDataset()).isEmpty();
    }

    @Test
    void returnsSavedDataset() {
        TransporterDataset dataset = dataset(1);

        repository.save(dataset);

        assertThat(repository.findDataset()).contains(dataset);
    }

    @Test
    void saveReplacesPreviousDataset() {
        repository.save(dataset(1));
        repository.save(dataset(2));

        assertThat(repository.findDataset()).get()
                .extracting(stored -> stored.lanes().getFirst().id())
                .isEqualTo(2L);
    }

    @Test
    void rejectsNullDataset() {
        assertThatThrownBy(() -> repository.save(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void storedSnapshotIsUnaffectedByLaterChangesToSourceLists() {
        List<Lane> lanes = new ArrayList<>(List.of(new Lane(1, "A", "B")));
        List<LaneQuote> quotes = new ArrayList<>(List.of(new LaneQuote(1, 100)));
        List<Transporter> transporters = new ArrayList<>(List.of(new Transporter(1, "T1", quotes)));
        repository.save(new TransporterDataset(lanes, transporters));

        lanes.add(new Lane(2, "C", "D"));
        quotes.add(new LaneQuote(2, 200));
        transporters.clear();

        TransporterDataset stored = repository.findDataset().orElseThrow();
        assertThat(stored.lanes()).hasSize(1);
        assertThat(stored.transporters()).hasSize(1);
        assertThat(stored.transporters().getFirst().laneQuotes()).hasSize(1);
    }

    @Test
    void storedSnapshotCannotBeModified() {
        repository.save(dataset(1));
        TransporterDataset stored = repository.findDataset().orElseThrow();

        assertThatThrownBy(() -> stored.lanes().add(new Lane(9, "X", "Y")))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> stored.transporters().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> stored.transporters().getFirst().laneQuotes().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
