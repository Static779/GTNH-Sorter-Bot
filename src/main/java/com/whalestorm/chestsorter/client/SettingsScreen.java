package com.whalestorm.chestsorter.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.common.config.ConfigElement;

import com.whalestorm.chestsorter.ChestSorter;
import com.whalestorm.chestsorter.Config;

import cpw.mods.fml.client.config.GuiConfig;
import cpw.mods.fml.client.config.IConfigElement;

/**
 * In-game settings: Forge's standard config screen over config/chestsorter.cfg, one button per category. Opened from
 * the Mods list (Config button), the settings key, or /cs config. Done saves and applies to the next run.
 */
public class SettingsScreen extends GuiConfig {

    public SettingsScreen(GuiScreen parent) {
        super(
            parent,
            elements(),
            ChestSorter.MODID,
            false,
            false,
            ChestSorter.NAME + " settings",
            "Changes apply to the next run");
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static List<IConfigElement> elements() {
        List<IConfigElement> out = new ArrayList<>();
        for (String cat : Config.GUI_CATEGORIES) out.add(new ConfigElement(Config.config.getCategory(cat)));
        return out;
    }
}
