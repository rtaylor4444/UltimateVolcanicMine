package com.ultimatevm;

import java.util.Random;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Whole solo games with the real vent rules, random earthquakes and the plugin's vent inference, and
 * a player who does whatever the coach says (each step takes a fixed walk). Checks the coach never
 * gets stuck and keeps the player alive.
 */
public class CoachGameTest
{
	private static final int U = VentRules.UNKNOWN;
	private static final int WALK_TICKS = 12;
	private static final int STAGE_TICKS = 118;
	private static final int B5_TICKS = 176;
	private static final int ROLL_TICKS = 26;

	static final class Outcome
	{
		boolean died;
		boolean teleported;
		boolean left;
		int boulders;
		int caps;
		int checks;
		int fixes;
		String last;
	}

	static Outcome play(long seed)
	{
		return play(seed, null);
	}

	/** When set, games are recorded in the plugin's coach-trace format (tick/rem added here). */
	static StrategyCoach.TraceSink recorder;
	private static final int[] clock = new int[2];

	private static void rec(String type, Object... keyValues)
	{
		if (recorder == null)
		{
			return;
		}
		java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
		m.put("tick", clock[0]);
		m.put("rem", clock[1]);
		for (int i = 0; i + 1 < keyValues.length; i += 2)
		{
			m.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
		}
		recorder.record(type, m);
	}

	static Outcome play(long seed, java.util.concurrent.Executor background)
	{
		Random random = new Random(seed);
		int a = 30 + random.nextInt(41), b = 30 + random.nextInt(41), c = 30 + random.nextInt(41);
		int stability = 50, chamber = 0;
		boolean[] checked = new boolean[3];
		VentInference inference = new VentInference();
		final int[] chamberRef = {0};
		StrategyCoach coach = new StrategyCoach(o ->
		{
			int n = inference.getCount();
			// The plugin's limit is 200,000 with the heavy work in the background; here (synchronous)
			// half 1 keeps to 10,000 so the games run quickly - only the first check-ins differ.
			return n == 0 || n > (o.now < 300 ? 10000 : 200000) ? null
				: new StrategyPredictor(inference.getCandidates(), inference.getWeights(), o.chamberStatus, o.stability, o.startSecond);
		});
		coach.setBackground(background);
		TraceExpander expander = new TraceExpander();
		if (recorder != null)
		{
			coach.setTrace((type, fields) ->
			{
				java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
				m.put("tick", clock[0]);
				m.put("rem", clock[1]);
				m.putAll(fields);
				recorder.record(type, expander.expand(m));
			});
		}
		Outcome out = new Outcome();
		int points = 0;
		int step = 0;
		double[] xpLeft = {0};
		int boulder = 1;
		int boulderReadyAt = 1000 - 20;    // rem when the next boulder can be mined
		int mineLeft = STAGE_TICKS;
		int walk = WALK_TICKS;             // ticks left walking to the current step
		String stepKey = null;
		int rocks = 0;
		int caps = 0;
		StrategyCoach.Observation previous = null;
		rec("start", "format", "uvm-coach-1", "players", 1, "miningLevel", 99, "seed", seed);
		for (int rem = 1000; rem >= 0; rem--)
		{
			clock[0] = 1000 - rem;
			clock[1] = rem;
			// Environment: one earthquake roll per tick, then the move and update due.
			boolean quake = rem % 15 == 0 && rem < 1000 && random.nextDouble() < 16.0 / 89;
			if (rem < 1000 && rem % 10 == 0 && rem != 500 && !(rem % 30 == 0 && quake))
			{
				int[] v = VentRules.move(a, b, c, chamber);
				a = v[0];
				b = v[1];
				c = v[2];
			}
			if (rem < 1000 && rem % 25 == 0 && rem > 50 && !(rem % 75 == 0 && quake))
			{
				stability = VentRules.clampStability(stability + VentRules.stabilityChange(a, b, c));
				if (stability <= 0 && rem > 75)
				{
					out.died = true;
					out.last = "died at rem " + rem;
					return out;
				}
			}
			if (rem == 500)
			{
				a = 25 + random.nextInt(51);
				b = 25 + random.nextInt(51);
				c = 25 + random.nextInt(51);
				checked = new boolean[3];
			}
			int[] raw = {checked[0] ? a : U, checked[1] ? b : U, checked[2] ? c : U};
			inference.onTick(rem, stability, raw, chamber, 1);
			if (quake)
			{
				inference.takeEarthquakeEffect();
			}

			StrategyCoach.Observation o = new StrategyCoach.Observation();
			o.now = (1000 - rem) * 0.6;
			o.startSecond = (1000 - Math.max(0, rem)) * 3 / 5 + 1;
			o.stability = stability;
			o.chamberStatus = chamber;
			o.rawVents = raw;
			o.boulderNumber = boulder;
			o.rocksInHand = rocks;
			o.capsMade = caps;
			o.stabilityUpdate = rem % 25 == 24;
			if (background != null)
			{
				// A tick is 0.6 s: background work started last tick is done by now in game.
				long deadline = System.currentTimeMillis() + 3000;
				while (coach.isWorking() && System.currentTimeMillis() < deadline)
				{
					try
					{
						Thread.sleep(1);
					}
					catch (InterruptedException e)
					{
						Thread.currentThread().interrupt();
						break;
					}
				}
			}
			StrategyCoach.Instruction i = coach.update(o);
			o.atBoulder = i.action == CoachAction.MINE_BOULDER && walk <= 0 && rem <= boulderReadyAt;
			i = coach.update(o);
			previous = o;
			out.last = i.text;
			rec("tick", "now", o.now, "dx", 0, "dy", 0, "stab", stability, "chamber", chamber, "vents", raw, "boulder", boulder,
				"health", mineLeft, "atBoulder", o.atBoulder, "rocks", rocks, "caps", caps, "step", step);

			String key = i.action + ":" + i.spot + ":" + i.text.replaceAll("\\(\\d/6\\)", "");
			if (!key.equals(stepKey))
			{
				stepKey = key;
				step++;
				rec("step", "step", step, "action", String.valueOf(i.action), "spot", String.valueOf(i.spot), "text", i.text,
					"detail", i.detail, "urgent", i.urgent);
				walk = i.action == CoachAction.MINE_BOULDER && boulder > 1 ? WALK_TICKS : WALK_TICKS;
			}
			if (walk > 0)
			{
				walk--;
				continue;
			}
			switch (i.action)
			{
				case LEAVE:
					out.left = true;
					out.caps = caps;
					rec("end", "points", points, "caps", caps);
					return out;
				case TELEPORT:
					out.teleported = true;
					return out;
				case MINE_BOULDER:
					if (rem > boulderReadyAt)
					{
						break;
					}
					if (recorder != null && mineLeft % 4 == 0)
					{
						// An XP drop and the boulder's points every 4 ticks, adding up to the simulator's averages.
						int total = boulder == 5 ? B5_TICKS : STAGE_TICKS;
						double[][] avg = {{401.96, 148.27}, {529.85, 219.79}, {689.71, 309.18}, {977.46, 470.09}, {2215.36, 1125.01}};
						int xp = (int) Math.round(avg[boulder - 1][0] * 4 / total);
						int pts = (int) Math.round(avg[boulder - 1][1] * 4 / total);
						points += pts;
						rec("xp", "delta", xp, "boulder", boulder);
						rec("points", "delta", pts, "total", points);
					}
					if (--mineLeft <= 0)
					{
						rec("boulder_broken", "boulder", boulder);
						if (boulder == 5)
						{
							points += 100;
							rec("points", "delta", 100, "total", points);
						}
						coach.onBoulderBroken(boulder, o);
						out.boulders = boulder;
						if (boulder == 5)
						{
							break;
						}
						boulder++;
						mineLeft = boulder == 5 ? B5_TICKS : STAGE_TICKS;
						boulderReadyAt = rem - ROLL_TICKS;
						walk = WALK_TICKS;
					}
					break;
				case CHECK_VENT:
				{
					int vent = i.spot.name().contains("_A_") ? 0 : i.spot.name().contains("_B_") ? 1 : 2;
					checked[vent] = true;
					out.checks++;
					points += 50;
					rec("points", "delta", 50, "total", points);
					break;
				}
				case TAKE_ROCK:
					rocks++;
					break;
				case FIX_CHAMBER:
				case CAP_CHAMBER:
				{
					int bit = i.spot == MineLayout.Spot.CHAMBER_A ? 1 : i.spot == MineLayout.Spot.CHAMBER_B ? 2 : 4;
					if ((chamber & bit) == 0)
					{
						rocks--;
					}
					rec("chamber", "from", chamber, "to", chamber ^ bit);
					chamber ^= bit;
					caps++;
					if (caps <= 6)
					{
						points += 50;
						rec("points", "delta", 50, "total", points);
					}
					if (i.action == CoachAction.FIX_CHAMBER)
					{
						out.fixes++;
					}
					break;
				}
				default:
					break;
			}
			chamberRef[0] = chamber;
		}
		out.last = "clock ran out: " + out.last;
		return out;
	}

