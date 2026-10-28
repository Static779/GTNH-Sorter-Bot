import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import com.whalestorm.chestsorter.core.Assigner;
import com.whalestorm.chestsorter.core.BlockPos;
import com.whalestorm.chestsorter.core.Categorizer;
import com.whalestorm.chestsorter.core.ChestModel;
import com.whalestorm.chestsorter.core.ChestPairing;
import com.whalestorm.chestsorter.core.ItemInfo;
import com.whalestorm.chestsorter.core.PathFinder;
import com.whalestorm.chestsorter.core.Plan;
import com.whalestorm.chestsorter.core.PlanChecker;
import com.whalestorm.chestsorter.core.Planner;
import com.whalestorm.chestsorter.core.SorterState;

/** Plain-Java tests for the core package (no JUnit, no Minecraft). Run with tools/test-core.sh. */
public class CoreTests {

    static int failures = 0, checks = 0;

    public static void main(String[] args) throws Exception {
        String prefixFile = args.length > 0 ? args[0] : "src/main/resources/assets/chestsorter/gt-ore-prefixes.txt";
        // pairing + state
        singleChestStaysSingle();
        twoTouchingChestsPair();
        chestAndTrappedChestDoNotPair();
        diagonalAndVerticalDoNotPair();
        threeInARowPairsFirstTwoOnly();
        twoSeparateDoubles();
        toggle();
        // categorizer
        categorizer(new Categorizer(Files.readAllLines(Paths.get(prefixFile))));
        // model
        roomAndPlace();
        // assigner
        assignerMajorityAndEmpties();
        assignerLocked();
        // planner
        plannerBasicSort();
        plannerLeavesUnassignedAndFull();
        plannerRespectsCarryLimit();
        plannerUsesFreedSlots();
        plannerNeverTouchesWellPlaced();
        randomizedPlans(3000);
        checkerCatchesBadPlans();
        // pathfinder
        pathFlat();
        pathAroundWall();
        pathStepUpAndDrop();
        pathNoCornerCutting();
        pathBlockedReturnsNull();
        pathAvoidsTooHighDrop();
        pathAlreadyThere();

        System.out.println(checks + " checks, " + failures + " failed");
        if (failures > 0) System.exit(1);
        System.out.println("all core tests passed");
    }

    // ---------------- pairing / state ----------------

    static final String CHEST = "chest", TRAPPED = "trapped";

    static Map<BlockPos, Object> chests(Object... xyzKey) {
        Map<BlockPos, Object> m = new HashMap<>();
        for (int i = 0; i < xyzKey.length; i += 4) {
            m.put(new BlockPos((int) xyzKey[i], (int) xyzKey[i + 1], (int) xyzKey[i + 2]), xyzKey[i + 3]);
        }
        return m;
    }

    static void singleChestStaysSingle() {
        check("single", ChestPairing.pair(chests(0, 64, 0, CHEST)).isEmpty());
    }

    static void twoTouchingChestsPair() {
        Map<BlockPos, BlockPos> p = ChestPairing.pair(chests(0, 64, 0, CHEST, 1, 64, 0, CHEST));
        check("pair x", new BlockPos(1, 64, 0).equals(p.get(new BlockPos(0, 64, 0))));
        check("pair x reverse", new BlockPos(0, 64, 0).equals(p.get(new BlockPos(1, 64, 0))));
        check("pair z", ChestPairing.pair(chests(5, 70, -3, CHEST, 5, 70, -2, CHEST)).size() == 2);
    }

    static void chestAndTrappedChestDoNotPair() {
        check("chest+trapped", ChestPairing.pair(chests(0, 64, 0, CHEST, 1, 64, 0, TRAPPED)).isEmpty());
        check("trapped+trapped", ChestPairing.pair(chests(0, 64, 0, TRAPPED, 1, 64, 0, TRAPPED)).size() == 2);
    }

    static void diagonalAndVerticalDoNotPair() {
        check("diagonal", ChestPairing.pair(chests(0, 64, 0, CHEST, 1, 64, 1, CHEST)).isEmpty());
        check("vertical", ChestPairing.pair(chests(0, 64, 0, CHEST, 0, 65, 0, CHEST)).isEmpty());
    }

