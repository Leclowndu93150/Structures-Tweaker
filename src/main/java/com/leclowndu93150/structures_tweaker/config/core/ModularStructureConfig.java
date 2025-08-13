package com.leclowndu93150.structures_tweaker.config.core;

import com.leclowndu93150.structures_tweaker.config.properties.ConfigProperty;
import com.leclowndu93150.structures_tweaker.config.properties.ConfigRegistry;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.Map;

public class ModularStructureConfig {
    private final Map<String, Object> properties = new HashMap<>();
    
    public ModularStructureConfig() {
        for (ConfigProperty<?> property : ConfigRegistry.getAllProperties().values()) {
            properties.put(property.getKey(), property.getDefaultValue());
        }
    }
    
    public ModularStructureConfig(Map<String, Object> initialValues) {
        this();
        properties.putAll(initialValues);
    }
    
    @SuppressWarnings("unchecked")
    public <T> T getValue(ConfigProperty<T> property) {
        Object value = properties.get(property.getKey());
        if (value == null) {
            return property.getDefaultValue();
        }
        return property.convert(value);
    }
    
    public <T> void setValue(ConfigProperty<T> property, T value) {
        properties.put(property.getKey(), value);
    }
    
    public void setValue(String key, Object value) {
        properties.put(key, value);
    }
    
    public Map<String, Object> getAllValues() {
        return new HashMap<>(properties);
    }
    
    public void applyMissingDefaults() {
        for (ConfigProperty<?> property : ConfigRegistry.getAllProperties().values()) {
            if (!properties.containsKey(property.getKey())) {
                properties.put(property.getKey(), property.getDefaultValue());
            }
        }
    }
    
    public static ModularStructureConfig fromJson(JsonObject json) {
        ModularStructureConfig config = new ModularStructureConfig();
        
        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            String key = entry.getKey();
            JsonElement value = entry.getValue();
            ConfigProperty<?> property = ConfigRegistry.getProperty(key);
            
            if (property != null) {
                if (property.getType() == Boolean.class && value.isJsonPrimitive()) {
                    config.setValue(key, value.getAsBoolean());
                } else if (property.getType() == Integer.class && value.isJsonPrimitive()) {
                    config.setValue(key, value.getAsInt());
                } else if (property.getType() == String.class && value.isJsonPrimitive()) {
                    config.setValue(key, value.getAsString());
                }
            }
        }
        
        config.applyMissingDefaults();
        return config;
    }
    
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Boolean) {
                json.addProperty(entry.getKey(), (Boolean) value);
            } else if (value instanceof Number) {
                json.addProperty(entry.getKey(), (Number) value);
            } else if (value instanceof String) {
                json.addProperty(entry.getKey(), (String) value);
            }
        }
        return json;
    }
    
    public boolean canBreakBlocks() { return getValue(ConfigRegistry.CAN_BREAK_BLOCKS); }
    public boolean canInteract() { return getValue(ConfigRegistry.CAN_INTERACT); }
    public boolean canPlaceBlocks() { return getValue(ConfigRegistry.CAN_PLACE_BLOCKS); }
    public boolean allowPlayerPVP() { return getValue(ConfigRegistry.ALLOW_PLAYER_PVP); }
    public boolean allowCreatureSpawning() { return getValue(ConfigRegistry.ALLOW_CREATURE_SPAWNING); }
    public boolean allowFireSpread() { return getValue(ConfigRegistry.ALLOW_FIRE_SPREAD); }
    public boolean allowExplosions() { return getValue(ConfigRegistry.ALLOW_EXPLOSIONS); }
    public boolean allowItemPickup() { return getValue(ConfigRegistry.ALLOW_ITEM_PICKUP); }
    public boolean onlyProtectOriginalBlocks() { return getValue(ConfigRegistry.ONLY_PROTECT_ORIGINAL_BLOCKS); }
    public boolean allowElytraFlight() { return getValue(ConfigRegistry.ALLOW_ELYTRA_FLIGHT); }
    public boolean allowEnderPearls() { return getValue(ConfigRegistry.ALLOW_ENDER_PEARLS); }
    public boolean allowRiptide() { return getValue(ConfigRegistry.ALLOW_RIPTIDE); }
}