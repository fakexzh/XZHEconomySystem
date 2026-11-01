package com.xzh.economy;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.block.Biome;
import org.bukkit.block.data.Ageable;
import org.bukkit.advancement.Advancement;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Vehicle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTameEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/** Random single quests, achievement-unlocked multi-objective packs, and shared community chests. */
public final class QuestManager implements Listener {
    private record Objective(String target, int goal, String description) {}
    private record Pack(String id, String name, Material icon, int cost, int reward, String advancement, String unlockTitle, int timerMinutes, List<Objective> objectives) {}
    private record RandomQuest(String name, Material icon, int cost, int reward, Objective objective) {}
    private record GlobalQuest(String name, Material icon, String target, int goal, String description) {}
    private final List<GlobalQuest> globalQuestPool = List.of(
        new GlobalQuest("Community Ore Rush", Material.IRON_PICKAXE, "ore", 40, "Mine ores together"),
        new GlobalQuest("Deep Mining Expedition", Material.DEEPSLATE_DIAMOND_ORE, "ore", 80, "Mine deep ores together"),
        new GlobalQuest("Stoneworker Union", Material.STONE_PICKAXE, "ore", 120, "Keep the mining crews busy"),
        new GlobalQuest("Monster Patrol", Material.ROTTEN_FLESH, "mob", 80, "Defeat monsters together"),
        new GlobalQuest("Night Watch", Material.SHIELD, "mob", 180, "Protect the settlement from monsters"),
        new GlobalQuest("Dangerous Hunt", Material.NETHER_STAR, "mob", 300, "Complete a demanding monster hunt"),
        new GlobalQuest("Fishing Fleet", Material.FISHING_ROD, "fish", 10, "Catch fish together"),
        new GlobalQuest("Deepwater Haul", Material.COD, "fish", 20, "Bring in a major fishing haul"),
        new GlobalQuest("Anglers Marathon", Material.SALMON, "fish", 30, "Keep fishing until the goal is met"),
        new GlobalQuest("Forest Cleanup", Material.OAK_LOG, "wood", 30, "Chop logs together"),
        new GlobalQuest("Lumber Guild", Material.SPRUCE_LOG, "wood", 60, "Supply the community with timber"),
        new GlobalQuest("Great Reforestation", Material.BIRCH_LOG, "wood", 100, "Gather a huge amount of wood"),
        new GlobalQuest("Harvest Festival", Material.CARROT, "crop", 25, "Harvest crops together"),
        new GlobalQuest("Farmers Market", Material.WHEAT, "crop", 45, "Collect a crop surplus"),
        new GlobalQuest("Grand Harvest", Material.PUMPKIN, "crop", 70, "Complete a demanding farming drive"),
        new GlobalQuest("Community Food Drive", Material.POTATO, "crop", 35, "Stock the community food supply"),
        new GlobalQuest("Carrot Cartel", Material.CARROT, "crop", 45, "Gather carrots for the community"),
        new GlobalQuest("Wheat Supply Run", Material.WHEAT, "crop", 60, "Harvest wheat for a large order"),
        new GlobalQuest("Woodland Supply Run", Material.JUNGLE_LOG, "wood", 45, "Gather timber for building")
    );

    private final XZHEconomySystem plugin;
    private final File file;
    private final YamlConfiguration data;
    private final NamespacedKey actionKey;
    private final NamespacedKey valueKey;
    private final Random random = new Random();
    private final Map<String, Double> travelRemainders = new HashMap<>();
    // Prevent block-break quests from being farmed by placing and repeatedly breaking the same blocks.
    private final Set<String> playerPlacedBlocks = new HashSet<>();

    private final List<Pack> packs = buildPacks();

