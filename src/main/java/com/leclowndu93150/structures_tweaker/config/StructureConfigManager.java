package com.leclowndu93150.structures_tweaker.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.leclowndu93150.structures_tweaker.StructuresTweaker;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
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

    private static final Logger LOGGER = LogManager.getLogger();

    public void generateConfigs() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            server.registryAccess().registry(Registries.STRUCTURE).ifPresent(registry -> {
                registry.forEach(structure -> {
                    ResourceLocation id = registry.getKey(structure);
                    if (id != null) {
                        Path configPath = CONFIG_DIR.resolve(id.getNamespace() + "/" + id.getPath() + ".json");
                        try {
                            Files.createDirectories(configPath.getParent());
                            if (!Files.exists(configPath)) {
                                Files.writeString(configPath, GSON.toJson(new StructureConfig()));
                            }
                        } catch (IOException e) {
                            LOGGER.error("Failed to generate config for {}: {}", id, e.getMessage());
                        }
                    }
                });
            });
        }
    }

    public boolean isReady() {
        return configsLoaded;
    }

    public void loadConfigs() {
        configCache.clear();
        LOGGER.info("Loading structure configs...");

        try {
            Files.walk(CONFIG_DIR)
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".json"))
                    .forEach(path -> {
                        try {
                            String relativePath = CONFIG_DIR.relativize(path).toString().replace('\\', '/');
                            String structureId = relativePath.substring(0, relativePath.length() - 5);
                            ResourceLocation id = ResourceLocation.tryParse(structureId);
                            if (id != null) {
                                String content = Files.readString(path);
                                StructureConfig config = GSON.fromJson(content, StructureConfig.class);
                                configCache.put(id, config);
                            }
                        } catch (IOException e) {
                            LOGGER.error("Error loading config {}: {}", path, e.getMessage());
                        }
                    });
        } catch (IOException e) {
            LOGGER.error("Error walking config directory: {}", e.getMessage());
        }
        configsLoaded = true;
        LOGGER.info("Loaded {} structure configs", configCache.size());
    }

    public Map<ResourceLocation, StructureConfig> getAllConfigs() {
        return configCache;
    }
}