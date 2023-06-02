package com.ultimatevm;

import java.awt.Color;
import net.runelite.api.coords.WorldPoint;

public abstract class TimedObject {
    public enum ObjectType {
        ROCK,
        PLATFORM
    }

    protected double ticksPassed;
    private final WorldPoint worldLocation;
    private final ObjectType type;

    TimedObject(WorldPoint worldLocation, ObjectType type) {
        this.worldLocation = worldLocation;
        this.type = type;
    }

    public Color getStateColor() {
        return new Color(0, 0, 0);
    }
    public void updateTimeRemaining() {
        ++ticksPassed;
    }

    public double getTimeLeft() { return ticksPassed; }
    public double getTimeRemaining()  {
        return ticksPassed;
    }
    public WorldPoint getWorldLocation() { return worldLocation; }
    public ObjectType getObjectType() { return type; }
    public boolean isTimeExpired() { return true; }
}
