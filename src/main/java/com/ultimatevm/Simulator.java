package com.ultimatevm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Simulates stability forward from the possible vent values ({@link VentInference}) using the
 * game's rules ({@link VentRules}). Times here are seconds from the start of the game (600s).
 *
 * <ul>
 *     <li>Vents move every 6s (10 ticks) and stability updates every 15s (25 ticks), on one
 *     clock for the whole game; the vent move due at exactly 5:00 doesn't happen (the reset
 *     replaces it).</li>
 *     <li>Sudden earthquakes are rolled every 9s. One on a vent-move tick (every 18s: 6, 24, 42,
 *     ...) cancels A's and B's move, and C's move may or may not be cancelled too; one on a
 *     stability-update tick (every 45s: 15, 60, 105, ...) cancels that update. Whether one
 *     happens is random, so predictions are ranges covering every possibility.</li>
 * </ul>
 */
public final class Simulator
{
	private static final int FIVE_MINUTE_MARK_TIME = 300;
	private static final int DEFAULT_MAX_TIME = 300;
	private static final int EXTENDED_MAX_TIME = 555; // 0:45, as the 0:30 change always sets stab to 5%
	private static final int MIN_STABILITY = 0;
	private static final int MAX_STABILITY = 100;
	private static final int DEFAULT_MIN_SAFE_STABILITY = 1;

	// Earthquake chances, estimated from logged games (only used to weight possibilities for the
	// blow-up chance and the guide). An earthquake is one roll on its tick that cancels whatever is
	// due then: a vent-move tick lost its move 11 times in 69 and a stability-update tick lost its
	// update 5 times in 20 - the same rate within the noise, so one chance, 16 in 89. On the three
	// ticks where both are due (1:00, 2:30, 4:00) one earthquake cancels both. When A and B's move
	// is cancelled, C's was seen both cancelled and not - assumed even.
	static final double P_EARTHQUAKE = 16.0 / 89;
	static final double P_VENT_MOVE_CANCELLED = P_EARTHQUAKE;
	static final double P_C_ALSO_CANCELLED = 0.5;
	static final double P_STABILITY_UPDATE_CANCELLED = P_EARTHQUAKE;
	// Marks mass whose move an earthquake just cancelled on a shared tick, so the same earthquake
	// cancels that second's update too. Above any packed state (at most 101^4).
	private static final int QUAKED = 1 << 30;
	// Probability mass below this is dropped when simulating weighted futures (keeps it bounded).
	private static final double NEGLIGIBLE_PROBABILITY = 1e-7;
	private static final double IGNORABLE_PROBABILITY_TAIL = 0.001;
	private static final int MAX_FULLY_WEIGHTED_CANDIDATES = 200;
	// The guide checks many plans per update, so it gives the full earthquake treatment to fewer.
	private static final int MAX_GUIDE_WEIGHTED_CANDIDATES = 100;
	// The likely band shown for each future update: from the 1st to the 99th percentile.
	static final double LIKELY_BAND_LOW = 0.01;
	static final double LIKELY_BAND_HIGH = 0.99;

	// Chamber-status bitmask (see VentRules.direction).
	public static final int CHAMBER_A_BIT = 1;
	public static final int CHAMBER_B_BIT = 2;
	public static final int CHAMBER_C_BIT = 4;

	private Simulator()
	{
	}

	// ------------------------------------------------------------------ timing

	static boolean isVentMoveTime(int time)
	{
		// Nothing moves at 0:00 itself (the game starts then).
		return time > 0 && time % 6 == 0 && time != FIVE_MINUTE_MARK_TIME;
	}

	static boolean isStabilityUpdateTime(int time)
	{
		return time > 0 && time % 15 == 0;
	}

	static boolean canEarthquakeCancelVentMove(int time)
	{
		return time % 18 == 6;
	}

	static boolean canEarthquakeCancelStabilityUpdate(int time)
	{
		return time % 45 == 15;
	}

	/** A tick where one earthquake can cancel both the vent move and the stability update. */
	static boolean isSharedEarthquakeTime(int time)
	{
		return canEarthquakeCancelVentMove(time) && canEarthquakeCancelStabilityUpdate(time) && isVentMoveTime(time);
	}

	/**
	 * Mass going ahead with / held back from a stability update. On a shared tick the earthquake
	 * already decided it: held back exactly when the move was cancelled (marked {@link #QUAKED}).
	 */
	private static double[] updateSplit(int time, boolean quaked, double mass)
	{
		if (isSharedEarthquakeTime(time))
		{
			return quaked ? new double[]{0, mass} : new double[]{mass, 0};
		}
		if (canEarthquakeCancelStabilityUpdate(time))
		{
			return new double[]{mass * (1 - P_STABILITY_UPDATE_CANCELLED), mass * P_STABILITY_UPDATE_CANCELLED};
		}
		return new double[]{mass, 0};
	}

	/**
	 * The vent values possible after a move on an earthquake-cancellable tick: the normal move;
	 * all three cancelled; or A and B cancelled while C still moves.
	 */
	static int[][] ventOutcomesOnCancellableMove(int a, int b, int c, int chamberStatus)
	{
		int[] moved = VentRules.move(a, b, c, chamberStatus);
		return new int[][]{moved, {a, b, c}, {a, b, moved[2]}};
	}

