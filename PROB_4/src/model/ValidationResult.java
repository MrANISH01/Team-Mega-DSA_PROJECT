package model;

import java.util.*;

/**
 * Result of rigorous timetable validation.
 */
public class ValidationResult {
    private final boolean valid;
    private final boolean courseCoveragePassed;
    private final boolean noDuplicatesPassed;
    private final boolean conflictConstraintPassed;
    private final boolean capacityConstraintPassed;
    private final boolean slotCountAccuratePassed;
    private final boolean optimalityConsistent;
    private final List<String> errorMessages;
    private final List<String> passNotes;
    private final int totalSlots;
    private final int maxHallUsage;
    private final double averageHallUsage;

    public ValidationResult(boolean valid,
                            boolean courseCoveragePassed,
                            boolean noDuplicatesPassed,
                            boolean conflictConstraintPassed,
                            boolean capacityConstraintPassed,
                            boolean slotCountAccuratePassed,
                            boolean optimalityConsistent,
                            List<String> errorMessages,
                            List<String> passNotes,
                            int totalSlots,
                            int maxHallUsage,
                            double averageHallUsage) {
        this.valid = valid;
        this.courseCoveragePassed = courseCoveragePassed;
        this.noDuplicatesPassed = noDuplicatesPassed;
        this.conflictConstraintPassed = conflictConstraintPassed;
        this.capacityConstraintPassed = capacityConstraintPassed;
        this.slotCountAccuratePassed = slotCountAccuratePassed;
        this.optimalityConsistent = optimalityConsistent;
        this.errorMessages = (errorMessages == null) ? Collections.emptyList() : Collections.unmodifiableList(errorMessages);
        this.passNotes = (passNotes == null) ? Collections.emptyList() : Collections.unmodifiableList(passNotes);
        this.totalSlots = totalSlots;
        this.maxHallUsage = maxHallUsage;
        this.averageHallUsage = averageHallUsage;
    }

    public boolean isValid() {
        return valid;
    }

    public boolean isCourseCoveragePassed() {
        return courseCoveragePassed;
    }

    public boolean isNoDuplicatesPassed() {
        return noDuplicatesPassed;
    }

    public boolean isConflictConstraintPassed() {
        return conflictConstraintPassed;
    }

    public boolean isCapacityConstraintPassed() {
        return capacityConstraintPassed;
    }

    public boolean isSlotCountAccuratePassed() {
        return slotCountAccuratePassed;
    }

    public boolean isOptimalityConsistent() {
        return optimalityConsistent;
    }

    public List<String> getErrorMessages() {
        return errorMessages;
    }

    public List<String> getPassNotes() {
        return passNotes;
    }

    public int getTotalSlots() {
        return totalSlots;
    }

    public int getMaxHallUsage() {
        return maxHallUsage;
    }

    public double getAverageHallUsage() {
        return averageHallUsage;
    }
}
