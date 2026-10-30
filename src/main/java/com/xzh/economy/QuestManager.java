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
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityTameEvent;
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
    private record RandomQuest(String name, Material icon, int cost, int reward, Objective objective, boolean timed) {
        private RandomQuest(String name, Material icon, int cost, int reward, Objective objective) {
            this(name, icon, cost, reward, objective, false);
        }
    }
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
    private final QuestCatalogue v2Catalogue;
    private final NamespacedKey actionKey;
    private final NamespacedKey valueKey;
    private final NamespacedKey mobSpawnReasonKey;
    private final NamespacedKey mobSpawnerKey;
    private final NamespacedKey directMeleeKillerKey;
    private final NamespacedKey feedCooldownKey;
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
    private final List<Pack> packs;

    private List<Pack> buildPacks() {
        List<Pack> result = new ArrayList<>(v2Catalogue.entries().size());
        for (QuestCatalogue.Entry entry : v2Catalogue.entries()) {
            List<Objective> objectives = new ArrayList<>(entry.parsedObjectives().size());
            for (QuestObjectiveSpec spec : entry.parsedObjectives()) {
                String target = String.join("|", spec.targets());
                String prefix = switch (spec.type()) {
                    case MINE -> "break:";
                    case HARVEST -> "harvest:";
                    case KILL -> "kill:";
                    case FISH -> "fish:";
                    case BREED -> "breed:";
                    case FEED -> "feed:";
                    case TAME -> "tame:";
                    case SMELT -> "smelt:";
                    case CRAFT -> "craft:";
                    case ENCHANT -> "enchant:";
                    case BARTER -> "barter:";
                    case LOOT -> "loot:";
                    case VAULT -> "vault:";
                    case TRAVEL -> "travel:";
                    case BIOME -> "biome:";
                };
                String description = spec.quantity() + " " + target.toLowerCase(Locale.ROOT).replace('_', ' ');
                String dimension = spec.dimension() == QuestObjectiveSpec.Dimension.ANY ? ""
                        : "@" + spec.dimension().name();
                objectives.add(new Objective(prefix + dimension + ":" + target, spec.quantity(), description));
            }
            String advancement = entry.unlock().isEmpty() ? entry.tierGate() : entry.unlock().get(0);
            result.add(new Pack(entry.id(), entry.name(), entry.icon(), entry.fee(),
                    entry.reward(), advancement, List.copyOf(objectives)));
        }
        return List.copyOf(result);
    }

    private String tierOf(Pack pack) {
        String id = pack.id().toUpperCase(Locale.ROOT);
        if (id.startsWith("M") && id.length() == 4) return "master";
        if (id.startsWith("H") && id.length() == 4) return "hard";
        if (id.startsWith("E") && id.length() == 4) return "easy";
        return pack.id().startsWith("master_") ? "master" : pack.id().startsWith("hard_") ? "hard" : "easy";
    }

    private boolean tierUnlocked(Player player, String tier) {
        return switch (tier) {
            case "hard" -> hasAdvancement(player, "minecraft:nether/find_fortress");
            case "master" -> hasAdvancement(player, "minecraft:end/find_end_city");
            default -> true;
        };
    }

    private String tierUnlockDescription(String tier) {
        return switch (tier) {
            case "hard" -> "Unlock Hard packs by reaching A Terrible Fortress (minecraft:nether/find_fortress)";
            case "master" -> "Unlock Master packs by reaching The City at the End of the Game (minecraft:end/find_end_city)";
            default -> "Easy packs are available from the start; individual packs have requirements";
        };
    }

    private boolean completedPack(UUID playerId, String packId) {
        return data.getBoolean("completed-packs." + playerId + "." + packId, false);
    }

    private final List<RandomQuest> randomPool = List.of(
        new RandomQuest("Deep Diamond Expedition", Material.DIAMOND, 180, 252, new Objective("break:DIAMOND_ORE", 12, "Mine 12 Diamond Ore")),
        new RandomQuest("Ancient Debris Search", Material.ANCIENT_DEBRIS, 260, 390, new Objective("break:ANCIENT_DEBRIS", 8, "Mine 8 Ancient Debris"), true),
        new RandomQuest("Raid the Fortress", Material.BLAZE_ROD, 220, 330, new Objective("kill:BLAZE", 18, "Kill 18 Blazes in the Nether"), true),
        new RandomQuest("Undead Patrol", Material.BOW, 150, 210, new Objective("kill:ZOMBIE", 35, "Kill 35 Zombies")),
        new RandomQuest("Ocean Treasure Hunt", Material.PRISMARINE_SHARD, 200, 300, new Objective("break:PRISMARINE", 96, "Mine 96 Prismarine blocks")),
        new RandomQuest("Ender Pearl Collection", Material.ENDER_PEARL, 240, 360, new Objective("kill:ENDERMAN", 24, "Kill 24 Endermen")),
        new RandomQuest("Iron Supply Contract", Material.RAW_IRON, 130, 182, new Objective("break:IRON_ORE", 64, "Mine 64 Iron Ore")),
        new RandomQuest("Wither Skeleton Hunt", Material.WITHER_SKELETON_SKULL, 350, 525, new Objective("kill:WITHER_SKELETON", 16, "Kill 16 Wither Skeletons"), true),
        new RandomQuest("Fishing for Cod", Material.COD, 90, 126, new Objective("fish:COD", 20, "Catch 20 Cod"), true),
        new RandomQuest("Fishing for Salmon", Material.SALMON, 110, 154, new Objective("fish:SALMON", 20, "Catch 20 Salmon")),
        new RandomQuest("Feed the Cows", Material.WHEAT, 100, 140, new Objective("feed:COW", 20, "Feed 20 cows")),
        new RandomQuest("Animal Breeding", Material.PORKCHOP, 120, 168, new Objective("breed:ANY", 10, "Breed 10 animals")),
        new RandomQuest("Travel Contract", Material.COMPASS, 100, 140, new Objective("travel", 2500, "Travel 2,500 blocks"), true),
        new RandomQuest("Woodcutting Contract", Material.OAK_LOG, 100, 140, new Objective("break:OAK_LOG", 128, "Chop 128 Oak Logs")),
        new RandomQuest("Crop Delivery", Material.CARROT, 85, 119, new Objective("harvest:CARROT", 96, "Harvest 96 fully grown Carrot crops")),
        new RandomQuest("Blaze Rod Shipment", Material.BLAZE_ROD, 190, 266, new Objective("kill:@NETHER:BLAZE", 32, "Kill 32 Blazes in the Nether")),
        new RandomQuest("Sand Quarry", Material.SAND, 75, 105, new Objective("break:SAND", 256, "Mine 256 Sand")),
        new RandomQuest("Monster Cleanup", Material.IRON_SWORD, 140, 196, new Objective("kill:SKELETON", 30, "Kill 30 Skeletons")),
        new RandomQuest("Food Pantry", Material.BREAD, 90, 126, new Objective("craft:BREAD", 64, "Craft 64 Bread")),
        new RandomQuest("Quartz Shipment", Material.QUARTZ, 160, 224, new Objective("break:@NETHER:NETHER_QUARTZ_ORE", 96, "Mine 96 Nether Quartz Ore in the Nether")),
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
        v2Catalogue = QuestCatalogue.load(plugin);
        validateCatalogueAdvancements();
        packs = buildPacks();
        // Run the legacy-pack refund exactly once. Without this marker, every restart
        // would cancel and refund a currently active v2 pack.
        if (!data.getBoolean("v2-migration-complete", false)) {
            for (String root : new ArrayList<>(data.getKeys(false))) {
                try {
                    UUID playerId = UUID.fromString(root);
                    String active = data.getString(root + ".active", "");
                    if (active.isBlank()) continue;
                    boolean legacyPack = active.startsWith("pack:") || !active.startsWith("single:");
                    if (!legacyPack) continue;
                    int refund = Math.max(0, data.getInt(root + ".cost", 0));
                    if (refund > 0) plugin.setBalance(playerId, plugin.getBalance(playerId) + refund);
                    data.set(root, null);
                    data.set("v2-migration-notice." + playerId, true);
                } catch (IllegalArgumentException ignored) { }
            }
            data.set("v2-migration-complete", true);
            try { data.save(file); }
            catch (IOException e) { plugin.getLogger().severe("Could not save one-time quest migration: " + e.getMessage()); }
        }
        actionKey = new NamespacedKey(plugin, "quest_action");
        valueKey = new NamespacedKey(plugin, "quest_value");
        mobSpawnReasonKey = new NamespacedKey(plugin, "quest_mob_spawn_reason");
        mobSpawnerKey = new NamespacedKey(plugin, "quest_mob_spawner");
        directMeleeKillerKey = new NamespacedKey(plugin, "quest_direct_melee_killer");
        feedCooldownKey = new NamespacedKey(plugin, "quest_feed_cooldown");
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

    private void validateCatalogueAdvancements() {
        Set<String> checked = new HashSet<>();
        for (QuestCatalogue.Entry entry : v2Catalogue.entries()) {
            List<String> keys = new ArrayList<>(entry.unlock());
            keys.add(entry.tierGate());
            for (String raw : keys) {
                String key = raw.contains(":") ? raw : "minecraft:" + raw;
                if (!checked.add(key)) continue;
                NamespacedKey namespaced = NamespacedKey.fromString(key);
                if (namespaced == null || Bukkit.getAdvancement(namespaced) == null) {
                    plugin.getLogger().warning("Quest catalogue references missing advancement '" + key
                            + "' (used by " + entry.id() + ").");
                }
            }
        }
    }

    /**
     * Integration hook for XZHSurvivalPlugin's tree-felling listener.
     * The caller must invoke this once per non-player-placed felled log and only
     * after its own cancellation/ownership checks have passed.
     */
    public void recordFelledLogs(Player player, Material material, int amount) {
        if (player == null || material == null || amount <= 0
                || player.getGameMode() == org.bukkit.GameMode.CREATIVE
                || player.getGameMode() == org.bukkit.GameMode.SPECTATOR) return;
        String name = material.name();
        if (!(name.endsWith("_LOG") || name.endsWith("_STEM"))) return;
        incrementTarget(player, "break:" + name, amount);
        recordGlobalProgress(player, "wood", amount);
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
            boolean unlocked = catalogueEntryFor(pack.id()).map(entry -> catalogueEntryUnlocked(player, entry))
                    .orElseGet(() -> tierUnlocked(player, tierOf(pack)) && hasAdvancement(player, pack.advancement()));
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.WHITE + "Cost: " + ChatColor.YELLOW + pack.cost() + " coins");
            lore.add(ChatColor.WHITE + "Reward: " + ChatColor.GREEN + rewardForPack(pack) + " coins");
            lore.add(ChatColor.WHITE + "Profit: " + ChatColor.GOLD + "+" + (rewardForPack(pack) - pack.cost()) + " coins");
            lore.add(ChatColor.WHITE + "Objectives:");
            for (Objective objective : pack.objectives()) lore.add(ChatColor.AQUA + "• " + objective.description());
            if (done) {
                lore.add(ChatColor.DARK_GRAY + "COMPLETED FOR LIFE — reward already claimed");
            } else {
                List<String> missing = catalogueEntryFor(pack.id())
                        .map(entry -> missingCatalogueUnlocks(player, entry)).orElse(List.of());
                if (!tierUnlocked(player, tierOf(pack))) lore.add(ChatColor.RED + tierUnlockDescription(tierOf(pack)));
                for (String key : missing) {
                    String normalized = key.contains(":") ? key : "minecraft:" + key;
                    NamespacedKey advancementKey = NamespacedKey.fromString(normalized);
                    Advancement required = advancementKey == null ? null : Bukkit.getAdvancement(advancementKey);
                    String title = required == null ? "(invalid key)" : required.getKey().getKey().replace('_', ' ');
                    lore.add(ChatColor.RED + "Locked: requires " + title + " (" + normalized + ")");
                }
                if (missing.isEmpty()) lore.add(ChatColor.GREEN + "UNLOCKED");
            }
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
        lore.add(ChatColor.WHITE + "Fee: " + ChatColor.YELLOW + pack.cost() + " coins");
        lore.add(ChatColor.WHITE + "Reward: " + ChatColor.GREEN + rewardForPack(pack) + " coins");
        lore.add(ChatColor.WHITE + "Profit: " + ChatColor.GOLD + "+" + (rewardForPack(pack) - pack.cost()) + " coins");
        lore.add(ChatColor.AQUA + "Objectives:");
        for (Objective objective : pack.objectives()) lore.add(ChatColor.AQUA + "• " + objective.description());
        catalogueEntryFor(pack.id()).ifPresent(entry -> {
            for (String key : missingCatalogueUnlocks(player, entry)) {
                String normalized = key.contains(":") ? key : "minecraft:" + key;
                lore.add(ChatColor.RED + "Locked: requires " + normalized);
            }
        });
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
        long singleTimeLimit = template.timed() ? 30L * 60L : 0L;
        data.set(id + ".time-limit-seconds", singleTimeLimit);
        data.set(id + ".remaining-seconds", singleTimeLimit);
        data.set(id + ".last-online-tick", System.currentTimeMillis());
        data.set(id + ".profit-margin", margin);
        if (!secret) data.set("single-shop.offers." + offerSlot, nextOfferForSlot(offerSlot));
        save();
        plugin.tellPublic(player, "Started " + questName + ": " + objective.description() + ". Cost " + cost + "; reward " + reward + " coins. " + (isTimedSingle(template) ? "Time limit: 30 minutes." : "No timer."));
        player.closeInventory();
    }

    private boolean isTimedSingle(RandomQuest quest) {
        return quest.timed();
    }

    private int configuredTierValue(String tier, String setting, int fallback) {
        return Math.max(0, plugin.getConfig().getInt("quests." + setting + "." + tier, fallback));
    }

    private String activePackTier(String activeId) {
        if (activeId == null || !activeId.startsWith("pack:")) return "";
        String packId = activeId.substring("pack:".length());
        if (packId.matches("(?i)M[0-9]{3}")) return "master";
        if (packId.matches("(?i)H[0-9]{3}")) return "hard";
        if (packId.matches("(?i)E[0-9]{3}")) return "easy";
        if (packId.startsWith("master_")) return "master";
        if (packId.startsWith("hard_")) return "hard";
        if (packId.startsWith("easy_")) return "easy";
        return "";
    }

    private boolean tierPurchaseLimitsAllow(Player player, String tier) {
        if (tier.isBlank()) return true;
        UUID id = player.getUniqueId();
        long cooldown = data.getLong(id + ".cooldown-until." + tier, 0L);
        if (System.currentTimeMillis() < cooldown) {
            long minutes = Math.max(1L, (cooldown - System.currentTimeMillis() + 59_999L) / 60_000L);
            plugin.tellPublic(player, "Tier cooldown active. You can buy another " + tier + " pack in " + minutes + " minute(s).");
            return false;
        }
        int cap = configuredTierValue(tier, "daily-profit-cap", tier.equals("easy") ? 2500 : tier.equals("hard") ? 5000 : 12000);
        String day = LocalDate.now().toString();
        int earned = data.getInt("daily." + tier + "." + day + ".profit", 0);
        if (cap > 0 && earned >= cap) {
            plugin.tellPublic(player, "You have reached today's " + tier + " quest profit cap (" + cap + " coins).");
            return false;
        }
        return true;
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
        if (!tierPurchaseLimitsAllow(player, tierOf(pack))) return;
        if (!catalogueEntryFor(pack.id()).map(entry -> catalogueEntryUnlocked(player, entry))
                .orElseGet(() -> tierUnlocked(player, tierOf(pack)) && hasAdvancement(player, pack.advancement()))) {
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
        long timeLimit = timerLimitSeconds(uuid, id);
        data.set(uuid + ".time-limit-seconds", timeLimit);
        data.set(uuid + ".remaining-seconds", timeLimit);
        data.set(uuid + ".last-online-tick", System.currentTimeMillis());
        data.set(uuid + ".started-at", System.currentTimeMillis());
        data.set(uuid + ".warned-5m", false);
        data.set(uuid + ".warned-1m", false);
        save();
        updateHud(player);
    }

    @EventHandler
    public void onQuestActivityMove(PlayerMoveEvent event) {
        if (event.getTo() == null) return;
        var from = event.getFrom();
        var to = event.getTo();
        if (from.getWorld() != to.getWorld()
                || from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()
                || from.getYaw() != to.getYaw() || from.getPitch() != to.getPitch()) {
            data.set("activity." + event.getPlayer().getUniqueId(), System.currentTimeMillis());
        }
    }

    @EventHandler
    public void onQuestActivityInteract(PlayerInteractEvent event) {
        data.set("activity." + event.getPlayer().getUniqueId(), System.currentTimeMillis());
    }

    private boolean isQuestActivityRecent(Player player) {
        int seconds = Math.max(1, plugin.getConfig().getInt("quests.activity-seconds", 90));
        long last = data.getLong("activity." + player.getUniqueId(), 0L);
        return last > 0L && System.currentTimeMillis() - last <= seconds * 1000L;
    }

    private String placedBlockPath(org.bukkit.Location location) {
        return "placed-blocks." + location.getWorld().getUID() + "."
                + location.getBlockX() + "." + location.getBlockY() + "." + location.getBlockZ();
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        if (event.isCancelled()) return;
        data.set(placedBlockPath(event.getBlock().getLocation()), true);
        save();
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        if (event.isCancelled()) return;
        Player p = event.getPlayer();
        if (p.getGameMode() == org.bukkit.GameMode.CREATIVE || p.getGameMode() == org.bukkit.GameMode.SPECTATOR) return;
        String placedPath = placedBlockPath(event.getBlock().getLocation());
        boolean playerPlaced = data.getBoolean(placedPath, false);
        if (playerPlaced) {
            data.set(placedPath, null);
            save();
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
    public void onBerryHarvest(PlayerInteractEvent event) {
        if (event.isCancelled() || event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND
                || event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK
                || event.getClickedBlock() == null) return;
        Player player = event.getPlayer();
        if (player.getGameMode() == org.bukkit.GameMode.CREATIVE
                || player.getGameMode() == org.bukkit.GameMode.SPECTATOR) return;
        Material type = event.getClickedBlock().getType();
        if (type == Material.SWEET_BERRY_BUSH
                && event.getClickedBlock().getBlockData() instanceof Ageable ageable
                && ageable.getAge() >= 2) {
            incrementTarget(player, "harvest:SWEET_BERRIES", 1);
        } else if ((type == Material.CAVE_VINES || type == Material.CAVE_VINES_PLANT)
                && event.getClickedBlock().getBlockData() instanceof org.bukkit.block.data.type.CaveVines vines
                && vines.hasBerries()) {
            incrementTarget(player, "harvest:GLOW_BERRIES", 1);
        }
    }

    @EventHandler
    public void onMobSpawn(CreatureSpawnEvent event) {
        CreatureSpawnEvent.SpawnReason reason = event.getSpawnReason();
        Set<CreatureSpawnEvent.SpawnReason> allowed = Set.of(
                CreatureSpawnEvent.SpawnReason.NATURAL, CreatureSpawnEvent.SpawnReason.CHUNK_GEN,
                CreatureSpawnEvent.SpawnReason.VILLAGE_INVASION, CreatureSpawnEvent.SpawnReason.PATROL,
                CreatureSpawnEvent.SpawnReason.RAID, CreatureSpawnEvent.SpawnReason.REINFORCEMENTS,
                CreatureSpawnEvent.SpawnReason.JOCKEY, CreatureSpawnEvent.SpawnReason.MOUNT,
                CreatureSpawnEvent.SpawnReason.SPELL, CreatureSpawnEvent.SpawnReason.INFECTION,
                CreatureSpawnEvent.SpawnReason.CURED, CreatureSpawnEvent.SpawnReason.LIGHTNING,
                CreatureSpawnEvent.SpawnReason.SILVERFISH_BLOCK, CreatureSpawnEvent.SpawnReason.TRIAL_SPAWNER,
                CreatureSpawnEvent.SpawnReason.SPAWNER);
        if (!allowed.contains(reason)) return;
        var pdc = event.getEntity().getPersistentDataContainer();
        pdc.set(mobSpawnReasonKey, PersistentDataType.STRING, reason.name());
        if (reason == CreatureSpawnEvent.SpawnReason.SPAWNER
                || reason == CreatureSpawnEvent.SpawnReason.TRIAL_SPAWNER) {
            org.bukkit.Location source = event.getLocation();
            String key = source.getWorld().getUID() + ":" + source.getBlockX() + ":"
                    + source.getBlockY() + ":" + source.getBlockZ();
            pdc.set(mobSpawnerKey, PersistentDataType.STRING, key);
        }
    }

    @EventHandler
    public void onDirectMeleeDamage(EntityDamageByEntityEvent event) {
        if (event.isCancelled()) return;
        var pdc = event.getEntity().getPersistentDataContainer();
        if (event.getDamager() instanceof Player attacker) {
            pdc.set(directMeleeKillerKey, PersistentDataType.STRING, attacker.getUniqueId().toString());
        } else {
            // Projectile, trident, pet, or other indirect damage invalidates melee credit.
            pdc.remove(directMeleeKillerKey);
        }
    }

    @EventHandler
    public void onKill(EntityDeathEvent event) {
        Player p = event.getEntity().getKiller();
        if (p == null || p.getGameMode() == org.bukkit.GameMode.CREATIVE
                || p.getGameMode() == org.bukkit.GameMode.SPECTATOR) return;
        String spawnReason = event.getEntity().getPersistentDataContainer()
                .get(mobSpawnReasonKey, PersistentDataType.STRING);
        if (spawnReason == null) return; // Unclassified, command, egg, conversion and custom spawns never count.
        String meleeKiller = event.getEntity().getPersistentDataContainer()
                .get(directMeleeKillerKey, PersistentDataType.STRING);
        if (meleeKiller == null || !meleeKiller.equals(p.getUniqueId().toString())) return;
        String spawner = event.getEntity().getPersistentDataContainer()
                .get(mobSpawnerKey, PersistentDataType.STRING);
        if (spawner != null) {
            UUID id = p.getUniqueId();
            String questId = data.getString(id + ".active", "");
            int limit = spawnReason.equals(CreatureSpawnEvent.SpawnReason.TRIAL_SPAWNER.name()) ? 20 : 10;
            String countPath = id + ".spawner-counts." + questId + "." + spawner;
            int used = data.getInt(countPath, 0);
            if (used >= limit) return;
            data.set(countPath, used + 1);
        }
        incrementTarget(p, "kill:" + event.getEntityType().name(), 1);
        incrementTarget(p, "kill:KILL_" + event.getEntityType().name(), 1);
        if (event.getEntity() instanceof org.bukkit.entity.Monster) recordGlobalProgress(p, "mob", 1);
    }

    @EventHandler
    public void onEnchant(EnchantItemEvent event) {
        Player player = event.getEnchanter();
        if (player.getGameMode() == org.bukkit.GameMode.CREATIVE
                || player.getGameMode() == org.bukkit.GameMode.SPECTATOR
                || event.whichButton() != 2) return;
        incrementTarget(player, "enchant:ANY", 1);
        incrementTarget(player, "enchant:" + event.getItem().getType().name(), 1);
    }

    @EventHandler
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)
                || player.getGameMode() == org.bukkit.GameMode.CREATIVE
                || player.getGameMode() == org.bukkit.GameMode.SPECTATOR) return;
        ItemStack result = event.getCurrentItem();
        if (result != null && result.getType() != Material.AIR) {
            incrementTarget(player, "collect:" + result.getType().name(), result.getAmount());
            Set<Material> questCraftWhitelist = Set.of(Material.BREAD, Material.PUMPKIN_PIE, Material.CAKE,
                    Material.COOKIE, Material.LANTERN, Material.SHIELD, Material.BUCKET, Material.BOOKSHELF,
                    Material.IRON_HELMET, Material.IRON_CHESTPLATE, Material.IRON_LEGGINGS, Material.IRON_BOOTS);
            if (questCraftWhitelist.contains(result.getType())) {
                incrementTarget(player, "craft:" + result.getType().name(), result.getAmount());
            }
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
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;
        if (p.getGameMode() == org.bukkit.GameMode.CREATIVE || p.getGameMode() == org.bukkit.GameMode.SPECTATOR
                || data.getString(id + ".active", "").isBlank() || !matchesTarget(id, "travel")) {
            travelRemainders.remove(id);
            return;
        }
        boolean elytra = p.isGliding();
        if (p.isInsideVehicle() || p.isFlying() || (!elytra && (p.isGliding() || p.getFallDistance() > 3.0f))) {
            travelRemainders.remove(id);
            return;
        }
        double dx = event.getTo().getX() - event.getFrom().getX();
        double dz = event.getTo().getZ() - event.getFrom().getZ();
        double step = Math.sqrt(dx * dx + dz * dz);
        if (step > 10.0) {
            travelRemainders.remove(id);
            return;
        }
        if (elytra && !matchesTarget(id, "travel:ELYTRA") && !matchesTarget(id, "travel:ANY")) return;
        if (!elytra && matchesTarget(id, "travel:ELYTRA") && !matchesTarget(id, "travel:ANY")) return;
        // PlayerMoveEvent is emitted synchronously; cap pathological movement packets to 12 blocks per second.
        long now = System.currentTimeMillis();
        String ratePath = id + ".travel-rate";
        long last = data.getLong(ratePath + ".time", now);
        double allowance = Math.max(0.0, 12.0 * Math.max(0L, now - last) / 1000.0);
        double accepted = Math.min(step, allowance);
        data.set(ratePath + ".time", now);
        if (accepted <= 0.0) return;
        double accumulated = travelRemainders.getOrDefault(id, 0.0) + accepted;
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
    public void onTame(EntityTameEvent event) {
        if (!(event.getOwner() instanceof Player player)
                || player.getGameMode() == org.bukkit.GameMode.CREATIVE
                || player.getGameMode() == org.bukkit.GameMode.SPECTATOR) return;
        incrementTarget(player, "tame:" + event.getEntityType().name(), 1);
        incrementTarget(player, "tame:ANY", 1);
    }

    @EventHandler
    public void onBreed(EntityBreedEvent event) {
        if (!(event.getBreeder() instanceof Player player)
                || player.getGameMode() == org.bukkit.GameMode.CREATIVE
                || player.getGameMode() == org.bukkit.GameMode.SPECTATOR) return;
        if (!(event.getMother() instanceof Animals mother) || !(event.getFather() instanceof Animals father)
                || mother.getAge() != 0 || father.getAge() != 0) return;
        incrementTarget(player, "breed:" + event.getEntityType().name(), 1);
        incrementTarget(player, "breed:ANY", 1);
    }

    @EventHandler
    public void onFeed(PlayerInteractEntityEvent event) {
        if (event.isCancelled() || event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND
                || !(event.getRightClicked() instanceof Animals animal) || animal.getAge() != 0) return;
        Player player = event.getPlayer();
        if (player.getGameMode() == org.bukkit.GameMode.CREATIVE
                || player.getGameMode() == org.bukkit.GameMode.SPECTATOR) return;
        Material food = player.getInventory().getItemInMainHand().getType();
        boolean validFeed = switch (animal.getType()) {
            case COW, SHEEP, GOAT -> food == Material.WHEAT;
            case PIG -> food == Material.CARROT || food == Material.POTATO || food == Material.BEETROOT;
            case CHICKEN -> food == Material.WHEAT_SEEDS || food == Material.BEETROOT_SEEDS
                    || food == Material.MELON_SEEDS || food == Material.PUMPKIN_SEEDS
                    || food == Material.TORCHFLOWER_SEEDS;
            case RABBIT -> food == Material.CARROT || food == Material.GOLDEN_CARROT || food == Material.DANDELION;
            default -> false;
        };
        if (!validFeed) return;
        long now = System.currentTimeMillis();
        var pdc = animal.getPersistentDataContainer();
        long last = pdc.getOrDefault(feedCooldownKey, PersistentDataType.LONG, 0L);
        if (now - last < 60_000L) return;
        pdc.set(feedCooldownKey, PersistentDataType.LONG, now);
        incrementTarget(player, "feed:" + animal.getType().name(), 1);
        incrementTarget(player, "feed:ANY", 1);
    }

    @EventHandler
    public void onQuestMigrationJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        if (!data.getString(id + ".active", "").isBlank() && data.getLong(id + ".time-limit-seconds", 0L) > 0L) {
            data.set(id + ".last-online-tick", System.currentTimeMillis());
        }
        String path = "v2-migration-notice." + id;
        if (data.getBoolean(path, false)) {
            data.set(path, null);
            save();
            plugin.tellPublic(player, "Quest system updated - your active quest was cancelled and fully refunded.");
        }
    }

    @EventHandler
    public void onQuestMigrationQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        if (!data.getString(id + ".active", "").isBlank() && data.getLong(id + ".time-limit-seconds", 0L) > 0L) {
            data.set(id + ".last-online-tick", System.currentTimeMillis());
            save();
        }
    }

    private boolean objectiveTargetMatches(String stored, String eventTarget, World.Environment environment) {
        if (stored.equals(eventTarget)) return true;
        int split = stored.indexOf(':');
        int eventSplit = eventTarget.indexOf(':');
        if (split <= 0 || eventSplit <= 0) return false;
        String objectiveType = stored.substring(0, split);
        String targetExpression = stored.substring(split + 1);
        if (targetExpression.startsWith("@")) {
            int dimensionSplit = targetExpression.indexOf(':');
            if (dimensionSplit < 0) return false;
            String dimension = targetExpression.substring(1, dimensionSplit);
            String expected = switch (dimension) {
                case "OVERWORLD" -> "NORMAL";
                case "NETHER" -> "NETHER";
                case "END" -> "THE_END";
                default -> "";
            };
            if (!environment.name().equals(expected)) return false;
            targetExpression = targetExpression.substring(dimensionSplit + 1);
        }
        if (!objectiveType.equals(eventTarget.substring(0, eventSplit))) return false;
        String[] alternatives = targetExpression.split("\\|");
        String actual = eventTarget.substring(eventSplit + 1);
        for (String candidate : alternatives) {
            if (candidate.equals(actual)) return true;
            if (candidate.startsWith("#") && objectiveTagMatches(candidate, actual)) return true;
        }
        return false;
    }

    private boolean objectiveTagMatches(String tag, String actual) {
        if (actual.startsWith("KILL_")) {
            String entity = actual.substring("KILL_".length());
            return switch (tag) {
                case "#HOSTILES" -> {
                    EntityType type;
                    try { type = EntityType.valueOf(entity); } catch (IllegalArgumentException ex) { yield false; }
                    yield type != EntityType.ENDER_DRAGON && type != EntityType.WITHER
                            && type != EntityType.ELDER_GUARDIAN && type != EntityType.WARDEN
                            && type != EntityType.BREEZE && type != EntityType.BOGGED
                            && switch (type) {
                                case ZOMBIE, HUSK, DROWNED, SKELETON, STRAY, CREEPER, SPIDER, CAVE_SPIDER,
                                     ENDERMAN, WITCH, SLIME, PHANTOM, BLAZE, GHAST, MAGMA_CUBE,
                                     PILLAGER, VINDICATOR, EVOKER, RAVAGER, VEX, SILVERFISH,
                                     ZOMBIFIED_PIGLIN, HOGLIN, ZOGLIN, PIGLIN, PIGLIN_BRUTE -> true;
                                default -> false;
                            };
                }
                case "#RAIDERS" -> Set.of("PILLAGER", "VINDICATOR", "EVOKER", "RAVAGER", "WITCH", "VEX").contains(entity);
                case "#TRIAL_SPAWN_MOBS" -> Set.of("ZOMBIE", "SKELETON", "STRAY", "SPIDER", "CAVE_SPIDER",
                        "SLIME", "SILVERFISH", "BREEZE", "BOGGED").contains(entity);
                default -> false;
            };
        }
        Material material = Material.matchMaterial(actual);
        if (material == null) return false;
        return switch (tag) {
            case "#LOGS" -> actual.endsWith("_LOG") || actual.endsWith("_STEM");
            case "#OVERWORLD_ORES" -> Set.of("COAL_ORE", "COPPER_ORE", "IRON_ORE", "GOLD_ORE",
                    "REDSTONE_ORE", "LAPIS_ORE", "DIAMOND_ORE", "EMERALD_ORE",
                    "DEEPSLATE_COAL_ORE", "DEEPSLATE_COPPER_ORE", "DEEPSLATE_IRON_ORE",
                    "DEEPSLATE_GOLD_ORE", "DEEPSLATE_REDSTONE_ORE", "DEEPSLATE_LAPIS_ORE",
                    "DEEPSLATE_DIAMOND_ORE", "DEEPSLATE_EMERALD_ORE").contains(actual);
            case "#STONE_TYPES" -> Set.of("STONE", "DEEPSLATE", "ANDESITE", "DIORITE", "GRANITE", "TUFF").contains(actual);
            case "#FISH_JUNK" -> Set.of("LILY_PAD", "BOWL", "LEATHER", "LEATHER_BOOTS", "ROTTEN_FLESH",
                    "STICK", "STRING", "POTION", "BONE", "INK_SAC", "TRIPWIRE_HOOK").contains(actual);
            case "#FISH_TREASURE" -> Set.of("BOW", "ENCHANTED_BOOK", "FISHING_ROD", "NAME_TAG",
                    "NAUTILUS_SHELL", "SADDLE").contains(actual);
            case "#COOKED_FOOD" -> Set.of("COOKED_BEEF", "COOKED_PORKCHOP", "COOKED_CHICKEN",
                    "COOKED_MUTTON", "COOKED_RABBIT", "COOKED_COD", "COOKED_SALMON",
                    "BAKED_POTATO", "DRIED_KELP").contains(actual);
            default -> false;
        };
    }

    private void incrementTarget(Player player, String target, int amount) {
        if (amount <= 0 || !isQuestActivityRecent(player)) return;
        UUID id = player.getUniqueId();
        if (data.getString(id + ".active", "").isBlank()) return;
        int count = data.getInt(id + ".objective-count", 0);
        boolean changed = false;
        for (int i = 0; i < count; i++) {
            String base = id + ".objectives." + i;
            if (!objectiveTargetMatches(data.getString(base + ".target", ""), target, player.getWorld().getEnvironment())) continue;
            int goal = data.getInt(base + ".goal", 1);
            int progress = Math.min(goal, data.getInt(base + ".progress", 0) + amount);
            data.set(base + ".progress", progress);
            changed = true;
        }
        if (!changed) return;
        if (allComplete(id)) {
            int reward = data.getInt(id + ".reward", 0);
            String activeQuestId = data.getString(id + ".active", "");
            String completedTier = activePackTier(activeQuestId);
            if (!completedTier.isBlank()) {
                int cost = Math.max(0, data.getInt(id + ".cost", 0));
                int profit = Math.max(0, reward - cost);
                String day = LocalDate.now().toString();
                String dailyPath = "daily." + completedTier + "." + day + ".profit";
                data.set(dailyPath, data.getInt(dailyPath, 0) + profit);
                int cooldownMinutes = configuredTierValue(completedTier, "cooldown-minutes",
                        completedTier.equals("easy") ? 5 : completedTier.equals("hard") ? 10 : 15);
                data.set(id + ".cooldown-until." + completedTier,
                        System.currentTimeMillis() + cooldownMinutes * 60_000L);
            }
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
        for (QuestCatalogue.Entry entry : v2Catalogue.entries()) {
            if (entry.id().equalsIgnoreCase(packId)) return entry.timerMinutes();
        }
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
        long limit = data.getLong(id + ".time-limit-seconds", timerLimitSeconds(id, active));
        if (limit <= 0) return;
        long now = System.currentTimeMillis();
        long lastTick = data.getLong(id + ".last-online-tick", now);
        long elapsed = Math.max(0L, Math.min(30L, (now - lastTick) / 1000L));
        long remaining = data.getLong(id + ".remaining-seconds", limit);
        if (elapsed > 0L) {
            remaining = Math.max(0L, remaining - elapsed);
            data.set(id + ".remaining-seconds", remaining);
            data.set(id + ".last-online-tick", now);
        }
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
        long limit = data.getLong(id + ".time-limit-seconds", timerLimitSeconds(id, active));
        if (limit > 0) {
            long remaining = Math.max(0L, data.getLong(id + ".remaining-seconds", limit));
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
        long now = System.currentTimeMillis();
        String today = LocalDate.now().toString();
        if (!today.equals(data.getString("event.completion-day", ""))) {
            data.set("event.completion-day", today);
            data.set("event.completions-today", 0);
        }
        data.set("event.completions-today", data.getInt("event.completions-today", 0) + 1);
        data.set("event.completed", true);
        data.set("event.cooldown-until", now + configuredGlobalCooldownMinutes() * 60_000L);
        int goal = Math.max(1, data.getInt("event.goal", configuredEventGoal()));
        int baseReward = data.getInt("event.reward", configuredEventReward());
        int minContribution = Math.max(10, (int) Math.ceil(goal * configuredGlobalMinContributionPercent() / 100.0));
        int fullContribution = Math.max(minContribution, (int) Math.ceil(goal * configuredGlobalFullRewardPercent() / 100.0));
        org.bukkit.configuration.ConfigurationSection contributors = data.getConfigurationSection("event.contribution");
        int paid = 0;
        int skipped = 0;
        if (contributors != null) {
            for (String rawId : contributors.getKeys(false)) {
                try {
                    UUID id = UUID.fromString(rawId);
                    int contribution = data.getInt("event.contribution." + rawId, 0);
                    if (contribution < minContribution) { skipped++; continue; }
                    double scale = Math.max(0.5, Math.min(1.0, (double) contribution / fullContribution));
                    int reward = Math.max(1, (int) Math.round(baseReward * scale));
                    plugin.setBalance(id, plugin.getBalance(id) + reward);
                    Player online = Bukkit.getPlayer(id);
                    if (online != null) plugin.tellPublic(online, "§aGlobal quest reward received: " + reward + " coins for contributing to " + data.getString("event.name", "the global quest") + ".");
                    paid++;
                } catch (IllegalArgumentException ignored) { }
            }
        }
        Bukkit.broadcastMessage(ChatColor.GOLD + "[Global Quest] " + ChatColor.GREEN
                + data.getString("event.name", "Global Quest") + " completed! " + paid
                + " qualified contributor(s) paid; " + skipped + " below the contribution threshold. Next event in "
                + configuredGlobalCooldownMinutes() + " minutes.");
        save();
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
        String normalized = rawKey != null && rawKey.contains(":") ? rawKey : "minecraft:" + rawKey;
        NamespacedKey key = NamespacedKey.fromString(normalized);
        if (key == null) return false;
        Advancement advancement = Bukkit.getAdvancement(key);
        if (advancement == null) return false;
        return player.getAdvancementProgress(advancement).isDone();
    }

    private java.util.Optional<QuestCatalogue.Entry> catalogueEntryFor(String id) {
        return v2Catalogue.entries().stream().filter(entry -> entry.id().equalsIgnoreCase(id)).findFirst();
    }

    private boolean catalogueEntryUnlocked(Player player, QuestCatalogue.Entry entry) {
        if (!tierUnlocked(player, entry.tier()) || !hasAdvancement(player, entry.tierGate())) return false;
        for (String key : entry.unlock()) if (!hasAdvancement(player, key)) return false;
        return true;
    }

    private List<String> missingCatalogueUnlocks(Player player, QuestCatalogue.Entry entry) {
        List<String> missing = new ArrayList<>();
        if (!tierUnlocked(player, entry.tier())) missing.add(entry.tierGate());
        for (String key : entry.unlock()) {
            if (!hasAdvancement(player, key)) {
                String normalized = key.contains(":") ? key : "minecraft:" + key;
                if (!missing.contains(normalized)) missing.add(normalized);
            }
        }
        return List.copyOf(missing);
    }

    private void resetEventIfNeeded() {
        long now = System.currentTimeMillis();
        String today = LocalDate.now().toString();
        String eventName = data.getString("event.name", "");
        if (data.getBoolean("event.completed", false)) {
            if (data.getString("event.completion-day", "").equals(today)
                    && data.getInt("event.completions-today", 0) >= configuredGlobalMaxCompletions()) return;
            if (now < data.getLong("event.cooldown-until", 0L)) return;
            startNewGlobalQuest(now, true);
            return;
        }
        long endAt = data.getLong("event.ends-at", 0L);
        if (eventName.isBlank() || endAt <= now) {
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
        data.set("event.cooldown-until", 0L);
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

    private int configuredGlobalMinContributionPercent() { return Math.max(0, Math.min(100, plugin.getConfig().getInt("quests.global-event.min-contribution-percent", 3))); }
    private int configuredGlobalFullRewardPercent() { return Math.max(1, Math.min(100, plugin.getConfig().getInt("quests.global-event.full-reward-percent", 10))); }
    private int configuredGlobalMaxCompletions() { return Math.max(1, plugin.getConfig().getInt("quests.global-event.max-completions-per-day", 3)); }
    private int configuredGlobalCooldownMinutes() { return Math.max(0, plugin.getConfig().getInt("quests.global-event.cooldown-minutes", 30)); }
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
