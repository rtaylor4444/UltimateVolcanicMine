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
        int[] values = {VentStatus.STARTING_VENT_VALUE, 0, 50};
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
