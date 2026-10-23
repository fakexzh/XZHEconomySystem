package com.xzh.economy;

import io.papermc.paper.event.player.PlayerTradeEvent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.VillagerAcquireTradeEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Vanilla-interface XZH profession market. Offers rotate globally per profession, while
 * each player has an independent one-trade-per-offer allowance for each 4-hour rotation.
 * XZH coins are the real tagged pocket coins.
 */
public final class VillagerTradeManager implements Listener {
    private static final long REFRESH_MILLIS = 4L * 60L * 60L * 1000L;
    private final XZHEconomySystem plugin;
    private final File file;
    private final YamlConfiguration data;
    private final Map<String, Offer> all = new LinkedHashMap<>();
    private final Map<Villager.Profession, List<Offer>> active = new HashMap<>();
    private final Map<UUID, Set<String>> playerUsedOffers = new HashMap<>();
    private long nextRefresh;
    private BukkitTask ticker;

    private enum Kind { PLAYER_SELLS, PLAYER_SELLS_POTION, PLAYER_BUYS, PLAYER_BUYS_POTION, BARTER }
    private record Offer(String id, Villager.Profession profession, Kind kind,
                         Material item, int amount, Material other, int otherAmount,
                         int stock, double payoutRate) {}

    public VillagerTradeManager(XZHEconomySystem plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "villager-global-stock.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
        defineOffers();
        loadOrRefresh();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        ticker = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (System.currentTimeMillis() >= nextRefresh) refreshAll();
        }, 20L, 20L);
    }

    private void defineOffers() {
        // Expanded pool: 5 coin offers and 3 item-for-item offers per profession.
        // Six random offers from each pool are shown for each 4-hour rotation.
        // Farmer
        add("farmer_wheat", Villager.Profession.FARMER, Kind.PLAYER_SELLS, Material.WHEAT, 32, null, 0, 80, .70);
        add("farmer_carrot", Villager.Profession.FARMER, Kind.PLAYER_SELLS, Material.CARROT, 32, null, 0, 80, .70);
        add("farmer_pumpkin", Villager.Profession.FARMER, Kind.PLAYER_SELLS, Material.PUMPKIN, 16, null, 0, 48, .70);
        add("farmer_bread", Villager.Profession.FARMER, Kind.PLAYER_BUYS, Material.BREAD, 16, null, 0, 32, 1.05);
        add("farmer_golden_carrot", Villager.Profession.FARMER, Kind.PLAYER_BUYS, Material.GOLDEN_CARROT, 4, null, 0, 12, 1.05);
        add("farmer_wheat_bread", Villager.Profession.FARMER, Kind.BARTER, Material.WHEAT, 24, Material.BREAD, 8, 24, 1.0);
        add("farmer_potato_carrot", Villager.Profession.FARMER, Kind.BARTER, Material.POTATO, 32, Material.CARROT, 24, 24, 1.0);
        add("farmer_beetroot_soup", Villager.Profession.FARMER, Kind.BARTER, Material.BEETROOT, 32, Material.BEETROOT_SOUP, 4, 16, 1.0);

        // Fisherman
        add("fish_cod", Villager.Profession.FISHERMAN, Kind.PLAYER_SELLS, Material.COD, 16, null, 0, 64, .75);
        add("fish_salmon", Villager.Profession.FISHERMAN, Kind.PLAYER_SELLS, Material.SALMON, 16, null, 0, 48, .75);
        add("fish_string", Villager.Profession.FISHERMAN, Kind.PLAYER_SELLS, Material.STRING, 32, null, 0, 32, .65);
        add("fish_cooked_cod", Villager.Profession.FISHERMAN, Kind.PLAYER_BUYS, Material.COOKED_COD, 16, null, 0, 32, 1.05);
        add("fish_tropical_bucket", Villager.Profession.FISHERMAN, Kind.PLAYER_SELLS, Material.TROPICAL_FISH_BUCKET, 1, null, 0, 6, .65);
        add("fish_redstone_cod", Villager.Profession.FISHERMAN, Kind.BARTER, Material.REDSTONE, 64, Material.COD, 50, 8, 1.0);
        add("fish_lapis_salmon", Villager.Profession.FISHERMAN, Kind.BARTER, Material.LAPIS_LAZULI, 64, Material.COOKED_SALMON, 30, 8, 1.0);
        add("fish_cod_salmon", Villager.Profession.FISHERMAN, Kind.BARTER, Material.COD, 24, Material.SALMON, 16, 24, 1.0);

        // Cleric / potion villager
        add("cleric_blaze", Villager.Profession.CLERIC, Kind.PLAYER_SELLS, Material.BLAZE_ROD, 8, null, 0, 24, .60);
        add("cleric_ender", Villager.Profession.CLERIC, Kind.PLAYER_SELLS, Material.ENDER_PEARL, 8, null, 0, 24, .65);
        add("cleric_glowstone", Villager.Profession.CLERIC, Kind.PLAYER_BUYS, Material.GLOWSTONE_DUST, 16, null, 0, 32, 1.05);
        add("cleric_redstone", Villager.Profession.CLERIC, Kind.PLAYER_BUYS, Material.REDSTONE, 32, null, 0, 32, 1.05);
        add("cleric_strength_potion", Villager.Profession.CLERIC, Kind.PLAYER_BUYS_POTION, Material.BLAZE_POWDER, 4, null, 1, 12, 2.0);
        add("cleric_fire_resistance_potion", Villager.Profession.CLERIC, Kind.PLAYER_BUYS_POTION, Material.MAGMA_CREAM, 4, null, 1, 12, 2.0);
        // Potion market: buy brewed potions from players and sell useful potions for coins.
        for (String potionId : List.of("healing", "regeneration", "strength", "fire_resistance",
                "swiftness", "water_breathing", "night_vision", "slow_falling")) {
            add("cleric_potion_buy_" + potionId, Villager.Profession.CLERIC,
                    Kind.PLAYER_BUYS_POTION, Material.POTION, 1, null, 0, 12, 1.0);
            add("cleric_potion_sell_" + potionId, Villager.Profession.CLERIC,
                    Kind.PLAYER_SELLS_POTION, Material.POTION, 1, null, 0, 12, 0.45);
        }
        add("cleric_wither_skull", Villager.Profession.CLERIC, Kind.PLAYER_SELLS,
                Material.WITHER_SKELETON_SKULL, 1, null, 0, 2, .50);
        add("cleric_netherwart_glowstone", Villager.Profession.CLERIC, Kind.BARTER, Material.NETHER_WART, 16, Material.GLOWSTONE_DUST, 12, 20, 1.0);
        add("cleric_ghast_blaze", Villager.Profession.CLERIC, Kind.BARTER, Material.GHAST_TEAR, 2, Material.BLAZE_ROD, 4, 12, 1.0);

        // Librarian
        add("librarian_paper", Villager.Profession.LIBRARIAN, Kind.PLAYER_SELLS, Material.PAPER, 32, null, 0, 48, .70);
        add("librarian_book", Villager.Profession.LIBRARIAN, Kind.PLAYER_SELLS, Material.BOOK, 8, null, 0, 24, .70);
        add("librarian_lapis", Villager.Profession.LIBRARIAN, Kind.PLAYER_BUYS, Material.LAPIS_LAZULI, 16, null, 0, 32, 1.05);
        add("librarian_glass", Villager.Profession.LIBRARIAN, Kind.PLAYER_BUYS, Material.GLASS, 16, null, 0, 24, 1.05);
        add("librarian_ink", Villager.Profession.LIBRARIAN, Kind.PLAYER_SELLS, Material.INK_SAC, 16, null, 0, 20, .65);
        add("librarian_book_paper", Villager.Profession.LIBRARIAN, Kind.BARTER, Material.PAPER, 24, Material.BOOK, 4, 24, 1.0);
        add("librarian_lapis_redstone", Villager.Profession.LIBRARIAN, Kind.BARTER, Material.LAPIS_LAZULI, 32, Material.REDSTONE, 48, 20, 1.0);
        add("librarian_ink_paper", Villager.Profession.LIBRARIAN, Kind.BARTER, Material.INK_SAC, 8, Material.PAPER, 24, 20, 1.0);

        // Mason
        add("mason_clay", Villager.Profession.MASON, Kind.PLAYER_SELLS, Material.CLAY_BALL, 32, null, 0, 48, .70);
        add("mason_brick", Villager.Profession.MASON, Kind.PLAYER_SELLS, Material.BRICK, 32, null, 0, 32, .70);
        add("mason_obsidian", Villager.Profession.MASON, Kind.PLAYER_SELLS, Material.OBSIDIAN, 4, null, 0, 12, .60);
        add("mason_glass", Villager.Profession.MASON, Kind.PLAYER_BUYS, Material.GLASS, 32, null, 0, 32, 1.05);
        add("mason_stone", Villager.Profession.MASON, Kind.PLAYER_BUYS, Material.STONE, 32, null, 0, 32, 1.05);
        add("mason_clay_brick", Villager.Profession.MASON, Kind.BARTER, Material.CLAY_BALL, 32, Material.BRICK, 16, 24, 1.0);
        add("mason_cobble_stone", Villager.Profession.MASON, Kind.BARTER, Material.COBBLESTONE, 64, Material.STONE, 48, 32, 1.0);
        add("mason_quartz_glass", Villager.Profession.MASON, Kind.BARTER, Material.QUARTZ, 16, Material.GLASS, 32, 16, 1.0);

        // Toolsmith
        add("toolsmith_iron", Villager.Profession.TOOLSMITH, Kind.PLAYER_SELLS, Material.IRON_INGOT, 16, null, 0, 32, .60);
        add("toolsmith_diamond", Villager.Profession.TOOLSMITH, Kind.PLAYER_SELLS, Material.DIAMOND, 2, null, 0, 6, .55);
        add("toolsmith_redstone", Villager.Profession.TOOLSMITH, Kind.PLAYER_BUYS, Material.REDSTONE, 32, null, 0, 32, 1.05);
        add("toolsmith_iron_pickaxe", Villager.Profession.TOOLSMITH, Kind.PLAYER_BUYS, Material.IRON_PICKAXE, 1, null, 0, 12, 1.05);
        add("toolsmith_copper", Villager.Profession.TOOLSMITH, Kind.PLAYER_SELLS, Material.COPPER_INGOT, 32, null, 0, 24, .60);
        add("toolsmith_iron_copper", Villager.Profession.TOOLSMITH, Kind.BARTER, Material.COPPER_INGOT, 32, Material.IRON_INGOT, 8, 16, 1.0);
        add("toolsmith_flint_iron", Villager.Profession.TOOLSMITH, Kind.BARTER, Material.FLINT, 32, Material.IRON_INGOT, 4, 16, 1.0);
        add("toolsmith_redstone_iron", Villager.Profession.TOOLSMITH, Kind.BARTER, Material.REDSTONE, 64, Material.IRON_INGOT, 8, 16, 1.0);

        // Weaponsmith
        add("weaponsmith_gold", Villager.Profession.WEAPONSMITH, Kind.PLAYER_SELLS, Material.GOLD_INGOT, 16, null, 0, 24, .60);
        add("weaponsmith_iron", Villager.Profession.WEAPONSMITH, Kind.PLAYER_SELLS, Material.IRON_INGOT, 16, null, 0, 24, .60);
        add("weaponsmith_diamond", Villager.Profession.WEAPONSMITH, Kind.PLAYER_SELLS, Material.DIAMOND, 2, null, 0, 4, .50);
        add("weaponsmith_shield", Villager.Profession.WEAPONSMITH, Kind.PLAYER_BUYS, Material.SHIELD, 1, null, 0, 12, 1.05);
        add("weaponsmith_iron_sword", Villager.Profession.WEAPONSMITH, Kind.PLAYER_BUYS, Material.IRON_SWORD, 1, null, 0, 12, 1.05);
        add("weaponsmith_gold_iron", Villager.Profession.WEAPONSMITH, Kind.BARTER, Material.GOLD_INGOT, 16, Material.IRON_INGOT, 8, 16, 1.0);
        add("weaponsmith_flint_arrow", Villager.Profession.WEAPONSMITH, Kind.BARTER, Material.FLINT, 16, Material.ARROW, 32, 16, 1.0);
        add("weaponsmith_iron_shield", Villager.Profession.WEAPONSMITH, Kind.BARTER, Material.IRON_INGOT, 8, Material.SHIELD, 1, 12, 1.0);

        // Armorer
        add("armorer_iron", Villager.Profession.ARMORER, Kind.PLAYER_SELLS, Material.IRON_INGOT, 16, null, 0, 32, .60);
        add("armorer_gold", Villager.Profession.ARMORER, Kind.PLAYER_SELLS, Material.GOLD_INGOT, 16, null, 0, 24, .60);
        add("armorer_diamond", Villager.Profession.ARMORER, Kind.PLAYER_SELLS, Material.DIAMOND, 2, null, 0, 4, .50);
        add("armorer_chainmail", Villager.Profession.ARMORER, Kind.PLAYER_BUYS, Material.CHAINMAIL_CHESTPLATE, 1, null, 0, 8, 1.05);
        add("armorer_iron_chest", Villager.Profession.ARMORER, Kind.PLAYER_BUYS, Material.IRON_CHESTPLATE, 1, null, 0, 12, 1.05);
        add("armorer_iron_gold", Villager.Profession.ARMORER, Kind.BARTER, Material.IRON_INGOT, 16, Material.GOLD_INGOT, 8, 16, 1.0);
        add("armorer_chainmail_iron", Villager.Profession.ARMORER, Kind.BARTER, Material.IRON_INGOT, 12, Material.CHAINMAIL_HELMET, 1, 8, 1.0);
        add("armorer_gold_helmet", Villager.Profession.ARMORER, Kind.BARTER, Material.GOLD_INGOT, 16, Material.GOLDEN_HELMET, 1, 8, 1.0);

        // Fletcher
        add("fletcher_feather", Villager.Profession.FLETCHER, Kind.PLAYER_SELLS, Material.FEATHER, 32, null, 0, 32, .70);
        add("fletcher_string", Villager.Profession.FLETCHER, Kind.PLAYER_SELLS, Material.STRING, 32, null, 0, 32, .70);
        add("fletcher_flint", Villager.Profession.FLETCHER, Kind.PLAYER_SELLS, Material.FLINT, 32, null, 0, 24, .65);
        add("fletcher_arrow", Villager.Profession.FLETCHER, Kind.PLAYER_BUYS, Material.ARROW, 32, null, 0, 32, 1.05);
        add("fletcher_bow", Villager.Profession.FLETCHER, Kind.PLAYER_BUYS, Material.BOW, 1, null, 0, 8, 1.05);
        add("fletcher_flint_feather", Villager.Profession.FLETCHER, Kind.BARTER, Material.FLINT, 16, Material.ARROW, 16, 16, 1.0);
        add("fletcher_string_bow", Villager.Profession.FLETCHER, Kind.BARTER, Material.STRING, 16, Material.BOW, 1, 8, 1.0);
        add("fletcher_feather_string", Villager.Profession.FLETCHER, Kind.BARTER, Material.FEATHER, 24, Material.STRING, 12, 16, 1.0);

        // Leatherworker
        add("leather_leather", Villager.Profession.LEATHERWORKER, Kind.PLAYER_SELLS, Material.LEATHER, 16, null, 0, 24, .70);
        add("leather_rabbit", Villager.Profession.LEATHERWORKER, Kind.PLAYER_SELLS, Material.RABBIT_HIDE, 16, null, 0, 24, .70);
        add("leather_scute", Villager.Profession.LEATHERWORKER, Kind.PLAYER_SELLS, Material.ARMADILLO_SCUTE, 8, null, 0, 12, .60);
        add("leather_saddle", Villager.Profession.LEATHERWORKER, Kind.PLAYER_BUYS, Material.SADDLE, 1, null, 0, 8, 1.05);
        add("leather_horse_armor", Villager.Profession.LEATHERWORKER, Kind.PLAYER_BUYS, Material.LEATHER_HORSE_ARMOR, 1, null, 0, 8, 1.05);
        add("leather_rabbit_leather", Villager.Profession.LEATHERWORKER, Kind.BARTER, Material.RABBIT_HIDE, 16, Material.LEATHER, 8, 16, 1.0);
        add("leather_leather_boots", Villager.Profession.LEATHERWORKER, Kind.BARTER, Material.LEATHER, 16, Material.LEATHER_BOOTS, 1, 12, 1.0);
        add("leather_string_leather", Villager.Profession.LEATHERWORKER, Kind.BARTER, Material.STRING, 32, Material.LEATHER, 4, 16, 1.0);

        // Shepherd
        add("shepherd_wool", Villager.Profession.SHEPHERD, Kind.PLAYER_SELLS, Material.WHITE_WOOL, 16, null, 0, 32, .70);
        add("shepherd_string", Villager.Profession.SHEPHERD, Kind.PLAYER_SELLS, Material.STRING, 32, null, 0, 32, .65);
        add("shepherd_dye", Villager.Profession.SHEPHERD, Kind.PLAYER_BUYS, Material.WHITE_DYE, 16, null, 0, 24, 1.05);
        add("shepherd_carpet", Villager.Profession.SHEPHERD, Kind.PLAYER_BUYS, Material.WHITE_CARPET, 16, null, 0, 20, 1.05);
        add("shepherd_bed", Villager.Profession.SHEPHERD, Kind.PLAYER_BUYS, Material.WHITE_BED, 1, null, 0, 12, 1.05);
        add("shepherd_wool_string", Villager.Profession.SHEPHERD, Kind.BARTER, Material.WHITE_WOOL, 16, Material.STRING, 8, 16, 1.0);
        add("shepherd_dye_wool", Villager.Profession.SHEPHERD, Kind.BARTER, Material.WHITE_DYE, 8, Material.WHITE_WOOL, 8, 16, 1.0);
        add("shepherd_wool_carpet", Villager.Profession.SHEPHERD, Kind.BARTER, Material.WHITE_WOOL, 8, Material.WHITE_CARPET, 8, 16, 1.0);

        // Butcher
        add("butcher_beef", Villager.Profession.BUTCHER, Kind.PLAYER_SELLS, Material.BEEF, 16, null, 0, 32, .60);
        add("butcher_pork", Villager.Profession.BUTCHER, Kind.PLAYER_SELLS, Material.PORKCHOP, 16, null, 0, 32, .60);
        add("butcher_chicken", Villager.Profession.BUTCHER, Kind.PLAYER_SELLS, Material.CHICKEN, 32, null, 0, 32, .60);
        add("butcher_cooked_beef", Villager.Profession.BUTCHER, Kind.PLAYER_BUYS, Material.COOKED_BEEF, 16, null, 0, 24, 1.05);
        add("butcher_cooked_pork", Villager.Profession.BUTCHER, Kind.PLAYER_BUYS, Material.COOKED_PORKCHOP, 16, null, 0, 24, 1.05);
        add("butcher_beef_pork", Villager.Profession.BUTCHER, Kind.BARTER, Material.BEEF, 16, Material.PORKCHOP, 12, 16, 1.0);
        add("butcher_chicken_feather", Villager.Profession.BUTCHER, Kind.BARTER, Material.CHICKEN, 16, Material.FEATHER, 16, 16, 1.0);
        add("butcher_beef_cooked", Villager.Profession.BUTCHER, Kind.BARTER, Material.BEEF, 16, Material.COOKED_BEEF, 12, 16, 1.0);

        // Cartographer
        add("cartographer_paper", Villager.Profession.CARTOGRAPHER, Kind.PLAYER_SELLS, Material.PAPER, 32, null, 0, 32, .70);
        add("cartographer_compass", Villager.Profession.CARTOGRAPHER, Kind.PLAYER_BUYS, Material.COMPASS, 1, null, 0, 12, 1.05);
        add("cartographer_map", Villager.Profession.CARTOGRAPHER, Kind.PLAYER_BUYS, Material.MAP, 1, null, 0, 8, 1.05);
        add("cartographer_glass_pane", Villager.Profession.CARTOGRAPHER, Kind.PLAYER_SELLS, Material.GLASS_PANE, 32, null, 0, 24, .60);
        add("cartographer_ink", Villager.Profession.CARTOGRAPHER, Kind.PLAYER_SELLS, Material.INK_SAC, 16, null, 0, 16, .60);
        add("cartographer_paper_compass", Villager.Profession.CARTOGRAPHER, Kind.BARTER, Material.PAPER, 32, Material.COMPASS, 1, 12, 1.0);
        add("cartographer_pane_paper", Villager.Profession.CARTOGRAPHER, Kind.BARTER, Material.GLASS_PANE, 32, Material.PAPER, 24, 16, 1.0);
        add("cartographer_ink_map", Villager.Profession.CARTOGRAPHER, Kind.BARTER, Material.INK_SAC, 8, Material.MAP, 1, 8, 1.0);
    }

    private void add(String id, Villager.Profession profession, Kind kind, Material item, int amount,
                     Material other, int otherAmount, int stock, double rate) {
        all.put(id, new Offer(id, profession, kind, item, amount, other, otherAmount, stock, rate));
    }

    private void loadOrRefresh() {
        nextRefresh = data.getLong("next-refresh", 0L);
        // Force one clean rotation when upgrading from the old shared-stock, 2-hour system.
        if (data.getInt("rotation-version", 0) < 2) nextRefresh = 0L;
        for (Villager.Profession profession : Villager.Profession.values()) {
            List<String> ids = data.getStringList("active." + profession.name());
            List<Offer> offers = ids.stream().map(all::get).filter(Objects::nonNull)
                    .filter(o -> o.profession() == profession).collect(Collectors.toCollection(ArrayList::new));
            if (offers.isEmpty()) offers = choose(profession);
            active.put(profession, offers);
        }
        org.bukkit.configuration.ConfigurationSection usedSection = data.getConfigurationSection("player-used");
        if (usedSection != null) {
            for (String uuidText : usedSection.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidText);
                    Set<String> used = new HashSet<>(data.getStringList("player-used." + uuidText));
                    if (!used.isEmpty()) playerUsedOffers.put(uuid, used);
                } catch (IllegalArgumentException ignored) {
                    plugin.getLogger().warning("Ignoring invalid player UUID in villager market data: " + uuidText);
                }
            }
        }
        if (nextRefresh <= System.currentTimeMillis()) refreshAll();
        else save();
    }

    private List<Offer> choose(Villager.Profession profession) {
        List<Offer> pool = all.values().stream().filter(o -> o.profession() == profession).collect(Collectors.toCollection(ArrayList::new));
        Collections.shuffle(pool);
        int visibleOffers = profession == Villager.Profession.CLERIC ? 10 : 6;
        return new ArrayList<>(pool.subList(0, Math.min(visibleOffers, pool.size())));
    }

    private void refreshAll() {
        for (Villager.Profession profession : Villager.Profession.values()) {
            List<Offer> offers = choose(profession);
            active.put(profession, offers);
        }
        playerUsedOffers.clear();
        nextRefresh = System.currentTimeMillis() + REFRESH_MILLIS;
        save();
        for (Entity entity : Bukkit.getWorlds().stream().flatMap(w -> w.getEntities().stream()).toList()) {
            if (entity instanceof Villager villager && villager.getProfession() != Villager.Profession.NONE) {
                villager.setRecipes(recipes(villager.getProfession()));
            }
        }
        Bukkit.broadcastMessage(ChatColor.DARK_GRAY + "[" + ChatColor.GOLD + "XZH MARKET" + ChatColor.DARK_GRAY
                + "] " + ChatColor.YELLOW + "Villager offers refreshed! Personal trade limits have reset. Next refresh in 4 hours.");
    }

    private List<MerchantRecipe> recipes(Villager.Profession profession) {
        List<MerchantRecipe> result = new ArrayList<>();
        for (Offer offer : active.getOrDefault(profession, List.of())) {
            ItemStack output;
            List<ItemStack> inputs = new ArrayList<>();
            if (offer.kind() == Kind.BARTER) {
                inputs.add(new ItemStack(offer.item(), offer.amount()));
                output = new ItemStack(offer.other(), offer.otherAmount());
            } else if (offer.kind() == Kind.PLAYER_SELLS) {
                inputs.add(new ItemStack(offer.item(), offer.amount()));
                int rawPayout = Math.max(1, (int) Math.floor(referenceBuyPrice(offer.item()) * offer.payoutRate() * offer.amount() * 0.90));
                int payout = rawPayout >= 640 ? (int) Math.round(rawPayout / 100.0) * 100 : Math.max(1, (int) Math.round(rawPayout / 10.0) * 10);
                output = coins(payout);
            } else if (offer.kind() == Kind.PLAYER_BUYS_POTION) {
                int price = potionPrice(offer.id(), true);
                addCoinInputs(inputs, price);
                output = potionForOffer(offer.id());
            } else if (offer.kind() == Kind.PLAYER_SELLS_POTION) {
                inputs.add(potionForOffer(offer.id()));
                output = coins(potionPrice(offer.id(), false));
            } else {
                int price = Math.max(1, (int) Math.ceil(referenceBuyPrice(offer.item()) * offer.payoutRate() * offer.amount() * 0.95));
                addCoinInputs(inputs, price);
                output = new ItemStack(offer.item(), offer.amount());
            }
            if (output == null || output.getAmount() > output.getMaxStackSize()) continue;
            // MerchantRecipe uses are shared by every player who opens this villager.
            // Keep the vanilla recipe open and enforce the real limit by player UUID in onTrade.
            MerchantRecipe recipe = new MerchantRecipe(output, 0, 1_000_000, false, 0, 0.0f);
            for (ItemStack input : inputs) recipe.addIngredient(input);
            result.add(recipe);
        }
        return result;
    }

    private int potionPrice(String id, boolean buyingFromVillager) {
        String potion = id.substring(id.lastIndexOf('_') + 1);
        int buyPrice = switch (potion) {
            case "healing" -> 200;
            case "regeneration" -> 260;
            case "strength" -> 180;
            case "resistance" -> 220;
            case "vision" -> 120;
            case "breathing" -> 180;
            case "swiftness" -> 140;
            case "falling" -> 160;
            case "potion" -> id.equals("cleric_strength_potion") ? 180 : 220;
            default -> 180;
        };
        return buyingFromVillager ? buyPrice : Math.max(10, (int) Math.round(buyPrice * 0.45 / 10.0) * 10);
    }

    private ItemStack potionForOffer(String id) {
        String potion = id.substring(id.lastIndexOf('_') + 1);
        PotionType type;
        if (id.equals("cleric_strength_potion")) type = PotionType.STRENGTH;
        else if (id.equals("cleric_fire_resistance_potion")) type = PotionType.FIRE_RESISTANCE;
        else type = switch (potion) {
            case "healing" -> PotionType.HEALING;
            case "regeneration" -> PotionType.REGENERATION;
            case "strength" -> PotionType.STRENGTH;
            case "resistance" -> PotionType.FIRE_RESISTANCE;
            case "vision" -> PotionType.NIGHT_VISION;
            case "breathing" -> PotionType.WATER_BREATHING;
            case "swiftness" -> PotionType.SWIFTNESS;
            case "falling" -> PotionType.SLOW_FALLING;
            case "potion" -> id.equals("cleric_strength_potion") ? PotionType.STRENGTH : PotionType.FIRE_RESISTANCE;
            default -> null;
        };
        if (type == null) return null;
        ItemStack potionItem = new ItemStack(Material.POTION, 1);
        if (potionItem.getItemMeta() instanceof PotionMeta meta) {
            meta.setBasePotionType(type);
            potionItem.setItemMeta(meta);
        }
        return potionItem;
    }

    private double referenceBuyPrice(Material material) {
        String path = "shop.prices." + material.name().toLowerCase(Locale.ROOT) + ".buy";
        if (plugin.getConfig().contains(path)) return Math.max(1, plugin.getConfig().getDouble(path));
        return switch (material) {
            case WITHER_SKELETON_SKULL -> 1000;
            case AXOLOTL_BUCKET -> 1200;
            case TROPICAL_FISH_BUCKET, PUFFERFISH_BUCKET -> 180;
            case REDSTONE -> 8;
            case LAPIS_LAZULI -> 10;
            case DIAMOND -> 250;
            case IRON_INGOT -> 25;
            case GOLD_INGOT -> 40;
            case COD -> 5;
            case SALMON -> 6;
            case BLAZE_ROD -> 35;
            case GHAST_TEAR -> 120;
            case ENDER_PEARL -> 30;
            case PAPER -> 2;
            case BOOK -> 20;
            case WHEAT, CARROT, POTATO -> 2;
            case PUMPKIN -> 8;
            case BREAD -> 8;
            case GOLDEN_CARROT -> 10;
            case COOKED_COD -> 10;
            case CLAY_BALL -> 2;
            case BRICK -> 3;
            case OBSIDIAN -> 30;
            case GLASS -> 10;
            case STONE -> 2;
            case FEATHER, STRING -> 2;
            case LEATHER -> 12;
            case RABBIT_HIDE -> 8;
            case WHITE_WOOL -> 4;
            case BEEF -> 8;
            case PORKCHOP -> 9;
            case ARROW -> 2;
            case COMPASS -> 40;
            case DIAMOND_CHESTPLATE -> 900;
            default -> 5;
        };
    }

    private ItemStack coins(int value) {
        int denomination = value >= 1000 && value % 1000 == 0 && value / 1000 <= 64 ? 1000
                : value >= 100 && value % 100 == 0 && value / 100 <= 64 ? 100
                : value >= 10 && value % 10 == 0 && value / 10 <= 64 ? 10 : 1;
        int count = Math.max(1, (int) Math.round(value / (double) denomination));
        if (count > 64) {
            denomination = 100;
            count = Math.max(1, (int) Math.round(value / 100.0));
        }
        return plugin.getPocketCoinManager().createCoin(denomination, Math.min(64, count));
    }

    private void addCoinInputs(List<ItemStack> inputs, int rawPrice) {
        int price = Math.max(10, (int) Math.round(rawPrice / 10.0) * 10);
        if (price >= 1000) {
            price = (int) Math.round(price / 100.0) * 100;
            int thousands = price / 1000;
            int hundreds = (price % 1000) / 100;
            if (thousands > 0) inputs.add(plugin.getPocketCoinManager().createCoin(1000, Math.min(64, thousands)));
            if (hundreds > 0) inputs.add(plugin.getPocketCoinManager().createCoin(100, Math.min(64, hundreds)));
        } else if (price > 640) {
            int hundreds = price / 100;
            int tens = (price % 100) / 10;
            if (hundreds > 0) inputs.add(plugin.getPocketCoinManager().createCoin(100, Math.min(64, hundreds)));
            if (tens > 0) inputs.add(plugin.getPocketCoinManager().createCoin(10, Math.min(64, tens)));
        } else {
            inputs.add(plugin.getPocketCoinManager().createCoin(10, Math.min(64, price / 10)));
        }
        if (inputs.isEmpty()) inputs.add(plugin.getPocketCoinManager().createCoin(10, 1));
    }

    private void save() {
        data.set("rotation-version", 2);
        data.set("next-refresh", nextRefresh);
        for (Villager.Profession profession : Villager.Profession.values()) {
            data.set("active." + profession.name(), active.getOrDefault(profession, List.of()).stream().map(Offer::id).toList());
        }
        data.set("player-used", null);
        for (Map.Entry<UUID, Set<String>> entry : playerUsedOffers.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                data.set("player-used." + entry.getKey(), new ArrayList<>(entry.getValue()));
            }
        }
        try { file.getParentFile().mkdirs(); data.save(file); }
        catch (IOException ex) { plugin.getLogger().severe("Could not save villager market data: " + ex.getMessage()); }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAcquireTrade(VillagerAcquireTradeEvent event) {
        // Vanilla level-up offers must not add enchanted books or bypass the XZH market.
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onVillagerInteract(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Villager villager)) return;
        if (villager.getProfession() == Villager.Profession.NONE) {
            event.setCancelled(true);
            return;
        }
        villager.setRecipes(recipes(villager.getProfession()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTrade(PlayerTradeEvent event) {
        if (!(event.getMerchant() instanceof Villager villager)) return;
        Villager.Profession profession = villager.getProfession();
        List<Offer> offers = active.getOrDefault(profession, List.of());
        List<MerchantRecipe> currentRecipes = villager.getRecipes();
        int index = -1;
        for (int i = 0; i < currentRecipes.size(); i++) {
            if (sameRecipe(currentRecipes.get(i), event.getTrade())) { index = i; break; }
        }
        if (index < 0 || index >= offers.size()) return;
        Offer offer = offers.get(index);
        UUID playerId = event.getPlayer().getUniqueId();
        Set<String> used = playerUsedOffers.computeIfAbsent(playerId, ignored -> new HashSet<>());
        if (used.contains(offer.id())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "You have already used this market offer this rotation. It refreshes every 4 hours.");
            return;
        }
        used.add(offer.id());
        save();
        // If another listener cancels the trade, return the player's allowance next tick.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (event.isCancelled()) {
                Set<String> current = playerUsedOffers.get(playerId);
                if (current != null) {
                    current.remove(offer.id());
                    if (current.isEmpty()) playerUsedOffers.remove(playerId);
                }
                save();
            }
        });
    }

    private boolean sameRecipe(MerchantRecipe a, MerchantRecipe b) {
        if (!a.getResult().isSimilar(b.getResult())) return false;
        List<ItemStack> aa = a.getIngredients(), bb = b.getIngredients();
        if (aa.size() != bb.size()) return false;
        for (int i = 0; i < aa.size(); i++) {
            ItemStack x = aa.get(i), y = bb.get(i);
            if (x.getType() != y.getType() || x.getAmount() != y.getAmount()) return false;
            // Potion base types must match too; otherwise two potion offers with equal coin payouts
            // could be mistaken for one another by the player-use limiter.
            if (x.getType() == Material.POTION || plugin.getPocketCoinManager().isCoin(x)) {
                if (!x.isSimilar(y)) return false;
            }
        }
        return true;
    }

    public void shutdown() {
        if (ticker != null) ticker.cancel();
        save();
    }
}
