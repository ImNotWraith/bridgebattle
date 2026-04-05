package org.minigame.minigame;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Vector;

import java.util.Arrays;

public class GameListener implements Listener {

    private BridgeBattle getPlugin() {
        return BridgeBattle.getInstance();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        BridgeBattle plugin = getPlugin();

        if (plugin.getSpawn("lobby") != null) {
            p.teleport(plugin.getSpawn("lobby"));
        }

        plugin.giveLobbyItems(p);

        p.sendMessage(ChatColor.GREEN + "Welcome to Bridge Battle!");
        p.sendMessage(ChatColor.YELLOW + "Type " + ChatColor.GOLD + "/bridge join" + ChatColor.YELLOW + " to play!");

        plugin.updateScoreboard(p);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        getPlugin().leaveGame(event.getPlayer());
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player p = event.getPlayer();
        ItemStack item = event.getItem();

        if (item != null && item.getType() == Material.NETHER_STAR) {
            event.setCancelled(true);
            getPlugin().joinGame(p);
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        BridgeBattle plugin = BridgeBattle.getInstance();
        Player p = event.getPlayer();

        // ADMINS BYPASS ALL RULES
        if (p.hasPermission("bridge.admin") && p.getGameMode() == GameMode.CREATIVE) return;

        // 1. Only allow placing during active game
        if (plugin.getGameState() != BridgeBattle.GameState.ACTIVE) {
            event.setCancelled(true);
            p.sendMessage(ChatColor.RED + "Game not active!");
            return;
        }

        // 2. Only allow white wool
        if (event.getBlock().getType() != Material.WHITE_WOOL) {
            event.setCancelled(true);
            p.sendMessage(ChatColor.RED + "You can only place white wool!");
            return;
        }

        // 3. Check if in bridge zone
        if (!plugin.isInBridgeZone(event.getBlock().getLocation())) {
            event.setCancelled(true);
            p.sendMessage(ChatColor.RED + "You can only place blocks in the bridge zone!");
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        BridgeBattle plugin = BridgeBattle.getInstance();
        Player p = event.getPlayer();

        // ADMINS BYPASS ALL RULES
        if (p.hasPermission("bridge.admin") && p.getGameMode() == GameMode.CREATIVE) return;

        if (plugin.getGameState() != BridgeBattle.GameState.ACTIVE) {
            event.setCancelled(true);
            return;
        }

        if (event.getBlock().getType() != Material.WHITE_WOOL) {
            event.setCancelled(true);
            p.sendMessage(ChatColor.RED + "You can only break white wool!");
            return;
        }

        if (!plugin.isInBridgeZone(event.getBlock().getLocation())) {
            event.setCancelled(true);
            p.sendMessage(ChatColor.RED + "You can only break blocks in the bridge zone!");
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;

        BridgeBattle plugin = getPlugin();

        if (plugin.getGameState() != BridgeBattle.GameState.ACTIVE) {
            event.setCancelled(true);
            return;
        }

        // Cancel fall damage and void damage
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL ||
                event.getCause() == EntityDamageEvent.DamageCause.VOID) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) return;

        BridgeBattle plugin = getPlugin();

        if (plugin.getGameState() != BridgeBattle.GameState.ACTIVE) {
            event.setCancelled(true);
            return;
        }

        Player damaged = (Player) event.getEntity();

        // Snowball knockback
        if (event.getDamager() instanceof Snowball) {
            event.setCancelled(true);
            Snowball snowball = (Snowball) event.getDamager();
            if (snowball.getShooter() instanceof Player) {
                Player shooter = (Player) snowball.getShooter();

                // Don't knockback same team
                if (plugin.getPlayerTeam(shooter) == plugin.getPlayerTeam(damaged)) {
                    return;
                }

                Vector knockback = damaged.getLocation().getDirection().multiply(1.5);
                knockback.setY(0.5);
                damaged.setVelocity(knockback);

                damaged.sendMessage(ChatColor.RED + "You were knocked back by " + shooter.getName() + "!");
                damaged.getWorld().playSound(damaged.getLocation(), Sound.ENTITY_PLAYER_HURT, 1.0f, 1.0f);
            }
        }

        // Sword knockback
        if (event.getDamager() instanceof Player) {
            Player attacker = (Player) event.getDamager();

            // Don't knockback same team
            if (plugin.getPlayerTeam(attacker) == plugin.getPlayerTeam(damaged)) {
                event.setCancelled(true);
                return;
            }

            if (attacker.getInventory().getItemInMainHand().getType() == Material.WOODEN_SWORD) {
                event.setCancelled(true);

                Vector knockback = damaged.getLocation().getDirection().multiply(1.2);
                knockback.setY(0.3);
                damaged.setVelocity(knockback);

                damaged.sendMessage(ChatColor.RED + "You were knocked back by " + attacker.getName() + "!");
                damaged.getWorld().playSound(damaged.getLocation(), Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1.0f, 1.0f);

                // Add kill credit
                plugin.addKill(attacker);
            }
        }
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        if (event.getEntity() instanceof Snowball) {
            event.getEntity().remove();
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player p = event.getPlayer();
        BridgeBattle plugin = getPlugin();

        // Void check during active game
        if (plugin.getGameState() == BridgeBattle.GameState.ACTIVE && p.getLocation().getY() <= 0) {
            BridgeBattle.Team team = plugin.getPlayerTeam(p);

            if (team == BridgeBattle.Team.RED && plugin.getSpawn("red") != null) {
                p.teleport(plugin.getSpawn("red"));
                p.sendMessage(ChatColor.YELLOW + "You fell into the void! Respawned at base!");
                p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
            } else if (team == BridgeBattle.Team.BLUE && plugin.getSpawn("blue") != null) {
                p.teleport(plugin.getSpawn("blue"));
                p.sendMessage(ChatColor.YELLOW + "You fell into the void! Respawned at base!");
                p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
            }
        }
    }

    @EventHandler
    public void onChestOpen(PlayerInteractEvent e) {

        BridgeBattle plugin = getPlugin();
        Player p = e.getPlayer();

        if (e.getClickedBlock() == null || e.getClickedBlock().getType() != Material.CHEST) return;

        // ALLOW ADMINS TO OPEN CHESTS AT ANY TIME
        if (p.hasPermission("bridge.admin")) {
            return; // Exit the listener and let the admin open the chest normally
        }

        if (plugin.getGameState() != BridgeBattle.GameState.ACTIVE) {
            p.sendMessage(ChatColor.RED + "The game has not started yet!");
            e.setCancelled(true);
            return;
        }

        Location clickedLoc = e.getClickedBlock().getLocation();

        // Check if this chest is one of the 3 game chests
        for (Location chestLoc : plugin.getChestLocations()) {
            if (chestLoc.equals(clickedLoc)) {
                e.setCancelled(true); // Prevent them from actually seeing inside

                BridgeBattle.Team team = plugin.getPlayerTeam(p);

                if (team == BridgeBattle.Team.NONE) return;

                // Check if team already owns it
                if (plugin.getCapturedChests().get(clickedLoc) == team) {
                    p.sendMessage(ChatColor.RED + "Your team already captured this chest!");
                    return;
                }

                // Capture logic
                plugin.claimChest(clickedLoc, team);
                String teamColor = (team == BridgeBattle.Team.RED) ? "§cRED" : "§9BLUE";
                Bukkit.broadcastMessage(teamColor + " §ehas captured a chest!");
                p.playSound(p.getLocation(), Sound.BLOCK_CHEST_OPEN, 1f, 1f);

                // Visual indicator: Change block above to team color?
                clickedLoc.clone().add(0, 1, 0).getBlock().setType(
                        team == BridgeBattle.Team.RED ? Material.RED_STAINED_GLASS : Material.BLUE_STAINED_GLASS
                );
            }
        }
    }

    @EventHandler
    public void onChestInteract(PlayerInteractEvent e) {
        BridgeBattle plugin = BridgeBattle.getInstance();

        // Only run if the game is ACTIVE
        if (plugin.getGameState() != BridgeBattle.GameState.ACTIVE) return;

        // Check if they clicked a block
        if (e.getClickedBlock() == null || e.getClickedBlock().getType() != Material.CHEST) return;

        Location clickedLoc = e.getClickedBlock().getLocation();
        Player p = e.getPlayer();

        // Check against the 3 saved chests
        for (Location savedLoc : plugin.getChestLocations()) {
            // Compare X, Y, Z directly to avoid decimal errors
            if (savedLoc.getBlockX() == clickedLoc.getBlockX() &&
                    savedLoc.getBlockY() == clickedLoc.getBlockY() &&
                    savedLoc.getBlockZ() == clickedLoc.getBlockZ()) {

                e.setCancelled(true); // Stop them from opening the chest UI
                plugin.handleChestCapture(clickedLoc, p);
                return;
            }
        }
    }

    @EventHandler
    public void onArrowHit(ProjectileHitEvent e) {
        if (!(e.getEntity() instanceof Arrow)) return;
        Arrow arrow = (Arrow) e.getEntity();
        if (!(arrow.getShooter() instanceof Player)) return;

        Player shooter = (Player) arrow.getShooter();
        Block hitBlock = e.getHitBlock();

        if (hitBlock != null && hitBlock.getType() == Material.WHITE_WOOL) {
            // Basic Level 1: Destroy the hit block
            hitBlock.setType(Material.AIR);
            hitBlock.getWorld().playEffect(hitBlock.getLocation(), Effect.STEP_SOUND, Material.WHITE_WOOL);

            // Level 2/3 Upgrade: Check if player has the "Explosive" upgrade
            // We assume you store this in a Map<Player, Integer> bowUpgradeLevel;
            int level = BridgeBattle.getInstance().getBowLevel(shooter);

            if (level >= 2) {
                // Destroy nearby 2 blocks (3x3 area)
                for (int x = -1; x <= 1; x++) {
                    for (int y = -1; y <= 1; y++) {
                        for (int z = -1; z <= 1; z++) {
                            Block b = hitBlock.getRelative(x, y, z);
                            if (b.getType() == Material.WHITE_WOOL) {
                                b.setType(Material.AIR);
                            }
                        }
                    }
                }
            }
        }
        arrow.remove(); // Remove arrow after impact
    }

    @EventHandler
    public void onNPCInteract(PlayerInteractEntityEvent e) {
        if (e.getRightClicked() instanceof Villager) {
            Villager v = (Villager) e.getRightClicked();
            if (v.getCustomName() != null && v.getCustomName().contains("GAME SHOP")) {
                e.setCancelled(true);
                new GameShop().openMainMenu(e.getPlayer());
            }
        }
    }

    @EventHandler
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        event.setCancelled(true);
        if (event.getEntity() instanceof Player) {
            ((Player) event.getEntity()).setFoodLevel(20);
        }
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        BridgeBattle plugin = getPlugin();
        // Allow dropping items during game, prevent in lobby
        if (plugin.getGameState() != BridgeBattle.GameState.ACTIVE) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerPickupItem(PlayerPickupItemEvent event) {
        BridgeBattle plugin = getPlugin();
        if (plugin.getGameState() != BridgeBattle.GameState.ACTIVE) {
            event.setCancelled(true);
        }
    }
}