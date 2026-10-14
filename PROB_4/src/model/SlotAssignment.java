package model;

/**
 * Detailed record of a course assignment step for audit and 3D step-by-step playback.
 */
public class SlotAssignment {
    private final int stepNumber;
    private final int courseId;
    private final int slot;
    private final int saturationDegree;
    private final int ordinaryDegree;
    private final String reason;

    public SlotAssignment(int stepNumber, int courseId, int slot, int saturationDegree, int ordinaryDegree, String reason) {
        this.stepNumber = stepNumber;
        this.courseId = courseId;
        this.slot = slot;
        this.saturationDegree = saturationDegree;
        this.ordinaryDegree = ordinaryDegree;
        this.reason = reason;
    }

    public int getStepNumber() {
        return stepNumber;
    }

    public int getCourseId() {
        return courseId;
    }

    public int getSlot() {
        return slot;
    }

    public int getSaturationDegree() {
        return saturationDegree;
    }

    public int getOrdinaryDegree() {
        return ordinaryDegree;
    }

    public String getReason() {
        return reason;
    }

    @Override
    public String toString() {
        return "Step " + stepNumber + ": Course " + courseId + " -> Slot " + slot + " (sat=" + saturationDegree + ", deg=" + ordinaryDegree + ")";
    }
}
