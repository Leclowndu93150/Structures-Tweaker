package com.leclowndu93150.structures_tweaker.command;

import com.leclowndu93150.structures_tweaker.StructuresTweaker;
import com.leclowndu93150.structures_tweaker.config.core.StructureConfigManager;
import com.leclowndu93150.structures_tweaker.config.properties.ConfigProperty;
import com.leclowndu93150.structures_tweaker.config.properties.ConfigRegistry;
import com.leclowndu93150.structures_tweaker.data.DefeatedStructuresData;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceKeyArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;


@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ServerCommands {
    private static StructureConfigManager configManager;
    
    public static void setConfigManager(StructureConfigManager manager) {
        configManager = manager;
    }
    
    @SubscribeEvent
    public static void onCommandRegister(RegisterCommandsEvent event) {
        registerDefeatCommand(event.getDispatcher());
        registerConfigCommand(event.getDispatcher());
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
    
    private static final SuggestionProvider<CommandSourceStack> SUGGEST_STRUCTURES = (context, builder) -> {
        ServerLevel level = context.getSource().getLevel();
        var registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        Stream<String> structureIds = registry.keySet().stream()
                .map(ResourceLocation::toString);
        return SharedSuggestionProvider.suggest(structureIds, builder);
    };
    
    private static final SuggestionProvider<CommandSourceStack> SUGGEST_CURRENT_STRUCTURE = (context, builder) -> {
        ServerLevel level = context.getSource().getLevel();
        BlockPos pos = BlockPos.containing(context.getSource().getPosition());
        var registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        List<String> nearbyStructures = new ArrayList<>();
        
        for (var structure : registry) {
            ResourceLocation id = registry.getKey(structure);
            if (id == null) continue;
            var reference = level.structureManager().getStructureAt(pos, structure);
            if (reference.isValid()) {
                nearbyStructures.add(id.toString());
            }
        }
        
        return SharedSuggestionProvider.suggest(nearbyStructures, builder);
    };
    
    private static final SuggestionProvider<CommandSourceStack> SUGGEST_CONFIG_PROPERTIES = (context, builder) -> {
        Stream<String> propertyKeys = ConfigRegistry.getAllProperties().keySet().stream();
        return SharedSuggestionProvider.suggest(propertyKeys, builder);
    };
    
    private static void registerConfigCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("structuretweaker")
                .then(Commands.literal("config")
                        .then(Commands.literal("set")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.argument("structure", ResourceKeyArgument.key(Registries.STRUCTURE))
                                        .then(Commands.argument("property", StringArgumentType.string())
                                                .suggests(SUGGEST_CONFIG_PROPERTIES)
                                                .then(Commands.argument("value", StringArgumentType.greedyString())
                                                        .executes(ServerCommands::executeConfigSet)))))));
    }
    
    private static int executeConfigSet(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        if (configManager == null) {
            context.getSource().sendFailure(Component.literal("Config manager not initialized!"));
            return 0;
        }
        
        ResourceKey<Structure> structureKey = ResourceKeyArgument.getStructure(context, "structure").key();
        ResourceLocation structureId = structureKey.location();
        String propertyKey = StringArgumentType.getString(context, "property");
        String valueStr = StringArgumentType.getString(context, "value");
        
        ConfigProperty<?> property = ConfigRegistry.getProperty(propertyKey);
        if (property == null) {
            context.getSource().sendFailure(Component.literal("Unknown property: " + propertyKey));
            return 0;
        }
        
        try {
            Object parsedValue = parseValue(valueStr, property.getType());
            if (parsedValue == null) {
                context.getSource().sendFailure(Component.literal("Invalid value '" + valueStr + "' for property type " + property.getType().getSimpleName()));
                return 0;
            }
            
            boolean success = configManager.setConfigValue(structureId, propertyKey, parsedValue);
            if (success) {
                context.getSource().sendSuccess(() -> 
                        Component.literal("Set " + structureId + " > " + propertyKey + " = " + valueStr), true);
                return 1;
            } else {
                context.getSource().sendFailure(Component.literal("Failed to save config for " + structureId));
                return 0;
            }
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Error setting config: " + e.getMessage()));
            StructuresTweaker.LOGGER.error("Error setting config", e);
            return 0;
        }
    }
    
    private static Object parseValue(String valueStr, Class<?> type) {
        if (type == Boolean.class) {
            if ("true".equalsIgnoreCase(valueStr)) return true;
            if ("false".equalsIgnoreCase(valueStr)) return false;
            return null;
        } else if (type == Integer.class) {
            try {
                return Integer.parseInt(valueStr);
            } catch (NumberFormatException e) {
                return null;
            }
        } else if (type == String.class) {
            return valueStr;
        } else if (type == List.class) {
            List<String> list = new ArrayList<>();
            for (String item : valueStr.split(",")) {
                list.add(item.trim());
            }
            return list;
        }
        return null;
    }
}
