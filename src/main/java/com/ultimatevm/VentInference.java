package com.ultimatevm;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/**
 * Works out which vent values are still possible from everything seen so far.
 *
 * It starts from every possible combination of vent values (30-70 each at the start of the game;
 * after the 5-minute reset 25-75 solo or 0-100 in a team; 0-100 when the plugin joins mid-way)
 * and replays the game tick by tick with the exact rules: vents move every 10 ticks in the
 * direction the chambers set, stability updates every 25 ticks by -25 + vent influence. Any
 * combination that doesn't produce the stability change the game showed, or doesn't match a vent
 * the player has checked, is dropped.
 *
 * Earthquakes are allowed for: on the ticks where one can cancel a vent move (every 30 ticks)
 * each combination is kept both moved and unmoved; on the ticks where one can cancel a stability
 * update (every 75 ticks) "no change" is also accepted.
 *
 * Each combination also carries a weight (its relative probability, used only for the blow-up
 * chance and the guide, never shown): starting values are equally likely, and an explanation that
 * needs an earthquake to have cancelled something is weighted by that chance (see Simulator).
 *
 * If nothing is left (something happened the rules don't cover), it starts again from every
 * value 0-100, keeping the checked vents.
 */
final class VentInference
{
	private static final int UNKNOWN = VentRules.UNKNOWN;
	// A stability change has occasionally been 1 more or 1 less than the formula (4 times in about
	// 300 updates seen) - kept as a rare explanation so one surprise can't rule out the truth.
	static final double P_PLUS_OR_MINUS_ONE = 0.005;
	// Chance an earthquake on a shared move-and-update tick cancels only one of them (assumed
	// impossible, but kept slightly possible so the true vents can't be lost).
	static final double P_ONE_SIDED_CANCEL = 0.01;
	// Combinations below this share of the probability are dropped (keeps the set small).
	// (relative to the most likely combination; allows for a dozen or more silent earthquakes)
	private static final double NEGLIGIBLE_WEIGHT = 1e-14;
	// What the display, predictions and guide use: the most likely combinations covering this much
	// of the probability (so rare explanations don't widen what's shown).
	static final double LIKELY_COVERAGE = 0.999;
	// Above this many combinations the likely set isn't worked out (it's all of them; too many to
	// predict from anyway).
	private static final int MAX_FOR_LIKELY_SET = 20000;
	private static final int[] INFLUENCE = new int[101];

	static
	{
		for (int v = 0; v <= 100; v++)
		{
			INFLUENCE[v] = VentRules.influence(v);
		}
	}

	private int[] candidates = new int[0];
	private double[] weights = new double[0];
	private int count;
	private boolean seeded;
	private int lastRem;
	private int lastStability;
	private int lastChamber;
	private final int[] lastRawVents = {UNKNOWN, UNKNOWN, UNKNOWN};

	/** What the last chat earthquake did. */
	enum EarthquakeEffect
	{
		NONE, VENT_MOVE_CANCELLED, STABILITY_UPDATE_CANCELLED, BOTH_CANCELLED
	}

	// Set by the earthquake chat message; used (and cleared) on the next tick, the earthquake's tick.
	private boolean earthquakeMessageSeen;
	private EarthquakeEffect earthquakeEffect;

	/** "A sudden earthquake strikes the cavern!" - it happens on the tick about to be processed. */
	void onEarthquakeMessage()
	{
		earthquakeMessageSeen = true;
	}

	/** What the latest chat earthquake turned out to do (once), or null if nothing new. */
	EarthquakeEffect takeEarthquakeEffect()
	{
		EarthquakeEffect effect = earthquakeEffect;
		earthquakeEffect = null;
		return effect;
	}
	// How far behind the game the client's clock can be and still be caught up from what's seen.
	private static final int MAX_CATCH_UP_TICKS = 3;
	// Ticks the clock may jump in one go before giving up and starting again.
	private static final int MAX_TICKS_TO_REPLAY = 100;
	private int restarts;
	// Packed vents -> slot in the list being built (-1 = none), to merge identical combinations.
	private final int[] slotOf = new int[101 * 101 * 101];
	private int[] nextCandidates;
	private double[] nextWeights;

