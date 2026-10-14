package algorithm;

import model.Course;
import model.Graph;

import java.util.*;

/**
 * Graph utility algorithms including Bron-Kerbosch maximum clique search,
 * saturation degrees, and lower bound derivations.
 */
public final class GraphUtils {

    private GraphUtils() {}

    public static class CliqueResult {
        private final Set<Integer> clique;
        private final boolean exactMaximum;

        public CliqueResult(Set<Integer> clique, boolean exactMaximum) {
            this.clique = clique;
            this.exactMaximum = exactMaximum;
        }

        public Set<Integer> getClique() {
            return clique;
        }

        public int getSize() {
            return clique.size();
        }

        public boolean isExactMaximum() {
            return exactMaximum;
        }
    }

    /**
     * Finds a maximum clique using Bron-Kerbosch algorithm with pivoting.
     * Guaranteed exact if completed within node budget.
     */
    public static CliqueResult findMaximumClique(Graph graph, int maxRecursions) {
        int n = graph.getNumCourses();
        if (n == 0) return new CliqueResult(Collections.emptySet(), true);
        if (n == 1) return new CliqueResult(Collections.singleton(1), true);

        Set<Integer> r = new HashSet<>();
        Set<Integer> p = new HashSet<>();
        Set<Integer> x = new HashSet<>();
        for (int i = 1; i <= n; i++) {
            p.add(i);
        }

        Set<Integer> maxClique = new HashSet<>();
        int[] recursionCounter = new int[]{0};
        boolean[] completed = new boolean[]{true};

        bronKerboschPivot(graph, r, p, x, maxClique, recursionCounter, maxRecursions, completed);

        return new CliqueResult(maxClique, completed[0]);
    }

    private static void bronKerboschPivot(Graph graph,
                                         Set<Integer> r,
                                         Set<Integer> p,
                                         Set<Integer> x,
                                         Set<Integer> bestClique,
                                         int[] counter,
                                         int limit,
                                         boolean[] completed) {
        if (counter[0]++ > limit) {
            completed[0] = false;
            return;
        }

        if (p.isEmpty() && x.isEmpty()) {
            if (r.size() > bestClique.size()) {
                bestClique.clear();
                bestClique.addAll(r);
            }
            return;
        }

        // Upper bound pruning: if current r + p cannot exceed bestClique, prune!
        if (r.size() + p.size() <= bestClique.size()) {
            return;
        }

        // Choose pivot u in P union X with maximum degree in P
        int pivot = -1;
        int maxDeg = -1;
        Set<Integer> pxUnion = new HashSet<>(p);
        pxUnion.addAll(x);
        for (int u : pxUnion) {
            int degInP = 0;
            Course cu = graph.getCourse(u);
            for (int v : cu.getNeighborIds()) {
                if (p.contains(v)) degInP++;
            }
            if (degInP > maxDeg) {
                maxDeg = degInP;
                pivot = u;
            }
        }

        // Candidates: P \ Neighbors(pivot)
        Set<Integer> candidates = new HashSet<>(p);
        if (pivot != -1) {
            candidates.removeAll(graph.getCourse(pivot).getNeighborIds());
        }

        for (int v : candidates) {
            if (!completed[0]) return;
            Course cv = graph.getCourse(v);
            Set<Integer> newR = new HashSet<>(r);
            newR.add(v);

            Set<Integer> newP = new HashSet<>();
            for (int nbr : cv.getNeighborIds()) {
                if (p.contains(nbr)) newP.add(nbr);
            }

            Set<Integer> newX = new HashSet<>();
            for (int nbr : cv.getNeighborIds()) {
                if (x.contains(nbr)) newX.add(nbr);
            }

            bronKerboschPivot(graph, newR, newP, newX, bestClique, counter, limit, completed);

            p.remove(v);
            x.add(v);
        }
    }

    /**
     * Computes the proven theoretical lower bound for the capacity-constrained coloring:
     * LB = max( ceil(N / R), cliqueSize )
     */
    public static int computeTheoreticalLowerBound(Graph graph) {
        int capLB = graph.getCapacityLowerBound();
        CliqueResult cr = findMaximumClique(graph, 15000);
        int cliqueLB = cr.getSize();
        return Math.max(capLB, cliqueLB);
    }

    /**
     * Computes saturation degree of an unassigned vertex given current partial coloring.
     * Saturation degree = number of DISTINCT slots already assigned to its neighbors.
     */
    public static int computeSaturationDegree(Graph graph, int courseId, Map<Integer, Integer> partialAssignment) {
        Course course = graph.getCourse(courseId);
        Set<Integer> neighborColors = new HashSet<>();
        for (int nbrId : course.getNeighborIds()) {
            Integer slot = partialAssignment.get(nbrId);
            if (slot != null) {
                neighborColors.add(slot);
            }
        }
        return neighborColors.size();
    }
}
