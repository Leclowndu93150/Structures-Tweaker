package com.leclowndu93150.structures_tweaker.config.core;

public class StructureConfig extends ModularStructureConfig {
    
    public StructureConfig() {
        super();
    }
    
    public static StructureConfig fromModular(ModularStructureConfig modular) {
        StructureConfig config = new StructureConfig();
        for (var entry : modular.getAllValues().entrySet()) {
            config.setValue(entry.getKey(), entry.getValue());
        }
        return config;
    }
}