	{
		java.util.Arrays.fill(slotOf, -1);
	}

	/** New game. */
	void reset()
	{
		candidates = new int[0];
		weights = new double[0];
		count = 0;
		seeded = false;
		restarts = 0;
		likelyCache = null;
		java.util.Arrays.fill(lastRawVents, UNKNOWN);
		earthquakeMessageSeen = false;
		earthquakeEffect = null;
	}

	/**
	 * Called every game tick while the game is running.
	 *
	 * @param rem       ticks remaining on the clock (1000 at the start, 500 at the reset)
	 * @param rawVents  the vent varbits (127 = not checked)
	 */
	void onTick(int rem, int stability, int[] rawVents, int chamberStatus, int players)
	{
		if (rem <= 50 || rem > 1000)
		{
			return;
		}

		if (!seeded)
		{
			seed(rem, rawVents, players);
			remember(rem, stability, chamberStatus, rawVents);
			return;
		}

		// The client sometimes misses a game tick or two, leaving its clock behind the game's until
		// the timer varbit corrects it. A visible change (a checked vent moving, stability changing)
		// shows where the game really is, so jump ahead to it.
		int target = Math.min(rem, catchUpTarget(rem, stability, rawVents));
		// Earthquakes only happen every 15 ticks, so the chat message also shows the real tick.
		boolean earthquake = earthquakeMessageSeen;
		earthquakeMessageSeen = false;
		if (earthquake && rem % 15 != 0 && rem % 15 <= MAX_CATCH_UP_TICKS)
		{
			target = Math.min(target, rem - rem % 15);
		}

		if (target >= lastRem)
		{
			// Already at or past this tick (the same tick again, or the clock catching up after a
			// jump ahead): nothing new.
			return;
		}

		if (lastRem - target > MAX_TICKS_TO_REPLAY)
		{
			// Lost track of several ticks - start again from what's known now.
			restart(rawVents);
			remember(target, stability, chamberStatus, rawVents);
			return;
		}

		boolean chamberChanged = chamberStatus != lastChamber;
		// After a freeze several ticks are replayed at once with only the final state seen. A
		// stability update inside the gap is checked against the stability seen now if it was the
		// only one; with more than one, the combined change can't be split, so they're not checked.
		int updatesInGap = 0;
		for (int r = lastRem - 1; r >= target; r--)
		{
			if (r % 25 == 0 && r != VentRules.RESET_TIME_TICKS)
			{
				updatesInGap++;
			}
		}
		for (int r = lastRem - 1; r >= target; r--)
		{
			boolean observedTick = r == target;
			if (r == VentRules.RESET_TIME_TICKS)
			{
				// The 5:00 stability update uses the vents from before the reset (no move this
				// tick), then the game re-rolls every vent.
				if (observedTick)
				{
					filterStability(lastStability, stability, false, players);
				}
				seedRange(resetMin(players), resetMax(players));
				filterKnownVents(rawVents);
				continue;
			}

			// A chat earthquake on this tick: if it's a vent-move tick the move was cancelled (A and B;
			// C may or may not have moved), if it's a stability-update tick the update was cancelled.
			boolean earthquakeHere = earthquake && observedTick;
			if (earthquakeHere)
			{
				earthquakeEffect = r % 150 == 0 ? EarthquakeEffect.BOTH_CANCELLED
					: r % 30 == 0 ? EarthquakeEffect.VENT_MOVE_CANCELLED
					: r % 75 == 0 ? EarthquakeEffect.STABILITY_UPDATE_CANCELLED
					: EarthquakeEffect.NONE;
			}

			boolean jointShared = r % 150 == 0 && !earthquakeHere && updatesInGap == 1;
			if (jointShared)
			{
				// A move and an update both due with no earthquake message: one earthquake would
				// cancel both, so the move and the update are judged together.
				sharedTick(lastChamber, chamberStatus, chamberChanged, lastStability, stability, players);
			}
			else if (r % 10 == 0)
			{
				// If the chamber flipped somewhere in a replayed gap, any move in it may have gone either way.
				move(lastChamber, chamberStatus, chamberChanged, r % 30 == 0, earthquakeHere && r % 30 == 0);
			}

			if (r % 25 == 0 && updatesInGap == 1 && !jointShared)
			{
				filterStability(lastStability, stability, r % 75 == 0, players, earthquakeHere && r % 75 == 0);
			}
			if (observedTick)
			{
				filterKnownVents(rawVents);
			}

			if (count == 0)
			{
				restart(rawVents);
			}
		}
		remember(target, stability, chamberStatus, rawVents);
	}

