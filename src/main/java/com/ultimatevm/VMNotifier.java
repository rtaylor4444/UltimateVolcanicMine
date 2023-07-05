package com.ultimatevm;

import java.util.HashMap;
import java.util.HashSet;
import net.runelite.client.Notifier;

import javax.inject.Inject;


public class VMNotifier {
    public enum NotificationEvents {
        VM_PLAYER_LEAVE,
        VM_EXTRA_PLAYER,
        VM_RESET,
        VM_ERUPTION,
        VM_PRE_RESET_VENT_FIX,
        VM_PREDICTED_VENT_FIX,
        VM_PICKAXE_DESPAWN,
        VM_PICKAXE_LOW_STABILITY,
        VM_PICKAXE_BOULDER_COMPLETE;
    }
    public static final int NOTIFICATION_START_COOLDOWN_TICKS = 10;
    @Inject
    private UltimateVolcanicMineConfig config;
    private HashSet<NotificationEvents> oneTimeEvents = new HashSet<>();
    private HashMap<NotificationEvents, Integer> continousEvents = new HashMap<>();
    @Inject
    VMNotifier(UltimateVolcanicMineConfig config) {
        this.config = config;
        reset();
    }
    private void setOneTimeEvents() {
        oneTimeEvents.add(NotificationEvents.VM_PLAYER_LEAVE);
        oneTimeEvents.add(NotificationEvents.VM_EXTRA_PLAYER);
        oneTimeEvents.add(NotificationEvents.VM_RESET);
        oneTimeEvents.add(NotificationEvents.VM_ERUPTION);
        oneTimeEvents.add(NotificationEvents.VM_PRE_RESET_VENT_FIX);
        oneTimeEvents.add(NotificationEvents.VM_PREDICTED_VENT_FIX);
        oneTimeEvents.add(NotificationEvents.VM_PICKAXE_DESPAWN);
        oneTimeEvents.add(NotificationEvents.VM_PICKAXE_LOW_STABILITY);
        oneTimeEvents.add(NotificationEvents.VM_PICKAXE_BOULDER_COMPLETE);
    }
    private void setContinousEvents() {
        continousEvents.put(NotificationEvents.VM_PICKAXE_DESPAWN, 0);
    }
    public void reset() {
        setOneTimeEvents();
        setContinousEvents();
    }
    public void removeEvent(NotificationEvents event) {
        oneTimeEvents.remove(event);
    }
    public void notify(Notifier notifier, NotificationEvents event, int ticksPassed) {
        if(!oneTimeEvents.contains(event)) return;
        //Special case for extra player since we want notif to go off asap
        if(event == NotificationEvents.VM_EXTRA_PLAYER) {
            if(!config.extraPlayerNotifier()) return;
            notifier.notify("An extra player has joined your team!");
            oneTimeEvents.remove(event);
        }

        if(ticksPassed <= NOTIFICATION_START_COOLDOWN_TICKS) return;

        switch (event) {
            case VM_PLAYER_LEAVE:
                oneTimeEvents.remove(event);
                if(!config.playerLeaveNotifier()) return;
                notifier.notify("A player has left the mine!");
                break;

            case VM_RESET:
                oneTimeEvents.remove(event);
                if(!config.showVentWarning()) return;
                notifier.notify("The vents will shift in " + config.ventWarningTime() + " seconds!");
                break;

            case VM_ERUPTION:
                oneTimeEvents.remove(event);
                if(!config.showEruptionWarning()) return;
                notifier.notify("The volcano will erupt in " + config.eruptionWarningTime() + " seconds!");
                break;

            case VM_PRE_RESET_VENT_FIX:
                oneTimeEvents.remove(event);
                if(!config.ventFixNotifier()) return;
                notifier.notify("Fix your vent!");
                break;

            case VM_PREDICTED_VENT_FIX:
                oneTimeEvents.remove(event);
                if(!config.predictedVentFixNotifier()) return;
                notifier.notify("Be alert you might have to fix your vent soon!");
                break;

            case VM_PICKAXE_DESPAWN:
                int lastEventTime = continousEvents.get(NotificationEvents.VM_PICKAXE_DESPAWN);
                if(lastEventTime + NOTIFICATION_START_COOLDOWN_TICKS < ticksPassed) {
                    notifier.notify("Be careful your pickaxe will despawn soon!");
                    continousEvents.put(NotificationEvents.VM_PICKAXE_DESPAWN, ticksPassed);
                }
                break;

            case VM_PICKAXE_LOW_STABILITY:
                oneTimeEvents.remove(event);
                notifier.notify("Mine Stability is low don't forget your pickaxe!");
                break;

            case VM_PICKAXE_BOULDER_COMPLETE:
                oneTimeEvents.remove(event);
                notifier.notify("You finished the boulder, pick up your pickaxe!");
                break;

            default:
                break;
        }
    }
}
