package com.xzh.economy;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Enchantment book catalogue with confirmation, per-player 24-hour cooldowns, and daily global stock. */
public final class BookShopManager implements Listener {
    
    private static final List<String> KEYS = List.of(
            "aqua_affinity", "bane_of_arthropods", "binding_curse", "blast_protection",
            "breach", "channeling", "density", "depth_strider", "efficiency", "feather_falling",
            "fire_aspect", "fire_protection", "flame", "fortune", "frost_walker", "impaling",
            "infinity", "knockback", "looting", "loyalty", "luck_of_the_sea", "lure", "mending",
            "multishot", "piercing", "power", "projectile_protection", "protection", "punch",
            "quick_charge", "respiration", "riptide", "sharpness", "silk_touch", "smite",
            "soul_speed", "sweeping_edge", "swift_sneak", "thorns", "unbreaking", "vanishing_curse",
            "wind_burst", "lunge"
    );
    private final XZHEconomySystem plugin;
    private final File file;
    private final YamlConfiguration data;
    private final NamespacedKey actionKey;
    private final NamespacedKey bookKey;

    public BookShopManager(XZHEconomySystem plugin) {
        this.plugin = plugin;
        file = new File(plugin.getDataFolder(), "books.yml");
        data = YamlConfiguration.loadConfiguration(file);
        actionKey = new NamespacedKey(plugin, "book_shop_action");
        bookKey = new NamespacedKey(plugin, "book_shop_key");
        resetStockIfNeeded();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player) { open(player, 0); }

