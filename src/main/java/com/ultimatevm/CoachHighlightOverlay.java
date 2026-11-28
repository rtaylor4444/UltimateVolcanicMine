package com.ultimatevm;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Shape;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.GroundObject;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.TileObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

/** Highlights, in the game world, the object the coach's next step is about. */
public class CoachHighlightOverlay extends Overlay
{
	// Boulder footprint (tiles) by boulder number (volcanicMine mapModel.BOULDERS).
	private static final int[] BOULDER_SIZE = {8, 8, 8, 6, 4, 2};

	private final Client client;
	private final UltimateVolcanicMineConfig config;
	private final BoulderHealthOverlay boulderHealthOverlay;
	private MineLayout layout;
	private volatile StrategyCoach.Instruction instruction;
	private volatile int boulderNumber = 1;
	// What the last frame drew, for the coach trace and the debug view.
	private volatile String status = "nothing";

	@Inject
	CoachHighlightOverlay(Client client, UltimateVolcanicMineConfig config, BoulderHealthOverlay boulderHealthOverlay)
	{
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
		this.client = client;
		this.config = config;
		this.boulderHealthOverlay = boulderHealthOverlay;
	}

	void setLayout(MineLayout layout)
	{
		this.layout = layout;
	}

	void setInstruction(StrategyCoach.Instruction instruction, int boulderNumber)
	{
		this.instruction = instruction;
		this.boulderNumber = boulderNumber;
	}

	/** What the last frame highlighted: the target, whether it was found and on screen, and where. */
	String getStatus()
	{
		return status;
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (config.coachDebug())
		{
			drawSpotLabels(graphics);
		}
		StrategyCoach.Instruction shown = instruction;
		if (!config.showGuide() || !config.coachHighlight() || shown == null)
		{
			status = "off";
			return null;
		}
		Color color = CoachOverlay.colorFor(shown);
		Color outline = new Color(color.getRed(), color.getGreen(), color.getBlue(), 255);
		Color fill = new Color(color.getRed(), color.getGreen(), color.getBlue(), 50);
		String label = shortLabel(shown);

		boolean mining = client.getLocalPlayer() != null
			&& VolcanicMineMiningAnimation.VOLCANIC_MINE_ANIMATIONS.contains(client.getLocalPlayer().getAnimation());
		if (shown.action == CoachAction.MINE_BOULDER)
		{
			// A light tint while you should be mining it; the label only while you're on your way.
			Shape boulder = boulderShape();
			if (boulder == null)
			{
				status = "boulder " + boulderNumber + ": not tracked yet";
				return null;
			}
			status = "boulder " + boulderNumber + ": on screen" + offsetText(boulderHealthOverlay.getBoulderLocation());
			Color light = new Color(color.getRed(), color.getGreen(), color.getBlue(), 35);
			Color edge = new Color(color.getRed(), color.getGreen(), color.getBlue(), 140);
			drawShape(graphics, boulder, edge, light, 2);
			if (!mining)
			{
				drawBoulderLabel(graphics, label, outline);
			}
			return null;
		}
		if (mining && shown.action != CoachAction.LEAVE && shown.action != CoachAction.TELEPORT)
		{
			// Still mining when something else is next: say so on the boulder.
			Shape boulder = boulderShape();
			if (boulder != null)
			{
				Color red = new Color(220, 30, 30);
				drawShape(graphics, boulder, red, new Color(220, 30, 30, 30), 3);
				drawBoulderLabel(graphics, "STOP - " + shortLabel(shown), red);
			}
		}

		if (shown.spot == null || layout == null)
		{
			status = "no target (" + shown.action + ")";
			return null;
		}
		TileObject object = shown.action == CoachAction.START && client.getLocalPlayer() != null
			? layout.lobbyWestStairs(client.getLocalPlayer().getWorldLocation().getPlane())
			: layout.find(shown.spot);
		if (object == null)
		{
			status = shown.spot + ": NOT FOUND" + (layout.westStairs() == null ? " (West stairs not seen)" : "");
			return null;
		}
		Shape hull = null;
		if (object instanceof GameObject)
		{
			hull = ((GameObject) object).getConvexHull();
		}
		else if (object instanceof GroundObject)
		{
			hull = ((GroundObject) object).getConvexHull();
		}
		if (hull == null)
		{
			hull = object.getCanvasTilePoly();
		}
		if (hull != null)
		{
			drawShape(graphics, hull, outline, fill);
		}
		Polygon tile = object.getCanvasTilePoly();
		if (tile != null)
		{
			drawShape(graphics, tile, outline, fill);
		}
		status = shown.spot + " id " + object.getId() + (hull != null || tile != null ? ": on screen" : ": off screen")
			+ offsetText(object.getWorldLocation());
		Point text = object.getCanvasTextLocation(graphics, label, 150);
		if (text != null)
		{
			OverlayUtil.renderTextLocation(graphics, text, label, outline);
		}
		return null;
	}

