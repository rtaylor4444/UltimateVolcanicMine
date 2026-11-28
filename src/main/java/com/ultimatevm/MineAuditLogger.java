package com.ultimatevm;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;

/**
 * Temporary audit log for checking the mine status (stability) predictions against what really
 * happens. Everything goes to the RuneLite log (client.log) prefixed "UVM Audit".
 *
 * <ul>
 *     <li>STATE - every change of stability / vents / chamber status, with the tick and clock;</li>
 *     <li>STAB - each stability change; when all three vents are known, the formula
 *     (-25 + vent influence) is checked and the leftover (the random part) is reported;</li>
 *     <li>VENT - each known vent's move, the ticks since its last move, and what the movement rules
 *     (VentRules.move) expected;</li>
 *     <li>QUAKE - the earthquake chat message;</li>
 *     <li>PRED - each prediction the plugin makes (the timeline printed to chat);</li>
 *     <li>CHECK - when stability changes, the latest prediction for each look-ahead (1 = the next
 *     update, 2 = the one after...) is compared with the real value;</li>
 *     <li>SUMMARY - totals at the end of the game.</li>
 * </ul>
 */
@Slf4j
final class MineAuditLogger
{
	private static final String PREFIX = "UVM Audit | ";
	private static final int UNKNOWN = VentRules.UNKNOWN;
	private static final int TOTAL_GAME_TICKS = 1000;
	private static final float SECONDS_TO_TICKS = 1.666f;
	// Predictions older than this (simulation seconds) are no longer checked.
	private static final int MAX_CHECK_AGE_SECONDS = 75;
	private static final int MAX_HORIZON = 5;
	private static final int TIME_MATCH_TOLERANCE_SECONDS = 2;
	// An earthquake within this many ticks before an event is flagged on that event.
	private static final int RECENT_QUAKE_TICKS = 30;

	private static final class Prediction
	{
		final int tick;
		final int simTime;
		final int chamberStatus;
		final int quakeCount;
		final int ventResetCount;
		final String source;
		final List<Simulator.StabilityRangePoint> points;
		// Most likely value (no earthquake cancels anything); null when vents are unknown.
		List<Simulator.StabilityPoint> likely;

		Prediction(int tick, int simTime, int chamberStatus, int quakeCount, int ventResetCount, String source,
			List<Simulator.StabilityRangePoint> points)
		{
			this.tick = tick;
			this.simTime = simTime;
			this.chamberStatus = chamberStatus;
			this.quakeCount = quakeCount;
			this.ventResetCount = ventResetCount;
			this.source = source;
			this.points = points;
		}
	}

	private boolean active;
	private int tick;
	private int lastTimeRemaining;
	private int stability = -1;
	private final int[] vents = {UNKNOWN, UNKNOWN, UNKNOWN};
	private final int[] lastMoveTick = {-1, -1, -1};
	private int chamberStatus = -1;
	private int lastStabilityChangeTick = -1;
	private int lastQuakeTick = -1;
	private int quakeCount;
	private int ventResetCount;
	private final List<Prediction> predictions = new ArrayList<>();

	// Summary counters.
	private final int[] checksOk = new int[MAX_HORIZON + 1];
	private final int[] checksMiss = new int[MAX_HORIZON + 1];
	private final int[] cleanChecksOk = new int[MAX_HORIZON + 1];
	private final int[] cleanChecksMiss = new int[MAX_HORIZON + 1];
	private final java.util.TreeMap<Integer, Integer> randomPartCounts = new java.util.TreeMap<>();
	private int ventMovesChecked;
	private int ventMovesUnexpected;
	private int stabilityChanges;
	private final java.util.Set<Integer> quakeTicks = new java.util.HashSet<>();
	private final java.util.Set<Integer> loggedCancelledMoveTicks = new java.util.HashSet<>();
	private int cancelledMoves;
	private int cancelledMovesWithChat;
	// "Most likely" (no earthquake cancels anything) value checks, all-vents-known predictions only.
	private final int[] likelyExact = new int[MAX_HORIZON + 1];
	private final int[] likelyChecked = new int[MAX_HORIZON + 1];