    private List<Pack> buildPacks() {
        List<Pack> result = new ArrayList<>(300);
        result.add(new Pack("E008", "Granite Gang", Material.IRON_PICKAXE, 50, 210, "minecraft:story/upgrade_tools", "Getting an Upgrade", 0, List.of(new Objective("break:GRANITE", 200, "Mine 200 Granite"))));
        result.add(new Pack("E005", "Gravel Pit Shift", Material.IRON_PICKAXE, 50, 220, "minecraft:story/mine_stone", "Stone Age", 30, List.of(new Objective("break:GRAVEL", 250, "Mine 250 Gravel"))));
        result.add(new Pack("E006", "Dune Digger", Material.IRON_PICKAXE, 50, 220, "minecraft:story/mine_stone", "Stone Age", 30, List.of(new Objective("break:SAND", 250, "Mine 250 Sand"))));
        result.add(new Pack("E019", "Bamboo Thicket", Material.IRON_PICKAXE, 50, 220, "minecraft:husbandry/plant_seed", "A Seedy Place", 0, List.of(new Objective("break:BAMBOO", 350, "Mine 350 Bamboo"))));
        result.add(new Pack("E021", "Wheat Weekend", Material.WHEAT, 50, 220, "minecraft:husbandry/plant_seed", "A Seedy Place", 0, List.of(new Objective("harvest:WHEAT", 250, "Harvest 250 Wheat"))));
        result.add(new Pack("E022", "Carrot Cartel", Material.WHEAT, 50, 220, "minecraft:husbandry/plant_seed", "A Seedy Place", 0, List.of(new Objective("harvest:CARROT", 250, "Harvest 250 Carrot"))));
        result.add(new Pack("E029", "Sugar Cane Run", Material.IRON_PICKAXE, 50, 220, "minecraft:husbandry/plant_seed", "A Seedy Place", 0, List.of(new Objective("break:SUGAR_CANE", 300, "Mine 300 Sugar Cane"))));
        result.add(new Pack("E030", "Kelp Farm Shift", Material.IRON_PICKAXE, 50, 220, "minecraft:husbandry/plant_seed", "A Seedy Place", 0, List.of(new Objective("break:KELP", 300, "Mine 300 Kelp"))));
        result.add(new Pack("E046", "Torch Bearer", Material.TORCH, 50, 220, "minecraft:husbandry/fishy_business", "Fishy Business", 0, List.of(new Objective("collect:TORCH", 250, "Collect 250 Torch"))));
        result.add(new Pack("E071", "First Mile", Material.COMPASS, 50, 220, "minecraft:story/mine_stone", "Stone Age", 0, List.of(new Objective("travel", 1600, "Travel 1,600 blocks"))));
        result.add(new Pack("E073", "Marathon Stroll", Material.COMPASS, 50, 220, "minecraft:story/iron_tools", "Isn't It Iron Pick", 0, List.of(new Objective("travel-biome:PLAINS", 1200, "Travel 1,200 blocks inside Plains biomes"))));
        result.add(new Pack("E024", "Beetroot Brigade", Material.WHEAT, 50, 230, "minecraft:husbandry/plant_seed", "A Seedy Place", 0, List.of(new Objective("harvest:BEETROOT", 200, "Harvest 200 Beetroot"))));
        result.add(new Pack("E050", "Topsoil Crew", Material.DIRT, 50, 230, "minecraft:husbandry/fishy_business", "Fishy Business", 0, List.of(new Objective("break:DIRT", 350, "Mine 350 Dirt"))));
        result.add(new Pack("E075", "Woodland Wander", Material.COMPASS, 50, 230, "minecraft:story/iron_tools", "Isn't It Iron Pick", 0, List.of(new Objective("travel-biome:FOREST", 1300, "Travel 1,300 blocks inside Forest biomes"))));
        result.add(new Pack("E078", "Frozen Frontier", Material.COMPASS, 50, 230, "minecraft:story/iron_tools", "Isn't It Iron Pick", 0, List.of(new Objective("travel-biome:SNOWY_PLAINS", 1300, "Travel 1,300 blocks inside Snowy Plains biomes"))));
        result.add(new Pack("E079", "Sandy Safari", Material.COMPASS, 50, 230, "minecraft:story/iron_tools", "Isn't It Iron Pick", 0, List.of(new Objective("travel-biome:DESERT", 1400, "Travel 1,400 blocks inside Desert biomes"))));
        result.add(new Pack("E001", "Stonecutter's Warm-Up", Material.IRON_PICKAXE, 50, 240, "minecraft:story/mine_stone", "Stone Age", 30, List.of(new Objective("break:STONE", 200, "Mine 200 Stone"))));
        result.add(new Pack("E076", "Taiga Trail", Material.COMPASS, 50, 240, "minecraft:story/iron_tools", "Isn't It Iron Pick", 30, List.of(new Objective("travel-biome:TAIGA", 1300, "Travel 1,300 blocks inside Taiga biomes"))));
        result.add(new Pack("E012", "Birch Brigade", Material.IRON_PICKAXE, 60, 250, "minecraft:story/upgrade_tools", "Getting an Upgrade", 0, List.of(new Objective("break:BIRCH_LOG", 200, "Mine 200 Birch Log"))));
        result.add(new Pack("E013", "Spruce Supply", Material.IRON_PICKAXE, 60, 250, "minecraft:story/upgrade_tools", "Getting an Upgrade", 0, List.of(new Objective("break:SPRUCE_LOG", 200, "Mine 200 Spruce Log"))));
        result.add(new Pack("E015", "Jungle Chopper", Material.IRON_PICKAXE, 60, 250, "minecraft:story/iron_tools", "Isn't It Iron Pick", 0, List.of(new Objective("break:JUNGLE_LOG", 200, "Mine 200 Jungle Log"))));
        result.add(new Pack("E016", "Savanna Sawyer", Material.IRON_PICKAXE, 60, 250, "minecraft:story/iron_tools", "Isn't It Iron Pick", 0, List.of(new Objective("break:ACACIA_LOG", 200, "Mine 200 Acacia Log"))));
        result.add(new Pack("E011", "Lumberjack Basics", Material.IRON_PICKAXE, 60, 260, "minecraft:story/mine_stone", "Stone Age", 35, List.of(new Objective("break:OAK_LOG", 200, "Mine 200 Oak Log"))));
        result.add(new Pack("E027", "Sweet Berry Picker", Material.WHEAT, 60, 260, "minecraft:husbandry/plant_seed", "A Seedy Place", 0, List.of(new Objective("harvest:SWEET_BERRIES", 200, "Harvest 200 Sweet Berries"))));
        result.add(new Pack("E063", "Glassblower", Material.FURNACE, 60, 260, "minecraft:story/smelt_iron", "Acquire Hardware", 35, List.of(new Objective("smelt:GLASS", 200, "Smelt 200 Glass"))));
        result.add(new Pack("E065", "Stone Mason", Material.FURNACE, 60, 260, "minecraft:story/mine_stone", "Stone Age", 35, List.of(new Objective("smelt:STONE", 200, "Smelt 200 Stone"))));
        result.add(new Pack("E066", "Charcoal Burner", Material.FURNACE, 60, 260, "minecraft:story/mine_stone", "Stone Age", 0, List.of(new Objective("smelt:CHARCOAL", 200, "Smelt 200 Charcoal"))));
        result.add(new Pack("E067", "Cookout", Material.FURNACE, 60, 260, "minecraft:story/smelt_iron", "Acquire Hardware", 0, List.of(new Objective("smelt:COOKED_BEEF", 200, "Smelt 200 Cooked Beef"))));
        result.add(new Pack("E068", "Seafood Platter", Material.FURNACE, 60, 260, "minecraft:husbandry/fishy_business", "Fishy Business", 0, List.of(new Objective("smelt:COOKED_COD", 200, "Smelt 200 Cooked Cod"))));
        result.add(new Pack("E084", "Roast Chicken Roster", Material.FURNACE, 60, 260, "minecraft:husbandry/fishy_business", "Fishy Business", 0, List.of(new Objective("smelt:COOKED_CHICKEN", 200, "Smelt 200 Cooked Chicken"))));
        result.add(new Pack("E085", "Potato Oven", Material.FURNACE, 60, 260, "minecraft:story/iron_tools", "Isn't It Iron Pick", 0, List.of(new Objective("smelt:BAKED_POTATO", 200, "Smelt 200 Baked Potato"))));
        result.add(new Pack("E081", "Village Pantry", Material.WHEAT, 60, 270, "minecraft:adventure/trade", "What a Deal!", 40, List.of(new Objective("harvest:WHEAT", 200, "Harvest 200 Wheat"), new Objective("harvest:CARROT", 200, "Harvest 200 Carrot"))));
        result.add(new Pack("E014", "Dark Forest Cutter", Material.IRON_PICKAXE, 70, 280, "minecraft:story/iron_tools", "Isn't It Iron Pick", 0, List.of(new Objective("break:DARK_OAK_LOG", 190, "Mine 190 Dark Oak Log"))));
        result.add(new Pack("E017", "Cherry Grove Grind", Material.IRON_PICKAXE, 70, 280, "minecraft:husbandry/plant_seed", "A Seedy Place", 0, List.of(new Objective("break:CHERRY_LOG", 200, "Mine 200 Cherry Log"))));
        result.add(new Pack("E018", "Mangrove Marsh Crew", Material.IRON_PICKAXE, 70, 280, "minecraft:story/iron_tools", "Isn't It Iron Pick", 0, List.of(new Objective("break:MANGROVE_LOG", 200, "Mine 200 Mangrove Log"))));
        result.add(new Pack("E020", "Pale Garden Harvest", Material.IRON_PICKAXE, 70, 280, "minecraft:story/iron_tools", "Isn't It Iron Pick", 0, List.of(new Objective("break:PALE_OAK_LOG", 200, "Mine 200 Pale Oak Log"))));
        result.add(new Pack("E002", "Coal Seam Crew", Material.IRON_PICKAXE, 70, 290, "minecraft:story/upgrade_tools", "Getting an Upgrade", 0, List.of(new Objective("break:COAL_ORE", 180, "Mine 180 Coal Ore"))));
        result.add(new Pack("E007", "Clay Banks", Material.IRON_PICKAXE, 70, 290, "minecraft:story/upgrade_tools", "Getting an Upgrade", 0, List.of(new Objective("break:CLAY", 180, "Mine 180 Clay"))));
        result.add(new Pack("E023", "Potato Patch", Material.WHEAT, 70, 290, "minecraft:husbandry/plant_seed", "A Seedy Place", 0, List.of(new Objective("harvest:POTATO", 190, "Harvest 190 Potato"))));
        result.add(new Pack("E028", "Cocoa Grove", Material.WHEAT, 70, 290, "minecraft:husbandry/plant_seed", "A Seedy Place", 0, List.of(new Objective("harvest:COCOA_BEANS", 190, "Harvest 190 Cocoa Beans"))));
        result.add(new Pack("E064", "Brickyard", Material.FURNACE, 70, 290, "minecraft:story/smelt_iron", "Acquire Hardware", 0, List.of(new Objective("smelt:BRICK", 190, "Smelt 190 Brick"))));
        result.add(new Pack("E069", "Terracotta Kiln", Material.FURNACE, 70, 300, "minecraft:story/smelt_iron", "Acquire Hardware", 0, List.of(new Objective("smelt:TERRACOTTA", 200, "Smelt 200 Terracotta"))));
        result.add(new Pack("E093", "Cookie Jar", Material.CRAFTING_TABLE, 70, 310, "minecraft:husbandry/plant_seed", "A Seedy Place", 50, List.of(new Objective("collect:COOKIE", 200, "Collect 200 Cookie"))));
        result.add(new Pack("E003", "Copper Crawl", Material.IRON_PICKAXE, 80, 310, "minecraft:story/upgrade_tools", "Getting an Upgrade", 0, List.of(new Objective("break:COPPER_ORE", 150, "Mine 150 Copper Ore"))));
        result.add(new Pack("E025", "Melon Madness", Material.WHEAT, 80, 310, "minecraft:husbandry/plant_seed", "A Seedy Place", 0, List.of(new Objective("harvest:MELON_SLICE", 150, "Harvest 150 Melon Slice"))));
        result.add(new Pack("E026", "Pumpkin Parade", Material.WHEAT, 80, 320, "minecraft:husbandry/plant_seed", "A Seedy Place", 0, List.of(new Objective("harvest:PUMPKIN", 160, "Harvest 160 Pumpkin"))));
        result.add(new Pack("E062", "Copper Smelter", Material.FURNACE, 80, 320, "minecraft:story/smelt_iron", "Acquire Hardware", 0, List.of(new Objective("smelt:COPPER_INGOT", 160, "Smelt 160 Copper Ingot"))));
        result.add(new Pack("E070", "Baker's Dozen", Material.CRAFTING_TABLE, 80, 320, "minecraft:husbandry/plant_seed", "A Seedy Place", 0, List.of(new Objective("collect:BREAD", 160, "Collect 160 Bread"))));
        result.add(new Pack("E009", "Redstone Rush", Material.IRON_PICKAXE, 90, 340, "minecraft:story/smelt_iron", "Acquire Hardware", 55, List.of(new Objective("break:REDSTONE_ORE", 130, "Mine 130 Redstone Ore"))));
        result.add(new Pack("E091", "Pie Maker", Material.CRAFTING_TABLE, 90, 340, "minecraft:husbandry/plant_seed", "A Seedy Place", 60, List.of(new Objective("collect:PUMPKIN_PIE", 140, "Collect 140 Pumpkin Pie"))));
        result.add(new Pack("E061", "Furnace Apprentice", Material.FURNACE, 100, 340, "minecraft:story/smelt_iron", "Acquire Hardware", 0, List.of(new Objective("smelt:IRON_INGOT", 110, "Smelt 110 Iron Ingot"))));
        result.add(new Pack("E094", "Lantern Lighter", Material.CRAFTING_TABLE, 100, 340, "minecraft:story/smelt_iron", "Acquire Hardware", 0, List.of(new Objective("collect:LANTERN", 110, "Collect 110 Lantern"))));
        result.add(new Pack("E098", "Forge Supplies", Material.IRON_ORE, 100, 350, "minecraft:story/enchant_item", "Enchanter", 0, List.of(new Objective("break:IRON_ORE", 60, "Mine 60 Iron Ore"), new Objective("break:COAL_ORE", 110, "Mine 110 Coal Ore"))));
        result.add(new Pack("E004", "Iron Vein Run", Material.IRON_PICKAXE, 100, 360, "minecraft:story/smelt_iron", "Acquire Hardware", 55, List.of(new Objective("break:IRON_ORE", 110, "Mine 110 Iron Ore"))));
        result.add(new Pack("E010", "Lapis Lodestar", Material.IRON_PICKAXE, 110, 370, "minecraft:story/iron_tools", "Isn't It Iron Pick", 0, List.of(new Objective("break:LAPIS_ORE", 90, "Mine 90 Lapis Ore"))));
        result.add(new Pack("E095", "Shield Wall", Material.CRAFTING_TABLE, 110, 370, "minecraft:story/iron_tools", "Isn't It Iron Pick", 0, List.of(new Objective("collect:SHIELD", 90, "Collect 90 Shield"))));
        result.add(new Pack("E096", "Bucket Brigade", Material.CRAFTING_TABLE, 110, 370, "minecraft:story/iron_tools", "Isn't It Iron Pick", 0, List.of(new Objective("collect:BUCKET", 90, "Collect 90 Bucket"))));
        result.add(new Pack("E048", "Chest Crafter", Material.CHEST, 110, 380, "minecraft:husbandry/fishy_business", "Fishy Business", 60, List.of(new Objective("collect:CHEST", 90, "Collect 90 Chest"))));
        result.add(new Pack("E097", "Library Builder", Material.CRAFTING_TABLE, 120, 380, "minecraft:story/iron_tools", "Isn't It Iron Pick", 0, List.of(new Objective("collect:BOOKSHELF", 60, "Collect 60 Bookshelf"))));
        result.add(new Pack("E051", "Zombie Sweep", Material.ROTTEN_FLESH, 120, 390, "minecraft:adventure/kill_a_mob", "Monster Hunter", 75, List.of(new Objective("kill:ZOMBIE", 50, "Defeat 50 Zombie"))));
        result.add(new Pack("E054", "Spider Season", Material.ROTTEN_FLESH, 120, 390, "minecraft:adventure/kill_a_mob", "Monster Hunter", 75, List.of(new Objective("kill:SPIDER", 50, "Defeat 50 Spider"))));
        result.add(new Pack("E041", "First Catch Club", Material.FISHING_ROD, 130, 400, "minecraft:husbandry/fishy_business", "Fishy Business", 0, List.of(new Objective("fish:COD", 45, "Catch 45 Cod"))));
        result.add(new Pack("E092", "Cake Day", Material.CRAFTING_TABLE, 130, 410, "minecraft:husbandry/plant_seed", "A Seedy Place", 0, List.of(new Objective("collect:CAKE", 50, "Collect 50 Cake"))));
        result.add(new Pack("E100", "Jack of All Trades", Material.IRON_PICKAXE, 130, 410, "minecraft:story/iron_tools", "Isn't It Iron Pick", 0, List.of(new Objective("break:IRON_ORE", 35, "Mine 35 Iron Ore"), new Objective("kill:ZOMBIE", 15, "Defeat 15 Zombie"), new Objective("fish:COD", 12, "Catch 12 Cod"), new Objective("harvest:WHEAT", 170, "Harvest 170 Wheat"))));
        result.add(new Pack("E035", "Herd Builder", Material.WHEAT, 130, 420, "minecraft:husbandry/breed_an_animal", "The Parrots and the Bats", 0, List.of(new Objective("breed:ANY", 45, "Breed 45 Animals"))));
        result.add(new Pack("E052", "Skeleton Shooting Gallery", Material.ROTTEN_FLESH, 130, 420, "minecraft:adventure/kill_a_mob", "Monster Hunter", 85, List.of(new Objective("kill:SKELETON", 45, "Defeat 45 Skeleton"))));
        result.add(new Pack("E087", "Husk Dune Patrol", Material.ROTTEN_FLESH, 130, 420, "minecraft:story/iron_tools", "Isn't It Iron Pick", 0, List.of(new Objective("kill:HUSK", 45, "Defeat 45 Husk"))));
        result.add(new Pack("E083", "Mineshaft Scavenger", Material.ROTTEN_FLESH, 130, 430, "minecraft:story/iron_tools", "Isn't It Iron Pick", 90, List.of(new Objective("kill:CAVE_SPIDER", 45, "Defeat 45 Cave Spider"))));
        result.add(new Pack("E036", "Rabbit Ranch", Material.WHEAT, 140, 360, "minecraft:husbandry/breed_an_animal", "The Parrots and the Bats", 0, List.of(new Objective("breed:RABBIT", 16, "Breed 16 Rabbits"))));
        result.add(new Pack("E031", "Cow Whisperer", Material.WHEAT, 140, 410, "minecraft:husbandry/breed_an_animal", "The Parrots and the Bats", 0, List.of(new Objective("breed:COW", 30, "Breed 30 Cows"))));
        result.add(new Pack("E032", "Shepherd's Pride", Material.WHEAT, 140, 410, "minecraft:husbandry/breed_an_animal", "The Parrots and the Bats", 0, List.of(new Objective("breed:SHEEP", 30, "Breed 30 Sheep"))));
        result.add(new Pack("E033", "Piggy Bank", Material.WHEAT, 140, 410, "minecraft:husbandry/breed_an_animal", "The Parrots and the Bats", 0, List.of(new Objective("breed:PIG", 30, "Breed 30 Pigs"))));
        result.add(new Pack("E034", "Coop Commander", Material.WHEAT, 140, 410, "minecraft:husbandry/breed_an_animal", "The Parrots and the Bats", 0, List.of(new Objective("breed:CHICKEN", 30, "Breed 30 Chickens"))));
        result.add(new Pack("E047", "Night Patrol", Material.ROTTEN_FLESH, 140, 430, "minecraft:husbandry/fishy_business", "Fishy Business", 0, List.of(new Objective("kill:ZOMBIE", 30, "Defeat 30 Zombie"), new Objective("kill:SKELETON", 25, "Defeat 25 Skeleton"))));
        result.add(new Pack("E053", "Creeper Cleanup", Material.ROTTEN_FLESH, 140, 440, "minecraft:adventure/kill_a_mob", "Monster Hunter", 90, List.of(new Objective("kill:CREEPER", 40, "Defeat 40 Creeper"))));
        result.add(new Pack("E059", "Drowned Dive", Material.ROTTEN_FLESH, 140, 440, "minecraft:adventure/kill_a_mob", "Monster Hunter", 85, List.of(new Objective("kill:DROWNED", 35, "Defeat 35 Drowned"))));
        result.add(new Pack("E082", "Dungeon Delver", Material.ROTTEN_FLESH, 140, 440, "minecraft:story/iron_tools", "Isn't It Iron Pick", 90, List.of(new Objective("kill:SKELETON", 25, "Defeat 25 Skeleton"), new Objective("kill:SPIDER", 30, "Defeat 30 Spider"))));
        result.add(new Pack("E089", "Cavern Cleanup", Material.ROTTEN_FLESH, 140, 440, "minecraft:story/iron_tools", "Isn't It Iron Pick", 0, List.of(new Objective("kill:SKELETON", 25, "Defeat 25 Skeleton"), new Objective("kill:CAVE_SPIDER", 25, "Defeat 25 Cave Spider"))));
        result.add(new Pack("E088", "Mob Trio", Material.ROTTEN_FLESH, 150, 450, "minecraft:story/iron_tools", "Isn't It Iron Pick", 0, List.of(new Objective("kill:ZOMBIE", 22, "Defeat 22 Zombie"), new Objective("kill:SPIDER", 22, "Defeat 22 Spider"), new Objective("kill:SKELETON", 17, "Defeat 17 Skeleton"))));
        result.add(new Pack("E055", "Night Shift", Material.ROTTEN_FLESH, 150, 460, "minecraft:adventure/kill_a_mob", "Monster Hunter", 95, List.of(new Objective("kill:ZOMBIE", 35, "Defeat 35 Zombie"), new Objective("kill:CREEPER", 20, "Defeat 20 Creeper"))));
        result.add(new Pack("E058", "Slime Squisher", Material.ROTTEN_FLESH, 150, 460, "minecraft:adventure/kill_a_mob", "Monster Hunter", 0, List.of(new Objective("kill:SLIME", 35, "Defeat 35 Slime"))));
        result.add(new Pack("E060", "Desert & Snow Patrol", Material.ROTTEN_FLESH, 150, 460, "minecraft:story/iron_tools", "Isn't It Iron Pick", 0, List.of(new Objective("kill:HUSK", 25, "Defeat 25 Husk"), new Objective("kill:STRAY", 20, "Defeat 20 Stray"))));
        result.add(new Pack("E056", "Enderman Encounter", Material.ROTTEN_FLESH, 160, 460, "minecraft:story/iron_tools", "Isn't It Iron Pick", 0, List.of(new Objective("kill:ENDERMAN", 25, "Defeat 25 Enderman"))));
        result.add(new Pack("E043", "Salmon Run", Material.FISHING_ROD, 160, 470, "minecraft:husbandry/fishy_business", "Fishy Business", 0, List.of(new Objective("fish:SALMON", 25, "Catch 25 Salmon"))));
        result.add(new Pack("E049", "Fisherman's Mix", Material.FISHING_ROD, 160, 470, "minecraft:husbandry/fishy_business", "Fishy Business", 0, List.of(new Objective("fish:COD", 30, "Catch 30 Cod"), new Objective("fish:SALMON", 12, "Catch 12 Salmon"))));
        result.add(new Pack("E086", "Portal Obsidian Salvage", Material.IRON_PICKAXE, 160, 480, "minecraft:story/form_obsidian", "Ice Bucket Challenge", 0, List.of(new Objective("break:OBSIDIAN", 25, "Mine 25 Obsidian"))));
        result.add(new Pack("E038", "Wolf Pack", Material.WHEAT, 170, 380, "minecraft:husbandry/tame_an_animal", "Best Friends Forever", 0, List.of(new Objective("tame:WOLF", 6, "Tame 6 Wolves"))));
        result.add(new Pack("E057", "Witch Hunt", Material.ROTTEN_FLESH, 170, 490, "minecraft:adventure/kill_a_mob", "Monster Hunter", 0, List.of(new Objective("kill:WITCH", 17, "Defeat 17 Witch"))));
        result.add(new Pack("E072", "Long Walk", Material.COMPASS, 180, 390, "minecraft:story/upgrade_tools", "Getting an Upgrade", 0, List.of(new Objective("visit-biome:FOREST", 1, "Enter the Forest biome"))));
        result.add(new Pack("E090", "Mixed Catch", Material.FISHING_ROD, 180, 510, "minecraft:husbandry/fishy_business", "Fishy Business", 0, List.of(new Objective("fish:COD", 30, "Catch 30 Cod"), new Objective("fish:PUFFERFISH", 7, "Catch 7 Pufferfish"))));
        result.add(new Pack("E044", "Pufferfish Pals", Material.FISHING_ROD, 180, 520, "minecraft:husbandry/fishy_business", "Fishy Business", 0, List.of(new Objective("fish:PUFFERFISH", 15, "Catch 15 Pufferfish"))));
        result.add(new Pack("E037", "Cat Companion", Material.WHEAT, 190, 410, "minecraft:husbandry/tame_an_animal", "Best Friends Forever", 0, List.of(new Objective("tame:CAT", 4, "Tame 4 Cats"))));
        result.add(new Pack("E040", "Turtle Beach", Material.WHEAT, 190, 440, "minecraft:husbandry/breed_an_animal", "The Parrots and the Bats", 0, List.of(new Objective("breed:TURTLE", 6, "Breed 6 Turtles"))));
        result.add(new Pack("E042", "Reef Sampler", Material.FISHING_ROD, 190, 530, "minecraft:husbandry/fishy_business", "Fishy Business", 0, List.of(new Objective("fish:SALMON", 15, "Catch 15 Salmon"), new Objective("fish:PUFFERFISH", 7, "Catch 7 Pufferfish"))));
        result.add(new Pack("E099", "Armorer's Order", Material.CRAFTING_TABLE, 200, 420, "minecraft:story/obtain_armor", "Suit Up", 0, List.of(new Objective("collect:IRON_HELMET", 1, "Collect 1 Iron Helmet"), new Objective("collect:IRON_CHESTPLATE", 1, "Collect 1 Iron Chestplate"), new Objective("collect:IRON_LEGGINGS", 1, "Collect 1 Iron Leggings"), new Objective("collect:IRON_BOOTS", 1, "Collect 1 Iron Boots"))));
        result.add(new Pack("E039", "Parrot Pals", Material.WHEAT, 200, 430, "minecraft:husbandry/tame_an_animal", "Best Friends Forever", 0, List.of(new Objective("tame:PARROT", 3, "Tame 3 Parrots"))));
        result.add(new Pack("E080", "Jungle Expedition", Material.COMPASS, 230, 450, "minecraft:story/iron_tools", "Isn't It Iron Pick", 0, List.of(new Objective("visit-biome:JUNGLE", 1, "Enter the Jungle biome"))));
        result.add(new Pack("E077", "Cave Cartographer", Material.COMPASS, 230, 460, "minecraft:story/iron_tools", "Isn't It Iron Pick", 0, List.of(new Objective("visit-biome:LUSH_CAVES", 1, "Enter a Lush Caves biome"))));
        result.add(new Pack("E074", "Biome Hopper", Material.COMPASS, 230, 480, "minecraft:story/upgrade_tools", "Getting an Upgrade", 0, List.of(new Objective("visit-biome:DESERT", 1, "Enter the Desert biome"), new Objective("visit-biome:SAVANNA", 1, "Enter the Savanna biome"))));
        result.add(new Pack("E045", "Tropical Treasure", Material.FISHING_ROD, 250, 600, "minecraft:husbandry/tactical_fishing", "Tactical Fishing", 0, List.of(new Objective("fish:TROPICAL_FISH", 2, "Catch 2 Tropical Fish"))));
        result.add(new Pack("H049", "Ocean Explorer", Material.COMPASS, 300, 650, "minecraft:story/iron_tools", "Isn't It Iron Pick", 0, List.of(new Objective("travel-biome:OCEAN", 4000, "Travel 4,000 blocks inside Ocean biomes"))));
        result.add(new Pack("H059", "Sugar Cane Syndicate", Material.IRON_PICKAXE, 300, 660, "minecraft:husbandry/plant_seed", "A Seedy Place", 0, List.of(new Objective("break:SUGAR_CANE", 700, "Mine 700 Sugar Cane"))));
        result.add(new Pack("H015", "Basalt Delta Crew", Material.IRON_PICKAXE, 300, 690, "minecraft:nether/root", "Nether", 50, List.of(new Objective("break:BASALT", 500, "Mine 500 Basalt"))));
        result.add(new Pack("H013", "Soul Sand Scout", Material.IRON_PICKAXE, 300, 710, "minecraft:nether/root", "Nether", 55, List.of(new Objective("break:SOUL_SAND", 400, "Mine 400 Soul Sand"))));
        result.add(new Pack("H055", "Beetroot Brigade II", Material.WHEAT, 300, 710, "minecraft:husbandry/plant_seed", "A Seedy Place", 0, List.of(new Objective("harvest:BEETROOT", 450, "Harvest 450 Beetroot"))));
        result.add(new Pack("H078", "Nether Grand Tour", Material.COMPASS, 300, 710, "minecraft:nether/ride_strider", "This Boat Has Legs", 0, List.of(new Objective("travel-world:NETHER", 3200, "Travel 3,200 blocks in the Nether"))));
        result.add(new Pack("H051", "Wheat Baron", Material.WHEAT, 300, 720, "minecraft:husbandry/plant_seed", "A Seedy Place", 55, List.of(new Objective("harvest:WHEAT", 550, "Harvest 550 Wheat"))));
        result.add(new Pack("H052", "Carrot Tycoon", Material.WHEAT, 300, 720, "minecraft:husbandry/plant_seed", "A Seedy Place", 55, List.of(new Objective("harvest:CARROT", 550, "Harvest 550 Carrot"))));
        result.add(new Pack("H071", "Cross-Country Hike", Material.COMPASS, 300, 740, "minecraft:story/iron_tools", "Isn't It Iron Pick", 60, List.of(new Objective("travel", 3500, "Travel 3,500 blocks"))));
        result.add(new Pack("H073", "Mountain Pass", Material.COMPASS, 300, 750, "minecraft:story/iron_tools", "Isn't It Iron Pick", 60, List.of(new Objective("travel-biome:WINDSWEPT_HILLS", 2700, "Travel 2,700 blocks inside Windswept Hills biomes"))));
        result.add(new Pack("H077", "Savanna Safari", Material.COMPASS, 300, 760, "minecraft:story/iron_tools", "Isn't It Iron Pick", 65, List.of(new Objective("travel-biome:SAVANNA", 2800, "Travel 2,800 blocks inside Savanna biomes"))));
        result.add(new Pack("H079", "Jungle Trek", Material.COMPASS, 300, 770, "minecraft:story/shiny_gear", "Cover Me with Diamonds", 65, List.of(new Objective("travel-biome:JUNGLE", 2900, "Travel 2,900 blocks inside Jungle biomes"))));
        result.add(new Pack("H004", "Deepslate Delver", Material.IRON_PICKAXE, 310, 800, "minecraft:story/mine_diamond", "Diamonds!", 70, List.of(new Objective("break:DEEPSLATE", 400, "Mine 400 Deepslate"))));
        result.add(new Pack("H065", "Glasswork Empire", Material.FURNACE, 310, 800, "minecraft:story/smelt_iron", "Acquire Hardware", 70, List.of(new Objective("smelt:GLASS", 400, "Smelt 400 Glass"))));
        result.add(new Pack("H066", "Steakhouse", Material.FURNACE, 320, 790, "minecraft:husbandry/breed_an_animal", "The Parrots and the Bats", 0, List.of(new Objective("smelt:COOKED_BEEF", 400, "Smelt 400 Cooked Beef"))));
        result.add(new Pack("H067", "Smokehouse", Material.FURNACE, 320, 790, "minecraft:husbandry/breed_an_animal", "The Parrots and the Bats", 0, List.of(new Objective("smelt:COOKED_PORKCHOP", 400, "Smelt 400 Cooked Porkchop"))));
        result.add(new Pack("H018", "Wart Farmer", Material.WHEAT, 320, 830, "minecraft:nether/root", "Nether", 0, List.of(new Objective("harvest:NETHER_WART", 400, "Harvest 400 Nether Wart"))));
        result.add(new Pack("H056", "Sweet Berry Syndicate", Material.WHEAT, 320, 830, "minecraft:husbandry/plant_seed", "A Seedy Place", 0, List.of(new Objective("harvest:SWEET_BERRIES", 400, "Harvest 400 Sweet Berries"))));
        result.add(new Pack("H016", "Crimson Logger", Material.IRON_PICKAXE, 320, 840, "minecraft:nether/root", "Nether", 70, List.of(new Objective("break:CRIMSON_STEM", 350, "Mine 350 Crimson Stem"))));
        result.add(new Pack("H017", "Warped Logger", Material.IRON_PICKAXE, 320, 890, "minecraft:nether/root", "Nether", 80, List.of(new Objective("break:WARPED_STEM", 400, "Mine 400 Warped Stem"))));
        result.add(new Pack("H068", "Charcoal Baron", Material.FURNACE, 320, 890, "minecraft:story/upgrade_tools", "Getting an Upgrade", 80, List.of(new Objective("smelt:CHARCOAL", 400, "Smelt 400 Charcoal"))));
        result.add(new Pack("H057", "Glow Berry Gatherer", Material.WHEAT, 340, 910, "minecraft:story/iron_tools", "Isn't It Iron Pick", 0, List.of(new Objective("harvest:GLOW_BERRIES", 350, "Harvest 350 Glow Berries"))));
        result.add(new Pack("H058", "Cocoa Cartel", Material.WHEAT, 340, 910, "minecraft:husbandry/plant_seed", "A Seedy Place", 0, List.of(new Objective("harvest:COCOA_BEANS", 350, "Harvest 350 Cocoa Beans"))));
        result.add(new Pack("H053", "Potato Mogul", Material.WHEAT, 340, 970, "minecraft:husbandry/plant_seed", "A Seedy Place", 90, List.of(new Objective("harvest:POTATO", 350, "Harvest 350 Potato"))));
        result.add(new Pack("H069", "Kiln Master", Material.FURNACE, 340, 970, "minecraft:story/iron_tools", "Isn't It Iron Pick", 90, List.of(new Objective("smelt:TERRACOTTA", 350, "Smelt 350 Terracotta"))));
        result.add(new Pack("H009", "Mixed Ore Blitz", Material.IRON_PICKAXE, 360, 930, "minecraft:story/mine_diamond", "Diamonds!", 0, List.of(new Objective("break:ANY_ORE", 250, "Mine 250 Any Ore"))));
        result.add(new Pack("H089", "Village Heros Haul", Material.WHEAT, 360, 980, "minecraft:adventure/trade", "What a Deal!", 0, List.of(new Objective("harvest:WHEAT", 300, "Harvest 300 Wheat"), new Objective("harvest:CARROT", 300, "Harvest 300 Carrot"), new Objective("harvest:POTATO", 130, "Harvest 130 Potato"))));
        result.add(new Pack("H007", "Copper Cavern Crew", Material.IRON_PICKAXE, 360, 990, "minecraft:story/iron_tools", "Isn't It Iron Pick", 85, List.of(new Objective("break:COPPER_ORE", 250, "Mine 250 Copper Ore"))));
        result.add(new Pack("H012", "Glowstone Gatherer", Material.IRON_PICKAXE, 360, 990, "minecraft:nether/root", "Nether", 85, List.of(new Objective("break:GLOWSTONE", 250, "Mine 250 Glowstone"))));
        result.add(new Pack("H063", "Copper Furnaces", Material.FURNACE, 360, 1070, "minecraft:story/iron_tools", "Isn't It Iron Pick", 100, List.of(new Objective("smelt:COPPER_INGOT", 300, "Smelt 300 Copper Ingot"))));
        result.add(new Pack("H006", "Redstone Royalty", Material.IRON_PICKAXE, 370, 1040, "minecraft:story/mine_diamond", "Diamonds!", 0, List.of(new Objective("break:REDSTONE_ORE", 250, "Mine 250 Redstone Ore"))));
        result.add(new Pack("H054", "Melon & Pumpkin Patch", Material.WHEAT, 370, 1040, "minecraft:husbandry/plant_seed", "A Seedy Place", 0, List.of(new Objective("harvest:MELON_SLICE", 150, "Harvest 150 Melon Slice"), new Objective("harvest:PUMPKIN", 150, "Harvest 150 Pumpkin"))));
        result.add(new Pack("H011", "Quartz Quarry", Material.IRON_PICKAXE, 370, 1100, "minecraft:nether/root", "Nether", 100, List.of(new Objective("break:NETHER_QUARTZ_ORE", 250, "Mine 250 Nether Quartz Ore"))));
        result.add(new Pack("H014", "Nether Gold Rush", Material.IRON_PICKAXE, 380, 1060, "minecraft:nether/root", "Nether", 0, List.of(new Objective("break:NETHER_GOLD_ORE", 200, "Mine 200 Nether Gold Ore"))));
        result.add(new Pack("H061", "Iron Foundry", Material.FURNACE, 380, 1120, "minecraft:story/iron_tools", "Isn't It Iron Pick", 100, List.of(new Objective("smelt:IRON_INGOT", 200, "Smelt 200 Iron Ingot"))));
        result.add(new Pack("H005", "Lapis Lazuli Lodes", Material.IRON_PICKAXE, 400, 1160, "minecraft:story/enchant_item", "Enchanter", 100, List.of(new Objective("break:LAPIS_ORE", 150, "Mine 150 Lapis Ore"))));
        result.add(new Pack("H042", "Prismarine Pillager", Material.IRON_PICKAXE, 400, 1200, "minecraft:story/mine_diamond", "Diamonds!", 110, List.of(new Objective("break:PRISMARINE", 160, "Mine 160 Prismarine"))));
        result.add(new Pack("H003", "Gold Vein Gambit", Material.IRON_PICKAXE, 440, 1180, "minecraft:story/smelt_iron", "Acquire Hardware", 0, List.of(new Objective("break:GOLD_ORE", 80, "Mine 80 Gold Ore"))));
        result.add(new Pack("H045", "Deep Sea Haul", Material.FISHING_ROD, 440, 1220, "minecraft:husbandry/fishy_business", "Fishy Business", 0, List.of(new Objective("fish:COD", 80, "Catch 80 Cod"))));
        result.add(new Pack("H062", "Gold Foundry", Material.FURNACE, 440, 1250, "minecraft:story/mine_diamond", "Diamonds!", 0, List.of(new Objective("smelt:GOLD_INGOT", 90, "Smelt 90 Gold Ingot"))));
        result.add(new Pack("H070", "Smelter's Gauntlet", Material.FURNACE, 450, 1280, "minecraft:story/mine_diamond", "Diamonds!", 0, List.of(new Objective("smelt:IRON_INGOT", 80, "Smelt 80 Iron Ingot"), new Objective("smelt:GOLD_INGOT", 30, "Smelt 30 Gold Ingot"), new Objective("smelt:COPPER_INGOT", 110, "Smelt 110 Copper Ingot"))));
        result.add(new Pack("H031", "Undead Annihilator", Material.NETHER_STAR, 450, 1290, "minecraft:adventure/kill_a_mob", "Monster Hunter", 135, List.of(new Objective("kill:ZOMBIE", 70, "Defeat 70 Zombie"))));
        result.add(new Pack("H033", "Arachnophobia", Material.NETHER_STAR, 450, 1290, "minecraft:adventure/kill_a_mob", "Monster Hunter", 135, List.of(new Objective("kill:SPIDER", 70, "Defeat 70 Spider"))));
        result.add(new Pack("H032", "Bone Collector", Material.NETHER_STAR, 470, 1370, "minecraft:adventure/shoot_arrow", "Take Aim", 145, List.of(new Objective("kill:SKELETON", 60, "Defeat 60 Skeleton"))));
        result.add(new Pack("H030", "Zombified Piglin Purge", Material.NETHER_STAR, 470, 1410, "minecraft:nether/root", "Nether", 150, List.of(new Objective("kill:ZOMBIFIED_PIGLIN", 60, "Defeat 60 Zombified Piglin"))));
        result.add(new Pack("H081", "Husk Hunt Squad", Material.NETHER_STAR, 470, 1410, "minecraft:story/iron_tools", "Isn't It Iron Pick", 150, List.of(new Objective("kill:HUSK", 60, "Defeat 60 Husk"))));
        result.add(new Pack("H086", "Mineshaft Marathon", Material.NETHER_STAR, 470, 1410, "minecraft:story/shiny_gear", "Cover Me with Diamonds", 150, List.of(new Objective("kill:CAVE_SPIDER", 60, "Defeat 60 Cave Spider"))));
        result.add(new Pack("H027", "Magma Cube Mash", Material.NETHER_STAR, 480, 1400, "minecraft:nether/obtain_blaze_rod", "Into Fire", 0, List.of(new Objective("kill:MAGMA_CUBE", 50, "Defeat 50 Magma Cube"))));
        result.add(new Pack("H034", "Creeper Clearance", Material.NETHER_STAR, 480, 1400, "minecraft:adventure/shoot_arrow", "Take Aim", 145, List.of(new Objective("kill:CREEPER", 50, "Defeat 50 Creeper"))));
        result.add(new Pack("H044", "Drowned Hunter", Material.NETHER_STAR, 480, 1400, "minecraft:husbandry/fishy_business", "Fishy Business", 0, List.of(new Objective("kill:DROWNED", 50, "Defeat 50 Drowned"))));
        result.add(new Pack("H082", "Web Cutters", Material.ROTTEN_FLESH, 480, 1430, "minecraft:story/iron_tools", "Isn't It Iron Pick", 150, List.of(new Objective("kill:SPIDER", 40, "Defeat 40 Spider"), new Objective("kill:CAVE_SPIDER", 30, "Defeat 30 Cave Spider"))));
        result.add(new Pack("H021", "Blaze Brigade", Material.NETHER_STAR, 480, 1460, "minecraft:nether/obtain_blaze_rod", "Into Fire", 160, List.of(new Objective("kill:BLAZE", 50, "Defeat 50 Blaze"))));
        result.add(new Pack("H085", "Frostbite Hunters", Material.ROTTEN_FLESH, 500, 1470, "minecraft:story/shiny_gear", "Cover Me with Diamonds", 0, List.of(new Objective("kill:HUSK", 35, "Defeat 35 Husk"), new Objective("kill:STRAY", 25, "Defeat 25 Stray"))));
        result.add(new Pack("H024", "Piglin Patrol", Material.NETHER_STAR, 500, 1480, "minecraft:nether/find_bastion", "Those Were the Days", 0, List.of(new Objective("kill:PIGLIN", 45, "Defeat 45 Piglin"))));
        result.add(new Pack("H038", "Silverfish Sweeper", Material.NETHER_STAR, 500, 1480, "minecraft:story/follow_ender_eye", "Eye Spy", 0, List.of(new Objective("kill:SILVERFISH", 45, "Defeat 45 Silverfish"))));
        result.add(new Pack("H088", "Nether Portal Salvage", Material.IRON_PICKAXE, 500, 1480, "minecraft:story/form_obsidian", "Ice Bucket Challenge", 0, List.of(new Objective("break:OBSIDIAN", 40, "Mine 40 Obsidian"))));
        result.add(new Pack("H048", "Salmon Syndicate", Material.FISHING_ROD, 500, 1500, "minecraft:husbandry/fishy_business", "Fishy Business", 0, List.of(new Objective("fish:SALMON", 45, "Catch 45 Salmon"))));
        result.add(new Pack("H094", "Rod and Cube", Material.ROTTEN_FLESH, 500, 1500, "minecraft:nether/obtain_blaze_rod", "Into Fire", 160, List.of(new Objective("kill:BLAZE", 25, "Defeat 25 Blaze"), new Objective("kill:MAGMA_CUBE", 25, "Defeat 25 Magma Cube"))));
        result.add(new Pack("H039", "Raid Cleanup", Material.NETHER_STAR, 500, 1540, "minecraft:adventure/voluntary_exile", "Voluntary Exile", 170, List.of(new Objective("kill:PILLAGER", 40, "Defeat 40 Pillager"))));
        result.add(new Pack("H100", "Table Builder", Material.ENCHANTING_TABLE, 510, 1070, "minecraft:story/shiny_gear", "Cover Me with Diamonds", 0, List.of(new Objective("collect:ENCHANTING_TABLE", 6, "Collect 6 Enchanting Table"))));
        result.add(new Pack("H083", "Night Triple", Material.ROTTEN_FLESH, 510, 1520, "minecraft:story/follow_ender_eye", "Eye Spy", 0, List.of(new Objective("kill:ZOMBIE", 30, "Defeat 30 Zombie"), new Objective("kill:SKELETON", 25, "Defeat 25 Skeleton"), new Objective("kill:CREEPER", 20, "Defeat 20 Creeper"))));
        result.add(new Pack("H001", "Diamond Prospector", Material.IRON_PICKAXE, 510, 1540, "minecraft:story/mine_diamond", "Diamonds!", 0, List.of(new Objective("break:DIAMOND_ORE", 35, "Mine 35 Diamond Ore"))));
        result.add(new Pack("H087", "Mansion Vindicator Hunt", Material.NETHER_STAR, 510, 1590, "minecraft:adventure/voluntary_exile", "Voluntary Exile", 0, List.of(new Objective("kill:VINDICATOR", 40, "Defeat 40 Vindicator"))));
        result.add(new Pack("H037", "Enderman Ambush", Material.NETHER_STAR, 510, 1650, "minecraft:story/shiny_gear", "Cover Me with Diamonds", 190, List.of(new Objective("kill:ENDERMAN", 40, "Defeat 40 Enderman"))));
        result.add(new Pack("H041", "Guardian Patrol", Material.NETHER_STAR, 510, 1650, "minecraft:story/shiny_gear", "Cover Me with Diamonds", 190, List.of(new Objective("kill:GUARDIAN", 40, "Defeat 40 Guardian"))));
        result.add(new Pack("H097", "Vex Vanquisher", Material.NETHER_STAR, 510, 1650, "minecraft:adventure/voluntary_exile", "Voluntary Exile", 190, List.of(new Objective("kill:VEX", 40, "Defeat 40 Vex"))));
        result.add(new Pack("H010", "Below Zero Run", Material.IRON_PICKAXE, 520, 1640, "minecraft:story/mine_diamond", "Diamonds!", 0, List.of(new Objective("break:DEEPSLATE_DIAMOND_ORE", 12, "Mine 12 Deepslate Diamond Ore"), new Objective("break:DEEPSLATE_REDSTONE_ORE", 150, "Mine 150 Deepslate Redstone Ore"))));
        result.add(new Pack("H022", "Wither Skeleton Cull", Material.NETHER_STAR, 520, 1670, "minecraft:nether/obtain_blaze_rod", "Into Fire", 190, List.of(new Objective("kill:WITHER_SKELETON", 35, "Defeat 35 Wither Skeleton"))));
        result.add(new Pack("H029", "Fortress Duo", Material.ROTTEN_FLESH, 530, 1620, "minecraft:nether/obtain_blaze_rod", "Into Fire", 0, List.of(new Objective("kill:BLAZE", 30, "Defeat 30 Blaze"), new Objective("kill:WITHER_SKELETON", 17, "Defeat 17 Wither Skeleton"))));
        result.add(new Pack("H084", "Outpost Overrun", Material.ROTTEN_FLESH, 530, 1620, "minecraft:adventure/voluntary_exile", "Voluntary Exile", 0, List.of(new Objective("kill:PILLAGER", 22, "Defeat 22 Pillager"), new Objective("kill:VINDICATOR", 20, "Defeat 20 Vindicator"))));
        result.add(new Pack("H008", "Geode Raider", Material.IRON_PICKAXE, 530, 1630, "minecraft:story/mine_diamond", "Diamonds!", 0, List.of(new Objective("break:AMETHYST_CLUSTER", 30, "Mine 30 Amethyst Cluster"))));
        result.add(new Pack("H043", "Sea Lantern Looter", Material.IRON_PICKAXE, 530, 1630, "minecraft:story/mine_diamond", "Diamonds!", 0, List.of(new Objective("break:SEA_LANTERN", 30, "Mine 30 Sea Lantern"))));
        result.add(new Pack("H026", "Hoglin Roundup", Material.NETHER_STAR, 530, 1690, "minecraft:nether/find_bastion", "Those Were the Days", 190, List.of(new Objective("kill:HOGLIN", 30, "Defeat 30 Hoglin"))));
        result.add(new Pack("H035", "Phantom Menace", Material.NETHER_STAR, 540, 1650, "minecraft:adventure/kill_a_mob", "Monster Hunter", 0, List.of(new Objective("kill:PHANTOM", 25, "Defeat 25 Phantom"))));
        result.add(new Pack("H036", "Witch's Cauldron", Material.NETHER_STAR, 540, 1650, "minecraft:story/shiny_gear", "Cover Me with Diamonds", 0, List.of(new Objective("kill:WITCH", 25, "Defeat 25 Witch"))));
        result.add(new Pack("H046", "Pufferfish Plague", Material.FISHING_ROD, 540, 1660, "minecraft:husbandry/fishy_business", "Fishy Business", 0, List.of(new Objective("fish:PUFFERFISH", 25, "Catch 25 Pufferfish"))));
        result.add(new Pack("H060", "Stable Master", Material.WHEAT, 550, 1160, "minecraft:husbandry/breed_an_animal", "The Parrots and the Bats", 0, List.of(new Objective("breed:HORSE", 8, "Breed 8 Horses"))));
        result.add(new Pack("H050", "Anglers Trio", Material.FISHING_ROD, 550, 1730, "minecraft:husbandry/fishy_business", "Fishy Business", 0, List.of(new Objective("fish:COD", 40, "Catch 40 Cod"), new Objective("fish:SALMON", 17, "Catch 17 Salmon"), new Objective("fish:PUFFERFISH", 9, "Catch 9 Pufferfish"))));
        result.add(new Pack("H040", "Ravager Wrangler", Material.NETHER_STAR, 550, 1750, "minecraft:adventure/voluntary_exile", "Voluntary Exile", 0, List.of(new Objective("kill:RAVAGER", 22, "Defeat 22 Ravager"))));
        result.add(new Pack("H098", "Captains Bounty", Material.ROTTEN_FLESH, 550, 1850, "minecraft:adventure/voluntary_exile", "Voluntary Exile", 220, List.of(new Objective("kill:PILLAGER", 25, "Defeat 25 Pillager"), new Objective("kill:WITCH", 15, "Defeat 15 Witch"))));
        result.add(new Pack("H019", "Ancient Debris Dig", Material.IRON_PICKAXE, 570, 1350, "minecraft:nether/obtain_ancient_debris", "Hidden in the Depths", 0, List.of(new Objective("break:ANCIENT_DEBRIS", 8, "Mine 8 Ancient Debris"))));
        result.add(new Pack("H064", "Scrap Smelter", Material.FURNACE, 570, 1350, "minecraft:nether/obtain_ancient_debris", "Hidden in the Depths", 0, List.of(new Objective("smelt:NETHERITE_SCRAP", 8, "Smelt 8 Netherite Scrap"))));
        result.add(new Pack("H096", "Evoker Execution", Material.NETHER_STAR, 570, 1820, "minecraft:adventure/voluntary_exile", "Voluntary Exile", 0, List.of(new Objective("kill:EVOKER", 17, "Defeat 17 Evoker"))));
        result.add(new Pack("H095", "Ghast Gauntlet", Material.ROTTEN_FLESH, 570, 1860, "minecraft:nether/return_to_sender", "Return to Sender", 0, List.of(new Objective("kill:GHAST", 9, "Defeat 9 Ghast"), new Objective("kill:ZOMBIFIED_PIGLIN", 45, "Defeat 45 Zombified Piglin"))));
        result.add(new Pack("H023", "Ghast Tear Hunter", Material.NETHER_STAR, 570, 1880, "minecraft:nether/return_to_sender", "Return to Sender", 215, List.of(new Objective("kill:GHAST", 17, "Defeat 17 Ghast"))));
        result.add(new Pack("H093", "Soul Fire Hunt", Material.ROTTEN_FLESH, 580, 1920, "minecraft:nether/obtain_blaze_rod", "Into Fire", 220, List.of(new Objective("kill:WITHER_SKELETON", 20, "Defeat 20 Wither Skeleton"), new Objective("kill:GHAST", 9, "Defeat 9 Ghast"))));
        result.add(new Pack("H099", "Nether Conqueror", Material.IRON_PICKAXE, 580, 1930, "minecraft:nether/obtain_ancient_debris", "Hidden in the Depths", 0, List.of(new Objective("break:ANCIENT_DEBRIS", 6, "Mine 6 Ancient Debris"), new Objective("kill:BLAZE", 25, "Defeat 25 Blaze"), new Objective("kill:WITHER_SKELETON", 15, "Defeat 15 Wither Skeleton"))));
        result.add(new Pack("H002", "Emerald Mountain Miner", Material.IRON_PICKAXE, 590, 1810, "minecraft:story/mine_diamond", "Diamonds!", 0, List.of(new Objective("break:EMERALD_ORE", 12, "Mine 12 Emerald Ore"))));
        result.add(new Pack("H020", "Crying Obsidian Harvest", Material.IRON_PICKAXE, 590, 1810, "minecraft:nether/obtain_crying_obsidian", "Who is Cutting Onions?", 0, List.of(new Objective("break:CRYING_OBSIDIAN", 12, "Mine 12 Crying Obsidian"))));
        result.add(new Pack("H028", "Bastion Raiders", Material.ROTTEN_FLESH, 600, 1990, "minecraft:nether/find_bastion", "Those Were the Days", 0, List.of(new Objective("kill:PIGLIN", 30, "Defeat 30 Piglin"), new Objective("kill:PIGLIN_BRUTE", 5, "Defeat 5 Piglin Brute"))));
        result.add(new Pack("H025", "Brute Force", Material.NETHER_STAR, 620, 1550, "minecraft:nether/find_bastion", "Those Were the Days", 0, List.of(new Objective("kill:PIGLIN_BRUTE", 5, "Defeat 5 Piglin Brute"))));
        result.add(new Pack("H090", "Colorful Catch", Material.FISHING_ROD, 620, 1940, "minecraft:husbandry/fishy_business", "Fishy Business", 0, List.of(new Objective("fish:COD", 70, "Catch 70 Cod"), new Objective("fish:TROPICAL_FISH", 2, "Catch 2 Tropical Fish"))));
        result.add(new Pack("H074", "Swamp & Mangrove Trek", Material.COMPASS, 650, 1410, "minecraft:story/iron_tools", "Isn't It Iron Pick", 50, List.of(new Objective("visit-biome:SWAMP", 1, "Enter the Swamp biome"), new Objective("visit-biome:MANGROVE_SWAMP", 1, "Enter the Mangrove Swamp biome"))));
        result.add(new Pack("H072", "Biome Collector", Material.COMPASS, 650, 1440, "minecraft:adventure/sleep_in_bed", "Sweet Dreams", 0, List.of(new Objective("visit-biome:DESERT", 1, "Enter the Desert biome"), new Objective("visit-biome:BADLANDS", 1, "Enter the Badlands biome"), new Objective("visit-biome:SAVANNA", 1, "Enter the Savanna biome"), new Objective("visit-biome:PLAINS", 1, "Enter the Plains biome"))));
        result.add(new Pack("H047", "Tropical Trove", Material.FISHING_ROD, 660, 2160, "minecraft:husbandry/tactical_fishing", "Tactical Fishing", 0, List.of(new Objective("fish:TROPICAL_FISH", 5, "Catch 5 Tropical Fish"))));
        result.add(new Pack("H076", "Blossom & Pale Wanderer", Material.COMPASS, 680, 1530, "minecraft:story/iron_tools", "Isn't It Iron Pick", 0, List.of(new Objective("visit-biome:CHERRY_GROVE", 1, "Enter the Cherry Grove biome"), new Objective("visit-biome:PALE_GARDEN", 1, "Enter the Pale Garden biome"))));
        result.add(new Pack("H075", "Mushroom Pilgrim", Material.COMPASS, 690, 1480, "minecraft:story/iron_tools", "Isn't It Iron Pick", 0, List.of(new Objective("visit-biome:MUSHROOM_FIELDS", 1, "Enter the Mushroom Fields biome"))));
        result.add(new Pack("H080", "Cave Biome Delver", Material.COMPASS, 690, 1610, "minecraft:story/shiny_gear", "Cover Me with Diamonds", 0, List.of(new Objective("visit-biome:DRIPSTONE_CAVES", 1, "Enter a Dripstone Caves biome"), new Objective("visit-biome:DEEP_DARK", 1, "Enter a Deep Dark biome"))));
        result.add(new Pack("H091", "Elder Guardian Slayer", Material.NETHER_STAR, 700, 1580, "minecraft:story/shiny_gear", "Cover Me with Diamonds", 0, List.of(new Objective("kill:ELDER_GUARDIAN", 1, "Defeat 1 Elder Guardian"))));
        result.add(new Pack("H092", "Roaming Boss Hunter", Material.NETHER_STAR, 700, 1580, "minecraft:adventure/kill_a_mob", "Monster Hunter", 0, List.of(new Objective("kill:@ROAMING_BOSS", 1, "Defeat 1 Roaming Boss"))));
        result.add(new Pack("M002", "End Stone Excavator", Material.IRON_PICKAXE, 1000, 3000, "minecraft:end/enter_end_gateway", "Remote Getaway", 0, List.of(new Objective("break:END_STONE", 800, "Mine 800 End Stone"))));
        result.add(new Pack("M084", "Field Marshal", Material.WHEAT, 1000, 3150, "minecraft:husbandry/plant_seed", "A Seedy Place", 0, List.of(new Objective("harvest:WHEAT", 1000, "Harvest 1,000 Wheat"))));
        result.add(new Pack("M085", "Mesa Marathon", Material.COMPASS, 1000, 3150, "minecraft:story/iron_tools", "Isn't It Iron Pick", 0, List.of(new Objective("travel-biome:BADLANDS", 4600, "Travel 4,600 blocks inside Badlands biomes"))));
        result.add(new Pack("M081", "Mountain Mover", Material.IRON_PICKAXE, 1000, 3200, "minecraft:story/mine_diamond", "Diamonds!", 0, List.of(new Objective("break:STONE", 750, "Mine 750 Stone"))));
        result.add(new Pack("M090", "Nether Wanderer", Material.COMPASS, 1000, 3200, "minecraft:nether/fast_travel", "Subspace Bubble", 0, List.of(new Objective("travel-world:NETHER", 5300, "Travel 5,300 blocks in the Nether"))));
        result.add(new Pack("M008", "Sky Pirate", Material.COMPASS, 1000, 3350, "minecraft:end/elytra", "Sky's the Limit", 95, List.of(new Objective("travel", 5600, "Travel 5,600 blocks"))));
        result.add(new Pack("M009", "Void Walker", Material.COMPASS, 1000, 3400, "minecraft:end/enter_end_gateway", "Remote Getaway", 100, List.of(new Objective("travel-world:THE_END", 3400, "Travel 3,400 blocks in the End"))));
        result.add(new Pack("M083", "Forest Eater", Material.IRON_PICKAXE, 1050, 3450, "minecraft:story/iron_tools", "Isn't It Iron Pick", 0, List.of(new Objective("break:OAK_LOG", 700, "Mine 700 Oak Log"))));
        result.add(new Pack("M034", "Deepslate Titan", Material.IRON_PICKAXE, 1050, 3650, "minecraft:story/mine_diamond", "Diamonds!", 110, List.of(new Objective("break:DEEPSLATE", 650, "Mine 650 Deepslate"))));
        result.add(new Pack("M088", "Furnace Emperor", Material.FURNACE, 1100, 3550, "minecraft:story/mine_diamond", "Diamonds!", 0, List.of(new Objective("smelt:ANY", 650, "Smelt 650 Any"))));
        result.add(new Pack("M080", "Master Chef", Material.FURNACE, 1100, 3850, "minecraft:husbandry/balanced_diet", "A Balanced Diet", 120, List.of(new Objective("smelt:COOKED_BEEF", 650, "Smelt 650 Cooked Beef"))));
        result.add(new Pack("M040", "Dark Descent", Material.IRON_PICKAXE, 1250, 4050, "minecraft:story/shiny_gear", "Cover Me with Diamonds", 0, List.of(new Objective("break:ANY_ORE", 400, "Mine 400 Any Ore"))));
        result.add(new Pack("M031", "Sculk Sweeper", Material.IRON_PICKAXE, 1250, 4150, "minecraft:adventure/avoid_vibration", "Sneak 100", 120, List.of(new Objective("break:SCULK", 350, "Mine 350 Sculk"))));
        result.add(new Pack("M013", "Quartz Empire", Material.IRON_PICKAXE, 1300, 4550, "minecraft:nether/find_fortress", "A Terrible Fortress", 140, List.of(new Objective("break:NETHER_QUARTZ_ORE", 350, "Mine 350 Nether Quartz Ore"))));
        result.add(new Pack("M037", "Redstone Reactor", Material.IRON_PICKAXE, 1300, 4550, "minecraft:story/mine_diamond", "Diamonds!", 140, List.of(new Objective("break:REDSTONE_ORE", 350, "Mine 350 Redstone Ore"))));
        result.add(new Pack("M073", "Farmer's Grand Slam", Material.WHEAT, 1350, 4550, "minecraft:husbandry/plant_seed", "A Seedy Place", 135, List.of(new Objective("harvest:WHEAT", 250, "Harvest 250 Wheat"), new Objective("harvest:CARROT", 250, "Harvest 250 Carrot"), new Objective("harvest:POTATO", 110, "Harvest 110 Potato"), new Objective("harvest:MELON_SLICE", 80, "Harvest 80 Melon Slice"), new Objective("harvest:PUMPKIN", 80, "Harvest 80 Pumpkin"))));
        result.add(new Pack("M004", "Chorus Harvest", Material.IRON_PICKAXE, 1350, 4800, "minecraft:end/enter_end_gateway", "Remote Getaway", 150, List.of(new Objective("break:CHORUS_PLANT", 300, "Mine 300 Chorus Plant"))));
        result.add(new Pack("M003", "Purpur Pillager", Material.IRON_PICKAXE, 1450, 4750, "minecraft:end/find_end_city", "The City at the End of the Game", 135, List.of(new Objective("break:PURPUR_BLOCK", 200, "Mine 200 Purpur Block"))));
        result.add(new Pack("M038", "Lapis Legion", Material.IRON_PICKAXE, 1450, 4750, "minecraft:story/enchant_item", "Enchanter", 135, List.of(new Objective("break:LAPIS_ORE", 200, "Mine 200 Lapis Ore"))));
        result.add(new Pack("M064", "Prismarine Palace", Material.IRON_PICKAXE, 1450, 4750, "minecraft:story/mine_diamond", "Diamonds!", 135, List.of(new Objective("break:PRISMARINE", 200, "Mine 200 Prismarine"))));
        result.add(new Pack("M057", "Wind Charge Works", Material.WIND_CHARGE, 1650, 4350, "minecraft:adventure/revaulting", "Revaulting", 0, List.of(new Objective("collect:WIND_CHARGE", 64, "Collect 64 Wind Charge"))));
        result.add(new Pack("M067", "Cod Angler Master", Material.FISHING_ROD, 1650, 5200, "minecraft:husbandry/fishy_business", "Fishy Business", 0, List.of(new Objective("fish:COD", 120, "Catch 120 Cod"))));
        result.add(new Pack("M075", "Smith's Grand Slam", Material.FURNACE, 1650, 5450, "minecraft:story/mine_diamond", "Diamonds!", 160, List.of(new Objective("smelt:IRON_INGOT", 80, "Smelt 80 Iron Ingot"), new Objective("smelt:GOLD_INGOT", 30, "Smelt 30 Gold Ingot"), new Objective("smelt:COPPER_INGOT", 110, "Smelt 110 Copper Ingot"), new Objective("smelt:GLASS", 250, "Smelt 250 Glass"))));
        result.add(new Pack("M078", "Breeder's Grand Slam", Material.WHEAT, 1700, 5250, "minecraft:husbandry/bred_all_animals", "Two by Two", 0, List.of(new Objective("breed:ANY", 100, "Breed 100 Animals"))));
        result.add(new Pack("M055", "Trial Chamber Zombie Sweep", Material.NETHER_STAR, 1750, 5750, "minecraft:adventure/minecraft_trials_edition", "Minecraft: Trial(s) Edition", 200, List.of(new Objective("kill:ZOMBIE", 80, "Defeat 80 Zombie"))));
        result.add(new Pack("M060", "Skeleton Siege", Material.ROTTEN_FLESH, 1850, 6100, "minecraft:adventure/overoverkill", "Over-Overkill", 220, List.of(new Objective("kill:SKELETON", 70, "Defeat 70 Skeleton"))));
        result.add(new Pack("M059", "Blast Hunter", Material.ROTTEN_FLESH, 1900, 6050, "minecraft:adventure/under_lock_and_key", "Under Lock and Key", 0, List.of(new Objective("kill:CREEPER", 60, "Defeat 60 Creeper"))));
        result.add(new Pack("M077", "Spider Nest", Material.ROTTEN_FLESH, 1900, 6050, "minecraft:story/shiny_gear", "Cover Me with Diamonds", 0, List.of(new Objective("kill:SPIDER", 45, "Defeat 45 Spider"), new Objective("kill:CAVE_SPIDER", 35, "Defeat 35 Cave Spider"))));
        result.add(new Pack("M087", "Salmon Run Master", Material.FISHING_ROD, 1900, 6050, "minecraft:husbandry/tactical_fishing", "Tactical Fishing", 0, List.of(new Objective("fish:SALMON", 60, "Catch 60 Salmon"))));
        result.add(new Pack("M010", "Endgame Supply Run", Material.NETHER_STAR, 1900, 6300, "minecraft:end/find_end_city", "The City at the End of the Game", 230, List.of(new Objective("kill:ENDERMAN", 12, "Defeat 12 Enderman"), new Objective("break:END_STONE", 550, "Mine 550 End Stone"), new Objective("break:CHORUS_PLANT", 120, "Mine 120 Chorus Plant"))));
        result.add(new Pack("M018", "Blaze Baron", Material.NETHER_STAR, 1900, 6550, "minecraft:nether/obtain_blaze_rod", "Into Fire", 250, List.of(new Objective("kill:BLAZE", 60, "Defeat 60 Blaze"))));
        result.add(new Pack("M066", "Trench Diver", Material.NETHER_STAR, 1900, 6550, "minecraft:husbandry/fishy_business", "Fishy Business", 250, List.of(new Objective("kill:DROWNED", 60, "Defeat 60 Drowned"))));
        result.add(new Pack("M016", "Hoglin Husbandry", Material.WHEAT, 1950, 4950, "minecraft:nether/find_bastion", "Those Were the Days", 0, List.of(new Objective("breed:HOGLIN", 14, "Breed 14 Hoglins"))));
        result.add(new Pack("M089", "Pasture Pioneer", Material.WHEAT, 1950, 6300, "minecraft:husbandry/breed_an_animal", "The Parrots and the Bats", 0, List.of(new Objective("breed:COW", 25, "Breed 25 Cows"), new Objective("breed:SHEEP", 25, "Breed 25 Sheep"), new Objective("breed:PIG", 25, "Breed 25 Pigs"), new Objective("breed:CHICKEN", 25, "Breed 25 Chickens"))));
        result.add(new Pack("M035", "Diamond Dynasty", Material.IRON_PICKAXE, 1950, 6350, "minecraft:story/shiny_gear", "Cover Me with Diamonds", 0, List.of(new Objective("break:DIAMOND_ORE", 50, "Mine 50 Diamond Ore"))));
        result.add(new Pack("M014", "Piglin Diplomat", Material.NETHER_STAR, 1950, 6650, "minecraft:nether/distract_piglin", "Oh Shiny", 250, List.of(new Objective("kill:PIGLIN", 50, "Defeat 50 Piglin"))));
        result.add(new Pack("M054", "Bogged Down", Material.NETHER_STAR, 1950, 6650, "minecraft:adventure/minecraft_trials_edition", "Minecraft: Trial(s) Edition", 250, List.of(new Objective("kill:BOGGED", 50, "Defeat 50 Bogged"))));
        result.add(new Pack("M050", "Raid Quartermaster", Material.CROSSBOW, 2000, 6500, "minecraft:adventure/hero_of_the_village", "Hero of the Village", 0, List.of(new Objective("collect:CROSSBOW", 80, "Collect 80 Crossbow"), new Objective("kill:PILLAGER", 22, "Defeat 22 Pillager"))));
        result.add(new Pack("M086", "Hostile Horde", Material.ROTTEN_FLESH, 2000, 6500, "minecraft:story/shiny_gear", "Cover Me with Diamonds", 0, List.of(new Objective("kill:ZOMBIE", 25, "Defeat 25 Zombie"), new Objective("kill:SKELETON", 20, "Defeat 20 Skeleton"), new Objective("kill:CREEPER", 17, "Defeat 17 Creeper"), new Objective("kill:SPIDER", 25, "Defeat 25 Spider"))));
        result.add(new Pack("M027", "Raid Breaker", Material.NETHER_STAR, 2000, 6750, "minecraft:adventure/voluntary_exile", "Voluntary Exile", 250, List.of(new Objective("kill:PILLAGER", 45, "Defeat 45 Pillager"))));
        result.add(new Pack("M041", "Mansion Vindicator Hunt II", Material.NETHER_STAR, 2050, 6850, "minecraft:adventure/voluntary_exile", "Voluntary Exile", 0, List.of(new Objective("kill:VINDICATOR", 45, "Defeat 45 Vindicator"))));
        result.add(new Pack("M017", "Skull Magnate", Material.NETHER_STAR, 2050, 6900, "minecraft:nether/get_wither_skull", "Spooky Scary Skeleton", 0, List.of(new Objective("kill:WITHER_SKELETON", 40, "Defeat 40 Wither Skeleton"))));
        result.add(new Pack("M001", "Enderman Exterminator", Material.NETHER_STAR, 2050, 7150, "minecraft:end/enter_end_gateway", "Remote Getaway", 285, List.of(new Objective("kill:ENDERMAN", 45, "Defeat 45 Enderman"))));
        result.add(new Pack("M039", "Geode Gatherer", Material.IRON_PICKAXE, 2050, 7150, "minecraft:story/mine_diamond", "Diamonds!", 225, List.of(new Objective("break:AMETHYST_CLUSTER", 45, "Mine 45 Amethyst Cluster"))));
        result.add(new Pack("M049", "Vex Vendetta", Material.NETHER_STAR, 2050, 7150, "minecraft:adventure/voluntary_exile", "Voluntary Exile", 285, List.of(new Objective("kill:VEX", 45, "Defeat 45 Vex"))));
        result.add(new Pack("M061", "Monument Marauder", Material.NETHER_STAR, 2050, 7150, "minecraft:story/shiny_gear", "Cover Me with Diamonds", 285, List.of(new Objective("kill:GUARDIAN", 45, "Defeat 45 Guardian"))));
        result.add(new Pack("M065", "Sea Lantern Empire", Material.IRON_PICKAXE, 2050, 7150, "minecraft:story/mine_diamond", "Diamonds!", 225, List.of(new Objective("break:SEA_LANTERN", 45, "Mine 45 Sea Lantern"))));
        result.add(new Pack("M091", "Three Realms Trial", Material.IRON_PICKAXE, 2050, 7200, "minecraft:end/find_end_city", "The City at the End of the Game", 290, List.of(new Objective("break:IRON_ORE", 160, "Mine 160 Iron Ore"), new Objective("kill:BLAZE", 22, "Defeat 22 Blaze"), new Objective("kill:ENDERMAN", 15, "Defeat 15 Enderman"))));
        result.add(new Pack("M069", "Pufferfish Plague II", Material.FISHING_ROD, 2100, 7150, "minecraft:husbandry/fishy_business", "Fishy Business", 0, List.of(new Objective("fish:PUFFERFISH", 40, "Catch 40 Pufferfish"))));
        result.add(new Pack("M006", "End City Raid", Material.ROTTEN_FLESH, 2100, 7250, "minecraft:end/find_end_city", "The City at the End of the Game", 0, List.of(new Objective("kill:SHULKER", 12, "Defeat 12 Shulker"), new Objective("break:PURPUR_BLOCK", 190, "Mine 190 Purpur Block"))));
        result.add(new Pack("M051", "Trial Initiate", Material.NETHER_STAR, 2100, 7400, "minecraft:adventure/minecraft_trials_edition", "Minecraft: Trial(s) Edition", 295, List.of(new Objective("kill:BREEZE", 35, "Defeat 35 Breeze"))));
        result.add(new Pack("M020", "Fortress Overlord", Material.NETHER_STAR, 2100, 7500, "minecraft:nether/find_fortress", "A Terrible Fortress", 305, List.of(new Objective("kill:WITHER_SKELETON", 22, "Defeat 22 Wither Skeleton"), new Objective("kill:BLAZE", 35, "Defeat 35 Blaze"))));
        result.add(new Pack("M056", "Breeze Rod Smith", Material.BREEZE_ROD, 2150, 6650, "minecraft:adventure/minecraft_trials_edition", "Minecraft: Trial(s) Edition", 0, List.of(new Objective("collect:BREEZE_ROD", 24, "Collect 24 Breeze Rod"))));
        result.add(new Pack("M015", "Gilded Gambit", Material.IRON_PICKAXE, 2150, 6950, "minecraft:nether/find_bastion", "Those Were the Days", 195, List.of(new Objective("break:GILDED_BLACKSTONE", 24, "Mine 24 Gilded Blackstone"))));
        result.add(new Pack("M052", "Trial Keepers", Material.ROTTEN_FLESH, 2150, 7750, "minecraft:adventure/under_lock_and_key", "Under Lock and Key", 320, List.of(new Objective("kill:BREEZE", 20, "Defeat 20 Breeze"), new Objective("kill:BOGGED", 30, "Defeat 30 Bogged"))));
        result.add(new Pack("M098", "Trial by Fire", Material.NETHER_STAR, 2150, 7900, "minecraft:adventure/under_lock_and_key", "Under Lock and Key", 335, List.of(new Objective("kill:BREEZE", 20, "Defeat 20 Breeze"), new Objective("kill:BLAZE", 40, "Defeat 40 Blaze"))));
        result.add(new Pack("M047", "Witch Doctor", Material.NETHER_STAR, 2150, 8050, "minecraft:story/shiny_gear", "Cover Me with Diamonds", 350, List.of(new Objective("kill:WITCH", 35, "Defeat 35 Witch"))));
        result.add(new Pack("M033", "Shrieker Silencer", Material.SCULK_SHRIEKER, 2200, 7250, "minecraft:adventure/avoid_vibration", "Sneak 100", 0, List.of(new Objective("break:SCULK_SHRIEKER", 6, "Mine 6 Sculk Shrieker"), new Objective("break:SCULK_VEIN", 400, "Mine 400 Sculk Vein"))));
        result.add(new Pack("M058", "Trial Chamber Clearing", Material.ROTTEN_FLESH, 2200, 7550, "minecraft:adventure/revaulting", "Revaulting", 0, List.of(new Objective("kill:BREEZE", 12, "Defeat 12 Breeze"), new Objective("kill:ZOMBIE", 45, "Defeat 45 Zombie"), new Objective("kill:SKELETON", 35, "Defeat 35 Skeleton"))));
        result.add(new Pack("M048", "Raid Captain", Material.ROTTEN_FLESH, 2200, 8050, "minecraft:adventure/voluntary_exile", "Voluntary Exile", 340, List.of(new Objective("kill:PILLAGER", 30, "Defeat 30 Pillager"), new Objective("kill:WITCH", 17, "Defeat 17 Witch"))));
        result.add(new Pack("M011", "Debris Magnate", Material.IRON_PICKAXE, 2250, 7450, "minecraft:nether/obtain_ancient_debris", "Hidden in the Depths", 0, List.of(new Objective("break:ANCIENT_DEBRIS", 24, "Mine 24 Ancient Debris"))));
        result.add(new Pack("M012", "Scrap Smelter Supreme", Material.FURNACE, 2250, 7450, "minecraft:nether/obtain_ancient_debris", "Hidden in the Depths", 0, List.of(new Objective("smelt:NETHERITE_SCRAP", 24, "Smelt 24 Netherite Scrap"))));
        result.add(new Pack("M072", "Hunter's Grand Slam", Material.NETHER_STAR, 2250, 8350, "minecraft:story/shiny_gear", "Cover Me with Diamonds", 360, List.of(new Objective("kill:ENDERMAN", 15, "Defeat 15 Enderman"), new Objective("kill:BLAZE", 22, "Defeat 22 Blaze"), new Objective("kill:WITCH", 9, "Defeat 9 Witch"), new Objective("kill:CREEPER", 22, "Defeat 22 Creeper"))));
        result.add(new Pack("M005", "Shulker Shell Hunt", Material.NETHER_STAR, 2250, 8500, "minecraft:end/find_end_city", "The City at the End of the Game", 360, List.of(new Objective("kill:SHULKER", 30, "Defeat 30 Shulker"))));
        result.add(new Pack("M028", "Ravager Rodeo", Material.NETHER_STAR, 2250, 8500, "minecraft:adventure/voluntary_exile", "Voluntary Exile", 360, List.of(new Objective("kill:RAVAGER", 30, "Defeat 30 Ravager"))));
        result.add(new Pack("M093", "End Game Gauntlet", Material.NETHER_STAR, 2250, 8500, "minecraft:end/find_end_city", "The City at the End of the Game", 360, List.of(new Objective("kill:SHULKER", 15, "Defeat 15 Shulker"), new Objective("kill:ENDERMAN", 30, "Defeat 30 Enderman"))));
        result.add(new Pack("M043", "Illager Gauntlet", Material.ROTTEN_FLESH, 2250, 8600, "minecraft:adventure/voluntary_exile", "Voluntary Exile", 360, List.of(new Objective("kill:PILLAGER", 35, "Defeat 35 Pillager"), new Objective("kill:RAVAGER", 15, "Defeat 15 Ravager"))));
        result.add(new Pack("M070", "Deep Ocean Voyager", Material.COMPASS, 2300, 6300, "minecraft:story/iron_tools", "Isn't It Iron Pick", 0, List.of(new Objective("travel-biome:DEEP_OCEAN", 8000, "Travel 8,000 blocks inside Deep Ocean biomes"), new Objective("visit-biome:WARM_OCEAN", 1, "Enter a Warm Ocean biome"))));
        result.add(new Pack("M029", "Evoker Executioner", Material.NETHER_STAR, 2300, 8250, "minecraft:adventure/totem_of_undying", "Postmortal", 0, List.of(new Objective("kill:EVOKER", 22, "Defeat 22 Evoker"))));
        result.add(new Pack("M036", "Emerald Empire", Material.IRON_PICKAXE, 2300, 8250, "minecraft:story/mine_diamond", "Diamonds!", 0, List.of(new Objective("break:EMERALD_ORE", 22, "Mine 22 Emerald Ore"))));
        result.add(new Pack("M079", "Dimension Hopper", Material.IRON_PICKAXE, 2300, 8400, "minecraft:end/find_end_city", "The City at the End of the Game", 0, List.of(new Objective("break:ANCIENT_DEBRIS", 10, "Mine 10 Ancient Debris"), new Objective("kill:SHULKER", 10, "Defeat 10 Shulker"), new Objective("fish:COD", 80, "Catch 80 Cod"))));
        result.add(new Pack("M032", "Ancient City Looter", Material.SCULK_SENSOR, 2350, 7000, "minecraft:adventure/avoid_vibration", "Sneak 100", 0, List.of(new Objective("break:SCULK_SENSOR", 12, "Mine 12 Sculk Sensor"), new Objective("break:SCULK_CATALYST", 8, "Mine 8 Sculk Catalyst"))));
        result.add(new Pack("M046", "Evoker Trio", Material.ROTTEN_FLESH, 2350, 8500, "minecraft:adventure/totem_of_undying", "Postmortal", 0, List.of(new Objective("kill:EVOKER", 12, "Defeat 12 Evoker"), new Objective("kill:VEX", 30, "Defeat 30 Vex"))));
        result.add(new Pack("M071", "Miner's Grand Slam", Material.IRON_PICKAXE, 2350, 8600, "minecraft:story/shiny_gear", "Cover Me with Diamonds", 0, List.of(new Objective("break:DIAMOND_ORE", 20, "Mine 20 Diamond Ore"), new Objective("break:EMERALD_ORE", 6, "Mine 6 Emerald Ore"), new Objective("break:GOLD_ORE", 60, "Mine 60 Gold Ore"), new Objective("break:ANCIENT_DEBRIS", 8, "Mine 8 Ancient Debris"))));
        result.add(new Pack("M092", "Netherite Initiation", Material.IRON_PICKAXE, 2350, 8600, "minecraft:nether/obtain_ancient_debris", "Hidden in the Depths", 0, List.of(new Objective("break:ANCIENT_DEBRIS", 10, "Mine 10 Ancient Debris"), new Objective("smelt:NETHERITE_SCRAP", 10, "Smelt 10 Netherite Scrap"), new Objective("kill:WITHER_SKELETON", 20, "Defeat 20 Wither Skeleton"))));
        result.add(new Pack("M044", "Mansion Crashers", Material.ROTTEN_FLESH, 2350, 8800, "minecraft:adventure/voluntary_exile", "Voluntary Exile", 360, List.of(new Objective("kill:VINDICATOR", 30, "Defeat 30 Vindicator"), new Objective("kill:EVOKER", 12, "Defeat 12 Evoker"))));
        result.add(new Pack("M096", "Pillager's Doom", Material.NETHER_STAR, 2400, 8750, "minecraft:adventure/voluntary_exile", "Voluntary Exile", 0, List.of(new Objective("kill:VINDICATOR", 22, "Defeat 22 Vindicator"), new Objective("kill:EVOKER", 8, "Defeat 8 Evoker"), new Objective("kill:RAVAGER", 10, "Defeat 10 Ravager"))));
        result.add(new Pack("M042", "Mansion Cleansing", Material.NETHER_STAR, 2400, 9150, "minecraft:adventure/voluntary_exile", "Voluntary Exile", 360, List.of(new Objective("kill:VINDICATOR", 22, "Defeat 22 Vindicator"), new Objective("kill:EVOKER", 8, "Defeat 8 Evoker"), new Objective("kill:VEX", 22, "Defeat 22 Vex"))));
        result.add(new Pack("M063", "Sponge Collector", Material.IRON_PICKAXE, 2450, 7850, "minecraft:story/shiny_gear", "Cover Me with Diamonds", 0, List.of(new Objective("break:WET_SPONGE", 12, "Collect 12 Wet Sponge"))));
        result.add(new Pack("M019", "Bastion Warlord", Material.ROTTEN_FLESH, 2500, 9050, "minecraft:nether/loot_bastion", "War Pigs", 0, List.of(new Objective("kill:PIGLIN_BRUTE", 6, "Defeat 6 Piglin Brute"), new Objective("kill:HOGLIN", 25, "Defeat 25 Hoglin"))));
        result.add(new Pack("M099", "Legend of the Wanderer", Material.COMPASS, 2500, 9250, "minecraft:adventure/sleep_in_bed", "Sweet Dreams", 0, List.of(new Objective("travel", 8000, "Travel 8,000 blocks"), new Objective("visit-biome:CHERRY_GROVE", 1, "Enter the Cherry Grove biome"), new Objective("visit-biome:PALE_GARDEN", 1, "Enter the Pale Garden biome"), new Objective("kill:ZOMBIE", 70, "Defeat 70 Zombie"))));
        result.add(new Pack("M074", "Angler's Grand Slam", Material.FISHING_ROD, 2500, 9300, "minecraft:husbandry/tactical_fishing", "Tactical Fishing", 0, List.of(new Objective("fish:COD", 60, "Catch 60 Cod"), new Objective("fish:SALMON", 30, "Catch 30 Salmon"), new Objective("fish:PUFFERFISH", 15, "Catch 15 Pufferfish"), new Objective("fish:TROPICAL_FISH", 2, "Catch 2 Tropical Fish"))));
        result.add(new Pack("M094", "Abyss Walker", Material.NETHER_STAR, 2500, 9300, "minecraft:adventure/avoid_vibration", "Sneak 100", 0, List.of(new Objective("kill:WARDEN", 1, "Defeat 1 Warden"), new Objective("break:SCULK", 700, "Mine 700 Sculk"))));
        result.add(new Pack("M082", "Netherite Smith", Material.NETHERITE_INGOT, 2650, 6750, "minecraft:story/shiny_gear", "Cover Me with Diamonds", 0, List.of(new Objective("collect:NETHERITE_INGOT", 3, "Collect 3 Netherite Ingot"))));
        result.add(new Pack("M045", "Totem Collector", Material.TOTEM_OF_UNDYING, 2650, 8550, "minecraft:adventure/voluntary_exile", "Voluntary Exile", 240, List.of(new Objective("collect:TOTEM_OF_UNDYING", 6, "Collect 6 Totem Of Undying"))));
        result.add(new Pack("M053", "Vault Raider", Material.TRIAL_KEY, 2650, 9550, "minecraft:adventure/minecraft_trials_edition", "Minecraft: Trial(s) Edition", 320, List.of(new Objective("collect:TRIAL_KEY", 8, "Collect 8 Trial Key"))));
        result.add(new Pack("M068", "Tropical Tycoon", Material.FISHING_ROD, 2650, 9750, "minecraft:husbandry/tactical_fishing", "Tactical Fishing", 0, List.of(new Objective("fish:TROPICAL_FISH", 9, "Catch 9 Tropical Fish"))));
        result.add(new Pack("M062", "Guardian Duo", Material.ROTTEN_FLESH, 2700, 10050, "minecraft:story/shiny_gear", "Cover Me with Diamonds", 0, List.of(new Objective("kill:ELDER_GUARDIAN", 1, "Defeat 1 Elder Guardian"), new Objective("kill:GUARDIAN", 60, "Defeat 60 Guardian"))));
        result.add(new Pack("M095", "Siege of the Wither", Material.NETHER_STAR, 2750, 10350, "minecraft:nether/summon_wither", "Withering Heights", 360, List.of(new Objective("kill:WITHER", 1, "Defeat 1 Wither"), new Objective("kill:WITHER_SKELETON", 45, "Defeat 45 Wither Skeleton"))));
        result.add(new Pack("M076", "Explorer's Grand Slam", Material.COMPASS, 2800, 7300, "minecraft:adventure/sleep_in_bed", "Sweet Dreams", 0, List.of(new Objective("visit-biome:DESERT", 1, "Enter the Desert biome"), new Objective("visit-biome:JUNGLE", 1, "Enter the Jungle biome"), new Objective("visit-biome:BADLANDS", 1, "Enter the Badlands biome"), new Objective("visit-biome:SNOWY_PLAINS", 1, "Enter the Snowy Plains biome"), new Objective("visit-biome:MUSHROOM_FIELDS", 1, "Enter the Mushroom Fields biome"))));
        result.add(new Pack("M026", "Roaming Boss Triple", Material.NETHER_STAR, 2800, 8350, "minecraft:adventure/kill_a_mob", "Monster Hunter", 0, List.of(new Objective("kill:@ROAMING_BOSS", 3, "Defeat 3 Roaming Boss"))));
        result.add(new Pack("M024", "Elder Guardian Trio", Material.NETHER_STAR, 2800, 8650, "minecraft:story/shiny_gear", "Cover Me with Diamonds", 280, List.of(new Objective("kill:ELDER_GUARDIAN", 3, "Defeat 3 Elder Guardian"))));
        result.add(new Pack("M097", "Ocean Overlord", Material.NETHER_STAR, 2800, 10600, "minecraft:story/shiny_gear", "Cover Me with Diamonds", 360, List.of(new Objective("kill:ELDER_GUARDIAN", 2, "Defeat 2 Elder Guardian"), new Objective("kill:GUARDIAN", 22, "Defeat 22 Guardian"), new Objective("break:WET_SPONGE", 6, "Collect 6 Wet Sponge"))));
        result.add(new Pack("M025", "Warden Duel", Material.NETHER_STAR, 2850, 7050, "minecraft:adventure/avoid_vibration", "Sneak 100", 0, List.of(new Objective("kill:WARDEN", 1, "Defeat 1 Warden"))));
        result.add(new Pack("M021", "Wither Wrecker", Material.NETHER_STAR, 2850, 7350, "minecraft:nether/summon_wither", "Withering Heights", 140, List.of(new Objective("kill:WITHER", 1, "Defeat 1 Wither"))));
        result.add(new Pack("M022", "Beacon Bound", Material.ROTTEN_FLESH, 2850, 7550, "minecraft:nether/summon_wither", "Withering Heights", 0, List.of(new Objective("kill:WITHER", 1, "Defeat 1 Wither"), new Objective("collect:BEACON", 1, "Collect 1 Beacon"))));
        result.add(new Pack("M007", "Dragon Head Heist", Material.IRON_PICKAXE, 2850, 7750, "minecraft:end/find_end_city", "The City at the End of the Game", 0, List.of(new Objective("break:DRAGON_HEAD", 1, "Mine 1 Dragon Head"))));
        result.add(new Pack("M023", "Dragon Slayer Redux", Material.NETHER_STAR, 2850, 7950, "minecraft:end/respawn_dragon", "The End... Again...", 0, List.of(new Objective("kill:ENDER_DRAGON", 1, "Defeat 1 Ender Dragon"))));
        result.add(new Pack("M030", "Boss Rush", Material.NETHER_STAR, 3000, 10600, "minecraft:end/respawn_dragon", "The End... Again...", 0, List.of(new Objective("kill:ELDER_GUARDIAN", 1, "Defeat 1 Elder Guardian"), new Objective("kill:WITHER", 1, "Defeat 1 Wither"), new Objective("kill:ENDER_DRAGON", 1, "Defeat 1 Ender Dragon"))));
        result.add(new Pack("M100", "The Grand Master", Material.NETHER_STAR, 3000, 11000, "minecraft:end/respawn_dragon", "The End... Again...", 0, List.of(new Objective("kill:ENDER_DRAGON", 1, "Defeat 1 Ender Dragon"), new Objective("kill:WITHER", 1, "Defeat 1 Wither"), new Objective("kill:ELDER_GUARDIAN", 1, "Defeat 1 Elder Guardian"), new Objective("kill:WARDEN", 1, "Defeat 1 Warden"))));
        return sortAndPricePacks(result);
    }

