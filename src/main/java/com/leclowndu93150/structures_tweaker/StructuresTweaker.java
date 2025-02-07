package com.leclowndu93150.structures_tweaker;

import com.leclowndu93150.structures_tweaker.cache.StructureCache;
import com.leclowndu93150.structures_tweaker.command.ShowStructureCommand;
import com.leclowndu93150.structures_tweaker.config.StructureConfigManager;
import com.leclowndu93150.structures_tweaker.data.EmptyChunksData;
import com.leclowndu93150.structures_tweaker.events.StructureEventHandler;
import com.leclowndu93150.structures_tweaker.render.StructureBoxRenderer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(StructuresTweaker.MODID)
public class StructuresTweaker {
    public static final String MODID = "structures_tweaker";
    public static final Logger LOGGER = LogManager.getLogger(MODID);
    private final StructureConfigManager configManager;
    private final StructureCache structureCache;
    private final StructureEventHandler structureEventHandler;

    public StructuresTweaker() {
        IEventBus eventBus = FMLJavaModLoadingContext.get().getModEventBus();
        this.configManager = new StructureConfigManager();
        this.structureCache = new StructureCache();
        this.structureEventHandler = new StructureEventHandler(configManager, structureCache);

        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(structureCache);
        MinecraftForge.EVENT_BUS.register(this.structureEventHandler);
        MinecraftForge.EVENT_BUS.register(StructureBoxRenderer.class);
        MinecraftForge.EVENT_BUS.register(ShowStructureCommand.class);
        MinecraftForge.EVENT_BUS.register(EmptyChunksData.class);
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

    @SubscribeEvent
    public void onCommandRegister(RegisterCommandsEvent event) {
        ShowStructureCommand.register(event.getDispatcher());
    }
}