import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

import com.whalestorm.chestsorter.core.Assigner;
import com.whalestorm.chestsorter.core.BlockPos;
import com.whalestorm.chestsorter.core.ChestModel;
import com.whalestorm.chestsorter.core.Combiner;
import com.whalestorm.chestsorter.core.ItemInfo;
import com.whalestorm.chestsorter.core.Plan;
import com.whalestorm.chestsorter.core.PlanChecker;
import com.whalestorm.chestsorter.core.Planner;
import com.whalestorm.chestsorter.core.Taxonomy;

/**
 * Theoretical runs on generated chest rooms, one setting varied at a time from a baseline, to find where the
 * grouping, layout and planning fall short. Items come from a real trace (category + max stack per item type).
 * Rooms are walls of double chests like the one in the traces. Prints one CSV row per scenario (averaged over seeds).
 *
 * Usage: tools/sweep.sh [items.tsv] [seeds]
 */
public class Sweep {

    static List<String[]> catalog = new ArrayList<>();

    /** One scenario's settings. */
    static final class P implements Cloneable {

        String varied = "baseline", value = "";
        int chests = 48, itemTypes = 400, carry = 13, minSplit = 6;
        double fill = 0.6, mess = 0.7, partial = 0.3, layout = 1.0, keepFree = 0.15;
        boolean mixedSizes = false;

