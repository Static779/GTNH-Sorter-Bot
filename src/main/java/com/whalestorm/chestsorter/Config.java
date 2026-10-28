package com.whalestorm.chestsorter;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

public class Config {

    /** How far (blocks) the scanner looks for containers. Used from stage 1 on. */
    public static int scanRadius = 16;

    public static void synchronizeConfiguration(File configFile) {
        Configuration configuration = new Configuration(configFile);

        scanRadius = configuration.getInt(
            "scanRadius",
            Configuration.CATEGORY_GENERAL,
            scanRadius,
            1,
            64,
            "Container scan radius in blocks.");

        if (configuration.hasChanged()) {
            configuration.save();
        }
    }
}
