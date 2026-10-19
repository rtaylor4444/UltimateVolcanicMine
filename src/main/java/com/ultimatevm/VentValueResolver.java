package com.ultimatevm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class VentValueResolver
{
	private VentValueResolver()
	{
	}

	static List<Integer> resolvePossibleValues(int rawVarbitValue, VentStatus predictedVent)
	{
		if (rawVarbitValue != VentStatus.STARTING_VENT_VALUE)
		{
			return Collections.singletonList(rawVarbitValue);
		}

		if (predictedVent == null)
		{
			return Collections.emptyList();
		}

		if (predictedVent.isIdentified())
		{
			return Collections.singletonList(predictedVent.getActualValue());
		}

		if (!predictedVent.isRangeDefined())
		{
			return Collections.emptyList();
		}

		Set<Integer> possible = new LinkedHashSet<>();
		addRangeToSet(possible, predictedVent.getLowerBoundStart(), predictedVent.getLowerBoundEnd());
		if (predictedVent.isTwoSeperateValues())
		{
			addRangeToSet(possible, predictedVent.getUpperBoundStart(), predictedVent.getUpperBoundEnd());
		}

		List<Integer> values = new ArrayList<>(possible);
		Collections.sort(values);
		return values;
	}

	static void addRangeToSet(Set<Integer> target, int start, int end)
	{
		int min = Math.max(VentStatus.MIN_VENT_VALUE, Math.min(start, end));
		int max = Math.min(VentStatus.MAX_VENT_VALUE, Math.max(start, end));
		for (int value = min; value <= max; value++)
		{
			target.add(value);
		}
	}
}