	/** Called every game tick while the game is running. */
	void onTick(int timeRemaining, int serverTimeRemaining, int newStability, int ventA, int ventB, int ventC,
		int newChamberStatus, int players)
	{
		if (!active)
		{
			active = true;
			tick = 0;
			log.info(PREFIX + "GAME START | rem={} players={} stab={} vents={}/{}/{} chamber={}",
				timeRemaining, players, newStability, fmt(ventA), fmt(ventB), fmt(ventC), newChamberStatus);
		}
		else
		{
			++tick;
		}
		lastTimeRemaining = timeRemaining;

		int[] newVents = {ventA, ventB, ventC};
		int[] previousVents = vents.clone();
		boolean ventsChanged = previousVents[0] != ventA || previousVents[1] != ventB || previousVents[2] != ventC;
		boolean stabilityChanged = stability != -1 && newStability != stability;
		boolean chamberChanged = chamberStatus != -1 && newChamberStatus != chamberStatus;
		if (!ventsChanged && !stabilityChanged && !chamberChanged && stability != -1)
		{
			return;
		}

		log.info(PREFIX + "STATE | tick={} rem={} srv={} sim={}s stab={} vents={}/{}/{} chamber={} players={}{}",
			tick, timeRemaining, serverTimeRemaining, simTime(timeRemaining), newStability,
			fmt(ventA), fmt(ventB), fmt(ventC), newChamberStatus, players, quakeNote());

		if (chamberChanged)
		{
			log.info(PREFIX + "CHAMBER | tick={} rem={} {} -> {} (flipped {})", tick, timeRemaining, chamberStatus,
				newChamberStatus, flippedChambers(chamberStatus, newChamberStatus));
		}

		if (ventsChanged)
		{
			logVentMoves(previousVents, newVents, timeRemaining);
		}

		if (stabilityChanged)
		{
			logStabilityChange(newStability, previousVents, newVents, timeRemaining, players);
		}

		stability = newStability;
		System.arraycopy(newVents, 0, vents, 0, 3);
		chamberStatus = newChamberStatus;
	}

	void onEarthquake()
	{
		if (!active)
		{
			return;
		}
		lastQuakeTick = tick;
		++quakeCount;
		quakeTicks.add(tick);
		// The quake happens on the next game tick (the chat message arrives before it).
		int remAtQuake = lastTimeRemaining - 1;
		log.info(PREFIX + "QUAKE | tick={} rem={} sim={}s remMod15={} remMod30={} (move tick if 0) stab={} vents={}/{}/{} chamber={} ticksSinceStabChange={} ticksSinceMove={}/{}/{}",
			tick, lastTimeRemaining, simTime(lastTimeRemaining), Math.floorMod(remAtQuake, 15), Math.floorMod(remAtQuake, 30),
			stability, fmt(vents[0]), fmt(vents[1]), fmt(vents[2]),
			chamberStatus, since(lastStabilityChangeTick), since(lastMoveTick[0]), since(lastMoveTick[1]), since(lastMoveTick[2]));
	}

	void onPrediction(String source, int simTime, int currentStability, List<Simulator.StabilityRangePoint> points)
	{
		onPrediction(source, simTime, currentStability, points, null);
	}

	void onPrediction(String source, int simTime, int currentStability, List<Simulator.StabilityRangePoint> points,
		List<Simulator.StabilityPoint> likely)
	{
		if (!active || points == null || points.isEmpty())
		{
			return;
		}
		Prediction prediction = new Prediction(tick, simTime, chamberStatus, quakeCount, ventResetCount, source, new ArrayList<>(points));
		prediction.likely = likely == null ? null : new ArrayList<>(likely);
		predictions.add(prediction);
		StringBuilder timeline = new StringBuilder();
		for (int i = 0; i < points.size() && i < 12; i++)
		{
			Simulator.StabilityRangePoint p = points.get(i);
			timeline.append(i == 0 ? "" : ", ").append(p.time).append(':').append(range(p.minStability, p.maxStability));
		}
		log.info(PREFIX + "PRED | tick={} rem={} sim={}s src={} stab={} vents={}/{}/{} chamber={} timeline={}{}",
			tick, lastTimeRemaining, simTime, source, currentStability, fmt(vents[0]), fmt(vents[1]), fmt(vents[2]),
			chamberStatus, timeline, points.size() > 12 ? ", ..." : "");
	}

