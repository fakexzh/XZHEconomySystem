package com.xzh.economy;

import org.bukkit.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Prevents players from dragging items into custom economy menus.
 * Click handlers already cancel InventoryClickEvent; drags use a separate event.
 */
public final class GuiDragGuard implements Listener {
    private static final String[] PROTECTED_TITLES = {
            "Shop", "Shop: ", "Choose quantity", "Confirm transaction",
            "Enchantment Books ", "Confirm enchanted book",
            "Loans", "Choose loan term", "Confirm loan",
            "Quest Packs", "Confirm quest pack", "Confirm abandon quest", "Global Event",
            "Death Inventory Recovery", "Admin"
    };

    public GuiDragGuard(JavaPlugin plugin) {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        String title = ChatColor.stripColor(event.getView().getTitle());
        if (title == null) return;
        for (String protectedTitle : PROTECTED_TITLES) {
            if (title.equals(protectedTitle) || title.startsWith(protectedTitle)) {
                event.setCancelled(true);
                return;
            }
        }
    }
}
