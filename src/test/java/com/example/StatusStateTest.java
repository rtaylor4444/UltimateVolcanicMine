package com.example;

import org.testng.annotations.Test;
import org.testng.Assert;

@Test()
public class StatusStateTest {
    VentStatus[] makeNewVents() {
        VentStatus[] vents = new VentStatus[StatusState.NUM_VENTS];
        vents[0] = new VentStatus('A');
        vents[1] = new VentStatus('B');
        vents[2] = new VentStatus('C');
        return vents;
    }

    public void constructorTest() {
        StatusState state = new StatusState(makeNewVents(), 1);
        for(int i = 0; i < StatusState.NUM_VENTS; ++i)
            Assert.assertNotNull(state.getVent(i));
        Assert.assertEquals(state.getStabilityChange(), 1);
        Assert.assertEquals(state.getNumIdentifiedVents(), 0);
        Assert.assertFalse(state.isAllVentsIdentified());
        Assert.assertFalse(state.isEnoughVentsIdentified());
    }

    public void copyConstructorTest() {
        StatusState originalState = new StatusState(makeNewVents(), 1);
        StatusState state = new StatusState(originalState);

        for(int i = 0; i < StatusState.NUM_VENTS; ++i)
            Assert.assertNotNull(state.getVent(i));
        Assert.assertEquals(state.getStabilityChange(), 1);
        Assert.assertEquals(state.getNumIdentifiedVents(), 0);
        Assert.assertFalse(state.isAllVentsIdentified());
        Assert.assertFalse(state.isEnoughVentsIdentified());
    }

    public void updateWithVentsTest() {
        VentStatus[] vents = makeNewVents();
        StatusState state = new StatusState(makeNewVents(), 0);
        vents[0].update(VentStatus.PERFECT_VENT_VALUE, 1);

        state.update(vents, 1);
        for(int i = 0; i < StatusState.NUM_VENTS; ++i)
            Assert.assertNotNull(state.getVent(i));
        Assert.assertEquals(state.getVent(0).getActualValue(), vents[0].getActualValue());
        Assert.assertEquals(state.getStabilityChange(), 1);
        Assert.assertEquals(state.getNumIdentifiedVents(), 1);
        Assert.assertFalse(state.isAllVentsIdentified());
        Assert.assertFalse(state.isEnoughVentsIdentified());
    }

    public void updateWithStatusTest() {
        VentStatus[] vents = makeNewVents();
        StatusState state = new StatusState(makeNewVents(), 0);
        vents[0].update(VentStatus.PERFECT_VENT_VALUE, 1);
        StatusState otherState = new StatusState(vents, 1);

        state.update(otherState);
        for(int i = 0; i < StatusState.NUM_VENTS; ++i)
            Assert.assertNotNull(state.getVent(i));
        Assert.assertEquals(state.getVent(0).getActualValue(), vents[0].getActualValue());
        Assert.assertEquals(state.getStabilityChange(), 1);
        Assert.assertEquals(state.getNumIdentifiedVents(), 1);
        Assert.assertFalse(state.isAllVentsIdentified());
        Assert.assertFalse(state.isEnoughVentsIdentified());
    }

    public void numIdentifiedVentsTest() {
        VentStatus[] vents = makeNewVents();
        StatusState state = new StatusState(makeNewVents(), 0);

        vents[0].update(VentStatus.PERFECT_VENT_VALUE, 1);
        state.update(vents, 1);
        Assert.assertEquals(state.getNumIdentifiedVents(), 1);
        Assert.assertFalse(state.isAllVentsIdentified());
        Assert.assertFalse(state.isEnoughVentsIdentified());

        vents[1].update(VentStatus.PERFECT_VENT_VALUE, 1);
        state.update(vents, 1);
        Assert.assertEquals(state.getNumIdentifiedVents(), 2);
        Assert.assertFalse(state.isAllVentsIdentified());
        Assert.assertTrue(state.isEnoughVentsIdentified());

        vents[2].update(VentStatus.PERFECT_VENT_VALUE, 1);
        state.update(vents, 1);
        Assert.assertEquals(state.getNumIdentifiedVents(), 3);
        Assert.assertTrue(state.isAllVentsIdentified());
        Assert.assertTrue(state.isEnoughVentsIdentified());
    }
}
