package com.ultimatevm;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.NPC;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.TextComponent;
import net.runelite.client.util.Text;

public class BoulderHealthOverlay extends Overlay
{
	private static final int TEXT_Z_OFFSET = 100;
	private static final int BOULDER_BREAK_STAGE_1_ID = 7807;
	private static final int BOULDER_BREAK_STAGE_2_ID = 7809;
	private static final int BOULDER_BREAK_STAGE_3_ID = 7811;
	private static final int BOULDER_BREAK_STAGE_4_ID = 7813;
	private static final int BOULDER_BREAK_STAGE_5_ID = 7815;
	private static final int BOULDER_GIANT_ATTACHED_OBJECT_ID = 31034;
	private static final int BOULDER_GIANT_UNATTACHED_OBJECT_ID = 31035;
	private static final int BOULDER_LARGE_OBJECT_ID = 31036;
	private static final int BOULDER_MEDIUM_OBJECT_ID = 31037;
	private static final int BOULDER_SMALL_OBJECT_ID = 31038;
	private static final int ENTITY_BOULDER_WORLD_X_OFFSET_TILES = 1;
	private static final int ENTITY_BOULDER_WORLD_Y_OFFSET_TILES = 1;
	private static final int BOULDER_DEFAULT_HEALTH = 41;
	private static final int BOULDER_FINAL_STAGE_HEALTH = 61;
	private static final int VARBIT_PLAYER_COUNT = 5937;
	// A teammate doing a pickaxe mining animation within this many tiles of the tracked boulder point
	// counts as mining it. Generous because the giant boulder is large and the tracked point is only
	// roughly its centre - and the boulder is the only thing mined in the Volcanic Mine anyway.
	private static final int MINING_DISTANCE_TILES = 8;
	// Further than this from the boulder (or on another floor) you can't reliably see teammates at
	// it, so their last seen state is kept instead of being re-read.
	private static final int TEAMMATE_VISIBILITY_TILES = 12;
	private static final int NOT_MINING = -1;

	private final Client client;
	private final UltimateVolcanicMineConfig config;
	private final BoulderHealthEstimator healthEstimator = new BoulderHealthEstimator();
	private LocalPoint boulderLocalLocation;
	private WorldPoint boulderWorldLocation;
	// Teammate name -> mining animation they were last seen doing at the boulder (NOT_MINING if not).
	private final java.util.Map<String, Integer> teammateMiningAnimations = new java.util.HashMap<>();
	// Teammate name -> the pickaxe animation they were last seen mining with (kept for the whole game).
	private final java.util.Map<String, Integer> teammatePickaxes = new java.util.HashMap<>();
	private int miningTeammates;
	private double teammateDamageAt99;
	// Your own pickaxe, from the last mining animation seen (crystal speed until then).
	private int ownMiningAnimation = 8347;
	private int currentBoulderNumber = 1;
	private boolean boulderMoving;

	@Inject
	BoulderHealthOverlay(Client client, UltimateVolcanicMineConfig config)
	{
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
		this.client = client;
		this.config = config;
	}

	void startBoulder(LocalPoint localLocation, WorldPoint worldLocation, int baseHealth)
	{
		// Only a new stage resets the health - the boulder NPC can also respawn/change mid-stage.
		if (boulderLocalLocation == null)
		{
			healthEstimator.startStage(Math.max(0, baseHealth), getPlayerCount());
		}
		boulderLocalLocation = localLocation;
		boulderWorldLocation = worldLocation;
	}

	private int getPlayerCount()
	{
		return client != null ? Math.max(1, client.getVarbitValue(VARBIT_PLAYER_COUNT)) : 1;
	}

	void updateBoulderLocation(LocalPoint localLocation, WorldPoint worldLocation)
	{
		if (localLocation == null || worldLocation == null)
		{
			return;
		}

		boulderLocalLocation = localLocation;
		boulderWorldLocation = worldLocation;
	}

	void decrementHealth()
	{
		if (boulderLocalLocation == null)
		{
			return;
		}

		healthEstimator.onOwnSuccessfulMine();
	}

	void onGameTick()
	{
		ensureActiveBoulderTracked();
		refreshTrackedBoulderLocation();
		updateTeammateMining();
		healthEstimator.onTick(miningTeammates, teammateDamageAt99);
	}

