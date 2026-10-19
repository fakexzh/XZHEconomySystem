package com.xzh.economy;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Persistent single-active-loan system with a confirmation GUI and a 12-hour grace period. */
public final class LoanManager implements Listener {
    private static final int[] AMOUNTS = {50, 100, 300, 500, 1000};
    private static final int[] TERMS = {2, 5, 7, 10};
    private final XZHEconomySystem plugin;
    private final File file;
    private final YamlConfiguration data;
    private final NamespacedKey actionKey;
    private final NamespacedKey amountKey;
    private final NamespacedKey termKey;

    public LoanManager(XZHEconomySystem plugin) {
        this.plugin = plugin;
        file = new File(plugin.getDataFolder(), "loans.yml");
        data = YamlConfiguration.loadConfiguration(file);
        for (String key : data.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                double owed = data.getDouble(key + ".amount", 0.0);
                long due = data.getLong(key + ".due-at", 0L);
                long grace = data.getLong(key + ".grace-until", due + 12L * 60L * 60L * 1000L);
                if (owed > 0) plugin.getHudManager().setLoan(id, owed, due, grace);
            } catch (IllegalArgumentException ignored) { }
        }
        actionKey = new NamespacedKey(plugin, "loan_action");
        amountKey = new NamespacedKey(plugin, "loan_amount");
        termKey = new NamespacedKey(plugin, "loan_term");
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, this::processLoans, 20L, 20L * 60L);
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.DARK_AQUA + "XZH BANK: Loans");
        UUID id = player.getUniqueId();
        double owed = getOwed(id);
        if (owed > 0) {
            add(inv, 13, Material.WRITABLE_BOOK, ChatColor.YELLOW + "Active loan",
                    "status", 0, 0, List.of(
                    ChatColor.WHITE + "Owed: " + ChatColor.RED + money(owed) + " coins",
                    ChatColor.WHITE + "Status: " + ChatColor.AQUA + loanStatus(id),
                    ChatColor.GRAY + "Repay available funds or use /loan repay."));
            add(inv, 15, Material.GOLD_INGOT, ChatColor.GREEN + "Repay now", "repay", 0, 0, List.of(ChatColor.WHITE + "Repay available funds; full repayment clears the loan"));
            add(inv, 22, Material.ARROW, ChatColor.WHITE + "Close", "close", 0, 0, List.of());
        } else {
            int[] slots = {10, 11, 12, 13, 14};
            for (int i = 0; i < AMOUNTS.length; i++) {
                add(inv, slots[i], Material.GOLD_INGOT, ChatColor.YELLOW + "Borrow " + AMOUNTS[i],
                        "amount", AMOUNTS[i], 0,
                        List.of(ChatColor.GRAY + "Choose a repayment term next."));
            }
            add(inv, 22, Material.BARRIER, ChatColor.RED + "No loans are granted automatically",
                    "info", 0, 0, List.of(ChatColor.GRAY + "Choose an amount to continue."));
        }
        player.openInventory(inv);
    }

    private void openTerms(Player player, int amount) {
        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.DARK_AQUA + "XZH BANK: Choose loan term");
        int[] slots = {10, 12, 14, 16};
        for (int i = 0; i < TERMS.length; i++) {
            int days = TERMS[i];
            double rate = rateFor(days);
            double total = Math.ceil(amount * (1 + rate * days));
            add(inv, slots[i], Material.CLOCK, ChatColor.YELLOW.toString() + days + " days",
                    "term", amount, days,
                    List.of(ChatColor.WHITE + "Daily interest: " + (int)(rate * 100) + "%",
                            ChatColor.WHITE + "Total repayment: " + ChatColor.RED + money(total) + " coins",
                            ChatColor.GRAY + "Click to review and confirm."));
        }
        add(inv, 22, Material.ARROW, ChatColor.WHITE + "Back", "back", amount, 0, List.of());
        player.openInventory(inv);
    }

    private void openConfirm(Player player, int amount, int days) {
        double total = Math.ceil(amount * (1 + rateFor(days) * days));
        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.DARK_RED + "XZH BANK: Confirm loan");
        add(inv, 13, Material.GOLD_INGOT, ChatColor.YELLOW + "Borrow " + amount + " coins",
                "info", amount, days, List.of(
                ChatColor.WHITE + "Term: " + days + " days",
                ChatColor.WHITE + "Total repayment: " + ChatColor.RED + money(total),
                ChatColor.GRAY + "12-hour grace period after due time."));
        add(inv, 11, Material.LIME_CONCRETE, ChatColor.GREEN + "XZH BANK: Confirm loan", "confirm", amount, days, List.of());
        add(inv, 15, Material.RED_CONCRETE, ChatColor.RED + "Cancel", "cancel", 0, 0, List.of());
        player.openInventory(inv);
    }

    public boolean repay(Player player) {
        UUID id = player.getUniqueId();
        double owed = getOwed(id);
        if (owed <= 0) {
            plugin.tellPublic(player, "You do not have an active loan.");
            return true;
        }
        double balance = plugin.getCombinedBalance(id);
        double payment = Math.min(balance, owed);
        if (payment <= 0.0) {
            plugin.tellPublic(player, "You have no available pocket or bank funds to repay this loan yet.");
            return true;
        }
        if (!plugin.spendAvailableFunds(id, payment)) {
            plugin.tellPublic(player, "Repayment failed because your available funds changed. Please try again.");
            return true;
        }
        double remaining = Math.max(0.0, Math.round((owed - payment) * 100.0) / 100.0);
        if (remaining <= 0.0) {
            data.set(id.toString(), null);
            save();
            plugin.getHudManager().setLoan(id, 0, 0);
            plugin.tellPublic(player, "Loan fully repaid: " + money(payment) + " coins.");
            plugin.announceLoanRepaid(player.getName(), payment, false);
        } else {
            data.set(id + ".amount", remaining);
            save();
            plugin.getHudManager().setLoan(id, remaining, getDue(id), data.getLong(id + ".grace-until", getDue(id)));
            plugin.tellPublic(player, "Partial loan repayment: " + money(payment) + " coins. Remaining debt: " + money(remaining) + " coins. Shop purchases stay locked if your loan is overdue.");
        }
        return true;
    }

    private void grant(Player player, int amount, int days) {
        UUID id = player.getUniqueId();
        if (getOwed(id) > 0) {
            plugin.tellPublic(player, "You already have an active loan.");
            return;
        }
        double principal = amount;
        double total = Math.ceil(principal * (1 + rateFor(days) * days));
        long dueAt = System.currentTimeMillis() + days * 24L * 60L * 60L * 1000L;
        data.set(id + ".principal", principal);
        data.set(id + ".amount", total);
        data.set(id + ".term-days", days);
        data.set(id + ".due-at", dueAt);
        data.set(id + ".grace-until", dueAt + 12L * 60L * 60L * 1000L);
        data.set(id + ".overdue", false);
        save();
        plugin.setBalance(id, plugin.getBalance(id) + principal);
        plugin.getHudManager().setLoan(id, total, dueAt, dueAt + 12L * 60L * 60L * 1000L);
        plugin.tellPublic(player, "Loan approved: +" + amount + " coins. Repay " + money(total) + " within " + days + " days.");
        plugin.announceLoanBorrow(player, amount, total, days);
    }

    private void processLoans() {
        long now = System.currentTimeMillis();
        for (String key : data.getKeys(false)) {
            UUID id;
            try { id = UUID.fromString(key); } catch (IllegalArgumentException ignored) { continue; }
            double owed = getOwed(id);
            if (owed <= 0) continue;
            long due = getDue(id);
            long grace = data.getLong(key + ".grace-until", due + 12L * 60L * 60L * 1000L);
            if (now >= due && !data.getBoolean(key + ".overdue", false)) {
                data.set(key + ".overdue", true);
                save();
            }
            // Do not collect anything during the term or the 12-hour grace period.
            // Once grace expires, perform one safe partial sweep only. Any remaining
            // debt must be repaid manually; earnings are not silently confiscated again.
            if (now >= grace && !data.getBoolean(key + ".auto-sweep-done", false)) {
                double available = plugin.getCombinedBalance(id);
                double collected = Math.min(available, owed);
                data.set(key + ".auto-sweep-done", true);
                if (collected > 0.0 && plugin.spendAvailableFunds(id, collected)) {
                    double remaining = Math.max(0.0, Math.round((owed - collected) * 100.0) / 100.0);
                    Player player = Bukkit.getPlayer(id);
                    if (remaining <= 0.0) {
                        data.set(key, null);
                        save();
                        plugin.getHudManager().setLoan(id, 0, 0);
                        if (player != null) plugin.tellPublic(player, "Your overdue loan was automatically repaid: " + money(collected) + " coins.");
                        String name = player != null ? player.getName() : (Bukkit.getOfflinePlayer(id).getName() != null ? Bukkit.getOfflinePlayer(id).getName() : id.toString());
                        plugin.announceLoanRepaid(name, collected, true);
                        continue;
                    }
                    data.set(key + ".amount", remaining);
                    plugin.getHudManager().setLoan(id, remaining, due, grace);
                    if (player != null) {
                        plugin.tellPublic(player, "Your loan is overdue. " + money(collected) + " coins were collected from pocket first, then bank. Remaining debt: " + money(remaining) + ". Shop purchases are locked until full repayment.");
                    }
                } else {
                    Player player = Bukkit.getPlayer(id);
                    if (player != null) plugin.tellPublic(player, "Your loan is overdue and you have no available funds to collect. Shop purchases are locked until full repayment.");
                }
                save();
            }
        }
    }

    private double rateFor(int days) {
        return switch (days) {
            case 2 -> 0.05;
            case 5 -> 0.07;
            case 7 -> 0.08;
            default -> 0.10;
        };
    }

    public void adminClearLoan(UUID id) {
        data.set(id.toString(), null);
        save();
        plugin.getHudManager().setLoan(id, 0, 0);
    }

    public boolean isShopPurchaseRestricted(UUID id) {
        return getOwed(id) > 0 && data.getLong(id + ".grace-until", getDue(id) + 12L * 60L * 60L * 1000L) > 0 && System.currentTimeMillis() >= data.getLong(id + ".grace-until", getDue(id) + 12L * 60L * 60L * 1000L);
    }

    private double getOwed(UUID id) { return data.getDouble(id + ".amount", 0.0); }
    private long getDue(UUID id) { return data.getLong(id + ".due-at", 0L); }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = ChatColor.stripColor(event.getView().getTitle());
        if (title == null || !(title.equals("XZH BANK: Loans") || title.equals("XZH BANK: Choose loan term") || title.equals("XZH BANK: Confirm loan"))) return;
        event.setCancelled(true);
        ItemStack item = event.getCurrentItem();
        if (item == null || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        String action = meta.getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
        Integer amount = meta.getPersistentDataContainer().get(amountKey, PersistentDataType.INTEGER);
        Integer term = meta.getPersistentDataContainer().get(termKey, PersistentDataType.INTEGER);
        if (action == null) return;
        int a = amount == null ? 0 : amount;
        int t = term == null ? 0 : term;
        switch (action) {
            case "amount" -> openTerms(player, a);
            case "term" -> openConfirm(player, a, t);
            case "back" -> openTerms(player, a);
            case "confirm" -> { grant(player, a, t); player.closeInventory(); }
            case "repay" -> { repay(player); player.closeInventory(); }
            case "cancel", "close" -> player.closeInventory();
            default -> { }
        }
    }

    private void add(Inventory inv, int slot, Material material, String name, String action,
                     int amount, int term, List<String> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
        meta.getPersistentDataContainer().set(amountKey, PersistentDataType.INTEGER, amount);
        meta.getPersistentDataContainer().set(termKey, PersistentDataType.INTEGER, term);
        stack.setItemMeta(meta);
        inv.setItem(slot, stack);
    }

    private String loanStatus(UUID id) {
        long now = System.currentTimeMillis();
        long due = getDue(id);
        long grace = data.getLong(id + ".grace-until", due + 12L * 60L * 60L * 1000L);
        if (now < due) return "Due in " + remaining(due);
        if (now < grace) return "Grace period: " + remaining(grace) + " left";
        return "OVERDUE — purchases locked";
    }

    private String remaining(long due) {
        if (due <= 0) return "Unset";
        long ms = due - System.currentTimeMillis();
        if (ms <= 0) return "OVERDUE";
        long hours = ms / 3_600_000L;
        return (hours / 24) + "d " + (hours % 24) + "h left";
    }
    private String money(double n) { return String.format(Locale.US, "%.2f", n).replaceAll("\\.00$", ""); }
    private void save() {
        try { data.save(file); }
        catch (IOException e) { plugin.getLogger().severe("Could not save loans.yml: " + e.getMessage()); }
    }
}
