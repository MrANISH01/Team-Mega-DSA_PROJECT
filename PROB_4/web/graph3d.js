/**
 * ExamCraft 3D: Conflict Graph Visualizer
 * Supports interactive Three.js 3D WebGL rendering with OrbitControls
 * and an automatic 2D Canvas fallback.
 */

const PALETTE = [
  "#14b8a6", "#f59e0b", "#6366f1", "#ec4899",
  "#10b981", "#8b5cf6", "#f97316", "#06b6d4",
  "#3b82f6", "#e11d48", "#84cc16", "#d946ef"
];

function getSlotColor(slot) {
  if (!slot || slot <= 0) return "#475569"; // Unassigned slate
  return PALETTE[(slot - 1) % PALETTE.length];
}

class GraphVisualizer {
  constructor(containerId, tooltipId) {
    this.container = document.getElementById(containerId);
    this.tooltip = document.getElementById(tooltipId);
    this.is2D = false;
    this.showEdges = true;
    this.showLabels = true;
    this.nodeBaseRadius = 8;
    this.selectedNodeId = null;
    this.onNodeSelectedCallback = null;

    this.graphData = null;
    this.scheduleData = null;
    this.nodePositions = {}; // id -> {x, y, z, vx, vy, vz}

    this.initThree();
    this.init2DCanvas();
    this.setupEvents();
  }

  onNodeSelected(callback) {
    this.onNodeSelectedCallback = callback;
  }

  initThree() {
    this.threeCanvas = document.createElement("canvas");
    this.threeCanvas.id = "canvas-3d";
    this.container.appendChild(this.threeCanvas);

    try {
      this.renderer = new THREE.WebGLRenderer({
        canvas: this.threeCanvas,
        antialias: true,
        alpha: true
      });
      this.renderer.setSize(this.container.clientWidth, this.container.clientHeight);
      this.renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));

      this.scene = new THREE.Scene();

      this.camera = new THREE.PerspectiveCamera(
        45,
        this.container.clientWidth / this.container.clientHeight,
        0.1,
        2000
      );
      this.camera.position.set(0, 150, 450);

      // Lights
      const ambientLight = new THREE.AmbientLight(0xffffff, 0.7);
      this.scene.add(ambientLight);

      const dirLight1 = new THREE.DirectionalLight(0xffffff, 0.8);
      dirLight1.position.set(200, 300, 200);
      this.scene.add(dirLight1);

      const dirLight2 = new THREE.DirectionalLight(0x6366f1, 0.4);
      dirLight2.position.set(-200, -100, -200);
      this.scene.add(dirLight2);

      // Orbit controls
      if (typeof THREE.OrbitControls !== "undefined") {
        this.controls = new THREE.OrbitControls(this.camera, this.renderer.domElement);
        this.controls.enableDamping = true;
        this.controls.dampingFactor = 0.08;
      }

      this.nodeMeshMap = new Map(); // courseId -> Mesh
      this.edgeLineGroup = new THREE.Group();
      this.scene.add(this.edgeLineGroup);
      this.labelsGroup = new THREE.Group();
      this.scene.add(this.labelsGroup);

      this.raycaster = new THREE.Raycaster();
      this.mouse = new THREE.Vector2(-1000, -1000);

