package cpw.mods.fml.common;
import java.lang.annotation.*;
@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.TYPE)
public @interface Mod { String modid(); String name() default ""; String version() default ""; String dependencies() default "";
 String acceptedMinecraftVersions() default ""; String acceptableRemoteVersions() default ""; String guiFactory() default "";
 @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.METHOD) @interface EventHandler {} }
