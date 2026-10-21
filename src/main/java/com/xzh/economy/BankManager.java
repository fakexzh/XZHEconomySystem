package com.xzh.economy;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
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

/** Separate persistent player bank with opening hours controlled by Minecraft world time. */
public final class BankManager implements Listener {
    private final XZHEconomySystem plugin;
    private final File file;
    private final YamlConfiguration data;
    private final NamespacedKey actionKey;
    private final NamespacedKey amountKey;
    private Boolean previousOpenState;
    private boolean openingWarned;
    private boolean closingWarned;

    public BankManager(XZHEconomySystem plugin) {
        this.plugin = plugin;
        file = new File(plugin.getDataFolder(), "bank-balances.yml");
        data = YamlConfiguration.loadConfiguration(file);
        actionKey = new NamespacedKey(plugin, "bank_action");
        amountKey = new NamespacedKey(plugin, "bank_amount");
        Bukkit.getPluginManager().registerEvents(this, plugin);
        previousOpenState = isOpenNow();
        Bukkit.getScheduler().runTaskTimer(plugin, this::announceSchedule, 20L, 20L);
    }

    public double getBalance(UUID id) {
        return Math.max(0.0, Math.round(data.getDouble(id.toString(), 0.0)));
    }

    public void setBalance(UUID id, double amount) {
        if (!Double.isFinite(amount) || amount < 0) return;
        data.set(id.toString(), Math.round(amount));
        save();
    }

    public boolean isOpenNow() {
        World world = getClockWorld();
        if (world == null) return false;
        int openHour = Math.max(0, Math.min(23, plugin.getConfig().getInt("bank.open-hour", 9)));
        int closeHour = Math.max(1, Math.min(24, plugin.getConfig().getInt("bank.close-hour", 20)));
        long time = Math.floorMod(world.getTime(), 24000L);
        long openTick = Math.floorMod((openHour - 6L) * 1000L, 24000L);
        long closeTick = Math.floorMod((closeHour - 6L) * 1000L, 24000L);
        return openTick < closeTick ? time >= openTick && time < closeTick : time >= openTick || time < closeTick;
    }

    private void announceSchedule() {
        if (!plugin.getConfig().getBoolean("announcements.bank.enabled", true)) return;
        World world = getClockWorld();
        if (world == null) return;
        boolean open = isOpenNow();
        if (previousOpenState != null && previousOpenState != open) {
            Bukkit.broadcastMessage(open
                    ? ChatColor.GOLD + "[XZH BANK] " + ChatColor.GREEN + "The bank is now OPEN."
                    : ChatColor.GOLD + "[XZH BANK] " + ChatColor.RED + "The bank is now CLOSED.");
            if (open) { openingWarned = false; closingWarned = false; }
        }
        previousOpenState = open;
        long now = Math.floorMod(world.getTime(), 24000L);
        long openTick = Math.floorMod((plugin.getConfig().getInt("bank.open-hour", 9) - 6L) * 1000L, 24000L);
        long closeTick = Math.floorMod((plugin.getConfig().getInt("bank.close-hour", 20) - 6L) * 1000L, 24000L);
        if (open) {
            double seconds = secondsUntil(now, closeTick);
            if (!closingWarned && seconds > 0 && seconds <= 300) {
                closingWarned = true;
                Bukkit.broadcastMessage(ChatColor.GOLD + "[XZH BANK] " + ChatColor.YELLOW + "The bank will close in about 5 minutes.");
            }
        } else {
            double seconds = secondsUntil(now, openTick);
            if (!openingWarned && seconds > 0 && seconds <= 300) {
                openingWarned = true;
                Bukkit.broadcastMessage(ChatColor.GOLD + "[XZH BANK] " + ChatColor.YELLOW + "The bank will open in about 5 minutes.");
            }
        }
    }

    private double secondsUntil(long from, long target) {
        long distance = Math.floorMod(target - from, 24000L);
        double seconds = 0.0;
        long cursor = from;
        while (distance > 0) {
            boolean day = cursor < 12000L;
            long boundary = day ? 12000L : 24000L;
            long span = Math.min(distance, boundary - cursor);
            // Vanilla daylight cycling: each 12,000-tick half-day lasts 10 real minutes.
            seconds += span * (600.0 / 12000.0);
            distance -= span;
            cursor += span;
            if (cursor >= 24000L) cursor = 0L;
        }
        return seconds;
    }

    public String hoursMessage() {
        World world = getClockWorld();
        String worldName = world == null ? "server world" : world.getName();
        return "Bank hours are " + plugin.getConfig().getInt("bank.open-hour", 9) + ":00–"
                + plugin.getConfig().getInt("bank.close-hour", 20) + ":00 Minecraft time (" + worldName + ").";
    }

    private World getClockWorld() {
        String name = plugin.getConfig().getString("bank.clock-world", "world");
        World world = name == null ? null : Bukkit.getWorld(name);
        if (world != null) return world;
        return Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
    }

    public void open(Player player) {
        UUID id = player.getUniqueId();
        boolean open = isOpenNow();
        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.GOLD + "XZH BANK");
        add(inv, 4, Material.GOLD_INGOT, ChatColor.YELLOW + "Your balances", "refresh", 0,
                List.of(ChatColor.WHITE + "Pocket: " + ChatColor.YELLOW + money(plugin.getBalance(id)) + " coins",
                        ChatColor.WHITE + "Bank: " + ChatColor.GREEN + money(getBalance(id)) + " coins",
                        ChatColor.GRAY + (open ? "Bank is OPEN" : "Bank is CLOSED"),
                        ChatColor.GRAY + hoursMessage()));

