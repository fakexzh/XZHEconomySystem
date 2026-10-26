package com.xzh.economy;

import io.papermc.paper.event.player.PlayerTradeEvent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.VillagerAcquireTradeEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.MerchantInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.Merchant;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Fixed XZH profession market. Each player can use each offer once every two hours;
 * the player's own merchant inventory displays vanilla's red X while that offer is cooling down.
 * XZH coins are the real tagged pocket coins; offer definitions are fixed and never randomly rotated.
 * A villager's AI is disabled only while at least one player has its XZH trade menu open.
 */
public final class VillagerTradeManager implements Listener {
    private static final long OFFER_COOLDOWN_MILLIS = 2L * 60L * 60L * 1000L;
    private final XZHEconomySystem plugin;
    private final File file;
    private final YamlConfiguration data;
    private final Map<String, Offer> all = new LinkedHashMap<>();
    private final Map<Villager.Profession, List<Offer>> active = new HashMap<>();
    private final Map<UUID, Map<String, Long>> playerUsedOffers = new HashMap<>();
    private final Map<Merchant, MerchantSession> merchantSessions = new HashMap<>();
    private final Map<UUID, Boolean> originalVillagerAi = new HashMap<>();
    private final Map<UUID, Set<UUID>> playersViewingVillager = new HashMap<>();
    private final Map<UUID, UUID> villagerByPlayer = new HashMap<>();
    private record MerchantSession(UUID playerId, Villager.Profession profession, UUID villagerId, List<String> offerIds) {}
    private record RecipeSet(List<MerchantRecipe> recipes, List<String> offerIds) {}

