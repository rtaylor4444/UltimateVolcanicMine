package com.ultimatevm;

public class StabilityUpdateInfo {
    static final private int MAX_RNG_MOD = 1;
    static private int numPlayers = 1;
    static public int getNumPlayers() { return numPlayers; }
    static public void setNumPlayers(int players) {
        numPlayers = Math.max(numPlayers, players);
    }
    static public int getMinRNGVariation() {
        return -numPlayers / 3;
    }
    static private int getMaxRNGPossibleSize() { return (numPlayers / 3) + 1;}

    //Stability updates have a max rng mod of +1
    //and a min mod of -numPlayers / 3 for team sizes less than 6
    //team sizes 6 and up
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
        calcStabilityChange();
    }

    public void verifyByInvalidPoints() {
        if(!isValid()) return;
        if(isVerified()) return;

        //TODO: This only works solo/duo need to make it work for other sizes
        for(int i = getMaxRNGPossibleSize() - 1; i >= 0; --i) {
            int currentRNGMod = -i;
            //check if this mod is possible
            if(!stabilityUpdateState.calcPredictedVentValues(initialChange - currentRNGMod)) {
                possibleRNGMods &= ~(1 << i);
            }
        }
        if(possibleRNGMods == 0) {
            RNGUpdateMod = MAX_RNG_MOD;
            isVerified = true;
        }
    }
    public void verifyByBreakpointShift(StabilityUpdateInfo futureStabState, int movementAmount) {
        if(!isValid()) return;
        //This will only work for one missing vent
        int ventIndex = futureStabState.getStabilityUpdateState().getUnidentifiedVentIndices()[0];
        int config = Math.abs(movementAmount) % 3;
        int shift = movementAmount / 3;
        if(config == 2) {
            //Two numbers have advanced to the next breakpoint
            //Two numbers got eclipsed means breakpoint didnt shift
            //Two numbers remain means breakpoint shifted

        } else if (config == 1) {
            //One number has advanced to the next breakpoint
            //One number got eclipsed means breakpoint didnt shift
            //Only one number remains means breakpoint shifted
            //Both cases will result in at max +1 -1 diff if same mod
            //(anymore means diff mod by the extra diff)

        } else {
            //All numbers are on the same breakpoint
            StatusState testState = new StatusState(stabilityUpdateState);
            //If there is no overlap mod is the same
            testState.setOverlappingRangesWith(futureStabState.getStabilityUpdateState());
            //Otherwise the mod is based off the difference between the two ranges
        }
    }

    public void calcStabilityChange() {
        stabilityUpdateState.calcPredictedVentValues(initialChange - RNGUpdateMod);
    }
    public void updateVentValues(StatusState updatedState) {
        stabilityUpdateState.setVentsEqualTo(updatedState);
        verifyByInvalidPoints();
        calcStabilityChange();
    }
    public boolean updatePredictedState(StatusState predictedState) {
        if(new StatusState(predictedState).setOverlappingRangesWith(stabilityUpdateState))
            return predictedState.setOverlappingRangesWith(stabilityUpdateState);
        return false;
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
