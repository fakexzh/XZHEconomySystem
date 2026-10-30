package com.xzh.economy;

import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.entity.Animals;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Tameable;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Strict loader for the approved, CSV-derived 300-entry quest catalogue. */
final class QuestCatalogue {
    record Entry(String id, String tier, String group, String groupName, String name,
                 List<String> objectives, List<QuestObjectiveSpec> parsedObjectives, List<String> unlock,
                 int fee, int profit, int timerMinutes, Material icon, String tierGate) {
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
            List<Map<?, ?>> rows = yaml.getMapList("quests");
            if (rows.size() != 300) throw new IllegalStateException("Expected 300 quest rows; found " + rows.size());

            List<Entry> loaded = new ArrayList<>(300);
            Set<String> ids = new HashSet<>();
            int[] tierCounts = new int[3];
            for (Map<?, ?> q : rows) {
                String id = required(q, "id");
                if (!ids.add(id)) throw new IllegalStateException("Duplicate quest id: " + id);
                String tier = required(q, "tier").toLowerCase(Locale.ROOT);
                int tierIndex = switch (tier) {
                    case "easy" -> 0;
                    case "hard" -> 1;
                    case "master" -> 2;
                    default -> throw new IllegalStateException("Invalid tier for " + id + ": " + tier);
                };
                tierCounts[tierIndex]++;
                String group = required(q, "group");
                String groupName = required(q, "group-name");
                String name = required(q, "name");
                List<String> objectives = strings(q.get("objectives"));
                List<String> unlock = strings(q.get("unlock"));
                if (objectives.isEmpty()) throw new IllegalStateException("Quest " + id + " has no objectives");
                if (unlock.isEmpty() || unlock.size() > 3) {
                    throw new IllegalStateException("Quest " + id + " must have 1-3 advancement unlock requirements");
                }
                Set<String> uniqueUnlocks = new HashSet<>();
                for (String key : unlock) {
                    if (key == null || key.isBlank() || !uniqueUnlocks.add(key)) {
                        throw new IllegalStateException("Quest " + id + " has a blank or duplicate unlock key");
                    }
                }
                List<QuestObjectiveSpec> parsedObjectives = new ArrayList<>(objectives.size());
                for (String objective : objectives) {
                    try {
                        QuestObjectiveSpec spec = QuestObjectiveSpec.parse(objective);
                        validateObjectiveTargets(id, spec);
                        parsedObjectives.add(spec);
                    } catch (IllegalArgumentException ex) {
                        throw new IllegalStateException("Invalid or unsupported objective in " + id + ": " + ex.getMessage(), ex);
                    }
                }
                int fee = integer(q, "fee");
                int profit = integer(q, "profit");
                int timer = integer(q, "timer-minutes");
                int minProfit = tierIndex == 0 ? 160 : tierIndex == 1 ? 350 : 2000;
                int maxProfit = tierIndex == 0 ? 350 : tierIndex == 1 ? 950 : 5000;
                if (fee < 0 || profit < minProfit || profit > maxProfit || timer < 0) {
                    throw new IllegalStateException("Invalid fee/profit/timer values in " + id);
                }
                Material icon = Material.matchMaterial(required(q, "icon"));
                if (icon == null || !icon.isItem()) throw new IllegalStateException("Invalid item icon in " + id);
                String gate = required(q, "tier-gate");
                loaded.add(new Entry(id, tier, group, groupName, name, List.copyOf(objectives),
                        List.copyOf(parsedObjectives), unlock, fee, profit, timer, icon, gate));
            }
            if (tierCounts[0] != 100 || tierCounts[1] != 100 || tierCounts[2] != 100) {
                throw new IllegalStateException("Expected 100 quests per tier; found "
                        + tierCounts[0] + "/" + tierCounts[1] + "/" + tierCounts[2]);
            }
            plugin.getLogger().info("Validated v2 quest catalogue: 300 entries (100 per tier).");
            return new QuestCatalogue(loaded);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not load approved quest catalogue: " + ex.getMessage(), ex);
        }
    }

    private static void validateObjectiveTargets(String questId, QuestObjectiveSpec spec) {
        for (String target : spec.targets()) {
            boolean valid = switch (spec.type()) {
                case MINE -> Set.of("#LOGS", "#OVERWORLD_ORES", "#STONE_TYPES", "ANY", "ANY_ORE").contains(target)
                        || isBlock(target);
                case HARVEST -> Set.of("ANY", "ANY_CROP", "WHEAT", "CARROT", "POTATO", "BEETROOT",
                        "MELON", "PUMPKIN", "SWEET_BERRY_BUSH", "COCOA", "NETHER_WART", "GLOW_BERRIES").contains(target);
                case KILL -> Set.of("ANY", "#HOSTILES", "#RAIDERS", "#TRIAL_SPAWN_MOBS", "@ROAMING_BOSS").contains(target)
                        || isEntityType(target);
                case FISH -> Set.of("ANY", "#FISH_JUNK", "#FISH_TREASURE").contains(target) || isItem(target);
                case BREED -> target.equals("ANY") || isAnimal(target);
                case FEED -> target.equals("ANY") || isAnimal(target);
                case TAME -> target.equals("ANY") || isTameable(target);
                case SMELT -> target.equals("ANY") || target.equals("#COOKED_FOOD")
                        || Set.of("IRON_INGOT", "GOLD_INGOT", "COPPER_INGOT", "NETHERITE_SCRAP", "GLASS",
                        "BRICK", "STONE", "CHARCOAL", "TERRACOTTA", "COOKED_BEEF", "COOKED_PORKCHOP",
                        "COOKED_MUTTON", "COOKED_CHICKEN", "COOKED_RABBIT", "COOKED_COD",
                        "COOKED_SALMON", "BAKED_POTATO", "DRIED_KELP").contains(target);
                case CRAFT -> Set.of("BREAD", "PUMPKIN_PIE", "CAKE", "COOKIE", "LANTERN", "SHIELD",
                        "BUCKET", "BOOKSHELF", "IRON_HELMET", "IRON_CHESTPLATE",
                        "IRON_LEGGINGS", "IRON_BOOTS", "ANY").contains(target);
                case ENCHANT -> Set.of("ANY", "SLOT3").contains(target) || isItem(target);
                case BARTER -> target.equals("ANY");
                case LOOT -> target.equals("ANY") || target.toLowerCase(Locale.ROOT).startsWith("chests/") || target.toLowerCase(Locale.ROOT).startsWith("underwater_ruin_");
                case VAULT -> Set.of("ANY", "OMINOUS").contains(target);
                case TRAVEL -> Set.of("ANY", "FOOT", "ELYTRA").contains(target);
                case BIOME -> target.equals("ANY") || isBiome(target);
            };
            if (!valid) throw new IllegalArgumentException("target '" + target + "' has no supported " + spec.type() + " mechanic");
        }
        if (spec.dimension() != QuestObjectiveSpec.Dimension.ANY
                && !Set.of(QuestObjectiveSpec.Type.MINE, QuestObjectiveSpec.Type.HARVEST,
                        QuestObjectiveSpec.Type.KILL, QuestObjectiveSpec.Type.FISH,
                        QuestObjectiveSpec.Type.TRAVEL, QuestObjectiveSpec.Type.BIOME).contains(spec.type())) {
            throw new IllegalArgumentException("dimension filters are not supported for " + spec.type());
        }
    }

    private static boolean isBlock(String target) {
        Material material = Material.matchMaterial(target);
        return material != null && material.isBlock();
    }

    private static boolean isItem(String target) {
        Material material = Material.matchMaterial(target);
        return material != null && material.isItem();
    }

    private static boolean isEntityType(String target) {
        try { return EntityType.valueOf(target).getEntityClass() != null; }
        catch (IllegalArgumentException ex) { return false; }
    }

    private static boolean isAnimal(String target) {
        try {
            Class<?> type = EntityType.valueOf(target).getEntityClass();
            return type != null && Animals.class.isAssignableFrom(type);
        } catch (IllegalArgumentException ex) { return false; }
    }

    private static boolean isTameable(String target) {
        try {
            Class<?> type = EntityType.valueOf(target).getEntityClass();
            return type != null && Tameable.class.isAssignableFrom(type);
        } catch (IllegalArgumentException ex) { return false; }
    }

    private static boolean isBiome(String target) {
        try { Biome.valueOf(target); return true; }
        catch (IllegalArgumentException ex) { return false; }
    }

    List<Entry> entries() { return entries; }

    private static String required(Map<?, ?> map, String key) {
        Object value = map.get(key);
        if (value == null || value.toString().isBlank()) throw new IllegalStateException("Missing " + key);
        return value.toString();
    }

    private static int integer(Map<?, ?> map, String key) {
        Object value = map.get(key);
        if (!(value instanceof Number number)) throw new IllegalStateException("Missing or nonnumeric " + key);
        return number.intValue();
    }

    private static List<String> strings(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().map(Object::toString).toList();
    }
}
