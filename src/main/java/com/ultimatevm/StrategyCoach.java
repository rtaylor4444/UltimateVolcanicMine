package com.ultimatevm;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * Plays the simulator's best strategy ("straight to B4", volcanicMine optStrategy.py) alongside the
 * player and always says the one thing to do next: mine a boulder, check a vent, fix or cap a
 * chamber, take a rock, leave or teleport. The decisions are the simulator's own (see
 * {@link HalfOnePlanner} and {@link HalfTwoPlanner}), made at the same moments; the coach follows the
 * game (boulders breaking, vents checked, chambers flipped, rocks taken) to tick each step off.
 *
 * <p>Pure logic: the plugin feeds it an {@link Observation} every tick and boulder-break events.
 */
final class StrategyCoach
{
	static final double TICK = 0.6;
	static final int RESET_TIME = 300;
	private static final String[] VENT = {"A", "B", "C"};

	/** What the plugin sees this tick. */
	static final class Observation
	{
		/** Game seconds elapsed (0-600). */
		double now;
		/** First simulation second still to come (predictions start here). */
		int startSecond;
		int stability;
		int chamberStatus;
		int[] rawVents = {VentRules.UNKNOWN, VentRules.UNKNOWN, VentRules.UNKNOWN};
		/** The boulder being mined or rolled to (1-5). */
		int boulderNumber = 1;
		/** The player is at the current boulder. */
		boolean atBoulder;
		/** Seconds left on the current boulder (negative = unknown, use a whole stage). */
		double boulderSecondsRemaining = -1;
		int rocksInHand;
		/** Chamber flips made this game (each pays as a cap, up to 6). */
		int capsMade;
		int playerCount = 1;
		/** A stability update has just happened (a checkpoint while mining). */
		boolean stabilityUpdate;
		/** Changes whenever the possible vents change (the walk-out time is cached against it). */
		int predictorVersion;
		/** Which rock piles have a rock to take (A, B, C). */
		boolean[] rockAvailable = {true, true, true};
	}

	/** Receives a record of every decision with its inputs (the coach trace). */
	interface TraceSink
	{
		void record(String type, java.util.Map<String, Object> fields);
	}

	/** The predictor for the current state, or null while too many vents are still possible. */
	interface PredictorSource
	{
		StrategyPredictor get(Observation observation);
	}

	/** The next thing to do. */
	static final class Instruction
	{
		final CoachAction action;
		/** What to highlight (null = the boulder for MINE_BOULDER, nothing otherwise). */
		final MineLayout.Spot spot;
		final String text;
		/** Context: the plan, or why. */
		final String detail;
		/** The step after this one ("???" while it hangs on a decision not made yet; null = none). */
		String next;
		/** Where this step is done, where the player should be coming from, and where the next is
		 * (names of the stock routes in coach_paths.json: "B1", "B vent", "A chamber", "E stair"...). */
		String place;
		String from;
		String nextPlace;
		final boolean urgent;

		Instruction(CoachAction action, MineLayout.Spot spot, String text, String detail, boolean urgent)
		{
			this.action = action;
			this.spot = spot;
			this.text = text;
			this.detail = detail;
			this.urgent = urgent;
		}

		@Override
		public String toString()
		{
			return text + (detail == null ? "" : " (" + detail + ")");
		}

		/** A "leave" step sent to the West stairs instead (quicker from where you are). */
		Instruction byWestStairs()
		{
			if (action != CoachAction.LEAVE || spot == MineLayout.Spot.STAIRS_WEST)
			{
				return this;
			}
			Instruction west = new Instruction(action, MineLayout.Spot.STAIRS_WEST, text.replace("East", "West"), detail, urgent);
			west.next = next;
			west.from = from;
			west.place = "W stair";
			west.nextPlace = nextPlace;
			return west;
		}
	}

	// ------------------------------------------------------------------ tasks

	private abstract class Task
	{
		/** How long it takes to get out from where this task is done. */
		double exitSeconds = HalfTwoPlanner.EXIT_NEAR_B_CHAMBER;
		String why;
		/** Boulders broken when it was queued (a half-1 vent check is stale once the next breaks). */
		int queuedAtBoulders = -1;

		void start(Observation o)
		{
		}

		abstract boolean done(Observation o);

		abstract Instruction instruction(Observation o);
	}

	/** Block or unblock a chamber (a fix or a cap). Blocking without a rock: take one first. */
	private final class ChamberTask extends Task
	{
		final int vent;
		final boolean cap;
		int startBit = -1;

		ChamberTask(int vent, boolean cap, String why)
		{
			this.vent = vent;
			this.cap = cap;
			this.why = why;
		}

		@Override
		void start(Observation o)
		{
			startBit = o.chamberStatus & (1 << vent);
		}

		@Override
		boolean done(Observation o)
		{
			return startBit >= 0 && (o.chamberStatus & (1 << vent)) != startBit;
		}

		@Override
		Instruction instruction(Observation o)
		{
			boolean blocked = (o.chamberStatus & (1 << vent)) != 0;
			String verb = cap ? "Cap" : "Fix " + VENT[vent];
			if (!blocked && o.rocksInHand <= 0)
			{
				return new Instruction(CoachAction.TAKE_ROCK, MineLayout.Spot.rock(vent),
					"Take the " + VENT[vent] + " rock", verb + " next: block the " + VENT[vent] + " chamber", false);
			}
			String how = (blocked ? "unblock" : "block") + " the " + VENT[vent] + " chamber";
			String text = cap ? "Cap: " + how + (o.capsMade < HalfTwoPlanner.MAX_CAPS
				? " (" + (o.capsMade + 1) + "/" + HalfTwoPlanner.MAX_CAPS + ")" : "") : verb + ": " + how;
			return new Instruction(cap ? CoachAction.CAP_CHAMBER : CoachAction.FIX_CHAMBER, MineLayout.Spot.chamber(vent), text, why, false);
		}
	}

	private final class CheckTask extends Task
	{
		final int vent;
		boolean onTheWay;

		CheckTask(int vent, String why)
		{
			this.vent = vent;
			this.why = why;
		}

		@Override
		boolean done(Observation o)
		{
			return o.rawVents[vent] != VentRules.UNKNOWN;
		}

		@Override
		Instruction instruction(Observation o)
		{
			return new Instruction(CoachAction.CHECK_VENT, MineLayout.Spot.vent(vent, o.now >= RESET_TIME),
				"Check the " + VENT[vent] + " vent" + (onTheWay ? " (on the way to fix " + VENT[vent] + ")" : ""), why, false);
		}
	}

	private final class RockTask extends Task
	{
		final int pile;
		int startRocks = Integer.MAX_VALUE;
		/** For the end caps: drops out once no cap left needs a rock (null = always wanted). */
		java.util.function.Predicate<Observation> stillNeeded;

