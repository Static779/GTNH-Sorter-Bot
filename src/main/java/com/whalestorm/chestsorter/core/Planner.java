package com.whalestorm.chestsorter.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Turns surveyed chests plus an assignment into trips.
 *
 * Safety rules the tests check:
 * - A stack is moved only if its category has a chest and that chest has room for the WHOLE stack. Otherwise it
 * stays where it is. Nothing is ever dropped, split across chests, or moved to a chest of another category.
 * - Items whose category has no chest, and items already in a chest of their category, are never touched.
 * - Stacks are moved whole. A trip picks at most {@code carrySlots} stacks, one per empty inventory slot.
 * - Trips are simulated in order, so slots freed by an earlier trip can be used by a later one, exactly as they
 * will be during execution.
 */
public final class Planner {

    private Planner() {}

    public static Plan plan(List<ChestModel> chests, Map<Integer, ItemInfo> items, Assigner.Result assignment,
        int carrySlots, int maxTrips) {
        if (carrySlots < 1) throw new IllegalArgumentException("carrySlots must be >= 1");

        Map<Integer, ChestModel> sim = new LinkedHashMap<>();
        for (ChestModel c : chests) sim.put(c.id, c.copy());

        Map<String, List<Integer>> chestsOf = new TreeMap<>();
        for (Map.Entry<Integer, String> e : assignment.chestCategory.entrySet()) {
            chestsOf.computeIfAbsent(e.getValue(), k -> new ArrayList<Integer>())
                .add(e.getKey());
        }

        List<Plan.Trip> trips = new ArrayList<>();
        boolean progress = true;
        while (progress && trips.size() < maxTrips) {
            progress = false;
            for (ChestModel original : chests) {
                if (trips.size() >= maxTrips) break;
                Plan.Trip trip = planTrip(sim.get(original.id), sim, items, assignment, chestsOf, carrySlots);
                if (trip != null) {
                    trips.add(trip);
                    progress = true;
                }
            }
        }

        int leftNoRoom = 0;
        for (ChestModel c : sim.values()) {
            for (int s = 0; s < c.size(); s++) {
                if (isMisplaced(c, s, items, assignment, chestsOf)) leftNoRoom++;
            }
        }
        return new Plan(trips, assignment.chestCategory, assignment.unassigned, assignment.merged, leftNoRoom, sim);
    }

    /** Builds one trip from {@code source}, committing its effects to {@code sim}. Null if nothing can move. */
    private static Plan.Trip planTrip(ChestModel source, Map<Integer, ChestModel> sim, Map<Integer, ItemInfo> items,
        Assigner.Result assignment, Map<String, List<Integer>> chestsOf, int carrySlots) {
        Map<Integer, ChestModel> scratch = new HashMap<>();
        ChestModel src = source.copy();
        List<Plan.Pick> picks = new ArrayList<>();
        Map<Integer, List<Integer>> byTarget = new LinkedHashMap<>();

        for (int s = 0; s < src.size() && picks.size() < carrySlots; s++) {
            if (!isMisplaced(src, s, items, assignment, chestsOf)) continue;
            int key = src.keys[s];
            int count = src.counts[s];
            ItemInfo info = Assigner.info(items, key);

            Integer target = null;
            double bestDist = Double.MAX_VALUE;
            boolean bestAlreadyVisited = false;
            for (int t : chestsOf.get(assignment.effective(info.category))) {
                if (t == src.id) continue;
                ChestModel tm = scratch.containsKey(t) ? scratch.get(t) : sim.get(t);
                if (tm.roomFor(key, info.maxStack) < count) continue;
                boolean visited = byTarget.containsKey(t);
                double d = dist(src.pos, tm.pos);
                // Prefer a chest this trip already visits (fewer stops), then the nearest.
                if (target == null || (visited && !bestAlreadyVisited)
                    || (visited == bestAlreadyVisited && d < bestDist)) {
                    target = t;
                    bestDist = d;
                    bestAlreadyVisited = visited;
                }
            }
            if (target == null) continue;

            ChestModel tm = scratch.computeIfAbsent(
                target,
                k -> sim.get(k)
                    .copy());
            int left = tm.place(key, count, info.maxStack);
            if (left != 0) throw new IllegalStateException("roomFor/place disagree for key " + key);
            src.set(s, ChestModel.EMPTY, 0);
            byTarget.computeIfAbsent(target, k -> new ArrayList<Integer>())
                .add(picks.size());
            picks.add(new Plan.Pick(s, key, count));
        }
        if (picks.isEmpty()) return null;

        // Commit.
        sim.put(src.id, src);
        sim.putAll(scratch);

        // Visit targets nearest-neighbour from the source.
        List<Plan.Deposit> deposits = new ArrayList<>();
        List<Integer> remaining = new ArrayList<>(byTarget.keySet());
        BlockPos at = src.pos;
        while (!remaining.isEmpty()) {
            int bestI = 0;
            for (int i = 1; i < remaining.size(); i++) {
                if (dist(at, sim.get(remaining.get(i)).pos) < dist(at, sim.get(remaining.get(bestI)).pos)) bestI = i;
            }
            int t = remaining.remove(bestI);
            deposits.add(new Plan.Deposit(t, byTarget.get(t)));
            at = sim.get(t).pos;
        }
        return new Plan.Trip(src.id, picks, deposits);
    }

    /**
     * True if the stack in this slot belongs (after merging) to a category that has chests, and this chest is not one
     * of them.
     */
    public static boolean isMisplaced(ChestModel c, int slot, Map<Integer, ItemInfo> items, Assigner.Result assignment,
        Map<String, List<Integer>> chestsOf) {
        int key = c.keys[slot];
        if (key == ChestModel.EMPTY) return false;
        String cat = assignment.effective(Assigner.info(items, key).category);
        if (cat.equals(assignment.chestCategory.get(c.id))) return false;
        List<Integer> owners = chestsOf.get(cat);
        return owners != null && !owners.isEmpty();
    }

    static double dist(BlockPos a, BlockPos b) {
        double dx = a.x - b.x, dy = a.y - b.y, dz = a.z - b.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
