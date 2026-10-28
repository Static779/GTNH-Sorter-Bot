package com.whalestorm.chestsorter.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * "What would N more chests do?" Plans the same items again with N empty chests added and compares the results, so
 * the player can see whether more chests are worth placing. Nothing here touches the world.
 *
 * The extra chests are imaginary: the size of your most common chest, placed one at a time on the free spot next to
 * an existing chest that is closest to the middle of the room (beside or on top). Walls and floors are not checked, so
 * walking figures for them are estimates.
 */
public final class WhatIf {

    private WhatIf() {}

    /** Ids for imaginary chests start here (real chests use scan indexes). */
    public static final int FIRST_EXTRA_ID = 1_000_000;
    /** A group is "nearly full" below this share of free slots after sorting. */
    public static final double TIGHT_FREE = 0.10;
    /** Time model from trace 012605: ticks per block walked beyond reach, to open a chest, to check it. */
    public static final double WALK_TICKS_PER_BLOCK = 4.2, REACH = 3.0;
    public static final int OPEN_TICKS = 5, CHECK_TICKS = 5;

    /** Planning settings, the same ones a real run uses. */
    public static final class Settings {

        public final int carry, minSplit, maxTrips;
        public final double layoutWeight, keepFree;
        public final Taxonomy tax;

        public Settings(int carry, double layoutWeight, int minSplit, double keepFree, Taxonomy tax, int maxTrips) {
            this.carry = carry;
            this.layoutWeight = layoutWeight;
            this.minSplit = minSplit;
            this.keepFree = keepFree;
            this.tax = tax;
            this.maxTrips = maxTrips;
        }
    }

    /** The outcome of sorting with {@link #extra} more chests. */
    public static final class Row {

        public final int extra, chests;
        /** Chests holding a group, and distinct groups. */
        public final int chestsUsed, groups;
        /** Stacks that stay where they are: their group's chests are full, or their group got no chest. */
        public final int leftOver;
        /** Groups under {@link #TIGHT_FREE} free after sorting. */
        public final int nearlyFull;
        /** Share of stacks that end up in the catch-all "misc" chests (0-1). */
        public final double miscShare;
        /** Average number of different item categories per chest after sorting (lower = finer sorting). */
        public final double categoriesPerChest;
        /** Free slots after sorting, over all chests. */
        public final int freeSlots;
        public final int moves, trips;
        /** Blocks walked (straight lines between chests) and estimated minutes to carry everything. */
        public final double walk, minutes;

        Row(int extra, int chests, int chestsUsed, int groups, int leftOver, int nearlyFull, double miscShare,
            double categoriesPerChest, int freeSlots, int moves, int trips, double walk, double minutes) {
            this.extra = extra;
            this.chests = chests;
            this.chestsUsed = chestsUsed;
            this.groups = groups;
            this.leftOver = leftOver;
            this.nearlyFull = nearlyFull;
            this.miscShare = miscShare;
            this.categoriesPerChest = categoriesPerChest;
            this.freeSlots = freeSlots;
            this.moves = moves;
            this.trips = trips;
            this.walk = walk;
            this.minutes = minutes;
        }

        /** Nothing left over and every group has room to grow. */
        public boolean enough() {
            return leftOver == 0 && nearlyFull == 0;
        }

        public String line() {
            return String.format(
                Locale.ROOT,
                "%s: %d groups using %d of %d chests, %d stacks left over, %d nearly full, misc %.0f%%, %.1f"
                    + " categories per chest, %d free slots, %d moves, ~%.0f min",
                extra == 0 ? "now" : "+" + extra,
                groups,
                chestsUsed,
                chests,
                leftOver,
                nearlyFull,
                100 * miscShare,
                categoriesPerChest,
                freeSlots,
                moves,
                minutes);
        }
    }

    /**
     * Rows for 0 extra chests and each of {@code extras}. The chests are not changed. Throws nothing for an empty
     * room; returns an empty list.
     */
    public static List<Row> evaluate(List<ChestModel> chests, Map<Integer, ItemInfo> items, Settings s, BlockPos start,
        int[] extras) {
        List<Row> rows = new ArrayList<>();
        if (chests.isEmpty()) return rows;
        rows.add(measure(chests, items, s, start, 0));
        for (int n : extras) if (n > 0) rows.add(measure(chests, items, s, start, n));
        return rows;
    }

