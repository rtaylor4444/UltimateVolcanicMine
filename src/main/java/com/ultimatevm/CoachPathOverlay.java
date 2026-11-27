package com.ultimatevm;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * The stock route for the coach's current step in green, and the next step's in light orange,
 * drawn as the tiles the player stands on (about every second tile when running) from where they
 * should be to where the step is done. Lava tiles (a platform
 * to build) are filled more strongly.
 */
public class CoachPathOverlay extends Overlay
{
	private static final Color CURRENT = new Color(0, 200, 60);
	private static final Color NEXT = new Color(255, 170, 80);

	private final Client client;
	private final UltimateVolcanicMineConfig config;
	private MineLayout layout;
	private CoachPaths paths;
	private volatile StrategyCoach.Instruction instruction;
	// The routes chosen for this step, from where the player was standing when it began.
	private String chosenFor;
	private int[][] currentRoute;
	private int[][] nextRoute;

	@Inject
	CoachPathOverlay(Client client, UltimateVolcanicMineConfig config)
	{
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
		this.client = client;
		this.config = config;
	}

	void setSources(MineLayout layout, CoachPaths paths)
	{
		this.layout = layout;
		this.paths = paths;
	}

	void setInstruction(StrategyCoach.Instruction instruction)
	{
		this.instruction = instruction;
		if (instruction == null)
		{
			chosenFor = null;
		}
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		StrategyCoach.Instruction shown = instruction;
		if (!config.showGuide() || !config.coachPath() || shown == null || layout == null || paths == null)
		{
			return null;
		}
		WorldPoint origin = layout.westStairs();
		if (origin == null)
		{
			return null;
		}
		String key = shown.from + "|" + shown.place + "|" + shown.nextPlace;
		if (currentRoute == null && client.getLocalPlayer() != null)
		{
			// At the place already (no green path): the next route starts where you stand now.
			WorldPoint me = client.getLocalPlayer().getWorldLocation();
			key += "|" + me.getX() + "," + me.getY();
		}
		if (!key.equals(chosenFor) && client.getLocalPlayer() != null)
		{
			// Chosen once per step: the route starting nearest to where you are (yours first), and
			// the next one from where that ends.
			chosenFor = key;
			WorldPoint me = client.getLocalPlayer().getWorldLocation();
			int dx = me.getX() - origin.getX();
			int dy = me.getY() - origin.getY();
			currentRoute = paths.route(shown.from, shown.place, dx, dy);
			if (currentRoute != null && currentRoute.length > 0)
			{
				int[] end = currentRoute[currentRoute.length - 1];
				dx = end[0];
				dy = end[1];
			}
			nextRoute = paths.route(shown.place, shown.nextPlace, dx, dy);
		}
		// The next step's route under the current one; of the current one, only what's still ahead.
		drawRoute(graphics, origin, nextRoute, NEXT, 15, 28, 110, 0);
		drawRoute(graphics, origin, currentRoute, CURRENT, 20, 38, 170, aheadOf(currentRoute, origin));
		return null;
	}

	/** The index after the route tile nearest the player (the walked part is not drawn). */
	private int aheadOf(int[][] route, WorldPoint origin)
	{
		if (route == null || client.getLocalPlayer() == null)
		{
			return 0;
		}
		WorldPoint me = client.getLocalPlayer().getWorldLocation();
		int dx = me.getX() - origin.getX();
		int dy = me.getY() - origin.getY();
		int best = 0;
		int bestDistance = Integer.MAX_VALUE;
		for (int i = 0; i < route.length; i++)
		{
			int d = Math.max(Math.abs(route[i][0] - dx), Math.abs(route[i][1] - dy));
			if (d < bestDistance)
			{
				best = i;
				bestDistance = d;
			}
		}
		// Off the route (more than 2 tiles away): show all of it.
		return bestDistance <= 2 ? best + 1 : 0;
	}

	private void drawRoute(Graphics2D graphics, WorldPoint origin, int[][] route, Color color, int fill, int lavaFill, int edge, int from)
	{
		if (route == null)
		{
			return;
		}
		Color fillColor = new Color(color.getRed(), color.getGreen(), color.getBlue(), fill);
		Color lavaColor = new Color(color.getRed(), color.getGreen(), color.getBlue(), lavaFill);
		Color edgeColor = new Color(color.getRed(), color.getGreen(), color.getBlue(), edge);
		graphics.setStroke(new BasicStroke(1.5f));
		for (int i = Math.max(0, from); i < route.length; i++)
		{
			int[] tile = route[i];
			if (tile[3] != 1)
			{
				continue; // passed over while running: only the tiles you stand on are drawn
			}
			WorldPoint at = new WorldPoint(origin.getX() + tile[0], origin.getY() + tile[1], origin.getPlane());
			LocalPoint local = LocalPoint.fromWorld(client, at);
			if (local == null)
			{
				continue;
			}
			Polygon poly = Perspective.getCanvasTilePoly(client, local);
			if (poly == null)
			{
				continue;
			}
			graphics.setColor(tile[2] == 1 ? lavaColor : fillColor);
			graphics.fill(poly);
			graphics.setColor(edgeColor);
			graphics.draw(poly);
		}
	}
}
