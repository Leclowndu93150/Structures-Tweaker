package com.leclowndu93150.structures_tweaker.command;

import com.leclowndu93150.structures_tweaker.data.EmptyChunksData;
import com.leclowndu93150.structures_tweaker.render.StructureBoxRenderer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.concurrent.atomic.AtomicBoolean;

@OnlyIn(Dist.CLIENT)
public class ShowStructureCommand {
    private static boolean isEnabled = false;
    private static long lastCheckTime = 0;
    private static final long CHECK_INTERVAL = 20;

    public static void setEnabled(boolean enabled) {
        isEnabled = enabled;
        StructureBoxRenderer.setEnabled(enabled);
        if (!enabled) {
            StructureBoxRenderer.clearBoxes();
        }
    }

    public static boolean isEnabled() {
        return isEnabled;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (!isEnabled || event.phase != TickEvent.Phase.END) return;

        long currentTime = event.getServer().getTickCount();
        if (currentTime - lastCheckTime >= CHECK_INTERVAL) {
            lastCheckTime = currentTime;
            var player = event.getServer().getPlayerList().getPlayers();
            for (var p : player) {
                checkStructures(p.createCommandSourceStack());
            }
        }
    }

    public static int checkStructures(CommandSourceStack source) {
        ServerLevel serverLevel = source.getLevel();
        BlockPos pos = BlockPos.containing(source.getPosition());
        AtomicBoolean found = new AtomicBoolean(false);

        ChunkPos centerChunk = new ChunkPos(pos);
        EmptyChunksData emptyChunksData = EmptyChunksData.get(serverLevel);

        int searchRadius = 2;

        for (int x = -searchRadius; x <= searchRadius; x++) {
            for (int z = -searchRadius; z <= searchRadius; z++) {
                ChunkPos checkChunk = new ChunkPos(centerChunk.x + x, centerChunk.z + z);

                if (emptyChunksData.isEmpty(checkChunk)) {
                    continue;
                }

                ChunkAccess chunk = serverLevel.getChunk(checkChunk.x, checkChunk.z);
                boolean structureFound = false;

                chunk.getAllStarts().forEach((structure, structureStart) -> {
                    if (structureStart != null) {
                        structureStart.getBoundingBox();
                        ResourceLocation id = serverLevel.registryAccess()
                                .registryOrThrow(Registries.STRUCTURE)
                                .getKey(structure);
                        if (id != null) {
                            found.set(true);
                            BoundingBox box = structureStart.getBoundingBox();
                            StructureBoxRenderer.addBox(id, box);
                        }
                    }
                });

                if (!structureFound) {
                    emptyChunksData.markEmpty(checkChunk);
                }
            }
        }

        return 1;
    }
}