    private List<Pack> sortAndPricePacks(List<Pack> source) {
        List<Pack> ordered = new ArrayList<>(300);
        for (char tier : new char[] {'E', 'H', 'M'}) {
            List<Pack> tierPacks = new ArrayList<>();
            for (Pack pack : source) if (pack.id().charAt(0) == tier) tierPacks.add(pack);
            tierPacks.sort(Comparator.comparingInt(Pack::cost)
                    .thenComparingInt(Pack::reward)
                    .thenComparing(Pack::id));
            for (int i = 0; i < tierPacks.size(); i++) {
                Pack pack = tierPacks.get(i);
                String numberedName = String.format(Locale.ROOT, "(%02d) %s", i + 1,
                        pack.name().replaceFirst("^\\(\\d{2}\\)\\s*", ""));
                Material icon = questIconForPack(pack);
                int profit = pack.reward() - pack.cost();
                ordered.add(new Pack(pack.id(), numberedName, icon, pack.cost(), pack.cost() + profit,
                        pack.advancement(), pack.unlockTitle(), pack.timerMinutes(), pack.objectives()));
            }
        }
        return ordered;
    }

    /**
     * Keeps collection objectives achievable during ordinary play. The limits are
     * deliberately tiered by activity: ores are rarer than stone, fishing and
     * smelting take longer per item, and logs/crops are faster to gather.
     */
    /**
     * Derives collection quantities from the economic value and real effort of each
     * objective. Tier, material rarity, activity speed, pack cost/profit, objective
     * count, and any timer determine the result; no universal quantity caps are used.
     */
    private Objective rebalanceCombatObjective(char tier, Objective objective, int cost,
            int profit, int timerMinutes, int objectiveCount) {
        String target = objective.target().toUpperCase(Locale.ROOT);
        if (!target.startsWith("KILL:") || target.equals("KILL:@ROAMING_BOSS")) return objective;
        String mob = target.substring("KILL:".length());
        double effort = switch (mob) {
            case "ZOMBIE", "SKELETON", "SPIDER", "CAVE_SPIDER", "CREEPER", "DROWNED", "HUSK", "STRAY" -> 1.0;
            case "ENDERMAN", "WITCH", "SLIME", "ZOMBIFIED_PIGLIN", "PHANTOM" -> 2.5;
            case "PIGLIN" -> 4.0;
            case "PILLAGER", "VINDICATOR", "BLAZE", "GUARDIAN", "HOGLIN", "MAGMA_CUBE" -> 4.0;
            case "WITHER_SKELETON", "SHULKER", "BREEZE", "EVOKER", "RAVAGER", "VEX" -> 5.0;
            case "PIGLIN_BRUTE", "ELDER_GUARDIAN", "WARDEN", "WITHER", "ENDER_DRAGON" -> 7.0;
            default -> 2.0;
        };
        // Monster Level raises mob health and equipment on this server, so combat
        // contracts use a deliberately conservative target instead of raw kill counts.
        double economicBudget = 4.0 + Math.sqrt(Math.max(0, profit)) * 0.90
                + Math.sqrt(Math.max(0, cost)) * 0.20;
        double tierFactor = tier == 'M' ? 0.85 : tier == 'H' ? 0.75 : 0.65;
        double timeFactor = timerMinutes > 0
                ? Math.max(0.45, Math.min(1.0, timerMinutes / 120.0)) : 1.0;
        double sharedObjectiveFactor = 1.0 / Math.sqrt(Math.max(1, objectiveCount));
        int balancedGoal = Math.max(1, (int) Math.round(economicBudget * tierFactor
                * timeFactor * sharedObjectiveFactor / effort));
        if (balancedGoal == objective.goal()) return objective;
        String formattedGoal = String.format(Locale.ROOT, "%,d", balancedGoal);
        return new Objective(objective.target(), balancedGoal,
                objective.description().replaceFirst("\\b[\\d,]+\\b", formattedGoal));
    }

