package com.ultimatevm;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
public class GuideManager
{
	private static final int VARBIT_VENT_STATUS_A = 5939;
	private static final int VARBIT_VENT_STATUS_B = 5940;
	private static final int VARBIT_VENT_STATUS_C = 5942;

	// Flip times past this are beyond the 5-minute reset boundary.
	private static final int SIMULATION_BOUNDARY_TIME = 300;
	private static final double TICK_SECONDS = 0.6;

	// Ground truth from rules.py/XpUtils.BOULDERS - a stage-1..4 boulder has 41 health, real mining
	// time 71.01s (not a flat per-health rate guess).
	private static final double BOULDER_STAGE_HEALTH = 41.0;
	private static final double BOULDER_STAGE_SECONDS = 71.01;
	// times.txt / utils.DEFAULT_TRAVEL_TICKS[("B1","B2")] = 15 ticks.
	private static final double TRAVEL_B1_B2_SECONDS = 15 * TICK_SECONDS;

	private static final int ANCHOR_NONE = 0;
	private static final int ANCHOR_AFTER_B1 = 1;
	private static final int ANCHOR_AFTER_B2 = 2;
	private static final int ANCHOR_NOW = 3;

	public enum Scenario
	{
		NO_FIX,
		FIX_A_AFTER_B2,
		FIX_B_AFTER_B2,
		FIX_A_AFTER_B1,
		FIX_A_RETURN_B1,
		FIX_B_AFTER_B1,
		FIX_AB_AFTER_B2,
		FIX_B_RETURN_B1,
		FIX_C_RETURN_B1,
		FIX_AB_AFTER_B1,
		FIX_BC_AFTER_B1,
		FIX_CB_RETURN_B1,
		FIX_AB_RETURN_B1,
		RESET
	}

	// Mirrors decisionEngine.SCENARIO_SCHEDULE_KEYS exactly - order is priority (cheapest/least
	// disruptive first), tick offsets copied verbatim from rules.py's _RULES_ONE_TAIL.
	private static final ScenarioPlan[] SCENARIO_PLANS = new ScenarioPlan[]{
		new ScenarioPlan(Scenario.FIX_A_AFTER_B2, ANCHOR_AFTER_B2, new int[]{0}, new double[]{32.2}),
		new ScenarioPlan(Scenario.FIX_B_AFTER_B2, ANCHOR_AFTER_B2, new int[]{1}, new double[]{33.2}),
		new ScenarioPlan(Scenario.FIX_A_AFTER_B1, ANCHOR_AFTER_B1, new int[]{0}, new double[]{44.22}),
		new ScenarioPlan(Scenario.FIX_A_RETURN_B1, ANCHOR_NOW, new int[]{0}, new double[]{32}),
		new ScenarioPlan(Scenario.FIX_B_AFTER_B1, ANCHOR_AFTER_B1, new int[]{1}, new double[]{47.22}),
		new ScenarioPlan(Scenario.FIX_AB_AFTER_B2, ANCHOR_AFTER_B2, new int[]{0, 1}, new double[]{29.2, 57.4}),
		new ScenarioPlan(Scenario.FIX_B_RETURN_B1, ANCHOR_NOW, new int[]{1}, new double[]{47.22}),
		new ScenarioPlan(Scenario.FIX_C_RETURN_B1, ANCHOR_NOW, new int[]{2}, new double[]{43.22}),
		new ScenarioPlan(Scenario.FIX_AB_AFTER_B1, ANCHOR_AFTER_B1, new int[]{0, 1}, new double[]{44.22, 73.94}),
		new ScenarioPlan(Scenario.FIX_BC_AFTER_B1, ANCHOR_AFTER_B1, new int[]{2, 1}, new double[]{44.22, 80.44}),
		new ScenarioPlan(Scenario.FIX_CB_RETURN_B1, ANCHOR_NOW, new int[]{2, 1}, new double[]{43.22, 69.44}),
		new ScenarioPlan(Scenario.FIX_AB_RETURN_B1, ANCHOR_NOW, new int[]{0, 1}, new double[]{44.22, 73.94}),
	};

	private static final Map<Scenario, int[]> VENT_INDICES_BY_SCENARIO = buildVentIndexLookup();

	private static Map<Scenario, int[]> buildVentIndexLookup()
	{
		Map<Scenario, int[]> lookup = new HashMap<>();
		for (ScenarioPlan plan : SCENARIO_PLANS)
		{
			lookup.put(plan.scenario, plan.ventIndices);
		}
		return lookup;
	}

