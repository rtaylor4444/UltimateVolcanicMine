package com.ultimatevm;

import com.google.gson.Gson;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

public final class Simulator
{
	private static final int FIVE_MINUTE_MARK_TIME = 300;
	private static final int DEFAULT_MAX_TIME = 300;
	private static final int EXTENDED_MAX_TIME = 555; // 0:45, as the 0:30 change always sets stab to 5%
	private static final int MIN_STABILITY = 0;
	private static final int MAX_STABILITY = 100;
	private static final String NO_VENT_STABILITY_RESOURCE = "stability_change.json";
	private static final Gson GSON = new Gson();
	private static volatile StabilityPatternPredictor noVentStabilityPredictor;

	// Chamber-status bitmask, shared with StatusState.getDirectionFromChambers(index, ...).
	public static final int CHAMBER_A_BIT = 1;
	public static final int CHAMBER_B_BIT = 2;
	public static final int CHAMBER_C_BIT = 4;

	private Simulator()
	{
	}

	public static SimulationResult simulate(int a, int b, int c, int chamberStatus, int stability, int time)
	{
		List<StabilityPoint> stabilityOverTime = new ArrayList<>();
		int clampedStability = clamp(stability, MIN_STABILITY, MAX_STABILITY);
		int startTime = clamp(time, 0, EXTENDED_MAX_TIME);
		int maxTime = getMaxTimeForStart(startTime);
		boolean willDie = false;

		for (int currentTime = startTime; currentTime <= maxTime; currentTime++)
		{
			int timeLeft = maxTime - currentTime;

			if (timeLeft % 6 == 0)
			{
				int[] vents = updateVents(a, b, c, chamberStatus);
				a = vents[0];
				b = vents[1];
				c = vents[2];
			}

			if (timeLeft % 15 == 0)
			{
				int change = calculateStability(a, b, c);
				clampedStability = clamp(clampedStability + change, MIN_STABILITY, MAX_STABILITY);
				stabilityOverTime.add(new StabilityPoint(currentTime, clampedStability));
			}

			if (clampedStability <= 0)
			{
				willDie = true;
				break;
			}
		}

		return new SimulationResult(willDie, stabilityOverTime);
	}

	public static NoVentPatternResult simulateNoKnownVentsFromPatterns(int stability, int time, List<Integer> negativeTail)
	{
		if (negativeTail == null || negativeTail.isEmpty())
		{
			return new NoVentPatternResult(0, 0.0, Collections.emptyList(), false, false, false, 0);
		}
		return simulateNoKnownVentsFromPatterns(stability, time, negativeTail, getNoVentStabilityPredictor());
	}

	@Deprecated
	public static NoVentPatternResult simulateNoKnownVentsFromPatterns(int stability, int time, List<Integer> negativeTail, boolean flaggedLookup)
	{
		return simulateNoKnownVentsFromPatterns(stability, time, negativeTail);
	}

