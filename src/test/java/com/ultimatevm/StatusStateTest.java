package com.ultimatevm;

import org.testng.annotations.Test;
import org.testng.Assert;

@Test()
public class StatusStateTest {
    private final int u = VentStatus.STARTING_VENT_VALUE;
    private int makeMoveBitState(int aMove, int bMove, int cMove) {
        return aMove | (bMove << 2) | (cMove << 4);
    }

    public void constructorTest() {
        StatusState state = new StatusState();
        for(int i = 0; i < StatusState.NUM_VENTS; ++i)
            Assert.assertNotNull(state.getVents()[i]);
        Assert.assertEquals(state.getNumIdentifiedVents(), 0);
        Assert.assertFalse(state.isAllVentsIdentified());
        Assert.assertFalse(state.isEnoughVentsIdentified());
        Assert.assertFalse(state.hasDoneVMReset());
    }

    public void copyConstructorTest() {
        StatusState originalState = new StatusState();
        originalState.doVMReset();
        originalState.updateVentStatus(new int[]{0,0,0},0);
        StatusState state = new StatusState(originalState);

        for(int i = 0; i < StatusState.NUM_VENTS; ++i) {
            Assert.assertNotNull(state.getVents()[i]);
            Assert.assertEquals(state.getVents()[i].getActualValue(), 0);
        }
        Assert.assertEquals(state.getNumIdentifiedVents(), 3);
        Assert.assertTrue(state.isAllVentsIdentified());
        Assert.assertTrue(state.isEnoughVentsIdentified());
        Assert.assertTrue(state.hasDoneVMReset());
    }

    public void setVentsEqualToTest() {
        StatusState originalState = new StatusState();
        originalState.updateVentStatus(new int[]{0,0,0},0);
        StatusState state = new StatusState();
        state.setVentsEqualTo(originalState);
        for(int i = 0; i < StatusState.NUM_VENTS; ++i) {
            Assert.assertEquals(state.getVents()[i].getActualValue(), 0);
        }
        Assert.assertEquals(state.getNumIdentifiedVents(), 3);
        Assert.assertTrue(state.isAllVentsIdentified());
        Assert.assertTrue(state.isEnoughVentsIdentified());
    }

    public void setEqualToTest() {
        StatusState state = new StatusState();
        StatusState originalState = new StatusState();
        originalState.doVMReset();
        originalState.updateVentStatus(new int[]{0,0,0},0);
        state.setEqualTo(originalState);

        for(int i = 0; i < StatusState.NUM_VENTS; ++i) {
            Assert.assertNotNull(state.getVents()[i]);
            Assert.assertEquals(state.getVents()[i].getActualValue(), 0);
        }
        Assert.assertEquals(state.getNumIdentifiedVents(), 3);
        Assert.assertTrue(state.isAllVentsIdentified());
        Assert.assertTrue(state.isEnoughVentsIdentified());
        Assert.assertTrue(state.hasDoneVMReset());
    }

    public void updateVentStatusTest() {
        StatusState state = new StatusState();
        final VentStatus[] vents = state.getVents();
        //Ensure our vents are set properly
        int[] changeStates = state.updateVentStatus(new int[]{50,50,50}, 7);
        for(int i = 0; i < StatusState.NUM_VENTS; ++i) {
            Assert.assertEquals(vents[i].getActualValue(), 50);
            Assert.assertEquals(vents[i].getDirection(), 1);
            Assert.assertEquals(changeStates[i], VentStatus.VentChangeStateFlag.IDENTIFIED.bitFlag());
        }
        changeStates = state.updateVentStatus(new int[]{49,49,49}, 0);
        for(int i = 0; i < StatusState.NUM_VENTS; ++i) {
            Assert.assertEquals(vents[i].getActualValue(), 49);
            Assert.assertEquals(vents[i].getDirection(), -1);
            Assert.assertEquals(changeStates[i],
                    VentStatus.VentChangeStateFlag.ONE_CHANGE.bitFlag() + VentStatus.VentChangeStateFlag.DIRECTION_CHANGE.bitFlag());
        }
    }

    public void updateVentMovementTest() {
        StatusState state = new StatusState();
        final VentStatus[] vents = state.getVents();
        state.updateVentStatus(new int[]{u, u, u}, 7);

        vents[0].clearRanges();
        vents[0].setUpperBoundRange(50, 50);
        vents[0].setLowerBoundRange(50, 50);

        state.updateVentMovement();
        Assert.assertEquals(vents[0].getLowerBoundStart(), 51);
        Assert.assertEquals(vents[0].getLowerBoundEnd(), 51);
        Assert.assertEquals(vents[0].getUpperBoundStart(), 51);
        Assert.assertEquals(vents[0].getUpperBoundEnd(), 51);
    }

    public void updateVentMovementAInfluenceTest() {
        StatusState state = new StatusState();
        final VentStatus[] vents = state.getVents();
        state.updateVentStatus(new int[]{u, 50, 50}, 7);

        //A never freezes
        //Movement by 1 since 41-59% range
        vents[0].clearRanges();
        vents[0].setLowerBoundRange(41, 59);
        vents[0].setUpperBoundRange(41, 59);
        state.updateVentMovement();
        Assert.assertEquals(vents[0].getLowerBoundStart(), 42);
        Assert.assertEquals(vents[0].getUpperBoundStart(), 42);
        Assert.assertEquals(vents[0].getLowerBoundEnd(), 60);
        Assert.assertEquals(vents[0].getUpperBoundEnd(), 60);

        //Movement by 2 since outside of 41-59% range
        vents[0].clearRanges();
        vents[0].setLowerBoundRange(0, 40);
        vents[0].setUpperBoundRange(60, 99);
        state.updateVentMovement();
        Assert.assertEquals(vents[0].getLowerBoundStart(), 2);
        Assert.assertEquals(vents[0].getLowerBoundEnd(), 42);
        Assert.assertEquals(vents[0].getUpperBoundStart(), 62);
        Assert.assertEquals(vents[0].getUpperBoundEnd(), 100);
    }

    public void updateVentMovementBInfluenceTest() {
        StatusState state = new StatusState();
        final VentStatus[] vents = state.getVents();

        //B is only influenced by A
        //Unidentified A influence (possible 0, -1 movement inf)
        state.updateVentStatus(new int[]{u, u, 50}, 7);
        //Movement by 0 and 1 since A is unknown and B is 41-59% range
        vents[1].clearRanges();
        vents[1].setLowerBoundRange(41, 59);
        vents[1].setUpperBoundRange(41, 59);
        state.updateVentMovement();
        Assert.assertEquals(vents[1].getLowerBoundStart(), 41);
        Assert.assertEquals(vents[1].getUpperBoundStart(), 41);
        Assert.assertEquals(vents[1].getLowerBoundEnd(), 60);
        Assert.assertEquals(vents[1].getUpperBoundEnd(), 60);

        //Movement by 1 and 2 since A is unknown and B is outside of 41-59% range
        vents[1].clearRanges();
        vents[1].setLowerBoundRange(0, 40);
        vents[1].setUpperBoundRange(60, 99);
        state.updateVentMovement();
        Assert.assertEquals(vents[1].getLowerBoundStart(), 1);
        Assert.assertEquals(vents[1].getLowerBoundEnd(), 42);
        Assert.assertEquals(vents[1].getUpperBoundStart(), 61);
        Assert.assertEquals(vents[1].getUpperBoundEnd(), 100);


        //A 41-59% range influence (possible -1 movement inf)
        state.updateVentStatus(new int[]{50, u, 50}, 7);
        //Movement by 0 since A and B are 41-59% range
        vents[1].clearRanges();
        vents[1].setLowerBoundRange(41, 59);
        vents[1].setUpperBoundRange(41, 59);
        state.updateVentMovement();
        Assert.assertEquals(vents[1].getLowerBoundStart(), 41);
        Assert.assertEquals(vents[1].getUpperBoundStart(), 41);
        Assert.assertEquals(vents[1].getLowerBoundEnd(), 59);
        Assert.assertEquals(vents[1].getUpperBoundEnd(), 59);

        //Movement by 1 since A is 41-59% and B is outside of 41-59% range
        vents[1].clearRanges();
        vents[1].setLowerBoundRange(0, 40);
        vents[1].setUpperBoundRange(60, 99);
        state.updateVentMovement();
        Assert.assertEquals(vents[1].getLowerBoundStart(), 1);
        Assert.assertEquals(vents[1].getLowerBoundEnd(), 41);
        Assert.assertEquals(vents[1].getUpperBoundStart(), 61);
        Assert.assertEquals(vents[1].getUpperBoundEnd(), 100);


        //Zero A influence (outside 41-59%) (possible 0 movement inf)
        state.updateVentStatus(new int[]{0, u, 50}, 7);
        //Movement by 1 since A is not 41-59% but B is 41-59% range
        vents[1].clearRanges();
        vents[1].setLowerBoundRange(41, 59);
        vents[1].setUpperBoundRange(41, 59);
        state.updateVentMovement();
        Assert.assertEquals(vents[1].getLowerBoundStart(), 42);
        Assert.assertEquals(vents[1].getUpperBoundStart(), 42);
        Assert.assertEquals(vents[1].getLowerBoundEnd(), 60);
        Assert.assertEquals(vents[1].getUpperBoundEnd(), 60);

        //Movement by 2 since A and B are outside of 41-59% range
        vents[1].clearRanges();
        vents[1].setLowerBoundRange(0, 40);
        vents[1].setUpperBoundRange(60, 99);
        state.updateVentMovement();
        Assert.assertEquals(vents[1].getLowerBoundStart(), 2);
        Assert.assertEquals(vents[1].getLowerBoundEnd(), 42);
        Assert.assertEquals(vents[1].getUpperBoundStart(), 62);
        Assert.assertEquals(vents[1].getUpperBoundEnd(), 100);
    }