	/** The plugin reset its predictions at the 5-minute mark (vents are re-rolled by the game). */
	void onFiveMinuteReset()
	{
		if (!active)
		{
			return;
		}
		++ventResetCount;
		log.info(PREFIX + "FIVE-MINUTE RESET | tick={} rem={} stab={} vents={}/{}/{}", tick, lastTimeRemaining, stability,
			fmt(vents[0]), fmt(vents[1]), fmt(vents[2]));
	}

	/** Left the mine / game over: print the summary once. */
	void onGameEnd()
	{
		if (!active)
		{
			return;
		}
		StringBuilder horizons = new StringBuilder();
		for (int h = 1; h <= MAX_HORIZON; h++)
		{
			horizons.append(String.format(Locale.ROOT, " h%d: %d/%d ok (clean %d/%d)", h,
				checksOk[h], checksOk[h] + checksMiss[h], cleanChecksOk[h], cleanChecksOk[h] + cleanChecksMiss[h]));
		}
		StringBuilder likely = new StringBuilder();
		for (int h = 1; h <= MAX_HORIZON; h++)
		{
			likely.append(String.format(Locale.ROOT, " h%d: %d/%d", h, likelyExact[h], likelyChecked[h]));
		}
		log.info(PREFIX + "SUMMARY | ticks={} stabilityChanges={} quakes={} predictionChecks:{} | mostLikelyExact (clean, all vents known):{} | cancelledMoves={} (with chat earthquake {}) | randomPart(actual - formula) counts={} | ventMoves checked={} unexpected={}",
			tick, stabilityChanges, quakeCount, horizons, likely, cancelledMoves, cancelledMovesWithChat, randomPartCounts,
			ventMovesChecked, ventMovesUnexpected);
		reset();
	}

	private void reset()
	{
		active = false;
		stability = -1;
		chamberStatus = -1;
		for (int i = 0; i < 3; i++)
		{
			vents[i] = UNKNOWN;
			lastMoveTick[i] = -1;
		}
		lastStabilityChangeTick = -1;
		lastQuakeTick = -1;
		quakeCount = 0;
		ventResetCount = 0;
		predictions.clear();
		java.util.Arrays.fill(checksOk, 0);
		java.util.Arrays.fill(checksMiss, 0);
		java.util.Arrays.fill(cleanChecksOk, 0);
		java.util.Arrays.fill(cleanChecksMiss, 0);
		randomPartCounts.clear();
		ventMovesChecked = ventMovesUnexpected = stabilityChanges = 0;
		quakeTicks.clear();
		loggedCancelledMoveTicks.clear();
		cancelledMoves = cancelledMovesWithChat = 0;
		java.util.Arrays.fill(likelyExact, 0);
		java.util.Arrays.fill(likelyChecked, 0);
	}

	private void logVentMoves(int[] previous, int[] current, int timeRemaining)
	{
		// What the movement rules expect from the previous values (only meaningful for a vent whose
		// own value and the vents that slow it are all known: C depends on A and B, B on A).
		int[] expected = previous[0] != UNKNOWN && previous[1] != UNKNOWN && previous[2] != UNKNOWN
			? VentRules.move(previous[0], previous[1], previous[2], chamberStatus)
			: null;
		for (int i = 0; i < 3; i++)
		{
			if (previous[i] == current[i])
			{
				continue;
			}
			char name = (char) ('A' + i);
			if (previous[i] == UNKNOWN || current[i] == UNKNOWN)
			{
				log.info(PREFIX + "VENT {} | tick={} rem={} {} -> {} ({}){}", name, tick, timeRemaining, fmt(previous[i]),
					fmt(current[i]), current[i] == UNKNOWN ? "hidden again" : "checked", quakeNote());
				// A check isn't a move: gaps are only counted from the vent's next real move.
				lastMoveTick[i] = -1;
				continue;
			}

			int delta = current[i] - previous[i];
			String expectation = "n/a (other vents unknown)";
			if (expected != null)
			{
				int expectedDelta = expected[i] - previous[i];
				++ventMovesChecked;
				boolean ok = expectedDelta == delta;
				if (!ok)
				{
					++ventMovesUnexpected;
				}
				expectation = (ok ? "OK" : "UNEXPECTED") + " expected " + signed(expectedDelta);
			}
			log.info(PREFIX + "VENT {} | tick={} rem={} {} -> {} delta={} ticksSinceLastMove={} chamberDir={} | {}{}",
				name, tick, timeRemaining, previous[i], current[i], signed(delta), since(lastMoveTick[i]),
				VentRules.direction(i, chamberStatus) > 0 ? "up" : "down", expectation, quakeNote());
			// A gap of 20+ ticks means the move(s) in between didn't happen - but only for a vent that
			// always moves: A (unless stuck at 0/100), or B when A isn't holding it still.
			boolean alwaysMoves = previous[i] > 0 && previous[i] < 100
				&& (i == 0 || (i == 1 && previous[0] != UNKNOWN
					&& !(VentRules.inFreezeRange(previous[0]) && VentRules.inFreezeRange(previous[1]))));
			if (alwaysMoves && lastMoveTick[i] >= 0 && tick - lastMoveTick[i] >= 20)
			{
				for (int skipped = lastMoveTick[i] + 10; skipped < tick; skipped += 10)
				{
					logCancelledMove(skipped);
				}
			}
			lastMoveTick[i] = tick;
		}
	}

