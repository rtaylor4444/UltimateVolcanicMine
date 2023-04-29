package com.ultimatevm;

public class VentStatus {
    public static final int STARTING_VENT_VALUE = 127;
    public static final int MIN_VENT_VALUE = 0;
    public static final int MIN_VENT_START_VALUE = 25;
    public static final int PERFECT_VENT_VALUE = 50;
    public static final int MAX_VENT_START_VALUE = 75;
    public static final int MAX_VENT_VALUE = 100;
    public static final float VENT_STABILITY_WEIGHT = 16.0f;
    public static int BASE_MOVE_RATE = 2;

    public enum VentChangeStateFlag {
        IDENTIFIED (1),
        NO_CHANGE (2),
        ONE_CHANGE (4),
        TWO_CHANGE (8),
        DIRECTION_CHANGE (16),
        RESET(32);
        private final int bitFlag;

        VentChangeStateFlag(int bitFlag) {
            this.bitFlag = bitFlag;
        }
        int bitFlag() {return bitFlag;}
    }
    private char ventName;
    private int actualValue;
    private int movementDirection;

    //Estimated Bounds
    private int totalBoundStart, totalBoundEnd;
    private int lowerBoundStart, lowerBoundEnd;
    private int upperBoundStart, upperBoundEnd;

    public static int getStabilityInfluence(int ventValue) {
        float percentValue = PERFECT_VENT_VALUE - Math.abs(PERFECT_VENT_VALUE - ventValue);
        percentValue = (percentValue / 50.0f) * VENT_STABILITY_WEIGHT;
        return (int) Math.ceil(percentValue);
    }
    public static int getInfluenceOfValue(int value) {
        if(value > 40 && value < 60) return -1;
        return 0;
    }

    public VentStatus(char name) {
        ventName = name;
        this.movementDirection = 0;
        doVMReset();
        totalBoundStart = MIN_VENT_START_VALUE;
        totalBoundEnd = MAX_VENT_START_VALUE;
    }
    public VentStatus(VentStatus vent) {
        setEqualTo(vent);
    }

