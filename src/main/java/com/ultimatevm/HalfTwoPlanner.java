package com.ultimatevm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The strategy's half-2 decisions, ported from the simulator (volcanicMine optStrategy.py play with
 * its defaults): B3 -> B rock -> one cap at the B chamber -> B4 (early B / C fixes at its stability
 * updates) -> rock -> set the B chamber -> check C, check B -> fix nothing / B / C / both (b5ev) ->
 * B5 -> caps at the B chamber -> East stairs, walking out early whenever more than 5% of the
 * still-possible vents would blow the mine up before the player is out.
 */
final class HalfTwoPlanner
{
	static final double TICK = 0.6;
	static final int GAME_END = 600;
	static final int LAST_UPDATE = 555;
	/** No capping after 9:30. */
	static final int CAP_CUTOFF = 570;
	static final int MAX_CAPS = 6;
	static final double EPS = 0.001;
	/** Walk out once more than this share of the still-possible vents would blow up before you're out. */
	static final double EXIT_RISK = 0.05;
	static final double EARLY_CONF_B = 0.80;
	static final double EARLY_CONF_C = 0.95;
	/** An early C fix also needs this predicted no-fix blow-up chance by the last update. */
	static final double NEED_RISK_C = 0.30;

	// Legs in ticks (optStrategy.OPT_LEGS).
	static final double B3_TO_ROCK = 9, ROCK_TAKE = 1, B_ROCK_FETCH = 3, ROCK_TO_B4 = 4, B4_TO_ROCK = 2, ROCK_TO_B_CHAMBER = 1;
	static final double B_CHAMBER_TO_C_VENT = 18, C_VENT_TO_B_VENT = 13, B_VENT_TO_B5 = 9;
	// B_CHAMBER_TO_B5: optStrategy.OPT_LEGS lists it twice (25, then 27); Python keeps the later 27.
	static final double B_VENT_TO_B_CHAMBER = 22, B_CHAMBER_TO_B5 = 27, B_VENT_TO_C_CHAMBER = 43, C_CHAMBER_TO_B5 = 42;
	static final double B_CHAMBER_TO_C_CHAMBER = 22, C_CHAMBER_TO_B5_AFTER_B = 38, C_CHAMBER_TO_C_VENT = 31;
	static final double B5_TO_ROCK = 26, CAP = 2.22, BLOCK = 1, UNBLOCK = 3.44, C_ROCK_ROUND_TRIP = 6, VENT_CHECK = 1;
	static final double B_CHAMBER_TO_E_STAIR = 6, B4_TO_E_STAIR = 6, B5_TO_E_STAIR = 31, CLIMB = 3, LOBBY_EXIT = 20;
	static final double B3_TO_B4_ROLL = 26, B4_TO_B5_ROLL = 32, B_CHAMBER_TO_B4 = 2;
	static final double B5_SECONDS = 105.65;

	static final double EXIT_NEAR_B_CHAMBER = exitSeconds(6);
	static final double EXIT_C_VENT_LEG = exitSeconds(24);
	static final double EXIT_B_VENT_LEG = exitSeconds(27);
	static final double EXIT_FAR = exitSeconds(31);

	// Expected-XP weighing of the fix choice after the vent checks.
	static final double XP_PER_SECOND = 93_000.0 / 3600;
	static final double PER_POINT = 3.8 * 1.025;
	static final double B5_VALUE = 2215.36 + 1125.01 * PER_POINT;
	static final double FINISH_POINTS = 100, CAP_POINTS = 50;

	private HalfTwoPlanner()
	{
	}

	static double exitSeconds(double ticksToStairs)
	{
		return (ticksToStairs + CLIMB + LOBBY_EXIT) * TICK;
	}

	/**
	 * The latest moment (game seconds) to start walking out from somewhere exitSeconds from being
	 * out: before more than 5% of the still-possible vents blow the mine up, and before 10:00.
	 */
	static double leaveTime(StrategyPredictor predictor, double exitSeconds)
	{
		return leaveTime(predictor, exitSeconds, java.util.Collections.emptyList());
	}

