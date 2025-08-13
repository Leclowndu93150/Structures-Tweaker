package com.leclowndu93150.structures_tweaker.config.migration;

import com.leclowndu93150.structures_tweaker.config.core.ModularStructureConfig;
import com.leclowndu93150.structures_tweaker.config.properties.ConfigRegistry;
import com.leclowndu93150.structures_tweaker.config.presets.StructureConfigPresets;
import com.google.gson.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ModularConfigMigration {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    public static class ConfigWrapper {
        private final List<String> registeredProperties;
        private final JsonObject config;
        
        public ConfigWrapper(ModularStructureConfig config) {
            this.registeredProperties = ConfigRegistry.getPropertyKeys();
            this.config = config.toJson();
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
            String content = Files.readString(configPath);
            JsonObject json = JsonParser.parseString(content).getAsJsonObject();
            
            JsonObject configObject = extractConfigObject(json);
            List<String> existingProperties = new ArrayList<>();
            
            if (json.has("registeredProperties") && json.get("registeredProperties").isJsonArray()) {
                JsonArray props = json.getAsJsonArray("registeredProperties");
                for (JsonElement prop : props) {
                    existingProperties.add(prop.getAsString());
                }
            }
            
            ModularStructureConfig config = ModularStructureConfig.fromJson(configObject);
            
            List<String> currentProperties = ConfigRegistry.getPropertyKeys();
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
            
            // Find obsolete properties (in config but not registered anymore)
            for (String key : configObject.keySet()) {
                if (!currentProperties.contains(key)) {
                    obsoleteProperties.add(key);
                    needsUpdate = true;
                }
            }
            
            if (needsUpdate) {
                config.applyMissingDefaults();
                ConfigWrapper wrapper = new ConfigWrapper(config);
                Files.writeString(configPath, GSON.toJson(wrapper));
                
                if (!newProperties.isEmpty()) {
                    LOGGER.info("Migrated config {} - added new properties: {}", configPath, newProperties);
                }
                if (!obsoleteProperties.isEmpty()) {
                    LOGGER.info("Cleaned config {} - removed obsolete properties: {}", configPath, obsoleteProperties);
                }
            }
            
        } catch (IOException e) {
            LOGGER.error("Failed to migrate config {}: {}", configPath, e.getMessage());
        } catch (JsonSyntaxException e) {
            LOGGER.error("Invalid JSON in config {}: {}", configPath, e.getMessage());
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
                JsonObject config = configElement.getAsJsonObject();
                if (config.has("canBreakBlocks") || config.has("canInteract") || 
                    config.has("allowItemPickup") || config.has("allowElytraFlight")) {
                    return config;
                }
            }
        }

        if (json.has("configVersion")) {
            JsonObject result = new JsonObject();
            for (var entry : json.entrySet()) {
                String key = entry.getKey();
                if (!key.equals("configVersion") && !key.equals("config") && !key.equals("registeredProperties")) {
                    result.add(key, entry.getValue());
                }
            }
            return result;
        }

        return json;
    }
}