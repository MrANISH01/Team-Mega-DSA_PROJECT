"""
Core Maze Solver, CSV Loader/Exporter, and Random Solvable Maze Generator.
Implements Breadth-First Search (BFS) over state space (row, col, key_mask)
to find the shortest path from 'S' to 'E' with keys ('a'-'f') and doors ('A'-'F').
"""

import collections
import csv
import io
import os
import random

# Color palette for Key/Door pairs
PAIR_COLORS = [
    {"name": "Blue", "hex": "#1E88E5", "key_bg": "#BBDEFB", "door_bg": "#1565C0", "text": "#FFFFFF"},
    {"name": "Orange", "hex": "#FB8C00", "key_bg": "#FFE0B2", "door_bg": "#E65100", "text": "#FFFFFF"},
    {"name": "Purple", "hex": "#8E24AA", "key_bg": "#E1BEE7", "door_bg": "#6A1B9A", "text": "#FFFFFF"},
    {"name": "Gold", "hex": "#FDD835", "key_bg": "#FFF9C4", "door_bg": "#F57F17", "text": "#212121"},
    {"name": "Cyan", "hex": "#00ACC1", "key_bg": "#B2EBF2", "door_bg": "#00838F", "text": "#FFFFFF"},
    {"name": "Pink", "hex": "#E91E63", "key_bg": "#F8BBD0", "door_bg": "#AD1457", "text": "#FFFFFF"}
]

def is_key(ch):
    return isinstance(ch, str) and len(ch) == 1 and 'a' <= ch <= 'f'

def is_door(ch):
    return isinstance(ch, str) and len(ch) == 1 and 'A' <= ch <= 'F'

def key_index(ch):
    return ord(ch) - ord('a')

def door_index(ch):
    return ord(ch) - ord('A')

class Maze:
    def __init__(self, grid, T=None, exit_pos=None):
        self.grid = [list(row) for row in grid]
        self.N = len(self.grid)
        self.M = len(self.grid[0]) if self.N > 0 else 0
        self.T = T
        self.start = None
        self.exit = exit_pos
        self.keys = collections.defaultdict(list)
        self.doors = collections.defaultdict(list)
        
        e_positions = []
        for r in range(self.N):
            for c in range(self.M):
                cell = self.grid[r][c]
                if cell == 'S':
                    self.start = (r, c)
                elif cell == 'E':
                    e_positions.append((r, c))
                elif is_key(cell):
                    self.keys[cell].append((r, c))
                elif is_door(cell):
                    self.doors[cell].append((r, c))

        if self.exit is None:
            if len(e_positions) == 1:
                self.exit = e_positions[0]
            elif len(e_positions) > 1:
                # Disambiguate if multiple 'E's: pick the one furthest from S or designated
                if self.start:
                    e_positions.sort(key=lambda p: abs(p[0] - self.start[0]) + abs(p[1] - self.start[1]), reverse=True)
                self.exit = e_positions[0]

        if self.T is None:
            max_k = -1
            for k in self.keys:
                max_k = max(max_k, key_index(k))
            for d in self.doors:
                if d != 'E' or d in self.doors:
                    max_k = max(max_k, door_index(d))
            self.T = max(1, min(6, max_k + 1))

    def clone(self):
        return Maze([list(row) for row in self.grid], T=self.T, exit_pos=self.exit)


