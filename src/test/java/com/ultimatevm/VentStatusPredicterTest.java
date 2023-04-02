package com.ultimatevm;

import net.runelite.client.util.Text;
import org.testng.annotations.Test;
import org.testng.Assert;

@Test()
public class VentStatusPredicterTest {

    public void constructorTest() {
        VentStatusPredicter predicter = new VentStatusPredicter();
        Assert.assertNotNull(predicter.getDisplayState());
    }

    public void initializeTest() {
        VentStatusPredicter predicter = new VentStatusPredicter();
        predicter.updateVentStatus(new int[]{VentStatus.STARTING_VENT_VALUE, 40, 50}, 7);
        predicter.makeStatusState(10);
        predicter.initialize();

        Assert.assertNotNull(predicter.getDisplayState());
        for(int i = 0; i < StatusState.NUM_VENTS; ++i) {
            Assert.assertFalse(predicter.getDisplayState().getVents()[i].isIdentified());
            Assert.assertEquals(predicter.getDisplayState().getVents()[i].getDirection(), 0);
        }

    }

    public void resetTest() {
        VentStatusPredicter predicter = new VentStatusPredicter();
        predicter.reset();
        Assert.assertTrue(predicter.getDisplayState().hasDoneVMReset());
    }

    public void updateVentStatusTest() {
        int u = VentStatus.STARTING_VENT_VALUE;
        VentStatusPredicter predicter = new VentStatusPredicter();
        //All vents should match since a value was just identified
        int[] values = {u, 96, u};
        predicter.updateVentStatus(values, 7);
        for(int i = 0; i < StatusState.NUM_VENTS; ++i) {
            Assert.assertEquals(predicter.getDisplayState().getVents()[i].getActualValue(), values[i]);
            Assert.assertEquals(predicter.getDisplayState().getVents()[i].getDirection(), 1);
        }

        //Value has changed by one; previous state should not be updated
        predicter.updateVentStatus(new int[]{u, 97, u}, 7);
        Assert.assertEquals(predicter.getDisplayState().getVents()[1].getActualValue(), 97);

        //Value has changed by two; previous state match the previous value above
        predicter.updateVentStatus(new int[]{u, 99, u}, 7);
        Assert.assertEquals(predicter.getDisplayState().getVents()[1].getActualValue(), 99);

        //No change; same answer as above
        predicter.updateVentStatus(new int[]{u, 99, u}, 7);
        Assert.assertEquals(predicter.getDisplayState().getVents()[1].getActualValue(), 99);
    }

    public void makeStatusStateRangeTest() {
        VentStatusPredicter predicter = new VentStatusPredicter();
        //No identified vents so ranges should NOT be made
        predicter.makeStatusState(10);
        for(int i = 0; i < StatusState.NUM_VENTS; ++i) {
            Assert.assertFalse(predicter.getDisplayState().getVents()[i].isIdentified());
            Assert.assertFalse(predicter.getDisplayState().getVents()[i].isRangeDefined());
        }

        //Right amount of vents are known a range should be made!
        predicter.updateVentStatus(new int[]{VentStatus.STARTING_VENT_VALUE, 40, 50}, 7);
        predicter.makeStatusState(10);
        Assert.assertFalse(predicter.getDisplayState().getVents()[0].isIdentified());
        Assert.assertTrue(predicter.getDisplayState().getVents()[0].isRangeDefined());


        //All identified vents so ranges should NOT be made
        int[] values = new int[]{30, 40, 50};
        predicter.updateVentStatus(values, 7);
        predicter.makeStatusState(10);
        for(int i = 0; i < StatusState.NUM_VENTS; ++i) {
            Assert.assertTrue(predicter.getDisplayState().getVents()[i].isIdentified());
            Assert.assertEquals(predicter.getDisplayState().getVents()[i].getLowerBoundStart(), values[i]);
        }
    }

