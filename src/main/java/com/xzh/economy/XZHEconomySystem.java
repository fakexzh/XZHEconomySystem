package com.xzh.economy;

import org.bukkit.plugin.ServicePriority;

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
    private File walletStatusFile;
    private YamlConfiguration walletStatus;
    private PocketCoinManager pocketCoinManager;
    private HudManager hudManager;
    private BankManager bankManager;
    private ShopManager shopManager;
    private LoanManager loanManager;
    private QuestManager questManager;
    private DeathRecoveryManager deathRecoveryManager;
    private UtilityManager utilityManager;
    private AdminManager adminManager;
    private BookShopManager bookShopManager;
    private File transactionsFile;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        transactionsFile = new File(getDataFolder(), "transactions.log");
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
        walletStatusFile = new File(getDataFolder(), "wallets.yml");
        walletStatus = YamlConfiguration.loadConfiguration(walletStatusFile);
        pocketCoinManager = new PocketCoinManager(this);
        hudManager = new HudManager(this);
        hudManager.start();
        bankManager = new BankManager(this);
        shopManager = new ShopManager(this);
        bookShopManager = new BookShopManager(this);
        loanManager = new LoanManager(this);
        getServer().getPluginManager().registerEvents(new LibrarianTradeListener(this), this);
        questManager = new QuestManager(this);
        deathRecoveryManager = new DeathRecoveryManager(this);
        utilityManager = new UtilityManager(this);
        adminManager = new AdminManager(this);
        new GuiDragGuard(this);
        registerVaultProvider();
        getServer().getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler
            public void onJoin(org.bukkit.event.player.PlayerJoinEvent event) { initializePocketWallet(event.getPlayer()); hudManager.onJoin(event.getPlayer()); }
            @org.bukkit.event.EventHandler
            public void onQuit(org.bukkit.event.player.PlayerQuitEvent event) { hudManager.onQuit(event.getPlayer()); }
        }, this);
        for (String name : List.of("xzh", "balance", "pay", "eco", "shop", "bank", "loan", "quests", "deathinventory", "shopadmin", "spawn", "setspawn", "home", "sethome", "delhome", "warp", "setwarp", "delwarp", "tpa", "tpaccept", "tpdeny", "back", "msg", "reply", "ignore", "afk", "near", "list", "top", "rtp", "xzhadmin", "books", "kit")) {
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
    public BankManager getBankManager() { return bankManager; }
    public LoanManager getLoanManager() { return loanManager; }
    public ShopManager getShopManager() { return shopManager; }
    public BookShopManager getBookShopManager() { return bookShopManager; }
    public QuestManager getQuestManager() { return questManager; }
    public DeathRecoveryManager getDeathRecoveryManager() { return deathRecoveryManager; }

    public void tellPublic(CommandSender sender, String message) { tell(sender, message); }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void registerVaultProvider() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) return;
        try {
            Class<?> economyApi = Class.forName("net.milkbowl.vault.economy.Economy");
            Class<?> providerType = Class.forName("com.xzh.economy.VaultEconomyProvider");
            Object provider = providerType.getConstructor(XZHEconomySystem.class).newInstance(this);
            getServer().getServicesManager().register((Class) economyApi, provider, this, ServicePriority.High);
            getLogger().info("Vault economy provider registered.");
        } catch (ReflectiveOperationException | LinkageError e) {
            getLogger().warning("Vault was detected but the economy bridge could not be registered: " + e.getMessage());
        }
    }

    public PocketCoinManager getPocketCoinManager() { return pocketCoinManager; }
    public double getPocketBalanceLimit() { return Math.max(1000.0, getConfig().getDouble("wallet.max-pocket-coins", 1000000.0)); }

    public void initializePocketWallet(Player player) {
        if (walletStatus == null || walletStatus.getBoolean(player.getUniqueId().toString(), false)) return;
        String key = player.getUniqueId().toString();
        double initial = balances.contains(key)
                ? Math.min(getPocketBalanceLimit(), Math.max(0.0, Math.round(balances.getDouble(key, 0.0))))
                : Math.min(getPocketBalanceLimit(), Math.max(0.0, Math.round(getConfig().getDouble("starting-balance", 100.0))));
        // Persist the migration marker before issuing items so a crash cannot duplicate old balances.
        walletStatus.set(key, true);
        balances.set(key, 0.0);
        saveWalletStatus();
        saveBalances();
        pocketCoinManager.setCoins(player, initial);
        balances.set(key, pocketCoinManager.count(player));
        saveBalances();
    }

    public void setOfflinePocketSnapshot(String key, double amount) {
        if (balances == null) return;
        balances.set(key, Math.max(0.0, Math.round(amount)));
        saveBalances();
    }

    private void saveWalletStatus() {
        if (walletStatus == null || walletStatusFile == null) return;
        try {
            walletStatus.save(walletStatusFile);
        } catch (IOException e) {
            getLogger().severe("Could not save wallets.yml: " + e.getMessage());
        }
    }

    public double getCombinedBalance(UUID uuid) {
        double pocket = getBalance(uuid);
        double bank = bankManager == null ? 0.0 : bankManager.getBalance(uuid);
        return Math.round((pocket + bank) * 100.0) / 100.0;
    }

    /** Charges pocket funds first, then bank funds. Call on the server thread. */
    public boolean spendAvailableFunds(UUID uuid, double amount) {
        if (!Double.isFinite(amount) || amount < 0) return false;
        double pocket = getBalance(uuid);
        double bank = bankManager == null ? 0.0 : bankManager.getBalance(uuid);
        if (pocket + bank + 1e-9 < amount) return false;
        double fromPocket = Math.min(pocket, amount);
        double remainder = Math.max(0.0, Math.round((amount - fromPocket) * 100.0) / 100.0);
        setBalance(uuid, pocket - fromPocket);
        if (remainder > 0 && bankManager != null) bankManager.setBalance(uuid, bank - remainder);
        return true;
    }

    public boolean hasBalanceAccount(UUID uuid) {
        return balances.contains(uuid.toString()) || (walletStatus != null && walletStatus.getBoolean(uuid.toString(), false));
    }

    public double getBalance(UUID uuid) {
        String key = uuid.toString();
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            initializePocketWallet(online);
            return pocketCoinManager.count(online);
        }
        if (!balances.contains(key)) {
            balances.set(key, Math.max(0, Math.round(getConfig().getDouble("starting-balance", 100.0))));
            if (walletStatus != null) walletStatus.set(key, false);
            saveBalances();
            saveWalletStatus();
        }
        return Math.max(0.0, balances.getDouble(key, 0.0));
    }

    public void setBalance(UUID uuid, double amount) {
        if (!Double.isFinite(amount)) {
            getLogger().warning("Rejected non-finite balance update for " + uuid + ".");
            return;
        }
        String key = uuid.toString();
        Player online = Bukkit.getPlayer(uuid);
        double oldAmount;
        double newAmount = Math.min(getPocketBalanceLimit(), Math.max(0.0, Math.round(amount)));
        if (online != null) {
            initializePocketWallet(online);
            oldAmount = pocketCoinManager.count(online);
            pocketCoinManager.setCoins(online, newAmount);
            newAmount = pocketCoinManager.count(online);
            balances.set(key, newAmount);
            walletStatus.set(key, true);
        } else {
            oldAmount = balances.getDouble(key, 0.0);
            balances.set(key, newAmount);
            walletStatus.set(key, false);
        }
        saveBalances();
        saveWalletStatus();
        try {
            getDataFolder().mkdirs();
            java.nio.file.Files.writeString(transactionsFile.toPath(),
                    java.time.Instant.now() + " uuid=" + uuid + " old=" + money(oldAmount)
                            + " new=" + money(newAmount) + " delta=" + money(newAmount - oldAmount)
                            + System.lineSeparator(),
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException e) {
            getLogger().warning("Could not append transactions.log: " + e.getMessage());
        }
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
            case "bank" -> {
                if (!(sender instanceof Player player)) { tell(sender, "Only players can open the bank."); return true; }
                bankManager.open(player);
                return true;
            }
            case "loan" -> {
                if (!(sender instanceof Player player)) { tell(sender, "Only players can use loans."); return true; }
                if (args.length == 1 && args[0].equalsIgnoreCase("repay")) return loanManager.repay(player);
                if (args.length > 0) { tell(sender, "Usage: /loan [repay]"); return true; }
                bankManager.open(player);
                return true;
            }
            case "books" -> {
                if (!(sender instanceof Player player)) { tell(sender, "Only players can open the book shop."); return true; }
                bookShopManager.open(player);
                return true;
            }
            case "xzhadmin" -> {
                if (!(sender instanceof Player player)) { tell(sender, "Only players can open the admin GUI."); return true; }
                adminManager.open(player);
                return true;
            }
            case "shopadmin" -> {
                if (!sender.hasPermission("xzh.admin")) { tell(sender, "No permission."); return true; }
                if (args.length == 1 && args[0].equalsIgnoreCase("restock")) {
                    shopManager.restockAll();
                    tell(sender, "Global shop stock has been restocked to configured limits.");
                    return true;
                }
                if (args.length == 3 && args[0].equalsIgnoreCase("stock")) {
                    org.bukkit.Material material;
                    int amount;
                    try { material = org.bukkit.Material.valueOf(args[1].toUpperCase(Locale.ROOT)); amount = Integer.parseInt(args[2]); }
                    catch (IllegalArgumentException ex) { tell(sender, "Usage: /shopadmin stock <MATERIAL> <amount>"); return true; }
                    if (amount < 0 || amount > 1000000) { tell(sender, "Stock must be between 0 and 1000000."); return true; }
                    shopManager.setStock(material, amount);
                    tell(sender, "Global stock for " + material.name() + " set to " + amount + ".");
                    return true;
                }
                tell(sender, "Usage: /shopadmin <restock|stock <MATERIAL> <amount>>");
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
                tell(sender, "§f/shop §7- open XZH SHOP");
                tell(sender, "§f/bank §7- open XZH BANK, deposits, withdrawals and loans");
                if (sender.hasPermission("xzh.admin")) tell(sender, "§f/shopadmin <restock|stock> §7- manage global shop stock");
                tell(sender, "§f/loan repay §7- repay an active loan (also available in /bank)");
                tell(sender, "§f/quests §7- buy quest packs and track progress");
                tell(sender, "§f/deathinventory §7- recover your last death inventory");
                tell(sender, "§f/books §7- enchantment book shop");
                tell(sender, "§f/kit starter §7- claim starter kit");
                tell(sender, "§f/home, /sethome, /warp, /tpa, /back, /rtp §7- utilities");
                if (sender.hasPermission("xzh.admin")) tell(sender, "§f/eco <set|add> <player> <amount> §7- admin economy");
                return true;
            }
            case "balance" -> {
                if (args.length == 0) {
                    if (!(sender instanceof Player player)) {
                        tell(sender, "Console usage: /balance <player>");
                        return true;
                    }
                    tell(sender, "Pocket: §e" + money(getBalance(player.getUniqueId())) + " " + currency() + " §7| Bank: §a" + money(bankManager.getBalance(player.getUniqueId())) + " " + currency());
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[0]);
                if (target == null) {
                    tell(sender, "That player must be online.");
                    return true;
                }
                tell(sender, target.getName() + " — Pocket: §e" + money(getBalance(target.getUniqueId())) + " " + currency() + " §7| Bank: §a" + money(bankManager.getBalance(target.getUniqueId())) + " " + currency());
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
                if (!Double.isFinite(amount) || amount <= 0 || amount != Math.rint(amount)
                        || amount < Math.max(1.0, getConfig().getDouble("minimum-pay", 1.0))
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
                tell(player, "Sent §e" + money(Math.round(amount)) + " " + currency() + "§r to " + target.getName() + ".");
                tell(target, "Received §e" + money(Math.round(amount)) + " " + currency() + "§r from " + player.getName() + ".");
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
            default -> {
                return utilityManager != null && utilityManager.handle(sender, command.getName(), args);
            }
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
