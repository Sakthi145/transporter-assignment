package com.freightfox.strategy;

import com.freightfox.exception.InfeasibleAssignmentException;
import com.freightfox.model.AssignmentResult;
import com.freightfox.model.Lane;
import com.freightfox.model.LaneAssignment;
import com.freightfox.model.LaneQuote;
import com.freightfox.model.Transporter;
import com.freightfox.model.TransporterDataset;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Exact solver that evaluates every combination of 1..K transporters.
 *
 * <p>For each combination every lane is given its cheapest quote among the combination's members
 * (lowest transporter ID on equal prices); combinations that leave a lane without a quote are skipped.
 * The best combination is chosen by, in order: lowest total cost, then more distinct transporters actually
 * used, then the lexicographically smallest sorted list of used transporter IDs. The result is deterministic.
 *
 * <p><b>Complexity:</b> with {@code n} transporters, {@code L} lanes and limit {@code K} the search evaluates
 * {@code C(n,1) + ... + C(n,K)} combinations, each in {@code O(L * K)} time using a precomputed quote matrix.
 * This is exact and fast for small inputs (e.g. 7 transporters give at most 127 combinations), but grows
 * exponentially with {@code n}; for large inputs a different {@link TransporterSelectionStrategy}
 * (such as an ILP or heuristic solver) can be plugged in.
 */
@Component
public class ExhaustiveSearchStrategy implements TransporterSelectionStrategy {

    private static final long NO_QUOTE = -1L;

    @Override
    public AssignmentResult selectTransporters(TransporterDataset dataset, int maxTransporters) {
        if (maxTransporters < 1) {
            throw new IllegalArgumentException("maxTransporters must be at least 1");
        }
        QuoteMatrix matrix = QuoteMatrix.from(dataset);
        ensureEveryLaneHasAQuote(matrix);

        int limit = Math.min(maxTransporters, matrix.transporterCount());
        Candidate best = findBest(matrix, limit);
        if (best == null) {
            throw InfeasibleAssignmentException.belowMinimum(maxTransporters, minimumRequired(matrix, limit + 1));
        }
        return toResult(matrix, best);
    }

    private static void ensureEveryLaneHasAQuote(QuoteMatrix matrix) {
        for (int lane = 0; lane < matrix.laneCount(); lane++) {
            if (!matrix.hasAnyQuote(lane)) {
                throw new InfeasibleAssignmentException("Lane %d has no quotes".formatted(matrix.laneIds[lane]));
            }
        }
    }

    private static Candidate findBest(QuoteMatrix matrix, int limit) {
        Candidate best = null;
        for (int size = 1; size <= limit; size++) {
            int[] combination = firstCombination(size);
            do {
                Candidate candidate = evaluate(matrix, combination);
                if (candidate != null && (best == null || candidate.isBetterThan(best))) {
                    best = candidate;
                }
            } while (nextCombination(combination, matrix.transporterCount()));
        }
        return best;
    }

    /**
     * Smallest combination size that covers every lane. Sizes below {@code fromSize} are known to be infeasible.
     * Terminates because every lane has at least one quote, so all transporters together always cover all lanes.
     */
    private static int minimumRequired(QuoteMatrix matrix, int fromSize) {
        for (int size = fromSize; size < matrix.transporterCount(); size++) {
            int[] combination = firstCombination(size);
            do {
                if (evaluate(matrix, combination) != null) {
                    return size;
                }
            } while (nextCombination(combination, matrix.transporterCount()));
        }
        return matrix.transporterCount();
    }

    /**
     * Assigns each lane to its cheapest member of the combination, or returns {@code null} if a lane is uncovered.
     * Members are iterated in ascending ID order and only a strictly cheaper price replaces the current choice,
     * so equal prices resolve to the lowest transporter ID.
     */
    private static Candidate evaluate(QuoteMatrix matrix, int[] combination) {
        int[] chosen = new int[matrix.laneCount()];
        boolean[] used = new boolean[matrix.transporterCount()];
        long totalCost = 0;
        for (int lane = 0; lane < matrix.laneCount(); lane++) {
            int bestTransporter = cheapestMember(matrix, lane, combination);
            if (bestTransporter < 0) {
                return null;
            }
            chosen[lane] = bestTransporter;
            used[bestTransporter] = true;
            totalCost += matrix.price(lane, bestTransporter);
        }
        return new Candidate(totalCost, chosen, usedIndices(used));
    }

