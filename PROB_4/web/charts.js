/**
 * ExamCraft 3D: High-Performance SVG Charts for Algorithm Benchmarks & Comparative Analysis.
 * Pure native SVG rendering with zero external library overhead.
 */

class BenchmarkCharts {
  static renderBarChart(containerId, data, title, valueKey, labelKey, valueSuffix = "") {
    const container = document.getElementById(containerId);
    if (!container) return;

    if (!data || data.length === 0) {
      container.innerHTML = `<div style="text-align:center; padding:40px; color:var(--text-muted);">No comparison data yet.</div>`;
      return;
    }

    const width = container.clientWidth || 500;
    const height = 240;
    const padding = { top: 30, right: 30, bottom: 45, left: 55 };

    const chartW = width - padding.left - padding.right;
    const chartH = height - padding.top - padding.bottom;

    const values = data.map(d => d[valueKey] != null ? d[valueKey] : 0);
    const maxVal = Math.max(...values, 1);

    const barWidth = Math.min(60, (chartW / data.length) * 0.55);
    const gap = chartW / data.length;

    const colors = ["#14b8a6", "#6366f1", "#f59e0b"];

    let svg = `<svg viewBox="0 0 ${width} ${height}" width="100%" height="100%" style="font-family:sans-serif;">`;

    // Title
    svg += `<text x="${padding.left}" y="20" fill="#94a3b8" font-size="12" font-weight="700" text-anchor="start">${title}</text>`;

    // Y Axis Grid lines and ticks
    const ticks = 4;
    for (let i = 0; i <= ticks; i++) {
      const val = (maxVal / ticks) * i;
      const y = padding.top + chartH - (i / ticks) * chartH;
      svg += `
        <line x1="${padding.left}" y1="${y}" x2="${width - padding.right}" y2="${y}" stroke="rgba(255,255,255,0.06)" stroke-dasharray="2,2" />
        <text x="${padding.left - 8}" y="${y + 4}" fill="#64748b" font-size="10" text-anchor="end" font-family="monospace">${val >= 10 ? Math.round(val) : val.toFixed(1)}</text>
      `;
    }

    // Bars
    data.forEach((d, idx) => {
      const val = d[valueKey] != null ? d[valueKey] : 0;
      const barH = (val / maxVal) * chartH;
      const x = padding.left + idx * gap + (gap - barWidth) / 2;
      const y = padding.top + chartH - barH;
      const color = colors[idx % colors.length];

      svg += `
        <g class="chart-bar-group">
          <rect x="${x}" y="${y}" width="${barWidth}" height="${barH}" fill="${color}" rx="4" opacity="0.9">
            <title>${d[labelKey]}: ${val}${valueSuffix}</title>
          </rect>
          <text x="${x + barWidth / 2}" y="${y - 6}" fill="#f8fafc" font-size="11" font-weight="700" text-anchor="middle" font-family="monospace">
            ${val}${valueSuffix}
          </text>
          <text x="${x + barWidth / 2}" y="${height - 15}" fill="#94a3b8" font-size="11" font-weight="600" text-anchor="middle">
            ${d[labelKey]}
          </text>
        </g>
      `;
    });

    svg += `</svg>`;
    container.innerHTML = svg;
  }

