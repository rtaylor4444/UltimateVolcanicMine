package com.ultimatevm;

import net.runelite.client.config.Config;
import net.runelite.client.config.*;

@ConfigGroup("ultimate-volcanic-mine")
public interface UltimateVolcanicMineConfig extends Config
{
	enum PredictionScenario
	{
		WORST_CASE,
		BEST_CASE
	}

	enum TimingRenderMode {
		PROGRESS_PIE,
		NUMBER
	}

	@ConfigSection(
			name = "Display",
			description = "All the options for how your interface to look",
			position = 0,
			closedByDefault = false
	)
	String display = "display";
	@ConfigItem(
			position = 1,
			keyName = "capCounter",
			name = "Cap counter",
			description = "Displays an infobox with the total vents capped",
			section = display
	)
	default boolean capCounter()
	{
		return true;
	}
	@ConfigItem(
			keyName = "stabilityUpdateHistoryCount",
			name = "Stability Update History",
			description = "The number of stability updates to track",
			position = 2,
			section = display
	)
	@Range(
			max = 3,
			min = 0
	)
	default int stabilityUpdateHistoryCount()
	{
		return 3;
	}
	@ConfigItem(
			keyName = "predictedStabilityChangeHistoryCount",
			name = "Predicted Stability Change History",
			description = "The number of predicted stability changes to track",
			position = 3,
			section = display
	)
	@Range(
			max = 3,
			min = 0
	)
	default int predictedStabilityChangeHistoryCount()
	{
		return 3;
	}
	@ConfigItem(
			position = 4,
			keyName = "ventStatusUpdateHistory",
			name = "Vent Status Update History",
			description = "Sends a chat message with the vent status for each stability update",
			section = display
	)
	default boolean ventStatusUpdateHistory()
	{
		return false;
	}
	@ConfigItem(
			position = 5,
			keyName = "ventStatusPrediction",
			name = "Vent Status Prediction",
			description = "Displays an estimate for a single missing vent",
			section = display
	)
	default boolean ventStatusPrediction()
	{
		return true;
	}

	@ConfigSection(
			name = "Timing",
			description = "Options on how to display timed objects",
			position = 1,
			closedByDefault = false
	)
	String timing = "timing";
	@ConfigItem(
			position = 0,
			keyName = "rockTimer",
			name = "Rock Timer",
			description = "Shows a respawn timer when a rock is taken",
			section = timing
	)
	default boolean rockTimer()
	{
		return true;
	}
	@ConfigItem(
			keyName = "rockRenderMode",
			name = "Rock Render Mode",
			description = "How the rock respawn timer is rendered",
			position = 1,
			section = timing
	)
	default TimingRenderMode rockRenderMode() {
		return TimingRenderMode.PROGRESS_PIE;
	}
	@ConfigItem(
			position = 2,
			keyName = "platformTimer",
			name = "Platform Timer",
			description = "Shows a despawn timer when a platform is made",
			section = timing
	)
	default boolean platformTimer()
	{
		return false;
	}
	@ConfigItem(
			keyName = "platformRenderMode",
			name = "Platform Render Mode",
			description = "Uses best or worst case scenario when predicting fixes or stability changes",
			position = 3,
			section = timing
	)
	default TimingRenderMode platformRenderMode() {
		return TimingRenderMode.NUMBER;
	}

