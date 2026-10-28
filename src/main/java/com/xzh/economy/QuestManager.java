package com.xzh.economy;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.data.Ageable;
import org.bukkit.advancement.Advancement;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.entity.Animals;
import org.bukkit.event.player.PlayerAttemptPickupItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/** Achievement packs, risk contracts, secret challenges, and shared Community quests. */
public final class QuestManager implements Listener {
    private record Objective(String target, int goal, String description) {}
    private record Pack(String id, String name, Material icon, int cost, int reward, String advancement, List<Objective> objectives) {}
    private record RandomQuest(String name, Material icon, int cost, int reward, Objective objective) {}
    private record GlobalQuest(String name, Material icon, String target, int goal, String description) {}
    private final List<GlobalQuest> globalQuestPool = List.of(
        new GlobalQuest("Community Ore Rush", Material.IRON_PICKAXE, "ore", 160, "Mine ores together"),
        new GlobalQuest("Deep Mining Expedition", Material.DEEPSLATE_DIAMOND_ORE, "ore", 320, "Mine deep ores together"),
        new GlobalQuest("Stoneworker Union", Material.STONE_PICKAXE, "ore", 500, "Keep the mining crews busy"),
        new GlobalQuest("Monster Patrol", Material.IRON_SWORD, "mob", 80, "Defeat monsters together"),
        new GlobalQuest("Night Watch", Material.SHIELD, "mob", 180, "Protect the settlement from monsters"),
        new GlobalQuest("Dangerous Hunt", Material.DIAMOND_SWORD, "mob", 300, "Complete a demanding monster hunt"),
        new GlobalQuest("Fishing Fleet", Material.FISHING_ROD, "fish", 30, "Catch fish together"),
        new GlobalQuest("Deepwater Haul", Material.COD, "fish", 75, "Bring in a major fishing haul"),
        new GlobalQuest("Anglers Marathon", Material.SALMON, "fish", 120, "Keep fishing until the goal is met"),
        new GlobalQuest("Forest Cleanup", Material.OAK_LOG, "wood", 128, "Chop logs together"),
        new GlobalQuest("Lumber Guild", Material.SPRUCE_LOG, "wood", 300, "Supply the community with timber"),
        new GlobalQuest("Great Reforestation", Material.BIRCH_LOG, "wood", 450, "Gather a huge amount of wood"),
        new GlobalQuest("Harvest Festival", Material.CARROT, "crop", 96, "Harvest crops together"),
        new GlobalQuest("Farmers Market", Material.WHEAT, "crop", 220, "Collect a crop surplus"),
        new GlobalQuest("Grand Harvest", Material.PUMPKIN, "crop", 400, "Complete a demanding farming drive"),
        new GlobalQuest("Community Food Drive", Material.POTATO, "crop", 180, "Stock the community food supply"),
        new GlobalQuest("Carrot Cartel", Material.CARROT, "crop", 260, "Gather carrots for the community"),
        new GlobalQuest("Wheat Supply Run", Material.WHEAT, "crop", 320, "Harvest wheat for a large order"),
        new GlobalQuest("Woodland Supply Run", Material.JUNGLE_LOG, "wood", 220, "Gather timber for building")
    );

    private final XZHEconomySystem plugin;
    private final File file;
    private final YamlConfiguration data;
    private final NamespacedKey actionKey;
    private final NamespacedKey valueKey;
    private final NamespacedKey excludedMobKey;
    private final Random random = new Random();
    private final Map<UUID, Double> travelRemainders = new HashMap<>();