	/** The chances of each outcome of {@link #ventOutcomesOnCancellableMove}, in the same order. */
	static double[] cancellableMoveChances()
	{
		return new double[]{
			1 - P_VENT_MOVE_CANCELLED,
			P_VENT_MOVE_CANCELLED * P_C_ALSO_CANCELLED,
			P_VENT_MOVE_CANCELLED * (1 - P_C_ALSO_CANCELLED)};
	}

	private static int getMaxTimeForStart(int startTime)
	{
		return startTime < FIVE_MINUTE_MARK_TIME ? DEFAULT_MAX_TIME : EXTENDED_MAX_TIME;
	}

	// ------------------------------------------------------------------ one set of vents

	public static SimulationResult simulate(int a, int b, int c, int chamberStatus, int stability, int time)
	{
		SimulationResult nominal = simulateNominal(a, b, c, chamberStatus, stability, time, DEFAULT_MIN_SAFE_STABILITY);
		int startTime = clamp(time, 0, EXTENDED_MAX_TIME);
		return new SimulationResult(nominal.willDie, nominal.stabilityOverTime, simulateEarthquakeRange(
			Collections.singletonList(new int[]{a, b, c}), chamberStatus, clamp(stability, MIN_STABILITY, MAX_STABILITY),
			startTime, getMaxTimeForStart(startTime)));
	}

	/**
	 * The most likely future (no earthquake cancels anything). Before the 5-minute reset only
	 * stability 0 counts as blowing up; from the reset on, stability must stay at or above
	 * minSafeStability.
	 */
	static SimulationResult simulateNominal(int a, int b, int c, int chamberStatus, int stability, int time, int minSafeStability)
	{
		int startTime = clamp(time, 0, EXTENDED_MAX_TIME);
		return simulateNominal(a, b, c, chamberStatus, stability, startTime, getMaxTimeForStart(startTime), minSafeStability);
	}

	/** As above, ending at maxTime (a later phase of a simulation keeps the first phase's end). */
	private static SimulationResult simulateNominal(int a, int b, int c, int chamberStatus, int stability, int startTime, int maxTime,
		int minSafeStability)
	{
		List<StabilityPoint> stabilityOverTime = new ArrayList<>();
		int currentStability = clamp(stability, MIN_STABILITY, MAX_STABILITY);
		boolean willDie = false;

		for (int currentTime = startTime; currentTime <= maxTime; currentTime++)
		{
			if (isVentMoveTime(currentTime))
			{
				int[] vents = VentRules.move(a, b, c, chamberStatus);
				a = vents[0];
				b = vents[1];
				c = vents[2];
			}

			if (isStabilityUpdateTime(currentTime))
			{
				currentStability = clamp(currentStability + VentRules.stabilityChange(a, b, c), MIN_STABILITY, MAX_STABILITY);
				stabilityOverTime.add(new StabilityPoint(currentTime, currentStability));
			}

			int effectiveMinSafeStability = currentTime >= FIVE_MINUTE_MARK_TIME ? minSafeStability : DEFAULT_MIN_SAFE_STABILITY;
			if (currentStability < effectiveMinSafeStability)
			{
				willDie = true;
				break;
			}
		}

		return new SimulationResult(willDie, stabilityOverTime);
	}

	public static int[] getVentValuesAtTime(int a, int b, int c, int chamberStatus, int startTime, int targetTime)
	{
		int clampedTarget = Math.min(targetTime, getMaxTimeForStart(startTime));
		for (int currentTime = startTime; currentTime < clampedTarget; currentTime++)
		{
			if (isVentMoveTime(currentTime))
			{
				int[] vents = VentRules.move(a, b, c, chamberStatus);
				a = vents[0];
				b = vents[1];
				c = vents[2];
			}
		}
		return new int[]{a, b, c};
	}

	// ------------------------------------------------------------------ every possible set of vents

	/** {@link #predict(List, double[], int, int, int)} with every candidate equally likely. */
	public static Prediction predict(List<int[]> candidates, int chamberStatus, int stability, int time)
	{
		double[] weights = new double[candidates == null ? 0 : candidates.size()];
		java.util.Arrays.fill(weights, 1);
		return predict(candidates, weights, chamberStatus, stability, time);
	}

