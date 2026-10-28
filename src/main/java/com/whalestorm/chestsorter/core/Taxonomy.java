package com.whalestorm.chestsorter.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Two-level category tree, after Inventory Tweaks' category tree and class-based warehouse storage: fine categories
 * (from {@link Categorizer}: "dustTiny", "mod:gregtech:gregtech.main") belong to groups ("dusts", "mod:gregtech"), and
 * groups are listed in layout order so neighbouring groups end up in neighbouring chests.
 *
 * {@link #bucket} decides, for the chests that exist, which categories share chests:
 * <ol>
 * <li>Start with one bucket per group. Groups with fewer than {@code minSplitStacks} stacks are parked in their most
 * related bucket (or "misc"), so the chests go to the big groups first.</li>
 * <li>Too many buckets for the chests: the smallest bucket joins its most related bucket, or the "misc" bucket when
 * nothing is related. Every item still gets a chest.</li>
 * <li>Spare chests: split the biggest part out of a bucket into its own bucket (first whole groups that were merged in,
 * then single fine categories), biggest first, while it still fits the chests and the part has at least
 * {@code minSplitStacks} stacks.</li>
 * <li>A bucket left with fewer than {@code minSplitStacks} stacks after splitting joins its most related split-off
 * part, so leftovers don't take a chest of their own.</li>
 * <li>Chests still spare: parked groups get their own chest back, biggest first.</li>
 * </ol>
 *
 * Group lines: {@code name: member, member*, ...}. A member is a fine category name, or a prefix ending in "*".
 * Exact names win over prefixes; the longest prefix wins among prefixes. A category no line covers goes to "mod:&lt;id&gt;"
 * for mod categories, otherwise forms a group of its own. Pure and deterministic.
 */
public final class Taxonomy {

    public static final String MISC = "misc";

    /** Default groups, in layout order (neighbours are related). */
    public static final String[] DEFAULT_GROUPS = {
        "ores: ore*, rawOre, raw, crushed*, cleanGravel, dirtyGravel, reduced, cluster, clump, shard, crystalline, rubble, chunk",
        "dusts: dustImpure, dustPure, dustRefined, dust, dustSmall, dustTiny, powder, ground, milled",
        "metals: ingot*, nugget*, block, block_, storage, coin",
        "gems: gem*, lens, crystal, coal, charcoal, glowstone, enderpearl, pearl",
        "plates: plate*, foil*, sheet*, itemCasing, plating",
        "parts: stick*, rod, bolt, screw, round, ring, spring*, gear*, rotor, frame*, turbine*, handle*, toolHead*, blockCasing*",
        "wires: wire*, cable*",
        "pipes: pipe*",
        "electronics: circuit*, battery*, wafer, chipset, componentCircuit, wrapCircuit, motor, cover, nanite, computer, reactor, mod:minecraft:redstone",
        "fluids: cell*, capsule*, bucket*, bottle, liquid, plasma, molecule, mod:minecraft:brewing",
        "tools: tool*, craftingTool*, shears, scoop, grafter, armor*, bullet*, bauble, mod:minecraft:tools, mod:minecraft:combat, mod:tconstruct*",
        "wood: log*, plank*, slab*, stair*, fence*, wood*, tree*, door, chest, button, pressure, torch",
        "stone: cobblestone*, stone*, compressed*, gravel, sand*, dirt*, grass, netherrack, marble, obsidian, soulsand, ice, snow, brick*, wall",
        "glass: glass*, blockGlass, paneGlass, stainedGlass, stainedClay",
        "farming: seed*, crop*, plant*, flower*, petal, mushroom, reed, sugarcane, pinecone, beans, fertilizer, bee*, comb, wax, apiaryUpgrade",
        "food: food*, flour, egg, cooking, bowl, mod:minecraft:food, mod:harvestcraft*",
        "mobdrops: bone, string, feather, leather, slimeball, gunpowder, soul*, skull, drop, mod:minecraft:materials",
        "dyes: dye*, blockWool, wool*",
        "books: book, paper, record, tome, rune, mana, essence" };

    private final List<String> groups = new ArrayList<>();
    private final Map<String, Integer> groupIndex = new HashMap<>();
    private final Map<String, String> exact = new HashMap<>();
    /** prefix → group, longest prefix first. */
    private final List<String[]> prefixes = new ArrayList<>();
    /** Lines that could not be read, for the trace. */
    public final List<String> problems = new ArrayList<>();

    public static Taxonomy defaults() {
        return parse(DEFAULT_GROUPS);
    }

    public static Taxonomy parse(String[] lines) {
        Taxonomy t = new Taxonomy();
        if (lines == null) return t;
        for (String raw : lines) {
            if (raw == null) continue;
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            int colon = line.indexOf(':');
            // "mod:x: a, b" has its name before the LAST ": " — find the separator as ": " or a trailing ':'.
            int sep = line.indexOf(": ");
            if (sep < 0 && line.endsWith(":")) sep = line.length() - 1;
            if (sep < 0) sep = colon;
            if (sep <= 0) {
                t.problems.add("no 'name:' in \"" + line + "\"");
                continue;
            }
            String name = line.substring(0, sep)
                .trim();
            if (name.isEmpty()) {
                t.problems.add("empty group name in \"" + line + "\"");
                continue;
            }
            if (t.groupIndex.containsKey(name)) {
                t.problems.add("group \"" + name + "\" listed twice; the first one is used");
                continue;
            }
            t.groupIndex.put(name, t.groups.size());
            t.groups.add(name);
            for (String m : line.substring(sep + 1)
                .split(",")) {
                String member = m.trim();
                if (member.isEmpty()) continue;
                if (member.endsWith("*")) {
                    String p = member.substring(0, member.length() - 1);
                    if (p.isEmpty()) {
                        t.problems.add("\"*\" alone in group \"" + name + "\" ignored");
                        continue;
                    }
                    t.prefixes.add(new String[] { p, name });
                } else if (t.exact.containsKey(member)) {
                    t.problems.add(
                        "\"" + member + "\" is in both \"" + t.exact.get(member) + "\" and \"" + name + "\"; first wins");
                } else {
                    t.exact.put(member, name);
                }
            }
        }
        // Longest prefix first; ties keep line order (stable sort).
        t.prefixes.sort((a, b) -> b[0].length() - a[0].length());
        return t;
    }

    public List<String> groups() {
        return Collections.unmodifiableList(groups);
    }

    /** The group a fine category belongs to. */
    public String groupOf(String category) {
        if (category == null) return MISC;
        if (MISC.equals(category)) return MISC;
        String g = exact.get(category);
        if (g != null) return g;
        for (String[] p : prefixes) if (category.startsWith(p[0])) return p[1];
        String mod = Relations.modOf(category);
        if (mod != null) return "mod:" + mod;
        return category;
    }

    /** The group a bucket name stands for: a bucket is named after a group or after a fine category. */
    String groupOfBucket(String bucket) {
        if (groupIndex.containsKey(bucket)) return bucket;
        return groupOf(bucket);
    }

    /**
     * How related two buckets are, 0..1: same group 0.7..1 (closer along a processing chain scores higher), listed
     * groups one apart 0.6, two apart 0.45, three apart 0.3, four apart 0.15; fine-level chains and same-mod from
     * {@link Relations}. "misc" is related to nothing.
     */
    public double relation(String a, String b) {
        if (a == null || b == null) return 0;
        if (a.equals(b)) return 1;
        if (MISC.equals(a) || MISC.equals(b)) return 0;
        String ga = groupOfBucket(a), gb = groupOfBucket(b);
        double fine = Relations.score(a, b);
        if (ga.equals(gb)) return 0.7 + 0.3 * fine;
        double best = fine;
        Integer ia = groupIndex.get(ga), ib = groupIndex.get(gb);
        if (ia != null && ib != null) {
            int gap = Math.abs(ia - ib);
            if (gap <= 4) best = Math.max(best, 0.6 - 0.15 * (gap - 1));
        }
        return best;
    }

    /** Which bucket each fine category goes to, and why. */
    public static final class Buckets {

        /** fine category → bucket name. */
        public final Map<String, String> bucketOf;
        /** bucket → its fine categories (sorted). */
        public final Map<String, List<String>> members;
        /** bucket → stacks, after packing each item's stacks full. */
        public final Map<String, Integer> demand;
        /** Each merge and split, in order, for the trace. */
        public final List<String> steps;

        Buckets(Map<String, String> bucketOf, Map<String, List<String>> members, Map<String, Integer> demand,
            List<String> steps) {
            this.bucketOf = bucketOf;
            this.members = members;
            this.demand = demand;
            this.steps = steps;
        }
    }

    /**
     * @param fineDemand     stacks per fine category
     * @param chestCount     chests available
     * @param avgSlots       average slots per chest (for "how many chests does this need")
     * @param minSplitStacks smallest bucket worth its own chest when there are spare chests
     */
    public Buckets bucket(Map<String, Integer> fineDemand, int chestCount, double avgSlots, int minSplitStacks) {
        double avg = Math.max(1, avgSlots);
        List<String> steps = new ArrayList<>();
        // bucket → fine categories in it
        TreeMap<String, TreeSet<String>> b = new TreeMap<>();
        for (Map.Entry<String, Integer> e : new TreeMap<>(fineDemand).entrySet()) {
            String g = groupOf(e.getKey());
            b.computeIfAbsent(g, x -> new TreeSet<>())
                .add(e.getKey());
        }
        if (chestCount < 1) return result(b, fineDemand, steps);

        // ---- 0. park small groups so big groups get chests first (step 5 gives them back if chests are spare) ----
        while (true) {
            String small = null;
            int sd = 0;
            for (Map.Entry<String, TreeSet<String>> e : b.entrySet()) {
                if (MISC.equals(e.getKey())) continue;
                int v = d(e.getValue(), fineDemand);
                if (v < minSplitStacks && (small == null || v < sd)) {
                    small = e.getKey();
                    sd = v;
                }
            }
            if (small == null || b.size() <= 1) break;
            String target = mostRelated(small, b, fineDemand);
            if (target == null) target = MISC;
            steps.add("park " + small + " (" + sd + " stacks) in " + target);
            TreeSet<String> moved = b.remove(small);
            b.computeIfAbsent(target, x -> new TreeSet<>())
                .addAll(moved);
        }

        // ---- 1. too many buckets: smallest joins its most related, else misc ----
        while (b.size() > chestCount) {
            String small = smallest(b, fineDemand, true);
            if (small == null) small = smallest(b, fineDemand, false);
            String target = mostRelated(small, b, fineDemand);
            if (target == null) {
                if (MISC.equals(small)) break; // only misc is unrelated to all; cannot happen with size > 1
                target = MISC;
            }
            steps.add("merge " + small + " (" + d(b.get(small), fineDemand) + " stacks) -> " + target);
            b.computeIfAbsent(target, x -> new TreeSet<>())
                .addAll(b.remove(small));
        }

        // ---- 2. still more chests needed than exist: merge where it saves a chest ----
        while (need(b, fineDemand, avg) > chestCount) {
            boolean done = false;
            List<String> order = new ArrayList<>(b.keySet());
            order.sort((x, y) -> {
                int dx = d(b.get(x), fineDemand), dy = d(b.get(y), fineDemand);
                return dx != dy ? dx - dy : x.compareTo(y);
            });
            for (String small : order) {
                String target = mostRelated(small, b, fineDemand);
                if (target == null && !MISC.equals(small) && b.containsKey(MISC)) target = MISC;
                if (target == null) continue;
                int ds = d(b.get(small), fineDemand), dt = d(b.get(target), fineDemand);
                if (need(ds + dt, avg) < need(ds, avg) + need(dt, avg)) {
                    // The bigger side keeps its name ("dusts" holding a few misc items, not "misc" holding dusts).
                    String from = ds <= dt ? small : target, into = ds <= dt ? target : small;
                    steps.add(
                        "merge " + from + " (" + Math.min(ds, dt) + " stacks) -> " + into + " to save a chest");
                    b.get(into)
                        .addAll(b.remove(from));
                    done = true;
                    break;
                }
            }
            if (!done) break;
        }

        // ---- 3. spare chests: split the biggest part out, biggest first ----
        Map<String, String> splitFrom = new HashMap<>();
        split(b, fineDemand, chestCount, avg, minSplitStacks, false, splitFrom, steps);

        // ---- 4. tiny leftovers of a split bucket join their most related split-off part ----
        for (String bucket : new ArrayList<>(b.keySet())) {
            if (!b.containsKey(bucket) || !splitFrom.containsValue(bucket)) continue;
            int left = d(b.get(bucket), fineDemand);
            if (left >= minSplitStacks) continue;
            String target = null;
            double best = -1;
            for (Map.Entry<String, String> e : new TreeMap<>(splitFrom).entrySet()) {
                if (!e.getValue()
                    .equals(bucket) || !b.containsKey(e.getKey())) continue;
                double r = 0;
                for (String f : b.get(bucket)) r = Math.max(r, relation(f, e.getKey()));
                if (r > best) {
                    best = r;
                    target = e.getKey();
                }
            }
            if (target == null) continue;
            steps.add("leftover " + bucket + " (" + left + " stacks) -> " + target);
            b.get(target)
                .addAll(b.remove(bucket));
        }

        // ---- 5. chests still spare: parked groups get their own chest back, biggest first ----
        split(b, fineDemand, chestCount, avg, 1, true, new HashMap<String, String>(), steps);
        return result(b, fineDemand, steps);
    }

    /**
     * Splits the biggest part out of a bucket while the chests allow, biggest first. Parts are whole groups when a
     * bucket mixes groups, else single fine categories; {@code groupsOnly} limits it to whole groups.
     */
    private void split(TreeMap<String, TreeSet<String>> b, Map<String, Integer> fineDemand, int chestCount, double avg,
        int minStacks, boolean groupsOnly, Map<String, String> splitFrom, List<String> steps) {
        while (true) {
            String bestFrom = null, bestName = null;
            TreeSet<String> bestPart = null;
            int bestD = 0;
            int now = need(b, fineDemand, avg);
            for (Map.Entry<String, TreeSet<String>> e : b.entrySet()) {
                String bucket = e.getKey();
                TreeSet<String> fines = e.getValue();
                if (fines.size() < 2) continue;
                // Parts: whole groups when the bucket mixes groups, else single fine categories.
                TreeMap<String, TreeSet<String>> parts = new TreeMap<>();
                for (String f : fines) parts.computeIfAbsent(groupOf(f), x -> new TreeSet<>())
                    .add(f);
                boolean byGroup = parts.size() > 1;
                if (!byGroup && groupsOnly) continue;
                if (!byGroup) {
                    parts.clear();
                    for (String f : fines) parts.put(f, new TreeSet<>(Collections.singleton(f)));
                }
                for (Map.Entry<String, TreeSet<String>> p : parts.entrySet()) {
                    String name = p.getKey();
                    if (byGroup && name.equals(groupOfBucket(bucket))) continue; // the bucket's own group stays
                    if (b.containsKey(name)) continue;
                    int dp = d(p.getValue(), fineDemand), dall = d(fines, fineDemand);
                    if (dp < minStacks || dp >= dall) continue;
                    int after = now - need(dall, avg) + need(dp, avg) + need(dall - dp, avg);
                    if (after > chestCount) continue;
                    if (dp > bestD || (dp == bestD && bestName != null && name.compareTo(bestName) < 0)) {
                        bestD = dp;
                        bestFrom = bucket;
                        bestName = name;
                        bestPart = p.getValue();
                    }
                }
            }
            if (bestFrom == null) break;
            b.get(bestFrom)
                .removeAll(bestPart);
            b.put(bestName, new TreeSet<>(bestPart));
            splitFrom.put(bestName, bestFrom);
            steps.add("split " + bestName + " (" + bestD + " stacks) out of " + bestFrom);
        }
    }

    private Buckets result(TreeMap<String, TreeSet<String>> b, Map<String, Integer> fineDemand, List<String> steps) {
        // A group-named bucket left holding only another group's categories takes that group's name.
        for (String bucket : new ArrayList<>(b.keySet())) {
            TreeSet<String> fines = b.get(bucket);
            if (fines == null || fines.isEmpty() || fines.contains(bucket)) continue;
            TreeSet<String> gs = new TreeSet<>();
            for (String f : fines) gs.add(groupOf(f));
            if (gs.size() != 1) continue;
            String g = gs.first();
            if (g.equals(bucket) || b.containsKey(g)) continue;
            b.put(g, b.remove(bucket));
            steps.add("rename " + bucket + " -> " + g + " (only holds " + g + ")");
        }
        Map<String, String> bucketOf = new TreeMap<>();
        Map<String, List<String>> members = new LinkedHashMap<>();
        Map<String, Integer> demand = new TreeMap<>();
        for (Map.Entry<String, TreeSet<String>> e : b.entrySet()) {
            for (String f : e.getValue()) bucketOf.put(f, e.getKey());
            members.put(e.getKey(), new ArrayList<>(e.getValue()));
            demand.put(e.getKey(), d(e.getValue(), fineDemand));
        }
        return new Buckets(bucketOf, members, demand, steps);
    }

    private String smallest(Map<String, TreeSet<String>> b, Map<String, Integer> fineDemand, boolean skipMisc) {
        String small = null;
        int sd = 0;
        for (Map.Entry<String, TreeSet<String>> e : b.entrySet()) {
            if (skipMisc && MISC.equals(e.getKey())) continue;
            int v = d(e.getValue(), fineDemand);
            if (small == null || v < sd) {
                small = e.getKey();
                sd = v;
            }
        }
        return small;
    }

    /** The bucket most related to {@code from} (ties: bigger, then alphabetical), or null if none is related. */
    private String mostRelated(String from, Map<String, TreeSet<String>> b, Map<String, Integer> fineDemand) {
        String best = null;
        double br = 0;
        int bd = 0;
        for (Map.Entry<String, TreeSet<String>> e : b.entrySet()) {
            String t = e.getKey();
            if (t.equals(from)) continue;
            double r = relation(from, t);
            int dt = d(e.getValue(), fineDemand);
            if (r > br + 1e-9 || (r > 0 && Math.abs(r - br) <= 1e-9 && dt > bd)) {
                best = t;
                br = r;
                bd = dt;
            }
        }
        return best;
    }

    private static int d(Iterable<String> fines, Map<String, Integer> fineDemand) {
        int s = 0;
        for (String f : fines) {
            Integer v = fineDemand.get(f);
            if (v != null) s += v;
        }
        return s;
    }

    static int need(int stacks, double avg) {
        return Math.max(1, (int) Math.ceil(stacks / avg - 1e-9));
    }

    private static int need(Map<String, TreeSet<String>> b, Map<String, Integer> fineDemand, double avg) {
        int s = 0;
        for (TreeSet<String> v : b.values()) s += need(d(v, fineDemand), avg);
        return s;
    }
}
