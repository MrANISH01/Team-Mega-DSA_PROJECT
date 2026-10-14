/**
 * ExamCraft 3D: Timetable Matrix Grid Renderer
 * Synchronizes slot assignments, colors, and interactive selection with 3D conflict graph.
 */

class TimetableGrid {
  constructor(tableContainerId) {
    this.container = document.getElementById(tableContainerId);
    this.graph = null;
    this.schedule = null;
    this.selectedCourseId = null;
    this.onCourseSelectCallback = null;
  }

  onCourseSelect(callback) {
    this.onCourseSelectCallback = callback;
  }

  render(graph, schedule) {
    this.graph = graph;
    this.schedule = schedule;
    if (!this.container) return;

    if (!schedule || !schedule.slotToCourses) {
      this.container.innerHTML = `<div style="text-align:center; padding:30px; color:var(--text-muted);">No schedule available yet. Run an algorithm above.</div>`;
      return;
    }

    const r = schedule.hallCapacity || (graph ? graph.hallCapacity : 3);
    const slotMap = schedule.slotToCourses;
    const sortedSlots = Object.keys(slotMap).map(Number).sort((a, b) => a - b);

    if (sortedSlots.length === 0) {
      this.container.innerHTML = `<div style="text-align:center; padding:30px; color:var(--text-muted);">Schedule has 0 time slots.</div>`;
      return;
    }

    let html = `
      <div class="timetable-scroll-wrapper">
        <table class="timetable-grid" id="exam-timetable-table">
          <thead>
            <tr>
              <th style="width: 100px;">Time Slot</th>
              <th style="width: 120px;">Occupancy</th>
    `;

    for (let h = 1; h <= r; h++) {
      html += `<th>Hall ${h}</th>`;
    }
    html += `</tr></thead><tbody>`;

    sortedSlots.forEach((slot) => {
      const courseIds = slotMap[slot] || [];
      const occupancy = courseIds.length;
      const slotColor = window.getSlotColor ? window.getSlotColor(slot) : "#14b8a6";
      const occupancyPct = Math.round((occupancy / r) * 100);

      html += `
        <tr>
          <td class="timetable-slot-label">
            <div style="display:flex; align-items:center; justify-content:center; gap:6px;">
              <span style="display:inline-block; width:10px; height:10px; border-radius:50%; background:${slotColor};"></span>
              Slot ${slot}
            </div>
          </td>
          <td style="text-align:center; font-family:var(--font-mono); font-size:11px;">
            <div style="display:flex; flex-direction:column; gap:2px; align-items:center;">
              <span style="font-weight:700;">${occupancy} / ${r} halls</span>
              <div style="width:60px; height:4px; background:rgba(255,255,255,0.1); border-radius:2px; overflow:hidden;">
                <div style="width:${occupancyPct}%; height:100%; background:${slotColor};"></div>
              </div>
            </div>
          </td>
      `;

      for (let h = 0; h < r; h++) {
        if (h < courseIds.length) {
          const cId = courseIds[h];
          const course = graph ? graph.courses.find(c => c.id === cId) : null;
          const code = course ? course.code : `C${cId}`;
          const name = course ? course.name : `Course ${cId}`;
          const deg = course ? course.degree : 0;
          const isSelected = (cId === this.selectedCourseId);

          html += `
            <td>
              <div class="course-chip ${isSelected ? 'highlighted' : ''}" 
                   data-course-id="${cId}"
                   style="background: linear-gradient(135deg, ${slotColor}, rgba(15,23,42,0.95)); border-left: 4px solid ${slotColor};">
                <div style="display:flex; justify-content:space-between; align-items:center;">
                  <span class="chip-code">${code}</span>
                  <span style="font-size:10px; opacity:0.8; font-family:var(--font-mono);">deg:${deg}</span>
                </div>
                <div class="chip-name">${name}</div>
              </div>
            </td>
          `;
        } else {
          html += `
            <td class="empty-hall-cell">
              <span>Empty Hall</span>
            </td>
          `;
        }
      }
      html += `</tr>`;
    });

    html += `</tbody></table></div>`;
    this.container.innerHTML = html;

    // Attach click events to course chips
    const chips = this.container.querySelectorAll(".course-chip");
    chips.forEach(chip => {
      chip.addEventListener("click", () => {
        const id = parseInt(chip.getAttribute("data-course-id"), 10);
        this.selectCourse(id);
      });
    });
  }

  selectCourse(courseId) {
    this.selectedCourseId = courseId;
    if (!this.container) return;

    const chips = this.container.querySelectorAll(".course-chip");
    chips.forEach(chip => {
      const id = parseInt(chip.getAttribute("data-course-id"), 10);
      if (id === courseId) {
        chip.classList.add("highlighted");
      } else {
        chip.classList.remove("highlighted");
      }
    });

    if (this.onCourseSelectCallback) {
      this.onCourseSelectCallback(courseId);
    }
  }

  clearSelection() {
    this.selectedCourseId = null;
    if (!this.container) return;
    const chips = this.container.querySelectorAll(".course-chip");
    chips.forEach(chip => chip.classList.remove("highlighted"));
  }
}

window.TimetableGrid = TimetableGrid;
