package model;

import java.util.*;

/**
 * Encapsulates the complete result of executing a scheduling algorithm.
 */
public class AlgorithmResult {
    private final String algorithmName;
    private final int slotCount;
    private final long runtimeNano;
    private final double runtimeMs;
    private final List<SlotAssignment> assignmentSteps;
    private final Schedule schedule;
    private ValidationResult validation;
    private final boolean provenOptimal;
    private final String optimalityReason;
    private final long nodesExplored;
    private final int lowerBound;
    private Integer gapFromOptimum; // null if optimum unproven

    public AlgorithmResult(String algorithmName,
                           int slotCount,
                           long runtimeNano,
                           List<SlotAssignment> assignmentSteps,
                           Schedule schedule,
                           boolean provenOptimal,
                           String optimalityReason,
                           long nodesExplored,
                           int lowerBound) {
        this.algorithmName = algorithmName;
        this.slotCount = slotCount;
        this.runtimeNano = runtimeNano;
        this.runtimeMs = runtimeNano / 1_000_000.0;
        this.assignmentSteps = (assignmentSteps == null) ? Collections.emptyList() : Collections.unmodifiableList(assignmentSteps);
        this.schedule = schedule;
        this.provenOptimal = provenOptimal;
        this.optimalityReason = optimalityReason;
        this.nodesExplored = nodesExplored;
        this.lowerBound = lowerBound;
        this.gapFromOptimum = null;
    }

    public String getAlgorithmName() {
        return algorithmName;
    }

    public int getSlotCount() {
        return slotCount;
    }

    public long getRuntimeNano() {
        return runtimeNano;
    }

    public double getRuntimeMs() {
        return runtimeMs;
    }

    public List<SlotAssignment> getAssignmentSteps() {
        return assignmentSteps;
    }

    public Schedule getSchedule() {
        return schedule;
    }

    public ValidationResult getValidation() {
        return validation;
    }

    public void setValidation(ValidationResult validation) {
        this.validation = validation;
    }

    public boolean isProvenOptimal() {
        return provenOptimal;
    }

    public String getOptimalityReason() {
        return optimalityReason;
    }

    public long getNodesExplored() {
        return nodesExplored;
    }

    public int getLowerBound() {
        return lowerBound;
    }

    public Integer getGapFromOptimum() {
        return gapFromOptimum;
    }

    public void setGapFromOptimum(Integer gapFromOptimum) {
        this.gapFromOptimum = gapFromOptimum;
    }
}
