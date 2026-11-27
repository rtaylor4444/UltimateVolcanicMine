package com.ultimatevm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class StrategyCoachTest
{
	private static final int U = VentRules.UNKNOWN;

	/** A game state the coach sees, with the vents it believes possible. */
	private static final class Game
	{
		List<int[]> candidates = new ArrayList<>();
		double[] weights;
		final StrategyCoach coach = new StrategyCoach(this::predictor);
		double now = 20;
		int stability = 50;
		int chamber;
		int[] raw = {U, U, U};
		int boulder = 1;
		boolean atBoulder;
		int rocks;
		int caps;
		boolean update;
		double boulderSeconds = -1;

		Game(int[]... vents)
		{
			Collections.addAll(candidates, vents);
			weights = new double[vents.length];
			java.util.Arrays.fill(weights, 1.0);
		}

		StrategyPredictor predictor(StrategyCoach.Observation o)
		{
			return new StrategyPredictor(candidates, weights, o.chamberStatus, o.stability, o.startSecond);
		}

		StrategyCoach.Observation observation()
		{
			StrategyCoach.Observation o = new StrategyCoach.Observation();
			o.now = now;
			o.startSecond = (int) Math.floor(now) + 1;
			o.stability = stability;
			o.chamberStatus = chamber;
			o.rawVents = raw.clone();
			o.boulderNumber = boulder;
			o.atBoulder = atBoulder;
			o.rocksInHand = rocks;
			o.capsMade = caps;
			o.stabilityUpdate = update;
			o.boulderSecondsRemaining = boulderSeconds;
			return o;
		}

		StrategyCoach.Instruction tick()
		{
			StrategyCoach.Instruction i = coach.update(observation());
			update = false;
			return i;
		}

		StrategyCoach.Instruction breakBoulder(int number)
		{
			coach.onBoulderBroken(number, observation());
			boulder = Math.min(5, number + 1);
			atBoulder = false;
			return tick();
		}

		/** Does what the instruction says (instantly) and returns the next one. */
		StrategyCoach.Instruction obey(StrategyCoach.Instruction i)
		{
			switch (i.action)
			{
				case TAKE_ROCK:
					rocks++;
					break;
				case FIX_CHAMBER:
				case CAP_CHAMBER:
				{
					int bit = i.spot == MineLayout.Spot.CHAMBER_A ? 1 : i.spot == MineLayout.Spot.CHAMBER_B ? 2 : 4;
					if ((chamber & bit) == 0)
					{
						rocks--;
					}
					chamber ^= bit;
					caps++;
					// A fix flips the believed vents' direction: the candidates don't change (raw values).
					break;
				}
				case CHECK_VENT:
				{
					int vent = i.spot.name().contains("_A_") ? 0 : i.spot.name().contains("_B_") ? 1 : 2;
					raw[vent] = candidates.get(0)[vent];
					break;
				}
				default:
					break;
			}
			now += 3;
			return tick();
		}
	}

	@Test
	public void aCalmGameIsMineCheckCapAndLeave()
	{
		Game g = new Game(new int[]{50, 50, 50});
		g.stability = 60;
		StrategyCoach.Instruction i = g.tick();
		assertEquals(CoachAction.MINE_BOULDER, i.action);
		assertEquals("Mine boulder 1", i.text);
		g.atBoulder = true;
		g.update = true;
		g.now = 45;
		assertEquals("Mine boulder 1", g.tick().text);

		g.now = 100;
		assertEquals("Mine boulder 2", g.breakBoulder(1).text);
		g.now = 180;
		assertEquals("Mine boulder 3", g.breakBoulder(2).text);

		// B3 -> take the B rock -> cap the B chamber while B4 rolls in -> B4.
		g.now = 270;
		// While mining B3 the step after it is already known.
		assertEquals("Take the B rock", g.tick().next);
		i = g.breakBoulder(3);
		assertEquals(CoachAction.TAKE_ROCK, i.action);
		assertEquals(MineLayout.Spot.ROCK_B, i.spot);
		assertTrue(i.next, i.next.startsWith("Cap: block the B chamber"));
		i = g.obey(i);
		// As many caps as fit before B4 rolls in: B open and one rock - block, then unblock.
		assertEquals(CoachAction.CAP_CHAMBER, i.action);
		assertEquals(MineLayout.Spot.CHAMBER_B, i.spot);
		assertTrue(i.text, i.text.startsWith("Cap: block"));
		i = g.obey(i);
		assertTrue(i.text, i.text.startsWith("Cap: unblock"));
		assertEquals("Mine boulder 4", i.next);
		i = g.obey(i);
		assertEquals("Mine boulder 4", i.text);

		// After the reset (new vents, all at 50 here): B4 -> rock -> no B flip -> check C, B -> B5.
		g.now = 370;
		g.raw = new int[]{U, U, U};
		i = g.breakBoulder(4);
		assertEquals(CoachAction.TAKE_ROCK, i.action);
		// What comes after the rock is known before it's taken.
		assertEquals("Check the C vent", i.next);
		i = g.obey(i);
		assertEquals(CoachAction.CHECK_VENT, i.action);
		assertEquals(MineLayout.Spot.VENT_C_NORTH, i.spot);
		assertEquals("Check the B vent", i.next);
		i = g.obey(i);
		assertEquals(MineLayout.Spot.VENT_B_NORTH, i.spot);
		// The fix choice after it isn't made until the B vent is checked.
		assertEquals(StrategyCoach.UNKNOWN_NEXT, i.next);
		i = g.obey(i);
		assertEquals("Mine boulder 5", i.text);
		// While mining B5 the way out is already known (caps, or straight out).
		assertTrue(i.next, i.next.startsWith("Take the B rock") || i.next.startsWith("Cap") || i.next.startsWith("Leave"));

		// B5 -> rock -> caps at the B chamber up to 6 -> leave by the East stairs.
		g.now = 480;
		i = g.breakBoulder(5);
		int guard = 0;
		while (i.action != CoachAction.LEAVE && guard++ < 20)
		{
			assertTrue(i.action == CoachAction.TAKE_ROCK || i.action == CoachAction.CAP_CHAMBER);
			i = g.obey(i);
		}
		assertEquals(CoachAction.LEAVE, i.action);
		assertEquals(MineLayout.Spot.STAIRS_EAST, i.spot);
		assertEquals(HalfTwoPlanner.MAX_CAPS, g.caps);
		assertFalse(i.urgent);
	}

	@Test
	public void aDangerousFirstHalfGetsAFixAndTheStepsInOrder()
	{
		// A and B both falling fast from low values: the mine blows up well before 5:00 unaided.
		Game g = new Game(new int[]{20, 25, 50});
		g.stability = 60;
		g.atBoulder = true;
		g.now = 30;
		g.update = true;
		StrategyCoach.Instruction i = g.tick();
		assertNotNull(i);
		// Something to do away from the boulder (a fix now, or a check/fix soon after B1).
		boolean fixing = i.action == CoachAction.FIX_CHAMBER || i.action == CoachAction.TAKE_ROCK || i.action == CoachAction.CHECK_VENT;
		if (!fixing)
		{
			assertEquals("Mine boulder 1", i.text);
			assertNotNull("a plan is shown", i.detail);
			g.now = 100;
			i = g.breakBoulder(1);
		}
		int fixes = 0;
		int guard = 0;
		while (i.action != CoachAction.MINE_BOULDER && guard++ < 10)
		{
			if (i.action == CoachAction.FIX_CHAMBER)
			{
				fixes++;
			}
			i = g.obey(i);
		}
		assertTrue("fixed something", fixes > 0);
		assertEquals(CoachAction.MINE_BOULDER, i.action);
	}

	@Test
	public void aHopelessFirstHalfMeansTeleport()
	{
		Game g = new Game(new int[]{0, 0, 0});
		g.stability = 10;
		g.now = 200;
		g.boulder = 3;
		g.coach.onBoulderBroken(2, g.observation());
		g.atBoulder = true;
		StrategyCoach.Instruction i = g.tick();
		assertEquals(CoachAction.TELEPORT, i.action);
		assertTrue(i.urgent);
	}

	@Test
	public void theSecondHalfWalksOutWhenTheMineCouldBlowUp()
	{
		Game g = new Game(new int[]{50, 50, 50});
		g.stability = 60;
		g.now = 250;
		g.coach.onBoulderBroken(1, g.observation());
		g.coach.onBoulderBroken(2, g.observation());
		g.boulder = 4;
		g.coach.onBoulderBroken(3, g.observation());
		// After the reset: all three vents at 0 and falling, stability low.
		g.candidates = new ArrayList<>(Collections.singletonList(new int[]{0, 0, 0}));
		g.weights = new double[]{1};
		g.stability = 20;
		g.now = 320;
		g.atBoulder = true;
		StrategyCoach.Instruction i = g.tick();
		assertEquals(CoachAction.LEAVE, i.action);
		assertTrue(i.urgent);
		assertEquals(MineLayout.Spot.STAIRS_EAST, i.spot);
	}

	@Test
	public void aTeamGameIsStillCoached()
	{
		Game g = new Game(new int[]{50, 50, 50});
		StrategyCoach.Observation o = g.observation();
		o.playerCount = 2;
		assertEquals(CoachAction.MINE_BOULDER, g.coach.update(o).action);
	}

	@Test
	public void blockingWithoutARockFirstSaysTakeTheRock()
	{
		Game g = new Game(new int[]{50, 50, 50});
		g.now = 270;
		g.coach.onBoulderBroken(1, g.observation());
		g.coach.onBoulderBroken(2, g.observation());
		StrategyCoach.Instruction i = g.breakBoulder(3);
		assertEquals(CoachAction.TAKE_ROCK, i.action);
		// The chamber is already blocked: capping it is an unblock, no rock needed.
		Game blocked = new Game(new int[]{50, 50, 50});
		blocked.chamber = 2;
		blocked.now = 270;
		blocked.coach.onBoulderBroken(1, blocked.observation());
		blocked.coach.onBoulderBroken(2, blocked.observation());
		i = blocked.breakBoulder(3);
		assertEquals(CoachAction.TAKE_ROCK, i.action);
		i = blocked.obey(i);
		assertEquals(CoachAction.CAP_CHAMBER, i.action);
		assertTrue(i.text.contains("unblock"));
	}

	@Test
	public void theWestStairsAnchorTheSpots()
	{
		assertEquals(11, MineLayout.Spot.STAIRS_EAST.dx);
		assertEquals(MineLayout.Spot.VENT_B_SOUTH, MineLayout.Spot.vent(1, false));
		assertEquals(MineLayout.Spot.VENT_C_NORTH, MineLayout.Spot.vent(2, true));
		assertEquals(MineLayout.Spot.ROCK_C, MineLayout.Spot.rock(2));
	}

	@Test
	public void theEndCapsOnlyFetchARockWhenABlockNeedsOne()
	{
		StrategyCoach.Observation o = new StrategyCoach.Observation();
		o.chamberStatus = 2;
		o.capsMade = 5;
		// One cap left and B is blocked: an unblock, no rock needed.
		assertFalse(StrategyCoach.endCapsNeedARock(o));
		// Three left from blocked: unblock, block, unblock - one rock.
		o.capsMade = 3;
		assertTrue(StrategyCoach.endCapsNeedARock(o));
		o.rocksInHand = 1;
		assertFalse(StrategyCoach.endCapsNeedARock(o));
		// From open with two left: block, unblock - one rock.
		o.chamberStatus = 0;
		o.capsMade = 4;
		o.rocksInHand = 0;
		assertTrue(StrategyCoach.endCapsNeedARock(o));
	}

	@Test
	public void theEndRockStepDropsOutOnceTheCapsAreDone()
	{
		// B5 broken with 4 caps, B open, no rock: take the rock (a block is needed)...
		Game g = new Game(new int[]{50, 50, 50});
		g.stability = 100;
		g.now = 250;
		for (int b = 1; b <= 4; b++)
		{
			g.coach.onBoulderBroken(b, g.observation());
			g.tick();
		}
		g.caps = 4;
		g.now = 480;
		g.boulder = 5;
		StrategyCoach.Instruction i = g.breakBoulder(5);
		assertEquals(CoachAction.TAKE_ROCK, i.action);
		// ...but the player caps twice some other way (6 caps): no rock, no "7/6", just leave.
		g.chamber = 2;
		g.caps = 6;
		g.rocks = 0;
		i = g.tick();
		assertEquals(CoachAction.LEAVE, i.action);
	}

	@Test
	public void stepsNameThePlacesTheirRoutesGoBetween()
	{
		assertEquals("B3", StrategyCoach.placeForText("Mine boulder 3", 200));
		assertEquals("B vent", StrategyCoach.placeForText("Check the B vent", 90));
		assertEquals("C vent N", StrategyCoach.placeForText("Check the C vent (on the way to fix C)", 380));
		assertEquals("C vent", StrategyCoach.placeForText("Fix C+B after B1 (check the C vent on the way)", 60));
		assertEquals("A chamber", StrategyCoach.placeForText("Fix A: unblock the A chamber", 60));
		assertEquals("B chamber", StrategyCoach.placeForText("Cap: block the B chamber (2/6)", 290));
		assertEquals("B rock", StrategyCoach.placeForText("Take the B rock", 290));
		assertEquals("E stair", StrategyCoach.placeForText("Leave by the East stairs", 560));
		assertEquals(null, StrategyCoach.placeForText(StrategyCoach.UNKNOWN_NEXT, 60));
	}

	@Test
	public void theStockRoutesShipWithThePlugin()
	{
		CoachPaths paths = CoachPaths.load();
		assertTrue(paths.size() > 250);
		for (String[] leg : new String[][]{{"start", "B1"}, {"B1", "B vent"}, {"B vent", "B2"}, {"B3", "B rock"},
			{"B rock", "B chamber"}, {"B chamber", "C vent N"}, {"C vent N", "B vent N"}, {"B vent N", "B5"}, {"B chamber", "E stair"}})
		{
			int[][] route = paths.route(leg[0], leg[1]);
			assertNotNull(leg[0] + " -> " + leg[1], route);
			assertTrue(route.length >= 2);
		}
	}

	@Test
	public void theCoachSaysWhereEachStepStartsAndEnds()
	{
		Game g = new Game(new int[]{50, 50, 50});
		g.stability = 60;
		StrategyCoach.Instruction i = g.tick();
		assertEquals("start", i.from);
		assertEquals("B1", i.place);
		g.now = 270;
		g.coach.onBoulderBroken(1, g.observation());
		g.coach.onBoulderBroken(2, g.observation());
		i = g.breakBoulder(3);
		assertEquals("B3", i.from);
		assertEquals("B rock", i.place);
		assertEquals("B chamber", i.nextPlace);
		i = g.obey(i);
		assertEquals("B rock", i.from);
		assertEquals("B chamber", i.place);
		// Another cap fits before B4 arrives (it's at the same chamber).
		assertEquals("B chamber", i.nextPlace);
	}

	@Test
	public void onlyTheTilesYouStandOnAreDrawn()
	{
		// A stock route: 6 land tiles, then 2 lava tiles, then land to the end.
		int[][] route = {{0, 0, 0, -1}, {0, 1, 0, -1}, {0, 2, 0, -1}, {0, 3, 0, -1}, {0, 4, 0, -1}, {0, 5, 0, -1},
			{0, 6, 1, -1}, {0, 7, 1, -1}, {0, 8, 0, -1}, {0, 9, 0, -1}};
		CoachPaths.markStoodOn(route);
		int[] stood = new int[route.length];
		for (int i = 0; i < route.length; i++)
		{
			stood[i] = route[i][3];
		}
		// Not the start (you're on it), every second land tile, every lava tile, the last tile.
		org.junit.Assert.assertArrayEquals(new int[]{0, 0, 1, 0, 1, 0, 1, 1, 0, 1}, stood);
	}

	@Test
	public void theRouteDrawnStartsWhereYouAre()
	{
		CoachPaths paths = CoachPaths.load();
		// B3 mined from its east side (9,-6) or its west side (2,-6): B3 -> B rock starts from that side.
		int[][] east = paths.route("B3", "B rock", 9, -6);
		int[][] west = paths.route("B3", "B rock", 2, -6);
		assertNotNull(east);
		assertNotNull(west);
		assertTrue(Math.abs(east[0][0] - 9) <= 3);
		assertTrue(Math.abs(west[0][0] - 2) <= 3);
	}

	@Test
	public void whileMiningB1TheBCheckComesBeforeTheFix()
	{
		// All three vents low and falling, B unknown: the B check comes first as B1 breaks.
		java.util.List<int[]> vents = new ArrayList<>();
		for (int b = 15; b <= 30; b++)
		{
			vents.add(new int[]{20, b, 25});
		}
		Game g = new Game(vents.toArray(new int[0][]));
		g.stability = 60;
		g.atBoulder = true;
		g.now = 45;
		g.update = true;
		g.boulderSeconds = 30;
		StrategyCoach.Instruction i = g.tick();
		if (i.action == CoachAction.MINE_BOULDER)
		{
			assertEquals("Check the B vent", i.next);
		}
	}

	@Test
	public void fromAnySideOfABoulderTheRouteStartsAtYourFeet()
	{
		CoachPaths paths = CoachPaths.load();
		// Mining B2 from its north-west corner (2,-18): B2 -> B3 starts there, not on B2's east side.
		int[][] route = paths.route("B2", "B3", 2, -18);
		assertNotNull(route);
		assertTrue(java.util.Arrays.toString(route[0]), Math.max(Math.abs(route[0][0] - 2), Math.abs(route[0][1] + 18)) <= 1);
	}

	@Test
	public void aFixStillOnItsWayWhenB1BreaksIsDoneBeforeDecidingOnTheBCheck()
	{
		// A and B falling fast from low values: a fix-now detour while mining B1.
		Game g = new Game(new int[]{20, 25, 50});
		g.stability = 60;
		g.atBoulder = true;
		g.now = 30;
		g.update = true;
		StrategyCoach.Instruction i = g.tick();
		org.junit.Assume.assumeTrue("needs a fix now", i.action == CoachAction.TAKE_ROCK || i.action == CoachAction.FIX_CHAMBER);
		// The player keeps mining and B1 breaks before the fixes are done.
		g.now = 80;
		g.coach.onBoulderBroken(1, g.observation());
		g.boulder = 2;
		g.atBoulder = false;
		i = g.tick();
		// The fixes come first, and no B check is queued behind them while A and B are being fixed.
		int guard = 0;
		boolean sawCheckBeforeFixesDone = false;
		while (i.action != CoachAction.MINE_BOULDER && guard++ < 10)
		{
			if (i.action == CoachAction.CHECK_VENT && (g.chamber & 3) != 3)
			{
				sawCheckBeforeFixesDone = true;
			}
			i = g.obey(i);
		}
		assertFalse(sawCheckBeforeFixesDone);
	}

	@Test
	public void theCapsRouteGoesFromTheRockToTheChamber()
	{
		// Standing at the B rock: the route to the B chamber is the couple of tiles between them.
		int[][] route = CoachPaths.load().route("B rock", "B chamber", 12, 11);
		assertNotNull(route);
		assertTrue("length " + route.length, route.length <= 4);
		for (int[] tile : route)
		{
			assertTrue(java.util.Arrays.toString(tile), tile[1] >= 10 && tile[1] <= 14);
		}
	}

	@Test
	public void leavingIsCalledOffWhenTheDangerClears()
	{
		Game g = new Game(new int[]{50, 50, 50});
		g.stability = 60;
		g.now = 250;
		g.coach.onBoulderBroken(1, g.observation());
		g.coach.onBoulderBroken(2, g.observation());
		g.boulder = 4;
		g.coach.onBoulderBroken(3, g.observation());
		g.candidates = new ArrayList<>(Collections.singletonList(new int[]{0, 0, 0}));
		g.weights = new double[]{1};
		g.stability = 20;
		g.now = 320;
		g.atBoulder = true;
		assertEquals(CoachAction.LEAVE, g.tick().action);
		// Fixed (vents at 50 now, stability back up): carry on instead of leaving.
		g.candidates = new ArrayList<>(Collections.singletonList(new int[]{50, 50, 50}));
		g.stability = 80;
		g.now = 324;
		StrategyCoach.Instruction i = g.tick();
		assertTrue(i.text, i.action != CoachAction.LEAVE);
	}

	@Test
	public void aSkippedBCheckLeavesNoStaleFixBehind()
	{
		// B1 breaks in a risky game: the coach queues the B check (and the fix decision after it).
		java.util.List<int[]> vents = new ArrayList<>();
		for (int b = 15; b <= 30; b++)
		{
			vents.add(new int[]{20, b, 25});
		}
		Game g = new Game(vents.toArray(new int[0][]));
		g.stability = 60;
		g.now = 90;
		StrategyCoach.Instruction i = g.breakBoulder(1);
		org.junit.Assume.assumeTrue("needs a B check", i.action == CoachAction.CHECK_VENT);
		// The player skips it and mines B2; B2 breaks. No B1 fix may turn up now.
		g.now = 170;
		i = g.breakBoulder(2);
		for (int k = 0; k < 5; k++)
		{
			assertTrue(i.text, i.text == null || !(i.text.contains("A rock") || i.text.startsWith("Check the B vent")) || g.coach.getBouldersBroken() < 2);
			g.now += 1;
			i = g.tick();
		}
		// And at 5:00 nothing from the first half is left queued.
		g.now = 301;
		g.coach.onBoulderBroken(3, g.observation());
		i = g.tick();
		assertTrue(i.text, i.action == CoachAction.TAKE_ROCK || i.action == CoachAction.CAP_CHAMBER || i.action == CoachAction.MINE_BOULDER || i.action == CoachAction.LEAVE);
	}
}
