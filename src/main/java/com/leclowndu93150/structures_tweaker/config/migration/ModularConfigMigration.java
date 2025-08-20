package com.leclowndu93150.structures_tweaker.config.migration;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.leclowndu93150.structures_tweaker.config.core.ModularStructureConfig;
import com.leclowndu93150.structures_tweaker.config.presets.StructureConfigPresets;
import com.leclowndu93150.structures_tweaker.config.properties.ConfigRegistry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class ModularConfigMigration {
    private static final Logger LOGGER = LogManager.getLogger();
    
    public static class ConfigWrapper {
        public final ModularStructureConfig config;
        
        public ConfigWrapper(ModularStructureConfig config) {
            this.config = config;
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
    
    private static void migrateConfig(Path configPath) {
        try {
            if (!Files.exists(configPath)) {
                return;
            }
            
            String content = Files.readString(configPath);
            JsonObject json = JsonParser.parseString(content).getAsJsonObject();
            JsonObject configObject = extractConfigObject(json);
            
            Set<String> currentProperties = ConfigRegistry.getAllProperties().keySet();
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
                LOGGER.info("Migrating config file: {}", configPath.getFileName());
                
                if (!newProperties.isEmpty()) {
                    LOGGER.info("Adding new properties: {}", newProperties);
                }
                
                if (!obsoleteProperties.isEmpty()) {
                    LOGGER.info("Removing obsolete properties: {}", obsoleteProperties);
                    for (String prop : obsoleteProperties) {
                        configObject.remove(prop);
                    }
                }
                
                ModularStructureConfig migratedConfig = ModularStructureConfig.fromJson(configObject);
                ConfigWrapper wrapper = new ConfigWrapper(migratedConfig);
                
                // Write back the migrated config (this will be done by the caller)
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
                LOGGER.warn("Config element is not a JSON object, using root object");
                return json;
            }
        }

        if (json.has("configVersion")) {
            JsonObject result = new JsonObject();
            for (var entry : json.entrySet()) {
                if (!entry.getKey().equals("configVersion") && 
                    !entry.getKey().equals("lastMigration") &&
                    !entry.getKey().equals("migratedProperties")) {
                    result.add(entry.getKey(), entry.getValue());
                }
            }
            return result;
        }
        
        return json;
    }
}