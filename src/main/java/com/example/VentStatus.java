package com.example;

public class VentStatus {

    public static final int STARTING_VENT_VALUE = 127;
    public static final int MIN_VENT_VALUE = 0;
    public static final int PERFECT_VENT_VALUE = 50;
    public static final int MAX_VENT_VALUE = 100;
    private char ventName;
    private int actualValue;
    private int movementDirection;
    private int movementSinceLastState;

    private int lowerBoundStart, lowerBoundEnd;
    private int upperBoundStart, upperBoundEnd;

    public VentStatus(char name) {
        ventName = name;
        update(STARTING_VENT_VALUE, 0);
        clearMovement();
        lowerBoundStart = lowerBoundEnd = STARTING_VENT_VALUE;
        upperBoundStart = upperBoundEnd = STARTING_VENT_VALUE;
    }
    public VentStatus(VentStatus vent) {
        setEqualTo(vent);
    }

    public void doVMReset() {
        //Direction state will remain the same as before
        actualValue = STARTING_VENT_VALUE;
        clearMovement();
        lowerBoundStart = lowerBoundEnd = STARTING_VENT_VALUE;
        upperBoundStart = upperBoundEnd = STARTING_VENT_VALUE;
    }
    public void setEqualTo(VentStatus vent) {
        this.ventName = vent.ventName;
        this.actualValue = vent.actualValue;
        this.movementDirection = vent.movementDirection;
        this.movementSinceLastState = vent.movementSinceLastState;
        this.lowerBoundStart = vent.lowerBoundStart;
        this.lowerBoundEnd = vent.lowerBoundEnd;
        this.upperBoundStart = vent.upperBoundStart;
        this.upperBoundEnd = vent.upperBoundEnd;
    }
    public void update(int actualValue, int direction) {
        this.actualValue = actualValue;
        this.movementDirection = direction;
    }
    public void updateMovement() {
        movementSinceLastState += movementDirection;
        setLowerBoundRange(lowerBoundStart + movementDirection, lowerBoundEnd + movementDirection);
        setUpperBoundRange(upperBoundStart + movementDirection, upperBoundEnd + movementDirection);
    }
    public void clearMovement() {
        movementSinceLastState = 0;
    }
    public void setLowerBoundRange(int start, int end) {
        lowerBoundStart = capVentValue(start);
        lowerBoundEnd = capVentValue(end);
    }
    public void setUpperBoundRange(int start, int end) {
        upperBoundStart = capVentValue(start);
        upperBoundEnd = capVentValue(end);
    }

    public boolean isIdentified() { return actualValue != STARTING_VENT_VALUE; }
    public boolean isRangeDefined() {
        if(lowerBoundStart == STARTING_VENT_VALUE || lowerBoundEnd == STARTING_VENT_VALUE) return false;
        if(upperBoundStart == STARTING_VENT_VALUE || upperBoundEnd == STARTING_VENT_VALUE) return false;
        return true;
    }

    private int capVentValue(int value) { return Math.min(MAX_VENT_VALUE, Math.max(MIN_VENT_VALUE, value));}

    public char getName() { return ventName; }
    public int getActualValue() { return actualValue; }
    public int getDirection() { return movementDirection; }
    public int getLowerBoundStart() { return lowerBoundStart; }
    public int getLowerBoundEnd() { return lowerBoundEnd; }
    public int getUpperBoundStart() { return upperBoundStart; }
    public int getUpperBoundEnd() { return upperBoundEnd; }
    public int getMovementSinceLastState() { return movementSinceLastState; }
}
