package algorithm;

import model.AlgorithmResult;
import model.Course;
import model.Graph;
import model.Schedule;
import model.SlotAssignment;

import java.util.*;

/**
 * Exact Branch-and-Bound Backtracking Solver for Capacity-Constrained Graph Coloring.
 * Employs:
 * - Initial DSatur Upper Bound
 * - Theoretical Lower Bounds (ceil(N/R) and verified Maximum Clique)
 * - Dynamic DSatur branching (highest saturation degree vertex selection)
 * - Symmetry breaking (at most one empty slot opened per branch)
 * - Remaining capacity pruning: ceil(remaining_unassigned / R)
 * - Time and Node limit safeguards with unproven optimality reporting upon timeout
 */
public class ExactBranchAndBoundScheduler implements Scheduler {

    private final long maxNodes;
    private final long timeoutMs;

    public ExactBranchAndBoundScheduler() {
        this(100_000L, 5_000L); // 100k nodes, 5s timeout default
    }

    public ExactBranchAndBoundScheduler(long maxNodes, long timeoutMs) {
        this.maxNodes = maxNodes;
        this.timeoutMs = timeoutMs;
    }

    @Override
    public String getName() {
        return "Exact Branch-and-Bound (Capacity-Constrained)";
    }

    @Override
    public AlgorithmResult schedule(Graph graph) {
        int n = graph.getNumCourses();
        int r = graph.getHallCapacity();

        int theoreticalLB = GraphUtils.computeTheoreticalLowerBound(graph);

        long startNano = System.nanoTime();
        long deadlineMs = System.currentTimeMillis() + timeoutMs;

        // 1. Initial heuristic upper bound via DSatur
        DSaturScheduler dsatur = new DSaturScheduler();
        AlgorithmResult initialHeuristic = dsatur.schedule(graph);
        int bestSlots = initialHeuristic.getSlotCount();
        Schedule bestSchedule = initialHeuristic.getSchedule().copy();
        List<SlotAssignment> bestSteps = new ArrayList<>(initialHeuristic.getAssignmentSteps());

        // Fast path: if initial heuristic already matches theoretical lower bound, it is proven optimal!
        if (bestSlots <= theoreticalLB) {
            long endNano = System.nanoTime();
            return new AlgorithmResult(
                getName(),
                bestSlots,
                endNano - startNano,
                bestSteps,
                bestSchedule,
                true,
                "Proven optimal: initial heuristic achieved theoretical lower bound LB=" + theoreticalLB + " with 0 search nodes.",
                1L,
                theoreticalLB
            );
        }

        // Branch and bound search state
        int[] courseSlots = new int[n + 1]; // 1-indexed, 0 = unassigned
        List<List<Integer>> slotContents = new ArrayList<>(); // slotContents.get(s - 1) = courses in slot s
        long[] nodesExplored = new long[]{0L};
        boolean[] timeoutReached = new boolean[]{false};
        boolean[] nodeLimitReached = new boolean[]{false};

        // Current best holder
        int[] bestUpperBound = new int[]{bestSlots};
        Schedule[] optimalScheduleHolder = new Schedule[]{bestSchedule};
        List<SlotAssignment> currentSteps = new ArrayList<>(n);
        List<SlotAssignment>[] bestStepsHolder = new List[]{bestSteps};

        // Launch recursive branch and bound
        search(
            graph,
            n,
            r,
            theoreticalLB,
            courseSlots,
            slotContents,
            0, // number of assigned courses
            bestUpperBound,
            optimalScheduleHolder,
            currentSteps,
            bestStepsHolder,
            nodesExplored,
            deadlineMs,
            timeoutReached,
            nodeLimitReached
        );

        long endNano = System.nanoTime();
        long runtimeNano = endNano - startNano;

        boolean provenOptimal;
        String optimalityReason;

        if (timeoutReached[0]) {
            provenOptimal = false;
            optimalityReason = "Time limit (" + timeoutMs + "ms) reached. Returned best known valid schedule with " +
                bestUpperBound[0] + " slots (LB=" + theoreticalLB + "). Optimality unproven.";
        } else if (nodeLimitReached[0]) {
            provenOptimal = false;
            optimalityReason = "Node limit (" + maxNodes + ") reached. Returned best known valid schedule with " +
                bestUpperBound[0] + " slots (LB=" + theoreticalLB + "). Optimality unproven.";
        } else {
            provenOptimal = true;
            optimalityReason = "Proven globally optimal: branch-and-bound exhaustively established that no valid schedule exists with fewer than " +
                bestUpperBound[0] + " slots (LB=" + theoreticalLB + ").";
        }

        return new AlgorithmResult(
            getName(),
            bestUpperBound[0],
            runtimeNano,
            bestStepsHolder[0],
            optimalScheduleHolder[0],
            provenOptimal,
            optimalityReason,
            nodesExplored[0],
            theoreticalLB
        );
    }

