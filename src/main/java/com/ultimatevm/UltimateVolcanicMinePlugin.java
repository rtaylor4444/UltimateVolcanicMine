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
	private GuideOverlay guideOverlay;

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


	private VentStatusPredicter ventStatusPredicter = new VentStatusPredicter();
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
	private GuideManager guideManager;
	private IdlePlayerTracker idlePlayerTracker = new IdlePlayerTracker();

	private static final class VentResolution
	{
		private final Integer resolvedValue;
		private final String source;
		private final String reason;

		private VentResolution(Integer resolvedValue, String source, String reason)
		{
			this.resolvedValue = resolvedValue;
			this.source = source;
			this.reason = reason;
		}
	}


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

		overlayManager.remove(guideOverlay);
		if(config.showGuide()) overlayManager.add(guideOverlay);

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
			ventStatusPredicter,
			VARBIT_VENT_STATUS_A,
			VARBIT_VENT_STATUS_B,
			VARBIT_VENT_STATUS_C
		);
		stabilityTracker.setDisplayCount(config.stabilityUpdateHistoryCount());
		futureStabilityTracker.setDisplayCount(config.predictedStabilityChangeHistoryCount());
		capInfoBox = new CapCounterInfoBox(capCounter, this);
		blowUpChanceInfoBox = new BlowUpChanceInfoBox(this);
		blowUpChanceInfoBoxShown = false;
		guideManager = new GuideManager(client, ventStatusPredicter, guideOverlay, config);
		timedObjectOverlay.setRockTracker(timedObjectTracker);
		overlayManager.add(timedObjectOverlay);
		if(config.showBoulderHealth()) overlayManager.add(boulderHealthOverlay);
		if(config.showGuide()) overlayManager.add(guideOverlay);
		eruptionTime = (int) (config.eruptionWarningTime() * SECONDS_TO_TICKS);
		ventWarningTime = (int) (config.ventWarningTime() * SECONDS_TO_TICKS);
	}

	@Override
	protected void shutDown() throws Exception
	{
		ventStatusOverlayOverride = null;
		overlayManager.remove(timedObjectOverlay);
		overlayManager.remove(boulderHealthOverlay);
		overlayManager.remove(guideOverlay);
		infoBoxManager.removeInfoBox(capInfoBox);
		removeBlowUpChanceInfoBox();
		boulderHealthOverlay.resetTracking();
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged)
	{
		if(!isInVM()) return;
		vmGameState = client.getVarbitValue(VARBIT_GAME_STATE);
		if (vmGameState == VM_GAME_STATE_IN_LOBBY) {
			stabilityTracker.initialize();
			ventStatusPredicter.initialize();
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
			StabilityUpdateInfo.resetPlayers();
			infoBoxManager.removeInfoBox(capInfoBox);
			clearBlowUpChance();
			resetGameVariables();
			return;
		}

		if(maxPlayerCount > config.expectedTeamSize())
			VM_notifier.notify(notifier, VMNotifier.NotificationEvents.VM_EXTRA_PLAYER, ventStatusPredicter.getCurrentTick());

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

        if(!hasGameStarted()) return;

		if (config.showGuide())
		{
			guideManager.onTick(estimatedTimeRemaining);
		}

		StabilityUpdateInfo.setNumPlayers(client.getVarbitValue(VARBIT_PLAYER_COUNT));
		timedObjectTracker.updateRockTimers();

		int ventStatusA = client.getVarbitValue(VARBIT_VENT_STATUS_A);
		int ventStatusB = client.getVarbitValue(VARBIT_VENT_STATUS_B);
		int ventStatusC = client.getVarbitValue(VARBIT_VENT_STATUS_C);
		int chamberStatus = client.getVarbitValue(VARBIT_CHAMBER_STATUS);
		int currentStability = client.getVarbitValue(VARBIT_STABILITY);
		if (shouldResetPredictionsAtFiveMinuteMark())
		{
			resetPredictionsForFiveMinuteMark(currentStability, chamberStatus);
		}
		if (lastKnownChamberStatus == -1)
		{
			lastKnownChamberStatus = chamberStatus;
		}

		boolean ventChecked = updateVentStatus(ventStatusA, ventStatusB, ventStatusC, chamberStatus);
		if (ventChecked)
		{
			refreshBlowUpChance(currentStability, chamberStatus);
		}
		//Update our predicted stability change on the same exact tick the vent status changes
		if(ventStatusPredicter.isMovementUpdateTick()) {
			//Check if we have to fix vents in the future
			int futureChange = ventStatusPredicter.getFutureStabilityChange(config.predictedVentFixScenario());
			if(futureChange != VentStatus.STARTING_VENT_VALUE) {
				futureStabilityTracker.addChange(futureChange);
				if (futureStabilityTracker.isFutureStabilityBad(config.predictedStabilityChange())) {
					int startTime = 900 - (int)(config.predictedventWarningStartTime() * SECONDS_TO_TICKS);
					int endTime = 600 + (int)(config.predictedventWarningEndTime() * SECONDS_TO_TICKS);
					if(estimatedTimeRemaining < startTime && estimatedTimeRemaining > endTime)
						VM_notifier.notify(notifier, VMNotifier.NotificationEvents.VM_PREDICTED_VENT_FIX, ventStatusPredicter.getCurrentTick());
				}
			}
		}

		int simulationTime = toSimulationTimeSeconds(estimatedTimeRemaining);
		if (simulationTime >= BLOW_UP_SIMULATION_END_SECONDS)
		{
			clearBlowUpChance();
		}

		boolean stabilityChanged = updateStability(currentStability);
		if(stabilityChanged) {
			if(config.ventStatusUpdateHistory()) {
				Widget widget = client.getWidget(ComponentID.VOLCANIC_MINE_VENT_A_PERCENTAGE+1);
				client.addChatMessage(ChatMessageType.GAMEMESSAGE, "CyanWarrior4: ", ventStatusPredicter.getVentStatusText(0, widget.getText()), null);
				widget = client.getWidget(ComponentID.VOLCANIC_MINE_VENT_B_PERCENTAGE+1);
				client.addChatMessage(ChatMessageType.GAMEMESSAGE, "CyanWarrior4: ", ventStatusPredicter.getVentStatusText(1, widget.getText()), null);
				widget = client.getWidget(ComponentID.VOLCANIC_MINE_VENT_C_PERCENTAGE+1);
				client.addChatMessage(ChatMessageType.GAMEMESSAGE, "CyanWarrior4: ", ventStatusPredicter.getVentStatusText(2, widget.getText()), null);
				client.addChatMessage(ChatMessageType.GAMEMESSAGE, "CyanWarrior4: ", "Stability Update: " + stabilityTracker.getCurrentChange(), null);
				client.addChatMessage(ChatMessageType.GAMEMESSAGE, "CyanWarrior4: ", "Time: " + estimatedTimeRemaining, null);
			}

			//Check if we have to fix vents now
			if(stabilityTracker.getCurrentChange() < 0 && estimatedTimeRemaining > 595)
				VM_notifier.notify(notifier, VMNotifier.NotificationEvents.VM_PRE_RESET_VENT_FIX, ventStatusPredicter.getCurrentTick());

			//Check if we should have all 3 vents fixed (post-reset)
			boolean isPostResetTime = (estimatedTimeRemaining <= (VentStatusTimeline.VM_GAME_RESET_TIME-15) && estimatedTimeRemaining > 305);
			if(currentStability + (stabilityTracker.getCurrentChange() * 4) <= 0 && isPostResetTime)
				VM_notifier.notify(notifier, VMNotifier.NotificationEvents.VM_POST_RESET_VENT_FIX, ventStatusPredicter.getCurrentTick());
		}

		if (shouldRunSimulationThisTick(simulationTime))
		{
			refreshBlowUpChance(currentStability, chamberStatus);
			if (config.showGuide())
			{
				guideManager.update(estimatedTimeRemaining, currentStability, chamberStatus, simulationTime,
					boulderHealthOverlay.getCurrentBoulderNumber(), boulderHealthOverlay.getCurrentHealth());
			}
		}

		int currentTick = ventStatusPredicter.getCurrentTick();
		if (estimatedTimeRemaining <= (VentStatusTimeline.VM_GAME_RESET_TIME + ventWarningTime))
			VM_notifier.notify(notifier, VMNotifier.NotificationEvents.VM_RESET, currentTick);

		if (estimatedTimeRemaining <= eruptionTime) {
			VM_notifier.notify(notifier, VMNotifier.NotificationEvents.VM_ERUPTION, currentTick);
		}

		ventStatusPredicter.updateDisplayState();
		if (ventStatusOverlayOverride != null)
		{
			ventStatusOverlayOverride.updateChamberStatusWidgetColors(config.chamberStatusPredictionColors());
		}

		ventStatusPredicter.getTimeline().updateTick();
		if(ventStatusPredicter.getCurrentTick() > VentStatusTimeline.VM_GAME_RESET_TIME) {
			ventStatusPredicter.reset();
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
			ventStatusPredicter.markEarthquakeEvent();
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
				VM_notifier.notify(notifier, VMNotifier.NotificationEvents.VM_PICKAXE_LOW_STABILITY, ventStatusPredicter.getCurrentTick());
		}

		if(stabilityTracker.updateStability(newStability)) {
			ventStatusPredicter.makeStatusState(stabilityTracker.getCurrentChange());
			return true;
		}
		return false;
	}

	private void runAndLogSimulationOnStabilityUpdate(int currentStability, int chamberStatus, int simulationTime)
	{
		int ventA = client.getVarbitValue(VARBIT_VENT_STATUS_A);
		int ventB = client.getVarbitValue(VARBIT_VENT_STATUS_B);
		int ventC = client.getVarbitValue(VARBIT_VENT_STATUS_C);
		VentStatus[] predictedVents = ventStatusPredicter.getDisplayState().getVents();

		VentResolution resolvedA = resolveVentValue(ventA, predictedVents[0]);
		VentResolution resolvedB = resolveVentValue(ventB, predictedVents[1]);
		VentResolution resolvedC = resolveVentValue(ventC, predictedVents[2]);

		log.info("UVM Sim Trigger | tick={}, timeRemainingTicks={}, time={}s, stability={}, chamberStatus={}, trigger=stability-update, A={}, B={}, C={}",
			ventStatusPredicter.getCurrentTick(),
			estimatedTimeRemaining,
			simulationTime,
			currentStability,
			chamberStatus,
			formatVentResolution("A", ventA, predictedVents[0], resolvedA),
			formatVentResolution("B", ventB, predictedVents[1], resolvedB),
			formatVentResolution("C", ventC, predictedVents[2], resolvedC));

		if (!areAllVentsKnown(resolvedA, resolvedB, resolvedC))
		{
			runAndLogUnknownVentSimulation(
				currentStability,
				simulationTime,
				chamberStatus,
				new int[]{ventA, ventB, ventC},
				predictedVents,
				new VentResolution[]{resolvedA, resolvedB, resolvedC});
			return;
		}

		Simulator.SimulationResult result = Simulator.simulate(
			resolvedA.resolvedValue,
			resolvedB.resolvedValue,
			resolvedC.resolvedValue,
			chamberStatus,
			currentStability,
			simulationTime);
		double blowUpChancePercent = result.willDie ? 100.0 : 0.0;
		setBlowUpChance(blowUpChancePercent);
		printBlowUpTimelineToChat(blowUpChancePercent, toRangePoints(result.stabilityOverTime));

		log.info("UVM Sim | time={}s, A={}({}), B={}({}), C={}({}), stability={}, chamberStatus={} -> points={}, willDie={}, timeline={}",
			simulationTime,
			resolvedA.resolvedValue,
			resolvedA.source,
			resolvedB.resolvedValue,
			resolvedB.source,
			resolvedC.resolvedValue,
			resolvedC.source,
			currentStability,
			chamberStatus,
			result.stabilityOverTime.size(),
			result.willDie,
			formatStabilityTimeline(result.stabilityOverTime));
	}

	private void refreshBlowUpChance(int currentStability, int chamberStatus)
	{
		int simulationTime = toSimulationTimeSeconds(estimatedTimeRemaining);
		if (estimatedTimeRemaining < 0 || simulationTime >= BLOW_UP_SIMULATION_END_SECONDS)
		{
			clearBlowUpChance();
			return;
		}

		runAndLogSimulationOnStabilityUpdate(currentStability, chamberStatus, simulationTime);
	}

	private void runAndLogUnknownVentSimulation(
		int currentStability,
		int simulationTime,
		int chamberStatus,
		int[] rawVents,
		VentStatus[] predictedVents,
		VentResolution[] resolutions)
	{
		List<Integer> unknownIndices = new ArrayList<>();
		List<List<Integer>> candidateValuesByUnknown = new ArrayList<>();
		int[] knownValues = new int[3];

		for (int i = 0; i < resolutions.length; i++)
		{
			if (resolutions[i].resolvedValue == null)
			{
				unknownIndices.add(i);
				candidateValuesByUnknown.add(VentValueResolver.resolvePossibleValues(rawVents[i], predictedVents[i]));
				continue;
			}
			knownValues[i] = resolutions[i].resolvedValue;
		}

		int knownCount = 3 - unknownIndices.size();
		if (knownCount < 1)
		{
			if (!stabilityTracker.isPatternPredictionEnabled())
			{
				log.info(
					"UVM Sim Skip | reason=no-vent pattern prediction invalidated by chamber status change, time={}s, stability={}",
					simulationTime,
					currentStability);
				clearBlowUpChance();
				return;
			}

			List<Integer> negativeTail = stabilityTracker.getConsecutiveNegativeTailOldestFirst();
			if (negativeTail.isEmpty())
			{
				log.info(
					"UVM Sim Skip | reason=no negative tail for no-vent lookup, time={}s, stability={}",
					simulationTime,
					currentStability);
				clearBlowUpChance();
				return;
			}

			Simulator.NoVentPatternResult fallback = Simulator.simulateNoKnownVentsFromPatterns(
				currentStability,
				simulationTime,
				negativeTail);

			if (fallback.matchedScenarioCount == 0 || fallback.stabilityRangeOverTime.isEmpty())
			{
				client.addChatMessage(
					ChatMessageType.GAMEMESSAGE,
					"UVM",
					"No-vent prediction unavailable: no data matches recent negative sequence " + negativeTail + ".",
					null);
				log.info(
					"UVM Sim Skip | reason=no pattern matches for no-vent lookup, time={}s, stability={}, tail={}",
					simulationTime,
					currentStability,
					negativeTail);
				clearBlowUpChance();
				return;
			}

			setBlowUpChance(fallback.blowUpChancePercent);
			printBlowUpTimelineToChat(fallback.blowUpChancePercent, fallback.stabilityRangeOverTime);
			printPredictedExplosionTimeToChat("No-vent data", fallback.stabilityRangeOverTime);

			log.info(
				"UVM Sim Pattern | time={}s, knownCount=0, stability={}, tail={}, matchedTailLength={}, interiorMatch={}, weightedMatches={}, blowUpChance={}%, rangeTimeline= {}",
				simulationTime,
				currentStability,
				negativeTail,
				fallback.matchedTailLength,
				fallback.usedInteriorMatch,
				fallback.matchedScenarioCount,
				formatPercentage(fallback.blowUpChancePercent),
				formatStabilityRangeTimeline(fallback.stabilityRangeOverTime));
			return;
		}

		for (int i = 0; i < unknownIndices.size(); i++)
		{
			if (candidateValuesByUnknown.get(i).isEmpty())
			{
				log.info("UVM Sim Skip | reason=unknown vent has no possible values, unknownVent={}", ventName(unknownIndices.get(i)));
				clearBlowUpChance();
				return;
			}
		}

		List<Simulator.VentUnknownInput> unknownInputs = new ArrayList<>();
		for (int i = 0; i < unknownIndices.size(); i++)
		{
			unknownInputs.add(new Simulator.VentUnknownInput(
				unknownIndices.get(i),
				candidateValuesByUnknown.get(i)));
		}

		Simulator.MultiVentSweepResult sweep = Simulator.simulateUnknownVents(
			knownValues,
			unknownInputs,
			chamberStatus,
			currentStability,
			simulationTime);
		setBlowUpChance(sweep.blowUpChancePercent);
		printBlowUpTimelineToChat(sweep.blowUpChancePercent, sweep.stabilityRangeOverTime);

		log.info(
			"UVM Sim Sweep Summary | time={}s, knownCount={}, unknowns={}, combinations={}, blowUpChance={}%, rangeTimeline={}",
			simulationTime,
			knownCount,
			formatUnknownVentCandidates(unknownIndices, candidateValuesByUnknown),
			sweep.scenarios.size(),
			formatPercentage(sweep.blowUpChancePercent),
			formatStabilityRangeTimeline(sweep.stabilityRangeOverTime));
	}

	private boolean areAllVentsKnown(VentResolution ventA, VentResolution ventB, VentResolution ventC)
	{
		return ventA.resolvedValue != null
			&& ventB.resolvedValue != null
			&& ventC.resolvedValue != null;
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
		return !predictionsResetAtFiveMinutes
			&& ventStatusPredicter.getCurrentTick() > VMNotifier.NOTIFICATION_START_COOLDOWN_TICKS
			&& timeRemainingFromServer <= VentStatusTimeline.VM_GAME_RESET_TIME;
	}

	private void resetPredictionsForFiveMinuteMark(int currentStability, int chamberStatus)
	{
		predictionsResetAtFiveMinutes = true;
		stabilityTracker.rebase(currentStability);
		futureStabilityTracker.initialize();
		futureStabilityTracker.setDisplayCount(config.predictedStabilityChangeHistoryCount());
		ventStatusPredicter.reset();
		clearBlowUpChance();
		lastKnownChamberStatus = chamberStatus;
		log.info(
			"UVM Sim Reset | reason=five-minute reset, timeRemainingTicks={}, stability={}, chamberStatus={}",
			timeRemainingFromServer,
			currentStability,
			chamberStatus);
	}

	private VentResolution resolveVentValue(int rawVarbitValue, VentStatus predictedVent)
	{
		if (rawVarbitValue != VentStatus.STARTING_VENT_VALUE)
		{
			return new VentResolution(rawVarbitValue, "varbit", "identified by server");
		}

		if (predictedVent == null)
		{
			return new VentResolution(null, "prediction", "missing prediction object");
		}

		if (predictedVent.isIdentified())
		{
			return new VentResolution(predictedVent.getActualValue(), "prediction", "identified in display state");
		}

		if (!predictedVent.isRangeDefined())
		{
			return new VentResolution(null, "prediction", "range undefined");
		}

		if (!predictedVent.isTwoSeperateValues())
		{
			if (predictedVent.isLowerBoundSingleValue())
			{
				return new VentResolution(predictedVent.getLowerBoundStart(), "prediction", "single-value range");
			}

			return new VentResolution(null, "prediction", "range has multiple values");
		}

		if (predictedVent.isLowerBoundSingleValue() && predictedVent.isUpperBoundSingleValue())
		{
			int lower = predictedVent.getLowerBoundStart();
			int upper = predictedVent.getUpperBoundStart();
			if (lower == upper)
			{
				return new VentResolution(lower, "prediction", "merged single-value ranges");
			}
		}

		return new VentResolution(null, "prediction", "range ambiguous");
	}

	private String formatVentResolution(String ventName, int rawVarbitValue, VentStatus predictedVent, VentResolution resolution)
	{
		return ventName
			+ "{raw=" + rawVarbitValue
			+ ",pred=" + formatVentPrediction(predictedVent)
			+ ",resolved=" + (resolution.resolvedValue == null ? "unknown" : resolution.resolvedValue)
			+ ",source=" + resolution.source
			+ ",reason=" + resolution.reason
			+ "}";
	}

	private String formatVentPrediction(VentStatus vent)
	{
		if (vent == null)
		{
			return "null";
		}

		if (vent.isIdentified())
		{
			return "id=" + vent.getActualValue();
		}

		if (!vent.isRangeDefined())
		{
			return "range=undefined";
		}

		String lower = "[" + vent.getLowerBoundStart() + "-" + vent.getLowerBoundEnd() + "]";
		String upper = "[" + vent.getUpperBoundStart() + "-" + vent.getUpperBoundEnd() + "]";
		return vent.isTwoSeperateValues() ? (lower + "|" + upper) : lower;
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
		if (!config.showBlowUpChance() || points == null || points.isEmpty())
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

	private void printPredictedExplosionTimeToChat(String sourceLabel, List<Simulator.StabilityRangePoint> points)
	{
		if (!config.showBlowUpChance() || points == null || points.isEmpty())
		{
			return;
		}

		Integer explosionTime = null;
		for (Simulator.StabilityRangePoint point : points)
		{
			if (point.maxStability <= 0)
			{
				explosionTime = point.time;
				break;
			}
		}

		if (explosionTime != null)
		{
			client.addChatMessage(
				ChatMessageType.GAMEMESSAGE,
				"UVM",
				sourceLabel + " predicts blow-up at " + formatCountdownFromElapsedSeconds(explosionTime),
				null);
			return;
		}

		int lastTime = points.get(points.size() - 1).time;
		client.addChatMessage(
			ChatMessageType.GAMEMESSAGE,
			"UVM",
			sourceLabel + " predicts no blow-up by " + formatCountdownFromElapsedSeconds(lastTime),
			null);
	}

	private List<Simulator.StabilityRangePoint> toRangePoints(List<Simulator.StabilityPoint> points)
	{
		List<Simulator.StabilityRangePoint> ranges = new ArrayList<>();
		if (points == null || points.isEmpty())
		{
			return ranges;
		}

		for (Simulator.StabilityPoint point : points)
		{
			ranges.add(new Simulator.StabilityRangePoint(point.time, point.stability, point.stability));
		}

		return ranges;
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

	private char ventName(int index)
	{
		switch (index)
		{
			case 0:
				return 'A';
			case 1:
				return 'B';
			case 2:
				return 'C';
			default:
				return '?';
		}
	}

	private String formatPercentage(double value)
	{
		return String.format(Locale.US, "%.2f", value);
	}

	private String formatUnknownVentCandidates(List<Integer> unknownIndices, List<List<Integer>> candidateValuesByUnknown)
	{
		if (unknownIndices == null || candidateValuesByUnknown == null || unknownIndices.isEmpty())
		{
			return "none";
		}

		StringBuilder builder = new StringBuilder();
		for (int i = 0; i < unknownIndices.size() && i < candidateValuesByUnknown.size(); i++)
		{
			if (builder.length() > 0)
			{
				builder.append(", ");
			}

			builder.append(ventName(unknownIndices.get(i)))
				.append('=')
				.append(candidateValuesByUnknown.get(i));
		}

		return builder.toString();
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

	public boolean updateVentStatus(int ventA, int ventB, int ventC, int chamberStatus) {
		VentStatus[] vents = ventStatusPredicter.getDisplayState().getVents();
		boolean ventChecked = (vents[0].getActualValue() == VentStatus.STARTING_VENT_VALUE && ventA != VentStatus.STARTING_VENT_VALUE)
			|| (vents[1].getActualValue() == VentStatus.STARTING_VENT_VALUE && ventB != VentStatus.STARTING_VENT_VALUE)
			|| (vents[2].getActualValue() == VentStatus.STARTING_VENT_VALUE && ventC != VentStatus.STARTING_VENT_VALUE);
		ventStatusPredicter.updateVentStatus(new int[]{ventA, ventB, ventC}, chamberStatus);
		return ventChecked;
	}

	private void handleChamberStatusChanged(int oldStatus, int newStatus)
	{
		stabilityTracker.invalidatePatternHistory();
		refreshBlowUpChance(client.getVarbitValue(VARBIT_STABILITY), newStatus);
		client.addChatMessage(
			ChatMessageType.GAMEMESSAGE,
			"UVM",
			"No-vent pattern prediction paused after chamber status changed; it will resume after a new negative stability change.",
			null);
		log.info(
			"UVM Sim Invalidate | reason=chamber status changed, oldStatus={}, newStatus={}, patternPredictionEnabled=false",
			oldStatus,
			newStatus);
	}
	public final VentStatusPredicter getVentStatusPredicter() { return ventStatusPredicter; }

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
				if (config.showGuide())
				{
					guideManager.onChamberStatusChanged(lastKnownChamberStatus, chamberStatus);
				}
			}
			lastKnownChamberStatus = chamberStatus;
		}

		//Check if a player leaves/dies - player count can only move down in game
		if(event.getVarbitId() == VARBIT_PLAYER_COUNT) {
			//Skip this check if its time to exit the mine
			if (estimatedTimeRemaining <= VM_EXIT_TIME) return;
			VM_notifier.notify(notifier, VMNotifier.NotificationEvents.VM_PLAYER_LEAVE, ventStatusPredicter.getCurrentTick());
		}

		//Keep track of points for our cap counter
		if(event.getVarbitId() == VARBIT_POINTS) {
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
		int gameObjectId = event.getGameObject().getId();
		if(gameObjectId == GAME_OBJ_ROCK) {
			timedObjectTracker.addObject(event.getGameObject().getWorldLocation(), TimedObject.ObjectType.ROCK);
		}
	}

	private void resetGameVariables() {
		guideManager.reset();
		VM_notifier.reset();
		capCounter.initialize();
		clearBlowUpChance();
		predictionsResetAtFiveMinutes = false;
		lastKnownChamberStatus = -1;
		timedObjectTracker.clearRocks();
		boulderHealthOverlay.resetTracking();
        pickaxeProtector.resetStartingPickaxes();
		idlePlayerTracker.reset();
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
				VM_notifier.notify(notifier, VMNotifier.NotificationEvents.VM_PICKAXE_BOULDER_COMPLETE, ventStatusPredicter.getCurrentTick());
			}
			VM_notifier.removeEvent(VMNotifier.NotificationEvents.VM_PLAYER_LEAVE);
		}

		if (boulderHealthOverlay.handleBreakStageNpc(npc.getId()) && config.showBoulderWarning())
		{
			notifier.notify(BOULDER_WARNING_MESSAGE);
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
