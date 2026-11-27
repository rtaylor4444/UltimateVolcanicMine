package com.ultimatevm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The strategy's half-1 fix choice, ported from the simulator (volcanicMine decisionEngine.py
 * choose_half1_strategy with risk per second 0.001, utils._half1_decision_schedule, rules.py route
 * timings from map/chain_costs.py, B1 mined from its north-west corner).
 *
 * <p>At each checkpoint every fix plan whose window is still open is tried cheapest first; the
 * cheapest plan with a 0% predicted blow-up before 5:00 is the "safe" plan, and a cheaper plan wins
 * instead if its blow-up chance is at most 0.001 x the seconds it saves. No safe plan and nothing
 * cheap enough: teleport (only at the last checkpoint, or when the mine could blow up before the
 * next one).
 */
final class HalfOnePlanner
{
	static final double TICK = 0.6;
	static final int RESET_TIME = 300;
	static final double RISK_PER_SECOND = 0.001;
	static final double TELEPORT_SECONDS = 120;
	/** A vent check's 50 points in seconds of play (routes that include one rank that much cheaper). */
	static final double CHECK_CREDIT_SECONDS = 50 * 3.8 * 1.025 / (96_400.0 / 3600);
	static final double STAGE_SECONDS = 71.01;
	static final double B1_TO_B2_TICKS = 15;
	static final double B2_TO_B3_TICKS = 18;
	/** B check as B1 breaks: the detour from B1's north-west corner (B3 starts 16 ticks later). */
	static final double B_CHECK_DETOUR_TICKS = 16;
	/** The B check is made as B1 breaks if the no-fix blow-up chance before 5:00 is at least this. */
	static final double B_CHECK_RISK = 0.50;

	// Route timings in ticks (rules.py, west side). *_TTF: ticks until the fix lands; *_END: what the
	// leg costs in place of the usual leg (after-B1 legs replace the 15-tick B1 -> B2, after-B2 the
	// 18-tick B2 -> B3; "now" detours while mining are pure extra).
	static final double A_VENT_CHECK_FROM_B1 = 14;
	static final double A_AFTER_B1_TTF = 34.2, A_AFTER_B1_END = 70.7;
	static final double B_AFTER_B1_TTF = 39.2, B_AFTER_B1_END = 66.2;
	static final double AB_AFTER_B1_B_TTF = 75.9, AB_AFTER_B1_END = 102.9;
	static final double BC_AFTER_B1_CHECK = 18, BC_AFTER_B1_C_TTF = 41, BC_AFTER_B1_B_TTF = 70.4, BC_AFTER_B1_END = 97.4;
	static final double BA_B_TTF = 39.2, BA_A_TTF = 78.4, BA_AFTER_B1_END = 101.9;
	static final double A_RETURN_B1_TTF = 31.2, A_RETURN_B1_END = 51.7;
	static final double B_RETURN_B1_TTF = 35.2, B_RETURN_B1_END = 61.2;
	static final double C_RETURN_B1_CHECK = 33, C_RETURN_B1_TTF = 56, C_RETURN_B1_END = 75.2;
	static final double AB_RETURN_B1_A_TTF = 31.2, AB_RETURN_B1_B_TTF = 62.9, AB_RETURN_B1_END = 88.9;
	static final double BA_RETURN_B1_B_TTF = 35.2, BA_RETURN_B1_A_TTF = 74.4, BA_RETURN_B1_END = 98.9;
	static final double CB_RETURN_B1_C_TTF = 50, CB_RETURN_B1_B_TTF = 68.4, CB_RETURN_B1_END = 94.4;
	static final double A_AFTER_B2_TTF = 51.2, A_AFTER_B2_END = 76.7;
	static final double B_AFTER_B2_TTF = 31.2, B_AFTER_B2_END = 43.2;
	static final double AB_AFTER_B2_B_TTF = 82.9, AB_AFTER_B2_END = 94.9;
	static final double C_AFTER_B1_CHECK = 18, C_AFTER_B1_TTF = 41, C_AFTER_B1_END = 15 + 57.2;
	static final double C_AFTER_B2_TTF = 40, C_AFTER_B2_END = 18 + 35.2;
	// While mining B2 (from B2's north side and back): {ticks until the fix lands, ticks until back}.
	static final double[] A_RETURN_B2 = {25 + 4.2, 25 + 6.72 + 27};
	static final double[] B_RETURN_B2 = {22 + 4.2, 22 + 5.22 + 24};
	static final double[] C_RETURN_B2 = {28 + 7, 28 + 9.22 + 30};

