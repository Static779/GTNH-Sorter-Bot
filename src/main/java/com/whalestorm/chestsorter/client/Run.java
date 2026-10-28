package com.whalestorm.chestsorter.client;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Vec3;

import com.whalestorm.chestsorter.Config;
import com.whalestorm.chestsorter.core.Assigner;
import com.whalestorm.chestsorter.core.BlockPos;
import com.whalestorm.chestsorter.core.ChestModel;
import com.whalestorm.chestsorter.core.Container;
import com.whalestorm.chestsorter.core.ItemInfo;
import com.whalestorm.chestsorter.core.Plan;
import com.whalestorm.chestsorter.core.PlanChecker;
import com.whalestorm.chestsorter.core.Planner;
import com.whalestorm.chestsorter.core.SorterState;
import com.whalestorm.chestsorter.core.Taxonomy;

/**
 * One sorting run: SCAN → SURVEY → PLAN → EXECUTE → VERIFY, driven one client tick at a time.
 *
 * How items stay safe:
 * - Only two kinds of click are ever sent: left-click to pick up a whole stack, left-click to put it down. No
 * shift-click, no throwing, no clicks outside the window.
 * - A pickup and its put-down happen in the same tick, so the cursor is empty between ticks and when a chest closes.
 * - Picked stacks go only into player inventory slots that were empty when the run started ("carry slots"), never
 * onto your own items. The hotbar and armour are never touched.
 * - Every transfer is checked: after waiting for the server, item totals in the open container must be unchanged,
 * the slots involved must hold exactly what was expected, and the cursor must be empty. Any mismatch stops the run.
 * - The plan is replayed by {@link PlanChecker} before anything moves; any problem means nothing moves.
 * - If a run stops while carrying items, they stay in your inventory and the report says where they came from.
 * - Checks compare items by loose key (see {@link ItemKeys}) so a tool that changes its own NBT in the inventory does
 * not stop the run; counts are always exact.
 * - A chest that cannot be reached or opened is skipped for the rest of the run: stacks meant for it go back to
 * where they came from and the other trips carry on.
 */
public final class Run {

    public enum Mode {
        /** Survey and plan, print the plan, move nothing. */
        PLAN_ONLY,
        /** Everything. */
        FULL
    }

    private enum Outcome {
        RUNNING,
        DONE,
        FAILED
    }

    /** A step of the run. Steps in a skippable group may fail without stopping the run. */
    private abstract class Step {

        final String group;
        final boolean skippable;

        Step(String group, boolean skippable) {
            this.group = group;
            this.skippable = skippable;
        }

        abstract String describe();

        abstract Outcome tick();
    }

    /** Items picked up this trip, by player inventory index. */
    private static final class Carried {

        final int key;
        int count;
        final int sourceChest;

        Carried(int key, int count, int sourceChest) {
            this.key = key;
            this.count = count;
            this.sourceChest = sourceChest;
        }
    }

    /** Ticks after a chest opens before its contents are read (they arrive with the open packet). */
    private static final int OPEN_SETTLE_TICKS = 2;

    private final Minecraft mc = Minecraft.getMinecraft();
    private final Mode mode;
    private final Trace trace;
    private final Safety safety = new Safety();
    private final AutoInput input = new AutoInput();
    private final Walker walker;
    private final ItemKeys keys = new ItemKeys();

    private SorterState state = SorterState.SCAN;
    private final Deque<Step> steps = new ArrayDeque<>();
    private Step current;
    private String failure;
    private boolean finished;

    private List<Container> containers = new ArrayList<>();
    private final Map<Integer, ChestModel> survey = new LinkedHashMap<>();
    private final Map<Integer, ChestModel> after = new LinkedHashMap<>();
    private final Set<Integer> skippedChests = new TreeSet<>();
    /** Chests that could not be reached or opened during EXECUTE; trips avoid them from then on. */
    private final Set<Integer> unreachable = new TreeSet<>();
    private int tripsSkipped;
    private final Set<Integer> touched = new TreeSet<>();
    private final List<Integer> carrySlots = new ArrayList<>();
    private final Map<Integer, Carried> carried = new TreeMap<>();
    private Plan plan;
    private int tripsDone, stacksMoved, itemsMoved;

    /** Chest id currently open by us, or -1. */
    private int openChest = -1;
    private int windowId;
    /** Safety.GUI_NONE / GUI_TRANSITION / GUI_OPEN. */
    private int guiMode = Safety.GUI_NONE;
    /** Hotbar slot selected for the run (empty hand), and the one to restore afterwards. */
    private int handSlot = -1, originalHandSlot = -1;
    private int wait;
    /** Set by a transfer: end this tick after it, so transfers run at most one per tick. */
    private boolean yieldTick;
    /** Snapshot taken when the current chest finished opening; checked again before it closes. */
    private Map<Integer, Long> openTotals;
    private int[] openInvKeys, openInvCounts;
    private Set<Integer> carriedAtOpen;

    public Run(Mode mode, Trace trace) {
        this.mode = mode;
        this.trace = trace;
        this.walker = new Walker(input, trace);
    }

    public SorterState state() {
        return state;
    }

    public boolean finished() {
        return finished;
    }

    public String status() {
        String s = state + (current != null ? ": " + current.describe() : "");
        if (plan != null) s += " | trips " + tripsDone + "/" + plan.trips.size() + ", stacks moved " + stacksMoved;
        if (!carried.isEmpty()) s += " | carrying " + carried.size() + " stacks";
        return s;
    }

    // =====================================================================================================
    // lifecycle
    // =====================================================================================================

