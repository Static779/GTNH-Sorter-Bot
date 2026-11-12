package com.whalestorm.chestsorter.client;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

import com.whalestorm.chestsorter.ChestSorter;
import com.whalestorm.chestsorter.Config;
import com.whalestorm.chestsorter.core.Container;
import com.whalestorm.chestsorter.core.ItemInfo;

import cpw.mods.fml.client.event.ConfigChangedEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/** Owns the current run. The toggle key starts a full run or stops the current one. */
public final class SorterController {

    public static final SorterController INSTANCE = new SorterController();

    private final Trace trace = new Trace();
    private Run run;
    private String lastResult = "no run yet";
    /** A run asked for from chat starts once the chat screen has closed. */
    private Run.Mode pending;
    private boolean pendingMigrate;
    private int pendingTicks;
    /** /cs config: open the settings once the chat screen has closed. */
    private boolean pendingSettings;

    private SorterController() {}

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        if (KeyBindings.TOGGLE.isPressed()) {
            if (isRunning()) stop("stopped by key");
            else start(Run.Mode.FULL);
        }
        if (KeyBindings.SETTINGS.isPressed()) openSettings();
        if (KeyBindings.MARK.isPressed()) cycleMarkLookedAt();
    }

    // ---- marks for moving storage ----

    /** The container the crosshair is on (within 8 blocks), or null. */
    private static Container lookedAt() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null || mc.objectMouseOver == null
            || mc.objectMouseOver.typeOfHit != net.minecraft.util.MovingObjectPosition.MovingObjectType.BLOCK) {
            return null;
        }
        int x = mc.objectMouseOver.blockX, y = mc.objectMouseOver.blockY, z = mc.objectMouseOver.blockZ;
        for (Container c : ContainerScanner.scan(mc.theWorld, mc.thePlayer, 8)) {
            for (com.whalestorm.chestsorter.core.BlockPos b : Marks.blocksOf(c)) {
                if (b.x == x && b.y == y && b.z == z) return c;
            }
        }
        return null;
    }

    private static String where(Container c) {
        return c.name + " at " + c.x + " " + c.y + " " + c.z;
    }

    private boolean markAllowed() {
        if (isRunning()) {
            Trace.chat("Stop the run before changing marks.");
            return false;
        }
        if (Marks.worldKey() == null) {
            Trace.chat("Can't tell which world this is, so marks can't be saved here.");
            return false;
        }
        return true;
    }

    /** Mark key: unmarked -> input -> output -> unmarked, on the chest you look at. */
    public void cycleMarkLookedAt() {
        if (!markAllowed()) return;
        Container c = lookedAt();
        if (c == null) {
            Trace.chat("Look at a chest (vanilla or Iron Chest) within 8 blocks to mark it.");
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        String w = Marks.worldKey();
        int dim = mc.thePlayer.dimension;
        Marks.Kind now = Marks.INSTANCE.kindOf(w, dim, c, null);
        Marks.Kind next = now == null ? Marks.Kind.INPUT : now == Marks.Kind.INPUT ? Marks.Kind.OUTPUT : null;
        Marks.INSTANCE.set(w, dim, java.util.Collections.singletonList(c), next);
        MarkRenderer.INSTANCE.refresh();
        Trace.chat(where(c) + ": " + (next == null ? "unmarked" : next.name().toLowerCase()) + ". " + markCounts());
    }

    /** /cs mark input|output|clear [radius]: the looked-at chest, or every chest within radius. */
    public void mark(Marks.Kind kind, int radius) {
        if (!markAllowed()) return;
        Minecraft mc = Minecraft.getMinecraft();
        List<Container> cs;
        if (radius > 0) {
            cs = ContainerScanner.scan(mc.theWorld, mc.thePlayer, radius);
        } else {
            Container c = lookedAt();
            if (c == null) {
                Trace.chat("Look at a chest within 8 blocks, or give a radius: /cs mark input 6");
                return;
            }
            cs = java.util.Collections.singletonList(c);
        }
        if (cs.isEmpty()) {
            Trace.chat("No chests within " + radius + " blocks.");
            return;
        }
        Marks.INSTANCE.set(Marks.worldKey(), mc.thePlayer.dimension, cs, kind);
        MarkRenderer.INSTANCE.refresh();
        String what = kind == null ? "Unmarked " : "Marked as " + kind.name().toLowerCase() + ": ";
        Trace.chat(what + (cs.size() == 1 ? where(cs.get(0)) : cs.size() + " chests") + ". " + markCounts());
    }

    public void listMarks() {
        if (Marks.worldKey() == null) {
            Trace.chat("Can't tell which world this is, so there are no marks here.");
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        java.util.Map<com.whalestorm.chestsorter.core.BlockPos, Marks.Kind> m = Marks.INSTANCE
            .here(Marks.worldKey(), mc.thePlayer.dimension);
        Trace.chat(markCounts());
        int shown = 0;
        for (java.util.Map.Entry<com.whalestorm.chestsorter.core.BlockPos, Marks.Kind> e : m.entrySet()) {
            if (shown++ >= 12) {
                Trace.chat("  ... and " + (m.size() - 12) + " more blocks");
                break;
            }
            com.whalestorm.chestsorter.core.BlockPos b = e.getKey();
            Trace.chat(
                "  " + e.getValue()
                    .name()
                    .toLowerCase() + " " + b.x + " " + b.y + " " + b.z);
        }
    }

    public void clearMarks() {
        if (!markAllowed()) return;
        int n = Marks.INSTANCE.clear(Marks.worldKey(), Minecraft.getMinecraft().thePlayer.dimension);
        MarkRenderer.INSTANCE.refresh();
        Trace.chat("Cleared " + n + " marked blocks in this dimension.");
    }

    /** "Marked here: 3 input, 5 output chests" (counting chests near you; far ones by block). */
    private static String markCounts() {
        Minecraft mc = Minecraft.getMinecraft();
        String w = Marks.worldKey();
        if (w == null) return "";
        int in = 0, out = 0;
        for (Container c : ContainerScanner.scan(mc.theWorld, mc.thePlayer, Config.migrateRadius)) {
            Marks.Kind k = Marks.INSTANCE.kindOf(w, mc.thePlayer.dimension, c, null);
            if (k == Marks.Kind.INPUT) in++;
            else if (k == Marks.Kind.OUTPUT) out++;
        }
        return "Marked within " + Config.migrateRadius + " blocks: " + in + " input, " + out + " output chests.";
    }

    /** The settings screen saved: re-read the file so the next run uses the new values. */
    @SubscribeEvent
    public void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
        if (!ChestSorter.MODID.equals(event.modID)) return;
        Config.sync();
        ChestSorter.LOG.info("Settings saved and reloaded");
    }

    /** Opens the settings screen (stops a running sort first: any screen would stop it anyway). */
    public void openSettings() {
        if (isRunning()) stop("settings opened");
        Minecraft.getMinecraft()
            .displayGuiScreen(new SettingsScreen(null));
    }

    /** For /cs config: open once chat has closed (closing chat would otherwise close the settings too). */
    public void openSettingsWhenScreenCloses() {
        pendingSettings = true;
        pendingTicks = 0;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (pendingSettings) {
            if (mc.currentScreen == null) {
                pendingSettings = false;
                openSettings();
            } else if (++pendingTicks > 100) {
                pendingSettings = false;
            }
        }
        if (pending != null && mc.thePlayer != null) {
            if (mc.currentScreen == null) {
                Run.Mode m = pending;
                pending = null;
                start(m, pendingMigrate);
            } else if (++pendingTicks > 100) {
                pending = null;
                Trace.chat("Not starting: a screen stayed open.");
            }
        }
        if (run == null) return;
        if (mc.thePlayer == null || mc.theWorld == null) {
            run.abort("left the world");
            run = null;
            return;
        }
        run.tick();
        if (run.finished()) {
            lastResult = run.status();
            run = null;
        }
    }

    /** For commands: chat is still open while a command runs, so start on the first tick after it closes. */
    public void startWhenScreenCloses(Run.Mode mode) {
        startWhenScreenCloses(mode, false);
    }

    /** As above; {@code migrate}: move everything from the marked input chests into the marked output chests. */
    public void startWhenScreenCloses(Run.Mode mode, boolean migrate) {
        if (isRunning()) {
            Trace.chat("Already running: " + run.status());
            return;
        }
        pending = mode;
        pendingMigrate = migrate;
        pendingTicks = 0;
    }

    public boolean isRunning() {
        return run != null && !run.finished();
    }

    public void start(Run.Mode mode) {
        start(mode, false);
    }

    public void start(Run.Mode mode, boolean migrate) {
        if (isRunning()) {
            Trace.chat("Already running: " + run.status());
            return;
        }
        Trace.chat(
            (mode == Run.Mode.PLAN_ONLY ? "Planning (nothing will be moved)"
                : migrate ? "Moving storage from input to output chests" : "Sorting") + ". Press the key, Esc, "
                + "or any movement key to stop.");
        run = new Run(mode, trace, migrate);
        if (!run.begin() || run.finished()) run = null;
    }

    public void stop(String reason) {
        if (!isRunning()) {
            Trace.chat("Nothing is running.");
            return;
        }
        run.abort(reason);
        run = null;
    }

    public String status() {
        return isRunning() ? run.status() : "idle (last run: " + lastResult + ")";
    }

    /** Stage-1 style list of nearby containers, no survey. */
    public void listContainers() {
        Minecraft mc = Minecraft.getMinecraft();
        List<Container> found = ContainerScanner.scan(mc.theWorld, mc.thePlayer, Config.scanRadius);
        Trace.chat("Found " + found.size() + " containers within " + Config.scanRadius + " blocks.");
        for (int i = 0; i < found.size() && i < 10; i++) {
            Trace.chat(EnumChatFormatting.GRAY + "#" + i + " " + found.get(i));
        }
        if (found.size() > 10) Trace.chat(EnumChatFormatting.GRAY + "... and " + (found.size() - 10) + " more.");
    }

    /** Shows how the held item would be categorised. */
    public void explainHeld() {
        ItemStack held = Minecraft.getMinecraft().thePlayer.getHeldItem();
        if (held == null) {
            Trace.chat("Hold an item to see its category.");
            return;
        }
        ItemKeys k = new ItemKeys();
        int key = k.keyOf(held);
        ItemInfo info = k.info(key);
        String group = com.whalestorm.chestsorter.core.Taxonomy.parse(Config.groups)
            .groupOf(info.category);
        Trace.chat(
            info.label + EnumChatFormatting.GRAY
                + " -> category "
                + EnumChatFormatting.RESET
                + info.category
                + EnumChatFormatting.GRAY
                + ", group "
                + EnumChatFormatting.RESET
                + group);
        Trace.chat(
            EnumChatFormatting.GRAY + "ore names: "
                + k.oreNames(key)
                + ", mod: "
                + ItemKeys.modIdOf(held)
                + ", max stack: "
                + info.maxStack);
    }

    public Trace trace() {
        return trace;
    }
}