        P with(String v, String val) {
            try {
                P p = (P) clone();
                p.varied = v;
                p.value = val;
                return p;
            } catch (CloneNotSupportedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        String file = args.length > 0 ? args[0] : "tools/sweep/items-012605.tsv";
        int seeds = args.length > 1 ? Integer.parseInt(args[1]) : 5;
        for (String l : Files.readAllLines(Paths.get(file), StandardCharsets.UTF_8)) {
            String[] f = l.split("\t");
            if (f.length == 2) catalog.add(f);
        }
        P base = new P();
        List<P> runs = new ArrayList<>();
        runs.add(base.with("baseline", "-"));
        for (int v : new int[] { 8, 16, 32, 48, 96 }) {
            P p = base.with("chests", "" + v);
            p.chests = v;
            runs.add(p);
        }
        for (double v : new double[] { 0.3, 0.5, 0.7, 0.85, 0.95 }) {
            P p = base.with("fill", "" + v);
            p.fill = v;
            runs.add(p);
        }
        for (int v : new int[] { 50, 150, 400, 880 }) {
            P p = base.with("itemTypes", "" + v);
            p.itemTypes = v;
            runs.add(p);
        }
        for (double v : new double[] { 0.1, 0.4, 0.7, 1.0 }) {
            P p = base.with("mess", "" + v);
            p.mess = v;
            runs.add(p);
        }
        for (double v : new double[] { 0.0, 0.3, 0.7 }) {
            P p = base.with("partialStacks", "" + v);
            p.partial = v;
            runs.add(p);
        }
        for (int v : new int[] { 3, 8, 13, 27 }) {
            P p = base.with("carrySlots", "" + v);
            p.carry = v;
            runs.add(p);
        }
        for (int v : new int[] { 2, 4, 6, 12, 24, 54 }) {
            P p = base.with("minStacksForOwnChest", "" + v);
            p.minSplit = v;
            runs.add(p);
        }
        for (double v : new double[] { 0, 0.5, 1, 3 }) {
            P p = base.with("layoutWeight", "" + v);
            p.layout = v;
            runs.add(p);
        }
        for (double v : new double[] { 0, 0.15, 0.3 }) {
            P p = base.with("keepFree", "" + v);
            p.keepFree = v;
            runs.add(p);
        }
        P mixed = base.with("chestSizes", "mixed 27/54/81");
        mixed.mixedSizes = true;
        runs.add(mixed);
        P tight = base.with("combo", "16 chests; fill 0.9");
        tight.chests = 16;
        tight.fill = 0.9;
        runs.add(tight);
        P bigRoom = base.with("combo", "96 chests; fill 0.3; 880 types");
        bigRoom.chests = 96;
        bigRoom.fill = 0.3;
        bigRoom.itemTypes = 880;
        runs.add(bigRoom);

        String[] cols = { "varied", "value", "stacks", "buckets", "chestsUsed", "miscShare", "stranded", "moves",
            "trips", "walkBlocks", "combineFreed", "scatterMax", "scatteredBuckets", "tightBuckets",
            "nearEmptyChests", "relatedDist", "planMs", "checkFails" };
        System.out.println(String.join(",", cols));
        for (P p : runs) {
            double[] sum = new double[cols.length - 2];
            for (int seed = 0; seed < seeds; seed++) {
                double[] r = run(p, seed);
                for (int i = 0; i < r.length; i++) sum[i] += r[i];
            }
            StringBuilder sb = new StringBuilder(p.varied + "," + p.value);
            for (double v : sum) sb.append(',')
                .append(String.format(Locale.ROOT, "%.2f", v / seeds));
            System.out.println(sb);
        }
    }

    /** One generated room. Returns the metrics in the column order above (after varied/value). */
    static double[] run(P p, int seed) {
        Random rnd = new Random(1000L * seed + p.chests * 7 + p.itemTypes * 13 + (long) (p.fill * 1000));
        // ---- chests: walls of 6 columns x 5 rows (x step 3, y 56..60), walls 5 blocks apart along z ----
        List<ChestModel> chests = new ArrayList<>();
        for (int i = 0; i < p.chests; i++) {
            int wall = i / 30, inWall = i % 30;
            int size = p.mixedSizes ? new int[] { 27, 54, 54, 81 }[rnd.nextInt(4)] : 54;
            chests.add(
                new ChestModel(i, new BlockPos((inWall % 6) * 3, 56 + inWall / 6, wall * 5), "c" + i, size, 64));
        }
        int slots = 0;
        for (ChestModel c : chests) slots += c.size();
        // ---- item types: sampled from the real catalogue; stacks per type heavy-tailed ----
        Map<Integer, ItemInfo> items = new HashMap<>();
        List<Integer> stacksOf = new ArrayList<>();
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < catalog.size(); i++) order.add(i);
        java.util.Collections.shuffle(order, rnd);
        for (int k = 0; k < p.itemTypes; k++) {
            String[] c = catalog.get(order.get(k % order.size()));
            items.put(k, new ItemInfo(k, "i" + k, c[0], Integer.parseInt(c[1])));
            // Pareto-like: most types 1-2 stacks, a few large
            double u = Math.max(1e-6, rnd.nextDouble());
            stacksOf.add((int) Math.min(200, Math.floor(1 / Math.pow(u, 0.9))));
        }
        double want = p.fill * slots, have = 0;
        for (int s : stacksOf) have += s;
        double scale = want / Math.max(1, have);
        // ---- place: home chest by category (sorted-ish) unless "messy" ----
        Map<String, Integer> home = new HashMap<>();
        List<int[]> stacks = new ArrayList<>(); // key, count
        for (int k = 0; k < p.itemTypes; k++) {
            int n = Math.max(1, (int) Math.round(stacksOf.get(k) * scale));
            int lim = Math.min(64, items.get(k).maxStack);
            for (int j = 0; j < n; j++) {
                int count = lim;
                if (lim > 1 && rnd.nextDouble() < p.partial) count = 1 + rnd.nextInt(lim - 1);
                stacks.add(new int[] { k, count });
            }
        }
        java.util.Collections.shuffle(stacks, rnd);
        for (int[] st : stacks) {
            String cat = items.get(st[0]).category;
            int target = rnd.nextDouble() < p.mess ? rnd.nextInt(chests.size())
                : home.computeIfAbsent(cat, x -> rnd.nextInt(chests.size()));
            for (int tries = 0; tries < chests.size(); tries++) {
                ChestModel c = chests.get((target + tries) % chests.size());
                int free = -1;
                for (int s = 0; s < c.size(); s++) if (c.keys[s] == ChestModel.EMPTY) {
                    free = s;
                    break;
                }
                if (free >= 0) {
                    c.set(free, st[0], st[1]);
                    break;
                }
            }
        }
        int totalStacks = 0;
        for (ChestModel c : chests) for (int k : c.keys) if (k != ChestModel.EMPTY) totalStacks++;
        // ---- combine (what the run does on first open), then assign + plan ----
        int freed = 0;
        for (ChestModel c : chests) {
            int before = used(c);
            Combiner.combine(c, items);
            freed += before - used(c);
        }
        long t0 = System.nanoTime();
        Assigner.Result a = Assigner.assign(chests, items, new HashMap<Integer, String>(),
            new Assigner.Weights(1.0, p.layout), Taxonomy.defaults(), p.minSplit, p.keepFree);
        Plan plan = Planner.plan(chests, items, a, p.carry, 100000, new BlockPos(7, 57, -3));
        double ms = (System.nanoTime() - t0) / 1e6;
        int checkFails = PlanChecker.check(chests, plan, items, p.carry)
            .size();
        // ---- metrics ----
        Map<String, List<ChestModel>> byBucket = new TreeMap<>();
        for (ChestModel c : chests) {
            String b = a.chestCategory.get(c.id);
            if (b != null) byBucket.computeIfAbsent(b, x -> new ArrayList<ChestModel>())
                .add(c);
        }
        int miscStacks = 0, unassignedStacks = 0, after = 0;
        for (ChestModel c : plan.expected.values()) {
            for (int s = 0; s < c.size(); s++) {
                if (c.keys[s] == ChestModel.EMPTY) continue;
                after++;
                String b = a.effective(items.get(c.keys[s]).category);
                if (Taxonomy.MISC.equals(b)) miscStacks++;
                if (a.unassigned.contains(items.get(c.keys[s]).category)) unassignedStacks++;
            }
        }
        double scatterMax = 0;
        int scattered = 0, tightB = 0;
        for (Map.Entry<String, List<ChestModel>> e : byBucket.entrySet()) {
            List<ChestModel> cs = e.getValue();
            double far = 0;
            for (ChestModel x : cs) for (ChestModel y : cs) far = Math.max(far, d(x.pos, y.pos));
            scatterMax = Math.max(scatterMax, far);
            if (far > 6) scattered++;
            int cap = 0, usedSlots = 0;
            for (ChestModel c : cs) {
                cap += c.size();
                usedSlots += used(plan.expected.get(c.id));
            }
            if (cap - usedSlots < Math.max(1, cap / 10)) tightB++;
        }
        int nearEmpty = 0;
        for (ChestModel c : chests) {
            if (a.chestCategory.containsKey(c.id) && used(plan.expected.get(c.id)) < c.size() / 10) nearEmpty++;
        }
        // Related: for each bucket, distance to the nearest chest of its most related other bucket.
        Taxonomy tax = Taxonomy.defaults();
        double relSum = 0;
        int relN = 0;
        for (String b : byBucket.keySet()) {
            String best = null;
            double r = 0;
            for (String o : byBucket.keySet()) {
                if (o.equals(b)) continue;
                double v = tax.relation(b, o);
                if (v > r) {
                    r = v;
                    best = o;
                }
            }
            if (best == null || r < 0.3) continue;
            double near = Double.MAX_VALUE;
            for (ChestModel x : byBucket.get(b)) for (ChestModel y : byBucket.get(best)) near = Math.min(near, d(x.pos, y.pos));
            relSum += near;
            relN++;
        }
        // Walking: from start to each trip's source, then through its deposits.
        double walk = 0;
        BlockPos at = new BlockPos(7, 57, -3);
        Map<Integer, BlockPos> pos = new HashMap<>();
        for (ChestModel c : chests) pos.put(c.id, c.pos);
        for (Plan.Trip t : plan.trips) {
            walk += d(at, pos.get(t.source));
            at = pos.get(t.source);
            for (Plan.Deposit dp : t.deposits) {
                walk += d(at, pos.get(dp.target));
                at = pos.get(dp.target);
            }
        }
        return new double[] { totalStacks, byBucket.size(), a.chestCategory.size(),
            after == 0 ? 0 : 100.0 * miscStacks / after, plan.stacksLeftNoRoom + unassignedStacks, plan.stackMoves(),
            plan.trips.size(), walk, freed, scatterMax, scattered, tightB, nearEmpty, relN == 0 ? 0 : relSum / relN,
            ms, checkFails };
    }

    static int used(ChestModel c) {
        int n = 0;
        for (int k : c.keys) if (k != ChestModel.EMPTY) n++;
        return n;
    }

    static double d(BlockPos a, BlockPos b) {
        double dx = a.x - b.x, dy = a.y - b.y, dz = a.z - b.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
