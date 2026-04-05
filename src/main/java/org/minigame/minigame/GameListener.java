package org.minigame.minigame;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.*;
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
    public void onBlockPlace(BlockPlaceEvent e) {
        BridgeBattle plugin = BridgeBattle.getInstance();
        Player p = e.getPlayer();

        if (p.hasPermission("bridge.admin") && p.getGameMode() == GameMode.CREATIVE) return;

        // Fix: Allow all wool types (Red/Blue/White)
        if (!e.getBlock().getType().name().contains("WOOL")) {
            e.setCancelled(true);
            p.sendMessage("§cYou can only place wool!");
            return;
        }

        if (plugin.getGameState() == BridgeBattle.GameState.ACTIVE) {
            plugin.getPlayerPlacedBlocks().add(e.getBlock().getLocation());
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        Material mat = e.getItemDrop().getItemStack().getType();
        // Prevent dropping weapons
        if (mat == Material.BOW || mat == Material.IRON_SWORD || mat == Material.FISHING_ROD) {
            e.setCancelled(true);
            e.getPlayer().sendMessage("§cYou cannot drop your gear!");
        }
    }

    @EventHandler
    public void onSpecialWeapon(PlayerInteractEvent e) {
        if (!e.getAction().name().contains("RIGHT_CLICK")) return;
        ItemStack item = e.getItem();
        if (item == null) return;

        // Fireball Logic
        if (item.getType() == Material.FIRE_CHARGE) {
            e.setCancelled(true);
            Fireball f = e.getPlayer().launchProjectile(Fireball.class);
            f.setYield(2.5f);
            f.setIsIncendiary(false);
            item.setAmount(item.getAmount() - 1);
        }
    }

    @EventHandler
    public void onTNTFishing(PlayerFishEvent e) {
        // When the hook hits something, spawn TNT
        if (e.getState() == PlayerFishEvent.State.IN_GROUND || e.getState() == PlayerFishEvent.State.CAUGHT_ENTITY) {
            Location loc = e.getHook().getLocation();
            TNTPrimed tnt = loc.getWorld().spawn(loc, TNTPrimed.class);
            tnt.setFuseTicks(30);
            e.getHook().remove();
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        BridgeBattle plugin = BridgeBattle.getInstance();
        Player p = event.getPlayer();

        // Admins in Creative bypass all checks
        if (p.hasPermission("bridge.admin") && p.getGameMode() == GameMode.CREATIVE) return;

        if (plugin.getGameState() != BridgeBattle.GameState.ACTIVE) {
            event.setCancelled(true);
            return;
        }

        Material type = event.getBlock().getType();
        if (type.name().contains("WOOL")) {
            BridgeBattle.Team playerTeam = plugin.getPlayerTeam(p);

            // Logic: You can ONLY break wool that matches your team color
            boolean isOwnTeamWool = (playerTeam == BridgeBattle.Team.RED && type == Material.RED_WOOL) ||
                    (playerTeam == BridgeBattle.Team.BLUE && type == Material.BLUE_WOOL);

            if (!isOwnTeamWool) {
                event.setCancelled(true);
                p.sendMessage("§cYou can only break your own team's wool by hand!");
                return;
            }

            // Remove from tracking if successful
            plugin.getPlayerPlacedBlocks().remove(event.getBlock().getLocation());
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
    public void onPlayerMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        BridgeBattle plugin = BridgeBattle.getInstance();

        // Check if they passed the configurable void limit
        if (p.getLocation().getY() <= plugin.getVoidLevel()) {

            if (plugin.getGameState() == BridgeBattle.GameState.ACTIVE) {
                BridgeBattle.Team team = plugin.getPlayerTeam(p);
                if (team != BridgeBattle.Team.NONE) {
                    // Cancel momentum and teleport
                    p.setFallDistance(0);
                    p.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
                    p.teleport(plugin.getSpawn(team == BridgeBattle.Team.RED ? "red" : "blue"));

                    // Reset stats and inventory
                    p.setHealth(20.0);
                    p.getInventory().clear();
                    plugin.giveGameItems(p);
                    p.sendMessage("§cYou fell into the void!");
                }
            } else {
                // If they fall off the lobby while waiting
                Location lobby = plugin.getSpawn("lobby");
                if (lobby != null) {
                    p.setFallDistance(0);
                    p.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
                    p.teleport(lobby);
                }
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
        if (!(e.getEntity() instanceof Arrow arrow)) return;
        if (!(arrow.getShooter() instanceof Player shooter)) return;

        Block hitBlock = e.getHitBlock();
        if (hitBlock == null || !hitBlock.getType().name().contains("WOOL")) return;

        BridgeBattle plugin = BridgeBattle.getInstance();
        BridgeBattle.Team shooterTeam = plugin.getPlayerTeam(shooter);
        Material blockType = hitBlock.getType();

        // PROTECTION: Arrows ONLY break the OPPOSITE team's wool
        boolean isEnemyWool = (shooterTeam == BridgeBattle.Team.RED && blockType == Material.BLUE_WOOL) ||
                (shooterTeam == BridgeBattle.Team.BLUE && blockType == Material.RED_WOOL);

        if (!isEnemyWool) {
            arrow.remove();
            return; // Arrow does nothing to your own team's blocks
        }

        // BREAK LOGIC (3x3 for Level 2+)
        int level = plugin.getBowLevel(shooter);
        if (level == 1) {
            breakBridgeBlock(hitBlock, plugin);
        } else {
            for (int x = -1; x <= 1; x++) {
                for (int y = -1; y <= 1; y++) {
                    for (int z = -1; z <= 1; z++) {
                        Block rel = hitBlock.getRelative(x, y, z);
                        if (rel.getType().name().contains("WOOL")) {
                            breakBridgeBlock(rel, plugin);
                        }
                    }
                }
            }
        }
        arrow.remove();
    }

    @EventHandler
    public void onArrowHitPlayer(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Arrow arrow)) return;
        if (!(e.getEntity() instanceof Player victim)) return;
        if (!(arrow.getShooter() instanceof Player shooter)) return;

        BridgeBattle plugin = BridgeBattle.getInstance();

        // Friendly Fire Check
        if (plugin.getPlayerTeam(victim) == plugin.getPlayerTeam(shooter)) {
            e.setCancelled(true);
            return;
        }

        // Apply Knockback (Punch Effect)
        Vector direction = arrow.getLocation().getDirection().normalize().multiply(1.5);
        direction.setY(0.4); // Give a slight upward lift to help them clear the edge
        victim.setVelocity(direction);

        shooter.sendMessage("§aDirect Hit on " + victim.getName() + "!");
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent e) {
        BridgeBattle plugin = BridgeBattle.getInstance();
        Player p = e.getPlayer();
        BridgeBattle.Team team = plugin.getPlayerTeam(p);

        if (plugin.getGameState() == BridgeBattle.GameState.ACTIVE) {
            Location spawn = (team == BridgeBattle.Team.RED) ? plugin.getSpawn("red") : plugin.getSpawn("blue");
            if (spawn != null) e.setRespawnLocation(spawn);

            // Give base armor back after delay
            Bukkit.getScheduler().runTaskLater(plugin, () -> plugin.giveGameItems(p), 5L);
        } else {
            if (plugin.getSpawn("lobby") != null) e.setRespawnLocation(plugin.getSpawn("lobby"));
        }
    }

    // Helper method to keep code clean
    private void breakBridgeBlock(Block block, BridgeBattle plugin) {
        block.setType(Material.AIR);
        block.getWorld().playEffect(block.getLocation(), Effect.STEP_SOUND, Material.WHITE_WOOL);
        // Optional: If you track breaks, remove from map here too
        plugin.getPlayerPlacedBlocks().remove(block.getLocation());
    }

    @EventHandler
    public void onNPCInteract(PlayerInteractEntityEvent e) {
        if (!(e.getRightClicked() instanceof Villager v)) return;
        if (v.getCustomName() == null || !v.getCustomName().contains("SHOP")) return;

        e.setCancelled(true);
        if (BridgeBattle.getInstance().getGameState() != BridgeBattle.GameState.ACTIVE) {
            e.getPlayer().sendMessage("§cThe shop is closed until the game starts!");
            return;
        }
        new GameShop().openMainMenu(e.getPlayer());
    }

    @EventHandler
    public void onArrowPickup(PlayerPickupArrowEvent e) {
        // Simply cancel the event so arrows on the ground stay there or disappear
        e.setCancelled(true);
    }

    // Alternatively, to keep the ground clean, we can set the metadata on spawn
    @EventHandler
    public void onShoot(EntityShootBowEvent e) {
        if (e.getProjectile() instanceof Arrow arrow) {
            // This prevents the arrow from being picked up by anyone
            arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        }
    }

    @EventHandler
    public void onNPCDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Villager villager) {
            // Check if it's our shop NPC by name
            if (villager.getCustomName() != null && villager.getCustomName().contains("GAME SHOP")) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onNPCHit(EntityDamageByEntityEvent e) {
        if (e.getEntity() instanceof Villager villager) {
            if (villager.getCustomName() != null && villager.getCustomName().contains("GAME SHOP")) {
                e.setCancelled(true);
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