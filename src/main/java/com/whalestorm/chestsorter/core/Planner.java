package com.whalestorm.chestsorter.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Plans one route through the chests that puts every misplaced stack into a chest of its category, cut into trips that
 * start and end with nothing carried.
 *
 * The route is built greedily, the usual way for this kind of routing problem (pickup and delivery with a carry
 * limit, which contains the travelling-salesman problem, so an exact shortest route is out of reach): from where the
 * player is, go to the chest where the most work can be done for the least walking, where work is stacks to put in
 * plus stacks to take out. At each chest: put in what belongs there, take out what does not (as many as there are free
 * carry slots, the ones whose destination is nearest first), then put in again, because taking out may have made room.
 * Any chest can be both a pickup and a drop-off in one trip.
 *
 * Safety rules the tests check:
 * - A stack is taken only when a chest of its category has room promised for it (room is promised at pickup, so two
 * stacks never count on the same space), or, when nothing else can move, when its category's chest is full but holds
 * misplaced stacks that can be taken out first (a swap). A trip whose swap does not work out is dropped.
 * - Stacks move whole; nothing is dropped, split, or sent to a chest of another category.
 * - Items whose category has no chest, and items already in a chest of their category, are never touched.
 * - Never more stacks carried than carry slots; every trip ends empty-handed.
 */
public final class Planner {

    private Planner() {}

    /** Walking (blocks) one stack of work is worth when choosing the next chest. */
    static final double WORK_VALUE = 0.75;
    /**
     * After this many visits (or twice the carry slots, if more) a trip stops taking new stacks and only delivers, so
     * every trip ends empty-handed and a problem mid-route only affects a short stretch.
     */
    static final int MIN_TAKING_VISITS = 8;

    public static Plan plan(List<ChestModel> chests, Map<Integer, ItemInfo> items, Assigner.Result assignment,
        int carrySlots, int maxTrips) {
        return plan(chests, items, assignment, carrySlots, maxTrips, chests.isEmpty() ? null : chests.get(0).pos);
    }

    /** @param start where the player stands when the route begins (null: the first chest) */
    public static Plan plan(List<ChestModel> chests, Map<Integer, ItemInfo> items, Assigner.Result assignment,
        int carrySlots, int maxTrips, BlockPos start) {
        if (carrySlots < 1) throw new IllegalArgumentException("carrySlots must be >= 1");
        Map<String, List<Integer>> chestsOf = new TreeMap<>();
        for (Map.Entry<Integer, String> e : assignment.chestCategory.entrySet()) {
            chestsOf.computeIfAbsent(e.getValue(), k -> new ArrayList<Integer>())
                .add(e.getKey());
        }
        Ctx ctx = new Ctx(chests, items, assignment, chestsOf, carrySlots);
        State state = new State(chests);
        BlockPos at = start != null ? start : (chests.isEmpty() ? new BlockPos(0, 0, 0) : chests.get(0).pos);
        List<Plan.Trip> trips = new ArrayList<>();
        while (trips.size() < maxTrips) {
            State work = state.fork();
            Plan.Trip trip = buildTrip(ctx, work, at, false);
            if (trip == null) {
                work = state.fork();
                trip = buildTrip(ctx, work, at, true); // only when nothing else can move: try swaps
            }
            if (trip == null) break;
            work.commit();
            trips.add(trip);
            at = ctx.pos.get(trip.end());
        }
        int leftNoRoom = 0;
        Map<Integer, ChestModel> end = new LinkedHashMap<>();
        for (ChestModel c : chests) {
            ChestModel m = state.peekSim(c.id);
            end.put(c.id, m);
            for (int s = 0; s < m.size(); s++) if (isMisplaced(m, s, items, assignment, chestsOf)) leftNoRoom++;
        }
        return new Plan(trips, assignment.chestCategory, assignment.unassigned, assignment.merged, leftNoRoom, end);
    }

    /** Fixed facts for one planning run. */
    private static final class Ctx {

        final List<ChestModel> chests;
        final Map<Integer, ItemInfo> items;
        final Assigner.Result a;
        final Map<String, List<Integer>> chestsOf;
        final int carry;
        final Map<Integer, BlockPos> pos = new HashMap<>();

        Ctx(List<ChestModel> chests, Map<Integer, ItemInfo> items, Assigner.Result a,
            Map<String, List<Integer>> chestsOf, int carry) {
            this.chests = chests;
            this.items = items;
            this.a = a;
            this.chestsOf = chestsOf;
            this.carry = carry;
            for (ChestModel c : chests) pos.put(c.id, c.pos);
        }

