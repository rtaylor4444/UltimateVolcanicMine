package com.ultimatevm;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;

import java.util.Collections;
import java.util.List;

@Slf4j
public class GuideManager
{
	private static final int VARBIT_VENT_STATUS_A = 5939;
	private static final int VARBIT_VENT_STATUS_B = 5940;
	private static final int VARBIT_VENT_STATUS_C = 5942;

	// 6:18 path: player heads to fix A at 6:18, fix arrives at 6:04 = 236s elapsed
	private static final int CHAMBER_A_618_FIX_TIME = 236;
	// Flip times past this are beyond the 5-minute reset boundary
	private static final int SIMULATION_BOUNDARY_TIME = 300;
	// Boulder phase constants
	private static final int BOULDER_PHASE2_DEFAULT_HEALTH = 41;
	private static final float SECONDS_PER_HEALTH = 1.5f;
	private static final int BOULDER_PHASE_GAP_SECONDS = 12;
	// Travel time from boulder completion to each vent fix (seconds)
	private static final int TRAVEL_B2_FIX_A = 19;
	private static final int TRAVEL_B2_FIX_B = 21;
	private static final int TRAVEL_B1_FIX_A = 28;
	private static final int TRAVEL_B1_FIX_B = 29;
	// chamberStatus direction bit masks
	private static final int CHAMBER_A_BIT = 1;
	private static final int CHAMBER_B_BIT = 2;

	public enum Scenario
	{
		NO_FIX,
		FIX_A_AT_618,
		FIX_A_AFTER_B2,
		FIX_B_AFTER_B2,
		FIX_A_AFTER_B1,
		FIX_B_AFTER_B1,
		RESET
	}

	private final Client client;
	private final VentStatusPredicter ventStatusPredicter;
	private final GuideOverlay guideOverlay;
	private Scenario currentScenario;

	GuideManager(Client client, VentStatusPredicter ventStatusPredicter, GuideOverlay guideOverlay)
	{
		this.client = client;
		this.ventStatusPredicter = ventStatusPredicter;
		this.guideOverlay = guideOverlay;
	}

	public void reset()
	{
		currentScenario = null;
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

		boolean aFlipped = (oldStatus & CHAMBER_A_BIT) != (newStatus & CHAMBER_A_BIT);
		boolean bFlipped = (oldStatus & CHAMBER_B_BIT) != (newStatus & CHAMBER_B_BIT);

		switch (currentScenario)
		{
			case FIX_A_AT_618:
			case FIX_A_AFTER_B2:
			case FIX_A_AFTER_B1:
				if (aFlipped)
				{
					log.info("UVM Guide | chamber flip | A flipped, dismissing scenario={}", currentScenario);
					reset();
				}
				break;
			case FIX_B_AFTER_B2:
			case FIX_B_AFTER_B1:
				if (bFlipped)
				{
					log.info("UVM Guide | chamber flip | B flipped, dismissing scenario={}", currentScenario);
					reset();
				}
				break;
			default:
				break;
		}
	}

	public void update(int estimatedTimeRemaining, int currentStability, int chamberStatus, int simulationTime, int boulderHealth)
	{
		if (estimatedTimeRemaining <= VentStatusTimeline.VM_GAME_RESET_TIME)
		{
			guideOverlay.reset();
			return;
		}

		int ventA = client.getVarbitValue(VARBIT_VENT_STATUS_A);
		int ventB = client.getVarbitValue(VARBIT_VENT_STATUS_B);
		int ventC = client.getVarbitValue(VARBIT_VENT_STATUS_C);

		log.info("UVM Guide | update | time={}s, stability={}, chamberStatus={}, boulderHealth={}, ventA={}, ventB={}, ventC={}",
			simulationTime, currentStability, chamberStatus, boulderHealth, ventA, ventB, ventC);

		if (ventA == VentStatus.STARTING_VENT_VALUE || ventB == VentStatus.STARTING_VENT_VALUE)
		{
			log.info("UVM Guide | update | skip: A or B not server-confirmed, ventA={}, ventB={}", ventA, ventB);
			guideOverlay.reset();
			return;
		}

		VentStatus predictedC = ventStatusPredicter.getDisplayState().getVents()[2];
		List<Integer> possibleCValues = VentValueResolver.resolvePossibleValues(ventC, predictedC);

		if (possibleCValues.isEmpty())
		{
			log.info("UVM Guide | update | skip: no possible C values, ventC={}", ventC);
			guideOverlay.reset();
			return;
		}

		log.info("UVM Guide | update | possibleCValues={}", possibleCValues);
		Scenario scenario = identifyScenario(ventA, ventB, possibleCValues, chamberStatus, currentStability, simulationTime, boulderHealth);
		currentScenario = scenario;
		log.info("UVM Guide | update | final scenario={}", scenario);
		guideOverlay.setScenario(scenario);
	}