    private Objective rebalanceCollectionObjective(char tier, Objective objective, int cost,
            int profit, int timerMinutes, int objectiveCount) {
        String target = objective.target().toUpperCase(Locale.ROOT);
        String category = target.contains(":") ? target.substring(0, target.indexOf(':')) : target;
        String item = target.contains(":") ? target.substring(target.indexOf(':') + 1).split(":")[0] : target;

        if (!List.of("FISH", "SMELT", "HARVEST", "COLLECT", "BREAK").contains(category)) return objective;

        double unitEffort = collectionEffortPerUnit(category, item);
        if (unitEffort <= 0.0) return objective;

        double economicBudget = 4.0 + Math.sqrt(Math.max(0, profit)) * 0.90
                + Math.sqrt(Math.max(0, cost)) * 0.20;
        double tierFactor = tier == 'M' ? 1.10 : tier == 'H' ? 1.0 : 0.90;
        double timeFactor = timerMinutes > 0
                ? Math.max(0.35, Math.min(1.0, timerMinutes / 60.0)) : 1.0;
        double sharedObjectiveFactor = 1.0 / Math.sqrt(Math.max(1, objectiveCount));
        int effortBasedGoal = (int) Math.round(economicBudget * tierFactor * timeFactor
                * sharedObjectiveFactor / unitEffort);
        int balancedGoal = Math.max(1, effortBasedGoal);
        if (tier == 'M') {
            int masterCeiling = masterCollectionCeiling(category, item);
            if (masterCeiling > 0) balancedGoal = Math.min(balancedGoal, masterCeiling);
        }

        if (balancedGoal == objective.goal()) return objective;
        String formattedGoal = String.format(Locale.ROOT, "%,d", balancedGoal);
        String description = objective.description().replaceFirst("\\b[\\d,]+\\b", formattedGoal);
        return new Objective(objective.target(), balancedGoal, description);
    }


