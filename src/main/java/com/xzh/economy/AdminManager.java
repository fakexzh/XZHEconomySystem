package com.xzh.economy;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AdminManager implements Listener {
    private final XZHEconomySystem plugin;
    private final NamespacedKey actionKey;
    private final Map<UUID, String> prompts = new ConcurrentHashMap<>();
    private final File transactionsFile;

    public AdminManager(XZHEconomySystem plugin) {
        this.plugin = plugin;
        actionKey = new NamespacedKey(plugin, "admin_action");
        transactionsFile = new File(plugin.getDataFolder(), "transactions.log");
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player) {
        if (!player.hasPermission("xzh.admin")) {
            player.sendMessage(ChatColor.RED + "No permission.");
            return;
        }
        Inventory inv = Bukkit.createInventory(null, 54, ChatColor.DARK_RED + "XZH Admin");
        add(inv, 10, Material.GOLD_INGOT, "Set player balance", "balance",
                List.of("Set an online or previously known player's balance"));
        add(inv, 12, Material.CHEST, "Set global stock", "stock",
                List.of("Set current stock for a material"));
        add(inv, 14, Material.HOPPER, "Restock all shop items", "restock",
                List.of("Refill to configured global stock caps"));
        add(inv, 20, Material.CLOCK, "Reset book cooldowns", "book-cooldown", List.of("Clear a player's 24-hour book cooldowns"));
        add(inv, 22, Material.REDSTONE, "Reload config", "reload", List.of("Reload config.yml values"));
        add(inv, 24, Material.BARRIER, "Clear a player's loan", "clear-loan", List.of("Admin override: remove active debt"));
        add(inv, 26, Material.CHEST, "Clear death recovery", "clear-recovery", List.of("Admin override: discard recovery snapshot"));
        add(inv, 28, Material.TNT, "Reset global event", "reset-event", List.of("Reset shared event progress and reward stock"));
        add(inv, 18, Material.EMERALD, "Set item price", "price",
                List.of("Configure buy or sell price for a material"));
        add(inv, 16, Material.BOOK, "Recent transactions", "transactions",
                List.of("Show the last 10 balance changes"));
        add(inv, 30, Material.WRITABLE_BOOK, "Loans overview", "loans", List.of("View active loan count and total owed"));
        add(inv, 32, Material.NETHER_STAR, "Quest/event status", "quests", List.of("View global event progress and pack stock"));
        add(inv, 34, Material.CHEST, "Death recovery status", "recovery", List.of("View active recovery snapshots"));
        add(inv, 36, Material.WRITABLE_BOOK, "Reset active quest", "clear-quest", List.of("Cancel a player's active quest without refunding its pack cost"));
        add(inv, 29, Material.MAP, "Diagnostics", "diagnostics",
                List.of("Show online count and persisted data files"));
        add(inv, 49, Material.BARRIER, "Close", "close", List.of());
        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!"XZH Admin".equals(ChatColor.stripColor(event.getView().getTitle()))) return;
        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        String action = clicked.getItemMeta().getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
        if (action == null) return;
        switch (action) {
            case "balance" -> prompt(player, "balance-player", "Type the player's exact name in chat.");
            case "stock" -> prompt(player, "stock-material", "Type a material name in chat (for example ELYTRA).");
            case "price" -> prompt(player, "price-material", "Type a material name in chat to configure its buy or sell price.");
            case "book-cooldown" -> prompt(player, "book-cooldown-player", "Type the player's exact name to reset book cooldowns.");
            case "reload" -> { plugin.reloadConfig(); player.sendMessage(ChatColor.GREEN + "config.yml reloaded."); player.closeInventory(); }
            case "clear-loan" -> prompt(player, "clear-loan-player", "Type the player's exact name to clear their loan (or cancel).");
            case "clear-recovery" -> prompt(player, "clear-recovery-player", "Type the player's exact name to discard their death recovery (or cancel).");
            case "clear-quest" -> prompt(player, "clear-quest-player", "Type the player's exact name to clear their active quest (or cancel).");
            case "reset-event" -> { plugin.getQuestManager().adminResetEvent(); player.sendMessage(ChatColor.GREEN + "Global event progress and stock reset."); player.closeInventory(); }
            case "restock" -> {
                plugin.getShopManager().restockAll();
                player.sendMessage(ChatColor.GREEN + "Global shop stock restocked.");
                player.closeInventory();
            }
            case "transactions" -> { showTransactions(player); player.closeInventory(); }
            case "loans" -> { loansOverview(player); player.closeInventory(); }
            case "quests" -> { questOverview(player); player.closeInventory(); }
            case "recovery" -> { recoveryOverview(player); player.closeInventory(); }
            case "diagnostics" -> { diagnostics(player); player.closeInventory(); }
            case "close" -> player.closeInventory();
            default -> { }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        prompts.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String prompt = prompts.get(player.getUniqueId());
        if (prompt == null) return;
        event.setCancelled(true);
        String input = event.getMessage().trim();
        Bukkit.getScheduler().runTask(plugin, () -> processPrompt(player, prompt, input));
    }

    private void processPrompt(Player player, String prompt, String input) {
        if (!player.isOnline()) { prompts.remove(player.getUniqueId()); return; }
        if (!player.hasPermission("xzh.admin")) { prompts.remove(player.getUniqueId()); return; }
        if (input.equalsIgnoreCase("cancel")) {
            prompts.remove(player.getUniqueId());
            player.sendMessage(ChatColor.YELLOW + "Admin action cancelled.");
            return;
        }
        if (prompt.equals("balance-player")) {
            if (!input.matches("[A-Za-z0-9_]{1,16}")) {
                player.sendMessage(ChatColor.RED + "Invalid player name. Type a Minecraft username or 'cancel'.");
                return;
            }
            prompts.put(player.getUniqueId(), "balance-amount:" + input);
            player.sendMessage(ChatColor.YELLOW + "Type the new balance amount for " + input + " (or 'cancel').");
            return;
        }
        if (prompt.startsWith("balance-amount:")) {
            String name = prompt.substring("balance-amount:".length());
            double amount;
            try { amount = Double.parseDouble(input); }
            catch (NumberFormatException ex) { player.sendMessage(ChatColor.RED + "Enter a valid amount or 'cancel'."); return; }
            if (!Double.isFinite(amount) || amount < 0 || amount > 1.0E12) {
                player.sendMessage(ChatColor.RED + "Amount must be between 0 and 1000000000000."); return;
            }
            OfflinePlayer target = Bukkit.getOfflinePlayer(name);
            plugin.setBalance(target.getUniqueId(), amount);
            prompts.remove(player.getUniqueId());
            player.sendMessage(ChatColor.GREEN + "Set " + name + "'s balance to " + amount + " coins.");
            return;
        }
        if (prompt.equals("clear-loan-player")) {
            if (!input.matches("[A-Za-z0-9_]{1,16}")) { player.sendMessage(ChatColor.RED + "Invalid player name. Try again or type cancel."); return; }
            OfflinePlayer target = Bukkit.getOfflinePlayer(input);
            plugin.getLoanManager().adminClearLoan(target.getUniqueId());
            prompts.remove(player.getUniqueId());
            player.sendMessage(ChatColor.GREEN + "Cleared active loan for " + input + ".");
            return;
        }
        if (prompt.equals("clear-recovery-player")) {
            if (!input.matches("[A-Za-z0-9_]{1,16}")) { player.sendMessage(ChatColor.RED + "Invalid player name. Try again or type cancel."); return; }
            OfflinePlayer target = Bukkit.getOfflinePlayer(input);
            plugin.getDeathRecoveryManager().adminClearRecovery(target.getUniqueId());
            prompts.remove(player.getUniqueId());
            player.sendMessage(ChatColor.GREEN + "Cleared death recovery for " + input + ".");
            return;
        }
        if (prompt.equals("clear-quest-player")) {
            if (!input.matches("[A-Za-z0-9_]{1,16}")) { player.sendMessage(ChatColor.RED + "Invalid player name. Try again or type cancel."); return; }
            OfflinePlayer target = Bukkit.getOfflinePlayer(input);
            plugin.getQuestManager().adminClearQuest(target.getUniqueId());
            prompts.remove(player.getUniqueId());
            player.sendMessage(ChatColor.GREEN + "Cleared active quest for " + input + ".");
            return;
        }
        if (prompt.equals("book-cooldown-player")) {
            if (!input.matches("[A-Za-z0-9_]{1,16}")) { player.sendMessage(ChatColor.RED + "Invalid player name. Try again or type cancel."); return; }
            OfflinePlayer target = Bukkit.getOfflinePlayer(input);
            plugin.getBookShopManager().clearCooldowns(target.getUniqueId());
            prompts.remove(player.getUniqueId());
            player.sendMessage(ChatColor.GREEN + "Cleared enchantment book cooldowns for " + input + ".");
            return;
        }
        if (prompt.equals("price-material")) {
            Material material;
            try { material = Material.valueOf(input.toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException ex) { player.sendMessage(ChatColor.RED + "Unknown material. Try again or type cancel."); return; }
            prompts.put(player.getUniqueId(), "price-kind:" + material.name());
            player.sendMessage(ChatColor.YELLOW + "Type buy or sell for " + material.name() + ", or cancel.");
            return;
        }
        if (prompt.startsWith("price-kind:")) {
            String material = prompt.substring("price-kind:".length());
            if (!input.equalsIgnoreCase("buy") && !input.equalsIgnoreCase("sell")) {
                player.sendMessage(ChatColor.RED + "Type buy or sell, or cancel."); return;
            }
            prompts.put(player.getUniqueId(), "price-amount:" + material + ":" + input.toLowerCase(Locale.ROOT));
            player.sendMessage(ChatColor.YELLOW + "Type the new " + input.toLowerCase(Locale.ROOT) + " price for " + material + " (0–1000000), or cancel.");
            return;
        }
        if (prompt.startsWith("price-amount:")) {
            String[] parts = prompt.split(":");
            double amount;
            try { amount = Double.parseDouble(input); }
            catch (NumberFormatException ex) { player.sendMessage(ChatColor.RED + "Enter a valid price or cancel."); return; }
            if (!Double.isFinite(amount) || amount < 0 || amount > 1_000_000) { player.sendMessage(ChatColor.RED + "Price must be between 0 and 1000000."); return; }
            Material material = Material.valueOf(parts[1]);
            plugin.getShopManager().setPrice(material, parts[2], amount);
            prompts.remove(player.getUniqueId());
            player.sendMessage(ChatColor.GREEN + "Set " + parts[2] + " price for " + material.name() + " to " + amount + " coins.");
            return;
        }
        if (prompt.equals("stock-material")) {
            Material material;
            try { material = Material.valueOf(input.toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException ex) { player.sendMessage(ChatColor.RED + "Unknown material. Try again or type 'cancel'."); return; }
            prompts.put(player.getUniqueId(), "stock-amount:" + material.name());
            player.sendMessage(ChatColor.YELLOW + "Type the current stock amount for " + material.name() + " (0–1000000), or 'cancel'.");
            return;
        }
        if (prompt.startsWith("stock-amount:")) {
            String name = prompt.substring("stock-amount:".length());
            int amount;
            try { amount = Integer.parseInt(input); }
            catch (NumberFormatException ex) { player.sendMessage(ChatColor.RED + "Enter a whole number or 'cancel'."); return; }
            if (amount < 0 || amount > 1_000_000) {
                player.sendMessage(ChatColor.RED + "Stock must be between 0 and 1000000."); return;
            }
            plugin.getShopManager().setStock(Material.valueOf(name), amount);
            prompts.remove(player.getUniqueId());
            player.sendMessage(ChatColor.GREEN + "Global stock for " + name + " set to " + amount + ".");
        }
    }

    private void prompt(Player player, String state, String text) {
        prompts.put(player.getUniqueId(), state);
        player.closeInventory();
        player.sendMessage(ChatColor.YELLOW + text + " Type 'cancel' to stop.");
    }

    private void showTransactions(Player player) {
        try {
            if (!transactionsFile.exists()) {
                player.sendMessage(ChatColor.GRAY + "No transaction log entries yet.");
                return;
            }
            List<String> lines = Files.readAllLines(transactionsFile.toPath());
            player.sendMessage(ChatColor.GOLD + "Recent balance changes:");
            int start = Math.max(0, lines.size() - 10);
            for (int i = start; i < lines.size(); i++) player.sendMessage(ChatColor.GRAY + lines.get(i));
        } catch (IOException ex) {
            player.sendMessage(ChatColor.RED + "Could not read transaction log.");
        }
    }


    private void loansOverview(Player player) {
        YamlConfiguration loans = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "loans.yml"));
        int count = 0;
        double total = 0;
        for (String key : loans.getKeys(false)) {
            double owed = loans.getDouble(key + ".amount", 0);
            if (owed > 0) { count++; total += owed; }
        }
        player.sendMessage(ChatColor.GOLD + "Loan overview: " + count + " active loans; total owed " + String.format(Locale.US, "%.2f", total) + " coins.");
    }

    private void questOverview(Player player) {
        YamlConfiguration quests = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "quests.yml"));
        player.sendMessage(ChatColor.GOLD + "Global event: " + quests.getInt("event.progress", 0) + "/"
                + quests.getInt("event.goal", 500) + " ores; reward claims remaining: "
                + quests.getInt("event.claims-remaining", 25) + ".");
        player.sendMessage(ChatColor.GRAY + "Pack stock — Beginner: " + quests.getInt("stock.beginner", 0)
                + ", Miner: " + quests.getInt("stock.miner", 0) + ", Hunter: " + quests.getInt("stock.hunter", 0)
                + ", Explorer: " + quests.getInt("stock.explorer", 0) + ", Nether: " + quests.getInt("stock.nether", 0)
                + ", End: " + quests.getInt("stock.end", 0) + ".");
    }

    private void recoveryOverview(Player player) {
        YamlConfiguration recovery = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "death-recovery.yml"));
        int active = 0;
        long now = System.currentTimeMillis();
        for (String key : recovery.getKeys(false)) {
            if (!key.matches("[0-9a-fA-F-]{36}")) continue;
            boolean hasItems = recovery.getList(key + ".items") != null && !recovery.getList(key + ".items").isEmpty();
            boolean hasXp = recovery.getInt(key + ".total-xp", 0) > 0;
            if (!recovery.getBoolean(key + ".recovered", false) && now < recovery.getLong(key + ".expires-at", 0) && (hasItems || hasXp)) active++;
        }
        player.sendMessage(ChatColor.GOLD + "Active death recovery snapshots: " + active + ".");
    }

    private void diagnostics(Player player) {
        player.sendMessage(ChatColor.GOLD + "XZHEconomySystem diagnostics");
        player.sendMessage(ChatColor.GRAY + "Online players: " + Bukkit.getOnlinePlayers().size());
        for (String name : List.of("balances.yml", "loans.yml", "quests.yml", "shop-stock.yml", "death-recovery.yml", "locations.yml", "transactions.log")) {
            File f = new File(plugin.getDataFolder(), name);
            player.sendMessage((f.exists() ? ChatColor.GREEN : ChatColor.RED) + name + ": " + (f.exists() ? f.length() + " bytes" : "not created yet"));
        }
    }

    private void add(Inventory inv, int slot, Material material, String name, String action, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.YELLOW + name);
        meta.setLore(lore.stream().map(line -> ChatColor.GRAY + line).toList());
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
        item.setItemMeta(meta);
        inv.setItem(slot, item);
    }
}
