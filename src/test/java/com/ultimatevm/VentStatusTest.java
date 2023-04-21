package com.ultimatevm;

import org.testng.annotations.Test;
import org.testng.Assert;

@Test()
public class VentStatusTest {

    @Test()
    public void constructorTest() {
        VentStatus vent = new VentStatus('A');
        Assert.assertEquals(vent.getName(), 'A');
        Assert.assertEquals(vent.getDirection(), 0);
        Assert.assertEquals(vent.getActualValue(), VentStatus.STARTING_VENT_VALUE);
        Assert.assertEquals(vent.getLowerBoundStart(), VentStatus.STARTING_VENT_VALUE);
        Assert.assertEquals(vent.getLowerBoundEnd(), VentStatus.STARTING_VENT_VALUE);
        Assert.assertEquals(vent.getUpperBoundStart(), VentStatus.STARTING_VENT_VALUE);
        Assert.assertEquals(vent.getUpperBoundEnd(), VentStatus.STARTING_VENT_VALUE);
        //Vent values vary between 25-75% at the start
        Assert.assertEquals(vent.getTotalBoundStart(), VentStatus.MIN_VENT_START_VALUE);
        Assert.assertEquals(vent.getTotalBoundEnd(), VentStatus.MAX_VENT_START_VALUE);
    }

    public void copyConstructorTest() {
        VentStatus vent = new VentStatus('A');
        vent.update(VentStatus.STARTING_VENT_VALUE, 1);
        vent.setLowerBoundRange(30, 40);
        vent.setUpperBoundRange(60, 70);
        vent.updateMovement(0);

        VentStatus newVent = new VentStatus(vent);
        Assert.assertEquals(newVent.getName(), 'A');
        Assert.assertEquals(newVent.getDirection(), 1);
        Assert.assertEquals(newVent.getActualValue(), VentStatus.STARTING_VENT_VALUE);
        Assert.assertEquals(newVent.getLowerBoundStart(), 30+VentStatus.BASE_MOVE_RATE);
        Assert.assertEquals(newVent.getLowerBoundEnd(), 40+VentStatus.BASE_MOVE_RATE);
        Assert.assertEquals(newVent.getUpperBoundStart(), 60+VentStatus.BASE_MOVE_RATE);
        Assert.assertEquals(newVent.getUpperBoundEnd(), 70+VentStatus.BASE_MOVE_RATE);
        //Vent bounds should be updated
        Assert.assertEquals(newVent.getTotalBoundStart(), VentStatus.MIN_VENT_START_VALUE+VentStatus.BASE_MOVE_RATE);
        Assert.assertEquals(newVent.getTotalBoundEnd(), VentStatus.MAX_VENT_START_VALUE+VentStatus.BASE_MOVE_RATE);
    }

    public void setEqualToTest() {
        VentStatus vent = new VentStatus('A');
        vent.update(VentStatus.STARTING_VENT_VALUE, 1);
        vent.setLowerBoundRange(30, 40);
        vent.setUpperBoundRange(60, 70);
        vent.updateMovement(0);

        VentStatus newVent = new VentStatus('A');
        newVent.setEqualTo(vent);
        Assert.assertEquals(newVent.getName(), 'A');
        Assert.assertEquals(newVent.getDirection(), 1);
        Assert.assertEquals(newVent.getActualValue(), VentStatus.STARTING_VENT_VALUE);
        Assert.assertEquals(newVent.getLowerBoundStart(), 30+VentStatus.BASE_MOVE_RATE);
        Assert.assertEquals(newVent.getLowerBoundEnd(), 40+VentStatus.BASE_MOVE_RATE);
        Assert.assertEquals(newVent.getUpperBoundStart(), 60+VentStatus.BASE_MOVE_RATE);
        Assert.assertEquals(newVent.getUpperBoundEnd(), 70+VentStatus.BASE_MOVE_RATE);
        //Vent bounds should be updated
        Assert.assertEquals(newVent.getTotalBoundStart(), VentStatus.MIN_VENT_START_VALUE+VentStatus.BASE_MOVE_RATE);
        Assert.assertEquals(newVent.getTotalBoundEnd(), VentStatus.MAX_VENT_START_VALUE+VentStatus.BASE_MOVE_RATE);
    }