	/**
	 * The prediction for every possible set of current vent values:
	 * <ul>
	 *     <li>the blow-up chance: the probability of blowing up, weighing each set of vents by its
	 *     weight and every future earthquake by its chance;</li>
	 *     <li>at each stability update, the lowest and highest stability across every set of vents
	 *     and every earthquake possibility.</li>
	 * </ul>
	 * Both are single shared passes in which identical states are merged, so overlapping
	 * candidates cost nothing extra.
	 */
	public static Prediction predict(List<int[]> candidates, double[] weights, int chamberStatus, int stability, int time)
	{
		if (candidates == null || candidates.isEmpty())
		{
			return new Prediction(0, 0.0, Collections.emptyList(), Collections.emptyList(), null);
		}
		int startTime = clamp(time, 0, EXTENDED_MAX_TIME);
		int maxTime = getMaxTimeForStart(startTime);
		int clampedStability = clamp(stability, MIN_STABILITY, MAX_STABILITY);
		List<StabilityPoint> likely = candidates.size() == 1
			? simulateNominal(candidates.get(0)[0], candidates.get(0)[1], candidates.get(0)[2], chamberStatus, clampedStability,
				startTime, DEFAULT_MIN_SAFE_STABILITY).stabilityOverTime
			: null;
		WeightedFuture future = simulateWeightedFuture(candidates, weights, chamberStatus, clampedStability, startTime, maxTime);
		List<StabilityRangePoint> possible = simulateEarthquakeRange(candidates, chamberStatus, clampedStability, startTime, maxTime);
		return new Prediction(candidates.size(), future.blowUpProbability * 100.0, future.likelyRange, possible, likely);
	}

	static double simulateBlowUpProbability(List<int[]> candidates, double[] weights, int chamberStatus, int stability,
		int startTime, int maxTime)
	{
		return simulateWeightedFuture(candidates, weights, chamberStatus, stability, startTime, maxTime).blowUpProbability;
	}

	/** The weighted future: the blow-up probability and the likely stability band at each update. */
	static final class WeightedFuture
	{
		final double blowUpProbability;
		final List<StabilityRangePoint> likelyRange;

		WeightedFuture(double blowUpProbability, List<StabilityRangePoint> likelyRange)
		{
			this.blowUpProbability = blowUpProbability;
			this.likelyRange = likelyRange;
		}
	}

	/**
	 * Probability of stability reaching 0 by maxTime, and at each update the band stability will
	 * most likely be in (the {@link #LIKELY_BAND_LOW} to {@link #LIKELY_BAND_HIGH} percentile, a
	 * blown-up mine counting as 0). Tracks probability mass per (vents, stability) state: each vent
	 * move or stability update an earthquake could cancel splits the mass by the earthquake
	 * chances; identical states merge; mass that blows up is counted; negligible mass is dropped.
	 */
	static WeightedFuture simulateWeightedFuture(List<int[]> candidates, double[] weights, int chamberStatus, int stability,
		int startTime, int maxTime)
	{
		List<StabilityRangePoint> likelyRange = new ArrayList<>();
		double total = 0;
		for (int i = 0; i < candidates.size(); i++)
		{
			total += weights[i];
		}
		if (total <= 0)
		{
			return new WeightedFuture(0, likelyRange);
		}
		if (stability <= 0)
		{
			return new WeightedFuture(1, likelyRange);
		}
		// The least likely candidates making up the last 0.1% of probability are left out (they
		// can move the answer by at most 0.1%); the displayed range still covers every candidate.
		Integer[] order = new Integer[candidates.size()];
		for (int i = 0; i < order.length; i++)
		{
			order[i] = i;
		}
		java.util.Arrays.sort(order, (x, y) -> Double.compare(weights[y], weights[x]));
		// The most likely candidates get the full treatment (every future earthquake weighed by its
		// chance); any beyond MAX_FULLY_WEIGHTED_CANDIDATES (only possible early in a half) use the
		// cheap no-earthquake outcome instead, still weighted. This keeps the cost bounded.
		MassTable states = new MassTable(Math.max(16, Math.min(candidates.size(), MAX_FULLY_WEIGHTED_CANDIDATES) * 2));
		double kept = 0;
		double blownUp = 0;
		int fullyWeighted = 0;
		for (int index : order)
		{
			if (kept >= total * (1 - IGNORABLE_PROBABILITY_TAIL))
			{
				break;
			}
			int[] v = candidates.get(index);
			if (fullyWeighted < MAX_FULLY_WEIGHTED_CANDIDATES)
			{
				states.add(packState(v[0], v[1], v[2], stability), weights[index] / total);
				fullyWeighted++;
			}
			else if (simulateNominal(v[0], v[1], v[2], chamberStatus, stability, startTime, DEFAULT_MIN_SAFE_STABILITY).willDie)
			{
				blownUp += weights[index] / total;
			}
			kept += weights[index];
		}

		double[] moveChances = cancellableMoveChances();
		for (int currentTime = startTime; currentTime <= maxTime; currentTime++)
		{
			if (isVentMoveTime(currentTime))
			{
				states = moveStep(states, currentTime, chamberStatus, moveChances);
			}

			if (isStabilityUpdateTime(currentTime))
			{
				boolean cancellable = canEarthquakeCancelStabilityUpdate(currentTime);
				MassTable next = new MassTable(states.size() * (cancellable ? 2 : 1));
				for (int i = 0; i < states.capacity(); i++)
				{
					int key = states.keyAt(i);
					if (key < 0)
					{
						continue;
					}
					boolean quaked = (key & QUAKED) != 0;
					key &= ~QUAKED;
					double[] split = updateSplit(currentTime, quaked, states.massAt(i));
					int s = key % 101, vents = key / 101;
					int a = vents / 10201, b = (vents / 101) % 101, c = vents % 101;
					int updated = clamp(s + VentRules.stabilityChange(a, b, c), MIN_STABILITY, MAX_STABILITY);
					if (split[0] > 0)
					{
						if (updated <= 0)
						{
							blownUp += split[0];
						}
						else
						{
							next.add(packState(a, b, c, updated), split[0]);
						}
					}
					if (split[1] > 0)
					{
						next.add(key, split[1]);
					}
				}
				states = next;
				likelyRange.add(likelyBand(currentTime, states, blownUp));
			}
		}
		return new WeightedFuture(Math.min(1, blownUp), likelyRange);
	}

