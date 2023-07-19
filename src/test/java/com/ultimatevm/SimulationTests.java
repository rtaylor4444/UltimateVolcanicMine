package com.ultimatevm;

import org.junit.Assert;
import org.testng.annotations.Test;
@Test()
public class SimulationTests {
    VentStatusPredicter predicter;
    int[] ventValues, ventDirection;
    int u = VentStatus.STARTING_VENT_VALUE;
    int directionBitState, currentTick;

    //Helpers
    private void createPredicter(int dir, int tick, int size) {
        StabilityUpdateInfo.setNumPlayers(size);
        predicter = new VentStatusPredicter();
        ventValues = new int[]{u,u,u};
        ventDirection = new int[]{0,0,0};
        currentTick = tick+1;
        updateVentDirection(dir);
        predicter.updateVentStatus(ventValues, directionBitState);
        predicter.getTimeline().updateTick();
    }
    private int getDirectionFromChambers(int index, int chambers) { return (chambers & (1 << index)) != 0 ? 1 : -1;}
    private void updateVentDirection(int dir) {
        directionBitState = dir;
        for(int i = 0; i < StatusState.NUM_VENTS; ++i)
            ventDirection[i] = getDirectionFromChambers(i, directionBitState);
    }
    private void advanceTicks(int nextTick) {
        int advTicks = nextTick - currentTick;
        for(int i = 0; i < advTicks; ++i) {
            predicter.updateVentStatus(ventValues, directionBitState);
            predicter.getTimeline().updateTick();
        }
        currentTick = nextTick;
    }
    private void identifyVent(int A, int B, int C) {
        if(A != u) ventValues[0] = A;
        if(B != u) ventValues[1] = B;
        if(C != u) ventValues[2] = C;
    }

    //Events
    private void doIdentifyVent(int tick, int A, int B, int C) {
        advanceTicks(tick-1);
        identifyVent(A, B, C);
        predicter.updateVentStatus(ventValues, directionBitState);
        predicter.getTimeline().updateTick();
        ++currentTick;
    }
    private void doDirectionChange(int tick, int newDir) {
        advanceTicks(tick-1);
        updateVentDirection(newDir);
        predicter.updateVentStatus(ventValues, directionBitState);
        predicter.getTimeline().updateTick();
        ++currentTick;
    }
    private void doStabilityUpdate(int tick, int change) {
        advanceTicks(tick-1);
        predicter.updateVentStatus(ventValues, directionBitState);
        predicter.makeStatusState(change);
        predicter.getTimeline().updateTick();
        ++currentTick;
    }
    private void doMovementUpdateByValue(int tick, int A, int B, int C) {
        advanceTicks(tick-1);
        identifyVent(A, B, C);
        predicter.updateVentStatus(ventValues, directionBitState);
        predicter.getTimeline().updateTick();
        ++currentTick;
    }
    private void doSameTickMovementStabilityUpdate(int tick, int A, int B, int C, int change) {
        advanceTicks(tick-1);
        identifyVent(A, B, C);
        predicter.updateVentStatus(ventValues, directionBitState);
        predicter.makeStatusState(change);
        predicter.getTimeline().updateTick();
        ++currentTick;
    }
    private void doEarthquake(int tick) {
        advanceTicks(tick-1);
        predicter.markEarthquakeEvent();
    }
    private void doReset() {
        advanceTicks(500);
        predicter.reset();
        predicter.getTimeline().updateTick();
        ++currentTick;
    }

