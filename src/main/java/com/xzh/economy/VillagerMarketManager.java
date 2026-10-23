package com.xzh.economy;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantInventory;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
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
    private final NamespacedKey voucherKey;
    private final File file;
    private final YamlConfiguration data;
    private final Map<UUID, Long> appliedSeeds = new HashMap<>();

    public VillagerMarketManager(XZHEconomySystem plugin) {
        this.plugin = plugin;
        this.voucherKey = new NamespacedKey(plugin, "villager_balance_voucher");
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

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVillagerInteract(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Villager villager)) return;
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        if (event.getPlayer().isSneaking()) return;
        Villager.Profession profession = villager.getProfession();
        if (profession == Villager.Profession.NONE || profession == Villager.Profession.NITWIT) return;

        event.setCancelled(true);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!event.getPlayer().isOnline() || !villager.isValid()) return;
            refreshIfDue(profession);
            applyOffers(villager, profession);
            event.getPlayer().openMerchant(villager, true);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!event.getPlayer().isOnline()) return;
                if (event.getPlayer().getOpenInventory().getTopInventory() instanceof MerchantInventory inventory) {
                    inventory.setItem(0, createVoucher());
                }
            });
        });
    }

    private void applyOffers(Villager villager, Villager.Profession profession) {
        List<Offer> offers = generateOffers(profession);
        List<MerchantRecipe> recipes = new ArrayList<>();
        for (Offer offer : offers) {
            ItemStack result = offer.kind() == Kind.BUY_FOR_COINS
                    ? coinPreview(offer.coins())
                    : previewResult(offer);
            MerchantRecipe recipe = new MerchantRecipe(result, 999999);
            recipe.setUses(0);
            recipe.setMaxUses(999999);
            recipe.setExperienceReward(true);
            List<ItemStack> ingredients = new ArrayList<>();
            ingredients.add(createVoucher());
            if (offer.input() != null && offer.inputAmount() > 0) ingredients.add(new ItemStack(offer.input(), offer.inputAmount()));
            recipe.setIngredients(ingredients);
            recipes.add(recipe);
        }
        villager.setRecipes(recipes);
        appliedSeeds.put(villager.getUniqueId(), data.getLong(professionPath(profession) + ".seed", 1L));
        // Store current offer identity on the entity so a second villager of this profession
        // cannot grant additional stock; the global seed remains the source of truth.
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMerchantClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory() instanceof MerchantInventory inventory)) return;
        if (!(inventory.getMerchant() instanceof Villager villager)) return;
        if (event.getSlotType() == InventoryType.SlotType.RESULT && event.getRawSlot() == 2) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player player)) return;
            Villager.Profession profession = villager.getProfession();
            refreshIfDue(profession);
            List<Offer> offers = generateOffers(profession);
            int selected = inventory.getSelectedRecipeIndex();
            if (selected < 0 || selected >= offers.size()) return;
            Offer offer = offers.get(selected);
            String stockPath = stockPath(player.getUniqueId(), profession, offer.key());
            int used = data.getInt(stockPath, 0);
            int limit = Math.max(1, plugin.getConfig().getInt("villager-market.stock-per-player-per-offer", 3));
            if (used >= limit) {
                player.sendMessage(Component.text("You've used all your stock for this offer until the profession restocks.", NamedTextColor.RED));
                return;
            }
            ItemStack input = inventory.getItem(1);
            if (offer.input() != null) {
                if (input == null || input.getType() != offer.input() || input.getAmount() < offer.inputAmount()) {
                    player.sendMessage(Component.text("Place the requested items in the second trade slot.", NamedTextColor.RED));
                    return;
                }
            }
            if (offer.kind() == Kind.SELL_FOR_COINS) {
                double balance = plugin.getBalance(player.getUniqueId());
                if (balance + 1e-9 < offer.coins()) {
                    player.sendMessage(Component.text("Not enough pocket balance. This offer costs " + money(offer.coins()) + " coins.", NamedTextColor.RED));
                    return;
                }
            }
            if (offer.input() != null) {
                int left = input.getAmount() - offer.inputAmount();
                if (left <= 0) inventory.setItem(1, null);
                else input.setAmount(left);
            }
            if (offer.kind() == Kind.BUY_FOR_COINS) {
                double old = plugin.getBalance(player.getUniqueId());
                plugin.setBalance(player.getUniqueId(), old + offer.coins());
                player.sendMessage(Component.text("Villager paid you " + money(offer.coins()) + " coins into your pocket balance.", NamedTextColor.GREEN));
            } else if (offer.kind() == Kind.SELL_FOR_COINS) {
                plugin.setBalance(player.getUniqueId(), plugin.getBalance(player.getUniqueId()) - offer.coins());
                giveReward(player, offer.output(), offer.outputAmount());
                player.sendMessage(Component.text("Purchased for " + money(offer.coins()) + " coins from your pocket balance.", NamedTextColor.GREEN));
            } else {
                giveReward(player, offer.output(), offer.outputAmount());
                player.sendMessage(Component.text("Trade completed.", NamedTextColor.GREEN));
            }
            data.set(stockPath, used + 1);
            save();
            int xp = Math.min(250, villager.getVillagerExperience() + 2);
            villager.setVillagerExperience(xp);
            int level = xp >= 250 ? 5 : xp >= 150 ? 4 : xp >= 70 ? 3 : xp >= 10 ? 2 : 1;
            if (level > villager.getVillagerLevel()) villager.setVillagerLevel(level);
            inventory.setItem(0, createVoucher());
            inventory.setItem(2, null);
            player.updateInventory();
        } else if (event.getRawSlot() == 0 && event.getClickedInventory() == inventory) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMerchantDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory() instanceof MerchantInventory inventory)) return;
        if (event.getRawSlots().contains(0)) event.setCancelled(true);
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
            inventory.setItem(0, createVoucher());
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
            int type = random.nextInt(3);
            Material input = wanted.get(random.nextInt(wanted.size()));
            Material output = rewards.get(random.nextInt(rewards.size()));
            int inputAmount = randomAmount(random, input);
            int outputAmount = randomAmount(random, output);
            String key = seed + "-" + i;
            if (type == 0) {
                double base = plugin.getShopManager().getShopSellPrice(input);
                if (base <= 0) {
                    // Zero-value shop items can still be used in barter deals rather than being
                    // assigned an invented cash value.
                    type = 2;
                } else {
                    double premium = 1.0 + random.nextDouble() * Math.max(0.0,
                            plugin.getConfig().getDouble("villager-market.max-buy-premium", 0.35));
                    double payout = Math.max(1.0, Math.floor(base * inputAmount * premium));
                    result.add(new Offer(Kind.BUY_FOR_COINS, input, inputAmount, Material.GOLD_NUGGET, 1, payout, key));
                    continue;
                }
            }
            if (type == 1) {
                double base = plugin.getShopManager().getShopBuyPrice(output);
                if (base <= 0) base = Math.max(1.0, plugin.getConfig().getDouble("villager-market.fallback-item-price", 20.0));
                double modifier = 0.75 + random.nextDouble() * 0.60;
                double cost = Math.max(1.0, Math.ceil(base * outputAmount * modifier));
                result.add(new Offer(Kind.SELL_FOR_COINS, null, 0, output, outputAmount, cost, key));
            } else {
                // Profession-themed bartering, including unusual/rare requested materials.
                if (profession == Villager.Profession.FISHERMAN && random.nextInt(4) == 0) {
                    input = Material.REDSTONE;
                    inputAmount = 64 * (1 + random.nextInt(5));
                    output = random.nextBoolean() ? Material.COD : Material.COOKED_COD;
                    outputAmount = 8 + random.nextInt(25);
                }
                result.add(new Offer(Kind.ITEM_EXCHANGE, input, inputAmount, output, outputAmount, 0, key));
            }
        }
        return result;
    }

    private List<Material> wantedItems(Villager.Profession profession) {
        List<Material> list = switch (profession) {
            case FARMER -> new ArrayList<>(List.of(Material.WHEAT, Material.CARROT, Material.POTATO, Material.BEETROOT, Material.PUMPKIN, Material.MELON_SLICE));
            case FISHERMAN -> new ArrayList<>(List.of(Material.COD, Material.SALMON, Material.PUFFERFISH, Material.TROPICAL_FISH, Material.STRING, Material.REDSTONE));
            case ARMORER, TOOLSMITH, WEAPONSMITH -> new ArrayList<>(List.of(Material.COAL, Material.IRON_INGOT, Material.COPPER_INGOT, Material.GOLD_INGOT, Material.DIAMOND));
            case MASON -> new ArrayList<>(List.of(Material.CLAY_BALL, Material.BRICK, Material.STONE, Material.COBBLESTONE, Material.OBSIDIAN));
            case CLERIC -> new ArrayList<>(List.of(Material.ROTTEN_FLESH, Material.GOLD_INGOT, Material.BLAZE_ROD, Material.ENDER_PEARL, Material.GLOWSTONE_DUST));
            case LIBRARIAN, CARTOGRAPHER -> new ArrayList<>(List.of(Material.PAPER, Material.BOOK, Material.INK_SAC, Material.COMPASS, Material.GLASS_PANE));
            case BUTCHER -> new ArrayList<>(List.of(Material.BEEF, Material.PORKCHOP, Material.CHICKEN, Material.MUTTON, Material.RABBIT));
            case FLETCHER -> new ArrayList<>(List.of(Material.STICK, Material.FEATHER, Material.FLINT, Material.STRING));
            case SHEPHERD -> new ArrayList<>(List.of(Material.WHITE_WOOL, Material.BLACK_WOOL, Material.RED_DYE, Material.BLUE_DYE, Material.YELLOW_DYE));
            case LEATHERWORKER -> new ArrayList<>(List.of(Material.LEATHER, Material.RABBIT_HIDE, Material.FLINT, Material.STRING));
            default -> new ArrayList<>(List.of(Material.COAL, Material.IRON_INGOT, Material.REDSTONE, Material.OBSIDIAN, Material.ENDER_PEARL));
        };
        // Add rare, high-demand items as occasional barter requests.
        if (profession == Villager.Profession.CLERIC || profession == Villager.Profession.WEAPONSMITH
                || profession == Villager.Profession.ARMORER || profession == Villager.Profession.TOOLSMITH) {
            list.addAll(List.of(Material.BLAZE_ROD, Material.OBSIDIAN, Material.LAVA_BUCKET, Material.ENDER_PEARL,
                    Material.WITHER_SKELETON_SKULL, Material.AXOLOTL_BUCKET, Material.COD_BUCKET));
        }
        return list;
    }

    private List<Material> rewardItems(Villager.Profession profession) {
        return switch (profession) {
            case FARMER -> List.of(Material.BREAD, Material.COOKED_BEEF, Material.GOLDEN_CARROT, Material.PUMPKIN_PIE, Material.GOLDEN_APPLE);
            case FISHERMAN -> List.of(Material.COD, Material.COOKED_COD, Material.SALMON, Material.COOKED_SALMON, Material.TROPICAL_FISH);
            case ARMORER -> List.of(Material.IRON_CHESTPLATE, Material.IRON_HELMET, Material.SHIELD, Material.IRON_INGOT, Material.GOLDEN_APPLE);
            case TOOLSMITH -> List.of(Material.IRON_PICKAXE, Material.IRON_AXE, Material.DIAMOND_PICKAXE, Material.IRON_INGOT, Material.DIAMOND);
            case WEAPONSMITH -> List.of(Material.IRON_SWORD, Material.SHIELD, Material.DIAMOND_SWORD, Material.BLAZE_ROD, Material.ENDER_PEARL);
            case MASON -> List.of(Material.BRICKS, Material.OBSIDIAN, Material.GLASS, Material.QUARTZ, Material.CLAY);
            case CLERIC -> List.of(Material.GLOWSTONE, Material.BLAZE_ROD, Material.ENDER_PEARL, Material.EXPERIENCE_BOTTLE, Material.GOLDEN_APPLE);
            case LIBRARIAN -> List.of(Material.BOOK, Material.BOOKSHELF, Material.PAPER, Material.COMPASS, Material.ENDER_PEARL);
            case CARTOGRAPHER -> List.of(Material.MAP, Material.COMPASS, Material.GLASS_PANE, Material.PAPER, Material.ENDER_PEARL);
            case BUTCHER -> List.of(Material.COOKED_BEEF, Material.COOKED_PORKCHOP, Material.COOKED_CHICKEN, Material.COOKED_MUTTON, Material.GOLDEN_CARROT);
            case FLETCHER -> List.of(Material.ARROW, Material.BOW, Material.CROSSBOW, Material.FLINT, Material.ENDER_PEARL);
            case SHEPHERD -> List.of(Material.WHITE_WOOL, Material.BLACK_WOOL, Material.RED_WOOL, Material.BLUE_WOOL, Material.WHITE_BANNER);
            case LEATHERWORKER -> List.of(Material.LEATHER, Material.LEATHER_CHESTPLATE, Material.SADDLE, Material.RABBIT_HIDE, Material.LEAD);
            default -> List.of(Material.IRON_INGOT, Material.GOLD_INGOT, Material.DIAMOND, Material.OBSIDIAN, Material.ENDER_PEARL);
        };
    }

    private int randomAmount(Random random, Material material) {
        if (material == Material.WITHER_SKELETON_SKULL || material == Material.LAVA_BUCKET
                || material == Material.AXOLOTL_BUCKET || material == Material.COD_BUCKET) return 1;
        if (material == Material.ENDER_PEARL || material == Material.BLAZE_ROD || material == Material.DIAMOND) return Math.min(material.getMaxStackSize(), 1 + random.nextInt(4));
        if (material.getMaxStackSize() == 1) return 1;
        return Math.min(material.getMaxStackSize(), 4 + random.nextInt(29));
    }

    private ItemStack createVoucher() {
        ItemStack voucher = new ItemStack(Material.PAPER);
        ItemMeta meta = voucher.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "XZH Balance");
        meta.setLore(List.of(ChatColor.GRAY + "Villager market payment slot",
                ChatColor.YELLOW + "Coin transactions use your pocket balance."));
        meta.getPersistentDataContainer().set(voucherKey, PersistentDataType.BYTE, (byte) 1);
        voucher.setItemMeta(meta);
        return voucher;
    }

    private ItemStack coinPreview(double coins) {
        ItemStack item = new ItemStack(Material.GOLD_NUGGET);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "XZH Coins");
        meta.setLore(List.of(ChatColor.YELLOW + money(coins) + " coins credited to pocket balance",
                ChatColor.GRAY + "This is a balance payment, not a physical coin."));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack previewResult(Offer offer) {
        ItemStack item = new ItemStack(offer.output(), offer.outputAmount());
        ItemMeta meta = item.getItemMeta();
        List<String> lore = new ArrayList<>();
        if (offer.kind() == Kind.SELL_FOR_COINS) lore.add(ChatColor.YELLOW + "Cost: " + money(offer.coins()) + " pocket coins");
        if (offer.kind() == Kind.ITEM_EXCHANGE) lore.add(ChatColor.GRAY + "Give " + offer.inputAmount() + " " + pretty(offer.input()) + " in the second slot");
        lore.add(ChatColor.DARK_GRAY + "Stock limit applies per player until restock.");
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
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
