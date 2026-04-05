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
        inv.setItem(4, createItem(Material.WHITE_WOOL, "§f32x Wool", "§7Cost: §610 Gold"));
        p.openInventory(inv);
    }

    public void openCombatMenu(Player p) {
        Inventory inv = Bukkit.createInventory(null, 9, "§8Combat Items");
        inv.setItem(4, createItem(Material.ARROW, "§fExplosive Arrow", "§7Cost: §b2 Emeralds"));
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

        // Example Purchase Logic (Wool)
        if (name.contains("32x Wool")) {
            if (p.getInventory().contains(Material.GOLD_INGOT, 10)) {
                p.getInventory().removeItem(new ItemStack(Material.GOLD_INGOT, 10));
                p.getInventory().addItem(new ItemStack(Material.WHITE_WOOL, 32));
                p.sendMessage("§aPurchase successful!");
            } else {
                p.sendMessage("§cNot enough Gold!");
            }
        }

        // Example Purchase Logic (Upgrade)
        if (name.contains("Bow Level 2")) {
            if (p.getInventory().contains(Material.EMERALD, 5)) {
                p.getInventory().removeItem(new ItemStack(Material.EMERALD, 5));
                BridgeBattle.getInstance().setBowLevel(p, 2);
                p.sendMessage("§bBow Upgraded to Level 2! §7(3x3 Destruction)");
            } else {
                p.sendMessage("§cNot enough Emeralds!");
            }
        }
    }
}