  static renderScalingLineChart(containerId, points, title) {
    const container = document.getElementById(containerId);
    if (!container) return;

    if (!points || points.length === 0) {
      container.innerHTML = `<div style="text-align:center; padding:40px; color:var(--text-muted);">Run scaling benchmark to view curve.</div>`;
      return;
    }

    const width = container.clientWidth || 500;
    const height = 260;
    const padding = { top: 35, right: 30, bottom: 45, left: 55 };

    const chartW = width - padding.left - padding.right;
    const chartH = height - padding.top - padding.bottom;

    const nVals = points.map(p => p.n);
    const minN = Math.min(...nVals);
    const maxN = Math.max(...nVals);

    const dsaturSlots = points.map(p => p.dsaturSlots);
    const welshSlots = points.map(p => p.welshPowellSlots);
    const exactSlots = points.filter(p => p.exactSlots != null).map(p => p.exactSlots);

    const allSlots = [...dsaturSlots, ...welshSlots, ...exactSlots];
    const maxSlot = Math.max(...allSlots, 1);

    const getX = (n) => padding.left + ((n - minN) / Math.max(1, maxN - minN)) * chartW;
    const getY = (s) => padding.top + chartH - (s / maxSlot) * chartH;

    let svg = `<svg viewBox="0 0 ${width} ${height}" width="100%" height="100%" style="font-family:sans-serif;">`;

    // Title and Legend
    svg += `
      <text x="${padding.left}" y="20" fill="#94a3b8" font-size="12" font-weight="700" text-anchor="start">${title}</text>
      <g transform="translate(${width - 240}, 12)" font-size="10">
        <circle cx="0" cy="4" r="4" fill="#14b8a6"/>
        <text x="8" y="7" fill="#94a3b8">DSatur</text>
        <circle cx="65" cy="4" r="4" fill="#6366f1"/>
        <text x="73" y="7" fill="#94a3b8">Welsh-Powell</text>
        <circle cx="150" cy="4" r="4" fill="#f59e0b"/>
        <text x="158" y="7" fill="#94a3b8">Exact B&B</text>
      </g>
    `;

    // Y Axis Grid
    const yTicks = 4;
    for (let i = 0; i <= yTicks; i++) {
      const val = Math.round((maxSlot / yTicks) * i);
      const y = padding.top + chartH - (i / yTicks) * chartH;
      svg += `
        <line x1="${padding.left}" y1="${y}" x2="${width - padding.right}" y2="${y}" stroke="rgba(255,255,255,0.06)" stroke-dasharray="2,2"/>
        <text x="${padding.left - 8}" y="${y + 4}" fill="#64748b" font-size="10" text-anchor="end" font-family="monospace">${val}</text>
      `;
    }

    // X Axis Ticks
    points.forEach(p => {
      const x = getX(p.n);
      svg += `
        <text x="${x}" y="${height - 15}" fill="#94a3b8" font-size="10" text-anchor="middle" font-family="monospace">N=${p.n}</text>
      `;
    });

    // Path builder helper
    const buildPath = (pts, key) => {
      let d = "";
      pts.forEach((p, idx) => {
        if (p[key] == null) return;
        const x = getX(p.n);
        const y = getY(p[key]);
        d += (idx === 0 || d === "" ? `M ${x} ${y}` : ` L ${x} ${y}`);
      });
      return d;
    };

    // DSatur Line
    svg += `<path d="${buildPath(points, 'dsaturSlots')}" fill="none" stroke="#14b8a6" stroke-width="2.5" />`;
    // Welsh Powell Line
    svg += `<path d="${buildPath(points, 'welshPowellSlots')}" fill="none" stroke="#6366f1" stroke-width="2" stroke-dasharray="4,3"/>`;
    // Exact Line
    const exactPts = points.filter(p => p.exactSlots != null);
    if (exactPts.length > 0) {
      svg += `<path d="${buildPath(exactPts, 'exactSlots')}" fill="none" stroke="#f59e0b" stroke-width="2.5" />`;
    }

    // Dots
    points.forEach(p => {
      const x = getX(p.n);
      svg += `<circle cx="${x}" cy="${getY(p.dsaturSlots)}" r="3.5" fill="#14b8a6"><title>DSatur: N=${p.n}, slots=${p.dsaturSlots}</title></circle>`;
      svg += `<circle cx="${x}" cy="${getY(p.welshPowellSlots)}" r="3" fill="#6366f1"><title>Welsh-Powell: N=${p.n}, slots=${p.welshPowellSlots}</title></circle>`;
      if (p.exactSlots != null) {
        svg += `<circle cx="${x}" cy="${getY(p.exactSlots)}" r="3.5" fill="#f59e0b"><title>Exact: N=${p.n}, slots=${p.exactSlots}</title></circle>`;
      }
    });

    svg += `</svg>`;
    container.innerHTML = svg;
  }
}

window.BenchmarkCharts = BenchmarkCharts;