    /** @return false if the run could not start (reason already reported). */
    public boolean begin() {
        trace.open(mode + " run");
        EntityClientPlayerMP p = mc.thePlayer;
        String why = safety.begin(mc);
        if (why != null) {
            trace.error("Not starting: " + why + ".");
            finish();
            return false;
        }
        // Carry slots: main-inventory slots that are empty right now.
        for (int i = GuiOps.MAIN_FIRST; i <= GuiOps.MAIN_LAST; i++) {
            if (p.inventory.getStackInSlot(i) == null) carrySlots.add(i);
        }
        if (mode == Mode.FULL && carrySlots.isEmpty()) {
            trace.error("Not starting: no empty slot in your main inventory (the 27 slots above the hotbar).");
            finish();
            return false;
        }
        // Empty hand: Forge runs the held item's onItemUseFirst before the chest opens, and a tool could act on the
        // chest. Switch to an empty hotbar slot for the run and restore the selection afterwards.
        originalHandSlot = p.inventory.currentItem;
        for (int i = 0; i < 9 && handSlot < 0; i++) {
            int slot = (originalHandSlot + i) % 9;
            if (p.inventory.getStackInSlot(slot) == null) handSlot = slot;
        }
        if (handSlot < 0) {
            trace.error("Not starting: free one hotbar slot (the run needs an empty hand to open chests).");
            finish();
            return false;
        }
        p.inventory.currentItem = handSlot;
        input.install(p);
        trace.log("Start " + mode + " at " + Walker.feet(p) + ", carry slots " + carrySlots);

        containers = ContainerScanner.scan(mc.theWorld, p, Config.scanRadius);
        trace.info("Found " + containers.size() + " containers within " + Config.scanRadius + " blocks.");
        for (int i = 0; i < containers.size(); i++) trace.log("  #" + i + " " + containers.get(i));
        if (containers.isEmpty()) {
            finishOk("Nothing to sort.");
            return true;
        }
        state = SorterState.SURVEY;
        for (final int id : nearestFirstOrder(Walker.feet(p))) {
            String g = "survey #" + id;
            steps.add(new GoTo(g, true, id));
            steps.add(new Open(g, true, id));
            steps.add(new Step(g, true) {

                String describe() {
                    return "read chest #" + id;
                }

                Outcome tick() {
                    ChestModel m = readOpen(id);
                    survey.put(id, m);
                    trace.step("Surveyed #" + id + ": " + countUsed(m) + "/" + m.size() + " slots used");
                    trace.log("Contents #" + id + ": " + contentsLine(m));
                    return Outcome.DONE;
                }
            });
            steps.add(new Close(g, true));
        }
        return true;
    }

    /** Stop now. Never closes a chest while an item is on the cursor. */
    public void abort(String reason) {
        if (finished) return;
        failure = reason;
        trace.error("Stopped: " + reason);
        walker.stop();
        steps.clear();
        current = null;
        recoverCursorAndClose();
        reportCarried();
        finish();
    }

    public void tick() {
        if (finished) return;
        trace.tick();
        String stop = safety.check(mc, guiMode);
        if (stop == null && handSlot >= 0
            && (mc.thePlayer.inventory.currentItem != handSlot || mc.thePlayer.getHeldItem() != null)) {
            stop = "hotbar selection changed or something is in hand";
        }
        if (stop != null) {
            abort(stop);
            return;
        }
        if (wait > 0) {
            wait--;
            return;
        }
        for (int guard = 0; guard < 4 && !finished; guard++) { // allow a few instant steps per tick
            if (current == null) {
                current = steps.poll();
                if (current == null) {
                    nextPhase();
                    if (finished) return;
                    continue;
                }
                trace.step("> " + current.describe());
            }
            Outcome o;
            try {
                o = current.tick();
            } catch (Throwable t) {
                trace.log("Exception in step '" + current.describe() + "': " + t);
                for (StackTraceElement e : t.getStackTrace()) trace.log("    at " + e);
                o = Outcome.FAILED;
                if (failure == null) failure = "internal error: " + t;
            }
            if (o == Outcome.RUNNING) return;
            Step done = current;
            current = null;
            if (o == Outcome.FAILED) {
                onStepFailed(done);
                return;
            }
            if (wait > 0 || yieldTick) {
                yieldTick = false;
                return;
            }
        }
    }

    private void onStepFailed(Step s) {
        String why = failure != null ? failure : "step failed: " + s.describe();
        failure = null;
        boolean environmental = s instanceof GoTo || s instanceof Open;
        int chest = s instanceof GoTo ? ((GoTo) s).id : s instanceof Open ? ((Open) s).id : -1;
        if (s.skippable && carried.isEmpty()) {
            trace.warn("Skipping " + s.group + ": " + why);
            if (s.group.startsWith("survey #")) skippedChests.add(Integer.parseInt(s.group.substring(8)));
            if (s.group.startsWith("trip ")) {
                tripsSkipped++;
                if (environmental) unreachable.add(chest);
            }
            walker.stop();
            // Drop the rest of this group, then make sure no chest is left open.
            while (!steps.isEmpty() && steps.peek().group.equals(s.group)) steps.poll();
            if (openChest >= 0 || mc.thePlayer.openContainer != mc.thePlayer.inventoryContainer) {
                steps.addFirst(new Close(s.group + " cleanup", false));
            }
            return;
        }
        // Could not reach or open a deposit chest: skip that chest for the rest of the run. This trip drops its visit
        // there and carries on; whatever was meant for it goes back to its source at the end of the trip. Any other
        // failure (a check that found unexpected items) stops at once, because more clicking could make it worse;
        // items stay in your inventory.
        if (environmental && !s.group.startsWith("return")) {
            unreachable.add(chest);
            trace.warn(
                "Chest #" + chest
                    + ": "
                    + why
                    + ". Skipping it for the rest of the run"
                    + (carried.isEmpty() ? "." : "; stacks meant for it go back where they came from."));
            walker.stop();
            while (!steps.isEmpty() && steps.peek().group.equals(s.group) && isVisitStep(steps.peek())) steps.poll();
            if (openChest >= 0 || mc.thePlayer.openContainer != mc.thePlayer.inventoryContainer) {
                steps.addFirst(new Close(s.group + " cleanup", false));
            }
            return;
        }
        abort(why);
    }

    /** Steps that belong to one chest visit after its GoTo (dropped together when that chest is unreachable). */
    private static boolean isVisitStep(Step s) {
        return s instanceof Open || s instanceof Deposit || s instanceof VerifyVisit || s instanceof Close;
    }

