package server;

import algorithm.*;
import benchmark.BenchmarkRunner;
import export.CsvExporter;
import export.DotExporter;
import json.SimpleJson;
import model.*;
import validation.ScheduleValidator;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.Executors;

/**
 * Built-in HTTP server providing REST API endpoints and static asset serving for ExamCraft 3D.
 */
public class ApiServer {

    private final int port;
    private final File webDir;
    private final File outputDir;
    private HttpServer server;

    // Active server state
    private Graph activeGraph;
    private AlgorithmResult activeResult;
    private BenchmarkRunner.ComparisonReport activeComparison;
    private List<BenchmarkPoint> activeBenchmarkPoints;

    public ApiServer(int port, File webDir, File outputDir) {
        this.port = port;
        this.webDir = webDir;
        this.outputDir = outputDir;
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        // Initialize with default preset
        this.activeGraph = Graph.createPreset("medium");
        DSaturScheduler dsatur = new DSaturScheduler();
        this.activeResult = dsatur.schedule(this.activeGraph);
        this.activeResult.setValidation(ScheduleValidator.validate(this.activeGraph, this.activeResult));
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor() != null ?
            Executors.newVirtualThreadPerTaskExecutor() : Executors.newFixedThreadPool(16));

        // API Endpoints
        server.createContext("/api/health", new HealthHandler());
        server.createContext("/api/graph/manual", new ManualGraphHandler());
        server.createContext("/api/graph/random", new RandomGraphHandler());
        server.createContext("/api/graph/preset", new PresetGraphHandler());
        server.createContext("/api/graph/current", new CurrentGraphHandler());
        server.createContext("/api/schedule/dsatur", new DSaturHandler());
        server.createContext("/api/schedule/welsh-powell", new WelshPowellHandler());
        server.createContext("/api/schedule/exact", new ExactHandler());
        server.createContext("/api/schedule/compare", new CompareHandler());
        server.createContext("/api/benchmark", new BenchmarkHandler());
        server.createContext("/api/export/timetable.csv", new ExportTimetableCsvHandler());
        server.createContext("/api/export/benchmark.csv", new ExportBenchmarkCsvHandler());
        server.createContext("/api/export/graph.dot", new ExportDotHandler());

        // Static Frontend Asset Handler
        server.createContext("/", new StaticFileHandler(webDir));

        server.start();
        System.out.println("==================================================================");
        System.out.println("   ExamCraft 3D Server running at http://localhost:" + port + "/");
        System.out.println("==================================================================");
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    // --- Helper Methods ---

    private static String readBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void sendJsonResponse(HttpExchange exchange, int statusCode, Object data) throws IOException {
        byte[] bytes = SimpleJson.serialize(data).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static void sendErrorResponse(HttpExchange exchange, int statusCode, String message) throws IOException {
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("error", true);
        err.put("message", message);
        sendJsonResponse(exchange, statusCode, err);
    }

    private static void sendFileResponse(HttpExchange exchange, String contentType, String filename, byte[] content) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(200, content.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(content);
        }
    }

    private Map<String, Object> serializeGraph(Graph g) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("numCourses", g.getNumCourses());
        map.put("hallCapacity", g.getHallCapacity());
        map.put("numEdges", g.getNumEdges());
        map.put("density", g.getEdgeDensity());
        map.put("capacityLowerBound", g.getCapacityLowerBound());
        map.put("theoreticalLowerBound", GraphUtils.computeTheoreticalLowerBound(g));

        List<Map<String, Object>> coursesList = new ArrayList<>();
        for (Course c : g.getCourses()) {
            Map<String, Object> cm = new LinkedHashMap<>();
            cm.put("id", c.getId());
            cm.put("code", c.getCode());
            cm.put("name", c.getName());
            cm.put("degree", c.getDegree());
            cm.put("neighbors", new ArrayList<>(c.getNeighborIds()));
            coursesList.add(cm);
        }
        map.put("courses", coursesList);

