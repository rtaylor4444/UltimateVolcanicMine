package com.ultimatevm;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class UltimateVolcanicMinePluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(UltimateVolcanicMinePlugin.class);
		RuneLite.main(args);
	}
}