	/** How many combinations are in the likely set (see {@link #LIKELY_COVERAGE}). */
	int getCount()
	{
		return seeded ? likelyIndices().length : 0;
	}

	/** Every combination still possible, however unlikely (for tests and diagnostics). */
	int getAllCount()
	{
		return seeded ? count : 0;
	}

	List<int[]> getAllCandidates()
	{
		List<int[]> list = new ArrayList<>(count);
		for (int i = 0; i < count; i++)
		{
			list.add(unpack(candidates[i]));
		}
		return list;
	}

	// Indices of the likely set, most likely first; cached until the combinations change.
	private int[] likelyCache;

	private int[] likelyIndices()
	{
		if (likelyCache != null)
		{
			return likelyCache;
		}
		if (count > MAX_FOR_LIKELY_SET)
		{
			int[] all = new int[count];
			for (int i = 0; i < count; i++)
			{
				all[i] = i;
			}
			return likelyCache = all;
		}
		Integer[] order = new Integer[count];
		double total = 0;
		for (int i = 0; i < count; i++)
		{
			order[i] = i;
			total += weights[i];
		}
		java.util.Arrays.sort(order, (x, y) -> Double.compare(weights[y], weights[x]));
		int n = 0;
		double covered = 0;
		while (n < count && (n == 0 || covered < total * LIKELY_COVERAGE))
		{
			covered += weights[order[n]];
			n++;
		}
		int[] likely = new int[n];
		for (int i = 0; i < n; i++)
		{
			likely[i] = order[i];
		}
		return likelyCache = likely;
	}

	/** How many times it had to start again because nothing fitted. */
	int getRestarts()
	{
		return restarts;
	}

	/** The likely vent values right now, each as {A, B, C}, most likely first. */
	List<int[]> getCandidates()
	{
		int[] likely = likelyIndices();
		List<int[]> list = new ArrayList<>(likely.length);
		for (int index : likely)
		{
			list.add(unpack(candidates[index]));
		}
		return list;
	}

	/** Each likely candidate's relative probability, in the same order as {@link #getCandidates()} (sums to 1). */
	double[] getWeights()
	{
		int[] likely = likelyIndices();
		double[] copy = new double[likely.length];
		for (int i = 0; i < likely.length; i++)
		{
			copy[i] = weights[likely[i]];
		}
		double total = 0;
		for (double w : copy)
		{
			total += w;
		}
		if (total > 0)
		{
			for (int i = 0; i < copy.length; i++)
			{
				copy[i] /= total;
			}
		}
		return copy;
	}

	/** Every value still possible for this vent (0-2 = A-C), or null if not tracking. */
	BitSet possibleValues(int ventIndex)
	{
		if (!seeded || count == 0)
		{
			return null;
		}
		BitSet values = new BitSet(101);
		for (int index : likelyIndices())
		{
			values.set(unpack(candidates[index])[ventIndex]);
		}
		return values;
	}

	private void remember(int rem, int stability, int chamberStatus, int[] rawVents)
	{
		lastRem = rem;
		lastStability = stability;
		lastChamber = chamberStatus;
		if (rawVents != null)
		{
			System.arraycopy(rawVents, 0, lastRawVents, 0, 3);
		}
	}

