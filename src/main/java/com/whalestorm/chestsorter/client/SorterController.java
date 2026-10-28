package com.whalestorm.chestsorter.client;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import com.whalestorm.chestsorter.ChestSorter;
import com.whalestorm.chestsorter.Config;
import com.whalestorm.chestsorter.core.Container;
import com.whalestorm.chestsorter.core.SorterState;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Stage 1: the toggle key runs one container scan and lists the results in chat, then returns to IDLE.
 * No movement, no clicks, no chest opening.
 */
public class SorterController {

    /** Chat lines listing individual containers; the full list always goes to the log. */
    private static final int MAX_CHAT_LINES = 10;

    private SorterState state = SorterState.IDLE;

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        if (KeyBindings.TOGGLE.isPressed()) {
            setState(state.toggled());
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || state == SorterState.IDLE) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) {
            // Left the world mid-run: stop without trying to chat.
            state = SorterState.IDLE;
            return;
        }
        if (state == SorterState.SCAN) {
            List<Container> found = ContainerScanner.scan(mc.theWorld, mc.thePlayer, Config.scanRadius);
            report(found);
            // Stage 1 stops here. SURVEY (opening chests) is the next stage.
            setState(SorterState.IDLE);
        }
    }

    private void report(List<Container> found) {
        ChestSorter.LOG.info("Scan within {} blocks found {} containers", Config.scanRadius, found.size());
        for (Container c : found) {
            ChestSorter.LOG.info("  {}", c);
        }
        chat(
            EnumChatFormatting.GOLD + "[Chest Sorter] "
                + EnumChatFormatting.RESET
                + "Found "
                + found.size()
                + (found.size() == 1 ? " container" : " containers")
                + " within "
                + Config.scanRadius
                + " blocks.");
        for (int i = 0; i < found.size() && i < MAX_CHAT_LINES; i++) {
            chat(EnumChatFormatting.GRAY + "  " + found.get(i));
        }
        if (found.size() > MAX_CHAT_LINES) {
            chat(EnumChatFormatting.GRAY + "  ... and " + (found.size() - MAX_CHAT_LINES) + " more (see log).");
        }
    }

    private void setState(SorterState next) {
        ChestSorter.LOG.info("State {} -> {}", state, next);
        state = next;
        chat(EnumChatFormatting.GOLD + "[Chest Sorter] " + EnumChatFormatting.RESET + next);
    }

    private static void chat(String message) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer != null) {
            mc.thePlayer.addChatMessage(new ChatComponentText(message));
        }
    }
}
