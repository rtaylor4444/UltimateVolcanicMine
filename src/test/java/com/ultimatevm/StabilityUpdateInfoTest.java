package com.ultimatevm;

import org.testng.annotations.Test;
import org.testng.Assert;

@Test()
public class StabilityUpdateInfoTest {

    public void constructorTest() {
        StatusState state = new StatusState();
        int u = VentStatus.STARTING_VENT_VALUE;
        state.updateVentStatus(new int[]{50, 50, u}, 20);
        StabilityUpdateInfo stabInfo = new StabilityUpdateInfo(state, 0, 20);

        Assert.assertFalse(stabInfo.isVerified());
        Assert.assertNotEquals(stabInfo.getStabilityUpdateState(), state);
        Assert.assertEquals(stabInfo.getRNGUpdateMod(), 0);
        Assert.assertEquals(stabInfo.getTickTimeStamp(), 0);
        Assert.assertEquals(stabInfo.getInitialChange(), 20);
        //State should have a calculated stability
        Assert.assertTrue(stabInfo.getStabilityUpdateState().getVents()[2].isRangeDefined());
    }

    public void verifyByInvalidPointsTest() {
        StatusState state = new StatusState();
        int u = VentStatus.STARTING_VENT_VALUE;
        state.updateVentStatus(new int[]{50, 50, u}, 0);
        StabilityUpdateInfo stabInfo = new StabilityUpdateInfo(state, 0, 24);
        stabInfo.verifyByInvalidPoints();

        Assert.assertEquals(stabInfo.getRNGUpdateMod(), 1);
        Assert.assertTrue(stabInfo.isVerified());
    }

    public void getAllPossiblePredictedValuesStateTest() {
        StatusState state = new StatusState();
        int u = VentStatus.STARTING_VENT_VALUE;
        state.updateVentStatus(new int[]{50, 50, u}, 0);

        //One huge range should be set here
        StabilityUpdateInfo stabInfo = new StabilityUpdateInfo(state, 0, 23);
        StatusState possibleValues = stabInfo.getAllPossiblePredictedValuesState();

        Assert.assertEquals(possibleValues.getVents()[2].getLowerBoundStart(), 44);
        Assert.assertEquals(possibleValues.getVents()[2].getUpperBoundStart(), 44);
        Assert.assertEquals(possibleValues.getVents()[2].getLowerBoundEnd(), 56);
        Assert.assertEquals(possibleValues.getVents()[2].getUpperBoundEnd(), 56);


        //Two huge ranges should be set here
        stabInfo = new StabilityUpdateInfo(state, 0, 22);
        possibleValues = stabInfo.getAllPossiblePredictedValuesState();

        Assert.assertEquals(possibleValues.getVents()[2].getLowerBoundStart(), 41);
        Assert.assertEquals(possibleValues.getVents()[2].getLowerBoundEnd(), 46);
        Assert.assertEquals(possibleValues.getVents()[2].getUpperBoundStart(), 54);
        Assert.assertEquals(possibleValues.getVents()[2].getUpperBoundEnd(), 59);
    }
}
