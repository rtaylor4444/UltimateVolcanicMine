package com.ultimatevm;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

public class StabilityPatternPredictorTest
{
	@Test
	public void loadsCompressedDatasetWithOriginalOccurrenceCount()
	{
		Simulator simulator = new Simulator(new Gson());
		StabilityPatternPredictor predictor = simulator.getNoVentStabilityPredictor();

		Assert.assertEquals(6860, predictor.getPatternCount());
		Assert.assertEquals(46541, predictor.getOccurrenceCount());
		Assert.assertSame(predictor, simulator.getNoVentStabilityPredictor());
	}

	@Test(expected = IllegalStateException.class)
	public void usesSuppliedGsonToLoadPatterns()
	{
		Gson gson = new GsonBuilder()
			.setFieldNamingStrategy(field -> "unused_" + field.getName())
			.create();
		Simulator simulator = new Simulator(gson);

		simulator.getNoVentStabilityPredictor();
	}

	@Test
	public void simulatesNoKnownVentsUsingLoadedPatterns()
	{
		Simulator simulator = new Simulator(new Gson());
		Simulator.NoVentPatternResult result = simulator.simulateNoKnownVentsFromPatterns(
			100, 270, Collections.singletonList(-1));

		Assert.assertTrue(result.matchedScenarioCount > 0);
		Assert.assertFalse(result.stabilityRangeOverTime.isEmpty());
	}

	@Test
	public void matchesFullStreakBeforeLookingForTrailingWindow()
	{
		StabilityPatternPredictor predictor = predictor(
			Arrays.asList(
				Arrays.asList(-1, -2, -3, -4),
				Arrays.asList(-9, -1, -2, -3, -5)),
			Arrays.asList(2, 1));

		List<StabilityPatternPredictor.Match> matches =
			predictor.matchingPatterns(Arrays.asList(-1, -2, -3));

		Assert.assertEquals(1, matches.size());
		Assert.assertEquals(Arrays.asList(-1, -2, -3, -4), matches.get(0).pattern);
		Assert.assertEquals(3, matches.get(0).nextIndex);
		Assert.assertEquals(2, matches.get(0).count);
	}

	@Test
	public void fallsBackOnlyToLastThreeChangesForStreaksLongerThanThree()
	{
		StabilityPatternPredictor predictor = predictor(
			Collections.singletonList(Arrays.asList(-1, -2, -3, -4)),
			Collections.singletonList(1));

		Assert.assertTrue(predictor.matchingPatterns(Arrays.asList(-9, -1, -2)).isEmpty());
		List<StabilityPatternPredictor.Match> matches =
			predictor.matchingPatterns(Arrays.asList(-9, -1, -2, -3));

		Assert.assertEquals(1, matches.size());
		Assert.assertEquals(3, matches.get(0).nextIndex);
	}

	@Test
	public void doesNotRetryWithShorterSuffixAfterTrailingWindowMisses()
	{
		StabilityPatternPredictor predictor = predictor(
			Collections.singletonList(Arrays.asList(-2, -3, -4)),
			Collections.singletonList(1));

		Assert.assertTrue(
			predictor.matchingPatterns(Arrays.asList(-9, -2, -3, -8)).isEmpty());
	}

	@Test
	public void retainsEveryOccurrenceOfRepeatedWindow()
	{
		StabilityPatternPredictor predictor = predictor(
			Collections.singletonList(Arrays.asList(-1, -2, -3, -9, -1, -2, -3, -4)),
			Collections.singletonList(2));

		List<StabilityPatternPredictor.Match> matches =
			predictor.matchingPatterns(Arrays.asList(-7, -1, -2, -3));

		Assert.assertEquals(2, matches.size());
		Assert.assertEquals(3, matches.get(0).nextIndex);
		Assert.assertEquals(7, matches.get(1).nextIndex);
		Assert.assertEquals(2, matches.get(0).count);
		Assert.assertEquals(2, matches.get(1).count);
	}

	@Test
	public void weightsRiskAndIncludesFiveMinuteEndpoint()
	{
		StabilityPatternPredictor predictor = predictor(
			Arrays.asList(
				Arrays.asList(-1, -3, -5, -4),
				Arrays.asList(-1, -3, -2, -2)),
			Arrays.asList(3, 1));

		Simulator.NoVentPatternResult result = Simulator.simulateNoKnownVentsFromPatterns(
			8, 270, Arrays.asList(-1, -3), predictor);

		Assert.assertEquals(4, result.matchedScenarioCount);
		Assert.assertEquals(75.0, result.blowUpChancePercent, 0.0);
		Assert.assertEquals(2, result.stabilityRangeOverTime.size());
		Assert.assertEquals(285, result.stabilityRangeOverTime.get(0).time);
		Assert.assertEquals(300, result.stabilityRangeOverTime.get(1).time);
		Assert.assertEquals(0, result.stabilityRangeOverTime.get(1).minStability);
		Assert.assertEquals(4, result.stabilityRangeOverTime.get(1).maxStability);
	}

	@Test
	public void includesFinalSecondHalfUpdateAtFortyFiveSecondsRemaining()
	{
		StabilityPatternPredictor predictor = predictor(
			Collections.singletonList(Arrays.asList(-1, -3, -8)),
			Collections.singletonList(1));

		Simulator.NoVentPatternResult result = Simulator.simulateNoKnownVentsFromPatterns(
			5, 540, Arrays.asList(-1, -3), predictor);

		Assert.assertEquals(100.0, result.blowUpChancePercent, 0.0);
		Assert.assertEquals(1, result.stabilityRangeOverTime.size());
		Assert.assertEquals(555, result.stabilityRangeOverTime.get(0).time);
		Assert.assertEquals(0, result.stabilityRangeOverTime.get(0).minStability);
	}

	@Test
	public void ignoresZeroChangesButEndsStreakOnPositiveChange()
	{
		StabilityTracker tracker = new StabilityTracker();
		tracker.addChange(-1);
		tracker.addChange(-3);
		tracker.addChange(-5);
		tracker.addChange(0);
		tracker.addChange(-7);

		Assert.assertEquals(Arrays.asList(-1, -3, -5, -7), tracker.getConsecutiveNegativeTailOldestFirst());
		Assert.assertEquals(4, tracker.getConsecutiveNegativeChangeCount());

		tracker.addChange(1);
		tracker.addChange(-2);
		Assert.assertEquals(Collections.singletonList(-2), tracker.getConsecutiveNegativeTailOldestFirst());
	}

	@Test
	public void chamberChangeDisablesPredictionUntilANewNegativeChange()
	{
		StabilityTracker tracker = new StabilityTracker();
		tracker.addChange(-4);
		tracker.invalidatePatternHistory();

		Assert.assertTrue(tracker.getConsecutiveNegativeTailOldestFirst().isEmpty());
		Assert.assertEquals(-4, tracker.getCurrentChange());
		Assert.assertFalse(tracker.isPatternPredictionEnabled());

		tracker.addChange(0);
		tracker.addChange(2);
		Assert.assertFalse(tracker.isPatternPredictionEnabled());

		tracker.addChange(-3);
		Assert.assertTrue(tracker.isPatternPredictionEnabled());
		Assert.assertEquals(Collections.singletonList(-3), tracker.getConsecutiveNegativeTailOldestFirst());
	}

	private static StabilityPatternPredictor predictor(List<List<Integer>> patterns, List<Integer> counts)
	{
		return new StabilityPatternPredictor(patterns, counts);
	}
}
