package com.example;

import java.util.HashSet;

public class CapCounter {
    private final int CAP_POINT_GAIN = 50;
    private HashSet<Integer> seenChambers = new HashSet<>();
    private HashSet<Integer> possibleCapPositions = new HashSet<>();
    private int timesCapped;
    private int currentScore;

    CapCounter() {
        initialize();
    }
    public void initialize() {
        seenChambers.clear();
        possibleCapPositions.clear();
        currentScore = timesCapped = 0;
    }
    public void addCappingPositions(int chamberX, int chamberY) {
        int positionCode = (chamberX << 16) | chamberY;
        if(seenChambers.contains(positionCode)) return;
        seenChambers.add(positionCode);

        possibleCapPositions.add(((chamberX+1) << 16) | (chamberY-1));
        possibleCapPositions.add((chamberX << 16) | (chamberY-1));
        possibleCapPositions.add(((chamberX+2) << 16) | chamberY);
        possibleCapPositions.add(((chamberX+2) << 16) | (chamberY+1));
        possibleCapPositions.add(((chamberX+1) << 16) | (chamberY+2));
        possibleCapPositions.add((chamberX << 16) | (chamberY+2));
        possibleCapPositions.add(((chamberX-1) << 16) | (chamberY+1));
        possibleCapPositions.add(((chamberX-1) << 16) | chamberY);
    }
    public boolean updateScore(int newScore, int playerX, int playerY) {
        int scoreDiff = newScore - currentScore;
        currentScore = newScore;
        //Player has to be in specific positions in order to cap
        if(!possibleCapPositions.contains((playerX << 16) | playerY))
            return false;

        if(scoreDiff == CAP_POINT_GAIN) ++timesCapped;
        return (scoreDiff == CAP_POINT_GAIN);
    }
    public int getTimesCapped() {
        return timesCapped;
    }
}
