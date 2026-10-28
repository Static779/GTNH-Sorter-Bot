import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.whalestorm.chestsorter.core.Assigner;
import com.whalestorm.chestsorter.core.BlockPos;
import com.whalestorm.chestsorter.core.ChestModel;
import com.whalestorm.chestsorter.core.ItemInfo;
import com.whalestorm.chestsorter.core.Plan;
import com.whalestorm.chestsorter.core.PlanChecker;
import com.whalestorm.chestsorter.core.Planner;
import com.whalestorm.chestsorter.core.Taxonomy;

/**
 * Re-plans a run from its trace file with the current core code, offline. Reads the container list, the item keys
 * and the "Contents #id:" lines (traces from before those lines existed get one stack per key, spread over the
 * chests, which shows the grouping but not the real moves).
 *
 * Usage: tools/replay-trace.sh trace.log [minStacksForOwnChest] [layoutWeight] [groups-file]
 */
public class Replay {

    public static void main(String[] args) throws Exception {
        List<String> lines = Files.readAllLines(Paths.get(args[0]), StandardCharsets.UTF_8);
        int minSplit = args.length > 1 ? Integer.parseInt(args[1]) : 6;
        double layout = args.length > 2 ? Double.parseDouble(args[2]) : 1.0;
        Taxonomy tax = args.length > 3
            ? Taxonomy.parse(Files.readAllLines(Paths.get(args[3]), StandardCharsets.UTF_8).toArray(new String[0]))
            : Taxonomy.defaults();
        for (String p : tax.problems) System.out.println("groups problem: " + p);

        Pattern cont = Pattern.compile("\\]   #(\\d+) (.+?) \\((\\d+) slots\\) at (-?\\d+) (-?\\d+) (-?\\d+)");
        Pattern key = Pattern.compile("\\]   key (\\d+): (.*) \\[([^\\[\\]]*)\\] max (\\d+) ore \\[");
        Pattern contents = Pattern.compile("\\] Contents #(\\d+): (.*)$");
        Map<Integer, ChestModel> chests = new LinkedHashMap<>();
        Map<Integer, ItemInfo> items = new HashMap<>();
        Map<Integer, String> contentLines = new HashMap<>();
        for (String l : lines) {
            Matcher m = cont.matcher(l);
            if (m.find() && !chests.containsKey(Integer.parseInt(m.group(1)))) {
                int id = Integer.parseInt(m.group(1));
                chests.put(id, new ChestModel(id, new BlockPos(Integer.parseInt(m.group(4)),
                    Integer.parseInt(m.group(5)), Integer.parseInt(m.group(6))), m.group(2),
                    Integer.parseInt(m.group(3)), 64));
                continue;
            }
            m = key.matcher(l);
            if (m.find()) {
                int k = Integer.parseInt(m.group(1));
                items.put(k, new ItemInfo(k, m.group(2), m.group(3), Integer.parseInt(m.group(4))));
                continue;
            }
            m = contents.matcher(l);
            if (m.find()) contentLines.put(Integer.parseInt(m.group(1)), m.group(2));
        }
        System.out.println(chests.size() + " containers, " + items.size() + " item keys, " + contentLines.size()
            + " content lines");
        List<ChestModel> surveyed = new ArrayList<>();
        if (!contentLines.isEmpty()) {
            for (Map.Entry<Integer, String> e : contentLines.entrySet()) {
                ChestModel c = chests.get(e.getKey());
                if (c == null) continue;
                if (!e.getValue().startsWith("(")) {
                    for (String t : e.getValue().trim().split(" ")) {
                        String[] p = t.split(":");
                        c.set(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]));
                    }
                }
                surveyed.add(c);
            }
        } else {
            System.out.println("No Contents lines (older trace): one stack per key, spread round-robin.");
            surveyed.addAll(chests.values());
            int i = 0;
            for (int k : new TreeMap<>(items).keySet()) {
                for (int tries = 0; tries < surveyed.size(); tries++, i++) {
                    ChestModel c = surveyed.get(i % surveyed.size());
                    int slot = firstEmpty(c);
                    if (slot >= 0) {
                        c.set(slot, k, Math.min(16, items.get(k).maxStack));
                        i++;
                        break;
                    }
                }
            }
        }
        long t0 = System.nanoTime();
        Assigner.Result a = Assigner.assign(surveyed, items, new HashMap<Integer, String>(),
            new Assigner.Weights(1.0, layout), tax, minSplit);
        long ms = (System.nanoTime() - t0) / 1000000;
        System.out.println("Assigned in " + ms + " ms: " + a.costReport);
        System.out.println("Grouping steps:");
        for (String s : a.buckets.steps) System.out.println("  " + s);
        System.out.println("Buckets (stacks: categories):");
        for (Map.Entry<String, List<String>> e : a.buckets.members.entrySet()) {
            System.out.println("  " + e.getKey() + " (" + a.buckets.demand.get(e.getKey()) + "): " + e.getValue());
        }
        System.out.println("Chests:");
        for (ChestModel c : surveyed) {
            System.out.println("  #" + c.id + " " + c.name + " at " + c.pos + " (" + c.size() + ") -> "
                + a.chestCategory.getOrDefault(c.id, "(unused)"));
        }
        if (!a.unassigned.isEmpty()) System.out.println("UNASSIGNED: " + a.unassigned);
        Plan p = Planner.plan(surveyed, items, a, 27, 10000, surveyed.get(0).pos);
        int chained = 0;
        for (int i = 1; i < p.trips.size(); i++) {
            Plan.Trip prev = p.trips.get(i - 1);
            int last = prev.deposits.isEmpty() ? prev.source : prev.deposits.get(prev.deposits.size() - 1).target;
            if (p.trips.get(i).source == last) chained++;
        }
        System.out.println("Trips starting at the chest the last trip ended at (kept open): " + chained + " of "
            + Math.max(0, p.trips.size() - 1));
        List<String> problems = PlanChecker.check(surveyed, p, items, 27);
        System.out.println("Plan: " + p.trips.size() + " trips, " + p.stackMoves() + " stack moves, "
            + p.stacksLeftNoRoom + " stacks left for lack of room; check " + (problems.isEmpty() ? "OK" : problems));
    }

    static int firstEmpty(ChestModel c) {
        for (int s = 0; s < c.size(); s++) if (c.keys[s] == ChestModel.EMPTY) return s;
        return -1;
    }
}
