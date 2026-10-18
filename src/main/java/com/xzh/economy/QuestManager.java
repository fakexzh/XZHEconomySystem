package com.xzh.economy;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.advancement.Advancement;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerAttemptPickupItemEvent;
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
import java.util.Random;
import java.util.UUID;

/** Random single quests, achievement-unlocked multi-objective packs, and shared global quests. */
public final class QuestManager implements Listener {
    private record Objective(String target, int goal, String description) {}
    private record Pack(String id, String name, Material icon, int cost, int reward, String advancement, List<Objective> objectives) {}
    private record RandomQuest(String name, Material icon, int cost, int reward, Objective objective) {}

    private final XZHEconomySystem plugin;
    private final File file;
    private final YamlConfiguration data;
    private final NamespacedKey actionKey;
    private final NamespacedKey valueKey;
    private final Random random = new Random();
    private final List<Double> travelRemainders = new ArrayList<>();

    private final List<Pack> packs = List.of(
        new Pack("nether_hunter", "Nether Trophy Hunt", Material.WITHER_SKELETON_SKULL, 400, 560,
            "minecraft:nether/root", List.of(
                new Objective("kill:WITHER_SKELETON", 20, "Kill 20 Wither Skeletons"),
                new Objective("collect:WITHER_SKELETON_SKULL", 2, "Collect 2 Wither Skeleton Skulls"))),
        new Pack("nether_blaze", "Fortress Conqueror", Material.BLAZE_ROD, 550, 825,
            "minecraft:nether/root", List.of(
                new Objective("kill:BLAZE", 25, "Kill 25 Blazes"),
                new Objective("collect:BLAZE_ROD", 48, "Collect 48 Blaze Rods"))),
        new Pack("end_expedition", "End Expedition", Material.END_STONE, 900, 1350,
            "minecraft:story/enter_the_end", List.of(
                new Objective("kill:ENDERMAN", 30, "Kill 30 Endermen"),
                new Objective("break:END_STONE", 128, "Mine 128 End Stone"),
                new Objective("collect:ENDER_PEARL", 32, "Collect 32 Ender Pearls")))
    );

    private final List<RandomQuest> randomPool = List.of(
        new RandomQuest("Deep Diamond Expedition", Material.DIAMOND, 180, 252,
            new Objective("break:DIAMOND_ORE", 12, "Mine 12 Diamond Ore")),
        new RandomQuest("Ancient Debris Search", Material.ANCIENT_DEBRIS, 260, 390,
            new Objective("break:ANCIENT_DEBRIS", 8, "Mine 8 Ancient Debris")),
        new RandomQuest("Raid the Fortress", Material.BLAZE_ROD, 220, 330,
            new Objective("kill:BLAZE", 18, "Kill 18 Blazes in the Nether")),
        new RandomQuest("Undead Patrol", Material.BOW, 150, 210,
            new Objective("kill:ZOMBIE", 35, "Kill 35 Zombies")),
        new RandomQuest("Ocean Treasure Hunt", Material.PRISMARINE_SHARD, 200, 300,
            new Objective("break:PRISMARINE", 96, "Mine 96 Prismarine blocks")),
        new RandomQuest("Ender Pearl Collection", Material.ENDER_PEARL, 240, 360,
            new Objective("collect:ENDER_PEARL", 24, "Collect 24 Ender Pearls")),
        new RandomQuest("Iron Supply Contract", Material.RAW_IRON, 130, 182,
            new Objective("break:IRON_ORE", 64, "Mine 64 Iron Ore")),
        new RandomQuest("Wither Skeleton Hunt", Material.WITHER_SKELETON_SKULL, 350, 525,
            new Objective("kill:WITHER_SKELETON", 16, "Kill 16 Wither Skeletons"))
    );