	/** The band holding the middle of the probability (blown-up counted as stability 0). */
	private static StabilityRangePoint likelyBand(int time, MassTable states, double blownUp)
	{
		double[] byStability = new double[MAX_STABILITY + 1];
		byStability[0] += blownUp;
		double sum = blownUp;
		for (int i = 0; i < states.capacity(); i++)
		{
			int key = states.keyAt(i);
			if (key >= 0)
			{
				byStability[key % 101] += states.massAt(i);
				sum += states.massAt(i);
			}
		}
		if (sum <= 0)
		{
			return new StabilityRangePoint(time, 0, 0);
		}
		int low = -1;
		int high = 0;
		double cumulative = 0;
		for (int s = 0; s <= MAX_STABILITY; s++)
		{
			cumulative += byStability[s];
			if (low < 0 && cumulative >= sum * LIKELY_BAND_LOW)
			{
				low = s;
			}
			if (cumulative >= sum * LIKELY_BAND_HIGH)
			{
				high = s;
				break;
			}
		}
		low = Math.max(0, low);
		return new StabilityRangePoint(time, low, Math.max(low, high));
	}

	/**
	 * One vent move for every state's mass. On a tick an earthquake can cancel, the mass splits:
	 * moved, all three cancelled, or A and B cancelled while C still moves (marked {@link #QUAKED}
	 * on a shared tick so that second's update is cancelled too).
	 */
	private static MassTable moveStep(MassTable states, int time, int chamber, double[] moveChances)
	{
		boolean cancellable = canEarthquakeCancelVentMove(time);
		int mark = isSharedEarthquakeTime(time) ? QUAKED : 0;
		MassTable next = new MassTable(states.size() * (cancellable ? 3 : 1));
		for (int i = 0; i < states.capacity(); i++)
		{
			int key = states.keyAt(i);
			if (key < 0)
			{
				continue;
			}
			double mass = states.massAt(i);
			int s = key % 101, vents = key / 101;
			int a = vents / 10201, b = (vents / 101) % 101, c = vents % 101;
			next.add(VentRules.movePacked(a, b, c, chamber, false) * 101 + s, cancellable ? mass * moveChances[0] : mass);
			if (cancellable)
			{
				next.add((vents * 101 + s) | mark, mass * moveChances[1]);
				next.add((VentRules.movePacked(a, b, c, chamber, true) * 101 + s) | mark, mass * moveChances[2]);
			}
		}
		return next;
	}

	private static int packState(int a, int b, int c, int stability)
	{
		return ((a * 101 + b) * 101 + c) * 101 + stability;
	}

	/** int key -> probability mass, open addressing (no boxing). Keys are non-negative. */
	private static final class MassTable
	{
		private final int[] keys;
		private final double[] mass;
		private int size;

		MassTable(int expected)
		{
			int capacity = Integer.highestOneBit(Math.max(16, expected * 2 - 1)) << 1;
			keys = new int[capacity];
			mass = new double[capacity];
			java.util.Arrays.fill(keys, -1);
		}

		void add(int key, double amount)
		{
			if (amount < NEGLIGIBLE_PROBABILITY)
			{
				return;
			}
			int mask = keys.length - 1;
			int slot = (key * 0x9E3779B1) >>> 1 & mask;
			while (keys[slot] >= 0 && keys[slot] != key)
			{
				slot = (slot + 1) & mask;
			}
			if (keys[slot] < 0)
			{
				keys[slot] = key;
				size++;
			}
			mass[slot] += amount;
		}

		int size()
		{
			return size;
		}

		int capacity()
		{
			return keys.length;
		}

		int keyAt(int slot)
		{
			return keys[slot];
		}

		double massAt(int slot)
		{
			return mass[slot];
		}
	}