    private int masterCollectionCeiling(String category, String item) {
        if (category.equals("FISH")) return 200;
        if (category.equals("HARVEST")) return 1000;
        if (category.equals("BREAK")) {
            if (item.endsWith("_LOG") || item.endsWith("_STEM")) return 1000;
            if (isOreMaterial(item)) return 300;
            return 1000;
        }
        if (category.equals("SMELT") || category.equals("COLLECT")) {
            return isOreMaterial(item) ? 300 : 700;
        }
        return 700;
    }

    private boolean isOreMaterial(String item) {
        return item.endsWith("_ORE") || List.of("ANCIENT_DEBRIS", "RAW_IRON", "RAW_COPPER",
                "RAW_GOLD", "IRON_INGOT", "COPPER_INGOT", "GOLD_INGOT", "NETHERITE_SCRAP",
                "NETHERITE_INGOT", "COAL", "DIAMOND", "EMERALD", "LAPIS_LAZULI", "REDSTONE", "QUARTZ").contains(item);
    }

    /**
     * Relative work required per unit. Higher values mean fewer items are fair
     * for the same purchase price and profit.
     */
    private double collectionEffortPerUnit(String category, String item) {
        if (category.equals("FISH")) {
            return switch (item) {
                case "COD" -> 0.65;
                case "SALMON" -> 0.85;
                case "PUFFERFISH" -> 2.4;
                case "TROPICAL_FISH" -> 3.2;
                default -> 1.0;
            };
        }
        if (category.equals("HARVEST")) {
            return switch (item) {
                case "MELON_SLICE" -> 0.10;
                case "WHEAT", "CARROT", "POTATO" -> 0.24;
                case "BEETROOT", "PUMPKIN" -> 0.45;
                case "SWEET_BERRIES" -> 0.30;
                case "COCOA_BEANS", "NETHER_WART" -> 0.65;
                default -> 0.35;
            };
        }
        if (category.equals("BREAK")) {
            if (item.equals("DRAGON_HEAD")) return 100.0;
            if (item.equals("SEA_LANTERN")) return 4.0;
            if (item.equals("AMETHYST_CLUSTER")) return 2.5;
            if (item.equals("GILDED_BLACKSTONE")) return 3.0;
            if (item.equals("WET_SPONGE") || item.equals("SPONGE")) return 4.0;
            if (item.endsWith("_LOG")) return 0.28;
            if (item.endsWith("_STEM")) return 0.55;
            if (item.equals("BAMBOO") || item.equals("SUGAR_CANE") || item.equals("KELP")) return 0.12;
            if (item.equals("ANCIENT_DEBRIS")) return 9.0;
            if (item.endsWith("_ORE")) {
                return switch (item) {
                    case "COAL_ORE", "DEEPSLATE_COAL_ORE" -> 0.65;
                    case "COPPER_ORE", "DEEPSLATE_COPPER_ORE" -> 0.75;
                    case "IRON_ORE", "DEEPSLATE_IRON_ORE" -> 0.95;
                    case "GOLD_ORE", "DEEPSLATE_GOLD_ORE", "NETHER_GOLD_ORE" -> 1.35;
                    case "LAPIS_ORE", "DEEPSLATE_LAPIS_ORE", "REDSTONE_ORE",
                            "DEEPSLATE_REDSTONE_ORE" -> 1.45;
                    case "EMERALD_ORE", "DEEPSLATE_EMERALD_ORE" -> 2.2;
                    case "DIAMOND_ORE", "DEEPSLATE_DIAMOND_ORE" -> 3.2;
                    default -> 1.0;
                };
            }
            return switch (item) {
                case "STONE", "COBBLESTONE", "DEEPSLATE", "COBBLED_DEEPSLATE",
                        "NETHERRACK" -> 0.22;
                case "DIRT", "GRAVEL", "SAND", "RED_SAND" -> 0.18;
                case "CLAY" -> 0.38;
                case "PRISMARINE", "DARK_PRISMARINE", "PRISMARINE_BRICKS" -> 1.1;
                case "OBSIDIAN", "CRYING_OBSIDIAN" -> 2.8;
                case "END_STONE" -> 0.65;
                default -> 0.35;
            };
        }
        if (category.equals("SMELT")) {
            double materialEffort = isOreMaterial(item) ? collectionEffortPerUnit("COLLECT", item) : 0.5;
            return Math.max(0.8, materialEffort + 0.55);
        }
        if (category.equals("COLLECT")) {
            return switch (item) {
                case "ANCIENT_DEBRIS", "NETHERITE_SCRAP" -> 8.0;
                case "DIAMOND", "DIAMOND_BLOCK" -> 3.2;
                case "EMERALD", "EMERALD_BLOCK" -> 2.4;
                case "ENDER_PEARL", "BLAZE_ROD", "GHAST_TEAR", "SHULKER_SHELL" -> 1.8;
                case "RAW_IRON", "IRON_INGOT", "RAW_COPPER", "COPPER_INGOT",
                        "RAW_GOLD", "GOLD_INGOT", "QUARTZ", "REDSTONE", "LAPIS_LAZULI" -> 0.9;
                case "BREAD", "COOKED_BEEF", "COOKED_PORKCHOP", "COOKED_CHICKEN" -> 0.35;
                default -> 0.45;
            };
        }
        return 0.5;
    }

