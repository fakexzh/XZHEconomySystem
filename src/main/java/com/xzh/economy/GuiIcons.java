package com.xzh.economy;

import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Locale;
import java.util.Map;

/**
 * Maps menu buttons to custom item models in the XZH resource pack (xzh:gui/NAME).
 * Players without the pack still see the normal vanilla item, so nothing breaks.
 * Disable with gui.custom-icons: false in config.yml.
 */
final class GuiIcons {
    private static volatile boolean enabled = true;

    // action|value -> icon (checked first)
    private static final Map<String, String> BY_ACTION_VALUE = Map.ofEntries(
        Map.entry("category|ores", "ores"), Map.entry("category|blocks", "blocks"),
        Map.entry("category|armor", "armor"), Map.entry("category|tools", "tools"),
        Map.entry("category|food", "food"), Map.entry("category|food-cooked", "food"),
        Map.entry("category|food-raw", "food"), Map.entry("category|seeds", "seeds"),
        Map.entry("category|essentials", "essentials"), Map.entry("category|special", "special"),
        Map.entry("category|flowers-dyes", "flowers"), Map.entry("category|eggs-heads", "eggs"),
        Map.entry("category|travel", "travel"), Map.entry("category|xp", "xp"),
        Map.entry("category|potions", "potions"), Map.entry("category|bounty-maps", "bounty"),
        Map.entry("transition|history", "ledger"),
        // quest tiers
        Map.entry("pack-tier|easy", "easy"), Map.entry("pack-tier|hard", "hard"), Map.entry("pack-tier|master", "master"),
        Map.entry("tier-locked|easy", "easy"), Map.entry("tier-locked|hard", "hard"), Map.entry("tier-locked|master", "master")
    );

    // action -> icon (checked second)
    private static final Map<String, String> BY_ACTION = Map.ofEntries(
        Map.entry("books", "books"), Map.entry("quests", "quests"), Map.entry("bank", "bank"),
        Map.entry("players-money", "players"), Map.entry("help", "help"), Map.entry("balance", "coins"),
        Map.entry("deathinventory", "death"), Map.entry("recover", "death"),
        Map.entry("sell-hand", "handsell"), Map.entry("sell-hand-confirm", "handsell"),
        Map.entry("shop-search", "search"), Map.entry("search-again", "search"),
        Map.entry("back-shop", "home"), Map.entry("category-back", "back"), Map.entry("back-packs", "back"), Map.entry("back-tiers", "back"),
        // trade
        Map.entry("buy", "buy"), Map.entry("confirm-buy", "buy"), Map.entry("xp-buy", "buy"), Map.entry("xp-buy-confirm", "buy"),
        Map.entry("sell", "sell"), Map.entry("confirm-sell", "sell"), Map.entry("xp-sell", "sell"), Map.entry("xp-sell-confirm", "sell"),
        Map.entry("quantity-minus", "minus"), Map.entry("quantity-plus", "plus"),
        Map.entry("quantity-minus-10", "minus10"), Map.entry("quantity-plus-10", "plus10"),
        Map.entry("quantity-minus-64", "minus64"), Map.entry("quantity-plus-64", "plus64"),
        // bank + loans
        Map.entry("refresh", "refresh"), Map.entry("deposit", "deposit"), Map.entry("deposit-all", "deposit_all"),
        Map.entry("withdraw", "withdraw"), Map.entry("withdraw-all", "withdraw_all"), Map.entry("loans", "loans"),
        Map.entry("repay", "repay"), Map.entry("amount", "amount"), Map.entry("term", "timer"),
        // quests
        Map.entry("section-risk", "risk"), Map.entry("section-packs", "quest_packs"), Map.entry("section-community", "global_quest"),
        Map.entry("section-single", "risk"), Map.entry("section-global", "global_quest"), Map.entry("global-info", "global_quest"),
        Map.entry("global-claim", "claim"), Map.entry("risk-buy", "risk"), Map.entry("risk-secret-buy", "secret"),
        Map.entry("risk-info", "hourglass"), Map.entry("single-info", "hourglass"),
        Map.entry("pack-completed", "completed"), Map.entry("locked", "lock"), Map.entry("tier-locked", "lock"),
        Map.entry("ore", "ore"), Map.entry("mob", "mob"), Map.entry("fish", "fish"), Map.entry("wood", "wood"), Map.entry("crop", "crop"),
        // generic
        Map.entry("accept", "confirm"), Map.entry("decline", "cancel"),
        Map.entry("confirm", "confirm"), Map.entry("cancel", "cancel"), Map.entry("close", "close")
    );

    private GuiIcons() { }

    static void setEnabled(boolean value) { enabled = value; }

    /** Sets the item_model component for a menu button when a matching icon exists. */
    static void apply(ItemMeta meta, String action, String value, String title) {
        if (!enabled || meta == null) return;
        String icon = lookup(action == null ? "" : action, value == null ? "" : value, title);
        if (icon != null) meta.setItemModel(new NamespacedKey("xzh", "gui/" + icon));
    }

    private static String lookup(String action, String value, String title) {
        String icon = BY_ACTION_VALUE.get(action + "|" + value);
        if (icon != null) return icon;
        icon = BY_ACTION.get(action);
        if (icon != null) return icon;
        String t = ChatColor.stripColor(title == null ? "" : title).toLowerCase(Locale.ROOT);
        if (action.equals("quantity-buy")) return t.startsWith("sell") ? "sell" : "buy";
        if (t.startsWith("previous")) return "arrow_left";
        if (t.startsWith("next")) return "arrow_right";
        if (t.startsWith("back to shop")) return "home";
        if (t.startsWith("back") || t.equals("difficulties")) return "back";
        if (t.equals("cancel") || t.equals("decline") || t.equals("close")) return "cancel";
        if (t.contains("confirm") || t.equals("accept") || t.equals("claim reward")) return "confirm";
        if (t.startsWith("borrow") || t.equals("contributor rewards")) return "coins";
        if (t.endsWith(" days")) return "hourglass";
        return null;
    }
}
