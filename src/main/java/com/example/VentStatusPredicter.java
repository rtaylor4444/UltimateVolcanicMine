package com.example;

import static com.example.StatusState.*;
import static com.example.VentStatus.*;

import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;

public class VentStatusPredicter {

    private static final char[] VENT_TAGS = {'A', 'B', 'C'};
    private static final int STABILITY_CHANGE_CONSTANT = 25;
    private static final int TRUNCATION_POSSIBILITIES = NUM_VENTS - 1;

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
    public boolean updateVentMovement() {
        int updatedVents = 0;
        for(int i = 0; i < vents.length; ++i) {
            if(!vents[i].isRangeDefined() || isFrozen(vents[i].getName())) continue;
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
    public void makeStatusState(Client client, int change) {
        if(currentState == null)
            currentState = new StatusState(vents, change);
        else {
            if(previousState == null) previousState = new StatusState(currentState);
            else previousState.update(currentState);
            currentState.update(vents, change);
        }
        clearVentMovement();

        if(currentState.isAllVentsIdentified() || !currentState.isEnoughVentsIdentified()) return;

        //Get the unidentified vent (we assume that there is just one)
        int idVentIndex = 0;
        for(int i = 0; i < vents.length; ++i) {
            if(!vents[i].isIdentified()) {
                idVentIndex = i;
                break;
            }
        }

        VentStatus currentVent = currentState.getVent(idVentIndex);
        calcSingleVentValue(currentVent, change);
        if(!fixRanges(client, idVentIndex)) {
            vents[idVentIndex].setEqualTo(currentVent);
            clearVentMovement();
        }
    }
    public boolean fixRanges(Client client, int idVentIndex) {
        if(previousState == null) return false;
        VentStatus previousVent = previousState.getVent(idVentIndex);
        if(!previousVent.isRangeDefined()) return false;

        VentStatus currentVent = currentState.getVent(idVentIndex);
        //Debug prints
        int move = currentVent.getMovementSinceLastState();
        if(client != null) {
            client.addChatMessage(ChatMessageType.GAMEMESSAGE, "CyanWarrior4: ", "move: " + move, null);
        }

        if(isFrozen(currentVent.getName())) return true;

        //Clear previous ranges (just incase of merged values)
        int lowerDiff = currentVent.getLowerBoundEnd() - previousVent.getLowerBoundEnd();
        int upperDiff = currentVent.getUpperBoundStart() - previousVent.getUpperBoundStart();
        if(lowerDiff != 0 || upperDiff != 0) {
            vents[idVentIndex].clearRanges();
        }

        //If movement is 0 vent is stuck at 0%/100%
        if(move == 0) move += currentVent.getDirection();
        //Pick the correct answer based on our move
        //BUG - both lower + upper same direction
        if(move < 0) {
            if(lowerDiff < 0) {
                vents[idVentIndex].setLowerBoundRange(currentVent.getLowerBoundStart(), currentVent.getLowerBoundEnd());
                vents[idVentIndex].setUpperBoundRange(currentVent.getLowerBoundStart(), currentVent.getLowerBoundEnd());
            }
            if(upperDiff < 0) {
                vents[idVentIndex].setLowerBoundRange(currentVent.getUpperBoundStart(), currentVent.getUpperBoundEnd());
                vents[idVentIndex].setUpperBoundRange(currentVent.getUpperBoundStart(), currentVent.getUpperBoundEnd());
            }
        }
        else {
            if(lowerDiff > 0) {
                vents[idVentIndex].setLowerBoundRange(currentVent.getLowerBoundStart(), currentVent.getLowerBoundEnd());
                vents[idVentIndex].setUpperBoundRange(currentVent.getLowerBoundStart(), currentVent.getLowerBoundEnd());
            }
            if(upperDiff > 0) {
                vents[idVentIndex].setLowerBoundRange(currentVent.getUpperBoundStart(), currentVent.getUpperBoundEnd());
                vents[idVentIndex].setUpperBoundRange(currentVent.getUpperBoundStart(), currentVent.getUpperBoundEnd());
            }
        }
        //Do nothing on 0 difference (the answer is the same as the last one)
        return true;
    }
    public String getVentStatusText(int index, String startingText) {
        if(vents[index].isIdentified() || !vents[index].isRangeDefined()) return startingText;
        StringBuilder builder = new StringBuilder();
        builder.append(startingText.substring(0, 3));
        builder.append("<col=00ffff>");
        if(vents[index].isTwoSeperateValues()) {
            if(vents[index].isLowerBoundSingleValue())
                builder.append(vents[index].getLowerBoundStart()).append("%");
            else {
                builder.append(vents[index].getLowerBoundStart()).append("-");
                builder.append(vents[index].getLowerBoundEnd());
            }
            builder.append(" ");
            if(vents[index].isUpperBoundSingleValue())
                builder.append(vents[index].getUpperBoundStart()).append("%");
            else {
                builder.append(vents[index].getUpperBoundStart()).append("-");
                builder.append(vents[index].getUpperBoundEnd());
            }
        } else {
            if(vents[index].isLowerBoundSingleValue())
                builder.append(vents[index].getLowerBoundStart()).append("%");
            else {
                builder.append(vents[index].getLowerBoundStart()).append("-");
                builder.append(vents[index].getLowerBoundEnd()).append("%");
            }
        }
        return builder.append("</col>").toString();
    }
    public void clearStabilityStates() {
        identifiedBitMask = 0;
        currentState = previousState = null;
    }


    private int getIdentifiedVentTotalValue() {
        int totalVentUpdate = 0;
        for(int i = 0; i < vents.length; ++i) {
            if(!vents[i].isIdentified()) continue;
            totalVentUpdate += getSpecificVentUpdate(vents[i].getActualValue());
        }
        return totalVentUpdate;
    }
    private void calcSingleVentValue(VentStatus unIdVent, int change) {
        int partialVentUpdate = getIdentifiedVentTotalValue();
        int missingVentUpdate = getTotalVentUpdate(change) - partialVentUpdate;
        int lowerBoundStart = (PERFECT_VENT_VALUE - TRUNCATION_POSSIBILITIES) - missingVentUpdate;
        int lowerBoundEnd = (PERFECT_VENT_VALUE + TRUNCATION_POSSIBILITIES) - missingVentUpdate;
        int upperBoundStart = (PERFECT_VENT_VALUE - TRUNCATION_POSSIBILITIES) + missingVentUpdate;
        int upperBoundEnd = (PERFECT_VENT_VALUE + TRUNCATION_POSSIBILITIES) + missingVentUpdate;
        while(lowerBoundStart < lowerBoundEnd) {
            int newChange1 = calcStabilityChange(partialVentUpdate + getSpecificVentUpdate(lowerBoundStart));
            int newChange2 = calcStabilityChange(partialVentUpdate + getSpecificVentUpdate(lowerBoundEnd));
            if(newChange1 == change && newChange2 == change) break;

            if(newChange1 != change) {
                ++lowerBoundStart; --upperBoundEnd;
            }
            if(newChange2 != change) {
                --lowerBoundEnd; ++upperBoundStart;
            }
        }
        unIdVent.clearRanges();
        unIdVent.setLowerBoundRange(lowerBoundStart, lowerBoundEnd);
        unIdVent.setUpperBoundRange(upperBoundStart, upperBoundEnd);
    }
    private void calcDoubleVentValue(VentStatus[] unIdVent, int change) {
        int partialVentUpdate = getIdentifiedVentTotalValue();
        int missingVentUpdate = getTotalVentUpdate(change) - partialVentUpdate;
        int maxDistance = Math.min(MAX_VENT_VALUE - PERFECT_VENT_VALUE, missingVentUpdate);
        int minDistance = Math.max(0, missingVentUpdate - PERFECT_VENT_VALUE);

        int lowerBoundStart = PERFECT_VENT_VALUE - minDistance;
        int lowerBoundEnd = PERFECT_VENT_VALUE - maxDistance;
        int upperBoundStart = PERFECT_VENT_VALUE + minDistance;
        int upperBoundEnd = PERFECT_VENT_VALUE + maxDistance;

        for(int i = 0; i < unIdVent.length; ++i) {
            unIdVent[i].setLowerBoundRange(lowerBoundStart, lowerBoundEnd);
            unIdVent[i].setUpperBoundRange(upperBoundStart, upperBoundEnd);
        }
    }
    private int calcStabilityChange(int totalVentValue) {
        return STABILITY_CHANGE_CONSTANT - (totalVentValue / NUM_VENTS);
    }
    private int getDirectionFromChambers(int index, int chambers) { return (chambers & (1 << index)) != 0 ? 1 : -1;}
    private int getTotalVentUpdate(int change) { return (STABILITY_CHANGE_CONSTANT - change) * NUM_VENTS; }
    private int getSpecificVentUpdate(int ventValue) { return Math.abs(PERFECT_VENT_VALUE - ventValue); }
    public boolean isFrozen(Character ventName) {
        boolean hasEstimatedARange = (vents[0].isIdentified() || vents[0].isRangeDefined());
        boolean hasEstimatedBRange = (vents[1].isIdentified() || vents[1].isRangeDefined());
        boolean hasEstimatedCRange = (vents[2].isIdentified() || vents[2].isRangeDefined());

        switch(ventName) {
            case 'A':
                //A never freezes
                return false;

            case 'B':
                //B will only freeze if A is within 49-59% and B is also 49-59%
                //If A is undefined we must assume it could be in freeze range
                if(!hasEstimatedARange) return vents[1].isWithinRange(41, 59);
                if(!vents[0].isWithinRange(41, 59)) return false;
                if(!hasEstimatedBRange) return true;
                return vents[1].isWithinRange(41, 59);

            case 'C':
                //Undefined ranges means we must assume that they can be in range
                if(!hasEstimatedARange && !hasEstimatedBRange) return true;
                //If A is within 41-59%..
                if(!hasEstimatedARange || vents[0].isWithinRange(41, 59)) {
                    //and B is within 41-59% C will freeze no matter what
                    if(!hasEstimatedBRange || vents[1].isWithinRange(41, 59)) return true;
                    //and B is not within 41-59% C will only freeze if its within 41-59%
                    else if(!hasEstimatedCRange || vents[2].isWithinRange(41, 59)) return true;
                }
                //C will freeze if both B and C are within 49-59%
                if(!hasEstimatedBRange || vents[1].isWithinRange(41, 59))
                    return !hasEstimatedCRange || vents[2].isWithinRange(41, 59);
                return false;
        }
        return true;
    }

    public final StatusState getCurrentState() { return currentState; }
    public final StatusState getPreviousState() { return previousState; }
    public final VentStatus[] getCurrentVents() { return vents; }
    public boolean areAnyVentIdentified() { return identifiedBitMask > 0; }
}
