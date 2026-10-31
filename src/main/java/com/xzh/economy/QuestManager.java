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
    private record Pack(String id, String name, Material icon, int cost, int reward, String advancement, String unlockTitle, int timerMinutes, List<Objective> objectives) {}
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

    private final List<Pack> packs = buildPacks();

    private List<Pack> buildPacks() {
        List<Pack> result = new ArrayList<>(300);
        result.add(new Pack("E001", "Stonecutter's Warm-Up", Material.IRON_PICKAXE, 50, 210, "minecraft:story/mine_stone", "Stone Age", 30, List.of(new Objective("break:STONE", 500, "Mine 500 Stone"))));
        result.add(new Pack("E002", "Coal Seam Crew", Material.IRON_PICKAXE, 50, 210, "minecraft:story/upgrade_tools", "Getting an Upgrade", 0, List.of(new Objective("break:COAL_ORE", 96, "Mine 96 Coal Ore"))));
        result.add(new Pack("E003", "Copper Crawl", Material.IRON_PICKAXE, 90, 260, "minecraft:story/upgrade_tools", "Getting an Upgrade", 0, List.of(new Objective("break:COPPER_ORE", 96, "Mine 96 Copper Ore"))));
        result.add(new Pack("E004", "Iron Vein Run", Material.IRON_PICKAXE, 130, 340, "minecraft:story/smelt_iron", "Acquire Hardware", 40, List.of(new Objective("break:IRON_ORE", 64, "Mine 64 Iron Ore"))));
        result.add(new Pack("E005", "Gravel Pit Shift", Material.IRON_PICKAXE, 50, 210, "minecraft:story/mine_stone", "Stone Age", 30, List.of(new Objective("break:GRAVEL", 640, "Mine 640 Gravel"))));
        result.add(new Pack("E006", "Dune Digger", Material.IRON_PICKAXE, 50, 210, "minecraft:story/mine_stone", "Stone Age", 30, List.of(new Objective("break:SAND", 896, "Mine 896 Sand"))));
        result.add(new Pack("E007", "Clay Banks", Material.IRON_PICKAXE, 130, 330, "minecraft:story/upgrade_tools", "Getting an Upgrade", 0, List.of(new Objective("break:CLAY", 160, "Mine 160 Clay"))));
        result.add(new Pack("E008", "Granite Gang", Material.IRON_PICKAXE, 50, 210, "minecraft:story/upgrade_tools", "Getting an Upgrade", 0, List.of(new Objective("break:GRANITE", 640, "Mine 640 Granite"))));
        result.add(new Pack("E009", "Redstone Rush", Material.IRON_PICKAXE, 130, 340, "minecraft:story/smelt_iron", "Acquire Hardware", 45, List.of(new Objective("break:REDSTONE_ORE", 80, "Mine 80 Redstone Ore"))));
        result.add(new Pack("E010", "Lapis Lodestar", Material.IRON_PICKAXE, 170, 380, "minecraft:story/iron_tools", "Isn't It Iron Pick", 0, List.of(new Objective("break:LAPIS_ORE", 48, "Mine 48 Lapis Ore"))));
        result.add(new Pack("E011", "Lumberjack Basics", Material.IRON_PICKAXE, 50, 210, "minecraft:story/mine_stone", "Stone Age", 30, List.of(new Objective("break:OAK_LOG", 192, "Mine 192 Oak Log"))));
        result.add(new Pack("E012", "Birch Brigade", Material.IRON_PICKAXE, 90, 260, "minecraft:story/upgrade_tools", "Getting an Upgrade", 0, List.of(new Objective("break:BIRCH_LOG", 128, "Mine 128 Birch Log"))));
        result.add(new Pack("E013", "Spruce Supply", Material.IRON_PICKAXE, 90, 260, "minecraft:story/upgrade_tools", "Getting an Upgrade", 0, List.of(new Objective("break:SPRUCE_LOG", 128, "Mine 128 Spruce Log"))));
        result.add(new Pack("E014", "Dark Forest Cutter", Material.IRON_PICKAXE, 130, 320, "minecraft:story/iron_tools", "Isn't It Iron Pick", 0, List.of(new Objective("break:DARK_OAK_LOG", 96, "Mine 96 Dark Oak Log"))));
        result.add(new Pack("E015", "Jungle Chopper", Material.IRON_PICKAXE, 130, 330, "minecraft:story/iron_tools", "Isn't It Iron Pick", 0, List.of(new Objective("break:JUNGLE_LOG", 96, "Mine 96 Jungle Log"))));
        result.add(new Pack("E016", "Savanna Sawyer", Material.IRON_PICKAXE, 130, 320, "minecraft:story/iron_tools", "Isn't It Iron Pick", 0, List.of(new Objective("break:ACACIA_LOG", 96, "Mine 96 Acacia Log"))));
        result.add(new Pack("E017", "Cherry Grove Grind", Material.IRON_PICKAXE, 170, 370, "minecraft:husbandry/plant_seed", "A Seedy Place", 0, List.of(new Objective("break:CHERRY_LOG", 64, "Mine 64 Cherry Log"))));
        result.add(new Pack("E018", "Mangrove Marsh Crew", Material.IRON_PICKAXE, 170, 370, "minecraft:story/iron_tools", "Isn't It Iron Pick", 0, List.of(new Objective("break:MANGROVE_LOG", 64, "Mine 64 Mangrove Log"))));
        result.add(new Pack("E019", "Bamboo Thicket", Material.IRON_PICKAXE, 90, 260, "minecraft:husbandry/plant_seed", "A Seedy Place", 0, List.of(new Objective("break:BAMBOO", 384, "Mine 384 Bamboo"))));
        result.add(new Pack("E020", "Pale Garden Harvest", Material.IRON_PICKAXE, 210, 440, "minecraft:story/iron_tools", "Isn't It Iron Pick", 0, List.of(new Objective("break:PALE_OAK_LOG", 80, "Mine 80 Pale Oak Log"))));
        result.add(new Pack("E021", "Wheat Weekend", Material.WHEAT, 50, 220, "minecraft:husbandry/plant_seed", "A Seedy Place", 0, List.of(new Objective("harvest:WHEAT", 192, "Harvest 192 Wheat"))));
        result.add(new Pack("E022", "Carrot Cartel", Material.WHEAT, 50, 220, "minecraft:husbandry/plant_seed", "A Seedy Place", 0, List.of(new Objective("harvest:CARROT", 160, "Harvest 160 Carrot"))));
        result.add(new Pack("E023", "Potato Patch", Material.WHEAT, 50, 220, "minecraft:husbandry/plant_seed", "A Seedy Place", 0, List.of(new Objective("harvest:POTATO", 160, "Harvest 160 Potato"))));
        result.add(new Pack("E024", "Beetroot Brigade", Material.WHEAT, 90, 270, "minecraft:husbandry/plant_seed", "A Seedy Place", 0, List.of(new Objective("harvest:BEETROOT", 128, "Harvest 128 Beetroot"))));
        result.add(new Pack("E025", "Melon Madness", Material.WHEAT, 90, 270, "minecraft:husbandry/plant_seed", "A Seedy Place", 0, List.of(new Objective("harvest:MELON_SLICE", 128, "Harvest 128 Melon Slice"))));
        result.add(new Pack("E026", "Pumpkin Parade", Material.WHEAT, 90, 260, "minecraft:husbandry/plant_seed", "A Seedy Place", 0, List.of(new Objective("harvest:PUMPKIN", 64, "Harvest 64 Pumpkin"))));
        result.add(new Pack("E027", "Sweet Berry Picker", Material.WHEAT, 130, 320, "minecraft:husbandry/plant_seed", "A Seedy Place", 0, List.of(new Objective("harvest:WHEAT", 128, "Harvest 128 Wheat"))));
        result.add(new Pack("E028", "Cocoa Grove", Material.WHEAT, 130, 320, "minecraft:husbandry/plant_seed", "A Seedy Place", 0, List.of(new Objective("harvest:COCOA_BEANS", 96, "Harvest 96 Cocoa Beans"))));
        result.add(new Pack("E029", "Sugar Cane Run", Material.IRON_PICKAXE, 90, 260, "minecraft:husbandry/plant_seed", "A Seedy Place", 0, List.of(new Objective("break:SUGAR_CANE", 384, "Mine 384 Sugar Cane"))));
        result.add(new Pack("E030", "Kelp Farm Shift", Material.IRON_PICKAXE, 90, 260, "minecraft:husbandry/plant_seed", "A Seedy Place", 0, List.of(new Objective("break:KELP", 320, "Mine 320 Kelp"))));
        result.add(new Pack("E031", "Cow Whisperer", Material.WHEAT, 50, 210, "minecraft:husbandry/breed_an_animal", "The Parrots and the Bats", 0, List.of(new Objective("harvest:WHEAT", 96, "Harvest 96 Wheat"))));
        result.add(new Pack("E032", "Shepherd's Pride", Material.WHEAT, 90, 270, "minecraft:husbandry/breed_an_animal", "The Parrots and the Bats", 0, List.of(new Objective("harvest:WHEAT", 48, "Harvest 48 Wheat"))));
        result.add(new Pack("E033", "Piggy Bank", Material.WHEAT, 90, 270, "minecraft:husbandry/breed_an_animal", "The Parrots and the Bats", 0, List.of(new Objective("harvest:WHEAT", 48, "Harvest 48 Wheat"))));
        result.add(new Pack("E034", "Coop Commander", Material.WHEAT, 90, 270, "minecraft:husbandry/breed_an_animal", "The Parrots and the Bats", 0, List.of(new Objective("harvest:WHEAT", 64, "Harvest 64 Wheat"))));
        result.add(new Pack("E035", "Herd Builder", Material.WHEAT, 90, 330, "minecraft:husbandry/breed_an_animal", "The Parrots and the Bats", 0, List.of(new Objective("harvest:WHEAT", 120, "Harvest 120 Wheat"))));
        result.add(new Pack("E036", "Rabbit Ranch", Material.WHEAT, 130, 330, "minecraft:husbandry/breed_an_animal", "The Parrots and the Bats", 0, List.of(new Objective("harvest:WHEAT", 40, "Harvest 40 Wheat"))));
        result.add(new Pack("E037", "Cat Companion", Material.WHEAT, 130, 310, "minecraft:husbandry/tame_an_animal", "Best Friends Forever", 0, List.of(new Objective("harvest:WHEAT", 32, "Harvest 32 Wheat"))));
        result.add(new Pack("E038", "Wolf Pack", Material.WHEAT, 130, 330, "minecraft:husbandry/tame_an_animal", "Best Friends Forever", 0, List.of(new Objective("harvest:WHEAT", 32, "Harvest 32 Wheat"))));
        result.add(new Pack("E039", "Parrot Pals", Material.WHEAT, 170, 390, "minecraft:husbandry/tame_an_animal", "Best Friends Forever", 0, List.of(new Objective("harvest:WHEAT", 32, "Harvest 32 Wheat"))));
        result.add(new Pack("E040", "Turtle Beach", Material.WHEAT, 210, 440, "minecraft:husbandry/breed_an_animal", "The Parrots and the Bats", 0, List.of(new Objective("harvest:WHEAT", 32, "Harvest 32 Wheat"))));
        result.add(new Pack("E041", "First Catch Club", Material.FISHING_ROD, 50, 230, "minecraft:husbandry/fishy_business", "Fishy Business", 0, List.of(new Objective("fish:COD", 24, "Catch 24 Cod"))));
        result.add(new Pack("E042", "Cod Quota", Material.FISHING_ROD, 50, 230, "minecraft:husbandry/fishy_business", "Fishy Business", 0, List.of(new Objective("fish:COD", 30, "Catch 30 Cod"))));
        result.add(new Pack("E043", "Salmon Run", Material.FISHING_ROD, 90, 280, "minecraft:husbandry/fishy_business", "Fishy Business", 0, List.of(new Objective("fish:SALMON", 16, "Catch 16 Salmon"))));
        result.add(new Pack("E044", "Pufferfish Pals", Material.FISHING_ROD, 130, 350, "minecraft:husbandry/fishy_business", "Fishy Business", 0, List.of(new Objective("fish:PUFFERFISH", 8, "Catch 8 Pufferfish"))));
        result.add(new Pack("E045", "Tropical Treasure", Material.FISHING_ROD, 210, 470, "minecraft:husbandry/tactical_fishing", "Tactical Fishing", 0, List.of(new Objective("fish:TROPICAL_FISH", 2, "Catch 2 Tropical Fish"))));
        result.add(new Pack("E046", "Junk Collector", Material.FISHING_ROD, 90, 300, "minecraft:husbandry/fishy_business", "Fishy Business", 0, List.of(new Objective("fish:COD", 8, "Catch 8 Cod"))));
        result.add(new Pack("E047", "Treasure Angler", Material.FISHING_ROD, 170, 390, "minecraft:husbandry/fishy_business", "Fishy Business", 0, List.of(new Objective("fish:COD", 2, "Catch 2 Cod"))));
        result.add(new Pack("E048", "Big Haul", Material.FISHING_ROD, 90, 330, "minecraft:husbandry/fishy_business", "Fishy Business", 70, List.of(new Objective("fish:COD", 80, "Catch 80 Cod"))));
        result.add(new Pack("E049", "Fisherman's Mix", Material.FISHING_ROD, 130, 350, "minecraft:husbandry/fishy_business", "Fishy Business", 0, List.of(new Objective("fish:COD", 16, "Catch 16 Cod"), new Objective("fish:SALMON", 8, "Catch 8 Salmon"))));
        result.add(new Pack("E050", "Deep Pond Marathon", Material.FISHING_ROD, 90, 350, "minecraft:husbandry/fishy_business", "Fishy Business", 0, List.of(new Objective("fish:COD", 120, "Catch 120 Cod"))));
        result.add(new Pack("E051", "Zombie Sweep", Material.IRON_SWORD, 50, 240, "minecraft:adventure/kill_a_mob", "Monster Hunter", 50, List.of(new Objective("kill:ZOMBIE", 30, "Defeat 30 Zombie"))));
        result.add(new Pack("E052", "Skeleton Shooting Gallery", Material.IRON_SWORD, 90, 290, "minecraft:adventure/kill_a_mob", "Monster Hunter", 55, List.of(new Objective("kill:SKELETON", 25, "Defeat 25 Skeleton"))));
        result.add(new Pack("E053", "Creeper Cleanup", Material.IRON_SWORD, 90, 290, "minecraft:adventure/kill_a_mob", "Monster Hunter", 50, List.of(new Objective("kill:CREEPER", 15, "Defeat 15 Creeper"))));
        result.add(new Pack("E054", "Spider Season", Material.IRON_SWORD, 50, 240, "minecraft:adventure/kill_a_mob", "Monster Hunter", 50, List.of(new Objective("kill:SPIDER", 25, "Defeat 25 Spider"))));
        result.add(new Pack("E055", "Night Shift", Material.IRON_SWORD, 90, 310, "minecraft:adventure/kill_a_mob", "Monster Hunter", 70, List.of(new Objective("kill:ZOMBIE", 60, "Defeat 60 Zombie"))));
        result.add(new Pack("E056", "Enderman Encounter", Material.IRON_SWORD, 170, 390, "minecraft:story/iron_tools", "Isn't It Iron Pick", 0, List.of(new Objective("kill:ENDERMAN", 6, "Defeat 6 Enderman"))));
        result.add(new Pack("E057", "Witch Hunt", Material.IRON_SWORD, 170, 410, "minecraft:adventure/kill_a_mob", "Monster Hunter", 0, List.of(new Objective("kill:WITCH", 5, "Defeat 5 Witch"))));
        result.add(new Pack("E058", "Slime Squisher", Material.IRON_SWORD, 130, 340, "minecraft:adventure/kill_a_mob", "Monster Hunter", 0, List.of(new Objective("kill:SLIME", 12, "Defeat 12 Slime"))));
        result.add(new Pack("E059", "Drowned Dive", Material.IRON_SWORD, 90, 290, "minecraft:adventure/kill_a_mob", "Monster Hunter", 55, List.of(new Objective("kill:DROWNED", 15, "Defeat 15 Drowned"))));
        result.add(new Pack("E060", "Desert & Snow Patrol", Material.IRON_SWORD, 170, 400, "minecraft:story/iron_tools", "Isn't It Iron Pick", 0, List.of(new Objective("kill:HUSK", 10, "Defeat 10 Husk"), new Objective("kill:STRAY", 10, "Defeat 10 Stray"))));
        result.add(new Pack("E061", "Furnace Apprentice", Material.FURNACE, 90, 270, "minecraft:story/smelt_iron", "Acquire Hardware", 0, List.of(new Objective("smelt:IRON_INGOT", 96, "Smelt 96 Iron Ingot"))));
        result.add(new Pack("E062", "Copper Smelter", Material.FURNACE, 90, 270, "minecraft:story/smelt_iron", "Acquire Hardware", 0, List.of(new Objective("smelt:COPPER_INGOT", 128, "Smelt 128 Copper Ingot"))));
        result.add(new Pack("E063", "Glassblower", Material.FURNACE, 50, 220, "minecraft:story/smelt_iron", "Acquire Hardware", 30, List.of(new Objective("smelt:GLASS", 256, "Smelt 256 Glass"))));
        result.add(new Pack("E064", "Brickyard", Material.FURNACE, 130, 320, "minecraft:story/smelt_iron", "Acquire Hardware", 0, List.of(new Objective("smelt:BRICK", 192, "Smelt 192 Brick"))));
        result.add(new Pack("E065", "Stone Mason", Material.FURNACE, 50, 210, "minecraft:story/mine_stone", "Stone Age", 30, List.of(new Objective("smelt:STONE", 256, "Smelt 256 Stone"))));
        result.add(new Pack("E066", "Charcoal Burner", Material.FURNACE, 50, 210, "minecraft:story/mine_stone", "Stone Age", 0, List.of(new Objective("smelt:CHARCOAL", 128, "Smelt 128 Charcoal"))));
        result.add(new Pack("E067", "Cookout", Material.FURNACE, 90, 270, "minecraft:story/smelt_iron", "Acquire Hardware", 0, List.of(new Objective("smelt:COOKED_BEEF", 96, "Smelt 96 Cooked Beef"))));
        result.add(new Pack("E068", "Seafood Platter", Material.FURNACE, 90, 270, "minecraft:husbandry/fishy_business", "Fishy Business", 0, List.of(new Objective("smelt:COOKED_COD", 80, "Smelt 80 Cooked Cod"))));
        result.add(new Pack("E069", "Terracotta Kiln", Material.FURNACE, 130, 330, "minecraft:story/smelt_iron", "Acquire Hardware", 0, List.of(new Objective("smelt:TERRACOTTA", 128, "Smelt 128 Terracotta"))));
        result.add(new Pack("E070", "Baker's Dozen", Material.CRAFTING_TABLE, 90, 270, "minecraft:husbandry/plant_seed", "A Seedy Place", 0, List.of(new Objective("collect:BREAD", 96, "Collect 96 Bread"))));
        result.add(new Pack("E071", "First Mile", Material.COMPASS, 50, 210, "minecraft:story/mine_stone", "Stone Age", 0, List.of(new Objective("travel", 500, "Travel 500 blocks"))));
        result.add(new Pack("E072", "Long Walk", Material.COMPASS, 50, 230, "minecraft:story/upgrade_tools", "Getting an Upgrade", 0, List.of(new Objective("travel", 500, "Travel 500 blocks"))));
        result.add(new Pack("E073", "Marathon Stroll", Material.COMPASS, 90, 320, "minecraft:story/iron_tools", "Isn't It Iron Pick", 0, List.of(new Objective("travel", 500, "Travel 500 blocks"))));
        result.add(new Pack("E074", "Biome Hopper", Material.COMPASS, 90, 290, "minecraft:story/upgrade_tools", "Getting an Upgrade", 0, List.of(new Objective("travel", 500, "Travel 500 blocks"))));
        result.add(new Pack("E075", "Plains to Peaks", Material.COMPASS, 130, 370, "minecraft:story/iron_tools", "Isn't It Iron Pick", 0, List.of(new Objective("travel", 500, "Travel 500 blocks"))));
        result.add(new Pack("E076", "Sprint Training", Material.COMPASS, 90, 300, "minecraft:story/iron_tools", "Isn't It Iron Pick", 50, List.of(new Objective("travel", 500, "Travel 500 blocks"))));
        result.add(new Pack("E077", "Cave Cartographer", Material.COMPASS, 170, 390, "minecraft:story/iron_tools", "Isn't It Iron Pick", 0, List.of(new Objective("travel", 500, "Travel 500 blocks"))));
        result.add(new Pack("E078", "Frozen Frontier", Material.COMPASS, 130, 340, "minecraft:story/iron_tools", "Isn't It Iron Pick", 0, List.of(new Objective("travel", 500, "Travel 500 blocks"))));
        result.add(new Pack("E079", "Sandy Safari", Material.COMPASS, 130, 340, "minecraft:story/iron_tools", "Isn't It Iron Pick", 0, List.of(new Objective("travel", 500, "Travel 500 blocks"))));
        result.add(new Pack("E080", "Jungle Expedition", Material.COMPASS, 130, 340, "minecraft:story/iron_tools", "Isn't It Iron Pick", 0, List.of(new Objective("travel", 500, "Travel 500 blocks"))));
        result.add(new Pack("E081", "Village Pantry Raid", Material.WHEAT, 90, 290, "minecraft:adventure/trade", "What a Deal!", 45, List.of(new Objective("harvest:WHEAT", 64, "Harvest 64 Wheat"))));
        result.add(new Pack("E082", "Dungeon Delver", Material.IRON_SWORD, 130, 350, "minecraft:story/iron_tools", "Isn't It Iron Pick", 50, List.of(new Objective("kill:ZOMBIE", 15, "Defeat 15 Zombie"))));
        result.add(new Pack("E083", "Mineshaft Scavenger", Material.IRON_SWORD, 130, 360, "minecraft:story/iron_tools", "Isn't It Iron Pick", 60, List.of(new Objective("kill:CAVE_SPIDER", 20, "Defeat 20 Cave Spider"))));
        result.add(new Pack("E084", "Shipwreck Salvage", Material.FISHING_ROD, 170, 410, "minecraft:husbandry/fishy_business", "Fishy Business", 0, List.of(new Objective("fish:COD", 20, "Catch 20 Cod"))));
        result.add(new Pack("E085", "X Marks the Spot", Material.FISHING_ROD, 210, 470, "minecraft:story/iron_tools", "Isn't It Iron Pick", 0, List.of(new Objective("fish:COD", 10, "Catch 10 Cod"))));
        result.add(new Pack("E086", "Ruined Portal Looter", Material.IRON_PICKAXE, 170, 400, "minecraft:story/form_obsidian", "Ice Bucket Challenge", 0, List.of(new Objective("break:OBSIDIAN", 12, "Mine 12 Obsidian"))));
        result.add(new Pack("E087", "Desert Pyramid Heist", Material.IRON_SWORD, 170, 410, "minecraft:story/iron_tools", "Isn't It Iron Pick", 0, List.of(new Objective("kill:HUSK", 10, "Defeat 10 Husk"))));
        result.add(new Pack("E088", "Jungle Temple Tour", Material.IRON_SWORD, 170, 410, "minecraft:story/iron_tools", "Isn't It Iron Pick", 0, List.of(new Objective("kill:SPIDER", 10, "Defeat 10 Spider"))));
        result.add(new Pack("E089", "Mixed Loot Run", Material.IRON_SWORD, 130, 370, "minecraft:story/iron_tools", "Isn't It Iron Pick", 0, List.of(new Objective("kill:ZOMBIE", 50, "Defeat 50 Zombie"))));
        result.add(new Pack("E090", "Underwater Ruin Dive", Material.FISHING_ROD, 170, 410, "minecraft:husbandry/fishy_business", "Fishy Business", 0, List.of(new Objective("fish:COD", 30, "Catch 30 Cod"))));
        result.add(new Pack("E091", "Pie Maker", Material.CRAFTING_TABLE, 90, 280, "minecraft:husbandry/plant_seed", "A Seedy Place", 40, List.of(new Objective("collect:PUMPKIN_PIE", 32, "Collect 32 Pumpkin Pie"))));
        result.add(new Pack("E092", "Cake Day", Material.CRAFTING_TABLE, 170, 390, "minecraft:husbandry/plant_seed", "A Seedy Place", 0, List.of(new Objective("collect:CAKE", 3, "Collect 3 Cake"))));
        result.add(new Pack("E093", "Cookie Jar", Material.CRAFTING_TABLE, 130, 340, "minecraft:husbandry/plant_seed", "A Seedy Place", 40, List.of(new Objective("collect:COOKIE", 192, "Collect 192 Cookie"))));
        result.add(new Pack("E094", "Lantern Lighter", Material.CRAFTING_TABLE, 130, 330, "minecraft:story/smelt_iron", "Acquire Hardware", 0, List.of(new Objective("collect:LANTERN", 16, "Collect 16 Lantern"))));
        result.add(new Pack("E095", "Shield Wall", Material.CRAFTING_TABLE, 130, 330, "minecraft:story/iron_tools", "Isn't It Iron Pick", 0, List.of(new Objective("collect:SHIELD", 12, "Collect 12 Shield"))));
        result.add(new Pack("E096", "Bucket Brigade", Material.CRAFTING_TABLE, 170, 380, "minecraft:story/iron_tools", "Isn't It Iron Pick", 0, List.of(new Objective("collect:BUCKET", 10, "Collect 10 Bucket"))));
        result.add(new Pack("E097", "Library Builder", Material.CRAFTING_TABLE, 130, 330, "minecraft:story/iron_tools", "Isn't It Iron Pick", 0, List.of(new Objective("collect:BOOKSHELF", 10, "Collect 10 Bookshelf"))));
        result.add(new Pack("E098", "Enchanter's Apprentice", Material.IRON_PICKAXE, 170, 380, "minecraft:story/enchant_item", "Enchanter", 0, List.of(new Objective("break:LAPIS_ORE", 30, "Mine 30 Lapis Ore"))));
        result.add(new Pack("E099", "Armorer's Order", Material.CRAFTING_TABLE, 170, 390, "minecraft:story/obtain_armor", "Suit Up", 0, List.of(new Objective("collect:IRON_HELMET", 1, "Collect 1 Iron Helmet"), new Objective("collect:IRON_CHESTPLATE", 1, "Collect 1 Iron Chestplate"), new Objective("collect:IRON_LEGGINGS", 1, "Collect 1 Iron Leggings"), new Objective("collect:IRON_BOOTS", 1, "Collect 1 Iron Boots"))));
        result.add(new Pack("E100", "Jack of All Trades", Material.IRON_PICKAXE, 250, 550, "minecraft:story/iron_tools", "Isn't It Iron Pick", 0, List.of(new Objective("break:IRON_ORE", 32, "Mine 32 Iron Ore"), new Objective("kill:ZOMBIE", 15, "Defeat 15 Zombie"), new Objective("fish:COD", 15, "Catch 15 Cod"), new Objective("harvest:WHEAT", 64, "Harvest 64 Wheat"))));
        result.add(new Pack("H001", "Diamond Prospector", Material.IRON_PICKAXE, 590, 1120, "minecraft:story/mine_diamond", "Diamonds!", 0, List.of(new Objective("break:DIAMOND_ORE", 24, "Mine 24 Diamond Ore"))));
        result.add(new Pack("H002", "Emerald Mountain Miner", Material.IRON_PICKAXE, 640, 1220, "minecraft:story/mine_diamond", "Diamonds!", 0, List.of(new Objective("break:EMERALD_ORE", 12, "Mine 12 Emerald Ore"))));
        result.add(new Pack("H003", "Gold Vein Gambit", Material.IRON_PICKAXE, 530, 1030, "minecraft:story/smelt_iron", "Acquire Hardware", 0, List.of(new Objective("break:GOLD_ORE", 48, "Mine 48 Gold Ore"))));
        result.add(new Pack("H004", "Deepslate Delver", Material.IRON_PICKAXE, 360, 720, "minecraft:story/mine_diamond", "Diamonds!", 50, List.of(new Objective("break:DEEPSLATE", 500, "Mine 500 Deepslate"))));
        result.add(new Pack("H005", "Lapis Lazuli Lodes", Material.IRON_PICKAXE, 470, 970, "minecraft:story/enchant_item", "Enchanter", 80, List.of(new Objective("break:LAPIS_ORE", 128, "Mine 128 Lapis Ore"))));
        result.add(new Pack("H006", "Redstone Royalty", Material.IRON_PICKAXE, 470, 940, "minecraft:story/mine_diamond", "Diamonds!", 0, List.of(new Objective("break:REDSTONE_ORE", 192, "Mine 192 Redstone Ore"))));
        result.add(new Pack("H007", "Copper Cavern Crew", Material.IRON_PICKAXE, 410, 850, "minecraft:story/iron_tools", "Isn't It Iron Pick", 70, List.of(new Objective("break:COPPER_ORE", 256, "Mine 256 Copper Ore"))));
        result.add(new Pack("H008", "Geode Raider", Material.IRON_PICKAXE, 590, 1120, "minecraft:story/mine_diamond", "Diamonds!", 0, List.of(new Objective("break:AMETHYST_CLUSTER", 24, "Mine 24 Amethyst Cluster"))));
        result.add(new Pack("H009", "Mixed Ore Blitz", Material.IRON_PICKAXE, 470, 960, "minecraft:story/mine_diamond", "Diamonds!", 0, List.of(new Objective("break:ANY_ORE", 400, "Mine 400 Any Ore"))));
        result.add(new Pack("H010", "Below Zero Run", Material.IRON_PICKAXE, 590, 1140, "minecraft:story/mine_diamond", "Diamonds!", 0, List.of(new Objective("break:DEEPSLATE_DIAMOND_ORE", 16, "Mine 16 Deepslate Diamond Ore"), new Objective("break:DEEPSLATE_REDSTONE_ORE", 64, "Mine 64 Deepslate Redstone Ore"))));
        result.add(new Pack("H011", "Quartz Quarry", Material.IRON_PICKAXE, 410, 800, "minecraft:nether/root", "Nether", 50, List.of(new Objective("break:NETHER_QUARTZ_ORE", 128, "Mine 128 Nether Quartz Ore"))));
        result.add(new Pack("H012", "Glowstone Gatherer", Material.IRON_PICKAXE, 410, 800, "minecraft:nether/root", "Nether", 50, List.of(new Objective("break:GLOWSTONE", 128, "Mine 128 Glowstone"))));
        result.add(new Pack("H013", "Soul Sand Scout", Material.IRON_PICKAXE, 360, 720, "minecraft:nether/root", "Nether", 50, List.of(new Objective("break:SOUL_SAND", 384, "Mine 384 Soul Sand"))));
        result.add(new Pack("H014", "Nether Gold Rush", Material.IRON_PICKAXE, 410, 790, "minecraft:nether/root", "Nether", 0, List.of(new Objective("break:NETHER_GOLD_ORE", 160, "Mine 160 Nether Gold Ore"))));
        result.add(new Pack("H015", "Basalt Delta Crew", Material.IRON_PICKAXE, 360, 720, "minecraft:nether/root", "Nether", 50, List.of(new Objective("break:BASALT", 500, "Mine 500 Basalt"))));
        result.add(new Pack("H016", "Crimson Logger", Material.IRON_PICKAXE, 410, 830, "minecraft:nether/root", "Nether", 60, List.of(new Objective("break:CRIMSON_STEM", 160, "Mine 160 Crimson Stem"))));
        result.add(new Pack("H017", "Warped Logger", Material.IRON_PICKAXE, 410, 830, "minecraft:nether/root", "Nether", 60, List.of(new Objective("break:WARPED_STEM", 160, "Mine 160 Warped Stem"))));
        result.add(new Pack("H018", "Wart Farmer", Material.WHEAT, 470, 910, "minecraft:nether/root", "Nether", 0, List.of(new Objective("harvest:NETHER_WART", 192, "Harvest 192 Nether Wart"))));
        result.add(new Pack("H019", "Ancient Debris Dig", Material.IRON_PICKAXE, 700, 1370, "minecraft:nether/obtain_ancient_debris", "Hidden in the Depths", 0, List.of(new Objective("break:ANCIENT_DEBRIS", 8, "Mine 8 Ancient Debris"))));
        result.add(new Pack("H020", "Crying Obsidian Harvest", Material.IRON_PICKAXE, 590, 1120, "minecraft:nether/obtain_crying_obsidian", "Who is Cutting Onions?", 0, List.of(new Objective("break:CRYING_OBSIDIAN", 12, "Mine 12 Crying Obsidian"))));
        result.add(new Pack("H021", "Blaze Brigade", Material.DIAMOND_SWORD, 530, 1030, "minecraft:nether/obtain_blaze_rod", "Into Fire", 90, List.of(new Objective("kill:BLAZE", 30, "Defeat 30 Blaze"))));
        result.add(new Pack("H022", "Wither Skeleton Cull", Material.DIAMOND_SWORD, 530, 1060, "minecraft:nether/obtain_blaze_rod", "Into Fire", 100, List.of(new Objective("kill:WITHER_SKELETON", 20, "Defeat 20 Wither Skeleton"))));
        result.add(new Pack("H023", "Ghast Tear Hunter", Material.DIAMOND_SWORD, 530, 1030, "minecraft:nether/return_to_sender", "Return to Sender", 90, List.of(new Objective("kill:GHAST", 8, "Defeat 8 Ghast"))));
        result.add(new Pack("H024", "Piglin Patrol", Material.DIAMOND_SWORD, 410, 800, "minecraft:nether/find_bastion", "Those Were the Days", 0, List.of(new Objective("kill:PIGLIN", 30, "Defeat 30 Piglin"))));
        result.add(new Pack("H025", "Brute Force", Material.DIAMOND_SWORD, 640, 1220, "minecraft:nether/find_bastion", "Those Were the Days", 0, List.of(new Objective("kill:PIGLIN_BRUTE", 4, "Defeat 4 Piglin Brute"))));
        result.add(new Pack("H026", "Hoglin Roundup", Material.DIAMOND_SWORD, 470, 920, "minecraft:nether/find_bastion", "Those Were the Days", 75, List.of(new Objective("kill:HOGLIN", 20, "Defeat 20 Hoglin"))));
        result.add(new Pack("H027", "Magma Cube Mash", Material.DIAMOND_SWORD, 410, 800, "minecraft:nether/obtain_blaze_rod", "Into Fire", 0, List.of(new Objective("kill:MAGMA_CUBE", 20, "Defeat 20 Magma Cube"))));
        result.add(new Pack("H028", "Bastion Looter", Material.DIAMOND_SWORD, 590, 1140, "minecraft:nether/find_bastion", "Those Were the Days", 0, List.of(new Objective("kill:PIGLIN", 30, "Defeat 30 Piglin"))));
        result.add(new Pack("H029", "Fortress Chest Crawl", Material.DIAMOND_SWORD, 530, 1030, "minecraft:nether/obtain_blaze_rod", "Into Fire", 0, List.of(new Objective("kill:BLAZE", 25, "Defeat 25 Blaze"))));
        result.add(new Pack("H030", "Zombified Piglin Purge", Material.DIAMOND_SWORD, 360, 750, "minecraft:nether/root", "Nether", 75, List.of(new Objective("kill:ZOMBIFIED_PIGLIN", 60, "Defeat 60 Zombified Piglin"))));
        result.add(new Pack("H031", "Undead Annihilator", Material.DIAMOND_SWORD, 360, 800, "minecraft:adventure/kill_a_mob", "Monster Hunter", 100, List.of(new Objective("kill:ZOMBIE", 80, "Defeat 80 Zombie"))));
        result.add(new Pack("H032", "Bone Collector", Material.DIAMOND_SWORD, 360, 800, "minecraft:adventure/shoot_arrow", "Take Aim", 100, List.of(new Objective("kill:SKELETON", 60, "Defeat 60 Skeleton"))));
        result.add(new Pack("H033", "Arachnophobia", Material.DIAMOND_SWORD, 360, 770, "minecraft:adventure/kill_a_mob", "Monster Hunter", 90, List.of(new Objective("kill:SPIDER", 70, "Defeat 70 Spider"))));
        result.add(new Pack("H034", "Creeper Clearance", Material.DIAMOND_SWORD, 410, 880, "minecraft:adventure/shoot_arrow", "Take Aim", 100, List.of(new Objective("kill:CREEPER", 40, "Defeat 40 Creeper"))));
        result.add(new Pack("H035", "Phantom Menace", Material.DIAMOND_SWORD, 530, 1050, "minecraft:adventure/kill_a_mob", "Monster Hunter", 0, List.of(new Objective("kill:PHANTOM", 12, "Defeat 12 Phantom"))));
        result.add(new Pack("H036", "Witch's Cauldron", Material.DIAMOND_SWORD, 530, 1050, "minecraft:story/shiny_gear", "Cover Me with Diamonds", 0, List.of(new Objective("kill:WITCH", 12, "Defeat 12 Witch"))));
        result.add(new Pack("H037", "Enderman Ambush", Material.DIAMOND_SWORD, 530, 1080, "minecraft:story/shiny_gear", "Cover Me with Diamonds", 115, List.of(new Objective("kill:ENDERMAN", 25, "Defeat 25 Enderman"))));
        result.add(new Pack("H038", "Silverfish Sweeper", Material.DIAMOND_SWORD, 470, 910, "minecraft:story/follow_ender_eye", "Eye Spy", 0, List.of(new Objective("kill:SILVERFISH", 40, "Defeat 40 Silverfish"))));
        result.add(new Pack("H039", "Raid Cleanup", Material.DIAMOND_SWORD, 590, 1170, "minecraft:adventure/voluntary_exile", "Voluntary Exile", 115, List.of(new Objective("kill:PILLAGER", 30, "Defeat 30 Pillager"))));
        result.add(new Pack("H040", "Ravager Wrangler", Material.DIAMOND_SWORD, 700, 1370, "minecraft:adventure/voluntary_exile", "Voluntary Exile", 0, List.of(new Objective("kill:RAVAGER", 3, "Defeat 3 Ravager"))));
        result.add(new Pack("H041", "Guardian Patrol", Material.DIAMOND_SWORD, 530, 1110, "minecraft:story/shiny_gear", "Cover Me with Diamonds", 125, List.of(new Objective("kill:GUARDIAN", 30, "Defeat 30 Guardian"))));
        result.add(new Pack("H042", "Prismarine Pillager", Material.IRON_PICKAXE, 470, 940, "minecraft:story/mine_diamond", "Diamonds!", 70, List.of(new Objective("break:PRISMARINE", 256, "Mine 256 Prismarine"))));
        result.add(new Pack("H043", "Sea Lantern Looter", Material.IRON_PICKAXE, 590, 1140, "minecraft:story/mine_diamond", "Diamonds!", 0, List.of(new Objective("break:SEA_LANTERN", 24, "Mine 24 Sea Lantern"))));
        result.add(new Pack("H044", "Drowned Hunter", Material.DIAMOND_SWORD, 410, 850, "minecraft:husbandry/fishy_business", "Fishy Business", 0, List.of(new Objective("kill:DROWNED", 50, "Defeat 50 Drowned"))));
        result.add(new Pack("H045", "Deep Sea Haul", Material.FISHING_ROD, 410, 950, "minecraft:husbandry/fishy_business", "Fishy Business", 0, List.of(new Objective("fish:COD", 200, "Catch 200 Cod"))));
        result.add(new Pack("H046", "Pufferfish Plague", Material.FISHING_ROD, 470, 960, "minecraft:husbandry/fishy_business", "Fishy Business", 0, List.of(new Objective("fish:PUFFERFISH", 20, "Catch 20 Pufferfish"))));
        result.add(new Pack("H047", "Tropical Trove", Material.FISHING_ROD, 640, 1330, "minecraft:husbandry/tactical_fishing", "Tactical Fishing", 0, List.of(new Objective("fish:TROPICAL_FISH", 5, "Catch 5 Tropical Fish"))));
        result.add(new Pack("H048", "Treasure Tycoon", Material.FISHING_ROD, 590, 1140, "minecraft:husbandry/fishy_business", "Fishy Business", 0, List.of(new Objective("fish:COD", 6, "Catch 6 Cod"))));
        result.add(new Pack("H049", "Ocean Explorer", Material.COMPASS, 410, 820, "minecraft:story/iron_tools", "Isn't It Iron Pick", 0, List.of(new Objective("travel", 500, "Travel 500 blocks"))));
        result.add(new Pack("H050", "Wreck & Ruin Tour", Material.FISHING_ROD, 530, 1080, "minecraft:husbandry/fishy_business", "Fishy Business", 0, List.of(new Objective("fish:COD", 80, "Catch 80 Cod"))));
        result.add(new Pack("H051", "Wheat Baron", Material.WHEAT, 360, 770, "minecraft:husbandry/plant_seed", "A Seedy Place", 70, List.of(new Objective("harvest:WHEAT", 640, "Harvest 640 Wheat"))));
        result.add(new Pack("H052", "Carrot Tycoon", Material.WHEAT, 360, 770, "minecraft:husbandry/plant_seed", "A Seedy Place", 70, List.of(new Objective("harvest:CARROT", 512, "Harvest 512 Carrot"))));
        result.add(new Pack("H053", "Potato Mogul", Material.WHEAT, 360, 770, "minecraft:husbandry/plant_seed", "A Seedy Place", 70, List.of(new Objective("harvest:POTATO", 512, "Harvest 512 Potato"))));
        result.add(new Pack("H054", "Melon & Pumpkin Patch", Material.WHEAT, 410, 850, "minecraft:husbandry/plant_seed", "A Seedy Place", 0, List.of(new Objective("harvest:MELON_SLICE", 192, "Harvest 192 Melon Slice"), new Objective("harvest:PUMPKIN", 128, "Harvest 128 Pumpkin"))));
        result.add(new Pack("H055", "Beetroot Brigade II", Material.WHEAT, 410, 820, "minecraft:husbandry/plant_seed", "A Seedy Place", 0, List.of(new Objective("harvest:BEETROOT", 400, "Harvest 400 Beetroot"))));
        result.add(new Pack("H056", "Sweet Berry Syndicate", Material.WHEAT, 410, 850, "minecraft:husbandry/plant_seed", "A Seedy Place", 0, List.of(new Objective("harvest:WHEAT", 384, "Harvest 384 Wheat"))));
        result.add(new Pack("H057", "Glow Berry Gatherer", Material.WHEAT, 530, 1030, "minecraft:story/iron_tools", "Isn't It Iron Pick", 0, List.of(new Objective("harvest:GLOW_BERRIES", 96, "Harvest 96 Glow Berries"))));
        result.add(new Pack("H058", "Cocoa Cartel", Material.WHEAT, 470, 940, "minecraft:husbandry/plant_seed", "A Seedy Place", 0, List.of(new Objective("harvest:COCOA_BEANS", 256, "Harvest 256 Cocoa Beans"))));
        result.add(new Pack("H059", "Sugar Cane Syndicate", Material.IRON_PICKAXE, 360, 740, "minecraft:husbandry/plant_seed", "A Seedy Place", 0, List.of(new Objective("break:SUGAR_CANE", 500, "Mine 500 Sugar Cane"))));
        result.add(new Pack("H060", "Stable Master", Material.WHEAT, 410, 950, "minecraft:husbandry/breed_an_animal", "The Parrots and the Bats", 0, List.of(new Objective("harvest:WHEAT", 240, "Harvest 240 Wheat"))));
        result.add(new Pack("H061", "Iron Foundry", Material.FURNACE, 410, 900, "minecraft:story/iron_tools", "Isn't It Iron Pick", 90, List.of(new Objective("smelt:IRON_INGOT", 384, "Smelt 384 Iron Ingot"))));
        result.add(new Pack("H062", "Gold Foundry", Material.FURNACE, 530, 1050, "minecraft:story/mine_diamond", "Diamonds!", 0, List.of(new Objective("smelt:GOLD_INGOT", 192, "Smelt 192 Gold Ingot"))));
        result.add(new Pack("H063", "Copper Furnaces", Material.FURNACE, 360, 750, "minecraft:story/iron_tools", "Isn't It Iron Pick", 60, List.of(new Objective("smelt:COPPER_INGOT", 512, "Smelt 512 Copper Ingot"))));
        result.add(new Pack("H064", "Scrap Smelter", Material.FURNACE, 700, 1340, "minecraft:nether/obtain_ancient_debris", "Hidden in the Depths", 0, List.of(new Objective("smelt:NETHERITE_SCRAP", 6, "Smelt 6 Netherite Scrap"))));
        result.add(new Pack("H065", "Glasswork Empire", Material.FURNACE, 360, 720, "minecraft:story/smelt_iron", "Acquire Hardware", 50, List.of(new Objective("smelt:GLASS", 1024, "Smelt 1024 Glass"))));
        result.add(new Pack("H066", "Steakhouse", Material.FURNACE, 410, 790, "minecraft:husbandry/breed_an_animal", "The Parrots and the Bats", 0, List.of(new Objective("smelt:COOKED_BEEF", 256, "Smelt 256 Cooked Beef"))));
        result.add(new Pack("H067", "Smokehouse", Material.FURNACE, 410, 800, "minecraft:husbandry/breed_an_animal", "The Parrots and the Bats", 0, List.of(new Objective("smelt:COOKED_PORKCHOP", 320, "Smelt 320 Cooked Porkchop"))));
        result.add(new Pack("H068", "Charcoal Baron", Material.FURNACE, 300, 650, "minecraft:story/upgrade_tools", "Getting an Upgrade", 60, List.of(new Objective("smelt:CHARCOAL", 512, "Smelt 512 Charcoal"))));
        result.add(new Pack("H069", "Kiln Master", Material.FURNACE, 410, 880, "minecraft:story/iron_tools", "Isn't It Iron Pick", 80, List.of(new Objective("smelt:TERRACOTTA", 384, "Smelt 384 Terracotta"))));
        result.add(new Pack("H070", "Smelter's Gauntlet", Material.FURNACE, 530, 1050, "minecraft:story/mine_diamond", "Diamonds!", 0, List.of(new Objective("smelt:IRON_INGOT", 128, "Smelt 128 Iron Ingot"), new Objective("smelt:GOLD_INGOT", 64, "Smelt 64 Gold Ingot"), new Objective("smelt:COPPER_INGOT", 128, "Smelt 128 Copper Ingot"))));
        result.add(new Pack("H071", "Cross-Country Hike", Material.COMPASS, 410, 980, "minecraft:story/iron_tools", "Isn't It Iron Pick", 120, List.of(new Objective("travel", 500, "Travel 500 blocks"))));
        result.add(new Pack("H072", "Biome Collector", Material.COMPASS, 530, 1180, "minecraft:adventure/sleep_in_bed", "Sweet Dreams", 0, List.of(new Objective("travel", 500, "Travel 500 blocks"))));
        result.add(new Pack("H073", "Mountain Pass", Material.COMPASS, 470, 990, "minecraft:story/iron_tools", "Isn't It Iron Pick", 90, List.of(new Objective("travel", 500, "Travel 500 blocks"))));
        result.add(new Pack("H074", "Swamp & Mangrove Trek", Material.COMPASS, 410, 850, "minecraft:story/iron_tools", "Isn't It Iron Pick", 70, List.of(new Objective("travel", 500, "Travel 500 blocks"), new Objective("travel", 500, "Travel 500 blocks"))));
        result.add(new Pack("H075", "Mushroom Pilgrim", Material.COMPASS, 640, 1220, "minecraft:story/iron_tools", "Isn't It Iron Pick", 0, List.of(new Objective("travel", 500, "Travel 500 blocks"))));
        result.add(new Pack("H076", "Blossom & Pale Wanderer", Material.COMPASS, 530, 1080, "minecraft:story/iron_tools", "Isn't It Iron Pick", 0, List.of(new Objective("travel", 500, "Travel 500 blocks"))));
        result.add(new Pack("H077", "Savanna Safari", Material.COMPASS, 410, 850, "minecraft:story/iron_tools", "Isn't It Iron Pick", 70, List.of(new Objective("travel", 500, "Travel 500 blocks"))));
        result.add(new Pack("H078", "Nether Grand Tour", Material.COMPASS, 530, 1080, "minecraft:nether/ride_strider", "This Boat Has Legs", 0, List.of(new Objective("travel", 500, "Travel 500 blocks"))));
        result.add(new Pack("H079", "Sprint Challenge", Material.COMPASS, 410, 930, "minecraft:story/shiny_gear", "Cover Me with Diamonds", 100, List.of(new Objective("travel", 500, "Travel 500 blocks"))));
        result.add(new Pack("H080", "Cave Biome Delver", Material.COMPASS, 590, 1200, "minecraft:story/shiny_gear", "Cover Me with Diamonds", 0, List.of(new Objective("travel", 500, "Travel 500 blocks"))));
        result.add(new Pack("H081", "Desert Pyramid Plunder", Material.DIAMOND_SWORD, 530, 1060, "minecraft:story/iron_tools", "Isn't It Iron Pick", 80, List.of(new Objective("kill:HUSK", 20, "Defeat 20 Husk"))));
        result.add(new Pack("H082", "Jungle Temple Raider", Material.DIAMOND_SWORD, 530, 1060, "minecraft:story/iron_tools", "Isn't It Iron Pick", 80, List.of(new Objective("kill:SPIDER", 20, "Defeat 20 Spider"))));
        result.add(new Pack("H083", "Stronghold Stacks", Material.DIAMOND_SWORD, 590, 1170, "minecraft:story/follow_ender_eye", "Eye Spy", 0, List.of(new Objective("kill:ZOMBIE", 30, "Defeat 30 Zombie"))));
        result.add(new Pack("H084", "Outpost Overrun", Material.DIAMOND_SWORD, 530, 1030, "minecraft:adventure/voluntary_exile", "Voluntary Exile", 0, List.of(new Objective("kill:PILLAGER", 15, "Defeat 15 Pillager"))));
        result.add(new Pack("H085", "Dungeon Crawl XL", Material.DIAMOND_SWORD, 470, 1020, "minecraft:story/shiny_gear", "Cover Me with Diamonds", 0, List.of(new Objective("kill:ZOMBIE", 50, "Defeat 50 Zombie"))));
        result.add(new Pack("H086", "Mineshaft Marathon", Material.DIAMOND_SWORD, 470, 1050, "minecraft:story/shiny_gear", "Cover Me with Diamonds", 110, List.of(new Objective("kill:CAVE_SPIDER", 60, "Defeat 60 Cave Spider"))));
        result.add(new Pack("H087", "Mansion Heist", Material.DIAMOND_SWORD, 700, 1420, "minecraft:adventure/voluntary_exile", "Voluntary Exile", 0, List.of(new Objective("kill:VINDICATOR", 20, "Defeat 20 Vindicator"))));
        result.add(new Pack("H088", "Ruined Portal Raider", Material.IRON_PICKAXE, 530, 1110, "minecraft:story/form_obsidian", "Ice Bucket Challenge", 0, List.of(new Objective("break:OBSIDIAN", 32, "Mine 32 Obsidian"))));
        result.add(new Pack("H089", "Village Hero's Haul", Material.WHEAT, 470, 990, "minecraft:adventure/trade", "What a Deal!", 0, List.of(new Objective("harvest:WHEAT", 192, "Harvest 192 Wheat"))));
        result.add(new Pack("H090", "Treasure Map Master", Material.FISHING_ROD, 640, 1300, "minecraft:husbandry/fishy_business", "Fishy Business", 0, List.of(new Objective("fish:COD", 20, "Catch 20 Cod"))));
        result.add(new Pack("H091", "Elder Guardian Slayer", Material.DIAMOND_SWORD, 700, 1390, "minecraft:story/shiny_gear", "Cover Me with Diamonds", 0, List.of(new Objective("kill:ELDER_GUARDIAN", 1, "Defeat 1 Elder Guardian"))));
        result.add(new Pack("H092", "Roaming Boss Hunter", Material.DIAMOND_SWORD, 640, 1300, "minecraft:adventure/kill_a_mob", "Monster Hunter", 0, List.of(new Objective("kill:@ROAMING_BOSS", 1, "Defeat 1 @roaming Boss"))));
        result.add(new Pack("H093", "Skull Hunter", Material.DIAMOND_SWORD, 590, 1300, "minecraft:nether/obtain_blaze_rod", "Into Fire", 175, List.of(new Objective("kill:WITHER_SKELETON", 60, "Defeat 60 Wither Skeleton"))));
        result.add(new Pack("H094", "Blaze Rod Baron", Material.DIAMOND_SWORD, 530, 1160, "minecraft:nether/obtain_blaze_rod", "Into Fire", 150, List.of(new Objective("kill:BLAZE", 60, "Defeat 60 Blaze"))));
        result.add(new Pack("H095", "Ghast Gauntlet", Material.DIAMOND_SWORD, 590, 1220, "minecraft:nether/return_to_sender", "Return to Sender", 0, List.of(new Objective("kill:GHAST", 16, "Defeat 16 Ghast"))));
        result.add(new Pack("H096", "Evoker Execution", Material.DIAMOND_SWORD, 700, 1390, "minecraft:adventure/voluntary_exile", "Voluntary Exile", 0, List.of(new Objective("kill:EVOKER", 2, "Defeat 2 Evoker"))));
        result.add(new Pack("H097", "Vex Vanquisher", Material.DIAMOND_SWORD, 530, 1080, "minecraft:adventure/voluntary_exile", "Voluntary Exile", 115, List.of(new Objective("kill:VEX", 20, "Defeat 20 Vex"))));
        result.add(new Pack("H098", "Captain's Bounty", Material.DIAMOND_SWORD, 470, 990, "minecraft:adventure/voluntary_exile", "Voluntary Exile", 115, List.of(new Objective("kill:PILLAGER", 40, "Defeat 40 Pillager"))));
        result.add(new Pack("H099", "Nether Conqueror", Material.IRON_PICKAXE, 700, 1470, "minecraft:nether/obtain_ancient_debris", "Hidden in the Depths", 0, List.of(new Objective("break:ANCIENT_DEBRIS", 4, "Mine 4 Ancient Debris"), new Objective("kill:BLAZE", 25, "Defeat 25 Blaze"), new Objective("kill:WITHER_SKELETON", 15, "Defeat 15 Wither Skeleton"))));
        result.add(new Pack("H100", "Overworld Overlord", Material.IRON_PICKAXE, 700, 1550, "minecraft:story/shiny_gear", "Cover Me with Diamonds", 0, List.of(new Objective("break:DIAMOND_ORE", 20, "Mine 20 Diamond Ore"), new Objective("kill:CREEPER", 20, "Defeat 20 Creeper"), new Objective("fish:COD", 3, "Catch 3 Cod"), new Objective("travel", 500, "Travel 500 blocks"))));
        result.add(new Pack("M001", "Enderman Exterminator", Material.NETHERITE_SWORD, 2100, 4900, "minecraft:end/enter_end_gateway", "Remote Getaway", 140, List.of(new Objective("kill:ENDERMAN", 60, "Defeat 60 Enderman"))));
        result.add(new Pack("M002", "End Stone Excavator", Material.IRON_PICKAXE, 1200, 3200, "minecraft:end/enter_end_gateway", "Remote Getaway", 0, List.of(new Objective("break:END_STONE", 500, "Mine 500 End Stone"))));
        result.add(new Pack("M003", "Purpur Pillager", Material.IRON_PICKAXE, 1650, 4150, "minecraft:end/find_end_city", "The City at the End of the Game", 100, List.of(new Objective("break:PURPUR_BLOCK", 384, "Mine 384 Purpur Block"))));
        result.add(new Pack("M004", "Chorus Harvest", Material.IRON_PICKAXE, 1450, 3750, "minecraft:end/enter_end_gateway", "Remote Getaway", 90, List.of(new Objective("break:CHORUS_PLANT", 600, "Mine 600 Chorus Plant"))));
        result.add(new Pack("M005", "Shulker Shell Hunt", Material.NETHERITE_SWORD, 2350, 5300, "minecraft:end/find_end_city", "The City at the End of the Game", 150, List.of(new Objective("kill:SHULKER", 16, "Defeat 16 Shulker"))));
        result.add(new Pack("M006", "End City Loot Run", Material.NETHERITE_SWORD, 2350, 5300, "minecraft:end/find_end_city", "The City at the End of the Game", 0, List.of(new Objective("kill:SHULKER", 40, "Defeat 40 Shulker"))));
        result.add(new Pack("M007", "Dragon Head Heist", Material.IRON_PICKAXE, 2800, 6050, "minecraft:end/find_end_city", "The City at the End of the Game", 0, List.of(new Objective("break:DRAGON_HEAD", 1, "Mine 1 Dragon Head"))));
        result.add(new Pack("M008", "Sky Pirate", Material.COMPASS, 2100, 4800, "minecraft:end/elytra", "Sky's the Limit", 100, List.of(new Objective("travel", 500, "Travel 500 blocks"))));
        result.add(new Pack("M009", "Void Walker", Material.COMPASS, 1900, 4500, "minecraft:end/enter_end_gateway", "Remote Getaway", 100, List.of(new Objective("travel", 500, "Travel 500 blocks"))));
        result.add(new Pack("M010", "Endgame Supply Run", Material.NETHERITE_SWORD, 2100, 5200, "minecraft:end/find_end_city", "The City at the End of the Game", 200, List.of(new Objective("kill:ENDERMAN", 30, "Defeat 30 Enderman"), new Objective("break:END_STONE", 500, "Mine 500 End Stone"), new Objective("break:CHORUS_PLANT", 256, "Mine 256 Chorus Plant"))));
        result.add(new Pack("M011", "Debris Magnate", Material.IRON_PICKAXE, 2800, 6250, "minecraft:nether/obtain_ancient_debris", "Hidden in the Depths", 0, List.of(new Objective("break:ANCIENT_DEBRIS", 24, "Mine 24 Ancient Debris"))));
        result.add(new Pack("M012", "Scrap Smelter Supreme", Material.FURNACE, 2800, 6400, "minecraft:nether/obtain_ancient_debris", "Hidden in the Depths", 0, List.of(new Objective("smelt:NETHERITE_SCRAP", 12, "Smelt 12 Netherite Scrap"))));
        result.add(new Pack("M013", "Quartz Empire", Material.IRON_PICKAXE, 1450, 3900, "minecraft:nether/find_fortress", "A Terrible Fortress", 110, List.of(new Objective("break:NETHER_QUARTZ_ORE", 768, "Mine 768 Nether Quartz Ore"))));
        result.add(new Pack("M014", "Piglin Diplomat", Material.NETHERITE_SWORD, 1900, 4500, "minecraft:nether/distract_piglin", "Oh Shiny", 100, List.of(new Objective("kill:PIGLIN", 480, "Defeat 480 Piglin"))));
        result.add(new Pack("M015", "Gilded Gambit", Material.IRON_PICKAXE, 2100, 4800, "minecraft:nether/find_bastion", "Those Were the Days", 100, List.of(new Objective("break:GILDED_BLACKSTONE", 40, "Mine 40 Gilded Blackstone"))));
        result.add(new Pack("M016", "Hoglin Husbandry", Material.WHEAT, 2100, 4650, "minecraft:nether/find_bastion", "Those Were the Days", 0, List.of(new Objective("harvest:WHEAT", 48, "Harvest 48 Wheat"))));
        result.add(new Pack("M017", "Skull Magnate", Material.NETHERITE_SWORD, 2350, 5700, "minecraft:nether/get_wither_skull", "Spooky Scary Skeleton", 0, List.of(new Objective("kill:WITHER_SKELETON", 120, "Defeat 120 Wither Skeleton"))));
        result.add(new Pack("M018", "Blaze Baron", Material.NETHERITE_SWORD, 2100, 5300, "minecraft:nether/obtain_blaze_rod", "Into Fire", 215, List.of(new Objective("kill:BLAZE", 120, "Defeat 120 Blaze"))));
        result.add(new Pack("M019", "Bastion Vault", Material.NETHERITE_SWORD, 2550, 5600, "minecraft:nether/loot_bastion", "War Pigs", 0, List.of(new Objective("kill:PIGLIN", 20, "Defeat 20 Piglin"))));
        result.add(new Pack("M020", "Fortress Overlord", Material.NETHERITE_SWORD, 2550, 6250, "minecraft:nether/find_fortress", "A Terrible Fortress", 265, List.of(new Objective("kill:WITHER_SKELETON", 50, "Defeat 50 Wither Skeleton"), new Objective("kill:BLAZE", 50, "Defeat 50 Blaze"), new Objective("kill:BLAZE", 30, "Defeat 30 Blaze"))));
        result.add(new Pack("M021", "Wither Wrecker", Material.NETHERITE_SWORD, 2800, 6400, "minecraft:nether/summon_wither", "Withering Heights", 225, List.of(new Objective("kill:WITHER", 1, "Defeat 1 Wither"))));
        result.add(new Pack("M022", "Triple Wither", Material.NETHERITE_SWORD, 3000, 7750, "minecraft:nether/summon_wither", "Withering Heights", 0, List.of(new Objective("kill:WITHER", 3, "Defeat 3 Wither"))));
        result.add(new Pack("M023", "Dragon Slayer Redux", Material.NETHERITE_SWORD, 3000, 6950, "minecraft:end/respawn_dragon", "The End... Again...", 0, List.of(new Objective("kill:ENDER_DRAGON", 1, "Defeat 1 Ender Dragon"))));
        result.add(new Pack("M024", "Elder Guardian Trio", Material.NETHERITE_SWORD, 2800, 6650, "minecraft:story/shiny_gear", "Cover Me with Diamonds", 275, List.of(new Objective("kill:ELDER_GUARDIAN", 3, "Defeat 3 Elder Guardian"))));
        result.add(new Pack("M025", "Warden Duel", Material.NETHERITE_SWORD, 3000, 6950, "minecraft:adventure/avoid_vibration", "Sneak 100", 0, List.of(new Objective("kill:WARDEN", 1, "Defeat 1 Warden"))));
        result.add(new Pack("M026", "Roaming Boss Triple", Material.NETHERITE_SWORD, 2800, 7400, "minecraft:adventure/kill_a_mob", "Monster Hunter", 0, List.of(new Objective("kill:@ROAMING_BOSS", 3, "Defeat 3 @roaming Boss"))));
        result.add(new Pack("M027", "Raid Breaker", Material.NETHERITE_SWORD, 2100, 5200, "minecraft:adventure/voluntary_exile", "Voluntary Exile", 200, List.of(new Objective("kill:PILLAGER", 100, "Defeat 100 Pillager"))));
        result.add(new Pack("M028", "Ravager Rodeo", Material.NETHERITE_SWORD, 2800, 6550, "minecraft:adventure/voluntary_exile", "Voluntary Exile", 250, List.of(new Objective("kill:RAVAGER", 8, "Defeat 8 Ravager"))));
        result.add(new Pack("M029", "Evoker Executioner", Material.NETHERITE_SWORD, 2800, 6500, "minecraft:adventure/totem_of_undying", "Postmortal", 0, List.of(new Objective("kill:EVOKER", 6, "Defeat 6 Evoker"))));
        result.add(new Pack("M030", "Boss Rush", Material.NETHERITE_SWORD, 3000, 8000, "minecraft:end/respawn_dragon", "The End... Again...", 0, List.of(new Objective("kill:ELDER_GUARDIAN", 1, "Defeat 1 Elder Guardian"), new Objective("kill:WITHER", 1, "Defeat 1 Wither"), new Objective("kill:ENDER_DRAGON", 1, "Defeat 1 Ender Dragon"))));
        result.add(new Pack("M031", "Sculk Sweeper", Material.IRON_PICKAXE, 1650, 4150, "minecraft:adventure/avoid_vibration", "Sneak 100", 100, List.of(new Objective("break:SCULK", 600, "Mine 600 Sculk"))));
        result.add(new Pack("M032", "Ancient City Raid", Material.IRON_PICKAXE, 2800, 6400, "minecraft:adventure/avoid_vibration", "Sneak 100", 0, List.of(new Objective("break:SCULK", 160, "Mine 160 Sculk"))));
        result.add(new Pack("M033", "Ice Box Heist", Material.IRON_PICKAXE, 2550, 5600, "minecraft:adventure/avoid_vibration", "Sneak 100", 0, List.of(new Objective("break:SCULK", 48, "Mine 48 Sculk"))));
        result.add(new Pack("M034", "Deepslate Titan", Material.IRON_PICKAXE, 1200, 3550, "minecraft:story/mine_diamond", "Diamonds!", 120, List.of(new Objective("break:DEEPSLATE", 500, "Mine 500 Deepslate"))));
        result.add(new Pack("M035", "Diamond Dynasty", Material.IRON_PICKAXE, 2550, 6000, "minecraft:story/shiny_gear", "Cover Me with Diamonds", 0, List.of(new Objective("break:DIAMOND_ORE", 64, "Mine 64 Diamond Ore"))));
        result.add(new Pack("M036", "Emerald Empire", Material.IRON_PICKAXE, 2550, 6150, "minecraft:story/mine_diamond", "Diamonds!", 0, List.of(new Objective("break:EMERALD_ORE", 32, "Mine 32 Emerald Ore"))));
        result.add(new Pack("M037", "Redstone Reactor", Material.IRON_PICKAXE, 1650, 4200, "minecraft:story/mine_diamond", "Diamonds!", 110, List.of(new Objective("break:REDSTONE_ORE", 600, "Mine 600 Redstone Ore"))));
        result.add(new Pack("M038", "Lapis Legion", Material.IRON_PICKAXE, 1650, 4200, "minecraft:story/enchant_item", "Enchanter", 110, List.of(new Objective("break:LAPIS_ORE", 400, "Mine 400 Lapis Ore"))));
        result.add(new Pack("M039", "Geode Gatherer", Material.IRON_PICKAXE, 2100, 5100, "minecraft:story/mine_diamond", "Diamonds!", 140, List.of(new Objective("break:AMETHYST_CLUSTER", 80, "Mine 80 Amethyst Cluster"))));
        result.add(new Pack("M040", "Dark Descent", Material.IRON_PICKAXE, 1900, 5000, "minecraft:story/shiny_gear", "Cover Me with Diamonds", 0, List.of(new Objective("break:ANY_ORE", 500, "Mine 500 Any Ore"))));
        result.add(new Pack("M041", "Mansion Plunder", Material.NETHERITE_SWORD, 2800, 6400, "minecraft:adventure/voluntary_exile", "Voluntary Exile", 0, List.of(new Objective("kill:VINDICATOR", 40, "Defeat 40 Vindicator"))));
        result.add(new Pack("M042", "Mansion Cleansing", Material.NETHERITE_SWORD, 2550, 6050, "minecraft:adventure/voluntary_exile", "Voluntary Exile", 225, List.of(new Objective("kill:VINDICATOR", 30, "Defeat 30 Vindicator"), new Objective("kill:EVOKER", 3, "Defeat 3 Evoker"), new Objective("kill:VEX", 20, "Defeat 20 Vex"))));
        result.add(new Pack("M043", "Outpost Overlord", Material.NETHERITE_SWORD, 2100, 5100, "minecraft:adventure/voluntary_exile", "Voluntary Exile", 175, List.of(new Objective("kill:PILLAGER", 30, "Defeat 30 Pillager"), new Objective("kill:PILLAGER", 40, "Defeat 40 Pillager"))));
        result.add(new Pack("M044", "Captain Hunter", Material.NETHERITE_SWORD, 1900, 4900, "minecraft:adventure/voluntary_exile", "Voluntary Exile", 200, List.of(new Objective("kill:PILLAGER", 80, "Defeat 80 Pillager"))));
        result.add(new Pack("M045", "Illager Annihilator", Material.NETHERITE_SWORD, 2100, 5200, "minecraft:adventure/voluntary_exile", "Voluntary Exile", 200, List.of(new Objective("kill:VINDICATOR", 50, "Defeat 50 Vindicator"))));
        result.add(new Pack("M046", "Totem Trader", Material.NETHERITE_SWORD, 2800, 6750, "minecraft:adventure/totem_of_undying", "Postmortal", 0, List.of(new Objective("kill:EVOKER", 10, "Defeat 10 Evoker"))));
        result.add(new Pack("M047", "Witch Doctor", Material.NETHERITE_SWORD, 2100, 5150, "minecraft:story/shiny_gear", "Cover Me with Diamonds", 190, List.of(new Objective("kill:WITCH", 40, "Defeat 40 Witch"))));
        result.add(new Pack("M048", "Patrol Marathon", Material.NETHERITE_SWORD, 1900, 5150, "minecraft:adventure/voluntary_exile", "Voluntary Exile", 250, List.of(new Objective("kill:PILLAGER", 150, "Defeat 150 Pillager"))));
        result.add(new Pack("M049", "Vex Vendetta", Material.NETHERITE_SWORD, 2100, 5100, "minecraft:adventure/voluntary_exile", "Voluntary Exile", 175, List.of(new Objective("kill:VEX", 60, "Defeat 60 Vex"))));
        result.add(new Pack("M050", "Raid Master", Material.NETHERITE_SWORD, 2550, 6650, "minecraft:adventure/hero_of_the_village", "Hero of the Village", 0, List.of(new Objective("kill:PILLAGER", 200, "Defeat 200 Pillager"))));
        result.add(new Pack("M051", "Trial Initiate", Material.NETHERITE_SWORD, 2100, 4950, "minecraft:adventure/minecraft_trials_edition", "Minecraft: Trial(s) Edition", 120, List.of(new Objective("kill:BREEZE", 40, "Defeat 40 Breeze"))));
        result.add(new Pack("M052", "Vault Cracker", Material.NETHERITE_SWORD, 2350, 5450, "minecraft:adventure/under_lock_and_key", "Under Lock and Key", 140, List.of(new Objective("kill:BREEZE", 15, "Defeat 15 Breeze"))));
        result.add(new Pack("M053", "Breeze Breaker", Material.NETHERITE_SWORD, 2350, 5450, "minecraft:adventure/minecraft_trials_edition", "Minecraft: Trial(s) Edition", 175, List.of(new Objective("kill:BREEZE", 30, "Defeat 30 Breeze"))));
        result.add(new Pack("M054", "Bogged Down", Material.NETHERITE_SWORD, 1900, 4650, "minecraft:adventure/minecraft_trials_edition", "Minecraft: Trial(s) Edition", 150, List.of(new Objective("kill:BOGGED", 40, "Defeat 40 Bogged"))));
        result.add(new Pack("M055", "Spawner Sweep", Material.NETHERITE_SWORD, 2100, 5200, "minecraft:adventure/minecraft_trials_edition", "Minecraft: Trial(s) Edition", 200, List.of(new Objective("kill:ZOMBIE", 80, "Defeat 80 Zombie"))));
        result.add(new Pack("M056", "Chamber Looter", Material.NETHERITE_SWORD, 2350, 5700, "minecraft:adventure/minecraft_trials_edition", "Minecraft: Trial(s) Edition", 0, List.of(new Objective("kill:BREEZE", 100, "Defeat 100 Breeze"))));
        result.add(new Pack("M057", "Vault Veteran", Material.NETHERITE_SWORD, 2550, 6150, "minecraft:adventure/revaulting", "Revaulting", 0, List.of(new Objective("kill:BREEZE", 40, "Defeat 40 Breeze"))));
        result.add(new Pack("M058", "Ominous Trials", Material.NETHERITE_SWORD, 3000, 7200, "minecraft:adventure/revaulting", "Revaulting", 0, List.of(new Objective("kill:BREEZE", 15, "Defeat 15 Breeze"))));
        result.add(new Pack("M059", "Trial Marathon", Material.NETHERITE_SWORD, 2350, 5800, "minecraft:adventure/under_lock_and_key", "Under Lock and Key", 0, List.of(new Objective("kill:ZOMBIE", 200, "Defeat 200 Zombie"))));
        result.add(new Pack("M060", "Heavy Hitter", Material.NETHERITE_SWORD, 2550, 6150, "minecraft:adventure/overoverkill", "Over-Overkill", 250, List.of(new Objective("kill:ZOMBIE", 150, "Defeat 150 Zombie"))));
        result.add(new Pack("M061", "Monument Marauder", Material.NETHERITE_SWORD, 2100, 5350, "minecraft:story/shiny_gear", "Cover Me with Diamonds", 225, List.of(new Objective("kill:GUARDIAN", 80, "Defeat 80 Guardian"))));
        result.add(new Pack("M062", "Elder Duo", Material.NETHERITE_SWORD, 2800, 6400, "minecraft:story/shiny_gear", "Cover Me with Diamonds", 0, List.of(new Objective("kill:ELDER_GUARDIAN", 2, "Defeat 2 Elder Guardian"))));
        result.add(new Pack("M063", "Sponge Collector", Material.IRON_PICKAXE, 2550, 5600, "minecraft:story/shiny_gear", "Cover Me with Diamonds", 0, List.of(new Objective("break:WET_SPONGE", 24, "Mine 24 Wet Sponge"))));
        result.add(new Pack("M064", "Prismarine Palace", Material.IRON_PICKAXE, 1650, 4400, "minecraft:story/mine_diamond", "Diamonds!", 140, List.of(new Objective("break:PRISMARINE", 800, "Mine 800 Prismarine"))));
        result.add(new Pack("M065", "Sea Lantern Empire", Material.IRON_PICKAXE, 2100, 5100, "minecraft:story/mine_diamond", "Diamonds!", 140, List.of(new Objective("break:SEA_LANTERN", 80, "Mine 80 Sea Lantern"))));
        result.add(new Pack("M066", "Trench Diver", Material.NETHERITE_SWORD, 1650, 4650, "minecraft:husbandry/fishy_business", "Fishy Business", 225, List.of(new Objective("kill:DROWNED", 150, "Defeat 150 Drowned"))));
        result.add(new Pack("M067", "Treasure Tycoon II", Material.FISHING_ROD, 2350, 5700, "minecraft:husbandry/fishy_business", "Fishy Business", 0, List.of(new Objective("fish:COD", 12, "Catch 12 Cod"))));
        result.add(new Pack("M068", "Tropical Tycoon", Material.FISHING_ROD, 2550, 6250, "minecraft:husbandry/tactical_fishing", "Tactical Fishing", 0, List.of(new Objective("fish:TROPICAL_FISH", 10, "Catch 10 Tropical Fish"))));
        result.add(new Pack("M069", "Pufferfish Plague II", Material.FISHING_ROD, 2100, 5300, "minecraft:husbandry/fishy_business", "Fishy Business", 0, List.of(new Objective("fish:PUFFERFISH", 40, "Catch 40 Pufferfish"))));
        result.add(new Pack("M070", "Deep Ocean Voyager", Material.COMPASS, 1900, 4850, "minecraft:story/iron_tools", "Isn't It Iron Pick", 0, List.of(new Objective("travel", 500, "Travel 500 blocks"), new Objective("travel", 500, "Travel 500 blocks"))));
        result.add(new Pack("M071", "Miner's Grand Slam", Material.IRON_PICKAXE, 2800, 7050, "minecraft:story/shiny_gear", "Cover Me with Diamonds", 0, List.of(new Objective("break:DIAMOND_ORE", 32, "Mine 32 Diamond Ore"), new Objective("break:EMERALD_ORE", 12, "Mine 12 Emerald Ore"), new Objective("break:GOLD_ORE", 64, "Mine 64 Gold Ore"), new Objective("break:ANCIENT_DEBRIS", 6, "Mine 6 Ancient Debris"))));
        result.add(new Pack("M072", "Hunter's Grand Slam", Material.NETHERITE_SWORD, 2350, 5950, "minecraft:story/shiny_gear", "Cover Me with Diamonds", 275, List.of(new Objective("kill:ENDERMAN", 30, "Defeat 30 Enderman"), new Objective("kill:BLAZE", 30, "Defeat 30 Blaze"), new Objective("kill:WITCH", 15, "Defeat 15 Witch"), new Objective("kill:CREEPER", 40, "Defeat 40 Creeper"))));
        result.add(new Pack("M073", "Farmer's Grand Slam", Material.WHEAT, 1650, 4800, "minecraft:husbandry/plant_seed", "A Seedy Place", 200, List.of(new Objective("harvest:WHEAT", 512, "Harvest 512 Wheat"), new Objective("harvest:CARROT", 512, "Harvest 512 Carrot"), new Objective("harvest:POTATO", 512, "Harvest 512 Potato"), new Objective("harvest:MELON_SLICE", 128, "Harvest 128 Melon Slice"), new Objective("harvest:PUMPKIN", 128, "Harvest 128 Pumpkin"))));
        result.add(new Pack("M074", "Angler's Grand Slam", Material.FISHING_ROD, 2350, 5950, "minecraft:husbandry/tactical_fishing", "Tactical Fishing", 0, List.of(new Objective("fish:COD", 60, "Catch 60 Cod"), new Objective("fish:SALMON", 40, "Catch 40 Salmon"), new Objective("fish:PUFFERFISH", 20, "Catch 20 Pufferfish"), new Objective("fish:TROPICAL_FISH", 4, "Catch 4 Tropical Fish"))));
        result.add(new Pack("M075", "Smith's Grand Slam", Material.FURNACE, 1900, 5150, "minecraft:story/mine_diamond", "Diamonds!", 200, List.of(new Objective("smelt:IRON_INGOT", 256, "Smelt 256 Iron Ingot"), new Objective("smelt:GOLD_INGOT", 96, "Smelt 96 Gold Ingot"), new Objective("smelt:COPPER_INGOT", 256, "Smelt 256 Copper Ingot"), new Objective("smelt:GLASS", 512, "Smelt 512 Glass"))));
        result.add(new Pack("M076", "Explorer's Grand Slam", Material.COMPASS, 2100, 5950, "minecraft:adventure/sleep_in_bed", "Sweet Dreams", 0, List.of(new Objective("travel", 500, "Travel 500 blocks"), new Objective("travel", 500, "Travel 500 blocks"))));
        result.add(new Pack("M077", "Looter's Grand Slam", Material.NETHERITE_SWORD, 2350, 6100, "minecraft:story/shiny_gear", "Cover Me with Diamonds", 0, List.of(new Objective("kill:ZOMBIE", 200, "Defeat 200 Zombie"))));
        result.add(new Pack("M078", "Breeder's Grand Slam", Material.WHEAT, 1900, 5250, "minecraft:husbandry/bred_all_animals", "Two by Two", 0, List.of(new Objective("harvest:WHEAT", 400, "Harvest 400 Wheat"))));
        result.add(new Pack("M079", "Dimension Hopper", Material.IRON_PICKAXE, 2800, 6900, "minecraft:end/find_end_city", "The City at the End of the Game", 0, List.of(new Objective("break:ANCIENT_DEBRIS", 8, "Mine 8 Ancient Debris"), new Objective("kill:SHULKER", 8, "Defeat 8 Shulker"), new Objective("fish:COD", 100, "Catch 100 Cod"))));
        result.add(new Pack("M080", "Master Chef", Material.FURNACE, 1650, 4650, "minecraft:husbandry/balanced_diet", "A Balanced Diet", 180, List.of(new Objective("smelt:COOKED_BEEF", 1100, "Smelt 1100 Cooked Beef"))));
        result.add(new Pack("M081", "Mountain Mover", Material.IRON_PICKAXE, 1000, 3500, "minecraft:story/mine_diamond", "Diamonds!", 0, List.of(new Objective("break:STONE", 500, "Mine 500 Stone"))));
        result.add(new Pack("M082", "Ore Baron", Material.IRON_PICKAXE, 1900, 5500, "minecraft:story/shiny_gear", "Cover Me with Diamonds", 0, List.of(new Objective("break:ANY_ORE", 500, "Mine 500 Any Ore"))));
        result.add(new Pack("M083", "Forest Eater", Material.IRON_PICKAXE, 1200, 3950, "minecraft:story/iron_tools", "Isn't It Iron Pick", 0, List.of(new Objective("break:OAK_LOG", 500, "Mine 500 Oak Log"))));
        result.add(new Pack("M084", "Field Marshal", Material.WHEAT, 1200, 4200, "minecraft:husbandry/plant_seed", "A Seedy Place", 0, List.of(new Objective("harvest:WHEAT", 4000, "Harvest 4000 Wheat"))));
        result.add(new Pack("M085", "Great Wall Walk", Material.COMPASS, 1450, 5600, "minecraft:story/iron_tools", "Isn't It Iron Pick", 0, List.of(new Objective("travel", 500, "Travel 500 blocks"))));
        result.add(new Pack("M086", "Slayer Ascendant", Material.NETHERITE_SWORD, 1650, 5800, "minecraft:story/shiny_gear", "Cover Me with Diamonds", 0, List.of(new Objective("kill:ZOMBIE", 1000, "Defeat 1000 Zombie"))));
        result.add(new Pack("M087", "Angler Ascendant", Material.FISHING_ROD, 1450, 5200, "minecraft:husbandry/tactical_fishing", "Tactical Fishing", 0, List.of(new Objective("fish:COD", 900, "Catch 900 Cod"))));
        result.add(new Pack("M088", "Furnace Emperor", Material.FURNACE, 1450, 4850, "minecraft:story/mine_diamond", "Diamonds!", 0, List.of(new Objective("smelt:ANY", 3000, "Smelt 3000 Any"))));
        result.add(new Pack("M089", "Herd Master", Material.WHEAT, 1200, 3950, "minecraft:husbandry/breed_an_animal", "The Parrots and the Bats", 0, List.of(new Objective("harvest:WHEAT", 500, "Harvest 500 Wheat"))));
        result.add(new Pack("M090", "Nether Wanderer", Material.COMPASS, 1650, 4750, "minecraft:nether/fast_travel", "Subspace Bubble", 0, List.of(new Objective("travel", 500, "Travel 500 blocks"))));
        result.add(new Pack("M091", "Three Realms Trial", Material.IRON_PICKAXE, 2550, 6550, "minecraft:end/find_end_city", "The City at the End of the Game", 325, List.of(new Objective("break:IRON_ORE", 128, "Mine 128 Iron Ore"), new Objective("kill:BLAZE", 30, "Defeat 30 Blaze"), new Objective("kill:ENDERMAN", 30, "Defeat 30 Enderman"))));
        result.add(new Pack("M092", "Netherite Initiation", Material.IRON_PICKAXE, 2800, 6900, "minecraft:nether/obtain_ancient_debris", "Hidden in the Depths", 0, List.of(new Objective("break:ANCIENT_DEBRIS", 8, "Mine 8 Ancient Debris"), new Objective("smelt:NETHERITE_SCRAP", 8, "Smelt 8 Netherite Scrap"), new Objective("kill:WITHER_SKELETON", 30, "Defeat 30 Wither Skeleton"))));
        result.add(new Pack("M093", "End Game Gauntlet", Material.NETHERITE_SWORD, 2800, 6800, "minecraft:end/find_end_city", "The City at the End of the Game", 300, List.of(new Objective("kill:SHULKER", 12, "Defeat 12 Shulker"), new Objective("kill:SHULKER", 30, "Defeat 30 Shulker"), new Objective("kill:ENDERMAN", 40, "Defeat 40 Enderman"))));
        result.add(new Pack("M094", "Abyss Walker", Material.NETHERITE_SWORD, 3000, 7350, "minecraft:adventure/avoid_vibration", "Sneak 100", 0, List.of(new Objective("kill:WARDEN", 1, "Defeat 1 Warden"), new Objective("break:SCULK", 96, "Mine 96 Sculk"))));
        result.add(new Pack("M095", "Siege of the Wither", Material.NETHERITE_SWORD, 3000, 7250, "minecraft:nether/summon_wither", "Withering Heights", 325, List.of(new Objective("kill:WITHER", 1, "Defeat 1 Wither"), new Objective("kill:WITHER_SKELETON", 40, "Defeat 40 Wither Skeleton"))));
        result.add(new Pack("M096", "Pillager's Doom", Material.NETHERITE_SWORD, 3000, 7200, "minecraft:adventure/voluntary_exile", "Voluntary Exile", 0, List.of(new Objective("kill:VINDICATOR", 30, "Defeat 30 Vindicator"), new Objective("kill:EVOKER", 4, "Defeat 4 Evoker"), new Objective("kill:RAVAGER", 4, "Defeat 4 Ravager"))));
        result.add(new Pack("M097", "Ocean Overlord", Material.NETHERITE_SWORD, 2800, 7200, "minecraft:story/shiny_gear", "Cover Me with Diamonds", 360, List.of(new Objective("kill:ELDER_GUARDIAN", 2, "Defeat 2 Elder Guardian"), new Objective("kill:GUARDIAN", 60, "Defeat 60 Guardian"), new Objective("break:SPONGE", 16, "Mine 16 Sponge"))));
        result.add(new Pack("M098", "Trial by Fire", Material.NETHERITE_SWORD, 2800, 6900, "minecraft:adventure/under_lock_and_key", "Under Lock and Key", 325, List.of(new Objective("kill:BREEZE", 40, "Defeat 40 Breeze"), new Objective("kill:BREEZE", 25, "Defeat 25 Breeze"), new Objective("kill:BLAZE", 40, "Defeat 40 Blaze"))));
        result.add(new Pack("M099", "Legend of the Wanderer", Material.COMPASS, 2800, 7700, "minecraft:adventure/sleep_in_bed", "Sweet Dreams", 0, List.of(new Objective("travel", 500, "Travel 500 blocks"), new Objective("travel", 500, "Travel 500 blocks"), new Objective("kill:ZOMBIE", 150, "Defeat 150 Zombie"))));
        result.add(new Pack("M100", "The Grand Master", Material.NETHERITE_SWORD, 3000, 8000, "minecraft:end/respawn_dragon", "The End... Again...", 0, List.of(new Objective("kill:ENDER_DRAGON", 1, "Defeat 1 Ender Dragon"), new Objective("kill:WITHER", 1, "Defeat 1 Wither"), new Objective("kill:ELDER_GUARDIAN", 1, "Defeat 1 Elder Guardian"), new Objective("kill:WARDEN", 1, "Defeat 1 Warden"))));
        return List.copyOf(result);
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
                    : !tierUnlocked(player, tierOf(pack)) ? ChatColor.RED + tierUnlockDescription(tierOf(pack))
                    : unlocked ? ChatColor.GREEN + "UNLOCKED"
                    : ChatColor.RED + "Locked: earn " + pack.unlockTitle() + " (" + pack.advancement() + ")");
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
        lore.add(ChatColor.WHITE + "Fee: " + pack.cost() + " coins");
        lore.add(ChatColor.WHITE + "Reward: " + rewardForPack(pack) + " coins");
        lore.add(ChatColor.GOLD + "Profit: +" + (rewardForPack(pack) - pack.cost()) + " coins");
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
        incrementTarget(player, "smelt:" + material.name(), amount);
        incrementTarget(player, "smelt:ANY", amount);
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
