package com.ultimatevm;

import static com.ultimatevm.StatusState.*;
import static com.ultimatevm.VentStatus.*;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

public class VentStatusPredicter {
    private static final String FILE_PATH =
            "C:\\Users\\cyanw\\IdeaProjects\\UltimateVolcanicMine\\src\\main\\resources\\game_log.txt";

    private StatusState displayState, currentState, previousState;

    FileWriter logWriter;
    public VentStatusPredicter() {
        try {
            logWriter = new FileWriter(FILE_PATH, false);
        } catch (IOException ignored) {

        }
        initialize();
    }
    public void initialize() {
        displayState = new StatusState();
        currentState = new StatusState();
        previousState = new StatusState();
        try {
            logWriter.close();
            logWriter = new FileWriter(FILE_PATH, false);
        } catch (IOException ignored) {

        }
    }
    public void reset() {
        displayState.doVMReset();
        currentState.doVMReset();
        previousState.doVMReset();
    }
    public void updateVentStatus(int[] ventStatus, int chambers) {
        processVentChangeState(displayState.updateVentStatus(ventStatus, chambers));
    }
    public void updateVentMovement() {
        displayState.updateVentMovement();
        currentState.updateVentMovement();
        previousState.updateVentMovement();
    }
    public void clearVentMovement() {
        displayState.clearVentMovement();
        currentState.clearVentMovement();
        previousState.clearVentMovement();
    }
    public void makeStatusState(int change) {
        previousState.calcPredictedVentValues(change);
        currentState.calcPredictedVentValues(change);

        int[] unknownVentIndices = displayState.getUnidentifiedVentIndices();
        if(currentState.getNumIdentifiedVents() == 2) {
            if (!fixRangesSingle(unknownVentIndices[0])) {
                displayState.getVents()[unknownVentIndices[0]].setEqualTo(currentState.getVents()[unknownVentIndices[0]]);
            }
        }
//        } else if(currentState.getNumIdentifiedVents() == 1) {
//            for(int i = 0; i < 2; ++i) {
//                if (!fixRangesDouble(unknownVentIndices[i])) {
//                    displayState.getVents()[unknownVentIndices[i]].setEqualTo(currentState.getVents()[unknownVentIndices[i]]);
//                }
//            }
//        }
        log();
        clearVentMovement();
    }
    public boolean fixRangesSingle(int idVentIndex) {
        VentStatus currentVent = currentState.getVents()[idVentIndex];
        VentStatus displayVent = displayState.getVents()[idVentIndex];
        //Check if calculated answers are outside our possible bounds
        //TODO: Keep track of the previous bounds for more accurate clipping
        //Add extra move rate to account
//        int totalBoundStart = displayVent.getTotalBoundStart() - BASE_MOVE_RATE;
//        if(totalBoundStart > currentVent.getLowerBoundEnd()) {
//            //Calculated lower bound is out of possible range
//            displayVent.clearRanges();
//            displayVent.setLowerBoundRange(currentVent.getUpperBoundStart(),
//                    currentVent.getUpperBoundEnd());
//            displayVent.setUpperBoundRange(currentVent.getUpperBoundStart(),
//                    currentVent.getUpperBoundEnd());
//            return true;
//        }
//        int totalBoundEnd = displayVent.getTotalBoundEnd() + BASE_MOVE_RATE;
//        if(totalBoundEnd < currentVent.getUpperBoundStart()) {
//            //Calculated upper bound is out of possible range
//            displayVent.clearRanges();
//            displayVent.setLowerBoundRange(currentVent.getLowerBoundStart(),
//                    currentVent.getLowerBoundEnd());
//            displayVent.setUpperBoundRange(currentVent.getLowerBoundStart(),
//                    currentVent.getLowerBoundEnd());
//            return true;
//        }

        int totalDirMove = currentVent.getTotalDirectionalMovement();
        //Fix our ranges to account for movement inaccuracies
        //EX: Update movement tick is off (pes move varies by -1 or +1)
        int lowerBoundStart = displayVent.getLowerBoundStart() - currentVent.getLowerBoundStartMove();
        int lowerBoundEnd = displayVent.getLowerBoundEnd() - currentVent.getLowerBoundEndMove();
        int upperBoundStart = displayVent.getUpperBoundStart() - currentVent.getUpperBoundStartMove();
        int upperBoundEnd = displayVent.getUpperBoundEnd() - currentVent.getUpperBoundEndMove();
        if(totalDirMove < 0) {
            //maximum possible estimated value
            lowerBoundStart += (currentVent.getLowerBoundStartMove() - BASE_MOVE_RATE);
            upperBoundStart += (currentVent.getUpperBoundStartMove() - BASE_MOVE_RATE);
            //minimum possible estimated value (account for truncation possibilities)
            //ex: 31-33 shifted to 32-34 its possible it moved from 33 to 32
            //31-33 shifted to 33-35 its possible its frozen at 33
            lowerBoundEnd += TRUNCATION_POSSIBILITIES;
            upperBoundEnd += TRUNCATION_POSSIBILITIES;
        }
        else if(totalDirMove > 0) {
            //maximum possible estimated value
            lowerBoundEnd += (currentVent.getLowerBoundEndMove() + BASE_MOVE_RATE);
            upperBoundEnd += (currentVent.getUpperBoundEndMove() + BASE_MOVE_RATE);
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
                displayVent.clearRanges();
                displayVent.setLowerBoundRange(currentVent.getUpperBoundStart(),
                        currentVent.getUpperBoundEnd());
                displayVent.setUpperBoundRange(currentVent.getUpperBoundStart(),
                        currentVent.getUpperBoundEnd());
            }
            if(isWithinLowerRange) {
                //Only lower bound range matches so it must be the right answer
                displayVent.clearRanges();
                displayVent.setLowerBoundRange(currentVent.getLowerBoundStart(),
                        currentVent.getLowerBoundEnd());
                displayVent.setUpperBoundRange(currentVent.getLowerBoundStart(),
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
                displayVent.clearRanges();
                displayVent.setLowerBoundRange(currentVent.getLowerBoundStart(),
                        currentVent.getLowerBoundEnd());
                displayVent.setUpperBoundRange(currentVent.getLowerBoundStart(),
                        currentVent.getLowerBoundEnd());
            }
            else if(lowerRangeLength < upperRangeLength) {
                displayVent.clearRanges();
                displayVent.setLowerBoundRange(currentVent.getUpperBoundStart(),
                        currentVent.getUpperBoundEnd());
                displayVent.setUpperBoundRange(currentVent.getUpperBoundStart(),
                        currentVent.getUpperBoundEnd());
            }
            else {
                //If they are even pick both answers and start over next time
                displayVent.clearRanges();
                displayVent.setLowerBoundRange(currentVent.getLowerBoundStart(),
                        currentVent.getLowerBoundEnd());
                displayVent.setUpperBoundRange(currentVent.getUpperBoundStart(),
                        currentVent.getUpperBoundEnd());
            }
        }
        return true;
    }
    public boolean fixRangesDouble(int idVentIndex) {
        VentStatus currentVent = currentState.getVents()[idVentIndex];
        VentStatus displayVent = displayState.getVents()[idVentIndex];

        int[] lowerOverlap = displayVent.getOverlappedLowerBoundRange(
                currentVent.getLowerBoundStart(),
                currentVent.getLowerBoundEnd());
        int[] upperOverlap = displayVent.getOverlappedUpperBoundRange(
                currentVent.getUpperBoundStart(),
                currentVent.getUpperBoundEnd());
        int lowerRangeLength = (lowerOverlap[1] - lowerOverlap[0]);
        int upperRangeLength = (upperOverlap[1] - upperOverlap[0]);

        displayVent.clearRanges();
        if(lowerRangeLength > 0 && upperRangeLength > 0) {
            displayVent.setLowerBoundRange(lowerOverlap[0],
                    lowerOverlap[1]);
            displayVent.setUpperBoundRange(upperOverlap[0],
                    upperOverlap[1]);
        }
        else if(lowerRangeLength > 0) {
            displayVent.setLowerBoundRange(lowerOverlap[0],
                    lowerOverlap[1]);
            displayVent.setUpperBoundRange(lowerOverlap[0],
                    lowerOverlap[1]);
        }
        else if(upperRangeLength > 0) {
            displayVent.setLowerBoundRange(upperOverlap[0],
                    upperOverlap[1]);
            displayVent.setUpperBoundRange(upperOverlap[0],
                    upperOverlap[1]);
        } else {
            //Hopefully not!
            //Gotta take the answer calced and start over!
            displayVent.setLowerBoundRange(currentVent.getLowerBoundStart(),
                    currentVent.getLowerBoundEnd());
            displayVent.setUpperBoundRange(currentVent.getUpperBoundStart(),
                    currentVent.getUpperBoundEnd());
        }
        return true;
    }
    public String getVentStatusText(int index, String startingText) {
        VentStatus[] vents = displayState.getVents();
        if(vents[index].isIdentified() || !vents[index].isRangeDefined()) return startingText;
        StringBuilder builder = new StringBuilder();
        builder.append(startingText, 0, 3);
        builder.append("<col=00ffff>");
//        if(vents[index].isTwoSeperateValues()) {
//            if(vents[index].isLowerBoundSingleValue())
//                builder.append(vents[index].getLowerBoundStart()).append("%");
//            else {
//                builder.append(vents[index].getLowerBoundStart()).append("-");
//                builder.append(vents[index].getLowerBoundEnd());
//            }
//            builder.append(" ");
//            if(vents[index].isUpperBoundSingleValue())
//                builder.append(vents[index].getUpperBoundStart()).append("%");
//            else {
//                builder.append(vents[index].getUpperBoundStart()).append("-");
//                builder.append(vents[index].getUpperBoundEnd());
//            }
//        } else {
//            if(vents[index].isLowerBoundSingleValue())
//                builder.append(vents[index].getLowerBoundStart()).append("%");
//            else {
//                builder.append(vents[index].getLowerBoundStart()).append("-");
//                builder.append(vents[index].getLowerBoundEnd()).append("%");
//            }
//        }
        builder.append(getVentPercentText(vents[index]));
        return builder.append("</col>").toString();
    }
    private String getVentPercentText(VentStatus vent) {
        StringBuilder builder = new StringBuilder();
        if(vent.isTwoSeperateValues()) {
            if(vent.isLowerBoundSingleValue())
                builder.append(vent.getLowerBoundStart()).append("%");
            else {
                builder.append(vent.getLowerBoundStart()).append("-");
                builder.append(vent.getLowerBoundEnd());
            }
            builder.append(" ");
            if(vent.isUpperBoundSingleValue())
                builder.append(vent.getUpperBoundStart()).append("%");
            else {
                builder.append(vent.getUpperBoundStart()).append("-");
                builder.append(vent.getUpperBoundEnd());
            }
        } else {
            if(vent.isLowerBoundSingleValue())
                builder.append(vent.getLowerBoundStart()).append("%");
            else {
                builder.append(vent.getLowerBoundStart()).append("-");
                builder.append(vent.getLowerBoundEnd()).append("%");
            }
        }
        return builder.toString();
    }
    private void processVentChangeState(VentChangeState[] changeStates) {
        int numUnchanged = 0;
        for(int i = 0; i < changeStates.length; ++i) {
            switch(changeStates[i]) {
                case IDENTIFIED:
                    //Update both current and previous state to match
                    currentState.setVentsEqualTo(displayState);
                    previousState.setVentsEqualTo(displayState);
                    return;
                case NO_CHANGE:
                    //TODO: Record how many ticks a specific vent has no change
                case BOUNDED:
                case UNIDENTIFIED:
                    ++numUnchanged;
                    break;
                case ONE_CHANGE:
                case TWO_CHANGE:
                    //TODO: Narrow down display states ranges accordingly
                    break;
            }
        }
        //Do nothing if the vents have not changed yet
        if(numUnchanged == 3) return;
        previousState.setVentsEqualTo(currentState);
        currentState.setVentsEqualTo(displayState);
    }

