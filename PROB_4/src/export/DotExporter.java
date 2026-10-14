package export;

import model.Course;
import model.Graph;
import model.Schedule;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Exports conflict graphs to Graphviz DOT format with colorized slot assignments.
 */
public final class DotExporter {

    private DotExporter() {}

    private static final String[] PALETTE = {
        "#14b8a6", "#f59e0b", "#6366f1", "#ec4899",
        "#10b981", "#8b5cf6", "#f97316", "#06b6d4",
        "#3b82f6", "#e11d48", "#84cc16", "#d946ef"
    };

    public static String generateDotString(Graph graph, Schedule schedule) {
        StringBuilder sb = new StringBuilder();
        sb.append("graph ExamConflictGraph {\n");
        sb.append("  layout=neato;\n");
        sb.append("  overlap=false;\n");
        sb.append("  node [shape=circle, style=filled, fontname=\"Arial\", fontsize=10, fontcolor=\"white\"];\n");
        sb.append("  edge [color=\"#94a3b8\", penwidth=1.2];\n\n");

        for (Course c : graph.getCourses()) {
            int id = c.getId();
            Integer slot = (schedule != null) ? schedule.getSlotForCourse(id) : null;
            String color = "#64748b"; // default slate
            if (slot != null && slot > 0) {
                color = PALETTE[(slot - 1) % PALETTE.length];
            }
            String label = "C" + id + (slot != null ? "\\nSlot " + slot : "");
            sb.append("  ").append(id)
              .append(" [label=\"").append(label).append("\", fillcolor=\"").append(color).append("\"];\n");
        }

        sb.append("\n");
        for (String edgeKey : graph.getCanonicalEdges()) {
            String[] parts = edgeKey.split("-");
            sb.append("  ").append(parts[0]).append(" -- ").append(parts[1]).append(";\n");
        }
        sb.append("}\n");
        return sb.toString();
    }

    public static void saveDotFile(Graph graph, Schedule schedule, File file) throws IOException {
        String dot = generateDotString(graph, schedule);
        if (file.getParentFile() != null) {
            file.getParentFile().mkdirs();
        }
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            writer.write(dot);
        }
    }
}
