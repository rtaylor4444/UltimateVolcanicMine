package com.ultimatevm;

/**
 * The game's vent and stability rules (confirmed against logged games).
 *
 * <ul>
 *     <li>Vents are 0-100 (50 is perfect). The vent varbits read 127 until a vent is checked.</li>
 *     <li>Vents start at 30-70 each; at the 5-minute reset they're re-rolled to 25-75 solo,
 *     0-100 in a team.</li>
 *     <li>Every 10 ticks each vent moves 2 in the direction its chamber sets (blocked = up), or 1
 *     while it's in 41-59. A vent in 41-59 also slows the vents after it by 1 (A slows B and C,
 *     B slows C). Movement never goes below 0 (slowing never pushes a vent backwards).</li>
 *     <li>Every 25 ticks stability changes by -25 + each vent's influence (up to 16 at 50,
 *     falling off linearly), capped to 0-100.</li>
 * </ul>
 */
final class VentRules
{
	static final int UNKNOWN = 127;
	static final int MIN_VENT = 0;
	static final int MAX_VENT = 100;
	static final int PERFECT_VENT = 50;
	static final int MIN_START = 30;
	static final int MAX_START = 70;
	static final int MIN_RESET_SOLO = 25;
	static final int MAX_RESET_SOLO = 75;
	static final int STABILITY_CHANGE_CONSTANT = -25;
	static final float VENT_STABILITY_WEIGHT = 16.0f;

	/** Ticks remaining on the clock at the 5-minute reset. */
	static final int RESET_TIME_TICKS = 500;

	private static final int[] INFLUENCE = new int[MAX_VENT + 1];

	static
	{
		for (int v = MIN_VENT; v <= MAX_VENT; v++)
		{
			float percent = PERFECT_VENT - Math.abs(PERFECT_VENT - v);
			INFLUENCE[v] = (int) Math.ceil((percent / 50.0f) * VENT_STABILITY_WEIGHT);
		}
	}

	private VentRules()
	{
	}

	static int influence(int vent)
	{
		return INFLUENCE[Math.max(MIN_VENT, Math.min(MAX_VENT, vent))];
	}

	static int stabilityChange(int a, int b, int c)
	{
		return STABILITY_CHANGE_CONSTANT + influence(a) + influence(b) + influence(c);
	}

	static boolean inFreezeRange(int vent)
	{
		return vent >= 41 && vent <= 59;
	}

	/** +1 if the vent's chamber is blocked (vent rises), -1 if not. */
	static int direction(int ventIndex, int chamberStatus)
	{
		return (chamberStatus & (1 << ventIndex)) != 0 ? 1 : -1;
	}

	static int[] move(int a, int b, int c, int chamberStatus)
	{
		int cMovement = inFreezeRange(c) ? 1 : 2;
		if (inFreezeRange(b))
		{
			cMovement -= 1;
		}
		if (inFreezeRange(a))
		{
			cMovement -= 1;
		}
		int bMovement = inFreezeRange(b) ? 1 : 2;
		if (inFreezeRange(a))
		{
			bMovement -= 1;
		}
		int aMovement = inFreezeRange(a) ? 1 : 2;
		return new int[]{
			clampVent(a + aMovement * direction(0, chamberStatus)),
			clampVent(b + Math.max(0, bMovement) * direction(1, chamberStatus)),
			clampVent(c + Math.max(0, cMovement) * direction(2, chamberStatus))};
	}

	/**
	 * {@link #move} for vents packed as {@code (a * 101 + b) * 101 + c}, without allocating. With
	 * {@code cOnly}, A and B stay put and only C moves (an earthquake cancelling A and B's move).
	 */
	static int movePacked(int a, int b, int c, int chamberStatus, boolean cOnly)
	{
		boolean aFrozen = a >= 41 && a <= 59;
		boolean bFrozen = b >= 41 && b <= 59;
		int cMovement = (c >= 41 && c <= 59 ? 1 : 2) - (bFrozen ? 1 : 0) - (aFrozen ? 1 : 0);
		int newC = clampVent(c + Math.max(0, cMovement) * ((chamberStatus & 4) != 0 ? 1 : -1));
		if (cOnly)
		{
			return (a * 101 + b) * 101 + newC;
		}
		int bMovement = (bFrozen ? 1 : 2) - (aFrozen ? 1 : 0);
		int newB = clampVent(b + Math.max(0, bMovement) * ((chamberStatus & 2) != 0 ? 1 : -1));
		int newA = clampVent(a + (aFrozen ? 1 : 2) * ((chamberStatus & 1) != 0 ? 1 : -1));
		return (newA * 101 + newB) * 101 + newC;
	}

	static int clampVent(int value)
	{
		return Math.max(MIN_VENT, Math.min(MAX_VENT, value));
	}

	static int clampStability(int value)
	{
		return Math.max(0, Math.min(100, value));
	}
}
