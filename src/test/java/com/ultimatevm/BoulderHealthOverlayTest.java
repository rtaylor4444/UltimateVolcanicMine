package com.ultimatevm;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BoulderHealthOverlayTest
{
	@Test
	public void tracksBoulderNumberAcrossBreakStages()
	{
		BoulderHealthOverlay overlay = new BoulderHealthOverlay(null, null);
		assertEquals(1, overlay.getCurrentBoulderNumber());
		assertFalse(overlay.handleBreakStageNpc(-1));
		assertEquals(1, overlay.getCurrentBoulderNumber());

		int[] breakStages = {7807, 7809, 7811, 7813, 7815};
		for (int i = 0; i < breakStages.length; ++i)
		{
			assertTrue(overlay.handleBreakStageNpc(breakStages[i]));
			assertEquals(Math.min(i + 2, 5), overlay.getCurrentBoulderNumber());
			assertEquals(0, overlay.getCurrentHealth());
		}
	}

	@Test
	public void resetsBoulderNumberForNewGame()
	{
		BoulderHealthOverlay overlay = new BoulderHealthOverlay(null, null);
		overlay.handleBreakStageNpc(7807);
		assertEquals(2, overlay.getCurrentBoulderNumber());

		overlay.resetTracking();

		assertEquals(1, overlay.getCurrentBoulderNumber());
		assertEquals(0, overlay.getCurrentHealth());
	}
}
