package com.leclowndu93150.structures_tweaker.data;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;

import java.util.HashMap;
import java.util.Map;

public class DefeatedStructuresData extends SavedData {
    private final Map<ResourceLocation, BoundingBox> defeatedStructures = new HashMap<>();

    public static DefeatedStructuresData get(ServerLevel level) {
        DimensionDataStorage storage = level.getDataStorage();
        return storage.computeIfAbsent(
                new SavedData.Factory<>(
                        DefeatedStructuresData::new,
                        (tag, provider) -> load(tag),
                        null
                ),
                "structures_tweaker_defeated"
        );
    }

    private static DefeatedStructuresData load(CompoundTag tag) {
        DefeatedStructuresData data = new DefeatedStructuresData();
        ListTag list = tag.getList("defeated_structures", 10);

        for (int i = 0; i < list.size(); i++) {
            CompoundTag structureTag = list.getCompound(i);
            ResourceLocation id = ResourceLocation.tryParse(structureTag.getString("id"));
            if (id != null) {
                BoundingBox box = new BoundingBox(
                        structureTag.getInt("minX"),
                        structureTag.getInt("minY"),
                        structureTag.getInt("minZ"),
                        structureTag.getInt("maxX"),
                        structureTag.getInt("maxY"),
                        structureTag.getInt("maxZ")
                );
                data.defeatedStructures.put(id, box);
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        defeatedStructures.forEach((id, box) -> {
            CompoundTag structureTag = new CompoundTag();
            structureTag.putString("id", id.toString());
            structureTag.putInt("minX", box.minX());
            structureTag.putInt("minY", box.minY());
            structureTag.putInt("minZ", box.minZ());
            structureTag.putInt("maxX", box.maxX());
            structureTag.putInt("maxY", box.maxY());
            structureTag.putInt("maxZ", box.maxZ());
            list.add(structureTag);
        });
        tag.put("defeated_structures", list);
        return tag;
    }

    public void markDefeated(ResourceLocation id, BoundingBox box) {
        defeatedStructures.put(id, box);
        setDirty();
    }

    public boolean isDefeated(ResourceLocation id, BoundingBox box) {
        BoundingBox defeatedBox = defeatedStructures.get(id);
        return defeatedBox != null && defeatedBox.equals(box);
    }
}