	/** When a plan's first step happens. */
	enum Window
	{
		/** A detour from B1 while mining it, back to B1. */
		NOW_B1,
		/** On the way from B1 to B2. */
		AFTER_B1,
		/** A detour from B2 while mining it, back to B2. */
		NOW_B2,
		/** On the way from B2 to B3. */
		AFTER_B2,
		NONE
	}

	/** Where the player is in half 1 when deciding (the simulator's checkpoints). */
	enum Checkpoint
	{
		/** Arrived at B1, or at a stability update while mining it. */
		MINING_B1(0),
		/** B1 just broke (after the B check if one was made). */
		B1_BROKEN(1),
		/** Arrived at B2. */
		AT_B2(1),
		/** At a stability update while mining B2. */
		MINING_B2(1),
		/** B2 just broke. */
		B2_BROKEN(2),
		/** Arrived at B3: no fix windows left, only a teleport. */
		AT_B3(2);

		final int bouldersCompleted;

		Checkpoint(int bouldersCompleted)
		{
			this.bouldersCompleted = bouldersCompleted;
		}
	}

	/** One step of a plan, in order. */
	static final class Step
	{
		final CoachAction action;
		final int vent;

		Step(CoachAction action, int vent)
		{
			this.action = action;
			this.vent = vent;
		}

		@Override
		public String toString()
		{
			return action + " " + "ABC".charAt(vent);
		}
	}

	enum Plan
	{
		NONE("No fix", Window.NONE, 0),
		FIX_A_AFTER_B2("Fix A after B2", Window.AFTER_B2, (A_AFTER_B2_END - B2_TO_B3_TICKS) * TICK, fix(0)),
		FIX_C_AFTER_B1("Fix C after B1", Window.AFTER_B1, (C_AFTER_B1_END - B1_TO_B2_TICKS) * TICK - CHECK_CREDIT_SECONDS, check(2), fix(2)),
		FIX_C_AFTER_B2("Fix C after B2", Window.AFTER_B2, (C_AFTER_B2_END - B2_TO_B3_TICKS) * TICK, fix(2)),
		FIX_B_AFTER_B2("Fix B after B2", Window.AFTER_B2, (B_AFTER_B2_END - B2_TO_B3_TICKS) * TICK, fix(1)),
		FIX_A_AFTER_B1("Fix A after B1", Window.AFTER_B1, (A_AFTER_B1_END - B1_TO_B2_TICKS) * TICK - CHECK_CREDIT_SECONDS, check(0), fix(0)),
		FIX_A_NOW_B1("Fix A now", Window.NOW_B1, A_RETURN_B1_END * TICK, fix(0)),
		FIX_B_AFTER_B1("Fix B after B1", Window.AFTER_B1, (B_AFTER_B1_END - B1_TO_B2_TICKS) * TICK, fix(1)),
		FIX_AB_AFTER_B2("Fix A+B after B2", Window.AFTER_B2, (AB_AFTER_B2_END - B2_TO_B3_TICKS) * TICK, fix(0), fix(1)),
		FIX_B_NOW_B1("Fix B now", Window.NOW_B1, B_RETURN_B1_END * TICK, fix(1)),
		FIX_C_NOW_B1("Fix C now", Window.NOW_B1, C_RETURN_B1_END * TICK - CHECK_CREDIT_SECONDS, check(2), fix(2)),
		FIX_AB_AFTER_B1("Fix A+B after B1", Window.AFTER_B1, (AB_AFTER_B1_END - B1_TO_B2_TICKS) * TICK - CHECK_CREDIT_SECONDS, check(0), fix(0), fix(1)),
		FIX_BC_AFTER_B1("Fix C+B after B1", Window.AFTER_B1, (BC_AFTER_B1_END - B1_TO_B2_TICKS) * TICK - CHECK_CREDIT_SECONDS, check(2), fix(2), fix(1)),
		FIX_CB_NOW_B1("Fix C+B now", Window.NOW_B1, CB_RETURN_B1_END * TICK, fix(2), fix(1)),
		FIX_AB_NOW_B1("Fix A+B now", Window.NOW_B1, AB_RETURN_B1_END * TICK, fix(0), fix(1)),
		FIX_BA_AFTER_B1("Fix B+A after B1", Window.AFTER_B1, (BA_AFTER_B1_END - B1_TO_B2_TICKS) * TICK, fix(1), fix(0)),
		FIX_BA_NOW_B1("Fix B+A now", Window.NOW_B1, BA_RETURN_B1_END * TICK, fix(1), fix(0)),
		FIX_A_NOW_B2("Fix A now", Window.NOW_B2, A_RETURN_B2[1] * TICK, fix(0)),
		FIX_B_NOW_B2("Fix B now", Window.NOW_B2, B_RETURN_B2[1] * TICK, fix(1)),
		FIX_C_NOW_B2("Fix C now", Window.NOW_B2, C_RETURN_B2[1] * TICK, fix(2)),
		TELEPORT("Teleport out", Window.NONE, Double.NaN);