    /** Plans with {@code extra} imaginary empty chests added and measures the result. */
    public static Row measure(List<ChestModel> chests, Map<Integer, ItemInfo> items, Settings s, BlockPos start,
        int extra) {
        List<ChestModel> room = new ArrayList<>();
        for (ChestModel c : chests) room.add(c.copy());
        room.addAll(extraChests(chests, extra));
        Assigner.Result a = Assigner.assign(
            room,
            items,
            new HashMap<Integer, String>(),
            new Assigner.Weights(1.0, s.layoutWeight),
            s.tax,
            s.minSplit,
            s.keepFree);
        Plan plan = Planner.plan(room, items, a, s.carry, s.maxTrips, start);
        return measure(room, items, a, plan, start, extra);
    }

    /** Measures a finished plan ({@code room} as it was before the plan). */
    public static Row measure(List<ChestModel> room, Map<Integer, ItemInfo> items, Assigner.Result a, Plan plan,
        BlockPos start, int extra) {
        int after = 0, misc = 0, unassigned = 0, free = 0, catChests = 0;
        double catSum = 0;
        Map<String, int[]> perGroup = new TreeMap<>(); // group -> {slots, used}
        for (ChestModel before : room) {
            ChestModel c = plan.expected.get(before.id);
            if (c == null) c = before;
            String own = a.chestCategory.get(c.id);
            Set<String> cats = new HashSet<>();
            int used = 0;
            for (int i = 0; i < c.size(); i++) {
                if (c.keys[i] == ChestModel.EMPTY) continue;
                used++;
                after++;
                ItemInfo info = items.get(c.keys[i]);
                String cat = info == null ? Taxonomy.MISC : info.category;
                cats.add(cat);
                if (Taxonomy.MISC.equals(a.effective(cat))) misc++;
                if (a.unassigned.contains(cat)) unassigned++;
            }
            free += c.size() - used;
            if (!cats.isEmpty()) {
                catSum += cats.size();
                catChests++;
            }
            if (own != null) {
                int[] g = perGroup.computeIfAbsent(own, k -> new int[2]);
                g[0] += c.size();
                g[1] += used;
            }
        }
        int tight = 0;
        for (int[] g : perGroup.values()) if (g[0] - g[1] < TIGHT_FREE * g[0]) tight++;

        Map<Integer, BlockPos> pos = new HashMap<>();
        for (ChestModel c : room) pos.put(c.id, c.pos);
        double walk = 0, ticks = 0;
        BlockPos at = start;
        int last = -1;
        for (Plan.Trip t : plan.trips) {
            for (Plan.Visit v : t.visits) {
                BlockPos p = pos.get(v.chest);
                double d = at == null ? 0 : Planner.dist(at, p);
                walk += d;
                ticks += WALK_TICKS_PER_BLOCK * Math.max(0, d - REACH) + v.ops.size() + CHECK_TICKS;
                if (v.chest != last) ticks += OPEN_TICKS;
                at = p;
                last = v.chest;
            }
        }
        return new Row(
            extra,
            room.size(),
            a.chestCategory.size(),
            perGroup.size(),
            plan.stacksLeftNoRoom + unassigned,
            tight,
            after == 0 ? 0 : (double) misc / after,
            catChests == 0 ? 0 : catSum / catChests,
            free,
            plan.stackMoves(),
            plan.trips.size(),
            walk,
            ticks / 1200.0);
    }