	private static final class ScenarioPlan
	{
		private final Scenario scenario;
		private final int anchorType;
		private final int[] ventIndices;
		private final double[] offsetTicks;

		private ScenarioPlan(Scenario scenario, int anchorType, int[] ventIndices, double[] offsetTicks)
		{
			this.scenario = scenario;
			this.anchorType = anchorType;
			this.ventIndices = ventIndices;
			this.offsetTicks = offsetTicks;
		}
	}

	private final Client client;
	private final VentStatusPredicter ventStatusPredicter;
	private final GuideOverlay guideOverlay;
	private final UltimateVolcanicMineConfig config;
	private Scenario currentScenario;

	// Boulder-progress tracking, persistent across update() calls within one game (see reset()).
	private int previousBoulderNumber = 1;
	private Integer b1FinishTimeSeconds;
	private Integer b2FinishTimeSeconds;

	GuideManager(Client client, VentStatusPredicter ventStatusPredicter, GuideOverlay guideOverlay, UltimateVolcanicMineConfig config)
	{
		this.client = client;
		this.ventStatusPredicter = ventStatusPredicter;
		this.guideOverlay = guideOverlay;
		this.config = config;
	}

	public void reset()
	{
		currentScenario = null;
		previousBoulderNumber = 1;
		b1FinishTimeSeconds = null;
		b2FinishTimeSeconds = null;
		guideOverlay.reset();
	}

	public void onTick(int estimatedTimeRemaining)
	{
		if (estimatedTimeRemaining <= VentStatusTimeline.VM_GAME_RESET_TIME)
		{
			reset();
		}
	}

	public void onChamberStatusChanged(int oldStatus, int newStatus)
	{
		if (currentScenario == null)
		{
			return;
		}

		int[] ventIndices = VENT_INDICES_BY_SCENARIO.get(currentScenario);
		if (ventIndices == null)
		{
			return;
		}

		boolean aFlipped = (oldStatus & Simulator.CHAMBER_A_BIT) != (newStatus & Simulator.CHAMBER_A_BIT);
		boolean bFlipped = (oldStatus & Simulator.CHAMBER_B_BIT) != (newStatus & Simulator.CHAMBER_B_BIT);
		boolean cFlipped = (oldStatus & Simulator.CHAMBER_C_BIT) != (newStatus & Simulator.CHAMBER_C_BIT);

		for (int ventIndex : ventIndices)
		{
			boolean flipped = ventIndex == 0 ? aFlipped : ventIndex == 1 ? bFlipped : cFlipped;
			if (flipped)
			{
				log.info("UVM Guide | chamber flip | vent {} flipped, dismissing scenario={}", ventIndex, currentScenario);
				reset();
				return;
			}
		}
	}

	public void update(int estimatedTimeRemaining, int currentStability, int chamberStatus, int simulationTime, int boulderNumber, int boulderHealth)
	{
		trackBoulderProgress(boulderNumber, simulationTime);

		if (estimatedTimeRemaining <= VentStatusTimeline.VM_GAME_RESET_TIME)
		{
			guideOverlay.reset();
			return;
		}

		// Only B needs to be server-confirmed to start guiding - A (like C) is swept as an unknown
		// vent when it hasn't been checked yet, so the CHECK_B_ONLY route gets predictions too.
		int ventB = client.getVarbitValue(VARBIT_VENT_STATUS_B);
		if (ventB == VentStatus.STARTING_VENT_VALUE)
		{
			log.info("UVM Guide | update | skip: B not server-confirmed");
			guideOverlay.reset();
			return;
		}

		int ventA = client.getVarbitValue(VARBIT_VENT_STATUS_A);
		int ventC = client.getVarbitValue(VARBIT_VENT_STATUS_C);

		log.info("UVM Guide | update | time={}s, stability={}, chamberStatus={}, boulderNumber={}, boulderHealth={}, ventA={}, ventB={}, ventC={}",
			simulationTime, currentStability, chamberStatus, boulderNumber, boulderHealth, ventA, ventB, ventC);

		VentStatus[] predictedVents = ventStatusPredicter.getDisplayState().getVents();
		List<Integer> possibleAValues = VentValueResolver.resolvePossibleValues(ventA, predictedVents[0]);
		List<Integer> possibleCValues = VentValueResolver.resolvePossibleValues(ventC, predictedVents[2]);

		if (possibleAValues.isEmpty() || possibleCValues.isEmpty())
		{
			log.info("UVM Guide | update | skip: no possible values, possibleAValues={}, possibleCValues={}", possibleAValues, possibleCValues);
			guideOverlay.reset();
			return;
		}

		int[] knownVents = new int[]{0, ventB, 0};
		List<Simulator.VentUnknownInput> unknownInputs = new ArrayList<>();
		if (possibleAValues.size() == 1)
		{
			knownVents[0] = possibleAValues.get(0);
		}
		else
		{
			unknownInputs.add(new Simulator.VentUnknownInput(0, possibleAValues));
		}
		if (possibleCValues.size() == 1)
		{
			knownVents[2] = possibleCValues.get(0);
		}
		else
		{
			unknownInputs.add(new Simulator.VentUnknownInput(2, possibleCValues));
		}

		Scenario scenario = identifyScenario(knownVents, unknownInputs, chamberStatus, currentStability, simulationTime, boulderNumber, boulderHealth);
		currentScenario = scenario;
		log.info("UVM Guide | update | final scenario={}", scenario);
		guideOverlay.setScenario(scenario);
	}