		final String label;
		final Window window;
		final double costSeconds;
		final Step[] steps;

		Plan(String label, Window window, double costSeconds, Step... steps)
		{
			this.label = label;
			this.window = window;
			this.costSeconds = costSeconds;
			this.steps = steps;
		}
	}

	private static Step fix(int vent)
	{
		return new Step(CoachAction.FIX_CHAMBER, vent);
	}

	private static Step check(int vent)
	{
		return new Step(CoachAction.CHECK_VENT, vent);
	}

	/** The decision at a checkpoint. */
	static final class Decision
	{
		final Plan plan;
		/** The plan's predicted blow-up chance before 5:00 (NaN for a teleport). */
		final double deathRate;

		Decision(Plan plan, double deathRate)
		{
			this.plan = plan;
			this.deathRate = deathRate;
		}

		/** True when the plan's first step happens right now (its window is this checkpoint's). */
		boolean startsNow(Checkpoint checkpoint)
		{
			switch (plan.window)
			{
				case NOW_B1:
					return checkpoint == Checkpoint.MINING_B1;
				case AFTER_B1:
					return checkpoint == Checkpoint.B1_BROKEN;
				case NOW_B2:
					return checkpoint == Checkpoint.MINING_B2;
				case AFTER_B2:
					return checkpoint == Checkpoint.B2_BROKEN;
				default:
					return false;
			}
		}

		@Override
		public String toString()
		{
			return plan + (Double.isNaN(deathRate) ? "" : String.format(java.util.Locale.ROOT, " (%.2f%%)", deathRate * 100));
		}
	}

	private HalfOnePlanner()
	{
	}

