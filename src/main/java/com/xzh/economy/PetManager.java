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
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.RayTraceResult;

import java.util.*;

public final class PetManager implements Listener {
    private static final String MENU_TITLE = ChatColor.DARK_GREEN + "Baby Pets";
    private static final String CONFIRM_TITLE = ChatColor.DARK_GREEN + "Confirm Pet Purchase";
    private final XZHEconomySystem plugin;
    private final NamespacedKey ownerKey;
    private final NamespacedKey petKey;
    private final NamespacedKey variantKey;
    private final NamespacedKey sittingKey;
    private final NamespacedKey identityKey;
    private final File activePetsFile;
    private final YamlConfiguration activePets;
    private final Map<UUID, UUID> following = new HashMap<>();
    private long petTick = 0L;
    private final Map<String, PetOption> options = new LinkedHashMap<>();
    private final int followTask;

    private record PetOption(String id, String display, EntityType type, String variant, int price, Material icon) {}

    public PetManager(XZHEconomySystem plugin) {
        this.plugin = plugin;
        ownerKey = new NamespacedKey(plugin, "pet_owner");
        petKey = new NamespacedKey(plugin, "xzh_baby_pet");
        variantKey = new NamespacedKey(plugin, "pet_variant");
        sittingKey = new NamespacedKey(plugin, "pet_sitting");
        identityKey = new NamespacedKey(plugin, "pet_identity");
        activePetsFile = new File(plugin.getDataFolder(), "active-pets.yml");
        activePets = YamlConfiguration.loadConfiguration(activePetsFile);
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
        followTask = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::tickPets, 1L, 1L);
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
            lore.add(ChatColor.WHITE + "Price: " + ChatColor.GOLD + String.valueOf(option.price()) + " coins");
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
        openConfirmation(player, option);
    }

    private void openConfirmation(Player player, PetOption option) {
        Inventory inv = Bukkit.createInventory(null, 27, CONFIRM_TITLE);
        ItemStack yes = new ItemStack(Material.LIME_CONCRETE);
        ItemMeta ym = yes.getItemMeta();
        ym.setDisplayName(ChatColor.GREEN + "Confirm purchase");
        ym.setLore(List.of(ChatColor.GRAY + option.display(), ChatColor.GOLD.toString() + option.price() + " coins"));
        ym.getPersistentDataContainer().set(new NamespacedKey(plugin, "pet_confirm"), PersistentDataType.STRING, option.id());
        yes.setItemMeta(ym); inv.setItem(11, yes);
        ItemStack no = new ItemStack(Material.RED_CONCRETE);
        ItemMeta nm = no.getItemMeta(); nm.setDisplayName(ChatColor.RED + "Cancel"); no.setItemMeta(nm); inv.setItem(15, no);
        player.openInventory(inv);
    }

    private void purchase(Player player, PetOption option) {
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
            pdc.set(identityKey, PersistentDataType.STRING, UUID.randomUUID().toString());
            entity.setCustomName(ChatColor.LIGHT_PURPLE + "@" + player.getName() + "'s Pet");
            entity.setCustomNameVisible(true);
            mob.setCanPickupItems(false);
            mob.setAware(true);
        } catch (RuntimeException ex) {
            entity.remove();
            plugin.getLogger().warning("Could not initialize purchased pet " + option.id() + ": " + ex.getMessage());
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

    @EventHandler
    public void onPetConfirmationClick(InventoryClickEvent event) {
        if (!CONFIRM_TITLE.equals(event.getView().getTitle())) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        String id = clicked.getItemMeta().getPersistentDataContainer().get(new NamespacedKey(plugin, "pet_confirm"), PersistentDataType.STRING);
        if (id == null) { if (clicked.getType() == Material.RED_CONCRETE) { player.closeInventory(); openShop(player); } return; }
        PetOption option = options.get(id); if (option != null) purchase(player, option);
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
            case CHICKEN -> ((Chicken) entity).setVariant(switch (variant) {
                case "TEMPERATE" -> Chicken.Variant.TEMPERATE;
                case "WARM" -> Chicken.Variant.WARM;
                case "COLD" -> Chicken.Variant.COLD;
                default -> throw new IllegalArgumentException("Unknown chicken variant");
            });
            case PIG -> ((Pig) entity).setVariant(switch (variant) {
                case "TEMPERATE" -> Pig.Variant.TEMPERATE;
                case "WARM" -> Pig.Variant.WARM;
                case "COLD" -> Pig.Variant.COLD;
                default -> throw new IllegalArgumentException("Unknown pig variant");
            });
            case GOAT -> ((Goat) entity).setScreaming(variant.equals("SCREAMING"));
            default -> throw new IllegalArgumentException("Unsupported pet type");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPetInteract(PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();
        if (!isPet(entity)) return;
        UUID owner = owner(entity);
        if (owner == null || !owner.equals(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        ItemStack hand = event.getPlayer().getInventory().getItem(event.getHand());
        if (hand != null && hand.getType() == Material.NAME_TAG) return;
        event.setCancelled(true);
        if (event.getPlayer().isSneaking()) toggleFollowing(event.getPlayer(), entity);
        else toggleSit(event.getPlayer(), entity);
    }

    private void toggleSit(Player player, Entity entity) {
        if (!(entity instanceof Mob mob)) return;
        boolean sitting = isSitting(entity);
        boolean shouldSit = !sitting;
        entity.getPersistentDataContainer().set(sittingKey, PersistentDataType.BYTE, (byte) (shouldSit ? 1 : 0));
        mob.setAware(!shouldSit);
        if (shouldSit) mob.getPathfinder().stopPathfinding();
        entity.setPose(shouldSit ? Pose.SITTING : Pose.STANDING, true);
        mob.setVelocity(new org.bukkit.util.Vector(0, mob.getVelocity().getY(), 0));
        player.sendMessage("§8[§dPET§8] §f" + petName(entity) + (sitting ? " is now standing." : " is now resting."));
        player.playSound(entity.getLocation(), Sound.ENTITY_CAT_PURR, 0.5f, sitting ? 1.3f : 0.8f);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void protectPet(EntityDamageEvent event) {
        if (!isPet(event.getEntity())) return;
        if (!(event instanceof org.bukkit.event.entity.EntityDamageByEntityEvent byEntity)) { event.setCancelled(true); return; }
        Player attacker = null; Entity damager = byEntity.getDamager();
        if (damager instanceof Player direct) attacker = direct;
        else if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) attacker = shooter;
        if (attacker == null || !isOwnedBy(event.getEntity(), attacker)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void protectPetTarget(EntityTargetEvent event) {
        if (isPet(event.getEntity()) || isPet(event.getTarget())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void preventPetAttacks(org.bukkit.event.entity.EntityDamageByEntityEvent event) {
        if (isPet(event.getDamager())) event.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID ownerId = event.getPlayer().getUniqueId();
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (Entity pet : new ArrayList<>(world.getEntities())) {
                if (!isPet(pet) || !ownerId.equals(owner(pet)) || !ownerId.equals(following.get(pet.getUniqueId()))) continue;
                saveActivePet(pet, ownerId);
                following.remove(pet.getUniqueId());
                pet.remove();
            }
        }
        saveActivePets();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        UUID ownerId = event.getPlayer().getUniqueId();
        Bukkit.getScheduler().runTask(plugin, () -> restoreActivePets(event.getPlayer()));
    }

    private void saveActivePet(Entity pet, UUID ownerId) {
        String identity = pet.getPersistentDataContainer().get(identityKey, PersistentDataType.STRING);
        if (identity == null) {
            identity = UUID.randomUUID().toString();
            pet.getPersistentDataContainer().set(identityKey, PersistentDataType.STRING, identity);
        }
        String path = "pets." + identity;
        activePets.set(path + ".owner", ownerId.toString());
        activePets.set(path + ".type", pet.getType().name());
        activePets.set(path + ".variant", pet.getPersistentDataContainer().getOrDefault(variantKey, PersistentDataType.STRING, "NORMAL"));
        activePets.set(path + ".sitting", isSitting(pet));
        activePets.set(path + ".name", pet.getCustomName());
    }

    private void saveActivePets() {
        try {
            plugin.getDataFolder().mkdirs();
            activePets.save(activePetsFile);
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not save active Baby Pets: " + ex.getMessage());
        }
    }

    private void restoreActivePets(Player player) {
        String ownerPath = player.getUniqueId().toString();
        for (String identity : new ArrayList<>(activePets.getConfigurationSection("pets") == null
                ? Collections.emptySet() : activePets.getConfigurationSection("pets").getKeys(false))) {
            String path = "pets." + identity;
            if (!ownerPath.equals(activePets.getString(path + ".owner"))) continue;
            EntityType type;
            try { type = EntityType.valueOf(activePets.getString(path + ".type", "")); }
            catch (IllegalArgumentException ex) { plugin.getLogger().warning("Skipping saved Baby Pet with unknown type: " + identity); continue; }
            String variant = activePets.getString(path + ".variant", "NORMAL");
            boolean sitting = activePets.getBoolean(path + ".sitting", false);
            String savedName = activePets.getString(path + ".name");

            // A hard crash may have left the original entity in the world. Remove it by its stable identity first.
            for (org.bukkit.World world : Bukkit.getWorlds()) {
                for (Entity existing : new ArrayList<>(world.getEntities())) {
                    if (identity.equals(existing.getPersistentDataContainer().get(identityKey, PersistentDataType.STRING))) existing.remove();
                }
            }

            Location spawn = player.getLocation().clone().add(player.getLocation().getDirection().multiply(-1.5));
            spawn.setY(player.getLocation().getY());
            Entity entity = player.getWorld().spawnEntity(spawn, type);
            if (!(entity instanceof Animals animal) || !(entity instanceof Mob mob)) { entity.remove(); continue; }
            try {
                applyVariant(entity, variant);
                if (animal instanceof Ageable ageable) { ageable.setAge(-24000); ageable.setAgeLock(true); }
                PersistentDataContainer pdc = entity.getPersistentDataContainer();
                pdc.set(petKey, PersistentDataType.BYTE, (byte) 1);
                pdc.set(ownerKey, PersistentDataType.STRING, ownerPath);
                pdc.set(variantKey, PersistentDataType.STRING, variant);
                pdc.set(sittingKey, PersistentDataType.BYTE, (byte) (sitting ? 1 : 0));
                pdc.set(identityKey, PersistentDataType.STRING, identity);
                entity.setCustomName(savedName == null ? ChatColor.LIGHT_PURPLE + "@" + player.getName() + "'s Pet" : savedName);
                entity.setCustomNameVisible(true);
                mob.setCanPickupItems(false);
                mob.setAware(!sitting);
                if (sitting) mob.getPathfinder().stopPathfinding();
                entity.setPose(sitting ? Pose.SITTING : Pose.STANDING, true);
                following.put(entity.getUniqueId(), player.getUniqueId());
                activePets.set(path, null);
            } catch (RuntimeException ex) {
                entity.remove();
                plugin.getLogger().warning("Could not restore Baby Pet " + identity + ": " + ex.getMessage());
            }
        }
        saveActivePets();
    }

    public boolean handleCommand(Player player, String command, String[] args) {
        if (command.equalsIgnoreCase("unpet")) {
            Entity target = getTargetPet(player);
            if (target == null || !isOwnedBy(target, player)) {
                plugin.tellPublic(player, "Look directly at one of your own Baby Pets.");
                return true;
            }
            following.remove(target.getUniqueId());
            String identity = target.getPersistentDataContainer().get(identityKey, PersistentDataType.STRING);
            if (identity != null) activePets.set("pets." + identity, null);
            saveActivePets();
            plugin.tellPublic(player, "Your pet stopped following you and will roam nearby.");
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("all")) {
            List<Entity> ownedPets = new ArrayList<>();
            for (org.bukkit.World world : Bukkit.getWorlds()) for (Entity pet : world.getEntities()) if (isPet(pet) && isOwnedBy(pet, player)) ownedPets.add(pet);
            if (ownedPets.isEmpty()) { plugin.tellPublic(player, "You do not own any Baby Pets."); return true; }
            Location destination = player.getLocation().clone().add(player.getLocation().getDirection().multiply(-1.5));
            destination.setY(player.getLocation().getY());
            int brought = 0;
            for (Entity pet : ownedPets) if (pet.teleport(destination)) brought++;
            plugin.tellPublic(player, "Brought " + brought + " of your Baby Pets to you. Only your active pet will follow.");
            return true;
        }
        if (args.length == 0) {
            List<Entity> activePets = following.entrySet().stream()
                    .filter(e -> e.getValue().equals(player.getUniqueId()))
                    .map(e -> Bukkit.getEntity(e.getKey()))
                    .filter(e -> e != null && e.isValid() && isOwnedBy(e, player)).toList();
            if (activePets.isEmpty()) {
                plugin.tellPublic(player, "You have no active pets. Shift + right-click a pet to enable following.");
                return true;
            }
            Location destination = player.getLocation().clone().add(player.getLocation().getDirection().multiply(-1.5));
            destination.setY(player.getLocation().getY());
            for (Entity active : activePets) active.teleport(destination);
            plugin.tellPublic(player, "Your active pet has been called to you.");
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
            plugin.tellPublic(player, "Usage: /pet [all|transfer <player>]");
            return true;
        }
        return true;
    }

    private void toggleFollowing(Player player, Entity pet) {
        UUID petId = pet.getUniqueId();
        if (following.containsKey(petId) && following.get(petId).equals(player.getUniqueId())) {
            following.remove(petId);
            String identity = pet.getPersistentDataContainer().get(identityKey, PersistentDataType.STRING);
            if (identity != null) activePets.set("pets." + identity, null);
            saveActivePets();
            plugin.tellPublic(player, petName(pet) + " is no longer following you.");
        } else {
            for (UUID oldPetId : new ArrayList<>(following.keySet())) {
                if (!player.getUniqueId().equals(following.get(oldPetId))) continue;
                following.remove(oldPetId);
                Entity oldPet = Bukkit.getEntity(oldPetId);
                if (oldPet != null) { String oldIdentity = oldPet.getPersistentDataContainer().get(identityKey, PersistentDataType.STRING); if (oldIdentity != null) activePets.set("pets." + oldIdentity, null); }
            }
            following.put(petId, player.getUniqueId());
            saveActivePet(pet, player.getUniqueId());
            saveActivePets();
            plugin.tellPublic(player, petName(pet) + " is now your active pet.");
        }
    }

    private Entity getTargetPet(Player player) {
        RayTraceResult result = player.getWorld().rayTraceEntities(player.getEyeLocation(),
                player.getEyeLocation().getDirection(), 7.0, 0.55,
                entity -> entity instanceof Animals && isPet(entity));
        return result == null ? null : result.getHitEntity();
    }

    private void tickPets() {
        petTick++;
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (Entity pet : world.getEntities()) {
                if (!isPet(pet) || !pet.isValid()) continue;

                boolean nameVisible = Bukkit.getOnlinePlayers().stream()
                        .anyMatch(viewer -> viewer.getWorld().equals(pet.getWorld())
                                && viewer.getLocation().distanceSquared(pet.getLocation()) <= 256.0);
                ChatColor nameColor = isSitting(pet) ? ChatColor.RED : following.containsKey(pet.getUniqueId()) ? ChatColor.LIGHT_PURPLE : ChatColor.WHITE;
                pet.setCustomName(nameColor + petName(pet));
                pet.setCustomNameVisible(nameVisible);

                if (!(pet instanceof Mob mob)) continue;

                if (isSitting(pet)) {
                    mob.setAware(false);
                    mob.getPathfinder().stopPathfinding();
                    mob.setVelocity(new org.bukkit.util.Vector(0, mob.getVelocity().getY(), 0));
                    Player lookAt = Bukkit.getOnlinePlayers().stream()
                            .filter(p -> p.getWorld().equals(pet.getWorld())
                                    && p.getLocation().distanceSquared(pet.getLocation()) <= 256.0)
                            .min(Comparator.comparingDouble(p -> p.getLocation().distanceSquared(pet.getLocation())))
                            .orElse(null);
                    if (lookAt != null) {
                        org.bukkit.util.Vector direction = lookAt.getEyeLocation().toVector()
                                .subtract(pet.getLocation().toVector());
                        float yaw = (float) Math.toDegrees(Math.atan2(-direction.getX(), direction.getZ()));
                        pet.setRotation(yaw, pet.getLocation().getPitch());
                    }
                    continue;
                }

                // Pets not explicitly marked as following retain ordinary animal AI and never chase their owner.
                UUID activeOwnerId = following.get(pet.getUniqueId());
                if (activeOwnerId == null) {
                    mob.setAware(true);
                    continue;
                }

                Player owner = Bukkit.getPlayer(activeOwnerId);
                if (owner == null || !owner.isOnline() || !isOwnedBy(pet, owner)) {
                    mob.setAware(true);
                    mob.getPathfinder().stopPathfinding();
                    continue;
                }

                mob.setAware(true);
                if (!pet.getWorld().equals(owner.getWorld())) {
                    pet.teleport(owner.getLocation().clone().add(1.0, 0.0, 1.0));
                    continue;
                }

                if (pet instanceof Panda panda) {
                    if (panda.getMainGene() == Panda.Gene.LAZY || panda.getMainGene() == Panda.Gene.WEAK) panda.setMainGene(Panda.Gene.NORMAL);
                    if (panda.getHiddenGene() == Panda.Gene.LAZY || panda.getHiddenGene() == Panda.Gene.WEAK) panda.setHiddenGene(Panda.Gene.NORMAL);
                }
                double distance = pet.getLocation().distance(owner.getLocation());
                if (distance > 10.0) {
                    Location destination = owner.getLocation().clone()
                            .add(owner.getLocation().getDirection().multiply(-1.5));
                    destination.setY(owner.getLocation().getY());
                    pet.teleport(destination);
                } else if (distance > 3.0) {
                    if (petTick % 10L == 0L) mob.getPathfinder().moveTo(owner.getLocation(), 1.0);
                } else if (distance <= 2.5 && petTick % 10L == 0L) {
                    mob.getPathfinder().stopPathfinding();
                }
            }
        }
    }

    private String petName(Entity entity) {
        if (entity == null) return "Pet";
        String variant = entity.getPersistentDataContainer().get(variantKey, PersistentDataType.STRING);
        String species = switch (entity.getType()) { case PANDA -> "Panda"; case FOX -> "Fox"; case PIG -> "Pig"; case CHICKEN -> "Chicken"; case GOAT -> "Goat"; default -> "Pet"; };
        if (variant == null || variant.isBlank()) return species;
        String pretty = variant.substring(0, 1).toUpperCase(Locale.ROOT) + variant.substring(1).toLowerCase(Locale.ROOT);
        return species + " (" + pretty + ")";
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
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (Entity pet : new ArrayList<>(world.getEntities())) {
                if (!isPet(pet)) continue;
                UUID ownerId = following.get(pet.getUniqueId());
                if (ownerId != null && ownerId.equals(owner(pet))) {
                    saveActivePet(pet, ownerId);
                    pet.remove();
                }
            }
        }
        saveActivePets();
        following.clear();
    }
}
