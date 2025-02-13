package com.leclowndu93150.structures_tweaker.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.leclowndu93150.structures_tweaker.StructuresTweaker;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class StructureConfigManager {
    private static final Path CONFIG_DIR = Path.of("config", StructuresTweaker.MODID);
    private final Map<ResourceLocation, StructureConfig> configCache = new ConcurrentHashMap<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private volatile boolean configsLoaded = false;
    private static final Logger LOGGER = LogManager.getLogger();

    private final Map<String, Map<String, StructureConfig>> customModConfigs = new HashMap<>();

    public StructureConfigManager() {
        registerCustomModConfig("das", Map.of(
                "deep_fortress", createCustomConfig(false, true, false, true, true, true, true, true, false)
        ));
        registerCustomModConfig("das", Map.of(
                "deep_spawner_1", createCustomConfig(false, true, false, true, true, true, true, true, false)
        ));
        registerCustomModConfig("das", Map.of(
                "deep_spawner_2", createCustomConfig(false, true, false, true, true, true, true, true, false)
        ));
        registerCustomModConfig("das", Map.of(
                "deep_spawner_3", createCustomConfig(false, true, false, true, true, true, true, true, false)
        ));

        registerCustomModConfig("uas", Map.of(
                "carousel_spawner_1", createCustomConfig(false, true, false, true, true, true, true, true, false)
        ));
        registerCustomModConfig("uas", Map.of(
                "carousel_spawner_2", createCustomConfig(false, true, false, true, true, true, true, true, false)
        ));
        registerCustomModConfig("uas", Map.of(
                "carousel_spawner_3", createCustomConfig(false, true, false, true, true, true, true, true, false)
        ));
        registerCustomModConfig("uas", Map.of(
                "garden_fortress", createCustomConfig(false, true, false, true, true, true, true, true, false)
        ));

    }

    private StructureConfig createCustomConfig(boolean canBreakBlocks, boolean canInteract, boolean canPlaceBlocks,
                                               boolean allowPlayerPVP, boolean allowCreatureSpawning, boolean allowFireSpread,
                                               boolean allowExplosions, boolean allowItemPickup, boolean onlyProtectOriginalBlocks) {
        StructureConfig config = new StructureConfig();
        try {
            var fields = StructureConfig.class.getDeclaredFields();
            for (var field : fields) {
                field.setAccessible(true);
                switch (field.getName()) {
                    case "canBreakBlocks" -> field.set(config, canBreakBlocks);
                    case "canInteract" -> field.set(config, canInteract);
                    case "canPlaceBlocks" -> field.set(config, canPlaceBlocks);
                    case "allowPlayerPVP" -> field.set(config, allowPlayerPVP);
                    case "allowCreatureSpawning" -> field.set(config, allowCreatureSpawning);
                    case "allowFireSpread" -> field.set(config, allowFireSpread);
                    case "allowExplosions" -> field.set(config, allowExplosions);
                    case "allowItemPickup" -> field.set(config, allowItemPickup);
                    case "onlyProtectOriginalBlocks" -> field.set(config, onlyProtectOriginalBlocks);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to create custom config: {}", e.getMessage());
        }
        return config;
    }

    public void registerCustomModConfig(String modId, Map<String, StructureConfig> configs) {
        customModConfigs.put(modId, configs);
    }

    public void generateConfigs() {
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
                        StructureConfig config = getCustomConfigOrDefault(id);
                        String json = GSON.toJson(config);
                        Files.writeString(configPath, json);
                    }
                } catch (IOException e) {
                    LOGGER.error("Failed to generate config for {}: {}", id, e.getMessage());
                }
            });
        });
    }

    private StructureConfig getCustomConfigOrDefault(ResourceLocation id) {
        Map<String, StructureConfig> modConfigs = customModConfigs.get(id.getNamespace());
        if (modConfigs != null) {
            StructureConfig config = modConfigs.get(id.getPath());
            if (config != null) {
                return config;
            }
        }
        return new StructureConfig();
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