    static void threeInARowPairsFirstTwoOnly() {
        Map<BlockPos, BlockPos> p = ChestPairing.pair(chests(0, 64, 0, CHEST, 1, 64, 0, CHEST, 2, 64, 0, CHEST));
        check("three: first two paired", new BlockPos(1, 64, 0).equals(p.get(new BlockPos(0, 64, 0))));
        check("three: third alone", !p.containsKey(new BlockPos(2, 64, 0)));
    }

    static void twoSeparateDoubles() {
        check("two doubles",
            ChestPairing.pair(chests(0, 64, 0, CHEST, 1, 64, 0, CHEST, 0, 64, 2, CHEST, 1, 64, 2, CHEST)).size() == 4);
    }

    static void toggle() {
        check("IDLE->SCAN", SorterState.IDLE.toggled() == SorterState.SCAN);
        check("SCAN->IDLE", SorterState.SCAN.toggled() == SorterState.IDLE);
        check("EXECUTE->IDLE", SorterState.EXECUTE.toggled() == SorterState.IDLE);
    }

    // ---------------- categorizer ----------------

    static void categorizer(Categorizer c) {
        eq("ingot", c.categorize(Arrays.asList("ingotCopper"), "gregtech"));
        eq("ingotHot", c.categorize(Arrays.asList("ingotHotTitanium"), "gregtech"));
        eq("dustTiny", c.categorize(Arrays.asList("dustTinyIron"), "gregtech"));
        eq("dust", c.categorize(Arrays.asList("dustIron"), "gregtech"));
        eq("crushedPurified", c.categorize(Arrays.asList("crushedPurifiedTin"), "gregtech"));
        eq("plateDouble", c.categorize(Arrays.asList("plateDoubleSteel"), "gregtech"));
        eq("ore", c.categorize(Arrays.asList("oreCopper"), "gregtech"));
        eq("ore", c.categorize(Arrays.asList("oreBlackgraniteCopper"), "gregtech"));
        // Longest prefix wins among several names; ties alphabetical.
        eq("dustTiny", c.categorize(Arrays.asList("dustIron", "dustTinyIron"), "x"));
        eq("ingot", c.categorize(Arrays.asList("ingotIron", "dustIron"), "x")); // longer prefix wins
        eq("dust", c.categorize(Arrays.asList("foilIron", "dustIron"), "x")); // same length: alphabetical
        // Unknown prefix: leading lowercase run.
        eq("zorp", c.categorize(Arrays.asList("zorpThing"), "x"));
        // No usable ore name.
        eq("mod:gregtech", c.categorize(new ArrayList<String>(), "gregtech"));
        eq("mod:minecraft", c.categorize(Arrays.asList("X"), "Minecraft"));
        eq("misc", c.categorize(null, null));
        // Boundary: "ingotx" is not "ingot" + name.
        eq("ingotx", c.categorize(Arrays.asList("ingotx"), "x"));
        check("prefix of null", c.prefixOf(null) == null);
    }

    // ---------------- model ----------------

    static void roomAndPlace() {
        ChestModel c = new ChestModel(0, new BlockPos(0, 0, 0), "t", 3, 64);
        c.set(0, 7, 60);
        c.set(1, 8, 10);
        check("room 7", c.roomFor(7, 64) == 4 + 64);
        check("room 7 max16", c.roomFor(7, 16) == 0 + 16);
        check("room 9", c.roomFor(9, 64) == 64);
        int left = c.place(7, 30, 64);
        check("place left 0", left == 0);
        check("place topped up first", c.counts[0] == 64 && c.keys[2] == 7 && c.counts[2] == 26);
        check("place overflow", c.place(9, 10, 64) == 10);
    }

    // ---------------- assigner ----------------

    static Map<Integer, ItemInfo> items(String... catPerKey) {
        Map<Integer, ItemInfo> m = new HashMap<>();
        for (int i = 0; i < catPerKey.length; i++) m.put(i, new ItemInfo(i, "item" + i, catPerKey[i], 64));
        return m;
    }

    static ChestModel chest(int id, int slots, int... keyCount) {
        ChestModel c = new ChestModel(id, new BlockPos(id * 2, 64, 0), "c", slots, 64);
        for (int i = 0, s = 0; i < keyCount.length; i += 2, s++) c.set(s, keyCount[i], keyCount[i + 1]);
        return c;
    }

