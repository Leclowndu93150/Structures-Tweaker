package com.leclowndu93150.structures_tweaker.cache;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.bus.api.SubscribeEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class StructureCache {
    private static final int MAX_ENTRIES_PER_DIMENSION = 10000;
    // Using BlockPos.asLong() for more precise caching
    private final Map<String, Map<Long, ResourceLocation>> structureCache = new ConcurrentHashMap<>();

    private long getBlockKey(BlockPos pos) {
        return pos.asLong();
    }

    public void cacheStructure(Level level, BlockPos pos, ResourceLocation structure) {
        String dimensionKey = level.dimension().location().toString();
        Map<Long, ResourceLocation> dimensionCache = structureCache.computeIfAbsent(dimensionKey, k -> new ConcurrentHashMap<>());

        if (dimensionCache.size() >= MAX_ENTRIES_PER_DIMENSION) {
            dimensionCache.clear();
        }

        dimensionCache.put(getBlockKey(pos), structure);
    }

    public ResourceLocation getStructureAt(Level level, BlockPos pos) {
        String dimensionKey = level.dimension().location().toString();
        Map<Long, ResourceLocation> dimensionCache = structureCache.get(dimensionKey);
        return dimensionCache != null ? dimensionCache.get(getBlockKey(pos)) : null;
    }

    @SubscribeEvent
    public void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getChunk() instanceof LevelChunk chunk) {
            String dimensionKey = chunk.getLevel().dimension().location().toString();
            Map<Long, ResourceLocation> dimensionCache = structureCache.get(dimensionKey);
            if (dimensionCache != null) {
                ChunkPos chunkPos = chunk.getPos();
                dimensionCache.keySet().removeIf(blockPosLong -> {
                    BlockPos pos = BlockPos.of(blockPosLong);
                    return chunkPos.equals(new ChunkPos(pos));
                });
            }
        }
    }

    public void clearCache() {
        structureCache.clear();
    }
}