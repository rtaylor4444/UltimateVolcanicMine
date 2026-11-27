package com.ultimatevm;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;

/**
 * Finds the mine's vents, chambers, rocks and staircases so the coach can highlight the one to use.
 * The mine is an instance (coordinates change every game), so each object is placed by its offset
 * from the West staircase, as labelled on the recorded map (volcanicMine mapModel.SPOTS).
 */
final class MineLayout
{
	static final int STAIRCASE_ID = 31030;
	/** The lobby's staircases down into the mine (right above the mine's two). */
	static final int LOBBY_STAIRCASE_ID = 31029;
	static final int VENT_ID = 31040;
	static final int CHAMBER_UNBLOCKED_ID = 31043;
	static final int CHAMBER_BLOCKED_ID = 31044;
	static final int ROCK_ID = 31045;
	static final int TAKEN_ROCK_ID = 31046;

	/** Everything the coach can point at (besides the boulder). */
	enum Spot
	{
		STAIRS_WEST(0, 0),
		STAIRS_EAST(11, 0),
		CHAMBER_A(-17, -3),
		CHAMBER_B(11, 13),
		CHAMBER_C(26, -4),
		// Half 1 uses the southern vents (by B1), half 2 the northern ones (by B4/B5).
		VENT_A_SOUTH(-19, -21),
		VENT_B_SOUTH(5, -41),
		VENT_C_SOUTH(32, -23),
		VENT_A_NORTH(-14, 25),
		VENT_B_NORTH(14, 43),
		VENT_C_NORTH(26, 35),
		ROCK_A(-18, -1),
		ROCK_B(13, 11),
		ROCK_C(29, 2);

		final int dx;
		final int dy;

		Spot(int dx, int dy)
		{
			this.dx = dx;
			this.dy = dy;
		}

		static Spot chamber(int vent)
		{
			return vent == 0 ? CHAMBER_A : vent == 1 ? CHAMBER_B : CHAMBER_C;
		}

		static Spot rock(int vent)
		{
			return vent == 0 ? ROCK_A : vent == 1 ? ROCK_B : ROCK_C;
		}

		static Spot vent(int vent, boolean secondHalf)
		{
			if (secondHalf)
			{
				return vent == 0 ? VENT_A_NORTH : vent == 1 ? VENT_B_NORTH : VENT_C_NORTH;
			}
			return vent == 0 ? VENT_A_SOUTH : vent == 1 ? VENT_B_SOUTH : VENT_C_SOUTH;
		}
	}

	/** How far (tiles) an object may sit from its labelled spot. */
	private static final int MATCH_DISTANCE = 3;

	private final List<TileObject> objects = new ArrayList<>();

	void clear()
	{
		objects.clear();
	}

	// The boulder as a game object, by stage (giant attached/unattached, large, medium, small).
	static final int FIRST_BOULDER_ID = 31034;
	static final int LAST_BOULDER_ID = 31038;

	static boolean isTracked(int id)
	{
		return (id >= FIRST_BOULDER_ID && id <= LAST_BOULDER_ID) || id == STAIRCASE_ID || id == LOBBY_STAIRCASE_ID || id == VENT_ID || id == CHAMBER_UNBLOCKED_ID || id == CHAMBER_BLOCKED_ID
			|| id == ROCK_ID || id == TAKEN_ROCK_ID;
	}

	void onSpawned(TileObject object)
	{
		if (object != null && isTracked(object.getId()))
		{
			objects.removeIf(o -> o == object);
			objects.add(object);
		}
	}

	void onDespawned(TileObject object)
	{
		objects.removeIf(o -> o == object);
	}

	/** In the lobby: the staircase down to the mine's West stairs (the more westerly), or null. */
	TileObject lobbyWestStairs(int plane)
	{
		TileObject best = null;
		for (TileObject o : objects)
		{
			if (o.getId() == LOBBY_STAIRCASE_ID && o.getWorldLocation().getPlane() == plane
				&& (best == null || o.getWorldLocation().getX() < best.getWorldLocation().getX()))
			{
				best = o;
			}
		}
		return best;
	}

	/** The West staircase: the more westerly of the two mine staircases. */
	WorldPoint westStairs()
	{
		WorldPoint best = null;
		for (TileObject o : objects)
		{
			if (o.getId() == STAIRCASE_ID)
			{
				WorldPoint p = o.getWorldLocation();
				if (best == null || p.getX() < best.getX())
				{
					best = p;
				}
			}
		}
		return best;
	}

	/** The object at this spot (for a rock: the pile, taken or not), or null if not loaded. */
	TileObject find(Spot spot)
	{
		WorldPoint origin = westStairs();
		if (origin == null)
		{
			return null;
		}
		TileObject best = null;
		int bestDistance = Integer.MAX_VALUE;
		for (Iterator<TileObject> it = objects.iterator(); it.hasNext(); )
		{
			TileObject o = it.next();
			if (!matchesKind(spot, o.getId()))
			{
				continue;
			}
			WorldPoint p = o.getWorldLocation();
			if (p.getPlane() != origin.getPlane())
			{
				continue;
			}
			int distance = Math.max(Math.abs(p.getX() - origin.getX() - spot.dx), Math.abs(p.getY() - origin.getY() - spot.dy));
			if (distance <= MATCH_DISTANCE && distance < bestDistance)
			{
				best = o;
				bestDistance = distance;
			}
		}
		return best;
	}

	/** The boulder object nearest this point (the tracked boulder), or the only one; null if none. */
	TileObject boulder(WorldPoint near)
	{
		TileObject best = null;
		int bestDistance = Integer.MAX_VALUE;
		for (TileObject o : objects)
		{
			if (o.getId() < FIRST_BOULDER_ID || o.getId() > LAST_BOULDER_ID)
			{
				continue;
			}
			int distance = near == null ? 0 : o.getWorldLocation().distanceTo(near);
			if (distance < bestDistance)
			{
				best = o;
				bestDistance = distance;
			}
		}
		return best;
	}

	/** True when this spot's rock pile currently has a rock to take. */
	boolean rockAvailable(Spot rockSpot)
	{
		TileObject o = find(rockSpot);
		return o != null && o.getId() == ROCK_ID;
	}

	/** The spot an object belongs to, or null. */
	Spot spotOf(TileObject object)
	{
		for (Spot spot : Spot.values())
		{
			if (find(spot) == object)
			{
				return spot;
			}
		}
		return null;
	}

	private static boolean matchesKind(Spot spot, int id)
	{
		switch (spot)
		{
			case STAIRS_WEST:
			case STAIRS_EAST:
				return id == STAIRCASE_ID;
			case CHAMBER_A:
			case CHAMBER_B:
			case CHAMBER_C:
				return id == CHAMBER_UNBLOCKED_ID || id == CHAMBER_BLOCKED_ID;
			case ROCK_A:
			case ROCK_B:
			case ROCK_C:
				return id == ROCK_ID || id == TAKEN_ROCK_ID;
			default:
				return id == VENT_ID;
		}
	}
}
