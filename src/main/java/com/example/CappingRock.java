package com.example;

import java.awt.Color;
import net.runelite.api.coords.WorldPoint;

public class CappingRock {
    private final double TICK_RESPAWN_TIME = 25;
    private double ticksPassed;
    private final WorldPoint worldLocation;

    CappingRock(WorldPoint worldLocation) {
        this.worldLocation = worldLocation;
    }

    public Color getStateColor() {
        double ratio = ticksPassed / TICK_RESPAWN_TIME;
        if(ratio < 0.4) return new Color(255, 50, 0);
        else if(ratio < 0.8) return new Color(255, 187, 0);
        return new Color(0, 217, 0);
    }
    public void updateTimeRemaining() {
        ++ticksPassed;
    }

    public double getTimeRemaining()  {
        return Math.min(1, ticksPassed / TICK_RESPAWN_TIME);
    }
    public WorldPoint getWorldLocation() { return worldLocation; }
    public boolean isTimeExpired() { return ticksPassed >= TICK_RESPAWN_TIME; }
}