    public void doVMReset() {
        //Direction state will remain the same as before
        actualValue = STARTING_VENT_VALUE;
        totalBoundStart = MIN_VENT_VALUE;
        totalBoundEnd = MAX_VENT_VALUE;
        clearRanges();
    }
    public void setEqualTo(VentStatus vent) {
        this.ventName = vent.ventName;
        this.actualValue = vent.actualValue;
        this.movementDirection = vent.movementDirection;
        //Bounds
        this.lowerBoundStart = vent.lowerBoundStart;
        this.lowerBoundEnd = vent.lowerBoundEnd;
        this.upperBoundStart = vent.upperBoundStart;
        this.upperBoundEnd = vent.upperBoundEnd;
        this.totalBoundStart = vent.totalBoundStart;
        this.totalBoundEnd = vent.totalBoundEnd;
    }
    public int update(int actualValue, int direction) {
        int bitState = 0;
        int prevValue = this.actualValue;
        this.actualValue = actualValue;
        if(!isIdentified() && prevValue != STARTING_VENT_VALUE)
            bitState |= VentChangeStateFlag.RESET.bitFlag;
        if(this.movementDirection != 0 && this.movementDirection != direction)
            bitState |= VentChangeStateFlag.DIRECTION_CHANGE.bitFlag;
        this.movementDirection = direction;

        if(isIdentified() || (bitState & VentChangeStateFlag.RESET.bitFlag) != 0) {
            lowerBoundStart = lowerBoundEnd = actualValue;
            upperBoundStart = upperBoundEnd = actualValue;
        }
        if(!isIdentified()) return bitState;

        int diff = Math.abs(this.actualValue - prevValue);
        if(diff == 1) return bitState | VentChangeStateFlag.ONE_CHANGE.bitFlag;
        else if(diff == 2) return bitState |  VentChangeStateFlag.TWO_CHANGE.bitFlag;
        else if(diff > 2) return bitState | VentChangeStateFlag.IDENTIFIED.bitFlag;

        if(this.actualValue == MIN_VENT_VALUE && this.movementDirection == -1)
            return bitState;

        if(this.actualValue == MAX_VENT_VALUE && this.movementDirection == 1)
            return bitState;

        return bitState | VentChangeStateFlag.NO_CHANGE.bitFlag;
    }
    public void updateMovement(int outsideVentInfluence) {
        if(isIdentified()) return;

        //Allow bounds to be updated even if no ranges are defined
        int currentMoveRate = BASE_MOVE_RATE + outsideVentInfluence;
        //Update our maximum bounds
        totalBoundStart += Math.max(0, (currentMoveRate + getInfluenceOfValue(totalBoundStart))) * movementDirection;
        totalBoundStart = Math.max(MIN_VENT_VALUE, totalBoundStart);
        totalBoundEnd += Math.max(0, (currentMoveRate + getInfluenceOfValue(totalBoundEnd))) * movementDirection;
        totalBoundEnd = Math.min(MAX_VENT_VALUE, totalBoundEnd);
        if(!isRangeDefined()) return;

        //Update our current ranges
        int lowerStart = getLowerBoundStart();
        int lowerStartMove = Math.max(0, (currentMoveRate + getInfluenceOfValue(lowerStart))) * movementDirection;

        int upperEnd = getUpperBoundEnd();
        int upperEndMove = Math.max(0, (currentMoveRate + getInfluenceOfValue(upperEnd))) * movementDirection;

        int lowerEnd = getLowerBoundEnd();
        int lowerEndMove = Math.max(0, (currentMoveRate + getInfluenceOfValue(lowerEnd))) * movementDirection;

        int upperStart = getUpperBoundStart();
        int upperStartMove = Math.max(0, (currentMoveRate + getInfluenceOfValue(upperStart))) * movementDirection;

        clearRanges();
        setLowerBoundRange(lowerStart + lowerStartMove, lowerEnd + lowerEndMove);
        setUpperBoundRange(upperStart + upperStartMove, upperEnd + upperEndMove);
    }
    public void clearRanges() {
        lowerBoundStart = lowerBoundEnd = STARTING_VENT_VALUE;
        upperBoundStart = upperBoundEnd = STARTING_VENT_VALUE;
    }
    public void mergeLowerBoundRanges(int start, int end) {
        lowerBoundStart = Math.min(lowerBoundStart, capVentValue(start));
        lowerBoundEnd = Math.max(lowerBoundEnd, capVentValue(end));
    }
    public void mergeUpperBoundRanges(int start, int end) {
        upperBoundStart = Math.min(upperBoundStart, capVentValue(start));
        upperBoundEnd = Math.max(upperBoundEnd, capVentValue(end));
    }
    public void setLowerBoundRange(int start, int end) {
        lowerBoundStart = capVentValue(start);
        lowerBoundEnd = capVentValue(end);
        //Merge ranges if they are both within bounds
        if(isUpperBoundWithinRange(lowerBoundStart, lowerBoundEnd)) {
            mergeUpperBoundRanges(lowerBoundStart, lowerBoundEnd);
            mergeLowerBoundRanges(upperBoundStart, upperBoundEnd);
        }
    }
    public void setUpperBoundRange(int start, int end) {
        upperBoundStart = capVentValue(start);
        upperBoundEnd = capVentValue(end);
        //Merge ranges if they are both within bounds
        if(isLowerBoundWithinRange(upperBoundStart, upperBoundEnd)) {
            mergeUpperBoundRanges(lowerBoundStart, lowerBoundEnd);
            mergeLowerBoundRanges(upperBoundStart, upperBoundEnd);
        }
    }
    public void doBoundsClipping() {
        if(!isRangeDefined()) return;
        int[] lower = getOverlappedLowerBoundRange(getTotalBoundStart(), getTotalBoundEnd());
        int[] upper = getOverlappedUpperBoundRange(getTotalBoundStart(), getTotalBoundEnd());
        boolean isLowerBoundClipped = (lower[0] == -1 && lower[1] == -1);
        boolean isUpperBoundClipped = (upper[0] == -1 && upper[1] == -1);
        clearRanges();
        if(isLowerBoundClipped && isUpperBoundClipped) {
            //Both ranges are outside possible bounds (shouldn't happen)
            setLowerBoundRange(getTotalBoundStart(), getTotalBoundEnd());
            setUpperBoundRange(getTotalBoundStart(), getTotalBoundEnd());
        }
        else if(isLowerBoundClipped) {
            //Lower bound doesnt fit in total bounds use upper bound instead
            setUpperBoundRange(upper[0], upper[1]);
            setLowerBoundRange(upper[0], upper[1]);
        }
        else if(isUpperBoundClipped) {
            //Upper bound doesnt fit in total bounds use lower bound instead
            setUpperBoundRange(lower[0], lower[1]);
            setLowerBoundRange(lower[0], lower[1]);
        } else {
            setUpperBoundRange(upper[0], upper[1]);
            setLowerBoundRange(lower[0], lower[1]);
        }
    }
    public void flipDirection() {
        movementDirection *= -1;
    }

