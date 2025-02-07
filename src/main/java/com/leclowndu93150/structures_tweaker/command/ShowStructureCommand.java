package com.leclowndu93150.structures_tweaker.command;

import com.leclowndu93150.structures_tweaker.render.StructureBoxRenderer;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.resources.ResourceLocation;

import java.util.concurrent.atomic.AtomicBoolean;

public class ShowStructureCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("structuretweaker")
                .then(Commands.literal("show")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> {
                            return showStructures(context.getSource());
                        }))
                .then(Commands.literal("clear")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> {
                            StructureBoxRenderer.setEnabled(false);
                            context.getSource().sendSuccess(() -> Component.literal("Cleared structure displays"), false);
                            return 1;
                        })));
    }

    private static int showStructures(CommandSourceStack source) {
        ServerLevel serverLevel = source.getLevel();
        BlockPos pos = BlockPos.containing(source.getPosition());
        AtomicBoolean found = new AtomicBoolean(false);

        // Clear previous and enable new detection
        StructureBoxRenderer.setEnabled(true);

        // Get the chunk position the player is in
        ChunkPos centerChunk = new ChunkPos(pos);

        // Search in a 5x5 chunk area around the player
        int searchRadius = 2;

        for (int x = -searchRadius; x <= searchRadius; x++) {
            for (int z = -searchRadius; z <= searchRadius; z++) {
                ChunkPos checkChunk = new ChunkPos(centerChunk.x + x, centerChunk.z + z);
                ChunkAccess chunk = serverLevel.getChunk(checkChunk.x, checkChunk.z);

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

                            source.sendSuccess(() -> Component.literal(String.format(
                                    "Found structure %s at [%d, %d, %d] to [%d, %d, %d]",
                                    id,
                                    box.minX(), box.minY(), box.minZ(),
                                    box.maxX(), box.maxY(), box.maxZ()
                            )), false);
                        }
                    }
                });
            }
        }

        if (!found.get()) {
            source.sendFailure(Component.literal("No structures found in the nearby area"));
        }

        return 1;
    }
}