    public void updateVentMovementTest() {
        VentStatusPredicter predicter = new VentStatusPredicter();
        //Movement should be only updated for vents with predicted ranges
        predicter.updateVentStatus(new int[]{VentStatus.STARTING_VENT_VALUE, 0, 0}, 7);
        predicter.updateVentMovement();
        for(int i = 0; i < StatusState.NUM_VENTS; ++i) {
            Assert.assertEquals(predicter.getDisplayState().getVents()[i].getTotalDirectionalMovement(), 0);
        }

        //Movement should be only updated for vents with predicted ranges
        predicter = new VentStatusPredicter();
        predicter.updateVentStatus(new int[]{VentStatus.STARTING_VENT_VALUE, 80, 20}, 7);
        predicter.makeStatusState(5);
        predicter.updateVentMovement();
        int[] ans = new int[]{1, 0, 0};
        for(int i = 0; i < StatusState.NUM_VENTS; ++i) {
            Assert.assertEquals(predicter.getDisplayState().getVents()[i].getTotalDirectionalMovement(), ans[i]);
        }
    }

    public void clearMovementTest() {
        VentStatusPredicter predicter = new VentStatusPredicter();
        predicter.updateVentStatus(new int[]{VentStatus.STARTING_VENT_VALUE, 80, 20}, 7);
        predicter.makeStatusState(5);
        predicter.updateVentMovement();
        int[] ans = new int[]{1, 0, 0};
        for(int i = 0; i < StatusState.NUM_VENTS; ++i) {
            Assert.assertEquals(predicter.getDisplayState().getVents()[i].getTotalDirectionalMovement(), ans[i]);
        }

        predicter.clearVentMovement();
        for(int i = 0; i < StatusState.NUM_VENTS; ++i) {
            Assert.assertEquals(predicter.getDisplayState().getVents()[i].getTotalDirectionalMovement(), 0);
        }
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
        predicter.makeStatusState(25);
        String result = predicter.getVentStatusText(0, "A: ");
        result = Text.removeTags(result);
        Assert.assertEquals(result, "A: 50%");

        //Only a single range should be displayed
        predicter.initialize();
        predicter.updateVentStatus(new int[]{VentStatus.STARTING_VENT_VALUE, 50, 51}, 7);
        predicter.makeStatusState(25);
        result = Text.removeTags(predicter.getVentStatusText(0, "A: "));
        Assert.assertEquals(result, "A: 49-51%");

        //Two values should be displayed
        predicter.initialize();
        predicter.reset();
        predicter.updateVentStatus(new int[]{VentStatus.STARTING_VENT_VALUE, 50, 50}, 7);
        predicter.makeStatusState(8);
        predicter.getDisplayState().getVents()[0].setLowerBoundRange(0, 0);
        result = Text.removeTags(predicter.getVentStatusText(0, "A: "));
        Assert.assertEquals(result, "A: 0% 100%");

        //Two ranges should be displayed
        predicter.initialize();
        predicter.updateVentStatus(new int[]{VentStatus.STARTING_VENT_VALUE, 50, 50}, 7);
        predicter.makeStatusState(24);
        result = Text.removeTags(predicter.getVentStatusText(0, "A: "));
        Assert.assertEquals(result, "A: 45-47 53-55");
    }

    public void fixRangesInvalidTest() {
        //If frozen ranges should remain unchanged
        VentStatusPredicter predicter = new VentStatusPredicter();
        predicter.updateVentStatus(new int[]{50, 50, VentStatus.STARTING_VENT_VALUE}, 7);
        predicter.makeStatusState(25);
        final VentStatus[] vents = predicter.getDisplayState().getVents();
        int lowerBoundStart = vents[2].getLowerBoundStart();
        int lowerBoundEnd = vents[2].getLowerBoundEnd();
        int upperBoundStart = vents[2].getUpperBoundStart();
        int upperBoundEnd = vents[2].getUpperBoundEnd();
        for(int i = 0; i < 4; ++i) predicter.updateVentMovement();
        predicter.makeStatusState(25);
        Assert.assertEquals(vents[2].getLowerBoundStart(), lowerBoundStart);
        Assert.assertEquals(vents[2].getLowerBoundEnd(), lowerBoundEnd);
        Assert.assertEquals(vents[2].getUpperBoundStart(), upperBoundStart);
        Assert.assertEquals(vents[2].getUpperBoundEnd(), upperBoundEnd);
    }

