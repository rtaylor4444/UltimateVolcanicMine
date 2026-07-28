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

	private final Client client;
	private final UltimateVolcanicMineConfig config;
	private LocalPoint boulderLocalLocation;
	private WorldPoint boulderWorldLocation;
	private int currentHealth;
	private int currentBoulderNumber = 1;

	@Inject
	BoulderHealthOverlay(Client client, UltimateVolcanicMineConfig config)
	{
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
		this.client = client;
		this.config = config;
	}

	void startBoulder(LocalPoint localLocation, WorldPoint worldLocation, int maxHealth)
	{
		boulderLocalLocation = localLocation;
		boulderWorldLocation = worldLocation;
		currentHealth = Math.max(0, maxHealth);
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
		if (boulderLocalLocation == null || currentHealth <= 0)
		{
			return;
		}

		currentHealth = Math.max(0, currentHealth - 1);
	}

	void onGameTick()
	{
		ensureActiveBoulderTracked();
		refreshTrackedBoulderLocation();
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

		reset();
		return true;
	}

	boolean isFinalBreakStageNpcId(int npcId)
	{
		return npcId == BOULDER_BREAK_STAGE_5_ID;
	}

	void resetTracking()
	{
		reset();
		currentBoulderNumber = 1;
	}

	void reset()
	{
		boulderLocalLocation = null;
		boulderWorldLocation = null;
		currentHealth = 0;
	}

	int getCurrentHealth()
	{
		return currentHealth;
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
		textComponent.setText(Integer.toString(currentHealth));
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

	private Color getHealthColor()
	{
		if (currentHealth <= 10)
		{
			return Color.RED;
		}
		if (currentHealth <= 20)
		{
			return Color.ORANGE;
		}
		return Color.GREEN;
	}
}
