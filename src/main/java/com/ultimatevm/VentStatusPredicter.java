package com.ultimatevm;

import static com.ultimatevm.StatusState.*;
import static com.ultimatevm.VentStatus.*;

import net.runelite.api.Client;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

public class VentStatusPredicter {

    private static final char[] VENT_TAGS = {'A', 'B', 'C'};
    private static final int STABILITY_CHANGE_CONSTANT = 25;
    private static final int TRUNCATION_POSSIBILITIES = NUM_VENTS - 1;
    private static final String FILE_PATH =
            "C:\\Users\\cyanw\\IdeaProjects\\UltimateVolcanicMine\\src\\main\\resources\\game_log.txt";

    private VentStatus vents[] = new VentStatus[NUM_VENTS];
    private StatusState currentState, previousState;
    private StatusState preFrameState;
    private int identifiedBitMask;
    private boolean hasReset = false;

    FileWriter logWriter;
    public VentStatusPredicter() {
        try {
            logWriter = new FileWriter(FILE_PATH, false);
        } catch (IOException ignored) {

        }
        initialize();
    }
    public void initialize() {
        hasReset = false;
        for(int i = 0; i < vents.length; ++i) {
            vents[i] = new VentStatus(VENT_TAGS[i]);
        }
        clearStabilityStates();

        try {
            logWriter.close();
            logWriter = new FileWriter(FILE_PATH, false);
        } catch (IOException ignored) {

        }

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
        int updatedVents = 0, currentVentInfluence = 0;
        for(int i = 0; i < vents.length; ++i) {
            if(vents[i].isRangeDefined()) ++updatedVents;
            vents[i].updateMovement(currentVentInfluence);
            currentVentInfluence += vents[i].getEstimatedInfluence();
        }
        return updatedVents > 0;
    }
    public void clearVentMovement() {
        for(int i = 0; i < vents.length; ++i) {
            vents[i].clearMovement();
        }
    }
    public void makePreFrameState(int change) {
        if(preFrameState == null) preFrameState = new StatusState(vents, change);
        preFrameState.update(vents, change);
        int[] unknownVentIndices = preFrameState.getUnidentifiedVentIndices();
        if(unknownVentIndices.length == 0) return;
        VentStatus currentVent = preFrameState.getVent(unknownVentIndices[0]);
        calcSingleVentValue(currentVent, change);
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

        if(currentState.isAllVentsIdentified() || !currentState.isEnoughVentsIdentified()) return;

        int[] unknownVentIndices = currentState.getUnidentifiedVentIndices();

        if(currentState.getNumIdentifiedVents() == 2) {
            VentStatus currentVent = currentState.getVent(unknownVentIndices[0]);
            calcSingleVentValue(currentVent, change);
            if (!fixRangesSingle(unknownVentIndices[0])) {
                vents[unknownVentIndices[0]].setEqualTo(currentVent);
                clearVentMovement();
            }
        } else if(currentState.getNumIdentifiedVents() == 1) {
            VentStatus[] currentVents = new VentStatus[]{
                    currentState.getVent(unknownVentIndices[0]),
                    currentState.getVent(unknownVentIndices[1])};
            calcDoubleVentValue(currentVents, change);
            for(int i = 0; i < 2; ++i) {
                if (!fixRangesDouble(unknownVentIndices[i])) {
                    vents[unknownVentIndices[i]].setEqualTo(currentVents[i]);
                }
            }
            clearVentMovement();
        }
    }
    public boolean fixRangesSingle(int idVentIndex) {
        if(previousState == null) return false;
        VentStatus previousVent = previousState.getVent(idVentIndex);
        if(!previousVent.isRangeDefined()) return false;
        VentStatus currentVent = currentState.getVent(idVentIndex);
        //Check if calculated answers are outside our possible bounds
        int totalBoundStart = vents[idVentIndex].getTotalBoundStart();// - BASE_MOVE_RATE;
        if(totalBoundStart > currentVent.getLowerBoundEnd()) {
            //Calculated lower bound is out of possible range
            vents[idVentIndex].clearRanges();
            vents[idVentIndex].setLowerBoundRange(currentVent.getUpperBoundStart(),
                    currentVent.getUpperBoundEnd());
            vents[idVentIndex].setUpperBoundRange(currentVent.getUpperBoundStart(),
                    currentVent.getUpperBoundEnd());
            return true;
        }
        int totalBoundEnd = vents[idVentIndex].getTotalBoundEnd();// + BASE_MOVE_RATE;
        if(totalBoundEnd < currentVent.getUpperBoundStart()) {
            //Calculated upper bound is out of possible range
            vents[idVentIndex].clearRanges();
            vents[idVentIndex].setLowerBoundRange(currentVent.getLowerBoundStart(),
                    currentVent.getLowerBoundEnd());
            vents[idVentIndex].setUpperBoundRange(currentVent.getLowerBoundStart(),
                    currentVent.getLowerBoundEnd());
            return true;
        }

        int totalDirMove = currentVent.getTotalDirectionalMovement();
        //Fix our ranges to account for movement inaccuracies
        //EX: Update movement tick is off (pes move varies by -1 or +1)
        int lowerBoundStart = vents[idVentIndex].getLowerBoundStart() - vents[idVentIndex].getLowerBoundStartMove();
        int lowerBoundEnd = vents[idVentIndex].getLowerBoundEnd() - vents[idVentIndex].getLowerBoundEndMove();
        int upperBoundStart = vents[idVentIndex].getUpperBoundStart() - vents[idVentIndex].getUpperBoundStartMove();
        int upperBoundEnd = vents[idVentIndex].getUpperBoundEnd() - vents[idVentIndex].getUpperBoundEndMove();
        if(totalDirMove < 0) {
            //maximum possible estimated value
            lowerBoundStart += (vents[idVentIndex].getLowerBoundStartMove() - BASE_MOVE_RATE);
            upperBoundStart += (vents[idVentIndex].getUpperBoundStartMove() - BASE_MOVE_RATE);
            //minimum possible estimated value (account for truncation possibilities)
            //ex: 31-33 shifted to 32-34 its possible it moved from 33 to 32
            //31-33 shifted to 33-35 its possible its frozen at 33
            lowerBoundEnd += TRUNCATION_POSSIBILITIES;
            upperBoundEnd += TRUNCATION_POSSIBILITIES;
        }
        else if(totalDirMove > 0) {
            //maximum possible estimated value
            lowerBoundEnd += (vents[idVentIndex].getLowerBoundEndMove() + BASE_MOVE_RATE);
            upperBoundEnd += (vents[idVentIndex].getUpperBoundEndMove() + BASE_MOVE_RATE);
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
    public boolean fixRangesDouble(int idVentIndex) {
        if(previousState == null) return false;
        VentStatus previousVent = previousState.getVent(idVentIndex);
        if(!previousVent.isRangeDefined()) return false;

        VentStatus currentVent = currentState.getVent(idVentIndex);
        int[] lowerOverlap = vents[idVentIndex].getOverlappedLowerBoundRange(
                currentVent.getLowerBoundStart(),
                currentVent.getLowerBoundEnd());
        int[] upperOverlap = vents[idVentIndex].getOverlappedUpperBoundRange(
                currentVent.getUpperBoundStart(),
                currentVent.getUpperBoundEnd());
        int lowerRangeLength = (lowerOverlap[1] - lowerOverlap[0]);
        int upperRangeLength = (upperOverlap[1] - upperOverlap[0]);

        vents[idVentIndex].clearRanges();
        if(lowerRangeLength > 0 && upperRangeLength > 0) {
            vents[idVentIndex].setLowerBoundRange(lowerOverlap[0],
                    lowerOverlap[1]);
            vents[idVentIndex].setUpperBoundRange(upperOverlap[0],
                    upperOverlap[1]);
        }
        else if(lowerRangeLength > 0) {
            vents[idVentIndex].setLowerBoundRange(lowerOverlap[0],
                    lowerOverlap[1]);
            vents[idVentIndex].setUpperBoundRange(lowerOverlap[0],
                    lowerOverlap[1]);
        }
        else if(upperRangeLength > 0) {
            vents[idVentIndex].setLowerBoundRange(upperOverlap[0],
                    upperOverlap[1]);
            vents[idVentIndex].setUpperBoundRange(upperOverlap[0],
                    upperOverlap[1]);
        } else {
            //Hopefully not!
            //Gotta take the answer calced and start over!
            vents[idVentIndex].setLowerBoundRange(currentVent.getLowerBoundStart(),
                    currentVent.getLowerBoundEnd());
            vents[idVentIndex].setUpperBoundRange(currentVent.getUpperBoundStart(),
                    currentVent.getUpperBoundEnd());
        }
        return true;
    }
    public String getVentStatusText(int index, String startingText) {
        if(vents[index].isIdentified() || !vents[index].isRangeDefined()) return startingText;
        StringBuilder builder = new StringBuilder();
        builder.append(startingText, 0, 3);
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
        preFrameState = currentState = previousState = null;
    }
    public int getFutureStabilityChange(UltimateVolcanicMineConfig.PredictionScenario scenario) {
        int totalVentValue = 0;
        ArrayList<VentStatus> estimatedVents = new ArrayList<>();
        for(int i = 0; i < NUM_VENTS; ++i) {
            if(!vents[i].isRangeDefined())
                return STARTING_VENT_VALUE;
            if(vents[i].isIdentified())
                totalVentValue += vents[i].getStabilityInfluence();
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
                    ventUpdate = Math.max(getVentStabilityInfluence(avgLower), getVentStabilityInfluence(avgUpper));
                    break;
                case BEST_CASE:
                    ventUpdate = Math.min(getVentStabilityInfluence(avgLower), getVentStabilityInfluence(avgUpper));
                    break;
                default:
                    //Average-case (crap)
                    ventUpdate = (getVentStabilityInfluence(avgLower) + getVentStabilityInfluence(avgUpper)) / 2;
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
            totalVentUpdate += vents[i].getStabilityInfluence();
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
        unIdVent.clearRanges();
        unIdVent.setLowerBoundRange(lowerBoundStart, lowerBoundEnd);
        unIdVent.setUpperBoundRange(upperBoundStart, upperBoundEnd);
    }
    public void calcDoubleVentValue(VentStatus[] unIdVent, int change) {
        int partialVentUpdate = getIdentifiedVentTotalValue();
        int missingVentUpdate = getTotalVentUpdate(change) - partialVentUpdate;
        int maxDistance = Math.min(MAX_VENT_VALUE - PERFECT_VENT_VALUE, missingVentUpdate);
        int minDistance = Math.max(0, missingVentUpdate - PERFECT_VENT_VALUE);

        int lowerBoundStart = PERFECT_VENT_VALUE - maxDistance;
        int lowerBoundEnd = PERFECT_VENT_VALUE - minDistance;
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
    public void log() {
        try {
            logWriter.write("-------------------------------------------------------------------------------" + "\n");
            logWriter.write("Pre State " + "\n");
            logState(preFrameState);
            logWriter.write("Post State " + "\n");
            logState(currentState);
        } catch (IOException ignored) {

        }
    }
    private void logState(StatusState state) {
        try {
            if(!vents[0].isIdentified()) {
                VentStatus AVent = state.getVent(0);
                logWriter.write("A Movement: " + AVent.getLowerBoundStartMove() + ", ");
                logWriter.write(AVent.getLowerBoundEndMove() + " | " + AVent.getUpperBoundStartMove());
                logWriter.write(", " + AVent.getUpperBoundEndMove() + "\n");
            }
            logWriter.write(getVentStatusText(0, "A: " +
                    vents[0].getActualValue()) + "\n");

            if(!vents[1].isIdentified()) {
                VentStatus BVent = state.getVent(1);
                logWriter.write("A Movement: " + BVent.getLowerBoundStartMove() + ", ");
                logWriter.write(BVent.getLowerBoundEndMove() + " | " + BVent.getUpperBoundStartMove());
                logWriter.write(", " + BVent.getUpperBoundEndMove() + "\n");
            }
            logWriter.write(getVentStatusText(1, "B: " +
                    vents[1].getActualValue()) + "\n");

            if(!vents[2].isIdentified()) {
                VentStatus CVent = state.getVent(2);
                logWriter.write("A Movement: " + CVent.getLowerBoundStartMove() + ", ");
                logWriter.write(CVent.getLowerBoundEndMove() + " | " + CVent.getUpperBoundStartMove());
                logWriter.write(", " + CVent.getUpperBoundEndMove() + "\n");
            }
            logWriter.write(getVentStatusText(2, "C: " +
                    vents[2].getActualValue()) + "\n");

            logWriter.write("Stability Change: " + state.getStabilityChange() + "\n");
            logWriter.write("\n");
            logWriter.flush();
        } catch (IOException ignored) {

        }
    }
}
