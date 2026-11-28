package com.ultimatevm;

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
			position = 2,
			keyName = "showBlowUpChance",
			name = "Show Blow-Up Chance",
			description = "Displays an infobox with the current simulated blow-up chance",
			section = display
	)
	default boolean showBlowUpChance()
	{
		return true;
	}
	@ConfigItem(
			keyName = "stabilityUpdateHistoryCount",
			name = "Stability Update History",
			description = "The number of stability updates to track",
			position = 3,
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
			position = 4,
			section = display
	)
	@Range(
			max = 3,
			min = 0
	)
	default int predictedStabilityChangeHistoryCount()
	{
		return 1;
	}
	@ConfigItem(
			position = 5,
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
			position = 6,
			keyName = "ventStatusPrediction",
			name = "Vent Status Prediction",
			description = "Displays estimates for missing vents",
			section = display
	)
	default boolean ventStatusPrediction()
	{
		return true;
	}
	@ConfigItem(
			position = 6,
			keyName = "showPlayersMiningCount",
			name = "Show players mining count",
			description = "Display the number of players mining the boulder",
			section = display
	)
	default boolean showPlayerCount()
	{
		return true;
	}
	@ConfigItem(
			position = 7,
			keyName = "chamberStatusPredictionColors",
			name = "Chamber status prediction colors",
			description = "Color chamber blocked or unblocked labels by whether the vent status is correct, wrong, or unknown",
			section = display
	)
	default boolean chamberStatusPredictionColors()
	{
		return false;
	}
	@ConfigItem(
			keyName = "showGuide",
			name = "Strategy Coach",
			description = "Always shows the next thing to do with the simulator's best solo strategy: mine a boulder, check a vent, fix or cap a chamber, take a rock, leave or teleport",
			position = 8,
			section = display
	)
	default boolean showGuide()
	{
		return false;
	}
	@ConfigItem(
			keyName = "showBoulderHealth",
			name = "Show Boulder Health",
			description = "Displays the boulder's remaining HP above the boulder (estimated for teammates' mining in a team)",
			position = 9,
			section = display
	)
	default boolean showBoulderHealth()
	{
		return false;
	}
	@ConfigItem(
			keyName = "coachHighlight",
			name = "Coach Highlights",
			description = "Highlights in the mine the boulder, vent, chamber, rock or staircase the coach's next step is about",
			position = 10,
			section = display
	)
	default boolean coachHighlight()
	{
		return true;
	}
	@ConfigItem(
			keyName = "coachPath",
			name = "Coach Path",
			description = "Draws the route for the coach's step as tiles (green) and the next step's (light orange); lava tiles, where you build a platform, are stronger",
			position = 12,
			section = display
	)
	default boolean coachPath()
	{
		return true;
	}
	@ConfigItem(
			keyName = "coachChat",
			name = "Coach Chat",
			description = "Says each new step from the coach in the game chat",
			position = 11,
			section = display
	)
	default boolean coachChat()
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
			description = "How the platform despawn timer is rendered",
			position = 3,
			section = timing
	)
	default TimingRenderMode platformRenderMode() {
		return TimingRenderMode.NUMBER;
	}
	@ConfigItem(
			keyName = "numberThreshold",
			name = "Platform Number Threshold",
			description = "Maximum number to display when a platform is up",
			position = 4,
			section = timing
	)
	@Range(
			max = 150,
			min = 0
	)
	default int numberThreshold() {
		return 25;
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
		return true;
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
	@ConfigItem(
			position = 7,
			keyName = "postResetVentFixNotifier",
			name = "Post Reset Vent Fix notifier",
			description = "Notifies on dangerous stability change 3 mins or prior after the reset",
			section = notifications
	)
	default boolean postResetVentFixNotifier()
	{
		return false;
	}
	@ConfigItem(
			position = 8,
			keyName = "idleNotification",
			name = "Idle Notification",
			description = "Repeatedly notifies while you are not moving or playing any animation",
			section = notifications
	)
	default boolean showIdleNotification()
	{
		return false;
	}
	@ConfigItem(
			keyName = "idleNotificationTime",
			name = "Idle Notification Time",
			description = "Number of seconds of inactivity before you start being notified",
			position = 9,
			section = notifications
	)
	@Range(
			max = 60,
			min = 1
	)
	@Units(Units.SECONDS)
	default int idleNotificationTime()
	{
		return 2;
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
			name = "Pickaxe Protection",
			description = "Options for customizing your predicted vent fix",
			position = 4,
			closedByDefault = true
	)
	String pickaxeprotection = "pickaxe protection";
	@ConfigItem(
			position = 0,
			keyName = "pickaxeDespawnProtection",
			name = "Pickaxe Despawn notifier",
			description = "Notifies when your pickaxe is about to despawn soon",
			section = pickaxeprotection
	)
	default boolean pickaxeDespawnProtection()
	{
		return true;
	}
	@ConfigItem(
			keyName = "pickaxeDespawnProtectionStartTime",
			name = "Pickaxe Despawn Start Time",
			description = "Notifies x seconds after you drop your pickaxe",
			position = 1,
			section = pickaxeprotection
	)
	@Range(
			max = 100,
			min = 1
	)
	@Units(Units.SECONDS)
	default int pickaxeDespawnProtectionStartTime()
	{
		return 75;
	}
	@ConfigItem(
			position = 2,
			keyName = "boulderFinishPickaxeLeaveProtection",
			name = "Boulder Finish Pickaxe Leave notifier",
			description = "Notifies if your pickaxe is still on the ground after finishing the boulder",
			section = pickaxeprotection
	)
	default boolean boulderFinishPickaxeLeaveProtection()
	{
		return true;
	}
	@ConfigItem(
			position = 3,
			keyName = "lowStabilityPickaxeLeaveProtection",
			name = "Low Stability Pickaxe Leave notifier",
			description = "Notifies if your pickaxe is still on the ground if stability gets low",
			section = pickaxeprotection
	)
	default boolean lowStabilityPickaxeLeaveProtection()
	{
		return true;
	}
	@ConfigItem(
			keyName = "pickaxeLeaveStabilityAmount",
			name = "Pickaxe Leave Stability Amount",
			description = "Notifies once stability is x amount or lower if pick is on the floor",
			position = 4,
			section = pickaxeprotection
	)
	@Range(
			max = 100,
			min = 1
	)
	default int pickaxeLeaveStabilityAmount()
	{
		return 35;
	}

	@ConfigSection(
			name = "Team Size Manager",
			description = "Notifications for any unexpected team size changes",
			position = 5,
			closedByDefault = true
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

	@ConfigSection(
			name = "Game chat output",
			description = "Options for diagnostic text printed to the game chat and console",
			position = 6,
			closedByDefault = true
	)
	String gameChatOutput = "gameChatOutput";
	@ConfigItem(
			keyName = "guideConsoleOutput",
			name = "Coach Decisions",
			description = "Print the coach's decisions (and the chances behind them) to the game chat and console",
			position = 0,
			section = gameChatOutput
	)
	default boolean guideConsoleOutput()
	{
		return false;
	}
	@ConfigItem(
			keyName = "mineStatusPredictionConsoleOutput",
			name = "Mine Status Predictions",
			description = "Print mine status predictions to the game chat and console",
			position = 1,
			section = gameChatOutput
	)
	default boolean mineStatusPredictionConsoleOutput()
	{
		return false;
	}
	@ConfigItem(
			keyName = "mineAuditLog",
			name = "Prediction Audit Log",
			description = "Log stability, vent moves, earthquakes and every stability prediction (with a check against what really happened) to the RuneLite log file",
			position = 2,
			section = gameChatOutput
	)
	default boolean mineAuditLog()
	{
		return false;
	}

	@ConfigSection(
			name = "Coach debugging",
			description = "Records games so the coach can be checked against the simulator (decisions, XP, walking times) and its display reviewed",
			position = 7,
			closedByDefault = true
	)
	String coachDebugging = "coachDebugging";
	@ConfigItem(
			keyName = "coachTrace",
			name = "Record Games",
			description = "Writes each game to .runelite/ultimate-volcanic-mine/coach/<time>/trace.jsonl: every tick, XP drop, point, click and coach decision with its inputs (for volcanicMine/verify_coach_trace.py)",
			position = 0,
			section = coachDebugging
	)
	default boolean coachTrace()
	{
		return true;
	}
	@ConfigItem(
			keyName = "coachScreenshots",
			name = "Screenshot Each Step",
			description = "Saves a screenshot (with the coach's panel and highlight) in the game's folder each time the coach shows a new step",
			position = 1,
			section = coachDebugging
	)
	default boolean coachScreenshots()
	{
		return true;
	}
	@ConfigItem(
			keyName = "coachDebug",
			name = "Debug View",
			description = "Shows the coach's internal state, what the highlight found, and labels every vent, chamber, rock and staircase the coach knows about",
			position = 2,
			section = coachDebugging
	)
	default boolean coachDebug()
	{
		return false;
	}
}
