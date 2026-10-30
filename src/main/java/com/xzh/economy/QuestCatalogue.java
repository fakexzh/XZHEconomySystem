package com.xzh.economy;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Strict loader for the approved, CSV-derived 300-entry quest catalogue. */
final class QuestCatalogue {
    record Entry(String id, String tier, String group, String groupName, String name,
                 List<String> objectives, List<String> unlock, int fee, int profit,
                 int timerMinutes, Material icon, String tierGate) {
        int reward() { return fee + profit; }
    }

    private final List<Entry> entries;

    private QuestCatalogue(List<Entry> entries) {
        this.entries = List.copyOf(entries);
    }

    static QuestCatalogue load(JavaPlugin plugin) {
        try (InputStream stream = plugin.getResource("quests-v2.yml")) {
            if (stream == null) throw new IllegalStateException("Missing bundled resource quests-v2.yml");
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(stream, StandardCharsets.UTF_8));
            if (yaml.getInt("version", -1) != 2) throw new IllegalStateException("Unsupported quests-v2.yml version");
            ConfigurationSection section = yaml.getConfigurationSection("quests");
            if (section == null) throw new IllegalStateException("quests-v2.yml has no quests section");

            List<Entry> loaded = new ArrayList<>(300);
            Set<String> ids = new HashSet<>();
            int[] tierCounts = new int[3];
            for (String key : section.getKeys(false)) {
                ConfigurationSection q = section.getConfigurationSection(key);
                if (q == null) throw new IllegalStateException("Quest " + key + " is not a mapping");
                String id = required(q, "id", key);
                if (!ids.add(id)) throw new IllegalStateException("Duplicate quest id: " + id);
                String tier = required(q, "tier", id).toLowerCase(Locale.ROOT);
                int tierIndex = switch (tier) {
                    case "easy" -> 0;
                    case "hard" -> 1;
                    case "master" -> 2;
                    default -> throw new IllegalStateException("Invalid tier for " + id + ": " + tier);
                };
                tierCounts[tierIndex]++;
                String group = required(q, "group", id);
                String groupName = required(q, "group-name", id);
                String name = required(q, "name", id);
                List<String> objectives = q.getStringList("objectives");
                List<String> unlock = q.getStringList("unlock");
                if (objectives.isEmpty()) throw new IllegalStateException("Quest " + id + " has no objectives");
                for (String objective : objectives) {
                    try { QuestObjectiveSpec.parse(objective); }
                    catch (IllegalArgumentException ex) {
                        throw new IllegalStateException("Invalid objective grammar in " + id + ": " + ex.getMessage(), ex);
                    }
                }
                int fee = q.getInt("fee", -1);
                int profit = q.getInt("profit", -1);
                int timer = q.getInt("timer-minutes", -1);
                if (fee < 0 || profit < 0 || timer < 0) throw new IllegalStateException("Negative or missing financial/timer value in " + id);
                Material icon = Material.matchMaterial(required(q, "icon", id));
                if (icon == null || !icon.isItem()) throw new IllegalStateException("Invalid item icon in " + id);
                String gate = required(q, "tier-gate", id);
                loaded.add(new Entry(id, tier, group, groupName, name, List.copyOf(objectives),
                        List.copyOf(unlock), fee, profit, timer, icon, gate));
            }
            if (loaded.size() != 300 || tierCounts[0] != 100 || tierCounts[1] != 100 || tierCounts[2] != 100) {
                throw new IllegalStateException("Expected 100 quests per tier (300 total); found "
                        + tierCounts[0] + "/" + tierCounts[1] + "/" + tierCounts[2]);
            }
            plugin.getLogger().info("Validated v2 quest catalogue: 300 entries (100 per tier).");
            return new QuestCatalogue(loaded);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not load approved quest catalogue: " + ex.getMessage(), ex);
        }
    }

    List<Entry> entries() { return entries; }

    private static String required(ConfigurationSection section, String path, String id) {
        String value = section.getString(path);
        if (value == null || value.isBlank()) throw new IllegalStateException("Missing " + path + " for " + id);
        return value;
    }
}