    private void nextPhase() {
        switch (state) {
            case SURVEY:
                state = SorterState.PLAN;
                buildPlan();
                return;
            case EXECUTE:
                if (touched.isEmpty()) {
                    finishOk("Done. Nothing needed moving.");
                    return;
                }
                state = SorterState.VERIFY;
                if (!Config.finalRecheck) {
                    // Every touched chest was already checked before it closed; compare those last-seen states.
                    finishVerify();
                    return;
                }
                after.clear();
                for (int id : touched) {
                    String g = "verify #" + id;
                    final int cid = id;
                    // Skippable: if a chest cannot be re-read, the report says so instead of stopping.
                    steps.add(new GoTo(g, true, cid));
                    steps.add(new Open(g, true, cid));
                    steps.add(new Step(g, true) {

                        String describe() {
                            return "re-read chest #" + cid;
                        }

                        Outcome tick() {
                            after.put(cid, readOpen(cid));
                            return Outcome.DONE;
                        }
                    });
                    steps.add(new Close(g, true));
                }
                return;
            case VERIFY:
                finishVerify();
                return;
            default:
                finishOk("Done.");
        }
    }

    // =====================================================================================================
    // planning
    // =====================================================================================================

    private void buildPlan() {
        if (!skippedChests.isEmpty()) trace.warn("Could not survey chests " + skippedChests + "; they are left out.");
        List<ChestModel> chests = new ArrayList<>(survey.values());
        if (chests.isEmpty()) {
            finishOk("No chest could be surveyed.");
            return;
        }
        Map<Integer, ItemInfo> items = keys.infos();
        Taxonomy tax = Taxonomy.parse(Config.groups);
        for (String pr : tax.problems) trace.warn("Category groups setting: " + pr);
        Assigner.Result a = Assigner.assign(
            chests,
            items,
            new java.util.HashMap<Integer, String>(),
            new Assigner.Weights(1.0, Config.layoutWeight),
            tax,
            Config.minStacksForOwnChest);
        int carry = Math.max(1, carrySlots.size());
        plan = Planner.plan(chests, items, a, carry, Config.maxTrips);
        List<String> problems = PlanChecker.check(chests, plan, items, carry);

        trace.log("Item keys:");
        for (ItemInfo i : items.values())
            trace.log("  key " + i.key + ": " + i + " max " + i.maxStack + " ore " + keys.oreNames(i.key));
        trace.info("Plan: " + plan.trips.size() + " trips, " + plan.stackMoves() + " stacks to move.");
        trace.log("Layout " + a.costReport + " (layoutWeight " + Config.layoutWeight + ")");
        Map<String, Integer> perCategory = new TreeMap<>();
        for (Map.Entry<Integer, String> e : plan.chestCategory.entrySet()) {
            Container c = containers.get(e.getKey());
            trace.log("  " + c.name + " at " + c.x + " " + c.y + " " + c.z + " -> " + e.getValue());
            perCategory.merge(e.getValue(), 1, Integer::sum);
        }
        StringBuilder sum = new StringBuilder();
        for (Map.Entry<String, Integer> e : perCategory.entrySet()) {
            if (sum.length() > 0) sum.append(", ");
            sum.append(e.getKey());
            if (e.getValue() > 1) sum.append(" x")
                .append(e.getValue());
        }
        trace.info("Chests: " + sum + " (each chest's category is in the trace)");
        if (a.buckets != null) {
            trace.log(
                "Grouping for " + chests.size()
                    + " chests (min "
                    + Config.minStacksForOwnChest
                    + " stacks for a chest of its own):");
            for (String st : a.buckets.steps) trace.log("  " + st);
            trace.log("Buckets (stacks: categories):");
            for (Map.Entry<String, List<String>> e : a.buckets.members.entrySet()) {
                trace.log("  " + e.getKey() + " (" + a.buckets.demand.get(e.getKey()) + "): " + e.getValue());
            }
        }
        if (!plan.merged.isEmpty()) {
            Map<String, List<String>> shares = new TreeMap<>();
            for (Map.Entry<String, String> e : plan.merged.entrySet()) {
                shares.computeIfAbsent(e.getValue(), x -> new ArrayList<>())
                    .add(e.getKey());
            }
            StringBuilder m = new StringBuilder();
            for (Map.Entry<String, List<String>> e : shares.entrySet()) {
                if (m.length() > 0) m.append("; ");
                m.append(e.getKey())
                    .append(" also holds ")
                    .append(String.join(", ", e.getValue()));
            }
            trace.log("Shared chests: " + m);
        }
        if (!plan.unassigned.isEmpty()) {
            trace.warn(
                "No chest for: " + plan.unassigned + ". Those items stay where they are. Add empty chests to sort them.");
        }
        if (plan.stacksLeftNoRoom > 0) {
            trace.warn(plan.stacksLeftNoRoom + " stacks stay where they are: their category's chests are full.");
        }
        int t = 0;
        for (Plan.Trip trip : plan.trips) {
            t++;
            trace.log("Trip " + t + " from #" + trip.source + ":");
            for (Plan.Deposit d : trip.deposits) {
                for (int idx : d.picks) {
                    Plan.Pick p = trip.picks.get(idx);
                    trace.log("  slot " + p.slot + ": " + p.count + " x " + keys.label(p.key) + " -> #" + d.target);
                }
            }
        }
        if (!problems.isEmpty()) {
            for (String pr : problems) trace.log("PLAN CHECK: " + pr);
            abort("the plan failed its safety check (" + problems.size() + " problems, see trace). Nothing was moved.");
            return;
        }
        trace.log("Plan check passed.");
        if (mode == Mode.PLAN_ONLY || plan.isEmpty()) {
            finishOk(plan.isEmpty() ? "Already sorted. Nothing to move." : "Plan only: nothing was moved.");
            return;
        }
        state = SorterState.EXECUTE;
        t = 0;
        for (Plan.Trip trip : plan.trips) queueTrip(++t, trip);
    }

