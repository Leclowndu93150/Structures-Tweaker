package com.leclowndu93150.structures_tweaker.config;

public class StructureConfig {
    private boolean canBreakBlocks;
    private boolean canInteract;
    private boolean canPlaceBlocks;
    private boolean allowPlayerPVP;
    private boolean allowCreatureSpawning;
    private boolean allowFireSpread;
    private boolean allowExplosions;
    private boolean allowItemPickup;
    private boolean onlyProtectOriginalBlocks;
    private boolean allowElytraFlight;
    private boolean allowEnderPearls;
    private boolean allowRiptide;

    public StructureConfig() {
        this.canBreakBlocks = true;
        this.canInteract = true;
        this.canPlaceBlocks = true;
        this.allowPlayerPVP = true;
        this.allowCreatureSpawning = true;
        this.allowFireSpread = true;
        this.allowExplosions = true;
        this.allowItemPickup = true;
        this.onlyProtectOriginalBlocks = false;
        this.allowElytraFlight = true;
        this.allowEnderPearls = true;
        this.allowRiptide = true;
    }

    public boolean canBreakBlocks() { return canBreakBlocks; }
    public boolean canInteract() { return canInteract; }
    public boolean canPlaceBlocks() { return canPlaceBlocks; }
    public boolean allowPlayerPVP() { return allowPlayerPVP; }
    public boolean allowCreatureSpawning() { return allowCreatureSpawning; }
    public boolean allowFireSpread() { return allowFireSpread; }
    public boolean allowExplosions() { return allowExplosions; }
    public boolean allowItemPickup() { return allowItemPickup; }
    public boolean onlyProtectOriginalBlocks() { return onlyProtectOriginalBlocks; }
    public boolean allowElytraFlight() { return allowElytraFlight; }
    public boolean allowEnderPearls() { return allowEnderPearls; }
    public boolean allowRiptide() { return allowRiptide; }
}

