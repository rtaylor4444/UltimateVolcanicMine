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
        advanceTicks(tick);
        predicter.markEarthquakeEvent();
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
}