	private Scenario identifyScenario(int resolvedA, int resolvedB, List<Integer> possibleCValues,
		int chamberStatus, int currentStability, int simulationTime, int boulderHealth)
	{
		// 1. No-fix: safe as-is
		double noFixChance = computeBlowUpChance(resolvedA, resolvedB, possibleCValues, chamberStatus, currentStability, simulationTime);
		log.info("UVM Guide | NO_FIX check | blowUpChance={}%", noFixChance);
		if (noFixChance == 0.0)
		{
			return Scenario.NO_FIX;
		}

		// 2. Fix A at 6:04 (player heads there at 6:18)
		if (simulationTime < CHAMBER_A_618_FIX_TIME)
		{
			double chance = computeFlipBlowUpChance(resolvedA, resolvedB, possibleCValues, chamberStatus, CHAMBER_A_BIT, currentStability, simulationTime, CHAMBER_A_618_FIX_TIME);
			log.info("UVM Guide | FIX_A_AT_618 check | flipTime={}s, blowUpChance={}%", CHAMBER_A_618_FIX_TIME, chance);
			if (chance == 0.0)
			{
				return Scenario.FIX_A_AT_618;
			}
		}
		else
		{
			log.info("UVM Guide | FIX_A_AT_618 | skipped: time={}s past flipTime={}s", simulationTime, CHAMBER_A_618_FIX_TIME);
		}

		// Boulder phase base finish times
		int b1FinishTime = simulationTime + Math.round(Math.max(0, boulderHealth) * SECONDS_PER_HEALTH);
		int b2FinishTime = b1FinishTime + BOULDER_PHASE_GAP_SECONDS + Math.round(BOULDER_PHASE2_DEFAULT_HEALTH * SECONDS_PER_HEALTH);
		log.info("UVM Guide | boulder times | boulderHealth={}, b1FinishTime={}s, b2FinishTime={}s",
			boulderHealth, b1FinishTime, b2FinishTime);

		// 3. Fix A after B2 (travel +19s)
		int b2FlipTimeA = b2FinishTime + TRAVEL_B2_FIX_A;
		if (b2FlipTimeA >= SIMULATION_BOUNDARY_TIME)
		{
			log.info("UVM Guide | FIX_A_AFTER_B2 | skipped: flipTime={}s >= boundary={}s", b2FlipTimeA, SIMULATION_BOUNDARY_TIME);
		}
		else if (simulationTime >= b2FlipTimeA)
		{
			log.info("UVM Guide | FIX_A_AFTER_B2 | skipped: flipTime={}s already past (time={}s)", b2FlipTimeA, simulationTime);
		}
		else
		{
			double chance = computeFlipBlowUpChance(resolvedA, resolvedB, possibleCValues, chamberStatus, CHAMBER_A_BIT, currentStability, simulationTime, b2FlipTimeA);
			log.info("UVM Guide | FIX_A_AFTER_B2 check | flipTime={}s, blowUpChance={}%", b2FlipTimeA, chance);
			if (chance == 0.0)
			{
				return Scenario.FIX_A_AFTER_B2;
			}
		}

		// 4. Fix B after B2 (travel +21s)
		int b2FlipTimeB = b2FinishTime + TRAVEL_B2_FIX_B;
		if (b2FlipTimeB >= SIMULATION_BOUNDARY_TIME)
		{
			log.info("UVM Guide | FIX_B_AFTER_B2 | skipped: flipTime={}s >= boundary={}s", b2FlipTimeB, SIMULATION_BOUNDARY_TIME);
		}
		else if (simulationTime >= b2FlipTimeB)
		{
			log.info("UVM Guide | FIX_B_AFTER_B2 | skipped: flipTime={}s already past (time={}s)", b2FlipTimeB, simulationTime);
		}
		else
		{
			double chance = computeFlipBlowUpChance(resolvedA, resolvedB, possibleCValues, chamberStatus, CHAMBER_B_BIT, currentStability, simulationTime, b2FlipTimeB);
			log.info("UVM Guide | FIX_B_AFTER_B2 check | flipTime={}s, blowUpChance={}%", b2FlipTimeB, chance);
			if (chance == 0.0)
			{
				return Scenario.FIX_B_AFTER_B2;
			}
		}

		// 5. Fix A after B1 (travel +28s, only while B1 still in progress)
		int b1FlipTimeA = b1FinishTime + TRAVEL_B1_FIX_A;
		if (boulderHealth <= 0)
		{
			log.info("UVM Guide | FIX_A_AFTER_B1 | skipped: B1 already complete (boulderHealth={})", boulderHealth);
		}
		else if (b1FlipTimeA >= SIMULATION_BOUNDARY_TIME)
		{
			log.info("UVM Guide | FIX_A_AFTER_B1 | skipped: flipTime={}s >= boundary={}s", b1FlipTimeA, SIMULATION_BOUNDARY_TIME);
		}
		else if (simulationTime >= b1FlipTimeA)
		{
			log.info("UVM Guide | FIX_A_AFTER_B1 | skipped: flipTime={}s already past (time={}s)", b1FlipTimeA, simulationTime);
		}
		else
		{
			double chance = computeFlipBlowUpChance(resolvedA, resolvedB, possibleCValues, chamberStatus, CHAMBER_A_BIT, currentStability, simulationTime, b1FlipTimeA);
			log.info("UVM Guide | FIX_A_AFTER_B1 check | flipTime={}s, blowUpChance={}%", b1FlipTimeA, chance);
			if (chance == 0.0)
			{
				return Scenario.FIX_A_AFTER_B1;
			}
		}

		// 6. Fix B after B1 (travel +29s)
		int b1FlipTimeB = b1FinishTime + TRAVEL_B1_FIX_B;
		if (boulderHealth <= 0)
		{
			log.info("UVM Guide | FIX_B_AFTER_B1 | skipped: B1 already complete (boulderHealth={})", boulderHealth);
		}
		else if (b1FlipTimeB >= SIMULATION_BOUNDARY_TIME)
		{
			log.info("UVM Guide | FIX_B_AFTER_B1 | skipped: flipTime={}s >= boundary={}s", b1FlipTimeB, SIMULATION_BOUNDARY_TIME);
		}
		else if (simulationTime >= b1FlipTimeB)
		{
			log.info("UVM Guide | FIX_B_AFTER_B1 | skipped: flipTime={}s already past (time={}s)", b1FlipTimeB, simulationTime);
		}
		else
		{
			double chance = computeFlipBlowUpChance(resolvedA, resolvedB, possibleCValues, chamberStatus, CHAMBER_B_BIT, currentStability, simulationTime, b1FlipTimeB);
			log.info("UVM Guide | FIX_B_AFTER_B1 check | flipTime={}s, blowUpChance={}%", b1FlipTimeB, chance);
			if (chance == 0.0)
			{
				return Scenario.FIX_B_AFTER_B1;
			}
		}

		return Scenario.RESET;
	}

