package com.leclowndu93150.structures_tweaker.config.presets;

import com.leclowndu93150.structures_tweaker.config.core.ModularStructureConfig;
import com.leclowndu93150.structures_tweaker.config.properties.ConfigProperty;
import com.leclowndu93150.structures_tweaker.config.properties.ConfigRegistry;
import java.util.HashMap;
import java.util.Map;

public class StructureConfigPresets {
    private static final Map<String, Map<String, Map<ConfigProperty<?>, Object>>> MOD_PRESETS = new HashMap<>();
    
    static {

        //Default presets for DAS and UAS structures by Veydiz

        Map<ConfigProperty<?>, Object> protectedSpawnerChanges = Map.of(
            ConfigRegistry.CAN_BREAK_BLOCKS, false,
            ConfigRegistry.CAN_PLACE_BLOCKS, false,
            ConfigRegistry.ALLOW_ELYTRA_FLIGHT, false,
            ConfigRegistry.ALLOW_ENDER_PEARLS, false,
            ConfigRegistry.ALLOW_RIPTIDE, false
        );
        
        registerPreset("das", "deep_fortress", protectedSpawnerChanges);
        registerPreset("das", "deep_spawner_1", protectedSpawnerChanges);
        registerPreset("das", "deep_spawner_2", protectedSpawnerChanges);
        registerPreset("das", "deep_spawner_3", protectedSpawnerChanges);
        
        registerPreset("uas", "carousel_spawner_1", protectedSpawnerChanges);
        registerPreset("uas", "carousel_spawner_2", protectedSpawnerChanges);
        registerPreset("uas", "carousel_spawner_3", protectedSpawnerChanges);
        registerPreset("uas", "garden_fortress", protectedSpawnerChanges);
    }
    
    /**
     * Register a preset by only specifying values that differ from defaults
     */
    public static void registerPreset(String modId, String structureId, Map<ConfigProperty<?>, Object> changes) {
        MOD_PRESETS.computeIfAbsent(modId, k -> new HashMap<>()).put(structureId, changes);
    }
    
    /**
     * Create a preset config with specified changes
     */
    public static ModularStructureConfig createPreset(Map<ConfigProperty<?>, Object> changes) {
        ModularStructureConfig config = new ModularStructureConfig();
        for (Map.Entry<ConfigProperty<?>, Object> entry : changes.entrySet()) {
            config.setValue(entry.getKey().getKey(), entry.getValue());
        }
        return config;
    }
    
    /**
     * Get a preset config for a specific mod and structure
     */
    public static ModularStructureConfig getPreset(String modId, String structureId) {
        Map<String, Map<ConfigProperty<?>, Object>> modPresets = MOD_PRESETS.get(modId);
        if (modPresets != null) {
            Map<ConfigProperty<?>, Object> changes = modPresets.get(structureId);
            if (changes != null) {
                return createPreset(changes);
            }
        }
        return null;
    }
    
    /**
     * Helper method to create a preset with a builder pattern
     */
    public static class PresetBuilder {
        private final Map<ConfigProperty<?>, Object> changes = new HashMap<>();
        
        public PresetBuilder with(ConfigProperty<?> property, Object value) {
            changes.put(property, value);
            return this;
        }
        
        public Map<ConfigProperty<?>, Object> build() {
            return new HashMap<>(changes);
        }
    }
}