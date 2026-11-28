package com.ultimatevm;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

/**
 * Coach Debug setting: the coach's state (queued steps, plan, walk-out time), what the highlight
 * drew, rocks and caps as the plugin counts them, the trace folder, and which mine spots were found.
 */
public class CoachDebugOverlay extends OverlayPanel
{
	private final UltimateVolcanicMineConfig config;
	private volatile Supplier<List<String>> lines = Collections::emptyList;

	@Inject
	CoachDebugOverlay(UltimateVolcanicMineConfig config)
	{
		setPosition(OverlayPosition.TOP_LEFT);
		this.config = config;
	}

	void setLines(Supplier<List<String>> lines)
	{
		this.lines = lines;
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.coachDebug())
		{
			return null;
		}
		panelComponent.getChildren().add(TitleComponent.builder().text("UVM Coach debug").color(Color.ORANGE).build());
		panelComponent.setPreferredSize(new Dimension(360, 0));
		List<String> shown;
		try
		{
			shown = new ArrayList<>(lines.get());
		}
		catch (RuntimeException e)
		{
			shown = Collections.singletonList("error: " + e);
		}
		for (String line : shown)
		{
			Color color = line.contains("NOT FOUND") || line.contains("missing") ? Color.RED : Color.WHITE;
			panelComponent.getChildren().add(LineComponent.builder().left(line).leftColor(color).build());
		}
		return super.render(graphics);
	}
}
