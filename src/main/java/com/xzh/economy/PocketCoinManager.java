package com.xzh.economy;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Physical, server-tagged pocket currency. Coins may stay in player inventory or be deposited in the bank, never stored in containers. */
public final class PocketCoinManager implements Listener {
    private static final int[] DENOMINATIONS = {1000, 100, 10, 1};
    private final XZHEconomySystem plugin;
    private final NamespacedKey denominationKey;

    public PocketCoinManager(XZHEconomySystem plugin) {
        this.plugin = plugin;
        denominationKey = new NamespacedKey(plugin, "coin_denomination");
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public double count(Player player) {
        long total = 0;
        PlayerInventory inventory = player.getInventory();
        for (ItemStack item : inventory.getStorageContents()) total += valueOf(item);
        total += valueOf(inventory.getItemInOffHand());
        total += valueOf(player.getOpenInventory().getCursor());
        return total;
    }

    public void setCoins(Player player, double requestedAmount) {
        long target = Math.min((long) plugin.getPocketBalanceLimit(), Math.max(0L, Math.round(requestedAmount)));
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getStorageContents().length; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (isCoin(item)) inventory.setItem(slot, null);
        }
        if (isCoin(inventory.getItemInOffHand())) inventory.setItemInOffHand(null);
        if (isCoin(player.getOpenInventory().getCursor())) player.getOpenInventory().setCursor(null);

        List<ItemStack> coins = new ArrayList<>();
        long remaining = target;
        for (int denomination : DENOMINATIONS) {
            int count = (int) Math.min(Integer.MAX_VALUE, remaining / denomination);
            remaining -= (long) count * denomination;
            while (count > 0) {
                int stack = Math.min(64, count);
                coins.add(createCoin(denomination, stack));
                count -= stack;
            }
        }
        for (ItemStack coinStack : coins) {
            var leftovers = inventory.addItem(coinStack);
            for (ItemStack leftover : leftovers.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
        }
    }

    public boolean isCoin(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(denominationKey, PersistentDataType.INTEGER);
    }

    private long valueOf(ItemStack item) {
        if (!isCoin(item)) return 0;
        Integer denomination = item.getItemMeta().getPersistentDataContainer()
                .get(denominationKey, PersistentDataType.INTEGER);
        if (denomination == null || (denomination != 1 && denomination != 10 && denomination != 100 && denomination != 1000)) return 0;
        return (long) item.getAmount() * denomination;
    }

    public ItemStack createCoin(int denomination, int amount) {
        // Use a non-placeable nugget base for every denomination; the persistent tag and name
        // distinguish official currency from ordinary gold nuggets.
        ItemStack item = new ItemStack(Material.GOLD_NUGGET, amount);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "XZH Coin " + ChatColor.YELLOW + "(" + denomination + ")");
        // Minecraft 26.3 reads custom model data through the component's float list.
        // Set that component explicitly so the resource-pack range_dispatch sees the denomination.
        var customModelData = meta.getCustomModelDataComponent();
        customModelData.setFloats(List.of((float) denomination));
        meta.setCustomModelDataComponent(customModelData);
        meta.setLore(List.of(ChatColor.GRAY + "Official XZH pocket currency",
                ChatColor.GRAY + "Value: " + denomination + " coin" + (denomination == 1 ? "" : "s"),
                ChatColor.DARK_GRAY + "Dropped on death; bank deposits are safe."));
        meta.getPersistentDataContainer().set(denominationKey, PersistentDataType.INTEGER, denomination);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onCraft(CraftItemEvent event) {
        for (ItemStack item : event.getInventory().getMatrix()) {
            if (isCoin(item)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        if (isCoin(event.getItemInHand())) event.setCancelled(true);
    }

    /**
     * Only player inventory is a valid item destination for physical coins.
     * This covers cursor placement, shift-click, number-key hotbar swaps and offhand swaps.
     * Taking coins out of a container remains possible so old/illegally stored coins can be recovered.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory clicked = event.getClickedInventory();
        if (clicked == null) return;

        // Shift-clicking a coin from the player inventory would send it to the open container.
        if (clicked == player.getInventory()) {
            if (event.isShiftClick() && isCoin(event.getCurrentItem())) {
                event.setCancelled(true);
            }
            return;
        }

        // Cursor placement or cursor/slot swapping would move a coin into a non-player inventory.
        if (isCoin(event.getCursor())) {
            event.setCancelled(true);
            return;
        }

        // Number-key swaps move the selected hotbar item into the clicked container slot.
        if (event.getClick() == ClickType.NUMBER_KEY) {
            int hotbarButton = event.getHotbarButton();
            if (hotbarButton >= 0 && hotbarButton < 9
                    && isCoin(player.getInventory().getItem(hotbarButton))) {
                event.setCancelled(true);
                return;
            }
        }

        // The offhand swap key can also move a coin into the clicked container slot.
        if (event.getClick() == ClickType.SWAP_OFFHAND
                && isCoin(player.getInventory().getItemInOffHand())) {
            event.setCancelled(true);
            return;
        }

    }

    /** Prevent dragging coins from the cursor into any non-player inventory. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!isCoin(event.getOldCursor())) return;
        int topSize = event.getView().getTopInventory().getSize();
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < topSize) {
                event.setCancelled(true);
                return;
            }
        }
    }

    /** Prevent hoppers and other inventory automation from storing/transferring official coins. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryMove(InventoryMoveItemEvent event) {
        if (isCoin(event.getItem())) event.setCancelled(true);
    }

    /** Prevent hoppers or other inventories from picking up dropped official coins. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryPickup(InventoryPickupItemEvent event) {
        if (isCoin(event.getItem().getItemStack())) event.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.setOfflinePocketSnapshot(event.getPlayer().getUniqueId().toString(), count(event.getPlayer()));
    }
}
