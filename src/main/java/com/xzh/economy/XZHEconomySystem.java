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
    private MoneyRequestManager moneyRequestManager;
    private BoatManager boatManager;
    private VillagerTradeManager villagerTradeManager;
    private PetManager petManager;
    private FortuneBookManager fortuneBookManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        migrateEconomyConfig();
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
        bankManager = new BankManager(this);
        fortuneBookManager = new FortuneBookManager(this);
        hudManager = new HudManager(this);
        hudManager.start();
        petManager = new PetManager(this);
        shopManager = new ShopManager(this);
        boatManager = new BoatManager(this);
        bookShopManager = new BookShopManager(this);
        loanManager = new LoanManager(this);
        villagerTradeManager = new VillagerTradeManager(this);
        questManager = new QuestManager(this);
        deathRecoveryManager = new DeathRecoveryManager(this);
        utilityManager = new UtilityManager(this);
        adminManager = new AdminManager(this);
        moneyRequestManager = new MoneyRequestManager(this);
        new GuiDragGuard(this);
        registerVaultProvider();
        getServer().getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler
            public void onJoin(org.bukkit.event.player.PlayerJoinEvent event) { initializePocketWallet(event.getPlayer()); hudManager.onJoin(event.getPlayer()); }
            @org.bukkit.event.EventHandler
            public void onQuit(org.bukkit.event.player.PlayerQuitEvent event) { hudManager.onQuit(event.getPlayer()); }
        }, this);
        for (String name : List.of("xzh", "balance", "pay", "sendmoney", "askmoney", "eco", "shop", "bank", "loan", "quest", "quests", "deathinventory", "shopadmin", "pet", "unpet", "spawn", "setspawn", "home", "warp", "setwarp", "delwarp", "tpa", "tpasend", "tpacancel", "tpaccept", "tpdeny", "back", "msg", "reply", "ignore", "afk", "near", "list", "top", "rtp", "xzhadmin", "books", "kit", "gmc", "gms", "gma", "gmsp", "day", "night", "gamemode", "heal", "feed", "fly", "weather", "helpxzh")) {
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
        if (petManager != null) petManager.shutdown();
        if (villagerTradeManager != null) villagerTradeManager.shutdown();
        if (fortuneBookManager != null) fortuneBookManager.shutdown();
        saveBalances();
    }

    public HudManager getHudManager() { return hudManager; }
    public BoatManager getBoatManager() { return boatManager; }
    private void migrateEconomyConfig() {
        boolean changed = false;
        // Existing server config.yml files are not overwritten by saveDefaultConfig().
        // Migrate the old economy defaults so the anti-farming balance applies on upgrade.
        if (Math.abs(getConfig().getDouble("shop.prices.diamond.buy", 250.0) - 250.0) < 0.0001) {
            getConfig().set("shop.prices.diamond.buy", 100.0);
            changed = true;
        }
        if (Math.abs(getConfig().getDouble("shop.prices.diamond.sell", 75.0) - 75.0) < 0.0001) {
            getConfig().set("shop.prices.diamond.sell", 5.0);
            changed = true;
        }
        if (getConfig().getDouble("shop.default-sell-price", 0.1) > 0.015625) {
            getConfig().set("shop.default-sell-price", 0.015625);
            changed = true;
        }
        if (!getConfig().contains("utilities.tpa-cost")) { getConfig().set("utilities.tpa-cost", 10.0); changed = true; }
        String[][] tpaPrices = {
            {"up-to-250", "10"}, {"up-to-1000", "20"}, {"up-to-3000", "30"},
            {"up-to-6000", "40"}, {"up-to-10000", "50"}, {"over-10000", "75"}
        };
        for (String[] price : tpaPrices) {
            String path = "utilities.tpa-prices." + price[0];
            if (!getConfig().contains(path)) { getConfig().set(path, Integer.parseInt(price[1])); changed = true; }
        }
        if (!getConfig().contains("utilities.tpa-nether-fixed-cost")) {
            getConfig().set("utilities.tpa-nether-fixed-cost", 66); changed = true;
        }
        if (!getConfig().contains("utilities.back-cost")) { getConfig().set("utilities.back-cost", 30.0); changed = true; }
        if (getConfig().contains("time-control")) { getConfig().set("time-control", null); changed = true; }
        if (!getConfig().contains("announcements.bank.enabled")) { getConfig().set("announcements.bank.enabled", true); changed = true; }

        // One-time migration is necessary because Bukkit does not overwrite an existing config.yml.
        // Apply the explicitly approved 2026-10-22 shop and enchantment-book prices to existing servers.
        if (!getConfig().getBoolean("migrations.price-rebalance-20261022", false)) {
            String[][] shopPrices = {
                {"elytra", "10000", "0"},
                {"netherite_ingot", "1800", "360"},
                {"netherite_helmet", "6000", "300"},
                {"netherite_chestplate", "6000", "300"},
                {"netherite_leggings", "6000", "300"},
                {"netherite_boots", "6000", "300"},
                {"mace", "4500", "900"},
                {"ender_chest", "500", "105"},
                {"enchanted_golden_apple", "2000", "750"},
                {"netherite_upgrade_smithing_template", "1500", "500"}
            };
            for (String[] price : shopPrices) {
                getConfig().set("shop.prices." + price[0] + ".buy", Integer.parseInt(price[1]));
                getConfig().set("shop.prices." + price[0] + ".sell", Integer.parseInt(price[2]));
            }
            String[][] bookPrices = {
                {"aqua_affinity","1700"},{"bane_of_arthropods","1500"},{"binding_curse","1000"},
                {"blast_protection","1700"},{"breach","3750"},{"channeling","2700"},{"density","3750"},
                {"depth_strider","2100"},{"efficiency","2700"},{"feather_falling","2100"},
                {"fire_aspect","2100"},{"fire_protection","1700"},{"flame","1700"},{"fortune","2900"},
                {"frost_walker","2900"},{"impaling","1900"},{"infinity","2500"},{"knockback","1500"},
                {"looting","2700"},{"loyalty","1900"},{"luck_of_the_sea","1900"},{"lure","1700"},
                {"mending","2500"},{"multishot","1900"},{"piercing","1700"},{"power","2100"},
                {"projectile_protection","1700"},{"protection","2300"},{"punch","1500"},
                {"quick_charge","1700"},{"respiration","1900"},{"riptide","2500"},{"sharpness","2300"},
                {"silk_touch","2900"},{"smite","1900"},{"soul_speed","3100"},{"sweeping_edge","2100"},
                {"swift_sneak","3350"},{"thorns","2100"},{"unbreaking","2700"},{"vanishing_curse","1000"},
                {"wind_burst","7300"},{"lunge","3750"}
            };
            for (String[] price : bookPrices) {
                getConfig().set("book-shop.prices." + price[0], Integer.parseInt(price[1]));
            }
            getConfig().set("migrations.price-rebalance-20261022", true);
            changed = true;
        }
        // One-time combined shop rebalance for existing servers; later admin edits remain authoritative.
        if (!getConfig().getBoolean("migrations.shop-villager-rebalance-20261026", false)) {
            String[][] prices = {
                {"iron_ingot","25","1"},{"gold_ingot","40","2"},{"coal","8","0"},{"copper_ingot","10","1"},
                {"lapis_lazuli","10","0"},{"redstone","8","0"},{"emerald","70","8"},{"amethyst_shard","12","1"},
                {"quartz","12","0"},{"netherite_scrap","300","35"},{"netherite_ingot","1800","250"},
                {"diamond","100","5"},{"obsidian","30","0"},{"glass","6","0"},{"end_stone","8","0"},
                {"iron_block","225","0"},{"diamond_block","900","0"},{"tnt","70","10"},
                {"iron_sword","80","4"},{"iron_helmet","190","4"},{"iron_chestplate","300","6"},
                {"iron_leggings","265","5"},{"iron_boots","150","3"},{"shield","60","3"},{"bow","60","3"},
                {"crossbow","110","5"},{"diamond_pickaxe","450","22"},{"diamond_sword","300","15"},
                {"diamond_axe","450","22"},{"diamond_shovel","150","7"},{"diamond_hoe","300","15"},
                {"diamond_helmet","750","37"},{"diamond_chestplate","1200","60"},{"diamond_leggings","1050","52"},
                {"diamond_boots","600","30"},{"netherite_pickaxe","2700","135"},{"netherite_axe","2700","135"},
                {"netherite_sword","2500","125"},{"netherite_helmet","3200","160"},{"netherite_chestplate","3600","180"},
                {"netherite_leggings","3500","175"},{"netherite_boots","3100","155"},{"trident","1000","100"},
                {"mace","3500","350"},{"totem_of_undying","1500","150"},{"ender_chest","500","50"},
                {"golden_carrot","50","5"},{"golden_apple","400","40"},{"enchanted_golden_apple","2000","400"},
                {"netherite_upgrade_smithing_template","1500","0"},{"cod","5","3"},{"salmon","6","3.5"},
                {"cooked_cod","10","4.5"},{"cooked_salmon","12","5.5"},{"cooked_rabbit","11","1"}
            };
            for (String[] price : prices) {
                getConfig().set("shop.prices." + price[0] + ".buy", Double.parseDouble(price[1]));
                getConfig().set("shop.prices." + price[0] + ".sell", Double.parseDouble(price[2]));
            }
            getConfig().set("shop.sell-stock-limits.potion", 256);
            getConfig().set("shop.sell-stock-limits.splash_potion", 256);
            getConfig().set("shop.sell-stock-limits.lingering_potion", 256);
            getConfig().set("migrations.shop-villager-rebalance-20261026", true);
            changed = true;
        }
        // Move existing servers from the original 50/50 Fortune Book default to the new 30/70 odds.
        // The one-time marker preserves future owner-configured odds after this migration.
        if (!getConfig().getBoolean("migrations.fortune-book-odds-20261101", false)) {
            getConfig().set("fortune-books.apple.win-chance", 0.3);
            getConfig().set("migrations.fortune-book-odds-20261101", true);
            changed = true;
        }
        if (changed) {
            saveConfig();
            getLogger().info("Migrated legacy config defaults for revised XZH economy rules.");
        }
    }

    public BankManager getBankManager() { return bankManager; }
    public FortuneBookManager getFortuneBookManager() { return fortuneBookManager; }
    public LoanManager getLoanManager() { return loanManager; }
    public ShopManager getShopManager() { return shopManager; }
    public PetManager getPetManager() { return petManager; }
    public BookShopManager getBookShopManager() { return bookShopManager; }
    public QuestManager getQuestManager() { return questManager; }
    public DeathRecoveryManager getDeathRecoveryManager() { return deathRecoveryManager; }

    public void tellPublic(CommandSender sender, String message) { tell(sender, message); }

    /** Sends all shop-related player notices with the dedicated shop prefix. */
    public void tellShop(CommandSender sender, String message) {
        sender.sendMessage("§8[§6shop§8] §r" + message);
    }

    /** Sends quest, achievement-pack, and Community Chest notices consistently. */
    public void tellQuest(CommandSender sender, String message) {
        sender.sendMessage("§8[§dquest§8] §r" + message);
    }

    /** Sends bank and bank-loan notices with the dedicated bank prefix. */
    public void tellBank(CommandSender sender, String message) {
        sender.sendMessage("§8[§6bank§8] §r" + message);
    }

    /** Sends Fortune Book notices with the dedicated fortune prefix. */
    public void tellFortune(CommandSender sender, String message) {
        sender.sendMessage("§8[§5fortune§8] §r" + message);
    }

    /** Announces only configured high-value, rare, or progression purchases. */
    public void announceSpecialPurchase(Player player, org.bukkit.Material material, int quantity, double total) {
        if (!getConfig().getBoolean("announcements.shop.enabled", true)) return;
        String item = material.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        boolean expensive = total >= getConfig().getDouble("announcements.shop.minimum-purchase-total", 1000.0);
        boolean rare = getConfig().getStringList("announcements.shop.rare-items").stream()
                .anyMatch(name -> name.equalsIgnoreCase(material.name()));
        boolean progression = getConfig().getStringList("announcements.shop.progression-items").stream()
                .anyMatch(name -> name.equalsIgnoreCase(material.name()));
        if (!expensive && !rare && !progression) return;
        String prefix = "§8[§6shop§8] §r";
        if (progression) {
            Bukkit.broadcastMessage(prefix + "§e" + player.getName() + " §funlocked a progression item: §b"
                    + item + " §7(" + money(total) + " coins).");
        } else {
            Bukkit.broadcastMessage(prefix + "§e" + player.getName() + " §fpurchased §b" + quantity + " × "
                    + item + " §ffor §6" + money(total) + " coins§f.");
        }
    }

    public void announceLoanBorrow(Player player, int principal, double repayment, int days) {
        if (!getConfig().getBoolean("announcements.loan.borrowing", true)) return;
        Bukkit.broadcastMessage("§8[§6bank§8] §e" + player.getName() + " §fborrowed §6"
                + money(principal) + " coins§f. Repayment: §c" + money(repayment)
                + " coins §fover §b" + days + " days§f.");
    }

    public void announceLoanRepaid(String playerName, double amount, boolean automatic) {
        if (!getConfig().getBoolean("announcements.loan.full-repayment", true)) return;
        Bukkit.broadcastMessage("§8[§6bank§8] §e" + playerName + " §ffully repaid their loan of §6"
                + money(amount) + " coins§f" + (automatic ? " §7(automatic repayment)." : "."));
    }

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
        double requestedInitial = balances.contains(key)
                ? Math.max(0.0, Math.round(balances.getDouble(key, 0.0)))
                : Math.max(0.0, Math.round(getConfig().getDouble("starting-balance", 100.0)));
        double initial = Math.min(getPocketBalanceLimit(), requestedInitial);
        double overflow = Math.max(0.0, requestedInitial - initial);
        // Persist the migration marker before issuing items so a crash cannot duplicate old balances.
        walletStatus.set(key, true);
        balances.set(key, 0.0);
        saveWalletStatus();
        saveBalances();
        pocketCoinManager.setCoins(player, initial);
        balances.set(key, pocketCoinManager.count(player));
        saveBalances();
        if (overflow > 0 && bankManager != null) bankManager.setBalance(player.getUniqueId(), bankManager.getBalance(player.getUniqueId()) + overflow);
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
        double requestedAmount = Math.max(0.0, Math.round(amount));
        double newAmount = Math.min(getPocketBalanceLimit(), requestedAmount);
        double overflow = Math.max(0.0, requestedAmount - newAmount);
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
        if (overflow > 0 && bankManager != null) {
            bankManager.setBalance(uuid, bankManager.getBalance(uuid) + overflow);
            Player recipient = Bukkit.getPlayer(uuid);
            if (recipient != null) tellBank(recipient, "Your pocket wallet reached its limit; " + money(overflow) + " excess coins were secured in the bank.");
        }
        saveBalances();
        saveWalletStatus();
        if (online != null && newAmount > oldAmount) online.playSound(online.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.55f, 1.25f);
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
            case "gmc", "gms", "gma", "gmsp", "gamemode", "day", "night", "heal", "feed", "fly", "weather" -> {
                if (!sender.hasPermission("xzh.admin")) { tell(sender, "No permission."); return true; }
                if (!(sender instanceof Player player)) { tell(sender, "This command can only be used by a player."); return true; }
                String adminCommand = command.getName().toLowerCase(Locale.ROOT);
                if (adminCommand.equals("gamemode")) {
                    if (args.length != 1) { tell(sender, "Usage: /gamemode <survival|creative|adventure|spectator>"); return true; }
                    switch (args[0].toLowerCase(Locale.ROOT)) {
                        case "s", "survival", "0" -> player.setGameMode(org.bukkit.GameMode.SURVIVAL);
                        case "c", "creative", "1" -> player.setGameMode(org.bukkit.GameMode.CREATIVE);
                        case "a", "adventure", "2" -> player.setGameMode(org.bukkit.GameMode.ADVENTURE);
                        case "sp", "spectator", "3" -> player.setGameMode(org.bukkit.GameMode.SPECTATOR);
                        default -> { tell(sender, "Unknown gamemode."); return true; }
                    }
                    tell(sender, "Gamemode set to " + player.getGameMode().name().toLowerCase(Locale.ROOT) + ".");
                    return true;
                }
                switch (adminCommand) {
                    case "gmc" -> { player.setGameMode(org.bukkit.GameMode.CREATIVE); tell(sender, "Gamemode set to Creative."); }
                    case "gms" -> { player.setGameMode(org.bukkit.GameMode.SURVIVAL); tell(sender, "Gamemode set to Survival."); }
                    case "gma" -> { player.setGameMode(org.bukkit.GameMode.ADVENTURE); tell(sender, "Gamemode set to Adventure."); }
                    case "gmsp" -> { player.setGameMode(org.bukkit.GameMode.SPECTATOR); tell(sender, "Gamemode set to Spectator."); }
                    case "day" -> { player.getWorld().setTime(1000L); tell(sender, "World time set to day."); }
                    case "night" -> { player.getWorld().setTime(13000L); tell(sender, "World time set to night."); }
                    case "heal" -> { player.setHealth(player.getMaxHealth()); player.setFireTicks(0); tell(sender, "Health restored."); }
                    case "feed" -> { player.setFoodLevel(20); player.setSaturation(20f); tell(sender, "Hunger restored."); }
                    case "fly" -> { boolean enable = !player.getAllowFlight(); player.setAllowFlight(enable); if (!enable) player.setFlying(false); tell(sender, "Flight " + (enable ? "enabled" : "disabled") + "."); }
                    case "weather" -> {
                        if (args.length != 1) { tell(sender, "Usage: /weather <clear|rain|thunder>"); return true; }
                        switch (args[0].toLowerCase(Locale.ROOT)) {
                            case "clear" -> { player.getWorld().setStorm(false); player.getWorld().setThundering(false); }
                            case "rain" -> { player.getWorld().setStorm(true); player.getWorld().setThundering(false); }
                            case "thunder" -> { player.getWorld().setStorm(true); player.getWorld().setThundering(true); }
                            default -> { tell(sender, "Unknown weather. Use clear, rain, or thunder."); return true; }
                        }
                        tell(sender, "Weather updated.");
                    }
                }
                return true;
            }
            case "deathinventory" -> {
                if (!(sender instanceof Player player)) { tell(sender, "Only players can recover a death inventory."); return true; }
                deathRecoveryManager.open(player);
                return true;
            }
            case "quest", "quests" -> {
                if (!(sender instanceof Player player)) { tellQuest(sender, "Only players can use quests."); return true; }
                if (args.length == 1 && args[0].equalsIgnoreCase("cancel")) {
                    questManager.cancelActiveQuest(player);
                    return true;
                }
                if (args.length != 0) { tellQuest(sender, "Usage: /quest cancel or /quests"); return true; }
                questManager.open(player);
                return true;
            }
            case "bank" -> {
                if (!(sender instanceof Player player)) { tellBank(sender, "Only players can open the bank."); return true; }
                bankManager.open(player);
                return true;
            }
            case "loan" -> {
                if (!(sender instanceof Player player)) { tellBank(sender, "Only players can use loans."); return true; }
                if (args.length == 1 && args[0].equalsIgnoreCase("repay")) return loanManager.repay(player);
                if (args.length > 0) { tellBank(sender, "Usage: /loan [repay]"); return true; }
                bankManager.open(player);
                return true;
            }
            case "books" -> {
                if (!(sender instanceof Player player)) { tellShop(sender, "Only players can open the book shop."); return true; }
                bookShopManager.open(player);
                return true;
            }
            case "xzhadmin" -> {
                if (!(sender instanceof Player player)) { tell(sender, "Only players can open the admin GUI."); return true; }
                adminManager.open(player);
                return true;
            }
            case "shopadmin" -> {
                if (!sender.hasPermission("xzh.admin")) { tellShop(sender, "No permission."); return true; }
                if (args.length == 1 && args[0].equalsIgnoreCase("restock")) {
                    shopManager.restockAll();
                    tellShop(sender, "Global shop stock has been restocked to configured limits.");
                    return true;
                }
                if (args.length == 3 && args[0].equalsIgnoreCase("stock")) {
                    org.bukkit.Material material;
                    int amount;
                    try { material = org.bukkit.Material.valueOf(args[1].toUpperCase(Locale.ROOT)); amount = Integer.parseInt(args[2]); }
                    catch (IllegalArgumentException ex) { tellShop(sender, "Usage: /shopadmin stock <MATERIAL> <amount>"); return true; }
                    if (amount < 0 || amount > 1000000) { tellShop(sender, "Stock must be between 0 and 1000000."); return true; }
                    shopManager.setStock(material, amount);
                    tellShop(sender, "Global stock for " + material.name() + " set to " + amount + ".");
                    return true;
                }
                tellShop(sender, "Usage: /shopadmin <restock|stock <MATERIAL> <amount>>");
                return true;
            }
            case "pet", "unpet" -> {
                if (!(sender instanceof Player player)) { tell(sender, "Only players can control pets."); return true; }
                return petManager != null && petManager.handleCommand(player, command.getName(), args);
            }
            case "shop" -> {
                if (!(sender instanceof Player player)) { tellShop(sender, "Only players can open the shop."); return true; }
                if (shopManager != null) shopManager.openMain(player);
                return true;
            }
            case "xzh" -> {
                tell(sender, "§eXZHEconomySystem §7v" + getPluginMeta().getVersion());
                tell(sender, "§f/balance §7- check your coins");
                tell(sender, "§f/pay <player> <amount> §7- send coins");
                tell(sender, "§f/sendmoney <player> <amount> §7- request a confirmed transfer to them");
                tell(sender, "§f/askmoney <player> <amount> §7- ask them for a confirmed payment");
                tell(sender, "§f/shop §7- open Shop");
                tell(sender, "§f/pet [all|transfer <player>] §7- call active pet, bring all pets, or transfer active pet");
                tell(sender, "§f/unpet §7- stop your targeted pet from following");
                tell(sender, "§f/bank §7- open Bank, deposits, withdrawals and loans");
                if (sender.hasPermission("xzh.admin")) tell(sender, "§f/shopadmin <restock|stock> §7- manage global shop stock");
                tell(sender, "§f/loan repay §7- repay an active loan (also available in /bank)");
                tell(sender, "§f/quests §7- buy quest packs and track progress");
                tell(sender, "§f/quest cancel §7- cancel an active paid quest for a 50% purchase-price refund");
                tell(sender, "§f/deathinventory §7- recover your last death inventory");
                tell(sender, "§f/books §7- enchantment book shop");
                tell(sender, "§f/kit starter §7- claim starter kit");
                tell(sender, "§f/home, /warp, /tpa, /back, /rtp §7- utilities");
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
            case "moneyresponse" -> {
                if (!(sender instanceof Player player)) { tell(sender, "Only players can respond to money requests."); return true; }
                return moneyRequestManager.handleResponse(player, args);
            }
            case "sendmoney", "askmoney" -> {
                if (!(sender instanceof Player player)) { tell(sender, "Only players can use money requests."); return true; }
                return moneyRequestManager.handle(player, command.getName(), args);
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
                try { amount = Double.parseDouble(args[1]); }
                catch (NumberFormatException e) { tell(sender, "Enter a valid number."); return true; }
                if (!Double.isFinite(amount) || amount <= 0 || amount != Math.rint(amount)
                        || amount < Math.max(1.0, getConfig().getDouble("minimum-pay", 1.0))
                        || amount > getConfig().getDouble("maximum-pay", 1000000.0)) {
                    tell(sender, "That amount is outside the allowed range.");
                    return true;
                }
                double senderPocket = getBalance(player.getUniqueId());
                double senderBank = bankManager == null ? 0.0 : bankManager.getBalance(player.getUniqueId());
                if (senderPocket + senderBank + 1e-9 < amount) {
                    tell(sender, "Not enough funds. Pocket: §e" + money(senderPocket)
                            + "§r; bank: §a" + money(senderBank) + "§r; payment: §e" + money(amount) + "§r.");
                    return true;
                }
                // Payments follow the same rule as shop purchases and loan repayments: pocket first, bank second.
                if (!spendAvailableFunds(player.getUniqueId(), amount)) {
                    tell(sender, "Payment failed because your available funds changed. No money was sent.");
                    return true;
                }
                setBalance(target.getUniqueId(), getBalance(target.getUniqueId()) + amount);
                tell(player, "Sent §e" + money(Math.round(amount)) + " " + currency() + "§r to " + target.getName()
                        + " (pocket first, then bank if needed).");
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
        } else if (command.getName().equalsIgnoreCase("quest") && args.length == 1) {
            suggestions.add("cancel");
        } else if (command.getName().equalsIgnoreCase("pet") && args.length == 1) {
            suggestions.add("all"); suggestions.add("transfer");
        } else if ((command.getName().equalsIgnoreCase("pet") && args.length == 2 && args[0].equalsIgnoreCase("transfer"))
                || (command.getName().equalsIgnoreCase("tpa") && args.length == 1)
                || (command.getName().equalsIgnoreCase("pay") && args.length == 1)
                || (command.getName().equalsIgnoreCase("sendmoney") && args.length == 1)
                || (command.getName().equalsIgnoreCase("askmoney") && args.length == 1)
                || (command.getName().equalsIgnoreCase("balance") && args.length == 1)
                || (command.getName().equalsIgnoreCase("eco") && args.length == 2 && sender.hasPermission("xzh.admin"))) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                suggestions.add(command.getName().equalsIgnoreCase("pet") ? "@" + player.getName() : player.getName());
            }
        }
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        if (command.getName().equalsIgnoreCase("pet") && args.length == 2 && prefix.startsWith("@")) prefix = prefix.substring(1);
        final String filterPrefix = prefix;
        suggestions.removeIf(s -> !(s.startsWith("@") ? s.substring(1) : s).toLowerCase(Locale.ROOT).startsWith(filterPrefix));
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
