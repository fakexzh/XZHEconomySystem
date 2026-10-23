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
import org.bukkit.inventory.Inventory;
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
    private final Map<UUID, TradeSession> sessions = new HashMap<>();
    private static final String MENU_PREFIX = ChatColor.DARK_GRAY + "XZH Villager: ";
    private static final int[] OFFER_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};
    private enum Screen { MAIN, OFFERS, CONFIRM }
    private enum TradeMode { BUY, SELL }
    private static final class TradeSession {
        private final Villager.Profession profession;
        private final UUID villagerId;
        private Screen screen = Screen.MAIN;
        private TradeMode mode = TradeMode.BUY;
        private List<Offer> offers = new ArrayList<>();
        private Offer selected;
        private long seed;
        private TradeSession(Villager.Profession profession, UUID villagerId) {
            this.profession = profession; this.villagerId = villagerId;
        }
    }

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
        if (!plugin.getConfig().getBoolean("villager-market.enabled", true)) return;
        if (!(event.getRightClicked() instanceof Villager villager)) return;
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND || event.getPlayer().isSneaking()) return;
        Villager.Profession profession = villager.getProfession();
        if (profession == Villager.Profession.NONE || profession == Villager.Profession.NITWIT) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        refreshIfDue(profession);
        TradeSession session = new TradeSession(profession, villager.getUniqueId());
        session.seed = data.getLong(professionPath(profession) + ".seed", 1L);
        sessions.put(player.getUniqueId(), session);
        openMainMenu(player, session);
    }

    private void openMainMenu(Player player, TradeSession session) {
        session.screen = Screen.MAIN;
        Inventory inv = Bukkit.createInventory(null, 27, MENU_PREFIX + "Market");
        fillMenu(inv);
        inv.setItem(11, menuItem(Material.EMERALD, ChatColor.GREEN + "BUY ITEMS",
                List.of(ChatColor.GRAY + "Browse items this villager sells",
                        ChatColor.YELLOW + "Prices use pocket coins", ChatColor.DARK_GRAY + "Click to view offers")));
        inv.setItem(15, menuItem(Material.GOLD_INGOT, ChatColor.GOLD + "SELL ITEMS",
                List.of(ChatColor.GRAY + "Sell requested items to this villager",
                        ChatColor.YELLOW + "Earnings go to pocket coins", ChatColor.DARK_GRAY + "Click to view offers")));
        inv.setItem(22, menuItem(Material.BARRIER, ChatColor.RED + "CLOSE", List.of(ChatColor.GRAY + "Close this menu")));
        inv.setItem(4, menuItem(Material.VILLAGER_SPAWN_EGG, ChatColor.AQUA + prettyProfession(session.profession),
                List.of(ChatColor.GRAY + "Choose whether to buy or sell.", ChatColor.YELLOW + "Every trade requires confirmation.")));
        player.openInventory(inv);
    }

    private void openOffersMenu(Player player, TradeSession session) {
        refreshIfDue(session.profession);
        session.seed = data.getLong(professionPath(session.profession) + ".seed", 1L);
        List<Offer> filtered = new ArrayList<>();
        for (Offer offer : generateOffers(session.profession)) {
            if (session.mode == TradeMode.BUY && (offer.kind() == Kind.SELL_FOR_COINS || offer.kind() == Kind.ITEM_EXCHANGE)) filtered.add(offer);
            else if (session.mode == TradeMode.SELL && offer.kind() == Kind.BUY_FOR_COINS) filtered.add(offer);
        }
        session.offers = filtered;
        session.selected = null;
        session.screen = Screen.OFFERS;
        Inventory inv = Bukkit.createInventory(null, 54, MENU_PREFIX + (session.mode == TradeMode.BUY ? "Buy Offers" : "Sell Offers"));
        fillMenu(inv);
        inv.setItem(4, menuItem(Material.VILLAGER_SPAWN_EGG,
                session.mode == TradeMode.BUY ? ChatColor.GREEN + "BUY OFFERS" : ChatColor.GOLD + "SELL OFFERS",
                List.of(ChatColor.GRAY + prettyProfession(session.profession),
                        ChatColor.YELLOW + "Coins: " + money(plugin.getBalance(player.getUniqueId())),
                        ChatColor.GRAY + "Select an offer to review it.")));
        for (int i = 0; i < filtered.size() && i < OFFER_SLOTS.length; i++) inv.setItem(OFFER_SLOTS[i], offerIcon(filtered.get(i)));
        inv.setItem(49, menuItem(Material.ARROW, ChatColor.AQUA + "BACK", List.of(ChatColor.GRAY + "Return to Buy / Sell")));
        inv.setItem(45, menuItem(Material.BARRIER, ChatColor.RED + "CLOSE", List.of(ChatColor.GRAY + "Close this menu")));
        player.openInventory(inv);
    }

    private void openConfirmMenu(Player player, TradeSession session, Offer offer) {
        session.selected = offer;
        session.screen = Screen.CONFIRM;
        Inventory inv = Bukkit.createInventory(null, 27, MENU_PREFIX + "Confirm Trade");
        fillMenu(inv);
        ItemStack preview;
        List<String> lore = new ArrayList<>();
        if (offer.kind() == Kind.SELL_FOR_COINS) {
            preview = new ItemStack(offer.output(), offer.outputAmount());
            lore.add(ChatColor.GRAY + "You receive: " + ChatColor.WHITE + offer.outputAmount() + " " + pretty(offer.output()));
            lore.add(ChatColor.GRAY + "You pay: " + ChatColor.YELLOW + money(offer.coins()) + " coins");
        } else if (offer.kind() == Kind.BUY_FOR_COINS) {
            preview = new ItemStack(offer.input(), Math.max(1, Math.min(offer.inputAmount(), offer.input().getMaxStackSize())));
            lore.add(ChatColor.GRAY + "You give: " + ChatColor.WHITE + offer.inputAmount() + " " + pretty(offer.input()));
            lore.add(ChatColor.GRAY + "You receive: " + ChatColor.YELLOW + money(offer.coins()) + " coins");
        } else {
            preview = new ItemStack(offer.output(), Math.max(1, Math.min(offer.outputAmount(), offer.output().getMaxStackSize())));
            lore.add(ChatColor.GRAY + "You give: " + ChatColor.WHITE + offer.inputAmount() + " " + pretty(offer.input()));
            lore.add(ChatColor.GRAY + "You receive: " + ChatColor.WHITE + offer.outputAmount() + " " + pretty(offer.output()));
        }
        lore.add(ChatColor.DARK_GRAY + "Nothing changes until you confirm.");
        ItemMeta meta = preview.getItemMeta();
        if (meta != null) { meta.setDisplayName(ChatColor.AQUA + "Trade Summary"); meta.setLore(lore); preview.setItemMeta(meta); }
        inv.setItem(13, preview);
        inv.setItem(11, menuItem(Material.LIME_DYE, ChatColor.GREEN + "CONFIRM TRADE",
                List.of(ChatColor.GRAY + "Complete this exact trade",
                        ChatColor.YELLOW + "Pocket coins: " + money(plugin.getBalance(player.getUniqueId())))));
        inv.setItem(15, menuItem(Material.RED_DYE, ChatColor.RED + "CANCEL", List.of(ChatColor.GRAY + "Return to the offers")));
        player.openInventory(inv);
    }

    private ItemStack offerIcon(Offer offer) {
        Material icon = offer.kind() == Kind.BUY_FOR_COINS ? offer.input() : offer.output();
        if (icon == null || icon == Material.AIR) icon = Material.EMERALD;
        int amount = offer.kind() == Kind.BUY_FOR_COINS ? offer.inputAmount() : offer.outputAmount();
        ItemStack item = new ItemStack(icon, Math.max(1, Math.min(icon.getMaxStackSize(), amount)));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<String> lore = new ArrayList<>();
            if (offer.kind() == Kind.BUY_FOR_COINS) {
                meta.setDisplayName(ChatColor.GOLD + "Sell " + offer.inputAmount() + " " + pretty(offer.input()));
                lore.add(ChatColor.GRAY + "You give: " + ChatColor.WHITE + offer.inputAmount() + " " + pretty(offer.input()));
                lore.add(ChatColor.GRAY + "You earn: " + ChatColor.YELLOW + money(offer.coins()) + " coins");
            } else if (offer.kind() == Kind.SELL_FOR_COINS) {
                meta.setDisplayName(ChatColor.GREEN + "Buy " + offer.outputAmount() + " " + pretty(offer.output()));
                lore.add(ChatColor.GRAY + "You receive: " + ChatColor.WHITE + offer.outputAmount() + " " + pretty(offer.output()));
                lore.add(ChatColor.GRAY + "Price: " + ChatColor.YELLOW + money(offer.coins()) + " coins");
            } else {
                meta.setDisplayName(ChatColor.GREEN + "Exchange for " + offer.outputAmount() + " " + pretty(offer.output()));
                lore.add(ChatColor.GRAY + "You give: " + ChatColor.WHITE + offer.inputAmount() + " " + pretty(offer.input()));
                lore.add(ChatColor.GRAY + "You receive: " + ChatColor.WHITE + offer.outputAmount() + " " + pretty(offer.output()));
                lore.add(ChatColor.YELLOW + "No coin cost");
            }
            lore.add(ChatColor.DARK_GRAY + "Limited stock until profession restock");
            lore.add(ChatColor.AQUA + "Click to review and confirm");
            meta.setLore(lore); item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onMarketMenuClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        TradeSession session = sessions.get(player.getUniqueId());
        if (session == null) return;
        String title = event.getView().getTitle();
        if (title == null || !title.startsWith(MENU_PREFIX)) return;
        event.setCancelled(true);
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;
        if (session.screen == Screen.MAIN) {
            if (event.getRawSlot() == 11 || event.getRawSlot() == 15) {
                session.mode = event.getRawSlot() == 11 ? TradeMode.BUY : TradeMode.SELL;
                openOffersMenu(player, session);
            } else if (event.getRawSlot() == 22) { sessions.remove(player.getUniqueId()); player.closeInventory(); }
            return;
        }
        if (session.screen == Screen.OFFERS) {
            if (event.getRawSlot() == 49) { openMainMenu(player, session); return; }
            if (event.getRawSlot() == 45) { sessions.remove(player.getUniqueId()); player.closeInventory(); return; }
            for (int i = 0; i < OFFER_SLOTS.length && i < session.offers.size(); i++) {
                if (event.getRawSlot() == OFFER_SLOTS[i]) { openConfirmMenu(player, session, session.offers.get(i)); return; }
            }
            return;
        }
        if (session.screen == Screen.CONFIRM) {
            if (event.getRawSlot() == 15) openOffersMenu(player, session);
            else if (event.getRawSlot() == 11) {
                sessions.remove(player.getUniqueId());
                player.closeInventory();
                completeTrade(player, session);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onMarketMenuDrag(InventoryDragEvent event) {
        if (event.getView().getTitle() != null && event.getView().getTitle().startsWith(MENU_PREFIX)) event.setCancelled(true);
    }

    private void completeTrade(Player player, TradeSession session) {
        Offer offer = session.selected;
        if (offer == null || !player.isOnline()) return;
        refreshIfDue(session.profession);
        long currentSeed = data.getLong(professionPath(session.profession) + ".seed", 1L);
        if (currentSeed != session.seed) {
            player.sendMessage(ChatColor.YELLOW + "This profession restocked. Please choose a new offer.");
            TradeSession refreshed = new TradeSession(session.profession, session.villagerId);
            refreshed.seed = currentSeed; sessions.put(player.getUniqueId(), refreshed); openMainMenu(player, refreshed); return;
        }
        Villager villager = null;
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            org.bukkit.entity.Entity entity = world.getEntity(session.villagerId);
            if (entity instanceof Villager found && found.isValid()) { villager = found; break; }
        }
        if (villager == null || villager.getProfession() != session.profession) {
            player.sendMessage(ChatColor.RED + "That villager is no longer available."); return;
        }
        String stock = stockPath(player.getUniqueId(), session.profession, offer.key());
        int used = data.getInt(stock, 0);
        int limit = Math.max(1, plugin.getConfig().getInt("villager-market.stock-per-player-per-offer", 3));
        if (used >= limit) { player.sendMessage(ChatColor.RED + "You have used all stock for this offer until restock."); return; }
        if (offer.input() != null && countMaterial(player, offer.input()) < offer.inputAmount()) {
            player.sendMessage(ChatColor.RED + "You need " + offer.inputAmount() + " " + pretty(offer.input()) + " to complete this trade."); return;
        }
        if (offer.kind() == Kind.SELL_FOR_COINS && plugin.getBalance(player.getUniqueId()) + 1e-9 < offer.coins()) {
            player.sendMessage(ChatColor.RED + "Not enough pocket coins. Price: " + ChatColor.YELLOW + money(offer.coins()) + " coins" + ChatColor.RED + "."); return;
        }
        if (offer.input() != null) {
            removeMaterial(player, offer.input(), offer.inputAmount());
            Material containerReturn = containerReturnFor(offer.input());
            if (containerReturn != null) giveReward(player, containerReturn, offer.inputAmount());
        }
        if (offer.kind() == Kind.BUY_FOR_COINS) {
            plugin.setBalance(player.getUniqueId(), plugin.getBalance(player.getUniqueId()) + offer.coins());
            player.sendMessage(ChatColor.GREEN + "Sold items to villager. Earned " + ChatColor.YELLOW + money(offer.coins()) + " coins" + ChatColor.GREEN + " to your pocket.");
        } else if (offer.kind() == Kind.SELL_FOR_COINS) {
            plugin.setBalance(player.getUniqueId(), plugin.getBalance(player.getUniqueId()) - offer.coins());
            giveReward(player, offer.output(), offer.outputAmount());
            player.sendMessage(ChatColor.GREEN + "Purchase complete! Paid " + ChatColor.YELLOW + money(offer.coins()) + " coins" + ChatColor.GREEN + ".");
        } else {
            giveReward(player, offer.output(), offer.outputAmount());
            player.sendMessage(ChatColor.GREEN + "Exchange complete!");
        }
        data.set(stock, used + 1); save();
        int xp = Math.min(250, villager.getVillagerExperience() + 2);
        villager.setVillagerExperience(xp);
        int level = xp >= 250 ? 5 : xp >= 150 ? 4 : xp >= 70 ? 3 : xp >= 10 ? 2 : 1;
        if (level > villager.getVillagerLevel()) villager.setVillagerLevel(level);
        TradeSession next = new TradeSession(session.profession, session.villagerId);
        next.seed = currentSeed; sessions.put(player.getUniqueId(), next); openMainMenu(player, next);
    }

    private int countMaterial(Player player, Material material) {
        int count = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) if (item != null && item.getType() == material) count += item.getAmount();
        return count;
    }

    private void removeMaterial(Player player, Material material, int amount) {
        int remaining = amount;
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack item = contents[i];
            if (item == null || item.getType() != material) continue;
            int take = Math.min(remaining, item.getAmount()); remaining -= take;
            if (item.getAmount() == take) contents[i] = null; else item.setAmount(item.getAmount() - take);
        }
        player.getInventory().setStorageContents(contents);
    }

    private void fillMenu(Inventory inv) {
        ItemStack filler = menuItem(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler);
    }

    private ItemStack menuItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) { meta.setDisplayName(name); meta.setLore(lore); item.setItemMeta(meta); }
        return item;
    }

    private String prettyProfession(Villager.Profession profession) {
        String raw = profession.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        StringBuilder result = new StringBuilder();
        for (String part : raw.split(" ")) {
            if (part.isEmpty()) continue;
            if (result.length() > 0) result.append(' ');
            result.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return result.toString();
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
