package com.leclowndu93150.structures_tweaker.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public class ClientCommands {
    @SubscribeEvent
    public static void onCommandRegister(RegisterCommandsEvent event) {
        registerShowCommand(event.getDispatcher());
    }

    private static void registerShowCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("structuretweaker")
                .then(Commands.literal("show")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("enable")
                                .executes(context -> {
                                    ShowStructureCommand.setEnabled(true);
                                    context.getSource().sendSuccess(() -> Component.literal("Structure display enabled"), false);
                                    ShowStructureCommand.checkStructures(context.getSource());
                                    return 1;
                                }))
                        .then(Commands.literal("disable")
                                .executes(context -> {
                                    ShowStructureCommand.setEnabled(false);
                                    context.getSource().sendSuccess(() -> Component.literal("Structure display disabled"), false);
                                    return 1;
                                }))
                        .executes(context -> ShowStructureCommand.checkStructures(context.getSource()))));
    }
}
