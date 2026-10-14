import server.ApiServer;

import java.io.File;

/**
 * Main application entry point for ExamCraft 3D.
 * Launches the embedded Java HTTP server and serves both API and interactive frontend.
 */
public class Main {

    public static void main(String[] args) {
        int port = 8080;
        for (int i = 0; i < args.length; i++) {
            if ("--port".equals(args[i]) && i + 1 < args.length) {
                try {
                    port = Integer.parseInt(args[i + 1]);
                } catch (NumberFormatException e) {
                    System.err.println("Invalid port number: " + args[i + 1]);
                }
            }
        }

        File webDir = new File("web");
        File outputDir = new File("output");

        System.out.println("==================================================================");
        System.out.println("         ExamCraft 3D: University Exam Timetable Optimizer        ");
        System.out.println("     Capacity-Constrained Graph Coloring & Optimization Engine    ");
        System.out.println("==================================================================");
        System.out.println("JVM Architecture: " + System.getProperty("os.arch") + " | Java: " + System.getProperty("java.version"));
        System.out.println("Web Assets Directory: " + webDir.getAbsolutePath());
        System.out.println("Output Directory:     " + outputDir.getAbsolutePath());

        try {
            ApiServer apiServer = new ApiServer(port, webDir, outputDir);
            apiServer.start();

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\nShutting down ExamCraft 3D server...");
                apiServer.stop();
            }));

        } catch (Exception e) {
            System.err.println("FATAL: Failed to start server on port " + port + ": " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