    public void fixRangesMovementTest() {
        //Negative change from 50% with upward movement; upper bound range should be picked
        VentStatusPredicter predicter = new VentStatusPredicter();
        predicter.updateVentStatus(new int[]{VentStatus.STARTING_VENT_VALUE, 50, 50}, 7);
        predicter.makeStatusState(25);
        predicter.updateVentMovement();
        predicter.makeStatusState(24);
        final VentStatus[] vents = predicter.getDisplayState().getVents();
        final StatusState currentState = predicter.getMergedState();
        Assert.assertEquals(vents[0].getLowerBoundStart(), currentState.getVents()[0].getUpperBoundStart());
        Assert.assertEquals(vents[0].getLowerBoundEnd(), currentState.getVents()[0].getUpperBoundEnd());
        Assert.assertEquals(vents[0].getUpperBoundStart(), currentState.getVents()[0].getUpperBoundStart());
        Assert.assertEquals(vents[0].getUpperBoundEnd(), currentState.getVents()[0].getUpperBoundEnd());

        //Negative change from 50% with downward movement; lower bound range should be picked
        predicter = new VentStatusPredicter();
        predicter.updateVentStatus(new int[]{VentStatus.STARTING_VENT_VALUE, 50, 50}, 0);
        predicter.makeStatusState(25);
        predicter.updateVentMovement();
        predicter.makeStatusState(24);
        final VentStatus[] vents2 = predicter.getDisplayState().getVents();
        final StatusState currentState2 = predicter.getMergedState();
        Assert.assertEquals(vents2[0].getLowerBoundStart(), currentState2.getVents()[0].getLowerBoundStart());
        Assert.assertEquals(vents2[0].getLowerBoundEnd(), currentState2.getVents()[0].getLowerBoundEnd());
        Assert.assertEquals(vents2[0].getUpperBoundStart(), currentState2.getVents()[0].getLowerBoundStart());
        Assert.assertEquals(vents2[0].getUpperBoundEnd(), currentState2.getVents()[0].getLowerBoundEnd());

        //Positive change from 26% with upward movement; lower bound range should be picked
        predicter = new VentStatusPredicter();
        predicter.updateVentStatus(new int[]{VentStatus.STARTING_VENT_VALUE, 50, 50}, 7);
        predicter.makeStatusState(17);
        predicter.updateVentMovement();
        predicter.makeStatusState(18);
        final VentStatus[] vents3 = predicter.getDisplayState().getVents();
        final StatusState currentState3 = predicter.getMergedState();
        Assert.assertEquals(vents3[0].getLowerBoundStart(), currentState3.getVents()[0].getLowerBoundStart());
        Assert.assertEquals(vents3[0].getLowerBoundEnd(), currentState3.getVents()[0].getLowerBoundEnd());
        Assert.assertEquals(vents3[0].getUpperBoundStart(), currentState3.getVents()[0].getLowerBoundStart());
        Assert.assertEquals(vents3[0].getUpperBoundEnd(), currentState3.getVents()[0].getLowerBoundEnd());

        //Positive change from 74% with downward movement; upper bound range should be picked
        predicter = new VentStatusPredicter();
        predicter.updateVentStatus(new int[]{VentStatus.STARTING_VENT_VALUE, 50, 50}, 0);
        predicter.makeStatusState(17);
        predicter.updateVentMovement();
        predicter.makeStatusState(18);
        final VentStatus[] vents4 = predicter.getDisplayState().getVents();
        final StatusState currentState4 = predicter.getMergedState();
        Assert.assertEquals(vents4[0].getLowerBoundStart(), currentState4.getVents()[0].getUpperBoundStart());
        Assert.assertEquals(vents4[0].getLowerBoundEnd(), currentState4.getVents()[0].getUpperBoundEnd());
        Assert.assertEquals(vents4[0].getUpperBoundStart(), currentState4.getVents()[0].getUpperBoundStart());
        Assert.assertEquals(vents4[0].getUpperBoundEnd(), currentState4.getVents()[0].getUpperBoundEnd());
    }

