package com.leclowndu93150.structures_tweaker;

import com.leclowndu93150.structures_tweaker.cache.StructureCache;
import com.leclowndu93150.structures_tweaker.config.StructureConfigManager;
import com.leclowndu93150.structures_tweaker.events.StructureEventHandler;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Mod(StructuresTweaker.MODID)
public class StructuresTweaker {
    public static final String MODID = "structures_tweaker";
    private static final Logger LOGGER = LogManager.getLogger(MODID);
    private static final int CONFIG_LOAD_TIMEOUT_SECONDS = 30;
    private final StructureConfigManager configManager;
    private final StructureCache structureCache;
    private final StructureEventHandler structureEventHandler;

    public StructuresTweaker(IEventBus modEventBus) {
        this.configManager = new StructureConfigManager();
        this.structureCache = new StructureCache();
        this.structureEventHandler = new StructureEventHandler(configManager, structureCache);

        NeoForge.EVENT_BUS.register(this);
        NeoForge.EVENT_BUS.register(structureCache);
        NeoForge.EVENT_BUS.register(this.structureEventHandler);
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        LOGGER.info("Server started, initializing StructuresTweaker");
        try {
            structureCache.clearCache();
            configManager.generateConfigs();
            configManager.loadConfigs();
            structureEventHandler.initializeFlags();
            LOGGER.info("StructuresTweaker initialization complete");
        } catch (Exception e) {
            LOGGER.error("Failed to initialize StructuresTweaker", e);
        }
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        structureCache.clearCache();
        LOGGER.info("StructuresTweaker cache cleared");
    }
}