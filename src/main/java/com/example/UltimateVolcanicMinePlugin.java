package com.example;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.widgets.WidgetID;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.Notifier;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

import net.runelite.api.coords.WorldPoint;
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

	private VentStatusPredicter ventStatusPredicter = new VentStatusPredicter();
	private StabilityTracker stabilityTracker = new StabilityTracker();
	private int vmGameState = VM_GAME_STATE_NONE;
	private int ventStatus[] = new int[3];
	private int varbitsUpdated = 0;
	private int chamberStatus;
	private int timeRemainingFromServer, estimatedTimeRemaining;


	@Provides
	UltimateVolcanicMineConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(UltimateVolcanicMineConfig.class);
	}

	@Override
	protected void startUp() throws Exception
	{
		log.info("Example started!");
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
			estimatedTimeRemaining = VM_GAME_FULL_TIME;
			varbitsUpdated = timeRemainingFromServer = 0;
		}
	}

	@Subscribe
	public void onGameTick(GameTick tick) {
		if(!isInVM()) {
			vmGameState = VM_GAME_STATE_NONE;
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

//		Widget widget = client.getWidget(WidgetInfo.VOLCANIC_MINE_TIME_LEFT);
//		widget.setText(Integer.toString(varbitsUpdated));

		ventStatusPredicter.updateVentStatus(ventStatus, chamberStatus);
		if(ventStatusPredicter.updateVentMovement(varbitsUpdated))
			varbitsUpdated = 0;

		if(stabilityTracker.updateStability(stability)) {
			ventStatusPredicter.makeStatusState(stabilityTracker.getCurrentChange());
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
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event) {
		//Exit if the game has not started yet
		if(vmGameState < VM_GAME_STATE_IN_GAME) return;

		if(event.getVarbitId() == VARBIT_VENT_STATUS_A) varbitsUpdated |= 1;
		if(event.getVarbitId() == VARBIT_VENT_STATUS_B) varbitsUpdated |= 2;
		if(event.getVarbitId() == VARBIT_VENT_STATUS_C) varbitsUpdated |= 4;
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

	//isInVM function taken from Hipipis Plugin hub VMPlugin
	private boolean isInVM()
	{
		return WorldPoint.fromLocalInstance(client, client.getLocalPlayer().getLocalLocation()).getRegionID() == VM_REGION_NORTH ||
				WorldPoint.fromLocalInstance(client, client.getLocalPlayer().getLocalLocation()).getRegionID() == VM_REGION_SOUTH;
	}
}
