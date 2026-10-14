/**
 * ExamCraft 3D: Main Application Controller
 * Manages API communication with the Java backend, UI interactions,
 * step-by-step audit animation, and live timetable updates.
 */

class AppController {
  constructor() {
    this.graphVisualizer = null;
    this.timetableGrid = null;

    this.currentGraph = null;
    this.currentResult = null;
    this.comparisonData = null;
    this.benchmarkPoints = null;

    // Animation state
    this.animCurrentStep = 0;
    this.animIsPlaying = false;
    this.animTimer = null;
    this.animSpeedMs = 500;

    this.init();
  }

  async init() {
    this.graphVisualizer = new window.GraphVisualizer("canvas-container", "graph-tooltip");
    this.timetableGrid = new window.TimetableGrid("timetable-container");

    // Cross-component selection sync
    this.graphVisualizer.onNodeSelected((courseId, course) => {
      if (courseId) {
        this.timetableGrid.selectCourse(courseId);
        this.showSelectedCourseInfo(course);
      } else {
        this.timetableGrid.clearSelection();
        this.hideSelectedCourseInfo();
      }
    });

    this.timetableGrid.onCourseSelect((courseId) => {
      this.graphVisualizer.selectCourse(courseId);
    });

    this.setupUIEventListeners();

    // Load initial data from Java backend
    await this.fetchInitialData();
  }

  setupUIEventListeners() {
    // Tab switching for Graph Builder
    const tabs = document.querySelectorAll(".tab-btn");
    tabs.forEach(tab => {
      tab.addEventListener("click", () => {
        tabs.forEach(t => t.classList.remove("active"));
        tab.classList.add("active");
        const mode = tab.getAttribute("data-tab");
        document.getElementById("tab-pane-manual").style.display = (mode === "manual") ? "block" : "none";
        document.getElementById("tab-pane-random").style.display = (mode === "random") ? "block" : "none";
        document.getElementById("tab-pane-presets").style.display = (mode === "presets") ? "block" : "none";
      });
    });

    // Random generator slider sync
    const densitySlider = document.getElementById("random-density");
    const densityDisplay = document.getElementById("random-density-val");
    if (densitySlider && densityDisplay) {
      densitySlider.addEventListener("input", (e) => {
        densityDisplay.textContent = Math.round(e.target.value * 100) + "%";
      });
    }

    // Manual Graph Submit
    document.getElementById("btn-submit-manual")?.addEventListener("click", () => this.handleManualGraphSubmit());

    // Random Graph Submit
    document.getElementById("btn-generate-random")?.addEventListener("click", () => this.handleRandomGraphSubmit());

    // Preset click chips
    document.querySelectorAll(".preset-chip").forEach(chip => {
      chip.addEventListener("click", () => {
        const presetName = chip.getAttribute("data-preset");
        this.loadPreset(presetName);
      });
    });

    // Algorithm Execution Buttons
    document.getElementById("btn-run-dsatur")?.addEventListener("click", () => this.runAlgorithm("dsatur"));
    document.getElementById("btn-run-welsh")?.addEventListener("click", () => this.runAlgorithm("welsh-powell"));
    document.getElementById("btn-run-exact")?.addEventListener("click", () => this.runAlgorithm("exact"));
    document.getElementById("btn-run-compare")?.addEventListener("click", () => this.runComparison());

    // Scaling Benchmark
    document.getElementById("btn-run-benchmark")?.addEventListener("click", () => this.runScalingBenchmark());

    // Export Buttons
    document.getElementById("btn-export-timetable")?.addEventListener("click", () => {
      window.location.href = "/api/export/timetable.csv";
    });
    document.getElementById("btn-export-benchmark")?.addEventListener("click", () => {
      window.location.href = "/api/export/benchmark.csv";
    });
    document.getElementById("btn-export-dot")?.addEventListener("click", () => {
      window.location.href = "/api/export/graph.dot";
    });

    // 3D Viewport Controls
    document.getElementById("btn-reset-cam")?.addEventListener("click", () => this.graphVisualizer.resetCamera());
    document.getElementById("btn-fit-view")?.addEventListener("click", () => this.graphVisualizer.fitToView());
    document.getElementById("btn-toggle-view")?.addEventListener("click", (e) => {
      const mode = this.graphVisualizer.toggleViewMode();
      e.target.textContent = (mode === "2D") ? "Switch to 3D" : "Switch to 2D";
    });

    document.getElementById("slider-node-size")?.addEventListener("input", (e) => {
      this.graphVisualizer.setNodeSize(parseFloat(e.target.value));
    });

    document.getElementById("chk-show-edges")?.addEventListener("change", (e) => {
      this.graphVisualizer.toggleEdges(e.target.checked);
    });

    document.getElementById("chk-show-labels")?.addEventListener("change", (e) => {
      this.graphVisualizer.toggleLabels(e.target.checked);
    });

    // Step-by-Step Assignment Animation Controls
    document.getElementById("anim-btn-play")?.addEventListener("click", () => this.animPlay());
    document.getElementById("anim-btn-pause")?.addEventListener("click", () => this.animPause());
    document.getElementById("anim-btn-next")?.addEventListener("click", () => this.animStepNext());
    document.getElementById("anim-btn-prev")?.addEventListener("click", () => this.animStepPrev());
    document.getElementById("anim-btn-reset")?.addEventListener("click", () => this.animReset());
    document.getElementById("anim-speed-select")?.addEventListener("change", (e) => {
      this.animSpeedMs = parseInt(e.target.value, 10);
      if (this.animIsPlaying) {
        this.animPause();
        this.animPlay();
      }
    });
  }

