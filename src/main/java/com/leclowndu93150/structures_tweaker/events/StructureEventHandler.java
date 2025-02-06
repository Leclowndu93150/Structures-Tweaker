package com.leclowndu93150.structures_tweaker.events;

import com.leclowndu93150.structures_tweaker.cache.StructureCache;
import com.leclowndu93150.structures_tweaker.config.StructureConfigManager;
import com.leclowndu93150.structures_tweaker.data.StructureBlockData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;

public class StructureEventHandler {
    private final StructureConfigManager configManager;
    private final StructureCache structureCache;
    private final Map<ResourceLocation, StructureEventFlags> structureFlags = new ConcurrentHashMap<>();

    private static final Set<Block> PROTECTED_BLOCKS = Set.of(
            Blocks.STRUCTURE_BLOCK, Blocks.STRUCTURE_VOID, Blocks.COMMAND_BLOCK,
            Blocks.CHAIN_COMMAND_BLOCK, Blocks.REPEATING_COMMAND_BLOCK,
            Blocks.END_PORTAL_FRAME, Blocks.BEDROCK,
            Blocks.CHEST, Blocks.TRAPPED_CHEST, Blocks.BARREL,
            Blocks.DISPENSER, Blocks.DROPPER, Blocks.HOPPER,
            Blocks.SHULKER_BOX
    );

    public StructureEventHandler(StructureConfigManager configManager, StructureCache structureCache) {
        this.configManager = configManager;
        this.structureCache = structureCache;
    }

    public void reloadFlags() {
        structureFlags.clear();
        configManager.getAllConfigs().forEach((id, config) ->
                structureFlags.put(normalizeStructureId(id), new StructureEventFlags(
                        config.canBreakBlocks(),
                        config.canInteract(),
                        config.canPlaceBlocks(),
                        config.allowPlayerPVP(),
                        config.allowCreatureSpawning(),
                        config.allowFireSpread(),
                        config.allowExplosions(),
                        config.allowItemPickup(),
                        config.onlyProtectOriginalBlocks()
                ))
        );
    }

