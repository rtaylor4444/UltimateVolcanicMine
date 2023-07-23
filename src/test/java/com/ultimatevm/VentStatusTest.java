package com.ultimatevm;

import org.testng.annotations.Test;
import org.testng.Assert;

@Test()
public class VentStatusTest {
    int u = VentStatus.STARTING_VENT_VALUE;
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
        //Vent was unidentified (reset)
        Assert.assertEquals(vent.update(VentStatus.STARTING_VENT_VALUE, -1), VentStatus.VentChangeStateFlag.RESET.bitFlag());
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
        //Vent became unidentified and direction changed
        Assert.assertEquals(vent.update(VentStatus.STARTING_VENT_VALUE, -1),
                VentStatus.VentChangeStateFlag.RESET.bitFlag() | VentStatus.VentChangeStateFlag.DIRECTION_CHANGE.bitFlag());
    }

    public void updateMovementInvalidTest() {
        VentStatus vent = new VentStatus('A');
        //Nothing should change if the vent is identified
        vent.update(VentStatus.PERFECT_VENT_VALUE, 1);
        vent.updateMovement(0);
        //Ranges
        Assert.assertEquals(vent.getLowerBoundStart(), VentStatus.PERFECT_VENT_VALUE);
        Assert.assertEquals(vent.getLowerBoundEnd(), VentStatus.PERFECT_VENT_VALUE);
        Assert.assertEquals(vent.getUpperBoundStart(), VentStatus.PERFECT_VENT_VALUE);
        Assert.assertEquals(vent.getUpperBoundEnd(), VentStatus.PERFECT_VENT_VALUE);

        //Nothing should change if range is undefined
        vent.clearRanges();
        vent.update(VentStatus.STARTING_VENT_VALUE, 1);
        vent.updateMovement(0);
        //Ranges
        Assert.assertFalse(vent.isRangeDefined());
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

    public void getOverlappedLowerBoundRangeTest() {
        VentStatus vent = new VentStatus('A');
        vent.setLowerBoundRange(35,45);
        //Start is lower
        int[] ans = vent.getOverlappedLowerBoundRange(30, 45);
        Assert.assertEquals(ans[0], 35);
        Assert.assertEquals(ans[1], 45);
        //End is higher
        ans = vent.getOverlappedLowerBoundRange(35, 50);
        Assert.assertEquals(ans[0], 35);
        Assert.assertEquals(ans[1], 45);
        //Range is greater
        ans = vent.getOverlappedLowerBoundRange(30, 50);
        Assert.assertEquals(ans[0], 35);
        Assert.assertEquals(ans[1], 45);
        //Start is higher
        ans = vent.getOverlappedLowerBoundRange(40, 45);
        Assert.assertEquals(ans[0], 40);
        Assert.assertEquals(ans[1], 45);
        //End is lower
        ans = vent.getOverlappedLowerBoundRange(35, 40);
        Assert.assertEquals(ans[0], 35);
        Assert.assertEquals(ans[1], 40);
        //Both are within bounds
        ans = vent.getOverlappedLowerBoundRange(40, 40);
        Assert.assertEquals(ans[0], 40);
        Assert.assertEquals(ans[1], 40);
        //Out of bounds
        ans = vent.getOverlappedLowerBoundRange(25, 30);
        Assert.assertEquals(ans[0], -1);
        Assert.assertEquals(ans[1], -1);
    }

    public void getOverlappedUpperBoundRangeTest() {
        VentStatus vent = new VentStatus('A');
        vent.setUpperBoundRange(35,45);
        //Start is lower
        int[] ans = vent.getOverlappedUpperBoundRange(30, 45);
        Assert.assertEquals(ans[0], 35);
        Assert.assertEquals(ans[1], 45);
        //End is higher
        ans = vent.getOverlappedUpperBoundRange(35, 50);
        Assert.assertEquals(ans[0], 35);
        Assert.assertEquals(ans[1], 45);
        //Range is greater
        ans = vent.getOverlappedUpperBoundRange(30, 50);
        Assert.assertEquals(ans[0], 35);
        Assert.assertEquals(ans[1], 45);
        //Start is higher
        ans = vent.getOverlappedUpperBoundRange(40, 45);
        Assert.assertEquals(ans[0], 40);
        Assert.assertEquals(ans[1], 45);
        //End is lower
        ans = vent.getOverlappedUpperBoundRange(35, 40);
        Assert.assertEquals(ans[0], 35);
        Assert.assertEquals(ans[1], 40);
        //Both are within bounds
        ans = vent.getOverlappedUpperBoundRange(40, 40);
        Assert.assertEquals(ans[0], 40);
        Assert.assertEquals(ans[1], 40);
        //Out of bounds
        ans = vent.getOverlappedUpperBoundRange(25, 30);
        Assert.assertEquals(ans[0], -1);
        Assert.assertEquals(ans[1], -1);
    }

    public void getOutsideLowerBoundRangeTest() {
        VentStatus vent = new VentStatus('A');
        vent.setLowerBoundRange(35,45);
        //Start is lower
        int[] ans = vent.getOutsideLowerBoundRange(40, 45);
        Assert.assertEquals(ans[0], 35);
        Assert.assertEquals(ans[1], 39);
        //End is higher
        ans = vent.getOutsideLowerBoundRange(35, 40);
        Assert.assertEquals(ans[0], 41);
        Assert.assertEquals(ans[1], 45);
        //Range completely overlaps
        ans = vent.getOutsideLowerBoundRange(35, 45);
        Assert.assertEquals(ans[0], -1);
        Assert.assertEquals(ans[1], -1);
        //Out of bounds - Start is higher
        ans = vent.getOutsideLowerBoundRange(25, 30);
        Assert.assertEquals(ans[0], 35);
        Assert.assertEquals(ans[1], 45);
        //Out of bounds - End is lower
        ans = vent.getOutsideLowerBoundRange(50, 55);
        Assert.assertEquals(ans[0], 35);
        Assert.assertEquals(ans[1], 45);
        //Range is within bounds
        ans = vent.getOutsideLowerBoundRange(38, 42);
        Assert.assertEquals(ans[0], 35);
        Assert.assertEquals(ans[1], 45);
    }

    public void getOutsideUpperBoundRangeTest() {
        VentStatus vent = new VentStatus('A');
        vent.setUpperBoundRange(35,45);
        //Start is lower
        int[] ans = vent.getOutsideUpperBoundRange(40, 45);
        Assert.assertEquals(ans[0], 35);
        Assert.assertEquals(ans[1], 39);
        //End is higher
        ans = vent.getOutsideUpperBoundRange(35, 40);
        Assert.assertEquals(ans[0], 41);
        Assert.assertEquals(ans[1], 45);
        //Range completely overlaps
        ans = vent.getOutsideUpperBoundRange(35, 45);
        Assert.assertEquals(ans[0], -1);
        Assert.assertEquals(ans[1], -1);
        //Out of bounds - Start is higher
        ans = vent.getOutsideUpperBoundRange(25, 30);
        Assert.assertEquals(ans[0], 35);
        Assert.assertEquals(ans[1], 45);
        //Out of bounds - End is lower
        ans = vent.getOutsideUpperBoundRange(50, 55);
        Assert.assertEquals(ans[0], 35);
        Assert.assertEquals(ans[1], 45);
        //Range is within bounds
        ans = vent.getOutsideUpperBoundRange(38, 42);
        Assert.assertEquals(ans[0], 35);
        Assert.assertEquals(ans[1], 45);
    }

    public void doInnerBoundsClippingTest() {
        //Only test case is 41-59
        VentStatus vent = new VentStatus('A');

        //Both are out of bounds
        vent.setLowerBoundRange(38, 40);
        vent.setUpperBoundRange(60, 62);
        vent.doInnerBoundsClipping(41, 59);
        Assert.assertEquals(vent.getLowerBoundStart(), u);
        Assert.assertEquals(vent.getLowerBoundEnd(), u);
        Assert.assertEquals(vent.getUpperBoundStart(), u);
        Assert.assertEquals(vent.getUpperBoundEnd(), u);

        //Only lower bound is in bounds
        vent.setLowerBoundRange(41, 43);
        vent.setUpperBoundRange(63, 65);
        vent.doInnerBoundsClipping(41, 59);
        Assert.assertEquals(vent.getLowerBoundStart(), 41);
        Assert.assertEquals(vent.getLowerBoundEnd(), 43);
        Assert.assertEquals(vent.getUpperBoundStart(), 41);
        Assert.assertEquals(vent.getUpperBoundEnd(), 43);

        //Only upper bound is in bounds
        vent.setLowerBoundRange(35, 37);
        vent.setUpperBoundRange(57, 59);
        vent.doInnerBoundsClipping(41, 59);
        Assert.assertEquals(vent.getLowerBoundStart(), 57);
        Assert.assertEquals(vent.getLowerBoundEnd(), 59);
        Assert.assertEquals(vent.getUpperBoundStart(), 57);
        Assert.assertEquals(vent.getUpperBoundEnd(), 59);

        //Both are in bounds
        vent.setLowerBoundRange(41, 43);
        vent.setUpperBoundRange(57, 59);
        vent.doInnerBoundsClipping(41, 59);
        Assert.assertEquals(vent.getLowerBoundStart(), 41);
        Assert.assertEquals(vent.getLowerBoundEnd(), 43);
        Assert.assertEquals(vent.getUpperBoundStart(), 57);
        Assert.assertEquals(vent.getUpperBoundEnd(), 59);
    }

    public void doOuterBoundsClippingTest() {
        //Only test case is 41-59
        VentStatus vent = new VentStatus('A');

        //Both are out of bounds
        vent.setLowerBoundRange(38, 40);
        vent.setUpperBoundRange(60, 62);
        vent.doOuterBoundsClipping(41, 59);
        Assert.assertEquals(vent.getLowerBoundStart(), 38);
        Assert.assertEquals(vent.getLowerBoundEnd(), 40);
        Assert.assertEquals(vent.getUpperBoundStart(), 60);
        Assert.assertEquals(vent.getUpperBoundEnd(), 62);

        //Only lower bound is in bounds
        vent.setLowerBoundRange(41, 43);
        vent.setUpperBoundRange(63, 65);
        vent.doOuterBoundsClipping(41, 59);
        Assert.assertEquals(vent.getLowerBoundStart(), 63);
        Assert.assertEquals(vent.getLowerBoundEnd(), 65);
        Assert.assertEquals(vent.getUpperBoundStart(), 63);
        Assert.assertEquals(vent.getUpperBoundEnd(), 65);

        //Only upper bound is in bounds
        vent.setLowerBoundRange(35, 37);
        vent.setUpperBoundRange(57, 59);
        vent.doOuterBoundsClipping(41, 59);
        Assert.assertEquals(vent.getLowerBoundStart(), 35);
        Assert.assertEquals(vent.getLowerBoundEnd(), 37);
        Assert.assertEquals(vent.getUpperBoundStart(), 35);
        Assert.assertEquals(vent.getUpperBoundEnd(), 37);

        //Both are in bounds
        vent.setLowerBoundRange(41, 43);
        vent.setUpperBoundRange(57, 59);
        vent.doOuterBoundsClipping(41, 59);
        Assert.assertEquals(vent.getLowerBoundStart(), u);
        Assert.assertEquals(vent.getLowerBoundEnd(), u);
        Assert.assertEquals(vent.getUpperBoundStart(), u);
        Assert.assertEquals(vent.getUpperBoundEnd(), u);
    }

    public void doOuterBoundsSingleRangeClippingTest() {
        //Only test case is 41-59
        VentStatus vent = new VentStatus('A');

        //Range is lower than our clipping
        vent.setLowerBoundRange(38, 40);
        vent.setUpperBoundRange(38, 40);
        vent.doOuterBoundsClipping(41, 59);
        Assert.assertEquals(vent.getLowerBoundStart(), 38);
        Assert.assertEquals(vent.getLowerBoundEnd(), 40);
        Assert.assertEquals(vent.getUpperBoundStart(), 38);
        Assert.assertEquals(vent.getUpperBoundEnd(), 40);

        //Range is higher than our clipping
        vent.setLowerBoundRange(60, 62);
        vent.setUpperBoundRange(60, 62);
        vent.doOuterBoundsClipping(41, 59);
        Assert.assertEquals(vent.getLowerBoundStart(), 60);
        Assert.assertEquals(vent.getLowerBoundEnd(), 62);
        Assert.assertEquals(vent.getUpperBoundStart(), 60);
        Assert.assertEquals(vent.getUpperBoundEnd(), 62);

        //Range is inside of clipping
        vent.setLowerBoundRange(41, 59);
        vent.setUpperBoundRange(41, 59);
        vent.doOuterBoundsClipping(41, 59);
        Assert.assertEquals(vent.getLowerBoundStart(), u);
        Assert.assertEquals(vent.getLowerBoundEnd(), u);
        Assert.assertEquals(vent.getUpperBoundStart(), u);
        Assert.assertEquals(vent.getUpperBoundEnd(), u);

        //Lower end range clipping - single value
        vent.setLowerBoundRange(41, 60);
        vent.setUpperBoundRange(41, 60);
        vent.doOuterBoundsClipping(41, 59);
        Assert.assertEquals(vent.getLowerBoundStart(), 60);
        Assert.assertEquals(vent.getLowerBoundEnd(), 60);
        Assert.assertEquals(vent.getUpperBoundStart(), 60);
        Assert.assertEquals(vent.getUpperBoundEnd(), 60);

        //Lower end range clipping - range
        vent.setLowerBoundRange(41, 62);
        vent.setUpperBoundRange(41, 62);
        vent.doOuterBoundsClipping(41, 59);
        Assert.assertEquals(vent.getLowerBoundStart(), 60);
        Assert.assertEquals(vent.getLowerBoundEnd(), 62);
        Assert.assertEquals(vent.getUpperBoundStart(), 60);
        Assert.assertEquals(vent.getUpperBoundEnd(), 62);

        //Upper end range clipping - single value
        vent.setLowerBoundRange(40, 59);
        vent.setUpperBoundRange(40, 59);
        vent.doOuterBoundsClipping(41, 59);
        Assert.assertEquals(vent.getLowerBoundStart(), 40);
        Assert.assertEquals(vent.getLowerBoundEnd(), 40);
        Assert.assertEquals(vent.getUpperBoundStart(), 40);
        Assert.assertEquals(vent.getUpperBoundEnd(), 40);

        //Upper end range clipping - range
        vent.setLowerBoundRange(38, 59);
        vent.setUpperBoundRange(38, 59);
        vent.doOuterBoundsClipping(41, 59);
        Assert.assertEquals(vent.getLowerBoundStart(), 38);
        Assert.assertEquals(vent.getLowerBoundEnd(), 40);
        Assert.assertEquals(vent.getUpperBoundStart(), 38);
        Assert.assertEquals(vent.getUpperBoundEnd(), 40);

        //Breaking single range
        vent.setLowerBoundRange(38, 62);
        vent.setUpperBoundRange(38, 62);
        vent.doOuterBoundsClipping(41, 59);
        Assert.assertEquals(vent.getLowerBoundStart(), 38);
        Assert.assertEquals(vent.getLowerBoundEnd(), 40);
        Assert.assertEquals(vent.getUpperBoundStart(), 60);
        Assert.assertEquals(vent.getUpperBoundEnd(), 62);
    }

    public void getReversedInfluenceATest() {
        VentStatus vent = new VentStatus('A');

        //Unidentified vent
        Assert.assertEquals(vent.getReversedInfluence(0), VentStatus.STARTING_VENT_VALUE);

        //Cannot reverse bounded values
        vent.update(0, -1);
        Assert.assertEquals(vent.getReversedInfluence(0), VentStatus.STARTING_VENT_VALUE);
        vent.update(0, 1);
        Assert.assertEquals(vent.getReversedInfluence(0), VentStatus.STARTING_VENT_VALUE);
        vent.update(100, -1);
        Assert.assertEquals(vent.getReversedInfluence(0), VentStatus.STARTING_VENT_VALUE);
        vent.update(100, 1);
        Assert.assertEquals(vent.getReversedInfluence(0), VentStatus.STARTING_VENT_VALUE);

        //Non Freeze range A
        vent.update(60, -1);
        Assert.assertEquals(vent.getReversedInfluence(0), 0);
        vent.update(40, 1);
        Assert.assertEquals(vent.getReversedInfluence(0), 0);

        //Freeze range A
        vent.update(40, -1);
        Assert.assertEquals(vent.getReversedInfluence(0), -1);
        vent.update(41, -1);
        Assert.assertEquals(vent.getReversedInfluence(0), -1);
        vent.update(59, 1);
        Assert.assertEquals(vent.getReversedInfluence(0), -1);
        vent.update(60, 1);
        Assert.assertEquals(vent.getReversedInfluence(0), -1);

        //Freeze - non-freeze mismatch A
        //only 39 -> 41 is possible; 40 -> 42
        vent.update(41, 1);
        Assert.assertEquals(vent.getReversedInfluence(0), 0);
        //40 -> 42 or 41 -> 42 is possible
        vent.update(42, 1);
        Assert.assertEquals(vent.getReversedInfluence(0), VentStatus.STARTING_VENT_VALUE);
        //60 -> 58 or 59 -> 58 is possible
        vent.update(58, -1);
        Assert.assertEquals(vent.getReversedInfluence(0), VentStatus.STARTING_VENT_VALUE);
        //only 61 -> 59 is possible; 60 -> 58
        vent.update(59, -1);
        Assert.assertEquals(vent.getReversedInfluence(0), 0);

        //Impossible reverses
        //60 -> 62 and 59 -> 60; blocked 61 is impossible to reverse
        vent.update(61, 1);
        Assert.assertEquals(vent.getReversedInfluence(0), VentStatus.STARTING_VENT_VALUE);
        //40 -> 38 and 41 -> 40; unblocked 39 is impossible to reverse
        vent.update(39, -1);
        Assert.assertEquals(vent.getReversedInfluence(0), VentStatus.STARTING_VENT_VALUE);
    }

    public void getReversedInfluenceBTest() {
        VentStatus vent = new VentStatus('B');

        //We will assume A is in freeze range otherwise same result as above test(s)
        //Unidentified vent
        Assert.assertEquals(vent.getReversedInfluence(-1), VentStatus.STARTING_VENT_VALUE);

        //Cannot reverse bounded values
        vent.update(0, -1);
        Assert.assertEquals(vent.getReversedInfluence(-1), VentStatus.STARTING_VENT_VALUE);
        vent.update(0, 1);
        Assert.assertEquals(vent.getReversedInfluence(-1), VentStatus.STARTING_VENT_VALUE);
        vent.update(100, -1);
        Assert.assertEquals(vent.getReversedInfluence(-1), VentStatus.STARTING_VENT_VALUE);
        vent.update(100, 1);
        Assert.assertEquals(vent.getReversedInfluence(-1), VentStatus.STARTING_VENT_VALUE);

        //Non Freeze range B
        vent.update(60, -1);
        Assert.assertEquals(vent.getReversedInfluence(-1), 0);
        vent.update(40, 1);
        Assert.assertEquals(vent.getReversedInfluence(-1), 0);

        //Freeze range B
        vent.update(41, -1);
        Assert.assertEquals(vent.getReversedInfluence(-1), -1);
        vent.update(59, 1);
        Assert.assertEquals(vent.getReversedInfluence(-1), -1);

        //Impossible reverses
        //40 -> 41 and 41 -> 41; blocked 41 is impossible to reverse
        vent.update(41, 1);
        Assert.assertEquals(vent.getReversedInfluence(-1), VentStatus.STARTING_VENT_VALUE);
        //60 -> 59 and 59 -> 59; unblocked 59 is impossible to reverse
        vent.update(59, -1);
        Assert.assertEquals(vent.getReversedInfluence(-1), VentStatus.STARTING_VENT_VALUE);
        //59 -> 59; blocked 60 cannot come from anywhere
        vent.update(60, 1);
        Assert.assertEquals(vent.getReversedInfluence(-1), VentStatus.STARTING_VENT_VALUE);
        //41 -> 41; unblocked 40 cannot come from anywhere
        vent.update(40, -1);
        Assert.assertEquals(vent.getReversedInfluence(-1), VentStatus.STARTING_VENT_VALUE);
    }

    public void getReversedInfluenceCTest() {
        VentStatus vent = new VentStatus('B');

        //We will assume A and B are in freeze range otherwise same result as above test(s)
        //Unidentified vent
        Assert.assertEquals(vent.getReversedInfluence(-2), 0);

        //In this case we know bounded values stay the same
        vent.update(0, -1);
        Assert.assertEquals(vent.getReversedInfluence(-2), 0);
        vent.update(0, 1);
        Assert.assertEquals(vent.getReversedInfluence(-2), 0);
        vent.update(100, -1);
        Assert.assertEquals(vent.getReversedInfluence(-2), 0);
        vent.update(100, 1);
        Assert.assertEquals(vent.getReversedInfluence(-2), 0);

        //Non Freeze range C
        vent.update(60, -1);
        Assert.assertEquals(vent.getReversedInfluence(-2), 0);
        vent.update(60, 1);
        Assert.assertEquals(vent.getReversedInfluence(-2), 0);
        vent.update(40, 1);
        Assert.assertEquals(vent.getReversedInfluence(-2), 0);
        vent.update(40, -1);
        Assert.assertEquals(vent.getReversedInfluence(-2), 0);

        //Freeze range C
        vent.update(41, -1);
        Assert.assertEquals(vent.getReversedInfluence(-2), -1);
        vent.update(41, 1);
        Assert.assertEquals(vent.getReversedInfluence(-2), -1);
        vent.update(59, 1);
        Assert.assertEquals(vent.getReversedInfluence(-2), -1);
        vent.update(59, -1);
        Assert.assertEquals(vent.getReversedInfluence(-2), -1);
    }
}
