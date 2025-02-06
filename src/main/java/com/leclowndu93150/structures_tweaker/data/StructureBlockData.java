package com.leclowndu93150.structures_tweaker.data;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;

import java.util.*;
import java.util.concurrent.*;
import java.util.BitSet;

public class StructureBlockData extends SavedData {
    private final Map<ResourceLocation, BoundingBox> bounds = new ConcurrentHashMap<>();
    private final Map<ResourceLocation, Map<Long, BitSet>> blockMasks = new ConcurrentHashMap<>();
    private static final ExecutorService executor = Executors.newFixedThreadPool(3);

    private record ChunkPos(int x, int z) {
        long asLong() {
            return (((long)x) << 32) | (z & 0xFFFFFFFFL);
        }

        static ChunkPos fromLong(long l) {
            return new ChunkPos((int)(l >> 32), (int)l);
        }
    }

    public CompletableFuture<Void> registerStructure(ResourceLocation id, ServerLevel level, BoundingBox box) {
        if (bounds.containsKey(id)) return CompletableFuture.completedFuture(null);

        return CompletableFuture.runAsync(() -> {
            bounds.put(id, box);
            Map<Long, BitSet> chunkMasks = new ConcurrentHashMap<>();

            int minChunkX = box.minX() >> 4;
            int maxChunkX = box.maxX() >> 4;
            int minChunkZ = box.minZ() >> 4;
            int maxChunkZ = box.maxZ() >> 4;

            for (int cx = minChunkX; cx <= maxChunkX; cx++) {
                for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                    if (!level.hasChunkAt(cx << 4, cz << 4)) continue;

                    LevelChunk chunk = level.getChunk(cx, cz);
                    BitSet mask = new BitSet(4096); // 16x16x16

                    int minX = Math.max(box.minX(), cx << 4);
                    int maxX = Math.min(box.maxX(), (cx << 4) + 15);
                    int minZ = Math.max(box.minZ(), cz << 4);
                    int maxZ = Math.min(box.maxZ(), (cz << 4) + 15);

                    for (int x = minX; x <= maxX; x++) {
                        for (int z = minZ; z <= maxZ; z++) {
                            for (int y = box.minY(); y <= box.maxY(); y++) {
                                BlockPos pos = new BlockPos(x, y, z);
                                if (!chunk.getBlockState(pos).isAir()) {
                                    int index = ((y & 15) << 8) | ((x & 15) << 4) | (z & 15);
                                    mask.set(index);
                                }
                            }
                        }
                    }

                    if (!mask.isEmpty()) {
                        chunkMasks.put(new ChunkPos(cx, cz).asLong(), mask);
                    }
                }
            }

            if (!chunkMasks.isEmpty()) {
                blockMasks.put(id, chunkMasks);
                setDirty();
            }
        }, executor);
    }

    public boolean isOriginalBlock(ResourceLocation id, BlockPos pos) {
        Map<Long, BitSet> masks = blockMasks.get(id);
        if (masks == null) return false;

        ChunkPos chunkPos = new ChunkPos(pos.getX() >> 4, pos.getZ() >> 4);
        BitSet mask = masks.get(chunkPos.asLong());
        if (mask == null) return false;

        int index = ((pos.getY() & 15) << 8) | ((pos.getX() & 15) << 4) | (pos.getZ() & 15);
        return mask.get(index);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        CompoundTag structuresTag = new CompoundTag();

        for (var entry : bounds.entrySet()) {
            CompoundTag structureTag = new CompoundTag();
            BoundingBox box = entry.getValue();

            // Save bounds
            structureTag.putInt("minX", box.minX());
            structureTag.putInt("minY", box.minY());
            structureTag.putInt("minZ", box.minZ());
            structureTag.putInt("maxX", box.maxX());
            structureTag.putInt("maxY", box.maxY());
            structureTag.putInt("maxZ", box.maxZ());

            // Save masks
            CompoundTag masksTag = new CompoundTag();
            Map<Long, BitSet> masks = blockMasks.get(entry.getKey());
            if (masks != null) {
                for (var maskEntry : masks.entrySet()) {
                    masksTag.putByteArray(String.valueOf(maskEntry.getKey()),
                            maskEntry.getValue().toByteArray());
                }
            }
            structureTag.put("masks", masksTag);

            structuresTag.put(entry.getKey().toString(), structureTag);
        }

        tag.put("structures", structuresTag);
        return tag;
    }

    public static StructureBlockData load(CompoundTag tag, HolderLookup.Provider provider) {
        StructureBlockData data = new StructureBlockData();
        CompoundTag structuresTag = tag.getCompound("structures");

        for (String key : structuresTag.getAllKeys()) {
            ResourceLocation id = ResourceLocation.tryParse(key);
            if (id == null) continue;

            CompoundTag structureTag = structuresTag.getCompound(key);

            // Load bounds
            BoundingBox box = new BoundingBox(
                    structureTag.getInt("minX"),
                    structureTag.getInt("minY"),
                    structureTag.getInt("minZ"),
                    structureTag.getInt("maxX"),
                    structureTag.getInt("maxY"),
                    structureTag.getInt("maxZ")
            );
            data.bounds.put(id, box);

            // Load masks
            Map<Long, BitSet> masks = new ConcurrentHashMap<>();
            CompoundTag masksTag = structureTag.getCompound("masks");
            for (String chunkKey : masksTag.getAllKeys()) {
                long chunk = Long.parseLong(chunkKey);
                byte[] bytes = masksTag.getByteArray(chunkKey);
                BitSet mask = BitSet.valueOf(bytes);
                masks.put(chunk, mask);
            }
            data.blockMasks.put(id, masks);
        }

        return data;
    }

    public static void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}