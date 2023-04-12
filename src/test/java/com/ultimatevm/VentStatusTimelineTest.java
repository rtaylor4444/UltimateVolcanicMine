package com.ultimatevm;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.HashMap;

@Test()
public class VentStatusTimelineTest {

    private void advanceTicks(VentStatusTimeline timeline, int numTicks) {
        for(int i = 0; i < numTicks; ++i) timeline.updateTick();
    }
    public void constructorTest() {
        VentStatusTimeline timeline = new VentStatusTimeline();
        Assert.assertEquals(timeline.getCurrentTick(), 0);
        Assert.assertEquals(timeline.getCurrentStartingTick(), 0);
        Assert.assertEquals(timeline.getNumIdentifiedVents(), 0);
        Assert.assertNotNull(timeline.getTimeline());
        Assert.assertNotNull(timeline.getMovementVentStates());
        Assert.assertNotNull(timeline.getStabilityUpdateStates());
        Assert.assertNotNull(timeline.getIdentifiedVentTicks());
        Assert.assertNotNull(timeline.getIdentifiedVentStates());
        for(int i = 0; i < StatusState.NUM_VENTS; ++i) {
            Assert.assertEquals(timeline.getIdentifiedVentTicks()[i], -1);
            Assert.assertNull(timeline.getIdentifiedVentStates()[i]);
        }
    }

    public void initializeTest() {
        VentStatusTimeline timeline = new VentStatusTimeline();
        short[] timelineEvents = timeline.getTimeline();
        HashMap<Integer, StatusState> tickToStabilityUpdateState = timeline.getStabilityUpdateStates();
        HashMap<Integer, StatusState> tickToMovementVentState = timeline.getMovementVentStates();

        timeline.addInitialState(new StatusState());
        timeline.addIdentifiedVentTick(new StatusState(), 1);
        timeline.addMovementTick(new StatusState());
        timeline.updateTick();

        //Everything should be reset back to starting values
        timeline.initialize();
        Assert.assertEquals(timeline.getCurrentTick(), 0);
        Assert.assertEquals(timeline.getCurrentStartingTick(), 0);
        Assert.assertEquals(timeline.getNumIdentifiedVents(), 0);
        Assert.assertNotEquals(timelineEvents, timeline.getTimeline());
        Assert.assertNotEquals(tickToMovementVentState, timeline.getMovementVentStates());
        Assert.assertNotEquals(tickToStabilityUpdateState, timeline.getStabilityUpdateStates());
        for(int i = 0; i < StatusState.NUM_VENTS; ++i) {
            Assert.assertEquals(timeline.getIdentifiedVentTicks()[i], -1);
            Assert.assertNull(timeline.getIdentifiedVentStates()[i]);
        }
    }

    public void resetTest() {
        VentStatusTimeline timeline = new VentStatusTimeline();
        short[] timelineEvents = timeline.getTimeline();
        HashMap<Integer, StatusState> tickToStabilityUpdateState = timeline.getStabilityUpdateStates();
        HashMap<Integer, StatusState> tickToMovementVentState = timeline.getMovementVentStates();

        timeline.addInitialState(new StatusState());
        timeline.addIdentifiedVentTick(new StatusState(), 1);
        timeline.addMovementTick(new StatusState());
        timeline.updateTick();

        //Stored states, events and ticks should remain the same
        timeline.reset();
        Assert.assertEquals(timeline.getCurrentTick(), 1);
        Assert.assertEquals(timeline.getCurrentStartingTick(), 1);
        Assert.assertEquals(timelineEvents, timeline.getTimeline());
        Assert.assertEquals(tickToMovementVentState, timeline.getMovementVentStates());
        Assert.assertEquals(tickToStabilityUpdateState, timeline.getStabilityUpdateStates());
        //Identified vents and states should be cleared
        Assert.assertEquals(timeline.getNumIdentifiedVents(), 0);
        for(int i = 0; i < StatusState.NUM_VENTS; ++i) {
            Assert.assertEquals(timeline.getIdentifiedVentTicks()[i], -1);
            Assert.assertNull(timeline.getIdentifiedVentStates()[i]);
        }
    }