    public void simulateFreezeClipAFreezeThresholdCross() {
        createPredicter(1, 500, 1);
        doEarthquake(505);
        doIdentifyVent(523, u, 66, u);
        doMovementUpdateByValue(530, u, 64, u);
        doIdentifyVent(537, u, 64, 29);
        doMovementUpdateByValue(540, u, 62, 27);
        doMovementUpdateByValue(550, u, 60, 25);
        doMovementUpdateByValue(560, u, 58, 23);
        doMovementUpdateByValue(570, u, 57, 22);
        doMovementUpdateByValue(580, u, 56, 21);
        doMovementUpdateByValue(590, u, 55, 20);
        doDirectionChange(595, 3);
        doDirectionChange(599, 1);
        updateVentDirection(3);
        doMovementUpdateByValue(600, u, 56, 19);
        doDirectionChange(603, 1);
        doMovementUpdateByValue(610, u, 55, 18);
        doMovementUpdateByValue(620, u, 54, 17);
        doMovementUpdateByValue(630, u, 53, 16);
        doEarthquake(640);
        doMovementUpdateByValue(650, u, 52, 15);
        doEarthquake(655);
        doMovementUpdateByValue(660, u, 51, 14);
        doMovementUpdateByValue(680, u, 50, 13);
        doMovementUpdateByValue(690, u, 49, 12);
        doSameTickMovementStabilityUpdate(700, u, 48, 11, -3);
        doMovementUpdateByValue(710, u, 47, 10);
        doMovementUpdateByValue(720, u, 46, 9);
        doStabilityUpdate(725, -6);
        doMovementUpdateByValue(730, u, 45, 8);
        doMovementUpdateByValue(740, u, 44, 7);
        doSameTickMovementStabilityUpdate(750, u, 43, 6, -9);
        doMovementUpdateByValue(760, u, 42, 5);
        doDirectionChange(764, 5);
        doMovementUpdateByValue(770, u, 41, 6);
        doStabilityUpdate(775, -9);
        doMovementUpdateByValue(780, u, 40, 7);

        StatusState predictedState = predicter.getDisplayState();
        Assert.assertTrue(predictedState.getVents()[0].isRangeDefined());
    }

    public void simulateFreezeClipABoundThresholdCross() {
        createPredicter(0, 500, 1);
        doIdentifyVent(500, u, u, 33);
        doMovementUpdateByValue(509, u, u, 31);
        identifyVent(u, 3, u);
        doMovementUpdateByValue(519, u, 3, 29);
        doStabilityUpdate(524, -4);
        doMovementUpdateByValue(529, u, 1, 27);
        updateVentDirection(2);
        doMovementUpdateByValue(539, u, 0, 25);

        StatusState predictedState = predicter.getDisplayState();
        Assert.assertTrue(predictedState.getVents()[0].isRangeDefined());
    }

    public void simulateEstimatedMoveRemoved() {
        createPredicter(4, 500, 1);
        doIdentifyVent(501, u, u, 41);
        doMovementUpdateByValue(509, u, u, 42);
        doMovementUpdateByValue(519, u, u, 43);
        doIdentifyVent(520, u, 72, u);
        doStabilityUpdate(524, 9);
        doMovementUpdateByValue(529, u, 70, 44);
        doMovementUpdateByValue(539, u, 68, 45);
        doSameTickMovementStabilityUpdate(549, u, 66, 46, 14);
        doMovementUpdateByValue(559, u, 64, 47);
        doMovementUpdateByValue(569, u, 63, 47);
        doStabilityUpdate(574, 17);
        doMovementUpdateByValue(579, u, 62, 47);
        doMovementUpdateByValue(589, u, 61, 47);
        doMovementUpdateByValue(599, u, 60, 47);
        doMovementUpdateByValue(609, u, 59, 47);
        doDirectionChange(615, 6);
        doDirectionChange(624, 4);
        doDirectionChange(626, 6);
        doDirectionChange(629, 4);
        doMovementUpdateByValue(749, u, 58, 47);
        doMovementUpdateByValue(759, u, 57, 47);

        StatusState predictedState = predicter.getDisplayState();
        Assert.assertTrue(predictedState.getVents()[0].isRangeDefined());
    }

    public void simulateFlickeringA() {
        createPredicter(6, 0, 1);
        doStabilityUpdate(24, 15);
        doIdentifyVent(43, u, 60, u);
        doSameTickMovementStabilityUpdate(49, u, 62, u, 12);
        doMovementUpdateByValue(59, u, 64, u);
        doIdentifyVent(60, u, u, 53);
        doMovementUpdateByValue(69, u, 66, 54);
        doDirectionChange(73, 2);
        doStabilityUpdate(74, 7);
        doMovementUpdateByValue(79, u, 68, 53);
        doMovementUpdateByValue(89, u, 70, 52);
        doDirectionChange(91, 0);
        doEarthquake(99);
        doMovementUpdateByValue(109, u, 68, 51);
        doMovementUpdateByValue(119, u, 66, 50);
        doStabilityUpdate(124, 6);
        advanceTicks(129);
//        doMovementUpdateByValue(139, u, 64, 49);
//        doSameTickMovementStabilityUpdate(149, u, 62, 48, 6);

        StatusState predictedState = predicter.getDisplayState();
        Assert.assertTrue(predictedState.getVents()[0].isRangeDefined());
    }