	/**
	 * @param now                   game seconds elapsed (the simulator's current tick)
	 * @param miningRemainingSeconds seconds left on the boulder being mined (MINING_B1 / MINING_B2), or
	 *                              a negative number for a whole stage
	 * @param whileMining           a stability-update checkpoint while mining (the fix-now detours are
	 *                              only offered then)
	 */
	static Decision decide(StrategyPredictor predictor, Checkpoint checkpoint, double now, double miningRemainingSeconds,
		boolean whileMining)
	{
		List<Plan> plans = new ArrayList<>();
		List<List<Simulator.FlipEvent>> flipsByPlan = new ArrayList<>();
		for (Plan plan : Plan.values())
		{
			if (plan == Plan.TELEPORT)
			{
				continue;
			}
			List<Simulator.FlipEvent> flips = flips(plan, checkpoint, now, miningRemainingSeconds, whileMining);
			if (flips != null)
			{
				plans.add(plan);
				flipsByPlan.add(flips);
			}
		}
		// Cheapest first (stable: equal costs keep the simulator's order).
		List<Integer> order = new ArrayList<>();
		for (int i = 0; i < plans.size(); i++)
		{
			order.add(i);
		}
		order.sort((x, y) -> Double.compare(plans.get(x).costSeconds, plans.get(y).costSeconds));

		List<double[]> rates = new ArrayList<>();
		Plan safe = null;
		for (int index : order)
		{
			Plan plan = plans.get(index);
			double rate = predictor.deathRate(flipsByPlan.get(index), RESET_TIME);
			rates.add(new double[]{index, rate});
			if (rate <= 0)
			{
				safe = plan;
				break;
			}
		}
		double safeCost = safe == null ? TELEPORT_SECONDS : safe.costSeconds;
		for (double[] entry : rates)
		{
			Plan plan = plans.get((int) entry[0]);
			if (entry[1] <= RISK_PER_SECOND * (safeCost - plan.costSeconds))
			{
				return new Decision(plan, entry[1]);
			}
		}
		Decision teleport = new Decision(Plan.TELEPORT, Double.NaN);
		if (checkpoint == Checkpoint.AT_B3)
		{
			return teleport;
		}
		// Not the last checkpoint: only teleport if the mine could blow up before the next one.
		double next = nextCheckpoint(checkpoint, now, miningRemainingSeconds);
		if (predictor.deathRate((int) Math.floor(next)) <= 0)
		{
			return new Decision(Plan.NONE, rates.isEmpty() ? 0 : rates.get(0)[1]);
		}
		return teleport;
	}

	/** Whether to check the B vent as B1 breaks (before the decision). */
	static boolean shouldCheckB(StrategyPredictor predictor, double now)
	{
		if (predictor.deathRate(RESET_TIME) < B_CHECK_RISK)
		{
			return false;
		}
		// Not if the mine could blow up before the check walk is over: the fix comes first.
		return predictor.deathRate((int) Math.floor(now + B_CHECK_DETOUR_TICKS * TICK)) <= 0;
	}

	static double nextCheckpoint(Checkpoint checkpoint, double now, double miningRemainingSeconds)
	{
		double remaining = miningRemainingSeconds >= 0 ? miningRemainingSeconds : STAGE_SECONDS;
		double nextUpdate = (Math.floor(Math.floor(now) / 15) + 1) * 15;
		switch (checkpoint)
		{
			case MINING_B1:
			case MINING_B2:
				return Math.min(now + remaining, nextUpdate);
			case B1_BROKEN:
				return now + B1_TO_B2_TICKS * TICK;
			case AT_B2:
				return now + STAGE_SECONDS;
			case B2_BROKEN:
				return now + B2_TO_B3_TICKS * TICK;
			default:
				return now;
		}
	}

