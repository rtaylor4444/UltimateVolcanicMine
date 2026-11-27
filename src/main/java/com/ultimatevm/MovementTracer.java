package com.ultimatevm;

import com.google.gson.Gson;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.MenuAction;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.RuneLite;
import net.runelite.client.eventbus.Subscribe;

/**
 * Dev tool (::vmtrace toggles it): writes one JSON line per game tick to
 * .runelite/ultimate-volcanic-mine/trace_<time>.jsonl, for fitting the
 * route-time model (running, platform building, stairs, gas holes).
 *
 * Each tick line: tick, time-remaining varbit, player tile, animation, pose
 * animation, the boulder NPC's tile/id/size, and everything that happened
 * since the last tick: menu clicks (option, target, object id, scene tile) and
 * object spawns/despawns (lava, platform stages, chambers, rocks...) with their
 * tiles. Read-only towards the game. Registered on the event bus only while on.
 */
@Slf4j
class MovementTracer
{
	static final String COMMAND = "vmtrace";
	private static final int VARBIT_TIME_REMAINING = 5944;
	// Objects whose spawn/despawn is logged: lava, platform stages, chambers, rocks.
	private static final int MIN_LOGGED_OBJECT_ID = 30990;
	private static final int MAX_LOGGED_OBJECT_ID = 31060;

	private final Client client;
	private final Gson gson = new Gson();
	private BufferedWriter out;
	private File file;
	private final List<Map<String, Object>> pendingEvents = new ArrayList<>();

	MovementTracer(Client client)
	{
		this.client = client;
	}

	boolean isOn()
	{
		return out != null;
	}

	/** Opens a new trace file. Returns a chat message. */
	String start()
	{
		File dir = new File(RuneLite.RUNELITE_DIR, "ultimate-volcanic-mine");
		String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
		file = new File(dir, "trace_" + stamp + ".jsonl");
		try
		{
			if (!dir.isDirectory() && !dir.mkdirs())
			{
				return "Movement trace: could not create " + dir;
			}
			out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8));
		}
		catch (IOException e)
		{
			out = null;
			return "Movement trace failed: " + e.getMessage();
		}
		pendingEvents.clear();
		return "Movement trace ON -> " + file.getAbsolutePath() + " (type ::vmtrace again to stop)";
	}

	/** Closes the trace file. Returns a chat message. */
	String stop()
	{
		if (out != null)
		{
			try
			{
				out.close();
			}
			catch (IOException e)
			{
				log.warn("Movement trace close failed", e);
			}
			out = null;
		}
		return "Movement trace OFF, saved " + (file == null ? "" : file.getAbsolutePath());
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("type", "click");
		m.put("option", event.getMenuOption());
		m.put("target", event.getMenuTarget());
		MenuAction action = event.getMenuAction();
		m.put("action", action == null ? null : action.name());
		m.put("id", event.getId());
		m.put("sceneX", event.getParam0());
		m.put("sceneY", event.getParam1());
		if (client.getScene() != null)
		{
			m.put("worldX", client.getScene().getBaseX() + event.getParam0());
			m.put("worldY", client.getScene().getBaseY() + event.getParam1());
		}
		pendingEvents.add(m);
	}

	@Subscribe
	public void onGameObjectSpawned(GameObjectSpawned event)
	{
		logObject("spawn", event.getGameObject());
	}

	@Subscribe
	public void onGameObjectDespawned(GameObjectDespawned event)
	{
		logObject("despawn", event.getGameObject());
	}

	private void logObject(String type, GameObject o)
	{
		if (o == null || o.getId() < MIN_LOGGED_OBJECT_ID || o.getId() > MAX_LOGGED_OBJECT_ID)
		{
			return;
		}
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("type", type);
		m.put("id", o.getId());
		WorldPoint wp = o.getWorldLocation();
		m.put("x", wp.getX());
		m.put("y", wp.getY());
		pendingEvents.add(m);
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		if (out == null)
		{
			return;
		}
		Player me = client.getLocalPlayer();
		Map<String, Object> line = new LinkedHashMap<>();
		line.put("tick", client.getTickCount());
		line.put("rem", client.getVarbitValue(VARBIT_TIME_REMAINING));
		if (me != null && me.getWorldLocation() != null)
		{
			WorldPoint wp = me.getWorldLocation();
			line.put("x", wp.getX());
			line.put("y", wp.getY());
			line.put("plane", wp.getPlane());
			line.put("anim", me.getAnimation());
			line.put("pose", me.getPoseAnimation());
		}
		List<Map<String, Object>> boulders = new ArrayList<>();
		for (NPC npc : client.getNpcs())
		{
			if (npc != null && npc.getWorldLocation() != null
				&& ((npc.getId() >= 7807 && npc.getId() <= 7815)
					|| (npc.getName() != null && npc.getName().toLowerCase().contains("boulder"))))
			{
				Map<String, Object> b = new LinkedHashMap<>();
				b.put("id", npc.getId());
				b.put("x", npc.getWorldLocation().getX());
				b.put("y", npc.getWorldLocation().getY());
				b.put("size", npc.getComposition() == null ? 1 : npc.getComposition().getSize());
				boulders.add(b);
			}
		}
		if (!boulders.isEmpty())
		{
			line.put("boulders", boulders);
		}
		if (!pendingEvents.isEmpty())
		{
			line.put("events", new ArrayList<>(pendingEvents));
			pendingEvents.clear();
		}
		try
		{
			out.write(gson.toJson(line));
			out.newLine();
			out.flush();
		}
		catch (IOException e)
		{
			log.warn("Movement trace write failed", e);
			stop();
		}
	}
}