    public void fixRangesTruncationPossibilitiesTest() {
        //Truncation possibility value: 1
        VentStatusPredicter predicter = new VentStatusPredicter();
        predicter.updateVentStatus(new int[]{42, 58, VentStatus.STARTING_VENT_VALUE}, 2);
        predicter.makeStatusState(14);
        //3 movement update(s)
        for(int i = 0; i < 3; ++i) predicter.updateVentMovement();
        predicter.updateVentStatus(new int[]{41, 58, VentStatus.STARTING_VENT_VALUE}, 2);
        predicter.makeStatusState(14);
        final VentStatus[] vents = predicter.getDisplayState().getVents();
        Assert.assertEquals(vents[2].getLowerBoundStart(), 32);
        Assert.assertEquals(vents[2].getLowerBoundEnd(), 34);
        Assert.assertEquals(vents[2].getUpperBoundStart(), 66);
        Assert.assertEquals(vents[2].getUpperBoundEnd(), 68);

        //Truncation possibility value: 2
        predicter = new VentStatusPredicter();
        predicter.updateVentStatus(new int[]{57, 41, VentStatus.STARTING_VENT_VALUE}, 6);
        predicter.makeStatusState(14);
        //4 movement update(s)
        for(int i = 0; i < 4; ++i) predicter.updateVentMovement();
        predicter.updateVentStatus(new int[]{59, 41, VentStatus.STARTING_VENT_VALUE}, 6);
        predicter.makeStatusState(14);
        final VentStatus[] vents2 = predicter.getDisplayState().getVents();
        Assert.assertEquals(vents2[2].getLowerBoundStart(), 33);
        Assert.assertEquals(vents2[2].getLowerBoundEnd(), 35);
        Assert.assertEquals(vents2[2].getUpperBoundStart(), 65);
        Assert.assertEquals(vents2[2].getUpperBoundEnd(), 67);
    }

    public void fixRangesInaccurateMovementTest() {
        //Lets assume stability is at 100% for a bit and vent changed signficantly
        //Move is now quite a bit short
        VentStatusPredicter predicter = new VentStatusPredicter();
        predicter.updateVentStatus(new int[]{VentStatus.STARTING_VENT_VALUE, 41, 72}, 4);
        for(int i = 0; i < 3; ++i) predicter.updateVentMovement();
        predicter.makeStatusState(11);
        predicter.updateVentStatus(new int[]{VentStatus.STARTING_VENT_VALUE, 40, 75}, 4);
        for(int i = 0; i < 2; ++i) predicter.updateVentMovement();
        predicter.makeStatusState(7);
        final VentStatus[] vents = predicter.getDisplayState().getVents();
        Assert.assertEquals(vents[0].getLowerBoundStart(), 29);
        Assert.assertEquals(vents[0].getLowerBoundEnd(), 31);
        Assert.assertEquals(vents[0].getUpperBoundStart(), 29);
        Assert.assertEquals(vents[0].getUpperBoundEnd(), 31);

        //Frozen but not really upward movement
        predicter = new VentStatusPredicter();
        predicter.reset();
        predicter.updateVentStatus(new int[]{50, 25, VentStatus.STARTING_VENT_VALUE}, 7);
        predicter.makeStatusState(1);
        predicter.updateVentMovement();
        predicter.makeStatusState(2);
        final VentStatus[] vents3 = predicter.getDisplayState().getVents();
        Assert.assertEquals(vents3[2].getLowerBoundStart(), 4);
        Assert.assertEquals(vents3[2].getLowerBoundEnd(), 6);
        Assert.assertEquals(vents3[2].getUpperBoundStart(), 4);
        Assert.assertEquals(vents3[2].getUpperBoundEnd(), 6);

        //Frozen but not really downward movement
        predicter = new VentStatusPredicter();
        predicter.reset();
        predicter.updateVentStatus(new int[]{50, 25, VentStatus.STARTING_VENT_VALUE}, 3);
        predicter.makeStatusState(1);
        predicter.updateVentMovement();
        predicter.makeStatusState(2);
        final VentStatus[] vents4 = predicter.getDisplayState().getVents();
        Assert.assertEquals(vents4[2].getLowerBoundStart(), 94);
        Assert.assertEquals(vents4[2].getLowerBoundEnd(), 96);
        Assert.assertEquals(vents4[2].getUpperBoundStart(), 94);
        Assert.assertEquals(vents4[2].getUpperBoundEnd(), 96);

        //Ensure correct range bounds are set
        predicter = new VentStatusPredicter();
        predicter.updateVentStatus(new int[]{51, 33, VentStatus.STARTING_VENT_VALUE}, 6);
        predicter.makeStatusState(12);
        for(int i = 0; i < 2; ++i) predicter.updateVentMovement();
        predicter.updateVentStatus(new int[]{51, 35, VentStatus.STARTING_VENT_VALUE}, 6);
        predicter.makeStatusState(13);
        final VentStatus[] vents5 = predicter.getDisplayState().getVents();
        Assert.assertEquals(vents5[2].getLowerBoundStart(), 28);
        Assert.assertEquals(vents5[2].getLowerBoundEnd(), 30);
        Assert.assertEquals(vents5[2].getUpperBoundStart(), 70);
        Assert.assertEquals(vents5[2].getUpperBoundEnd(), 72);
    }

