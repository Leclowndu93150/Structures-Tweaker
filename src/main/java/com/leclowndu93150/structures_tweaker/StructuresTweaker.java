package com.leclowndu93150.structures_tweaker;

import com.leclowndu93150.structures_tweaker.cache.StructureCache;
import com.leclowndu93150.structures_tweaker.config.StructureConfigManager;
import com.leclowndu93150.structures_tweaker.events.StructureEventHandler;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(StructuresTweaker.MODID)
public class StructuresTweaker {
    public static final String MODID = "structures_tweaker";
    public static final Logger LOGGER = LogManager.getLogger(MODID);
    private final StructureConfigManager configManager;
    private final StructureCache structureCache;
    private final StructureEventHandler structureEventHandler;

    //TODO: Save the structure's original blocks as save data instead of temporary

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
        structureCache.clearCache();
        configManager.generateConfigs();
        configManager.loadConfigs();
        structureEventHandler.reloadFlags();
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        structureCache.clearCache();
        //StructureBlockData.shutdown();
        LOGGER.info("StructuresTweaker cache cleared");
    }
}