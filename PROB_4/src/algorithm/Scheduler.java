package algorithm;

import model.AlgorithmResult;
import model.Graph;

/**
 * Common interface for all university examination timetable scheduling algorithms.
 */
public interface Scheduler {
    /**
     * Executes the scheduling algorithm on the provided conflict graph.
     * All runtime measurement must be encapsulated strictly within the algorithm execution.
     *
     * @param graph The conflict graph with course nodes and capacity R
     * @return AlgorithmResult containing the schedule, metrics, step sequence, and optimality status
     */
    AlgorithmResult schedule(Graph graph);

    String getName();
}
