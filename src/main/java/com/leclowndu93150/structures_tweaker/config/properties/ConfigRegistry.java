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
    
    public static final ConfigProperty<Boolean> PREVENT_HOSTILE_SPAWNS = register(
        "preventHostileSpawns", false, Boolean.class, "Prevent hostile mob spawning in structure"
    );
    
    public static final ConfigProperty<Boolean> PREVENT_PASSIVE_SPAWNS = register(
        "preventPassiveSpawns", false, Boolean.class, "Prevent passive mob spawning in structure"
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
    
    public static final ConfigProperty<Boolean> ALLOW_CREATIVE_FLIGHT = register(
        "allowCreativeFlight", true, Boolean.class, "Allow creative flight in structure"
    );
    
    public static final ConfigProperty<Boolean> ALLOW_ENDER_TELEPORTATION = register(
        "allowEnderTeleportation", true, Boolean.class, "Allow all ender-based teleportation (pearls, chorus fruit) in structure"
    );
    
    @SuppressWarnings("unchecked")
    public static final ConfigProperty<List<String>> INTERACTION_WHITELIST = registerList(
        "interactionWhitelist", new ArrayList<>(), "Blocks that can always be interacted with (e.g., minecraft:lever, minecraft:button)"
    );
    
    @SuppressWarnings("unchecked")
    public static final ConfigProperty<List<String>> INTERACTION_BLACKLIST = registerList(
        "interactionBlacklist", new ArrayList<>(), "Blocks that can never be interacted with (e.g., minecraft:repeater, minecraft:comparator)"
    );
    
    @SuppressWarnings("unchecked")
    public static final ConfigProperty<List<String>> ITEM_USE_BLACKLIST = registerList(
        "itemUseBlacklist", new ArrayList<>(), "Items that cannot be used in the structure (e.g., minecraft:boat, minecraft:water_bucket)"
    );
    
    @SuppressWarnings("unchecked")
    public static final ConfigProperty<List<String>> ITEM_USE_WHITELIST = registerList(
        "itemUseWhitelist", new ArrayList<>(), "Items that can always be used in the structure, overrides blacklist"
    );
    
    public static <T> ConfigProperty<T> register(String key, T defaultValue, Class<T> type, String description) {
        ConfigProperty<T> property = new ConfigProperty<>(key, defaultValue, type, description);
        PROPERTIES.put(key, property);
        REGISTRATIONS.add(new ConfigPropertyRegistration(key, System.currentTimeMillis()));
        return property;
    }
    
    @SuppressWarnings("unchecked")
    public static ConfigProperty<List<String>> registerList(String key, List<String> defaultValue, String description) {
        ConfigProperty<List<String>> property = new ConfigProperty<>(key, defaultValue, (Class<List<String>>)(Class<?>)List.class, description);
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