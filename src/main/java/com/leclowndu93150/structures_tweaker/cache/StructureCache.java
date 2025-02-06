package com.leclowndu93150.structures_tweaker.cache;

import com.leclowndu93150.structures_tweaker.StructuresTweaker;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.bus.api.SubscribeEvent;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class StructureCache {
    Logger LOGGER = StructuresTweaker.LOGGER;
    private static final int MAX_ENTRIES_PER_DIMENSION = 10000;
    private final Map<String, Long2ObjectMap<ResourceLocation>> structureCache = new ConcurrentHashMap<>();
    private final Map<String, Long2ObjectMap<BoundingBox>> boundingBoxCache = new ConcurrentHashMap<>();

    private long getBlockKey(BlockPos pos) {
        return pos.asLong();
    }

    private long getChunkKey(ChunkPos pos) {
        return pos.toLong();
    }

    public void cacheStructure(Level level, BlockPos pos, ResourceLocation structure, BoundingBox bounds) {
        LOGGER.debug("Caching structure {} at {} with bounds {}", structure, pos, bounds);
        String dimensionKey = level.dimension().location().toString();

        Long2ObjectMap<ResourceLocation> structureDimCache = structureCache.computeIfAbsent(
                dimensionKey, k -> {
                    LOGGER.debug("Creating new structure cache for dimension {}", k);
                    return new Long2ObjectOpenHashMap<>();
                });

        Long2ObjectMap<BoundingBox> boundsDimCache = boundingBoxCache.computeIfAbsent(
                dimensionKey, k -> {
                    LOGGER.debug("Creating new bounds cache for dimension {}", k);
                    return new Long2ObjectOpenHashMap<>();
                });

        if (structureDimCache.size() >= MAX_ENTRIES_PER_DIMENSION) {
            LOGGER.warn("Cache limit reached for dimension {}, clearing caches", dimensionKey);
            structureDimCache.clear();
            boundsDimCache.clear();
        }

        long blockKey = getBlockKey(pos);
        long chunkKey = getChunkKey(new ChunkPos(pos));
        structureDimCache.put(blockKey, structure);
        boundsDimCache.put(chunkKey, bounds);
        LOGGER.debug("Successfully cached structure {} with keys block={}, chunk={}", structure, blockKey, chunkKey);
    }

    public ResourceLocation getStructureAt(Level level, BlockPos pos) {
        String dimensionKey = level.dimension().location().toString();
        LOGGER.debug("Looking up structure at {} in dimension {}", pos, dimensionKey);

        Long2ObjectMap<ResourceLocation> dimCache = structureCache.get(dimensionKey);
        if (dimCache == null) {
            LOGGER.debug("No cache found for dimension {}", dimensionKey);
            return null;
        }

        long key = getBlockKey(pos);
        ResourceLocation result = dimCache.get(key);
        LOGGER.debug("Structure lookup result for key {}: {}", key, result);
        return result;
    }

    @SubscribeEvent
    public void onChunkUnload(ChunkEvent.Unload event) {
        if (!(event.getChunk() instanceof LevelChunk chunk)) return;

        String dimensionKey = chunk.getLevel().dimension().location().toString();
        LOGGER.debug("Chunk unloading in dimension {} at {}", dimensionKey, chunk.getPos());

        Long2ObjectMap<ResourceLocation> structureDimCache = structureCache.get(dimensionKey);
        Long2ObjectMap<BoundingBox> boundsDimCache = boundingBoxCache.get(dimensionKey);

        if (structureDimCache != null && boundsDimCache != null) {
            ChunkPos chunkPos = chunk.getPos();
            long chunkKey = getChunkKey(chunkPos);
            int removedCount = 0;

            for (Long blockPosLong : new ArrayList<>(structureDimCache.keySet())) {
                if (new ChunkPos(BlockPos.of(blockPosLong)).toLong() == chunkKey) {
                    structureDimCache.remove(blockPosLong);
                    removedCount++;
                }
            }

            boundsDimCache.remove(chunkKey);
            LOGGER.debug("Removed {} entries for chunk {} in dimension {}",
                    removedCount, chunkPos, dimensionKey);
        }
    }

    public void clearCache() {
        structureCache.clear();
        boundingBoxCache.clear();
    }
}