        List<List<Integer>> edgesList = new ArrayList<>();
        for (String edge : g.getCanonicalEdges()) {
            String[] p = edge.split("-");
            edgesList.add(Arrays.asList(Integer.parseInt(p[0]), Integer.parseInt(p[1])));
        }
        map.put("edges", edgesList);
        return map;
    }

    private Map<String, Object> serializeResult(AlgorithmResult res) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("algorithmName", res.getAlgorithmName());
        map.put("slotCount", res.getSlotCount());
        map.put("runtimeNano", res.getRuntimeNano());
        map.put("runtimeMs", res.getRuntimeMs());
        map.put("provenOptimal", res.isProvenOptimal());
        map.put("optimalityReason", res.getOptimalityReason());
        map.put("nodesExplored", res.getNodesExplored());
        map.put("lowerBound", res.getLowerBound());
        map.put("gapFromOptimum", res.getGapFromOptimum());

        // Schedule mapping
        Schedule sched = res.getSchedule();
        Map<String, Object> schedMap = new LinkedHashMap<>();
        schedMap.put("hallCapacity", sched.getHallCapacity());
        schedMap.put("totalSlots", sched.getTotalSlots());
        schedMap.put("courseToSlot", sched.getCourseToSlot());

        Map<String, Object> slotToCourses = new LinkedHashMap<>();
        for (Map.Entry<Integer, List<Integer>> e : sched.getSlotToCourses().entrySet()) {
            slotToCourses.put(String.valueOf(e.getKey()), e.getValue());
        }
        schedMap.put("slotToCourses", slotToCourses);
        map.put("schedule", schedMap);

        // Validation
        ValidationResult val = res.getValidation();
        if (val != null) {
            Map<String, Object> valMap = new LinkedHashMap<>();
            valMap.put("valid", val.isValid());
            valMap.put("courseCoveragePassed", val.isCourseCoveragePassed());
            valMap.put("noDuplicatesPassed", val.isNoDuplicatesPassed());
            valMap.put("conflictConstraintPassed", val.isConflictConstraintPassed());
            valMap.put("capacityConstraintPassed", val.isCapacityConstraintPassed());
            valMap.put("slotCountAccuratePassed", val.isSlotCountAccuratePassed());
            valMap.put("optimalityConsistent", val.isOptimalityConsistent());
            valMap.put("errors", val.getErrorMessages());
            valMap.put("notes", val.getPassNotes());
            valMap.put("totalSlots", val.getTotalSlots());
            valMap.put("maxHallUsage", val.getMaxHallUsage());
            valMap.put("averageHallUsage", val.getAverageHallUsage());
            map.put("validation", valMap);
        }

        // Assignment steps
        List<Map<String, Object>> stepsList = new ArrayList<>();
        for (SlotAssignment step : res.getAssignmentSteps()) {
            Map<String, Object> sm = new LinkedHashMap<>();
            sm.put("stepNumber", step.getStepNumber());
            sm.put("courseId", step.getCourseId());
            sm.put("slot", step.getSlot());
            sm.put("saturationDegree", step.getSaturationDegree());
            sm.put("ordinaryDegree", step.getOrdinaryDegree());
            sm.put("reason", step.getReason());
            stepsList.add(sm);
        }
        map.put("assignmentSteps", stepsList);

        return map;
    }

    // --- Endpoint Handlers ---

    private class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("status", "ok");
            resp.put("system", "ExamCraft 3D");
            resp.put("javaVersion", System.getProperty("java.version"));
            resp.put("timestamp", System.currentTimeMillis());
            sendJsonResponse(exchange, 200, resp);
        }
    }

    private class CurrentGraphHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("graph", serializeGraph(activeGraph));
            if (activeResult != null) {
                resp.put("activeResult", serializeResult(activeResult));
            }
            sendJsonResponse(exchange, 200, resp);
        }
    }

    private class ManualGraphHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendErrorResponse(exchange, 405, "Method not allowed. Use POST.");
                return;
            }
            try {
                String body = readBody(exchange);
                Map<String, Object> req = SimpleJson.parseObject(body);
                int n = ((Number) req.get("numCourses")).intValue();
                int r = ((Number) req.get("hallCapacity")).intValue();

                List<?> rawEdges = (List<?>) req.get("edges");
                List<int[]> edges = new ArrayList<>();
                if (rawEdges != null) {
                    for (Object item : rawEdges) {
                        List<?> pair = (List<?>) item;
                        int u = ((Number) pair.get(0)).intValue();
                        int v = ((Number) pair.get(1)).intValue();
                        edges.add(new int[]{u, v});
                    }
                }

                Graph g = Graph.fromManualInput(n, r, edges);
                activeGraph = g;

                // Re-run DSatur automatically on new graph
                DSaturScheduler dsatur = new DSaturScheduler();
                activeResult = dsatur.schedule(g);
                activeResult.setValidation(ScheduleValidator.validate(g, activeResult));

                Map<String, Object> resp = new LinkedHashMap<>();
                resp.put("success", true);
                resp.put("graph", serializeGraph(activeGraph));
                resp.put("activeResult", serializeResult(activeResult));
                sendJsonResponse(exchange, 200, resp);
            } catch (Exception e) {
                sendErrorResponse(exchange, 400, "Invalid manual graph input: " + e.getMessage());
            }
        }
    }

    private class RandomGraphHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendErrorResponse(exchange, 405, "Method not allowed. Use POST.");
                return;
            }
            try {
                String body = readBody(exchange);
                Map<String, Object> req = SimpleJson.parseObject(body);
                int n = ((Number) req.get("numCourses")).intValue();
                int r = ((Number) req.get("hallCapacity")).intValue();
                double density = ((Number) req.get("density")).doubleValue();
                Long seed = req.containsKey("seed") && req.get("seed") != null ?
                    ((Number) req.get("seed")).longValue() : null;

                Graph g = Graph.generateRandom(n, r, density, seed);
                activeGraph = g;

                DSaturScheduler dsatur = new DSaturScheduler();
                activeResult = dsatur.schedule(g);
                activeResult.setValidation(ScheduleValidator.validate(g, activeResult));

                Map<String, Object> resp = new LinkedHashMap<>();
                resp.put("success", true);
                resp.put("graph", serializeGraph(activeGraph));
                resp.put("activeResult", serializeResult(activeResult));
                sendJsonResponse(exchange, 200, resp);
            } catch (Exception e) {
                sendErrorResponse(exchange, 400, "Invalid random graph parameters: " + e.getMessage());
            }
        }
    }

    private class PresetGraphHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                String body = readBody(exchange);
                Map<String, Object> req = SimpleJson.parseObject(body);
                String preset = String.valueOf(req.getOrDefault("preset", "medium"));

                Graph g = Graph.createPreset(preset);
                activeGraph = g;

                DSaturScheduler dsatur = new DSaturScheduler();
                activeResult = dsatur.schedule(g);
                activeResult.setValidation(ScheduleValidator.validate(g, activeResult));

                Map<String, Object> resp = new LinkedHashMap<>();
                resp.put("success", true);
                resp.put("graph", serializeGraph(activeGraph));
                resp.put("activeResult", serializeResult(activeResult));
                sendJsonResponse(exchange, 200, resp);
            } catch (Exception e) {
                sendErrorResponse(exchange, 400, "Failed to load preset: " + e.getMessage());
            }
        }
    }

    private class DSaturHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                DSaturScheduler dsatur = new DSaturScheduler();
                activeResult = dsatur.schedule(activeGraph);
                activeResult.setValidation(ScheduleValidator.validate(activeGraph, activeResult));

                Map<String, Object> resp = new LinkedHashMap<>();
                resp.put("result", serializeResult(activeResult));
                sendJsonResponse(exchange, 200, resp);
            } catch (Exception e) {
                sendErrorResponse(exchange, 500, "DSatur scheduling error: " + e.getMessage());
            }
        }
    }

    private class WelshPowellHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                WelshPowellScheduler welsh = new WelshPowellScheduler();
                activeResult = welsh.schedule(activeGraph);
                activeResult.setValidation(ScheduleValidator.validate(activeGraph, activeResult));

                Map<String, Object> resp = new LinkedHashMap<>();
                resp.put("result", serializeResult(activeResult));
                sendJsonResponse(exchange, 200, resp);
            } catch (Exception e) {
                sendErrorResponse(exchange, 500, "Welsh-Powell scheduling error: " + e.getMessage());
            }
        }
    }

    private class ExactHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                long timeoutMs = 5000L;
                long maxNodes = 100000L;

                String body = readBody(exchange);
                if (body != null && !body.isBlank()) {
                    try {
                        Map<String, Object> req = SimpleJson.parseObject(body);
                        if (req.containsKey("timeoutMs") && req.get("timeoutMs") != null) {
                            timeoutMs = ((Number) req.get("timeoutMs")).longValue();
                        }
                        if (req.containsKey("maxNodes") && req.get("maxNodes") != null) {
                            maxNodes = ((Number) req.get("maxNodes")).longValue();
                        }
                    } catch (Exception ignored) {}
                }

                ExactBranchAndBoundScheduler exact = new ExactBranchAndBoundScheduler(maxNodes, timeoutMs);
                activeResult = exact.schedule(activeGraph);
                activeResult.setValidation(ScheduleValidator.validate(activeGraph, activeResult));

                Map<String, Object> resp = new LinkedHashMap<>();
                resp.put("result", serializeResult(activeResult));
                sendJsonResponse(exchange, 200, resp);
            } catch (Exception e) {
                sendErrorResponse(exchange, 500, "Exact solver error: " + e.getMessage());
            }
        }
    }

    private class CompareHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                long timeoutMs = 5000L;
                long maxNodes = 50000L;
                activeComparison = BenchmarkRunner.compareAll(activeGraph, timeoutMs, maxNodes);

                Map<String, Object> resp = new LinkedHashMap<>();
                resp.put("dsatur", serializeResult(activeComparison.getDsaturResult()));
                resp.put("welshPowell", serializeResult(activeComparison.getWelshPowellResult()));
                resp.put("exact", serializeResult(activeComparison.getExactResult()));
                sendJsonResponse(exchange, 200, resp);
            } catch (Exception e) {
                sendErrorResponse(exchange, 500, "Comparison failed: " + e.getMessage());
            }
        }
    }

    private class BenchmarkHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                String body = readBody(exchange);
                Map<String, Object> req = SimpleJson.parseObject(body);
                List<?> nList = (List<?>) req.getOrDefault("nValues", Arrays.asList(6, 10, 14, 18, 22));
                int r = ((Number) req.getOrDefault("hallCapacity", 3)).intValue();
                double density = ((Number) req.getOrDefault("density", 0.35)).doubleValue();
                long seed = ((Number) req.getOrDefault("seed", 42L)).longValue();

                int[] nValues = new int[nList.size()];
                for (int i = 0; i < nList.size(); i++) {
                    nValues[i] = ((Number) nList.get(i)).intValue();
                }

                activeBenchmarkPoints = BenchmarkRunner.runScalingBenchmark(nValues, r, density, seed);

                // Save to output/benchmark.csv
                File bFile = new File(outputDir, "benchmark.csv");
                CsvExporter.saveBenchmarkCsv(activeBenchmarkPoints, bFile);

                List<Map<String, Object>> pts = new ArrayList<>();
                for (BenchmarkPoint p : activeBenchmarkPoints) {
                    Map<String, Object> pm = new LinkedHashMap<>();
                    pm.put("n", p.getN());
                    pm.put("r", p.getR());
                    pm.put("m", p.getM());
                    pm.put("density", p.getDensity());
                    pm.put("dsaturSlots", p.getDsaturSlots());
                    pm.put("dsaturTimeMs", p.getDsaturTimeMs());
                    pm.put("welshPowellSlots", p.getWelshPowellSlots());
                    pm.put("welshPowellTimeMs", p.getWelshPowellTimeMs());
                    pm.put("exactSlots", p.getExactSlots());
                    pm.put("exactTimeMs", p.getExactTimeMs());
                    pm.put("exactProven", p.getExactProven());
                    pm.put("exactNodes", p.getExactNodes());
                    pts.add(pm);
                }

                Map<String, Object> resp = new LinkedHashMap<>();
                resp.put("success", true);
                resp.put("points", pts);
                sendJsonResponse(exchange, 200, resp);
            } catch (Exception e) {
                sendErrorResponse(exchange, 500, "Benchmark run error: " + e.getMessage());
            }
        }
    }

    private class ExportTimetableCsvHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                if (activeResult == null || activeResult.getSchedule() == null) {
                    sendErrorResponse(exchange, 400, "No active schedule to export.");
                    return;
                }
                String csv = CsvExporter.generateTimetableCsvString(activeGraph, activeResult.getSchedule());
                File tFile = new File(outputDir, "timetable.csv");
                CsvExporter.saveTimetableCsv(activeGraph, activeResult.getSchedule(), tFile);
                sendFileResponse(exchange, "text/csv; charset=UTF-8", "timetable.csv", csv.getBytes(StandardCharsets.UTF_8));
            } catch (Exception e) {
                sendErrorResponse(exchange, 500, "Export timetable failed: " + e.getMessage());
            }
        }
    }

    private class ExportBenchmarkCsvHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                if (activeBenchmarkPoints == null || activeBenchmarkPoints.isEmpty()) {
                    // Generate default benchmark if none exists
                    activeBenchmarkPoints = BenchmarkRunner.runScalingBenchmark(new int[]{6, 10, 14, 18, 22}, 3, 0.35, 42L);
                }
                String csv = CsvExporter.generateBenchmarkCsvString(activeBenchmarkPoints);
                File bFile = new File(outputDir, "benchmark.csv");
                CsvExporter.saveBenchmarkCsv(activeBenchmarkPoints, bFile);
                sendFileResponse(exchange, "text/csv; charset=UTF-8", "benchmark.csv", csv.getBytes(StandardCharsets.UTF_8));
            } catch (Exception e) {
                sendErrorResponse(exchange, 500, "Export benchmark failed: " + e.getMessage());
            }
        }
    }

    private class ExportDotHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                Schedule sched = (activeResult != null) ? activeResult.getSchedule() : null;
                String dot = DotExporter.generateDotString(activeGraph, sched);
                File dFile = new File(outputDir, "graph.dot");
                DotExporter.saveDotFile(activeGraph, sched, dFile);
                sendFileResponse(exchange, "text/vnd.graphviz; charset=UTF-8", "graph.dot", dot.getBytes(StandardCharsets.UTF_8));
            } catch (Exception e) {
                sendErrorResponse(exchange, 500, "Export DOT failed: " + e.getMessage());
            }
        }
    }

    // Static File Serving
    private static class StaticFileHandler implements HttpHandler {
        private final File baseDir;

        StaticFileHandler(File baseDir) {
            this.baseDir = baseDir;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if (path == null || path.equals("/") || path.isBlank()) {
                path = "/index.html";
            }
            File file = new File(baseDir, path.substring(1));
            if (!file.exists() || file.isDirectory()) {
                file = new File(baseDir, "index.html");
            }
            if (!file.exists()) {
                String notFound = "404 Not Found";
                exchange.sendResponseHeaders(404, notFound.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(notFound.getBytes());
                }
                return;
            }

            String mime = getMimeType(file.getName());
            byte[] bytes = Files.readAllBytes(file.toPath());
            exchange.getResponseHeaders().set("Content-Type", mime);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }

        private String getMimeType(String filename) {
            if (filename.endsWith(".html")) return "text/html; charset=UTF-8";
            if (filename.endsWith(".css")) return "text/css; charset=UTF-8";
            if (filename.endsWith(".js")) return "application/javascript; charset=UTF-8";
            if (filename.endsWith(".svg")) return "image/svg+xml";
            if (filename.endsWith(".json")) return "application/json; charset=UTF-8";
            if (filename.endsWith(".png")) return "image/png";
            if (filename.endsWith(".ico")) return "image/x-icon";
            return "application/octet-stream";
        }
    }
}
