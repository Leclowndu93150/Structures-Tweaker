package com.leclowndu93150.structures_tweaker.command;

import com.leclowndu93150.structures_tweaker.data.DefeatedStructuresData;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;


@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ServerCommands {
    @SubscribeEvent
    public static void onCommandRegister(RegisterCommandsEvent event) {
        registerDefeatCommand(event.getDispatcher());
    }
    private static void registerDefeatCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("structuretweaker")
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
}