	static NoVentPatternResult simulateNoKnownVentsFromPatterns(
		int stability,
		int time,
		List<Integer> negativeTail,
		StabilityPatternPredictor predictor)
	{
		int clampedStability = clamp(stability, MIN_STABILITY, MAX_STABILITY);
		int startTime = clamp(time, 0, EXTENDED_MAX_TIME);
		int maxTime = getMaxTimeForStart(startTime);

		if (negativeTail == null || negativeTail.isEmpty())
		{
			return new NoVentPatternResult(0, 0.0, Collections.emptyList(), false, false, false, 0);
		}

		List<StabilityPatternPredictor.Match> matches = predictor.matchingPatterns(negativeTail);
		if (matches.isEmpty())
		{
			return new NoVentPatternResult(0, 0.0, Collections.emptyList(), false, false, false, 0);
		}

		boolean usedWindowFallback = !predictor.hasExactPrefix(negativeTail);
		int matchedTailLength = usedWindowFallback ? 3 : negativeTail.size();
		List<Integer> futureTimes = new ArrayList<>();
		for (int currentTime = startTime; currentTime <= maxTime; currentTime++)
		{
			int timeLeft = maxTime - currentTime;
			if (currentTime > startTime && timeLeft % 15 == 0)
			{
				futureTimes.add(currentTime);
			}
		}

		int[] scenarioStabilities = new int[matches.size()];
		boolean[] scenarioDied = new boolean[matches.size()];
		int matchedScenarioCount = 0;
		boolean usedInteriorMatch = false;
		for (int i = 0; i < matches.size(); i++)
		{
			StabilityPatternPredictor.Match match = matches.get(i);
			scenarioStabilities[i] = clampedStability;
			matchedScenarioCount += match.count;
			usedInteriorMatch |= match.nextIndex > 3;
		}

		List<StabilityRangePoint> stabilityRanges = new ArrayList<>();
		for (int step = 0; step < futureTimes.size(); step++)
		{
			int minStability = Integer.MAX_VALUE;
			int maxStability = Integer.MIN_VALUE;
			boolean hasAnyScenarioAtStep = false;

			for (int i = 0; i < matches.size(); i++)
			{
				StabilityPatternPredictor.Match match = matches.get(i);
				int patternIndex = match.nextIndex + step;
				if (patternIndex >= match.pattern.size())
				{
					continue;
				}

				hasAnyScenarioAtStep = true;
				int change = match.pattern.get(patternIndex);
				scenarioStabilities[i] = clamp(scenarioStabilities[i] + change, MIN_STABILITY, MAX_STABILITY);
				if (scenarioStabilities[i] <= 0)
				{
					scenarioDied[i] = true;
				}

				minStability = Math.min(minStability, scenarioStabilities[i]);
				maxStability = Math.max(maxStability, scenarioStabilities[i]);
			}

			if (!hasAnyScenarioAtStep)
			{
				break;
			}

			stabilityRanges.add(new StabilityRangePoint(futureTimes.get(step), minStability, maxStability));
		}

		int deathCount = 0;
		for (int i = 0; i < scenarioDied.length; i++)
		{
			if (scenarioDied[i])
			{
				deathCount += matches.get(i).count;
			}
		}

		double blowUpChancePercent = matchedScenarioCount == 0
			? 0.0
			: (deathCount * 100.0) / matchedScenarioCount;

		return new NoVentPatternResult(
			matchedScenarioCount,
			blowUpChancePercent,
			stabilityRanges,
			false,
			false,
			usedWindowFallback && usedInteriorMatch,
			matchedTailLength);
	}

	static StabilityPatternPredictor getNoVentStabilityPredictor()
	{
		StabilityPatternPredictor cached = noVentStabilityPredictor;
		if (cached != null)
		{
			return cached;
		}

		synchronized (Simulator.class)
		{
			if (noVentStabilityPredictor == null)
			{
				noVentStabilityPredictor = loadNoVentStabilityPredictor();
			}
			return noVentStabilityPredictor;
		}
	}

