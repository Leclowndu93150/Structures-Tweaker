package com.leclowndu93150.structures_tweaker.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ConfigMigration {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final int CURRENT_VERSION = 2;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static void migrateConfigs(Path configDir) {
        try {
            if (!Files.exists(configDir)) {
                return;
            }

            Files.walk(configDir)
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".json"))
                    .forEach(ConfigMigration::migrateConfig);

        } catch (IOException e) {
            LOGGER.error("Error during config migration: {}", e.getMessage());
        }
    }

    private static void migrateConfig(Path configPath) {
        try {
            String content = Files.readString(configPath);
            JsonObject json = JsonParser.parseString(content).getAsJsonObject();

            // Check if version exists, if not it's version 1
            int version = json.has("configVersion") ? json.get("configVersion").getAsInt() : 1;

            if (version < CURRENT_VERSION) {
                JsonObject migrated = migrateConfigVersion(json, version);
                Files.writeString(configPath, GSON.toJson(migrated));
                LOGGER.info("Migrated config {} from version {} to {}", configPath, version, CURRENT_VERSION);
            }

        } catch (IOException e) {
            LOGGER.error("Failed to migrate config {}: {}", configPath, e.getMessage());
        }
    }

    private static JsonObject migrateConfigVersion(JsonObject json, int fromVersion) {
        switch (fromVersion) {
            case 1:
                // Migrate from version 1 to 2
                json.addProperty("allowElytraFlight", true);
                json.addProperty("allowEnderPearls", true);
                json.addProperty("allowRiptide", true);
                json.addProperty("configVersion", 2);
                break;
        }

        return json;
    }

    public static class ConfigWrapper {
        private final int configVersion = CURRENT_VERSION;
        private final StructureConfig config;

        public ConfigWrapper(StructureConfig config) {
            this.config = config;
        }
    }
}