def solve_bfs(maze):
    """
    Breadth-First Search on state space (row, col, key_mask).
    Returns:
        length: int, shortest path length (number of steps) or -1 if unreachable.
        path: list of (r, c) coordinates from S to E.
        history: list of step dictionaries describing state, keys held, and pickup/unlock events.
    """
    if not maze.start or not maze.exit:
        return -1, [], []

    sr, sc = maze.start
    er, ec = maze.exit

    # Initial key mask (in case start is on a key)
    init_mask = 0
    start_cell = maze.grid[sr][sc]
    if is_key(start_cell) and key_index(start_cell) < maze.T:
        init_mask |= (1 << key_index(start_cell))

    # Queue stores: (row, col, mask)
    queue = collections.deque([(sr, sc, init_mask)])
    visited = {(sr, sc, init_mask): None}  # state -> parent_state

    found_state = None

    while queue:
        r, c, mask = queue.popleft()

        if (r, c) == (er, ec):
            found_state = (r, c, mask)
            break

        for dr, dc in [(-1, 0), (1, 0), (0, -1), (0, 1)]:
            nr, nc = r + dr, c + dc
            if 0 <= nr < maze.N and 0 <= nc < maze.M:
                cell = maze.grid[nr][nc]
                if cell == '#':
                    continue

                nmask = mask
                # Check door
                if is_door(cell):
                    # Check if this cell is designated as exit E
                    if (nr, nc) == (er, ec) and cell == 'E' and 'e' not in maze.keys:
                        nmask = mask
                    else:
                        d_idx = door_index(cell)
                        if d_idx < maze.T:
                            if not (mask & (1 << d_idx)):
                                # Locked: do not have corresponding key
                                continue
                        nmask = mask
                elif is_key(cell):
                    k_idx = key_index(cell)
                    if k_idx < maze.T:
                        nmask = mask | (1 << k_idx)

                next_state = (nr, nc, nmask)
                if next_state not in visited:
                    visited[next_state] = (r, c, mask)
                    queue.append(next_state)

    if found_state is None:
        return -1, [], []

    # Reconstruct path backwards
    curr = found_state
    state_path = []
    while curr is not None:
        state_path.append(curr)
        curr = visited[curr]
    state_path.reverse()

    coord_path = [(s[0], s[1]) for s in state_path]
    length = len(coord_path) - 1

    # Build detailed history of events at each step
    history = []
    collected_keys = set()
    opened_doors = set()

    for idx, (r, c, mask) in enumerate(state_path):
        cell = maze.grid[r][c]
        picked_key = None
        opened_door = None

        if is_key(cell) and cell not in collected_keys and key_index(cell) < maze.T:
            picked_key = cell
            collected_keys.add(cell)

        if is_door(cell) and (r, c) != (er, ec):
            if cell not in opened_doors and door_index(cell) < maze.T:
                opened_door = cell
                opened_doors.add(cell)

        history.append({
            'step': idx,
            'pos': (r, c),
            'mask': mask,
            'keys_held': sorted(list(collected_keys)),
            'picked_key': picked_key,
            'opened_door': opened_door,
            'cell_type': cell
        })

    return length, coord_path, history


def load_from_csv(filepath_or_content, T=None):
    """
    Parses an NxN grid from a CSV file path or string content.
    Supports comma, semicolon, whitespace, or single-string row representations.
    """
    if os.path.exists(str(filepath_or_content)):
        with open(filepath_or_content, 'r', encoding='utf-8') as f:
            raw_text = f.read()
    else:
        raw_text = str(filepath_or_content)

    reader = csv.reader(io.StringIO(raw_text.strip()))
    grid = []
    for row in reader:
        if not row:
            continue
        # If single column with continuous characters, split them
        if len(row) == 1 and len(row[0].strip()) > 1:
            line = row[0].strip()
            if ' ' in line:
                grid.append(line.split())
            elif '\t' in line:
                grid.append(line.split('\t'))
            elif ';' in line:
                grid.append(line.split(';'))
            else:
                grid.append(list(line))
        else:
            cleaned = [col.strip() for col in row if col.strip() != '']
            if cleaned:
                grid.append(cleaned)

    if not grid:
        raise ValueError("CSV grid is empty.")

    # Normalize rows to same length if needed
    max_len = max(len(r) for r in grid)
    for r in grid:
        while len(r) < max_len:
            r.append('.')

    return Maze(grid, T=T)


def generate_marked_csv(maze, path, output_path=None, mark_mode="detailed"):
    """
    Creates Output 2: A CSV file with the shortest path marked with 'x's.
    Keys picked up and doors opened along the path remain clearly identifiable.
    
    mark_mode options:
      - 'detailed': '.' -> 'x', key 'a' -> 'x(a)', door 'A' -> 'x(A)', 'S' -> 'S', 'E' -> 'E'
      - 'suffix': '.' -> 'x', key 'a' -> 'a*', door 'A' -> 'A*'
      - 'plain': '.' -> 'x', keys and doors remain 'a', 'A' (identifiable)
    """
    marked_grid = [list(row) for row in maze.grid]
    path_set = set(path)

    for r in range(maze.N):
        for c in range(maze.M):
            if (r, c) in path_set:
                cell = maze.grid[r][c]
                if cell == '.':
                    marked_grid[r][c] = 'x'
                elif is_key(cell):
                    if mark_mode == "detailed":
                        marked_grid[r][c] = f"x({cell})"
                    elif mark_mode == "suffix":
                        marked_grid[r][c] = f"{cell}*"
                    else:
                        marked_grid[r][c] = cell
                elif is_door(cell) and (r, c) != maze.exit:
                    if mark_mode == "detailed":
                        marked_grid[r][c] = f"x({cell})"
                    elif mark_mode == "suffix":
                        marked_grid[r][c] = f"{cell}*"
                    else:
                        marked_grid[r][c] = cell
                elif cell == 'S':
                    marked_grid[r][c] = 'S'
                elif (r, c) == maze.exit:
                    marked_grid[r][c] = 'E'

    if output_path:
        os.makedirs(os.path.dirname(os.path.abspath(output_path)), exist_ok=True)
        with open(output_path, 'w', newline='', encoding='utf-8') as f:
            writer = csv.writer(f)
            writer.writerows(marked_grid)

    return marked_grid


