package com.leclowndu93150.structures_tweaker.command;

import com.leclowndu93150.structures_tweaker.data.DefeatedStructuresData;
import com.leclowndu93150.structures_tweaker.data.EmptyChunksData;
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
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.concurrent.atomic.AtomicBoolean;

public class ShowStructureCommand {
    private static boolean isEnabled = false;
    private static long lastCheckTime = 0;
    private static final long CHECK_INTERVAL = 20;

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("structuretweaker")
                .then(Commands.literal("show")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("enable")
                                .executes(context -> {
                                    isEnabled = true;
                                    StructureBoxRenderer.setEnabled(true);
                                    context.getSource().sendSuccess(() -> Component.literal("Structure display enabled"), false);
                                    checkStructures(context.getSource());
                                    return 1;
                                }))
                        .then(Commands.literal("disable")
                                .executes(context -> {
                                    isEnabled = false;
                                    StructureBoxRenderer.setEnabled(false);
                                    StructureBoxRenderer.clearBoxes();
                                    context.getSource().sendSuccess(() -> Component.literal("Structure display disabled"), false);
                                    return 1;
                                }))
                        .executes(context -> checkStructures(context.getSource())))
                .then(Commands.literal("defeat")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> {
                            ServerLevel level = context.getSource().getLevel();
                            BlockPos pos = BlockPos.containing(context.getSource().getPosition());
                            boolean found = false;

                            var registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
                            for (var structure : registry) {
                                ResourceLocation id = registry.getKey(structure);
                                if (id == null) continue;

                                var reference = level.structureManager().getStructureAt(pos, structure);
                                if (reference.isValid()) {
                                    DefeatedStructuresData data = DefeatedStructuresData.get(level);
                                    data.markDefeated(id, reference.getBoundingBox());
                                    context.getSource().sendSuccess(() ->
                                            Component.literal("Structure " + id + " marked as defeated!"), true);
                                    found = true;
                                    break;
                                }
                            }

                            if (!found) {
                                context.getSource().sendFailure(
                                        Component.literal("No structure found at your position"));
                            }

                            return 1;
                        })));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!isEnabled) return;

        long currentTime = event.getServer().getTickCount();
        if (currentTime - lastCheckTime >= CHECK_INTERVAL) {
            lastCheckTime = currentTime;
            var player = event.getServer().getPlayerList().getPlayers();
            for (var p : player) {
                checkStructures(p.createCommandSourceStack());
            }
        }
    }

    private static int checkStructures(CommandSourceStack source) {
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