    public void doVMResetTest() {
        VentStatus vent = new VentStatus('A');
        vent.update(VentStatus.PERFECT_VENT_VALUE, 1);
        vent.updateMovement(0);

        vent.doVMReset();
        Assert.assertEquals(vent.getName(), 'A');
        Assert.assertEquals(vent.getDirection(), 1);
        Assert.assertEquals(vent.getActualValue(), VentStatus.STARTING_VENT_VALUE);
        Assert.assertEquals(vent.getLowerBoundStart(), VentStatus.STARTING_VENT_VALUE);
        Assert.assertEquals(vent.getLowerBoundEnd(), VentStatus.STARTING_VENT_VALUE);
        Assert.assertEquals(vent.getUpperBoundStart(), VentStatus.STARTING_VENT_VALUE);
        Assert.assertEquals(vent.getUpperBoundEnd(), VentStatus.STARTING_VENT_VALUE);
        //Vent values vary between 0-100% after the reset
        Assert.assertEquals(vent.getTotalBoundStart(), VentStatus.MIN_VENT_VALUE);
        Assert.assertEquals(vent.getTotalBoundEnd(), VentStatus.MAX_VENT_VALUE);
    }

    public void isIdentifiedTest() {
        VentStatus vent = new VentStatus('A');
        Assert.assertFalse(vent.isIdentified());
        vent.update(20, 1);
        Assert.assertTrue(vent.isIdentified());
    }

    public void setLowerBoundRangeTest() {
        VentStatus vent = new VentStatus('A');
        vent.setLowerBoundRange(49,51);
        Assert.assertEquals(vent.getLowerBoundStart(), 49);
        Assert.assertEquals(vent.getLowerBoundEnd(), 51);
    }

    public void setLowerBoundRangeCapTest() {
        VentStatus vent = new VentStatus('A');
        vent.setLowerBoundRange(VentStatus.MIN_VENT_VALUE-1, VentStatus.MAX_VENT_VALUE+1);
        Assert.assertEquals(vent.getLowerBoundStart(), VentStatus.MIN_VENT_VALUE);
        Assert.assertEquals(vent.getLowerBoundEnd(), VentStatus.MAX_VENT_VALUE);
    }

    public void setUpperBoundRangeTest() {
        VentStatus vent = new VentStatus('A');
        vent.setUpperBoundRange(49,51);
        Assert.assertEquals(vent.getUpperBoundStart(), 49);
        Assert.assertEquals(vent.getUpperBoundEnd(), 51);
    }

    public void setUpperBoundRangeCapTest() {
        VentStatus vent = new VentStatus('A');
        vent.setUpperBoundRange(VentStatus.MIN_VENT_VALUE-1, VentStatus.MAX_VENT_VALUE+1);
        Assert.assertEquals(vent.getUpperBoundStart(), VentStatus.MIN_VENT_VALUE);
        Assert.assertEquals(vent.getUpperBoundEnd(), VentStatus.MAX_VENT_VALUE);
    }

    public void isLowerBoundWithinRange() {
        VentStatus vent = new VentStatus('A');
        vent.setLowerBoundRange(35,50);
        //Actually out of range
        Assert.assertFalse(vent.isLowerBoundWithinRange(10, 34));
        Assert.assertFalse(vent.isLowerBoundWithinRange(51, 76));
        //Partially in range
        Assert.assertTrue(vent.isLowerBoundWithinRange(10, 35));
        Assert.assertTrue(vent.isLowerBoundWithinRange(50, 76));
        //Within the range
        Assert.assertTrue(vent.isLowerBoundWithinRange(40, 45));
        Assert.assertTrue(vent.isLowerBoundWithinRange(20, 55));
    }

    public void isUpperBoundWithinRange() {
        VentStatus vent = new VentStatus('A');
        vent.setUpperBoundRange(35,50);
        //Actually out of range
        Assert.assertFalse(vent.isUpperBoundWithinRange(10, 34));
        Assert.assertFalse(vent.isUpperBoundWithinRange(51, 76));
        //Partially in range
        Assert.assertTrue(vent.isUpperBoundWithinRange(10, 35));
        Assert.assertTrue(vent.isUpperBoundWithinRange(50, 76));
        //Within the range
        Assert.assertTrue(vent.isUpperBoundWithinRange(40, 45));
        Assert.assertTrue(vent.isUpperBoundWithinRange(20, 55));
    }

