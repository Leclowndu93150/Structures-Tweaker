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

    private static final org.apache.logging.log4j.Logger LOGGER = org.apache.logging.log4j.LogManager.getLogger();

    public void generateConfigs() {
        try {
            LOGGER.debug("Generating missing configs at {}", CONFIG_DIR);
            Files.createDirectories(CONFIG_DIR);

            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) {
                LOGGER.error("Server is null during config generation");
                return;
            }

            server.registryAccess().registry(Registries.STRUCTURE).ifPresent(registry -> {
                registry.forEach(structure -> {
                    ResourceLocation id = registry.getKey(structure);
                    if (id != null) {
                        Path configPath = CONFIG_DIR.resolve(id.toString().replace(':', '/') + ".json");

                        if (!configPath.toFile().exists()) {
                            LOGGER.debug("Generating new config for {}", id);
                            try {
                                Files.createDirectories(configPath.getParent());
                                StructureConfig config = loadModDefault(id);
                                try (FileWriter writer = new FileWriter(configPath.toFile())) {
                                    GSON.toJson(config, writer);
                                }
                            } catch (IOException e) {
                                LOGGER.error("Failed to generate config for {}", id, e);
                            }
                        } else {
                            LOGGER.debug("Config already exists for {}", id);
                        }
                    }
                });
            });
        } catch (IOException e) {
            LOGGER.error("Failed to create config directory", e);
        }
    }

    private StructureConfig loadModDefault(ResourceLocation id) {
        String path = "data/" + id.getNamespace() + "/structure_tweaker/" + id.getPath() + ".json";

        var optional = ModList.get().getModContainerById(id.getNamespace());
        if (optional.isPresent()) {
            try (InputStream is = optional.get().getModInfo().getClass().getClassLoader().getResourceAsStream(path)) {
                if (is != null) {
                    return GSON.fromJson(new InputStreamReader(is), StructureConfig.class);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return new StructureConfig();
    }


    public boolean isReady() {
        return configsLoaded;
    }

    public void loadConfigs() {
        LOGGER.debug("Loading configs from {}", CONFIG_DIR);
        configCache.clear();

        try {
            Files.walk(CONFIG_DIR)
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".json"))
                    .forEach(path -> {
                        try {
                            String relativePath = CONFIG_DIR.relativize(path).toString().replace('\\', '/');
                            String structureId = relativePath.substring(0, relativePath.length() - 5);
                            ResourceLocation id = ResourceLocation.tryParse(structureId);

                            LOGGER.debug("Loading config for structure: {} from {}", id, path);
                            StructureConfig config = GSON.fromJson(Files.readString(path), StructureConfig.class);
                            configCache.put(id, config);
                            LOGGER.debug("Loaded config: {}", config);
                        } catch (IOException e) {
                            LOGGER.error("Failed to load config from {}", path, e);
                        }
                    });

            LOGGER.debug("Loaded {} structure configs", configCache.size());
        } catch (IOException e) {
            LOGGER.error("Failed to load configs", e);
        }
        configsLoaded = true;
    }

    public Map<ResourceLocation, StructureConfig> getAllConfigs() {
        return configCache;
    }

    public StructureConfig getConfig(ResourceLocation id) {
        return configCache.get(id);
    }

    @Override
    public String toString() {
        return "StructureConfigManager{" +
                "configCache=" + configCache +
                ", configsLoaded=" + configsLoaded +
                '}';
    }
}
