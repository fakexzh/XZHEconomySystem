package com.xzh.economy;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/** Protected, vanilla-client hotbar shortcuts for the XZH shop and bank GUIs. */
public final class HotbarMenuManager implements Listener {
    private final XZHEconomySystem plugin;
    private final NamespacedKey menuKey;
    private static final String SHOP = "shop";
    private static final String BANK = "bank";

    public HotbarMenuManager(XZHEconomySystem plugin) {
        this.plugin = plugin;
        this.menuKey = new NamespacedKey(plugin, "protected_menu_item");
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void ensureItems(Player player) {
        ensureItem(player, SHOP);
        ensureItem(player, BANK);
    }

    private void ensureItem(Player player, String menu) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (isMenuItem(item, menu)) return;
        }
        ItemStack item = createItem(menu);
        int slot = findFreeHotbarSlot(player);
        if (slot >= 0) {
            player.getInventory().setItem(slot, item);
            return;
        }
        int free = player.getInventory().firstEmpty();
        if (free >= 0) {
            player.getInventory().setItem(free, item);
            player.sendMessage(ChatColor.YELLOW + "Your XZH " + menu.toUpperCase() + " shortcut was placed in your inventory because your hotbar is full.");
        } else {
            player.sendMessage(ChatColor.RED + "Make room in your inventory to receive the protected XZH " + menu.toUpperCase() + " shortcut.");
        }
    }

    private int findFreeHotbarSlot(Player player) {
        for (int i = 0; i < 9; i++) {
            if (player.getInventory().getItem(i) == null || player.getInventory().getItem(i).getType().isAir()) return i;
        }
        return -1;
    }

    private ItemStack createItem(String menu) {
        Material material = SHOP.equals(menu) ? Material.COMPASS : Material.EMERALD;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName((SHOP.equals(menu) ? ChatColor.GREEN : ChatColor.AQUA) + "XZH " + menu.toUpperCase());
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Right-click to open the " + menu.toUpperCase() + ".");
        lore.add(ChatColor.DARK_GRAY + "Protected server shortcut");
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(menuKey, PersistentDataType.STRING, menu);
        item.setItemMeta(meta);
        return item;
    }

    private String getMenu(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(menuKey, PersistentDataType.STRING);
    }

    private boolean isMenuItem(ItemStack item, String menu) {
        return menu.equals(getMenu(item));
    }

    private boolean isMenuItem(ItemStack item) {
        return getMenu(item) != null;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> ensureItems(event.getPlayer()), 1L);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> ensureItems(event.getPlayer()), 1L);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        String menu = getMenu(event.getItem());
        if (menu == null) return;
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (SHOP.equals(menu)) plugin.getShopManager().openMain(player);
        else if (BANK.equals(menu)) plugin.getBankManager().open(player);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (isMenuItem(event.getItemDrop().getItemStack())) event.setCancelled(true);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (isMenuItem(event.getCurrentItem()) || isMenuItem(event.getCursor())) {
            event.setCancelled(true);
            return;
        }
        if (event.getHotbarButton() >= 0 && isMenuItem(event.getWhoClicked().getInventory().getItem(event.getHotbarButton()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (isMenuItem(event.getOldCursor())) event.setCancelled(true);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        event.getDrops().removeIf(this::isMenuItem);
    }
}