    //region Event Handlers
    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!configManager.isReady()) return;
        handleStructureEvent(event.getPlayer().level(), event.getPos(), (structure, flags) -> {
            if (checkOriginalBlockProtection(event.getPlayer().level(), event.getPos(), structure, flags)) {
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
        if (event.getLevel().isClientSide() || !configManager.isReady() || event.getEntity() == null) return;
        handleStructureEvent(event.getEntity().level(), event.getPos(), (structure, flags) -> {
            BlockState placedBlock = event.getPlacedBlock();
            if (placedBlock.getBlock() == Blocks.FIRE && !flags.allowFireSpread()) {
                event.setCanceled(true);
                return true;
            }
            if (!flags.canPlaceBlocks()) {
                event.setCanceled(true);
                return true;
            }
            return false;
        });
    }

    @SubscribeEvent
    public void onFluidSpread(BlockEvent.NeighborNotifyEvent event) {
        if (event.getLevel().isClientSide() || !configManager.isReady()) return;
        handleStructureEvent((Level) event.getLevel(), event.getPos(), (structure, flags) -> {
            if (event.getState().getFluidState().is(Fluids.LAVA) || event.getState().getFluidState().is(Fluids.WATER)) {
                if (!flags.canPlaceBlocks()) {
                    event.setCanceled(true);
                    return true;
                }
            }
            return false;
        });
    }

    @SubscribeEvent
    public void onLeafDecay(BlockEvent.BreakEvent event) {
        if (!configManager.isReady() || !event.getState().is(Blocks.AZALEA_LEAVES)) return;
        handleStructureEvent(event.getPlayer().level(), event.getPos(), (structure, flags) -> {
            if (!flags.canBreakBlocks()) {
                event.setCanceled(true);
                return true;
            }
            return false;
        });
    }

    @SubscribeEvent
    public void onFarmlandTrample(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide() || !configManager.isReady()) return;
        if (event.getLevel().getBlockState(event.getPos()).is(Blocks.FARMLAND)) {
            handleStructureEvent(event.getLevel(), event.getPos(), (structure, flags) -> {
                if (!flags.canInteract()) {
                    event.setCanceled(true);
                    return true;
                }
                return false;
            });
        }
    }

    @SubscribeEvent
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide() || !configManager.isReady()) return;
        if (event.getTarget() instanceof ItemFrame) {
            handleStructureEvent(event.getLevel(), event.getTarget().blockPosition(), (structure, flags) -> {
                if (!flags.canInteract()) {
                    event.setCanceled(true);
                    return true;
                }
                return false;
            });
        }
    }

    @SubscribeEvent
    public void onPlayerInteract(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide() || !configManager.isReady()) return;
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
                BlockPos.containing(event.getExplosion().x, event.getExplosion().y, event.getExplosion().z);
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
                    if (event.getEntity().getType().getCategory() == MobCategory.CREATURE
                            && !flags.allowCreatureSpawning()) {
                        event.setCanceled(true);
                        return true;
                    }
                    return false;
                });
    }

    @SubscribeEvent
    public void onPlayerPvP(AttackEntityEvent event) {
        if (event.getEntity().level().isClientSide() || !configManager.isReady()) return;
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
    public void onItemPickup(ItemEntityPickupEvent.Pre event) {
        if (!configManager.isReady()) return;
        ItemEntity item = event.getItemEntity();
        handleStructureEvent(item.level(), item.blockPosition(), (structure, flags) -> {
            if (!flags.allowItemPickup() || isProtectedItem(item)) {
                event.setCanPickup(TriState.FALSE);
                return true;
            }
            return false;
        });
    }

    @SubscribeEvent
    public void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel().isClientSide() ||
                !(event.getChunk() instanceof LevelChunk chunk) ||
                chunk.getAllStarts().isEmpty() ||
                !(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }

        var registry = chunk.getLevel().registryAccess().registryOrThrow(Registries.STRUCTURE);

        chunk.getAllStarts().forEach((structure, start) -> {
            ResourceLocation id = registry.getKey(structure);
            if (id != null && start != null && start.getBoundingBox() != null) {
                structureCache.cacheStructure(serverLevel, id, start.getBoundingBox());
            }
        });
    }

    @SubscribeEvent
    public void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel().isClientSide()) return;
        structureCache.clearCache();
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        if (event.getServer().overworld() != null) {
            DimensionDataStorage storage = event.getServer().overworld().getDataStorage();
            storage.computeIfAbsent(
                    new SavedData.Factory<>(
                            StructureBlockData::new,
                            StructureBlockData::load
                    ),
                    "structures_tweaker_blocks"
            ).shutdown();
        }
        structureCache.clearCache();
        structureFlags.clear();
    }
    //endregion

    //region Helper Methods
    private boolean checkOriginalBlockProtection(Level level, BlockPos pos, ResourceLocation structure, StructureEventFlags flags) {
        if (!flags.onlyProtectOriginalBlocks()) return false;
        if (!(level instanceof ServerLevel serverLevel)) return false;

        DimensionDataStorage storage = serverLevel.getDataStorage();
        StructureBlockData blockData = storage.computeIfAbsent(
                new SavedData.Factory<>(
                        StructureBlockData::new,
                        StructureBlockData::load
                ),
                "structures_tweaker_blocks"
        );
        return blockData.isOriginalBlock(structure, pos);
    }

    private boolean isProtectedItem(ItemEntity item) {
        return item.getItem().getItem() instanceof BlockItem blockItem &&
                PROTECTED_BLOCKS.contains(blockItem.getBlock());
    }

    private ResourceLocation normalizeStructureId(ResourceLocation id) {
        return ResourceLocation.tryParse(id.toString()
                .replace("minecraft:minecraft/", "minecraft:")
                .replace("minecraft/minecraft:", "minecraft:"));
    }

    private void handleStructureEvent(Level level, BlockPos pos,
                                      BiPredicate<ResourceLocation, StructureEventFlags> callback) {
        ResourceLocation structure = structureCache.getStructureAt(level, pos);
        if (structure != null) {
            StructureEventFlags flags = structureFlags.get(structure);
            if (flags != null) {
                callback.test(structure, flags);
            }
        }
    }
    //endregion

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
}