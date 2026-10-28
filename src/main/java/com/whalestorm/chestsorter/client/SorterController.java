package com.whalestorm.chestsorter.client;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import com.whalestorm.chestsorter.ChestSorter;
import com.whalestorm.chestsorter.core.SorterState;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Stage 0: proves the mod loads in GTNH and the toggle key works. No world access, no movement, no clicks.
 * Later stages hang SCAN/SURVEY/... work off onClientTick.
 */
public class SorterController {

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
        }
    }

    private void setState(SorterState next) {
        ChestSorter.LOG.info("State {} -> {}", state, next);
        state = next;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer != null) {
            mc.thePlayer.addChatMessage(
                new ChatComponentText(EnumChatFormatting.GOLD + "[Chest Sorter] " + EnumChatFormatting.RESET + next));
        }
    }
}