    private void queueTrip(int n, final Plan.Trip trip) {
        final String g = "trip " + n;
        final int src = trip.source;
        final Map<Integer, Integer> pickToSlot = new HashMap<>();
        // Before touching anything: reaching and opening the source may fail harmlessly (trip skipped).
        steps.add(new GoTo(g, true, src));
        steps.add(new Open(g, true, src));
        steps.add(new Step(g, true) {

            String describe() {
                return "check chest #" + src + " still matches the survey";
            }

            Outcome tick() {
                ChestModel live = readOpen(src);
                for (Plan.Pick p : trip.picks) {
                    if (live.keys[p.slot] != p.key || live.counts[p.slot] != p.count) {
                        failure = "chest #" + src + " changed since the survey (slot " + p.slot + ")";
                        return Outcome.FAILED;
                    }
                }
                return Outcome.DONE;
            }
        });
        final Map<Integer, Integer> pickTarget = new HashMap<>();
        for (Plan.Deposit d : trip.deposits) for (int idx : d.picks) pickTarget.put(idx, d.target);
        for (int i = 0; i < trip.picks.size(); i++) {
            steps.add(new Pick(g, src, i, trip.picks.get(i), pickToSlot, pickTarget.get(i)));
        }
        steps.add(new VerifyVisit(g, src));
        steps.add(new Close(g, false));
        for (Plan.Deposit d : trip.deposits) {
            steps.add(new GoTo(g, false, d.target));
            steps.add(new Open(g, false, d.target));
            for (int idx : d.picks) steps.add(new Deposit(g, d.target, idx, pickToSlot, false));
            steps.add(new VerifyVisit(g, d.target));
            steps.add(new Close(g, false));
        }
        steps.add(new Step(g, false) {

            String describe() {
                return "finish " + g;
            }

            Outcome tick() {
                tripsDone++;
                if (!carried.isEmpty()) {
                    trace.warn(carried.size() + " stacks could not be stored; taking them back to chest #" + src + ".");
                    queueReturn("return " + g);
                }
                return Outcome.DONE;
            }
        });
    }

    /** Takes every carried stack back to its source chest. */
    private void queueReturn(String g) {
        Set<Integer> sources = new TreeSet<>();
        for (Carried c : carried.values()) sources.add(c.sourceChest);
        List<Step> ret = new ArrayList<>();
        for (int src : sources) {
            ret.add(new GoTo(g, false, src));
            ret.add(new Open(g, false, src));
            for (Map.Entry<Integer, Carried> e : carried.entrySet()) {
                if (e.getValue().sourceChest == src) ret.add(new ReturnStack(g, src, e.getKey()));
            }
            ret.add(new VerifyVisit(g, src));
            ret.add(new Close(g, false));
        }
        ret.add(new Step(g, false) {

            String describe() {
                return "confirm nothing is still carried";
            }

            Outcome tick() {
                if (carried.isEmpty()) return Outcome.DONE;
                failure = carried.size() + " stacks could not be returned (source chest full or changed)";
                return Outcome.FAILED;
            }
        });
        for (int i = ret.size() - 1; i >= 0; i--) steps.addFirst(ret.get(i));
    }

    // =====================================================================================================
    // steps
    // =====================================================================================================

    private final class GoTo extends Step {

        final int id;
        boolean started;

        GoTo(String g, boolean skippable, int id) {
            super(g, skippable);
            this.id = id;
        }

        String describe() {
            return "walk to chest #" + id + " at " + pos(id);
        }

        Outcome tick() {
            if (!started && unreachable.contains(id) && !group.startsWith("return")) {
                failure = "chest #" + id + " could not be reached or opened earlier in this run";
                return Outcome.FAILED;
            }
            if (!started) {
                walker.start(pos(id));
                started = true;
            }
            Walker.Status s = walker.tick(mc.theWorld, mc.thePlayer);
            if (s == Walker.Status.ARRIVED) return Outcome.DONE;
            if (s == Walker.Status.FAILED) {
                failure = "cannot reach chest #" + id + ": " + walker.failure();
                return Outcome.FAILED;
            }
            return Outcome.RUNNING;
        }
    }

    private final class Open extends Step {

        final int id;
        int phase, ticks, stableReads;
        ChestModel lastRead;

        Open(String g, boolean skippable, int id) {
            super(g, skippable);
            this.id = id;
        }

        String describe() {
            return "open chest #" + id;
        }

        Outcome tick() {
            EntityClientPlayerMP p = mc.thePlayer;
            ticks++;
            switch (phase) {
                case 0: // aim
                    if (openChest >= 0 || p.openContainer != p.inventoryContainer) {
                        failure = "another container is still open";
                        return Outcome.FAILED;
                    }
                    if (!Walker.inReach(p, pos(id))) {
                        failure = "chest #" + id + " is out of reach";
                        return Outcome.FAILED;
                    }
                    if (Walker.aimAt(p, pos(id)) || ticks > 3) {
                        phase = 1;
                    }
                    return Outcome.RUNNING;
                case 1: { // click
                    BlockPos b = pos(id);
                    p.swingItem();
                    guiMode = Safety.GUI_TRANSITION;
                    mc.playerController.onPlayerRightClick(
                        p,
                        mc.theWorld,
                        p.getHeldItem(),
                        b.x,
                        b.y,
                        b.z,
                        1,
                        Vec3.createVectorHelper(b.x + 0.5D, b.y + 1D, b.z + 0.5D));
                    trace.step("Right-clicked " + b);
                    phase = 2;
                    ticks = 0;
                    return Outcome.RUNNING;
                }
                case 2: // wait for the server to open it
                    if (p.openContainer != p.inventoryContainer && mc.currentScreen instanceof GuiContainer) {
                        windowId = p.openContainer.windowId;
                        openChest = id;
                        guiMode = Safety.GUI_OPEN;
                        phase = 3;
                        ticks = 0;
                        return Outcome.RUNNING;
                    }
                    if (ticks > Config.openTimeoutTicks) {
                        guiMode = Safety.GUI_NONE;
                        failure = "chest #" + id
                            + " did not open within "
                            + Config.openTimeoutTicks
                            + " ticks (blocked lid, protected, or not a chest?)";
                        return Outcome.FAILED;
                    }
                    return Outcome.RUNNING;
                default: { // wait for contents, then require two identical reads
                    if (ticks < OPEN_SETTLE_TICKS) return Outcome.RUNNING;
                    int expectedSlots = containers.get(id).slots;
                    int actual = GuiOps.chestSlots(p.openContainer, p)
                        .size();
                    if (actual != expectedSlots) {
                        failure = "opened screen has " + actual + " chest slots, expected " + expectedSlots;
                        return Outcome.FAILED;
                    }
                    ChestModel m = readOpen(id);
                    if (lastRead != null && java.util.Arrays.equals(lastRead.keys, m.keys)
                        && java.util.Arrays.equals(lastRead.counts, m.counts)) {
                        if (++stableReads >= 1) {
                            snapshotOpen();
                            return Outcome.DONE;
                        }
                    } else {
                        stableReads = 0;
                    }
                    lastRead = m;
                    if (ticks > OPEN_SETTLE_TICKS + 40) {
                        failure = "chest #" + id + " contents kept changing";
                        return Outcome.FAILED;
                    }
                    wait = 1;
                    return Outcome.RUNNING;
                }
            }
        }
    }

