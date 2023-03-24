package com.ultimatevm;

import static com.ultimatevm.VentStatus.*;

public class StatusState {
    private static final char[] VENT_TAGS = {'A', 'B', 'C'};
    public static final int NUM_VENTS = 3;
    public static final int STABILITY_CHANGE_CONSTANT = 25;
    public static final int TRUNCATION_POSSIBILITIES = NUM_VENTS - 1;

    private VentStatus[] vents = new VentStatus[NUM_VENTS];
    private int numIdentifiedVents;
    private int stabilityChange;
    private boolean hasReset;

    public static int getTotalVentUpdate(int change) { return (STABILITY_CHANGE_CONSTANT - change) * NUM_VENTS; }
    public static int calcStabilityChange(int totalVentValue) {
        return STABILITY_CHANGE_CONSTANT - (totalVentValue / NUM_VENTS);
    }

    public StatusState() {
        hasReset = false;
        numIdentifiedVents = 0;
        for(int i = 0; i < vents.length; ++i) {
            vents[i] = new VentStatus(VENT_TAGS[i]);
        }
    }
    public StatusState(StatusState state) {
        for(int i = 0; i < vents.length; ++i) {
            vents[i] = new VentStatus(VENT_TAGS[i]);
        }
        setEqualTo(state);
    }
    public void setVentsEqualTo(StatusState state) {
        this.numIdentifiedVents = state.numIdentifiedVents;
        for(int i = 0; i < vents.length; ++i) {
            vents[i].setEqualTo(state.vents[i]);
        }
    }
    public void setEqualTo(StatusState state) {
        this.hasReset = state.hasReset;
        this.stabilityChange = state.stabilityChange;
        setVentsEqualTo(state);
    }

    public VentChangeState[] updateVentStatus(int[] ventStatus, int chambers) {
        numIdentifiedVents = 0;
        VentChangeState[] changeStates = new VentChangeState[NUM_VENTS];
        for(int i = 0; i < ventStatus.length; ++i) {
            changeStates[i] = vents[i].update(ventStatus[i], getDirectionFromChambers(i, chambers));
            if(vents[i].isIdentified()) ++numIdentifiedVents;
        }
        //TODO: Narrow range bounds based on movement
        return changeStates;
    }
    public void updateVentMovement() {
        int currentVentInfluence = 0;
        for(int i = 0; i < vents.length; ++i) {
            vents[i].updateMovement(currentVentInfluence);
            currentVentInfluence += vents[i].getEstimatedInfluence();
        }
    }
    public void clearVentMovement() {
        for(int i = 0; i < vents.length; ++i) {
            vents[i].clearMovement();
        }
    }
    public void doVMReset() {
        if(hasReset) return;
        for(int i = 0; i < vents.length; ++i) {
            vents[i].doVMReset();
        }
        hasReset = true;
    }
    public int[] getUnidentifiedVentIndices() {
        int curIndex = 0;
        int[] indices = new int[NUM_VENTS - numIdentifiedVents];
        for(int i = 0; i < vents.length; ++i) {
            if(this.vents[i].isIdentified()) continue;
            indices[curIndex++] = i;
        }
        return indices;
    }

    public void calcPredictedVentValues(int change) {
        stabilityChange = change;
        if(isAllVentsIdentified()) return;
        if(!isEnoughVentsIdentified()) return;
        int[] indices = getUnidentifiedVentIndices();
        calcSingleVentValue(vents[indices[0]], change);
    }
    private void calcSingleVentValue(VentStatus vent, int change) {
        int partialVentUpdate = getIdentifiedVentTotalValue();
        int missingVentUpdate = getTotalVentUpdate(change) - partialVentUpdate;
        int lowerBoundStart = (PERFECT_VENT_VALUE - TRUNCATION_POSSIBILITIES) - missingVentUpdate;
        int lowerBoundEnd = (PERFECT_VENT_VALUE + TRUNCATION_POSSIBILITIES) - missingVentUpdate;
        int upperBoundStart = (PERFECT_VENT_VALUE - TRUNCATION_POSSIBILITIES) + missingVentUpdate;
        int upperBoundEnd = (PERFECT_VENT_VALUE + TRUNCATION_POSSIBILITIES) + missingVentUpdate;
        while(lowerBoundStart < lowerBoundEnd) {
            int newChange1 = calcStabilityChange(partialVentUpdate + getVentStabilityInfluence(lowerBoundStart));
            int newChange2 = calcStabilityChange(partialVentUpdate + getVentStabilityInfluence(lowerBoundEnd));
            if(newChange1 == change && newChange2 == change) break;

            if(newChange1 != change) {
                ++lowerBoundStart; --upperBoundEnd;
            }
            if(newChange2 != change) {
                --lowerBoundEnd; ++upperBoundStart;
            }
        }

        vent.clearRanges();
        vent.setLowerBoundRange(lowerBoundStart, lowerBoundEnd);
        vent.setUpperBoundRange(upperBoundStart, upperBoundEnd);
    }
    private void calcDoubleVentValue(VentStatus[] vents, int change) {
        int partialVentUpdate = getIdentifiedVentTotalValue();
        int missingVentUpdate = getTotalVentUpdate(change) - partialVentUpdate;
        int maxDistance = Math.min(MAX_VENT_VALUE - PERFECT_VENT_VALUE, missingVentUpdate);
        int minDistance = Math.max(0, missingVentUpdate - PERFECT_VENT_VALUE);

        int lowerBoundStart = PERFECT_VENT_VALUE - maxDistance;
        int lowerBoundEnd = PERFECT_VENT_VALUE - minDistance;
        int upperBoundStart = PERFECT_VENT_VALUE + minDistance;
        int upperBoundEnd = PERFECT_VENT_VALUE + maxDistance;

        for(int i = 0; i < vents.length; ++i) {
            vents[i].clearRanges();
            vents[i].setLowerBoundRange(lowerBoundStart, lowerBoundEnd);
            vents[i].setUpperBoundRange(upperBoundStart, upperBoundEnd);
        }
    }
    private int getDirectionFromChambers(int index, int chambers) { return (chambers & (1 << index)) != 0 ? 1 : -1;}
    private int getIdentifiedVentTotalValue() {
        int totalVentUpdate = 0;
        for(int i = 0; i < NUM_VENTS; ++i) {
            totalVentUpdate += vents[i].getStabilityInfluence();
        }
        return totalVentUpdate;
    }

    public boolean hasDoneVMReset() { return hasReset; }
    public boolean isEnoughVentsIdentified() { return numIdentifiedVents > 1; }
    public boolean isAllVentsIdentified() { return numIdentifiedVents == NUM_VENTS; }
    public final VentStatus[] getVents() { return vents; }
    public int getStabilityChange() { return stabilityChange; }
    public int getNumIdentifiedVents() { return numIdentifiedVents; }
}
