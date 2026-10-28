package com.whalestorm.chestsorter.client;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.IMob;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import com.whalestorm.chestsorter.Config;

/**
 * Stop conditions checked every tick of a run. Any hit stops the run immediately; the controller then makes sure no
 * item is left on the cursor before closing a chest.
 */
public final class Safety {

    private World world;
    private float lastHealth;
    private boolean[] wasDown = new boolean[0];

    /** Call at run start. @return a reason the run cannot start, or null. */
    public String begin(Minecraft mc) {
        EntityClientPlayerMP p = mc.thePlayer;
        world = mc.theWorld;
        lastHealth = p.getHealth();
        KeyBinding[] keys = watched(mc);
        wasDown = new boolean[keys.length];
        for (int i = 0; i < keys.length; i++) wasDown[i] = isDown(keys[i]);
        if (mc.currentScreen != null) return "close the open screen first";
        if (p.capabilities.isFlying) return "stop flying first (the walker only walks)";
        if (p.isSneaking()) return "release sneak first";
        String danger = threat(mc);
        if (danger != null) return danger;
        return null;
    }

    /** No screen may be open. */
    public static final int GUI_NONE = 0;
    /** A chest is opening or closing: no screen or a chest screen are both fine. */
    public static final int GUI_TRANSITION = 1;
    /** A chest is open: its screen must stay open. */
    public static final int GUI_OPEN = 2;

    /**
     * @param gui one of GUI_NONE, GUI_TRANSITION, GUI_OPEN
     * @return why the run must stop, or null to continue
     */
    public String check(Minecraft mc, int gui) {
        EntityClientPlayerMP p = mc.thePlayer;
        if (p == null || mc.theWorld == null) return "left the world";
        if (mc.theWorld != world) return "changed world or dimension";
        if (p.isDead || p.getHealth() <= 0) return "player died";
        if (p.getHealth() < lastHealth || p.hurtTime > 0) return "took damage";
        lastHealth = p.getHealth();

        if (gui == GUI_OPEN) {
            if (!(mc.currentScreen instanceof GuiContainer)) return "chest screen was closed (Esc stops the run)";
        } else if (gui == GUI_TRANSITION) {
            if (mc.currentScreen != null && !(mc.currentScreen instanceof GuiContainer)) {
                return "a screen was opened (" + mc.currentScreen.getClass()
                    .getSimpleName() + ")";
            }
        } else if (mc.currentScreen != null) {
            return "a screen was opened (" + mc.currentScreen.getClass()
                .getSimpleName() + ")";
        }

        // You take over: any newly pressed movement, jump, sneak, attack, use or inventory key.
        KeyBinding[] keys = watched(mc);
        for (int i = 0; i < keys.length && i < wasDown.length; i++) {
            boolean down = isDown(keys[i]);
            if (down && !wasDown[i]) return "you pressed a control key";
            wasDown[i] = down;
        }
        return threat(mc);
    }

    private String threat(Minecraft mc) {
        EntityClientPlayerMP p = mc.thePlayer;
        if (p.getHealth() < p.getMaxHealth() * Config.minHealthFraction) {
            return "health below " + Math.round(Config.minHealthFraction * 100) + "%";
        }
        if (Config.hostileRadius > 0) {
            double r = Config.hostileRadius;
            AxisAlignedBB box = AxisAlignedBB
                .getBoundingBox(p.posX - r, p.posY - r, p.posZ - r, p.posX + r, p.posY + r, p.posZ + r);
            @SuppressWarnings("unchecked")
            List<Entity> near = (List<Entity>) (List<?>) mc.theWorld.getEntitiesWithinAABB(EntityLivingBase.class, box);
            for (Entity e : near) {
                if (e instanceof IMob && !e.isDead) return "hostile mob nearby (" + e.getClass()
                    .getSimpleName() + ")";
            }
        }
        return null;
    }

    private static KeyBinding[] watched(Minecraft mc) {
        return new KeyBinding[] { mc.gameSettings.keyBindForward, mc.gameSettings.keyBindBack,
            mc.gameSettings.keyBindLeft, mc.gameSettings.keyBindRight, mc.gameSettings.keyBindJump,
            mc.gameSettings.keyBindSneak, mc.gameSettings.keyBindAttack, mc.gameSettings.keyBindUseItem,
            mc.gameSettings.keyBindInventory };
    }

    /** Raw hardware state, independent of our replaced movement input. Mouse buttons are stored as code - 100. */
    private static boolean isDown(KeyBinding k) {
        int code = k.getKeyCode();
        try {
            if (code < 0) return Mouse.isButtonDown(code + 100);
            if (code > 0) return Keyboard.isKeyDown(code);
        } catch (Throwable t) {
            return false;
        }
        return false;
    }
}
