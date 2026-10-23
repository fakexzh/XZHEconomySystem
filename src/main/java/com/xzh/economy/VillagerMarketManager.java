package com.xzh.economy;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerTradeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantInventory;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;

/**
 * Profession-wide randomized merchant market. The vanilla merchant screen is
 * retained; a server-side ledger token represents pocket-balance payment.
 * Offers and cooldowns are shared per profession, while trade counts are per player.
 */
public final class VillagerMarketManager implements Listener {
    private enum Kind { BUY_FOR_COINS, SELL_FOR_COINS, ITEM_EXCHANGE }
    private record Offer(Kind kind, Material input, int inputAmount, Material output, int outputAmount,
                         double coins, String key) { }

    private final XZHEconomySystem plugin;
    private final File file;
    private final YamlConfiguration data;
    private final Map<UUID, Long> appliedSeeds = new HashMap<>();

    public VillagerMarketManager(XZHEconomySystem plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "villager-market.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
        initializeProfessions();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickOpenMerchants, 20L, 20L);
    }

    private void initializeProfessions() {
        for (Villager.Profession profession : Villager.Profession.values()) {
            if (profession == Villager.Profession.NONE || profession == Villager.Profession.NITWIT) continue;
            String base = professionPath(profession);
            if (!data.contains(base + ".seed")) data.set(base + ".seed", new Random().nextLong());
            if (!data.contains(base + ".next-restock")) data.set(base + ".next-restock", System.currentTimeMillis() + restockMillis(profession));
        }
        save();
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onVillagerInteract(PlayerInteractEntityEvent event) {
        if (!plugin.getConfig().getBoolean("villager-market.enabled", true)) return;
        if (!(event.getRightClicked() instanceof Villager villager)) return;
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        if (event.getPlayer().isSneaking()) return;
        Villager.Profession profession = villager.getProfession();
        if (profession == Villager.Profession.NONE || profession == Villager.Profession.NITWIT) return;

        // Do not cancel interaction or manually open a screen: vanilla opens its own
        // MerchantInventory with the villager's normal model and trade layout.
        refreshIfDue(profession);
        applyOffers(villager, profession);
    }

    private void applyOffers(Villager villager, Villager.Profession profession) {
        List<Offer> offers = generateOffers(profession);
        List<MerchantRecipe> recipes = new ArrayList<>();
        for (Offer offer : offers) {
            ItemStack result = new ItemStack(offer.output(), offer.outputAmount());
            MerchantRecipe recipe = new MerchantRecipe(result, 999999);
            recipe.setUses(0);
            recipe.setMaxUses(999999);
            recipe.setExperienceReward(true);
            recipe.setVillagerExperience(2);
            recipe.setIngredients(List.of(new ItemStack(offer.input(), offer.inputAmount())));
            recipes.add(recipe);
        }
        villager.setRecipes(recipes);
        appliedSeeds.put(villager.getUniqueId(), data.getLong(professionPath(profession) + ".seed", 1L));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerTrade(PlayerTradeEvent event) {
        if (!(event.getVillager() instanceof Villager villager)) return;
        if (!plugin.getConfig().getBoolean("villager-market.enabled", true)) return;
        Player player = event.getPlayer();
        Villager.Profession profession = villager.getProfession();
        int offerIndex = -1;
        if (player.getOpenInventory().getTopInventory() instanceof MerchantInventory inventory
                && inventory.getMerchant() == villager) {
            offerIndex = inventory.getSelectedRecipeIndex();
        }
        if (offerIndex < 0) return;
        List<Offer> offers = generateOffers(profession);
        if (offerIndex >= offers.size()) return;
        Offer offer = offers.get(offerIndex);
        String stockPath = stockPath(player.getUniqueId(), profession, offer.key());
        int used = data.getInt(stockPath, 0);
        int limit = Math.max(1, plugin.getConfig().getInt("villager-market.stock-per-player-per-offer", 3));
        if (used >= limit) {
            event.setCancelled(true);
            player.sendMessage(Component.text("You've used all your stock for this offer until the profession restocks.", NamedTextColor.RED));
            return;
        }
        data.set(stockPath, used + 1);
        save();
    }

    private void tickOpenMerchants() {
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!(player.getOpenInventory().getTopInventory() instanceof MerchantInventory inventory)) continue;
            if (!(inventory.getMerchant() instanceof Villager villager)) continue;
            Villager.Profession profession = villager.getProfession();
            refreshIfDue(profession);
            long currentSeed = data.getLong(professionPath(profession) + ".seed", 1L);
            if (appliedSeeds.getOrDefault(villager.getUniqueId(), Long.MIN_VALUE) != currentSeed) {
                applyOffers(villager, profession);
            }
            long remaining = Math.max(0L, data.getLong(professionPath(profession) + ".next-restock", now) - now);
            long seconds = (remaining + 999L) / 1000L;
            String time = seconds >= 3600 ? (seconds / 3600) + "h " + ((seconds % 3600) / 60) + "m"
                    : seconds >= 60 ? (seconds / 60) + "m " + (seconds % 60) + "s" : seconds + "s";
            player.sendActionBar(Component.text("Profession restock: " + time, NamedTextColor.YELLOW));
        }
    }

    private List<Offer> generateOffers(Villager.Profession profession) {
        refreshIfDue(profession);
        long seed = data.getLong(professionPath(profession) + ".seed", 1L);
        Random random = new Random(seed ^ ((long) profession.ordinal() * 0x9E3779B97F4A7C15L));
        List<Material> wanted = wantedItems(profession);
        List<Material> rewards = rewardItems(profession);
        List<Offer> result = new ArrayList<>();
        int count = Math.max(3, Math.min(8, plugin.getConfig().getInt("villager-market.offers-per-profession", 5)));
        for (int i = 0; i < count; i++) {
            Material input = wanted.get(random.nextInt(wanted.size()));
            Material output = rewards.get(random.nextInt(rewards.size()));
            int inputAmount = randomAmount(random, input);
            int outputAmount = randomAmount(random, output);
            // Every offer is a real item-for-item MerchantRecipe. No placeholder,
            // voucher, coin-preview, or intercepted result click is used.
            if (profession == Villager.Profession.FISHERMAN && random.nextInt(4) == 0) {
                input = Material.REDSTONE;
                inputAmount = 64 * (1 + random.nextInt(5));
                output = random.nextBoolean() ? Material.COD : Material.COOKED_COD;
                outputAmount = 8 + random.nextInt(25);
            }
            result.add(new Offer(Kind.ITEM_EXCHANGE, input, inputAmount, output, outputAmount, 0, seed + "-" + i));
        }
        return result;
    }

    private List<Material> wantedItems(Villager.Profession profession) {
        List<Material> list;
        // Profession is a registry-backed OldEnum in current Paper APIs, so use
        // qualified profession constants rather than Java enum switch labels.
        if (profession == Villager.Profession.FARMER) {
            list = new ArrayList<>(List.of(Material.WHEAT, Material.CARROT, Material.POTATO, Material.BEETROOT, Material.PUMPKIN, Material.MELON_SLICE));
        } else if (profession == Villager.Profession.FISHERMAN) {
            list = new ArrayList<>(List.of(Material.COD, Material.SALMON, Material.PUFFERFISH, Material.TROPICAL_FISH, Material.STRING, Material.REDSTONE));
        } else if (profession == Villager.Profession.ARMORER || profession == Villager.Profession.TOOLSMITH || profession == Villager.Profession.WEAPONSMITH) {
            list = new ArrayList<>(List.of(Material.COAL, Material.IRON_INGOT, Material.COPPER_INGOT, Material.GOLD_INGOT, Material.DIAMOND));
        } else if (profession == Villager.Profession.MASON) {
            list = new ArrayList<>(List.of(Material.CLAY_BALL, Material.BRICK, Material.STONE, Material.COBBLESTONE, Material.OBSIDIAN));
        } else if (profession == Villager.Profession.CLERIC) {
            list = new ArrayList<>(List.of(Material.ROTTEN_FLESH, Material.GOLD_INGOT, Material.BLAZE_ROD, Material.ENDER_PEARL, Material.GLOWSTONE_DUST));
        } else if (profession == Villager.Profession.LIBRARIAN || profession == Villager.Profession.CARTOGRAPHER) {
            list = new ArrayList<>(List.of(Material.PAPER, Material.BOOK, Material.INK_SAC, Material.COMPASS, Material.GLASS_PANE));
        } else if (profession == Villager.Profession.BUTCHER) {
            list = new ArrayList<>(List.of(Material.BEEF, Material.PORKCHOP, Material.CHICKEN, Material.MUTTON, Material.RABBIT));
        } else if (profession == Villager.Profession.FLETCHER) {
            list = new ArrayList<>(List.of(Material.STICK, Material.FEATHER, Material.FLINT, Material.STRING));
        } else if (profession == Villager.Profession.SHEPHERD) {
            list = new ArrayList<>(List.of(Material.WHITE_WOOL, Material.BLACK_WOOL, Material.RED_DYE, Material.BLUE_DYE, Material.YELLOW_DYE));
        } else if (profession == Villager.Profession.LEATHERWORKER) {
            list = new ArrayList<>(List.of(Material.LEATHER, Material.RABBIT_HIDE, Material.FLINT, Material.STRING));
        } else {
            list = new ArrayList<>(List.of(Material.COAL, Material.IRON_INGOT, Material.REDSTONE, Material.OBSIDIAN, Material.ENDER_PEARL));
        }
        // Add rare, high-demand items as occasional barter requests.
        if (profession == Villager.Profession.CLERIC || profession == Villager.Profession.WEAPONSMITH
                || profession == Villager.Profession.ARMORER || profession == Villager.Profession.TOOLSMITH) {
            list.addAll(List.of(Material.BLAZE_ROD, Material.OBSIDIAN, Material.LAVA_BUCKET, Material.ENDER_PEARL,
                    Material.WITHER_SKELETON_SKULL, Material.AXOLOTL_BUCKET, Material.COD_BUCKET));
        }
        return list;
    }

    private List<Material> rewardItems(Villager.Profession profession) {
        if (profession == Villager.Profession.FARMER) {
            return List.of(Material.BREAD, Material.COOKED_BEEF, Material.GOLDEN_CARROT, Material.PUMPKIN_PIE, Material.GOLDEN_APPLE);
        } else if (profession == Villager.Profession.FISHERMAN) {
            return List.of(Material.COD, Material.COOKED_COD, Material.SALMON, Material.COOKED_SALMON, Material.TROPICAL_FISH);
        } else if (profession == Villager.Profession.ARMORER) {
            return List.of(Material.IRON_CHESTPLATE, Material.IRON_HELMET, Material.SHIELD, Material.IRON_INGOT, Material.GOLDEN_APPLE);
        } else if (profession == Villager.Profession.TOOLSMITH) {
            return List.of(Material.IRON_PICKAXE, Material.IRON_AXE, Material.DIAMOND_PICKAXE, Material.IRON_INGOT, Material.DIAMOND);
        } else if (profession == Villager.Profession.WEAPONSMITH) {
            return List.of(Material.IRON_SWORD, Material.SHIELD, Material.DIAMOND_SWORD, Material.BLAZE_ROD, Material.ENDER_PEARL);
        } else if (profession == Villager.Profession.MASON) {
            return List.of(Material.BRICKS, Material.OBSIDIAN, Material.GLASS, Material.QUARTZ, Material.CLAY);
        } else if (profession == Villager.Profession.CLERIC) {
            return List.of(Material.GLOWSTONE, Material.BLAZE_ROD, Material.ENDER_PEARL, Material.EXPERIENCE_BOTTLE, Material.GOLDEN_APPLE);
        } else if (profession == Villager.Profession.LIBRARIAN) {
            return List.of(Material.BOOK, Material.BOOKSHELF, Material.PAPER, Material.COMPASS, Material.ENDER_PEARL);
        } else if (profession == Villager.Profession.CARTOGRAPHER) {
            return List.of(Material.MAP, Material.COMPASS, Material.GLASS_PANE, Material.PAPER, Material.ENDER_PEARL);
        } else if (profession == Villager.Profession.BUTCHER) {
            return List.of(Material.COOKED_BEEF, Material.COOKED_PORKCHOP, Material.COOKED_CHICKEN, Material.COOKED_MUTTON, Material.GOLDEN_CARROT);
        } else if (profession == Villager.Profession.FLETCHER) {
            return List.of(Material.ARROW, Material.BOW, Material.CROSSBOW, Material.FLINT, Material.ENDER_PEARL);
        } else if (profession == Villager.Profession.SHEPHERD) {
            return List.of(Material.WHITE_WOOL, Material.BLACK_WOOL, Material.RED_WOOL, Material.BLUE_WOOL, Material.WHITE_BANNER);
        } else if (profession == Villager.Profession.LEATHERWORKER) {
            return List.of(Material.LEATHER, Material.LEATHER_CHESTPLATE, Material.SADDLE, Material.RABBIT_HIDE, Material.LEAD);
        }
        return List.of(Material.IRON_INGOT, Material.GOLD_INGOT, Material.DIAMOND, Material.OBSIDIAN, Material.ENDER_PEARL);
    }

    private int randomAmount(Random random, Material material) {
        if (material == Material.WITHER_SKELETON_SKULL || material == Material.LAVA_BUCKET
                || material == Material.AXOLOTL_BUCKET || material == Material.COD_BUCKET) return 1;
        if (material == Material.ENDER_PEARL || material == Material.BLAZE_ROD || material == Material.DIAMOND) return Math.min(material.getMaxStackSize(), 1 + random.nextInt(4));
        if (material.getMaxStackSize() == 1) return 1;
        return Math.min(material.getMaxStackSize(), 4 + random.nextInt(29));
    }

    private Material containerReturnFor(Material material) {
        return switch (material) {
            case LAVA_BUCKET, COD_BUCKET, SALMON_BUCKET, PUFFERFISH_BUCKET, TROPICAL_FISH_BUCKET,
                    AXOLOTL_BUCKET, MILK_BUCKET, WATER_BUCKET -> Material.BUCKET;
            default -> null;
        };
    }

    private ItemStack previewResult(Offer offer) {
        return new ItemStack(offer.output(), offer.outputAmount());
    }

    private void giveReward(Player player, Material material, int amount) {
        var leftovers = player.getInventory().addItem(new ItemStack(material, amount));
        for (ItemStack leftover : leftovers.values()) player.getWorld().dropItemNaturally(player.getLocation(), leftover);
    }

    private void refreshIfDue(Villager.Profession profession) {
        String base = professionPath(profession);
        long now = System.currentTimeMillis();
        long next = data.getLong(base + ".next-restock", now + restockMillis(profession));
        if (now < next) return;
        data.set(base + ".seed", new Random().nextLong());
        data.set(base + ".next-restock", now + restockMillis(profession));
        save();
    }

    private long restockMillis(Villager.Profession profession) {
        long minutes = Math.max(1L, plugin.getConfig().getLong("villager-market.profession-restock-minutes."
                + profession.name().toLowerCase(Locale.ROOT),
                plugin.getConfig().getLong("villager-market.restock-duration-minutes", 120L)));
        return minutes * 60_000L;
    }

    private String professionPath(Villager.Profession profession) {
        return "professions." + profession.name().toLowerCase(Locale.ROOT);
    }

    private String stockPath(UUID player, Villager.Profession profession, String offerKey) {
        return "player-stock." + player + "." + profession.name().toLowerCase(Locale.ROOT) + "." + offerKey;
    }

    private String pretty(Material material) {
        return material == null ? "coins" : material.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private String money(double amount) {
        return String.format(Locale.US, "%.0f", amount);
    }

    private void save() {
        try { data.save(file); }
        catch (IOException e) { plugin.getLogger().severe("Could not save villager-market.yml: " + e.getMessage()); }
    }
}