    public QuestManager(XZHEconomySystem plugin) {
        this.plugin = plugin;
        file = new File(plugin.getDataFolder(), "quests.yml");
        data = YamlConfiguration.loadConfiguration(file);
        actionKey = new NamespacedKey(plugin, "quest_action");
        valueKey = new NamespacedKey(plugin, "quest_value");
        Bukkit.getPluginManager().registerEvents(this, plugin);
        resetEventIfNeeded();
        Bukkit.getScheduler().runTaskTimer(plugin, this::resetEventIfNeeded, 20L * 60L, 20L * 60L);
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) updateHud(player);
        }, 20L, 20L * 5L);
        // Shared global reminder and progress broadcast every 15 minutes while the event is active.
        Bukkit.getScheduler().runTaskTimer(plugin, this::broadcastGlobalProgress, 20L * 60L * 15L, 20L * 60L * 15L);
        if (!data.contains("single-shop.next-refresh")) {
            data.set("single-shop.next-refresh", nextRefresh(System.currentTimeMillis()));
            data.set("single-shop.refresh-id", 0);
            save();
        }
    }

    public void adminClearQuest(UUID id) {
        data.set(id.toString(), null);
        Player player = Bukkit.getPlayer(id);
        if (player != null) plugin.getHudManager().hideActiveQuest(player);
        save();
    }

    public void adminResetEvent() {
        data.set("event", null);
        data.set("event.date", LocalDate.now().toString());
        data.set("event.progress", 0);
        data.set("event.goal", configuredEventGoal());
        data.set("event.reward", configuredEventReward());
        data.set("event.claims-remaining", configuredEventClaims());
        save();
    }

    public boolean setPackStock(String packId, int amount) {
        if (amount < 0 || amount > 1_000_000) return false;
        data.set("pack-stock." + packId.toLowerCase(Locale.ROOT), amount);
        save();
        return true;
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.DARK_PURPLE + "XZH Quests");
        add(inv, 10, Material.WRITABLE_BOOK, ChatColor.YELLOW + "Single Quests", "section-single", "single",
            List.of(ChatColor.GRAY + "Random challenge generated on every purchase",
                    ChatColor.GRAY + "Shop offer refreshes every 2 hours"));
        add(inv, 13, Material.ENCHANTED_BOOK, ChatColor.LIGHT_PURPLE + "Achievement Quest Packs", "section-packs", "packs",
            List.of(ChatColor.GRAY + "Hard multi-objective challenges",
                    ChatColor.GRAY + "Unlock packs by completing Minecraft advancements"));
        add(inv, 16, Material.NETHER_STAR, ChatColor.GOLD + "Global Quest", "section-global", "global",
            List.of(ChatColor.GRAY + "Everyone contributes to shared progress",
                    ChatColor.GRAY + "Server reminders every 15 minutes"));
        add(inv, 22, Material.BARRIER, ChatColor.RED + "Close", "close", "");
        player.openInventory(inv);
    }

    private void openSingle(Player player) {
        refreshSingleShopIfDue();
        long next = data.getLong("single-shop.next-refresh", nextRefresh(System.currentTimeMillis()));
        long mins = Math.max(0, (next - System.currentTimeMillis()) / 60000L);
        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.DARK_PURPLE + "XZH Single Quests");
        add(inv, 13, Material.WRITABLE_BOOK, ChatColor.YELLOW + "Buy Random Quest", "single-buy", "random",
            List.of(ChatColor.WHITE + "A new random quest is generated for every purchase",
                    ChatColor.WHITE + "Reward: 30%-50% profit based on difficulty",
                    ChatColor.GRAY + "Shop refresh in " + (mins / 60) + "h " + (mins % 60) + "m",
                    ChatColor.GRAY + "One active quest at a time"));
        add(inv, 22, Material.ARROW, ChatColor.WHITE + "Back", "back", "");
        player.openInventory(inv);
    }

    private void openPacks(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, ChatColor.DARK_PURPLE + "XZH Achievement Packs");
        int slot = 0;
        for (Pack pack : packs) {
            boolean unlocked = hasAdvancement(player, pack.advancement());
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.WHITE + "Cost: " + ChatColor.YELLOW + pack.cost() + " coins");
            lore.add(ChatColor.WHITE + "Reward: " + ChatColor.GREEN + pack.reward() + " coins");
            lore.add(ChatColor.WHITE + "Objectives:");
            for (Objective objective : pack.objectives()) lore.add(ChatColor.AQUA + "• " + objective.description());
            lore.add(unlocked ? ChatColor.GREEN + "UNLOCKED" : ChatColor.RED + "Locked: complete " + pack.advancement());
            lore.add(ChatColor.GRAY + "Fixed handcrafted pack; multi-objective");
            add(inv, slot++, unlocked ? pack.icon() : Material.BARRIER,
                (unlocked ? ChatColor.YELLOW : ChatColor.RED) + pack.name(), unlocked ? "pack-select" : "locked", pack.id(), lore);
        }
        add(inv, 49, Material.ARROW, ChatColor.WHITE + "Back", "back", "");
        player.openInventory(inv);
    }

    private void confirmPack(Player player, Pack pack) {
        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.DARK_RED + "Confirm quest pack");
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.WHITE + "Cost: " + pack.cost() + " coins");
        lore.add(ChatColor.WHITE + "Reward: " + pack.reward() + " coins");
        for (Objective objective : pack.objectives()) lore.add(ChatColor.AQUA + objective.description());
        add(inv, 13, pack.icon(), ChatColor.YELLOW + pack.name(), "pack-info", pack.id(), lore);
        add(inv, 11, Material.LIME_CONCRETE, ChatColor.GREEN + "Confirm purchase", "pack-buy", pack.id());
        add(inv, 15, Material.RED_CONCRETE, ChatColor.RED + "Cancel", "back-packs", "");
        player.openInventory(inv);
    }

    private void buyRandom(Player player) {
        UUID id = player.getUniqueId();
        if (plugin.getLoanManager().isShopPurchaseRestricted(id)) {
            plugin.tellPublic(player, "Your loan is overdue. Quest purchases are locked; sell eligible items and repay your debt.");
            return;
        }
        if (!data.getString(id + ".active", "").isBlank()) {
            plugin.tellPublic(player, "Finish your active quest before buying another.");
            return;
        }
        RandomQuest quest = randomPool.get(random.nextInt(randomPool.size()));
        // Reward margin is intentionally varied from 30% to 50%; objective pool avoids trivial tasks.
        int margin = 30 + random.nextInt(21);
        int reward = (int) Math.ceil(quest.cost() * (1.0 + margin / 100.0));
        double balance = plugin.getCombinedBalance(id);
        if (balance < quest.cost() || !plugin.spendAvailableFunds(id, quest.cost())) {
            plugin.tellPublic(player, "You need " + quest.cost() + " coins to buy this random quest.");
            return;
        }
        startQuest(player, "single:" + System.currentTimeMillis(), quest.name(), quest.cost(), reward, List.of(quest.objective()));
        data.set(id + ".profit-margin", margin);
        save();
        plugin.tellPublic(player, "Random quest generated: " + quest.name() + ". Complete it for " + reward + " coins (" + margin + "% profit).");
        player.closeInventory();
    }

    private void buyPack(Player player, Pack pack) {
        UUID id = player.getUniqueId();
        if (plugin.getLoanManager().isShopPurchaseRestricted(id)) {
            plugin.tellPublic(player, "Your loan is overdue. Quest-pack purchases are locked until full repayment.");
            return;
        }
        if (!hasAdvancement(player, pack.advancement())) {
            plugin.tellPublic(player, "Complete the required advancement to unlock this quest pack.");
            return;
        }
        if (!data.getString(id + ".active", "").isBlank()) {
            plugin.tellPublic(player, "Finish your active quest before buying another.");
            return;
        }
        if (plugin.getCombinedBalance(id) < pack.cost() || !plugin.spendAvailableFunds(id, pack.cost())) {
            plugin.tellPublic(player, "Not enough coins for this quest pack.");
            return;
        }
        startQuest(player, "pack:" + pack.id(), pack.name(), pack.cost(), pack.reward(), pack.objectives());
        player.closeInventory();
        plugin.tellPublic(player, "Quest pack started: " + pack.name() + ". Complete every objective to earn " + pack.reward() + " coins.");
    }

    private void startQuest(Player player, String id, String name, int cost, int reward, List<Objective> objectives) {
        UUID uuid = player.getUniqueId();
        data.set(uuid + ".active", id);
        data.set(uuid + ".name", name);
        data.set(uuid + ".cost", cost);
        data.set(uuid + ".reward", reward);
        data.set(uuid + ".objective-count", objectives.size());
        for (int i = 0; i < objectives.size(); i++) {
            Objective o = objectives.get(i);
            data.set(uuid + ".objectives." + i + ".target", o.target());
            data.set(uuid + ".objectives." + i + ".goal", o.goal());
            data.set(uuid + ".objectives." + i + ".description", o.description());
            data.set(uuid + ".objectives." + i + ".progress", 0);
        }
        data.set(uuid + ".started-at", System.currentTimeMillis());
        save();
        updateHud(player);
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        if (event.isCancelled()) return;
        Player p = event.getPlayer();
        if (p.getGameMode() == org.bukkit.GameMode.CREATIVE || p.getGameMode() == org.bukkit.GameMode.SPECTATOR) return;
        Material material = event.getBlock().getType();
        String id = material.name();
        incrementTarget(p, "break:" + id, 1);
        if (material.name().endsWith("_ORE") || material == Material.ANCIENT_DEBRIS) {
            incrementTarget(p, "break:ANY_ORE", 1);
            recordEventOre(p);
        }
    }

    @EventHandler
    public void onKill(EntityDeathEvent event) {
        Player p = event.getEntity().getKiller();
        if (p == null || p.getGameMode() == org.bukkit.GameMode.CREATIVE || p.getGameMode() == org.bukkit.GameMode.SPECTATOR) return;
        incrementTarget(p, "kill:" + event.getEntityType().name(), 1);
    }

    @EventHandler
    public void onPickup(PlayerAttemptPickupItemEvent event) {
        Player p = event.getPlayer();
        if (p.getGameMode() == org.bukkit.GameMode.CREATIVE || p.getGameMode() == org.bukkit.GameMode.SPECTATOR) return;
        Material material = event.getItem().getItemStack().getType();
        incrementTarget(p, "collect:" + material.name(), event.getItem().getItemStack().getAmount());
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (event.isCancelled() || event.getTo() == null || event.getFrom().getWorld() != event.getTo().getWorld()) return;
        Player p = event.getPlayer();
        if (p.getGameMode() == org.bukkit.GameMode.CREATIVE || p.getGameMode() == org.bukkit.GameMode.SPECTATOR) return;
        UUID id = p.getUniqueId();
        if (data.getString(id + ".active", "").isBlank()) return;
        double step = event.getFrom().distance(event.getTo());
        if (step > 10) return;
        if (matchesTarget(id, "travel")) incrementTarget(p, "travel", (int) Math.floor(step));
    }

    private void incrementTarget(Player player, String target, int amount) {
        if (amount <= 0) return;
        UUID id = player.getUniqueId();
        if (data.getString(id + ".active", "").isBlank()) return;
        int count = data.getInt(id + ".objective-count", 0);
        boolean changed = false;
        for (int i = 0; i < count; i++) {
            String base = id + ".objectives." + i;
            if (!target.equals(data.getString(base + ".target", ""))) continue;
            int goal = data.getInt(base + ".goal", 1);
            int progress = Math.min(goal, data.getInt(base + ".progress", 0) + amount);
            data.set(base + ".progress", progress);
            changed = true;
        }
        if (!changed) return;
        if (allComplete(id)) {
            int reward = data.getInt(id + ".reward", 0);
            plugin.setBalance(id, plugin.getBalance(id) + reward);
            plugin.tellPublic(player, "Quest completed! Reward: " + reward + " coins.");
            data.set(id.toString(), null);
            plugin.getHudManager().hideActiveQuest(player);
        } else updateHud(player);
        save();
    }

    private boolean matchesTarget(UUID id, String target) {
        int count = data.getInt(id + ".objective-count", 0);
        for (int i = 0; i < count; i++) if (target.equals(data.getString(id + ".objectives." + i + ".target", ""))) return true;
        return false;
    }

    private boolean allComplete(UUID id) {
        int count = data.getInt(id + ".objective-count", 0);
        if (count <= 0) return false;
        for (int i = 0; i < count; i++) {
            String base = id + ".objectives." + i;
            if (data.getInt(base + ".progress", 0) < data.getInt(base + ".goal", 1)) return false;
        }
        return true;
    }

    private void updateHud(Player player) {
        UUID id = player.getUniqueId();
        String active = data.getString(id + ".active", "");
        if (active.isBlank()) { plugin.getHudManager().hideActiveQuest(player); return; }
        int count = data.getInt(id + ".objective-count", 0);
        int done = 0, total = 0;
        List<String> descriptions = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String base = id + ".objectives." + i;
            int goal = Math.max(1, data.getInt(base + ".goal", 1));
            int progress = Math.min(goal, data.getInt(base + ".progress", 0));
            done += progress;
            total += goal;
            descriptions.add(data.getString(base + ".description", "Objective") + " " + progress + "/" + goal);
        }
        String display = String.join(" | ", descriptions);
        plugin.getHudManager().showActiveQuest(player, data.getString(id + ".name", "Active quest"),
                display, total <= 0 ? 0 : Math.min(1.0, (double) done / total));
    }

    private void openGlobal(Player player) {
        resetEventIfNeeded();
        UUID id = player.getUniqueId();
        int progress = data.getInt("event.progress", 0);
        int goal = data.getInt("event.goal", configuredEventGoal());
        int contribution = data.getInt("event.contribution." + id, 0);
        boolean claimed = LocalDate.now().toString().equals(data.getString("event.claimed." + id, ""));
        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.DARK_PURPLE + "XZH Global Quest");
        add(inv, 13, Material.NETHER_STAR, ChatColor.GOLD + "Community Ore Rush", "global-info", "global",
            List.of(ChatColor.WHITE + "Shared progress: " + progress + "/" + goal + " ores",
                    ChatColor.WHITE + "Your contribution: " + contribution,
                    ChatColor.WHITE + "Reward: " + data.getInt("event.reward", configuredEventReward()) + " coins",
                    ChatColor.GRAY + "Everyone can contribute; mine ores to help the server."));
        add(inv, 11, Material.LIME_CONCRETE, ChatColor.GREEN + "Claim reward", "global-claim", "global",
            List.of(ChatColor.WHITE + "Requires event completion and 10 personal ores",
                    ChatColor.GRAY + (claimed ? "Already claimed today" : "One reward per player per day")));
        add(inv, 15, Material.ARROW, ChatColor.WHITE + "Back", "back", "");
        player.openInventory(inv);
    }

    private void claimGlobal(Player player) {
        resetEventIfNeeded();
        UUID id = player.getUniqueId();
        String today = LocalDate.now().toString();
        if (today.equals(data.getString("event.claimed." + id, ""))) { plugin.tellPublic(player, "You already claimed today's global quest reward."); return; }
        if (data.getInt("event.progress", 0) < data.getInt("event.goal", configuredEventGoal())) { plugin.tellPublic(player, "The global quest is not complete yet."); return; }
        if (data.getInt("event.contribution." + id, 0) < 10) { plugin.tellPublic(player, "Contribute at least 10 ores before claiming."); return; }
        int remaining = data.getInt("event.claims-remaining", configuredEventClaims());
        if (remaining <= 0) { plugin.tellPublic(player, "All global quest rewards have been claimed."); return; }
        int reward = data.getInt("event.reward", configuredEventReward());
        data.set("event.claimed." + id, today);
        data.set("event.claims-remaining", remaining - 1);
        save();
        plugin.setBalance(id, plugin.getBalance(id) + reward);
        plugin.tellPublic(player, "Global quest reward claimed: " + reward + " coins.");
        openGlobal(player);
    }

    private void recordEventOre(Player player) {
        resetEventIfNeeded();
        int goal = data.getInt("event.goal", configuredEventGoal());
        int progress = Math.min(goal, data.getInt("event.progress", 0) + 1);
        data.set("event.progress", progress);
        UUID id = player.getUniqueId();
        String path = "event.contribution." + id;
        data.set(path, data.getInt(path, 0) + 1);
        save();
    }

    private void broadcastGlobalProgress() {
        resetEventIfNeeded();
        if (data.getInt("event.progress", 0) >= data.getInt("event.goal", configuredEventGoal())) return;
        String message = ChatColor.GOLD + "[XZH Global Quest] " + ChatColor.YELLOW
            + "Community Ore Rush: " + data.getInt("event.progress", 0) + "/"
            + data.getInt("event.goal", configuredEventGoal()) + " ores mined. Everyone can contribute!";
        Bukkit.broadcastMessage(message);
    }

    private void refreshSingleShopIfDue() {
        long now = System.currentTimeMillis();
        long next = data.getLong("single-shop.next-refresh", 0L);
        if (next <= 0 || now >= next) {
            data.set("single-shop.next-refresh", nextRefresh(now));
            data.set("single-shop.refresh-id", data.getInt("single-shop.refresh-id", 0) + 1);
            save();
            Bukkit.broadcastMessage(ChatColor.GOLD + "[XZH Quests] New random single-quest offers are available!");
        }
    }

    private long nextRefresh(long now) { return ((now / (2L * 60L * 60L * 1000L)) + 1L) * (2L * 60L * 60L * 1000L); }

    private boolean hasAdvancement(Player player, String rawKey) {
        NamespacedKey key = NamespacedKey.fromString(rawKey);
        if (key == null) return false;
        Advancement advancement = Bukkit.getAdvancement(key);
        if (advancement == null) return false;
        return player.getAdvancementProgress(advancement).isDone();
    }

    private void resetEventIfNeeded() {
        String today = LocalDate.now().toString();
        if (!today.equals(data.getString("event.date", ""))) {
            data.set("event", null);
            data.set("event.date", today);
            data.set("event.progress", 0);
            data.set("event.goal", configuredEventGoal());
            data.set("event.reward", configuredEventReward());
            data.set("event.claims-remaining", configuredEventClaims());
            save();
        } else {
            if (!data.contains("event.goal")) data.set("event.goal", configuredEventGoal());
            if (!data.contains("event.reward")) data.set("event.reward", configuredEventReward());
            if (!data.contains("event.claims-remaining")) data.set("event.claims-remaining", configuredEventClaims());
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = ChatColor.stripColor(event.getView().getTitle());
        if (title == null || !(title.equals("XZH Quests") || title.equals("XZH Single Quests")
                || title.equals("XZH Achievement Packs") || title.equals("Confirm quest pack")
                || title.equals("XZH Global Quest"))) return;
        event.setCancelled(true);
        ItemStack item = event.getCurrentItem();
        if (item == null || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        String action = meta.getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
        String value = meta.getPersistentDataContainer().get(valueKey, PersistentDataType.STRING);
        if (action == null) return;
        switch (action) {
            case "close" -> player.closeInventory();
            case "section-single" -> openSingle(player);
            case "section-packs" -> openPacks(player);
            case "section-global" -> openGlobal(player);
            case "single-buy" -> buyRandom(player);
            case "pack-select" -> packs.stream().filter(p -> p.id().equals(value)).findFirst().ifPresent(p -> confirmPack(player, p));
            case "pack-buy" -> packs.stream().filter(p -> p.id().equals(value)).findFirst().ifPresent(p -> buyPack(player, p));
            case "global-claim" -> claimGlobal(player);
            case "back" -> open(player);
            case "back-packs" -> openPacks(player);
            default -> { }
        }
    }

    private void add(Inventory inv, int slot, Material material, String name, String action, String value) {
        add(inv, slot, material, name, action, value, List.of());
    }

    private void add(Inventory inv, int slot, Material material, String name, String action, String value, List<String> lore) {
        if (slot >= inv.getSize()) return;
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
        meta.getPersistentDataContainer().set(valueKey, PersistentDataType.STRING, value);
        stack.setItemMeta(meta);
        inv.setItem(slot, stack);
    }

    private int configuredEventGoal() { return Math.max(1, Math.min(1_000_000, plugin.getConfig().getInt("quests.global-event.goal", 500))); }
    private int configuredEventReward() { return Math.max(0, Math.min(1_000_000, plugin.getConfig().getInt("quests.global-event.reward", 100))); }
    private int configuredEventClaims() { return Math.max(0, Math.min(1_000_000, plugin.getConfig().getInt("quests.global-event.claims-per-day", 25))); }

    private void save() {
        try { data.save(file); } catch (IOException e) { plugin.getLogger().severe("Could not save quests.yml: " + e.getMessage()); }
    }
}
