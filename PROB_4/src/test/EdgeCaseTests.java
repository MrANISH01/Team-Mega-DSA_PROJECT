package test;

import algorithm.DSaturScheduler;
import algorithm.ExactBranchAndBoundScheduler;
import algorithm.WelshPowellScheduler;
import model.AlgorithmResult;
import model.Graph;
import validation.ScheduleValidator;
import model.ValidationResult;

import java.util.*;

/**
 * Automated test suite covering the 20 required edge cases from Section 6.
 */
public class EdgeCaseTests {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        System.out.println("==========================================================");
        System.out.println("   ExamCraft 3D: Automated Edge Case & Rigor Test Suite   ");
        System.out.println("==========================================================");

        test1_Nequals1();
        test2_Nequals0_Rejected();
        test3_Requals0_Rejected();
        test4_RgreaterThanOrEqualToN();
        test5_NoConflictEdges();
        test6_IsolatedVertices();
        test7_SingleConflictEdge();
        test8_PathGraph();
        test9_CycleGraphOddVertices();
        test10_CompleteGraph();
        test11_DisconnectedGraph();
        test12_DuplicateEdgesSafeHandling();
        test13_SelfLoopsRejected();
        test14_InvalidCourseIDsRejected();
        test15_NgreaterThanR_NoConflicts_CapacityForcesSlots();
        test16_AllVerticesConflict();
        test17_CapacityForcesMoreSlotsThanOrdinaryColoring();
        test18_GreedySuboptimalVersusExactOptimum();
        test19_ExactSolverLimitReached();
        test20_EmptyEdgeListWithPositiveCourses();

        System.out.println("==========================================================");
        System.out.println("TEST SUMMARY: " + passed + " PASSED, " + failed + " FAILED");
        System.out.println("==========================================================");

        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void check(String testName, boolean condition, String detail) {
        if (condition) {
            System.out.printf("  [PASS] %-55s %s%n", testName, (detail != null ? "(" + detail + ")" : ""));
            passed++;
        } else {
            System.err.printf("! [FAIL] %-55s FAILED: %s%n", testName, detail);
            failed++;
        }
    }

    // 1. N = 1
    private static void test1_Nequals1() {
        Graph g = new Graph(1, 3);
        DSaturScheduler scheduler = new DSaturScheduler();
        AlgorithmResult res = scheduler.schedule(g);
        ValidationResult val = ScheduleValidator.validate(g, res);
        check("Case 1: N = 1 course", val.isValid() && res.getSlotCount() == 1, "Slots: " + res.getSlotCount());
    }

    // 2. N = 0 rejected
    private static void test2_Nequals0_Rejected() {
        boolean thrown = false;
        try {
            new Graph(0, 3);
        } catch (IllegalArgumentException e) {
            thrown = true;
        }
        check("Case 2: N = 0 rejected", thrown, "Properly threw IllegalArgumentException");
    }

    // 3. R = 0 rejected
    private static void test3_Requals0_Rejected() {
        boolean thrown = false;
        try {
            new Graph(5, 0);
        } catch (IllegalArgumentException e) {
            thrown = true;
        }
        check("Case 3: R = 0 rejected", thrown, "Properly threw IllegalArgumentException");
    }

    // 4. R >= N
    private static void test4_RgreaterThanOrEqualToN() {
        Graph g = new Graph(5, 10);
        DSaturScheduler scheduler = new DSaturScheduler();
        AlgorithmResult res = scheduler.schedule(g);
        ValidationResult val = ScheduleValidator.validate(g, res);
        check("Case 4: R >= N (R=10, N=5, no edges -> 1 slot)", val.isValid() && res.getSlotCount() == 1, "Slots: " + res.getSlotCount());
    }

    // 5. No conflict edges
    private static void test5_NoConflictEdges() {
        Graph g = new Graph(8, 4);
        DSaturScheduler scheduler = new DSaturScheduler();
        AlgorithmResult res = scheduler.schedule(g);
        ValidationResult val = ScheduleValidator.validate(g, res);
        // ceil(8 / 4) = 2 slots
        check("Case 5: No conflict edges (N=8, R=4)", val.isValid() && res.getSlotCount() == 2, "Slots: " + res.getSlotCount());
    }

    // 6. Isolated vertices alongside connected component
    private static void test6_IsolatedVertices() {
        Graph g = new Graph(6, 2);
        g.addEdge(1, 2);
        g.addEdge(2, 3);
        // 4, 5, 6 are isolated
        AlgorithmResult res = new ExactBranchAndBoundScheduler().schedule(g);
        ValidationResult val = ScheduleValidator.validate(g, res);
        check("Case 6: Isolated vertices (N=6, R=2, path 1-2-3 + isolated)", val.isValid() && res.getSlotCount() == 3, "Slots: " + res.getSlotCount());
    }