	// Works out which teammates are mining the boulder this tick, and with which pickaxe. While you
	// are near the boulder that's read from what you can see; while you're away (upstairs, fixing a
	// vent) teammates can't be seen reliably, so they're assumed to carry on as last seen.
	private void updateTeammateMining()
	{
		Player local = client.getLocalPlayer();
		if (local != null && local.getWorldLocation() != null)
		{
			int ownAnimation = local.getAnimation();
			if (PickaxeSpeeds.isMiningAnimation(ownAnimation))
			{
				ownMiningAnimation = ownAnimation;
			}
		}

		if (boulderWorldLocation == null)
		{
			setTeammateTotals(0, 0);
			return;
		}

		boolean canSeeBoulder = local != null && local.getWorldLocation() != null
			&& local.getWorldLocation().distanceTo(boulderWorldLocation) <= TEAMMATE_VISIBILITY_TILES;
		if (canSeeBoulder)
		{
			teammateMiningAnimations.clear();
			for (Player player : client.getPlayers())
			{
				if (player == null || player == local || player.getName() == null || player.getWorldLocation() == null)
				{
					continue;
				}
				int animation = player.getAnimation();
				boolean mining = PickaxeSpeeds.isMiningAnimation(animation)
					&& player.getWorldLocation().distanceTo(boulderWorldLocation) <= MINING_DISTANCE_TILES;
				teammateMiningAnimations.put(player.getName(), mining ? animation : NOT_MINING);
				if (mining)
				{
					teammatePickaxes.put(player.getName(), animation);
				}
			}
		}

		int count = 0;
		double damage = 0;
		for (int animation : teammateMiningAnimations.values())
		{
			if (animation == NOT_MINING)
			{
				continue;
			}
			++count;
			damage += BoulderHealthEstimator.damagePerTick(animation);
		}
		setTeammateTotals(count, damage);
	}

	private void setTeammateTotals(int count, double damageAt99)
	{
		miningTeammates = count;
		teammateDamageAt99 = damageAt99;
	}

	/** Estimated seconds until the current stage breaks, assuming you keep mining. */
	double estimateSecondsRemaining()
	{
		return healthEstimator.estimateSecondsRemaining(BoulderHealthEstimator.damagePerTick(ownMiningAnimation), teammateDamageAt99);
	}

	/**
	 * Estimated seconds for the whole team to mine a full stage (e.g. the next boulder): the stage's
	 * health for this many players, divided by your pickaxe's rate plus every teammate's (from the
	 * pickaxe they were last seen with; a 99 crystal pickaxe for anyone not seen mining yet).
	 */
	double estimateFullStageSeconds(int stageNumber)
	{
		int players = getPlayerCount();
		int baseHealth = stageNumber >= 5 ? BOULDER_FINAL_STAGE_HEALTH : BOULDER_DEFAULT_HEALTH;
		return healthEstimator.secondsForHealth(baseHealth * players,
			BoulderHealthEstimator.damagePerTick(ownMiningAnimation), teamDamageAt99(players - 1, teammatePickaxes.values()));
	}

	static double teamDamageAt99(int teammates, java.util.Collection<Integer> knownPickaxes)
	{
		double damage = 0;
		int counted = 0;
		for (int animation : knownPickaxes)
		{
			if (counted >= teammates)
			{
				break;
			}
			damage += BoulderHealthEstimator.damagePerTick(animation);
			++counted;
		}
		// Teammates not seen mining yet: assume a 99 with a crystal pickaxe.
		damage += Math.max(0, teammates - counted) * BoulderHealthEstimator.SOLO_DAMAGE_PER_TICK;
		return damage;
	}

	void onNpcSpawned(NPC npc)
	{
		trackActiveBoulderFromNpc(npc);
	}

	void onNpcChanged(NPC npc)
	{
		trackActiveBoulderFromNpc(npc);
	}

	boolean handleBreakStageNpc(int npcId)
	{
		if (!isBreakStageNpcId(npcId))
		{
			return false;
		}

		if (currentBoulderNumber < 5)
		{
			++currentBoulderNumber;
		}

		boulderMoving = true;
		healthEstimator.onStageBroken();
		reset();
		return true;
	}

	boolean isBoulderMoving()
	{
		return boulderMoving;
	}

	/** The boulder (1-5) a break-stage NPC says just broke, or 0 if it isn't one. */
	static int brokenStageOf(int npcId)
	{
		switch (npcId)
		{
			case BOULDER_BREAK_STAGE_1_ID: return 1;
			case BOULDER_BREAK_STAGE_2_ID: return 2;
			case BOULDER_BREAK_STAGE_3_ID: return 3;
			case BOULDER_BREAK_STAGE_4_ID: return 4;
			case BOULDER_BREAK_STAGE_5_ID: return 5;
			default: return 0;
		}
	}

	boolean isFinalBreakStageNpcId(int npcId)
	{
		return npcId == BOULDER_BREAK_STAGE_5_ID;
	}

