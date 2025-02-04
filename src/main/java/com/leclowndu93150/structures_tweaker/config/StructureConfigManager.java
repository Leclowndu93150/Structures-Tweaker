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
                            List<ResourceLocation> structures = new ArrayList<>();
                            registry.forEach(structure -> {
                                ResourceLocation id = registry.getKey(structure);
                                if (id != null) structures.add(id);
                            });

                            structures.forEach(id -> {
                                Path configPath = CONFIG_DIR.resolve(id.getPath() + ".json");
                                if (!configPath.toFile().exists()) {
                                    try (FileWriter writer = new FileWriter(configPath.toFile())) {
                                        GSON.toJson(new StructureConfig(), writer);
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
        System.out.println("Loading configs from: " + CONFIG_DIR);
        try {
            Files.walk(CONFIG_DIR)
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".json"))
                    .forEach(path -> {
                        try {
                            String structureName = path.getFileName().toString().replace(".json", "");
                            System.out.println("Loading config for: " + structureName);
                            StructureConfig config = GSON.fromJson(Files.readString(path), StructureConfig.class);
                            configCache.put(ResourceLocation.tryParse(structureName), config);
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
