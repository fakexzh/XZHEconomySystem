package com.xzh.economy;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;

/**
 * Profession-specific XZH villager market. All trades run through an explicit
 * confirmation menu and use the plugin's pocket-first, bank-second economy API.
 */
public final class VillagerMarketManager implements Listener {
    private enum Screen { MAIN, OFFERS, CONFIRM }
    private enum Mode { BUY, SELL }
    private record Offer(String key, Material item, int amount, int price) { }
    private record Catalog(List<Offer> buy, List<Offer> sell) { }

    private static final String PREFIX = ChatColor.DARK_GRAY + "XZH Villager: ";
    private static final int[] OFFER_SLOTS = {10, 12, 14, 16, 19, 21, 23, 25, 28, 30, 32, 34, 37, 39, 41, 43};

    private static final class Session {
        private final Villager.Profession profession;
        private final UUID villagerId;
        private Screen screen = Screen.MAIN;
        private Mode mode;
        private Offer selected;
        private Session(Villager.Profession profession, UUID villagerId) {
            this.profession = profession;
            this.villagerId = villagerId;
        }
    }

    private final XZHEconomySystem plugin;
    private final File file;
    private final YamlConfiguration data;
    private final Map<UUID, Session> sessions = new HashMap<>();

    public VillagerMarketManager(XZHEconomySystem plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "villager-market.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
        initializeProfessions();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVillagerInteract(PlayerInteractEntityEvent event) {
        if (!plugin.getConfig().getBoolean("villager-market.enabled", true)) return;
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        if (!(event.getRightClicked() instanceof Villager villager)) return;
        Villager.Profession profession = villager.getProfession();
        if (profession == Villager.Profession.NITWIT || profession == Villager.Profession.NONE) return;

        event.setCancelled(true);
        Player player = event.getPlayer();
        refreshIfDue(profession);
        Session session = new Session(profession, villager.getUniqueId());
        sessions.put(player.getUniqueId(), session);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && villager.isValid()) openMain(player, session);
        });
    }

    private void initializeProfessions() {
        long now = System.currentTimeMillis();
        for (Villager.Profession profession : Villager.Profession.values()) {
            if (profession == Villager.Profession.NITWIT || profession == Villager.Profession.NONE) continue;
            String path = professionPath(profession);
            if (!data.contains(path + ".next-restock")) {
                data.set(path + ".next-restock", now + restockMillis());
            }
        }
        save();
    }

    private void openMain(Player player, Session session) {
        session.screen = Screen.MAIN;
        session.mode = null;
        session.selected = null;
        Inventory inv = Bukkit.createInventory(null, 27, PREFIX + "Market");
        fill(inv);
        inv.setItem(4, item(Material.VILLAGER_SPAWN_EGG, ChatColor.AQUA + prettyProfession(session.profession),
                List.of(ChatColor.GRAY + "A profession-specific XZH market.",
                        ChatColor.GRAY + "Every trade has a confirmation step.",
                        ChatColor.YELLOW + "Restock in: " + timeUntilRestock(session.profession))));
        inv.setItem(11, item(Material.EMERALD, ChatColor.GREEN + "BUY",
                List.of(ChatColor.GRAY + "Buy goods from this villager.",
                        ChatColor.YELLOW + "Prices are shown in coins.",
                        ChatColor.DARK_GRAY + "Click to browse offers.")));
        inv.setItem(15, item(Material.GOLD_INGOT, ChatColor.GOLD + "SELL",
                List.of(ChatColor.GRAY + "Sell requested items to this villager.",
                        ChatColor.YELLOW + "Earnings are paid as coins.",
                        ChatColor.DARK_GRAY + "Click to browse offers.")));
        inv.setItem(22, item(Material.BARRIER, ChatColor.RED + "CLOSE", List.of(ChatColor.GRAY + "Close the market.")));
        player.openInventory(inv);
    }

    private void openOffers(Player player, Session session) {
        refreshIfDue(session.profession);
        session.screen = Screen.OFFERS;
        session.selected = null;
        Catalog catalog = catalog(session.profession);
        List<Offer> offers = session.mode == Mode.BUY ? catalog.buy() : catalog.sell();
        Inventory inv = Bukkit.createInventory(null, 54, PREFIX + (session.mode == Mode.BUY ? "Buy Offers" : "Sell Offers"));
        fill(inv);
        inv.setItem(4, item(Material.VILLAGER_SPAWN_EGG,
                (session.mode == Mode.BUY ? ChatColor.GREEN : ChatColor.GOLD)
                        + (session.mode == Mode.BUY ? "BUY OFFERS" : "SELL OFFERS"),
                List.of(ChatColor.GRAY + prettyProfession(session.profession),
                        ChatColor.GRAY + "Pocket: " + ChatColor.YELLOW + money(plugin.getBalance(player.getUniqueId())) + " coins",
                        ChatColor.GRAY + "Bank: " + ChatColor.YELLOW + money(plugin.getBankManager().getBalance(player.getUniqueId())) + " coins",
                        ChatColor.DARK_GRAY + "Offer descriptions show stock, restock time and cooldown.")));
        for (int i = 0; i < offers.size() && i < OFFER_SLOTS.length; i++) {
            inv.setItem(OFFER_SLOTS[i], offerIcon(player, session, offers.get(i)));
        }
        inv.setItem(45, item(Material.BARRIER, ChatColor.RED + "CLOSE", List.of(ChatColor.GRAY + "Close the market.")));
        inv.setItem(49, item(Material.ARROW, ChatColor.AQUA + "BACK", List.of(ChatColor.GRAY + "Return to BUY / SELL.")));
        player.openInventory(inv);
    }

    private void openConfirm(Player player, Session session, Offer offer) {
        session.screen = Screen.CONFIRM;
        session.selected = offer;
        Inventory inv = Bukkit.createInventory(null, 27, PREFIX + "Confirm Trade");
        fill(inv);
        ItemStack preview = new ItemStack(offer.item(), Math.max(1, Math.min(offer.amount(), offer.item().getMaxStackSize())));
        ItemMeta meta = preview.getItemMeta();
        if (meta != null) {
            meta.setDisplayName((session.mode == Mode.BUY ? ChatColor.GREEN : ChatColor.GOLD)
                    + (session.mode == Mode.BUY ? "Buy " : "Sell ") + offer.amount() + " " + pretty(offer.item()));
            List<String> lore = new ArrayList<>();
            if (session.mode == Mode.BUY) {
                lore.add(ChatColor.GRAY + "You receive: " + ChatColor.WHITE + offer.amount() + " " + pretty(offer.item()));
                lore.add(ChatColor.GRAY + "You pay: " + ChatColor.YELLOW + money(offer.price()) + " coins");
            } else {
                lore.add(ChatColor.GRAY + "You give: " + ChatColor.WHITE + offer.amount() + " " + pretty(offer.item()));
                lore.add(ChatColor.GRAY + "You earn: " + ChatColor.YELLOW + money(offer.price()) + " coins");
            }
            addStatusLore(lore, player, session, offer);
            lore.add(ChatColor.DARK_GRAY + "Nothing changes until you confirm.");
            meta.setLore(lore);
            preview.setItemMeta(meta);
        }
        inv.setItem(13, preview);
        inv.setItem(11, item(Material.LIME_DYE, ChatColor.GREEN + "CONFIRM TRADE",
                List.of(ChatColor.GRAY + "Validate stock, cooldown, funds/items and inventory space again.",
                        ChatColor.YELLOW + (session.mode == Mode.BUY ? "Cost: " : "Reward: ") + money(offer.price()) + " coins")));
        inv.setItem(15, item(Material.RED_DYE, ChatColor.RED + "CANCEL",
                List.of(ChatColor.GRAY + "Return to the offers without trading.")));
        player.openInventory(inv);
    }

    private ItemStack offerIcon(Player player, Session session, Offer offer) {
        ItemStack icon = new ItemStack(offer.item(), Math.max(1, Math.min(offer.amount(), offer.item().getMaxStackSize())));
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            meta.setDisplayName((session.mode == Mode.BUY ? ChatColor.GREEN + "Buy " : ChatColor.GOLD + "Sell ")
                    + offer.amount() + " " + pretty(offer.item()));
            List<String> lore = new ArrayList<>();
            if (session.mode == Mode.BUY) {
                lore.add(ChatColor.GRAY + "You receive: " + ChatColor.WHITE + offer.amount() + " " + pretty(offer.item()));
                lore.add(ChatColor.GRAY + "Price: " + ChatColor.YELLOW + money(offer.price()) + " coins");
            } else {
                lore.add(ChatColor.GRAY + "You give: " + ChatColor.WHITE + offer.amount() + " " + pretty(offer.item()));
                lore.add(ChatColor.GRAY + "You earn: " + ChatColor.YELLOW + money(offer.price()) + " coins");
            }
            addStatusLore(lore, player, session, offer);
            lore.add(ChatColor.AQUA + "Click to review and confirm.");
            meta.setLore(lore);
            icon.setItemMeta(meta);
        }
        return icon;
    }

    private void addStatusLore(List<String> lore, Player player, Session session, Offer offer) {
        int limit = stockLimit();
        int remaining = Math.max(0, limit - data.getInt(stockPath(session.profession, session.mode, offer), 0));
        long now = System.currentTimeMillis();
        long cooldownUntil = data.getLong(cooldownPath(player.getUniqueId(), session.profession, session.mode, offer), 0L);
        lore.add(ChatColor.GRAY + "Stock available: " + (remaining > 0 ? ChatColor.GREEN : ChatColor.RED)
                + remaining + ChatColor.GRAY + " / " + limit + " per profession");
        lore.add(ChatColor.GRAY + "Restock in: " + ChatColor.YELLOW + timeUntilRestock(session.profession));
        lore.add(ChatColor.GRAY + "Cooldown: " + ChatColor.YELLOW + formatDuration(cooldownMillis() / 1000L)
                + ChatColor.GRAY + " per player / offer");
        lore.add(ChatColor.GRAY + "Your cooldown: " + (cooldownUntil > now
                ? ChatColor.RED + formatDuration((cooldownUntil - now + 999L) / 1000L)
                : ChatColor.GREEN + "Ready"));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onMenuClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = event.getView().getTitle();
        if (title == null || !title.startsWith(PREFIX)) return;
        event.setCancelled(true);
        Session session = sessions.get(player.getUniqueId());
        if (session == null) return;
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) return;

        if (session.screen == Screen.MAIN) {
            if (slot == 11) { session.mode = Mode.BUY; openOffers(player, session); }
            else if (slot == 15) { session.mode = Mode.SELL; openOffers(player, session); }
            else if (slot == 22) { sessions.remove(player.getUniqueId()); player.closeInventory(); }
            return;
        }
        if (session.screen == Screen.OFFERS) {
            if (slot == 45) { sessions.remove(player.getUniqueId()); player.closeInventory(); return; }
            if (slot == 49) { openMain(player, session); return; }
            Catalog catalog = catalog(session.profession);
            List<Offer> offers = session.mode == Mode.BUY ? catalog.buy() : catalog.sell();
            for (int i = 0; i < offers.size() && i < OFFER_SLOTS.length; i++) {
                if (slot == OFFER_SLOTS[i]) { openConfirm(player, session, offers.get(i)); return; }
            }
            return;
        }
        if (session.screen == Screen.CONFIRM) {
            if (slot == 15) openOffers(player, session);
            else if (slot == 11) completeTrade(player, session);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onMenuDrag(InventoryDragEvent event) {
        String title = event.getView().getTitle();
        if (title != null && title.startsWith(PREFIX)) event.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        sessions.remove(event.getPlayer().getUniqueId());
    }

    private void completeTrade(Player player, Session session) {
        Offer offer = session.selected;
        if (offer == null || !player.isOnline()) return;
        refreshIfDue(session.profession);
        Villager villager = findVillager(session.villagerId);
        if (villager == null || villager.getProfession() != session.profession) {
            player.sendMessage(ChatColor.RED + "That villager is no longer available.");
            sessions.remove(player.getUniqueId());
            player.closeInventory();
            return;
        }

        long now = System.currentTimeMillis();
        long cooldownUntil = data.getLong(cooldownPath(player.getUniqueId(), session.profession, session.mode, offer), 0L);
        if (cooldownUntil > now) {
            player.sendMessage(ChatColor.RED + "This offer is on cooldown for " + ChatColor.YELLOW
                    + formatDuration((cooldownUntil - now + 999L) / 1000L) + ChatColor.RED + ".");
            openOffers(player, session);
            return;
        }
        String stockPath = stockPath(session.profession, session.mode, offer);
        int used = data.getInt(stockPath, 0);
        if (used >= stockLimit()) {
            player.sendMessage(ChatColor.RED + "This offer is out of stock. Restock in "
                    + ChatColor.YELLOW + timeUntilRestock(session.profession) + ChatColor.RED + ".");
            openOffers(player, session);
            return;
        }

        if (session.mode == Mode.BUY) {
            if (plugin.getCombinedBalance(player.getUniqueId()) + 1e-9 < offer.price()) {
                player.sendMessage(ChatColor.RED + "Not enough funds. Price: " + ChatColor.YELLOW + money(offer.price()) + " coins.");
                openOffers(player, session);
                return;
            }
            if (!hasSpace(player, offer.item(), offer.amount())) {
                player.sendMessage(ChatColor.RED + "Make room in your inventory before buying this offer.");
                openOffers(player, session);
                return;
            }
            if (!plugin.spendAvailableFunds(player.getUniqueId(), offer.price())) {
                player.sendMessage(ChatColor.RED + "Your available balance changed; no trade was made.");
                openOffers(player, session);
                return;
            }
            giveItems(player, offer.item(), offer.amount());
            player.sendMessage(ChatColor.GREEN + "Purchase complete: " + ChatColor.WHITE + offer.amount() + " "
                    + pretty(offer.item()) + ChatColor.GREEN + " for " + ChatColor.YELLOW + money(offer.price()) + " coins.");
        } else {
            if (countItems(player, offer.item()) < offer.amount()) {
                player.sendMessage(ChatColor.RED + "You need " + offer.amount() + " " + pretty(offer.item()) + " to sell this offer.");
                openOffers(player, session);
                return;
            }
            removeItems(player, offer.item(), offer.amount());
            plugin.setBalance(player.getUniqueId(), plugin.getBalance(player.getUniqueId()) + offer.price());
            player.sendMessage(ChatColor.GREEN + "Sale complete: earned " + ChatColor.YELLOW + money(offer.price())
                    + " coins" + ChatColor.GREEN + ".");
        }

        data.set(stockPath, used + 1);
        data.set(cooldownPath(player.getUniqueId(), session.profession, session.mode, offer), now + cooldownMillis());
        save();
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.2f);
        openOffers(player, session);
    }

    private Villager findVillager(UUID id) {
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            org.bukkit.entity.Entity entity = world.getEntity(id);
            if (entity instanceof Villager villager && villager.isValid()) return villager;
        }
        return null;
    }

    private boolean hasSpace(Player player, Material material, int amount) {
        int remaining = amount;
        int max = material.getMaxStackSize();
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (stack == null || stack.getType().isAir()) remaining -= max;
            else if (stack.getType() == material && stack.getAmount() < max) remaining -= max - stack.getAmount();
            if (remaining <= 0) return true;
        }
        return remaining <= 0;
    }

    private int countItems(Player player, Material material) {
        int total = 0;
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (stack != null && stack.getType() == material) total += stack.getAmount();
        }
        return total;
    }

    private void removeItems(Player player, Material material, int amount) {
        int remaining = amount;
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack stack = contents[i];
            if (stack == null || stack.getType() != material) continue;
            int taken = Math.min(remaining, stack.getAmount());
            remaining -= taken;
            if (taken == stack.getAmount()) contents[i] = null;
            else stack.setAmount(stack.getAmount() - taken);
        }
        player.getInventory().setStorageContents(contents);
    }

    private void giveItems(Player player, Material material, int amount) {
        int remaining = amount;
        while (remaining > 0) {
            int stackAmount = Math.min(remaining, material.getMaxStackSize());
            player.getInventory().addItem(new ItemStack(material, stackAmount));
            remaining -= stackAmount;
        }
    }

    private Catalog catalog(Villager.Profession profession) {
        if (profession == Villager.Profession.FARMER) return new Catalog(
                List.of(o("bread", Material.BREAD, 8, 12), o("golden-carrot", Material.GOLDEN_CARROT, 4, 80),
                        o("pumpkin-pie", Material.PUMPKIN_PIE, 4, 30), o("golden-apple", Material.GOLDEN_APPLE, 1, 150)),
                List.of(o("wheat", Material.WHEAT, 16, 5), o("carrot", Material.CARROT, 16, 4),
                        o("potato", Material.POTATO, 16, 4), o("beetroot", Material.BEETROOT, 16, 3),
                        o("pumpkin", Material.PUMPKIN, 8, 6), o("melon-slice", Material.MELON_SLICE, 16, 4)));
        if (profession == Villager.Profession.FISHERMAN) return new Catalog(
                List.of(o("cooked-cod", Material.COOKED_COD, 8, 20), o("cooked-salmon", Material.COOKED_SALMON, 8, 28),
                        o("fishing-rod", Material.FISHING_ROD, 1, 35)),
                List.of(o("cod", Material.COD, 16, 8), o("salmon", Material.SALMON, 16, 10),
                        o("string", Material.STRING, 16, 4), o("pufferfish", Material.PUFFERFISH, 8, 8)));
        if (profession == Villager.Profession.ARMORER) return new Catalog(
                List.of(o("iron-helmet", Material.IRON_HELMET, 1, 80), o("iron-chestplate", Material.IRON_CHESTPLATE, 1, 150),
                        o("shield", Material.SHIELD, 1, 50), o("diamond-helmet", Material.DIAMOND_HELMET, 1, 500)),
                List.of(o("coal", Material.COAL, 16, 5), o("iron-ingot", Material.IRON_INGOT, 8, 20),
                        o("copper-ingot", Material.COPPER_INGOT, 16, 6), o("gold-ingot", Material.GOLD_INGOT, 4, 20),
                        o("diamond", Material.DIAMOND, 1, 25)));
        if (profession == Villager.Profession.TOOLSMITH) return new Catalog(
                List.of(o("iron-pickaxe", Material.IRON_PICKAXE, 1, 90), o("iron-axe", Material.IRON_AXE, 1, 75),
                        o("diamond-pickaxe", Material.DIAMOND_PICKAXE, 1, 450), o("iron-shovel", Material.IRON_SHOVEL, 1, 40)),
                List.of(o("coal", Material.COAL, 16, 5), o("iron-ingot", Material.IRON_INGOT, 8, 20),
                        o("copper-ingot", Material.COPPER_INGOT, 16, 6), o("gold-ingot", Material.GOLD_INGOT, 4, 20),
                        o("diamond", Material.DIAMOND, 1, 25)));
        if (profession == Villager.Profession.WEAPONSMITH) return new Catalog(
                List.of(o("iron-sword", Material.IRON_SWORD, 1, 60), o("shield", Material.SHIELD, 1, 50),
                        o("diamond-sword", Material.DIAMOND_SWORD, 1, 350), o("iron-axe", Material.IRON_AXE, 1, 75)),
                List.of(o("coal", Material.COAL, 16, 5), o("iron-ingot", Material.IRON_INGOT, 8, 20),
                        o("gold-ingot", Material.GOLD_INGOT, 4, 20), o("diamond", Material.DIAMOND, 1, 25)));
        if (profession == Villager.Profession.MASON) return new Catalog(
                List.of(o("bricks", Material.BRICKS, 16, 20), o("glass", Material.GLASS, 16, 18),
                        o("obsidian", Material.OBSIDIAN, 4, 45), o("quartz-block", Material.QUARTZ_BLOCK, 8, 35)),
                List.of(o("clay-ball", Material.CLAY_BALL, 16, 4), o("brick", Material.BRICK, 16, 6),
                        o("cobblestone", Material.COBBLESTONE, 32, 2), o("stone", Material.STONE, 16, 3)));
        if (profession == Villager.Profession.CLERIC) return new Catalog(
                List.of(o("glowstone", Material.GLOWSTONE, 8, 30), o("blaze-rod", Material.BLAZE_ROD, 4, 80),
                        o("ender-pearl", Material.ENDER_PEARL, 4, 90), o("xp-bottle", Material.EXPERIENCE_BOTTLE, 4, 70)),
                List.of(o("rotten-flesh", Material.ROTTEN_FLESH, 32, 4), o("gold-ingot", Material.GOLD_INGOT, 4, 18),
                        o("blaze-rod", Material.BLAZE_ROD, 2, 25), o("ender-pearl", Material.ENDER_PEARL, 1, 12)));
        if (profession == Villager.Profession.LIBRARIAN) return new Catalog(
                List.of(o("bookshelf", Material.BOOKSHELF, 4, 50), o("paper", Material.PAPER, 16, 10),
                        o("compass", Material.COMPASS, 1, 30), o("ender-pearl", Material.ENDER_PEARL, 4, 90)),
                List.of(o("paper", Material.PAPER, 16, 8), o("book", Material.BOOK, 8, 20),
                        o("ink-sac", Material.INK_SAC, 16, 5), o("glass-pane", Material.GLASS_PANE, 16, 6)));
        if (profession == Villager.Profession.CARTOGRAPHER) return new Catalog(
                List.of(o("map", Material.MAP, 1, 25), o("compass", Material.COMPASS, 1, 30),
                        o("glass-pane", Material.GLASS_PANE, 16, 12)),
                List.of(o("paper", Material.PAPER, 16, 8), o("ink-sac", Material.INK_SAC, 16, 5),
                        o("glass-pane", Material.GLASS_PANE, 16, 6)));
        if (profession == Villager.Profession.BUTCHER) return new Catalog(
                List.of(o("cooked-beef", Material.COOKED_BEEF, 8, 45), o("cooked-porkchop", Material.COOKED_PORKCHOP, 8, 40),
                        o("cooked-chicken", Material.COOKED_CHICKEN, 8, 25), o("cooked-mutton", Material.COOKED_MUTTON, 8, 30)),
                List.of(o("beef", Material.BEEF, 16, 12), o("porkchop", Material.PORKCHOP, 16, 10),
                        o("chicken", Material.CHICKEN, 16, 8), o("mutton", Material.MUTTON, 16, 8),
                        o("rabbit", Material.RABBIT, 16, 8)));
        if (profession == Villager.Profession.FLETCHER) return new Catalog(
                List.of(o("arrows", Material.ARROW, 16, 12), o("bow", Material.BOW, 1, 45),
                        o("crossbow", Material.CROSSBOW, 1, 80)),
                List.of(o("sticks", Material.STICK, 32, 2), o("feather", Material.FEATHER, 16, 5),
                        o("flint", Material.FLINT, 16, 7), o("string", Material.STRING, 16, 5)));
        if (profession == Villager.Profession.SHEPHERD) return new Catalog(
                List.of(o("white-wool", Material.WHITE_WOOL, 16, 25), o("white-banner", Material.WHITE_BANNER, 1, 20),
                        o("red-wool", Material.RED_WOOL, 16, 25), o("blue-wool", Material.BLUE_WOOL, 16, 25)),
                List.of(o("white-wool", Material.WHITE_WOOL, 16, 8), o("black-wool", Material.BLACK_WOOL, 16, 8),
                        o("red-dye", Material.RED_DYE, 16, 3), o("blue-dye", Material.BLUE_DYE, 16, 3)));
        if (profession == Villager.Profession.LEATHERWORKER) return new Catalog(
                List.of(o("saddle", Material.SADDLE, 1, 150), o("leather-chestplate", Material.LEATHER_CHESTPLATE, 1, 60),
                        o("lead", Material.LEAD, 4, 20)),
                List.of(o("leather", Material.LEATHER, 16, 8), o("rabbit-hide", Material.RABBIT_HIDE, 16, 5),
                        o("string", Material.STRING, 16, 4)));
        return new Catalog(
                List.of(o("bread", Material.BREAD, 8, 15), o("iron-ingot", Material.IRON_INGOT, 8, 35),
                        o("torch", Material.TORCH, 16, 10)),
                List.of(o("coal", Material.COAL, 16, 5), o("iron-ingot", Material.IRON_INGOT, 8, 18),
                        o("copper-ingot", Material.COPPER_INGOT, 16, 6)));
    }

    private Offer o(String key, Material material, int amount, int price) {
        return new Offer(key, material, amount, price);
    }

    private void refreshIfDue(Villager.Profession profession) {
        String base = professionPath(profession);
        long now = System.currentTimeMillis();
        long next = data.getLong(base + ".next-restock", now + restockMillis());
        if (now < next) return;
        data.set("stock." + professionKey(profession), null);
        data.set(base + ".next-restock", now + restockMillis());
        save();
    }

    private String professionPath(Villager.Profession profession) {
        return "professions." + professionKey(profession);
    }

    private String professionKey(Villager.Profession profession) {
        return profession.name().toLowerCase(Locale.ROOT);
    }

    private String stockPath(Villager.Profession profession, Mode mode, Offer offer) {
        return "stock." + professionKey(profession) + "." + mode.name().toLowerCase(Locale.ROOT) + "-" + offer.key();
    }

    private String cooldownPath(UUID player, Villager.Profession profession, Mode mode, Offer offer) {
        return "cooldowns." + player + "." + professionKey(profession) + "." + mode.name().toLowerCase(Locale.ROOT) + "-" + offer.key();
    }

    private int stockLimit() {
        return Math.max(1, Math.min(10000, plugin.getConfig().getInt("villager-market.stock-per-offer", 24)));
    }

    private long cooldownMillis() {
        return Math.max(0L, Math.min(86400L, plugin.getConfig().getLong("villager-market.cooldown-seconds", 300L))) * 1000L;
    }

    private long restockMillis() {
        return Math.max(1L, Math.min(10080L, plugin.getConfig().getLong("villager-market.restock-duration-minutes", 120L))) * 60_000L;
    }

    private String timeUntilRestock(Villager.Profession profession) {
        refreshIfDue(profession);
        long remaining = Math.max(0L, data.getLong(professionPath(profession) + ".next-restock", System.currentTimeMillis()) - System.currentTimeMillis());
        return formatDuration((remaining + 999L) / 1000L);
    }

    private String formatDuration(long seconds) {
        seconds = Math.max(0L, seconds);
        long hours = seconds / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        long secs = seconds % 60L;
        if (hours > 0) return hours + "h " + minutes + "m";
        if (minutes > 0) return minutes + "m " + secs + "s";
        return secs + "s";
    }

    private String pretty(Material material) {
        return material.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private String prettyProfession(Villager.Profession profession) {
        String value = profession.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1);
    }

    private String money(double amount) {
        return String.format(Locale.US, "%.0f", amount);
    }

    private void fill(Inventory inv) {
        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler);
    }

    private ItemStack item(Material material, String name, List<String> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private void save() {
        try {
            plugin.getDataFolder().mkdirs();
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save villager-market.yml: " + e.getMessage());
        }
    }
}
