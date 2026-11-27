package com.ultimatevm;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SimulatorEarthquakeTest
{
	private static final int ALL_DOWN = 0;
	private static final int ALL_UP = 7;

	@Test
	public void ventsMoveOnTheSameClockAfterTheFiveMinuteReset()
	{
		// Moves are every 6s from the start of the game (306, 312, ...), not offset from 0:45.
		assertArrayEquals(new int[]{20, 20, 20}, Simulator.getVentValuesAtTime(20, 20, 20, ALL_UP, 301, 306));
		assertArrayEquals(new int[]{22, 22, 22}, Simulator.getVentValuesAtTime(20, 20, 20, ALL_UP, 301, 307));
	}

	@Test
	public void blowUpChanceFollowsTheWeights()
	{
		// One set of vents that certainly blows up (all at 0, stability 10) and one that certainly
		// doesn't (all at 50, stability 10 - they stay near 50 and add +23 a time).
		List<int[]> candidates = Arrays.asList(new int[]{0, 0, 0}, new int[]{50, 50, 50});
		Simulator.Prediction likelyDeath = Simulator.predict(candidates, new double[]{0.9, 0.1}, ALL_DOWN, 10, 120);
		Simulator.Prediction likelySafe = Simulator.predict(candidates, new double[]{0.1, 0.9}, ALL_DOWN, 10, 120);
		assertEquals(90.0, likelyDeath.blowUpChancePercent, 0.5);
		assertEquals(10.0, likelySafe.blowUpChancePercent, 0.5);
		// Equal weights by default.
		assertEquals(50.0, Simulator.predict(candidates, ALL_DOWN, 10, 120).blowUpChancePercent, 0.5);
	}

	@Test
	public void likelyBandSitsInsideThePossibleRange()
	{
		List<int[]> candidates = Arrays.asList(new int[]{62, 35, 71}, new int[]{63, 35, 71}, new int[]{40, 58, 66});
		Simulator.Prediction p = Simulator.predict(candidates, 5, 80, 46);
		assertEquals(p.possibleRangeOverTime.size(), p.stabilityRangeOverTime.size());
		int widerSomewhere = 0;
		for (int i = 0; i < p.stabilityRangeOverTime.size(); i++)
		{
			Simulator.StabilityRangePoint likely = p.stabilityRangeOverTime.get(i);
			Simulator.StabilityRangePoint possible = p.possibleRangeOverTime.get(i);
			assertEquals(possible.time, likely.time);
			assertTrue("t=" + likely.time, possible.minStability <= likely.minStability && likely.maxStability <= possible.maxStability);
			if (possible.maxStability - possible.minStability > likely.maxStability - likely.minStability)
			{
				widerSomewhere++;
			}
		}
		// The rare earthquake extremes widen the possible range well beyond the likely band.
		assertTrue(widerSomewhere > 0);
	}

	@Test
	public void planRiskCountsBeingShortAtFiveMinutes()
	{
		// Vents all at 0 (moving down, so they stay there): every update is -25. From 35 at 285s
		// the 5:00 update leaves stability at 10 - short of 20, though not blown up.
		List<int[]> vents = java.util.Collections.singletonList(new int[]{0, 0, 0});
		double[] one = {1};
		assertEquals(1.0, Simulator.planRisk(vents, one, ALL_DOWN, 35, 286, java.util.Collections.emptyList(), 20), 1e-9);
		assertEquals(0.0, Simulator.planRisk(vents, one, ALL_DOWN, 35, 286, java.util.Collections.emptyList(), 1), 1e-9);
	}

	@Test
	public void planRiskFollowsTheWeights()
	{
		// One set of vents certainly short at 5:00, one certainly fine.
		List<int[]> candidates = Arrays.asList(new int[]{0, 0, 0}, new int[]{50, 50, 50});
		double risk = Simulator.planRisk(candidates, new double[]{0.3, 0.7}, ALL_DOWN, 35, 286, java.util.Collections.emptyList(), 20);
		assertEquals(0.3, risk, 1e-6);
	}

	@Test
	public void aHelpfulFixLowersThePlanRisk()
	{
		// Vents drifting away from 50 with stability falling; flipping all three chambers early
		// turns them back towards 50.
		List<int[]> vents = java.util.Collections.singletonList(new int[]{30, 30, 30});
		double[] one = {1};
		double noFix = Simulator.planRisk(vents, one, ALL_DOWN, 60, 120, java.util.Collections.emptyList(), 20);
		List<Simulator.FlipEvent> fixAll = Arrays.asList(new Simulator.FlipEvent(130, Simulator.CHAMBER_A_BIT),
			new Simulator.FlipEvent(130, Simulator.CHAMBER_B_BIT), new Simulator.FlipEvent(130, Simulator.CHAMBER_C_BIT));
		double withFix = Simulator.planRisk(vents, one, ALL_DOWN, 60, 120, fixAll, 20);
		assertTrue("noFix " + noFix + " withFix " + withFix, withFix < noFix);
	}

	@Test
	public void aFlipOnAnUpdateSecondAppliesThatUpdateOnce()
	{
		// Vents at 0 moving down: -25 an update. From 70, the 285s and 300s updates leave 20 - just
		// safe. A flip at 285s used to apply the 285s update twice (once each side of the flip),
		// ending at -5. A moving up after the flip only helps.
		List<int[]> vents = java.util.Collections.singletonList(new int[]{0, 0, 0});
		List<Simulator.FlipEvent> flip = java.util.Collections.singletonList(new Simulator.FlipEvent(285, Simulator.CHAMBER_A_BIT));
		assertTrue(!Simulator.anyCandidateBlowsUp(vents, ALL_DOWN, 70, 271, flip, 20));
		assertEquals(0.0, Simulator.weightedBlowUpShare(vents, new double[]{1}, ALL_DOWN, 70, 271, flip, 20), 1e-9);
		assertEquals(0.0, Simulator.planRisk(vents, new double[]{1}, ALL_DOWN, 70, 271, flip, 20), 1e-9);
	}

	@Test
	public void oneEarthquakeCancelsBothTheMoveAndTheUpdateAtOneMinute()
	{
		// At 60s a move and an update are both due. Pick a stability the update only kills if the
		// vents moved first: one earthquake cancels both (survive), no earthquake means both
		// happen (blow up). So the blow-up chance is exactly the no-earthquake chance.
		int[] start = {10, 10, 10};
		int[] moved = VentRules.move(start[0], start[1], start[2], ALL_DOWN);
		int changeIfMoved = VentRules.stabilityChange(moved[0], moved[1], moved[2]);
		assertTrue(changeIfMoved < VentRules.stabilityChange(start[0], start[1], start[2]));
		assertTrue(changeIfMoved < 0);
		double chance = Simulator.simulateBlowUpProbability(java.util.Collections.singletonList(start), new double[]{1}, ALL_DOWN,
			-changeIfMoved, 55, 60);
		assertEquals(1 - Simulator.P_EARTHQUAKE, chance, 1e-9);
	}

	@Test
	public void aFixAtFiveMinutesDoesNotStretchTheCheckIntoTheSecondHalf()
	{
		// Vents at 0 moving down: -25 an update. From 67 at 4:31 the 4:45 and 5:00 updates leave
		// 17 - safe for the first half. A fix landing at exactly 5:00 used to run the check on to
		// 0:45 with the old vents, where it blows up.
		List<int[]> vents = java.util.Collections.singletonList(new int[]{0, 0, 0});
		List<Simulator.FlipEvent> fixAtFive = java.util.Collections.singletonList(new Simulator.FlipEvent(300, Simulator.CHAMBER_A_BIT));
		assertTrue(!Simulator.anyCandidateBlowsUp(vents, ALL_DOWN, 67, 271, fixAtFive, 1));
		assertEquals(0.0, Simulator.weightedBlowUpShare(vents, new double[]{1}, ALL_DOWN, 67, 271, fixAtFive, 1), 1e-9);
		assertEquals(0.0, Simulator.planRisk(vents, new double[]{1}, ALL_DOWN, 67, 271, fixAtFive, 1), 1e-9);
	}

	@Test
	public void aBlowUpBeforeAFixStaysABlowUp()
	{
		// Vents at 25 (-1 a time) blow up at 15s from stability 1. By 30s they've moved up enough
		// for a positive update, which used to "revive" the mine when a fix landed at 30s.
		int[] at30 = Simulator.getVentValuesAtTime(25, 25, 25, ALL_UP, 13, 31);
		assertTrue(VentRules.stabilityChange(25, 25, 25) == -1 && VentRules.stabilityChange(at30[0], at30[1], at30[2]) > 0);
		List<int[]> vents = java.util.Collections.singletonList(new int[]{25, 25, 25});
		// Two flips of A at 30s: a fix there that leaves the chambers as they were.
		List<Simulator.FlipEvent> fix = Arrays.asList(new Simulator.FlipEvent(30, Simulator.CHAMBER_A_BIT),
			new Simulator.FlipEvent(30, Simulator.CHAMBER_A_BIT));
		assertTrue(Simulator.anyCandidateBlowsUp(vents, ALL_UP, 1, 13, fix, 1));
		assertEquals(1.0, Simulator.weightedBlowUpShare(vents, new double[]{1}, ALL_UP, 1, 13, fix, 1), 1e-9);
	}

	@Test
	public void nothingHappensAtTheVeryStartOfTheGame()
	{
		// A prediction from 0:00 must not count a move or an update at 0:00.
		assertEquals(15, Simulator.simulate(50, 50, 50, ALL_UP, 50, 0).stabilityOverTime.get(0).time);
		assertArrayEquals(new int[]{50, 50, 50}, Simulator.getVentValuesAtTime(50, 50, 50, ALL_UP, 0, 6));
	}

	@Test
	public void cancellableMoveChancesAddUpToOne()
	{
		double total = 0;
		for (double chance : Simulator.cancellableMoveChances())
		{
			total += chance;
		}
		assertEquals(1.0, total, 1e-12);
	}

	@Test
	public void futureEarthquakesCanSaveAnOtherwiseCertainBlowUp()
	{
		// Stability 3 just before the 60s update (an earthquake can cancel it). Without an
		// earthquake these vents blow up there; the blow-up chance has to be below 100% because an
		// earthquake can cancel the update, but it's still the most likely outcome.
		Simulator.SimulationResult nominal = Simulator.simulate(5, 5, 5, ALL_DOWN, 3, 50);
		assertTrue(nominal.willDie);
		double chance = Simulator.predict(java.util.Collections.singletonList(new int[]{5, 5, 5}), ALL_DOWN, 3, 50).blowUpChancePercent;
		assertTrue("chance " + chance, chance > 50 && chance <= 100);
	}

	@Test
	public void slowedVentsNeverMoveBackwards()
	{
		// All three in 41-59: B's movement is 1 - 1 = 0 and C's is 1 - 1 - 1, but C stays put
		// rather than reversing.
		assertArrayEquals(new int[]{51, 50, 50}, VentRules.move(50, 50, 50, ALL_UP));
		// B in 41-59 with A in 41-59: 1 - 1 = 0, B stays.
		assertArrayEquals(new int[]{44, 45, 30}, VentRules.move(45, 45, 30, ALL_DOWN));
	}

	@Test
	public void noVentMoveAtTheFiveMinuteUpdate()
	{
		// Logged game: A=0, B=78 (up), stability 67 at 294s -> 50 at 300s, i.e. B didn't move to 80.
		Simulator.SimulationResult result = Simulator.simulate(0, 78, 84, 6, 67, 295);
		assertEquals(300, result.stabilityOverTime.get(0).time);
		// The only move tick between 295s and the update is 300s itself, which doesn't happen.
		assertEquals(67 + VentRules.stabilityChange(0, 78, 84), result.stabilityOverTime.get(0).stability);
	}

	@Test
	public void earthquakeTimesMatchTheGameClock()
	{
		assertTrue(Simulator.canEarthquakeCancelVentMove(6));
		assertTrue(Simulator.canEarthquakeCancelVentMove(24));
		assertTrue(!Simulator.canEarthquakeCancelVentMove(12));
		assertTrue(Simulator.canEarthquakeCancelStabilityUpdate(15));
		assertTrue(Simulator.canEarthquakeCancelStabilityUpdate(60));
		assertTrue(!Simulator.canEarthquakeCancelStabilityUpdate(30));
	}

	@Test
	public void rangeCoversCancelledMoveAndCancelledUpdate()
	{
		// From 46s the next update is at 60s. Moves at 48 and 54 always happen; the move at 60 and
		// the update at 60 can each be cancelled by an earthquake.
		int stability = 50;
		int[] after2 = move(move(new int[]{30, 30, 30}));
		int[] after3 = move(after2);
		int withMove = clamp(stability + VentRules.stabilityChange(after3[0], after3[1], after3[2]));
		int withoutMove = clamp(stability + VentRules.stabilityChange(after2[0], after2[1], after2[2]));
		// A and B cancelled while C still moves.
		int onlyCMoved = clamp(stability + VentRules.stabilityChange(after2[0], after2[1], after3[2]));
		int expectedMin = Math.min(stability, Math.min(withMove, Math.min(withoutMove, onlyCMoved)));
		int expectedMax = Math.max(stability, Math.max(withMove, Math.max(withoutMove, onlyCMoved)));

		Simulator.SimulationResult result = Simulator.simulate(30, 30, 30, ALL_DOWN, stability, 46);
		Simulator.StabilityRangePoint first = result.earthquakeRangeOverTime.get(0);
		assertEquals(60, first.time);
		assertEquals(expectedMin, first.minStability);
		assertEquals(expectedMax, first.maxStability);
		// Without earthquakes it's the normal prediction.
		assertEquals(withMove, result.stabilityOverTime.get(0).stability);
	}

	@Test
	public void rangeAlwaysContainsTheNoEarthquakePrediction()
	{
		for (int start : Arrays.asList(1, 46, 133, 250, 301, 400))
		{
			Simulator.SimulationResult result = Simulator.simulate(62, 35, 71, 5, 80, start);
			List<Simulator.StabilityRangePoint> range = result.earthquakeRangeOverTime;
			for (Simulator.StabilityPoint point : result.stabilityOverTime)
			{
				Simulator.StabilityRangePoint r = range.stream().filter(p -> p.time == point.time).findFirst().orElse(null);
				assertTrue("missing range at " + point.time, r != null);
				assertTrue("start " + start + " t=" + point.time + " " + point.stability + " not in [" + r.minStability + "," + r.maxStability + "]",
					r.minStability <= point.stability && point.stability <= r.maxStability);
			}
		}
	}

	@Test
	public void blowUpIsFinalInTheRange()
	{
		// Falling fast: once the lowest path hits 0 it stays at 0.
		Simulator.SimulationResult result = Simulator.simulate(0, 0, 0, ALL_DOWN, 30, 1);
		boolean seenZero = false;
		for (Simulator.StabilityRangePoint p : result.earthquakeRangeOverTime)
		{
			if (seenZero)
			{
				assertEquals(0, p.minStability);
			}
			seenZero |= p.minStability == 0;
		}
		assertTrue(seenZero);
	}

	private static int[] move(int[] v)
	{
		return VentRules.move(v[0], v[1], v[2], ALL_DOWN);
	}

	private static int clamp(int value)
	{
		return Math.max(0, Math.min(100, value));
	}
}
