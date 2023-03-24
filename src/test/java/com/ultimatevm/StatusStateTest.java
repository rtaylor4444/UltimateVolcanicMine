package com.ultimatevm;

import org.testng.annotations.Test;
import org.testng.Assert;

@Test()
public class StatusStateTest {

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
        VentStatus.VentChangeState[] changeStates = state.updateVentStatus(new int[]{50,50,50}, 7);
        for(int i = 0; i < StatusState.NUM_VENTS; ++i) {
            Assert.assertEquals(vents[i].getActualValue(), 50);
            Assert.assertEquals(vents[i].getDirection(), 1);
            Assert.assertEquals(changeStates[i], VentStatus.VentChangeState.IDENTIFIED);
        }
        changeStates = state.updateVentStatus(new int[]{49,49,49}, 0);
        for(int i = 0; i < StatusState.NUM_VENTS; ++i) {
            Assert.assertEquals(vents[i].getActualValue(), 49);
            Assert.assertEquals(vents[i].getDirection(), -1);
            Assert.assertEquals(changeStates[i], VentStatus.VentChangeState.ONE_CHANGE);
        }
    }

    public void updateVentMovementTest() {
        StatusState state = new StatusState();
        final VentStatus[] vents = state.getVents();
        state.updateVentStatus(new int[]{VentStatus.STARTING_VENT_VALUE,
                VentStatus.STARTING_VENT_VALUE,
                VentStatus.STARTING_VENT_VALUE}, 7);
        state.updateVentMovement();
        for(int i = 0; i < StatusState.NUM_VENTS; ++i) {
            Assert.assertEquals(vents[i].getTotalBoundStart(), 27-i);
        }
    }

    public void updateVentMovementAInfluenceTest() {
        StatusState state = new StatusState();
        final VentStatus[] vents = state.getVents();
        int u = VentStatus.STARTING_VENT_VALUE;
        state.updateVentStatus(new int[]{u, 50, 50}, 7);

        //A never freezes
        //Movement by 1 since 41-59% range
        vents[0].setLowerBoundRange(41, 59);
        vents[0].setUpperBoundRange(41, 59);
        state.updateVentMovement();
        Assert.assertEquals(vents[0].getLowerBoundStartMove(), 1);

        //Movement by 2 since outside of 41-59% range
        vents[0].clearMovement();
        vents[0].setLowerBoundRange(0, 40);
        vents[0].setUpperBoundRange(60, 99);
        state.updateVentMovement();
        Assert.assertEquals(vents[0].getLowerBoundStartMove(), 2);
    }

    public void updateVentMovementBInfluenceTest() {
        StatusState state = new StatusState();
        final VentStatus[] vents = state.getVents();
        int u = VentStatus.STARTING_VENT_VALUE;

        //B is only influenced by A
        //Unidentified A influence (assume worst movement)
        state.updateVentStatus(new int[]{u, u, 50}, 7);
        //Movement by 0 since A is unknown and B is 41-59% range
        vents[1].setLowerBoundRange(41, 59);
        vents[1].setUpperBoundRange(41, 59);
        state.updateVentMovement();
        Assert.assertEquals(vents[1].getLowerBoundStartMove(), 0);

        //Movement by 1 since A is unknown and B is outside of 41-59% range
        vents[1].setLowerBoundRange(0, 40);
        vents[1].setUpperBoundRange(60, 99);
        state.updateVentMovement();
        Assert.assertEquals(vents[1].getLowerBoundStartMove(), 1);


        //A 41-59% range influence
        state.clearVentMovement();
        state.updateVentStatus(new int[]{50, u, 50}, 7);
        //Movement by 0 since A and B are 41-59% range
        vents[1].setLowerBoundRange(41, 59);
        vents[1].setUpperBoundRange(41, 59);
        state.updateVentMovement();
        Assert.assertEquals(vents[1].getLowerBoundStartMove(), 0);

        //Movement by 1 since A is 41-59% and B is outside of 41-59% range
        vents[1].setLowerBoundRange(0, 40);
        vents[1].setUpperBoundRange(60, 99);
        state.updateVentMovement();
        Assert.assertEquals(vents[1].getLowerBoundStartMove(), 1);


        //Zero A influence (outside 41-59%)
        state.updateVentStatus(new int[]{0, u, 50}, 7);
        //Movement by 1 since A is not 41-59% but B is 41-59% range
        state.clearVentMovement();
        vents[1].setLowerBoundRange(41, 59);
        vents[1].setUpperBoundRange(41, 59);
        state.updateVentMovement();
        Assert.assertEquals(vents[1].getLowerBoundStartMove(), 1);

        //Movement by 2 since A and B are outside of 41-59% range
        state.clearVentMovement();
        vents[1].setLowerBoundRange(0, 40);
        vents[1].setUpperBoundRange(60, 99);
        state.updateVentMovement();
        Assert.assertEquals(vents[1].getLowerBoundStartMove(), 2);

    }

    public void updateVentMovementCInfluenceTest() {
        StatusState state = new StatusState();
        final VentStatus[] vents = state.getVents();
        int u = VentStatus.STARTING_VENT_VALUE;

        //C is influenced by both A and B
        //Unidentified A and B influence (assume worst movement)
        state.updateVentStatus(new int[]{u, u, u}, 7);

        //Movement by 0 since A and B are unknown
        vents[2].setLowerBoundRange(41, 59);
        vents[2].setUpperBoundRange(41, 59);
        state.updateVentMovement();
        Assert.assertEquals(vents[2].getLowerBoundStartMove(), 0);

        //Movement by 0 since A and B are unknown;
        //doesnt matter C is outside of 41-59% range
        vents[2].setLowerBoundRange(0, 40);
        vents[2].setUpperBoundRange(60, 99);
        state.updateVentMovement();
        Assert.assertEquals(vents[2].getLowerBoundStartMove(), 0);


        //Unidentified A and 41-59% B influence (assume worst movement)
        state.updateVentStatus(new int[]{u, 50, u}, 7);

        //Movement by 0 since A is unknown and B is 41-59%
        vents[2].setLowerBoundRange(41, 59);
        vents[2].setUpperBoundRange(41, 59);
        state.updateVentMovement();
        Assert.assertEquals(vents[2].getLowerBoundStartMove(), 0);

        //Movement by 0 since A is unknown and B is 41-59%;
        //doesnt matter C is outside of 41-59% range
        vents[2].setLowerBoundRange(0, 40);
        vents[2].setUpperBoundRange(60, 99);
        state.updateVentMovement();
        Assert.assertEquals(vents[2].getLowerBoundStartMove(), 0);


        //Unidentified A and outside 41-59% B influence (assume worst movement)
        state.updateVentStatus(new int[]{u, 0, u}, 7);

        //Movement by 0 since A is unknown and C is 41-59%;
        //doesnt matter B is outside of 41-59% range
        vents[2].setLowerBoundRange(41, 59);
        vents[2].setUpperBoundRange(41, 59);
        state.updateVentMovement();
        Assert.assertEquals(vents[2].getLowerBoundStartMove(), 0);

        //Movement by 1 since A is unknown;
        //B and C are outside of 41-59% range
        vents[2].setLowerBoundRange(0, 40);
        vents[2].setUpperBoundRange(60, 99);
        state.updateVentMovement();
        Assert.assertEquals(vents[2].getLowerBoundStartMove(), 1);




        //41-59% A and unidentified B influence (assume worst movement)
        state.updateVentStatus(new int[]{50, u, u}, 7);
        state.clearVentMovement();
        vents[1].clearRanges();

        //Movement by 0 since A is 41-59% and B is unknown
        vents[2].setLowerBoundRange(41, 59);
        vents[2].setUpperBoundRange(41, 59);
        state.updateVentMovement();
        Assert.assertEquals(vents[2].getLowerBoundStartMove(), 0);

        //Movement by 0 since A and B are unknown;
        //doesnt matter C is outside of 41-59% range
        vents[2].setLowerBoundRange(0, 40);
        vents[2].setUpperBoundRange(60, 99);
        state.updateVentMovement();
        Assert.assertEquals(vents[2].getLowerBoundStartMove(), 0);


        //41-59% A and B influence
        state.updateVentStatus(new int[]{50, 50, u}, 7);

        //Movement by 0 since A and B are 41-59%
        vents[2].setLowerBoundRange(41, 59);
        vents[2].setUpperBoundRange(41, 59);
        state.updateVentMovement();
        Assert.assertEquals(vents[2].getLowerBoundStartMove(), 0);

        //Movement by 0 since A and B are 41-59%;
        //doesnt matter C is outside of 41-59% range
        vents[2].setLowerBoundRange(0, 40);
        vents[2].setUpperBoundRange(60, 99);
        state.updateVentMovement();
        Assert.assertEquals(vents[2].getLowerBoundStartMove(), 0);


        //41-59% A and outside 41-59% B influence
        state.updateVentStatus(new int[]{50, 0, u}, 7);

        //Movement by 0 since A and C are 41-59%;
        //doesnt matter B is outside of 41-59% range
        vents[2].setLowerBoundRange(41, 59);
        vents[2].setUpperBoundRange(41, 59);
        state.updateVentMovement();
        Assert.assertEquals(vents[2].getLowerBoundStartMove(), 0);

        //Movement by 1 since B and C are outside of 41-59% range;
        //because A is 41-59%
        vents[2].setLowerBoundRange(0, 40);
        vents[2].setUpperBoundRange(60, 99);
        state.updateVentMovement();
        Assert.assertEquals(vents[2].getLowerBoundStartMove(), 1);




        //Outside 41-59% A and unidentified B influence (assume worst movement)
        state.updateVentStatus(new int[]{0, u, u}, 7);
        state.clearVentMovement();
        vents[1].clearRanges();

        //Movement by 0 since B is unknown and C is 41-59%
        //even though A is outside of 41-59%
        vents[2].setLowerBoundRange(41, 59);
        vents[2].setUpperBoundRange(41, 59);
        state.updateVentMovement();
        Assert.assertEquals(vents[2].getLowerBoundStartMove(), 0);

        //Movement by 1 since B is unknown;
        //both A and C are outside of 41-59% range
        vents[2].setLowerBoundRange(0, 40);
        vents[2].setUpperBoundRange(60, 99);
        state.updateVentMovement();
        Assert.assertEquals(vents[2].getLowerBoundStartMove(), 1);


        //Outside 41-59% A and 41-59% B influence
        state.updateVentStatus(new int[]{0, 50, u}, 7);
        state.clearVentMovement();

        //Movement by 0 since B and C are 41-59%
        vents[2].setLowerBoundRange(41, 59);
        vents[2].setUpperBoundRange(41, 59);
        state.updateVentMovement();
        Assert.assertEquals(vents[2].getLowerBoundStartMove(), 0);

        //Movement by 1 since A and C are outside 41-59%;
        //B is 41-59% range
        vents[2].setLowerBoundRange(0, 40);
        vents[2].setUpperBoundRange(60, 99);
        state.updateVentMovement();
        Assert.assertEquals(vents[2].getLowerBoundStartMove(), 1);


        //41-59% A and outside 41-59% B influence
        state.updateVentStatus(new int[]{0, 0, u}, 7);
        state.clearVentMovement();

        //Movement by 1 since A and B are outside 41-59%;
        //C is 41-59% range
        vents[2].setLowerBoundRange(41, 59);
        vents[2].setUpperBoundRange(41, 59);
        state.updateVentMovement();
        Assert.assertEquals(vents[2].getLowerBoundStartMove(), 1);

        //Movement by 2 since all vents are outside of 41-59% range;
        state.clearVentMovement();
        vents[2].setLowerBoundRange(0, 40);
        vents[2].setUpperBoundRange(60, 99);
        state.updateVentMovement();
        Assert.assertEquals(vents[2].getLowerBoundStartMove(), 2);
    }

    public void clearVentMovementTest() {
        StatusState state = new StatusState();
        final VentStatus[] vents = state.getVents();
        state.updateVentStatus(new int[]{VentStatus.STARTING_VENT_VALUE,
                VentStatus.STARTING_VENT_VALUE,
                VentStatus.STARTING_VENT_VALUE}, 7);
        for(int i = 0; i < StatusState.NUM_VENTS; ++i) {
            vents[i].setLowerBoundRange(0,0);
            vents[i].setUpperBoundRange(0,0);
        }
        state.updateVentMovement();
        for(int i = 0; i < StatusState.NUM_VENTS; ++i) {
            Assert.assertEquals(vents[i].getLowerBoundStartMove(), 2);
        }
        state.clearVentMovement();
        for(int i = 0; i < StatusState.NUM_VENTS; ++i) {
            Assert.assertEquals(vents[i].getLowerBoundStartMove(), 0);
        }
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
        int u = VentStatus.STARTING_VENT_VALUE;
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
        int u = VentStatus.STARTING_VENT_VALUE;
        state = new StatusState();
        state.updateVentStatus(new int[]{u, u, u}, 7);
        state.calcPredictedVentValues(25);
        Assert.assertEquals(state.getStabilityChange(), 25);
        final VentStatus[] vents2 = state.getVents();
        Assert.assertFalse(vents2[0].isRangeDefined());
        Assert.assertEquals(vents2[0].getLowerBoundStart(), u);
        Assert.assertEquals(vents2[0].getLowerBoundEnd(), u);
        Assert.assertEquals(vents2[0].getUpperBoundStart(), u);
        Assert.assertEquals(vents2[0].getUpperBoundEnd(), u);
    }

    public void calcSingleVentValueTest() {
        //Normal calc test
        StatusState state = new StatusState();
        state.updateVentStatus(new int[]{VentStatus.STARTING_VENT_VALUE, 51, 51}, 7);
        state.calcPredictedVentValues(25);
        Assert.assertEquals(state.getStabilityChange(), 25);
        final VentStatus[] vents = state.getVents();
        Assert.assertTrue(vents[0].isRangeDefined());
        Assert.assertEquals(vents[0].getLowerBoundStart(), 50);
        Assert.assertEquals(vents[0].getLowerBoundEnd(), 50);
        Assert.assertEquals(vents[0].getUpperBoundStart(), 50);
        Assert.assertEquals(vents[0].getUpperBoundEnd(), 50);

        //Truncation possibilities test
        state = new StatusState();
        state.updateVentStatus(new int[]{VentStatus.STARTING_VENT_VALUE, 50, 50}, 7);
        state.calcPredictedVentValues(24);
        Assert.assertEquals(state.getStabilityChange(), 24);
        final VentStatus[] vents2 = state.getVents();
        Assert.assertTrue(vents2[0].isRangeDefined());
        Assert.assertEquals(vents2[0].getLowerBoundStart(), 45);
        Assert.assertEquals(vents2[0].getLowerBoundEnd(), 47);
        Assert.assertEquals(vents2[0].getUpperBoundStart(), 53);
        Assert.assertEquals(vents2[0].getUpperBoundEnd(), 55);
    }
