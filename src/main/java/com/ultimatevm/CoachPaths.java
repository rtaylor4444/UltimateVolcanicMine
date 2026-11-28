package com.ultimatevm;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * The stock routes between the places the coach sends the player (coach_paths.json, exported from
 * the simulator's map model by volcanicMine/map/export_coach_paths.py): tiles as offsets from the
 * West staircase, each with whether it's lava (a platform to build).
 */
@Slf4j
final class CoachPaths
{
	/** One tile of a route: {dx, dy, lava (1/0), stood on (1/0)} from the West stairs. */
	private final Map<String, int[][]> routes;
	/** The player's own recorded routes for a leg, one per start spot they've used. */
	private final Map<String, java.util.List<int[][]>> recorded;
	/** The model's route from each side of a boulder (you can be mining it from any of them). */
	private final Map<String, java.util.List<int[][]>> modelVariants;

	private CoachPaths(Map<String, int[][]> routes, Map<String, java.util.List<int[][]>> recorded,
		Map<String, java.util.List<int[][]>> modelVariants)
	{
		this.routes = routes;
		this.recorded = recorded;
		this.modelVariants = modelVariants;
	}

	// Each route's time in ticks (by identity), for choosing between staircases.
	private static final Map<int[][], Double> TICKS = java.util.Collections.synchronizedMap(new java.util.IdentityHashMap<>());

	/** A route's walking time in ticks (as exported or recorded). */
	static double ticks(int[][] route)
	{
		Double t = route == null ? null : TICKS.get(route);
		return t == null ? Double.MAX_VALUE : t;
	}

	/** Within this many tiles of the start of one of your recorded routes, yours is drawn. */
	static final int OWN_ROUTE_START_TILES = 3;

	/**
	 * The route to draw from one place to another, for a player standing at (dx, dy) from the West
	 * stairs: your recorded route starting there if there is one, otherwise whichever route (yours or
	 * the model's) starts nearest to you. Null if there's no route.
	 */
	int[][] route(String from, String to, int dx, int dy)
	{
		if (from == null || to == null || from.equals(to))
		{
			return null;
		}
		String key = from + "|" + to;
		int[][] best = null;
		int bestDistance = Integer.MAX_VALUE;
		for (int[][] own : recorded.getOrDefault(key, Collections.emptyList()))
		{
			int d = startDistance(own, dx, dy);
			if (d < bestDistance)
			{
				best = own;
				bestDistance = d;
			}
		}
		if (best != null && bestDistance <= OWN_ROUTE_START_TILES)
		{
			return best;
		}
		// Not near the start of one of yours: the stock route starting nearest you. (A recorded route
		// is never drawn from somewhere else - it might be a different leg filed under this one.)
		java.util.List<int[][]> stock = new java.util.ArrayList<>(modelVariants.getOrDefault(key, Collections.emptyList()));
		if (routes.containsKey(key))
		{
			stock.add(routes.get(key));
		}
		int[][] chosen = null;
		int chosenDistance = Integer.MAX_VALUE;
		for (int[][] model : stock)
		{
			int d = startDistance(model, dx, dy);
			if (d < chosenDistance)
			{
				chosen = model;
				chosenDistance = d;
			}
		}
		return chosen;
	}

	private static int startDistance(int[][] route, int dx, int dy)
	{
		return route.length == 0 ? Integer.MAX_VALUE : Math.max(Math.abs(route[0][0] - dx), Math.abs(route[0][1] - dy));
	}

	static CoachPaths load()
	{
		try (InputStream in = CoachPaths.class.getResourceAsStream("coach_paths.json"))
		{
			if (in == null)
			{
				log.warn("UVM Coach: coach_paths.json missing");
				return new CoachPaths(Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap());
			}
			JsonObject root = new JsonParser().parse(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			Map<String, int[][]> routes = new HashMap<>();
			for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("routes").entrySet())
			{
				routes.put(e.getKey(), tiles(e.getValue().getAsJsonObject()));
			}
			return new CoachPaths(routes, lists(root, "variants"), lists(root, "modelVariants"));
		}
		catch (Exception e)
		{
			log.warn("UVM Coach: could not read coach_paths.json", e);
			return new CoachPaths(Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap());
		}
	}

	/**
	 * The tiles the player actually stands on (tile[3] = 1): as recorded, or for a stock route every
	 * second land tile (running covers 2 a tick), every lava tile (each platform is stepped onto) and
	 * the last tile.
	 */
	static void markStoodOn(int[][] route)
	{
		boolean recorded = route.length > 0 && route[0][3] >= 0;
		if (recorded)
		{
			route[route.length - 1][3] = 1;
			return;
		}
		int sinceStood = 0;
		for (int i = 0; i < route.length; i++)
		{
			boolean lava = route[i][2] == 1;
			boolean last = i == route.length - 1;
			boolean stand = i > 0 && (lava || last || sinceStood >= 1);
			route[i][3] = stand ? 1 : 0;
			// You start on the first tile: it counts as stood on (just not drawn).
			sinceStood = stand || i == 0 ? 0 : sinceStood + 1;
		}
	}

	/** The model's route from one place to another (names as in the export), or null. */
	int[][] route(String from, String to)
	{
		if (from == null || to == null || from.equals(to))
		{
			return null;
		}
		return routes.get(from + "|" + to);
	}

	private static Map<String, java.util.List<int[][]>> lists(JsonObject root, String name)
	{
		Map<String, java.util.List<int[][]>> out = new HashMap<>();
		if (root.has(name))
		{
			for (Map.Entry<String, JsonElement> e : root.getAsJsonObject(name).entrySet())
			{
				java.util.List<int[][]> list = new java.util.ArrayList<>();
				for (JsonElement v : e.getValue().getAsJsonArray())
				{
					list.add(tiles(v.getAsJsonObject()));
				}
				out.put(e.getKey(), list);
			}
		}
		return out;
	}

	private static int[][] tiles(JsonObject route)
	{
		JsonArray tiles = route.getAsJsonArray("tiles");
		int[][] out = new int[tiles.size()][];
		for (int i = 0; i < out.length; i++)
		{
			JsonArray t = tiles.get(i).getAsJsonArray();
			out[i] = new int[]{t.get(0).getAsInt(), t.get(1).getAsInt(), t.get(2).getAsInt(), t.size() > 3 ? t.get(3).getAsInt() : -1};
		}
		markStoodOn(out);
		if (route.has("ticks"))
		{
			TICKS.put(out, route.get("ticks").getAsDouble());
		}
		return out;
	}

	int size()
	{
		return routes.size();
	}
}
