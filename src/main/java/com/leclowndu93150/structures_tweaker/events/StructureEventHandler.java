package com.leclowndu93150.structures_tweaker.events;

import com.leclowndu93150.structures_tweaker.cache.StructureCache;
import com.leclowndu93150.structures_tweaker.config.StructureConfig;
import com.leclowndu93150.structures_tweaker.config.StructureConfigManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;

public class StructureEventHandler {
    private final StructureConfigManager configManager;
    private final StructureCache structureCache;
    private final Map<ResourceLocation, StructureEventFlags> structureFlags = new ConcurrentHashMap<>();

    private record StructureEventFlags(
            boolean canBreakBlocks,
            boolean canBreakStructureBlocks,
            boolean canInteract,
            boolean canPlaceBlocks,
            float mobSpawnRate,
            boolean allowMobSpawning,
            boolean allowPlayerPVP,
            boolean allowCreatureSpawning,
            int regenerationTime,
            boolean protectArtifacts,
            boolean allowFireSpread,
            boolean allowExplosions,
            boolean allowItemPickup
    ) {
        static StructureEventFlags fromConfig(StructureConfig config) {
            return new StructureEventFlags(
                    config.canBreakBlocks(),
                    config.canBreakStructureBlocks(),
                    config.canInteract(),
                    config.canPlaceBlocks(),
                    config.getMobSpawnRate(),
                    config.allowMobSpawning(),
                    config.allowPlayerPVP(),
                    config.allowCreatureSpawning(),
                    config.getRegenerationTime(),
                    config.protectArtifacts(),
                    config.allowFireSpread(),
                    config.allowExplosions(),
                    config.allowItemPickup()
            );
        }
    }

    public StructureEventHandler(StructureConfigManager configManager, StructureCache structureCache) {
        this.configManager = configManager;
        this.structureCache = structureCache;
        initializeFlags();
    }

    public void initializeFlags() {
        structureFlags.clear();
        configManager.getAllConfigs().forEach((id, config) -> {
            structureFlags.put(id, StructureEventFlags.fromConfig(config));
        });
    }

    private void handleStructureEvent(Level level, BlockPos pos, BiPredicate<ResourceLocation, StructureEventFlags> callback) {
        ResourceLocation structure = structureCache.getStructureAt(level, pos);

        if (structure == null) {
            LevelChunk chunk = level.getChunkAt(pos);
            Map<Structure, StructureStart> starts = chunk.getAllStarts();
            if (!starts.isEmpty()) {
                for (Map.Entry<Structure, StructureStart> entry : starts.entrySet()) {
                    if (entry.getValue().getBoundingBox().isInside(pos)) {
                        ResourceLocation id = level.registryAccess().registryOrThrow(Registries.STRUCTURE).getKey(entry.getKey());
                        if (id != null) {
                            structureCache.cacheStructure(level, pos, id);
                            structure = id;
                            if (structureFlags.containsKey(structure)) {
                                callback.test(structure, structureFlags.get(structure));
                                return;
                            }
                        }
                    }
                }
            }
        } else if (structureFlags.containsKey(structure)) {
            callback.test(structure, structureFlags.get(structure));
        }
    }

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        System.out.println("Block break event triggered at " + event.getPos());
        handleStructureEvent(event.getPlayer().level(), event.getPos(), (structure, flags) -> {
            System.out.println("Structure check for break: " + structure + " flags: " + flags);
            if (!flags.canBreakBlocks()) {
                System.out.println("Canceling break at " + event.getPos() + " in " + structure);
                event.setCanceled(true);
                return true;
            }
            return false;
        });
    }

    @SubscribeEvent
    public void onPlayerInteract(PlayerInteractEvent.RightClickBlock event) {
        handleStructureEvent(event.getLevel(), event.getPos(), (structure, flags) -> {
            if (!flags.canInteract()) {
                event.setCanceled(true);
                return true;
            }
            return false;
        });
    }

    @SubscribeEvent
    public void onExplosion(ExplosionEvent.Start event) {
        handleStructureEvent(event.getLevel(), event.getExplosion().getDirectSourceEntity().blockPosition(),
                (structure, flags) -> {
                    if (!flags.allowExplosions()) {
                        event.setCanceled(true);
                        return true;
                    }
                    return false;
                });
    }

    @SubscribeEvent
    public void onMobSpawn(FinalizeSpawnEvent event) {
        handleStructureEvent(event.getLevel().getLevel(), event.getEntity().blockPosition(),
                (structure, flags) -> {
                    if (!flags.allowMobSpawning()) {
                        event.setCanceled(true);
                        return true;
                    }
                    return false;
                });
    }
}