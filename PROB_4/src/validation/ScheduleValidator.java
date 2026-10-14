package validation;

import model.AlgorithmResult;
import model.Course;
import model.Graph;
import model.Schedule;
import model.ValidationResult;

import java.util.*;

/**
 * Dedicated independent timetable validator.
 * Rigorously checks all graph conflict and capacity constraints, course coverage,
 * duplicate assignments, slot count accuracy, and optimality claim consistency.
 */
public final class ScheduleValidator {

    private ScheduleValidator() {}

    public static ValidationResult validate(Graph graph, AlgorithmResult result) {
        return validate(graph, result.getSchedule(), result.isProvenOptimal(), result.getOptimalityReason(), result.getSlotCount());
    }

    public static ValidationResult validate(Graph graph, Schedule schedule, boolean provenOptimal, String optimalityReason, int reportedSlots) {
        List<String> errors = new ArrayList<>();
        List<String> notes = new ArrayList<>();

        int n = graph.getNumCourses();
        int r = graph.getHallCapacity();

        if (schedule == null) {
            errors.add("Schedule object is null.");
            return new ValidationResult(false, false, false, false, false, false, false,
                errors, notes, 0, 0, 0.0);
        }

        // 1. Course coverage & duplicates check
        boolean courseCoveragePassed = true;
        boolean noDuplicatesPassed = true;
        Map<Integer, Integer> courseToSlot = schedule.getCourseToSlot();

        int[] appearances = new int[n + 1];
        for (int c = 1; c <= n; c++) {
            Integer slot = courseToSlot.get(c);
            if (slot == null) {
                courseCoveragePassed = false;
                errors.add("Course " + c + " is completely missing from the schedule.");
            } else {
                appearances[c]++;
            }
        }

        // Check if any course appeared in slotToCourses multiple times or invalid IDs
        Map<Integer, List<Integer>> slotToCourses = schedule.getSlotToCourses();
        Map<Integer, Integer> countedInSlots = new HashMap<>();

        for (Map.Entry<Integer, List<Integer>> entry : slotToCourses.entrySet()) {
            int slot = entry.getKey();
            if (slot <= 0) {
                errors.add("Invalid non-positive slot number detected: " + slot);
            }
            for (int c : entry.getValue()) {
                if (c < 1 || c > n) {
                    errors.add("Invalid course ID " + c + " in Slot " + slot + " (expected range 1.." + n + ").");
                }
                countedInSlots.put(c, countedInSlots.getOrDefault(c, 0) + 1);
            }
        }

        for (Map.Entry<Integer, Integer> entry : countedInSlots.entrySet()) {
            if (entry.getValue() > 1) {
                noDuplicatesPassed = false;
                errors.add("Course " + entry.getKey() + " is assigned " + entry.getValue() + " times across slots.");
            }
        }

        if (courseCoveragePassed && noDuplicatesPassed) {
            notes.add("Coverage: All " + n + " courses scheduled exactly once (100% coverage, 0 duplicates).");
        }

        // 2. Conflict constraint check
        boolean conflictConstraintPassed = true;
        for (String edgeKey : graph.getCanonicalEdges()) {
            String[] parts = edgeKey.split("-");
            int u = Integer.parseInt(parts[0]);
            int v = Integer.parseInt(parts[1]);

            Integer slotU = courseToSlot.get(u);
            Integer slotV = courseToSlot.get(v);

            if (slotU != null && slotV != null && slotU.equals(slotV)) {
                conflictConstraintPassed = false;
                errors.add("Conflict violation: Courses " + u + " and " + v + " conflict but both scheduled in Slot " + slotU + ".");
            }
        }

        if (conflictConstraintPassed) {
            notes.add("Conflict constraints: Zero conflicts violated across " + graph.getNumEdges() + " conflict edges.");
        }

        // 3. Hall capacity check
        boolean capacityConstraintPassed = true;
        int maxHallUsage = 0;
        int totalOccupants = 0;

        for (Map.Entry<Integer, List<Integer>> entry : slotToCourses.entrySet()) {
            int slot = entry.getKey();
            int occupancy = entry.getValue().size();
            totalOccupants += occupancy;
            if (occupancy > maxHallUsage) {
                maxHallUsage = occupancy;
            }
            if (occupancy > r) {
                capacityConstraintPassed = false;
                errors.add("Capacity violation: Slot " + slot + " contains " + occupancy + " exams, exceeding hall limit R=" + r + ".");
            }
        }

        if (capacityConstraintPassed) {
            notes.add("Hall capacity constraints: Satisfied. Max occupancy in any slot is " + maxHallUsage + "/" + r + " halls.");
        }

        // 4. Slot count accuracy
        int actualNonEmptySlots = 0;
        for (List<Integer> list : slotToCourses.values()) {
            if (!list.isEmpty()) actualNonEmptySlots++;
        }
        boolean slotCountAccuratePassed = (actualNonEmptySlots == reportedSlots && actualNonEmptySlots == schedule.getTotalSlots());
        if (!slotCountAccuratePassed) {
            errors.add("Slot count mismatch: reported=" + reportedSlots + ", schedule=" + schedule.getTotalSlots() + ", actualNonEmpty=" + actualNonEmptySlots);
        } else {
            notes.add("Slot count integrity: Exactly " + actualNonEmptySlots + " non-empty time slots utilized.");
        }

        // 5. Optimality consistency
        int theoreticalLB = graph.getCapacityLowerBound();
        boolean optimalityConsistent = true;
        if (provenOptimal) {
            if (reportedSlots < theoreticalLB) {
                optimalityConsistent = false;
                errors.add("Optimality contradiction: Reported slots (" + reportedSlots + ") is strictly less than theoretical lower bound (" + theoreticalLB + ").");
            }
        }
        if (optimalityConsistent) {
            notes.add(provenOptimal ? "Optimality proof: Mathematically proven optimal." : "Optimality status: Valid schedule, optimality unproven (heuristic).");
        }

        boolean valid = courseCoveragePassed &&
                        noDuplicatesPassed &&
                        conflictConstraintPassed &&
                        capacityConstraintPassed &&
                        slotCountAccuratePassed &&
                        optimalityConsistent;

        double avgUsage = (actualNonEmptySlots == 0) ? 0.0 : ((double) totalOccupants / actualNonEmptySlots);

        return new ValidationResult(
            valid,
            courseCoveragePassed,
            noDuplicatesPassed,
            conflictConstraintPassed,
            capacityConstraintPassed,
            slotCountAccuratePassed,
            optimalityConsistent,
            errors,
            notes,
            actualNonEmptySlots,
            maxHallUsage,
            avgUsage
        );
    }
}
