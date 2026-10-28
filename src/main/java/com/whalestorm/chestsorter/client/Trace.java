package com.whalestorm.chestsorter.client;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import com.whalestorm.chestsorter.ChestSorter;
import com.whalestorm.chestsorter.Config;

/**
 * Debug record of one run. Everything goes to the game log; with traceFile on it also goes to
 * .minecraft/chestsorter/trace-<time>.log, one line per event, prefixed with the client tick number.
 * Chat gets results and errors (and every step when verboseChat is on).
 */
public final class Trace {

    private PrintWriter file;
    private File path;
    private long tick;

    public void open(String why) {
        close();
        if (!Config.traceFile) return;
        try {
            File dir = new File(Minecraft.getMinecraft().mcDataDir, "chestsorter");
            if (!dir.isDirectory() && !dir.mkdirs()) throw new IllegalStateException("cannot create " + dir);
            String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(new Date());
            path = new File(dir, "trace-" + stamp + ".log");
            file = new PrintWriter(new OutputStreamWriter(new FileOutputStream(path), StandardCharsets.UTF_8), true);
            file.println("Chest Sorter " + com.whalestorm.chestsorter.Tags.VERSION + " trace: " + why);
        } catch (Exception e) {
            file = null;
            path = null;
            ChestSorter.LOG.warn("Trace file disabled: {}", e.toString());
        }
    }

    public void close() {
        if (file != null) file.close();
        file = null;
    }

    public File path() {
        return path;
    }

    public void tick() {
        tick++;
    }

    /** Log + trace file only. */
    public void log(String msg) {
        ChestSorter.LOG.info(msg);
        if (file != null) file.println("[t" + tick + "] " + msg);
    }

    /** Log + file, and chat when verboseChat is on. */
    public void step(String msg) {
        log(msg);
        if (Config.verboseChat) chat(EnumChatFormatting.GRAY + msg);
    }

    /** Log + file + chat. */
    public void info(String msg) {
        log(msg);
        chat(msg);
    }

    public void warn(String msg) {
        ChestSorter.LOG.warn(msg);
        if (file != null) file.println("[t" + tick + "] WARN " + msg);
        chat(EnumChatFormatting.YELLOW + msg);
    }

    public void error(String msg) {
        ChestSorter.LOG.error(msg);
        if (file != null) file.println("[t" + tick + "] ERROR " + msg);
        chat(EnumChatFormatting.RED + msg);
    }

    public static void chat(String msg) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer != null) {
            mc.thePlayer.addChatMessage(
                new ChatComponentText(EnumChatFormatting.GOLD + "[Chest Sorter] " + EnumChatFormatting.RESET + msg));
        }
    }
}
