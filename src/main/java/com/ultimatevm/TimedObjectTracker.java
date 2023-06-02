package com.ultimatevm;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import net.runelite.api.coords.WorldPoint;

public class TimedObjectTracker {
    HashSet<Integer> positionCodes;
    LinkedList<TimedObject> timedObjects;
    public TimedObjectTracker() {
        timedObjects = new LinkedList<>();
        positionCodes = new HashSet<>();
    }

    public void addObject(WorldPoint worldLocation, TimedObject.ObjectType type) {
        int positionCode = (worldLocation.getX() << 16) | worldLocation.getY();
        if(positionCodes.contains(positionCode)) return;
        positionCodes.add(positionCode);
        switch(type) {
            case ROCK:
                timedObjects.addLast(new CappingRock(worldLocation));
                break;

            case PLATFORM:
                timedObjects.addLast(new PlayerPlatform(worldLocation));
                break;
        }
    }
    public void clearRocks() {
        timedObjects.clear();
        positionCodes.clear();
    }
    public void updateRockTimers() {
        for (TimedObject obj : timedObjects) {
            obj.updateTimeRemaining();
        }
        removeExpiredRocks();
    }
    private void removeExpiredRocks() {
        Iterator<TimedObject> it = timedObjects.iterator();
        while(it.hasNext()) {
            TimedObject obj = it.next();
            if(!obj.isTimeExpired()) continue;
            int positionCode = (obj.getWorldLocation().getX() << 16) | obj.getWorldLocation().getY();
            positionCodes.remove(positionCode);
            it.remove();
        }
    }

    public final LinkedList<TimedObject> getObjects() { return timedObjects; }
}
