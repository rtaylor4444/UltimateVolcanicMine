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

public class GuideOverlay extends Overlay
{
	private static final int OVERLAY_SIZE = 48;
	private static final int TEXT_PADDING = 14;
	private static final Color SAFE_COLOR = new Color(0, 180, 0, 210);
	private static final Color RESET_COLOR = new Color(200, 0, 0, 210);
	private static final Color FIX_COLOR = new Color(220, 130, 0, 210);
	private static final Font LABEL_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 14);

	private final UltimateVolcanicMineConfig config;
	private GuideManager.Scenario currentScenario;

	@Inject
	GuideOverlay(UltimateVolcanicMineConfig config)
	{
		setPosition(OverlayPosition.BOTTOM_LEFT);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		this.config = config;
	}

	public void setScenario(GuideManager.Scenario scenario)
	{
		currentScenario = scenario;
	}

	public void reset()
	{
		currentScenario = null;
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.showGuide() || currentScenario == null)
		{
			return null;
		}

		String label = scenarioLabel(currentScenario);

		if (label != null)
		{
			graphics.setFont(LABEL_FONT);
			FontMetrics fm = graphics.getFontMetrics();
			int width = fm.stringWidth(label) + TEXT_PADDING * 2;
			graphics.setColor(FIX_COLOR);
			graphics.fillRect(0, 0, width, OVERLAY_SIZE);
			graphics.setColor(Color.WHITE);
			int textY = (OVERLAY_SIZE + fm.getAscent() - fm.getDescent()) / 2;
			graphics.drawString(label, TEXT_PADDING, textY);
			return new Dimension(width, OVERLAY_SIZE);
		}

		Color bg = currentScenario == GuideManager.Scenario.NO_FIX ? SAFE_COLOR : RESET_COLOR;
		graphics.setColor(bg);
		graphics.fillRect(0, 0, OVERLAY_SIZE, OVERLAY_SIZE);
		return new Dimension(OVERLAY_SIZE, OVERLAY_SIZE);
	}

	private static String scenarioLabel(GuideManager.Scenario scenario)
	{
		switch (scenario)
		{
			case FIX_A_AFTER_B2:    return "Fix A after B2";
			case FIX_B_AFTER_B2:    return "Fix B after B2";
			case FIX_AB_AFTER_B2:   return "Fix A+B after B2";
			case FIX_A_AFTER_B1:    return "Fix A after B1";
			case FIX_B_AFTER_B1:    return "Fix B after B1";
			case FIX_AB_AFTER_B1:   return "Fix A+B after B1";
			case FIX_BC_AFTER_B1:   return "Fix B+C after B1";
			case FIX_A_RETURN_B1:   return "Return to B1: fix A";
			case FIX_B_RETURN_B1:   return "Return to B1: fix B";
			case FIX_C_RETURN_B1:   return "Return to B1: fix C";
			case FIX_CB_RETURN_B1:  return "Return to B1: fix C+B";
			case FIX_AB_RETURN_B1:  return "Return to B1: fix A+B";
			default: return null;
		}
	}
}