    private Material questIconForPack(Pack pack) {
        if (pack.objectives().isEmpty()) return Material.BOOK;
        String target = pack.objectives().get(0).target().toUpperCase(Locale.ROOT);
        if (target.equals("TRAVEL") || target.startsWith("TRAVEL-") || target.startsWith("VISIT-BIOME:")) {
            return target.startsWith("VISIT-BIOME:") ? Material.FILLED_MAP : Material.COMPASS;
        }
        String category = target.contains(":") ? target.substring(0, target.indexOf(':')) : target;
        String targetItem = target.contains(":") ? target.substring(target.indexOf(':') + 1).split(":")[0] : target;
        if (category.equals("KILL")) {
            return switch (targetItem) {
                case "ZOMBIE" -> Material.ZOMBIE_HEAD;
                case "SKELETON" -> Material.SKELETON_SKULL;
                case "WITHER_SKELETON" -> Material.WITHER_SKELETON_SKULL;
                case "CREEPER" -> Material.CREEPER_HEAD;
                case "ENDERMAN" -> Material.ENDER_PEARL;
                case "SPIDER", "CAVE_SPIDER" -> Material.SPIDER_EYE;
                case "BLAZE" -> Material.BLAZE_ROD;
                case "GHAST" -> Material.GHAST_TEAR;
                case "SLIME", "MAGMA_CUBE" -> Material.SLIME_BALL;
                case "DROWNED" -> Material.ROTTEN_FLESH;
                case "WITCH" -> Material.GLASS_BOTTLE;
                case "PIGLIN", "PIGLIN_BRUTE", "ZOMBIFIED_PIGLIN" -> Material.GOLD_INGOT;
                case "HOGLIN" -> Material.PORKCHOP;
                case "PHANTOM" -> Material.PHANTOM_MEMBRANE;
                case "SHULKER" -> Material.SHULKER_SHELL;
                case "PILLAGER", "VINDICATOR", "EVOKER", "RAVAGER", "VEX" -> Material.EMERALD;
                case "GUARDIAN", "ELDER_GUARDIAN" -> Material.PRISMARINE_SHARD;
                case "BREEZE" -> Material.BREEZE_ROD;
                case "BOGGED" -> Material.MOSS_BLOCK;
                case "WARDEN" -> Material.SCULK_CATALYST;
                case "WITHER", "ENDER_DRAGON", "@ROAMING_BOSS" -> Material.NETHER_STAR;
                default -> Material.ROTTEN_FLESH;
            };
        }
        Material exact = Material.matchMaterial(targetItem);
        if (exact != null && exact.isItem() && !exact.isAir()) return exact;
        return switch (category) {
            case "HARVEST" -> Material.WHEAT;
            case "FISH" -> Material.FISHING_ROD;
            case "TRAVEL", "TRAVEL-WORLD", "TRAVEL-BIOME", "VISIT-BIOME" -> Material.COMPASS;
            case "SMELT" -> Material.FURNACE;
            case "BREAK" -> Material.IRON_PICKAXE;
            case "FEED", "BREED" -> Material.WHEAT;
            case "COLLECT" -> Material.CHEST;
            default -> Material.BOOK;
        };
    }

