package com.xzh.economy;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/** Owns the Economy plugin's player-list names and Community Chest footer. */
public final class TablistManager {
    private final XZHEconomySystem plugin;
    private BukkitTask refreshTask;

    public TablistManager(XZHEconomySystem plugin) {
        this.plugin = plugin;
    }

    public void start() {
        refreshAll();
        refreshTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshAll, 20L, 20L);
    }

    public void shutdown() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
    }

    public void refreshAll() {
        QuestManager quests = plugin.getQuestManager();
        if (quests == null) return;

        for (Player viewer : Bukkit.getOnlinePlayers()) {
            int ping = Math.max(0, viewer.getPing());
            viewer.setPlayerListName("§f" + viewer.getName() + " §8| §e" + ping + "ms");
            String communityChest = quests.getGlobalQuestTabText(viewer);
            viewer.setPlayerListHeaderFooter("", "\n" + communityChest);
        }
    }
}
