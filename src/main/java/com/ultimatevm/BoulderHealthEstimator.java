package com.ultimatevm;

/**
 * Estimates the boulder's remaining health for the whole team.
 *
 * The game doesn't expose the boulder's health, and teammates' successful mines can't be seen
 * (no shared chat message, health bar, varbit, sound or animation), so:
 * <ul>
 *     <li>a stage starts at its base health (41, or 61 for the last stage) x the player count;</li>
 *     <li>your own successful mines are exact (from your chat messages);</li>
 *     <li>teammates' mining is estimated from their pickaxe: each tick a teammate mines adds
 *     {@link #damagePerTick(int)} for their pickaxe animation, times a level multiplier;</li>
 *     <li>when a stage breaks, the real teammate damage (stage health - your mines) is known, so
 *     the level multiplier is corrected for the next stage. It absorbs teammates' mining levels
 *     and anything else the pickaxe speed alone doesn't explain.</li>
 * </ul>
 * The estimate never shows the stage as finished before it really breaks (minimum 1 health).
 */
final class BoulderHealthEstimator
{
	static final double TICK_SECONDS = 0.6;
	// A 99+ miner with a crystal pickaxe takes 71.01s for 41 health (volcanicMine/XpUtils.BOULDERS).
	static final double SOLO_DAMAGE_PER_TICK = 41.0 / (71.01 / TICK_SECONDS);
	// Success chance per roll implied by that (about 95%).
	static final double SUCCESS_CHANCE = SOLO_DAMAGE_PER_TICK * PickaxeSpeeds.CRYSTAL_TICKS;

	// Need at least this much expected teammate damage in a stage before correcting from it, and
	// this much before trusting the measurement outright instead of averaging with the old value.
	private static final double MIN_CALIBRATION_DAMAGE = 5;
	private static final double FULL_TRUST_CALIBRATION_DAMAGE = 30;
	private static final double MIN_MULTIPLIER = 0.3;
	private static final double MAX_MULTIPLIER = 1.1;

	private int maxHealth;
	private int ownSuccesses;
	private double teammateDamage;
	// Teammate damage predicted from pickaxe speed alone (before the level multiplier).
	private double expectedTeammateDamage;
	private boolean active;
	private double levelMultiplier = 1.0;

	/** Expected damage per tick for one person mining with this pickaxe animation, at 99. */
	static double damagePerTick(int miningAnimation)
	{
		return SUCCESS_CHANCE / PickaxeSpeeds.ticksPerRoll(miningAnimation);
	}

	void startStage(int baseHealth, int playerCount)
	{
		maxHealth = baseHealth * Math.max(1, playerCount);
		ownSuccesses = 0;
		teammateDamage = 0;
		expectedTeammateDamage = 0;
		active = true;
	}

	void onOwnSuccessfulMine()
	{
		if (active)
		{
			++ownSuccesses;
		}
	}

	/**
	 * @param miningTeammates      teammates (not counting you) mining the boulder this tick
	 * @param teammateDamageAt99   sum of {@link #damagePerTick(int)} over those teammates
	 */
	void onTick(int miningTeammates, double teammateDamageAt99)
	{
		if (!active || miningTeammates <= 0)
		{
			return;
		}
		expectedTeammateDamage += teammateDamageAt99;
		teammateDamage += teammateDamageAt99 * levelMultiplier;
	}

	/** Called when the stage breaks: corrects the level multiplier from what really happened. */
	void onStageBroken()
	{
		if (active && expectedTeammateDamage >= MIN_CALIBRATION_DAMAGE)
		{
			double actualTeammateDamage = Math.max(0, maxHealth - ownSuccesses);
			double measured = actualTeammateDamage / expectedTeammateDamage;
			// A stage with plenty of teammate mining is trusted outright; a thin one is averaged
			// with the previous value so one odd stage doesn't swing it too far.
			double updated = expectedTeammateDamage >= FULL_TRUST_CALIBRATION_DAMAGE
				? measured
				: (levelMultiplier + measured) / 2;
			levelMultiplier = Math.max(MIN_MULTIPLIER, Math.min(MAX_MULTIPLIER, updated));
		}
		active = false;
	}

	/** Between stages (or before the first one). */
	void clear()
	{
		active = false;
	}

	/** New game: also forget the calibrated multiplier. */
	void resetForNewGame()
	{
		clear();
		levelMultiplier = 1.0;
	}

	/** Estimated remaining health, or 0 when no stage is being mined. */
	int getHealth()
	{
		if (!active)
		{
			return 0;
		}
		double remaining = maxHealth - ownSuccesses - teammateDamage;
		// The stage hasn't broken yet, so at least 1 health is really left.
		return (int) Math.max(1, Math.round(remaining));
	}

	int getMaxHealth()
	{
		return active ? maxHealth : 0;
	}

	/**
	 * Estimated seconds until the stage breaks, assuming you keep mining (at ownDamagePerTick)
	 * alongside the teammates currently mining.
	 */
	double estimateSecondsRemaining(double ownDamagePerTick, double teammateDamageAt99)
	{
		return secondsForHealth(getHealth(), ownDamagePerTick, teammateDamageAt99);
	}

	/**
	 * Seconds for the team to mine this much health: you at ownDamagePerTick plus teammates at
	 * teammateDamageAt99 (corrected by the level multiplier learnt from earlier stages).
	 */
	double secondsForHealth(double health, double ownDamagePerTick, double teammateDamageAt99)
	{
		double damagePerTick = ownDamagePerTick + Math.max(0, teammateDamageAt99) * levelMultiplier;
		if (damagePerTick <= 0)
		{
			return 0;
		}
		return Math.max(0, health) / damagePerTick * TICK_SECONDS;
	}

	double getLevelMultiplier()
	{
		return levelMultiplier;
	}
}
