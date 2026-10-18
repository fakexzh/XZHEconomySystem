package com.xzh.economy;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Persistent, paid recovery of the most recent death inventory. */
public final class DeathRecoveryManager implements Listener {
    private static final long WINDOW_MILLIS = 30L * 60L * 1000L;
    private static final int DAILY_LIMIT = 2;
    private final XZHEconomySystem plugin;
    private final File file;
    private final YamlConfiguration data;
    private final NamespacedKey actionKey;

    public DeathRecoveryManager(XZHEconomySystem plugin) {
        this.plugin = plugin;
        file = new File(plugin.getDataFolder(), "death-recovery.yml");
        data = YamlConfiguration.loadConfiguration(file);
        actionKey = new NamespacedKey(plugin, "death_recovery_action");
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (plugin.getConfig().getBoolean("death-recovery.overworld-only", true)
                && player.getWorld().getEnvironment() != org.bukkit.World.Environment.NORMAL) {
            event.setKeepInventory(true);
            event.setKeepLevel(true);
            event.getDrops().clear();
            event.setDroppedExp(0);
            return;
        }
        if (event.getKeepInventory()) return;
        UUID id = player.getUniqueId();
        List<ItemStack> items = new ArrayList<>();
        for (ItemStack drop : event.getDrops()) {
            if (drop != null && !drop.getType().isAir()) items.add(drop.clone());
        }
        int level = player.getLevel();
        float progress = player.getExp();
        int totalXp = Math.max(0, player.getTotalExperience());
        event.getDrops().clear();
        event.setDroppedExp(0);

        double configuredXpValue = plugin.getConfig().getDouble("death-recovery.xp-value-per-point", 0.25);
        double xpValue = Double.isFinite(configuredXpValue) && configuredXpValue >= 0
                ? Math.min(configuredXpValue, 1000.0) : 0.25;
        double lostValue = Math.max(0.0, valueOf(items) + totalXp * xpValue);
        String base = id.toString();
        String priorDailyDate = data.getString(base + ".daily-date", "");
        int priorDailyCount = data.getInt(base + ".daily-count", 0);
        data.set(base, null);
        data.set(base + ".daily-date", priorDailyDate);
        data.set(base + ".daily-count", priorDailyCount);
        if (!items.isEmpty() || totalXp > 0) {
            data.set(base + ".items", items);
            data.set(base + ".level", level);
            data.set(base + ".exp-progress", progress);
            data.set(base + ".total-xp", totalXp);
            data.set(base + ".value", round(lostValue));
            data.set(base + ".expires-at", System.currentTimeMillis() + WINDOW_MILLIS);
            data.set(base + ".recovered", false);
            double penalty = Math.min(1000.0, Math.max(10.0, lostValue * 0.10));
            penalty = Math.min(penalty, plugin.getBalance(id));
            plugin.setBalance(id, plugin.getBalance(id) - penalty);
            plugin.tellPublic(player, "Death penalty: " + money(penalty) + " coins. Use /deathinventory within 30 minutes to recover your lost items and XP for a fee.");
        } else {
            plugin.tellPublic(player, "You lost no recoverable items or XP.");
        }
        save();
    }

    public void adminClearRecovery(UUID id) {
        data.set(id.toString(), null);
        save();
    }