    private enum Kind { PLAYER_SELLS, PLAYER_SELLS_POTION, PLAYER_BUYS, PLAYER_BUYS_POTION, BARTER, BARTER_POTION }
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
    }

    private void defineOffers() {
        // Combined rebalance; enchanted books and premium boats remain XZH-shop-exclusive.
        addSellMany(Villager.Profession.FARMER, "WHEAT:32:0.70,CARROT:32:0.70,POTATO:32:0.70,BEETROOT:32:0.28,PUMPKIN:16:0.40,MELON_SLICE:32:0.42,SUGAR_CANE:32:0.42,SWEET_BERRIES:32:0.42,COCOA_BEANS:16:0.60,EGG:16:0.30");
        addBuyMany(Villager.Profession.FARMER, "BREAD:16,GOLDEN_CARROT:4:0.21,CAKE:1:12.0,PUMPKIN_PIE:8:2.4,COOKIE:16,HAY_BLOCK:8:1.85,BONE_MEAL:16");
        addBarterMany(Villager.Profession.FARMER, "PUMPKIN:16>PUMPKIN_PIE:6,SWEET_BERRIES:16>COOKIE:8");
        addSellMany(Villager.Profession.FISHERMAN, "COD:16:1.00,SALMON:16:1.00,TROPICAL_FISH:16:0.60,PUFFERFISH:8:0.55,NAUTILUS_SHELL:2:5.5,INK_SAC:16:0.60,STRING:32:0.65,KELP:32:0.28,LILY_PAD:8:0.60");
        addBuyMany(Villager.Profession.FISHERMAN, "COOKED_COD:16,COOKED_SALMON:16");
        addBarterMany(Villager.Profession.FISHERMAN, "COD:24>COOKED_COD:16,SALMON:24>COOKED_SALMON:16,KELP:32>DRIED_KELP:16");
        addSellMany(Villager.Profession.CLERIC, "ROTTEN_FLESH:32:0.45,SPIDER_EYE:16:0.45,GUNPOWDER:16:0.45,BLAZE_ROD:8:0.60,ENDER_PEARL:8:0.65,GHAST_TEAR:2:0.40,PHANTOM_MEMBRANE:4:2.5,WITHER_SKELETON_SKULL:1:0.50");
        addBuyMany(Villager.Profession.CLERIC, "GLOWSTONE_DUST:16,REDSTONE:32,LAPIS_LAZULI:16,ENDER_PEARL:4");
        addBarterMany(Villager.Profession.CLERIC, "BLAZE_POWDER:8>ENDER_PEARL:2,GHAST_TEAR:2>BLAZE_ROD:4");
        addSellMany(Villager.Profession.LIBRARIAN, "PAPER:32:0.70,BOOK:8:0.42,INK_SAC:16:0.65,RABBIT_HIDE:16:0.45");
        addBuyMany(Villager.Profession.LIBRARIAN, "LAPIS_LAZULI:16,BOOKSHELF:4:6.9,LANTERN:4:4.7,NAME_TAG:1:16.0");
        addBarterMany(Villager.Profession.LIBRARIAN, "PAPER:24>BOOK:4,BOOK:4>BOOKSHELF:1,PAPER:32>MAP:1");
        addSellMany(Villager.Profession.MASON, "CLAY_BALL:32:0.70,BRICK:32:0.70,STONE:32:0.60,ANDESITE:32:0.35,DIORITE:32:0.35,GRANITE:32:0.35,SAND:32:0.28,GRAVEL:32:0.28,QUARTZ:16:0.45,OBSIDIAN:4:0.60");
        addBuyMany(Villager.Profession.MASON, "GLASS:32:1.75,STONE_BRICKS:32,BRICKS:32,TERRACOTTA:16,WHITE_TERRACOTTA:16,QUARTZ_BLOCK:8,POLISHED_ANDESITE:16,POLISHED_DIORITE:16,POLISHED_GRANITE:16");
        addBarterMany(Villager.Profession.MASON, "CLAY_BALL:32>BRICK:16,COBBLESTONE:64>STONE:48,SAND:32>GLASS:16,GRAVEL:32>FLINT:8");
        addSellMany(Villager.Profession.TOOLSMITH, "IRON_INGOT:16:0.60,COPPER_INGOT:32:0.55,GOLD_INGOT:8:0.55,DIAMOND:2:0.55,FLINT:32:0.50,COAL:32:0.40");
        addBarterMany(Villager.Profession.TOOLSMITH, "COPPER_INGOT:32>IRON_INGOT:6");
        addSellMany(Villager.Profession.WEAPONSMITH, "IRON_INGOT:16:0.60,BLAZE_ROD:8:0.45");
        addBuyMany(Villager.Profession.WEAPONSMITH, "ARROW:32");
        addBarterMany(Villager.Profession.WEAPONSMITH, "FLINT:8>ARROW:16");
        addSellMany(Villager.Profession.ARMORER, "IRON_INGOT:16:0.60,GOLD_INGOT:16:0.60");
        addBuyMany(Villager.Profession.ARMORER, "CHAINMAIL_CHESTPLATE:1:42.0,IRON_HORSE_ARMOR:1:21.0");
        addBarterMany(Villager.Profession.ARMORER, "IRON_INGOT:12>CHAINMAIL_HELMET:1");
        addSellMany(Villager.Profession.FLETCHER, "FEATHER:32:0.70,STRING:32:0.70,FLINT:32:0.50,STICK:64:0.18,INK_SAC:16:0.50");
        addBuyMany(Villager.Profession.FLETCHER, "ARROW:32,SPECTRAL_ARROW:8:2.0");
        addBarterMany(Villager.Profession.FLETCHER, "FLINT:8>ARROW:16");
        addSellMany(Villager.Profession.LEATHERWORKER, "LEATHER:16:0.50,RABBIT_HIDE:16:0.45,ARMADILLO_SCUTE:8:1.5");
        addBuyMany(Villager.Profession.LEATHERWORKER, "SADDLE:1:19.0,LEATHER_HORSE_ARMOR:1:12.6,ITEM_FRAME:8:2.4");
        addBarterMany(Villager.Profession.LEATHERWORKER, "RABBIT_HIDE:16>LEATHER:8");
        addSellMany(Villager.Profession.SHEPHERD, "WHITE_WOOL:16:0.70,BLACK_WOOL:16:0.60,RED_WOOL:16:0.60,BLUE_WOOL:16:0.60,YELLOW_WOOL:16:0.60,STRING:32:0.65,WHITE_CARPET:16:0.30");
        addBuyMany(Villager.Profession.SHEPHERD, "WHITE_DYE:16,BLACK_DYE:16,RED_DYE:16,BLUE_DYE:16,YELLOW_DYE:16,WHITE_BED:1:4.2,WHITE_BANNER:4:3.2,PAINTING:4");
        addBarterMany(Villager.Profession.SHEPHERD, "STRING:16>WHITE_WOOL:8");
        addSellMany(Villager.Profession.BUTCHER, "BEEF:16:0.60,PORKCHOP:16:0.60,CHICKEN:32:0.60,MUTTON:16:0.55,RABBIT:16:0.50,ROTTEN_FLESH:32:0.35,SWEET_BERRIES:32:0.45");
        addBuyMany(Villager.Profession.BUTCHER, "COOKED_BEEF:16,COOKED_PORKCHOP:16:2.8,COOKED_CHICKEN:32:2.1,COOKED_MUTTON:16:1.85,COOKED_RABBIT:16:0.65,RABBIT_STEW:1:8.4,COOKED_COD:16");
        addBarterMany(Villager.Profession.BUTCHER, "BEEF:16>COOKED_BEEF:12,PORKCHOP:16>COOKED_PORKCHOP:12,CHICKEN:16>COOKED_CHICKEN:12,MUTTON:16>COOKED_MUTTON:12,RABBIT:16>COOKED_RABBIT:12");
        addSellMany(Villager.Profession.CARTOGRAPHER, "PAPER:32:0.70,GLASS_PANE:32:0.55,INK_SAC:16:0.60");
        addBuyMany(Villager.Profession.CARTOGRAPHER, "ITEM_FRAME:8:2.4,NAME_TAG:1:16.0,CREEPER_BANNER_PATTERN:1:16.8,FLOWER_BANNER_PATTERN:1:12.6,SKULL_BANNER_PATTERN:1:33.7,MOJANG_BANNER_PATTERN:1:50.5");
        addBarterMany(Villager.Profession.CARTOGRAPHER, "PAPER:64>COMPASS:1");
        for (String potionId : List.of("healing", "regeneration", "strength", "fire_resistance", "swiftness", "water_breathing", "night_vision", "slow_falling")) {
            add("cleric_potion_buy_" + potionId, Villager.Profession.CLERIC, Kind.PLAYER_BUYS_POTION, Material.POTION, 1, null, 0, 1, 1.0);
            add("cleric_potion_sell_" + potionId, Villager.Profession.CLERIC, Kind.PLAYER_SELLS_POTION, Material.POTION, 1, null, 0, 1, 0.45);
        }
        add("cleric_barter_regeneration", Villager.Profession.CLERIC, Kind.BARTER_POTION, Material.GHAST_TEAR, 2, null, 0, 1, 1.0);
        add("cleric_barter_strength", Villager.Profession.CLERIC, Kind.BARTER_POTION, Material.BLAZE_ROD, 4, null, 0, 1, 1.0);
    }

    private void add(String id, Villager.Profession profession, Kind kind, Material item, int amount,
                     Material other, int otherAmount, int stock, double rate) {
        all.put(id, new Offer(id, profession, kind, item, amount, other, otherAmount, stock, rate));
    }

    private void addSellMany(Villager.Profession profession, String encoded) {
        int index = 0;
        for (String token : encoded.split(",")) {
            String[] parts = token.trim().split(":");
            Material material = Material.valueOf(parts[0]);
            int amount = Integer.parseInt(parts[1]);
            double rate = parts.length > 2 ? Double.parseDouble(parts[2]) : 0.70;
            add(profession.name().toLowerCase(Locale.ROOT) + "_fixed_sell_" + index++,
                    profession, Kind.PLAYER_SELLS, material, amount, null, 0, 1, rate);
        }
    }

    private void addBuyMany(Villager.Profession profession, String encoded) {
        int index = 0;
        for (String token : encoded.split(",")) {
            String[] parts = token.trim().split(":");
            Material material = Material.valueOf(parts[0]);
            int amount = Integer.parseInt(parts[1]);
            double rate = parts.length > 2 ? Double.parseDouble(parts[2]) : 1.05;
            add(profession.name().toLowerCase(Locale.ROOT) + "_fixed_buy_" + index++,
                    profession, Kind.PLAYER_BUYS, material, amount, null, 0, 1, rate);
        }
    }

    private void addBarterMany(Villager.Profession profession, String encoded) {
        int index = 0;
        for (String token : encoded.split(",")) {
            String[] sides = token.trim().split(">");
            String[] input = sides[0].split(":");
            String[] output = sides[1].split(":");
            add(profession.name().toLowerCase(Locale.ROOT) + "_fixed_barter_" + index++,
                    profession, Kind.BARTER, Material.valueOf(input[0]), Integer.parseInt(input[1]),
                    Material.valueOf(output[0]), Integer.parseInt(output[1]), 1, 1.0);
        }
    }

    private void loadOrRefresh() {
        for (Villager.Profession profession : Villager.Profession.values()) {
            active.put(profession, all.values().stream()
                    .filter(offer -> offer.profession() == profession)
                    .collect(Collectors.toCollection(ArrayList::new)));
        }
        long now = System.currentTimeMillis();
        org.bukkit.configuration.ConfigurationSection usedSection = data.getConfigurationSection("player-used");
        if (usedSection != null) {
            for (String uuidText : usedSection.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidText);
                    org.bukkit.configuration.ConfigurationSection playerSection = usedSection.getConfigurationSection(uuidText);
                    if (playerSection == null) continue;
                    Map<String, Long> used = new HashMap<>();
                    for (String offerId : playerSection.getKeys(false)) {
                        long until = playerSection.getLong(offerId, 0L);
                        if (until > now && all.containsKey(offerId)) used.put(offerId, until);
                    }
                    if (!used.isEmpty()) playerUsedOffers.put(uuid, used);
                } catch (IllegalArgumentException ignored) {
                    plugin.getLogger().warning("Ignoring invalid player UUID in villager market data: " + uuidText);
                }
            }
        }
        save();
    }

    private List<MerchantRecipe> recipes(Villager.Profession profession, UUID playerId) {
        return buildRecipeSet(profession, playerId).recipes();
    }

    private RecipeSet buildRecipeSet(Villager.Profession profession, UUID playerId) {
        Map<String, Long> used = playerUsedOffers.getOrDefault(playerId, Map.of());
        long now = System.currentTimeMillis();
        List<MerchantRecipe> result = new ArrayList<>();
        List<String> recipeOfferIds = new ArrayList<>();
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
            } else if (offer.kind() == Kind.BARTER_POTION) {
                inputs.add(new ItemStack(offer.item(), offer.amount()));
                output = potionForOffer(offer.id());
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
            boolean exhaustedForPlayer = used.getOrDefault(offer.id(), 0L) > now;
            MerchantRecipe recipe = new MerchantRecipe(output, 1, exhaustedForPlayer ? 1 : 0, false, 0, 0.0f);
            for (ItemStack input : inputs) recipe.addIngredient(input);
            result.add(recipe);
            recipeOfferIds.add(offer.id());
        }
        return new RecipeSet(result, recipeOfferIds);
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
        data.set("rotation-version", 3);
        data.set("next-refresh", null);
        data.set("active", null);
        data.set("player-used", null);
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Map<String, Long>> entry : playerUsedOffers.entrySet()) {
            for (Map.Entry<String, Long> offer : entry.getValue().entrySet()) {
                if (offer.getValue() > now) {
                    data.set("player-used." + entry.getKey() + "." + offer.getKey(), offer.getValue());
                }
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
        event.setCancelled(true);
        if (villager.getProfession() == Villager.Profession.NONE) return;
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        UUID villagerId = villager.getUniqueId();
        Villager.Profession profession = villager.getProfession();
        releaseVillager(playerId, null);
        originalVillagerAi.putIfAbsent(villagerId, villager.hasAI());
        playersViewingVillager.computeIfAbsent(villagerId, ignored -> new HashSet<>()).add(playerId);
        villagerByPlayer.put(playerId, villagerId);
        villager.getPathfinder().stopPathfinding();
        villager.setAI(false);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || !villager.isValid()) {
                releaseVillager(playerId, villagerId);
                return;
            }
            Merchant merchant = Bukkit.createMerchant(ChatColor.GOLD + "XZH " + profession.name().replace('_', ' '));
            RecipeSet recipeSet = buildRecipeSet(profession, playerId);
            merchant.setRecipes(recipeSet.recipes());
            merchantSessions.put(merchant, new MerchantSession(playerId, profession, villagerId, recipeSet.offerIds()));
            player.openMerchant(merchant, true);
        });
    }

    @EventHandler
    public void onMerchantClose(InventoryCloseEvent event) {
        if (event.getInventory() instanceof MerchantInventory merchantInventory) {
            MerchantSession session = merchantSessions.remove(merchantInventory.getMerchant());
            if (session != null) releaseVillager(session.playerId(), session.villagerId());
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        releaseVillager(event.getPlayer().getUniqueId(), null);
    }

    private void releaseVillager(UUID playerId, UUID expectedVillagerId) {
        UUID villagerId = villagerByPlayer.get(playerId);
        if (villagerId == null || (expectedVillagerId != null && !expectedVillagerId.equals(villagerId))) return;
        villagerByPlayer.remove(playerId);
        Set<UUID> viewers = playersViewingVillager.get(villagerId);
        if (viewers != null) {
            viewers.remove(playerId);
            if (!viewers.isEmpty()) return;
            playersViewingVillager.remove(villagerId);
        }
        Boolean wasAiEnabled = originalVillagerAi.remove(villagerId);
        Entity entity = Bukkit.getEntity(villagerId);
        if (entity instanceof Villager villager && villager.isValid() && wasAiEnabled != null) {
            villager.setAI(wasAiEnabled);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTrade(PlayerTradeEvent event) {
        Merchant merchant = event.getMerchant();
        MerchantSession session = merchantSessions.get(merchant);
        if (session == null || !session.playerId().equals(event.getPlayer().getUniqueId())) return;
        Villager.Profession profession = session.profession();
        List<String> recipeOfferIds = session.offerIds();
        List<MerchantRecipe> currentRecipes = merchant.getRecipes();
        int index = -1;

        // Resolve against the exact recipe list built for this player's merchant.
        // Do not index the profession's full offer list: invalid/unsupported recipes
        // can be omitted when building the merchant and shift every later index.
        if (event.getPlayer().getOpenInventory().getTopInventory() instanceof MerchantInventory inventory
                && inventory.getMerchant() == merchant) {
            int selectedIndex = inventory.getSelectedRecipeIndex();
            if (selectedIndex >= 0 && selectedIndex < recipeOfferIds.size()
                    && selectedIndex < currentRecipes.size()) {
                index = selectedIndex;
            }
        }

        // Fallback to the recipe actually traded, still resolving through the aligned ID list.
        if (index < 0) {
            for (int i = 0; i < currentRecipes.size() && i < recipeOfferIds.size(); i++) {
                if (sameRecipe(currentRecipes.get(i), event.getTrade())) { index = i; break; }
            }
        }
        if (index < 0 || index >= recipeOfferIds.size()) {
            plugin.getLogger().warning("Could not map villager trade to a fixed offer for " + event.getPlayer().getUniqueId());
            return;
        }
        Offer offer = all.get(recipeOfferIds.get(index));
        if (offer == null) {
            plugin.getLogger().warning("Unknown villager offer ID in merchant session: " + recipeOfferIds.get(index));
            return;
        }
        UUID playerId = event.getPlayer().getUniqueId();
        long now = System.currentTimeMillis();
        Map<String, Long> used = playerUsedOffers.computeIfAbsent(playerId, ignored -> new HashMap<>());
        if (used.getOrDefault(offer.id(), 0L) > now) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "You already used this offer. It resets 2 hours after your trade.");
            return;
        }
        long expiresAt = now + OFFER_COOLDOWN_MILLIS;
        used.put(offer.id(), expiresAt);
        save();
        // Do not replace merchant recipes inside PlayerTradeEvent: Paper may still be
        // processing the selected recipe, and mutating the merchant here can block the trade.
        // Refresh only after the event finishes; if another listener cancels it, roll back
        // the cooldown so a failed trade never consumes the offer.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (event.isCancelled()) {
                Map<String, Long> current = playerUsedOffers.get(playerId);
                if (current != null && current.getOrDefault(offer.id(), 0L) == expiresAt) {
                    current.remove(offer.id());
                    if (current.isEmpty()) playerUsedOffers.remove(playerId);
                }
                save();
            }
            if (event.getPlayer().isOnline()) {
                merchant.setRecipes(recipes(profession, playerId));
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
        for (Map.Entry<UUID, Boolean> entry : new HashMap<>(originalVillagerAi).entrySet()) {
            Entity entity = Bukkit.getEntity(entry.getKey());
            if (entity instanceof Villager villager && villager.isValid()) villager.setAI(entry.getValue());
        }
        originalVillagerAi.clear();
        playersViewingVillager.clear();
        villagerByPlayer.clear();
        merchantSessions.clear();
        save();
    }
}
