package org.minigame.minigame;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.Arrays;

public class GameShop implements Listener {

    // Helper method to create items with lore
    private ItemStack createItem(Material mat, String name, String lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(Arrays.asList(lore));
        item.setItemMeta(meta);
        return item;
    }

    public void openMainMenu(Player p) {
        Inventory inv = Bukkit.createInventory(null, 9, "§8Game Shop");
        inv.setItem(2, createItem(Material.WHITE_WOOL, "§eBuilding Blocks", "§7Click to view"));
        inv.setItem(4, createItem(Material.BOW, "§6Combat Items", "§7Click to view"));
        inv.setItem(6, createItem(Material.NETHER_STAR, "§bUpgrades", "§7Click to view"));
        p.openInventory(inv);
    }

    public void openBlocksMenu(Player p) {
        Inventory inv = Bukkit.createInventory(null, 9, "§8Building Blocks");
        // Increased cost from 10 to 32 Gold for 16 wool (Harder to bridge)
        inv.setItem(4, createItem(Material.WHITE_WOOL, "§f16x Wool", "§7Cost: §632 Gold"));
        p.openInventory(inv);
    }

    public void openCombatMenu(Player p) {
        Inventory inv = Bukkit.createInventory(null, 9, "§8Combat Items");

        // Sword: Costs Gold
        inv.setItem(2, createItem(Material.STONE_SWORD, "§fStone Sword", "§7Cost: §648 Gold"));
        inv.setItem(3, createItem(Material.IRON_SWORD, "§fIron Sword", "§7Cost: §b5 Emeralds"));

        // Bow: Costs Emeralds (High value)
        inv.setItem(5, createItem(Material.BOW, "§6Basic Bow", "§7Cost: §b3 Emeralds"));
        inv.setItem(6, createItem(Material.ARROW, "§fArrows (8x)", "§7Cost: §620 Gold"));

        p.openInventory(inv);
    }

    public void openUpgradesMenu(Player p) {
        Inventory inv = Bukkit.createInventory(null, 9, "§8Upgrades");
        inv.setItem(4, createItem(Material.NETHER_STAR, "§bBow Level 2", "§7Cost: §b5 Emeralds"));
        p.openInventory(inv);
    }

    @EventHandler
    public void onShopClick(InventoryClickEvent e) {
        String title = e.getView().getTitle();
        if (!title.contains("Shop") && !title.contains("Blocks") && !title.contains("Combat") && !title.contains("Upgrades")) return;

        e.setCancelled(true);
        Player p = (Player) e.getWhoClicked();
        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        // Navigation
        String name = clicked.getItemMeta().getDisplayName();
        if (name.contains("Building Blocks")) openBlocksMenu(p);
        else if (name.contains("Combat Items")) openCombatMenu(p);
        else if (name.contains("Upgrades")) openUpgradesMenu(p);

        // BUYING LOGIC
        if (name.contains("16x Wool")) {
            Material woolToGive = getFormattedWool(p);
            // Cost: 32 Gold for 16 Wool
            handlePurchase(p, Material.GOLD_INGOT, 48, new ItemStack(woolToGive, 5));
        }
        else if (name.contains("Stone Sword")) {
            handlePurchase(p, Material.GOLD_INGOT, 16, new ItemStack(Material.STONE_SWORD));
        }
        else if (name.contains("Iron Sword")) {
            handlePurchase(p, Material.GOLD_INGOT, 32, new ItemStack(Material.IRON_SWORD));
        }
        else if (name.contains("Basic Bow")) {
            handlePurchase(p, Material.EMERALD, 1, new ItemStack(Material.BOW));
        }
        else if (name.contains("Arrows (8x)")) {
            handlePurchase(p, Material.GOLD_INGOT, 10, new ItemStack(Material.ARROW, 8));
        }
    }

    // Helper for cleaner code
    private void handlePurchase(Player p, Material currency, int price, ItemStack reward) {
        if (p.getInventory().containsAtLeast(new ItemStack(currency), price)) {
            p.getInventory().removeItem(new ItemStack(currency, price));
            p.getInventory().addItem(reward);
            p.sendMessage("§aYou purchased " + reward.getItemMeta().getDisplayName() + "!");
            p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
        } else {
            p.sendMessage("§cYou need " + price + " " + currency.name().replace("_", " ") + "!");
        }
    }

    private Material getFormattedWool(Player p) {
        BridgeBattle plugin = BridgeBattle.getInstance();
        // Check player's team from the main class
        BridgeBattle.Team team = plugin.getPlayerTeam(p);

        if (team == BridgeBattle.Team.RED) {
            return Material.RED_WOOL;
        } else if (team == BridgeBattle.Team.BLUE) {
            return Material.BLUE_WOOL;
        }

        return Material.WHITE_WOOL; // Fallback
    }
}