package com.ultimatevm;

import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;

public class VentStatusTimeline {
    //Constants
    public static final int VENT_MOVE_TICK_TIME = 10;
    public static final int STABILITY_UPDATE_TICK_TIME = 25;
    public static final int VM_GAME_FULL_TIME = 1000;
    public static final int VM_GAME_RESET_TIME = 500;

    //Flags
    public static final int STABILITY_UPDATE_FLAG = 10;
    public static final int MOVEMENT_UPDATE_FLAG = 9;
    public static final int IDENTIFIED_VENT_FLAG = 8;
    public static final int DIRECTION_CHANGED_FLAG = 7;

    //Masks
    public static final int IDENTIFIED_BIT_MASK = 7;
    public static final int DIRECTION_CHANGED_BIT_MASK = 7 << 3;

    private static final String FILE_PATH =
            "C:\\Users\\cyanw\\IdeaProjects\\UltimateVolcanicMine\\src\\main\\resources\\game_log.txt";
    FileWriter logWriter;

    private int currentTick, startingTick;
    private short[] timeline;
    private int[] identifiedVentTick;
    private StatusState[] identifiedVentStates;
    private int numIdentifiedVents;
    HashMap<Integer, StatusState> tickToMovementVentState;
    HashMap<Integer, StatusState> tickToStabilityUpdateState;

    public VentStatusTimeline() {
        createLog();
        initialize();
    }
    public void initialize() {
        startLog();
        currentTick = 0;
        timeline = new short[VM_GAME_FULL_TIME];
        tickToMovementVentState = new HashMap<>();
        tickToStabilityUpdateState = new HashMap<>();
        reset();
    }
    public void reset() {
        startingTick = currentTick;
        numIdentifiedVents = 0;
        identifiedVentTick = new int[StatusState.NUM_VENTS];
        identifiedVentStates = new StatusState[StatusState.NUM_VENTS];
        for(int i = 0; i < StatusState.NUM_VENTS; ++i) {
            identifiedVentTick[i] = -1;
            identifiedVentStates[i] = null;
        }
    }
    public boolean addInitialState(StatusState startingState) {
        //Only add initial state once for pre reset and post reset
        if(tickToStabilityUpdateState.containsKey(startingTick)) return false;
        StatusState newState = new StatusState(startingState);
        tickToStabilityUpdateState.put(startingTick, newState);
        return true;
    }
    public void addIdentifiedVentTick(StatusState currentState, int bitState) {
        for(int i = 0; i < StatusState.NUM_VENTS; ++i) {
            if(identifiedVentTick[i] != -1) continue;
            if ((bitState & (1 << i)) != 0) {
                timeline[currentTick] |= (1 << IDENTIFIED_VENT_FLAG);
                timeline[currentTick] |= bitState & IDENTIFIED_BIT_MASK;
                ++numIdentifiedVents;
                identifiedVentStates[i] = new StatusState(currentState);
                identifiedVentTick[i] = currentTick;
            }
        }
//        processIdentifiedVent();
    }
//    private void processIdentifiedVent() {
//        //Exit if our movement tick has not been defined yet
//        if(currentMovementUpdateTick == 0) return;
//        //Exit if all three vents are identified (defeats purpose of prediction)
//        if(numIdentifiedVents == StatusState.NUM_VENTS) return;
//        //Go back in time and determine what the vent values would have been
//        for(int i = 0; i < StatusState.NUM_VENTS; ++i) {
//            //Skip unidentified vents
//            if(identifiedVentTick[i] == -1) continue;
//            //Skip processed vents
//            if((identifiedVentTick[i] & (1 << (IDENTIFIED_VENT_FLAG * 2))) == 0)
//                continue;
//
//            //Mark that this identified vent has been processed
//            identifiedVentTick[i] &= ~(1 << (IDENTIFIED_VENT_FLAG * 2));
//            fixPreviousUnidentifiedVents(i);
//        }
//    }
    private void fixPreviousUnidentifiedVents(int ventIndex) {
        StatusState fixedState = new StatusState(identifiedVentStates[ventIndex]);
        fixedState.clearPredictedVentValues();
        int i = identifiedVentTick[ventIndex];
        for(; i > startingTick; --i) {
            //Process all events in reverse order since we are going backwards
            if((timeline[i] & (1 << STABILITY_UPDATE_FLAG)) != 0) {
                //Update this stability state with our new identified vent
                StatusState stabilityState = tickToStabilityUpdateState.get(i);
                stabilityState.setVentsEqualTo(fixedState);
                stabilityState.calcPredictedVentValues(stabilityState.getStabilityChange());
            }
            if((timeline[i] & (1 << MOVEMENT_UPDATE_FLAG)) != 0) {
                //Update our movement backwards
                fixedState.doActualBackwardMovement();
            }
            if((timeline[i] & (1 << DIRECTION_CHANGED_FLAG)) != 0) {
                //Change our direction if it occured this tick
                changeStateDirection(fixedState, i);
            }
        }
        //Finally fix our initial starting state
        tickToStabilityUpdateState.get(i).setVentsEqualTo(fixedState);
    }
    public void addDirectionChangeTick(int bitState) {
        timeline[currentTick] |= (bitState & DIRECTION_CHANGED_BIT_MASK);
        timeline[currentTick] |= (1 << DIRECTION_CHANGED_FLAG);
    }
    public void addMovementTick(StatusState currentState) {
        addNewMovementTickState(currentTick, currentState);
//        addPreviousPossibleMovementTicks(currentState);
//        processIdentifiedVent();
    }
//    private void addPreviousPossibleMovementTicks(StatusState currentState) {
//        //We will assume that there are no skipped movement updates
//        //Account for the fact is possible for the movement tick to shift
//        int movementTick = currentMovementUpdateTick - VENT_MOVE_TICK_TIME;
//        while(movementTick > prevMovementUpdateTick) {
//            addNewMovementTickState(movementTick, currentState);
//            movementTick -= VENT_MOVE_TICK_TIME;
//        }
//    }
    public void addStabilityUpdateTick(StatusState currentState, int change) {
        currentState.calcPredictedVentValues(change);
        addNewStabilityUpdateTickState(currentTick, currentState);
    }
    public StatusState getTimelinePredictionState() {
        StatusState predictedState = new StatusState(tickToStabilityUpdateState.get(startingTick));
        for(int i = startingTick+1; i <= currentTick; ++i) {
            if((timeline[i] & (1 << DIRECTION_CHANGED_FLAG)) != 0) {
                //Change our direction if it occured this tick
                changeStateDirection(predictedState, i);
            }
            if((timeline[i] & (1 << MOVEMENT_UPDATE_FLAG)) != 0) {
                //Update our vent values
                predictedState.doActualVentMovement();
            }
            if((timeline[i] & (1 << STABILITY_UPDATE_FLAG)) != 0) {
                //Use stability updates to set/narrow our possible values
                StatusState tempState = new StatusState(predictedState);
                tempState.calcPredictedVentValues(tickToStabilityUpdateState.get(i).getStabilityChange());
                tempState.doBoundsClipping();
                predictedState.setOverlappingRangesWith(tempState);
            }
        }
        return predictedState;
    }

