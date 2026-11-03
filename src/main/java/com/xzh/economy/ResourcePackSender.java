package com.xzh.economy;

import java.util.HexFormat;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/** Sends the optional XZH GUI resource pack after a player joins. */
public final class ResourcePackSender implements Listener {
    private final XZHEconomySystem plugin;

    public ResourcePackSender(XZHEconomySystem plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        String url = plugin.getConfig().getString("resource-pack.url", "").trim();
        String sha1 = plugin.getConfig().getString("resource-pack.sha1", "").trim();
        // Assumption: do nothing until the owner configures a direct URL and the matching ZIP SHA-1.
        if (url.isBlank() || !sha1.matches("(?i)[0-9a-f]{40}")) return;
        boolean required = plugin.getConfig().getBoolean("resource-pack.required", false);
        Player player = event.getPlayer();
        byte[] hash = HexFormat.of().parseHex(sha1);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) player.setResourcePack(url, hash, required);
        }, 40L);
    }
}
