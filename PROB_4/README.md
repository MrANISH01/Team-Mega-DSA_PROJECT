# Problem 4: ExamCraft 3D - Conflict-Free Exam Scheduling System

## Overview
ExamCraft 3D is a graph-theoretic exam timetable scheduling and visualization platform. It models university course scheduling as a **Vertex Coloring Problem** where courses represent vertices and shared student enrollments represent conflicting edges.

## Key Features
- **Scheduling Algorithms**:
  - **Welsh-Powell**: Degree-ordered heuristic greedy coloring.
  - **DSatur (Degree of Saturation)**: Dynamically prioritizes vertices with the highest number of distinct colored neighbors.
  - **Exact Branch & Bound**: Finds the guaranteed minimum chromatic number (optimal slot count) with maximum clique lower-bound pruning.
- **Conflict Validation & Proof Engine**: Strict non-adjacent color verification with zero tolerance for timetable collisions.
- **Interactive 3D Web Visualizer**: WebGL/Three.js-powered 3D conflict graph visualizer with orbital camera, time slot grouping, and inspection.
- **Embedded HTTP REST Server**: Zero external dependency Java HTTP backend with interactive API endpoints for benchmarks, graph generation, and CSV/DOT export.

## Project Structure
- src/: Java source code (algorithms, models, REST API, validators, benchmarks, tests)
- web/: 3D WebGL visualizer application
- uild.bat: Windows compilation script
- 
un.bat: Launch script for local HTTP server & visualizer
- 	est.bat: Edge-case verification test runner
- output/: Generated timetable exports (CSV/DOT)

## How to Build & Run
1. **Compile**: run uild.bat
2. **Launch Visualizer**: run 
un.bat (navigate to http://localhost:8080/)
3. **Run Unit Tests**: run 	est.bat

<img width="926" height="503" alt="image" src="https://github.com/user-attachments/assets/6a16a641-6043-4f42-8448-8c390fd1d57d" />

