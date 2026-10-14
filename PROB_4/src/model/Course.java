package model;

import java.util.*;

/**
 * Represents a university course node in the conflict graph.
 * Course IDs are strictly 1-indexed (1..N).
 */
public class Course implements Comparable<Course> {
    private final int id;
    private final String code;
    private final String name;
    private final Set<Integer> neighborIds; // 1-indexed IDs of conflicting courses

    public Course(int id, String code, String name) {
        if (id <= 0) {
            throw new IllegalArgumentException("Course ID must be positive: " + id);
        }
        this.id = id;
        this.code = (code == null || code.isBlank()) ? "C" + id : code.trim();
        this.name = (name == null || name.isBlank()) ? "Course " + id : name.trim();
        this.neighborIds = new TreeSet<>();
    }

    public Course(int id) {
        this(id, "C" + id, "Course " + id);
    }

    public int getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public Set<Integer> getNeighborIds() {
        return Collections.unmodifiableSet(neighborIds);
    }

    public void addNeighbor(int neighborId) {
        if (neighborId == this.id) {
            throw new IllegalArgumentException("Self-loops are forbidden for course " + id);
        }
        neighborIds.add(neighborId);
    }

    public int getDegree() {
        return neighborIds.size();
    }

    public boolean conflictsWith(int otherId) {
        return neighborIds.contains(otherId);
    }

    @Override
    public int compareTo(Course o) {
        return Integer.compare(this.id, o.id);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Course course = (Course) o;
        return id == course.id;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(id);
    }

    @Override
    public String toString() {
        return code + " (ID:" + id + ", deg:" + getDegree() + ")";
    }
}
