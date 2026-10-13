package com.freightfox.service;

import com.freightfox.dto.response.AssignmentResponse;
import com.freightfox.dto.response.LaneAssignmentResponse;
import com.freightfox.exception.InfeasibleAssignmentException;
import com.freightfox.exception.InvalidInputException;
import com.freightfox.exception.NoInputDataException;
import com.freightfox.model.AssignmentResult;
import com.freightfox.model.LaneAssignment;
import com.freightfox.model.TransporterDataset;
import com.freightfox.repository.TransporterDataRepository;
import com.freightfox.strategy.TransporterSelectionStrategy;
import com.freightfox.support.TestDatasets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransporterAssignmentServiceTest {

    private static final TransporterDataset DATASET = TestDatasets.threeLanesFourTransporters();

    @Mock
    private TransporterDataRepository repository;

    @Mock
    private TransporterSelectionStrategy strategy;

    @InjectMocks
    private TransporterAssignmentService service;

    @Test
    void missingInputDataRaisesNoInputDataException() {
        when(repository.findDataset()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assign(3))
                .isInstanceOf(NoInputDataException.class)
                .hasMessage("No input data submitted. Call /api/v1/transporters/input first.");
        verifyNoInteractions(strategy);
    }

    @Test
    void limitBelowOneIsRejectedBeforeLoadingData() {
        assertThatThrownBy(() -> service.assign(0))
                .isInstanceOf(InvalidInputException.class)
                .hasMessage("maxTransporters must be at least 1");
        verifyNoInteractions(repository, strategy);
    }

    @Test
    void limitAboveTransporterCountIsCapped() {
        when(repository.findDataset()).thenReturn(Optional.of(DATASET));
        when(strategy.selectTransporters(any(), anyInt()))
                .thenReturn(new AssignmentResult(List.of(), List.of(), 0));

        service.assign(10);

        verify(strategy).selectTransporters(DATASET, 4);
    }

    @Test
    void limitWithinRangeIsPassedThrough() {
        when(repository.findDataset()).thenReturn(Optional.of(DATASET));
        when(strategy.selectTransporters(any(), anyInt()))
                .thenReturn(new AssignmentResult(List.of(), List.of(), 0));

        service.assign(2);

        verify(strategy).selectTransporters(DATASET, 2);
    }

    @Test
    void infeasibleAssignmentIsPropagated() {
        when(repository.findDataset()).thenReturn(Optional.of(DATASET));
        InfeasibleAssignmentException infeasible = InfeasibleAssignmentException.belowMinimum(1, 2);
        when(strategy.selectTransporters(DATASET, 1)).thenThrow(infeasible);

        assertThatThrownBy(() -> service.assign(1)).isSameAs(infeasible);
    }

    @Test
    void strategyResultIsMappedToResponse() {
        when(repository.findDataset()).thenReturn(Optional.of(DATASET));
        AssignmentResult result = new AssignmentResult(
                List.of(new LaneAssignment(1, 1, 100), new LaneAssignment(2, 2, 100), new LaneAssignment(3, 1, 200)),
                List.of(1L, 2L), 400);
        when(strategy.selectTransporters(DATASET, 2)).thenReturn(result);

        AssignmentResponse response = service.assign(2);

        assertThat(response.status()).isEqualTo("success");
        assertThat(response.totalCost()).isEqualTo(400);
        assertThat(response.selectedTransporters()).containsExactly(1L, 2L);
        assertThat(response.assignments()).containsExactly(
                new LaneAssignmentResponse(1, 1, 100),
                new LaneAssignmentResponse(2, 2, 100),
                new LaneAssignmentResponse(3, 1, 200));
    }
}