        List<Integer> owners(int key) {
            return chestsOf.get(a.effective(Assigner.info(items, key).category));
        }

        int maxStack(int key) {
            return Assigner.info(items, key).maxStack;
        }
    }

    /**
     * Chest contents now ({@code sim}) and if every carried stack reaches the chest promised to it ({@code promised}).
     * A fork copies chests on first write and writes them back on commit.
     */
    private static final class State {

        final State parent;
        final Map<Integer, ChestModel> sim = new HashMap<>(), promised = new HashMap<>();

        State(List<ChestModel> chests) {
            parent = null;
            for (ChestModel c : chests) {
                sim.put(c.id, c.copy());
                promised.put(c.id, c.copy());
            }
        }

        private State(State parent) {
            this.parent = parent;
        }

        State fork() {
            return new State(this);
        }

        /** For writing (copied into this fork first). */
        ChestModel sim(int id) {
            ChestModel m = sim.get(id);
            if (m == null) {
                m = parent.peekSim(id)
                    .copy();
                sim.put(id, m);
            }
            return m;
        }

        ChestModel promised(int id) {
            ChestModel m = promised.get(id);
            if (m == null) {
                m = parent.peekPromised(id)
                    .copy();
                promised.put(id, m);
            }
            return m;
        }

        /** For reading only. */
        ChestModel peekSim(int id) {
            ChestModel m = sim.get(id);
            return m != null ? m : parent.peekSim(id);
        }

        ChestModel peekPromised(int id) {
            ChestModel m = promised.get(id);
            return m != null ? m : parent.peekPromised(id);
        }

        void commit() {
            parent.sim.putAll(sim);
            parent.promised.putAll(promised);
        }
    }

    private static final class Carried {

        final int pick, key, count, target;
        final boolean swap;

        Carried(int pick, int key, int count, int target, boolean swap) {
            this.pick = pick;
            this.key = key;
            this.count = count;
            this.target = target;
            this.swap = swap;
        }
    }