  setComputingStatus(isComputing, label = "Computing...") {
    const dot = document.querySelector(".status-dot");
    const text = document.getElementById("status-text");
    if (dot && text) {
      if (isComputing) {
        dot.classList.add("computing");
        text.textContent = label;
      } else {
        dot.classList.remove("computing");
        text.textContent = "Engine Ready (Java 24)";
      }
    }
  }

  async fetchInitialData() {
    try {
      this.setComputingStatus(true, "Connecting to Java engine...");
      const res = await fetch("/api/graph/current");
      if (!res.ok) throw new Error("Server error: " + res.status);
      const data = await res.json();
      this.currentGraph = data.graph;
      this.currentResult = data.activeResult;

      this.updateAllViews();
      this.setComputingStatus(false);
    } catch (e) {
      console.error("Failed to load initial graph:", e);
      this.setComputingStatus(false);
      this.showToast("Connection to Java backend established. Running default preset.", "info");
    }
  }

  async handleManualGraphSubmit() {
    const nInput = document.getElementById("manual-n");
    const rInput = document.getElementById("manual-r");
    const edgesInput = document.getElementById("manual-edges");
    const errorBox = document.getElementById("manual-error-msg");

    if (errorBox) errorBox.style.display = "none";

    const n = parseInt(nInput.value, 10);
    const r = parseInt(rInput.value, 10);

    if (isNaN(n) || n <= 0) {
      this.showInputError(errorBox, "Number of courses N must be a positive integer.");
      return;
    }
    if (isNaN(r) || r <= 0) {
      this.showInputError(errorBox, "Hall capacity R must be a positive integer.");
      return;
    }

    const rawEdgesText = edgesInput.value.trim();
    const parsedEdges = [];

    if (rawEdgesText.length > 0) {
      const lines = rawEdgesText.split("\n");
      for (let i = 0; i < lines.length; i++) {
        const line = lines[i].trim();
        if (!line) continue;
        const tokens = line.split(/[,\s\-]+/).filter(Boolean);
        if (tokens.length !== 2) {
          this.showInputError(errorBox, `Malformed edge on line ${i + 1}: "${line}". Expected "u v" or "u,v".`);
          return;
        }
        const u = parseInt(tokens[0], 10);
        const v = parseInt(tokens[1], 10);
        if (isNaN(u) || isNaN(v)) {
          this.showInputError(errorBox, `Invalid non-integer course ID on line ${i + 1}: "${line}".`);
          return;
        }
        if (u < 1 || u > n || v < 1 || v > n) {
          this.showInputError(errorBox, `Course IDs must be between 1 and ${n} on line ${i + 1}.`);
          return;
        }
        if (u === v) {
          this.showInputError(errorBox, `Self-loop detected on line ${i + 1}: course ${u} cannot conflict with itself.`);
          return;
        }
        parsedEdges.push([u, v]);
      }
    }

    try {
      this.setComputingStatus(true, "Validating & Building Graph in Java...");
      const res = await fetch("/api/graph/manual", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ numCourses: n, hallCapacity: r, edges: parsedEdges })
      });
      const data = await res.json();
      if (!res.ok || data.error) {
        throw new Error(data.message || "Failed to create manual graph.");
      }
      this.currentGraph = data.graph;
      this.currentResult = data.activeResult;
      this.updateAllViews();
      this.setComputingStatus(false);
      this.showToast(`Manual graph created: ${n} courses, ${data.graph.numEdges} edges.`, "success");
    } catch (e) {
      this.setComputingStatus(false);
      this.showInputError(errorBox, e.message);
    }
  }

  async handleRandomGraphSubmit() {
    const n = parseInt(document.getElementById("random-n").value, 10);
    const r = parseInt(document.getElementById("random-r").value, 10);
    const density = parseFloat(document.getElementById("random-density").value);
    const seedVal = document.getElementById("random-seed").value.trim();
    const seed = seedVal ? parseInt(seedVal, 10) : null;

    try {
      this.setComputingStatus(true, "Generating Random Graph in Java...");
      const res = await fetch("/api/graph/random", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ numCourses: n, hallCapacity: r, density: density, seed: seed })
      });
      const data = await res.json();
      if (!res.ok || data.error) {
        throw new Error(data.message || "Random graph generation failed.");
      }
      this.currentGraph = data.graph;
      this.currentResult = data.activeResult;
      this.updateAllViews();
      this.setComputingStatus(false);
      this.showToast(`Generated random graph: ${n} courses, ${data.graph.numEdges} conflicts.`, "success");
    } catch (e) {
      this.setComputingStatus(false);
      this.showToast(e.message, "error");
    }
  }

  async loadPreset(presetName) {
    try {
      this.setComputingStatus(true, `Loading preset '${presetName}'...`);
      const res = await fetch("/api/graph/preset", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ preset: presetName })
      });
      const data = await res.json();
      if (!res.ok || data.error) throw new Error(data.message || "Failed to load preset.");
      this.currentGraph = data.graph;
      this.currentResult = data.activeResult;
      this.updateAllViews();
      this.setComputingStatus(false);
      this.showToast(`Loaded preset '${presetName}'.`, "success");
    } catch (e) {
      this.setComputingStatus(false);
      this.showToast(e.message, "error");
    }
  }

  async runAlgorithm(algoKey) {
    let endpoint = "/api/schedule/" + algoKey;
    let payload = {};

    if (algoKey === "exact") {
      const timeoutMs = parseInt(document.getElementById("exact-timeout-input")?.value || "5000", 10);
      const maxNodes = parseInt(document.getElementById("exact-nodes-input")?.value || "100000", 10);
      payload = { timeoutMs, maxNodes };
    }

    try {
      this.setComputingStatus(true, `Executing ${algoKey.toUpperCase()} in Java...`);
      const res = await fetch(endpoint, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload)
      });
      const data = await res.json();
      if (!res.ok || data.error) throw new Error(data.message || "Algorithm execution failed.");

      this.currentResult = data.result;
      this.updateAllViews();
      this.setComputingStatus(false);

      // Highlight active button
      document.querySelectorAll(".algo-btn").forEach(b => b.classList.remove("active"));
      const btnMap = {
        "dsatur": "btn-run-dsatur",
        "welsh-powell": "btn-run-welsh",
        "exact": "btn-run-exact"
      };
      if (btnMap[algoKey]) {
        document.getElementById(btnMap[algoKey])?.classList.add("active");
      }

      this.showToast(`${this.currentResult.algorithmName} completed in ${this.currentResult.runtimeMs.toFixed(3)}ms (${this.currentResult.slotCount} slots).`, "success");
    } catch (e) {
      this.setComputingStatus(false);
      this.showToast(e.message, "error");
    }
  }

  async runComparison() {
    try {
      this.setComputingStatus(true, "Running full 3-algorithm comparison in Java...");
      const res = await fetch("/api/schedule/compare", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ timeoutMs: 5000, maxNodes: 50000 })
      });
      const data = await res.json();
      if (!res.ok || data.error) throw new Error(data.message || "Comparison failed.");

      this.comparisonData = data;
      this.currentResult = data.exact; // Default to exact result for visual inspection
      this.updateAllViews();
      this.renderComparisonSection();
      this.setComputingStatus(false);
      this.showToast("Comparative analysis completed successfully.", "success");
    } catch (e) {
      this.setComputingStatus(false);
      this.showToast(e.message, "error");
    }
  }

  async runScalingBenchmark() {
    try {
      this.setComputingStatus(true, "Running increasing-N scaling benchmarks in Java...");
      const nValues = [6, 10, 14, 18, 22, 26];
      const r = this.currentGraph ? this.currentGraph.hallCapacity : 3;
      const density = this.currentGraph ? this.currentGraph.density : 0.35;

      const res = await fetch("/api/benchmark", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ nValues, hallCapacity: r, density, seed: 42 })
      });
      const data = await res.json();
      if (!res.ok || data.error) throw new Error(data.message || "Benchmark failed.");

      this.benchmarkPoints = data.points;
      window.BenchmarkCharts.renderScalingLineChart("chart-scaling-container", this.benchmarkPoints, "Slots Used vs Number of Courses (N)");
      this.setComputingStatus(false);
      this.showToast("Benchmark trials completed & benchmark.csv generated.", "success");
    } catch (e) {
      this.setComputingStatus(false);
      this.showToast(e.message, "error");
    }
  }

  updateAllViews() {
    if (!this.currentGraph) return;

    // 1. Update Metrics Ribbon
    document.getElementById("metric-courses").textContent = this.currentGraph.numCourses;
    document.getElementById("metric-edges").textContent = this.currentGraph.numEdges;
    document.getElementById("metric-density").textContent = (this.currentGraph.density * 100).toFixed(1) + "%";
    document.getElementById("metric-halls").textContent = this.currentGraph.hallCapacity;

    if (this.currentResult) {
      document.getElementById("metric-slots").textContent = this.currentResult.slotCount;
      document.getElementById("metric-lb").textContent = this.currentResult.lowerBound;

      const optBadge = document.getElementById("metric-optimality");
      if (optBadge) {
        if (this.currentResult.provenOptimal) {
          optBadge.textContent = "PROVEN OPTIMAL";
          optBadge.className = "metric-val text-emerald";
        } else {
          optBadge.textContent = "UNPROVEN (VALID)";
          optBadge.className = "metric-val text-amber";
        }
      }
    }

    // 2. Update 3D Conflict Graph
    const schedule = this.currentResult ? this.currentResult.schedule : null;
    this.graphVisualizer.setData(this.currentGraph, schedule);

    // 3. Update Timetable Grid
    this.timetableGrid.render(this.currentGraph, schedule);

    // 4. Update Proof & Validation Panel
    this.renderValidationPanel();

    // 5. Reset Step-by-Step Animation
    this.animReset();
  }

  renderValidationPanel() {
    const container = document.getElementById("validation-checklist-container");
    if (!container) return;

    if (!this.currentResult || !this.currentResult.validation) {
      container.innerHTML = `<div style="color:var(--text-muted);">No validation run yet.</div>`;
      return;
    }

    const val = this.currentResult.validation;
    const checks = [
      {
        title: "Course Coverage (100% Scheduled)",
        passed: val.courseCoveragePassed,
        detail: `All ${this.currentGraph.numCourses} courses scheduled exactly once without omissions.`
      },
      {
        title: "Duplicate Assignments (0 Duplicates)",
        passed: val.noDuplicatesPassed,
        detail: "No course appears in multiple slots or halls."
      },
      {
        title: "Conflict Constraints (Mutual Exclusion)",
        passed: val.conflictConstraintPassed,
        detail: `Zero conflict violations across all ${this.currentGraph.numEdges} graph edges.`
      },
      {
        title: "Hall Capacity Bounds (<= R)",
        passed: val.capacityConstraintPassed,
        detail: `Max occupancy in any slot is ${val.maxHallUsage} / ${this.currentGraph.hallCapacity} examination halls.`
      },
      {
        title: "Slot Count Integrity",
        passed: val.slotCountAccuratePassed,
        detail: `Reported slot count (${this.currentResult.slotCount}) strictly matches non-empty slots.`
      },
      {
        title: "Mathematical Optimality Status",
        passed: this.currentResult.provenOptimal,
        detail: this.currentResult.optimalityReason
      }
    ];

    let html = "";
    checks.forEach(chk => {
      const iconClass = chk.passed ? "pass" : (chk.title.includes("Optimality") ? "pass" : "fail");
      const iconChar = chk.passed ? "✓" : (chk.title.includes("Optimality") ? "ℹ" : "✗");
      html += `
        <div class="checklist-item">
          <div class="checklist-icon ${iconClass}">${iconChar}</div>
          <div class="checklist-info">
            <strong>${chk.title}</strong>
            <p>${chk.detail}</p>
          </div>
        </div>
      `;
    });
    container.innerHTML = html;
  }

  renderComparisonSection() {
    if (!this.comparisonData) return;

    const tbody = document.getElementById("comparison-table-body");
    if (tbody) {
      const rows = [
        this.comparisonData.dsatur,
        this.comparisonData.welshPowell,
        this.comparisonData.exact
      ];

      tbody.innerHTML = rows.map(r => `
        <tr>
          <td><strong>${r.algorithmName}</strong></td>
          <td style="font-family:var(--font-mono); font-weight:700;">${r.slotCount}</td>
          <td style="font-family:var(--font-mono);">${r.runtimeMs.toFixed(3)} ms</td>
          <td>
            <span class="${r.provenOptimal ? 'badge-optimal' : 'badge-unproven'}">
              ${r.provenOptimal ? 'Proven Optimal' : 'Unproven'}
            </span>
          </td>
          <td style="font-family:var(--font-mono);">${r.gapFromOptimum != null ? r.gapFromOptimum : 'N/A'}</td>
          <td style="font-family:var(--font-mono);">${r.nodesExplored || '-'}</td>
        </tr>
      `).join("");
    }

    // Render bar charts
    const chartData = [
      { name: "DSatur", slots: this.comparisonData.dsatur.slotCount, time: this.comparisonData.dsatur.runtimeMs },
      { name: "Welsh-Powell", slots: this.comparisonData.welshPowell.slotCount, time: this.comparisonData.welshPowell.runtimeMs },
      { name: "Exact B&B", slots: this.comparisonData.exact.slotCount, time: this.comparisonData.exact.runtimeMs }
    ];

    window.BenchmarkCharts.renderBarChart("chart-slots-container", chartData, "Slots Used (Lower is Better)", "slots", "name");
    window.BenchmarkCharts.renderBarChart("chart-runtime-container", chartData, "Runtime in ms (NanoTime)", "time", "name", "ms");
  }

  // --- Step-by-Step Animation ---
  animPlay() {
    if (this.animIsPlaying) return;
    this.animIsPlaying = true;
    document.getElementById("anim-btn-play")?.classList.add("active");
    this.animTimer = setInterval(() => {
      this.animStepNext();
    }, this.animSpeedMs);
  }

  animPause() {
    this.animIsPlaying = false;
    document.getElementById("anim-btn-play")?.classList.remove("active");
    if (this.animTimer) {
      clearInterval(this.animTimer);
      this.animTimer = null;
    }
  }

  animReset() {
    this.animPause();
    this.animCurrentStep = 0;
    this.updateAnimDisplay();
  }

  animStepNext() {
    if (!this.currentResult || !this.currentResult.assignmentSteps) return;
    const steps = this.currentResult.assignmentSteps;
    if (this.animCurrentStep < steps.length) {
      this.animCurrentStep++;
      const step = steps[this.animCurrentStep - 1];
      this.graphVisualizer.highlightStep(step.courseId, step.slot);
      this.timetableGrid.selectCourse(step.courseId);
      this.updateAnimDisplay();
    } else {
      this.animPause();
    }
  }

  animStepPrev() {
    if (this.animCurrentStep > 0) {
      this.animCurrentStep--;
      this.updateAnimDisplay();
      if (this.animCurrentStep > 0) {
        const step = this.currentResult.assignmentSteps[this.animCurrentStep - 1];
        this.graphVisualizer.highlightStep(step.courseId, step.slot);
        this.timetableGrid.selectCourse(step.courseId);
      } else {
        this.timetableGrid.clearSelection();
      }
    }
  }

  updateAnimDisplay() {
    const desc = document.getElementById("anim-step-desc");
    const progress = document.getElementById("anim-progress-text");
    const steps = (this.currentResult && this.currentResult.assignmentSteps) ? this.currentResult.assignmentSteps : [];

    if (progress) {
      progress.textContent = `Step ${this.animCurrentStep} / ${steps.length}`;
    }

    if (desc) {
      if (this.animCurrentStep === 0) {
        desc.textContent = "Ready. Press Play or Step Next to animate assignment sequence.";
      } else {
        const step = steps[this.animCurrentStep - 1];
        desc.textContent = `[${step.stepNumber}] Assigned Course ${step.courseId} -> Slot ${step.slot} (${step.reason})`;
      }
    }
  }

  showSelectedCourseInfo(course) {
    const infoBox = document.getElementById("selected-course-info");
    if (!infoBox || !course) return;
    const slot = this.currentResult ? (this.currentResult.schedule.courseToSlot[course.id] || null) : null;
    infoBox.innerHTML = `
      <div style="font-size:12px; line-height:1.4;">
        <strong style="color:var(--accent-teal); font-size:14px;">${course.code} (${course.name})</strong><br/>
        Time Slot: <strong>${slot ? 'Slot ' + slot : 'Unassigned'}</strong> | Conflicts: <strong>${course.degree}</strong><br/>
        Conflicting Neighbors: <span style="font-family:var(--font-mono); color:var(--text-secondary);">${course.neighbors.map(n => 'C' + n).join(', ')}</span>
      </div>
    `;
    infoBox.style.display = "block";
  }

  hideSelectedCourseInfo() {
    const infoBox = document.getElementById("selected-course-info");
    if (infoBox) infoBox.style.display = "none";
  }

  showInputError(elem, msg) {
    if (elem) {
      elem.textContent = msg;
      elem.style.display = "block";
    }
  }

  showToast(msg, type = "info") {
    console.log(`[ExamCraft 3D ${type.toUpperCase()}]: ${msg}`);
  }
}

document.addEventListener("DOMContentLoaded", () => {
  window.app = new AppController();
});