    public void setLowerBoundRangeMergeTest() {
        VentStatus vent = new VentStatus('A');
        vent.setUpperBoundRange(50,65);
        vent.setLowerBoundRange(35,50);
        Assert.assertEquals(vent.getLowerBoundStart(), 35);
        Assert.assertEquals(vent.getLowerBoundEnd(), 65);
        Assert.assertEquals(vent.getUpperBoundStart(), 35);
        Assert.assertEquals(vent.getUpperBoundEnd(), 65);
    }

    public void setUpperBoundRangeMergeTest() {
        VentStatus vent = new VentStatus('A');
        vent.setLowerBoundRange(35,50);
        vent.setUpperBoundRange(50,65);
        Assert.assertEquals(vent.getLowerBoundStart(), 35);
        Assert.assertEquals(vent.getLowerBoundEnd(), 65);
        Assert.assertEquals(vent.getUpperBoundStart(), 35);
        Assert.assertEquals(vent.getUpperBoundEnd(), 65);
    }

    public void updateTest() {
        VentStatus vent = new VentStatus('A');

        //Predicted ranges should be unchanged when vent isnt identified
        vent.update(VentStatus.STARTING_VENT_VALUE, 1);
        vent.setLowerBoundRange(100, 100);
        vent.setUpperBoundRange(0, 0);
        Assert.assertFalse(vent.isIdentified());
        Assert.assertEquals(vent.getActualValue(), VentStatus.STARTING_VENT_VALUE);
        Assert.assertEquals(vent.getDirection(), 1);
        Assert.assertEquals(vent.getLowerBoundStart(), 100);
        Assert.assertEquals(vent.getLowerBoundEnd(), 100);
        Assert.assertEquals(vent.getUpperBoundStart(), 0);
        Assert.assertEquals(vent.getUpperBoundEnd(), 0);

        //Otherwise will change
        vent.update(50, 1);
        Assert.assertTrue(vent.isIdentified());
        Assert.assertEquals(vent.getActualValue(), 50);
        Assert.assertEquals(vent.getDirection(), 1);
        Assert.assertEquals(vent.getLowerBoundStart(), 50);
        Assert.assertEquals(vent.getLowerBoundEnd(), 50);
        Assert.assertEquals(vent.getUpperBoundStart(), 50);
        Assert.assertEquals(vent.getUpperBoundEnd(), 50);
    }

    public void updateDifferenceTest() {
        VentStatus vent = new VentStatus('A');
        //Unidentified Tests
        //No change state returned for the very first update
        Assert.assertEquals(vent.update(VentStatus.STARTING_VENT_VALUE, 1), 0);
        //Nothing changed from the previous update
        Assert.assertEquals(vent.update(VentStatus.STARTING_VENT_VALUE, 1), 0);
        //Direction was changed here
        Assert.assertEquals(vent.update(VentStatus.STARTING_VENT_VALUE, -1), VentStatus.VentChangeStateFlag.DIRECTION_CHANGE.bitFlag());

        //Identified tests
        //Vent was just identified
        Assert.assertEquals(vent.update(100, -1), VentStatus.VentChangeStateFlag.IDENTIFIED.bitFlag());
        vent.update(VentStatus.STARTING_VENT_VALUE, -1);
        //Vent was just identified and direction changed
        Assert.assertEquals(vent.update(100, 1),
                VentStatus.VentChangeStateFlag.IDENTIFIED.bitFlag() + VentStatus.VentChangeStateFlag.DIRECTION_CHANGE.bitFlag());

        //Bounded tests
        //Vent must be bounded so no change state
        Assert.assertEquals(vent.update(100, 1), 0);
        //Direction has changed and vent cannot be bounded
        Assert.assertEquals(vent.update(100, -1),
                VentStatus.VentChangeStateFlag.NO_CHANGE.bitFlag() + VentStatus.VentChangeStateFlag.DIRECTION_CHANGE.bitFlag());
        vent.update(0, -1);
        //Vent must be bounded so no change state
        Assert.assertEquals(vent.update(0, -1), 0);
        //Direction has changed and vent cannot be bounded
        Assert.assertEquals(vent.update(0, 1),
                VentStatus.VentChangeStateFlag.NO_CHANGE.bitFlag() + VentStatus.VentChangeStateFlag.DIRECTION_CHANGE.bitFlag());
        vent.update(0, 1);

        //Increase of 1
        Assert.assertEquals(vent.update(1, 1), VentStatus.VentChangeStateFlag.ONE_CHANGE.bitFlag());
        //Decrease of 1
        Assert.assertEquals(vent.update(0, 1), VentStatus.VentChangeStateFlag.ONE_CHANGE.bitFlag());
        //Increase of 2
        Assert.assertEquals(vent.update(2, 1), VentStatus.VentChangeStateFlag.TWO_CHANGE.bitFlag());
        //Decrease of 2
        Assert.assertEquals(vent.update(0, 1), VentStatus.VentChangeStateFlag.TWO_CHANGE.bitFlag());

        //No change
        vent.update(50, 1);
        Assert.assertEquals(vent.update(50, 1), VentStatus.VentChangeStateFlag.NO_CHANGE.bitFlag());
        //Vent became unidentified
        Assert.assertEquals(vent.update(VentStatus.STARTING_VENT_VALUE, 1), 0);
    }