	// Walks from B4 to each chamber for a fix during B4 (optStrategy.OPT_LEGS).
	static final double B4_TO_B_CHAMBER_TICKS = 2, B4_TO_C_CHAMBER_TICKS = 22, B4_TO_A_CHAMBER_TICKS = 34;

	/** As {@link #leaveTime(StrategyPredictor, double)}, with these fixes assumed to land. */
	static double leaveTime(StrategyPredictor predictor, double exitSeconds, List<Simulator.FlipEvent> fixes)
	{
		double latest = GAME_END - exitSeconds - EPS;
		if (predictor == null || predictor.time > LAST_UPDATE)
		{
			return latest;
		}
		Integer death = predictor.deathTime(LAST_UPDATE, EXIT_RISK, fixes);
		return death == null ? latest : Math.min(latest, death - exitSeconds - EPS);
	}

	/** Vents to fix now during B4 (B first, then C), each at most once. */
	static List<Integer> earlyFixesDuringB4(StrategyPredictor predictor, boolean bDone, boolean cDone)
	{
		List<Integer> out = new ArrayList<>();
		if (!bDone && predictor.pHeadingAway(1) >= EARLY_CONF_B)
		{
			out.add(1);
		}
		if (!cDone && predictor.pHeadingAway(2) >= EARLY_CONF_C && predictor.deathRate(LAST_UPDATE) >= NEED_RISK_C)
		{
			out.add(2);
		}
		return out;
	}

	/** Leaving the B chamber after B4: flip B if it's more likely than not heading away from 50. */
	static boolean flipBAfterB4(StrategyPredictor predictor)
	{
		return predictor.pHeadingAway(1) > 0.5;
	}

	/** After B4, fix C on the way to the C vent when confidently heading away and needed. */
	static boolean fixCAfterB4(StrategyPredictor predictor, boolean cDone)
	{
		return !cDone && predictor.pHeadingAway(2) >= EARLY_CONF_C && predictor.deathRate(LAST_UPDATE) >= NEED_RISK_C;
	}

	/**
	 * B5 heading for a finish too late to cap after it (no capping after 9:30; the caps on the way out
	 * come ~17 s after B5): cap at the B chamber now instead, in pairs.
	 */
	static boolean capNowBecauseB5FinishesLate(double now, double b4Done)
	{
		double b5Done = Math.max(b4Done + B4_TO_B5_ROLL * TICK,
			now + (B_CHAMBER_TO_C_VENT + C_VENT_TO_B_VENT + B_VENT_TO_B5 + 2 * VENT_CHECK) * TICK) + B5_SECONDS;
		double atChamber = b5Done + (B5_TO_ROCK + ROCK_TAKE + ROCK_TO_B_CHAMBER) * TICK;
		return atChamber + BLOCK * TICK > CAP_CUTOFF;
	}

	/**
	 * Coach-only rule: skip the C and B vent checks and go straight to B5 when, with them, B5 couldn't
	 * be finished with you out by 10:00 but without them it could, and fewer than 5% of the possible
	 * vents blow the mine up by the last update (the checks would change nothing worth the finish).
	 */
	static boolean skipChecksForB5(StrategyPredictor predictor, double now, double b4Done, boolean cFixedOnTheWay)
	{
		double b5Ready = b4Done + B4_TO_B5_ROLL * TICK;
		double checked = (cFixedOnTheWay ? C_CHAMBER_TO_C_VENT : B_CHAMBER_TO_C_VENT) + VENT_CHECK + C_VENT_TO_B_VENT + VENT_CHECK + B_VENT_TO_B5;
		double direct = cFixedOnTheWay ? 48 : B_CHAMBER_TO_B5;
		double outChecked = Math.max(now + checked * TICK, b5Ready) + B5_SECONDS + EXIT_FAR;
		double outDirect = Math.max(now + direct * TICK, b5Ready) + B5_SECONDS + EXIT_FAR;
		return outChecked > GAME_END && outDirect <= GAME_END && predictor.deathRate(LAST_UPDATE) < EXIT_RISK;
	}