    /**
     * {@code n} empty chests the size of the most common chest, each on the free spot beside or on top of an existing
     * (or already added) chest that is nearest the middle of the room. Ties go to the lowest x, then y, then z.
     */
    public static List<ChestModel> extraChests(List<ChestModel> chests, int n) {
        List<ChestModel> out = new ArrayList<>();
        if (chests.isEmpty() || n <= 0) return out;
        Map<Long, Integer> sizes = new TreeMap<>();
        double cx = 0, cy = 0, cz = 0;
        int maxY = Integer.MIN_VALUE;
        Set<BlockPos> taken = new HashSet<>();
        for (ChestModel c : chests) {
            sizes.merge(((long) c.size() << 20) | c.slotLimit, 1, Integer::sum);
            cx += c.pos.x;
            cy += c.pos.y;
            cz += c.pos.z;
            maxY = Math.max(maxY, c.pos.y);
            taken.add(c.pos);
        }
        cx /= chests.size();
        cy /= chests.size();
        cz /= chests.size();
        long common = 0;
        int best = -1;
        for (Map.Entry<Long, Integer> e : sizes.entrySet()) {
            if (e.getValue() > best) {
                best = e.getValue();
                common = e.getKey();
            }
        }
        int size = (int) (common >> 20), limit = (int) (common & 0xFFFFF);
        List<BlockPos> anchors = new ArrayList<>(taken);
        int[][] offsets = { { 1, 0, 0 }, { -1, 0, 0 }, { 0, 0, 1 }, { 0, 0, -1 }, { 0, 1, 0 } };
        for (int k = 0; k < n; k++) {
            BlockPos pick = null;
            double pd = Double.MAX_VALUE;
            for (BlockPos p : anchors) {
                for (int[] o : offsets) {
                    BlockPos q = new BlockPos(p.x + o[0], p.y + o[1], p.z + o[2]);
                    if (q.y > maxY + 1 || taken.contains(q)) continue;
                    double dx = q.x - cx, dy = q.y - cy, dz = q.z - cz, d = dx * dx + dy * dy + dz * dz;
                    if (d < pd - 1e-9 || (Math.abs(d - pd) <= 1e-9 && before(q, pick))) {
                        pd = d;
                        pick = q;
                    }
                }
            }
            if (pick == null) break;
            taken.add(pick);
            anchors.add(pick);
            out.add(new ChestModel(FIRST_EXTRA_ID + k, pick, "extra chest", size, limit));
        }
        return out;
    }

    private static boolean before(BlockPos a, BlockPos b) {
        if (b == null) return true;
        if (a.x != b.x) return a.x < b.x;
        if (a.y != b.y) return a.y < b.y;
        return a.z < b.z;
    }

    /**
     * A short conclusion for the rows from {@link #evaluate}: when everything fits, when every group has room to grow,
     * and how much finer the sorting gets.
     */
    public static String verdict(List<Row> rows) {
        if (rows.isEmpty()) return "No chests to compare.";
        Row now = rows.get(0), last = rows.get(rows.size() - 1);
        StringBuilder v = new StringBuilder();
        // 1. stacks left over
        if (now.leftOver == 0) v.append("Everything fits now.");
        else {
            Row fits = first(rows, r -> r.leftOver == 0);
            if (fits != null) v.append(
                String.format(
                    Locale.ROOT,
                    "+%d chests fit everything (now %d stacks are left over).",
                    fits.extra,
                    now.leftOver));
            else v.append(
                String.format(
                    Locale.ROOT,
                    "%d stacks are left over now, %d even with +%d chests.",
                    now.leftOver,
                    last.leftOver,
                    last.extra));
        }
        // 2. room to grow
        String pct = String.format(Locale.ROOT, "%.0f%%", 100 * TIGHT_FREE);
        if (now.nearlyFull == 0) v.append(" Every group has ")
            .append(pct)
            .append(" room.");
        else {
            Row roomy = first(rows, r -> r.nearlyFull == 0);
            if (roomy != null) v.append(
                String.format(
                    Locale.ROOT,
                    " +%d gives every group %s room (now %d nearly full).",
                    roomy.extra,
                    pct,
                    now.nearlyFull));
            else v.append(
                String.format(
                    Locale.ROOT,
                    " Nearly full groups: %d now, %d with +%d (no count tried got it to 0).",
                    now.nearlyFull,
                    last.nearlyFull,
                    last.extra));
        }
        // 3. finer sorting
        if (rows.size() > 1) v.append(
            String.format(
                Locale.ROOT,
                " Sorting gets finer: %.1f categories per chest now, %.1f with +%d.",
                now.categoriesPerChest,
                last.categoriesPerChest,
                last.extra));
        else if (!now.enough()) v.append(" Set extra chests to compare.");
        return v.toString();
    }

    private static Row first(List<Row> rows, java.util.function.Predicate<Row> p) {
        for (Row r : rows) if (r.extra > 0 && p.test(r)) return r;
        return null;
    }

    /** Parses "6, 12, 24" into up to {@code max} distinct positive numbers no bigger than {@code cap}, sorted. */
    public static int[] parse(String s, int max, int cap) {
        Set<Integer> v = new java.util.TreeSet<>();
        if (s != null) {
            for (String part : s.split("[,;\\s]+")) {
                if (part.isEmpty()) continue;
                try {
                    int n = Integer.parseInt(part.trim());
                    if (n > 0 && n <= cap) v.add(n);
                } catch (NumberFormatException e) {
                    // ignored: not a number
                }
                if (v.size() >= max) break;
            }
        }
        int[] out = new int[v.size()];
        int i = 0;
        for (int n : v) out[i++] = n;
        return out;
    }
}