    private String tierOf(Pack pack) {
        return pack.id().startsWith("M") ? "master" : pack.id().startsWith("H") ? "hard" : "easy";
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
            case "hard" -> "Unlock Hard packs by earning the Minecraft advancement Diamonds! (obtain a diamond)";
            case "master" -> "Unlock Master packs by earning Free the End (defeat the Ender Dragon)";
            default -> "Easy packs are available from the start; individual packs have requirements";
        };
    }

    private boolean completedPack(UUID playerId, String packId) {
        return data.getBoolean("completed-packs." + playerId + "." + packId, false);
    }

    private final List<RandomQuest> randomPool = List.of(
        new RandomQuest("Deep Diamond Expedition", Material.DIAMOND, 180, 252, new Objective("break:DIAMOND_ORE", 4, "Mine 4 Diamond Ore")),
        new RandomQuest("Ancient Debris Search", Material.ANCIENT_DEBRIS, 260, 390, new Objective("break:ANCIENT_DEBRIS", 8, "Mine 8 Ancient Debris")),
        new RandomQuest("Raid the Fortress", Material.BLAZE_ROD, 220, 330, new Objective("kill:BLAZE", 18, "Kill 18 Blazes in the Nether")),
        new RandomQuest("Undead Patrol", Material.ZOMBIE_HEAD, 150, 210, new Objective("kill:ZOMBIE", 35, "Kill 35 Zombies")),
        new RandomQuest("Ocean Treasure Hunt", Material.PRISMARINE_SHARD, 200, 300, new Objective("break:PRISMARINE", 24, "Mine 24 Prismarine blocks")),
        new RandomQuest("Ender Pearl Collection", Material.ENDER_PEARL, 240, 360, new Objective("collect:ENDER_PEARL", 12, "Collect 12 Ender Pearls")),
        new RandomQuest("Iron Supply Contract", Material.RAW_IRON, 130, 182, new Objective("break:IRON_ORE", 64, "Mine 64 Iron Ore")),
        new RandomQuest("Wither Skeleton Hunt", Material.WITHER_SKELETON_SKULL, 350, 525, new Objective("kill:WITHER_SKELETON", 16, "Kill 16 Wither Skeletons")),
        new RandomQuest("Fishing for Cod", Material.COD, 90, 126, new Objective("fish:COD", 20, "Catch 20 Cod")),
        new RandomQuest("Fishing for Salmon", Material.SALMON, 110, 154, new Objective("fish:SALMON", 5, "Catch 5 Salmon")),
        new RandomQuest("Feed the Cows", Material.WHEAT, 100, 140, new Objective("feed:COW", 20, "Feed 20 cows")),
        new RandomQuest("Animal Breeding", Material.PORKCHOP, 120, 168, new Objective("breed:ANY", 10, "Breed 10 animals")),
        new RandomQuest("Travel Contract", Material.COMPASS, 100, 140, new Objective("travel", 3000, "Travel 3,000 blocks")),
        new RandomQuest("Woodcutting Contract", Material.OAK_LOG, 100, 140, new Objective("break:OAK_LOG", 16, "Chop 16 Oak Logs")),
        new RandomQuest("Crop Delivery", Material.CARROT, 85, 119, new Objective("harvest:CARROT", 12, "Harvest 12 fully grown Carrot crops")),
        new RandomQuest("Blaze Rod Shipment", Material.BLAZE_ROD, 190, 266, new Objective("collect:BLAZE_ROD", 8, "Collect 8 Blaze Rods")),
        new RandomQuest("Sand Quarry", Material.SAND, 75, 105, new Objective("break:SAND", 32, "Mine 32 Sand")),
        new RandomQuest("Monster Cleanup", Material.SKELETON_SKULL, 140, 196, new Objective("kill:SKELETON", 30, "Kill 30 Skeletons")),
        new RandomQuest("Food Pantry", Material.BREAD, 90, 126, new Objective("collect:BREAD", 16, "Collect 16 Bread")),
        new RandomQuest("Quartz Shipment", Material.QUARTZ, 160, 224, new Objective("collect:QUARTZ", 12, "Collect 12 Nether Quartz")),
        new RandomQuest("Tropical Catch", Material.TROPICAL_FISH, 150, 225, new Objective("fish:TROPICAL_FISH", 4, "Catch 4 Tropical Fish")),
        new RandomQuest("Pufferfish Watch", Material.PUFFERFISH, 135, 203, new Objective("fish:PUFFERFISH", 5, "Catch 5 Pufferfish")),
        new RandomQuest("Cod Fishing Run", Material.FISHING_ROD, 120, 180, new Objective("fish:COD", 16, "Catch 16 Cod")),
        new RandomQuest("Potato Harvest", Material.POTATO, 100, 150, new Objective("harvest:POTATO", 12, "Harvest 12 fully grown Potato crops")),
        new RandomQuest("Pumpkin Season", Material.PUMPKIN, 125, 188, new Objective("harvest:PUMPKIN", 8, "Harvest 8 pumpkins")),
        new RandomQuest("Melon Shipment", Material.MELON_SLICE, 110, 165, new Objective("harvest:MELON_SLICE", 24, "Harvest 24 melon slices")),
        new RandomQuest("Copper Ore Contract", Material.RAW_COPPER, 145, 218, new Objective("break:COPPER_ORE", 12, "Mine 12 Copper Ore")),
        new RandomQuest("Lapis Prospecting", Material.LAPIS_LAZULI, 175, 263, new Objective("break:LAPIS_ORE", 8, "Mine 8 Lapis Lazuli Ore")),
        new RandomQuest("Redstone Extraction", Material.REDSTONE, 165, 248, new Objective("break:REDSTONE_ORE", 10, "Mine 10 Redstone Ore"))
    );

    public QuestManager(XZHEconomySystem plugin) {
        this.plugin = plugin;
        file = new File(plugin.getDataFolder(), "quests.yml");
        data = YamlConfiguration.loadConfiguration(file);
        playerPlacedBlocks.addAll(data.getStringList("anti-exploit.placed-blocks"));
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
        FortuneBookManager fortune = plugin.getFortuneBookManager();
        int bookPrice = fortune.getApplePrice();
        int bookReward = fortune.getAppleReward();
        String cooldown = fortune.getAppleCooldownStatus(player.getUniqueId());
        add(inv, 10, Material.WRITTEN_BOOK, ChatColor.LIGHT_PURPLE + "Fortune Book", "fortune-buy-apple", "book_of_apple",
            List.of(ChatColor.YELLOW + "The Book of Apple",
                    ChatColor.GRAY + "An enchanted apple will be placed in your inventory.",
                    ChatColor.GRAY + "The book will guide you.",
                    ChatColor.DARK_RED + "Every Fortune has a price. Every choice has a consequence.",
                    ChatColor.WHITE + "Price: " + ChatColor.GOLD + bookPrice + " coins",
                    ChatColor.WHITE + "Reward: " + ChatColor.GREEN + bookReward + " bank coins",
                    ChatColor.WHITE + "Odds: " + ChatColor.YELLOW + fortune.getAppleOddsDescription(),
                    ChatColor.WHITE + "Rebuy cooldown: " + ChatColor.AQUA + fortune.getAppleCooldownHours() + " hours",
                    ChatColor.GRAY + "Inventory space required: 2 slots",
                    ChatColor.GRAY + "Rebuy status: " + cooldown,
                    ChatColor.YELLOW + "Click to purchase"));
        add(inv, 13, Material.WRITABLE_BOOK, ChatColor.LIGHT_PURPLE + "Achievement Quest Packs", "section-packs", "packs",
            List.of(ChatColor.GRAY + "Hard multi-objective challenges",
                    ChatColor.GRAY + "Each locked pack explains its unlock requirement"));
        add(inv, 16, Material.NETHER_STAR, ChatColor.GOLD + "Community Chest", "section-global", "global",
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
            appendQuestInfoLore(lore, cost, reward, List.of(offer.objective()),
                    objectiveExplanation(offer.objective()), isTimedSingle(offer) ? 30 : 0,
                    "UNLOCKED", null);
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
            Material icon = tier.equals("easy") ? Material.AXOLOTL_SPAWN_EGG
                    : tier.equals("hard") ? Material.BREEZE_SPAWN_EGG : Material.BAT_SPAWN_EGG;
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
            int reward = rewardForPack(pack);
            int timerMinutes = packTimerLimitMinutes(pack.id());
            String status = done ? "COMPLETED" : unlocked ? "UNLOCKED" : "LOCKED";
            String unlockInstruction = unlocked || done ? null
                    : !tierUnlocked(player, tierOf(pack)) ? tierUnlockDescription(tierOf(pack))
                    : unlockHowTo(pack);
            appendQuestInfoLore(lore, pack.cost(), reward, pack.objectives(), packDescription(pack),
                    timerMinutes, status, unlockInstruction);
            Material icon = done ? Material.BLACK_STAINED_GLASS_PANE : unlocked ? pack.icon() : Material.BARRIER;
            String displayName = (done ? ChatColor.DARK_GRAY : ChatColor.YELLOW) + pack.name();
            int separator = pack.name().indexOf(' ');
            if (separator > 0 && pack.name().startsWith("(")) {
                displayName = (done ? ChatColor.DARK_GRAY : ChatColor.LIGHT_PURPLE) + pack.name().substring(0, separator)
                        + " " + (done ? ChatColor.DARK_GRAY : ChatColor.YELLOW) + pack.name().substring(separator + 1);
            }
            add(inv, i - pg * 45, icon, displayName,
                    done ? "pack-completed" : unlocked ? "pack-select" : "locked", pack.id(), lore);
        }
        if (pg > 0) add(inv, 45, Material.PAPER, ChatColor.WHITE + "Previous page", "pack-page", tier + ":" + (pg - 1));
        add(inv, 49, Material.ARROW, ChatColor.WHITE + "Difficulties", "back-tiers", "");
        if (pg + 1 < pages) add(inv, 53, Material.PAPER, ChatColor.WHITE + "Next page", "pack-page", tier + ":" + (pg + 1));
        player.openInventory(inv);
    }

    private void appendQuestInfoLore(List<String> lore, int cost, int reward,
                                     List<Objective> objectives, String explanation,
                                     int timerMinutes, String status, String unlockInstruction) {
        lore.add(ChatColor.WHITE + "Cost: " + ChatColor.YELLOW + cost + " coins");
        lore.add(ChatColor.WHITE + "Reward: " + ChatColor.GREEN + reward + " coins");
        lore.add(ChatColor.WHITE + "Profit: " + ChatColor.GOLD + "+" + (reward - cost) + " coins");
        for (Objective objective : objectives) {
            appendTwoLineLore(lore, "Job - ", objective.description(), ChatColor.YELLOW, ChatColor.LIGHT_PURPLE);
        }
        appendTwoLineLore(lore, "Objective: ", explanation, ChatColor.WHITE, ChatColor.GRAY);
        if (timerMinutes > 0) {
            lore.add(ChatColor.WHITE + "Time limit: " + ChatColor.RED + timerMinutes + " minutes");
        }
        if ("LOCKED".equals(status)) lore.add(ChatColor.RED + "LOCKED");
        else if ("UNLOCKED".equals(status)) lore.add(ChatColor.GREEN + "UNLOCKED");
        else if ("COMPLETED".equals(status)) lore.add(ChatColor.DARK_GRAY + "COMPLETED");
        if (unlockInstruction != null && !unlockInstruction.isBlank()) {
            lore.add(ChatColor.GRAY + "To unlock: " + unlockInstruction);
        }
    }

    private void appendTwoLineLore(List<String> lore, String prefix, String text,
                                   ChatColor prefixColor, ChatColor textColor) {
        String value = text == null ? "" : text.trim();
        int maxLineLength = 38;
        int firstLineTextLimit = Math.max(8, maxLineLength - prefix.length());
        if (value.length() <= firstLineTextLimit) {
            lore.add(prefixColor + prefix + textColor + value);
            return;
        }
        int splitAt = value.lastIndexOf(' ', Math.min(firstLineTextLimit, value.length() - 1));
        if (splitAt <= 0) splitAt = Math.min(firstLineTextLimit, value.length());
        lore.add(prefixColor + prefix + textColor + value.substring(0, splitAt).trim());
        String remainder = value.substring(splitAt).trim();
        if (!remainder.isEmpty()) lore.add(textColor + "  " + remainder);
    }

    private String objectiveExplanation(Objective objective) {
        String target = objective.target().toLowerCase(Locale.ROOT);
        if (target.startsWith("kill:")) return "Defeat the specified creatures and meet the required count.";
        if (target.startsWith("break:")) return "Mine or gather the requested blocks and materials.";
        if (target.startsWith("harvest:")) return "Harvest the requested fully grown crops.";
        if (target.startsWith("fish:")) return "Catch the requested fish.";
        if (target.startsWith("smelt:")) return "Smelt the requested materials in a furnace.";
        if (target.startsWith("collect:")) return "Collect the requested items.";
        if (target.startsWith("travel")) return "Travel the required distance using legitimate movement.";
        if (target.startsWith("breed:")) return "Breed the requested animals.";
        if (target.startsWith("tame:")) return "Tame the requested animals.";
        if (target.startsWith("feed:")) return "Feed the requested animals.";
        return "Complete the listed task and reach its required amount.";
    }

    private void confirmPack(Player player, Pack pack) {
        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.DARK_RED + "Confirm quest pack");
        List<String> lore = new ArrayList<>();
        appendQuestInfoLore(lore, pack.cost(), rewardForPack(pack), pack.objectives(),
                packDescription(pack), packTimerLimitMinutes(pack.id()), "UNLOCKED", null);
        String displayName = ChatColor.YELLOW + pack.name();
        int separator = pack.name().indexOf(' ');
        if (separator > 0 && pack.name().startsWith("(")) {
            displayName = ChatColor.LIGHT_PURPLE + pack.name().substring(0, separator)
                    + " " + ChatColor.YELLOW + pack.name().substring(separator + 1);
        }
        add(inv, 13, pack.icon(), displayName, "pack-info", pack.id(), lore);
        add(inv, 11, Material.SLIME_BALL, ChatColor.GREEN + "Confirm purchase", "pack-buy", pack.id());
        add(inv, 15, Material.FIRE_CHARGE, ChatColor.RED + "Cancel", "back-packs", pack.id());
        add(inv, 22, Material.ARROW, ChatColor.WHITE + "Back to packs", "back-packs", pack.id());
        player.openInventory(inv);
    }

    public boolean cancelActiveQuest(Player player) {
        UUID id = player.getUniqueId();
        String activeId = data.getString(id + ".active", "");
        if (activeId.isBlank()) {
            plugin.tellQuest(player, "You do not have an active quest or mission to cancel.");
            return false;
        }
        int originalCost = Math.max(0, data.getInt(id + ".cost", 0));
        int refund = originalCost / 2;
        // Clear the active record before refunding so this purchase can never be refunded twice.
        data.set(id.toString(), null);
        clearTravelRemainders(id);
        save();
        plugin.getHudManager().hideActiveQuest(player);
        if (refund > 0) plugin.setBalance(id, plugin.getBalance(id) + refund);
        if (originalCost > 0) {
            plugin.tellQuest(player, "Quest cancelled. Refunded 50% of its purchase price: " + refund
                    + " coins (original price " + originalCost + "). No completion reward was paid.");
        } else {
            plugin.tellQuest(player, "Quest cancelled. It had no purchase price, so no refund or completion reward was paid.");
        }
        return true;
    }

    private void buyRandom(Player player, int offerSlot) {
        UUID id = player.getUniqueId();
        if (plugin.getLoanManager().isShopPurchaseRestricted(id)) { plugin.tellQuest(player, "Your loan is overdue. Quest purchases are locked; sell eligible items and repay your debt."); return; }
        if (!data.getString(id + ".active", "").isBlank()) { plugin.tellQuest(player, "Finish your active quest before buying another."); return; }
        if (offerSlot < 0 || offerSlot > 5) return;
        boolean secret = offerSlot == 5;
        if (secret) refreshSecretQuestIfDue();
        int poolIndex = Math.floorMod(data.getInt(secret ? "single-shop.secret.offer" : "single-shop.offers." + offerSlot, offerSlot), randomPool.size());
        RandomQuest template = randomPool.get(poolIndex);
        int cost = secret ? data.getInt("single-shop.secret.cost", 250) : Math.max(50, (int) Math.round(template.cost() * (0.90 + random.nextDouble() * 0.21)));
        int margin = secret ? data.getInt("single-shop.secret.margin", 40) : 40 + random.nextInt(21);
        int reward = (int) Math.ceil(cost * (1.0 + margin / 100.0));
        int timerMinutes = isTimedSingle(template) ? 30 : 0;
        char economicTier = reward - cost >= 180 ? 'M' : reward - cost >= 90 ? 'H' : 'E';
        Objective objective = rebalanceCollectionObjective(economicTier, template.objective(),
                cost, reward - cost, timerMinutes, 1);
        if (plugin.getCombinedBalance(id) < cost || !plugin.spendAvailableFunds(id, cost)) { plugin.tellQuest(player, "You need " + cost + " coins to buy this random quest."); return; }
        String questName = template.name() + " #" + (data.getInt("single-shop.quest-sequence", 0) + 1);
        data.set("single-shop.quest-sequence", data.getInt("single-shop.quest-sequence", 0) + 1);
        startQuest(player, "single:" + System.currentTimeMillis(), questName, cost, reward, List.of(objective));
        data.set(id + ".profit-margin", margin);
        if (!secret) data.set("single-shop.offers." + offerSlot, nextOfferForSlot(offerSlot));
        save();
        plugin.tellQuest(player, "Started " + questName + ": " + objective.description() + ". Cost " + cost + "; reward " + reward + " coins. " + (isTimedSingle(template) ? "Time limit: 30 minutes." : "No timer."));
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
            plugin.tellQuest(player, "You have already completed this achievement pack. It can only be completed once.");
            openPacks(player);
            return;
        }
        if (plugin.getLoanManager().isShopPurchaseRestricted(id)) {
            plugin.tellQuest(player, "Your loan is overdue. Quest-pack purchases are locked until full repayment.");
            return;
        }
        if (!tierUnlocked(player, tierOf(pack))) {
            plugin.tellQuest(player, tierUnlockDescription(tierOf(pack)));
            return;
        }
        if (!hasAdvancement(player, pack.advancement())) {
            plugin.tellQuest(player, "Unlock " + pack.unlockTitle() + ": " + unlockHowTo(pack));
            return;
        }
        if (!data.getString(id + ".active", "").isBlank()) {
            plugin.tellQuest(player, "Finish your active quest before buying another.");
            return;
        }
        if (plugin.getCombinedBalance(id) < pack.cost() || !plugin.spendAvailableFunds(id, pack.cost())) {
            plugin.tellQuest(player, "Not enough coins for this quest pack.");
            return;
        }
        startQuest(player, "pack:" + pack.id(), pack.name(), pack.cost(), rewardForPack(pack), pack.objectives());
        player.closeInventory();
        plugin.tellQuest(player, "Quest pack started: " + pack.name() + ". Complete every objective to earn " + pack.reward() + " coins.");
    }

    private void startQuest(Player player, String id, String name, int cost, int reward, List<Objective> objectives) {
        UUID uuid = player.getUniqueId();
        clearTravelRemainders(uuid);
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
        data.set(uuid + ".paused-at", null);
        data.set(uuid + ".warned-5m", false);
        data.set(uuid + ".warned-1m", false);
        data.set(uuid + ".warned-5m", false);
        data.set(uuid + ".warned-1m", false);
        save();
        updateHud(player);
        player.sendTitle(ChatColor.GREEN + "QUEST STARTED", ChatColor.WHITE + name, 10, 60, 10);
        player.playSound(player.getLocation(), Sound.UI_TOAST_IN, 0.85f, 1.0f);
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        if (event.isCancelled()) return;
        Player p = event.getPlayer();
        if (p.getGameMode() == org.bukkit.GameMode.CREATIVE || p.getGameMode() == org.bukkit.GameMode.SPECTATOR) return;
        Material material = event.getBlock().getType();
        String id = material.name();
        boolean playerPlaced = playerPlacedBlocks.remove(blockKey(event.getBlock().getLocation()));
        if (playerPlaced) savePlacedBlockTracking();
        if (!playerPlaced) incrementTarget(p, "break:" + id, 1);
        // Deepslate ore variants count toward the matching ore contracts too.
        if (!playerPlaced && id.startsWith("DEEPSLATE_") && id.endsWith("_ORE")) {
            incrementTarget(p, "break:" + id.substring("DEEPSLATE_".length()), 1);
        }
        if (!playerPlaced && (material.name().endsWith("_ORE") || material == Material.ANCIENT_DEBRIS)) {
            incrementTarget(p, "break:ANY_ORE", 1);
            recordGlobalProgress(p, "ore", 1);
        }
        if (!playerPlaced && (material.name().endsWith("_LOG") || material.name().endsWith("_STEM"))) {
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
                case SWEET_BERRY_BUSH -> Material.SWEET_BERRIES;
                default -> null;
            };
            if (harvested != null) {
                incrementTarget(p, "harvest:" + harvested.name(), 1);
                matureCrop = true;
            }
        }
        // Glow berries grow on cave vines rather than Ageable crop blocks.
        if ((material == Material.CAVE_VINES || material == Material.CAVE_VINES_PLANT)
                && event.getBlock().getBlockData().getAsString().contains("berries=true")) {
            incrementTarget(p, "harvest:GLOW_BERRIES", 1);
            matureCrop = true;
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
    public void onPlace(BlockPlaceEvent event) {
        if (event.isCancelled()) return;
        Material type = event.getBlockPlaced().getType();
        String name = type.name();
        // Only retain locations for rare or structure-dependent block objectives.
        if (name.endsWith("_ORE") || type == Material.ANCIENT_DEBRIS
                || type == Material.DRAGON_HEAD || type == Material.WET_SPONGE
                || type == Material.SPONGE || type == Material.SEA_LANTERN
                || type == Material.AMETHYST_CLUSTER || type == Material.GILDED_BLACKSTONE) {
            playerPlacedBlocks.add(blockKey(event.getBlockPlaced().getLocation()));
            savePlacedBlockTracking();
        }
    }

    private void savePlacedBlockTracking() {
        data.set("anti-exploit.placed-blocks", new ArrayList<>(playerPlacedBlocks));
        save();
    }

    private String blockKey(Location location) {
        return location.getWorld().getUID() + ":" + location.getBlockX() + ":"
                + location.getBlockY() + ":" + location.getBlockZ();
    }

    @EventHandler
    public void onKill(EntityDeathEvent event) {
        Player p = event.getEntity().getKiller();
        if (p == null || p.getGameMode() == org.bukkit.GameMode.CREATIVE || p.getGameMode() == org.bukkit.GameMode.SPECTATOR) return;
        incrementTarget(p, "kill:" + event.getEntityType().name(), 1);
        // XZHSurvivalPlugin tags roaming bosses in persistent data; recognize that marker
        // without depending on the other plugin's Java classes.
        if (event.getEntity().getPersistentDataContainer().getKeys().stream()
                .anyMatch(key -> key.getKey().equalsIgnoreCase("roaming_boss"))) {
            incrementTarget(p, "kill:@ROAMING_BOSS", 1);
        }
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
        incrementTarget(player, "smelt:" + material.name(), amount);
        incrementTarget(player, "smelt:ANY", amount);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (event instanceof PlayerTeleportEvent) {
            clearTravelRemainders(player.getUniqueId());
            return;
        }
        if (event.isCancelled() || event.getTo() == null) return;
        // Boats and other Bukkit vehicles are tracked by VehicleMoveEvent to avoid double-counting.
        if (player.isInsideVehicle() && player.getVehicle() instanceof Vehicle) return;
        recordTravelMovement(player, event.getFrom(), event.getTo());
    }

    @EventHandler
    public void onVehicleMove(VehicleMoveEvent event) {
        if (event.getFrom().getWorld() != event.getTo().getWorld()) return;
        recordVehiclePassengers(event.getVehicle(), event.getFrom(), event.getTo());
    }

    private void recordVehiclePassengers(Entity vehicle, Location from, Location to) {
        for (Entity passenger : vehicle.getPassengers()) {
            if (passenger instanceof Player player) {
                recordTravelMovement(player, from, to);
            } else {
                recordVehiclePassengers(passenger, from, to);
            }
        }
    }

    private void recordTravelMovement(Player player, Location from, Location to) {
        UUID id = player.getUniqueId();
        if (from == null || to == null || from.getWorld() == null || to.getWorld() == null
                || from.getWorld() != to.getWorld()) {
            clearTravelRemainders(id);
            return;
        }
        if (player.getGameMode() == org.bukkit.GameMode.CREATIVE
                || player.getGameMode() == org.bukkit.GameMode.SPECTATOR
                || data.getString(id + ".active", "").isBlank()) return;

        double step = from.distance(to);
        // Reject abnormal same-world jumps while allowing fast Elytra and vehicle movement.
        if (step <= 0.0 || step > 32.0) {
            if (step > 32.0) clearTravelRemainders(id);
            return;
        }

        Biome fromBiome = from.getBlock().getBiome();
        Biome toBiome = to.getBlock().getBiome();
        if (fromBiome != toBiome) {
            incrementTarget(player, "visit-biome:" + toBiome.name(), 1);
            if (data.getString(id + ".active", "").isBlank()) return;
        }

        int count = data.getInt(id + ".objective-count", 0);
        Set<String> matchingDistanceTargets = new HashSet<>();
        for (int i = 0; i < count; i++) {
            String target = data.getString(id + ".objectives." + i + ".target", "");
            if (target.equals("travel")) {
                matchingDistanceTargets.add(target);
            } else if (target.startsWith("travel-world:")) {
                String environment = target.substring("travel-world:".length());
                if (environment.equalsIgnoreCase(to.getWorld().getEnvironment().name())) {
                    matchingDistanceTargets.add(target);
                }
            } else if (target.startsWith("travel-biome:")) {
                String biome = target.substring("travel-biome:".length());
                if (biome.equalsIgnoreCase(toBiome.name()) && fromBiome == toBiome) {
                    matchingDistanceTargets.add(target);
                }
            }
        }
        for (String target : matchingDistanceTargets) {
            String remainderKey = id + ":" + target;
            double accumulated = travelRemainders.getOrDefault(remainderKey, 0.0) + step;
            int wholeBlocks = (int) Math.floor(accumulated);
            travelRemainders.put(remainderKey, accumulated - wholeBlocks);
            if (wholeBlocks > 0) incrementTarget(player, target, wholeBlocks);
        }
    }

    private void clearTravelRemainders(UUID playerId) {
        String prefix = playerId + ":";
        travelRemainders.keySet().removeIf(key -> key.startsWith(prefix));
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
        if (player.getGameMode() == org.bukkit.GameMode.CREATIVE || player.getGameMode() == org.bukkit.GameMode.SPECTATOR) return;
        incrementTarget(player, "breed:ANY", 1);
        incrementTarget(player, "breed:" + event.getEntity().getType().name(), 1);
    }

    @EventHandler
    public void onTame(EntityTameEvent event) {
        if (!(event.getOwner() instanceof Player player)) return;
        if (player.getGameMode() == org.bukkit.GameMode.CREATIVE || player.getGameMode() == org.bukkit.GameMode.SPECTATOR) return;
        incrementTarget(player, "tame:" + event.getEntity().getType().name(), 1);
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
            String completedQuestName = data.getString(id + ".name", "Quest");
            player.sendTitle(ChatColor.GOLD + "QUEST COMPLETED", ChatColor.YELLOW + completedQuestName, 10, 70, 20);
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            String activeQuestId = data.getString(id + ".active", "");
            if (activeQuestId.startsWith("pack:")) {
                String packId = activeQuestId.substring("pack:".length());
                data.set("completed-packs." + id + "." + packId, true);
                plugin.tellQuest(player, "§6Achievement pack permanently completed: §e" + packId + "§6. It is now marked with black stained glass and cannot be repeated.");
            }
            plugin.setBalance(id, plugin.getBalance(id) + reward);
            plugin.tellQuest(player, "Quest completed! Reward: " + reward + " coins.");
            data.set(id.toString(), null);
            clearTravelRemainders(id);
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
        if (id.matches("[EHM]\\d{3}")) return pack.reward();
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
        return packs.stream().filter(pack -> pack.id().equals(packId))
                .mapToInt(Pack::timerMinutes).findFirst().orElse(0);
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

    @EventHandler
    public void onQuestQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        expireTimedQuestIfNeeded(player);
        UUID id = player.getUniqueId();
        String active = data.getString(id + ".active", "");
        if (timerLimitSeconds(id, active) > 0 && !active.isBlank()) {
            data.set(id + ".paused-at", System.currentTimeMillis());
            save();
        }
    }

    @EventHandler
    public void onQuestJoin(PlayerJoinEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        long pausedAt = data.getLong(id + ".paused-at", 0L);
        if (pausedAt <= 0L) return;
        String active = data.getString(id + ".active", "");
        if (timerLimitSeconds(id, active) > 0 && !active.isBlank()) {
            long offlineDuration = Math.max(0L, System.currentTimeMillis() - pausedAt);
            long startedAt = data.getLong(id + ".started-at", System.currentTimeMillis());
            data.set(id + ".started-at", startedAt + offlineDuration);
        }
        data.set(id + ".paused-at", null);
        save();
        updateHud(event.getPlayer());
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
            plugin.tellQuest(player, "Quest timer expired. The quest was forfeited; choose another challenge.");
            return;
        }
        if (remaining <= 5L * 60L && !data.getBoolean(id + ".warned-5m", false)) {
            data.set(id + ".warned-5m", true);
            save();
            plugin.tellQuest(player, "Quest warning: 5 minutes or less remain on your timed quest!");
        }
        if (remaining <= 60L && !data.getBoolean(id + ".warned-1m", false)) {
            data.set(id + ".warned-1m", true);
            save();
            plugin.tellQuest(player, "Quest warning: 1 minute or less remains on your timed quest!");
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
        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.DARK_PURPLE + "XZH Community Chest");
        add(inv, 13, Material.NETHER_STAR, ChatColor.GOLD + data.getString("event.name", "Community Chest"), "global-info", "global",
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
                    if (online != null) plugin.tellQuest(online, "§aGlobal quest reward received: " + reward + " coins for contributing to " + data.getString("event.name", "the community chest") + ".");
                    paid++;
                } catch (IllegalArgumentException ignored) { }
            }
        }
        Bukkit.broadcastMessage(ChatColor.GOLD + "[quest] " + ChatColor.GREEN
                + data.getString("event.name", "Community Chest") + " completed! " + paid
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
        return "§6Community Chest: §f" + objective + " §8(" + percent + "%, " + formatGlobalTime(remaining) + ")";
    }

    private void broadcastGlobalProgress() {
        resetEventIfNeeded();
        if (data.getBoolean("event.completed", false)) return;
        String message = ChatColor.GOLD + "[quest] " + ChatColor.YELLOW
            + data.getString("event.name", "Community Chest") + ": " + data.getInt("event.progress", 0) + "/"
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
            Bukkit.broadcastMessage(ChatColor.GOLD + "[quest] Five new random single-quest offers are available!");
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
        if (target.equals("travel") || target.startsWith("travel-") || target.startsWith("visit-biome:")) return "travel";
        if (target.startsWith("feed:") || target.startsWith("breed:") || target.startsWith("tame:")) return "farming";
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

    private String unlockHowTo(Pack pack) {
        return unlockHowTo(pack.advancement(), pack.unlockTitle());
    }

    private String unlockHowTo(String advancement, String title) {
        return switch (advancement) {
            case "minecraft:story/mine_stone" -> "Mine stone with a pickaxe to earn Stone Age.";
            case "minecraft:story/upgrade_tools" -> "Craft a stone pickaxe to earn Getting an Upgrade.";
            case "minecraft:story/smelt_iron" -> "Smelt an iron ingot.";
            case "minecraft:story/iron_tools" -> "Craft an iron pickaxe.";
            case "minecraft:husbandry/plant_seed" -> "Plant a seed and grow a crop.";
            case "minecraft:husbandry/breed_an_animal" -> "Breed two animals.";
            case "minecraft:husbandry/tame_an_animal" -> "Tame an animal.";
            case "minecraft:husbandry/fishy_business" -> "Catch a fish with a fishing rod.";
            case "minecraft:husbandry/tactical_fishing" -> "Catch a fish using a water bucket.";
            case "minecraft:adventure/kill_a_mob" -> "Defeat a hostile mob.";
            case "minecraft:adventure/trade" -> "Trade with a villager.";
            case "minecraft:story/form_obsidian" -> "Obtain obsidian.";
            case "minecraft:story/enchant_item" -> "Enchant an item at an enchanting table.";
            case "minecraft:story/obtain_armor" -> "Equip a piece of armor.";
            case "minecraft:story/mine_diamond" -> "Obtain a diamond.";
            case "minecraft:nether/root" -> "Enter the Nether.";
            case "minecraft:nether/obtain_ancient_debris" -> "Obtain Ancient Debris.";
            case "minecraft:nether/obtain_crying_obsidian" -> "Obtain Crying Obsidian.";
            case "minecraft:nether/obtain_blaze_rod" -> "Obtain a Blaze Rod.";
            case "minecraft:nether/return_to_sender" -> "Complete Return to Sender by sending a Ghast fireball back.";
            case "minecraft:nether/find_bastion" -> "Find and enter a Bastion Remnant.";
            case "minecraft:adventure/shoot_arrow" -> "Complete the Take Aim advancement; its target requirement is shown in your Advancements menu.";
            case "minecraft:story/shiny_gear" -> "Equip a full set of diamond armor.";
            case "minecraft:story/follow_ender_eye" -> "Use an Eye of Ender to locate a stronghold.";
            case "minecraft:adventure/voluntary_exile" -> "Defeat a raid captain.";
            case "minecraft:adventure/sleep_in_bed" -> "Sleep in a bed to set your respawn point.";
            case "minecraft:nether/ride_strider" -> "Ride a Strider using a Warped Fungus on a Stick.";
            case "minecraft:end/enter_end_gateway" -> "Enter an End Gateway.";
            case "minecraft:end/find_end_city" -> "Find and enter an End City.";
            case "minecraft:end/elytra" -> "Obtain an Elytra.";
            case "minecraft:nether/find_fortress" -> "Find and enter a Nether Fortress.";
            case "minecraft:nether/distract_piglin" -> "Distract Piglins with gold.";
            case "minecraft:nether/get_wither_skull" -> "Obtain a Wither Skeleton Skull.";
            case "minecraft:nether/loot_bastion" -> "Open a chest in a Bastion Remnant.";
            case "minecraft:nether/summon_wither" -> "Summon the Wither.";
            case "minecraft:end/respawn_dragon" -> "Respawn the Ender Dragon using End Crystals.";
            case "minecraft:adventure/avoid_vibration" -> "Complete Sneak 100; its in-game requirement is shown in your Advancements menu.";
            case "minecraft:adventure/totem_of_undying" -> "Use a Totem of Undying to survive a fatal hit.";
            case "minecraft:adventure/hero_of_the_village" -> "Complete a raid and earn Hero of the Village.";
            case "minecraft:adventure/minecraft_trials_edition" -> "Find and enter a Trial Chamber.";
            case "minecraft:adventure/under_lock_and_key" -> "Unlock a Vault with a Trial Key.";
            case "minecraft:adventure/revaulting" -> "Unlock a Vault that another player has already opened.";
            case "minecraft:adventure/overoverkill" -> "Complete Over-Overkill; its in-game requirement is shown in your Advancements menu.";
            case "minecraft:husbandry/bred_all_animals" -> "Breed every required animal type.";
            case "minecraft:husbandry/balanced_diet" -> "Eat every food required by A Balanced Diet.";
            case "minecraft:nether/fast_travel" -> "Use the Nether to travel between two Overworld locations at least 7 km apart.";
            default -> "Earn the Minecraft advancement \"" + title + "\". Check the in-game Advancements menu for its requirement.";
        };
    }

    private String packDescription(Pack pack) {
        if (pack.objectives().size() > 1) return "Complete every objective listed below to finish this challenge.";
        if (pack.objectives().isEmpty()) return "Complete this challenge's listed requirements.";
        String target = pack.objectives().get(0).target().toLowerCase(Locale.ROOT);
        if (target.startsWith("kill:")) return "Defeat the specified creatures and meet the required count.";
        if (target.startsWith("travel-biome:")) return "Cover the required distance while travelling inside the named biome.";
        if (target.startsWith("visit-biome:")) return "Reach the named biome. Entering it counts; first-time discovery is not required.";
        if (target.startsWith("travel-world:")) return "Explore the required dimension and cover the listed distance there.";
        if (target.equals("travel")) return "Explore the world and cover the required distance using any legitimate travel method.";
        if (target.startsWith("break:")) return "Gather the requested blocks and materials.";
        if (target.startsWith("harvest:")) return "Harvest the requested fully grown crops.";
        if (target.startsWith("fish:")) return "Catch the requested fish.";
        if (target.startsWith("smelt:")) return "Process the requested materials in a furnace.";
        if (target.startsWith("collect:")) return "Collect the requested items.";
        if (target.startsWith("breed:")) return "Breed the requested animals to complete the objective.";
        if (target.startsWith("tame:")) return "Tame the requested animals to complete the objective.";
        if (target.startsWith("feed:")) return "Feed the requested animals to complete the objective.";
        if (target.startsWith("feed:")) return "Feed the requested animals.";
        return "Complete the objective shown below.";
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
            Bukkit.broadcastMessage(ChatColor.GOLD + "[quest] " + ChatColor.YELLOW
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
                || title.equals("XZH Community Chest"))) return;
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
            case "fortune-buy-apple" -> plugin.getFortuneBookManager().purchaseApple(player);
            case "section-packs" -> openPacks(player);
            case "section-global" -> openGlobal(player);
            case "single-buy" -> { try { buyRandom(player, Integer.parseInt(value)); } catch (NumberFormatException ignored) { } }
            case "single-secret-buy" -> buyRandom(player, 5);
            case "single-info" -> plugin.tellQuest(player, "Five random quests are available; each purchase replaces its offer slot.");
            case "pack-tier" -> openPacks(player, value, 0);
            case "tier-locked" -> {
                plugin.tellQuest(player, tierUnlockDescription(value));
                openPacks(player, value, 0);
            }
            case "pack-page" -> {
                String[] parts = value.split(":");
                try { openPacks(player, parts[0], Integer.parseInt(parts[1])); }
                catch (RuntimeException ignored) { openPacks(player); }
            }
            case "back-tiers" -> openPacks(player);
            case "pack-completed" -> plugin.tellQuest(player, "This achievement pack is permanently completed and cannot reward you again.");
            case "locked" -> packs.stream().filter(p -> p.id().equals(value)).findFirst().ifPresent(p -> plugin.tellQuest(player, "Unlock " + p.unlockTitle() + ": " + unlockHowTo(p)));
            case "pack-select" -> packs.stream().filter(p -> p.id().equals(value)).findFirst().ifPresent(p -> confirmPack(player, p));
            case "pack-buy" -> packs.stream().filter(p -> p.id().equals(value)).findFirst().ifPresent(p -> buyPack(player, p));
            case "global-claim" -> plugin.tellQuest(player, "Global quest rewards are paid automatically to contributors when the community completes the objective.");
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
