package com.example;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Color;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.ProgressPieComponent;
import net.runelite.client.util.ColorUtil;

public class CappingRockOverlay extends Overlay {
    private final Client client;
    private CappingRockTracker rockTracker;

    @Inject
    CappingRockOverlay(Client client)
    {
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
        this.client = client;
        this.rockTracker = rockTracker;
    }

    public void setRockTracker(CappingRockTracker rockTracker) {
        this.rockTracker = rockTracker;
    }
    @Override
    public Dimension render(Graphics2D graphics)
    {
        for (CappingRock rock : rockTracker.getRocks()) {
            final LocalPoint localLocation = LocalPoint.fromWorld(client, rock.getWorldLocation());
            if (localLocation == null) continue;

            final Point canvasLocation = Perspective.localToCanvas(client, localLocation, client.getPlane());
            if (canvasLocation == null) continue;

            final ProgressPieComponent progressPieComponent = new ProgressPieComponent();
            progressPieComponent.setPosition(canvasLocation);
            progressPieComponent.setProgress(rock.getTimeRemaining());
            Color stateColor = rock.getStateColor();

            progressPieComponent.setBorderColor(stateColor);
            progressPieComponent.setFill(ColorUtil.colorWithAlpha(stateColor, (int) (stateColor.getAlpha() / 2.5)));
            progressPieComponent.render(graphics);
        }
        return null;
    }
}
