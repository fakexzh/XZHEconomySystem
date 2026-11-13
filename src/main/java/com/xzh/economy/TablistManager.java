package com.xzh.economy;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Owns the Economy plugin's player-list names, biome header, and quest footer. */
public final class TablistManager {
    private final XZHEconomySystem plugin;
    private BukkitTask refreshTask;
    private final Map<UUID, String> lastPlayerNames = new HashMap<>();
    private final Map<UUID, String> lastHeaders = new HashMap<>();
    private final Map<UUID, String> lastFooters = new HashMap<>();

    public TablistManager(XZHEconomySystem plugin) {
        this.plugin = plugin;
    }

    public void start() {
        refreshAll();
        refreshTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshAll, 100L, 100L);
    }

    public void shutdown() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
        lastPlayerNames.clear();
        lastHeaders.clear();
        lastFooters.clear();
    }

    public void refreshAll() {
        QuestManager quests = plugin.getQuestManager();
        if (quests == null) return;

        java.util.Set<UUID> onlineIds = new java.util.HashSet<>();
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            onlineIds.add(viewer.getUniqueId());
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
            String playerName = "§f" + viewer.getName() + " §8| §e" + ping + "ms";
            if (!playerName.equals(lastPlayerNames.get(viewer.getUniqueId()))) {
                viewer.setPlayerListName(playerName);
                lastPlayerNames.put(viewer.getUniqueId(), playerName);
            }

            String communityChest = quests.getGlobalQuestTabText(viewer);
            String paidQuest = quests.getPaidQuestTabText(viewer);
            String footer = "\n" + communityChest;
            if (!paidQuest.isBlank()) footer += "\n\n" + paidQuest;

            String header = "§b" + biomeName;
            UUID id = viewer.getUniqueId();
            if (!header.equals(lastHeaders.get(id)) || !footer.equals(lastFooters.get(id))) {
                viewer.setPlayerListHeaderFooter(header, footer);
                lastHeaders.put(id, header);
                lastFooters.put(id, footer);
            }
        }
        lastPlayerNames.keySet().removeIf(id -> !onlineIds.contains(id));
        lastHeaders.keySet().removeIf(id -> !onlineIds.contains(id));
        lastFooters.keySet().removeIf(id -> !onlineIds.contains(id));
    }
}
