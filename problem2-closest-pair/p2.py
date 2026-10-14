import math
import random
import sys
import time

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt


def dist(a, b):
    return math.hypot(a[0] - b[0], a[1] - b[1])



def closest_pair_brute(points):
    n = len(points)
    if n < 2:
        return None, None
    best, pair = float("inf"), None
    for i in range(n):
        for j in range(i + 1, n):
            d = dist(points[i], points[j])
            if d < best:
                best, pair = d, (points[i], points[j])
    return best, pair



def closest_pair_dc(points, info=None):
    if len(points) < 2:
        return None, None

    pts = [(x, y, i) for i, (x, y) in enumerate(points)]
    px = sorted(pts, key=lambda p: (p[0], p[1], p[2]))
    py = sorted(pts, key=lambda p: (p[1], p[0], p[2]))

    def solve(px, py, depth):
        n = len(px)

        if n <= 3:
            best, pair = float("inf"), None
            for i in range(n):
                for j in range(i + 1, n):
                    d = dist(px[i], px[j])
                    if d < best:
                        best, pair = d, (px[i], px[j])
            return best, pair

        mid = n // 2
        split_x = px[mid][0]
        left_ids = {p[2] for p in px[:mid]}
        left_y = [p for p in py if p[2] in left_ids]
        right_y = [p for p in py if p[2] not in left_ids]

        dl, pl = solve(px[:mid], left_y, depth + 1)
        dr, pr = solve(px[mid:], right_y, depth + 1)
        best, pair = (dl, pl) if dl <= dr else (dr, pr)
        d = best

        strip = [p for p in py if abs(p[0] - split_x) < d]
        for i in range(len(strip)):
            j = i + 1
            while j < len(strip) and strip[j][1] - strip[i][1] < best:
                dd = dist(strip[i], strip[j])
                if dd < best:
                    best, pair = dd, (strip[i], strip[j])
                j += 1

        if depth == 0 and info is not None:
            info.update(split_x=split_x, d=d, strip=[(p[0], p[1]) for p in strip])
        return best, pair

    best, pair = solve(px, py, 0)
    return best, ((pair[0][0], pair[0][1]), (pair[1][0], pair[1][1]))


def timed(fn, *args):
    t = time.perf_counter()
    result = fn(*args)
    return result, (time.perf_counter() - t) * 1000.0  # ms


def fmt_pair(p):
    return f"({p[0]:g}, {p[1]:g}) ({p[2]:g}, {p[3]:g})"


def generate_points(n):
    return [(round(random.uniform(0, 100), 2), round(random.uniform(0, 100), 2))
            for _ in range(n)]


def agree(d1, d2):
    return math.isclose(d1, d2, rel_tol=1e-9, abs_tol=1e-9)


def run_verification():
    cases = [
        ("N = 2", [(0, 0), (3, 4)], 5.0),
        ("duplicate points", [(1, 1), (5, 5), (1, 1), (9, 2)], 0.0),
        ("all points identical", [(2, 2)] * 6, 0.0),
        ("collinear (horizontal)", [(0, 3), (10, 3), (4, 3), (4.5, 3), (20, 3)], 0.5),
        ("collinear (vertical)", [(7, 0), (7, 9), (7, 4), (7, 4.25), (7, -3)], 0.25),
        ("collinear (diagonal)", [(i, i) for i in (0, 5, 9, 10, 20)], math.sqrt(2)),
        ("same x for all", [(2, y) for y in (0, 6, 13, 14, 30, 50)], 1.0),
        ("same y for all", [(x, 2) for x in (0, 6, 13, 15, 30, 50)], 2.0),
        ("negative / mixed", [(-5, -5), (5, 5), (-5.5, -5), (0, 0)], 0.5),
    ]
    print("\n--- Corner-case verification ---")
    ok = True
    for name, pts, expected in cases:
        b, _ = closest_pair_brute(pts)
        c, _ = closest_pair_dc(pts)
        good = agree(b, expected) and agree(c, expected)
        ok &= good
        print(f"{'PASS' if good else 'FAIL'}  {name:26s} brute={b:.6f}  dc={c:.6f}")
    print("N = 1 / N = 0 ->", closest_pair_brute([(1, 1)]), closest_pair_dc([]))

    trials, bad = 2000, 0
    for _ in range(trials):
        n = random.randint(2, 40)
        r = random.choice([3, 10, 1000])
        pts = [(random.randint(0, r), random.randint(0, r)) for _ in range(n)]
        if not agree(closest_pair_brute(pts)[0], closest_pair_dc(pts)[0]):
            bad += 1
    print(f"Random stress test: {trials - bad}/{trials} agree")
    return ok and bad == 0


