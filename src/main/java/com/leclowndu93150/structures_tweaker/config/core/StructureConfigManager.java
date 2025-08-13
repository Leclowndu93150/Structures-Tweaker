package com.leclowndu93150.structures_tweaker.config.core;

import com.leclowndu93150.structures_tweaker.config.migration.ModularConfigMigration;
import com.google.gson.*;
import com.leclowndu93150.structures_tweaker.StructuresTweaker;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class StructureConfigManager {
    private static final Path CONFIG_DIR = Path.of("config", StructuresTweaker.MODID);
    private final Map<ResourceLocation, StructureConfig> configCache = new ConcurrentHashMap<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private volatile boolean configsLoaded = false;
    private static final Logger LOGGER = LogManager.getLogger();

    public StructureConfigManager() {
    }

    public void generateConfigs() {
        ModularConfigMigration.migrateAllConfigs(CONFIG_DIR);

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            LOGGER.error("Server instance is null during config generation!");
            return;
        }

        server.registryAccess().registry(Registries.STRUCTURE).ifPresent(registry -> {
            registry.forEach(structure -> {
                ResourceLocation id = registry.getKey(structure);
                if (id == null) return;

                Path configPath = CONFIG_DIR.resolve(id.getNamespace() + "/" + id.getPath() + ".json");
                try {
                    Files.createDirectories(configPath.getParent());
                    if (!Files.exists(configPath)) {
                        ModularStructureConfig config = ModularConfigMigration.loadOrCreateConfig(
                            configPath, id.getNamespace(), id.getPath()
                        );
                        ModularConfigMigration.ConfigWrapper wrapper = new ModularConfigMigration.ConfigWrapper(config);
                        String json = GSON.toJson(wrapper);
                        Files.writeString(configPath, json);
                    }
                } catch (IOException e) {
                    LOGGER.error("Failed to generate config for {}: {}", id, e.getMessage());
                }
            });
        });
    }

    public void loadConfigs() {
        configCache.clear();
        configsLoaded = false;

        try {
            Files.walk(CONFIG_DIR)
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".json"))
                    .forEach(path -> {
                        try {
                            String relativePath = CONFIG_DIR.relativize(path).toString().replace('\\', '/');
                            String[] parts = relativePath.substring(0, relativePath.length() - 5).split("/", 2);

                            if (parts.length != 2) {
                                LOGGER.error("Invalid config path format: {}", relativePath);
                                return;
                            }

                            ResourceLocation id = ResourceLocation.tryParse(parts[0] + ":" + parts[1]);
                            if (id == null) {
                                LOGGER.error("Invalid resource location from path: {}", relativePath);
                                return;
                            }

                            ModularStructureConfig modularConfig = ModularConfigMigration.loadOrCreateConfig(
                                path, parts[0], parts[1]
                            );
                            
                            if (modularConfig != null) {
                                configCache.put(id, StructureConfig.fromModular(modularConfig));
                            } else {
                                LOGGER.error("Failed to parse config for {}", id);
                            }
                        } catch (Exception e) {
                            LOGGER.error("Unexpected error loading config {}: {}", path, e.getMessage());
                        }
                    });
        } catch (IOException e) {
            LOGGER.error("Error walking config directory: {}", e.getMessage());
        }

        configsLoaded = true;
        LOGGER.info("Loaded {} structure configs", configCache.size());
    }

    public boolean isReady() {
        return configsLoaded;
    }

    public StructureConfig getConfig(ResourceLocation id) {
        if (!configsLoaded) {
            loadConfigs();
        }
        return configCache.get(id);
    }

    public Map<ResourceLocation, StructureConfig> getAllConfigs() {
        return configCache;
    }
}