		RockTask(int pile, String why)
		{
			this.pile = pile;
			this.why = why;
		}

		@Override
		void start(Observation o)
		{
			startRocks = o.rocksInHand;
		}

		@Override
		boolean done(Observation o)
		{
			return o.rocksInHand > startRocks || (stillNeeded != null && !stillNeeded.test(o));
		}

		@Override
		Instruction instruction(Observation o)
		{
			return new Instruction(CoachAction.TAKE_ROCK, MineLayout.Spot.rock(pile), "Take the " + VENT[pile] + " rock", why, false);
		}
	}

	/** Caps at the B chamber on the way out, as many as fit. */
	private final class EndCapsTask extends Task
	{
		EndCapsTask()
		{
			exitSeconds = HalfTwoPlanner.exitSeconds(HalfTwoPlanner.B_CHAMBER_TO_E_STAIR);
			why = "then leave by the East stairs";
		}

		@Override
		boolean done(Observation o)
		{
			boolean blocked = (o.chamberStatus & 2) != 0;
			return !HalfTwoPlanner.anotherEndCap(predictorFor(o), o.now, o.capsMade, blocked, o.rocksInHand > 0);
		}

		@Override
		Instruction instruction(Observation o)
		{
			return new ChamberTask(1, true, why).instruction(o);
		}
	}

	/** A decision made when the steps before it are done: replaced by the steps it decides on. */
	private final class DecideTask extends Task
	{
		final Function<Observation, List<Task>> decide;
		/** What it will decide, worked out ahead for the "Next" line (null = can't be known yet). */
		Function<Observation, String> preview;

		DecideTask(Function<Observation, List<Task>> decide)
		{
			this.decide = decide;
		}

		@Override
		boolean done(Observation o)
		{
			return false;
		}

		@Override
		Instruction instruction(Observation o)
		{
			return null;
		}
	}

	// ------------------------------------------------------------------ state

	private final PredictorSource predictorSource;
	// Where the heavy decisions run (null = right away, as in tests).
	private java.util.concurrent.Executor background;
	private TraceSink trace;
	private int traceId;
	private final Deque<PendingDecision> pending = new ArrayDeque<>();
	// Other background work (walk-out time, early fixes during B4), applied in order.
	private final Deque<PendingWork<?>> work = new ArrayDeque<>();
	// Bumped on reset so a decision for an earlier game is never applied.
	private int generation;
	private final Deque<Task> tasks = new ArrayDeque<>();
	private Task startedTask;
	private int bouldersBroken;
	private final boolean[] arrived = new boolean[6];
	private final boolean[] detourDone = new boolean[3];
	private boolean bCheckedHalf1;
	private boolean teleport;
	private boolean leaving;
	private String leaveReason;
	private boolean mineScaled;
	private boolean earlyFixB;
	private boolean earlyFixC;
	private boolean headedToB5;
	private double b4Done = Double.NaN;
	private double b3Done = Double.NaN;
	private HalfOnePlanner.Decision plan;
	private StrategyPredictor cachedPredictor;
	private Observation cachedFor;
	private final List<String> log = new ArrayList<>();
	private Instruction current;
	private long leaveKey = Long.MIN_VALUE;
	private double leaveAt = Double.MAX_VALUE;

	StrategyCoach(PredictorSource predictorSource)
	{
		this.predictorSource = predictorSource;
	}

	void setTrace(TraceSink trace)
	{
		this.trace = trace;
	}

	private void trace(String kind, StrategyPredictor p, double now, Object... keyValues)
	{
		if (trace == null)
		{
			return;
		}
		java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
		m.put("kind", kind);
		m.put("now", now);
		for (int i = 0; i + 1 < keyValues.length; i += 2)
		{
			m.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
		}
		m.put("id", traceId++);
		if (p != null)
		{
			// Expanded (and packed) by TraceExpander on the trace's own thread.
			m.put(TraceExpander.PREDICTOR, p);
		}
		trace.record("decision", m);
	}

	void setBackground(java.util.concurrent.Executor background)
	{
		this.background = background;
	}

	/** True while background work started by the coach hasn't finished (tests wait on it). */
	boolean isWorking()
	{
		for (PendingDecision d : pending)
		{
			if (!d.result.isDone())
			{
				return true;
			}
		}
		for (PendingWork<?> w : work)
		{
			if (!w.result.isDone())
			{
				return true;
			}
		}
		return false;
	}

	void reset()
	{
		generation++;
		pending.clear();
		work.clear();
		tasks.clear();
		startedTask = null;
		bouldersBroken = 0;
		java.util.Arrays.fill(arrived, false);
		java.util.Arrays.fill(detourDone, false);
		bCheckedHalf1 = false;
		teleport = false;
		leaving = false;
		leaveReason = null;
		mineScaled = false;
		earlyFixB = false;
		earlyFixC = false;
		headedToB5 = false;
		b4Done = Double.NaN;
		b3Done = Double.NaN;
		plan = null;
		cachedPredictor = null;
		cachedFor = null;
		log.clear();
		current = null;
		leaveKey = Long.MIN_VALUE;
		leaveAt = Double.MAX_VALUE;
		lastPlace = "start";
		lastShownPlace = null;
		awaitingDecisionAt = -1;
		bCheckComing = null;
		lastRocks = 0;
	}

	Instruction getInstruction()
	{
		return current;
	}

	/** Decisions made since the last call (for chat / the log). */
	List<String> takeLog()
	{
		List<String> out = new ArrayList<>(log);
		log.clear();
		return out;
	}

	int getBouldersBroken()
	{
		return bouldersBroken;
	}

	/** The coach's state, for the debug view. */
	List<String> debugLines()
	{
		List<String> out = new ArrayList<>();
		out.add("Boulders broken: " + bouldersBroken + "   plan: " + (plan == null ? "-" : plan));
		out.add("Steps queued: " + (tasks.isEmpty() ? "none" : ""));
		int n = 0;
		for (Task task : tasks)
		{
			if (n++ >= 6)
			{
				out.add("  ...");
				break;
			}
			out.add("  " + describe(task));
		}
		out.add("Pending decisions: " + pending.size() + (teleport ? "   TELEPORT" : "") + (leaving ? "   LEAVING (" + leaveReason + ")" : ""));
		out.add(String.format(Locale.ROOT, "Early fixes B/C: %s/%s   walk out by: %s", earlyFixB, earlyFixC,
			leaveAt == Double.MAX_VALUE ? "-" : clock(leaveAt)));
		return out;
	}

	private static String clock(double now)
	{
		int remaining = (int) Math.max(0, Math.round(600 - now));
		return String.format(Locale.ROOT, "%d:%02d", remaining / 60, remaining % 60);
	}

