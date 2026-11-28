package com.ultimatevm;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SimulationStartTest
{
	@Test
	public void predictionsStartAfterTheEventsOfTheCurrentTick()
	{
		// rem 975 is the 15s stability update: it has happened, so the simulation starts at 16.
		assertEquals(16, UltimateVolcanicMinePlugin.toSimulationStartSeconds(975));
		// rem 976 is the tick before it: the 15s update is still to come.
		assertEquals(15, UltimateVolcanicMinePlugin.toSimulationStartSeconds(976));
		// rem 990 is the 6s vent move (e.g. one an earthquake just cancelled).
		assertEquals(7, UltimateVolcanicMinePlugin.toSimulationStartSeconds(990));
		assertEquals(6, UltimateVolcanicMinePlugin.toSimulationStartSeconds(991));
		// Game start: nothing has happened yet, and nothing happens at 0:00.
		assertEquals(1, UltimateVolcanicMinePlugin.toSimulationStartSeconds(1000));
		// The tick before the 5:00 update still has it to come.
		assertEquals(300, UltimateVolcanicMinePlugin.toSimulationStartSeconds(501));
		assertEquals(301, UltimateVolcanicMinePlugin.toSimulationStartSeconds(500));
	}
}
