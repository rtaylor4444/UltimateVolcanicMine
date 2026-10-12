package com.ultimatevm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Indexes weighted stability histories by full prefixes and three-change windows.
 * Each window match retains its pattern position so repeated occurrences remain distinct.
 */
final class StabilityPatternPredictor
{
	private static final int WINDOW_SIZE = 3;

	private final Map<List<Integer>, List<Match>> prefixIndex;
	private final Map<List<Integer>, List<Match>> windowIndex;
	private final int patternCount;
	private final int occurrenceCount;

	StabilityPatternPredictor(List<List<Integer>> patterns, List<Integer> counts)
	{
		if (patterns == null || counts == null || patterns.size() != counts.size())
		{
			throw new IllegalArgumentException("patterns and counts must be non-null lists of equal length");
		}

		Map<List<Integer>, List<Match>> mutablePrefixIndex = new HashMap<>();
		Map<List<Integer>, List<Match>> mutableWindowIndex = new HashMap<>();
		int totalOccurrences = 0;

		for (int patternIndex = 0; patternIndex < patterns.size(); patternIndex++)
		{
			List<Integer> pattern = patterns.get(patternIndex);
			Integer count = counts.get(patternIndex);
			if (pattern == null || pattern.isEmpty() || count == null || count < 1)
			{
				throw new IllegalArgumentException("each pattern must be non-empty and have a positive count");
			}
			for (Integer change : pattern)
			{
				if (change == null)
				{
					throw new IllegalArgumentException("patterns cannot contain null changes");
				}
			}

			List<Integer> immutablePattern = Collections.unmodifiableList(new ArrayList<>(pattern));
			totalOccurrences += count;
			for (int prefixLength = 0; prefixLength <= immutablePattern.size(); prefixLength++)
			{
				List<Integer> prefix = immutablePattern.subList(0, prefixLength);
				addMatch(mutablePrefixIndex, prefix, new Match(immutablePattern, prefixLength, count));
			}
			for (int start = 0; start <= immutablePattern.size() - WINDOW_SIZE; start++)
			{
				List<Integer> window = immutablePattern.subList(start, start + WINDOW_SIZE);
				addMatch(mutableWindowIndex, window, new Match(immutablePattern, start + WINDOW_SIZE, count));
			}
		}

		prefixIndex = freeze(mutablePrefixIndex);
		windowIndex = freeze(mutableWindowIndex);
		patternCount = patterns.size();
		occurrenceCount = totalOccurrences;
	}

	List<Match> matchingPatterns(List<Integer> negativeStreak)
	{
		if (negativeStreak == null || negativeStreak.isEmpty())
		{
			return Collections.emptyList();
		}

		List<Match> exactMatches = prefixIndex.get(negativeStreak);
		if (exactMatches != null && !exactMatches.isEmpty())
		{
			return exactMatches;
		}

		if (negativeStreak.size() <= WINDOW_SIZE)
		{
			return Collections.emptyList();
		}

		List<Integer> trailingWindow = new ArrayList<>(
			negativeStreak.subList(negativeStreak.size() - WINDOW_SIZE, negativeStreak.size()));
		List<Match> windowMatches = windowIndex.get(trailingWindow);
		return windowMatches == null ? Collections.<Match>emptyList() : windowMatches;
	}

	boolean hasExactPrefix(List<Integer> negativeStreak)
	{
		return prefixIndex.containsKey(negativeStreak);
	}

	int getPatternCount()
	{
		return patternCount;
	}

	int getOccurrenceCount()
	{
		return occurrenceCount;
	}

	private static void addMatch(Map<List<Integer>, List<Match>> index, List<Integer> key, Match match)
	{
		List<Integer> immutableKey = Collections.unmodifiableList(new ArrayList<>(key));
		List<Match> matches = index.get(immutableKey);
		if (matches == null)
		{
			matches = new ArrayList<>();
			index.put(immutableKey, matches);
		}
		matches.add(match);
	}

	private static Map<List<Integer>, List<Match>> freeze(Map<List<Integer>, List<Match>> index)
	{
		Map<List<Integer>, List<Match>> frozenIndex = new HashMap<>();
		for (Map.Entry<List<Integer>, List<Match>> entry : index.entrySet())
		{
			frozenIndex.put(entry.getKey(), Collections.unmodifiableList(entry.getValue()));
		}
		return Collections.unmodifiableMap(frozenIndex);
	}

	static final class Match
	{
		final List<Integer> pattern;
		final int nextIndex;
		final int count;

		private Match(List<Integer> pattern, int nextIndex, int count)
		{
			this.pattern = pattern;
			this.nextIndex = nextIndex;
			this.count = count;
		}
	}
}