	private String describe(Task task)
	{
		String name = task.getClass().getSimpleName().replace("Task", "");
		if (task instanceof ChamberTask)
		{
			ChamberTask t = (ChamberTask) task;
			name += " " + VENT[t.vent] + (t.cap ? " (cap)" : "");
		}
		else if (task instanceof CheckTask)
		{
			name += " " + VENT[((CheckTask) task).vent];
		}
		else if (task instanceof RockTask)
		{
			name += " " + VENT[((RockTask) task).pile];
		}
		return name + (task.why == null ? "" : " - " + task.why);
	}

	/** Someone left early with the mine still scaled to the starting team: leave too. */
	void onMineScaled()
	{
		mineScaled = true;
	}

	private StrategyPredictor predictorFor(Observation o)
	{
		if (cachedFor != o)
		{
			cachedPredictor = predictorSource.get(o);
			cachedFor = o;
		}
		return cachedPredictor;
	}

	private void note(Observation o, String message)
	{
		note(o.now, message);
	}

	private void note(double now, String message)
	{
		int remaining = (int) Math.max(0, Math.round(600 - now));
		log.add(String.format(Locale.ROOT, "%d:%02d %s", remaining / 60, remaining % 60, message));
	}

	// ------------------------------------------------------------------ events

	/** Boulder number {@code number} (1-5) just broke. */
	void onBoulderBroken(int number, Observation o)
	{
		if (number <= bouldersBroken)
		{
			return;
		}
		bouldersBroken = number;
		lastPlace = "B" + number;
		// A first-half vent check still waiting when the next boulder breaks has missed its decision.
		tasks.removeIf(t -> t instanceof CheckTask && t.queuedAtBoulders >= 0 && t.queuedAtBoulders < number && number <= 3);
		if (teleport || leaving || mineScaled)
		{
			return;
		}
		switch (number)
		{
			case 1:
				afterB1(o);
				break;
			case 2:
				decideHalfOne(o, HalfOnePlanner.Checkpoint.B2_BROKEN, -1, false);
				break;
			case 3:
				// B3 -> take the B rock -> cap at the B chamber as many times as fit before B4 rolls in
				// (the simulator's "max" window caps; its default is one, within noise of this).
				b3Done = o.now;
				tasks.add(new RockTask(1, "for caps while B4 rolls in"));
				DecideTask windowCaps = new DecideTask(obs ->
				{
					List<Task> caps = new ArrayList<>();
					int n = HalfTwoPlanner.windowCaps(obs.now, b3Done, (obs.chamberStatus & 2) != 0, obs.rocksInHand, obs.capsMade);
					trace("window_caps", null, obs.now, "b3Done", b3Done, "rocks", obs.rocksInHand, "caps", obs.capsMade,
						"chamber", obs.chamberStatus, "result", n);
					for (int i = 0; i < n; i++)
					{
						caps.add(new ChamberTask(1, true, "while B4 rolls in"));
					}
					return caps;
				});
				windowCaps.preview = obs -> HalfTwoPlanner.windowCaps(obs.now, b3Done, (obs.chamberStatus & 2) != 0, obs.rocksInHand, obs.capsMade) > 0
					? new ChamberTask(1, true, null).instruction(obs).text : "Mine boulder 4";
				tasks.add(windowCaps);
				break;
			case 4:
				afterB4(o);
				break;
			case 5:
				afterB5(o);
				break;
			default:
				break;
		}
	}

	private void afterB1(Observation o)
	{
		if (!tasks.isEmpty())
		{
			// A fix is still on its way (B1 broke before you set off): decide on the B check and
			// the next fix once it's done, with the chambers as they are then. (The simulator always
			// finishes a fix-now detour before B1 breaks.)
			tasks.add(new DecideTask(obs ->
			{
				afterB1Decide(obs);
				return new ArrayList<>();
			}));
			return;
		}
		afterB1Decide(o);
	}

	private void afterB1Decide(Observation o)
	{
		StrategyPredictor p = predictorFor(o);
		boolean bKnown = o.rawVents[1] != VentRules.UNKNOWN || (p != null && p.isKnown(1));
		boolean check = !bCheckedHalf1 && !bKnown && p != null && HalfOnePlanner.shouldCheckB(p, o.now);
		trace("b_check", p, o.now, "bKnown", bKnown, "result", check);
		if (check)
		{
			bCheckedHalf1 = true;
			note(o, "Check B as B1 breaks (no-fix blow-up chance " + percent(p.deathRate(RESET_TIME)) + ")");
			tasks.add(new CheckTask(1, "it decides the fix"));
			tasks.add(new DecideTask(obs ->
			{
				decideHalfOne(obs, HalfOnePlanner.Checkpoint.B1_BROKEN, -1, false);
				return new ArrayList<>();
			}));
			return;
		}
		decideHalfOne(o, HalfOnePlanner.Checkpoint.B1_BROKEN, -1, false);
	}

	private void decideHalfOne(Observation o, HalfOnePlanner.Checkpoint checkpoint, double miningRemaining, boolean whileMining)
	{
		if (bouldersBroken != checkpoint.bouldersCompleted || o.now >= RESET_TIME)
		{
			return; // that checkpoint has passed (e.g. a decision queued behind a skipped B check)
		}
		StrategyPredictor p = predictorFor(o);
		if (p == null || p.isEmpty())
		{
			return;
		}
		double now = o.now;
		if (background == null)
		{
			applyHalfOne(HalfOnePlanner.decide(p, checkpoint, now, miningRemaining, whileMining), checkpoint, whileMining, now, p, miningRemaining);
			return;
		}
		// Up to ~0.1 s with thousands of possible vents: worked out on the background thread and
		// applied on a later tick (see applyFinishedDecisions).
		int forGeneration = generation;
		if (checkpoint == HalfOnePlanner.Checkpoint.B1_BROKEN || checkpoint == HalfOnePlanner.Checkpoint.B2_BROKEN)
		{
			awaitingDecisionAt = checkpoint.bouldersCompleted;
		}
		pending.add(new PendingDecision(java.util.concurrent.CompletableFuture.supplyAsync(
			() -> HalfOnePlanner.decide(p, checkpoint, now, miningRemaining, whileMining), background),
			checkpoint, whileMining, now, forGeneration, p, miningRemaining));
	}

	private static final class PendingDecision
	{
		final java.util.concurrent.CompletableFuture<HalfOnePlanner.Decision> result;
		final HalfOnePlanner.Checkpoint checkpoint;
		final boolean whileMining;
		final double now;
		final int generation;
		final StrategyPredictor predictor;
		final double miningRemaining;

		PendingDecision(java.util.concurrent.CompletableFuture<HalfOnePlanner.Decision> result, HalfOnePlanner.Checkpoint checkpoint,
			boolean whileMining, double now, int generation, StrategyPredictor predictor, double miningRemaining)
		{
			this.predictor = predictor;
			this.miningRemaining = miningRemaining;
			this.result = result;
			this.checkpoint = checkpoint;
			this.whileMining = whileMining;
			this.now = now;
			this.generation = generation;
		}
	}