    public void updateMovementInvalidTest() {
        VentStatus vent = new VentStatus('A');
        //Nothing should change if the vent is identified
        vent.update(VentStatus.PERFECT_VENT_VALUE, 1);
        vent.updateMovement(0);
        //Bounds
        Assert.assertEquals(vent.getTotalBoundStart(), VentStatus.MIN_VENT_START_VALUE);
        Assert.assertEquals(vent.getTotalBoundEnd(), VentStatus.MAX_VENT_START_VALUE);
        //Ranges
        Assert.assertEquals(vent.getLowerBoundStart(), VentStatus.PERFECT_VENT_VALUE);
        Assert.assertEquals(vent.getLowerBoundEnd(), VentStatus.PERFECT_VENT_VALUE);
        Assert.assertEquals(vent.getUpperBoundStart(), VentStatus.PERFECT_VENT_VALUE);
        Assert.assertEquals(vent.getUpperBoundEnd(), VentStatus.PERFECT_VENT_VALUE);

        //Only bounds should change if range is undefined
        vent.clearRanges();
        vent.update(VentStatus.STARTING_VENT_VALUE, 1);
        vent.updateMovement(0);
        //Bounds
        Assert.assertEquals(vent.getTotalBoundStart(), VentStatus.MIN_VENT_START_VALUE+VentStatus.BASE_MOVE_RATE);
        Assert.assertEquals(vent.getTotalBoundEnd(), VentStatus.MAX_VENT_START_VALUE+VentStatus.BASE_MOVE_RATE);
        //Ranges
        Assert.assertFalse(vent.isRangeDefined());
    }