        add(inv, 10, Material.CHEST, ChatColor.GREEN + "Deposit 10", "deposit", 10,
                List.of(ChatColor.GRAY + "Move 10 pocket coins into your bank."));
        add(inv, 11, Material.ENDER_CHEST, ChatColor.GREEN + "Deposit 100", "deposit", 100,
                List.of(ChatColor.GRAY + "Move 100 pocket coins into your bank."));
        add(inv, 12, Material.BARREL, ChatColor.GREEN + "Deposit all", "deposit-all", 0,
                List.of(ChatColor.GRAY + "Deposit your entire pocket balance."));

        add(inv, 14, Material.GOLD_NUGGET, ChatColor.AQUA + "Withdraw 10", "withdraw", 10,
                List.of(ChatColor.GRAY + "Move 10 bank coins into your pocket."));
        add(inv, 15, Material.GOLD_INGOT, ChatColor.AQUA + "Withdraw 100", "withdraw", 100,
                List.of(ChatColor.GRAY + "Move 100 bank coins into your pocket."));
        add(inv, 16, Material.GOLD_BLOCK, ChatColor.AQUA + "Withdraw 500", "withdraw", 500,
                List.of(ChatColor.GRAY + "Move 500 bank coins into your pocket."));
        add(inv, 17, Material.CHEST_MINECART, ChatColor.AQUA + "Withdraw all", "withdraw-all", 0,
                List.of(ChatColor.GRAY + "Withdraw your entire bank balance."));

        add(inv, 22, Material.WRITABLE_BOOK, ChatColor.LIGHT_PURPLE + "Loans", "loans", 0,
                List.of(ChatColor.GRAY + "Borrow, review your debt, or repay.",
                        ChatColor.GRAY + "Loan repayments can use pocket and bank funds."));
        add(inv, 26, Material.BARRIER, ChatColor.RED + "Close", "close", 0, List.of());
        player.openInventory(inv);
    }

    private void add(Inventory inv, int slot, Material material, String name, String action,
                     int amount, List<String> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
        meta.getPersistentDataContainer().set(amountKey, PersistentDataType.INTEGER, amount);
        stack.setItemMeta(meta);
        inv.setItem(slot, stack);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = ChatColor.stripColor(event.getView().getTitle());
        if (!"XZH BANK".equals(title)) return;
        event.setCancelled(true);
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) return;
        ItemStack item = event.getCurrentItem();
        if (item == null || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        String action = meta.getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
        Integer amount = meta.getPersistentDataContainer().get(amountKey, PersistentDataType.INTEGER);
        if (action == null) return;
        int value = amount == null ? 0 : amount;
        UUID id = player.getUniqueId();
        switch (action) {
            case "refresh" -> open(player);
            case "deposit", "deposit-all", "withdraw", "withdraw-all" -> {
                if (!isOpenNow()) {
                    plugin.tellPublic(player, "The bank is closed. " + hoursMessage());
                    open(player);
                    return;
                }
                boolean deposit = action.startsWith("deposit");
                double pocket = plugin.getBalance(id);
                double bank = getBalance(id);
                double requested = switch (action) {
                    case "deposit-all" -> pocket;
                    case "withdraw-all" -> Math.min(bank, Math.max(0.0, plugin.getPocketBalanceLimit() - pocket));
                    default -> value;
                };
                if (!deposit && requested > Math.max(0.0, plugin.getPocketBalanceLimit() - pocket)) {
                    plugin.tellPublic(player, "Your pocket wallet is at its limit. Deposit coins before withdrawing more.");
                    open(player);
                    return;
                }
                if (!Double.isFinite(requested) || requested <= 0) {
                    plugin.tellPublic(player, deposit ? "You have no pocket money to deposit." : "You have no bank funds to withdraw.");
                    open(player);
                    return;
                }
                if (deposit) {
                    if (pocket + 1e-9 < requested) {
                        plugin.tellPublic(player, "Not enough pocket money. Your pocket balance is " + money(pocket) + " coins.");
                    } else {
                        plugin.setBalance(id, pocket - requested);
                        setBalance(id, bank + requested);
                        plugin.tellPublic(player, "Deposited " + money(requested) + " coins into your bank.");
                    }
                } else {
                    if (bank + 1e-9 < requested) {
                        plugin.tellPublic(player, "Not enough bank funds. Your bank balance is " + money(bank) + " coins.");
                    } else {
                        setBalance(id, bank - requested);
                        plugin.setBalance(id, pocket + requested);
                        plugin.tellPublic(player, "Withdrew " + money(requested) + " coins from your bank.");
                    }
                }
                open(player);
            }
            case "loans" -> plugin.getLoanManager().open(player);
            case "close" -> player.closeInventory();
            default -> { }
        }
    }

    private String money(double n) {
        return String.format(Locale.US, "%.2f", n).replaceAll("\\.00$", "");
    }

    private void save() {
        try {
            file.getParentFile().mkdirs();
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save bank-balances.yml: " + e.getMessage());
        }
    }
}
