package export;

import model.BenchmarkPoint;
import model.Course;
import model.Graph;
import model.Schedule;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Handles real Java-based CSV export for timetables and algorithm benchmarks.
 */
public final class CsvExporter {

    private CsvExporter() {}

    /**
     * Generates timetable.csv according to specification:
     * Slot,Hall 1,Hall 2,Hall 3...
     */
    public static String generateTimetableCsvString(Graph graph, Schedule schedule) {
        StringBuilder sb = new StringBuilder();
        int r = graph.getHallCapacity();

        // Header: Slot,Hall 1,Hall 2,...,Hall R
        sb.append("Slot");
        for (int h = 1; h <= r; h++) {
            sb.append(",Hall ").append(h);
        }
        sb.append("\r\n");

        Map<Integer, List<Integer>> slotToCourses = schedule.getSlotToCourses();
        for (Map.Entry<Integer, List<Integer>> entry : slotToCourses.entrySet()) {
            int slot = entry.getKey();
            List<Integer> courses = entry.getValue();

            sb.append(slot);
            for (int h = 0; h < r; h++) {
                sb.append(",");
                if (h < courses.size()) {
                    int courseId = courses.get(h);
                    Course course = graph.getCourse(courseId);
                    String label = (course != null) ? course.getName() : "Course " + courseId;
                    sb.append(escapeCsv(label));
                }
            }
            sb.append("\r\n");
        }
        return sb.toString();
    }

    public static void saveTimetableCsv(Graph graph, Schedule schedule, File file) throws IOException {
        String content = generateTimetableCsvString(graph, schedule);
        if (file.getParentFile() != null) {
            file.getParentFile().mkdirs();
        }
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            writer.write(content);
        }
    }

    /**
     * Generates benchmark.csv containing comparative performance metrics.
     */
    public static String generateBenchmarkCsvString(List<BenchmarkPoint> points) {
        StringBuilder sb = new StringBuilder();
        sb.append("N,R,Edges,Density,DSatur_Slots,DSatur_Time_ms,WelshPowell_Slots,WelshPowell_Time_ms,Exact_Slots,Exact_Time_ms,Exact_Proven,Exact_Nodes\r\n");

        if (points != null) {
            for (BenchmarkPoint p : points) {
                sb.append(p.getN()).append(",")
                  .append(p.getR()).append(",")
                  .append(p.getM()).append(",")
                  .append(String.format(Locale.US, "%.4f", p.getDensity())).append(",")
                  .append(p.getDsaturSlots()).append(",")
                  .append(String.format(Locale.US, "%.4f", p.getDsaturTimeMs())).append(",")
                  .append(p.getWelshPowellSlots()).append(",")
                  .append(String.format(Locale.US, "%.4f", p.getWelshPowellTimeMs())).append(",")
                  .append(p.getExactSlots() != null ? p.getExactSlots() : "").append(",")
                  .append(p.getExactTimeMs() != null ? String.format(Locale.US, "%.4f", p.getExactTimeMs()) : "").append(",")
                  .append(p.getExactProven() != null ? p.getExactProven() : "").append(",")
                  .append(p.getExactNodes() != null ? p.getExactNodes() : "")
                  .append("\r\n");
            }
        }
        return sb.toString();
    }

    public static void saveBenchmarkCsv(List<BenchmarkPoint> points, File file) throws IOException {
        String content = generateBenchmarkCsvString(points);
        if (file.getParentFile() != null) {
            file.getParentFile().mkdirs();
        }
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            writer.write(content);
        }
    }

    private static String escapeCsv(String val) {
        if (val == null) return "";
        if (val.contains(",") || val.contains("\"") || val.contains("\n") || val.contains("\r")) {
            return "\"" + val.replace("\"", "\"\"") + "\"";
        }
        return val;
    }
}
