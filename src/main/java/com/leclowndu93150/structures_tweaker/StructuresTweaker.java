package com.leclowndu93150.structures_tweaker;

import com.leclowndu93150.structures_tweaker.cache.StructureCache;
import com.leclowndu93150.structures_tweaker.command.ShowStructureCommand;
import com.leclowndu93150.structures_tweaker.config.core.StructureConfigManager;
import com.leclowndu93150.structures_tweaker.data.EmptyChunksData;
import com.leclowndu93150.structures_tweaker.events.StructureEventHandler;
import com.leclowndu93150.structures_tweaker.render.StructureBoxRenderer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLLoader;
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

    public StructuresTweaker(IEventBus modEventBus) {
        this.configManager = new StructureConfigManager();
        this.structureCache = new StructureCache();
        this.structureEventHandler = new StructureEventHandler(configManager, structureCache);

        StructureEventHandler.setInstance(structureEventHandler);

        NeoForge.EVENT_BUS.register(this);
        NeoForge.EVENT_BUS.register(structureCache);
        NeoForge.EVENT_BUS.register(this.structureEventHandler);
        if(FMLLoader.getDist().isClient()){
            NeoForge.EVENT_BUS.register(StructureBoxRenderer.class);
            NeoForge.EVENT_BUS.register(ShowStructureCommand.class);
        }
        NeoForge.EVENT_BUS.register(EmptyChunksData.class);
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
        LOGGER.info("StructuresTweaker cache cleared");
    }
}