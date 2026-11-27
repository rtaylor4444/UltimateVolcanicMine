package com.ultimatevm;

import net.runelite.api.Client;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.util.Text;

public class VentStatusOverlayOverride
{
	private static final String COLOR_RED = "ff0000";
	private static final String COLOR_GREEN = "00ff00";
	private static final String COLOR_BLUE = "00ffff";

	private enum VentState
	{
		CORRECT,
		WRONG,
		UNKNOWN
	}

	private final Client client;
	private final int ventStatusVarbitA;
	private final int ventStatusVarbitB;
	private final int ventStatusVarbitC;
	private VentInference ventInference;
	private static final int[] VENT_PERCENTAGE_WIDGET_COMPONENTS = {
		ComponentID.VOLCANIC_MINE_VENT_A_PERCENTAGE + 1,
		ComponentID.VOLCANIC_MINE_VENT_B_PERCENTAGE + 1,
		ComponentID.VOLCANIC_MINE_VENT_C_PERCENTAGE + 1
	};

	public VentStatusOverlayOverride(
		Client client,
		int ventStatusVarbitA,
		int ventStatusVarbitB,
		int ventStatusVarbitC)
	{
		this.client = client;
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

	public void updateChamberStatusWidgetColors(boolean predictionColorsEnabled)
	{
		updateChamberStatusWidgetColor(ComponentID.VOLCANIC_MINE_VENT_A_STATUS + 1, 0, predictionColorsEnabled);
		updateChamberStatusWidgetColor(ComponentID.VOLCANIC_MINE_VENT_B_STATUS + 1, 1, predictionColorsEnabled);
		updateChamberStatusWidgetColor(ComponentID.VOLCANIC_MINE_VENT_C_STATUS + 1, 2, predictionColorsEnabled);
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

		String text = displayText(ventIndex, currentText, varbitValue);
		if (varbitValue != VentRules.UNKNOWN)
		{
			if (isWidgetShowingExactVentValue(currentText, varbitValue))
			{
				return;
			}

			text = extractVentPrefix(currentText) + varbitValue + "%";
		}

		widget.setText(text);
	}

	/** The vent line as shown: the game's text, or the values still possible for an unchecked vent. */
	public String ventDisplayText(int ventIndex, Widget widget)
	{
		if (widget == null || widget.getText() == null)
		{
			return "";
		}
		int varbit = ventIndex == 0 ? ventStatusVarbitA : ventIndex == 1 ? ventStatusVarbitB : ventStatusVarbitC;
		return displayText(ventIndex, widget.getText(), client.getVarbitValue(varbit));
	}

	private String displayText(int ventIndex, String currentText, int varbitValue)
	{
		String inferredText = varbitValue == VentRules.UNKNOWN ? formatInferredValues(ventIndex) : null;
		if (inferredText != null && currentText.length() >= 3)
		{
			return currentText.substring(0, 3) + "<col=" + COLOR_BLUE + ">" + inferredText + "</col>";
		}
		return currentText;
	}

	/**
	 * The values still possible for an unchecked vent, from the vent inference: one or two ranges
	 * (e.g. "30-33 67-70%"), or the overall span if the possibilities are more scattered.
	 */
	private String formatInferredValues(int ventIndex)
	{
		java.util.BitSet values = ventInference != null ? ventInference.possibleValues(ventIndex) : null;
		if (values == null || values.isEmpty())
		{
			return null;
		}
		return formatValueRanges(values);
	}

	static String formatValueRanges(java.util.BitSet values)
	{
		java.util.List<int[]> runs = new java.util.ArrayList<>();
		for (int start = values.nextSetBit(0); start >= 0; )
		{
			int end = values.nextClearBit(start) - 1;
			runs.add(new int[]{start, end});
			start = values.nextSetBit(end + 1);
		}
		if (runs.size() > 2)
		{
			runs = java.util.Collections.singletonList(new int[]{runs.get(0)[0], runs.get(runs.size() - 1)[1]});
		}
		StringBuilder text = new StringBuilder();
		for (int[] run : runs)
		{
			if (text.length() > 0)
			{
				text.append(' ');
			}
			text.append(run[0]);
			if (run[1] != run[0])
			{
				text.append('-').append(run[1]);
			}
			text.append('%');
		}
		return text.toString();
	}

	/** Lets the vent display show the values the vent inference still allows. */
	public void setVentInference(VentInference ventInference)
	{
		this.ventInference = ventInference;
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

	private void updateChamberStatusWidgetColor(int widgetComponentId, int ventIndex, boolean predictionColorsEnabled)
	{
		Widget widget = client.getWidget(widgetComponentId);
		if (widget == null)
		{
			return;
		}

		String widgetText = widget.getText();
		if (widgetText == null)
		{
			return;
		}

		Boolean isBlocked = getBlockedState(widgetText);
		if (isBlocked == null)
		{
			return;
		}

		VentState ventState = getVentState(ventIndex, isBlocked);
		String color = predictionColorsEnabled ? getPredictionColor(ventState) : getDefaultChamberColor(isBlocked);
		String recoloredText = applyStatusWordColor(widgetText, color);
		if (!widgetText.equals(recoloredText))
		{
			widget.setText(recoloredText);
		}
	}

	private Boolean getBlockedState(String widgetText)
	{
		if (widgetText == null)
		{
			return null;
		}

		String rawText = Text.removeTags(widgetText).toLowerCase();
		if (rawText.contains("unblocked"))
		{
			return false;
		}
		if (rawText.contains("blocked"))
		{
			return true;
		}

		return null;
	}

	private String getPredictionColor(VentState ventState)
	{
		switch (ventState)
		{
			case CORRECT:
				return COLOR_GREEN;
			case WRONG:
				return COLOR_RED;
			default:
				return COLOR_BLUE;
		}
	}

	private String getDefaultChamberColor(boolean isBlocked)
	{
		return isBlocked ? COLOR_RED : COLOR_GREEN;
	}

	private String getVentPercentageWidgetText(int ventIndex)
	{
		Widget ventPercentageWidget = client.getWidget(VENT_PERCENTAGE_WIDGET_COMPONENTS[ventIndex]);
		if (ventPercentageWidget == null)
		{
			return null;
		}

		return ventPercentageWidget.getText();
	}

	private String applyStatusWordColor(String widgetText, String color)
	{
		String rawText = Text.removeTags(widgetText);
		int blockedIndex = rawText.toLowerCase().indexOf("blocked");
		int unblockedIndex = rawText.toLowerCase().indexOf("unblocked");

		int startIndex = unblockedIndex >= 0 ? unblockedIndex : blockedIndex;
		if (startIndex < 0)
		{
			return widgetText;
		}

		String statusWord = unblockedIndex >= 0 ? "Unblocked" : "Blocked";
		String prefix = rawText.substring(0, startIndex);
		return prefix + "<col=" + color + ">" + statusWord + "</col>";
	}

	private VentState getVentState(int ventIndex, boolean isBlocked)
	{
		String widgetText = getVentPercentageWidgetText(ventIndex);
		if (widgetText == null)
		{
			return VentState.UNKNOWN;
		}

		String rawText = Text.removeTags(widgetText);
		int separatorIndex = rawText.indexOf(':');
		if (separatorIndex < 0)
		{
			return VentState.UNKNOWN;
		}

		String[] segments = rawText.substring(separatorIndex + 1).trim().split("\\s+");
		boolean canBeBelow = false;
		boolean canBeAbove = false;
		boolean canBePerfect = false;

		for (String segment : segments)
		{
			if (segment.isEmpty())
			{
				continue;
			}

			int[] bounds = parseDisplayedBounds(segment);
			if (bounds == null)
			{
				continue;
			}

			if (bounds[0] <= VentRules.PERFECT_VENT - 1)
			{
				canBeBelow = true;
			}
			if (bounds[1] >= VentRules.PERFECT_VENT + 1)
			{
				canBeAbove = true;
			}
			if (bounds[0] <= VentRules.PERFECT_VENT && bounds[1] >= VentRules.PERFECT_VENT)
			{
				canBePerfect = true;
			}
		}

		if (canBePerfect || (canBeBelow && canBeAbove))
		{
			return VentState.UNKNOWN;
		}

		if (canBeBelow)
		{
			return isBlocked ? VentState.CORRECT : VentState.WRONG;
		}

		if (canBeAbove)
		{
			return isBlocked ? VentState.WRONG : VentState.CORRECT;
		}

		return VentState.UNKNOWN;
	}

	private int[] parseDisplayedBounds(String segment)
	{
		String cleanedSegment = segment.replace("%", "");
		if (cleanedSegment.isEmpty())
		{
			return null;
		}
		try
		{
			if (cleanedSegment.contains("-"))
			{
				String[] parts = cleanedSegment.split("-", 2);
				if (parts.length != 2)
				{
					return null;
				}
				return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1])};
			}

			int value = Integer.parseInt(cleanedSegment);
			return new int[]{value, value};
		}
		catch (NumberFormatException ex)
		{
			return null;
		}
	}
}