    public void updateVentMovementCInfluenceTest() {
        StatusState state = new StatusState();
        final VentStatus[] vents = state.getVents();

        //C is influenced by both A and B
        //Unidentified A and B influence (possible 0, -2 movement inf)
        state.updateVentStatus(new int[]{u, u, u}, 7);

        //Movement by 0 and 1 since A and B are unknown
        vents[2].clearRanges();
        vents[2].setLowerBoundRange(41, 59);
        vents[2].setUpperBoundRange(41, 59);
        state.updateVentMovement();
        Assert.assertEquals(vents[2].getLowerBoundStart(), 41);
        Assert.assertEquals(vents[2].getUpperBoundStart(), 41);
        Assert.assertEquals(vents[2].getLowerBoundEnd(), 60);
        Assert.assertEquals(vents[2].getUpperBoundEnd(), 60);

        //Movement by 0 and 2 since A and B are unknown;
        //C is also outside of 41-59% range
        vents[2].clearRanges();
        vents[2].setLowerBoundRange(0, 40);
        vents[2].setUpperBoundRange(60, 98);
        state.updateVentMovement();
        Assert.assertEquals(vents[2].getLowerBoundStart(), 0);
        Assert.assertEquals(vents[2].getLowerBoundEnd(), 42);
        Assert.assertEquals(vents[2].getUpperBoundStart(), 60);
        Assert.assertEquals(vents[2].getUpperBoundEnd(), 100);


        //Unidentified A and 41-59% B influence (possible -1, -2 movement inf)
        state.updateVentStatus(new int[]{u, 50, u}, 7);

        //Movement by 0 since A is unknown and B is 41-59%
        vents[2].clearRanges();
        vents[2].setLowerBoundRange(41, 59);
        vents[2].setUpperBoundRange(41, 59);
        state.updateVentMovement();
        Assert.assertEquals(vents[2].getLowerBoundStart(), 41);
        Assert.assertEquals(vents[2].getUpperBoundStart(), 41);
        Assert.assertEquals(vents[2].getLowerBoundEnd(), 59);
        Assert.assertEquals(vents[2].getUpperBoundEnd(), 59);

        //Movement by 0 and 1 since A is unknown and B is 41-59%;
        //doesnt matter C is outside of 41-59% range
        vents[2].clearRanges();
        vents[2].setLowerBoundRange(0, 40);
        vents[2].setUpperBoundRange(60, 99);
        state.updateVentMovement();
        Assert.assertEquals(vents[2].getLowerBoundStart(), 0);
        Assert.assertEquals(vents[2].getLowerBoundEnd(), 41);
        Assert.assertEquals(vents[2].getUpperBoundStart(), 60);
        Assert.assertEquals(vents[2].getUpperBoundEnd(), 100);


        //Unidentified A and outside 41-59% B influence (possible 0, -1 movement inf)
        state.updateVentStatus(new int[]{u, 0, u}, 7);

        //Movement by 0 and 1 since A is unknown and C is 41-59%;
        //B is outside of 41-59% range
        vents[2].clearRanges();
        vents[2].setLowerBoundRange(41, 59);
        vents[2].setUpperBoundRange(41, 59);
        state.updateVentMovement();
        Assert.assertEquals(vents[2].getLowerBoundStart(), 41);
        Assert.assertEquals(vents[2].getUpperBoundStart(), 41);
        Assert.assertEquals(vents[2].getLowerBoundEnd(), 60);
        Assert.assertEquals(vents[2].getUpperBoundEnd(), 60);

        //Movement by 1 and 2 since A is unknown;
        //B and C are outside of 41-59% range
        vents[2].clearRanges();
        vents[2].setLowerBoundRange(0, 40);
        vents[2].setUpperBoundRange(60, 98);
        state.updateVentMovement();
        Assert.assertEquals(vents[2].getLowerBoundStart(), 1);
        Assert.assertEquals(vents[2].getLowerBoundEnd(), 42);
        Assert.assertEquals(vents[2].getUpperBoundStart(), 61);
        Assert.assertEquals(vents[2].getUpperBoundEnd(), 100);




        //41-59% A and unidentified B influence (possible -1, -2 movement inf)
        state.updateVentStatus(new int[]{50, u, u}, 7);
        vents[1].clearRanges();

        //Movement by 0 since A is 41-59% and B is unknown
        vents[2].clearRanges();
        vents[2].setLowerBoundRange(41, 59);
        vents[2].setUpperBoundRange(41, 59);
        state.updateVentMovement();
        Assert.assertEquals(vents[2].getLowerBoundStart(), 41);
        Assert.assertEquals(vents[2].getUpperBoundStart(), 41);
        Assert.assertEquals(vents[2].getLowerBoundEnd(), 59);
        Assert.assertEquals(vents[2].getUpperBoundEnd(), 59);

        //Movement by 0 and 1 since A and B are unknown;
        //C is outside of 41-59% range
        vents[2].clearRanges();
        vents[2].setLowerBoundRange(0, 40);
        vents[2].setUpperBoundRange(60, 99);
        state.updateVentMovement();
        Assert.assertEquals(vents[2].getLowerBoundStart(), 0);
        Assert.assertEquals(vents[2].getLowerBoundEnd(), 41);
        Assert.assertEquals(vents[2].getUpperBoundStart(), 60);
        Assert.assertEquals(vents[2].getUpperBoundEnd(), 100);


        //41-59% A and B influence (possible -2 movement inf)
        state.updateVentStatus(new int[]{50, 50, u}, 7);

        //Movement by 0 since A and B are 41-59%
        vents[2].clearRanges();
        vents[2].setLowerBoundRange(41, 59);
        vents[2].setUpperBoundRange(41, 59);
        state.updateVentMovement();
        Assert.assertEquals(vents[2].getLowerBoundStart(), 41);
        Assert.assertEquals(vents[2].getUpperBoundStart(), 41);
        Assert.assertEquals(vents[2].getLowerBoundEnd(), 59);
        Assert.assertEquals(vents[2].getUpperBoundEnd(), 59);

        //Movement by 0 since A and B are 41-59%;
        //doesnt matter C is outside of 41-59% range
        vents[2].clearRanges();
        vents[2].setLowerBoundRange(0, 40);
        vents[2].setUpperBoundRange(60, 99);
        state.updateVentMovement();
        Assert.assertEquals(vents[2].getLowerBoundStart(), 0);
        Assert.assertEquals(vents[2].getLowerBoundEnd(), 40);
        Assert.assertEquals(vents[2].getUpperBoundStart(), 60);
        Assert.assertEquals(vents[2].getUpperBoundEnd(), 99);


        //41-59% A and outside 41-59% B influence (possible -1 movement inf)
        state.updateVentStatus(new int[]{50, 0, u}, 7);

        //Movement by 0 since A and C are 41-59%;
        //doesnt matter B is outside of 41-59% range
        vents[2].clearRanges();
        vents[2].setLowerBoundRange(41, 59);
        vents[2].setUpperBoundRange(41, 59);
        state.updateVentMovement();
        Assert.assertEquals(vents[2].getLowerBoundStart(), 41);
        Assert.assertEquals(vents[2].getUpperBoundStart(), 41);
        Assert.assertEquals(vents[2].getLowerBoundEnd(), 59);
        Assert.assertEquals(vents[2].getUpperBoundEnd(), 59);

        //Movement by 1 since B and C are outside of 41-59% range;
        //because A is 41-59%
        vents[2].clearRanges();
        vents[2].setLowerBoundRange(0, 40);
        vents[2].setUpperBoundRange(60, 99);
        state.updateVentMovement();
        Assert.assertEquals(vents[2].getLowerBoundStart(), 1);
        Assert.assertEquals(vents[2].getLowerBoundEnd(), 41);
        Assert.assertEquals(vents[2].getUpperBoundStart(), 61);
        Assert.assertEquals(vents[2].getUpperBoundEnd(), 100);




        //Outside 41-59% A and unidentified B influence (possible 0, -1 movement inf)
        state.updateVentStatus(new int[]{0, u, u}, 7);
        vents[1].clearRanges();

        //Movement by 0 and 1 since B is unknown and C is 41-59%
        //even though A is outside of 41-59%
        vents[2].clearRanges();
        vents[2].setLowerBoundRange(41, 59);
        vents[2].setUpperBoundRange(41, 59);
        state.updateVentMovement();
        Assert.assertEquals(vents[2].getLowerBoundStart(), 41);
        Assert.assertEquals(vents[2].getUpperBoundStart(), 41);
        Assert.assertEquals(vents[2].getLowerBoundEnd(), 60);
        Assert.assertEquals(vents[2].getUpperBoundEnd(), 60);

        //Movement by 1 and 2  since B is unknown;
        //both A and C are outside of 41-59% range
        vents[2].clearRanges();
        vents[2].setLowerBoundRange(0, 40);
        vents[2].setUpperBoundRange(60, 98);
        state.updateVentMovement();
        Assert.assertEquals(vents[2].getLowerBoundStart(), 1);
        Assert.assertEquals(vents[2].getLowerBoundEnd(), 42);
        Assert.assertEquals(vents[2].getUpperBoundStart(), 61);
        Assert.assertEquals(vents[2].getUpperBoundEnd(), 100);


        //Outside 41-59% A and 41-59% B influence (possible -1 movement inf)
        state.updateVentStatus(new int[]{0, 50, u}, 7);

        //Movement by 0 since B and C are 41-59%
        vents[2].clearRanges();
        vents[2].setLowerBoundRange(41, 59);
        vents[2].setUpperBoundRange(41, 59);
        state.updateVentMovement();
        Assert.assertEquals(vents[2].getLowerBoundStart(), 41);
        Assert.assertEquals(vents[2].getUpperBoundStart(), 41);
        Assert.assertEquals(vents[2].getLowerBoundEnd(), 59);
        Assert.assertEquals(vents[2].getUpperBoundEnd(), 59);

        //Movement by 1 since A and C are outside 41-59%;
        //B is 41-59% range
        vents[2].clearRanges();
        vents[2].setLowerBoundRange(0, 40);
        vents[2].setUpperBoundRange(60, 99);
        state.updateVentMovement();
        Assert.assertEquals(vents[2].getLowerBoundStart(), 1);
        Assert.assertEquals(vents[2].getLowerBoundEnd(), 41);
        Assert.assertEquals(vents[2].getUpperBoundStart(), 61);
        Assert.assertEquals(vents[2].getUpperBoundEnd(), 100);


        //41-59% A and outside 41-59% B influence (possible 0 movement inf)
        state.updateVentStatus(new int[]{0, 0, u}, 7);

        //Movement by 1 since A and B are outside 41-59%;
        //C is 41-59% range
        vents[2].clearRanges();
        vents[2].setLowerBoundRange(41, 59);
        vents[2].setUpperBoundRange(41, 59);
        state.updateVentMovement();
        Assert.assertEquals(vents[2].getLowerBoundStart(), 42);
        Assert.assertEquals(vents[2].getUpperBoundStart(), 42);
        Assert.assertEquals(vents[2].getLowerBoundEnd(), 60);
        Assert.assertEquals(vents[2].getUpperBoundEnd(), 60);

        //Movement by 2 since all vents are outside of 41-59% range;
        vents[2].clearRanges();
        vents[2].setLowerBoundRange(0, 40);
        vents[2].setUpperBoundRange(60, 99);
        state.updateVentMovement();
        Assert.assertEquals(vents[2].getLowerBoundStart(), 2);
        Assert.assertEquals(vents[2].getLowerBoundEnd(), 42);
        Assert.assertEquals(vents[2].getUpperBoundStart(), 62);
        Assert.assertEquals(vents[2].getUpperBoundEnd(), 100);
    }

