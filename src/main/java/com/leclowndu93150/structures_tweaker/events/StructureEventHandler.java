package com.leclowndu93150.structures_tweaker.events;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.leclowndu93150.structures_tweaker.StructuresTweaker;
import com.leclowndu93150.structures_tweaker.cache.StructureCache;
import com.leclowndu93150.structures_tweaker.config.StructureConfig;
import com.leclowndu93150.structures_tweaker.config.StructureConfigManager;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.function.BiPredicate;

public class StructureEventHandler {
    private final StructureConfigManager configManager;
    private final StructureCache structureCache;
    private final Map<ResourceLocation, StructureEventFlags> structureFlags;
    private final Cache<BlockPos, RegenerationData> regenerationQueue;
    private final Map<BlockPos, ScheduledFuture<?>> regenerationTasks;
    private final ScheduledExecutorService regenerationExecutor;
    private final Long2ObjectMap<ResourceLocation> structureLocations;

    private static final Logger LOGGER = LogManager.getLogger(StructuresTweaker.MODID);

    private static final Set<Block> STRUCTURE_BLOCKS = Set.of(
            Blocks.STRUCTURE_BLOCK, Blocks.STRUCTURE_VOID, Blocks.COMMAND_BLOCK,
            Blocks.CHAIN_COMMAND_BLOCK, Blocks.REPEATING_COMMAND_BLOCK,
            Blocks.END_PORTAL_FRAME, Blocks.BEDROCK
    );

    private static final Set<Block> PROTECTED_BLOCKS = Set.of(
            Blocks.CHEST, Blocks.TRAPPED_CHEST, Blocks.BARREL,
            Blocks.DISPENSER, Blocks.DROPPER, Blocks.HOPPER,
            Blocks.SHULKER_BOX
    );

