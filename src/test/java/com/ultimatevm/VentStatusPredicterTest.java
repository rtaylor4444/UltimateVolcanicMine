package com.ultimatevm;

import net.runelite.client.util.Text;
import org.testng.annotations.Test;
import org.testng.Assert;

@Test()
public class VentStatusPredicterTest {

    public void constructorTest() {
        VentStatusPredicter predicter = new VentStatusPredicter();
        Assert.assertNotNull(predicter.getDisplayState());
    }

    public void initializeTest() {
        VentStatusPredicter predicter = new VentStatusPredicter();
        predicter.updateVentStatus(new int[]{VentStatus.STARTING_VENT_VALUE, 40, 50}, 7);
        predicter.makeStatusState(10);
        predicter.initialize();

        Assert.assertNotNull(predicter.getDisplayState());
        for(int i = 0; i < StatusState.NUM_VENTS; ++i) {
            Assert.assertFalse(predicter.getDisplayState().getVents()[i].isIdentified());
            Assert.assertEquals(predicter.getDisplayState().getVents()[i].getDirection(), 0);
        }

    }

    public void resetTest() {
        VentStatusPredicter predicter = new VentStatusPredicter();
        predicter.reset();
        Assert.assertTrue(predicter.getDisplayState().hasDoneVMReset());
    }

    public void updateVentStatusTest() {
        int u = VentStatus.STARTING_VENT_VALUE;
        VentStatusPredicter predicter = new VentStatusPredicter();
        //All vents should match since a value was just identified
        int[] values = {u, 96, u};
        predicter.updateVentStatus(values, 7);
        for(int i = 0; i < StatusState.NUM_VENTS; ++i) {
            Assert.assertEquals(predicter.getDisplayState().getVents()[i].getActualValue(), values[i]);
            Assert.assertEquals(predicter.getDisplayState().getVents()[i].getDirection(), 1);
        }

        //Value has changed by one; previous state should not be updated
        predicter.updateVentStatus(new int[]{u, 97, u}, 7);
        Assert.assertEquals(predicter.getDisplayState().getVents()[1].getActualValue(), 97);

        //Value has changed by two; previous state match the previous value above
        predicter.updateVentStatus(new int[]{u, 99, u}, 7);
        Assert.assertEquals(predicter.getDisplayState().getVents()[1].getActualValue(), 99);

        //No change; same answer as above
        predicter.updateVentStatus(new int[]{u, 99, u}, 7);
        Assert.assertEquals(predicter.getDisplayState().getVents()[1].getActualValue(), 99);
    }

    public void makeStatusStateRangeTest() {
        VentStatusPredicter predicter = new VentStatusPredicter();
        //No identified vents so ranges should NOT be made
        predicter.makeStatusState(10);
        for(int i = 0; i < StatusState.NUM_VENTS; ++i) {
            Assert.assertFalse(predicter.getDisplayState().getVents()[i].isIdentified());
            Assert.assertFalse(predicter.getDisplayState().getVents()[i].isRangeDefined());
        }

        //Right amount of vents are known a range should be made!
        predicter.updateVentStatus(new int[]{VentStatus.STARTING_VENT_VALUE, 40, 50}, 7);
        predicter.makeStatusState(10);
        Assert.assertFalse(predicter.getDisplayState().getVents()[0].isIdentified());
        Assert.assertTrue(predicter.getDisplayState().getVents()[0].isRangeDefined());


        //All identified vents so ranges should NOT be made
        int[] values = new int[]{30, 40, 50};
        predicter.updateVentStatus(values, 7);
        predicter.makeStatusState(10);
        for(int i = 0; i < StatusState.NUM_VENTS; ++i) {
            Assert.assertTrue(predicter.getDisplayState().getVents()[i].isIdentified());
            Assert.assertEquals(predicter.getDisplayState().getVents()[i].getLowerBoundStart(), values[i]);
        }
    }

    public void updateVentMovementTest() {
        VentStatusPredicter predicter = new VentStatusPredicter();
        //Movement should be only updated for vents with predicted ranges
        predicter.updateVentStatus(new int[]{VentStatus.STARTING_VENT_VALUE, 0, 0}, 7);
        predicter.updateVentMovement();
        for(int i = 0; i < StatusState.NUM_VENTS; ++i) {
            Assert.assertEquals(predicter.getDisplayState().getVents()[i].getTotalDirectionalMovement(), 0);
        }

        //Movement should be only updated for vents with predicted ranges
        predicter = new VentStatusPredicter();
        predicter.updateVentStatus(new int[]{VentStatus.STARTING_VENT_VALUE, 80, 20}, 7);
        predicter.makeStatusState(5);
        predicter.updateVentMovement();
        int[] ans = new int[]{1, 0, 0};
        for(int i = 0; i < StatusState.NUM_VENTS; ++i) {
            Assert.assertEquals(predicter.getDisplayState().getVents()[i].getTotalDirectionalMovement(), ans[i]);
        }
    }

    public void clearMovementTest() {
        VentStatusPredicter predicter = new VentStatusPredicter();
        predicter.updateVentStatus(new int[]{VentStatus.STARTING_VENT_VALUE, 80, 20}, 7);
        predicter.makeStatusState(5);
        predicter.updateVentMovement();
        int[] ans = new int[]{1, 0, 0};
        for(int i = 0; i < StatusState.NUM_VENTS; ++i) {
            Assert.assertEquals(predicter.getDisplayState().getVents()[i].getTotalDirectionalMovement(), ans[i]);
        }

        predicter.clearVentMovement();
        for(int i = 0; i < StatusState.NUM_VENTS; ++i) {
            Assert.assertEquals(predicter.getDisplayState().getVents()[i].getTotalDirectionalMovement(), 0);
        }
    }

    public void getVentStatusTextTest() {
        //Undefined range vents return the default text
        VentStatusPredicter predicter = new VentStatusPredicter();
        Assert.assertEquals(predicter.getVentStatusText(0, ""), "");

        //Identified vents return the default text
        predicter.updateVentStatus(new int[]{0, 50, 50}, 7);
        Assert.assertEquals(predicter.getVentStatusText(0, ""), "");

        //Only a single value should be displayed
        predicter.updateVentStatus(new int[]{VentStatus.STARTING_VENT_VALUE, 51, 51}, 7);
        predicter.makeStatusState(25);
        String result = predicter.getVentStatusText(0, "A: ");
        result = Text.removeTags(result);
        Assert.assertEquals(result, "A: 50%");

        //Only a single range should be displayed
        predicter.initialize();
        predicter.updateVentStatus(new int[]{VentStatus.STARTING_VENT_VALUE, 50, 51}, 7);
        predicter.makeStatusState(25);
        result = Text.removeTags(predicter.getVentStatusText(0, "A: "));
        Assert.assertEquals(result, "A: 49-51%");

        //Two values should be displayed
        predicter.initialize();
        predicter.reset();
        predicter.updateVentStatus(new int[]{VentStatus.STARTING_VENT_VALUE, 50, 50}, 7);
        predicter.makeStatusState(8);
        predicter.getDisplayState().getVents()[0].setLowerBoundRange(0, 0);
        result = Text.removeTags(predicter.getVentStatusText(0, "A: "));
        Assert.assertEquals(result, "A: 0% 100%");

        //Two ranges should be displayed
        predicter.initialize();
        predicter.updateVentStatus(new int[]{VentStatus.STARTING_VENT_VALUE, 50, 50}, 7);
        predicter.makeStatusState(24);
        result = Text.removeTags(predicter.getVentStatusText(0, "A: "));
        Assert.assertEquals(result, "A: 45-47 53-55");
    }

}
