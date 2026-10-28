package com.whalestorm.chestsorter.client;

import java.util.List;

import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

import com.whalestorm.chestsorter.Config;
import com.whalestorm.chestsorter.core.BlockPos;
import com.whalestorm.chestsorter.core.PathFinder;

/**
 * Walks the player to a spot within reach of a block, one tick at a time.
 *
 * Rotation smoothing (at most 35 degrees yaw and 25 degrees pitch per tick) follows LootGameSolver by KyroXova
 * (MIT, github.com/KyroXova/LootGameSolver). Unlike LootGameSolver, which pushes the player's velocity straight at the
 * target, this follows an A* path using normal movement input, so walls and corners are handled.
 */
public final class Walker {

    public enum Status {
        RUNNING,
        ARRIVED,
        FAILED
    }

    /** Vanilla eye height above the feet, used for the reach test while planning. */
    public static final double EYE = 1.62;

    private final AutoInput input;
    private final Trace trace;

    private BlockPos target;
    private List<BlockPos> path;
    private int index;
    private int repaths;
    private double bestDist;
    private int sinceProgress;
    private String failure;

    public Walker(AutoInput input, Trace trace) {
        this.input = input;
        this.trace = trace;
    }

    public String failure() {
        return failure;
    }

    public void start(BlockPos target) {
        this.target = target;
        this.path = null;
        this.index = 0;
        this.repaths = 0;
        this.failure = null;
        resetProgress();
    }

    public void stop() {
        input.forward = 0f;
        input.jumpNext = false;
        path = null;
    }

    /** True when the player's eye is within reach of the target block's centre. */
    public static boolean inReach(EntityClientPlayerMP p, BlockPos b) {
        double dx = b.x + 0.5 - p.posX;
        double dy = b.y + 0.5 - (p.posY + p.getEyeHeight());
        double dz = b.z + 0.5 - p.posZ;
        return dx * dx + dy * dy + dz * dz <= Config.reach * Config.reach;
    }

    public static BlockPos feet(EntityClientPlayerMP p) {
        return new BlockPos(
            MathHelper.floor_double(p.posX),
            MathHelper.floor_double(p.boundingBox.minY + 0.01),
            MathHelper.floor_double(p.posZ));
    }

    public Status tick(World world, EntityClientPlayerMP p) {
        if (inReach(p, target)) {
            stop();
            return Status.ARRIVED;
        }
        if (!Config.walking) return fail("out of reach and walking is off");
        if (path == null) {
            BlockPos from = feet(p);
            path = PathFinder.find(
                new ClientTerrain(world),
                from,
                PathFinder.withinReach(target, Config.reach - 0.3, EYE),
                Config.maxPathNodes);
            index = 0;
            resetProgress();
            if (path == null) return fail("no walkable path from " + from + " to " + target);
            trace.step("Path to " + target + ": " + path.size() + " steps");
            if (path.isEmpty()) {
                // A* thinks we already stand within reach, but the live check above disagreed (e.g. half-block).
                return fail("standing next to the chest but still out of reach");
            }
        }
        if (index >= path.size()) {
            // End of path but not in reach: re-path from here.
            return repath("path ended out of reach");
        }

        BlockPos node = path.get(index);
        double cx = node.x + 0.5, cz = node.z + 0.5;
        double dx = cx - p.posX, dz = cz - p.posZ;
        double dist = Math.sqrt(dx * dx + dz * dz);
        double feetY = p.boundingBox.minY;

        if (dist < 0.35 && feetY > node.y - 0.6 && feetY < node.y + 0.6) {
            index++;
            resetProgress();
            return Status.RUNNING;
        }

        // Turn toward the node, then walk. Don't walk while facing far off the line.
        float yaw = (float) (Math.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
        p.rotationYaw = approach(p.rotationYaw, yaw, 35.0F);
        p.rotationPitch = approach(p.rotationPitch, 10.0F, 25.0F);
        float off = Math.abs(MathHelper.wrapAngleTo180_float(p.rotationYaw - yaw));
        input.forward = off > 45.0F ? 0f : (dist < 0.6 ? 0.4f : 1.0f);
        input.jumpNext = node.y > MathHelper.floor_double(feetY + 0.01) && p.onGround;

        // Stuck detection, only while on the ground (falling is progress).
        if (p.onGround) {
            if (dist < bestDist - 0.05) {
                bestDist = dist;
                sinceProgress = 0;
            } else if (++sinceProgress > Config.stuckTicks) {
                return repath("no progress toward " + node + " for " + Config.stuckTicks + " ticks");
            }
        }
        return Status.RUNNING;
    }

    private Status repath(String why) {
        if (++repaths > Config.maxRepaths) return fail(why + "; gave up after " + Config.maxRepaths + " re-paths");
        trace.step("Re-path " + repaths + ": " + why);
        path = null;
        input.forward = 0f;
        input.jumpNext = false;
        return Status.RUNNING;
    }

    private Status fail(String why) {
        failure = why;
        stop();
        return Status.FAILED;
    }

    private void resetProgress() {
        bestDist = Double.MAX_VALUE;
        sinceProgress = 0;
    }

    /** Move {@code current} toward {@code target} by at most {@code maxStep} degrees (from LootGameSolver). */
    static float approach(float current, float target, float maxStep) {
        float diff = current - target;
        while (diff >= 180.0F) diff -= 360.0F;
        while (diff < -180.0F) diff += 360.0F;
        if (diff > maxStep) diff = maxStep;
        if (diff < -maxStep) diff = -maxStep;
        return current - diff;
    }

    /** Smoothly aim at a block's centre. @return true once within 5 degrees. */
    public static boolean aimAt(EntityClientPlayerMP p, BlockPos b) {
        double dx = b.x + 0.5 - p.posX;
        double dy = b.y + 0.5 - (p.posY + p.getEyeHeight());
        double dz = b.z + 0.5 - p.posZ;
        double h = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
        float pitch = (float) (-(Math.atan2(dy, h) * 180.0D / Math.PI));
        p.rotationYaw = approach(p.rotationYaw, yaw, 35.0F);
        p.rotationPitch = approach(p.rotationPitch, pitch, 25.0F);
        return Math.abs(MathHelper.wrapAngleTo180_float(p.rotationYaw - yaw)) < 5.0F
            && Math.abs(p.rotationPitch - pitch) < 5.0F;
    }
}
