package com.xzh.economy;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.time.LocalDate;

/** Persisted starter quest-pack system with progress HUD and shared pack stock. */
public final class QuestManager implements Listener {
    private record Pack(String id, String name, Material icon, int cost, int reward, String target, int goal, int stock) {}
    private final XZHEconomySystem plugin;
    private final File file;
    private final YamlConfiguration data;
    private final NamespacedKey actionKey;
    private final NamespacedKey valueKey;
    private final java.util.Map<UUID, Double> travelRemainders = new java.util.HashMap<>();
    private final List<Pack> packs = List.of(
        new Pack("beginner", "Beginner", Material.WHEAT, 25, 40, "break", 16, 50),
        new Pack("miner", "Miner", Material.IRON_PICKAXE, 75, 120, "mine", 32, 30),
        new Pack("hunter", "Hunter", Material.BOW, 100, 160, "kill", 10, 25),
        new Pack("explorer", "Explorer", Material.COMPASS, 200, 350, "travel", 500, 15),
        new Pack("nether", "Nether", Material.NETHERRACK, 300, 500, "nether-kill", 10, 10),
        new Pack("end", "End", Material.END_STONE, 750, 1250, "end-kill", 10, 5)
    );

    public QuestManager(XZHEconomySystem plugin) {
        this.plugin = plugin;
        file = new File(plugin.getDataFolder(), "quests.yml");
        data = YamlConfiguration.loadConfiguration(file);
        actionKey = new NamespacedKey(plugin, "quest_action");
        valueKey = new NamespacedKey(plugin, "quest_value");
        Bukkit.getPluginManager().registerEvents(this, plugin);
        String today = LocalDate.now().toString();
        if (!today.equals(data.getString("stock-reset-day", ""))) {
            for (Pack pack : packs) data.set("stock." + pack.id(), pack.stock());
            data.set("stock-reset-day", today);
        } else {
            for (Pack pack : packs) if (!data.contains("stock." + pack.id())) data.set("stock." + pack.id(), pack.stock());
        }
        save();
        resetEventIfNeeded();
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            resetPackStockIfNeeded();
            resetEventIfNeeded();
        }, 20L * 60L, 20L * 60L);
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) updateHud(player);
        }, 20L, 20L * 5L);
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (data.getString(player.getUniqueId() + ".active", "").isBlank()) continue;
                player.sendActionBar(ChatColor.GOLD + "Quest: " + ChatColor.YELLOW
                        + data.getString(player.getUniqueId() + ".name", "Active quest")
                        + ChatColor.GRAY + " — " + progressText(player.getUniqueId()));
            }
        }, 20L * 60L * 30L, 20L * 60L * 30L);
    }

    public void adminResetEvent() {
        data.set("event", null);
        data.set("event.date", LocalDate.now().toString());
        data.set("event.progress", 0);
        data.set("event.goal", 500);
        data.set("event.reward", 100);
        data.set("event.claims-remaining", 25);
        save();
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, ChatColor.DARK_PURPLE + "XZH Quest Packs");
        int slot = 0;
        for (Pack pack : packs) {
            ItemStack item = new ItemStack(pack.icon());
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(ChatColor.YELLOW + pack.name() + " Quest Pack");
            meta.setLore(List.of(
                    ChatColor.WHITE + "Cost: " + ChatColor.YELLOW + pack.cost() + " coins",
                    ChatColor.WHITE + "Reward: " + ChatColor.GREEN + pack.reward() + " coins",
                    ChatColor.WHITE + "Objective: " + ChatColor.AQUA + describe(pack),
                    ChatColor.GRAY + "Global stock: " + data.getInt("stock." + pack.id()),
                    ChatColor.GRAY + "Click to buy (confirmation follows)"
            ));
            meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "select");
            meta.getPersistentDataContainer().set(valueKey, PersistentDataType.STRING, pack.id());
            item.setItemMeta(meta);
            inv.setItem(slot++, item);
        }
        add(inv, 22, Material.NETHER_STAR, ChatColor.LIGHT_PURPLE + "Global Event Quest", "event-open", "global");
        add(inv, 49, Material.BARRIER, ChatColor.RED + "Close", "close", "");
        player.openInventory(inv);
    }

    private void confirm(Player player, Pack pack) {
        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.DARK_RED + "Confirm quest pack");
        ItemStack item = new ItemStack(pack.icon());
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.YELLOW + pack.name() + " Quest Pack");
        meta.setLore(List.of(ChatColor.WHITE + "Pay: " + pack.cost() + " coins",
                ChatColor.WHITE + "Reward on completion: " + pack.reward() + " coins",
                ChatColor.WHITE + "Objective: " + describe(pack),
                ChatColor.GRAY + "Global stock is consumed on purchase and is not refunded."));
        item.setItemMeta(meta);
        inv.setItem(13, item);
        add(inv, 11, Material.LIME_CONCRETE, ChatColor.GREEN + "Confirm purchase", "buy", pack.id());
        add(inv, 15, Material.RED_CONCRETE, ChatColor.RED + "Cancel", "back", "");
        player.openInventory(inv);
    }

    private void buy(Player player, Pack pack) {
        UUID id = player.getUniqueId();
        if (plugin.getLoanManager().isShopPurchaseRestricted(id)) {
            plugin.tellPublic(player, "Your loan is overdue. You can keep earning quest rewards, but new quest packs are locked until repayment.");
            return;
        }
        if (!data.getString(id + ".active", "").isBlank()) {
            plugin.tellPublic(player, "Finish or abandon your active quest before buying another pack.");
            return;
        }
        int stock = data.getInt("stock." + pack.id(), pack.stock());
        if (stock <= 0) { plugin.tellPublic(player, "That quest pack is out of global stock."); return; }
        double balance = plugin.getBalance(id);
        if (balance < pack.cost()) { plugin.tellPublic(player, "Not enough coins for this quest pack."); return; }
        plugin.setBalance(id, balance - pack.cost());
        data.set("stock." + pack.id(), stock - 1);
        data.set(id + ".active", pack.id());
        data.set(id + ".name", pack.name());
        data.set(id + ".progress", 0);
        data.set(id + ".goal", pack.goal());
        data.set(id + ".reward", pack.reward());
        data.set(id + ".target", pack.target());
        data.set(id + ".started-at", System.currentTimeMillis());
        save();
        updateHud(player);
        plugin.tellPublic(player, "Quest started: " + pack.name() + ". " + describe(pack));
        player.closeInventory();
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        if (event.isCancelled()) return;
        Player player = event.getPlayer();
        String target = data.getString(player.getUniqueId() + ".target", "");
        boolean ore = isOre(event.getBlock().getType());
        if (ore) recordEventOre(player);
        if (target.equals("break") || target.equals("mine") && ore) increment(player, 1);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (event.isCancelled() || event.getTo() == null) return;
        if (event.getFrom().getWorld() != event.getTo().getWorld()) return;
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        if (!"travel".equals(data.getString(id + ".target", ""))) {
            travelRemainders.remove(id);
            return;
        }
        double accumulated = travelRemainders.getOrDefault(id, 0.0) + event.getFrom().distance(event.getTo());
        int distance = (int) Math.floor(accumulated);
        travelRemainders.put(id, accumulated - distance);
        if (distance > 0) increment(player, distance);
    }

    @EventHandler
    public void onKill(EntityDeathEvent event) {
        Player player = event.getEntity().getKiller();
        if (player == null) return;
        String target = data.getString(player.getUniqueId() + ".target", "");
        if (target.equals("kill")) increment(player, 1);
        else if (target.equals("nether-kill") && event.getEntity().getWorld().getEnvironment() == org.bukkit.World.Environment.NETHER) increment(player, 1);
        else if (target.equals("end-kill") && event.getEntity().getWorld().getEnvironment() == org.bukkit.World.Environment.THE_END) increment(player, 1);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = ChatColor.stripColor(event.getView().getTitle());
        if (title == null || !(title.equals("XZH Quest Packs") || title.equals("Confirm quest pack") || title.equals("XZH Global Event"))) return;
        event.setCancelled(true);
        ItemStack item = event.getCurrentItem();
        if (item == null || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        String action = meta.getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
        String value = meta.getPersistentDataContainer().get(valueKey, PersistentDataType.STRING);
        if (action == null) return;
        if (action.equals("close")) { player.closeInventory(); return; }
        if (action.equals("back")) { open(player); return; }
        if (action.equals("event-open")) { openEvent(player); return; }
        if (action.equals("event-claim")) { claimEvent(player); return; }
        Pack pack = packs.stream().filter(p -> p.id().equals(value)).findFirst().orElse(null);
        if (pack == null) return;
        if (action.equals("select")) confirm(player, pack);
        else if (action.equals("buy")) buy(player, pack);
    }


    private void resetPackStockIfNeeded() {
        String today = LocalDate.now().toString();
        if (today.equals(data.getString("stock-reset-day", ""))) return;
        for (Pack pack : packs) data.set("stock." + pack.id(), pack.stock());
        data.set("stock-reset-day", today);
        save();
    }

    private void resetEventIfNeeded() {
        String today = LocalDate.now().toString();
        if (!today.equals(data.getString("event.date", ""))) {
            data.set("event", null);
            data.set("event.date", today);
            data.set("event.progress", 0);
            data.set("event.goal", 500);
            data.set("event.reward", 100);
            data.set("event.claims-remaining", 25);
            save();
        }
    }

    private void recordEventOre(Player player) {
        resetEventIfNeeded();
        int goal = data.getInt("event.goal", 500);
        int progress = Math.min(goal, data.getInt("event.progress", 0) + 1);
        data.set("event.progress", progress);
        String contributionPath = "event.contribution." + player.getUniqueId();
        data.set(contributionPath, data.getInt(contributionPath, 0) + 1);
        save();
    }

    private void openEvent(Player player) {
        resetEventIfNeeded();
        String contributionPath = "event.contribution." + player.getUniqueId();
        boolean alreadyClaimed = LocalDate.now().toString().equals(
                data.getString("event.claimed." + player.getUniqueId(), ""));
        int progress = data.getInt("event.progress", 0);
        int goal = data.getInt("event.goal", 500);
        int contribution = data.getInt(contributionPath, 0);
        int remaining = data.getInt("event.claims-remaining", 25);
        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.DARK_PURPLE + "XZH Global Event");
        add(inv, 13, Material.NETHER_STAR, ChatColor.LIGHT_PURPLE + "Community Ore Rush", "event-info", "global",
                List.of(ChatColor.WHITE + "Global progress: " + progress + "/" + goal + " ores",
                        ChatColor.WHITE + "Your contribution: " + contribution + " ores",
                        ChatColor.WHITE + "Reward: " + data.getInt("event.reward", 100) + " coins",
                        ChatColor.WHITE + "Rewards remaining: " + remaining,
                        ChatColor.GRAY + "Mine ores to contribute. Need 10 personal ores and global completion."));
        add(inv, 11, Material.LIME_CONCRETE, ChatColor.GREEN + "Claim event reward", "event-claim", "global",
                List.of(ChatColor.WHITE + "Requires event complete and 10+ personal ores",
                        ChatColor.GRAY + (alreadyClaimed ? "Already claimed today" : "One claim per player per day")));
        add(inv, 15, Material.ARROW, ChatColor.WHITE + "Back", "back", "");
        player.openInventory(inv);
    }

    private void claimEvent(Player player) {
        resetEventIfNeeded();
        UUID id = player.getUniqueId();
        String today = LocalDate.now().toString();
        if (today.equals(data.getString("event.claimed." + id, ""))) {
            plugin.tellPublic(player, "You have already claimed today's global event reward.");
            return;
        }
        if (data.getInt("event.progress", 0) < data.getInt("event.goal", 500)) {
            plugin.tellPublic(player, "The global event objective is not complete yet.");
            return;
        }
        if (data.getInt("event.contribution." + id, 0) < 10) {
            plugin.tellPublic(player, "Contribute at least 10 ores before claiming the event reward.");
            return;
        }
        int remaining = data.getInt("event.claims-remaining", 25);
        if (remaining <= 0) {
            plugin.tellPublic(player, "All global event rewards have been claimed today.");
            return;
        }
        int reward = data.getInt("event.reward", 100);
        data.set("event.claimed." + id, today);
        data.set("event.claims-remaining", remaining - 1);
        save();
        plugin.setBalance(id, plugin.getBalance(id) + reward);
        plugin.tellPublic(player, "Global event reward claimed: " + reward + " coins.");
        openEvent(player);
    }

    private void increment(Player player, int amount) {
        UUID id = player.getUniqueId();
        String active = data.getString(id + ".active", "");
        if (active.isBlank()) return;
        int progress = data.getInt(id + ".progress", 0) + amount;
        int goal = data.getInt(id + ".goal", 1);
        if (progress >= goal) {
            int reward = data.getInt(id + ".reward", 0);
            plugin.setBalance(id, plugin.getBalance(id) + reward);
            plugin.tellPublic(player, "Quest completed! Reward: " + reward + " coins.");
            travelRemainders.remove(id);
            data.set(id.toString(), null);
            plugin.getHudManager().hideActiveQuest(player);
        } else {
            data.set(id + ".progress", progress);
            updateHud(player);
        }
        save();
    }

    private void updateHud(Player player) {
        UUID id = player.getUniqueId();
        String active = data.getString(id + ".active", "");
        if (active.isBlank()) { plugin.getHudManager().hideActiveQuest(player); return; }
        int progress = data.getInt(id + ".progress", 0);
        int goal = Math.max(1, data.getInt(id + ".goal", 1));
        plugin.getHudManager().showActiveQuest(player, data.getString(id + ".name", active),
                progress + "/" + goal + " " + data.getString(id + ".target", "objective"),
                Math.min(1.0, (double) progress / goal));
    }

    private String progressText(UUID id) {
        return data.getInt(id + ".progress", 0) + "/" + Math.max(1, data.getInt(id + ".goal", 1));
    }
    private String describe(Pack p) {
        return switch (p.target()) {
            case "break" -> "Break " + p.goal() + " blocks";
            case "mine" -> "Mine " + p.goal() + " ore blocks";
            case "kill" -> "Defeat " + p.goal() + " mobs";
            case "nether-kill" -> "Defeat " + p.goal() + " mobs in the Nether";
            case "end-kill" -> "Defeat " + p.goal() + " mobs in the End";
            default -> "Travel " + p.goal() + " blocks";
        };
    }
    private boolean isOre(Material m) {
        return m.name().endsWith("_ORE") || m == Material.ANCIENT_DEBRIS;
    }
    private void add(Inventory inv, int slot, Material material, String name, String action, String value) {
        add(inv, slot, material, name, action, value, List.of());
    }

    private void add(Inventory inv, int slot, Material material, String name, String action, String value, List<String> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
        meta.getPersistentDataContainer().set(valueKey, PersistentDataType.STRING, value);
        stack.setItemMeta(meta);
        inv.setItem(slot, stack);
    }
    private void save() {
        try { data.save(file); } catch (IOException e) { plugin.getLogger().severe("Could not save quests.yml: " + e.getMessage()); }
    }
}
