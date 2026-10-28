package cpw.mods.fml.common.gameevent;
public class TickEvent extends cpw.mods.fml.common.eventhandler.Event { public enum Phase { START, END } public final Phase phase = null;
 public static class ClientTickEvent extends TickEvent {} }
