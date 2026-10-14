# dsahack.py — Startup Guide

**Budget Traveller with Coupons:** find the cheapest way to fly from city 1 to city N with
at most **K stops** and **C coupons**. Each coupon halves one flight's price (rounded down),
and you can use at most one coupon per flight.

---

## 1. One-time setup

You need Python 3.9 or newer. Install the two libraries the script uses:

```bash
pip3 install matplotlib networkx
```

Check that both are installed:

```bash
python3 -c "import matplotlib, networkx; print('ready')"
```

Go to the project folder:

```bash
cd ~/Akshath/Code/Python
```

---

## 2. Input files included

| File | Used with | What it contains |
|---|---|---|
| `sample_input.txt` | Option 1 | 5 cities, 7 flights, K=2, C=1 |
| `no_route_input.txt` | Option 1 | 4 cities in a chain, K=1, so there's no possible route (answer −1) |
| `flights.csv` | Option 2 | 8 cities, 12 flights (you choose K and C) |

**`sample_input.txt`** (Option 1 format):
```
5 7          <- N (cities)  M (flights)
2 1          <- K (max stops)  C (coupons)
1 2 100      <- flight from 1 to 2 costs 100
2 5 600
1 3 300
3 4 100
4 5 100
1 5 900
2 3 50
```

**`flights.csv`** (Option 2 format: a header row, then `u,v,price`):
```
u,v,price
1,2,120
1,3,450
1,4,80
2,5,200
3,8,300
4,5,90
4,6,700
5,6,60
5,8,900
6,7,50
7,8,40
2,8,1000
```
N is taken from the highest city number in the file (8 here).

---

## 3. Run it with the menu

```bash
python3 dsahack.py
```

```
Budget Traveller with Coupons
  1) Type the input (N M / K C / u v p ...)
  2) K C + read flights from a CSV file
  3) Generate a random valid graph for given K and C
Choose 1/2/3:
```

### Option 1: text input
```
Choose 1/2/3: 1
Input file path (blank to type it here): sample_input.txt
```
To type the input yourself instead, press **Enter** at the file prompt, then:
```
N M: 5 7
K C: 2 1
Enter 7 flights as 'u v p':
1 2 100
2 5 600
1 3 300
3 4 100
4 5 100
1 5 900
2 3 50
```

**Expected output:**
```
350
Minimum cost: 350
Route: 1 -> 3 -> 4 -> 5
Stops used: 2/2   Coupons used: 1/1
  1 -> 3: 300 -> 150 (coupon)
  3 -> 4: 100
  4 -> 5: 100
```

### Option 2: K C + CSV file
```
Choose 1/2/3: 2
K C: 2 1
CSV file path: flights.csv
```
**Expected output:**
```
525
Minimum cost: 525
Route: 1 -> 3 -> 8
Stops used: 1/2   Coupons used: 1/1
  1 -> 3: 450 -> 225 (coupon)
  3 -> 8: 300
```

Here's how K and C change the answer for the same `flights.csv`:

| K C | Min cost | Route |
|---|---|---|
| `1 0` | 750 | 1 → 3 → 8 |
| `2 1` | 525 | 1 → 3 → 8 (coupon on 450) |
| `4 2` | 235 | 1 → 4 → 5 → 6 → 7 → 8 (coupons on 80 and 90) |

### Option 3: generate a random graph
```
Choose 1/2/3: 3
K C: 3 2
Number of cities N (blank = random): 9
```
The generated graph always has a valid route. It's also saved as `result_input.csv`, so you
can rerun it later with Option 2.

---

## 4. What you get

After each run, these files appear in the folder (`result` is the default name; change it with `--out`):

| File | What it is |
|---|---|
| terminal | **Output 1:** the minimum cost (or `-1`), then the route breakdown |
| `result.csv` | **Output 2:** every flight; `on_path = x` marks the chosen route, `coupon = x` marks flights where a coupon was used, plus `paid` and `leg` number |
| `result.png` | Image of the solved graph |
| `result_journey.gif` | Animated journey |

A window then opens and plays the animation. **Close the window to end the program.**

**Colours:** green = start · red = destination · orange = stops · blue = flight ·
gold = coupon used (label shows `original → discounted`).

---

## 5. Shortcuts (no menu)

```bash
python3 dsahack.py text sample_input.txt
```
```bash
python3 dsahack.py csv flights.csv --k 2 --c 1
```
```bash
python3 dsahack.py generate --k 3 --c 2 --n 9 --seed 7
```
```bash
python3 dsahack.py text no_route_input.txt
```
The last one prints `-1`.

Extra flags:
- `--out NAME` names the output files `NAME.csv`, `NAME.png` and `NAME_journey.gif`
- `--no-show` saves the files without opening a window
- `--seed N` (generate only) gives the same random graph every time

---

## 6. Troubleshooting

| Problem | Fix |
|---|---|
| `ModuleNotFoundError: matplotlib` | `pip3 install matplotlib networkx` |
| It seems stuck on "Rendering animation…" | It's working: the GIF takes about a minute. Watch the `frame x/y` counter. |
| Program doesn't exit | Close the animation window, or use `--no-show` |
| `flight 3->9 uses a city outside 1..N` | A flight uses a city number above N; fix N or that flight |
| `expected 7 flights, found 6` | The flight count M doesn't match the number of flight lines |
