package com.leclowndu93150.structures_tweaker.config.presets;

import com.leclowndu93150.structures_tweaker.config.core.ModularStructureConfig;
import com.leclowndu93150.structures_tweaker.config.properties.ConfigProperty;
import com.leclowndu93150.structures_tweaker.config.properties.ConfigRegistry;

import java.util.HashMap;
import java.util.Map;

public class StructureConfigPresets {
    private static final Map<String, Map<String, Map<ConfigProperty<?>, Object>>> MOD_PRESETS = new HashMap<>();
    
    static {

        Map<ConfigProperty<?>, Object> protectedSpawnerChanges = new HashMap<>();
        protectedSpawnerChanges.put(ConfigRegistry.CAN_BREAK_BLOCKS, false);           // was false
        protectedSpawnerChanges.put(ConfigRegistry.CAN_INTERACT, true);               // was true  
        protectedSpawnerChanges.put(ConfigRegistry.CAN_PLACE_BLOCKS, false);          // was false
        protectedSpawnerChanges.put(ConfigRegistry.ALLOW_PLAYER_PVP, true);           // was true
        protectedSpawnerChanges.put(ConfigRegistry.ALLOW_CREATURE_SPAWNING, true);    // was true
        protectedSpawnerChanges.put(ConfigRegistry.ALLOW_FIRE_SPREAD, true);          // was true
        protectedSpawnerChanges.put(ConfigRegistry.ALLOW_EXPLOSIONS, true);           // was true
        protectedSpawnerChanges.put(ConfigRegistry.ALLOW_ITEM_PICKUP, true);          // was true
        protectedSpawnerChanges.put(ConfigRegistry.ONLY_PROTECT_ORIGINAL_BLOCKS, false); // was false
        protectedSpawnerChanges.put(ConfigRegistry.ALLOW_ELYTRA_FLIGHT, false);       // was false
        protectedSpawnerChanges.put(ConfigRegistry.ALLOW_ENDER_PEARLS, false);        // was false
        protectedSpawnerChanges.put(ConfigRegistry.ALLOW_RIPTIDE, false);             // was false
        
        registerPreset("das", "deep_fortress", protectedSpawnerChanges);
        registerPreset("das", "deep_spawner_1", protectedSpawnerChanges);
        registerPreset("das", "deep_spawner_2", protectedSpawnerChanges);
        registerPreset("das", "deep_spawner_3", protectedSpawnerChanges);
        registerPreset("das", "deep_spawner_4", protectedSpawnerChanges);
        registerPreset("undergarden_structures", "spawner_1", protectedSpawnerChanges);
        registerPreset("undergarden_structures", "spawner_2", protectedSpawnerChanges);
        registerPreset("undergarden_structures", "spawner_3", protectedSpawnerChanges);
        registerPreset("undergarden_structures", "spawner_4", protectedSpawnerChanges);
    }
    
    /**
     * Register a preset by only specifying values that differ from defaults
     */
    public static void registerPreset(String modId, String structureId, Map<ConfigProperty<?>, Object> changes) {
        Map<ConfigProperty<?>, Object> changesCopy = new HashMap<>(changes);
        MOD_PRESETS.computeIfAbsent(modId, k -> new HashMap<>()).put(structureId, changesCopy);
    }
    
    /**
     * Create a config with specified changes applied to defaults
     */
    private static ModularStructureConfig createPreset(Map<ConfigProperty<?>, Object> changes) {
        ModularStructureConfig config = new ModularStructureConfig();
        for (Map.Entry<ConfigProperty<?>, Object> entry : changes.entrySet()) {
            config.setValue(entry.getKey().getKey(), entry.getValue());
        }
        return config;
    }
    
    /**
     * Get a preset config for a specific mod and structure
     * Returns a config with only the preset values, designed to work with global inheritance
     */
    public static ModularStructureConfig getPreset(String modId, String structureId) {
        Map<String, Map<ConfigProperty<?>, Object>> modPresets = MOD_PRESETS.get(modId);
        if (modPresets != null) {
            Map<ConfigProperty<?>, Object> changes = modPresets.get(structureId);
            if (changes != null) {
                ModularStructureConfig config = new ModularStructureConfig();
                for (Map.Entry<ConfigProperty<?>, Object> entry : changes.entrySet()) {
                    config.setValue(entry.getKey().getKey(), entry.getValue());
                }
                return config;
            }
        }
        return null;
    }
    
    /**
     * Get just the preset changes (not a full config)
     */
    public static Map<ConfigProperty<?>, Object> getPresetChanges(String modId, String structureId) {
        Map<String, Map<ConfigProperty<?>, Object>> modPresets = MOD_PRESETS.get(modId);
        if (modPresets != null) {
            return modPresets.get(structureId);
        }
        return null;
    }
    
    /**
     * Helper method to create a preset with a builder pattern
     */
    public static PresetBuilder builder() {
        return new PresetBuilder();
    }
    
    public static class PresetBuilder {
        private final Map<ConfigProperty<?>, Object> changes = new HashMap<>();
        
        public <T> PresetBuilder set(ConfigProperty<T> property, T value) {
            changes.put(property, value);
            return this;
        }
        
        public ModularStructureConfig build() {
            return createPreset(changes);
        }
        
        public void register(String modId, String structureId) {
            registerPreset(modId, structureId, changes);
        }
    }
}