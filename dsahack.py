#!/usr/bin/env python3
"""
Budget Traveller with Coupons
-----------------------------
Travel from city 1 to city N with one-way flights, using at most K intermediate
stops (i.e. at most K+1 flights) and at most C coupons. A coupon halves one
flight's price (rounded down); at most one coupon per flight.

Algorithm: Dijkstra over the layered state (city, flights_taken, coupons_used).
From state (u, e, c) along a flight u->v with price p:
    (v, e+1, c)    cost + p
    (v, e+1, c+1)  cost + p // 2     (if c < C)
with e+1 <= K+1. Prices are non-negative, so the first time city N is popped
its cost is optimal. States: N*(K+2)*(C+1); time O(M*(K+1)*(C+1)*log).

Input options
  1) Text:  "N M" / "K C" / M lines "u v p"   (typed, or from a file)
  2) "K C" + a CSV file of flights (columns u,v,price; header optional)
  3) Generate a random valid graph for a user-specified K and C

Outputs
  1) Minimum cost (or -1)
  2) CSV of the graph: on_path = 'x' for chosen flights, coupon = 'x' where a
     coupon was used, plus the price actually paid
  Plus a PNG of the solved graph and an animated GIF of the journey.

Usage
  python3 dsahack.py                          # interactive menu
  python3 dsahack.py text input.txt
  python3 dsahack.py csv flights.csv --k 2 --c 1
  python3 dsahack.py generate --k 3 --c 2 [--n 9] [--seed 7]
  add --no-show to skip the pop-up window, --out NAME to change output prefix
"""

import argparse
import csv
import heapq
import math
import random
import sys

# ----------------------------------------------------------------------------
# Core solver
# ----------------------------------------------------------------------------


