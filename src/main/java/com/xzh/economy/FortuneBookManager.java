package com.xzh.economy;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Handles the first Fortune Book and keeps its challenge state separate from normal apples. */
public final class FortuneBookManager implements Listener {
    private static final String APPLE_ID = "book_of_apple";
    private static final long TICKS_PER_SECOND = 20L;

    private final XZHEconomySystem plugin;
    private final File file;
    private final YamlConfiguration data;
    private final NamespacedKey appleKey;
    private final NamespacedKey bookKey;
    private final Map<UUID, BukkitTask> activeTasks = new HashMap<>();

    public FortuneBookManager(XZHEconomySystem plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "fortune-books.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
        this.appleKey = new NamespacedKey(plugin, "mysterious_apple");
        this.bookKey = new NamespacedKey(plugin, "fortune_book_id");
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTask(plugin, this::resumeOnlineChallenges);
    }

    public int getApplePrice() {
        return Math.max(1, plugin.getConfig().getInt("fortune-books.apple.price", 222));
    }

    public int getAppleReward() {
        return Math.max(0, plugin.getConfig().getInt("fortune-books.apple.reward", 1222));
    }

    public int getAppleWinChancePercent() {
        double chance = getAppleWinChancePercent() / 100.0;
        return (int) Math.round(chance * 100.0);
    }

    public int getAppleLoseChancePercent() {
        return 100 - getAppleWinChancePercent();
    }

    public String getAppleOddsDescription() {
        return getAppleWinChancePercent() + "% reward / " + getAppleLoseChancePercent() + "% death";
    }

    public int getAppleCooldownHours() {
        return Math.max(1, plugin.getConfig().getInt("fortune-books.apple.cooldown-hours", 3));
    }

    public String getAppleCooldownStatus(UUID playerId) {
        long until = data.getLong("cooldowns." + playerId + "." + APPLE_ID, 0L);
        return until > System.currentTimeMillis()
                ? ChatColor.RED + remaining(until)
                : ChatColor.GREEN + "Available";
    }

    public void purchaseApple(Player player) {
        UUID id = player.getUniqueId();
        if (plugin.getLoanManager() != null && plugin.getLoanManager().isShopPurchaseRestricted(id)) {
            plugin.tellFortune(player, "Your loan is overdue. Fortune Book purchases are locked until you repay it.");
            return;
        }
        if (hasPendingChallenge(id)) {
            plugin.tellFortune(player, "Your current fate has not been decided yet.");
            return;
        }

        long now = System.currentTimeMillis();
        String cooldownPath = "cooldowns." + id + "." + APPLE_ID;
        long until = data.getLong(cooldownPath, 0L);
        if (until > now) {
            plugin.tellFortune(player, "You can purchase The Book of Apple again in " + remaining(until) + ".");
            return;
        }

        if (emptyStorageSlots(player.getInventory()) < 2) {
            plugin.tellFortune(player, "Your inventory is too full. Make space for the book and the Mysterious Apple (2 empty slots required).");
            return;
        }

        int price = getApplePrice();
        if (plugin.getCombinedBalance(id) < price) {
            plugin.tellFortune(player, "You need " + price + " coins to purchase The Book of Apple.");
            return;
        }

        ItemStack book = createBook();
        ItemStack apple = createApple();
        if (!plugin.spendAvailableFunds(id, price)) {
            plugin.tellFortune(player, "Your balance changed before the purchase could be completed. No items were delivered.");
            return;
        }

        data.set(cooldownPath, now + getAppleCooldownHours() * 60L * 60L * 1000L);
        save();
        player.getInventory().addItem(book, apple);
        plugin.tellFortune(player, "Purchased The Book of Apple for " + price + " coins. The Mysterious Apple is now in your inventory.");
        Bukkit.broadcastMessage("§8[§5fortune§8] §e" + player.getName()
                + " §fpurchased §dThe Book of Apple§f. Will they dare to eat the apple?");
        player.closeInventory();
    }

