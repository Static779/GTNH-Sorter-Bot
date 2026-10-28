package com.whalestorm.chestsorter;

import java.io.File;
import java.util.Arrays;

import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

import com.whalestorm.chestsorter.core.Taxonomy;

/**
 * All settings, in config/chestsorter.cfg. Editable in game: Mods list → Chest Sorter → Config, the settings key, or
 * /cs config. Changes apply to the next run. Defaults favour safety over speed.
 */
public class Config {

    public static final String CAT_SORT = "sorting";
    public static final String CAT_MOVE = "movement";
    public static final String CAT_SAFETY = "safety";
    public static final String CAT_DEBUG = "debug";
    /** Categories shown in the settings screen, in order. */
    public static final String[] GUI_CATEGORIES = { CAT_SORT, CAT_MOVE, CAT_SAFETY, CAT_DEBUG };
    private static final String LANG = "chestsorter.cfg.";

    // sorting
    public static int scanRadius = 16;
    /** 0 = only minimise moves; higher = tidier layout (big categories together, related ones near). */
    public static double layoutWeight = 2.0;
    /** Smallest group of stacks that gets a chest of its own when there are spare chests. */
    public static int minStacksForOwnChest = 6;
    /** Singleplayer: read chests from the built-in server instead of opening them. */
    public static boolean localRead = true;
    /** Servers: read chests by looking at them (HoloInventory) instead of opening them. */
    public static boolean holoRead = true;
    /** Combine partial stacks of the same item in every chest the run handles. */
    public static boolean combineStacks = true;
    /** Percent of each chest to keep free for new items when deciding how narrow chests get. */
    public static int keepFreePercent = 15;
    /** Category group lines, see {@link Taxonomy}. */
    public static String[] groups = Taxonomy.DEFAULT_GROUPS.clone();

    // movement
    public static boolean walking = true;
    public static boolean sprint = true;
    public static double reach = 4.0;
    public static int maxPathNodes = 20000;
    public static int stuckTicks = 40;
    public static int maxRepaths = 3;

    // safety / pacing
    public static int clickDelayTicks = 1;
    public static int settleTicks = 4;
    public static int openTimeoutTicks = 60;
    public static boolean verifyEveryTransfer = false;
    public static boolean finalRecheck = false;
    public static int hostileRadius = 8;
    public static double minHealthFraction = 0.5;
    public static int maxTrips = 300;

    // debug
    public static boolean traceFile = true;
    public static boolean verboseChat = false;

    /** Bump when defaults or layout change in a way existing config files should pick up. */
    private static final int CONFIG_VERSION = 4;

    /** The loaded file; null until {@link #init}. */
    public static Configuration config;

    public static void init(File configFile) {
        config = new Configuration(configFile);
        migrate();
        sync();
    }

    /** Older files: v2 made runs faster (reset those keys); v3 moved sorting settings into their own category. */
    private static void migrate() {
        Configuration c = config;
        int version = c.get(Configuration.CATEGORY_GENERAL, "configVersion", 1)
            .getInt();
        if (version < 2) {
            c.getCategory(CAT_SAFETY)
                .remove("clickDelayTicks");
            c.getCategory(CAT_SAFETY)
                .remove("settleTicks");
            c.getCategory(CAT_SAFETY)
                .remove("verifyEveryTransfer");
        }
        if (version < 3) {
            move(Configuration.CATEGORY_GENERAL, CAT_SORT, "scanRadius");
            move(Configuration.CATEGORY_GENERAL, CAT_SORT, "layoutWeight");
            move(CAT_SAFETY, CAT_SORT, "maxTrips");
        }
        if (version < 4) {
            // v4: tidiness default 1 -> 2 (sweep of 1116 rooms: groups whose chests touch 54% -> 74%, same moves,
            // nothing stranded). Only an untouched old default is changed.
            ConfigCategory s = c.getCategory(CAT_SORT);
            if (s.containsKey("layoutWeight") && "1.0".equals(
                s.get("layoutWeight")
                    .getString()
                    .trim())) s.remove("layoutWeight");
        }
        c.get(Configuration.CATEGORY_GENERAL, "configVersion", CONFIG_VERSION)
            .set(CONFIG_VERSION);
    }

    private static void move(String from, String to, String key) {
        ConfigCategory f = config.getCategory(from);
        if (!f.containsKey(key)) return;
        Property p = f.remove(key);
        ConfigCategory t = config.getCategory(to);
        if (!t.containsKey(key)) t.put(key, p);
    }

