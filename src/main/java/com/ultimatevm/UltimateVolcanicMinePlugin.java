package com.ultimatevm;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.events.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.WidgetID;
import net.runelite.client.Notifier;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.infobox.InfoBoxManager;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.api.widgets.Widget;

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
	private UltimateVolcanicMineConfig config;

	@Inject
	private InfoBoxManager infoBoxManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private CappingRockOverlay cappingRockOverlay;

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
	private static final int HUD_STABILITY_COMPONENT = 11;
	private static final int HUD_VENT_A_PERCENTAGE = 17;
	private static final int HUD_VENT_B_PERCENTAGE = 18;
	private static final int HUD_VENT_C_PERCENTAGE = 19;
	private static final int VM_GAME_STATE_NONE = 0;
	private static final int VM_GAME_STATE_IN_LOBBY = 1;
	private static final int VM_GAME_STATE_IN_GAME = 2;
	private static final int VM_REGION_NORTH = 15263;
	private static final int VM_REGION_SOUTH = 15262;
	private static final int GAME_OBJ_CHAMBER_BLOCKED = 31044;
	private static final int GAME_OBJ_CHAMBER_UNBLOCKED = 31043;
	private static final int GAME_OBJ_TAKEN_ROCK = 31046;
	private static final int GAME_OBJ_ROCK = 31045;

	private static final int VM_GAME_FULL_TIME = 1000;
	private static final int VM_GAME_RESET_TIME = 500;
	private static final float SECONDS_TO_TICKS = 1.666f;
	private static final int VENT_MOVE_TICK_TIME = 10;

	private VentStatusPredicter ventStatusPredicter = new VentStatusPredicter();
	private StabilityTracker stabilityTracker = new StabilityTracker();
	private StabilityTracker futureStabilityTracker = new StabilityTracker();
	private VMNotifier VM_notifier;
	private CapCounter capCounter = new CapCounter();
	private CappingRockTracker rockTracker = new CappingRockTracker();
	private CapCounterInfoBox capInfoBox;
	private int vmGameState = VM_GAME_STATE_NONE;
	private int ventStatus[] = new int[3];
	private int varbitsUpdated = 0;
	private int timeRemainingFromServer, estimatedTimeRemaining;
	private int ticksPassed, movementUpdateTick;
	private int eruptionTime, ventWarningTime;


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

		overlayManager.remove(cappingRockOverlay);
		if(config.rockTimer()) overlayManager.add(cappingRockOverlay);
	}

	@Override
	protected void startUp() throws Exception {
		VM_notifier = new VMNotifier(config);
		capInfoBox = new CapCounterInfoBox(capCounter, this);
		cappingRockOverlay.setRockTracker(rockTracker);
		overlayManager.add(cappingRockOverlay);
		eruptionTime = (int) (config.eruptionWarningTime() * SECONDS_TO_TICKS);
		ventWarningTime = (int) (config.ventWarningTime() * SECONDS_TO_TICKS);
	}

	@Override
	protected void shutDown() throws Exception
	{
		overlayManager.remove(cappingRockOverlay);
		infoBoxManager.removeInfoBox(capInfoBox);
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
			resetGameVariables();
		}
	}

	@Subscribe
	public void onGameTick(GameTick tick) {
		if(!isInVM()) {
			vmGameState = VM_GAME_STATE_NONE;
			infoBoxManager.removeInfoBox(capInfoBox);
			resetGameVariables();
			return;
		}

		//Exit if the game has not started yet
		if(vmGameState < VM_GAME_STATE_IN_GAME) return;

		int newTimeRemaining = client.getVarbitValue(VARBIT_TIME_REMAINING);
		if(newTimeRemaining != timeRemainingFromServer) {
			estimatedTimeRemaining = timeRemainingFromServer = newTimeRemaining;
		} else --estimatedTimeRemaining;

		rockTracker.updateRockTimers();

		updateVentStatus(client.getVarbitValue(VARBIT_VENT_STATUS_A),
				client.getVarbitValue(VARBIT_VENT_STATUS_B),
				client.getVarbitValue(VARBIT_VENT_STATUS_C),
				client.getVarbitValue(VARBIT_CHAMBER_STATUS));
		//Update our movement on the same exact tick the vent status changes
		if(ticksPassed % VENT_MOVE_TICK_TIME == movementUpdateTick) {
			updateVentMovement();

			//Check if we have to fix vents in the future
			int futureChange = ventStatusPredicter.getFutureStabilityChange(config.predictedVentFixScenario());
			if(futureChange != VentStatus.STARTING_VENT_VALUE) {
				futureStabilityTracker.addChange(futureChange);
				if (futureStabilityTracker.isFutureStabilityBad(config.predictedStabilityChange()) && estimatedTimeRemaining > 595)
					VM_notifier.notify(notifier, VMNotifier.NotificationEvents.VM_PREDICTED_VENT_FIX, ticksPassed);
			}
		}

		if(updateStability(client.getVarbitValue(VARBIT_STABILITY))) {
			if(config.ventStatusUpdateHistory()) {
				Widget widget = client.getWidget(WidgetID.VOLCANIC_MINE_GROUP_ID, HUD_VENT_A_PERCENTAGE);
				client.addChatMessage(ChatMessageType.GAMEMESSAGE, "CyanWarrior4: ", ventStatusPredicter.getVentStatusText(0, widget.getText()), null);
				widget = client.getWidget(WidgetID.VOLCANIC_MINE_GROUP_ID, HUD_VENT_B_PERCENTAGE);
				client.addChatMessage(ChatMessageType.GAMEMESSAGE, "CyanWarrior4: ", ventStatusPredicter.getVentStatusText(1, widget.getText()), null);
				widget = client.getWidget(WidgetID.VOLCANIC_MINE_GROUP_ID, HUD_VENT_C_PERCENTAGE);
				client.addChatMessage(ChatMessageType.GAMEMESSAGE, "CyanWarrior4: ", ventStatusPredicter.getVentStatusText(2, widget.getText()), null);
				client.addChatMessage(ChatMessageType.GAMEMESSAGE, "CyanWarrior4: ", "Stability Update: " + stabilityTracker.getCurrentChange(), null);
			}

			//Check if we have to fix vents now
			if(stabilityTracker.getCurrentChange() < 0 && estimatedTimeRemaining > 595)
				VM_notifier.notify(notifier, VMNotifier.NotificationEvents.VM_PRE_RESET_VENT_FIX, ticksPassed);
		}

		//Ensure reset will not happen at start before the server sends the new game time
		//Reset around 5:00 when the server sends new unidentified vent
		if(ticksPassed > VMNotifier.NOTIFICATION_START_COOLDOWN_TICKS &&
				estimatedTimeRemaining <= VM_GAME_RESET_TIME) {
			stabilityTracker.resetStabilityHistory();
			futureStabilityTracker.resetStabilityHistory();
			ventStatusPredicter.reset();
		}

		if (estimatedTimeRemaining <= (VM_GAME_RESET_TIME + ventWarningTime))
			VM_notifier.notify(notifier, VMNotifier.NotificationEvents.VM_RESET, ticksPassed);

		if (estimatedTimeRemaining <= eruptionTime)
			VM_notifier.notify(notifier, VMNotifier.NotificationEvents.VM_ERUPTION, ticksPassed);

		++ticksPassed;
	}

	//Helper functions for testing
	public boolean updateStability(int newStability) {
		if(stabilityTracker.updateStability(newStability)) {
			ventStatusPredicter.makeStatusState(client, stabilityTracker.getCurrentChange());
			return true;
		}
		return false;
	}
	public void updateVentStatus(int ventA, int ventB, int ventC, int chamberStatus) {
		ventStatusPredicter.updateVentStatus(new int[]{ventA, ventB, ventC}, chamberStatus);
	}
	public void updateVentMovement() {
		ventStatusPredicter.updateVentMovement();
		varbitsUpdated = 0;
	}
	public final VentStatusPredicter getVentStatusPredicter() { return ventStatusPredicter; }

	@Subscribe
	public void onVarbitChanged(VarbitChanged event) {
		//Exit if the game has not started yet
		if(!isInVM() || vmGameState < VM_GAME_STATE_IN_GAME) return;

		//Keep track of points for our cap counter
		if(event.getVarbitId() == VARBIT_POINTS) {
			int playerX = client.getLocalPlayer().getWorldLocation().getX();
			int playerY = client.getLocalPlayer().getWorldLocation().getY();
			if(capCounter.updateScore(client.getVarbitValue(VARBIT_POINTS), playerX, playerY)) {
				//Only add the info box once the player caps for the first time
				if (capCounter.getTimesCapped() == 1) infoBoxManager.addInfoBox(capInfoBox);
			}
		}

		//Do not get the movement update tick until at least 1 vent
		//is identified
		if(!ventStatusPredicter.areAnyVentIdentified()) return;

		if(event.getVarbitId() == VARBIT_VENT_STATUS_A) varbitsUpdated |= 1;
		if(event.getVarbitId() == VARBIT_VENT_STATUS_B) varbitsUpdated |= 2;
		if(event.getVarbitId() == VARBIT_VENT_STATUS_C) varbitsUpdated |= 4;

		if(varbitsUpdated != 0 && movementUpdateTick == -1)
			movementUpdateTick = ticksPassed % VENT_MOVE_TICK_TIME;
	}

	@Subscribe
	public void onScriptPostFired(ScriptPostFired event) {
		if (event.getScriptId() != PROC_VOLCANIC_MINE_SET_OTHERINFO) {
			return;
		}

		//Stability Trackers
		Widget widget = client.getWidget(WidgetID.VOLCANIC_MINE_GROUP_ID, HUD_STABILITY_COMPONENT);
		if(config.stabilityUpdateHistoryCount() > 0 && widget != null)
			widget.setText(widget.getText() + stabilityTracker.getStabilityText());

		widget = client.getWidget(WidgetID.VOLCANIC_MINE_GROUP_ID, HUD_STABILITY_COMPONENT-1);
		if(widget != null) {
			if (config.predictedStabilityChangeHistoryCount() > 0)
				widget.setText("Stab." + futureStabilityTracker.getStabilityText());
			else
				widget.setText("Stability");
		}


		//Vent Status
		if(config.ventStatusPrediction()) {
			widget = client.getWidget(WidgetID.VOLCANIC_MINE_GROUP_ID, HUD_VENT_A_PERCENTAGE);
			if (widget != null) widget.setText(ventStatusPredicter.getVentStatusText(0, widget.getText()));
			widget = client.getWidget(WidgetID.VOLCANIC_MINE_GROUP_ID, HUD_VENT_B_PERCENTAGE);
			if (widget != null) widget.setText(ventStatusPredicter.getVentStatusText(1, widget.getText()));
			widget = client.getWidget(WidgetID.VOLCANIC_MINE_GROUP_ID, HUD_VENT_C_PERCENTAGE);
			if (widget != null) widget.setText(ventStatusPredicter.getVentStatusText(2, widget.getText()));
		}
	}

	@Subscribe
	void onGameObjectDespawned(GameObjectDespawned event) {
		int gameObjectId = event.getGameObject().getId();
		if(gameObjectId == GAME_OBJ_ROCK) {
			rockTracker.addRock(event.getGameObject().getWorldLocation());
		}

	}

	private void resetGameVariables() {
		estimatedTimeRemaining = VM_GAME_FULL_TIME;
		VM_notifier.reset();
		capCounter.initialize();
		varbitsUpdated = timeRemainingFromServer = 0;
		ticksPassed = 0;
		movementUpdateTick = -1;
	}


	//Function(s) taken from Hipipis Plugin hub VMPlugin
	private static final String PLATFORM_WARNING_MESSAGE = "The platform beneath you will disappear soon!";
	private static final String BOULDER_WARNING_MESSAGE = "The current boulder stage is complete.";
	// Constants
	private static final int PLATFORM_STAGE_3_ID = 31000;
	private static final int BOULDER_BREAK_STAGE_1_ID = 7807;
	private static final int BOULDER_BREAK_STAGE_2_ID = 7809;
	private static final int BOULDER_BREAK_STAGE_3_ID = 7811;
	private static final int BOULDER_BREAK_STAGE_4_ID = 7813;
	private static final int BOULDER_BREAK_STAGE_5_ID = 7815;
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

		// If warning is enabled and npc spawned is a boulder that is breaking
		if (config.showBoulderWarning())
		{
			NPC npc = npcSpawned.getNpc();

			switch(npc.getId())
			{
				case BOULDER_BREAK_STAGE_1_ID:
				case BOULDER_BREAK_STAGE_2_ID:
				case BOULDER_BREAK_STAGE_3_ID:
				case BOULDER_BREAK_STAGE_4_ID:
				case BOULDER_BREAK_STAGE_5_ID:
					notifier.notify(BOULDER_WARNING_MESSAGE);
					break;
				default:
					break;
			}
		}
	}
	private boolean isInVM()
	{
		return WorldPoint.fromLocalInstance(client, client.getLocalPlayer().getLocalLocation()).getRegionID() == VM_REGION_NORTH ||
				WorldPoint.fromLocalInstance(client, client.getLocalPlayer().getLocalLocation()).getRegionID() == VM_REGION_SOUTH;
	}
}