    private final class Close extends Step {

        int ticks;
        boolean sent;

        Close(String g, boolean skippable) {
            super(g, skippable);
        }

        String describe() {
            return "close chest";
        }

        Outcome tick() {
            EntityClientPlayerMP p = mc.thePlayer;
            if (!sent) {
                if (p.inventory.getItemStack() != null && !placeCursorSafely()) {
                    failure = "an item is on the cursor and there is no empty slot to put it in; chest left open";
                    return Outcome.FAILED;
                }
                guiMode = Safety.GUI_TRANSITION;
                if (p.openContainer != p.inventoryContainer || mc.currentScreen != null) p.closeScreen();
                sent = true;
            }
            if (p.openContainer == p.inventoryContainer && mc.currentScreen == null) {
                openChest = -1;
                guiMode = Safety.GUI_NONE;
                return Outcome.DONE;
            }
            if (++ticks > 20) {
                failure = "chest screen did not close";
                return Outcome.FAILED;
            }
            return Outcome.RUNNING;
        }
    }

    /** Moves one planned stack from the open source chest into an empty carry slot. */
    private final class Pick extends Step {

        final int src, index, target;
        final Plan.Pick pick;
        final Map<Integer, Integer> pickToSlot;
        int phase;
        Map<Integer, Long> before;
        Slot chestSlot, carrySlot;
        int carryIndex;

        Pick(String g, int src, int index, Plan.Pick pick, Map<Integer, Integer> pickToSlot, Integer target) {
            super(g, false);
            this.src = src;
            this.index = index;
            this.pick = pick;
            this.pickToSlot = pickToSlot;
            this.target = target == null ? -1 : target;
        }

        String describe() {
            return "take " + pick.count + " x " + keys.label(pick.key) + " from #" + src + " slot " + pick.slot;
        }

        Outcome tick() {
            EntityClientPlayerMP p = mc.thePlayer;
            if (phase == 0) {
                if (unreachable.contains(target)) {
                    trace.step("Leaving " + pick.count + " x " + keys.label(pick.key) + " in #" + src
                        + ": its chest #" + target + " was unreachable");
                    return Outcome.DONE;
                }
                if (!requireOpen(src)) return Outcome.FAILED;
                if (p.inventory.getItemStack() != null) return fail("cursor not empty before pickup");
                chestSlot = GuiOps.chestSlots(p.openContainer, p)
                    .get(pick.slot);
                ItemStack st = chestSlot.getStack();
                if (keys.keyOf(st) != pick.key || st.stackSize != pick.count) {
                    return fail(
                        "slot " + pick.slot
                            + " holds "
                            + GuiOps.describe(st, keys)
                            + ", expected "
                            + pick.count
                            + " x "
                            + keys.label(pick.key));
                }
                carryIndex = -1;
                for (int i : carrySlots) {
                    if (!carried.containsKey(i) && p.inventory.getStackInSlot(i) == null) {
                        carryIndex = i;
                        break;
                    }
                }
                if (carryIndex < 0) return fail("no empty carry slot left");
                carrySlot = GuiOps.playerSlot(p.openContainer, p, carryIndex);
                if (carrySlot == null) return fail("inventory slot " + carryIndex + " not shown in this chest screen");
                before = GuiOps.totals(p.openContainer, p, keys);

                // Both clicks in this tick: the cursor is never left holding items between ticks.
                click(chestSlot);
                ItemStack cur = p.inventory.getItemStack();
                if (keys.keyOf(cur) != pick.key || cur.stackSize != pick.count) {
                    if (cur != null) click(chestSlot); // put it straight back
                    return fail("pickup gave " + GuiOps.describe(cur, keys));
                }
                click(carrySlot);
                if (p.inventory.getItemStack() != null) {
                    return fail(
                        "put-down left " + GuiOps.describe(p.inventory.getItemStack(), keys) + " on the cursor");
                }
                carried.put(carryIndex, new Carried(pick.key, pick.count, src));
                pickToSlot.put(index, carryIndex);
                touched.add(src);
                trace.step(
                    "Took " + pick.count
                        + " x "
                        + keys.label(pick.key)
                        + " #"
                        + src
                        + "["
                        + pick.slot
                        + "] -> inv "
                        + carryIndex);
                phase = 1;
                if (!Config.verifyEveryTransfer) return tick(); // check the client view now; server checked per visit
                wait = Config.settleTicks;
                return Outcome.RUNNING;
            }
            // verify after the server had time to answer
            if (!requireOpen(src)) return Outcome.FAILED;
            Map<Integer, Long> now = GuiOps.totals(p.openContainer, p, keys);
            if (!totalsMatch(before, now)) return fail("item totals changed: " + looseDiff(before, now));
            if (chestSlot.getStack() != null)
                return fail("source slot not empty after pickup: " + GuiOps.describe(chestSlot.getStack(), keys));
            ItemStack inCarry = carrySlot.getStack();
            if (keys.looseOf(inCarry) != keys.loose(pick.key) || inCarry.stackSize != pick.count) {
                return fail("carry slot holds " + GuiOps.describe(inCarry, keys));
            }
            if (p.inventory.getItemStack() != null) return fail("cursor not empty after pickup");
            wait = Config.clickDelayTicks - 1;
            yieldTick = true;
            return Outcome.DONE;
        }
    }