	@ConfigSection(
			name = "Notifications",
			description = "All the options for how you want to customize your notifications",
			position = 2,
			closedByDefault = false
	)
	String notifications = "notifications";
	@ConfigItem(
			keyName = "ventWarning",
			name = "Vent Shift Notification",
			description = "Show warning in advance of vents resetting 5 minutes into game",
			position = 0,
			section = notifications
	)
	default boolean showVentWarning()
	{
		return true;
	}
	@ConfigItem(
			keyName = "ventWarningTime",
			name = "Vent Shift Warning Time",
			description = "Number of seconds before the vents reset",
			position = 1,
			section = notifications
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
			position = 2,
			section = notifications
	)
	default boolean showEruptionWarning()
	{
		return true;
	}
	@ConfigItem(
			keyName = "eruptionWarningTime",
			name = "Eruption Warning Time",
			description = "Number of seconds before the volcano erupts",
			position = 3,
			section = notifications
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
			position = 4,
			section = notifications
	)
	default boolean showPlatformWarning()
	{
		return true;
	}
	@ConfigItem(
			keyName = "boulderMovement",
			name = "Boulder Movement Notification",
			description = "Notify when current boulder stage is complete",
			position = 5,
			section = notifications
	)
	default boolean showBoulderWarning()
	{
		return false;
	}
	@ConfigItem(
			position = 6,
			keyName = "ventFixNotifier",
			name = "Vent Fix notifier",
			description = "Notifies on stability change 6 mins or prior for A role and B/C role",
			section = notifications
	)
	default boolean ventFixNotifier()
	{
		return true;
	}

	@ConfigSection(
			name = "Predicted Pre Reset Fix",
			description = "Options for customizing your predicted vent fix",
			position = 3,
			closedByDefault = false
	)
	String preresetfix = "predicted pre reset fix";
	@ConfigItem(
			position = 0,
			keyName = "predictedVentFixNotifier",
			name = "Predicted Vent Fix notifier",
			description = "Notifies when predicted stability change drops below a specific amount",
			section = preresetfix
	)
	default boolean predictedVentFixNotifier()
	{
		return false;
	}
	@ConfigItem(
			keyName = "predictedVentFixScenario",
			name = "Prediction Scenario",
			description = "Uses best or worst case scenario when predicting fixes or stability changes",
			position = 1,
			section = preresetfix
	)
	default PredictionScenario predictedVentFixScenario() {
		return PredictionScenario.WORST_CASE;
	}
	@ConfigItem(
			keyName = "predictedStabilityChange",
			name = "Stability Change",
			description = "The estimated change before a recommended vent fix",
			position = 2,
			section = preresetfix
	)
	@Range(
			max = 13,
			min = 1
	)
	default int predictedStabilityChange()
	{
		return 12;
	}
	@ConfigItem(
			keyName = "predictedventWarningStartTime",
			name = "Warning Start Time",
			description = "Number of seconds after 9:00 to suggest a vent fix",
			position = 3,
			section = preresetfix
	)
	@Range(
			max = 60,
			min = 1
	)
	@Units(Units.SECONDS)
	default int predictedventWarningStartTime()
	{
		return 15;
	}
	@ConfigItem(
			keyName = "predictedventWarningEndTime",
			name = "Warning End Time",
			description = "Number of seconds before 6:00 to suggest a vent fix",
			position = 4,
			section = preresetfix
	)
	@Range(
			max = 60,
			min = 1
	)
	@Units(Units.SECONDS)
	default int predictedventWarningEndTime()
	{
		return 30;
	}

	@ConfigSection(
			name = "Team Size Manager",
			description = "Notifications for any unexpected team size changes",
			position = 4,
			closedByDefault = false
	)
	String teamSize = "team size";
	@ConfigItem(
			keyName = "playerLeaveNotifier",
			name = "Player Leave Notifier",
			description = "Notifies when a player dies or exits the mine",
			position = 0,
			section = teamSize
	)
	default boolean playerLeaveNotifier()
	{
		return true;
	}
	@ConfigItem(
			keyName = "extraPlayerNotifier",
			name = "Extra Player Notifier",
			description = "Notifies when an unexpected player enters the mine",
			position = 1,
			section = teamSize
	)
	default boolean extraPlayerNotifier()
	{
		return false;
	}
	@ConfigItem(
			keyName = "expectedTeamSize",
			name = "Expected Team Size",
			description = "Set to the number of players in your current team",
			position = 2,
			section = teamSize
	)
	@Range(
			max = 50,
			min = 1
	)
	default int expectedTeamSize()
	{
		return 1;
	}
}