	/**
	 * The plan's chamber flips (simulation seconds), or null if its window has passed at this
	 * checkpoint (the simulator's _half1_decision_schedule).
	 */
	static List<Simulator.FlipEvent> flips(Plan plan, Checkpoint checkpoint, double now, double miningRemainingSeconds,
		boolean whileMining)
	{
		if (plan == Plan.NONE)
		{
			return Collections.emptyList();
		}
		double remaining = miningRemainingSeconds >= 0 ? miningRemainingSeconds : STAGE_SECONDS;
		Double afterB1Anchor = null;
		Double afterB2Anchor = null;
		boolean nowB1 = false;
		boolean nowB2 = false;
		switch (checkpoint)
		{
			case MINING_B1:
			{
				double b1Mined = now + remaining;
				afterB1Anchor = b1Mined;
				afterB2Anchor = b1Mined + B1_TO_B2_TICKS * TICK + STAGE_SECONDS;
				nowB1 = whileMining;
				break;
			}
			case B1_BROKEN:
				afterB1Anchor = now;
				afterB2Anchor = now + B1_TO_B2_TICKS * TICK + STAGE_SECONDS;
				break;
			case MINING_B2:
				afterB2Anchor = now + remaining;
				nowB2 = true;
				break;
			case AT_B2:
				afterB2Anchor = now + STAGE_SECONDS;
				break;
			case B2_BROKEN:
				afterB2Anchor = now;
				break;
			default:
				break;
		}
		double[] ttf;
		double anchor;
		switch (plan)
		{
			case FIX_A_AFTER_B1: ttf = new double[]{A_AFTER_B1_TTF}; anchor = orNaN(afterB1Anchor); break;
			case FIX_C_AFTER_B1: ttf = new double[]{C_AFTER_B1_TTF}; anchor = orNaN(afterB1Anchor); break;
			case FIX_B_AFTER_B1: ttf = new double[]{B_AFTER_B1_TTF}; anchor = orNaN(afterB1Anchor); break;
			case FIX_BA_AFTER_B1: ttf = new double[]{BA_B_TTF, BA_A_TTF}; anchor = orNaN(afterB1Anchor); break;
			case FIX_AB_AFTER_B1: ttf = new double[]{A_AFTER_B1_TTF, AB_AFTER_B1_B_TTF}; anchor = orNaN(afterB1Anchor); break;
			case FIX_BC_AFTER_B1: ttf = new double[]{BC_AFTER_B1_C_TTF, BC_AFTER_B1_B_TTF}; anchor = orNaN(afterB1Anchor); break;
			case FIX_A_NOW_B1: ttf = new double[]{A_RETURN_B1_TTF}; anchor = nowB1 ? now : Double.NaN; break;
			case FIX_B_NOW_B1: ttf = new double[]{B_RETURN_B1_TTF}; anchor = nowB1 ? now : Double.NaN; break;
			case FIX_BA_NOW_B1: ttf = new double[]{BA_RETURN_B1_B_TTF, BA_RETURN_B1_A_TTF}; anchor = nowB1 ? now : Double.NaN; break;
			case FIX_C_NOW_B1: ttf = new double[]{C_RETURN_B1_TTF}; anchor = nowB1 ? now : Double.NaN; break;
			case FIX_CB_NOW_B1: ttf = new double[]{CB_RETURN_B1_C_TTF, CB_RETURN_B1_B_TTF}; anchor = nowB1 ? now : Double.NaN; break;
			case FIX_AB_NOW_B1: ttf = new double[]{AB_RETURN_B1_A_TTF, AB_RETURN_B1_B_TTF}; anchor = nowB1 ? now : Double.NaN; break;
			case FIX_A_AFTER_B2: ttf = new double[]{A_AFTER_B2_TTF}; anchor = orNaN(afterB2Anchor); break;
			case FIX_C_AFTER_B2: ttf = new double[]{C_AFTER_B2_TTF}; anchor = orNaN(afterB2Anchor); break;
			case FIX_B_AFTER_B2: ttf = new double[]{B_AFTER_B2_TTF}; anchor = orNaN(afterB2Anchor); break;
			case FIX_AB_AFTER_B2: ttf = new double[]{A_AFTER_B2_TTF, AB_AFTER_B2_B_TTF}; anchor = orNaN(afterB2Anchor); break;
			case FIX_A_NOW_B2: ttf = new double[]{A_RETURN_B2[0]}; anchor = nowB2 ? now : Double.NaN; break;
			case FIX_B_NOW_B2: ttf = new double[]{B_RETURN_B2[0]}; anchor = nowB2 ? now : Double.NaN; break;
			case FIX_C_NOW_B2: ttf = new double[]{C_RETURN_B2[0]}; anchor = nowB2 ? now : Double.NaN; break;
			default: return null;
		}
		if (Double.isNaN(anchor))
		{
			return null;
		}
		List<Simulator.FlipEvent> flips = new ArrayList<>();
		int f = 0;
		for (Step step : plan.steps)
		{
			if (step.action != CoachAction.FIX_CHAMBER)
			{
				continue;
			}
			flips.add(new Simulator.FlipEvent((int) Math.ceil(anchor + ttf[f++] * TICK - 1e-9), 1 << step.vent));
		}
		return flips;
	}

	private static double orNaN(Double value)
	{
		return value == null ? Double.NaN : value;
	}
}