def export_original_csv(maze, output_path):
    """Exports the original maze grid to CSV."""
    os.makedirs(os.path.dirname(os.path.abspath(output_path)), exist_ok=True)
    with open(output_path, 'w', newline='', encoding='utf-8') as f:
        writer = csv.writer(f)
        writer.writerows(maze.grid)


def generate_solvable_maze(N=None, T=3, max_retries=20):
    """
    Generates a guaranteed solvable NxN grid containing T keys and T doors.
    Uses regional partitioning and chained dependencies to guarantee that:
      1. Key i is placed in region i.
      2. Door i gates the transition from region i to region i+1.
      3. At least one valid path from S to E exists.
    """
    assert 1 <= T <= 6, "T must be between 1 and 6"

    num_regions = T + 1
    # Minimum rows needed: num_regions * 2 corridor rows + (num_regions - 1) dividing walls + 2 borders
    min_N = num_regions * 2 + (num_regions - 1) + 2
    if N is None or N < min_N:
        N = min_N
    if N % 2 == 0:
        N += 1

    for attempt in range(max_retries):
        grid = [['#' for _ in range(N)] for _ in range(N)]
        
        # Partition grid into num_regions bands
        avail_rows = N - 2 - (num_regions - 1)
        base_h = avail_rows // num_regions
        rem = avail_rows % num_regions

        regions = []
        curr_r = 1
        for i in range(num_regions):
            h = base_h + (1 if i < rem else 0)
            r_start = curr_r
            r_end = curr_r + h - 1
            regions.append((r_start, r_end))
            curr_r = r_end + 2

        open_cells_by_region = []
        for reg_idx, (r_start, r_end) in enumerate(regions):
            for r in range(r_start, r_end + 1):
                for c in range(1, N - 1):
                    # Random wall density ~35%
                    if random.random() < 0.65 or r == r_start or c == 1 or c == N - 2:
                        grid[r][c] = '.'
                    else:
                        grid[r][c] = '#'
            
            # Connect region with horizontal spine and vertical ribs
            spine_r = (r_start + r_end) // 2
            for c in range(1, N - 1):
                grid[spine_r][c] = '.'
                
            for c in range(1, N - 1, 2):
                for r in range(r_start, r_end + 1):
                    grid[r][c] = '.'

            actual_open = [(r, c) for r in range(r_start, r_end + 1) for c in range(1, N - 1) if grid[r][c] == '.']
            open_cells_by_region.append(actual_open)

        # Place Start 'S' in region 0
        if not open_cells_by_region[0]:
            continue
        s_cell = random.choice(open_cells_by_region[0])
        grid[s_cell[0]][s_cell[1]] = 'S'
        open_cells_by_region[0].remove(s_cell)

        # Place Exit 'E' in final region
        if not open_cells_by_region[-1]:
            continue
        e_cell = random.choice(open_cells_by_region[-1])
        grid[e_cell[0]][e_cell[1]] = 'E'
        open_cells_by_region[-1].remove(e_cell)

        key_chars = [chr(ord('a') + i) for i in range(T)]
        door_chars = [chr(ord('A') + i) for i in range(T)]

        valid_placement = True
        for i in range(T):
            if not open_cells_by_region[i]:
                valid_placement = False
                break

            k_char = key_chars[i]
            k_cell = random.choice(open_cells_by_region[i])
            grid[k_cell[0]][k_cell[1]] = k_char
            open_cells_by_region[i].remove(k_cell)

            div_row = regions[i][1] + 1
            # Find column with open cell above and below
            valid_cols = [c for c in range(1, N - 1) if grid[div_row - 1][c] == '.' and grid[div_row + 1][c] == '.']
            if not valid_cols:
                door_c = random.randint(2, N - 3)
                grid[div_row - 1][door_c] = '.'
                grid[div_row + 1][door_c] = '.'
            else:
                door_c = random.choice(valid_cols)

            grid[div_row][door_c] = door_chars[i]

        if not valid_placement:
            continue

        maze = Maze(grid, T=T, exit_pos=e_cell)
        length, path, history = solve_bfs(maze)
        if length > 0:
            return maze

    raise RuntimeError(f"Failed to generate solvable maze after {max_retries} attempts.")
