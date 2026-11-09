package com.xzh.economy;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/** Owns the Economy plugin's player-list names, biome header, and quest footer. */
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
            String biome = viewer.getLocation().getBlock().getBiome().getKey().getKey()
                    .replace('_', ' ');
            String[] words = biome.split(" ");
            StringBuilder biomeName = new StringBuilder();
            for (String word : words) {
                if (word.isEmpty()) continue;
                if (biomeName.length() > 0) biomeName.append(' ');
                biomeName.append(Character.toUpperCase(word.charAt(0)))
                        .append(word.substring(1));
            }

            int ping = Math.max(0, viewer.getPing());
            viewer.setPlayerListName("§f" + viewer.getName() + " §8| §e" + ping + "ms");

            String communityChest = quests.getGlobalQuestTabText(viewer);
            String paidQuest = quests.getPaidQuestTabText(viewer);
            String footer = "\n" + communityChest;
            if (!paidQuest.isBlank()) footer += "\n\n" + paidQuest;

            viewer.setPlayerListHeaderFooter("§b" + biomeName, footer);
        }
    }
}