    private final List<Pack> basePacks = List.of(
        new Pack("stone_survey", "Stone Survey", Material.IRON_PICKAXE, 80, 112, "minecraft:story/iron_tools", List.of(new Objective("break:STONE", 256, "Mine 256 Stone"))),
        new Pack("coal_contract", "Coal Contract", Material.COAL, 100, 140, "minecraft:story/smelt_iron", List.of(new Objective("collect:COAL", 64, "Collect 64 Coal"))),
        new Pack("iron_miner", "Iron Miner", Material.RAW_IRON, 140, 196, "minecraft:story/iron_tools", List.of(new Objective("break:IRON_ORE", 48, "Mine 48 Iron Ore"))),
        new Pack("copper_miner", "Copper Rush", Material.COPPER_INGOT, 120, 168, "minecraft:story/iron_tools", List.of(new Objective("collect:RAW_COPPER", 96, "Collect 96 Raw Copper"))),
        new Pack("gold_digger", "Gold Digger", Material.GOLD_INGOT, 220, 308, "minecraft:story/upgrade_tools", List.of(new Objective("break:GOLD_ORE", 32, "Mine 32 Gold Ore"))),
        new Pack("diamond_miner", "Diamond Prospector", Material.DIAMOND_PICKAXE, 350, 490, "minecraft:story/mine_diamond", List.of(new Objective("break:DIAMOND_ORE", 16, "Mine 16 Diamond Ore"))),
        new Pack("deepslate_delver", "Deepslate Delver", Material.DEEPSLATE, 180, 252, "minecraft:story/iron_tools", List.of(new Objective("break:DEEPSLATE", 384, "Mine 384 Deepslate"))),
        new Pack("quartz_quarry", "Quartz Quarry", Material.QUARTZ, 240, 336, "minecraft:nether/root", List.of(new Objective("break:NETHER_QUARTZ_ORE", 64, "Mine 64 Nether Quartz Ore"))),
        new Pack("debris_hunter", "Ancient Debris Hunt", Material.ANCIENT_DEBRIS, 550, 770, "minecraft:nether/obtain_ancient_debris", List.of(new Objective("break:ANCIENT_DEBRIS", 10, "Mine 10 Ancient Debris"))),
        new Pack("nether_hunter", "Nether Trophy Hunt", Material.WITHER_SKELETON_SKULL, 400, 560, "minecraft:nether/root", List.of(new Objective("kill:WITHER_SKELETON", 20, "Kill 20 Wither Skeletons"), new Objective("collect:WITHER_SKELETON_SKULL", 2, "Collect 2 Wither Skeleton Skulls"))),
        new Pack("nether_blaze", "Fortress Conqueror", Material.BLAZE_ROD, 550, 825, "minecraft:nether/obtain_blaze_rod", List.of(new Objective("kill:BLAZE", 25, "Kill 25 Blazes"), new Objective("collect:BLAZE_ROD", 32, "Collect 32 Blaze Rods"))),
        new Pack("magma_march", "Magma March", Material.MAGMA_BLOCK, 260, 364, "minecraft:nether/root", List.of(new Objective("break:MAGMA_BLOCK", 96, "Mine 96 Magma Blocks"))),
        new Pack("hoglin_hunt", "Hoglin Hunt", Material.PORKCHOP, 300, 420, "minecraft:nether/root", List.of(new Objective("kill:HOGLIN", 20, "Hunt 20 Hoglins"))),
        new Pack("end_expedition", "End Expedition", Material.END_STONE, 900, 1350, "minecraft:story/enter_the_end", List.of(new Objective("kill:ENDERMAN", 30, "Kill 30 Endermen"), new Objective("break:END_STONE", 128, "Mine 128 End Stone"), new Objective("collect:ENDER_PEARL", 32, "Collect 32 Ender Pearls"))),
        new Pack("dragon_slayer", "Dragon Slayer", Material.DRAGON_HEAD, 1800, 2700, "minecraft:end/kill_dragon", List.of(new Objective("kill:ENDER_DRAGON", 1, "Defeat the Ender Dragon"))),
        new Pack("pearl_trader", "Pearl Trader", Material.ENDER_PEARL, 320, 448, "minecraft:story/enter_the_end", List.of(new Objective("collect:ENDER_PEARL", 48, "Collect 48 Ender Pearls"))),
        new Pack("ocean_fisher", "Ocean Fishing", Material.FISHING_ROD, 180, 252, "minecraft:husbandry/tactical_fishing", List.of(new Objective("fish:COD", 16, "Catch 16 Cod while fishing"))),
        new Pack("salmon_run", "Salmon Run", Material.SALMON, 200, 280, "minecraft:husbandry/fishy_business", List.of(new Objective("fish:SALMON", 16, "Catch 16 Salmon while fishing"))),
        new Pack("deep_sea", "Deep Sea Haul", Material.TROPICAL_FISH, 420, 588, "minecraft:husbandry/fishy_business", List.of(new Objective("fish:TROPICAL_FISH", 8, "Catch 8 Tropical Fish"))),
        new Pack("farm_starter", "Farm Starter", Material.WHEAT, 70, 98, "minecraft:husbandry/plant_seed", List.of(new Objective("harvest:WHEAT", 64, "Harvest 64 fully grown Wheat crops"))),
        new Pack("root_vegetables", "Root Vegetable Run", Material.CARROT, 100, 140, "minecraft:husbandry/plant_seed", List.of(new Objective("harvest:CARROT", 128, "Harvest 128 fully grown Carrot crops"))),
        new Pack("baked_goods", "Baker's Order", Material.BREAD, 120, 168, "minecraft:husbandry/balanced_diet", List.of(new Objective("collect:BREAD", 64, "Collect 64 Bread"))),
        new Pack("meat_supply", "Meat Supply", Material.COOKED_BEEF, 180, 252, "minecraft:husbandry/balanced_diet", List.of(new Objective("collect:COOKED_BEEF", 64, "Collect 64 Cooked Beef"))),
        new Pack("animal_breeder", "Animal Breeder", Material.WHEAT, 150, 210, "minecraft:husbandry/breed_an_animal", List.of(new Objective("breed:ANY", 12, "Breed 12 animals"))),
        new Pack("cattle_feed", "Cattle Feeding", Material.WHEAT, 110, 154, "minecraft:husbandry/breed_an_animal", List.of(new Objective("feed:COW", 20, "Feed 20 cows"))),
        new Pack("sheep_flock", "Sheep Flock", Material.WHITE_WOOL, 140, 196, "minecraft:husbandry/breed_an_animal", List.of(new Objective("feed:SHEEP", 20, "Feed 20 sheep"))),
        new Pack("woodcutter", "Woodcutter Contract", Material.IRON_AXE, 130, 182, "minecraft:story/upgrade_tools", List.of(new Objective("break:OAK_LOG", 128, "Chop 128 Oak Logs"))),
        new Pack("builder_supply", "Builder's Supply", Material.BRICKS, 240, 336, "minecraft:story/upgrade_tools", List.of(new Objective("collect:BRICKS", 128, "Collect 128 Bricks"))),
        new Pack("explorer_trail", "Explorer's Trail", Material.COMPASS, 160, 224, "minecraft:adventure/adventuring_time", List.of(new Objective("travel", 5000, "Travel 5,000 blocks"))),
        new Pack("monster_patrol", "Monster Patrol", Material.IRON_SWORD, 200, 280, "minecraft:adventure/kill_a_mob", List.of(new Objective("kill:ZOMBIE", 30, "Kill 30 Zombies"), new Objective("kill:SKELETON", 20, "Kill 20 Skeletons"))),
        new Pack("raid_supply", "Raid Supply", Material.CROSSBOW, 380, 532, "minecraft:adventure/ol_betsy", List.of(new Objective("kill:PILLAGER", 25, "Defeat 25 Pillagers"))),
        new Pack("wither_siege", "Wither Siege", Material.NETHER_STAR, 2500, 3750, "minecraft:nether/summon_wither", List.of(new Objective("kill:WITHER", 1, "Defeat the Wither"), new Objective("collect:NETHER_STAR", 1, "Collect a Nether Star"))),
        new Pack("oak_guardian", "Oak Guardian", Material.OAK_LOG, 100, 150, "minecraft:story/iron_tools", List.of(new Objective("break:OAK_LOG", 96, "Chop 96 Oak Logs"))),
        new Pack("birch_builder", "Birch Builder", Material.BIRCH_PLANKS, 147, 206, "minecraft:story/iron_tools", List.of(new Objective("collect:BIRCH_PLANKS", 256, "Collect 256 Birch Planks"))),
        new Pack("spruce_supply", "Spruce Supply", Material.SPRUCE_LOG, 194, 272, "minecraft:story/iron_tools", List.of(new Objective("break:SPRUCE_LOG", 128, "Chop 128 Spruce Logs"))),
        new Pack("jungle_journey", "Jungle Journey", Material.JUNGLE_LOG, 241, 362, "minecraft:adventure/adventuring_time", List.of(new Objective("break:JUNGLE_LOG", 96, "Chop 96 Jungle Logs"))),
        new Pack("acacia_order", "Acacia Order", Material.ACACIA_LOG, 288, 404, "minecraft:story/iron_tools", List.of(new Objective("break:ACACIA_LOG", 96, "Chop 96 Acacia Logs"))),
        new Pack("dark_oak_contract", "Dark Oak Contract", Material.DARK_OAK_LOG, 335, 469, "minecraft:story/iron_tools", List.of(new Objective("break:DARK_OAK_LOG", 96, "Chop 96 Dark Oak Logs"))),
        new Pack("mangrove_harvest", "Mangrove Harvest", Material.MANGROVE_LOG, 382, 573, "minecraft:story/iron_tools", List.of(new Objective("break:MANGROVE_LOG", 96, "Chop 96 Mangrove Logs"))),
        new Pack("cherry_grove", "Cherry Grove Supply", Material.CHERRY_LOG, 429, 601, "minecraft:adventure/adventuring_time", List.of(new Objective("break:CHERRY_LOG", 96, "Chop 96 Cherry Logs"))),
        new Pack("bamboo_crafter", "Bamboo Crafter", Material.BAMBOO, 476, 667, "minecraft:story/iron_tools", List.of(new Objective("break:BAMBOO", 192, "Harvest 192 Bamboo"))),
        new Pack("stone_bricks", "Stone Brick Shipment", Material.STONE_BRICKS, 523, 785, "minecraft:story/iron_tools", List.of(new Objective("collect:STONE_BRICKS", 192, "Collect 192 Stone Bricks"))),
        new Pack("granite_quarry", "Granite Quarry", Material.GRANITE, 570, 798, "minecraft:story/iron_tools", List.of(new Objective("break:GRANITE", 192, "Mine 192 Granite"))),
        new Pack("andesite_quarry", "Andesite Quarry", Material.ANDESITE, 617, 864, "minecraft:story/iron_tools", List.of(new Objective("break:ANDESITE", 192, "Mine 192 Andesite"))),
        new Pack("diorite_quarry", "Diorite Quarry", Material.DIORITE, 113, 170, "minecraft:story/iron_tools", List.of(new Objective("break:DIORITE", 192, "Mine 192 Diorite"))),
        new Pack("clay_contract", "Clay Contract", Material.CLAY, 160, 224, "minecraft:story/iron_tools", List.of(new Objective("break:CLAY", 96, "Mine 96 Clay Blocks"))),
        new Pack("terracotta_order", "Terracotta Order", Material.TERRACOTTA, 207, 290, "minecraft:story/iron_tools", List.of(new Objective("collect:TERRACOTTA", 128, "Collect 128 Terracotta"))),
        new Pack("glassworks", "Glassworks", Material.GLASS, 254, 381, "minecraft:story/iron_tools", List.of(new Objective("collect:GLASS", 128, "Collect 128 Glass"))),
        new Pack("sandstone_supply", "Sandstone Supply", Material.SANDSTONE, 301, 422, "minecraft:story/iron_tools", List.of(new Objective("collect:SANDSTONE", 128, "Collect 128 Sandstone"))),
        new Pack("obsidian_vault", "Obsidian Vault", Material.OBSIDIAN, 348, 488, "minecraft:story/enter_the_nether", List.of(new Objective("break:OBSIDIAN", 32, "Mine 32 Obsidian"))),
        new Pack("lapis_order", "Lapis Order", Material.LAPIS_LAZULI, 395, 593, "minecraft:story/iron_tools", List.of(new Objective("collect:LAPIS_LAZULI", 96, "Collect 96 Lapis Lazuli"))),
        new Pack("redstone_rush", "Redstone Rush", Material.REDSTONE, 442, 619, "minecraft:story/iron_tools", List.of(new Objective("collect:REDSTONE", 128, "Collect 128 Redstone Dust"))),
        new Pack("emerald_broker", "Emerald Broker", Material.EMERALD, 489, 685, "minecraft:story/upgrade_tools", List.of(new Objective("collect:EMERALD", 24, "Collect 24 Emeralds"))),
        new Pack("amethyst_miner", "Amethyst Miner", Material.AMETHYST_SHARD, 536, 804, "minecraft:story/iron_tools", List.of(new Objective("collect:AMETHYST_SHARD", 96, "Collect 96 Amethyst Shards"))),
        new Pack("copper_smelter", "Copper Smelter", Material.COPPER_INGOT, 583, 817, "minecraft:story/iron_tools", List.of(new Objective("collect:COPPER_INGOT", 96, "Collect 96 Copper Ingots"))),
        new Pack("iron_foundry", "Iron Foundry", Material.IRON_INGOT, 630, 882, "minecraft:story/iron_tools", List.of(new Objective("collect:IRON_INGOT", 128, "Collect 128 Iron Ingots"))),
        new Pack("gold_foundry", "Gold Foundry", Material.GOLD_INGOT, 126, 189, "minecraft:story/upgrade_tools", List.of(new Objective("collect:GOLD_INGOT", 64, "Collect 64 Gold Ingots"))),
        new Pack("nether_brick_run", "Nether Brick Run", Material.NETHER_BRICK, 173, 243, "minecraft:nether/root", List.of(new Objective("collect:NETHER_BRICK", 128, "Collect 128 Nether Bricks"))),
        new Pack("soul_sand_scout", "Soul Sand Scout", Material.SOUL_SAND, 220, 308, "minecraft:nether/root", List.of(new Objective("break:SOUL_SAND", 128, "Gather 128 Soul Sand"))),
        new Pack("basalt_breaker", "Basalt Breaker", Material.BASALT, 267, 401, "minecraft:nether/root", List.of(new Objective("break:BASALT", 192, "Mine 192 Basalt"))),
        new Pack("crimson_harvest", "Crimson Harvest", Material.CRIMSON_STEM, 314, 440, "minecraft:nether/root", List.of(new Objective("break:CRIMSON_STEM", 96, "Harvest 96 Crimson Stems"))),
        new Pack("warped_harvest", "Warped Harvest", Material.WARPED_STEM, 361, 506, "minecraft:nether/root", List.of(new Objective("break:WARPED_STEM", 96, "Harvest 96 Warped Stems"))),
        new Pack("ghast_hunter", "Ghast Hunter", Material.GHAST_TEAR, 408, 612, "minecraft:nether/root", List.of(new Objective("kill:GHAST", 8, "Defeat 8 Ghasts"))),
        new Pack("piglin_patrol", "Piglin Patrol", Material.GOLD_INGOT, 455, 637, "minecraft:nether/root", List.of(new Objective("kill:PIGLIN", 24, "Defeat 24 Piglins"))),
        new Pack("magma_cube_hunt", "Magma Cube Hunt", Material.MAGMA_CREAM, 502, 703, "minecraft:nether/root", List.of(new Objective("kill:MAGMA_CUBE", 20, "Defeat 20 Magma Cubes"))),
        new Pack("end_city_scout", "End City Scout", Material.PURPUR_BLOCK, 549, 824, "minecraft:story/enter_the_end", List.of(new Objective("break:PURPUR_BLOCK", 128, "Mine 128 Purpur Blocks"))),
        new Pack("shulker_sweep", "Shulker Sweep", Material.SHULKER_SHELL, 596, 835, "minecraft:story/enter_the_end", List.of(new Objective("kill:SHULKER", 12, "Defeat 12 Shulkers"))),
        new Pack("chorus_gatherer", "Chorus Gatherer", Material.CHORUS_FRUIT, 643, 901, "minecraft:story/enter_the_end", List.of(new Objective("collect:CHORUS_FRUIT", 64, "Collect 64 Chorus Fruit"))),
        new Pack("phantom_watch", "Phantom Watch", Material.PHANTOM_MEMBRANE, 139, 209, "minecraft:adventure/kill_a_mob", List.of(new Objective("kill:PHANTOM", 12, "Defeat 12 Phantoms"))),
        new Pack("spider_control", "Spider Control", Material.SPIDER_EYE, 186, 261, "minecraft:adventure/kill_a_mob", List.of(new Objective("kill:SPIDER", 30, "Defeat 30 Spiders"))),
        new Pack("creeper_control", "Creeper Control", Material.GUNPOWDER, 233, 327, "minecraft:adventure/kill_a_mob", List.of(new Objective("kill:CREEPER", 25, "Defeat 25 Creepers"))),
        new Pack("witch_hunt", "Witch Hunt", Material.GLASS_BOTTLE, 280, 420, "minecraft:adventure/kill_a_mob", List.of(new Objective("kill:WITCH", 12, "Defeat 12 Witches"))),
        new Pack("slime_patrol", "Slime Patrol", Material.SLIME_BALL, 327, 458, "minecraft:adventure/kill_a_mob", List.of(new Objective("kill:SLIME", 24, "Defeat 24 Slimes"))),
        new Pack("drowned_cleanup", "Drowned Cleanup", Material.TRIDENT, 374, 524, "minecraft:adventure/kill_a_mob", List.of(new Objective("kill:DROWNED", 24, "Defeat 24 Drowned"))),
        new Pack("guardian_patrol", "Guardian Patrol", Material.PRISMARINE_SHARD, 421, 632, "minecraft:adventure/kill_a_mob", List.of(new Objective("kill:GUARDIAN", 16, "Defeat 16 Guardians"))),
        new Pack("rabbit_harvest", "Rabbit Harvest", Material.RABBIT, 468, 656, "minecraft:husbandry/balanced_diet", List.of(new Objective("collect:RAW_RABBIT", 32, "Collect 32 Raw Rabbit"))),
        new Pack("poultry_order", "Poultry Order", Material.CHICKEN, 515, 721, "minecraft:husbandry/balanced_diet", List.of(new Objective("collect:CHICKEN", 64, "Collect 64 Raw Chicken"))),
        new Pack("pork_supply", "Pork Supply", Material.PORKCHOP, 562, 843, "minecraft:husbandry/balanced_diet", List.of(new Objective("collect:PORKCHOP", 64, "Collect 64 Porkchops"))),
        new Pack("potato_farmer", "Potato Farmer", Material.POTATO, 609, 853, "minecraft:husbandry/plant_seed", List.of(new Objective("harvest:POTATO", 128, "Harvest 128 fully grown Potato crops"))),
        new Pack("beetroot_farmer", "Beetroot Farmer", Material.BEETROOT, 105, 147, "minecraft:husbandry/plant_seed", List.of(new Objective("harvest:BEETROOT", 128, "Harvest 128 fully grown Beetroot crops"))),
        new Pack("melon_delivery", "Melon Delivery", Material.MELON_SLICE, 152, 228, "minecraft:husbandry/plant_seed", List.of(new Objective("harvest:MELON_SLICE", 192, "Harvest 192 melons"))),
        new Pack("pumpkin_delivery", "Pumpkin Delivery", Material.PUMPKIN, 199, 279, "minecraft:husbandry/plant_seed", List.of(new Objective("harvest:PUMPKIN", 64, "Harvest 64 pumpkins")))
    );

    // Generate 100 one-time packs per progression tier from the expanded curated objective pool.
    private final List<Pack> packs = buildPacks();

