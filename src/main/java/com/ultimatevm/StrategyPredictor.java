package com.ultimatevm;

import java.util.Collections;
import java.util.List;

/**
 * What the strategy asks of the vent inference, as in the simulator's VentTracker: the weighted
 * share of the still-possible vents that blow the mine up by a time (no earthquakes, optionally with
 * planned chamber fixes), the earliest such time, and how likely a vent is heading away from 50.
 */
final class StrategyPredictor
{
	private final List<int[]> candidates;
	private final double[] weights;
	private final double totalWeight;
	final int chamberStatus;
	final int stability;
	/** First simulation second still to come. */
	final int time;

	StrategyPredictor(List<int[]> candidates, double[] weights, int chamberStatus, int stability, int time)
	{
		this.candidates = candidates;
		this.weights = weights;
		double total = 0;
		for (double w : weights)
		{
			total += w;
		}
		this.totalWeight = total;
		this.chamberStatus = chamberStatus;
		this.stability = stability;
		this.time = time;
	}

	/** Largest candidate list written into a trace record (packed: ~12 bytes a candidate). */
	static final int MAX_TRACED_CANDIDATES = 140000;

	/**
	 * The inputs, for the coach trace (so each decision can be re-run in the simulator). Candidates
	 * are packed as base64: little-endian int32 keys (a * 101 + b) * 101 + c, and float64 weights.
	 */
	java.util.Map<String, Object> traceFields()
	{
		java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
		m.put("stability", stability);
		m.put("chamber", chamberStatus);
		m.put("startSecond", time);
		m.put("n", candidates.size());
		if (candidates.size() <= MAX_TRACED_CANDIDATES)
		{
			java.nio.ByteBuffer keys = java.nio.ByteBuffer.allocate(candidates.size() * 4).order(java.nio.ByteOrder.LITTLE_ENDIAN);
			java.nio.ByteBuffer w = java.nio.ByteBuffer.allocate(weights.length * 8).order(java.nio.ByteOrder.LITTLE_ENDIAN);
			for (int i = 0; i < candidates.size(); i++)
			{
				int[] v = candidates.get(i);
				keys.putInt((v[0] * 101 + v[1]) * 101 + v[2]);
				w.putDouble(weights[i]);
			}
			m.put("cands", gzipBase64(keys.array()));
			m.put("w", gzipBase64(w.array()));
		}
		return m;
	}

	private static String gzipBase64(byte[] bytes)
	{
		java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(bytes.length / 4 + 64);
		try (java.util.zip.GZIPOutputStream gzip = new java.util.zip.GZIPOutputStream(out))
		{
			gzip.write(bytes);
		}
		catch (java.io.IOException e)
		{
			throw new java.io.UncheckedIOException(e);
		}
		return java.util.Base64.getEncoder().encodeToString(out.toByteArray());
	}

	boolean isEmpty()
	{
		return candidates.isEmpty() || totalWeight <= 0;
	}

	int size()
	{
		return candidates.size();
	}

	/** Weighted share (0-1) blowing up by endTime with these fixes. */
	double deathRate(List<Simulator.FlipEvent> flips, int endTime)
	{
		if (isEmpty() || endTime < time)
		{
			return 0;
		}
		int[] times = Simulator.blowUpTimes(candidates, chamberStatus, stability, time, endTime, flips);
		double dead = 0;
		for (int i = 0; i < times.length; i++)
		{
			if (times[i] <= endTime)
			{
				dead += weights[i];
			}
		}
		return dead / totalWeight;
	}

	double deathRate(int endTime)
	{
		return deathRate(Collections.emptyList(), endTime);
	}

	/**
	 * Cumulative blow-up share at each stability update from now to endTime (index i = the i-th
	 * update second from {@link #firstUpdateAtOrAfter}), with these fixes.
	 */
	double[] cumulativeDeathByUpdate(List<Simulator.FlipEvent> flips, int endTime)
	{
		int first = firstUpdateAtOrAfter(time);
		int n = first > endTime ? 0 : (endTime - first) / 15 + 1;
		double[] out = new double[n];
		if (isEmpty() || n == 0)
		{
			return out;
		}
		int[] times = Simulator.blowUpTimes(candidates, chamberStatus, stability, time, endTime, flips);
		for (int i = 0; i < times.length; i++)
		{
			if (times[i] <= endTime)
			{
				int index = Math.max(0, (times[i] - first + 14) / 15);
				if (index < n)
				{
					out[index] += weights[i] / totalWeight;
				}
			}
		}
		for (int i = 1; i < n; i++)
		{
			out[i] += out[i - 1];
		}
		return out;
	}

	static int firstUpdateAtOrAfter(int t)
	{
		return Math.max(15, (t + 14) / 15 * 15);
	}

	/**
	 * The first update second by which more than {@code risk} of the weight has blown up (no more
	 * fixes), or null. risk 0 = the earliest blow-up of any still-possible set of vents.
	 */
	Integer deathTime(int endTime, double risk)
	{
		return deathTime(endTime, risk, Collections.emptyList());
	}

	/** As {@link #deathTime(int, double)}, with these chamber flips. */
	Integer deathTime(int endTime, double risk, List<Simulator.FlipEvent> flips)
	{
		if (isEmpty())
		{
			return null;
		}
		int[] times = Simulator.blowUpTimes(candidates, chamberStatus, stability, time, endTime, flips);
		int first = firstUpdateAtOrAfter(time);
		for (int t = first; t <= endTime; t += 15)
		{
			double dead = 0;
			for (int i = 0; i < times.length; i++)
			{
				if (times[i] <= t)
				{
					dead += weights[i];
				}
			}
			if (dead > risk * totalWeight + 1e-12)
			{
				return t;
			}
		}
		return null;
	}

	/**
	 * The chance vent (0 = A) is heading away from 50: below 50 and falling (chamber open) or above
	 * 50 and rising (chamber blocked) - the simulator's "effective value below 50".
	 */
	double pHeadingAway(int vent)
	{
		if (isEmpty())
		{
			return 0;
		}
		boolean blocked = (chamberStatus & (1 << vent)) != 0;
		double away = 0;
		for (int i = 0; i < candidates.size(); i++)
		{
			int v = candidates.get(i)[vent];
			int effective = blocked ? 100 - v : v;
			if (effective < 50)
			{
				away += weights[i];
			}
		}
		return away / totalWeight;
	}

	/** True when every still-possible set of vents agrees on this vent's value. */
	boolean isKnown(int vent)
	{
		if (candidates.isEmpty())
		{
			return false;
		}
		int value = candidates.get(0)[vent];
		for (int[] v : candidates)
		{
			if (v[vent] != value)
			{
				return false;
			}
		}
		return true;
	}
}
