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
import org.bukkit.entity.Monster;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
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

/** Random single quests, achievement-unlocked multi-objective packs, and shared global quests. */
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
    private final NamespacedKey monsterLevelKey;
    private final Set<String> placedBlocks = new HashSet<>();
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
        new Pack("nether_hunter", "Nether Trophy Hunt", Material.WITHER_SKELETON_SKULL, 400, 560, "minecraft:nether/root", List.of(new Objective("kill:WITHER_SKELETON", 40, "Kill 40 Wither Skeletons; skull drops are not required"))),
        new Pack("nether_blaze", "Fortress Conqueror", Material.BLAZE_ROD, 550, 825, "minecraft:nether/obtain_blaze_rod", List.of(new Objective("kill:BLAZE", 25, "Kill 25 Blazes"), new Objective("collect:BLAZE_ROD", 32, "Collect 32 Blaze Rods"))),
        new Pack("magma_march", "Magma March", Material.MAGMA_BLOCK, 260, 364, "minecraft:nether/root", List.of(new Objective("break:MAGMA_BLOCK", 96, "Mine 96 Magma Blocks"))),
        new Pack("hoglin_hunt", "Hoglin Hunt", Material.PORKCHOP, 300, 420, "minecraft:nether/root", List.of(new Objective("kill:HOGLIN", 20, "Hunt 20 Hoglins"))),
        new Pack("end_expedition", "End Expedition", Material.END_STONE, 900, 1350, "minecraft:story/enter_the_end", List.of(new Objective("kill:ENDERMAN", 30, "Kill 30 Endermen"), new Objective("break:END_STONE", 128, "Mine 128 End Stone"), new Objective("collect:ENDER_PEARL", 32, "Collect 32 Ender Pearls"))),
        new Pack("dragon_slayer", "Dragon Slayer", Material.DRAGON_HEAD, 1800, 2700, "minecraft:end/kill_dragon", List.of(new Objective("kill:ENDER_DRAGON", 1, "Defeat the Ender Dragon"))),
        new Pack("pearl_trader", "Pearl Trader", Material.ENDER_PEARL, 320, 448, "minecraft:story/enter_the_end", List.of(new Objective("collect:ENDER_PEARL", 48, "Collect 48 Ender Pearls"))),
        new Pack("ocean_fisher", "Ocean Fishing", Material.FISHING_ROD, 180, 252, "minecraft:husbandry/tactical_fishing", List.of(new Objective("fish:COD", 16, "Catch 16 Cod while fishing"))),
        new Pack("salmon_run", "Salmon Run", Material.SALMON, 200, 280, "minecraft:husbandry/fishy_business", List.of(new Objective("fish:SALMON", 16, "Catch 16 Salmon while fishing"))),
        new Pack("deep_sea", "Deep Sea Haul", Material.TROPICAL_FISH, 420, 588, "minecraft:husbandry/fishy_business", List.of(new Objective("fish:ANY", 60, "Catch 60 fish while fishing"))),
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
            List<Pack> candidates = basePacks.stream().filter(pack -> eligibleTemplate(pack, tier)).toList();
            if (candidates.isEmpty()) candidates = basePacks;
            for (int i = 0; i < 100; i++) {
                String id = tiers[tier] + "_" + String.format(Locale.ROOT, "%03d", i + 1);
                Pack template;
                boolean featured = i == 0;
                if (featured && tier == 0) {
                    template = new Pack("iron_supply", "Iron Supply", Material.RAW_IRON, 200, 500,
                            "minecraft:story/iron_tools",
                            List.of(new Objective("break:IRON_ORE", 48, "Mine 48 Iron Ore")));
                } else if (featured && tier == 1) {
                    template = new Pack("fortress_breaker", "Fortress Breaker", Material.BLAZE_ROD, 700, 2000,
                            "minecraft:nether/obtain_blaze_rod",
                            List.of(new Objective("killlevel:BLAZE:10", 8, "Defeat 8 Blazes at level 10+"),
                                    new Objective("collect:BLAZE_ROD", 8, "Collect 8 Blaze Rods")));
                } else if (featured) {
                    template = new Pack("elite_monster_hunt", "Elite Monster Hunt", Material.NETHER_STAR, 1500, 5000,
                            "minecraft:end/kill_dragon",
                            List.of(new Objective("killlevel:ANY:20", 5, "Defeat 5 hostile monsters at level 20+")));
                } else {
                    template = candidates.get(Math.floorMod(i * 7 + tier * 11, candidates.size()));
                }

                List<Objective> objectives = new ArrayList<>();
                int cost = template.cost();
                int reward = template.reward();
                if (!featured) {
                    int targetMinutes = tier == 0 ? 8 + (i % 18) : tier == 1 ? 20 + (i % 16) : 30 + (i % 31);
                    double baseMinutes = Math.max(1.0, estimateMinutes(template.objectives()));
                    double scale = Math.max(0.10, Math.min(40.0, targetMinutes / baseMinutes));
                    for (Objective objective : template.objectives()) {
                        int goal = isBossObjective(objective.target()) ? 1
                                : Math.max(1, (int) Math.ceil(objective.goal() * scale));
                        objectives.add(new Objective(objective.target(), goal,
                                objective.description().replaceFirst("\\d+", Integer.toString(goal))));
                    }
                    double minutes = Math.max(targetMinutes * 0.75, estimateMinutes(objectives));
                    double feePerMinute = tier == 0 ? 20.0 : tier == 1 ? 25.0 : 38.0;
                    double profitPerMinute = tier == 0 ? 32.0 : tier == 1 ? 46.0 : 88.0;
                    cost = Math.max(40, (int) Math.round(minutes * feePerMinute));
                    reward = cost + (int) Math.round(minutes * profitPerMinute);
                } else {
                    objectives.addAll(template.objectives());
                }
                String advancement = template.advancement();
                String label = tiers[tier].substring(0, 1).toUpperCase(Locale.ROOT) + tiers[tier].substring(1);
                result.add(new Pack(id, label + " Pack " + String.format(Locale.ROOT, "%03d", i + 1)
                        + " - " + template.name(), template.icon(), cost, reward, advancement, List.copyOf(objectives)));
            }
        }
        return List.copyOf(result);
    }

    private boolean eligibleTemplate(Pack pack, int tier) {
        if (tier == 0) return !pack.objectives().stream().anyMatch(o -> isBossObjective(o.target()));
        for (Objective objective : pack.objectives()) {
            String target = objective.target();
            if (target.equals("break:STONE") || target.equals("break:SAND") || target.equals("break:BASALT")
                    || target.equals("break:SOUL_SAND") || (target.startsWith("break:") && target.endsWith("_LOG"))
                    || (target.startsWith("collect:") && target.endsWith("_PLANKS"))) return false;
        }
        double minutes = estimateMinutes(pack.objectives());
        return tier == 1 ? minutes >= 2.0 : minutes >= 3.0;
    }

    private boolean isBossObjective(String target) {
        return target.equals("kill:ENDER_DRAGON") || target.equals("kill:WITHER");
    }

    private double estimateMinutes(List<Objective> objectives) {
        double minutes = 0.0;
        for (Objective objective : objectives) minutes += objective.goal() / Math.max(0.01, ratePerMinute(objective.target()));
        return Math.max(1.0, minutes);
    }

    private double ratePerMinute(String target) {
        String[] parts = target.split(":");
        String type = parts[0];
        String item = parts.length > 1 ? parts[1] : "ANY";
        if (type.equals("killlevel")) type = "kill";
        double configured = plugin.getConfig().getDouble("quests.rates." + type + "." + item, -1.0);
        if (configured > 0.0) return configured;
        if (type.equals("break")) {
            if (item.equals("DIAMOND_ORE")) return 0.5;
            if (item.equals("ANCIENT_DEBRIS")) return 0.35;
            if (item.equals("IRON_ORE") || item.equals("COPPER_ORE")) return 6.0;
            if (item.equals("GOLD_ORE")) return 3.0;
            if (item.equals("NETHER_QUARTZ_ORE")) return 12.0;
            if (List.of("STONE", "SAND", "BASALT", "SOUL_SAND", "DEEPSLATE", "GRANITE", "ANDESITE", "DIORITE").contains(item)) return 120.0;
            if (item.endsWith("_LOG") || item.endsWith("_STEM")) return 60.0;
            return 40.0;
        }
        if (type.equals("collect")) {
            if (item.equals("WITHER_SKELETON_SKULL")) return 0.02;
            if (item.equals("BLAZE_ROD")) return 3.0;
            if (item.equals("ENDER_PEARL")) return 6.0;
            if (item.endsWith("_INGOT")) return 8.0;
            return 20.0;
        }
        if (type.equals("kill")) {
            if (item.equals("ENDER_DRAGON")) return 1.0 / 30.0;
            if (item.equals("WITHER")) return 1.0 / 20.0;
            if (item.equals("BLAZE")) return 3.0;
            if (item.equals("WITHER_SKELETON")) return 2.0;
            if (item.equals("GHAST")) return 2.0;
            if (item.equals("ANY")) return 2.0;
            return 10.0;
        }
        if (type.equals("fish")) return 4.0;
        if (type.equals("harvest")) return 60.0;
        if (type.equals("travel")) return 1000.0;
        if (type.equals("breed")) return 6.0;
        if (type.equals("feed")) return 20.0;
        return 20.0;
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
        if (activeId.startsWith("single:")) {
            String questName = data.getString(playerId + ".name", "");
            return switch (questName.replaceFirst(" #\\d+$", "")) {
                case "Ancient Debris Search", "Raid the Fortress", "Fishing for Cod", "Travel Contract", "Wither Skeleton Hunt" -> 30L * 60L;
                default -> 0L;
            };
        }
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

    private void openGlobal(Player player) {
        resetEventIfNeeded();
        UUID id = player.getUniqueId();
        int progress = data.getInt("event.progress", 0);
        int goal = data.getInt("event.goal", configuredEventGoal());
        int contribution = data.getInt("event.contribution." + id, 0);
        long remaining = Math.max(0L, data.getLong("event.ends-at", System.currentTimeMillis()) - System.currentTimeMillis());
        boolean completed = data.getBoolean("event.completed", false);
        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.DARK_PURPLE + "XZH Global Quest");
        add(inv, 13, Material.NETHER_STAR, ChatColor.GOLD + data.getString("event.name", "Global Quest"), "global-info", "global",
            List.of(ChatColor.WHITE + data.getString("event.description", "Work together to complete the objective."),
                    ChatColor.WHITE + "Shared progress: " + progress + "/" + goal,
                    ChatColor.WHITE + "Your contribution: " + contribution,
                    ChatColor.WHITE + "Reward: " + data.getInt("event.reward", configuredEventReward()) + " coins each",
                    ChatColor.WHITE + "Time left: " + formatGlobalTime(remaining),
                    completed ? ChatColor.GREEN + "Completed! Rewards have been paid." : ChatColor.GRAY + "Contribute before the deadline to earn the reward."));
        add(inv, 11, Material.GOLD_INGOT, ChatColor.GREEN + "Contributor rewards", "global-info", "global",
            List.of(ChatColor.WHITE + "Rewards are paid automatically when",
                    ChatColor.WHITE + "the community completes the objective.",
                    ChatColor.GRAY + "Only players who contributed receive a reward."));
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
        int progress = Math.min(goal, data.getInt("event.progress", 0) + amount);
        data.set("event.progress", progress);
        String contributionPath = "event.contribution." + player.getUniqueId();
        data.set(contributionPath, data.getInt(contributionPath, 0) + amount);
        save();
        if (progress >= goal) completeGlobalQuest();
    }

    private void completeGlobalQuest() {
        if (data.getBoolean("event.completed", false)) return;
        data.set("event.completed", true);
        save();
        int reward = data.getInt("event.reward", configuredEventReward());
        org.bukkit.configuration.ConfigurationSection contributors = data.getConfigurationSection("event.contribution");
        int paid = 0;
        if (contributors != null) {
            for (String rawId : contributors.getKeys(false)) {
                try {
                    UUID id = UUID.fromString(rawId);
                    int contribution = data.getInt("event.contribution." + rawId, 0);
                    if (contribution <= 0) continue;
                    plugin.setBalance(id, plugin.getBalance(id) + reward);
                    Player online = Bukkit.getPlayer(id);
                    if (online != null) plugin.tellPublic(online, "§aGlobal quest reward received: " + reward + " coins for contributing to " + data.getString("event.name", "the global quest") + ".");
                    paid++;
                } catch (IllegalArgumentException ignored) { }
            }
        }
        Bukkit.broadcastMessage(ChatColor.GOLD + "[XZH Global Quest] " + ChatColor.GREEN
                + data.getString("event.name", "Global Quest") + " completed! " + paid
                + " contributor(s) received " + reward + " coins each.");
        save();
        // A successful early completion immediately opens the next four-hour community challenge.
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
        return "§6Global Quest: §f" + objective + " §8(" + percent + "%, " + formatGlobalTime(remaining) + ")";
    }

    private void broadcastGlobalProgress() {
        resetEventIfNeeded();
        if (data.getBoolean("event.completed", false)) return;
        String message = ChatColor.GOLD + "[XZH Global Quest] " + ChatColor.YELLOW
            + data.getString("event.name", "Global Quest") + ": " + data.getInt("event.progress", 0) + "/"
            + data.getInt("event.goal", configuredEventGoal()) + " — "
            + data.getString("event.description", "Help complete the community objective.")
            + " Time left: " + formatGlobalTime(Math.max(0L, data.getLong("event.ends-at", 0L) - System.currentTimeMillis())) + ".";
        Bukkit.broadcastMessage(message);
    }

    private void refreshSingleShopIfDue() {
        long now = System.currentTimeMillis();
        long next = data.getLong("single-shop.next-refresh", 0L);
        if (next <= 0 || now >= next) {
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
            for (int i = 0; i < choices.size(); i++) data.set("single-shop.offers." + i, choices.get(i));
            save();
            Bukkit.broadcastMessage(ChatColor.GOLD + "[XZH Quests] Five new random single-quest offers are available!");
        }
    }

    private void ensureSingleOfferDiversity() {
        Set<String> categories = new HashSet<>();
        boolean diverse = true;
        for (int i = 0; i < 5; i++) {
            int index = Math.floorMod(data.getInt("single-shop.offers." + i, i), randomPool.size());
            if (!categories.add(questCategory(randomPool.get(index)))) diverse = false;
        }
        if (diverse) return;
        List<Integer> candidates = new ArrayList<>();
        for (int i = 0; i < randomPool.size(); i++) candidates.add(i);
        Collections.shuffle(candidates, random);
        List<Integer> choices = new ArrayList<>();
        Set<String> used = new HashSet<>();
        for (int candidate : candidates) {
            if (used.add(questCategory(randomPool.get(candidate)))) choices.add(candidate);
            if (choices.size() == 5) break;
        }
        for (int i = 0; i < choices.size(); i++) data.set("single-shop.offers." + i, choices.get(i));
        save();
    }

    private int nextOfferForSlot(int slot) {
        Set<String> usedCategories = new HashSet<>();
        for (int i = 0; i < 5; i++) {
            if (i == slot) continue;
            int index = Math.floorMod(data.getInt("single-shop.offers." + i, i), randomPool.size());
            usedCategories.add(questCategory(randomPool.get(index)));
        }
        List<Integer> eligible = new ArrayList<>();
        for (int i = 0; i < randomPool.size(); i++) {
            if (!usedCategories.contains(questCategory(randomPool.get(i)))) eligible.add(i);
        }
        return eligible.isEmpty() ? random.nextInt(randomPool.size()) : eligible.get(random.nextInt(eligible.size()));
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
        data.set("single-shop.secret.cost", 150 + random.nextInt(351));
        data.set("single-shop.secret.margin", 30 + random.nextInt(51));
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
            Bukkit.broadcastMessage(ChatColor.GOLD + "[XZH Global Quest] " + ChatColor.YELLOW
                    + "New 4-hour community quest: " + selected.name() + " — " + selected.description()
                    + ". Reward: " + reward + " coins for each contributor. Work together before the deadline!");
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = ChatColor.stripColor(event.getView().getTitle());
        if (title == null || !(title.equals("XZH Quests") || title.equals("XZH Single Quests")
                || title.startsWith("XZH Achievement Packs") || title.equals("XZH Pack Tiers") || title.startsWith("XZH Easy Packs") || title.startsWith("XZH Hard Packs") || title.startsWith("XZH Master Packs") || title.equals("Confirm quest pack")
                || title.equals("XZH Global Quest"))) return;
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
            case "section-single" -> openSingle(player);
            case "section-packs" -> openPacks(player);
            case "section-global" -> openGlobal(player);
            case "single-buy" -> { try { buyRandom(player, Integer.parseInt(value)); } catch (NumberFormatException ignored) { } }
            case "single-secret-buy" -> buyRandom(player, 5);
            case "single-info" -> plugin.tellPublic(player, "Five random quests are available; each purchase replaces its offer slot.");
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

    private void save() {
        try { data.save(file); } catch (IOException e) { plugin.getLogger().severe("Could not save quests.yml: " + e.getMessage()); }
    }
}
