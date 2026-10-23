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
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Vanilla-interface XZH profession market. Stock and offer rotations are shared globally
 * per profession and persisted between restarts. XZH coins are the real tagged pocket coins.
 */
public final class VillagerTradeManager implements Listener {
    private static final long REFRESH_MILLIS = 2L * 60L * 60L * 1000L;
    private final XZHEconomySystem plugin;
    private final File file;
    private final YamlConfiguration data;
    private final Map<String, Offer> all = new LinkedHashMap<>();
    private final Map<Villager.Profession, List<Offer>> active = new HashMap<>();
    private final Map<String, Integer> remaining = new HashMap<>();
    private long nextRefresh;
    private BukkitTask ticker;

    private enum Kind { PLAYER_SELLS, PLAYER_BUYS, BARTER }
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
        add("farmer_wheat", Villager.Profession.FARMER, Kind.PLAYER_SELLS, Material.WHEAT, 32, null, 0, 80, .70);
        add("farmer_carrot", Villager.Profession.FARMER, Kind.PLAYER_SELLS, Material.CARROT, 32, null, 0, 80, .70);
        add("farmer_potato", Villager.Profession.FARMER, Kind.PLAYER_SELLS, Material.POTATO, 32, null, 0, 80, .70);
        add("farmer_pumpkin", Villager.Profession.FARMER, Kind.PLAYER_SELLS, Material.PUMPKIN, 16, null, 0, 48, .75);
        add("farmer_bread", Villager.Profession.FARMER, Kind.PLAYER_BUYS, Material.BREAD, 16, null, 0, 48, 1.10);
        add("farmer_golden_carrot", Villager.Profession.FARMER, Kind.PLAYER_BUYS, Material.GOLDEN_CARROT, 8, null, 0, 16, 1.05);

        add("fish_cod", Villager.Profession.FISHERMAN, Kind.PLAYER_SELLS, Material.COD, 16, null, 0, 64, .75);
        add("fish_salmon", Villager.Profession.FISHERMAN, Kind.PLAYER_SELLS, Material.SALMON, 16, null, 0, 48, .78);
        add("fish_tropical_bucket", Villager.Profession.FISHERMAN, Kind.PLAYER_SELLS, Material.TROPICAL_FISH_BUCKET, 1, null, 0, 8, .82);
        add("fish_puffer_bucket", Villager.Profession.FISHERMAN, Kind.PLAYER_SELLS, Material.PUFFERFISH_BUCKET, 1, null, 0, 8, .82);
        add("fish_axolotl_bucket", Villager.Profession.FISHERMAN, Kind.PLAYER_SELLS, Material.AXOLOTL_BUCKET, 1, null, 0, 3, .85);
        add("fish_cod_redstone", Villager.Profession.FISHERMAN, Kind.BARTER, Material.COD, 50, Material.REDSTONE, 64, 4, 1.0);
        add("fish_lava_bucket", Villager.Profession.FISHERMAN, Kind.PLAYER_SELLS, Material.LAVA_BUCKET, 1, null, 0, 8, .70);
        add("fish_cooked_cod", Villager.Profession.FISHERMAN, Kind.PLAYER_BUYS, Material.COOKED_COD, 16, null, 0, 32, 1.05);

        add("cleric_blaze", Villager.Profession.CLERIC, Kind.PLAYER_SELLS, Material.BLAZE_ROD, 8, null, 0, 24, .65);
        add("cleric_wither_skull", Villager.Profession.CLERIC, Kind.PLAYER_SELLS, Material.WITHER_SKELETON_SKULL, 1, null, 0, 2, .55);
        add("cleric_ghast", Villager.Profession.CLERIC, Kind.PLAYER_SELLS, Material.GHAST_TEAR, 4, null, 0, 12, .70);
        add("cleric_ender", Villager.Profession.CLERIC, Kind.PLAYER_SELLS, Material.ENDER_PEARL, 8, null, 0, 24, .70);
        add("cleric_glowstone", Villager.Profession.CLERIC, Kind.PLAYER_BUYS, Material.GLOWSTONE_DUST, 16, null, 0, 32, 1.10);
        add("cleric_redstone", Villager.Profession.CLERIC, Kind.PLAYER_BUYS, Material.REDSTONE, 32, null, 0, 32, 1.10);
        add("cleric_lapis", Villager.Profession.CLERIC, Kind.PLAYER_BUYS, Material.LAPIS_LAZULI, 16, null, 0, 32, 1.10);
        add("cleric_potion", Villager.Profession.CLERIC, Kind.PLAYER_SELLS, Material.POTION, 1, null, 0, 16, .85);

