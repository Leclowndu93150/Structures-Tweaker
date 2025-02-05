package com.leclowndu93150.structures_tweaker.data;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import javax.annotation.Nullable;
import java.util.*;

public class StructureDataManager extends SavedData {
    private final Map<ResourceLocation, Set<BlockPos>> structureBlocks = new HashMap<>();
    private final Map<BlockPos, ResourceLocation> posToStructure = new HashMap<>();

    public static StructureDataManager get(ServerLevel level) {
        DimensionDataStorage storage = level.getDataStorage();
        return storage.computeIfAbsent(
                new SavedData.Factory<>(
                        StructureDataManager::new,
                        (tag, provider) -> load(tag),
                        null
                ),
                "structures_tweaker_data"
        );
    }

    private static StructureDataManager load(CompoundTag tag) {
        StructureDataManager data = new StructureDataManager();
        CompoundTag structuresTag = tag.getCompound("structures");

        for (String key : structuresTag.getAllKeys()) {
            ResourceLocation structureId = ResourceLocation.tryParse(key);
            ListTag positions = structuresTag.getList(key, 10);

            Set<BlockPos> blocks = new HashSet<>();
            for (int i = 0; i < positions.size(); i++) {
                CompoundTag posTag = positions.getCompound(i);
                BlockPos pos = BlockPos.of(posTag.getLong("pos"));
                blocks.add(pos);
                data.posToStructure.put(pos, structureId);
            }

            data.structureBlocks.put(structureId, blocks);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag compoundTag, HolderLookup.Provider provider) {
        CompoundTag structuresTag = new CompoundTag();

        structureBlocks.forEach((id, positions) -> {
            ListTag positionsTag = new ListTag();
            positions.forEach(pos -> {
                CompoundTag posTag = new CompoundTag();
                posTag.putLong("pos", pos.asLong());
                positionsTag.add(posTag);
            });
            structuresTag.put(id.toString(), positionsTag);
        });

        compoundTag.put("structures", structuresTag);
        return compoundTag;
    }

    public void registerStructureBlocks(ResourceLocation structureId, Set<BlockPos> positions) {
        structureBlocks.put(structureId, positions);
        positions.forEach(pos -> posToStructure.put(pos, structureId));
        setDirty();
    }

    @Nullable
    public ResourceLocation getStructureAt(BlockPos pos) {
        return posToStructure.get(pos);
    }
}