	private static StabilityPatternPredictor loadNoVentStabilityPredictor()
	{
		InputStream stream = Simulator.class.getClassLoader().getResourceAsStream(NO_VENT_STABILITY_RESOURCE);
		if (stream == null)
		{
			throw new IllegalStateException("Missing stability pattern resource: " + NO_VENT_STABILITY_RESOURCE);
		}

		try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8))
		{
			CompressedStabilityPatterns data = GSON.fromJson(reader, CompressedStabilityPatterns.class);
			if (data == null || data.patterns == null || data.counts == null)
			{
				throw new IllegalStateException("Invalid stability pattern resource: missing patterns or counts");
			}
			return new StabilityPatternPredictor(data.patterns, data.counts);
		}
		catch (java.io.IOException ex)
		{
			throw new IllegalStateException("Unable to read stability pattern resource: " + NO_VENT_STABILITY_RESOURCE, ex);
		}
		catch (RuntimeException ex)
		{
			throw new IllegalStateException("Unable to parse stability pattern resource: " + NO_VENT_STABILITY_RESOURCE, ex);
		}
	}

	private static final class CompressedStabilityPatterns
	{
		private List<List<Integer>> patterns;
		private List<Integer> counts;
	}

	public static VentSweepResult simulateUnknownVent(int unknownVentIndex, int[] knownVentValues, List<Integer> possibleUnknownValues, int chamberStatus, int stability, int time)
	{
		if (possibleUnknownValues == null || possibleUnknownValues.isEmpty())
		{
			return new VentSweepResult(unknownVentIndex, Collections.emptyList(), 0.0, Collections.emptyList());
		}

		List<VentUnknownInput> unknownInputs = Collections.singletonList(
			new VentUnknownInput(unknownVentIndex, possibleUnknownValues));
		MultiVentSweepResult result = simulateUnknownVents(
			knownVentValues,
			unknownInputs,
			chamberStatus,
			stability,
			time);

		List<VentScenarioResult> scenarios = new ArrayList<>();
		for (VentCombinationScenarioResult scenario : result.scenarios)
		{
			scenarios.add(new VentScenarioResult(scenario.ventValues[unknownVentIndex], scenario.result));
		}

		return new VentSweepResult(
			unknownVentIndex,
			scenarios,
			result.blowUpChancePercent,
			result.stabilityRangeOverTime);
	}

	public static MultiVentSweepResult simulateUnknownVents(
		int[] knownVentValues,
		List<VentUnknownInput> unknownInputs,
		int chamberStatus,
		int stability,
		int time)
	{
		if (knownVentValues == null || knownVentValues.length != 3)
		{
			throw new IllegalArgumentException("knownVentValues must have exactly 3 elements");
		}

		if (unknownInputs == null || unknownInputs.isEmpty())
		{
			return new MultiVentSweepResult(Collections.emptyList(), 0.0, Collections.emptyList());
		}

		List<VentUnknownInput> normalizedInputs = new ArrayList<>();
		for (VentUnknownInput input : unknownInputs)
		{
			if (input == null || input.ventIndex < 0 || input.ventIndex >= 3)
			{
				throw new IllegalArgumentException("unknown vent index must be in range [0, 2]");
			}

			if (input.possibleValues == null || input.possibleValues.isEmpty())
			{
				return new MultiVentSweepResult(Collections.emptyList(), 0.0, Collections.emptyList());
			}

			TreeSet<Integer> dedupedAndSortedValues = new TreeSet<>(input.possibleValues);
			normalizedInputs.add(new VentUnknownInput(input.ventIndex, new ArrayList<>(dedupedAndSortedValues)));
		}

		int startTime = clamp(time, 0, EXTENDED_MAX_TIME);
		int maxTime = getMaxTimeForStart(startTime);
		List<VentCombinationScenarioResult> scenarios = new ArrayList<>();
		int[] workingVents = knownVentValues.clone();

		buildUnknownVentScenarios(
			scenarios,
			workingVents,
			normalizedInputs,
			0,
			chamberStatus,
			stability,
			startTime);

		int deathCount = 0;
		for (VentCombinationScenarioResult scenario : scenarios)
		{
			if (scenario.result.willDie)
			{
				deathCount++;
			}
		}

		double blowUpChancePercent = scenarios.isEmpty()
			? 0.0
			: (deathCount * 100.0) / scenarios.size();

		List<Integer> times = getStabilityUpdateTimes(startTime, maxTime);
		List<StabilityRangePoint> stabilityRanges = new ArrayList<>();

		for (int currentTime : times)
		{
			int minStability = Integer.MAX_VALUE;
			int maxStability = Integer.MIN_VALUE;

			for (VentCombinationScenarioResult scenario : scenarios)
			{
				int stabilityAtTime = getStabilityAtTime(scenario.result, currentTime, stability);
				minStability = Math.min(minStability, stabilityAtTime);
				maxStability = Math.max(maxStability, stabilityAtTime);
			}

			if (minStability != Integer.MAX_VALUE)
			{
				stabilityRanges.add(new StabilityRangePoint(currentTime, minStability, maxStability));
			}
		}

		return new MultiVentSweepResult(scenarios, blowUpChancePercent, stabilityRanges);
	}

	private static void buildUnknownVentScenarios(
		List<VentCombinationScenarioResult> scenarios,
		int[] workingVents,
		List<VentUnknownInput> unknownInputs,
		int inputIndex,
		int chamberStatus,
		int stability,
		int startTime)
	{
		if (inputIndex >= unknownInputs.size())
		{
			SimulationResult result = simulate(
				workingVents[0],
				workingVents[1],
				workingVents[2],
				chamberStatus,
				stability,
				startTime);
			scenarios.add(new VentCombinationScenarioResult(workingVents.clone(), result));
			return;
		}

		VentUnknownInput input = unknownInputs.get(inputIndex);
		for (int value : input.possibleValues)
		{
			workingVents[input.ventIndex] = value;
			buildUnknownVentScenarios(
				scenarios,
				workingVents,
				unknownInputs,
				inputIndex + 1,
				chamberStatus,
				stability,
				startTime);
		}
	}

	/**
	 * Generalizes {@link #simulateUnknownVents} to a chain of one or more scheduled chamber flips
	 * (each toggling a bit of {@code chamberStatus} at a given absolute time), evaluated across
	 * every combination of the given unknown vents. Used for multi-vent fix scenarios (e.g. fixing
	 * A then B at different times) where a single before/after flip isn't enough.
	 */
	public static MultiVentSweepResult simulateUnknownVentsWithFlips(
		int[] knownVentValues,
		List<VentUnknownInput> unknownInputs,
		int chamberStatus,
		int stability,
		int time,
		List<FlipEvent> flipEvents)
	{
		if (knownVentValues == null || knownVentValues.length != 3)
		{
			throw new IllegalArgumentException("knownVentValues must have exactly 3 elements");
		}

		List<FlipEvent> sortedEvents = new ArrayList<>(flipEvents == null ? Collections.emptyList() : flipEvents);
		Collections.sort(sortedEvents, (left, right) -> Integer.compare(left.time, right.time));

		List<VentUnknownInput> normalizedInputs = new ArrayList<>();
		if (unknownInputs != null)
		{
			for (VentUnknownInput input : unknownInputs)
			{
				if (input == null || input.ventIndex < 0 || input.ventIndex >= 3)
				{
					throw new IllegalArgumentException("unknown vent index must be in range [0, 2]");
				}

				if (input.possibleValues == null || input.possibleValues.isEmpty())
				{
					return new MultiVentSweepResult(Collections.emptyList(), 0.0, Collections.emptyList());
				}

				TreeSet<Integer> dedupedAndSortedValues = new TreeSet<>(input.possibleValues);
				normalizedInputs.add(new VentUnknownInput(input.ventIndex, new ArrayList<>(dedupedAndSortedValues)));
			}
		}

		int startTime = clamp(time, 0, EXTENDED_MAX_TIME);
		List<VentCombinationScenarioResult> scenarios = new ArrayList<>();
		int[] workingVents = knownVentValues.clone();

		buildUnknownVentFlipScenarios(
			scenarios,
			workingVents,
			normalizedInputs,
			0,
			chamberStatus,
			stability,
			startTime,
			sortedEvents);

		int deathCount = 0;
		for (VentCombinationScenarioResult scenario : scenarios)
		{
			if (scenario.result.willDie)
			{
				deathCount++;
			}
		}

		double blowUpChancePercent = scenarios.isEmpty()
			? 0.0
			: (deathCount * 100.0) / scenarios.size();

		int maxTime = getMaxTimeForStart(startTime);
		List<Integer> times = getStabilityUpdateTimes(startTime, maxTime);
		List<StabilityRangePoint> stabilityRanges = new ArrayList<>();

		for (int currentTime : times)
		{
			int minStability = Integer.MAX_VALUE;
			int maxStability = Integer.MIN_VALUE;

			for (VentCombinationScenarioResult scenario : scenarios)
			{
				int stabilityAtTime = getStabilityAtTime(scenario.result, currentTime, stability);
				minStability = Math.min(minStability, stabilityAtTime);
				maxStability = Math.max(maxStability, stabilityAtTime);
			}

			if (minStability != Integer.MAX_VALUE)
			{
				stabilityRanges.add(new StabilityRangePoint(currentTime, minStability, maxStability));
			}
		}

		return new MultiVentSweepResult(scenarios, blowUpChancePercent, stabilityRanges);
	}

	private static void buildUnknownVentFlipScenarios(
		List<VentCombinationScenarioResult> scenarios,
		int[] workingVents,
		List<VentUnknownInput> unknownInputs,
		int inputIndex,
		int chamberStatus,
		int stability,
		int startTime,
		List<FlipEvent> sortedEvents)
	{
		if (inputIndex >= unknownInputs.size())
		{
			SimulationResult result = simulateSequentialPhases(
				workingVents[0],
				workingVents[1],
				workingVents[2],
				chamberStatus,
				stability,
				startTime,
				sortedEvents);
			scenarios.add(new VentCombinationScenarioResult(workingVents.clone(), result));
			return;
		}

		VentUnknownInput input = unknownInputs.get(inputIndex);
		for (int value : input.possibleValues)
		{
			workingVents[input.ventIndex] = value;
			buildUnknownVentFlipScenarios(
				scenarios,
				workingVents,
				unknownInputs,
				inputIndex + 1,
				chamberStatus,
				stability,
				startTime,
				sortedEvents);
		}
	}

	/**
	 * Runs {@link #simulate} in successive phases split at each flip event's time, carrying the
	 * vent/stability state (and any already-locked-in death, via getStabilityAtTime's post-death
	 * clamp-to-0 behavior) forward across the chamber toggle. Only the final phase's willDie matters
	 * for the overall result; the per-phase stability points are concatenated for display/range use.
	 */
	private static SimulationResult simulateSequentialPhases(
		int a, int b, int c, int chamberStatus, int stability, int startTime, List<FlipEvent> sortedEvents)
	{
		List<StabilityPoint> combinedPoints = new ArrayList<>();
		int currentA = a;
		int currentB = b;
		int currentC = c;
		int currentChamberStatus = chamberStatus;
		int currentStability = stability;
		int currentTime = startTime;
		SimulationResult lastPhase = null;

		for (FlipEvent event : sortedEvents)
		{
			if (event.time <= currentTime)
			{
				currentChamberStatus ^= event.chamberBit;
				continue;
			}

			SimulationResult phase = simulate(currentA, currentB, currentC, currentChamberStatus, currentStability, currentTime);
			combinedPoints.addAll(phase.stabilityOverTime);
			lastPhase = phase;

			currentStability = getStabilityAtTime(phase, event.time, currentStability);
			int[] ventsAtEvent = getVentValuesAtTime(currentA, currentB, currentC, currentChamberStatus, currentTime, event.time);
			currentA = ventsAtEvent[0];
			currentB = ventsAtEvent[1];
			currentC = ventsAtEvent[2];
			currentChamberStatus ^= event.chamberBit;
			currentTime = event.time;
		}

		SimulationResult finalPhase = simulate(currentA, currentB, currentC, currentChamberStatus, currentStability, currentTime);
		combinedPoints.addAll(finalPhase.stabilityOverTime);

		return new SimulationResult(finalPhase.willDie, combinedPoints);
	}

	public static int[] updateVents(int a, int b, int c, int chamberStatus)
	{
		int aDirection = StatusState.getDirectionFromChambers(0, chamberStatus);
		int bDirection = StatusState.getDirectionFromChambers(1, chamberStatus);
		int cDirection = StatusState.getDirectionFromChambers(2, chamberStatus);

		int cMovement = isInFreezeRange(c) ? 1 : 2;
		if (isInFreezeRange(b))
		{
			cMovement -= 1;
		}
		if (isInFreezeRange(a))
		{
			cMovement -= 1;
		}
		int newC = clamp(c + (cMovement * cDirection), MIN_STABILITY, MAX_STABILITY);

		int bMovement = isInFreezeRange(b) ? 1 : 2;
		if (isInFreezeRange(a))
		{
			bMovement -= 1;
		}
		int newB = clamp(b + (bMovement * bDirection), MIN_STABILITY, MAX_STABILITY);

		int aMovement = isInFreezeRange(a) ? 1 : 2;
		int newA = clamp(a + (aMovement * aDirection), MIN_STABILITY, MAX_STABILITY);

		return new int[]{newA, newB, newC};
	}

	public static int calculateStability(int a, int b, int c)
	{
		int totalVentInfluence = VentStatus.getStabilityInfluence(a)
			+ VentStatus.getStabilityInfluence(b)
			+ VentStatus.getStabilityInfluence(c);
		return StatusState.calcStabilityChange(totalVentInfluence);
	}

	public static boolean isInFreezeRange(int vent)
	{
		return vent >= 41 && vent <= 59;
	}

	private static int clamp(int value, int min, int max)
	{
		return Math.max(min, Math.min(max, value));
	}

	private static int getMaxTimeForStart(int startTime)
	{
		return startTime < FIVE_MINUTE_MARK_TIME ? DEFAULT_MAX_TIME : EXTENDED_MAX_TIME;
	}

	public static int[] getVentValuesAtTime(int a, int b, int c, int chamberStatus, int startTime, int targetTime)
	{
		int maxTime = getMaxTimeForStart(startTime);
		int clampedTarget = Math.min(targetTime, maxTime);
		for (int currentTime = startTime; currentTime < clampedTarget; currentTime++)
		{
			int timeLeft = maxTime - currentTime;
			if (timeLeft % 6 == 0)
			{
				int[] vents = updateVents(a, b, c, chamberStatus);
				a = vents[0];
				b = vents[1];
				c = vents[2];
			}
		}
		return new int[]{a, b, c};
	}

	private static List<Integer> getStabilityUpdateTimes(int startTime, int maxTime)
	{
		List<Integer> times = new ArrayList<>();
		for (int currentTime = startTime; currentTime <= maxTime; currentTime++)
		{
			int timeLeft = maxTime - currentTime;
			if (timeLeft % 15 == 0)
			{
				times.add(currentTime);
			}
		}
		return times;
	}

	private static int getStabilityAtTime(SimulationResult result, int time, int startingStability)
	{
		int stabilityAtTime = clamp(startingStability, MIN_STABILITY, MAX_STABILITY);
		int deathTime = -1;

		for (StabilityPoint point : result.stabilityOverTime)
		{
			if (point.stability <= 0 && deathTime == -1)
			{
				deathTime = point.time;
			}

			if (point.time <= time)
			{
				stabilityAtTime = point.stability;
			}
			else
			{
				break;
			}
		}

		if (deathTime != -1 && time > deathTime)
		{
			return 0;
		}

		return clamp(stabilityAtTime, MIN_STABILITY, MAX_STABILITY);
	}

	public static final class SimulationResult
	{
		public final boolean willDie;
		public final List<StabilityPoint> stabilityOverTime;

		public SimulationResult(boolean willDie, List<StabilityPoint> stabilityOverTime)
		{
			this.willDie = willDie;
			this.stabilityOverTime = Collections.unmodifiableList(new ArrayList<>(stabilityOverTime));
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

	public static final class VentScenarioResult
	{
		public final int unknownVentValue;
		public final SimulationResult result;

		public VentScenarioResult(int unknownVentValue, SimulationResult result)
		{
			this.unknownVentValue = unknownVentValue;
			this.result = result;
		}
	}

	public static final class VentCombinationScenarioResult
	{
		public final int[] ventValues;
		public final SimulationResult result;

		public VentCombinationScenarioResult(int[] ventValues, SimulationResult result)
		{
			this.ventValues = ventValues.clone();
			this.result = result;
		}
	}

	public static final class VentUnknownInput
	{
		public final int ventIndex;
		public final List<Integer> possibleValues;

		public VentUnknownInput(int ventIndex, List<Integer> possibleValues)
		{
			this.ventIndex = ventIndex;
			this.possibleValues = Collections.unmodifiableList(new ArrayList<>(possibleValues));
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

	public static final class VentSweepResult
	{
		public final int unknownVentIndex;
		public final List<VentScenarioResult> scenarios;
		public final double blowUpChancePercent;
		public final List<StabilityRangePoint> stabilityRangeOverTime;

		public VentSweepResult(
			int unknownVentIndex,
			List<VentScenarioResult> scenarios,
			double blowUpChancePercent,
			List<StabilityRangePoint> stabilityRangeOverTime)
		{
			this.unknownVentIndex = unknownVentIndex;
			this.scenarios = Collections.unmodifiableList(new ArrayList<>(scenarios));
			this.blowUpChancePercent = blowUpChancePercent;
			this.stabilityRangeOverTime = Collections.unmodifiableList(new ArrayList<>(stabilityRangeOverTime));
		}

		public Map<Integer, SimulationResult> byVentValue()
		{
			Map<Integer, SimulationResult> output = new LinkedHashMap<>();
			for (VentScenarioResult scenario : scenarios)
			{
				output.put(scenario.unknownVentValue, scenario.result);
			}
			return output;
		}
	}

	public static final class MultiVentSweepResult
	{
		public final List<VentCombinationScenarioResult> scenarios;
		public final double blowUpChancePercent;
		public final List<StabilityRangePoint> stabilityRangeOverTime;

		public MultiVentSweepResult(
			List<VentCombinationScenarioResult> scenarios,
			double blowUpChancePercent,
			List<StabilityRangePoint> stabilityRangeOverTime)
		{
			this.scenarios = Collections.unmodifiableList(new ArrayList<>(scenarios));
			this.blowUpChancePercent = blowUpChancePercent;
			this.stabilityRangeOverTime = Collections.unmodifiableList(new ArrayList<>(stabilityRangeOverTime));
		}
	}

	public static final class NoVentPatternResult
	{
		// Weighted count of matching raw historical observations, not distinct patterns.
		public final int matchedScenarioCount;
		public final double blowUpChancePercent;
		public final List<StabilityRangePoint> stabilityRangeOverTime;
		public final boolean usedFlaggedLookup;
		public final boolean usedTrimmedTail;
		public final boolean usedInteriorMatch;
		public final int matchedTailLength;

		public NoVentPatternResult(
			int matchedScenarioCount,
			double blowUpChancePercent,
			List<StabilityRangePoint> stabilityRangeOverTime,
			boolean usedFlaggedLookup,
			boolean usedTrimmedTail,
			boolean usedInteriorMatch,
			int matchedTailLength)
		{
			this.matchedScenarioCount = matchedScenarioCount;
			this.blowUpChancePercent = blowUpChancePercent;
			this.stabilityRangeOverTime = Collections.unmodifiableList(new ArrayList<>(stabilityRangeOverTime));
			this.usedFlaggedLookup = usedFlaggedLookup;
			this.usedTrimmedTail = usedTrimmedTail;
			this.usedInteriorMatch = usedInteriorMatch;
			this.matchedTailLength = matchedTailLength;
		}
	}
}