	/**
	 * Whether any of the candidates blows up with these chamber flips (the guide's question: is
	 * this plan safe for every possible set of vents?). Stops at the first one that does.
	 */
	public static boolean anyCandidateBlowsUp(List<int[]> candidates, int chamberStatus, int stability, int time,
		List<FlipEvent> flipEvents, int minSafeStability)
	{
		List<FlipEvent> sortedEvents = new ArrayList<>(flipEvents == null ? Collections.emptyList() : flipEvents);
		sortedEvents.sort((left, right) -> Integer.compare(left.time, right.time));
		int startTime = clamp(time, 0, EXTENDED_MAX_TIME);
		FlipTimes flips = new FlipTimes(sortedEvents);
		for (int[] vents : candidates)
		{
			if (blowsUp(vents[0], vents[1], vents[2], chamberStatus, stability, startTime, flips, minSafeStability))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Share (0-1) of the candidates' total weight that blows up with these chamber flips (no
	 * future earthquakes). Used by the guide only when no plan is safe for every candidate.
	 */
	/**
	 * The guide's risk for a plan: the probability that, with these chamber flips, stability is
	 * below minSafeStability at the 5-minute update or hits 0 before it. Each possible set of vents
	 * is weighed by its weight and every earthquake by its chance (probability mass per (vents,
	 * stability) state, as for the blow-up chance). The most likely {@link #MAX_GUIDE_WEIGHTED_CANDIDATES}
	 * sets get the full treatment; the rest count by their no-earthquake outcome.
	 */
	public static double planRisk(List<int[]> candidates, double[] weights, int chamberStatus, int stability, int time,
		List<FlipEvent> flipEvents, int minSafeStability)
	{
		int startTime = clamp(time, 0, EXTENDED_MAX_TIME);
		if (startTime > FIVE_MINUTE_MARK_TIME || candidates.isEmpty())
		{
			return 0;
		}
		List<FlipEvent> sortedEvents = new ArrayList<>(flipEvents == null ? Collections.emptyList() : flipEvents);
		sortedEvents.sort((left, right) -> Integer.compare(left.time, right.time));
		FlipTimes flips = new FlipTimes(sortedEvents);
		int clampedStability = clamp(stability, MIN_STABILITY, MAX_STABILITY);

		double total = 0;
		for (int i = 0; i < candidates.size(); i++)
		{
			total += weights[i];
		}
		if (total <= 0)
		{
			return 0;
		}
		Integer[] order = new Integer[candidates.size()];
		for (int i = 0; i < order.length; i++)
		{
			order[i] = i;
		}
		java.util.Arrays.sort(order, (x, y) -> Double.compare(weights[y], weights[x]));

		MassTable states = new MassTable(Math.max(16, Math.min(candidates.size(), MAX_GUIDE_WEIGHTED_CANDIDATES) * 2));
		double failed = 0;
		int fullyWeighted = 0;
		for (int index : order)
		{
			int[] v = candidates.get(index);
			double share = weights[index] / total;
			if (fullyWeighted < MAX_GUIDE_WEIGHTED_CANDIDATES)
			{
				states.add(packState(v[0], v[1], v[2], clampedStability), share);
				fullyWeighted++;
			}
			else if (blowsUp(v[0], v[1], v[2], chamberStatus, clampedStability, startTime, flips, minSafeStability))
			{
				failed += share;
			}
		}

		int chamber = chamberStatus;
		int nextEvent = 0;
		while (nextEvent < sortedEvents.size() && sortedEvents.get(nextEvent).time <= startTime)
		{
			chamber ^= sortedEvents.get(nextEvent++).chamberBit;
		}
		double[] moveChances = cancellableMoveChances();
		for (int currentTime = startTime; currentTime <= FIVE_MINUTE_MARK_TIME; currentTime++)
		{
			while (nextEvent < sortedEvents.size() && sortedEvents.get(nextEvent).time == currentTime)
			{
				chamber ^= sortedEvents.get(nextEvent++).chamberBit;
			}
			if (isVentMoveTime(currentTime))
			{
				states = moveStep(states, currentTime, chamber, moveChances);
			}

			if (isStabilityUpdateTime(currentTime))
			{
				boolean cancellable = canEarthquakeCancelStabilityUpdate(currentTime);
				boolean fiveMinuteMark = currentTime == FIVE_MINUTE_MARK_TIME;
				MassTable next = new MassTable(states.size() * (cancellable ? 2 : 1));
				for (int i = 0; i < states.capacity(); i++)
				{
					int key = states.keyAt(i);
					if (key < 0)
					{
						continue;
					}
					boolean quaked = (key & QUAKED) != 0;
					key &= ~QUAKED;
					double[] split = updateSplit(currentTime, quaked, states.massAt(i));
					int s = key % 101, vents = key / 101;
					int a = vents / 10201, b = (vents / 101) % 101, c = vents % 101;
					int updated = clamp(s + VentRules.stabilityChange(a, b, c), MIN_STABILITY, MAX_STABILITY);
					double goesAhead = split[0];
					double heldBack = split[1];
					// Blown up before 5:00, or short of the safe level at the 5:00 update.
					int floor = fiveMinuteMark ? minSafeStability : DEFAULT_MIN_SAFE_STABILITY;
					if (updated < floor)
					{
						failed += goesAhead;
					}
					else
					{
						next.add(packState(a, b, c, updated), goesAhead);
					}
					if (heldBack > 0)
					{
						if (s < floor)
						{
							failed += heldBack;
						}
						else
						{
							next.add(key, heldBack);
						}
					}
				}
				states = next;
			}
		}
		return Math.min(1, failed);
	}

	/** Quick no-earthquake version of {@link #planRisk}: the weighted share of candidates that fall short. */
	public static double weightedBlowUpShare(List<int[]> candidates, double[] weights, int chamberStatus, int stability, int time,
		List<FlipEvent> flipEvents, int minSafeStability)
	{
		List<FlipEvent> sortedEvents = new ArrayList<>(flipEvents == null ? Collections.emptyList() : flipEvents);
		sortedEvents.sort((left, right) -> Integer.compare(left.time, right.time));
		int startTime = clamp(time, 0, EXTENDED_MAX_TIME);
		FlipTimes flips = new FlipTimes(sortedEvents);
		double total = 0;
		double dying = 0;
		for (int i = 0; i < candidates.size(); i++)
		{
			int[] vents = candidates.get(i);
			total += weights[i];
			if (blowsUp(vents[0], vents[1], vents[2], chamberStatus, stability, startTime, flips, minSafeStability))
			{
				dying += weights[i];
			}
		}
		return total > 0 ? dying / total : 0;
	}

	/**
	 * The lowest and highest stability the next update can bring, across the candidates (no
	 * earthquakes), as {min, max}; null if there are no candidates.
	 */
	static int[] nextStabilityChangeRange(List<int[]> candidates, int chamberStatus, int time)
	{
		if (candidates == null || candidates.isEmpty())
		{
			return null;
		}
		int startTime = clamp(time, 0, EXTENDED_MAX_TIME);
		int updateTime = startTime + 1;
		while (!isStabilityUpdateTime(updateTime))
		{
			updateTime++;
		}
		int min = Integer.MAX_VALUE;
		int max = Integer.MIN_VALUE;
		for (int[] start : candidates)
		{
			int[] v = getVentValuesAtTime(start[0], start[1], start[2], chamberStatus, startTime + 1, updateTime + 1);
			int change = VentRules.stabilityChange(v[0], v[1], v[2]);
			min = Math.min(min, change);
			max = Math.max(max, change);
		}
		return new int[]{min, max};
	}

	/**
	 * Lowest and highest stability at each update from startTime to maxTime, across all the
	 * starting vents and every earthquake cancellation. Each distinct set of vent values reached
	 * is one state holding the lowest and highest stability that can go with it; states with the
	 * same vents are merged, so overlapping candidates cost nothing extra. Stability 0 is final.
	 */
	static List<StabilityRangePoint> simulateEarthquakeRange(List<int[]> startVents, int chamberStatus, int stability,
		int startTime, int maxTime)
	{
		RangeTable states = new RangeTable(startVents.size());
		for (int[] v : startVents)
		{
			states.merge((v[0] * 101 + v[1]) * 101 + v[2], stability, stability);
		}
		List<StabilityRangePoint> points = new ArrayList<>();

		for (int currentTime = startTime; currentTime <= maxTime; currentTime++)
		{
			if (isVentMoveTime(currentTime))
			{
				boolean cancellable = canEarthquakeCancelVentMove(currentTime);
				RangeTable next = new RangeTable(states.size * (cancellable ? 3 : 1));
				for (int i = 0; i < states.keys.length; i++)
				{
					int vents = states.keys[i];
					if (vents < 0)
					{
						continue;
					}
					int a = vents / 10201, b = (vents / 101) % 101, c = vents % 101;
					int min = states.min[i], max = states.max[i];
					next.merge(VentRules.movePacked(a, b, c, chamberStatus, false), min, max);
					if (cancellable)
					{
						next.merge(vents, min, max);
						next.merge(VentRules.movePacked(a, b, c, chamberStatus, true), min, max);
					}
				}
				states = next;
			}

			if (isStabilityUpdateTime(currentTime))
			{
				boolean cancellable = canEarthquakeCancelStabilityUpdate(currentTime);
				int overallMin = Integer.MAX_VALUE;
				int overallMax = Integer.MIN_VALUE;
				for (int i = 0; i < states.keys.length; i++)
				{
					int vents = states.keys[i];
					if (vents < 0)
					{
						continue;
					}
					int change = VentRules.stabilityChange(vents / 10201, (vents / 101) % 101, vents % 101);
					int oldMin = states.min[i], oldMax = states.max[i];
					int newMin = oldMin == 0 ? 0 : clamp(oldMin + change, MIN_STABILITY, MAX_STABILITY);
					int newMax = oldMax == 0 ? 0 : clamp(oldMax + change, MIN_STABILITY, MAX_STABILITY);
					states.min[i] = cancellable ? Math.min(oldMin, newMin) : newMin;
					states.max[i] = cancellable ? Math.max(oldMax, newMax) : newMax;
					overallMin = Math.min(overallMin, states.min[i]);
					overallMax = Math.max(overallMax, states.max[i]);
				}
				points.add(new StabilityRangePoint(currentTime, overallMin, overallMax));
			}
		}
		return points;
	}

	/** Packed vents -> the lowest and highest stability that can go with them (open addressing). */
	private static final class RangeTable
	{
		final int[] keys;
		final int[] min;
		final int[] max;
		int size;

		RangeTable(int expected)
		{
			int capacity = Integer.highestOneBit(Math.max(16, expected * 2 - 1)) << 1;
			keys = new int[capacity];
			min = new int[capacity];
			max = new int[capacity];
			java.util.Arrays.fill(keys, -1);
		}

		void merge(int vents, int minStability, int maxStability)
		{
			int mask = keys.length - 1;
			int slot = (vents * 0x9E3779B1) >>> 1 & mask;
			while (keys[slot] >= 0 && keys[slot] != vents)
			{
				slot = (slot + 1) & mask;
			}
			if (keys[slot] < 0)
			{
				keys[slot] = vents;
				min[slot] = minStability;
				max[slot] = maxStability;
				size++;
			}
			else
			{
				min[slot] = Math.min(min[slot], minStability);
				max[slot] = Math.max(max[slot], maxStability);
			}
		}
	}

	/** Flip times and chamber bits, sorted by time, as plain arrays for {@link #blowsUp}. */
	private static final class FlipTimes
	{
		final int[] times;
		final int[] bits;

		FlipTimes(List<FlipEvent> sortedEvents)
		{
			times = new int[sortedEvents.size()];
			bits = new int[sortedEvents.size()];
			for (int i = 0; i < times.length; i++)
			{
				times[i] = sortedEvents.get(i).time;
				bits[i] = sortedEvents.get(i).chamberBit;
			}
		}
	}

	/**
	 * The no-earthquake outcome with chamber flips: whether stability hits 0 (before 5:00) or
	 * falls below minSafeStability (from 5:00 on) by the end of the half the start is in. A flip
	 * lands before its second's move and update; flips at or before the start apply from the
	 * start. One pass with no allocation - the guide runs this for every plan and every set of
	 * vents.
	 */
	private static boolean blowsUp(int a, int b, int c, int chamberStatus, int stability, int time, FlipTimes flips,
		int minSafeStability)
	{
		int startTime = clamp(time, 0, EXTENDED_MAX_TIME);
		int maxTime = getMaxTimeForStart(startTime);
		int s = clamp(stability, MIN_STABILITY, MAX_STABILITY);
		int chamber = chamberStatus;
		int next = 0;
		while (next < flips.times.length && flips.times[next] < startTime)
		{
			chamber ^= flips.bits[next++];
		}
		// Already short at the start (unless an update at the start second comes first).
		if (!isStabilityUpdateTime(startTime) && s < (startTime >= FIVE_MINUTE_MARK_TIME ? minSafeStability : DEFAULT_MIN_SAFE_STABILITY))
		{
			return true;
		}
		// Only seconds with a flip, a move or an update can change anything; stability is only
		// checked again after an update (or from 5:00, where the safe level rises).
		for (int t = startTime; t <= maxTime; t++)
		{
			while (next < flips.times.length && flips.times[next] == t)
			{
				chamber ^= flips.bits[next++];
			}
			if (t > 0 && t % 6 == 0 && t != FIVE_MINUTE_MARK_TIME)
			{
				// VentRules.move, inline.
				boolean aFrozen = a >= 41 && a <= 59;
				boolean bFrozen = b >= 41 && b <= 59;
				int cMovement = (c >= 41 && c <= 59 ? 1 : 2) - (bFrozen ? 1 : 0) - (aFrozen ? 1 : 0);
				int bMovement = (bFrozen ? 1 : 2) - (aFrozen ? 1 : 0);
				a = VentRules.clampVent(a + (aFrozen ? 1 : 2) * ((chamber & 1) != 0 ? 1 : -1));
				b = VentRules.clampVent(b + Math.max(0, bMovement) * ((chamber & 2) != 0 ? 1 : -1));
				c = VentRules.clampVent(c + Math.max(0, cMovement) * ((chamber & 4) != 0 ? 1 : -1));
			}
			if (t > 0 && t % 15 == 0)
			{
				s = clamp(s + VentRules.stabilityChange(a, b, c), MIN_STABILITY, MAX_STABILITY);
				if (s < (t >= FIVE_MINUTE_MARK_TIME ? minSafeStability : DEFAULT_MIN_SAFE_STABILITY))
				{
					return true;
				}
			}
		}
		return false;
	}

	/** {@link #firstBlowUpTime} never reached: the candidate doesn't blow up by the end time. */
	public static final int NEVER = Integer.MAX_VALUE;

	/**
	 * For each candidate, the second its stability hits 0 (no earthquakes, with these flips), up to
	 * endTime, or {@link #NEVER}. The strategy coach's predictor: the share blowing up by any time
	 * and the earliest blow-up follow from these.
	 */
	public static int[] blowUpTimes(List<int[]> candidates, int chamberStatus, int stability, int time, int endTime,
		List<FlipEvent> flipEvents)
	{
		List<FlipEvent> sortedEvents = new ArrayList<>(flipEvents == null ? Collections.emptyList() : flipEvents);
		sortedEvents.sort((left, right) -> Integer.compare(left.time, right.time));
		FlipTimes flips = new FlipTimes(sortedEvents);
		int startTime = clamp(time, 0, EXTENDED_MAX_TIME);
		int[] out = new int[candidates.size()];
		for (int i = 0; i < out.length; i++)
		{
			int[] v = candidates.get(i);
			out[i] = firstBlowUpTime(v[0], v[1], v[2], chamberStatus, stability, startTime, endTime, flips);
		}
		return out;
	}

	/**
	 * The second stability hits 0 from startTime to endTime (no earthquakes, with flips; a flip lands
	 * before its second's move and update), or {@link #NEVER}. Vents don't move at 5:00; the run never
	 * crosses the 5:00 vent re-roll (half-1 runs end at 300).
	 */
	private static int firstBlowUpTime(int a, int b, int c, int chamberStatus, int stability, int startTime, int endTime,
		FlipTimes flips)
	{
		int s = clamp(stability, MIN_STABILITY, MAX_STABILITY);
		if (s <= 0 && !isStabilityUpdateTime(startTime))
		{
			return startTime;
		}
		int chamber = chamberStatus;
		int next = 0;
		while (next < flips.times.length && flips.times[next] < startTime)
		{
			chamber ^= flips.bits[next++];
		}
		for (int t = startTime; t <= endTime; t++)
		{
			while (next < flips.times.length && flips.times[next] == t)
			{
				chamber ^= flips.bits[next++];
			}
			if (t > 0 && t % 6 == 0 && t != FIVE_MINUTE_MARK_TIME)
			{
				boolean aFrozen = a >= 41 && a <= 59;
				boolean bFrozen = b >= 41 && b <= 59;
				int cMovement = (c >= 41 && c <= 59 ? 1 : 2) - (bFrozen ? 1 : 0) - (aFrozen ? 1 : 0);
				int bMovement = (bFrozen ? 1 : 2) - (aFrozen ? 1 : 0);
				a = VentRules.clampVent(a + (aFrozen ? 1 : 2) * ((chamber & 1) != 0 ? 1 : -1));
				b = VentRules.clampVent(b + Math.max(0, bMovement) * ((chamber & 2) != 0 ? 1 : -1));
				c = VentRules.clampVent(c + Math.max(0, cMovement) * ((chamber & 4) != 0 ? 1 : -1));
			}
			if (t > 0 && t % 15 == 0)
			{
				s = clamp(s + VentRules.stabilityChange(a, b, c), MIN_STABILITY, MAX_STABILITY);
				if (s <= 0)
				{
					return t;
				}
			}
		}
		return NEVER;
	}

	private static int clamp(int value, int min, int max)
	{
		return Math.max(min, Math.min(max, value));
	}

	// ------------------------------------------------------------------ results

	public static final class SimulationResult
	{
		public final boolean willDie;
		/** Assuming no earthquake cancels anything (the most likely single outcome). */
		public final List<StabilityPoint> stabilityOverTime;
		/** Lowest/highest stability if earthquakes cancel any vent move or stability update they can. */
		public final List<StabilityRangePoint> earthquakeRangeOverTime;

		public SimulationResult(boolean willDie, List<StabilityPoint> stabilityOverTime)
		{
			this(willDie, stabilityOverTime, null);
		}

		public SimulationResult(boolean willDie, List<StabilityPoint> stabilityOverTime, List<StabilityRangePoint> earthquakeRangeOverTime)
		{
			this.willDie = willDie;
			this.stabilityOverTime = Collections.unmodifiableList(new ArrayList<>(stabilityOverTime));
			List<StabilityRangePoint> range = new ArrayList<>();
			if (earthquakeRangeOverTime != null)
			{
				range.addAll(earthquakeRangeOverTime);
			}
			else
			{
				for (StabilityPoint point : stabilityOverTime)
				{
					range.add(new StabilityRangePoint(point.time, point.stability, point.stability));
				}
			}
			this.earthquakeRangeOverTime = Collections.unmodifiableList(range);
		}
	}

	public static final class Prediction
	{
		public final int candidateCount;
		public final double blowUpChancePercent;
		/** Where stability will most likely be at each update (1st-99th percentile band). */
		public final List<StabilityRangePoint> stabilityRangeOverTime;
		/** Every value stability could possibly reach at each update, however unlikely. */
		public final List<StabilityRangePoint> possibleRangeOverTime;
		/** The single most likely future, only when the vents are known exactly (one candidate). */
		public final List<StabilityPoint> likely;

		Prediction(int candidateCount, double blowUpChancePercent, List<StabilityRangePoint> stabilityRangeOverTime,
			List<StabilityRangePoint> possibleRangeOverTime, List<StabilityPoint> likely)
		{
			this.candidateCount = candidateCount;
			this.blowUpChancePercent = blowUpChancePercent;
			this.stabilityRangeOverTime = Collections.unmodifiableList(new ArrayList<>(stabilityRangeOverTime));
			this.possibleRangeOverTime = Collections.unmodifiableList(new ArrayList<>(possibleRangeOverTime));
			this.likely = likely;
		}
	}

	public static final class StabilityPoint
	{
		public final int time;
		public final int stability;

		public StabilityPoint(int time, int stability)
		{
			this.time = time;
			this.stability = stability;
		}
	}

	public static final class StabilityRangePoint
	{
		public final int time;
		public final int minStability;
		public final int maxStability;

		public StabilityRangePoint(int time, int minStability, int maxStability)
		{
			this.time = time;
			this.minStability = minStability;
			this.maxStability = maxStability;
		}
	}

	public static final class FlipEvent
	{
		public final int time;
		public final int chamberBit;

		public FlipEvent(int time, int chamberBit)
		{
			this.time = time;
			this.chamberBit = chamberBit;
		}
	}
}
