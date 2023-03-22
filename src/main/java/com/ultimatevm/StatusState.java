package com.ultimatevm;

public class StatusState {
    public static final int NUM_VENTS = 3;

    private VentStatus[] vents = new VentStatus[NUM_VENTS];
    private int[] unidentifiedVentIndices, identifiedVentIndices;
    private int stabilityChange;
    private int numIdentifiedVents;

    public StatusState(VentStatus[] vents, int stabChange) {
        numIdentifiedVents = 0;
        unidentifiedVentIndices = new int[]{0,1,2};
        identifiedVentIndices = new int[]{};
        update(vents, stabChange);
    }
    public StatusState(StatusState snapshot) {
        numIdentifiedVents = 0;
        update(snapshot);
    }

    private void setVent(int index, VentStatus vent) {
        if(this.vents[index] == null) this.vents[index] = new VentStatus(vent);
        else this.vents[index].setEqualTo(vent);
    }
    private void setChange(int change) {
        this.stabilityChange = change;
    }

    public void update(VentStatus[] ventStatus, int stabChange) {
        int startingIdentifiedVents = numIdentifiedVents;
        setChange(stabChange);
        for(int i = 0; i < ventStatus.length; ++i) {
            if(this.vents[i] == null || !this.vents[i].isIdentified()) {
                setVent(i, ventStatus[i]);
                if(this.vents[i].isIdentified()) ++numIdentifiedVents;
            }
            else setVent(i, ventStatus[i]);
        }

        //Update our array of vents
        if(startingIdentifiedVents != numIdentifiedVents) {
            int uIndex = 0, iIndex = 0;
            unidentifiedVentIndices = new int[NUM_VENTS - numIdentifiedVents];
            identifiedVentIndices = new int[numIdentifiedVents];
            for(int i = 0; i < NUM_VENTS; ++i) {
                if(this.vents[i].isIdentified()) identifiedVentIndices[iIndex++] = i;
                else unidentifiedVentIndices[uIndex++] = i;
            }
        }
    }
    public void update(StatusState newSnapShot) {
        update(newSnapShot.vents, newSnapShot.stabilityChange);
    }
    public boolean isEnoughVentsIdentified() { return numIdentifiedVents > 1; }
    public boolean isAllVentsIdentified() { return numIdentifiedVents == this.vents.length; }


    public VentStatus getVent(int index) {
        return vents[index];
    }
    public int[] getUnidentifiedVentIndices() { return unidentifiedVentIndices; }
    public int getStabilityChange() { return stabilityChange; }
    public int getNumIdentifiedVents() { return numIdentifiedVents; }
}
