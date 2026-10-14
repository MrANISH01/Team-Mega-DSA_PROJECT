package model;

import java.util.*;

/**
 * Undirected conflict graph representation for examination scheduling.
 * Vertices represent courses (strictly IDs 1..N).
 * Edges represent mutual exclusion conflict constraints.
 * Capacity R defines maximum exams per time slot.
 */
public class Graph {
    private final int numCourses; // N
    private final int hallCapacity; // R
    private final Map<Integer, Course> courses;
    private final Set<String> edgeSet; // canonical "u-v" where u < v
    private int numEdges; // M

    public Graph(int numCourses, int hallCapacity) {
        if (numCourses <= 0) {
            throw new IllegalArgumentException("Number of courses N must be positive: " + numCourses);
        }
        if (hallCapacity <= 0) {
            throw new IllegalArgumentException("Hall capacity R must be positive: " + hallCapacity);
        }
        this.numCourses = numCourses;
        this.hallCapacity = hallCapacity;
        this.courses = new LinkedHashMap<>(numCourses);
        this.edgeSet = new HashSet<>();
        this.numEdges = 0;

        for (int i = 1; i <= numCourses; i++) {
            courses.put(i, new Course(i));
        }
    }

    public void addEdge(int u, int v) {
        if (u < 1 || u > numCourses || v < 1 || v > numCourses) {
            throw new IllegalArgumentException("Course IDs must be within range [1, " + numCourses + "]. Given: (" + u + ", " + v + ")");
        }
        if (u == v) {
            throw new IllegalArgumentException("Self-loops are forbidden: course " + u + " cannot conflict with itself.");
        }
        int min = Math.min(u, v);
        int max = Math.max(u, v);
        String edgeKey = min + "-" + max;
        if (!edgeSet.contains(edgeKey)) {
            edgeSet.add(edgeKey);
            courses.get(u).addNeighbor(v);
            courses.get(v).addNeighbor(u);
            numEdges++;
        }
        // Duplicate edges are safely handled (ignored without error)
    }

    public boolean hasEdge(int u, int v) {
        if (u < 1 || u > numCourses || v < 1 || v > numCourses || u == v) {
            return false;
        }
        int min = Math.min(u, v);
        int max = Math.max(u, v);
        return edgeSet.contains(min + "-" + max);
    }

    public int getNumCourses() {
        return numCourses;
    }

    public int getHallCapacity() {
        return hallCapacity;
    }

    public int getNumEdges() {
        return numEdges;
    }

    public Course getCourse(int id) {
        return courses.get(id);
    }

    public Collection<Course> getCourses() {
        return Collections.unmodifiableCollection(courses.values());
    }

    public Set<String> getCanonicalEdges() {
        return Collections.unmodifiableSet(edgeSet);
    }

    /**
     * Edge density: M / (N * (N - 1) / 2).
     */
    public double getEdgeDensity() {
        if (numCourses < 2) return 0.0;
        double maxEdges = ((double) numCourses * (numCourses - 1)) / 2.0;
        return numEdges / maxEdges;
    }

    /**
     * Capacity-based lower bound: ceil(N / R).
     */
    public int getCapacityLowerBound() {
        return (int) Math.ceil((double) numCourses / hallCapacity);
    }

    /**
     * Validates and constructs graph from manual input.
     */
    public static Graph fromManualInput(int n, int r, List<int[]> edgePairs) {
        Graph g = new Graph(n, r);
        if (edgePairs != null) {
            for (int[] pair : edgePairs) {
                if (pair == null || pair.length != 2) {
                    throw new IllegalArgumentException("Malformed edge pair. Expected [u, v].");
                }
                g.addEdge(pair[0], pair[1]);
            }
        }
        return g;
    }

