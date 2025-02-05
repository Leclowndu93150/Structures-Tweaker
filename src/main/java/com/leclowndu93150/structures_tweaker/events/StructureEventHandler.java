package com.leclowndu93150.structures_tweaker.events;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.leclowndu93150.structures_tweaker.StructuresTweaker;
import com.leclowndu93150.structures_tweaker.cache.StructureCache;
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
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.*;
import java.util.function.BiPredicate;

public class StructureEventHandler {
    private final StructureConfigManager configManager;
    private final StructureCache structureCache;
    private final Map<ResourceLocation, StructureEventFlags> structureFlags;
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
        this.structureLocations = new Long2ObjectOpenHashMap<>();
    }

    public void reloadFlags() {
        structureFlags.clear();
        configManager.getAllConfigs().forEach((id, config) -> {
            ResourceLocation normalizedId = normalizeStructureId(id);
            structureFlags.put(normalizedId, new StructureEventFlags(
                    config.canBreakBlocks(),
                    config.canInteract(),
                    config.canPlaceBlocks(),
                    config.allowPlayerPVP(),
                    config.allowCreatureSpawning(),
                    config.allowFireSpread(),
                    config.allowExplosions(),
                    config.allowItemPickup(),
                    config.onlyProtectOriginalBlocks()
            ));
        });
    }

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!configManager.isReady()) return;

        handleStructureEvent(event.getPlayer().level(), event.getPos(), (structure, flags) -> {
            if (flags.onlyProtectOriginalBlocks() && isStructureBlock(event.getPlayer().level(), event.getPos())) {
                event.setCanceled(true);
                return true;
            }
            if (!flags.canBreakBlocks()) {
                event.setCanceled(true);
                return true;
            }
            return false;
        });
    }


    @SubscribeEvent
    public void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!configManager.isReady()) return;

        handleStructureEvent(Objects.requireNonNull(event.getEntity()).level(), event.getPos(), (structure, flags) -> {
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
        if (event.getLevel().isClientSide() || !configManager.isReady()) return;

        Entity source = event.getExplosion().getDirectSourceEntity();
        BlockPos pos = source != null ? source.blockPosition() :
                new BlockPos((int)event.getExplosion().x,
                        (int)event.getExplosion().y,
                        (int)event.getExplosion().z);

        handleStructureEvent(event.getLevel(), pos, (structure, flags) -> {
            if (!flags.allowExplosions()) {
                event.setCanceled(true);
                return true;
            }
            return false;
        });
    }

    @SubscribeEvent
    public void onMobSpawn(FinalizeSpawnEvent event) {
        if (event.getLevel().isClientSide() || !configManager.isReady()) return;

        handleStructureEvent(event.getLevel().getLevel(), event.getEntity().blockPosition(),
                (structure, flags) -> {
                    if (event.getEntity().getType().getCategory() == MobCategory.CREATURE && !flags.allowCreatureSpawning()) {
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
                        (isProtectedItem(item))) {
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
        structureLocations.clear();
        structureFlags.clear();
    }

    private void handleStructureEvent(Level level, BlockPos pos, BiPredicate<ResourceLocation, StructureEventFlags> callback) {
        try {
            Optional<ResourceLocation> structure = structureLookupCache.get(Triple.of(level, pos, level.getGameTime()));
            structure.ifPresent(id -> {
                StructureEventFlags flags = structureFlags.get(id);
                if (flags != null) {
                    callback.test(id, flags);
                }
            });
        } catch (ExecutionException e) {
            LOGGER.error("Error handling structure event at {}", pos, e);
        }
    }

    private ResourceLocation normalizeStructureId(ResourceLocation id) {
        return ResourceLocation.tryParse(id.toString()
                .replace("minecraft:minecraft/", "minecraft:")
                .replace("minecraft/minecraft:", "minecraft:"));
    }

    private boolean isProtectedItem(ItemEntity item) {
        return item.getItem().getItem() instanceof BlockItem blockItem &&
                (STRUCTURE_BLOCKS.contains(blockItem.getBlock()) ||
                        PROTECTED_BLOCKS.contains(blockItem.getBlock()));
    }

    private record StructureEventFlags(
            boolean canBreakBlocks,
            boolean canInteract,
            boolean canPlaceBlocks,
            boolean allowPlayerPVP,
            boolean allowCreatureSpawning,
            boolean allowFireSpread,
            boolean allowExplosions,
            boolean allowItemPickup,
            boolean onlyProtectOriginalBlocks
    ) {}


    private final Map<ChunkPos, Set<BlockPos>> structureBlocks = new ConcurrentHashMap<>();

    @SubscribeEvent
    public void onChunkLoad(ChunkEvent.Load event) {
        if (event.getChunk() instanceof LevelChunk chunk) {
            chunk.getAllStarts().forEach((structure, start) -> {
                BoundingBox box = start.getBoundingBox();
                ChunkPos chunkPos = new ChunkPos(chunk.getPos().getWorldPosition());
                structureBlocks.computeIfAbsent(chunkPos, k -> ConcurrentHashMap.newKeySet())
                        .addAll(scanStructureBlocks(chunk.getLevel(), box, chunkPos));
            });
        }
    }

    private Set<BlockPos> scanStructureBlocks(Level level, BoundingBox box, ChunkPos chunkPos) {
        Set<BlockPos> blocks = ConcurrentHashMap.newKeySet();
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int y = box.minY(); y <= box.maxY(); y++) {
                for (int z = box.minZ(); z <= box.maxZ(); z++) {
                    mutable.set(x, y, z);
                    if (new ChunkPos(mutable).equals(chunkPos)) {
                        BlockState state = level.getBlockState(mutable);
                        if (!state.isAir()) {
                            blocks.add(mutable.immutable());
                        }
                    }
                }
            }
        }
        return blocks;
    }

    private final LoadingCache<Triple<Level, BlockPos, Long>, Optional<ResourceLocation>> structureLookupCache = CacheBuilder.newBuilder()
            .expireAfterWrite(1, TimeUnit.SECONDS)
            .build(new CacheLoader<Triple<Level, BlockPos, Long>, Optional<ResourceLocation>>() {
                @Override
                public Optional<ResourceLocation> load(Triple<Level, BlockPos, Long> key) {
                    Level level = key.getLeft();
                    BlockPos pos = key.getMiddle();

                    ResourceLocation cached = structureCache.getStructureAt(level, pos);
                    if (cached != null) return Optional.of(cached);

                    LevelChunk chunk = level.getChunkAt(pos);
                    var registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);

                    for (Map.Entry<Structure, StructureStart> entry : chunk.getAllStarts().entrySet()) {
                        if (entry.getValue().getBoundingBox().isInside(pos)) {
                            ResourceLocation id = registry.getKey(entry.getKey());
                            if (id != null) {
                                id = normalizeStructureId(id);
                                structureCache.cacheStructure(level, pos, id, entry.getValue().getBoundingBox());
                                return Optional.of(id);
                            }
                        }
                    }
                    return Optional.empty();
                }
            });

    private boolean isStructureBlock(Level level, BlockPos pos) {
        ChunkPos chunkPos = new ChunkPos(pos);
        Set<BlockPos> blocks = structureBlocks.get(chunkPos);
        return blocks != null && blocks.contains(pos);
    }
}