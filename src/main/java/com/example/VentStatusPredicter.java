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
        int idVentIndex = -1;
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
        int lowerDiff = currentVent.getLowerBoundStart() - previousVent.getLowerBoundStart();
        int upperDiff = currentVent.getUpperBoundEnd() - previousVent.getUpperBoundEnd();
        client.addChatMessage(ChatMessageType.GAMEMESSAGE, "CyanWarrior4: ", "move: " + move, null);
        client.addChatMessage(ChatMessageType.GAMEMESSAGE, "CyanWarrior4: ", "lowerDiff: " + lowerDiff, null);
        client.addChatMessage(ChatMessageType.GAMEMESSAGE, "CyanWarrior4: ", "upperDiff: " + upperDiff, null);

        //If difference is 0 vent is either frozen or stuck at 0%/100%
        if(lowerDiff == 0 && upperDiff == 0) return true;

        //Determine the correct diff of the two
        int correctDiff = 0;
        if(move < 0) correctDiff = Math.min(lowerDiff, upperDiff);
        else if(move > 0) correctDiff = Math.max(lowerDiff, upperDiff);

        int lowerBoundStart = vents[idVentIndex].getLowerBoundStart() + correctDiff;
        int lowerBoundEnd = vents[idVentIndex].getLowerBoundEnd() + correctDiff;
        int upperBoundStart = vents[idVentIndex].getUpperBoundStart() + correctDiff;
        int upperBoundEnd = vents[idVentIndex].getUpperBoundEnd() + correctDiff;
        boolean isLowerMatch = currentVent.isLowerBoundWithinRange(lowerBoundStart - TRUNCATION_POSSIBILITIES,
                lowerBoundEnd + TRUNCATION_POSSIBILITIES);
        boolean isUpperMatch = currentVent.isUpperBoundWithinRange(upperBoundStart - TRUNCATION_POSSIBILITIES,
                upperBoundEnd + TRUNCATION_POSSIBILITIES);
        if(isLowerMatch && isUpperMatch) {
            vents[idVentIndex].setLowerBoundRange(currentVent.getLowerBoundStart(), currentVent.getLowerBoundEnd());
            vents[idVentIndex].setUpperBoundRange(currentVent.getUpperBoundStart(), currentVent.getUpperBoundEnd());
        }
        else if(isLowerMatch) {
            vents[idVentIndex].setLowerBoundRange(currentVent.getLowerBoundStart(), currentVent.getLowerBoundEnd());
            vents[idVentIndex].setUpperBoundRange(currentVent.getLowerBoundStart(), currentVent.getLowerBoundEnd());
        }
        else if(isUpperMatch) {
            vents[idVentIndex].setLowerBoundRange(currentVent.getUpperBoundStart(), currentVent.getUpperBoundEnd());
            vents[idVentIndex].setUpperBoundRange(currentVent.getUpperBoundStart(), currentVent.getUpperBoundEnd());
        }
        return true;
    }
    public String getVentStatusText(int index, String startingText) {
        if(vents[index].isIdentified() || !vents[index].isRangeDefined()) return startingText;
        StringBuilder builder = new StringBuilder();
        builder.append(startingText.substring(0, 3));
        builder.append("<col=00ffff>");
        if(vents[index].isTwoSeperateValues()) {
            if(vents[index].isLowerBoundSingleValue())
                builder.append(Integer.toString(vents[index].getLowerBoundStart()) + "%");
            else {
                builder.append(Integer.toString(vents[index].getLowerBoundStart()) + "-");
                builder.append(Integer.toString(vents[index].getLowerBoundEnd()));
            }
            builder.append(" ");
            if(vents[index].isUpperBoundSingleValue())
                builder.append(Integer.toString(vents[index].getUpperBoundStart()) + "%");
            else {
                builder.append(Integer.toString(vents[index].getUpperBoundStart()) + "-");
                builder.append(Integer.toString(vents[index].getUpperBoundEnd()));
            }
        } else {
            if(vents[index].isLowerBoundSingleValue())
                builder.append(Integer.toString(vents[index].getLowerBoundStart()) + "%");
            else {
                builder.append(Integer.toString(vents[index].getLowerBoundStart()) + "-");
                builder.append(Integer.toString(vents[index].getLowerBoundEnd()) + "%");
            }
        }
        return builder.append("</col>").toString();
    }
    public void clearStabilityStates() {
        identifiedBitMask = 0;
        currentState = previousState = null;
    }
    public boolean areAnyVentIdentified() { return identifiedBitMask > 0; }
    private boolean isFrozen(VentStatus vent) {
        boolean hasEstimatedARange = (vents[0].isIdentified() || vents[0].isRangeDefined());
        boolean hasEstimatedBRange = (vents[1].isIdentified() || vents[1].isRangeDefined());

        switch(vent.getName()) {
            case 'A':
                //A never freezes
                return false;

            case 'B':
                //B will only freeze if A is within 49-59% and B is also 49-59%
                //(undefined ranges means we assume frozen)
                if(!hasEstimatedARange) return true;
                if(!vents[0].isWithinRange(41, 59)) return false;
                if(!hasEstimatedBRange) return true;
                return vents[1].isWithinRange(41, 59);

            case 'C':
                if(!hasEstimatedARange || !hasEstimatedBRange) return true;
                //If A is within 41-59%..
                if(vents[0].isWithinRange(41, 59)) {
                    //and B is within 41-59% C will freeze no matter what
                    if(vents[1].isWithinRange(41, 59)) return true;
                    //and B is not within 41-59% C will only freeze if its within 41-59%
                    else if(vents[2].isWithinRange(41, 59)) return true;
                }
                //C will freeze if both B and C are within 49-59%
                if(vents[1].isWithinRange(41, 59)) return vents[2].isWithinRange(41, 59);
                return false;
        }
        return true;
    }
    private void calcSingleVentValue(VentStatus unIdVent, int change) {
     //Calculate the missing vent value
        int partialVentUpdate = 0;
        for(int i = 0; i < vents.length; ++i) {
            if(!vents[i].isIdentified()) continue;
            partialVentUpdate += getSpecificVentUpdate(vents[i].getActualValue());
        }

        int missingVentUpdate = getTotalVentUpdate(change) - partialVentUpdate;
        int lowerBoundStart = (PERFECT_VENT_VALUE - TRUNCATION_POSSIBILITIES) - missingVentUpdate;
        int lowerBoundEnd = (PERFECT_VENT_VALUE + TRUNCATION_POSSIBILITIES) - missingVentUpdate;
        int upperBoundStart = (PERFECT_VENT_VALUE - TRUNCATION_POSSIBILITIES) + missingVentUpdate;
        int upperBoundEnd = (PERFECT_VENT_VALUE + TRUNCATION_POSSIBILITIES) + missingVentUpdate;
        while(lowerBoundStart < lowerBoundEnd) {
            int newChange1 = calcStabilityChange(partialVentUpdate + lowerBoundStart);
            int newChange2 = calcStabilityChange(partialVentUpdate + lowerBoundEnd);
            if(newChange1 == change && newChange2 == change) break;

            if(newChange1 != change) {
                ++lowerBoundStart; --upperBoundEnd;
            }
            if(newChange2 != change) {
                --lowerBoundEnd; ++upperBoundStart;
            }
        }
        unIdVent.setLowerBoundRange(lowerBoundStart, lowerBoundEnd);
        unIdVent.setUpperBoundRange(upperBoundStart, upperBoundEnd);
    }
    private int calcStabilityChange(int totalVentValue) {
        return STABILITY_CHANGE_CONSTANT - (totalVentValue / NUM_VENTS);
    }
    private int getDirectionFromChambers(int index, int chambers) { return (chambers & (1 << index)) != 0 ? 1 : -1;}
    private int getTotalVentUpdate(int change) { return (STABILITY_CHANGE_CONSTANT - change) * NUM_VENTS; }
    private int getSpecificVentUpdate(int ventValue) { return Math.abs(PERFECT_VENT_VALUE - ventValue); }
}