    public void doVMResetTest() {
        StatusState state = new StatusState();
        final VentStatus[] vents = state.getVents();
        state.updateVentStatus(new int[]{0,0,0}, 7);
        state.doVMReset();
        //All values should be clear
        for(int i = 0; i < StatusState.NUM_VENTS; ++i) {
            Assert.assertEquals(vents[i].getActualValue(), VentStatus.STARTING_VENT_VALUE);
        }
        Assert.assertTrue(state.hasDoneVMReset());
        //Should fail if called a second time
        state.updateVentStatus(new int[]{0,0,0}, 7);
        state.doVMReset();
        for(int i = 0; i < StatusState.NUM_VENTS; ++i) {
            Assert.assertEquals(vents[i].getActualValue(), 0);
        }
        Assert.assertTrue(state.hasDoneVMReset());
    }

    public void getUnidentifiedVentIndicesTest() {
        StatusState state = new StatusState();
        //All
        state.updateVentStatus(new int[]{u,u,u}, 7);
        Assert.assertEquals(state.getUnidentifiedVentIndices(), new int[]{0,1,2});
        //Two
        state.updateVentStatus(new int[]{0,u,u}, 7);
        Assert.assertEquals(state.getUnidentifiedVentIndices(), new int[]{1,2});
        state.updateVentStatus(new int[]{u,0,u}, 7);
        Assert.assertEquals(state.getUnidentifiedVentIndices(), new int[]{0,2});
        state.updateVentStatus(new int[]{u,u,0}, 7);
        Assert.assertEquals(state.getUnidentifiedVentIndices(), new int[]{0,1});
        //One
        state.updateVentStatus(new int[]{u,0,0}, 7);
        Assert.assertEquals(state.getUnidentifiedVentIndices(), new int[]{0});
        state.updateVentStatus(new int[]{0,u,0}, 7);
        Assert.assertEquals(state.getUnidentifiedVentIndices(), new int[]{1});
        state.updateVentStatus(new int[]{0,0,u}, 7);
        Assert.assertEquals(state.getUnidentifiedVentIndices(), new int[]{2});
        //None
        state.updateVentStatus(new int[]{0,0,0}, 7);
        Assert.assertEquals(state.getUnidentifiedVentIndices(), new int[]{});
    }

    public void calcPredictedVentValuesTest() {
        StatusState state = new StatusState();
        //All vents are identifed should only update stability change
        state.updateVentStatus(new int[]{0, 51, 51}, 7);
        state.calcPredictedVentValues(25);
        Assert.assertEquals(state.getStabilityChange(), 25);
        final VentStatus[] vents = state.getVents();
        Assert.assertTrue(vents[0].isRangeDefined());
        Assert.assertEquals(vents[0].getLowerBoundStart(), 0);
        Assert.assertEquals(vents[0].getLowerBoundEnd(), 0);
        Assert.assertEquals(vents[0].getUpperBoundStart(), 0);
        Assert.assertEquals(vents[0].getUpperBoundEnd(), 0);

        //No vents are identifed should only update stability change
        state = new StatusState();
        state.updateVentStatus(new int[]{u, u, u}, 7);
        state.calcPredictedVentValues(25);
        Assert.assertEquals(state.getStabilityChange(), 25);
        final VentStatus[] vents2 = state.getVents();
        for(int i = 0; i < vents2.length; ++i) {
            Assert.assertEquals(vents2[i].getLowerBoundStart(), VentStatus.MIN_STARTING_VENT_VALUE);
            Assert.assertEquals(vents2[i].getLowerBoundEnd(), VentStatus.MAX_STARTING_VENT_VALUE);
            Assert.assertEquals(vents2[i].getUpperBoundStart(), VentStatus.MIN_STARTING_VENT_VALUE);
            Assert.assertEquals(vents2[i].getUpperBoundEnd(), VentStatus.MAX_STARTING_VENT_VALUE);
        }
    }

    public void calcSingleVentValueTest() {
        //Max calc test
        StatusState state = new StatusState();
        state.updateVentStatus(new int[]{VentStatus.STARTING_VENT_VALUE, 50, 50}, 7);
        Assert.assertTrue(state.calcPredictedVentValues(23));
        Assert.assertEquals(state.getStabilityChange(), 23);
        final VentStatus[] vents = state.getVents();
        Assert.assertTrue(vents[0].isRangeDefined());
        Assert.assertEquals(vents[0].getLowerBoundStart(), 47);
        Assert.assertEquals(vents[0].getLowerBoundEnd(), 53);
        Assert.assertEquals(vents[0].getUpperBoundStart(), 47);
        Assert.assertEquals(vents[0].getUpperBoundEnd(), 53);

        //Normal calc test
        Assert.assertTrue(state.calcPredictedVentValues(22));
        Assert.assertEquals(state.getStabilityChange(), 22);
        Assert.assertEquals(vents[0].getLowerBoundStart(), 44);
        Assert.assertEquals(vents[0].getLowerBoundEnd(), 46);
        Assert.assertEquals(vents[0].getUpperBoundStart(), 54);
        Assert.assertEquals(vents[0].getUpperBoundEnd(), 56);

        //Min calc test
        Assert.assertTrue(state.calcPredictedVentValues(7));
        Assert.assertEquals(state.getStabilityChange(), 7);
        Assert.assertEquals(vents[0].getLowerBoundStart(), 0);
        Assert.assertEquals(vents[0].getLowerBoundEnd(), 0);
        Assert.assertEquals(vents[0].getUpperBoundStart(), 100);
        Assert.assertEquals(vents[0].getUpperBoundEnd(), 100);
    }

    public void calcSingleVentValueInvalidTest() {
        //Max calc test
        StatusState state = new StatusState();
        state.updateVentStatus(new int[]{u, 50, 50}, 7);
        state.clearAllRanges();
        Assert.assertFalse(state.calcPredictedVentValues(24));
        final VentStatus[] vents = state.getVents();
        Assert.assertEquals(vents[0].getLowerBoundStart(), u);
        Assert.assertEquals(vents[0].getLowerBoundEnd(), u);
        Assert.assertEquals(vents[0].getUpperBoundStart(), u);
        Assert.assertEquals(vents[0].getUpperBoundEnd(), u);

        //Min calc test
        state.updateVentStatus(new int[]{u, 0, 0}, 7);
        state.clearAllRanges();
        Assert.assertFalse(state.calcPredictedVentValues(-27));
        Assert.assertEquals(vents[0].getLowerBoundStart(), u);
        Assert.assertEquals(vents[0].getLowerBoundEnd(), u);
        Assert.assertEquals(vents[0].getUpperBoundStart(), u);
        Assert.assertEquals(vents[0].getUpperBoundEnd(), u);
    }