	private static final class PendingWork<T>
	{
		final java.util.concurrent.CompletableFuture<T> result;
		final java.util.function.Consumer<T> apply;
		final int generation;

		PendingWork(java.util.concurrent.CompletableFuture<T> result, java.util.function.Consumer<T> apply, int generation)
		{
			this.result = result;
			this.apply = apply;
			this.generation = generation;
		}

		void applyResult()
		{
			apply.accept(result.join());
		}
	}

	/** Works something out on the background thread (right away without one) and applies it on a later tick. */
	private <T> void later(java.util.function.Supplier<T> compute, java.util.function.Consumer<T> apply)
	{
		if (background == null)
		{
			apply.accept(compute.get());
			return;
		}
		work.add(new PendingWork<>(java.util.concurrent.CompletableFuture.supplyAsync(compute, background), apply, generation));
	}

	private void applyFinishedWork()
	{
		while (!work.isEmpty() && work.peekFirst().result.isDone())
		{
			PendingWork<?> done = work.pollFirst();
			if (done.generation == generation && !done.result.isCompletedExceptionally())
			{
				done.applyResult();
			}
		}
	}

	/** Applies background decisions that have finished, in the order they were asked for. */
	private void applyFinishedDecisions()
	{
		while (!pending.isEmpty() && pending.peekFirst().result.isDone())
		{
			PendingDecision done = pending.pollFirst();
			if (done.generation != generation || done.result.isCompletedExceptionally())
			{
				continue;
			}
			applyHalfOne(done.result.join(), done.checkpoint, done.whileMining, done.now, done.predictor, done.miningRemaining);
		}
	}

	private void applyHalfOne(HalfOnePlanner.Decision decision, HalfOnePlanner.Checkpoint checkpoint, boolean whileMining, double now,
		StrategyPredictor p, double miningRemaining)
	{
		if (awaitingDecisionAt == checkpoint.bouldersCompleted
			&& (checkpoint == HalfOnePlanner.Checkpoint.B1_BROKEN || checkpoint == HalfOnePlanner.Checkpoint.B2_BROKEN))
		{
			awaitingDecisionAt = -1;
		}
		trace("h1", p, now, "checkpoint", checkpoint.name(), "remaining", miningRemaining, "whileMining", whileMining,
			"result", decision.plan.name(), "deathRate", decision.deathRate, "startsNow", decision.startsNow(checkpoint),
			"stale", bouldersBroken != checkpoint.bouldersCompleted);
		if (bouldersBroken != checkpoint.bouldersCompleted || now >= RESET_TIME)
		{
			// Its boulder has moved on (or it's 5:00): the decision's moment has passed.
			if (awaitingDecisionAt == checkpoint.bouldersCompleted)
			{
				awaitingDecisionAt = -1;
			}
			return;
		}
		if (teleport || leaving || mineScaled)
		{
			return;
		}
		plan = decision;
		if (decision.plan == HalfOnePlanner.Plan.TELEPORT)
		{
			teleport = true;
			tasks.clear();
			note(now, "Teleport: no plan keeps the mine from blowing up before 5:00");
			return;
		}
		if (decision.plan != HalfOnePlanner.Plan.NONE)
		{
			note(now, checkpointLabel(checkpoint, whileMining) + ": " + decision.plan.label + " (" + percent(decision.deathRate) + " risk)");
		}
		if (decision.startsNow(checkpoint))
		{
			if (decision.plan.window == HalfOnePlanner.Window.NOW_B1)
			{
				detourDone[1] = true;
			}
			if (decision.plan.window == HalfOnePlanner.Window.NOW_B2)
			{
				detourDone[2] = true;
			}
			for (HalfOnePlanner.Step step : decision.plan.steps)
			{
				if (step.action == CoachAction.CHECK_VENT)
				{
					// Fix routes that walk past the vent check it on the way (50 points for a tick).
					CheckTask check = new CheckTask(step.vent, decision.plan.label);
					check.onTheWay = true;
					tasks.add(check);
				}
				else
				{
					tasks.add(new ChamberTask(step.vent, false, decision.plan.label));
				}
			}
		}
	}

	private void afterB4(Observation o)
	{
		b4Done = o.now;
		if (o.rocksInHand <= 0)
		{
			tasks.add(new RockTask(1, "for the B chamber"));
		}
		DecideTask atChamber = new DecideTask(obs ->
		{
			List<Task> next = new ArrayList<>();
			StrategyPredictor p = predictorFor(obs);
			if (p == null)
			{
				next.add(new CheckTask(2, "then the B vent"));
				next.add(new CheckTask(1, "decides the fix before B5"));
				next.add(bVentDecision());
				return next;
			}
			boolean flip = HalfTwoPlanner.flipBAfterB4(p);
			trace("flip_b", p, obs.now, "result", flip);
			if (flip)
			{
				note(obs, "B is heading away from 50 (" + percent(p.pHeadingAway(1)) + "): fix B leaving the chamber");
				next.add(new ChamberTask(1, false, "B is heading away from 50"));
			}
			next.add(new DecideTask(o2 -> lateCapsAndC(o2)));
			return next;
		});
		// Taking the rock changes nothing these depend on: say what comes after it straight away.
		atChamber.preview = this::previewAtBChamber;
		tasks.add(atChamber);
	}

	/** The first step the B-chamber decisions after B4 will give (the same rules, worked out ahead). */
	private String previewAtBChamber(Observation o)
	{
		long key = ((long) o.predictorVersion * 31 + o.chamberStatus) * 31 + o.rocksInHand + (long) Math.floor(o.now / 3) * 1_000_003L;
		if (key == atChamberPreviewKey && atChamberPreview != null)
		{
			return atChamberPreview;
		}
		atChamberPreviewKey = key;
		StrategyPredictor p = predictorFor(o);
		String out;
		if (p == null)
		{
			out = "Check the C vent";
		}
		else if (HalfTwoPlanner.flipBAfterB4(p))
		{
			out = new ChamberTask(1, false, null).instruction(o).text;
		}
		else if (HalfTwoPlanner.capNowBecauseB5FinishesLate(o.now, b4Done) && o.rocksInHand > 0 && o.capsMade + 2 <= HalfTwoPlanner.MAX_CAPS)
		{
			out = new ChamberTask(1, true, null).instruction(o).text;
		}
		else
		{
			boolean fixC = HalfTwoPlanner.fixCAfterB4(p, earlyFixC);
			if (fixC)
			{
				out = new ChamberTask(2, false, null).instruction(o).text;
			}
			else if (HalfTwoPlanner.skipChecksForB5(p, o.now, b4Done, false))
			{
				out = "Mine boulder 5";
			}
			else
			{
				out = "Check the C vent";
			}
		}
		atChamberPreview = out;
		return out;
	}

	private long atChamberPreviewKey = Long.MIN_VALUE;
	private String atChamberPreview;