    public void open(Player player) {
        String base = player.getUniqueId().toString();
        if (!hasRecovery(base)) {
            plugin.tellPublic(player, "You have no recoverable death inventory.");
            return;
        }
        long expires = data.getLong(base + ".expires-at", 0L);
        if (System.currentTimeMillis() >= expires) {
            String dailyDate = data.getString(base + ".daily-date", "");
            int dailyCount = data.getInt(base + ".daily-count", 0);
            data.set(base, null);
            data.set(base + ".daily-date", dailyDate);
            data.set(base + ".daily-count", dailyCount);
            save();
            plugin.tellPublic(player, "Your 30-minute recovery window has expired.");
            return;
        }
        LocalDate today = LocalDate.now();
        String storedDay = data.getString(base + ".daily-date", "");
        int used = storedDay.equals(today.toString()) ? data.getInt(base + ".daily-count", 0) : 0;
        if (used >= DAILY_LIMIT) {
            plugin.tellPublic(player, "You have reached the limit of 2 death recoveries today.");
            return;
        }
        double value = recoveryValue(base);
        double fee = round(value * safeFeeMultiplier());
        int count = readItems(base).stream().mapToInt(ItemStack::getAmount).sum();
        int xp = data.getInt(base + ".total-xp", 0);
        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.DARK_RED + "Death Inventory Recovery");
        add(inv, 13, Material.CHEST, ChatColor.YELLOW + "Recover last death", "recover",
                List.of(ChatColor.WHITE + "Items: " + count,
                        ChatColor.WHITE + "XP points: " + xp,
                        ChatColor.WHITE + "Estimated lost value: " + money(value) + " coins",
                        ChatColor.WHITE + "Recovery fee: " + ChatColor.RED + money(fee) + " coins",
                        ChatColor.GRAY + "Window expires in " + remaining(expires),
                        ChatColor.GRAY + "Daily limit: 2 recoveries"));
        add(inv, 11, Material.LIME_CONCRETE, ChatColor.GREEN + "Confirm recovery", "confirm",
                List.of(ChatColor.WHITE + "Pay " + money(fee) + " coins"));
        add(inv, 15, Material.RED_CONCRETE, ChatColor.RED + "Cancel", "cancel", List.of());
        player.openInventory(inv);
    }

    private void recover(Player player) {
        String base = player.getUniqueId().toString();
        if (!hasRecovery(base) || System.currentTimeMillis() >= data.getLong(base + ".expires-at", 0L)) {
            plugin.tellPublic(player, "Your death inventory is no longer available.");
            data.set(base, null);
            save();
            return;
        }
        LocalDate today = LocalDate.now();
        String storedDay = data.getString(base + ".daily-date", "");
        int used = storedDay.equals(today.toString()) ? data.getInt(base + ".daily-count", 0) : 0;
        if (used >= DAILY_LIMIT) {
            plugin.tellPublic(player, "You have reached the limit of 2 death recoveries today.");
            return;
        }
        double value = recoveryValue(base);
        double fee = round(value * safeFeeMultiplier());
        double balance = plugin.getBalance(player.getUniqueId());
        if (balance < fee) {
            plugin.tellPublic(player, "Recovery costs " + money(fee) + " coins; your balance is " + money(balance) + ".");
            return;
        }

        List<ItemStack> items = readItems(base);
        int level = data.getInt(base + ".level", 0);
        float progress = (float) data.getDouble(base + ".exp-progress", 0.0);
        int totalXp = data.getInt(base + ".total-xp", 0);
        // Mark consumed and persist before delivery to prevent double recovery after a restart.
        data.set(base + ".recovered", true);
        data.set(base + ".items", null);
        data.set(base + ".total-xp", 0);
        data.set(base + ".level", 0);
        data.set(base + ".exp-progress", 0.0);
        data.set(base + ".daily-date", today.toString());
        data.set(base + ".daily-count", used + 1);
        save();
        plugin.setBalance(player.getUniqueId(), balance - fee);
        for (ItemStack item : items) {
            if (item == null || item.getType().isAir()) continue;
            for (ItemStack leftover : player.getInventory().addItem(item.clone()).values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
        }
        player.setTotalExperience(0);
        player.setLevel(level);
        player.setExp(Math.max(0.0f, Math.min(1.0f, progress)));
        player.setTotalExperience(totalXp);
        data.set(base, null);
        data.set(base + ".daily-date", today.toString());
        data.set(base + ".daily-count", used + 1);
        save();
        plugin.tellPublic(player, "Death inventory recovered for " + money(fee) + " coins.");
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = ChatColor.stripColor(event.getView().getTitle());
        if (!"Death Inventory Recovery".equals(title)) return;
        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        String action = clicked.getItemMeta().getPersistentDataContainer()
                .get(actionKey, PersistentDataType.STRING);
        if ("confirm".equals(action)) {
            player.closeInventory();
            recover(player);
        } else if ("cancel".equals(action)) {
            player.closeInventory();
        }
    }

    private boolean hasRecovery(String base) {
        return !data.getBoolean(base + ".recovered", false)
                && (data.getList(base + ".items") != null && !data.getList(base + ".items").isEmpty()
                || data.getInt(base + ".total-xp", 0) > 0);
    }

    @SuppressWarnings("unchecked")
    private List<ItemStack> readItems(String base) {
        List<?> raw = data.getList(base + ".items", List.of());
        List<ItemStack> items = new ArrayList<>();
        for (Object entry : raw) if (entry instanceof ItemStack stack) items.add(stack.clone());
        return items;
    }

    private double valueOf(List<ItemStack> items) {
        double total = 0;
        for (ItemStack item : items) {
            String path = "death-recovery.material-values." + item.getType().name().toLowerCase(Locale.ROOT);
            double configured = plugin.getConfig().getDouble(path, defaultValue(item.getType()));
            double unit = Double.isFinite(configured) && configured >= 0
                    ? Math.min(configured, 1.0E9) : defaultValue(item.getType());
            total += unit * item.getAmount();
            if (item.hasItemMeta() && item.getItemMeta().hasEnchants()) total += item.getAmount() * 10.0 * item.getItemMeta().getEnchants().size();
            if (item.getItemMeta() instanceof org.bukkit.inventory.meta.EnchantmentStorageMeta stored && stored.hasStoredEnchants()) {
                for (org.bukkit.enchantments.Enchantment enchant : stored.getStoredEnchants().keySet()) {
                    total += item.getAmount() * bookValue(enchant.getKey().getKey());
                }
            }
        }
        return Double.isFinite(total) ? round(Math.min(total, 1.0E12)) : 1.0E12;
    }

    private double safeFeeMultiplier() {
        double configured = plugin.getConfig().getDouble("death-recovery.fee-multiplier", 1.5);
        return Double.isFinite(configured) && configured >= 1.0 ? Math.min(configured, 10.0) : 1.5;
    }

    private double recoveryValue(String base) {
        double value = data.getDouble(base + ".value", 0.0);
        return Double.isFinite(value) && value >= 0.0 ? Math.min(value, 1.0E12) : 0.0;
    }

    private double defaultValue(Material m) {
        String n = m.name();
        if (n.equals("ELYTRA")) return 2500;
        if (n.equals("ENCHANTED_GOLDEN_APPLE")) return 1250;
        if (n.equals("GOLDEN_APPLE")) return 90;
        if (n.equals("DIAMOND_BLOCK")) return 720;
        if (n.equals("IRON_BLOCK")) return 180;
        if (n.equals("GOLD_BLOCK")) return 72;
        if (n.equals("NETHERITE_INGOT")) return 600;
        if (n.equals("NETHERITE_SCRAP")) return 150;
        if (n.equals("DIAMOND")) return 125;
        if (n.equals("IRON_INGOT")) return 12;
        if (n.equals("GOLD_INGOT")) return 20;
        if (n.equals("EMERALD")) return 20;
        if (n.equals("ANCIENT_DEBRIS")) return 120;
        if (n.contains("ARMOR_TRIM_SMITHING_TEMPLATE"))
            return n.contains("SILENCE") ? 1000 : (n.contains("FLOW") || n.contains("BOLT") ? 600 : 300);
        if (n.contains("SPAWN_EGG")) return n.contains("SNIFFER") ? 2000 : 1000;
        if (n.contains("ZOMBIE_HEAD") || n.contains("CREEPER_HEAD") || n.contains("SKELETON_SKULL")) return 200;
        if (n.contains("WITHER_SKELETON_SKULL")) return 500;
        if (n.contains("ENCHANTED_BOOK")) return 100;
        if (n.contains("NETHERITE")) return 1200;
        if (n.contains("DIAMOND")) return 300;
        if (n.contains("IRON")) return 75;
        if (n.contains("TRIDENT") || n.contains("TOTEM") || n.contains("HEAVY_CORE") || n.equals("MACE")) return 750;
        if (n.contains("ENDER_PEARL") || n.contains("BLAZE_ROD")) return 10;
        if (n.contains("FOOD") || n.contains("BEEF") || n.contains("PORKCHOP") || n.contains("BREAD")) return 2;
        if (m.isBlock()) return 1;
        return 3;
    }

    private double bookValue(String key) {
        return switch (key) {
            case "mending" -> 1200;
            case "wind_burst" -> 1500;
            case "swift_sneak", "soul_speed", "frost_walker" -> 700;
            case "fortune", "silk_touch", "unbreaking", "efficiency", "looting" -> 600;
            case "sharpness", "protection", "feather_falling", "power", "breach", "density", "lunge" -> 500;
            default -> 150;
        };
    }

    private void add(Inventory inv, int slot, Material material, String title, String action, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(title);
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
        item.setItemMeta(meta);
        inv.setItem(slot, item);
    }

    private double round(double value) { return Math.round(value * 100.0) / 100.0; }
    private String money(double value) { return String.format(Locale.US, "%.2f", value).replaceAll("\\.00$", ""); }
    private String remaining(long expires) {
        long ms = Math.max(0, expires - System.currentTimeMillis());
        return (ms / 60_000L) + "m";
    }
    private void save() {
        try { data.save(file); }
        catch (IOException e) { plugin.getLogger().severe("Could not save death-recovery.yml: " + e.getMessage()); }
    }
}
