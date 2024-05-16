package com.ultimatevm;

import java.util.LinkedList;

public class TimelineCache {
    public LinkedList<StatusState> possibleStates;
    public StabilityUpdateInfo prevStabInfo;
    public StatusState predictedState;
    public int startingTick, i;
    public int previousMovementTick, numTicksNegativePredictedStability;
    public int mostRecentIdentifyTick;

    TimelineCache() {

    }
    void initalize(StatusState initialState, int startTick) {
        possibleStates = new LinkedList<>();
        prevStabInfo = null;
        predictedState = new StatusState(initialState);
        i = startingTick = previousMovementTick = startTick;
        mostRecentIdentifyTick = startTick;
        numTicksNegativePredictedStability = 0;
        possibleStates.push(predictedState);
    }
}
