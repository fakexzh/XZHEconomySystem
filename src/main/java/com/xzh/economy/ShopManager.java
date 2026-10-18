package com.xzh.economy;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * First functional shop GUI slice: category browsing, quantity selection,
 * and confirmation before buying/selling configured items.
 */
public final class ShopManager implements Listener {
    private final XZHEconomySystem plugin;
    private final NamespacedKey actionKey;
    private final NamespacedKey materialKey;
    private final NamespacedKey quantityKey;

    private record Product(String category, Material material, double buy, double sell) {}

    private final List<Product> products = List.of(
        new Product("ores", Material.COAL, 8, 2),
        new Product("ores", Material.IRON_INGOT, 25, 7.5),
        new Product("ores", Material.GOLD_INGOT, 40, 12),
        new Product("ores", Material.DIAMOND, 250, 75),
        new Product("blocks", Material.OBSIDIAN, 30, 9),
        new Product("food", Material.BREAD, 8, 2.4),
        new Product("food", Material.COOKED_BEEF, 14, 4.2),
        new Product("equipment", Material.IRON_PICKAXE, 120, 36),
        new Product("equipment", Material.DIAMOND_PICKAXE, 650, 195),
        new Product("special", Material.ELYTRA, 5000, 1500)
    );

    public ShopManager(XZHEconomySystem plugin) {
        this.plugin = plugin;
        this.actionKey = new NamespacedKey(plugin, "shop_action");
        this.materialKey = new NamespacedKey(plugin, "shop_material");
        this.quantityKey = new NamespacedKey(plugin, "shop_quantity");
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void openMain(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.DARK_GREEN + "XZH Shop");
        add(inv, 10, Material.IRON_INGOT, "Ores", "category", "ores");
        add(inv, 12, Material.BRICKS, "Blocks", "category", "blocks");
        add(inv, 14, Material.COOKED_BEEF, "Food", "category", "food");
        add(inv, 16, Material.DIAMOND_PICKAXE, "Equipment", "category", "equipment");
        add(inv, 22, Material.ELYTRA, "Special Items", "category", "special");
        add(inv, 26, Material.GOLD_INGOT, "Sell Hand (held item)", "sell-hand", "hand");
        player.openInventory(inv);
    }

