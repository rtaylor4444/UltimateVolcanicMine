package com.ultimatevm;

/** The kinds of thing the strategy coach tells the player to do. */
enum CoachAction
{
	/** In the lobby: which staircase to go down. */
	START,
	MINE_BOULDER,
	CHECK_VENT,
	FIX_CHAMBER,
	TAKE_ROCK,
	CAP_CHAMBER,
	LEAVE,
	TELEPORT,
	WAIT
}