    public boolean isIdentified() { return actualValue != STARTING_VENT_VALUE; }
    public boolean isRangeDefined() {
        if(lowerBoundStart == STARTING_VENT_VALUE || lowerBoundEnd == STARTING_VENT_VALUE) return false;
        if(upperBoundStart == STARTING_VENT_VALUE || upperBoundEnd == STARTING_VENT_VALUE) return false;
        return true;
    }
    public boolean isTwoSeperateValues() { return !(lowerBoundStart == upperBoundStart && lowerBoundEnd == upperBoundEnd); }
    public boolean isLowerBoundSingleValue() { return (lowerBoundStart == lowerBoundEnd); }
    public boolean isUpperBoundSingleValue() { return (upperBoundStart == upperBoundEnd); }
    public boolean isLowerBoundWithinRange(int start, int end) {
        return !(start > lowerBoundEnd || end < lowerBoundStart);
    }
    public boolean isUpperBoundWithinRange(int start, int end) {
        return !(start > upperBoundEnd || end < upperBoundStart);
    }
    public boolean isWithinRange(int start, int end) {
        if(isLowerBoundWithinRange(start, end)) return true;
        if(isTwoSeperateValues()) {
            return isUpperBoundWithinRange(start, end);
        }
        return false;
    }
    public int[] getOverlappedLowerBoundRange(int start, int end) {
        if(!isLowerBoundWithinRange(start, end)) return new int[]{-1, -1};
        int maxStart = Math.max(getLowerBoundStart(), start);
        int minEnd = Math.min(getLowerBoundEnd(), end);
        return new int[]{maxStart, minEnd};
    }
    public int[] getOverlappedUpperBoundRange(int start, int end) {
        if(!isUpperBoundWithinRange(start, end)) return new int[]{-1, -1};
        int maxStart = Math.max(getUpperBoundStart(), start);
        int minEnd = Math.min(getUpperBoundEnd(), end);
        return new int[]{maxStart, minEnd};
    }
    public int getEstimatedInfluence() {
        if(!isRangeDefined()) return -1;
        if(isWithinRange(41,59)) return -1;
        return 0;
    }
    public int getReversedInfluence() {
        //TODO: Fix this code later to work with estimated ranges and bounded values
        //in the future exit when ranges give two possible values
        if(!isIdentified()) return STARTING_VENT_VALUE;
        return getInfluenceOfValue(capVentValue(actualValue - movementDirection));
    }
    public void doReversedMovement(int outsideVentInfluence) {
        //TODO: Fix this code later to work with estimated ranges and bounded values
        if(!isIdentified()) return;
        int currentMoveRate = Math.max(0, BASE_MOVE_RATE + outsideVentInfluence);
        actualValue = capVentValue(actualValue - (currentMoveRate * movementDirection));
        lowerBoundStart = lowerBoundEnd = actualValue;
        upperBoundStart = upperBoundEnd = actualValue;
    }

    public int getStabilityInfluence() {
        if(!isIdentified()) return 0;
        return getStabilityInfluence(actualValue);
    }

    private int capVentValue(int value) { return Math.min(MAX_VENT_VALUE, Math.max(MIN_VENT_VALUE, value));}


    //Getters
    public char getName() { return ventName; }
    public int getActualValue() { return actualValue; }
    public int getDirection() { return movementDirection; }
    public int getLowerBoundStart() { return lowerBoundStart; }
    public int getLowerBoundEnd() { return lowerBoundEnd; }
    public int getUpperBoundStart() { return upperBoundStart; }
    public int getUpperBoundEnd() { return upperBoundEnd; }
    public int getTotalBoundStart() { return totalBoundStart; }
    public int getTotalBoundEnd() { return totalBoundEnd; }
}
