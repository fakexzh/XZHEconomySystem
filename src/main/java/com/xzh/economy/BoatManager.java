package com.xzh.economy;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Map;

/**
 * Curated XZH boat shop. Boat tier identity is stored as persistent item/entity data,
 * not inferred from a mutable display name.
 */
public final class BoatManager implements Listener {
    private static final String GUI = "XZH PREMIUM BOATS";
    private final XZHEconomySystem plugin;
    private final NamespacedKey tierKey;
    private final NamespacedKey chestKey;
    private final NamespacedKey entityTierKey;

    private record BoatOffer(String tier, String name, Material material, Material chestMaterial,
                             double price, double chestPrice, String finish, String branding, String speed) { }

    private final List<BoatOffer> offers = List.of(
        new BoatOffer("basic", "Basic Boat", Material.OAK_BOAT, Material.OAK_CHEST_BOAT,
            200, 300, "Natural wooden raft", "Black XZH", "Normal speed"),
        new BoatOffer("aura", "Aura Boat", Material.BIRCH_BOAT, Material.BIRCH_CHEST_BOAT,
            700, 999, "Premium white finish", "Black XZH", "Mid speed"),
        new BoatOffer("super", "Super Boat", Material.DARK_OAK_BOAT, Material.DARK_OAK_CHEST_BOAT,
            1500, 2100, "Sleek black speedboat", "White XZH", "High speed")
    );