    public void calcDoubleVentValueTest() {
        //Max calc test
        StatusState state = new StatusState();
        state.updateVentStatus(new int[]{u, 50, u}, 7);
        final VentStatus[] vents = state.getVents();
        Assert.assertTrue(state.calcPredictedVentValues(23));
        Assert.assertEquals(state.getStabilityChange(), 23);
        Assert.assertEquals(vents[0].getLowerBoundStart(), 47);
        Assert.assertEquals(vents[0].getLowerBoundEnd(), 53);
        Assert.assertEquals(vents[0].getUpperBoundStart(), 47);
        Assert.assertEquals(vents[0].getUpperBoundEnd(), 53);
        Assert.assertEquals(vents[2].getLowerBoundStart(), 47);
        Assert.assertEquals(vents[2].getLowerBoundEnd(), 53);
        Assert.assertEquals(vents[2].getUpperBoundStart(), 47);
        Assert.assertEquals(vents[2].getUpperBoundEnd(), 53);

        //Max calc test - increased range size
        state.updateVentStatus(new int[]{u, 50, u}, 7);
        final VentStatus[] vents2 = state.getVents();
        Assert.assertTrue(state.calcPredictedVentValues(22));
        Assert.assertEquals(state.getStabilityChange(), 22);
        Assert.assertEquals(vents2[0].getLowerBoundStart(), 44);
        Assert.assertEquals(vents2[0].getLowerBoundEnd(), 56);
        Assert.assertEquals(vents2[0].getUpperBoundStart(), 44);
        Assert.assertEquals(vents2[0].getUpperBoundEnd(), 56);
        Assert.assertEquals(vents2[2].getLowerBoundStart(), 44);
        Assert.assertEquals(vents2[2].getLowerBoundEnd(), 56);
        Assert.assertEquals(vents2[2].getUpperBoundStart(), 44);
        Assert.assertEquals(vents2[2].getUpperBoundEnd(), 56);

        //Double range test
        state.updateVentStatus(new int[]{u, 50, u}, 7);
        final VentStatus[] vents3 = state.getVents();
        Assert.assertTrue(state.calcPredictedVentValues(6));
        Assert.assertEquals(state.getStabilityChange(), 6);
        Assert.assertEquals(vents3[0].getLowerBoundStart(), 0);
        Assert.assertEquals(vents3[0].getLowerBoundEnd(), 46);
        Assert.assertEquals(vents3[0].getUpperBoundStart(), 54);
        Assert.assertEquals(vents3[0].getUpperBoundEnd(), 100);
        Assert.assertEquals(vents3[2].getLowerBoundStart(), 0);
        Assert.assertEquals(vents3[2].getLowerBoundEnd(), 46);
        Assert.assertEquals(vents3[2].getUpperBoundStart(), 54);
        Assert.assertEquals(vents3[2].getUpperBoundEnd(), 100);

        //Min calc test
        state.updateVentStatus(new int[]{u, 50, u}, 7);
        final VentStatus[] vents4 = state.getVents();
        Assert.assertTrue(state.calcPredictedVentValues(-9));
        Assert.assertEquals(state.getStabilityChange(), -9);
        Assert.assertEquals(vents4[0].getLowerBoundStart(), 0);
        Assert.assertEquals(vents4[0].getLowerBoundEnd(), 0);
        Assert.assertEquals(vents4[0].getUpperBoundStart(), 100);
        Assert.assertEquals(vents4[0].getUpperBoundEnd(), 100);
        Assert.assertEquals(vents4[2].getLowerBoundStart(), 0);
        Assert.assertEquals(vents4[2].getLowerBoundEnd(), 0);
        Assert.assertEquals(vents4[2].getUpperBoundStart(), 100);
        Assert.assertEquals(vents4[2].getUpperBoundEnd(), 100);
    }

    public void calcDoubleVentValueInvalidTest() {
        //Max calc test
        StatusState state = new StatusState();
        state.updateVentStatus(new int[]{u, 50, u}, 7);
        final VentStatus[] vents = state.getVents();
        state.clearAllRanges();
        Assert.assertFalse(state.calcPredictedVentValues(24));
        Assert.assertEquals(vents[0].getLowerBoundStart(), u);
        Assert.assertEquals(vents[0].getLowerBoundEnd(), u);
        Assert.assertEquals(vents[0].getUpperBoundStart(), u);
        Assert.assertEquals(vents[0].getUpperBoundEnd(), u);
        Assert.assertEquals(vents[2].getLowerBoundStart(), u);
        Assert.assertEquals(vents[2].getLowerBoundEnd(), u);
        Assert.assertEquals(vents[2].getUpperBoundStart(), u);
        Assert.assertEquals(vents[2].getUpperBoundEnd(), u);

        //Min calc test
        state.updateVentStatus(new int[]{u, 0, u}, 7);
        final VentStatus[] vents2 = state.getVents();
        state.clearAllRanges();
        Assert.assertFalse(state.calcPredictedVentValues(-27));
        Assert.assertEquals(vents2[0].getLowerBoundStart(), u);
        Assert.assertEquals(vents2[0].getLowerBoundEnd(), u);
        Assert.assertEquals(vents2[0].getUpperBoundStart(), u);
        Assert.assertEquals(vents2[0].getUpperBoundEnd(), u);
        Assert.assertEquals(vents2[2].getLowerBoundStart(), u);
        Assert.assertEquals(vents2[2].getLowerBoundEnd(), u);
        Assert.assertEquals(vents2[2].getUpperBoundStart(), u);
        Assert.assertEquals(vents2[2].getUpperBoundEnd(), u);
    }

    public void mergePredictedRangesWithTest() {
        StatusState state = new StatusState();
        StatusState toMerge = new StatusState();
        toMerge.updateVentStatus(new int[]{50, 50, u}, 7);
        toMerge.getVents()[2].clearRanges();
        toMerge.getVents()[2].setLowerBoundRange(41, 59);
        toMerge.getVents()[2].setUpperBoundRange(41, 59);

        //When we have no range defined to take the other states range
        state.updateVentStatus(new int[]{50, 50, u}, 7);
        state.getVents()[2].clearRanges();
        state.mergePredictedRangesWith(toMerge);
        Assert.assertEquals(state.getVents()[2].getLowerBoundStart(), 41);
        Assert.assertEquals(state.getVents()[2].getLowerBoundEnd(), 59);
        Assert.assertEquals(state.getVents()[2].getUpperBoundStart(), 41);
        Assert.assertEquals(state.getVents()[2].getUpperBoundEnd(), 59);

        //Ensure ranges merge correctly
        state.updateVentStatus(new int[]{50, 50, u}, 7);
        state.getVents()[2].clearRanges();
        state.getVents()[2].setLowerBoundRange(30, 40);
        state.getVents()[2].setUpperBoundRange(60, 70);
        state.mergePredictedRangesWith(toMerge);
        Assert.assertEquals(state.getVents()[2].getLowerBoundStart(), 30);
        Assert.assertEquals(state.getVents()[2].getLowerBoundEnd(), 70);
        Assert.assertEquals(state.getVents()[2].getUpperBoundStart(), 30);
        Assert.assertEquals(state.getVents()[2].getUpperBoundEnd(), 70);
    }

    public void mergePredictedRangesWithInvalidTest() {
        StatusState state = new StatusState();
        StatusState toMerge = new StatusState();
        toMerge.updateVentStatus(new int[]{50, 50, u}, 7);
        toMerge.getVents()[2].setLowerBoundRange(41, 59);
        toMerge.getVents()[2].setUpperBoundRange(41, 59);

        //All vents identified nothing to merge
        state.updateVentStatus(new int[]{50, 50, 50}, 7);
        state.mergePredictedRangesWith(toMerge);
        Assert.assertEquals(state.getVents()[2].getLowerBoundStart(), 50);
        Assert.assertEquals(state.getVents()[2].getLowerBoundEnd(), 50);
        Assert.assertEquals(state.getVents()[2].getUpperBoundStart(), 50);
        Assert.assertEquals(state.getVents()[2].getUpperBoundEnd(), 50);

        //Undefined ranges to merge means nothing to merge
        state.updateVentStatus(new int[]{50, 50, u}, 7);
        state.getVents()[2].setLowerBoundRange(47, 53);
        state.getVents()[2].setUpperBoundRange(47, 53);
        toMerge.getVents()[2].clearRanges();
        state.mergePredictedRangesWith(toMerge);
        Assert.assertEquals(state.getVents()[2].getLowerBoundStart(), 47);
        Assert.assertEquals(state.getVents()[2].getLowerBoundEnd(), 53);
        Assert.assertEquals(state.getVents()[2].getUpperBoundStart(), 47);
        Assert.assertEquals(state.getVents()[2].getUpperBoundEnd(), 53);
    }

    public void setOverlappingRangesWithTest() {
        StatusState state = new StatusState();
        final VentStatus vent = state.getVents()[2];
        StatusState toOverlap = new StatusState();
        toOverlap.updateVentStatus(new int[]{50, 50, u}, 7);
        toOverlap.getVents()[2].clearRanges();
        toOverlap.getVents()[2].setLowerBoundRange(41, 46);
        toOverlap.getVents()[2].setUpperBoundRange(54, 59);

        //Both ranges overlap
        vent.clearRanges();
        vent.setLowerBoundRange(38, 44);
        vent.setUpperBoundRange(56, 62);
        state.setOverlappingRangesWith(toOverlap);
        Assert.assertEquals(vent.getLowerBoundStart(), 41);
        Assert.assertEquals(vent.getLowerBoundEnd(), 44);
        Assert.assertEquals(vent.getUpperBoundStart(), 56);
        Assert.assertEquals(vent.getUpperBoundEnd(), 59);

        //Only lower bound overlaps
        vent.setLowerBoundRange(38, 44);
        vent.setUpperBoundRange(62, 65);
        state.setOverlappingRangesWith(toOverlap);
        Assert.assertEquals(vent.getLowerBoundStart(), 41);
        Assert.assertEquals(vent.getLowerBoundEnd(), 44);
        Assert.assertEquals(vent.getUpperBoundStart(), 41);
        Assert.assertEquals(vent.getUpperBoundEnd(), 44);

        //Only upper bound overlaps
        vent.setLowerBoundRange(35, 38);
        vent.setUpperBoundRange(56, 62);
        state.setOverlappingRangesWith(toOverlap);
        Assert.assertEquals(vent.getLowerBoundStart(), 56);
        Assert.assertEquals(vent.getLowerBoundEnd(), 59);
        Assert.assertEquals(vent.getUpperBoundStart(), 56);
        Assert.assertEquals(vent.getUpperBoundEnd(), 59);

        //Neither range overlap
        vent.setLowerBoundRange(35, 38);
        vent.setUpperBoundRange(62, 65);
        state.setOverlappingRangesWith(toOverlap);
        Assert.assertEquals(vent.getLowerBoundStart(), u);
        Assert.assertEquals(vent.getLowerBoundEnd(), u);
        Assert.assertEquals(vent.getUpperBoundStart(), u);
        Assert.assertEquals(vent.getUpperBoundEnd(), u);

    }

