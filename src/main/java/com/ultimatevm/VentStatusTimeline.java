package com.ultimatevm;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;

public class VentStatusTimeline {
    //Constants
    public static final int VENT_MOVE_TICK_TIME = 10;
    public static final int STABILITY_UPDATE_TICK_TIME = 25;
    public static final int VM_GAME_FULL_TIME = 1000;
    public static final int VM_GAME_RESET_TIME = 500;

    //Flags
    public static final int EARTHQUAKE_EVENT_FLAG = 11;
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
    private int currentMovementTick;
    private short[] timeline;
    private int[] identifiedVentTick;
    private StatusState[] identifiedVentStates;
    private int numIdentifiedVents;
    StatusState initialState;
    StabilityUpdateInfo initialStabInfo;
    HashMap<Integer, StatusState> tickToMovementVentState;
    HashMap<Integer, StabilityUpdateInfo> tickToStabilityUpdateState;

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
        currentMovementTick = startingTick = currentTick;
        numIdentifiedVents = 0;
        initialState = null;
        initialStabInfo = null;
        identifiedVentTick = new int[StatusState.NUM_VENTS];
        identifiedVentStates = new StatusState[StatusState.NUM_VENTS];
        for(int i = 0; i < StatusState.NUM_VENTS; ++i) {
            identifiedVentTick[i] = -1;
            identifiedVentStates[i] = null;
        }
    }
    public boolean addInitialState(StatusState startingState) {
        //Only add initial state once for pre reset and post reset
        if(initialState != null) return false;
        initialState = new StatusState(startingState);
        return true;
    }
    public void addIdentifiedVentTick(StatusState currentState, int bitState) {
        int ventIndex = -1;
        for(int i = 0; i < StatusState.NUM_VENTS; ++i) {
            if(identifiedVentTick[i] != -1) continue;
            if ((bitState & (1 << i)) != 0) {
                timeline[currentTick] |= (1 << IDENTIFIED_VENT_FLAG);
                timeline[currentTick] |= bitState & IDENTIFIED_BIT_MASK;
                ++numIdentifiedVents;
                identifiedVentStates[i] = new StatusState(currentState);
                identifiedVentTick[i] = currentTick;
                ventIndex = i;
            }
        }

        if(numIdentifiedVents == 3 || ventIndex == -1) return;
        //Backtrack and fill out missing vent values
        updatePreviousVentValues(identifiedVentStates[ventIndex], currentTick);
    }
    private void updatePreviousVentValues(StatusState startingState, int tick) {
        StatusState curState = new StatusState(startingState);
        LinkedList<Integer> stabilityUpdateTicks = new LinkedList<>();
        int numTicksNoMovement = 0, futureMovementTick = Integer.MAX_VALUE;
        for(int i = tick; i >= startingTick; --i) {
            //Exit when there is a chain of missing movement updates
            if(numTicksNoMovement > (VENT_MOVE_TICK_TIME * 2)) break;

            if((timeline[i] & (1 << STABILITY_UPDATE_FLAG)) != 0) {
                //If future movement occured within 10 ticks we can process this now
                if(futureMovementTick - i <= VENT_MOVE_TICK_TIME) {
                    StabilityUpdateInfo stabilityInfo = tickToStabilityUpdateState.get(i);
                    stabilityInfo.updateVentValues(curState);
                    setInitialStabilityUpdateInfo(stabilityInfo);
                }
                //otherwise we have to process this during the previous movement update
                else stabilityUpdateTicks.addLast(i);
            }

            if((timeline[i] & (1 << MOVEMENT_UPDATE_FLAG)) != 0) {
                numTicksNoMovement = 0;
                futureMovementTick = i;
                //update the future stability state
                if(!stabilityUpdateTicks.isEmpty()) {
                    StabilityUpdateInfo stabilityInfo = tickToStabilityUpdateState.get(stabilityUpdateTicks.getFirst());
                    stabilityInfo.updateVentValues(curState);
                    setInitialStabilityUpdateInfo(stabilityInfo);
                    stabilityUpdateTicks.removeFirst();
                }
                //update the movement state
                StatusState movementState = tickToMovementVentState.get(i);
                movementState.setVentsEqualTo(curState);
                //exit if we can longer reverse the movement
                if(!reverseMovement(curState, i-1)) break;
            } else ++numTicksNoMovement;

            if(isEarthquakeDelayMovement(i)) numTicksNoMovement = 0;

            if((timeline[i] & (1 << DIRECTION_CHANGED_FLAG)) != 0) {
                //Change our direction if it occured this tick
                changeStateDirection(curState, i);
            }
        }
    }

    public void addDirectionChangeTick(int bitState) {
        timeline[currentTick] |= (bitState & DIRECTION_CHANGED_BIT_MASK);
        timeline[currentTick] |= (1 << DIRECTION_CHANGED_FLAG);
    }
    public void addEarthquakeEventTick() {
        timeline[currentTick] |= (1 << EARTHQUAKE_EVENT_FLAG);
    }
    public void addMovementTick(StatusState currentState) {
        addNewMovementTickState(currentTick, currentState);
        //Update previous values on the very first movement update (likely after a vent check)
        if(currentMovementTick == startingTick) {
            updatePreviousVentValues(currentState, currentTick);
        }
        currentMovementTick = currentTick;
    }
    public void addStabilityUpdateTick(StatusState currentState, int change) {
        addNewStabilityUpdateTickState(currentTick, currentState, change);
    }

    public StatusState getTimelinePredictionState() {
        StatusState predictedState = new StatusState(initialState);
        for(int i = startingTick+1; i <= currentTick; ++i) {
            if((timeline[i] & (1 << IDENTIFIED_VENT_FLAG)) != 0) {
                int idFlags = timeline[i] & IDENTIFIED_BIT_MASK;
                if((idFlags & 1) != 0) {
                    predictedState.setVentEqualTo(identifiedVentStates[0], 0);
                }
                if((idFlags & 2) != 0) {
                    predictedState.setVentEqualTo(identifiedVentStates[1], 1);
                }
                if((idFlags & 4) != 0) {
                    predictedState.setVentEqualTo(identifiedVentStates[2], 2);
                }
            }
            if((timeline[i] & (1 << DIRECTION_CHANGED_FLAG)) != 0) {
                //Change our direction if it occured this tick
                changeStateDirection(predictedState, i);
            }
            if((timeline[i] & (1 << MOVEMENT_UPDATE_FLAG)) != 0) {
                //Update our estimated vent values
                syncWithMovementState(predictedState, i);
                predictedState.updateVentMovement();
            }
            if((timeline[i] & (1 << STABILITY_UPDATE_FLAG)) != 0) {
                //Use stability updates to set/narrow our possible values
                StabilityUpdateInfo stabilityInfo = tickToStabilityUpdateState.get(i);
                if(stabilityInfo == initialStabInfo)
                    predictedState.mergePredictedRangesWith(initialStabInfo.getStabilityUpdateState());
                else stabilityInfo.updatePredictedState(predictedState);
            }
        }
        return predictedState;
    }
    public StatusState getCurrentPredictionState() {
        return StabilityUpdateInfo.getPredictionState(initialStabInfo, this);
    }

    //Helpers
    private void addNewMovementTickState(int tick, StatusState currentState) {
        StatusState newState = new StatusState(currentState);
        tickToMovementVentState.put(tick, newState);
        timeline[tick] |= (1 << MOVEMENT_UPDATE_FLAG);
    }
    private void addNewStabilityUpdateTickState(int tick, StatusState currentState, int change) {
        StabilityUpdateInfo newInfo = new StabilityUpdateInfo(currentState, tick, change);
        tickToStabilityUpdateState.put(currentTick, newInfo);
        timeline[tick] |= (1 << STABILITY_UPDATE_FLAG);
        setInitialStabilityUpdateInfo(newInfo);
    }
    private void setInitialStabilityUpdateInfo(StabilityUpdateInfo info) {
        if(!info.isValid()) return;
        if(initialStabInfo != null) {
            //TODO: account for different number of known vents
            if(initialStabInfo.getTickTimeStamp() > info.getTickTimeStamp())
                initialStabInfo = info;
        }
        else initialStabInfo = info;
    }
    private void changeStateDirection(StatusState state, int tick) {
        int directionFlags = timeline[tick] & DIRECTION_CHANGED_BIT_MASK;
        directionFlags >>= 3;
        if((directionFlags & 1) != 0) state.getVents()[0].flipDirection();
        if((directionFlags & 2) != 0) state.getVents()[1].flipDirection();
        if((directionFlags & 4) != 0) state.getVents()[2].flipDirection();
    }
    private void syncWithMovementState(StatusState state, int tick) {
        StatusState moveState = tickToMovementVentState.get(tick);
        for(int i = 0; i < StatusState.NUM_VENTS; ++i) {
            if(!moveState.getVents()[i].isIdentified()) continue;
            state.setVentEqualTo(moveState, i);
        }
    }
    private boolean isEarthquakeDelayMovement(int tick) {
        if((timeline[tick] & (1 << EARTHQUAKE_EVENT_FLAG)) == 0) return false;

        //Check if next 10 ticks was a movement update
        if(tick + VENT_MOVE_TICK_TIME <= VM_GAME_FULL_TIME) {
            if ((timeline[tick + VENT_MOVE_TICK_TIME] & (1 << MOVEMENT_UPDATE_FLAG)) != 0)
                return true;
        }
        //Check if previous 10 ticks was a movement update
        if(tick - VENT_MOVE_TICK_TIME >= startingTick) {
            return (timeline[tick - VENT_MOVE_TICK_TIME] & (1 << MOVEMENT_UPDATE_FLAG)) != 0;
        }
        return false;
    }
    private boolean reverseMovement(StatusState curState, int tick) {
        //Check what vent values are known from the previous movement tick
        StatusState prevMoveTickState = null;
        int numTicksNoMovement = 0;
        for(int i = tick; i >= startingTick; --i) {
            //Exit when there is a chain of missing movement updates
            if(numTicksNoMovement > (VENT_MOVE_TICK_TIME * 2)) break;

            if((timeline[i] & (1 << IDENTIFIED_VENT_FLAG)) != 0) {
                int idFlags = timeline[i] & IDENTIFIED_BIT_MASK;
                if((idFlags & 1) != 0) {
                    prevMoveTickState = identifiedVentStates[0];
                }
                if((idFlags & 2) != 0) {
                    prevMoveTickState = identifiedVentStates[1];
                }
                if((idFlags & 4) != 0) {
                    prevMoveTickState = identifiedVentStates[2];
                }
                break;
            }

            if((timeline[i] & (1 << MOVEMENT_UPDATE_FLAG)) != 0) {
                prevMoveTickState = tickToMovementVentState.get(i);
                break;
            } else ++numTicksNoMovement;

            if(isEarthquakeDelayMovement(i)) numTicksNoMovement = 0;
        }

        //Set and mark known values
        int knownBitFlag = 0, unknownBitMask = 0;
        if(prevMoveTickState != null) {
            for(int i = 0; i < StatusState.NUM_VENTS; ++i) {
                VentStatus prevVent = prevMoveTickState.getVents()[i];
                VentStatus curVent = curState.getVents()[i];
                //Both identified we already know the reverse state
                if(curVent.isIdentified() && prevVent.isIdentified()) {
                    knownBitFlag |= (1 << i);
                    curState.setVentEqualTo(prevMoveTickState, i);
                }
                if(!curVent.isIdentified() && !prevVent.isIdentified()) {
                    knownBitFlag |= (1 << i);
                    unknownBitMask |= (1 << i);
                }
            }
        }
        //If the full previous state is known to us no need to reverse
        if(knownBitFlag == 7) return true;

        //TODO: Fix this code later to work with estimated ranges
        int result = curState.reverseMovement(knownBitFlag & (~unknownBitMask));
        //A failed to reverse always exit
        if(result == -1) return false;
        //B failed to reverse
        else if(result == -2) {
            //If only A is identified ignore this
            if(numIdentifiedVents == 1 && identifiedVentTick[0] != -1) return true;
            //Otherwise treat B, C, AB, AC, BC identification as failure
            return false;
        }
        //C failed to reverse
        else if(result == -3) {
            //Typically do not care unless its known and bounded
        }
        return true;
    }

    //Accessors
    public int getCurrentTick() { return currentTick; }
    public int getCurrentStartingTick() {return startingTick;}
    public int getNumIdentifiedVents() { return numIdentifiedVents; }
    public final short[] getTimeline() { return timeline; }
    public final int[] getIdentifiedVentTicks() { return identifiedVentTick; }
    public final StatusState[] getIdentifiedVentStates() { return identifiedVentStates; }
    public final StatusState getInitialState() { return initialState; }
    public final HashMap<Integer, StatusState> getMovementVentStates() { return tickToMovementVentState; }
    public final HashMap<Integer, StabilityUpdateInfo> getStabilityUpdateStates() { return tickToStabilityUpdateState; }

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
            logWriter.write("A: " + getVentPercentText(vents[0]) + "\n");
            logWriter.write("Direction " + vents[0].getDirection() + "\n");
            logWriter.write("B: " + getVentPercentText(vents[1]) + "\n");
            logWriter.write("Direction " + vents[1].getDirection() + "\n");
            logWriter.write("C: " + getVentPercentText(vents[2]) + "\n");
            logWriter.write("Direction " + vents[2].getDirection() + "\n");
            logWriter.write("\n");
            logWriter.flush();
        } catch (IOException ignored) {

        }
    }
    private void logText(String text) {
        try {
            logWriter.write(text + "\n");
            logWriter.flush();
        } catch (IOException ignored) {

        }
    }
    public void log() {
        StatusState predictedState = new StatusState(initialState);
        logText("Starting player count was: " + StabilityUpdateInfo.getNumPlayers());
        for(int i = startingTick+1; i <= currentTick; ++i) {
            if((timeline[i] & (1 << IDENTIFIED_VENT_FLAG)) != 0) {
                int idFlags = timeline[i] & IDENTIFIED_BIT_MASK;
                if((idFlags & 1) != 0) {
                    logText("On tick: " + i + " A Vent was identified to be "
                            + identifiedVentStates[0].getVents()[0].getActualValue());
                    predictedState.getVents()[0].setEqualTo(identifiedVentStates[0].getVents()[0]);
                }
                if((idFlags & 2) != 0) {
                    logText("On tick: " + i + " B Vent was identified to be "
                                    + identifiedVentStates[1].getVents()[1].getActualValue());
                    predictedState.getVents()[1].setEqualTo(identifiedVentStates[1].getVents()[1]);
                }
                if((idFlags & 4) != 0) {
                    logText("On tick: " + i + " C Vent was identified to be "
                            + identifiedVentStates[2].getVents()[2].getActualValue());
                    predictedState.getVents()[2].setEqualTo(identifiedVentStates[2].getVents()[2]);
                }
            }
            if((timeline[i] & (1 << DIRECTION_CHANGED_FLAG)) != 0) {
                int directionFlags = timeline[i] & DIRECTION_CHANGED_BIT_MASK;
                directionFlags >>= 3;
                if((directionFlags & 1) != 0) logText("On tick: " + i + " A Vent direction has changed");
                if((directionFlags & 2) != 0) logText("On tick: " + i + " B Vent direction has changed");
                if((directionFlags & 4) != 0) logText("On tick: " + i + " C Vent direction has changed");
                changeStateDirection(predictedState, i);
            }
            if((timeline[i] & (1 << EARTHQUAKE_EVENT_FLAG)) != 0) {
                logText("On tick: " + i + " there was an earthquake event");
            }
            if((timeline[i] & (1 << MOVEMENT_UPDATE_FLAG)) != 0) {
                logText("On tick: " + i + " there was a movement update");
                StatusState moveState = tickToMovementVentState.get(i);
                logState(moveState);
                if(moveState.isAllVentsIdentified()) {
                    logText("Calculated Stability: " + StatusState.calcStabilityChange(moveState));
                }
                syncWithMovementState(predictedState, i);
                predictedState.updateVentMovement();
            }
            if((timeline[i] & (1 << STABILITY_UPDATE_FLAG)) != 0) {
                StabilityUpdateInfo stabilityInfo = tickToStabilityUpdateState.get(i);
                logText("On tick: " + i + " there was a stability update of " + stabilityInfo.getInitialChange());
                stabilityInfo.updatePredictedState(predictedState);
            }
        }
    }
}