    /** Reads every value from the loaded file (after the settings screen changed it, too) and saves if needed. */
    public static void sync() {
        Configuration c = config;
        if (c == null) return;

        c.setCategoryComment(CAT_SORT, "How items are grouped and which chests they go to.");
        c.setCategoryLanguageKey(CAT_SORT, LANG + "cat." + CAT_SORT);
        scanRadius = c.getInt(
            "scanRadius",
            CAT_SORT,
            16,
            1,
            64,
            "Use containers within this many blocks of where you start.",
            LANG + "scanRadius");
        minStacksForOwnChest = c.getInt(
            "minStacksForOwnChest",
            CAT_SORT,
            6,
            1,
            1728,
            "With spare chests, a category (or group) gets a chest of its own once it has at least this many stacks. "
                + "Lower = more, narrower chests. Higher = broader chests (whole groups like 'dusts', 'metals').",
            LANG + "minStacksForOwnChest");
        keepFreePercent = c.getInt(
            "keepFreePercent",
            CAT_SORT,
            15,
            0,
            60,
            "Room to grow: when deciding how many chests each group gets, count every chest as this much smaller. "
                + "Higher = fewer, roomier chests per group; 0 = pack chests as full as possible.",
            LANG + "keepFreePercent");
        layoutWeight = dbl(
            CAT_SORT,
            "layoutWeight",
            2.0,
            0.0,
            10.0,
            "How much a tidy layout matters against moving fewer stacks. 0 = keep chests' current roles. 2 (default) = "
                + "each group's chests side by side and related groups near each other, at almost no extra moves.");
        Property g = c.get(
            CAT_SORT,
            "groups",
            Taxonomy.DEFAULT_GROUPS,
            "Category groups, one per line: 'name: member, prefix*, ...'. Lines are in layout order: neighbouring "
                + "groups go in neighbouring chests. Members are category names from /cs item (ore-dictionary "
                + "prefixes like dustTiny, or mod:<modid>:<tab>); a trailing * matches any name starting with it. "
                + "Anything not listed is grouped by mod, or goes to 'misc' when chests run short.");
        g.setLanguageKey(LANG + "groups");
        groups = g.getStringList();
        maxTrips = c.getInt("maxTrips", CAT_SORT, 300, 1, 10000, "Most carry trips per run.", LANG + "maxTrips");
        localRead = c.getBoolean(
            "localRead",
            CAT_SORT,
            true,
            "Singleplayer (and LAN host): read every chest straight from your world instead of walking to and "
                + "opening each one. Exact and instant. Has no effect on servers.",
            LANG + "localRead");
        holoRead = c.getBoolean(
            "holoRead",
            CAT_SORT,
            true,
            "Servers: read each chest by looking at it, using HoloInventory's data (no glasses needed), instead of "
                + "opening it. Needs HoloInventory on the server; if it sends nothing, chests are opened instead.",
            LANG + "holoRead");
        combineStacks = c.getBoolean(
            "combineStacks",
            CAT_SORT,
            true,
            "Combine partial stacks of the same item (e.g. 20 + 30 iron dust into one 50 stack) in every chest the "
                + "run opens, and visit untouched chests at the end to combine theirs. Frees slots.",
            LANG + "combineStacks");
        c.setCategoryPropertyOrder(
            CAT_SORT,
            Arrays.asList(
                "scanRadius",
                "localRead",
                "holoRead",
                "combineStacks",
                "minStacksForOwnChest",
                "keepFreePercent",
                "layoutWeight",
                "maxTrips",
                "groups"));

        c.setCategoryComment(CAT_MOVE, "Walking to chests.");
        c.setCategoryLanguageKey(CAT_MOVE, LANG + "cat." + CAT_MOVE);
        walking = c.getBoolean(
            "walking",
            CAT_MOVE,
            true,
            "Walk to chests out of reach. Off: only chests within reach of where you stand are used.",
            LANG + "walking");
        sprint = c
            .getBoolean("sprint", CAT_MOVE, true, "Sprint on straight stretches when food allows.", LANG + "sprint");
        reach = dbl(CAT_MOVE, "reach", 4.0, 2.0, 5.0, "Max distance (eye to chest centre) to open a chest.");
        maxPathNodes = c.getInt(
            "maxPathNodes",
            CAT_MOVE,
            20000,
            1000,
            200000,
            "Path search limit per walk. Raise for big, mazy rooms.",
            LANG + "maxPathNodes");
        stuckTicks = c.getInt(
            "stuckTicks",
            CAT_MOVE,
            40,
            10,
            400,
            "Ticks without progress before finding a new path.",
            LANG + "stuckTicks");
        maxRepaths = c
            .getInt("maxRepaths", CAT_MOVE, 3, 0, 20, "New paths to try before skipping a chest.", LANG + "maxRepaths");

        c.setCategoryComment(CAT_SAFETY, "Pacing and when to stop.");
        c.setCategoryLanguageKey(CAT_SAFETY, LANG + "cat." + CAT_SAFETY);
        clickDelayTicks = c.getInt(
            "clickDelayTicks",
            CAT_SAFETY,
            1,
            1,
            40,
            "Ticks between stack transfers (1 = one stack per tick).",
            LANG + "clickDelayTicks");
        settleTicks = c.getInt(
            "settleTicks",
            CAT_SAFETY,
            4,
            2,
            40,
            "Ticks to wait for the server before checking a chest before it closes. Raise on laggy servers.",
            LANG + "settleTicks");
        openTimeoutTicks = c.getInt(
            "openTimeoutTicks",
            CAT_SAFETY,
            60,
            10,
            400,
            "Ticks to wait for a chest to open.",
            LANG + "openTimeoutTicks");
        verifyEveryTransfer = c.getBoolean(
            "verifyEveryTransfer",
            CAT_SAFETY,
            false,
            "Also wait settleTicks and check after every single transfer, not only before each chest closes. Slower.",
            LANG + "verifyEveryTransfer");
        finalRecheck = c.getBoolean(
            "finalRecheck",
            CAT_SAFETY,
            false,
            "After sorting, walk back and re-open every touched chest to compare totals. Each chest is already checked "
                + "before it closes, so this is an extra, slower check.",
            LANG + "finalRecheck");
        hostileRadius = c.getInt(
            "hostileRadius",
            CAT_SAFETY,
            8,
            0,
            32,
            "Stop if a hostile mob is this close. 0 = off.",
            LANG + "hostileRadius");
        minHealthFraction = dbl(
            CAT_SAFETY,
            "minHealthFraction",
            0.5,
            0.0,
            1.0,
            "Stop if health falls below this fraction of max.");
        c.setCategoryPropertyOrder(
            CAT_SAFETY,
            Arrays.asList(
                "clickDelayTicks",
                "settleTicks",
                "openTimeoutTicks",
                "verifyEveryTransfer",
                "finalRecheck",
                "hostileRadius",
                "minHealthFraction"));

        c.setCategoryComment(CAT_DEBUG, "Trace files and chat output.");
        c.setCategoryLanguageKey(CAT_DEBUG, LANG + "cat." + CAT_DEBUG);
        traceFile = c.getBoolean(
            "traceFile",
            CAT_DEBUG,
            true,
            "Write every step, click and check of each run to .minecraft/chestsorter/trace-<time>.log.",
            LANG + "traceFile");
        verboseChat = c.getBoolean(
            "verboseChat",
            CAT_DEBUG,
            false,
            "Print every step in chat, not just results.",
            LANG + "verboseChat");

        if (c.hasChanged()) c.save();
    }

    /** Saves one boolean right away (used by /cs verbose). */
    public static void setBoolean(String category, String key, boolean value) {
        if (config == null) return;
        config.getCategory(category)
            .get(key)
            .set(value);
        sync();
    }

    /**
     * A number setting stored as a real double (older files kept these as text, which the settings screen can't
     * check): converts the old entry, keeping its value.
     */
    private static double dbl(String cat, String key, double def, double min, double max, String comment) {
        ConfigCategory cc = config.getCategory(cat);
        Double old = null;
        if (cc.containsKey(key) && cc.get(key)
            .getType() != Property.Type.DOUBLE) {
            try {
                old = Double.parseDouble(
                    cc.get(key)
                        .getString()
                        .trim());
            } catch (NumberFormatException e) {
                ChestSorter.LOG.warn("Setting {} had an unreadable value; using the default", key);
            }
            cc.remove(key);
        }
        Property p = config
            .get(cat, key, def, comment + " [range: " + min + " ~ " + max + ", default: " + def + "]", min, max);
        p.setLanguageKey(LANG + key);
        if (old != null) p.set(old);
        double v = p.getDouble(def);
        return v < min ? min : (v > max ? max : v);
    }
}
