package com.xzh.economy;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.ItemStack;

import java.util.Set;

/**
 * Prevents official XZH pocket coins from being moved into storage inventories.
 * Ordinary gold nuggets and normal player-inventory movement remain unaffected.
 */
public final class CoinStorageGuard implements Listener {
    private static final Set<String> STORAGE_TYPES = Set.of(
            "CHEST", "ENDER_CHEST", "BARREL", "SHULKER_BOX",
            "HOPPER", "DISPENSER", "DROPPER", "FURNACE",
            "BLAST_FURNACE", "SMOKER", "BREWING",
            "CRAFTER", "CHISELED_BOOKSHELF",
            "MINECART", "MINECART_CHEST", "MINECART_HOPPER"
    );

    private final XZHEconomySystem plugin;

    public CoinStorageGuard(XZHEconomySystem plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    private boolean isStorage(Inventory inventory) {
        return inventory != null && STORAGE_TYPES.contains(inventory.getType().name());
    }

    private boolean isCoin(ItemStack item) {
        return plugin.getPocketCoinManager() != null
                && plugin.getPocketCoinManager().isCoin(item);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!isStorage(top)) return;

        Inventory clicked = event.getClickedInventory();
        if (clicked == null) return;

        // Cursor placement or cursor swap into a storage slot.
        if (clicked == top && isCoin(event.getCursor())) {
            InventoryAction action = event.getAction();
            if (action == InventoryAction.PLACE_ALL
                    || action == InventoryAction.PLACE_ONE
                    || action == InventoryAction.PLACE_SOME
                    || action == InventoryAction.SWAP_WITH_CURSOR) {
                event.setCancelled(true);
                return;
            }
        }

        // Shift-clicking from the player's inventory would route the coin into storage.
        if (event.isShiftClick()
                && clicked instanceof PlayerInventory
                && isCoin(event.getCurrentItem())) {
            event.setCancelled(true);
            return;
        }

        // Number-key hotbar swap into a storage slot.
        if (clicked == top && event.getClick() == ClickType.NUMBER_KEY) {
            int hotbar = event.getHotbarButton();
            if (hotbar >= 0 && hotbar < 9
                    && isCoin(event.getWhoClicked().getInventory().getItem(hotbar))) {
                event.setCancelled(true);
                return;
            }
        }

        // Offhand swap into a storage slot.
        if (clicked == top && event.getClick() == ClickType.SWAP_OFFHAND
                && isCoin(event.getWhoClicked().getInventory().getItemInOffHand())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!isStorage(event.getView().getTopInventory()) || !isCoin(event.getOldCursor())) return;
        int topSize = event.getView().getTopInventory().getSize();
        if (event.getRawSlots().stream().anyMatch(slot -> slot >= 0 && slot < topSize)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryMove(InventoryMoveItemEvent event) {
        // Covers hopper and other automated inventory-to-inventory transfers.
        if (isStorage(event.getDestination()) && isCoin(event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryPickup(InventoryPickupItemEvent event) {
        // Prevent hoppers from sucking official coins off the ground into storage.
        if (isStorage(event.getInventory()) && isCoin(event.getItem().getItemStack())) {
            event.setCancelled(true);
        }
    }
}
