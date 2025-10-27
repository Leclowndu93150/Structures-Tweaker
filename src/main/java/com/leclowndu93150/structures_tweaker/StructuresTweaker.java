package com.leclowndu93150.structures_tweaker;

import com.leclowndu93150.structures_tweaker.cache.StructureCache;
import com.leclowndu93150.structures_tweaker.command.ServerCommands;
import com.leclowndu93150.structures_tweaker.command.ShowStructureCommand;
import com.leclowndu93150.structures_tweaker.compat.CompatManager;
import com.leclowndu93150.structures_tweaker.compat.arsnouveau.ArsNouveauCompat;
import com.leclowndu93150.structures_tweaker.config.core.StructureConfigManager;
import com.leclowndu93150.structures_tweaker.data.EmptyChunksData;
import com.leclowndu93150.structures_tweaker.events.StructureEventHandler;
import com.leclowndu93150.structures_tweaker.render.StructureBoxRenderer;
//import dev.architectury.event.events.common.BlockEvent;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.projectile.WitherSkull;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.UseItemOnBlockEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(StructuresTweaker.MODID)
public class StructuresTweaker {
    public static final String MODID = "structures_tweaker";
    public static final Logger LOGGER = LogManager.getLogger(MODID);
    private static StructureConfigManager configManager;
    private static StructureCache structureCache;
    private static StructureEventHandler structureEventHandler;

    public StructuresTweaker(IEventBus modEventBus) {
        configManager = new StructureConfigManager();
        structureCache = new StructureCache();
        structureEventHandler = new StructureEventHandler(configManager, structureCache);

        StructureEventHandler.setInstance(structureEventHandler);

        NeoForge.EVENT_BUS.register(this);
        NeoForge.EVENT_BUS.register(structureCache);
        NeoForge.EVENT_BUS.register(structureEventHandler);
        if(FMLLoader.getDist().isClient()){
            NeoForge.EVENT_BUS.register(StructureBoxRenderer.class);
            NeoForge.EVENT_BUS.register(ShowStructureCommand.class);
        }
        NeoForge.EVENT_BUS.register(EmptyChunksData.class);

        CompatManager.registerCompat(new ArsNouveauCompat());
        CompatManager.initializeCompat();
    }

    public static StructureConfigManager getConfigManager() {
        return configManager;
    }

    public static StructureCache getStructureCache() {
        return structureCache;
    }

    public static StructureEventHandler getEventHandler() {
        return structureEventHandler;
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        structureCache.clearCache();
        configManager.generateConfigs();
        configManager.loadConfigs();
        structureEventHandler.reloadFlags();
        ServerCommands.setConfigManager(configManager);
        configManager.setConfigUpdateListener(structureEventHandler::updateStructureFlag);
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        structureCache.clearCache();
    }

}