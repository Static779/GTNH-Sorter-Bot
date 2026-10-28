package com.whalestorm.chestsorter.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Gives each chest a category, balancing four things (weighted):
 * <ol>
 * <li><b>Few moves</b>: a chest that already mostly holds a category tends to keep it.</li>
 * <li><b>Room</b>: a category gets enough chests for all its stacks (big categories get several).</li>
 * <li><b>Together</b>: the chests of one category sit next to each other.</li>
 * <li><b>Related nearby</b>: related categories ({@link Relations}) sit in nearby chests.</li>
 * </ol>
 * When there are more categories than chests, the smallest category merges into its most related category (its items
 * go to that category's chests). A small category with no related category is left where it is.
 *
 * Steps: merge until categories fit the chest count; start from "each chest takes what it mostly holds"; give
 * categories without a chest an empty chest; then improve with single-chest changes and two-chest swaps while the
 * weighted cost drops. Deterministic: same input, same result.
 */
public final class Assigner {

    private Assigner() {}

    /** Cost weights. moveWeight: per stack that has to move. layoutWeight: scales the together and nearby terms. */
    public static final class Weights {

        public final double moveWeight, layoutWeight;

        public Weights(double moveWeight, double layoutWeight) {
            this.moveWeight = moveWeight;
            this.layoutWeight = layoutWeight;
        }

        public static final Weights DEFAULT = new Weights(1.0, 1.0);
    }

    /**
     * Per missing slot of room. Room must win over layout at any layoutWeight: the sweep (tools/sweep.sh) showed a
     * weight of 4 let layoutWeight 3 leave ~26 stacks with nowhere to go, to keep chests tidy.
     */
    static final double CAPACITY_WEIGHT = 50.0;
    /** Relative strength of the "related nearby" term against the "together" term. */
    static final double RELATED_WEIGHT = 0.5;
    private static final int MAX_PASSES = 30;
    /**
     * Hard cap on cost evaluations (keeps planning well under a second with ~100 chests); count-based so it stays
     * deterministic.
     */
    private static final int MAX_EVALS = 60000;

    /** How related two categories (or buckets) are, 0..1. */
    public interface Relatedness {

        double score(String a, String b);
    }

    public static final class Result {

        /** chest id → category. Chests absent here stay empty and unused. */
        public final Map<Integer, String> chestCategory;
        /** Categories with items but no chest and no related category; those items stay where they are. */
        public final List<String> unassigned;
        /** Small category → the category whose chests take its items. */
        public final Map<String, String> merged;
        /** Final cost and its parts, for the trace. */
        public final String costReport;
        /** How fine categories were grouped into buckets before assigning chests (null without a taxonomy). */
        public final Taxonomy.Buckets buckets;

        Result(Map<Integer, String> chestCategory, List<String> unassigned, Map<String, String> merged,
            String costReport, Taxonomy.Buckets buckets) {
            this.chestCategory = chestCategory;
            this.unassigned = unassigned;
            this.merged = merged;
            this.costReport = costReport;
            this.buckets = buckets;
        }

        /** The category whose chests an item of {@code category} belongs in (itself unless merged). */
        public String effective(String category) {
            String m = merged.get(category);
            return m != null ? m : category;
        }
    }

    public static Result assign(List<ChestModel> chests, Map<Integer, ItemInfo> items) {
        return assign(chests, items, new HashMap<Integer, String>(), Weights.DEFAULT);
    }

    public static Result assign(List<ChestModel> chests, Map<Integer, ItemInfo> items, Map<Integer, String> locked) {
        return assign(chests, items, locked, Weights.DEFAULT);
    }

    /**
     * @param chests in a stable order (nearest first); ties are broken by this order
     * @param items  key → info, must cover every key in the chests
     * @param locked chest id → forced category (may be empty)
     */
    public static Result assign(List<ChestModel> chests, Map<Integer, ItemInfo> items, Map<Integer, String> locked,
        Weights w) {
        return assign(chests, items, locked, w, new HashMap<String, String>(), Relations::score, null);
    }

    /**
     * Groups fine categories into buckets with {@code tax} first (sized to the chests that exist), then assigns
     * chests to buckets. Every item gets a bucket; related buckets sit near each other.
     */
    public static Result assign(List<ChestModel> chests, Map<Integer, ItemInfo> items, Map<Integer, String> locked,
        Weights w, Taxonomy tax, int minSplitStacks) {
        return assign(chests, items, locked, w, tax, minSplitStacks, 0);
    }

    /**
     * @param keepFree fraction of each chest to leave free when deciding how many buckets the chests can hold (room to
     *                 grow): 0.15 sizes buckets as if chests were 15% smaller
     */
    public static Result assign(List<ChestModel> chests, Map<Integer, ItemInfo> items, Map<Integer, String> locked,
        Weights w, Taxonomy tax, int minSplitStacks, double keepFree) {
        Map<String, Integer> fine = demand(chests, items, new HashMap<String, String>());
        int slots = 0;
        for (ChestModel m : chests) slots += m.size();
        double avg = (chests.isEmpty() ? 27 : (double) slots / chests.size())
            * (1 - Math.max(0, Math.min(0.9, keepFree)));
        Taxonomy.Buckets bk = tax.bucket(fine, chests.size() - locked.size(), avg, minSplitStacks);
        Map<String, String> pre = new TreeMap<>();
        for (Map.Entry<String, String> e : bk.bucketOf.entrySet()) {
            if (!e.getKey()
                .equals(e.getValue())) pre.put(e.getKey(), e.getValue());
        }
        return assign(chests, items, locked, w, pre, tax::relation, bk);
    }

    /** Stacks per category (after {@code pre}), packing each item's stacks full. */
    static Map<String, Integer> demand(List<ChestModel> chests, Map<Integer, ItemInfo> items, Map<String, String> pre) {
        Map<Integer, Long> totals = PlanChecker.totals(chests);
        Map<String, Integer> demand = new TreeMap<>();
        for (Map.Entry<Integer, Long> e : totals.entrySet()) {
            ItemInfo info = info(items, e.getKey());
            int limit = Math.max(1, Math.min(info.maxStack, 64));
            demand.merge(effectiveOf(info.category, pre), (int) ((e.getValue() + limit - 1) / limit), Integer::sum);
        }
        return demand;
    }

    private static Result assign(List<ChestModel> chests, Map<Integer, ItemInfo> items, Map<Integer, String> locked,
        Weights w, Map<String, String> pre, Relatedness relation, Taxonomy.Buckets bk) {
        // ---- 1. demand per category, in slots, after packing stacks of each item full ----
        Map<String, Integer> demand = demand(chests, items, pre);
        for (String forced : locked.values()) demand.putIfAbsent(forced, 0);

        // ---- 2. merge the smallest categories until they fit the number of chests ----
        Map<String, String> merged = new TreeMap<>(pre);
        List<String> unassigned = new ArrayList<>();
        Map<String, Integer> active = new TreeMap<>(demand);
        while (active.size() > chests.size()) {
            String small = null;
            for (Map.Entry<String, Integer> e : active.entrySet()) {
                if (locked.containsValue(e.getKey())) continue;
                if (small == null || e.getValue() < active.get(small)) small = e.getKey();
            }
            if (small == null) break;
            String parent = null;
            double best = 0;
            for (String t : active.keySet()) {
                if (t.equals(small)) continue;
                double r = relation.score(small, t);
                if (r > best || (r == best && r > 0 && active.get(t) > active.get(parent))) {
                    best = r;
                    parent = t;
                }
            }
            int d = active.remove(small);
            if (parent != null) {
                merged.put(small, parent);
                active.put(parent, active.get(parent) + d);
            } else {
                unassigned.add(small);
            }
        }
        // Resolve chains (a → b, then b → c means a → c).
        for (String k : new ArrayList<>(merged.keySet())) {
            String t = merged.get(k);
            for (int guard = 0; merged.containsKey(t) && guard < 100; guard++) t = merged.get(t);
            merged.put(k, t);
        }
        for (String u : new ArrayList<>(unassigned)) { // categories merged into something later unassigned
            for (Map.Entry<String, String> e : new ArrayList<>(merged.entrySet())) {
                if (e.getValue()
                    .equals(u)) {
                    merged.remove(e.getKey());
                    unassigned.add(e.getKey());
                }
            }
        }
        Collections.sort(unassigned);

        // ---- 3. problem in arrays ----
        List<String> cats = new ArrayList<>(active.keySet());
        Map<String, Integer> catIndex = new HashMap<>();
        for (int i = 0; i < cats.size(); i++) catIndex.put(cats.get(i), i);
        int n = chests.size(), k = cats.size();
        int[] slots = new int[n];
        boolean[] fixed = new boolean[n];
        double[][] dist = new double[n][n];
        // mismatch[c][j] = stacks in chest c that would have to move if c had category j (j == k: no category)
        int[][] mismatch = new int[n][k + 1];
        int[] start = new int[n];
        for (int c = 0; c < n; c++) {
            ChestModel m = chests.get(c);
            slots[c] = m.size();
            for (int d = 0; d < n; d++) dist[c][d] = Planner.dist(m.pos, chests.get(d).pos);
            int[] count = new int[k];
            int movable = 0;
            for (int s = 0; s < m.size(); s++) {
                if (m.keys[s] == ChestModel.EMPTY) continue;
                String eff = effectiveOf(info(items, m.keys[s]).category, merged);
                Integer j = catIndex.get(eff);
                if (j == null) {
                    slots[c]--; // unassigned: never moves, so this slot is not room for anything
                    continue;
                }
                count[j]++;
                movable++;
            }
            for (int j = 0; j < k; j++) mismatch[c][j] = movable - count[j];
            mismatch[c][k] = movable;
            // Starting point: what it mostly holds (ties: alphabetical), or nothing if empty.
            int best = k;
            for (int j = 0; j < k; j++) if (count[j] > 0 && (best == k || count[j] > count[best])) best = j;
            start[c] = best;
            if (locked.containsKey(m.id)) {
                start[c] = catIndex.get(locked.get(m.id));
                fixed[c] = true;
            }
        }
        int[] dem = new int[k];
        for (int j = 0; j < k; j++) dem[j] = active.get(cats.get(j));
        double[][] rel = new double[k][k];
        for (int a = 0; a < k; a++)
            for (int b = 0; b < k; b++) if (a != b) rel[a][b] = relation.score(cats.get(a), cats.get(b));

        // Categories without a chest (biggest first) take the unused chest that gives the lowest cost.
        Cost cost = new Cost(n, k, slots, dist, mismatch, dem, rel, w);
        int[] cur = start.clone();
        List<Integer> order = new ArrayList<>();
        for (int j = 0; j < k; j++) order.add(j);
        order.sort(
            (a, b) -> dem[b] != dem[a] ? dem[b] - dem[a]
                : cats.get(a)
                    .compareTo(cats.get(b)));
        for (int j : order) {
            boolean has = false;
            for (int c = 0; c < n && !has; c++) has = cur[c] == j;
            if (has) continue;
            int bestC = -1;
            double bestV = Double.MAX_VALUE;
            for (int c = 0; c < n; c++) {
                if (fixed[c] || cur[c] != k) continue;
                cur[c] = j;
                double v = cost.of(cur);
                cur[c] = k;
                if (v < bestV - 1e-9) {
                    bestV = v;
                    bestC = c;
                }
            }
            if (bestC >= 0) cur[bestC] = j;
        }

        // ---- 4. improve: single-chest changes and swaps while the cost drops ----
        double now = cost.of(cur);
        int evals = 0;
        for (int pass = 0; pass < MAX_PASSES && evals < MAX_EVALS; pass++) {
            boolean improved = false;
            for (int c = 0; c < n; c++) {
                if (fixed[c]) continue;
                int keep = cur[c], bestJ = keep;
                double bestCost = now;
                for (int j = 0; j <= k; j++) {
                    if (j == keep) continue;
                    cur[c] = j;
                    double v = cost.of(cur);
                    evals++;
                    if (v < bestCost - 1e-9) {
                        bestCost = v;
                        bestJ = j;
                    }
                }
                cur[c] = bestJ;
                if (bestJ != keep) {
                    now = bestCost;
                    improved = true;
                }
            }
            // Swaps: take the best one this pass (first-improvement gets stuck one swap short of better layouts).
            int bestA = -1, bestB = -1;
            double bestSwap = now;
            for (int a = 0; a < n; a++) {
                for (int b = a + 1; b < n; b++) {
                    if (fixed[a] || fixed[b] || cur[a] == cur[b]) continue;
                    int t = cur[a];
                    cur[a] = cur[b];
                    cur[b] = t;
                    double v = cost.of(cur);
                    evals++;
                    cur[b] = cur[a];
                    cur[a] = t;
                    if (v < bestSwap - 1e-9) {
                        bestSwap = v;
                        bestA = a;
                        bestB = b;
                    }
                }
            }
            if (bestA >= 0) {
                int t = cur[bestA];
                cur[bestA] = cur[bestB];
                cur[bestB] = t;
                now = bestSwap;
                improved = true;
            }
            if (!improved) break;
        }

        // A category that still has no chest after all this has nowhere to go: its items stay put.
        Map<Integer, String> result = new LinkedHashMap<>();
        boolean[] owned = new boolean[k];
        for (int c = 0; c < n; c++) {
            if (cur[c] < k) {
                result.put(chests.get(c).id, cats.get(cur[c]));
                owned[cur[c]] = true;
            }
        }
        for (int j = 0; j < k; j++) {
            if (!owned[j]) {
                unassigned.add(cats.get(j));
                for (Map.Entry<String, String> e : new ArrayList<>(merged.entrySet())) {
                    if (e.getValue()
                        .equals(cats.get(j))) {
                        merged.remove(e.getKey());
                        unassigned.add(e.getKey());
                    }
                }
            }
        }
        Collections.sort(unassigned);
        return new Result(result, unassigned, merged, cost.report(cur), bk);
    }

    private static String effectiveOf(String cat, Map<String, String> merged) {
        String m = merged.get(cat);
        return m != null ? m : cat;
    }

    /** The weighted cost of one assignment (cur[c] = category index, k = none). */
    private static final class Cost {

        final int n, k;
        final int[] slots, dem;
        final double[][] dist, rel;
        final int[][] mismatch;
        final Weights w;
        double moves, missing, apart, unrelated;

        Cost(int n, int k, int[] slots, double[][] dist, int[][] mismatch, int[] dem, double[][] rel, Weights w) {
            this.n = n;
            this.k = k;
            this.slots = slots;
            this.dist = dist;
            this.mismatch = mismatch;
            this.dem = dem;
            this.rel = rel;
            this.w = w;
        }

        double of(int[] cur) {
            moves = 0;
            missing = 0;
            apart = 0;
            unrelated = 0;
            int[] cap = new int[k];
            for (int c = 0; c < n; c++) {
                moves += mismatch[c][cur[c]];
                if (cur[c] < k) cap[cur[c]] += slots[c];
            }
            for (int j = 0; j < k; j++) missing += Math.max(0, dem[j] - cap[j]);
            // Together: each chest's distance to the nearest other chest of its category (adjacent = 0).
            for (int c = 0; c < n; c++) {
                if (cur[c] == k) continue;
                double near = Double.MAX_VALUE;
                for (int d = 0; d < n; d++) if (d != c && cur[d] == cur[c]) near = Math.min(near, dist[c][d]);
                if (near != Double.MAX_VALUE) apart += Math.max(0, near - 1);
            }
            // Related nearby: for each related pair of categories, the closest distance between their chests.
            double[][] closest = new double[k][k];
            for (double[] row : closest) java.util.Arrays.fill(row, Double.MAX_VALUE);
            for (int c = 0; c < n; c++) {
                if (cur[c] == k) continue;
                for (int d = 0; d < n; d++) {
                    if (cur[d] == k || cur[d] == cur[c]) continue;
                    if (dist[c][d] < closest[cur[c]][cur[d]]) closest[cur[c]][cur[d]] = dist[c][d];
                }
            }
            for (int a = 0; a < k; a++) {
                for (int b = a + 1; b < k; b++) {
                    if (rel[a][b] > 0 && closest[a][b] != Double.MAX_VALUE) {
                        unrelated += rel[a][b] * Math.max(0, closest[a][b] - 1);
                    }
                }
            }
            return w.moveWeight * moves + CAPACITY_WEIGHT * missing
                + w.layoutWeight * (apart + RELATED_WEIGHT * unrelated);
        }

        String report(int[] cur) {
            double total = of(cur);
            return String.format(
                java.util.Locale.ROOT,
                "cost %.1f = %d stacks to move x %.1f + %d slots short x %.1f + layout x %.1f (apart %.1f, related %.1f)",
                total,
                (int) moves,
                w.moveWeight,
                (int) missing,
                CAPACITY_WEIGHT,
                w.layoutWeight,
                apart,
                RELATED_WEIGHT * unrelated);
        }
    }

    static ItemInfo info(Map<Integer, ItemInfo> items, int key) {
        ItemInfo i = items.get(key);
        if (i == null) throw new IllegalArgumentException("No item info for key " + key);
        return i;
    }
}
