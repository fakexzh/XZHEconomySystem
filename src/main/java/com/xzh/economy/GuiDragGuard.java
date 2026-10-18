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
            "XZH Shop", "Shop: ", "Choose quantity", "Confirm transaction",
            "XZH Enchantment Books ", "Confirm enchanted book",
            "XZH Loans", "Choose loan term", "Confirm loan",
            "XZH Quest Packs", "Confirm quest pack", "Confirm abandon quest", "XZH Global Event",
            "Death Inventory Recovery", "XZH Admin"
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
