package com.example;

import net.runelite.client.util.Text;
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
        predicter.initialize();
        predicter.updateVentStatus(values, 7);
        Assert.assertFalse(predicter.isFrozen('B'));

        //We must assume B is frozen since it is in freeze range
        //and the value of A vent is unknown
        values = new int[]{VentStatus.STARTING_VENT_VALUE, 50, 50};
        predicter.initialize();
        predicter.updateVentStatus(values, 7);
        Assert.assertTrue(predicter.isFrozen('B'));

        //B cannot be frozen if A is out of range
        values = new int[]{0, 50, 50};
        predicter.initialize();
        predicter.updateVentStatus(values, 7);
        Assert.assertFalse(predicter.isFrozen('B'));

        //If B is undefined and A is in range then we must assume its frozen
        values = new int[]{50, VentStatus.STARTING_VENT_VALUE, 50};
        predicter.initialize();
        predicter.updateVentStatus(values, 7);
        Assert.assertTrue(predicter.isFrozen('B'));

        //If B is out of range and A is in range then B is not frozen
        values = new int[]{50, 0, 50};
        predicter.initialize();
        predicter.updateVentStatus(values, 7);
        Assert.assertFalse(predicter.isFrozen('B'));

        //If B is in range and A is in range then B is frozen
        values = new int[]{50, 50, 50};
        predicter.initialize();
        predicter.updateVentStatus(values, 7);
        Assert.assertTrue(predicter.isFrozen('B'));
    }

    public void isFrozenCTest() {
        VentStatusPredicter predicter = new VentStatusPredicter();
        //Since A and B are undefined we must assume they can be in range
        //which means C freezes no matter what
        int[] values = {VentStatus.STARTING_VENT_VALUE, VentStatus.STARTING_VENT_VALUE, 0};
        predicter.initialize();
        predicter.updateVentStatus(values, 7);
        Assert.assertTrue(predicter.isFrozen('C'));

        //C cannot be frozen since it is not in freeze range,
        //B is not within range and A is undefined
        values = new int[]{VentStatus.STARTING_VENT_VALUE, 0, 0};
        predicter.initialize();
        predicter.updateVentStatus(values, 7);
        Assert.assertFalse(predicter.isFrozen('C'));

        //C cannot be frozen since it is not in freeze range,
        //A is not within range and B is undefined
        values = new int[]{0, VentStatus.STARTING_VENT_VALUE, 0};
        predicter.initialize();
        predicter.updateVentStatus(values, 7);
        Assert.assertFalse(predicter.isFrozen('C'));

        //C can be frozen no matter what,
        //A is within range and B is undefined
        values = new int[]{50, VentStatus.STARTING_VENT_VALUE, 0};
        predicter.initialize();
        predicter.updateVentStatus(values, 7);
        Assert.assertTrue(predicter.isFrozen('C'));

        //C can be frozen no matter what,
        //B is within range and A is undefined
        values = new int[]{VentStatus.STARTING_VENT_VALUE, 50, 0};
        predicter.initialize();
        predicter.updateVentStatus(values, 7);
        Assert.assertTrue(predicter.isFrozen('C'));

        //C can be frozen no matter what,
        //A and B are within range
        values = new int[]{50, 50, 0};
        predicter.initialize();
        predicter.updateVentStatus(values, 7);
        Assert.assertTrue(predicter.isFrozen('C'));

        //C cannot be frozen it isnt in range, B is not within range and A is undefined
        values = new int[]{VentStatus.STARTING_VENT_VALUE, 0, 0};
        predicter.initialize();
        predicter.updateVentStatus(values, 7);
        Assert.assertFalse(predicter.isFrozen('C'));

        //C can be frozen it is in range, B is not within range and A is undefined
        values = new int[]{VentStatus.STARTING_VENT_VALUE, 0, 50};
        predicter.initialize();
        predicter.updateVentStatus(values, 7);
        Assert.assertTrue(predicter.isFrozen('C'));

        //C cannot be frozen it isnt in range even though B is in range
        values = new int[]{0, 50, 0};
        predicter.initialize();
        predicter.updateVentStatus(values, 7);
        Assert.assertFalse(predicter.isFrozen('C'));

        //C cannot be frozen it isnt in range even though B is undefined
        values = new int[]{0, VentStatus.STARTING_VENT_VALUE, 0};
        predicter.initialize();
        predicter.updateVentStatus(values, 7);
        Assert.assertFalse(predicter.isFrozen('C'));

        //C is frozen since both C and B are in range
        values = new int[]{0, 50, 50};
        predicter.initialize();
        predicter.updateVentStatus(values, 7);
        Assert.assertTrue(predicter.isFrozen('C'));

        //C is frozen since its in range and B is undefined
        values = new int[]{0, VentStatus.STARTING_VENT_VALUE, 50};
        predicter.initialize();
        predicter.updateVentStatus(values, 7);
        Assert.assertTrue(predicter.isFrozen('C'));

        //C cannot be frozen none of the vents are in range
        values = new int[]{0, 0, 50};
        predicter.initialize();
        predicter.updateVentStatus(values, 7);
        Assert.assertFalse(predicter.isFrozen('C'));

        //A and C are undefined and B is not within range so C ban be frozen
        values = new int[]{VentStatus.STARTING_VENT_VALUE, 0, VentStatus.STARTING_VENT_VALUE};
        predicter.initialize();
        predicter.updateVentStatus(values, 7);
        Assert.assertTrue(predicter.isFrozen('C'));

        //B and C are undefined so C ban be frozen
        values = new int[]{0, VentStatus.STARTING_VENT_VALUE, VentStatus.STARTING_VENT_VALUE};
        predicter.initialize();
        predicter.updateVentStatus(values, 7);
        Assert.assertTrue(predicter.isFrozen('C'));
    }

    public void isFrozenRangeTest() {
        //Verify that ranges work for frozen tests
        VentStatusPredicter predicter = new VentStatusPredicter();
        int[] values = {VentStatus.STARTING_VENT_VALUE, 50, 0};
        predicter.updateVentStatus(values, 7);
        predicter.makeStatusState(null, 9);
        Assert.assertTrue(predicter.isFrozen('B'));

        predicter = new VentStatusPredicter();
        values = new int[]{50, VentStatus.STARTING_VENT_VALUE, 0};
        predicter.updateVentStatus(values, 7);
        predicter.makeStatusState(null, 9);
        Assert.assertTrue(predicter.isFrozen('B'));

        predicter = new VentStatusPredicter();
        values = new int[]{0, 50, VentStatus.STARTING_VENT_VALUE}; //50
        predicter.updateVentStatus(values, 7);
        predicter.makeStatusState(null, 9);
        Assert.assertTrue(predicter.isFrozen('C'));
    }

    public void updateVentMovementTest() {
        VentStatusPredicter predicter = new VentStatusPredicter();
        //Movement should not be updated for vents with no predicted ranges
        Assert.assertFalse(predicter.updateVentMovement());
        predicter.updateVentStatus(new int[]{VentStatus.STARTING_VENT_VALUE, 0, 0}, 7);
        Assert.assertTrue(predicter.updateVentMovement());
        final VentStatus[] vents = predicter.getCurrentVents();
        Assert.assertEquals(vents[0].getMovementSinceLastState(), 0);
        Assert.assertEquals(vents[1].getMovementSinceLastState(), 1);
        Assert.assertEquals(vents[2].getMovementSinceLastState(), 1);

        //Movement should not be updated for frozen vents
        predicter = new VentStatusPredicter();
        predicter.updateVentStatus(new int[]{50, 50, 50}, 7);
        Assert.assertTrue(predicter.updateVentMovement());
        final VentStatus[] vents2 = predicter.getCurrentVents();
        Assert.assertEquals(vents2[0].getMovementSinceLastState(), 1);
        Assert.assertEquals(vents2[1].getMovementSinceLastState(), 0);
        Assert.assertEquals(vents2[2].getMovementSinceLastState(), 0);

        //Movement should be updated for vents with predicted ranges
        predicter = new VentStatusPredicter();
        predicter.updateVentStatus(new int[]{VentStatus.STARTING_VENT_VALUE, 80, 20}, 7);
        predicter.makeStatusState(null, 5);
        Assert.assertTrue(predicter.updateVentMovement());
        final VentStatus[] vents3 = predicter.getCurrentVents();
        Assert.assertEquals(vents3[0].getMovementSinceLastState(), 1);
        Assert.assertEquals(vents3[1].getMovementSinceLastState(), 1);
        Assert.assertEquals(vents3[2].getMovementSinceLastState(), 1);
    }

    public void clearMovementTest() {
        VentStatusPredicter predicter = new VentStatusPredicter();
        predicter.updateVentStatus(new int[]{0, 0, 0}, 7);
        Assert.assertTrue(predicter.updateVentMovement());
        final VentStatus[] vents = predicter.getCurrentVents();
        Assert.assertEquals(vents[0].getMovementSinceLastState(), 1);
        Assert.assertEquals(vents[1].getMovementSinceLastState(), 1);
        Assert.assertEquals(vents[2].getMovementSinceLastState(), 1);

        predicter.clearVentMovement();
        final VentStatus[] vents2 = predicter.getCurrentVents();
        Assert.assertEquals(vents2[0].getMovementSinceLastState(), 0);
        Assert.assertEquals(vents2[1].getMovementSinceLastState(), 0);
        Assert.assertEquals(vents2[2].getMovementSinceLastState(), 0);
    }

    public void calcSingleVentValueTest() {
        //Normal calc test
        VentStatusPredicter predicter = new VentStatusPredicter();
        predicter.updateVentStatus(new int[]{VentStatus.STARTING_VENT_VALUE, 51, 51}, 7);
        predicter.makeStatusState(null, 25);
        final VentStatus[] vents = predicter.getCurrentVents();
        Assert.assertTrue(vents[0].isRangeDefined());
        Assert.assertEquals(vents[0].getLowerBoundStart(), 50);
        Assert.assertEquals(vents[0].getLowerBoundEnd(), 50);
        Assert.assertEquals(vents[0].getUpperBoundStart(), 50);
        Assert.assertEquals(vents[0].getUpperBoundEnd(), 50);

        //Truncation possibilities test
        predicter = new VentStatusPredicter();
        predicter.updateVentStatus(new int[]{VentStatus.STARTING_VENT_VALUE, 50, 50}, 7);
        predicter.makeStatusState(null, 24);
        final VentStatus[] vents2 = predicter.getCurrentVents();
        Assert.assertTrue(vents2[0].isRangeDefined());
        Assert.assertEquals(vents2[0].getLowerBoundStart(), 45);
        Assert.assertEquals(vents2[0].getLowerBoundEnd(), 47);
        Assert.assertEquals(vents2[0].getUpperBoundStart(), 53);
        Assert.assertEquals(vents2[0].getUpperBoundEnd(), 55);
    }

    public void fixRangesInvalidTest() {
        //Invalid previous state; ranges should still be made
        VentStatusPredicter predicter = new VentStatusPredicter();
        predicter.updateVentStatus(new int[]{VentStatus.STARTING_VENT_VALUE,
                VentStatus.STARTING_VENT_VALUE, VentStatus.STARTING_VENT_VALUE}, 7);
        predicter.makeStatusState(null, 25);
        predicter.updateVentStatus(new int[]{50, 50, VentStatus.STARTING_VENT_VALUE}, 7);
        predicter.makeStatusState(null, 25);
        final VentStatus[] vents = predicter.getCurrentVents();
        Assert.assertTrue(vents[2].isRangeDefined());

        //If frozen ranges should remain unchanged
        predicter = new VentStatusPredicter();
        predicter.updateVentStatus(new int[]{50, 50, VentStatus.STARTING_VENT_VALUE}, 7);
        predicter.makeStatusState(null, 25);
        final VentStatus[] vents2 = predicter.getCurrentVents();
        int lowerBoundStart = vents[2].getLowerBoundStart();
        int lowerBoundEnd = vents[2].getLowerBoundEnd();
        int upperBoundStart = vents[2].getUpperBoundStart();
        int upperBoundEnd = vents[2].getUpperBoundEnd();
        predicter.makeStatusState(null, 25);
        Assert.assertEquals(vents2[2].getLowerBoundStart(), lowerBoundStart);
        Assert.assertEquals(vents2[2].getLowerBoundEnd(), lowerBoundEnd);
        Assert.assertEquals(vents2[2].getUpperBoundStart(), upperBoundStart);
        Assert.assertEquals(vents2[2].getUpperBoundEnd(), upperBoundEnd);

    }

    public void fixRangesMovementTest() {
        //Negative change from 50% with upward movement; upper bound range should be picked
        VentStatusPredicter predicter = new VentStatusPredicter();
        predicter.updateVentStatus(new int[]{VentStatus.STARTING_VENT_VALUE, 50, 50}, 7);
        predicter.makeStatusState(null, 25);
        predicter.updateVentMovement();
        predicter.makeStatusState(null, 24);
        final VentStatus[] vents = predicter.getCurrentVents();
        final StatusState currentState = predicter.getCurrentState();
        Assert.assertEquals(vents[0].getLowerBoundStart(), currentState.getVent(0).getUpperBoundStart());
        Assert.assertEquals(vents[0].getLowerBoundEnd(), currentState.getVent(0).getUpperBoundEnd());
        Assert.assertEquals(vents[0].getUpperBoundStart(), currentState.getVent(0).getUpperBoundStart());
        Assert.assertEquals(vents[0].getUpperBoundEnd(), currentState.getVent(0).getUpperBoundEnd());

        //Negative change from 50% with downward movement; lower bound range should be picked
        predicter = new VentStatusPredicter();
        predicter.updateVentStatus(new int[]{VentStatus.STARTING_VENT_VALUE, 50, 50}, 0);
        predicter.makeStatusState(null, 25);
        predicter.updateVentMovement();
        predicter.makeStatusState(null, 24);
        final VentStatus[] vents2 = predicter.getCurrentVents();
        final StatusState currentState2 = predicter.getCurrentState();
        Assert.assertEquals(vents2[0].getLowerBoundStart(), currentState2.getVent(0).getLowerBoundStart());
        Assert.assertEquals(vents2[0].getLowerBoundEnd(), currentState2.getVent(0).getLowerBoundEnd());
        Assert.assertEquals(vents2[0].getUpperBoundStart(), currentState2.getVent(0).getLowerBoundStart());
        Assert.assertEquals(vents2[0].getUpperBoundEnd(), currentState2.getVent(0).getLowerBoundEnd());

        //Positive change from 26% with upward movement; lower bound range should be picked
        predicter = new VentStatusPredicter();
        predicter.updateVentStatus(new int[]{VentStatus.STARTING_VENT_VALUE, 50, 50}, 7);
        predicter.makeStatusState(null, 17);
        predicter.updateVentMovement();
        predicter.makeStatusState(null, 18);
        final VentStatus[] vents3 = predicter.getCurrentVents();
        final StatusState currentState3 = predicter.getCurrentState();
        Assert.assertEquals(vents3[0].getLowerBoundStart(), currentState3.getVent(0).getLowerBoundStart());
        Assert.assertEquals(vents3[0].getLowerBoundEnd(), currentState3.getVent(0).getLowerBoundEnd());
        Assert.assertEquals(vents3[0].getUpperBoundStart(), currentState3.getVent(0).getLowerBoundStart());
        Assert.assertEquals(vents3[0].getUpperBoundEnd(), currentState3.getVent(0).getLowerBoundEnd());

        //Positive change from 74% with downward movement; upper bound range should be picked
        predicter = new VentStatusPredicter();
        predicter.updateVentStatus(new int[]{VentStatus.STARTING_VENT_VALUE, 50, 50}, 0);
        predicter.makeStatusState(null, 17);
        predicter.updateVentMovement();
        predicter.makeStatusState(null, 18);
        final VentStatus[] vents4 = predicter.getCurrentVents();
        final StatusState currentState4 = predicter.getCurrentState();
        Assert.assertEquals(vents4[0].getLowerBoundStart(), currentState4.getVent(0).getUpperBoundStart());
        Assert.assertEquals(vents4[0].getLowerBoundEnd(), currentState4.getVent(0).getUpperBoundEnd());
        Assert.assertEquals(vents4[0].getUpperBoundStart(), currentState4.getVent(0).getUpperBoundStart());
        Assert.assertEquals(vents4[0].getUpperBoundEnd(), currentState4.getVent(0).getUpperBoundEnd());
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
        predicter.makeStatusState(null, 25);
        String result = predicter.getVentStatusText(0, "A: ");
        result = Text.removeTags(result);
        Assert.assertEquals(result, "A: 50%");

        //Only a single range should be displayed
        predicter.initialize();
        predicter.updateVentStatus(new int[]{VentStatus.STARTING_VENT_VALUE, 50, 51}, 7);
        predicter.makeStatusState(null, 25);
        result = Text.removeTags(predicter.getVentStatusText(0, "A: "));
        Assert.assertEquals(result, "A: 49-51%");

        //Two values should be displayed
        predicter.initialize();
        predicter.updateVentStatus(new int[]{VentStatus.STARTING_VENT_VALUE, 50, 50}, 7);
        predicter.makeStatusState(null, 8);
        result = Text.removeTags(predicter.getVentStatusText(0, "A: "));
        Assert.assertEquals(result, "A: 0% 100%");

        //Two ranges should be displayed
        predicter.initialize();
        predicter.updateVentStatus(new int[]{VentStatus.STARTING_VENT_VALUE, 50, 50}, 7);
        predicter.makeStatusState(null, 24);
        result = Text.removeTags(predicter.getVentStatusText(0, "A: "));
        Assert.assertEquals(result, "A: 45-47 53-55");
    }

    public void fixRangesInaccurateMovementTest() {
        //Inaccurate movement downward
        VentStatusPredicter predicter = new VentStatusPredicter();
        predicter.updateVentStatus(new int[]{VentStatus.STARTING_VENT_VALUE, 25, 5}, 6);
        predicter.makeStatusState(null, -2);
        for(int i = 0; i < 3; ++i) predicter.updateVentMovement();
        predicter.updateVentStatus(new int[]{VentStatus.STARTING_VENT_VALUE, 28, 4}, 6);
        predicter.makeStatusState(null, -2);
        //inaccurate movement here
        for(int i = 0; i < 2; ++i) predicter.updateVentMovement();
        predicter.updateVentStatus(new int[]{VentStatus.STARTING_VENT_VALUE, 32, 8}, 6);
        predicter.makeStatusState(null, 1);
        final VentStatus[] vents = predicter.getCurrentVents();
        Assert.assertEquals(vents[0].getLowerBoundStart(), 36);
        Assert.assertEquals(vents[0].getLowerBoundEnd(), 38);
        Assert.assertEquals(vents[0].getUpperBoundStart(), 36);
        Assert.assertEquals(vents[0].getUpperBoundEnd(), 38);

        //Inaccurate movement upward
        predicter = new VentStatusPredicter();
        predicter.updateVentStatus(new int[]{VentStatus.STARTING_VENT_VALUE, 25, 5}, 7);
        predicter.makeStatusState(null, -2);
        for(int i = 0; i < 3; ++i) predicter.updateVentMovement();
        predicter.updateVentStatus(new int[]{VentStatus.STARTING_VENT_VALUE, 28, 4}, 7);
        predicter.makeStatusState(null, -2);
        //inaccurate movement here
        for(int i = 0; i < 2; ++i) predicter.updateVentMovement();
        predicter.updateVentStatus(new int[]{VentStatus.STARTING_VENT_VALUE, 32, 8}, 7);
        predicter.makeStatusState(null, 1);
        final VentStatus[] vents2 = predicter.getCurrentVents();
        Assert.assertEquals(vents2[0].getLowerBoundStart(), 62);
        Assert.assertEquals(vents2[0].getLowerBoundEnd(), 64);
        Assert.assertEquals(vents2[0].getUpperBoundStart(), 62);
        Assert.assertEquals(vents2[0].getUpperBoundEnd(), 64);

        //Frozen but not really upward movement
        predicter = new VentStatusPredicter();
        predicter.updateVentStatus(new int[]{50, 25, VentStatus.STARTING_VENT_VALUE}, 7);
        predicter.makeStatusState(null, 1);
        predicter.makeStatusState(null, 2);
        final VentStatus[] vents3 = predicter.getCurrentVents();
        Assert.assertEquals(vents3[2].getLowerBoundStart(), 4);
        Assert.assertEquals(vents3[2].getLowerBoundEnd(), 6);
        Assert.assertEquals(vents3[2].getUpperBoundStart(), 4);
        Assert.assertEquals(vents3[2].getUpperBoundEnd(), 6);

        //Frozen but not really downward movement
        predicter = new VentStatusPredicter();
        predicter.updateVentStatus(new int[]{50, 25, VentStatus.STARTING_VENT_VALUE}, 3);
        predicter.makeStatusState(null, 1);
        predicter.makeStatusState(null, 2);
        final VentStatus[] vents4 = predicter.getCurrentVents();
        Assert.assertEquals(vents4[2].getLowerBoundStart(), 94);
        Assert.assertEquals(vents4[2].getLowerBoundEnd(), 96);
        Assert.assertEquals(vents4[2].getUpperBoundStart(), 94);
        Assert.assertEquals(vents4[2].getUpperBoundEnd(), 96);

        //Frozen but not really single range in the middle; upward move
        predicter = new VentStatusPredicter();
        predicter.updateVentStatus(new int[]{50, 50, VentStatus.STARTING_VENT_VALUE}, 7);
        predicter.makeStatusState(null, 25);
        final VentStatus[] vents5 = predicter.getCurrentVents();
        predicter.makeStatusState(null, 24);
        Assert.assertEquals(vents5[2].getLowerBoundStart(), 53);
        Assert.assertEquals(vents5[2].getLowerBoundEnd(), 55);
        Assert.assertEquals(vents5[2].getUpperBoundStart(), 53);
        Assert.assertEquals(vents5[2].getUpperBoundEnd(), 55);

        //Frozen but not really single range in the middle; upward move
        predicter = new VentStatusPredicter();
        predicter.updateVentStatus(new int[]{50, 50, VentStatus.STARTING_VENT_VALUE}, 3);
        predicter.makeStatusState(null, 25);
        final VentStatus[] vents6 = predicter.getCurrentVents();
        predicter.makeStatusState(null, 24);
        Assert.assertEquals(vents6[2].getLowerBoundStart(), 45);
        Assert.assertEquals(vents6[2].getLowerBoundEnd(), 47);
        Assert.assertEquals(vents6[2].getUpperBoundStart(), 45);
        Assert.assertEquals(vents6[2].getUpperBoundEnd(), 47);
    }
}