    /** Puts one carried stack into the open target chest: top up matching stacks, then empty slots. */
    private class Deposit extends Step {

        final int target, pickIndex;
        final Map<Integer, Integer> pickToSlot;
        final boolean returning;
        int phase;
        Map<Integer, Long> before;
        int invIndex;
        Carried c;

        Deposit(String g, int target, int pickIndex, Map<Integer, Integer> pickToSlot, boolean returning) {
            super(g, false);
            this.target = target;
            this.pickIndex = pickIndex;
            this.pickToSlot = pickToSlot;
            this.returning = returning;
        }

        String describe() {
            return (returning ? "return" : "store") + " stack into #" + target;
        }

        int inventoryIndex() {
            Integer i = pickToSlot.get(pickIndex);
            return i == null ? -1 : i;
        }

        Outcome tick() {
            EntityClientPlayerMP p = mc.thePlayer;
            if (phase == 0) {
                if (!requireOpen(target)) return Outcome.FAILED;
                invIndex = inventoryIndex();
                c = carried.get(invIndex);
                if (c == null) {
                    return Outcome.DONE; // already returned or never picked
                }
                Slot carrySlot = GuiOps.playerSlot(p.openContainer, p, invIndex);
                if (carrySlot == null) return fail("inventory slot " + invIndex + " not shown in this chest screen");
                ItemStack mine = carrySlot.getStack();
                if (keys.looseOf(mine) != keys.loose(c.key) || mine.stackSize != c.count) {
                    return fail(
                        "inventory slot " + invIndex
                            + " holds "
                            + GuiOps.describe(mine, keys)
                            + ", expected "
                            + c.count
                            + " x "
                            + keys.label(c.key)
                            + "; leaving it alone");
                }
                if (p.inventory.getItemStack() != null) return fail("cursor not empty before deposit");
                before = GuiOps.totals(p.openContainer, p, keys);
                int maxStack = mine.getMaxStackSize();

                // Pick up the carried stack and place it, all in this tick.
                click(carrySlot);
                ItemStack cur = p.inventory.getItemStack();
                if (keys.looseOf(cur) != keys.loose(c.key) || cur.stackSize != c.count) {
                    if (cur != null) click(carrySlot);
                    return fail("pickup from inventory gave " + GuiOps.describe(cur, keys));
                }
                List<Slot> chest = GuiOps.chestSlots(p.openContainer, p);
                List<Slot> order = new ArrayList<>();
                for (Slot s : chest) { // partial stacks of the same item first
                    ItemStack st = s.getStack();
                    if (st != null && ItemKeys.same(st, cur)
                        && st.stackSize < Math.min(maxStack, s.getSlotStackLimit())
                        && s.isItemValid(cur)) order.add(s);
                }
                for (Slot s : chest) if (s.getStack() == null && s.isItemValid(cur)) order.add(s);
                for (Slot s : order) {
                    click(s);
                    ItemStack now = p.inventory.getItemStack();
                    if (now == null) break;
                    if (!ItemKeys.same(now, cur)) {
                        click(s); // vanilla swapped: swap back immediately
                        return fail("slot swapped items instead of merging; swapped back");
                    }
                }
                if (p.inventory.getItemStack() != null) {
                    click(carrySlot); // remainder back into its (now empty) carry slot
                    if (p.inventory.getItemStack() != null)
                        return fail("remainder could not go back to inventory slot " + invIndex);
                }
                ItemStack left = carrySlot.getStack();
                int leftCount = left == null ? 0 : left.stackSize;
                int stored = c.count - leftCount;
                if (leftCount == 0) carried.remove(invIndex);
                else c.count = leftCount;
                touched.add(target);
                if (!returning) {
                    stacksMoved += leftCount == 0 ? 1 : 0;
                    itemsMoved += stored;
                }
                trace.step(
                    (returning ? "Returned " : "Stored ") + stored
                        + " x "
                        + keys.label(c.key)
                        + " into #"
                        + target
                        + (leftCount > 0 ? " (" + leftCount + " did not fit, kept in inv " + invIndex + ")" : ""));
                phase = 1;
                if (!Config.verifyEveryTransfer) return tick();
                wait = Config.settleTicks;
                return Outcome.RUNNING;
            }
            if (!requireOpen(target)) return Outcome.FAILED;
            Map<Integer, Long> now = GuiOps.totals(p.openContainer, p, keys);
            if (!totalsMatch(before, now)) return fail("item totals changed: " + looseDiff(before, now));
            if (p.inventory.getItemStack() != null) return fail("cursor not empty after deposit");
            Carried still = carried.get(invIndex);
            ItemStack inCarry = p.inventory.getStackInSlot(invIndex);
            int expectLeft = still == null ? 0 : still.count;
            int haveLeft = inCarry == null ? 0 : inCarry.stackSize;
            if (haveLeft != expectLeft)
                return fail("inventory slot " + invIndex + " holds " + haveLeft + ", expected " + expectLeft);
            wait = Config.clickDelayTicks - 1;
            yieldTick = true;
            return Outcome.DONE;
        }
    }

    /** Deposit of a carried stack back into the chest it came from. */
    private final class ReturnStack extends Deposit {

        ReturnStack(String g, int source, final int invIndex) {
            super(g, source, -1, singleton(-1, invIndex), true);
        }
    }

    private static Map<Integer, Integer> singleton(int k, int v) {
        Map<Integer, Integer> m = new HashMap<>();
        m.put(k, v);
        return m;
    }