    private List<Pack> buildPacks() {
        List<Pack> result = new ArrayList<>(300);
        String[] tiers = {"easy", "hard", "master"};
        for (int tier = 0; tier < tiers.length; tier++) {
            for (int i = 0; i < 100; i++) {
                int currentTier = tier;
                List<Pack> tierTemplates = basePacks.stream()
                        .filter(template -> templateEligibleForTier(template, currentTier))
                        .toList();
                Pack template = tierTemplates.get((i * 7 + tier * 11) % tierTemplates.size());
                String id = tiers[tier] + "_" + String.format(Locale.ROOT, "%03d", i + 1);
                int cost = tier == 0
                        ? 100 + Math.floorMod(template.cost() + i * 17, 201)
                        : tier == 1
                            ? 300 + Math.floorMod(template.cost() + i * 31, 701)
                            : 1000 + Math.floorMod(template.cost() + i * 47, 2001);
                int profit = tier == 0
                        ? 30 + Math.floorMod(template.cost() + i * 13, 71)
                        : tier == 1
                            ? 150 + Math.floorMod(template.cost() + i * 19, 351)
                            : 3000 + Math.floorMod(template.cost() + i * 29, 3001);
                int reward = cost + profit;
                double scale = tier == 0 ? 0.80 + (i % 4) * 0.10
                        : tier == 1 ? 1.10 + (i % 4) * 0.15 : 1.50 + (i % 4) * 0.20;
                List<Objective> objectives = new ArrayList<>();
                for (Objective objective : template.objectives()) {
                    int goal = Math.max(1, (int) Math.ceil(objective.goal() * scale));
                    objectives.add(new Objective(objective.target(), goal,
                            objective.description().replaceFirst("\\d+", Integer.toString(goal))));
                }
                String advancement = tier == 0 ? template.advancement()
                        : tier == 1 ? "minecraft:story/mine_diamond" : "minecraft:end/kill_dragon";
                String label = tiers[tier].substring(0, 1).toUpperCase(Locale.ROOT) + tiers[tier].substring(1);
                result.add(new Pack(id, label + " Pack " + String.format(Locale.ROOT, "%03d", i + 1)
                        + " - " + template.name(), template.icon(), cost, reward, advancement, List.copyOf(objectives)));
            }
        }
        return List.copyOf(result);
    }

    private boolean templateEligibleForTier(Pack template, int tier) {
        String advancement = template.advancement();
        boolean netherOrEnd = advancement.startsWith("minecraft:nether/")
                || advancement.equals("minecraft:story/enter_the_end")
                || advancement.startsWith("minecraft:end/");
        if (tier == 0) return !netherOrEnd;
        if (tier == 1) return !advancement.equals("minecraft:story/enter_the_end")
                && !advancement.startsWith("minecraft:end/");
        return true;
    }

    private String tierOf(Pack pack) {
        return pack.id().startsWith("master_") ? "master" : pack.id().startsWith("hard_") ? "hard" : "easy";
    }

    private boolean tierUnlocked(Player player, String tier) {
        return switch (tier) {
            case "hard" -> hasAdvancement(player, "minecraft:story/mine_diamond");
            case "master" -> hasAdvancement(player, "minecraft:end/kill_dragon");
            default -> true;
        };
    }

    private String tierUnlockDescription(String tier) {
        return switch (tier) {
            case "hard" -> "Unlock Hard packs by obtaining the Diamonds advancement";
            case "master" -> "Unlock Master packs by defeating the Ender Dragon";
            default -> "Easy packs are available from the start; individual packs have requirements";
        };
    }

    private boolean completedPack(UUID playerId, String packId) {
        return data.getBoolean("completed-packs." + playerId + "." + packId, false);
    }

    private final List<RandomQuest> randomPool = List.of(
        new RandomQuest("Deep Diamond Expedition", Material.DIAMOND, 180, 252, new Objective("break:DIAMOND_ORE", 12, "Mine 12 Diamond Ore")),
        new RandomQuest("Ancient Debris Search", Material.ANCIENT_DEBRIS, 260, 390, new Objective("break:ANCIENT_DEBRIS", 8, "Mine 8 Ancient Debris")),
        new RandomQuest("Raid the Fortress", Material.BLAZE_ROD, 220, 330, new Objective("kill:BLAZE", 18, "Kill 18 Blazes in the Nether")),
        new RandomQuest("Undead Patrol", Material.BOW, 150, 210, new Objective("kill:ZOMBIE", 35, "Kill 35 Zombies")),
        new RandomQuest("Ocean Treasure Hunt", Material.PRISMARINE_SHARD, 200, 300, new Objective("break:PRISMARINE", 96, "Mine 96 Prismarine blocks")),
        new RandomQuest("Ender Pearl Collection", Material.ENDER_PEARL, 240, 360, new Objective("collect:ENDER_PEARL", 24, "Collect 24 Ender Pearls")),
        new RandomQuest("Iron Supply Contract", Material.RAW_IRON, 130, 182, new Objective("break:IRON_ORE", 64, "Mine 64 Iron Ore")),
        new RandomQuest("Wither Skeleton Hunt", Material.WITHER_SKELETON_SKULL, 350, 525, new Objective("kill:WITHER_SKELETON", 16, "Kill 16 Wither Skeletons")),
        new RandomQuest("Fishing for Cod", Material.COD, 90, 126, new Objective("fish:COD", 20, "Catch 20 Cod")),
        new RandomQuest("Fishing for Salmon", Material.SALMON, 110, 154, new Objective("fish:SALMON", 20, "Catch 20 Salmon")),
        new RandomQuest("Feed the Cows", Material.WHEAT, 100, 140, new Objective("feed:COW", 20, "Feed 20 cows")),
        new RandomQuest("Animal Breeding", Material.PORKCHOP, 120, 168, new Objective("breed:ANY", 10, "Breed 10 animals")),
        new RandomQuest("Travel Contract", Material.COMPASS, 100, 140, new Objective("travel", 2500, "Travel 2,500 blocks")),
        new RandomQuest("Woodcutting Contract", Material.OAK_LOG, 100, 140, new Objective("break:OAK_LOG", 128, "Chop 128 Oak Logs")),
        new RandomQuest("Crop Delivery", Material.CARROT, 85, 119, new Objective("harvest:CARROT", 96, "Harvest 96 fully grown Carrot crops")),
        new RandomQuest("Blaze Rod Shipment", Material.BLAZE_ROD, 190, 266, new Objective("collect:BLAZE_ROD", 32, "Collect 32 Blaze Rods")),
        new RandomQuest("Sand Quarry", Material.SAND, 75, 105, new Objective("break:SAND", 256, "Mine 256 Sand")),
        new RandomQuest("Monster Cleanup", Material.IRON_SWORD, 140, 196, new Objective("kill:SKELETON", 30, "Kill 30 Skeletons")),
        new RandomQuest("Food Pantry", Material.BREAD, 90, 126, new Objective("collect:BREAD", 64, "Collect 64 Bread")),
        new RandomQuest("Quartz Shipment", Material.QUARTZ, 160, 224, new Objective("collect:QUARTZ", 96, "Collect 96 Nether Quartz")),
        new RandomQuest("Tropical Catch", Material.TROPICAL_FISH, 150, 225, new Objective("fish:TROPICAL_FISH", 8, "Catch 8 Tropical Fish")),
        new RandomQuest("Pufferfish Watch", Material.PUFFERFISH, 135, 203, new Objective("fish:PUFFERFISH", 10, "Catch 10 Pufferfish")),
        new RandomQuest("Cod Fishing Run", Material.FISHING_ROD, 120, 180, new Objective("fish:COD", 16, "Catch 16 Cod")),
        new RandomQuest("Potato Harvest", Material.POTATO, 100, 150, new Objective("harvest:POTATO", 128, "Harvest 128 fully grown Potato crops")),
        new RandomQuest("Pumpkin Season", Material.PUMPKIN, 125, 188, new Objective("harvest:PUMPKIN", 64, "Harvest 64 pumpkins")),
        new RandomQuest("Melon Shipment", Material.MELON_SLICE, 110, 165, new Objective("harvest:MELON_SLICE", 192, "Harvest 192 melons")),
        new RandomQuest("Copper Ore Contract", Material.RAW_COPPER, 145, 218, new Objective("break:COPPER_ORE", 64, "Mine 64 Copper Ore")),
        new RandomQuest("Lapis Prospecting", Material.LAPIS_LAZULI, 175, 263, new Objective("break:LAPIS_ORE", 32, "Mine 32 Lapis Lazuli Ore")),
        new RandomQuest("Redstone Extraction", Material.REDSTONE, 165, 248, new Objective("break:REDSTONE_ORE", 48, "Mine 48 Redstone Ore"))
    );