	private List<Task> lateCapsAndC(Observation o)
	{
		List<Task> next = new ArrayList<>();
		StrategyPredictor p = predictorFor(o);
		boolean late = HalfTwoPlanner.capNowBecauseB5FinishesLate(o.now, b4Done);
		trace("late_caps", null, o.now, "b4Done", b4Done, "rocks", o.rocksInHand, "caps", o.capsMade, "result", late);
		if (late)
		{
			int rocks = o.rocksInHand;
			int caps = o.capsMade;
			while (rocks > 0 && caps + 2 <= HalfTwoPlanner.MAX_CAPS)
			{
				next.add(new ChamberTask(1, true, "B5 finishes too late to cap after it"));
				next.add(new ChamberTask(1, true, "B5 finishes too late to cap after it"));
				rocks--;
				caps += 2;
			}
		}
		boolean fixC4 = p != null && HalfTwoPlanner.fixCAfterB4(p, earlyFixC);
		trace("c_after_b4", p, o.now, "cDone", earlyFixC, "result", fixC4);
		if (fixC4)
		{
			earlyFixC = true;
			note(o, "C is heading away from 50 (" + percent(p.pHeadingAway(2)) + "): fix C on the way to the C vent");
			ChamberTask fixC = new ChamberTask(2, false, "C is heading away from 50");
			fixC.exitSeconds = HalfTwoPlanner.EXIT_FAR;
			next.add(fixC);
		}
		// Coach-only rule (the simulator always checks): if checking C and B would leave too little
		// time to finish B5 and get out, but going straight there wouldn't, and the mine is safe, skip
		// the checks - B5's finish (100 points) and the end of B5 are worth more than the two checks.
		// Steps already queued here (caps, a C fix) take time first.
		double queued = 0;
		for (Task t : next)
		{
			queued += t instanceof ChamberTask && ((ChamberTask) t).cap ? HalfTwoPlanner.CAP : 0;
		}
		boolean straightToB5 = p != null && HalfTwoPlanner.skipChecksForB5(p, o.now + queued * TICK, b4Done, fixC4);
		trace("skip_checks", p, o.now, "b4Done", b4Done, "cFixed", fixC4, "result", straightToB5);
		if (straightToB5)
		{
			note(o, "Short of time: straight to B5 (with the vent checks B5 couldn't be finished)");
			headedToB5 = true;
			return next;
		}
		CheckTask checkC = new CheckTask(2, "then the B vent");
		checkC.exitSeconds = HalfTwoPlanner.EXIT_C_VENT_LEG;
		next.add(checkC);
		CheckTask checkB = new CheckTask(1, "decides the fix before B5");
		checkB.exitSeconds = HalfTwoPlanner.EXIT_B_VENT_LEG;
		next.add(checkB);
		next.add(bVentDecision());
		return next;
	}

	private DecideTask bVentDecision()
	{
		DecideTask decide = new DecideTask(o ->
		{
			headedToB5 = true;
			List<Task> next = new ArrayList<>();
			StrategyPredictor p = predictorFor(o);
			if (p == null)
			{
				return next;
			}
			HalfTwoPlanner.B5Option option = HalfTwoPlanner.chooseAtBVent(p, o.now, b4Done, o.rocksInHand > 0, o.capsMade);
			trace("b_vent", p, o.now, "b4Done", b4Done, "rock", o.rocksInHand > 0, "caps", o.capsMade, "result", option.name());
			note(o, "At the B vent: " + option.label);
			for (int vent : option.vents())
			{
				ChamberTask fix = new ChamberTask(vent, false, option.label);
				fix.exitSeconds = HalfTwoPlanner.EXIT_FAR;
				next.add(fix);
			}
			return next;
		});
		decide.exitSeconds = HalfTwoPlanner.EXIT_B_VENT_LEG;
		return decide;
	}

	/**
	 * Whether the caps still to make (up to 6) include a block the rocks in hand can't cover. From a
	 * blocked chamber the caps go unblock, block, unblock...; only the blocks use a rock. (The
	 * simulator takes the rock whenever the pile has one: a tick for nothing when it isn't needed.)
	 */
	static boolean endCapsNeedARock(Observation o)
	{
		boolean blocked = (o.chamberStatus & 2) != 0;
		int blocks = 0;
		for (int cap = o.capsMade; cap < HalfTwoPlanner.MAX_CAPS; cap++)
		{
			if (!blocked)
			{
				blocks++;
			}
			blocked = !blocked;
		}
		return blocks > o.rocksInHand;
	}

	/** While mining B5: the first step after it (caps on the way out, or straight out). */
	private String afterB5Preview(Observation o)
	{
		long key = ((long) o.predictorVersion * 31 + o.capsMade) * 31 + o.chamberStatus * 7L + o.rocksInHand
			+ (long) Math.floor(o.now / 3) * 1_000_003L;
		if (key == previewKey && preview != null)
		{
			return preview;
		}
		previewKey = key;
		double finish = o.now + Math.max(0, o.boulderSecondsRemaining >= 0 ? o.boulderSecondsRemaining : HalfTwoPlanner.B5_SECONDS);
		StrategyPredictor p = predictorFor(o);
		if (p == null || !HalfTwoPlanner.capAfterB5(p, finish, o.capsMade))
		{
			preview = "Leave by the East stairs";
		}
		else if (o.rockAvailable[1] && endCapsNeedARock(o))
		{
			preview = "Take the B rock";
		}
		else
		{
			Instruction cap = new ChamberTask(1, true, null).instruction(o);
			preview = cap.action == CoachAction.TAKE_ROCK ? "Leave by the East stairs" : cap.text;
		}
		return preview;
	}

	private long previewKey = Long.MIN_VALUE;
	private String preview;

	private void afterB5(Observation o)
	{
		tasks.clear();
		StrategyPredictor p = predictorFor(o);
		boolean endCaps = HalfTwoPlanner.capAfterB5(p, o.now, o.capsMade);
		trace("cap_after_b5", p, o.now, "caps", o.capsMade, "rockAvailable", o.rockAvailable[1], "result", endCaps);
		if (endCaps)
		{
			if (o.rockAvailable[1] && endCapsNeedARock(o))
			{
				RockTask rock = new RockTask(1, "for the caps");
				rock.stillNeeded = StrategyCoach::endCapsNeedARock;
				rock.exitSeconds = HalfTwoPlanner.EXIT_FAR;
				tasks.add(rock);
			}
			tasks.add(new EndCapsTask());
		}
		leaving = false;
	}

	// ------------------------------------------------------------------ every tick

