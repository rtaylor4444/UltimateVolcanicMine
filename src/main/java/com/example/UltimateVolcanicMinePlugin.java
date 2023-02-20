package com.example;

import com.google.inject.Provides;
import javax.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;

import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ScriptPostFired;
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

	private static final int HUD_COMPONENT = 611;
	private static final int HUD_STABILITY_COMPONENT = 11;

	private static final int VM_GAME_STATE_NONE = 0;
	private static final int VM_GAME_STATE_IN_LOBBY = 1;
	private static final int VM_GAME_STATE_IN_GAME = 2;
	private static final int VM_REGION_NORTH = 15263;
	private static final int VM_REGION_SOUTH = 15262;
	private static final Duration VM_RESET_TIME = Duration.ofMinutes(5);

	private StabilityTracker stabilityTracker = new StabilityTracker();
	private int vmGameState = VM_GAME_STATE_NONE;
	private Instant VMStartTime;

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
		}
		else if(vmGameState == VM_GAME_STATE_IN_GAME) {
			VMStartTime = Instant.now();
		}
	}

	@Subscribe
	public void onGameTick(GameTick tick) {
		if(!isInVM()) {
			vmGameState = VM_GAME_STATE_NONE;
			return;
		}

		int stability = client.getVarbitValue(VARBIT_STABILITY);
		stabilityTracker.updateStability(stability);

		Duration timeSinceStart = Duration.between(VMStartTime, Instant.now());
		if(timeSinceStart.compareTo(VM_RESET_TIME) > 0)
			stabilityTracker.resetStabilityHistory();
	}

	@Subscribe
	public void onScriptPostFired(ScriptPostFired event) {
		if (event.getScriptId() != PROC_VOLCANIC_MINE_SET_OTHERINFO) {
			return;
		}

		Widget widget = client.getWidget(HUD_COMPONENT, HUD_STABILITY_COMPONENT);
		if (widget != null) {
			widget.setText(widget.getText() + stabilityTracker.getStabilityText());
		}
	}

	//isInVM function taken from Hipipis Plugin hub VMPlugin
	private boolean isInVM()
	{
		return WorldPoint.fromLocalInstance(client, client.getLocalPlayer().getLocalLocation()).getRegionID() == VM_REGION_NORTH ||
				WorldPoint.fromLocalInstance(client, client.getLocalPlayer().getLocalLocation()).getRegionID() == VM_REGION_SOUTH;
	}
}
