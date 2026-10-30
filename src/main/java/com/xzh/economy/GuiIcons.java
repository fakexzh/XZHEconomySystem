package com.xzh.economy;

import java.util.Locale;
import java.util.Map;

import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.meta.ItemMeta;

/** Applies XZH pixel-art resource-pack icons to GUI buttons without changing their slots or actions. */
public final class GuiIcons {
    private static volatile boolean enabled = true;
    private static final Map<String, String> CATEGORY = Map.ofEntries(
            Map.entry("ores", "ore_chunk"), Map.entry("blocks", "stone_block"),
            Map.entry("armor", "chestplate"), Map.entry("tools", "pickaxe"),
            Map.entry("food", "bread"), Map.entry("seeds", "wheat"),
            Map.entry("essentials", "supply_crate"), Map.entry("special", "treasure_chest"),
            Map.entry("flowers-dyes", "flower"), Map.entry("eggs-heads", "mob_head"),
            Map.entry("travel", "compass"), Map.entry("xp", "xp_orb"),
            Map.entry("potions", "potion"), Map.entry("bounty-maps", "bounty_skull"));
    private static final Map<String, String> ACTION = Map.ofEntries(
            Map.entry("books", "ench_book"), Map.entry("quests", "quest_scroll"),
            Map.entry("section-single", "quest_scroll"), Map.entry("section-packs", "quest_scroll"),
            Map.entry("search", "magnifier"), Map.entry("bank", "vault"),
            Map.entry("transition", "ledger"), Map.entry("players-money", "player_coins"),
            Map.entry("deathinventory", "recovery_chest"), Map.entry("help", "help"),
            Map.entry("sell-hand", "hand_sell"), Map.entry("back", "arrow_left"),
            Map.entry("category-back", "arrow_left"), Map.entry("back-list", "arrow_left"),
            Map.entry("back-shop", "arrow_left"), Map.entry("back-packs", "arrow_left"),
            Map.entry("back-tiers", "arrow_left"), Map.entry("quantity-back", "arrow_left"),
            Map.entry("hand-sell-cancel", "arrow_left"), Map.entry("quantity-buy", "basket"),
            Map.entry("confirm-sell", "sell_coin"), Map.entry("quantity-plus", "plus_1"),
            Map.entry("quantity-plus-10", "plus_10"), Map.entry("quantity-plus-64", "plus_64"),
            Map.entry("quantity-minus", "minus_1"), Map.entry("quantity-minus-10", "minus_10"),
            Map.entry("quantity-minus-64", "minus_64"), Map.entry("refresh", "vault_coins"),
            Map.entry("deposit", "deposit"), Map.entry("deposit-all", "deposit"),
            Map.entry("withdraw", "withdraw"), Map.entry("withdraw-all", "withdraw"),
            Map.entry("loans", "loan_doc"), Map.entry("status", "loan_doc"),
            Map.entry("repay", "coin_check"), Map.entry("recover", "recovery_chest"));
    private GuiIcons() { }
    public static void setEnabled(boolean value) { enabled = value; }
    public static void apply(ItemMeta meta, String icon) {
        if (!enabled || meta == null || icon == null) return;
        NamespacedKey key = NamespacedKey.fromString("xzh:gui/" + icon);
        if (key != null) meta.setItemModel(key);
    }
    public static void apply(ItemMeta meta, String action, String title, String value) {
        apply(meta, resolve(action, title, value));
    }
    static String resolve(String action, String title, String value) {
        String t = title == null ? "" : ChatColor.stripColor(title).trim().toLowerCase(Locale.ROOT);
        if (t.equals("cancel")) return "cross";
        if (t.contains("confirm")) return "check";
        if (t.equals("previous page")) return "chev_left";
        if (t.equals("next page")) return "chev_right";
        if (action == null) return null;
        if (action.equals("category")) return value == null ? null : CATEGORY.get(value);
        return ACTION.get(action);
    }
}
