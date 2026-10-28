package com.whalestorm.chestsorter.client;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

import com.whalestorm.chestsorter.Config;
import com.whalestorm.chestsorter.core.Container;
import com.whalestorm.chestsorter.core.ItemInfo;

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

    private SorterController() {}

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        if (KeyBindings.TOGGLE.isPressed()) {
            if (isRunning()) stop("stopped by key");
            else start(Run.Mode.FULL);
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
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
        Trace.chat((mode == Run.Mode.PLAN_ONLY ? "Planning (nothing will be moved)" : "Sorting") + ". Press the key, Esc, "
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
        Trace.chat(info.label + EnumChatFormatting.GRAY + " -> " + EnumChatFormatting.RESET + info.category);
        Trace.chat(EnumChatFormatting.GRAY + "ore names: " + k.oreNames(key) + ", mod: " + ItemKeys.modIdOf(held)
            + ", max stack: " + info.maxStack);
    }

    public Trace trace() {
        return trace;
    }
}