    public void setOverlappingRangesWithInvalidTest() {
        StatusState state = new StatusState();
        StatusState toOverlap = new StatusState();
        toOverlap.updateVentStatus(new int[]{50, 50, u}, 7);
        toOverlap.getVents()[2].setLowerBoundRange(41, 59);
        toOverlap.getVents()[2].setUpperBoundRange(41, 59);

        //All vents identified no overlapping should be done
        state.updateVentStatus(new int[]{50, 50, 50}, 7);
        state.setOverlappingRangesWith(toOverlap);
        Assert.assertEquals(state.getVents()[2].getLowerBoundStart(), 50);
        Assert.assertEquals(state.getVents()[2].getLowerBoundEnd(), 50);
        Assert.assertEquals(state.getVents()[2].getUpperBoundStart(), 50);
        Assert.assertEquals(state.getVents()[2].getUpperBoundEnd(), 50);

        //When we have no range defined to take the other states range
        state.updateVentStatus(new int[]{50, 50, u}, 7);
        state.setOverlappingRangesWith(toOverlap);
        Assert.assertEquals(state.getVents()[2].getLowerBoundStart(), u);
        Assert.assertEquals(state.getVents()[2].getLowerBoundEnd(), u);
        Assert.assertEquals(state.getVents()[2].getUpperBoundStart(), u);
        Assert.assertEquals(state.getVents()[2].getUpperBoundEnd(), u);

        //Undefined ranges to merge means our range gets cleared
        state.updateVentStatus(new int[]{50, 50, u}, 7);
        state.getVents()[2].setLowerBoundRange(47, 53);
        state.getVents()[2].setUpperBoundRange(47, 53);
        toOverlap.getVents()[2].clearRanges();
        state.setOverlappingRangesWith(toOverlap);
        Assert.assertEquals(state.getVents()[2].getLowerBoundStart(), u);
        Assert.assertEquals(state.getVents()[2].getLowerBoundEnd(), u);
        Assert.assertEquals(state.getVents()[2].getUpperBoundStart(), u);
        Assert.assertEquals(state.getVents()[2].getUpperBoundEnd(), u);
    }

    public void setOverlappingRangesWithRangeMismatchTest() {
        StatusState state = new StatusState();
        final VentStatus vent = state.getVents()[2];
        StatusState toOverlap = new StatusState();
        toOverlap.updateVentStatus(new int[]{46, 0, u}, 7);
        toOverlap.getVents()[2].setLowerBoundRange(22, 28);
        toOverlap.getVents()[2].setUpperBoundRange(72, 78);

        //Lower upper range overlap
        vent.setLowerBoundRange(59, 75);
        vent.setUpperBoundRange(91, 95);
        state.setOverlappingRangesWith(toOverlap);
        Assert.assertEquals(vent.getLowerBoundStart(), 72);
        Assert.assertEquals(vent.getLowerBoundEnd(), 75);
        Assert.assertEquals(vent.getUpperBoundStart(), 72);
        Assert.assertEquals(vent.getUpperBoundEnd(), 75);

        //Lower both range overlap
        toOverlap.getVents()[2].clearRanges();
        toOverlap.getVents()[2].setLowerBoundRange(72, 78);
        toOverlap.getVents()[2].setUpperBoundRange(72, 78);
        state.setOverlappingRangesWith(toOverlap);
        Assert.assertEquals(vent.getLowerBoundStart(), 72);
        Assert.assertEquals(vent.getLowerBoundEnd(), 75);
        Assert.assertEquals(vent.getUpperBoundStart(), 72);
        Assert.assertEquals(vent.getUpperBoundEnd(), 75);

        //Upper lower range overlap
        toOverlap.getVents()[2].clearRanges();
        toOverlap.getVents()[2].setLowerBoundRange(52, 58);
        toOverlap.getVents()[2].setUpperBoundRange(100, 100);
        vent.clearRanges();
        vent.setLowerBoundRange(9, 25);
        vent.setUpperBoundRange(51, 55);
        state.setOverlappingRangesWith(toOverlap);
        Assert.assertEquals(vent.getLowerBoundStart(), 52);
        Assert.assertEquals(vent.getLowerBoundEnd(), 55);
        Assert.assertEquals(vent.getUpperBoundStart(), 52);
        Assert.assertEquals(vent.getUpperBoundEnd(), 55);

        //Upper both range overlap
        toOverlap.getVents()[2].clearRanges();
        toOverlap.getVents()[2].setLowerBoundRange(52, 58);
        toOverlap.getVents()[2].setUpperBoundRange(52, 58);
        state.setOverlappingRangesWith(toOverlap);
        Assert.assertEquals(vent.getLowerBoundStart(), 52);
        Assert.assertEquals(vent.getLowerBoundEnd(), 55);
        Assert.assertEquals(vent.getUpperBoundStart(), 52);
        Assert.assertEquals(vent.getUpperBoundEnd(), 55);
    }

    public void setOverlappingRangesLowerUpperBothMatchTest() {
        StatusState state = new StatusState();
        final VentStatus vent = state.getVents()[2];
        StatusState toOverlap = new StatusState();
        toOverlap.updateVentStatus(new int[]{50, 50, u}, 7);
        toOverlap.getVents()[2].clearRanges();
        toOverlap.getVents()[2].setLowerBoundRange(0, 46);
        toOverlap.getVents()[2].setUpperBoundRange(54, 100);

        //Upper range values match both lower and upper overlap
        vent.clearRanges();
        vent.setLowerBoundRange(0, 9);
        vent.setUpperBoundRange(46, 59);
        state.setOverlappingRangesWith(toOverlap);
        Assert.assertEquals(vent.getLowerBoundStart(), 0);
        Assert.assertEquals(vent.getLowerBoundEnd(), 9);
        Assert.assertEquals(vent.getUpperBoundStart(), 46);
        Assert.assertEquals(vent.getUpperBoundEnd(), 59);

        //Lower range values match both lower and upper overlap
        vent.clearRanges();
        vent.setLowerBoundRange(41, 54);
        vent.setUpperBoundRange(91, 100);
        state.setOverlappingRangesWith(toOverlap);
        Assert.assertEquals(vent.getLowerBoundStart(), 41);
        Assert.assertEquals(vent.getLowerBoundEnd(), 54);
        Assert.assertEquals(vent.getUpperBoundStart(), 91);
        Assert.assertEquals(vent.getUpperBoundEnd(), 100);

        //Both range values match both lower and upper overlap
        //Our vent has a huge single range
        vent.clearRanges();
        vent.setLowerBoundRange(41, 54);
        vent.setUpperBoundRange(46, 59);
        state.setOverlappingRangesWith(toOverlap);
        Assert.assertEquals(vent.getLowerBoundStart(), 41);
        Assert.assertEquals(vent.getLowerBoundEnd(), 46);
        Assert.assertEquals(vent.getUpperBoundStart(), 54);
        Assert.assertEquals(vent.getUpperBoundEnd(), 59);

        //Other vent has a huge single range
        toOverlap.getVents()[2].setLowerBoundRange(0, 54);
        toOverlap.getVents()[2].setUpperBoundRange(46, 100);
        state.setOverlappingRangesWith(toOverlap);
        Assert.assertEquals(vent.getLowerBoundStart(), 41);
        Assert.assertEquals(vent.getLowerBoundEnd(), 46);
        Assert.assertEquals(vent.getUpperBoundStart(), 54);
        Assert.assertEquals(vent.getUpperBoundEnd(), 59);
    }

    public void doFreezeClippingBTest() {
        StatusState state = new StatusState();
        final VentStatus vent = state.getVents()[0];
        state.updateVentStatus(new int[]{u, 50, 50}, 0);

        //B is frozen with 0 move
        vent.clearRanges();
        vent.setLowerBoundRange(30, 33);
        vent.setUpperBoundRange(42, 44);
        Assert.assertFalse(state.doFreezeClipping(makeMoveBitState(1, 0, 0)));
        Assert.assertEquals(vent.getLowerBoundStart(), 42);
        Assert.assertEquals(vent.getLowerBoundEnd(), 44);
        Assert.assertEquals(vent.getUpperBoundStart(), 42);
        Assert.assertEquals(vent.getUpperBoundEnd(), 44);

        //B is 41-59 with 1 move
        vent.clearRanges();
        vent.setLowerBoundRange(30, 33);
        vent.setUpperBoundRange(42, 44);
        Assert.assertFalse(state.doFreezeClipping(makeMoveBitState(2, 1, 0)));
        Assert.assertEquals(vent.getLowerBoundStart(), 30);
        Assert.assertEquals(vent.getLowerBoundEnd(), 33);
        Assert.assertEquals(vent.getUpperBoundStart(), 30);
        Assert.assertEquals(vent.getUpperBoundEnd(), 33);

        //B is outside 41-59 with 1 move
        state.updateVentStatus(new int[]{u, 60, 50}, 0);
        vent.clearRanges();
        vent.setLowerBoundRange(30, 33);
        vent.setUpperBoundRange(42, 44);
        Assert.assertFalse(state.doFreezeClipping(makeMoveBitState(2, 1, 0)));
        Assert.assertEquals(vent.getLowerBoundStart(), 42);
        Assert.assertEquals(vent.getLowerBoundEnd(), 44);
        Assert.assertEquals(vent.getUpperBoundStart(), 42);
        Assert.assertEquals(vent.getUpperBoundEnd(), 44);

        //B is bounded with 1 move edge case - dont clip anything
        state.updateVentStatus(new int[]{u, 1, 50}, 0);
        vent.clearRanges();
        vent.setLowerBoundRange(30, 33);
        vent.setUpperBoundRange(42, 44);
        Assert.assertFalse(state.doFreezeClipping(makeMoveBitState(2, 1, 0)));
        Assert.assertEquals(vent.getLowerBoundStart(), 30);
        Assert.assertEquals(vent.getLowerBoundEnd(), 33);
        Assert.assertEquals(vent.getUpperBoundStart(), 42);
        Assert.assertEquals(vent.getUpperBoundEnd(), 44);

        //B is outside 41-59 with 2 move
        vent.clearRanges();
        vent.setLowerBoundRange(30, 33);
        vent.setUpperBoundRange(42, 44);
        Assert.assertFalse(state.doFreezeClipping(makeMoveBitState(2, 2, 0)));
        Assert.assertEquals(vent.getLowerBoundStart(), 30);
        Assert.assertEquals(vent.getLowerBoundEnd(), 33);
        Assert.assertEquals(vent.getUpperBoundStart(), 30);
        Assert.assertEquals(vent.getUpperBoundEnd(), 33);

        //B is bounded with 0 move - don't clip anything
        state.updateVentStatus(new int[]{u, 0, 50}, 0);
        vent.clearRanges();
        vent.setLowerBoundRange(30, 33);
        vent.setUpperBoundRange(42, 44);
        Assert.assertFalse(state.doFreezeClipping(makeMoveBitState(2, 0, 0)));
        Assert.assertEquals(vent.getLowerBoundStart(), 30);
        Assert.assertEquals(vent.getLowerBoundEnd(), 33);
        Assert.assertEquals(vent.getUpperBoundStart(), 42);
        Assert.assertEquals(vent.getUpperBoundEnd(), 44);
    }