	/**
	 * How many caps fit at the B chamber while B4 rolls in (26 ticks after B3 breaks, then 2 ticks to
	 * B4), starting from the rock (1 tick to the chamber): unblock 3.44 ticks, block 1 (uses a rock),
	 * up to the 6 paid caps (optStrategy b4_window_caps "max").
	 */
	static int windowCaps(double now, double b3Done, boolean blocked, int rocks, int capsMade)
	{
		double deadline = b3Done + (B3_TO_B4_ROLL - B_CHAMBER_TO_B4) * TICK;
		double t = now + ROCK_TO_B_CHAMBER * TICK;
		int made = 0;
		while (capsMade + made < MAX_CAPS)
		{
			if (!blocked && rocks == 0)
			{
				break;
			}
			double ticks = blocked ? UNBLOCK : BLOCK;
			if (t + ticks * TICK > deadline + 1e-9)
			{
				break;
			}
			t += ticks * TICK;
			if (!blocked)
			{
				rocks--;
			}
			blocked = !blocked;
			made++;
		}
		return made;
	}

	/** Ticks to fix a chamber standing at it: unblock, or block (fetching its rock if none in hand). */
	static double fixTicks(int vent, int chamberStatus, boolean rockInHand)
	{
		if ((chamberStatus & (1 << vent)) != 0)
		{
			return UNBLOCK;
		}
		double rock = rockInHand ? 0 : (vent == 2 ? C_ROCK_ROUND_TRIP : B_ROCK_FETCH);
		return BLOCK + rock;
	}

	/** The fix choice after checking both vents: which chambers to fix on the way to B5. */
	enum B5Option
	{
		NONE("Go to B5"),
		B("Fix B, then B5"),
		C("Fix C, then B5"),
		BC("Fix B and C, then B5");

		final String label;

		B5Option(String label)
		{
			this.label = label;
		}

		int[] vents()
		{
			switch (this)
			{
				case B: return new int[]{1};
				case C: return new int[]{2};
				case BC: return new int[]{1, 2};
				default: return new int[0];
			}
		}
	}

	/**
	 * Picks the option with the best expected XP minus the long-run rate times its time (b5ev): for
	 * each, the chance the mine would blow up at each update (with its fixes) decides how much of
	 * B5 is mined before walking out.
	 */
	static B5Option chooseAtBVent(StrategyPredictor predictor, double now, double b4Done, boolean rockInHand, int capsMade)
	{
		double fb = fixTicks(1, predictor.chamberStatus, rockInHand);
		double fc = fixTicks(2, predictor.chamberStatus, rockInHand);
		double b5Arrival = Math.max(b4Done + B4_TO_B5_ROLL * TICK, now);
		double b5Need = B5_SECONDS + exitSeconds(B5_TO_E_STAIR);
		B5Option best = null;
		double bestScore = Double.NEGATIVE_INFINITY;
		for (B5Option option : B5Option.values())
		{
			double toB5;
			double[][] fixes;
			switch (option)
			{
				case B:
					fixes = new double[][]{{B_VENT_TO_B_CHAMBER + fb, 1}};
					toB5 = B_VENT_TO_B_CHAMBER + fb + B_CHAMBER_TO_B5;
					break;
				case C:
					fixes = new double[][]{{B_VENT_TO_C_CHAMBER + fc, 2}};
					toB5 = B_VENT_TO_C_CHAMBER + fc + C_CHAMBER_TO_B5;
					break;
				case BC:
					fixes = new double[][]{{B_VENT_TO_B_CHAMBER + fb, 1}, {B_VENT_TO_B_CHAMBER + fb + B_CHAMBER_TO_C_CHAMBER + fc, 2}};
					toB5 = B_VENT_TO_B_CHAMBER + fb + B_CHAMBER_TO_C_CHAMBER + fc + C_CHAMBER_TO_B5_AFTER_B;
					break;
				default:
					fixes = new double[0][];
					toB5 = B_VENT_TO_B5;
					break;
			}
			boolean feasible = Math.max(b5Arrival, now + toB5 * TICK) + b5Need <= GAME_END;
			if (!feasible && option != B5Option.NONE)
			{
				continue;
			}
			int capsLeft = Math.max(0, MAX_CAPS - capsMade - fixes.length);
			double score = b5OptionValue(predictor, now, fixes, toB5, b5Arrival, capsLeft);
			// Ties (within rounding) go to the earlier option, as in the simulator's ordering.
			if (score > bestScore + 1e-6)
			{
				best = option;
				bestScore = score;
			}
		}
		return best == null ? B5Option.NONE : best;
	}