    public void updateMovementBoundsTest() {
        VentStatus vent = new VentStatus('A');

        //Upward movement total bound end should remain the same
        vent.update(VentStatus.STARTING_VENT_VALUE, 1);
        vent.doVMReset();
        vent.setLowerBoundRange(VentStatus.PERFECT_VENT_VALUE, VentStatus.PERFECT_VENT_VALUE);
        vent.setUpperBoundRange(VentStatus.PERFECT_VENT_VALUE, VentStatus.PERFECT_VENT_VALUE);
        vent.updateMovement(0);
        Assert.assertEquals(vent.getTotalBoundEnd(), VentStatus.MAX_VENT_VALUE);
        Assert.assertEquals(vent.getTotalBoundStart(), VentStatus.MIN_VENT_VALUE+VentStatus.BASE_MOVE_RATE);

        //Downward movement total bound start should remain the same
        vent.update(VentStatus.STARTING_VENT_VALUE, -1);
        vent.doVMReset();
        vent.setLowerBoundRange(VentStatus.PERFECT_VENT_VALUE, VentStatus.PERFECT_VENT_VALUE);
        vent.setUpperBoundRange(VentStatus.PERFECT_VENT_VALUE, VentStatus.PERFECT_VENT_VALUE);
        vent.updateMovement(0);
        Assert.assertEquals(vent.getTotalBoundEnd(), VentStatus.MAX_VENT_VALUE-VentStatus.BASE_MOVE_RATE);
        Assert.assertEquals(vent.getTotalBoundStart(), VentStatus.MIN_VENT_VALUE);

        //Multiple movements test - bounds should actually narrow
        vent.doVMReset();
        vent.setLowerBoundRange(VentStatus.PERFECT_VENT_VALUE, VentStatus.PERFECT_VENT_VALUE);
        vent.setUpperBoundRange(VentStatus.PERFECT_VENT_VALUE, VentStatus.PERFECT_VENT_VALUE);
        //4 downward movements
        for(int i = 0; i < 4; ++i) vent.updateMovement(0);
        Assert.assertEquals(vent.getTotalBoundEnd(), VentStatus.MAX_VENT_VALUE-(VentStatus.BASE_MOVE_RATE*4));
        Assert.assertEquals(vent.getTotalBoundStart(), VentStatus.MIN_VENT_VALUE);
        vent.update(VentStatus.STARTING_VENT_VALUE, 1);
        //2 upward movements
        for(int i = 0; i < 2; ++i) vent.updateMovement(0);
        Assert.assertEquals(vent.getTotalBoundEnd(), VentStatus.MAX_VENT_VALUE-(VentStatus.BASE_MOVE_RATE*2));
        Assert.assertEquals(vent.getTotalBoundStart(), VentStatus.MIN_VENT_VALUE+(VentStatus.BASE_MOVE_RATE*2));

        //Same as above but with some influence
        int newBaseRate = VentStatus.BASE_MOVE_RATE-1;
        //Upward movement total bound end should remain the same
        vent.update(VentStatus.STARTING_VENT_VALUE, 1);
        vent.doVMReset();
        vent.setLowerBoundRange(VentStatus.PERFECT_VENT_VALUE, VentStatus.PERFECT_VENT_VALUE);
        vent.setUpperBoundRange(VentStatus.PERFECT_VENT_VALUE, VentStatus.PERFECT_VENT_VALUE);
        vent.updateMovement(-1);
        Assert.assertEquals(vent.getTotalBoundEnd(), VentStatus.MAX_VENT_VALUE);
        Assert.assertEquals(vent.getTotalBoundStart(), VentStatus.MIN_VENT_VALUE+newBaseRate);

        //Downward movement total bound start should remain the same
        vent.update(VentStatus.STARTING_VENT_VALUE, -1);
        vent.doVMReset();
        vent.setLowerBoundRange(VentStatus.PERFECT_VENT_VALUE, VentStatus.PERFECT_VENT_VALUE);
        vent.setUpperBoundRange(VentStatus.PERFECT_VENT_VALUE, VentStatus.PERFECT_VENT_VALUE);
        vent.updateMovement(-1);
        Assert.assertEquals(vent.getTotalBoundEnd(), VentStatus.MAX_VENT_VALUE-newBaseRate);
        Assert.assertEquals(vent.getTotalBoundStart(), VentStatus.MIN_VENT_VALUE);

        //Multiple movements test - bounds should actually narrow
        vent.doVMReset();
        vent.setLowerBoundRange(VentStatus.PERFECT_VENT_VALUE, VentStatus.PERFECT_VENT_VALUE);
        vent.setUpperBoundRange(VentStatus.PERFECT_VENT_VALUE, VentStatus.PERFECT_VENT_VALUE);
        //4 downward movements
        for(int i = 0; i < 4; ++i) vent.updateMovement(-1);
        Assert.assertEquals(vent.getTotalBoundEnd(), VentStatus.MAX_VENT_VALUE-(newBaseRate*4));
        Assert.assertEquals(vent.getTotalBoundStart(), VentStatus.MIN_VENT_VALUE);
        vent.update(VentStatus.STARTING_VENT_VALUE, 1);
        //2 upward movements
        for(int i = 0; i < 2; ++i) vent.updateMovement(-1);
        Assert.assertEquals(vent.getTotalBoundEnd(), VentStatus.MAX_VENT_VALUE-(newBaseRate*2));
        Assert.assertEquals(vent.getTotalBoundStart(), VentStatus.MIN_VENT_VALUE+(newBaseRate*2));
    }