    /** Builds one trip on {@code w}; null if nothing can move (or, with swaps, if a swap did not work out). */
    private static Plan.Trip buildTrip(Ctx ctx, State w, BlockPos from, boolean allowSwaps) {
        List<Plan.Pick> picks = new ArrayList<>();
        List<Plan.Visit> visits = new ArrayList<>();
        List<Carried> carried = new ArrayList<>();
        Set<Integer> idle = new HashSet<>(); // chests where nothing could be done this trip
        BlockPos at = from;
        int takingVisits = Math.max(MIN_TAKING_VISITS, 2 * ctx.carry);
        int guard = takingVisits + 2 * ctx.carry + ctx.chests.size() + 20;
        while (guard-- > 0) {
            boolean taking = visits.size() < takingVisits;
            int free = taking ? ctx.carry - carried.size() : 0;
            // ---- next chest: most work for the least walking ----
            ChestModel best = null;
            double bestScore = Double.MAX_VALUE;
            for (ChestModel c : ctx.chests) {
                if (idle.contains(c.id)) continue;
                int put = 0;
                for (Carried k : carried) if (k.target == c.id) put++;
                int take = taking && (free + put) > 0 ? Math.min(free + put, takeable(ctx, w, c.id, allowSwaps)) : 0;
                if (put + take == 0) continue;
                double score = dist(at, c.pos) - WORK_VALUE * (put + take);
                if (score < bestScore) {
                    bestScore = score;
                    best = c;
                }
            }
            if (best == null) break;
            List<Plan.Op> ops = new ArrayList<>();
            int id = best.id;
            // ---- 1. put in what was promised here ----
            for (Iterator<Carried> it = carried.iterator(); it.hasNext();) {
                Carried k = it.next();
                if (k.target != id || k.swap) continue;
                int left = w.sim(id)
                    .place(k.key, k.count, ctx.maxStack(k.key));
                if (left != 0) throw new IllegalStateException("promised room missing in chest " + id);
                ops.add(new Plan.Op(true, k.pick));
                it.remove();
            }
            // ---- 2. take out misplaced stacks, nearest destination first ----
            ChestModel here = w.peekSim(id);
            List<double[]> options = new ArrayList<>(); // slot, distance to destination
            for (int s = 0; s < here.size(); s++) {
                if (!isMisplaced(here, s, ctx.items, ctx.a, ctx.chestsOf)) continue;
                int[] t = destination(ctx, w, id, here.keys[s], here.counts[s], allowSwaps);
                if (t != null) options.add(new double[] { s, dist(best.pos, ctx.pos.get(t[0])) });
            }
            options.sort((p, q) -> p[1] != q[1] ? Double.compare(p[1], q[1]) : Double.compare(p[0], q[0]));
            for (double[] o : options) {
                if (!taking || carried.size() >= ctx.carry) break;
                int slot = (int) o[0];
                ChestModel live = w.peekSim(id);
                int key = live.keys[slot], count = live.counts[slot];
                // Re-check: an earlier pick at this chest may have taken the room it counted on.
                int[] t = destination(ctx, w, id, key, count, allowSwaps);
                if (t == null) continue;
                w.sim(id)
                    .set(slot, ChestModel.EMPTY, 0);
                w.promised(id)
                    .set(slot, ChestModel.EMPTY, 0);
                boolean swap = t[1] == 1;
                if (!swap) w.promised(t[0])
                    .place(key, count, ctx.maxStack(key));
                int pi = picks.size();
                picks.add(new Plan.Pick(id, slot, key, count));
                carried.add(new Carried(pi, key, count, t[0], swap));
                ops.add(new Plan.Op(false, pi));
            }
            // ---- 3. put in again: taking out may have made room (swaps) ----
            for (Iterator<Carried> it = carried.iterator(); it.hasNext();) {
                Carried k = it.next();
                if (k.target != id) continue;
                int max = ctx.maxStack(k.key);
                if (w.peekSim(id)
                    .roomFor(k.key, max) < k.count
                    || (k.swap && w.peekPromised(id)
                        .roomFor(k.key, max) < k.count))
                    continue;
                w.sim(id)
                    .place(k.key, k.count, max);
                if (k.swap) w.promised(id)
                    .place(k.key, k.count, max);
                ops.add(new Plan.Op(true, k.pick));
                it.remove();
            }
            if (ops.isEmpty()) {
                idle.add(id);
                continue;
            }
            visits.add(new Plan.Visit(id, ops));
            at = best.pos;
            if (carried.isEmpty()) break;
        }
        if (!carried.isEmpty() || picks.isEmpty()) return null; // stuck (a swap that did not work out) or no work
        return new Plan.Trip(picks, visits);
    }

    /** How many misplaced stacks in chest {@code id} could be taken out now. */
    private static int takeable(Ctx ctx, State w, int id, boolean allowSwaps) {
        ChestModel c = w.peekSim(id);
        int n = 0;
        for (int s = 0; s < c.size(); s++) {
            if (!isMisplaced(c, s, ctx.items, ctx.a, ctx.chestsOf)) continue;
            if (destination(ctx, w, id, c.keys[s], c.counts[s], allowSwaps) != null) n++;
        }
        return n;
    }

    /**
     * Where a misplaced stack in chest {@code from} can go: {target, 0} for the nearest chest of its category with
     * promised room, else (swaps allowed) {target, 1} for the nearest one holding a misplaced stack that can be taken
     * out to make room. Null: nowhere.
     */
    private static int[] destination(Ctx ctx, State w, int from, int key, int count, boolean allowSwaps) {
        List<Integer> owners = ctx.owners(key);
        if (owners == null) return null;
        int max = ctx.maxStack(key);
        BlockPos fp = ctx.pos.get(from);
        int best = -1;
        double bd = Double.MAX_VALUE;
        for (int t : owners) {
            if (t == from || w.peekPromised(t)
                .roomFor(key, max) < count) continue;
            double d = dist(fp, ctx.pos.get(t));
            if (d < bd) {
                bd = d;
                best = t;
            }
        }
        if (best >= 0) return new int[] { best, 0 };
        if (!allowSwaps) return null;
        for (int t : owners) {
            if (t == from) continue;
            ChestModel tm = w.peekSim(t);
            boolean hasMisplaced = false;
            for (int s = 0; s < tm.size() && !hasMisplaced; s++) {
                hasMisplaced = isMisplaced(tm, s, ctx.items, ctx.a, ctx.chestsOf);
            }
            if (!hasMisplaced) continue;
            double d = dist(fp, ctx.pos.get(t));
            if (d < bd) {
                bd = d;
                best = t;
            }
        }
        return best >= 0 ? new int[] { best, 1 } : null;
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
