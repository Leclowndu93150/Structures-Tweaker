package com.leclowndu93150.structures_tweaker.events;

import com.leclowndu93150.structures_tweaker.StructuresTweaker;
import com.leclowndu93150.structures_tweaker.cache.StructureCache;
import com.leclowndu93150.structures_tweaker.config.StructureConfigManager;
import com.leclowndu93150.structures_tweaker.data.DefeatedStructuresData;
import com.leclowndu93150.structures_tweaker.data.EmptyChunksData;
import com.leclowndu93150.structures_tweaker.data.StructureBlocksData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.*;
import java.util.function.BiPredicate;

public class StructureEventHandler {
    private final StructureConfigManager configManager;
    private final StructureCache structureCache;
    private final Map<ResourceLocation, StructureEventFlags> structureFlags;

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
        if (!configManager.isReady()) {
            return;
        }

        if (!(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }

        handleStructureEvent(event.getPlayer().level(), event.getPos(), (structure, flags) -> {

            StructureBlocksData blockData = StructureBlocksData.get(serverLevel);

            if (flags.onlyProtectOriginalBlocks()) {
                if (blockData.isPlayerPlaced(event.getPos())) {
                    blockData.removePlayerBlock(event.getPos());
                    return false;
                }
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
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Player) ||
                !(event.getLevel() instanceof ServerLevel serverLevel)) return;
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

            if (!event.isCanceled()) {
                StructureBlocksData blockData = StructureBlocksData.get(serverLevel);
                blockData.addPlayerBlock(structure, event.getPos());
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
                new BlockPos((int)event.getExplosion().getPosition().x(),
                        (int)event.getExplosion().getPosition().y(),
                        (int)event.getExplosion().getPosition().z());

        handleStructureEvent(event.getLevel(), pos, (structure, flags) -> {
            if (!flags.allowExplosions()) {
                event.setCanceled(true);
                return true;
            }
            return false;
        });
    }

    @SubscribeEvent
    public void onMobSpawn(MobSpawnEvent.FinalizeSpawn event) {
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
    public void onItemPickup(EntityItemPickupEvent event) {
        if (!configManager.isReady()) return;

        ItemEntity item = event.getItem();
        handleStructureEvent(item.level(), item.blockPosition(), (structure, flags) -> {
            if (!flags.allowItemPickup() || isProtectedItem(item)) {
                event.setResult(Event.Result.DENY);
                return true;
            }
            return false;
        });
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        structureFlags.clear();
    }

    private void handleStructureEvent(Level level, BlockPos pos, BiPredicate<ResourceLocation, StructureEventFlags> callback) {
        if (Thread.currentThread().getName().contains("worldgen")) return;
        if (!configManager.isReady() || !level.hasChunkAt(pos)) return;
        if (!(level instanceof ServerLevel serverLevel)) return;

        ResourceLocation cached = structureCache.getStructureAt(level, pos);
        if (cached != null) {
            StructureEventFlags flags = structureFlags.get(cached);
            if (flags != null) {
                var registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
                for (var structure : registry) {
                    ResourceLocation id = registry.getKey(structure);
                    if (id != null && id.equals(cached)) {
                        var reference = serverLevel.structureManager().getStructureAt(pos, structure);
                        if (reference.isValid()) {
                            DefeatedStructuresData data = DefeatedStructuresData.get(serverLevel);
                            if (data.isDefeated(id, reference.getBoundingBox())) {
                                return;
                            }
                        }
                        break;
                    }
                }
                callback.test(cached, flags);
            }
            return;
        }

        var registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        boolean foundStructure = false;

        for (Structure structure : registry) {
            ResourceLocation id = registry.getKey(structure);
            if (id == null) continue;

            var reference = serverLevel.structureManager().getStructureAt(pos, structure);
            if (reference.isValid()) {
                foundStructure = true;
                id = normalizeStructureId(id);

                structureCache.cacheStructure(level, pos, id, reference.getBoundingBox());

                StructureEventFlags flags = structureFlags.get(id);
                if (flags != null) {
                    callback.test(id, flags);
                }
                break;
            }
        }

        if (!foundStructure) {
            EmptyChunksData.get(serverLevel).markEmpty(new ChunkPos(pos));
        }
    }

    private ResourceLocation normalizeStructureId(ResourceLocation id) {
        String namespace = id.getNamespace();
        String path = id.getPath();
        if (path.startsWith(namespace + "/")) {
            path = path.substring(namespace.length() + 1);
        }
        return new ResourceLocation(namespace, path);
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

}