    private void openCategory(Player player, String category) {
        List<Product> list = products.stream().filter(p -> p.category().equals(category)).toList();
        Inventory inv = Bukkit.createInventory(null, 54, ChatColor.DARK_GREEN + "Shop: " + category);
        int slot = 0;
        for (Product p : list) {
            if (slot >= 45) break;
            ItemStack item = new ItemStack(p.material());
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(ChatColor.YELLOW + pretty(p.material()));
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.WHITE + "Buy: " + ChatColor.YELLOW + money(p.buy()) + " coins each");
            lore.add(ChatColor.WHITE + "Sell: " + ChatColor.RED + money(p.sell()) + " coins each");
            lore.add(ChatColor.GRAY + "Click to choose quantity");
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "product");
            meta.getPersistentDataContainer().set(materialKey, PersistentDataType.STRING, p.material().name());
            item.setItemMeta(meta);
            inv.setItem(slot++, item);
        }
        add(inv, 49, Material.ARROW, "Back", "back", "main");
        player.openInventory(inv);
    }

    private void openQuantity(Player player, Material material) {
        Product p = product(material);
        if (p == null) return;
        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.DARK_GREEN + "Choose quantity");
        int[] quantities = {1, 10, 32, 64};
        int[] slots = {10, 12, 14, 16};
        for (int i = 0; i < quantities.length; i++) {
            ItemStack item = new ItemStack(material, Math.min(64, quantities[i]));
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(ChatColor.YELLOW + "Buy " + quantities[i]);
            meta.setLore(List.of(ChatColor.WHITE + "Total: " + ChatColor.YELLOW + money(p.buy() * quantities[i]) + " coins"));
            meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "confirm-buy");
            meta.getPersistentDataContainer().set(materialKey, PersistentDataType.STRING, material.name());
            meta.getPersistentDataContainer().set(quantityKey, PersistentDataType.INTEGER, quantities[i]);
            item.setItemMeta(meta);
            inv.setItem(slots[i], item);
        }
        ItemStack sell = new ItemStack(material);
        ItemMeta sm = sell.getItemMeta();
        sm.setDisplayName(ChatColor.RED + "Sell all of this item in inventory");
        sm.setLore(List.of(ChatColor.WHITE + "Value: " + ChatColor.RED + money(p.sell()) + " coins each",
                ChatColor.GRAY + "You will confirm before selling."));
        sm.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "confirm-sell");
        sm.getPersistentDataContainer().set(materialKey, PersistentDataType.STRING, material.name());
        sell.setItemMeta(sm);
        inv.setItem(22, sell);
        add(inv, 26, Material.ARROW, "Back", "category-back", p.category());
        player.openInventory(inv);
    }

    private void openConfirm(Player player, Material material, int quantity, boolean buying) {
        Product p = product(material);
        if (p == null) return;
        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.DARK_RED + "Confirm transaction");
        double total = (buying ? p.buy() : p.sell()) * quantity;
        ItemStack summary = new ItemStack(material, Math.min(64, quantity));
        ItemMeta sm = summary.getItemMeta();
        sm.setDisplayName(ChatColor.WHITE + (buying ? "Buy " : "Sell ") + quantity + " × " + pretty(material));
        sm.setLore(List.of(ChatColor.WHITE + "Total: " + (buying ? ChatColor.YELLOW : ChatColor.RED) + money(total) + " coins"));
        summary.setItemMeta(sm);
        inv.setItem(13, summary);
        add(inv, 11, Material.LIME_CONCRETE, "Confirm", buying ? "buy" : "sell", material.name() + ":" + quantity);
        add(inv, 15, Material.RED_CONCRETE, "Cancel", "category-back", p.category());
        player.openInventory(inv);
    }

    private void add(Inventory inv, int slot, Material material, String title, String action, String value) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.WHITE + title);
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
        meta.getPersistentDataContainer().set(materialKey, PersistentDataType.STRING, value);
        item.setItemMeta(meta);
        inv.setItem(slot, item);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = ChatColor.stripColor(event.getView().getTitle());
        if (title == null || !title.startsWith("XZH Shop") && !title.startsWith("Shop: ")
                && !title.equals("Choose quantity") && !title.equals("Confirm transaction")) return;
        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        ItemMeta meta = clicked.getItemMeta();
        String action = meta.getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
        String value = meta.getPersistentDataContainer().get(materialKey, PersistentDataType.STRING);
        if (action == null || value == null) return;
        switch (action) {
            case "category" -> openCategory(player, value);
            case "product" -> {
                Material material = parseMaterial(value);
                if (material != null) openQuantity(player, material);
            }
            case "back" -> openMain(player);
            case "category-back" -> {
                if (value.equals("main")) openMain(player);
                else openCategory(player, value);
            }
            case "confirm-buy" -> {
                Material material = parseMaterial(value);
                Integer quantity = meta.getPersistentDataContainer().get(quantityKey, PersistentDataType.INTEGER);
                if (material != null && quantity != null) openConfirm(player, material, quantity, true);
            }
            case "confirm-sell" -> {
                Material material = parseMaterial(value);
                if (material != null) {
                    int amount = count(player, material);
                    if (amount < 1) plugin.tellPublic(player, "You don't have that item to sell.");
                    else openConfirm(player, material, amount, false);
                }
            }
            case "buy", "sell" -> {
                String[] bits = value.split(":");
                if (bits.length != 2) return;
                Material material = parseMaterial(bits[0]);
                int quantity;
                try { quantity = Integer.parseInt(bits[1]); } catch (NumberFormatException ex) { return; }
                Product p = material == null ? null : product(material);
                if (p == null || quantity < 1) return;
                double total = (action.equals("buy") ? p.buy() : p.sell()) * quantity;
                if (action.equals("buy")) {
                    if (plugin.getBalance(player.getUniqueId()) < total) {
                        plugin.tellPublic(player, "Not enough coins.");
                        player.closeInventory();
                        return;
                    }
                    java.util.Map<Integer, ItemStack> leftovers = player.getInventory().addItem(new ItemStack(material, quantity));
                    int notGiven = leftovers.values().stream().mapToInt(ItemStack::getAmount).sum();
                    int delivered = quantity - notGiven;
                    if (delivered <= 0) {
                        plugin.tellPublic(player, "Your inventory is full.");
                        player.closeInventory();
                        return;
                    }
                    double charged = p.buy() * delivered;
                    plugin.setBalance(player.getUniqueId(), plugin.getBalance(player.getUniqueId()) - charged);
                    plugin.tellPublic(player, "Bought " + delivered + " × " + pretty(material) + " for " + money(charged) + " coins.");
                } else {
                    if (p.sell() * quantity < 1) {
                        plugin.tellPublic(player, "Items worth less than 1 coin cannot be sold.");
                        player.closeInventory();
                        return;
                    }
                    int sold = remove(player, material, quantity);
                    if (sold < 1) {
                        plugin.tellPublic(player, "No matching items found.");
                        player.closeInventory();
                        return;
                    }
                    double earned = p.sell() * sold;
                    plugin.setBalance(player.getUniqueId(), plugin.getBalance(player.getUniqueId()) + earned);
                    plugin.tellPublic(player, "Sold " + sold + " × " + pretty(material) + " for " + money(earned) + " coins.");
                }
                player.closeInventory();
            }
            case "sell-hand" -> {
                ItemStack hand = player.getInventory().getItemInMainHand();
                Product p = hand.getType() == Material.AIR ? null : product(hand.getType());
                if (p == null || p.sell() * hand.getAmount() < 1) {
                    plugin.tellPublic(player, "That item cannot be sold here.");
                    return;
                }
                openConfirm(player, hand.getType(), hand.getAmount(), false);
            }
        }
    }

    private Product product(Material material) {
        return products.stream().filter(p -> p.material() == material).findFirst().orElse(null);
    }
    private Material parseMaterial(String name) {
        try { return Material.valueOf(name.toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException e) { return null; }
    }
    private int count(Player player, Material material) {
        int total = 0;
        for (ItemStack item : player.getInventory().getStorageContents())
            if (item != null && item.getType() == material) total += item.getAmount();
        return total;
    }
    private int remove(Player player, Material material, int requested) {
        int remaining = requested;
        for (int i = 0; i < player.getInventory().getStorageContents().length && remaining > 0; i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item == null || item.getType() != material) continue;
            int take = Math.min(item.getAmount(), remaining);
            item.setAmount(item.getAmount() - take);
            remaining -= take;
            if (item.getAmount() <= 0) player.getInventory().setItem(i, null);
        }
        return requested - remaining;
    }
    private String pretty(Material material) {
        return material.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }
    private String money(double amount) {
        return String.format(Locale.US, "%.2f", amount).replaceAll("\\.00$", "");
    }
}