	/**
	 * The tick the game is really on if a visible change shows the clock is behind: a checked
	 * vent moving, or stability changing, up to {@link #MAX_CATCH_UP_TICKS} before the tick the
	 * clock says it should happen on. Otherwise the clock's own tick.
	 */
	private int catchUpTarget(int rem, int stability, int[] rawVents)
	{
		int target = rem;
		boolean checkedVentMoved = false;
		for (int i = 0; i < 3 && rawVents != null; i++)
		{
			checkedVentMoved |= rawVents[i] != UNKNOWN && lastRawVents[i] != UNKNOWN && rawVents[i] != lastRawVents[i];
		}
		if (checkedVentMoved && rem % 10 != 0 && rem % 10 <= MAX_CATCH_UP_TICKS)
		{
			target = Math.min(target, rem - rem % 10);
		}
		if (stability != lastStability && rem % 25 != 0 && rem % 25 <= MAX_CATCH_UP_TICKS)
		{
			target = Math.min(target, rem - rem % 25);
		}
		return target;
	}

	private void seed(int rem, int[] rawVents, int players)
	{
		seeded = true;
		if (rem > 990)
		{
			// Start of the game, before the first vent move.
			seedRange(VentRules.MIN_START, VentRules.MAX_START);
		}
		else if (rem <= VentRules.RESET_TIME_TICKS && rem > 490)
		{
			seedRange(resetMin(players), resetMax(players));
		}
		else
		{
			seedRange(0, 100);
		}
		filterKnownVents(rawVents);
	}

	private void restart(int[] rawVents)
	{
		++restarts;
		seedRange(0, 100);
		filterKnownVents(rawVents);
	}

	private static int resetMin(int players)
	{
		return players <= 1 ? VentRules.MIN_RESET_SOLO : VentRules.MIN_VENT;
	}

	private static int resetMax(int players)
	{
		return players <= 1 ? VentRules.MAX_RESET_SOLO : VentRules.MAX_VENT;
	}

	private void seedRange(int min, int max)
	{
		int span = max - min + 1;
		candidates = new int[span * span * span];
		weights = new double[candidates.length];
		count = 0;
		likelyCache = null;
		for (int a = min; a <= max; a++)
		{
			for (int b = min; b <= max; b++)
			{
				for (int c = min; c <= max; c++)
				{
					weights[count] = 1;
					candidates[count++] = pack(a, b, c);
				}
			}
		}
	}

	private void filterKnownVents(int[] rawVents)
	{
		if (rawVents == null)
		{
			return;
		}
		int kept = 0;
		for (int i = 0; i < count; i++)
		{
			int[] v = unpack(candidates[i]);
			if ((rawVents[0] == UNKNOWN || rawVents[0] == v[0])
				&& (rawVents[1] == UNKNOWN || rawVents[1] == v[1])
				&& (rawVents[2] == UNKNOWN || rawVents[2] == v[2]))
			{
				weights[kept] = weights[i];
				candidates[kept++] = candidates[i];
			}
		}
		count = kept;
		likelyCache = null;
	}

	private void filterStability(int previous, int observed, boolean updateCancellable, int players)
	{
		filterStability(previous, observed, updateCancellable, players, false);
	}

	/** @param earthquakeCancelled a chat earthquake landed on this update: it certainly didn't happen */
	private void filterStability(int previous, int observed, boolean updateCancellable, int players, boolean earthquakeCancelled)
	{
		if (earthquakeCancelled)
		{
			if (observed != previous)
			{
				// Stability changed after all - the earthquake didn't cancel it; judge it normally.
				filterStability(previous, observed, updateCancellable, players, false);
				return;
			}
			// No update happened, so the stability change says nothing about the vents.
			likelyCache = null;
			return;
		}
		int kept = 0;
		for (int i = 0; i < count; i++)
		{
			// How likely this combination makes what was seen: the update going ahead normally
			// (less the chance an earthquake cancelled it) and/or it being cancelled (no change).
			double goesAhead = updateCancellable ? 1 - Simulator.P_STABILITY_UPDATE_CANCELLED : 1;
			double likelihood = goesAhead * updateLikelihood(unpack(candidates[i]), previous, observed, players);
			if (updateCancellable && observed == previous)
			{
				likelihood += Simulator.P_STABILITY_UPDATE_CANCELLED;
			}
			if (likelihood > 0)
			{
				weights[kept] = weights[i] * likelihood;
				candidates[kept++] = candidates[i];
			}
		}
		count = kept;
		normalise();
	}

