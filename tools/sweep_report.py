#!/usr/bin/env python3
"""Summarises tools/sweep.sh output: one-at-a-time effects, what drives each shortfall in the random rooms,
and how often each shortfall happens. Prints a text summary and writes <csv>.json for the report page.

Usage: python3 tools/sweep_report.py sweep.csv
"""
import json
import sys

import numpy as np
import pandas as pd
from scipy.stats import spearmanr

df = pd.read_csv(sys.argv[1])
out = {}

KEY = ["strandedPct", "miscSharePct", "purityPct", "minutesOpenSurvey", "minutesLocalRead", "walkPerMove",
       "trips", "carryUtilPct", "keptOpenTrips", "fineCatsPerChest", "contiguousPct", "scatteredBuckets",
       "tightBuckets", "minFreePct", "nearEmptyChests", "combineFreed", "buckets", "planMs", "actualFill"]
NUM_VARS = ["chests", "fill", "itemTypes", "skew", "mess", "partial", "carry", "minSplit", "layout", "keepFree",
            "toolShare"]
CAT_VARS = ["shape", "sizes", "catalogMix", "start"]

# Shortfall definitions: (name, test on a row, plain description)
SHORT = [
    ("stranded", lambda r: r.strandedPct > 1.0, "more than 1% of stacks have nowhere to go"),
    ("miscHeavy", lambda r: r.miscSharePct > 10, "more than 10% of stacks end up in misc"),
    ("scattered", lambda r: r.contiguousPct < 75, "under 75% of multi-chest groups sit in touching chests"),
    ("noRoomToGrow", lambda r: r.tightBuckets >= 3, "3+ groups left under 10% free"),
    ("nearEmpty", lambda r: r.nearEmptyChests >= 5, "5+ chests left under 10% full"),
    ("slow", lambda r: r.minutesOpenSurvey > 15, "over 15 minutes when chests must be opened (servers)"),
    ("walky", lambda r: r.walkPerMove > 4, "over 4 blocks walked per stack moved"),
    ("slowPlan", lambda r: r.planMs > 3000, "planning over 3 s"),
    ("unsafe", lambda r: r.checkFails > 0, "plan failed its safety replay"),
    ("nondeterministic", lambda r: r.deterministic < 1, "same room gave a different plan"),
]

# ---- one-at-a-time ----
oat = df[df.part == "oat"]
base = oat[oat.varied == "baseline"][KEY].mean()
out["baseline"] = base.round(2).to_dict()
oat_tab = {}
for var, g in oat[oat.varied != "baseline"].groupby("varied", sort=False):
    rows = []
    for val, h in g.groupby("value", sort=False):
        m = h[KEY].mean()
        rows.append({"value": str(val), **{k: round(float(m[k]), 2) for k in KEY},
                     "p90strandedPct": round(float(h.strandedPct.quantile(0.9)), 2)})
    oat_tab[var] = rows
out["oat"] = oat_tab

# ---- random: drivers ----
rnd = df[df.part == "random"].copy()
out["randomCount"] = int(len(rnd))
drivers = {}
for k in KEY:
    row = {}
    for v in NUM_VARS:
        x = rnd[v]
        if v == "toolShare":
            x = x.where(x >= 0)  # -1 means natural catalogue
        ok = x.notna()
        rho = spearmanr(x[ok], rnd[k][ok]).statistic if ok.sum() > 10 else float("nan")
        row[v] = None if np.isnan(rho) else round(float(rho), 2)
    for v in CAT_VARS:
        means = rnd.groupby(v)[k].mean()
        row[v] = {str(a): round(float(b), 2) for a, b in means.items()}
    drivers[k] = row
out["drivers"] = drivers

# ---- shortfall frequency and conditions ----
short = {}
for name, test, desc in SHORT:
    hit = rnd.apply(test, axis=1)
    entry = {"desc": desc, "rate": round(100 * float(hit.mean()), 1), "count": int(hit.sum())}
    # strongest numeric associations with this shortfall
    assoc = []
    for v in NUM_VARS:
        x = rnd[v].where(rnd[v] >= 0) if v == "toolShare" else rnd[v]
        ok = x.notna()
        if ok.sum() > 10 and hit[ok].nunique() > 1:
            rho = spearmanr(x[ok], hit[ok].astype(int)).statistic
            assoc.append((v, round(float(rho), 2)))
    assoc.sort(key=lambda t: -abs(t[1]))
    entry["drivers"] = assoc[:4]
    for v in CAT_VARS:
        entry["by_" + v] = {str(a): round(100 * float(b), 1) for a, b in hit.groupby(rnd[v]).mean().items()}
    short[name] = entry