    private static int cheapestMember(QuoteMatrix matrix, int lane, int[] combination) {
        int bestTransporter = -1;
        long bestPrice = Long.MAX_VALUE;
        for (int transporter : combination) {
            long price = matrix.price(lane, transporter);
            if (price != NO_QUOTE && price < bestPrice) {
                bestPrice = price;
                bestTransporter = transporter;
            }
        }
        return bestTransporter;
    }

    private static int[] usedIndices(boolean[] used) {
        int count = 0;
        for (boolean isUsed : used) {
            if (isUsed) {
                count++;
            }
        }
        int[] indices = new int[count];
        int next = 0;
        for (int i = 0; i < used.length; i++) {
            if (used[i]) {
                indices[next++] = i;
            }
        }
        return indices;
    }

    private static int[] firstCombination(int size) {
        int[] combination = new int[size];
        for (int i = 0; i < size; i++) {
            combination[i] = i;
        }
        return combination;
    }

    /**
     * Advances {@code combination} to the next size-k subset of {@code 0..n-1} in lexicographic order.
     *
     * @return {@code false} when the last combination has already been reached
     */
    private static boolean nextCombination(int[] combination, int n) {
        int k = combination.length;
        int i = k - 1;
        while (i >= 0 && combination[i] == n - k + i) {
            i--;
        }
        if (i < 0) {
            return false;
        }
        combination[i]++;
        for (int j = i + 1; j < k; j++) {
            combination[j] = combination[j - 1] + 1;
        }
        return true;
    }

    private static AssignmentResult toResult(QuoteMatrix matrix, Candidate best) {
        List<LaneAssignment> assignments = new ArrayList<>(matrix.laneCount());
        for (int lane = 0; lane < matrix.laneCount(); lane++) {
            int transporter = best.chosenTransporters()[lane];
            assignments.add(new LaneAssignment(matrix.laneIds[lane], matrix.transporterIds[transporter],
                    matrix.price(lane, transporter)));
        }
        List<Long> selected = Arrays.stream(best.usedTransporters())
                .mapToObj(index -> matrix.transporterIds[index])
                .toList();
        return new AssignmentResult(assignments, selected, best.totalCost());
    }

    /**
     * A feasible combination outcome. Indices refer to {@link QuoteMatrix} positions, which are sorted by ID,
     * so comparing index arrays is equivalent to comparing transporter ID lists.
     */
    private record Candidate(long totalCost, int[] chosenTransporters, int[] usedTransporters) {

        boolean isBetterThan(Candidate other) {
            if (totalCost != other.totalCost) {
                return totalCost < other.totalCost;
            }
            if (usedTransporters.length != other.usedTransporters.length) {
                return usedTransporters.length > other.usedTransporters.length;
            }
            return Arrays.compare(usedTransporters, other.usedTransporters) < 0;
        }
    }

    /**
     * Dense price lookup with lanes and transporters sorted by ID; {@link #NO_QUOTE} marks a missing quote.
     */
    private static final class QuoteMatrix {

        private final long[] laneIds;
        private final long[] transporterIds;
        private final long[][] prices;

        private QuoteMatrix(long[] laneIds, long[] transporterIds, long[][] prices) {
            this.laneIds = laneIds;
            this.transporterIds = transporterIds;
            this.prices = prices;
        }

        static QuoteMatrix from(TransporterDataset dataset) {
            long[] laneIds = dataset.lanes().stream().mapToLong(Lane::id).sorted().toArray();
            List<Transporter> transporters = dataset.transporters().stream()
                    .sorted((a, b) -> Long.compare(a.id(), b.id()))
                    .toList();
            long[] transporterIds = transporters.stream().mapToLong(Transporter::id).toArray();

            Map<Long, Integer> laneIndex = new HashMap<>();
            for (int i = 0; i < laneIds.length; i++) {
                laneIndex.put(laneIds[i], i);
            }
            long[][] prices = new long[laneIds.length][transporterIds.length];
            for (long[] row : prices) {
                Arrays.fill(row, NO_QUOTE);
            }
            for (int t = 0; t < transporters.size(); t++) {
                for (LaneQuote quote : transporters.get(t).laneQuotes()) {
                    Integer lane = laneIndex.get(quote.laneId());
                    if (lane != null) {
                        prices[lane][t] = quote.quote();
                    }
                }
            }
            return new QuoteMatrix(laneIds, transporterIds, prices);
        }

        int laneCount() {
            return laneIds.length;
        }

        int transporterCount() {
            return transporterIds.length;
        }

        long price(int lane, int transporter) {
            return prices[lane][transporter];
        }

        boolean hasAnyQuote(int lane) {
            return Arrays.stream(prices[lane]).anyMatch(price -> price != NO_QUOTE);
        }
    }
}