    public void addInitialStateTest() {
        VentStatusTimeline timeline = new VentStatusTimeline();
        HashMap<Integer, StatusState> tickToStabilityUpdateState = timeline.getStabilityUpdateStates();
        StatusState state1 = new StatusState();

        //Should successfully be added
        Assert.assertTrue(timeline.addInitialState(state1));
        StatusState addedState = tickToStabilityUpdateState.get(0);
        Assert.assertNotNull(addedState);
        //New copied instance should be added
        Assert.assertNotEquals(addedState, state1);
        //Should fail if a second initial state is added since one exists
        Assert.assertFalse(timeline.addInitialState(state1));
    }

    public void addIdentifiedVentTickTest() {
        VentStatusTimeline timeline = new VentStatusTimeline();
        short[] timelineEvents = timeline.getTimeline();
        int[] identifiedVentTicks = timeline.getIdentifiedVentTicks();
        StatusState[] identifiedVentStates = timeline.getIdentifiedVentStates();
        StatusState state1 = new StatusState();
        int onFlag = (1 << VentStatusTimeline.IDENTIFIED_VENT_FLAG);
        int garbageValue = 64;

        timeline.addIdentifiedVentTick(state1, 1 | garbageValue);
        Assert.assertEquals(timelineEvents[0], 1 | onFlag);
        Assert.assertEquals(identifiedVentTicks[0], 0);
        Assert.assertNotNull(identifiedVentStates[0]);
        Assert.assertNotEquals(identifiedVentStates[0], state1);
        Assert.assertEquals(timeline.getNumIdentifiedVents(), 1);

        timeline.addIdentifiedVentTick(state1, 2 | garbageValue);
        Assert.assertEquals(timelineEvents[0], 3 | onFlag);
        Assert.assertEquals(identifiedVentTicks[1], 0);
        Assert.assertNotNull(identifiedVentStates[1]);
        Assert.assertNotEquals(identifiedVentStates[1], state1);
        Assert.assertEquals(timeline.getNumIdentifiedVents(), 2);

        timeline.addIdentifiedVentTick(state1, 4 | garbageValue);
        Assert.assertEquals(timelineEvents[0], 7 | onFlag);
        Assert.assertEquals(identifiedVentTicks[2], 0);
        Assert.assertNotNull(identifiedVentStates[2]);
        Assert.assertNotEquals(identifiedVentStates[2], state1);
        Assert.assertEquals(timeline.getNumIdentifiedVents(), 3);
    }

    public void addIdentifiedVentTickInvalidTest() {
        VentStatusTimeline timeline = new VentStatusTimeline();
        short[] timelineEvents = timeline.getTimeline();
        int[] identifiedVentTicks = timeline.getIdentifiedVentTicks();
        StatusState[] identifiedVentStates = timeline.getIdentifiedVentStates();
        StatusState state1 = new StatusState();

        timeline.addIdentifiedVentTick(state1, 64);
        Assert.assertEquals(timelineEvents[0], 0);
        for(int i = 0; i < StatusState.NUM_VENTS; ++i) {
            Assert.assertEquals(identifiedVentTicks[0], -1);
            Assert.assertNull(identifiedVentStates[0]);
        }
        Assert.assertEquals(timeline.getNumIdentifiedVents(), 0);
    }

    public void addIdentifiedVentTickReassignTest() {
        VentStatusTimeline timeline = new VentStatusTimeline();
        short[] timelineEvents = timeline.getTimeline();
        int[] identifiedVentTicks = timeline.getIdentifiedVentTicks();
        StatusState[] identifiedVentStates = timeline.getIdentifiedVentStates();


        timeline.addIdentifiedVentTick(new StatusState(), 1);
        StatusState state = identifiedVentStates[0];
        timeline.updateTick();
        timeline.addIdentifiedVentTick(state, 1);

        //Values should be unchanged from last assignment
        Assert.assertEquals(timelineEvents[1], 0);
        Assert.assertEquals(identifiedVentTicks[0], 0);
        Assert.assertEquals(identifiedVentStates[0], state);
        Assert.assertEquals(timeline.getNumIdentifiedVents(), 1);
    }

