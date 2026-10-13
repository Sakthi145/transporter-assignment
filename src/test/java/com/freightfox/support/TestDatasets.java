package com.freightfox.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.freightfox.dto.request.TransporterInputRequest;
import com.freightfox.model.Lane;
import com.freightfox.model.LaneQuote;
import com.freightfox.model.Transporter;
import com.freightfox.model.TransporterDataset;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Datasets shared by the tests, including the cases from the assignment PDF and the 9-lane Excel data.
 */
public final class TestDatasets {

    public static final String NINE_LANE_RESOURCE = "/transporter_9_lanes.json";

    private TestDatasets() {
    }

    /**
     * Builds a dataset from a price matrix where {@code rows[lane][transporter]} is the quote and 0 means
     * "no quote". Lanes and transporters get IDs 1..n in row and column order.
     */
    public static TransporterDataset fromMatrix(long[][] rows) {
        int transporterCount = rows[0].length;
        List<Lane> lanes = new ArrayList<>();
        for (int lane = 0; lane < rows.length; lane++) {
            lanes.add(new Lane(lane + 1, "Origin " + (lane + 1), "Destination " + (lane + 1)));
        }
        List<Transporter> transporters = new ArrayList<>();
        for (int t = 0; t < transporterCount; t++) {
            List<LaneQuote> quotes = new ArrayList<>();
            for (int lane = 0; lane < rows.length; lane++) {
                if (rows[lane][t] > 0) {
                    quotes.add(new LaneQuote(lane + 1, rows[lane][t]));
                }
            }
            transporters.add(new Transporter(t + 1, "T" + (t + 1), quotes));
        }
        return new TransporterDataset(lanes, transporters);
    }

    /**
     * Builds a dataset from sparse quotes: {@code quotes.get(transporterId).get(laneId) = price}.
     */
    public static TransporterDataset fromQuotes(int laneCount, Map<Long, Map<Long, Long>> quotes) {
        List<Lane> lanes = new ArrayList<>();
        for (long id = 1; id <= laneCount; id++) {
            lanes.add(new Lane(id, "Origin " + id, "Destination " + id));
        }
        List<Transporter> transporters = new TreeMap<>(quotes).entrySet().stream()
                .map(entry -> new Transporter(entry.getKey(), "T" + entry.getKey(),
                        new TreeMap<>(entry.getValue()).entrySet().stream()
                                .map(quote -> new LaneQuote(quote.getKey(), quote.getValue()))
                                .toList()))
                .toList();
        return new TransporterDataset(lanes, transporters);
    }

    /** 3 lanes x 4 transporters with a deliberate cost tie between {T1,T2} and {T2,T3} at K=2. */
    public static TransporterDataset threeLanesFourTransporters() {
        return fromMatrix(new long[][]{
                {100, 300, 200, 500},
                {400, 100, 300, 200},
                {200, 400, 100, 300}});
    }

    /** The 9-lane x 7-transporter Excel dataset. */
    public static TransporterDataset nineLanes() {
        return toDataset(nineLaneRequest());
    }

    /** Test Case 1 of the PDF: the first five lanes of the Excel dataset. */
    public static TransporterDataset testCase1() {
        return fromMatrix(new long[][]{
                {20835, 48844, 39020, 14400, 11601, 35095, 26070},
                {10512, 31326, 20648, 44514, 19760, 12494, 41098},
                {22105, 18640, 31438, 14316, 40870, 17808, 20932},
                {42481, 45828, 36447, 10678, 20635, 36210, 16897},
                {19862, 18297, 12789, 13032, 26421, 39444, 27938}});
    }

    /** Test Case 2 of the PDF: X=1, Y=2, Z=3, W=4. */
    public static TransporterDataset testCase2() {
        return fromQuotes(5, Map.of(
                1L, Map.of(1L, 1000L, 2L, 1500L),
                2L, Map.of(3L, 2000L, 4L, 1800L),
                3L, Map.of(5L, 1200L),
                4L, Map.of(1L, 1100L, 4L, 1750L)));
    }

    /** The 10-lane sample input from the API specification section of the PDF. */
    public static TransporterDataset pdfTenLaneExample() {
        return fromQuotes(10, Map.of(
                1L, Map.of(1L, 5000L, 2L, 6000L),
                2L, Map.of(3L, 7000L, 4L, 4000L),
                3L, Map.of(5L, 3000L, 6L, 2000L),
                4L, Map.of(7L, 8000L, 8L, 5000L),
                5L, Map.of(9L, 4500L, 10L, 3500L),
                6L, Map.of(1L, 5500L, 2L, 6200L),
                7L, Map.of(3L, 7500L, 4L, 4500L)));
    }

    /** The raw JSON of the 9-lane dataset, as sent to the input API. */
    public static String nineLaneJson() {
        try (InputStream in = resource()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static TransporterInputRequest nineLaneRequest() {
        try (InputStream in = resource()) {
            return new ObjectMapper().readValue(in, TransporterInputRequest.class);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static InputStream resource() {
        InputStream in = TestDatasets.class.getResourceAsStream(NINE_LANE_RESOURCE);
        if (in == null) {
            throw new IllegalStateException("Missing test resource " + NINE_LANE_RESOURCE);
        }
        return in;
    }

    private static TransporterDataset toDataset(TransporterInputRequest request) {
        List<Lane> lanes = request.lanes().stream()
                .map(lane -> new Lane(lane.id(), lane.origin(), lane.destination()))
                .toList();
        List<Transporter> transporters = request.transporters().stream()
                .map(t -> new Transporter(t.id(), t.name(), t.laneQuotes().stream()
                        .map(q -> new LaneQuote(q.laneId(), q.quote()))
                        .toList()))
                .toList();
        return new TransporterDataset(lanes, transporters);
    }
}