    public void doFreezeClippingBClippedATest() {
        StatusState state = new StatusState();
        final VentStatus vent = state.getVents()[0];
        state.updateVentStatus(new int[]{u, 50, 50}, 0);

        //B is outside 41-59 with 2 move - ensure A gets clipped
        vent.clearRanges();
        vent.setLowerBoundRange(42, 44);
        vent.setUpperBoundRange(42, 44);
        Assert.assertTrue(state.doFreezeClipping(makeMoveBitState(0, 2, 0)));
        Assert.assertEquals(vent.getLowerBoundStart(), u);
        Assert.assertEquals(vent.getLowerBoundEnd(), u);
        Assert.assertEquals(vent.getUpperBoundStart(), u);
        Assert.assertEquals(vent.getUpperBoundEnd(), u);
    }

    public void doFreezeClippingCTest() {
        StatusState state = new StatusState();
        final VentStatus ventA = state.getVents()[0];
        final VentStatus ventB = state.getVents()[1];

        //0 move tests
        //C is outside 41-59 with 0 move - both A B 41-59
        state.updateVentStatus(new int[]{u, u, 60}, 0);
        ventA.clearRanges();
        ventA.setLowerBoundRange(30, 33);
        ventA.setUpperBoundRange(42, 44);
        ventB.clearRanges();
        ventB.setLowerBoundRange(30, 33);
        ventB.setUpperBoundRange(42, 44);
        state.doFreezeClipping(makeMoveBitState(1, 0, 0));
        Assert.assertEquals(ventA.getLowerBoundStart(), 42);
        Assert.assertEquals(ventA.getLowerBoundEnd(), 44);
        Assert.assertEquals(ventA.getUpperBoundStart(), 42);
        Assert.assertEquals(ventA.getUpperBoundEnd(), 44);
        Assert.assertEquals(ventB.getLowerBoundStart(), 42);
        Assert.assertEquals(ventB.getLowerBoundEnd(), 44);
        Assert.assertEquals(ventB.getUpperBoundStart(), 42);
        Assert.assertEquals(ventB.getUpperBoundEnd(), 44);

        //C is 41-59 with 0 move - do nothing
        state.updateVentStatus(new int[]{u, u, 50}, 0);
        ventA.clearRanges();
        ventA.setLowerBoundRange(30, 33);
        ventA.setUpperBoundRange(42, 44);
        ventB.clearRanges();
        ventB.setLowerBoundRange(30, 33);
        ventB.setUpperBoundRange(42, 44);
        state.doFreezeClipping(makeMoveBitState(1, 0, 0));
        Assert.assertEquals(ventA.getLowerBoundStart(), 30);
        Assert.assertEquals(ventA.getLowerBoundEnd(), 33);
        Assert.assertEquals(ventA.getUpperBoundStart(), 42);
        Assert.assertEquals(ventA.getUpperBoundEnd(), 44);
        Assert.assertEquals(ventB.getLowerBoundStart(), 30);
        Assert.assertEquals(ventB.getLowerBoundEnd(), 33);
        Assert.assertEquals(ventB.getUpperBoundStart(), 42);
        Assert.assertEquals(ventB.getUpperBoundEnd(), 44);

        //C is bounded with 0 move - do nothing
        state.updateVentStatus(new int[]{u, u, 0}, 0);
        ventA.clearRanges();
        ventA.setLowerBoundRange(30, 33);
        ventA.setUpperBoundRange(42, 44);
        ventB.clearRanges();
        ventB.setLowerBoundRange(30, 33);
        ventB.setUpperBoundRange(42, 44);
        state.doFreezeClipping(makeMoveBitState(1, 0, 0));
        Assert.assertEquals(ventA.getLowerBoundStart(), 30);
        Assert.assertEquals(ventA.getLowerBoundEnd(), 33);
        Assert.assertEquals(ventA.getUpperBoundStart(), 42);
        Assert.assertEquals(ventA.getUpperBoundEnd(), 44);
        Assert.assertEquals(ventB.getLowerBoundStart(), 30);
        Assert.assertEquals(ventB.getLowerBoundEnd(), 33);
        Assert.assertEquals(ventB.getUpperBoundStart(), 42);
        Assert.assertEquals(ventB.getUpperBoundEnd(), 44);


        //1 move tests
        //C is outside 41-59 with 1 move - either A or B 41-59
        state.updateVentStatus(new int[]{u, u, 60}, 0);
        ventA.clearRanges();
        ventA.setLowerBoundRange(30, 33);
        ventA.setUpperBoundRange(42, 44);
        ventB.clearRanges();
        ventB.setLowerBoundRange(30, 33);
        ventB.setUpperBoundRange(42, 44);
        state.doFreezeClipping(makeMoveBitState(1, 1, 1));
        Assert.assertEquals(ventA.getLowerBoundStart(), 30);
        Assert.assertEquals(ventA.getLowerBoundEnd(), 33);
        Assert.assertEquals(ventA.getUpperBoundStart(), 42);
        Assert.assertEquals(ventA.getUpperBoundEnd(), 44);
        Assert.assertEquals(ventB.getLowerBoundStart(), 30);
        Assert.assertEquals(ventB.getLowerBoundEnd(), 33);
        Assert.assertEquals(ventB.getUpperBoundStart(), 42);
        Assert.assertEquals(ventB.getUpperBoundEnd(), 44);

        //C is 41-59 with 1 move - both A B outside 41-59
        state.updateVentStatus(new int[]{u, u, 50}, 0);
        ventA.clearRanges();
        ventA.setLowerBoundRange(30, 33);
        ventA.setUpperBoundRange(42, 44);
        ventB.clearRanges();
        ventB.setLowerBoundRange(30, 33);
        ventB.setUpperBoundRange(42, 44);
        state.doFreezeClipping(makeMoveBitState(2, 2, 1));
        Assert.assertEquals(ventA.getLowerBoundStart(), 30);
        Assert.assertEquals(ventA.getLowerBoundEnd(), 33);
        Assert.assertEquals(ventA.getUpperBoundStart(), 30);
        Assert.assertEquals(ventA.getUpperBoundEnd(), 33);
        Assert.assertEquals(ventB.getLowerBoundStart(), 30);
        Assert.assertEquals(ventB.getLowerBoundEnd(), 33);
        Assert.assertEquals(ventB.getUpperBoundStart(), 30);
        Assert.assertEquals(ventB.getUpperBoundEnd(), 33);


        //2 move tests
        //C is outside 41-59 with 2 move - both A B outside 41-59
        state.updateVentStatus(new int[]{u, u, 60}, 0);
        ventA.clearRanges();
        ventA.setLowerBoundRange(30, 33);
        ventA.setUpperBoundRange(42, 44);
        ventB.clearRanges();
        ventB.setLowerBoundRange(30, 33);
        ventB.setUpperBoundRange(42, 44);
        state.doFreezeClipping(makeMoveBitState(2, 2, 2));
        Assert.assertEquals(ventA.getLowerBoundStart(), 30);
        Assert.assertEquals(ventA.getLowerBoundEnd(), 33);
        Assert.assertEquals(ventA.getUpperBoundStart(), 30);
        Assert.assertEquals(ventA.getUpperBoundEnd(), 33);
        Assert.assertEquals(ventB.getLowerBoundStart(), 30);
        Assert.assertEquals(ventB.getLowerBoundEnd(), 33);
        Assert.assertEquals(ventB.getUpperBoundStart(), 30);
        Assert.assertEquals(ventB.getUpperBoundEnd(), 33);
    }

