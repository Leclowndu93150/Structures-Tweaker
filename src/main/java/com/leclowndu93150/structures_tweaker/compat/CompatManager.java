package com.leclowndu93150.structures_tweaker.compat;

import net.neoforged.fml.ModList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

public class CompatManager {
    private static final Logger LOGGER = LogManager.getLogger("StructuresTweaker/Compat");
    private static final List<CompatAPI> compatModules = new ArrayList<>();
    
    public static void registerCompat(CompatAPI compat) {
        compatModules.add(compat);
    }
    
    public static void initializeCompat() {
        LOGGER.info("Initializing compatibility modules...");
        
        for (CompatAPI compat : compatModules) {
            if (ModList.get().isLoaded(compat.getModId())) {
                try {
                    compat.initialize();
                    LOGGER.info("Loaded compatibility for: {}", compat.getModId());
                } catch (Exception e) {
                    LOGGER.error("Failed to load compatibility for {}: {}", compat.getModId(), e.getMessage());
                }
            }
        }
    }
}