    public void updateMovementRangeTest() {
        //Ranges should be updated and capped at 100%
        VentStatus vent = new VentStatus('A');
        vent.update(VentStatus.STARTING_VENT_VALUE, 1);
        vent.setLowerBoundRange(VentStatus.MIN_VENT_VALUE, 3);
        vent.setUpperBoundRange(97, VentStatus.MAX_VENT_VALUE);
        vent.updateMovement(-1);
        Assert.assertEquals(vent.getLowerBoundStart(), VentStatus.MIN_VENT_VALUE+1);
        Assert.assertEquals(vent.getLowerBoundEnd(), 4);
        Assert.assertEquals(vent.getUpperBoundStart(), 98);
        Assert.assertEquals(vent.getUpperBoundEnd(), VentStatus.MAX_VENT_VALUE);

        //Ranges should be updated and capped at 0%
        vent.update(VentStatus.STARTING_VENT_VALUE, -1);
        vent.setLowerBoundRange(VentStatus.MIN_VENT_VALUE, 3);
        vent.setUpperBoundRange(97, VentStatus.MAX_VENT_VALUE);
        vent.updateMovement(-1);
        Assert.assertEquals(vent.getLowerBoundStart(), VentStatus.MIN_VENT_VALUE);
        Assert.assertEquals(vent.getLowerBoundEnd(), 2);
        Assert.assertEquals(vent.getUpperBoundStart(), 96);
        Assert.assertEquals(vent.getUpperBoundEnd(), VentStatus.MAX_VENT_VALUE-1);

        //Ranges should merge and move properly
        vent.setLowerBoundRange(47, VentStatus.PERFECT_VENT_VALUE);
        vent.setUpperBoundRange(VentStatus.PERFECT_VENT_VALUE, 53);
        vent.updateMovement(0);
        Assert.assertEquals(vent.getLowerBoundStart(), 46);
        Assert.assertEquals(vent.getLowerBoundEnd(), 52);
        Assert.assertEquals(vent.getUpperBoundStart(), 46);
        Assert.assertEquals(vent.getUpperBoundEnd(), 52);

        //Ranges should merge and move properly even max ranges
        vent.setLowerBoundRange(VentStatus.MIN_VENT_VALUE, VentStatus.PERFECT_VENT_VALUE);
        vent.setUpperBoundRange(VentStatus.PERFECT_VENT_VALUE, VentStatus.MAX_VENT_VALUE);
        vent.updateMovement(-1);
        Assert.assertEquals(vent.getLowerBoundStart(), VentStatus.MIN_VENT_VALUE);
        Assert.assertEquals(vent.getLowerBoundEnd(), VentStatus.MAX_VENT_VALUE-1);
        Assert.assertEquals(vent.getUpperBoundStart(), VentStatus.MIN_VENT_VALUE);
        Assert.assertEquals(vent.getUpperBoundEnd(), VentStatus.MAX_VENT_VALUE-1);
    }

    public void updateMovementRangeFrozenTest() {
        //Ranges should not be updated if frozen
        VentStatus vent = new VentStatus('C');
        vent.update(VentStatus.STARTING_VENT_VALUE, 1);
        vent.setLowerBoundRange(VentStatus.MIN_VENT_VALUE, 3);
        vent.setUpperBoundRange(97, VentStatus.MAX_VENT_VALUE);
        vent.updateMovement(-2);
        Assert.assertEquals(vent.getLowerBoundStart(), VentStatus.MIN_VENT_VALUE);
        Assert.assertEquals(vent.getLowerBoundEnd(), 3);
        Assert.assertEquals(vent.getUpperBoundStart(), 97);
        Assert.assertEquals(vent.getUpperBoundEnd(), VentStatus.MAX_VENT_VALUE);

        //Since ranges are within 41-59% there is an additional influence
        //making this -2
        vent.setLowerBoundRange(41, 45);
        vent.setUpperBoundRange(55, 59);
        vent.updateMovement(-1);
        Assert.assertEquals(vent.getLowerBoundStart(), 41);
        Assert.assertEquals(vent.getLowerBoundEnd(), 45);
        Assert.assertEquals(vent.getUpperBoundStart(), 55);
        Assert.assertEquals(vent.getUpperBoundEnd(), 59);

        //Test for -3 influence it should still be 0 movement
        vent.setLowerBoundRange(41, 45);
        vent.setUpperBoundRange(55, 59);
        vent.updateMovement(-2);
        Assert.assertEquals(vent.getLowerBoundStart(), 41);
        Assert.assertEquals(vent.getLowerBoundEnd(), 45);
        Assert.assertEquals(vent.getUpperBoundStart(), 55);
        Assert.assertEquals(vent.getUpperBoundEnd(), 59);
    }