    static void assignerMajorityAndEmpties() {
        Map<Integer, ItemInfo> it = items("ingot", "dust", "plate");
        List<ChestModel> cs = Arrays.asList(chest(0, 27, 0, 64, 0, 64, 1, 10), chest(1, 27), chest(2, 27, 2, 5));
        Assigner.Result r = Assigner.assign(cs, it);
        eq("ingot", r.chestCategory.get(0));
        eq("dust", r.chestCategory.get(1)); // dust had no owner; chest 1 is the first empty chest
        eq("plate", r.chestCategory.get(2));
        check("nothing unassigned", r.unassigned.isEmpty());
        // Not enough empties.
        Assigner.Result r2 = Assigner.assign(Arrays.asList(chest(0, 27, 0, 64, 0, 64, 1, 10)), it);
        check("dust unassigned", r2.unassigned.equals(Arrays.asList("dust")));
        // Tie goes alphabetical.
        Assigner.Result r3 = Assigner.assign(Arrays.asList(chest(0, 27, 1, 1, 0, 1)), it);
        eq("dust", r3.chestCategory.get(0));
    }

    static void assignerLocked() {
        Map<Integer, ItemInfo> it = items("ingot", "dust");
        Map<Integer, String> locked = new HashMap<>();
        locked.put(0, "dust");
        Assigner.Result r = Assigner.assign(Arrays.asList(chest(0, 27, 0, 64), chest(1, 27)), it, locked);
        eq("dust", r.chestCategory.get(0));
        eq("ingot", r.chestCategory.get(1));
    }

    // ---------------- planner ----------------

    static Plan planAndCheck(String name, List<ChestModel> cs, Map<Integer, ItemInfo> it, int carry) {
        Assigner.Result a = Assigner.assign(cs, it);
        Plan p = Planner.plan(cs, it, a, carry, 1000);
        List<String> problems = PlanChecker.check(cs, p, it, carry);
        if (!problems.isEmpty()) {
            failures++;
            System.out.println("FAIL " + name + ": " + problems);
        }
        checks++;
        return p;
    }

    static void plannerBasicSort() {
        Map<Integer, ItemInfo> it = items("ingot", "dust");
        // chest0: mostly ingots + 1 dust stack; chest1: mostly dust + 1 ingot stack.
        List<ChestModel> cs = Arrays.asList(chest(0, 27, 0, 64, 0, 64, 1, 30), chest(1, 27, 1, 64, 1, 64, 0, 20));
        Plan p = planAndCheck("basic", cs, it, 27);
        check("basic: 2 stacks moved", p.stackMoves() == 2);
        check("basic: chest0 all ingot", p.expected.get(0).totals().keySet().equals(set(0)));
        check("basic: chest1 all dust", p.expected.get(1).totals().keySet().equals(set(1)));
        check("basic: 20 ingots merged into partial", p.expected.get(0).totals().get(0) == 148);
    }

    static void plannerLeavesUnassignedAndFull() {
        Map<Integer, ItemInfo> it = items("ingot", "dust", "plate");
        // One chest, full of ingots; another full of dust with one plate stack. No empty chest for plates.
        ChestModel a = chest(0, 2, 0, 64, 0, 64); // full ingot chest
        ChestModel b = chest(1, 3, 1, 64, 2, 7, 0, 5); // dust chest with a plate stack and 5 ingots
        Plan p = planAndCheck("full", Arrays.asList(a, b), it, 27);
        check("full: ingots cannot move (target full)", p.stackMoves() == 0);
        check("full: plate unassigned", p.unassigned.contains("plate"));
        check("full: one stack left for lack of room", p.stacksLeftNoRoom == 1);
    }

    static void plannerRespectsCarryLimit() {
        Map<Integer, ItemInfo> it = items("ingot", "dust");
        ChestModel a = chest(0, 27, 0, 64, 0, 64, 0, 64, 0, 64, 0, 64, 0, 64, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1);
        ChestModel b = chest(1, 27, 1, 64, 1, 64, 1, 64, 1, 64, 1, 64, 1, 64);
        Plan p = planAndCheck("carry", Arrays.asList(a, b), it, 2);
        check("carry: 5 dust stacks moved", p.stackMoves() == 5);
        for (Plan.Trip t : p.trips) check("carry: trip <= 2", t.picks.size() <= 2);
        check("carry: 3 trips", p.trips.size() == 3);
    }

