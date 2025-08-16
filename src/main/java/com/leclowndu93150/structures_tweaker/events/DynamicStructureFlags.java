package com.leclowndu93150.structures_tweaker.events;

import com.leclowndu93150.structures_tweaker.config.core.StructureConfig;
import com.leclowndu93150.structures_tweaker.config.properties.ConfigProperty;
import com.leclowndu93150.structures_tweaker.config.properties.ConfigRegistry;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Dynamic structure flags that automatically work with any registered properties
 */
public class DynamicStructureFlags {
    private final Map<String, Object> flags;
    
    public DynamicStructureFlags(StructureConfig config) {
        this.flags = new HashMap<>();
        for (ConfigProperty<?> property : ConfigRegistry.getAllProperties().values()) {
            flags.put(property.getKey(), config.getValue(property));
        }
    }
    
    @SuppressWarnings("unchecked")
    public <T> T getValue(ConfigProperty<T> property) {
        Object value = flags.get(property.getKey());
        if (value == null) {
            return property.getDefaultValue();
        }
        return property.convert(value);
    }
    
    // Convenience methods for common checks
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
    public boolean allowCreativeFlight() { return getValue(ConfigRegistry.ALLOW_CREATIVE_FLIGHT); }
    public boolean allowEnderTeleportation() { return getValue(ConfigRegistry.ALLOW_ENDER_TELEPORTATION); }
    public boolean preventHostileSpawns() { return getValue(ConfigRegistry.PREVENT_HOSTILE_SPAWNS); }
    public boolean preventPassiveSpawns() { return getValue(ConfigRegistry.PREVENT_PASSIVE_SPAWNS); }
    
    @SuppressWarnings("unchecked")
    public List<String> getInteractionWhitelist() { 
        return getValue(ConfigRegistry.INTERACTION_WHITELIST); 
    }
    
    @SuppressWarnings("unchecked")
    public List<String> getInteractionBlacklist() { 
        return getValue(ConfigRegistry.INTERACTION_BLACKLIST); 
    }
    
    @SuppressWarnings("unchecked")
    public List<String> getItemUseBlacklist() { 
        return getValue(ConfigRegistry.ITEM_USE_BLACKLIST); 
    }
    
    @SuppressWarnings("unchecked")
    public List<String> getItemUseWhitelist() { 
        return getValue(ConfigRegistry.ITEM_USE_WHITELIST); 
    }
    
    public Map<String, Object> getAllFlags() {
        return new HashMap<>(flags);
    }
}