	static double b5OptionValue(StrategyPredictor predictor, double now, double[][] fixes, double toB5Ticks, double b5ReadyAt,
		int capsLeft)
	{
		double arrive = Math.max(b5ReadyAt, now + toB5Ticks * TICK);
		List<Simulator.FlipEvent> flips = new ArrayList<>();
		for (double[] fix : fixes)
		{
			flips.add(new Simulator.FlipEvent((int) Math.ceil(now + fix[0] * TICK - 1e-9), 1 << (int) fix[1]));
		}
		double[] cumulative = predictor.cumulativeDeathByUpdate(flips, LAST_UPDATE);
		int firstUpdate = StrategyPredictor.firstUpdateAtOrAfter(predictor.time);
		double total = 0;
		double blown = 0;
		for (int i = 0; i < cumulative.length; i++)
		{
			int t = firstUpdate + 15 * i;
			double p = cumulative[i] - blown;
			if (p > 1e-12)
			{
				double[] outcome = b5Outcome(now, arrive, Math.min(t, GAME_END) - EXIT_FAR, capsLeft);
				total += p * (outcome[0] - XP_PER_SECOND * (outcome[1] - now));
				blown = cumulative[i];
			}
		}
		double[] outcome = b5Outcome(now, arrive, GAME_END - EXIT_FAR, capsLeft);
		total += (1 - blown) * (outcome[0] - XP_PER_SECOND * (outcome[1] - now));
		return total;
	}

	/** {XP, time out of the mine} mining B5 from arrive until leaveBy at the latest. */
	private static double[] b5Outcome(double now, double arrive, double leaveBy, int capsLeft)
	{
		double mined = Math.min(B5_SECONDS, Math.max(0, leaveBy - arrive));
		if (mined >= B5_SECONDS - 1e-9)
		{
			int caps = Math.min(capsLeft, 3);
			double extra = (FINISH_POINTS + caps * CAP_POINTS) * PER_POINT;
			double out = arrive + B5_SECONDS + (B5_TO_ROCK + ROCK_TAKE + ROCK_TO_B_CHAMBER + caps * CAP) * TICK + EXIT_NEAR_B_CHAMBER;
			return new double[]{B5_VALUE + extra, out};
		}
		return new double[]{B5_VALUE * mined / B5_SECONDS, Math.max(now, Math.min(leaveBy, arrive + B5_SECONDS)) + EXIT_FAR};
	}

	/** After B5: whether to go cap at the B chamber on the way out. */
	static boolean capAfterB5(StrategyPredictor predictor, double now, int capsMade)
	{
		if (now + (B5_TO_ROCK + ROCK_TAKE + ROCK_TO_B_CHAMBER + BLOCK) * TICK > CAP_CUTOFF || capsMade >= MAX_CAPS)
		{
			return false;
		}
		return now < leaveTime(predictor, exitSeconds(B5_TO_ROCK + ROCK_TAKE + ROCK_TO_B_CHAMBER + B_CHAMBER_TO_E_STAIR));
	}

	/** At the B chamber at the end: whether one more cap fits (paid caps, 9:30, the predictor). */
	static boolean anotherEndCap(StrategyPredictor predictor, double now, int capsMade, boolean blocked, boolean rockInHand)
	{
		if (capsMade >= MAX_CAPS || (!blocked && !rockInHand))
		{
			return false;
		}
		double ticks = blocked ? UNBLOCK : BLOCK;
		if (now + ticks * TICK > CAP_CUTOFF + 1e-9)
		{
			return false;
		}
		return now < leaveTime(predictor, exitSeconds(B_CHAMBER_TO_E_STAIR) + ticks * TICK);
	}

	static List<Integer> none()
	{
		return Collections.emptyList();
	}
}