	private void logCancelledMove(int skippedTick)
	{
		if (!loggedCancelledMoveTicks.add(skippedTick))
		{
			return;
		}
		++cancelledMoves;
		// The earthquake chat message arrives just before that tick's game tick, so it's logged
		// with the previous tick number.
		boolean chatQuake = quakeTicks.contains(skippedTick - 1);
		if (chatQuake)
		{
			++cancelledMovesWithChat;
		}
		int remAtSkip = lastTimeRemaining + (tick - skippedTick);
		log.info(PREFIX + "CANCELLED MOVE | tick={} rem={} remMod15={} remMod30={} chatEarthquakeThatTick={}",
			skippedTick, remAtSkip, Math.floorMod(remAtSkip, 15), Math.floorMod(remAtSkip, 30), chatQuake ? "yes" : "no");
	}

	private void logStabilityChange(int newStability, int[] previousVents, int[] currentVents, int timeRemaining, int players)
	{
		++stabilityChanges;
		int delta = newStability - stability;
		boolean clamped = newStability == 0 || newStability == 100;
		String formula = "n/a (not all vents known)";
		if (allKnown(previousVents) || allKnown(currentVents))
		{
			StringBuilder sb = new StringBuilder();
			if (allKnown(previousVents))
			{
				int f = VentRules.stabilityChange(previousVents[0], previousVents[1], previousVents[2]);
				sb.append("formula(prev-tick vents)=").append(signed(f)).append(" random=").append(signed(delta - f));
				if (!clamped && VentRules.stabilityChange(currentVents[0], currentVents[1], currentVents[2]) == f)
				{
					randomPartCounts.merge(delta - f, 1, Integer::sum);
				}
			}
			if (allKnown(currentVents) && !java.util.Arrays.equals(previousVents, currentVents))
			{
				int f = VentRules.stabilityChange(currentVents[0], currentVents[1], currentVents[2]);
				sb.append(sb.length() > 0 ? " | " : "").append("formula(this-tick vents)=").append(signed(f))
					.append(" random=").append(signed(delta - f));
			}
			sb.append(String.format(Locale.ROOT, " | expected random range for %d player(s): %d..+1",
				players, -players / 3));
			formula = sb.toString();
		}
		log.info(PREFIX + "STAB | tick={} rem={} sim={}s {} -> {} delta={} ticksSinceLastChange={}{} | {}{}",
			tick, timeRemaining, simTime(timeRemaining), stability, newStability, signed(delta), since(lastStabilityChangeTick),
			clamped ? " (clamped)" : "", formula, quakeNote());
		lastStabilityChangeTick = tick;
		checkPredictions(simTime(timeRemaining), newStability);
	}