	void trackBoulderProgress(int boulderNumber, int simulationTime)
	{
		if (boulderNumber >= 2 && previousBoulderNumber < 2)
		{
			b1FinishTimeSeconds = simulationTime;
		}
		if (boulderNumber >= 3 && previousBoulderNumber < 3)
		{
			b2FinishTimeSeconds = simulationTime;
		}
		previousBoulderNumber = boulderNumber;
	}

	static double remainingBoulderSeconds(int boulderHealth)
	{
		return Math.max(0, boulderHealth) * (BOULDER_STAGE_SECONDS / BOULDER_STAGE_HEALTH);
	}

	double computeB1FinishAnchor(int boulderNumber, int boulderHealth, int simulationTime)
	{
		if (boulderNumber == 1)
		{
			return simulationTime + remainingBoulderSeconds(boulderHealth);
		}
		return b1FinishTimeSeconds != null ? b1FinishTimeSeconds : simulationTime;
	}

	double computeB2FinishAnchor(int boulderNumber, int boulderHealth, int simulationTime, double b1FinishAnchor)
	{
		if (boulderNumber <= 1)
		{
			return b1FinishAnchor + TRAVEL_B1_B2_SECONDS + BOULDER_STAGE_SECONDS;
		}
		if (boulderNumber == 2)
		{
			if (boulderHealth > 0)
			{
				return simulationTime + remainingBoulderSeconds(boulderHealth);
			}
			double b1Finish = b1FinishTimeSeconds != null ? b1FinishTimeSeconds : simulationTime;
			return b1Finish + TRAVEL_B1_B2_SECONDS + BOULDER_STAGE_SECONDS;
		}
		return b2FinishTimeSeconds != null ? b2FinishTimeSeconds : simulationTime;
	}

	// afterB1 fix opportunities exist from the start of B1 mining until arrival at B2 (boulderHealth
	// still 0 while boulderNumber has already advanced to 2 means still travelling there).
	static boolean isAfterB1WindowOpen(int boulderNumber, int boulderHealth)
	{
		return boulderNumber <= 1 || (boulderNumber == 2 && boulderHealth <= 0);
	}

	static boolean isAfterB2WindowOpen(int boulderNumber, int boulderHealth)
	{
		return boulderNumber <= 2 || (boulderNumber == 3 && boulderHealth <= 0);
	}

	// returnB1 emergency detours only apply while actively mining B1 (Python's while_mining checkpoint).
	static boolean isReturnB1WindowOpen(int boulderNumber, int boulderHealth)
	{
		return boulderNumber == 1 && boulderHealth > 0;
	}

