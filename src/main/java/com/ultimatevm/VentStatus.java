package com.ultimatevm;

public class VentStatus {
    public static final int STARTING_VENT_VALUE = 127;
    public static final int MIN_VENT_VALUE = 0;
    public static final int PERFECT_VENT_VALUE = 50;
    public static final int MAX_VENT_VALUE = 100;
    public static final int MIN_STARTING_VENT_VALUE = 30;
    public static final int MAX_STARTING_VENT_VALUE = 70;
    public static final float VENT_STABILITY_WEIGHT = 16.0f;
    public static int BASE_MOVE_RATE = 2;
    public static int[][] pointsToLowerRanges = null, pointsToUpperRanges = null;


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
    private boolean isFreezeClipAccurate;
    private int lowerBoundStart, lowerBoundEnd;
    private int upperBoundStart, upperBoundEnd;
    private int totalBoundStart, totalBoundEnd;

    private static void makePointsToRangeTable() {
        if(pointsToLowerRanges != null && pointsToUpperRanges != null) return;
        pointsToLowerRanges = new int[(int)VENT_STABILITY_WEIGHT+1][2];
        pointsToUpperRanges = new int[(int)VENT_STABILITY_WEIGHT+1][2];

        //Mid values
        pointsToLowerRanges[16][0] = pointsToUpperRanges[16][0] = 47;
        pointsToLowerRanges[16][1] = pointsToUpperRanges[16][1] = 53;
        //Lower values
        pointsToLowerRanges[15][0] = 44; pointsToLowerRanges[15][1] = 46;
        pointsToLowerRanges[14][0] = 41; pointsToLowerRanges[14][1] = 43;
        pointsToLowerRanges[13][0] = 38; pointsToLowerRanges[13][1] = 40;
        pointsToLowerRanges[12][0] = 35; pointsToLowerRanges[12][1] = 37;
        pointsToLowerRanges[11][0] = 32; pointsToLowerRanges[11][1] = 34;
        pointsToLowerRanges[10][0] = 29; pointsToLowerRanges[10][1] = 31;
        pointsToLowerRanges[9][0] = 26; pointsToLowerRanges[9][1] = 28;
        pointsToLowerRanges[8][0] = 22; pointsToLowerRanges[8][1] = 25;
        pointsToLowerRanges[7][0] = 19; pointsToLowerRanges[7][1] = 21;
        pointsToLowerRanges[6][0] = 16; pointsToLowerRanges[6][1] = 18;
        pointsToLowerRanges[5][0] = 13; pointsToLowerRanges[5][1] = 15;
        pointsToLowerRanges[4][0] = 10; pointsToLowerRanges[4][1] = 12;
        pointsToLowerRanges[3][0] = 7; pointsToLowerRanges[3][1] = 9;
        pointsToLowerRanges[2][0] = 4; pointsToLowerRanges[2][1] = 6;
        pointsToLowerRanges[1][0] = 1; pointsToLowerRanges[1][1] = 3;
        pointsToLowerRanges[0][0] = pointsToLowerRanges[0][1] = 0;
        //Upper values
        pointsToUpperRanges[15][0] = 54; pointsToLowerRanges[15][1] = 56;
        pointsToUpperRanges[14][0] = 57; pointsToLowerRanges[14][1] = 59;
        pointsToUpperRanges[13][0] = 60; pointsToLowerRanges[13][1] = 62;
        pointsToUpperRanges[12][0] = 63; pointsToLowerRanges[12][1] = 65;
        pointsToUpperRanges[11][0] = 66; pointsToLowerRanges[11][1] = 68;
        pointsToUpperRanges[10][0] = 69; pointsToUpperRanges[10][1] = 71;
        pointsToUpperRanges[9][0] = 72; pointsToUpperRanges[9][1] = 74;
        pointsToUpperRanges[8][0] = 75; pointsToUpperRanges[8][1] = 78;
        pointsToUpperRanges[7][0] = 79; pointsToUpperRanges[7][1] = 81;
        pointsToUpperRanges[6][0] = 82; pointsToUpperRanges[6][1] = 84;
        pointsToUpperRanges[5][0] = 85; pointsToUpperRanges[5][1] = 87;
        pointsToUpperRanges[4][0] = 88; pointsToUpperRanges[4][1] = 90;
        pointsToUpperRanges[3][0] = 91; pointsToUpperRanges[3][1] = 93;
        pointsToUpperRanges[2][0] = 94; pointsToUpperRanges[2][1] = 96;
        pointsToUpperRanges[1][0] = 97; pointsToUpperRanges[1][1] = 99;
        pointsToUpperRanges[0][0] = pointsToUpperRanges[0][1] = 100;
    }
    public static int getStabilityInfluence(int ventValue) {
        float percentValue = PERFECT_VENT_VALUE - Math.abs(PERFECT_VENT_VALUE - ventValue);
        percentValue = (percentValue / 50.0f) * VENT_STABILITY_WEIGHT;
        return (int) Math.ceil(percentValue);
    }
    public static int getMovementInfluenceOfValue(int value) {
        if(value > 40 && value < 60) return -1;
        return 0;
    }
    public static int[] pointsToLowerRange(int points) {
        if(pointsToLowerRanges == null) return new int[]{-1, -1};
        if(points < 0 || points > (int)VENT_STABILITY_WEIGHT) return new int[]{-1, -1};
        return pointsToLowerRanges[points];
    }
    public static int[] pointsToUpperRange(int points) {
        if(pointsToUpperRanges == null) return new int[]{-1, -1};
        if(points < 0 || points > (int)VENT_STABILITY_WEIGHT) return new int[]{-1, -1};
        return pointsToUpperRanges[points];
    }