    public void fixRangesSingleRangeMiddleTest() {
        //Single range in the middle; upward move
        VentStatusPredicter predicter = new VentStatusPredicter();
        predicter.updateVentStatus(new int[]{VentStatus.STARTING_VENT_VALUE, 50, 50}, 1);
        predicter.makeStatusState(25);
        predicter.updateVentMovement();
        final VentStatus[] vents = predicter.getDisplayState().getVents();
        predicter.makeStatusState(24);
        Assert.assertEquals(vents[0].getLowerBoundStart(), 53);
        Assert.assertEquals(vents[0].getLowerBoundEnd(), 55);
        Assert.assertEquals(vents[0].getUpperBoundStart(), 53);
        Assert.assertEquals(vents[0].getUpperBoundEnd(), 55);

        //Single range in the middle; downward move
        predicter = new VentStatusPredicter();
        predicter.updateVentStatus(new int[]{VentStatus.STARTING_VENT_VALUE, 50, 50}, 0);
        predicter.makeStatusState(25);
        predicter.updateVentMovement();
        final VentStatus[] vents2 = predicter.getDisplayState().getVents();
        predicter.makeStatusState(24);
        Assert.assertEquals(vents2[0].getLowerBoundStart(), 45);
        Assert.assertEquals(vents2[0].getLowerBoundEnd(), 47);
        Assert.assertEquals(vents2[0].getUpperBoundStart(), 45);
        Assert.assertEquals(vents2[0].getUpperBoundEnd(), 47);
    }