    //Helpers
    private void addNewMovementTickState(int tick, StatusState currentState) {
        StatusState newState = new StatusState(currentState);
        tickToMovementVentState.put(tick, newState);
        timeline[tick] |= (1 << MOVEMENT_UPDATE_FLAG);
    }
    private void addNewStabilityUpdateTickState(int tick, StatusState currentState) {
        StatusState newState = new StatusState(currentState);
        tickToStabilityUpdateState.put(currentTick, newState);
        timeline[tick] |= (1 << STABILITY_UPDATE_FLAG);
    }
    private void changeStateDirection(StatusState state, int tick) {
        int directionFlags = timeline[tick] & DIRECTION_CHANGED_BIT_MASK;
        directionFlags >>= 3;
        if((directionFlags & 1) != 0) state.getVents()[0].flipDirection();
        if((directionFlags & 2) != 0) state.getVents()[1].flipDirection();
        if((directionFlags & 4) != 0) state.getVents()[2].flipDirection();
    }

    //Accessors
    public int getCurrentTick() { return currentTick; }
    public int getCurrentStartingTick() {return startingTick;}
    public int getNumIdentifiedVents() { return numIdentifiedVents; }
    public final short[] getTimeline() { return timeline; }
    public final int[] getIdentifiedVentTicks() { return identifiedVentTick; }
    public final StatusState[] getIdentifiedVentStates() { return identifiedVentStates; }
    public final HashMap<Integer, StatusState> getMovementVentStates() { return tickToMovementVentState; }
    public final HashMap<Integer, StatusState> getStabilityUpdateStates() { return tickToStabilityUpdateState; }

    //Modifiers
    public void updateTick() { ++currentTick; }

    //Logging
    private void createLog() {
        try {
            logWriter = new FileWriter(FILE_PATH, false);
        } catch (IOException ignored) {

        }
    }
    private void startLog() {
        try {
            logWriter.close();
            logWriter = new FileWriter(FILE_PATH, false);
        } catch (IOException ignored) {

        }
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
