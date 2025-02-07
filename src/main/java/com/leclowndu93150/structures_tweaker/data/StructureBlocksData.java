package com.leclowndu93150.structures_tweaker.data;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class StructureBlocksData extends SavedData {
    private final Map<ResourceLocation, Set<BlockPos>> playerPlacedBlocks = new HashMap<>();
    private final Map<BlockPos, ResourceLocation> posToStructure = new HashMap<>();

    public static StructureBlocksData get(ServerLevel level) {
        DimensionDataStorage storage = level.getDataStorage();
        return storage.computeIfAbsent(
                StructureBlocksData::load,
                StructureBlocksData::new,
                "structures_tweaker_player_blocks"
        );
    }

    private static StructureBlocksData load(CompoundTag tag) {
        StructureBlocksData data = new StructureBlocksData();
        CompoundTag structuresTag = tag.getCompound("player_placed_blocks");

        for (String key : structuresTag.getAllKeys()) {
            ResourceLocation structureId = ResourceLocation.tryParse(key);
            if (structureId != null) {
                ListTag positions = structuresTag.getList(key, 10);
                Set<BlockPos> blocks = new HashSet<>();

                for (int i = 0; i < positions.size(); i++) {
                    CompoundTag posTag = positions.getCompound(i);
                    BlockPos pos = BlockPos.of(posTag.getLong("pos"));
                    blocks.add(pos);
                    data.posToStructure.put(pos, structureId);
                }

                data.playerPlacedBlocks.put(structureId, blocks);
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag structuresTag = new CompoundTag();

        playerPlacedBlocks.forEach((id, positions) -> {
            ListTag positionsTag = new ListTag();
            positions.forEach(pos -> {
                CompoundTag posTag = new CompoundTag();
                posTag.putLong("pos", pos.asLong());
                positionsTag.add(posTag);
            });
            structuresTag.put(id.toString(), positionsTag);
        });

        tag.put("player_placed_blocks", structuresTag);
        return tag;
    }

    public void addPlayerBlock(ResourceLocation structureId, BlockPos pos) {
        playerPlacedBlocks.computeIfAbsent(structureId, k -> new HashSet<>()).add(pos);
        posToStructure.put(pos, structureId);
        setDirty();
    }

    public void removePlayerBlock(BlockPos pos) {
        ResourceLocation structureId = posToStructure.remove(pos);
        if (structureId != null) {
            Set<BlockPos> blocks = playerPlacedBlocks.get(structureId);
            if (blocks != null) {
                blocks.remove(pos);
                if (blocks.isEmpty()) {
                    playerPlacedBlocks.remove(structureId);
                }
                setDirty();
            }
        }
    }

    public boolean isPlayerPlaced(BlockPos pos) {
        return posToStructure.containsKey(pos);
    }

    public void clearStructure(ResourceLocation structureId) {
        Set<BlockPos> blocks = playerPlacedBlocks.remove(structureId);
        if (blocks != null) {
            blocks.forEach(posToStructure::remove);
            setDirty();
        }
    }
}