      this.animate = this.animate.bind(this);
      requestAnimationFrame(this.animate);
    } catch (e) {
      console.warn("Three.js WebGL not available or failed. Falling back to 2D Canvas.", e);
      this.is2D = true;
      this.threeCanvas.style.display = "none";
    }
  }

  init2DCanvas() {
    this.canvas2D = document.createElement("canvas");
    this.canvas2D.id = "canvas-2d";
    this.canvas2D.style.display = this.is2D ? "block" : "none";
    this.container.appendChild(this.canvas2D);
    this.ctx2D = this.canvas2D.getContext("2d");
    this.resize2D();
  }

  resize2D() {
    if (!this.canvas2D) return;
    this.canvas2D.width = this.container.clientWidth;
    this.canvas2D.height = this.container.clientHeight;
  }

  setupEvents() {
    window.addEventListener("resize", () => {
      const width = this.container.clientWidth;
      const height = this.container.clientHeight;
      if (this.renderer && this.camera) {
        this.camera.aspect = width / height;
        this.camera.updateProjectionMatrix();
        this.renderer.setSize(width, height);
      }
      this.resize2D();
      if (this.is2D) this.draw2D();
    });

    const getPointerPos = (e) => {
      const rect = this.container.getBoundingClientRect();
      const x = e.clientX - rect.left;
      const y = e.clientY - rect.top;
      return { x, y, normX: (x / rect.width) * 2 - 1, normY: -(y / rect.height) * 2 + 1 };
    };

    this.container.addEventListener("mousemove", (e) => {
      const p = getPointerPos(e);
      if (!this.is2D && this.mouse) {
        this.mouse.x = p.normX;
        this.mouse.y = p.normY;
        this.handle3DHover(e.clientX, e.clientY);
      } else if (this.is2D) {
        this.handle2DHover(p.x, p.y, e.clientX, e.clientY);
      }
    });

    this.container.addEventListener("mouseleave", () => {
      if (this.tooltip) this.tooltip.style.display = "none";
      if (!this.is2D && this.mouse) {
        this.mouse.x = -1000;
        this.mouse.y = -1000;
      }
    });

    this.container.addEventListener("click", (e) => {
      const p = getPointerPos(e);
      if (!this.is2D && this.raycaster && this.camera) {
        this.raycaster.setFromCamera(this.mouse, this.camera);
        const meshes = Array.from(this.nodeMeshMap.values());
        const intersects = this.raycaster.intersectObjects(meshes);
        if (intersects.length > 0) {
          const hitMesh = intersects[0].object;
          const courseId = hitMesh.userData.courseId;
          this.selectCourse(courseId);
        } else {
          this.clearSelection();
        }
      } else if (this.is2D) {
        const hitId = this.find2DNodeAt(p.x, p.y);
        if (hitId) {
          this.selectCourse(hitId);
        } else {
          this.clearSelection();
        }
      }
    });
  }

  toggleViewMode() {
    this.is2D = !this.is2D;
    if (this.is2D) {
      if (this.threeCanvas) this.threeCanvas.style.display = "none";
      if (this.canvas2D) {
        this.canvas2D.style.display = "block";
        this.resize2D();
        this.draw2D();
      }
    } else {
      if (this.canvas2D) this.canvas2D.style.display = "none";
      if (this.threeCanvas) {
        this.threeCanvas.style.display = "block";
        const width = this.container.clientWidth;
        const height = this.container.clientHeight;
        if (this.camera && this.renderer) {
          this.camera.aspect = width / height;
          this.camera.updateProjectionMatrix();
          this.renderer.setSize(width, height);
        }
      }
    }
    return this.is2D ? "2D" : "3D";
  }

  setNodeSize(size) {
    this.nodeBaseRadius = size;
    if (this.nodeMeshMap) {
      for (const [id, mesh] of this.nodeMeshMap.entries()) {
        const scale = size / 8;
        mesh.scale.set(scale, scale, scale);
      }
    }
    if (this.is2D) this.draw2D();
  }

  toggleEdges(show) {
    this.showEdges = show;
    if (this.edgeLineGroup) this.edgeLineGroup.visible = show;
    if (this.is2D) this.draw2D();
  }

  toggleLabels(show) {
    this.showLabels = show;
    if (this.labelsGroup) this.labelsGroup.visible = show;
    if (this.is2D) this.draw2D();
  }

  resetCamera() {
    if (this.camera && this.controls) {
      this.camera.position.set(0, 150, 450);
      this.controls.target.set(0, 0, 0);
      this.controls.update();
    }
  }

  fitToView() {
    if (!this.graphData || !this.camera || !this.controls) return;
    this.camera.position.set(0, 180, 520);
    this.controls.target.set(0, 0, 0);
    this.controls.update();
  }

  setData(graph, schedule) {
    this.graphData = graph;
    this.scheduleData = schedule;
    this.computeLayout();
    this.rebuild3DScene();
    if (this.is2D) this.draw2D();
  }

  computeLayout() {
    if (!this.graphData || !this.graphData.courses) return;
    const courses = this.graphData.courses;
    const n = courses.length;

    this.nodePositions = {};

    // Spherical force-balanced layout
    const radius = Math.min(220, 90 + n * 10);
    const phiSpan = Math.PI * (3 - Math.sqrt(5)); // Golden angle

    courses.forEach((c, idx) => {
      const y = 1 - (idx / Math.max(1, n - 1)) * 2; // from 1 to -1
      const radAtY = Math.sqrt(Math.max(0, 1 - y * y));
      const theta = phiSpan * idx;

      const x = Math.cos(theta) * radAtY;
      const z = Math.sin(theta) * radAtY;

      this.nodePositions[c.id] = {
        x: x * radius,
        y: y * radius * 0.85,
        z: z * radius
      };
    });
  }

  rebuild3DScene() {
    if (!this.scene) return;

    // Clear existing nodes and labels
    for (const mesh of this.nodeMeshMap.values()) {
      this.scene.remove(mesh);
    }
    this.nodeMeshMap.clear();

    while (this.edgeLineGroup.children.length > 0) {
      this.edgeLineGroup.remove(this.edgeLineGroup.children[0]);
    }
    while (this.labelsGroup.children.length > 0) {
      this.labelsGroup.remove(this.labelsGroup.children[0]);
    }

    if (!this.graphData || !this.graphData.courses) return;

    const sphereGeo = new THREE.SphereGeometry(this.nodeBaseRadius, 24, 24);

    // Build nodes
    this.graphData.courses.forEach((c) => {
      const pos = this.nodePositions[c.id] || { x: 0, y: 0, z: 0 };
      const slot = this.getSlotForCourse(c.id);
      const colorHex = getSlotColor(slot);

      const mat = new THREE.MeshStandardMaterial({
        color: new THREE.Color(colorHex),
        metalness: 0.15,
        roughness: 0.35,
        emissive: new THREE.Color(colorHex),
        emissiveIntensity: 0.15
      });

      const mesh = new THREE.Mesh(sphereGeo, mat);
      mesh.position.set(pos.x, pos.y, pos.z);
      mesh.userData = { courseId: c.id, course: c, slot: slot, baseColor: colorHex };

      this.scene.add(mesh);
      this.nodeMeshMap.set(c.id, mesh);

      // Create text sprite label
      const sprite = this.makeTextSprite(c.code, colorHex);
      sprite.position.set(pos.x, pos.y + this.nodeBaseRadius + 8, pos.z);
      sprite.userData = { courseId: c.id };
      this.labelsGroup.add(sprite);
    });

    // Build conflict edges
    if (this.graphData.edges) {
      const linePositions = [];
      this.graphData.edges.forEach((edge) => {
        const u = edge[0];
        const v = edge[1];
        const p1 = this.nodePositions[u];
        const p2 = this.nodePositions[v];
        if (p1 && p2) {
          linePositions.push(p1.x, p1.y, p1.z);
          linePositions.push(p2.x, p2.y, p2.z);
        }
      });

      const lineGeo = new THREE.BufferGeometry();
      lineGeo.setAttribute("position", new THREE.Float32BufferAttribute(linePositions, 3));
      const lineMat = new THREE.LineBasicMaterial({
        color: 0x64748b,
        transparent: true,
        opacity: 0.35,
        linewidth: 1
      });
      const lines = new THREE.LineSegments(lineGeo, lineMat);
      this.edgeLineGroup.add(lines);
    }
  }

  makeTextSprite(message, colorHex) {
    const canvas = document.createElement("canvas");
    canvas.width = 128;
    canvas.height = 64;
    const ctx = canvas.getContext("2d");

    ctx.fillStyle = "rgba(15, 23, 42, 0.75)";
    ctx.roundRect ? ctx.roundRect(8, 12, 112, 40, 8) : ctx.rect(8, 12, 112, 40);
    ctx.fill();

    ctx.font = "bold 22px 'Inter', sans-serif";
    ctx.fillStyle = colorHex || "#ffffff";
    ctx.textAlign = "center";
    ctx.textBaseline = "middle";
    ctx.fillText(message, 64, 32);

    const texture = new THREE.CanvasTexture(canvas);
    const spriteMaterial = new THREE.SpriteMaterial({ map: texture, depthTest: false });
    const sprite = new THREE.Sprite(spriteMaterial);
    sprite.scale.set(24, 12, 1);
    return sprite;
  }

  getSlotForCourse(courseId) {
    if (!this.scheduleData) return null;
    if (this.scheduleData.courseToSlot) {
      return this.scheduleData.courseToSlot[courseId] || null;
    }
    return null;
  }

  handle3DHover(clientX, clientY) {
    if (!this.raycaster || !this.camera) return;
    this.raycaster.setFromCamera(this.mouse, this.camera);
    const meshes = Array.from(this.nodeMeshMap.values());
    const intersects = this.raycaster.intersectObjects(meshes);

    if (intersects.length > 0) {
      const hit = intersects[0].object;
      const c = hit.userData.course;
      const slot = hit.userData.slot;
      this.showTooltip(clientX, clientY, c, slot);
      document.body.style.cursor = "pointer";
    } else {
      this.hideTooltip();
      document.body.style.cursor = "default";
    }
  }

  showTooltip(x, y, course, slot) {
    if (!this.tooltip || !course) return;
    const slotText = (slot && slot > 0) ? `Slot ${slot}` : "Unassigned";
    const slotColor = getSlotColor(slot);
    this.tooltip.innerHTML = `
      <div class="graph-tooltip-title">${course.code} — ${course.name}</div>
      <div style="margin-top: 4px; display: flex; align-items: center; gap: 6px;">
        <span style="display:inline-block; width:10px; height:10px; border-radius:50%; background:${slotColor};"></span>
        <strong>${slotText}</strong>
      </div>
      <div style="color: var(--text-secondary); margin-top: 4px;">
        Conflicts (Degree): <strong>${course.degree}</strong>
      </div>
      <div style="font-size: 10px; color: var(--text-muted); margin-top: 2px;">
        Conflicting Neighbors: ${course.neighbors ? course.neighbors.slice(0, 8).map(n => 'C' + n).join(', ') : 'None'}
      </div>
    `;
    this.tooltip.style.left = (x + 15) + "px";
    this.tooltip.style.top = (y + 15) + "px";
    this.tooltip.style.display = "block";
  }

  hideTooltip() {
    if (this.tooltip) this.tooltip.style.display = "none";
  }

  selectCourse(courseId) {
    this.selectedNodeId = courseId;
    if (!this.graphData) return;

    const targetCourse = this.graphData.courses.find(c => c.id === courseId);
    if (!targetCourse) return;
    const neighborSet = new Set(targetCourse.neighbors || []);

    // Update 3D appearance
    if (this.nodeMeshMap) {
      for (const [id, mesh] of this.nodeMeshMap.entries()) {
        const isSelected = (id === courseId);
        const isNeighbor = neighborSet.has(id);

        if (isSelected) {
          mesh.material.emissiveIntensity = 0.9;
          mesh.scale.set(1.5, 1.5, 1.5);
        } else if (isNeighbor) {
          mesh.material.emissiveIntensity = 0.6;
          mesh.scale.set(1.15, 1.15, 1.15);
        } else {
          mesh.material.emissiveIntensity = 0.05;
          mesh.scale.set(0.7, 0.7, 0.7);
        }
      }
    }

    if (this.is2D) this.draw2D();

    if (this.onNodeSelectedCallback) {
      this.onNodeSelectedCallback(courseId, targetCourse);
    }
  }

  clearSelection() {
    this.selectedNodeId = null;
    if (this.nodeMeshMap) {
      for (const mesh of this.nodeMeshMap.values()) {
        mesh.material.emissiveIntensity = 0.15;
        mesh.scale.set(1, 1, 1);
      }
    }
    if (this.is2D) this.draw2D();
    if (this.onNodeSelectedCallback) {
      this.onNodeSelectedCallback(null, null);
    }
  }

  highlightStep(courseId, slot) {
    if (!this.nodeMeshMap) return;
    const mesh = this.nodeMeshMap.get(courseId);
    if (!mesh) return;

    const newColor = getSlotColor(slot);
    mesh.material.color.set(newColor);
    mesh.material.emissive.set(newColor);
    mesh.material.emissiveIntensity = 0.9;
    mesh.scale.set(1.6, 1.6, 1.6);

    setTimeout(() => {
      mesh.scale.set(1, 1, 1);
      mesh.material.emissiveIntensity = 0.25;
    }, 400);
  }

  animate() {
    requestAnimationFrame(this.animate);
    if (!this.is2D && this.renderer && this.scene && this.camera) {
      if (this.controls) this.controls.update();

      // Gentle idle rotation if not hovered or dragging
      if (this.edgeLineGroup && !this.selectedNodeId) {
        this.scene.rotation.y += 0.0008;
      }

      this.renderer.render(this.scene, this.camera);
    }
  }

  // --- 2D Fallback Rendering ---
  draw2D() {
    if (!this.ctx2D || !this.graphData) return;
    const ctx = this.ctx2D;
    const width = this.canvas2D.width;
    const height = this.canvas2D.height;
    ctx.clearRect(0, 0, width, height);

    const cx = width / 2;
    const cy = height / 2;

    // Draw edges
    if (this.showEdges && this.graphData.edges) {
      ctx.strokeStyle = "rgba(148, 163, 184, 0.25)";
      ctx.lineWidth = 1;
      this.graphData.edges.forEach(edge => {
        const p1 = this.nodePositions[edge[0]];
        const p2 = this.nodePositions[edge[1]];
        if (p1 && p2) {
          ctx.beginPath();
          ctx.moveTo(cx + p1.x * 0.9, cy + p1.y * 0.9);
          ctx.lineTo(cx + p2.x * 0.9, cy + p2.y * 0.9);
          ctx.stroke();
        }
      });
    }

    // Draw nodes
    this.graphData.courses.forEach(c => {
      const p = this.nodePositions[c.id];
      if (!p) return;
      const x = cx + p.x * 0.9;
      const y = cy + p.y * 0.9;
      const slot = this.getSlotForCourse(c.id);
      const color = getSlotColor(slot);

      const isSelected = (c.id === this.selectedNodeId);
      const r = isSelected ? this.nodeBaseRadius * 1.5 : this.nodeBaseRadius;

      ctx.beginPath();
      ctx.arc(x, y, r, 0, Math.PI * 2);
      ctx.fillStyle = color;
      ctx.shadowColor = color;
      ctx.shadowBlur = isSelected ? 16 : 4;
      ctx.fill();
      ctx.shadowBlur = 0;

      ctx.strokeStyle = "#ffffff";
      ctx.lineWidth = isSelected ? 2.5 : 1;
      ctx.stroke();

      if (this.showLabels) {
        ctx.fillStyle = "#ffffff";
        ctx.font = "bold 10px sans-serif";
        ctx.textAlign = "center";
        ctx.fillText(c.code, x, y - r - 4);
      }
    });
  }

  handle2DHover(x, y, clientX, clientY) {
    const hitId = this.find2DNodeAt(x, y);
    if (hitId && this.graphData) {
      const course = this.graphData.courses.find(c => c.id === hitId);
      const slot = this.getSlotForCourse(hitId);
      this.showTooltip(clientX, clientY, course, slot);
      document.body.style.cursor = "pointer";
    } else {
      this.hideTooltip();
      document.body.style.cursor = "default";
    }
  }

  find2DNodeAt(x, y) {
    if (!this.graphData || !this.canvas2D) return null;
    const cx = this.canvas2D.width / 2;
    const cy = this.canvas2D.height / 2;

    for (const c of this.graphData.courses) {
      const p = this.nodePositions[c.id];
      if (!p) continue;
      const nx = cx + p.x * 0.9;
      const ny = cy + p.y * 0.9;
      const dx = x - nx;
      const dy = y - ny;
      if (dx * dx + dy * dy <= (this.nodeBaseRadius + 5) * (this.nodeBaseRadius + 5)) {
        return c.id;
      }
    }
    return null;
  }
}

window.GraphVisualizer = GraphVisualizer;
window.getSlotColor = getSlotColor;
