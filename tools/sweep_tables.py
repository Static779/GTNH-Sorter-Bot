#!/usr/bin/env python3
"""Turns tools/sweep.sh output into the small tables the stress-test dashboard shows.

Usage: python3 tools/sweep_tables.py sweep.csv outdir
Writes: oat.csv (one-at-a-time means), shortfalls.csv, heat.csv, drivers.csv
"""
import os
import sys

import numpy as np
import pandas as pd
from scipy.stats import spearmanr

df = pd.read_csv(sys.argv[1])
out = sys.argv[2]
os.makedirs(out, exist_ok=True)

METRICS = ["strandedPct", "miscSharePct", "minutesOpenSurvey", "minutesLocalRead", "walkPerMove", "trips",
           "carryUtilPct", "keptOpenTrips", "fineCatsPerChest", "contiguousPct", "scatteredBuckets",
           "tightBuckets", "avgFreePct", "nearEmptyChests", "combineFreed", "buckets", "planMs", "actualFill"]
NUM_VARS = ["chests", "fill", "itemTypes", "skew", "mess", "partial", "carry", "minSplit", "layout", "keepFree",
            "toolShare"]
VAR_LABEL = {"chests": "Chests", "fill": "Room fill", "itemTypes": "Item types", "skew": "Size skew",
             "mess": "Mess", "partial": "Partial stacks", "carry": "Free inventory slots",
             "minSplit": "Stacks for own chest", "layout": "Layout tidiness", "keepFree": "Keep free",
             "toolShare": "Unstackable share", "shape": "Room shape", "sizes": "Chest sizes",
             "catalogMix": "Item mix", "start": "Start position"}
SHORT = [
    ("stranded", "Stacks left with nowhere to go (over 1%)", lambda r: r.strandedPct > 1.0),
    ("misc", "Over 10% of stacks in misc", lambda r: r.miscSharePct > 10),
    ("scattered", "Groups' chests not touching (under 75% together)", lambda r: r.contiguousPct < 75),
    ("tight", "3+ groups under 10% free", lambda r: r.tightBuckets >= 3),
    ("nearEmpty", "5+ chests under 10% full", lambda r: r.nearEmptyChests >= 5),
    ("slow", "Over 15 minutes on a server", lambda r: r.minutesOpenSurvey > 15),
    ("walky", "Over 4 blocks walked per stack moved", lambda r: r.walkPerMove > 4),
    ("slowPlan", "Planning over 3 seconds", lambda r: r.planMs > 3000),
    ("unsafe", "Plan failed its safety replay", lambda r: r.checkFails > 0),
    ("nondet", "Same room, different plan", lambda r: r.deterministic < 1),
]

# one-at-a-time means, one row per setting value
oat = df[(df.part == "oat") & (df.varied != "baseline")]
rows = []
for var, g in oat.groupby("varied", sort=False):
    for i, (val, h) in enumerate(g.groupby("value", sort=False)):
        m = h[METRICS].mean()
        rows.append({"setting": VAR_LABEL.get(var, var), "value": str(val), "step": i, "rooms": len(h),
                     **{k: round(float(m[k]), 3) for k in METRICS}})
pd.DataFrame(rows).to_csv(os.path.join(out, "oat.csv"), index=False)

rnd = df[df.part == "random"]
srows = []
for sid, label, test in SHORT:
    hit = rnd.apply(test, axis=1).astype(int)
    assoc = []
    for v in NUM_VARS:
        x = rnd[v].where(rnd[v] >= 0) if v == "toolShare" else rnd[v]
        ok = x.notna()
        if ok.sum() > 10 and hit[ok].nunique() > 1:
            assoc.append((VAR_LABEL[v], float(spearmanr(x[ok], hit[ok]).statistic)))
    assoc.sort(key=lambda t: -abs(t[1]))
    a1 = assoc[0] if assoc else ("", float("nan"))
    a2 = assoc[1] if len(assoc) > 1 else ("", float("nan"))
    srows.append({"id": sid, "shortfall": label, "ratePct": round(100 * hit.mean(), 1), "rooms": int(hit.sum()),
                  "driver1": a1[0], "rho1": round(a1[1], 2), "driver2": a2[0], "rho2": round(a2[1], 2)})
pd.DataFrame(srows).to_csv(os.path.join(out, "shortfalls.csv"), index=False)

fb = pd.cut(rnd.actualFill, [0, 50, 65, 75, 85, 92, 101],
            labels=["under 50%", "50-65%", "65-75%", "75-85%", "85-92%", "92%+"])
cb = pd.cut(rnd.chests, [0, 16, 32, 64, 96, 200], labels=["8-16", "17-32", "33-64", "65-96", "97-128"])
grid = rnd.assign(fb=fb, cb=cb, hit=(rnd.strandedPct > 1).astype(int))
hrows = []
for (f, c), g in grid.groupby(["fb", "cb"], observed=False):
    hrows.append({"fill": str(f), "chests": str(c), "rooms": len(g),
                  "strandedRatePct": round(100 * g.hit.mean(), 0) if len(g) else None,
                  "meanStrandedPct": round(float(g.strandedPct.mean()), 2) if len(g) else None})
pd.DataFrame(hrows).to_csv(os.path.join(out, "heat.csv"), index=False)

drows = []
for k in METRICS:
    for v in NUM_VARS:
        x = rnd[v].where(rnd[v] >= 0) if v == "toolShare" else rnd[v]
        ok = x.notna()
        rho = spearmanr(x[ok], rnd[k][ok]).statistic
        drows.append({"measure": k, "setting": VAR_LABEL[v], "rho": None if np.isnan(rho) else round(float(rho), 2)})
pd.DataFrame(drows).to_csv(os.path.join(out, "drivers.csv"), index=False)
print("wrote", out)
