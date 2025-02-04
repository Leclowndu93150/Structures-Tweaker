package com.leclowndu93150.structures_tweaker.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.leclowndu93150.structures_tweaker.StructuresTweaker;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class StructureConfigManager {
    private static final Path CONFIG_DIR = Path.of("config", StructuresTweaker.MODID);
    private final Map<ResourceLocation, StructureConfig> configCache = new ConcurrentHashMap<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private volatile boolean configsLoaded = false;

    public void generateConfigs() {
        try {
            Files.createDirectories(CONFIG_DIR);
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                server.registryAccess()
                        .registry(Registries.STRUCTURE)
                        .ifPresent(registry -> {
                            registry.forEach(structure -> {
                                ResourceLocation id = registry.getKey(structure);
                                if (id != null) {
                                    Path configPath = CONFIG_DIR.resolve(id.toString().replace(':', '/') + ".json");
                                    try {
                                        Files.createDirectories(configPath.getParent());
                                        if (!configPath.toFile().exists()) {
                                            try (FileWriter writer = new FileWriter(configPath.toFile())) {
                                                GSON.toJson(new StructureConfig(), writer);
                                            }
                                        }
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                }
                            });
                        });
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public boolean isReady() {
        return configsLoaded;
    }

    public void loadConfigs() {
        configCache.clear();
        try {
            Files.walk(CONFIG_DIR)
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".json"))
                    .forEach(path -> {
                        try {
                            String relativePath = CONFIG_DIR.relativize(path).toString().replace('\\', '/');
                            String structureId = relativePath.substring(0, relativePath.length() - 5); // Remove .json
                            ResourceLocation id = ResourceLocation.tryParse(structureId);
                            StructureConfig config = GSON.fromJson(Files.readString(path), StructureConfig.class);
                            configCache.put(id, config);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    });
        } catch (IOException e) {
            e.printStackTrace();
        }
        configsLoaded = true;
    }

    public Map<ResourceLocation, StructureConfig> getAllConfigs() {
        return configCache;
    }

    public StructureConfig getConfig(ResourceLocation id) {
        return configCache.get(id);
    }
}