    public void simulateBCSoloStart() {
        createPredicter(3, 0, 1);
        doEarthquake(24);
        doIdentifyVent(44, u, u, 57);
        doStabilityUpdate(49, 15);
        doIdentifyVent(69, u, 37, u);
        doStabilityUpdate(74, 14);

        StatusState predictedState = predicter.getDisplayState();
        //Ensure freeze setting + stability clipping works properly
        Assert.assertEquals(predictedState.getVents()[0].getLowerBoundStart(), 60);
        Assert.assertEquals(predictedState.getVents()[0].getLowerBoundEnd(), 60);
        Assert.assertEquals(predictedState.getVents()[0].getUpperBoundStart(), 60);
        Assert.assertEquals(predictedState.getVents()[0].getUpperBoundEnd(), 60);
    }

    public void simulateAFreezeRangeNoStabilityUpdate() {
        createPredicter(3, 500, 1);
        doIdentifyVent(506, u, u, 17);
        doMovementUpdateByValue(509, u, u, 15);
        doMovementUpdateByValue(519, u, u, 14);
        doIdentifyVent(524, u, 42, u);
        doMovementUpdateByValue(529, u, 43, 13);
        doMovementUpdateByValue(539, u, 44, 12);
        doMovementUpdateByValue(549, u, 45, 11);
        doMovementUpdateByValue(559, u, 46, 10);
        doEarthquake(669);
        doEarthquake(699);
        doMovementUpdateByValue(779, u, 47, 9);

        StatusState predictedState = predicter.getDisplayState();
        Assert.assertEquals(predictedState.getVents()[0].getLowerBoundStart(), 62);
        Assert.assertEquals(predictedState.getVents()[0].getLowerBoundEnd(), 62);
        Assert.assertEquals(predictedState.getVents()[0].getUpperBoundStart(), 62);
        Assert.assertEquals(predictedState.getVents()[0].getUpperBoundEnd(), 62);
    }

    public void simulateIncorrectFreezeRangeA() {
        createPredicter(2, 500, 1);
        doIdentifyVent(501, u, u, 57);
        doMovementUpdateByValue(509, u, u, 56);
        doIdentifyVent(517, u, 42, u);
        doMovementUpdateByValue(519, u, 43, 56);
        doStabilityUpdate(524, 16);
        doMovementUpdateByValue(529, u, 44, 56);
        doMovementUpdateByValue(539, u, 45, 56);
        doMovementUpdateByValue(549, u, 46, 56);
        doEarthquake(579);
        doEarthquake(654);
        doEarthquake(669);
        doMovementUpdateByValue(779, u, 47, 56);
        doMovementUpdateByValue(789, u, 48, 56);

        StatusState predictedState = predicter.getDisplayState();
        Assert.assertEquals(predictedState.getVents()[0].getLowerBoundStart(), 36);
        Assert.assertEquals(predictedState.getVents()[0].getLowerBoundEnd(), 36);
        Assert.assertEquals(predictedState.getVents()[0].getUpperBoundStart(), 36);
        Assert.assertEquals(predictedState.getVents()[0].getUpperBoundEnd(), 36);
    }

