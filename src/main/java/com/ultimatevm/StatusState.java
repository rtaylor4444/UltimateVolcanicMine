package com.ultimatevm;

import java.util.ArrayList;

import static com.ultimatevm.VentStatus.*;

public class StatusState {
    private static final char[] VENT_TAGS = {'A', 'B', 'C'};
    public static final int NUM_VENTS = 3;
    public static final int STABILITY_CHANGE_CONSTANT = -25;

    public static final int TRUNCATION_POSSIBILITIES = NUM_VENTS;

    private VentStatus[] vents = new VentStatus[NUM_VENTS];
    private int numIdentifiedVents;
    private int stabilityChange;
    private boolean hasReset;

    public static int getTotalVentUpdate(int change) {
        return (change - STABILITY_CHANGE_CONSTANT);
    }
    public static int calcStabilityChange(StatusState state) {
        return calcStabilityChange(state.getIdentifiedVentTotalValue());
    }
    public static int calcStabilityChange(int totalVentInfluence) {
        return STABILITY_CHANGE_CONSTANT + totalVentInfluence;
    }

    public StatusState() {
        hasReset = false;
        numIdentifiedVents = 0;
        for(int i = 0; i < vents.length; ++i) {
            vents[i] = new VentStatus(VENT_TAGS[i]);
        }
    }
    public StatusState(StatusState state) {
        for(int i = 0; i < vents.length; ++i) {
            vents[i] = new VentStatus(VENT_TAGS[i]);
        }
        setEqualTo(state);
    }
    public void setVentEqualTo(StatusState state, int ventIndex) {
        //Update number of vents we have identified
        if(vents[ventIndex].isIdentified() && !state.vents[ventIndex].isIdentified())
            --numIdentifiedVents;
        if(!vents[ventIndex].isIdentified() && state.vents[ventIndex].isIdentified())
            ++numIdentifiedVents;

        vents[ventIndex].setEqualTo(state.vents[ventIndex]);
    }
    public void setVentsEqualTo(StatusState state) {
        for(int i = 0; i < vents.length; ++i) {
            setVentEqualTo(state, i);
        }
    }
    public void setEqualTo(StatusState state) {
        this.hasReset = state.hasReset;
        this.stabilityChange = state.stabilityChange;
        setVentsEqualTo(state);
    }

