package com.ultimatevm;

import java.util.LinkedList;

public class TimelineCache {
    public LinkedList<StatusState> possibleStates;
    public StabilityUpdateInfo prevStabInfo;
    public StatusState predictedState;
    public int startingTick, i;
    public int previousMovementTick, numTicksNegativePredictedStability;

    TimelineCache() {

    }
    void initalize(StatusState initialState, int startTick) {
        possibleStates = new LinkedList<>();
        prevStabInfo = null;
        predictedState = new StatusState(initialState);
        i = startingTick = previousMovementTick = startTick;
        numTicksNegativePredictedStability = 0;
        possibleStates.push(predictedState);
    }
}
