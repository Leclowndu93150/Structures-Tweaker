package com.leclowndu93150.structures_tweaker.config;

public class StructureConfig {
    private boolean canBreakBlocks;
    private boolean canBreakStructureBlocks;
    private boolean canInteract;
    private boolean canPlaceBlocks;
    private float mobSpawnRate;
    private boolean allowMobSpawning;
    private boolean allowPlayerPVP;
    private boolean allowCreatureSpawning;
    private int regenerationTime; // in minutes, -1 for never
    private boolean protectArtifacts;
    private boolean allowFireSpread;
    private boolean allowExplosions;
    private boolean allowItemPickup;

    public StructureConfig() {
        this.canBreakBlocks = true;
        this.canBreakStructureBlocks = true;
        this.canInteract = true;
        this.canPlaceBlocks = true;
        this.mobSpawnRate = 1.0f;
        this.allowMobSpawning = true;
        this.allowPlayerPVP = true;
        this.allowCreatureSpawning = true;
        this.regenerationTime = -1;
        this.protectArtifacts = false;
        this.allowFireSpread = true;
        this.allowExplosions = true;
        this.allowItemPickup = true;
    }

    public boolean canBreakBlocks() { return canBreakBlocks; }
    public boolean canBreakStructureBlocks() { return canBreakStructureBlocks; }
    public boolean canInteract() { return canInteract; }
    public boolean canPlaceBlocks() { return canPlaceBlocks; }
    public float getMobSpawnRate() { return mobSpawnRate; }
    public boolean allowMobSpawning() { return allowMobSpawning; }
    public boolean allowPlayerPVP() { return allowPlayerPVP; }
    public boolean allowCreatureSpawning() { return allowCreatureSpawning; }
    public int getRegenerationTime() { return regenerationTime; }
    public boolean protectArtifacts() { return protectArtifacts; }
    public boolean allowFireSpread() { return allowFireSpread; }
    public boolean allowExplosions() { return allowExplosions; }
    public boolean allowItemPickup() { return allowItemPickup; }
}

