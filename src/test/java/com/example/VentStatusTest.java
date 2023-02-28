package com.example;

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
        Assert.assertEquals(vent.getMovementSinceLastState(), 0);
    }

    public void copyConstructorTest() {
        VentStatus vent = new VentStatus('A');
        vent.update(VentStatus.PERFECT_VENT_VALUE, 1);
        vent.updateMovement();

        VentStatus newVent = new VentStatus(vent);
        Assert.assertEquals(newVent.getName(), 'A');
        Assert.assertEquals(newVent.getDirection(), 1);
        Assert.assertEquals(newVent.getActualValue(), VentStatus.PERFECT_VENT_VALUE);
        Assert.assertEquals(newVent.getLowerBoundStart(), VentStatus.PERFECT_VENT_VALUE+1);
        Assert.assertEquals(newVent.getLowerBoundEnd(), VentStatus.PERFECT_VENT_VALUE+1);
        Assert.assertEquals(newVent.getUpperBoundStart(), VentStatus.PERFECT_VENT_VALUE+1);
        Assert.assertEquals(newVent.getUpperBoundEnd(), VentStatus.PERFECT_VENT_VALUE+1);
        Assert.assertEquals(newVent.getMovementSinceLastState(), 1);
    }

    public void setEqualToTest() {
        VentStatus vent = new VentStatus('A');
        vent.update(VentStatus.PERFECT_VENT_VALUE, 1);
        vent.updateMovement();

        VentStatus newVent = new VentStatus('A');
        newVent.setEqualTo(vent);
        Assert.assertEquals(newVent.getName(), 'A');
        Assert.assertEquals(newVent.getDirection(), 1);
        Assert.assertEquals(newVent.getActualValue(), VentStatus.PERFECT_VENT_VALUE);
        Assert.assertEquals(newVent.getLowerBoundStart(), VentStatus.PERFECT_VENT_VALUE+1);
        Assert.assertEquals(newVent.getLowerBoundEnd(), VentStatus.PERFECT_VENT_VALUE+1);
        Assert.assertEquals(newVent.getUpperBoundStart(), VentStatus.PERFECT_VENT_VALUE+1);
        Assert.assertEquals(newVent.getUpperBoundEnd(), VentStatus.PERFECT_VENT_VALUE+1);
        Assert.assertEquals(newVent.getMovementSinceLastState(), 1);
    }

    public void doVMResetTest() {
        VentStatus vent = new VentStatus('A');
        vent.update(VentStatus.PERFECT_VENT_VALUE, 1);
        vent.updateMovement();

        vent.doVMReset();
        Assert.assertEquals(vent.getName(), 'A');
        Assert.assertEquals(vent.getDirection(), 1);
        Assert.assertEquals(vent.getActualValue(), VentStatus.STARTING_VENT_VALUE);
        Assert.assertEquals(vent.getLowerBoundStart(), VentStatus.STARTING_VENT_VALUE);
        Assert.assertEquals(vent.getLowerBoundEnd(), VentStatus.STARTING_VENT_VALUE);
        Assert.assertEquals(vent.getUpperBoundStart(), VentStatus.STARTING_VENT_VALUE);
        Assert.assertEquals(vent.getUpperBoundEnd(), VentStatus.STARTING_VENT_VALUE);
        Assert.assertEquals(vent.getMovementSinceLastState(), 0);
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

    public void updateMovementTest() {
        //Upward movement should update since neither 0% or 100% are in range
        VentStatus vent = new VentStatus('A');
        vent.update(VentStatus.STARTING_VENT_VALUE, 1);
        vent.setLowerBoundRange(VentStatus.MIN_VENT_VALUE+1, 47);
        vent.setUpperBoundRange(53, VentStatus.MAX_VENT_VALUE-1);
        vent.updateMovement();
        Assert.assertEquals(vent.getMovementSinceLastState(), 1);

        //Upward movement should not be updated 100% is in range
        vent.update(VentStatus.STARTING_VENT_VALUE, 1);
        vent.setLowerBoundRange(VentStatus.MIN_VENT_VALUE+1, 47);
        vent.setUpperBoundRange(53, VentStatus.MAX_VENT_VALUE+1);
        vent.clearMovement();
        vent.updateMovement();
        Assert.assertEquals(vent.getMovementSinceLastState(), 0);

        //Upward movement should update even though 0% is in range
        vent.update(VentStatus.STARTING_VENT_VALUE, 1);
        vent.setLowerBoundRange(VentStatus.MIN_VENT_VALUE-1, 47);
        vent.setUpperBoundRange(53, VentStatus.MAX_VENT_VALUE-1);
        vent.clearMovement();
        vent.updateMovement();
        Assert.assertEquals(vent.getMovementSinceLastState(), 1);


        //Downward movement should update since neither 0% or 100% are in range
        vent.update(VentStatus.STARTING_VENT_VALUE, -1);
        vent.setLowerBoundRange(VentStatus.MIN_VENT_VALUE+1, 47);
        vent.setUpperBoundRange(53, VentStatus.MAX_VENT_VALUE-1);
        vent.clearMovement();
        vent.updateMovement();
        Assert.assertEquals(vent.getMovementSinceLastState(), -1);

        //Downward movement should not be updated 0% is in range
        vent.update(VentStatus.STARTING_VENT_VALUE, -1);
        vent.setLowerBoundRange(VentStatus.MIN_VENT_VALUE-1, 47);
        vent.setUpperBoundRange(53, VentStatus.MAX_VENT_VALUE+1);
        vent.clearMovement();
        vent.updateMovement();
        Assert.assertEquals(vent.getMovementSinceLastState(), 0);

        //Downward movement should update even though 100% is in range
        vent.update(VentStatus.STARTING_VENT_VALUE, -1);
        vent.setLowerBoundRange(VentStatus.MIN_VENT_VALUE+1, 47);
        vent.setUpperBoundRange(53, VentStatus.MAX_VENT_VALUE+1);
        vent.clearMovement();
        vent.updateMovement();
        Assert.assertEquals(vent.getMovementSinceLastState(), -1);
    }

    public void updateMovementRangeTest() {
        //Ranges should not be made if they are undefined
        VentStatus vent = new VentStatus('A');
        vent.update(VentStatus.STARTING_VENT_VALUE, 1);
        vent.updateMovement();
        Assert.assertFalse(vent.isRangeDefined());

        //Ranges should be updated and capped at 100%
        vent.setLowerBoundRange(VentStatus.MIN_VENT_VALUE, 3);
        vent.setUpperBoundRange(97, VentStatus.MAX_VENT_VALUE);
        vent.updateMovement();
        Assert.assertEquals(vent.getLowerBoundStart(), VentStatus.MIN_VENT_VALUE+1);
        Assert.assertEquals(vent.getLowerBoundEnd(), 4);
        Assert.assertEquals(vent.getUpperBoundStart(), 98);
        Assert.assertEquals(vent.getUpperBoundEnd(), VentStatus.MAX_VENT_VALUE);

        //Ranges should be updated and capped at 0%
        vent.update(VentStatus.STARTING_VENT_VALUE, -1);
        vent.setLowerBoundRange(VentStatus.MIN_VENT_VALUE, 3);
        vent.setUpperBoundRange(97, VentStatus.MAX_VENT_VALUE);
        vent.updateMovement();
        Assert.assertEquals(vent.getLowerBoundStart(), VentStatus.MIN_VENT_VALUE);
        Assert.assertEquals(vent.getLowerBoundEnd(), 2);
        Assert.assertEquals(vent.getUpperBoundStart(), 96);
        Assert.assertEquals(vent.getUpperBoundEnd(), VentStatus.MAX_VENT_VALUE-1);

        //Ranges should merge and move properly
        vent.setLowerBoundRange(47, VentStatus.PERFECT_VENT_VALUE);
        vent.setUpperBoundRange(VentStatus.PERFECT_VENT_VALUE, 53);
        vent.updateMovement();
        Assert.assertEquals(vent.getLowerBoundStart(), 46);
        Assert.assertEquals(vent.getLowerBoundEnd(), 52);
        Assert.assertEquals(vent.getUpperBoundStart(), 46);
        Assert.assertEquals(vent.getUpperBoundEnd(), 52);

        //Ranges should merge and move properly even max ranges
        vent.setLowerBoundRange(VentStatus.MIN_VENT_VALUE, VentStatus.PERFECT_VENT_VALUE);
        vent.setUpperBoundRange(VentStatus.PERFECT_VENT_VALUE, VentStatus.MAX_VENT_VALUE);
        vent.updateMovement();
        Assert.assertEquals(vent.getLowerBoundStart(), VentStatus.MIN_VENT_VALUE);
        Assert.assertEquals(vent.getLowerBoundEnd(), VentStatus.MAX_VENT_VALUE-1);
        Assert.assertEquals(vent.getUpperBoundStart(), VentStatus.MIN_VENT_VALUE);
        Assert.assertEquals(vent.getUpperBoundEnd(), VentStatus.MAX_VENT_VALUE-1);
    }

    public void clearMovementTest() {
        VentStatus vent = new VentStatus('A');
        vent.update(VentStatus.STARTING_VENT_VALUE, 1);
        vent.setLowerBoundRange(50, 50);
        vent.setUpperBoundRange(50, 50);
        vent.updateMovement();
        Assert.assertEquals(vent.getMovementSinceLastState(), 1);
        vent.clearMovement();
        Assert.assertEquals(vent.getMovementSinceLastState(), 0);
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
}
