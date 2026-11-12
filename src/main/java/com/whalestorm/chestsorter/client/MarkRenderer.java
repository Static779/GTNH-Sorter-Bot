package com.whalestorm.chestsorter.client;

import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraftforge.client.event.RenderWorldLastEvent;

import org.lwjgl.opengl.GL11;

import com.whalestorm.chestsorter.Config;
import com.whalestorm.chestsorter.core.BlockPos;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Draws an outline around marked chests, seen through walls: orange for input, green for output. Same GL calls as
 * Inventory Bogosorter's drop-off highlight (RendererCube), which runs in GTNH 2.9. Off with the showMarks setting.
 */
public final class MarkRenderer {

    public static final MarkRenderer INSTANCE = new MarkRenderer();
    /** Only marks this close are drawn. */
    private static final double RANGE = 96;

    private Map<BlockPos, Marks.Kind> cached;
    private String cachedWorld;
    private int cachedDim;
    private long cachedAt;

    private MarkRenderer() {}

    /** Forget the cached marks (after they change). */
    void refresh() {
        cached = null;
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        if (!Config.showMarks) return;
        Minecraft mc = Minecraft.getMinecraft();
        EntityLivingBase viewer = mc.renderViewEntity;
        if (viewer == null || mc.theWorld == null || mc.thePlayer == null) return;
        String world = Marks.worldKey();
        if (world == null) return;
        int dim = mc.thePlayer.dimension;
        long now = System.currentTimeMillis();
        if (cached == null || !world.equals(cachedWorld) || dim != cachedDim || now - cachedAt > 2000) {
            cached = Marks.INSTANCE.here(world, dim);
            cachedWorld = world;
            cachedDim = dim;
            cachedAt = now;
        }
        if (cached.isEmpty()) return;

        double px = viewer.lastTickPosX + (viewer.posX - viewer.lastTickPosX) * event.partialTicks;
        double py = viewer.lastTickPosY + (viewer.posY - viewer.lastTickPosY) * event.partialTicks;
        double pz = viewer.lastTickPosZ + (viewer.posZ - viewer.lastTickPosZ) * event.partialTicks;

        GL11.glPushMatrix();
        GL11.glTranslated(-px, -py, -pz);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glLineWidth(2.0f);
        GL11.glBegin(GL11.GL_LINES);
        for (Map.Entry<BlockPos, Marks.Kind> e : cached.entrySet()) {
            BlockPos b = e.getKey();
            double dx = b.x + 0.5 - px, dy = b.y + 0.5 - py, dz = b.z + 0.5 - pz;
            if (dx * dx + dy * dy + dz * dz > RANGE * RANGE) continue;
            if (e.getValue() == Marks.Kind.INPUT) GL11.glColor4ub((byte) 255, (byte) 150, (byte) 30, (byte) 220);
            else GL11.glColor4ub((byte) 60, (byte) 230, (byte) 80, (byte) 220);
            box(b.x - 0.02, b.y - 0.02, b.z - 0.02, b.x + 1.02, b.y + 1.02, b.z + 1.02);
        }
        GL11.glEnd();
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glPopMatrix();
    }

    private static void box(double x0, double y0, double z0, double x1, double y1, double z1) {
        // bottom
        line(x0, y0, z0, x1, y0, z0);
        line(x1, y0, z0, x1, y0, z1);
        line(x1, y0, z1, x0, y0, z1);
        line(x0, y0, z1, x0, y0, z0);
        // top
        line(x0, y1, z0, x1, y1, z0);
        line(x1, y1, z0, x1, y1, z1);
        line(x1, y1, z1, x0, y1, z1);
        line(x0, y1, z1, x0, y1, z0);
        // sides
        line(x0, y0, z0, x0, y1, z0);
        line(x1, y0, z0, x1, y1, z0);
        line(x1, y0, z1, x1, y1, z1);
        line(x0, y0, z1, x0, y1, z1);
    }

    private static void line(double x0, double y0, double z0, double x1, double y1, double z1) {
        GL11.glVertex3d(x0, y0, z0);
        GL11.glVertex3d(x1, y1, z1);
    }
}
