package com.ultimatevm;

import java.util.HashSet;
import net.runelite.client.Notifier;

import javax.inject.Inject;


public class VMNotifier {
    public enum NotificationEvents {
        VM_RESET,
        VM_ERUPTION,
        VM_PRE_RESET_VENT_FIX,
        VM_PREDICTED_VENT_FIX;
    }
    public static final int NOTIFICATION_START_COOLDOWN_TICKS = 10;
    @Inject
    private UltimateVolcanicMineConfig config;
    private HashSet<NotificationEvents> oneTimeEvents = new HashSet<>();
    @Inject
    VMNotifier(UltimateVolcanicMineConfig config) {
        this.config = config;
        reset();
    }
    private void setOneTimeEvents() {
        oneTimeEvents.add(NotificationEvents.VM_RESET);
        oneTimeEvents.add(NotificationEvents.VM_ERUPTION);
        oneTimeEvents.add(NotificationEvents.VM_PRE_RESET_VENT_FIX);
        oneTimeEvents.add(NotificationEvents.VM_PREDICTED_VENT_FIX);
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
                if(!config.showVentWarning()) return;
                notifier.notify("The vents will shift in " + config.ventWarningTime() + " seconds!");
                break;

            case VM_ERUPTION:
                if(!config.showEruptionWarning()) return;
                notifier.notify("The volcano will erupt in " + config.eruptionWarningTime() + " seconds!");
                break;

            case VM_PRE_RESET_VENT_FIX:
                if(!config.ventFixNotifier()) return;
                notifier.notify("Fix your vent!");
                break;

            case VM_PREDICTED_VENT_FIX:
                if(!config.predictedVentFixNotifier()) return;
                notifier.notify("Fix your vent! (Prediction)");
                break;
        }
    }
}
