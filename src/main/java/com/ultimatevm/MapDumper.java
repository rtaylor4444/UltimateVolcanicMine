package com.ultimatevm;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.CollisionData;
import net.runelite.api.DecorativeObject;
import net.runelite.api.GameObject;
import net.runelite.api.GroundObject;
import net.runelite.api.NPC;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Player;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.TileObject;
import net.runelite.api.WallObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.RuneLite;

/**
 * Dev tool (::vmdump): writes the loaded scene's tile data around the player to
 * a JSON file, for the Python route-time model. Read-only: it only reads what
 * the client already has loaded and writes one file under
 * .runelite/ultimate-volcanic-mine/. Must be called on the client thread.
 *
 * Per tile (world x/y on the player's plane): collision flags (blocked tile and
 * wall edges), floor overlay/underlay ids and tile shape. Plus every object
 * (game, wall, ground, decorative) and NPC with id, name, tile and size.
 */
class MapDumper
{
	static final String COMMAND = "vmdump";
	private static final int[] VM_REGIONS = {15262, 15263};

	private final Client client;

	MapDumper(Client client)
	{
		this.client = client;
	}

	/** Returns a one-line chat summary of what was written (or why not). */
	String dump()
	{
		Player me = client.getLocalPlayer();
		Scene scene = client.getScene();
		CollisionData[] maps = client.getCollisionMaps();
		if (me == null || scene == null || maps == null)
		{
			return "Map dump: not logged in / scene not loaded.";
		}
		int plane = client.getPlane();
		int baseX = scene.getBaseX();
		int baseY = scene.getBaseY();
		int[][] flags = maps[plane].getFlags();
		short[][][] overlays = scene.getOverlayIds();
		short[][][] underlays = scene.getUnderlayIds();
		byte[][][] shapes = scene.getTileShapes();
		Tile[][][] tiles = scene.getTiles();

		List<int[]> tileRows = new ArrayList<>();
		List<Map<String, Object>> objects = new ArrayList<>();
		int vmTiles = 0;
		for (int sx = 0; sx < flags.length; sx++)
		{
			for (int sy = 0; sy < flags[sx].length; sy++)
			{
				int wx = baseX + sx;
				int wy = baseY + sy;
				int region = ((wx >> 6) << 8) | (wy >> 6);
				if (region == VM_REGIONS[0] || region == VM_REGIONS[1])
				{
					vmTiles++;
				}
				tileRows.add(new int[]{
					wx, wy, flags[sx][sy],
					overlays == null ? -1 : overlays[plane][sx][sy],
					underlays == null ? -1 : underlays[plane][sx][sy],
					shapes == null ? -1 : shapes[plane][sx][sy],
				});
				Tile tile = tiles[plane][sx][sy];
				if (tile == null)
				{
					continue;
				}
				for (GameObject o : tile.getGameObjects())
				{
					// A multi-tile object is listed on each of its tiles: keep its origin only.
					if (o != null && o.getSceneMinLocation().getX() == sx && o.getSceneMinLocation().getY() == sy)
					{
						Map<String, Object> m = object("game", o);
						m.put("sizeX", o.sizeX());
						m.put("sizeY", o.sizeY());
						m.put("orientation", o.getOrientation());
						objects.add(m);
					}
				}
				WallObject wall = tile.getWallObject();
				if (wall != null)
				{
					Map<String, Object> m = object("wall", wall);
					m.put("orientationA", wall.getOrientationA());
					m.put("orientationB", wall.getOrientationB());
					objects.add(m);
				}
				GroundObject ground = tile.getGroundObject();
				if (ground != null)
				{
					objects.add(object("ground", ground));
				}
				DecorativeObject deco = tile.getDecorativeObject();
				if (deco != null)
				{
					objects.add(object("decorative", deco));
				}
			}
		}

		List<Map<String, Object>> npcs = new ArrayList<>();
		for (NPC npc : client.getNpcs())
		{
			if (npc == null || npc.getWorldLocation() == null)
			{
				continue;
			}
			Map<String, Object> m = new LinkedHashMap<>();
			m.put("id", npc.getId());
			m.put("name", npc.getName());
			m.put("x", npc.getWorldLocation().getX());
			m.put("y", npc.getWorldLocation().getY());
			m.put("size", npc.getComposition() == null ? 1 : npc.getComposition().getSize());
			npcs.add(m);
		}

		WorldPoint at = me.getWorldLocation();
		Map<String, Object> out = new LinkedHashMap<>();
		out.put("format", "ultimate-volcanic-mine map dump v1");
		out.put("tick", client.getTickCount());
		out.put("plane", plane);
		out.put("baseX", baseX);
		out.put("baseY", baseY);
		out.put("player", new int[]{at.getX(), at.getY()});
		out.put("tileColumns", new String[]{"x", "y", "collisionFlags", "overlayId", "underlayId", "shape"});
		out.put("tiles", tileRows);
		out.put("objects", objects);
		out.put("npcs", npcs);

		File dir = new File(RuneLite.RUNELITE_DIR, "ultimate-volcanic-mine");
		String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
		File file = new File(dir, "map_dump_" + stamp + ".json");
		try
		{
			if (!dir.isDirectory() && !dir.mkdirs())
			{
				return "Map dump: could not create " + dir;
			}
			Gson gson = new GsonBuilder().create();
			try (Writer w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))
			{
				gson.toJson(out, w);
			}
		}
		catch (IOException e)
		{
			return "Map dump failed: " + e.getMessage();
		}
		int total = 2 * 64 * 64;
		return String.format("Map dump: %d tiles, %d objects, %d NPCs -> %s (covers %d%% of the mine's two regions)",
			tileRows.size(), objects.size(), npcs.size(), file.getAbsolutePath(), 100 * vmTiles / total);
	}

	private Map<String, Object> object(String kind, TileObject o)
	{
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("kind", kind);
		m.put("id", o.getId());
		ObjectComposition def = client.getObjectDefinition(o.getId());
		m.put("name", def == null ? null : def.getName());
		if (def != null && def.getImpostorIds() != null)
		{
			ObjectComposition imp = def.getImpostor();
			m.put("impostorName", imp == null ? null : imp.getName());
			m.put("impostorId", imp == null ? null : imp.getId());
		}
		WorldPoint wp = o.getWorldLocation();
		m.put("x", wp.getX());
		m.put("y", wp.getY());
		return m;
	}
}
