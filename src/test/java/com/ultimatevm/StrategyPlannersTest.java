package com.ultimatevm;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Spot checks of the ported strategy. The full check against the simulator (thousands of random
 * states, every decision identical) is the scratch comparison run described in the change notes.
 */
public class StrategyPlannersTest
{
	private static StrategyPredictor predictor(int chamber, int stability, int time, int[]... vents)
	{
		double[] w = new double[vents.length];
		Arrays.fill(w, 1);
		return new StrategyPredictor(Arrays.asList(vents), w, chamber, stability, time);
	}

	@Test
	public void plansAreCheapestFirstAsInTheSimulator()
	{
		// rules.py costs: B after B2 15.12 s, A after B1 26.15 s (after its vent check's credit).
		assertEquals(15.12, HalfOnePlanner.Plan.FIX_B_AFTER_B2.costSeconds, 1e-9);
		assertEquals((70.7 - 15) * 0.6 - HalfOnePlanner.CHECK_CREDIT_SECONDS, HalfOnePlanner.Plan.FIX_A_AFTER_B1.costSeconds, 1e-9);
	}

	@Test
	public void aSafeGameNeedsNoFix()
	{
		StrategyPredictor p = predictor(0, 60, 46, new int[]{50, 50, 50});
		assertEquals(HalfOnePlanner.Plan.NONE, HalfOnePlanner.decide(p, HalfOnePlanner.Checkpoint.MINING_B1, 45, 40, true).plan);
	}

	@Test
	public void fixNowDetoursAreOnlyOfferedWhileMining()
	{
		StrategyPredictor p = predictor(0, 60, 31, new int[]{20, 25, 50});
		assertNull(HalfOnePlanner.flips(HalfOnePlanner.Plan.FIX_A_NOW_B1, HalfOnePlanner.Checkpoint.MINING_B1, 30, 40, false));
		List<Simulator.FlipEvent> flips = HalfOnePlanner.flips(HalfOnePlanner.Plan.FIX_A_NOW_B1, HalfOnePlanner.Checkpoint.MINING_B1, 30, 40, true);
		// 31.2 ticks after setting off at 30 s: lands at 48.72 s -> second 49.
		assertEquals(1, flips.size());
		assertEquals(49, flips.get(0).time);
		assertEquals(Simulator.CHAMBER_A_BIT, flips.get(0).chamberBit);
		assertTrue(p.deathRate(300) > 0);
	}

	@Test
	public void deathTimeAllowsAFivePercentShare()
	{
		// One of 30 candidates blows up early: under 5%, so it doesn't make you walk out.
		int[][] vents = new int[30][];
		for (int i = 0; i < 29; i++)
		{
			vents[i] = new int[]{50, 50, 50};
		}
		vents[29] = new int[]{0, 0, 0};
		StrategyPredictor p = predictor(0, 20, 301, vents);
		Integer any = p.deathTime(555, 0);
		assertTrue(any != null);
		assertNull(p.deathTime(555, HalfTwoPlanner.EXIT_RISK));
	}

	@Test
	public void headingAwayFollowsTheChamber()
	{
		// 30 falling (open) is heading away; 30 rising (blocked) is heading back to 50.
		assertEquals(1.0, predictor(0, 50, 301, new int[]{30, 30, 30}).pHeadingAway(1), 1e-9);
		assertEquals(0.0, predictor(2, 50, 301, new int[]{30, 30, 30}).pHeadingAway(1), 1e-9);
		assertEquals(1.0, predictor(2, 50, 301, new int[]{70, 70, 70}).pHeadingAway(1), 1e-9);
	}

	@Test
	public void theBVentChoiceIsTheBestScoringOption()
	{
		// B rising from 80 (blocked) heads away; A and C hold near 50 for a while. Whatever wins must
		// score at least as well as going straight to B5.
		StrategyPredictor p = predictor(2, 45, 371, new int[]{55, 80, 52}, new int[]{56, 85, 50});
		HalfTwoPlanner.B5Option option = HalfTwoPlanner.chooseAtBVent(p, 370, 365, true, 1);
		double none = HalfTwoPlanner.b5OptionValue(p, 370, new double[0][], HalfTwoPlanner.B_VENT_TO_B5, 365 + 32 * 0.6, 5);
		double fixB = HalfTwoPlanner.b5OptionValue(p, 370, new double[][]{{22 + HalfTwoPlanner.UNBLOCK, 1}},
			22 + HalfTwoPlanner.UNBLOCK + HalfTwoPlanner.B_CHAMBER_TO_B5, 365 + 32 * 0.6, 4);
		assertEquals(fixB > none + 1e-6 ? HalfTwoPlanner.B5Option.B : option, option);
	}

	@Test
	public void blowUpTimesStopAtZero()
	{
		int[] times = Simulator.blowUpTimes(Collections.singletonList(new int[]{0, 0, 0}), 0, 30, 1, 300, Collections.emptyList());
		assertTrue(times[0] < 300);
		assertEquals(0, times[0] % 15);
		// Vents at 50 add +23 an update for the first minute.
		int[] safe = Simulator.blowUpTimes(Collections.singletonList(new int[]{50, 50, 50}), 0, 30, 1, 60, Collections.emptyList());
		assertEquals(Simulator.NEVER, safe[0]);
	}

	@Test
	public void shortOfTimeAfterB4GoesStraightToB5()
	{
		// B4 done at 2:43 left (437 s), the mine safe: the vent checks would leave B5 unfinished.
		StrategyPredictor safe = predictor(2, 100, 440, new int[]{50, 50, 50});
		assertTrue(HalfTwoPlanner.skipChecksForB5(safe, 440, 437, false));
		// With time to spare (B4 done at 6:10 left), the checks are made as usual.
		StrategyPredictor early = predictor(2, 100, 233, new int[]{50, 50, 50});
		assertTrue(!HalfTwoPlanner.skipChecksForB5(early, 233, 230, false));
	}

	@Test
	public void asManyCapsAsFitWhileB4RollsIn()
	{
		// At the B rock 10 ticks after B3 broke (from B3's east side), one rock in hand.
		double b3 = 270;
		double atRock = b3 + 10 * 0.6;
		// B blocked: unblock, block (the rock), unblock - 3 caps in ~8 ticks, before B4 arrives.
		assertEquals(3, HalfTwoPlanner.windowCaps(atRock, b3, true, 1, 1));
		// B open: block (the rock), unblock - 2.
		assertEquals(2, HalfTwoPlanner.windowCaps(atRock, b3, false, 1, 1));
		// Only the 6 paid caps.
		assertEquals(1, HalfTwoPlanner.windowCaps(atRock, b3, true, 1, 5));
		// Late to the rock (from B3's west side, 21 ticks): fewer fit.
		assertTrue(HalfTwoPlanner.windowCaps(b3 + 21 * 0.6, b3, true, 1, 1) < 3);
	}
}