    private ItemStack createBook() {
        ItemStack item = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) item.getItemMeta();
        meta.setTitle("The Book of Apple");
        meta.setAuthor("Unknown");
        meta.setPages(List.of(
                "A gift has been placed in your hands.\n\nA Mysterious Apple.\n\nEat it, and let fate decide your future.",
                "A " + getAppleWinChancePercent() + "% chance to receive " + getAppleReward()
                        + " coins in your bank.\n\nA " + getAppleLoseChancePercent()
                        + "% chance to meet your end.\n\nNo refunds. No second chances.\n\n— Unknown"
        ));
        meta.getPersistentDataContainer().set(bookKey, PersistentDataType.STRING, APPLE_ID);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createApple() {
        ItemStack item = new ItemStack(Material.APPLE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.RED + "Mysterious Apple");
        meta.setLore(List.of(
                ChatColor.DARK_PURPLE + "A strange enchantment surrounds this apple.",
                ChatColor.GRAY + "The Book of Apple holds its secret."
        ));
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        meta.getPersistentDataContainer().set(appleKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    private boolean isMysteriousApple(ItemStack item) {
        if (item == null || item.getType() != Material.APPLE || !item.hasItemMeta()) return false;
        Byte marker = item.getItemMeta().getPersistentDataContainer().get(appleKey, PersistentDataType.BYTE);
        return marker != null && marker == (byte) 1;
    }

    private int emptyStorageSlots(PlayerInventory inventory) {
        int empty = 0;
        for (ItemStack item : inventory.getStorageContents()) {
            if (item == null || item.getType().isAir()) empty++;
        }
        return empty;
    }

    private boolean hasPendingChallenge(UUID id) {
        return data.contains("pending." + id + ".outcome");
    }

    @EventHandler
    public void onAppleConsumed(PlayerItemConsumeEvent event) {
        if (!isMysteriousApple(event.getItem())) return;
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        if (hasPendingChallenge(id)) {
            event.setCancelled(true);
            plugin.tellFortune(player, "Your current fate is still being decided. You cannot eat another Mysterious Apple yet.");
            return;
        }

        double chance = Math.max(0.0, Math.min(1.0, plugin.getConfig().getDouble("fortune-books.apple.win-chance", 0.5)));
        String outcome = ThreadLocalRandom.current().nextDouble() < chance ? "WIN" : "LOSE";
        String path = "pending." + id;
        data.set(path + ".outcome", outcome);
        data.set(path + ".remaining", 5);
        data.set(path + ".death-started", false);
        data.set(path + ".death-triggered", false);
        save();

        Bukkit.broadcastMessage("§8[§5fortune§8] §e" + player.getName()
                + " §fhas eaten the §dMysterious Apple§f. Fate will be decided in five seconds.");
        player.playSound(player.getLocation(), Sound.AMBIENT_CAVE, 0.8f, 0.65f);
        startCountdown(player, 5);
    }

    private void startCountdown(Player player, int startAt) {
        UUID id = player.getUniqueId();
        if (activeTasks.containsKey(id)) return;
        if (startAt <= 0) {
            resolveOutcome(player);
            return;
        }

        BukkitRunnable runnable = new BukkitRunnable() {
            private int number = startAt;

            @Override
            public void run() {
                Player online = Bukkit.getPlayer(id);
                if (online == null || !online.isOnline()) {
                    data.set("pending." + id + ".remaining", number);
                    save();
                    activeTasks.remove(id);
                    cancel();
                    return;
                }
                if (number > 0) {
                    online.sendTitle(ChatColor.RED + Integer.toString(number),
                            ChatColor.DARK_GRAY + "The apple has chosen...", 0, 18, 2);
                    online.playSound(online.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.9f,
                            Math.max(0.5f, 0.65f + (5 - number) * 0.12f));
                    number--;
                    data.set("pending." + id + ".remaining", number);
                    save();
                    return;
                }
                activeTasks.remove(id);
                cancel();
                resolveOutcome(online);
            }
        };
        activeTasks.put(id, runnable.runTaskTimer(plugin, 0L, TICKS_PER_SECOND));
    }

    private void resolveOutcome(Player player) {
        UUID id = player.getUniqueId();
        String path = "pending." + id;
        String outcome = data.getString(path + ".outcome", "");
        if (outcome.equals("WIN")) {
            int reward = getAppleReward();
            double newBankBalance = plugin.getBankManager().getBalance(id) + reward;
            plugin.getBankManager().setBalance(id, newBankBalance);
            data.set(path, null);
            save();
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.15f);
            player.sendTitle(ChatColor.GREEN + "FORTUNE!", ChatColor.YELLOW + "+" + reward + " coins added to your bank", 10, 55, 15);
            Bukkit.broadcastMessage("§8[§5fortune§8] §e" + player.getName()
                    + " §fdefied fate and won §6" + reward + " coins§f! The money has been deposited into their bank.");
            return;
        }
        if (outcome.equals("LOSE")) beginLoss(player);
    }

    private void beginLoss(Player player) {
        UUID id = player.getUniqueId();
        String path = "pending." + id;
        if (player.isDead()) {
            data.set(path, null);
            save();
            return;
        }
        if (activeTasks.containsKey(id)) return;
        data.set(path + ".death-started", true);
        data.set(path + ".death-triggered", false);
        save();

        player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 60, 1, true, false, true));
        player.playSound(player.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1.0f, 0.8f);
        player.sendTitle(ChatColor.DARK_RED + "FATE HAS CHOSEN", ChatColor.RED + "There is no second chance.", 5, 45, 10);
        Bukkit.broadcastMessage("§8[§5fortune§8] §e" + player.getName()
                + " §fdrew the losing fate. Their fortune has run out.");

