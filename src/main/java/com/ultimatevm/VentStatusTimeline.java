package com.ultimatevm;

import java.io.FileWriter;
import java.io.IOException;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;

public class VentStatusTimeline {
    //Constants
    public static final int VENT_MOVE_TICK_TIME = 10;
    public static final int STABILITY_UPDATE_TICK_TIME = 25;
    public static final int VM_GAME_FULL_TIME = 1000;
    public static final int VM_GAME_RESET_TIME = 500;

    //Flags
    public static final int DIRECTION_CHANGED_FLAG = 16;
    public static final int IDENTIFIED_VENT_FLAG = DIRECTION_CHANGED_FLAG+1;
    public static final int MOVEMENT_UPDATE_FLAG = IDENTIFIED_VENT_FLAG+1;
    public static final int STABILITY_UPDATE_FLAG = MOVEMENT_UPDATE_FLAG+1;
    public static final int EARTHQUAKE_EVENT_FLAG = STABILITY_UPDATE_FLAG+1;
    public static final int ESTIMATED_MOVEMENT_FLAG = EARTHQUAKE_EVENT_FLAG+1;
    public static final int HALF_SPACE_COMPLETED_FLAG = ESTIMATED_MOVEMENT_FLAG+1;


    //Masks
    public static final int IDENTIFIED_BIT_MASK = 7;
    public static final int DIRECTION_CHANGED_BIT_MASK = 7 << 3;
    public static final int MOVEMENT_BIT_MASK = 63 << 6;
    //       |   move    | dir |  id |
    //0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0

    public static final int HALF_SPACE_VENTS_BIT_MASK = 7 << HALF_SPACE_COMPLETED_FLAG+1;
    public static final int HALF_SPACE_CLIP_BIT_MASK = 7 << HALF_SPACE_COMPLETED_FLAG+4;
    //clip |vents| space completed?
    //0 0 0 0 0 0  0

    private static final String FILE_PATH =
            "C:\\Users\\cyanw\\IdeaProjects\\UltimateVolcanicMine\\src\\main\\resources\\game_log.txt";
    FileWriter logWriter;