    public QuestManager(XZHEconomySystem plugin) {
        this.plugin = plugin;
        file = new File(plugin.getDataFolder(), "quests.yml");
        data = YamlConfiguration.loadConfiguration(file);
        // Remove active quests from the retired system so old easy objectives cannot persist.
        boolean migratedOldQuests = false;
        for (String root : new ArrayList<>(data.getKeys(false))) {
            try {
                UUID.fromString(root);
                if (data.contains(root + ".active") && !data.contains(root + ".objective-count")) {
                    data.set(root, null);
                    migratedOldQuests = true;
                }
            } catch (IllegalArgumentException ignored) { }
        }
        if (migratedOldQuests) {
            try { data.save(file); } catch (IOException e) { plugin.getLogger().severe("Could not migrate old quest data: " + e.getMessage()); }
        }
        actionKey = new NamespacedKey(plugin, "quest_action");
        valueKey = new NamespacedKey(plugin, "quest_value");
        excludedMobKey = new NamespacedKey(plugin, "quest_excluded_mob");
        Bukkit.getPluginManager().registerEvents(this, plugin);
        resetEventIfNeeded();
        Bukkit.getScheduler().runTaskTimer(plugin, this::resetEventIfNeeded, 20L * 60L, 20L * 60L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::refreshRiskShopIfDue, 20L * 60L, 20L * 60L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::refreshSecretQuestIfDue, 20L * 60L, 20L * 60L);
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) updateHud(player);
        }, 20L, 20L);
        // Shared global reminder and progress broadcast every 15 minutes while the event is active.
        Bukkit.getScheduler().runTaskTimer(plugin, this::broadcastGlobalProgress, 20L * 60L * 15L, 20L * 60L * 15L);
        // Persist placed-block anti-farming markers in batches rather than writing YAML on every placement.
        Bukkit.getScheduler().runTaskTimer(plugin, this::save, 20L * 60L, 20L * 60L);
        if (!data.contains("single-shop.next-refresh")) {
            data.set("single-shop.next-refresh", 0L);
            data.set("single-shop.refresh-id", 0);
            refreshRiskShopIfDue();
        }
    }

    public void adminClearQuest(UUID id) {
        data.set(id.toString(), null);
        Player player = Bukkit.getPlayer(id);
        if (player != null) plugin.getHudManager().hideActiveQuest(player);
        save();
    }

    public void adminResetEvent() {
        data.set("event", null);
        startNewGlobalQuest(System.currentTimeMillis(), true);
    }

    public boolean setPackStock(String packId, int amount) {
        if (amount < 0 || amount > 1_000_000) return false;
        data.set("pack-stock." + packId.toLowerCase(Locale.ROOT), amount);
        save();
        return true;
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.DARK_PURPLE + "XZH Quests");
        add(inv, 10, Material.ENDER_EYE, ChatColor.LIGHT_PURPLE + "Secret & Risk Quests", "section-risk", "risk",
            List.of(ChatColor.AQUA + "Risk contracts and secret challenges",
                    ChatColor.YELLOW + "Fees, rewards and refunds shown before purchase"));
        add(inv, 13, Material.ENCHANTED_BOOK, ChatColor.LIGHT_PURPLE + "Achievements", "section-packs", "packs",
            List.of(ChatColor.AQUA + "Easy · Hard · Master",
                    ChatColor.YELLOW + "100 lifetime packs per tier"));
        add(inv, 16, Material.NETHER_STAR, ChatColor.GOLD + "Community", "section-community", "community",
            List.of(ChatColor.AQUA + "Shared server-wide objectives",
                    ChatColor.YELLOW + "Rewards depend on contribution"));
        add(inv, 22, Material.ARROW, ChatColor.WHITE + "Back to Shop", "back-shop", "");
        player.openInventory(inv);
    }

    private void openRiskQuests(Player player) {
        refreshRiskShopIfDue();
        refreshSecretQuestIfDue();
        ensureRiskOfferDetails();
        Inventory inv = Bukkit.createInventory(null, 54, ChatColor.DARK_PURPLE + "XZH Secret & Risk Quests");
        int secretCost = data.getInt("single-shop.secret.cost", 500);
        int secretProfit = data.getInt("single-shop.secret.profit", 2500);
        int secretRefund = data.getInt("single-shop.secret.refund-percent", 35);
        add(inv, 10, Material.ENDER_EYE, ChatColor.LIGHT_PURPLE + "??? Secret Quest", "risk-secret-buy",
                Long.toString(data.getLong("single-shop.secret.next-refresh", 0L)),
            List.of(ChatColor.YELLOW + "Entry fee: " + secretCost + " coins",
                    ChatColor.GREEN + "Completion reward: " + (secretCost + secretProfit) + " coins",
                    ChatColor.GOLD + "Net profit: +" + secretProfit + " coins",
                    ChatColor.RED + "Cancel refund: " + secretRefund + "% (" + (secretCost * secretRefund / 100) + " coins)",
                    ChatColor.GRAY + "Objective hidden until accepted"));
        for (int i = 0; i < 5; i++) {
            int poolIndex = Math.floorMod(data.getInt("single-shop.offers." + i, i % randomPool.size()), randomPool.size());
            RandomQuest offer = randomPool.get(poolIndex);
            int goal = data.getInt("single-shop.risk-goal." + i, offer.objective().goal() * 2);
            int cost = data.getInt("single-shop.risk-cost." + i, 500);
            int profit = data.getInt("single-shop.risk-profit." + i, 2000);
            int refund = data.getInt("single-shop.risk-refund-percent." + i, 30);
            String objective = offer.objective().description().replaceFirst("\\d[\\d,]*", Integer.toString(goal));
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.AQUA + objective);
            lore.add(ChatColor.YELLOW + "Entry fee: " + cost + " coins");
            lore.add(ChatColor.GREEN + "Completion reward: " + (cost + profit) + " coins");
            lore.add(ChatColor.GOLD + "Net profit: +" + profit + " coins");
            lore.add(ChatColor.RED + "Cancel refund: " + refund + "% (" + (cost * refund / 100) + " coins)");
            lore.add(ChatColor.GRAY + "Time limit: 60 minutes");
            add(inv, 11 + i, offer.icon(), ChatColor.YELLOW + "Risk Contract", "risk-buy",
                    i + ":" + data.getInt("single-shop.refresh-id", 0), lore);
        }
        int legendaryCost = data.getInt("single-shop.legendary.cost", 3000);
        int legendaryProfit = data.getInt("single-shop.legendary.profit", 8000);
        int legendaryRefund = data.getInt("single-shop.legendary.refund-percent", 25);
        add(inv, 16, Material.NETHER_STAR, ChatColor.GOLD + "Legendary Risk", "risk-legendary-buy", "legendary",
            List.of(ChatColor.AQUA + "Defeat the Wither and collect its Nether Star",
                    ChatColor.YELLOW + "Entry fee: " + legendaryCost + " coins",
                    ChatColor.GREEN + "Completion reward: " + (legendaryCost + legendaryProfit) + " coins",
                    ChatColor.GOLD + "Net profit: +" + legendaryProfit + " coins",
                    ChatColor.RED + "Cancel refund: " + legendaryRefund + "% (" + (legendaryCost * legendaryRefund / 100) + " coins)",
                    ChatColor.GRAY + "Time limit: 180 minutes"));
        add(inv, 31, Material.CLOCK, ChatColor.AQUA + "Contract refresh", "risk-info", "",
            List.of(ChatColor.GRAY + "Risk contracts refresh every 2 hours.",
                    ChatColor.GRAY + "Secret quests refresh every 30 minutes.",
                    ChatColor.GRAY + "Every quest has its own cancellation refund."));
        add(inv, 49, Material.ARROW, ChatColor.WHITE + "Back", "back", "");
        player.openInventory(inv);
    }

    private void openPacks(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.DARK_PURPLE + "XZH Pack Tiers");
        for (String tier : List.of("easy", "hard", "master")) {
            boolean unlocked = tierUnlocked(player, tier);
            Material icon = tier.equals("easy") ? Material.OAK_SAPLING
                    : tier.equals("hard") ? Material.IRON_SWORD : Material.GOLDEN_HELMET;
            String label = tier.equals("easy") ? "Easy Packs" : tier.equals("hard") ? "Hard Packs" : "Master Packs";
            long done = packs.stream().filter(pack -> tierOf(pack).equals(tier)
                    && completedPack(player.getUniqueId(), pack.id())).count();
            int slot = tier.equals("easy") ? 11 : tier.equals("hard") ? 13 : 15;
            add(inv, slot, icon, (unlocked ? ChatColor.GREEN : ChatColor.RED) + label,
                    unlocked ? "pack-tier" : "tier-locked", tier,
                    List.of(ChatColor.WHITE + "Packs: 100", ChatColor.WHITE + "Completed: " + done + "/100",
                            ChatColor.GOLD + tierUnlockDescription(tier)));
        }
        add(inv, 22, Material.ARROW, ChatColor.WHITE + "Back", "back", "");
        player.openInventory(inv);
    }

    private void openPacks(Player player, String tier, int page) {
        if (!List.of("easy", "hard", "master").contains(tier)) {
            openPacks(player);
            return;
        }
        List<Pack> list = packs.stream().filter(pack -> tierOf(pack).equals(tier)).toList();
        int pages = Math.max(1, (list.size() + 44) / 45);
        int pg = Math.max(0, Math.min(page, pages - 1));
        String label = tier.substring(0, 1).toUpperCase(Locale.ROOT) + tier.substring(1);
        Inventory inv = Bukkit.createInventory(null, 54, ChatColor.DARK_PURPLE + "XZH " + label + " Packs " + (pg + 1));
        for (int i = pg * 45; i < Math.min(list.size(), pg * 45 + 45); i++) {
            Pack pack = list.get(i);
            boolean done = completedPack(player.getUniqueId(), pack.id());
            boolean unlocked = tierUnlocked(player, tierOf(pack)) && hasAdvancement(player, pack.advancement());
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.WHITE + "Cost: " + ChatColor.YELLOW + pack.cost() + " coins");
            lore.add(ChatColor.WHITE + "Reward: " + ChatColor.GREEN + rewardForPack(pack) + " coins");
            lore.add(ChatColor.GOLD + "Net profit: +" + (rewardForPack(pack) - pack.cost()) + " coins");
            int refundPercent = refundPercentForId(pack.id());
            lore.add(ChatColor.RED + "Cancel refund: " + refundPercent + "% (" + (pack.cost() * refundPercent / 100) + " coins)");
            lore.add(ChatColor.WHITE + "Objectives:");
            for (Objective objective : pack.objectives()) lore.add(ChatColor.AQUA + "• " + objective.description());
            lore.add(done ? ChatColor.DARK_GRAY + "COMPLETED FOR LIFE — reward already claimed"
                    : !tierUnlocked(player, tierOf(pack)) ? ChatColor.RED + tierUnlockDescription(tierOf(pack))
                    : unlocked ? ChatColor.GREEN + "UNLOCKED"
                    : ChatColor.RED + "Complete the pack's progression achievement");
            int timerMinutes = packTimerLimitMinutes(pack.id());
            lore.add(timerMinutes > 0 ? ChatColor.RED + "Time limit: " + timerMinutes + " minutes"
                    : ChatColor.GRAY + "No time limit");
            Material icon = done ? Material.BLACK_STAINED_GLASS_PANE : unlocked ? pack.icon() : Material.BARRIER;
            add(inv, i - pg * 45, icon, (done ? ChatColor.DARK_GRAY : unlocked ? ChatColor.YELLOW : ChatColor.RED)
                    + pack.name(), done ? "pack-completed" : unlocked ? "pack-select" : "locked", pack.id(), lore);
        }
        if (pg > 0) add(inv, 45, Material.PAPER, ChatColor.WHITE + "Previous page", "pack-page", tier + ":" + (pg - 1));
        add(inv, 49, Material.ARROW, ChatColor.WHITE + "Difficulties", "back-tiers", "");
        if (pg + 1 < pages) add(inv, 53, Material.PAPER, ChatColor.WHITE + "Next page", "pack-page", tier + ":" + (pg + 1));
        player.openInventory(inv);
    }

    private void confirmPack(Player player, Pack pack) {
        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.DARK_RED + "Confirm quest pack");
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.YELLOW + "Entry fee: " + pack.cost() + " coins");
        lore.add(ChatColor.GREEN + "Completion reward: " + rewardForPack(pack) + " coins");
        lore.add(ChatColor.GOLD + "Net profit: +" + (rewardForPack(pack) - pack.cost()) + " coins");
        int refundPercent = refundPercentForId(pack.id());
        lore.add(ChatColor.RED + "Cancel refund: " + refundPercent + "% (" + (pack.cost() * refundPercent / 100) + " coins)");
        for (Objective objective : pack.objectives()) lore.add(ChatColor.AQUA + objective.description());
        int timerMinutes = packTimerLimitMinutes(pack.id());
        lore.add(timerMinutes > 0 ? ChatColor.RED + "Time limit: " + timerMinutes + " minutes" : ChatColor.GRAY + "No time limit");
        add(inv, 13, pack.icon(), ChatColor.YELLOW + pack.name(), "pack-info", pack.id(), lore);
        add(inv, 11, Material.SLIME_BALL, ChatColor.GREEN + "Confirm purchase", "pack-buy", pack.id());
        add(inv, 15, Material.FIRE_CHARGE, ChatColor.RED + "Cancel", "back-packs", pack.id());
        add(inv, 22, Material.ARROW, ChatColor.WHITE + "Back to packs", "back-packs", pack.id());
        player.openInventory(inv);
    }

    public boolean cancelActiveQuest(Player player) {
        UUID id = player.getUniqueId();
        String activeId = data.getString(id + ".active", "");
        if (activeId.isBlank()) {
            plugin.tellPublic(player, ChatColor.RED + "No active quest to cancel.");
            return false;
        }
        int originalCost = Math.max(0, data.getInt(id + ".cost", 0));
        int refundPercent = Math.max(20, Math.min(50, data.getInt(id + ".refund-percent", 30)));
        int refund = originalCost * refundPercent / 100;
        data.set(id.toString(), null);
        save();
        plugin.getHudManager().hideActiveQuest(player);
        if (refund > 0) {
            deliverQuestCoins(id, refund, player, "Cancel refund (" + refundPercent + "%)");
        } else {
            plugin.tellPublic(player, ChatColor.RED + "Quest cancelled. No fee was charged.");
        }
        return true;
    }

    private void buySecretQuest(Player player, String shownVersion) {
        long shown;
        try { shown = Long.parseLong(shownVersion); }
        catch (NumberFormatException ignored) { openRiskQuests(player); return; }
        if (shown != data.getLong("single-shop.secret.next-refresh", 0L)
                || shown <= System.currentTimeMillis()) {
            refreshSecretQuestIfDue();
            openRiskQuests(player);
            plugin.tellPublic(player, ChatColor.YELLOW + "Secret offer refreshed. Check the new details.");
            return;
        }
        buyRiskQuest(player, 5);
    }

    private void buyRiskOffer(Player player, String value) {
        String[] parts = value.split(":");
        if (parts.length != 2) { openRiskQuests(player); return; }
        try {
            int slot = Integer.parseInt(parts[0]);
            int shownRefresh = Integer.parseInt(parts[1]);
            if (shownRefresh != data.getInt("single-shop.refresh-id", 0)) {
                refreshRiskShopIfDue();
                openRiskQuests(player);
                plugin.tellPublic(player, ChatColor.YELLOW + "Risk offers refreshed. Check the new details.");
                return;
            }
            buyRiskQuest(player, slot);
        } catch (NumberFormatException ignored) {
            openRiskQuests(player);
        }
    }

    private void buyRiskQuest(Player player, int offerSlot) {
        if (offerSlot == 6) { buyLegendaryRisk(player); return; }
        UUID id = player.getUniqueId();
        if (plugin.getLoanManager().isShopPurchaseRestricted(id)) {
            plugin.tellPublic(player, ChatColor.RED + "Overdue loan: quest purchases are locked.");
            return;
        }
        if (!data.getString(id + ".active", "").isBlank()) {
            plugin.tellPublic(player, ChatColor.RED + "Finish your active quest first.");
            return;
        }
        if (offerSlot < 0 || offerSlot > 5) return;
        boolean secret = offerSlot == 5;
        if (secret) refreshSecretQuestIfDue();
        int poolIndex = Math.floorMod(data.getInt(secret ? "single-shop.secret.offer" : "single-shop.offers." + offerSlot, offerSlot), randomPool.size());
        RandomQuest template = randomPool.get(poolIndex);
        int goal = secret
                ? data.getInt("single-shop.secret.goal", Math.max(1, template.objective().goal() * 2))
                : data.getInt("single-shop.risk-goal." + offerSlot, Math.max(1, template.objective().goal() * 2));
        Objective objective = new Objective(template.objective().target(), goal,
                template.objective().description().replaceFirst("\\d[\\d,]*", Integer.toString(goal)));
        int cost = secret ? data.getInt("single-shop.secret.cost", 500) : data.getInt("single-shop.risk-cost." + offerSlot, 500);
        int profit = secret ? data.getInt("single-shop.secret.profit", 2500) : data.getInt("single-shop.risk-profit." + offerSlot, 2000);
        int refundPercent = secret ? data.getInt("single-shop.secret.refund-percent", 35) : data.getInt("single-shop.risk-refund-percent." + offerSlot, 30);
        int reward = cost + profit;
        if (plugin.getCombinedBalance(id) < cost || !plugin.spendAvailableFunds(id, cost)) {
            plugin.tellPublic(player, ChatColor.RED + "Not enough coins for this quest.");
            return;
        }
        String questName = secret ? "Secret Quest" : "Risk Contract: " + template.name();
        startPaidQuest(player, "risk:" + (secret ? "secret:" : "contract:") + System.currentTimeMillis(),
                questName, cost, reward, List.of(objective), refundPercent, secret ? "secret" : "risk");
        player.closeInventory();
    }

    private void buyLegendaryRisk(Player player) {
        UUID id = player.getUniqueId();
        if (plugin.getLoanManager().isShopPurchaseRestricted(id)) {
            plugin.tellPublic(player, ChatColor.RED + "Overdue loan: quest purchases are locked.");
            return;
        }
        if (!data.getString(id + ".active", "").isBlank()) {
            plugin.tellPublic(player, ChatColor.RED + "Finish your active quest first.");
            return;
        }
        int cost = data.getInt("single-shop.legendary.cost", 3000);
        int profit = data.getInt("single-shop.legendary.profit", 8000);
        int refund = data.getInt("single-shop.legendary.refund-percent", 25);
        if (plugin.getCombinedBalance(id) < cost || !plugin.spendAvailableFunds(id, cost)) {
            plugin.tellPublic(player, ChatColor.RED + "Not enough coins for Legendary Risk.");
            return;
        }
        List<Objective> objectives = List.of(
                new Objective("kill:WITHER", 1, "Defeat the Wither"),
                new Objective("collect:NETHER_STAR", 1, "Collect 1 Nether Star"));
        startPaidQuest(player, "risk:legendary:" + System.currentTimeMillis(), "Legendary Risk",
                cost, cost + profit, objectives, refund, "legendary");
        player.closeInventory();
    }

    private void startPaidQuest(Player player, String id, String name, int cost, int reward,
                                List<Objective> objectives, int refundPercent, String tier) {
        startQuest(player, id, name, cost, reward, objectives);
        String playerPath = player.getUniqueId().toString();
        data.set(playerPath + ".refund-percent", Math.max(20, Math.min(50, refundPercent)));
        data.set(playerPath + ".risk-tier", tier);
        save();
        String publicTier = switch (tier) {
            case "easy" -> "Easy Achievement";
            case "hard" -> "Hard Achievement";
            case "master" -> "Master Achievement";
            case "secret" -> "Secret Quest";
            case "legendary" -> "Legendary Risk";
            default -> "Risk Quest";
        };
        Bukkit.broadcastMessage(ChatColor.GOLD + "[XZH] " + ChatColor.YELLOW + player.getName()
                + ChatColor.AQUA + " accepted a " + ChatColor.LIGHT_PURPLE + publicTier + ChatColor.AQUA + ".");
    }

    private void buyPack(Player player, Pack pack) {
        UUID id = player.getUniqueId();
        if (data.getBoolean("completed-packs." + id + "." + pack.id(), false)) {
            plugin.tellPublic(player, "You have already completed this achievement pack. It can only be completed once.");
            openPacks(player);
            return;
        }
        if (plugin.getLoanManager().isShopPurchaseRestricted(id)) {
            plugin.tellPublic(player, "Your loan is overdue. Quest-pack purchases are locked until full repayment.");
            return;
        }
        if (!tierUnlocked(player, tierOf(pack)) || !hasAdvancement(player, pack.advancement())) {
            plugin.tellPublic(player, "Complete the required advancement to unlock this quest pack.");
            return;
        }
        if (!data.getString(id + ".active", "").isBlank()) {
            plugin.tellPublic(player, "Finish your active quest before buying another.");
            return;
        }
        if (plugin.getCombinedBalance(id) < pack.cost() || !plugin.spendAvailableFunds(id, pack.cost())) {
            plugin.tellPublic(player, "Not enough coins for this quest pack.");
            return;
        }
        startPaidQuest(player, "pack:" + pack.id(), pack.name(), pack.cost(), rewardForPack(pack), pack.objectives(),
                refundPercentForId(pack.id()), tierOf(pack));
        player.closeInventory();
        plugin.tellPublic(player, ChatColor.GREEN + "Quest accepted: " + ChatColor.YELLOW + pack.name()
                + ChatColor.GREEN + ". Reward: " + ChatColor.GOLD + rewardForPack(pack) + " coins.");
    }

    private void startQuest(Player player, String id, String name, int cost, int reward, List<Objective> objectives) {
        UUID uuid = player.getUniqueId();
        data.set(uuid + ".active", id);
        data.set(uuid + ".name", name);
        data.set(uuid + ".cost", cost);
        data.set(uuid + ".reward", reward);
        data.set(uuid + ".objective-count", objectives.size());
        for (int i = 0; i < objectives.size(); i++) {
            Objective o = objectives.get(i);
            data.set(uuid + ".objectives." + i + ".target", o.target());
            data.set(uuid + ".objectives." + i + ".goal", o.goal());
            data.set(uuid + ".objectives." + i + ".description", o.description());
            data.set(uuid + ".objectives." + i + ".progress", 0);
        }
        data.set(uuid + ".started-at", System.currentTimeMillis());
        data.set(uuid + ".refund-percent", 30);
        data.set(uuid + ".risk-tier", id.startsWith("risk:") ? "risk" : "achievement");
        data.set(uuid + ".warned-5m", false);
        data.set(uuid + ".warned-1m", false);
        save();
        updateHud(player);
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        if (event.isCancelled()) return;
        Player p = event.getPlayer();
        if (p.getGameMode() == org.bukkit.GameMode.CREATIVE || p.getGameMode() == org.bukkit.GameMode.SPECTATOR) return;
        String placedKey = placedBlockKey(event.getBlock());
        if (data.getBoolean("placed-blocks." + placedKey, false)) {
            data.set("placed-blocks." + placedKey, null);
            return;
        }
        Material material = event.getBlock().getType();
        String id = material.name();
        incrementTarget(p, "break:" + id, 1);
        // Deepslate ore variants count toward the matching ore contracts too.
        if (id.startsWith("DEEPSLATE_") && id.endsWith("_ORE")) {
            incrementTarget(p, "break:" + id.substring("DEEPSLATE_".length()), 1);
        }
        if (material.name().endsWith("_ORE") || material == Material.ANCIENT_DEBRIS) {
            incrementTarget(p, "break:ANY_ORE", 1);
            recordGlobalProgress(p, "ore", 1);
        }
        if (material.name().endsWith("_LOG") || material.name().endsWith("_STEM")) {
            recordGlobalProgress(p, "wood", 1);
        }
        boolean matureCrop = false;
        if (event.getBlock().getBlockData() instanceof Ageable ageable
                && ageable.getAge() >= ageable.getMaximumAge()) {
            Material harvested = switch (material) {
                case WHEAT -> Material.WHEAT;
                case CARROTS -> Material.CARROT;
                case POTATOES -> Material.POTATO;
                case BEETROOTS -> Material.BEETROOT;
                case NETHER_WART -> Material.NETHER_WART;
                case COCOA -> Material.COCOA_BEANS;
                default -> null;
            };
            if (harvested != null) {
                incrementTarget(p, "harvest:" + harvested.name(), 1);
                matureCrop = true;
            }
        }
        // Melons and pumpkins are harvestable blocks rather than Ageable crops.
        if (material == Material.MELON) {
            incrementTarget(p, "harvest:MELON_SLICE", 1);
            matureCrop = true;
        } else if (material == Material.PUMPKIN) {
            incrementTarget(p, "harvest:PUMPKIN", 1);
            matureCrop = true;
        }
        if (matureCrop) recordGlobalProgress(p, "crop", 1);
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (event.isCancelled()) return;
        data.set("placed-blocks." + placedBlockKey(event.getBlockPlaced()), true);
    }

    private String placedBlockKey(org.bukkit.block.Block block) {
        return block.getWorld().getUID() + "_" + block.getX() + "_" + block.getY() + "_" + block.getZ();
    }

    @EventHandler
    public void onCreatureSpawn(org.bukkit.event.entity.CreatureSpawnEvent event) {
        org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason reason = event.getSpawnReason();
        if (reason == org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.SPAWNER
                || reason == org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.SPAWNER_EGG
                || reason == org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.DISPENSE_EGG) {
            event.getEntity().getPersistentDataContainer().set(excludedMobKey, PersistentDataType.BYTE, (byte) 1);
        }
    }

    @EventHandler
    public void onKill(EntityDeathEvent event) {
        if (event.getEntity().getPersistentDataContainer().has(excludedMobKey, PersistentDataType.BYTE)) return;
        Player p = event.getEntity().getKiller();
        if (p == null || p.getGameMode() == org.bukkit.GameMode.CREATIVE || p.getGameMode() == org.bukkit.GameMode.SPECTATOR) return;
        incrementTarget(p, "kill:" + event.getEntityType().name(), 1);
        if (event.getEntity() instanceof org.bukkit.entity.Monster) recordGlobalProgress(p, "mob", 1);
    }

    @EventHandler
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)
                || player.getGameMode() == org.bukkit.GameMode.CREATIVE
                || player.getGameMode() == org.bukkit.GameMode.SPECTATOR) return;
        ItemStack result = event.getCurrentItem();
        if (result != null && result.getType() != Material.AIR) {
            incrementTarget(player, "collect:" + result.getType().name(), craftedOutputAmount(event, result));
        }
    }

    private int craftedOutputAmount(CraftItemEvent event, ItemStack result) {
        if (!event.isShiftClick()) return result.getAmount();
        int maxCrafts = Integer.MAX_VALUE;
        boolean hasIngredient = false;
        for (ItemStack ingredient : event.getInventory().getMatrix()) {
            if (ingredient == null || ingredient.getType() == Material.AIR) continue;
            hasIngredient = true;
            maxCrafts = Math.min(maxCrafts, ingredient.getAmount());
        }
        if (!hasIngredient || maxCrafts == Integer.MAX_VALUE) return result.getAmount();
        long possible = (long) maxCrafts * result.getAmount();
        int capacity = 0;
        int maxStack = result.getMaxStackSize();
        for (ItemStack item : event.getWhoClicked().getInventory().getStorageContents()) {
            if (item == null || item.getType() == Material.AIR) capacity += maxStack;
            else if (item.isSimilar(result)) capacity += Math.max(0, maxStack - item.getAmount());
        }
        return (int) Math.max(result.getAmount(), Math.min(possible, capacity));
    }

    @EventHandler
    public void onPickup(PlayerAttemptPickupItemEvent event) {
        if (event.isCancelled()) return;
        Player p = event.getPlayer();
        if (p.getGameMode() == org.bukkit.GameMode.CREATIVE || p.getGameMode() == org.bukkit.GameMode.SPECTATOR) return;
        // Player-dropped items can be dropped and picked up repeatedly, so they must not
        // count as newly collected quest items.
        if (event.getItem().getThrower() != null) return;
        Material material = event.getItem().getItemStack().getType();
        incrementTarget(p, "collect:" + material.name(), event.getItem().getItemStack().getAmount());
    }

    @EventHandler
    public void onFurnaceExtract(FurnaceExtractEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == org.bukkit.GameMode.CREATIVE
                || player.getGameMode() == org.bukkit.GameMode.SPECTATOR) return;
        Material material = event.getItemType();
        int amount = event.getItemAmount();
        if (material == null || material == Material.AIR || amount <= 0) return;
        // Count only the items actually extracted from furnace-family output slots.
        // FurnaceExtractEvent covers furnaces, blast furnaces, and smokers, including shift-click extraction.
        incrementTarget(player, "collect:" + material.name(), amount);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player p = event.getPlayer();
        UUID id = p.getUniqueId();
        if (event.isCancelled() || event.getTo() == null || event.getFrom().getWorld() != event.getTo().getWorld()) {
            travelRemainders.remove(id);
            return;
        }
        if (p.getGameMode() == org.bukkit.GameMode.CREATIVE || p.getGameMode() == org.bukkit.GameMode.SPECTATOR
                || data.getString(id + ".active", "").isBlank() || !matchesTarget(id, "travel")) {
            travelRemainders.remove(id);
            return;
        }
        double step = event.getFrom().distance(event.getTo());
        if (step > 10) {
            travelRemainders.remove(id);
            return;
        }
        double accumulated = travelRemainders.getOrDefault(id, 0.0) + step;
        int wholeBlocks = (int) Math.floor(accumulated);
        travelRemainders.put(id, accumulated - wholeBlocks);
        if (wholeBlocks > 0) incrementTarget(p, "travel", wholeBlocks);
    }

    @EventHandler
    public void onFish(PlayerFishEvent event) {
        if (event.isCancelled() || event.getState() != PlayerFishEvent.State.CAUGHT_FISH || !(event.getCaught() instanceof org.bukkit.entity.Item caught)) return;
        Player player = event.getPlayer();
        if (player.getGameMode() == org.bukkit.GameMode.CREATIVE || player.getGameMode() == org.bukkit.GameMode.SPECTATOR) return;
        incrementTarget(player, "fish:" + caught.getItemStack().getType().name(), 1);
        recordGlobalProgress(player, "fish", 1);
    }

    @EventHandler
    public void onBreed(EntityBreedEvent event) {
        if (!(event.getBreeder() instanceof Player player)) return;
        incrementTarget(player, "breed:ANY", 1);
    }

    @EventHandler
    public void onFeed(PlayerInteractEntityEvent event) {
        if (event.isCancelled() || event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        if (!(event.getRightClicked() instanceof Animals animal) || animal.getAge() != 0
                || animal.getLoveModeTicks() > 0) return;
        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held == null || held.getType() == Material.AIR) return;
        Material food = held.getType();
        boolean validFeed = switch (animal.getType()) {
            case COW, SHEEP, GOAT -> food == Material.WHEAT;
            case PIG -> food == Material.CARROT || food == Material.POTATO || food == Material.BEETROOT;
            case CHICKEN -> food == Material.WHEAT_SEEDS || food == Material.BEETROOT_SEEDS || food == Material.MELON_SEEDS || food == Material.PUMPKIN_SEEDS || food == Material.TORCHFLOWER_SEEDS;
            case RABBIT -> food == Material.CARROT || food == Material.GOLDEN_CARROT || food == Material.DANDELION;
            default -> false;
        };
        if (!validFeed) return;
        int before = held.getAmount();
        UUID animalId = animal.getUniqueId();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || !animal.isValid() || !animal.getUniqueId().equals(animalId)
                    || animal.getLoveModeTicks() <= 0) return;
            ItemStack current = player.getInventory().getItemInMainHand();
            int after = current != null && current.getType() == food ? current.getAmount() : 0;
            if (after < before) incrementTarget(player, "feed:" + animal.getType().name(), 1);
        });
    }

    private void incrementTarget(Player player, String target, int amount) {
        if (amount <= 0) return;
        UUID id = player.getUniqueId();
        if (data.getString(id + ".active", "").isBlank()) return;
        int count = data.getInt(id + ".objective-count", 0);
        boolean changed = false;
        for (int i = 0; i < count; i++) {
            String base = id + ".objectives." + i;
            if (!target.equals(data.getString(base + ".target", ""))) continue;
            int goal = data.getInt(base + ".goal", 1);
            int progress = Math.min(goal, data.getInt(base + ".progress", 0) + amount);
            data.set(base + ".progress", progress);
            changed = true;
        }
        if (!changed) return;
        if (allComplete(id)) {
            int reward = Math.max(0, data.getInt(id + ".reward", 0));
            String activeQuestId = data.getString(id + ".active", "");
            String tier = data.getString(id + ".risk-tier", "achievement");
            if (activeQuestId.startsWith("pack:")) {
                String packId = activeQuestId.substring("pack:".length());
                data.set("completed-packs." + id + "." + packId, true);
            }
            data.set(id.toString(), null);
            save();
            plugin.getHudManager().hideActiveQuest(player);
            if (reward > 0) deliverQuestCoins(id, reward, player, "Quest reward");
            String title = switch (tier) {
                case "easy" -> "EASY QUEST COMPLETE";
                case "hard" -> "HARD QUEST COMPLETE";
                case "master" -> "MASTER QUEST COMPLETE";
                case "secret" -> "SECRET QUEST COMPLETE";
                case "legendary" -> "LEGENDARY RISK COMPLETE";
                default -> activeQuestId.startsWith("pack:") ? "ACHIEVEMENT COMPLETE" : "RISK QUEST COMPLETE";
            };
            player.sendTitle(ChatColor.GREEN + "✦ " + title + " ✦",
                    ChatColor.GOLD + "+" + reward + " coins", 10, 60, 15);
            String publicLabel = switch (tier) {
                case "easy" -> "an Easy Achievement";
                case "hard" -> "a Hard Achievement";
                case "master" -> "a Master Achievement";
                case "secret" -> "a Secret Quest";
                case "legendary" -> "a Legendary Risk Quest";
                default -> activeQuestId.startsWith("pack:") ? "an Achievement" : "a Risk Quest";
            };
            Bukkit.broadcastMessage(ChatColor.GOLD + "[XZH] " + ChatColor.YELLOW + player.getName()
                    + ChatColor.GREEN + " completed " + ChatColor.AQUA + publicLabel
                    + ChatColor.GREEN + "! " + ChatColor.GOLD + "+" + reward + " coins");
        } else updateHud(player);
        save();
    }

    private boolean matchesTarget(UUID id, String target) {
        int count = data.getInt(id + ".objective-count", 0);
        for (int i = 0; i < count; i++) if (target.equals(data.getString(id + ".objectives." + i + ".target", ""))) return true;
        return false;
    }

    private boolean allComplete(UUID id) {
        int count = data.getInt(id + ".objective-count", 0);
        if (count <= 0) return false;
        for (int i = 0; i < count; i++) {
            String base = id + ".objectives." + i;
            if (data.getInt(base + ".progress", 0) < data.getInt(base + ".goal", 1)) return false;
        }
        return true;
    }

    private int refundPercentForId(String id) {
        return 20 + Math.floorMod(id.hashCode(), 31);
    }

    private int rewardForPack(Pack pack) {
        String id = pack.id();
        if (id.startsWith("easy_") || id.startsWith("hard_") || id.startsWith("master_")) return pack.reward();
        double multiplier = 2.0;
        if (List.of("dragon_slayer", "wither_siege", "debris_hunter", "end_expedition",
                "nether_blaze", "nether_hunter", "raid_supply", "shulker_sweep",
                "end_city_scout", "guardian_patrol", "deep_sea").contains(id)) {
            multiplier = 3.5;
        } else if (pack.objectives().size() > 1 || packTimerLimitMinutes(id) > 0) {
            multiplier = 2.75;
        } else if (pack.cost() >= 500) {
            multiplier = 2.5;
        }
        return Math.max(pack.reward(), (int) Math.ceil(pack.cost() * multiplier));
    }

    private int packTimerLimitMinutes(String packId) {
        // A subset of generated packs remain timed, with stricter tiers offering more timed challenges.
        if (packId.startsWith("easy_") || packId.startsWith("hard_") || packId.startsWith("master_")) {
            String tier = packId.substring(0, packId.indexOf('_'));
            int number;
            try { number = Integer.parseInt(packId.substring(packId.indexOf('_') + 1)); }
            catch (NumberFormatException ignored) { return 0; }
            if (tier.equals("easy") && number % 10 == 0) return 30;
            if (tier.equals("hard") && number % 5 == 0) return 30;
            if (tier.equals("hard") && number % 5 == 1) return 60;
            if (tier.equals("master") && number % 4 == 0) return 30;
            if (tier.equals("master") && number % 4 == 1) return 60;
            return 0;
        }
        return switch (packId) {
            case "debris_hunter", "nether_hunter", "nether_blaze", "ocean_fisher", "salmon_run", "deep_sea", "explorer_trail", "monster_patrol", "raid_supply" -> 30;
            case "diamond_miner", "pearl_trader", "animal_breeder", "cattle_feed", "sheep_flock", "woodcutter", "builder_supply" -> 60;
            default -> 0;
        };
    }

    private long timerLimitSeconds(UUID playerId, String activeId) {
        if (activeId == null || activeId.isBlank()) return 0L;
        if (activeId.startsWith("risk:legendary:")) return 180L * 60L;
        if (activeId.startsWith("risk:")) return 60L * 60L;
        if (!activeId.startsWith("pack:")) return 0L;
        return packTimerLimitMinutes(activeId.substring("pack:".length())) * 60L;
    }

    private void expireTimedQuestIfNeeded(Player player) {
        UUID id = player.getUniqueId();
        String active = data.getString(id + ".active", "");
        long limit = timerLimitSeconds(id, active);
        if (limit <= 0) return;
        long started = data.getLong(id + ".started-at", System.currentTimeMillis());
        long remaining = limit - Math.max(0L, (System.currentTimeMillis() - started) / 1000L);
        if (remaining <= 0L) {
            data.set(id.toString(), null);
            save();
            plugin.getHudManager().hideActiveQuest(player);
            plugin.tellPublic(player, "Quest timer expired. The quest was forfeited; choose another challenge.");
            return;
        }
        if (remaining <= 5L * 60L && !data.getBoolean(id + ".warned-5m", false)) {
            data.set(id + ".warned-5m", true);
            save();
            plugin.tellPublic(player, "Quest warning: 5 minutes or less remain on your timed quest!");
        }
        if (remaining <= 60L && !data.getBoolean(id + ".warned-1m", false)) {
            data.set(id + ".warned-1m", true);
            save();
            plugin.tellPublic(player, "Quest warning: 1 minute or less remains on your timed quest!");
        }
    }

    private void updateHud(Player player) {
        expireTimedQuestIfNeeded(player);
        UUID id = player.getUniqueId();
        String active = data.getString(id + ".active", "");
        if (active.isBlank()) { plugin.getHudManager().hideActiveQuest(player); return; }
        int count = data.getInt(id + ".objective-count", 0);
        int done = 0, total = 0;
        List<String> descriptions = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String base = id + ".objectives." + i;
            int goal = Math.max(1, data.getInt(base + ".goal", 1));
            int progress = Math.min(goal, data.getInt(base + ".progress", 0));
            done += progress;
            total += goal;
            descriptions.add(data.getString(base + ".description", "Objective") + " " + progress + "/" + goal);
        }
        String display = String.join(" | ", descriptions);
        long limit = timerLimitSeconds(id, active);
        if (limit > 0) {
            long started = data.getLong(id + ".started-at", System.currentTimeMillis());
            long remaining = Math.max(0L, limit - Math.max(0L, (System.currentTimeMillis() - started) / 1000L));
            long minutes = remaining / 60L;
            long seconds = remaining % 60L;
            display += " | " + ChatColor.YELLOW + "Time " + String.format(Locale.ROOT, "%02d:%02d", minutes, seconds) + " left";
        }
        plugin.getHudManager().showActiveQuest(player, data.getString(id + ".name", "Active quest"),
                display, total <= 0 ? 0 : Math.min(1.0, (double) done / total));
    }

    private void openCommunity(Player player) {
        resetEventIfNeeded();
        UUID id = player.getUniqueId();
        int progress = data.getInt("event.progress", 0);
        int goal = data.getInt("event.goal", configuredEventGoal());
        int contribution = data.getInt("event.contribution." + id, 0);
        long remaining = Math.max(0L, data.getLong("event.ends-at", System.currentTimeMillis()) - System.currentTimeMillis());
        boolean completed = data.getBoolean("event.completed", false);
        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.DARK_PURPLE + "XZH Community");
        add(inv, 13, Material.NETHER_STAR, ChatColor.GOLD + data.getString("event.name", "Global Quest"), "global-info", "global",
            List.of(ChatColor.WHITE + data.getString("event.description", "Work together to complete the objective."),
                    ChatColor.AQUA + "Progress: " + progress + "/" + goal,
                    ChatColor.AQUA + "Your contribution: " + contribution,
                    ChatColor.GOLD + "Reward: up to " + data.getInt("event.reward", configuredEventReward()) + " coins",
                    ChatColor.YELLOW + "Minimum contribution: 5%",
                    ChatColor.YELLOW + "Time left: " + formatGlobalTime(remaining),
                    completed ? ChatColor.GREEN + "Completed! Rewards have been paid." : ChatColor.GRAY + "Contribute before the deadline to earn the reward."));
        add(inv, 11, Material.GOLD_INGOT, ChatColor.GREEN + "Contributor rewards", "global-info", "global",
            List.of(ChatColor.AQUA + "Rewards are paid automatically.",
                    ChatColor.YELLOW + "5% minimum contribution required.",
                    ChatColor.GRAY + "Higher contribution earns more."));
        add(inv, 15, Material.ARROW, ChatColor.WHITE + "Back", "back", "");
        player.openInventory(inv);
    }

    private void recordGlobalProgress(Player player, String target, int amount) {
        if (amount <= 0 || data.getBoolean("event.completed", false)) return;
        if (data.getLong("event.ends-at", 0L) <= System.currentTimeMillis()) {
            resetEventIfNeeded();
            if (data.getBoolean("event.completed", false)) return;
        }
        if (!target.equals(data.getString("event.target", "ore"))) return;
        int goal = Math.max(1, data.getInt("event.goal", configuredEventGoal()));
        int previousProgress = Math.min(goal, data.getInt("event.progress", 0));
        int credited = Math.min(amount, Math.max(0, goal - previousProgress));
        if (credited <= 0) return;
        int progress = previousProgress + credited;
        data.set("event.progress", progress);
        String contributionPath = "event.contribution." + player.getUniqueId();
        data.set(contributionPath, data.getInt(contributionPath, 0) + credited);
        save();
        if (progress >= goal) completeGlobalQuest();
    }

    private void completeGlobalQuest() {
        if (data.getBoolean("event.completed", false)) return;
        data.set("event.completed", true);
        save();
        int baseReward = data.getInt("event.reward", configuredEventReward());
        int goal = Math.max(1, data.getInt("event.goal", configuredEventGoal()));
        org.bukkit.configuration.ConfigurationSection contributors = data.getConfigurationSection("event.contribution");
        int paid = 0;
        if (contributors != null) {
            for (String rawId : contributors.getKeys(false)) {
                try {
                    UUID id = UUID.fromString(rawId);
                    int contribution = data.getInt("event.contribution." + rawId, 0);
                    String paidPath = "event.paid." + rawId;
                    if (data.getBoolean(paidPath, false)) continue;
                    int minimum = Math.max(1, (int) Math.ceil(goal * 0.05));
                    if (contribution < minimum) {
                        Player online = Bukkit.getPlayer(id);
                        if (online != null) plugin.tellPublic(online, ChatColor.RED + "Community reward missed: contribute at least 5% next time.");
                        continue;
                    }
                    String date = LocalDate.now().toString();
                    String dailyPath = "community-claims." + date + "." + rawId;
                    int cap = configuredEventClaims();
                    if (cap > 0 && data.getInt(dailyPath, 0) >= cap) {
                        Player online = Bukkit.getPlayer(id);
                        if (online != null) plugin.tellPublic(online, ChatColor.RED + "Daily Community reward limit reached.");
                        continue;
                    }
                    double share = contribution >= Math.ceil(goal * 0.20) ? 1.0
                            : contribution >= Math.ceil(goal * 0.10) ? 0.75 : 0.50;
                    int payout = Math.max(1, (int) Math.floor(baseReward * share));
                    data.set(paidPath, true);
                    data.set(dailyPath, data.getInt(dailyPath, 0) + 1);
                    Player online = Bukkit.getPlayer(id);
                    deliverQuestCoins(id, payout, online, "Community reward");
                    paid++;
                } catch (IllegalArgumentException ignored) { }
            }
        }
        save();
        Bukkit.broadcastMessage(ChatColor.GOLD + "[XZH] " + ChatColor.GREEN + "Community quest completed! "
                + ChatColor.YELLOW + paid + ChatColor.GREEN + " players rewarded.");
        startNewGlobalQuest(System.currentTimeMillis(), true);
    }

    private String formatGlobalTime(long millis) {
        long minutes = Math.max(0L, millis) / 60000L;
        return (minutes / 60L) + "h " + (minutes % 60L) + "m";
    }

    private void recordEventOre(Player player) {
        resetEventIfNeeded();
        int goal = data.getInt("event.goal", configuredEventGoal());
        int progress = Math.min(goal, data.getInt("event.progress", 0) + 1);
        data.set("event.progress", progress);
        UUID id = player.getUniqueId();
        String path = "event.contribution." + id;
        data.set(path, data.getInt(path, 0) + 1);
        save();
    }

    /** Compact shared quest status for the Survival plugin's TAB footer. */
    public String getGlobalQuestTabText() {
        resetEventIfNeeded();
        int progress = Math.max(0, data.getInt("event.progress", 0));
        int goal = Math.max(1, data.getInt("event.goal", configuredEventGoal()));
        int percent = Math.min(100, (int) Math.floor(progress * 100.0 / goal));
        long remaining = Math.max(0L, data.getLong("event.ends-at", System.currentTimeMillis()) - System.currentTimeMillis());
        String target = data.getString("event.target", "ore");
        String objective = switch (target.toLowerCase(Locale.ROOT)) {
            case "ore" -> "Mine " + progress + "/" + goal + " Ores";
            case "mob" -> "Defeat " + progress + "/" + goal + " Mobs";
            case "fish" -> "Catch " + progress + "/" + goal + " Fish";
            case "wood" -> "Chop " + progress + "/" + goal + " Logs";
            case "crop" -> "Harvest " + progress + "/" + goal + " Crops";
            default -> data.getString("event.description", "Complete the community objective") + " (" + progress + "/" + goal + ")";
        };
        return "§6Community: §f" + objective + " §8(" + percent + "%, " + formatGlobalTime(remaining) + ")";
    }

    private void broadcastGlobalProgress() {
        resetEventIfNeeded();
        if (data.getBoolean("event.completed", false)) return;
        Bukkit.broadcastMessage(ChatColor.GOLD + "[XZH] " + ChatColor.AQUA + "Community: "
                + ChatColor.YELLOW + data.getString("event.name", "Community quest")
                + ChatColor.AQUA + " " + data.getInt("event.progress", 0) + "/"
                + data.getInt("event.goal", configuredEventGoal())
                + ChatColor.GRAY + " · " + formatGlobalTime(Math.max(0L,
                data.getLong("event.ends-at", 0L) - System.currentTimeMillis())) + " left.");
    }

    private void refreshRiskShopIfDue() {
        long now = System.currentTimeMillis();
        long next = data.getLong("single-shop.next-refresh", 0L);
        boolean refresh = next <= 0 || now >= next;
        if (refresh) {
            data.set("single-shop.next-refresh", nextRefresh(now));
            data.set("single-shop.refresh-id", data.getInt("single-shop.refresh-id", 0) + 1);
            List<Integer> candidates = new ArrayList<>();
            for (int i = 0; i < randomPool.size(); i++) candidates.add(i);
            Collections.shuffle(candidates, random);
            List<Integer> choices = new ArrayList<>();
            Set<String> categories = new HashSet<>();
            for (int candidate : candidates) {
                if (categories.add(questCategory(randomPool.get(candidate)))) choices.add(candidate);
                if (choices.size() == 5) break;
            }
            for (int candidate : candidates) {
                if (choices.size() == 5) break;
                if (!choices.contains(candidate)) choices.add(candidate);
            }
            for (int i = 0; i < choices.size(); i++) {
                data.set("single-shop.offers." + i, choices.get(i));
                createRiskOfferDetails(i);
            }
            data.set("single-shop.legendary.cost", 2000 + random.nextInt(3001));
            data.set("single-shop.legendary.profit", 6000 + random.nextInt(4001));
            data.set("single-shop.legendary.refund-percent", 20 + random.nextInt(31));
            save();
        } else {
            ensureRiskOfferDetails();
        }
    }

    private void createRiskOfferDetails(int slot) {
        int poolIndex = Math.floorMod(data.getInt("single-shop.offers." + slot, slot), randomPool.size());
        RandomQuest template = randomPool.get(poolIndex);
        double scale = 1.5 + random.nextDouble();
        data.set("single-shop.risk-goal." + slot, Math.max(1, (int) Math.ceil(template.objective().goal() * scale)));
        data.set("single-shop.risk-cost." + slot, 500 + random.nextInt(1001));
        data.set("single-shop.risk-profit." + slot, 2000 + random.nextInt(4001));
        data.set("single-shop.risk-refund-percent." + slot, 20 + random.nextInt(31));
    }

    private void ensureRiskOfferDetails() {
        boolean changed = false;
        for (int i = 0; i < 5; i++) {
            if (!data.contains("single-shop.risk-goal." + i)
                    || !data.contains("single-shop.risk-cost." + i)
                    || !data.contains("single-shop.risk-profit." + i)
                    || !data.contains("single-shop.risk-refund-percent." + i)) {
                createRiskOfferDetails(i);
                changed = true;
            }
        }
        if (!data.contains("single-shop.legendary.cost")
                || !data.contains("single-shop.legendary.profit")
                || !data.contains("single-shop.legendary.refund-percent")) {
            data.set("single-shop.legendary.cost", 2000 + random.nextInt(3001));
            data.set("single-shop.legendary.profit", 6000 + random.nextInt(4001));
            data.set("single-shop.legendary.refund-percent", 20 + random.nextInt(31));
            changed = true;
        }
        if (changed) save();
    }

    private String questCategory(RandomQuest quest) {
        String target = quest.objective().target();
        String material = target.contains(":") ? target.substring(target.indexOf(':') + 1) : target;
        if (target.startsWith("fish:")) return "fishing";
        if (target.equals("travel")) return "travel";
        if (target.startsWith("feed:") || target.startsWith("breed:")) return "farming";
        if (target.startsWith("break:") && (material.endsWith("_LOG") || material.endsWith("_STEM") || material.equals("BAMBOO"))) return "woodcutting";
        if (target.startsWith("break:")) return "mining";
        if (target.startsWith("kill:") || material.equals("BLAZE_ROD") || material.equals("ENDER_PEARL") || material.equals("WITHER_SKELETON_SKULL")) return "combat";
        if (List.of("WHEAT", "CARROT", "POTATO", "BEETROOT", "MELON_SLICE", "PUMPKIN", "SUGAR_CANE", "NETHER_WART", "COCOA_BEANS", "WHEAT_SEEDS", "BEETROOT_SEEDS").contains(material)) return "farming";
        if (List.of("BREAD", "COOKED_BEEF", "PORKCHOP", "CHICKEN", "RAW_RABBIT").contains(material)) return "food";
        if (material.equals("QUARTZ") || material.equals("RAW_IRON") || material.equals("RAW_COPPER") || material.equals("LAPIS_LAZULI") || material.equals("REDSTONE")) return "mining";
        return "gathering";
    }

    private long nextRefresh(long now) { return ((now / (2L * 60L * 60L * 1000L)) + 1L) * (2L * 60L * 60L * 1000L); }

    private void refreshSecretQuestIfDue() {
        long now = System.currentTimeMillis();
        if (data.getLong("single-shop.secret.next-refresh", 0L) <= now) refreshSecretQuestNow();
    }

    private void refreshSecretQuestNow() {
        long now = System.currentTimeMillis();
        data.set("single-shop.secret.offer", random.nextInt(randomPool.size()));
        data.set("single-shop.secret.cost", 300 + random.nextInt(501));
        data.set("single-shop.secret.profit", 1500 + random.nextInt(3501));
        data.set("single-shop.secret.refund-percent", 20 + random.nextInt(31));
        int secretIndex = data.getInt("single-shop.secret.offer", 0);
        RandomQuest secretTemplate = randomPool.get(Math.floorMod(secretIndex, randomPool.size()));
        data.set("single-shop.secret.goal", Math.max(1, (int) Math.ceil(secretTemplate.objective().goal() * (1.5 + random.nextDouble()))));
        data.set("single-shop.secret.next-refresh", ((now / (30L * 60L * 1000L)) + 1L) * (30L * 60L * 1000L));
        save();
    }

    private String unlockDescription(String id) {
        return switch (id) {
            case "stone_survey" -> "Craft iron tools";
            case "coal_contract" -> "Smelt an iron ingot";
            case "iron_miner", "copper_miner", "deepslate_delver" -> "Craft iron tools";
            case "gold_digger" -> "Upgrade your tools with gold";
            case "diamond_miner" -> "Mine a diamond";
            case "quartz_quarry", "nether_hunter", "magma_march", "hoglin_hunt" -> "Enter the Nether";
            case "debris_hunter" -> "Obtain Ancient Debris";
            case "nether_blaze" -> "Obtain a Blaze Rod";
            case "end_expedition", "pearl_trader" -> "Enter the End";
            case "dragon_slayer" -> "Defeat the Ender Dragon";
            case "ocean_fisher" -> "Catch a fish";
            case "salmon_run", "deep_sea" -> "Catch a fish";
            case "farm_starter", "root_vegetables" -> "Plant and grow a crop";
            case "baked_goods", "meat_supply" -> "Complete the Balanced Diet food challenge";
            case "animal_breeder", "cattle_feed", "sheep_flock" -> "Breed an animal";
            case "woodcutter", "builder_supply" -> "Upgrade your tools";
            case "explorer_trail" -> "Discover many Overworld biomes";
            case "monster_patrol" -> "Defeat a hostile mob";
            case "raid_supply" -> "Fire a crossbow";
            case "wither_siege" -> "Summon the Wither";
            default -> "Complete the related Minecraft challenge";
        };
    }

    private boolean hasAdvancement(Player player, String rawKey) {
        NamespacedKey key = NamespacedKey.fromString(rawKey);
        if (key == null) return false;
        Advancement advancement = Bukkit.getAdvancement(key);
        if (advancement == null) return false;
        return player.getAdvancementProgress(advancement).isDone();
    }

    private void resetEventIfNeeded() {
        long now = System.currentTimeMillis();
        long endAt = data.getLong("event.ends-at", 0L);
        if (data.getString("event.name", "").isBlank() || endAt <= now) {
            startNewGlobalQuest(now, true);
            return;
        }
        if (!data.contains("event.progress")) data.set("event.progress", 0);
        if (!data.contains("event.reward")) data.set("event.reward", configuredEventReward());
        if (!data.contains("event.completed")) data.set("event.completed", false);
    }

    private void startNewGlobalQuest(long now, boolean announce) {
        GlobalQuest selected = globalQuestPool.get(random.nextInt(globalQuestPool.size()));
        int goal = Math.max(1, (int) Math.round(selected.goal() * (0.80 + random.nextDouble() * 0.40)));
        int reward = rewardForGlobalQuest(selected, goal);
        data.set("event", null);
        data.set("event.name", selected.name());
        data.set("event.icon", selected.icon().name());
        data.set("event.target", selected.target());
        data.set("event.description", selected.description());
        data.set("event.progress", 0);
        data.set("event.goal", goal);
        data.set("event.reward", reward);
        data.set("event.ends-at", now + 4L * 60L * 60L * 1000L);
        data.set("event.completed", false);
        data.set("event.started-at", now);
        save();
        if (announce) {
            Bukkit.broadcastMessage(ChatColor.GOLD + "[XZH] " + ChatColor.AQUA + "New Community quest: "
                    + ChatColor.YELLOW + selected.name() + ChatColor.GRAY + ". Rewards up to "
                    + ChatColor.GOLD + reward + " coins" + ChatColor.GRAY + ".");
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = ChatColor.stripColor(event.getView().getTitle());
        if (title == null || !(title.equals("XZH Quests") || title.equals("XZH Secret & Risk Quests")
                || title.startsWith("XZH Achievement Packs") || title.equals("XZH Pack Tiers") || title.startsWith("XZH Easy Packs") || title.startsWith("XZH Hard Packs") || title.startsWith("XZH Master Packs") || title.equals("Confirm quest pack")
                || title.equals("XZH Community"))) return;
        event.setCancelled(true);
        ItemStack item = event.getCurrentItem();
        if (item == null || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        String action = meta.getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
        String value = meta.getPersistentDataContainer().get(valueKey, PersistentDataType.STRING);
        if (action == null) return;
        switch (action) {
            case "close" -> player.closeInventory();
            case "back-shop" -> plugin.getShopManager().openMain(player);
            case "section-risk" -> openRiskQuests(player);
            case "section-packs" -> openPacks(player);
            case "section-community" -> openCommunity(player);
            case "risk-buy" -> buyRiskOffer(player, value);
            case "risk-secret-buy" -> buySecretQuest(player, value);
            case "risk-legendary-buy" -> buyLegendaryRisk(player);
            case "risk-info" -> plugin.tellPublic(player, ChatColor.AQUA + "Risk contracts refresh every 2 hours; secret quests every 30 minutes.");

            case "pack-tier" -> openPacks(player, value, 0);
            case "tier-locked" -> {
                plugin.tellPublic(player, tierUnlockDescription(value));
                openPacks(player, value, 0);
            }
            case "pack-page" -> {
                String[] parts = value.split(":");
                try { openPacks(player, parts[0], Integer.parseInt(parts[1])); }
                catch (RuntimeException ignored) { openPacks(player); }
            }
            case "back-tiers" -> openPacks(player);
            case "pack-completed" -> plugin.tellPublic(player, "This achievement pack is permanently completed and cannot reward you again.");
            case "pack-select" -> packs.stream().filter(p -> p.id().equals(value)).findFirst().ifPresent(p -> confirmPack(player, p));
            case "pack-buy" -> packs.stream().filter(p -> p.id().equals(value)).findFirst().ifPresent(p -> buyPack(player, p));
            case "global-claim" -> plugin.tellPublic(player, "Global quest rewards are paid automatically to contributors when the community completes the objective.");
            case "back" -> open(player);
            case "back-packs" -> { Pack pack = packs.stream().filter(p -> p.id().equals(value)).findFirst().orElse(null); if (pack == null) openPacks(player); else openPacks(player, tierOf(pack), 0); }
            default -> { }
        }
    }

    private void add(Inventory inv, int slot, Material material, String name, String action, String value) {
        add(inv, slot, material, name, action, value, List.of());
    }

    private void add(Inventory inv, int slot, Material material, String name, String action, String value, List<String> lore) {
        if (slot >= inv.getSize()) return;
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
        meta.getPersistentDataContainer().set(valueKey, PersistentDataType.STRING, value);
        stack.setItemMeta(meta);
        inv.setItem(slot, stack);
    }

    private void deliverQuestCoins(UUID id, int amount, Player online, String label) {
        if (amount <= 0) return;
        if (online == null) {
            if (plugin.getBankManager() != null) {
                plugin.getBankManager().setBalance(id, plugin.getBankManager().getBalance(id) + amount);
                data.set("pending-bank-notice." + id, data.getInt("pending-bank-notice." + id, 0) + amount);
                save();
            } else {
                plugin.setBalance(id, plugin.getBalance(id) + amount);
            }
            return;
        }
        if (canFitPocketBalance(online, amount)) {
            plugin.setBalance(id, plugin.getBalance(id) + amount);
            plugin.tellPublic(online, ChatColor.GREEN + label + " added to " + ChatColor.YELLOW + "pocket"
                    + ChatColor.GREEN + ": " + ChatColor.GOLD + "+" + amount + " coins");
        } else {
            plugin.getBankManager().setBalance(id, plugin.getBankManager().getBalance(id) + amount);
            plugin.tellPublic(online, ChatColor.YELLOW + "Inventory full. " + ChatColor.GREEN + label
                    + " added to " + ChatColor.GOLD + "bank" + ChatColor.GREEN + ": "
                    + ChatColor.GOLD + "+" + amount + " coins");
        }
    }

    private boolean canFitPocketBalance(Player player, int amount) {
        long total = Math.round(plugin.getBalance(player.getUniqueId())) + (long) amount;
        if (total > plugin.getPocketBalanceLimit()) return false;
        int freeSlots = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item == null || item.getType() == Material.AIR || plugin.getPocketCoinManager().isCoin(item)) freeSlots++;
        }
        long remaining = total;
        int requiredSlots = 0;
        for (int denomination : new int[]{1000, 100, 10, 1}) {
            long count = remaining / denomination;
            remaining %= denomination;
            requiredSlots += (int) ((count + 63L) / 64L);
        }
        return requiredSlots <= freeSlots;
    }

    @EventHandler
    public void onQuestRewardJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String path = "pending-bank-notice." + player.getUniqueId();
        int amount = data.getInt(path, 0);
        if (amount > 0) {
            data.set(path, null);
            save();
            plugin.tellPublic(player, ChatColor.GREEN + "Quest reward added to " + ChatColor.GOLD
                    + "bank" + ChatColor.GREEN + ": " + ChatColor.GOLD + "+" + amount + " coins");
        }
    }

    private int configuredEventGoal() { return Math.max(1, Math.min(1_000_000, plugin.getConfig().getInt("quests.global-event.goal", 500))); }
    private int rewardForGlobalQuest(GlobalQuest quest, int goal) {
        double effort = switch (quest.target()) {
            case "mob" -> 1.35;
            case "ore" -> 1.25;
            case "fish" -> 1.55;
            case "wood" -> 0.80;
            case "crop" -> 0.75;
            default -> 1.0;
        };
        double base = Math.max(100.0, Math.min(5000.0, goal * effort * 2.0));
        double varied = base * (0.80 + random.nextDouble() * 0.40);
        int ceiling = Math.max(100, Math.min(10000, plugin.getConfig().getInt("quests.global-event.reward-ceiling", 5000)));
        return Math.max(100, Math.min(ceiling, (int) Math.round(varied)));
    }
    private int configuredEventReward() { return Math.max(100, Math.min(10000, plugin.getConfig().getInt("quests.global-event.reward", 200))); }
    private int configuredEventClaims() { return Math.max(0, Math.min(1_000_000, plugin.getConfig().getInt("quests.global-event.claims-per-day", 25))); }

    public void shutdown() { save(); }

    private void save() {
        try { data.save(file); } catch (IOException e) { plugin.getLogger().severe("Could not save quests.yml: " + e.getMessage()); }
    }
}