package com.xnexusacs.visioncore;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VisionCore implements ModInitializer {

    public static final String MOD_ID = "visioncore";

    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing VisionCore (on general side)");
    }
}
