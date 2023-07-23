package com.ultimatevm;

import net.runelite.client.util.Text;
import org.testng.annotations.Test;
import org.testng.Assert;

@Test()
public class VentStatusPredicterTest {

    int u = VentStatus.STARTING_VENT_VALUE;

    public void constructorTest() {
        VentStatusPredicter predicter = new VentStatusPredicter();
        Assert.assertNotNull(predicter.getDisplayState());
        Assert.assertNotNull(predicter.getTimeline());
    }

    public void initializeTest() {
        VentStatusPredicter predicter = new VentStatusPredicter();
        predicter.updateVentStatus(new int[]{VentStatus.STARTING_VENT_VALUE, 40, 50}, 7);
        predicter.makeStatusState(10);
        final StatusState prevState = predicter.getDisplayState();
        final VentStatusTimeline prevTimeline = predicter.getTimeline();
        predicter.initialize();

        Assert.assertNotEquals(predicter.getDisplayState(), prevState);
        Assert.assertNotEquals(predicter.getTimeline(), prevTimeline);
        for(int i = 0; i < StatusState.NUM_VENTS; ++i) {
            Assert.assertFalse(predicter.getDisplayState().getVents()[i].isIdentified());
            Assert.assertEquals(predicter.getDisplayState().getVents()[i].getDirection(), 0);
        }

    }

    public void resetTest() {
        VentStatusPredicter predicter = new VentStatusPredicter();
        predicter.updateVentStatus(new int[]{50, 50, 50}, 7);

        //Ensure reset is done properly
        predicter.reset();
        Assert.assertEquals(predicter.getTimeline().getNumIdentifiedVents(), 0);

        //Reset should not work the second time
        predicter.updateVentStatus(new int[]{u, u, u}, 7);
        predicter.updateVentStatus(new int[]{50, 50, 50}, 7);
        predicter.reset();
        Assert.assertEquals(predicter.getTimeline().getNumIdentifiedVents(), 3);
    }

    public void updateVentStatusTest() {
        int u = VentStatus.STARTING_VENT_VALUE;
        VentStatusPredicter predicter = new VentStatusPredicter();
        //Make sure values are set
        int[] values = {u, 96, u};
        predicter.updateVentStatus(values, 7);
        for(int i = 0; i < StatusState.NUM_VENTS; ++i) {
            Assert.assertEquals(predicter.getDisplayState().getVents()[i].getActualValue(), values[i]);
            Assert.assertEquals(predicter.getDisplayState().getVents()[i].getDirection(), 1);
        }
    }

    public void makeStatusStateTest() {
        VentStatusPredicter predicter = new VentStatusPredicter();
        int u = VentStatus.STARTING_VENT_VALUE;
        //Right amount of vents are known a range should be made!
        predicter.updateVentStatus(new int[]{u, 40, 50}, 7);
        predicter.getTimeline().updateTick();
        predicter.makeStatusState(10);
        Assert.assertFalse(predicter.getDisplayState().getVents()[0].isIdentified());
        Assert.assertTrue(predicter.getDisplayState().getVents()[0].isRangeDefined());
    }