    public int[] updateVentStatus(int[] ventStatus, int chambers) {
        numIdentifiedVents = 0;
        int[] changeStates = new int[NUM_VENTS];
        for(int i = 0; i < ventStatus.length; ++i) {
            changeStates[i] = vents[i].update(ventStatus[i], getDirectionFromChambers(i, chambers));
            if(vents[i].isIdentified()) ++numIdentifiedVents;
        }
        return changeStates;
    }
    public void updateVentMovement() {
        int currentVentInfluence = 0;
        for(int i = 0; i < vents.length; ++i) {
            vents[i].updateMovement(currentVentInfluence);
            currentVentInfluence += vents[i].getEstimatedInfluence();
        }
    }
    public int reverseMovement(int knownBitFlag) {
        int currentVentInfluence = 0;
        for(int i = 0; i < vents.length; ++i) {
            //Do reversed movement only if the previous value is unknown
            if((knownBitFlag & (1 << i)) == 0) {
                //Exit if the value cannot be reversed
                int inf = vents[i].getReversedInfluence(currentVentInfluence);
                if (inf == STARTING_VENT_VALUE) return -(i + 1);

                currentVentInfluence += inf;
                vents[i].doReversedMovement(currentVentInfluence);
            }
            //Otherwise if known just update movement influence
            else currentVentInfluence += vents[i].getEstimatedInfluence();
        }
        return 0;
    }
    public void mergePredictedRangesWith(StatusState state) {
        for(int i = 0; i < NUM_VENTS; ++i) {
            if(vents[i].isIdentified()) continue;
            if(!state.vents[i].isRangeDefined()) continue;
            mergeVentWith(i, state.vents[i]);
        }
    }
    public void setOverlappingRangesWith(StatusState state) {
        for(int i = 0; i < NUM_VENTS; ++i) {
            if(vents[i].isIdentified()) continue;
            if(!vents[i].isRangeDefined()) continue;
            if(!state.vents[i].isRangeDefined()) continue;
            overlapVentWith(i, state.vents[i]);
        }
    }
    public void doFreezeClipping(int moveBitState) {
        for(int i = 0; i < NUM_VENTS; ++i) {
            int curMove = moveBitState & (3 << (i * 2));
            curMove = (curMove >> (i * 2));
            switch(vents[i].getName()) {
                case 'A':
                    //Cant freeze clip based on A's movement
                    break;
                case 'B':
                    //Skip if not identified movement wont be accurate
                    if(!vents[i].isIdentified()) continue;
                    if(curMove == 0) {
                        //Skip if bounded
                        if(vents[i].isBounded()) continue;
                        //B cannot freeze unless A is 41-59
                        vents[0].doInnerBoundsClipping(41, 59);
                    } else if(curMove == 1) {
                        //Skip if B could be bounded
                        int actualValue = vents[i].getActualValue();
                        if(actualValue == 1 || actualValue == 99) return;
                        //If B is 41-59 A must be outside of 41-59
                        if(vents[i].isWithinRange(41, 59)) vents[0].doOuterBoundsClipping(41, 59);
                        //Otherwise A must be 41-59 to slow B's movement
                        else vents[0].doInnerBoundsClipping(41, 59);
                    } else if(curMove == 2) {
                        //B cannot have full movement unless A is not 41-59
                        vents[0].doOuterBoundsClipping(41, 59);
                    }
                    break;
                case 'C':
                    //Skip if not identified movement wont be accurate
                    if(!vents[i].isIdentified()) continue;
                    if(curMove == 0) {
                        //Skip if bounded
                        if(vents[i].isBounded()) continue;
                        //If C is outside 41-59 then both A and B must be 41-59
                        if(!vents[i].isWithinRange(41, 59)) {
                            vents[0].doInnerBoundsClipping(41, 59);
                            vents[1].doInnerBoundsClipping(41, 59);
                        }
                    }
                    else if(curMove == 1) {
                        if(vents[i].isWithinRange(41, 59)) {
                            //C cannot have full movement unless A and B arent 41-59
                            vents[0].doOuterBoundsClipping(41, 59);
                            vents[1].doOuterBoundsClipping(41, 59);
                        }
                    }
                    else if(curMove == 2) {
                        //C cannot have full movement unless A and B arent 41-59
                        vents[0].doOuterBoundsClipping(41, 59);
                        vents[1].doOuterBoundsClipping(41, 59);
                    }
                    break;
            }
        }
    }
    public void setFreezeRanges(int moveBitState) {
        for(int i = 0; i < NUM_VENTS; ++i) {
            int curMove = moveBitState & (3 << (i * 2));
            curMove = (curMove >> (i * 2));
            switch(vents[i].getName()) {
                case 'A':
                    //Cant set any vents based on A's movement
                    break;
                case 'B':
                    //Skip if not identified movement wont be accurate
                    if(!vents[i].isIdentified()) continue;
                    if(vents[0].isRangeDefined()) continue;
                    if(curMove == 0) {
                        //Skip if bounded
                        if(vents[i].isBounded()) continue;
                        //B cannot freeze unless A is 41-59
                        vents[0].setLowerBoundRange(41, 59);
                        vents[0].setUpperBoundRange(41, 59);
                    } else if(curMove == 1) {
                        //Skip if B could be bounded
                        int actualValue = vents[i].getActualValue();
                        if(actualValue == 1 || actualValue == 99) return;
                        //If B is 41-59 A must be outside of 41-59
                        //TODO: Set large range A - for now skip until predicted stability clipping
                        if(vents[i].isWithinRange(41, 59)) {}
                            //Otherwise A must be 41-59 to slow B's movement
                        else {
                            vents[0].setLowerBoundRange(41, 59);
                            vents[0].setUpperBoundRange(41, 59);
                        }
                    } else if(curMove == 2) {
                        //B cannot have full movement unless A is not 41-59
                        //TODO: Set large range A - for now skip until predicted stability clipping
                    }
                    break;
                case 'C':
                    //Skip if not identified movement won't be accurate
                    if(!vents[i].isIdentified()) continue;
                    if(vents[0].isRangeDefined() && vents[1].isRangeDefined()) continue;
                    if(curMove == 0) {
                        //Skip if bounded
                        if(vents[i].isBounded()) continue;
                        //If C is outside 41-59 then both A and B must be 41-59
                        if(!vents[i].isWithinRange(41, 59)) {
                            if(!vents[0].isRangeDefined()) {
                                vents[0].setLowerBoundRange(41, 59);
                                vents[0].setUpperBoundRange(41, 59);
                            }

                            if(!vents[1].isRangeDefined()) {
                                vents[1].setLowerBoundRange(41, 59);
                                vents[1].setUpperBoundRange(41, 59);
                            }
                        }
                    }
                    else if(curMove == 1) {
                        if(vents[i].isWithinRange(41, 59)) {
                            //C cannot have full movement unless A and B arent 41-59
                            //TODO: Set large range AB - for now skip until predicted stability clipping
                        }
                    }
                    else if(curMove == 2) {
                        //C cannot have full movement unless A and B arent 41-59
                        //TODO: Set large range AB - for now skip until predicted stability clipping
                    }
                    break;
            }
        }
    }
    public void forceReset() {
        numIdentifiedVents = 0;
        for(int i = 0; i < vents.length; ++i) {
            vents[i].doVMReset();
        }
    }
    public void doVMReset() {
        if(hasReset) return;
        forceReset();
        hasReset = true;
    }
    public void doHalfSpaceClipping(int ventsToClip, int clipInfo) {
        for(int i = 0; i < NUM_VENTS; ++i) {
            if(vents[i].isIdentified()) continue;
            if((ventsToClip & (1 << i)) == 0) continue;

            int ventDirection = vents[i].getDirection();
            //0 - up, 1 - down
            boolean downwardClip = ((clipInfo & (1 << i)) != 0);

            //Vent percent is moving down
            if(ventDirection < 0) {
                if(downwardClip) vents[i].doInnerBoundsClipping(0, 53);
                else vents[i].doInnerBoundsClipping(47, 100);
            }
            //Vent percent is moving up
            else if(ventDirection > 0) {
                if(downwardClip) vents[i].doInnerBoundsClipping(47, 100);
                else vents[i].doInnerBoundsClipping(0, 53);
            }
        }
    }
    public int[] getUnidentifiedVentIndices() {
        int curIndex = 0;
        int[] indices = new int[NUM_VENTS - numIdentifiedVents];
        for(int i = 0; i < vents.length; ++i) {
            if(this.vents[i].isIdentified()) continue;
            indices[curIndex++] = i;
        }
        return indices;
    }
    public boolean calcPredictedVentValues(int change) {
        stabilityChange = change;
        if(isAllVentsIdentified()) return false;
        if(!isEnoughVentsIdentified()) return false;
        int[] indices = getUnidentifiedVentIndices();
        if(numIdentifiedVents == 1) return calcDoubleVentValue(new VentStatus[]{vents[indices[0]], vents[indices[1]]}, change);
        return calcSingleVentValue(vents[indices[0]], change);
    }
    public void alignPredictedRangesWith(StatusState state) {
        for(int i = 0; i < NUM_VENTS; ++i) {
            if(vents[i].isIdentified()) continue;
            if(!state.vents[i].isRangeDefined()) continue;

            if(!vents[i].isRangeDefined()) mergeVentWith(i, state.vents[i]);
            else overlapVentWith(i, state.vents[i]);
        }
    }
    public void clipPredictedStabilityMismatch(int stabilityAmount) {
        if(numIdentifiedVents != 2) return;
        int ventIndex = getUnidentifiedVentIndices()[0];
        if(!vents[ventIndex].isTwoSeperateValues()) return;
        int partialVentUpdate = getIdentifiedVentTotalValue();

        //Exit if both changes are equal the stability amount or are equal
        int lowerBoundStart = vents[ventIndex].getLowerBoundStart();
        int lowerBoundStability = calcStabilityChange(partialVentUpdate + getStabilityInfluence(lowerBoundStart));
        int upperBoundEnd = vents[ventIndex].getUpperBoundEnd();
        int upperBoundStability = calcStabilityChange(partialVentUpdate + getStabilityInfluence(upperBoundEnd));
        if(lowerBoundStability >= stabilityAmount && upperBoundStability >= stabilityAmount) return;
        if(lowerBoundStability == upperBoundStability) return;

        //Check and clip the range with the lowest stability value
        boolean clipLowerBound = lowerBoundStability < upperBoundStability;
        int boundStart, boundEnd;
        if(clipLowerBound) {
            boundStart = lowerBoundStart;
            boundEnd = vents[ventIndex].getLowerBoundEnd();
            for(; boundStart <= boundEnd; ++boundStart) {
                int change = calcStabilityChange(partialVentUpdate + getStabilityInfluence(boundStart));
                if(change >= stabilityAmount) break;
            }
            if(boundStart > boundEnd) {
                int upperBoundStart = vents[ventIndex].getUpperBoundStart();
                vents[ventIndex].clearRanges();
                vents[ventIndex].setLowerBoundRange(upperBoundStart, upperBoundEnd);
                vents[ventIndex].setUpperBoundRange(upperBoundStart, upperBoundEnd);
            }
            else vents[ventIndex].setLowerBoundRange(boundStart, boundEnd);
        } else {
            boundStart = vents[ventIndex].getUpperBoundStart();
            boundEnd = upperBoundEnd;
            for(; boundStart <= boundEnd; --boundEnd) {
                int change = calcStabilityChange(partialVentUpdate + getStabilityInfluence(boundEnd));
                if(change >= stabilityAmount) break;
            }
            if(boundStart > boundEnd) {
                int lowerBoundEnd = vents[ventIndex].getLowerBoundEnd();
                vents[ventIndex].clearRanges();
                vents[ventIndex].setLowerBoundRange(lowerBoundStart, lowerBoundEnd);
                vents[ventIndex].setUpperBoundRange(lowerBoundStart, lowerBoundEnd);
            }
            else vents[ventIndex].setUpperBoundRange(boundStart, boundEnd);
        }
    }
    public int getFutureStabilityChange(UltimateVolcanicMineConfig.PredictionScenario scenario) {
        if(numIdentifiedVents < NUM_VENTS - 1) return STARTING_VENT_VALUE;
        int totalVentValue = 0;
        ArrayList<VentStatus> estimatedVents = new ArrayList<>();
        for(int i = 0; i < NUM_VENTS; ++i) {
            if(!vents[i].isRangeDefined())
                return STARTING_VENT_VALUE;
            if(vents[i].isIdentified())
                totalVentValue += vents[i].getStabilityInfluence();
            else
                estimatedVents.add(vents[i]);
        }

        int estimatedVentValue = Integer.MAX_VALUE;
        for(int i = 0; i < estimatedVents.size(); ++i) {
            VentStatus vent = estimatedVents.get(i);
            int avgLower = (vent.getLowerBoundEnd() + vent.getLowerBoundStart()) / 2;
            int avgUpper = (vent.getUpperBoundStart() + vent.getUpperBoundEnd()) / 2;
            int ventUpdate = 0;

            switch(scenario) {
                case WORST_CASE:
                    ventUpdate = Math.min(getStabilityInfluence(avgLower), getStabilityInfluence(avgUpper));
                    break;
                case BEST_CASE:
                    ventUpdate = Math.max(getStabilityInfluence(avgLower), getStabilityInfluence(avgUpper));
                    break;
                default:
                    //Average-case (crap)
                    ventUpdate = (getStabilityInfluence(avgLower) + getStabilityInfluence(avgUpper)) / 2;
                    break;
            }

            if(estimatedVentValue == Integer.MAX_VALUE) estimatedVentValue = ventUpdate;
            else estimatedVentValue += ventUpdate;
        }

        if(estimatedVentValue != Integer.MAX_VALUE)
            totalVentValue += estimatedVentValue;
        return calcStabilityChange(totalVentValue) + StabilityUpdateInfo.getMinRNGVariation();
    }

