package com.ultimatevm;

import java.util.LinkedList;
import java.util.Iterator;

public class StabilityTracker {

    //Constants
    public static final int STARTING_STABILITY = 50;
    private static final int MAX_STABILITY = 100;
    private static final int MAX_STABILITY_UPDATES = 3;
    private static final int MIN_STABILITY_CHANGE = -25;
    private static final int MAX_STABILITY_CHANGE = 25;

    private boolean hasResetHistory = false;
    private int currentStability;
    private int numDisplay;
    private LinkedList<Integer> stabilityHistory = new LinkedList<>();

    public StabilityTracker() {
        initialize();
        numDisplay = 3;
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
        if(change > MAX_STABILITY_CHANGE || change < MIN_STABILITY_CHANGE) return false;

        currentStability = newStability;

        addChange(change);
        //If stability is max highly likely the change was truncated so this update is invalid
        return currentStability != MAX_STABILITY;
    }

    private int calcTrend() {
        if(stabilityHistory.size() < 2) return 0;
        int currentTrend = 0, nextChange = Integer.MIN_VALUE;
        Iterator<Integer> it = stabilityHistory.iterator();
        while(it.hasNext()) {
            int change = (Integer)it.next();
            if(nextChange != Integer.MIN_VALUE) {
                currentTrend += (nextChange - change);
            }
            nextChange = change;
        }
        return currentTrend / (MAX_STABILITY_UPDATES - 1);
    }

    public boolean isFutureStabilityBad(int stabilityThreshold) {
        int trend = 0;
//        int trend = calcTrend();
//        if(trend >= 0) return false;
        return getCurrentChange() + trend <= stabilityThreshold;
    }
    public String getStabilityText() {
        if(stabilityHistory.isEmpty()) return "";

        StringBuilder builder = new StringBuilder();
        Iterator<Integer> it = stabilityHistory.iterator();
        int numIterations = 0;
        while(it.hasNext()) {
            if(numIterations == numDisplay)
                return " (" + builder.toString() + ")";

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
    public void addChange(int change) {
        stabilityHistory.addFirst(change);
        while(stabilityHistory.size() > MAX_STABILITY_UPDATES) stabilityHistory.removeLast();
    }
    public void setDisplayCount(int count) { numDisplay = count;}
}
