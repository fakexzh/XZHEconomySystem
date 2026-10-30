package com.xzh.economy;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.RayTraceResult;

import java.util.*;

public final class PetManager implements Listener {
    private static final String MENU_TITLE = ChatColor.DARK_GREEN + "XZH SHOP: Baby Pets";
    private final XZHEconomySystem plugin;
    private final NamespacedKey ownerKey;
    private final NamespacedKey petKey;
    private final NamespacedKey variantKey;
    private final NamespacedKey sittingKey;
    private final Map<UUID, UUID> following = new HashMap<>();
    private final Map<String, PetOption> options = new LinkedHashMap<>();
    private final int followTask;

    private record PetOption(String id, String display, EntityType type, String variant, int price, Material icon) {}

    public PetManager(XZHEconomySystem plugin) {
        this.plugin = plugin;
        ownerKey = new NamespacedKey(plugin, "pet_owner");
        petKey = new NamespacedKey(plugin, "xzh_baby_pet");
        variantKey = new NamespacedKey(plugin, "pet_variant");
        sittingKey = new NamespacedKey(plugin, "pet_sitting");
        add("panda-normal", "Panda - Normal", EntityType.PANDA, "NORMAL", 3000, Material.PANDA_SPAWN_EGG);
        add("panda-lazy", "Panda - Lazy", EntityType.PANDA, "LAZY", 4000, Material.PANDA_SPAWN_EGG);
        add("panda-worried", "Panda - Worried", EntityType.PANDA, "WORRIED", 4000, Material.PANDA_SPAWN_EGG);
        add("panda-playful", "Panda - Playful", EntityType.PANDA, "PLAYFUL", 4000, Material.PANDA_SPAWN_EGG);
        add("panda-brown", "Panda - Brown", EntityType.PANDA, "BROWN", 6000, Material.PANDA_SPAWN_EGG);
        add("panda-weak", "Panda - Weak", EntityType.PANDA, "WEAK", 6000, Material.PANDA_SPAWN_EGG);
        add("panda-aggressive", "Panda - Aggressive", EntityType.PANDA, "AGGRESSIVE", 6000, Material.PANDA_SPAWN_EGG);
        add("fox-red", "Fox - Red", EntityType.FOX, "RED", 2000, Material.FOX_SPAWN_EGG);
        add("fox-snow", "Fox - Snow", EntityType.FOX, "SNOW", 3500, Material.FOX_SPAWN_EGG);
        add("pig-pink", "Pig - Pink", EntityType.PIG, "TEMPERATE", 1500, Material.PIG_SPAWN_EGG);
        add("chicken-temperate", "Chicken - Temperate", EntityType.CHICKEN, "TEMPERATE", 1200, Material.CHICKEN_SPAWN_EGG);
        add("chicken-warm", "Chicken - Warm", EntityType.CHICKEN, "WARM", 2000, Material.CHICKEN_SPAWN_EGG);
        add("chicken-cold", "Chicken - Cold", EntityType.CHICKEN, "COLD", 2500, Material.CHICKEN_SPAWN_EGG);
        add("goat-normal", "Goat - Normal", EntityType.GOAT, "NORMAL", 2000, Material.GOAT_SPAWN_EGG);
        add("goat-screaming", "Goat - Screaming", EntityType.GOAT, "SCREAMING", 3500, Material.GOAT_SPAWN_EGG);
        Bukkit.getPluginManager().registerEvents(this, plugin);
        followTask = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::tickFollowing, 20L, 20L);
    }

    private void add(String id, String display, EntityType type, String variant, int price, Material icon) {
        options.put(id, new PetOption(id, display, type, variant, price, icon));
    }

    public void openShop(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, MENU_TITLE);
        int slot = 0;
        for (PetOption option : options.values()) {
            ItemStack item = new ItemStack(option.icon());
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(ChatColor.YELLOW + option.display());
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.WHITE + "Price: " + ChatColor.GOLD + option.price() + " coins");
            lore.add(ChatColor.GRAY + "Permanent baby pet - one-time purchase");
            lore.add(ChatColor.GRAY + "Right-click your pet to sit / stand");
            lore.add(ChatColor.GREEN + "Click to purchase");
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "pet_shop_option"), PersistentDataType.STRING, option.id());
            item.setItemMeta(meta);
            inv.setItem(slot++, item);
        }
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.setDisplayName(ChatColor.WHITE + "Back to Shop");
        back.setItemMeta(backMeta);
        inv.setItem(49, back);
        player.openInventory(inv);
    }

    @EventHandler
    public void onPetShopClick(InventoryClickEvent event) {
        if (!MENU_TITLE.equals(event.getView().getTitle())) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        String id = clicked.getItemMeta().getPersistentDataContainer()
                .get(new NamespacedKey(plugin, "pet_shop_option"), PersistentDataType.STRING);
        if (id == null) {
            if (clicked.getType() == Material.ARROW) plugin.getShopManager().openMain(player);
            return;
        }
        PetOption option = options.get(id);
        if (option == null) return;
        if (plugin.getLoanManager().isShopPurchaseRestricted(player.getUniqueId())) {
            plugin.tellPublic(player, "Your loan is overdue. Shop purchases are locked until you repay it.");
            return;
        }
        if (plugin.getCombinedBalance(player.getUniqueId()) < option.price()) {
            plugin.tellPublic(player, "Not enough coins. This pet costs " + option.price() + " coins.");
            return;
        }
        Location spawn = player.getLocation().clone().add(player.getLocation().getDirection().setY(0).normalize().multiply(1.5));
        spawn.setY(player.getLocation().getY());
        Entity entity = player.getWorld().spawnEntity(spawn, option.type());
        if (!(entity instanceof Animals animal) || !(entity instanceof Mob mob)) {
            entity.remove();
            plugin.tellPublic(player, "This pet could not be spawned. Your coins were not charged.");
            return;
        }
        try {
            applyVariant(entity, option.variant());
            if (animal instanceof Ageable ageable) {
                ageable.setAge(-24000);
                ageable.setAgeLock(true);
            }
            PersistentDataContainer pdc = entity.getPersistentDataContainer();
            pdc.set(petKey, PersistentDataType.BYTE, (byte) 1);
            pdc.set(ownerKey, PersistentDataType.STRING, player.getUniqueId().toString());
            pdc.set(variantKey, PersistentDataType.STRING, option.variant());
            pdc.set(sittingKey, PersistentDataType.BYTE, (byte) 0);
            entity.setCustomName(ChatColor.LIGHT_PURPLE + "@" + player.getName() + "'s Pet");
            entity.setCustomNameVisible(true);
            mob.setCanPickupItems(false);
        } catch (RuntimeException ex) {
            entity.remove();
            plugin.getLogger().warning("Could not initialize purchased pet " + id + ": " + ex.getMessage());
            plugin.tellPublic(player, "Pet creation failed. Your coins were not charged.");
            return;
        }
        if (!plugin.spendAvailableFunds(player.getUniqueId(), option.price())) {
            entity.remove();
            plugin.tellPublic(player, "Purchase cancelled because your balance changed. No coins were charged.");
            return;
        }
        plugin.getShopManager().recordExternalPurchase(player, option.display(), option.price());
        plugin.tellPublic(player, "You purchased " + option.display() + " for " + option.price() + " coins. Your pet is now yours permanently.");
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.2f);
        player.closeInventory();
    }

    private void applyVariant(Entity entity, String variant) {
        switch (entity.getType()) {
            case PANDA -> {
                Panda panda = (Panda) entity;
                Panda.Gene gene = Panda.Gene.valueOf(variant);
                panda.setMainGene(gene);
                panda.setHiddenGene(gene);
            }
            case FOX -> ((Fox) entity).setFoxType(Fox.Type.valueOf(variant));
            case CHICKEN -> ((Chicken) entity).setVariant(Chicken.Variant.valueOf(variant));
            case PIG -> ((Pig) entity).setVariant(Pig.Variant.valueOf(variant));
            case GOAT -> ((Goat) entity).setScreaming(variant.equals("SCREAMING"));
            default -> throw new IllegalArgumentException("Unsupported pet type");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPetInteract(PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();
        if (!isPet(entity)) return;
        UUID owner = owner(entity);
        if (owner == null || !owner.equals(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        ItemStack hand = event.getPlayer().getInventory().getItem(event.getHand());
        if (hand != null && hand.getType() == Material.NAME_TAG) return;
        event.setCancelled(true);
        toggleSit(event.getPlayer(), entity);
    }

    private void toggleSit(Player player, Entity entity) {
        if (!(entity instanceof Mob mob)) return;
        boolean sitting = isSitting(entity);
        entity.getPersistentDataContainer().set(sittingKey, PersistentDataType.BYTE, (byte) (sitting ? 0 : 1));
        mob.setAI(sitting);
        mob.setVelocity(mob.getVelocity().zero());
        following.remove(entity.getUniqueId());
        player.sendMessage("§8[§dXZH PETS§8] §f" + (sitting ? "Your pet is standing and roaming again." : "Your pet is sitting and will stay here."));
        player.playSound(entity.getLocation(), Sound.ENTITY_CAT_PURR, 0.5f, sitting ? 1.3f : 0.8f);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void protectPet(EntityDamageEvent event) {
        if (isPet(event.getEntity())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void protectPetTarget(EntityTargetEvent event) {
        if (isPet(event.getEntity())) event.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        following.entrySet().removeIf(entry -> entry.getValue().equals(event.getPlayer().getUniqueId()));
    }

    public boolean handleCommand(Player player, String command, String[] args) {
        if (command.equalsIgnoreCase("unpet")) {
            Entity target = getTargetPet(player);
            if (target == null || !isOwnedBy(target, player)) {
                plugin.tellPublic(player, "Look directly at one of your own Baby Pets.");
                return true;
            }
            following.remove(target.getUniqueId());
            plugin.tellPublic(player, "Your pet stopped following you and will roam nearby.");
            return true;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("transfer")) {
            Entity target = getTargetPet(player);
            if (target == null || !isOwnedBy(target, player)) {
                plugin.tellPublic(player, "Look directly at the Baby Pet you want to transfer.");
                return true;
            }
            Player recipient = Bukkit.getPlayerExact(args[1].startsWith("@") ? args[1].substring(1) : args[1]);
            if (recipient == null) {
                plugin.tellPublic(player, "That player must be online. Usage: /pet transfer <player>.");
                return true;
            }
            if (recipient.equals(player)) {
                plugin.tellPublic(player, "You already own this pet.");
                return true;
            }
            target.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, recipient.getUniqueId().toString());
            String currentName = ChatColor.stripColor(target.getCustomName() == null ? "" : target.getCustomName());
            if (currentName.isBlank() || currentName.equals("@" + player.getName() + "'s Pet")) {
                target.setCustomName(ChatColor.LIGHT_PURPLE + "@" + recipient.getName() + "'s Pet");
                target.setCustomNameVisible(true);
            }
            plugin.tellPublic(player, "You transferred this pet to " + recipient.getName() + ".");
            plugin.tellPublic(recipient, player.getName() + " transferred a Baby Pet to you. You are now its owner.");
            return true;
        }
        if (args.length != 0) {
            plugin.tellPublic(player, "Usage: /pet [transfer <player>]");
            return true;
        }
        Entity target = getTargetPet(player);
        if (target == null || !isOwnedBy(target, player)) {
            plugin.tellPublic(player, "Look directly at one of your own Baby Pets.");
            return true;
        }
        if (isSitting(target)) {
            plugin.tellPublic(player, "Your pet is sitting. Right-click it to make it stand first.");
            return true;
        }
        following.entrySet().removeIf(entry -> entry.getValue().equals(player.getUniqueId()));
        following.put(target.getUniqueId(), player.getUniqueId());
        plugin.tellPublic(player, "Your pet is now following you. Any other pet will continue roaming independently.");
        return true;
    }

    private Entity getTargetPet(Player player) {
        RayTraceResult result = player.getWorld().rayTraceEntities(player.getEyeLocation(),
                player.getEyeLocation().getDirection(), 7.0, 0.55,
                entity -> entity instanceof Animals && isPet(entity));
        return result == null ? null : result.getHitEntity();
    }

    private void tickFollowing() {
        Iterator<Map.Entry<UUID, UUID>> iterator = following.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, UUID> entry = iterator.next();
            Entity pet = Bukkit.getEntity(entry.getKey());
            Player owner = Bukkit.getPlayer(entry.getValue());
            if (pet == null || !pet.isValid() || owner == null || !owner.isOnline() || isSitting(pet)) {
                iterator.remove();
                continue;
            }
            if (!isOwnedBy(pet, owner)) {
                iterator.remove();
                continue;
            }
            if (!pet.getWorld().equals(owner.getWorld())) {
                pet.teleport(owner.getLocation().clone().add(1.0, 0.0, 1.0));
                continue;
            }
            if (pet.getLocation().distanceSquared(owner.getLocation()) > 100.0) {
                Location destination = owner.getLocation().clone().add(owner.getLocation().getDirection().multiply(-1.5));
                destination.setY(owner.getLocation().getY());
                pet.teleport(destination);
            } else if (pet instanceof Mob mob && pet.getLocation().distanceSquared(owner.getLocation()) > 9.0) {
                mob.getPathfinder().moveTo(owner.getLocation(), 1.05);
            }
        }
    }

    private boolean isPet(Entity entity) {
        return entity != null && entity.getPersistentDataContainer().has(petKey, PersistentDataType.BYTE);
    }

    private UUID owner(Entity entity) {
        String value = entity.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        if (value == null) return null;
        try { return UUID.fromString(value); } catch (IllegalArgumentException ex) { return null; }
    }

    private boolean isOwnedBy(Entity entity, Player player) {
        UUID owner = owner(entity);
        return owner != null && owner.equals(player.getUniqueId());
    }

    private boolean isSitting(Entity entity) {
        return entity.getPersistentDataContainer().getOrDefault(sittingKey, PersistentDataType.BYTE, (byte) 0) == 1;
    }

    public void shutdown() {
        Bukkit.getScheduler().cancelTask(followTask);
        following.clear();
    }
}
