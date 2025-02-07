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
    private static final int MAX_ENTRIES_PER_DIMENSION = 1000;
    private final Map<String, StructureBoundCache> dimensionCaches = new ConcurrentHashMap<>();

    private static class StructureBoundCache {
        final Long2ObjectMap<ResourceLocation> structures = new Long2ObjectOpenHashMap<>();
        final Long2ObjectMap<BoundingBox> bounds = new Long2ObjectOpenHashMap<>();
        final Map<ResourceLocation, BoundingBox[]> structureBounds = new ConcurrentHashMap<>();
    }

    public void cacheStructure(Level level, BlockPos pos, ResourceLocation structure, BoundingBox bounds) {
        String dimensionKey = level.dimension().location().toString();
        StructureBoundCache cache = dimensionCaches.computeIfAbsent(dimensionKey, k -> new StructureBoundCache());

        if (cache.structures.size() >= MAX_ENTRIES_PER_DIMENSION) {
            cache.structures.clear();
            cache.bounds.clear();
            cache.structureBounds.clear();
        }

        // Cache the reference point and bounds
        ChunkPos chunkPos = new ChunkPos(pos);
        cache.structures.put(chunkPos.toLong(), structure);
        cache.bounds.put(chunkPos.toLong(), bounds);

        // Add to structure-specific bounds cache
        cache.structureBounds.compute(structure, (k, v) -> {
            if (v == null) {
                return new BoundingBox[]{bounds};
            }
            BoundingBox[] newArray = new BoundingBox[v.length + 1];
            System.arraycopy(v, 0, newArray, 0, v.length);
            newArray[v.length] = bounds;
            return newArray;
        });

        // Cache all chunks that this structure covers
        int minChunkX = bounds.minX() >> 4;
        int maxChunkX = bounds.maxX() >> 4;
        int minChunkZ = bounds.minZ() >> 4;
        int maxChunkZ = bounds.maxZ() >> 4;

        for (int x = minChunkX; x <= maxChunkX; x++) {
            for (int z = minChunkZ; z <= maxChunkZ; z++) {
                long chunkKey = ChunkPos.asLong(x, z);
                cache.structures.put(chunkKey, structure);
                cache.bounds.put(chunkKey, bounds);
            }
        }
    }

    public ResourceLocation getStructureAt(Level level, BlockPos pos) {
        String dimensionKey = level.dimension().location().toString();
        StructureBoundCache cache = dimensionCaches.get(dimensionKey);
        if (cache == null) return null;

        ChunkPos chunkPos = new ChunkPos(pos);
        long chunkKey = chunkPos.toLong();

        ResourceLocation structure = cache.structures.get(chunkKey);
        if (structure == null) return null;

        BoundingBox bounds = cache.bounds.get(chunkKey);
        if (bounds == null) return null;

        if (bounds.isInside(pos)) {
            return structure;
        } else {
            // Remove invalid cache entry
            cache.structures.remove(chunkKey);
            cache.bounds.remove(chunkKey);
            return null;
        }
    }

    public boolean isInStructure(Level level, BlockPos pos, ResourceLocation structure) {
        String dimensionKey = level.dimension().location().toString();
        StructureBoundCache cache = dimensionCaches.get(dimensionKey);
        if (cache == null) return false;

        BoundingBox[] bounds = cache.structureBounds.get(structure);
        if (bounds == null) return false;

        for (BoundingBox box : bounds) {
            if (box.isInside(pos)) {
                return true;
            }
        }
        return false;
    }

    @SubscribeEvent
    public void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getChunk() instanceof LevelChunk chunk) {
            String dimensionKey = chunk.getLevel().dimension().location().toString();
            StructureBoundCache cache = dimensionCaches.get(dimensionKey);
            if (cache != null) {
                long chunkKey = chunk.getPos().toLong();
                ResourceLocation structure = cache.structures.remove(chunkKey);
                BoundingBox bounds = cache.bounds.remove(chunkKey);

                if (structure != null && bounds != null) {
                    cache.structureBounds.computeIfPresent(structure, (k, v) -> {
                        if (v.length == 1) return null;
                        return removeFromArray(v, bounds);
                    });
                }
            }
        }
    }

    private BoundingBox[] removeFromArray(BoundingBox[] array, BoundingBox toRemove) {
        BoundingBox[] newArray = new BoundingBox[array.length - 1];
        int newIndex = 0;
        for (BoundingBox box : array) {
            if (!box.equals(toRemove)) {
                newArray[newIndex++] = box;
            }
        }
        return newArray;
    }

    public void clearCache() {
        dimensionCaches.clear();
    }
}