    public void addDirectionChangeTickTest() {
        VentStatusTimeline timeline = new VentStatusTimeline();
        short[] timelineEvents = timeline.getTimeline();
        int onFlag = (1 << VentStatusTimeline.DIRECTION_CHANGED_FLAG);

        timeline.addDirectionChangeTick((1 << 3) | 1);
        Assert.assertEquals(timelineEvents[0], (1 << 3) | onFlag);
        timeline.updateTick();
        timeline.addDirectionChangeTick((1 << 3) | 1);
        Assert.assertEquals(timelineEvents[1], (1 << 3) | onFlag);
    }

    public void addMovementTickTest() {
        VentStatusTimeline timeline = new VentStatusTimeline();
        short[] timelineEvents = timeline.getTimeline();
        HashMap<Integer, StatusState> tickToMovementVentState = timeline.getMovementVentStates();
        StatusState state1 = new StatusState();
        int onFlag = (1 << VentStatusTimeline.MOVEMENT_UPDATE_FLAG);

        timeline.addMovementTick(state1);
        //Should successfully be added
        StatusState addedState = tickToMovementVentState.get(0);
        Assert.assertNotNull(addedState);
        Assert.assertEquals(timelineEvents[0], onFlag);
        //New copied instance should be added
        Assert.assertNotEquals(addedState, state1);
    }

    public void addStabilityUpdateTickTest() {
        VentStatusTimeline timeline = new VentStatusTimeline();
        short[] timelineEvents = timeline.getTimeline();
        HashMap<Integer, StatusState> tickToStabilityUpdateState = timeline.getStabilityUpdateStates();
        StatusState state1 = new StatusState();
        int onFlag = (1 << VentStatusTimeline.STABILITY_UPDATE_FLAG);

        timeline.addStabilityUpdateTick(state1, 0);
        //Should successfully be added
        StatusState addedState = tickToStabilityUpdateState.get(0);
        Assert.assertNotNull(addedState);
        Assert.assertEquals(timelineEvents[0], onFlag);
        //New copied instance should be added
        Assert.assertNotEquals(addedState, state1);
    }

    public void addStabilityUpdateTickCalcTest() {
        VentStatusTimeline timeline = new VentStatusTimeline();
        HashMap<Integer, StatusState> tickToStabilityUpdateState = timeline.getStabilityUpdateStates();
        StatusState state1 = new StatusState();
        state1.updateVentStatus(new int[]{VentStatus.STARTING_VENT_VALUE,50, 50}, 0);
        timeline.addStabilityUpdateTick(state1, 0);

        //The added state should have a calculated estimated value
        StatusState addedState = tickToStabilityUpdateState.get(0);
        Assert.assertTrue(addedState.getVents()[0].isRangeDefined());
    }

    public void getTimelinePredictionStateIdentifyTest() {
        VentStatusTimeline timeline = new VentStatusTimeline();
        StatusState state = new StatusState();
        int u = VentStatus.STARTING_VENT_VALUE;
        state.updateVentStatus(new int[]{u,u,u}, 0);
        timeline.addInitialState(state);

        //Vent B is identified
        advanceTicks(timeline, 10);
        state.updateVentStatus(new int[]{u,50,u}, 0);
        timeline.addIdentifiedVentTick(state, 2);

        StatusState resultState = timeline.getTimelinePredictionState();
        Assert.assertEquals(resultState.getVents()[0].getActualValue(), u);
        Assert.assertEquals(resultState.getVents()[1].getActualValue(), 50);
        Assert.assertEquals(resultState.getVents()[2].getActualValue(), u);

        //Vent A is identified
        advanceTicks(timeline, 10);
        state.updateVentStatus(new int[]{50,50,u}, 0);
        timeline.addIdentifiedVentTick(state, 1);

        resultState = timeline.getTimelinePredictionState();
        Assert.assertEquals(resultState.getVents()[0].getActualValue(), 50);
        Assert.assertEquals(resultState.getVents()[1].getActualValue(), 50);
        Assert.assertEquals(resultState.getVents()[2].getActualValue(), u);

        //Vent C is identified
        advanceTicks(timeline, 10);
        state.updateVentStatus(new int[]{50,50,50}, 0);
        timeline.addIdentifiedVentTick(state, 4);

        resultState = timeline.getTimelinePredictionState();
        Assert.assertEquals(resultState.getVents()[0].getActualValue(), 50);
        Assert.assertEquals(resultState.getVents()[1].getActualValue(), 50);
        Assert.assertEquals(resultState.getVents()[2].getActualValue(), 50);
    }

