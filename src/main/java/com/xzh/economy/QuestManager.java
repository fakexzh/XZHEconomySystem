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
    private final Random random = new Random();
    private final Map<UUID, Double> travelRemainders = new HashMap<>();

    // Curated achievement-pack content. Targets use the existing QuestManager trackers;
    // event listeners, progress counting, persistence, and reward flow remain unchanged.
    private final List<Pack> packs = List.of(
        new Pack("easy_001", "Stonecutter's Warm-Up", Material.STONE, 50, 210, "minecraft:story/mine_stone", List.of(new Objective("break:STONE", 1200, "Break 1,200 Stone blocks."))),
        new Pack("easy_002", "Coal Seam Crew", Material.COAL_ORE, 50, 210, "minecraft:story/upgrade_tools", List.of(new Objective("break:COAL_ORE", 96, "Mine 96 Coal Ore blocks (deepslate variants also count)."))),
        new Pack("easy_003", "Copper Crawl", Material.COPPER_ORE, 90, 260, "minecraft:story/upgrade_tools", List.of(new Objective("break:COPPER_ORE", 96, "Mine 96 Copper Ore blocks (deepslate variants also count)."))),
        new Pack("easy_004", "Iron Vein Run", Material.IRON_ORE, 130, 340, "minecraft:story/smelt_iron", List.of(new Objective("break:IRON_ORE", 64, "Mine 64 Iron Ore blocks (deepslate variants also count)."))),
        new Pack("easy_005", "Gravel Pit Shift", Material.GRAVEL, 50, 210, "minecraft:story/mine_stone", List.of(new Objective("break:GRAVEL", 640, "Break 640 Gravel blocks."))),
        new Pack("easy_006", "Dune Digger", Material.SAND, 50, 210, "minecraft:story/mine_stone", List.of(new Objective("break:SAND", 896, "Break 896 Sand blocks."))),
        new Pack("easy_007", "Clay Banks", Material.CLAY, 130, 330, "minecraft:story/upgrade_tools", List.of(new Objective("break:CLAY", 160, "Break 160 Clay blocks."))),
        new Pack("easy_008", "Granite Gang", Material.GRANITE, 50, 210, "minecraft:story/upgrade_tools", List.of(new Objective("break:GRANITE", 640, "Break 640 Granite blocks."))),
        new Pack("easy_009", "Redstone Rush", Material.REDSTONE_ORE, 130, 340, "minecraft:story/smelt_iron", List.of(new Objective("break:REDSTONE_ORE", 80, "Mine 80 Redstone Ore blocks (deepslate variants also count)."))),
        new Pack("easy_010", "Lapis Lodestar", Material.LAPIS_ORE, 170, 380, "minecraft:story/iron_tools", List.of(new Objective("break:LAPIS_ORE", 48, "Mine 48 Lapis Ore blocks (deepslate variants also count)."))),
        new Pack("easy_011", "Lumberjack Basics", Material.OAK_LOG, 50, 210, "minecraft:story/mine_stone", List.of(new Objective("break:OAK_LOG", 192, "Chop 192 Oak Logs."))),
        new Pack("easy_012", "Birch Brigade", Material.BIRCH_LOG, 90, 260, "minecraft:story/upgrade_tools", List.of(new Objective("break:BIRCH_LOG", 128, "Chop 128 Birch Logs."))),
        new Pack("easy_013", "Spruce Supply", Material.SPRUCE_LOG, 90, 260, "minecraft:story/upgrade_tools", List.of(new Objective("break:SPRUCE_LOG", 128, "Chop 128 Spruce Logs."))),
        new Pack("easy_014", "Dark Forest Cutter", Material.DARK_OAK_LOG, 130, 320, "minecraft:story/iron_tools", List.of(new Objective("break:DARK_OAK_LOG", 96, "Chop 96 Dark Oak Logs."))),
        new Pack("easy_015", "Jungle Chopper", Material.JUNGLE_LOG, 130, 330, "minecraft:story/iron_tools", List.of(new Objective("break:JUNGLE_LOG", 96, "Chop 96 Jungle Logs."))),
        new Pack("easy_016", "Savanna Sawyer", Material.ACACIA_LOG, 130, 320, "minecraft:story/iron_tools", List.of(new Objective("break:ACACIA_LOG", 96, "Chop 96 Acacia Logs."))),
        new Pack("easy_017", "Cherry Grove Grind", Material.CHERRY_LOG, 170, 370, "minecraft:husbandry/plant_seed", List.of(new Objective("break:CHERRY_LOG", 64, "Chop 64 Cherry Logs."))),
        new Pack("easy_018", "Mangrove Marsh Crew", Material.MANGROVE_LOG, 170, 370, "minecraft:story/iron_tools", List.of(new Objective("break:MANGROVE_LOG", 64, "Chop 64 Mangrove Logs."))),
        new Pack("easy_019", "Bamboo Thicket", Material.BAMBOO, 90, 260, "minecraft:husbandry/plant_seed", List.of(new Objective("break:BAMBOO", 384, "Chop 384 Bamboo."))),
        new Pack("easy_020", "Pale Garden Harvest", Material.PALE_OAK_LOG, 210, 440, "minecraft:story/iron_tools", List.of(new Objective("break:PALE_OAK_LOG", 80, "Chop 80 Pale Oak Logs."))),
        new Pack("easy_021", "Wheat Weekend", Material.WHEAT, 50, 220, "minecraft:husbandry/plant_seed", List.of(new Objective("harvest:WHEAT", 192, "Harvest 192 Wheat from fully grown crops."))),
        new Pack("easy_022", "Carrot Cartel", Material.CARROT, 50, 220, "minecraft:husbandry/plant_seed", List.of(new Objective("harvest:CARROT", 160, "Harvest 160 Carrots from fully grown crops."))),
        new Pack("easy_023", "Potato Patch", Material.POTATO, 50, 220, "minecraft:husbandry/plant_seed", List.of(new Objective("harvest:POTATO", 160, "Harvest 160 Potatos from fully grown crops."))),
        new Pack("easy_024", "Beetroot Brigade", Material.BEETROOT, 90, 270, "minecraft:husbandry/plant_seed", List.of(new Objective("harvest:BEETROOT", 128, "Harvest 128 Beetroots from fully grown crops."))),
        new Pack("easy_025", "Melon Madness", Material.MELON_SLICE, 90, 270, "minecraft:husbandry/plant_seed", List.of(new Objective("harvest:MELON_SLICE", 128, "Harvest 128 Melons."))),
        new Pack("easy_026", "Pumpkin Parade", Material.PUMPKIN, 90, 260, "minecraft:husbandry/plant_seed", List.of(new Objective("harvest:PUMPKIN", 64, "Harvest 64 Pumpkins."))),
        new Pack("easy_027", "Sweet Berry Picker", Material.SWEET_BERRIES, 130, 320, "minecraft:husbandry/plant_seed", List.of(new Objective("collect:SWEET_BERRIES", 128, "Collect 128 Sweet Berries."))),
        new Pack("easy_028", "Cocoa Grove", Material.COCOA_BEANS, 130, 320, "minecraft:husbandry/plant_seed", List.of(new Objective("harvest:COCOA_BEANS", 96, "Harvest 96 Cocoa Beanss from fully grown crops."))),
        new Pack("easy_029", "Sugar Cane Run", Material.SUGAR_CANE, 90, 260, "minecraft:husbandry/plant_seed", List.of(new Objective("break:SUGAR_CANE", 384, "Break 384 Sugar Cane."))),
        new Pack("easy_030", "Kelp Farm Shift", Material.KELP, 90, 260, "minecraft:husbandry/plant_seed", List.of(new Objective("break:KELP", 320, "Break 320 Kelp."))),
        new Pack("easy_031", "Cow Whisperer", Material.WHEAT, 50, 210, "minecraft:husbandry/plant_seed", List.of(new Objective("feed:COW", 24, "Feed 24 adult cows using wheat."))),
        new Pack("easy_032", "Shepherd's Feeding Shift", Material.WHEAT, 90, 270, "minecraft:husbandry/plant_seed", List.of(new Objective("feed:SHEEP", 12, "Feed 12 adult sheep using wheat."))),
        new Pack("easy_033", "Piggy Bank", Material.WHEAT, 90, 270, "minecraft:husbandry/plant_seed", List.of(new Objective("feed:PIG", 12, "Feed 12 adult pigs using carrots, potatoes, or beetroot."))),
        new Pack("easy_034", "Coop Keeper's Feeding Shift", Material.WHEAT, 90, 270, "minecraft:husbandry/plant_seed", List.of(new Objective("feed:CHICKEN", 16, "Feed 16 adult chickens using seeds."))),
        new Pack("easy_035", "Herd Care Circuit", Material.WHEAT, 90, 330, "minecraft:husbandry/plant_seed", List.of(new Objective("feed:COW", 5, "Feed 5 adult cows using wheat."), new Objective("feed:SHEEP", 5, "Feed 5 adult sheep using wheat."), new Objective("feed:GOAT", 5, "Feed 5 adult goats using wheat."), new Objective("feed:PIG", 5, "Feed 5 adult pigs using carrots, potatoes, or beetroot."), new Objective("feed:CHICKEN", 5, "Feed 5 adult chickens using seeds."), new Objective("feed:RABBIT", 5, "Feed 5 adult rabbits using carrots, golden carrots, or dandelions."))),
        new Pack("easy_036", "Rabbit Feeding Run", Material.WHEAT, 130, 330, "minecraft:husbandry/plant_seed", List.of(new Objective("feed:RABBIT", 10, "Feed 10 adult rabbits using carrots, golden carrots, or dandelions."))),
        new Pack("easy_037", "Cat Food Supply", Material.COD, 130, 310, "minecraft:husbandry/tame_an_animal", List.of(new Objective("collect:COD", 32, "Collect 32 Cods."))),
        new Pack("easy_038", "Wolf Pack Provisioner", Material.BONE, 130, 330, "minecraft:husbandry/tame_an_animal", List.of(new Objective("collect:BONE", 48, "Collect 48 Bones."))),
        new Pack("easy_039", "Parrot Seed Supply", Material.WHEAT_SEEDS, 170, 390, "minecraft:husbandry/tame_an_animal", List.of(new Objective("collect:WHEAT_SEEDS", 32, "Collect 32 Wheat Seeds."))),
        new Pack("easy_040", "Coastal Herd Care", Material.WHEAT, 210, 440, "minecraft:husbandry/plant_seed", List.of(new Objective("feed:GOAT", 3, "Feed 3 adult goats using wheat."))),
        new Pack("easy_041", "First Catch Club", Material.FISHING_ROD, 50, 230, "minecraft:husbandry/fishy_business", List.of(new Objective("fish:COD", 24, "Catch 24 Cods while fishing."))),
        new Pack("easy_042", "Cod Quota", Material.FISHING_ROD, 50, 230, "minecraft:husbandry/fishy_business", List.of(new Objective("fish:COD", 30, "Catch 30 Cods while fishing."))),
        new Pack("easy_043", "Salmon Run", Material.FISHING_ROD, 90, 280, "minecraft:husbandry/fishy_business", List.of(new Objective("fish:SALMON", 16, "Catch 16 Salmons while fishing."))),
        new Pack("easy_044", "Pufferfish Pals", Material.FISHING_ROD, 130, 350, "minecraft:husbandry/fishy_business", List.of(new Objective("fish:PUFFERFISH", 8, "Catch 8 Pufferfishs while fishing."))),
        new Pack("easy_045", "Tropical Treasure", Material.FISHING_ROD, 210, 470, "minecraft:husbandry/tactical_fishing", List.of(new Objective("fish:TROPICAL_FISH", 2, "Catch 2 Tropical Fishs while fishing."))),
        new Pack("easy_046", "Junk Collector", Material.FISHING_ROD, 90, 300, "minecraft:husbandry/fishy_business", List.of(new Objective("fish:STRING", 8, "Catch 8 String while fishing."))),
        new Pack("easy_047", "Treasure Angler", Material.FISHING_ROD, 170, 390, "minecraft:husbandry/fishy_business", List.of(new Objective("fish:ENCHANTED_BOOK", 2, "Catch 2 Enchanted Books while fishing."))),
        new Pack("easy_048", "Big Haul", Material.FISHING_ROD, 90, 330, "minecraft:husbandry/fishy_business", List.of(new Objective("fish:COD", 80, "Catch 80 Cods while fishing."))),
        new Pack("easy_049", "Fisherman's Mix", Material.FISHING_ROD, 130, 350, "minecraft:husbandry/fishy_business", List.of(new Objective("fish:COD", 16, "Catch 16 Cods while fishing."), new Objective("fish:SALMON", 8, "Catch 8 Salmons while fishing."))),
        new Pack("easy_050", "Deep Pond Marathon", Material.FISHING_ROD, 90, 350, "minecraft:husbandry/fishy_business", List.of(new Objective("fish:COD", 120, "Catch 120 Cods while fishing."))),
        new Pack("easy_051", "Zombie Sweep", Material.IRON_SWORD, 50, 240, "minecraft:adventure/kill_a_mob", List.of(new Objective("kill:ZOMBIE", 30, "Defeat 30 Zombies."))),
        new Pack("easy_052", "Skeleton Shooting Gallery", Material.IRON_SWORD, 90, 290, "minecraft:adventure/kill_a_mob", List.of(new Objective("kill:SKELETON", 25, "Defeat 25 Skeletons."))),
        new Pack("easy_053", "Creeper Cleanup", Material.IRON_SWORD, 90, 290, "minecraft:adventure/kill_a_mob", List.of(new Objective("kill:CREEPER", 15, "Defeat 15 Creepers."))),
        new Pack("easy_054", "Spider Season", Material.IRON_SWORD, 50, 240, "minecraft:adventure/kill_a_mob", List.of(new Objective("kill:SPIDER", 25, "Defeat 25 Spiders."))),
        new Pack("easy_055", "Night Shift", Material.IRON_SWORD, 90, 310, "minecraft:adventure/kill_a_mob", List.of(new Objective("kill:ZOMBIE", 60, "Defeat 60 Zombies."))),
        new Pack("easy_056", "Enderman Encounter", Material.IRON_SWORD, 170, 390, "minecraft:story/iron_tools", List.of(new Objective("kill:ENDERMAN", 6, "Defeat 6 Endermen."))),
        new Pack("easy_057", "Witch Hunt", Material.IRON_SWORD, 170, 410, "minecraft:adventure/kill_a_mob", List.of(new Objective("kill:WITCH", 5, "Defeat 5 Witches."))),
        new Pack("easy_058", "Slime Squisher", Material.IRON_SWORD, 130, 340, "minecraft:adventure/kill_a_mob", List.of(new Objective("kill:SLIME", 12, "Defeat 12 Slimes."))),
        new Pack("easy_059", "Drowned Dive", Material.IRON_SWORD, 90, 290, "minecraft:adventure/kill_a_mob", List.of(new Objective("kill:DROWNED", 15, "Defeat 15 Drowned."))),
        new Pack("easy_060", "Desert & Snow Patrol", Material.IRON_SWORD, 170, 400, "minecraft:story/iron_tools", List.of(new Objective("kill:HUSK", 10, "Defeat 10 Husks."), new Objective("kill:STRAY", 10, "Defeat 10 Strays."))),
        new Pack("easy_061", "Furnace Apprentice", Material.IRON_INGOT, 90, 270, "minecraft:story/smelt_iron", List.of(new Objective("collect:IRON_INGOT", 96, "Collect 96 Iron Ingots."))),
        new Pack("easy_062", "Copper Smelter", Material.COPPER_INGOT, 90, 270, "minecraft:story/smelt_iron", List.of(new Objective("collect:COPPER_INGOT", 128, "Collect 128 Copper Ingots."))),
        new Pack("easy_063", "Glassblower", Material.GLASS, 50, 220, "minecraft:story/smelt_iron", List.of(new Objective("collect:GLASS", 256, "Collect 256 Glasss."))),
        new Pack("easy_064", "Brickyard", Material.BRICK, 130, 320, "minecraft:story/smelt_iron", List.of(new Objective("collect:BRICK", 192, "Collect 192 Bricks."))),
        new Pack("easy_065", "Stone Mason", Material.STONE, 50, 210, "minecraft:story/mine_stone", List.of(new Objective("collect:STONE", 256, "Collect 256 Stone."))),
        new Pack("easy_066", "Charcoal Burner", Material.CHARCOAL, 50, 210, "minecraft:story/mine_stone", List.of(new Objective("collect:CHARCOAL", 128, "Collect 128 Charcoals."))),
        new Pack("easy_067", "Cookout", Material.COOKED_BEEF, 90, 270, "minecraft:story/smelt_iron", List.of(new Objective("collect:COOKED_BEEF", 96, "Collect 96 Cooked Beef."))),
        new Pack("easy_068", "Seafood Platter", Material.COOKED_COD, 90, 270, "minecraft:husbandry/fishy_business", List.of(new Objective("collect:COOKED_COD", 80, "Collect 80 Cooked Cods."))),
        new Pack("easy_069", "Terracotta Kiln", Material.TERRACOTTA, 130, 330, "minecraft:story/smelt_iron", List.of(new Objective("collect:TERRACOTTA", 128, "Collect 128 Terracottas."))),
        new Pack("easy_070", "Baker's Dozen", Material.BREAD, 90, 270, "minecraft:husbandry/plant_seed", List.of(new Objective("collect:BREAD", 96, "Collect 96 Bread."))),
        new Pack("easy_071", "First Mile", Material.COMPASS, 50, 210, "minecraft:story/mine_stone", List.of(new Objective("travel", 3000, "Travel 3,000 blocks."))),
        new Pack("easy_072", "Long Walk", Material.COMPASS, 50, 230, "minecraft:story/upgrade_tools", List.of(new Objective("travel", 6000, "Travel 6,000 blocks."))),
        new Pack("easy_073", "Marathon Stroll", Material.COMPASS, 90, 320, "minecraft:story/iron_tools", List.of(new Objective("travel", 10000, "Travel 10,000 blocks."))),
        new Pack("easy_074", "Trailblazer's Route", Material.COMPASS, 90, 290, "minecraft:story/upgrade_tools", List.of(new Objective("travel", 6000, "Travel 6,000 blocks."))),
        new Pack("easy_075", "Peaks & Valleys Trek", Material.COMPASS, 130, 370, "minecraft:story/iron_tools", List.of(new Objective("travel", 10000, "Travel 10,000 blocks."))),
        new Pack("easy_076", "Sprint Training", Material.COMPASS, 90, 300, "minecraft:story/iron_tools", List.of(new Objective("travel", 8000, "Travel 8,000 blocks."))),
        new Pack("easy_077", "Cavern Trek", Material.COMPASS, 170, 390, "minecraft:story/iron_tools", List.of(new Objective("travel", 2000, "Travel 2,000 blocks."))),
        new Pack("easy_078", "Frozen Frontier Trek", Material.COMPASS, 130, 340, "minecraft:story/iron_tools", List.of(new Objective("travel", 3000, "Travel 3,000 blocks."))),
        new Pack("easy_079", "Desert Trail", Material.COMPASS, 130, 340, "minecraft:story/iron_tools", List.of(new Objective("travel", 2000, "Travel 2,000 blocks."))),
        new Pack("easy_080", "Jungle Trail", Material.COMPASS, 130, 340, "minecraft:story/iron_tools", List.of(new Objective("travel", 2000, "Travel 2,000 blocks."))),
        new Pack("easy_081", "Village Bread Supply", Material.BREAD, 90, 290, "minecraft:adventure/trade", List.of(new Objective("collect:BREAD", 16, "Collect 16 Bread."))),
        new Pack("easy_082", "Dungeon Gunpowder Supply", Material.GUNPOWDER, 130, 350, "minecraft:story/iron_tools", List.of(new Objective("collect:GUNPOWDER", 12, "Collect 12 Gunpowder."))),
        new Pack("easy_083", "Mineshaft Iron Stockpile", Material.IRON_INGOT, 130, 360, "minecraft:story/iron_tools", List.of(new Objective("collect:IRON_INGOT", 16, "Collect 16 Iron Ingots."))),
        new Pack("easy_084", "Shipwreck Gold Supply", Material.GOLD_INGOT, 170, 410, "minecraft:husbandry/fishy_business", List.of(new Objective("collect:GOLD_INGOT", 8, "Collect 8 Gold Ingots."))),
        new Pack("easy_085", "Buried Treasure Diamond Reserve", Material.DIAMOND, 210, 470, "minecraft:story/iron_tools", List.of(new Objective("collect:DIAMOND", 4, "Collect 4 Diamonds."))),
        new Pack("easy_086", "Portal Obsidian Reserve", Material.OBSIDIAN, 170, 400, "minecraft:story/form_obsidian", List.of(new Objective("collect:OBSIDIAN", 12, "Collect 12 Obsidian."))),
        new Pack("easy_087", "Desert Pyramid Gold Contract", Material.GOLD_INGOT, 170, 410, "minecraft:story/iron_tools", List.of(new Objective("collect:GOLD_INGOT", 8, "Collect 8 Gold Ingots."))),
        new Pack("easy_088", "Temple Emerald Reserve", Material.EMERALD, 170, 410, "minecraft:story/iron_tools", List.of(new Objective("collect:EMERALD", 8, "Collect 8 Emeralds."))),
        new Pack("easy_089", "Emerald Supply Run", Material.EMERALD, 130, 370, "minecraft:story/iron_tools", List.of(new Objective("collect:EMERALD", 40, "Collect 40 Emeralds."))),
        new Pack("easy_090", "Underwater Prismarine Supply", Material.PRISMARINE_SHARD, 170, 410, "minecraft:husbandry/fishy_business", List.of(new Objective("collect:PRISMARINE_SHARD", 12, "Collect 12 Prismarine Shards."))),
        new Pack("easy_091", "Pie Maker", Material.PUMPKIN_PIE, 90, 280, "minecraft:husbandry/plant_seed", List.of(new Objective("collect:PUMPKIN_PIE", 32, "Collect 32 Pumpkin Pies."))),
        new Pack("easy_092", "Cake Day", Material.CAKE, 170, 390, "minecraft:husbandry/plant_seed", List.of(new Objective("collect:CAKE", 3, "Collect 3 Cakes."))),
        new Pack("easy_093", "Cookie Jar", Material.COOKIE, 130, 340, "minecraft:husbandry/plant_seed", List.of(new Objective("collect:COOKIE", 192, "Collect 192 Cookies."))),
        new Pack("easy_094", "Lantern Lighter", Material.LANTERN, 130, 330, "minecraft:story/smelt_iron", List.of(new Objective("collect:LANTERN", 16, "Collect 16 Lanterns."))),
        new Pack("easy_095", "Shield Wall", Material.SHIELD, 130, 330, "minecraft:story/iron_tools", List.of(new Objective("collect:SHIELD", 12, "Collect 12 Shields."))),
        new Pack("easy_096", "Bucket Brigade", Material.BUCKET, 170, 380, "minecraft:story/iron_tools", List.of(new Objective("collect:BUCKET", 10, "Collect 10 Buckets."))),
        new Pack("easy_097", "Library Builder", Material.BOOKSHELF, 130, 330, "minecraft:story/iron_tools", List.of(new Objective("collect:BOOKSHELF", 10, "Collect 10 Bookshelfs."))),
        new Pack("easy_098", "Enchanted Book Apprentice", Material.ENCHANTED_BOOK, 170, 380, "minecraft:story/enchant_item", List.of(new Objective("collect:ENCHANTED_BOOK", 6, "Collect 6 Enchanted Books."))),
        new Pack("easy_099", "Armorer's Order", Material.IRON_HELMET, 170, 390, "minecraft:story/obtain_armor", List.of(new Objective("collect:IRON_HELMET", 1, "Collect 1 Iron Helmet."), new Objective("collect:IRON_CHESTPLATE", 1, "Collect 1 Iron Chestplate."), new Objective("collect:IRON_LEGGINGS", 1, "Collect 1 Iron Leggings."), new Objective("collect:IRON_BOOTS", 1, "Collect 1 Iron Boots."))),
        new Pack("easy_100", "Jack of All Trades", Material.IRON_ORE, 250, 550, "minecraft:story/iron_tools", List.of(new Objective("break:IRON_ORE", 32, "Mine 32 Iron Ore blocks (deepslate variants also count)."), new Objective("kill:ZOMBIE", 15, "Defeat 15 Zombies."), new Objective("fish:COD", 15, "Catch 15 Cods while fishing."), new Objective("harvest:WHEAT", 64, "Harvest 64 Wheat from fully grown crops."))),
        new Pack("hard_001", "Diamond Prospector", Material.DIAMOND_ORE, 590, 1120, "minecraft:story/mine_diamond", List.of(new Objective("break:DIAMOND_ORE", 24, "Mine 24 Diamond Ore blocks (deepslate variants also count)."))),
        new Pack("hard_002", "Emerald Mountain Miner", Material.EMERALD_ORE, 640, 1220, "minecraft:story/mine_diamond", List.of(new Objective("break:EMERALD_ORE", 12, "Mine 12 Emerald Ore blocks (deepslate variants also count)."))),
        new Pack("hard_003", "Gold Vein Gambit", Material.GOLD_ORE, 530, 1030, "minecraft:story/smelt_iron", List.of(new Objective("break:GOLD_ORE", 48, "Mine 48 Gold Ore blocks (deepslate variants also count)."))),
        new Pack("hard_004", "Deepslate Delver", Material.DEEPSLATE, 360, 720, "minecraft:story/mine_diamond", List.of(new Objective("break:DEEPSLATE", 2000, "Break 2,000 Deepslate blocks."))),
        new Pack("hard_005", "Lapis Lazuli Lodes", Material.LAPIS_ORE, 470, 970, "minecraft:story/enchant_item", List.of(new Objective("break:LAPIS_ORE", 128, "Mine 128 Lapis Ore blocks (deepslate variants also count)."))),
        new Pack("hard_006", "Redstone Royalty", Material.REDSTONE_ORE, 470, 940, "minecraft:story/mine_diamond", List.of(new Objective("break:REDSTONE_ORE", 192, "Mine 192 Redstone Ore blocks (deepslate variants also count)."))),
        new Pack("hard_007", "Copper Cavern Crew", Material.COPPER_ORE, 410, 850, "minecraft:story/iron_tools", List.of(new Objective("break:COPPER_ORE", 256, "Mine 256 Copper Ore blocks (deepslate variants also count)."))),
        new Pack("hard_008", "Geode Raider", Material.AMETHYST_CLUSTER, 590, 1120, "minecraft:story/mine_diamond", List.of(new Objective("break:AMETHYST_CLUSTER", 24, "Break 24 Amethyst Clusters blocks."))),
        new Pack("hard_009", "Mixed Ore Blitz", Material.IRON_ORE, 470, 960, "minecraft:story/mine_diamond", List.of(new Objective("break:IRON_ORE", 400, "Mine 400 Iron Ore blocks (deepslate variants also count)."))),
        new Pack("hard_010", "Below Zero Run", Material.DIAMOND_ORE, 590, 1140, "minecraft:story/mine_diamond", List.of(new Objective("break:DIAMOND_ORE", 16, "Mine 16 Diamond Ore blocks (deepslate variants also count)."), new Objective("break:REDSTONE_ORE", 64, "Mine 64 Redstone Ore blocks (deepslate variants also count)."))),
        new Pack("hard_011", "Quartz Quarry", Material.NETHER_QUARTZ_ORE, 410, 800, "minecraft:nether/root", List.of(new Objective("break:NETHER_QUARTZ_ORE", 128, "Mine 128 Nether Quartz Ore blocks (deepslate variants also count)."))),
        new Pack("hard_012", "Glowstone Gatherer", Material.GLOWSTONE, 410, 800, "minecraft:nether/root", List.of(new Objective("break:GLOWSTONE", 128, "Break 128 Glowstones blocks."))),
        new Pack("hard_013", "Soul Sand Scout", Material.SOUL_SAND, 360, 720, "minecraft:nether/root", List.of(new Objective("break:SOUL_SAND", 384, "Break 384 Soul Sand blocks."))),
        new Pack("hard_014", "Nether Gold Rush", Material.NETHER_GOLD_ORE, 410, 790, "minecraft:nether/root", List.of(new Objective("break:NETHER_GOLD_ORE", 160, "Mine 160 Nether Gold Ore blocks (deepslate variants also count)."))),
        new Pack("hard_015", "Basalt Delta Crew", Material.BASALT, 360, 720, "minecraft:nether/root", List.of(new Objective("break:BASALT", 1000, "Break 1,000 Basalt blocks."))),
        new Pack("hard_016", "Crimson Logger", Material.CRIMSON_STEM, 410, 830, "minecraft:nether/root", List.of(new Objective("break:CRIMSON_STEM", 160, "Chop 160 Crimson Stems."))),
        new Pack("hard_017", "Warped Logger", Material.WARPED_STEM, 410, 830, "minecraft:nether/root", List.of(new Objective("break:WARPED_STEM", 160, "Chop 160 Warped Stems."))),
        new Pack("hard_018", "Wart Farmer", Material.NETHER_WART, 470, 910, "minecraft:nether/root", List.of(new Objective("harvest:NETHER_WART", 192, "Harvest 192 Nether Wart from fully grown crops."))),
        new Pack("hard_019", "Ancient Debris Dig", Material.ANCIENT_DEBRIS, 700, 1370, "minecraft:nether/obtain_ancient_debris", List.of(new Objective("break:ANCIENT_DEBRIS", 8, "Mine 8 Ancient Debris blocks."))),
        new Pack("hard_020", "Crying Obsidian Harvest", Material.CRYING_OBSIDIAN, 590, 1120, "minecraft:nether/obtain_crying_obsidian", List.of(new Objective("break:CRYING_OBSIDIAN", 12, "Break 12 Crying Obsidians blocks."))),
        new Pack("hard_021", "Blaze Brigade", Material.DIAMOND_SWORD, 530, 1030, "minecraft:nether/obtain_blaze_rod", List.of(new Objective("kill:BLAZE", 30, "Defeat 30 Blazes."))),
        new Pack("hard_022", "Wither Skeleton Cull", Material.DIAMOND_SWORD, 530, 1060, "minecraft:nether/obtain_blaze_rod", List.of(new Objective("kill:WITHER_SKELETON", 20, "Defeat 20 Wither Skeletons."))),
        new Pack("hard_023", "Ghast Tear Hunter", Material.DIAMOND_SWORD, 530, 1030, "minecraft:nether/return_to_sender", List.of(new Objective("kill:GHAST", 8, "Defeat 8 Ghasts."))),
        new Pack("hard_024", "Piglin Patrol", Material.DIAMOND_SWORD, 410, 800, "minecraft:nether/find_bastion", List.of(new Objective("kill:PIGLIN", 30, "Defeat 30 Piglins."))),
        new Pack("hard_025", "Brute Force", Material.DIAMOND_SWORD, 640, 1220, "minecraft:nether/find_bastion", List.of(new Objective("kill:PIGLIN_BRUTE", 4, "Defeat 4 Piglin Brutes."))),
        new Pack("hard_026", "Hoglin Roundup", Material.DIAMOND_SWORD, 470, 920, "minecraft:nether/find_bastion", List.of(new Objective("kill:HOGLIN", 20, "Defeat 20 Hoglins."))),
        new Pack("hard_027", "Magma Cube Mash", Material.DIAMOND_SWORD, 410, 800, "minecraft:nether/obtain_blaze_rod", List.of(new Objective("kill:MAGMA_CUBE", 20, "Defeat 20 Magma Cubes."))),
        new Pack("hard_028", "Bastion Gold Shipment", Material.GOLD_INGOT, 590, 1140, "minecraft:nether/find_bastion", List.of(new Objective("collect:GOLD_INGOT", 24, "Collect 24 Gold Ingots."))),
        new Pack("hard_029", "Fortress Blaze Rod Reserve", Material.BLAZE_ROD, 530, 1030, "minecraft:nether/obtain_blaze_rod", List.of(new Objective("collect:BLAZE_ROD", 20, "Collect 20 Blaze Rods."))),
        new Pack("hard_030", "Zombified Piglin Purge", Material.DIAMOND_SWORD, 360, 750, "minecraft:nether/root", List.of(new Objective("kill:ZOMBIFIED_PIGLIN", 60, "Defeat 60 Zombified Piglins."))),
        new Pack("hard_031", "Zombie Annihilator", Material.DIAMOND_SWORD, 360, 800, "minecraft:adventure/kill_a_mob", List.of(new Objective("kill:ZOMBIE", 80, "Defeat 80 Zombies."))),
        new Pack("hard_032", "Skeleton Marksman", Material.DIAMOND_SWORD, 360, 800, "minecraft:adventure/shoot_arrow", List.of(new Objective("kill:SKELETON", 60, "Defeat 60 Skeletons."))),
        new Pack("hard_033", "Arachnophobia", Material.DIAMOND_SWORD, 360, 770, "minecraft:adventure/kill_a_mob", List.of(new Objective("kill:SPIDER", 70, "Defeat 70 Spiders."))),
        new Pack("hard_034", "Creeper Clearance", Material.DIAMOND_SWORD, 410, 880, "minecraft:adventure/shoot_arrow", List.of(new Objective("kill:CREEPER", 40, "Defeat 40 Creepers."))),
        new Pack("hard_035", "Phantom Menace", Material.DIAMOND_SWORD, 530, 1050, "minecraft:adventure/kill_a_mob", List.of(new Objective("kill:PHANTOM", 12, "Defeat 12 Phantoms."))),
        new Pack("hard_036", "Witch's Cauldron", Material.DIAMOND_SWORD, 530, 1050, "minecraft:story/shiny_gear", List.of(new Objective("kill:WITCH", 12, "Defeat 12 Witches."))),
        new Pack("hard_037", "Enderman Ambush", Material.DIAMOND_SWORD, 530, 1080, "minecraft:story/shiny_gear", List.of(new Objective("kill:ENDERMAN", 25, "Defeat 25 Endermen."))),
        new Pack("hard_038", "Silverfish Sweeper", Material.DIAMOND_SWORD, 470, 910, "minecraft:story/follow_ender_eye", List.of(new Objective("kill:SILVERFISH", 40, "Defeat 40 Silverfish."))),
        new Pack("hard_039", "Pillager Cleanup", Material.DIAMOND_SWORD, 590, 1170, "minecraft:adventure/voluntary_exile", List.of(new Objective("kill:PILLAGER", 30, "Defeat 30 Pillagers."))),
        new Pack("hard_040", "Ravager Wrangler", Material.DIAMOND_SWORD, 700, 1370, "minecraft:adventure/voluntary_exile", List.of(new Objective("kill:RAVAGER", 3, "Defeat 3 Ravagers."))),
        new Pack("hard_041", "Guardian Patrol", Material.DIAMOND_SWORD, 530, 1110, "minecraft:story/shiny_gear", List.of(new Objective("kill:GUARDIAN", 30, "Defeat 30 Guardians."))),
        new Pack("hard_042", "Prismarine Pillager", Material.PRISMARINE, 470, 940, "minecraft:story/mine_diamond", List.of(new Objective("break:PRISMARINE", 256, "Break 256 Prismarine blocks."))),
        new Pack("hard_043", "Sea Lantern Looter", Material.SEA_LANTERN, 590, 1140, "minecraft:story/mine_diamond", List.of(new Objective("break:SEA_LANTERN", 24, "Break 24 Sea Lanterns blocks."))),
        new Pack("hard_044", "Drowned Hunter", Material.DIAMOND_SWORD, 410, 850, "minecraft:husbandry/fishy_business", List.of(new Objective("kill:DROWNED", 50, "Defeat 50 Drowned."))),
        new Pack("hard_045", "Deep Sea Haul", Material.FISHING_ROD, 410, 950, "minecraft:husbandry/fishy_business", List.of(new Objective("fish:COD", 200, "Catch 200 Cods while fishing."))),
        new Pack("hard_046", "Pufferfish Plague", Material.FISHING_ROD, 470, 960, "minecraft:husbandry/fishy_business", List.of(new Objective("fish:PUFFERFISH", 20, "Catch 20 Pufferfishs while fishing."))),
        new Pack("hard_047", "Tropical Trove", Material.FISHING_ROD, 640, 1330, "minecraft:husbandry/tactical_fishing", List.of(new Objective("fish:TROPICAL_FISH", 5, "Catch 5 Tropical Fishs while fishing."))),
        new Pack("hard_048", "Treasure Tycoon", Material.FISHING_ROD, 590, 1140, "minecraft:husbandry/fishy_business", List.of(new Objective("fish:ENCHANTED_BOOK", 6, "Catch 6 Enchanted Books while fishing."))),
        new Pack("hard_049", "Ocean Distance Trail", Material.COMPASS, 410, 820, "minecraft:story/iron_tools", List.of(new Objective("travel", 3000, "Travel 3,000 blocks."))),
        new Pack("hard_050", "Shipwreck Supply Run", Material.GOLD_INGOT, 530, 1080, "minecraft:husbandry/fishy_business", List.of(new Objective("collect:GOLD_INGOT", 32, "Collect 32 Gold Ingots."))),
        new Pack("hard_051", "Wheat Baron", Material.WHEAT, 360, 770, "minecraft:husbandry/plant_seed", List.of(new Objective("harvest:WHEAT", 640, "Harvest 640 Wheat from fully grown crops."))),
        new Pack("hard_052", "Carrot Tycoon", Material.CARROT, 360, 770, "minecraft:husbandry/plant_seed", List.of(new Objective("harvest:CARROT", 512, "Harvest 512 Carrots from fully grown crops."))),
        new Pack("hard_053", "Potato Mogul", Material.POTATO, 360, 770, "minecraft:husbandry/plant_seed", List.of(new Objective("harvest:POTATO", 512, "Harvest 512 Potatos from fully grown crops."))),
        new Pack("hard_054", "Melon & Pumpkin Patch", Material.MELON_SLICE, 410, 850, "minecraft:husbandry/plant_seed", List.of(new Objective("harvest:MELON_SLICE", 192, "Harvest 192 Melons."), new Objective("harvest:PUMPKIN", 128, "Harvest 128 Pumpkins."))),
        new Pack("hard_055", "Beetroot Brigade II", Material.BEETROOT, 410, 820, "minecraft:husbandry/plant_seed", List.of(new Objective("harvest:BEETROOT", 400, "Harvest 400 Beetroots from fully grown crops."))),
        new Pack("hard_056", "Sweet Berry Syndicate", Material.SWEET_BERRIES, 410, 850, "minecraft:husbandry/plant_seed", List.of(new Objective("collect:SWEET_BERRIES", 384, "Collect 384 Sweet Berries."))),
        new Pack("hard_057", "Glow Berry Gatherer", Material.GLOW_BERRIES, 530, 1030, "minecraft:story/iron_tools", List.of(new Objective("collect:GLOW_BERRIES", 96, "Collect 96 Glow Berries."))),
        new Pack("hard_058", "Cocoa Cartel", Material.COCOA_BEANS, 470, 940, "minecraft:husbandry/plant_seed", List.of(new Objective("harvest:COCOA_BEANS", 256, "Harvest 256 Cocoa Beanss from fully grown crops."))),
        new Pack("hard_059", "Sugar Cane Syndicate", Material.SUGAR_CANE, 360, 740, "minecraft:husbandry/plant_seed", List.of(new Objective("break:SUGAR_CANE", 1200, "Break 1,200 Sugar Cane."))),
        new Pack("hard_060", "Stable Feeding Master", Material.WHEAT, 410, 950, "minecraft:husbandry/plant_seed", List.of(new Objective("feed:COW", 10, "Feed 10 adult cows using wheat."), new Objective("feed:SHEEP", 10, "Feed 10 adult sheep using wheat."), new Objective("feed:GOAT", 10, "Feed 10 adult goats using wheat."), new Objective("feed:PIG", 10, "Feed 10 adult pigs using carrots, potatoes, or beetroot."), new Objective("feed:CHICKEN", 10, "Feed 10 adult chickens using seeds."), new Objective("feed:RABBIT", 10, "Feed 10 adult rabbits using carrots, golden carrots, or dandelions."))),
        new Pack("hard_061", "Iron Foundry", Material.IRON_INGOT, 410, 900, "minecraft:story/iron_tools", List.of(new Objective("collect:IRON_INGOT", 384, "Collect 384 Iron Ingots."))),
        new Pack("hard_062", "Gold Foundry", Material.GOLD_INGOT, 530, 1050, "minecraft:story/mine_diamond", List.of(new Objective("collect:GOLD_INGOT", 192, "Collect 192 Gold Ingots."))),
        new Pack("hard_063", "Copper Furnaces", Material.COPPER_INGOT, 360, 750, "minecraft:story/iron_tools", List.of(new Objective("collect:COPPER_INGOT", 512, "Collect 512 Copper Ingots."))),
        new Pack("hard_064", "Scrap Smelter", Material.NETHERITE_SCRAP, 700, 1340, "minecraft:nether/obtain_ancient_debris", List.of(new Objective("collect:NETHERITE_SCRAP", 6, "Collect 6 Netherite Scraps."))),
        new Pack("hard_065", "Glasswork Empire", Material.GLASS, 360, 720, "minecraft:story/smelt_iron", List.of(new Objective("collect:GLASS", 1024, "Collect 1,024 Glasss."))),
        new Pack("hard_066", "Steakhouse", Material.COOKED_BEEF, 410, 790, "minecraft:husbandry/plant_seed", List.of(new Objective("collect:COOKED_BEEF", 256, "Collect 256 Cooked Beef."))),
        new Pack("hard_067", "Smokehouse", Material.COOKED_PORKCHOP, 410, 800, "minecraft:husbandry/plant_seed", List.of(new Objective("collect:COOKED_PORKCHOP", 320, "Collect 320 Cooked Porkchops."))),
        new Pack("hard_068", "Charcoal Baron", Material.CHARCOAL, 300, 650, "minecraft:story/upgrade_tools", List.of(new Objective("collect:CHARCOAL", 512, "Collect 512 Charcoals."))),
        new Pack("hard_069", "Kiln Master", Material.TERRACOTTA, 410, 880, "minecraft:story/iron_tools", List.of(new Objective("collect:TERRACOTTA", 384, "Collect 384 Terracottas."))),
        new Pack("hard_070", "Smelter's Gauntlet", Material.IRON_INGOT, 530, 1050, "minecraft:story/mine_diamond", List.of(new Objective("collect:IRON_INGOT", 128, "Collect 128 Iron Ingots."), new Objective("collect:GOLD_INGOT", 64, "Collect 64 Gold Ingots."), new Objective("collect:COPPER_INGOT", 128, "Collect 128 Copper Ingots."))),
        new Pack("hard_071", "Cross-Country Hike", Material.COMPASS, 410, 980, "minecraft:story/iron_tools", List.of(new Objective("travel", 18000, "Travel 18,000 blocks."))),
        new Pack("hard_072", "Wilderness Marathon", Material.COMPASS, 530, 1180, "minecraft:adventure/sleep_in_bed", List.of(new Objective("travel", 18000, "Travel 18,000 blocks."))),
        new Pack("hard_073", "Mountain Distance Trail", Material.COMPASS, 470, 990, "minecraft:story/iron_tools", List.of(new Objective("travel", 5000, "Travel 5,000 blocks."))),
        new Pack("hard_074", "Swamp & Mangrove Trek", Material.COMPASS, 410, 850, "minecraft:story/iron_tools", List.of(new Objective("travel", 6000, "Travel 6,000 blocks."))),
        new Pack("hard_075", "Long-Distance Pilgrim", Material.COMPASS, 640, 1220, "minecraft:story/iron_tools", List.of(new Objective("travel", 1000, "Travel 1,000 blocks."))),
        new Pack("hard_076", "Woodland Distance Trek", Material.COMPASS, 530, 1080, "minecraft:story/iron_tools", List.of(new Objective("travel", 2000, "Travel 2,000 blocks."))),
        new Pack("hard_077", "Savanna Roadtrip", Material.COMPASS, 410, 850, "minecraft:story/iron_tools", List.of(new Objective("travel", 3000, "Travel 3,000 blocks."))),
        new Pack("hard_078", "Long-Distance Expedition", Material.COMPASS, 530, 1080, "minecraft:nether/ride_strider", List.of(new Objective("travel", 5000, "Travel 5,000 blocks."))),
        new Pack("hard_079", "Sprint Challenge", Material.COMPASS, 410, 930, "minecraft:story/shiny_gear", List.of(new Objective("travel", 12000, "Travel 12,000 blocks."))),
        new Pack("hard_080", "Cave Country Trek", Material.COMPASS, 590, 1200, "minecraft:story/shiny_gear", List.of(new Objective("travel", 3000, "Travel 3,000 blocks."))),
        new Pack("hard_081", "Pyramid Gold Reserve", Material.GOLD_INGOT, 530, 1060, "minecraft:story/iron_tools", List.of(new Objective("collect:GOLD_INGOT", 16, "Collect 16 Gold Ingots."))),
        new Pack("hard_082", "Jungle Emerald Contract", Material.EMERALD, 530, 1060, "minecraft:story/iron_tools", List.of(new Objective("collect:EMERALD", 16, "Collect 16 Emeralds."))),
        new Pack("hard_083", "Stronghold Pearl Reserve", Material.ENDER_PEARL, 590, 1170, "minecraft:story/follow_ender_eye", List.of(new Objective("collect:ENDER_PEARL", 24, "Collect 24 Ender Pearls."))),
        new Pack("hard_084", "Outpost Crossbow Supply", Material.CROSSBOW, 530, 1030, "minecraft:adventure/voluntary_exile", List.of(new Objective("collect:CROSSBOW", 12, "Collect 12 Crossbows."))),
        new Pack("hard_085", "Dungeon Gunpowder Stockpile", Material.GUNPOWDER, 470, 1020, "minecraft:story/shiny_gear", List.of(new Objective("collect:GUNPOWDER", 40, "Collect 40 Gunpowder."))),
        new Pack("hard_086", "Mineshaft Iron Marathon", Material.IRON_INGOT, 470, 1050, "minecraft:story/shiny_gear", List.of(new Objective("collect:IRON_INGOT", 48, "Collect 48 Iron Ingots."))),
        new Pack("hard_087", "Mansion Totem Reserve", Material.TOTEM_OF_UNDYING, 700, 1420, "minecraft:adventure/voluntary_exile", List.of(new Objective("collect:TOTEM_OF_UNDYING", 16, "Collect 16 Totems of Undying."))),
        new Pack("hard_088", "Ruined Portal Obsidian Contract", Material.OBSIDIAN, 530, 1110, "minecraft:story/form_obsidian", List.of(new Objective("collect:OBSIDIAN", 32, "Collect 32 Obsidian."))),
        new Pack("hard_089", "Village Bread Shipment", Material.BREAD, 470, 990, "minecraft:adventure/trade", List.of(new Objective("collect:BREAD", 48, "Collect 48 Bread."))),
        new Pack("hard_090", "Buried Diamond Hoard", Material.DIAMOND, 640, 1300, "minecraft:husbandry/fishy_business", List.of(new Objective("collect:DIAMOND", 8, "Collect 8 Diamonds."))),
        new Pack("hard_091", "Elder Guardian Slayer", Material.DIAMOND_SWORD, 700, 1390, "minecraft:story/shiny_gear", List.of(new Objective("kill:ELDER_GUARDIAN", 1, "Defeat 1 Elder Guardian."))),
        new Pack("hard_092", "Ravager Hunter", Material.DIAMOND_SWORD, 640, 1300, "minecraft:adventure/kill_a_mob", List.of(new Objective("kill:RAVAGER", 1, "Defeat 1 Ravager."))),
        new Pack("hard_093", "Skull Hunter", Material.DIAMOND_SWORD, 590, 1300, "minecraft:nether/obtain_blaze_rod", List.of(new Objective("kill:WITHER_SKELETON", 60, "Defeat 60 Wither Skeletons."))),
        new Pack("hard_094", "Blaze Rod Baron", Material.DIAMOND_SWORD, 530, 1160, "minecraft:nether/obtain_blaze_rod", List.of(new Objective("kill:BLAZE", 60, "Defeat 60 Blazes."))),
        new Pack("hard_095", "Ghast Gauntlet", Material.DIAMOND_SWORD, 590, 1220, "minecraft:nether/return_to_sender", List.of(new Objective("kill:GHAST", 16, "Defeat 16 Ghasts."))),
        new Pack("hard_096", "Evoker Execution", Material.DIAMOND_SWORD, 700, 1390, "minecraft:adventure/voluntary_exile", List.of(new Objective("kill:EVOKER", 2, "Defeat 2 Evokers."))),
        new Pack("hard_097", "Vex Vanquisher", Material.DIAMOND_SWORD, 530, 1080, "minecraft:adventure/voluntary_exile", List.of(new Objective("kill:VEX", 20, "Defeat 20 Vexes."))),
        new Pack("hard_098", "Captain's Bounty", Material.DIAMOND_SWORD, 470, 990, "minecraft:adventure/voluntary_exile", List.of(new Objective("kill:PILLAGER", 40, "Defeat 40 Pillagers."))),
        new Pack("hard_099", "Nether Conqueror", Material.ANCIENT_DEBRIS, 700, 1470, "minecraft:nether/obtain_ancient_debris", List.of(new Objective("break:ANCIENT_DEBRIS", 4, "Mine 4 Ancient Debris blocks."), new Objective("kill:BLAZE", 25, "Defeat 25 Blazes."), new Objective("kill:WITHER_SKELETON", 15, "Defeat 15 Wither Skeletons."))),
        new Pack("hard_100", "Overworld Overlord", Material.DIAMOND_ORE, 700, 1550, "minecraft:story/shiny_gear", List.of(new Objective("break:DIAMOND_ORE", 20, "Mine 20 Diamond Ore blocks (deepslate variants also count)."), new Objective("kill:CREEPER", 20, "Defeat 20 Creepers."), new Objective("fish:ENCHANTED_BOOK", 3, "Catch 3 Enchanted Books while fishing."), new Objective("travel", 5000, "Travel 5,000 blocks."))),
        new Pack("master_001", "Enderman Exterminator", Material.NETHERITE_SWORD, 2100, 4900, "minecraft:end/enter_end_gateway", List.of(new Objective("kill:ENDERMAN", 60, "Defeat 60 Endermen."))),
        new Pack("master_002", "End Stone Excavator", Material.END_STONE, 1200, 3200, "minecraft:end/enter_end_gateway", List.of(new Objective("break:END_STONE", 3000, "Break 3,000 End Stone blocks."))),
        new Pack("master_003", "Purpur Pillager", Material.PURPUR_BLOCK, 1650, 4150, "minecraft:end/find_end_city", List.of(new Objective("break:PURPUR_BLOCK", 384, "Break 384 Purpur Blocks blocks."))),
        new Pack("master_004", "Chorus Harvest", Material.CHORUS_PLANT, 1450, 3750, "minecraft:end/enter_end_gateway", List.of(new Objective("break:CHORUS_PLANT", 600, "Break 600 Chorus Plants blocks."))),
        new Pack("master_005", "Shulker Shell Hunt", Material.NETHERITE_SWORD, 2350, 5300, "minecraft:end/find_end_city", List.of(new Objective("kill:SHULKER", 16, "Defeat 16 Shulkers."))),
        new Pack("master_006", "End City Diamond Reserve", Material.DIAMOND, 2350, 5300, "minecraft:end/find_end_city", List.of(new Objective("collect:DIAMOND", 32, "Collect 32 Diamonds."))),
        new Pack("master_007", "Dragon Head Heist", Material.DRAGON_HEAD, 2800, 6050, "minecraft:end/find_end_city", List.of(new Objective("break:DRAGON_HEAD", 1, "Break 1 Dragon Head blocks."))),
        new Pack("master_008", "Sky Pirate", Material.COMPASS, 2100, 4800, "minecraft:end/elytra", List.of(new Objective("travel", 25000, "Travel 25,000 blocks."))),
        new Pack("master_009", "Long-Range End Trek", Material.COMPASS, 1900, 4500, "minecraft:end/enter_end_gateway", List.of(new Objective("travel", 4000, "Travel 4,000 blocks."))),
        new Pack("master_010", "Endgame Supply Run", Material.NETHERITE_SWORD, 2100, 5200, "minecraft:end/find_end_city", List.of(new Objective("kill:ENDERMAN", 30, "Defeat 30 Endermen."), new Objective("break:END_STONE", 1500, "Break 1,500 End Stone blocks."), new Objective("break:CHORUS_PLANT", 256, "Break 256 Chorus Plants blocks."))),
        new Pack("master_011", "Debris Magnate", Material.ANCIENT_DEBRIS, 2800, 6250, "minecraft:nether/obtain_ancient_debris", List.of(new Objective("break:ANCIENT_DEBRIS", 24, "Mine 24 Ancient Debris blocks."))),
        new Pack("master_012", "Scrap Smelter Supreme", Material.NETHERITE_SCRAP, 2800, 6400, "minecraft:nether/obtain_ancient_debris", List.of(new Objective("collect:NETHERITE_SCRAP", 12, "Collect 12 Netherite Scraps."))),
        new Pack("master_013", "Quartz Empire", Material.NETHER_QUARTZ_ORE, 1450, 3900, "minecraft:nether/find_fortress", List.of(new Objective("break:NETHER_QUARTZ_ORE", 768, "Mine 768 Nether Quartz Ore blocks (deepslate variants also count)."))),
        new Pack("master_014", "Gold Nugget Reserve", Material.GOLD_NUGGET, 1900, 4500, "minecraft:nether/distract_piglin", List.of(new Objective("collect:GOLD_NUGGET", 96, "Collect 96 Gold Nuggets."))),
        new Pack("master_015", "Gilded Gambit", Material.GILDED_BLACKSTONE, 2100, 4800, "minecraft:nether/find_bastion", List.of(new Objective("break:GILDED_BLACKSTONE", 40, "Break 40 Gilded Blackstone blocks."))),
        new Pack("master_016", "Rancher's Feeding Order", Material.WHEAT, 2100, 4650, "minecraft:nether/find_bastion", List.of(new Objective("feed:PIG", 12, "Feed 12 adult pigs using carrots, potatoes, or beetroot."))),
        new Pack("master_017", "Skull Magnate", Material.NETHERITE_SWORD, 2350, 5700, "minecraft:nether/get_wither_skull", List.of(new Objective("kill:WITHER_SKELETON", 120, "Defeat 120 Wither Skeletons."))),
        new Pack("master_018", "Blaze Baron", Material.NETHERITE_SWORD, 2100, 5300, "minecraft:nether/obtain_blaze_rod", List.of(new Objective("kill:BLAZE", 120, "Defeat 120 Blazes."))),
        new Pack("master_019", "Bastion Gold Reserve", Material.GOLD_INGOT, 2550, 5600, "minecraft:nether/loot_bastion", List.of(new Objective("collect:GOLD_INGOT", 16, "Collect 16 Gold Ingots."))),
        new Pack("master_020", "Fortress Blaze Rod Shipment", Material.NETHERITE_SWORD, 2550, 6250, "minecraft:nether/find_fortress", List.of(new Objective("kill:WITHER_SKELETON", 50, "Defeat 50 Wither Skeletons."), new Objective("kill:BLAZE", 50, "Defeat 50 Blazes."), new Objective("collect:BLAZE_ROD", 24, "Collect 24 Blaze Rods."))),
        new Pack("master_021", "Wither Wrecker", Material.NETHERITE_SWORD, 2800, 6400, "minecraft:nether/summon_wither", List.of(new Objective("kill:WITHER", 1, "Defeat 1 Wither."))),
        new Pack("master_022", "Triple Wither", Material.NETHERITE_SWORD, 3000, 7750, "minecraft:nether/summon_wither", List.of(new Objective("kill:WITHER", 3, "Defeat 3 Withers."))),
        new Pack("master_023", "Dragon Slayer Redux", Material.NETHERITE_SWORD, 3000, 6950, "minecraft:end/respawn_dragon", List.of(new Objective("kill:ENDER_DRAGON", 1, "Defeat 1 Ender Dragon."))),
        new Pack("master_024", "Elder Guardian Trio", Material.NETHERITE_SWORD, 2800, 6650, "minecraft:story/shiny_gear", List.of(new Objective("kill:ELDER_GUARDIAN", 3, "Defeat 3 Elder Guardians."))),
        new Pack("master_025", "Warden Duel", Material.NETHERITE_SWORD, 3000, 6950, "minecraft:adventure/avoid_vibration", List.of(new Objective("kill:WARDEN", 1, "Defeat 1 Warden."))),
        new Pack("master_026", "Ravager Triple Hunt", Material.NETHERITE_SWORD, 2800, 7400, "minecraft:adventure/kill_a_mob", List.of(new Objective("kill:RAVAGER", 3, "Defeat 3 Ravagers."))),
        new Pack("master_027", "Pillager Breaker", Material.NETHERITE_SWORD, 2100, 5200, "minecraft:adventure/voluntary_exile", List.of(new Objective("kill:PILLAGER", 100, "Defeat 100 Pillagers."))),
        new Pack("master_028", "Ravager Rodeo", Material.NETHERITE_SWORD, 2800, 6550, "minecraft:adventure/voluntary_exile", List.of(new Objective("kill:RAVAGER", 8, "Defeat 8 Ravagers."))),
        new Pack("master_029", "Evoker Executioner", Material.NETHERITE_SWORD, 2800, 6500, "minecraft:adventure/totem_of_undying", List.of(new Objective("kill:EVOKER", 6, "Defeat 6 Evokers."))),
        new Pack("master_030", "Boss Rush", Material.NETHERITE_SWORD, 3000, 8000, "minecraft:end/respawn_dragon", List.of(new Objective("kill:ELDER_GUARDIAN", 1, "Defeat 1 Elder Guardian."), new Objective("kill:WITHER", 1, "Defeat 1 Wither."), new Objective("kill:ENDER_DRAGON", 1, "Defeat 1 Ender Dragon."))),
        new Pack("master_031", "Sculk Sweeper", Material.SCULK, 1650, 4150, "minecraft:adventure/avoid_vibration", List.of(new Objective("break:SCULK", 600, "Break 600 Sculks blocks."))),
        new Pack("master_032", "Echo Shard Expedition", Material.ECHO_SHARD, 2800, 6400, "minecraft:adventure/avoid_vibration", List.of(new Objective("collect:ECHO_SHARD", 40, "Collect 40 Echo Shards."))),
        new Pack("master_033", "Golden Apple Cache", Material.GOLDEN_APPLE, 2550, 5600, "minecraft:adventure/avoid_vibration", List.of(new Objective("collect:GOLDEN_APPLE", 12, "Collect 12 Golden Apples."))),
        new Pack("master_034", "Deepslate Titan", Material.DEEPSLATE, 1200, 3550, "minecraft:story/mine_diamond", List.of(new Objective("break:DEEPSLATE", 8000, "Break 8,000 Deepslate blocks."))),
        new Pack("master_035", "Diamond Dynasty", Material.DIAMOND_ORE, 2550, 6000, "minecraft:story/shiny_gear", List.of(new Objective("break:DIAMOND_ORE", 64, "Mine 64 Diamond Ore blocks (deepslate variants also count)."))),
        new Pack("master_036", "Emerald Empire", Material.EMERALD_ORE, 2550, 6150, "minecraft:story/mine_diamond", List.of(new Objective("break:EMERALD_ORE", 32, "Mine 32 Emerald Ore blocks (deepslate variants also count)."))),
        new Pack("master_037", "Redstone Reactor", Material.REDSTONE_ORE, 1650, 4200, "minecraft:story/mine_diamond", List.of(new Objective("break:REDSTONE_ORE", 600, "Mine 600 Redstone Ore blocks (deepslate variants also count)."))),
        new Pack("master_038", "Lapis Legion", Material.LAPIS_ORE, 1650, 4200, "minecraft:story/enchant_item", List.of(new Objective("break:LAPIS_ORE", 400, "Mine 400 Lapis Ore blocks (deepslate variants also count)."))),
        new Pack("master_039", "Geode Gatherer", Material.AMETHYST_CLUSTER, 2100, 5100, "minecraft:story/mine_diamond", List.of(new Objective("break:AMETHYST_CLUSTER", 80, "Break 80 Amethyst Clusters blocks."))),
        new Pack("master_040", "Dark Descent", Material.IRON_ORE, 1900, 5000, "minecraft:story/shiny_gear", List.of(new Objective("break:IRON_ORE", 1500, "Mine 1,500 Iron Ore blocks (deepslate variants also count)."))),
        new Pack("master_041", "Mansion Totem Empire", Material.TOTEM_OF_UNDYING, 2800, 6400, "minecraft:adventure/voluntary_exile", List.of(new Objective("collect:TOTEM_OF_UNDYING", 32, "Collect 32 Totems of Undying."))),
        new Pack("master_042", "Mansion Cleansing", Material.NETHERITE_SWORD, 2550, 6050, "minecraft:adventure/voluntary_exile", List.of(new Objective("kill:VINDICATOR", 30, "Defeat 30 Vindicators."), new Objective("kill:EVOKER", 3, "Defeat 3 Evokers."), new Objective("kill:VEX", 20, "Defeat 20 Vexes."))),
        new Pack("master_043", "Outpost Pillager Supply", Material.CROSSBOW, 2100, 5100, "minecraft:adventure/voluntary_exile", List.of(new Objective("collect:CROSSBOW", 24, "Collect 24 Crossbows."), new Objective("kill:PILLAGER", 40, "Defeat 40 Pillagers."))),
        new Pack("master_044", "Captain Hunter", Material.NETHERITE_SWORD, 1900, 4900, "minecraft:adventure/voluntary_exile", List.of(new Objective("kill:PILLAGER", 80, "Defeat 80 Pillagers."))),
        new Pack("master_045", "Illager Annihilator", Material.NETHERITE_SWORD, 2100, 5200, "minecraft:adventure/voluntary_exile", List.of(new Objective("kill:VINDICATOR", 50, "Defeat 50 Vindicators."))),
        new Pack("master_046", "Totem Trader", Material.NETHERITE_SWORD, 2800, 6750, "minecraft:adventure/totem_of_undying", List.of(new Objective("kill:EVOKER", 10, "Defeat 10 Evokers."))),
        new Pack("master_047", "Witch Doctor", Material.NETHERITE_SWORD, 2100, 5150, "minecraft:story/shiny_gear", List.of(new Objective("kill:WITCH", 40, "Defeat 40 Witches."))),
        new Pack("master_048", "Patrol Marathon", Material.NETHERITE_SWORD, 1900, 5150, "minecraft:adventure/voluntary_exile", List.of(new Objective("kill:PILLAGER", 150, "Defeat 150 Pillagers."))),
        new Pack("master_049", "Vex Vendetta", Material.NETHERITE_SWORD, 2100, 5100, "minecraft:adventure/voluntary_exile", List.of(new Objective("kill:VEX", 60, "Defeat 60 Vexes."))),
        new Pack("master_050", "Raid Master", Material.NETHERITE_SWORD, 2550, 6650, "minecraft:adventure/hero_of_the_village", List.of(new Objective("kill:PILLAGER", 200, "Defeat 200 Pillagers."))),
        new Pack("master_051", "Trial Key Collection", Material.TRIAL_KEY, 2100, 4950, "minecraft:adventure/minecraft_trials_edition", List.of(new Objective("collect:TRIAL_KEY", 8, "Collect 8 Trial Keys."))),
        new Pack("master_052", "Breeze Trial Patrol", Material.NETHERITE_SWORD, 2350, 5450, "minecraft:adventure/under_lock_and_key", List.of(new Objective("kill:BREEZE", 12, "Defeat 12 Breezes."))),
        new Pack("master_053", "Breeze Breaker", Material.NETHERITE_SWORD, 2350, 5450, "minecraft:adventure/minecraft_trials_edition", List.of(new Objective("kill:BREEZE", 30, "Defeat 30 Breezes."))),
        new Pack("master_054", "Bogged Down", Material.NETHERITE_SWORD, 1900, 4650, "minecraft:adventure/minecraft_trials_edition", List.of(new Objective("kill:BOGGED", 40, "Defeat 40 Bogged."))),
        new Pack("master_055", "Trial Breeze Patrol", Material.NETHERITE_SWORD, 2100, 5200, "minecraft:adventure/minecraft_trials_edition", List.of(new Objective("kill:BREEZE", 80, "Defeat 80 Breezes."))),
        new Pack("master_056", "Trial Key Stockpile", Material.TRIAL_KEY, 2350, 5700, "minecraft:adventure/minecraft_trials_edition", List.of(new Objective("collect:TRIAL_KEY", 20, "Collect 20 Trial Keys."))),
        new Pack("master_057", "Breeze Veteran", Material.NETHERITE_SWORD, 2550, 6150, "minecraft:adventure/revaulting", List.of(new Objective("kill:BREEZE", 32, "Defeat 32 Breezes."))),
        new Pack("master_058", "Ominous Breeze Hunt", Material.NETHERITE_SWORD, 3000, 7200, "minecraft:adventure/revaulting", List.of(new Objective("kill:BREEZE", 12, "Defeat 12 Breezes."))),
        new Pack("master_059", "Breeze Marathon", Material.NETHERITE_SWORD, 2350, 5800, "minecraft:adventure/under_lock_and_key", List.of(new Objective("kill:BREEZE", 200, "Defeat 200 Breezes."))),
        new Pack("master_060", "Heavy Hitter", Material.NETHERITE_SWORD, 2550, 6150, "minecraft:adventure/overoverkill", List.of(new Objective("kill:ZOMBIE", 150, "Defeat 150 Zombies."))),
        new Pack("master_061", "Monument Marauder", Material.NETHERITE_SWORD, 2100, 5350, "minecraft:story/shiny_gear", List.of(new Objective("kill:GUARDIAN", 80, "Defeat 80 Guardians."))),
        new Pack("master_062", "Elder Duo", Material.NETHERITE_SWORD, 2800, 6400, "minecraft:story/shiny_gear", List.of(new Objective("kill:ELDER_GUARDIAN", 2, "Defeat 2 Elder Guardians."))),
        new Pack("master_063", "Sponge Collector", Material.WET_SPONGE, 2550, 5600, "minecraft:story/shiny_gear", List.of(new Objective("break:WET_SPONGE", 24, "Break 24 Wet Sponges blocks."))),
        new Pack("master_064", "Prismarine Palace", Material.PRISMARINE, 1650, 4400, "minecraft:story/mine_diamond", List.of(new Objective("break:PRISMARINE", 800, "Break 800 Prismarine blocks."))),
        new Pack("master_065", "Sea Lantern Empire", Material.SEA_LANTERN, 2100, 5100, "minecraft:story/mine_diamond", List.of(new Objective("break:SEA_LANTERN", 80, "Break 80 Sea Lanterns blocks."))),
        new Pack("master_066", "Trench Diver", Material.NETHERITE_SWORD, 1650, 4650, "minecraft:husbandry/fishy_business", List.of(new Objective("kill:DROWNED", 150, "Defeat 150 Drowned."))),
        new Pack("master_067", "Treasure Tycoon II", Material.FISHING_ROD, 2350, 5700, "minecraft:husbandry/fishy_business", List.of(new Objective("fish:ENCHANTED_BOOK", 12, "Catch 12 Enchanted Books while fishing."))),
        new Pack("master_068", "Tropical Tycoon", Material.FISHING_ROD, 2550, 6250, "minecraft:husbandry/tactical_fishing", List.of(new Objective("fish:TROPICAL_FISH", 10, "Catch 10 Tropical Fishs while fishing."))),
        new Pack("master_069", "Pufferfish Plague II", Material.FISHING_ROD, 2100, 5300, "minecraft:husbandry/fishy_business", List.of(new Objective("fish:PUFFERFISH", 40, "Catch 40 Pufferfishs while fishing."))),
        new Pack("master_070", "Explorer's 16,000-Block Route", Material.COMPASS, 1900, 4850, "minecraft:story/iron_tools", List.of(new Objective("travel", 16000, "Travel 16,000 blocks."))),
        new Pack("master_071", "Miner's Grand Slam", Material.DIAMOND_ORE, 2800, 7050, "minecraft:story/shiny_gear", List.of(new Objective("break:DIAMOND_ORE", 32, "Mine 32 Diamond Ore blocks (deepslate variants also count)."), new Objective("break:EMERALD_ORE", 12, "Mine 12 Emerald Ore blocks (deepslate variants also count)."), new Objective("break:GOLD_ORE", 64, "Mine 64 Gold Ore blocks (deepslate variants also count)."), new Objective("break:ANCIENT_DEBRIS", 6, "Mine 6 Ancient Debris blocks."))),
        new Pack("master_072", "Hunter's Grand Slam", Material.NETHERITE_SWORD, 2350, 5950, "minecraft:story/shiny_gear", List.of(new Objective("kill:ENDERMAN", 30, "Defeat 30 Endermen."), new Objective("kill:BLAZE", 30, "Defeat 30 Blazes."), new Objective("kill:WITCH", 15, "Defeat 15 Witches."), new Objective("kill:CREEPER", 40, "Defeat 40 Creepers."))),
        new Pack("master_073", "Farmer's Grand Slam", Material.WHEAT, 1650, 4800, "minecraft:husbandry/plant_seed", List.of(new Objective("harvest:WHEAT", 512, "Harvest 512 Wheat from fully grown crops."), new Objective("harvest:CARROT", 512, "Harvest 512 Carrots from fully grown crops."), new Objective("harvest:POTATO", 512, "Harvest 512 Potatos from fully grown crops."), new Objective("harvest:MELON_SLICE", 128, "Harvest 128 Melons."), new Objective("harvest:PUMPKIN", 128, "Harvest 128 Pumpkins."))),
        new Pack("master_074", "Angler's Grand Slam", Material.FISHING_ROD, 2350, 5950, "minecraft:husbandry/tactical_fishing", List.of(new Objective("fish:COD", 60, "Catch 60 Cods while fishing."), new Objective("fish:SALMON", 40, "Catch 40 Salmons while fishing."), new Objective("fish:PUFFERFISH", 20, "Catch 20 Pufferfishs while fishing."), new Objective("fish:TROPICAL_FISH", 4, "Catch 4 Tropical Fishs while fishing."))),
        new Pack("master_075", "Smith's Grand Slam", Material.IRON_INGOT, 1900, 5150, "minecraft:story/mine_diamond", List.of(new Objective("collect:IRON_INGOT", 256, "Collect 256 Iron Ingots."), new Objective("collect:GOLD_INGOT", 96, "Collect 96 Gold Ingots."), new Objective("collect:COPPER_INGOT", 256, "Collect 256 Copper Ingots."), new Objective("collect:GLASS", 512, "Collect 512 Glasss."))),
        new Pack("master_076", "Explorer's Distance Grand Slam", Material.COMPASS, 2100, 5950, "minecraft:adventure/sleep_in_bed", List.of(new Objective("travel", 55000, "Travel 55,000 blocks."))),
        new Pack("master_077", "Emerald Collector's Grand Slam", Material.EMERALD, 2350, 6100, "minecraft:story/shiny_gear", List.of(new Objective("collect:EMERALD", 160, "Collect 160 Emeralds."))),
        new Pack("master_078", "Rancher's Grand Circuit", Material.WHEAT, 1900, 5250, "minecraft:husbandry/plant_seed", List.of(new Objective("feed:COW", 17, "Feed 17 adult cows using wheat."), new Objective("feed:SHEEP", 17, "Feed 17 adult sheep using wheat."), new Objective("feed:GOAT", 17, "Feed 17 adult goats using wheat."), new Objective("feed:PIG", 17, "Feed 17 adult pigs using carrots, potatoes, or beetroot."), new Objective("feed:CHICKEN", 16, "Feed 16 adult chickens using seeds."), new Objective("feed:RABBIT", 16, "Feed 16 adult rabbits using carrots, golden carrots, or dandelions."))),
        new Pack("master_079", "Dimension Hopper", Material.ANCIENT_DEBRIS, 2800, 6900, "minecraft:end/find_end_city", List.of(new Objective("break:ANCIENT_DEBRIS", 8, "Mine 8 Ancient Debris blocks."), new Objective("kill:SHULKER", 8, "Defeat 8 Shulkers."), new Objective("fish:COD", 100, "Catch 100 Cods while fishing."))),
        new Pack("master_080", "Master Chef", Material.COOKED_BEEF, 1650, 4650, "minecraft:husbandry/balanced_diet", List.of(new Objective("collect:COOKED_BEEF", 1100, "Collect 1,100 Cooked Beef."))),
        new Pack("master_081", "Mountain Mover", Material.STONE, 1000, 3500, "minecraft:story/mine_diamond", List.of(new Objective("break:STONE", 10000, "Break 10,000 Stone blocks."))),
        new Pack("master_082", "Ore Baron", Material.IRON_ORE, 1900, 5500, "minecraft:story/shiny_gear", List.of(new Objective("break:IRON_ORE", 3000, "Mine 3,000 Iron Ore blocks (deepslate variants also count)."))),
        new Pack("master_083", "Forest Eater", Material.OAK_LOG, 1200, 3950, "minecraft:story/iron_tools", List.of(new Objective("break:OAK_LOG", 6000, "Chop 6,000 Oak Logs."))),
        new Pack("master_084", "Wheatfield Marshal", Material.WHEAT, 1200, 4200, "minecraft:husbandry/plant_seed", List.of(new Objective("harvest:WHEAT", 4000, "Harvest 4,000 Wheat from fully grown crops."))),
        new Pack("master_085", "Great Wall Walk", Material.COMPASS, 1450, 5600, "minecraft:story/iron_tools", List.of(new Objective("travel", 60000, "Travel 60,000 blocks."))),
        new Pack("master_086", "Zombie Slayer Ascendant", Material.NETHERITE_SWORD, 1650, 5800, "minecraft:story/shiny_gear", List.of(new Objective("kill:ZOMBIE", 1000, "Defeat 1,000 Zombies."))),
        new Pack("master_087", "Angler Ascendant", Material.FISHING_ROD, 1450, 5200, "minecraft:husbandry/tactical_fishing", List.of(new Objective("fish:COD", 900, "Catch 900 Cods while fishing."))),
        new Pack("master_088", "Ironworks Emperor", Material.ANY, 1450, 4850, "minecraft:story/mine_diamond", List.of(new Objective("collect:ANY", 3000, "Collect 3,000 Anys."))),
        new Pack("master_089", "Herd Feeding Master", Material.WHEAT, 1200, 3950, "minecraft:husbandry/plant_seed", List.of(new Objective("feed:COW", 50, "Feed 50 adult cows using wheat."), new Objective("feed:SHEEP", 50, "Feed 50 adult sheep using wheat."), new Objective("feed:GOAT", 50, "Feed 50 adult goats using wheat."), new Objective("feed:PIG", 50, "Feed 50 adult pigs using carrots, potatoes, or beetroot."), new Objective("feed:CHICKEN", 50, "Feed 50 adult chickens using seeds."), new Objective("feed:RABBIT", 50, "Feed 50 adult rabbits using carrots, golden carrots, or dandelions."))),
        new Pack("master_090", "Nether Wanderer", Material.COMPASS, 1650, 4750, "minecraft:nether/fast_travel", List.of(new Objective("travel", 30000, "Travel 30,000 blocks."))),
        new Pack("master_091", "Three Realms Trial", Material.IRON_ORE, 2550, 6550, "minecraft:end/find_end_city", List.of(new Objective("break:IRON_ORE", 128, "Mine 128 Iron Ore blocks (deepslate variants also count)."), new Objective("kill:BLAZE", 30, "Defeat 30 Blazes."), new Objective("kill:ENDERMAN", 30, "Defeat 30 Endermen."))),
        new Pack("master_092", "Netherite Initiation", Material.ANCIENT_DEBRIS, 2800, 6900, "minecraft:nether/obtain_ancient_debris", List.of(new Objective("break:ANCIENT_DEBRIS", 8, "Mine 8 Ancient Debris blocks."), new Objective("collect:NETHERITE_SCRAP", 8, "Collect 8 Netherite Scraps."), new Objective("kill:WITHER_SKELETON", 30, "Defeat 30 Wither Skeletons."))),
        new Pack("master_093", "End Game Gauntlet", Material.NETHERITE_SWORD, 2800, 6800, "minecraft:end/find_end_city", List.of(new Objective("kill:SHULKER", 12, "Defeat 12 Shulkers."), new Objective("collect:DIAMOND", 24, "Collect 24 Diamonds."), new Objective("kill:ENDERMAN", 40, "Defeat 40 Endermen."))),
        new Pack("master_094", "Abyss Walker", Material.NETHERITE_SWORD, 3000, 7350, "minecraft:adventure/avoid_vibration", List.of(new Objective("kill:WARDEN", 1, "Defeat 1 Warden."), new Objective("collect:ECHO_SHARD", 24, "Collect 24 Echo Shards."))),
        new Pack("master_095", "Siege of the Wither", Material.NETHERITE_SWORD, 3000, 7250, "minecraft:nether/summon_wither", List.of(new Objective("kill:WITHER", 1, "Defeat 1 Wither."), new Objective("kill:WITHER_SKELETON", 40, "Defeat 40 Wither Skeletons."))),
        new Pack("master_096", "Pillager's Doom", Material.TOTEM_OF_UNDYING, 3000, 7200, "minecraft:adventure/voluntary_exile", List.of(new Objective("collect:TOTEM_OF_UNDYING", 24, "Collect 24 Totems of Undying."), new Objective("kill:EVOKER", 4, "Defeat 4 Evokers."), new Objective("kill:RAVAGER", 4, "Defeat 4 Ravagers."))),
        new Pack("master_097", "Ocean Overlord", Material.NETHERITE_SWORD, 2800, 7200, "minecraft:story/shiny_gear", List.of(new Objective("kill:ELDER_GUARDIAN", 2, "Defeat 2 Elder Guardians."), new Objective("kill:GUARDIAN", 60, "Defeat 60 Guardians."), new Objective("break:SPONGE", 16, "Break 16 Sponges blocks."))),
        new Pack("master_098", "Breeze & Blaze Gauntlet", Material.NETHERITE_SWORD, 2800, 6900, "minecraft:adventure/under_lock_and_key", List.of(new Objective("kill:BREEZE", 60, "Defeat 60 Breezes."), new Objective("kill:BLAZE", 40, "Defeat 40 Blazes."))),
        new Pack("master_099", "Legend of the Long-Distance Wanderer", Material.COMPASS, 2800, 7700, "minecraft:adventure/sleep_in_bed", List.of(new Objective("travel", 76000, "Travel 76,000 blocks."), new Objective("collect:EMERALD", 120, "Collect 120 Emeralds."))),
        new Pack("master_100", "The Grand Master", Material.NETHERITE_SWORD, 3000, 8000, "minecraft:end/respawn_dragon", List.of(new Objective("kill:ENDER_DRAGON", 1, "Defeat 1 Ender Dragon."), new Objective("kill:WITHER", 1, "Defeat 1 Wither."), new Objective("kill:ELDER_GUARDIAN", 1, "Defeat 1 Elder Guardian."), new Objective("kill:WARDEN", 1, "Defeat 1 Warden.")))
    );

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
            case "hard" -> "Unlock Hard packs by earning Diamonds! (minecraft:story/mine_diamond).";
            case "master" -> "Unlock Master packs by earning Free the End (minecraft:end/kill_dragon).";
            default -> "Easy packs are available from the start; individual packs have requirements.";
        };
    }

    private String packUnlockDescription(Player player, Pack pack) {
        if (!tierUnlocked(player, tierOf(pack))) return tierUnlockDescription(tierOf(pack));
        return "Unlock requirement: " + advancementTitle(pack.advancement()) + " (" + pack.advancement() + ").";
    }

    private String advancementTitle(String rawKey) {
        String key = rawKey.startsWith("minecraft:") ? rawKey.substring("minecraft:".length()) : rawKey;
        return switch (key) {
            case "adventure/avoid_vibration" -> "Sneak 100";
            case "adventure/hero_of_the_village" -> "Hero of the Village";
            case "adventure/kill_a_mob" -> "Monster Hunter";
            case "adventure/minecraft_trials_edition" -> "Minecraft: Trial(s) Edition";
            case "adventure/overoverkill" -> "Over-Overkill";
            case "adventure/revaulting" -> "Revaulting";
            case "adventure/shoot_arrow" -> "Take Aim";
            case "adventure/sleep_in_bed" -> "Sweet Dreams";
            case "adventure/totem_of_undying" -> "Postmortal";
            case "adventure/trade" -> "What a Deal!";
            case "adventure/under_lock_and_key" -> "Under Lock and Key";
            case "adventure/voluntary_exile" -> "Voluntary Exile";
            case "end/elytra" -> "Sky's the Limit";
            case "end/enter_end_gateway" -> "Remote Getaway";
            case "end/find_end_city" -> "The City at the End of the Game";
            case "end/respawn_dragon" -> "The End... Again...";
            case "end/kill_dragon" -> "Free the End";
            case "husbandry/balanced_diet" -> "A Balanced Diet";
            case "husbandry/breed_an_animal" -> "The Parrots and the Bats";
            case "husbandry/bred_all_animals" -> "Two by Two";
            case "husbandry/fishy_business" -> "Fishy Business";
            case "husbandry/plant_seed" -> "A Seedy Place";
            case "husbandry/tactical_fishing" -> "Tactical Fishing";
            case "husbandry/tame_an_animal" -> "Best Friends Forever";
            case "nether/distract_piglin" -> "Oh Shiny";
            case "nether/fast_travel" -> "Subspace Bubble";
            case "nether/find_bastion" -> "Those Were the Days";
            case "nether/find_fortress" -> "A Terrible Fortress";
            case "nether/get_wither_skull" -> "Spooky Scary Skeleton";
            case "nether/loot_bastion" -> "War Pigs";
            case "nether/obtain_ancient_debris" -> "Hidden in the Depths";
            case "nether/obtain_blaze_rod" -> "Into Fire";
            case "nether/obtain_crying_obsidian" -> "Who is Cutting Onions?";
            case "nether/return_to_sender" -> "Return to Sender";
            case "nether/ride_strider" -> "This Boat Has Legs";
            case "nether/root" -> "Nether";
            case "nether/summon_wither" -> "Withering Heights";
            case "story/enchant_item" -> "Enchanter";
            case "story/follow_ender_eye" -> "Eye Spy";
            case "story/form_obsidian" -> "Ice Bucket Challenge";
            case "story/iron_tools" -> "Isn't It Iron Pick";
            case "story/mine_diamond" -> "Diamonds!";
            case "story/mine_stone" -> "Stone Age";
            case "story/obtain_armor" -> "Suit Up";
            case "story/shiny_gear" -> "Cover Me with Diamonds";
            case "story/smelt_iron" -> "Acquire Hardware";
            case "story/upgrade_tools" -> "Getting an Upgrade";
            default -> {
                String leaf = key.substring(key.lastIndexOf('/') + 1).replace('_', ' ');
                yield leaf.isEmpty() ? key : leaf.substring(0, 1).toUpperCase(Locale.ROOT) + leaf.substring(1);
            }
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
        Bukkit.getPluginManager().registerEvents(this, plugin);
        resetEventIfNeeded();
        Bukkit.getScheduler().runTaskTimer(plugin, this::resetEventIfNeeded, 20L * 60L, 20L * 60L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::refreshSingleShopIfDue, 20L * 60L, 20L * 60L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::refreshSecretQuestIfDue, 20L * 60L, 20L * 60L);
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) updateHud(player);
        }, 20L, 20L);
        // Shared global reminder and progress broadcast every 15 minutes while the event is active.
        Bukkit.getScheduler().runTaskTimer(plugin, this::broadcastGlobalProgress, 20L * 60L * 15L, 20L * 60L * 15L);
        if (!data.contains("single-shop.next-refresh")) {
            data.set("single-shop.next-refresh", 0L);
            data.set("single-shop.refresh-id", 0);
            refreshSingleShopIfDue();
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
        add(inv, 10, Material.WRITABLE_BOOK, ChatColor.YELLOW + "Single Quests", "section-single", "single",
            List.of(ChatColor.GRAY + "Random challenge generated on every purchase",
                    ChatColor.GRAY + "Shop offer refreshes every 2 hours"));
        add(inv, 13, Material.ENCHANTED_BOOK, ChatColor.LIGHT_PURPLE + "Achievement Quest Packs", "section-packs", "packs",
            List.of(ChatColor.GRAY + "Hard multi-objective challenges",
                    ChatColor.GRAY + "Each locked pack explains its unlock requirement"));
        add(inv, 16, Material.NETHER_STAR, ChatColor.GOLD + "Global Quest", "section-global", "global",
            List.of(ChatColor.GRAY + "Everyone contributes to shared progress",
                    ChatColor.GRAY + "Server reminders every 15 minutes"));
        add(inv, 22, Material.ARROW, ChatColor.WHITE + "Back to Shop", "back-shop", "");
        player.openInventory(inv);
    }

    private void openSingle(Player player) {
        refreshSingleShopIfDue();
        ensureSingleOfferDiversity();
        refreshSecretQuestIfDue();
        long next = data.getLong("single-shop.next-refresh", nextRefresh(System.currentTimeMillis()));
        long mins = Math.max(0, (next - System.currentTimeMillis()) / 60000L);
        Inventory inv = Bukkit.createInventory(null, 54, ChatColor.DARK_PURPLE + "XZH Single Quests");
        // Secret Quest is intentionally the first item in the menu; its 30-minute cycle is unchanged.
        add(inv, 10, Material.ENDER_EYE, ChatColor.LIGHT_PURPLE + "??? Secret Quest", "single-secret-buy", "secret",
            List.of(ChatColor.WHITE + "Purchase price: " + ChatColor.YELLOW + data.getInt("single-shop.secret.cost", 250) + " coins",
                    ChatColor.WHITE + "Profit: " + ChatColor.GREEN + "+" + data.getInt("single-shop.secret.margin", 40) + "%",
                    ChatColor.GRAY + "Objective and reward details are hidden.",
                    ChatColor.GRAY + "Refresh in " + Math.max(0, (data.getLong("single-shop.secret.next-refresh", System.currentTimeMillis()) - System.currentTimeMillis()) / 60000L) + " minute(s)"));
        for (int i = 0; i < 5; i++) {
            int poolIndex = data.getInt("single-shop.offers." + i, i % randomPool.size());
            RandomQuest offer = randomPool.get(Math.floorMod(poolIndex, randomPool.size()));
            int cost = offer.cost();
            int reward = (int) Math.ceil(cost * 1.4);
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.WHITE + "Job: " + offer.name());
            lore.add(ChatColor.AQUA + offer.objective().description());
            lore.add(ChatColor.WHITE + "Required amount: " + offer.objective().goal());
            lore.add(ChatColor.WHITE + "Purchase price: " + ChatColor.YELLOW + cost + " coins");
            lore.add(ChatColor.WHITE + "Completion reward: " + ChatColor.GREEN + reward + " coins");
            lore.add(isTimedSingle(offer) ? ChatColor.RED + "Time limit: 30 minutes" : ChatColor.GRAY + "No time limit");
            lore.add(ChatColor.GRAY + "Refresh in " + (mins / 60) + "h " + (mins % 60) + "m");
            add(inv, 11 + i, offer.icon(), ChatColor.YELLOW + offer.name(), "single-buy", Integer.toString(i), lore);
        }
        add(inv, 31, Material.CLOCK, ChatColor.AQUA + "Offer refresh", "single-info", "",
            List.of(ChatColor.WHITE + "Five regular offers refresh every 2 hours.",
                    ChatColor.WHITE + "The secret quest refreshes every 30 minutes.",
                    ChatColor.GRAY + "Buying a quest reveals its objective."));
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
            lore.add(ChatColor.WHITE + "Profit: " + ChatColor.GOLD + "+" + (rewardForPack(pack) - pack.cost()) + " coins");
            lore.add(ChatColor.WHITE + "Objectives:");
            for (Objective objective : pack.objectives()) lore.add(ChatColor.AQUA + "• " + objective.description());
            lore.add(done ? ChatColor.DARK_GRAY + "COMPLETED FOR LIFE — reward already claimed"
                    : unlocked ? ChatColor.GREEN + "UNLOCKED"
                    : ChatColor.RED + packUnlockDescription(player, pack));
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
        lore.add(ChatColor.WHITE + "Cost: " + pack.cost() + " coins");
        lore.add(ChatColor.WHITE + "Reward: " + rewardForPack(pack) + " coins");
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
            plugin.tellPublic(player, "You do not have an active quest or mission to cancel.");
            return false;
        }
        int originalCost = Math.max(0, data.getInt(id + ".cost", 0));
        int refund = originalCost / 2;
        // Clear the active record before refunding so this purchase can never be refunded twice.
        data.set(id.toString(), null);
        save();
        plugin.getHudManager().hideActiveQuest(player);
        if (refund > 0) plugin.setBalance(id, plugin.getBalance(id) + refund);
        if (originalCost > 0) {
            plugin.tellPublic(player, "Quest cancelled. Refunded 50% of its purchase price: " + refund
                    + " coins (original price " + originalCost + "). No completion reward was paid.");
        } else {
            plugin.tellPublic(player, "Quest cancelled. It had no purchase price, so no refund or completion reward was paid.");
        }
        return true;
    }

    private void buyRandom(Player player, int offerSlot) {
        UUID id = player.getUniqueId();
        if (plugin.getLoanManager().isShopPurchaseRestricted(id)) { plugin.tellPublic(player, "Your loan is overdue. Quest purchases are locked; sell eligible items and repay your debt."); return; }
        if (!data.getString(id + ".active", "").isBlank()) { plugin.tellPublic(player, "Finish your active quest before buying another."); return; }
        if (offerSlot < 0 || offerSlot > 5) return;
        boolean secret = offerSlot == 5;
        if (secret) refreshSecretQuestIfDue();
        int poolIndex = Math.floorMod(data.getInt(secret ? "single-shop.secret.offer" : "single-shop.offers." + offerSlot, offerSlot), randomPool.size());
        RandomQuest template = randomPool.get(poolIndex);
        int difficultyScale = 85 + random.nextInt(51);
        int goal = Math.max(1, (int) Math.ceil(template.objective().goal() * difficultyScale / 100.0));
        Objective objective = new Objective(template.objective().target(), goal, template.objective().description().replaceFirst("\\d+", Integer.toString(goal)));
        int cost = secret ? data.getInt("single-shop.secret.cost", 250) : Math.max(50, (int) Math.round(template.cost() * (0.90 + random.nextDouble() * 0.21)));
        int margin = secret ? data.getInt("single-shop.secret.margin", 40) : 40 + random.nextInt(21);
        int reward = (int) Math.ceil(cost * (1.0 + margin / 100.0));
        if (plugin.getCombinedBalance(id) < cost || !plugin.spendAvailableFunds(id, cost)) { plugin.tellPublic(player, "You need " + cost + " coins to buy this random quest."); return; }
        String questName = template.name() + " #" + (data.getInt("single-shop.quest-sequence", 0) + 1);
        data.set("single-shop.quest-sequence", data.getInt("single-shop.quest-sequence", 0) + 1);
        startQuest(player, "single:" + System.currentTimeMillis(), questName, cost, reward, List.of(objective));
        data.set(id + ".profit-margin", margin);
        if (!secret) data.set("single-shop.offers." + offerSlot, nextOfferForSlot(offerSlot));
        save();
        plugin.tellPublic(player, "Started " + questName + ": " + objective.description() + ". Cost " + cost + "; reward " + reward + " coins. " + (isTimedSingle(template) ? "Time limit: 30 minutes." : "No timer."));
        player.closeInventory();
    }

    private boolean isTimedSingle(RandomQuest quest) {
        return quest.name().equals("Ancient Debris Search") || quest.name().equals("Raid the Fortress")
                || quest.name().equals("Fishing for Cod") || quest.name().equals("Travel Contract")
                || quest.name().equals("Wither Skeleton Hunt");
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
        if (!tierUnlocked(player, tierOf(pack))) {
            plugin.tellPublic(player, tierUnlockDescription(tierOf(pack)));
            return;
        }
        if (!hasAdvancement(player, pack.advancement())) {
            plugin.tellPublic(player, packUnlockDescription(player, pack));
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
        startQuest(player, "pack:" + pack.id(), pack.name(), pack.cost(), rewardForPack(pack), pack.objectives());
        player.closeInventory();
        plugin.tellPublic(player, "Quest pack started: " + pack.name() + ". Complete every objective to earn " + pack.reward() + " coins.");
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
    public void onKill(EntityDeathEvent event) {
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
            incrementTarget(player, "collect:" + result.getType().name(), result.getAmount());
        }
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
        if (!(event.getRightClicked() instanceof Animals animal) || animal.getAge() != 0) return;
        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held == null) return;
        Material food = held.getType();
        boolean validFeed = switch (animal.getType()) {
            case COW, SHEEP, GOAT -> food == Material.WHEAT;
            case PIG -> food == Material.CARROT || food == Material.POTATO || food == Material.BEETROOT;
            case CHICKEN -> food == Material.WHEAT_SEEDS || food == Material.BEETROOT_SEEDS || food == Material.MELON_SEEDS || food == Material.PUMPKIN_SEEDS || food == Material.TORCHFLOWER_SEEDS;
            case RABBIT -> food == Material.CARROT || food == Material.GOLDEN_CARROT || food == Material.DANDELION;
            default -> false;
        };
        if (validFeed) incrementTarget(player, "feed:" + animal.getType().name(), 1);
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
            int reward = data.getInt(id + ".reward", 0);
            String activeQuestId = data.getString(id + ".active", "");
            if (activeQuestId.startsWith("pack:")) {
                String packId = activeQuestId.substring("pack:".length());
                data.set("completed-packs." + id + "." + packId, true);
                plugin.tellPublic(player, "§6Achievement pack permanently completed: §e" + packId + "§6. It is now marked with black stained glass and cannot be repeated.");
            }
            plugin.setBalance(id, plugin.getBalance(id) + reward);
            plugin.tellPublic(player, "Quest completed! Reward: " + reward + " coins.");
            data.set(id.toString(), null);
            plugin.getHudManager().hideActiveQuest(player);
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
        Bukkit.broadcastMessage(ChatColor.GOLD + "[Global Quest] " + ChatColor.GREEN
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
        String message = ChatColor.GOLD + "[Global Quest] " + ChatColor.YELLOW
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
            Bukkit.broadcastMessage(ChatColor.GOLD + "[Quests] Five new random single-quest offers are available!");
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
            Bukkit.broadcastMessage(ChatColor.GOLD + "[Global Quest] " + ChatColor.YELLOW
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
            case "locked" -> packs.stream().filter(p -> p.id().equals(value)).findFirst()
                    .ifPresent(p -> plugin.tellPublic(player, packUnlockDescription(player, p)));
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
