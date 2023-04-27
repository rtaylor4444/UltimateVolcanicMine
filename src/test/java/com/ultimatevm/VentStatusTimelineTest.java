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
        Assert.assertNull(timeline.getInitialState());
        for(int i = 0; i < StatusState.NUM_VENTS; ++i) {
            Assert.assertEquals(timeline.getIdentifiedVentTicks()[i], -1);
            Assert.assertNull(timeline.getIdentifiedVentStates()[i]);
        }
    }

    public void initializeTest() {
        VentStatusTimeline timeline = new VentStatusTimeline();
        short[] timelineEvents = timeline.getTimeline();
        HashMap<Integer, StabilityUpdateInfo> tickToStabilityUpdateState = timeline.getStabilityUpdateStates();
        HashMap<Integer, StatusState> tickToMovementVentState = timeline.getMovementVentStates();

        timeline.addInitialState(new StatusState());
        timeline.addIdentifiedVentTick(new StatusState(), 1);
        timeline.addMovementTick(new StatusState());
        timeline.addStabilityUpdateTick(new StatusState(), 23);
        timeline.updateTick();

        //Everything should be reset back to starting values
        timeline.initialize();
        Assert.assertEquals(timeline.getCurrentTick(), 0);
        Assert.assertEquals(timeline.getCurrentStartingTick(), 0);
        Assert.assertEquals(timeline.getNumIdentifiedVents(), 0);
        Assert.assertNull(timeline.getInitialState());
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
        HashMap<Integer, StabilityUpdateInfo> tickToStabilityUpdateState = timeline.getStabilityUpdateStates();
        HashMap<Integer, StatusState> tickToMovementVentState = timeline.getMovementVentStates();

        timeline.addInitialState(new StatusState());
        timeline.addIdentifiedVentTick(new StatusState(), 1);
        timeline.addMovementTick(new StatusState());
        timeline.updateTick();

        //Stored states, events and ticks should remain the same
        timeline.reset();
        Assert.assertEquals(timeline.getCurrentTick(), 1);
        Assert.assertEquals(timeline.getCurrentStartingTick(), 1);
        Assert.assertNull(timeline.getInitialState());
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
        StatusState state1 = new StatusState();

        //Should successfully be added
        Assert.assertTrue(timeline.addInitialState(state1));
        StatusState addedState = timeline.getInitialState();
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
        HashMap<Integer, StabilityUpdateInfo> tickToStabilityUpdateState = timeline.getStabilityUpdateStates();
        StatusState state1 = new StatusState();
        int onFlag = (1 << VentStatusTimeline.STABILITY_UPDATE_FLAG);

        timeline.addStabilityUpdateTick(state1, 0);
        //Should successfully be added
        StabilityUpdateInfo addedInfo = tickToStabilityUpdateState.get(0);
        Assert.assertNotNull(addedInfo);
        Assert.assertEquals(timelineEvents[0], onFlag);
        //New copied instance should be added
        Assert.assertNotEquals(addedInfo.getStabilityUpdateState(), state1);
    }

    public void addStabilityUpdateTickCalcTest() {
        VentStatusTimeline timeline = new VentStatusTimeline();
        HashMap<Integer, StabilityUpdateInfo> tickToStabilityUpdateState = timeline.getStabilityUpdateStates();
        StatusState state1 = new StatusState();
        state1.updateVentStatus(new int[]{VentStatus.STARTING_VENT_VALUE,50, 50}, 0);
        timeline.addStabilityUpdateTick(state1, 10);

        //The added state should have a calculated estimated value
        StabilityUpdateInfo addedInfo = tickToStabilityUpdateState.get(0);
        StatusState addedState = addedInfo.getStabilityUpdateState();
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

    public void updatePreviousVentValuesTest() {
        VentStatusTimeline timeline = new VentStatusTimeline();
        StatusState state = new StatusState();
        int u = VentStatus.STARTING_VENT_VALUE;
        state.updateVentStatus(new int[]{u,u,u}, 0);
        timeline.addInitialState(state);

        //Early Stability update
        advanceTicks(timeline, 20);
        timeline.addStabilityUpdateTick(state, 16);
        //Vent B is identified
        advanceTicks(timeline, 20);
        state.updateVentStatus(new int[]{u,75,u}, 0);
        timeline.addIdentifiedVentTick(state, 2);
        //A's direction was changed
        advanceTicks(timeline, 1);
        timeline.addDirectionChangeTick(1 << 3);
        //Do movement ticks
        for(int i = 0; i < 2; ++i) {
            state.updateVentStatus(new int[]{u,74-i,u}, 1);
            timeline.addMovementTick(state);
            //Do same tick stability update
            if(i == 0) timeline.addStabilityUpdateTick(state, 16);
            advanceTicks(timeline, 10);
        }
        //Set earthquake and movement skip
        timeline.addEarthquakeEventTick();
        advanceTicks(timeline, 10);
        //A's direction was changed
        advanceTicks(timeline, 1);
        timeline.addDirectionChangeTick(1 << 3);
        //Vent A is identified
        advanceTicks(timeline, 2);
        state.updateVentStatus(new int[]{50,73,u}, 0);
        timeline.addIdentifiedVentTick(state, 1);

        //Verify results
        HashMap<Integer, StabilityUpdateInfo> tickToStabilityUpdateState = timeline.getStabilityUpdateStates();
        HashMap<Integer, StatusState> tickToMovementVentState = timeline.getMovementVentStates();
        //Early Stability update should remain unchanged
        StatusState tick20StabState = tickToStabilityUpdateState.get(20).getStabilityUpdateState();
        Assert.assertEquals(tick20StabState.getVents()[0].getActualValue(), u);
        //Movement ticks should have the correct values
        Assert.assertEquals(tickToMovementVentState.get(41).getVents()[0].getActualValue(), 49);
        Assert.assertEquals(tickToMovementVentState.get(51).getVents()[0].getActualValue(), 50);
        //Second stability update will have a value
        StatusState tick41StabState = tickToStabilityUpdateState.get(41).getStabilityUpdateState();
        Assert.assertEquals(tick41StabState.getVents()[0].getActualValue(), 49);
        //it should also have a new estimated value
        Assert.assertTrue(tick41StabState.getVents()[2].isRangeDefined());
    }

    public void updatePreviousVentValuesOnMovementTickTest() {
        VentStatusTimeline timeline = new VentStatusTimeline();
        StatusState state = new StatusState();
        int u = VentStatus.STARTING_VENT_VALUE;
        state.updateVentStatus(new int[]{u,u,u}, 0);
        timeline.addInitialState(state);

        //Stability update
        advanceTicks(timeline, 20);
        timeline.addStabilityUpdateTick(state, 23);
        //Vents A B is identified
        advanceTicks(timeline, 1);
        state.updateVentStatus(new int[]{50,50,u}, 0);
        timeline.addIdentifiedVentTick(state, 3);

        //Stability update will not update since there are no movement ticks
        HashMap<Integer, StabilityUpdateInfo> tickToStabilityUpdateState = timeline.getStabilityUpdateStates();
        StatusState tick20StabState = tickToStabilityUpdateState.get(20).getStabilityUpdateState();
        Assert.assertEquals(tick20StabState.getVents()[0].getActualValue(), u);
        Assert.assertEquals(tick20StabState.getVents()[1].getActualValue(), u);
        Assert.assertFalse(tick20StabState.getVents()[2].isRangeDefined());

        //Do movement tick
        advanceTicks(timeline, 1);
        state.updateVentStatus(new int[]{49,50,u}, 0);
        timeline.addMovementTick(state);

        //Stability update will be updated with new values
        Assert.assertEquals(tick20StabState.getVents()[0].getActualValue(), 50);
        Assert.assertEquals(tick20StabState.getVents()[1].getActualValue(), 50);
        Assert.assertTrue(tick20StabState.getVents()[2].isRangeDefined());
    }

    public void updatePreviousVentValuesMissingMovementTest() {
        VentStatusTimeline timeline = new VentStatusTimeline();
        StatusState state = new StatusState();
        int u = VentStatus.STARTING_VENT_VALUE;
        state.updateVentStatus(new int[]{u,u,u}, 0);
        timeline.addInitialState(state);

        //1st Stability update
        advanceTicks(timeline, 20);
        timeline.addStabilityUpdateTick(state, 19);
        //Vents B is identified
        advanceTicks(timeline, 5);
        state.updateVentStatus(new int[]{u,60,u}, 0);
        timeline.addIdentifiedVentTick(state, 2);
        //Do movement tick
        advanceTicks(timeline, 4);
        state.updateVentStatus(new int[]{u,59,u}, 0);
        timeline.addMovementTick(state);

        //1st Stability update should be changed
        //TODO: Fix code so it will be changed
        HashMap<Integer, StabilityUpdateInfo> tickToStabilityUpdateState = timeline.getStabilityUpdateStates();
        StatusState tick20StabState = tickToStabilityUpdateState.get(20).getStabilityUpdateState();
        Assert.assertEquals(tick20StabState.getVents()[0].getActualValue(), u);
        Assert.assertEquals(tick20StabState.getVents()[1].getActualValue(), u);
        Assert.assertEquals(tick20StabState.getVents()[2].getActualValue(), u);

        //Skipped movement updates due to a freeze
        advanceTicks(timeline, 30);
        state.updateVentStatus(new int[]{u,58,u}, 0);
        timeline.addMovementTick(state);
        //Reachable Stability update
        advanceTicks(timeline, 10);
        timeline.addStabilityUpdateTick(state, 18);
        //Vent A is identified
        advanceTicks(timeline, 2);
        state.updateVentStatus(new int[]{55,58,u}, 0);
        timeline.addIdentifiedVentTick(state, 1);

        //1st Stability update should be the same as before - skipped movement updates
        Assert.assertEquals(tick20StabState.getVents()[0].getActualValue(), u);
        Assert.assertEquals(tick20StabState.getVents()[1].getActualValue(), u);
        Assert.assertEquals(tick20StabState.getVents()[2].getActualValue(), u);
        //2nd Stability update should be changed
        StatusState tick69StabState = tickToStabilityUpdateState.get(69).getStabilityUpdateState();
        Assert.assertEquals(tick69StabState.getVents()[0].getActualValue(), 55);
        Assert.assertEquals(tick69StabState.getVents()[1].getActualValue(), 58);
        Assert.assertEquals(tick69StabState.getVents()[2].getActualValue(), u);
        Assert.assertTrue(tick69StabState.getVents()[2].isRangeDefined());
    }

    public void sandbox() {
        VentStatusTimeline timeline = new VentStatusTimeline();
        StatusState state = new StatusState();
        int u = VentStatus.STARTING_VENT_VALUE;
        state.updateVentStatus(new int[]{u,u,u}, 3);
        timeline.addInitialState(state);

        //24: Early Stability update
        advanceTicks(timeline, 24);
        timeline.addStabilityUpdateTick(state, 14);
        //33: A Vent was identified to be 66
        advanceTicks(timeline, 9);
        state.updateVentStatus(new int[]{66,u,u}, 3);
        timeline.addIdentifiedVentTick(state, 1);
        //39: Earthquake and movement skip
        advanceTicks(timeline, 6);
        timeline.addEarthquakeEventTick();
        //47: A's direction was changed
        advanceTicks(timeline, 8);
        timeline.addDirectionChangeTick(1 << 3);
        //49: Same tick movement and stability update of 11
        advanceTicks(timeline, 2);
        state.updateVentStatus(new int[]{64,u,u}, 2);
        timeline.addMovementTick(state);
        timeline.addStabilityUpdateTick(state, 11);
        //59: Movement update
        advanceTicks(timeline, 10);
        state.updateVentStatus(new int[]{62,u,u}, 2);
        timeline.addMovementTick(state);
        //69: Movement update
        advanceTicks(timeline, 10);
        state.updateVentStatus(new int[]{60,u,u}, 2);
        timeline.addMovementTick(state);
        //74: Stability update of 10
        advanceTicks(timeline, 5);
        timeline.addStabilityUpdateTick(state, 10);
        //79: Movement update
        advanceTicks(timeline, 5);
        state.updateVentStatus(new int[]{58,u,u}, 2);
        timeline.addMovementTick(state);
        //80: B Vent was identified to be 77
        advanceTicks(timeline, 1);
        state.updateVentStatus(new int[]{58,77,u}, 3);
        timeline.addIdentifiedVentTick(state, 2);

        //Verify results
        HashMap<Integer, StabilityUpdateInfo> tickToStabilityUpdateState = timeline.getStabilityUpdateStates();
        HashMap<Integer, StatusState> tickToMovementVentState = timeline.getMovementVentStates();
        StatusState tick74StabState = tickToStabilityUpdateState.get(74).getStabilityUpdateState();
        StatusState predictedState = timeline.getTimelinePredictionState();
    }
}