	/** Updates the next step from what the plugin sees this tick. */
	Instruction update(Observation o)
	{
		current = compute(o);
		if (current != null)
		{
			if (o.rocksInHand > lastRocks && lastShownPlace != null && lastShownPlace.endsWith("rock"))
			{
				lastPlace = lastShownPlace;
			}
			lastRocks = o.rocksInHand;
			current.next = nextStep(o, current);
			current.place = placeOf(current, o.now);
			current.from = lastPlace;
			current.nextPlace = current.next == null ? null : placeForText(current.next, o.now);
			if (current.action == CoachAction.MINE_BOULDER && o.atBoulder)
			{
				lastPlace = current.place;
			}
			lastShownPlace = current.place;
		}
		return current;
	}

	// Boulders broken when a fix decision was asked for that the steps wait on (-1 = none).
	private int awaitingDecisionAt = -1;
	// Will the B vent be checked as B1 breaks (for "Next" while mining B1)? Worked out at each update.
	private Boolean bCheckComing;
	private int lastRocks;

	// Where the player should be standing now (the last step's place), for drawing the route on.
	private String lastPlace = "start";
	private String lastShownPlace;

	/** The stock-route name of where an instruction is carried out. */
	static String placeOf(Instruction i, double now)
	{
		if (i.action == CoachAction.MINE_BOULDER)
		{
			return placeForText(i.text, now);
		}
		if (i.spot == null)
		{
			return null;
		}
		switch (i.spot)
		{
			case STAIRS_EAST: return "E stair";
			case STAIRS_WEST: return "W stair";
			case CHAMBER_A: return "A chamber";
			case CHAMBER_B: return "B chamber";
			case CHAMBER_C: return "C chamber";
			case ROCK_A: return "A rock";
			case ROCK_B: return "B rock";
			case ROCK_C: return "C rock";
			case VENT_A_SOUTH: return "A vent";
			case VENT_B_SOUTH: return "B vent";
			case VENT_C_SOUTH: return "C vent";
			case VENT_A_NORTH: return "A vent N";
			case VENT_B_NORTH: return "B vent N";
			case VENT_C_NORTH: return "C vent N";
			default: return null;
		}
	}

	/** Where a step described in words ("Mine boulder 3", "Take the B rock", a plan) is carried out. */
	static String placeForText(String text, double now)
	{
		if (text == null || UNKNOWN_NEXT.equals(text))
		{
			return null;
		}
		String north = now >= RESET_TIME ? " N" : "";
		java.util.regex.Matcher m;
		if ((m = java.util.regex.Pattern.compile("^Mine boulder (\\d)").matcher(text)).find())
		{
			return "B" + m.group(1);
		}
		if ((m = java.util.regex.Pattern.compile("(?i)check the ([ABC]) vent").matcher(text)).find() && !text.startsWith("Fix") && !text.startsWith("Cap"))
		{
			return m.group(1).toUpperCase() + " vent" + north;
		}
		if ((m = java.util.regex.Pattern.compile("^Take the ([ABC]) rock").matcher(text)).find())
		{
			return m.group(1) + " rock";
		}
		if (text.startsWith("Leave"))
		{
			return "E stair";
		}
		if (text.startsWith("Cap"))
		{
			return "B chamber";
		}
		// A plan ("Fix C+B after B1 (check the C vent on the way)") starts at its vent or first chamber.
		if ((m = java.util.regex.Pattern.compile("check the ([ABC]) vent on the way").matcher(text)).find())
		{
			return m.group(1) + " vent" + north;
		}
		if ((m = java.util.regex.Pattern.compile("^Fix ([ABC])").matcher(text)).find())
		{
			return m.group(1) + " chamber";
		}
		return null;
	}

	static final String UNKNOWN_NEXT = "???";

	/** In the lobby: the strategy starts from the West stairs (B1 is mined from its north-west corner). */
	static Instruction lobbyInstruction()
	{
		Instruction i = new Instruction(CoachAction.START, MineLayout.Spot.STAIRS_WEST, "Go down the West stairs",
			"B1 is mined from its north-west corner", false);
		i.next = "Mine boulder 1";
		return i;
	}

	/** What comes after the current step, as far as it's already worked out. */
	private String nextStep(Observation o, Instruction now)
	{
		if (now.action == CoachAction.LEAVE || now.action == CoachAction.TELEPORT || now.action == CoachAction.WAIT)
		{
			return null;
		}
		java.util.Iterator<Task> it = tasks.iterator();
		Task front = it.hasNext() ? it.next() : null;
		if (front != null && !(front instanceof DecideTask))
		{
			// The current step is a queued task: the next is the one after it.
			Observation after = afterDoing(o, front, now);
			if (front instanceof EndCapsTask)
			{
				Instruction more = front.done(after) ? null : front.instruction(after);
				return more != null ? more.text : "Leave by the East stairs";
			}
			if (now.action == CoachAction.TAKE_ROCK && front instanceof ChamberTask)
			{
				// Taking the rock is the first half of this fix: the fix is next.
				return front.instruction(after).text;
			}
			if (it.hasNext())
			{
				Task second = it.next();
				if (second instanceof DecideTask)
				{
					DecideTask d = (DecideTask) second;
					String ahead = d.preview == null ? null : d.preview.apply(after);
					return ahead == null ? UNKNOWN_NEXT : ahead;
				}
				Instruction i = second.instruction(after);
				return i == null ? UNKNOWN_NEXT : i.text;
			}
			return defaultInstruction(o).text;
		}
		// Mining: what happens when this boulder breaks.
		switch (bouldersBroken)
		{
			case 0:
			case 1:
			{
				HalfOnePlanner.Window window = bouldersBroken == 0 ? HalfOnePlanner.Window.AFTER_B1 : HalfOnePlanner.Window.AFTER_B2;
				if (bouldersBroken == 1 && plan != null && plan.plan.window == window && plan.plan.steps.length > 0)
				{
					// The plan so far (decided again as the boulder breaks).
					HalfOnePlanner.Step first = plan.plan.steps[0];
					return plan.plan.label + (first.action == CoachAction.CHECK_VENT
						? " (check the " + VENT[first.vent] + " vent on the way)" : "");
				}
				if (bouldersBroken == 0)
				{
					// As B1 breaks the B vent is checked first if B is unknown and the mine's in danger
					// (the fix is decided after it); otherwise the plan's first step, or not known yet.
					if (bCheckComing == null || o.stabilityUpdate)
					{
						StrategyPredictor p = predictorFor(o);
						boolean bKnown = o.rawVents[1] != VentRules.UNKNOWN || (p != null && p.isKnown(1));
						bCheckComing = !bCheckedHalf1 && !bKnown && p != null && p.size() <= 20000
							&& HalfOnePlanner.shouldCheckB(p, o.now + Math.max(0, o.boulderSecondsRemaining));
					}
					if (bCheckComing)
					{
						return "Check the B vent";
					}
					if (plan != null && plan.plan.window == window && plan.plan.steps.length > 0)
					{
						HalfOnePlanner.Step first = plan.plan.steps[0];
						return plan.plan.label + (first.action == CoachAction.CHECK_VENT
							? " (check the " + VENT[first.vent] + " vent on the way)" : "");
					}
					return UNKNOWN_NEXT;
				}
				return "Mine boulder 3";
			}
			case 2:
				return o.capsMade < HalfTwoPlanner.MAX_CAPS || o.rocksInHand <= 0 ? "Take the B rock" : "Mine boulder 4";
			case 3:
				return o.rocksInHand <= 0 ? "Take the B rock" : UNKNOWN_NEXT;
			case 4:
				return headedToB5 ? afterB5Preview(o) : UNKNOWN_NEXT;
			default:
				return UNKNOWN_NEXT;
		}
	}

