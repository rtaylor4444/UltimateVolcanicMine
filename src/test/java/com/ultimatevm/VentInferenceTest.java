package com.ultimatevm;

import java.util.BitSet;
import java.util.List;
import java.util.Random;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class VentInferenceTest
{
	private static final int U = VentRules.UNKNOWN;

	/** A hidden game played with the real rules, fed to the inference one tick at a time. */
	private static final class Game
	{
		final Random random;
		final VentInference inference = new VentInference();
		int a, b, c, stability = 50, chamber;
		boolean checkA;
		double cancelChance;
		int flipAtRem = -1;
		int flipBit;
		// A one-off extra +1 on the stability update at this time (-1 = none).
		int plusOneAtRem = -1;
		// An earthquake on this tick (cancels a move/update there); sendMessage = chat shows it.
		int earthquakeAtRem = -1;
		boolean sendEarthquakeMessage;
		VentInference.EarthquakeEffect lastEffect;

		Game(long seed)
		{
			random = new Random(seed);
			a = 30 + random.nextInt(41);
			b = 30 + random.nextInt(41);
			c = 30 + random.nextInt(41);
			chamber = random.nextInt(8);
		}

		/** Plays from startRem down to endRem; returns true if the true vents were always possible. */
		boolean play(int startRem, int endRem)
		{
			boolean truthKept = true;
			for (int rem = startRem; rem >= endRem; rem--)
			{
				if (rem == flipAtRem)
				{
					chamber ^= flipBit;
				}
				// One earthquake roll per tick: it cancels the move and/or update due on that tick.
				boolean earthquake = rem % 15 == 0 && (random.nextDouble() < cancelChance || rem == earthquakeAtRem);
				if (rem < startRem && rem % 10 == 0 && rem != 500)
				{
					boolean cancelled = rem % 30 == 0 && earthquake;
					if (!cancelled)
					{
						int[] v = VentRules.move(a, b, c, chamber);
						a = v[0];
						b = v[1];
						c = v[2];
					}
				}
				if (rem < startRem && rem % 25 == 0)
				{
					boolean cancelled = rem % 75 == 0 && earthquake;
					if (!cancelled)
					{
						int extra = rem == plusOneAtRem ? 1 : 0;
						stability = Math.max(0, Math.min(100, stability + VentRules.stabilityChange(a, b, c) + extra));
					}
				}
				if (rem == 500)
				{
					a = 25 + random.nextInt(51);
					b = 25 + random.nextInt(51);
					c = 25 + random.nextInt(51);
				}
				if (rem == earthquakeAtRem && sendEarthquakeMessage)
				{
					inference.onEarthquakeMessage();
				}
				inference.onTick(rem, stability, new int[]{checkA ? a : U, U, U}, chamber, 1);
				VentInference.EarthquakeEffect effect = inference.takeEarthquakeEffect();
				if (effect != null)
				{
					lastEffect = effect;
				}
				if (rem > 50 && inference.getAllCount() > 0 && !contains(inference, a, b, c))
				{
					truthKept = false;
				}
			}
			return truthKept;
		}
	}

	private static boolean contains(VentInference inference, int a, int b, int c)
	{
		return contains(inference.getAllCandidates(), a, b, c);
	}

	private static boolean contains(List<int[]> candidates, int a, int b, int c)
	{
		for (int[] v : candidates)
		{
			if (v[0] == a && v[1] == b && v[2] == c)
			{
				return true;
			}
		}
		return false;
	}

	@Test
	public void keepsTheTruthAndNarrowsDownWithoutEarthquakes()
	{
		long totalLeft = 0;
		for (int seed = 0; seed < 20; seed++)
		{
			Game game = new Game(seed);
			assertTrue("seed " + seed, game.play(1000, 600));
			assertEquals("seed " + seed, 0, game.inference.getRestarts());
			totalLeft += game.inference.getCount();
		}
		// From 68,921 starting combinations down to a few hundred at most (it always allows for
		// earthquakes that could have cancelled a move, and stability stuck at 100 says little).
		assertTrue("average left " + totalLeft / 20, totalLeft / 20 < 1500);
	}

	@Test
	public void keepsTheTruthWithEarthquakeCancellations()
	{
		for (int seed = 0; seed < 20; seed++)
		{
			Game game = new Game(100 + seed);
			game.cancelChance = 0.3;
			assertTrue("seed " + seed, game.play(1000, 600));
			assertEquals("seed " + seed, 0, game.inference.getRestarts());
		}
	}

	@Test
	public void weightsAreAProbabilityAndFavourNoEarthquake()
	{
		Game game = new Game(11);
		game.checkA = true;
		assertTrue(game.play(1000, 960));
		double[] weights = game.inference.getWeights();
		double total = 0;
		for (double w : weights)
		{
			total += w;
		}
		assertEquals(1.0, total, 1e-9);
		// The true vents (no earthquakes in this game) should be at least as likely as an average combination.
		List<int[]> candidates = game.inference.getCandidates();
		for (int i = 0; i < candidates.size(); i++)
		{
			int[] v = candidates.get(i);
			if (v[0] == game.a && v[1] == game.b && v[2] == game.c)
			{
				assertTrue("truth weight " + weights[i], weights[i] >= 1.0 / candidates.size());
			}
		}
	}

	@Test
	public void aRarePlusOneDoesNotWipeOutTheTruth()
	{
		// The game showed one unexplained +1 once the vents were pinned down; the inference must
		// keep the truth instead of starting again from every value.
		for (int seed = 0; seed < 10; seed++)
		{
			Game game = new Game(400 + seed);
			game.checkA = true;
			game.plusOneAtRem = 700;
			assertTrue("seed " + seed, game.play(1000, 600));
			assertEquals("seed " + seed, 0, game.inference.getRestarts());
		}
	}

	@Test
	public void withoutSurprisesTheTruthIsInTheLikelySet()
	{
		for (int seed = 0; seed < 10; seed++)
		{
			Game game = new Game(500 + seed);
			game.checkA = true;
			game.play(1000, 600);
			assertTrue("seed " + seed, contains(game.inference.getCandidates(), game.a, game.b, game.c));
		}
	}

	@Test
	public void anEarthquakeMessageOnAVentMoveIsTakenAsFact()
	{
		int narrower = 0;
		for (int seed = 0; seed < 10; seed++)
		{
			Game told = new Game(600 + seed);
			told.earthquakeAtRem = 960;
			told.sendEarthquakeMessage = true;
			Game notTold = new Game(600 + seed);
			notTold.earthquakeAtRem = 960;
			assertTrue("seed " + seed, told.play(1000, 950));
			assertTrue("seed " + seed, notTold.play(1000, 950));
			assertEquals(0, told.inference.getRestarts());
			assertTrue(told.inference.getAllCount() <= notTold.inference.getAllCount());
			if (told.inference.getAllCount() < notTold.inference.getAllCount())
			{
				narrower++;
			}
			assertEquals(VentInference.EarthquakeEffect.VENT_MOVE_CANCELLED, told.lastEffect);
		}
		assertTrue("narrower in " + narrower + " games", narrower > 0);
	}

	@Test
	public void anUnseenEarthquakeOnASharedTickIsKept()
	{
		// rem 900 (1:00) has a move and an update due; an earthquake with no message cancels both.
		for (int seed = 0; seed < 10; seed++)
		{
			Game game = new Game(800 + seed);
			game.checkA = true;
			game.earthquakeAtRem = 900;
			assertTrue("seed " + seed, game.play(1000, 850));
			assertEquals("seed " + seed, 0, game.inference.getRestarts());
		}
	}

	@Test
	public void anEarthquakeMessageOnASharedTickCancelsBoth()
	{
		Game game = new Game(900);
		game.earthquakeAtRem = 900;
		game.sendEarthquakeMessage = true;
		assertTrue(game.play(1000, 850));
		assertEquals(0, game.inference.getRestarts());
		assertEquals(VentInference.EarthquakeEffect.BOTH_CANCELLED, game.lastEffect);
	}

	@Test
	public void anEarthquakeMessageOnAStabilityUpdateIsTakenAsFact()
	{
		Game game = new Game(700);
		game.earthquakeAtRem = 975;
		game.sendEarthquakeMessage = true;
		assertTrue(game.play(1000, 940));
		assertEquals(0, game.inference.getRestarts());
		assertEquals(VentInference.EarthquakeEffect.STABILITY_UPDATE_CANCELLED, game.lastEffect);
	}

	@Test
	public void followsAChamberFlip()
	{
		for (int seed = 0; seed < 10; seed++)
		{
			Game game = new Game(200 + seed);
			game.flipAtRem = 853;
			game.flipBit = 1 << (seed % 3);
			assertTrue("seed " + seed, game.play(1000, 600));
			assertEquals("seed " + seed, 0, game.inference.getRestarts());
		}
	}

	@Test
	public void startsAgainAfterTheFiveMinuteReset()
	{
		for (int seed = 0; seed < 10; seed++)
		{
			Game game = new Game(300 + seed);
			game.cancelChance = 0.2;
			assertTrue("seed " + seed, game.play(1000, 300));
			assertEquals("seed " + seed, 0, game.inference.getRestarts());
		}
	}

	@Test
	public void aCheckedVentNarrowsItAtOnce()
	{
		Game game = new Game(42);
		game.checkA = true;
		// Before the first vent move (at 990), so the other two vents are still 30-70 each.
		assertTrue(game.play(1000, 995));
		BitSet aValues = game.inference.possibleValues(0);
		assertEquals(1, aValues.cardinality());
		assertEquals(41 * 41, game.inference.getAllCount());
	}

	@Test
	public void joiningMidGameStillFindsTheVents()
	{
		Game game = new Game(7);
		game.play(1000, 801);
		// Carry on from 800 with an inference that has seen none of the history.
		game.inference.reset();
		assertTrue(game.play(800, 600));
		assertTrue(game.inference.getCount() > 0);
	}

	@Test
	public void formatsOneOrTwoRanges()
	{
		BitSet values = new BitSet();
		values.set(30, 34);
		values.set(67, 71);
		assertEquals("30-33% 67-70%", VentStatusOverlayOverride.formatValueRanges(values));
		BitSet single = new BitSet();
		single.set(44);
		assertEquals("44%", VentStatusOverlayOverride.formatValueRanges(single));
		BitSet scattered = new BitSet();
		scattered.set(10);
		scattered.set(20);
		scattered.set(30);
		assertEquals("10-30%", VentStatusOverlayOverride.formatValueRanges(scattered));
	}
}