	/** How likely these vents make the stability seen, if the update went ahead. */
	private static double updateLikelihood(int[] v, int previous, int observed, int players)
	{
		int change = VentRules.STABILITY_CHANGE_CONSTANT + INFLUENCE[v[0]] + INFLUENCE[v[1]] + INFLUENCE[v[2]];
		if (players >= 3)
		{
			// Random part for 3+ players: the range the vent tracker has always assumed
			// (-players/3 .. +1), even across it - not yet confirmed.
			int minRandom = -(players / 3);
			int maxRandom = 1;
			double likelihood = 0;
			for (int random = minRandom; random <= maxRandom; random++)
			{
				if (clamp(previous + change + random) == observed)
				{
					likelihood += 1.0 / (maxRandom - minRandom + 1);
				}
			}
			return likelihood;
		}
		if (clamp(previous + change) == observed)
		{
			return 1 - 2 * P_PLUS_OR_MINUS_ONE;
		}
		if (clamp(previous + change + 1) == observed || clamp(previous + change - 1) == observed)
		{
			// The rare one-off +1 / -1.
			return P_PLUS_OR_MINUS_ONE;
		}
		return 0;
	}

	/**
	 * A tick with both a vent move and a stability update due (1:00, 2:30, 4:00) and no earthquake
	 * message. One earthquake cancels both, so the outcomes are: neither cancelled, or both. A move
	 * cancelled on its own, or an update cancelled on its own, gets a small chance
	 * ({@link #P_ONE_SIDED_CANCEL}) so the true vents are never lost if that's wrong.
	 */
	private void sharedTick(int oldChamber, int newChamber, boolean tryBothDirections, int previous, int observed, int players)
	{
		double p = Simulator.P_EARTHQUAKE;
		// {move cancelled, update cancelled, chance}
		double[][] branches = {
			{0, 0, 1 - p - 2 * P_ONE_SIDED_CANCEL},
			{1, 1, p},
			{1, 0, P_ONE_SIDED_CANCEL},
			{0, 1, P_ONE_SIDED_CANCEL}};
		int[] chambers = tryBothDirections ? new int[]{newChamber, oldChamber} : new int[]{newChamber};
		nextCandidates = new int[count * chambers.length * 3 + 1];
		nextWeights = new double[nextCandidates.length];
		int n = 0;
		for (int i = 0; i < count; i++)
		{
			int[] v = unpack(candidates[i]);
			for (int chamber : chambers)
			{
				double w = weights[i] / chambers.length;
				int[][] outcomes = Simulator.ventOutcomesOnCancellableMove(v[0], v[1], v[2], chamber);
				for (double[] branch : branches)
				{
					boolean moveCancelled = branch[0] > 0;
					boolean updateCancelled = branch[1] > 0;
					for (int k = moveCancelled ? 1 : 0; k <= (moveCancelled ? 2 : 0); k++)
					{
						double share = moveCancelled ? (k == 1 ? Simulator.P_C_ALSO_CANCELLED : 1 - Simulator.P_C_ALSO_CANCELLED) : 1;
						double likelihood = updateCancelled
							? (observed == previous ? 1 : 0)
							: updateLikelihood(outcomes[k], previous, observed, players);
						if (likelihood > 0)
						{
							n = add(n, outcomes[k], w * branch[2] * share * likelihood);
						}
					}
				}
			}
		}
		for (int i = 0; i < n; i++)
		{
			slotOf[nextCandidates[i]] = -1;
		}
		candidates = nextCandidates;
		weights = nextWeights;
		count = n;
		nextCandidates = null;
		nextWeights = null;
		normalise();
	}

