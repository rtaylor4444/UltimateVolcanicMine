package com.ultimatevm;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.InterfaceID;
import net.runelite.client.Notifier;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.infobox.InfoBoxManager;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Text;
import net.runelite.api.widgets.Widget;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

@Slf4j
@PluginDescriptor(
	name = "Ultimate Volcanic Mine"
)
public class UltimateVolcanicMinePlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private Notifier notifier;

	@Inject
	private ClientThread clientThread;

	// RuneLite's shared background thread, for the stability predictions.
	@Inject
	private java.util.concurrent.ScheduledExecutorService executor;

	// Bumped for every prediction request (and when predictions are cleared) so a slow, outdated
	// background result is never shown.
	private final java.util.concurrent.atomic.AtomicInteger predictionGeneration = new java.util.concurrent.atomic.AtomicInteger();

	@Inject
	private UltimateVolcanicMineConfig config;

	@Inject
	private InfoBoxManager infoBoxManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private TimedObjectOverlay timedObjectOverlay;

	@Inject
	private ItemManager itemManager;

	public ItemManager getItemManager()
	{
		return itemManager;
	}

	public UltimateVolcanicMineConfig getConfig()
	{
		return config;
	}

	@Inject
	private BoulderHealthOverlay boulderHealthOverlay;

	@Inject
	private CoachOverlay coachOverlay;

	@Inject
	private CoachHighlightOverlay coachHighlightOverlay;

	@Inject
	private CoachDebugOverlay coachDebugOverlay;

	@Inject
	private CoachPathOverlay coachPathOverlay;

	@Inject
	private net.runelite.client.ui.DrawManager drawManager;

	// Coach debugging: one folder per game with the trace and a screenshot per step.
	private CoachTracer coachTracer;
	private int coachStep;
	private String lastHighlightStatus;
	private int lastMiningXp = -1;
	private int lastPoints = -1;
	private int gameStartMiningXp = -1;

	// Where the mine's vents, chambers, rocks and staircases are (for the coach's highlights).
	private final MineLayout mineLayout = new MineLayout();
	// The simulator's best strategy, played alongside: always the one next step.
	private StrategyCoach coach;
	private StrategyCoach.Observation lastObservation;
	private String lastCoachText;
	private String lastCoachNext;
	// Large Rocks the player is carrying (taken from a pile, used up blocking a chamber).
	private int rocksInHand;
	// Chamber flips this game (each pays as a cap, up to 6).
	private int chamberFlips;
	// A player leaving with more than 6:30 on the clock leaves the mine scaled to the starting team.
	static final int MINE_SCALED_CUTOFF_TICKS = 650;
	// The coach's predictor waits while more vent combinations than this are possible (never, in
	// practice: 41^3 at the start, 51^3 after the reset; its heavy work runs in the background).
	private static final int MAX_COACH_COMBOS = 200000;

	// Which vent values are still possible, from every stability change and vent check so far.
	private final VentInference ventInference = new VentInference();
	// Above this many possible vent combinations, the inference isn't used for predictions yet
	// (usually only right after the start of each half). An earthquake can push a narrowed-down set
	// back up past a few thousand, so this is set high enough that the blow-up chance doesn't vanish
	// then: 10,000 takes about 0.2 s on the background thread.
	private static final int MAX_INFERENCE_COMBOS_FOR_PREDICTION = 10000;

	//Constants
	private static final int PROC_VOLCANIC_MINE_SET_OTHERINFO = 2022;
	private static final int VARBIT_STABILITY = 5938;
	private static final int VARBIT_GAME_STATE = 5941;
	private static final int VARBIT_TIME_REMAINING = 5944;
	private static final int VARBIT_VENT_STATUS_A = 5939;
	private static final int VARBIT_VENT_STATUS_B = 5940;
	private static final int VARBIT_VENT_STATUS_C = 5942;
	private static final int VARBIT_CHAMBER_STATUS = 5936;
	private static final int VARBIT_POINTS = 5934;
	private static final int VARBIT_PLAYER_COUNT = 5937;
	private static final int HUD_STABILITY_COMPONENT = 11;
	private static final int VM_GAME_STATE_NONE = 0;
	private static final int VM_GAME_STATE_IN_LOBBY = 1;
	private static final int VM_GAME_STATE_IN_GAME = 2;
	private static final int VM_REGION_NORTH = 15263;
	private static final int VM_REGION_SOUTH = 15262;
	private static final int GAME_OBJ_CHAMBER_BLOCKED = 31044;
	private static final int GAME_OBJ_CHAMBER_UNBLOCKED = 31043;
	private static final int GAME_OBJ_TAKEN_ROCK = 31046;
	private static final int GAME_OBJ_ROCK = 31045;
	private static final int VM_EXIT_TIME = 50;
	private static final int VM_LOBBY_TIME = 50;
	private static final int VM_TOTAL_GAME_TICKS = 1000;
	private static final int VM_TOTAL_GAME_SECONDS = 600;
	private static final int BLOW_UP_SIMULATION_END_SECONDS = 570;
	private static final int CHAT_TIMELINE_CHUNK_SIZE = 220;
	private static final int TICKS_PER_STABILITY_UPDATE = 25;
	private static final int STABILITY_SIMULATION_OFFSET_TICKS = 24;
	private static final float SECONDS_TO_TICKS = 1.666f;


	private StabilityTracker stabilityTracker = new StabilityTracker();
	private StabilityTracker futureStabilityTracker = new StabilityTracker();
	private VMNotifier VM_notifier;
	private CapCounter capCounter = new CapCounter();
	private TimedObjectTracker timedObjectTracker = new TimedObjectTracker();
	private CapCounterInfoBox capInfoBox;
	private BlowUpChanceInfoBox blowUpChanceInfoBox;
	private boolean blowUpChanceInfoBoxShown;
	private PickaxeProtector pickaxeProtector;
	private boolean predictionsResetAtFiveMinutes;
	private int lastKnownChamberStatus = -1;
	private int vmGameState = VM_GAME_STATE_NONE;
	private int timeRemainingFromServer, estimatedTimeRemaining;
	private int eruptionTime, ventWarningTime;
	private int maxPlayerCount, ticksSinceLobbyStart;
	private PlayerCountInfoBox playerCountBox;
	private VentStatusOverlayOverride ventStatusOverlayOverride;
	private final MineAuditLogger mineAuditLogger = new MineAuditLogger();

	@Inject
	private net.runelite.client.eventbus.EventBus eventBus;
	private MovementTracer movementTracer;

	// Dev tools for the route-time model: "::vmdump" writes the loaded tiles/objects to a JSON
	// file; "::vmtrace" toggles a per-tick movement log.
	@Subscribe
	public void onCommandExecuted(CommandExecuted event)
	{
		String result = null;
		if (MapDumper.COMMAND.equalsIgnoreCase(event.getCommand()))
		{
			result = new MapDumper(client).dump();
		}
		else if (MovementTracer.COMMAND.equalsIgnoreCase(event.getCommand()))
		{
			if (movementTracer != null && movementTracer.isOn())
			{
				eventBus.unregister(movementTracer);
				result = movementTracer.stop();
			}
			else
			{
				movementTracer = new MovementTracer(client);
				result = movementTracer.start();
				if (movementTracer.isOn())
				{
					eventBus.register(movementTracer);
				}
			}
		}
		if (result != null)
		{
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", result, null);
		}
	}

	private void stopMovementTracer()
	{
		if (movementTracer != null && movementTracer.isOn())
		{
			eventBus.unregister(movementTracer);
			movementTracer.stop();
		}
	}
	// Simulation time (seconds) the player walked away from the boulder, or null while at it - lets
	// the guide time a "Fix X now" detour from when the player actually set off.
	private Integer awayFromBoulderSince;
	// Further than this (tiles) from the tracked boulder counts as having left it.
	private static final int AWAY_FROM_BOULDER_DISTANCE = 4;
	private IdlePlayerTracker idlePlayerTracker = new IdlePlayerTracker();

	// Game ticks since the game started (for the notifications' start-of-game cooldown).
	private int gameTicks;
	// The vent varbits last tick, to spot a vent being checked.
	private final int[] lastRawVents = {VentRules.UNKNOWN, VentRules.UNKNOWN, VentRules.UNKNOWN};
	// The predicted next stability change only counts when the possible vents agree this closely.
	private static final int MAX_NEXT_CHANGE_SPREAD = 6;


	@Provides
	UltimateVolcanicMineConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(UltimateVolcanicMineConfig.class);
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		eruptionTime = (int) (config.eruptionWarningTime() * SECONDS_TO_TICKS);
		ventWarningTime = (int) (config.ventWarningTime() * SECONDS_TO_TICKS);

		stabilityTracker.setDisplayCount(config.stabilityUpdateHistoryCount());
		futureStabilityTracker.setDisplayCount(config.predictedStabilityChangeHistoryCount());

		infoBoxManager.removeInfoBox(capInfoBox);
		if(config.capCounter() && capCounter.getTimesCapped() >= 1) infoBoxManager.addInfoBox(capInfoBox);

		if (!config.showBlowUpChance())
		{
			removeBlowUpChanceInfoBox();
		}
		else if (blowUpChanceInfoBox != null && blowUpChanceInfoBox.hasValue())
		{
			showBlowUpChanceInfoBox();
		}

		overlayManager.remove(timedObjectOverlay);
		if(config.rockTimer() || config.platformTimer()) overlayManager.add(timedObjectOverlay);

		overlayManager.remove(boulderHealthOverlay);
		if(config.showBoulderHealth()) overlayManager.add(boulderHealthOverlay);

		overlayManager.remove(coachOverlay);
		overlayManager.remove(coachHighlightOverlay);
		overlayManager.remove(coachDebugOverlay);
		overlayManager.remove(coachPathOverlay);
		if(config.showGuide() || config.coachDebug())
		{
			overlayManager.add(coachPathOverlay);
			overlayManager.add(coachOverlay);
			overlayManager.add(coachHighlightOverlay);
		}
		if(config.coachDebug()) overlayManager.add(coachDebugOverlay);
		if (!config.coachTrace() && coachTracer != null)
		{
			coachTracer.stop(null);
		}

		if (ventStatusOverlayOverride != null)
		{
			clientThread.invokeLater(() ->
			{
				if (config.ventStatusPrediction())
				{
					ventStatusOverlayOverride.overrideVentStatusWidgetsFromVarbits();
				}
				ventStatusOverlayOverride.updateChamberStatusWidgetColors(config.chamberStatusPredictionColors());
				return true;
			});
		}
	}

	@Override
	protected void startUp() throws Exception {
		VM_notifier = new VMNotifier(config);
        pickaxeProtector = new PickaxeProtector(client);
		ventStatusOverlayOverride = new VentStatusOverlayOverride(
			client,
			VARBIT_VENT_STATUS_A,
			VARBIT_VENT_STATUS_B,
			VARBIT_VENT_STATUS_C
		);
		ventStatusOverlayOverride.setVentInference(ventInference);
		stabilityTracker.setDisplayCount(config.stabilityUpdateHistoryCount());
		futureStabilityTracker.setDisplayCount(config.predictedStabilityChangeHistoryCount());
		capInfoBox = new CapCounterInfoBox(capCounter, this);
		blowUpChanceInfoBox = new BlowUpChanceInfoBox(this);
		blowUpChanceInfoBoxShown = false;
		coach = new StrategyCoach(this::coachPredictor);
		coach.setBackground(executor);
		coachTracer = new CoachTracer(drawManager, executor);
		coach.setTrace((type, fields) -> coachTracer.record(type, fields));
		coachDebugOverlay.setLines(this::coachDebugLines);
		coachPaths = CoachPaths.load();
		coachPathOverlay.setSources(mineLayout, coachPaths);
		coachHighlightOverlay.setLayout(mineLayout);
		timedObjectOverlay.setRockTracker(timedObjectTracker);
		overlayManager.add(timedObjectOverlay);
		if(config.showBoulderHealth()) overlayManager.add(boulderHealthOverlay);
		if(config.showGuide() || config.coachDebug())
		{
			overlayManager.add(coachPathOverlay);
			overlayManager.add(coachOverlay);
			overlayManager.add(coachHighlightOverlay);
		}
		if(config.coachDebug()) overlayManager.add(coachDebugOverlay);
		eruptionTime = (int) (config.eruptionWarningTime() * SECONDS_TO_TICKS);
		ventWarningTime = (int) (config.ventWarningTime() * SECONDS_TO_TICKS);
	}

	@Override
	protected void shutDown() throws Exception
	{
		stopMovementTracer();
		ventStatusOverlayOverride = null;
		overlayManager.remove(timedObjectOverlay);
		overlayManager.remove(boulderHealthOverlay);
		overlayManager.remove(coachOverlay);
		overlayManager.remove(coachHighlightOverlay);
		overlayManager.remove(coachDebugOverlay);
		overlayManager.remove(coachPathOverlay);
		if (coachTracer != null)
		{
			coachTracer.shutdown();
		}
		infoBoxManager.removeInfoBox(capInfoBox);
		removeBlowUpChanceInfoBox();
		boulderHealthOverlay.resetTracking();
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged)
	{
		// A new scene: its objects are spawned again after loading.
		if (gameStateChanged.getGameState() == GameState.LOADING)
		{
			mineLayout.clear();
		}
		if(!isInVM()) return;
		vmGameState = client.getVarbitValue(VARBIT_GAME_STATE);
		if (vmGameState == VM_GAME_STATE_IN_LOBBY) {
			stabilityTracker.initialize();
			futureStabilityTracker.initialize();
			stabilityTracker.setDisplayCount(config.stabilityUpdateHistoryCount());
			futureStabilityTracker.setDisplayCount(config.predictedStabilityChangeHistoryCount());
			resetGameVariables();
		}
	}

	@Subscribe
	public void onGameTick(GameTick tick) {
		if(!isInVM()) {
			vmGameState = VM_GAME_STATE_NONE;
			infoBoxManager.removeInfoBox(capInfoBox);
			clearBlowUpChance();
			resetGameVariables();
			return;
		}

		if(maxPlayerCount > config.expectedTeamSize())
			VM_notifier.notify(notifier, VMNotifier.NotificationEvents.VM_EXTRA_PLAYER, gameTicks);

		int newTimeRemaining = client.getVarbitValue(VARBIT_TIME_REMAINING);
		if(newTimeRemaining != timeRemainingFromServer) {
			estimatedTimeRemaining = timeRemainingFromServer = newTimeRemaining;
		} else --estimatedTimeRemaining;

        pickaxeProtector.getStartingPickaxes();
        if(pickaxeProtector.isPickaxeDropped()) pickaxeProtector.incrementTicksDropped();
		else pickaxeProtector.resetTicksDropped();
		if(config.pickaxeDespawnProtection()) {
			int requiredDroppedTicks = (int)(config.pickaxeDespawnProtectionStartTime() * SECONDS_TO_TICKS);
			if(requiredDroppedTicks <= pickaxeProtector.getNumTicksPickaxeDropped())
				VM_notifier.notify(notifier, VMNotifier.NotificationEvents.VM_PICKAXE_DESPAWN, ticksSinceLobbyStart);
		}

		//Idle notifier shouldn't fire during the pre-game wait or while the boulder is rolling to its next stage
		if(hasGameStarted() && !boulderHealthOverlay.isBoulderMoving()) {
			idlePlayerTracker.onGameTick(client.getLocalPlayer());
			if(config.showIdleNotification()) {
				int requiredIdleTicks = (int)(config.idleNotificationTime() * SECONDS_TO_TICKS);
				if(requiredIdleTicks <= idlePlayerTracker.getIdleTicks())
					VM_notifier.notify(notifier, VMNotifier.NotificationEvents.VM_IDLE_PLAYER, ticksSinceLobbyStart);
			}
		} else {
			idlePlayerTracker.reset();
		}
		++ticksSinceLobbyStart;

		boulderHealthOverlay.onGameTick();

        if(!hasGameStarted())
		{
			// Waiting in the lobby: say which staircase to go down.
			if (coach != null && client.getVarbitValue(VARBIT_GAME_STATE) == VM_GAME_STATE_IN_LOBBY)
			{
				showCoach(StrategyCoach.lobbyInstruction());
			}
			return;
		}

		++gameTicks;
		timedObjectTracker.updateRockTimers();

		int ventStatusA = client.getVarbitValue(VARBIT_VENT_STATUS_A);
		int ventStatusB = client.getVarbitValue(VARBIT_VENT_STATUS_B);
		int ventStatusC = client.getVarbitValue(VARBIT_VENT_STATUS_C);
		int chamberStatus = client.getVarbitValue(VARBIT_CHAMBER_STATUS);
		int currentStability = client.getVarbitValue(VARBIT_STABILITY);
		int combosBefore = ventInference.getCount();
		ventInference.onTick(estimatedTimeRemaining, currentStability, new int[]{ventStatusA, ventStatusB, ventStatusC},
			chamberStatus, client.getVarbitValue(VARBIT_PLAYER_COUNT));
		VentInference.EarthquakeEffect earthquakeEffect = ventInference.takeEarthquakeEffect();
		if (earthquakeEffect != null && earthquakeEffect != VentInference.EarthquakeEffect.NONE)
		{
			String what = earthquakeEffect == VentInference.EarthquakeEffect.BOTH_CANCELLED
				? "Earthquake on a vent move and stability update: neither happened this time."
				: earthquakeEffect == VentInference.EarthquakeEffect.VENT_MOVE_CANCELLED
				? "Earthquake on a vent move: the vents didn't move this time."
				: "Earthquake on a stability update: stability didn't change this time.";
			addMineStatusChatMessage(what);
			logMineStatusPrediction("UVM Sim | earthquake effect={}, rem={}", earthquakeEffect, estimatedTimeRemaining);
			// The possible futures just changed; recalculate now rather than at the next update.
			refreshBlowUpChance(currentStability, chamberStatus);
		}
		// The possible vent values narrow on stability changes too, not just vent varbit changes.
		if (ventInference.getCount() != combosBefore && config.ventStatusPrediction() && ventStatusOverlayOverride != null)
		{
			ventStatusOverlayOverride.overrideVentStatusWidgetsFromVarbits();
			ventStatusOverlayOverride.updateChamberStatusWidgetColors(config.chamberStatusPredictionColors());
		}
		if (config.mineAuditLog())
		{
			mineAuditLogger.onTick(estimatedTimeRemaining, timeRemainingFromServer, currentStability,
				ventStatusA, ventStatusB, ventStatusC, chamberStatus, client.getVarbitValue(VARBIT_PLAYER_COUNT));
		}
		if (shouldResetPredictionsAtFiveMinuteMark())
		{
			resetPredictionsForFiveMinuteMark(currentStability, chamberStatus);
		}
		if (lastKnownChamberStatus == -1)
		{
			lastKnownChamberStatus = chamberStatus;
		}

		boolean ventChecked = updateCheckedVents(ventStatusA, ventStatusB, ventStatusC);
		if (ventChecked)
		{
			refreshBlowUpChance(currentStability, chamberStatus);
		}
		// On each vent move, predict the next stability change from the possible vents (the "Stab."
		// line and the predicted vent fix warning).
		if(estimatedTimeRemaining % 10 == 0) {
			int futureChange = predictNextStabilityChange(chamberStatus, toSimulationTimeSeconds(estimatedTimeRemaining));
			if(futureChange != VentRules.UNKNOWN) {
				futureStabilityTracker.addChange(futureChange);
				if (futureStabilityTracker.isFutureStabilityBad(config.predictedStabilityChange())) {
					int startTime = 900 - (int)(config.predictedventWarningStartTime() * SECONDS_TO_TICKS);
					int endTime = 600 + (int)(config.predictedventWarningEndTime() * SECONDS_TO_TICKS);
					if(estimatedTimeRemaining < startTime && estimatedTimeRemaining > endTime)
						VM_notifier.notify(notifier, VMNotifier.NotificationEvents.VM_PREDICTED_VENT_FIX, gameTicks);
				}
			}
		}

		int simulationTime = toSimulationTimeSeconds(estimatedTimeRemaining);
		updateAwayFromBoulder(simulationTime);
		if (simulationTime >= BLOW_UP_SIMULATION_END_SECONDS)
		{
			clearBlowUpChance();
		}

		boolean stabilityChanged = updateStability(currentStability);
		if(stabilityChanged) {
			if(config.ventStatusUpdateHistory()) {
				Widget widget = client.getWidget(ComponentID.VOLCANIC_MINE_VENT_A_PERCENTAGE+1);
				client.addChatMessage(ChatMessageType.GAMEMESSAGE, "CyanWarrior4: ", ventStatusOverlayOverride.ventDisplayText(0, widget), null);
				widget = client.getWidget(ComponentID.VOLCANIC_MINE_VENT_B_PERCENTAGE+1);
				client.addChatMessage(ChatMessageType.GAMEMESSAGE, "CyanWarrior4: ", ventStatusOverlayOverride.ventDisplayText(1, widget), null);
				widget = client.getWidget(ComponentID.VOLCANIC_MINE_VENT_C_PERCENTAGE+1);
				client.addChatMessage(ChatMessageType.GAMEMESSAGE, "CyanWarrior4: ", ventStatusOverlayOverride.ventDisplayText(2, widget), null);
				client.addChatMessage(ChatMessageType.GAMEMESSAGE, "CyanWarrior4: ", "Stability Update: " + stabilityTracker.getCurrentChange(), null);
				client.addChatMessage(ChatMessageType.GAMEMESSAGE, "CyanWarrior4: ", "Time: " + estimatedTimeRemaining, null);
			}

			//Check if we have to fix vents now
			if(stabilityTracker.getCurrentChange() < 0 && estimatedTimeRemaining > 595)
				VM_notifier.notify(notifier, VMNotifier.NotificationEvents.VM_PRE_RESET_VENT_FIX, gameTicks);

			//Check if we should have all 3 vents fixed (post-reset)
			boolean isPostResetTime = (estimatedTimeRemaining <= (VentRules.RESET_TIME_TICKS-15) && estimatedTimeRemaining > 305);
			if(currentStability + (stabilityTracker.getCurrentChange() * 4) <= 0 && isPostResetTime)
				VM_notifier.notify(notifier, VMNotifier.NotificationEvents.VM_POST_RESET_VENT_FIX, gameTicks);
		}

		boolean simulationTick = shouldRunSimulationThisTick(simulationTime);
		if (simulationTick)
		{
			refreshBlowUpChance(currentStability, chamberStatus);
		}
		updateCoach(currentStability, chamberStatus, ventStatusA, ventStatusB, ventStatusC, simulationTick);

		int currentTick = gameTicks;
		if (estimatedTimeRemaining <= (VentRules.RESET_TIME_TICKS + ventWarningTime))
			VM_notifier.notify(notifier, VMNotifier.NotificationEvents.VM_RESET, currentTick);

		if (estimatedTimeRemaining <= eruptionTime) {
			VM_notifier.notify(notifier, VMNotifier.NotificationEvents.VM_ERUPTION, currentTick);
		}

		if (ventStatusOverlayOverride != null)
		{
			ventStatusOverlayOverride.updateChamberStatusWidgetColors(config.chamberStatusPredictionColors());
		}


		if (config.showPlayerCount() && hasGameStarted())
			{
			if (playerCountBox == null)
			{
				playerCountBox = new PlayerCountInfoBox(this, client);
				infoBoxManager.addInfoBox(playerCountBox);
			}
			playerCountBox.updatePlayerCount();
			}
		else if (playerCountBox != null)
		{
			infoBoxManager.removeInfoBox(playerCountBox);
			playerCountBox = null;
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (event.getType() != ChatMessageType.GAMEMESSAGE && event.getType() != ChatMessageType.SPAM)
			return;

		// Remove colour and line breaks
		String chatMsg = Text.removeTags(event.getMessage());

		// Mark when an earthquake occurs
		if (chatMsg.equals("A sudden earthquake strikes the cavern!")) {
			// It lands on the game tick about to be processed: if that's a vent move or stability
			// update, it was cancelled - the vent inference takes that as fact.
			ventInference.onEarthquakeMessage();
			mineAuditLogger.onEarthquake();
			traceEvent("earthquake");
		}

		boolean inVm = isInVM();
		boolean successfulMine = isSuccessfulBoulderMineMessage(chatMsg);

		if (inVm && successfulMine && boulderHealthOverlay.getBoulderLocation() != null) {
			boulderHealthOverlay.decrementHealth();
		}
	}
	//Helper functions for testing
	public boolean updateStability(int newStability) {
		if(config.lowStabilityPickaxeLeaveProtection()) {
			if (newStability <= config.pickaxeLeaveStabilityAmount() && pickaxeProtector.isPickaxeDropped())
				VM_notifier.notify(notifier, VMNotifier.NotificationEvents.VM_PICKAXE_LOW_STABILITY, gameTicks);
		}

		if(stabilityTracker.updateStability(newStability)) {
			return true;
		}
		return false;
	}

	static boolean shouldLeaveAfterPlayerLeft(int remainingTicks)
	{
		return remainingTicks > MINE_SCALED_CUTOFF_TICKS;
	}

	/** The coach's predictor: every set of vents still possible, or null while there are too many. */
	private StrategyPredictor coachPredictor(StrategyCoach.Observation o)
	{
		int combos = ventInference.getCount();
		if (combos == 0 || combos > MAX_COACH_COMBOS)
		{
			return null;
		}
		return new StrategyPredictor(ventInference.getCandidates(), ventInference.getWeights(), o.chamberStatus, o.stability,
			o.startSecond);
	}

	private StrategyCoach.Observation observe(int currentStability, int chamberStatus, int ventA, int ventB, int ventC,
		boolean stabilityUpdate)
	{
		StrategyCoach.Observation o = new StrategyCoach.Observation();
		int clamped = Math.max(0, Math.min(VM_TOTAL_GAME_TICKS, estimatedTimeRemaining));
		o.now = (VM_TOTAL_GAME_TICKS - clamped) * 0.6;
		o.startSecond = toSimulationStartSeconds(estimatedTimeRemaining);
		o.stability = currentStability;
		o.chamberStatus = chamberStatus;
		o.rawVents = new int[]{ventA, ventB, ventC};
		o.boulderNumber = boulderHealthOverlay.getCurrentBoulderNumber();
		o.atBoulder = isAtBoulder(o.boulderNumber);
		o.boulderSecondsRemaining = boulderHealthOverlay.getCurrentHealth() > 0 ? boulderHealthOverlay.estimateSecondsRemaining() : -1;
		o.rocksInHand = rocksInHand;
		o.capsMade = chamberFlips;
		o.playerCount = Math.max(1, client.getVarbitValue(VARBIT_PLAYER_COUNT));
		o.stabilityUpdate = stabilityUpdate;
		o.predictorVersion = java.util.Objects.hash(ventInference.getCount(), ventA, ventB, ventC, estimatedTimeRemaining / 10);
		o.rockAvailable = new boolean[]{
			mineLayout.rockAvailable(MineLayout.Spot.ROCK_A),
			mineLayout.rockAvailable(MineLayout.Spot.ROCK_B),
			mineLayout.rockAvailable(MineLayout.Spot.ROCK_C)};
		return o;
	}

	// Boulder footprints (tiles) by boulder number; the tracked location is near the middle.
	private static final int[] BOULDER_SIZE = {8, 8, 8, 6, 4, 2};

	/** At the boulder: mining it, or standing next to it (big boulders are mined from a corner). */
	private boolean isAtBoulder(int boulderNumber)
	{
		WorldPoint boulder = boulderHealthOverlay.getBoulderLocation();
		Player player = client.getLocalPlayer();
		if (boulder == null || player == null)
		{
			return false;
		}
		if (VolcanicMineMiningAnimation.VOLCANIC_MINE_ANIMATIONS.contains(player.getAnimation()))
		{
			return true;
		}
		int size = BOULDER_SIZE[Math.max(0, Math.min(5, boulderNumber))];
		return player.getWorldLocation().distanceTo(boulder) <= size / 2 + 2;
	}

	private void updateCoach(int currentStability, int chamberStatus, int ventA, int ventB, int ventC, boolean stabilityUpdate)
	{
		if (coach == null || estimatedTimeRemaining < 0)
		{
			return;
		}
		if (coachTracer != null)
		{
			coachTracer.setClock(gameTicks, estimatedTimeRemaining);
			if (config.coachTrace() && !coachTracer.isOn())
			{
				startCoachTrace(currentStability);
			}
		}
		StrategyCoach.Observation o = observe(currentStability, chamberStatus, ventA, ventB, ventC, stabilityUpdate);
		lastObservation = o;
		StrategyCoach.Instruction instruction = coach.update(o);
		showCoach(instruction);
		traceTick(o);
	}

	private void startCoachTrace(int stability)
	{
		java.util.Map<String, Object> header = new java.util.LinkedHashMap<>();
		header.put("format", "uvm-coach-1");
		header.put("players", client.getVarbitValue(VARBIT_PLAYER_COUNT));
		header.put("stability", stability);
		header.put("miningXp", client.getSkillExperience(Skill.MINING));
		header.put("miningLevel", client.getRealSkillLevel(Skill.MINING));
		header.put("points", client.getVarbitValue(VARBIT_POINTS));
		coachTracer.start(header);
		coachStep = 0;
		lastHighlightStatus = null;
		gameStartMiningXp = client.getSkillExperience(Skill.MINING);
		lastMiningXp = gameStartMiningXp;
		lastPoints = client.getVarbitValue(VARBIT_POINTS);
		if (coachTracer.isOn() && config.coachChat())
		{
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "UVM", "Recording this game for the coach check: " + coachTracer.getFolder().getName(), null);
		}
	}

	private void traceTick(StrategyCoach.Observation o)
	{
		if (coachTracer == null || !coachTracer.isOn())
		{
			return;
		}
		java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
		m.put("now", o.now);
		Player player = client.getLocalPlayer();
		WorldPoint origin = mineLayout.westStairs();
		if (player != null)
		{
			WorldPoint at = player.getWorldLocation();
			if (origin != null)
			{
				m.put("dx", at.getX() - origin.getX());
				m.put("dy", at.getY() - origin.getY());
			}
			m.put("plane", at.getPlane());
			m.put("anim", player.getAnimation());
		}
		m.put("stab", o.stability);
		m.put("chamber", o.chamberStatus);
		m.put("vents", o.rawVents);
		m.put("boulder", o.boulderNumber);
		m.put("health", boulderHealthOverlay.getCurrentHealth());
		m.put("atBoulder", o.atBoulder);
		m.put("rocks", o.rocksInHand);
		m.put("caps", o.capsMade);
		m.put("combos", ventInference.getCount());
		m.put("step", coachStep);
		coachTracer.record("tick", m);
		String highlight = coachHighlightOverlay.getStatus();
		if (highlight != null && !highlight.equals(lastHighlightStatus))
		{
			lastHighlightStatus = highlight;
			coachTracer.record("highlight", java.util.Collections.singletonMap("status", highlight));
		}
	}

	private void traceEvent(String type, Object... keyValues)
	{
		if (coachTracer == null || !coachTracer.isOn())
		{
			return;
		}
		java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
		for (int i = 0; i + 1 < keyValues.length; i += 2)
		{
			m.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
		}
		coachTracer.record(type, m);
	}

	private List<String> coachDebugLines()
	{
		List<String> lines = new ArrayList<>(coach == null ? Collections.emptyList() : coach.debugLines());
		lines.add("Highlight: " + coachHighlightOverlay.getStatus());
		lines.add("Rocks in hand: " + rocksInHand + "   chamber flips (caps): " + chamberFlips
			+ "   possible vents: " + ventInference.getCount());
		List<String> missing = new ArrayList<>();
		for (MineLayout.Spot spot : MineLayout.Spot.values())
		{
			if (mineLayout.find(spot) == null)
			{
				missing.add(spot.name().toLowerCase());
			}
		}
		lines.add(missing.isEmpty() ? "Mine spots: all found" : "Mine spots missing: " + String.join(", ", missing));
		lines.add("Recording: " + (coachTracer != null && coachTracer.isOn() ? coachTracer.getFolder().getName() : "off"));
		return lines;
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		if (event.getSkill() != Skill.MINING || !isInVM())
		{
			return;
		}
		int xp = event.getXp();
		if (lastMiningXp >= 0 && xp > lastMiningXp)
		{
			traceEvent("xp", "delta", xp - lastMiningXp, "total", xp - Math.max(0, gameStartMiningXp),
				"boulder", boulderHealthOverlay.getCurrentBoulderNumber());
		}
		lastMiningXp = xp;
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (coachTracer == null || !coachTracer.isOn())
		{
			return;
		}
		traceEvent("click", "option", Text.removeTags(event.getMenuOption()), "target", Text.removeTags(event.getMenuTarget()),
			"id", event.getId(), "action", String.valueOf(event.getMenuAction()));
	}

	// Paths between the coach's places (also used to pick the nearer staircase to leave by).
	private CoachPaths coachPaths;

	/** "Leave" goes to whichever staircase is quicker from where you are (East by default). */
	private StrategyCoach.Instruction nearestExit(StrategyCoach.Instruction instruction)
	{
		if (instruction == null || instruction.action != CoachAction.LEAVE || coachPaths == null)
		{
			return instruction;
		}
		WorldPoint origin = mineLayout.westStairs();
		Player player = client.getLocalPlayer();
		if (origin == null || player == null)
		{
			return instruction;
		}
		int dx = player.getWorldLocation().getX() - origin.getX();
		int dy = player.getWorldLocation().getY() - origin.getY();
		String from = instruction.from != null ? instruction.from : "start";
		double east = CoachPaths.ticks(coachPaths.route(from, "E stair", dx, dy));
		double west = CoachPaths.ticks(coachPaths.route(from, "W stair", dx, dy));
		return west < east ? instruction.byWestStairs() : instruction;
	}

	private void showCoach(StrategyCoach.Instruction instruction)
	{
		instruction = nearestExit(instruction);
		coachOverlay.setInstruction(instruction);
		coachHighlightOverlay.setInstruction(instruction, boulderHealthOverlay.getCurrentBoulderNumber());
		coachPathOverlay.setInstruction(instruction);
		for (String line : coach.takeLog())
		{
			traceEvent("note", "text", line);
			if (config.guideConsoleOutput())
			{
				log.info("UVM Coach | {}", line);
				client.addChatMessage(ChatMessageType.GAMEMESSAGE, "UVM", line, null);
			}
		}
		String text = instruction == null ? null : instruction.toString();
		if (text != null && !text.equals(lastCoachText))
		{
			if (config.showGuide() && config.coachChat() && instruction.action != CoachAction.WAIT)
			{
				client.addChatMessage(ChatMessageType.GAMEMESSAGE, "UVM", "Next: " + text, null);
			}
			if (coachTracer != null && coachTracer.isOn())
			{
				coachStep++;
				String shot = config.coachScreenshots() && config.showGuide() ? coachTracer.screenshot(instruction.action + "_" + instruction.text) : null;
				traceEvent("step", "step", coachStep, "action", String.valueOf(instruction.action), "spot", String.valueOf(instruction.spot),
					"text", instruction.text, "detail", instruction.detail, "next", instruction.next, "from", instruction.from, "place", instruction.place,
					"nextPlace", instruction.nextPlace, "urgent", instruction.urgent, "screenshot", shot);
			}
		}
		lastCoachText = text;
		// Every change to the "Next" line (the verifier checks the forecasts against what came next).
		String next = instruction == null ? null : instruction.next;
		if (!java.util.Objects.equals(next, lastCoachNext))
		{
			lastCoachNext = next;
			traceEvent("next", "next", next, "nextPlace", instruction == null ? null : instruction.nextPlace,
				"during", instruction == null ? null : instruction.text);
		}
	}

	private void refreshBlowUpChance(int currentStability, int chamberStatus)
	{
		int simulationTime = toSimulationTimeSeconds(estimatedTimeRemaining);
		if (estimatedTimeRemaining < 0 || simulationTime >= BLOW_UP_SIMULATION_END_SECONDS)
		{
			clearBlowUpChance();
			return;
		}

		predictFromPossibleVents(currentStability, chamberStatus, toSimulationStartSeconds(estimatedTimeRemaining));
	}

	/**
	 * The one prediction: simulates every set of vent values still possible (checked vents and
	 * the stability history narrow them down) and shows the blow-up chance and the stability range
	 * (covering every earthquake possibility). Runs once per stability update, and when a vent is
	 * checked or a chamber flips.
	 */
	private void predictFromPossibleVents(int currentStability, int chamberStatus, int simulationTime)
	{
		int combos = ventInference.getCount();
		if (combos == 0 || combos > MAX_INFERENCE_COMBOS_FOR_PREDICTION)
		{
			logMineStatusPrediction("UVM Sim Skip | reason={} possible vent combinations, time={}s, stability={}",
				combos, simulationTime, currentStability);
			clearBlowUpChance();
			return;
		}

		// Simulated on RuneLite's background thread so even a large set of combinations on a slow
		// computer can't stall the game; the result is applied back on the client thread. Only the
		// latest request's result is used.
		List<int[]> candidates = ventInference.getCandidates();
		double[] weights = ventInference.getWeights();
		int generation = predictionGeneration.incrementAndGet();
		executor.execute(() ->
		{
			Simulator.Prediction prediction = Simulator.predict(candidates, weights, chamberStatus, currentStability, simulationTime);
			clientThread.invokeLater(() ->
			{
				if (generation != predictionGeneration.get())
				{
					return;
				}
				setBlowUpChance(prediction.blowUpChancePercent);
				printBlowUpTimelineToChat(prediction.blowUpChancePercent, prediction.stabilityRangeOverTime);
				mineAuditLogger.onPrediction("vents(" + combos + " combos)", simulationTime, currentStability,
					prediction.stabilityRangeOverTime, prediction.likely);
				logMineStatusPrediction(
					"UVM Sim | time={}s, combos={}, restarts={}, stability={}, chamberStatus={}, blowUpChance={}%, rangeTimeline={}",
					simulationTime, combos, ventInference.getRestarts(), currentStability, chamberStatus,
					formatPercentage(prediction.blowUpChancePercent), formatStabilityRangeTimeline(prediction.stabilityRangeOverTime));
			});
		});
	}

	/**
	 * The next stability change predicted from the possible vents: the worst or best case (per the
	 * Predicted Stability Change setting), or {@link VentRules#UNKNOWN} while they disagree by more
	 * than {@link #MAX_NEXT_CHANGE_SPREAD}.
	 */
	private int predictNextStabilityChange(int chamberStatus, int simulationTime)
	{
		int combos = ventInference.getCount();
		if (combos == 0 || combos > MAX_INFERENCE_COMBOS_FOR_PREDICTION)
		{
			return VentRules.UNKNOWN;
		}
		int[] range = Simulator.nextStabilityChangeRange(ventInference.getCandidates(), chamberStatus, simulationTime);
		if (range == null || range[1] - range[0] > MAX_NEXT_CHANGE_SPREAD)
		{
			return VentRules.UNKNOWN;
		}
		return config.predictedVentFixScenario() == UltimateVolcanicMineConfig.PredictionScenario.BEST_CASE ? range[1] : range[0];
	}

	/** True when a vent was checked this tick (its varbit went from unknown to a value). */
	private boolean updateCheckedVents(int ventA, int ventB, int ventC)
	{
		int[] raw = {ventA, ventB, ventC};
		boolean checked = false;
		for (int i = 0; i < 3; i++)
		{
			checked |= lastRawVents[i] == VentRules.UNKNOWN && raw[i] != VentRules.UNKNOWN;
			lastRawVents[i] = raw[i];
		}
		return checked;
	}

	private void updateAwayFromBoulder(int simulationTime)
	{
		Player player = client.getLocalPlayer();
		WorldPoint boulder = boulderHealthOverlay.getBoulderLocation();
		if (player == null || boulder == null)
		{
			awayFromBoulderSince = null;
			return;
		}

		// distanceTo returns Integer.MAX_VALUE across planes, which correctly counts as away.
		boolean away = player.getWorldLocation().distanceTo(boulder) > AWAY_FROM_BOULDER_DISTANCE;
		if (!away)
		{
			awayFromBoulderSince = null;
		}
		else if (awayFromBoulderSince == null)
		{
			awayFromBoulderSince = simulationTime;
		}
	}

	private boolean shouldRunSimulationThisTick(int simulationTime)
	{
		if (estimatedTimeRemaining < 0)
		{
			return false;
		}

		if (simulationTime >= BLOW_UP_SIMULATION_END_SECONDS)
		{
			return false;
		}

		return estimatedTimeRemaining % TICKS_PER_STABILITY_UPDATE == STABILITY_SIMULATION_OFFSET_TICKS;
	}

	private boolean shouldResetPredictionsAtFiveMinuteMark()
	{
		// Uses the tick-by-tick clock: the server's time varbit only updates every few ticks, which
		// left predictions running on pre-reset stability history for up to 4 ticks after 5:00.
		return !predictionsResetAtFiveMinutes
			&& gameTicks > VMNotifier.NOTIFICATION_START_COOLDOWN_TICKS
			&& estimatedTimeRemaining <= VentRules.RESET_TIME_TICKS;
	}

	private void resetPredictionsForFiveMinuteMark(int currentStability, int chamberStatus)
	{
		predictionsResetAtFiveMinutes = true;
		mineAuditLogger.onFiveMinuteReset();
		stabilityTracker.rebase(currentStability);
		futureStabilityTracker.initialize();
		futureStabilityTracker.setDisplayCount(config.predictedStabilityChangeHistoryCount());
		clearBlowUpChance();
		lastKnownChamberStatus = chamberStatus;
		logMineStatusPrediction(
			"UVM Sim Reset | reason=five-minute reset, timeRemainingTicks={}, stability={}, chamberStatus={}",
			timeRemainingFromServer,
			currentStability,
			chamberStatus);
	}

	/**
	 * The first simulation second whose vent move and stability update haven't happened yet. The
	 * simulator applies the events at its start second, so a prediction made on the tick of a
	 * move or update (an earthquake, a vent check or a chamber flip can land on one) must start
	 * after it, or it counts that move or update - even a cancelled one - a second time.
	 */
	static int toSimulationStartSeconds(int remainingTicks)
	{
		int clampedRemainingTicks = Math.max(0, Math.min(VM_TOTAL_GAME_TICKS, remainingTicks));
		int elapsedTicks = VM_TOTAL_GAME_TICKS - clampedRemainingTicks;
		// A tick is 0.6s: seconds up to elapsedTicks * 0.6 are done.
		return elapsedTicks * 3 / 5 + 1;
	}

	private int toSimulationTimeSeconds(int remainingTicks)
	{
		int clampedRemainingTicks = Math.max(0, Math.min(VM_TOTAL_GAME_TICKS, remainingTicks));
		int elapsedTicks = VM_TOTAL_GAME_TICKS - clampedRemainingTicks;
		int elapsedSeconds = Math.round(elapsedTicks / SECONDS_TO_TICKS);
		return Math.max(0, elapsedSeconds);
	}

	private String formatStabilityTimeline(java.util.List<Simulator.StabilityPoint> points)
	{
		if (points == null || points.isEmpty())
		{
			return "none";
		}

		StringBuilder timeline = new StringBuilder();
		for (Simulator.StabilityPoint point : points)
		{
			if (timeline.length() > 0)
			{
				timeline.append(", ");
			}

			timeline.append(point.time)
				.append(':')
				.append(point.stability);
		}

		return timeline.toString();
	}

	private String formatStabilityRangeTimeline(List<Simulator.StabilityRangePoint> points)
	{
		if (points == null || points.isEmpty())
		{
			return "none";
		}

		StringBuilder timeline = new StringBuilder();
		for (Simulator.StabilityRangePoint point : points)
		{
			if (timeline.length() > 0)
			{
				timeline.append(", ");
			}

			timeline.append(point.time)
				.append(":")
				.append(formatStabilityRange(point.minStability, point.maxStability));
		}

		return timeline.toString();
	}

	private String formatStabilityRangeTimelineForCountdownChat(List<Simulator.StabilityRangePoint> points)
	{
		if (points == null || points.isEmpty())
		{
			return "none";
		}

		StringBuilder timeline = new StringBuilder();
		for (Simulator.StabilityRangePoint point : points)
		{
			if (timeline.length() > 0)
			{
				timeline.append(", ");
			}

			timeline.append(formatCountdownFromElapsedSeconds(point.time))
				.append(":")
				.append(formatStabilityRange(point.minStability, point.maxStability));
		}

		return timeline.toString();
	}

	private String formatStabilityRange(int minStability, int maxStability)
	{
		if (minStability == maxStability)
		{
			return "[" + minStability + "]";
		}

		return "[" + minStability + "," + maxStability + "]";
	}

	private String formatCountdownFromElapsedSeconds(int elapsedSeconds)
	{
		int clampedElapsed = Math.max(0, Math.min(VM_TOTAL_GAME_SECONDS, elapsedSeconds));
		int countdownSeconds = VM_TOTAL_GAME_SECONDS - clampedElapsed;
		int minutes = countdownSeconds / 60;
		int seconds = countdownSeconds % 60;
		return String.format(Locale.US, "%d:%02d", minutes, seconds);
	}

	private void printBlowUpTimelineToChat(double blowUpChancePercent, List<Simulator.StabilityRangePoint> points)
	{
		if (!config.showBlowUpChance() || !config.mineStatusPredictionConsoleOutput() || points == null || points.isEmpty())
		{
			return;
		}

		String timeline = formatStabilityRangeTimelineForCountdownChat(points);
		String prefix = "Blow-up " + formatPercentage(blowUpChancePercent) + "% | ";

		if (prefix.length() + timeline.length() <= CHAT_TIMELINE_CHUNK_SIZE)
		{
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "UVM", prefix + timeline, null);
			return;
		}

		client.addChatMessage(ChatMessageType.GAMEMESSAGE, "UVM", prefix, null);
		for (String chunk : splitTimelineForChat(timeline, CHAT_TIMELINE_CHUNK_SIZE))
		{
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "UVM", chunk, null);
		}
	}

	private List<String> splitTimelineForChat(String timeline, int maxChunkLength)
	{
		List<String> chunks = new ArrayList<>();
		if (timeline == null || timeline.isEmpty())
		{
			return chunks;
		}

		String[] segments = timeline.split(", ");
		StringBuilder current = new StringBuilder();
		for (String segment : segments)
		{
			if (current.length() == 0)
			{
				current.append(segment);
				continue;
			}

			if (current.length() + 2 + segment.length() > maxChunkLength)
			{
				chunks.add(current.toString());
				current.setLength(0);
				current.append(segment);
			}
			else
			{
				current.append(", ").append(segment);
			}
		}

		if (current.length() > 0)
		{
			chunks.add(current.toString());
		}

		return chunks;
	}

	private String formatPercentage(double value)
	{
		return String.format(Locale.US, "%.2f", value);
	}

	private void setBlowUpChance(double chance)
	{
		if (blowUpChanceInfoBox == null)
		{
			return;
		}

		blowUpChanceInfoBox.setBlowUpChancePercent(chance);
		if (config.showBlowUpChance())
		{
			showBlowUpChanceInfoBox();
		}
	}

	private void clearBlowUpChance()
	{
		predictionGeneration.incrementAndGet();
		if (blowUpChanceInfoBox == null)
		{
			return;
		}

		blowUpChanceInfoBox.clear();
		removeBlowUpChanceInfoBox();
	}

	private void showBlowUpChanceInfoBox()
	{
		if (blowUpChanceInfoBoxShown)
		{
			return;
		}

		infoBoxManager.addInfoBox(blowUpChanceInfoBox);
		blowUpChanceInfoBoxShown = true;
	}

	private void removeBlowUpChanceInfoBox()
	{
		if (!blowUpChanceInfoBoxShown)
		{
			return;
		}

		infoBoxManager.removeInfoBox(blowUpChanceInfoBox);
		blowUpChanceInfoBoxShown = false;
	}

	private void handleChamberStatusChanged(int oldStatus, int newStatus)
	{
		refreshBlowUpChance(client.getVarbitValue(VARBIT_STABILITY), newStatus);
		logMineStatusPrediction("UVM Sim | chamber status changed, oldStatus={}, newStatus={}", oldStatus, newStatus);
	}

	private void addMineStatusChatMessage(String message)
	{
		if (config.mineStatusPredictionConsoleOutput())
		{
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "UVM", message, null);
		}
	}

	private void logMineStatusPrediction(String message, Object... arguments)
	{
		if (config.mineStatusPredictionConsoleOutput())
		{
			log.info(message, arguments);
		}
	}


	@Subscribe
	public void onVarbitChanged(VarbitChanged event) {
		if(!isInVM()) return;

		if(ventStatusOverlayOverride != null
			&& (ventStatusOverlayOverride.isVentStatusVarbit(event.getVarbitId())
			|| event.getVarbitId() == VARBIT_CHAMBER_STATUS)) {
			if (config.ventStatusPrediction())
			{
				ventStatusOverlayOverride.overrideVentStatusWidgetsFromVarbits();
			}
			ventStatusOverlayOverride.updateChamberStatusWidgetColors(config.chamberStatusPredictionColors());
		}

		//Set our starting player count
		if(event.getVarbitId() == VARBIT_PLAYER_COUNT) {
			maxPlayerCount = Math.max(maxPlayerCount, client.getVarbitValue(VARBIT_PLAYER_COUNT));
		}

		if(!hasGameStarted()) return;

		if(event.getVarbitId() == VARBIT_CHAMBER_STATUS) {
			int chamberStatus = client.getVarbitValue(VARBIT_CHAMBER_STATUS);
			if (lastKnownChamberStatus != -1 && lastKnownChamberStatus != chamberStatus)
			{
				handleChamberStatusChanged(lastKnownChamberStatus, chamberStatus);
				int changed = lastKnownChamberStatus ^ chamberStatus;
				traceEvent("chamber", "from", lastKnownChamberStatus, "to", chamberStatus);
				chamberFlips += Integer.bitCount(changed);
				// Blocking a chamber uses up a rock in hand.
				rocksInHand = Math.max(0, rocksInHand - Integer.bitCount(changed & chamberStatus));
			}
			lastKnownChamberStatus = chamberStatus;
		}

		//Check if a player leaves/dies - player count can only move down in game
		if(event.getVarbitId() == VARBIT_PLAYER_COUNT) {
			// Fewer players than the game started with: someone left. Early on that leaves the
			// mine scaled to the starting team, so the coach says to leave.
			int playerCount = client.getVarbitValue(VARBIT_PLAYER_COUNT);
			if (playerCount > 0 && playerCount < maxPlayerCount && shouldLeaveAfterPlayerLeft(estimatedTimeRemaining))
			{
				coach.onMineScaled();
			}
			//Skip this check if its time to exit the mine
			if (estimatedTimeRemaining <= VM_EXIT_TIME) return;
			VM_notifier.notify(notifier, VMNotifier.NotificationEvents.VM_PLAYER_LEAVE, gameTicks);
		}

		//Keep track of points for our cap counter
		if(event.getVarbitId() == VARBIT_POINTS) {
			int points = client.getVarbitValue(VARBIT_POINTS);
			if (lastPoints >= 0 && points != lastPoints)
			{
				traceEvent("points", "delta", points - lastPoints, "total", points);
			}
			lastPoints = points;
			int playerX = client.getLocalPlayer().getWorldLocation().getX();
			int playerY = client.getLocalPlayer().getWorldLocation().getY();
			if(capCounter.updateScore(client.getVarbitValue(VARBIT_POINTS), playerX, playerY)) {
				//Only add the info box once the player caps for the first time
				if (capCounter.getTimesCapped() == 1) infoBoxManager.addInfoBox(capInfoBox);
			}
		}
	}

	@Subscribe
	public void onScriptPostFired(ScriptPostFired event) {
		if (event.getScriptId() != PROC_VOLCANIC_MINE_SET_OTHERINFO) {
			return;
		}

		//Stability Trackers
		Widget widget = client.getWidget(InterfaceID.VOLCANIC_MINE, HUD_STABILITY_COMPONENT);
		if(config.stabilityUpdateHistoryCount() > 0 && widget != null)
			widget.setText(widget.getText() + stabilityTracker.getStabilityText());

		widget = client.getWidget(InterfaceID.VOLCANIC_MINE, HUD_STABILITY_COMPONENT-1);
		if(widget != null) {
			if (config.predictedStabilityChangeHistoryCount() > 0)
				widget.setText("Stab." + futureStabilityTracker.getStabilityText());
			else
				widget.setText("Stability");
		}

		//Vent Status
		if (ventStatusOverlayOverride != null)
		{
			if(config.ventStatusPrediction())
			{
				ventStatusOverlayOverride.overrideVentStatusWidgetsFromVarbits();
			}
			ventStatusOverlayOverride.updateChamberStatusWidgetColors(config.chamberStatusPredictionColors());
		}
	}

	@Subscribe
	void onGameObjectDespawned(GameObjectDespawned event) {
		mineLayout.onDespawned(event.getGameObject());
		int gameObjectId = event.getGameObject().getId();
		if(gameObjectId == GAME_OBJ_ROCK) {
			timedObjectTracker.addObject(event.getGameObject().getWorldLocation(), TimedObject.ObjectType.ROCK);
			// A rock taken from the pile next to the player is now in their hands.
			Player player = client.getLocalPlayer();
			if (player != null && player.getWorldLocation().distanceTo(event.getGameObject().getWorldLocation()) <= ROCK_TAKE_DISTANCE)
			{
				rocksInHand++;
				traceEvent("rock_taken", "rocks", rocksInHand);
			}
		}
	}

	private static final int ROCK_TAKE_DISTANCE = 2;

	@Subscribe
	public void onGroundObjectSpawned(GroundObjectSpawned event)
	{
		mineLayout.onSpawned(event.getGroundObject());
	}

	@Subscribe
	public void onGroundObjectDespawned(GroundObjectDespawned event)
	{
		mineLayout.onDespawned(event.getGroundObject());
	}

	private void resetGameVariables() {
		mineAuditLogger.onGameEnd();
		ventInference.reset();
		gameTicks = 0;
		java.util.Arrays.fill(lastRawVents, VentRules.UNKNOWN);
		if (coachTracer != null && coachTracer.isOn())
		{
			java.util.Map<String, Object> footer = new java.util.LinkedHashMap<>();
			footer.put("miningXpGained", lastMiningXp - Math.max(0, gameStartMiningXp));
			footer.put("points", lastPoints);
			footer.put("caps", chamberFlips);
			footer.put("bouldersBroken", coach.getBouldersBroken());
			coachTracer.stop(footer);
		}
		lastMiningXp = -1;
		lastPoints = -1;
		gameStartMiningXp = -1;
		coach.reset();
		lastObservation = null;
		lastCoachText = null;
		lastCoachNext = null;
		rocksInHand = 0;
		chamberFlips = 0;
		coachOverlay.setInstruction(null);
		coachHighlightOverlay.setInstruction(null, 1);
		coachPathOverlay.setInstruction(null);
		VM_notifier.reset();
		capCounter.initialize();
		clearBlowUpChance();
		predictionsResetAtFiveMinutes = false;
		lastKnownChamberStatus = -1;
		timedObjectTracker.clearRocks();
		boulderHealthOverlay.resetTracking();
        pickaxeProtector.resetStartingPickaxes();
		idlePlayerTracker.reset();
		awayFromBoulderSince = null;
		estimatedTimeRemaining = timeRemainingFromServer = 0;
		ticksSinceLobbyStart = maxPlayerCount = 0;
		if (playerCountBox != null)
		{
			infoBoxManager.removeInfoBox(playerCountBox);
			playerCountBox = null;
		}
	}
	private boolean hasGameStarted() {
		if(vmGameState >= VM_GAME_STATE_IN_GAME) return true;
		//Both Lobby and exit time are 30 seconds
		return estimatedTimeRemaining > VM_LOBBY_TIME;
	}


	//Function(s) taken from Hipipis Plugin hub VMPlugin
	private static final String PLATFORM_WARNING_MESSAGE = "The platform beneath you will disappear soon!";
	private static final String BOULDER_WARNING_MESSAGE = "The current boulder stage is complete.";
	// Constants
	private static final int PLATFORM_STAGE_3_ID = 31000;
	private static final int PLATFORM_STAGE_1_ID = 30998;
	@Subscribe
	public void onGameObjectSpawned(GameObjectSpawned event) {
		mineLayout.onSpawned(event.getGameObject());
		if (!isInVM()) return;

		int gameObjectId = event.getGameObject().getId();
		int playerX = client.getLocalPlayer().getWorldLocation().getX();
		int playerY = client.getLocalPlayer().getWorldLocation().getY();
		int objectX = event.getGameObject().getWorldLocation().getX();
		int objectY = event.getGameObject().getWorldLocation().getY();

		//Get initial positions the player must be on to cap
		if(gameObjectId == GAME_OBJ_CHAMBER_BLOCKED || gameObjectId == GAME_OBJ_CHAMBER_UNBLOCKED) {
			objectX = event.getGameObject().getWorldLocation().getX();
			objectY = event.getGameObject().getWorldLocation().getY();
			capCounter.addCappingPositions(objectX, objectY);
		}

		if(gameObjectId == PLATFORM_STAGE_1_ID) {
			timedObjectTracker.addObject(event.getGameObject().getWorldLocation(), TimedObject.ObjectType.PLATFORM);
		}

		// If warning is enabled and game object spawned is a stage 3 platform
		if (gameObjectId == PLATFORM_STAGE_3_ID)
		{
			if(!config.showPlatformWarning()) return;
			// Notify player if the stage 3 platform is beneath them
			if (playerX == objectX && playerY == objectY)
			{
				notifier.notify(PLATFORM_WARNING_MESSAGE);
			}
		}
	}
	@Subscribe
	public void onNpcSpawned(NpcSpawned npcSpawned)
	{
		// Return if not in VM
		if (!isInVM())
		{
			return;
		}

		NPC npc = npcSpawned.getNpc();
		boulderHealthOverlay.onNpcSpawned(npc);

		if (boulderHealthOverlay.isFinalBreakStageNpcId(npc.getId()))
		{
			if (config.boulderFinishPickaxeLeaveProtection() && pickaxeProtector.isPickaxeDropped())
			{
				VM_notifier.notify(notifier, VMNotifier.NotificationEvents.VM_PICKAXE_BOULDER_COMPLETE, gameTicks);
			}
			VM_notifier.removeEvent(VMNotifier.NotificationEvents.VM_PLAYER_LEAVE);
		}

		int brokenStage = BoulderHealthOverlay.brokenStageOf(npc.getId());
		if (boulderHealthOverlay.handleBreakStageNpc(npc.getId()))
		{
			traceEvent("boulder_broken", "boulder", brokenStage);
			if (coach != null && brokenStage > 0 && lastObservation != null && hasGameStarted())
			{
				coach.onBoulderBroken(brokenStage, lastObservation);
				showCoach(coach.update(lastObservation));
			}
			if (config.showBoulderWarning())
			{
				notifier.notify(BOULDER_WARNING_MESSAGE);
			}
		}

	}

	@Subscribe
	public void onNpcChanged(NpcChanged npcChanged)
	{
		if (!isInVM())
		{
			return;
		}

		NPC npc = npcChanged.getNpc();
		boulderHealthOverlay.onNpcChanged(npc);
	}

	private boolean isSuccessfulBoulderMineMessage(String chatMsg)
	{
		String msg = chatMsg.toLowerCase();
		if (msg.contains("alongside"))
		{
			return false;
		}

		boolean chippedBoulder = msg.contains("chip off") && (msg.contains("boulder"));
		boolean minedFragment = msg.contains("mine out") && msg.contains("fragment");
		return chippedBoulder || minedFragment;
	}

	private boolean isInVM()
	{
		Player player = client.getLocalPlayer();
		if(player == null) return false;
		LocalPoint localPoint = player.getLocalLocation();
		if(localPoint == null) return false;
		WorldPoint worldPoint = WorldPoint.fromLocalInstance(client, localPoint);
		if(worldPoint == null) return false;
		int currentRegionID = worldPoint.getRegionID();
		return  currentRegionID == VM_REGION_NORTH || currentRegionID == VM_REGION_SOUTH;
	}
}
