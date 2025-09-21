package com.leclowndu93150.structures_tweaker.cache;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

public class StructureCache {
    private static final int MAX_ENTRIES_PER_DIMENSION = 1000;
    private final Map<String, StructureBoundCache> dimensionCaches = new ConcurrentHashMap<>();

    public static class StructureBoundCache {
        public final Long2ObjectMap<ResourceLocation> structures = new Long2ObjectOpenHashMap<>();
        public final Long2ObjectMap<BoundingBox> bounds = new Long2ObjectOpenHashMap<>();
        final Map<ResourceLocation, BoundingBox[]> structureBounds = new ConcurrentHashMap<>();
        // Cache for fast position checks - maps position to list of structure bounds containing it
        final Map<ChunkPos, List<StructureInstance>> chunkStructures = new ConcurrentHashMap<>();
    }
    
    public static class StructureInstance {
        public final ResourceLocation structure;
        public final BoundingBox bounds;
        
        public StructureInstance(ResourceLocation structure, BoundingBox bounds) {
            this.structure = structure;
            this.bounds = bounds;
        }
    }

    public void cacheStructure(Level level, BlockPos pos, ResourceLocation structure, BoundingBox bounds) {
        if (level == null || pos == null || structure == null || bounds == null) {
            return;
        }

        String dimensionKey = level.dimension().location().toString();
        StructureBoundCache cache = dimensionCaches.computeIfAbsent(dimensionKey, k -> new StructureBoundCache());

        if (cache.structures.size() >= MAX_ENTRIES_PER_DIMENSION) {
            cache.structures.clear();
            cache.bounds.clear();
            cache.structureBounds.clear();
        }

        ChunkPos chunkPos = new ChunkPos(pos);
        cache.structures.put(chunkPos.toLong(), structure);
        cache.bounds.put(chunkPos.toLong(), bounds);

        cache.structureBounds.compute(structure, (k, v) -> {
            if (v == null) {
                return new BoundingBox[]{bounds};
            }
            BoundingBox[] newArray = new BoundingBox[v.length + 1];
            System.arraycopy(v, 0, newArray, 0, v.length);
            newArray[v.length] = bounds;
            return newArray;
        });

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
        if (level == null || pos == null) {
            return null;
        }

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
            cache.structures.remove(chunkKey);
            cache.bounds.remove(chunkKey);
            return null;
        }
    }

    @SubscribeEvent
    public void onChunkUnload(ChunkEvent.Unload event) {
        if (!(event.getChunk() instanceof LevelChunk chunk)) {
            return;
        }

        String dimensionKey = chunk.getLevel().dimension().location().toString();
        StructureBoundCache cache = dimensionCaches.get(dimensionKey);
        if (cache == null) return;

        long chunkKey = chunk.getPos().toLong();
        ResourceLocation structure = cache.structures.remove(chunkKey);
        BoundingBox bounds = cache.bounds.remove(chunkKey);

        if (structure != null && bounds != null) {
            cache.structureBounds.computeIfPresent(structure, (k, v) -> {
                if (v == null || v.length == 0) return null;
                if (v.length == 1) {
                    return v[0] != null && v[0].equals(bounds) ? null : v;
                }
                return removeFromArray(v, bounds);
            });
        }
    }

    private BoundingBox[] removeFromArray(BoundingBox[] array, BoundingBox toRemove) {
        if (array == null || toRemove == null) {
            return array;
        }

        return Arrays.stream(array)
                .filter(box -> box != null && !box.equals(toRemove))
                .toArray(BoundingBox[]::new);
    }

    public void clearCache() {
        dimensionCaches.clear();
    }
    
    /**
     * Fast check if a position is within any cached structure bounds
     * Returns the structure at the position or null if not in a structure
     */
    public ResourceLocation getStructureAtPosition(Level level, BlockPos pos) {
        if (level == null || pos == null) return null;
        
        String dimensionKey = level.dimension().location().toString();
        StructureBoundCache cache = dimensionCaches.get(dimensionKey);
        if (cache == null) return null;
        
        ChunkPos chunkPos = new ChunkPos(pos);
        List<StructureInstance> instances = cache.chunkStructures.get(chunkPos);
        
        if (instances != null) {
            for (StructureInstance instance : instances) {
                if (instance.bounds.isInside(pos)) {
                    return instance.structure;
                }
            }
        }

        return getStructureAt(level, pos);
    }
    
    /**
     * Cache structure bounds for fast position checking
     */
    public void cacheStructureBounds(Level level, ResourceLocation structure, BoundingBox bounds) {
        if (level == null || structure == null || bounds == null) return;
        
        String dimensionKey = level.dimension().location().toString();
        StructureBoundCache cache = dimensionCaches.computeIfAbsent(dimensionKey, k -> new StructureBoundCache());
        
        StructureInstance instance = new StructureInstance(structure, bounds);

        int minChunkX = bounds.minX() >> 4;
        int maxChunkX = bounds.maxX() >> 4;
        int minChunkZ = bounds.minZ() >> 4;
        int maxChunkZ = bounds.maxZ() >> 4;
        
        for (int x = minChunkX; x <= maxChunkX; x++) {
            for (int z = minChunkZ; z <= maxChunkZ; z++) {
                ChunkPos chunkPos = new ChunkPos(x, z);
                cache.chunkStructures.computeIfAbsent(chunkPos, k -> Collections.synchronizedList(new ArrayList<>()))
                    .add(instance);
            }
        }
    }
    
    /**
     * Get cached bounds for a specific structure at a position
     */
    public BoundingBox getCachedBounds(Level level, ResourceLocation structure, BlockPos pos) {
        if (level == null || structure == null || pos == null) return null;
        
        String dimensionKey = level.dimension().location().toString();
        StructureBoundCache cache = dimensionCaches.get(dimensionKey);
        if (cache == null) return null;
        
        ChunkPos chunkPos = new ChunkPos(pos);
        List<StructureInstance> instances = cache.chunkStructures.get(chunkPos);
        
        if (instances != null) {
            for (StructureInstance instance : instances) {
                if (instance.structure.equals(structure) && instance.bounds.isInside(pos)) {
                    return instance.bounds;
                }
            }
        }
        
        // Fallback to chunk-based cache
        BoundingBox bounds = cache.bounds.get(chunkPos.toLong());
        if (bounds != null && bounds.isInside(pos)) {
            return bounds;
        }
        
        return null;
    }
}