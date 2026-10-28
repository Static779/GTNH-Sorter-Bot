import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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
 * Theoretical runs of the planner on generated chest rooms. Two parts:
 * <ol>
 * <li>One-at-a-time: every variable swept across its range from a baseline, other variables fixed.</li>
 * <li>Random: every variable drawn at random together, to see interactions.</li>
 * </ol>
 * Items come from a real trace (category + max stack per item type). Writes one CSV row per generated room with all
 * variables and metrics; tools/sweep_report.py summarises it.
 *
 * Time estimates use costs measured or derived from trace 012605 and the current code: walking 4.2 ticks per block
 * beyond reach (3 blocks), opening 5 ticks, the server check before a chest closes 5 ticks, 1 tick per stack moved or
 * pour combined. They are estimates.
 *
 * Usage: tools/sweep.sh [items.tsv] [oatSeeds] [randomRooms] [out.csv] [threads]
 */
public class Sweep {

    static List<String[]> catalog = new ArrayList<>();

    static final double WALK_TICKS_PER_BLOCK = 4.2, REACH = 3.0;
    static final int OPEN_TICKS = 5, CHECK_TICKS = 5;

    /** All variables of one generated room. */
    static final class P implements Cloneable {

        String part = "oat", varied = "baseline", value = "-";
        int chests = 48, itemTypes = 400, carry = 13, minSplit = 6, seed;
        double fill = 0.6, mess = 0.7, partial = 0.3, layout = 1.0, keepFree = 0.15, skew = 0.9, toolShare = -1;
        String shape = "walls", sizes = "double", catalogMix = "natural", start = "edge";

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

        static final String[] HEAD = { "part", "varied", "value", "seed", "chests", "shape", "sizes", "fill",
            "itemTypes", "skew", "catalogMix", "toolShare", "mess", "partial", "carry", "minSplit", "layout",
            "keepFree", "start" };

        String row() {
            return String.join(
                ",",
                part,
                varied,
                value,
                "" + seed,
                "" + chests,
                shape,
                sizes,
                f(fill),
                "" + itemTypes,
                f(skew),
                catalogMix,
                f(toolShare),
                f(mess),
                f(partial),
                "" + carry,
                "" + minSplit,
                f(layout),
                f(keepFree),
                start);
        }
    }

    static final String[] METRICS = { "stacksBefore", "stacksAfterCombine", "combineFreed", "slots", "actualFill",
        "buckets", "chestsUsed", "bucketsPerChest", "miscSharePct", "stranded", "strandedPct", "moves", "movedPct",
        "trips", "stacksPerTrip", "carryUtilPct", "depositsPerTrip", "keptOpenTrips", "walkBlocks", "walkPerMove",
        "execTicks", "execWalkPct", "surveyTicksOpen", "surveyTicksLook", "minutesOpenSurvey", "minutesLocalRead",
        "purityPct", "fineCatsPerChest", "scatterMax", "scatteredBuckets", "contiguousPct", "relatedDist",
        "tightBuckets", "minFreePct", "avgFreePct", "nearEmptyChests", "fillStdDev", "planMs", "checkFails",
        "deterministic" };

