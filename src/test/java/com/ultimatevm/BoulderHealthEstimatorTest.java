package com.ultimatevm;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BoulderHealthEstimatorTest
{
	private static final int DRAGON = 7139;
	private static final int CRYSTAL = 8347;
	private static final int RUNE = 624;

	@Test
	public void soloMatchesOwnMinesExactly()
	{
		BoulderHealthEstimator estimator = new BoulderHealthEstimator();
		estimator.startStage(41, 1);
		for (int i = 0; i < 10; i++)
		{
			estimator.onOwnSuccessfulMine();
			estimator.onTick(0, 0);
		}
		assertEquals(31, estimator.getHealth());
	}

	@Test
	public void stageHealthScalesWithPlayerCount()
	{
		BoulderHealthEstimator estimator = new BoulderHealthEstimator();
		estimator.startStage(41, 4);
		assertEquals(164, estimator.getHealth());
		estimator.startStage(61, 3);
		assertEquals(183, estimator.getHealth());
	}

	@Test
	public void crystalAt99MatchesTheSoloRate()
	{
		assertEquals(BoulderHealthEstimator.SOLO_DAMAGE_PER_TICK, BoulderHealthEstimator.damagePerTick(CRYSTAL), 1e-9);
	}

	@Test
	public void slowerPickaxesDoLessDamage()
	{
		double crystal = BoulderHealthEstimator.damagePerTick(CRYSTAL);
		double dragon = BoulderHealthEstimator.damagePerTick(DRAGON);
		double rune = BoulderHealthEstimator.damagePerTick(RUNE);
		assertTrue(crystal > dragon);
		assertTrue(dragon > rune);
		assertEquals(crystal * 2.75 / 3, rune, 1e-9);
	}

	@Test
	public void miningTeammatesWearHealthDown()
	{
		BoulderHealthEstimator estimator = new BoulderHealthEstimator();
		estimator.startStage(41, 2);
		// One crystal teammate mining for 30 ticks ~= 10.4 damage.
		for (int i = 0; i < 30; i++)
		{
			estimator.onTick(1, BoulderHealthEstimator.damagePerTick(CRYSTAL));
		}
		assertEquals(82 - 10, estimator.getHealth());
	}

	@Test
	public void neverShowsZeroBeforeTheStageBreaks()
	{
		BoulderHealthEstimator estimator = new BoulderHealthEstimator();
		estimator.startStage(41, 2);
		for (int i = 0; i < 1000; i++)
		{
			estimator.onTick(3, 3 * BoulderHealthEstimator.damagePerTick(CRYSTAL));
		}
		assertEquals(1, estimator.getHealth());
	}

	@Test
	public void wellMeasuredStageSetsLevelMultiplierOutright()
	{
		BoulderHealthEstimator estimator = new BoulderHealthEstimator();
		estimator.startStage(41, 2);
		for (int i = 0; i < 41; i++)
		{
			estimator.onOwnSuccessfulMine();
		}
		// 150 ticks of a crystal teammate is ~52 expected damage; they really did 41.
		double perTick = BoulderHealthEstimator.damagePerTick(CRYSTAL);
		for (int i = 0; i < 150; i++)
		{
			estimator.onTick(1, perTick);
		}
		estimator.onStageBroken();

		assertEquals(41 / (150 * perTick), estimator.getLevelMultiplier(), 1e-9);
		assertEquals(0, estimator.getHealth());
	}

	@Test
	public void thinStageIsAveragedWithPreviousMultiplier()
	{
		BoulderHealthEstimator estimator = new BoulderHealthEstimator();
		estimator.startStage(41, 2);
		for (int i = 0; i < 72; i++)
		{
			estimator.onOwnSuccessfulMine();
		}
		// 30 ticks ~= 10.4 expected damage (between the averaging and full-trust thresholds); real 82 - 72 = 10.
		double perTick = BoulderHealthEstimator.damagePerTick(CRYSTAL);
		for (int i = 0; i < 30; i++)
		{
			estimator.onTick(1, perTick);
		}
		estimator.onStageBroken();

		double measured = 10 / (30 * perTick);
		assertEquals((1.0 + measured) / 2, estimator.getLevelMultiplier(), 1e-9);
	}

	@Test
	public void ignoresCalibrationWithTooLittleData()
	{
		BoulderHealthEstimator estimator = new BoulderHealthEstimator();
		estimator.startStage(41, 2);
		estimator.onTick(1, BoulderHealthEstimator.damagePerTick(CRYSTAL));
		estimator.onStageBroken();
		assertEquals(1.0, estimator.getLevelMultiplier(), 1e-9);
	}

	@Test
	public void newGameForgetsCalibration()
	{
		BoulderHealthEstimator estimator = new BoulderHealthEstimator();
		estimator.startStage(41, 2);
		for (int i = 0; i < 100; i++)
		{
			estimator.onTick(1, BoulderHealthEstimator.damagePerTick(CRYSTAL));
		}
		estimator.onStageBroken();
		assertFalse(estimator.getLevelMultiplier() == 1.0);

		estimator.resetForNewGame();
		assertEquals(1.0, estimator.getLevelMultiplier(), 1e-9);
	}

	@Test
	public void soloSecondsRemainingMatchesOldGuideFormula()
	{
		BoulderHealthEstimator estimator = new BoulderHealthEstimator();
		estimator.startStage(41, 1);
		assertEquals(71.01,
			estimator.estimateSecondsRemaining(BoulderHealthEstimator.damagePerTick(CRYSTAL), 0), 1e-6);
	}

	@Test
	public void teammatesShortenSecondsRemaining()
	{
		BoulderHealthEstimator estimator = new BoulderHealthEstimator();
		estimator.startStage(41, 2);
		double perTick = BoulderHealthEstimator.damagePerTick(CRYSTAL);
		// Two people at the same rate clear 82 health in the time one clears 41.
		assertEquals(71.01, estimator.estimateSecondsRemaining(perTick, perTick), 1e-6);
	}

	@Test
	public void fullStageTimeUsesEveryonesPickaxe()
	{
		BoulderHealthEstimator estimator = new BoulderHealthEstimator();
		double crystal = BoulderHealthEstimator.damagePerTick(CRYSTAL);
		double rune = BoulderHealthEstimator.damagePerTick(RUNE);
		// Solo crystal: the 71.01s reference time for a 41-health stage.
		assertEquals(71.01, estimator.secondsForHealth(41, crystal, 0), 1e-6);
		// Duo, teammate with a rune pickaxe: 82 health at crystal + rune rates.
		double duo = estimator.secondsForHealth(82, crystal, BoulderHealthOverlay.teamDamageAt99(1, java.util.Collections.singletonList(RUNE)));
		assertEquals(82 / (crystal + rune) * 0.6, duo, 1e-6);
		assertTrue(duo > 71.01);
	}

	@Test
	public void unseenTeammatesCountAsCrystal()
	{
		assertEquals(2 * BoulderHealthEstimator.SOLO_DAMAGE_PER_TICK,
			BoulderHealthOverlay.teamDamageAt99(2, java.util.Collections.emptyList()), 1e-9);
		assertEquals(BoulderHealthEstimator.damagePerTick(DRAGON) + BoulderHealthEstimator.SOLO_DAMAGE_PER_TICK,
			BoulderHealthOverlay.teamDamageAt99(2, java.util.Collections.singletonList(DRAGON)), 1e-9);
	}

	@Test
	public void unknownAnimationsAreNotMining()
	{
		assertFalse(PickaxeSpeeds.isMiningAnimation(-1));
		assertFalse(PickaxeSpeeds.isMiningAnimation(829));
		assertTrue(PickaxeSpeeds.isMiningAnimation(DRAGON));
	}
}
