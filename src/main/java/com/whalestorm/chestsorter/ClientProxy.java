package com.whalestorm.chestsorter;

import net.minecraftforge.client.ClientCommandHandler;

import com.whalestorm.chestsorter.client.KeyBindings;
import com.whalestorm.chestsorter.client.LocalChests;
import com.whalestorm.chestsorter.client.SorterCommand;
import com.whalestorm.chestsorter.client.SorterController;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

public class ClientProxy extends CommonProxy {

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
        Config.init(event.getSuggestedConfigurationFile());
        ChestSorter.LOG.info("{} {} loading (client)", ChestSorter.NAME, Tags.VERSION);
    }

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
        KeyBindings.register();
        // KeyInputEvent and ClientTickEvent are posted on the FML bus (FMLCommonHandler), not MinecraftForge.EVENT_BUS.
        FMLCommonHandler.instance()
            .bus()
            .register(SorterController.INSTANCE);
        // Singleplayer survey reads run on the built-in server's tick, which is posted on the same FML bus.
        FMLCommonHandler.instance()
            .bus()
            .register(LocalChests.INSTANCE);
        ClientCommandHandler.instance.registerCommand(new SorterCommand());
    }
}
