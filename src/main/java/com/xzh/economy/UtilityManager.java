package com.xzh.economy;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class UtilityManager implements Listener {
    private static final long TPA_TTL = 60_000L;
    private final XZHEconomySystem plugin;
    private final File file;
    private final YamlConfiguration data;
    private final Map<UUID, UUID> requestsByTarget = new HashMap<>();
    private final Map<UUID, Long> requestExpiry = new HashMap<>();
    private final Map<UUID, TpaQuote> quoteByTarget = new HashMap<>();
    private final Map<UUID, PreparedTpa> preparedByRequester = new HashMap<>();
    private record TpaQuote(int distance, int cost, boolean netherFixed, String distanceLabel) {}
    private record PreparedTpa(UUID targetId, TpaQuote quote) {}
    private final Map<UUID, UUID> replies = new HashMap<>();
    private final Map<UUID, Boolean> afk = new HashMap<>();
    private final Map<UUID, Long> rtpCooldown = new HashMap<>();
    private final Map<UUID, BukkitRunnable> homeCountdowns = new HashMap<>();
    private final Map<UUID, Location> homeCountdownStarts = new HashMap<>();
    private final Map<UUID, PendingHome> pendingHomes = new HashMap<>();
    private final NamespacedKey homeActionKey;
    private final Map<UUID, BukkitRunnable> spawnCountdowns = new HashMap<>();
    private final Map<UUID, PendingSpawn> pendingSpawns = new HashMap<>();
    private final Map<UUID, PendingBack> pendingBacks = new HashMap<>();
    private final Map<UUID, BackCountdown> backCountdowns = new HashMap<>();
    private record PendingSpawn(Location destination, TpaQuote quote, String token, long createdAt) {}
    private record PendingHome(String name, Location destination, TpaQuote quote, String token, long createdAt) {}
    private record PendingBack(Location destination, int cost, String token, long createdAt) {}
    private static final class BackCountdown {
        private final Location start;
        private final PendingBack quote;
        private BukkitRunnable task;
        private BackCountdown(Location start, PendingBack quote) { this.start = start; this.quote = quote; }
    }

    public UtilityManager(XZHEconomySystem plugin) {
        this.plugin = plugin;
        this.homeActionKey = new NamespacedKey(plugin, "home_action");
        file = new File(plugin.getDataFolder(), "locations.yml");
        data = YamlConfiguration.loadConfiguration(file);
        boolean migratedHomes = false;
        if (data.contains("homes")) { data.set("homes", null); migratedHomes = true; }
        if (data.contains("bedhomes")) { data.set("bedhomes", null); migratedHomes = true; }
        if (migratedHomes) save();
        for (World world : Bukkit.getWorlds()) {
            world.setGameRule(org.bukkit.GameRule.DO_DAYLIGHT_CYCLE, true);
        }
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public boolean handle(CommandSender sender, String command, String[] args) {
        String cmd = command.toLowerCase(Locale.ROOT);
        if (!(sender instanceof Player p) && !cmd.equals("list") && !cmd.equals("help")) {
            plugin.tellPublic(sender, "This command can only be used by a player.");
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
                if (args.length == 2 && args[0].equalsIgnoreCase("accept")) {
                    acceptSpawn(player, args[1]);
                    return true;
                }
                if (args.length == 2 && args[0].equalsIgnoreCase("reject")) {
                    rejectSpawn(player, args[1]);
                    return true;
                }
                if (args.length != 0) {
                    msg(player, "Usage: /spawn");
                    return true;
                }
                Location destination = data.getLocation("spawn");
                if (destination == null) destination = player.getWorld().getSpawnLocation();
                if (destination.getWorld() == null) {
                    msg(player, "The server spawn location is unavailable.");
                    return true;
                }
                TpaQuote quote = quoteTravel(player, destination, "Teleporting to spawn between these world types is not supported.");
                if (quote == null) return true;
                XZHEconomySystem economy = (XZHEconomySystem) plugin;
                if (economy.getCombinedBalance(player.getUniqueId()) + 1e-9 < quote.cost()) {
                    msg(player, "You need " + money(quote.cost()) + " coins for /spawn. Your pocket + bank balance is too low.");
                    return true;
                }

                UUID id = player.getUniqueId();
                pendingSpawns.remove(id);
                String token = UUID.randomUUID().toString().substring(0, 8);
                PendingSpawn pending = new PendingSpawn(destination.clone(), quote, token, System.currentTimeMillis());
                pendingSpawns.put(id, pending);

                net.md_5.bungee.api.chat.TextComponent details = new net.md_5.bungee.api.chat.TextComponent(
                        "§8[§6economy§8] §r§6Teleport to spawn? §8| §bDistance: §f" + quote.distanceLabel()
                                + " §8| §eCost: §6✦ " + money(quote.cost()) + " coins ");
                net.md_5.bungee.api.chat.TextComponent accept = new net.md_5.bungee.api.chat.TextComponent("§a[ ACCEPT ]");
                accept.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
                        net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND, "/spawn accept " + token));
                net.md_5.bungee.api.chat.TextComponent reject = new net.md_5.bungee.api.chat.TextComponent(" §c[ REJECT ]");
                reject.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
                        net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND, "/spawn reject " + token));
                details.addExtra(accept);
                details.addExtra(reject);
                player.spigot().sendMessage(details);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.9f, 1.2f);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    PendingSpawn current = pendingSpawns.get(id);
                    if (current != null && current.token().equals(token)) {
                        pendingSpawns.remove(id);
                        Player online = Bukkit.getPlayer(id);
                        if (online != null) msg(online, "Spawn confirmation expired. Use /spawn again.");
                    }
                }, 600L);
                return true;
            }
            case "home" -> {
                if (args.length == 2 && args[0].equalsIgnoreCase("accept")) {
                    acceptHome(player, args[1]);
                    return true;
                }
                if (args.length == 2 && args[0].equalsIgnoreCase("reject")) {
                    rejectHome(player, args[1]);
                    return true;
                }
                if (args.length > 1) {
                    msg(player, "Usage: /home [name]");
                    return true;
                }
                String name = args.length == 1 ? args[0].toLowerCase(Locale.ROOT) : firstHomeName(player.getUniqueId());
                if (name != null) {
                    Location destination = data.getLocation(homePath(player.getUniqueId(), name));
                    if (destination == null || destination.getWorld() == null) {
                        msg(player, "That saved home is unavailable. Check /homes.");
                        return true;
                    }
                    requestHomeConfirmation(player, name, destination);
                    return true;
                }
                Location bed = bedHome(player);
                if (bed == null) {
                    msg(player, "You have no named homes or active vanilla bed spawn. Use /sethome <name> or set your bed spawn.");
                    return true;
                }
                requestHomeConfirmation(player, "Bed", bed);
                return true;
            }
            case "sethome" -> {
                String name = named(args);
                if (name == null) { msg(player, "Usage: /sethome <name> (letters, numbers, _ or -, max 24 characters)"); return true; }
                UUID id = player.getUniqueId();
                List<String> names = homeNames(id);
                if (!names.contains(name) && names.size() >= 2) {
                    msg(player, "You already have 2 named homes. Use /delhome <name> before adding another.");
                    return true;
                }
                data.set(homePath(id, name), player.getLocation().clone());
                if (!names.contains(name)) names.add(name);
                data.set(homeOrderPath(id), names);
                save();
                msg(player, "Home '" + name + "' saved. " + names.size() + "/2 homes used.");
                return true;
            }
            case "delhome" -> {
                String name = named(args);
                if (name == null) { msg(player, "Usage: /delhome <name>"); return true; }
                UUID id = player.getUniqueId();
                List<String> names = homeNames(id);
                if (!names.remove(name)) { msg(player, "You don't have a home named '" + name + "'."); return true; }
                data.set(homePath(id, name), null);
                data.set(homeOrderPath(id), names);
                save();
                msg(player, "Home '" + name + "' deleted.");
                return true;
            }
            case "homes" -> {
                if (args.length != 0) { msg(player, "Usage: /homes"); return true; }
                openHomes(player);
                return true;
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
                TpaQuote quote = tpaQuote(player, target);
                if (quote == null) return true;
                XZHEconomySystem economy = (XZHEconomySystem) plugin;
                if (economy.getCombinedBalance(player.getUniqueId()) + 1e-9 < quote.cost()) {
                    msg(player, "You need " + quote.cost() + " coins for this teleport. Your pocket + bank balance is too low.");
                    return true;
                }
                preparedByRequester.put(player.getUniqueId(), new PreparedTpa(target.getUniqueId(), quote));
                showTpaPreview(player, target, quote);
                return true;
            }
            case "tpasend" -> {
                PreparedTpa prepared = preparedByRequester.remove(player.getUniqueId());
                if (prepared == null) { msg(player, "Use /tpa <player> first to review a travel cost."); return true; }
                Player target = Bukkit.getPlayer(prepared.targetId());
                if (target == null || !target.isOnline()) { msg(player, "That player is no longer online."); return true; }
                // Keep the quote calculated when /tpa was entered. Movement after that
                // point must not change the displayed distance or travel cost.
                TpaQuote current = prepared.quote();
                Long oldExpiry = requestExpiry.get(target.getUniqueId());
                if (requestsByTarget.containsKey(target.getUniqueId()) && oldExpiry != null && oldExpiry > System.currentTimeMillis()) {
                    msg(player, "That player already has a pending teleport request.");
                    return true;
                }
                requestsByTarget.remove(target.getUniqueId());
                requestExpiry.remove(target.getUniqueId());
                quoteByTarget.remove(target.getUniqueId());
                requestsByTarget.put(target.getUniqueId(), player.getUniqueId());
                long expires = System.currentTimeMillis() + TPA_TTL;
                requestExpiry.put(target.getUniqueId(), expires);
                quoteByTarget.put(target.getUniqueId(), current);
                msg(player, "Teleport request sent to " + target.getName() + ".");
                net.md_5.bungee.api.chat.TextComponent details = new net.md_5.bungee.api.chat.TextComponent(
                        "§8[§6economy§8] §r§6Teleport request from §d" + player.getName() + " §8| §bDistance: §f" + current.distanceLabel()
                                + " §8| §eTravel cost: §6✦ " + current.cost() + " coins ");
                net.md_5.bungee.api.chat.TextComponent accept = new net.md_5.bungee.api.chat.TextComponent("§a[ ACCEPT ]");
                accept.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
                        net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND, "/tpaccept"));
                net.md_5.bungee.api.chat.TextComponent decline = new net.md_5.bungee.api.chat.TextComponent(" §c[ DECLINE ]");
                decline.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
                        net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND, "/tpdeny"));
                details.addExtra(accept);
                details.addExtra(decline);
                target.spigot().sendMessage(details);
                target.playSound(target.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.25f);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (requestExpiry.getOrDefault(target.getUniqueId(), 0L) == expires) {
                        requestsByTarget.remove(target.getUniqueId());
                        requestExpiry.remove(target.getUniqueId());
                        quoteByTarget.remove(target.getUniqueId());
                        Player onlineRequester = Bukkit.getPlayer(player.getUniqueId());
                        if (onlineRequester != null) msg(onlineRequester, "Your teleport request expired.");
                        if (target.isOnline()) msg(target, "Teleport request expired.");
                    }
                }, TPA_TTL / 50L);
                return true;
            }
            case "tpacancel" -> {
                preparedByRequester.remove(player.getUniqueId());
                msg(player, "Teleport preview cancelled.");
                return true;
            }
            case "tpaccept", "tpdeny" -> {
                UUID targetId = player.getUniqueId();
                UUID requesterId = requestsByTarget.remove(targetId);
                Long expiry = requestExpiry.remove(targetId);
                TpaQuote storedQuote = quoteByTarget.remove(targetId);
                if (requesterId == null || expiry == null || storedQuote == null || System.currentTimeMillis() > expiry) {
                    msg(player, "You have no active teleport request."); return true;
                }
                Player requester = Bukkit.getPlayer(requesterId);
                if (requester == null) { msg(player, "That player is no longer online."); return true; }
                if (cmd.equals("tpdeny")) {
                    msg(player, "Teleport request denied.");
                    msg(requester, player.getName() + " declined your teleport request.");
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.9f, 0.8f);
                    requester.playSound(requester.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.9f, 0.8f);
                    return true;
                }
                // Acceptance uses the exact quote captured when the requester typed /tpa,
                // regardless of movement by either player while the request was pending.
                TpaQuote current = storedQuote;
                XZHEconomySystem economy = (XZHEconomySystem) plugin;
                if (economy.getCombinedBalance(requesterId) + 1e-9 < current.cost()) {
                    msg(requester, "You no longer have enough pocket + bank coins for this teleport.");
                    msg(player, "Teleport cancelled because the requester cannot afford the travel cost.");
                    return true;
                }
                // Capture both balances so a failed teleport can roll back the exact debit.
                double pocketBefore = economy.getBalance(requesterId);
                double bankBefore = economy.getBankManager() == null
                        ? 0.0 : economy.getBankManager().getBalance(requesterId);
                if (!economy.spendAvailableFunds(requesterId, current.cost())) {
                    msg(requester, "Teleport cancelled because the travel fee could not be collected.");
                    msg(player, "Teleport cancelled because the requester could not pay the travel fee.");
                    return true;
                }
                boolean teleported = requester.teleport(player.getLocation());
                if (!teleported) {
                    // Refund to the original pocket/bank split; no fee remains charged.
                    economy.setBalance(requesterId, pocketBefore);
                    if (economy.getBankManager() != null) {
                        economy.getBankManager().setBalance(requesterId, bankBefore);
                    }
                    msg(requester, "Teleport failed; your full travel fee was refunded.");
                    msg(player, "Teleport failed; the travel fee was refunded.");
                    return true;
                }
                msg(player, "Teleport request accepted.");
                msg(requester, "Teleported to " + player.getName() + " for " + current.cost() + " coins.");
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.9f, 1.2f);
                requester.playSound(requester.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.9f, 1.2f);
                return true;
            }
            case "back" -> {
                if (args.length == 0) { requestBack(player); return true; }
                if (args.length == 2 && args[0].equalsIgnoreCase("accept")) {
                    acceptBack(player, args[1]); return true;
                }
                if (args.length == 2 && args[0].equalsIgnoreCase("decline")) {
                    declineBack(player, args[1]); return true;
                }
                msg(player, "Usage: /back"); return true;
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
                Bukkit.broadcastMessage("§8[§6economy§8] §e" + player.getName() + " §f" + (value ? "is now AFK." : "is no longer AFK."));
                return true;
            }
            case "helpxzh" -> {
                net.md_5.bungee.api.chat.TextComponent link = new net.md_5.bungee.api.chat.TextComponent("§6[SERVER] §eClick here to open the Server guide");
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
                plugin.tellPublic(sender, ChatColor.GOLD + "Economy commands:");
                plugin.tellPublic(sender, ChatColor.WHITE + "/balance, /pay, /shop, /books, /loan, /quests, /deathinventory");
                plugin.tellPublic(sender, ChatColor.WHITE + "/spawn, /home, /warp, /tpa, /tpaccept, /tpdeny, /back, /rtp");
                plugin.tellPublic(sender, ChatColor.WHITE + "/msg, /reply, /ignore, /afk, /near, /list, /top, /kit");
                if (sender.hasPermission("xzh.admin")) plugin.tellPublic(sender, ChatColor.WHITE + "/xzhadmin, /eco, /shopadmin, /setspawn, /setwarp, /delwarp");
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
                plugin.tellPublic(sender, ChatColor.GOLD + "Online (" + Bukkit.getOnlinePlayers().size() + "): " + ChatColor.WHITE + names);
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
                XZHEconomySystem economy = (XZHEconomySystem) plugin;
                final double fee = 30.0;
                if (economy.getCombinedBalance(player.getUniqueId()) + 1e-9 < fee) {
                    msg(player, "Random teleport costs 30 coins. You need more pocket/bank funds.");
                    return true;
                }
                long now = System.currentTimeMillis();
                long ready = rtpCooldown.getOrDefault(player.getUniqueId(), 0L);
                if (now < ready) { msg(player, "Random teleport cooldown: " + ((ready - now + 999) / 1000) + " seconds."); return true; }
                int radius = Math.max(100, plugin.getConfig().getInt("utilities.rtp-radius", 3000));
                Location center = data.getLocation("spawn");
                if (center == null) center = player.getWorld().getSpawnLocation();
                World world = center.getWorld();
                if (world == null || world.getEnvironment() != player.getWorld().getEnvironment()) {
                    msg(player, "Random teleport is only available in your current dimension, and no valid center was found.");
                    return true;
                }
                java.util.concurrent.ThreadLocalRandom random = java.util.concurrent.ThreadLocalRandom.current();
                for (int attempt = 0; attempt < 50; attempt++) {
                    int x = center.getBlockX() + random.nextInt(-radius, radius + 1);
                    int z = center.getBlockZ() + random.nextInt(-radius, radius + 1);
                    Block floor = world.getHighestBlockAt(x, z);
                    Material ground = floor.getType();
                    Material feet = floor.getRelative(0, 1, 0).getType();
                    Material head = floor.getRelative(0, 2, 0).getType();
                    if (!ground.isSolid() || ground == Material.MAGMA_BLOCK || ground == Material.CACTUS
                            || ground == Material.CAMPFIRE || ground == Material.SOUL_CAMPFIRE
                            || ground == Material.FIRE || ground == Material.SOUL_FIRE
                            || ground == Material.LAVA || feet != Material.AIR || head != Material.AIR) continue;
                    Location destination = new Location(world, x + 0.5, floor.getY() + 1, z + 0.5,
                            player.getLocation().getYaw(), player.getLocation().getPitch());
                    if (!player.teleport(destination)) {
                        msg(player, "Teleport failed; no fee was charged.");
                        return true;
                    }
                    if (!economy.spendAvailableFunds(player.getUniqueId(), fee)) {
                        msg(player, "Teleport succeeded, but payment could not be collected. Contact an admin.");
                        return true;
                    }
                    long cooldownSeconds = Math.max(1L, plugin.getConfig().getLong("utilities.rtp-cooldown-seconds", 600));
                    rtpCooldown.put(player.getUniqueId(), now + cooldownSeconds * 1000L);
                    msg(player, "Randomly teleported safely for 30 coins. Cooldown: " + Math.max(1L, cooldownSeconds / 60L) + " minutes.");
                    return true;
                }
                msg(player, "Could not find a safe location. No coins were charged; try again.");
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

    private void requestBack(Player player) {
        UUID id = player.getUniqueId();
        Location destination = data.getLocation("back." + id);
        if (destination == null || destination.getWorld() == null) {
            msg(player, "No saved death location.");
            return;
        }
        if (!isSupportedBackRoute(player.getLocation(), destination)) {
            msg(player, "A direct Nether-to-End return is not configured. No coins were charged.");
            return;
        }
        int cost = calculateBackCost(player.getLocation(), destination);
        XZHEconomySystem economy = (XZHEconomySystem) plugin;
        if (economy.getCombinedBalance(id) + 1e-9 < cost) {
            msg(player, "You need " + money(cost) + " coins to use /back. Your pocket + bank balance is too low.");
            return;
        }
        String token = UUID.randomUUID().toString().substring(0, 8);
        PendingBack pending = new PendingBack(destination.clone(), cost, token, System.currentTimeMillis());
        pendingBacks.put(id, pending);

        String route = backRouteLabel(player.getLocation(), destination);
        net.md_5.bungee.api.chat.TextComponent details = new net.md_5.bungee.api.chat.TextComponent(
                "§8[§6economy§8] §r§6Return to your last death location? §8| §b" + route
                        + " §8| §eCost: §6✦ " + money(cost) + " coins ");
        net.md_5.bungee.api.chat.TextComponent accept = new net.md_5.bungee.api.chat.TextComponent("§a[ ACCEPT ]");
        accept.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
                net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND, "/back accept " + token));
        net.md_5.bungee.api.chat.TextComponent decline = new net.md_5.bungee.api.chat.TextComponent(" §c[ DECLINE ]");
        decline.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
                net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND, "/back decline " + token));
        details.addExtra(accept);
        details.addExtra(decline);
        player.spigot().sendMessage(details);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.9f, 1.2f);
    }

    private void acceptBack(Player player, String token) {
        UUID id = player.getUniqueId();
        PendingBack pending = pendingBacks.get(id);
        if (pending == null || !pending.token().equals(token)
                || System.currentTimeMillis() - pending.createdAt() > 30_000L) {
            pendingBacks.remove(id);
            msg(player, "That /back confirmation has expired. Use /back again.");
            return;
        }
        pendingBacks.remove(id);
        if (pending.destination().getWorld() == null) {
            msg(player, "Your saved death world is unavailable. No coins were charged.");
            return;
        }
        XZHEconomySystem economy = (XZHEconomySystem) plugin;
        if (economy.getCombinedBalance(id) + 1e-9 < pending.cost()) {
            msg(player, "You no longer have enough pocket + bank coins for /back.");
            return;
        }
        BackCountdown previous = backCountdowns.remove(id);
        if (previous != null && previous.task != null) previous.task.cancel();
        startBackCountdown(player, pending);
    }

    private void declineBack(Player player, String token) {
        PendingBack pending = pendingBacks.get(player.getUniqueId());
        if (pending == null || !pending.token().equals(token)) {
            msg(player, "That /back confirmation has expired.");
            return;
        }
        pendingBacks.remove(player.getUniqueId());
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.8f);
        msg(player, "Return cancelled. No coins were charged.");
    }

    private boolean isSupportedBackRoute(Location from, Location to) {
        World.Environment a = from.getWorld().getEnvironment();
        World.Environment b = to.getWorld().getEnvironment();
        if (a == b) return true;
        return (a == World.Environment.NORMAL && (b == World.Environment.NETHER || b == World.Environment.THE_END))
                || (b == World.Environment.NORMAL && (a == World.Environment.NETHER || a == World.Environment.THE_END));
    }

    private int calculateBackCost(Location from, Location to) {
        World.Environment fromEnvironment = from.getWorld().getEnvironment();
        World.Environment toEnvironment = to.getWorld().getEnvironment();
        if (fromEnvironment != toEnvironment) {
            if (fromEnvironment == World.Environment.THE_END || toEnvironment == World.Environment.THE_END) {
                return Math.max(0, plugin.getConfig().getInt("utilities.back-end-cost", 666));
            }
            if (fromEnvironment == World.Environment.NETHER || toEnvironment == World.Environment.NETHER) {
                return Math.max(0, plugin.getConfig().getInt("utilities.back-nether-cost", 333));
            }
        }
        double dx = from.getX() - to.getX();
        double dy = from.getY() - to.getY();
        double dz = from.getZ() - to.getZ();
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        int interval = Math.max(1, plugin.getConfig().getInt("utilities.back-distance-blocks-per-tier", 1000));
        int price = Math.max(0, plugin.getConfig().getInt("utilities.back-distance-cost-per-tier", 50));
        long tiers = (long) Math.ceil(distance / interval);
        return (int) Math.min(Integer.MAX_VALUE, tiers * price);
    }

    private String backRouteLabel(Location from, Location to) {
        World.Environment fromEnvironment = from.getWorld().getEnvironment();
        World.Environment toEnvironment = to.getWorld().getEnvironment();
        if (fromEnvironment != toEnvironment) {
            if (fromEnvironment == World.Environment.THE_END || toEnvironment == World.Environment.THE_END) return "End travel";
            if (fromEnvironment == World.Environment.NETHER || toEnvironment == World.Environment.NETHER) return "Nether travel";
        }
        double dx = from.getX() - to.getX(), dy = from.getY() - to.getY(), dz = from.getZ() - to.getZ();
        return String.format(Locale.US, "%,.0f blocks", Math.sqrt(dx * dx + dy * dy + dz * dz));
    }

    private void startBackCountdown(Player player, PendingBack quote) {
        UUID id = player.getUniqueId();
        BackCountdown session = new BackCountdown(player.getLocation().clone(), quote);
        backCountdowns.put(id, session);
        msg(player, "Return accepted. Stay still for 3 seconds; movement cancels the teleport.");
        session.task = new BukkitRunnable() {
            private int seconds = Math.max(1, plugin.getConfig().getInt("utilities.back-countdown-seconds", 3));
            @Override public void run() {
                if (!player.isOnline()) { cancelBackCountdown(id, null); return; }
                if (seconds > 0) {
                    player.sendTitle("§e" + seconds, "§cMove to cancel", 0, 20, 0);
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.9f,
                            1.0f + (3 - seconds) * 0.18f);
                    seconds--;
                    return;
                }
                finishBackCountdown(player, session);
            }
        };
        session.task.runTaskTimer(plugin, 0L, 20L);
    }

    private void finishBackCountdown(Player player, BackCountdown session) {
        UUID id = player.getUniqueId();
        if (backCountdowns.get(id) != session) return;
        backCountdowns.remove(id);
        if (session.task != null) session.task.cancel();
        player.resetTitle();
        Location start = session.start;
        if (player.getWorld() != start.getWorld() || player.getLocation().distanceSquared(start) > 0.0001) {
            msg(player, "Return cancelled because you moved. No coins were charged.");
            return;
        }
        Location destination = session.quote.destination();
        if (destination.getWorld() == null) {
            msg(player, "Your saved death world is unavailable. No coins were charged.");
            return;
        }
        XZHEconomySystem economy = (XZHEconomySystem) plugin;
        if (economy.getCombinedBalance(id) + 1e-9 < session.quote.cost()) {
            msg(player, "Return cancelled because you no longer have enough coins.");
            return;
        }
        if (!player.teleport(destination)) {
            msg(player, "Teleport failed; no coins were charged.");
            return;
        }
        if (!economy.spendAvailableFunds(id, session.quote.cost())) {
            player.teleport(start);
            msg(player, "Payment could not be collected, so your return was cancelled and you were moved back.");
            return;
        }
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.9f, 1.2f);
        msg(player, "Returned to your last death location for " + money(session.quote.cost()) + " coins.");
    }

    private void cancelBackCountdown(UUID id, String reason) {
        BackCountdown session = backCountdowns.remove(id);
        if (session == null) return;
        if (session.task != null) session.task.cancel();
        Player player = Bukkit.getPlayer(id);
        if (player != null && player.isOnline()) {
            player.resetTitle();
            if (reason != null) msg(player, reason);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBackCountdownMove(PlayerMoveEvent event) {
        BackCountdown session = backCountdowns.get(event.getPlayer().getUniqueId());
        if (session == null || event.getTo() == null) return;
        Location from = event.getFrom(), to = event.getTo();
        if (from.getWorld() != to.getWorld() || from.distanceSquared(to) > 0.0001) {
            cancelBackCountdown(event.getPlayer().getUniqueId(), "Return cancelled because you moved. No coins were charged.");
        }
    }

    @EventHandler
    public void onBackQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        pendingBacks.remove(id);
        cancelBackCountdown(id, null);
    }

    private void acceptSpawn(Player player, String token) {
        UUID id = player.getUniqueId();
        PendingSpawn pending = pendingSpawns.get(id);
        if (pending == null || !pending.token().equals(token)
                || System.currentTimeMillis() - pending.createdAt() > 30_000L) {
            pendingSpawns.remove(id);
            msg(player, "That spawn confirmation has expired. Use /spawn again.");
            return;
        }
        pendingSpawns.remove(id);
        if (pending.destination().getWorld() == null) {
            msg(player, "The server spawn location is unavailable. No coins were charged.");
            return;
        }
        XZHEconomySystem economy = (XZHEconomySystem) plugin;
        if (economy.getCombinedBalance(id) + 1e-9 < pending.quote().cost()) {
            msg(player, "You no longer have enough pocket + bank coins for /spawn. No coins were charged.");
            return;
        }
        startSpawnCountdown(player, pending.destination(), pending.quote());
    }

    private void rejectSpawn(Player player, String token) {
        PendingSpawn pending = pendingSpawns.get(player.getUniqueId());
        if (pending == null || !pending.token().equals(token)) {
            msg(player, "That spawn confirmation has expired.");
            return;
        }
        pendingSpawns.remove(player.getUniqueId());
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.8f);
        msg(player, "Spawn teleport rejected. No coins were charged.");
    }

    private void startSpawnCountdown(Player player, Location destination, TpaQuote initialQuote) {
        UUID id = player.getUniqueId();
        BukkitRunnable previous = spawnCountdowns.remove(id);
        if (previous != null) previous.cancel();

        destination = destination.clone();
        if (destination.getWorld() == null) {
            msg(player, "The server spawn location is unavailable.");
            return;
        }

        XZHEconomySystem economy = (XZHEconomySystem) plugin;
        if (economy.getCombinedBalance(id) + 1e-9 < initialQuote.cost()) {
            msg(player, "You no longer have enough pocket + bank coins for /spawn.");
            return;
        }

        final Location confirmedDestination = destination;
        player.sendTitle("§6SPAWN", "§e3", 0, 20, 0);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.0f);
        BukkitRunnable task = new BukkitRunnable() {
            private int seconds = 2;

            @Override public void run() {
                if (!player.isOnline()) {
                    spawnCountdowns.remove(id);
                    cancel();
                    return;
                }
                if (seconds > 0) {
                    player.sendTitle("§6SPAWN", "§e" + seconds, 0, 20, 0);
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.0f);
                    seconds--;
                    return;
                }

                if (confirmedDestination.getWorld() == null) {
                    msg(player, "The server spawn location is unavailable. No fee was charged.");
                    spawnCountdowns.remove(id);
                    cancel();
                    return;
                }
                if (economy.getCombinedBalance(id) + 1e-9 < initialQuote.cost()) {
                    msg(player, "Spawn teleport cancelled: you no longer have enough pocket + bank coins.");
                    spawnCountdowns.remove(id);
                    cancel();
                    return;
                }

                // Remove the pending marker before teleporting; Bukkit fires a PlayerMoveEvent for teleports.
                spawnCountdowns.remove(id);
                boolean teleported = player.teleport(confirmedDestination);
                if (!teleported) {
                    msg(player, "Spawn teleport failed; no fee was charged.");
                } else if (!economy.spendAvailableFunds(id, initialQuote.cost())) {
                    msg(player, "Spawn teleport succeeded, but payment could not be collected. Please contact an admin.");
                } else {
                    msg(player, "Teleported to spawn for " + money(initialQuote.cost()) + " coins.");
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.9f, 1.2f);
                }
                spawnCountdowns.remove(id);
                cancel();
            }
        };
        spawnCountdowns.put(id, task);
        task.runTaskTimer(plugin, 20L, 20L);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpawnCountdownMove(PlayerMoveEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        if (!spawnCountdowns.containsKey(id) || event.getTo() == null) return;
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getWorld().equals(to.getWorld())
                && Double.compare(from.getX(), to.getX()) == 0
                && Double.compare(from.getY(), to.getY()) == 0
                && Double.compare(from.getZ(), to.getZ()) == 0) return;

        BukkitRunnable task = spawnCountdowns.remove(id);
        if (task != null) task.cancel();
        event.getPlayer().sendTitle("§cSPAWN", "§cCancelled", 0, 20, 5);
        msg(event.getPlayer(), "Spawn teleport cancelled because you moved.");
    }

    @EventHandler
    public void onSpawnCountdownQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        pendingSpawns.remove(id);
        pendingHomes.remove(id);
        BukkitRunnable homeTask = homeCountdowns.remove(id);
        homeCountdownStarts.remove(id);
        if (homeTask != null) homeTask.cancel();
        BukkitRunnable task = spawnCountdowns.remove(id);
        if (task != null) task.cancel();
    }

    private void requestHomeConfirmation(Player player, String name, Location destination) {
        TpaQuote quote = quoteTravel(player, destination, "Teleporting to this home between these world types is not supported.");
        if (quote == null) return;
        XZHEconomySystem economy = (XZHEconomySystem) plugin;
        if (economy.getCombinedBalance(player.getUniqueId()) + 1e-9 < quote.cost()) {
            msg(player, "You need " + money(quote.cost()) + " coins for this home teleport. Your pocket + bank balance is too low.");
            return;
        }
        UUID id = player.getUniqueId();
        String token = UUID.randomUUID().toString().substring(0, 8);
        PendingHome pending = new PendingHome(name, destination.clone(), quote, token, System.currentTimeMillis());
        pendingHomes.put(id, pending);
        net.md_5.bungee.api.chat.TextComponent details = new net.md_5.bungee.api.chat.TextComponent(
                "§8[§6home§8] §r§6Teleport to §e" + name + " §8| §bDistance: §f" + quote.distanceLabel()
                        + " §8| §eCost: §6✦ " + money(quote.cost()) + " coins ");
        net.md_5.bungee.api.chat.TextComponent accept = new net.md_5.bungee.api.chat.TextComponent("§a[ ACCEPT ]");
        accept.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
                net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND, "/home accept " + token));
        net.md_5.bungee.api.chat.TextComponent reject = new net.md_5.bungee.api.chat.TextComponent(" §c[ REJECT ]");
        reject.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
                net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND, "/home reject " + token));
        details.addExtra(accept);
        details.addExtra(reject);
        player.spigot().sendMessage(details);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.9f, 1.2f);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            PendingHome current = pendingHomes.get(id);
            if (current != null && current.token().equals(token)) {
                pendingHomes.remove(id);
                Player online = Bukkit.getPlayer(id);
                if (online != null) msg(online, "Home confirmation expired. Use /home again.");
            }
        }, 600L);
    }

    private void acceptHome(Player player, String token) {
        UUID id = player.getUniqueId();
        PendingHome pending = pendingHomes.get(id);
        if (pending == null || !pending.token().equals(token)
                || System.currentTimeMillis() - pending.createdAt() > 30_000L) {
            pendingHomes.remove(id);
            msg(player, "That home confirmation has expired. Use /home again.");
            return;
        }
        pendingHomes.remove(id);
        if (pending.destination().getWorld() == null) {
            msg(player, "That home world is unavailable. No coins were charged.");
            return;
        }
        XZHEconomySystem economy = (XZHEconomySystem) plugin;
        if (economy.getCombinedBalance(id) + 1e-9 < pending.quote().cost()) {
            msg(player, "You no longer have enough pocket + bank coins. No coins were charged.");
            return;
        }
        startHomeCountdown(player, pending);
    }

    private void rejectHome(Player player, String token) {
        PendingHome pending = pendingHomes.get(player.getUniqueId());
        if (pending == null || !pending.token().equals(token)) {
            msg(player, "That home confirmation has expired.");
            return;
        }
        pendingHomes.remove(player.getUniqueId());
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.8f);
        msg(player, "Home teleport rejected. No coins were charged.");
    }

    private void startHomeCountdown(Player player, PendingHome pending) {
        UUID id = player.getUniqueId();
        BukkitRunnable previous = homeCountdowns.remove(id);
        if (previous != null) previous.cancel();
        Location start = player.getLocation().clone();
        homeCountdownStarts.put(id, start);
        BukkitRunnable task = new BukkitRunnable() {
            private int seconds = 3;
            @Override public void run() {
                if (!player.isOnline()) {
                    homeCountdowns.remove(id);
                    homeCountdownStarts.remove(id);
                    cancel();
                    return;
                }
                if (seconds > 0) {
                    player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                            new net.md_5.bungee.api.chat.TextComponent("§6Home teleport in §e" + seconds + "s §8• §7Move to cancel"));
                    seconds--;
                    return;
                }
                homeCountdowns.remove(id);
                homeCountdownStarts.remove(id);
                Location destination = pending.destination();
                XZHEconomySystem economy = (XZHEconomySystem) plugin;
                if (destination.getWorld() == null) {
                    msg(player, "Home teleport cancelled: destination world is unavailable. No coins were charged.");
                } else if (economy.getCombinedBalance(id) + 1e-9 < pending.quote().cost()) {
                    msg(player, "Home teleport cancelled: you no longer have enough coins. No coins were charged.");
                } else if (!player.teleport(destination)) {
                    msg(player, "Home teleport failed. No coins were charged.");
                } else if (!economy.spendAvailableFunds(id, pending.quote().cost())) {
                    player.teleport(start);
                    msg(player, "Payment failed; you were returned to your starting location. No coins were charged.");
                } else {
                    player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                            new net.md_5.bungee.api.chat.TextComponent("§aTeleported to " + pending.name()));
                    msg(player, "Teleported to '" + pending.name() + "' for " + money(pending.quote().cost()) + " coins.");
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.9f, 1.2f);
                }
                cancel();
            }
        };
        homeCountdowns.put(id, task);
        task.runTaskTimer(plugin, 0L, 20L);
    }

    @EventHandler(ignoreCancelled = true)
    public void onHomeCountdownMove(PlayerMoveEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        if (!homeCountdowns.containsKey(id) || event.getTo() == null) return;
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getWorld() == to.getWorld()
                && Double.compare(from.getX(), to.getX()) == 0
                && Double.compare(from.getY(), to.getY()) == 0
                && Double.compare(from.getZ(), to.getZ()) == 0) return;
        BukkitRunnable task = homeCountdowns.remove(id);
        homeCountdownStarts.remove(id);
        if (task != null) task.cancel();
        msg(event.getPlayer(), "Home teleport cancelled because you moved.");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onHomeCommandPreprocess(PlayerCommandPreprocessEvent event) {
        String raw = event.getMessage();
        if (raw == null || !raw.startsWith("/")) return;
        String[] parts = raw.substring(1).trim().split("\\s+");
        if (parts.length == 0 || parts[0].isBlank()) return;

        String root = parts[0].toLowerCase(Locale.ROOT);
        int namespace = root.indexOf(':');
        if (namespace >= 0) root = root.substring(namespace + 1);

        if (root.equals("home") || root.equals("sethome") || root.equals("delhome") || root.equals("homes")) {
            event.setCancelled(true);
            String[] args = java.util.Arrays.copyOfRange(parts, 1, parts.length);
            handle(event.getPlayer(), root, args);
        }
    }

    private Location bedHome(Player player) {
        Location bed = player.getBedSpawnLocation();
        if (bed == null || bed.getWorld() == null) return null;
        // Use Bukkit's vanilla respawn point; do not maintain or override a plugin bed-home record.
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
        // Save the location for /back. The global death announcement is handled once
        // by XZH Survival so this listener does not send a second death message.
        data.set("back." + event.getEntity().getUniqueId(), event.getEntity().getLocation());
        save();
    }

    private TpaQuote tpaQuote(Player requester, Player target) {
        return quoteTravel(requester, target.getLocation(),
                "Teleport requests between these world types are not supported.");
    }

    private TpaQuote quoteTravel(Player requester, Location destination, String unsupportedMessage) {
        if (destination == null || destination.getWorld() == null) {
            msg(requester, "Teleport destination is unavailable.");
            return null;
        }
        World.Environment from = requester.getWorld().getEnvironment();
        World.Environment to = destination.getWorld().getEnvironment();

        // Preserve the exact travel pricing already used by /tpa in Build #1132.
        if (from != to) {
            int cost;
            if ((from == World.Environment.NORMAL && to == World.Environment.NETHER)
                    || (from == World.Environment.NETHER && to == World.Environment.NORMAL)) {
                cost = 200;
            } else if ((from == World.Environment.NORMAL && to == World.Environment.THE_END)
                    || (from == World.Environment.THE_END && to == World.Environment.NORMAL)) {
                cost = 333;
            } else if ((from == World.Environment.NETHER && to == World.Environment.THE_END)
                    || (from == World.Environment.THE_END && to == World.Environment.NETHER)) {
                cost = 222;
            } else {
                msg(requester, unsupportedMessage);
                return null;
            }
            return new TpaQuote(-1, cost, true, "cross-dimension");
        }

        Location requesterLocation = requester.getLocation();
        double dx = requesterLocation.getX() - destination.getX();
        double dy = requesterLocation.getY() - destination.getY();
        double dz = requesterLocation.getZ() - destination.getZ();
        double exactDistance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        int distance = (int) Math.ceil(exactDistance);
        int cost = (int) Math.ceil(distance / 1000.0) * 33;
        return new TpaQuote(distance, cost, false, String.format(Locale.US, "%,d blocks", distance));
    }

    private boolean sameQuote(TpaQuote first, TpaQuote second) {
        return first.distance() == second.distance() && first.cost() == second.cost()
                && first.netherFixed() == second.netherFixed();
    }

    private void showTpaPreview(Player requester, Player target, TpaQuote quote) {
        net.md_5.bungee.api.chat.TextComponent line = new net.md_5.bungee.api.chat.TextComponent(
                "§8[§6economy§8] §r§6Teleport to §d@" + target.getName() + " §8| §bDistance: §f" + quote.distanceLabel()
                        + " §8| §eTravel cost: §6✦ " + quote.cost() + " coins");
        net.md_5.bungee.api.chat.TextComponent send = new net.md_5.bungee.api.chat.TextComponent("§a[ SEND ]");
        send.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
                net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND, "/tpasend"));
        net.md_5.bungee.api.chat.TextComponent decline = new net.md_5.bungee.api.chat.TextComponent(" §c[ DECLINE ]");
        decline.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
                net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND, "/tpacancel"));
        requester.spigot().sendMessage(line);
        requester.spigot().sendMessage(send, decline);
    }

    private String homePath(UUID id, String name) { return "named-homes." + id + "." + name; }
    private String homeOrderPath(UUID id) { return "named-home-order." + id; }

    private List<String> homeNames(UUID id) {
        List<String> result = new java.util.ArrayList<>();
        for (String name : data.getStringList(homeOrderPath(id))) {
            String normalized = name.toLowerCase(Locale.ROOT);
            if (data.getLocation(homePath(id, normalized)) != null && !result.contains(normalized)) result.add(normalized);
        }
        return result;
    }

    private String firstHomeName(UUID id) {
        List<String> names = homeNames(id);
        return names.isEmpty() ? null : names.get(0);
    }

    public List<String> homeTabCompletions(Player player, String prefix) {
        String filter = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        return homeNames(player.getUniqueId()).stream().filter(name -> name.startsWith(filter)).toList();
    }

    private void openHomes(Player player) {
        Inventory inventory = Bukkit.createInventory(null, 27, "§8Your Homes");
        List<String> names = homeNames(player.getUniqueId());
        int[] slots = {11, 15};
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            Location location = data.getLocation(homePath(player.getUniqueId(), name));
            ItemStack icon = new ItemStack(Material.RED_BED);
            ItemMeta meta = icon.getItemMeta();
            meta.setDisplayName("§e" + name);
            List<String> lore = new java.util.ArrayList<>();
            lore.add("§7Click to request teleport");
            if (location != null && location.getWorld() != null) {
                lore.add("§8" + location.getWorld().getName());
                lore.add("§8" + location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ());
            }
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(homeActionKey, PersistentDataType.STRING, name);
            icon.setItemMeta(meta);
            inventory.setItem(slots[i], icon);
        }
        if (names.isEmpty()) {
            ItemStack info = new ItemStack(Material.PAPER);
            ItemMeta meta = info.getItemMeta();
            meta.setDisplayName("§7No named homes saved");
            meta.setLore(List.of("§8Use /sethome <name>", "§8Your vanilla bed is the /home fallback."));
            info.setItemMeta(meta);
            inventory.setItem(13, info);
        }
        player.openInventory(inventory);
    }

    @EventHandler
    public void onHomesInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!ChatColor.stripColor(event.getView().getTitle()).equals("Your Homes")) return;
        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        String name = clicked.getItemMeta().getPersistentDataContainer().get(homeActionKey, PersistentDataType.STRING);
        if (name == null) return;
        player.closeInventory();
        Location destination = data.getLocation(homePath(player.getUniqueId(), name));
        if (destination == null || destination.getWorld() == null) {
            msg(player, "That saved home is unavailable.");
            return;
        }
        requestHomeConfirmation(player, name, destination);
    }

    private String money(double amount) {
        return String.format(Locale.US, "%.2f", amount).replaceAll("\\.00$", "");
    }

    private String named(String[] args) {
        if (args.length != 1 || !args[0].matches("[A-Za-z0-9_-]{1,24}")) return null;
        return args[0].toLowerCase(Locale.ROOT);
    }
    private void msg(Player player, String text) {
        plugin.tellPublic(player, text);
    }
    private void save() {
        try { data.save(file); }
        catch (IOException e) { plugin.getLogger().severe("Could not save locations.yml: " + e.getMessage()); }
    }
}
