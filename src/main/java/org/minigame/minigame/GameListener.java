package org.minigame.minigame;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
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
        BridgeBattle plugin = getPlugin();

        // Only allow block placing during active game
        if (plugin.getGameState() != BridgeBattle.GameState.ACTIVE) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "Game not active!");
            return;
        }

        // Only allow white wool
        if (event.getBlock().getType() != Material.WHITE_WOOL) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "You can only place white wool!");
            return;
        }

        // Check if in bridge zone (if set)
        if (!plugin.isInBridgeZone(event.getBlock().getLocation())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "You can only place blocks in the bridge zone!");
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        BridgeBattle plugin = getPlugin();

        // Only allow block breaking during active game
        if (plugin.getGameState() != BridgeBattle.GameState.ACTIVE) {
            event.setCancelled(true);
            return;
        }

        // Only allow breaking white wool
        if (event.getBlock().getType() != Material.WHITE_WOOL) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "You can only break white wool!");
            return;
        }

        // Check if in bridge zone (if set)
        if (!plugin.isInBridgeZone(event.getBlock().getLocation())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "You can only break blocks in the bridge zone!");
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