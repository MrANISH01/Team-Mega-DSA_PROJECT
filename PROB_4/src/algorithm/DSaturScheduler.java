package algorithm;

import model.AlgorithmResult;
import model.Course;
import model.Graph;
import model.Schedule;
import model.SlotAssignment;

import java.util.*;

/**
 * DSatur (Degree of Saturation) greedy coloring adapted with hall capacity R constraints.
 * Deterministic tie-breakers:
 * 1. Highest saturation degree
 * 2. Highest ordinary degree
 * 3. Lowest course ID
 */
public class DSaturScheduler implements Scheduler {

    @Override
    public String getName() {
        return "DSatur Greedy (Capacity-Constrained)";
    }

    @Override
    public AlgorithmResult schedule(Graph graph) {
        int n = graph.getNumCourses();
        int r = graph.getHallCapacity();

        int lowerBound = GraphUtils.computeTheoreticalLowerBound(graph);

        long startNano = System.nanoTime();

        Schedule schedule = new Schedule(r);
        List<SlotAssignment> steps = new ArrayList<>(n);

        Map<Integer, Integer> assignedSlots = new HashMap<>(n);
        Set<Integer> unassigned = new TreeSet<>();
        for (int i = 1; i <= n; i++) {
            unassigned.add(i);
        }

        // Precompute degrees
        Map<Integer, Integer> ordinaryDegrees = new HashMap<>(n);
        for (Course c : graph.getCourses()) {
            ordinaryDegrees.put(c.getId(), c.getDegree());
        }

        int stepCounter = 1;

        while (!unassigned.isEmpty()) {
            // Select course using deterministic criteria
            int bestCourseId = -1;
            int maxSat = -1;
            int maxDeg = -1;

            for (int courseId : unassigned) {
                int sat = GraphUtils.computeSaturationDegree(graph, courseId, assignedSlots);
                int deg = ordinaryDegrees.get(courseId);

                boolean better = false;
                if (sat > maxSat) {
                    better = true;
                } else if (sat == maxSat) {
                    if (deg > maxDeg) {
                        better = true;
                    } else if (deg == maxDeg) {
                        if (bestCourseId == -1 || courseId < bestCourseId) {
                            better = true;
                        }
                    }
                }

                if (better) {
                    bestCourseId = courseId;
                    maxSat = sat;
                    maxDeg = deg;
                }
            }

            // Find lowest available slot that satisfies:
            // 1. No conflict with existing courses in that slot
            // 2. Slot occupancy < R
            int chosenSlot = -1;
            int currentMaxSlot = schedule.getMaxSlotNumber();
            Course targetCourse = graph.getCourse(bestCourseId);

            for (int slot = 1; slot <= currentMaxSlot; slot++) {
                List<Integer> coursesInSlot = schedule.getCoursesInSlot(slot);
                if (coursesInSlot.size() >= r) {
                    continue; // Capacity full
                }
                boolean conflict = false;
                for (int occupiedCourseId : coursesInSlot) {
                    if (targetCourse.conflictsWith(occupiedCourseId)) {
                        conflict = true;
                        break;
                    }
                }
                if (!conflict) {
                    chosenSlot = slot;
                    break;
                }
            }

            if (chosenSlot == -1) {
                // Must open a new slot
                chosenSlot = currentMaxSlot + 1;
            }

            schedule.assignCourse(bestCourseId, chosenSlot);
            assignedSlots.put(bestCourseId, chosenSlot);
            unassigned.remove(bestCourseId);

            String reason = "Assigned to Slot " + chosenSlot + " (sat=" + maxSat + ", deg=" + maxDeg + ")";
            steps.add(new SlotAssignment(stepCounter++, bestCourseId, chosenSlot, maxSat, maxDeg, reason));
        }

        long endNano = System.nanoTime();
        long runtimeNano = endNano - startNano;

        int totalSlots = schedule.getTotalSlots();
        boolean provenOptimal = false;
        String optimalityReason;

        if (totalSlots == lowerBound) {
            provenOptimal = true;
            optimalityReason = "Proven optimal: slot count (" + totalSlots + ") matches theoretical lower bound LB=" + lowerBound + ".";
        } else {
            optimalityReason = "Heuristic solution: validity verified, but global optimality unproven (LB=" + lowerBound + ").";
        }

        return new AlgorithmResult(
            getName(),
            totalSlots,
            runtimeNano,
            steps,
            schedule,
            provenOptimal,
            optimalityReason,
            0L,
            lowerBound
        );
    }
}