	void resetTracking()
	{
		reset();
		healthEstimator.resetForNewGame();
		teammateMiningAnimations.clear();
		teammatePickaxes.clear();
		ownMiningAnimation = 8347;
		currentBoulderNumber = 1;
	}

	void reset()
	{
		boulderLocalLocation = null;
		boulderWorldLocation = null;
		miningTeammates = 0;
		healthEstimator.clear();
	}

	int getCurrentHealth()
	{
		return healthEstimator.getHealth();
	}

	int getCurrentBoulderNumber()
	{
		return currentBoulderNumber;
	}

	WorldPoint getBoulderLocation()
	{
		return boulderWorldLocation;
	}

	LocalPoint getBoulderLocalLocation()
	{
		return boulderLocalLocation;
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.showBoulderHealth() || boulderLocalLocation == null || currentBoulderNumber > 5)
		{
			return null;
		}

		Point canvasLocation = Perspective.localToCanvas(client, boulderLocalLocation, client.getPlane(), TEXT_Z_OFFSET);
		if (canvasLocation == null)
		{
			return null;
		}

		TextComponent textComponent = new TextComponent();
		textComponent.setText(Integer.toString(getCurrentHealth()));
		textComponent.setFont(new Font("Arial Bold", Font.BOLD, 16));
		textComponent.setPosition(new java.awt.Point(canvasLocation.getX(), canvasLocation.getY()));
		textComponent.setColor(getHealthColor());
		textComponent.render(graphics);
		return null;
	}

	private void trackActiveBoulderFromNpc(NPC npc)
	{
		if (!isMineableBoulderNpc(npc))
		{
			return;
		}

		boulderMoving = false;
		WorldPoint adjustedWorld = adjustBoulderWorldPoint(npc, npc.getWorldLocation());
		startBoulderTracking(adjustedWorld, npc.getLocalLocation());
	}

	private void ensureActiveBoulderTracked()
	{
		if (getBoulderLocalLocation() != null)
		{
			return;
		}

		for (NPC npc : client.getNpcs())
		{
			if (!isMineableBoulderNpc(npc))
			{
				continue;
			}
			trackActiveBoulderFromNpc(npc);
			return;
		}

		scanSceneForBoulder();
	}

	private void refreshTrackedBoulderLocation()
	{
		WorldPoint trackedWorld = getBoulderLocation();
		if (trackedWorld == null)
		{
			return;
		}

		NPC candidateNpc = null;
		int bestNpcDistance = Integer.MAX_VALUE;
		for (NPC npc : client.getNpcs())
		{
			if (!isMineableBoulderNpc(npc) || npc.getWorldLocation() == null)
			{
				continue;
			}

			int distance = npc.getWorldLocation().distanceTo2D(trackedWorld);
			if (distance < bestNpcDistance)
			{
				bestNpcDistance = distance;
				candidateNpc = npc;
			}
		}

		if (candidateNpc != null)
		{
			WorldPoint npcWorld = adjustBoulderWorldPoint(candidateNpc, candidateNpc.getWorldLocation());
			LocalPoint npcLocal = LocalPoint.fromWorld(client, npcWorld);
			if (npcLocal == null)
			{
				npcLocal = candidateNpc.getLocalLocation();
			}

			LocalPoint trackedLocal = getBoulderLocalLocation();
			if (!npcWorld.equals(trackedWorld) || (npcLocal != null && !npcLocal.equals(trackedLocal)))
			{
				updateBoulderLocation(npcLocal, npcWorld);
			}
			return;
		}

		LocalPoint refreshedLocal = LocalPoint.fromWorld(client, trackedWorld);
		LocalPoint trackedLocal = getBoulderLocalLocation();
		if (refreshedLocal != null && !refreshedLocal.equals(trackedLocal))
		{
			updateBoulderLocation(refreshedLocal, trackedWorld);
		}
	}

	private void scanSceneForBoulder()
	{
		Scene scene = client.getScene();
		if (scene == null || client.getPlane() < 0)
		{
			return;
		}

		Tile[][][] tiles = scene.getTiles();
		int plane = client.getPlane();
		if (tiles == null || plane >= tiles.length)
		{
			return;
		}

		for (Tile[] column : tiles[plane])
		{
			if (column == null)
			{
				continue;
			}

			for (Tile tile : column)
			{
				if (tile == null)
				{
					continue;
				}

				for (GameObject gameObject : tile.getGameObjects())
				{
					if (gameObject == null || !isMineableBoulderGameObject(gameObject))
					{
						continue;
					}
					trackActiveBoulderFromGameObject(gameObject);
					return;
				}
			}
		}
	}

	private void trackActiveBoulderFromGameObject(GameObject gameObject)
	{
		if (!isMineableBoulderGameObject(gameObject))
		{
			return;
		}

		WorldPoint adjustedWorld = adjustBoulderWorldPoint(gameObject, gameObject.getWorldLocation());
		startBoulderTracking(adjustedWorld, gameObject.getLocalLocation());
	}

	private WorldPoint adjustBoulderWorldPoint(GameObject gameObject, WorldPoint originalWorld)
	{
		if (gameObject == null || originalWorld == null)
		{
			return originalWorld;
		}

		return new WorldPoint(
			originalWorld.getX() + ENTITY_BOULDER_WORLD_X_OFFSET_TILES,
			originalWorld.getY(),
			originalWorld.getPlane());
	}

	private WorldPoint adjustBoulderWorldPoint(NPC npc, WorldPoint originalWorld)
	{
		if (npc == null || originalWorld == null)
		{
			return originalWorld;
		}

		if (currentBoulderNumber == 2)
		{
			return new WorldPoint(
				originalWorld.getX() + ENTITY_BOULDER_WORLD_X_OFFSET_TILES + 2,
				originalWorld.getY() + ENTITY_BOULDER_WORLD_Y_OFFSET_TILES + 2,
				originalWorld.getPlane());
		}

		if (currentBoulderNumber == 3)
		{
			return new WorldPoint(
				originalWorld.getX() + ENTITY_BOULDER_WORLD_X_OFFSET_TILES + 1,
				originalWorld.getY() + ENTITY_BOULDER_WORLD_Y_OFFSET_TILES + 1,
				originalWorld.getPlane());
		}

		if (currentBoulderNumber == 4)
		{
			return new WorldPoint(
				originalWorld.getX() + ENTITY_BOULDER_WORLD_X_OFFSET_TILES,
				originalWorld.getY() + ENTITY_BOULDER_WORLD_Y_OFFSET_TILES,
				originalWorld.getPlane());
		}

		return new WorldPoint(
			originalWorld.getX() + ENTITY_BOULDER_WORLD_X_OFFSET_TILES,
			originalWorld.getY(),
			originalWorld.getPlane());
	}

	private boolean isMineableBoulderNpc(NPC npc)
	{
		if (npc == null || isBreakStageNpcId(npc.getId()))
		{
			return false;
		}

		String npcName = sanitizeNpcName(npc).toLowerCase();
		return npcName.contains("boulder");
	}

	private boolean isMineableBoulderGameObject(GameObject gameObject)
	{
		if (gameObject == null)
		{
			return false;
		}

		switch (gameObject.getId())
		{
			case BOULDER_GIANT_ATTACHED_OBJECT_ID:
			case BOULDER_GIANT_UNATTACHED_OBJECT_ID:
			case BOULDER_LARGE_OBJECT_ID:
			case BOULDER_MEDIUM_OBJECT_ID:
			case BOULDER_SMALL_OBJECT_ID:
				return true;
			default:
				return false;
		}
	}

	private boolean isBreakStageNpcId(int npcId)
	{
		switch (npcId)
		{
			case BOULDER_BREAK_STAGE_1_ID:
			case BOULDER_BREAK_STAGE_2_ID:
			case BOULDER_BREAK_STAGE_3_ID:
			case BOULDER_BREAK_STAGE_4_ID:
			case BOULDER_BREAK_STAGE_5_ID:
				return true;
			default:
				return false;
		}
	}

	private int getExpectedHealthForCurrentBoulder()
	{
		return currentBoulderNumber == 5 ? BOULDER_FINAL_STAGE_HEALTH : BOULDER_DEFAULT_HEALTH;
	}

	private void startBoulderTracking(WorldPoint adjustedWorld, LocalPoint entityLocal)
	{
		int expectedHealth = getExpectedHealthForCurrentBoulder();
		LocalPoint adjustedLocal = LocalPoint.fromWorld(client, adjustedWorld);
		if (adjustedLocal == null)
		{
			adjustedLocal = entityLocal;
		}

		startBoulder(adjustedLocal, adjustedWorld, expectedHealth);
	}

	private String sanitizeNpcName(NPC npc)
	{
		if (npc == null || npc.getName() == null)
		{
			return "";
		}

		return Text.removeTags(npc.getName());
	}

	// Same thresholds as before for a solo 41-health stage (10 / 20), scaled to the team's total.
	private Color getHealthColor()
	{
		int health = getCurrentHealth();
		int maxHealth = Math.max(1, healthEstimator.getMaxHealth());
		double fraction = (double) health / maxHealth;
		if (fraction <= 10.0 / 41)
		{
			return Color.RED;
		}
		if (fraction <= 20.0 / 41)
		{
			return Color.ORANGE;
		}
		return Color.GREEN;
	}
}
