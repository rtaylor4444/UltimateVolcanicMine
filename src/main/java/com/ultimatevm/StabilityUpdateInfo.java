package com.ultimatevm;

public class StabilityUpdateInfo {
    static private int numPlayers = 1;
    static public void resetPlayers() { numPlayers = 1;}
    static public int getNumPlayers() { return numPlayers; }
    static public void setNumPlayers(int players) {
        numPlayers = Math.max(numPlayers, players);
    }
    static public int getMinRNGVariation() {
        return -numPlayers / 3;
    }
    static public int getMaxRNGPossibleSize() { return (numPlayers / 3) + 2;}

    static public StatusState getPredictionState(StabilityUpdateInfo initialStabUpdate, VentStatusTimeline timeline) {
        if(initialStabUpdate == null) return timeline.getTimelinePredictionState();
        StatusState predictionState = new StatusState();
        //Iterate until we get a valid uncut range prediction
        int startingRNGMod = initialStabUpdate.RNGUpdateMod;
        for(int i = getMaxRNGPossibleSize() - 1; i >= 0; --i) {
            //Start with more common mods first
            initialStabUpdate.RNGUpdateMod = 1 - i;
            initialStabUpdate.calcStabilityChange();
            //Run a prediction with this new mod to see if we get a valid result
            predictionState = timeline.getTimelinePredictionState();
            if(predictionState.areRangesDefined()) break;
        }
        initialStabUpdate.RNGUpdateMod = startingRNGMod;
        initialStabUpdate.calcStabilityChange();
        return predictionState;
    }
    //Stability updates have a max rng mod of +1
    //and a min mod of -numPlayers / 3 for team sizes less than 6
    //team sizes 6 and up I am unsure
    private byte possibleRNGMods;
    private StatusState stabilityUpdateState;
    private int RNGUpdateMod;
    private final int tickTimeStamp, initialChange;
    private boolean isVerified;
    StabilityUpdateInfo(StatusState stabilityUpdate, int currentTick, int change) {
        possibleRNGMods = 0;
        tickTimeStamp = currentTick;
        isVerified = false;
        for(int i = 0; i < getMaxRNGPossibleSize(); ++i)
            possibleRNGMods |= (1 << i);

        //By default we assume the most common rng mod
        this.stabilityUpdateState = new StatusState(stabilityUpdate);
        this.initialChange = change;
        this.RNGUpdateMod = getMinRNGVariation();
        verifyByInvalidPoints();
        calcStabilityChange();
    }

    public void verifyByInvalidPoints() {
        if(!isValid()) return;
        if(isVerified()) return;

        for(int i = 0; i < getMaxRNGPossibleSize(); ++i) {
            int currentRNGMod = 1 - i;
            //check if this mod is possible
            if(!stabilityUpdateState.calcPredictedVentValues(initialChange - currentRNGMod)) {
                possibleRNGMods &= ~(1 << i);
            }
        }
        checkVerification();
    }
    public void verifyByBreakpointShift(StabilityUpdateInfo futureStabState, int movementAmount) {
        if(!isValid()) return;
        if(isVerified() && futureStabState.isVerified()) return;
        //Ensure all possible predicted numbers are on the same breakpoint
        if((Math.abs(movementAmount) % 3) != 0) return;
        //This will only work for one missing vent
        int[] missingVentIndices = futureStabState.getStabilityUpdateState().getUnidentifiedVentIndices();
        if(missingVentIndices.length != 1) return;

        int shift = movementAmount / 3;
        StatusState testState = new StatusState(stabilityUpdateState);
        //If there is no overlap mod is the same
        testState.setOverlappingRangesWith(futureStabState.getStabilityUpdateState());
        //Otherwise the mod is based off the difference between the two ranges
    }

    public void calcStabilityChange() {
        stabilityUpdateState.clearAllRanges();
        stabilityUpdateState.calcPredictedVentValues(initialChange - RNGUpdateMod);
    }
    public void updateVentValues(StatusState updatedState) {
        stabilityUpdateState.setVentsEqualTo(updatedState);
        verifyByInvalidPoints();
        calcStabilityChange();
    }
    public void updatePredictedState(StatusState predictedState, StabilityUpdateInfo prevStabInfo, int initalRNGMod) {
        if(prevStabInfo == null || stabilityUpdateState.getNumIdentifiedVents() == prevStabInfo.getStabilityUpdateState().getNumIdentifiedVents()) {
            predictedState.setOverlappingRangesWith(getAllPossiblePredictedValuesState());
            trimDoubleVentRanges(predictedState);
        }
        else predictedState.setOverlappingRangesWith(getPossiblePredictedValuesState(initalRNGMod));
    }
    public StatusState getAllPossiblePredictedValuesState() {
        StatusState mergedPossiblities = new StatusState(stabilityUpdateState);
        for(int i = 0; i < getMaxRNGPossibleSize(); ++i) {
            StatusState testState = getPossiblePredictedValuesState(1 - i);
            mergedPossiblities.mergePredictedRangesWith(testState);
        }
        return mergedPossiblities;
    }
    public StatusState getPossiblePredictedValuesState(int rngMod) {
        StatusState possibleState = new StatusState(stabilityUpdateState);
        possibleState.clearAllRanges();
        possibleState.calcPredictedVentValues(initialChange - rngMod);
        return possibleState;
    }

    private void checkVerification() {
        int numBitsOn = 0;
        for(int i = 0; i < getMaxRNGPossibleSize(); ++i) {
            int currentRNGMod = 1 - i;
            //set our rngMod to the lowest possible value (since it would be more common)
            if((possibleRNGMods & (1 << i)) != 0) {
                RNGUpdateMod = currentRNGMod;
                ++numBitsOn;
            }
        }
        if(numBitsOn == 1) {
            isVerified = true;
        }
    }
    private void trimDoubleVentRanges(StatusState predictedState) {
        if(predictedState.getNumIdentifiedVents() != 1) return;
        StatusState mergedTrimmings = new StatusState();
        mergedTrimmings.clearAllRanges();
        for(int i = 0; i < getMaxRNGPossibleSize(); ++i) {
            StatusState testState = new StatusState(predictedState);
            testState.trimDoubleVentRanges(initialChange - (1 - i));
            mergedTrimmings.mergePredictedRangesWith(testState);
        }
        predictedState.setOverlappingRangesWith(mergedTrimmings);
    }

    //Accessors
    public final StatusState getStabilityUpdateState() { return stabilityUpdateState; }
    public boolean isValid() {
        if(stabilityUpdateState.isAllVentsIdentified()) return false;
        return stabilityUpdateState.isEnoughVentsIdentified();
    }
    public boolean isVerified() { return isVerified; }
    public int getTickTimeStamp() { return tickTimeStamp; }
    public int getRNGUpdateMod() { return RNGUpdateMod; }
    public int getInitialChange() { return initialChange; }
}
