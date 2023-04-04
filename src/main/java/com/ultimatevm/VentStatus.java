package com.ultimatevm;

public class VentStatus {
    public static final int STARTING_VENT_VALUE = 127;
    public static final int MIN_VENT_VALUE = 0;
    public static final int MIN_VENT_START_VALUE = 25;
    public static final int PERFECT_VENT_VALUE = 50;
    public static final int MAX_VENT_START_VALUE = 75;
    public static final int MAX_VENT_VALUE = 100;
    public static int BASE_MOVE_RATE = 2;

    public enum VentChangeStateFlag {
        IDENTIFIED (1),
        NO_CHANGE (2),
        ONE_CHANGE (4),
        TWO_CHANGE (8),
        DIRECTION_CHANGE (16);
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

    //Estimated Movement
    private int lowerBoundStartMove, lowerBoundEndMove;
    private int upperBoundStartMove, upperBoundEndMove;
    private int totalDirectionalMovement;

    public static int getVentStabilityInfluence(int ventValue) { return Math.abs(PERFECT_VENT_VALUE - ventValue); }
    public static int getInfluenceOfValue(int value) {
        if(value > 40 && value < 60) return -1;
        return 0;
    }
    public static int getReversedInfluenceOfValue(int value, int dir) {
        return getInfluenceOfValue(value - dir);
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
        clearMovement();
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
        //Movement
        this.lowerBoundStartMove = vent.lowerBoundStartMove;
        this.lowerBoundEndMove = vent.lowerBoundEndMove;
        this.upperBoundStartMove = vent.upperBoundStartMove;
        this.upperBoundEndMove = vent.upperBoundEndMove;
        this.totalDirectionalMovement = vent.totalDirectionalMovement;
    }
    public int update(int actualValue, int direction) {
        int bitState = 0;
        int prevValue = this.actualValue;
        this.actualValue = actualValue;
        if(this.movementDirection != 0 && this.movementDirection != direction)
            bitState |= VentChangeStateFlag.DIRECTION_CHANGE.bitFlag;
        this.movementDirection = direction;

        if(isIdentified()) {
            lowerBoundStart = lowerBoundEnd = actualValue;
            upperBoundStart = upperBoundEnd = actualValue;
        } else return bitState;

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
    public void doForwardMovement(int outsideVentInfluence) {
        if(!isIdentified()) return;
        int move = Math.max(0, BASE_MOVE_RATE + outsideVentInfluence) * movementDirection;
        update(this.actualValue + move, movementDirection);
    }
    public void doBackwardMovement(int outsideVentInfluence) {
        if(!isIdentified()) return;
        int move = Math.max(0, BASE_MOVE_RATE + outsideVentInfluence) * movementDirection;
        update(this.actualValue - move, movementDirection);
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

        //Keep track of the movement of our ranges
        lowerBoundStartMove += getLowerBoundStart() - lowerStart;
        upperBoundStartMove += getUpperBoundStart() - upperStart;
        lowerBoundEndMove += getLowerBoundEnd() - lowerEnd;
        upperBoundEndMove += getUpperBoundEnd() - upperEnd;

        //Increase amount of time we were moving in a specific direction
        //Use values before they were updated
        if(movementDirection > 0 && upperEnd >= MAX_VENT_VALUE) return;
        if(movementDirection < 0 && lowerStart <= MIN_VENT_VALUE) return;
        totalDirectionalMovement += movementDirection;
    }
    public void clearMovement() {
        lowerBoundStartMove = lowerBoundEndMove = 0;
        upperBoundStartMove = upperBoundEndMove = 0;
        totalDirectionalMovement = 0;
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
        boolean isLowerBoundClipped = getTotalBoundStart() > getLowerBoundEnd();
        boolean isUpperBoundClipped = getTotalBoundEnd() < getUpperBoundStart();
        if(isLowerBoundClipped && isUpperBoundClipped) {
            //Both ranges are outside possible bounds (shouldn't happen)
            clearRanges();
            setLowerBoundRange(getTotalBoundStart(), getTotalBoundEnd());
            setUpperBoundRange(getTotalBoundStart(), getTotalBoundEnd());
        }
        else if(isLowerBoundClipped) {
            //Lower bound doesnt fit in total bounds use upper bound instead
            setLowerBoundRange(getUpperBoundStart(), getUpperBoundEnd());
        }
        else if(isUpperBoundClipped) {
            //Upper bound doesnt fit in total bounds use lower bound instead
            setUpperBoundRange(getLowerBoundStart(), getLowerBoundEnd());
        }
        //Do nothing if neither range is outside total bounds
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
        if(!isLowerBoundWithinRange(start, end)) return new int[]{0, 0};
        int maxStart = Math.max(getLowerBoundStart(), start);
        int minEnd = Math.min(getLowerBoundEnd(), end);
        return new int[]{maxStart, minEnd};
    }
    public int[] getOverlappedUpperBoundRange(int start, int end) {
        if(!isUpperBoundWithinRange(start, end)) return new int[]{0, 0};
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
        //BUG - for now reversed movement only works for identified values
        if(!isRangeDefined()) return -1;
        return getReversedInfluenceOfValue(actualValue, movementDirection);
    }

    public int getStabilityInfluence() {
        if(!isIdentified()) return 0;
        return getVentStabilityInfluence(actualValue);
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
    public int getTotalDirectionalMovement() { return totalDirectionalMovement; }
    public int getLowerBoundStartMove() { return lowerBoundStartMove; }
    public int getLowerBoundEndMove() { return lowerBoundEndMove; }
    public int getUpperBoundStartMove() { return upperBoundStartMove; }
    public int getUpperBoundEndMove() { return upperBoundEndMove; }
}
