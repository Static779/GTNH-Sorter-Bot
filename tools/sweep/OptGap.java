import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Random;

import com.whalestorm.chestsorter.core.Assigner;
import com.whalestorm.chestsorter.core.BlockPos;
import com.whalestorm.chestsorter.core.ChestModel;
import com.whalestorm.chestsorter.core.ItemInfo;
import com.whalestorm.chestsorter.core.Plan;
import com.whalestorm.chestsorter.core.Planner;

/**
 * How far the planner's route is from the truly shortest one, on rooms small enough to solve exactly: 3-6 chests,
 * up to 8 stacks to move, 1-3 carry slots, plenty of room. The exact walk comes from a shortest-path search over every
 * state (where you stand, and for each stack: still in its chest, carried, or delivered to any chest of its group).
 * Both use the same chest assignment and straight-line distances between chest centres.
 *
 * Usage: java OptGap [rooms]
 */
public class OptGap {

    public static void main(String[] args) {
        int rooms = args.length > 0 ? Integer.parseInt(args[0]) : 500;
        Random rnd = new Random(7);
        List<Double> ratios = new ArrayList<>();
        int exactBetter = 0, equal = 0, skipped = 0;
        double worst = 1;
        for (int r = 0; r < rooms; r++) {
            int n = 3 + rnd.nextInt(4), cats = 2 + rnd.nextInt(Math.min(3, n - 1)), carry = 1 + rnd.nextInt(3);
            Map<Integer, ItemInfo> items = new HashMap<>();
            for (int k = 0; k < cats; k++) items.put(k, new ItemInfo(k, "i" + k, "cat" + k, 64));
            List<ChestModel> chests = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                chests.add(new ChestModel(i, new BlockPos(rnd.nextInt(14), 60, rnd.nextInt(14)), "c" + i, 27, 64));
            }
            // each chest mostly one category (so the assignment is clear), plus a few stray stacks
            int stray = 0;
            for (ChestModel c : chests) {
                int home = c.id % cats, s = 0;
                for (int j = 0; j < 3; j++) c.set(s++, home, 64);
                int extra = rnd.nextInt(3);
                for (int j = 0; j < extra && stray < 8; j++, stray++) c.set(s++, (home + 1 + rnd.nextInt(cats - 1)) % cats, 64);
            }
            Assigner.Result a = Assigner.assign(chests, items);
            BlockPos start = new BlockPos(rnd.nextInt(14), 60, rnd.nextInt(14));
            Plan plan = Planner.plan(chests, items, a, carry, 1000, start);
            if (plan.stacksLeftNoRoom > 0 || !a.unassigned.isEmpty()) {
                skipped++;
                continue;
            }
            double greedy = 0;
            BlockPos at = start;
            for (Plan.Trip t : plan.trips) {
                for (Plan.Visit v : t.visits) {
                    greedy += d(at, chests.get(v.chest).pos);
                    at = chests.get(v.chest).pos;
                }
            }
            // stacks to move: source chest and group
            List<int[]> moves = new ArrayList<>();
            for (ChestModel c : chests) {
                for (int s = 0; s < c.size(); s++) {
                    if (c.keys[s] == ChestModel.EMPTY) continue;
                    String b = a.effective(items.get(c.keys[s]).category);
                    if (!b.equals(a.chestCategory.get(c.id))) moves.add(new int[] { c.id, c.keys[s] });
                }
            }
            if (moves.size() > 9) {
                skipped++;
                continue;
            }
            double opt = exact(chests, a, items, moves, carry, start);
            if (opt <= 1e-9) {
                if (greedy <= 1e-9) equal++;
                continue;
            }
            double ratio = greedy / opt;
            ratios.add(ratio);
            if (ratio > 1 + 1e-9) exactBetter++;
            else equal++;
            worst = Math.max(worst, ratio);
        }
        double[] v = ratios.stream()
            .mapToDouble(Double::doubleValue)
            .sorted()
            .toArray();
        double mean = Arrays.stream(v)
            .average()
            .orElse(1);
        System.out.println(
            String.format(
                Locale.ROOT,
                "rooms solved exactly: %d (skipped %d)%nplanner route equal to the shortest: %d, longer: %d%n"
                    + "planner / shortest: mean %.3f, median %.3f, 90th %.3f, worst %.3f",
                v.length,
                skipped,
                equal,
                exactBetter,
                mean,
                v.length == 0 ? 1 : v[v.length / 2],
                v.length == 0 ? 1 : v[(int) (v.length * 0.9)],
                worst));
    }

    /** Shortest walk that delivers every stack (Dijkstra over position x per-stack state). */
    static double exact(List<ChestModel> chests, Assigner.Result a, Map<Integer, ItemInfo> items, List<int[]> moves,
        int carry, BlockPos start) {
        int m = moves.size(), n = chests.size();
        int pow = 1;
        for (int i = 0; i < m; i++) pow *= 3;
        // position index n = start
        double[] dist = new double[(n + 1) * pow];
        Arrays.fill(dist, Double.MAX_VALUE);
        PriorityQueue<double[]> q = new PriorityQueue<>((x, y) -> Double.compare(x[0], y[0]));
        int s0 = n * pow; // all stacks state 0
        dist[s0] = 0;
        q.add(new double[] { 0, s0 });
        int goal = 0;
        for (int i = 0, p = 1; i < m; i++, p *= 3) goal += 2 * p;
        while (!q.isEmpty()) {
            double[] cur = q.poll();
            int st = (int) cur[1];
            if (cur[0] > dist[st]) continue;
            int pos = st / pow, code = st % pow;
            if (code == goal) return cur[0];
            int[] sv = new int[m];
            int carried = 0;
            for (int i = 0, c = code; i < m; i++, c /= 3) {
                sv[i] = c % 3;
                if (sv[i] == 1) carried++;
            }
            if (pos < n) {
                // free actions at this chest: take a stack that is here, put a carried one in if this chest is its group
                for (int i = 0, p = 1; i < m; i++, p *= 3) {
                    if (sv[i] == 0 && moves.get(i)[0] == pos && carried < carry) relax(q, dist, cur[0], pos * pow + code + p);
                    if (sv[i] == 1) {
                        String b = a.effective(items.get(moves.get(i)[1]).category);
                        if (b.equals(a.chestCategory.get(pos))) relax(q, dist, cur[0], pos * pow + code + p);
                    }
                }
            }
            BlockPos here = pos < n ? chests.get(pos).pos : start;
            for (int c = 0; c < n; c++) {
                if (c == pos) continue;
                relax(q, dist, cur[0] + d(here, chests.get(c).pos), c * pow + code);
            }
        }
        return Double.NaN;
    }

    static void relax(PriorityQueue<double[]> q, double[] dist, double v, int st) {
        if (v < dist[st] - 1e-12) {
            dist[st] = v;
            q.add(new double[] { v, st });
        }
    }

    static double d(BlockPos a, BlockPos b) {
        double dx = a.x - b.x, dy = a.y - b.y, dz = a.z - b.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
