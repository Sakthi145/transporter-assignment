package com.freightfox.service;

import com.freightfox.dto.response.ApiResponse;
import com.freightfox.dto.response.AssignmentResponse;
import com.freightfox.dto.response.LaneAssignmentResponse;
import com.freightfox.exception.InvalidInputException;
import com.freightfox.exception.NoInputDataException;
import com.freightfox.model.AssignmentResult;
import com.freightfox.model.TransporterDataset;
import com.freightfox.repository.TransporterDataRepository;
import com.freightfox.strategy.TransporterSelectionStrategy;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Computes transporter assignments for the stored dataset using the configured selection strategy.
 */
@Service
public class TransporterAssignmentService {

    private final TransporterDataRepository repository;
    private final TransporterSelectionStrategy strategy;

    public TransporterAssignmentService(TransporterDataRepository repository, TransporterSelectionStrategy strategy) {
        this.repository = repository;
        this.strategy = strategy;
    }

    /**
     * Assigns transporters to all stored lanes using at most {@code maxTransporters} transporters.
     * A limit above the number of known transporters is capped to that number.
     *
     * @throws InvalidInputException                                   if {@code maxTransporters} is below 1
     * @throws NoInputDataException                                    if no input has been submitted
     * @throws com.freightfox.exception.InfeasibleAssignmentException if the lanes cannot be covered
     */
    public AssignmentResponse assign(int maxTransporters) {
        if (maxTransporters < 1) {
            throw new InvalidInputException("maxTransporters must be at least 1");
        }
        TransporterDataset dataset = repository.findDataset().orElseThrow(NoInputDataException::new);
        int effectiveLimit = Math.min(maxTransporters, dataset.transporters().size());
        return toResponse(strategy.selectTransporters(dataset, effectiveLimit));
    }

    private static AssignmentResponse toResponse(AssignmentResult result) {
        List<LaneAssignmentResponse> assignments = result.assignments().stream()
                .map(a -> new LaneAssignmentResponse(a.laneId(), a.transporterId(), a.cost()))
                .toList();
        return new AssignmentResponse(ApiResponse.SUCCESS, result.totalCost(), assignments,
                result.selectedTransporterIds());
    }
}
