package com.ultimatevm;

import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.HashSet;

import static net.runelite.api.ItemID.*;

public class PickaxeProtector {
    public static int INVENTORY_SIZE = 28;
    static HashSet<Integer> protectedPickaxeTypes;
    private final Client client;
    HashSet<Integer> startingPickaxes;
    private int numTicksPickaxeDropped;
    @Inject
    PickaxeProtector(Client client) {
        this.client = client;
        startingPickaxes = new HashSet<>();
        resetStartingPickaxes();
        if(protectedPickaxeTypes != null) return;
        protectedPickaxeTypes = new HashSet<>(Arrays.asList(_3RD_AGE_PICKAXE, DRAGON_PICKAXE,
                DRAGON_PICKAXE_12797, DRAGON_PICKAXE_OR, DRAGON_PICKAXE_OR_25376, CRYSTAL_PICKAXE,
                CRYSTAL_PICKAXE_23863, CRYSTAL_PICKAXE_INACTIVE, CORRUPTED_PICKAXE, INFERNAL_PICKAXE,
                INFERNAL_PICKAXE_OR, INFERNAL_PICKAXE_UNCHARGED, INFERNAL_PICKAXE_UNCHARGED_25369,
                RUNE_PICKAXE));
    }

    void getStartingPickaxes() {
        if(!startingPickaxes.isEmpty()) return;
        final ItemContainer itemContainer = client.getItemContainer(InventoryID.INVENTORY);
        if (itemContainer == null) return;
        final Item[] items = itemContainer.getItems();
        for (int i = 0; i < INVENTORY_SIZE; i++) {
            if (i < items.length) {
                final Item item = items[i];
                if(!protectedPickaxeTypes.contains(item.getId())) continue;
                if (item.getQuantity() > 0) startingPickaxes.add(item.getId());
            }
        }
    }
    void resetStartingPickaxes() {
        startingPickaxes.clear();
        resetTicksDropped();
    }
    boolean isPickaxeDropped() {
        final ItemContainer itemContainer = client.getItemContainer(InventoryID.INVENTORY);
        if (itemContainer == null) return false;
        HashSet<Integer> droppedPickaxes = new HashSet<>(startingPickaxes);
        final Item[] items = itemContainer.getItems();
        for (int i = 0; i < INVENTORY_SIZE; i++) {
            if (i < items.length) {
                final Item item = items[i];
                if (item.getQuantity() > 0) {
                    //remove potential dropped since its in the inventory
                    droppedPickaxes.remove(item.getId());
                }
            }
        }
        return !droppedPickaxes.isEmpty();
    }
    void incrementTicksDropped() { ++numTicksPickaxeDropped; }
    void resetTicksDropped() { numTicksPickaxeDropped = 0; }
    int getNumTicksPickaxeDropped() { return numTicksPickaxeDropped; }
}
