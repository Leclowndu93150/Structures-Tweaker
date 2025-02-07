package com.leclowndu93150.structures_tweaker.data;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import java.util.HashSet;
import java.util.Set;

public class EmptyChunksData extends SavedData {
    private final Set<Long> emptyChunks = new HashSet<>();

    public static EmptyChunksData get(ServerLevel level) {
        DimensionDataStorage storage = level.getDataStorage();
        return storage.computeIfAbsent(
                new SavedData.Factory<>(
                        EmptyChunksData::new,
                        (tag, provider) -> load(tag),
                        null
                ),
                "structures_tweaker_empty_chunks"
        );
    }

    private static EmptyChunksData load(CompoundTag tag) {
        EmptyChunksData data = new EmptyChunksData();
        ListTag list = tag.getList("empty_chunks", 4); // 4 is for long array
        for (int i = 0; i < list.size(); i++) {
            data.emptyChunks.add(((LongTag)list.get(i)).getAsLong());
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        for (Long chunkPos : emptyChunks) {
            list.add(LongTag.valueOf(chunkPos));
        }
        tag.put("empty_chunks", list);
        return tag;
    }

    public void markEmpty(ChunkPos pos) {
        emptyChunks.add(pos.toLong());
        setDirty();
    }

    public boolean isEmpty(ChunkPos pos) {
        return emptyChunks.contains(pos.toLong());
    }

    public void clear() {
        emptyChunks.clear();
        setDirty();
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        event.getServer().getAllLevels().forEach(level ->
                EmptyChunksData.get(level).clear()
        );
    }
}