    public StructureEventHandler(StructureConfigManager configManager, StructureCache structureCache) {
        this.configManager = configManager;
        this.structureCache = structureCache;
        this.structureFlags = new ConcurrentHashMap<>();
        this.regenerationQueue = CacheBuilder.newBuilder()
                .expireAfterWrite(24, TimeUnit.HOURS)
                .build();
        this.regenerationTasks = new ConcurrentHashMap<>();
        this.regenerationExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Structure-Regeneration");
            t.setDaemon(true);
            return t;
        });
        this.structureLocations = new Long2ObjectOpenHashMap<>();
    }

    public void reloadFlags() {
        structureFlags.clear();
        configManager.getAllConfigs().forEach((id, config) -> {
            ResourceLocation normalizedId = normalizeStructureId(id);
            structureFlags.put(normalizedId, new StructureEventFlags(
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
            ));
        });
    }

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        LOGGER.debug("Block break at pos: {} dimension: {}", event.getPos(),
                event.getPlayer().level().dimension().location());

        if (!configManager.isReady()) {
            LOGGER.debug("Config manager not ready, skipping");
            return;
        }

        Level level = event.getPlayer().level();
        BlockPos pos = event.getPos();

        handleStructureEvent(level, pos, (structure, flags) -> {
            LOGGER.debug("Structure check: {} flags: {}", structure, flags);
            Block block = event.getState().getBlock();

            if (STRUCTURE_BLOCKS.contains(block)) {
                LOGGER.debug("Found structure block: {}", block);
                if (!flags.canBreakStructureBlocks()) {
                    LOGGER.debug("Structure block breaking disabled");
                    event.setCanceled(true);
                    return true;
                }
            }

            if (!flags.canBreakBlocks()) {
                LOGGER.debug("Block breaking disabled in structure");
                event.setCanceled(true);
                return true;
            }

            if (flags.regenerationTime() > 0) {
                LOGGER.debug("Scheduling regeneration in {} minutes", flags.regenerationTime());
                scheduleBlockRegeneration(pos, event.getState(), flags.regenerationTime(), level);
            }
            return false;
        });
    }


    @SubscribeEvent
    public void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!configManager.isReady()) return;

        handleStructureEvent(event.getEntity().level(), event.getPos(), (structure, flags) -> {
            if (event.getPlacedBlock().getBlock() == Blocks.FIRE) {
                if (!flags.allowFireSpread()) {
                    event.setCanceled(true);
                    return true;
                }
            } else if (!flags.canPlaceBlocks()) {
                event.setCanceled(true);
                return true;
            }
            return false;
        });
    }

    @SubscribeEvent
    public void onPlayerInteract(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()) return;
        if (!configManager.isReady()) return;

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
        if (event.getLevel().isClientSide()) return;
        if (!configManager.isReady()) return;

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
        if (event.getLevel().isClientSide()) return;
        if (!configManager.isReady()) return;

        handleStructureEvent(event.getLevel().getLevel(), event.getEntity().blockPosition(),
                (structure, flags) -> {
                    if (!flags.allowMobSpawning() ||
                            (event.getEntity().getType().getCategory() == MobCategory.CREATURE
                                    && !flags.allowCreatureSpawning())) {
                        event.setCanceled(true);
                        return true;
                    }

                    if (flags.mobSpawnRate() < 1.0f &&
                            event.getLevel().getRandom().nextFloat() > flags.mobSpawnRate()) {
                        event.setCanceled(true);
                        return true;
                    }
                    return false;
                });
    }

    @SubscribeEvent
    public void onPlayerPvP(AttackEntityEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        if (!configManager.isReady()) return;

        if (event.getTarget() instanceof Player) {
            handleStructureEvent(event.getEntity().level(), event.getEntity().blockPosition(),
                    (structure, flags) -> {
                        if (!flags.allowPlayerPVP()) {
                            event.setCanceled(true);
                            return true;
                        }
                        return false;
                    });
        }
    }

    @SubscribeEvent
    public void onItemPickup(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide()) return;
        if (!configManager.isReady()) return;

        if (event.getTarget() instanceof ItemEntity item) {
            handleStructureEvent(event.getLevel(), event.getPos(), (structure, flags) -> {
                if (!flags.allowItemPickup() ||
                        (flags.protectArtifacts() && isProtectedItem(item))) {
                    event.setCanceled(true);
                    return true;
                }
                return false;
            });
        }
    }

    @SubscribeEvent
    public void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel().isClientSide()) return;
        if (event.getChunk() instanceof LevelChunk chunk) {
            ChunkPos pos = chunk.getPos();
            structureLocations.remove(pos.toLong());
        }
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        regenerationTasks.values().forEach(task -> task.cancel(false));
        regenerationTasks.clear();
        regenerationQueue.invalidateAll();
        structureLocations.clear();
        structureFlags.clear();

        regenerationExecutor.shutdown();
        try {
            if (!regenerationExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                regenerationExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            regenerationExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private void handleStructureEvent(Level level, BlockPos pos, BiPredicate<ResourceLocation, StructureEventFlags> callback) {
        ResourceLocation structure = findAndCacheStructure(level, pos);
        if (structure != null) {
            structure = normalizeStructureId(structure);
            StructureEventFlags flags = structureFlags.get(structure);
            if (flags != null) {
                callback.test(structure, flags);
            }
        }
    }

    private ResourceLocation findAndCacheStructure(Level level, BlockPos pos) {
        ResourceLocation cached = structureCache.getStructureAt(level, pos);
        if (cached != null) return cached;

        LevelChunk chunk = level.getChunkAt(pos);
        var registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);

        for (Map.Entry<Structure, StructureStart> entry : chunk.getAllStarts().entrySet()) {
            if (entry.getValue().getBoundingBox().isInside(pos)) {
                ResourceLocation id = registry.getKey(entry.getKey());
                if (id != null) {
                    id = normalizeStructureId(id);
                    structureCache.cacheStructure(level, pos, id, entry.getValue().getBoundingBox());
                    return id;
                }
            }
        }
        return null;
    }

    private ResourceLocation normalizeStructureId(ResourceLocation id) {
        return ResourceLocation.tryParse(id.toString()
                .replace("minecraft:minecraft/", "minecraft:")
                .replace("minecraft/minecraft:", "minecraft:"));
    }

    private void scheduleBlockRegeneration(BlockPos pos, BlockState state, int minutes, Level level) {
        ScheduledFuture<?> existingTask = regenerationTasks.remove(pos);
        if (existingTask != null) {
            existingTask.cancel(false);
        }

        regenerationQueue.put(pos, new RegenerationData(level, state,
                System.currentTimeMillis() + minutes * 60000L));

        ScheduledFuture<?> task = regenerationExecutor.schedule(() -> {
            RegenerationData data = regenerationQueue.getIfPresent(pos);
            if (data != null && System.currentTimeMillis() >= data.regenerationTime()) {
                data.level().setBlock(pos, data.originalState(), 3);
                regenerationQueue.invalidate(pos);
                regenerationTasks.remove(pos);
            }
        }, minutes, TimeUnit.MINUTES);

        regenerationTasks.put(pos, task);
    }

    private boolean isProtectedItem(ItemEntity item) {
        return item.getItem().getItem() instanceof BlockItem blockItem &&
                (STRUCTURE_BLOCKS.contains(blockItem.getBlock()) ||
                        PROTECTED_BLOCKS.contains(blockItem.getBlock()));
    }

    private record RegenerationData(Level level, BlockState originalState, long regenerationTime) {}

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
    ) {}
}