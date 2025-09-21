package com.leclowndu93150.structures_tweaker.config.migration;

import com.leclowndu93150.structures_tweaker.config.core.ModularStructureConfig;
import com.leclowndu93150.structures_tweaker.config.presets.StructureConfigPresets;
import com.leclowndu93150.structures_tweaker.config.properties.ConfigProperty;
import com.leclowndu93150.structures_tweaker.config.properties.ConfigRegistry;
import com.google.gson.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ModularConfigMigration {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    public static class ConfigWrapper {
        private final Object config;
        private final String note;
        
        public ConfigWrapper(ModularStructureConfig config) {
            this.config = config.toJson();
            this.note = "Configuration for structure behavior modification";
        }
    }
    
    public static void migrateAllConfigs(Path configDir) {
        try {
            if (!Files.exists(configDir)) {
                return;
            }

            Path globalConfigPath = configDir.resolve("global.json");
            if (Files.exists(globalConfigPath)) {
                migrateConfig(globalConfigPath);
            }
        } catch (Exception e) {
            LOGGER.error("Error during config migration: {}", e.getMessage());
        }
    }
    
    public static void migrateConfig(Path configPath) {
        try {
            if (!Files.exists(configPath)) {
                return;
            }
            
            String content = Files.readString(configPath);
            JsonObject json = JsonParser.parseString(content).getAsJsonObject();
            JsonObject configObject = extractConfigObject(json);
            
            Set<String> currentProperties = new HashSet<>();
            for (ConfigProperty<?> prop : ConfigRegistry.getAllProperties().values()) {
                currentProperties.add(prop.getKey());
            }
            
            Set<String> existingProperties = configObject.keySet();
            
            List<String> newProperties = new ArrayList<>();
            List<String> obsoleteProperties = new ArrayList<>();
            boolean needsUpdate = false;

            // Find new properties (either not in registeredProperties or not in config)
            for (String prop : currentProperties) {
                if (!existingProperties.contains(prop) || !configObject.has(prop)) {
                    newProperties.add(prop);
                    needsUpdate = true;
                }
            }
            
            // Find obsolete properties
            for (String prop : existingProperties) {
                if (!currentProperties.contains(prop)) {
                    obsoleteProperties.add(prop);
                    needsUpdate = true;
                }
            }
            
            if (needsUpdate) {
                LOGGER.info("Migrating config: {}", configPath.getFileName());
                if (!newProperties.isEmpty()) {
                    LOGGER.info("  Adding new properties: {}", newProperties);
                }
                if (!obsoleteProperties.isEmpty()) {
                    LOGGER.info("  Removing obsolete properties: {}", obsoleteProperties);
                }
                
                // Remove obsolete properties
                for (String prop : obsoleteProperties) {
                    configObject.remove(prop);
                }
                
                // Load as ModularStructureConfig to apply defaults
                ModularStructureConfig config = ModularStructureConfig.fromJson(configObject);
                
                // Save updated config
                ConfigWrapper wrapper = new ConfigWrapper(config);
                Files.writeString(configPath, GSON.toJson(wrapper));
            }
            
        } catch (IOException | JsonSyntaxException e) {
            LOGGER.error("Failed to migrate config {}: {}", configPath, e.getMessage());
        }
    }
    
    public static ModularStructureConfig loadOrCreateConfig(Path configPath, String modId, String structureId) {
        if (Files.exists(configPath)) {
            try {
                String content = Files.readString(configPath);
                JsonObject json = JsonParser.parseString(content).getAsJsonObject();

                if (json.has("individualOverrides") && json.get("individualOverrides").isJsonObject()) {
                    JsonObject overrides = json.getAsJsonObject("individualOverrides");
                    // Don't apply defaults for override configs - they inherit from global
                    return ModularStructureConfig.fromJson(overrides, false);
                }

                JsonObject configObject = extractConfigObject(json);
                ModularStructureConfig config = ModularStructureConfig.fromJson(configObject);
                return config;
                
            } catch (IOException | JsonSyntaxException e) {
                LOGGER.error("Failed to load config {}: {}", configPath, e.getMessage());
            }
        }

        ModularStructureConfig preset = StructureConfigPresets.getPreset(modId, structureId);
        if (preset != null) {
            return preset;
        }

        return new ModularStructureConfig();
    }
    
    private static JsonObject extractConfigObject(JsonObject json) {
        if (json.has("config")) {
            JsonElement configElement = json.get("config");
            if (configElement.isJsonObject()) {
                return configElement.getAsJsonObject();
            } else {
                LOGGER.warn("Config field is not a JSON object, using root object");
                return json;
            }
        }

        if (json.has("configVersion")) {
            JsonObject result = new JsonObject();
            for (var entry : json.entrySet()) {
                if (!entry.getKey().equals("configVersion") && !entry.getKey().equals("note")) {
                    result.add(entry.getKey(), entry.getValue());
                }
            }
            return result;
        }
        
        return json;
    }
}