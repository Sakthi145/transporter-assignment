package com.freightfox.service;

import com.freightfox.dto.request.TransporterInputRequest;
import com.freightfox.dto.request.TransporterInputRequest.LaneQuoteRequest;
import com.freightfox.dto.request.TransporterInputRequest.LaneRequest;
import com.freightfox.dto.request.TransporterInputRequest.TransporterRequest;
import com.freightfox.exception.InvalidInputException;
import com.freightfox.model.Lane;
import com.freightfox.model.LaneQuote;
import com.freightfox.model.Transporter;
import com.freightfox.model.TransporterDataset;
import com.freightfox.repository.TransporterDataRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TransporterInputServiceTest {

    @Mock
    private TransporterDataRepository repository;

    @InjectMocks
    private TransporterInputService service;

    private static LaneRequest lane(long id) {
        return new LaneRequest(id, " Origin " + id + " ", "Destination " + id);
    }

    private static LaneQuoteRequest quote(long laneId, long price) {
        return new LaneQuoteRequest(laneId, price);
    }

    private static TransporterRequest transporter(long id, LaneQuoteRequest... quotes) {
        return new TransporterRequest(id, "Transporter " + id, List.of(quotes));
    }

    private static TransporterInputRequest validRequest() {
        return new TransporterInputRequest(
                List.of(lane(1), lane(2)),
                List.of(transporter(1, quote(1, 5000), quote(2, 6000)), transporter(2, quote(2, 5500))));
    }

    private void assertRejectedWith(TransporterInputRequest request, String expectedError) {
        assertThatThrownBy(() -> service.saveInput(request))
                .isInstanceOf(InvalidInputException.class)
                .hasMessage("Invalid input data")
                .satisfies(ex -> assertThat(((InvalidInputException) ex).getErrors()).contains(expectedError));
        verify(repository, never()).save(any());
    }

    @Test
    void validInputIsMappedToModelAndSaved() {
        service.saveInput(validRequest());

        ArgumentCaptor<TransporterDataset> captor = ArgumentCaptor.forClass(TransporterDataset.class);
        verify(repository).save(captor.capture());
        TransporterDataset saved = captor.getValue();
        assertThat(saved.lanes()).containsExactly(
                new Lane(1, "Origin 1", "Destination 1"),
                new Lane(2, "Origin 2", "Destination 2"));
        assertThat(saved.transporters()).containsExactly(
                new Transporter(1, "Transporter 1", List.of(new LaneQuote(1, 5000), new LaneQuote(2, 6000))),
                new Transporter(2, "Transporter 2", List.of(new LaneQuote(2, 5500))));
    }

    @Test
    void duplicateLaneIdsAreRejected() {
        TransporterInputRequest request = new TransporterInputRequest(
                List.of(lane(1), lane(1)), List.of(transporter(1, quote(1, 100))));

        assertRejectedWith(request, "Duplicate lane id: 1");
    }

    @Test
    void duplicateTransporterIdsAreRejected() {
        TransporterInputRequest request = new TransporterInputRequest(
                List.of(lane(1)), List.of(transporter(7, quote(1, 100)), transporter(7, quote(1, 200))));

        assertRejectedWith(request, "Duplicate transporter id: 7");
    }

    @Test
    void quoteForUnknownLaneIsRejected() {
        TransporterInputRequest request = new TransporterInputRequest(
                List.of(lane(1)), List.of(transporter(1, quote(1, 100), quote(99, 200))));

        assertRejectedWith(request, "Transporter 1 quotes unknown lane id: 99");
    }

    @Test
    void sameTransporterQuotingSameLaneTwiceIsRejected() {
        TransporterInputRequest request = new TransporterInputRequest(
                List.of(lane(1)), List.of(transporter(3, quote(1, 100), quote(1, 90))));

        assertRejectedWith(request, "Transporter 3 quotes lane 1 more than once");
    }

    @Test
    void laneWithoutAnyQuoteIsRejected() {
        TransporterInputRequest request = new TransporterInputRequest(
                List.of(lane(1), lane(2)), List.of(transporter(1, quote(1, 100))));

        assertRejectedWith(request, "Lane 2 has no quotes");
    }

    @Test
    void nonPositiveQuoteIsRejected() {
        TransporterInputRequest request = new TransporterInputRequest(
                List.of(lane(1)), List.of(transporter(1, quote(1, 0))));

        assertRejectedWith(request, "Transporter 1 has a non-positive quote for lane 1");
    }

    @Test
    void allViolationsAreReportedTogether() {
        TransporterInputRequest request = new TransporterInputRequest(
                List.of(lane(1), lane(1), lane(2)),
                List.of(transporter(1, quote(1, -5)), transporter(1, quote(3, 100))));

        assertThatThrownBy(() -> service.saveInput(request))
                .isInstanceOf(InvalidInputException.class)
                .satisfies(ex -> assertThat(((InvalidInputException) ex).getErrors()).containsExactlyInAnyOrder(
                        "Duplicate lane id: 1",
                        "Duplicate transporter id: 1",
                        "Transporter 1 has a non-positive quote for lane 1",
                        "Transporter 1 quotes unknown lane id: 3",
                        "Lane 2 has no quotes"));
    }

    @Test
    void eachSubmissionReplacesThePreviousDataset() {
        TransporterInputRequest second = new TransporterInputRequest(
                List.of(lane(5)), List.of(transporter(9, quote(5, 700))));

        service.saveInput(validRequest());
        service.saveInput(second);

        ArgumentCaptor<TransporterDataset> captor = ArgumentCaptor.forClass(TransporterDataset.class);
        verify(repository, times(2)).save(captor.capture());
        TransporterDataset latest = captor.getAllValues().get(1);
        assertThat(latest.lanes()).extracting(Lane::id).containsExactly(5L);
        assertThat(latest.transporters()).extracting(Transporter::id).containsExactly(9L);
    }
}