        BukkitRunnable runnable = new BukkitRunnable() {
            @Override
            public void run() {
                Player online = Bukkit.getPlayer(id);
                if (online == null || !online.isOnline()) {
                    data.set(path + ".death-started", false);
                    save();
                    activeTasks.remove(id);
                    cancel();
                    return;
                }
                data.set(path + ".death-triggered", true);
                save();
                activeTasks.remove(id);
                cancel();
                online.setHealth(0.0);
            }
        };
        activeTasks.put(id, runnable.runTaskLater(plugin, 60L));
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        if (!hasPendingChallenge(id)) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline() || !hasPendingChallenge(id)) return;
            String path = "pending." + id;
            String outcome = data.getString(path + ".outcome", "");
            if (outcome.equals("LOSE") && data.getInt(path + ".remaining", 0) <= 0) {
                beginLoss(player);
            } else {
                startCountdown(player, data.getInt(path + ".remaining", 5));
            }
        }, 1L);
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        UUID id = event.getEntity().getUniqueId();
        String path = "pending." + id;
        if (data.getString(path + ".outcome", "").equals("LOSE")) {
            boolean deathWasTriggeredByFortune = data.getBoolean(path + ".death-triggered", false);
            data.set(path, null);
            save();
            BukkitTask task = activeTasks.remove(id);
            if (task != null) task.cancel();
            if (!deathWasTriggeredByFortune) {
                Bukkit.broadcastMessage("§8[§5fortune§8] §e" + event.getEntity().getName()
                        + " §fwas claimed by fate before the countdown could finish.");
            }
        } else if (data.getBoolean(path + ".death-triggered", false)) {
            data.set(path, null);
            save();
        }
    }

    private void resumeOnlineChallenges() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID id = player.getUniqueId();
            if (!hasPendingChallenge(id)) continue;
            String path = "pending." + id;
            String outcome = data.getString(path + ".outcome", "");
            if (outcome.equals("LOSE") && data.getInt(path + ".remaining", 0) <= 0) {
                beginLoss(player);
            } else {
                startCountdown(player, data.getInt(path + ".remaining", 5));
            }
        }
    }

    private String remaining(long until) {
        long seconds = Math.max(0L, (until - System.currentTimeMillis() + 999L) / 1000L);
        long hours = seconds / 3600L;
        long minutes = (seconds % 3600L + 59L) / 60L;
        if (hours > 0) return hours + "h " + minutes + "m";
        if (minutes > 0) return minutes + "m";
        return "less than a minute";
    }

    private void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save fortune-books.yml: " + e.getMessage());
        }
    }

    public void shutdown() {
        for (BukkitTask task : activeTasks.values()) task.cancel();
        activeTasks.clear();
        save();
    }
}