def solve(n, flights, K, C):
    """Return (min_cost, legs) or (-1, []).

    flights: list of (u, v, price). legs: list of dicts describing each flight
    on the optimal route: {idx, u, v, price, coupon, paid}.
    """
    if n == 1:
        return 0, []
    max_edges = K + 1
    adj = [[] for _ in range(n + 1)]
    for i, (u, v, p) in enumerate(flights):
        adj[u].append((v, p, i))

    INF = float("inf")
    # dist[(node, edges, coupons)]
    dist = {(1, 0, 0): 0}
    parent = {}  # state -> (prev_state, flight_idx, coupon_used)
    pq = [(0, 1, 0, 0)]
    while pq:
        d, u, e, c = heapq.heappop(pq)
        if d > dist.get((u, e, c), INF):
            continue
        if u == n:
            legs = []
            state = (u, e, c)
            while state in parent:
                prev, idx, used = parent[state]
                fu, fv, fp = flights[idx]
                legs.append({"idx": idx, "u": fu, "v": fv, "price": fp,
                             "coupon": used, "paid": fp // 2 if used else fp})
                state = prev
            legs.reverse()
            return d, legs
        if e == max_edges:
            continue
        for v, p, idx in adj[u]:
            options = [(p, c, False)]
            if c < C:
                options.append((p // 2, c + 1, True))
            for cost, nc, used in options:
                ns = (v, e + 1, nc)
                nd = d + cost
                if nd < dist.get(ns, INF):
                    dist[ns] = nd
                    parent[ns] = ((u, e, c), idx, used)
                    heapq.heappush(pq, (nd, v, e + 1, nc))
    return -1, []


# ----------------------------------------------------------------------------
# Input
# ----------------------------------------------------------------------------


def parse_text(text):
    """Option 1 format: 'N M' / 'K C' / M lines 'u v p'."""
    tok = text.split()
    if len(tok) < 4:
        raise ValueError("expected 'N M' then 'K C' then M flights")
    n, m, K, C = (int(t) for t in tok[:4])
    rest = tok[4:]
    if len(rest) < 3 * m:
        raise ValueError(f"expected {m} flights, found {len(rest) // 3}")
    flights = []
    for i in range(m):
        u, v, p = (int(x) for x in rest[3 * i: 3 * i + 3])
        flights.append((u, v, p))
    validate(n, flights, K, C)
    return n, flights, K, C


def read_flights_csv(path):
    """Read flights from CSV with columns u,v,price (header row optional).

    Extra columns (e.g. on_path/coupon from a previous output) are ignored,
    so an output CSV can be fed back in. N = highest city number seen, unless
    a row like 'N,<n>' is present.
    """
    flights, n = [], 0
    with open(path, newline="") as f:
        for row in csv.reader(f):
            row = [x.strip() for x in row]
            if not row or not row[0] or row[0].startswith("#"):
                continue
            if row[0].upper() == "N" and len(row) > 1:
                n = max(n, int(row[1]))
                continue
            try:
                u, v, p = int(row[0]), int(row[1]), int(float(row[2]))
            except (ValueError, IndexError):
                continue  # header or malformed line
            flights.append((u, v, p))
            n = max(n, u, v)
    if not flights:
        raise ValueError(f"no flights found in {path}")
    return n, flights


def validate(n, flights, K, C):
    if n < 1:
        raise ValueError("N must be >= 1")
    if K < 0 or C < 0:
        raise ValueError("K and C must be non-negative")
    for u, v, p in flights:
        if not (1 <= u <= n and 1 <= v <= n):
            raise ValueError(f"flight {u}->{v} uses a city outside 1..{n}")
        if p < 0:
            raise ValueError(f"flight {u}->{v} has negative price")


def generate_graph(K, C, n=None, seed=None):
    """Random graph guaranteed to have a 1->N route with at most K stops.

    Also adds decoys: a cheap route with too many stops (when possible) and
    an expensive direct-ish flight, so K and C actually matter.
    """
    rng = random.Random(seed)
    if n is None:
        n = rng.randint(max(4, K + 2), max(8, K + 4))
    n = max(n, 2)
    edges = {}

    def add(u, v, p):
        if u != v and (u, v) not in edges:
            edges[(u, v)] = p

    middle = list(range(2, n))
    # Guaranteed feasible route with s stops, s <= K
    s = rng.randint(min(1, K, len(middle)), min(K, len(middle)))
    stops = rng.sample(middle, s)
    route = [1] + stops + [n]
    for a, b in zip(route, route[1:]):
        add(a, b, rng.randint(80, 400))

    # Decoy: a cheap route that needs more than K stops (if enough cities)
    if len(middle) >= K + 1:
        long_route = [1] + rng.sample(middle, K + 1) + [n]
        for a, b in zip(long_route, long_route[1:]):
            add(a, b, rng.randint(10, 60))

    # Expensive direct flight (often the coupon target)
    add(1, n, rng.randint(500, 1200))

    # Random extra flights
    extra = rng.randint(n, 2 * n)
    for _ in range(extra * 3):
        if len(edges) >= len(route) - 1 + extra + K + 3:
            break
        u, v = rng.sample(range(1, n + 1), 2)
        if v == 1 or u == n:
            continue
        add(u, v, rng.randint(40, 500))

    flights = [(u, v, p) for (u, v), p in edges.items()]
    rng.shuffle(flights)
    return n, flights


def save_input_csv(path, n, flights):
    with open(path, "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["u", "v", "price"])
        for u, v, p in flights:
            w.writerow([u, v, p])


# ----------------------------------------------------------------------------
# Output
# ----------------------------------------------------------------------------


def write_result_csv(path, n, flights, legs, cost, K, C):
    """Output 2: every flight, with 'x' on the chosen path / coupon flights."""
    on_path = {leg["idx"]: leg for leg in legs}
    order = {leg["idx"]: i + 1 for i, leg in enumerate(legs)}
    with open(path, "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["u", "v", "price", "on_path", "coupon", "paid", "leg"])
        for i, (u, v, p) in enumerate(flights):
            leg = on_path.get(i)
            w.writerow([u, v, p,
                        "x" if leg else "",
                        "x" if leg and leg["coupon"] else "",
                        leg["paid"] if leg else "",
                        order.get(i, "")])
        w.writerow([])
        w.writerow(["# N", n, "K", K, "C", C])
        w.writerow(["# min_cost", cost])
        if legs:
            route = [legs[0]["u"]] + [leg["v"] for leg in legs]
            w.writerow(["# route", " -> ".join(map(str, route))])


def describe(cost, legs, K, C):
    lines = [f"Minimum cost: {cost}"]
    if cost == -1:
        lines.append(f"No route from 1 to N with at most {K} stops.")
        return "\n".join(lines)
    if not legs:
        lines.append("Start is the destination.")
        return "\n".join(lines)
    route = [legs[0]["u"]] + [leg["v"] for leg in legs]
    used = sum(leg["coupon"] for leg in legs)
    lines.append("Route: " + " -> ".join(map(str, route)))
    lines.append(f"Stops used: {len(legs) - 1}/{K}   Coupons used: {used}/{C}")
    for leg in legs:
        tag = (f"{leg['price']} -> {leg['paid']} (coupon)"
               if leg["coupon"] else f"{leg['price']}")
        lines.append(f"  {leg['u']} -> {leg['v']}: {tag}")
    return "\n".join(lines)


# ----------------------------------------------------------------------------
# Visualisation  ("night flight" theme)
# ----------------------------------------------------------------------------

BG, PANEL, GRID = "#0b1020", "#121a33", "#1a2444"
INK, MUTED, DIM = "#e8ecf8", "#8a97bd", "#4a5784"
C_START, C_DEST, C_STOP = "#2ee88a", "#ff4d6d", "#ff9f1c"
C_NODE, C_NODE_EDGE = "#1e2a4a", "#41527f"
C_EDGE, C_PATH, C_GOLD = "#33416a", "#38bdf8", "#ffc93c"

LABEL_BOX = dict(boxstyle="round,pad=0.25", fc=BG, ec="none", alpha=0.9)


def layout(n, flights):
    """Positions with city 1 pinned left and city N pinned right."""
    try:
        import networkx as nx
        g = nx.DiGraph()
        g.add_nodes_from(range(1, n + 1))
        g.add_edges_from((u, v) for u, v, _ in flights)
        init = {i: (math.cos(2 * math.pi * i / n), math.sin(2 * math.pi * i / n))
                for i in range(1, n + 1)}
        init[1], init[n] = (-1.6, 0.0), (1.6, 0.0)
        pos = nx.spring_layout(g.to_undirected(), pos=init, fixed=[1, n] if n > 1 else [1],
                               k=2.2 / math.sqrt(n), iterations=300, seed=3)
        return {k: (float(x), float(y)) for k, (x, y) in pos.items()}
    except ImportError:
        pos = {}
        for i in range(1, n + 1):
            a = math.pi - 2 * math.pi * (i - 1) / n
            pos[i] = (math.cos(a), math.sin(a))
        return pos


def edge_curves(flights):
    """arc3 'rad' per flight so reverse/parallel flights don't overlap."""
    seen, rads = {}, []
    for u, v, _ in flights:
        k = seen.get((u, v), 0)
        seen[(u, v)] = k + 1
        rads.append(0.12 + 0.14 * k)
    return rads


def bezier(p0, p2, rad, t):
    """Point on matplotlib's arc3 quadratic Bezier at parameter t."""
    (x1, y1), (x2, y2) = p0, p2
    cx = (x1 + x2) / 2 + rad * (y2 - y1)
    cy = (y1 + y2) / 2 - rad * (x2 - x1)
    a, b, c = (1 - t) ** 2, 2 * (1 - t) * t, t * t
    return a * x1 + b * cx + c * x2, a * y1 + b * cy + c * y2


def bezier_angle(p0, p2, rad, t):
    """Heading (degrees) of the arc3 curve at parameter t."""
    (x1, y1), (x2, y2) = p0, p2
    cx = (x1 + x2) / 2 + rad * (y2 - y1)
    cy = (y1 + y2) / 2 - rad * (x2 - x1)
    dx = 2 * (1 - t) * (cx - x1) + 2 * t * (x2 - cx)
    dy = 2 * (1 - t) * (cy - y1) + 2 * t * (y2 - cy)
    return math.degrees(math.atan2(dy, dx))


def label_point(pos, u, v, rad, node_r):
    """Point on the flight's curve for its price label, avoiding other cities."""
    for t in (0.5, 0.42, 0.58, 0.34, 0.66, 0.27, 0.73):
        x, y = bezier(pos[u], pos[v], rad, t)
        if all(math.hypot(x - px, y - py) > 2.2 * node_r for px, py in pos.values()):
            return x, y
    return bezier(pos[u], pos[v], rad, 0.5)


def ease(t):
    return t * t * (3 - 2 * t)


def visualize(n, flights, cost, legs, K, C, out_prefix, show=True, fps=25):
    import matplotlib
    if not show:
        matplotlib.use("Agg")
    import matplotlib.pyplot as plt
    import matplotlib.patheffects as pe
    from matplotlib.animation import FuncAnimation, PillowWriter
    from matplotlib.patches import Circle, FancyArrowPatch, FancyBboxPatch

    def glow(color, width):
        return [pe.Stroke(linewidth=width, foreground=color, alpha=0.25), pe.Normal()]

    pos = layout(n, flights)
    rads = edge_curves(flights)
    L = len(legs)
    stops_order = {leg["v"]: i + 1 for i, leg in enumerate(legs[:-1])}
    total_saved = sum(leg["price"] - leg["paid"] for leg in legs)

    fig = plt.figure(figsize=(14, 8), facecolor=BG)
    ax = fig.add_axes([0.01, 0.03, 0.66, 0.83], facecolor=BG)
    hud = fig.add_axes([0.685, 0.05, 0.3, 0.81], facecolor=BG)
    for a in (ax, hud):
        a.set_xticks([])
        a.set_yticks([])
        for s in a.spines.values():
            s.set_visible(False)
    hud.set_xlim(0, 1)
    hud.set_ylim(0, 1)

    # ---- header ----
    fig.text(0.02, 0.945, "BUDGET TRAVELLER", color=INK, fontsize=22,
             fontweight="bold", va="center")
    fig.text(0.02, 0.9, f"City 1 → City {n}    ·    at most {K} stops    ·    "
             f"{C} coupon{'s' if C != 1 else ''} (½ price, one per flight)",
             color=MUTED, fontsize=11.5, va="center")
    answer = "NO ROUTE  (−1)" if cost == -1 else f"MIN COST  {cost}"
    fig.text(0.985, 0.94, answer, color=C_DEST if cost == -1 else C_PATH,
             fontsize=20, fontweight="bold", ha="right", va="center")

    # ---- graph area ----
    xs = [p[0] for p in pos.values()]
    ys = [p[1] for p in pos.values()]
    pad = 0.4
    ax.set_xlim(min(xs) - pad, max(xs) + pad)
    ax.set_ylim(min(ys) - pad, max(ys) + pad)
    ax.set_aspect("equal")
    rng = random.Random(11)
    x0, x1 = ax.get_xlim()
    y0, y1 = ax.get_ylim()
    ax.scatter([rng.uniform(x0, x1) for _ in range(160)],
               [rng.uniform(y0, y1) for _ in range(160)],
               s=[rng.uniform(0.5, 4) for _ in range(160)], color="white",
               alpha=0.25, lw=0, zorder=0)
    node_r = 0.09 * max(1.0, (x1 - x0) / 4.2)
    shrink = 19

    on_path = {leg["idx"]: j for j, leg in enumerate(legs)}
    arrows, glows, labels = {}, {}, {}
    for i, (u, v, p) in enumerate(flights):
        style = f"arc3,rad={rads[i]}"
        if i in on_path:
            g = FancyArrowPatch(pos[u], pos[v], connectionstyle=style, arrowstyle="-",
                                lw=10, color=C_PATH, alpha=0, shrinkA=shrink,
                                shrinkB=shrink, zorder=1)
            ax.add_patch(g)
            glows[i] = g
        a = FancyArrowPatch(pos[u], pos[v], connectionstyle=style, arrowstyle="-|>",
                            mutation_scale=13, lw=1.3, color=C_EDGE,
                            shrinkA=shrink, shrinkB=shrink, zorder=2)
        ax.add_patch(a)
        arrows[i] = a
        mx, my = label_point(pos, u, v, rads[i], node_r)
        labels[i] = ax.text(mx, my, str(p), fontsize=9, ha="center", va="center",
                            color=MUTED, zorder=6, bbox=dict(LABEL_BOX))

    role_color = {}
    for c, (x, y) in pos.items():
        if c == 1:
            col, tag = C_START, "START"
        elif c == n:
            col, tag = C_DEST, "DESTINATION"
        elif c in stops_order:
            col, tag = C_STOP, f"STOP {stops_order[c]}"
        else:
            col, tag = None, None
        role_color[c] = col
        if col:
            for r, al in ((2.1, 0.07), (1.6, 0.14), (1.25, 0.25)):
                ax.add_patch(Circle((x, y), node_r * r, color=col, alpha=al, lw=0, zorder=3))
            ax.add_patch(Circle((x, y), node_r, fc=col, ec="white", lw=1.8, zorder=4))
            ax.text(x, y - node_r * 2.1, tag, color=col, fontsize=8.5,
                    fontweight="bold", ha="center", va="top", zorder=6)
        else:
            ax.add_patch(Circle((x, y), node_r, fc=C_NODE, ec=C_NODE_EDGE, lw=1.5, zorder=4))
        ax.text(x, y, str(c), ha="center", va="center", fontsize=12,
                fontweight="bold", zorder=5, color=BG if col else INK)

    # animated actors
    trail, = ax.plot([], [], lw=3.2, color=C_PATH, solid_capstyle="round", zorder=7,
                     path_effects=glow(C_PATH, 9))
    plane = ax.text(0, 0, "✈", fontsize=30, color="white", ha="center",
                    va="center", zorder=9, rotation_mode="anchor",
                    path_effects=[pe.Stroke(linewidth=5, foreground=C_PATH, alpha=0.5),
                                  pe.Normal()])
    pulse = Circle((0, 0), node_r, fill=False, lw=2.5, ec="white", alpha=0, zorder=8)
    ax.add_patch(pulse)
    popup = ax.text(0, 0, "", fontsize=15, fontweight="bold", color=C_GOLD,
                    ha="center", va="bottom", zorder=10, alpha=0,
                    path_effects=[pe.Stroke(linewidth=4, foreground=BG), pe.Normal()])
    banner = ax.text(0.5, 0.06, "", transform=ax.transAxes, ha="center", va="bottom",
                     fontsize=16, fontweight="bold", color=BG, zorder=11, alpha=0,
                     bbox=dict(boxstyle="round,pad=0.6", fc=C_START, ec="none"))

    # ---- HUD panel ----
    hud.add_patch(FancyBboxPatch((0.0, 0.0), 1.0, 1.0, boxstyle="round,pad=0,rounding_size=0.04",
                                 fc=PANEL, ec=GRID, lw=1.5, transform=hud.transAxes))

    def hud_label(y, s):
        hud.text(0.07, y, s, color=MUTED, fontsize=9.5, fontweight="bold", va="center")

    hud_label(0.94, "RUNNING COST")
    cost_txt = hud.text(0.07, 0.855, "0", color=INK, fontsize=38, fontweight="bold",
                        va="center")
    saved_txt = hud.text(0.93, 0.855, "", color=C_GOLD, fontsize=11, ha="right",
                         va="center", fontweight="bold")

    hud_label(0.76, f"STOPS USED   (max {K})")
    stop_pips = []
    if 0 < K <= 12:
        step = min(0.072, 0.86 / K)
        for s in range(K):
            stop_pips.append(hud.scatter([0.1 + s * step], [0.705], s=230,
                                         color=GRID, edgecolors=DIM, linewidths=1.2))
    stops_txt = hud.text(0.93, 0.76, f"0 / {K}", color=INK, fontsize=12,
                         ha="right", va="center", fontweight="bold")

    hud_label(0.63, f"COUPONS   ({C} × ½ price)")
    tickets = []
    if 0 < C <= 8:
        tw = min(0.17, 0.82 / C - 0.02)
        for s in range(C):
            bx = 0.07 + s * (tw + 0.02)
            box = FancyBboxPatch((bx, 0.55), tw, 0.045,
                                 boxstyle="round,pad=0,rounding_size=0.01",
                                 fc=PANEL, ec=C_GOLD, lw=1.5, linestyle="--")
            hud.add_patch(box)
            t = hud.text(bx + tw / 2, 0.5725, "½", color=C_GOLD, fontsize=11,
                         ha="center", va="center", fontweight="bold")
            tickets.append((box, t))
    coupons_txt = hud.text(0.93, 0.63, f"0 / {C}", color=INK, fontsize=12,
                           ha="right", va="center", fontweight="bold")

    hud_label(0.47, "ITINERARY")
    rows = []
    max_rows = 9
    row_h = min(0.05, 0.42 / max(1, min(L, max_rows)))
    if cost == -1:
        hud.text(0.5, 0.3, f"No way to reach city {n}\nwith ≤ {K} stops",
                 color=C_DEST, fontsize=13, ha="center", va="center", fontweight="bold")
    for j, leg in enumerate(legs[:max_rows]):
        y = 0.425 - j * row_h
        marker = hud.text(0.06, y, "", color=C_PATH, fontsize=10, va="center")
        route = hud.text(0.11, y, f"{leg['u']} → {leg['v']}", color=DIM, fontsize=11,
                         va="center", fontweight="bold")
        price = (f"{leg['price']} → {leg['paid']}" if leg["coupon"] else f"{leg['price']}")
        ptxt = hud.text(0.93, y, price, color=DIM, fontsize=11, ha="right", va="center",
                        fontweight="bold")
        tag = hud.text(0.5, y, "COUPON" if leg["coupon"] else "", color=DIM,
                       fontsize=8, va="center", fontweight="bold")
        rows.append((marker, route, ptxt, tag))
    if L > max_rows:
        hud.text(0.5, 0.425 - max_rows * row_h, f"… +{L - max_rows} more flights",
                 color=MUTED, fontsize=9, ha="center", va="center")

    legend = [(0.07, C_START, "start"), (0.24, C_DEST, "destination"),
              (0.48, C_STOP, "stop"), (0.63, C_PATH, "flight"), (0.8, C_GOLD, "coupon")]
    for lx, col, name in legend:
        hud.scatter([lx], [0.04], s=60, color=col)
        hud.text(lx + 0.025, 0.04, name, color=MUTED, fontsize=8, va="center")

    # ---- timeline ----
    INTRO, DEPART, FLY, ARRIVE, END = 15, 12, 34, 10, 60
    timeline = [("intro", -1, k) for k in range(INTRO)]
    for j in range(L):
        timeline += [("depart", j, k) for k in range(DEPART)]
        timeline += [("fly", j, k) for k in range(FLY)]
        timeline += [("arrive", j, k) for k in range(ARRIVE)]
    timeline += [("end", L - 1, k) for k in range(END)]

    def leg_color(leg):
        return C_GOLD if leg["coupon"] else C_PATH

    def paint_leg(j, mode):
        """mode: 'idle' | 'active' | 'done'."""
        leg = legs[j]
        i = leg["idx"]
        col = leg_color(leg)
        a, g, lab = arrows[i], glows[i], labels[i]
        if mode == "idle":
            a.set_color(C_EDGE)
            a.set_linewidth(1.3)
            g.set_alpha(0)
            lab.set_text(str(leg["price"]))
            lab.set_color(MUTED)
            lab.set_fontweight("normal")
            lab.get_bbox_patch().set_edgecolor("none")
            return
        a.set_color(col)
        a.set_linewidth(3.2 if mode == "done" else 2.4)
        g.set_color(col)
        g.set_alpha(0.22 if mode == "done" else 0.12)
        lab.set_color(col)
        lab.set_fontweight("bold")
        lab.get_bbox_patch().set_edgecolor(col)
        lab.set_text(f"{leg['price']} → {leg['paid']}" if leg["coupon"] else str(leg["price"]))

    def set_hud(spent, done_stops, coupons, current, flown):
        cost_txt.set_text(f"{int(round(spent))}")
        saved = sum(legs[j]["price"] - legs[j]["paid"] for j in range(L)
                    if legs[j]["coupon"] and (j < flown or j == current))
        saved_txt.set_text(f"saved {saved}" if saved else "")
        for s, pip in enumerate(stop_pips):
            used = s < done_stops
            pip.set_facecolor(C_STOP if used else GRID)
            pip.set_edgecolors(C_STOP if used else DIM)
        stops_txt.set_text(f"{done_stops} / {K}")
        for s, (box, t) in enumerate(tickets):
            used = s < coupons
            box.set_facecolor(C_GOLD if used else PANEL)
            box.set_linestyle("-" if used else "--")
            t.set_text("USED" if used else "½")
            t.set_color(BG if used else C_GOLD)
            t.set_fontsize(8 if used else 11)
        coupons_txt.set_text(f"{coupons} / {C}")
        for j, (marker, route, ptxt, tag) in enumerate(rows):
            col = leg_color(legs[j])
            if j == current:
                marker.set_text("▶")
                marker.set_color(col)
                route.set_color(INK)
                ptxt.set_color(col)
                tag.set_color(C_GOLD)
            elif j < flown:
                marker.set_text("✓")
                marker.set_color(C_START)
                route.set_color(INK)
                ptxt.set_color(col)
                tag.set_color(C_GOLD)
            else:
                marker.set_text("")
                route.set_color(DIM)
                ptxt.set_color(DIM)
                tag.set_color(DIM)

    def render(frame):
        phase, j, k = timeline[frame]
        flown = {"intro": 0, "depart": j, "fly": j, "arrive": j + 1, "end": L}[phase]
        current = j if phase in ("depart", "fly") else None
        for jj in range(L):
            paint_leg(jj, "done" if jj < flown else "active" if jj == current else "idle")

        spent = sum(legs[jj]["paid"] for jj in range(flown))
        t = ease(k / (FLY - 1)) if phase == "fly" else 0
        if phase == "fly":
            spent += legs[j]["paid"] * t
        coupons = sum(legs[jj]["coupon"] for jj in range(flown))
        if current is not None and legs[current]["coupon"]:
            coupons += 1
        set_hud(spent, min(flown, L - 1), coupons, current, flown)

        # plane + trail
        trail.set_data([], [])
        if phase == "fly":
            leg = legs[j]
            p0, p2, rad = pos[leg["u"]], pos[leg["v"]], rads[leg["idx"]]
            x, y = bezier(p0, p2, rad, t)
            plane.set_position((x, y))
            plane.set_rotation(bezier_angle(p0, p2, rad, t))
            pts = [bezier(p0, p2, rad, t * s / 30) for s in range(31)]
            trail.set_data([q[0] for q in pts], [q[1] for q in pts])
            col = leg_color(leg)
            trail.set_color(col)
            trail.set_path_effects(glow(col, 9))
        else:
            city = 1 if phase == "intro" else (legs[j]["u"] if phase == "depart" else legs[j]["v"])
            plane.set_position(pos[city])
            if phase == "depart":
                leg = legs[j]
                plane.set_rotation(bezier_angle(pos[leg["u"]], pos[leg["v"]], rads[leg["idx"]], 0))
        plane.set_visible(not (phase == "end" and k > 10))

        # arrival pulse
        if phase == "arrive" or (phase == "end" and k < 20):
            city = legs[j]["v"]
            f = k / (ARRIVE if phase == "arrive" else 20)
            pulse.set_center(pos[city])
            pulse.set_radius(node_r * (1.2 + 2.2 * f))
            pulse.set_edgecolor(role_color.get(city) or INK)
            pulse.set_alpha(0.9 * (1 - f))
        else:
            pulse.set_alpha(0)

        # coupon pop-up
        popup.set_alpha(0)
        if current is not None and legs[current]["coupon"]:
            leg = legs[current]
            age = k if phase == "depart" else DEPART + k
            if age < 45:
                mx, my = labels[leg["idx"]].get_position()
                popup.set_text(f"COUPON!  −{leg['price'] - leg['paid']}")
                popup.set_position((mx, my + node_r * (0.8 + 1.4 * age / 45)))
                popup.set_alpha(min(1, 3 * (1 - age / 45)))

        # final banner
        if phase == "end":
            msg = f"✓  ARRIVED  ·  TOTAL {cost}"
            if total_saved:
                msg += f"  ·  SAVED {total_saved}"
            banner.set_text(msg)
            banner.set_alpha(min(1, k / 8))
            banner.get_bbox_patch().set_alpha(min(1, k / 8))
        else:
            banner.set_alpha(0)
            banner.get_bbox_patch().set_alpha(0)

    # ---- static image (final state) ----
    if L:
        render(len(timeline) - 1)
    else:
        set_hud(0, 0, 0, None, 0)
        plane.set_visible(False)
        if cost == 0:
            banner.set_text("Already at the destination · TOTAL 0")
            banner.set_alpha(1)
    png = f"{out_prefix}.png"
    fig.savefig(png, dpi=120, facecolor=BG)
    print(f"Saved graph image -> {png}")

    if not L:
        if show:
            plt.show()
        plt.close(fig)
        return png, None

    anim = FuncAnimation(fig, render, frames=len(timeline), interval=1000 / fps,
                         repeat=True)
    gif = f"{out_prefix}_journey.gif"
    print(f"Rendering animation ({len(timeline)} frames)...")
    def progress(i, total):
        if i % 25 == 0 or i == total - 1:
            print(f"\r  frame {i + 1}/{total}", end="", flush=True)

    anim.save(gif, writer=PillowWriter(fps=fps), dpi=72, savefig_kwargs={"facecolor": BG},
              progress_callback=progress)
    print()
    print(f"Saved journey animation -> {gif}")
    if show:
        plt.show()
    plt.close(fig)
    return png, gif


# ----------------------------------------------------------------------------
# Driver
# ----------------------------------------------------------------------------


def run(n, flights, K, C, out_prefix, show, input_csv=None):
    validate(n, flights, K, C)
    cost, legs = solve(n, flights, K, C)
    print()
    print(cost)  # Output 1
    print(describe(cost, legs, K, C))
    if input_csv:
        save_input_csv(input_csv, n, flights)
        print(f"Saved generated graph -> {input_csv}")
    out_csv = f"{out_prefix}.csv"
    write_result_csv(out_csv, n, flights, legs, cost, K, C)
    print(f"Saved result CSV -> {out_csv}")
    visualize(n, flights, cost, legs, K, C, out_prefix, show=show)
    return cost


def read_two_ints(prompt):
    while True:
        try:
            a, b = (int(x) for x in input(prompt).split())
            return a, b
        except ValueError:
            print("  please enter two integers, e.g. '2 1'")


def interactive(args):
    print("Budget Traveller with Coupons")
    print("  1) Type the input (N M / K C / u v p ...)")
    print("  2) K C + read flights from a CSV file")
    print("  3) Generate a random valid graph for given K and C")
    choice = input("Choose 1/2/3: ").strip()
    if choice == "1":
        src = input("Input file path (blank to type it here): ").strip()
        if src:
            text = open(src).read()
        else:
            n, m = read_two_ints("N M: ")
            k, c = read_two_ints("K C: ")
            lines = [f"{n} {m}", f"{k} {c}"]
            print(f"Enter {m} flights as 'u v p':")
            for _ in range(m):
                lines.append(input())
            text = "\n".join(lines)
        n, flights, K, C = parse_text(text)
        run(n, flights, K, C, args.out, not args.no_show)
    elif choice == "2":
        K, C = read_two_ints("K C: ")
        path = input("CSV file path: ").strip()
        n, flights = read_flights_csv(path)
        run(n, flights, K, C, args.out, not args.no_show)
    elif choice == "3":
        K, C = read_two_ints("K C: ")
        raw = input("Number of cities N (blank = random): ").strip()
        n, flights = generate_graph(K, C, int(raw) if raw else None)
        run(n, flights, K, C, args.out, not args.no_show,
            input_csv=f"{args.out}_input.csv")
    else:
        print("Unknown choice")


def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("mode", nargs="?", choices=["text", "csv", "generate"])
    ap.add_argument("file", nargs="?", help="input file (text or csv mode); '-' = stdin")
    ap.add_argument("--k", type=int, help="max stops K (csv/generate)")
    ap.add_argument("--c", type=int, help="number of coupons C (csv/generate)")
    ap.add_argument("--n", type=int, help="number of cities (generate)")
    ap.add_argument("--seed", type=int, help="random seed (generate)")
    ap.add_argument("--out", default="result", help="output file prefix")
    ap.add_argument("--no-show", action="store_true", help="don't open a window")
    args = ap.parse_args()

    try:
        if args.mode is None:
            interactive(args)
        elif args.mode == "text":
            text = sys.stdin.read() if args.file in (None, "-") else open(args.file).read()
            n, flights, K, C = parse_text(text)
            run(n, flights, K, C, args.out, not args.no_show)
        elif args.mode == "csv":
            if not args.file or args.k is None or args.c is None:
                ap.error("csv mode needs: FILE --k K --c C")
            n, flights = read_flights_csv(args.file)
            run(n, flights, args.k, args.c, args.out, not args.no_show)
        else:
            if args.k is None or args.c is None:
                ap.error("generate mode needs: --k K --c C")
            n, flights = generate_graph(args.k, args.c, args.n, args.seed)
            run(n, flights, args.k, args.c, args.out, not args.no_show,
                input_csv=f"{args.out}_input.csv")
    except (ValueError, OSError) as e:
        print(f"Error: {e}", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
