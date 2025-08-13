package com.leclowndu93150.structures_tweaker.config.properties;

import java.util.*;

public class ConfigRegistry {
    private static final Map<String, ConfigProperty<?>> PROPERTIES = new LinkedHashMap<>();
    private static final List<ConfigPropertyRegistration> REGISTRATIONS = new ArrayList<>();
    
    public static final ConfigProperty<Boolean> CAN_BREAK_BLOCKS = register(
        "canBreakBlocks", true, Boolean.class, "Allow breaking blocks in structure"
    );
    
    public static final ConfigProperty<Boolean> CAN_INTERACT = register(
        "canInteract", true, Boolean.class, "Allow interaction with blocks"
    );
    
    public static final ConfigProperty<Boolean> CAN_PLACE_BLOCKS = register(
        "canPlaceBlocks", true, Boolean.class, "Allow placing blocks in structure"
    );
    
    public static final ConfigProperty<Boolean> ALLOW_PLAYER_PVP = register(
        "allowPlayerPVP", true, Boolean.class, "Allow PVP in structure"
    );
    
    public static final ConfigProperty<Boolean> ALLOW_CREATURE_SPAWNING = register(
        "allowCreatureSpawning", true, Boolean.class, "Allow mob spawning in structure"
    );
    
    public static final ConfigProperty<Boolean> ALLOW_FIRE_SPREAD = register(
        "allowFireSpread", true, Boolean.class, "Allow fire to spread in structure"
    );
    
    public static final ConfigProperty<Boolean> ALLOW_EXPLOSIONS = register(
        "allowExplosions", true, Boolean.class, "Allow explosions in structure"
    );
    
    public static final ConfigProperty<Boolean> ALLOW_ITEM_PICKUP = register(
        "allowItemPickup", true, Boolean.class, "Allow picking up items in structure"
    );
    
    public static final ConfigProperty<Boolean> ONLY_PROTECT_ORIGINAL_BLOCKS = register(
        "onlyProtectOriginalBlocks", false, Boolean.class, "Only protect blocks that were part of the original structure"
    );
    
    public static final ConfigProperty<Boolean> ALLOW_ELYTRA_FLIGHT = register(
        "allowElytraFlight", true, Boolean.class, "Allow elytra flight in structure"
    );
    
    public static final ConfigProperty<Boolean> ALLOW_ENDER_PEARLS = register(
        "allowEnderPearls", true, Boolean.class, "Allow ender pearl usage in structure"
    );
    
    public static final ConfigProperty<Boolean> ALLOW_RIPTIDE = register(
        "allowRiptide", true, Boolean.class, "Allow riptide trident usage in structure"
    );
    
    public static <T> ConfigProperty<T> register(String key, T defaultValue, Class<T> type, String description) {
        ConfigProperty<T> property = new ConfigProperty<>(key, defaultValue, type, description);
        PROPERTIES.put(key, property);
        REGISTRATIONS.add(new ConfigPropertyRegistration(key, System.currentTimeMillis()));
        return property;
    }
    
    public static Map<String, ConfigProperty<?>> getAllProperties() {
        return Collections.unmodifiableMap(PROPERTIES);
    }
    
    public static ConfigProperty<?> getProperty(String key) {
        return PROPERTIES.get(key);
    }
    
    public static List<String> getPropertyKeys() {
        return new ArrayList<>(PROPERTIES.keySet());
    }
    
    public static List<ConfigPropertyRegistration> getRegistrationHistory() {
        return Collections.unmodifiableList(REGISTRATIONS);
    }
    
    public static class ConfigPropertyRegistration {
        public final String key;
        public final long timestamp;
        
        public ConfigPropertyRegistration(String key, long timestamp) {
            this.key = key;
            this.timestamp = timestamp;
        }
    }
}