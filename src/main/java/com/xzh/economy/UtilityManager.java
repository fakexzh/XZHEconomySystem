package com.xzh.economy;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class UtilityManager implements Listener {
    private static final long TPA_TTL = 60_000L;
    private final JavaPlugin plugin;
    private final File file;
    private final YamlConfiguration data;
    private final Map<UUID, UUID> requestsByTarget = new HashMap<>();
    private final Map<UUID, Long> requestExpiry = new HashMap<>();
    private final Map<UUID, UUID> replies = new HashMap<>();
    private final Map<UUID, Boolean> afk = new HashMap<>();
    private final Map<UUID, Long> rtpCooldown = new HashMap<>();
    private final Map<UUID, Double> fractionalTimeTicks = new HashMap<>();

    public UtilityManager(JavaPlugin plugin) {
        this.plugin = plugin;
        file = new File(plugin.getDataFolder(), "locations.yml");
        data = YamlConfiguration.loadConfiguration(file);
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, this::advanceServerTime, 20L, 20L);
    }

    public boolean handle(CommandSender sender, String command, String[] args) {
        String cmd = command.toLowerCase(Locale.ROOT);
        if (!(sender instanceof Player p) && !cmd.equals("list") && !cmd.equals("help")) {
            sender.sendMessage("This command can only be used by a player.");
            return true;
        }
        Player player = sender instanceof Player ? (Player) sender : null;
        switch (cmd) {
            case "setspawn" -> {
                if (!player.hasPermission("xzh.admin")) { msg(player, "No permission."); return true; }
                data.set("spawn", player.getLocation()); save();
                msg(player, "World spawn point saved."); return true;
            }
            case "spawn" -> {
                Location loc = data.getLocation("spawn");
                if (loc == null) loc = player.getWorld().getSpawnLocation();
                player.teleport(loc); msg(player, "Teleported to spawn."); return true;
            }
            case "sethome" -> {
                String name = homeName(args);
                if (name == null) { msg(player, "Usage: /sethome [name]"); return true; }
                String base = "homes." + player.getUniqueId();
                int max = Math.max(1, plugin.getConfig().getInt("utilities.max-homes", 3));
                if (!data.contains(base + "." + name) && data.getConfigurationSection(base) != null
                        && data.getConfigurationSection(base).getKeys(false).size() >= max) {
                    msg(player, "You have reached your home limit (" + max + ")."); return true;
                }
                data.set(base + "." + name, player.getLocation()); save();
                msg(player, "Home '" + name + "' saved."); return true;
            }
            case "home" -> {
                if (args.length == 0 && data.contains("bedhomes." + player.getUniqueId())) {
                    Location bed = bedHome(player.getUniqueId());
                    if (bed != null) { player.teleport(bed); msg(player, "Teleported to your bed home."); }
                    else msg(player, "Your bed home has no safe landing spot nearby. Clear space beside your bed and try again.");
                    return true;
                }
                String name = homeName(args);
                if (name == null) { msg(player, "Usage: /home [name]"); return true; }
                Location loc = data.getLocation("homes." + player.getUniqueId() + "." + name);
                if (loc == null) { msg(player, "Home not found."); return true; }
                player.teleport(loc); msg(player, "Teleported home."); return true;
            }
            case "delhome" -> {
                String name = homeName(args);
                if (name == null) { msg(player, "Usage: /delhome [name]"); return true; }
                String path = "homes." + player.getUniqueId() + "." + name;
                if (!data.contains(path)) { msg(player, "Home not found."); return true; }
                data.set(path, null); save(); msg(player, "Home deleted."); return true;
            }
            case "setwarp" -> {
                if (!player.hasPermission("xzh.admin")) { msg(player, "No permission."); return true; }
                String name = named(args);
                if (name == null) { msg(player, "Usage: /setwarp <name>"); return true; }
                data.set("warps." + name, player.getLocation()); save(); msg(player, "Warp '" + name + "' saved."); return true;
            }
            case "warp" -> {
                String name = named(args);
                if (name == null) { msg(player, "Usage: /warp <name>"); return true; }
                Location loc = data.getLocation("warps." + name);
                if (loc == null) { msg(player, "Warp not found."); return true; }
                player.teleport(loc); msg(player, "Teleported to " + name + "."); return true;
            }
            case "delwarp" -> {
                if (!player.hasPermission("xzh.admin")) { msg(player, "No permission."); return true; }
                String name = named(args);
                if (name == null) { msg(player, "Usage: /delwarp <name>"); return true; }
                if (!data.contains("warps." + name)) { msg(player, "Warp not found."); return true; }
                data.set("warps." + name, null); save(); msg(player, "Warp deleted."); return true;
            }
            case "tpa" -> {
                if (args.length != 1) { msg(player, "Usage: /tpa <player>"); return true; }
                Player target = Bukkit.getPlayerExact(args[0]);
                if (target == null || target.equals(player)) { msg(player, "Choose another online player."); return true; }
                requestsByTarget.put(target.getUniqueId(), player.getUniqueId());
                requestExpiry.put(target.getUniqueId(), System.currentTimeMillis() + TPA_TTL);
                msg(player, "Teleport request sent to " + target.getName() + ".");
                msg(target, player.getName() + " wants to teleport to you. Use /tpaccept or /tpdeny within 60 seconds.");
                return true;
            }
            case "tpaccept", "tpdeny" -> {
                UUID requesterId = requestsByTarget.remove(player.getUniqueId());
                Long expiry = requestExpiry.remove(player.getUniqueId());
                if (requesterId == null || expiry == null || System.currentTimeMillis() > expiry) {
                    msg(player, "You have no active teleport request."); return true;
                }
                Player requester = Bukkit.getPlayer(requesterId);
                if (requester == null) { msg(player, "That player is no longer online."); return true; }
                if (cmd.equals("tpdeny")) {
                    msg(player, "Teleport request denied."); msg(requester, player.getName() + " denied your teleport request.");
                } else {
                    XZHEconomySystem economy = (XZHEconomySystem) plugin;
                    double fee = Math.max(0.0, plugin.getConfig().getDouble("utilities.tpa-cost", 10.0));
                    if (economy.getCombinedBalance(requesterId) + 1e-9 < fee) {
                        msg(requester, "You need " + money(fee) + " coins to complete /tpa.");
                        msg(player, "Teleport request could not complete because the requester cannot afford the fee.");
                        return true;
                    }
                    boolean teleported = requester.teleport(player.getLocation());
                    if (!teleported) {
                        msg(requester, "Teleport failed; no fee was charged.");
                        msg(player, "Teleport failed; no fee was charged.");
                        return true;
                    }
                    if (!economy.spendAvailableFunds(requesterId, fee)) {
                        msg(requester, "Teleport succeeded, but payment could not be collected. Please contact an admin.");
                        msg(player, "Teleport request accepted.");
                        return true;
                    }
                    msg(player, "Teleport request accepted.");
                    msg(requester, "Teleported to " + player.getName() + " for " + money(fee) + " coins.");
                }
                return true;
            }
            case "back" -> {
                Location loc = data.getLocation("back." + player.getUniqueId());
                if (loc == null) { msg(player, "No saved death location."); return true; }
                XZHEconomySystem economy = (XZHEconomySystem) plugin;
                double fee = Math.max(0.0, plugin.getConfig().getDouble("utilities.back-cost", 30.0));
                if (economy.getCombinedBalance(player.getUniqueId()) + 1e-9 < fee) {
                    msg(player, "You need " + money(fee) + " coins to use /back. Sell eligible items to earn coins, then repay any overdue loan.");
                    return true;
                }
                if (!player.teleport(loc)) { msg(player, "Teleport failed; no fee was charged."); return true; }
                if (!economy.spendAvailableFunds(player.getUniqueId(), fee)) {
                    msg(player, "Teleport succeeded, but payment could not be collected. Please contact an admin.");
                    return true;
                }
                msg(player, "Returned to your last death location for " + money(fee) + " coins."); return true;
            }
            case "msg" -> {
                if (args.length < 2) { msg(player, "Usage: /msg <player> <message>"); return true; }
                Player target = Bukkit.getPlayerExact(args[0]);
                if (target == null || target.equals(player)) { msg(player, "Choose another online player."); return true; }
                if (data.getBoolean("ignores." + target.getUniqueId() + "." + player.getUniqueId(), false)) {
                    msg(player, "That player is not accepting your messages."); return true;
                }
                String body = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
                msg(player, "§7[me -> " + target.getName() + "] §f" + body);
                msg(target, "§7[" + player.getName() + " -> me] §f" + body);
                replies.put(player.getUniqueId(), target.getUniqueId());
                replies.put(target.getUniqueId(), player.getUniqueId());
                return true;
            }
            case "reply" -> {
                if (args.length < 1) { msg(player, "Usage: /reply <message>"); return true; }
                UUID targetId = replies.get(player.getUniqueId());
                Player target = targetId == null ? null : Bukkit.getPlayer(targetId);
                if (target == null) { msg(player, "Nobody to reply to."); return true; }
                if (data.getBoolean("ignores." + target.getUniqueId() + "." + player.getUniqueId(), false)) {
                    msg(player, "That player is not accepting your messages."); return true;
                }
                String body = String.join(" ", args);
                msg(player, "§7[me -> " + target.getName() + "] §f" + body);
                msg(target, "§7[" + player.getName() + " -> me] §f" + body);
                replies.put(player.getUniqueId(), target.getUniqueId());
                replies.put(target.getUniqueId(), player.getUniqueId());
                return true;
            }
            case "ignore" -> {
                if (args.length != 1) { msg(player, "Usage: /ignore <player>"); return true; }
                Player target = Bukkit.getPlayerExact(args[0]);
                if (target == null || target.equals(player)) { msg(player, "Choose another online player."); return true; }
                String path = "ignores." + player.getUniqueId() + "." + target.getUniqueId();
                boolean ignoring = !data.getBoolean(path, false);
                data.set(path, ignoring); save();
                msg(player, ignoring ? "Ignoring " + target.getName() + "." : "No longer ignoring " + target.getName() + ".");
                return true;
            }
            case "afk" -> {
                boolean value = !afk.getOrDefault(player.getUniqueId(), false);
                afk.put(player.getUniqueId(), value);
                Bukkit.broadcastMessage(ChatColor.GRAY + player.getName() + (value ? " is now AFK." : " is no longer AFK."));
                return true;
            }
            case "xzh" -> {
                if (args.length == 0 || !args[0].equalsIgnoreCase("time")) {
                    msg(player, "Usage: /xzh time <daylength|nightlength <minutes>|set <day|night>|pause|resume|info>");
                    return true;
                }
                if (!player.hasPermission("xzh.admin")) { msg(player, "No permission."); return true; }
                if (args.length == 2 && args[1].equalsIgnoreCase("pause")) {
                    plugin.getConfig().set("time-control.paused", true); saveConfig();
                    msg(player, "Custom day/night cycle paused."); return true;
                }
                if (args.length == 2 && args[1].equalsIgnoreCase("resume")) {
                    plugin.getConfig().set("time-control.paused", false); saveConfig();
                    msg(player, "Custom day/night cycle resumed."); return true;
                }
                if (args.length == 2 && args[1].equalsIgnoreCase("info")) {
                    msg(player, "Day: " + plugin.getConfig().getInt("time-control.daylength-minutes", 30)
                        + " minutes; night: " + plugin.getConfig().getInt("time-control.nightlength-minutes", 30)
                        + " minutes; paused: " + plugin.getConfig().getBoolean("time-control.paused", false) + ".");
                    return true;
                }
                if (args.length == 3 && args[1].equalsIgnoreCase("set")) {
                    if (args[2].equalsIgnoreCase("day")) {
                        for (World world : Bukkit.getWorlds()) if (world.getEnvironment() == World.Environment.NORMAL) world.setTime(1000L);
                    } else if (args[2].equalsIgnoreCase("night")) {
                        for (World world : Bukkit.getWorlds()) if (world.getEnvironment() == World.Environment.NORMAL) world.setTime(13000L);
                    } else { msg(player, "Use /xzh time set <day|night>."); return true; }
                    msg(player, "Server world time updated."); return true;
                }
                if (args.length == 3 && (args[1].equalsIgnoreCase("daylength") || args[1].equalsIgnoreCase("nightlength"))) {
                    int minutes;
                    try { minutes = Integer.parseInt(args[2]); } catch (NumberFormatException ex) { msg(player, "Minutes must be a number from 1 to 240."); return true; }
                    if (minutes < 1 || minutes > 240) { msg(player, "Minutes must be between 1 and 240."); return true; }
                    plugin.getConfig().set("time-control." + (args[1].equalsIgnoreCase("daylength") ? "daylength-minutes" : "nightlength-minutes"), minutes);
                    saveConfig();
                    msg(player, "Custom " + args[1].replace("length", "") + " length set to " + minutes + " minutes."); return true;
                }
                msg(player, "Usage: /xzh time <daylength|nightlength <minutes>|set <day|night>|pause|resume|info>");
                return true;
            }
            case "helpxzh" -> {
                net.md_5.bungee.api.chat.TextComponent link = new net.md_5.bungee.api.chat.TextComponent("§6[XZH] §eClick here to open the XZH server guide");
                link.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
                    net.md_5.bungee.api.chat.ClickEvent.Action.OPEN_URL,
                    "https://github.com/fakexzh/XZHEconomySystem/blob/main/README.md"));
                link.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(
                    net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
                    new net.md_5.bungee.api.chat.hover.content.Text("Open the XZHEconomySystem guide")));
                player.spigot().sendMessage(link);
                return true;
            }
            case "help" -> {
                sender.sendMessage(ChatColor.GOLD + "XZHEconomySystem commands:");
                sender.sendMessage(ChatColor.WHITE + "/balance, /pay, /shop, /books, /loan, /quests, /deathinventory");
                sender.sendMessage(ChatColor.WHITE + "/spawn, /home, /sethome, /delhome, /warp, /tpa, /tpaccept, /tpdeny, /back, /rtp");
                sender.sendMessage(ChatColor.WHITE + "/msg, /reply, /ignore, /afk, /near, /list, /top, /kit");
                if (sender.hasPermission("xzh.admin")) sender.sendMessage(ChatColor.WHITE + "/xzhadmin, /eco, /shopadmin, /setspawn, /setwarp, /delwarp");
                return true;
            }
            case "kit" -> {
                if (args.length != 1 || !args[0].equalsIgnoreCase("starter")) { msg(player, "Usage: /kit starter"); return true; }
                if (!player.hasPermission("xzh.kit.starter")) { msg(player, "No permission for this kit."); return true; }
                String path = "kits." + player.getUniqueId() + ".starter-last";
                long cooldown = Math.max(1L, plugin.getConfig().getLong("kits.starter.cooldown-hours", 24)) * 60L * 60L * 1000L;
                long last = data.getLong(path, 0L);
                long now = System.currentTimeMillis();
                if (last > 0 && now < last + cooldown) { msg(player, "Starter kit cooldown: " + ((last + cooldown - now) / 3_600_000L) + " hours remaining."); return true; }
                List<String> entries = plugin.getConfig().getStringList("kits.starter.items");
                if (entries.isEmpty()) { msg(player, "The starter kit has no items configured."); return true; }
                java.util.List<ItemStack> kitItems = new java.util.ArrayList<>();
                for (String entry : entries) {
                    String[] bits = entry.split(":");
                    Material material;
                    int amount;
                    try { material = Material.valueOf(bits[0].toUpperCase(Locale.ROOT)); amount = bits.length > 1 ? Integer.parseInt(bits[1]) : 1; }
                    catch (IllegalArgumentException ex) { plugin.getLogger().warning("Invalid starter kit entry: " + entry); continue; }
                    if (amount < 1 || amount > 64 || material.isAir()) continue;
                    kitItems.add(new org.bukkit.inventory.ItemStack(material, amount));
                }
                if (kitItems.isEmpty()) { msg(player, "The starter kit configuration contains no valid items."); return true; }
                data.set(path, now); save();
                for (org.bukkit.inventory.ItemStack kitItem : kitItems) {
                    for (org.bukkit.inventory.ItemStack leftover : player.getInventory().addItem(kitItem).values())
                        player.getWorld().dropItemNaturally(player.getLocation(), leftover);
                }
                msg(player, "Starter kit claimed."); return true;
            }
            case "list" -> {
                String names = Bukkit.getOnlinePlayers().stream().map(Player::getName).sorted().collect(java.util.stream.Collectors.joining(", "));
                sender.sendMessage(ChatColor.GOLD + "Online (" + Bukkit.getOnlinePlayers().size() + "): " + ChatColor.WHITE + names);
                return true;
            }
            case "near" -> {
                String nearby = player.getWorld().getPlayers().stream()
                        .filter(other -> !other.equals(player) && other.getLocation().distanceSquared(player.getLocation()) <= 10000)
                        .map(Player::getName).sorted().collect(java.util.stream.Collectors.joining(", "));
                msg(player, nearby.isEmpty() ? "No players within 100 blocks." : "Nearby (100 blocks): " + nearby);
                return true;
            }
            case "rtp" -> {
                long now = System.currentTimeMillis();
                long ready = rtpCooldown.getOrDefault(player.getUniqueId(), 0L);
                if (now < ready) { msg(player, "Random teleport cooldown: " + ((ready - now + 999) / 1000) + " seconds."); return true; }
                int radius = Math.max(100, plugin.getConfig().getInt("utilities.rtp-radius", 3000));
                Location center = data.getLocation("spawn");
                if (center == null) center = player.getWorld().getSpawnLocation();
                World world = center.getWorld();
                if (world == null) { msg(player, "No valid world for random teleport."); return true; }
                java.util.concurrent.ThreadLocalRandom random = java.util.concurrent.ThreadLocalRandom.current();
                for (int attempt = 0; attempt < 30; attempt++) {
                    int x = center.getBlockX() + random.nextInt(-radius, radius + 1);
                    int z = center.getBlockZ() + random.nextInt(-radius, radius + 1);
                    Block floor = world.getHighestBlockAt(x, z);
                    Material ground = floor.getType();
                    Material feet = floor.getRelative(0, 1, 0).getType();
                    Material head = floor.getRelative(0, 2, 0).getType();
                    if (!ground.isSolid() || ground == Material.MAGMA_BLOCK || ground == Material.CACTUS
                            || ground == Material.CAMPFIRE || ground == Material.SOUL_CAMPFIRE
                            || ground == Material.FIRE || ground == Material.SOUL_FIRE
                            || feet != Material.AIR || head != Material.AIR) continue;
                    player.teleport(new Location(world, x + 0.5, floor.getY() + 1, z + 0.5));
                    long cooldownSeconds = Math.max(1L, plugin.getConfig().getLong("utilities.rtp-cooldown-seconds", 600));
                    rtpCooldown.put(player.getUniqueId(), now + cooldownSeconds * 1000L);
                    msg(player, "Randomly teleported. Cooldown: " + Math.max(1L, cooldownSeconds / 60L) + " minutes.");
                    return true;
                }
                msg(player, "Could not find a safe location. Try again.");
                return true;
            }
            case "top" -> {
                Location at = player.getLocation();
                Block highest = at.getWorld().getHighestBlockAt(at.getBlockX(), at.getBlockZ());
                player.teleport(new Location(at.getWorld(), at.getBlockX() + 0.5, highest.getY() + 1, at.getBlockZ() + 0.5, at.getYaw(), at.getPitch()));
                msg(player, "Moved to the surface."); return true;
            }
            default -> { return false; }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBedPlace(BlockPlaceEvent event) {
        if (!event.getBlockPlaced().getType().name().endsWith("_BED")) return;
        Player player = event.getPlayer();
        String path = "bedhomes." + player.getUniqueId();
        // Store the bed block; /home resolves a safe position beside it.
        data.set(path, event.getBlockPlaced().getLocation());
        save();
        msg(player, "Your bed is now your home.");
    }

    @EventHandler(ignoreCancelled = true)
    public void onBedBreak(BlockBreakEvent event) {
        if (!event.getBlock().getType().name().endsWith("_BED")) return;
        var homes = data.getConfigurationSection("bedhomes");
        if (homes == null) return;
        boolean changed = false;
        for (String owner : homes.getKeys(false)) {
            Location bed = data.getLocation("bedhomes." + owner);
            if (bed == null || bed.getWorld() == null || !bed.getWorld().equals(event.getBlock().getWorld())) continue;
            if (isSameBed(bed, event.getBlock())) {
                data.set("bedhomes." + owner, null);
                changed = true;
                if (owner.equals(event.getPlayer().getUniqueId().toString())) {
                    msg(event.getPlayer(), "Your bed home was cleared because the bed was broken.");
                }
                Player ownerPlayer = null;
                try { ownerPlayer = Bukkit.getPlayer(UUID.fromString(owner)); } catch (IllegalArgumentException ignored) { }
                if (ownerPlayer != null && !ownerPlayer.equals(event.getPlayer())) {
                    msg(ownerPlayer, "Your bed home was cleared because the bed was broken.");
                }
            }
        }
        if (changed) save();
    }

    private boolean isSameBed(Location savedLocation, Block brokenBlock) {
        Block savedBlock = savedLocation.getBlock();
        if (savedBlock.equals(brokenBlock)) return true;
        if (!(savedBlock.getBlockData() instanceof org.bukkit.block.data.type.Bed savedBed)
                || !(brokenBlock.getBlockData() instanceof org.bukkit.block.data.type.Bed brokenBed)) return false;
        if (savedBed.getPart() == brokenBed.getPart() || savedBed.getFacing() != brokenBed.getFacing()) return false;
        org.bukkit.block.BlockFace otherHalfDirection = savedBed.getPart() == org.bukkit.block.data.type.Bed.Part.FOOT
                ? savedBed.getFacing() : savedBed.getFacing().getOppositeFace();
        return savedBlock.getRelative(otherHalfDirection).equals(brokenBlock);
    }

    private Location bedHome(UUID id) {
        Location bed = data.getLocation("bedhomes." + id);
        if (bed == null || bed.getWorld() == null) return null;
        // Bed may be represented by either half; find a nearby safe standing block.
        for (int radius = 1; radius <= 2; radius++) {
            for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
                Location candidate = new Location(bed.getWorld(), bed.getBlockX() + dx + 0.5,
                        bed.getBlockY() + 1, bed.getBlockZ() + dz + 0.5);
                if (candidate.getBlock().isPassable() && candidate.clone().add(0, 1, 0).getBlock().isPassable()
                        && candidate.clone().subtract(0, 1, 0).getBlock().getType().isSolid()) return candidate;
            }
        }
        return null;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        data.set("back." + event.getEntity().getUniqueId(), event.getEntity().getLocation());
        save();
        Player player = event.getEntity();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            msg(player, "You died. Use /back to return to your death location (30 coins), or /deathinventory to review recovery options.");
        });
    }

    private void advanceServerTime() {
        if (!plugin.getConfig().getBoolean("time-control.enabled", true)) return;
        for (World world : Bukkit.getWorlds()) {
            if (world.getEnvironment() != World.Environment.NORMAL) continue;
            world.setGameRule(org.bukkit.GameRule.DO_DAYLIGHT_CYCLE, false);
            if (plugin.getConfig().getBoolean("time-control.paused", false)) continue;
            long time = Math.floorMod(world.getTime(), 24000L);
            boolean day = time < 12000L;
            int minutes = Math.max(1, plugin.getConfig().getInt(day ? "time-control.daylength-minutes" : "time-control.nightlength-minutes", 30));
            double ticksPerSecond = 12000.0 / (minutes * 60.0);
            UUID worldId = world.getUID();
            double accumulated = fractionalTimeTicks.getOrDefault(worldId, 0.0) + ticksPerSecond;
            long advance = (long) accumulated;
            fractionalTimeTicks.put(worldId, accumulated - advance);
            if (advance > 0) world.setTime(Math.floorMod(time + advance, 24000L));
        }
    }

    private void saveConfig() {
        try { plugin.getConfig().save(new File(plugin.getDataFolder(), "config.yml")); }
        catch (IOException e) { plugin.getLogger().severe("Could not save time-control settings: " + e.getMessage()); }
    }

    private String money(double amount) {
        return String.format(Locale.US, "%.2f", amount).replaceAll("\\.00$", "");
    }

    private String homeName(String[] args) {
        if (args.length > 1) return null;
        return args.length == 0 ? "home" : named(args);
    }
    private String named(String[] args) {
        if (args.length != 1 || !args[0].matches("[A-Za-z0-9_-]{1,24}")) return null;
        return args[0].toLowerCase(Locale.ROOT);
    }
    private void msg(Player player, String text) { player.sendMessage("§8[§6XZH§8] §r" + text); }
    private void save() {
        try { data.save(file); }
        catch (IOException e) { plugin.getLogger().severe("Could not save locations.yml: " + e.getMessage()); }
    }
}
