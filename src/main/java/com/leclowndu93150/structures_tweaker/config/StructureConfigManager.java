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
                            Path parent = configPath.getParent();
                            Files.createDirectories(parent);

                            if (!Files.exists(configPath)) {
                                String json = GSON.toJson(new StructureConfig());
                                Files.writeString(configPath, json);
                            } else {
                            }
                        } catch (IOException e) {
                            LOGGER.error("Failed to generate config for {}: {}", id, e.getMessage());
                        }
                    }
                });
            });
        } else {
            LOGGER.error("Server instance is null during config generation!");
        }
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

                            // Parts[0] is namespace, parts[1] is path
                            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(parts[0], parts[1]);

                            String content = Files.readString(path);
                            StructureConfig config = GSON.fromJson(content, StructureConfig.class);
                            configCache.put(id, config);
                        } catch (IOException e) {
                            LOGGER.error("Error loading config {}: {}", path, e.getMessage());
                        }
                    });
        } catch (IOException e) {
            LOGGER.error("Error walking config directory: {}", e.getMessage());
        }

        configsLoaded = true;
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