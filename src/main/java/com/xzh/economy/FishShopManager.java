package com.xzh.economy;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Fish market and player collection browser, integrated with VillagerWorkstation specimen metadata. */
public final class FishShopManager implements Listener {
    private static final int[] CONTENT_SLOTS = {10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34,37,38,39,40,41,42,43};
    private static final String TITLE_PREFIX = "Fish Shop";
    private static final Set<String> CURATED_FISH_IDS = Set.of(
            "alpha_spiderfish", "catfish", "deadly_fin", "flarefin_koi", "king_of_the_pond",
            "ocean_sunfish", "rabbitfish", "seven_finned_death", "oarfish", "wyverntail",
            "princess_fish", "red_snapper", "mystery_fish", "arowana", "the_sounding",
            "the_beastfish", "tropical_barracuda", "saddled_bichir", "netherwrasse",
            "olive_flounder", "sturgeon", "pain_eel", "dorado", "eel", "touchy_fish",
            "moray_eel", "rusty_marlin", "football_fish", "unicorn_fish", "volcanic_bass",
            "laviathan_fry");
    private final XZHEconomySystem plugin;
    private final NamespacedKey actionKey;
    private final NamespacedKey valueKey;
    private final NamespacedKey fishIdKey = NamespacedKey.fromString("villagerworkstation:fish_uuid");
    private final NamespacedKey speciesKey = NamespacedKey.fromString("villagerworkstation:fish_species");
    private final NamespacedKey rarityKey = NamespacedKey.fromString("villagerworkstation:fish_rarity");
    private final NamespacedKey weightKey = NamespacedKey.fromString("villagerworkstation:fish_weight_kg");
    private final NamespacedKey lengthKey = NamespacedKey.fromString("villagerworkstation:fish_length_cm");
    private final NamespacedKey heightKey = NamespacedKey.fromString("villagerworkstation:fish_height_cm");
    private final NamespacedKey widthKey = NamespacedKey.fromString("villagerworkstation:fish_width_cm");
    private final NamespacedKey categoryKey = NamespacedKey.fromString("villagerworkstation:fish_size_category");
    private final NamespacedKey caughtAtKey = NamespacedKey.fromString("villagerworkstation:fish_caught_at");
    private final Map<String, FishSpecies> species = new HashMap<>();
    private final Map<UUID, FishSpecimen> pendingBuys = new HashMap<>();
    private final Map<UUID, List<String>> pendingSellAll = new HashMap<>();
    private final File collectionFile;

    private record FishSpecies(String id, String name, String rarity, String model,
                               double minWeight, double maxWeight, double typicalWeight,
                               double minLength, double maxLength, double minHeight, double maxHeight,
                               double minWidth, double maxWidth, double basePrice, String habitat, String rod, String time) {}
    private record FishSpecimen(UUID id, FishSpecies species, double weight, double length,
                                double height, double width, String size, long caughtAt) {}

    public FishShopManager(XZHEconomySystem plugin) {
        this.plugin = plugin;
        this.actionKey = new NamespacedKey(plugin, "fish_shop_action");
        this.valueKey = new NamespacedKey(plugin, "fish_shop_value");
        this.collectionFile = new File(plugin.getDataFolder().getParentFile(), "VillagerWorkstation/fish-collections.yml");
        loadCatalogue();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private void loadCatalogue() {
        File file = new File(plugin.getDataFolder(), "fish.yml");
        if (!file.exists()) plugin.saveResource("fish.yml", false);
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("fish");
        // Existing server installations keep their old fish.yml when a plugin JAR is replaced.
        // Back it up once, then install the bundled 31-species catalogue so removed fish cannot
        // remain purchasable/sellable through stale on-disk configuration.
        if (root == null || !isCuratedCatalogue(root)) {
            File backup = new File(plugin.getDataFolder(), "fish.yml.before-xzh-31.yml");
            try {
                if (file.exists() && !backup.exists()) {
                    Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
                }
                plugin.saveResource("fish.yml", true);
            } catch (IOException ex) {
                throw new IllegalStateException("Could not migrate the fish catalogue to the curated 31 species.", ex);
            }
            yaml = YamlConfiguration.loadConfiguration(file);
            root = yaml.getConfigurationSection("fish");
        }
        if (root == null || !isCuratedCatalogue(root)) {
            throw new IllegalStateException("Fish catalogue migration did not produce the required 31-species catalogue.");
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection f = root.getConfigurationSection(id);
            if (f == null) continue;
            String rarity = f.getString("rarity", "COMMON").toUpperCase(Locale.ROOT);
            species.put(id, new FishSpecies(id, f.getString("display-name", id), rarity,
                    f.getString("model", "barely_default:fish_" + id),
                    f.getDouble("weight.minimum"), f.getDouble("weight.maximum"), f.getDouble("weight.typical"),
                    f.getDouble("dimensions.length.minimum"), f.getDouble("dimensions.length.maximum"),
                    f.getDouble("dimensions.height.minimum"), f.getDouble("dimensions.height.maximum"),
                    f.getDouble("dimensions.width.minimum"), f.getDouble("dimensions.width.maximum"),
                    f.getDouble("value.base-price"), f.getString("habitat.description", "Unknown habitat"),
                    f.getString("habitat.rod", "Any fishing rod"), f.getString("habitat.time", "Any time")));
        }
        if (species.isEmpty()) throw new IllegalStateException("Fish catalogue contains no species.");
        plugin.getLogger().info("Fish Shop loaded " + species.size() + " species.");
    }

    private boolean isCuratedCatalogue(ConfigurationSection root) {
        Set<String> ids = new HashSet<>(root.getKeys(false));
        if (!ids.equals(CURATED_FISH_IDS)) return false;
        for (String id : CURATED_FISH_IDS) {
            ConfigurationSection fish = root.getConfigurationSection(id);
            if (fish == null || fish.getString("habitat.description", "").isBlank()
                    || fish.getString("habitat.rod", "").isBlank()
                    || fish.getString("habitat.time", "").isBlank()) return false;
        }
        return true;
    }

    public void openMain(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, ChatColor.DARK_AQUA + TITLE_PREFIX);

        ItemStack buyIcon = new ItemStack(Material.COD);
        ItemMeta buyMeta = buyIcon.getItemMeta();
        NamespacedKey customFishModel = NamespacedKey.fromString("barely_default:fish_alpha_spiderfish_medium");
        if (customFishModel != null) buyMeta.setItemModel(customFishModel);
        buyMeta.setDisplayName(ChatColor.YELLOW + "Buy Fish");
        buyMeta.setLore(List.of(ChatColor.GRAY + "Browse and buy XZH custom fish specimens."));
        buyMeta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "buy-menu");
        buyMeta.getPersistentDataContainer().set(valueKey, PersistentDataType.STRING, "buy");
        buyIcon.setItemMeta(buyMeta);
        inv.setItem(20, buyIcon);