    static void plannerUsesFreedSlots() {
        Map<Integer, ItemInfo> it = items("ingot", "dust");
        // Both chests full, each holds one stack of the other's category. Classic swap.
        ChestModel a = chest(0, 3, 0, 64, 0, 64, 1, 64);
        ChestModel b = chest(1, 3, 1, 64, 1, 64, 0, 64);
        Plan p = planAndCheck("swap", Arrays.asList(a, b), it, 27);
        // Neither can move first (no room anywhere): stays put. Documented limitation, safe.
        check("swap: nothing moved, nothing lost", p.stackMoves() == 0 && p.stacksLeftNoRoom == 2);
        // With one spare slot in A, B's ingot goes to A, freeing a slot in B for A's dust.
        ChestModel a2 = chest(0, 4, 0, 64, 0, 64, 1, 64);
        ChestModel b2 = chest(1, 3, 1, 64, 1, 64, 0, 64);
        Plan p2 = planAndCheck("swap2", Arrays.asList(a2, b2), it, 27);
        check("swap2: both moved", p2.stackMoves() == 2 && p2.stacksLeftNoRoom == 0);
    }

    static void plannerNeverTouchesWellPlaced() {
        Map<Integer, ItemInfo> it = items("ingot", "dust");
        ChestModel a = chest(0, 27, 0, 64, 0, 3);
        ChestModel b = chest(1, 27, 1, 64, 1, 2);
        Plan p = planAndCheck("sorted", Arrays.asList(a, b), it, 27);
        check("sorted: no trips", p.isEmpty());
    }

    static void randomizedPlans(int worlds) {
        Random rnd = new Random(12345);
        String[] cats = { "ingot", "dust", "plate", "gem", "mod:gregtech", "misc" };
        int moved = 0;
        for (int w = 0; w < worlds; w++) {
            int nItems = 1 + rnd.nextInt(12);
            Map<Integer, ItemInfo> it = new HashMap<>();
            for (int k = 0; k < nItems; k++) {
                int max = rnd.nextInt(5) == 0 ? (rnd.nextBoolean() ? 1 : 16) : 64;
                it.put(k, new ItemInfo(k, "i" + k, cats[rnd.nextInt(cats.length)], max));
            }
            int nChests = 1 + rnd.nextInt(8);
            List<ChestModel> cs = new ArrayList<>();
            for (int c = 0; c < nChests; c++) {
                int slots = rnd.nextBoolean() ? 27 : 1 + rnd.nextInt(10);
                ChestModel m = new ChestModel(c, new BlockPos(rnd.nextInt(20), 64, rnd.nextInt(20)), "c", slots, 64);
                double fill = rnd.nextDouble();
                for (int s = 0; s < slots; s++) {
                    if (rnd.nextDouble() < fill) {
                        int k = rnd.nextInt(nItems);
                        m.set(s, k, 1 + rnd.nextInt(it.get(k).maxStack));
                    }
                }
                cs.add(m);
            }
            int carry = 1 + rnd.nextInt(27);
            Plan p = planAndCheck("random world " + w, cs, it, carry);
            moved += p.stackMoves();
            // Extra invariant: anything still misplaced must truly lack room in every chest of its category.
            Map<String, List<Integer>> owners = new HashMap<>();
            for (Map.Entry<Integer, String> e : p.chestCategory.entrySet()) {
                owners.computeIfAbsent(e.getValue(), x -> new ArrayList<Integer>()).add(e.getKey());
            }
            for (ChestModel c : p.expected.values()) {
                for (int s = 0; s < c.size(); s++) {
                    if (!Planner.isMisplaced(c, s, it, p.chestCategory, owners)) continue;
                    if (p.trips.size() >= 1000) continue;
                    int key = c.keys[s];
                    for (int t : owners.get(it.get(key).category)) {
                        if (p.expected.get(t).roomFor(key, it.get(key).maxStack) >= c.counts[s]) {
                            failures++;
                            System.out.println("FAIL random world " + w + ": stack left behind although chest " + t
                                + " has room");
                        }
                        checks++;
                    }
                }
            }
        }
        check("random worlds moved something", moved > 0);
    }