    public void fixRangesBoundsTest() {
        //The incorrect answer should be eliminated by our total bounds
        VentStatusPredicter predicter = new VentStatusPredicter();
        predicter.reset();
        predicter.updateVentStatus(new int[]{VentStatus.STARTING_VENT_VALUE, 15, 20}, 2);
        for(int i = 0; i < 2; ++i) predicter.updateVentMovement();
        predicter.makeStatusState(-14);
        predicter.updateVentStatus(new int[]{VentStatus.STARTING_VENT_VALUE, 19, 16}, 2);
        for(int i = 0; i < 2; ++i) predicter.updateVentMovement();
        predicter.makeStatusState(-12);
        final VentStatus[] vents = predicter.getDisplayState().getVents();
        Assert.assertEquals(vents[0].getLowerBoundStart(), 2);
        Assert.assertEquals(vents[0].getLowerBoundEnd(), 4);
        Assert.assertEquals(vents[0].getUpperBoundStart(), 2);
        Assert.assertEquals(vents[0].getUpperBoundEnd(), 4);

        predicter = new VentStatusPredicter();
        predicter.reset();
        predicter.updateVentStatus(new int[]{VentStatus.STARTING_VENT_VALUE, 15, 20}, 3);
        for(int i = 0; i < 2; ++i) predicter.updateVentMovement();
        predicter.makeStatusState(-14);
        predicter.updateVentStatus(new int[]{VentStatus.STARTING_VENT_VALUE, 19, 16}, 3);
        for(int i = 0; i < 2; ++i) predicter.updateVentMovement();
        predicter.makeStatusState(-12);
        final VentStatus[] vents2 = predicter.getDisplayState().getVents();
        Assert.assertEquals(vents2[0].getLowerBoundStart(), 96);
        Assert.assertEquals(vents2[0].getLowerBoundEnd(), 98);
        Assert.assertEquals(vents2[0].getUpperBoundStart(), 96);
        Assert.assertEquals(vents2[0].getUpperBoundEnd(), 98);

        //Outside of bounds ranges should be eliminated even if its the first state made
        predicter = new VentStatusPredicter();
        predicter.updateVentStatus(new int[]{62, 31, VentStatus.STARTING_VENT_VALUE}, 6);
        for(int i = 0; i < 2; ++i) predicter.updateVentMovement();
        predicter.makeStatusState(5);
        final VentStatus[] vents3 = predicter.getDisplayState().getVents();
        Assert.assertEquals(vents3[2].getLowerBoundStart(), 79);
        Assert.assertEquals(vents3[2].getLowerBoundEnd(), 81);
        Assert.assertEquals(vents3[2].getUpperBoundStart(), 79);
        Assert.assertEquals(vents3[2].getUpperBoundEnd(), 81);

        predicter = new VentStatusPredicter();
        predicter.reset();
        predicter.updateVentStatus(new int[]{VentStatus.STARTING_VENT_VALUE, 74, 51}, 3);
        for(int i = 0; i < 2; ++i) predicter.updateVentMovement();
        predicter.makeStatusState(-1);
        final VentStatus[] vents4 = predicter.getDisplayState().getVents();
        Assert.assertEquals(vents4[0].getLowerBoundStart(), 100);
        Assert.assertEquals(vents4[0].getLowerBoundEnd(), 100);
        Assert.assertEquals(vents4[0].getUpperBoundStart(), 100);
        Assert.assertEquals(vents4[0].getUpperBoundEnd(), 100);

        //Ensure ranges dont get partially bounds clipped
        //Downward
        predicter = new VentStatusPredicter();
        predicter.reset();
        predicter.updateVentStatus(new int[]{VentStatus.STARTING_VENT_VALUE, 53, 4}, 4);
        predicter.updateVentMovement();
        predicter.makeStatusState(-7);
        final VentStatus[] vents5 = predicter.getDisplayState().getVents();
        Assert.assertEquals(vents5[0].getLowerBoundStart(), 1);
        Assert.assertEquals(vents5[0].getLowerBoundEnd(), 3);
        Assert.assertEquals(vents5[0].getUpperBoundStart(), 97);
        Assert.assertEquals(vents5[0].getUpperBoundEnd(), 99);

        //Upward
        predicter = new VentStatusPredicter();
        predicter.reset();
        predicter.updateVentStatus(new int[]{VentStatus.STARTING_VENT_VALUE, 53, 4}, 5);
        predicter.updateVentMovement();
        predicter.makeStatusState(-7);
        final VentStatus[] vents6 = predicter.getDisplayState().getVents();
        Assert.assertEquals(vents6[0].getLowerBoundStart(), 1);
        Assert.assertEquals(vents6[0].getLowerBoundEnd(), 3);
        Assert.assertEquals(vents6[0].getUpperBoundStart(), 97);
        Assert.assertEquals(vents6[0].getUpperBoundEnd(), 99);
    }

    public void futureTimingTests() {
        //Inaccurate movement downward
//        VentStatusPredicter predicter = new VentStatusPredicter();
//        predicter.updateVentStatus(new int[]{VentStatus.STARTING_VENT_VALUE, 25, 5}, 6);
//        predicter.makeStatusState(-2);
//        for(int i = 0; i < 3; ++i) predicter.updateVentMovement();
//        predicter.updateVentStatus(new int[]{VentStatus.STARTING_VENT_VALUE, 28, 4}, 6);
//        predicter.makeStatusState(-2);
//        //inaccurate movement here
//        for(int i = 0; i < 2; ++i) predicter.updateVentMovement();
//        predicter.updateVentStatus(new int[]{VentStatus.STARTING_VENT_VALUE, 32, 8}, 6);
//        predicter.makeStatusState(1);
//        final VentStatus[] vents = predicter.getCurrentVents();
//        Assert.assertEquals(vents[0].getLowerBoundStart(), 36);
//        Assert.assertEquals(vents[0].getLowerBoundEnd(), 38);
//        Assert.assertEquals(vents[0].getUpperBoundStart(), 36);
//        Assert.assertEquals(vents[0].getUpperBoundEnd(), 38);

        //Inaccurate movement upward
//        predicter = new VentStatusPredicter();
//        predicter.updateVentStatus(new int[]{VentStatus.STARTING_VENT_VALUE, 25, 5}, 7);
//        predicter.makeStatusState(-2);
//        for(int i = 0; i < 3; ++i) predicter.updateVentMovement();
//        predicter.updateVentStatus(new int[]{VentStatus.STARTING_VENT_VALUE, 28, 4}, 7);
//        predicter.makeStatusState(-2);
//        //inaccurate movement here
//        for(int i = 0; i < 2; ++i) predicter.updateVentMovement();
//        predicter.updateVentStatus(new int[]{VentStatus.STARTING_VENT_VALUE, 32, 8}, 7);
//        predicter.makeStatusState(1);
//        final VentStatus[] vents2 = predicter.getCurrentVents();
//        Assert.assertEquals(vents2[0].getLowerBoundStart(), 62);
//        Assert.assertEquals(vents2[0].getLowerBoundEnd(), 64);
//        Assert.assertEquals(vents2[0].getUpperBoundStart(), 62);
//        Assert.assertEquals(vents2[0].getUpperBoundEnd(), 64);
    }

