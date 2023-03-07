package com.ultimatevm;

import net.runelite.client.config.Config;
import net.runelite.client.config.*;

@ConfigGroup("volcanic mine")
public interface UltimateVolcanicMineConfig extends Config
{
	@ConfigItem(
			keyName = "ventWarning",
			name = "Vent Shift Notification",
			description = "Show warning in advance of vents resetting 5 minutes into game",
			position = 0
	)
	default boolean showVentWarning()
	{
		return true;
	}

	@ConfigItem(
			keyName = "ventWarningTime",
			name = "Vent Shift Warning Time",
			description = "Number of seconds before the vents reset",
			position = 1
	)
	@Range(
			max = 60,
			min = 1
	)
	@Units(Units.SECONDS)
	default int ventWarningTime()
	{
		return 25;
	}

	@ConfigItem(
			keyName = "eruptionWarning",
			name = "Eruption Notification",
			description = "Show warning in advance of the volcano erupting",
			position = 2
	)
	default boolean showEruptionWarning()
	{
		return true;
	}
	@ConfigItem(
			keyName = "eruptionWarningTime",
			name = "Eruption Warning Time",
			description = "Number of seconds before the volcano erupts",
			position = 3
	)
	@Range(
			max = 60,
			min = 30
	)
	@Units(Units.SECONDS)
	default int eruptionWarningTime()
	{
		return 40;
	}
	@ConfigItem(
			keyName = "platformWarning",
			name = "Platform Despawn Notification",
			description = "Show warning for when platform below you is about to disappear",
			position = 4
	)
	default boolean showPlatformWarning()
	{
		return true;
	}

	@ConfigItem(
			position = 5,
			keyName = "ventFixNotifier",
			name = "Vent Fix notifier",
			description = "Notifies on stability change 6 mins or prior for A role and B/C role"
	)
	default boolean ventFixNotifier()
	{
		return true;
	}

	@ConfigItem(
			position = 6,
			keyName = "predictedVentFixNotifier",
			name = "Predicted Vent Fix notifier",
			description = "Notifies when predicted stability change drops below a specific amount"
	)
	default boolean predictedVentFixNotifier()
	{
		return true;
	}
	@ConfigItem(
			keyName = "predictedStabilityChange",
			name = "Predicted Stability Change",
			description = "The estimated change before a recommended vent fix",
			position = 7
	)
	@Range(
			max = 5,
			min = 1
	)
	default int predictedStabilityChange()
	{
		return 1;
	}

	@ConfigItem(
			position = 8,
			keyName = "capCounter",
			name = "Cap counter",
			description = "Displays an infobox with the total vents capped"
	)
	default boolean capCounter()
	{
		return true;
	}

	@ConfigItem(
			position = 9,
			keyName = "rockTimer",
			name = "Rock Timer",
			description = "Shows a respawn timer when a rock is taken"
	)
	default boolean rockTimer()
	{
		return true;
	}
}
