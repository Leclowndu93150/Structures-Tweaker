package com.leclowndu93150.structures_tweaker.events;

import com.leclowndu93150.baguettelib.event.entity.CreativeFlightEvent;
import com.leclowndu93150.structures_tweaker.StructuresTweaker;
import com.leclowndu93150.structures_tweaker.cache.StructureCache;
import com.leclowndu93150.structures_tweaker.config.core.StructureConfig;
import com.leclowndu93150.structures_tweaker.config.core.StructureConfigManager;
import com.leclowndu93150.structures_tweaker.data.DefeatedStructuresData;
import com.leclowndu93150.structures_tweaker.data.EmptyChunksData;
import com.leclowndu93150.structures_tweaker.data.StructureBlocksData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiPredicate;

public class StructureEventHandler {
    private final StructureConfigManager configManager;
    private final StructureCache structureCache;
    private final Map<ResourceLocation, DynamicStructureFlags> structureFlags;

    private static final Logger LOGGER = LogManager.getLogger(StructuresTweaker.MODID);

    public StructureEventHandler(StructureConfigManager configManager, StructureCache structureCache) {
        this.configManager = configManager;
        this.structureCache = structureCache;
        this.structureFlags = new ConcurrentHashMap<>();
    }

    public void reloadFlags() {
        structureFlags.clear();
        configManager.getAllConfigs().forEach((id, config) -> {
            ResourceLocation normalizedId = normalizeStructureId(id);
            structureFlags.put(normalizedId, new DynamicStructureFlags(config));
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
            Block block = event.getLevel().getBlockState(event.getPos()).getBlock();
            ResourceLocation blockId = event.getLevel().registryAccess()
                    .registryOrThrow(Registries.BLOCK).getKey(block);
            
            if (blockId != null) {
                String blockIdStr = blockId.toString();
                
                List<String> whitelist = flags.getInteractionWhitelist();
                if (whitelist != null && !whitelist.isEmpty() && whitelist.contains(blockIdStr)) {
                    return false;
                }
                
                List<String> blacklist = flags.getInteractionBlacklist();
                if (blacklist != null && !blacklist.isEmpty() && blacklist.contains(blockIdStr)) {
                    event.setCanceled(true);
                    return true;
                }
            }
            
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
    public void onItemPickup(ItemEntityPickupEvent.Pre event) {
        if (!configManager.isReady()) return;

        ItemEntity item = event.getItemEntity();
        handleStructureEvent(item.level(), item.blockPosition(), (structure, flags) -> {
            if (!flags.allowItemPickup()) {
                event.setCanPickup(TriState.FALSE);
                return true;
            }
            return false;
        });
    }


    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        structureFlags.clear();
    }

    public void handleStructureEvent(Level level, BlockPos pos, BiPredicate<ResourceLocation, DynamicStructureFlags> callback) {

        if (Thread.currentThread().getName().contains("worldgen")) {
            return;
        }
        if (!configManager.isReady() || !level.hasChunkAt(pos)) {
            return;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        ResourceLocation cached = structureCache.getStructureAt(level, pos);
        if (cached != null) {
            DynamicStructureFlags flags = structureFlags.get(cached);
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

                DynamicStructureFlags flags = structureFlags.get(id);
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

    @SubscribeEvent
    public void onItemUse(PlayerInteractEvent.RightClickItem event) {
        if (event.getLevel().isClientSide()) return;
        Player player = event.getEntity();
        handleStructureEvent(player.level(), player.blockPosition(), (structure, flags) -> {
            ResourceLocation itemId = event.getLevel().registryAccess()
                    .registryOrThrow(Registries.ITEM).getKey(event.getItemStack().getItem());
            
            if (itemId != null) {
                String itemIdStr = itemId.toString();
                
                List<String> whitelist = flags.getItemUseWhitelist();
                if (whitelist != null && !whitelist.isEmpty() && whitelist.contains(itemIdStr)) {
                    return false;
                }
                
                List<String> blacklist = flags.getItemUseBlacklist();
                if (blacklist != null && !blacklist.isEmpty() && blacklist.contains(itemIdStr)) {
                    event.setCanceled(true);
                    player.displayClientMessage(Component.translatable("message.structures_tweaker.item_blacklisted"), true);
                    return true;
                }
            }
            
            if (!flags.allowEnderPearls() && event.getItemStack().is(Items.ENDER_PEARL)) {
                event.setCanceled(true);
                player.displayClientMessage(Component.translatable("message.structures_tweaker.no_pearls"), true);
                return true;
            }
            if (!flags.allowRiptide() && event.getItemStack().getItem() instanceof TridentItem) {
                if (player.isInWaterOrRain()) {
                    event.setCanceled(true);
                    player.displayClientMessage(Component.translatable("message.structures_tweaker.no_riptide"), true);
                    return true;
                }
            }
            return false;
        });
    }

    private ResourceLocation normalizeStructureId(ResourceLocation id) {
        String namespace = id.getNamespace();
        String path = id.getPath();
        if (path.startsWith(namespace + "/")) {
            path = path.substring(namespace.length() + 1);
        }
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }

    private static StructureEventHandler INSTANCE;
    public static void setInstance(StructureEventHandler handler) {
        INSTANCE = handler;
    }
    public static boolean shouldCancelElytraFlight(Level level, BlockPos pos) {
        if (level.isClientSide() || !(level instanceof ServerLevel) || INSTANCE == null) {
            return false;
        }
        AtomicBoolean shouldCancel = new AtomicBoolean(false);
        INSTANCE.handleStructureEvent(level, pos, (structure, flags) -> {
            if (!flags.allowElytraFlight()) {
                shouldCancel.set(true);
                return true;
            }
            return false;
        });
        return shouldCancel.get();
    }
    
    @SubscribeEvent
    public void onCreativeFlightToggle(CreativeFlightEvent.Toggle event) {
        if (!configManager.isReady()) {
            return;
        }
        
        if (!(event.getPlayer().level() instanceof ServerLevel)) {
            return;
        }

        if (!event.isEnablingFlight()) {
            return;
        }
        
        handleStructureEvent(event.getPlayer().level(), event.getPlayer().blockPosition(), (structure, flags) -> {
            if (!flags.allowCreativeFlight()) {
                event.getPlayer().displayClientMessage(Component.translatable("message.structures_tweaker.no_creative_flight"), true);
                event.setCanceled(true);
                event.setFlightState(false);
                return true;
            }
            return false;
        });
    }
    
    @SubscribeEvent
    public void onEnderPearlTeleport(EntityTeleportEvent.EnderPearl event) {
        if (!configManager.isReady()) {
            return;
        }
        
        Player player = event.getPlayer();
        if (player == null || player.level().isClientSide()) {
            return;
        }
        
        BlockPos targetPos = new BlockPos((int)event.getTargetX(), (int)event.getTargetY(), (int)event.getTargetZ());
        
        handleStructureEvent(player.level(), targetPos, (structure, flags) -> {
            if (!flags.allowEnderTeleportation()) {
                event.setCanceled(true);
                player.displayClientMessage(Component.translatable("message.structures_tweaker.no_ender_teleportation"), true);
                return true;
            }
            return false;
        });
    }
    
    @SubscribeEvent
    public void onChorusFruitTeleport(EntityTeleportEvent.ChorusFruit event) {
        if (!configManager.isReady()) {
            return;
        }
        
        if (!(event.getEntityLiving() instanceof Player player)) {
            return;
        }
        
        if (player.level().isClientSide()) {
            return;
        }
        
        BlockPos targetPos = new BlockPos((int)event.getTargetX(), (int)event.getTargetY(), (int)event.getTargetZ());
        
        handleStructureEvent(player.level(), targetPos, (structure, flags) -> {
            if (!flags.allowEnderTeleportation()) {
                event.setCanceled(true);
                player.displayClientMessage(Component.translatable("message.structures_tweaker.no_ender_teleportation"), true);
                return true;
            }
            return false;
        });
    }

}