package io.github.wzhy233.ardion;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Ardion implements ModInitializer {

    public static Logger LOGGER = LoggerFactory.getLogger(Ardion.class);

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing Ardion...");
    }
}