    public void clipPredictedStabilityMismatchInvalidTest() {
        StatusState state = new StatusState();
        state.updateVentStatus(new int[]{24, 76, u}, 6);
        final VentStatus ventC = state.getVents()[2];

        //Single value range do nothing
        ventC.setUpperBoundRange(79, 79);
        ventC.setLowerBoundRange(79, 79);
        state.clipPredictedStabilityMismatch(StabilityUpdateInfo.getMinRNGVariation()-1);
        Assert.assertEquals(ventC.getLowerBoundStart(), 79);
        Assert.assertEquals(ventC.getLowerBoundEnd(), 79);
        Assert.assertEquals(ventC.getUpperBoundStart(), 79);
        Assert.assertEquals(ventC.getUpperBoundEnd(), 79);

        //Both changes are above stability amount do nothing
        ventC.setUpperBoundRange(53, 53);
        ventC.setLowerBoundRange(47, 47);
        state.clipPredictedStabilityMismatch(StabilityUpdateInfo.getMinRNGVariation()-1);
        Assert.assertEquals(ventC.getLowerBoundStart(), 47);
        Assert.assertEquals(ventC.getLowerBoundEnd(), 47);
        Assert.assertEquals(ventC.getUpperBoundStart(), 53);
        Assert.assertEquals(ventC.getUpperBoundEnd(), 53);

        //Both changes are negative but match no clipping
        ventC.setUpperBoundRange(79, 79);
        ventC.setLowerBoundRange(21, 21);
        state.clipPredictedStabilityMismatch(StabilityUpdateInfo.getMinRNGVariation()-1);
        Assert.assertEquals(ventC.getLowerBoundStart(), 21);
        Assert.assertEquals(ventC.getLowerBoundEnd(), 21);
        Assert.assertEquals(ventC.getUpperBoundStart(), 79);
        Assert.assertEquals(ventC.getUpperBoundEnd(), 79);
    }

    public void clipPredictedStabilityMismatchTest() {
        StatusState state = new StatusState();
        state.updateVentStatus(new int[]{24, 76, u}, 6);
        final VentStatus ventC = state.getVents()[2];

        //Upper bound partial clipping
        ventC.setUpperBoundRange(77, 79);
        ventC.setLowerBoundRange(47, 49);
        state.clipPredictedStabilityMismatch(StabilityUpdateInfo.getMinRNGVariation()-1);
        Assert.assertEquals(ventC.getLowerBoundStart(), 47);
        Assert.assertEquals(ventC.getLowerBoundEnd(), 49);
        Assert.assertEquals(ventC.getUpperBoundStart(), 77);
        Assert.assertEquals(ventC.getUpperBoundEnd(), 78);

        //Upper bound full clipping
        ventC.setUpperBoundRange(79, 79);
        ventC.setLowerBoundRange(47, 49);
        state.clipPredictedStabilityMismatch(StabilityUpdateInfo.getMinRNGVariation()-1);
        Assert.assertEquals(ventC.getLowerBoundStart(), 47);
        Assert.assertEquals(ventC.getLowerBoundEnd(), 49);
        Assert.assertEquals(ventC.getUpperBoundStart(), 47);
        Assert.assertEquals(ventC.getUpperBoundEnd(), 49);

        //Lower bound partial clipping
        ventC.setUpperBoundRange(51, 53);
        ventC.setLowerBoundRange(21, 23);
        state.clipPredictedStabilityMismatch(StabilityUpdateInfo.getMinRNGVariation()-1);
        Assert.assertEquals(ventC.getLowerBoundStart(), 22);
        Assert.assertEquals(ventC.getLowerBoundEnd(), 23);
        Assert.assertEquals(ventC.getUpperBoundStart(), 51);
        Assert.assertEquals(ventC.getUpperBoundEnd(), 53);

        //Lower bound full clipping
        ventC.setUpperBoundRange(51, 53);
        ventC.setLowerBoundRange(21, 21);
        state.clipPredictedStabilityMismatch(StabilityUpdateInfo.getMinRNGVariation()-1);
        Assert.assertEquals(ventC.getLowerBoundStart(), 51);
        Assert.assertEquals(ventC.getLowerBoundEnd(), 53);
        Assert.assertEquals(ventC.getUpperBoundStart(), 51);
        Assert.assertEquals(ventC.getUpperBoundEnd(), 53);
    }

    public void doHalfSpaceClippingChangeTest() {
        StatusState state = new StatusState();
        final VentStatus[] vents = state.getVents();
        vents[0].setLowerBoundRange(0, 100);
        vents[0].setUpperBoundRange(0, 100);
        vents[1].setLowerBoundRange(0, 100);
        vents[1].setUpperBoundRange(0, 100);
        vents[2].setLowerBoundRange(0, 100);
        vents[2].setUpperBoundRange(0, 100);

        //A Clipping test - moving upward
        vents[0].update(u, 1);
        //Only clip A - negative point contribution
        state.doHalfSpaceClipping(1, 1);
        Assert.assertEquals(vents[0].getLowerBoundStart(), 47);
        Assert.assertEquals(vents[0].getLowerBoundEnd(), 100);
        Assert.assertEquals(vents[0].getUpperBoundStart(), 47);
        Assert.assertEquals(vents[0].getUpperBoundEnd(), 100);
        Assert.assertEquals(vents[1].getLowerBoundStart(), 0);
        Assert.assertEquals(vents[1].getLowerBoundEnd(), 100);
        Assert.assertEquals(vents[1].getUpperBoundStart(), 0);
        Assert.assertEquals(vents[1].getUpperBoundEnd(), 100);
        Assert.assertEquals(vents[2].getLowerBoundStart(), 0);
        Assert.assertEquals(vents[2].getLowerBoundEnd(), 100);
        Assert.assertEquals(vents[2].getUpperBoundStart(), 0);
        Assert.assertEquals(vents[2].getUpperBoundEnd(), 100);

        //Only clip A - positive point contribution
        vents[0].setLowerBoundRange(0, 100);
        vents[0].setUpperBoundRange(0, 100);
        state.doHalfSpaceClipping(1, 0);
        Assert.assertEquals(vents[0].getLowerBoundStart(), 0);
        Assert.assertEquals(vents[0].getLowerBoundEnd(), 53);
        Assert.assertEquals(vents[0].getUpperBoundStart(), 0);
        Assert.assertEquals(vents[0].getUpperBoundEnd(), 53);
        Assert.assertEquals(vents[1].getLowerBoundStart(), 0);
        Assert.assertEquals(vents[1].getLowerBoundEnd(), 100);
        Assert.assertEquals(vents[1].getUpperBoundStart(), 0);
        Assert.assertEquals(vents[1].getUpperBoundEnd(), 100);
        Assert.assertEquals(vents[2].getLowerBoundStart(), 0);
        Assert.assertEquals(vents[2].getLowerBoundEnd(), 100);
        Assert.assertEquals(vents[2].getUpperBoundStart(), 0);
        Assert.assertEquals(vents[2].getUpperBoundEnd(), 100);

        //A Clipping test - moving downward
        vents[0].update(u, -1);
        vents[0].setLowerBoundRange(0, 100);
        vents[0].setUpperBoundRange(0, 100);
        //Only clip A - negative point contribution
        state.doHalfSpaceClipping(1, 1);
        Assert.assertEquals(vents[0].getLowerBoundStart(), 0);
        Assert.assertEquals(vents[0].getLowerBoundEnd(), 53);
        Assert.assertEquals(vents[0].getUpperBoundStart(), 0);
        Assert.assertEquals(vents[0].getUpperBoundEnd(), 53);
        Assert.assertEquals(vents[1].getLowerBoundStart(), 0);
        Assert.assertEquals(vents[1].getLowerBoundEnd(), 100);
        Assert.assertEquals(vents[1].getUpperBoundStart(), 0);
        Assert.assertEquals(vents[1].getUpperBoundEnd(), 100);
        Assert.assertEquals(vents[2].getLowerBoundStart(), 0);
        Assert.assertEquals(vents[2].getLowerBoundEnd(), 100);
        Assert.assertEquals(vents[2].getUpperBoundStart(), 0);
        Assert.assertEquals(vents[2].getUpperBoundEnd(), 100);

        vents[0].setLowerBoundRange(0, 100);
        vents[0].setUpperBoundRange(0, 100);
        //Only clip A - positive point contribution
        state.doHalfSpaceClipping(1, 0);
        Assert.assertEquals(vents[0].getLowerBoundStart(), 47);
        Assert.assertEquals(vents[0].getLowerBoundEnd(), 100);
        Assert.assertEquals(vents[0].getUpperBoundStart(), 47);
        Assert.assertEquals(vents[0].getUpperBoundEnd(), 100);
        Assert.assertEquals(vents[1].getLowerBoundStart(), 0);
        Assert.assertEquals(vents[1].getLowerBoundEnd(), 100);
        Assert.assertEquals(vents[1].getUpperBoundStart(), 0);
        Assert.assertEquals(vents[1].getUpperBoundEnd(), 100);
        Assert.assertEquals(vents[2].getLowerBoundStart(), 0);
        Assert.assertEquals(vents[2].getLowerBoundEnd(), 100);
        Assert.assertEquals(vents[2].getUpperBoundStart(), 0);
        Assert.assertEquals(vents[2].getUpperBoundEnd(), 100);
    }