    /**
     * Server check for one chest visit, run just before the chest closes: after settleTicks, the open container must
     * hold exactly the items it held when it opened, the cursor must be empty, every carried stack must be in its carry
     * slot, and every other inventory slot must be as it was when the chest opened.
     */
    private final class VerifyVisit extends Step {

        final int id;
        boolean waited;

        VerifyVisit(String g, int id) {
            super(g, false);
            this.id = id;
        }

        String describe() {
            return "check chest #" + id + " before closing";
        }

        Outcome tick() {
            if (!requireOpen(id)) return Outcome.FAILED;
            if (!waited) {
                waited = true;
                wait = Config.settleTicks;
                return Outcome.RUNNING;
            }
            EntityClientPlayerMP p = mc.thePlayer;
            if (p.inventory.getItemStack() != null) return fail("cursor not empty before closing chest #" + id);
            Map<Integer, Long> now = GuiOps.totals(p.openContainer, p, keys);
            if (!totalsMatch(openTotals, now)) {
                return fail("item totals in chest #" + id + " + inventory changed: " + looseDiff(openTotals, now));
            }
            for (int i = 0; i < 36; i++) {
                ItemStack st = p.inventory.getStackInSlot(i);
                int k = keys.keyOf(st), n = st == null ? 0 : st.stackSize;
                int ek, en;
                Carried c = carried.get(i);
                if (c != null) {
                    ek = c.key;
                    en = c.count;
                } else if (carriedAtOpen.contains(i)) {
                    ek = ChestModel.EMPTY;
                    en = 0;
                } else {
                    ek = openInvKeys[i];
                    en = openInvCounts[i];
                }
                if (keys.loose(k) != keys.loose(ek) || n != en) {
                    return fail(
                        "inventory slot " + i
                            + " holds "
                            + GuiOps.describe(st, keys)
                            + ", expected "
                            + (ek == ChestModel.EMPTY ? "empty" : en + " x " + keys.label(ek)));
                }
            }
            after.put(id, readOpen(id));
            trace.step("Chest #" + id + " checked: totals unchanged, inventory as expected");
            return Outcome.DONE;
        }
    }

    // =====================================================================================================
    // helpers
    // =====================================================================================================

    /** Records what the just-opened container and the inventory hold, for {@link VerifyVisit}. */
    private void snapshotOpen() {
        EntityClientPlayerMP p = mc.thePlayer;
        openTotals = GuiOps.totals(p.openContainer, p, keys);
        openInvKeys = new int[36];
        openInvCounts = new int[36];
        for (int i = 0; i < 36; i++) {
            ItemStack st = p.inventory.getStackInSlot(i);
            openInvKeys[i] = keys.keyOf(st);
            openInvCounts[i] = st == null ? 0 : st.stackSize;
        }
        carriedAtOpen = new TreeSet<>(carried.keySet());
    }

    /**
     * Totals match exactly, or match by loose key: an item changed its own NBT or durability while we looked (logged,
     * allowed, because every count still matches).
     */
    private boolean totalsMatch(Map<Integer, Long> before, Map<Integer, Long> now) {
        if (now.equals(before)) return true;
        if (GuiOps.looseTotals(now, keys)
            .equals(GuiOps.looseTotals(before, keys))) {
            trace.log("Note: an item changed its own NBT/durability (counts unchanged, allowed): "
                + GuiOps.diff(before, now, keys));
            return true;
        }
        return false;
    }

    private String looseDiff(Map<Integer, Long> before, Map<Integer, Long> now) {
        return GuiOps.diff(GuiOps.looseTotals(before, keys), GuiOps.looseTotals(now, keys), keys);
    }

    private Outcome fail(String why) {
        failure = why;
        return Outcome.FAILED;
    }

    private BlockPos pos(int id) {
        Container c = containers.get(id);
        return new BlockPos(c.x, c.y, c.z);
    }

    private boolean requireOpen(int id) {
        EntityClientPlayerMP p = mc.thePlayer;
        if (openChest != id || p.openContainer == p.inventoryContainer || p.openContainer.windowId != windowId) {
            failure = "chest #" + id + " is not the open screen any more";
            return false;
        }
        return true;
    }

    private ChestModel readOpen(int id) {
        Container c = containers.get(id);
        return GuiOps
            .readChest(mc.thePlayer.openContainer, mc.thePlayer, keys, id, new BlockPos(c.x, c.y, c.z), c.name);
    }

    private void click(Slot s) {
        trace.log(
            "  click window " + windowId
                + " slot "
                + s.slotNumber
                + " ("
                + GuiOps.describe(s.getStack(), keys)
                + ", cursor "
                + GuiOps.describe(mc.thePlayer.inventory.getItemStack(), keys)
                + ")");
        mc.playerController.windowClick(windowId, s.slotNumber, 0, 0, mc.thePlayer);
    }

    /**
     * Puts a cursor item into an empty slot without merging or swapping: an empty carry slot or other empty main
     * inventory slot first, else an empty chest slot. @return true if the cursor is now empty.
     */
    private boolean placeCursorSafely() {
        EntityClientPlayerMP p = mc.thePlayer;
        if (p.inventory.getItemStack() == null) return true;
        if (p.openContainer == p.inventoryContainer) return false;
        List<Slot> candidates = new ArrayList<>();
        for (int i = GuiOps.MAIN_FIRST; i <= GuiOps.MAIN_LAST; i++) {
            Slot s = GuiOps.playerSlot(p.openContainer, p, i);
            if (s != null && s.getStack() == null) candidates.add(s);
        }
        for (Slot s : GuiOps.chestSlots(p.openContainer, p)) {
            if (s.getStack() == null && s.isItemValid(p.inventory.getItemStack())) candidates.add(s);
        }
        for (Slot s : candidates) {
            trace.warn(
                "Putting cursor item " + GuiOps.describe(p.inventory.getItemStack(), keys)
                    + " into empty slot "
                    + s.slotNumber);
            click(s);
            if (p.inventory.getItemStack() == null) return true;
        }
        return false;
    }

