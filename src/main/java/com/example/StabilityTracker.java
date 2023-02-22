package com.example;

import java.util.LinkedList;
import java.util.Iterator;

public class StabilityTracker {

    //Constants
    private static final int STARTING_STABILITY = 50;
    private static final int MAX_STABILITY = 100;
    private static final int MAX_STABILITY_UPDATES = 3;

    private boolean hasResetHistory = false;
    private int currentStability;
    private LinkedList<Integer> stabilityHistory = new LinkedList<>();

    public StabilityTracker() {
        initialize();
    }

    public void initialize() {
        currentStability = STARTING_STABILITY;
        stabilityHistory.clear();
        hasResetHistory = false;
    }

    public void resetStabilityHistory() {
        if(hasResetHistory) return;
        stabilityHistory.clear();
        hasResetHistory = true;
    }

    public boolean updateStability(int newStability) {
        if(currentStability == newStability) return false;
        int change = newStability - currentStability;
        currentStability = newStability;

        stabilityHistory.addFirst(change);
        while(stabilityHistory.size() > MAX_STABILITY_UPDATES) stabilityHistory.removeLast();
        //If stability is max highly likely the change was truncated so this update is invalid
        return currentStability != MAX_STABILITY;
    }

    public String getStabilityText() {
        if(stabilityHistory.isEmpty()) return "";

        StringBuilder builder = new StringBuilder();
        Iterator it = stabilityHistory.iterator();
        int numIterations = 0;
        while(it.hasNext()) {
            if(++numIterations > 1) builder.append(",");

            int change = (Integer)it.next();
            if (change >= 0) {
                builder.append("<col=00ff00>").append(change).append("</col>");
            } else {
                builder.append("<col=ff0000>").append(Math.abs(change)).append("</col>");
            }
        }
        return " (" + builder.toString() + ")";
    }
    public int getCurrentStability() { return currentStability; }
    public int getCurrentChange() {
        if(stabilityHistory.isEmpty()) return 0;
        return stabilityHistory.getFirst();
    }
}
