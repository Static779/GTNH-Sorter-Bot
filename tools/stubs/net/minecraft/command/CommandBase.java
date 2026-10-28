package net.minecraft.command;
@SuppressWarnings("rawtypes") public abstract class CommandBase implements ICommand { public int getRequiredPermissionLevel(){return 4;} public java.util.List getCommandAliases(){return null;}
 public boolean canCommandSenderUseCommand(ICommandSender s){return true;} public java.util.List addTabCompletionOptions(ICommandSender s, String[] a){return null;}
 public boolean isUsernameIndex(String[] a, int i){return false;} public int compareTo(Object o){return 0;}
 public static java.util.List getListOfStringsFromIterableMatchingLastWord(String[] a, Iterable i){return null;} }