        add("librarian_paper", Villager.Profession.LIBRARIAN, Kind.PLAYER_SELLS, Material.PAPER, 32, null, 0, 48, .75);
        add("librarian_book", Villager.Profession.LIBRARIAN, Kind.PLAYER_SELLS, Material.BOOK, 8, null, 0, 24, .75);
        add("librarian_lapis", Villager.Profession.LIBRARIAN, Kind.PLAYER_BUYS, Material.LAPIS_LAZULI, 16, null, 0, 32, 1.08);
        add("librarian_redstone", Villager.Profession.LIBRARIAN, Kind.PLAYER_BUYS, Material.REDSTONE, 16, null, 0, 32, 1.08);
        add("librarian_glass", Villager.Profession.LIBRARIAN, Kind.PLAYER_BUYS, Material.GLASS, 16, null, 0, 24, 1.10);

        add("mason_clay", Villager.Profession.MASON, Kind.PLAYER_SELLS, Material.CLAY_BALL, 32, null, 0, 48, .75);
        add("mason_brick", Villager.Profession.MASON, Kind.PLAYER_SELLS, Material.BRICK, 32, null, 0, 32, .78);
        add("mason_obsidian", Villager.Profession.MASON, Kind.PLAYER_SELLS, Material.OBSIDIAN, 8, null, 0, 16, .70);
        add("mason_glass", Villager.Profession.MASON, Kind.PLAYER_BUYS, Material.GLASS, 32, null, 0, 32, 1.08);
        add("mason_stone", Villager.Profession.MASON, Kind.PLAYER_BUYS, Material.STONE, 32, null, 0, 32, 1.08);

        add("toolsmith_iron", Villager.Profession.TOOLSMITH, Kind.PLAYER_SELLS, Material.IRON_INGOT, 16, null, 0, 32, .65);
        add("toolsmith_diamond", Villager.Profession.TOOLSMITH, Kind.PLAYER_SELLS, Material.DIAMOND, 4, null, 0, 8, .60);
        add("toolsmith_redstone", Villager.Profession.TOOLSMITH, Kind.PLAYER_BUYS, Material.REDSTONE, 32, null, 0, 32, 1.08);
        add("weaponsmith_gold", Villager.Profession.WEAPONSMITH, Kind.PLAYER_SELLS, Material.GOLD_INGOT, 16, null, 0, 24, .65);
        add("weaponsmith_diamond", Villager.Profession.WEAPONSMITH, Kind.PLAYER_BUYS, Material.DIAMOND_SWORD, 1, null, 0, 8, 1.10);
        add("armorer_iron", Villager.Profession.ARMORER, Kind.PLAYER_SELLS, Material.IRON_INGOT, 16, null, 0, 32, .65);
        add("armorer_diamond", Villager.Profession.ARMORER, Kind.PLAYER_BUYS, Material.DIAMOND_CHESTPLATE, 1, null, 0, 6, 1.10);