    public void simulateIncorrectMovementOrderBug() {
        createPredicter(3, 0, 1);
        doEarthquake(24);
        doIdentifyVent(33, 49, u, u);
        doMovementUpdateByValue(39, 50, u, u);
        doSameTickMovementStabilityUpdate(49, 51, u, u, 19);
        doIdentifyVent(54, u, 41, u);
        doMovementUpdateByValue(59, 52, 41, u);
        doMovementUpdateByValue(69, 53, 41, u);
        doStabilityUpdate(74, 19);
        doMovementUpdateByValue(79, 54, 41, u);
        doMovementUpdateByValue(89, 55, 41, u);
        doMovementUpdateByValue(99, 56, 41, u);
        doMovementUpdateByValue(109, 57, 41, u);
        doMovementUpdateByValue(119, 58, 41, u);
        doMovementUpdateByValue(129, 59, 41, u);
        doMovementUpdateByValue(139, 60, 41, u);
        doMovementUpdateByValue(149, 62, 42, u);
        doMovementUpdateByValue(159, 64, 43, u);
        doMovementUpdateByValue(169, 66, 44, u);
        doMovementUpdateByValue(179, 68, 45, u);
        doMovementUpdateByValue(189, 70, 46, u);
        doMovementUpdateByValue(199, 72, 47, u);
        doMovementUpdateByValue(209, 74, 48, u);
        doMovementUpdateByValue(229, 76, 49, u);
        doMovementUpdateByValue(239, 78, 50, u);
        doMovementUpdateByValue(249, 80, 51, u);
        doMovementUpdateByValue(259, 82, 52, u);
        doMovementUpdateByValue(269, 84, 53, u);
        doMovementUpdateByValue(279, 86, 54, u);
        doMovementUpdateByValue(289, 88, 55, u);
        doMovementUpdateByValue(299, 90, 56, u);
        doMovementUpdateByValue(309, 92, 57, u);
        doMovementUpdateByValue(319, 94, 58, u);
        doMovementUpdateByValue(329, 96, 59, u);
        doMovementUpdateByValue(339, 98, 60, u);
        doMovementUpdateByValue(349, 100, 62, u);
        doDirectionChange(351, 2);
        doMovementUpdateByValue(359, 98, 64, u);
        doMovementUpdateByValue(369, 96, 66, u);
        doMovementUpdateByValue(379, 94, 68, u);
        doMovementUpdateByValue(389, 92, 70, u);
        doMovementUpdateByValue(409, 90, 72, u);
        doMovementUpdateByValue(419, 88, 74, u);
        doStabilityUpdate(424, -1);
        doMovementUpdateByValue(429, 86, 76, u);
        doMovementUpdateByValue(439, 84, 78, u);
        doSameTickMovementStabilityUpdate(449, 82, 80, u, -2);

        //C is 26 - only possible answer
        StatusState predictedState = predicter.getDisplayState();
        Assert.assertEquals(predictedState.getVents()[2].getLowerBoundStart(), 26);
        Assert.assertEquals(predictedState.getVents()[2].getLowerBoundEnd(), 26);
        Assert.assertEquals(predictedState.getVents()[2].getUpperBoundStart(), 26);
        Assert.assertEquals(predictedState.getVents()[2].getUpperBoundEnd(), 26);
    }

    public void simulateMovementSkipNonFreezeRangeA() {
        createPredicter(2, 500, 1);
        doIdentifyVent(525, u, 48, u);
        doMovementUpdateByValue(528, u, 49, u);
        doMovementUpdateByValue(538, u, 50, u);
        doIdentifyVent(539, u, u, 33);
        doMovementUpdateByValue(548, u, 51, 32);
        doMovementUpdateByValue(558, u, 52, 31);
        doDirectionChange(565, 0);
        doMovementUpdateByValue(568, u, 51, 30);
        doMovementUpdateByValue(578, u, 50, 29);
        doMovementUpdateByValue(588, u, 49, 28);
        doMovementUpdateByValue(598, u, 48, 27);
        doDirectionChange(608, 0);
//        doMovementUpdateByValue(618, u, 47, 26);

        StatusState predictedState = predicter.getDisplayState();
        Assert.assertFalse(predictedState.getVents()[0].isRangeDefined());
    }

    public void simulateIncorrectPredictedStability() {
        createPredicter(7, 0, 1);
        doStabilityUpdate(23, 18);
        doIdentifyVent(36, 62, u, u);
        doSameTickMovementStabilityUpdate(48, 64, u, u, 15);
        doDirectionChange(53, 6);
        doMovementUpdateByValue(58, 62, u, u);
        doMovementUpdateByValue(68, 60, u, u);
        doStabilityUpdate(73, 16);
        doMovementUpdateByValue(78, 58, u, u);
        doIdentifyVent(82, u, 58, u);
        doMovementUpdateByValue(88, 57, 58, u);
        doMovementUpdateByValue(98, 56, 58, u);
        doMovementUpdateByValue(108, 55, 58, u);
        doMovementUpdateByValue(118, 54, 58, u);
        doMovementUpdateByValue(128, 53, 58, u);
        doMovementUpdateByValue(138, 52, 58, u);
        doMovementUpdateByValue(148, 51, 58, u);
        doEarthquake(158);
        doMovementUpdateByValue(168, 50, 58, u);
        doMovementUpdateByValue(178, 49, 58, u);
        doMovementUpdateByValue(188, 48, 58, u);
        doMovementUpdateByValue(198, 47, 58, u);
        doMovementUpdateByValue(208, 46, 58, u);
        doMovementUpdateByValue(218, 45, 58, u);
        doMovementUpdateByValue(228, 44, 58, u);
        doMovementUpdateByValue(238, 43, 58, u);
        doMovementUpdateByValue(248, 42, 58, u);
        doMovementUpdateByValue(258, 41, 58, u);
        doMovementUpdateByValue(268, 40, 58, u);
        doMovementUpdateByValue(278, 38, 59, u);
        doMovementUpdateByValue(288, 36, 60, u);
        doMovementUpdateByValue(298, 34, 62, u);
        doMovementUpdateByValue(308, 32, 64, u);
        doMovementUpdateByValue(318, 30, 66, u);
        doMovementUpdateByValue(328, 28, 68, u);
        doMovementUpdateByValue(338, 26, 70, u);
        doMovementUpdateByValue(348, 24, 72, u);
        doMovementUpdateByValue(358, 22, 74, u);
        doDirectionChange(360, 7);
        doEarthquake(368);
        //Negative Updates start here
        doMovementUpdateByValue(378, 24, 76, u);
        doMovementUpdateByValue(388, 26, 78, u);
        doMovementUpdateByValue(398, 28, 80, u);
        doMovementUpdateByValue(408, 30, 82, u);
        doMovementUpdateByValue(418, 32, 84, u);
        doMovementUpdateByValue(428, 34, 86, u);

        int predictedUpdate = predicter.getFutureStabilityChange(UltimateVolcanicMineConfig.PredictionScenario.WORST_CASE);
        StatusState predictedState = predicter.getDisplayState();
        Assert.assertEquals(predictedState.getVents()[2].getLowerBoundStart(), 54);
        Assert.assertEquals(predictedState.getVents()[2].getLowerBoundEnd(), 54);
        Assert.assertEquals(predictedState.getVents()[2].getUpperBoundStart(), 54);
        Assert.assertEquals(predictedState.getVents()[2].getUpperBoundEnd(), 54);
    }

