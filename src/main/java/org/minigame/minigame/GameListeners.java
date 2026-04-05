package org.minigame.minigame;

import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.util.Vector;

public class GameListeners implements Listener {

    private BridgeBattle plugin;

    public GameListeners(BridgeBattle plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();

        p.sendTitle(ChatColor.GOLD + "Welcome to", ChatColor.AQUA + "Bridge Battle", 10, 80, 20);
        p.sendMessage(ChatColor.GREEN + "═══════════════════════════════");
        p.sendMessage(ChatColor.GREEN + "  Welcome to Bridge Battle!");
        p.sendMessage(ChatColor.YELLOW + "  Right click the " + ChatColor.GOLD + "Nether Star" + ChatColor.YELLOW + " to join!");
        p.sendMessage(ChatColor.YELLOW + "  Right click a " + ChatColor.RED + "RED bed" + ChatColor.YELLOW + " or " + ChatColor.BLUE + "BLUE bed" + ChatColor.YELLOW + " to choose team!");
        p.sendMessage(ChatColor.GREEN + "═══════════════════════════════");

        if (plugin.getLobbySpawn() != null) {
            p.teleport(plugin.getLobbySpawn());
        }

        p.getInventory().clear();
        p.getInventory().setArmorContents(null);
        p.setGameMode(GameMode.ADVENTURE);
        plugin.giveNetherStar(p);
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player p = event.getPlayer();
        plugin.giveNetherStar(p);

        if (!plugin.isGameRunning() && plugin.getLobbySpawn() != null) {
            event.setRespawnLocation(plugin.getLobbySpawn());
        } else if (plugin.isGameRunning()) {
            String team = plugin.getPlayerTeam(p);
            if (team != null) {
                if (team.equals("red") && plugin.getTeamSpawn(1) != null) {
                    event.setRespawnLocation(plugin.getTeamSpawn(1));
                } else if (team.equals("blue") && plugin.getTeamSpawn(2) != null) {
                    event.setRespawnLocation(plugin.getTeamSpawn(2));
                }
            }
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player p = event.getPlayer();

        // Nether star click = join queue
        if ((event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) &&
                event.getItem() != null && event.getItem().getType() == Material.NETHER_STAR) {
            event.setCancelled(true);
            plugin.joinQueue(p);
        }

        // Bed click = join team
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
            Material blockType = event.getClickedBlock().getType();
            if (blockType == Material.RED_BED) {
                event.setCancelled(true);
                plugin.joinTeam(p, "red");
            } else if (blockType == Material.BLUE_BED) {
                event.setCancelled(true);
                plugin.joinTeam(p, "blue");
            }
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!plugin.isGameRunning()) {
            event.setCancelled(true);
            return;
        }

        Player p = event.getPlayer();
        String team = plugin.getPlayerTeam(p);

        // Only allow placing white wool
        if (event.getBlockPlaced().getType() != Material.WHITE_WOOL) {
            event.setCancelled(true);
            p.sendMessage(ChatColor.RED + "You can only place white wool on the bridge!");
            return;
        }

        // Check if in bridge zone
        if (!plugin.isInBridgeZone(event.getBlockPlaced().getLocation())) {
            event.setCancelled(true);
            p.sendMessage(ChatColor.RED + "You can only place blocks in the bridge zone!");
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (!plugin.isGameRunning()) {
            event.setCancelled(true);
            return;
        }

        // Allow breaking only white wool
        if (event.getBlock().getType() != Material.WHITE_WOOL) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;

        if (!plugin.isGameRunning()) {
            event.setCancelled(true);
            return;
        }

        // Fall damage
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) return;

        if (!plugin.isGameRunning()) {
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
                Vector knockback = damaged.getLocation().getDirection().multiply(1.5);
                knockback.setY(0.5);
                damaged.setVelocity(knockback);
                damaged.sendMessage(ChatColor.RED + "You were knocked back by " + shooter.getName() + "!");
            }
        }

        // Sword knockback
        if (event.getDamager() instanceof Player) {
            Player attacker = (Player) event.getDamager();
            if (attacker.getInventory().getItemInMainHand().getType() == Material.WOODEN_SWORD) {
                event.setCancelled(true);
                Vector knockback = damaged.getLocation().getDirection().multiply(1.2);
                knockback.setY(0.3);
                damaged.setVelocity(knockback);
                damaged.sendMessage(ChatColor.RED + "You were knocked back by " + attacker.getName() + "!");
            }
        }
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        if (event.getEntity() instanceof Snowball) {
            Snowball snowball = (Snowball) event.getEntity();
            snowball.remove();
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player p = event.getPlayer();

        // Check if player fell into void
        if (p.getLocation().getY() <= 0) {
            String team = plugin.getPlayerTeam(p);
            if (plugin.isGameRunning() && team != null) {
                if (team.equals("red") && plugin.getTeamSpawn(1) != null) {
                    p.teleport(plugin.getTeamSpawn(1));
                } else if (team.equals("blue") && plugin.getTeamSpawn(2) != null) {
                    p.teleport(plugin.getTeamSpawn(2));
                }
                p.sendMessage(ChatColor.YELLOW + "You fell into the void! Respawned at your base.");
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
}