    public void getTimelinePredictionStateDirectionTest() {
        VentStatusTimeline timeline = new VentStatusTimeline();
        StatusState state = new StatusState();
        int u = VentStatus.STARTING_VENT_VALUE;
        state.updateVentStatus(new int[]{u,u,u}, 0);
        timeline.addInitialState(state);

        advanceTicks(timeline, 5);
        timeline.addDirectionChangeTick(1 << 3);

        StatusState resultState = timeline.getTimelinePredictionState();
        Assert.assertEquals(resultState.getVents()[0].getDirection(), 1);
        Assert.assertEquals(resultState.getVents()[1].getDirection(), -1);
        Assert.assertEquals(resultState.getVents()[2].getDirection(), -1);

        advanceTicks(timeline, 5);
        timeline.addDirectionChangeTick(2 << 3);

        resultState = timeline.getTimelinePredictionState();
        Assert.assertEquals(resultState.getVents()[0].getDirection(), 1);
        Assert.assertEquals(resultState.getVents()[1].getDirection(), 1);
        Assert.assertEquals(resultState.getVents()[2].getDirection(), -1);

        advanceTicks(timeline, 5);
        timeline.addDirectionChangeTick(4 << 3);

        resultState = timeline.getTimelinePredictionState();
        Assert.assertEquals(resultState.getVents()[0].getDirection(), 1);
        Assert.assertEquals(resultState.getVents()[1].getDirection(), 1);
        Assert.assertEquals(resultState.getVents()[2].getDirection(), 1);

        advanceTicks(timeline, 5);
        timeline.addDirectionChangeTick(7 << 3);

        resultState = timeline.getTimelinePredictionState();
        Assert.assertEquals(resultState.getVents()[0].getDirection(), -1);
        Assert.assertEquals(resultState.getVents()[1].getDirection(), -1);
        Assert.assertEquals(resultState.getVents()[2].getDirection(), -1);
    }

    public void getTimelinePredictionStateMovementTest() {
        VentStatusTimeline timeline = new VentStatusTimeline();
        StatusState state = new StatusState();
        int u = VentStatus.STARTING_VENT_VALUE;
        state.updateVentStatus(new int[]{50,u,u}, 0);
        timeline.addInitialState(state);

        for(int i = 0; i < 5; ++i) {
            advanceTicks(timeline, 10);
            state.updateVentStatus(new int[]{50-(i+1),u,u}, 0);
            timeline.addMovementTick(state);

            StatusState result = timeline.getTimelinePredictionState();
            Assert.assertEquals(result.getVents()[0].getActualValue(), 50-(i+1));
            Assert.assertEquals(result.getVents()[1].getActualValue(), u);
            Assert.assertEquals(result.getVents()[2].getActualValue(), u);
        }
    }

    public void getTimelinePredictionStateStabilityTest() {
        VentStatusTimeline timeline = new VentStatusTimeline();
        StatusState state = new StatusState();
        int u = VentStatus.STARTING_VENT_VALUE;
        state.updateVentStatus(new int[]{u,75,75}, 0);
        timeline.addInitialState(state);

        advanceTicks(timeline, 25);
        timeline.addStabilityUpdateTick(state, 0);

        //Ensure we get a calculated value
        StatusState result = timeline.getTimelinePredictionState();
        VentStatus predictedVent = result.getVents()[0];
        Assert.assertEquals(predictedVent.getLowerBoundStart(), 26);
        Assert.assertEquals(predictedVent.getLowerBoundEnd(), 28);
        Assert.assertEquals(predictedVent.getUpperBoundStart(), 72);
        Assert.assertEquals(predictedVent.getUpperBoundEnd(), 74);
    }
}