out["shortfalls"] = short

# ---- heatmap: stranded rate by actual fill x chests ----
fb = pd.cut(rnd.actualFill, [0, 50, 65, 75, 85, 92, 101], labels=["<50", "50-65", "65-75", "75-85", "85-92", "92+"])
cb = pd.cut(rnd.chests, [0, 16, 32, 64, 96, 200], labels=["8-16", "17-32", "33-64", "65-96", "97-128"])
heat = (rnd.strandedPct > 1).groupby([fb, cb], observed=False).mean().unstack()
cnt = (rnd.strandedPct > 1).groupby([fb, cb], observed=False).size().unstack()
out["heat"] = {"rows": [str(i) for i in heat.index], "cols": [str(c) for c in heat.columns],
               "rate": [[None if np.isnan(v) else round(100 * float(v), 0) for v in r] for r in heat.values],
               "n": [[int(v) for v in r] for r in cnt.values]}
# misc share by itemTypes per chest
rnd["typesPerChest"] = rnd.itemTypes / rnd.chests
tb = pd.cut(rnd.typesPerChest, [0, 2, 5, 10, 20, 1000], labels=["<2", "2-5", "5-10", "10-20", "20+"])
out["miscByTypesPerChest"] = {str(k): round(float(v), 1) for k, v in rnd.groupby(tb, observed=False).miscSharePct.mean().items()}
out["strandByTypesPerChest"] = {str(k): round(float(v), 2) for k, v in rnd.groupby(tb, observed=False).strandedPct.mean().items()}
# safety totals over every room
out["totals"] = {"rooms": int(len(df)), "checkFails": int((df.checkFails > 0).sum()),
                 "nondeterministic": int((df.deterministic < 1).sum()), "maxPlanMs": round(float(df.planMs.max()), 0),
                 "medianPlanMs": round(float(df.planMs.median()), 0)}
# scatter points for charts (random rooms)
out["points"] = rnd[["actualFill", "chests", "strandedPct", "minutesOpenSurvey", "minutesLocalRead", "carry",
                     "walkPerMove", "miscSharePct", "typesPerChest", "contiguousPct", "layout", "shape"]].round(2).to_dict("records")

json.dump(out, open(sys.argv[1] + ".json", "w"))

# ---- text summary ----
print(f"rooms: {len(df)} ({len(oat)} one-at-a-time, {len(rnd)} random)")
print("totals:", out["totals"])
print("\nbaseline:", {k: out["baseline"][k] for k in KEY})
print("\nshortfalls in random rooms:")
for name, e in short.items():
    print(f"  {name:16s} {e['rate']:5.1f}%  ({e['desc']})  drivers {e['drivers']}")
print("\nstranded >1% rate by fill x chests:")
print(pd.DataFrame(out["heat"]["rate"], index=out["heat"]["rows"], columns=out["heat"]["cols"]))
print("\nmisc share by item types per chest:", out["miscByTypesPerChest"])
print("stranded % by item types per chest:", out["strandByTypesPerChest"])
for var, rows in oat_tab.items():
    print(f"\nOAT {var}:")
    for r in rows:
        print("  {value:>10s} stranded {strandedPct:6.2f}% misc {miscSharePct:5.1f}% purity {purityPct:5.1f}% "
              "open-min {minutesOpenSurvey:5.1f} local-min {minutesLocalRead:5.1f} walk/move {walkPerMove:4.2f} "
              "contig {contiguousPct:5.1f}% tight {tightBuckets:4.1f} nearEmpty {nearEmptyChests:4.1f} "
              "fine/chest {fineCatsPerChest:4.1f} buckets {buckets:5.1f} plan {planMs:6.0f}ms".format(**r))
