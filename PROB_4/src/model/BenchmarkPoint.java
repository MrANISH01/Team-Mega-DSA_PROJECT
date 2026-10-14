package model;

/**
 * Represents a single benchmark measurement for a graph configuration.
 */
public class BenchmarkPoint {
    private final int n;
    private final int r;
    private final int m;
    private final double density;
    private final int dsaturSlots;
    private final double dsaturTimeMs;
    private final int welshPowellSlots;
    private final double welshPowellTimeMs;
    private final Integer exactSlots;
    private final Double exactTimeMs;
    private final Boolean exactProven;
    private final Long exactNodes;

    public BenchmarkPoint(int n, int r, int m, double density,
                          int dsaturSlots, double dsaturTimeMs,
                          int welshPowellSlots, double welshPowellTimeMs,
                          Integer exactSlots, Double exactTimeMs,
                          Boolean exactProven, Long exactNodes) {
        this.n = n;
        this.r = r;
        this.m = m;
        this.density = density;
        this.dsaturSlots = dsaturSlots;
        this.dsaturTimeMs = dsaturTimeMs;
        this.welshPowellSlots = welshPowellSlots;
        this.welshPowellTimeMs = welshPowellTimeMs;
        this.exactSlots = exactSlots;
        this.exactTimeMs = exactTimeMs;
        this.exactProven = exactProven;
        this.exactNodes = exactNodes;
    }

    public int getN() { return n; }
    public int getR() { return r; }
    public int getM() { return m; }
    public double getDensity() { return density; }
    public int getDsaturSlots() { return dsaturSlots; }
    public double getDsaturTimeMs() { return dsaturTimeMs; }
    public int getWelshPowellSlots() { return welshPowellSlots; }
    public double getWelshPowellTimeMs() { return welshPowellTimeMs; }
    public Integer getExactSlots() { return exactSlots; }
    public Double getExactTimeMs() { return exactTimeMs; }
    public Boolean getExactProven() { return exactProven; }
    public Long getExactNodes() { return exactNodes; }
}