    public void updateMovementRangeDifferentMoveTest() {
        VentStatus vent = new VentStatus('C');
        //Lower bound start + upper bound end are out of freeze range
        vent.update(VentStatus.STARTING_VENT_VALUE, 1);
        vent.setLowerBoundRange(40, 45);
        vent.setUpperBoundRange(55, 60);
        vent.updateMovement(-1);
        Assert.assertEquals(vent.getLowerBoundStart(), 41);
        Assert.assertEquals(vent.getLowerBoundEnd(), 45);
        Assert.assertEquals(vent.getUpperBoundStart(), 55);
        Assert.assertEquals(vent.getUpperBoundEnd(), 61);

        //Upper bound start and end are out of freeze range
        vent.setLowerBoundRange(41, 45);
        vent.setUpperBoundRange(60, 62);
        vent.updateMovement(-1);
        Assert.assertEquals(vent.getLowerBoundStart(), 41);
        Assert.assertEquals(vent.getLowerBoundEnd(), 45);
        Assert.assertEquals(vent.getUpperBoundStart(), 61);
        Assert.assertEquals(vent.getUpperBoundEnd(), 63);

        //Lower bound start and end are out of freeze range
        vent.setLowerBoundRange(38, 40);
        vent.setUpperBoundRange(55, 59);
        vent.updateMovement(-1);
        Assert.assertEquals(vent.getLowerBoundStart(), 39);
        Assert.assertEquals(vent.getLowerBoundEnd(), 41);
        Assert.assertEquals(vent.getUpperBoundStart(), 55);
        Assert.assertEquals(vent.getUpperBoundEnd(), 59);
    }

    public void isRangeDefinedTest() {
        VentStatus vent = new VentStatus('A');
        Assert.assertFalse(vent.isRangeDefined());
        vent.setLowerBoundRange(40, 45);
        Assert.assertFalse(vent.isRangeDefined());
        vent.setUpperBoundRange(55, 60);
        Assert.assertTrue(vent.isRangeDefined());
    }

    public void clearRangesTest() {
        VentStatus vent = new VentStatus('A');
        vent.setLowerBoundRange(40, 45);
        vent.setUpperBoundRange(40, 45);
        Assert.assertTrue(vent.isRangeDefined());
        vent.clearRanges();
        Assert.assertFalse(vent.isRangeDefined());
    }

    public void isTwoSeperateValuesTest() {
        VentStatus vent = new VentStatus('A');
        Assert.assertFalse(vent.isTwoSeperateValues());
        vent.setLowerBoundRange(40, 45);
        Assert.assertTrue(vent.isTwoSeperateValues());
        vent.setUpperBoundRange(40, 45);
        Assert.assertFalse(vent.isTwoSeperateValues());
    }

    public void isLowerBoundSingleValueTest() {
        VentStatus vent = new VentStatus('A');
        Assert.assertTrue(vent.isLowerBoundSingleValue());
        vent.setLowerBoundRange(40, 45);
        Assert.assertFalse(vent.isLowerBoundSingleValue());
        vent.setLowerBoundRange(45, 45);
        Assert.assertTrue(vent.isLowerBoundSingleValue());
    }