	private void move(int oldChamber, int newChamber, boolean tryBothDirections, boolean cancellable)
	{
		move(oldChamber, newChamber, tryBothDirections, cancellable, false);
	}

	/** @param earthquakeCancelled a chat earthquake landed on this move: A and B certainly didn't move */
	private void move(int oldChamber, int newChamber, boolean tryBothDirections, boolean cancellable, boolean earthquakeCancelled)
	{
		int extra = (tryBothDirections ? 1 : 0) + (cancellable ? 2 : 0);
		nextCandidates = new int[count * (1 + extra) + 1];
		nextWeights = new double[nextCandidates.length];
		int n = 0;
		// If the chamber flipped on this very tick, either direction is possible: split evenly.
		double directionShare = tryBothDirections ? 0.5 : 1;
		double[] chances = Simulator.cancellableMoveChances();
		for (int i = 0; i < count; i++)
		{
			int[] v = unpack(candidates[i]);
			double w = weights[i] * directionShare;
			if (cancellable && earthquakeCancelled)
			{
				// Known cancelled: all three stayed, or A and B stayed while C still moved.
				int[][] outcomes = Simulator.ventOutcomesOnCancellableMove(v[0], v[1], v[2], newChamber);
				n = add(n, outcomes[1], w * Simulator.P_C_ALSO_CANCELLED);
				n = add(n, outcomes[2], w * (1 - Simulator.P_C_ALSO_CANCELLED));
			}
			else if (cancellable)
			{
				// Moved, all cancelled, or A and B cancelled while C still moved.
				int[][] outcomes = Simulator.ventOutcomesOnCancellableMove(v[0], v[1], v[2], newChamber);
				for (int k = 0; k < outcomes.length; k++)
				{
					n = add(n, outcomes[k], w * chances[k]);
				}
			}
			else
			{
				n = add(n, VentRules.move(v[0], v[1], v[2], newChamber), w);
			}
			if (tryBothDirections)
			{
				// The chamber flipped on a move tick: it may have moved the old way.
				n = add(n, VentRules.move(v[0], v[1], v[2], oldChamber), w);
			}
		}
		for (int i = 0; i < n; i++)
		{
			slotOf[nextCandidates[i]] = -1;
		}
		candidates = nextCandidates;
		weights = nextWeights;
		count = n;
		nextCandidates = null;
		nextWeights = null;
		normalise();
	}

	private int add(int n, int[] v, double w)
	{
		int packed = pack(v[0], v[1], v[2]);
		int slot = slotOf[packed];
		if (slot >= 0)
		{
			nextWeights[slot] += w;
			return n;
		}
		slotOf[packed] = n;
		nextCandidates[n] = packed;
		nextWeights[n] = w;
		return n + 1;
	}

	/** Keeps the weights summing to 1 (they only matter relative to each other). */
	private void normalise()
	{
		double total = 0;
		double max = 0;
		for (int i = 0; i < count; i++)
		{
			total += weights[i];
			max = Math.max(max, weights[i]);
		}
		if (total <= 0)
		{
			return;
		}
		// Only combinations vanishingly unlikely next to the most likely one are dropped - relative,
		// so a big set of individually small (but plausible) combinations is never thinned out.
		double cutoff = max * NEGLIGIBLE_WEIGHT;
		int kept = 0;
		for (int i = 0; i < count; i++)
		{
			double w = weights[i] / total;
			if (weights[i] >= cutoff)
			{
				weights[kept] = w;
				candidates[kept++] = candidates[i];
			}
		}
		count = kept;
		likelyCache = null;
	}

	private static int pack(int a, int b, int c)
	{
		return (a * 101 + b) * 101 + c;
	}

	private static int[] unpack(int packed)
	{
		return new int[]{packed / 10201, (packed / 101) % 101, packed % 101};
	}

	private static int clamp(int value)
	{
		return Math.max(0, Math.min(100, value));
	}
}