    private void open(Player player, int page) {
        List<String> available = KEYS.stream().filter(k -> enchantment(k) != null).toList();
        int pages = Math.max(1, (available.size() + 44) / 45);
        int currentPage = Math.max(0, Math.min(page, pages - 1));
        Inventory inv = Bukkit.createInventory(null, 54, ChatColor.DARK_PURPLE + "XZH Enchantment Books " + (currentPage + 1));
        int start = currentPage * 45;
        int end = Math.min(available.size(), start + 45);
        for (int i = start; i < end; i++) {
            String key = available.get(i);
            Enchantment enchant = enchantment(key);
            ItemStack item = createBook(key);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(ChatColor.YELLOW + displayName(key) + " " + roman(enchant.getMaxLevel()));
            long until = data.getLong("cooldowns." + player.getUniqueId() + "." + key, 0L);
            int stock = stock(key);
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.WHITE + "Price: " + ChatColor.GOLD + price(key) + " coins");
            lore.add(ChatColor.WHITE + "Global stock today: " + ChatColor.AQUA + stock);
            lore.add(ChatColor.WHITE + "Personal cooldown: " + (until > System.currentTimeMillis()
                    ? ChatColor.RED + remaining(until) : ChatColor.GREEN + "Available"));
            lore.add(ChatColor.GRAY + "Click to review and confirm");
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "select");
            meta.getPersistentDataContainer().set(bookKey, PersistentDataType.STRING, key);
            item.setItemMeta(meta);
            inv.setItem(i - start, item);
        }
        if (currentPage > 0) add(inv, 45, Material.ARROW, "Previous page", "page", Integer.toString(currentPage - 1));
        add(inv, 49, Material.BARRIER, "Back to shop", "back", "");
        if (currentPage + 1 < pages) add(inv, 53, Material.ARROW, "Next page", "page", Integer.toString(currentPage + 1));
        player.openInventory(inv);
    }

    private void confirm(Player player, String key) {
        Enchantment enchant = enchantment(key);
        if (enchant == null) return;
        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.DARK_RED + "Confirm enchanted book");
        ItemStack book = createBook(key);
        ItemMeta meta = book.getItemMeta();
        meta.setDisplayName(ChatColor.YELLOW + displayName(key) + " " + roman(enchant.getMaxLevel()));
        meta.setLore(List.of(ChatColor.WHITE + "Price: " + ChatColor.GOLD + price(key) + " coins",
                ChatColor.WHITE + "Global stock today: " + stock(key),
                ChatColor.GRAY + "One book per purchase. 24-hour personal cooldown."));
        book.setItemMeta(meta);
        inv.setItem(13, book);
        add(inv, 11, Material.LIME_CONCRETE, "Confirm purchase", "buy", key);
        add(inv, 15, Material.RED_CONCRETE, "Cancel", "back-list", "");
        player.openInventory(inv);
    }

    private void buy(Player player, String key) {
        if (plugin.getLoanManager().isShopPurchaseRestricted(player.getUniqueId())) {
            plugin.tellPublic(player, "Your loan is overdue. New purchases are locked until you repay it with /loan repay.");
            return;
        }
        Enchantment enchant = enchantment(key);
        if (enchant == null) return;
        long now = System.currentTimeMillis();
        String cooldownPath = "cooldowns." + player.getUniqueId() + "." + key;
        long until = data.getLong(cooldownPath, 0L);
        if (until > now) {
            plugin.tellPublic(player, "You can buy " + displayName(key) + " again in " + remaining(until) + ".");
            return;
        }
        resetStockIfNeeded();
        if (stock(key) <= 0) {
            plugin.tellPublic(player, "That enchantment book is out of global stock for today.");
            return;
        }
        double cost = price(key);
        if (plugin.getBalance(player.getUniqueId()) < cost) {
            plugin.tellPublic(player, "Not enough coins. This book costs " + cost + ".");
            return;
        }
        ItemStack book = createBook(key);
        if (player.getInventory().firstEmpty() < 0) {
            plugin.tellPublic(player, "Your inventory is full.");
            return;
        }
        // Persist the cooldown and stock decrement before delivery to avoid duplicate purchases after a crash.
        data.set(cooldownPath, now + plugin.getConfig().getLong("book-shop.cooldown-hours", 24) * 60L * 60L * 1000L);
        data.set("stock." + key, stock(key) - 1);
        save();
        plugin.setBalance(player.getUniqueId(), plugin.getBalance(player.getUniqueId()) - cost);
        player.getInventory().addItem(book);
        plugin.tellPublic(player, "Purchased " + displayName(key) + " " + roman(enchant.getMaxLevel()) + " for " + cost + " coins.");
        player.closeInventory();
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = ChatColor.stripColor(event.getView().getTitle());
        if (title == null || !(title.startsWith("XZH Enchantment Books ") || title.equals("Confirm enchanted book"))) return;
        event.setCancelled(true);
        ItemStack item = event.getCurrentItem();
        if (item == null || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        String action = meta.getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
        String key = meta.getPersistentDataContainer().get(bookKey, PersistentDataType.STRING);
        String value = meta.getPersistentDataContainer().get(bookKey, PersistentDataType.STRING);
        if (action == null) return;
        if (action.equals("back")) { plugin.getShopManager().openMain(player); return; }
        if (action.equals("back-list")) { player.closeInventory(); open(player); return; }
        if (action.equals("page")) {
            String page = meta.getPersistentDataContainer().get(bookKey, PersistentDataType.STRING);
            try { open(player, Integer.parseInt(page)); } catch (NumberFormatException ignored) { open(player); }
            return;
        }
        if (action.equals("select") && key != null) confirm(player, key);
        else if (action.equals("buy") && key != null) buy(player, key);
    }

    public void clearCooldowns(UUID playerId) {
        data.set("cooldowns." + playerId, null);
        save();
    }

    private void resetStockIfNeeded() {
        String today = java.time.LocalDate.now().toString();
        if (today.equals(data.getString("stock-date", ""))) return;
        data.set("stock", null);
        data.set("stock-date", today);
        for (String key : KEYS) data.set("stock." + key, stockLimit(key));
        save();
    }

    private int stock(String key) {
        resetStockIfNeeded();
        return data.getInt("stock." + key, stockLimit(key));
    }

    private boolean isLimited(String key) {
        return List.of("mending", "unbreaking", "efficiency", "fortune", "silk_touch",
                "sharpness", "protection", "feather_falling", "looting", "power", "wind_burst",
                "swift_sneak", "soul_speed", "frost_walker", "breach", "density").contains(key);
    }

    private int stockLimit(String key) {
        return Math.max(0, plugin.getConfig().getInt(isLimited(key)
                ? "book-shop.rare-daily-stock" : "book-shop.normal-daily-stock", isLimited(key) ? 3 : 100));
    }

    private double price(String key) {
        return plugin.getConfig().getDouble("book-shop.prices." + key, defaultPrice(key));
    }

    private double defaultPrice(String key) {
        return switch (key) {
            case "mending" -> 1200;
            case "wind_burst" -> 1500;
            case "lunge" -> 600;
            case "swift_sneak", "soul_speed", "frost_walker" -> 700;
            case "fortune", "silk_touch", "unbreaking", "efficiency", "looting" -> 600;
            case "sharpness", "protection", "feather_falling", "power", "breach", "density" -> 500;
            case "infinity", "riptide", "channeling", "thorns", "sweeping_edge" -> 450;
            default -> 250;
        };
    }

    private Enchantment enchantment(String key) {
        try { return Enchantment.getByKey(NamespacedKey.minecraft(key)); }
        catch (IllegalArgumentException ignored) { return null; }
    }

    private ItemStack createBook(String key) {
        ItemStack item = new ItemStack(Material.ENCHANTED_BOOK);
        Enchantment enchant = enchantment(key);
        if (enchant != null && item.getItemMeta() instanceof EnchantmentStorageMeta meta) {
            meta.addStoredEnchant(enchant, enchant.getMaxLevel(), true);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void add(Inventory inv, int slot, Material material, String name, String action, String value) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.WHITE + name);
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
        meta.getPersistentDataContainer().set(bookKey, PersistentDataType.STRING, value);
        item.setItemMeta(meta);
        inv.setItem(slot, item);
    }

    private String displayName(String key) {
        String[] words = key.split("_");
        StringBuilder out = new StringBuilder();
        for (String word : words) {
            if (out.length() > 0) out.append(' ');
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }

    private String roman(int level) {
        if (level <= 0) return "";
        String[] numerals = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return level < numerals.length ? numerals[level] : Integer.toString(level);
    }

    private String remaining(long until) {
        long seconds = Math.max(0, (until - System.currentTimeMillis()) / 1000L);
        return (seconds / 3600) + "h " + ((seconds % 3600) / 60) + "m";
    }

    private void save() {
        try { data.save(file); }
        catch (IOException e) { plugin.getLogger().severe("Could not save books.yml: " + e.getMessage()); }
    }
}
