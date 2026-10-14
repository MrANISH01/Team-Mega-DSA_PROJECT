package model;

import java.util.*;

/**
 * Represents a complete timetable schedule.
 * Slots are 1-indexed (Slot 1, Slot 2, ...).
 * Each slot contains a list of course IDs (size <= R).
 */
public class Schedule {
    private final int hallCapacity; // R
    private final Map<Integer, Integer> courseToSlot; // courseId -> slot (1-indexed)
    private final Map<Integer, List<Integer>> slotToCourses; // slot (1-indexed) -> list of course IDs

    public Schedule(int hallCapacity) {
        if (hallCapacity <= 0) {
            throw new IllegalArgumentException("Hall capacity R must be positive: " + hallCapacity);
        }
        this.hallCapacity = hallCapacity;
        this.courseToSlot = new LinkedHashMap<>();
        this.slotToCourses = new TreeMap<>();
    }

    public Schedule(int hallCapacity, Map<Integer, Integer> assignments) {
        this(hallCapacity);
        if (assignments != null) {
            for (Map.Entry<Integer, Integer> e : assignments.entrySet()) {
                assignCourse(e.getKey(), e.getValue());
            }
        }
    }

    public void assignCourse(int courseId, int slot) {
        if (courseId <= 0 || slot <= 0) {
            throw new IllegalArgumentException("Course ID and Slot must be positive.");
        }
        courseToSlot.put(courseId, slot);
        slotToCourses.computeIfAbsent(slot, k -> new ArrayList<>()).add(courseId);
    }

    public int getHallCapacity() {
        return hallCapacity;
    }

    public int getTotalSlots() {
        return slotToCourses.size();
    }

    public int getMaxSlotNumber() {
        return slotToCourses.isEmpty() ? 0 : Collections.max(slotToCourses.keySet());
    }

    public Integer getSlotForCourse(int courseId) {
        return courseToSlot.get(courseId);
    }

    public List<Integer> getCoursesInSlot(int slot) {
        return Collections.unmodifiableList(slotToCourses.getOrDefault(slot, Collections.emptyList()));
    }

    public Map<Integer, Integer> getCourseToSlot() {
        return Collections.unmodifiableMap(courseToSlot);
    }

    public Map<Integer, List<Integer>> getSlotToCourses() {
        return Collections.unmodifiableMap(slotToCourses);
    }

    public int getAssignedCourseCount() {
        return courseToSlot.size();
    }

    /**
     * Creates a deep copy of this schedule.
     */
    public Schedule copy() {
        Schedule cloned = new Schedule(this.hallCapacity);
        for (Map.Entry<Integer, Integer> entry : this.courseToSlot.entrySet()) {
            cloned.assignCourse(entry.getKey(), entry.getValue());
        }
        return cloned;
    }
}
