package com.xzh.economy;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
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
    private final File stockFile;
    private final YamlConfiguration stockData;

    private record Product(String category, Material material, double buy, double sell, PotionType potionType) {
        Product(String category, Material material, double buy, double sell) { this(category, material, buy, sell, null); }
    }

    private final List<Product> products = new ArrayList<>(List.of(
        new Product("ores", Material.COAL, 8, 2.4),
        new Product("ores", Material.IRON_INGOT, 25, 7.5),
        new Product("ores", Material.GOLD_INGOT, 40, 12),
        new Product("ores", Material.COPPER_INGOT, 15, 4.5),
        new Product("ores", Material.LAPIS_LAZULI, 10, 3),
        new Product("ores", Material.REDSTONE, 8, 2.4),
        new Product("ores", Material.EMERALD, 100, 30),
        new Product("ores", Material.AMETHYST_SHARD, 20, 6),
        new Product("ores", Material.QUARTZ, 12, 3.6),
        new Product("ores", Material.NETHERITE_SCRAP, 300, 90),
        new Product("ores", Material.NETHERITE_INGOT, 1200, 360),
        new Product("ores", Material.DIAMOND, 250, 75),
        new Product("blocks", Material.OBSIDIAN, 30, 9),
        new Product("blocks", Material.GLASS, 10, 3),
        new Product("blocks", Material.END_STONE, 15, 4.5),
        new Product("blocks", Material.IRON_BLOCK, 225, 67.5),
        new Product("blocks", Material.DIAMOND_BLOCK, 2250, 675),
        new Product("food-cooked", Material.BREAD, 8, 2.4),
        new Product("food-cooked", Material.COOKED_BEEF, 14, 4.2),
        new Product("food-cooked", Material.COOKED_PORKCHOP, 16, 4.8),
        new Product("food-cooked", Material.COOKED_CHICKEN, 12, 3.6),
        new Product("food-cooked", Material.BAKED_POTATO, 7, 2.1),
        new Product("food-cooked", Material.COOKED_COD, 10, 3),
        new Product("food-cooked", Material.COOKED_SALMON, 12, 3.6),
        new Product("food-raw", Material.BEEF, 8, 2.4),
        new Product("food-raw", Material.PORKCHOP, 9, 2.7),
        new Product("food-raw", Material.CHICKEN, 7, 2.1),
        new Product("food-raw", Material.COD, 5, 1.5),
        new Product("food-raw", Material.SALMON, 6, 1.8),
        new Product("food-raw", Material.MUTTON, 7, 2.1),
        new Product("food-raw", Material.RABBIT, 7, 2.1),
        new Product("equipment", Material.IRON_PICKAXE, 120, 36),
        new Product("equipment", Material.DIAMOND_PICKAXE, 650, 195),
        new Product("equipment", Material.IRON_CHESTPLATE, 180, 54),
        new Product("equipment", Material.DIAMOND_SWORD, 500, 150),
        new Product("equipment", Material.IRON_SWORD, 90, 27),
        new Product("equipment", Material.IRON_AXE, 110, 33),
        new Product("equipment", Material.DIAMOND_AXE, 600, 180),
        new Product("equipment", Material.DIAMOND_SHOVEL, 250, 75),
        new Product("equipment", Material.DIAMOND_HOE, 300, 90),
        new Product("equipment", Material.NETHERITE_PICKAXE, 3000, 900),
        new Product("equipment", Material.NETHERITE_AXE, 2800, 840),
        new Product("equipment", Material.NETHERITE_SWORD, 2500, 750),
        new Product("equipment", Material.IRON_HELMET, 100, 30),
        new Product("equipment", Material.IRON_LEGGINGS, 160, 48),
        new Product("equipment", Material.IRON_BOOTS, 90, 27),
        new Product("equipment", Material.DIAMOND_HELMET, 450, 135),
        new Product("equipment", Material.DIAMOND_CHESTPLATE, 900, 270),
        new Product("equipment", Material.DIAMOND_LEGGINGS, 800, 240),
        new Product("equipment", Material.DIAMOND_BOOTS, 400, 120),
        new Product("equipment", Material.NETHERITE_HELMET, 1800, 540),
        new Product("equipment", Material.NETHERITE_CHESTPLATE, 3600, 1080),
        new Product("equipment", Material.NETHERITE_LEGGINGS, 3200, 960),
        new Product("equipment", Material.NETHERITE_BOOTS, 1700, 510),
        new Product("equipment", Material.BOW, 180, 54),
        new Product("equipment", Material.CROSSBOW, 220, 66),
        new Product("equipment", Material.SHIELD, 100, 30),
        new Product("equipment", Material.TRIDENT, 1800, 540),
        new Product("equipment", Material.MACE, 3000, 900),
        new Product("equipment", Material.TOTEM_OF_UNDYING, 2500, 750),
        new Product("equipment", Material.ENDER_CHEST, 350, 105),
        new Product("food-cooked", Material.GOLDEN_CARROT, 10, 3),
        new Product("food-cooked", Material.PUMPKIN_PIE, 12, 3.6),
        new Product("food-cooked", Material.GOLDEN_APPLE, 180, 54),
        new Product("food-cooked", Material.ENCHANTED_GOLDEN_APPLE, 2500, 750),
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
        new Product("eggs-heads", Material.HORSE_SPAWN_EGG, 2500, 0),
        new Product("eggs-heads", Material.DONKEY_SPAWN_EGG, 2500, 0),
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
        new Product("special", Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE, 2000, 0),
        new Product("special", Material.ELYTRA, 5000, 0)
    ));

    public ShopManager(XZHEconomySystem plugin) {
        this.plugin = plugin;
        this.actionKey = new NamespacedKey(plugin, "shop_action");
        this.materialKey = new NamespacedKey(plugin, "shop_material");
        this.quantityKey = new NamespacedKey(plugin, "shop_quantity");
        this.potionTypeKey = new NamespacedKey(plugin, "shop_potion_type");
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
        this.stockFile = new File(plugin.getDataFolder(), "shop-stock.yml");
        this.stockData = YamlConfiguration.loadConfiguration(stockFile);
        initializeStock();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, this::restockIfDue, 20L * 60L, 20L * 60L);
    }

    public void openMain(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, ChatColor.DARK_GREEN + "XZH SHOP");
        add(inv, 10, Material.IRON_INGOT, "Ores", "category", "ores");
        add(inv, 12, Material.BRICKS, "Blocks", "category", "blocks");
        add(inv, 14, Material.COOKED_BEEF, "Food", "category", "food");
        add(inv, 16, Material.DIAMOND_PICKAXE, "Armor & Tools", "category", "equipment");
        add(inv, 19, Material.EXPERIENCE_BOTTLE, "XP", "category", "xp");
        add(inv, 21, Material.POTION, "Potions", "category", "potions");
        add(inv, 23, Material.ZOMBIE_HEAD, "Eggs & Heads", "category", "eggs-heads");
        add(inv, 25, Material.ELYTRA, "Special Items", "category", "special");
        add(inv, 28, Material.ENCHANTED_BOOK, "Enchantment Books", "books", "books");
        add(inv, 30, Material.WRITABLE_BOOK, "Quest Packs", "quests", "quests");
        add(inv, 32, Material.CHEST, "Death Inventory", "deathinventory", "deathinventory");
        add(inv, 34, Material.GOLD_INGOT, "Balance", "balance", "balance");
        add(inv, 36, Material.BOOK, "Help", "help", "help");
        add(inv, 38, Material.CLOCK, "XZH BANK", "bank", "bank");
        add(inv, 49, Material.GOLD_INGOT, "Sell Hand (held item)", "sell-hand", "hand");
        player.openInventory(inv);
    }

    private void openCategory(Player player, String category) {
        openCategory(player, category, 0);
    }

    private void openCategory(Player player, String category, int requestedPage) {
        if (category.equals("food")) { openFoodMenu(player); return; }
        List<Product> list = products.stream().filter(p -> p.category().equals(category)).toList();
        int pages = Math.max(1, (list.size() + 44) / 45);
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        Inventory inv = Bukkit.createInventory(null, 54, ChatColor.DARK_GREEN + "XZH SHOP: " + category + " " + (page + 1));
        int start = page * 45;
        int end = Math.min(list.size(), start + 45);
        for (int index = start; index < end; index++) {
            Product p = list.get(index);
            int slot = index - start;
            ItemStack item = createStack(p, 1);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(ChatColor.YELLOW + pretty(p.material()) + (p.potionType() == null ? "" : " - " + prettyPotion(p.potionType())));
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.WHITE + "Buy: " + ChatColor.YELLOW + money(buyPrice(p)) + " coins each");
            lore.add(ChatColor.WHITE + "Sell: " + ChatColor.RED + money(sellPrice(p)) + " coins each");
            lore.add(ChatColor.WHITE + "Global stock: " + ChatColor.AQUA + getStock(p.material()) + "/" + stockLimit(p.material()));
            if (p.material() == Material.ELYTRA) lore.add(ChatColor.LIGHT_PURPLE + "Unlock: Visit the End first");
            lore.add(ChatColor.GRAY + "Click to choose quantity");
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "product");
            meta.getPersistentDataContainer().set(materialKey, PersistentDataType.STRING, p.material().name());
            if (p.potionType() != null) meta.getPersistentDataContainer().set(potionTypeKey, PersistentDataType.STRING, p.potionType().name());
            item.setItemMeta(meta);
            inv.setItem(slot, item);
        }
        if (page > 0) add(inv, 45, Material.ARROW, "Previous page", "category-page", category + ":" + (page - 1));
        add(inv, 49, Material.ARROW, "Back", "back", "main");
        if (page + 1 < pages) add(inv, 53, Material.ARROW, "Next page", "category-page", category + ":" + (page + 1));
        player.openInventory(inv);
    }

    private void openFoodMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.DARK_GREEN + "XZH SHOP: food");
        add(inv, 11, Material.COOKED_BEEF, "Cooked Food", "category", "food-cooked");
        add(inv, 15, Material.BEEF, "Raw Food", "category", "food-raw");
        add(inv, 22, Material.ARROW, "Back", "back", "main");
        player.openInventory(inv);
    }

    private void openQuantity(Player player, Product p) {
        Material material = p.material();
        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.DARK_GREEN + "Choose quantity");
        int[] quantities = {1, 10, 32, 64};
        int[] slots = {10, 11, 19, 20};
        for (int i = 0; i < quantities.length; i++) {
            ItemStack item = createStack(p, Math.min(64, quantities[i]));
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(ChatColor.YELLOW + "Buy " + quantities[i]);
            meta.setLore(List.of(ChatColor.WHITE + "Total: " + ChatColor.YELLOW + money(buyPrice(p) * quantities[i]) + " coins",
                    ChatColor.WHITE + "Global stock: " + ChatColor.AQUA + getStock(material)));
            meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "confirm-buy");
            meta.getPersistentDataContainer().set(materialKey, PersistentDataType.STRING, material.name());
            if (p.potionType() != null) meta.getPersistentDataContainer().set(potionTypeKey, PersistentDataType.STRING, p.potionType().name());
            meta.getPersistentDataContainer().set(quantityKey, PersistentDataType.INTEGER, quantities[i]);
            item.setItemMeta(meta);
            inv.setItem(slots[i], item);
        }
        ItemStack sell = createStack(p, 1);
        ItemMeta sm = sell.getItemMeta();
        sm.setDisplayName(ChatColor.RED + "Sell all of this item in inventory");
        sm.setLore(List.of(ChatColor.WHITE + "Value: " + ChatColor.RED + money(sellPrice(p)) + " coins each",
                ChatColor.GRAY + "You will confirm before selling."));
        sm.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "confirm-sell");
        sm.getPersistentDataContainer().set(materialKey, PersistentDataType.STRING, material.name());
        if (p.potionType() != null) sm.getPersistentDataContainer().set(potionTypeKey, PersistentDataType.STRING, p.potionType().name());
        sell.setItemMeta(sm);
        inv.setItem(15, sell);
        add(inv, 26, Material.ARROW, "Back", "category-back", p.category());
        player.openInventory(inv);
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
        String txValue = material.name() + ":" + (p.potionType() == null ? "-" : p.potionType().name()) + ":" + quantity;
        add(inv, 11, Material.LIME_CONCRETE, "Confirm", buying ? "buy" : (handOnly ? "sell-hand-confirm" : "sell"), txValue);
        ItemMeta confirmMeta = inv.getItem(11).getItemMeta();
        if (p.potionType() != null) confirmMeta.getPersistentDataContainer().set(potionTypeKey, PersistentDataType.STRING, p.potionType().name());
        inv.getItem(11).setItemMeta(confirmMeta);
        add(inv, 15, Material.RED_CONCRETE, "Cancel", "category-back", handOnly ? "main" : p.category());
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

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = ChatColor.stripColor(event.getView().getTitle());
        if (title == null || !title.startsWith("XZH SHOP")
                && !title.equals("Choose quantity") && !title.equals("Confirm transaction")) return;
        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        ItemMeta meta = clicked.getItemMeta();
        String action = meta.getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
        String value = meta.getPersistentDataContainer().get(materialKey, PersistentDataType.STRING);
        if (action == null || value == null) return;
        switch (action) {
            case "category" -> openCategory(player, value);
            case "books" -> plugin.getBookShopManager().open(player);
            case "quests" -> plugin.getQuestManager().open(player);
            case "deathinventory" -> plugin.getDeathRecoveryManager().open(player);
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
            case "product" -> {
                Product selected = productFromMeta(meta);
                if (selected != null) openQuantity(player, selected);
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
                if (material != null && quantity != null) openConfirm(player, selected == null ? material : selected, quantity, true);
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
                Product p = material == null ? null : (potionTypeName.equals("-") ? product(material) : potionProduct(material, potionTypeName));
                if (p == null && !action.equals("buy") && material != null)
                    p = new Product("misc", material, 0.0, plugin.getConfig().getDouble("shop.default-sell-price", 0.1));
                if (p == null || quantity < 1) return;
                if (!action.equals("buy") && isUnsellableExploitItem(material)) {
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
                    if (getStock(material) < quantity) {
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
                    java.util.Map<Integer, ItemStack> leftovers = player.getInventory().addItem(createStack(p, quantity));
                    int notGiven = leftovers.values().stream().mapToInt(ItemStack::getAmount).sum();
                    int delivered = quantity - notGiven;
                    if (delivered <= 0) {
                        plugin.tellPublic(player, "Your inventory is full.");
                        player.closeInventory();
                        return;
                    }
                    double charged = buyPrice(p) * delivered;
                    setStock(material, getStock(material) - delivered);
                    if (!plugin.spendAvailableFunds(player.getUniqueId(), charged)) {
                        // Return the delivered items and stock if a concurrent economy action changed the balance.
                        player.getInventory().removeItem(createStack(p, delivered));
                        setStock(material, getStock(material) + delivered);
                        plugin.tellPublic(player, "Purchase cancelled: your available funds changed.");
                        player.closeInventory();
                        return;
                    }
                    plugin.tellPublic(player, "Bought " + delivered + " × " + prettyProduct(p) + " for " + money(charged) + " coins.");
                    plugin.announceSpecialPurchase(player, material, delivered, charged);
                } else {
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
                    plugin.setBalance(player.getUniqueId(), plugin.getBalance(player.getUniqueId()) + earned);
                    plugin.tellPublic(player, "Sold " + sold + " × " + prettyProduct(p) + " for " + money(earned) + " coins.");
                }
                player.closeInventory();
            }
            case "sell-hand" -> {
                ItemStack hand = player.getInventory().getItemInMainHand();
                if (plugin.getPocketCoinManager().isCoin(hand)) {
                    plugin.tellPublic(player, "Physical pocket coins cannot be sold as shop items.");
                    return;
                }
                if (hand.getType() == Material.AIR) {
                    plugin.tellPublic(player, "Hold an item first.");
                    return;
                }
                if (isUnsellableExploitItem(hand.getType())) {
                    plugin.tellPublic(player, "This item cannot be sold to the XZH shop because it is exploit-prone or intended to remain rare.");
                    return;
                }
                Product p = productForStack(hand);
                double configuredSell = plugin.getConfig().getDouble(
                        "shop.prices." + hand.getType().name().toLowerCase(Locale.ROOT) + ".sell", defaultSellPrice());
                double unitSell = p == null
                        ? (validPrice(configuredSell) ? configuredSell : defaultSellPrice())
                        : sellPrice(p);
                if (unitSell * hand.getAmount() < 1) {
                    plugin.tellPublic(player, "The held stack is worth less than 1 coin and cannot be sold.");
                    return;
                }
                if (p != null && p.potionType() != null) openConfirm(player, p, hand.getAmount(), false, true);
                else openConfirm(player, hand.getType(), hand.getAmount(), false, true);
            }
        }
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
                : material == Material.DIAMOND_PICKAXE ? 30
                : material == Material.IRON_PICKAXE ? 100 : 1000;
        return Math.max(0, plugin.getConfig().getInt(
                "shop.stock-limits." + material.name().toLowerCase(Locale.ROOT), fallback));
    }

    private int getStock(Material material) {
        restockIfDue();
        return Math.max(0, stockData.getInt("items." + material.name().toLowerCase(Locale.ROOT), stockLimit(material)));
    }

    public void setStock(Material material, int amount) {
        stockData.set("items." + material.name().toLowerCase(Locale.ROOT), Math.max(0, amount));
        saveStock();
    }

    public void restockAll() {
        for (Product p : products) stockData.set("items." + p.material().name().toLowerCase(Locale.ROOT), stockLimit(p.material()));
        stockData.set("last-restock-date", LocalDate.now().toString());
        saveStock();
    }

    private void restockIfDue() {
        String today = LocalDate.now().toString();
        if (today.equals(stockData.getString("last-restock-date", ""))) return;
        restockAll();
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
        if (product.potionType() != null && stack.getItemMeta() instanceof PotionMeta meta) {
            try { meta.setBasePotionType(product.potionType()); } catch (IllegalArgumentException ignored) { }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private Product productFromMeta(ItemMeta meta) {
        String materialName = meta.getPersistentDataContainer().get(materialKey, PersistentDataType.STRING);
        Material material = materialName == null ? null : parseMaterial(materialName);
        if (material == null) return null;
        String typeName = meta.getPersistentDataContainer().get(potionTypeKey, PersistentDataType.STRING);
        return typeName == null ? product(material) : potionProduct(material, typeName);
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
        return type.name().toLowerCase(Locale.ROOT).replace('_', ' ').replaceAll("\\b\\w", m -> m.group().toUpperCase(Locale.ROOT));
    }

    private String prettyProduct(Product product) {
        return pretty(product.material()) + (product.potionType() == null ? "" : " - " + prettyPotion(product.potionType()));
    }

    private double buyPrice(Product p) {
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
        if (p.category().equals("misc")) return p.sell();
        if (p.potionType() != null) {
            String variantSell = "shop.potion-prices." + p.potionType().name().toLowerCase(Locale.ROOT) + "." + p.material().name().toLowerCase(Locale.ROOT) + ".sell";
            if (plugin.getConfig().contains(variantSell)) {
                double configuredVariant = plugin.getConfig().getDouble(variantSell, Math.floor(buyPrice(p) * 0.30));
                return Math.floor(validPrice(configuredVariant) ? configuredVariant : Math.floor(buyPrice(p) * 0.30));
            }
            return Math.floor(buyPrice(p) * 0.30);
        }
        String materialName = p.material().name().toLowerCase(Locale.ROOT);
        String pricePath = "shop.prices." + materialName + ".sell";
        boolean spawnEgg = materialName.endsWith("_spawn_egg");
        if (spawnEgg && plugin.getConfig().getBoolean("restrictions.buy-only-spawn-eggs", true)) return 0.0;
        if (plugin.getConfig().contains(pricePath)) {
            double configured = plugin.getConfig().getDouble(pricePath, p.sell());
            return Math.floor(validPrice(configured) ? configured : p.sell());
        }
        if (spawnEgg) return Math.floor(buyPrice(p) * 0.30);
        double configured = plugin.getConfig().getDouble(pricePath, p.sell());
        return Math.floor(validPrice(configured) ? configured : p.sell());
    }

    /** Items that must never be convertible into shop money, even if a config assigns a sell price. */
    private boolean isUnsellableExploitItem(Material material) {
        if (material == null) return true;
        String name = material.name();
        // Every trim template and the Netherite Upgrade template can be duplicated in vanilla crafting.
        if (name.endsWith("_SMITHING_TEMPLATE")) return true;
        // Rare items or drops commonly targeted by duplication glitches / scalable farms.
        return name.equals("ELYTRA")
                || name.equals("ENCHANTED_GOLDEN_APPLE")
                || name.equals("SHULKER_SHELL")
                || name.contains("SHULKER_BOX")
                || name.equals("TOTEM_OF_UNDYING")
                || name.equals("TRIDENT")
                || name.equals("WITHER_SKELETON_SKULL")
                || name.equals("NETHER_STAR")
                || name.equals("DRAGON_EGG")
                || name.equals("DRAGON_HEAD")
                || name.equals("END_CRYSTAL");
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