    public BoatManager(XZHEconomySystem plugin) {
        this.plugin = plugin;
        tierKey = new NamespacedKey(plugin, "xzh_boat_tier");
        chestKey = new NamespacedKey(plugin, "xzh_boat_chest");
        entityTierKey = new NamespacedKey(plugin, "xzh_boat_entity_tier");
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, this::applyBoatSpeeds, 1L, 1L);
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, GUI);
        int[] slots = {10, 13, 16};
        for (int i = 0; i < offers.size(); i++) {
            BoatOffer offer = offers.get(i);
            ItemStack icon = createBoatItem(offer, false);
            ItemMeta meta = icon.getItemMeta();
            meta.setDisplayName(ChatColor.GOLD + offer.name());
            meta.setLore(List.of(
                ChatColor.WHITE + offer.finish(),
                ChatColor.WHITE + "Branding: " + ChatColor.YELLOW + offer.branding(),
                ChatColor.AQUA + offer.speed(),
                ChatColor.GRAY + "Normal: " + ChatColor.YELLOW + money(offer.price()) + " coins",
                ChatColor.GRAY + "With chest: " + ChatColor.YELLOW + money(offer.chestPrice()) + " coins",
                ChatColor.GREEN + "Click to choose version"
            ));
            meta.getPersistentDataContainer().set(tierKey, PersistentDataType.STRING, offer.tier());
            meta.getPersistentDataContainer().set(chestKey, PersistentDataType.BYTE, (byte) 0);
            icon.setItemMeta(meta);
            inv.setItem(slots[i], icon);
        }
        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta im = info.getItemMeta();
        im.setDisplayName(ChatColor.WHITE + "Only 3 XZH boat tiers");
        im.setLore(List.of(ChatColor.GRAY + "Boat crafting is disabled.", ChatColor.GRAY + "Choose a tier, then a chest version."));
        info.setItemMeta(im);
        inv.setItem(22, info);
        player.openInventory(inv);
    }

    @EventHandler
    public void onCraft(PrepareItemCraftEvent event) {
        if (event.getRecipe() == null) return;
        Material result = event.getRecipe().getResult().getType();
        if (isBoatMaterial(result)) event.getInventory().setResult(new ItemStack(Material.AIR));
    }

    @EventHandler
    public void onBoatChoice(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!GUI.equals(ChatColor.stripColor(event.getView().getTitle()))) return;
        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        String tier = clicked.getItemMeta().getPersistentDataContainer().get(tierKey, PersistentDataType.STRING);
        if (tier == null) return;
        BoatOffer offer = find(tier);
        if (offer == null) return;
        Inventory confirm = Bukkit.createInventory(null, 27, "Confirm XZH Boat");
        confirm.setItem(11, choiceItem(offer, false));
        confirm.setItem(13, choiceItem(offer, true));
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta bm = back.getItemMeta();
        bm.setDisplayName(ChatColor.GRAY + "Back");
        bm.getPersistentDataContainer().set(tierKey, PersistentDataType.STRING, "back");
        back.setItemMeta(bm);
        confirm.setItem(26, back);
        player.openInventory(confirm);
    }

    @EventHandler
    public void onConfirmClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!"Confirm XZH Boat".equals(ChatColor.stripColor(event.getView().getTitle()))) return;
        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        ItemMeta meta = clicked.getItemMeta();
        String tier = meta.getPersistentDataContainer().get(tierKey, PersistentDataType.STRING);
        if ("back".equals(tier)) { open(player); return; }
        if (tier == null) return;
        BoatOffer offer = find(tier);
        if (offer == null) return;
        Byte chestByte = meta.getPersistentDataContainer().get(chestKey, PersistentDataType.BYTE);
        boolean chest = chestByte != null && chestByte == (byte) 1;
        double price = chest ? offer.chestPrice() : offer.price();
        if (plugin.getLoanManager() != null && plugin.getLoanManager().isShopPurchaseRestricted(player.getUniqueId())) {
            plugin.tellPublic(player, "Your loan is overdue. Shop purchases are locked until you repay it.");
            player.closeInventory();
            return;
        }
        if (plugin.getCombinedBalance(player.getUniqueId()) + 1e-9 < price
                || !plugin.spendAvailableFunds(player.getUniqueId(), price)) {
            plugin.tellPublic(player, "Not enough coins for this boat.");
            player.closeInventory();
            return;
        }
        ItemStack boat = createBoatItem(offer, chest);
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(boat);
        if (!leftovers.isEmpty()) {
            plugin.setBalance(player.getUniqueId(), plugin.getBalance(player.getUniqueId()) + price);
            plugin.tellPublic(player, "Inventory full; purchase cancelled and refunded.");
        } else {
            plugin.tellPublic(player, "Purchased " + offer.name() + (chest ? " with chest" : "")
                + " for " + money(price) + " coins.");
        }
        player.closeInventory();
    }

    @EventHandler
    public void onPlace(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack item = event.getItem();
        if (item == null || !item.hasItemMeta()) return;
        String tier = item.getItemMeta().getPersistentDataContainer().get(tierKey, PersistentDataType.STRING);
        if (tier == null || find(tier) == null) return;
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Entity entity : player.getNearbyEntities(4, 4, 4)) {
                if (!(entity instanceof Boat boat)) continue;
                if (boat.getPassengers().contains(player) || boat.getLocation().distanceSquared(player.getLocation()) < 9) {
                    boat.getPersistentDataContainer().set(entityTierKey, PersistentDataType.STRING, tier);
                    boat.addScoreboardTag("xzh_boat_" + tier);
                    break;
                }
            }
        });
    }

    private void applyBoatSpeeds() {
        for (var world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (!(entity instanceof Boat boat)) continue;
                String tier = boat.getPersistentDataContainer().get(entityTierKey, PersistentDataType.STRING);
                if (tier == null) continue;
                double cap = switch (tier) { case "aura" -> 0.34; case "super" -> 0.46; default -> 0.25; };
                Vector v = boat.getVelocity();
                double horizontal = Math.sqrt(v.getX() * v.getX() + v.getZ() * v.getZ());
                if (horizontal > 0.01 && horizontal < cap) {
                    double scale = cap / horizontal;
                    boat.setVelocity(new Vector(v.getX() * scale, v.getY(), v.getZ() * scale));
                }
            }
        }
    }

    private ItemStack choiceItem(BoatOffer offer, boolean chest) {
        ItemStack item = createBoatItem(offer, chest);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName((chest ? ChatColor.AQUA : ChatColor.GREEN) + offer.name() + (chest ? " + Chest" : ""));
        meta.setLore(List.of(ChatColor.WHITE + "Price: " + ChatColor.YELLOW + money(chest ? offer.chestPrice() : offer.price()) + " coins",
            ChatColor.GRAY + offer.speed(), ChatColor.YELLOW + "Click to purchase"));
        meta.getPersistentDataContainer().set(tierKey, PersistentDataType.STRING, offer.tier());
        meta.getPersistentDataContainer().set(chestKey, PersistentDataType.BYTE, (byte) (chest ? 1 : 0));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createBoatItem(BoatOffer offer, boolean chest) {
        ItemStack item = new ItemStack(chest ? offer.chestMaterial() : offer.material());
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(tierKey, PersistentDataType.STRING, offer.tier());
        meta.getPersistentDataContainer().set(chestKey, PersistentDataType.BYTE, (byte) (chest ? 1 : 0));
        meta.setDisplayName(ChatColor.WHITE + "XZH " + offer.name() + (chest ? " with Chest" : ""));
        meta.setLore(List.of(ChatColor.GRAY + offer.finish(), ChatColor.GRAY + offer.branding(), ChatColor.GRAY + offer.speed()));
        item.setItemMeta(meta);
        return item;
    }

    private BoatOffer find(String tier) {
        return offers.stream().filter(o -> o.tier().equalsIgnoreCase(tier)).findFirst().orElse(null);
    }

    private boolean isBoatMaterial(Material m) {
        return m.name().endsWith("_BOAT") || m.name().endsWith("_CHEST_BOAT") || m == Material.BAMBOO_RAFT || m == Material.BAMBOO_CHEST_RAFT;
    }

    private String money(double value) {
        return String.format(java.util.Locale.US, "%,.0f", value);
    }
}
