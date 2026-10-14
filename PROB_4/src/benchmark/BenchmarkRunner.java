package benchmark;

import algorithm.DSaturScheduler;
import algorithm.ExactBranchAndBoundScheduler;
import algorithm.WelshPowellScheduler;
import model.AlgorithmResult;
import model.BenchmarkPoint;
import model.Graph;
import validation.ScheduleValidator;

import java.util.*;

/**
 * Executes rigorous benchmark experiments comparing DSatur, Welsh-Powell, and Exact Branch-and-Bound.
 * Excludes graph generation and network overhead from timing.
 */
public final class BenchmarkRunner {

    private BenchmarkRunner() {}

    public static class ComparisonReport {
        private final Graph graph;
        private final AlgorithmResult dsaturResult;
        private final AlgorithmResult welshPowellResult;
        private final AlgorithmResult exactResult;

        public ComparisonReport(Graph graph, AlgorithmResult dsaturResult, AlgorithmResult welshPowellResult, AlgorithmResult exactResult) {
            this.graph = graph;
            this.dsaturResult = dsaturResult;
            this.welshPowellResult = welshPowellResult;
            this.exactResult = exactResult;
        }

        public Graph getGraph() { return graph; }
        public AlgorithmResult getDsaturResult() { return dsaturResult; }
        public AlgorithmResult getWelshPowellResult() { return welshPowellResult; }
        public AlgorithmResult getExactResult() { return exactResult; }
    }

    /**
     * Runs all three algorithms on the same graph instance and validates each.
     */
    public static ComparisonReport compareAll(Graph graph, long exactTimeoutMs, long exactMaxNodes) {
        DSaturScheduler dsatur = new DSaturScheduler();
        WelshPowellScheduler welsh = new WelshPowellScheduler();
        ExactBranchAndBoundScheduler exact = new ExactBranchAndBoundScheduler(exactMaxNodes, exactTimeoutMs);

        // Run DSatur
        AlgorithmResult dsaturRes = dsatur.schedule(graph);
        dsaturRes.setValidation(ScheduleValidator.validate(graph, dsaturRes));

        // Run Welsh-Powell
        AlgorithmResult welshRes = welsh.schedule(graph);
        welshRes.setValidation(ScheduleValidator.validate(graph, welshRes));

        // Run Exact Solver
        AlgorithmResult exactRes = exact.schedule(graph);
        exactRes.setValidation(ScheduleValidator.validate(graph, exactRes));

        // If exact optimum is proven, calculate heuristic gap
        if (exactRes.isProvenOptimal()) {
            int optimalSlots = exactRes.getSlotCount();
            dsaturRes.setGapFromOptimum(dsaturRes.getSlotCount() - optimalSlots);
            welshRes.setGapFromOptimum(welshRes.getSlotCount() - optimalSlots);
            exactRes.setGapFromOptimum(0);
        } else {
            dsaturRes.setGapFromOptimum(null);
            welshRes.setGapFromOptimum(null);
            exactRes.setGapFromOptimum(null);
        }

        return new ComparisonReport(graph, dsaturRes, welshRes, exactRes);
    }

    /**
     * Executes scaling benchmark across increasing N values.
     */
    public static List<BenchmarkPoint> runScalingBenchmark(int[] nValues, int r, double density, long seed) {
        List<BenchmarkPoint> points = new ArrayList<>();
        DSaturScheduler dsatur = new DSaturScheduler();
        WelshPowellScheduler welsh = new WelshPowellScheduler();

        for (int n : nValues) {
            Graph g = Graph.generateRandom(n, r, density, seed + n);

            // DSatur
            AlgorithmResult dRes = dsatur.schedule(g);

            // Welsh-Powell
            AlgorithmResult wRes = welsh.schedule(g);

            // Exact Solver (run with modest node limit for scaling safety)
            Integer exactSlots = null;
            Double exactTimeMs = null;
            Boolean exactProven = null;
            Long exactNodes = null;

            if (n <= 24) {
                ExactBranchAndBoundScheduler exact = new ExactBranchAndBoundScheduler(25_000L, 2_000L);
                AlgorithmResult eRes = exact.schedule(g);
                exactSlots = eRes.getSlotCount();
                exactTimeMs = eRes.getRuntimeMs();
                exactProven = eRes.isProvenOptimal();
                exactNodes = eRes.getNodesExplored();
            }

            points.add(new BenchmarkPoint(
                n,
                r,
                g.getNumEdges(),
                g.getEdgeDensity(),
                dRes.getSlotCount(),
                dRes.getRuntimeMs(),
                wRes.getSlotCount(),
                wRes.getRuntimeMs(),
                exactSlots,
                exactTimeMs,
                exactProven,
                exactNodes
            ));
        }

        return points;
    }
}
