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
        state.updateVentStatus(new int[]{50, 50, u}, 24);
        StabilityUpdateInfo stabInfo = new StabilityUpdateInfo(state, 0, 24);
        stabInfo.verifyByInvalidPoints();

        Assert.assertEquals(stabInfo.getRNGUpdateMod(), 1);
        Assert.assertTrue(stabInfo.isVerified());
    }
}
