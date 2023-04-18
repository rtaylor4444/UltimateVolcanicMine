package com.ultimatevm;

import static com.ultimatevm.VentStatus.*;

public class StatusState {
    private static final char[] VENT_TAGS = {'A', 'B', 'C'};
    public static final int NUM_VENTS = 3;
    public static final int STABILITY_CHANGE_CONSTANT = -25;

    public static final int TRUNCATION_POSSIBILITIES = NUM_VENTS;

    private VentStatus[] vents = new VentStatus[NUM_VENTS];
    private int numIdentifiedVents;
    private int stabilityChange;
    private int tickTimeStamp;
    private boolean hasReset;

    public static int getTotalVentUpdate(int change) {
        return (change - STABILITY_CHANGE_CONSTANT);
    }
    public static int calcStabilityChange(StatusState state) {
        return calcStabilityChange(state.getIdentifiedVentTotalValue());
    }
    public static int calcStabilityChange(int totalVentInfluence) {
        return STABILITY_CHANGE_CONSTANT + totalVentInfluence;
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
    public void setVentValueEqualTo(int index, int value) {
        vents[index].update(value, vents[index].getDirection());
    }

    public int[] updateVentStatus(int[] ventStatus, int chambers) {
        numIdentifiedVents = 0;
        int[] changeStates = new int[NUM_VENTS];
        for(int i = 0; i < ventStatus.length; ++i) {
            changeStates[i] = vents[i].update(ventStatus[i], getDirectionFromChambers(i, chambers));
            if(vents[i].isIdentified()) ++numIdentifiedVents;
        }
        return changeStates;
    }
    public void updateVentMovement() {
        int currentVentInfluence = 0;
        for(int i = 0; i < vents.length; ++i) {
            vents[i].updateMovement(currentVentInfluence);
            currentVentInfluence += vents[i].getEstimatedInfluence();
        }
    }
    public void reverseMovement() {
        int currentVentInfluence = 0;
        for(int i = 0; i < vents.length; ++i) {
            int inf = vents[i].getReversedInfluence();
            if(inf == STARTING_VENT_VALUE) break;
            currentVentInfluence += inf;
            vents[i].doReversedMovement(currentVentInfluence);
        }
    }
    public void clearVentMovement() {
        for(int i = 0; i < vents.length; ++i) {
            vents[i].clearMovement();
        }
    }
    public void mergePredictedRangesWith(StatusState state) {
        for(int i = 0; i < NUM_VENTS; ++i) {
            if(vents[i].isIdentified()) continue;
            if(!state.vents[i].isRangeDefined()) continue;
            if(vents[i].isRangeDefined()) {
                vents[i].mergeLowerBoundRanges(vents[i].getLowerBoundStart(),
                        state.vents[i].getLowerBoundStart());
                vents[i].mergeUpperBoundRanges(vents[i].getUpperBoundStart(),
                        state.vents[i].getUpperBoundStart());
            } else {
                vents[i].setLowerBoundRange(state.vents[i].getLowerBoundStart(), state.vents[i].getLowerBoundEnd());
                vents[i].setUpperBoundRange(state.vents[i].getUpperBoundStart(), state.vents[i].getUpperBoundEnd());
            }
        }
    }
    public void setOverlappingRangesWith(StatusState state) {
        for(int i = 0; i < NUM_VENTS; ++i) {
            if(vents[i].isIdentified()) continue;
            if(!state.vents[i].isRangeDefined()) continue;
            if(vents[i].isRangeDefined()) {
                int[] lower = vents[i].getOverlappedLowerBoundRange(state.vents[i].getLowerBoundStart(),
                        state.vents[i].getLowerBoundEnd());
                int[] upper = vents[i].getOverlappedUpperBoundRange(state.vents[i].getUpperBoundStart(),
                        state.vents[i].getUpperBoundEnd());
                boolean isLowerValid = !(lower[0] == 0 && lower[1] == 0);
                boolean isUpperValid = !(upper[0] == 0 && upper[1] == 0);
                vents[i].clearRanges();
                if(!isLowerValid && !isUpperValid) {
                    //Do nothing our ranges do not overlap
                }
                else if(!isLowerValid) {
                    vents[i].setLowerBoundRange(upper[0], upper[1]);
                    vents[i].setUpperBoundRange(upper[0], upper[1]);
                }
                else if(!isUpperValid) {
                    vents[i].setLowerBoundRange(lower[0], lower[1]);
                    vents[i].setUpperBoundRange(lower[0], lower[1]);
                }
                else {
                    vents[i].setLowerBoundRange(lower[0], lower[1]);
                    vents[i].setUpperBoundRange(upper[0], upper[1]);
                }
            } else {
                vents[i].setLowerBoundRange(state.vents[i].getLowerBoundStart(), state.vents[i].getLowerBoundEnd());
                vents[i].setUpperBoundRange(state.vents[i].getUpperBoundStart(), state.vents[i].getUpperBoundEnd());
            }
        }
    }
    public void doBoundsClipping() {
        for(int i = 0; i < NUM_VENTS; ++i) {
            if(vents[i].isIdentified()) continue;
            vents[i].doBoundsClipping();
        }
    }
    public void doVMReset() {
        if(hasReset) return;
        numIdentifiedVents = 0;
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
    public void clearPredictedVentValues() {
        for(int i = 0; i < NUM_VENTS; ++i) {
            if(vents[i].isIdentified()) continue;
            vents[i].clearRanges();
            vents[i].clearMovement();
        }
    }

    private void calcSingleVentValue(VentStatus vent, int change) {
        int partialVentUpdate = getIdentifiedVentTotalValue();
        float missingInversePercent = 1.0f - (getTotalVentUpdate(change) - partialVentUpdate) / VENT_STABILITY_WEIGHT;
        int missingVentUpdate = (int)Math.ceil(PERFECT_VENT_VALUE * missingInversePercent);

        int lowerBoundStart = (PERFECT_VENT_VALUE - TRUNCATION_POSSIBILITIES) - missingVentUpdate;
        int lowerBoundEnd = (PERFECT_VENT_VALUE + TRUNCATION_POSSIBILITIES) - missingVentUpdate;
        int upperBoundStart = (PERFECT_VENT_VALUE - TRUNCATION_POSSIBILITIES) + missingVentUpdate;
        int upperBoundEnd = (PERFECT_VENT_VALUE + TRUNCATION_POSSIBILITIES) + missingVentUpdate;
        while(lowerBoundStart < lowerBoundEnd) {
            int newChange1 = calcStabilityChange(partialVentUpdate + getStabilityInfluence(lowerBoundStart));
            int newChange2 = calcStabilityChange(partialVentUpdate + getStabilityInfluence(lowerBoundEnd));
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
