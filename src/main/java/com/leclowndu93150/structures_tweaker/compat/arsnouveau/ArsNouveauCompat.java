package com.leclowndu93150.structures_tweaker.compat.arsnouveau;

import com.leclowndu93150.structures_tweaker.compat.CompatAPI;
import net.neoforged.fml.ModList;

public class ArsNouveauCompat implements CompatAPI {
    public static final String MOD_ID = "ars_nouveau";
    
    @Override
    public String getModId() {
        return MOD_ID;
    }
    
    @Override
    public void initialize() {
    }
    
    @Override
    public boolean isModLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }
}