    public void reverseMovementKnownBitTest() {
        StatusState state = new StatusState();
        final VentStatus[] vents = state.getVents();
        state.updateVentStatus(new int[]{30, 30, 30}, 0);

        //All vents should be reversed
        state.reverseMovement(0);
        Assert.assertEquals(vents[0].getActualValue(), 32);
        Assert.assertEquals(vents[1].getActualValue(), 32);
        Assert.assertEquals(vents[2].getActualValue(), 32);


        //Two vents should be reversed
        state.updateVentStatus(new int[]{30, 30, 30}, 0);
        state.reverseMovement(1);
        Assert.assertEquals(vents[0].getActualValue(), 30);
        Assert.assertEquals(vents[1].getActualValue(), 32);
        Assert.assertEquals(vents[2].getActualValue(), 32);

        state.updateVentStatus(new int[]{30, 30, 30}, 0);
        state.reverseMovement(2);
        Assert.assertEquals(vents[0].getActualValue(), 32);
        Assert.assertEquals(vents[1].getActualValue(), 30);
        Assert.assertEquals(vents[2].getActualValue(), 32);

        state.updateVentStatus(new int[]{30, 30, 30}, 0);
        state.reverseMovement(4);
        Assert.assertEquals(vents[0].getActualValue(), 32);
        Assert.assertEquals(vents[1].getActualValue(), 32);
        Assert.assertEquals(vents[2].getActualValue(), 30);


        //One vent should be reversed
        state.updateVentStatus(new int[]{30, 30, 30}, 0);
        state.reverseMovement(3);
        Assert.assertEquals(vents[0].getActualValue(), 30);
        Assert.assertEquals(vents[1].getActualValue(), 30);
        Assert.assertEquals(vents[2].getActualValue(), 32);

        state.updateVentStatus(new int[]{30, 30, 30}, 0);
        state.reverseMovement(5);
        Assert.assertEquals(vents[0].getActualValue(), 30);
        Assert.assertEquals(vents[1].getActualValue(), 32);
        Assert.assertEquals(vents[2].getActualValue(), 30);

        state.updateVentStatus(new int[]{30, 30, 30}, 0);
        state.reverseMovement(6);
        Assert.assertEquals(vents[0].getActualValue(), 32);
        Assert.assertEquals(vents[1].getActualValue(), 30);
        Assert.assertEquals(vents[2].getActualValue(), 30);


        //No vents should be reversed
        state.updateVentStatus(new int[]{30, 30, 30}, 0);
        state.reverseMovement(7);
        Assert.assertEquals(vents[0].getActualValue(), 30);
        Assert.assertEquals(vents[1].getActualValue(), 30);
        Assert.assertEquals(vents[2].getActualValue(), 30);
    }

    public void reverseMovementResultTest() {
        StatusState state = new StatusState();

        //A reverse move should fail
        state.updateVentStatus(new int[]{u, u, u}, 0);
        Assert.assertEquals(state.reverseMovement(0), -1);
        state.updateVentStatus(new int[]{u, 70, 70}, 0);
        Assert.assertEquals(state.reverseMovement(0), -1);
        state.updateVentStatus(new int[]{u, 70, u}, 0);
        Assert.assertEquals(state.reverseMovement(0), -1);
        state.updateVentStatus(new int[]{u, u, 70}, 0);
        Assert.assertEquals(state.reverseMovement(0), -1);

        //B reverse move should fail
        state.updateVentStatus(new int[]{70, u, u}, 0);
        Assert.assertEquals(state.reverseMovement(0), -2);
        state.updateVentStatus(new int[]{70, u, 70}, 0);
        Assert.assertEquals(state.reverseMovement(0), -2);

        //C reverse move should fail
        state.updateVentStatus(new int[]{70, 70, u}, 0);
        Assert.assertEquals(state.reverseMovement(0), -3);

        //No reverse move should fail
        state.updateVentStatus(new int[]{70, 70, 70}, 0);
        Assert.assertEquals(state.reverseMovement(0), 0);
    }

    public void trimDoubleVentSeperateRangesTest() {
        StatusState state = new StatusState();
        final VentStatus[] vents = state.getVents();
        state.updateVentStatus(new int[]{54, u, u}, 2);

        //Upper bound range C should be clipped
        vents[1].setLowerBoundRange(8, 20);
        vents[1].setUpperBoundRange(86, 96);
        vents[2].setLowerBoundRange(0, 11);
        vents[2].setUpperBoundRange(80, 83);
        state.trimDoubleVentRanges(-5);
        Assert.assertEquals(vents[2].getLowerBoundStart(), 0);
        Assert.assertEquals(vents[2].getUpperBoundStart(), 0);
        Assert.assertEquals(vents[2].getLowerBoundEnd(), 9);
        Assert.assertEquals(vents[2].getUpperBoundEnd(), 9);

        //Lower bound range C should be clipped
        vents[1].setLowerBoundRange(8, 20);
        vents[1].setUpperBoundRange(86, 96);
        vents[2].setLowerBoundRange(17, 20);
        vents[2].setUpperBoundRange(89, 100);
        state.trimDoubleVentRanges(-5);
        Assert.assertEquals(vents[2].getLowerBoundStart(), 91);
        Assert.assertEquals(vents[2].getUpperBoundStart(), 91);
        Assert.assertEquals(vents[2].getLowerBoundEnd(), 100);
        Assert.assertEquals(vents[2].getUpperBoundEnd(), 100);

        //Both ranges C should be trimmed
        vents[1].setLowerBoundRange(8, 20);
        vents[1].setUpperBoundRange(86, 96);
        vents[2].setLowerBoundRange(0, 11);
        vents[2].setUpperBoundRange(89, 100);
        state.trimDoubleVentRanges(-5);
        Assert.assertEquals(vents[2].getLowerBoundStart(), 0);
        Assert.assertEquals(vents[2].getLowerBoundEnd(), 9);
        Assert.assertEquals(vents[2].getUpperBoundStart(), 91);
        Assert.assertEquals(vents[2].getUpperBoundEnd(), 100);

        //Both ranges C should be clipped
        vents[1].setLowerBoundRange(8, 20);
        vents[1].setUpperBoundRange(86, 96);
        vents[2].setLowerBoundRange(17, 20);
        vents[2].setUpperBoundRange(80, 83);
        state.trimDoubleVentRanges(-5);
        Assert.assertFalse(vents[2].isRangeDefined());
    }

    public void trimDoubleVentSingleRangeTest() {
        StatusState state = new StatusState();
        final VentStatus[] vents = state.getVents();
        state.updateVentStatus(new int[]{54, u, u}, 2);

        //No valid upperbound ranges will be found
        vents[1].setLowerBoundRange(8, 20);
        vents[1].setUpperBoundRange(86, 96);
        vents[2].setLowerBoundRange(0, 31);
        vents[2].setUpperBoundRange(0, 31);
        state.trimDoubleVentRanges(-5);
        Assert.assertEquals(vents[2].getLowerBoundStart(), 0);
        Assert.assertEquals(vents[2].getUpperBoundStart(), 0);
        Assert.assertEquals(vents[2].getLowerBoundEnd(), 9);
        Assert.assertEquals(vents[2].getUpperBoundEnd(), 9);

        //No valid lowerbound ranges will be found
        vents[1].setLowerBoundRange(8, 20);
        vents[1].setUpperBoundRange(86, 96);
        vents[2].setLowerBoundRange(69, 100);
        vents[2].setUpperBoundRange(69, 100);
        state.trimDoubleVentRanges(-5);
        Assert.assertEquals(vents[2].getLowerBoundStart(), 91);
        Assert.assertEquals(vents[2].getUpperBoundStart(), 91);
        Assert.assertEquals(vents[2].getLowerBoundEnd(), 100);
        Assert.assertEquals(vents[2].getUpperBoundEnd(), 100);

        //No valid ranges will be found
        vents[1].setLowerBoundRange(8, 20);
        vents[1].setUpperBoundRange(86, 96);
        vents[2].setLowerBoundRange(47, 53);
        vents[2].setUpperBoundRange(47, 53);
        state.trimDoubleVentRanges(-5);
        Assert.assertFalse(vents[2].isRangeDefined());

        //Valid ranges will be found for both - large range will split
        vents[1].setLowerBoundRange(8, 20);
        vents[1].setUpperBoundRange(86, 96);
        vents[2].setLowerBoundRange(0, 100);
        vents[2].setUpperBoundRange(0, 100);
        state.trimDoubleVentRanges(-5);
        Assert.assertEquals(vents[2].getLowerBoundStart(), 0);
        Assert.assertEquals(vents[2].getUpperBoundStart(), 91);
        Assert.assertEquals(vents[2].getLowerBoundEnd(), 9);
        Assert.assertEquals(vents[2].getUpperBoundEnd(), 100);

        //Edge cases - range merge testing
        vents[1].setLowerBoundRange(8, 20);
        vents[1].setUpperBoundRange(86, 96);
        vents[2].setLowerBoundRange(0, 9);
        vents[2].setUpperBoundRange(0, 9);
        state.trimDoubleVentRanges(-5);
        Assert.assertEquals(vents[2].getLowerBoundStart(), 0);
        Assert.assertEquals(vents[2].getUpperBoundStart(), 0);
        Assert.assertEquals(vents[2].getLowerBoundEnd(), 9);
        Assert.assertEquals(vents[2].getUpperBoundEnd(), 9);

    }

    public float getPercent(int value) {
        float percentValue = 50 - Math.abs(50 - value);
        return percentValue / 50.0f;
    }
    public int calcStabReverse(int A, int B, int C) {
        float weight = 16;
        float valA = (getPercent(A) * weight);
        float valB = (getPercent(B) * weight);
        float valC = (getPercent(C) * weight);
        int infA = (int)Math.ceil(valA);
        int infB = (int)Math.ceil(valB);
        int infC = (int)Math.ceil(valC);
        int total = infA + infB + infC;
        return -25 + total;
    }
    public int calcStabReverseRound(int A, int B, int C) {
        float weight = 49 / 3f;
        float valA = (getPercent(A) * weight);
        float valB = (getPercent(B) * weight);
        float valC = (getPercent(C) * weight);
        int infA = (int)Math.round(valA);
        int infB = (int)Math.round(valB);
        int infC = (int)Math.round(valC);
        int total = infA + infB + infC;
        return -25 + total;
    }
    public void sandbox() {
        int u = VentStatus.STARTING_VENT_VALUE;
        StatusState testState = new StatusState();
        testState.updateVentStatus(new int[]{23, 42, u}, 4);
        testState.calcPredictedVentValues(11);
        //50-53 all gave the same value
        //Vents all have equal weight

        int A = 41, B = 52, C = 58;
        int stab = calcStabReverse(A, B, C);
        int stab2 = calcStabReverseRound(A, B, C);
    }

}