    public void updateVentStatusMovementTest() {
        VentStatusPredicter predicter = new VentStatusPredicter();
        int u = VentStatus.STARTING_VENT_VALUE;
        predicter.updateVentStatus(new int[]{u, 50, 50}, 7);
        predicter.getTimeline().updateTick();
        predicter.makeStatusState(23);
        Assert.assertEquals(predicter.getDisplayState().getVents()[0].getLowerBoundStart(), 47);
        Assert.assertEquals(predicter.getDisplayState().getVents()[0].getLowerBoundEnd(), 53);
        Assert.assertEquals(predicter.getDisplayState().getVents()[0].getUpperBoundStart(), 47);
        Assert.assertEquals(predicter.getDisplayState().getVents()[0].getUpperBoundEnd(), 53);

        //Movement should be updated here
        for(int i = 0; i < VentStatusTimeline.VENT_MOVE_TICK_TIME; ++i) {
            predicter.updateVentStatus(new int[]{u, 50, 50}, 7);
            predicter.getTimeline().updateTick();
        }
        Assert.assertEquals(predicter.getDisplayState().getVents()[0].getLowerBoundStart(), 48);
        Assert.assertEquals(predicter.getDisplayState().getVents()[0].getLowerBoundEnd(), 54);
        Assert.assertEquals(predicter.getDisplayState().getVents()[0].getUpperBoundStart(), 48);
        Assert.assertEquals(predicter.getDisplayState().getVents()[0].getUpperBoundEnd(), 54);

        //Movement should not update and values remain the same
        for(int i = 0; i < 5; ++i) {
            predicter.updateVentStatus(new int[]{u, 50, 50}, 7);
            predicter.getTimeline().updateTick();
        }
        Assert.assertEquals(predicter.getDisplayState().getVents()[0].getLowerBoundStart(), 48);
        Assert.assertEquals(predicter.getDisplayState().getVents()[0].getLowerBoundEnd(), 54);
        Assert.assertEquals(predicter.getDisplayState().getVents()[0].getUpperBoundStart(), 48);
        Assert.assertEquals(predicter.getDisplayState().getVents()[0].getUpperBoundEnd(), 54);
    }

    public void processVentChangeStateInitialTest() {
        VentStatusPredicter predicter = new VentStatusPredicter();
        int u = VentStatus.STARTING_VENT_VALUE;

        //Ensure the initial state is created
        predicter.updateVentStatus(new int[]{u, u, u}, 7);
        Assert.assertNotNull(predicter.getTimeline().getInitialState());
    }

    public void processVentChangeStateIdentifyTest() {
        VentStatusPredicter predicter = new VentStatusPredicter();
        final VentStatusTimeline timeline = predicter.getTimeline();
        int u = VentStatus.STARTING_VENT_VALUE;

        //Ensure timeline identified vent event was called
        predicter.updateVentStatus(new int[]{u, u, u}, 7);
        predicter.getTimeline().updateTick();
        predicter.updateVentStatus(new int[]{50, 50, 50}, 7);
        Assert.assertNotNull(predicter.getTimeline().getIdentifiedVentStates()[0]);
        Assert.assertNotNull(predicter.getTimeline().getIdentifiedVentStates()[1]);
        Assert.assertNotNull(predicter.getTimeline().getIdentifiedVentStates()[2]);
    }

    public void processVentChangeStateDirectionTest() {
        VentStatusPredicter predicter = new VentStatusPredicter();
        final VentStatusTimeline timeline = predicter.getTimeline();
        int u = VentStatus.STARTING_VENT_VALUE;

        //Ensure timeline direction change event was called
        predicter.updateVentStatus(new int[]{u, u, u}, 7);
        predicter.getTimeline().updateTick();
        predicter.updateVentStatus(new int[]{u, u, u}, 0);
        Assert.assertNotEquals(timeline.getTimeline()[1] & (1 << VentStatusTimeline.DIRECTION_CHANGED_FLAG), 0);
    }

    public void processVentChangeStateMovementTest() {
        VentStatusPredicter predicter = new VentStatusPredicter();
        final VentStatusTimeline timeline = predicter.getTimeline();
        int u = VentStatus.STARTING_VENT_VALUE;

        //Ensure timeline direction change event was called
        predicter.updateVentStatus(new int[]{50, u, u}, 7);
        //Movement by 1
        predicter.getTimeline().updateTick();
        predicter.updateVentStatus(new int[]{51, u, u}, 7);
        Assert.assertNotEquals(timeline.getTimeline()[1] & (1 << VentStatusTimeline.MOVEMENT_UPDATE_FLAG), 0);
        //Movement by 2
        predicter.getTimeline().updateTick();
        predicter.updateVentStatus(new int[]{53, u, u}, 7);
        Assert.assertNotEquals(timeline.getTimeline()[2] & (1 << VentStatusTimeline.MOVEMENT_UPDATE_FLAG), 0);
    }