	private static String stepText(HalfOnePlanner.Step step, Observation o)
	{
		if (step.action == CoachAction.CHECK_VENT)
		{
			return "Check the " + VENT[step.vent] + " vent";
		}
		boolean blocked = (o.chamberStatus & (1 << step.vent)) != 0;
		if (!blocked && o.rocksInHand <= 0)
		{
			return "Take the " + VENT[step.vent] + " rock";
		}
		return "Fix " + VENT[step.vent] + ": " + (blocked ? "unblock" : "block") + " the " + VENT[step.vent] + " chamber";
	}

	/** The observation as it will be once the current step is done (for previewing the next). */
	private static Observation afterDoing(Observation o, Task task, Instruction now)
	{
		Observation a = copy(o);
		if (now.action == CoachAction.TAKE_ROCK)
		{
			a.rocksInHand = o.rocksInHand + 1;
		}
		else if (task instanceof ChamberTask || task instanceof EndCapsTask)
		{
			int vent = task instanceof ChamberTask ? ((ChamberTask) task).vent : 1;
			int bit = 1 << vent;
			if ((o.chamberStatus & bit) == 0)
			{
				a.rocksInHand = Math.max(0, o.rocksInHand - 1);
			}
			a.chamberStatus = o.chamberStatus ^ bit;
			a.capsMade = o.capsMade + 1;
		}
		else if (task instanceof CheckTask)
		{
			a.rawVents = o.rawVents.clone();
			a.rawVents[((CheckTask) task).vent] = 50;
		}
		return a;
	}

	private static Observation copy(Observation o)
	{
		Observation a = new Observation();
		a.now = o.now;
		a.startSecond = o.startSecond;
		a.stability = o.stability;
		a.chamberStatus = o.chamberStatus;
		a.rawVents = o.rawVents;
		a.boulderNumber = o.boulderNumber;
		a.atBoulder = o.atBoulder;
		a.boulderSecondsRemaining = o.boulderSecondsRemaining;
		a.rocksInHand = o.rocksInHand;
		a.capsMade = o.capsMade;
		a.playerCount = o.playerCount;
		a.predictorVersion = o.predictorVersion;
		a.rockAvailable = o.rockAvailable;
		return a;
	}

	private Instruction compute(Observation o)
	{
		// Team games are coached with the solo strategy too (for now; it was tuned for solo).
		if (mineScaled)
		{
			return leave("The mine is scaled to the starting team");
		}
		if (teleport)
		{
			return new Instruction(CoachAction.TELEPORT, null, "Teleport out now", "no fix keeps the mine safe to 5:00", true);
		}

		applyFinishedDecisions();
		applyFinishedWork();
		checkpoints(o);
		applyFinishedDecisions();
		applyFinishedWork();
		if (teleport)
		{
			return new Instruction(CoachAction.TELEPORT, null, "Teleport out now", "no fix keeps the mine safe to 5:00", true);
		}

		advanceTasks(o);

		// Half 2: walk out as soon as the predictor says the mine could blow up before you're out.
		if (o.now >= RESET_TIME && bouldersBroken < 5 && !mineScaled)
		{
			double exit = currentExitSeconds();
			// A fix you're on your way to make counts: assume it lands (the simulator would walk out
			// mid-trip; that's the walk-out the fix is there to prevent).
			List<Simulator.FlipEvent> fixing = fixUnderWay(o);
			// The walk-out time only moves when the vents, stability, chambers or route change:
			// recomputed then, and every 3 s.
			long key = ((((long) o.chamberStatus * 101 + o.stability) * 100_000 + o.predictorVersion) * 1000
				+ (long) Math.floor(o.now / 3)) * 31 + Double.hashCode(exit)
				+ (fixing.isEmpty() ? 0 : 7L * fixing.get(0).chamberBit + 1_000_000_007L);
			if (key != leaveKey)
			{
				leaveKey = key;
				StrategyPredictor p = predictorFor(o);
				double now = o.now;
				// With tens of thousands of possible vents right after 5:00 this takes a while:
				// worked out in the background, the previous walk-out time holding meanwhile.
				later(() -> HalfTwoPlanner.leaveTime(p, exit, fixing), at ->
				{
					leaveAt = at;
					trace("leave_time", p, now, "exit", exit, "fixing", fixing.size(), "result", at);
				});
			}
			if (!leaving && o.now >= leaveAt)
			{
				// The steps stay queued: if the danger clears (a fix, a quieter vent), carry on.
				leaving = true;
				leaveReason = o.now >= HalfTwoPlanner.GAME_END - exit - 1 ? "out before 10:00" : "the mine could blow up before you're out";
				note(o, "Leave now: " + leaveReason);
				trace("leave", null, o.now, "exit", exit, "leaveAt", leaveAt, "reason", leaveReason);
			}
			else if (leaving && o.now < leaveAt && !"out before 10:00".equals(leaveReason))
			{
				leaving = false;
				note(o, "Safe again: carry on");
				trace("leave_cancelled", null, o.now, "exit", exit, "leaveAt", leaveAt);
			}
		}
		if (leaving)
		{
			return leave(leaveReason);
		}

		Task front = tasks.peekFirst();
		if (front != null)
		{
			Instruction instruction = front.instruction(o);
			if (instruction != null)
			{
				return instruction;
			}
		}
		if (awaitingDecisionAt >= 0)
		{
			// A fix decision is on its way (a tick or two): don't flash "Mine boulder N" meanwhile.
			return new Instruction(CoachAction.WAIT, null, "Working out the next step", null, false);
		}
		return defaultInstruction(o);
	}