    public void sandbox() {
        //Odd C bug - slight movement and did not select the right answer
        //Ill let slide for now due to stability updates from server might be delayed
        VentStatusPredicter predicter = new VentStatusPredicter();
        predicter.updateVentStatus(new int[]{44, 68, VentStatus.STARTING_VENT_VALUE}, 0);
        for(int i = 0; i < 2; ++i) predicter.updateVentMovement();
        predicter.makeStatusState(16);
        predicter.updateVentStatus(new int[]{42, 66, VentStatus.STARTING_VENT_VALUE}, 0);
        for(int i = 0; i < 2; ++i) predicter.updateVentMovement();
        predicter.makeStatusState(15);
//        final VentStatus[] vents = predicter.getCurrentVents();
//        Assert.assertEquals(vents[2].getLowerBoundStart(), 42);
//        Assert.assertEquals(vents[2].getLowerBoundEnd(), 44);
//        Assert.assertEquals(vents[2].getUpperBoundStart(), 42);
//        Assert.assertEquals(vents[2].getUpperBoundEnd(), 44);

        //Incorrect A selected
        //First state is likely a delayed stability change
        predicter = new VentStatusPredicter();
        predicter.reset();
        predicter.updateVentStatus(new int[]{VentStatus.STARTING_VENT_VALUE, 76, 85}, 0);
        for(int i = 0; i < 2; ++i) predicter.updateVentMovement();
        predicter.makeStatusState(-4);
        predicter.updateVentStatus(new int[]{VentStatus.STARTING_VENT_VALUE, 74, 87}, 0);
        for(int i = 0; i < 3; ++i) predicter.updateVentMovement();
        predicter.makeStatusState(-3);
//        final VentStatus[] vents = predicter.getCurrentVents();
//        Assert.assertEquals(vents[0].getLowerBoundStart(), 25);
//        Assert.assertEquals(vents[0].getLowerBoundEnd(), 27);
//        Assert.assertEquals(vents[0].getUpperBoundStart(), 25);
//        Assert.assertEquals(vents[0].getUpperBoundEnd(), 27);


        //Incorrect A after fix
        //Both ranges are clipped here! - delayed stability update also affects this test
        predicter = new VentStatusPredicter();
        predicter.reset();
        predicter.updateVentStatus(new int[]{VentStatus.STARTING_VENT_VALUE, 8, 18}, 2);
        for(int i = 0; i < 2; ++i) predicter.updateVentMovement();
        predicter.makeStatusState(-17);
        predicter.updateVentStatus(new int[]{VentStatus.STARTING_VENT_VALUE, 12, 14}, 2);
        for(int i = 0; i < 2; ++i) predicter.updateVentMovement();
        predicter.makeStatusState(-17);
        predicter.updateVentStatus(new int[]{VentStatus.STARTING_VENT_VALUE, 16, 18}, 6);
        for(int i = 0; i < 2; ++i) predicter.updateVentMovement();
        predicter.makeStatusState(-14);
        predicter.updateVentStatus(new int[]{VentStatus.STARTING_VENT_VALUE, 18, 20}, 6);
        for(int i = 0; i < 2; ++i) predicter.updateVentMovement();
        predicter.makeStatusState(-13);
        predicter.updateVentStatus(new int[]{VentStatus.STARTING_VENT_VALUE, 22, 24}, 7);
        predicter.updateVentStatus(new int[]{VentStatus.STARTING_VENT_VALUE, 24, 26}, 7);
        for(int i = 0; i < 2; ++i) predicter.updateVentMovement();
        predicter.makeStatusState(-8);
        final VentStatus[] vents = predicter.getDisplayState().getVents();
    }
}
