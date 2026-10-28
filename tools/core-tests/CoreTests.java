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
import com.whalestorm.chestsorter.core.Taxonomy;

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
        assignerMergesIntoRelative();
        assignerUnrelatedSmallStays();
        assignerBigCategoryTogether();
        assignerRelatedNearby();
        assignerDeterministicAndFast();
        relations();
        taxonomyParse();
        taxonomyBuckets();
        taxonomyAssignEveryItemGetsAChest();
        taxonomyManyCategories();
        // planner
        plannerBasicSort();
        plannerLeavesUnassignedAndFull();
        plannerRespectsCarryLimit();
        plannerUsesFreedSlots();
        plannerNeverTouchesWellPlaced();
        randomizedPlans(3000);
        tax = Taxonomy.defaults();
        for (int m : new int[] { 1, 3, 8 }) {
            minSplit = m;
            randomizedPlans(1000);
        }
        tax = null;
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
        // Recipe-group names are ignored in favour of real ones, or fall through to mod + tab.
        eq("seed", c.categorize(Arrays.asList("listAllseed", "seedCotton"), "harvestcraft"));
        eq("book", c.categorize(Arrays.asList("bookEmpty", "craftingBook"), "minecraft"));
        eq("mod:minecraft:redstone", c.categorize(Arrays.asList("craftingPiston"), "minecraft", "redstone"));
        eq("mod:gregtech:gregtech.main", c.categorize(new ArrayList<String>(), "gregtech", "GregTech.Main"));
        eq("craftingTool", c.categorize(Arrays.asList("craftingToolWrench"), "gregtech", "x"));
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
        // One chest, two unrelated equal-size categories: the alphabetically first ("dust") has no relative to merge
        // into, so it stays where it is and the chest becomes "ingot".
        Assigner.Result r3 = Assigner.assign(Arrays.asList(chest(0, 27, 1, 1, 0, 1)), it);
        eq("ingot", r3.chestCategory.get(0));
        check("tie: dust unassigned", r3.unassigned.equals(Arrays.asList("dust")));
    }

    static void assignerLocked() {
        Map<Integer, ItemInfo> it = items("ingot", "dust");
        Map<Integer, String> locked = new HashMap<>();
        locked.put(0, "dust");
        Assigner.Result r = Assigner.assign(Arrays.asList(chest(0, 27, 0, 64), chest(1, 27)), it, locked);
        eq("dust", r.chestCategory.get(0));
        eq("ingot", r.chestCategory.get(1));
    }

    /** A chest at an explicit position. */
    static ChestModel chestAt(int id, int x, int slots, int... keyCount) {
        ChestModel c = new ChestModel(id, new BlockPos(x, 64, 0), "c", slots, 64);
        for (int i = 0, s = 0; i < keyCount.length; i += 2, s++) c.set(s, keyCount[i], keyCount[i + 1]);
        return c;
    }

    static void assignerMergesIntoRelative() {
        Map<Integer, ItemInfo> it = items("dust", "ingot", "dustTiny");
        // Two chests, three categories: dustTiny (smallest) merges into its relative dust.
        List<ChestModel> cs = Arrays.asList(chest(0, 27, 0, 64, 0, 64, 0, 64), chest(1, 27, 1, 64, 1, 64, 2, 7));
        Plan p = planAndCheck("merge", cs, it, 27);
        eq("dust", lastAssign.merged.get("dustTiny"));
        check("merge: nothing unassigned", lastAssign.unassigned.isEmpty());
        check("merge: tiny dust moved into the dust chest", p.expected.get(0).totals().containsKey(2)
            && !p.expected.get(1).totals().containsKey(2));
    }

    static void assignerUnrelatedSmallStays() {
        Map<Integer, ItemInfo> it = items("mod:a", "mod:b", "misc");
        List<ChestModel> cs = Arrays.asList(chest(0, 27, 0, 64, 0, 64, 2, 1), chest(1, 27, 1, 64));
        Plan p = planAndCheck("unrelated", cs, it, 27);
        check("unrelated: misc stays put", lastAssign.unassigned.equals(Arrays.asList("misc")));
        check("unrelated: misc not moved", p.expected.get(0).totals().containsKey(2));
    }

    static void assignerBigCategoryTogether() {
        Map<Integer, ItemInfo> it = items("alpha", "beta", "gamma");
        // 4 chests of 3 slots in a row (x = 0,2,4,6). alpha needs 2 chests but starts in chests 0 and 2.
        List<ChestModel> cs = Arrays.asList(
            chestAt(0, 0, 3, 0, 64, 0, 64, 0, 64),
            chestAt(1, 2, 3, 1, 64),
            chestAt(2, 4, 3, 0, 64, 0, 64),
            chestAt(3, 6, 3, 2, 64));
        Assigner.Result r = Assigner.assign(cs, it);
        eq("alpha", r.chestCategory.get(0));
        eq("alpha", r.chestCategory.get(1)); // pulled next to chest 0
        Plan p = Planner.plan(cs, it, r, 27, 100);
        check("together: plan clean", PlanChecker.check(cs, p, it, 27).isEmpty());
        // layoutWeight 0: only moves count, so nothing is rearranged.
        Assigner.Result r0 = Assigner.assign(cs, it, new HashMap<Integer, String>(), new Assigner.Weights(1, 0));
        eq("alpha", r0.chestCategory.get(0));
        eq("alpha", r0.chestCategory.get(2));
        eq("beta", r0.chestCategory.get(1));
    }

    static void assignerRelatedNearby() {
        Map<Integer, ItemInfo> it = items("crushed", "ingot", "crushedPurified");
        // crushed at x=0, ingot chest at x=2 also holding a crushedPurified stack, empty chests at x=6 and x=4.
        // Listed x=6 first, so the plain "first empty chest" would be x=6; related-nearby should pick x=4.
        List<ChestModel> cs = Arrays.asList(
            chestAt(0, 0, 27, 0, 64),
            chestAt(1, 2, 27, 1, 64, 1, 64, 2, 64),
            chestAt(3, 6, 27),
            chestAt(2, 4, 27));
        Assigner.Result r = Assigner.assign(cs, it);
        eq("crushedPurified", r.chestCategory.get(2));
        check("related: far chest unused", !r.chestCategory.containsKey(3));
    }

    static void assignerDeterministicAndFast() {
        Random rnd = new Random(7);
        String[] cats = { "ore", "rawOre", "crushed", "crushedPurified", "dustImpure", "dust", "dustTiny", "ingot",
            "nugget", "plate", "gem", "mod:minecraft:redstone", "mod:minecraft:food", "mod:gregtech:main", "misc" };
        Map<Integer, ItemInfo> it = new HashMap<>();
        for (int k = 0; k < 80; k++) it.put(k, new ItemInfo(k, "i" + k, cats[rnd.nextInt(cats.length)], 64));
        List<ChestModel> cs = new ArrayList<>();
        for (int c = 0; c < 60; c++) {
            ChestModel m = new ChestModel(c, new BlockPos(c % 10, 60 + c / 10, 0), "c", 54, 64);
            for (int s = 0; s < 54; s++) if (rnd.nextInt(3) > 0) m.set(s, rnd.nextInt(80), 1 + rnd.nextInt(64));
            cs.add(m);
        }
        long t0 = System.nanoTime();
        Assigner.Result a = Assigner.assign(cs, it);
        long ms = (System.nanoTime() - t0) / 1000000;
        Assigner.Result b = Assigner.assign(cs, it);
        check("deterministic", a.chestCategory.equals(b.chestCategory) && a.merged.equals(b.merged));
        System.out.println("  60-chest assignment took " + ms + " ms; " + a.costReport);
        check("60 chests assigned in under 3 s (took " + ms + " ms)", ms < 3000);
        Plan p = Planner.plan(cs, it, a, 20, 1000);
        List<String> problems = PlanChecker.check(cs, p, it, 20);
        check("60-chest plan clean " + problems, problems.isEmpty());
    }

    static void relations() {
        check("rel chain neighbours", com.whalestorm.chestsorter.core.Relations.score("crushed", "crushedPurified") > 0.8);
        check("rel chain far ends low", com.whalestorm.chestsorter.core.Relations.score("ore", "dustTiny") <= 0.3 + 1e-9);
        check("rel nested", com.whalestorm.chestsorter.core.Relations.score("plateDouble", "plate") >= 0.8);
        check("rel same mod", com.whalestorm.chestsorter.core.Relations.score("mod:gregtech:main", "mod:gregtech") == 0.6);
        check("rel unrelated", com.whalestorm.chestsorter.core.Relations.score("ingot", "gem") == 0);
        check("rel not a word prefix", com.whalestorm.chestsorter.core.Relations.score("dust", "dusty") == 0);
    }

    // ---------------- taxonomy ----------------

    static void taxonomyParse() {
        Taxonomy t = Taxonomy.defaults();
        check("defaults parse cleanly " + t.problems, t.problems.isEmpty());
        eq("dusts", t.groupOf("dustTiny"));
        eq("ores", t.groupOf("ore"));
        eq("ores", t.groupOf("crushedPurified"));
        eq("parts", t.groupOf("toolHeadFile")); // longer prefix "toolHead*" beats "tool*"
        eq("tools", t.groupOf("toolSword"));
        eq("glass", t.groupOf("blockGlass")); // exact beats nothing; "block" alone is metals
        eq("metals", t.groupOf("block"));
        eq("food", t.groupOf("mod:minecraft:food"));
        eq("mod:gregtech", t.groupOf("mod:gregtech:gregtech.main"));
        eq("mod:gregtech", t.groupOf("mod:gregtech"));
        eq("anti", t.groupOf("anti"));
        eq("misc", t.groupOf("misc"));
        Taxonomy bad = Taxonomy.parse(new String[] { "no colon here", "a: x, y*", "a: z", "b: x", "# comment", "",
            "c: *" });
        check("bad lines reported " + bad.problems, bad.problems.size() == 4);
        eq("a", bad.groupOf("x")); // first listing wins
        eq("a", bad.groupOf("yes"));
        eq(Arrays.asList("a", "b", "c"), bad.groups());
        check("rel same group high", t.relation("dust", "dustTiny") >= 0.7);
        check("rel same group vs bucket", t.relation("dusts", "dustTiny") >= 0.7);
        check("rel neighbour groups", Math.abs(t.relation("ores", "dusts") - 0.6) < 1e-9);
        check("rel far groups 0", t.relation("ores", "books") == 0);
        check("rel misc 0", t.relation("misc", "dust") == 0);
        check("rel same mod group", t.relation("mod:gregtech:a", "mod:gregtech:b") >= 0.7);
    }

    static void taxonomyBuckets() {
        Taxonomy t = Taxonomy.defaults();
        Map<String, Integer> d = new HashMap<>();
        d.put("ore", 40);
        d.put("rawOre", 10);
        d.put("crushed", 30);
        d.put("dust", 20);
        d.put("dustTiny", 3);
        d.put("ingot", 25);
        d.put("nugget", 2);
        Taxonomy.Buckets b = t.bucket(d, 10, 27, 6);
        eq("ore", b.bucketOf.get("ore"));
        eq("crushed", b.bucketOf.get("crushed"));
        eq("ores", b.bucketOf.get("rawOre"));
        eq("ingot", b.bucketOf.get("nugget")); // 2-stack leftover joins its split-off sibling
        eq("dust", b.bucketOf.get("dustTiny"));
        eq(5, b.members.size());
        // Few chests: stay at group level.
        Taxonomy.Buckets g = t.bucket(d, 3, 27, 6);
        eq("ores", g.bucketOf.get("ore"));
        eq("dusts", g.bucketOf.get("dust"));
        eq("metals", g.bucketOf.get("ingot"));
        // Fewer chests than groups: smallest group joins its neighbour.
        Taxonomy.Buckets m = t.bucket(d, 2, 54, 6);
        check("2 chests -> 2 buckets " + m.members.keySet(), m.members.size() == 2);
        eq("ores", m.bucketOf.get("dust")); // equally related to ores and metals: joins the bigger one
        eq("metals", m.bucketOf.get("ingot"));
        // Unrelated small groups collect in misc instead of being left out.
        Map<String, Integer> u = new HashMap<>(d);
        u.put("anti", 1);
        u.put("mod:x:a", 1);
        u.put("mod:y", 1);
        Taxonomy.Buckets mu = t.bucket(u, 4, 27, 6);
        eq("misc", mu.bucketOf.get("anti"));
        eq("misc", mu.bucketOf.get("mod:x:a"));
        eq("misc", mu.bucketOf.get("mod:y"));
        check("every category has a bucket", mu.bucketOf.keySet().equals(u.keySet()));
        // Deterministic.
        check("buckets deterministic", t.bucket(u, 4, 27, 6).bucketOf.equals(mu.bucketOf));
        // Small world, plenty of chests: small groups are parked first but get their own chest back.
        Map<String, Integer> small = new HashMap<>();
        small.put("dust", 2);
        small.put("ingot", 1);
        small.put("mod:x:a", 3);
        Taxonomy.Buckets sb = t.bucket(small, 5, 27, 6);
        check("small world: 3 buckets " + sb.members, sb.members.size() == 3);
        eq("dusts", sb.bucketOf.get("dust"));
        eq("metals", sb.bucketOf.get("ingot"));
        eq("mod:x", sb.bucketOf.get("mod:x:a"));
        // Tight: 2 chests, 3 small groups -> two buckets, every category still has one.
        Taxonomy.Buckets tb = t.bucket(small, 2, 27, 6);
        check("tight small world: 2 buckets " + tb.members, tb.members.size() == 2);
        check("tight: all covered", tb.bucketOf.keySet().equals(small.keySet()));
        // Big groups get chests before tiny ones: 3 chests, a 60-stack dust group and two 2-stack mods.
        Map<String, Integer> bigFirst = new HashMap<>();
        bigFirst.put("dust", 30);
        bigFirst.put("dustImpure", 30);
        bigFirst.put("mod:a:x", 2);
        bigFirst.put("mod:b:x", 2);
        Taxonomy.Buckets bf = t.bucket(bigFirst, 5, 27, 6);
        check("big first: dust split before tiny mods " + bf.members,
            !bf.bucketOf.get("dust").equals(bf.bucketOf.get("dustImpure")));
        eq("misc", bf.bucketOf.get("mod:a:x"));
        eq("misc", bf.bucketOf.get("mod:b:x"));
        Taxonomy.Buckets bf6 = t.bucket(bigFirst, 6, 27, 6);
        check("6 chests: one tiny mod gets its chest back " + bf6.members, bf6.members.size() == 4);
        // Too few chests: the merged bucket keeps the bigger side's name.
        Taxonomy.Buckets bf3 = t.bucket(bigFirst, 3, 27, 6);
        eq("dusts", bf3.bucketOf.get("mod:a:x"));
        // Huge minimum: no splits at all.
        eq("ores", t.bucket(d, 10, 27, 1000).bucketOf.get("ore"));
    }

    static void taxonomyAssignEveryItemGetsAChest() {
        tax = Taxonomy.defaults();
        Map<Integer, ItemInfo> it = items("mod:a", "mod:b", "misc");
        List<ChestModel> cs = Arrays.asList(chest(0, 27, 0, 64, 0, 64, 2, 1), chest(1, 27, 1, 64));
        Plan p = planAndCheck("tax unrelated", cs, it, 27);
        check("tax: nothing unassigned " + lastAssign.unassigned, lastAssign.unassigned.isEmpty());
        check("tax: mod:b shares misc", lastAssign.effective("mod:b").equals("misc"));
        tax = null;
    }

    /** Like the 2026-10-28 trace: ~170 small categories, 48 chests. Everything must get a chest. */
    static void taxonomyManyCategories() {
        Random rnd = new Random(48);
        Taxonomy t = Taxonomy.defaults();
        List<String> cats = new ArrayList<>();
        String[] base = { "ore", "rawOre", "crushed", "crushedPurified", "dustImpure", "dustPure", "dust", "dustSmall",
            "dustTiny", "ingot", "nugget", "block", "gem", "gemFlawed", "plate", "stick", "bolt", "screw", "gearGt",
            "pipeSmall", "pipeLarge", "cableGt01", "cell", "bucket", "log", "plank", "slab", "cobblestone", "stone",
            "sand", "seed", "crop", "flower", "food", "dye", "bone", "string", "book", "anti", "ardite", "drawer" };
        cats.addAll(Arrays.asList(base));
        for (int i = 0; i < 60; i++) cats.add("mod:m" + (i % 25) + ":tab" + i);
        Map<Integer, ItemInfo> it = new HashMap<>();
        for (int k = 0; k < 400; k++) it.put(k, new ItemInfo(k, "i" + k, cats.get(rnd.nextInt(cats.size())), 64));
        List<ChestModel> cs = new ArrayList<>();
        for (int c = 0; c < 48; c++) {
            ChestModel m = new ChestModel(c, new BlockPos(c % 12, 60 + c / 12, 0), "c", c % 3 == 0 ? 54 : 27, 64);
            for (int s = 0; s < m.size(); s++) if (rnd.nextInt(5) < 2) m.set(s, rnd.nextInt(400), 1 + rnd.nextInt(64));
            cs.add(m);
        }
        long t0 = System.nanoTime();
        Assigner.Result a = Assigner.assign(cs, it, new HashMap<Integer, String>(), Assigner.Weights.DEFAULT, t, 6);
        long ms = (System.nanoTime() - t0) / 1000000;
        System.out.println("  48-chest taxonomy assignment: " + a.buckets.members.size() + " buckets in " + ms
            + " ms; " + a.costReport);
        check("many: nothing unassigned " + a.unassigned, a.unassigned.isEmpty());
        check("many: buckets <= chests", a.buckets.members.size() <= 48);
        check("many: every bucket has a chest", new HashSet<>(a.chestCategory.values()).containsAll(
            a.buckets.members.keySet()));
        check("many: under 3 s", ms < 3000);
        Plan p = Planner.plan(cs, it, a, 30, 1000);
        List<String> problems = PlanChecker.check(cs, p, it, 30);
        check("many: plan clean " + problems, problems.isEmpty());
        check("many: nothing left without room (" + p.stacksLeftNoRoom + ")", p.stacksLeftNoRoom == 0);
    }

    // ---------------- planner ----------------

    static Assigner.Result lastAssign;
    /** When set, planAndCheck groups categories with this taxonomy first. */
    static Taxonomy tax = null;
    static int minSplit = 6;

    static Plan planAndCheck(String name, List<ChestModel> cs, Map<Integer, ItemInfo> it, int carry) {
        Assigner.Result a = tax == null ? Assigner.assign(cs, it)
            : Assigner.assign(cs, it, new HashMap<Integer, String>(), Assigner.Weights.DEFAULT, tax, minSplit);
        lastAssign = a;
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
        // Three categories, two chests, none related: the smallest alphabetically (dust) stays where it is.
        check("full: dust unassigned", p.unassigned.equals(Arrays.asList("dust")));
        eq("ingot", p.chestCategory.get(0));
        eq("plate", p.chestCategory.get(1));
        check("full: ingots cannot move (target full)", p.stackMoves() == 0);
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
        String[] cats = { "ingot", "nugget", "dust", "dustTiny", "crushed", "plate", "gem", "mod:gregtech",
            "mod:gregtech:main", "misc" };
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
                    if (!Planner.isMisplaced(c, s, it, lastAssign, owners)) continue;
                    if (p.trips.size() >= 1000) continue;
                    int key = c.keys[s];
                    for (int t : owners.get(lastAssign.effective(it.get(key).category))) {
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
            t.deposits)), good.chestCategory, good.unassigned, good.merged, 0, good.expected);
        check("checker: wrong count caught", !PlanChecker.check(cs, bad, it, 27).isEmpty());
        // Deposit to wrong category.
        Plan bad2 = new Plan(Arrays.asList(new Plan.Trip(t.source, t.picks,
            Arrays.asList(new Plan.Deposit(t.source, Arrays.asList(0))))), good.chestCategory, good.unassigned,
            good.merged, 0, good.expected);
        check("checker: wrong target caught", !PlanChecker.check(cs, bad2, it, 27).isEmpty());
        // Pick never deposited (item would stay in inventory).
        Plan bad3 = new Plan(Arrays.asList(new Plan.Trip(t.source, t.picks, new ArrayList<Plan.Deposit>())),
            good.chestCategory, good.unassigned, good.merged, 0, good.expected);
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