    static void checkerCatchesBadPlans() {
        Map<Integer, ItemInfo> it = items("ingot", "dust");
        List<ChestModel> cs = Arrays.asList(chest(0, 27, 0, 64, 0, 64, 1, 10), chest(1, 27, 1, 64));
        Assigner.Result a = Assigner.assign(cs, it);
        Plan good = Planner.plan(cs, it, a, 27, 100);
        check("good plan clean", PlanChecker.check(cs, good, it, 27).isEmpty());
        // Wrong count picked.
        Plan.Trip t = good.trips.get(0);
        Plan.Pick p = t.picks.get(0);
        Plan bad = new Plan(Arrays.asList(new Plan.Trip(t.source, Arrays.asList(new Plan.Pick(p.slot, p.key, p.count + 1)),
            t.deposits)), good.chestCategory, good.unassigned, 0, good.expected);
        check("checker: wrong count caught", !PlanChecker.check(cs, bad, it, 27).isEmpty());
        // Deposit to wrong category.
        Plan bad2 = new Plan(Arrays.asList(new Plan.Trip(t.source, t.picks,
            Arrays.asList(new Plan.Deposit(t.source, Arrays.asList(0))))), good.chestCategory, good.unassigned, 0,
            good.expected);
        check("checker: wrong target caught", !PlanChecker.check(cs, bad2, it, 27).isEmpty());
        // Pick never deposited (item would stay in inventory).
        Plan bad3 = new Plan(Arrays.asList(new Plan.Trip(t.source, t.picks, new ArrayList<Plan.Deposit>())),
            good.chestCategory, good.unassigned, 0, good.expected);
        check("checker: undeposited pick caught", !PlanChecker.check(cs, bad3, it, 27).isEmpty());
        // Too many stacks for carry.
        check("checker: carry limit caught", !PlanChecker.check(cs, good, it, 0).isEmpty());
    }

    // ---------------- pathfinder ----------------

    /**
     * Map: list of layers from y=0 upward, each a list of rows (z) of chars (x). '#' solid, '.' open, 'L' lava.
     * Outside the map's x/z bounds is solid up to the top layer; above the top layer everything is open.
     */
    static PathFinder.Terrain terrain(final String[][] layers) {
        return new PathFinder.Terrain() {

            char at(int x, int y, int z) {
                if (y < 0) return '#';
                if (y >= layers.length) return '.';
                String[] rows = layers[y];
                if (z < 0 || z >= rows.length || x < 0 || x >= rows[z].length()) return '#'; // map edge: solid
                return rows[z].charAt(x);
            }

            public boolean open(int x, int y, int z) {
                return at(x, y, z) == '.';
            }

            public boolean floor(int x, int y, int z) {
                return at(x, y, z) == '#';
            }
        };
    }

    static PathFinder.Goal at(final int x, final int y, final int z) {
        return new PathFinder.Goal() {

            public boolean reached(BlockPos f) {
                return f.x == x && f.y == y && f.z == z;
            }

            public double estimate(BlockPos f) {
                return Math.abs(f.x - x) + Math.abs(f.y - y) + Math.abs(f.z - z);
            }
        };
    }

    static void pathFlat() {
        PathFinder.Terrain t = terrain(new String[][] { { "#####" }, { "....." }, { "....." } });
        List<BlockPos> p = PathFinder.find(t, new BlockPos(0, 1, 0), at(4, 1, 0), 1000);
        check("flat: found", p != null && p.size() == 4 && p.get(3).equals(new BlockPos(4, 1, 0)));
    }

    static void pathAroundWall() {
        String[] floor = { "#####", "#####", "#####" };
        String[] wall = { "..#..", "..#..", "....." };
        PathFinder.Terrain t = terrain(new String[][] { floor, wall, wall });
        List<BlockPos> p = PathFinder.find(t, new BlockPos(0, 1, 0), at(4, 1, 0), 1000);
        check("wall: found", p != null);
        if (p != null) {
            boolean usedGap = false;
            for (BlockPos b : p) {
                check("wall: never inside wall", !(b.x == 2 && b.z >= 0 && b.z < 2));
                check("wall: stays on the map", b.z >= 0 && b.z < 3 && b.x >= 0 && b.x < 5);
                if (b.x == 2 && b.z == 2) usedGap = true;
            }
            check("wall: went through gap", usedGap);
        }
    }

