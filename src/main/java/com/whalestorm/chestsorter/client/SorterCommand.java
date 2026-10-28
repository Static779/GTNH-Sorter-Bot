package com.whalestorm.chestsorter.client;

import java.util.Arrays;
import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.EnumChatFormatting;

import com.whalestorm.chestsorter.Config;
import com.whalestorm.chestsorter.core.WhatIf;

/** Client-side /chestsorter command. Never sent to the server. */
public final class SorterCommand extends CommandBase {

    private static final List<String> SUBS = Arrays
        .asList("run", "plan", "whatif", "stop", "status", "scan", "item", "config", "verbose", "help");

    @Override
    public String getCommandName() {
        return "chestsorter";
    }

    @Override
    @SuppressWarnings("rawtypes")
    public List getCommandAliases() {
        return Arrays.asList("cs");
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/chestsorter <run|plan|whatif|stop|status|scan|item|config|verbose|help>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return true;
    }

    @Override
    @SuppressWarnings("rawtypes")
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        return args.length == 1 ? getListOfStringsFromIterableMatchingLastWord(args, SUBS) : null;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        SorterController c = SorterController.INSTANCE;
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(java.util.Locale.ROOT);
        switch (sub) {
            case "run":
                c.startWhenScreenCloses(Run.Mode.FULL);
                break;
            case "plan":
                c.startWhenScreenCloses(Run.Mode.PLAN_ONLY);
                break;
            case "whatif":
                if (args.length > 1) {
                    String list = String.join(", ", Arrays.copyOfRange(args, 1, args.length));
                    int[] n = WhatIf.parse(list, 4, 500);
                    if (n.length == 0) {
                        Trace.chat("Give extra chest counts from 1 to 500, e.g. /cs whatif 6 12 24");
                        break;
                    }
                    StringBuilder b = new StringBuilder();
                    for (int v : n) b.append(b.length() == 0 ? "" : ", ")
                        .append(v);
                    Config.setString(Config.CAT_SORT, "extraChests", b.toString());
                    Trace.chat("Comparing with " + b + " extra chests (saved).");
                } else if (WhatIf.parse(Config.extraChests, 4, 500).length == 0) {
                    Config.setString(Config.CAT_SORT, "extraChests", "6, 12, 24");
                    Trace.chat("Comparing with 6, 12, 24 extra chests (saved).");
                }
                c.startWhenScreenCloses(Run.Mode.PLAN_ONLY);
                break;
            case "stop":
                c.stop("stopped by command");
                break;
            case "status":
                Trace.chat(c.status());
                break;
            case "scan":
                c.listContainers();
                break;
            case "item":
                c.explainHeld();
                break;
            case "config":
            case "settings":
                c.openSettingsWhenScreenCloses();
                break;
            case "verbose":
                Config.setBoolean(Config.CAT_DEBUG, "verboseChat", !Config.verboseChat);
                Trace.chat("Verbose chat " + (Config.verboseChat ? "on" : "off") + " (saved).");
                break;
            default:
                help();
        }
    }

    private static void help() {
        String g = EnumChatFormatting.GRAY.toString();
        Trace.chat("Commands (/cs works too):");
        Trace.chat(g + "/chestsorter run  - sort nearby chests (same as the key)");
        Trace.chat(g + "/chestsorter plan - survey and show the plan, move nothing");
        Trace.chat(g + "/chestsorter whatif [n ...] - plan only, and show what n extra chests would change");
        Trace.chat(g + "/chestsorter stop - stop now; carried items stay in your inventory");
        Trace.chat(g + "/chestsorter status - what it is doing");
        Trace.chat(g + "/chestsorter scan - list nearby chests only");
        Trace.chat(g + "/chestsorter item - show the held item's category and group");
        Trace.chat(g + "/chestsorter config - open the settings (also: Mods list > Config, or the settings key)");
        Trace.chat(g + "/chestsorter verbose - print every step in chat");
        Trace.chat(g + "Every run writes .minecraft/chestsorter/trace-<time>.log");
    }
}
