package com.ultimatevm;

import static com.ultimatevm.StatusState.*;
import static com.ultimatevm.VentStatus.*;

import java.util.ArrayList;

public class VentStatusPredicter {
    public static final int SLOWEST_VENT_UPDATE_TICK = VentStatusTimeline.VENT_MOVE_TICK_TIME-1;
    public static final int HIGHEST_STABLE_RNG_PLAYER_COUNT = 8;

    private VentStatusTimeline timeline;
    private StatusState displayState;
    private boolean hasDoneFinalLog;
    private int numTicksNoMove;


    public VentStatusPredicter() {
        initialize();
    }
    public void initialize() {
        timeline = new VentStatusTimeline();
        displayState = new StatusState();
        hasDoneFinalLog = false;
    }
    public void reset() {
        if(!displayState.hasDoneVMReset()) {
            timeline.reset();
        }
        displayState.doVMReset();
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
            if(!getDisplayState().hasDoneVMReset()) log();
            reset();
        }
        if((bitState & VentStatusTimeline.DIRECTION_CHANGED_BIT_MASK) != 0) timeline.addDirectionChangeTick(bitState);

        //Do an estimated move if a movement update was skips for whatever reason
        if(movementBitState == 0) {
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
        StatusState predictedState = timeline.getCurrentPredictionState();
        if(predictedState == null) return;
        for(int i = 0; i < NUM_VENTS; ++i) {
            VentStatus vent = displayState.getVents()[i];
            if(vent.isIdentified()) continue;
            vent.setEqualTo(predictedState.getVents()[i]);
        }
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
                    ventUpdate = Math.min(getStabilityInfluence(avgLower), getStabilityInfluence(avgUpper));
                    break;
                case BEST_CASE:
                    ventUpdate = Math.max(getStabilityInfluence(avgLower), getStabilityInfluence(avgUpper));
                    break;
                default:
                    //Average-case (crap)
                    ventUpdate = (getStabilityInfluence(avgLower) + getStabilityInfluence(avgUpper)) / 2;
                    break;
            }

            if(estimatedVentValue == Integer.MAX_VALUE) estimatedVentValue = ventUpdate;
            else estimatedVentValue += ventUpdate;
        }

        if(estimatedVentValue != Integer.MAX_VALUE)
            totalVentValue += estimatedVentValue;
        return calcStabilityChange(totalVentValue) + StabilityUpdateInfo.getMinRNGVariation();
    }
    public final StatusState getDisplayState() { return displayState; }
    public final VentStatusTimeline getTimeline() { return timeline; }
    public final int getCurrentTick() { return timeline.getCurrentTick(); }
    public boolean isMovementUpdateTick() { return getCurrentTick() % VentStatusTimeline.VENT_MOVE_TICK_TIME == SLOWEST_VENT_UPDATE_TICK;}
    public void log() {
        if(hasDoneFinalLog) return;
        timeline.log();
        if(displayState.hasDoneVMReset()) hasDoneFinalLog = true;
    }
}
