package com.ultimatevm;

import java.awt.Color;
import java.util.List;

import net.runelite.api.Client;
import net.runelite.api.ItemID;
import net.runelite.api.Player;
import net.runelite.client.ui.overlay.infobox.InfoBox;

public class PlayerCountInfoBox extends InfoBox
{
    private final Client client;
    private final UltimateVolcanicMinePlugin plugin;
    private int playerCount = 0;

    public PlayerCountInfoBox(UltimateVolcanicMinePlugin plugin, Client client)
    {
        super(plugin.getItemManager().getImage(ItemID.DRAGON_PICKAXE), plugin);
        this.client = client;
        this.plugin = plugin;
    }

    @Override
    public String getText()
    {
        return String.valueOf(playerCount);
    }

    public void updatePlayerCount()
    {
        this.playerCount = countMiningPlayers();
    }

    @Override
    public Color getTextColor()
    {
        return Color.WHITE;
    }

    @Override
    public String getTooltip()
    {
        return "Players currently mining the boulder: " + playerCount;
    }

    private int countMiningPlayers()
    {
        int count = 0;
        List<Player> players = client.getPlayers();

        if (players == null || players.isEmpty())
        {
            return 0;
        }

        for (Player player : players)
        {
            if (player == null)
            {
                continue;
            }

            int animationId = player.getAnimation();
            if (VolcanicMineMiningAnimation.VOLCANIC_MINE_ANIMATIONS.contains(animationId))
            {
                count++;
            }
        }

        return count;
    }
}