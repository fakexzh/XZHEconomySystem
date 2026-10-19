package com.xzh.economy;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.advancement.Advancement;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
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
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;

/** Random single quests, achievement-unlocked multi-objective packs, and shared global quests. */
public final class QuestManager implements Listener {
    private record Objective(String target, int goal, String description) {}
    private record Pack(String id, String name, Material icon, int cost, int reward, String advancement, List<Objective> objectives) {}
    private record RandomQuest(String name, Material icon, int cost, int reward, Objective objective) {}

    private final XZHEconomySystem plugin;
    private final File file;
    private final YamlConfiguration data;
    private final NamespacedKey actionKey;
    private final NamespacedKey valueKey;
    private final Random random = new Random();
    private final List<Double> travelRemainders = new ArrayList<>();

    private final List<Pack> packs = List.of(
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
        new Pack("farm_starter", "Farm Starter", Material.WHEAT, 70, 98, "minecraft:husbandry/plant_seed", List.of(new Objective("collect:WHEAT", 64, "Harvest or collect 64 Wheat"))),
        new Pack("root_vegetables", "Root Vegetable Run", Material.CARROT, 100, 140, "minecraft:husbandry/plant_seed", List.of(new Objective("collect:CARROT", 128, "Collect 128 Carrots"))),
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
        new Pack("potato_farmer", "Potato Farmer", Material.POTATO, 609, 853, "minecraft:husbandry/plant_seed", List.of(new Objective("collect:POTATO", 128, "Collect 128 Potatoes"))),
        new Pack("beetroot_farmer", "Beetroot Farmer", Material.BEETROOT, 105, 147, "minecraft:husbandry/plant_seed", List.of(new Objective("collect:BEETROOT", 128, "Collect 128 Beetroots"))),
        new Pack("melon_delivery", "Melon Delivery", Material.MELON_SLICE, 152, 228, "minecraft:husbandry/plant_seed", List.of(new Objective("collect:MELON_SLICE", 192, "Collect 192 Melon Slices"))),
        new Pack("pumpkin_delivery", "Pumpkin Delivery", Material.PUMPKIN, 199, 279, "minecraft:husbandry/plant_seed", List.of(new Objective("collect:PUMPKIN", 64, "Collect 64 Pumpkins"))),
    );

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
        new RandomQuest("Crop Delivery", Material.CARROT, 85, 119, new Objective("collect:CARROT", 96, "Collect 96 Carrots")),
        new RandomQuest("Blaze Rod Shipment", Material.BLAZE_ROD, 190, 266, new Objective("collect:BLAZE_ROD", 32, "Collect 32 Blaze Rods")),
        new RandomQuest("Sand Quarry", Material.SAND, 75, 105, new Objective("break:SAND", 256, "Mine 256 Sand")),
        new RandomQuest("Monster Cleanup", Material.IRON_SWORD, 140, 196, new Objective("kill:SKELETON", 30, "Kill 30 Skeletons")),
        new RandomQuest("Food Pantry", Material.BREAD, 90, 126, new Objective("collect:BREAD", 64, "Collect 64 Bread")),
        new RandomQuest("Quartz Shipment", Material.QUARTZ, 160, 224, new Objective("collect:QUARTZ", 96, "Collect 96 Nether Quartz"))
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
        }, 20L, 20L * 5L);
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
        data.set("event.date", LocalDate.now().toString());
        data.set("event.progress", 0);
        data.set("event.goal", configuredEventGoal());
        data.set("event.reward", configuredEventReward());
        data.set("event.claims-remaining", configuredEventClaims());
        save();
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
        add(inv, 22, Material.BARRIER, ChatColor.RED + "Close", "close", "");
        player.openInventory(inv);
    }

    private void openSingle(Player player) {
        refreshSingleShopIfDue();
        refreshSecretQuestIfDue();
        long next = data.getLong("single-shop.next-refresh", nextRefresh(System.currentTimeMillis()));
        long mins = Math.max(0, (next - System.currentTimeMillis()) / 60000L);
        Inventory inv = Bukkit.createInventory(null, 54, ChatColor.DARK_PURPLE + "XZH Single Quests");
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
            add(inv, 10 + i, offer.icon(), ChatColor.YELLOW + offer.name(), "single-buy", Integer.toString(i), lore);
        }
        add(inv, 22, Material.ENDER_EYE, ChatColor.LIGHT_PURPLE + "??? Secret Quest", "single-secret-buy", "secret",
            List.of(ChatColor.WHITE + "Purchase price: " + ChatColor.YELLOW + data.getInt("single-shop.secret.cost", 250) + " coins",
                    ChatColor.WHITE + "Profit: " + ChatColor.GREEN + "+" + data.getInt("single-shop.secret.margin", 40) + "%",
                    ChatColor.GRAY + "Objective and reward details are hidden.",
                    ChatColor.GRAY + "Refresh in " + Math.max(0, (data.getLong("single-shop.secret.next-refresh", System.currentTimeMillis()) - System.currentTimeMillis()) / 60000L) + " minute(s)"));
        add(inv, 31, Material.CLOCK, ChatColor.AQUA + "Offer refresh", "single-info", "",
            List.of(ChatColor.WHITE + "Five regular offers refresh every 2 hours.",
                    ChatColor.WHITE + "The secret quest refreshes every 30 minutes.",
                    ChatColor.GRAY + "Buying a quest reveals its objective."));
        add(inv, 49, Material.ARROW, ChatColor.WHITE + "Back", "back", "");
        player.openInventory(inv);
    }

    private void openPacks(Player player) { openPacks(player, 0); }

    private void openPacks(Player player, int page) {
        int pageSize = 45;
        int pageCount = (packs.size() + pageSize - 1) / pageSize;
        page = Math.max(0, Math.min(page, pageCount - 1));
        Inventory inv = Bukkit.createInventory(null, 54, ChatColor.DARK_PURPLE + "XZH Achievement Packs (" + (page + 1) + "/" + pageCount + ")");
        int start = page * pageSize;
        int end = Math.min(packs.size(), start + pageSize);
        for (int index = start; index < end; index++) {
            Pack pack = packs.get(index);
            boolean unlocked = hasAdvancement(player, pack.advancement());
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.WHITE + "Cost: " + ChatColor.YELLOW + pack.cost() + " coins");
            lore.add(ChatColor.WHITE + "Reward: " + ChatColor.GREEN + pack.reward() + " coins");
            lore.add(ChatColor.WHITE + "Objectives:");
            for (Objective objective : pack.objectives()) lore.add(ChatColor.AQUA + "• " + objective.description());
            lore.add(unlocked ? ChatColor.GREEN + "UNLOCKED" : ChatColor.RED + "Unlock requirement: " + unlockDescription(pack.id()));
            int timerMinutes = packTimerLimitMinutes(pack.id());
            lore.add(timerMinutes > 0 ? ChatColor.RED + "Time limit: " + timerMinutes + " minutes" : ChatColor.GRAY + "No time limit");
            lore.add(ChatColor.GRAY + "Achievement-unlocked challenge pack");
            add(inv, index - start, unlocked ? pack.icon() : Material.BARRIER,
                (unlocked ? ChatColor.YELLOW : ChatColor.RED) + pack.name(), unlocked ? "pack-select" : "locked", pack.id(), lore);
        }
        if (page > 0) add(inv, 45, Material.ARROW, ChatColor.WHITE + "Previous page", "pack-page", Integer.toString(page - 1));
        add(inv, 49, Material.ARROW, ChatColor.WHITE + "Back", "back", "");
        if (page + 1 < pageCount) add(inv, 53, Material.ARROW, ChatColor.WHITE + "Next page", "pack-page", Integer.toString(page + 1));
        player.openInventory(inv);
    }

    private void confirmPack(Player player, Pack pack) {
        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.DARK_RED + "Confirm quest pack");
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.WHITE + "Cost: " + pack.cost() + " coins");
        lore.add(ChatColor.WHITE + "Reward: " + pack.reward() + " coins");
        for (Objective objective : pack.objectives()) lore.add(ChatColor.AQUA + objective.description());
        int timerMinutes = packTimerLimitMinutes(pack.id());
        lore.add(timerMinutes > 0 ? ChatColor.RED + "Time limit: " + timerMinutes + " minutes" : ChatColor.GRAY + "No time limit");
        add(inv, 13, pack.icon(), ChatColor.YELLOW + pack.name(), "pack-info", pack.id(), lore);
        add(inv, 11, Material.LIME_CONCRETE, ChatColor.GREEN + "Confirm purchase", "pack-buy", pack.id());
        add(inv, 15, Material.RED_CONCRETE, ChatColor.RED + "Cancel", "back-packs", "");
        player.openInventory(inv);
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
        int margin = secret ? data.getInt("single-shop.secret.margin", 40) : 30 + random.nextInt(21);
        int reward = (int) Math.ceil(cost * (1.0 + margin / 100.0));
        if (plugin.getCombinedBalance(id) < cost || !plugin.spendAvailableFunds(id, cost)) { plugin.tellPublic(player, "You need " + cost + " coins to buy this random quest."); return; }
        String questName = template.name() + " #" + (data.getInt("single-shop.quest-sequence", 0) + 1);
        data.set("single-shop.quest-sequence", data.getInt("single-shop.quest-sequence", 0) + 1);
        startQuest(player, "single:" + System.currentTimeMillis(), questName, cost, reward, List.of(objective));
        data.set(id + ".profit-margin", margin);
        if (!secret) data.set("single-shop.offers." + offerSlot, random.nextInt(randomPool.size()));
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
        if (plugin.getLoanManager().isShopPurchaseRestricted(id)) {
            plugin.tellPublic(player, "Your loan is overdue. Quest-pack purchases are locked until full repayment.");
            return;
        }
        if (!hasAdvancement(player, pack.advancement())) {
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
        startQuest(player, "pack:" + pack.id(), pack.name(), pack.cost(), pack.reward(), pack.objectives());
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
        if (material.name().endsWith("_ORE") || material == Material.ANCIENT_DEBRIS) {
            incrementTarget(p, "break:ANY_ORE", 1);
            recordEventOre(p);
        }
    }

    @EventHandler
    public void onKill(EntityDeathEvent event) {
        Player p = event.getEntity().getKiller();
        if (p == null || p.getGameMode() == org.bukkit.GameMode.CREATIVE || p.getGameMode() == org.bukkit.GameMode.SPECTATOR) return;
        incrementTarget(p, "kill:" + event.getEntityType().name(), 1);
    }

    @EventHandler
    public void onPickup(PlayerAttemptPickupItemEvent event) {
        if (event.isCancelled()) return;
        Player p = event.getPlayer();
        if (p.getGameMode() == org.bukkit.GameMode.CREATIVE || p.getGameMode() == org.bukkit.GameMode.SPECTATOR) return;
        Material material = event.getItem().getItemStack().getType();
        incrementTarget(p, "collect:" + material.name(), event.getItem().getItemStack().getAmount());
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (event.isCancelled() || event.getTo() == null || event.getFrom().getWorld() != event.getTo().getWorld()) return;
        Player p = event.getPlayer();
        if (p.getGameMode() == org.bukkit.GameMode.CREATIVE || p.getGameMode() == org.bukkit.GameMode.SPECTATOR) return;
        UUID id = p.getUniqueId();
        if (data.getString(id + ".active", "").isBlank()) return;
        double step = event.getFrom().distance(event.getTo());
        if (step > 10) return;
        if (matchesTarget(id, "travel")) incrementTarget(p, "travel", (int) Math.floor(step));
    }

    @EventHandler
    public void onFish(PlayerFishEvent event) {
        if (event.isCancelled() || event.getState() != PlayerFishEvent.State.CAUGHT_FISH || !(event.getCaught() instanceof org.bukkit.entity.Item caught)) return;
        Player player = event.getPlayer();
        if (player.getGameMode() == org.bukkit.GameMode.CREATIVE || player.getGameMode() == org.bukkit.GameMode.SPECTATOR) return;
        incrementTarget(player, "fish:" + caught.getItemStack().getType().name(), 1);
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

    private int packTimerLimitMinutes(String packId) {
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
        String packId = activeId.substring("pack:".length());
        return switch (packId) {
            case "debris_hunter", "nether_hunter", "nether_blaze", "ocean_fisher", "salmon_run", "deep_sea", "explorer_trail", "monster_patrol", "raid_supply" -> 30L * 60L;
            case "diamond_miner", "pearl_trader", "animal_breeder", "cattle_feed", "sheep_flock", "woodcutter", "builder_supply" -> 60L * 60L;
            default -> 0L;
        };
    }

    private void expireTimedQuestIfNeeded(Player player) {
        UUID id = player.getUniqueId();
        String active = data.getString(id + ".active", "");
        long limit = timerLimitSeconds(id, active);
        if (limit <= 0) return;
        long started = data.getLong(id + ".started-at", System.currentTimeMillis());
        if (System.currentTimeMillis() - started <= limit * 1000L) return;
        data.set(id.toString(), null);
        save();
        plugin.getHudManager().hideActiveQuest(player);
        plugin.tellPublic(player, "Quest timer expired. The quest was forfeited; choose another challenge.");
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
        plugin.getHudManager().showActiveQuest(player, data.getString(id + ".name", "Active quest"),
                display, total <= 0 ? 0 : Math.min(1.0, (double) done / total));
    }

    private void openGlobal(Player player) {
        resetEventIfNeeded();
        UUID id = player.getUniqueId();
        int progress = data.getInt("event.progress", 0);
        int goal = data.getInt("event.goal", configuredEventGoal());
        int contribution = data.getInt("event.contribution." + id, 0);
        boolean claimed = LocalDate.now().toString().equals(data.getString("event.claimed." + id, ""));
        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.DARK_PURPLE + "XZH Global Quest");
        add(inv, 13, Material.NETHER_STAR, ChatColor.GOLD + "Community Ore Rush", "global-info", "global",
            List.of(ChatColor.WHITE + "Shared progress: " + progress + "/" + goal + " ores",
                    ChatColor.WHITE + "Your contribution: " + contribution,
                    ChatColor.WHITE + "Reward: " + data.getInt("event.reward", configuredEventReward()) + " coins",
                    ChatColor.GRAY + "Everyone can contribute; mine ores to help the server."));
        add(inv, 11, Material.LIME_CONCRETE, ChatColor.GREEN + "Claim reward", "global-claim", "global",
            List.of(ChatColor.WHITE + "Requires event completion and 10 personal ores",
                    ChatColor.GRAY + (claimed ? "Already claimed today" : "One reward per player per day")));
        add(inv, 15, Material.ARROW, ChatColor.WHITE + "Back", "back", "");
        player.openInventory(inv);
    }

    private void claimGlobal(Player player) {
        resetEventIfNeeded();
        UUID id = player.getUniqueId();
        String today = LocalDate.now().toString();
        if (today.equals(data.getString("event.claimed." + id, ""))) { plugin.tellPublic(player, "You already claimed today's global quest reward."); return; }
        if (data.getInt("event.progress", 0) < data.getInt("event.goal", configuredEventGoal())) { plugin.tellPublic(player, "The global quest is not complete yet."); return; }
        if (data.getInt("event.contribution." + id, 0) < 10) { plugin.tellPublic(player, "Contribute at least 10 ores before claiming."); return; }
        int remaining = data.getInt("event.claims-remaining", configuredEventClaims());
        if (remaining <= 0) { plugin.tellPublic(player, "All global quest rewards have been claimed."); return; }
        int reward = data.getInt("event.reward", configuredEventReward());
        data.set("event.claimed." + id, today);
        data.set("event.claims-remaining", remaining - 1);
        save();
        plugin.setBalance(id, plugin.getBalance(id) + reward);
        plugin.tellPublic(player, "Global quest reward claimed: " + reward + " coins.");
        openGlobal(player);
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

    private void broadcastGlobalProgress() {
        resetEventIfNeeded();
        if (data.getInt("event.progress", 0) >= data.getInt("event.goal", configuredEventGoal())) return;
        String message = ChatColor.GOLD + "[XZH Global Quest] " + ChatColor.YELLOW
            + "Community Ore Rush: " + data.getInt("event.progress", 0) + "/"
            + data.getInt("event.goal", configuredEventGoal()) + " ores mined. Everyone can contribute!";
        Bukkit.broadcastMessage(message);
    }

    private void refreshSingleShopIfDue() {
        long now = System.currentTimeMillis();
        long next = data.getLong("single-shop.next-refresh", 0L);
        if (next <= 0 || now >= next) {
            data.set("single-shop.next-refresh", nextRefresh(now));
            data.set("single-shop.refresh-id", data.getInt("single-shop.refresh-id", 0) + 1);
            List<Integer> choices = new ArrayList<>();
            while (choices.size() < 5) {
                int candidate = random.nextInt(randomPool.size());
                if (!choices.contains(candidate)) choices.add(candidate);
            }
            for (int i = 0; i < choices.size(); i++) data.set("single-shop.offers." + i, choices.get(i));
            save();
            Bukkit.broadcastMessage(ChatColor.GOLD + "[XZH Quests] Five new random single-quest offers are available!");
        }
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
        String today = LocalDate.now().toString();
        if (!today.equals(data.getString("event.date", ""))) {
            data.set("event", null);
            data.set("event.date", today);
            data.set("event.progress", 0);
            data.set("event.goal", configuredEventGoal());
            data.set("event.reward", configuredEventReward());
            data.set("event.claims-remaining", configuredEventClaims());
            save();
        } else {
            if (!data.contains("event.goal")) data.set("event.goal", configuredEventGoal());
            if (!data.contains("event.reward")) data.set("event.reward", configuredEventReward());
            if (!data.contains("event.claims-remaining")) data.set("event.claims-remaining", configuredEventClaims());
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = ChatColor.stripColor(event.getView().getTitle());
        if (title == null || !(title.equals("XZH Quests") || title.equals("XZH Single Quests")
                || title.startsWith("XZH Achievement Packs") || title.equals("Confirm quest pack")
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
            case "section-single" -> openSingle(player);
            case "section-packs" -> openPacks(player);
            case "section-global" -> openGlobal(player);
            case "single-buy" -> { try { buyRandom(player, Integer.parseInt(value)); } catch (NumberFormatException ignored) { } }
            case "single-secret-buy" -> buyRandom(player, 5);
            case "single-info" -> plugin.tellPublic(player, "Five random quests are available; each purchase replaces its offer slot.");
            case "pack-page" -> { try { openPacks(player, Integer.parseInt(value)); } catch (NumberFormatException ignored) { } }
            case "pack-select" -> packs.stream().filter(p -> p.id().equals(value)).findFirst().ifPresent(p -> confirmPack(player, p));
            case "pack-buy" -> packs.stream().filter(p -> p.id().equals(value)).findFirst().ifPresent(p -> buyPack(player, p));
            case "global-claim" -> claimGlobal(player);
            case "back" -> open(player);
            case "back-packs" -> openPacks(player);
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
    private int configuredEventReward() { return Math.max(0, Math.min(1_000_000, plugin.getConfig().getInt("quests.global-event.reward", 100))); }
    private int configuredEventClaims() { return Math.max(0, Math.min(1_000_000, plugin.getConfig().getInt("quests.global-event.claims-per-day", 25))); }

    private void save() {
        try { data.save(file); } catch (IOException e) { plugin.getLogger().severe("Could not save quests.yml: " + e.getMessage()); }
    }
}
