package com.ultimatevm;

import java.awt.Color;
import net.runelite.api.coords.WorldPoint;

public class CappingRock extends TimedObject {
    private final double TICK_RESPAWN_TIME = 25;

    CappingRock(WorldPoint worldLocation) {
        super(worldLocation, ObjectType.ROCK);
    }

    @Override
    public Color getStateColor() {
        double ratio = ticksPassed / TICK_RESPAWN_TIME;
        if(ratio < 0.4) return new Color(255, 50, 0);
        else if(ratio < 0.8) return new Color(255, 187, 0);
        return new Color(0, 217, 0);
    }

    @Override
    public double getTimeLeft() { return TICK_RESPAWN_TIME - ticksPassed; }
    @Override
    public double getTimeRemaining()  {
        return Math.min(1, ticksPassed / TICK_RESPAWN_TIME);
    }
    @Override
    public boolean isTimeExpired() { return ticksPassed >= TICK_RESPAWN_TIME; }
}