    private void search(
            Graph graph,
            int n,
            int r,
            int theoreticalLB,
            int[] courseSlots,
            List<List<Integer>> slotContents,
            int assignedCount,
            int[] bestUpperBound,
            Schedule[] optimalScheduleHolder,
            List<SlotAssignment> currentSteps,
            List<SlotAssignment>[] bestStepsHolder,
            long[] nodesExplored,
            long deadlineMs,
            boolean[] timeoutReached,
            boolean[] nodeLimitReached) {

        nodesExplored[0]++;

        // Check node limit on every node
        if (nodesExplored[0] >= maxNodes) {
            nodeLimitReached[0] = true;
            return;
        }

        // Check time cancellation limit every 64 nodes
        if ((nodesExplored[0] & 63) == 0) {
            if (System.currentTimeMillis() > deadlineMs) {
                timeoutReached[0] = true;
                return;
            }
        }

        // Base case: all courses assigned!
        if (assignedCount == n) {
            int usedSlots = slotContents.size();
            if (usedSlots < bestUpperBound[0]) {
                bestUpperBound[0] = usedSlots;
                Schedule sched = new Schedule(r);
                for (int c = 1; c <= n; c++) {
                    sched.assignCourse(c, courseSlots[c]);
                }
                optimalScheduleHolder[0] = sched;
                bestStepsHolder[0] = new ArrayList<>(currentSteps);
            }
            return;
        }

        int currentSlots = slotContents.size();

        // Pruning Rule 1: Current slots already equals or exceeds best upper bound
        if (currentSlots >= bestUpperBound[0]) {
            return;
        }

        // Pruning Rule 2: Remaining capacity bound
        int unassigned = n - assignedCount;
        int remainingCapInExisting = 0;
        for (List<Integer> list : slotContents) {
            remainingCapInExisting += (r - list.size());
        }
        int neededNewSlots = (unassigned > remainingCapInExisting) ?
            (int) Math.ceil((double) (unassigned - remainingCapInExisting) / r) : 0;

        if (currentSlots + neededNewSlots >= bestUpperBound[0]) {
            return;
        }

        // Dynamic DSatur vertex selection:
        // Choose unassigned vertex with highest saturation degree, tie-breaking on degree, then ID
        int bestVertex = -1;
        int maxSat = -1;
        int maxDeg = -1;

        for (int v = 1; v <= n; v++) {
            if (courseSlots[v] == 0) {
                int sat = computeVertexSaturation(graph, v, courseSlots);
                int deg = graph.getCourse(v).getDegree();

                if (sat > maxSat || (sat == maxSat && (deg > maxDeg || (deg == maxDeg && (bestVertex == -1 || v < bestVertex))))) {
                    maxSat = sat;
                    maxDeg = deg;
                    bestVertex = v;
                }
            }
        }

        if (bestVertex == -1) return;

        Course targetCourse = graph.getCourse(bestVertex);

        // Branch 1: Try placing bestVertex into existing slots 1..currentSlots
        for (int slotIdx = 0; slotIdx < currentSlots; slotIdx++) {
            List<Integer> coursesInSlot = slotContents.get(slotIdx);
            if (coursesInSlot.size() >= r) {
                continue; // Capacity full
            }

            // Conflict check
            boolean conflict = false;
            for (int occupant : coursesInSlot) {
                if (targetCourse.conflictsWith(occupant)) {
                    conflict = true;
                    break;
                }
            }

            if (!conflict) {
                int slotNum = slotIdx + 1;
                // Make assignment
                coursesInSlot.add(bestVertex);
                courseSlots[bestVertex] = slotNum;
                SlotAssignment step = new SlotAssignment(assignedCount + 1, bestVertex, slotNum, maxSat, maxDeg, "Explored in Slot " + slotNum);
                currentSteps.add(step);

                search(graph, n, r, theoreticalLB, courseSlots, slotContents, assignedCount + 1,
                    bestUpperBound, optimalScheduleHolder, currentSteps, bestStepsHolder, nodesExplored,
                    deadlineMs, timeoutReached, nodeLimitReached);

                // Backtrack
                currentSteps.remove(currentSteps.size() - 1);
                courseSlots[bestVertex] = 0;
                coursesInSlot.remove(coursesInSlot.size() - 1);

                if (timeoutReached[0] || nodeLimitReached[0]) return;
                // If bestUpperBound reached theoreticalLB, we can terminate early
                if (bestUpperBound[0] <= theoreticalLB) return;
            }
        }

        // Branch 2: Symmetry breaking - try placing bestVertex in a new slot (currentSlots + 1)
        // ONLY if opening a new slot remains strictly less than bestUpperBound
        if (currentSlots + 1 < bestUpperBound[0]) {
            int newSlotNum = currentSlots + 1;
            List<Integer> newSlotList = new ArrayList<>();
            newSlotList.add(bestVertex);
            slotContents.add(newSlotList);
            courseSlots[bestVertex] = newSlotNum;
            SlotAssignment step = new SlotAssignment(assignedCount + 1, bestVertex, newSlotNum, maxSat, maxDeg, "Opened new Slot " + newSlotNum);
            currentSteps.add(step);

            search(graph, n, r, theoreticalLB, courseSlots, slotContents, assignedCount + 1,
                bestUpperBound, optimalScheduleHolder, currentSteps, bestStepsHolder, nodesExplored,
                deadlineMs, timeoutReached, nodeLimitReached);

            // Backtrack
            currentSteps.remove(currentSteps.size() - 1);
            courseSlots[bestVertex] = 0;
            slotContents.remove(slotContents.size() - 1);
        }
    }

    private int computeVertexSaturation(Graph graph, int v, int[] courseSlots) {
        Course course = graph.getCourse(v);
        Set<Integer> slots = new HashSet<>();
        for (int nbr : course.getNeighborIds()) {
            int s = courseSlots[nbr];
            if (s > 0) {
                slots.add(s);
            }
        }
        return slots.size();
    }
}