	/** Coach Debug: every spot the layout matched, labelled where it is. */
	private void drawSpotLabels(Graphics2D graphics)
	{
		if (layout == null)
		{
			return;
		}
		for (MineLayout.Spot spot : MineLayout.Spot.values())
		{
			TileObject object = layout.find(spot);
			if (object == null)
			{
				continue;
			}
			String text = spot.name().toLowerCase().replace('_', ' ');
			Point at = object.getCanvasTextLocation(graphics, text, 60);
			if (at != null)
			{
				OverlayUtil.renderTextLocation(graphics, at, text, Color.YELLOW);
			}
		}
	}

	private String offsetText(net.runelite.api.coords.WorldPoint point)
	{
		net.runelite.api.coords.WorldPoint origin = layout == null ? null : layout.westStairs();
		if (point == null || origin == null)
		{
			return "";
		}
		return " at (" + (point.getX() - origin.getX()) + "," + (point.getY() - origin.getY()) + ") from the West stairs";
	}

	private static void drawShape(Graphics2D graphics, Shape shape, Color outline, Color fill)
	{
		drawShape(graphics, shape, outline, fill, 3);
	}

	private static void drawShape(Graphics2D graphics, Shape shape, Color outline, Color fill, float width)
	{
		graphics.setColor(fill);
		graphics.fill(shape);
		graphics.setColor(outline);
		graphics.setStroke(new BasicStroke(width));
		graphics.draw(shape);
	}

	/** The boulder's own outline (its model), or its footprint if the model isn't drawn. */
	private Shape boulderShape()
	{
		net.runelite.api.coords.WorldPoint at = boulderHealthOverlay.getBoulderLocation();
		TileObject object = layout == null ? null : layout.boulder(at);
		if (object instanceof GameObject)
		{
			Shape hull = ((GameObject) object).getConvexHull();
			if (hull != null)
			{
				return hull;
			}
		}
		LocalPoint local = boulderHealthOverlay.getBoulderLocalLocation();
		if (local == null)
		{
			return null;
		}
		return Perspective.getCanvasTileAreaPoly(client, local, BOULDER_SIZE[Math.max(0, Math.min(5, boulderNumber))]);
	}

	private void drawBoulderLabel(Graphics2D graphics, String text, Color color)
	{
		LocalPoint local = boulderHealthOverlay.getBoulderLocalLocation();
		if (local == null)
		{
			return;
		}
		Point at = Perspective.getCanvasTextLocation(client, graphics, local, text, 300);
		if (at != null)
		{
			OverlayUtil.renderTextLocation(graphics, at, text, color);
		}
	}

	private static String shortLabel(StrategyCoach.Instruction instruction)
	{
		switch (instruction.action)
		{
			case MINE_BOULDER:
				return "MINE";
			case CHECK_VENT:
				return "CHECK";
			case FIX_CHAMBER:
				return "FIX";
			case CAP_CHAMBER:
				return "CAP";
			case TAKE_ROCK:
				return "TAKE ROCK";
			case LEAVE:
				return "LEAVE";
			case START:
				return "GO DOWN";
			default:
				return instruction.text;
		}
	}
}
