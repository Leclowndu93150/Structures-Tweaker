package com.leclowndu93150.structures_tweaker.config.core;

import com.leclowndu93150.structures_tweaker.config.migration.ModularConfigMigration;
import com.leclowndu93150.structures_tweaker.StructuresTweaker;
import com.google.gson.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.server.ServerLifecycleHooks;
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
    private static final Path GLOBAL_CONFIG_PATH = CONFIG_DIR.resolve("global.json");
    private final Map<ResourceLocation, StructureConfig> configCache = new ConcurrentHashMap<>();
    private GlobalStructureConfig globalConfig;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private volatile boolean configsLoaded = false;
    private static final Logger LOGGER = LogManager.getLogger();

    public StructureConfigManager() {
    }

    public void generateConfigs() {
        loadOrCreateGlobalConfig();

        ConfigDocumentationGenerator.generateReadmeAndDocumentation(CONFIG_DIR);
        
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
                        ModularStructureConfig individualConfig = ModularConfigMigration.loadOrCreateConfig(
                            configPath, id.getNamespace(), id.getPath()
                        );

                        IndividualConfigWrapper wrapper = new IndividualConfigWrapper(
                            individualConfig, globalConfig
                        );
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
        
        // Load global config first
        loadOrCreateGlobalConfig();

        try {
            Files.walk(CONFIG_DIR)
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".json"))
                    .filter(path -> !path.equals(GLOBAL_CONFIG_PATH)) // Skip global config
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

                            // Load individual config and merge with global
                            ModularStructureConfig individualConfig = ModularConfigMigration.loadOrCreateConfig(
                                path, parts[0], parts[1]
                            );
                            
                            if (individualConfig != null) {
                                // Create inherited config that combines global + individual
                                // Use only explicitly set values, not defaults
                                InheritedStructureConfig inherited = new InheritedStructureConfig(
                                    globalConfig, individualConfig.getExplicitlySetValues()
                                );
                                configCache.put(id, inherited);
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
        LOGGER.info("Loaded global config and {} structure configs", configCache.size());
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
    
    public GlobalStructureConfig getGlobalConfig() {
        return globalConfig;
    }
    
    public boolean setConfigValue(ResourceLocation structureId, String propertyKey, Object value) {
        Path configPath = CONFIG_DIR.resolve(structureId.getNamespace() + "/" + structureId.getPath() + ".json");
        
        try {
            Files.createDirectories(configPath.getParent());
            
            ModularStructureConfig individualConfig;
            if (Files.exists(configPath)) {
                String content = Files.readString(configPath);
                JsonObject json = JsonParser.parseString(content).getAsJsonObject();
                
                JsonObject configObject;
                if (json.has("individualOverrides") && json.get("individualOverrides").isJsonObject()) {
                    configObject = json.getAsJsonObject("individualOverrides");
                } else if (json.has("config") && json.get("config").isJsonObject()) {
                    configObject = json.getAsJsonObject("config");
                } else {
                    configObject = new JsonObject();
                }
                
                individualConfig = ModularStructureConfig.fromJson(configObject, false);
            } else {
                individualConfig = new ModularStructureConfig();
            }
            
            individualConfig.setValue(propertyKey, value);
            
            IndividualConfigWrapper wrapper = new IndividualConfigWrapper(individualConfig, globalConfig);
            String json = GSON.toJson(wrapper);
            Files.writeString(configPath, json);
            
            InheritedStructureConfig inherited = new InheritedStructureConfig(
                globalConfig, individualConfig.getExplicitlySetValues()
            );
            configCache.put(structureId, inherited);
            
            if (configUpdateListener != null) {
                configUpdateListener.onConfigUpdate(structureId, inherited);
            }
            
            LOGGER.info("Updated config for {}: {} = {}", structureId, propertyKey, value);
            return true;
            
        } catch (Exception e) {
            LOGGER.error("Failed to set config value for {}: {}", structureId, e.getMessage());
            return false;
        }
    }
    
    private ConfigUpdateListener configUpdateListener;
    
    public void setConfigUpdateListener(ConfigUpdateListener listener) {
        this.configUpdateListener = listener;
    }
    
    public interface ConfigUpdateListener {
        void onConfigUpdate(ResourceLocation structureId, StructureConfig config);
    }
    
    private void loadOrCreateGlobalConfig() {
        try {
            Files.createDirectories(CONFIG_DIR);
            
            if (Files.exists(GLOBAL_CONFIG_PATH)) {
                String content = Files.readString(GLOBAL_CONFIG_PATH);
                JsonObject json = JsonParser.parseString(content).getAsJsonObject();
                
                JsonObject configObject;
                if (json.has("config") && json.get("config").isJsonObject()) {
                    configObject = json.getAsJsonObject("config");
                } else {
                    configObject = json;
                }
                
                ModularStructureConfig modular = ModularStructureConfig.fromJson(configObject);
                globalConfig = new GlobalStructureConfig(modular.getAllValues());
                
            } else {
                globalConfig = new GlobalStructureConfig();

                ModularConfigMigration.ConfigWrapper wrapper = new ModularConfigMigration.ConfigWrapper(globalConfig);
                Files.writeString(GLOBAL_CONFIG_PATH, GSON.toJson(wrapper));
                LOGGER.info("Created default global config at {}", GLOBAL_CONFIG_PATH);
            }
            
        } catch (IOException e) {
            LOGGER.error("Failed to load/create global config: {}", e.getMessage());
            globalConfig = new GlobalStructureConfig();
        }
    }
    
    public static class IndividualConfigWrapper {
        private final Map<String, Object> individualOverrides;
        private final String note;
        
        public IndividualConfigWrapper(ModularStructureConfig individual, GlobalStructureConfig global) {
            this.individualOverrides = new HashMap<>();
            this.note = "Only properties that differ from global config are saved here. See global.json for defaults.";
            
            for (var entry : individual.getExplicitlySetValues().entrySet()) {
                individualOverrides.put(entry.getKey(), entry.getValue());
            }
        }
    }
}