/*
    public void calcDoubleVentValueTest() {
        VentStatusPredicter predicter = new VentStatusPredicter();
        predicter.updateVentStatus(new int[]{VentStatus.STARTING_VENT_VALUE, 50, VentStatus.STARTING_VENT_VALUE}, 7);
        final VentStatus[] vents = predicter.getCurrentVents();
        predicter.calcDoubleVentValue(new VentStatus[]{vents[0], vents[2]}, 20);
        Assert.assertEquals(vents[0].getLowerBoundStart(), 35);
        Assert.assertEquals(vents[0].getLowerBoundEnd(), 65);
        Assert.assertEquals(vents[0].getUpperBoundStart(), 35);
        Assert.assertEquals(vents[0].getUpperBoundEnd(), 65);
        Assert.assertEquals(vents[2].getLowerBoundStart(), 35);
        Assert.assertEquals(vents[2].getLowerBoundEnd(), 65);
        Assert.assertEquals(vents[2].getUpperBoundStart(), 35);
        Assert.assertEquals(vents[2].getUpperBoundEnd(), 65);

        predicter = new VentStatusPredicter();
        predicter.updateVentStatus(new int[]{VentStatus.STARTING_VENT_VALUE, 50, VentStatus.STARTING_VENT_VALUE}, 7);
        final VentStatus[] vents2 = predicter.getCurrentVents();
        predicter.calcDoubleVentValue(new VentStatus[]{vents2[0], vents2[2]}, 0);
        Assert.assertEquals(vents2[0].getLowerBoundStart(), 0);
        Assert.assertEquals(vents2[0].getLowerBoundEnd(), 25);
        Assert.assertEquals(vents2[0].getUpperBoundStart(), 75);
        Assert.assertEquals(vents2[0].getUpperBoundEnd(), 100);
        Assert.assertEquals(vents2[2].getLowerBoundStart(), 0);
        Assert.assertEquals(vents2[2].getLowerBoundEnd(), 25);
        Assert.assertEquals(vents2[2].getUpperBoundStart(), 75);
        Assert.assertEquals(vents2[2].getUpperBoundEnd(), 100);

        predicter = new VentStatusPredicter();
        predicter.updateVentStatus(new int[]{VentStatus.STARTING_VENT_VALUE, 50, VentStatus.STARTING_VENT_VALUE}, 7);
        final VentStatus[] vents3 = predicter.getCurrentVents();
        predicter.calcDoubleVentValue(new VentStatus[]{vents3[0], vents3[2]}, -5);
        Assert.assertEquals(vents3[0].getLowerBoundStart(), 0);
        Assert.assertEquals(vents3[0].getLowerBoundEnd(), 10);
        Assert.assertEquals(vents3[0].getUpperBoundStart(), 90);
        Assert.assertEquals(vents3[0].getUpperBoundEnd(), 100);
        Assert.assertEquals(vents3[2].getLowerBoundStart(), 0);
        Assert.assertEquals(vents3[2].getLowerBoundEnd(), 10);
        Assert.assertEquals(vents3[2].getUpperBoundStart(), 90);
        Assert.assertEquals(vents3[2].getUpperBoundEnd(), 100);
    }
 */
}