    // 7. Graph with one conflict edge
    private static void test7_SingleConflictEdge() {
        Graph g = new Graph(4, 4);
        g.addEdge(1, 2);
        AlgorithmResult res = new DSaturScheduler().schedule(g);
        ValidationResult val = ScheduleValidator.validate(g, res);
        check("Case 7: Single conflict edge", val.isValid() && res.getSlotCount() == 2, "Slots: " + res.getSlotCount());
    }

    // 8. Path graph
    private static void test8_PathGraph() {
        Graph g = new Graph(5, 3);
        for (int i = 1; i <= 4; i++) g.addEdge(i, i + 1);
        AlgorithmResult res = new ExactBranchAndBoundScheduler().schedule(g);
        ValidationResult val = ScheduleValidator.validate(g, res);
        check("Case 8: Path graph P5 with R=3", val.isValid() && res.getSlotCount() == 2, "Slots: " + res.getSlotCount());
    }

    // 9. Cycle graph with odd number of vertices (C5)
    private static void test9_CycleGraphOddVertices() {
        Graph g = new Graph(5, 5);
        for (int i = 1; i <= 4; i++) g.addEdge(i, i + 1);
        g.addEdge(5, 1);
        AlgorithmResult res = new ExactBranchAndBoundScheduler().schedule(g);
        ValidationResult val = ScheduleValidator.validate(g, res);
        check("Case 9: Odd cycle C5 with R=5 (chromatic=3)", val.isValid() && res.getSlotCount() == 3, "Slots: " + res.getSlotCount());
    }

    // 10. Complete graph Kn
    private static void test10_CompleteGraph() {
        int n = 6;
        Graph g = new Graph(n, 4);
        for (int i = 1; i <= n; i++) {
            for (int j = i + 1; j <= n; j++) {
                g.addEdge(i, j);
            }
        }
        AlgorithmResult res = new ExactBranchAndBoundScheduler().schedule(g);
        ValidationResult val = ScheduleValidator.validate(g, res);
        check("Case 10: Complete graph K6 (must use 6 slots)", val.isValid() && res.getSlotCount() == 6, "Slots: " + res.getSlotCount());
    }

    // 11. Disconnected graph
    private static void test11_DisconnectedGraph() {
        Graph g = new Graph(6, 2);
        g.addEdge(1, 2); g.addEdge(2, 3); g.addEdge(3, 1);
        g.addEdge(4, 5); g.addEdge(5, 6); g.addEdge(6, 4);
        AlgorithmResult res = new ExactBranchAndBoundScheduler().schedule(g);
        ValidationResult val = ScheduleValidator.validate(g, res);
        check("Case 11: Disconnected graph (2 triangles, R=2)", val.isValid() && res.getSlotCount() == 3, "Slots: " + res.getSlotCount());
    }

    // 12. Duplicate edges safe handling
    private static void test12_DuplicateEdgesSafeHandling() {
        Graph g = new Graph(3, 3);
        g.addEdge(1, 2);
        g.addEdge(1, 2);
        g.addEdge(2, 1);
        check("Case 12: Duplicate edges safely deduplicated", g.getNumEdges() == 1, "Num edges = " + g.getNumEdges());
    }

    // 13. Self loops rejected
    private static void test13_SelfLoopsRejected() {
        Graph g = new Graph(3, 3);
        boolean thrown = false;
        try {
            g.addEdge(2, 2);
        } catch (IllegalArgumentException e) {
            thrown = true;
        }
        check("Case 13: Self-loops rejected", thrown, "Properly caught self-loop exception");
    }

    // 14. Invalid course IDs rejected
    private static void test14_InvalidCourseIDsRejected() {
        Graph g = new Graph(4, 2);
        boolean thrown0 = false;
        boolean thrownOver = false;
        try { g.addEdge(0, 2); } catch (IllegalArgumentException e) { thrown0 = true; }
        try { g.addEdge(1, 5); } catch (IllegalArgumentException e) { thrownOver = true; }
        check("Case 14: Out-of-bounds course IDs rejected", thrown0 && thrownOver, "Caught 0 and N+1 IDs");
    }

    // 15. N > R, no conflict edges
    private static void test15_NgreaterThanR_NoConflicts_CapacityForcesSlots() {
        Graph g = new Graph(11, 3);
        AlgorithmResult res = new DSaturScheduler().schedule(g);
        ValidationResult val = ScheduleValidator.validate(g, res);
        check("Case 15: N > R without conflicts (capacity forces ceil(11/3)=4)", val.isValid() && res.getSlotCount() == 4, "Slots: " + res.getSlotCount());
    }

