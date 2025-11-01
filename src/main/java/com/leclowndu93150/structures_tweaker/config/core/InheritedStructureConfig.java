package com.leclowndu93150.structures_tweaker.config.core;

import com.leclowndu93150.structures_tweaker.config.properties.ConfigProperty;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
        
        for (var entry : individualOverrides.entrySet()) {
            Object globalValue = merged.get(entry.getKey());
            Object individualValue = entry.getValue();
            
            if (globalValue instanceof List && individualValue instanceof List) {
                List<Object> mergedList = new ArrayList<>();
                mergedList.addAll((List<?>) globalValue);
                mergedList.addAll((List<?>) individualValue);
                merged.put(entry.getKey(), mergedList);
            } else {
                merged.put(entry.getKey(), individualValue);
            }
        }

        for (var entry : merged.entrySet()) {
            this.setValue(entry.getKey(), entry.getValue());
        }
    }
    
    @Override
    public <T> T getValue(ConfigProperty<T> property) {
        Object globalValue = globalConfig.getRawValue(property.getKey());
        Object individualValue = individualOverrides.get(property.getKey());
        
        if (globalValue instanceof List && individualValue instanceof List) {
            List<Object> mergedList = new ArrayList<>();
            mergedList.addAll((List<?>) globalValue);
            mergedList.addAll((List<?>) individualValue);
            return property.convert(mergedList);
        }
        
        if (individualOverrides.containsKey(property.getKey())) {
            return property.convert(individualValue);
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