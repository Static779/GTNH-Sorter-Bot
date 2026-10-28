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
                start(m);
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
        if (isRunning()) {
            Trace.chat("Already running: " + run.status());
            return;
        }
        pending = mode;
        pendingTicks = 0;
    }

    public boolean isRunning() {
        return run != null && !run.finished();
    }

    public void start(Run.Mode mode) {
        if (isRunning()) {
            Trace.chat("Already running: " + run.status());
            return;
        }
        Trace.chat(
            (mode == Run.Mode.PLAN_ONLY ? "Planning (nothing will be moved)" : "Sorting") + ". Press the key, Esc, "
                + "or any movement key to stop.");
        run = new Run(mode, trace);
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
