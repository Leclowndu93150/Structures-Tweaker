package com.leclowndu93150.structures_tweaker.cache;

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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class StructureCache {
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
        String dimensionKey = level.dimension().location().toString();
        Long2ObjectMap<ResourceLocation> structureDimCache = structureCache.computeIfAbsent(
                dimensionKey, k -> new Long2ObjectOpenHashMap<>());
        Long2ObjectMap<BoundingBox> boundsDimCache = boundingBoxCache.computeIfAbsent(
                dimensionKey, k -> new Long2ObjectOpenHashMap<>());

        if (structureDimCache.size() >= MAX_ENTRIES_PER_DIMENSION) {
            structureDimCache.clear();
            boundsDimCache.clear();
        }

        structureDimCache.put(getBlockKey(pos), structure);
        boundsDimCache.put(getChunkKey(new ChunkPos(pos)), bounds);
    }

    public ResourceLocation getStructureAt(Level level, BlockPos pos) {
        String dimensionKey = level.dimension().location().toString();
        Long2ObjectMap<ResourceLocation> dimCache = structureCache.get(dimensionKey);
        return dimCache != null ? dimCache.get(getBlockKey(pos)) : null;
    }

    public BoundingBox getBoundsForChunk(Level level, ChunkPos pos) {
        String dimensionKey = level.dimension().location().toString();
        Long2ObjectMap<BoundingBox> dimCache = boundingBoxCache.get(dimensionKey);
        return dimCache != null ? dimCache.get(getChunkKey(pos)) : null;
    }

    @SubscribeEvent
    public void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getChunk() instanceof LevelChunk chunk) {
            String dimensionKey = chunk.getLevel().dimension().location().toString();
            Long2ObjectMap<ResourceLocation> structureDimCache = structureCache.get(dimensionKey);
            Long2ObjectMap<BoundingBox> boundsDimCache = boundingBoxCache.get(dimensionKey);

            if (structureDimCache != null && boundsDimCache != null) {
                ChunkPos chunkPos = chunk.getPos();
                long chunkKey = getChunkKey(chunkPos);

                structureDimCache.keySet().removeIf(blockPosLong ->
                        new ChunkPos(BlockPos.of(blockPosLong)).toLong() == chunkKey);
                boundsDimCache.remove(chunkKey);
            }
        }
    }

    public void clearCache() {
        structureCache.clear();
        boundingBoxCache.clear();
    }
}