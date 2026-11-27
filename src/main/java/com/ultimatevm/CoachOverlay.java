package com.ultimatevm;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/** The coach's next step as a coloured panel: what to do, and why underneath. */
public class CoachOverlay extends Overlay
{
	private static final int PADDING = 10;
	private static final Font TEXT_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 16);
	private static final Font DETAIL_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
	static final Color MINE_COLOR = new Color(0, 150, 0, 220);
	static final Color CHECK_COLOR = new Color(30, 110, 200, 220);
	static final Color FIX_COLOR = new Color(220, 130, 0, 220);
	static final Color URGENT_COLOR = new Color(200, 0, 0, 230);
	static final Color WAIT_COLOR = new Color(90, 90, 90, 220);

	private final UltimateVolcanicMineConfig config;
	private volatile StrategyCoach.Instruction instruction;

	@Inject
	CoachOverlay(UltimateVolcanicMineConfig config)
	{
		setPosition(OverlayPosition.BOTTOM_LEFT);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		this.config = config;
	}

	void setInstruction(StrategyCoach.Instruction instruction)
	{
		this.instruction = instruction;
	}

	static Color colorFor(StrategyCoach.Instruction instruction)
	{
		if (instruction.urgent)
		{
			return URGENT_COLOR;
		}
		switch (instruction.action)
		{
			case START:
			case MINE_BOULDER:
				return MINE_COLOR;
			case CHECK_VENT:
				return CHECK_COLOR;
			case FIX_CHAMBER:
			case CAP_CHAMBER:
			case TAKE_ROCK:
				return FIX_COLOR;
			case LEAVE:
			case TELEPORT:
				return URGENT_COLOR;
			default:
				return WAIT_COLOR;
		}
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		StrategyCoach.Instruction shown = instruction;
		if (!config.showGuide() || shown == null)
		{
			return null;
		}
		graphics.setFont(TEXT_FONT);
		FontMetrics textMetrics = graphics.getFontMetrics();
		graphics.setFont(DETAIL_FONT);
		FontMetrics detailMetrics = graphics.getFontMetrics();
		// Under the step: what comes after it, so you know where to head the moment it's done.
		// Nothing when the next step isn't worked out yet.
		String sub = shown.next == null || StrategyCoach.UNKNOWN_NEXT.equals(shown.next) ? null : "Next: " + shown.next;
		int width = textMetrics.stringWidth(shown.text);
		if (sub != null)
		{
			width = Math.max(width, detailMetrics.stringWidth(sub));
		}
		width += PADDING * 2;
		int height = PADDING + textMetrics.getAscent() + (sub != null ? 4 + detailMetrics.getHeight() : 0) + PADDING;
		graphics.setColor(colorFor(shown));
		graphics.fillRect(0, 0, width, height);
		graphics.setColor(Color.WHITE);
		graphics.setFont(TEXT_FONT);
		int y = PADDING + textMetrics.getAscent();
		graphics.drawString(shown.text, PADDING, y);
		if (sub != null)
		{
			graphics.setFont(DETAIL_FONT);
			graphics.drawString(sub, PADDING, y + 4 + detailMetrics.getAscent());
		}
		return new Dimension(width, height);
	}
}
