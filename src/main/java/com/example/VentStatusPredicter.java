package com.example;

import static com.example.StatusState.*;
import static com.example.VentStatus.*;

public class VentStatusPredicter {

    private static final char[] VENT_TAGS = {'A', 'B', 'C'};
    private static final int STABILITY_CHANGE_CONSTANT = 25;

    private VentStatus vents[] = new VentStatus[NUM_VENTS];
    private StatusState currentState, previousState;
    private int identifiedBitMask;
    private boolean hasReset = false;
    public VentStatusPredicter() {
        initialize();
    }

    public void initialize() {
        hasReset = false;
        for(int i = 0; i < vents.length; ++i) {
            vents[i] = new VentStatus(VENT_TAGS[i]);
        }
        clearStabilityStates();
    }
    public void reset() {
        if(hasReset) return;
        for(int i = 0; i < vents.length; ++i) {
            vents[i].doVMReset();
        }
        clearStabilityStates();
        hasReset = true;
    }
    public void updateVentStatus(int[] ventStatus, int chambers) {
        identifiedBitMask = 0;
        for(int i = 0; i < vents.length; ++i) {
            vents[i].update(ventStatus[i], getDirectionFromChambers(i, chambers));
            if(vents[i].isIdentified()) identifiedBitMask |= (1 << i);
        }
    }
    public boolean updateVentMovement(int varbitsUpdated) {
        //Exit if no vents are identified or not all identified values have been updated from the server
        if(identifiedBitMask == 0 || (identifiedBitMask != varbitsUpdated)) return false;

        int updatedVents = 0;
        for(int i = 0; i < vents.length; ++i) {
            if(!vents[i].isRangeDefined() || isFrozen(vents[i])) continue;
            ++updatedVents;
            vents[i].updateMovement();
        }
        return updatedVents > 0;
    }
    public void clearVentMovement() {
        for(int i = 0; i < vents.length; ++i) {
            vents[i].clearMovement();
        }
    }
    public void makeStatusState(int change) {
        if(currentState == null)
            currentState = new StatusState(vents, change);
        else {
            if(previousState == null) previousState = new StatusState(currentState);
            else previousState.update(currentState);
            currentState.update(vents, change);
        }
        clearVentMovement();
        calcMissingSingleVentValue(change);
    }
    public String getVentStatusText(int index, String startingText) {
        if(vents[index].isIdentified() || !vents[index].isRangeDefined()) return startingText;
        StringBuilder builder = new StringBuilder();
        builder.append(startingText.substring(0, 3));
        builder.append("<col=00ffff>");
        builder.append(vents[index].getLowerBoundStart() +
                "% | " + vents[index].getUpperBoundStart()).append("%");
        return " " + builder.append("</col>").toString();
    }
    public void clearStabilityStates() {
        identifiedBitMask = 0;
        currentState = previousState = null;
    }
    private boolean isFrozen(VentStatus vent) {
        switch(vent.getName()) {
            case 'A':
                //A never freezes
                return false;
        }
        return true;
    }

    private void calcMissingSingleVentValue(int change) {
        if(currentState.isAllVentsIdentified() || !currentState.isEnoughVentsIdentified()) return;

        //Calculate the missing vent value
        int partialVentUpdate = 0;
        VentStatus unIdVent = null;
        for(int i = 0; i < vents.length; ++i) {
            if(!vents[i].isIdentified()) {
                unIdVent = vents[i];
                continue;
            }
            partialVentUpdate += getSpecificVentUpdate(vents[i].getActualValue());
        }
        int missingVentUpdate = getTotalVentUpdate(change) - partialVentUpdate;
        unIdVent.setLowerBoundRange(PERFECT_VENT_VALUE - missingVentUpdate, PERFECT_VENT_VALUE - missingVentUpdate);
        unIdVent.setUpperBoundRange(PERFECT_VENT_VALUE + missingVentUpdate, PERFECT_VENT_VALUE + missingVentUpdate);
    }
    private int getDirectionFromChambers(int index, int chambers) {
        return (chambers & (1 << index)) != 0 ? 1 : -1;
    }
    private int getTotalVentUpdate(int change) { return (STABILITY_CHANGE_CONSTANT - change) * NUM_VENTS; }
    private int getSpecificVentUpdate(int ventValue) { return Math.abs(PERFECT_VENT_VALUE - ventValue); }
}
