package com.ultimatevm;

import java.awt.*;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.ProgressPieComponent;
import net.runelite.client.ui.overlay.components.TextComponent;
import net.runelite.client.util.ColorUtil;

public class TimedObjectOverlay extends Overlay {
    private final Client client;
    private final UltimateVolcanicMineConfig config;
    private TimedObjectTracker objTracker;

    @Inject
    TimedObjectOverlay(Client client, UltimateVolcanicMineConfig config)
    {
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
        this.client = client;
        this.config = config;
    }

    public void setRockTracker(TimedObjectTracker rockTracker) {
        this.objTracker = rockTracker;
    }

    private void renderProgressPie(Graphics2D graphics, final Point canvasLocation, final TimedObject obj) {
        final ProgressPieComponent progressPieComponent = new ProgressPieComponent();
        progressPieComponent.setPosition(canvasLocation);
        progressPieComponent.setProgress(obj.getTimeRemaining());
        Color stateColor = obj.getStateColor();

        progressPieComponent.setBorderColor(stateColor);
        progressPieComponent.setFill(ColorUtil.colorWithAlpha(stateColor, (int) (stateColor.getAlpha() / 2.5)));
        progressPieComponent.render(graphics);
    }
    private void renderNumber(Graphics2D graphics, final Point canvasLocation, final TimedObject obj) {
        int currentTicks = (int)obj.getTimeLeft();
        if(currentTicks > config.numberThreshold()) return;
        final TextComponent textComponent = new TextComponent();
        textComponent.setText(Integer.toString(currentTicks));
        textComponent.setFont(new Font("Arial Bold", Font.BOLD, 16));
        textComponent.setPosition(new java.awt.Point(canvasLocation.getX()-10, canvasLocation.getY()));
        textComponent.setColor(obj.getStateColor());
        textComponent.render(graphics);
    }
    private void renderBasedOnMode(UltimateVolcanicMineConfig.TimingRenderMode mode, Graphics2D graphics, final Point canvasLocation, final TimedObject obj) {
        switch(mode) {
            case PROGRESS_PIE:
                renderProgressPie(graphics, canvasLocation, obj);
                break;

            case NUMBER:
                renderNumber(graphics, canvasLocation, obj);
                break;
        }
    }
    @Override
    public Dimension render(Graphics2D graphics)
    {
        for (TimedObject obj : objTracker.getObjects()) {
            final LocalPoint localLocation = LocalPoint.fromWorld(client, obj.getWorldLocation());
            if (localLocation == null) continue;

            final Point canvasLocation = Perspective.localToCanvas(client, localLocation, client.getPlane());
            if (canvasLocation == null) continue;

            switch(obj.getObjectType()) {
                case ROCK:
                    if(!config.rockTimer()) continue;
                    renderBasedOnMode(config.rockRenderMode(), graphics, canvasLocation, obj);
                    break;

                case PLATFORM:
                    if(!config.platformTimer()) continue;
                    renderBasedOnMode(config.platformRenderMode(), graphics, canvasLocation, obj);
                    break;
            }
        }
        return null;
    }
}
