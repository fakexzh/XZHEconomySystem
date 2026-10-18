package com.xzh.economy;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class XZHEconomySystem extends JavaPlugin implements CommandExecutor, TabCompleter {
    private File balancesFile;
    private YamlConfiguration balances;
    private HudManager hudManager;
    private ShopManager shopManager;
    private LoanManager loanManager;
    private QuestManager questManager;
    private DeathRecoveryManager deathRecoveryManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        balancesFile = new File(getDataFolder(), "balances.yml");
        if (!balancesFile.exists()) {
            try {
                getDataFolder().mkdirs();
                balancesFile.createNewFile();
            } catch (IOException e) {
                getLogger().severe("Could not create balances.yml: " + e.getMessage());
            }
        }
        balances = YamlConfiguration.loadConfiguration(balancesFile);
        hudManager = new HudManager(this);
        hudManager.start();
        shopManager = new ShopManager(this);
        loanManager = new LoanManager(this);
        getServer().getPluginManager().registerEvents(new LibrarianTradeListener(), this);
        questManager = new QuestManager(this);
        deathRecoveryManager = new DeathRecoveryManager(this);
        getServer().getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler
            public void onJoin(org.bukkit.event.player.PlayerJoinEvent event) { hudManager.onJoin(event.getPlayer()); }
            @org.bukkit.event.EventHandler
            public void onQuit(org.bukkit.event.player.PlayerQuitEvent event) { hudManager.onQuit(event.getPlayer()); }
        }, this);
        for (String name : List.of("xzh", "balance", "pay", "eco", "shop", "loan", "quests", "deathinventory")) {
            if (getCommand(name) != null) {
                getCommand(name).setExecutor(this);
                getCommand(name).setTabCompleter(this);
            }
        }
        getServer().getPluginManager().registerEvents(new JoinListener(this), this);
        getLogger().info("XZHEconomySystem enabled. Stored balances loaded.");
    }

    @Override
    public void onDisable() {
        if (hudManager != null) hudManager.shutdown();
        saveBalances();
    }

    public HudManager getHudManager() { return hudManager; }
    public LoanManager getLoanManager() { return loanManager; }

    public void tellPublic(CommandSender sender, String message) { tell(sender, message); }

    public double getBalance(UUID uuid) {
        String key = uuid.toString();
        if (!balances.contains(key)) {
            balances.set(key, getConfig().getDouble("starting-balance", 100.0));
            saveBalances();
        }
        return Math.max(0.0, balances.getDouble(key, 0.0));
    }

    public void setBalance(UUID uuid, double amount) {
        balances.set(uuid.toString(), Math.max(0.0, round(amount)));
        saveBalances();
    }

    private double round(double amount) {
        return Math.round(amount * 100.0) / 100.0;
    }

    private void saveBalances() {
        if (balances == null || balancesFile == null) return;
        try {
            balances.save(balancesFile);
        } catch (IOException e) {
            getLogger().severe("Could not save balances.yml: " + e.getMessage());
        }
    }

    private String money(double amount) {
        return String.format(Locale.US, "%.2f", round(amount)).replaceAll("\\.00$", "");
    }

    private String currency() {
        return getConfig().getString("currency-name", "coins");
    }

    private void tell(CommandSender sender, String message) {
        sender.sendMessage("§8[§6XZH§8] §r" + message);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "deathinventory" -> {
                if (!(sender instanceof Player player)) { tell(sender, "Only players can recover a death inventory."); return true; }
                deathRecoveryManager.open(player);
                return true;
            }
            case "quests" -> {
                if (!(sender instanceof Player player)) { tell(sender, "Only players can view quests."); return true; }
                questManager.open(player);
                return true;
            }
            case "loan" -> {
                if (!(sender instanceof Player player)) { tell(sender, "Only players can use loans."); return true; }
                if (args.length == 1 && args[0].equalsIgnoreCase("repay")) return loanManager.repay(player);
                if (args.length > 0) { tell(sender, "Usage: /loan [repay]"); return true; }
                loanManager.open(player);
                return true;
            }
            case "shop" -> {
                if (!(sender instanceof Player player)) { tell(sender, "Only players can open the shop."); return true; }
                if (shopManager != null) shopManager.openMain(player);
                return true;
            }
            case "xzh" -> {
                tell(sender, "§eXZHEconomySystem §7v" + getPluginMeta().getVersion());
                tell(sender, "§f/balance §7- check your coins");
                tell(sender, "§f/pay <player> <amount> §7- send coins");
                tell(sender, "§f/shop §7- open the shop");
                tell(sender, "§f/loan [repay] §7- borrow or repay coins");
                tell(sender, "§f/quests §7- buy quest packs and track progress");
                tell(sender, "§f/deathinventory §7- recover your last death inventory");
                if (sender.hasPermission("xzh.admin")) tell(sender, "§f/eco <set|add> <player> <amount> §7- admin economy");
                return true;
            }
            case "balance" -> {
                if (args.length == 0) {
                    if (!(sender instanceof Player player)) {
                        tell(sender, "Console usage: /balance <player>");
                        return true;
                    }
                    tell(sender, "Balance: §e" + money(getBalance(player.getUniqueId())) + " " + currency());
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[0]);
                if (target == null) {
                    tell(sender, "That player must be online.");
                    return true;
                }
                tell(sender, target.getName() + "'s balance: §e" + money(getBalance(target.getUniqueId())) + " " + currency());
                return true;
            }
            case "pay" -> {
                if (!(sender instanceof Player player)) {
                    tell(sender, "Only players can use /pay.");
                    return true;
                }
                if (args.length != 2) {
                    tell(sender, "Usage: /pay <player> <amount>");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[0]);
                if (target == null || target.equals(player)) {
                    tell(sender, "Choose another player who is online.");
                    return true;
                }
                double amount;
                try { amount = round(Double.parseDouble(args[1])); }
                catch (NumberFormatException e) { tell(sender, "Enter a valid number."); return true; }
                if (!Double.isFinite(amount) || amount < getConfig().getDouble("minimum-pay", 1.0)
                        || amount > getConfig().getDouble("maximum-pay", 1000000.0)) {
                    tell(sender, "That amount is outside the allowed range.");
                    return true;
                }
                double senderBalance = getBalance(player.getUniqueId());
                if (senderBalance < amount) {
                    tell(sender, "You don't have enough coins.");
                    return true;
                }
                setBalance(player.getUniqueId(), senderBalance - amount);
                setBalance(target.getUniqueId(), getBalance(target.getUniqueId()) + amount);
                tell(player, "Sent §e" + money(amount) + " " + currency() + "§r to " + target.getName() + ".");
                tell(target, "Received §e" + money(amount) + " " + currency() + "§r from " + player.getName() + ".");
                return true;
            }
            case "eco" -> {
                if (!sender.hasPermission("xzh.admin")) { tell(sender, "No permission."); return true; }
                if (args.length != 3 || !(args[0].equalsIgnoreCase("set") || args[0].equalsIgnoreCase("add"))) {
                    tell(sender, "Usage: /eco <set|add> <online-player> <amount>");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) { tell(sender, "That player must be online."); return true; }
                double amount;
                try { amount = Double.parseDouble(args[2]); }
                catch (NumberFormatException e) { tell(sender, "Enter a valid number."); return true; }
                if (!Double.isFinite(amount) || amount < 0 || amount > 1.0E12) { tell(sender, "Invalid amount."); return true; }
                double newBalance = args[0].equalsIgnoreCase("set") ? amount : getBalance(target.getUniqueId()) + amount;
                setBalance(target.getUniqueId(), newBalance);
                tell(sender, "Set " + target.getName() + " to §e" + money(newBalance) + " " + currency() + ".");
                tell(target, "Your balance is now §e" + money(newBalance) + " " + currency() + ".");
                return true;
            }
            default -> { return false; }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> suggestions = new ArrayList<>();
        if (command.getName().equalsIgnoreCase("eco") && args.length == 1 && sender.hasPermission("xzh.admin")) {
            suggestions.add("set"); suggestions.add("add");
        } else if ((command.getName().equalsIgnoreCase("pay") && args.length == 1)
                || (command.getName().equalsIgnoreCase("balance") && args.length == 1)
                || (command.getName().equalsIgnoreCase("eco") && args.length == 2 && sender.hasPermission("xzh.admin"))) {
            for (Player player : Bukkit.getOnlinePlayers()) suggestions.add(player.getName());
        }
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        suggestions.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(prefix));
        return suggestions;
    }

    private static final class JoinListener implements org.bukkit.event.Listener {
        private final XZHEconomySystem plugin;
        private JoinListener(XZHEconomySystem plugin) { this.plugin = plugin; }
        @org.bukkit.event.EventHandler
        public void onJoin(org.bukkit.event.player.PlayerJoinEvent event) {
            plugin.getBalance(event.getPlayer().getUniqueId());
        }
    }
}
