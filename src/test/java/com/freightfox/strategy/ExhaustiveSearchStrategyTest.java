package com.freightfox.strategy;

import com.freightfox.exception.InfeasibleAssignmentException;
import com.freightfox.model.AssignmentResult;
import com.freightfox.model.LaneAssignment;
import com.freightfox.model.TransporterDataset;
import com.freightfox.support.TestDatasets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExhaustiveSearchStrategyTest {

    private final ExhaustiveSearchStrategy strategy = new ExhaustiveSearchStrategy();

    private static long sumOfCosts(AssignmentResult result) {
        return result.assignments().stream().mapToLong(LaneAssignment::cost).sum();
    }

    @Nested
    @DisplayName("3 lanes x 4 transporters")
    class ThreeLanesFourTransporters {

        private final TransporterDataset dataset = TestDatasets.threeLanesFourTransporters();

        @Test
        void singleTransporterLimitPicksCheapestSingleCover() {
            AssignmentResult result = strategy.selectTransporters(dataset, 1);

            assertThat(result.totalCost()).isEqualTo(600);
            assertThat(result.selectedTransporterIds()).containsExactly(3L);
        }

        @Test
        void equalCostPairsResolveToLexicographicallySmallestIds() {
            AssignmentResult result = strategy.selectTransporters(dataset, 2);

            assertThat(result.totalCost()).isEqualTo(400);
            assertThat(result.selectedTransporterIds()).containsExactly(1L, 2L);
            assertThat(result.assignments()).containsExactly(
                    new LaneAssignment(1, 1, 100),
                    new LaneAssignment(2, 2, 100),
                    new LaneAssignment(3, 1, 200));
        }

        @ParameterizedTest(name = "K={0}")
        @ValueSource(ints = {3, 4})
        void largerLimitsReachTheGlobalOptimum(int k) {
            AssignmentResult result = strategy.selectTransporters(dataset, k);

            assertThat(result.totalCost()).isEqualTo(300);
            assertThat(result.selectedTransporterIds()).containsExactly(1L, 2L, 3L);
        }

        @Test
        void limitAboveTransporterCountIsTreatedAsAllTransporters() {
            AssignmentResult result = strategy.selectTransporters(dataset, 50);

            assertThat(result.totalCost()).isEqualTo(300);
            assertThat(result.selectedTransporterIds()).containsExactly(1L, 2L, 3L);
        }
    }

    @Nested
    @DisplayName("PDF Test Case 1 (5 lanes x 7 transporters)")
    class TestCase1 {

        private final TransporterDataset dataset = TestDatasets.testCase1();

        @Test
        void kOne() {
            AssignmentResult result = strategy.selectTransporters(dataset, 1);

            assertThat(result.totalCost()).isEqualTo(96_940);
            assertThat(result.selectedTransporterIds()).containsExactly(4L);
        }

        @Test
        void kTwo() {
            AssignmentResult result = strategy.selectTransporters(dataset, 2);

            assertThat(result.totalCost()).isEqualTo(62_938);
            assertThat(result.selectedTransporterIds()).containsExactly(1L, 4L);
        }

        @Test
        void kThreeMatchesHandVerifiedAssignment() {
            AssignmentResult result = strategy.selectTransporters(dataset, 3);

            assertThat(result.totalCost()).isEqualTo(60_139);
            assertThat(result.selectedTransporterIds()).containsExactly(1L, 4L, 5L);
            assertThat(result.assignments()).containsExactly(
                    new LaneAssignment(1, 5, 11_601),
                    new LaneAssignment(2, 1, 10_512),
                    new LaneAssignment(3, 4, 14_316),
                    new LaneAssignment(4, 4, 10_678),
                    new LaneAssignment(5, 4, 13_032));
            assertThat(sumOfCosts(result)).isEqualTo(result.totalCost());
        }

        @ParameterizedTest(name = "K={0} uses only four transporters")
        @ValueSource(ints = {4, 5, 6, 7})
        void limitsFromFourUpReachTheSameOptimum(int k) {
            AssignmentResult result = strategy.selectTransporters(dataset, k);

            assertThat(result.totalCost()).isEqualTo(59_896);
            assertThat(result.selectedTransporterIds()).containsExactly(1L, 3L, 4L, 5L);
        }
    }

    @Nested
    @DisplayName("PDF Test Case 2 (sparse quotes)")
    class TestCase2 {

        private final TransporterDataset dataset = TestDatasets.testCase2();

        @ParameterizedTest(name = "K={0}")
        @ValueSource(ints = {1, 2})
        void limitsBelowThreeAreInfeasible(int k) {
            assertThatThrownBy(() -> strategy.selectTransporters(dataset, k))
                    .isInstanceOf(InfeasibleAssignmentException.class)
                    .hasMessageContaining("Cannot cover all lanes with " + k)
                    .hasMessageEndingWith("(minimum required: 3)");
        }

        @Test
        void kTwoProducesPluralMessage() {
            assertThatThrownBy(() -> strategy.selectTransporters(dataset, 2))
                    .hasMessage("Cannot cover all lanes with 2 transporters (minimum required: 3)");
        }

        @Test
        void kThreeUsesXYZ() {
            AssignmentResult result = strategy.selectTransporters(dataset, 3);

            assertThat(result.totalCost()).isEqualTo(7_500);
            assertThat(result.selectedTransporterIds()).containsExactly(1L, 2L, 3L);
        }

        @Test
        void kFourAddsWForLaneFour() {
            AssignmentResult result = strategy.selectTransporters(dataset, 4);

            assertThat(result.totalCost()).isEqualTo(7_450);
            assertThat(result.selectedTransporterIds()).containsExactly(1L, 2L, 3L, 4L);
            assertThat(result.assignments()).contains(
                    new LaneAssignment(1, 1, 1_000),
                    new LaneAssignment(4, 4, 1_750));
        }
    }

    @Nested
    @DisplayName("PDF 10-lane API example")
    class TenLaneExample {

        private final TransporterDataset dataset = TestDatasets.pdfTenLaneExample();

        @ParameterizedTest(name = "K={0}")
        @ValueSource(ints = {3, 4})
        void sampleResponseWithThreeTransportersIsImpossible(int k) {
            assertThatThrownBy(() -> strategy.selectTransporters(dataset, k))
                    .isInstanceOf(InfeasibleAssignmentException.class)
                    .hasMessageEndingWith("(minimum required: 5)");
        }

        @Test
        void fiveTransportersCoverAllLanes() {
            AssignmentResult result = strategy.selectTransporters(dataset, 5);

            assertThat(result.totalCost()).isEqualTo(48_000);
            assertThat(result.selectedTransporterIds()).containsExactly(1L, 2L, 3L, 4L, 5L);
            assertThat(result.assignments()).hasSize(10);
        }
    }

    @Nested
    @DisplayName("9-lane x 7-transporter Excel dataset")
    class NineLaneDataset {

        private final TransporterDataset dataset = TestDatasets.nineLanes();

        @Test
        void kOne() {
            assertResult(strategy.selectTransporters(dataset, 1), 201_259, 1L);
        }

        @Test
        void kTwo() {
            assertResult(strategy.selectTransporters(dataset, 2), 148_402, 1L, 4L);
        }

        @Test
        void kThree() {
            assertResult(strategy.selectTransporters(dataset, 3), 134_876, 1L, 4L, 6L);
        }

        @Test
        void kFour() {
            assertResult(strategy.selectTransporters(dataset, 4), 127_844, 1L, 3L, 4L, 7L);
        }

        @ParameterizedTest(name = "K={0}")
        @ValueSource(ints = {5, 6, 7})
        void kFiveAndAbove(int k) {
            assertResult(strategy.selectTransporters(dataset, k), 125_045, 1L, 3L, 4L, 5L, 7L);
        }

        private void assertResult(AssignmentResult result, long expectedCost, Long... expectedTransporters) {
            assertThat(result.totalCost()).isEqualTo(expectedCost);
            assertThat(result.selectedTransporterIds()).containsExactly(expectedTransporters);
            assertThat(result.assignments()).extracting(LaneAssignment::laneId)
                    .containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L);
            assertThat(sumOfCosts(result)).isEqualTo(expectedCost);
        }
    }

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {

        @Test
        void combinationsMissingALaneQuoteAreSkipped() {
            TransporterDataset dataset = TestDatasets.fromQuotes(2, Map.of(
                    1L, Map.of(1L, 10L),
                    2L, Map.of(1L, 50L, 2L, 60L)));

            AssignmentResult single = strategy.selectTransporters(dataset, 1);
            AssignmentResult pair = strategy.selectTransporters(dataset, 2);

            assertThat(single.totalCost()).isEqualTo(110);
            assertThat(single.selectedTransporterIds()).containsExactly(2L);
            assertThat(pair.totalCost()).isEqualTo(70);
            assertThat(pair.selectedTransporterIds()).containsExactly(1L, 2L);
        }

        @Test
        void samePriceOnALaneGoesToLowestTransporterId() {
            TransporterDataset dataset = TestDatasets.fromMatrix(new long[][]{{100, 100}});

            AssignmentResult result = strategy.selectTransporters(dataset, 2);

            assertThat(result.assignments()).containsExactly(new LaneAssignment(1, 1, 100));
            assertThat(result.selectedTransporterIds()).containsExactly(1L);
        }

        @Test
        void equalTotalCostPrefersMoreTransportersUsed() {
            TransporterDataset dataset = TestDatasets.fromQuotes(2, Map.of(
                    1L, Map.of(1L, 100L, 2L, 100L),
                    2L, Map.of(1L, 100L),
                    3L, Map.of(2L, 100L)));

            assertThat(strategy.selectTransporters(dataset, 1).selectedTransporterIds()).containsExactly(1L);
            assertThat(strategy.selectTransporters(dataset, 2).selectedTransporterIds()).containsExactly(2L, 3L);
            assertThat(strategy.selectTransporters(dataset, 3).totalCost()).isEqualTo(200);
        }

        @Test
        void singleLaneSingleTransporter() {
            TransporterDataset dataset = TestDatasets.fromMatrix(new long[][]{{4_200}});

            AssignmentResult result = strategy.selectTransporters(dataset, 1);

            assertThat(result.totalCost()).isEqualTo(4_200);
            assertThat(result.selectedTransporterIds()).containsExactly(1L);
            assertThat(result.assignments()).containsExactly(new LaneAssignment(1, 1, 4_200));
        }

        @Test
        void laneWithoutAnyQuoteIsReportedAsInfeasible() {
            TransporterDataset dataset = TestDatasets.fromQuotes(2, Map.of(1L, Map.of(1L, 10L)));

            assertThatThrownBy(() -> strategy.selectTransporters(dataset, 1))
                    .isInstanceOf(InfeasibleAssignmentException.class)
                    .hasMessage("Lane 2 has no quotes");
        }

        @Test
        void limitBelowOneIsRejected() {
            TransporterDataset dataset = TestDatasets.threeLanesFourTransporters();

            assertThatThrownBy(() -> strategy.selectTransporters(dataset, 0))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void resultIsIndependentOfInputOrder() {
            TransporterDataset ordered = TestDatasets.testCase1();
            TransporterDataset reversed = new TransporterDataset(
                    ordered.lanes().reversed(), ordered.transporters().reversed());

            assertThat(strategy.selectTransporters(reversed, 3))
                    .isEqualTo(strategy.selectTransporters(ordered, 3));
        }

        @Test
        void resultCollectionsAreUnmodifiable() {
            AssignmentResult result = strategy.selectTransporters(TestDatasets.testCase1(), 3);

            assertThatThrownBy(() -> result.selectedTransporterIds().add(9L))
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> result.assignments().clear())
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }
}
