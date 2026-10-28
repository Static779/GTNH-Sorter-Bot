package net.minecraft.command;
@SuppressWarnings("rawtypes") public interface ICommand extends Comparable { String getCommandName(); String getCommandUsage(ICommandSender s); java.util.List getCommandAliases();
 void processCommand(ICommandSender s, String[] a); boolean canCommandSenderUseCommand(ICommandSender s); java.util.List addTabCompletionOptions(ICommandSender s, String[] a); boolean isUsernameIndex(String[] a, int i); }
