package com.ultimatevm;

import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;

// Tracks how many consecutive ticks the local player has had no animation and not moved
public class IdlePlayerTracker
{
	private static final int IDLE_ANIMATION_ID = -1;

	private WorldPoint lastWorldPoint;
	private int idleTicks;

	public void onGameTick(Player player)
	{
		if (player == null)
		{
			reset();
			return;
		}

		WorldPoint currentWorldPoint = player.getWorldLocation();
		boolean hasMoved = lastWorldPoint != null && !lastWorldPoint.equals(currentWorldPoint);
		lastWorldPoint = currentWorldPoint;

		if (!hasMoved && player.getAnimation() == IDLE_ANIMATION_ID)
		{
			++idleTicks;
		}
		else
		{
			idleTicks = 0;
		}
	}

	public int getIdleTicks()
	{
		return idleTicks;
	}

	public void reset()
	{
		idleTicks = 0;
		lastWorldPoint = null;
	}
}
