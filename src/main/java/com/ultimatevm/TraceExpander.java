package com.ultimatevm;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Turns a coach decision record's predictor into its inputs (stability, chambers, packed vents) for
 * the trace, writing a vent list only when it differs from the last one written ("candsFrom" points
 * back at that record's id otherwise). Runs on the trace's own thread; one per game file.
 */
final class TraceExpander
{
	static final String PREDICTOR = "predictor";

	private int lastKey;
	private Object lastId;

	void reset()
	{
		lastKey = 0;
		lastId = null;
	}

	Map<String, Object> expand(Map<String, Object> line)
	{
		Object p = line.get(PREDICTOR);
		if (!(p instanceof StrategyPredictor))
		{
			return line;
		}
		Map<String, Object> out = new LinkedHashMap<>(line);
		out.remove(PREDICTOR);
		Map<String, Object> fields = ((StrategyPredictor) p).traceFields();
		Object cands = fields.get("cands");
		if (cands != null)
		{
			int key = 31 * cands.hashCode() + fields.get("w").hashCode();
			if (lastId != null && key == lastKey)
			{
				fields.remove("cands");
				fields.remove("w");
				fields.put("candsFrom", lastId);
			}
			else
			{
				lastKey = key;
				lastId = line.get("id");
			}
		}
		out.putAll(fields);
		return out;
	}
}
