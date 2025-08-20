package com.leclowndu93150.structures_tweaker.config.core;

import com.leclowndu93150.structures_tweaker.config.properties.ConfigProperty;
import com.leclowndu93150.structures_tweaker.config.properties.ConfigRegistry;
import java.util.HashMap;
import java.util.Map;

/**
 * Global configuration that all structures inherit from unless individually overridden
 */
public class GlobalStructureConfig extends ModularStructureConfig {
    
    public GlobalStructureConfig() {
        super();
    }
    
    public GlobalStructureConfig(Map<String, Object> values) {
        super(values);
    }
    
    /**
     * Create a structure config that inherits from this global config
     * Individual values override global ones
     */
    public ModularStructureConfig createInheritedConfig(Map<String, Object> individualValues) {
        Map<String, Object> merged = new HashMap<>();
        
        // Start with global values
        merged.putAll(this.getAllValues());
        
        // Override with individual values
        merged.putAll(individualValues);
        
        return new ModularStructureConfig(merged);
    }
    
    /**
     * Check if a property has been explicitly set (different from default)
     */
    public boolean isPropertyCustomized(ConfigProperty<?> property) {
        Object currentValue = getValue(property);
        Object defaultValue = property.getDefaultValue();
        
        if (currentValue == null && defaultValue == null) return false;
        if (currentValue == null || defaultValue == null) return true;
        
        return !currentValue.equals(defaultValue);
    }
    
    /**
     * Get all customized (non-default) properties
     */
    public Map<String, Object> getCustomizedProperties() {
        Map<String, Object> customized = new HashMap<>();
        
        for (ConfigProperty<?> property : ConfigRegistry.getAllProperties().values()) {
            if (isPropertyCustomized(property)) {
                customized.put(property.getKey(), getValue(property));
            }
        }
        
        return customized;
    }
}