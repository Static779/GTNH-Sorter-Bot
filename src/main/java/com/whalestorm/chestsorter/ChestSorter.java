package com.whalestorm.chestsorter;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

/**
 * Client-side chest sorter for GTNH.
 *
 * acceptableRemoteVersions = "*" lets the client join servers that don't have this mod
 * (same pattern GTNH's MouseTweaks uses). On a dedicated server the CommonProxy does nothing.
 */
@Mod(
    modid = ChestSorter.MODID,
    name = ChestSorter.NAME,
    version = Tags.VERSION,
    acceptedMinecraftVersions = "[1.7.10]",
    acceptableRemoteVersions = "*")
public class ChestSorter {

    public static final String MODID = "chestsorter";
    public static final String NAME = "Chest Sorter";
    public static final Logger LOG = LogManager.getLogger(MODID);

    @SidedProxy(
        clientSide = "com.whalestorm.chestsorter.ClientProxy",
        serverSide = "com.whalestorm.chestsorter.CommonProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        proxy.preInit(event);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
    }
}
