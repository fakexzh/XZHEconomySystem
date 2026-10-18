package com.xzh.economy;

import org.bukkit.Material;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.VillagerAcquireTradeEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.MerchantRecipe;

import java.util.ArrayList;
import java.util.List;

/** Prevents librarians from offering enchanted books, including already generated offers. */
public final class LibrarianTradeListener implements Listener {
    @EventHandler
    public void onAcquireTrade(VillagerAcquireTradeEvent event) {
        if (event.getEntity().getProfession() == Villager.Profession.LIBRARIAN
                && event.getRecipe().getResult().getType() == Material.ENCHANTED_BOOK) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Villager villager)
                || villager.getProfession() != Villager.Profession.LIBRARIAN) return;
        List<MerchantRecipe> recipes = villager.getRecipes();
        List<MerchantRecipe> filtered = new ArrayList<>();
        boolean changed = false;
        for (MerchantRecipe recipe : recipes) {
            if (recipe.getResult().getType() == Material.ENCHANTED_BOOK) changed = true;
            else filtered.add(recipe);
        }
        if (changed) villager.setRecipes(filtered);
    }
}
