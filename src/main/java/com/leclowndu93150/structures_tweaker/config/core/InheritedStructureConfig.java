package com.leclowndu93150.structures_tweaker.config.core;

import com.leclowndu93150.structures_tweaker.config.StructureConfig;
import com.leclowndu93150.structures_tweaker.config.properties.ConfigProperty;
import java.util.HashMap;
import java.util.Map;

/**
 * A structure config that inherits from global config but can have individual overrides
 */
public class InheritedStructureConfig extends StructureConfig {
    private final GlobalStructureConfig globalConfig;
    private final Map<String, Object> individualOverrides;
    
    public InheritedStructureConfig(GlobalStructureConfig globalConfig, Map<String, Object> individualOverrides) {
        super();
        this.globalConfig = globalConfig;
        this.individualOverrides = new HashMap<>(individualOverrides);

        Map<String, Object> merged = new HashMap<>();
        merged.putAll(globalConfig.getAllValues());
        merged.putAll(individualOverrides);

        for (var entry : merged.entrySet()) {
            this.setValue(entry.getKey(), entry.getValue());
        }
    }
    
    @Override
    public <T> T getValue(ConfigProperty<T> property) {
        if (individualOverrides.containsKey(property.getKey())) {
            Object value = individualOverrides.get(property.getKey());
            return property.convert(value);
        }

        return globalConfig.getValue(property);
    }
    
    public boolean isOverridden(ConfigProperty<?> property) {
        return individualOverrides.containsKey(property.getKey());
    }
    
    public Map<String, Object> getIndividualOverrides() {
        return new HashMap<>(individualOverrides);
    }
    
    public GlobalStructureConfig getGlobalConfig() {
        return globalConfig;
    }
}