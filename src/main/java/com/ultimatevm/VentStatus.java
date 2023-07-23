package com.ultimatevm;

public class VentStatus {
    public static final int STARTING_VENT_VALUE = 127;
    public static final int MIN_VENT_VALUE = 0;
    public static final int PERFECT_VENT_VALUE = 50;
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
    }
    public VentStatus(VentStatus vent) {
        setEqualTo(vent);
    }

    public void doVMReset() {
        //Direction state will remain the same as before
        actualValue = STARTING_VENT_VALUE;
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
        if(!isRangeDefined()) return;

        //Update our current ranges
        int currentMoveRate = BASE_MOVE_RATE + outsideVentInfluence;

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
    public void doInnerBoundsClipping(int start, int end) {
        if(isIdentified()) return;
        if(!isRangeDefined()) return;
        int[] lower = getOverlappedLowerBoundRange(start, end);
        int[] upper = getOverlappedUpperBoundRange(start, end);
        boolean isLowerBoundClipped = (lower[0] == -1 && lower[1] == -1);
        boolean isUpperBoundClipped = (upper[0] == -1 && upper[1] == -1);
        clearRanges();
        if(isLowerBoundClipped && isUpperBoundClipped) return;

        if(isLowerBoundClipped) {
            //Lower bound doesnt fit use upper bound instead
            setUpperBoundRange(upper[0], upper[1]);
            setLowerBoundRange(upper[0], upper[1]);
        }
        else if(isUpperBoundClipped) {
            //Upper bound doesnt fit use lower bound instead
            setUpperBoundRange(lower[0], lower[1]);
            setLowerBoundRange(lower[0], lower[1]);
        } else {
            setUpperBoundRange(upper[0], upper[1]);
            setLowerBoundRange(lower[0], lower[1]);
        }
    }
    public void doOuterBoundsClipping(int start, int end) {
        if(isIdentified()) return;
        if(!isRangeDefined()) return;
        if(doOuterBoundsSingleRangeClipping(start, end)) return;

        int[] lower = getOutsideLowerBoundRange(start, end);
        int[] upper = getOutsideUpperBoundRange(start, end);
        boolean isLowerBoundClipped = (lower[0] == -1 && lower[1] == -1);
        boolean isUpperBoundClipped = (upper[0] == -1 && upper[1] == -1);
        clearRanges();
        if(isLowerBoundClipped && isUpperBoundClipped) return;

        if(isLowerBoundClipped) {
            //Lower bound doesnt fit use upper bound instead
            setUpperBoundRange(upper[0], upper[1]);
            setLowerBoundRange(upper[0], upper[1]);
        }
        else if(isUpperBoundClipped) {
            //Upper bound doesnt fit use lower bound instead
            setUpperBoundRange(lower[0], lower[1]);
            setLowerBoundRange(lower[0], lower[1]);
        } else {
            setUpperBoundRange(upper[0], upper[1]);
            setLowerBoundRange(lower[0], lower[1]);
        }
    }
    private boolean doOuterBoundsSingleRangeClipping(int start, int end) {
        if(isTwoSeperateValues()) return false;

        //Range to clip is out of our bounds
        int upperBoundEnd = getUpperBoundEnd();
        int lowerBoundStart = getLowerBoundStart();
        if(start > upperBoundEnd) return false;
        if(end < lowerBoundStart) return false;

        //Our current range fits inside of our range to clip
        clearRanges();
        if(start <= lowerBoundStart && end >= upperBoundEnd)
            return true;

        //Range to clip doesnt break the single range
        int maxStart = Math.max(start-1, lowerBoundStart);
        int minEnd = Math.min(end+1, upperBoundEnd);
        if(start <= lowerBoundStart || end >= upperBoundEnd) {
            //Lower end of our range is clipped
            if(start <= lowerBoundStart) {
                setUpperBoundRange(minEnd, upperBoundEnd);
                setLowerBoundRange(minEnd, upperBoundEnd);
            }
            //Upper end of our range is clipped
            if(end >= upperBoundEnd) {
                setUpperBoundRange(lowerBoundStart, maxStart);
                setLowerBoundRange(lowerBoundStart, maxStart);
            }
        }
        //Range to clip is inside of our current range
        else {
            setUpperBoundRange(minEnd, upperBoundEnd);
            setLowerBoundRange(lowerBoundStart, maxStart);
        }
        return true;
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
    public int[] getOutsideLowerBoundRange(int start, int end) {
        int newStart = lowerBoundStart, newEnd = lowerBoundEnd;
        if(!isLowerBoundWithinRange(start, end)) return new int[]{newStart, newEnd};
        //Check if start is within the passed bounds
        if(lowerBoundStart >= start && lowerBoundStart <= end) newStart = end+1;
        //Check if end is within the passed bounds
        if(lowerBoundEnd >= start && lowerBoundEnd <= end) newEnd = start-1;
        //return invalid range if both are within the passed bounds
        if(newStart > newEnd) return new int[]{-1, -1};
        return new int[]{newStart, newEnd};
    }
    public int[] getOverlappedUpperBoundRange(int start, int end) {
        if(!isUpperBoundWithinRange(start, end)) return new int[]{-1, -1};
        int maxStart = Math.max(getUpperBoundStart(), start);
        int minEnd = Math.min(getUpperBoundEnd(), end);
        return new int[]{maxStart, minEnd};
    }
    public int[] getOutsideUpperBoundRange(int start, int end) {
        int newStart = upperBoundStart, newEnd = upperBoundEnd;
        if(!isUpperBoundWithinRange(start, end)) return new int[]{newStart, newEnd};
        //Check if start is within the passed bounds
        if(upperBoundStart >= start && upperBoundStart <= end) newStart = end+1;
        //Check if end is within the passed bounds
        if(upperBoundEnd >= start && upperBoundEnd <= end) newEnd = start-1;
        //return invalid range if both are within the passed bounds
        if(newStart > newEnd) return new int[]{-1, -1};
        return new int[]{newStart, newEnd};
    }
    public int getEstimatedInfluence() {
        if(!isRangeDefined()) return -1;
        if(isWithinRange(41,59)) return -1;
        return 0;
    }
    public int getReversedInfluence(int outsideVentInfluence) {
        //TODO: Fix this code later to work with estimated ranges
        //We know the value is the same as before so we can exit safely
        if(outsideVentInfluence < -1)
            return isIdentified() ? getInfluenceOfValue(capVentValue(actualValue)) : 0;
        if(!isIdentified()) return STARTING_VENT_VALUE;
        //We cannot reverse bounded values; dont know how long they been bounded
        if(isBounded()) return STARTING_VENT_VALUE;

        int[] infPossibilities = new int[BASE_MOVE_RATE];
        for(int i = 0; i < BASE_MOVE_RATE; ++i) {
            int move = (outsideVentInfluence + (BASE_MOVE_RATE - i)) * movementDirection;
            infPossibilities[i] = getInfluenceOfValue(capVentValue(actualValue - move));
        }
        //Exit on freeze, non-freeze mismatch cannot reverse reliably
        //eg blocked 41 or unblocked 59
        if(infPossibilities[0] != infPossibilities[1]) return STARTING_VENT_VALUE;
        return getInfluenceOfValue(capVentValue(actualValue - movementDirection));
    }
    public void doReversedMovement(int outsideVentInfluence) {
        //TODO: Fix this code later to work with estimated ranges
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
    public boolean isBounded() {
        return actualValue == 100 || actualValue == 0;
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
}
