package com.example;

import java.util.HashSet;
import net.runelite.client.Notifier;


public class VMNotifier {
    public enum NotificationEvents {
        VM_RESET,
        VM_ERUPTION;
    }
    private static final int NOTIFICATION_START_COOLDOWN_TICKS = 10;
    private HashSet<NotificationEvents> oneTimeEvents = new HashSet<>();
    VMNotifier() {
        reset();
    }
    private void setOneTimeEvents() {
        oneTimeEvents.add(NotificationEvents.VM_RESET);
        oneTimeEvents.add(NotificationEvents.VM_ERUPTION);
    }
    public void reset() {
        setOneTimeEvents();
    }
    public void notify(Notifier notifier, NotificationEvents event, int ticksPassed) {
        if(ticksPassed <= NOTIFICATION_START_COOLDOWN_TICKS) return;
        if(!oneTimeEvents.contains(event)) return;
        oneTimeEvents.remove(event);

        switch (event) {
            case VM_RESET:
                notifier.notify("The vents will shift in " + 30 + " seconds!");
                break;

            case VM_ERUPTION:
                notifier.notify("The volcano will erupt in " + 40 + " seconds!");
                break;
        }
    }
}
