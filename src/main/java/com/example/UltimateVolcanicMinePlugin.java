package com.example;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.events.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.WidgetID;
import net.runelite.client.Notifier;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

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



	//Constants
	private static final int PROC_VOLCANIC_MINE_SET_OTHERINFO = 2022;
	private static final int VARBIT_STABILITY = 5938;
	private static final int VARBIT_GAME_STATE = 5941;
	private static final int VARBIT_TIME_REMAINING = 5944;
	private static final int VARBIT_VENT_STATUS_A = 5939;
	private static final int VARBIT_VENT_STATUS_B = 5940;
	private static final int VARBIT_VENT_STATUS_C = 5942;
	private static final int VARBIT_CHAMBER_STATUS = 5936;
	private static final int HUD_STABILITY_COMPONENT = 11;
	private static final int HUD_VENT_A_PERCENTAGE = 17;
	private static final int HUD_VENT_B_PERCENTAGE = 18;
	private static final int HUD_VENT_C_PERCENTAGE = 19;
	private static final int VM_GAME_STATE_NONE = 0;
	private static final int VM_GAME_STATE_IN_LOBBY = 1;
	private static final int VM_GAME_STATE_IN_GAME = 2;
	private static final int VM_REGION_NORTH = 15263;
	private static final int VM_REGION_SOUTH = 15262;

	private static final int VM_GAME_FULL_TIME = 1000;
	private static final int VM_GAME_RESET_TIME = 500;
	private static final float SECONDS_TO_TICKS = 1.666f;
	private static final int VENT_MOVE_TICK_TIME = 10;

	private VentStatusPredicter ventStatusPredicter = new VentStatusPredicter();
	private StabilityTracker stabilityTracker = new StabilityTracker();
	private VMNotifier VM_notifier = new VMNotifier();
	private int vmGameState = VM_GAME_STATE_NONE;
	private int ventStatus[] = new int[3];
	private int varbitsUpdated = 0;
	private int chamberStatus;
	private int timeRemainingFromServer, estimatedTimeRemaining;
	private int ticksPassed, movementUpdateTick;



	@Provides
	UltimateVolcanicMineConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(UltimateVolcanicMineConfig.class);
	}

	@Override
	protected void startUp() throws Exception {

	}

	@Override
	protected void shutDown() throws Exception
	{
		log.info("Example stopped!");
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged)
	{
		if(!isInVM()) return;
		vmGameState = client.getVarbitValue(VARBIT_GAME_STATE);
		if (vmGameState == VM_GAME_STATE_IN_LOBBY) {
			stabilityTracker.initialize();
			ventStatusPredicter.initialize();
			resetGameVariables();
		}
	}

	@Subscribe
	public void onGameTick(GameTick tick) {
		if(!isInVM()) {
			vmGameState = VM_GAME_STATE_NONE;
			resetGameVariables();
			return;
		}

		//Exit if the game has not started yet
		if(vmGameState < VM_GAME_STATE_IN_GAME) return;

		int newTimeRemaining = client.getVarbitValue(VARBIT_TIME_REMAINING);
		if(newTimeRemaining != timeRemainingFromServer) {
			estimatedTimeRemaining = timeRemainingFromServer = newTimeRemaining;
		} else --estimatedTimeRemaining;

		ventStatus[0] = client.getVarbitValue(VARBIT_VENT_STATUS_A);
		ventStatus[1] = client.getVarbitValue(VARBIT_VENT_STATUS_B);
		ventStatus[2] = client.getVarbitValue(VARBIT_VENT_STATUS_C);
		chamberStatus = client.getVarbitValue(VARBIT_CHAMBER_STATUS);
		int stability = client.getVarbitValue(VARBIT_STABILITY);


		ventStatusPredicter.updateVentStatus(ventStatus, chamberStatus);
		if(ticksPassed % VENT_MOVE_TICK_TIME == movementUpdateTick) {
			ventStatusPredicter.updateVentMovement();
			varbitsUpdated = 0;
		}

		if(stabilityTracker.updateStability(stability)) {
			ventStatusPredicter.makeStatusState(client, stabilityTracker.getCurrentChange());
			Widget widget = client.getWidget(WidgetID.VOLCANIC_MINE_GROUP_ID, HUD_VENT_A_PERCENTAGE);
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "CyanWarrior4: ", ventStatusPredicter.getVentStatusText(0, widget.getText()), null);
			widget = client.getWidget(WidgetID.VOLCANIC_MINE_GROUP_ID, HUD_VENT_B_PERCENTAGE);
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "CyanWarrior4: ", ventStatusPredicter.getVentStatusText(1, widget.getText()), null);
			widget = client.getWidget(WidgetID.VOLCANIC_MINE_GROUP_ID, HUD_VENT_C_PERCENTAGE);
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "CyanWarrior4: ", ventStatusPredicter.getVentStatusText(2, widget.getText()), null);
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "CyanWarrior4: ", "Stability Update: " + stabilityTracker.getCurrentChange(), null);
		}

		if(estimatedTimeRemaining <= VM_GAME_RESET_TIME) {
			stabilityTracker.resetStabilityHistory();
			ventStatusPredicter.reset();
		}

		if (estimatedTimeRemaining <= (VM_GAME_RESET_TIME + 50))
			VM_notifier.notify(notifier, VMNotifier.NotificationEvents.VM_RESET, ticksPassed);

		if (estimatedTimeRemaining <= 70)
			VM_notifier.notify(notifier, VMNotifier.NotificationEvents.VM_ERUPTION, ticksPassed);

		++ticksPassed;
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event) {
		//Exit if the game has not started yet
		if(!isInVM() || vmGameState < VM_GAME_STATE_IN_GAME) return;
		//Do not try to get the movement update tick until at least 1 vent
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

		Widget widget = client.getWidget(WidgetID.VOLCANIC_MINE_GROUP_ID, HUD_STABILITY_COMPONENT);
		if (widget != null) {
			widget.setText(widget.getText() + stabilityTracker.getStabilityText());
		}

		//Vent Status
		widget = client.getWidget(WidgetID.VOLCANIC_MINE_GROUP_ID, HUD_VENT_A_PERCENTAGE);
		widget.setText(ventStatusPredicter.getVentStatusText(0, widget.getText()));
		widget = client.getWidget(WidgetID.VOLCANIC_MINE_GROUP_ID, HUD_VENT_B_PERCENTAGE);
		widget.setText(ventStatusPredicter.getVentStatusText(1, widget.getText()));
		widget = client.getWidget(WidgetID.VOLCANIC_MINE_GROUP_ID, HUD_VENT_C_PERCENTAGE);
		widget.setText(ventStatusPredicter.getVentStatusText(2, widget.getText()));
	}

	private void resetGameVariables() {
		estimatedTimeRemaining = VM_GAME_FULL_TIME;
		VM_notifier.reset();
		varbitsUpdated = timeRemainingFromServer = 0;
		ticksPassed = 0;
		movementUpdateTick = -1;
	}

	//Function(s) taken from Hipipis Plugin hub VMPlugin
	private static final String PLATFORM_WARNING_MESSAGE = "The platform beneath you will disappear soon!";
	// Constants
	private static final int PLATFORM_STAGE_3_ID = 31000;
	@Subscribe
	public void onGameObjectSpawned(GameObjectSpawned event)
	{
		// Skip calculation if not in VM
		if (!isInVM()) return;

		// If warning is enabled and game object spawned is a stage 3 platform
		if (event.getGameObject().getId() == PLATFORM_STAGE_3_ID)
		{
			// Fetch coordinates of player and game object
			int playerX = client.getLocalPlayer().getWorldLocation().getX();
			int playerY = client.getLocalPlayer().getWorldLocation().getY();
			int objectX = event.getGameObject().getWorldLocation().getX();
			int objectY = event.getGameObject().getWorldLocation().getY();

			// Notify player if the stage 3 platform is beneath them
			if (playerX == objectX && playerY == objectY)
			{
				notifier.notify(PLATFORM_WARNING_MESSAGE);
			}
		}
	}
	private boolean isInVM()
	{
		return WorldPoint.fromLocalInstance(client, client.getLocalPlayer().getLocalLocation()).getRegionID() == VM_REGION_NORTH ||
				WorldPoint.fromLocalInstance(client, client.getLocalPlayer().getLocalLocation()).getRegionID() == VM_REGION_SOUTH;
	}
}
