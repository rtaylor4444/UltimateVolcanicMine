package com.ultimatevm;

import static com.ultimatevm.StatusState.*;
import static com.ultimatevm.VentStatus.*;

import java.util.ArrayList;

public class VentStatusPredicter {
    public static final int SLOWEST_VENT_UPDATE_TICK = VentStatusTimeline.VENT_MOVE_TICK_TIME-1;
    public static final int HIGHEST_STABLE_RNG_PLAYER_COUNT = 8;

    private VentStatusTimeline timeline;
    private StatusState displayState;
    private int numTicksNoMove, cooldownEndTick;


    public VentStatusPredicter() {
        initialize();
    }
    public void initialize() {
        timeline = new VentStatusTimeline();
        displayState = new StatusState();
        cooldownEndTick = 0;
    }
    public void reset() {
        if(!timeline.isHasReset()) displayState.forceReset();
        timeline.reset();
    }
    public void updateVentStatus(int[] ventStatus, int chambers) {
        processVentChangeState(displayState.updateVentStatus(ventStatus, chambers));
    }
    public void makeStatusState(int change) {
        timeline.addStabilityUpdateTick(displayState, change);
        updateDisplayState();
    }
    public String getVentStatusText(int index, String startingText) {
        VentStatus[] vents = displayState.getVents();
        if(vents[index].isIdentified() || !vents[index].isRangeDefined()) return startingText;
        StringBuilder builder = new StringBuilder();
        builder.append(startingText, 0, 3);
        builder.append("<col=00ffff>");
        builder.append(getVentPercentText(vents[index]));
        return builder.append("</col>").toString();
    }
    public void markEarthquakeEvent() {
        timeline.addEarthquakeEventTick();
    }

    //Helpers
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
    private void processVentChangeState(int[] changeStates) {
        int bitState = 0, movementBitState = 0;
        for(int i = 0; i < changeStates.length; ++i) {
            if((changeStates[i] & VentChangeStateFlag.IDENTIFIED.bitFlag()) != 0) {
                bitState |= (1 << i);
                movementBitState |= (3 << (i * 2));
            }
            if((changeStates[i] & VentChangeStateFlag.DIRECTION_CHANGE.bitFlag()) != 0) {
                bitState |= (1 << (i+3));
            }

            if((changeStates[i] & VentChangeStateFlag.NO_CHANGE.bitFlag()) != 0) {
                //TODO: Record how many ticks a specific vent has no change
                bitState |= 64;
            }

            if((changeStates[i] & VentChangeStateFlag.ONE_CHANGE.bitFlag()) != 0) {
                bitState |= 128;
                movementBitState |= (1 << (i * 2));
            }

            if((changeStates[i] & VentChangeStateFlag.TWO_CHANGE.bitFlag()) != 0) {
                bitState |= 128;
                movementBitState |= (2 << (i * 2));
            }

            if((changeStates[i] & VentChangeStateFlag.RESET.bitFlag()) != 0){
                bitState |= 512;
            }
        }

        timeline.addInitialState(displayState);
        //Reset when all vents are set to unidentified
        if((bitState & 512) != 0) {
            //Wait before updating this value from the timeline
            if(!displayState.hasDoneVMReset())
                cooldownEndTick = timeline.getCurrentTick() + SLOWEST_VENT_UPDATE_TICK;
            displayState.doVMReset();
        }
        if((bitState & VentStatusTimeline.DIRECTION_CHANGED_BIT_MASK) != 0) timeline.addDirectionChangeTick(bitState);

        //Do an estimated move if a movement update was skips for whatever reason
        if((bitState & 128) == 0) {
            if(++numTicksNoMove == VentStatusTimeline.VENT_MOVE_TICK_TIME) {
                if(timeline.addEstimatedMovementTick()) updateDisplayState();
                numTicksNoMove = 0;
            }
        }

        if((bitState & 128) != 0) {
            timeline.addMovementTick(displayState, (movementBitState << 6));
            updateDisplayState();
            numTicksNoMove = 0;
        }
        if((bitState & VentStatusTimeline.IDENTIFIED_BIT_MASK) != 0) {
            timeline.addIdentifiedVentTick(displayState, bitState);
            updateDisplayState();
        }
    }
    private void updateDisplayState() {
        if(StabilityUpdateInfo.getNumPlayers() > HIGHEST_STABLE_RNG_PLAYER_COUNT) return;
        if(displayState.isAllVentsIdentified()) return;
        if(cooldownEndTick > timeline.getCurrentTick()) return;
        StatusState predictedState = timeline.getCurrentPredictionState();
        if(predictedState == null) return;
        for(int i = 0; i < NUM_VENTS; ++i) {
            VentStatus vent = displayState.getVents()[i];
            if(vent.isIdentified()) continue;
            vent.setEqualTo(predictedState.getVents()[i]);
        }
    }

    public int getFutureStabilityChange(UltimateVolcanicMineConfig.PredictionScenario scenario) {
        return displayState.getFutureStabilityChange(scenario);
    }
    public final StatusState getDisplayState() { return displayState; }
    public final VentStatusTimeline getTimeline() { return timeline; }
    public final int getCurrentTick() { return timeline.getCurrentTick(); }
    public boolean isMovementUpdateTick() { return getCurrentTick() % VentStatusTimeline.VENT_MOVE_TICK_TIME == SLOWEST_VENT_UPDATE_TICK;}
}
