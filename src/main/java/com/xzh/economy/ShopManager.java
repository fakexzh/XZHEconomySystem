package com.xzh.economy;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Locale;

/**
 * First functional shop GUI slice: category browsing, quantity selection,
 * and confirmation before buying/selling configured items.
 */
public final class ShopManager implements Listener {
    private final XZHEconomySystem plugin;
    private final NamespacedKey actionKey;
    private final NamespacedKey materialKey;
    private final NamespacedKey quantityKey;
    private final NamespacedKey potionTypeKey;
    private final NamespacedKey fireworkPowerKey;
    private final NamespacedKey xpBottleKey;
    private final NamespacedKey xpPointsKey;
    private final NamespacedKey sellUnitPriceKey;
    private final File stockFile;
    private final YamlConfiguration stockData;
    private final File playerSellStockFile;
    private final YamlConfiguration playerSellStockData;
    private final File historyFile;
    private final YamlConfiguration historyData;
    private final File bountyCooldownFile;
    private final YamlConfiguration bountyCooldownData;
    private final Map<java.util.UUID, Boolean> pendingShopSearch = new java.util.concurrent.ConcurrentHashMap<>();

    private record Product(String category, Material material, double buy, double sell, PotionType potionType, int fireworkPower) {
        Product(String category, Material material, double buy, double sell) { this(category, material, buy, sell, null, 0); }
        Product(String category, Material material, double buy, double sell, PotionType potionType) { this(category, material, buy, sell, potionType, 0); }
    }

    private final List<Product> products = new ArrayList<>(List.of(
        new Product("ores", Material.COAL, 8, 0),
        new Product("ores", Material.IRON_INGOT, 25, 1),
        new Product("ores", Material.GOLD_INGOT, 40, 2),
        new Product("ores", Material.COPPER_INGOT, 10, 1),
        new Product("ores", Material.LAPIS_LAZULI, 10, 0),
        new Product("ores", Material.REDSTONE, 8, 0),
        new Product("ores", Material.EMERALD, 70, 8),
        new Product("ores", Material.AMETHYST_SHARD, 12, 1),
        new Product("ores", Material.QUARTZ, 12, 0),
        new Product("ores", Material.NETHERITE_SCRAP, 300, 35),
        new Product("ores", Material.NETHERITE_INGOT, 1800, 250),
        new Product("ores", Material.DIAMOND, 100, 5),
        new Product("blocks", Material.OBSIDIAN, 30, 0),
        new Product("blocks", Material.GLASS, 6, 0),
        new Product("blocks", Material.END_STONE, 8, 0),
        new Product("blocks", Material.IRON_BLOCK, 225, 0),
        new Product("blocks", Material.DIAMOND_BLOCK, 900, 0),
        new Product("blocks", Material.NETHERITE_BLOCK, 16200, 0),
        new Product("blocks", Material.GOLD_BLOCK, 360, 0),
        new Product("blocks", Material.RAW_GOLD_BLOCK, 360, 0),
        new Product("blocks", Material.RAW_IRON_BLOCK, 225, 0),
        new Product("blocks", Material.RAW_COPPER_BLOCK, 90, 0),
        new Product("blocks", Material.COPPER_BLOCK, 90, 0),
        new Product("blocks", Material.EMERALD_BLOCK, 630, 0),
        new Product("blocks", Material.LAPIS_BLOCK, 90, 0),
        new Product("blocks", Material.REDSTONE_BLOCK, 72, 0),
        new Product("blocks", Material.COAL_BLOCK, 72, 0),
        new Product("blocks", Material.AMETHYST_BLOCK, 48, 0),
        new Product("blocks", Material.SLIME_BLOCK, 135, 0),
        new Product("blocks", Material.MAGMA_BLOCK, 120, 0),
        new Product("blocks", Material.NETHER_WART_BLOCK, 180, 0),
        new Product("blocks", Material.PRISMARINE, 60, 0),
        new Product("blocks", Material.PRISMARINE_BRICKS, 135, 0),
        new Product("blocks", Material.DARK_PRISMARINE, 130, 0),
        new Product("blocks", Material.QUARTZ_BLOCK, 50, 0),
        new Product("blocks", Material.QUARTZ_PILLAR, 50, 0),
        new Product("blocks", Material.SMOOTH_QUARTZ, 50, 0),
        new Product("blocks", Material.CHISELED_QUARTZ_BLOCK, 50, 0),
        new Product("blocks", Material.BONE_BLOCK, 45, 0),
        new Product("blocks", Material.DRIED_KELP_BLOCK, 45, 0),
        new Product("blocks", Material.HAY_BLOCK, 18, 0),
        new Product("blocks", Material.HONEY_BLOCK, 30, 0),
        new Product("food-cooked", Material.BREAD, 8, 2.4),
        new Product("food-cooked", Material.COOKED_BEEF, 14, 4.2),
        new Product("food-cooked", Material.COOKED_PORKCHOP, 16, 4.8),
        new Product("food-cooked", Material.COOKED_CHICKEN, 12, 3.6),
        new Product("food-cooked", Material.BAKED_POTATO, 7, 2.1),
        new Product("food-cooked", Material.COOKED_COD, 10, 4.5),
        new Product("food-cooked", Material.COOKED_SALMON, 12, 5.5),
        new Product("food-raw", Material.BEEF, 8, 2.4),
        new Product("food-raw", Material.PORKCHOP, 9, 2.7),
        new Product("food-raw", Material.CHICKEN, 7, 2.1),
        new Product("food-raw", Material.COD, 5, 3),
        new Product("food-raw", Material.SALMON, 6, 3.5),
        new Product("food-raw", Material.MUTTON, 7, 2.1),
        new Product("food-raw", Material.RABBIT, 7, 2.1),
        new Product("food-raw", Material.MILK_BUCKET, 60, 50),
        new Product("equipment", Material.IRON_PICKAXE, 120, 36),
        new Product("equipment", Material.DIAMOND_PICKAXE, 450, 22),
        new Product("equipment", Material.IRON_CHESTPLATE, 300, 6),
        new Product("equipment", Material.DIAMOND_SWORD, 300, 15),
        new Product("equipment", Material.IRON_SWORD, 80, 4),
        new Product("equipment", Material.IRON_AXE, 110, 33),
        new Product("equipment", Material.DIAMOND_AXE, 450, 22),
        new Product("equipment", Material.DIAMOND_SHOVEL, 150, 7),
        new Product("equipment", Material.DIAMOND_HOE, 300, 15),
        new Product("equipment", Material.NETHERITE_PICKAXE, 2700, 135),
        new Product("equipment", Material.NETHERITE_AXE, 2700, 135),
        new Product("equipment", Material.NETHERITE_SWORD, 2500, 125),
        new Product("equipment", Material.IRON_HELMET, 190, 4),
        new Product("equipment", Material.IRON_LEGGINGS, 265, 5),
        new Product("equipment", Material.IRON_BOOTS, 150, 3),
        new Product("equipment", Material.DIAMOND_HELMET, 750, 37),
        new Product("equipment", Material.DIAMOND_CHESTPLATE, 1200, 60),
        new Product("equipment", Material.DIAMOND_LEGGINGS, 1050, 52),
        new Product("equipment", Material.DIAMOND_BOOTS, 600, 30),
        new Product("equipment", Material.NETHERITE_HELMET, 3200, 160),
        new Product("equipment", Material.NETHERITE_CHESTPLATE, 3600, 180),
        new Product("equipment", Material.NETHERITE_LEGGINGS, 3500, 175),
        new Product("equipment", Material.NETHERITE_BOOTS, 3100, 155),
        new Product("equipment", Material.BOW, 60, 3),
        new Product("equipment", Material.CROSSBOW, 110, 5),
        new Product("equipment", Material.SHIELD, 60, 3),
        new Product("equipment", Material.TRIDENT, 1000, 100),
        new Product("equipment", Material.MACE, 3500, 350),
        new Product("equipment", Material.TOTEM_OF_UNDYING, 1500, 150),
        new Product("equipment", Material.ENDER_CHEST, 500, 50),
        new Product("food-cooked", Material.GOLDEN_CARROT, 50, 5),
        new Product("food-cooked", Material.PUMPKIN_PIE, 12, 3.6),
        new Product("food-cooked", Material.GOLDEN_APPLE, 400, 40),
        new Product("food-cooked", Material.ENCHANTED_GOLDEN_APPLE, 2000, 400),
        new Product("food-cooked", Material.COOKED_MUTTON, 10, 3),
        new Product("eggs-heads", Material.CREEPER_HEAD, 500, 150),
        new Product("eggs-heads", Material.SKELETON_SKULL, 350, 105),
        new Product("eggs-heads", Material.WITHER_SKELETON_SKULL, 1000, 300),
        new Product("eggs-heads", Material.CAT_SPAWN_EGG, 2200, 0),
        new Product("eggs-heads", Material.WOLF_SPAWN_EGG, 2200, 0),
        new Product("eggs-heads", Material.FOX_SPAWN_EGG, 2500, 0),
        new Product("eggs-heads", Material.PARROT_SPAWN_EGG, 2800, 0),
        new Product("eggs-heads", Material.BEE_SPAWN_EGG, 2000, 0),
        new Product("eggs-heads", Material.PANDA_SPAWN_EGG, 2800, 0),
        new Product("eggs-heads", Material.GOAT_SPAWN_EGG, 2300, 0),
        new Product("eggs-heads", Material.MOOSHROOM_SPAWN_EGG, 3000, 0),
        new Product("xp", Material.EXPERIENCE_BOTTLE, 25, 7.5),
        new Product("eggs-heads", Material.ZOMBIE_HEAD, 400, 120),
        new Product("eggs-heads", Material.CHICKEN_SPAWN_EGG, 1000, 0),
        new Product("eggs-heads", Material.COW_SPAWN_EGG, 1500, 0),
        new Product("eggs-heads", Material.SHEEP_SPAWN_EGG, 1500, 0),
        new Product("eggs-heads", Material.PIG_SPAWN_EGG, 1500, 0),
                new Product("eggs-heads", Material.RABBIT_SPAWN_EGG, 1800, 0),
        new Product("eggs-heads", Material.TURTLE_SPAWN_EGG, 2200, 0),
        new Product("eggs-heads", Material.AXOLOTL_SPAWN_EGG, 2800, 0),
        new Product("eggs-heads", Material.CAMEL_SPAWN_EGG, 3000, 0),
        new Product("eggs-heads", Material.ARMADILLO_SPAWN_EGG, 2500, 0),
        new Product("eggs-heads", Material.SNIFFER_SPAWN_EGG, 3500, 0),
        new Product("eggs-heads", Material.FROG_SPAWN_EGG, 2200, 0),
        new Product("eggs-heads", Material.EGG, 2, 0.6),
        new Product("special", Material.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE, 600, 0),
        new Product("special", Material.DUNE_ARMOR_TRIM_SMITHING_TEMPLATE, 700, 0),
        new Product("special", Material.COAST_ARMOR_TRIM_SMITHING_TEMPLATE, 700, 0),
        new Product("special", Material.WARD_ARMOR_TRIM_SMITHING_TEMPLATE, 900, 0),
        new Product("special", Material.SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE, 1500, 0),
        new Product("special", Material.SPIRE_ARMOR_TRIM_SMITHING_TEMPLATE, 1000, 0),
        new Product("special", Material.TIDE_ARMOR_TRIM_SMITHING_TEMPLATE, 1000, 0),
        new Product("special", Material.WAYFINDER_ARMOR_TRIM_SMITHING_TEMPLATE, 900, 0),
        new Product("special", Material.SHAPER_ARMOR_TRIM_SMITHING_TEMPLATE, 900, 0),
        new Product("special", Material.RAISER_ARMOR_TRIM_SMITHING_TEMPLATE, 900, 0),
        new Product("special", Material.HOST_ARMOR_TRIM_SMITHING_TEMPLATE, 900, 0),
        new Product("special", Material.FLOW_ARMOR_TRIM_SMITHING_TEMPLATE, 1200, 0),
        new Product("special", Material.BOLT_ARMOR_TRIM_SMITHING_TEMPLATE, 1200, 0),
        new Product("special", Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE, 1500, 0),
        new Product("special", Material.ELYTRA, 10000, 0),
        new Product("bounty-maps", Material.FILLED_MAP, 100, 0, null, 1),
        new Product("bounty-maps", Material.FILLED_MAP, 300, 0, null, 2),
        new Product("bounty-maps", Material.FILLED_MAP, 600, 0, null, 3),
        new Product("bounty-maps", Material.FILLED_MAP, 150, 0, null, 4),
        new Product("bounty-maps", Material.FILLED_MAP, 450, 0, null, 5),
        new Product("bounty-maps", Material.FILLED_MAP, 900, 0, null, 6)
    ));

