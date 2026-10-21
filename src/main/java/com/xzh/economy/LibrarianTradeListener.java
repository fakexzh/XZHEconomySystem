package com.xzh.economy;

import org.bukkit.Material;
import org.bukkit.entity.Merchant;
import org.bukkit.entity.Villager;
import org.bukkit.entity.WanderingTrader;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.VillagerAcquireTradeEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.MerchantRecipe;

import java.util.ArrayList;
import java.util.List;

/** Removes book-related trades from villagers and wandering traders while preserving other offers. */
public final class LibrarianTradeListener implements Listener {
    private final XZHEconomySystem plugin;

    public LibrarianTradeListener(XZHEconomySystem plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onAcquireTrade(VillagerAcquireTradeEvent event) {
        if (containsBookTradeItem(event.getRecipe())) event.setCancelled(true);
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Villager)
                && !(event.getRightClicked() instanceof WanderingTrader)) return;
        if (!(event.getRightClicked() instanceof Merchant merchant)) return;

        List<MerchantRecipe> filtered = new ArrayList<>();
        boolean changed = false;
        for (MerchantRecipe recipe : merchant.getRecipes()) {
            if (containsBookTradeItem(recipe)) changed = true;
            else filtered.add(recipe);
        }
        if (changed) merchant.setRecipes(filtered);
    }

    private boolean containsBookTradeItem(MerchantRecipe recipe) {
        if (isBookMaterial(recipe.getResult().getType())) return true;
        for (org.bukkit.inventory.ItemStack ingredient : recipe.getIngredients()) {
            if (ingredient != null && isBookMaterial(ingredient.getType())) return true;
        }
        return false;
    }

    private boolean isBookMaterial(Material material) {
        return material.name().contains("BOOK");
    }
}
