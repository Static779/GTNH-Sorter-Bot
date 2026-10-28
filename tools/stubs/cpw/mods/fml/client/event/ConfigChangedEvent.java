package cpw.mods.fml.client.event;
import cpw.mods.fml.common.eventhandler.Event;
public class ConfigChangedEvent extends Event {
 public final String modID = null; public final boolean isWorldRunning = false; public final boolean requiresMcRestart = false; public final String configID = null;
 public static class OnConfigChangedEvent extends ConfigChangedEvent {}
 public static class PostConfigChangedEvent extends ConfigChangedEvent {} }
