package com.ultimatevm;

import static com.ultimatevm.StatusState.*;
import static com.ultimatevm.VentStatus.*;

import net.runelite.api.Client;

import java.util.ArrayList;

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
            if(!vents[i].isRangeDefined()) continue;
            ++updatedVents;
            vents[i].updateMovement(isFrozen(vents[i].getName()));
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

        //Get the unidentified vents
        int curIndex = 0;
        int[] unknownVentIndices = new int[NUM_VENTS - currentState.getNumIdentifiedVents()];
        for(int i = 0; i < vents.length; ++i) {
            if(!vents[i].isIdentified()) {
                unknownVentIndices[curIndex++] = i;
            }
        }

        VentStatus currentVent = currentState.getVent(unknownVentIndices[0]);
        calcSingleVentValue(currentVent, change);
        if (!fixRangesSingle(unknownVentIndices[0])) {
            vents[unknownVentIndices[0]].setEqualTo(currentVent);
            clearVentMovement();
        }
    }
    public boolean fixRangesSingle(int idVentIndex) {
        if(previousState == null) return false;
        VentStatus previousVent = previousState.getVent(idVentIndex);
        if(!previousVent.isRangeDefined()) return false;
        VentStatus currentVent = currentState.getVent(idVentIndex);

        int pesMove = currentVent.getPessimisticMovement();
        int optMove = currentVent.getOptimisticMovement();

        //Fix our ranges to account for movement inaccuracies
        //EX: Update movement tick is off (pes move varies by -1 or +1)
        int lowerBoundStart = vents[idVentIndex].getLowerBoundStart() - pesMove;
        int lowerBoundEnd = vents[idVentIndex].getLowerBoundEnd() - pesMove;
        int upperBoundStart = vents[idVentIndex].getUpperBoundStart() - pesMove;
        int upperBoundEnd = vents[idVentIndex].getUpperBoundEnd() - pesMove;
        if(optMove < 0) {
            //maximum possible estimated value
            lowerBoundStart += (optMove * 2);
            upperBoundStart += (optMove * 2);
            //minimum possible estimated value (account for truncation possibilities)
            //ex: 31-33 shifted to 32-34 its possible it moved from 33 to 32
            //31-33 shifted to 33-35 its possible its frozen at 33
            lowerBoundEnd += TRUNCATION_POSSIBILITIES;
            upperBoundEnd += TRUNCATION_POSSIBILITIES;
        }
        else if(optMove > 0) {
            //maximum possible estimated value
            lowerBoundEnd += (optMove * 2);
            upperBoundEnd += (optMove * 2);
            //minimum possible estimated value (account for truncation possibilities)
            lowerBoundStart -= TRUNCATION_POSSIBILITIES;
            upperBoundStart -= TRUNCATION_POSSIBILITIES;
        }

        //Pick the correct ranges
        boolean isWithinLowerRange = currentVent.isLowerBoundWithinRange(lowerBoundStart, lowerBoundEnd);
        boolean isWithinUpperRange = currentVent.isUpperBoundWithinRange(upperBoundStart, upperBoundEnd);
        if(!isWithinUpperRange && isWithinLowerRange || isWithinUpperRange && !isWithinLowerRange) {
            //One range matches but not the other
            if(isWithinUpperRange) {
                //Only upper bound range matches so it must be the right answer
                vents[idVentIndex].clearRanges();
                vents[idVentIndex].setLowerBoundRange(currentVent.getUpperBoundStart(),
                        currentVent.getUpperBoundEnd());
                vents[idVentIndex].setUpperBoundRange(currentVent.getUpperBoundStart(),
                        currentVent.getUpperBoundEnd());
            }
            if(isWithinLowerRange) {
                //Only lower bound range matches so it must be the right answer
                vents[idVentIndex].clearRanges();
                vents[idVentIndex].setLowerBoundRange(currentVent.getLowerBoundStart(),
                        currentVent.getLowerBoundEnd());
                vents[idVentIndex].setUpperBoundRange(currentVent.getLowerBoundStart(),
                        currentVent.getLowerBoundEnd());
            }
        }
        else {
            //Neither or both range(s) match so our movement is inaccurate
            //we must pick the answer that is the closest to our estimate
            int[] lowerOverlap = currentVent.getOverlappedLowerBoundRange(lowerBoundStart, lowerBoundEnd);
            int[] upperOverlap = currentVent.getOverlappedUpperBoundRange(upperBoundStart, upperBoundEnd);
            int lowerRangeLength = (lowerOverlap[1] - lowerOverlap[0]);
            int upperRangeLength = (upperOverlap[1] - upperOverlap[0]);
            if(lowerRangeLength > upperRangeLength) {
                vents[idVentIndex].clearRanges();
                vents[idVentIndex].setLowerBoundRange(currentVent.getLowerBoundStart(),
                        currentVent.getLowerBoundEnd());
                vents[idVentIndex].setUpperBoundRange(currentVent.getLowerBoundStart(),
                        currentVent.getLowerBoundEnd());
            }
            else if(lowerRangeLength < upperRangeLength) {
                vents[idVentIndex].clearRanges();
                vents[idVentIndex].setLowerBoundRange(currentVent.getUpperBoundStart(),
                        currentVent.getUpperBoundEnd());
                vents[idVentIndex].setUpperBoundRange(currentVent.getUpperBoundStart(),
                        currentVent.getUpperBoundEnd());
            }
            else {
                //If they are even pick both answers and start over next time
                vents[idVentIndex].clearRanges();
                vents[idVentIndex].setLowerBoundRange(currentVent.getLowerBoundStart(),
                        currentVent.getLowerBoundEnd());
                vents[idVentIndex].setUpperBoundRange(currentVent.getUpperBoundStart(),
                        currentVent.getUpperBoundEnd());
            }
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
    public int getFutureStabilityChange(UltimateVolcanicMineConfig.PredictionScenario scenario) {
        int totalVentValue = 0;
        ArrayList<VentStatus> estimatedVents = new ArrayList<>();
        for(int i = 0; i < NUM_VENTS; ++i) {
            if(!vents[i].isRangeDefined())
                return STARTING_VENT_VALUE;
            if(vents[i].isIdentified())
                totalVentValue += getSpecificVentUpdate(vents[i].getActualValue());
            else
                estimatedVents.add(vents[i]);
        }

        int estimatedVentValue = Integer.MAX_VALUE;
        for(int i = 0; i < estimatedVents.size(); ++i) {
            VentStatus vent = estimatedVents.get(i);
            int avgLower = (vent.getLowerBoundEnd() + vent.getLowerBoundStart()) / 2;
            int avgUpper = (vent.getUpperBoundStart() + vent.getUpperBoundEnd()) / 2;
            int ventUpdate = 0;

            switch(scenario) {
                case WORST_CASE:
                    ventUpdate = Math.max(getSpecificVentUpdate(avgLower), getSpecificVentUpdate(avgUpper));
                    break;
                case BEST_CASE:
                    ventUpdate = Math.min(getSpecificVentUpdate(avgLower), getSpecificVentUpdate(avgUpper));
                    break;
                default:
                    //Average-case (crap)
                    ventUpdate = (getSpecificVentUpdate(avgLower) + getSpecificVentUpdate(avgUpper)) / 2;
                    break;
            }

            if(estimatedVentValue == Integer.MAX_VALUE) estimatedVentValue = ventUpdate;
            else estimatedVentValue += ventUpdate;
        }

        if(estimatedVentValue != Integer.MAX_VALUE)
            totalVentValue += estimatedVentValue;
        return calcStabilityChange(totalVentValue);
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

        boolean isAWithinRange = !hasEstimatedARange || vents[0].isWithinRange(41, 59);
        boolean isBWithinRange = !hasEstimatedBRange || vents[1].isWithinRange(41, 59);
        boolean isCWithinRange = !hasEstimatedCRange || vents[2].isWithinRange(41, 59);
        switch(ventName) {
            case 'A':
                //A never freezes
                return false;

            case 'B':
                //B will only freeze if A is within 41-59% and B is also 41-59%
                if(isAWithinRange) return isBWithinRange;
                return false;

            case 'C':
                //C will always freeze if both A and B are 41-59%
                //C will also freeze if either A or B are 41-59% and C is 41-59%
                if(isAWithinRange && isBWithinRange) return true;
                if(isAWithinRange || isBWithinRange) return isCWithinRange;
                return false;
        }
        return true;
    }

    public final StatusState getCurrentState() { return currentState; }
    public final StatusState getPreviousState() { return previousState; }
    public final VentStatus[] getCurrentVents() { return vents; }
    public boolean areAnyVentIdentified() { return identifiedBitMask > 0; }
}