	Scenario identifyScenario(int[] knownVents, List<Simulator.VentUnknownInput> unknownInputs,
		int chamberStatus, int currentStability, int simulationTime, int boulderNumber, int boulderHealth)
	{
		double noFixChance = evaluateBlowUpChance(knownVents, unknownInputs, chamberStatus, currentStability, simulationTime, Collections.emptyList());
		logChanceCheck(Scenario.NO_FIX, noFixChance);
		if (noFixChance == 0.0)
		{
			return Scenario.NO_FIX;
		}

		double b1Anchor = computeB1FinishAnchor(boulderNumber, boulderHealth, simulationTime);
		double b2Anchor = computeB2FinishAnchor(boulderNumber, boulderHealth, simulationTime, b1Anchor);
		boolean afterB1Open = isAfterB1WindowOpen(boulderNumber, boulderHealth);
		boolean afterB2Open = isAfterB2WindowOpen(boulderNumber, boulderHealth);
		boolean returnB1Open = isReturnB1WindowOpen(boulderNumber, boulderHealth);

		for (ScenarioPlan plan : SCENARIO_PLANS)
		{
			boolean windowOpen;
			double anchor;
			switch (plan.anchorType)
			{
				case ANCHOR_AFTER_B1:
					windowOpen = afterB1Open;
					anchor = b1Anchor;
					break;
				case ANCHOR_AFTER_B2:
					windowOpen = afterB2Open;
					anchor = b2Anchor;
					break;
				case ANCHOR_NOW:
					windowOpen = returnB1Open;
					anchor = simulationTime;
					break;
				default:
					windowOpen = false;
					anchor = 0;
					break;
			}

			if (!windowOpen)
			{
				log.info("UVM Guide | {} | skipped: window closed", plan.scenario);
				continue;
			}

			List<Simulator.FlipEvent> flipEvents = new ArrayList<>();
			double earliestFlipTime = Double.MAX_VALUE;
			boolean tooLate = false;
			for (int i = 0; i < plan.ventIndices.length; i++)
			{
				double flipTime = anchor + plan.offsetTicks[i] * TICK_SECONDS;
				if (flipTime >= SIMULATION_BOUNDARY_TIME)
				{
					tooLate = true;
					break;
				}
				earliestFlipTime = Math.min(earliestFlipTime, flipTime);
				flipEvents.add(new Simulator.FlipEvent((int) Math.round(flipTime), chamberBitFor(plan.ventIndices[i])));
			}

			if (tooLate)
			{
				log.info("UVM Guide | {} | skipped: a flip time >= boundary={}s", plan.scenario, SIMULATION_BOUNDARY_TIME);
				continue;
			}
			if (simulationTime >= earliestFlipTime)
			{
				log.info("UVM Guide | {} | skipped: flip time already past (time={}s)", plan.scenario, simulationTime);
				continue;
			}

			double chance = evaluateBlowUpChance(knownVents, unknownInputs, chamberStatus, currentStability, simulationTime, flipEvents);
			logChanceCheck(plan.scenario, chance);
			if (chance == 0.0)
			{
				return plan.scenario;
			}
		}

		return Scenario.RESET;
	}

	private void logChanceCheck(Scenario scenario, double chance)
	{
		log.info("UVM Guide | {} check | blowUpChance={}%", scenario, chance);
		if (config.showBlowUpChance())
		{
			client.addChatMessage(
				ChatMessageType.GAMEMESSAGE,
				"UVM",
				getScenarioDisplayName(scenario) + " " + String.format(Locale.ROOT, "%.1f%%", chance),
				null);
		}
	}

	private static String getScenarioDisplayName(Scenario scenario)
	{
		switch (scenario)
		{
			case NO_FIX:
				return "No Fix";
			case FIX_A_AFTER_B2:
				return "Fix A after B2";
			case FIX_B_AFTER_B2:
				return "Fix B after B2";
			case FIX_A_AFTER_B1:
				return "Fix A after B1";
			case FIX_A_RETURN_B1:
				return "Fix A return B1";
			case FIX_B_AFTER_B1:
				return "Fix B after B1";
			case FIX_AB_AFTER_B2:
				return "Fix AB after B2";
			case FIX_B_RETURN_B1:
				return "Fix B return B1";
			case FIX_C_RETURN_B1:
				return "Fix C return B1";
			case FIX_AB_AFTER_B1:
				return "Fix AB after B1";
			case FIX_BC_AFTER_B1:
				return "Fix BC after B1";
			case FIX_CB_RETURN_B1:
				return "Fix CB return B1";
			case FIX_AB_RETURN_B1:
				return "Fix AB return B1";
			default:
				throw new IllegalArgumentException("No display name for guide scenario " + scenario);
		}
	}

	private static int chamberBitFor(int ventIndex)
	{
		return ventIndex == 0 ? Simulator.CHAMBER_A_BIT : ventIndex == 1 ? Simulator.CHAMBER_B_BIT : Simulator.CHAMBER_C_BIT;
	}

	private static double evaluateBlowUpChance(int[] knownVents, List<Simulator.VentUnknownInput> unknownInputs,
		int chamberStatus, int currentStability, int simulationTime, List<Simulator.FlipEvent> flipEvents)
	{
		Simulator.MultiVentSweepResult sweep = Simulator.simulateUnknownVentsWithFlips(
			knownVents, unknownInputs, chamberStatus, currentStability, simulationTime, flipEvents);
		return sweep.blowUpChancePercent;
	}
}