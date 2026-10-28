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
 * - Follows an A* path through vanilla movement input (collisions, gravity and step-up all stay vanilla).
 * - Cuts corners on level ground: steers straight at the furthest path node (up to 8 ahead) it can walk to in a
 * straight line, instead of visiting every block centre.
 * - Sprints on straight runs when food allows, turns while moving, and only slows down at the very end.
 * - Rotation smoothing follows LootGameSolver by KyroXova (MIT, github.com/KyroXova/LootGameSolver); this walker
 * turns faster (50 degrees per tick) since it is walking, not aiming.
 */
public final class Walker {

    public enum Status {
        RUNNING,
        ARRIVED,
        FAILED
    }

    /** Vanilla eye height above the feet, used for the reach test while planning. */
    public static final double EYE = 1.62;
    private static final int LOOKAHEAD = 8;

    private final AutoInput input;
    private final Trace trace;

    private BlockPos target;
    private ClientTerrain terrain;
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
        input.sprint = false;
        path = null;
    }

    /** True when the player's eye is within reach of the target block's centre. */
    public static boolean inReach(EntityClientPlayerMP p, BlockPos b) {
        double dx = b.x + 0.5 - p.posX;
        double dy = b.y + 0.5 - (p.posY + p.getEyeHeight());
        double dz = b.z + 0.5 - p.posZ;
        return dx * dx + dy * dy + dz * dz <= Config.reach * Config.reach;
    }

    /**
     * The path node the player is standing in. Uses feet height + 0.5 so that standing on a chest (0.875 tall) or a
     * slab (0.5) counts as being in the block above it, the same way the pathfinder treats them as floors.
     */
    public static BlockPos feet(EntityClientPlayerMP p) {
        return new BlockPos(
            MathHelper.floor_double(p.posX),
            MathHelper.floor_double(p.boundingBox.minY + 0.5),
            MathHelper.floor_double(p.posZ));
    }

    public Status tick(World world, EntityClientPlayerMP p) {
        if (inReach(p, target)) {
            stop();
            return Status.ARRIVED;
        }
        if (!Config.walking) return fail("out of reach and walking is off");
        if (path == null) {
            terrain = new ClientTerrain(world);
            BlockPos from = feet(p);
            path = PathFinder
                .find(terrain, from, PathFinder.withinReach(target, Config.reach - 0.3, EYE), Config.maxPathNodes);
            index = 0;
            resetProgress();
            if (path == null) return fail("no walkable path from " + from + " to " + target);
            trace.step("Path to " + target + ": " + path.size() + " steps");
            if (path.isEmpty()) return fail("standing next to the chest but still out of reach");
        }

        BlockPos here = feet(p);
        // Skip nodes already reached (or passed while cutting corners).
        for (int k = path.size() - 1; k >= index; k--) {
            BlockPos n = path.get(k);
            boolean last = k == path.size() - 1;
            if (n.y == here.y && horizontalDist(p, n) < (last ? 0.35 : 0.6)) {
                index = k + 1;
                resetProgress();
                break;
            }
        }
        if (index >= path.size()) return repath("path ended out of reach");

        // Steer at the furthest node we can walk to in a straight line on this level.
        int aim = index;
        BlockPos first = path.get(index);
        if (first.y == here.y) {
            while (aim + 1 < path.size() && aim + 1 - index <= LOOKAHEAD
                && path.get(aim + 1).y == here.y
                && straightLineClear(p, path.get(aim + 1))) {
                aim++;
            }
        }
        BlockPos node = path.get(aim);
        boolean finalNode = aim == path.size() - 1;
        double dx = node.x + 0.5 - p.posX, dz = node.z + 0.5 - p.posZ;
        double dist = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float) (Math.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
        p.rotationYaw = approach(p.rotationYaw, yaw, 50.0F);
        p.rotationPitch = approach(p.rotationPitch, 10.0F, 25.0F);
        float off = Math.abs(MathHelper.wrapAngleTo180_float(p.rotationYaw - yaw));

        input.forward = off > 60.0F ? 0f : (finalNode && dist < 0.8 ? 0.5f : 1.0f);
        input.jumpNext = first.y > here.y && p.onGround && horizontalDist(p, first) < 1.3;
        input.sprint = Config.sprint && input.forward >= 1.0f
            && off < 20.0F
            && remaining(p, aim) > 2.5
            && p.getFoodStats()
                .getFoodLevel() > 6;
        p.setSprinting(input.sprint);

        // Stuck detection, only while on the ground (falling is progress).
        double d = horizontalDist(p, first);
        if (p.onGround) {
            if (d < bestDist - 0.05) {
                bestDist = d;
                sinceProgress = 0;
            } else if (++sinceProgress > Config.stuckTicks) {
                return repath("no progress toward " + first + " for " + Config.stuckTicks + " ticks");
            }
        }
        return Status.RUNNING;
    }

    /** Distance still to walk: to the aimed node, then along the rest of the path. */
    private double remaining(EntityClientPlayerMP p, int aim) {
        double r = horizontalDist(p, path.get(aim));
        for (int k = aim + 1; k < path.size(); k++) {
            BlockPos a = path.get(k - 1), b = path.get(k);
            r += Math.sqrt((a.x - b.x) * (a.x - b.x) + (a.z - b.z) * (a.z - b.z));
        }
        return r;
    }

    /**
     * True if every point on the straight line from the player to the node's centre, widened by the player's half
     * width (0.3) on each side, is a standable cell at the node's height.
     */
    private boolean straightLineClear(EntityClientPlayerMP p, BlockPos n) {
        double x0 = p.posX, z0 = p.posZ, x1 = n.x + 0.5, z1 = n.z + 0.5;
        double len = Math.sqrt((x1 - x0) * (x1 - x0) + (z1 - z0) * (z1 - z0));
        int steps = Math.max(1, (int) Math.ceil(len / 0.2));
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            double x = x0 + (x1 - x0) * t, z = z0 + (z1 - z0) * t;
            for (double ox = -0.3; ox <= 0.3; ox += 0.6) {
                for (double oz = -0.3; oz <= 0.3; oz += 0.6) {
                    int bx = MathHelper.floor_double(x + ox), bz = MathHelper.floor_double(z + oz);
                    if (!PathFinder.standable(terrain, bx, n.y, bz)) return false;
                }
            }
        }
        return true;
    }

    private static double horizontalDist(EntityClientPlayerMP p, BlockPos n) {
        double dx = n.x + 0.5 - p.posX, dz = n.z + 0.5 - p.posZ;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private Status repath(String why) {
        if (++repaths > Config.maxRepaths) return fail(why + "; gave up after " + Config.maxRepaths + " re-paths");
        trace.step("Re-path " + repaths + ": " + why);
        path = null;
        input.forward = 0f;
        input.jumpNext = false;
        input.sprint = false;
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

    /**
     * Turn toward a block's centre (60 degrees yaw, 45 pitch per tick). @return true once within 25 degrees. The
     * right-click itself carries the target block; vanilla 1.7.10 checks distance to it, not where you look.
     */
    public static boolean aimAt(EntityClientPlayerMP p, BlockPos b) {
        double dx = b.x + 0.5 - p.posX;
        double dy = b.y + 0.5 - (p.posY + p.getEyeHeight());
        double dz = b.z + 0.5 - p.posZ;
        double h = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
        float pitch = (float) (-(Math.atan2(dy, h) * 180.0D / Math.PI));
        p.rotationYaw = approach(p.rotationYaw, yaw, 60.0F);
        p.rotationPitch = approach(p.rotationPitch, pitch, 45.0F);
        return Math.abs(MathHelper.wrapAngleTo180_float(p.rotationYaw - yaw)) < 25.0F
            && Math.abs(p.rotationPitch - pitch) < 25.0F;
    }
}