	private double computeBlowUpChance(int resolvedA, int resolvedB, List<Integer> possibleCValues,
		int chamberStatus, int currentStability, int simulationTime)
	{
		if (possibleCValues.size() == 1)
		{
			Simulator.SimulationResult result = Simulator.simulate(
				resolvedA, resolvedB, possibleCValues.get(0), chamberStatus, currentStability, simulationTime);
			return result.willDie ? 100.0 : 0.0;
		}

		List<Simulator.VentUnknownInput> unknownInputs = Collections.singletonList(
			new Simulator.VentUnknownInput(2, possibleCValues));
		Simulator.MultiVentSweepResult sweep = Simulator.simulateUnknownVents(
			new int[]{resolvedA, resolvedB, 0},
			unknownInputs,
			chamberStatus,
			currentStability,
			simulationTime);
		return sweep.blowUpChancePercent;
	}

	private double computeFlipBlowUpChance(int resolvedA, int resolvedB, List<Integer> possibleCValues,
		int chamberStatus, int chamberBit, int currentStability, int simulationTime, int flipTime)
	{
		int flippedChamberStatus = chamberStatus ^ chamberBit;
		int deathCount = 0;
		for (int cValue : possibleCValues)
		{
			if (runTwoPhaseSimulation(resolvedA, resolvedB, cValue, chamberStatus, flippedChamberStatus, currentStability, simulationTime, flipTime))
			{
				deathCount++;
			}
		}
		double result = (deathCount * 100.0) / possibleCValues.size();
		log.info("UVM Guide | flip sim summary | chamberBit={}, flipTime={}s, deaths={}/{}, blowUpChance={}%",
			chamberBit, flipTime, deathCount, possibleCValues.size(), result);
		return result;
	}

	private boolean runTwoPhaseSimulation(int a, int b, int c, int chamberStatus, int flippedChamberStatus,
		int currentStability, int simulationTime, int flipTime)
	{
		Simulator.SimulationResult phase1 = Simulator.simulate(a, b, c, chamberStatus, currentStability, simulationTime);
		int stabilityAtFlip = getStabilityAtTime(phase1.stabilityOverTime, flipTime, currentStability);
		int[] ventsAtFlip = Simulator.getVentValuesAtTime(a, b, c, chamberStatus, simulationTime, flipTime);
		Simulator.SimulationResult phase2 = Simulator.simulate(
			ventsAtFlip[0], ventsAtFlip[1], ventsAtFlip[2],
			flippedChamberStatus, stabilityAtFlip, flipTime);
		log.debug("UVM Guide | 2-phase sim | c={}, flipTime={}s, stabilityAtFlip={}, ventsAtFlip=[{},{},{}], phase2.willDie={}",
			c, flipTime, stabilityAtFlip, ventsAtFlip[0], ventsAtFlip[1], ventsAtFlip[2], phase2.willDie);
		return phase2.willDie;
	}

	private int getStabilityAtTime(List<Simulator.StabilityPoint> points, int targetTime, int fallbackStability)
	{
		int stab = fallbackStability;
		for (Simulator.StabilityPoint point : points)
		{
			if (point.time > targetTime)
			{
				break;
			}
			stab = point.stability;
		}
		return stab;
	}
}