package com.xzh.economy;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Short-lived, recipient-confirmed player money transfers and requests. */
public final class MoneyRequestManager implements Listener {
    private enum Kind { SEND, ASK }
    private record Pending(Kind kind, UUID requester, UUID target, long amount, long expiresAt) {}

    private final XZHEconomySystem plugin;
    private final NamespacedKey actionKey;
    private final Map<UUID, Pending> pendingByRecipient = new HashMap<>();
    private static final long TIMEOUT_MS = 60_000L;

    public MoneyRequestManager(XZHEconomySystem plugin) {
        this.plugin = plugin;
        actionKey = new NamespacedKey(plugin, "money_request_action");
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            pendingByRecipient.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);
        }, 20L * 10L, 20L * 10L);
    }

    public boolean handle(Player requester, String command, String[] args) {
        if (args.length != 2) {
            plugin.tellPublic(requester, "Usage: /" + command + " <player> <whole-coin amount>");
            return true;
        }
        String targetName = args[0].startsWith("@") ? args[0].substring(1) : args[0];
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null || target.equals(requester)) {
            plugin.tellPublic(requester, "Choose another player who is online.");
            return true;
        }
        long amount;
        try {
            amount = Long.parseLong(args[1]);
        } catch (NumberFormatException ex) {
            plugin.tellPublic(requester, "Enter a whole-number coin amount.");
            return true;
        }
        double minimum = Math.max(1.0, plugin.getConfig().getDouble("minimum-pay", 1.0));
        double maximum = plugin.getConfig().getDouble("maximum-pay", 1000000.0);
        if (amount <= 0 || amount < minimum || amount > maximum) {
            plugin.tellPublic(requester, "Amount must be between " + (long) minimum + " and " + (long) maximum + " coins.");
            return true;
        }

        Kind kind = command.equalsIgnoreCase("askmoney") ? Kind.ASK : Kind.SEND;
        UUID recipientId = target.getUniqueId();
        Pending existing = pendingByRecipient.get(recipientId);
        if (existing != null && existing.expiresAt() > System.currentTimeMillis()) {
            plugin.tellPublic(requester, target.getName() + " already has a pending money request. Try again shortly.");
            return true;
        }

        UUID payerId = kind == Kind.SEND ? requester.getUniqueId() : target.getUniqueId();
        if (plugin.getCombinedBalance(payerId) < amount) {
            plugin.tellPublic(requester, kind == Kind.SEND
                    ? "You don't have enough combined pocket and bank funds for this transfer."
                    : target.getName() + " may not have enough funds; they can still review the request.");
            if (kind == Kind.SEND) return true;
        }

        Pending pending = new Pending(kind, requester.getUniqueId(), target.getUniqueId(),
                amount, System.currentTimeMillis() + TIMEOUT_MS);
        pendingByRecipient.put(recipientId, pending);
        openPrompt(target, requester, pending);
        requester.playSound(requester.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_CHIME, 0.45f, 1.35f);
        target.playSound(target.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 0.45f, 1.45f);
        plugin.tellPublic(requester, (kind == Kind.SEND ? "Transfer request sent to " : "Money request sent to ")
                + target.getName() + " for " + amount + " coins. It expires in 60 seconds.");
        return true;
    }

    private void openPrompt(Player recipient, Player requester, Pending pending) {
        String actionText = pending.kind() == Kind.SEND ? "wants to send you" : "asks you to send";
        net.md_5.bungee.api.chat.TextComponent prompt = new net.md_5.bungee.api.chat.TextComponent(
                "§6[XZH] §e" + requester.getName() + " " + actionText + " " + pending.amount() + " coins. ");
        net.md_5.bungee.api.chat.TextComponent accept = new net.md_5.bungee.api.chat.TextComponent("§a[ACCEPT]");
        accept.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
                net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND, "/moneyresponse accept"));
        net.md_5.bungee.api.chat.TextComponent decline = new net.md_5.bungee.api.chat.TextComponent(" §c[DECLINE]");
        decline.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
                net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND, "/moneyresponse decline"));
        prompt.addExtra(accept);
        prompt.addExtra(decline);
        prompt.addExtra(new net.md_5.bungee.api.chat.TextComponent(" §7(expires in 60 seconds)"));
        recipient.spigot().sendMessage(prompt);
    }

    private void add(Inventory inv, int slot, Material material, String name, String action) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        GuiIcons.apply(meta, action, "", name);
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
        item.setItemMeta(meta);
        inv.setItem(slot, item);
    }

    public boolean handleResponse(Player recipient, String[] args) {
        if (args.length != 1 || !(args[0].equalsIgnoreCase("accept") || args[0].equalsIgnoreCase("decline"))) {
            plugin.tellPublic(recipient, "Usage: /moneyresponse <accept|decline>");
            return true;
        }
        Pending pending = pendingByRecipient.get(recipient.getUniqueId());
        if (pending == null || pending.expiresAt() <= System.currentTimeMillis()) {
            pendingByRecipient.remove(recipient.getUniqueId());
            plugin.tellPublic(recipient, "There is no active money request; it may have expired.");
            return true;
        }
        pendingByRecipient.remove(recipient.getUniqueId());
        Player requester = Bukkit.getPlayer(pending.requester());
        Player target = Bukkit.getPlayer(pending.target());
        if (args[0].equalsIgnoreCase("decline")) {
            if (requester != null) plugin.tellPublic(requester, recipient.getName() + " declined the money request.");
            plugin.tellPublic(recipient, "Money request declined.");
            return true;
        }
        if (requester == null || target == null) {
            plugin.tellPublic(recipient, "The other player must remain online. No money was transferred.");
            if (requester != null) plugin.tellPublic(requester, "The request could not be completed because a player went offline.");
            return true;
        }
        Player payer = pending.kind() == Kind.SEND ? requester : target;
        Player payee = pending.kind() == Kind.SEND ? target : requester;
        long amount = pending.amount();
        if (plugin.getCombinedBalance(payer.getUniqueId()) < amount
                || !plugin.spendAvailableFunds(payer.getUniqueId(), amount)) {
            plugin.tellPublic(recipient, "The payer no longer has enough available funds. Nothing was transferred.");
            plugin.tellPublic(payer, "Transfer failed: not enough available funds.");
            return true;
        }
        plugin.setBalance(payee.getUniqueId(), plugin.getBalance(payee.getUniqueId()) + amount);
        plugin.tellPublic(payer, "Transferred " + amount + " coins to " + payee.getName() + ".");
        plugin.tellPublic(payee, "Received " + amount + " coins from " + payer.getName() + ".");
        return true;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player recipient)) return;
        String title = ChatColor.stripColor(event.getView().getTitle());
        if (!"XZH Transfer Request".equals(title) && !"XZH Money Request".equals(title)) return;
        event.setCancelled(true);
        ItemStack item = event.getCurrentItem();
        if (item == null || !item.hasItemMeta()) return;
        String action = item.getItemMeta().getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
        if (action == null || !(action.equals("accept") || action.equals("decline"))) return;

        Pending pending = pendingByRecipient.get(recipient.getUniqueId());
        if (pending == null || pending.expiresAt() <= System.currentTimeMillis()) {
            pendingByRecipient.remove(recipient.getUniqueId());
            plugin.tellPublic(recipient, "This money request has expired.");
            recipient.closeInventory();
            return;
        }
        pendingByRecipient.remove(recipient.getUniqueId());
        recipient.closeInventory();
        Player requester = Bukkit.getPlayer(pending.requester());
        Player target = Bukkit.getPlayer(pending.target());
        if (action.equals("decline")) {
            if (requester != null) plugin.tellPublic(requester, recipient.getName() + " declined the money request.");
            plugin.tellPublic(recipient, "Money request declined.");
            return;
        }
        if (requester == null || target == null) {
            plugin.tellPublic(recipient, "The other player must remain online. No money was transferred.");
            if (requester != null) plugin.tellPublic(requester, "The request could not be completed because a player went offline.");
            return;
        }

        Player payer = pending.kind() == Kind.SEND ? requester : target;
        Player payee = pending.kind() == Kind.SEND ? target : requester;
        long amount = pending.amount();
        if (plugin.getCombinedBalance(payer.getUniqueId()) < amount
                || !plugin.spendAvailableFunds(payer.getUniqueId(), amount)) {
            plugin.tellPublic(recipient, "The payer no longer has enough available funds. Nothing was transferred.");
            plugin.tellPublic(payer, "Transfer failed: not enough available funds.");
            return;
        }
        plugin.setBalance(payee.getUniqueId(), plugin.getBalance(payee.getUniqueId()) + amount);
        plugin.tellPublic(payer, "Transferred " + amount + " coins to " + payee.getName() + ".");
        plugin.tellPublic(payee, "Received " + amount + " coins from " + payer.getName() + ".");
    }
}