    private int currentTick, startingTick;
    private int currentMovementTick, firstStabilityUpdateTick;
    private int[] timeline;
    private int[] identifiedVentTick;
    private StatusState[] identifiedVentStates;
    private int numIdentifiedVents;
    private boolean hasReset = false;
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
        timeline = new int[VM_GAME_FULL_TIME];
        tickToMovementVentState = new HashMap<>();
        tickToStabilityUpdateState = new HashMap<>();
        reset();
        hasReset = false;
    }
    public void reset() {
        if(hasReset) return;
        hasReset = true;
        firstStabilityUpdateTick = Integer.MAX_VALUE;
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
    private void clearMoveSkipEstimatedMove() {
        int minTick = Math.max(startingTick, currentTick - (int)(VENT_MOVE_TICK_TIME * 2.5f));
        int prevEstMoveTick = Integer.MIN_VALUE, prevMoveTick = Integer.MIN_VALUE;
        for(int i = currentTick-1; i >= minTick; --i) {
            if((timeline[i] & (1 << ESTIMATED_MOVEMENT_FLAG)) != 0) {
                //exit if consecutive estimated movement (means no skip)
                if(prevEstMoveTick != Integer.MIN_VALUE) return;
                prevEstMoveTick = i;
            }
            if((timeline[i] & (1 << MOVEMENT_UPDATE_FLAG)) != 0) {
                //exit if consecutive movement (means no skip)
                if(prevMoveTick != Integer.MIN_VALUE) return;
                prevMoveTick = i;
            }
        }
        //Exit if either estimated or actual movement tick are not found
        if(prevEstMoveTick == Integer.MIN_VALUE || prevMoveTick == Integer.MIN_VALUE) return;
        //Exit if the previous movement tick is after the estimated one
        if(prevMoveTick > prevEstMoveTick) return;
        //remove estimated movement tick was added since a movement tick was skipped
        timeline[prevEstMoveTick] &= ~(1 << ESTIMATED_MOVEMENT_FLAG);
    }
    private void fixPreviousEstimatedMoves() {
        int updateTick = currentTick % VENT_MOVE_TICK_TIME;
        for(int i = currentTick-1; i >= currentMovementTick; --i) {
            //Clear estimated movement flag
            timeline[i] &= ~(1 << ESTIMATED_MOVEMENT_FLAG);
            if(i % VENT_MOVE_TICK_TIME == updateTick)
                addEstimatedMovementTick(i);
        }
    }

    public void addDirectionChangeTick(int bitState) {
        timeline[currentTick] |= (bitState & DIRECTION_CHANGED_BIT_MASK);
        timeline[currentTick] |= (1 << DIRECTION_CHANGED_FLAG);
    }
    public void addEarthquakeEventTick() {
        timeline[currentTick] |= (1 << EARTHQUAKE_EVENT_FLAG);
        //Clear estimated movement flag
        timeline[currentTick] &= ~(1 << ESTIMATED_MOVEMENT_FLAG);
    }
    public void addMovementTick(StatusState currentState, int movementBitState) {
        addNewMovementTickState(currentTick, currentState, movementBitState);
        clearMoveSkipEstimatedMove();
        //Update previous values on the very first movement update (likely after a vent check)
        if(currentMovementTick == startingTick) {
            fixPreviousEstimatedMoves();
            updatePreviousVentValues(currentState, currentTick);
        }
        currentMovementTick = currentTick;
    }
    public void addStabilityUpdateTick(StatusState currentState, int change) {
        addNewStabilityUpdateTickState(currentTick, currentState, change);
    }
    public boolean addEstimatedMovementTick() {
        return addEstimatedMovementTick(currentTick);
    }
    private boolean addEstimatedMovementTick(int tick) {
        //Estimated movements cannot occur same tick as an earthquake
        if((timeline[tick] & (1 << EARTHQUAKE_EVENT_FLAG)) != 0)
            return false;
        timeline[tick] |= (1 << ESTIMATED_MOVEMENT_FLAG);
        return true;
    }
    private void checkHalfSpace(int tick) {
        if(!tickToStabilityUpdateState.containsKey(tick)) return;
        //Do this only if one vent is known
        int numKnownVents = tickToStabilityUpdateState.get(tick).getStabilityUpdateState().getNumIdentifiedVents();
        if(numKnownVents != 1) return;

        //Find a valid previous change
        int currentChange = tickToStabilityUpdateState.get(tick).getInitialChange();
        int endingTick = Math.max(tick - (STABILITY_UPDATE_TICK_TIME*2),startingTick);
        for(int i = tick - STABILITY_UPDATE_TICK_TIME; i >= endingTick; i -= STABILITY_UPDATE_TICK_TIME) {
            if(!tickToStabilityUpdateState.containsKey(i)) continue;

            int change = tickToStabilityUpdateState.get(i).getInitialChange();
            int prevKnownVents = tickToStabilityUpdateState.get(i).getStabilityUpdateState().getNumIdentifiedVents();

            //Ensure these two updates have the same number of known vents
            if(numKnownVents != prevKnownVents)
                continue;

            //Determine each vents contribution and direction changes
            int[] pointChange = new int[StatusState.NUM_VENTS];
            int[] moveChange = new int[StatusState.NUM_VENTS+1];
            if(!getPointContribution(i, tick, pointChange, moveChange))
                break;

            int timeframeSize = (tick - i) / STABILITY_UPDATE_TICK_TIME;
            if(completeHalfSpace(tick, timeframeSize,currentChange - change, pointChange, moveChange))
                break;
        }
    }
    public StatusState getTimelinePredictionState() {
        LinkedList<StatusState> possibleStates = new LinkedList<>();
        StabilityUpdateInfo prevStabInfo = null;
        StatusState predictedState = new StatusState(initialState);
        int previousMovementTick = startingTick, numTicksNegativePredictedStability = 0;
        possibleStates.push(predictedState);
        for(int i = startingTick; i <= currentTick; ++i) {
            if((timeline[i] & (1 << IDENTIFIED_VENT_FLAG)) != 0) {
                int idFlags = timeline[i] & IDENTIFIED_BIT_MASK;
                Iterator<StatusState> iterator = possibleStates.descendingIterator();
                while (iterator.hasNext()) {
                    StatusState curState = iterator.next();
                    if ((idFlags & 1) != 0) {
                        curState.setVentEqualTo(identifiedVentStates[0], 0);
                    }
                    if ((idFlags & 2) != 0) {
                        curState.setVentEqualTo(identifiedVentStates[1], 1);
                    }
                    if ((idFlags & 4) != 0) {
                        curState.setVentEqualTo(identifiedVentStates[2], 2);
                    }
                }
            }
            if((timeline[i] & (1 << ESTIMATED_MOVEMENT_FLAG)) != 0) {
                StatusState newPossibility = new StatusState(possibleStates.getLast());

                //Only set if value wasnt freeze clipped
                boolean isValueClipped = newPossibility.doFreezeClipping(0);
                if(!isValueClipped) {
                    handleSameTickDirectionChangeMovement(newPossibility, i);
                    possibleStates.addLast(newPossibility);
                }
                //Set predicted state to the new up to date possibility
                //If two consecutive movements are skipped just update the predicted state
                if(i - previousMovementTick > VENT_MOVE_TICK_TIME)
                    predictedState = possibleStates.getLast();
            }
            if((timeline[i] & (1 << MOVEMENT_UPDATE_FLAG)) != 0) {
                previousMovementTick = i;
                int moveBitState = timeline[i] & MOVEMENT_BIT_MASK;
                moveBitState >>= 6;
                Iterator<StatusState> iterator = possibleStates.descendingIterator();
                while (iterator.hasNext()) {
                    StatusState curState = iterator.next();
                    //Remove possibility if a value was clipped
                    boolean isValueClipped = curState.doFreezeClipping(moveBitState);
                    if(possibleStates.size() > 1 && isValueClipped) {
                        iterator.remove();
                        continue;
                    }

                    //Update our estimated vent values
                    handleSameTickDirectionChangeMovement(curState, i);
                    syncWithMovementState(curState, i);
                }
                predictedState = possibleStates.getLast();
            }
            if((timeline[i] & (1 << STABILITY_UPDATE_FLAG)) != 0) {
                Iterator<StatusState> iterator = possibleStates.descendingIterator();
                StabilityUpdateInfo stabilityInfo = tickToStabilityUpdateState.get(i);
                int initalRNGMod = initialStabInfo == null ? 0 : initialStabInfo.getRNGUpdateMod();
                while (iterator.hasNext()) {
                    StatusState curState = iterator.next();
                    if(stabilityInfo.isValid()) {
                        //Use stability updates to set/narrow our possible values
                        if (stabilityInfo == initialStabInfo) {
                            curState.alignPredictedRangesWith(initialStabInfo.getStabilityUpdateState());
                        } else stabilityInfo.updatePredictedState(curState, prevStabInfo, initalRNGMod);
                    }

                    if((timeline[i] & (1 << HALF_SPACE_COMPLETED_FLAG)) != 0) {
                        int ventsToClip = (timeline[i] & HALF_SPACE_VENTS_BIT_MASK) >> (HALF_SPACE_COMPLETED_FLAG+1);
                        int clipInfo = (timeline[i] & HALF_SPACE_CLIP_BIT_MASK) >> (HALF_SPACE_COMPLETED_FLAG+4);
                        curState.doHalfSpaceClipping(ventsToClip, clipInfo);
                    }
                }

                removeInvalidPossibilities(possibleStates);
                predictedState = possibleStates.getLast();
                prevStabInfo = stabilityInfo;
                numTicksNegativePredictedStability = 0;
            }
            if((timeline[i] & (1 << DIRECTION_CHANGED_FLAG)) != 0) {
                //Change our direction if it occured this tick
                Iterator<StatusState> iterator = possibleStates.descendingIterator();
                while (iterator.hasNext()) {
                    changeStateDirection(iterator.next(), i);
                }
            }

            int predictedChange = predictedState.getFutureStabilityChange(UltimateVolcanicMineConfig.PredictionScenario.WORST_CASE);
            if(predictedChange < StabilityUpdateInfo.getMinRNGVariation()-1)
                ++numTicksNegativePredictedStability;
            else numTicksNegativePredictedStability = 0;

            //Attempt to clip invalid predicted ranges
            //This scenario occurs when stability stays 100% for extended time
            if(prevStabInfo != null) {
                if (numTicksNegativePredictedStability < STABILITY_UPDATE_TICK_TIME * 2) continue;

                int ticksSinceLastUpdate = i - prevStabInfo.getTickTimeStamp();
                if (ticksSinceLastUpdate < STABILITY_UPDATE_TICK_TIME * 2) continue;

                //Check and see if we meet requirements to display
                //a predicted stability change
                Iterator<StatusState> iterator = possibleStates.descendingIterator();
                while (iterator.hasNext()) {
                    StatusState curState = iterator.next();
                    curState.clipPredictedStabilityMismatch(StabilityUpdateInfo.getMinRNGVariation() - 1);
                }
            }
        }
        return predictedState;
    }
    public StatusState getCurrentPredictionState() {
        return StabilityUpdateInfo.getPredictionState(initialStabInfo, this);
    }

    //Helpers
    private void addNewMovementTickState(int tick, StatusState currentState, int moveState) {
        StatusState newState = new StatusState(currentState);
        tickToMovementVentState.put(tick, newState);
        timeline[tick] |= (1 << MOVEMENT_UPDATE_FLAG);
        timeline[tick] |= moveState;
    }
    private void addNewStabilityUpdateTickState(int tick, StatusState currentState, int change) {
        StabilityUpdateInfo newInfo = new StabilityUpdateInfo(currentState, tick, change);
        tickToStabilityUpdateState.put(currentTick, newInfo);
        timeline[tick] |= (1 << STABILITY_UPDATE_FLAG);
        setInitialStabilityUpdateInfo(newInfo);
//        checkHalfSpace(currentTick);
    }
    private void setInitialStabilityUpdateInfo(StabilityUpdateInfo info) {
        firstStabilityUpdateTick = Math.min(firstStabilityUpdateTick, currentTick);
        //Skip if no identified vents
        int infoIdentifiedVentCount = info.getStabilityUpdateState().getNumIdentifiedVents();
        if(infoIdentifiedVentCount == 0) return;
        if(initialStabInfo == null) initialStabInfo = info;
        else if (initialStabInfo.getTickTimeStamp() > info.getTickTimeStamp())
            initialStabInfo = info;
    }
    private void changeStateDirection(StatusState state, int tick) {
        int directionFlags = timeline[tick] & DIRECTION_CHANGED_BIT_MASK;
        directionFlags >>= 3;
        if((directionFlags & 1) != 0) state.getVents()[0].flipDirection();
        if((directionFlags & 2) != 0) state.getVents()[1].flipDirection();
        if((directionFlags & 4) != 0) state.getVents()[2].flipDirection();
    }
    private void handleSameTickDirectionChangeMovement(StatusState curState, int tick) {
        if((timeline[tick] & (1 << DIRECTION_CHANGED_FLAG)) != 0) {
            //It's possible for the directional change to occur both
            //before and after this movement update; assume both possibilities
            StatusState newDirState = new StatusState(curState);
            changeStateDirection(newDirState, tick);
            newDirState.updateVentMovement();
            curState.updateVentMovement();
            curState.mergePredictedRangesWith(newDirState);
        }
        else curState.updateVentMovement();
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
    private void removeInvalidPossibilities(LinkedList<StatusState> possibleStates) {
        //Remove all invalid possibilities - always keep 1 state even if invalid
        Iterator<StatusState> iterator = possibleStates.descendingIterator();
        while (iterator.hasNext()) {
            if(possibleStates.size() == 1) break;
            StatusState curState = iterator.next();
            if(!curState.areRangesDefined()) iterator.remove();
        }
    }
    private boolean getPointContribution(int startTick, int endTick, int[] pointChange, int[] moveChange) {
        //Exit if starting stability update doesnt exist
        if(!tickToStabilityUpdateState.containsKey(startTick)) return false;

        //Get our starting points
        StatusState startState = tickToStabilityUpdateState.get(startTick).getStabilityUpdateState();
        int[] startingPoints = new int[StatusState.NUM_VENTS], endingPoints = new int[StatusState.NUM_VENTS];
        for(int i = 0; i < StatusState.NUM_VENTS; ++i) {
            if(!startState.getVents()[i].isIdentified())
                endingPoints[i] = startingPoints[i] = Integer.MAX_VALUE;
            else endingPoints[i] = startingPoints[i] = VentStatus.getStabilityInfluence(startState.getVents()[i].getActualValue());
        }

        int directionState = 0, numMovementUpdates = 0;
        for(int i = startTick; i <= endTick; ++i) {
            //Keep track of directional state to ensure proper half space clipping
            if((timeline[i] & (1 << DIRECTION_CHANGED_FLAG)) != 0) {
                int directionFlags = timeline[i] & DIRECTION_CHANGED_BIT_MASK;
                directionFlags >>= 3;
                if((directionFlags & 1) != 0) directionState |= 1;
                if((directionFlags & 2) != 0) directionState |= 2;
                if((directionFlags & 4) != 0) directionState |= 4;
            }

            //Update ending points
            if((timeline[i] & (1 << MOVEMENT_UPDATE_FLAG)) != 0) {
                StatusState moveState = tickToMovementVentState.get(i);
                for(int j = 0; j < StatusState.NUM_VENTS; ++j) {
                    if(!moveState.getVents()[j].isIdentified())
                        endingPoints[j] = Integer.MAX_VALUE;
                    else endingPoints[j] = VentStatus.getStabilityInfluence(moveState.getVents()[j].getActualValue());
                }
                //Keep track of how long each vent have been facing a specific direction
                moveChange[0] = ++numMovementUpdates;
            }
        }

        for(int i = 0; i < StatusState.NUM_VENTS; ++i) {
            if(startingPoints[i] == Integer.MAX_VALUE)
                pointChange[i] = Integer.MAX_VALUE;
            else {
                pointChange[i] = endingPoints[i] - startingPoints[i];
                directionState &= ~(1 << i);
            }
        }
        return directionState == 0;
    }
    private boolean completeHalfSpace(int tick, int timeframeSize, int changeDiff, int[] pointChange, int[] moveChange) {
        //Make sure change cannot be influenced by rng
        if(Math.abs(changeDiff) < StabilityUpdateInfo.getMaxRNGPossibleSize())
            return false;

        //Get influence of the two missing vents
        int knownVentIndex = 0;
        for(int i = 0; i < StatusState.NUM_VENTS; ++i) {
            if(pointChange[i] == Integer.MAX_VALUE) {
                continue;
            }
            changeDiff -= pointChange[i];
            knownVentIndex = i;
        }
        int missingVentFlag = 7 & ~(1 << knownVentIndex);

        //No movement check
        if(moveChange[0] == 0) {
            //For B this is only possible if A is 41-59; just clip A
            if(knownVentIndex == 1) {
                if(changeDiff < 0) {
                    //1 placed for downward clipping trend
                    timeline[tick] |= (1 << HALF_SPACE_COMPLETED_FLAG+4);
                }
                //0s placed for clipping for upward trend
                timeline[tick] |= (1 << HALF_SPACE_COMPLETED_FLAG+1);
                timeline[tick] |= (1 << HALF_SPACE_COMPLETED_FLAG);
                return true;
            }
            //No movement is unacceptable for A
            //For C Either A or B are 41-59 dont know which so cant clip
            return false;
        } else {
            //Both missing vents must have increased or decreased change
            if(Math.abs(changeDiff) <= timeframeSize) return false;

            if(changeDiff < 0) {
                //1 placed for downward clipping trend
                timeline[tick] |= (missingVentFlag << HALF_SPACE_COMPLETED_FLAG+4);
            }
            //0s placed for clipping for upward trend
            timeline[tick] |= (missingVentFlag << HALF_SPACE_COMPLETED_FLAG+1);
            timeline[tick] |= (1 << HALF_SPACE_COMPLETED_FLAG);
        }
        return true;
    }

    //Accessors
    public boolean isHasReset() { return hasReset; }
    public int getCurrentTick() { return currentTick; }
    public int getCurrentStartingTick() {return startingTick;}
    public int getNumIdentifiedVents() { return numIdentifiedVents; }
    public final int[] getTimeline() { return timeline; }
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
        StabilityUpdateInfo prevStabInfo = null;
        logText("Starting player count was: " + StabilityUpdateInfo.getNumPlayers());
        for(int i = startingTick; i <= currentTick; ++i) {
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
            if((timeline[i] & (1 << EARTHQUAKE_EVENT_FLAG)) != 0) {
                logText("On tick: " + i + " there was an earthquake event");
            }
            if((timeline[i] & (1 << ESTIMATED_MOVEMENT_FLAG)) != 0) {
                logText("On tick: " + i + " we added estimated movement");
            }
            if((timeline[i] & (1 << MOVEMENT_UPDATE_FLAG)) != 0) {
                logText("On tick: " + i + " there was a movement update");
                StatusState moveState = tickToMovementVentState.get(i);
                logState(moveState);
                if(moveState.isAllVentsIdentified()) {
                    logText("Calculated Stability: " + StatusState.calcStabilityChange(moveState));
                }
                int moveBitState = timeline[i] & MOVEMENT_BIT_MASK;
                predictedState.doFreezeClipping(moveBitState >> 6);
                handleSameTickDirectionChangeMovement(predictedState, i);
                syncWithMovementState(predictedState, i);
            }
            if((timeline[i] & (1 << STABILITY_UPDATE_FLAG)) != 0) {
                StabilityUpdateInfo stabilityInfo = tickToStabilityUpdateState.get(i);
                logText("On tick: " + i + " there was a stability update of " + stabilityInfo.getInitialChange());
                stabilityInfo.updatePredictedState(predictedState, prevStabInfo, initialStabInfo.getRNGUpdateMod());
                prevStabInfo = stabilityInfo;
                if((timeline[i] & (1 << HALF_SPACE_COMPLETED_FLAG)) != 0) {
                    int ventsToClip = (timeline[i] & HALF_SPACE_VENTS_BIT_MASK) >> (HALF_SPACE_COMPLETED_FLAG+1);
                    int clipInfo = (timeline[i] & HALF_SPACE_CLIP_BIT_MASK) >> (HALF_SPACE_COMPLETED_FLAG+4);
                    predictedState.doHalfSpaceClipping(ventsToClip, clipInfo);
                    logText("On tick: " + i + " half space clipping occurred");
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
        }
    }
}
