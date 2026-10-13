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
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Validates submitted input across fields, maps it to the domain model and stores it,
 * replacing any previously submitted dataset.
 */
@Service
public class TransporterInputService {

    private final TransporterDataRepository repository;

    public TransporterInputService(TransporterDataRepository repository) {
        this.repository = repository;
    }

    /**
     * Validates and saves the input.
     *
     * @throws InvalidInputException listing every rule violation found
     */
    public void saveInput(TransporterInputRequest request) {
        List<String> errors = validate(request);
        if (!errors.isEmpty()) {
            throw new InvalidInputException("Invalid input data", errors);
        }
        repository.save(toDataset(request));
    }

    private static List<String> validate(TransporterInputRequest request) {
        List<String> errors = new ArrayList<>();
        Set<Long> laneIds = collectLaneIds(request.lanes(), errors);
        Set<Long> quotedLaneIds = validateTransporters(request.transporters(), laneIds, errors);
        laneIds.stream()
                .filter(laneId -> !quotedLaneIds.contains(laneId))
                .forEach(laneId -> errors.add("Lane %d has no quotes".formatted(laneId)));
        return errors;
    }

    private static Set<Long> collectLaneIds(List<LaneRequest> lanes, List<String> errors) {
        Set<Long> laneIds = new LinkedHashSet<>();
        for (LaneRequest lane : lanes) {
            if (!laneIds.add(lane.id())) {
                errors.add("Duplicate lane id: %d".formatted(lane.id()));
            }
        }
        return laneIds;
    }

    private static Set<Long> validateTransporters(List<TransporterRequest> transporters, Set<Long> laneIds,
                                                  List<String> errors) {
        Set<Long> transporterIds = new HashSet<>();
        Set<Long> quotedLaneIds = new HashSet<>();
        for (TransporterRequest transporter : transporters) {
            if (!transporterIds.add(transporter.id())) {
                errors.add("Duplicate transporter id: %d".formatted(transporter.id()));
            }
            validateQuotes(transporter, laneIds, errors);
            transporter.laneQuotes().forEach(quote -> quotedLaneIds.add(quote.laneId()));
        }
        return quotedLaneIds;
    }

    private static void validateQuotes(TransporterRequest transporter, Set<Long> laneIds, List<String> errors) {
        Set<Long> seenLaneIds = new HashSet<>();
        for (LaneQuoteRequest quote : transporter.laneQuotes()) {
            if (!laneIds.contains(quote.laneId())) {
                errors.add("Transporter %d quotes unknown lane id: %d".formatted(transporter.id(), quote.laneId()));
            }
            if (!seenLaneIds.add(quote.laneId())) {
                errors.add("Transporter %d quotes lane %d more than once".formatted(transporter.id(), quote.laneId()));
            }
            if (quote.quote() <= 0) {
                errors.add("Transporter %d has a non-positive quote for lane %d"
                        .formatted(transporter.id(), quote.laneId()));
            }
        }
    }

    private static TransporterDataset toDataset(TransporterInputRequest request) {
        List<Lane> lanes = request.lanes().stream()
                .map(lane -> new Lane(lane.id(), lane.origin().trim(), lane.destination().trim()))
                .toList();
        List<Transporter> transporters = request.transporters().stream()
                .map(TransporterInputService::toTransporter)
                .toList();
        return new TransporterDataset(lanes, transporters);
    }

    private static Transporter toTransporter(TransporterRequest transporter) {
        List<LaneQuote> quotes = transporter.laneQuotes().stream()
                .map(quote -> new LaneQuote(quote.laneId(), quote.quote()))
                .toList();
        return new Transporter(transporter.id(), transporter.name().trim(), quotes);
    }
}