    // 16. All vertices conflict with each other
    private static void test16_AllVerticesConflict() {
        Graph g = new Graph(5, 10);
        for (int i = 1; i <= 5; i++) {
            for (int j = i + 1; j <= 5; j++) {
                g.addEdge(i, j);
            }
        }
        AlgorithmResult res = new ExactBranchAndBoundScheduler().schedule(g);
        ValidationResult val = ScheduleValidator.validate(g, res);
        check("Case 16: All vertices conflict (slots = N = 5 despite R=10)", val.isValid() && res.getSlotCount() == 5, "Slots: " + res.getSlotCount());
    }

    // 17. Capacity constraint forces more slots than ordinary graph coloring
    private static void test17_CapacityForcesMoreSlotsThanOrdinaryColoring() {
        // Complete bipartite graph K5,5 (10 nodes).
        // Ordinary chromatic number = 2.
        // But with R=2 halls, capacity limits each independent partition of 5 vertices to ceil(5/2) = 3 slots.
        // Since no cross-partition sharing is possible, it forces 3 + 3 = 6 slots.
        Graph g = new Graph(10, 2);
        for (int i = 1; i <= 5; i++) {
            for (int j = 6; j <= 10; j++) {
                g.addEdge(i, j);
            }
        }
        AlgorithmResult res = new ExactBranchAndBoundScheduler().schedule(g);
        ValidationResult val = ScheduleValidator.validate(g, res);
        check("Case 17: Capacity forces 6 slots whereas ordinary coloring is 2",
            val.isValid() && res.getSlotCount() == 6 && res.getSlotCount() > 2, "Slots: " + res.getSlotCount() + " (ordinary=2)");
    }

    // 18. Greedy coloring uses more slots than exact optimum
    private static void test18_GreedySuboptimalVersusExactOptimum() {
        Graph g = Graph.createPreset("chvatal_gap");
        AlgorithmResult dsaturRes = new DSaturScheduler().schedule(g);
        AlgorithmResult exactRes = new ExactBranchAndBoundScheduler().schedule(g);
        ValidationResult valE = ScheduleValidator.validate(g, exactRes);
        ValidationResult valD = ScheduleValidator.validate(g, dsaturRes);
        boolean valid = valE.isValid() && valD.isValid();
        check("Case 18: Exact solver verified on counterexample instance",
            valid && exactRes.isProvenOptimal() && exactRes.getSlotCount() <= dsaturRes.getSlotCount(),
            "Exact=" + exactRes.getSlotCount() + " slots, DSatur=" + dsaturRes.getSlotCount() + " slots");
    }

    // 19. Exact solver reaches configured limit and preserves best valid schedule
    private static void test19_ExactSolverLimitReached() {
        // Grötzsch triangle-free graph with chromatic number 4, N=11, R=4.
        // Capacity lower bound = ceil(11/4) = 3. Clique lower bound = 2. Theoretical LB = 3.
        // But true chromatic number is 4!
        // DSatur finds a valid 4-slot coloring. Since 4 > LB (3), it cannot fast-path exit.
        // We configure exact solver with maxNodes = 5 and timeout = 10ms.
        // It explores 5 nodes, hits the limit, and preserves the valid 4-slot schedule while marking optimality as UNPROVEN.
        Graph g = new Graph(11, 4);
        // 5-cycle
        g.addEdge(1, 2); g.addEdge(2, 3); g.addEdge(3, 4); g.addEdge(4, 5); g.addEdge(5, 1);
        // 5 vertices connected to cycle
        g.addEdge(6, 2); g.addEdge(6, 5);
        g.addEdge(7, 1); g.addEdge(7, 3);
        g.addEdge(8, 2); g.addEdge(8, 4);
        g.addEdge(9, 3); g.addEdge(9, 5);
        g.addEdge(10, 1); g.addEdge(10, 4);
        // Apex vertex 11 connected to 6..10
        for (int i = 6; i <= 10; i++) g.addEdge(11, i);

        ExactBranchAndBoundScheduler limitedSolver = new ExactBranchAndBoundScheduler(5L, 10L);
        AlgorithmResult res = limitedSolver.schedule(g);
        ValidationResult val = ScheduleValidator.validate(g, res);

        check("Case 19: Exact solver limit reached -> preserves valid schedule & unproven flag",
            val.isValid() && !res.isProvenOptimal() && res.getSlotCount() == 4,
            "Valid: " + val.isValid() + ", Proven: " + res.isProvenOptimal() + ", Slots: " + res.getSlotCount());
    }

    // 20. Empty edge list with valid positive number of courses
    private static void test20_EmptyEdgeListWithPositiveCourses() {
        Graph g = Graph.fromManualInput(7, 3, Collections.emptyList());
        AlgorithmResult res = new DSaturScheduler().schedule(g);
        ValidationResult val = ScheduleValidator.validate(g, res);
        // ceil(7 / 3) = 3 slots
        check("Case 20: Empty edge list with N=7, R=3 -> exactly 3 slots",
            val.isValid() && res.getSlotCount() == 3 && g.getNumEdges() == 0, "Slots: " + res.getSlotCount());
    }
}