    /**
     * Deterministically or randomly generates a simple graph with target density.
     */
    public static Graph generateRandom(int n, int r, double density, Long seed) {
        Graph g = new Graph(n, r);
        if (n < 2 || density <= 0.0) {
            return g; // empty or single node graph
        }
        density = Math.max(0.0, Math.min(1.0, density));
        long maxEdges = ((long) n * (n - 1)) / 2;
        long targetEdges = Math.round(density * maxEdges);

        Random random = (seed != null) ? new Random(seed) : new Random();

        if (density >= 1.0) {
            // Complete graph
            for (int i = 1; i <= n; i++) {
                for (int j = i + 1; j <= n; j++) {
                    g.addEdge(i, j);
                }
            }
            return g;
        }

        // List all possible pairs and shuffle if target is a significant fraction, or reservoir sample
        if (targetEdges <= maxEdges / 2) {
            while (g.getNumEdges() < targetEdges) {
                int u = 1 + random.nextInt(n);
                int v = 1 + random.nextInt(n);
                if (u != v) {
                    g.addEdge(u, v);
                }
            }
        } else {
            // Generate all edges, shuffle and take targetEdges
            List<int[]> allPairs = new ArrayList<>((int) maxEdges);
            for (int i = 1; i <= n; i++) {
                for (int j = i + 1; j <= n; j++) {
                    allPairs.add(new int[]{i, j});
                }
            }
            Collections.shuffle(allPairs, random);
            for (int i = 0; i < targetEdges; i++) {
                int[] pair = allPairs.get(i);
                g.addEdge(pair[0], pair[1]);
            }
        }
        return g;
    }

    /**
     * Graph presets for demonstration and testing.
     */
    public static Graph createPreset(String name) {
        switch (name.toLowerCase()) {
            case "sparse":
                // 10 courses, 3 halls, sparse conflicts
                return fromManualInput(10, 3, Arrays.asList(
                    new int[]{1, 2}, new int[]{2, 3}, new int[]{4, 5},
                    new int[]{5, 6}, new int[]{7, 8}, new int[]{8, 9}
                ));
            case "medium":
                // 14 courses, 3 halls, medium academic conflict structure
                return fromManualInput(14, 3, Arrays.asList(
                    new int[]{1, 2}, new int[]{1, 3}, new int[]{2, 4}, new int[]{3, 4},
                    new int[]{4, 5}, new int[]{5, 6}, new int[]{6, 7}, new int[]{7, 8},
                    new int[]{8, 9}, new int[]{9, 10}, new int[]{1, 10}, new int[]{11, 12},
                    new int[]{12, 13}, new int[]{13, 14}, new int[]{5, 11}, new int[]{6, 12}
                ));
            case "dense":
                // 12 courses, 3 halls, high conflict density with triangle cliques
                return fromManualInput(12, 3, Arrays.asList(
                    new int[]{1, 2}, new int[]{2, 3}, new int[]{3, 1},
                    new int[]{4, 5}, new int[]{5, 6}, new int[]{6, 4},
                    new int[]{7, 8}, new int[]{8, 9}, new int[]{9, 7},
                    new int[]{10, 11}, new int[]{11, 12}, new int[]{12, 10},
                    new int[]{1, 4}, new int[]{4, 7}, new int[]{7, 10},
                    new int[]{2, 5}, new int[]{5, 8}, new int[]{8, 11},
                    new int[]{3, 6}, new int[]{6, 9}, new int[]{9, 12}
                ));
            case "complete":
                // 8 courses, 2 halls, complete graph K8
                Graph k8 = new Graph(8, 2);
                for (int i = 1; i <= 8; i++) {
                    for (int j = i + 1; j <= 8; j++) {
                        k8.addEdge(i, j);
                    }
                }
                return k8;
            case "capacity_bottleneck":
                // 12 courses, 2 halls, NO conflict edges (demonstrates capacity forcing ceil(12/2)=6 slots)
                return new Graph(12, 2);
            case "bipartite":
                // 10 courses, 3 halls, complete bipartite K5,5
                Graph bip = new Graph(10, 3);
                for (int i = 1; i <= 5; i++) {
                    for (int j = 6; j <= 10; j++) {
                        bip.addEdge(i, j);
                    }
                }
                return bip;
            case "chvatal_gap":
                // Graph where greedy ordering can use more slots than exact solver
                // Crown graph / bipartite matching structure
                return fromManualInput(8, 2, Arrays.asList(
                    new int[]{1, 6}, new int[]{1, 7}, new int[]{1, 8},
                    new int[]{2, 5}, new int[]{2, 7}, new int[]{2, 8},
                    new int[]{3, 5}, new int[]{3, 6}, new int[]{3, 8},
                    new int[]{4, 5}, new int[]{4, 6}, new int[]{4, 7}
                ));
            default:
                return createPreset("medium");
        }
    }
}
