package com.ultimatevm;

import java.awt.Color;
import java.util.Locale;
import net.runelite.client.ui.overlay.infobox.InfoBox;
import net.runelite.client.util.ImageUtil;

public class BlowUpChanceInfoBox extends InfoBox
{
	private double blowUpChancePercent = Double.NaN;

	public BlowUpChanceInfoBox(UltimateVolcanicMinePlugin plugin)
	{
		super(ImageUtil.getResourceStreamFromClass(UltimateVolcanicMinePlugin.class, "/volcanic_mine_tab.png"), plugin);
	}

	public void setBlowUpChancePercent(double blowUpChancePercent)
	{
		this.blowUpChancePercent = Math.max(0.0, Math.min(100.0, blowUpChancePercent));
	}

	public void clear()
	{
		this.blowUpChancePercent = Double.NaN;
	}

	public boolean hasValue()
	{
		return !Double.isNaN(blowUpChancePercent);
	}

	@Override
	public String getText()
	{
		if (!hasValue())
		{
			return "--";
		}

		return Math.round(blowUpChancePercent) + "%";
	}

	@Override
	public Color getTextColor()
	{
		if (!hasValue())
		{
			return Color.WHITE;
		}

		if (blowUpChancePercent >= 70.0)
		{
			return Color.RED;
		}

		if (blowUpChancePercent >= 40.0)
		{
			return Color.ORANGE;
		}

		return Color.GREEN;
	}

	@Override
	public String getTooltip()
	{
		if (!hasValue())
		{
			return "Estimated blow-up chance unavailable";
		}

		return String.format(Locale.US, "Estimated blow-up chance: %.2f%%", blowUpChancePercent);
	}
}
