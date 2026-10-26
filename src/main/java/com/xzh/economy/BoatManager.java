package com.xzh.economy;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Boat;
import org.bukkit.entity.EntityType;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityBreedEvent;
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

import java.util.List;
import java.util.Map;

/**
 * Curated XZH boat shop. Boat tier identity is stored as persistent item/entity data,
 * not inferred from a mutable display name.
 */
public final class BoatManager implements Listener {
    private static final String GUI = "XZH TRAVEL";
    private final XZHEconomySystem plugin;
    private final NamespacedKey tierKey;
    private final NamespacedKey chestKey;
    private final NamespacedKey actionKey;
    private final NamespacedKey quantityKey;

    private record BoatOffer(String tier, String name, Material material, Material chestMaterial, double price, double chestPrice, String finish, String branding) { }

    private final List<BoatOffer> offers = List.of(
        new BoatOffer("basic", "Basic Bamboo Raft", Material.BAMBOO_RAFT, Material.BAMBOO_CHEST_RAFT,
            300, 200, "Classic bamboo raft", "XZH Travel"),
        new BoatOffer("aura", "Aura Pale Oak Boat", Material.PALE_OAK_BOAT, Material.PALE_OAK_CHEST_BOAT,
            700, 500, "Pale oak boat", "XZH Travel"),
        new BoatOffer("super", "Super Spruce Boat", Material.SPRUCE_BOAT, Material.SPRUCE_CHEST_BOAT,
            1600, 1300, "Spruce boat", "XZH Travel")
    );

