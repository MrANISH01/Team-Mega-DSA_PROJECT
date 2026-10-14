package algorithm;

import model.AlgorithmResult;
import model.Course;
import model.Graph;
import model.Schedule;
import model.SlotAssignment;

import java.util.*;

/**
 * Welsh-Powell greedy coloring adapted with hall capacity R constraints.
 * Vertices are ordered strictly by descending degree, with ascending course ID for tie-breaking.
 */
public class WelshPowellScheduler implements Scheduler {

    @Override
    public String getName() {
        return "Welsh-Powell Greedy (Capacity-Constrained)";
    }

    @Override
    public AlgorithmResult schedule(Graph graph) {
        int n = graph.getNumCourses();
        int r = graph.getHallCapacity();

        int lowerBound = GraphUtils.computeTheoreticalLowerBound(graph);

        long startNano = System.nanoTime();

        // 1. Sort vertices by descending degree, tie-break ascending course ID
        List<Course> sortedCourses = new ArrayList<>(graph.getCourses());
        sortedCourses.sort((c1, c2) -> {
            int degComp = Integer.compare(c2.getDegree(), c1.getDegree());
            if (degComp != 0) return degComp;
            return Integer.compare(c1.getId(), c2.getId());
        });

        Schedule schedule = new Schedule(r);
        List<SlotAssignment> steps = new ArrayList<>(n);
        Map<Integer, Integer> assignedSlots = new HashMap<>(n);

        int stepCounter = 1;

        for (Course course : sortedCourses) {
            int courseId = course.getId();
            int deg = course.getDegree();
            int sat = GraphUtils.computeSaturationDegree(graph, courseId, assignedSlots);

            // Find lowest compatible slot with remaining capacity
            int chosenSlot = -1;
            int currentMaxSlot = schedule.getMaxSlotNumber();

            for (int slot = 1; slot <= currentMaxSlot; slot++) {
                List<Integer> coursesInSlot = schedule.getCoursesInSlot(slot);
                if (coursesInSlot.size() >= r) {
                    continue; // Capacity full
                }
                boolean conflict = false;
                for (int occupiedCourseId : coursesInSlot) {
                    if (course.conflictsWith(occupiedCourseId)) {
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
                chosenSlot = currentMaxSlot + 1;
            }

            schedule.assignCourse(courseId, chosenSlot);
            assignedSlots.put(courseId, chosenSlot);

            String reason = "Assigned to Slot " + chosenSlot + " (deg=" + deg + ", tie-break rank)";
            steps.add(new SlotAssignment(stepCounter++, courseId, chosenSlot, sat, deg, reason));
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