    public void simulateSize3_5() {
//        createPredicter(0, 0, 5);
//        doStabilityUpdate(24, 14);
//        doIdentifyVent(33, u, 37, u);
//        updateVentDirection(2);
//        doMovementUpdateByValue(39, u, 36, u);
//        doSameTickMovementStabilityUpdate(49, u, 37, u, 9);
//        doIdentifyVent(53, 42, u, u);
//        doEarthquake(54);
//        doMovementUpdateByValue(59, 41, 38, u);
//        doMovementUpdateByValue(69, 40, 39, u);
//        doStabilityUpdate(74, 8);
//        doDirectionChange(78, 3);
//        doMovementUpdateByValue(79, 42, 41, u);
//
//        StatusState predictedState = predicter.getDisplayState();
    }

    public void simulateA4159NotAppear() {
        createPredicter(0, 0, 1);
        doIdentifyVent(15, u, 65, u);
        doMovementUpdateByValue(20, u, 63, u);
        doMovementUpdateByValue(30, u, 61, u);
        doMovementUpdateByValue(40, u, 59, u);
        doMovementUpdateByValue(50, u, 58, u);
        doMovementUpdateByValue(60, u, 57, u);
        doMovementUpdateByValue(70, u, 56, u);
        doDirectionChange(88, 2);
        doMovementUpdateByValue(90, u, 57, u);
        doDirectionChange(93, 0);
        doMovementUpdateByValue(100, u, 56, u);
        doEarthquake(110);
        doMovementUpdateByValue(120, u, 55, u);
        doMovementUpdateByValue(130, u, 54, u);
        doEarthquake(140);
        doMovementUpdateByValue(150, u, 53, u);
        doDirectionChange(186, 2);

        StatusState predictedState = predicter.getDisplayState();
        Assert.assertEquals(predictedState.getVents()[0].getLowerBoundStart(), 40);
        Assert.assertEquals(predictedState.getVents()[0].getLowerBoundEnd(), 57);
        Assert.assertEquals(predictedState.getVents()[0].getUpperBoundStart(), 40);
        Assert.assertEquals(predictedState.getVents()[0].getUpperBoundEnd(), 57);
    }

    public void simulateDoubleToSingleVentPrediction() {
        createPredicter(1, 0, 1);
        doStabilityUpdate(24, 16);
        doIdentifyVent(34, 46, u, u);
        doMovementUpdateByValue(39, 47, u, u);
        doSameTickMovementStabilityUpdate(49, 48, u, u, 17);
        doMovementUpdateByValue(59, 49, u, u);
        doIdentifyVent(65, u, 59, u);
        doMovementUpdateByValue(69, 50, 59, u);
        doStabilityUpdate(74, 17);

        StatusState predictedState = predicter.getDisplayState();
        Assert.assertEquals(predictedState.getVents()[2].getLowerBoundStart(), 35);
        Assert.assertEquals(predictedState.getVents()[2].getLowerBoundEnd(), 37);
        Assert.assertEquals(predictedState.getVents()[2].getUpperBoundStart(), 63);
        Assert.assertEquals(predictedState.getVents()[2].getUpperBoundEnd(), 65);
    }

