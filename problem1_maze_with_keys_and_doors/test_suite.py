"""
Comprehensive Test Suite for Maze with Keys and Doors.
Verifies all Problem 1 constraints:
- Key/door bitmask BFS
- Output 1 format (single integer or -1)
- Output 2 CSV file with 'x' marks preserving key/door identifiability
- Solvability for T = 1..6
- Unsolvable maze returning -1
- Interactive piped stdin simulation
"""

import subprocess
import os
import csv
from core_solver import load_from_csv, solve_bfs, generate_marked_csv, generate_solvable_maze

def test_t1_simple():
    print("Testing T=1 Simple Maze...")
    csv_path = "sample_mazes/maze_t1_simple.csv"
    maze = load_from_csv(csv_path, T=1)
    length, path, history = solve_bfs(maze)
    print(f"  Result length: {length}")
    assert length == 13, f"Expected 13, got {length}"
    out_csv = "test_out_t1.csv"
    marked = generate_marked_csv(maze, path, output_path=out_csv)
    assert os.path.exists(out_csv)
    # Check that x, x(a), x(A) exist
    found_xa = False
    found_xA = False
    for row in marked:
        for cell in row:
            if cell == "x(a)": found_xa = True
            if cell == "x(A)": found_xA = True
    assert found_xa, "Key 'a' must be marked as x(a) along the path"
    assert found_xA, "Door 'A' must be marked as x(A) along the path"
    print("  T=1 passed!")

def test_t2_multi_room():
    print("Testing T=2 Multi-Room Maze...")
    csv_path = "sample_mazes/maze_t2_multi_room.csv"
    maze = load_from_csv(csv_path, T=2)
    length, path, history = solve_bfs(maze)
    print(f"  Result length: {length}")
    assert length > 0, "Should be solvable"
    out_csv = "test_out_t2.csv"
    generate_marked_csv(maze, path, output_path=out_csv)
    assert os.path.exists(out_csv)
    print("  T=2 passed!")

def test_t3_complex():
    print("Testing T=3 Complex Maze...")
    csv_path = "sample_mazes/maze_t3_complex.csv"
    maze = load_from_csv(csv_path, T=3)
    length, path, history = solve_bfs(maze)
    print(f"  Result length: {length}")
    assert length > 0, "Should be solvable"
    out_csv = "test_out_t3.csv"
    generate_marked_csv(maze, path, output_path=out_csv)
    assert os.path.exists(out_csv)
    print("  T=3 passed!")

def test_unsolvable():
    print("Testing Unsolvable Maze (-1)...")
    csv_path = "sample_mazes/maze_unsolvable.csv"
    maze = load_from_csv(csv_path, T=1)
    length, path, history = solve_bfs(maze)
    print(f"  Result length: {length}")
    assert length == -1, f"Expected -1, got {length}"
    print("  Unsolvable passed!")

def test_random_generator_all_t():
    print("Testing Random Solvable Generator for T = 1..6...")
    for t in range(1, 7):
        maze = generate_solvable_maze(N=15 + 2 * t, T=t)
        length, path, history = solve_bfs(maze)
        print(f"  Generated T={t} ({maze.N}x{maze.M}): Solved with length={length}")
        assert length > 0, f"Generated maze for T={t} must be solvable!"
    print("  All T = 1..6 random mazes passed!")

def test_cli_execution():
    print("Testing CLI quiet mode...")
    cmd = ["python3", "maze_solver.py", "-T", "1", "--csv", "sample_mazes/maze_t1_simple.csv", "--quiet"]
    res = subprocess.run(cmd, capture_output=True, text=True, check=True)
    out = res.stdout.strip()
    print(f"  CLI quiet output: {out}")
    assert out == "13", f"Expected '13', got '{out}'"
    print("  CLI execution passed!")

def test_piped_interactive():
    print("Testing Piped Standard Input...")
    # Simulate entering: T=1, Option 1, sample_mazes/maze_t1_simple.csv, and Choice 2 (exit without web visualizer)
    stdin_data = "1\n1\nsample_mazes/maze_t1_simple.csv\n2\n"
    res = subprocess.run(["python3", "maze_solver.py"], input=stdin_data, text=True, capture_output=True)
    assert "OUTPUT 1: Shortest Path Length = 13" in res.stdout, "Expected Output 1 in stdout"
    assert "OUTPUT 2: CSV with shortest path marked" in res.stdout, "Expected Output 2 in stdout"
    print("  Piped interactive passed!")

if __name__ == "__main__":
    test_t1_simple()
    test_t2_multi_room()
    test_t3_complex()
    test_unsolvable()
    test_random_generator_all_t()
    test_cli_execution()
    test_piped_interactive()
    print("\n==========================================")
    print("ALL TESTS PASSED SUCCESSFULLY! (100% PASS)")
    print("==========================================")
