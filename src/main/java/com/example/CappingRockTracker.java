package com.example;

import java.util.LinkedList;
import net.runelite.api.coords.WorldPoint;

public class CappingRockTracker {
    LinkedList<CappingRock> rocks;
    public CappingRockTracker() {
        rocks = new LinkedList<>();
    }

    public void addRock(WorldPoint worldLocation) {
        rocks.add(new CappingRock(worldLocation));
    }
    public void updateRockTimers() {
        for (CappingRock rock : rocks) {
            rock.updateTimeRemaining();
        }
        removeExpiredRocks();
    }
    private void removeExpiredRocks() {
        while(!rocks.isEmpty()) {
            CappingRock rock = rocks.getFirst();
            if(!rock.isTimeExpired()) break;
            rocks.removeFirst();
        }
    }

    public final LinkedList<CappingRock> getRocks() { return rocks; }
}