	private void checkpoints(Observation o)
	{
		int b = o.boulderNumber;
		if (b >= 1 && b <= 5 && o.atBoulder && !arrived[b] && b == bouldersBroken + 1)
		{
			arrived[b] = true;
			if (b == 1)
			{
				decideHalfOne(o, HalfOnePlanner.Checkpoint.MINING_B1, o.boulderSecondsRemaining, false);
			}
			else if (b == 2 && tasks.isEmpty())
			{
				decideHalfOne(o, HalfOnePlanner.Checkpoint.AT_B2, -1, false);
			}
			else if (b == 3 && tasks.isEmpty() && o.now < RESET_TIME)
			{
				decideHalfOne(o, HalfOnePlanner.Checkpoint.AT_B3, -1, false);
			}
		}
		if (!o.stabilityUpdate || !tasks.isEmpty() || !o.atBoulder)
		{
			return;
		}
		if (b == 1 && bouldersBroken == 0 && arrived[1] && !detourDone[1])
		{
			decideHalfOne(o, HalfOnePlanner.Checkpoint.MINING_B1, o.boulderSecondsRemaining, true);
		}
		else if (b == 2 && bouldersBroken == 1 && arrived[2] && !detourDone[2] && o.now < RESET_TIME)
		{
			decideHalfOne(o, HalfOnePlanner.Checkpoint.MINING_B2, o.boulderSecondsRemaining, true);
		}
		else if (b == 4 && bouldersBroken == 3 && o.now >= RESET_TIME)
		{
			StrategyPredictor p = predictorFor(o);
			if (p == null)
			{
				return;
			}
			boolean bDone = earlyFixB;
			boolean cDone = earlyFixC;
			double now = o.now;
			later(() -> HalfTwoPlanner.earlyFixesDuringB4(p, bDone, cDone), early ->
			{
				trace("early_b4", p, now, "bDone", bDone, "cDone", cDone, "result", early);
				if (bouldersBroken != 3 || leaving || teleport || mineScaled)
				{
					return;
				}
				for (int vent : early)
				{
					if (vent == 1 ? earlyFixB : earlyFixC)
					{
						continue;
					}
					if (vent == 1)
					{
						earlyFixB = true;
					}
					else
					{
						earlyFixC = true;
					}
					note(now, VENT[vent] + " is heading away from 50 (" + percent(p.pHeadingAway(vent)) + "): fix it now, then back to B4");
					ChamberTask fix = new ChamberTask(vent, false, VENT[vent] + " is heading away from 50, then back to B4");
					fix.exitSeconds = vent == 1 ? HalfTwoPlanner.EXIT_NEAR_B_CHAMBER : HalfTwoPlanner.EXIT_FAR;
					tasks.add(fix);
				}
			});
		}
	}

	private void advanceTasks(Observation o)
	{
		if (o.now >= RESET_TIME)
		{
			// First-half steps still queued at 5:00 are moot (the vents re-roll): checks, fixes, the
			// decisions behind them. (The B4-window caps are queued once B3 has broken: they stay.)
			tasks.removeIf(t -> t.queuedAtBoulders >= 0 && t.queuedAtBoulders < 3);
		}
		for (Task t : tasks)
		{
			if (t.queuedAtBoulders < 0 && o.now < RESET_TIME)
			{
				t.queuedAtBoulders = bouldersBroken;
			}
		}
		for (int guard = 0; guard < 50; guard++)
		{
			Task front = tasks.peekFirst();
			if (front == null)
			{
				return;
			}
			if (front instanceof DecideTask)
			{
				tasks.pollFirst();
				List<Task> next = ((DecideTask) front).decide.apply(o);
				for (int i = next.size() - 1; i >= 0; i--)
				{
					tasks.addFirst(next.get(i));
				}
				continue;
			}
			if (startedTask != front)
			{
				startedTask = front;
				front.start(o);
				// A step with nothing left to do (a vent already known, the caps already made) is done at once.
				if (front.done(o))
				{
					tasks.pollFirst();
					continue;
				}
				return;
			}
			if (front.done(o))
			{
				tasks.pollFirst();
				if (lastShownPlace != null)
				{
					lastPlace = lastShownPlace;
				}
				continue;
			}
			return;
		}
	}

	/** The flip of a fix you're on your way to (the current step), when it would land; empty if none. */
	private List<Simulator.FlipEvent> fixUnderWay(Observation o)
	{
		Task front = tasks.peekFirst();
		if (!(front instanceof ChamberTask) || ((ChamberTask) front).cap)
		{
			return java.util.Collections.emptyList();
		}
		int vent = ((ChamberTask) front).vent;
		double walk = vent == 1 ? HalfTwoPlanner.B4_TO_B_CHAMBER_TICKS : vent == 2 ? HalfTwoPlanner.B4_TO_C_CHAMBER_TICKS
			: HalfTwoPlanner.B4_TO_A_CHAMBER_TICKS;
		double ticks = walk + HalfTwoPlanner.fixTicks(vent, o.chamberStatus, o.rocksInHand > 0);
		return java.util.Collections.singletonList(
			new Simulator.FlipEvent((int) Math.ceil(o.now + ticks * TICK - 1e-9), 1 << vent));
	}

	private double currentExitSeconds()
	{
		Task front = tasks.peekFirst();
		if (front != null)
		{
			return front.exitSeconds;
		}
		if (bouldersBroken >= 4 && headedToB5)
		{
			return HalfTwoPlanner.EXIT_FAR;
		}
		return HalfTwoPlanner.exitSeconds(HalfTwoPlanner.B4_TO_E_STAIR);
	}

	private Instruction defaultInstruction(Observation o)
	{
		switch (bouldersBroken)
		{
			case 0:
				return mine(1, planDetail());
			case 1:
				return mine(2, planDetail());
			case 2:
				// Which side: the drawn path shows it (the quicker one from where you are, counting the
				// walk on to the B rock).
				return mine(3, planDetail());
			case 3:
				return mine(4, null);
			case 4:
				return mine(5, null);
			default:
				return new Instruction(CoachAction.LEAVE, MineLayout.Spot.STAIRS_EAST, "Leave by the East stairs", "all done", false);
		}
	}

	private static String suffix(String detail)
	{
		return detail == null ? "" : "; " + detail;
	}

	private String planDetail()
	{
		if (plan == null || plan.plan == HalfOnePlanner.Plan.NONE || plan.plan == HalfOnePlanner.Plan.TELEPORT)
		{
			return null;
		}
		return "plan: " + plan.plan.label;
	}

	private static Instruction mine(int boulder, String detail)
	{
		return new Instruction(CoachAction.MINE_BOULDER, null, "Mine boulder " + boulder, detail, false);
	}

	private static Instruction leave(String why)
	{
		return new Instruction(CoachAction.LEAVE, MineLayout.Spot.STAIRS_EAST, "Leave now: East stairs", why, true);
	}

	private static String checkpointLabel(HalfOnePlanner.Checkpoint checkpoint, boolean whileMining)
	{
		switch (checkpoint)
		{
			case MINING_B1:
				return whileMining ? "Mining B1" : "At B1";
			case B1_BROKEN:
				return "B1 broke";
			case AT_B2:
				return "At B2";
			case MINING_B2:
				return "Mining B2";
			case B2_BROKEN:
				return "B2 broke";
			default:
				return "At B3";
		}
	}

	static String percent(double share)
	{
		return String.format(Locale.ROOT, "%.1f%%", share * 100);
	}
}
