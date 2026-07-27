package com.ultimatevm;

import net.runelite.api.Client;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.util.Text;

public class VentStatusOverlayOverride
{
	private final Client client;
	private final VentStatusPredicter ventStatusPredicter;
	private final int ventStatusVarbitA;
	private final int ventStatusVarbitB;
	private final int ventStatusVarbitC;

	public VentStatusOverlayOverride(
		Client client,
		VentStatusPredicter ventStatusPredicter,
		int ventStatusVarbitA,
		int ventStatusVarbitB,
		int ventStatusVarbitC)
	{
		this.client = client;
		this.ventStatusPredicter = ventStatusPredicter;
		this.ventStatusVarbitA = ventStatusVarbitA;
		this.ventStatusVarbitB = ventStatusVarbitB;
		this.ventStatusVarbitC = ventStatusVarbitC;
	}

	public boolean isVentStatusVarbit(int varbitId)
	{
		return varbitId == ventStatusVarbitA
			|| varbitId == ventStatusVarbitB
			|| varbitId == ventStatusVarbitC;
	}

	public void overrideVentStatusWidgetsFromVarbits()
	{
		overrideVentWidgetText(ComponentID.VOLCANIC_MINE_VENT_A_PERCENTAGE + 1, 0, client.getVarbitValue(ventStatusVarbitA));
		overrideVentWidgetText(ComponentID.VOLCANIC_MINE_VENT_B_PERCENTAGE + 1, 1, client.getVarbitValue(ventStatusVarbitB));
		overrideVentWidgetText(ComponentID.VOLCANIC_MINE_VENT_C_PERCENTAGE + 1, 2, client.getVarbitValue(ventStatusVarbitC));
	}

	private void overrideVentWidgetText(int widgetComponentId, int ventIndex, int varbitValue)
	{
		Widget widget = client.getWidget(widgetComponentId);
		if (widget == null)
		{
			return;
		}

		String currentText = widget.getText();
		if (currentText == null)
		{
			return;
		}

		String text = ventStatusPredicter.getVentStatusText(ventIndex, currentText);
		if (varbitValue != VentStatus.STARTING_VENT_VALUE)
		{
			if (isWidgetShowingExactVentValue(currentText, varbitValue))
			{
				return;
			}

			text = extractVentPrefix(currentText) + varbitValue + "%";
		}

		widget.setText(text);
	}

	private boolean isWidgetShowingExactVentValue(String widgetText, int ventValue)
	{
		String rawText = Text.removeTags(widgetText);
		int separatorIndex = rawText.indexOf(':');
		if (separatorIndex < 0)
		{
			return false;
		}

		String valuePortion = rawText.substring(separatorIndex + 1).trim();
		return valuePortion.equals(ventValue + "%");
	}

	private String extractVentPrefix(String widgetText)
	{
		int separatorIndex = widgetText.indexOf(':');
		if (separatorIndex < 0)
		{
			return widgetText.length() >= 3 ? widgetText.substring(0, 3) : "";
		}

		int endIndex = separatorIndex + 1;
		while (endIndex < widgetText.length() && Character.isWhitespace(widgetText.charAt(endIndex)))
		{
			++endIndex;
		}

		return widgetText.substring(0, endIndex);
	}
}