    public static void main(String[] args) throws Exception {
        String file = args.length > 0 ? args[0] : "tools/sweep/items-012605.tsv";
        int oatSeeds = args.length > 1 ? Integer.parseInt(args[1]) : 10;
        int randomRooms = args.length > 2 ? Integer.parseInt(args[2]) : 600;
        String out = args.length > 3 ? args[3] : "sweep.csv";
        int threads = args.length > 4 ? Integer.parseInt(args[4])
            : Runtime.getRuntime()
                .availableProcessors();
        for (String l : Files.readAllLines(Paths.get(file), StandardCharsets.UTF_8)) {
            String[] f = l.split("\t");
            if (f.length == 2) catalog.add(f);
        }

        List<P> runs = new ArrayList<>();
        P base = new P();
        addOat(runs, base, oatSeeds);
        Random r = new Random(2026);
        for (int i = 0; i < randomRooms; i++) {
            P p = base.with("random", "" + i);
            p.part = "random";
            p.seed = 100000 + i;
            p.chests = 8 + r.nextInt(121);
            p.shape = pick(r, "walls", "row", "tower", "scattered", "rooms");
            p.sizes = pick(r, "double", "single", "iron", "mixed");
            p.fill = 0.2 + 0.77 * r.nextDouble();
            p.itemTypes = 30 + r.nextInt(1471);
            p.skew = 0.6 + 0.8 * r.nextDouble();
            p.catalogMix = pick(r, "natural", "natural", "gtHeavy", "modHeavy");
            p.toolShare = r.nextDouble() < 0.5 ? -1 : 0.4 * r.nextDouble();
            p.mess = r.nextDouble();
            p.partial = 0.8 * r.nextDouble();
            p.carry = 1 + r.nextInt(27);
            p.minSplit = new int[] { 1, 2, 4, 6, 8, 12, 16, 24, 36, 54 }[r.nextInt(10)];
            p.layout = new double[] { 0, 0.5, 1, 1.5, 2, 3, 5 }[r.nextInt(7)];
            p.keepFree = 0.4 * r.nextDouble();
            p.start = pick(r, "edge", "center", "far");
            runs.add(p);
        }
        System.err.println(runs.size() + " rooms on " + threads + " threads");

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Future<String>> results = new ArrayList<>();
        for (final P p : runs) results.add(pool.submit(() -> p.row() + "," + join(run(p))));
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(Paths.get(out), StandardCharsets.UTF_8))) {
            w.println(String.join(",", P.HEAD) + "," + String.join(",", METRICS));
            int done = 0;
            for (Future<String> f : results) {
                w.println(f.get());
                if (++done % 100 == 0) System.err.println(done + " done");
            }
        }
        pool.shutdown();
    }

    static void addOat(List<P> runs, P base, int seeds) {
        List<P> points = new ArrayList<>();
        points.add(base.with("baseline", "-"));
        for (int v : new int[] { 8, 16, 32, 48, 72, 96, 128, 150 }) points.add(set(base.with("chests", "" + v), "chests", v));
        for (String v : new String[] { "walls", "row", "tower", "scattered", "rooms" }) {
            P p = base.with("shape", v);
            p.shape = v;
            points.add(p);
        }
        for (String v : new String[] { "double", "single", "iron", "mixed" }) {
            P p = base.with("sizes", v);
            p.sizes = v;
            points.add(p);
        }
        for (double v : new double[] { 0.2, 0.4, 0.6, 0.75, 0.85, 0.9, 0.95 }) {
            P p = base.with("fill", "" + v);
            p.fill = v;
            points.add(p);
        }
        for (int v : new int[] { 30, 100, 250, 400, 880, 1500 }) points.add(set(base.with("itemTypes", "" + v), "itemTypes", v));
        for (double v : new double[] { 0.6, 0.9, 1.2, 1.4 }) {
            P p = base.with("skew", "" + v);
            p.skew = v;
            points.add(p);
        }
        for (String v : new String[] { "natural", "gtHeavy", "modHeavy" }) {
            P p = base.with("catalogMix", v);
            p.catalogMix = v;
            points.add(p);
        }
        for (double v : new double[] { 0, 0.15, 0.4 }) {
            P p = base.with("toolShare", "" + v);
            p.toolShare = v;
            points.add(p);
        }
        for (double v : new double[] { 0, 0.25, 0.5, 0.75, 1.0 }) {
            P p = base.with("mess", "" + v);
            p.mess = v;
            points.add(p);
        }
        for (double v : new double[] { 0, 0.3, 0.6, 0.8 }) {
            P p = base.with("partial", "" + v);
            p.partial = v;
            points.add(p);
        }
        for (int v : new int[] { 1, 3, 6, 13, 20, 27 }) points.add(set(base.with("carry", "" + v), "carry", v));
        for (int v : new int[] { 1, 2, 4, 6, 12, 24, 54 }) points.add(set(base.with("minSplit", "" + v), "minSplit", v));
        for (double v : new double[] { 0, 0.5, 1, 2, 3, 5 }) {
            P p = base.with("layout", "" + v);
            p.layout = v;
            points.add(p);
        }
        for (double v : new double[] { 0, 0.1, 0.15, 0.25, 0.4 }) {
            P p = base.with("keepFree", "" + v);
            p.keepFree = v;
            points.add(p);
        }
        for (String v : new String[] { "edge", "center", "far" }) {
            P p = base.with("start", v);
            p.start = v;
            points.add(p);
        }
        for (P p : points) {
            for (int s = 0; s < seeds; s++) {
                P q = p.with(p.varied, p.value);
                q.seed = s;
                runs.add(q);
            }
        }
    }

    static P set(P p, String field, int v) {
        if (field.equals("chests")) p.chests = v;
        if (field.equals("itemTypes")) p.itemTypes = v;
        if (field.equals("carry")) p.carry = v;
        if (field.equals("minSplit")) p.minSplit = v;
        return p;
    }

    // =========================================================================================================

    static double[] run(P p) {
        Random rnd = new Random(p.seed * 7919L + p.varied.hashCode() * 31L + p.value.hashCode());
        List<ChestModel> chests = room(p, rnd);
        int slots = 0;
        for (ChestModel c : chests) slots += c.size();
        Map<Integer, ItemInfo> items = items(p, rnd);
        fillRoom(p, rnd, chests, items, slots);

        int stacksBefore = 0;
        for (ChestModel c : chests) stacksBefore += used(c);
        int pours = 0;
        Map<Integer, Integer> poursIn = new HashMap<>();
        for (ChestModel c : chests) {
            int n = Combiner.combine(c, items)
                .size();
            pours += n;
            poursIn.put(c.id, n);
        }
        int stacks = 0;
        for (ChestModel c : chests) stacks += used(c);

        BlockPos start = start(p, chests);
        long t0 = System.nanoTime();
        Assigner.Result a = Assigner.assign(
            chests,
            items,
            new HashMap<Integer, String>(),
            new Assigner.Weights(1.0, p.layout),
            Taxonomy.defaults(),
            p.minSplit,
            p.keepFree);
        Plan plan = Planner.plan(chests, items, a, p.carry, 100000, start);
        double ms = (System.nanoTime() - t0) / 1e6;
        int checkFails = PlanChecker.check(chests, plan, items, p.carry)
            .size();
        int deterministic = 1;
        if (p.seed % 5 == 0) {
            Assigner.Result a2 = Assigner.assign(
                chests,
                items,
                new HashMap<Integer, String>(),
                new Assigner.Weights(1.0, p.layout),
                Taxonomy.defaults(),
                p.minSplit,
                p.keepFree);
            Plan plan2 = Planner.plan(chests, items, a2, p.carry, 100000, start);
            deterministic = a2.chestCategory.equals(a.chestCategory) && plan2.trips.size() == plan.trips.size()
                && plan2.stackMoves() == plan.stackMoves() ? 1 : 0;
        }

        Map<Integer, BlockPos> pos = new HashMap<>();
        for (ChestModel c : chests) pos.put(c.id, c.pos);
        Map<String, List<ChestModel>> byBucket = new TreeMap<>();
        for (ChestModel c : chests) {
            String b = a.chestCategory.get(c.id);
            if (b != null) byBucket.computeIfAbsent(b, x -> new ArrayList<ChestModel>())
                .add(c);
        }

        // ---- after the plan: purity, misc, stranded, specificity ----
        int after = 0, inPlace = 0, misc = 0, unassigned = 0;
        double fineSum = 0;
        int fineChests = 0;
        for (ChestModel c : plan.expected.values()) {
            Set<String> fine = new HashSet<>();
            String own = a.chestCategory.get(c.id);
            for (int s = 0; s < c.size(); s++) {
                if (c.keys[s] == ChestModel.EMPTY) continue;
                after++;
                String cat = items.get(c.keys[s]).category;
                String b = a.effective(cat);
                fine.add(cat);
                if (b.equals(own)) inPlace++;
                if (Taxonomy.MISC.equals(b)) misc++;
                if (a.unassigned.contains(cat)) unassigned++;
            }
            if (!fine.isEmpty()) {
                fineSum += fine.size();
                fineChests++;
            }
        }
        int stranded = plan.stacksLeftNoRoom + unassigned;

        // ---- layout ----
        double scatterMax = 0;
        int scattered = 0, multi = 0, contiguous = 0, tight = 0;
        double minFree = 100, freeSum = 0;
        for (List<ChestModel> cs : byBucket.values()) {
            double far = 0;
            for (ChestModel x : cs) for (ChestModel y : cs) far = Math.max(far, d(x.pos, y.pos));
            scatterMax = Math.max(scatterMax, far);
            if (far > 6) scattered++;
            if (cs.size() > 1) {
                multi++;
                if (connected(cs, 3.5)) contiguous++;
            }
            int cap = 0, usedSlots = 0;
            for (ChestModel c : cs) {
                cap += c.size();
                usedSlots += used(plan.expected.get(c.id));
            }
            double freePct = 100.0 * (cap - usedSlots) / cap;
            minFree = Math.min(minFree, freePct);
            freeSum += freePct;
            if (freePct < 10) tight++;
        }
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
        int nearEmpty = 0;
        List<Double> fills = new ArrayList<>();
        for (ChestModel c : chests) {
            if (!a.chestCategory.containsKey(c.id)) continue;
            double f = (double) used(plan.expected.get(c.id)) / c.size();
            fills.add(f);
            if (f < 0.1) nearEmpty++;
        }

        // ---- trips and time ----
        double walk = 0, walkTicks = 0, execTicks = 0;
        int deposits = 0, kept = 0;
        BlockPos at = start;
        int lastChest = -1;
        Set<Integer> opened = new HashSet<>();
        for (Plan.Trip t : plan.trips) {
            boolean reuse = t.source == lastChest;
            if (reuse) kept++;
            double dd = d(at, pos.get(t.source));
            walk += dd;
            walkTicks += WALK_TICKS_PER_BLOCK * Math.max(0, dd - REACH);
            if (!reuse) execTicks += OPEN_TICKS;
            execTicks += firstOpenCost(t.source, opened, poursIn) + t.picks.size() + CHECK_TICKS;
            at = pos.get(t.source);
            for (Plan.Deposit dp : t.deposits) {
                double dx = d(at, pos.get(dp.target));
                walk += dx;
                walkTicks += WALK_TICKS_PER_BLOCK * Math.max(0, dx - REACH);
                execTicks += OPEN_TICKS + firstOpenCost(dp.target, opened, poursIn) + dp.picks.size() + CHECK_TICKS;
                at = pos.get(dp.target);
                deposits++;
                lastChest = dp.target;
            }
        }
        // chests with pours that no trip opened get a tidy visit
        for (ChestModel c : chests) {
            if (!opened.contains(c.id) && poursIn.get(c.id) > 0) {
                double dd = d(at, c.pos);
                walk += dd;
                walkTicks += WALK_TICKS_PER_BLOCK * Math.max(0, dd - REACH);
                execTicks += OPEN_TICKS + poursIn.get(c.id) + CHECK_TICKS;
                at = c.pos;
            }
        }
        execTicks += walkTicks;
        // survey: nearest-neighbour tour from the start; open = walk + open each; look = walk + 6 ticks each
        double tour = 0;
        List<ChestModel> left = new ArrayList<>(chests);
        BlockPos sp = start;
        while (!left.isEmpty()) {
            ChestModel best = null;
            double bd = Double.MAX_VALUE;
            for (ChestModel c : left) {
                double dd = d(sp, c.pos);
                if (dd < bd) {
                    bd = dd;
                    best = c;
                }
            }
            tour += WALK_TICKS_PER_BLOCK * Math.max(0, bd - REACH);
            sp = best.pos;
            left.remove(best);
        }
        double surveyOpen = tour + chests.size() * (OPEN_TICKS + 1.0);
        double surveyLook = tour + chests.size() * 6.0;
        int moves = plan.stackMoves();

        return new double[] { stacksBefore, stacks, stacksBefore - stacks, slots, 100.0 * stacks / slots,
            byBucket.size(), a.chestCategory.size(), (double) byBucket.size() / Math.max(1, chests.size()),
            after == 0 ? 0 : 100.0 * misc / after, stranded, stacks == 0 ? 0 : 100.0 * stranded / stacks, moves,
            stacks == 0 ? 0 : 100.0 * moves / stacks, plan.trips.size(),
            plan.trips.isEmpty() ? 0 : (double) moves / plan.trips.size(),
            plan.trips.isEmpty() ? 0 : 100.0 * moves / plan.trips.size() / p.carry,
            plan.trips.isEmpty() ? 0 : (double) deposits / plan.trips.size(), kept, walk,
            moves == 0 ? 0 : walk / moves, execTicks, execTicks == 0 ? 0 : 100.0 * walkTicks / execTicks, surveyOpen,
            surveyLook, (surveyOpen + execTicks) / 1200.0, (1 + execTicks) / 1200.0,
            after == 0 ? 100 : 100.0 * inPlace / after, fineChests == 0 ? 0 : fineSum / fineChests, scatterMax,
            scattered, multi == 0 ? 100 : 100.0 * contiguous / multi, relN == 0 ? 0 : relSum / relN, tight,
            byBucket.isEmpty() ? 0 : minFree, byBucket.isEmpty() ? 0 : freeSum / byBucket.size(), nearEmpty,
            stdDev(fills), ms, checkFails, deterministic };
    }

    static double firstOpenCost(int chest, Set<Integer> opened, Map<Integer, Integer> poursIn) {
        if (!opened.add(chest)) return 0;
        int n = poursIn.get(chest);
        return n == 0 ? 0 : n + CHECK_TICKS;
    }

    // ---- room generation ----

    static List<ChestModel> room(P p, Random rnd) {
        List<ChestModel> chests = new ArrayList<>();
        Set<String> taken = new HashSet<>();
        for (int i = 0; i < p.chests; i++) {
            int x, y, z;
            switch (p.shape) {
                case "row": // one long wall, 2 high
                    x = (i / 2) * 2;
                    y = 60 + i % 2;
                    z = 0;
                    break;
                case "tower": // columns 3 wide, up to 10 high
                    x = (i % 3) * 2 + (i / 30) * 8;
                    y = 56 + (i / 3) % 10;
                    z = 0;
                    break;
                case "scattered": { // random spots in a 30 x 30 area
                    do {
                        x = rnd.nextInt(30);
                        y = 60 + rnd.nextInt(3);
                        z = rnd.nextInt(30);
                    } while (!taken.add(x + "," + y + "," + z));
                    break;
                }
                case "rooms": { // rooms of 12 chests (4 x 3), 15 blocks apart
                    int r = i / 12, k = i % 12;
                    x = (r % 4) * 15 + (k % 4) * 2;
                    y = 60 + k / 4;
                    z = (r / 4) * 15;
                    break;
                }
                default: { // walls of 6 x 5, 5 blocks apart (like the traced room)
                    int wall = i / 30, in = i % 30;
                    x = (in % 6) * 3;
                    y = 56 + in / 6;
                    z = wall * 5;
                }
            }
            int size;
            switch (p.sizes) {
                case "single":
                    size = 27;
                    break;
                case "iron":
                    size = new int[] { 54, 81, 108 }[rnd.nextInt(3)];
                    break;
                case "mixed":
                    size = new int[] { 27, 54, 54, 81 }[rnd.nextInt(4)];
                    break;
                default:
                    size = 54;
            }
            chests.add(new ChestModel(i, new BlockPos(x, y, z), "c" + i, size, 64));
        }
        return chests;
    }

    static Map<Integer, ItemInfo> items(P p, Random rnd) {
        List<String[]> pool = new ArrayList<>();
        for (String[] c : catalog) {
            boolean mod = c[0].startsWith("mod:");
            if (p.catalogMix.equals("gtHeavy") && mod) continue;
            if (p.catalogMix.equals("modHeavy") && !mod) continue;
            pool.add(c);
        }
        Collections.shuffle(pool, rnd);
        Map<Integer, ItemInfo> items = new HashMap<>();
        for (int k = 0; k < p.itemTypes; k++) {
            String[] c = pool.get(k % pool.size());
            int max = Integer.parseInt(c[1]);
            if (p.toolShare >= 0) max = rnd.nextDouble() < p.toolShare ? 1 : (max == 1 ? 64 : max);
            items.put(k, new ItemInfo(k, "i" + k, c[0], max));
        }
        return items;
    }

    static void fillRoom(P p, Random rnd, List<ChestModel> chests, Map<Integer, ItemInfo> items, int slots) {
        List<Double> weight = new ArrayList<>();
        double total = 0;
        for (int k = 0; k < p.itemTypes; k++) {
            double u = Math.max(1e-6, rnd.nextDouble());
            double w = Math.min(300, 1 / Math.pow(u, p.skew));
            weight.add(w);
            total += w;
        }
        double want = p.fill * slots;
        List<int[]> stacks = new ArrayList<>();
        for (int k = 0; k < p.itemTypes; k++) {
            int n = (int) Math.round(weight.get(k) / total * want);
            if (n == 0 && rnd.nextDouble() < weight.get(k) / total * want) n = 1;
            int lim = Math.min(64, items.get(k).maxStack);
            for (int j = 0; j < n; j++) {
                int count = lim;
                if (lim > 1 && rnd.nextDouble() < p.partial) count = 1 + rnd.nextInt(lim - 1);
                stacks.add(new int[] { k, count });
            }
        }
        Collections.shuffle(stacks, rnd);
        Map<String, Integer> home = new HashMap<>();
        for (int[] st : stacks) {
            String cat = items.get(st[0]).category;
            int target = rnd.nextDouble() < p.mess ? rnd.nextInt(chests.size())
                : home.computeIfAbsent(cat, x -> rnd.nextInt(chests.size()));
            for (int tries = 0; tries < chests.size(); tries++) {
                ChestModel c = chests.get((target + tries) % chests.size());
                int free = -1;
                for (int s = 0; s < c.size(); s++) {
                    if (c.keys[s] == ChestModel.EMPTY) {
                        free = s;
                        break;
                    }
                }
                if (free >= 0) {
                    c.set(free, st[0], st[1]);
                    break;
                }
            }
        }
    }

    static BlockPos start(P p, List<ChestModel> chests) {
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (ChestModel c : chests) {
            minX = Math.min(minX, c.pos.x);
            maxX = Math.max(maxX, c.pos.x);
            minZ = Math.min(minZ, c.pos.z);
            maxZ = Math.max(maxZ, c.pos.z);
        }
        switch (p.start) {
            case "center":
                return new BlockPos((minX + maxX) / 2, 60, (minZ + maxZ) / 2);
            case "far":
                return new BlockPos(maxX + 20, 60, maxZ + 20);
            default:
                return new BlockPos(minX - 2, 60, minZ - 2);
        }
    }

    // ---- helpers ----

    static boolean connected(List<ChestModel> cs, double link) {
        Set<Integer> seen = new HashSet<>();
        List<ChestModel> q = new ArrayList<>();
        q.add(cs.get(0));
        seen.add(cs.get(0).id);
        for (int i = 0; i < q.size(); i++) {
            for (ChestModel o : cs) {
                if (!seen.contains(o.id) && d(q.get(i).pos, o.pos) <= link) {
                    seen.add(o.id);
                    q.add(o);
                }
            }
        }
        return seen.size() == cs.size();
    }

    static double stdDev(List<Double> v) {
        if (v.size() < 2) return 0;
        double m = 0;
        for (double x : v) m += x;
        m /= v.size();
        double s = 0;
        for (double x : v) s += (x - m) * (x - m);
        return Math.sqrt(s / (v.size() - 1));
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

    static String f(double v) {
        return String.format(Locale.ROOT, "%.3f", v);
    }

    static String join(double[] v) {
        StringBuilder sb = new StringBuilder();
        for (double x : v) {
            if (sb.length() > 0) sb.append(',');
            sb.append(f(x));
        }
        return sb.toString();
    }

    static String pick(Random r, String... v) {
        return v[r.nextInt(v.length)];
    }
}