    static void pathStepUpAndDrop() {
        // x2 is 1 block high, x3 is 2 high: two 1-block steps up, then a 2-block drop at x4.
        PathFinder.Terrain t = terrain(new String[][] { { "#######" }, { "..##..." }, { "...#..." }, { "......." } });
        List<BlockPos> p = PathFinder.find(t, new BlockPos(0, 1, 0), at(6, 1, 0), 5000);
        check("step/drop: found", p != null);
        if (p != null) {
            check("step/drop: climbed to y3", p.contains(new BlockPos(3, 3, 0)));
            check("step/drop: dropped to y1 at x4", p.contains(new BlockPos(4, 1, 0)));
        }
    }

    static void pathNoCornerCutting() {
        String[] floor = { "###", "###", "###" };
        String[] mid = { "...", ".#.", "..." };
        PathFinder.Terrain t = terrain(new String[][] { floor, mid, mid });
        // From (0,1,0) to (1,1,2)? Diagonal from (0,..,1)->(1,..,2) passes corner of (1,..,1) wall: must not cut.
        List<BlockPos> p = PathFinder.find(t, new BlockPos(0, 1, 1), at(1, 1, 2), 1000);
        check("corner: found", p != null);
        if (p != null) check("corner: did not cut", p.size() == 2);
    }

    static void pathBlockedReturnsNull() {
        String[] floor = { "#####" };
        String[] wall = { "..#.." };
        PathFinder.Terrain t = terrain(new String[][] { floor, wall, wall, wall });
        // Outside the map is open above y0, so close the sides: a 1-wide corridor fenced by solid rows.
        PathFinder.Terrain fenced = new PathFinder.Terrain() {

            public boolean open(int x, int y, int z) {
                return z == 0 && t.open(x, y, z);
            }

            public boolean floor(int x, int y, int z) {
                return z != 0 || t.floor(x, y, z);
            }
        };
        check("blocked: null", PathFinder.find(fenced, new BlockPos(0, 1, 0), at(4, 1, 0), 2000) == null);
    }

    static void pathAvoidsTooHighDrop() {
        // Start on a 5-high pillar in a 1-wide corridor; only way down is a 5-block drop: refused.
        PathFinder.Terrain pillar = new PathFinder.Terrain() {

            public boolean open(int x, int y, int z) {
                if (z != 0) return false;
                if (x == 0) return y > 5;
                return y > 0;
            }

            public boolean floor(int x, int y, int z) {
                return z != 0 || (x == 0 ? y <= 5 : y <= 0);
            }
        };
        check("drop>3 refused", PathFinder.find(pillar, new BlockPos(0, 6, 0), at(3, 1, 0), 2000) == null);
    }

    static void pathAlreadyThere() {
        PathFinder.Terrain t = terrain(new String[][] { { "###" }, { "..." }, { "..." } });
        List<BlockPos> p = PathFinder.find(t, new BlockPos(1, 1, 0), at(1, 1, 0), 10);
        check("already: empty", p != null && p.isEmpty());
        PathFinder.Goal reach = PathFinder.withinReach(new BlockPos(3, 1, 0), 4.0, 1.62);
        check("reach: near ok", reach.reached(new BlockPos(1, 1, 0)));
        check("reach: not inside chest", !reach.reached(new BlockPos(3, 1, 0)));
        check("reach: far no", !reach.reached(new BlockPos(9, 1, 0)));
    }

    // ---------------- helpers ----------------

    static Set<Integer> set(Integer... v) {
        return new HashSet<>(Arrays.asList(v));
    }

    static void eq(Object expected, Object actual) {
        check("expected " + expected + " got " + actual, expected == null ? actual == null : expected.equals(actual));
    }

    static void check(String name, boolean ok) {
        checks++;
        if (!ok) {
            failures++;
            System.out.println("FAIL " + name);
        }
    }
}
