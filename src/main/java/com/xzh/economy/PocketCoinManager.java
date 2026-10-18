package com.xzh.economy;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Physical, server-tagged pocket currency. These items naturally drop with inventory on death. */
public final class PocketCoinManager implements Listener {
    private static final int[] DENOMINATIONS = {100, 10, 1};
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
        return total;
    }

    public void setCoins(Player player, double requestedAmount) {
        long target = Math.max(0L, Math.round(requestedAmount));
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getStorageContents().length; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (isCoin(item)) inventory.setItem(slot, null);
        }
        if (isCoin(inventory.getItemInOffHand())) inventory.setItemInOffHand(null);

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
        if (denomination == null || (denomination != 1 && denomination != 10 && denomination != 100)) return 0;
        return (long) item.getAmount() * denomination;
    }

    private ItemStack createCoin(int denomination, int amount) {
        Material material = denomination == 100 ? Material.GOLD_BLOCK
                : denomination == 10 ? Material.GOLD_INGOT : Material.GOLD_NUGGET;
        ItemStack item = new ItemStack(material, amount);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "XZH Coin " + ChatColor.YELLOW + "(" + denomination + ")");
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

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        // Coins remain in the normal death drops. Update the offline balance snapshot so it
        // cannot be spent a second time while the player is dead or offline.
        if (!event.getKeepInventory()) {
            String key = event.getEntity().getUniqueId().toString();
            plugin.setOfflinePocketSnapshot(key, 0.0);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.setOfflinePocketSnapshot(event.getPlayer().getUniqueId().toString(), count(event.getPlayer()));
    }
}
