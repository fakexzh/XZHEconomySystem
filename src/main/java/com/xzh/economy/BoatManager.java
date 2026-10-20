package com.xzh.economy;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
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
import org.bukkit.util.Vector;

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
    private final NamespacedKey entityTierKey;

    private record BoatOffer(String tier, String name, Material material, Material chestMaterial,
                             double price, double chestPrice, String finish, String branding, String speed) { }

    private final List<BoatOffer> offers = List.of(
        new BoatOffer("basic", "Basic Boat", Material.OAK_BOAT, Material.OAK_CHEST_BOAT,
            200, 300, "Natural wooden raft", "Black XZH", "Vanilla speed (1x)"),
        new BoatOffer("aura", "Aura Boat", Material.BIRCH_BOAT, Material.BIRCH_CHEST_BOAT,
            700, 999, "Premium white finish", "Black XZH", "2x vanilla speed"),
        new BoatOffer("super", "Super Boat", Material.DARK_OAK_BOAT, Material.DARK_OAK_CHEST_BOAT,
            1500, 2100, "Sleek black speedboat", "White XZH", "3x vanilla speed")
    );

    public BoatManager(XZHEconomySystem plugin) {
        this.plugin = plugin;
        tierKey = new NamespacedKey(plugin, "xzh_boat_tier");
        chestKey = new NamespacedKey(plugin, "xzh_boat_chest");
        entityTierKey = new NamespacedKey(plugin, "xzh_boat_entity_tier");
        Bukkit.getPluginManager().registerEvents(this, plugin);
        // Re-apply settings periodically so the selected speed survives entity updates/reloads.
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
        inv.setItem(11, travelEgg(Material.HORSE_SPAWN_EGG, "horse", "Horse Spawn Egg"));
        inv.setItem(15, travelEgg(Material.DONKEY_SPAWN_EGG, "donkey", "Donkey Spawn Egg"));
        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta im = info.getItemMeta();
        im.setDisplayName(ChatColor.WHITE + "Travel");
        im.setLore(List.of(
            ChatColor.GRAY + "Premium boats and land companions.",
            ChatColor.GRAY + "Horse and donkey spawn eggs: " + ChatColor.YELLOW + "2,500 coins each",
            ChatColor.GRAY + "Natural horse/donkey spawning and breeding are disabled."
        ));
        info.setItemMeta(im);
        inv.setItem(22, info);
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
        if ("horse".equals(tier) || "donkey".equals(tier)) {
            Inventory confirm = Bukkit.createInventory(null, 27, "Confirm Travel Item");
            Material egg = "horse".equals(tier) ? Material.HORSE_SPAWN_EGG : Material.DONKEY_SPAWN_EGG;
            String name = ("horse".equals(tier) ? "Horse" : "Donkey") + " Spawn Egg";
            ItemStack preview = travelEgg(egg, "preview", name);
            ItemMeta previewMeta = preview.getItemMeta();
            previewMeta.setLore(List.of(ChatColor.WHITE + "Price: " + ChatColor.YELLOW + "2,500 coins"));
            preview.setItemMeta(previewMeta);
            confirm.setItem(13, preview);
            ItemStack yes = new ItemStack(Material.LIME_CONCRETE);
            ItemMeta yesMeta = yes.getItemMeta();
            yesMeta.setDisplayName(ChatColor.GREEN + "Confirm purchase");
            yesMeta.setLore(List.of(ChatColor.WHITE + "Price: " + ChatColor.YELLOW + "2,500 coins"));
            yesMeta.getPersistentDataContainer().set(tierKey, PersistentDataType.STRING, tier);
            yes.setItemMeta(yesMeta);
            confirm.setItem(11, yes);
            ItemStack no = new ItemStack(Material.RED_CONCRETE);
            ItemMeta noMeta = no.getItemMeta();
            noMeta.setDisplayName(ChatColor.RED + "Cancel");
            noMeta.getPersistentDataContainer().set(tierKey, PersistentDataType.STRING, "back");
            no.setItemMeta(noMeta);
            confirm.setItem(15, no);
            player.openInventory(confirm);
            return;
        }
        BoatOffer offer = find(tier);
        if (offer == null) return;
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
        if (!"Confirm XZH Boat".equals(title) && !"Confirm Travel Item".equals(title)) return;
        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        ItemMeta meta = clicked.getItemMeta();
        String tier = meta.getPersistentDataContainer().get(tierKey, PersistentDataType.STRING);
        if ("back".equals(tier)) { open(player); return; }
        if (tier == null) return;
        if ("horse".equals(tier) || "donkey".equals(tier)) {
            purchaseTravelEgg(player, tier);
            return;
        }
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
        // Boat placement happens as part of the interaction; inspect the nearby entity next tick.
        Bukkit.getScheduler().runTask(plugin, () -> {
            Boat candidate = null;
            double nearest = Double.MAX_VALUE;
            for (Entity entity : player.getNearbyEntities(5, 4, 5)) {
                if (!(entity instanceof Boat boat) || boat.getPersistentDataContainer().has(entityTierKey, PersistentDataType.STRING)) continue;
                if (!matchesBoatMaterial(boat, item.getType())) continue;
                double distance = boat.getLocation().distanceSquared(player.getLocation());
                if (distance < nearest) {
                    nearest = distance;
                    candidate = boat;
                }
            }
            if (candidate != null) {
                candidate.getPersistentDataContainer().set(entityTierKey, PersistentDataType.STRING, tier);
                candidate.addScoreboardTag("xzh_boat_" + tier);
                applyBoatSpeed(candidate, tier);
            }
        });
    }

    private boolean matchesBoatMaterial(Boat boat, Material itemMaterial) {
        String materialName = itemMaterial.name();
        String wood = materialName.replace("_CHEST_BOAT", "").replace("_BOAT", "");
        return boat.getBoatType().name().equals(wood);
    }

    private void applyBoatSpeeds() {
        for (var world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (!(entity instanceof Boat boat)) continue;
                String tier = boat.getPersistentDataContainer().get(entityTierKey, PersistentDataType.STRING);
                if (tier != null) applyBoatSpeed(boat, tier);
            }
        }
    }

    private void applyBoatSpeed(Boat boat, String tier) {
        // setMaxSpeed alone does not make vanilla boats move faster on Paper.
        // Keep the vanilla cap for Basic and actively boost horizontal velocity
        // for premium tiers while a player is riding the boat in water.
        double maxSpeed = switch (tier) {
            case "aura" -> 0.80;
            case "super" -> 1.20;
            default -> 0.40;
        };
        boat.setMaxSpeed(maxSpeed);
        if (tier.equals("basic") || boat.getPassengers().isEmpty() || !boat.isInWater()) return;

        Vector velocity = boat.getVelocity();
        double horizontal = Math.sqrt(velocity.getX() * velocity.getX() + velocity.getZ() * velocity.getZ());
        if (horizontal < 0.015) return;

        double multiplier = tier.equals("super") ? 1.22 : 1.10;
        double boosted = Math.min(maxSpeed, horizontal * multiplier);
        double scale = boosted / horizontal;
        boat.setVelocity(new Vector(velocity.getX() * scale, velocity.getY(), velocity.getZ() * scale));
    }

    private ItemStack travelEgg(Material material, String tier, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + name);
        meta.setLore(List.of(
            ChatColor.WHITE + "Price: " + ChatColor.YELLOW + "2,500 coins",
            ChatColor.GRAY + "Feeding and taming are allowed; breeding is disabled.",
            ChatColor.GREEN + "Click to review purchase"
        ));
        meta.getPersistentDataContainer().set(tierKey, PersistentDataType.STRING, tier);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack backItem() {
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta meta = back.getItemMeta();
        meta.setDisplayName(ChatColor.GRAY + "Back");
        meta.getPersistentDataContainer().set(tierKey, PersistentDataType.STRING, "back");
        back.setItemMeta(meta);
        return back;
    }

    private void purchaseTravelEgg(Player player, String tier) {
        double price = 2500.0;
        if (plugin.getLoanManager() != null && plugin.getLoanManager().isShopPurchaseRestricted(player.getUniqueId())) {
            plugin.tellPublic(player, "Your loan is overdue. Shop purchases are locked until you repay it.");
            player.closeInventory();
            return;
        }
        if (plugin.getCombinedBalance(player.getUniqueId()) + 1e-9 < price
                || !plugin.spendAvailableFunds(player.getUniqueId(), price)) {
            plugin.tellPublic(player, "Not enough coins for this spawn egg.");
            player.closeInventory();
            return;
        }
        Material material = "horse".equals(tier) ? Material.HORSE_SPAWN_EGG : Material.DONKEY_SPAWN_EGG;
        String name = ("horse".equals(tier) ? "Horse" : "Donkey") + " Spawn Egg";
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(travelEgg(material, tier, name));
        if (!leftovers.isEmpty()) {
            plugin.setBalance(player.getUniqueId(), plugin.getBalance(player.getUniqueId()) + price);
            plugin.tellPublic(player, "Inventory full; purchase cancelled and refunded.");
        } else {
            plugin.tellPublic(player, "Purchased " + name + " for " + money(price) + " coins.");
        }
        player.closeInventory();
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
        // CustomModelData selects the matching premium model in the optional XZH resource pack.
        meta.setCustomModelData(switch (offer.tier()) {
            case "aura" -> 1002;
            case "super" -> 1003;
            default -> 1001;
        });
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