    public int getFutureStabilityChange(UltimateVolcanicMineConfig.PredictionScenario scenario) {
        int totalVentValue = 0;
        ArrayList<VentStatus> estimatedVents = new ArrayList<>();
        VentStatus[] vents = getDisplayState().getVents();
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
    public final StatusState getCurrentState() { return currentState; }
    public final StatusState getPreviousState() { return previousState; }
    public final StatusState getDisplayState() { return displayState; }
    public boolean areAnyVentIdentified() { return displayState.getNumIdentifiedVents() > 0; }
    public void log() {
        try {
            logWriter.write("-------------------------------------------------------------------------------" + "\n");
            logWriter.write("Previous " + "\n");
            logState(previousState);
            logWriter.write("Current " + "\n");
            logState(currentState);
            logWriter.write("Display " + "\n");
            logState(displayState);
        } catch (IOException ignored) {

        }
    }
    private void logState(StatusState state) {
        VentStatus[] vents = state.getVents();
        try {
            if(!vents[0].isIdentified()) {
                VentStatus AVent = vents[0];
                logWriter.write("A Bounds: " + AVent.getTotalBoundStart() + ", " + AVent.getTotalBoundEnd() + "\n");
                logWriter.write("Directional Move: " + AVent.getTotalDirectionalMovement() + "\n");
                logWriter.write("A Movement: " + AVent.getLowerBoundStartMove() + ", ");
                logWriter.write(AVent.getLowerBoundEndMove() + " | " + AVent.getUpperBoundStartMove());
                logWriter.write(", " + AVent.getUpperBoundEndMove() + "\n");
            }
            logWriter.write("A: " + getVentPercentText(vents[0]) + "\n");

            if(!vents[1].isIdentified()) {
                VentStatus BVent = vents[1];
                logWriter.write("B Bounds: " + BVent.getTotalBoundStart() + ", " + BVent.getTotalBoundEnd() + "\n");
                logWriter.write("Directional Move: " + BVent.getTotalDirectionalMovement() + "\n");
                logWriter.write("B Movement: " + BVent.getLowerBoundStartMove() + ", ");
                logWriter.write(BVent.getLowerBoundEndMove() + " | " + BVent.getUpperBoundStartMove());
                logWriter.write(", " + BVent.getUpperBoundEndMove() + "\n");
            }
            logWriter.write("B: " + getVentPercentText(vents[1]) + "\n");

            if(!vents[2].isIdentified()) {
                VentStatus CVent = vents[2];
                logWriter.write("C Bounds: " + CVent.getTotalBoundStart() + ", " + CVent.getTotalBoundEnd() + "\n");
                logWriter.write("Directional Move: " + CVent.getTotalDirectionalMovement() + "\n");
                logWriter.write("C Movement: " + CVent.getLowerBoundStartMove() + ", ");
                logWriter.write(CVent.getLowerBoundEndMove() + " | " + CVent.getUpperBoundStartMove());
                logWriter.write(", " + CVent.getUpperBoundEndMove() + "\n");
            }
            logWriter.write("C: " + getVentPercentText(vents[2]) + "\n");

            logWriter.write("Stability Change: " + state.getStabilityChange() + "\n");
            logWriter.write("\n");
            logWriter.flush();
        } catch (IOException ignored) {

        }
    }
}