    //Helpers
    private boolean calcSingleVentValue(VentStatus vent, int change) {
        int partialVentUpdate = getIdentifiedVentTotalValue();
        int pointsNeeded = getTotalVentUpdate(change) - partialVentUpdate;
        //Exit if the value we need is out of range - stability change is invalid
        if(pointsNeeded < 0 || pointsNeeded > (int)VENT_STABILITY_WEIGHT) return false;

        float missingInversePercent = 1.0f - (pointsNeeded / VENT_STABILITY_WEIGHT);
        int missingVentUpdate = (int)Math.ceil(PERFECT_VENT_VALUE * missingInversePercent);

        int lowerBoundStart = (PERFECT_VENT_VALUE - TRUNCATION_POSSIBILITIES) - missingVentUpdate;
        int lowerBoundEnd = (PERFECT_VENT_VALUE + TRUNCATION_POSSIBILITIES) - missingVentUpdate;
        int upperBoundStart = (PERFECT_VENT_VALUE - TRUNCATION_POSSIBILITIES) + missingVentUpdate;
        int upperBoundEnd = (PERFECT_VENT_VALUE + TRUNCATION_POSSIBILITIES) + missingVentUpdate;
        while(lowerBoundStart < lowerBoundEnd) {
            int newChange1 = calcStabilityChange(partialVentUpdate + getStabilityInfluence(lowerBoundStart));
            int newChange2 = calcStabilityChange(partialVentUpdate + getStabilityInfluence(lowerBoundEnd));
            if(newChange1 == change && newChange2 == change) break;

            if(newChange1 != change) {
                ++lowerBoundStart; --upperBoundEnd;
            }
            if(newChange2 != change) {
                --lowerBoundEnd; ++upperBoundStart;
            }
        }

        vent.clearRanges();
        vent.setLowerBoundRange(lowerBoundStart, lowerBoundEnd);
        vent.setUpperBoundRange(upperBoundStart, upperBoundEnd);
        return true;
    }
    private boolean calcDoubleVentValue(VentStatus[] vents, int change) {
        int partialVentUpdate = getIdentifiedVentTotalValue();
        int pointsNeeded = getTotalVentUpdate(change) - partialVentUpdate;
        //Exit if the value we need is out of range - stability change is invalid
        if(pointsNeeded < 0 || pointsNeeded > (int)VENT_STABILITY_WEIGHT * 2) return false;

        int totalLowerBoundStart = STARTING_VENT_VALUE;
        int totalLowerBoundEnd = STARTING_VENT_VALUE;
        int totalUpperBoundStart = STARTING_VENT_VALUE;
        int totalUpperBoundEnd = STARTING_VENT_VALUE;

        //Try all of the possible double vent combos
        for(int takenPoints = 0; takenPoints <= (int)VENT_STABILITY_WEIGHT; ++takenPoints) {
            int remainingPoints = pointsNeeded - takenPoints;
            if(remainingPoints < 0 || remainingPoints > (int)VENT_STABILITY_WEIGHT) continue;
            float missingInversePercent = 1.0f - (takenPoints / VENT_STABILITY_WEIGHT);
            int missingVentUpdate = (int)Math.ceil(PERFECT_VENT_VALUE * missingInversePercent);

            int maxDistance = Math.min(MAX_VENT_VALUE - PERFECT_VENT_VALUE, missingVentUpdate);
            int minDistance = Math.max(0, missingVentUpdate - PERFECT_VENT_VALUE);

            int lowerBoundStart = (PERFECT_VENT_VALUE - TRUNCATION_POSSIBILITIES) - maxDistance;
            int lowerBoundEnd = (PERFECT_VENT_VALUE + TRUNCATION_POSSIBILITIES) - minDistance;
            int upperBoundStart = (PERFECT_VENT_VALUE - TRUNCATION_POSSIBILITIES) + minDistance;
            int upperBoundEnd = (PERFECT_VENT_VALUE + TRUNCATION_POSSIBILITIES) + maxDistance;

            //Get full possible range values
            while(lowerBoundStart < lowerBoundEnd) {
                int newChange1 = calcStabilityChange(partialVentUpdate + remainingPoints + getStabilityInfluence(lowerBoundStart));
                int newChange2 = calcStabilityChange(partialVentUpdate + remainingPoints + getStabilityInfluence(lowerBoundEnd));
                if(newChange1 == change && newChange2 == change) break;

                if(newChange1 != change) {
                    ++lowerBoundStart; --upperBoundEnd;
                }
                if(newChange2 != change) {
                    --lowerBoundEnd; ++upperBoundStart;
                }
            }

            //Update our total bounds
            if(totalLowerBoundStart == STARTING_VENT_VALUE) totalLowerBoundStart = lowerBoundStart;
            else totalLowerBoundStart = Math.min(totalLowerBoundStart, lowerBoundStart);

            if(totalLowerBoundEnd == STARTING_VENT_VALUE) totalLowerBoundEnd = lowerBoundEnd;
            else totalLowerBoundEnd = Math.max(totalLowerBoundEnd, lowerBoundEnd);

            if(totalUpperBoundStart == STARTING_VENT_VALUE) totalUpperBoundStart = upperBoundStart;
            else totalUpperBoundStart = Math.min(totalUpperBoundStart, upperBoundStart);

            if(totalUpperBoundEnd == STARTING_VENT_VALUE) totalUpperBoundEnd = upperBoundEnd;
            else totalUpperBoundEnd = Math.max(totalUpperBoundEnd, upperBoundEnd);
        }

        //Set both vents accordingly
        for(int i = 0; i < vents.length; ++i) {
            vents[i].clearRanges();
            vents[i].setLowerBoundRange(totalLowerBoundStart, totalLowerBoundEnd);
            vents[i].setUpperBoundRange(totalUpperBoundStart, totalUpperBoundEnd);
        }
        return true;
    }
    private int getDirectionFromChambers(int index, int chambers) { return (chambers & (1 << index)) != 0 ? 1 : -1;}
    private int getIdentifiedVentTotalValue() {
        int totalVentUpdate = 0;
        for(int i = 0; i < NUM_VENTS; ++i) {
            totalVentUpdate += vents[i].getStabilityInfluence();
        }
        return totalVentUpdate;
    }
    private void mergeVentWith(int index, VentStatus toMergeWith) {
        if(vents[index].isRangeDefined()) {
            vents[index].mergeLowerBoundRanges(toMergeWith.getLowerBoundStart(),
                    toMergeWith.getLowerBoundEnd());
            vents[index].mergeUpperBoundRanges(toMergeWith.getUpperBoundStart(),
                    toMergeWith.getUpperBoundEnd());
            //Merge ranges if they are both overlap
            if(vents[index].isUpperBoundWithinRange(vents[index].getLowerBoundStart(), vents[index].getLowerBoundEnd())) {
                vents[index].mergeUpperBoundRanges(vents[index].getLowerBoundStart(), vents[index].getLowerBoundEnd());
                vents[index].mergeLowerBoundRanges(vents[index].getUpperBoundStart(), vents[index].getUpperBoundEnd());
            }
        } else {
            vents[index].setLowerBoundRange(toMergeWith.getLowerBoundStart(), toMergeWith.getLowerBoundEnd());
            vents[index].setUpperBoundRange(toMergeWith.getUpperBoundStart(), toMergeWith.getUpperBoundEnd());
        }
    }
    private void overlapVentWith(int index, VentStatus toOverlapWith) {
        //Get all possible range combinations
        int[] lowerLower = vents[index].getOverlappedLowerBoundRange(toOverlapWith.getLowerBoundStart(),
                toOverlapWith.getLowerBoundEnd());
        int[] lowerUpper = vents[index].getOverlappedLowerBoundRange(toOverlapWith.getUpperBoundStart(),
                toOverlapWith.getUpperBoundEnd());
        int[] upperUpper = vents[index].getOverlappedUpperBoundRange(toOverlapWith.getUpperBoundStart(),
                toOverlapWith.getUpperBoundEnd());
        int[] upperLower = vents[index].getOverlappedUpperBoundRange(toOverlapWith.getLowerBoundStart(),
                toOverlapWith.getLowerBoundEnd());
        boolean isLowerLowerValid = !(lowerLower[0] == -1 && lowerLower[1] == -1);
        boolean isLowerUpperValid = !(lowerUpper[0] == -1 && lowerUpper[1] == -1);
        boolean isUpperUpperValid = !(upperUpper[0] == -1 && upperUpper[1] == -1);
        boolean isUpperLowerValid = !(upperLower[0] == -1 && upperLower[1] == -1);
        boolean isLowerValid = isLowerLowerValid || isLowerUpperValid;
        boolean isUpperValid = isUpperLowerValid || isUpperUpperValid;

        //Exit if neither range has any overlap
        vents[index].clearRanges();
        if(!isLowerValid && !isUpperValid) return;

        //TODO: For now we assume if both ranges match they are the same
        //For single range the minimum distance between lower and upper is 6
        //since our ranges are size 3 its impossible for both to match

        //Lower bound range overlaps with another range
        if(isLowerValid) {
            if(isLowerLowerValid) vents[index].setLowerBoundRange(lowerLower[0], lowerLower[1]);
            else vents[index].setLowerBoundRange(lowerUpper[0], lowerUpper[1]);
            //If only lower bound is valid set upper bound range as well
            if(!isUpperValid) vents[index].setUpperBoundRange(vents[index].getLowerBoundStart(), vents[index].getLowerBoundEnd());
        }
        //Upper bound range overlaps with another range
        if(isUpperValid) {
            if(isUpperUpperValid) vents[index].setUpperBoundRange(upperUpper[0], upperUpper[1]);
            else vents[index].setUpperBoundRange(upperLower[0], upperLower[1]);
            //If only upper bound is valid set lower bound range as well
            if(!isLowerValid) vents[index].setLowerBoundRange(vents[index].getUpperBoundStart(), vents[index].getUpperBoundEnd());
        }
    }

    //Accessors
    public boolean hasDoneVMReset() { return hasReset; }
    public boolean isEnoughVentsIdentified() { return numIdentifiedVents > 0; }
    public boolean isAllVentsIdentified() { return numIdentifiedVents == NUM_VENTS; }
    public final VentStatus[] getVents() { return vents; }
    public int getStabilityChange() { return stabilityChange; }
    public int getNumIdentifiedVents() { return numIdentifiedVents; }
    public boolean areRangesDefined() {
        //All ranges must be defined for this
        int[] missingVentIndices = getUnidentifiedVentIndices();
        for(int i = 0; i < missingVentIndices.length; ++i) {
            if(!vents[missingVentIndices[i]].isRangeDefined()) return false;
        }
        return true;
    }
}
