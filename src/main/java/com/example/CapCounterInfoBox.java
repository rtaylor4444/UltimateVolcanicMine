package com.example;

import java.awt.Color;
import net.runelite.api.Client;
import net.runelite.client.ui.overlay.infobox.InfoBox;
import net.runelite.client.util.ImageUtil;

public class CapCounterInfoBox extends InfoBox {
    private CapCounter capCounter;
    public CapCounterInfoBox(CapCounter capCounter, UltimateVolcanicMinePlugin plugin) {
        super(ImageUtil.getResourceStreamFromClass(UltimateVolcanicMinePlugin.class, "/chamber.png"), plugin);
        this.capCounter = capCounter;
    }

    public String getText() {
        return String.valueOf(capCounter.getTimesCapped());
    }

    public Color getTextColor() {
        return Color.WHITE;
    }

    public String getTooltip() {
        return "You have capped  " + capCounter.getTimesCapped() + " times.";
    }
}