	private void checkPredictions(int actualSimTime, int actualStability)
	{
		Prediction[] latestByHorizon = new Prediction[MAX_HORIZON + 1];
		Simulator.StabilityRangePoint[] pointByHorizon = new Simulator.StabilityRangePoint[MAX_HORIZON + 1];
		for (Iterator<Prediction> it = predictions.iterator(); it.hasNext(); )
		{
			Prediction prediction = it.next();
			if (actualSimTime - prediction.simTime > MAX_CHECK_AGE_SECONDS)
			{
				it.remove();
				continue;
			}
			if (prediction.simTime >= actualSimTime)
			{
				continue;
			}
			for (int i = 0; i < prediction.points.size() && i < MAX_HORIZON; i++)
			{
				Simulator.StabilityRangePoint point = prediction.points.get(i);
				if (Math.abs(point.time - actualSimTime) <= TIME_MATCH_TOLERANCE_SECONDS)
				{
					int horizon = i + 1;
					// Later predictions replace earlier ones for the same look-ahead.
					latestByHorizon[horizon] = prediction;
					pointByHorizon[horizon] = point;
					break;
				}
			}
		}

		for (int h = 1; h <= MAX_HORIZON; h++)
		{
			Prediction prediction = latestByHorizon[h];
			if (prediction == null)
			{
				continue;
			}
			Simulator.StabilityRangePoint point = pointByHorizon[h];
			boolean ok = actualStability >= point.minStability && actualStability <= point.maxStability;
			int missBy = ok ? 0 : actualStability < point.minStability
				? actualStability - point.minStability
				: actualStability - point.maxStability;
			List<String> taints = new ArrayList<>();
			if (prediction.chamberStatus != chamberStatus)
			{
				taints.add("chamber changed " + prediction.chamberStatus + "->" + chamberStatus);
			}
			if (prediction.quakeCount != quakeCount)
			{
				taints.add((quakeCount - prediction.quakeCount) + " quake(s) since");
			}
			if (prediction.ventResetCount != ventResetCount)
			{
				taints.add("5-minute reset since");
			}
			boolean clean = taints.isEmpty();
			if (ok)
			{
				++checksOk[h];
				if (clean)
				{
					++cleanChecksOk[h];
				}
			}
			else
			{
				++checksMiss[h];
				if (clean)
				{
					++cleanChecksMiss[h];
				}
			}
			String likelyNote = "";
			if (prediction.likely != null)
			{
				for (Simulator.StabilityPoint likelyPoint : prediction.likely)
				{
					if (likelyPoint.time == point.time)
					{
						boolean exact = likelyPoint.stability == actualStability;
						if (clean)
						{
							++likelyChecked[h];
							if (exact)
							{
								++likelyExact[h];
							}
						}
						likelyNote = " | most likely " + likelyPoint.stability
							+ (exact ? " EXACT" : " off by " + signed(actualStability - likelyPoint.stability));
						break;
					}
				}
			}
			log.info(PREFIX + "CHECK | h={} predicted {} at {}s (made tick {} sim {}s src={}) actual {} at {}s -> {}{}{}",
				h, range(point.minStability, point.maxStability), point.time, prediction.tick, prediction.simTime,
				prediction.source, actualStability, actualSimTime, ok ? "OK" : "MISS by " + signed(missBy), likelyNote,
				clean ? "" : " [" + String.join(", ", taints) + "]");
		}
	}

	private String quakeNote()
	{
		if (lastQuakeTick < 0 || tick - lastQuakeTick > RECENT_QUAKE_TICKS)
		{
			return "";
		}
		return " [quake " + (tick - lastQuakeTick) + " ticks ago]";
	}

	private String since(int pastTick)
	{
		return pastTick < 0 ? "-" : String.valueOf(tick - pastTick);
	}

	private static int simTime(int timeRemaining)
	{
		int clamped = Math.max(0, Math.min(TOTAL_GAME_TICKS, timeRemaining));
		return Math.max(0, Math.round((TOTAL_GAME_TICKS - clamped) / SECONDS_TO_TICKS));
	}

	private static boolean allKnown(int[] values)
	{
		return values[0] != UNKNOWN && values[1] != UNKNOWN && values[2] != UNKNOWN;
	}

	private static String fmt(int vent)
	{
		return vent == UNKNOWN ? "?" : String.valueOf(vent);
	}

	private static String signed(int value)
	{
		return value > 0 ? "+" + value : String.valueOf(value);
	}

	private static String range(int min, int max)
	{
		return min == max ? "[" + min + "]" : "[" + min + "," + max + "]";
	}

	private static String flippedChambers(int oldStatus, int newStatus)
	{
		int diff = oldStatus ^ newStatus;
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < 3; i++)
		{
			if ((diff & (1 << i)) != 0)
			{
				sb.append((char) ('A' + i));
			}
		}
		return sb.length() == 0 ? "none" : sb.toString();
	}
}