    public void processVentChangeStateResetTest() {
        VentStatusPredicter predicter = new VentStatusPredicter();
        final VentStatusTimeline timeline = predicter.getTimeline();
        int u = VentStatus.STARTING_VENT_VALUE;

        //Ensure reset event occurs
        predicter.updateVentStatus(new int[]{50, 50, 50}, 7);
        predicter.updateVentStatus(new int[]{u, u, u}, 7);
        Assert.assertTrue(predicter.getDisplayState().hasDoneVMReset());
        Assert.assertEquals(predicter.getDisplayState().getNumIdentifiedVents(), 0);
        //Timeline should remain unaffected
        Assert.assertEquals(predicter.getTimeline().getNumIdentifiedVents(), 3);
    }

    public void getVentStatusTextTest() {
        //Undefined range vents return the default text
        VentStatusPredicter predicter = new VentStatusPredicter();
        Assert.assertEquals(predicter.getVentStatusText(0, ""), "");

        //Identified vents return the default text
        predicter.initialize();
        predicter.updateVentStatus(new int[]{0, 50, 50}, 7);
        Assert.assertEquals(predicter.getVentStatusText(0, ""), "");

        //Only a single value should be displayed
        predicter.initialize();
        predicter.updateVentStatus(new int[]{VentStatus.STARTING_VENT_VALUE, 51, 51}, 7);
        predicter.getDisplayState().getVents()[0].setLowerBoundRange(50, 50);
        predicter.getDisplayState().getVents()[0].setUpperBoundRange(50, 50);
        String result = predicter.getVentStatusText(0, "A: ");
        result = Text.removeTags(result);
        Assert.assertEquals(result, "A: 50%");

        //Only a single range should be displayed
        predicter.initialize();
        predicter.updateVentStatus(new int[]{VentStatus.STARTING_VENT_VALUE, 50, 51}, 7);
        predicter.getDisplayState().getVents()[0].setLowerBoundRange(49, 51);
        predicter.getDisplayState().getVents()[0].setUpperBoundRange(49, 51);
        result = Text.removeTags(predicter.getVentStatusText(0, "A: "));
        Assert.assertEquals(result, "A: 49-51%");

        //Two values should be displayed
        predicter.initialize();
        predicter.reset();
        predicter.updateVentStatus(new int[]{VentStatus.STARTING_VENT_VALUE, 50, 50}, 7);
        predicter.getDisplayState().getVents()[0].setLowerBoundRange(0, 0);
        predicter.getDisplayState().getVents()[0].setUpperBoundRange(100, 100);
        result = Text.removeTags(predicter.getVentStatusText(0, "A: "));
        Assert.assertEquals(result, "A: 0% 100%");

        //Two ranges should be displayed
        predicter.initialize();
        predicter.updateVentStatus(new int[]{VentStatus.STARTING_VENT_VALUE, 50, 50}, 7);
        predicter.getDisplayState().getVents()[0].setLowerBoundRange(45, 47);
        predicter.getDisplayState().getVents()[0].setUpperBoundRange(53, 55);
        result = Text.removeTags(predicter.getVentStatusText(0, "A: "));
        Assert.assertEquals(result, "A: 45-47 53-55");

        //One value one range should be displayed
        predicter.initialize();
        predicter.updateVentStatus(new int[]{VentStatus.STARTING_VENT_VALUE, 50, 50}, 7);
        predicter.getDisplayState().getVents()[0].setLowerBoundRange(45, 45);
        predicter.getDisplayState().getVents()[0].setUpperBoundRange(53, 55);
        result = Text.removeTags(predicter.getVentStatusText(0, "A: "));
        Assert.assertEquals(result, "A: 45% 53-55");

        predicter.initialize();
        predicter.updateVentStatus(new int[]{VentStatus.STARTING_VENT_VALUE, 50, 50}, 7);
        predicter.getDisplayState().getVents()[0].setLowerBoundRange(45, 47);
        predicter.getDisplayState().getVents()[0].setUpperBoundRange(55, 55);
        result = Text.removeTags(predicter.getVentStatusText(0, "A: "));
        Assert.assertEquals(result, "A: 45-47 55%");
    }

}