    private void recoverCursorAndClose() {
        EntityClientPlayerMP p = mc.thePlayer;
        if (p == null) return;
        if (p.openContainer != p.inventoryContainer) {
            if (p.inventory.getItemStack() != null && !placeCursorSafely()) {
                trace.error(
                    "An item is still on your cursor and there is no empty slot. The chest is left open: "
                        + "put the item down yourself.");
                return;
            }
            p.closeScreen();
        }
        openChest = -1;
        guiMode = Safety.GUI_NONE;
    }

    private void reportCarried() {
        if (carried.isEmpty()) return;
        trace.warn("Still in your inventory (safe, not dropped):");
        for (Map.Entry<Integer, Carried> e : carried.entrySet()) {
            Carried c = e.getValue();
            Container src = containers.get(c.sourceChest);
            trace.warn(
                "  inventory slot " + e.getKey()
                    + ": "
                    + c.count
                    + " x "
                    + keys.label(c.key)
                    + " from "
                    + src.name
                    + " at "
                    + src.x
                    + " "
                    + src.y
                    + " "
                    + src.z);
        }
    }

    private void finishVerify() {
        Map<Integer, Long> beforeTotals = new TreeMap<>(), afterTotals = new TreeMap<>();
        List<String> perChest = new ArrayList<>();
        for (int id : touched) {
            ChestModel b = survey.get(id), a = after.get(id);
            if (b == null || a == null) continue;
            for (Map.Entry<Integer, Long> e : b.totals()
                .entrySet()) beforeTotals.merge(e.getKey(), e.getValue(), Long::sum);
            for (Map.Entry<Integer, Long> e : a.totals()
                .entrySet()) afterTotals.merge(e.getKey(), e.getValue(), Long::sum);
            ChestModel exp = plan.expected.get(id);
            if (exp != null && !GuiOps.looseTotals(exp.totals(), keys)
                .equals(GuiOps.looseTotals(a.totals(), keys))) perChest.add("#" + id);
        }
        for (Carried c : carried.values()) afterTotals.merge(c.key, (long) c.count, Long::sum);
        Set<Integer> unverified = new TreeSet<>(touched);
        unverified.removeAll(after.keySet());
        if (!unverified.isEmpty()) {
            trace.warn("Could not re-read chests " + unverified + "; the check below covers only the others.");
        }
        boolean complete = tripsSkipped == 0 && unreachable.isEmpty() && tripsDone == plan.trips.size();
        Map<Integer, Long> lb = GuiOps.looseTotals(beforeTotals, keys), la = GuiOps.looseTotals(afterTotals, keys);
        if (!lb.equals(la)) {
            trace.error("VERIFY FAILED: item totals in the touched chests changed: " + diffLabels(lb, la));
            failure = "verify failed";
        } else if (!complete) {
            // Skipped trips and returned stacks leave chests different from the full plan; that is expected.
            trace.info(
                "Verify passed: nothing lost. " + tripsDone
                    + " of "
                    + plan.trips.size()
                    + " trips ran"
                    + (unreachable.isEmpty() ? "" : "; unreachable chests " + unreachable)
                    + ". Run again to finish the rest.");
            if (!perChest.isEmpty()) trace.log("Chests not yet as planned: " + perChest);
        } else if (!perChest.isEmpty()) {
            trace.warn(
                "Verify: no items lost, but chests " + perChest + " differ from the plan (something changed them?).");
        } else {
            trace.info("Verify passed: every touched chest holds exactly what the plan said, nothing lost.");
        }
        finishOk("Done: " + tripsDone + " trips, " + stacksMoved + " stacks (" + itemsMoved + " items) moved.");
    }

    private String diffLabels(Map<Integer, Long> b, Map<Integer, Long> a) {
        return GuiOps.diff(b, a, keys);
    }

    private void finishOk(String msg) {
        trace.info(msg);
        recoverCursorAndClose();
        finish();
    }

    private void finish() {
        walker.stop();
        input.uninstall();
        guiMode = Safety.GUI_NONE;
        restoreHand();
        finished = true;
        state = SorterState.IDLE;
        if (trace.path() != null) trace.info(
            "Trace: " + trace.path()
                .getAbsolutePath());
        trace.close();
    }

    private void restoreHand() {
        EntityClientPlayerMP p = mc.thePlayer;
        if (p != null && handSlot >= 0 && originalHandSlot >= 0 && p.inventory.currentItem == handSlot) {
            p.inventory.currentItem = originalHandSlot;
        }
        handSlot = -1;
    }

    /** Visit order: always the nearest not-yet-visited chest from the previous one. */
    private List<Integer> nearestFirstOrder(BlockPos start) {
        List<Integer> left = new ArrayList<>();
        for (int i = 0; i < containers.size(); i++) left.add(i);
        List<Integer> order = new ArrayList<>();
        BlockPos at = start;
        while (!left.isEmpty()) {
            int best = 0;
            double bestD = Double.MAX_VALUE;
            for (int j = 0; j < left.size(); j++) {
                BlockPos b = pos(left.get(j));
                double dx = b.x - at.x, dy = b.y - at.y, dz = b.z - at.z;
                double d = dx * dx + dy * dy * 4 + dz * dz; // height changes cost more to walk
                if (d < bestD) {
                    bestD = d;
                    best = j;
                }
            }
            int id = left.remove(best);
            order.add(id);
            at = pos(id);
        }
        return order;
    }

    /** "slot:key:count ..." for every used slot; tools/replay-trace.sh reads these lines back. */
    private static String contentsLine(ChestModel m) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < m.size(); i++) {
            if (m.keys[i] == ChestModel.EMPTY) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(i)
                .append(':')
                .append(m.keys[i])
                .append(':')
                .append(m.counts[i]);
        }
        return sb.length() == 0 ? "(empty)" : sb.toString();
    }

    private static int countUsed(ChestModel m) {
        int n = 0;
        for (int k : m.keys) if (k != ChestModel.EMPTY) n++;
        return n;
    }
}