def plot_points(points, bf, dc, info, path):
    fig, ax = plt.subplots(figsize=(9, 7))
    xs, ys = zip(*points)
    ax.scatter(xs, ys, s=12, color="gray", alpha=0.6, label="points", zorder=1)

    sx, d = info["split_x"], info["d"]
    ax.axvspan(sx - d, sx + d, color="gold", alpha=0.25, label=f"strip (width 2d = {2*d:.3f})")
    ax.axvline(sx, color="black", linestyle="--", linewidth=1, label=f"dividing line x = {sx:g}")
    if info["strip"]:
        sxs, sys_ = zip(*info["strip"])
        ax.scatter(sxs, sys_, s=22, color="orange", zorder=2, label="points in strip")

    (a, b) = bf[1]
    ax.plot([a[0], b[0]], [a[1], b[1]], color="red", linewidth=2, zorder=3)
    ax.scatter([a[0], b[0]], [a[1], b[1]], s=160, facecolors="none",
               edgecolors="red", linewidths=2, zorder=4, label=f"Brute-Force pair (d={bf[0]:.6f})")
    (a, b) = dc[1]
    ax.plot([a[0], b[0]], [a[1], b[1]], color="blue", linewidth=2, linestyle=":", zorder=3)
    ax.scatter([a[0], b[0]], [a[1], b[1]], s=60, color="blue", marker="x",
               zorder=5, label=f"Divide-and-Conquer pair (d={dc[0]:.6f})")

    ax.set_title("Closest pair of points")
    ax.set_xlabel("x")
    ax.set_ylabel("y")
    ax.legend(loc="upper right", fontsize=8)
    fig.tight_layout()
    fig.savefig(path, dpi=130)
    plt.close(fig)


def benchmark(sizes, path):
    print("\n--- Running time vs N (ms) ---")
    print(f"{'N':>7} {'Brute':>12} {'D&C':>10}  agree")
    tb, td = [], []
    for n in sizes:
        pts = generate_points(n)
        (rb, t1), (rd, t2) = timed(closest_pair_brute, pts), timed(closest_pair_dc, pts)
        tb.append(t1)
        td.append(t2)
        print(f"{n:7d} {t1:12.3f} {t2:10.3f}  {agree(rb[0], rd[0])}")

    fig, ax = plt.subplots(figsize=(8, 5))
    ax.plot(sizes, tb, "o-", color="red", label="Brute-Force  O(N²)")
    ax.plot(sizes, td, "s-", color="blue", label="Divide-and-Conquer  O(N log N)")
    ax.set_xlabel("N (number of points)")
    ax.set_ylabel("running time (ms)")
    ax.set_title("Running time vs N")
    ax.grid(alpha=0.3)
    ax.legend()
    fig.tight_layout()
    fig.savefig(path, dpi=130)
    plt.close(fig)


def main():
    if sys.stdin.isatty():
        text = input("Enter N (number of points): ")
    else:
        text = sys.stdin.read()
    data = text.split()
    if not data:
        print("Please enter N.")
        return
    try:
        n = int(data[0])
    except ValueError:
        print("N must be an integer.")
        return
    if len(sys.argv) > 1:
        random.seed(int(sys.argv[1]))

    if n < 2:
        print("At least two points are required (N < 2).")
        return

    points = generate_points(n)
    info = {}
    (bd, bp), t_bf = timed(closest_pair_brute, points)
    (dd, dp), t_dc = timed(closest_pair_dc, points, info)

    print(f"Brute-Force: {bd:.6f} {fmt_pair(bp[0] + bp[1])}")
    print(f"Divide-and-Conquer: {dd:.6f} {fmt_pair(dp[0] + dp[1])}")
    print(f"Time: Brute-Force = {t_bf:.3f} ms, Divide-and-Conquer = {t_dc:.3f} ms")

    print("\nBoth algorithms agree on the minimum distance:", agree(bd, dd))
    run_verification()

    if n >= 4 and info:
        plot_points(points, (bd, bp), (dd, dp), info, "closest_pair_plot.png")
    benchmark([100, 200, 400, 800, 1600, 3200], "runtime_plot.png")
    print("\nSaved: closest_pair_plot.png, runtime_plot.png")


if __name__ == "__main__":
    main()