    public ShopManager(XZHEconomySystem plugin) {
        this.plugin = plugin;
        this.actionKey = new NamespacedKey(plugin, "shop_action");
        this.materialKey = new NamespacedKey(plugin, "shop_material");
        this.quantityKey = new NamespacedKey(plugin, "shop_quantity");
        this.potionTypeKey = new NamespacedKey(plugin, "shop_potion_type");
        this.fireworkPowerKey = new NamespacedKey(plugin, "shop_firework_power");
        this.xpBottleKey = new NamespacedKey(plugin, "xzh_xp_bottle");
        this.xpPointsKey = new NamespacedKey(plugin, "xzh_xp_points");
        this.sellUnitPriceKey = new NamespacedKey(plugin, "xzh_sell_unit_price");
        // Include every potion base type in all three bottle forms. Variant identity is
        // stored in item metadata so buying a Strength potion cannot become a Water potion.
        for (PotionType type : PotionType.values()) {
            String typeName = type.name();
            if (typeName.equals("UNCRAFTABLE") || typeName.equals("CUSTOM")) continue;
            double base = potionBasePrice(type);
            products.add(new Product("potions", Material.POTION, base, 0, type));
            products.add(new Product("potions", Material.SPLASH_POTION, base + 15, 0, type));
            products.add(new Product("potions", Material.LINGERING_POTION, base + 35, 0, type));
        }
        // Add every edible food item so players can sell crops, fish, cooked food, and other food.
        for (Material material : Material.values()) {
            if (material.isItem() && material.isEdible()
                    && products.stream().noneMatch(p -> p.material() == material && p.potionType() == null)) {
                String category = material.name().startsWith("COOKED_") || material.name().equals("BAKED_POTATO")
                        || material.name().equals("BREAD") || material.name().equals("COOKIE") || material.name().equals("CAKE")
                        || material.name().equals("PUMPKIN_PIE") ? "food-cooked" : "food-raw";
                double buy = Math.max(2.0, foodBuyPrice(material));
                products.add(new Product(category, material, buy, Math.max(1.0, Math.floor(buy * 0.15))));
            }
        }
        // Dynamically include survival building blocks only, not crafted variants or heads.
        // Existing curated products keep their configured prices; newly added farmable
        // blocks use conservative buy values and earn only 1 coin per full stack at most.
        for (Material material : Material.values()) {
            if (material.isBlock() && material.isItem() && !material.isAir() && isBuildingBlock(material) && !isCreativeOnlyBlock(material)
                    && products.stream().noneMatch(p -> p.material() == material && p.potionType() == null)) {
                double buy = blockBuyPrice(material);
                double sell = isFarmableCommonBlock(material) ? (1.0 / 64.0) : 0.0;
                products.add(new Product("blocks", material, buy, sell));
            }
        }
        // Keep planting materials in a dedicated Seeds & Farming menu.
        for (Material material : Material.values()) {
            if (isSeedOrPlantingMaterial(material)
                    && products.stream().noneMatch(p -> p.material() == material && p.potionType() == null)) {
                products.add(new Product("seeds", material, seedBuyPrice(material), seedSellPrice(material)));
            }
        }
        // Keep flowers and dyes out of Blocks and in their own tidy category.
        for (Material material : Material.values()) {
            if (isFlowerOrDyeMaterial(material) && material.isItem()
                    && products.stream().noneMatch(p -> p.material() == material && p.potionType() == null)) {
                products.add(new Product("flowers-dyes", material, flowerDyeBuyPrice(material), 0.0));
            }
        }
        // Common mob drops are sellable for small amounts; rare drops remain excluded.
        for (Material drop : List.of(Material.ROTTEN_FLESH, Material.BONE, Material.STRING, Material.FEATHER, Material.GUNPOWDER, Material.SPIDER_EYE, Material.LEATHER, Material.RABBIT_HIDE, Material.INK_SAC, Material.GLOW_INK_SAC, Material.PRISMARINE_SHARD, Material.PRISMARINE_CRYSTALS, Material.SLIME_BALL, Material.MAGMA_CREAM, Material.BLAZE_ROD, Material.GHAST_TEAR)) {
            if (products.stream().noneMatch(p -> p.material() == drop && p.potionType() == null)) products.add(new Product("essentials", drop, mobDropBuyPrice(drop), mobDropSellPrice(drop)));
        }
        products.add(new Product("essentials", Material.BONE_MEAL, 10.0, 5.0));
        products.add(new Product("essentials", Material.TNT, 70.0, 20.0));
        // Utility, lighting, crafting stations and bookshelves live in Essentials, not Blocks.
        for (Material material : Material.values()) {
            if (material != Material.FIREWORK_ROCKET && isEssentialsMaterial(material) && material.isItem()
                    && products.stream().noneMatch(p -> p.material() == material && p.potionType() == null)) {
                products.add(new Product("essentials", material, essentialsBuyPrice(material), 0.0));
            }
        }
        for (int power = 1; power <= 3; power++) {
            double price = switch (power) { case 1 -> 2.0; case 2 -> 3.0; default -> 5.0; };
            products.add(new Product("essentials", Material.FIREWORK_ROCKET, price, 0.0, null, power));
        }
        // Split the former combined equipment category into clear Armor and Tools menus.
        for (int i = 0; i < products.size(); i++) {
            Product product = products.get(i);
            if (!product.category().equals("equipment")) continue;
            String name = product.material().name();
            boolean armor = name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE")
                    || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS");
            products.set(i, new Product(armor ? "armor" : "tools", product.material(),
                    product.buy(), product.sell(), product.potionType(), product.fireworkPower()));
        }
        this.stockFile = new File(plugin.getDataFolder(), "shop-stock.yml");
        this.stockData = YamlConfiguration.loadConfiguration(stockFile);
        this.playerSellStockFile = new File(plugin.getDataFolder(), "player-sell-stock.yml");
        this.playerSellStockData = YamlConfiguration.loadConfiguration(playerSellStockFile);
        this.historyFile = new File(plugin.getDataFolder(), "shop-purchase-history.yml");
        this.historyData = YamlConfiguration.loadConfiguration(historyFile);
        this.bountyCooldownFile = new File(plugin.getDataFolder(), "bounty-cooldowns.yml");
        this.bountyCooldownData = YamlConfiguration.loadConfiguration(bountyCooldownFile);
        initializeStock();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, this::restockIfDue, 20L * 60L, 20L * 60L);
    }

    public void openMain(Player player) {
        // Compact 5-row shop hub: one search control, a tidy category grid,
        // and a single row of account/help actions. Keep icons simple and legible.
        Inventory inv = Bukkit.createInventory(null, 45, ChatColor.DARK_GREEN + "XZH SHOP");

        add(inv, 4, Material.SPYGLASS, "Search Items", "shop-search", "search",
                List.of(ChatColor.GRAY + "Find an item by name.",
                        ChatColor.YELLOW + "Click, then type in chat."));

        // Main categories: centered, evenly spaced rows.
        add(inv, 10, Material.IRON_INGOT, "Ores", "category", "ores");
        add(inv, 11, Material.BRICKS, "Blocks", "category", "blocks");
        add(inv, 12, Material.NETHERITE_CHESTPLATE, "Armor", "category", "armor");
        add(inv, 13, Material.NETHERITE_PICKAXE, "Tools", "category", "tools");
        add(inv, 14, Material.COOKED_BEEF, "Food", "category", "food");
        add(inv, 15, Material.WHEAT_SEEDS, "Seeds & Farming", "category", "seeds");
        add(inv, 16, Material.CRAFTING_TABLE, "Essentials", "category", "essentials");

        add(inv, 19, Material.ELYTRA, "Special Items", "category", "special");
        add(inv, 20, Material.POPPY, "Flowers & Dyes", "category", "flowers-dyes");
        add(inv, 21, Material.BEE_SPAWN_EGG, "Eggs & Heads", "category", "eggs-heads");
        add(inv, 22, Material.PALE_OAK_BOAT, "Travel", "category", "travel");
        add(inv, 23, Material.ENCHANTED_BOOK, "Enchantment Books", "books", "books");
        add(inv, 24, Material.EXPERIENCE_BOTTLE, "XP", "category", "xp");
        add(inv, 25, Material.POTION, "Potions", "category", "potions");

        add(inv, 28, Material.END_CRYSTAL, "Quest Packs", "quests", "quests");
        add(inv, 29, Material.NETHERITE_SWORD, "Bounty Hunt", "category", "bounty-maps");

        // Account tools share one compact row; no empty spacer row or decorative filler.
        ItemStack bankIcon = plugin.getPocketCoinManager().createCoin(1000, 1);
        ItemMeta bankMeta = bankIcon.getItemMeta();
        bankMeta.setDisplayName(ChatColor.WHITE + "XZH Bank");
        bankMeta.setLore(List.of(ChatColor.GRAY + "Open your bank account."));
        bankMeta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "bank");
        bankMeta.getPersistentDataContainer().set(materialKey, PersistentDataType.STRING, "bank");
        bankIcon.setItemMeta(bankMeta);
        inv.setItem(37, bankIcon);

        add(inv, 38, Material.PAPER, "Purchase History", "transition", "history",
                List.of(ChatColor.GRAY + "View your recent shop purchases."));
        add(inv, 39, Material.PLAYER_HEAD, "Players' Money", "players-money", "players-money");
        add(inv, 41, Material.SCULK_SENSOR, "Death Inventory", "deathinventory", "deathinventory");
        add(inv, 42, Material.BOOK, "Help", "help", "help");
        add(inv, 40, Material.CHEST, "Hand Sell", "sell-hand", "hand",
                List.of(ChatColor.GRAY + "Place items in the sell slots.",
                        ChatColor.GRAY + "Eligible items are totaled automatically.",
                        ChatColor.YELLOW + "Confirm to sell; other items are returned."));

        // Custom compact pixel-art icons from the companion resource pack.
        applyShopIcon(inv, 4, 2001);
        applyShopIcon(inv, 10, 2004);
        applyShopIcon(inv, 11, 2005);
        applyShopIcon(inv, 12, 2006);
        applyShopIcon(inv, 13, 2007);
        applyShopIcon(inv, 14, 2008);
        applyShopIcon(inv, 15, 2005);
        applyShopIcon(inv, 16, 2002);
        applyShopIcon(inv, 19, 2001);
        applyShopIcon(inv, 20, 2008);
        applyShopIcon(inv, 21, 2002);
        applyShopIcon(inv, 22, 2005);
        applyShopIcon(inv, 23, 2009);
        applyShopIcon(inv, 24, 2003);
        applyShopIcon(inv, 25, 2009);
        applyShopIcon(inv, 28, 2002);
        applyShopIcon(inv, 29, 2001);
        applyShopIcon(inv, 40, 2001);

        player.openInventory(inv);
    }

    private void applyShopIcon(Inventory inv, int slot, int customModelData) {
        ItemStack icon = inv.getItem(slot);
        if (icon == null) return;

        // Preserve GUI routing metadata while switching the icon to the
        // resource-pack carrier item (gold nugget).
        ItemMeta previous = icon.getItemMeta();
        String displayName = previous != null && previous.hasDisplayName() ? previous.getDisplayName() : null;
        List<String> lore = previous != null && previous.hasLore() ? previous.getLore() : null;
        String action = previous == null ? null : previous.getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
        String target = previous == null ? null : previous.getPersistentDataContainer().get(materialKey, PersistentDataType.STRING);

        icon.setType(Material.GOLD_NUGGET);
        ItemMeta meta = icon.getItemMeta();
        if (meta == null) return;
        if (displayName != null) meta.setDisplayName(displayName);
        if (lore != null) meta.setLore(lore);
        if (action != null) meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
        if (target != null) meta.getPersistentDataContainer().set(materialKey, PersistentDataType.STRING, target);
        meta.setCustomModelData(customModelData);
        icon.setItemMeta(meta);
        inv.setItem(slot, icon);
    }

    private void openOtherPlayersMoney(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, ChatColor.DARK_GREEN + "XZH SHOP: Players' Money");
        int slot = 0;
        for (Player target : Bukkit.getOnlinePlayers().stream()
                .sorted(java.util.Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER)).toList()) {
            if (slot >= 45) break;
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            org.bukkit.inventory.meta.SkullMeta meta = (org.bukkit.inventory.meta.SkullMeta) head.getItemMeta();
            meta.setOwningPlayer(target);
            meta.setDisplayName(ChatColor.YELLOW + target.getName());
            meta.setLore(List.of(
                    ChatColor.WHITE + "Pocket: " + ChatColor.YELLOW + money(plugin.getBalance(target.getUniqueId())) + " coins",
                    ChatColor.WHITE + "Bank: " + ChatColor.GREEN + money(plugin.getBankManager().getBalance(target.getUniqueId())) + " coins",
                    ChatColor.GRAY + "Hover to view current balances."
            ));
            meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "player-money-view");
            meta.getPersistentDataContainer().set(materialKey, PersistentDataType.STRING, target.getUniqueId().toString());
            head.setItemMeta(meta);
            inv.setItem(slot++, head);
        }
        if (slot == 0) add(inv, 22, Material.BARRIER, "No players online", "player-money-view", "none",
                List.of(ChatColor.GRAY + "There are currently no online players."));
        add(inv, 49, Material.ARROW, "Back to Shop", "back", "main");
        player.openInventory(inv);
    }

    private void openPurchaseHistory(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.DARK_GREEN + "XZH SHOP: Transition");
        List<String> entries = historyData.getStringList("players." + player.getUniqueId() + ".purchases");
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21};
        int shown = Math.min(entries.size(), slots.length);
        for (int i = 0; i < shown; i++) {
            String[] parts = entries.get(i).split("\\|", 4);
            if (parts.length != 4) continue;
            ItemStack entry = new ItemStack(Material.PAPER);
            ItemMeta meta = entry.getItemMeta();
            meta.setDisplayName(ChatColor.GOLD + parts[1]);
            meta.setLore(List.of(ChatColor.GRAY + "Quantity: " + parts[2],
                    ChatColor.YELLOW + "Spent: " + parts[3] + " coins",
                    ChatColor.DARK_GRAY + parts[0]));
            entry.setItemMeta(meta);
            inv.setItem(slots[i], entry);
        }
        if (shown == 0) {
            ItemStack empty = new ItemStack(Material.BOOK);
            ItemMeta meta = empty.getItemMeta();
            meta.setDisplayName(ChatColor.GRAY + "No purchases yet");
            meta.setLore(List.of(ChatColor.DARK_GRAY + "Your recent shop purchases will appear here."));
            empty.setItemMeta(meta);
            inv.setItem(13, empty);
        }
        add(inv, 22, Material.ARROW, "Back to Shop", "back", "main");
        player.openInventory(inv);
    }

    private void recordPurchase(Player player, String itemName, int quantity, double spent) {
        String path = "players." + player.getUniqueId() + ".purchases";
        List<String> entries = new ArrayList<>(historyData.getStringList(path));
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        entries.add(0, timestamp + "|" + itemName.replace("|", "/") + "|" + quantity + "|" + money(spent));
        if (entries.size() > 10) entries = new ArrayList<>(entries.subList(0, 10));
        historyData.set(path, entries);
        try {
            historyData.save(historyFile);
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not save shop purchase history: " + ex.getMessage());
        }
    }

    private void promptShopSearch(Player player) {
        pendingShopSearch.put(player.getUniqueId(), true);
        player.closeInventory();
        player.sendMessage(ChatColor.GOLD + "[XZH SHOP] " + ChatColor.WHITE + "Type an item name to search. Type " + ChatColor.YELLOW + "cancel" + ChatColor.WHITE + " to stop.");
    }

    @EventHandler
    public void onShopSearchChat(AsyncPlayerChatEvent event) {
        if (!pendingShopSearch.containsKey(event.getPlayer().getUniqueId())) return;
        event.setCancelled(true);
        String query = event.getMessage() == null ? "" : event.getMessage().trim();
        pendingShopSearch.remove(event.getPlayer().getUniqueId());
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            if (query.equalsIgnoreCase("cancel")) {
                player.sendMessage(ChatColor.GOLD + "[XZH SHOP] " + ChatColor.GRAY + "Search cancelled.");
                openMain(player);
                return;
            }
            if (query.isBlank()) {
                player.sendMessage(ChatColor.GOLD + "[XZH SHOP] " + ChatColor.RED + "Enter an item name to search.");
                promptShopSearch(player);
                return;
            }
            String searchTerm = query.length() > 48 ? query.substring(0, 48) : query;
            openSearchResults(player, searchTerm, 0);
        });
    }

    @EventHandler
    public void onShopSearchQuit(PlayerQuitEvent event) {
        pendingShopSearch.remove(event.getPlayer().getUniqueId());
    }

    private void openSearchResults(Player player, String query, int requestedPage) {
        String needle = query.toLowerCase(Locale.ROOT).replace('_', ' ').trim();
        List<Product> matches = products.stream()
                .filter(p -> {
                    String name = prettyProduct(p).toLowerCase(Locale.ROOT);
                    String material = p.material().name().toLowerCase(Locale.ROOT).replace('_', ' ');
                    String potion = p.potionType() == null ? "" : p.potionType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
                    return name.contains(needle) || material.contains(needle) || potion.contains(needle);
                })
                .sorted(java.util.Comparator.comparing((Product p) -> prettyProduct(p).toLowerCase(Locale.ROOT))
                        .thenComparing(p -> p.material().name())
                        .thenComparing(p -> p.potionType() == null ? "" : p.potionType().name()))
                .toList();
        int pages = Math.max(1, (matches.size() + 44) / 45);
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        Inventory inv = Bukkit.createInventory(null, 54, ChatColor.DARK_GREEN + "XZH SHOP: Search " + (page + 1));
        int start = page * 45;
        int end = Math.min(matches.size(), start + 45);
        for (int index = start; index < end; index++) {
            Product p = matches.get(index);
            ItemStack item = createStack(p, 1);
            if (p.category().equals("bounty-maps")) item.setType(Material.NETHERITE_SWORD);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(ChatColor.YELLOW + prettyProduct(p));
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.WHITE + "Buy: " + ChatColor.YELLOW + money(buyPrice(p)) + " coins each");
            lore.add(ChatColor.WHITE + "Sell: " + ChatColor.RED + money(sellPrice(p)) + " coins each");
            if (isFarmableSellItem(p.material())) lore.add(ChatColor.WHITE + "Your sell stock: " + ChatColor.AQUA + getSellStock(player, p.material()) + "/" + sellStockLimit(p.material()) + ChatColor.GRAY + " (personal 6h restock)");
            if (!p.category().equals("bounty-maps")) lore.add(ChatColor.WHITE + "Global buy stock: " + ChatColor.AQUA + getStock(p.material()) + "/" + stockLimit(p.material()));
            if (p.material() == Material.ELYTRA) lore.add(ChatColor.LIGHT_PURPLE + "Unlock: Visit the End first");
            lore.add(ChatColor.GRAY + "Click to choose quantity");
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "product");
            meta.getPersistentDataContainer().set(materialKey, PersistentDataType.STRING, p.material().name());
            if (p.potionType() != null) meta.getPersistentDataContainer().set(potionTypeKey, PersistentDataType.STRING, p.potionType().name());
            if (p.fireworkPower() > 0) meta.getPersistentDataContainer().set(fireworkPowerKey, PersistentDataType.INTEGER, p.fireworkPower());
            item.setItemMeta(meta);
            inv.setItem(index - start, item);
        }
        if (matches.isEmpty()) {
            add(inv, 22, Material.BARRIER, "No items found", "search-again", "search",
                    List.of(ChatColor.GRAY + "No shop items matched: " + query, ChatColor.YELLOW + "Click to search again."));
        }
        if (page > 0) add(inv, 45, Material.PAPER, "Previous page", "search-page", (page - 1) + "|" + query);
        add(inv, 48, Material.SPYGLASS, "Search again", "search-again", "search");
        add(inv, 49, Material.ARROW, "Back to Shop", "back", "main");
        if (page + 1 < pages) add(inv, 53, Material.PAPER, "Next page", "search-page", (page + 1) + "|" + query);
        player.openInventory(inv);
    }

    private void openCategory(Player player, String category) {
        openCategory(player, category, 0);
    }

    private void openCategory(Player player, String category, int requestedPage) {
        if (category.equals("travel")) { plugin.getBoatManager().open(player); return; }
        if (category.equals("food")) { openFoodMenu(player); return; }
        if (category.equals("xp")) { openXpMenu(player); return; }
        List<Product> list = products.stream().filter(p -> p.category().equals(category)).sorted(java.util.Comparator.comparingInt(this::productOrder).thenComparing(p -> p.material().name())).toList();
        int pages = Math.max(1, (list.size() + 44) / 45);
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        Inventory inv = Bukkit.createInventory(null, 54, ChatColor.DARK_GREEN + "XZH SHOP: " + category + " " + (page + 1));
        int start = page * 45;
        int end = Math.min(list.size(), start + 45);
        for (int index = start; index < end; index++) {
            Product p = list.get(index);
            int slot = index - start;
            ItemStack item = createStack(p, 1);
            if (p.category().equals("bounty-maps")) item.setType(Material.NETHERITE_SWORD);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(ChatColor.YELLOW + prettyProduct(p));
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.WHITE + "Buy: " + ChatColor.YELLOW + money(buyPrice(p)) + " coins each");
            lore.add(ChatColor.WHITE + "Sell: " + ChatColor.RED + money(sellPrice(p)) + " coins each");
            if (isFarmableSellItem(p.material())) lore.add(ChatColor.WHITE + "Your sell stock: " + ChatColor.AQUA + getSellStock(player, p.material()) + "/" + sellStockLimit(p.material()) + ChatColor.GRAY + " (personal 6h restock)");
            if (!p.category().equals("bounty-maps")) lore.add(ChatColor.WHITE + "Global buy stock: " + ChatColor.AQUA + getStock(p.material()) + "/" + stockLimit(p.material()));
            if (p.material() == Material.ELYTRA) lore.add(ChatColor.LIGHT_PURPLE + "Unlock: Visit the End first");
            lore.add(ChatColor.GRAY + "Click to choose quantity");
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "product");
            meta.getPersistentDataContainer().set(materialKey, PersistentDataType.STRING, p.material().name());
            if (p.potionType() != null) meta.getPersistentDataContainer().set(potionTypeKey, PersistentDataType.STRING, p.potionType().name());
            if (p.fireworkPower() > 0) meta.getPersistentDataContainer().set(fireworkPowerKey, PersistentDataType.INTEGER, p.fireworkPower());
            if (p.category().equals("bounty-maps")) {
                String tier = switch ((p.fireworkPower() - 1) % 3) { case 0 -> "common"; case 1 -> "rare"; default -> "legendary"; };
                String dimension = p.fireworkPower() >= 4 ? "nether" : "overworld";
                meta.setDisplayName(ChatColor.LIGHT_PURPLE + "XZH " + Character.toUpperCase(tier.charAt(0)) + tier.substring(1) + (dimension.equals("nether") ? " Nether" : "") + " Bounty Map");
                lore.add(ChatColor.GRAY + "Dimension: " + (dimension.equals("nether") ? "Nether" : "Overworld"));
                lore.add(ChatColor.GRAY + "Right-click to accept a " + tier + " bounty contract.");
                lore.add(ChatColor.GRAY + "Bow, arrows and tracking compass supplied.");
                lore.add(ChatColor.GRAY + "12-hour personal cooldown for this tier across both dimensions.");
                lore.add(ChatColor.GRAY + "Time limit: " + (tier.equals("common") ? "25 minutes" : tier.equals("rare") ? "45 minutes" : "2 hours"));
                lore.add(ChatColor.GRAY + "Hidden reward; possible profit bonus: 30–60%");
                meta.setLore(lore);
            }
            item.setItemMeta(meta);
            inv.setItem(slot, item);
        }
        if (page > 0) add(inv, 45, Material.PAPER, "Previous page", "category-page", category + ":" + (page - 1));
        add(inv, 49, Material.ARROW, "Back", "back", "main");
        if (page + 1 < pages) add(inv, 53, Material.PAPER, "Next page", "category-page", category + ":" + (page + 1));
        player.openInventory(inv);
    }

    private void openHandSellConfirm(Player player, ItemStack hand, double unitSell) {
        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.DARK_RED + "Confirm Sell Hand");
        ItemStack summary = hand.clone();
        ItemMeta meta = summary.getItemMeta();
        double total = unitSell * hand.getAmount();
        meta.setDisplayName(ChatColor.YELLOW + "Sell " + hand.getAmount() + " × " + pretty(hand.getType()));
        meta.setLore(List.of(ChatColor.WHITE + "Item: " + ChatColor.AQUA + pretty(hand.getType()),
                ChatColor.WHITE + "Quantity: " + ChatColor.AQUA + hand.getAmount(),
                ChatColor.WHITE + "Price each: " + ChatColor.YELLOW + money(unitSell) + " coins",
                ChatColor.WHITE + "Total payout: " + ChatColor.GREEN + money(total) + " coins",
                ChatColor.GRAY + "Hover to inspect the item; confirm to sell."));
        summary.setItemMeta(meta);
        inv.setItem(13, summary);
        add(inv, 11, Material.SNOWBALL, "Confirm sale", "sell-hand-confirm", hand.getType().name() + ":-:" + hand.getAmount());
        ItemStack confirm = inv.getItem(11);
        ItemMeta confirmMeta = confirm.getItemMeta();
        confirmMeta.getPersistentDataContainer().set(sellUnitPriceKey, PersistentDataType.DOUBLE, unitSell);
        confirm.setItemMeta(confirmMeta);
        add(inv, 15, Material.FIRE_CHARGE, "Cancel", "category-back", "main");
        add(inv, 22, Material.ARROW, "Back", "category-back", "main");
        player.openInventory(inv);
    }

    private void openXpMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.DARK_GREEN + "XZH SHOP: XP");
        int[] points = {25, 100, 250, 500};
        int[] prices = {8, 25, 55, 100};
        int[] slots = {10, 12, 14, 16};
        for (int i = 0; i < points.length; i++) {
            ItemStack bottle = customXpBottle(points[i]);
            ItemMeta meta = bottle.getItemMeta();
            meta.setDisplayName(ChatColor.AQUA + "XP Bottle — " + points[i] + " XP");
            meta.setLore(List.of(ChatColor.WHITE + "Contains: " + points[i] + " XP points",
                    ChatColor.WHITE + "Price: " + ChatColor.YELLOW + prices[i] + " coins",
                    ChatColor.GRAY + "Unstackable; right-click to consume.",
                    ChatColor.GREEN + "Click to review purchase"));
            meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "xp-buy");
            meta.getPersistentDataContainer().set(materialKey, PersistentDataType.STRING, Material.EXPERIENCE_BOTTLE.name());
            meta.getPersistentDataContainer().set(quantityKey, PersistentDataType.INTEGER, points[i]);
            meta.getPersistentDataContainer().set(xpPointsKey, PersistentDataType.INTEGER, prices[i]);
            bottle.setItemMeta(meta);
            inv.setItem(slots[i], bottle);
        }
        int xp = totalExperience(player);
        ItemStack sell = new ItemStack(Material.SNOWBALL);
        ItemMeta sm = sell.getItemMeta();
        sm.setDisplayName(ChatColor.GREEN + "Sell my XP bar");
        sm.setLore(List.of(ChatColor.WHITE + "Your current XP: " + xp + " points",
                ChatColor.WHITE + "Payout: " + ChatColor.YELLOW + money(xp * plugin.getConfig().getDouble("shop.xp-value-per-point", 0.1)) + " coins",
                ChatColor.GRAY + "Sells all current XP after confirmation."));
        sm.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "xp-sell");
        sm.getPersistentDataContainer().set(materialKey, PersistentDataType.STRING, Material.EXPERIENCE_BOTTLE.name());
        sell.setItemMeta(sm);
        inv.setItem(22, sell);
        add(inv, 26, Material.ARROW, "Back", "back", "main");
        player.openInventory(inv);
    }

    private ItemStack customXpBottle(int points) {
        ItemStack item = new ItemStack(Material.EXPERIENCE_BOTTLE);
        ItemMeta meta = item.getItemMeta();
        meta.setMaxStackSize(1);
        meta.setDisplayName(ChatColor.AQUA + "XP Bottle — " + points + " XP");
        meta.setLore(List.of(ChatColor.GRAY + "A sealed bottle containing " + points + " XP points."));
        meta.getPersistentDataContainer().set(xpBottleKey, PersistentDataType.BYTE, (byte) 1);
        meta.getPersistentDataContainer().set(xpPointsKey, PersistentDataType.INTEGER, points);
        item.setItemMeta(meta);
        return item;
    }

    private void openXpConfirm(Player player, boolean selling, int points, int price) {
        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.DARK_RED + "Confirm XP transaction");
        ItemStack summary = selling ? new ItemStack(Material.EXPERIENCE_BOTTLE) : customXpBottle(points);
        ItemMeta meta = summary.getItemMeta();
        meta.setDisplayName(ChatColor.WHITE + (selling ? "Sell your XP bar" : "Buy XP Bottle — " + points + " XP"));
        double payout = selling ? points * plugin.getConfig().getDouble("shop.xp-value-per-point", 0.1) : price;
        meta.setLore(List.of(ChatColor.WHITE + "XP amount: " + points + " points",
                ChatColor.WHITE + (selling ? "You receive: " : "You pay: ") + ChatColor.YELLOW + money(payout) + " coins",
                ChatColor.GRAY + (selling ? "Your current XP will be set to zero." : "Bottle is unstackable and consumed by right-click.")));
        summary.setItemMeta(meta);
        inv.setItem(13, summary);
        add(inv, 11, selling ? Material.SNOWBALL : Material.SLIME_BALL, "Confirm", selling ? "xp-sell-confirm" : "xp-buy-confirm", points + ":" + price);
        add(inv, 15, Material.FIRE_CHARGE, "Cancel", "category-back", "xp");
        add(inv, 22, Material.ARROW, "Back", "category-back", "xp");
        player.openInventory(inv);
    }

    private int totalExperience(Player player) {
        int level = player.getLevel();
        int total = 0;
        for (int i = 0; i < level; i++) total += xpToNextLevel(i);
        return total + Math.round(player.getExp() * xpToNextLevel(level));
    }

    private int xpToNextLevel(int level) {
        if (level <= 16) return 2 * level + 7;
        if (level <= 31) return 5 * level - 38;
        return 9 * level - 158;
    }

    /** Open the shop when an official XZH physical coin is right-clicked. */
    @EventHandler
    public void onPreventNetheriteTemplateDuplication(PrepareItemCraftEvent event) {
        ItemStack result = event.getInventory().getResult();
        if (result != null && result.getType() == Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE) {
            event.getInventory().setResult(new ItemStack(Material.AIR));
        }
    }

    @EventHandler
    public void onRightClickCoin(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack held = event.getItem();
        if (!plugin.getPocketCoinManager().isCoin(held)) return;

        // Only accept the four denominations minted by PocketCoinManager.
        ItemMeta meta = held.getItemMeta();
        Integer denomination = meta.getPersistentDataContainer().get(
                new NamespacedKey(plugin, "coin_denomination"), PersistentDataType.INTEGER);
        if (denomination == null || (denomination != 1 && denomination != 10
                && denomination != 100 && denomination != 1000)) return;

        // Bukkit fires an interaction event for each hand. If both hands hold coins,
        // let the main-hand event open the GUI once rather than reopening it from offhand.
        if (event.getHand() == org.bukkit.inventory.EquipmentSlot.OFF_HAND
                && plugin.getPocketCoinManager().isCoin(event.getPlayer().getInventory().getItemInMainHand())) return;

        event.setCancelled(true);
        openMain(event.getPlayer());
    }

    @EventHandler
    public void onUseXpBottle(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack item = event.getItem();
        if (item == null || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        if (!meta.getPersistentDataContainer().has(xpBottleKey, PersistentDataType.BYTE)) return;
        event.setCancelled(true);
        int points = meta.getPersistentDataContainer().getOrDefault(xpPointsKey, PersistentDataType.INTEGER, 0);
        if (points <= 0) return;
        if (item.getAmount() <= 1) event.getPlayer().getInventory().setItem(event.getHand(), null);
        else item.setAmount(item.getAmount() - 1);
        event.getPlayer().giveExp(points);
        event.getPlayer().updateInventory();
        plugin.tellPublic(event.getPlayer(), "Consumed an XP bottle and received " + points + " XP points.");
    }

    private void openFoodMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.DARK_GREEN + "XZH SHOP: food");
        add(inv, 11, Material.COOKED_BEEF, "Cooked Food", "category", "food-cooked");
        add(inv, 15, Material.BEEF, "Raw Food", "category", "food-raw");
        add(inv, 22, Material.ARROW, "Back", "back", "main");
        player.openInventory(inv);
    }

    private void openQuantity(Player player, Product p) {
        openQuantity(player, p, 1);
    }

    private void openQuantity(Player player, Product p, int requestedQuantity) {
        int max = Math.max(1, Math.min(64, getStock(p.material())));
        int quantity = Math.max(1, Math.min(max, requestedQuantity));
        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.DARK_GREEN + "Choose quantity");
        ItemStack minus64 = quantityControl(p, quantity, Material.COPPER_NUGGET, "−64", "quantity-minus-64");
        ItemStack minus10 = quantityControl(p, quantity, Material.COPPER_NUGGET, "−10", "quantity-minus-10");
        ItemStack minus1 = quantityControl(p, quantity, Material.COPPER_NUGGET, "−1", "quantity-minus");
        ItemStack plus1 = quantityControl(p, quantity, Material.GHAST_TEAR, "+1", "quantity-plus");
        ItemStack plus10 = quantityControl(p, quantity, Material.GHAST_TEAR, "+10", "quantity-plus-10");
        ItemStack plus64 = quantityControl(p, quantity, Material.GHAST_TEAR, "+64", "quantity-plus-64");
        ItemStack summary = createStack(p, quantity);
        ItemMeta summaryMeta = summary.getItemMeta();
        summaryMeta.setDisplayName(ChatColor.YELLOW + "Selected: " + quantity + " × " + prettyProduct(p));
        summaryMeta.setLore(List.of(
                ChatColor.WHITE + "Unit price: " + ChatColor.YELLOW + money(buyPrice(p)) + " coins",
                ChatColor.WHITE + "Total: " + ChatColor.GREEN + money(buyPrice(p) * quantity) + " coins",
                ChatColor.WHITE + "Available stock: " + ChatColor.AQUA + getStock(p.material())));
        summary.setItemMeta(summaryMeta);
        inv.setItem(9, minus64);
        inv.setItem(10, minus10);
        inv.setItem(11, minus1);
        inv.setItem(13, summary);
        inv.setItem(15, plus1);
        inv.setItem(16, plus10);
        inv.setItem(17, plus64);
        ItemStack buy = quantityControl(p, quantity, Material.SLIME_BALL, "Buy " + quantity, "quantity-buy");
        ItemMeta buyMeta = buy.getItemMeta();
        buyMeta.setLore(List.of(ChatColor.WHITE + "Total: " + ChatColor.GREEN + money(buyPrice(p) * quantity) + " coins",
                ChatColor.GRAY + "Confirm your purchase on the next screen."));
        buy.setItemMeta(buyMeta);
        inv.setItem(22, buy);
        if (!p.category().equals("bounty-maps")) {
            ItemStack sell = new ItemStack(Material.SNOWBALL);
            ItemMeta sm = sell.getItemMeta();
            sm.setDisplayName(ChatColor.RED + "Sell all of this item in inventory");
            sm.setLore(List.of(ChatColor.WHITE + "Value: " + ChatColor.RED + money(sellPrice(p)) + " coins each",
                    ChatColor.GRAY + "You will confirm before selling."));
            sm.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "confirm-sell");
            sm.getPersistentDataContainer().set(materialKey, PersistentDataType.STRING, p.material().name());
            if (p.potionType() != null) sm.getPersistentDataContainer().set(potionTypeKey, PersistentDataType.STRING, p.potionType().name());
            if (p.fireworkPower() > 0) sm.getPersistentDataContainer().set(fireworkPowerKey, PersistentDataType.INTEGER, p.fireworkPower());
            sell.setItemMeta(sm);
            inv.setItem(4, sell);
        }
        add(inv, 26, Material.ARROW, "Back", "category-back", p.category());
        player.openInventory(inv);
    }

    private ItemStack quantityControl(Product p, int quantity, Material icon, String title, String action) {
        ItemStack item = new ItemStack(icon);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.WHITE + title);
        meta.setLore(List.of(ChatColor.GRAY + "Selected quantity: " + quantity));
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
        meta.getPersistentDataContainer().set(materialKey, PersistentDataType.STRING, p.material().name());
        meta.getPersistentDataContainer().set(quantityKey, PersistentDataType.INTEGER, quantity);
        if (p.potionType() != null) meta.getPersistentDataContainer().set(potionTypeKey, PersistentDataType.STRING, p.potionType().name());
        if (p.fireworkPower() > 0) meta.getPersistentDataContainer().set(fireworkPowerKey, PersistentDataType.INTEGER, p.fireworkPower());
        item.setItemMeta(meta);
        return item;
    }

    private void openConfirm(Player player, Material material, int quantity, boolean buying) {
        openConfirm(player, material, quantity, buying, false);
    }

    private void openConfirm(Player player, Product product, int quantity, boolean buying) {
        openConfirmProduct(player, product.material(), quantity, buying, false, product);
    }

    private void openConfirm(Player player, Product product, int quantity, boolean buying, boolean handOnly) {
        openConfirmProduct(player, product.material(), quantity, buying, handOnly, product);
    }

    private void openConfirm(Player player, Material material, int quantity, boolean buying, boolean handOnly) {
        openConfirmProduct(player, material, quantity, buying, handOnly, product(material));
    }

    private void openConfirmProduct(Player player, Material material, int quantity, boolean buying, boolean handOnly, Product supplied) {
        Product p = supplied;
        if (p == null && !buying) p = new Product("misc", material, 0.0, defaultSellPrice());
        if (p == null) return;
        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.DARK_RED + "Confirm transaction");
        double total = (buying ? buyPrice(p) : sellPrice(p)) * quantity;
        ItemStack summary = createStack(p, Math.min(64, quantity));
        ItemMeta sm = summary.getItemMeta();
        sm.setDisplayName(ChatColor.WHITE + (buying ? "Buy " : "Sell ") + quantity + " × " + pretty(material)
                + (p.potionType() == null ? "" : " - " + prettyPotion(p.potionType())));
        sm.setLore(List.of(ChatColor.WHITE + "Total: " + (buying ? ChatColor.YELLOW : ChatColor.RED) + money(total) + " coins"));
        summary.setItemMeta(sm);
        inv.setItem(13, summary);
        String txValue = material.name() + ":" + (p.potionType() != null ? p.potionType().name() : (p.fireworkPower() > 0 ? "POWER_" + p.fireworkPower() : "-")) + ":" + quantity;
        add(inv, 11, buying ? Material.SLIME_BALL : Material.SNOWBALL,
                buying ? "Confirm Buy" : "Confirm Sell", buying ? "buy" : (handOnly ? "sell-hand-confirm" : "sell"), txValue);
        ItemMeta confirmMeta = inv.getItem(11).getItemMeta();
        if (p.potionType() != null) confirmMeta.getPersistentDataContainer().set(potionTypeKey, PersistentDataType.STRING, p.potionType().name());
        if (p.fireworkPower() > 0) confirmMeta.getPersistentDataContainer().set(fireworkPowerKey, PersistentDataType.INTEGER, p.fireworkPower());
        inv.getItem(11).setItemMeta(confirmMeta);
        add(inv, 15, Material.FIRE_CHARGE, "Cancel", "category-back", handOnly ? "main" : p.category());
        add(inv, 22, Material.ARROW, "Back", "category-back", handOnly ? "main" : p.category());
        player.openInventory(inv);
    }

    private void add(Inventory inv, int slot, Material material, String title, String action, String value) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.WHITE + title);
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
        meta.getPersistentDataContainer().set(materialKey, PersistentDataType.STRING, value);
        item.setItemMeta(meta);
        inv.setItem(slot, item);
    }

    private void add(Inventory inv, int slot, Material material, String title, String action, String value, List<String> lore) {
        add(inv, slot, material, title, action, value);
        ItemStack item = inv.getItem(slot);
        if (item == null) return;
        ItemMeta meta = item.getItemMeta();
        meta.setLore(lore);
        item.setItemMeta(meta);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = ChatColor.stripColor(event.getView().getTitle());
        if (title == null) return;
        if (title.equals("XZH Hand Sell")) {
            handleHandSellClick(event, player);
            return;
        }
        if (!title.startsWith("XZH SHOP")
                && !title.equals("Choose quantity") && !title.equals("Confirm transaction")
                && !title.equals("Confirm XP transaction") && !title.equals("Confirm Sell Hand")) return;
        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        ItemMeta meta = clicked.getItemMeta();
        String action = meta.getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
        String value = meta.getPersistentDataContainer().get(materialKey, PersistentDataType.STRING);
        if (action == null || value == null) return;
        switch (action) {
            case "category" -> openCategory(player, value);
            case "shop-search", "search-again" -> promptShopSearch(player);
            case "search-page" -> {
                String[] bits = value.split("\\|", 2);
                if (bits.length == 2) {
                    try { openSearchResults(player, bits[1], Integer.parseInt(bits[0])); }
                    catch (NumberFormatException ignored) { openSearchResults(player, bits[1], 0); }
                }
            }
            case "books" -> plugin.getBookShopManager().open(player);
            case "quests" -> plugin.getQuestManager().open(player);
            case "deathinventory" -> plugin.getDeathRecoveryManager().open(player);
            case "transition" -> openPurchaseHistory(player);
            case "players-money" -> openOtherPlayersMoney(player);
            case "player-money-view" -> { /* view-only player balance head */ }
            case "balance" -> plugin.tellPublic(player, "Pocket: " + money(plugin.getBalance(player.getUniqueId())) + " coins; Bank: " + money(plugin.getBankManager().getBalance(player.getUniqueId())) + " coins.");
            case "bank" -> plugin.getBankManager().open(player);
            case "help" -> {
                plugin.tellPublic(player, "/shop, /bank, /balance, /pay, /quests, /deathinventory, /books");
                player.closeInventory();
            }
            case "category-page" -> {
                String[] bits = value.split(":");
                if (bits.length == 2) {
                    try { openCategory(player, bits[0], Integer.parseInt(bits[1])); }
                    catch (NumberFormatException ignored) { openCategory(player, bits[0]); }
                }
            }
            case "xp-buy" -> {
                Integer points = meta.getPersistentDataContainer().get(quantityKey, PersistentDataType.INTEGER);
                Integer price = meta.getPersistentDataContainer().get(xpPointsKey, PersistentDataType.INTEGER);
                if (points != null && price != null) openXpConfirm(player, false, points, price);
            }
            case "xp-sell" -> openXpConfirm(player, true, totalExperience(player), 0);
            case "xp-buy-confirm", "xp-sell-confirm" -> {
                String[] parts = value.split(":");
                if (parts.length != 2) return;
                int points; int price;
                try { points = Integer.parseInt(parts[0]); price = Integer.parseInt(parts[1]); } catch (NumberFormatException ex) { return; }
                if (action.equals("xp-buy-confirm")) {
                    if (plugin.getLoanManager().isShopPurchaseRestricted(player.getUniqueId())) { plugin.tellPublic(player, "Your loan is overdue. Purchases are locked."); return; }
                    if (plugin.getCombinedBalance(player.getUniqueId()) < price || !plugin.spendAvailableFunds(player.getUniqueId(), price)) { plugin.tellPublic(player, "Not enough coins."); return; }
                    java.util.Map<Integer, ItemStack> leftovers = player.getInventory().addItem(customXpBottle(points));
                    if (!leftovers.isEmpty()) { plugin.setBalance(player.getUniqueId(), plugin.getBalance(player.getUniqueId()) + price); plugin.tellPublic(player, "Inventory full; purchase cancelled and refunded."); }
                    else {
                        recordPurchase(player, "XP Bottle (" + points + " XP)", 1, price);
                        plugin.tellPublic(player, "Bought an XP bottle containing " + points + " XP for " + price + " coins.");
                    }
                } else {
                    int actual = totalExperience(player);
                    if (actual <= 0 || actual != points) { plugin.tellPublic(player, "Your XP changed; reopen the XP menu and confirm again."); openXpMenu(player); return; }
                    double payout = actual * plugin.getConfig().getDouble("shop.xp-value-per-point", 0.1);
                    player.setExp(0f); player.setLevel(0); player.setTotalExperience(0);
                    plugin.setBalance(player.getUniqueId(), plugin.getBalance(player.getUniqueId()) + payout);
                    plugin.tellPublic(player, "Sold " + actual + " XP points for " + money(payout) + " coins.");
                }
                player.closeInventory();
            }
            case "product" -> {
                Product selected = productFromMeta(meta);
                if (selected != null) openQuantity(player, selected);
            }
            case "quantity-minus", "quantity-plus", "quantity-minus-10", "quantity-minus-64",
                    "quantity-plus-10", "quantity-plus-64", "quantity-buy" -> {
                Product selected = productFromMeta(meta);
                Integer selectedQuantity = meta.getPersistentDataContainer().get(quantityKey, PersistentDataType.INTEGER);
                if (selected == null || selectedQuantity == null) return;
                if (action.equals("quantity-buy")) {
                    openConfirm(player, selected, selectedQuantity, true);
                } else {
                    int delta = switch (action) {
                        case "quantity-minus-64" -> -64;
                        case "quantity-minus-10" -> -10;
                        case "quantity-plus-10" -> 10;
                        case "quantity-plus-64" -> 64;
                        case "quantity-plus" -> 1;
                        default -> -1;
                    };
                    openQuantity(player, selected, selectedQuantity + delta);
                }
            }
            case "back" -> openMain(player);
            case "category-back" -> {
                if (value.equals("main")) openMain(player);
                else openCategory(player, value);
            }
            case "confirm-buy" -> {
                Material material = parseMaterial(value);
                Integer quantity = meta.getPersistentDataContainer().get(quantityKey, PersistentDataType.INTEGER);
                Product selected = productFromMeta(meta);
                if (quantity != null) {
                    if (selected != null) openConfirm(player, selected, quantity, true);
                    else if (material != null) openConfirm(player, material, quantity, true);
                }
            }
            case "confirm-sell" -> {
                Material material = parseMaterial(value);
                Product selected = productFromMeta(meta);
                if (material != null) {
                    int amount = selected != null && selected.potionType() != null
                            ? countPotion(player, selected) : count(player, material);
                    if (amount < 1) plugin.tellPublic(player, "You don't have that item to sell.");
                    else if (selected != null && selected.potionType() != null) openConfirm(player, selected, amount, false);
                    else openConfirm(player, material, amount, false);
                }
            }
            case "buy", "sell", "sell-hand-confirm" -> {
                String[] bits = value.split(":");
                if (bits.length != 2 && bits.length != 3) return;
                Material material = parseMaterial(bits[0]);
                String potionTypeName = bits.length == 3 ? bits[1] : "-";
                int quantity;
                try { quantity = Integer.parseInt(bits[bits.length - 1]); } catch (NumberFormatException ex) { return; }
                Product p = material == null ? null : (potionTypeName.startsWith("POWER_")
                        ? products.stream().filter(candidate -> candidate.material() == material && candidate.fireworkPower() == Integer.parseInt(potionTypeName.substring(6))).findFirst().orElse(null)
                        : (potionTypeName.equals("-") ? product(material) : potionProduct(material, potionTypeName)));
                double handUnitPrice = meta.getPersistentDataContainer().getOrDefault(sellUnitPriceKey, PersistentDataType.DOUBLE, defaultSellPrice());
                if (p == null && !action.equals("buy") && material != null)
                    p = new Product("misc", material, 0.0, action.equals("sell-hand-confirm") ? handUnitPrice : defaultSellPrice());
                if (p == null || quantity < 1) return;
                if (!action.equals("buy") && isUnsellableExploitItem(material)
                        && material != Material.ENCHANTED_GOLDEN_APPLE
                        && material != Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE
                        && material != Material.TRIDENT && material != Material.TOTEM_OF_UNDYING) {
                    plugin.tellPublic(player, "This item cannot be sold to the XZH shop because it is exploit-prone or intended to remain rare.");
                    player.closeInventory();
                    return;
                }
                double total = (action.equals("buy") ? buyPrice(p) : sellPrice(p)) * quantity;
                if (action.equals("buy") && material == Material.ELYTRA
                        && plugin.getConfig().getBoolean("restrictions.elytra-requires-end-visit", true)
                        && !hasVisitedEnd(player)) {
                    plugin.tellPublic(player, "Elytra purchases unlock after you visit the End.");
                    player.closeInventory();
                    return;
                }
                if (action.equals("buy")) {
                    if (p.category().equals("bounty-maps")) {
                        if (quantity != 1) { plugin.tellPublic(player, "Bounty maps are limited to one per purchase so each map has its own 12-hour cooldown."); player.closeInventory(); return; }
                        if (isBountyOnCooldown(player, p)) { player.closeInventory(); return; }
                        int emptyHotbar = 0;
                        for (int hotbar = 0; hotbar < 9; hotbar++) {
                            ItemStack hotbarItem = player.getInventory().getItem(hotbar);
                            if (hotbarItem == null || hotbarItem.getType().isAir()) emptyHotbar++;
                        }
                        if (emptyHotbar < 3) {
                            plugin.tellPublic(player, "Bounty map purchase blocked: keep at least 3 empty hotbar slots for the map and mission equipment.");
                            player.closeInventory();
                            return;
                        }
                    }
                    if (!p.category().equals("bounty-maps") && getStock(material) < quantity) {
                        plugin.tellPublic(player, "Not enough global stock. Available: " + getStock(material) + ".");
                        player.closeInventory();
                        return;
                    }
                    if (plugin.getLoanManager().isShopPurchaseRestricted(player.getUniqueId())) {
                        plugin.tellPublic(player, "Your loan is overdue. Shop purchases are locked until you repay it with /loan repay; selling is still allowed.");
                        player.closeInventory();
                        return;
                    }
                    if (plugin.getCombinedBalance(player.getUniqueId()) + 1e-9 < total) {
                        plugin.tellPublic(player, "Not enough coins.");
                        player.closeInventory();
                        return;
                    }
                    java.util.Map<Integer, ItemStack> leftovers;
                    if (p.category().equals("bounty-maps")) {
                        int emptySlot = -1;
                        for (int hotbar = 0; hotbar < 9; hotbar++) {
                            ItemStack hotbarItem = player.getInventory().getItem(hotbar);
                            if (hotbarItem == null || hotbarItem.getType().isAir()) { emptySlot = hotbar; break; }
                        }
                        if (emptySlot < 0) {
                            plugin.tellPublic(player, "Bounty map purchase blocked: no empty hotbar slot.");
                            player.closeInventory();
                            return;
                        }
                        player.getInventory().setItem(emptySlot, createStack(p, quantity));
                        leftovers = java.util.Map.of();
                    } else {
                        leftovers = player.getInventory().addItem(createStack(p, quantity));
                    }
                    int notGiven = leftovers.values().stream().mapToInt(ItemStack::getAmount).sum();
                    int delivered = quantity - notGiven;
                    if (delivered <= 0) {
                        plugin.tellPublic(player, "Your inventory is full.");
                        player.closeInventory();
                        return;
                    }
                    double charged = buyPrice(p) * delivered;
                    if (!p.category().equals("bounty-maps")) setStock(material, getStock(material) - delivered);
                    if (!plugin.spendAvailableFunds(player.getUniqueId(), charged)) {
                        // Return the delivered items and stock if a concurrent economy action changed the balance.
                        player.getInventory().removeItem(createStack(p, delivered));
                        if (!p.category().equals("bounty-maps")) setStock(material, getStock(material) + delivered);
                        plugin.tellPublic(player, "Purchase cancelled: your available funds changed.");
                        player.closeInventory();
                        return;
                    }
                    recordPurchase(player, prettyProduct(p), delivered, charged);
                    if (p.category().equals("bounty-maps")) startBountyCooldown(player, p);
                    plugin.tellPublic(player, "Bought " + delivered + " × " + prettyProduct(p) + " for " + money(charged) + " coins.");
                    if (p.category().equals("bounty-maps")) {
                        String tier = switch ((p.fireworkPower() - 1) % 3) { case 0 -> "Common"; case 1 -> "Rare"; default -> "Legendary"; };
                        String dimension = p.fireworkPower() >= 4 ? " Nether" : "";
                        plugin.tellPublic(player, "§d§lXZH BOUNTY HUNT §8» §fRight-click your " + tier + dimension + " map to start the contract.");
                        plugin.tellPublic(player, "§e➤ This tier is now on cooldown for 12 hours in both dimensions.");
                        plugin.tellPublic(player, "§e➤ Bow, arrows and a tracking compass are supplied when the contract starts.");
                    }
                    plugin.announceSpecialPurchase(player, material, delivered, charged);
                } else {
                    if (isFarmableSellItem(material)) {
                        int availableSellStock = getSellStock(player, material);
                        if (availableSellStock <= 0) {
                            plugin.tellPublic(player, "You have used all your sell stock for " + pretty(material) + ". Your personal stock refills every 12 hours.");
                            player.closeInventory();
                            return;
                        }
                        if (quantity > availableSellStock) {
                            quantity = availableSellStock;
                            plugin.tellPublic(player, "Your personal sell limit applies. Selling only " + quantity + " item(s); your remaining stock refills every 12 hours.");
                        }
                    }
                    if (sellPrice(p) * quantity < 1) {
                        plugin.tellPublic(player, "Items worth less than 1 coin cannot be sold.");
                        player.closeInventory();
                        return;
                    }
                    int sold;
                    if (action.equals("sell-hand-confirm")) {
                        ItemStack held = player.getInventory().getItemInMainHand();
                        if (plugin.getPocketCoinManager().isCoin(held)) {
                            plugin.tellPublic(player, "Physical pocket coins cannot be sold as shop items.");
                            player.closeInventory();
                            return;
                        }
                        if (held.getType() != material || held.getAmount() < quantity
                                || (p.potionType() != null && !matchesPotion(held, p))) {
                            plugin.tellPublic(player, "Your held item changed; nothing was sold.");
                            player.closeInventory();
                            return;
                        }
                        sold = quantity;
                        int left = held.getAmount() - quantity;
                        if (left <= 0) player.getInventory().setItemInMainHand(null);
                        else held.setAmount(left);
                    } else {
                        sold = p.potionType() != null ? removePotion(player, p, quantity) : remove(player, material, quantity);
                    }
                    if (sold < 1) {
                        plugin.tellPublic(player, "No matching items found.");
                        player.closeInventory();
                        return;
                    }
                    double earned = sellPrice(p) * sold;
                    consumeSellStock(player, material, sold);
                    plugin.setBalance(player.getUniqueId(), plugin.getBalance(player.getUniqueId()) + earned);
                    plugin.tellPublic(player, "Sold " + sold + " × " + prettyProduct(p) + " for " + money(earned) + " coins.");
                }
                player.closeInventory();
            }
            case "sell-hand" -> openHandSell(player);
        }
    }


    private boolean isBountyOnCooldown(Player player, Product product) {
        String tier = switch ((product.fireworkPower() - 1) % 3) { case 0 -> "common"; case 1 -> "rare"; default -> "legendary"; };
        String dimension = product.fireworkPower() >= 4 ? "nether" : "overworld";
        String path = "players." + player.getUniqueId() + "." + tier;
        long left = bountyCooldownData.getLong(path, 0L) - System.currentTimeMillis();
        if (left <= 0L) return false;
        long hours = left / 3_600_000L;
        long minutes = (left % 3_600_000L + 59_999L) / 60_000L;
        plugin.tellPublic(player, "You can buy another " + tier + " bounty in " + hours + "h " + minutes + "m. This cooldown applies in both dimensions.");
        return true;
    }

    private void startBountyCooldown(Player player, Product product) {
        String tier = switch ((product.fireworkPower() - 1) % 3) { case 0 -> "common"; case 1 -> "rare"; default -> "legendary"; };
        String dimension = product.fireworkPower() >= 4 ? "nether" : "overworld";
        bountyCooldownData.set("players." + player.getUniqueId() + "." + tier,
                System.currentTimeMillis() + 12L * 60L * 60L * 1000L);
        try { bountyCooldownData.save(bountyCooldownFile); }
        catch (IOException ex) { plugin.getLogger().warning("Could not save bounty cooldowns: " + ex.getMessage()); }
    }

    private void openHandSell(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, ChatColor.DARK_GREEN + "XZH Hand Sell");
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        fillerMeta.setDisplayName(ChatColor.DARK_GRAY + "Place items in the slots above");
        filler.setItemMeta(fillerMeta);
        for (int slot = 45; slot < 54; slot++) inv.setItem(slot, filler.clone());
        add(inv, 45, Material.ARROW, "Back to Shop / Return Items", "hand-sell-cancel", "cancel",
                List.of(ChatColor.GRAY + "Return placed items and go back to the shop."));
        add(inv, 49, Material.GOLD_INGOT, "Total: 0 coins", "hand-sell-total", "total",
                List.of(ChatColor.GRAY + "Eligible items are counted automatically."));
        add(inv, 53, Material.SNOWBALL, "Confirm Sell", "hand-sell-confirm", "confirm",
                List.of(ChatColor.GRAY + "Sell eligible items and return everything else."));
        player.openInventory(inv);
        updateHandSellMenu(player);
    }

    private void handleHandSellClick(InventoryClickEvent event, Player player) {
        Inventory top = event.getView().getTopInventory();
        int raw = event.getRawSlot();
        if (raw < 0) return;
        if (raw < top.getSize() && raw >= 45) {
            event.setCancelled(true);
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || !clicked.hasItemMeta()) return;
            String action = clicked.getItemMeta().getPersistentDataContainer()
                    .get(actionKey, PersistentDataType.STRING);
            if ("hand-sell-cancel".equals(action)) {
                player.closeInventory();
            } else if ("hand-sell-confirm".equals(action)) {
                confirmHandSell(player, top);
            }
            return;
        }
        event.setCancelled(false);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && "XZH Hand Sell".equals(ChatColor.stripColor(player.getOpenInventory().getTitle()))
                    && player.getOpenInventory().getTopInventory() == top) updateHandSellMenu(player);
        });
    }

    @EventHandler
    public void onHandSellDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!"XZH Hand Sell".equals(ChatColor.stripColor(event.getView().getTitle()))) return;
        for (int raw : event.getRawSlots()) {
            if (raw >= 45 && raw < event.getView().getTopInventory().getSize()) {
                event.setCancelled(true);
                return;
            }
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && "XZH Hand Sell".equals(ChatColor.stripColor(player.getOpenInventory().getTitle()))) {
                updateHandSellMenu(player);
            }
        });
    }

    @EventHandler
    public void onHandSellClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!"XZH Hand Sell".equals(ChatColor.stripColor(event.getView().getTitle()))) return;
        Inventory inv = event.getInventory();
        for (int slot = 0; slot < 45; slot++) {
            ItemStack item = inv.getItem(slot);
            if (item == null || item.getType().isAir()) continue;
            inv.setItem(slot, null);
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
            for (ItemStack leftover : leftovers.values()) player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
    }

    private void updateHandSellMenu(Player player) {
        if (!"XZH Hand Sell".equals(ChatColor.stripColor(player.getOpenInventory().getTitle()))) return;
        Inventory inv = player.getOpenInventory().getTopInventory();
        Map<Integer, Integer> sellAmounts = handSellableAmounts(player, inv);
        double total = 0.0;
        int count = 0;
        for (Map.Entry<Integer, Integer> entry : sellAmounts.entrySet()) {
            ItemStack stack = inv.getItem(entry.getKey());
            if (stack == null) continue;
            total += handSellUnitPrice(stack) * entry.getValue();
            count += entry.getValue();
        }
        ItemStack display = new ItemStack(Material.GOLD_INGOT);
        ItemMeta meta = display.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "Total: " + money(total) + " coins");
        meta.setLore(List.of(ChatColor.WHITE + "Eligible quantity: " + count,
                ChatColor.GRAY + "Items with no sell price or exhausted stock are returned.",
                ChatColor.GRAY + "Nothing sells until you confirm."));
        display.setItemMeta(meta);
        inv.setItem(49, display);
    }

    private Map<Integer, Integer> handSellableAmounts(Player player, Inventory inv) {
        Map<Integer, Integer> amounts = new HashMap<>();
        Map<Material, Integer> reservedStock = new HashMap<>();
        for (int slot = 0; slot < 45; slot++) {
            ItemStack stack = inv.getItem(slot);
            if (stack == null || stack.getType().isAir() || plugin.getPocketCoinManager().isCoin(stack)) continue;
            Material material = stack.getType();
            if (isUnsellableExploitItem(material) && material != Material.ELYTRA
                    && material != Material.TRIDENT && material != Material.TOTEM_OF_UNDYING) continue;
            double unitPrice = handSellUnitPrice(stack);
            if (unitPrice <= 0.0) continue;
            int allowed = stack.getAmount();
            if (isFarmableSellItem(material)) {
                int alreadyReserved = reservedStock.getOrDefault(material, 0);
                int available = Math.max(0, getSellStock(player, material) - alreadyReserved);
                allowed = Math.min(allowed, available);
                if (allowed <= 0) continue;
                reservedStock.put(material, alreadyReserved + allowed);
            }
            amounts.put(slot, allowed);
        }
        return amounts;
    }

    private double handSellUnitPrice(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return 0.0;
        Product p = productForStack(stack);
        if (p != null) return sellPrice(p);
        double configured = plugin.getConfig().getDouble(
                "shop.prices." + stack.getType().name().toLowerCase(Locale.ROOT) + ".sell", defaultSellPrice());
        return validPrice(configured) ? configured : defaultSellPrice();
    }

    private void confirmHandSell(Player player, Inventory inv) {
        Map<Integer, Integer> sellAmounts = handSellableAmounts(player, inv);
        double total = 0.0;
        int count = 0;
        for (Map.Entry<Integer, Integer> entry : sellAmounts.entrySet()) {
            ItemStack stack = inv.getItem(entry.getKey());
            if (stack == null) continue;
            total += handSellUnitPrice(stack) * entry.getValue();
            count += entry.getValue();
        }
        if (count <= 0 || total < 1.0) {
            plugin.tellPublic(player, "No eligible items worth at least 1 coin are available to sell. Your items will be returned.");
            player.closeInventory();
            return;
        }
        for (Map.Entry<Integer, Integer> entry : sellAmounts.entrySet()) {
            ItemStack stack = inv.getItem(entry.getKey());
            if (stack == null) continue;
            int sold = entry.getValue();
            if (isFarmableSellItem(stack.getType())) consumeSellStock(player, stack.getType(), sold);
            int remaining = stack.getAmount() - sold;
            if (remaining <= 0) inv.setItem(entry.getKey(), null);
            else stack.setAmount(remaining);
        }
        plugin.setBalance(player.getUniqueId(), plugin.getBalance(player.getUniqueId()) + total);
        plugin.tellPublic(player, "Sold " + count + " eligible item(s) for " + money(total) + " coins. Unsellable items were returned to your inventory.");
        player.closeInventory();
    }


    private void initializeStock() {
        boolean changed = false;
        for (Product p : products) {
            String key = "items." + p.material().name().toLowerCase(Locale.ROOT);
            if (!stockData.contains(key)) {
                stockData.set(key, stockLimit(p.material()));
                changed = true;
            }
        }
        if (!stockData.contains("last-restock-date")) {
            stockData.set("last-restock-date", LocalDate.now().toString());
            changed = true;
        }
        if (changed) saveStock();
        restockIfDue();
    }

    private int stockLimit(Material material) {
        int fallback = material == Material.ELYTRA ? 3
                : material == Material.NETHERITE_BLOCK ? 5
                : material == Material.DIAMOND_PICKAXE ? 30
                : material == Material.IRON_PICKAXE ? 100 : 1000;
        return Math.max(0, plugin.getConfig().getInt(
                "shop.stock-limits." + material.name().toLowerCase(Locale.ROOT), fallback));
    }

    private int getStock(Material material) {
        restockIfDue();
        return Math.max(0, Math.min(stockLimit(material), stockData.getInt("items." + material.name().toLowerCase(Locale.ROOT), stockLimit(material))));
    }

    private boolean isFarmableSellItem(Material material) {
        if (material == null) return false;
        String n = material.name();
        return material.isEdible()
                || isSeedOrPlantingMaterial(material)
                || isFarmableCommonBlock(material)
                || isMobDrop(material)
                || n.equals("DRIED_KELP") || n.equals("DRIED_KELP_BLOCK")
                || n.equals("SUGAR_CANE") || n.equals("BAMBOO") || n.equals("CACTUS")
                || n.equals("HONEY_BOTTLE") || n.equals("HONEYCOMB")
                || n.equals("EGG") || n.equals("MILK_BUCKET")
                || n.equals("WHEAT") || n.equals("HAY_BLOCK")
                || n.equals("PUMPKIN") || n.equals("MELON") || n.equals("MELON_SLICE")
                || n.equals("NETHER_WART") || n.equals("COCOA_BEANS")
                || n.equals("KELP") || n.equals("SEAGRASS")
                || n.equals("COD") || n.equals("SALMON") || n.equals("TROPICAL_FISH") || n.equals("PUFFERFISH")
                || n.equals("COOKED_COD") || n.equals("COOKED_SALMON")
                || n.equals("POTION") || n.equals("SPLASH_POTION") || n.equals("LINGERING_POTION")
                // Renewable resources commonly produced by mob, iron, gold and witch farms.
                || n.equals("COAL") || n.equals("CHARCOAL") || n.equals("IRON_INGOT") || n.equals("GOLD_INGOT")
                || n.equals("COPPER_INGOT") || n.equals("REDSTONE") || n.equals("GLOWSTONE_DUST")
                || n.equals("ARROW") || n.equals("BONE_MEAL") || n.equals("PHANTOM_MEMBRANE")
                || n.equals("STRING") || n.equals("GUNPOWDER") || n.equals("ROTTEN_FLESH")
                || n.equals("BONE") || n.equals("FEATHER") || n.equals("LEATHER") || n.equals("RABBIT_HIDE")
                || n.equals("INK_SAC") || n.equals("GLOW_INK_SAC") || n.equals("PRISMARINE_SHARD")
                || n.equals("PRISMARINE_CRYSTALS") || n.equals("SLIME_BALL") || n.equals("MAGMA_CREAM")
                || n.equals("BLAZE_ROD") || n.equals("GHAST_TEAR");
    }

    private int sellStockLimit(Material material) {
        String key = "shop.sell-stock-limits." + material.name().toLowerCase(Locale.ROOT);
        int configured = plugin.getConfig().getInt(key, -1);
        if (configured >= 0) {
            // Explicit shop stock requests may intentionally be below the normal 200-item floor.
            if (material == Material.BONE_MEAL || material == Material.MILK_BUCKET) return configured;
            return Math.max(200, configured);
        }
        String n = material.name();
        if (n.equals("DRIED_KELP") || n.equals("DRIED_KELP_BLOCK")) return 200;
        if (isFarmableCommonBlock(material)) return 1000;
        if (isSeedOrPlantingMaterial(material) || n.contains("SEEDS") || n.equals("SUGAR_CANE")
                || n.equals("BAMBOO") || n.equals("CACTUS") || n.equals("KELP")
                || n.equals("PUMPKIN") || n.equals("MELON") || n.equals("MELON_SLICE")) return 512;
        if (isMobDrop(material)) return 256;
        return 256;
    }

    private int getSellStock(Player player, Material material) {
        if (!isFarmableSellItem(material)) return Integer.MAX_VALUE;
        restockSellIfDue(player);
        String key = "players." + player.getUniqueId() + ".items." + material.name().toLowerCase(Locale.ROOT);
        if (!playerSellStockData.contains(key)) {
            playerSellStockData.set(key, sellStockLimit(material));
            savePlayerSellStock();
        }
        return Math.max(0, playerSellStockData.getInt(key, sellStockLimit(material)));
    }

    private void consumeSellStock(Player player, Material material, int amount) {
        if (!isFarmableSellItem(material) || amount <= 0) return;
        String key = "players." + player.getUniqueId() + ".items." + material.name().toLowerCase(Locale.ROOT);
        playerSellStockData.set(key, Math.max(0, getSellStock(player, material) - amount));
        savePlayerSellStock();
    }

    private void restockSellIfDue(Player player) {
        String base = "players." + player.getUniqueId();
        long now = System.currentTimeMillis();
        long interval = Math.max(1, plugin.getConfig().getLong("shop.sell-stock-restock-hours", 6)) * 60L * 60L * 1000L;
        String lastKey = base + ".last-restock-ms";
        if (!playerSellStockData.contains(lastKey)) {
            playerSellStockData.set(lastKey, now);
            savePlayerSellStock();
            return;
        }
        long last = playerSellStockData.getLong(lastKey, now);
        long elapsed = now - last;
        if (elapsed < interval) return;
        // Persisted wall-clock milliseconds keep counting while the server is offline.
        // Advance by complete 6-hour periods instead of resetting the clock to startup time.
        long completedPeriods = elapsed / interval;
        long nextRestockTime = last + completedPeriods * interval;
        for (Product p : products) {
            if (isFarmableSellItem(p.material())) {
                playerSellStockData.set(base + ".items." + p.material().name().toLowerCase(Locale.ROOT), sellStockLimit(p.material()));
            }
        }
        playerSellStockData.set(lastKey, nextRestockTime);
        savePlayerSellStock();
    }

    private void savePlayerSellStock() {
        try { playerSellStockData.save(playerSellStockFile); }
        catch (IOException e) { plugin.getLogger().severe("Could not save player-sell-stock.yml: " + e.getMessage()); }
    }

    public void setStock(Material material, int amount) {
        stockData.set("items." + material.name().toLowerCase(Locale.ROOT), Math.max(0, amount));
        saveStock();
    }

    public void restockAll() {
        // Admin restock affects shop BUY inventory only; player sell quotas remain personal.
        for (Product p : products) stockData.set("items." + p.material().name().toLowerCase(Locale.ROOT), stockLimit(p.material()));
        stockData.set("last-restock-date", LocalDate.now().toString());
        saveStock();
    }

    private void restockIfDue() {
        String today = LocalDate.now().toString();
        if (!today.equals(stockData.getString("last-restock-date", ""))) {
            for (Product p : products) stockData.set("items." + p.material().name().toLowerCase(Locale.ROOT), stockLimit(p.material()));
            stockData.set("last-restock-date", today);
            saveStock();
        }
    }

    private void saveStock() {
        try { stockData.save(stockFile); }
        catch (IOException e) { plugin.getLogger().severe("Could not save shop-stock.yml: " + e.getMessage()); }
    }

    private boolean hasVisitedEnd(Player player) {
        org.bukkit.advancement.Advancement advancement = plugin.getServer().getAdvancement(NamespacedKey.minecraft("end/enter_end"));
        return advancement == null || player.getAdvancementProgress(advancement).isDone();
    }

    public void setPrice(Material material, String kind, double amount) {
        if (!kind.equals("buy") && !kind.equals("sell")) throw new IllegalArgumentException("kind must be buy or sell");
        plugin.getConfig().set("shop.prices." + material.name().toLowerCase(Locale.ROOT) + "." + kind, amount);
        plugin.saveConfig();
    }

    private ItemStack createStack(Material material, int amount) {
        Product p = product(material);
        return p == null ? new ItemStack(material, amount) : createStack(p, amount);
    }

    private ItemStack createStack(Product product, int amount) {
        ItemStack stack = new ItemStack(product.material(), amount);
        if (product.category().equals("bounty-maps")) {
            ItemMeta mapMeta = stack.getItemMeta();
            String tier = switch ((product.fireworkPower() - 1) % 3) { case 0 -> "common"; case 1 -> "rare"; default -> "legendary"; };
            String dimension = product.fireworkPower() >= 4 ? "nether" : "overworld";
            mapMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "mission_entry_price"), PersistentDataType.DOUBLE, product.buy());
            mapMeta.setDisplayName(ChatColor.LIGHT_PURPLE + "XZH " + Character.toUpperCase(tier.charAt(0)) + tier.substring(1) + (dimension.equals("nether") ? " Nether" : "") + " Bounty Map");
            mapMeta.setLore(List.of(ChatColor.GRAY + "Right-click to accept a " + tier + " " + dimension + " bounty contract.", ChatColor.GRAY + "Bow, arrows and tracking compass supplied."));
            mapMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "bounty_tier"), PersistentDataType.STRING, tier);
            mapMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "bounty_dimension"), PersistentDataType.STRING, dimension);
            stack.setItemMeta(mapMeta);
        }
        if (product.potionType() != null && stack.getItemMeta() instanceof PotionMeta meta) {
            try { meta.setBasePotionType(product.potionType()); } catch (IllegalArgumentException ignored) { }
            stack.setItemMeta(meta);
        }
        if (product.fireworkPower() > 0 && stack.getItemMeta() instanceof org.bukkit.inventory.meta.FireworkMeta meta) {
            meta.setPower(product.fireworkPower());
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private Product productFromMeta(ItemMeta meta) {
        String materialName = meta.getPersistentDataContainer().get(materialKey, PersistentDataType.STRING);
        Material material = materialName == null ? null : parseMaterial(materialName);
        if (material == null) return null;
        String typeName = meta.getPersistentDataContainer().get(potionTypeKey, PersistentDataType.STRING);
        if (typeName != null) return potionProduct(material, typeName);
        Integer power = meta.getPersistentDataContainer().get(fireworkPowerKey, PersistentDataType.INTEGER);
        if (power != null) return products.stream().filter(p -> p.material() == material && p.fireworkPower() == power).findFirst().orElse(null);
        return product(material);
    }

    private Product potionProduct(Material material, String typeName) {
        try {
            PotionType type = PotionType.valueOf(typeName);
            return products.stream().filter(p -> p.material() == material && p.potionType() == type).findFirst().orElse(null);
        } catch (IllegalArgumentException ex) { return null; }
    }

    private double potionBasePrice(PotionType type) {
        String n = type.name();
        if (n.equals("WATER")) return 5;
        if (n.contains("STRONG") || n.contains("TURTLE_MASTER")) return 180;
        if (n.contains("LONG")) return 130;
        if (n.contains("HEALING") || n.contains("HARMING") || n.contains("REGENERATION") || n.contains("STRENGTH")) return 150;
        if (n.equals("NIGHT_VISION") || n.equals("INVISIBILITY") || n.equals("FIRE_RESISTANCE") || n.equals("WATER_BREATHING") || n.equals("SLOW_FALLING")) return 140;
        if (n.equals("AWKWARD") || n.equals("MUNDANE") || n.equals("THICK")) return 25;
        return 100;
    }

    private String prettyPotion(PotionType type) {
        String text = type.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        StringBuilder result = new StringBuilder();
        boolean capitalize = true;
        for (char c : text.toCharArray()) {
            if (capitalize && Character.isLetter(c)) {
                result.append(Character.toUpperCase(c));
                capitalize = false;
            } else {
                result.append(c);
            }
            if (c == ' ') capitalize = true;
        }
        return result.toString();
    }

    private String prettyProduct(Product product) {
        if (product.category().equals("bounty-maps")) {
            String tier = switch ((product.fireworkPower() - 1) % 3) { case 0 -> "Common"; case 1 -> "Rare"; default -> "Legendary"; };
            return "XZH " + tier + (product.fireworkPower() >= 4 ? " Nether" : "") + " Bounty Map";
        }
        return pretty(product.material()) + (product.potionType() == null ? "" : " - " + prettyPotion(product.potionType()));
    }

    private double buyPrice(Product p) {
        // Each bounty map tier has its own price; never let legacy filled_map config flatten them.
        if (p.category().equals("bounty-maps")) return Math.ceil(p.buy());
        String materialPath = "shop.prices." + p.material().name().toLowerCase(Locale.ROOT) + ".buy";
        String variantPath = p.potionType() == null ? "" : "shop.potion-prices." + p.potionType().name().toLowerCase(Locale.ROOT) + "." + p.material().name().toLowerCase(Locale.ROOT) + ".buy";
        double fallback = p.buy();
        if (!variantPath.isEmpty() && plugin.getConfig().contains(variantPath)) {
            double configured = plugin.getConfig().getDouble(variantPath, fallback);
            return Math.ceil(validPrice(configured) ? configured : fallback);
        }
        // The legacy per-material overrides are retained for non-potion products.
        if (p.potionType() == null) {
            double configured = plugin.getConfig().getDouble(materialPath, fallback);
            return Math.ceil(validPrice(configured) ? configured : fallback);
        }
        return Math.ceil(fallback);
    }

    private double sellPrice(Product p) {
        if (p.category().equals("bounty-maps")) return 0.0;
        if (p.material() == Material.ENCHANTED_BOOK) return Math.floor(buyPrice(p) * 0.30);
        if (p.material() == Material.FIREWORK_ROCKET) return 0.0;
        if (p.potionType() != null || p.category().equals("potions")) {
            String type = p.potionType() == null ? "" : p.potionType().name();
            if (List.of("WATER", "AWKWARD", "MUNDANE", "THICK", "UNCRAFTABLE", "CUSTOM").contains(type)) return 0.0;
            return Math.floor(buyPrice(p) * 0.06);
        }
        // Exclusive and duplication-prone items cannot be converted back into coins.
        if (isUnsellableExploitItem(p.material())) return 0.0;
        String sellPath = "shop.prices." + p.material().name().toLowerCase(Locale.ROOT) + ".sell";
        if (plugin.getConfig().contains(sellPath)) {
            double configured = plugin.getConfig().getDouble(sellPath, p.sell());
            return validPrice(configured) ? configured : Math.max(0.0, p.sell());
        }
        if (p.material().name().endsWith("_SPAWN_EGG")
                && plugin.getConfig().getBoolean("restrictions.buy-only-spawn-eggs", true)) return 0.0;
        return Math.max(0.0, p.sell());
    }

    private boolean isMobDrop(Material m) {
        return switch (m) { case ROTTEN_FLESH, BONE, STRING, FEATHER, GUNPOWDER, SPIDER_EYE, LEATHER, RABBIT_HIDE, INK_SAC, GLOW_INK_SAC, PRISMARINE_SHARD, PRISMARINE_CRYSTALS, SLIME_BALL, MAGMA_CREAM, BLAZE_ROD, GHAST_TEAR -> true; default -> false; };
    }
    private double mobDropBuyPrice(Material m) {
        return switch (m) { case GHAST_TEAR -> 80; case BLAZE_ROD, MAGMA_CREAM -> 30; case SLIME_BALL, PRISMARINE_SHARD, PRISMARINE_CRYSTALS -> 15; case GUNPOWDER -> 2; case LEATHER -> 8; default -> 5; };
    }
    private double mobDropSellPrice(Material m) {
        return switch (m) { case GHAST_TEAR -> 8; case BLAZE_ROD, MAGMA_CREAM -> 4; case SLIME_BALL, PRISMARINE_SHARD, PRISMARINE_CRYSTALS -> 2; case GUNPOWDER -> 1.5; case LEATHER, FEATHER -> 1; default -> 0.5; };
    }
    private int productOrder(Product p) {
        if (p.category().equals("bounty-maps")) {
            return p.fireworkPower() - 1;
        }
        if (p.category().equals("potions")) {
            String n = p.potionType() == null ? "" : p.potionType().name();
            List<String> common = List.of("HEALING", "STRONG_HEALING", "REGENERATION", "LONG_REGENERATION", "STRENGTH", "LONG_STRENGTH", "SWIFTNESS", "LONG_SWIFTNESS", "STRONG_SWIFTNESS", "FIRE_RESISTANCE", "LONG_FIRE_RESISTANCE", "NIGHT_VISION", "LONG_NIGHT_VISION", "WATER_BREATHING", "LONG_WATER_BREATHING", "SLOW_FALLING", "LONG_SLOW_FALLING", "INVISIBILITY", "LONG_INVISIBILITY", "POISON", "LONG_POISON", "WEAKNESS", "LONG_WEAKNESS", "HARMING", "STRONG_HARMING", "TURTLE_MASTER", "LONG_TURTLE_MASTER", "WATER", "AWKWARD", "MUNDANE", "THICK");
            int i = common.indexOf(n); return i >= 0 ? i : 100 + Math.abs(n.hashCode() % 10000);
        }
        if (p.category().equals("blocks")) {
            List<String> common = List.of("STONE", "COBBLESTONE", "DIRT", "GRAVEL", "SAND", "OAK_PLANKS", "SPRUCE_PLANKS", "BIRCH_PLANKS", "DARK_OAK_PLANKS", "GLASS", "BRICKS", "STONE_BRICKS", "SANDSTONE", "DEEPSLATE", "NETHERRACK", "OBSIDIAN", "END_STONE", "IRON_BLOCK", "DIAMOND_BLOCK");
            int i = common.indexOf(p.material().name()); return i >= 0 ? i : 100 + Math.abs(p.material().name().hashCode() % 10000);
        }
        return 0;
    }

    private double blockBuyPrice(Material m) {
        String n = m.name();
        if (n.contains("CUT_COPPER") || n.contains("CHISELED_COPPER") || n.contains("COPPER_GRATE")) return 25.0;
        if (n.equals("WAXED_COPPER_BLOCK")) return 90.0;
        double exact = switch (n) {
            case "NETHERITE_BLOCK" -> 16200.0;
            case "ANCIENT_DEBRIS" -> 300.0;
            case "GOLD_BLOCK", "RAW_GOLD_BLOCK" -> 360.0;
            case "RAW_IRON_BLOCK" -> 225.0;
            case "RAW_COPPER_BLOCK", "COPPER_BLOCK" -> 90.0;
            case "EMERALD_BLOCK" -> 630.0;
            case "LAPIS_BLOCK" -> 90.0;
            case "REDSTONE_BLOCK", "COAL_BLOCK" -> 72.0;
            case "AMETHYST_BLOCK" -> 48.0;
            case "SLIME_BLOCK" -> 135.0;
            case "MAGMA_BLOCK" -> 120.0;
            case "NETHER_WART_BLOCK" -> 180.0;
            case "PRISMARINE" -> 60.0;
            case "PRISMARINE_BRICKS" -> 135.0;
            case "DARK_PRISMARINE" -> 130.0;
            case "QUARTZ_BLOCK", "QUARTZ_PILLAR", "QUARTZ_BRICKS", "SMOOTH_QUARTZ", "CHISELED_QUARTZ_BLOCK" -> 50.0;
            case "BONE_BLOCK", "DRIED_KELP_BLOCK" -> 45.0;
            case "HAY_BLOCK" -> 18.0;
            case "HONEY_BLOCK" -> 30.0;
            case "CONDUIT" -> 800.0;
            case "LODESTONE" -> 2100.0;
            case "RESPAWN_ANCHOR" -> 350.0;
            case "SHULKER_BOX" -> 500.0;
            case "SNIFFER_EGG" -> 3000.0;
            case "TURTLE_EGG" -> 300.0;
            case "CREAKING_HEART" -> 400.0;
            case "SCULK_SHRIEKER" -> 200.0;
            case "SCULK_CATALYST" -> 150.0;
            case "SCULK_SENSOR", "CALIBRATED_SCULK_SENSOR" -> 100.0;
            case "SPONGE", "WET_SPONGE" -> 150.0;
            case "HEAVY_CORE" -> 2000.0;
            case "FROGSPAWN" -> 50.0;
            case "HOPPER", "CRAFTER" -> 150.0;
            case "PISTON" -> 60.0;
            case "STICKY_PISTON" -> 75.0;
            case "DISPENSER" -> 60.0;
            case "DROPPER", "OBSERVER" -> 40.0;
            case "DAYLIGHT_DETECTOR" -> 60.0;
            case "NOTE_BLOCK" -> 20.0;
            case "TARGET" -> 30.0;
            case "JUKEBOX" -> 150.0;
            case "COPPER_BULB" -> 60.0;
            case "IRON_BARS" -> 10.0;
            case "CHAIN" -> 30.0;
            case "COPPER_BARS" -> 6.0;
            case "COPPER_CHAIN" -> 20.0;
            default -> -1.0;
        };
        if (exact >= 0.0) return exact;
        if (n.contains("NETHERRACK") || n.contains("DIRT") || n.contains("GRAVEL") || n.contains("SAND")
                || n.contains("COBBLESTONE") || n.equals("STONE") || n.equals("NETHERRACK")) return 2.0;
        if (n.contains("LEAVES")) return 2.0;
        if (n.contains("PLANKS")) return 4.0;
        if (n.contains("LOG") || n.contains("WOOD")) return 5.0;
        if (n.contains("ORE") || n.contains("ANCIENT_DEBRIS")) return 100.0;
        if (n.contains("DEEPSLATE") || n.contains("TUFF") || n.contains("ANDESITE") || n.contains("DIORITE") || n.contains("GRANITE")) return 3.0;
        if (n.contains("OBSIDIAN") || n.contains("CRYING")) return 30.0;
        if (n.contains("COPPER") || n.contains("IRON") || n.contains("GOLD") || n.contains("DIAMOND") || n.contains("EMERALD")) return 100.0;
        return 10.0;
    }

    private boolean isBuildingBlock(Material material) {
        String n = material.name();
        if (isFlowerOrDyeMaterial(material) || isEssentialsMaterial(material)
                || n.endsWith("_STAIRS") || n.endsWith("_SLAB") || n.endsWith("_FENCE") || n.endsWith("_FENCE_GATE")
                || n.endsWith("_WALL") || n.endsWith("_DOOR") || n.endsWith("_TRAPDOOR") || n.endsWith("_BUTTON")
                || n.endsWith("_PRESSURE_PLATE") || n.endsWith("_SIGN") || n.endsWith("_HANGING_SIGN")
                || n.endsWith("_BANNER") || n.endsWith("_HEAD") || n.endsWith("_SKULL") || n.endsWith("_BED")
                || n.endsWith("_CANDLE") || n.endsWith("_CORAL") || n.endsWith("_CORAL_FAN")
                || n.endsWith("_SAPLING") || n.endsWith("_FLOWER") || n.endsWith("_TULIP")
                || n.endsWith("_CARPET") || n.endsWith("_RAIL") || n.endsWith("_TORCH")
                || n.endsWith("_ANVIL") || n.endsWith("_CHEST") || n.endsWith("_SHULKER_BOX")
                || n.endsWith("_SPAWN_EGG") || n.endsWith("_ORE")) return false;
        return !n.equals("PLAYER_HEAD") && !n.equals("DRAGON_EGG") && !n.equals("END_CRYSTAL")
                && !n.equals("ENCHANTING_TABLE") && !n.equals("BREWING_STAND") && !n.equals("SPAWNER");
    }

    private boolean isFlowerOrDyeMaterial(Material material) {
        String n = material.name();
        if (n.endsWith("_DYE")) return true;
        return n.contains("FLOWER") || n.endsWith("_TULIP") || n.endsWith("_ORCHID")
                || n.endsWith("_BLUET") || n.endsWith("_DAISY") || n.equals("DANDELION")
                || n.equals("POPPY") || n.equals("ALLIUM") || n.equals("LILAC")
                || n.equals("PEONY") || n.equals("SUNFLOWER") || n.equals("ROSE_BUSH")
                || n.equals("LILY_OF_THE_VALLEY") || n.equals("PITCHER_PLANT")
                || n.equals("SPORE_BLOSSOM") || n.equals("CHORUS_FLOWER")
                || n.equals("CACTUS_FLOWER");
    }

    private double flowerDyeBuyPrice(Material material) {
        String n = material.name();
        if (n.endsWith("_DYE")) return 8.0;
        if (n.equals("WITHER_ROSE") || n.equals("PITCHER_PLANT") || n.equals("TORCHFLOWER")) return 80.0;
        if (n.equals("SUNFLOWER") || n.equals("LILAC") || n.equals("ROSE_BUSH") || n.equals("PEONY")) return 12.0;
        return 5.0;
    }

    private boolean isEssentialsMaterial(Material material) {
        String n = material.name();
        if (n.endsWith("_TORCH") || n.endsWith("_LANTERN") || n.endsWith("_CANDLE")
                || n.endsWith("_FROGLIGHT") || n.endsWith("_CAMPFIRE")) return true;
        if (isMobDrop(material) || n.equals("TNT") || n.equals("FIREWORK_ROCKET")) return true;
        return switch (n) {
            case "LEAD", "TORCH", "SOUL_TORCH", "REDSTONE_TORCH", "LANTERN", "SOUL_LANTERN",
                 "END_ROD", "GLOWSTONE", "SEA_LANTERN", "SHROOMLIGHT", "REDSTONE_LAMP", "JACK_O_LANTERN", "BEACON",
                 "ANVIL", "CHIPPED_ANVIL", "DAMAGED_ANVIL", "CRAFTING_TABLE",
                 "FLETCHING_TABLE", "SMITHING_TABLE", "CARTOGRAPHY_TABLE", "LOOM",
                 "STONECUTTER", "GRINDSTONE", "ENCHANTING_TABLE", "BREWING_STAND",
                 "LECTERN", "SMOKER", "BLAST_FURNACE", "FURNACE", "CAMPFIRE",
                 "SOUL_CAMPFIRE", "BOOK", "BOOKSHELF", "CHISELED_BOOKSHELF",
                 "WRITABLE_BOOK", "WRITTEN_BOOK", "PAPER", "SCAFFOLDING", "LADDER",
                 "CHAIN", "LIGHTNING_ROD", "BELL", "DECORATED_POT" -> true;
            default -> false;
        };
    }

    private double essentialsBuyPrice(Material material) {
        String n = material.name();
        if (n.equals("TNT")) return 90.0;
        if (n.equals("FIREWORK_ROCKET")) return 8.0;
        if (isMobDrop(material)) return mobDropBuyPrice(material);
        if (n.equals("BEACON")) return 1500.0;
        if (n.contains("ANVIL")) return 250.0;
        if (n.endsWith("_TABLE") || n.equals("CRAFTING_TABLE") || n.equals("ENCHANTING_TABLE")) return 40.0;
        if (n.equals("LEAD")) return 25.0;
        if (n.equals("BOOKSHELF") || n.equals("CHISELED_BOOKSHELF")) return 35.0;
        if (n.equals("BOOK") || n.equals("WRITABLE_BOOK") || n.equals("WRITTEN_BOOK")) return 10.0;
        if (n.contains("CAMPFIRE")) return 35.0;
        if (n.contains("LANTERN") || n.contains("FROGLIGHT") || n.equals("SEA_LANTERN")
                || n.equals("GLOWSTONE") || n.equals("SHROOMLIGHT")) return 25.0;
        if (n.contains("TORCH")) return 3.0;
        return 15.0;
    }

    private double foodBuyPrice(Material material) {
        String n = material.name();
        if (n.contains("ENCHANTED_GOLDEN_APPLE")) return 2000;
        if (n.contains("GOLDEN_APPLE")) return 400;
        if (n.contains("GOLDEN_CARROT")) return 50;
        if (n.equals("COOKED_RABBIT")) return 11;
        if (n.contains("BEEF") || n.contains("PORKCHOP") || n.contains("MUTTON")) return 10;
        if (n.contains("SALMON") || n.contains("COD") || n.contains("CHICKEN") || n.contains("RABBIT")) return 7;
        if (n.contains("POTATO") || n.contains("CARROT") || n.contains("BEETROOT") || n.contains("MELON") || n.contains("BERRY")) return 3;
        return 5;
    }

    private boolean isCreativeOnlyBlock(Material material) {
        return switch (material.name()) {
            case "BEDROCK", "BARRIER", "COMMAND_BLOCK", "CHAIN_COMMAND_BLOCK",
                 "REPEATING_COMMAND_BLOCK", "COMMAND_BLOCK_MINECART", "STRUCTURE_BLOCK",
                 "STRUCTURE_VOID", "JIGSAW", "END_PORTAL_FRAME", "SPAWNER", "TRIAL_SPAWNER", "VAULT",
                 "LIGHT", "DEBUG_STICK", "KNOWLEDGE_BOOK", "PLAYER_HEAD", "BUDDING_AMETHYST",
                 "REINFORCED_DEEPSLATE", "TEST_BLOCK", "TEST_INSTANCE_BLOCK", "INFESTED_STONE",
                 "INFESTED_COBBLESTONE", "INFESTED_STONE_BRICKS", "INFESTED_MOSSY_STONE_BRICKS",
                 "INFESTED_CRACKED_STONE_BRICKS", "INFESTED_CHISELED_STONE_BRICKS", "INFESTED_DEEPSLATE",
                 "FARMLAND", "DIRT_PATH", "SUSPICIOUS_SAND", "SUSPICIOUS_GRAVEL" -> true;
            default -> false;
        };
    }

    private boolean isFarmableCommonBlock(Material m) {
        String n = m.name();
        return n.contains("NETHERRACK") || n.equals("STONE") || n.contains("COBBLESTONE")
                || n.equals("DIRT") || n.equals("GRAVEL") || n.equals("SAND")
                || n.contains("ANDESITE") || n.contains("DIORITE") || n.contains("GRANITE")
                || n.contains("DEEPSLATE") || n.contains("TUFF") || n.contains("BASALT") || n.contains("BLACKSTONE");
    }

    private boolean isSeedOrPlantingMaterial(Material m) {
        String n = m.name();
        return n.endsWith("_SEEDS") || n.equals("WHEAT_SEEDS") || n.equals("WHEAT") || n.equals("NETHER_WART")
                || n.equals("COCOA_BEANS") || n.equals("CARROT") || n.equals("POTATO")
                || n.equals("BEETROOT") || n.equals("SWEET_BERRIES") || n.equals("GLOW_BERRIES")
                || n.equals("PITCHER_POD") || n.equals("TORCHFLOWER_SEEDS") || n.equals("SUGAR_CANE")
                || n.equals("BAMBOO") || n.equals("CACTUS") || n.equals("MUSHROOM") || n.equals("BROWN_MUSHROOM")
                || n.equals("RED_MUSHROOM") || n.equals("KELP") || n.equals("SEA_PICKLE");
    }

    private double seedBuyPrice(Material m) {
        String n = m.name();
        if (n.contains("TORCHFLOWER") || n.contains("PITCHER")) return 250.0;
        if (n.equals("NETHER_WART") || n.equals("COCOA_BEANS")) return 20.0;
        if (n.equals("WHEAT_SEEDS") || n.equals("BEETROOT_SEEDS")) return 2.0;
        return 5.0;
    }

    private double seedSellPrice(Material m) {
        String n = m.name();
        if (n.contains("TORCHFLOWER") || n.contains("PITCHER")) return 0.0;
        return 1.0 / 64.0;
    }

    /** Items that must never be convertible into shop money, even if a config assigns a sell price. */
    private boolean isUnsellableExploitItem(Material material) {
        if (material == null) return true;
        String name = material.name();
        // Every trim template and the Netherite Upgrade template can be duplicated in vanilla crafting.
        if (name.endsWith("_SMITHING_TEMPLATE")) return true;
        // Rare items or drops commonly targeted by duplication glitches / scalable farms.
        return name.equals("ELYTRA")
                || name.equals("SHULKER_SHELL")
                || name.contains("SHULKER_BOX")
                || name.equals("WITHER_SKELETON_SKULL")
                || name.equals("NETHER_STAR")
                || name.equals("DRAGON_EGG")
                || name.equals("DRAGON_HEAD")
                || name.equals("END_CRYSTAL")
                || name.equals("DIAMOND_BLOCK")
                || name.equals("NETHERITE_BLOCK")
                || name.equals("EMERALD_BLOCK");
    }

    private boolean validPrice(double value) {
        return Double.isFinite(value) && value >= 0.0;
    }

    private double defaultSellPrice() {
        double configured = plugin.getConfig().getDouble("shop.default-sell-price", 0.1);
        return validPrice(configured) ? configured : 0.1;
    }

    private Product product(Material material) {
        return products.stream().filter(p -> p.material() == material && p.potionType() == null).findFirst().orElseGet(() -> products.stream().filter(p -> p.material() == material).findFirst().orElse(null));
    }
    private Material parseMaterial(String name) {
        try { return Material.valueOf(name.toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException e) { return null; }
    }
    private Product productForStack(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR) return null;
        if (stack.getItemMeta() instanceof PotionMeta meta) {
            PotionType type = meta.getBasePotionType();
            if (type != null) {
                Product variant = products.stream().filter(p -> p.material() == stack.getType() && p.potionType() == type).findFirst().orElse(null);
                if (variant != null) return variant;
            }
        }
        return product(stack.getType());
    }

    private int countPotion(Player player, Product product) {
        int total = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (matchesPotion(item, product)) total += item.getAmount();
        }
        return total;
    }

    private int removePotion(Player player, Product product, int requested) {
        int remaining = requested;
        for (int slot = 0; slot < player.getInventory().getStorageContents().length && remaining > 0; slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (!matchesPotion(item, product)) continue;
            int take = Math.min(remaining, item.getAmount());
            remaining -= take;
            int left = item.getAmount() - take;
            if (left <= 0) player.getInventory().setItem(slot, null); else item.setAmount(left);
        }
        return requested - remaining;
    }

    private boolean matchesPotion(ItemStack item, Product product) {
        if (item == null || item.getType() != product.material() || !(item.getItemMeta() instanceof PotionMeta meta)) return false;
        return meta.getBasePotionType() == product.potionType();
    }

    private int count(Player player, Material material) {
        int total = 0;
        for (ItemStack item : player.getInventory().getStorageContents())
            if (item != null && item.getType() == material && !plugin.getPocketCoinManager().isCoin(item)) total += item.getAmount();
        return total;
    }
    private int remove(Player player, Material material, int requested) {
        int remaining = requested;
        for (int i = 0; i < player.getInventory().getStorageContents().length && remaining > 0; i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item == null || item.getType() != material || plugin.getPocketCoinManager().isCoin(item)) continue;
            int take = Math.min(item.getAmount(), remaining);
            item.setAmount(item.getAmount() - take);
            remaining -= take;
            if (item.getAmount() <= 0) player.getInventory().setItem(i, null);
        }
        return requested - remaining;
    }
    private String pretty(Material material) {
        return material.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }
    private String money(double amount) {
        return String.format(Locale.US, "%.2f", amount).replaceAll("\\.00$", "");
    }
}