        add(inv, 22, Material.HOPPER_MINECART, "Sell Hand (multiple items)", "sell-hand", "hand",
                "Place multiple fish stacks into the sell slots.",
                "The normal Shop Sell Hand menu will calculate the payout.",
                "All eligible fish sizes can be sold.");

        add(inv, 24, Material.BOOK, "Fish Collection", "collection-menu", "collection",
                "Browse all " + species.size() + " species and your catch discoveries.");
        add(inv, 49, Material.ARROW, "Back to Shop", "back-shop", "back", "Return to the main shop.");
        player.openInventory(inv);
    }

    private void openBuy(Player player, int requestedPage) {
        List<FishSpecies> all = orderedSpecies();
        int pages = Math.max(1, (all.size() + CONTENT_SLOTS.length - 1) / CONTENT_SLOTS.length);
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        Inventory inv = Bukkit.createInventory(null, 54, ChatColor.DARK_AQUA + TITLE_PREFIX + ": Buy " + (page + 1));
        int start = page * CONTENT_SLOTS.length;
        for (int i = start; i < Math.min(all.size(), start + CONTENT_SLOTS.length); i++) {
            FishSpecies fish = all.get(i);
            ItemStack icon = new ItemStack(Material.COD);
            ItemMeta meta = icon.getItemMeta();
            NamespacedKey catalogueModel = NamespacedKey.fromString(fish.model() + "_medium");
            if (catalogueModel != null) meta.setItemModel(catalogueModel);
            meta.setDisplayName(rarityColor(fish.rarity()) + fish.name());
            meta.setLore(List.of(ChatColor.GRAY + "Rarity: " + rarityColor(fish.rarity()) + fish.rarity(),
                    ChatColor.GRAY + "Found in: " + ChatColor.WHITE + fish.habitat(),
                    ChatColor.GRAY + "Rod: " + ChatColor.WHITE + fish.rod(),
                    ChatColor.GRAY + "Time: " + ChatColor.WHITE + fish.time(),
                    ChatColor.GRAY + "Typical weight: " + String.format(Locale.ROOT, "%.2f kg", fish.typicalWeight()),
                    pricesConfigured() ? ChatColor.GREEN + "Buy price: " + typicalPrice(fish) + " coins"
                            : ChatColor.RED + "Buy price: Not configured",
                    pricesConfigured() ? ChatColor.YELLOW + "Sell price: " + typicalSellPrice(fish) + " coins"
                            : ChatColor.RED + "Sell price: Not configured",
                    ChatColor.YELLOW + "Click to choose Small, Medium or Large."));
            meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "buy-select");
            meta.getPersistentDataContainer().set(valueKey, PersistentDataType.STRING, fish.id());
            icon.setItemMeta(meta);
            inv.setItem(CONTENT_SLOTS[i - start], icon);
        }
        add(inv, 45, Material.ARROW, "Back", "main", "main", "Return to Fish Shop.");
        if (page > 0) add(inv, 48, Material.PAPER, "Previous page", "buy-page", String.valueOf(page - 1), "Previous species.");
        add(inv, 49, Material.BOOK, "Page " + (page + 1) + "/" + pages, "noop", "noop", "Catalogue species: " + all.size());
        if (page + 1 < pages) add(inv, 50, Material.PAPER, "Next page", "buy-page", String.valueOf(page + 1), "Next species.");
        player.openInventory(inv);
    }

    private void openCollection(Player player, int requestedPage) {
        YamlConfiguration data = YamlConfiguration.loadConfiguration(collectionFile);
        String playerPath = "players." + player.getUniqueId();
        int catches = data.getInt(playerPath + ".total-catches", 0);
        List<FishSpecies> all = orderedSpecies();
        int discovered = (int) all.stream().filter(f -> data.getInt(playerPath + ".species." + f.id() + ".count", 0) > 0).count();
        int pages = Math.max(1, (all.size() + CONTENT_SLOTS.length - 1) / CONTENT_SLOTS.length);
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        Inventory inv = Bukkit.createInventory(null, 54, ChatColor.DARK_AQUA + TITLE_PREFIX + ": Collection " + (page + 1));
        int start = page * CONTENT_SLOTS.length;
        for (int i = start; i < Math.min(all.size(), start + CONTENT_SLOTS.length); i++) {
            FishSpecies fish = all.get(i);
            String path = playerPath + ".species." + fish.id();
            int count = data.getInt(path + ".count", 0);
            ItemStack icon = new ItemStack(count > 0 ? Material.COD : Material.GRAY_DYE);
            ItemMeta meta = icon.getItemMeta();
            if (count > 0) {
                NamespacedKey collectionModel = NamespacedKey.fromString(fish.model() + "_medium");
                if (collectionModel != null) meta.setItemModel(collectionModel);
            }
            String fishTooltipName = (count > 0 ? ChatColor.GREEN : ChatColor.GRAY) + fish.name();
            meta.setHideTooltip(false);
            meta.setItemName(fishTooltipName);
            meta.setDisplayName(fishTooltipName);
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Rarity: " + rarityColor(fish.rarity()) + fish.rarity());
            lore.add(ChatColor.YELLOW + "Found in: " + ChatColor.WHITE + fish.habitat());
            lore.add(ChatColor.YELLOW + "Rod: " + ChatColor.WHITE + fish.rod());
            lore.add(ChatColor.YELLOW + "Time: " + ChatColor.WHITE + fish.time());
            if (count > 0) {
                lore.add(ChatColor.GREEN + "Discovered");
                lore.add(ChatColor.GRAY + "Caught: " + ChatColor.WHITE + count);
                lore.add(ChatColor.GRAY + "Best weight: " + ChatColor.WHITE
                        + String.format(Locale.ROOT, "%.2f kg", data.getDouble(path + ".best-weight-kg", 0)));
                lore.add(ChatColor.GRAY + "Best size: " + ChatColor.WHITE + data.getString(path + ".best-size-category", "UNKNOWN"));
                lore.add(ChatColor.GRAY + "Best length: " + ChatColor.WHITE
                        + String.format(Locale.ROOT, "%.2f cm", data.getDouble(path + ".best-length-cm", 0)));
            } else lore.add(ChatColor.RED + "Not discovered yet");
            meta.setLore(lore);
            icon.setItemMeta(meta);
            inv.setItem(CONTENT_SLOTS[i - start], icon);
        }
        double completion = all.isEmpty() ? 0 : discovered * 100.0 / all.size();
        add(inv, 45, Material.ARROW, "Back", "main", "main", "Return to Fish Shop.");
        if (page > 0) add(inv, 48, Material.PAPER, "Previous page", "collection-page", String.valueOf(page - 1), "Previous species.");
        add(inv, 49, Material.BOOK, "Collection " + String.format(Locale.ROOT, "%.1f%%", completion),
                "noop", "noop", "Discovered " + discovered + "/" + all.size() + " species; total catches: " + catches + ".");
        if (page + 1 < pages) add(inv, 50, Material.PAPER, "Next page", "collection-page", String.valueOf(page + 1), "Next species.");
        player.openInventory(inv);
    }

    private void openBuySizes(Player player, String speciesId) {
        FishSpecies fish = species.get(speciesId);
        if (fish == null) return;
        Inventory inv = Bukkit.createInventory(null, 27,
                ChatColor.DARK_AQUA + TITLE_PREFIX + ": " + fish.name() + " Sizes");
        String[] sizes = {"SMALL", "MEDIUM", "LARGE"};
        int[] slots = {10, 13, 16};
        Material[] icons = {Material.COD, Material.SALMON, Material.PUFFERFISH};
        for (int i = 0; i < sizes.length; i++) {
            String size = sizes[i];
            FishSpecimen example = typicalSpecimenForSize(fish, size);
            ItemStack icon = new ItemStack(icons[i]);
            ItemMeta meta = icon.getItemMeta();
            NamespacedKey model = NamespacedKey.fromString(fish.model() + "_" + size.toLowerCase(Locale.ROOT));
            if (model != null) meta.setItemModel(model);
            meta.setDisplayName(ChatColor.AQUA + size.charAt(0) + size.substring(1).toLowerCase(Locale.ROOT)
                    + " " + fish.name());
            meta.setLore(List.of(
                    ChatColor.GRAY + "Size: " + ChatColor.WHITE + size,
                    ChatColor.GRAY + String.format(Locale.ROOT, "Typical weight: %.2f kg", example.weight()),
                    ChatColor.GREEN + "Buy price: " + price(example) + " coins",
                    ChatColor.YELLOW + "Sell price: " + sellPrice(example) + " coins",
                    ChatColor.GRAY + "Found in: " + fish.habitat(),
                    ChatColor.GRAY + "Rod: " + fish.rod(),
                    ChatColor.GRAY + "Time: " + fish.time(),
                    ChatColor.YELLOW + "Click to choose this size."));
            meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "buy-size-select");
            meta.getPersistentDataContainer().set(valueKey, PersistentDataType.STRING, speciesId + "|" + size);
            icon.setItemMeta(meta);
            inv.setItem(slots[i], icon);
        }
        add(inv, 22, Material.ARROW, "Back to catalogue", "buy-back", "0", "Return to the fish catalogue.");
        player.openInventory(inv);
    }

    private void openBuyConfirm(Player player, String speciesId, String requestedSize) {
        FishSpecies fish = species.get(speciesId);
        if (fish == null || !List.of("SMALL", "MEDIUM", "LARGE").contains(requestedSize.toUpperCase(Locale.ROOT))) return;
        FishSpecimen specimen = generate(fish, requestedSize);
        pendingBuys.put(player.getUniqueId(), specimen);
        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.DARK_AQUA + TITLE_PREFIX + ": Confirm Buy");
        inv.setItem(13, createFishItem(specimen));
        add(inv, 11, Material.LIME_CONCRETE, "Confirm Purchase", "buy-confirm", speciesId, "Cost: " + price(specimen) + " coins.");
        add(inv, 15, Material.RED_CONCRETE, "Cancel", "main", "main", "Do not buy this fish.");
        add(inv, 22, Material.ARROW, "Back to catalogue", "buy-back", "0", "Return to the fish catalogue.");
        player.openInventory(inv);
    }

    private void openSellAllConfirm(Player player) {
        List<String> specimenIds = new ArrayList<>();
        long totalValue = 0;
        ItemStack[] inventoryContents = player.getInventory().getStorageContents();
        for (int slot = 0; slot < inventoryContents.length; slot++) {
            ItemStack item = inventoryContents[slot];
            normalizeFishMetadata(item);
            if (!isCustomFish(item)) continue;
            player.getInventory().setItem(slot, item);
            String id = item.getItemMeta().getPersistentDataContainer().get(fishIdKey, PersistentDataType.STRING);
            if (id == null || specimenIds.contains(id)) continue;
            specimenIds.add(id);
            totalValue += sellPrice(item);
        }
        if (specimenIds.isEmpty()) {
            plugin.tellShop(player, "You have no eligible XZH custom fish to sell.");
            openMain(player);
            return;
        }
        pendingSellAll.put(player.getUniqueId(), List.copyOf(specimenIds));
        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.DARK_AQUA + TITLE_PREFIX + ": Confirm Sell All");
        add(inv, 13, Material.HOPPER, "Sell " + specimenIds.size() + " Fish", "noop", "noop",
                "Total payout: " + totalValue + " coins", "Only XZH custom fish will be sold.");
        add(inv, 11, Material.LIME_CONCRETE, "Confirm Sell All", "sell-all-confirm", "all",
                "Payout: " + totalValue + " coins.");
        add(inv, 15, Material.RED_CONCRETE, "Cancel", "main", "main", "Keep all your fish.");
        add(inv, 22, Material.ARROW, "Back", "main", "main", "Return to Fish Shop.");
        player.openInventory(inv);
    }

    private void confirmSellAll(Player player) {
        if (!pricesConfigured()) { plugin.tellShop(player, "Fish prices have not been configured yet."); return; }
        List<String> specimenIds = pendingSellAll.remove(player.getUniqueId());
        if (specimenIds == null || specimenIds.isEmpty()) return;
        long totalPayout = 0;
        int sold = 0;
        for (String id : specimenIds) {
            ItemStack held = findSpecimen(player, id);
            if (held == null) continue;
            int payout = sellPrice(held);
            removeSpecimen(player, id);
            totalPayout += payout;
            sold++;
        }
        if (sold == 0) {
            plugin.tellShop(player, "No listed fish were found in your inventory.");
            openMain(player);
            return;
        }
        plugin.setBalance(player.getUniqueId(), plugin.getBalance(player.getUniqueId()) + totalPayout);
        plugin.tellShop(player, "Sold " + sold + " custom fish for " + totalPayout + " coins.");
        player.closeInventory();
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = ChatColor.stripColor(event.getView().getTitle());
        if (title == null || !title.startsWith(TITLE_PREFIX)) return;
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        PersistentDataContainer pdc = clicked.getItemMeta().getPersistentDataContainer();
        String action = pdc.get(actionKey, PersistentDataType.STRING);
        String value = pdc.get(valueKey, PersistentDataType.STRING);
        if (action == null) return;
        switch (action) {
            case "buy-menu" -> openBuy(player, 0);
            case "collection-menu" -> openCollection(player, 0);
            case "buy-page" -> openBuy(player, parsePage(value));
            case "collection-page" -> openCollection(player, parsePage(value));
            case "buy-select" -> { if (pricesConfigured()) openBuySizes(player, value); else plugin.tellShop(player, "Fish prices have not been configured yet."); }
            case "buy-size-select" -> {
                String[] selection = value == null ? new String[0] : value.split("\\|", 2);
                if (selection.length == 2) openBuyConfirm(player, selection[0], selection[1]);
            }
            case "sell-hand" -> plugin.getShopManager().openFishHandSell(player);
            case "buy-confirm" -> confirmBuy(player, value);
            case "main" -> openMain(player);
            case "back-shop" -> plugin.getShopManager().openMain(player);
            case "buy-back" -> openBuy(player, parsePage(value));
            case "noop" -> { }
        }
    }

    private void confirmBuy(Player player, String speciesId) {
        if (!pricesConfigured()) { plugin.tellShop(player, "Fish prices have not been configured yet."); return; }
        FishSpecimen specimen = pendingBuys.get(player.getUniqueId());
        if (specimen == null || !specimen.species().id().equals(speciesId)) return;
        if (plugin.getLoanManager().isShopPurchaseRestricted(player.getUniqueId())) {
            plugin.tellShop(player, "Your loan is overdue. Shop purchases are locked.");
            return;
        }
        int cost = price(specimen);
        if (plugin.getCombinedBalance(player.getUniqueId()) < cost) {
            plugin.tellShop(player, "Not enough coins to buy this fish.");
            return;
        }
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(createFishItem(specimen));
        if (!leftovers.isEmpty()) {
            plugin.tellShop(player, "Your inventory is full. Purchase cancelled.");
            return;
        }
        if (!plugin.spendAvailableFunds(player.getUniqueId(), cost)) {
            removeSpecimen(player, specimen.id().toString());
            plugin.tellShop(player, "Purchase cancelled because your available funds changed.");
            return;
        }
        pendingBuys.remove(player.getUniqueId());
        plugin.tellShop(player, "Bought " + specimen.species().name() + " (" + specimen.size().toLowerCase(Locale.ROOT)
                + ") for " + cost + " coins. Purchased fish do not count as collection discoveries.");
        player.closeInventory();
    }

    private ItemStack createFishItem(FishSpecimen fish) {
        ItemStack item = new ItemStack(Material.COD);
        ItemMeta meta = item.getItemMeta();
        NamespacedKey model = NamespacedKey.fromString(fish.species().model() + "_" + fish.size().toLowerCase(Locale.ROOT));
        if (model != null) meta.setItemModel(model);
        String sizeName = fish.size().charAt(0) + fish.size().substring(1).toLowerCase(Locale.ROOT);
        meta.setDisplayName(ChatColor.AQUA + sizeName + " " + fish.species().name());
        meta.setLore(List.of(ChatColor.GRAY + "Species: " + ChatColor.WHITE + fish.species().name(),
                ChatColor.GRAY + "Size: " + ChatColor.WHITE + sizeName,
                ChatColor.GRAY + "Rarity: " + ChatColor.WHITE + fish.species().rarity(),
                ChatColor.GRAY + String.format(Locale.ROOT, "Weight: %.2f kg", fish.weight()),
                ChatColor.GRAY + String.format(Locale.ROOT, "Length: %.2f cm", fish.length()),
                ChatColor.GRAY + String.format(Locale.ROOT, "Height: %.2f cm", fish.height()),
                ChatColor.GRAY + String.format(Locale.ROOT, "Width: %.2f cm", fish.width()),
                ChatColor.GREEN + "Buy price: " + price(fish) + " coins",
                ChatColor.YELLOW + "Sell price: " + sellPrice(fish) + " coins",
                ChatColor.GRAY + "Found in: " + fish.species().habitat(),
                ChatColor.GRAY + "Rod: " + fish.species().rod(),
                ChatColor.GRAY + "Time: " + fish.species().time(),
                ChatColor.DARK_GRAY + "Specimen #" + fish.id().toString().substring(0, 8)));
        PersistentDataContainer data = meta.getPersistentDataContainer();
        data.set(fishIdKey, PersistentDataType.STRING, fish.id().toString());
        data.set(speciesKey, PersistentDataType.STRING, fish.species().id());
        data.set(rarityKey, PersistentDataType.STRING, fish.species().rarity());
        data.set(weightKey, PersistentDataType.DOUBLE, fish.weight());
        data.set(lengthKey, PersistentDataType.DOUBLE, fish.length());
        data.set(heightKey, PersistentDataType.DOUBLE, fish.height());
        data.set(widthKey, PersistentDataType.DOUBLE, fish.width());
        data.set(categoryKey, PersistentDataType.STRING, fish.size());
        data.set(caughtAtKey, PersistentDataType.LONG, fish.caughtAt());
        item.setItemMeta(meta);
        return item;
    }

    private FishSpecimen generate(FishSpecies fish, String requestedSize) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        String size = requestedSize.toUpperCase(Locale.ROOT);
        double bandStart = switch (size) { case "SMALL" -> 0.00; case "MEDIUM" -> 0.25; case "LARGE" -> 0.50; default -> 0.00; };
        double bandEnd = switch (size) { case "SMALL" -> 0.25; case "MEDIUM" -> 0.50; case "LARGE" -> 0.75; default -> 0.25; };
        double weightRange = fish.maxWeight() - fish.minWeight();
        double normalized = bandStart + (bandEnd - bandStart) * random.nextDouble();
        double weight = fish.minWeight() + weightRange * normalized;
        double length = interpolate(fish.minLength(), fish.maxLength(), normalized) * random.nextDouble(0.96, 1.041);
        double height = interpolate(fish.minHeight(), fish.maxHeight(), normalized) * random.nextDouble(0.96, 1.041);
        double width = interpolate(fish.minWidth(), fish.maxWidth(), normalized) * random.nextDouble(0.96, 1.041);
        return new FishSpecimen(UUID.randomUUID(), fish, round(weight), round(length), round(height), round(width), size,
                System.currentTimeMillis());
    }

    private FishSpecimen typicalSpecimenForSize(FishSpecies fish, String requestedSize) {
        String size = requestedSize.toUpperCase(Locale.ROOT);
        double normalized = switch (size) { case "SMALL" -> 0.125; case "MEDIUM" -> 0.375; case "LARGE" -> 0.625; default -> 0.375; };
        double weight = interpolate(fish.minWeight(), fish.maxWeight(), normalized);
        double length = interpolate(fish.minLength(), fish.maxLength(), normalized);
        double height = interpolate(fish.minHeight(), fish.maxHeight(), normalized);
        double width = interpolate(fish.minWidth(), fish.maxWidth(), normalized);
        return new FishSpecimen(UUID.randomUUID(), fish, round(weight), round(length), round(height), round(width), size,
                System.currentTimeMillis());
    }

    private double triangular(ThreadLocalRandom random, double min, double max, double mode) {
        if (max <= min) return min;
        double split = (mode - min) / (max - min);
        if (split <= 0 || split >= 1) return random.nextDouble(min, max);
        double u = random.nextDouble();
        return u < split ? min + Math.sqrt(u * (max - min) * (mode - min))
                : max - Math.sqrt((1 - u) * (max - min) * (max - mode));
    }

    private double interpolate(double min, double max, double ratio) {
        return min + (max - min) * Math.max(0, Math.min(1, ratio));
    }

    private int price(FishSpecimen fish) {
        return priceFor(fish.species(), fish.weight(), fish.length(), fish.height(), fish.width());
    }

    private int typicalPrice(FishSpecies fish) {
        double normalized = fish.typicalWeight() <= fish.minWeight() ? 0
                : (fish.typicalWeight() - fish.minWeight()) / Math.max(0.000001, fish.maxWeight() - fish.minWeight());
        return priceFor(fish, fish.typicalWeight(),
                interpolate(fish.minLength(), fish.maxLength(), normalized),
                interpolate(fish.minHeight(), fish.maxHeight(), normalized),
                interpolate(fish.minWidth(), fish.maxWidth(), normalized));
    }

    private int priceFor(FishSpecies fish, double weight, double length, double height, double width) {
        if (!pricesConfigured()) return 0;
        String rarityPath = "fish-market.prices." + fish.rarity().toLowerCase(Locale.ROOT);
        double configured = plugin.getConfig().getDouble(rarityPath, defaultRarityPrice(fish.rarity()));
        // Upgrade untouched legacy defaults while preserving any server-admin custom prices.
        if (Math.abs(configured - legacyRarityPrice(fish.rarity())) < 0.000001) {
            configured = defaultRarityPrice(fish.rarity());
        }
        double base = fish.basePrice() > 0 ? fish.basePrice() : configured;
        double weightFactor = relativeFactor(weight, fish.typicalWeight());
        double lengthFactor = relativeFactor(length, typicalDimension(fish.minLength(), fish.maxLength(), fish));
        double heightFactor = relativeFactor(height, typicalDimension(fish.minHeight(), fish.maxHeight(), fish));
        double widthFactor = relativeFactor(width, typicalDimension(fish.minWidth(), fish.maxWidth(), fish));
        double factor = Math.pow(weightFactor * lengthFactor * heightFactor * widthFactor, 0.25);
        return Math.max(1, (int) Math.round(base * Math.max(0.5, Math.min(2.0, factor))));
    }

    private double typicalDimension(double min, double max, FishSpecies fish) {
        double normalized = fish.typicalWeight() <= fish.minWeight() ? 0
                : (fish.typicalWeight() - fish.minWeight()) / Math.max(0.000001, fish.maxWeight() - fish.minWeight());
        return interpolate(min, max, normalized);
    }

    private double relativeFactor(double value, double typical) {
        return typical <= 0 ? 1 : Math.max(0.5, Math.min(2.0, value / typical));
    }

    private int sellPrice(ItemStack fish) {
        if (fish == null || !fish.hasItemMeta()) return 0;
        PersistentDataContainer data = fish.getItemMeta().getPersistentDataContainer();
        FishSpecies definition = species.get(data.get(speciesKey, PersistentDataType.STRING));
        if (definition == null) return 0;
        double weight = data.getOrDefault(weightKey, PersistentDataType.DOUBLE, definition.typicalWeight());
        double length = data.getOrDefault(lengthKey, PersistentDataType.DOUBLE,
                typicalDimension(definition.minLength(), definition.maxLength(), definition));
        double height = data.getOrDefault(heightKey, PersistentDataType.DOUBLE,
                typicalDimension(definition.minHeight(), definition.maxHeight(), definition));
        double width = data.getOrDefault(widthKey, PersistentDataType.DOUBLE,
                typicalDimension(definition.minWidth(), definition.maxWidth(), definition));
        return sellPrice(definition, weight, length, height, width);
    }

    private int sellPrice(FishSpecimen fish) {
        return sellPrice(fish.species(), fish.weight(), fish.length(), fish.height(), fish.width());
    }

    private int typicalSellPrice(FishSpecies fish) {
        double normalized = fish.typicalWeight() <= fish.minWeight() ? 0
                : (fish.typicalWeight() - fish.minWeight()) / Math.max(0.000001, fish.maxWeight() - fish.minWeight());
        return sellPrice(fish, fish.typicalWeight(),
                interpolate(fish.minLength(), fish.maxLength(), normalized),
                interpolate(fish.minHeight(), fish.maxHeight(), normalized),
                interpolate(fish.minWidth(), fish.maxWidth(), normalized));
    }

    private int sellPrice(FishSpecies fish, double weight, double length, double height, double width) {
        if (!pricesConfigured()) return 0;
        int base = priceFor(fish, weight, length, height, width);
        double multiplier = plugin.getConfig().getDouble("fish-market.sell-multiplier", 0.35);
        return Math.max(1, (int) Math.round(base * Math.max(0, Math.min(1, multiplier))));
    }

    private boolean pricesConfigured() {
        return plugin.getConfig().getBoolean("fish-market.prices-configured", false);
    }

    private int defaultRarityPrice(String rarity) {
        return switch (rarity) {
            case "UNCOMMON" -> 25;
            case "RARE" -> 100;
            case "EPIC" -> 500;
            case "LEGENDARY" -> 2500;
            case "MYTHICAL" -> 25000;
            default -> 5;
        };
    }

    private int legacyRarityPrice(String rarity) {
        return switch (rarity) {
            case "UNCOMMON" -> 40;
            case "RARE" -> 80;
            case "EPIC" -> 160;
            case "LEGENDARY" -> 400;
            case "MYTHICAL" -> 1000;
            default -> 20;
        };
    }

    private List<FishSpecies> orderedSpecies() {
        return species.values().stream().sorted(Comparator.comparingInt((FishSpecies f) -> rarityOrder(f.rarity()))
                .thenComparing(FishSpecies::name, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    private int rarityOrder(String rarity) {
        return switch (rarity) { case "COMMON" -> 0; case "UNCOMMON" -> 1; case "RARE" -> 2;
            case "EPIC" -> 3; case "LEGENDARY" -> 4; case "MYTHICAL" -> 5; default -> 6; };
    }

    private ChatColor rarityColor(String rarity) {
        return switch (rarity) { case "COMMON" -> ChatColor.WHITE; case "UNCOMMON" -> ChatColor.GREEN;
            case "RARE" -> ChatColor.BLUE; case "EPIC" -> ChatColor.DARK_PURPLE;
            case "LEGENDARY" -> ChatColor.GOLD; case "MYTHICAL" -> ChatColor.LIGHT_PURPLE; default -> ChatColor.GRAY; };
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> refreshInventoryFishInfo(event.getPlayer()));
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player) refreshInventoryFishInfo(player);
    }

    @EventHandler
    public void onFishPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            ItemStack pickedUp = event.getItem().getItemStack();
            if (normalizeFishMetadata(pickedUp)) event.getItem().setItemStack(pickedUp);
            Bukkit.getScheduler().runTask(plugin, () -> refreshInventoryFishInfo(player));
        }
    }

    @EventHandler
    public void onFishingCatch(PlayerFishEvent event) {
        if (event.getState() == PlayerFishEvent.State.CAUGHT_FISH) {
            Player player = event.getPlayer();
            Bukkit.getScheduler().runTask(plugin, () -> refreshInventoryFishInfo(player));
        }
    }

    @EventHandler
    public void onPlayerInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            Bukkit.getScheduler().runTask(plugin, () -> refreshInventoryFishInfo(player));
        }
    }

    private void refreshInventoryFishInfo(Player player) {
        if (!player.isOnline()) return;
        ItemStack[] contents = player.getInventory().getStorageContents();
        boolean changed = false;
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            normalizeFishMetadata(item);
            if (!isCustomFish(item)) continue;
            ItemMeta meta = item.getItemMeta();
            PersistentDataContainer data = meta.getPersistentDataContainer();
            FishSpecies definition = species.get(data.get(speciesKey, PersistentDataType.STRING));
            if (definition == null) continue;

            double weight = data.getOrDefault(weightKey, PersistentDataType.DOUBLE, definition.typicalWeight());
            double length = data.getOrDefault(lengthKey, PersistentDataType.DOUBLE,
                    typicalDimension(definition.minLength(), definition.maxLength(), definition));
            double height = data.getOrDefault(heightKey, PersistentDataType.DOUBLE,
                    typicalDimension(definition.minHeight(), definition.maxHeight(), definition));
            double width = data.getOrDefault(widthKey, PersistentDataType.DOUBLE,
                    typicalDimension(definition.minWidth(), definition.maxWidth(), definition));
            int buy = priceFor(definition, weight, length, height, width);
            int sell = sellPrice(definition, weight, length, height, width);

            List<String> lore = new ArrayList<>();
            for (String line : meta.getLore() == null ? List.<String>of() : meta.getLore()) {
                String plain = ChatColor.stripColor(line);
                if (plain == null || !(plain.startsWith("Buy price:") || plain.startsWith("Sell price:")
                        || plain.startsWith("Found in:") || plain.startsWith("Rod:") || plain.startsWith("Time:"))) {
                    lore.add(line);
                }
            }
            lore.add(ChatColor.GREEN + "Buy price: " + buy + " coins");
            lore.add(ChatColor.YELLOW + "Sell price: " + sell + " coins");
            lore.add(ChatColor.GRAY + "Found in: " + definition.habitat());
            lore.add(ChatColor.GRAY + "Rod: " + definition.rod());
            lore.add(ChatColor.GRAY + "Time: " + definition.time());
            meta.setLore(lore);
            item.setItemMeta(meta);
            player.getInventory().setItem(slot, item);
            changed = true;
        }
        if (changed) player.updateInventory();
    }

    /**
     * Repairs older/raw datapack fish that have a curated custom item model but
     * lack VillagerWorkstation specimen PDC. Only known catalogue models qualify.
     */
    private boolean normalizeFishMetadata(ItemStack item) {
        if (item == null || item.getType() != Material.COD || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        NamespacedKey model = meta.getItemModel();
        if (model == null || !model.getNamespace().equals("barely_default")
                || !model.getKey().startsWith("fish_")) return false;
        String id = model.getKey().substring("fish_".length());
        String modelSize = null;
        for (String size : List.of("_small", "_medium", "_large", "_giant")) {
            if (id.endsWith(size)) {
                id = id.substring(0, id.length() - size.length());
                modelSize = size.substring(1).toUpperCase(Locale.ROOT);
                break;
            }
        }
        FishSpecies definition = species.get(id);
        if (definition == null) return false;

        PersistentDataContainer data = meta.getPersistentDataContainer();
        if (!data.has(fishIdKey, PersistentDataType.STRING)) {
            data.set(fishIdKey, PersistentDataType.STRING, UUID.randomUUID().toString());
        }
        data.set(speciesKey, PersistentDataType.STRING, definition.id());
        data.set(rarityKey, PersistentDataType.STRING, definition.rarity());
        data.set(weightKey, PersistentDataType.DOUBLE,
                data.getOrDefault(weightKey, PersistentDataType.DOUBLE, definition.typicalWeight()));
        data.set(lengthKey, PersistentDataType.DOUBLE, data.getOrDefault(lengthKey, PersistentDataType.DOUBLE,
                typicalDimension(definition.minLength(), definition.maxLength(), definition)));
        data.set(heightKey, PersistentDataType.DOUBLE, data.getOrDefault(heightKey, PersistentDataType.DOUBLE,
                typicalDimension(definition.minHeight(), definition.maxHeight(), definition)));
        data.set(widthKey, PersistentDataType.DOUBLE, data.getOrDefault(widthKey, PersistentDataType.DOUBLE,
                typicalDimension(definition.minWidth(), definition.maxWidth(), definition)));
        data.set(categoryKey, PersistentDataType.STRING,
                data.getOrDefault(categoryKey, PersistentDataType.STRING,
                        modelSize == null ? "MEDIUM" : modelSize));
        data.set(caughtAtKey, PersistentDataType.LONG,
                data.getOrDefault(caughtAtKey, PersistentDataType.LONG, System.currentTimeMillis()));
        item.setItemMeta(meta);
        return true;
    }

    public boolean isHandSellFish(ItemStack item) {
        normalizeFishMetadata(item);
        return isCustomFish(item);
    }

    public int getHandSellFishPrice(ItemStack item) {
        return isCustomFish(item) ? sellPrice(item) : 0;
    }

    private boolean isCustomFish(ItemStack item) {
        if (item == null || item.getType() != Material.COD || !item.hasItemMeta()) return false;
        PersistentDataContainer data = item.getItemMeta().getPersistentDataContainer();
        return data.has(fishIdKey, PersistentDataType.STRING) && data.has(speciesKey, PersistentDataType.STRING)
                && species.containsKey(data.get(speciesKey, PersistentDataType.STRING));
    }

    private ItemStack findSpecimen(Player player, String specimenId) {
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (!isCustomFish(item)) continue;
            if (specimenId.equals(item.getItemMeta().getPersistentDataContainer().get(fishIdKey, PersistentDataType.STRING))) return item;
        }
        return null;
    }

    private void removeSpecimen(Player player, String specimenId) {
        for (int slot = 0; slot < player.getInventory().getStorageContents().length; slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (!isCustomFish(item)) continue;
            if (specimenId.equals(item.getItemMeta().getPersistentDataContainer().get(fishIdKey, PersistentDataType.STRING))) {
                player.getInventory().setItem(slot, null);
                return;
            }
        }
    }

    private String speciesName(ItemStack fish) {
        FishSpecies definition = species.get(fish.getItemMeta().getPersistentDataContainer().get(speciesKey, PersistentDataType.STRING));
        return definition == null ? "fish" : definition.name();
    }

    private int parsePage(String value) {
        try { return Integer.parseInt(value); } catch (RuntimeException ex) { return 0; }
    }

    private double round(double value) { return Math.round(value * 100.0) / 100.0; }

    private List<String> append(List<String> original, String... additions) {
        List<String> result = new ArrayList<>(original == null ? List.of() : original);
        result.addAll(List.of(additions));
        return result;
    }

    private void add(Inventory inv, int slot, Material material, String name, String action, String value, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.YELLOW + name);
        meta.setLore(java.util.Arrays.stream(lore).map(line -> ChatColor.GRAY + line).toList());
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
        meta.getPersistentDataContainer().set(valueKey, PersistentDataType.STRING, value);
        item.setItemMeta(meta);
        inv.setItem(slot, item);
    }
}
