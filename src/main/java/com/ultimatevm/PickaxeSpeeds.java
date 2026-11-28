package com.ultimatevm;

import java.util.HashMap;
import java.util.Map;

/**
 * Pickaxe mining animations and how often each pickaxe rolls to mine (OSRS wiki "Pickaxe" tables).
 * A tick is 0.6s. Dragon-tier pickaxes are 3 ticks with a 1/6 chance of 2 (2.83 average); the
 * crystal pickaxe is 3 ticks with a 1/4 chance of 2 (2.75 average).
 */
final class PickaxeSpeeds
{
	static final double DRAGON_TIER_TICKS = 3 - 1.0 / 6;
	static final double CRYSTAL_TICKS = 3 - 1.0 / 4;

	// Animation id (net.runelite.api.AnimationID.MINING_*) -> average ticks between mining rolls.
	private static final Map<Integer, Double> TICKS_BY_ANIMATION = new HashMap<>();

	static
	{
		TICKS_BY_ANIMATION.put(625, 8.0);   // bronze
		TICKS_BY_ANIMATION.put(626, 7.0);   // iron
		TICKS_BY_ANIMATION.put(627, 6.0);   // steel
		TICKS_BY_ANIMATION.put(3873, 5.0);  // black
		TICKS_BY_ANIMATION.put(629, 5.0);   // mithril
		TICKS_BY_ANIMATION.put(628, 4.0);   // adamant
		TICKS_BY_ANIMATION.put(624, 3.0);   // rune
		TICKS_BY_ANIMATION.put(8313, 3.0);  // gilded
		TICKS_BY_ANIMATION.put(7139, DRAGON_TIER_TICKS); // dragon
		TICKS_BY_ANIMATION.put(642, DRAGON_TIER_TICKS);  // dragon (upgraded)
		TICKS_BY_ANIMATION.put(8346, DRAGON_TIER_TICKS); // dragon (or)
		TICKS_BY_ANIMATION.put(8887, DRAGON_TIER_TICKS); // dragon (or) (Trailblazer)
		TICKS_BY_ANIMATION.put(4482, DRAGON_TIER_TICKS); // infernal
		TICKS_BY_ANIMATION.put(7283, DRAGON_TIER_TICKS); // 3rd age
		TICKS_BY_ANIMATION.put(8787, DRAGON_TIER_TICKS); // trailblazer variants
		TICKS_BY_ANIMATION.put(8788, DRAGON_TIER_TICKS);
		TICKS_BY_ANIMATION.put(8789, DRAGON_TIER_TICKS);
		TICKS_BY_ANIMATION.put(8347, CRYSTAL_TICKS);     // crystal
	}

	private PickaxeSpeeds()
	{
	}

	static boolean isMiningAnimation(int animationId)
	{
		return TICKS_BY_ANIMATION.containsKey(animationId);
	}

	/** Average ticks between mining rolls for this animation, or the crystal speed if unknown. */
	static double ticksPerRoll(int animationId)
	{
		Double ticks = TICKS_BY_ANIMATION.get(animationId);
		return ticks != null ? ticks : CRYSTAL_TICKS;
	}
}
