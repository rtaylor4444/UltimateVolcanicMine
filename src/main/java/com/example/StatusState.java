package com.example;

public class StatusState {
    public static final int NUM_VENTS = 3;

    private VentStatus[] vents = new VentStatus[NUM_VENTS];
    private int stabilityChange;
    private int numIdentifiedVents;

    public StatusState(VentStatus[] vents, int stabChange) {
        update(vents, stabChange);
    }
    public StatusState(StatusState snapshot) {
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
        numIdentifiedVents = 0;
        setChange(stabChange);
        for(int i = 0; i < ventStatus.length; ++i) {
            setVent(i, ventStatus[i]);
            if(this.vents[i].isIdentified()) ++numIdentifiedVents;
        }
    }
    public void update(StatusState newSnapShot) {
        update(newSnapShot.vents, newSnapShot.stabilityChange);
    }
    public boolean isEnoughVentsIdentified() { return numIdentifiedVents > 1; }
    public boolean isAllVentsIdentified() { return numIdentifiedVents == this.vents.length; }
}