    public void isUpperBoundSingleValueTest() {
        VentStatus vent = new VentStatus('A');
        Assert.assertTrue(vent.isUpperBoundSingleValue());
        vent.setUpperBoundRange(50, 55);
        Assert.assertFalse(vent.isUpperBoundSingleValue());
        vent.setUpperBoundRange(55, 55);
        Assert.assertTrue(vent.isUpperBoundSingleValue());
    }

    public void isWithinRangeLowerTest() {
        VentStatus vent = new VentStatus('A');
        vent.setLowerBoundRange(35,50);
        //Actually out of range
        Assert.assertFalse(vent.isWithinRange(10, 34));
        Assert.assertFalse(vent.isWithinRange(51, 76));
        //Partially in range
        Assert.assertTrue(vent.isWithinRange(10, 35));
        Assert.assertTrue(vent.isWithinRange(50, 76));
        //Within the range
        Assert.assertTrue(vent.isWithinRange(40, 45));
        Assert.assertTrue(vent.isWithinRange(20, 55));
    }

    public void isWithinRangeUpperTest() {
        VentStatus vent = new VentStatus('A');
        vent.setUpperBoundRange(35,50);
        //Actually out of range
        Assert.assertFalse(vent.isWithinRange(10, 34));
        Assert.assertFalse(vent.isWithinRange(51, 76));
        //Partially in range
        Assert.assertTrue(vent.isWithinRange(10, 35));
        Assert.assertTrue(vent.isWithinRange(50, 76));
        //Within the range
        Assert.assertTrue(vent.isWithinRange(40, 45));
        Assert.assertTrue(vent.isWithinRange(20, 55));
    }

    public void isWithinRangeTest() {
        VentStatus vent = new VentStatus('A');
        vent.setLowerBoundRange(35,45);
        vent.setUpperBoundRange(55,60);
        //Out of range
        Assert.assertFalse(vent.isWithinRange(10, 34));
        Assert.assertFalse(vent.isWithinRange(61, 90));
        //Inbetween both ranges
        Assert.assertFalse(vent.isWithinRange(46, 54));
        //Within one but not the other
        Assert.assertTrue(vent.isWithinRange(10, 35));
        Assert.assertTrue(vent.isWithinRange(59, 90));
        //Within both
        Assert.assertTrue(vent.isWithinRange(45, 55));
        Assert.assertTrue(vent.isWithinRange(VentStatus.MIN_VENT_VALUE - 1, VentStatus.MAX_VENT_VALUE + 1));
    }

    public void doBoundsClippingTest() {
        //Bounds starts at 25-75%

        //Both ranges outside of bounds
        VentStatus vent = new VentStatus('A');
        vent.setLowerBoundRange(15,24);
        vent.setUpperBoundRange(76,85);
        vent.doBoundsClipping();
        //Range should be set to bounds
        Assert.assertEquals(vent.getLowerBoundStart(), vent.getTotalBoundStart());
        Assert.assertEquals(vent.getUpperBoundStart(), vent.getTotalBoundStart());
        Assert.assertEquals(vent.getLowerBoundEnd(), vent.getTotalBoundEnd());
        Assert.assertEquals(vent.getUpperBoundEnd(), vent.getTotalBoundEnd());

        //Upper range outside of bounds should be set to lower range
        vent = new VentStatus('A');
        vent.setLowerBoundRange(15,25);
        vent.setUpperBoundRange(76,85);
        vent.doBoundsClipping();
        //Range should be set to bounds
        Assert.assertEquals(vent.getLowerBoundStart(), 25);
        Assert.assertEquals(vent.getUpperBoundStart(), 25);
        Assert.assertEquals(vent.getLowerBoundEnd(), 25);
        Assert.assertEquals(vent.getUpperBoundEnd(), 25);

        //lower range outside of bounds should be set to upper range
        vent = new VentStatus('A');
        vent.setLowerBoundRange(15,24);
        vent.setUpperBoundRange(75,85);
        vent.doBoundsClipping();
        //Range should be set to bounds
        Assert.assertEquals(vent.getLowerBoundStart(), 75);
        Assert.assertEquals(vent.getUpperBoundStart(), 75);
        Assert.assertEquals(vent.getLowerBoundEnd(), 75);
        Assert.assertEquals(vent.getUpperBoundEnd(), 75);
    }
}
