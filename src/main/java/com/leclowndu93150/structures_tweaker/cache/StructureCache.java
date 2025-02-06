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

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class StructureCache {
    Logger LOGGER = StructuresTweaker.LOGGER;
    private static final int MAX_ENTRIES_PER_DIMENSION = 10000;
    private final Map<String, Long2ObjectMap<List<CachedStructure>>> structureCache = new ConcurrentHashMap<>();

    private long getChunkKey(ChunkPos pos) {
        return pos.toLong();
    }

    public void cacheStructure(Level level, ResourceLocation structure, BoundingBox bounds) {
        LOGGER.debug("Caching structure {} with bounds {}", structure, bounds);
        String dimensionKey = level.dimension().location().toString();

        Long2ObjectMap<List<CachedStructure>> dimCache = structureCache.computeIfAbsent(
                dimensionKey, k -> new Long2ObjectOpenHashMap<>()
        );

        // Calculate all chunks covered by the bounding box
        int minChunkX = bounds.minX() >> 4;
        int maxChunkX = bounds.maxX() >> 4;
        int minChunkZ = bounds.minZ() >> 4;
        int maxChunkZ = bounds.maxZ() >> 4;

        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                ChunkPos chunkPos = new ChunkPos(cx, cz);
                long chunkKey = getChunkKey(chunkPos);

                List<CachedStructure> structuresInChunk = dimCache.computeIfAbsent(chunkKey, k -> new ArrayList<>());
                structuresInChunk.add(new CachedStructure(structure, bounds));

                if (structuresInChunk.size() > 50) {
                    LOGGER.warn("Too many structures in chunk {}! Trimming...", chunkPos);
                    structuresInChunk.remove(0);
                }
            }
        }
    }

    public ResourceLocation getStructureAt(Level level, BlockPos pos) {
        String dimensionKey = level.dimension().location().toString();
        ChunkPos chunkPos = new ChunkPos(pos);
        long chunkKey = getChunkKey(chunkPos);

        Long2ObjectMap<List<CachedStructure>> dimCache = structureCache.get(dimensionKey);
        if (dimCache == null) return null;

        List<CachedStructure> structuresInChunk = dimCache.get(chunkKey);
        if (structuresInChunk == null) return null;

        for (CachedStructure cached : structuresInChunk) {
            if (cached.bounds().isInside(pos)) {
                return cached.structure();
            }
        }
        return null;
    }

    @SubscribeEvent
    public void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel().isClientSide() || !(event.getChunk() instanceof LevelChunk chunk)) return;

        String dimensionKey = chunk.getLevel().dimension().location().toString();
        long chunkKey = getChunkKey(chunk.getPos());

        Long2ObjectMap<List<CachedStructure>> dimCache = structureCache.get(dimensionKey);
        if (dimCache != null) {
            dimCache.remove(chunkKey);
        }
    }

    public void clearCache() {
        structureCache.clear();
    }

    private record CachedStructure(ResourceLocation structure, BoundingBox bounds) {}
}