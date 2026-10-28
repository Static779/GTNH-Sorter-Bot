package com.whalestorm.chestsorter;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

/** All settings, read from config/chestsorter.cfg. Defaults favour safety over speed. */
public class Config {

    public static final String CAT_MOVE = "movement";
    public static final String CAT_SAFETY = "safety";
    public static final String CAT_DEBUG = "debug";

    /** How far (blocks) the scanner looks for containers. */
    public static int scanRadius = 16;

    // movement
    public static boolean walking = true;
    public static double reach = 4.0;
    public static int maxPathNodes = 20000;
    public static int stuckTicks = 40;
    public static int maxRepaths = 3;

    // safety / pacing
    public static int clickDelayTicks = 3;
    public static int settleTicks = 5;
    public static int openTimeoutTicks = 60;
    public static boolean verifyEveryTransfer = true;
    public static int hostileRadius = 8;
    public static double minHealthFraction = 0.5;
    public static int maxTrips = 300;

    // debug
    public static boolean traceFile = true;
    public static boolean verboseChat = false;

    public static void synchronizeConfiguration(File configFile) {
        Configuration c = new Configuration(configFile);

        scanRadius = c.getInt(
            "scanRadius",
            Configuration.CATEGORY_GENERAL,
            scanRadius,
            1,
            64,
            "Container scan radius in blocks.");

        walking = c.getBoolean(
            "walking",
            CAT_MOVE,
            walking,
            "Walk to chests out of reach. If false, only chests within reach of where you stand are used.");
        reach = c.getFloat("reach", CAT_MOVE, (float) reach, 2.0f, 5.0f, "Max distance (eye to chest centre) to open.");
        maxPathNodes = c.getInt("maxPathNodes", CAT_MOVE, maxPathNodes, 1000, 200000, "A* search limit per path.");
        stuckTicks = c.getInt("stuckTicks", CAT_MOVE, stuckTicks, 10, 400, "Ticks without progress before re-pathing.");
        maxRepaths = c.getInt("maxRepaths", CAT_MOVE, maxRepaths, 0, 20, "Re-path attempts before giving up a chest.");

        clickDelayTicks = c
            .getInt("clickDelayTicks", CAT_SAFETY, clickDelayTicks, 1, 40, "Ticks between stack transfers.");
        settleTicks = c.getInt(
            "settleTicks",
            CAT_SAFETY,
            settleTicks,
            2,
            40,
            "Ticks to wait for the server before checking a result. Raise on laggy servers.");
        openTimeoutTicks = c
            .getInt("openTimeoutTicks", CAT_SAFETY, openTimeoutTicks, 10, 400, "Ticks to wait for a chest to open.");
        verifyEveryTransfer = c.getBoolean(
            "verifyEveryTransfer",
            CAT_SAFETY,
            verifyEveryTransfer,
            "Wait settleTicks and check item totals after every single stack transfer. Slower, strongly recommended.");
        hostileRadius = c
            .getInt("hostileRadius", CAT_SAFETY, hostileRadius, 0, 32, "Stop if a hostile mob is this close. 0 = off.");
        minHealthFraction = c.getFloat(
            "minHealthFraction",
            CAT_SAFETY,
            (float) minHealthFraction,
            0.0f,
            1.0f,
            "Stop if health falls below this fraction of max.");
        maxTrips = c.getInt("maxTrips", CAT_SAFETY, maxTrips, 1, 10000, "Max carry trips per run.");

        traceFile = c.getBoolean(
            "traceFile",
            CAT_DEBUG,
            traceFile,
            "Write every step, click and check of each run to .minecraft/chestsorter/trace-<time>.log.");
        verboseChat = c
            .getBoolean("verboseChat", CAT_DEBUG, verboseChat, "Print every step in chat, not just results.");

        if (c.hasChanged()) {
            c.save();
        }
    }
}