    public VentStatus(char name) {
        makePointsToRangeTable();
        ventName = name;
        this.movementDirection = 0;
        actualValue = STARTING_VENT_VALUE;
        totalBoundStart = MIN_STARTING_VENT_VALUE;
        totalBoundEnd = MAX_STARTING_VENT_VALUE;
        setStartingRanges();
    }
    public VentStatus(VentStatus vent) {
        setEqualTo(vent);
    }

    public void doVMReset() {
        //Direction state will remain the same as before
        actualValue = STARTING_VENT_VALUE;
        totalBoundStart = MIN_VENT_VALUE;
        totalBoundEnd = MAX_VENT_VALUE;
        setStartingRanges();
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
        this.isFreezeClipAccurate = vent.isFreezeClipAccurate;
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
            isFreezeClipAccurate = false;
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
    public void updateMovement(int[] outsideVentInfluence) {
        if(isIdentified()) return;
        //Get our possible movement amounts
        int currentMinMoveRate = BASE_MOVE_RATE + outsideVentInfluence[0];
        int currentMaxMoveRate = BASE_MOVE_RATE + outsideVentInfluence[1];

        //Consider all movement possibilities when moving ranges
        int startRangeMoveRate, endRangeMoveRate;
        if(getDirection() > 0) {
            //upward movement
            endRangeMoveRate = currentMaxMoveRate;
            startRangeMoveRate = currentMinMoveRate;
        } else {
            //downward movement
            startRangeMoveRate = currentMaxMoveRate;
            endRangeMoveRate = currentMinMoveRate;
        }
        //Update total bounds even if range is not defined
        int totalBoundStartMove = Math.max(0, (startRangeMoveRate + getMovementInfluenceOfValue(totalBoundStart))) * movementDirection;
        totalBoundStart = capVentValue(totalBoundStart + totalBoundStartMove);

        int totalBoundEndMove = Math.max(0, (endRangeMoveRate + getMovementInfluenceOfValue(totalBoundEnd))) * movementDirection;
        totalBoundEnd = capVentValue(totalBoundEnd + totalBoundEndMove);

        if(!isRangeDefined()) return;
        //Update our current ranges
        int lowerStart = getLowerBoundStart();
        int lowerStartMove = Math.max(0, (startRangeMoveRate + getMovementInfluenceOfValue(lowerStart))) * movementDirection;

        int upperEnd = getUpperBoundEnd();
        int upperEndMove = Math.max(0, (endRangeMoveRate + getMovementInfluenceOfValue(upperEnd))) * movementDirection;

        int lowerEnd = getLowerBoundEnd();
        int lowerEndMove = Math.max(0, (endRangeMoveRate + getMovementInfluenceOfValue(lowerEnd))) * movementDirection;

        int upperStart = getUpperBoundStart();
        int upperStartMove = Math.max(0, (startRangeMoveRate + getMovementInfluenceOfValue(upperStart))) * movementDirection;

        clearRanges();
        setLowerBoundRange(lowerStart + lowerStartMove, lowerEnd + lowerEndMove);
        setUpperBoundRange(upperStart + upperStartMove, upperEnd + upperEndMove);
    }
    public void clearRanges() {
        lowerBoundStart = lowerBoundEnd = STARTING_VENT_VALUE;
        upperBoundStart = upperBoundEnd = STARTING_VENT_VALUE;
    }
    public boolean canLowerBoundMergeWith(int start, int end) {
        return isLowerBoundWithinRange(start-1, end+1);
    }
    public boolean canUpperBoundMergeWith(int start, int end) {
        return isUpperBoundWithinRange(start-1, end+1);
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
        if(canUpperBoundMergeWith(lowerBoundStart, lowerBoundEnd)) {
            mergeUpperBoundRanges(lowerBoundStart, lowerBoundEnd);
            mergeLowerBoundRanges(upperBoundStart, upperBoundEnd);
        }
    }
    public void setUpperBoundRange(int start, int end) {
        upperBoundStart = capVentValue(start);
        upperBoundEnd = capVentValue(end);
        //Merge ranges if they are both within bounds
        if(canLowerBoundMergeWith(upperBoundStart, upperBoundEnd)) {
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
    public void updateEstimatedMovementInfluence(int[] currentInf) {
        int minInf, maxInf;
        if(!isRangeDefined()) {
            //Assume 0-100 which means both -1 and 0 influence
            minInf = -1; maxInf = 0;
        } else {
            boolean canInfluence = isWithinRange(41,59);
            boolean canNotInfluence = isWithinRange(0, 40) || isWithinRange(60, 100);
            if(canInfluence && canNotInfluence) {
                //This vent has the possibility to both slow and not slow movement
                minInf = -1; maxInf = 0;
            }
            else if(canInfluence) {
                //This vent only has the possibility to slow movement
                minInf = maxInf = -1;
            }
            else if(canNotInfluence) {
                //This vent cannot influence movement
                minInf = maxInf = 0;
            }
            else {
                //Somehow invalid range - should be impossible
                //Assume 0-100 which means both -1 and 0 influence
                minInf = -1; maxInf = 0;
            }
        }
        currentInf[0] += minInf;
        currentInf[1] += maxInf;
    }

    public int getReversedInfluence(int outsideVentInfluence) {
        //We know the value is the same as before so we can exit safely
        if(outsideVentInfluence < -1)
            return isIdentified() ? getMovementInfluenceOfValue(capVentValue(actualValue)) : 0;
        //Cannot reverse a blank value
        if(!isRangeDefined()) return STARTING_VENT_VALUE;

        if(!isIdentified() && !isFreezeClipAccurate()) {
            //Our ranges are estimated
            if(!isTwoSeperateValues()) {
                //Exit on huge single ranges that border on freeze non-freeze
                if(lowerBoundStart < 41 && lowerBoundEnd > 59) return STARTING_VENT_VALUE;

                int lowerStartInf = determineReversedInfluence(outsideVentInfluence, lowerBoundStart);
                if(lowerStartInf == STARTING_VENT_VALUE) return STARTING_VENT_VALUE;
                //Can simply exit since this is a single estimated value
                if(isLowerBoundSingleValue()) return lowerStartInf;

                int lowerEndInf = determineReversedInfluence(outsideVentInfluence, lowerBoundEnd);
                if(lowerEndInf == STARTING_VENT_VALUE) return STARTING_VENT_VALUE;
                //Exit on influence mismatch
                if(lowerStartInf != lowerEndInf) return STARTING_VENT_VALUE;
                return lowerStartInf;
            }

            //We have two seperate estimated ranges
            int lowerStartInf = determineReversedInfluence(outsideVentInfluence, lowerBoundStart);
            if(lowerStartInf == STARTING_VENT_VALUE) return STARTING_VENT_VALUE;

            if(!isLowerBoundSingleValue()) {
                int lowerEndInf = determineReversedInfluence(outsideVentInfluence, lowerBoundEnd);
                if(lowerEndInf == STARTING_VENT_VALUE) return STARTING_VENT_VALUE;
                //Exit on influence mismatch
                if(lowerStartInf != lowerEndInf) return STARTING_VENT_VALUE;
            }

            int upperStartInf = determineReversedInfluence(outsideVentInfluence, upperBoundStart);
            if(upperStartInf == STARTING_VENT_VALUE) return STARTING_VENT_VALUE;
            //Exit on influence mismatch
            if(lowerStartInf != upperStartInf) return STARTING_VENT_VALUE;

            if(!isUpperBoundSingleValue()) {
                int upperEndInf = determineReversedInfluence(outsideVentInfluence, upperBoundEnd);
                if(upperEndInf == STARTING_VENT_VALUE) return STARTING_VENT_VALUE;
                //Exit on influence mismatch
                if(upperStartInf != upperEndInf) return STARTING_VENT_VALUE;
            }
            return lowerStartInf;
        }

        //We are dealing with a known single value
        //We cannot reverse bounded values; dont know how long they been bounded
        if(isBounded()) return STARTING_VENT_VALUE;
        //Attempt to reverse this value and determine influence
        int value = isFreezeClipAccurate() ? getLowerBoundStart() : actualValue;
        return determineReversedInfluence(outsideVentInfluence, value);
    }
    public void doReversedMovement(int outsideVentInfluence) {
        if(!isRangeDefined()) return;
        int currentMoveRate = Math.max(0, BASE_MOVE_RATE + outsideVentInfluence) * movementDirection;
        if(!isIdentified() && !isFreezeClipAccurate()) {
            if(!isBounded(lowerBoundStart)) lowerBoundStart = capVentValue(getLowerBoundStart() - currentMoveRate);
            if(!isBounded(lowerBoundEnd)) lowerBoundEnd = capVentValue(getLowerBoundEnd() - currentMoveRate);
            if(!isBounded(upperBoundStart)) upperBoundStart = capVentValue(getUpperBoundStart() - currentMoveRate);
            if(!isBounded(upperBoundEnd)) upperBoundEnd = capVentValue(getUpperBoundEnd() - currentMoveRate);
            return;
        }
        if(isIdentified()) actualValue = capVentValue(actualValue - currentMoveRate);
        int value = isFreezeClipAccurate() ? capVentValue(getLowerBoundStart() - currentMoveRate) : actualValue;
        lowerBoundStart = lowerBoundEnd = value;
        upperBoundStart = upperBoundEnd = value;
    }

    public int getStabilityInfluence() {
        if(isFreezeClipAccurate()) return getStabilityInfluence(lowerBoundStart);
        if(!isIdentified()) return 0;
        return getStabilityInfluence(actualValue);
    }
    public boolean isBounded() {
        return isBounded(actualValue);
    }
    public void makeFreezeClipAccurate() {
        if(ventName != 'A') return;
        if(getLowerBoundStart() != 40 && getLowerBoundStart() != 60) return;
        if(!canBeFreezeClipAccurate()) return;
        isFreezeClipAccurate = true;
    }

    //Helpers
    private int capVentValue(int value) { return Math.min(MAX_VENT_VALUE, Math.max(MIN_VENT_VALUE, value));}
    private void setStartingRanges() {
        isFreezeClipAccurate = false;
        clearRanges();
        setLowerBoundRange(totalBoundStart, totalBoundEnd);
        setUpperBoundRange(totalBoundStart, totalBoundEnd);
    }
    private boolean isBounded(int value) {
        return value == 100 || value == 0;
    }
    private int determineReversedInfluence(int outsideVentInfluence, int value) {
        int[] infPossibilities = new int[BASE_MOVE_RATE];
        for(int i = 0; i < BASE_MOVE_RATE; ++i) {
            int move = (outsideVentInfluence + (BASE_MOVE_RATE - i)) * movementDirection;
            infPossibilities[i] = getMovementInfluenceOfValue(capVentValue(value - move));
        }
        //Exit on freeze, non-freeze mismatch cannot reverse reliably
        //eg blocked 41 or unblocked 59
        if(infPossibilities[0] != infPossibilities[1]) return STARTING_VENT_VALUE;
        int move = Math.max(0, (outsideVentInfluence + BASE_MOVE_RATE)) * movementDirection;
        return getMovementInfluenceOfValue(capVentValue(value - move));
    }


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
    public boolean canBeFreezeClipAccurate() {
        if(isIdentified()) return false;
        if(isTwoSeperateValues()) return false;
        return (isLowerBoundSingleValue() || isUpperBoundSingleValue());
    }
    public boolean isFreezeClipAccurate() {
        if(!canBeFreezeClipAccurate()) return false;
        return isFreezeClipAccurate;
    }
}
