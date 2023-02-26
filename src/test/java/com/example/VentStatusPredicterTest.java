package com.example;

import org.testng.annotations.Test;
import org.testng.Assert;

@Test()
public class VentStatusPredicterTest {

    public void constructorTest() {
        VentStatusPredicter predicter = new VentStatusPredicter();
        final VentStatus[] vents = predicter.getCurrentVents();
        for(int i = 0; i < StatusState.NUM_VENTS; ++i)
            Assert.assertNotNull(vents[i]);
        Assert.assertNull(predicter.getCurrentState());
        Assert.assertNull(predicter.getPreviousState());
    }

    public void updateVentStatusTest() {
        VentStatusPredicter predicter = new VentStatusPredicter();
        int[] values = {30, 40, 50};
        predicter.updateVentStatus(values, 7);
        final VentStatus[] vents = predicter.getCurrentVents();
        for(int i = 0; i < StatusState.NUM_VENTS; ++i) {
            Assert.assertEquals(vents[i].getActualValue(), values[i]);
            Assert.assertEquals(vents[i].getDirection(), 1);
        }
        Assert.assertTrue(predicter.areAnyVentIdentified());
    }

    public void makeStatusStateCreatedTest() {
        VentStatusPredicter predicter = new VentStatusPredicter();
        predicter.makeStatusState(null, 10);
        Assert.assertNotNull(predicter.getCurrentState());
        Assert.assertNull(predicter.getPreviousState());

        predicter.makeStatusState(null, 10);
        Assert.assertNotNull(predicter.getCurrentState());
        Assert.assertNotNull(predicter.getPreviousState());
    }

    public void initializeTest() {
        VentStatusPredicter predicter = new VentStatusPredicter();
        predicter.updateVentStatus(new int[]{30, 40, 50}, 7);
        predicter.makeStatusState(null, 10);
        predicter.makeStatusState(null, 10);
        predicter.initialize();
        final VentStatus[] vents = predicter.getCurrentVents();
        for(int i = 0; i < StatusState.NUM_VENTS; ++i) {
            Assert.assertFalse(vents[i].isIdentified());
            Assert.assertEquals(vents[i].getDirection(), 0);
        }
        Assert.assertNull(predicter.getCurrentState());
        Assert.assertNull(predicter.getPreviousState());
    }

    public void resetTest() {
        VentStatusPredicter predicter = new VentStatusPredicter();
        predicter.updateVentStatus(new int[]{30, 40, 50}, 7);
        predicter.makeStatusState(null, 10);
        predicter.makeStatusState(null, 10);

        //Everything should be reset
        predicter.reset();
        final VentStatus[] vents = predicter.getCurrentVents();
        for(int i = 0; i < StatusState.NUM_VENTS; ++i) {
            Assert.assertFalse(vents[i].isIdentified());
            //Direction remains the same!
            Assert.assertEquals(vents[i].getDirection(), 1);
        }
        Assert.assertNull(predicter.getCurrentState());
        Assert.assertNull(predicter.getPreviousState());

        //Everything should not be reset
        predicter.reset();
        predicter.updateVentStatus(new int[]{30, 40, 50}, 7);
        predicter.makeStatusState(null, 10);
        predicter.makeStatusState(null, 10);
        for(int i = 0; i < StatusState.NUM_VENTS; ++i) {
            Assert.assertTrue(vents[i].isIdentified());
            Assert.assertEquals(vents[i].getDirection(), 1);
        }
        Assert.assertNotNull(predicter.getCurrentState());
        Assert.assertNotNull(predicter.getPreviousState());

        //Everything should be reset again since initialize was called
        predicter.initialize();
        predicter.updateVentStatus(new int[]{30, 40, 50}, 7);
        predicter.makeStatusState(null, 10);
        predicter.makeStatusState(null, 10);
        predicter.reset();
        for(int i = 0; i < StatusState.NUM_VENTS; ++i) {
            Assert.assertFalse(vents[i].isIdentified());
            //Direction remains the same!
            Assert.assertEquals(vents[i].getDirection(), 1);
        }
        Assert.assertNull(predicter.getCurrentState());
        Assert.assertNull(predicter.getPreviousState());
    }