	@Test
	public void thePlayerAlwaysGetsOutAlive()
	{
		// More with: gradlew test -DcoachGames=120 (about 2 s a game).
		int games = Integer.getInteger("coachGames", 20), died = 0, teleported = 0, b5 = 0, left = 0, caps = 0, fixes = 0;
		StringBuilder problems = new StringBuilder();
		for (int seed = 0; seed < games; seed++)
		{
			Outcome out = play(seed);
			if (out.died)
			{
				died++;
				problems.append("seed ").append(seed).append(": ").append(out.last).append('\n');
			}
			if (!out.died && !out.left && !out.teleported)
			{
				problems.append("seed ").append(seed).append(" stuck: ").append(out.last).append('\n');
			}
			teleported += out.teleported ? 1 : 0;
			left += out.left ? 1 : 0;
			b5 += out.boulders == 5 ? 1 : 0;
			caps += out.caps;
			fixes += out.fixes;
		}
		System.out.printf("coach games: %d left, %d teleported, %d died, B5 done %d, caps/game %.1f, fixes/game %.1f%n",
			left, teleported, died, b5, caps / (double) games, fixes / (double) games);
		System.out.print(problems);
		assertEquals("no game ends with the player stuck", 0, problems.toString().split("stuck").length - 1);
		assertTrue("deaths " + died, died <= games / 20);
		assertTrue("most games finish B5: " + b5, b5 > games / 2);
	}

	@Test
	public void decisionsOnABackgroundThreadStillGetEveryoneOut()
	{
		java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor();
		try
		{
			for (int seed = 0; seed < Integer.getInteger("coachBackgroundGames", 6); seed++)
			{
				Outcome out = play(1000 + seed, executor);
				assertTrue("seed " + seed + ": " + out.last, !out.died && (out.left || out.teleported));
			}
		}
		finally
		{
			executor.shutdownNow();
		}
	}
}