    public void simulateOverwrittenADoubleVent() {
        createPredicter(6, 0, 1);
        doMovementUpdateByValue(8, u, 84, u);
        doDirectionChange(9, 4);
        doMovementUpdateByValue(18, u, 83, u);
        doMovementUpdateByValue(28, u, 82, u);
        doMovementUpdateByValue(38, u, 81, u);
        doMovementUpdateByValue(48, u, 79, u);
        doMovementUpdateByValue(58, u, 77, u);
        doMovementUpdateByValue(68, u, 75, u);
        doMovementUpdateByValue(78, u, 73, u);
        doMovementUpdateByValue(88, u, 71, u);
        doMovementUpdateByValue(98, u, 69, u);
        doMovementUpdateByValue(108, u, 67, u);
        doMovementUpdateByValue(118, u, 65, u);
        doDirectionChange(126, 6);
        doMovementUpdateByValue(128, u, 67, u);
        doMovementUpdateByValue(138, u, 69, u);
        doDirectionChange(141, 4);
        doSameTickMovementStabilityUpdate(148, u, 67, u, -5);

        StatusState predictedState = predicter.getDisplayState();
        Assert.assertEquals(predictedState.getVents()[0].getLowerBoundStart(), 18);
        Assert.assertEquals(predictedState.getVents()[0].getLowerBoundEnd(), 18);
        Assert.assertEquals(predictedState.getVents()[0].getUpperBoundStart(), 18);
        Assert.assertEquals(predictedState.getVents()[0].getUpperBoundEnd(), 18);

    }

    public void simulateIncorrectHalfSpaceClipping() {
        createPredicter(5, 0, 1);
        doIdentifyVent(3, 63, u, u);
        doMovementUpdateByValue(9, 65, u, u);
        doDirectionChange(18,4);
        doStabilityUpdate(24, -9);
        doMovementUpdateByValue(29, 63, u, u);
        doMovementUpdateByValue(39, 61, u, u);
        doSameTickMovementStabilityUpdate(49, 59, u, u, -6);
        doMovementUpdateByValue(59, 58, u, u);
        doMovementUpdateByValue(69, 57, u, u);
        doStabilityUpdate(74, -5);

        //Half space clipping should not occur here
        StatusState predictedState = predicter.getDisplayState();
        Assert.assertEquals(predictedState.getVents()[1].getLowerBoundStart(), 0);
        Assert.assertEquals(predictedState.getVents()[1].getLowerBoundEnd(), 4);
        Assert.assertEquals(predictedState.getVents()[1].getUpperBoundStart(), 83);
        Assert.assertEquals(predictedState.getVents()[1].getUpperBoundEnd(), 92);
        Assert.assertEquals(predictedState.getVents()[2].getLowerBoundStart(), 8);
        Assert.assertEquals(predictedState.getVents()[2].getLowerBoundEnd(), 17);
        Assert.assertEquals(predictedState.getVents()[2].getUpperBoundStart(), 96);
        Assert.assertEquals(predictedState.getVents()[2].getUpperBoundEnd(), 100);
    }

    public void simulate0ToNegativeHalfSpace() {
//        createPredicter(1, 0, 1);
//        doStabilityUpdate(24, 19);
//        doIdentifyVent(34, 46, u, u);
//        doMovementUpdateByValue(39, 47, u, u);
//        doSameTickMovementStabilityUpdate(49, 48, u, u, 19);
//        doIdentifyVent(55, u, 43, u);
//        doMovementUpdateByValue(59, 49, 43, u);
//
//        //Post reset
//        doReset();
//        doIdentifyVent(502, 64, u, u);
//        doMovementUpdateByValue(506, 62, u, u);
//        doMovementUpdateByValue(516, 60, u, u);
//        doMovementUpdateByValue(526, 58, u, u);
//        doMovementUpdateByValue(536, 57, u, u);
//        doMovementUpdateByValue(546, 56, u, u);
//        doMovementUpdateByValue(556, 55, u, u);
//        doMovementUpdateByValue(566, 54, u, u);
//        doStabilityUpdate(571, -2);
//        doEarthquake(576);
//        doMovementUpdateByValue(586, 53, u, u);
//        doMovementUpdateByValue(596, 52, u, u);
//
//        StatusState predictedState = predicter.getDisplayState();
    }
}
