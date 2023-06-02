package com.ultimatevm;

import net.runelite.api.coords.WorldPoint;
import java.awt.Color;

public class PlayerPlatform extends TimedObject {
    private final double TICK_DESPAWN_TIME = 150;

    PlayerPlatform(WorldPoint worldLocation) {
        super(worldLocation, ObjectType.PLATFORM);
    }

    @Override
    public Color getStateColor() {
        double ticksLeft = getTimeLeft();
        if(ticksLeft < 10) return new Color(255, 50, 0);
        else if(ticksLeft < 75) return new Color(255, 187, 0);
        return new Color(0, 217, 0);
    }

    @Override
    public double getTimeLeft() { return TICK_DESPAWN_TIME - ticksPassed; }
    @Override
    public double getTimeRemaining()  {
        return Math.min(1, 1 - ticksPassed / TICK_DESPAWN_TIME);
    }
    @Override
    public boolean isTimeExpired() { return ticksPassed >= TICK_DESPAWN_TIME; }
}
