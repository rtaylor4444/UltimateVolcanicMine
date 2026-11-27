package com.ultimatevm;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import javax.imageio.ImageIO;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;
import net.runelite.client.ui.DrawManager;

/**
 * One folder per game under .runelite/ultimate-volcanic-mine/coach/: trace.jsonl (every tick's
 * state, XP drops, points, clicks, boulder events, each coach step and every decision with its
 * inputs) and a screenshot of the game at each new coach step. volcanicMine/verify_coach_trace.py
 * re-runs the decisions in the simulator and compares XP, mining times and walking legs with it.
 */
@Slf4j
class CoachTracer
{
	private static final Gson GSON = new GsonBuilder().serializeSpecialFloatingPointValues().create();

	private final DrawManager drawManager;
	// One thread for the file, in order: packing candidates and writing never touch the game thread.
	private final java.util.concurrent.ExecutorService io = java.util.concurrent.Executors.newSingleThreadExecutor(r ->
	{
		Thread t = new Thread(r, "uvm-coach-trace");
		t.setDaemon(true);
		return t;
	});
	private final TraceExpander expander = new TraceExpander();
	private volatile boolean on;
	private BufferedWriter out;
	private File folder;
	private int lines;
	private int screenshots;
	private int tick;
	private int remaining;

	CoachTracer(DrawManager drawManager, Executor unused)
	{
		this.drawManager = drawManager;
	}

	boolean isOn()
	{
		return on;
	}

	File getFolder()
	{
		return folder;
	}

	/** Opens a new game's folder. */
	void start(Map<String, Object> header)
	{
		stop(null);
		String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
		folder = new File(new File(new File(RuneLite.RUNELITE_DIR, "ultimate-volcanic-mine"), "coach"), stamp);
		on = true;
		lines = 0;
		screenshots = 0;
		File gameFolder = folder;
		Map<String, Object> line = stamp("start", header);
		io.execute(() -> open(gameFolder, line));
	}

	private void open(File gameFolder, Map<String, Object> header)
	{
		expander.reset();
		try
		{
			if (!gameFolder.isDirectory() && !gameFolder.mkdirs())
			{
				log.warn("UVM Coach trace: could not create {}", gameFolder);
				on = false;
				return;
			}
			out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(new File(gameFolder, "trace.jsonl")), StandardCharsets.UTF_8));
		}
		catch (IOException e)
		{
			log.warn("UVM Coach trace: could not open", e);
			out = null;
			on = false;
			return;
		}
		write(header);
	}

	void stop(Map<String, Object> footer)
	{
		if (!on)
		{
			return;
		}
		on = false;
		Map<String, Object> line = footer == null ? null : stamp("end", footer);
		io.execute(() ->
		{
			if (out == null)
			{
				return;
			}
			if (line != null)
			{
				write(line);
			}
			try
			{
				out.close();
			}
			catch (IOException e)
			{
				log.warn("UVM Coach trace: could not close", e);
			}
			out = null;
		});
	}

	/** The clock for the records that follow (call once per tick, first). */
	void setClock(int tick, int remaining)
	{
		this.tick = tick;
		this.remaining = remaining;
	}

	void record(String type, Map<String, Object> fields)
	{
		if (!on)
		{
			return;
		}
		Map<String, Object> line = stamp(type, fields);
		io.execute(() -> write(line));
	}

	private Map<String, Object> stamp(String type, Map<String, Object> fields)
	{
		Map<String, Object> line = new LinkedHashMap<>();
		line.put("type", type);
		line.put("tick", tick);
		line.put("rem", remaining);
		if (fields != null)
		{
			line.putAll(fields);
		}
		return line;
	}

	/** On the trace thread. */
	private void write(Map<String, Object> line)
	{
		if (out == null)
		{
			return;
		}
		try
		{
			out.write(GSON.toJson(expander.expand(line)));
			out.newLine();
			if (++lines % 50 == 0)
			{
				out.flush();
			}
		}
		catch (IOException | RuntimeException e)
		{
			log.warn("UVM Coach trace: write failed", e);
		}
	}

	/** Plugin shutdown: finishes writing and ends the trace thread. */
	void shutdown()
	{
		stop(null);
		io.shutdown();
	}

	/** Saves the next drawn frame (with the overlays) as step_NNN_name.jpg; returns the file name. */
	String screenshot(String name)
	{
		if (!on || drawManager == null || folder == null)
		{
			return null;
		}
		String file = String.format("step_%03d_%s.jpg", ++screenshots, name.replaceAll("[^A-Za-z0-9]+", "_"));
		File target = new File(folder, file);
		drawManager.requestNextFrameListener(image -> io.execute(() -> write(image, target)));
		return file;
	}

	private static void write(Image image, File target)
	{
		try
		{
			BufferedImage rgb = new BufferedImage(image.getWidth(null), image.getHeight(null), BufferedImage.TYPE_INT_RGB);
			Graphics2D g = rgb.createGraphics();
			g.drawImage(image, 0, 0, null);
			g.dispose();
			ImageIO.write(rgb, "jpg", target);
		}
		catch (IOException | RuntimeException e)
		{
			log.warn("UVM Coach trace: screenshot failed", e);
		}
	}
}