    public void makeStatusStateRangeTest() {
        VentStatusPredicter predicter = new VentStatusPredicter();
        //No identified vents so ranges should NOT be made
        predicter.makeStatusState(null, 10);
        final VentStatus[] vents = predicter.getCurrentVents();
        for(int i = 0; i < StatusState.NUM_VENTS; ++i) {
            Assert.assertFalse(vents[i].isRangeDefined());
        }

        //All identified vents so ranges should NOT be made
        predicter.updateVentStatus(new int[]{30, 40, 50}, 7);
        predicter.makeStatusState(null, 10);
        final VentStatus[] vents2 = predicter.getCurrentVents();
        for(int i = 0; i < StatusState.NUM_VENTS; ++i) {
            Assert.assertTrue(vents2[i].isIdentified());
            Assert.assertFalse(vents2[i].isTwoSeperateValues());
        }

        //Right amount of vents are known a range should be made!
        predicter.updateVentStatus(new int[]{VentStatus.STARTING_VENT_VALUE, 40, 50}, 7);
        predicter.makeStatusState(null, 10);
        final VentStatus[] vents3 = predicter.getCurrentVents();
        Assert.assertFalse(vents3[0].isIdentified());
        Assert.assertTrue(vents3[0].isRangeDefined());
    }

    public void isFrozenATest() {
        VentStatusPredicter predicter = new VentStatusPredicter();
        int[] values = {0, 50, 50};
        //A never freezes
        for(int i = VentStatus.MIN_VENT_VALUE; i < VentStatus.MAX_VENT_VALUE; ++i) {
            values[0] = i;
            predicter.updateVentStatus(values, 7);
            Assert.assertFalse(predicter.isFrozen('A'));
        }
    }

    public void isFrozenBTest() {
        VentStatusPredicter predicter = new VentStatusPredicter();
        //B cannot be frozen since it is not in freeze range
        int[] values = {VentStatus.STARTING_VENT_VALUE, 0, 50};
        predicter.updateVentStatus(values, 7);
        Assert.assertFalse(predicter.isFrozen('B'));

        //We must assume B is frozen since it is in freeze range
        //and the value of A vent is unknown
        values = new int[]{VentStatus.STARTING_VENT_VALUE, 50, 50};
        predicter.updateVentStatus(values, 7);
        Assert.assertTrue(predicter.isFrozen('B'));

        //B cannot be frozen if A is out of range
        values = new int[]{0, 50, 50};
        predicter.updateVentStatus(values, 7);
        Assert.assertFalse(predicter.isFrozen('B'));

        //If B is undefined and A is in range then we must assume its frozen
        values = new int[]{50, VentStatus.STARTING_VENT_VALUE, 50};
        predicter.updateVentStatus(values, 7);
        Assert.assertTrue(predicter.isFrozen('B'));

        //If B is out of range and A is in range then B is not frozen
        values = new int[]{50, 0, 50};
        predicter.updateVentStatus(values, 7);
        Assert.assertFalse(predicter.isFrozen('B'));

        //If B is in range and A is in range then B is frozen
        values = new int[]{50, 50, 50};
        predicter.updateVentStatus(values, 7);
        Assert.assertTrue(predicter.isFrozen('B'));
    }

    public void updateVentMovementNoFrozenTest() {
        VentStatusPredicter predicter = new VentStatusPredicter();
        int[] values = {40, 40, 40};
        predicter.updateVentStatus(new int[]{
                VentStatus.STARTING_VENT_VALUE,
                VentStatus.STARTING_VENT_VALUE,
                VentStatus.STARTING_VENT_VALUE}, 7);
        Assert.assertFalse(predicter.updateVentMovement());
    }




}
