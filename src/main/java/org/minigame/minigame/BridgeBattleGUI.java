package org.minigame.minigame;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;

public class BridgeBattleGUI implements InventoryHolder, Listener {

    private final BridgeBattle plugin;
    private final Inventory inv;

    public BridgeBattleGUI(BridgeBattle plugin) {
        this.plugin = plugin;
        this.inv = Bukkit.createInventory(this, 9, ChatColor.DARK_GRAY + "Bridge Battle Menu");
        initializeItems();
    }

    private void initializeItems() {
        inv.setItem(2, createItem(Material.RED_WOOL, ChatColor.RED + "Join Red Team"));
        inv.setItem(4, createItem(Material.NETHER_STAR, ChatColor.GOLD + "START GAME"));
        inv.setItem(6, createItem(Material.BLUE_WOOL, ChatColor.BLUE + "Join Blue Team"));

        // Fill empty slots with glass
        ItemStack glass = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 9; i++) {
            if (inv.getItem(i) == null) inv.setItem(i, glass);
        }
    }

    private ItemStack createItem(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        return item;
    }

    @Override
    public Inventory getInventory() { return inv; }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof BridgeBattleGUI)) return;
        e.setCancelled(true);

        Player p = (Player) e.getWhoClicked();
        int slot = e.getRawSlot();

        if (slot == 8) {
            p.sendMessage(ChatColor.LIGHT_PURPLE + "--- Admin Setup Commands ---");
            p.sendMessage("§d/bridge set lobby §f- Set Lobby");
            p.sendMessage("§d/bridge setchest 1 §f- Set First Chest");
            p.sendMessage("§d/bridge setchest 2 §f- Set Second Chest");
            p.sendMessage("§d/bridge setchest 3 §f- Set Third Chest");
            p.closeInventory();
        }

        if (slot == 2) { // Red Team
            plugin.joinGame(p); // Your existing auto-balance logic
            p.closeInventory();
        } else if (slot == 4) { // Start Game
            if (p.hasPermission("bridge.admin")) {
                p.performCommand("bridge start");
            } else {
                p.sendMessage(ChatColor.RED + "Only admins can force start!");
            }
            p.closeInventory();
        } else if (slot == 6) { // Blue Team
            plugin.joinGame(p); // Your existing auto-balance logic
            p.closeInventory();
        }
    }
}