    public BoatManager(XZHEconomySystem plugin) {
        this.plugin = plugin;
        tierKey = new NamespacedKey(plugin, "xzh_boat_tier");
        chestKey = new NamespacedKey(plugin, "xzh_boat_chest");
        actionKey = new NamespacedKey(plugin, "xzh_boat_action");
        quantityKey = new NamespacedKey(plugin, "xzh_boat_quantity");
        Bukkit.getPluginManager().registerEvents(this, plugin);
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
                ChatColor.GRAY + "Normal: " + ChatColor.YELLOW + money(offer.price()) + " coins",
                ChatColor.GRAY + "With chest: " + ChatColor.YELLOW + money(offer.chestPrice()) + " coins",
                ChatColor.GREEN + "Click to choose version"
            ));
            meta.getPersistentDataContainer().set(tierKey, PersistentDataType.STRING, offer.tier());
            meta.getPersistentDataContainer().set(chestKey, PersistentDataType.BYTE, (byte) 0);
            icon.setItemMeta(meta);
            inv.setItem(slots[i], icon);
        }
        // Horse and donkey spawn eggs are intentionally removed from the Travel menu.
        inv.setItem(22, travelEgg(Material.NAUTILUS_SPAWN_EGG, "nautilus", "Nautilus Spawn Egg"));
        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta im = info.getItemMeta();
        im.setDisplayName(ChatColor.WHITE + "Travel");
        im.setLore(List.of(
            ChatColor.GRAY + "Premium XZH boats and a Nautilus travel companion.",
            ChatColor.GRAY + "Nautilus Spawn Egg: " + ChatColor.YELLOW + "2,000 coins",
            ChatColor.GRAY + "Natural horse/donkey and nautilus spawning are disabled."
        ));
        info.setItemMeta(im);
        inv.setItem(24, info);
        inv.setItem(26, backItem());
        player.openInventory(inv);
    }

    @EventHandler
    public void blockNaturalHorseDonkeySpawn(CreatureSpawnEvent event) {
        if (!plugin.getConfig().getBoolean("travel.disable-natural-horse-donkey-spawning", true)) return;
        EntityType type = event.getEntityType();
        if ((type == EntityType.HORSE || type == EntityType.DONKEY)
                && event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.NATURAL) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void blockNaturalNautilusSpawn(CreatureSpawnEvent event) {
        if (event.getEntityType() != EntityType.NAUTILUS) return;
        CreatureSpawnEvent.SpawnReason reason = event.getSpawnReason();
        if (reason == CreatureSpawnEvent.SpawnReason.NATURAL || reason == CreatureSpawnEvent.SpawnReason.CHUNK_GEN) event.setCancelled(true);
    }

    @EventHandler
    public void blockHorseDonkeyBreeding(EntityBreedEvent event) {
        if (!plugin.getConfig().getBoolean("travel.disable-horse-donkey-breeding", true)) return;
        EntityType mother = event.getMother().getType();
        EntityType father = event.getFather().getType();
        if (mother == EntityType.HORSE || mother == EntityType.DONKEY
                || father == EntityType.HORSE || father == EntityType.DONKEY) {
            event.setCancelled(true);
        }
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
        if ("back".equals(tier)) { plugin.getShopManager().openMain(player); return; }
        if ("horse".equals(tier) || "donkey".equals(tier) || "nautilus".equals(tier)) {
            openTravelQuantity(player, tier, 1);
            return;
        }
        BoatOffer offer = find(tier);
        if (offer != null) openBoatOptions(player, offer);
    }

    private void openBoatOptions(Player player, BoatOffer offer) {
        Inventory confirm = Bukkit.createInventory(null, 27, "Confirm XZH Boat");
        confirm.setItem(11, choiceItem(offer, false));
        confirm.setItem(13, choiceItem(offer, true));
        confirm.setItem(26, backItem());
        player.openInventory(confirm);
    }

    @EventHandler
    public void onConfirmClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = ChatColor.stripColor(event.getView().getTitle());
        if (!"Confirm XZH Boat".equals(title) && !"Confirm Travel Item".equals(title)
                && !"Choose boat quantity".equals(title) && !"Choose travel quantity".equals(title)) return;
        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        ItemMeta meta = clicked.getItemMeta();
        String tier = meta.getPersistentDataContainer().get(tierKey, PersistentDataType.STRING);
        if (tier == null) return;

        if (title.equals("Confirm Travel Item")) {
            if ("back".equals(tier)) { open(player); return; }
            if ("horse".equals(tier) || "donkey".equals(tier) || "nautilus".equals(tier)) openTravelQuantity(player, tier, 1);
            return;
        }
        if (title.equals("Confirm XZH Boat")) {
            if ("back".equals(tier)) { open(player); return; }
            BoatOffer offer = find(tier);
            if (offer == null) return;
            Byte chestByte = meta.getPersistentDataContainer().get(chestKey, PersistentDataType.BYTE);
            openBoatQuantity(player, offer, chestByte != null && chestByte == (byte) 1, 1);
            return;
        }

        String action = meta.getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
        Integer quantity = meta.getPersistentDataContainer().get(quantityKey, PersistentDataType.INTEGER);
        if (action == null || quantity == null) return;
        boolean boatPage = title.equals("Choose boat quantity");
        if (action.equals("quantity-back")) {
            if (boatPage) {
                BoatOffer offer = find(tier);
                if (offer != null) openBoatOptions(player, offer);
            } else open(player);
            return;
        }
        if (action.equals("quantity-minus") || action.equals("quantity-plus")) {
            int next = Math.max(1, Math.min(64, quantity + (action.equals("quantity-plus") ? 1 : -1)));
            if (boatPage) {
                BoatOffer offer = find(tier);
                if (offer != null) {
                    Byte chestByte = meta.getPersistentDataContainer().get(chestKey, PersistentDataType.BYTE);
                    openBoatQuantity(player, offer, chestByte != null && chestByte == (byte) 1, next);
                }
            } else openTravelQuantity(player, tier, next);
            return;
        }
        if (!action.equals("quantity-buy")) return;
        if (boatPage) {
            BoatOffer offer = find(tier);
            if (offer != null) {
                Byte chestByte = meta.getPersistentDataContainer().get(chestKey, PersistentDataType.BYTE);
                purchaseBoats(player, offer, chestByte != null && chestByte == (byte) 1, quantity);
            }
        } else purchaseTravelEgg(player, tier, quantity);
    }

    private void openBoatQuantity(Player player, BoatOffer offer, boolean chest, int requestedQuantity) {
        int quantity = Math.max(1, Math.min(64, requestedQuantity));
        double unitPrice = chest ? offer.chestPrice() : offer.price();
        Inventory inv = Bukkit.createInventory(null, 27, "Choose boat quantity");
        ItemStack summary = createBoatItem(offer, chest);
        summary.setAmount(Math.min(quantity, summary.getMaxStackSize()));
        ItemMeta sm = summary.getItemMeta();
        sm.setDisplayName(ChatColor.YELLOW + "Selected: " + quantity + " × " + offer.name() + (chest ? " + Chest" : ""));
        sm.setLore(List.of(ChatColor.WHITE + "Unit price: " + ChatColor.YELLOW + money(unitPrice) + " coins",
                ChatColor.WHITE + "Total: " + ChatColor.GREEN + money(unitPrice * quantity) + " coins"));
        summary.setItemMeta(sm);
        inv.setItem(13, summary);
        inv.setItem(10, quantityButton(Material.RED_CONCRETE, "−1", offer.tier(), chest, quantity, "quantity-minus"));
        inv.setItem(16, quantityButton(Material.LIME_CONCRETE, "+1", offer.tier(), chest, quantity, "quantity-plus"));
        inv.setItem(22, quantityButton(Material.SLIME_BALL, "Buy " + quantity, offer.tier(), chest, quantity, "quantity-buy",
                List.of(ChatColor.WHITE + "Total: " + ChatColor.GREEN + money(unitPrice * quantity) + " coins")));
        inv.setItem(26, quantityButton(Material.ARROW, "Back", offer.tier(), chest, quantity, "quantity-back"));
        player.openInventory(inv);
    }

    private void openTravelQuantity(Player player, String tier, int requestedQuantity) {
        int quantity = Math.max(1, Math.min(64, requestedQuantity));
        String name = travelName(tier);
        Material material = travelMaterial(tier);
        double unitPrice = travelPrice(tier);
        Inventory inv = Bukkit.createInventory(null, 27, "Choose travel quantity");
        ItemStack summary = travelEgg(material, tier, name);
        summary.setAmount(Math.min(quantity, summary.getMaxStackSize()));
        ItemMeta sm = summary.getItemMeta();
        sm.setDisplayName(ChatColor.YELLOW + "Selected: " + quantity + " × " + name);
        sm.setLore(List.of(ChatColor.WHITE + "Unit price: " + ChatColor.YELLOW + money(unitPrice) + " coins",
                ChatColor.WHITE + "Total: " + ChatColor.GREEN + money(unitPrice * quantity) + " coins"));
        summary.setItemMeta(sm);
        inv.setItem(13, summary);
        inv.setItem(10, quantityButton(Material.RED_CONCRETE, "−1", tier, false, quantity, "quantity-minus"));
        inv.setItem(16, quantityButton(Material.LIME_CONCRETE, "+1", tier, false, quantity, "quantity-plus"));
        inv.setItem(22, quantityButton(Material.SLIME_BALL, "Buy " + quantity, tier, false, quantity, "quantity-buy",
                List.of(ChatColor.WHITE + "Total: " + ChatColor.GREEN + money(unitPrice * quantity) + " coins")));
        inv.setItem(26, quantityButton(Material.ARROW, "Back", tier, false, quantity, "quantity-back"));
        player.openInventory(inv);
    }

    private ItemStack quantityButton(Material material, String name, String tier, boolean chest, int quantity, String action) {
        return quantityButton(material, name, tier, chest, quantity, action, List.of(ChatColor.GRAY + "Selected quantity: " + quantity));
    }

    private ItemStack quantityButton(Material material, String name, String tier, boolean chest, int quantity, String action, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.WHITE + name);
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(tierKey, PersistentDataType.STRING, tier);
        meta.getPersistentDataContainer().set(chestKey, PersistentDataType.BYTE, (byte) (chest ? 1 : 0));
        meta.getPersistentDataContainer().set(quantityKey, PersistentDataType.INTEGER, quantity);
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
        item.setItemMeta(meta);
        return item;
    }

    private void purchaseBoats(Player player, BoatOffer offer, boolean chest, int quantity) {
        quantity = Math.max(1, Math.min(64, quantity));
        double unitPrice = chest ? offer.chestPrice() : offer.price();
        double total = unitPrice * quantity;
        if (plugin.getLoanManager() != null && plugin.getLoanManager().isShopPurchaseRestricted(player.getUniqueId())) {
            plugin.tellPublic(player, "Your loan is overdue. Shop purchases are locked until you repay it.");
            player.closeInventory();
            return;
        }
        if (plugin.getCombinedBalance(player.getUniqueId()) + 1e-9 < total) {
            plugin.tellPublic(player, "Not enough coins for this purchase.");
            return;
        }
        ItemStack boat = createBoatItem(offer, chest);
        boat.setAmount(quantity);
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(boat);
        int notGiven = leftovers.values().stream().mapToInt(ItemStack::getAmount).sum();
        if (notGiven > 0) {
            int delivered = quantity - notGiven;
            if (delivered > 0) {
                ItemStack rollback = createBoatItem(offer, chest);
                rollback.setAmount(delivered);
                player.getInventory().removeItem(rollback);
            }
            plugin.tellPublic(player, "Inventory full; purchase cancelled. Make room and try again.");
            return;
        }
        if (!plugin.spendAvailableFunds(player.getUniqueId(), total)) {
            ItemStack rollback = createBoatItem(offer, chest);
            rollback.setAmount(quantity);
            player.getInventory().removeItem(rollback);
            plugin.tellPublic(player, "Purchase cancelled because your available funds changed.");
            return;
        }
        plugin.tellPublic(player, "Purchased " + quantity + " × " + offer.name() + (chest ? " with chest" : "")
                + " for " + money(total) + " coins.");
        player.closeInventory();
    }

    private ItemStack travelEgg(Material material, String tier, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + name);
        meta.setLore(List.of(
            ChatColor.WHITE + "Price: " + ChatColor.YELLOW + money(travelPrice(tier)) + " coins",
            ChatColor.GRAY + ("nautilus".equals(tier) ? "Only available from the XZH Travel shop." : "Feeding and taming are allowed; breeding is disabled."),
            ChatColor.GREEN + "Click to review purchase"
        ));
        meta.getPersistentDataContainer().set(tierKey, PersistentDataType.STRING, tier);
        item.setItemMeta(meta);
        return item;
    }

    private double travelPrice(String tier) { return "nautilus".equals(tier) ? 2000.0 : 2500.0; }

    private Material travelMaterial(String tier) {
        return switch (tier) {
            case "horse" -> Material.HORSE_SPAWN_EGG;
            case "donkey" -> Material.DONKEY_SPAWN_EGG;
            case "nautilus" -> Material.NAUTILUS_SPAWN_EGG;
            default -> Material.AIR;
        };
    }

    private String travelName(String tier) {
        return switch (tier) {
            case "horse" -> "Horse Spawn Egg";
            case "donkey" -> "Donkey Spawn Egg";
            case "nautilus" -> "Nautilus Spawn Egg";
            default -> "Travel Spawn Egg";
        };
    }

    private ItemStack backItem() {
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta meta = back.getItemMeta();
        meta.setDisplayName(ChatColor.GRAY + "Back");
        meta.getPersistentDataContainer().set(tierKey, PersistentDataType.STRING, "back");
        back.setItemMeta(meta);
        return back;
    }

    private void purchaseTravelEgg(Player player, String tier, int requestedQuantity) {
        int quantity = Math.max(1, Math.min(64, requestedQuantity));
        double total = travelPrice(tier) * quantity;
        if (plugin.getLoanManager() != null && plugin.getLoanManager().isShopPurchaseRestricted(player.getUniqueId())) {
            plugin.tellPublic(player, "Your loan is overdue. Shop purchases are locked until you repay it.");
            player.closeInventory();
            return;
        }
        if (plugin.getCombinedBalance(player.getUniqueId()) + 1e-9 < total) {
            plugin.tellPublic(player, "Not enough coins for these spawn eggs.");
            return;
        }
        Material material = travelMaterial(tier);
        String name = travelName(tier);
        ItemStack egg = travelEgg(material, tier, name);
        egg.setAmount(quantity);
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(egg);
        int notGiven = leftovers.values().stream().mapToInt(ItemStack::getAmount).sum();
        if (notGiven > 0) {
            int delivered = quantity - notGiven;
            if (delivered > 0) {
                ItemStack rollback = travelEgg(material, tier, name);
                rollback.setAmount(delivered);
                player.getInventory().removeItem(rollback);
            }
            plugin.tellPublic(player, "Inventory full; purchase cancelled. Make room and try again.");
            return;
        }
        if (!plugin.spendAvailableFunds(player.getUniqueId(), total)) {
            ItemStack rollback = travelEgg(material, tier, name);
            rollback.setAmount(quantity);
            player.getInventory().removeItem(rollback);
            plugin.tellPublic(player, "Purchase cancelled because your available funds changed.");
            return;
        }
        plugin.tellPublic(player, "Purchased " + quantity + " × " + name + " for " + money(total) + " coins.");
        player.closeInventory();
    }

    private ItemStack choiceItem(BoatOffer offer, boolean chest) {
        ItemStack item = createBoatItem(offer, chest);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName((chest ? ChatColor.AQUA : ChatColor.GREEN) + offer.name() + (chest ? " + Chest" : ""));
        meta.setLore(List.of(ChatColor.WHITE + "Price: " + ChatColor.YELLOW + money(chest ? offer.chestPrice() : offer.price()) + " coins", ChatColor.YELLOW + "Click to purchase"));
        meta.getPersistentDataContainer().set(tierKey, PersistentDataType.STRING, offer.tier());
        meta.getPersistentDataContainer().set(chestKey, PersistentDataType.BYTE, (byte) (chest ? 1 : 0));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createBoatItem(BoatOffer offer, boolean chest) {
        ItemStack item = new ItemStack(chest ? offer.chestMaterial() : offer.material());
        ItemMeta meta = item.getItemMeta();
        // CustomModelData selects the matching XZH boat style in the optional resource pack.
        meta.setCustomModelData(switch (offer.tier()) {
            case "aura" -> 1002;
            case "super" -> 1003;
            default -> 1001;
        });
        meta.getPersistentDataContainer().set(tierKey, PersistentDataType.STRING, offer.tier());
        meta.getPersistentDataContainer().set(chestKey, PersistentDataType.BYTE, (byte) (chest ? 1 : 0));
        meta.setDisplayName(ChatColor.WHITE + "XZH " + offer.name() + (chest ? " with Chest" : ""));
        meta.setLore(List.of(ChatColor.GRAY + offer.finish(), ChatColor.GRAY + offer.branding()));
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