        add("fletcher_feather", Villager.Profession.FLETCHER, Kind.PLAYER_SELLS, Material.FEATHER, 32, null, 0, 32, .75);
        add("fletcher_string", Villager.Profession.FLETCHER, Kind.PLAYER_SELLS, Material.STRING, 32, null, 0, 32, .75);
        add("fletcher_arrow", Villager.Profession.FLETCHER, Kind.PLAYER_BUYS, Material.ARROW, 32, null, 0, 32, 1.10);
        add("leather_leather", Villager.Profession.LEATHERWORKER, Kind.PLAYER_SELLS, Material.LEATHER, 16, null, 0, 24, .75);
        add("leather_rabbit", Villager.Profession.LEATHERWORKER, Kind.PLAYER_SELLS, Material.RABBIT_HIDE, 16, null, 0, 24, .75);
        add("shepherd_wool", Villager.Profession.SHEPHERD, Kind.PLAYER_SELLS, Material.WHITE_WOOL, 16, null, 0, 32, .75);
        add("shepherd_dye", Villager.Profession.SHEPHERD, Kind.PLAYER_BUYS, Material.WHITE_DYE, 16, null, 0, 24, 1.10);
        add("butcher_beef", Villager.Profession.BUTCHER, Kind.PLAYER_SELLS, Material.BEEF, 16, null, 0, 32, .70);
        add("butcher_pork", Villager.Profession.BUTCHER, Kind.PLAYER_SELLS, Material.PORKCHOP, 16, null, 0, 32, .70);
        add("cartographer_paper", Villager.Profession.CARTOGRAPHER, Kind.PLAYER_SELLS, Material.PAPER, 32, null, 0, 32, .75);
        add("cartographer_compass", Villager.Profession.CARTOGRAPHER, Kind.PLAYER_BUYS, Material.COMPASS, 1, null, 0, 12, 1.10);
        add("nitwit_wheat", Villager.Profession.NITWIT, Kind.PLAYER_SELLS, Material.WHEAT, 32, null, 0, 16, .65);
    }

    private void add(String id, Villager.Profession profession, Kind kind, Material item, int amount,
                     Material other, int otherAmount, int stock, double rate) {
        all.put(id, new Offer(id, profession, kind, item, amount, other, otherAmount, stock, rate));
    }

    private void loadOrRefresh() {
        nextRefresh = data.getLong("next-refresh", 0L);
        for (Villager.Profession profession : Villager.Profession.values()) {
            List<String> ids = data.getStringList("active." + profession.name());
            List<Offer> offers = ids.stream().map(all::get).filter(Objects::nonNull)
                    .filter(o -> o.profession() == profession).collect(Collectors.toCollection(ArrayList::new));
            if (offers.isEmpty()) offers = choose(profession);
            active.put(profession, offers);
            for (Offer offer : offers) remaining.put(offer.id(), data.getInt("stock." + offer.id(), offer.stock()));
        }
        if (nextRefresh <= System.currentTimeMillis()) refreshAll();
        else save();
    }

    private List<Offer> choose(Villager.Profession profession) {
        List<Offer> pool = all.values().stream().filter(o -> o.profession() == profession).collect(Collectors.toCollection(ArrayList::new));
        Collections.shuffle(pool);
        return new ArrayList<>(pool.subList(0, Math.min(6, pool.size())));
    }

    private void refreshAll() {
        for (Villager.Profession profession : Villager.Profession.values()) {
            List<Offer> offers = choose(profession);
            active.put(profession, offers);
            for (Offer offer : offers) remaining.put(offer.id(), offer.stock());
        }
        nextRefresh = System.currentTimeMillis() + REFRESH_MILLIS;
        save();
        for (Entity entity : Bukkit.getWorlds().stream().flatMap(w -> w.getEntities().stream()).toList()) {
            if (entity instanceof Villager villager && villager.getProfession() != Villager.Profession.NONE) {
                villager.setRecipes(recipes(villager.getProfession()));
            }
        }
        Bukkit.broadcastMessage(ChatColor.DARK_GRAY + "[" + ChatColor.GOLD + "XZH MARKET" + ChatColor.DARK_GRAY
                + "] " + ChatColor.YELLOW + "Villager offers and global stock have refreshed.");
    }

    private List<MerchantRecipe> recipes(Villager.Profession profession) {
        List<MerchantRecipe> result = new ArrayList<>();
        for (Offer offer : active.getOrDefault(profession, List.of())) {
            int stock = remaining.getOrDefault(offer.id(), offer.stock());
            ItemStack output;
            List<ItemStack> inputs = new ArrayList<>();
            if (offer.kind() == Kind.BARTER) {
                inputs.add(new ItemStack(offer.item(), offer.amount()));
                output = new ItemStack(offer.other(), offer.otherAmount());
            } else if (offer.kind() == Kind.PLAYER_SELLS) {
                inputs.add(new ItemStack(offer.item(), offer.amount()));
                int payout = Math.max(1, (int) Math.floor(referenceBuyPrice(offer.item()) * offer.payoutRate() * offer.amount()));
                output = coins(payout);
            } else {
                int price = Math.max(1, (int) Math.ceil(referenceBuyPrice(offer.item()) * offer.payoutRate() * offer.amount()));
                addCoinInputs(inputs, price);
                output = new ItemStack(offer.item(), offer.amount());
            }
            if (output == null || output.getAmount() > output.getMaxStackSize()) continue;
            int maxUses = Math.max(1, stock);
            int uses = stock <= 0 ? maxUses : 0;
            MerchantRecipe recipe = new MerchantRecipe(output, uses, maxUses, false, 0, 0.0f);
            for (ItemStack input : inputs) recipe.addIngredient(input);
            result.add(recipe);
        }
        return result;
    }

    private double referenceBuyPrice(Material material) {
        String path = "shop.prices." + material.name().toLowerCase(Locale.ROOT) + ".buy";
        if (plugin.getConfig().contains(path)) return Math.max(1, plugin.getConfig().getDouble(path));
        return switch (material) {
            case WITHER_SKELETON_SKULL -> 1000;
            case POTION -> 150;
            case AXOLOTL_BUCKET -> 1200;
            case TROPICAL_FISH_BUCKET, PUFFERFISH_BUCKET -> 180;
            case LAVA_BUCKET -> 100;
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
            case DIAMOND_SWORD -> 500;
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
        int price = Math.max(1, (int) Math.round(rawPrice / 10.0) * 10);
        int thousands = price / 1000;
        int remainder = price % 1000;
        if (thousands > 0) inputs.add(plugin.getPocketCoinManager().createCoin(1000, Math.min(64, thousands)));
        if (remainder > 0) {
            int tens = remainder / 10;
            if (tens > 0) inputs.add(plugin.getPocketCoinManager().createCoin(10, Math.min(64, tens)));
        }
        if (inputs.isEmpty()) inputs.add(plugin.getPocketCoinManager().createCoin(10, 1));
        while (inputs.size() > 2) inputs.remove(inputs.size() - 1);
    }

    private void save() {
        data.set("next-refresh", nextRefresh);
        for (Villager.Profession profession : Villager.Profession.values()) {
            data.set("active." + profession.name(), active.getOrDefault(profession, List.of()).stream().map(Offer::id).toList());
        }
        for (Map.Entry<String, Integer> entry : remaining.entrySet()) data.set("stock." + entry.getKey(), entry.getValue());
        try { file.getParentFile().mkdirs(); data.save(file); }
        catch (IOException ex) { plugin.getLogger().severe("Could not save global villager stock: " + ex.getMessage()); }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAcquireTrade(VillagerAcquireTradeEvent event) {
        // Vanilla level-up offers must not add enchanted books or bypass the XZH stock pool.
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onVillagerInteract(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Villager villager)) return;
        if (villager.getProfession() == Villager.Profession.NONE) return;
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
        int stock = remaining.getOrDefault(offer.id(), offer.stock());
        if (stock <= 0) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "This profession's global stock is exhausted. It refreshes every 2 hours.");
            return;
        }
        remaining.put(offer.id(), stock - 1);
        save();
        // If another listener cancels later, restore the reserved stock next tick.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (event.isCancelled()) {
                remaining.put(offer.id(), Math.min(offer.stock(), remaining.getOrDefault(offer.id(), 0) + 1));
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
            if (plugin.getPocketCoinManager().isCoin(x) && plugin.getPocketCoinManager().isCoin(y)) {
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
