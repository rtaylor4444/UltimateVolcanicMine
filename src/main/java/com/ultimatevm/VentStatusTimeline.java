package com.ultimatevm;

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

    private int currentTick, startingTick;
    private int currentMovementTick, firstStabilityUpdateTick;
    private int[] timeline;
    private int[] identifiedVentTick;
    private StatusState[] identifiedVentStates;
    private TimelineCache[] timelineCaches;
    private int numIdentifiedVents;
    private boolean hasReset = false;
    StatusState initialState;
    StabilityUpdateInfo initialStabInfo;
    HashMap<Integer, StatusState> tickToMovementVentState;
    HashMap<Integer, StabilityUpdateInfo> tickToStabilityUpdateState;

    public VentStatusTimeline() {
        initialize();
    }
    public void initialize() {
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
        clearCache();
        identifiedVentTick = new int[StatusState.NUM_VENTS+1];
        identifiedVentStates = new StatusState[StatusState.NUM_VENTS+1];
        for(int i = 0; i < StatusState.NUM_VENTS+1; ++i) {
            identifiedVentTick[i] = -1;
            identifiedVentStates[i] = null;
        }
    }
    public void clearCache() {
        timelineCaches = new TimelineCache[StabilityUpdateInfo.getMaxRNGPossibleSize()];
        for(int i = 0; i < timelineCaches.length; ++i)
            timelineCaches[i] = new TimelineCache();
    }
    public void initalizeCache() {
        for(int i = 0; i < timelineCaches.length; ++i)
            timelineCaches[i].initalize(initialState, startingTick);
    }
    public boolean addInitialState(StatusState startingState) {
        //Only add initial state once for pre reset and post reset
        if(initialState != null) return false;
        initialState = new StatusState(startingState);
        initalizeCache();
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
    private void backtrackFreezeClipAccurateA(StatusState currentState) {
        //Exit if we already reversed this value
        if(identifiedVentTick[3] != -1) return;
        identifiedVentStates[3] = new StatusState(currentState);
        identifiedVentTick[3] = currentTick;
        //Don't backtrack if the other two vents are already identified
        if(numIdentifiedVents+1 >= 3) return;
        updatePreviousVentValues(identifiedVentStates[3], currentTick);
    }
    private void updatePreviousVentValues(StatusState startingState, int tick) {
        clearCache();
        StatusState curState = new StatusState(startingState);
        LinkedList<Integer> stabilityUpdateTicks = new LinkedList<>();
        int numTicksNoMovement = 0, futureMovementTick = Integer.MAX_VALUE;
        for(int i = tick; i > startingTick; --i) {
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
                int bitMoveState = (timeline[i] & MOVEMENT_BIT_MASK) >> 6;
                if(!curState.reverseMovement(bitMoveState)) break;
                //update the movement bit info
                bitMoveState = StatusState.makeMoveBitState(movementState, curState);
                timeline[i] &= ~(MOVEMENT_BIT_MASK);
                timeline[i] |= bitMoveState;
            } else ++numTicksNoMovement;

            if(isEarthquakeDelayMovement(i)) numTicksNoMovement = 0;

            if((timeline[i] & (1 << DIRECTION_CHANGED_FLAG)) != 0) {
                //Change our direction if it occured this tick
                changeStateDirection(curState, i);
            }
        }
        initalizeCache();
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
        clearCache();
        int updateTick = currentTick % VENT_MOVE_TICK_TIME;
        for(int i = currentTick-1; i >= currentMovementTick; --i) {
            //Clear estimated movement flag
            timeline[i] &= ~(1 << ESTIMATED_MOVEMENT_FLAG);
            if(i % VENT_MOVE_TICK_TIME == updateTick)
                addEstimatedMovementTick(i);
        }
        initalizeCache();
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
        //A movement tick cannot occur on or before a starting tick
        if(currentTick <= startingTick) return;
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
        //A movement tick cannot occur on or before a starting tick
        if(tick <= startingTick) return false;
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
        //Get the current cache to use
        TimelineCache cache;
        if(initialStabInfo == null) {
            cache = timelineCaches[(-StabilityUpdateInfo.getMinRNGVariation())+1];
        }
        else cache = timelineCaches[(-initialStabInfo.getRNGUpdateMod())+1];

        for(; cache.i <= currentTick; ++cache.i) {
            if((timeline[cache.i] & (1 << IDENTIFIED_VENT_FLAG)) != 0) {
                int idFlags = timeline[cache.i] & IDENTIFIED_BIT_MASK;
                Iterator<StatusState> iterator = cache.possibleStates.descendingIterator();
                cache.mostRecentIdentifyTick = cache.i;
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
            if((timeline[cache.i] & (1 << ESTIMATED_MOVEMENT_FLAG)) != 0) {
                int mostRecentEvent = Math.max(cache.mostRecentIdentifyTick, cache.previousMovementTick);
                boolean isValueClipped = false, isConsecMoveSkip = (cache.i - mostRecentEvent > VENT_MOVE_TICK_TIME);
                StatusState newPossibility = new StatusState(cache.possibleStates.getLast());

                //Don't do any freeze clipping unless two movements were skipped
                if(isConsecMoveSkip) isValueClipped = newPossibility.doFreezeClipping(0);

                //Only set if value wasnt freeze clipped
                if(!isValueClipped) {
                    handleSameTickDirectionChangeMovement(newPossibility, cache.i);
                    cache.possibleStates.addLast(newPossibility);
                }
                //Set predicted state to the new up to date possibility
                //If two consecutive movements are skipped just update the predicted state
                if(isConsecMoveSkip) cache.predictedState = cache.possibleStates.getLast();
            }
            if((timeline[cache.i] & (1 << MOVEMENT_UPDATE_FLAG)) != 0) {
                cache.previousMovementTick = cache.i;
                int moveBitState = timeline[cache.i] & MOVEMENT_BIT_MASK;
                moveBitState >>= 6;
                Iterator<StatusState> iterator = cache.possibleStates.descendingIterator();
                while (iterator.hasNext()) {
                    StatusState curState = iterator.next();
                    //Remove possibility if a value was clipped
                    boolean isValueClipped = curState.doFreezeClipping(moveBitState);
                    if(cache.possibleStates.size() > 1 && isValueClipped) {
                        iterator.remove();
                        continue;
                    }

                    //Update our estimated vent values
                    handleSameTickDirectionChangeMovement(curState, cache.i);
                    syncWithMovementState(curState, cache.i);
                }
                cache.predictedState = cache.possibleStates.getLast();
            }
            if((timeline[cache.i] & (1 << STABILITY_UPDATE_FLAG)) != 0) {
                Iterator<StatusState> iterator = cache.possibleStates.descendingIterator();
                StabilityUpdateInfo stabilityInfo = tickToStabilityUpdateState.get(cache.i);
                int initalRNGMod = initialStabInfo == null ?
                        StabilityUpdateInfo.getMinRNGVariation()
                        : initialStabInfo.getRNGUpdateMod();
                while (iterator.hasNext()) {
                    StatusState curState = iterator.next();
                    if(stabilityInfo.isValid()) {
                        //Use stability updates to set/narrow our possible values
                        if (stabilityInfo == initialStabInfo) {
                            if(curState.getVents()[0].isFreezeClipAccurate()) initialStabInfo.updateVentValues(curState);
                            curState.alignPredictedRangesWith(initialStabInfo.getStabilityUpdateState());
                        } else stabilityInfo.updatePredictedState(curState, cache.prevStabInfo, initalRNGMod);
                    }

                    if((timeline[cache.i] & (1 << HALF_SPACE_COMPLETED_FLAG)) != 0) {
                        int ventsToClip = (timeline[cache.i] & HALF_SPACE_VENTS_BIT_MASK) >> (HALF_SPACE_COMPLETED_FLAG+1);
                        int clipInfo = (timeline[cache.i] & HALF_SPACE_CLIP_BIT_MASK) >> (HALF_SPACE_COMPLETED_FLAG+4);
                        curState.doHalfSpaceClipping(ventsToClip, clipInfo);
                    }
                }

                removeInvalidPossibilities(cache.possibleStates);
                cache.predictedState = cache.possibleStates.getLast();
                cache.prevStabInfo = stabilityInfo;
                cache.numTicksNegativePredictedStability = 0;
            }
            if((timeline[cache.i] & (1 << DIRECTION_CHANGED_FLAG)) != 0) {
                //Change our direction if it occured this tick
                Iterator<StatusState> iterator = cache.possibleStates.descendingIterator();
                while (iterator.hasNext()) {
                    changeStateDirection(iterator.next(), cache.i);
                }
            }

            int predictedChange = cache.predictedState.getFutureStabilityChange(UltimateVolcanicMineConfig.PredictionScenario.WORST_CASE);
            if(predictedChange < StabilityUpdateInfo.getMinRNGVariation()-1)
                ++cache.numTicksNegativePredictedStability;
            else cache.numTicksNegativePredictedStability = 0;

            //Attempt to clip invalid predicted ranges
            //This scenario occurs when stability stays 100% for extended time
            if(cache.prevStabInfo != null) {
                if (cache.numTicksNegativePredictedStability < STABILITY_UPDATE_TICK_TIME * 2) continue;

                int ticksSinceLastUpdate = cache.i - cache.prevStabInfo.getTickTimeStamp();
                if (ticksSinceLastUpdate < STABILITY_UPDATE_TICK_TIME * 2) continue;

                //Check and see if we meet requirements to display
                //a predicted stability change
                Iterator<StatusState> iterator = cache.possibleStates.descendingIterator();
                while (iterator.hasNext()) {
                    StatusState curState = iterator.next();
                    curState.clipPredictedStabilityMismatch(StabilityUpdateInfo.getMinRNGVariation() - 1);
                }
            }
        }
        if(cache.predictedState.getVents()[0].isFreezeClipAccurate())
            backtrackFreezeClipAccurateA(cache.predictedState);
        return cache.predictedState;
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
        //Check and see if we can sync with an accurate freeze clipped value
        boolean accurateFreezeClipSync = (identifiedVentTick[3] != -1 && identifiedVentTick[3] >= tick);
        for(int i = 0; i < StatusState.NUM_VENTS; ++i) {
            if(!moveState.getVents()[i].isFreezeClipAccurate()) {
                if (!moveState.getVents()[i].isIdentified()) continue;
            } else if(!accurateFreezeClipSync) continue;

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
    public boolean hasEventOccuredThisTick() { return timeline[currentTick] != 0; }
    public final int[] getTimeline() { return timeline; }
    public final int[] getIdentifiedVentTicks() { return identifiedVentTick; }
    public final StatusState[] getIdentifiedVentStates() { return identifiedVentStates; }
    public final StatusState getInitialState() { return initialState; }
    public final HashMap<Integer, StatusState> getMovementVentStates() { return tickToMovementVentState; }
    public final HashMap<Integer, StabilityUpdateInfo> getStabilityUpdateStates() { return tickToStabilityUpdateState; }

    //Modifiers
    public void updateTick() { ++currentTick; }
}
