package org.minigame.minigame;

import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class BridgeBattle extends JavaPlugin {

    private static BridgeBattle instance;

    // Game data
    private List<Player> teamRed = new ArrayList<>();
    private List<Player> teamBlue = new ArrayList<>();
    private List<Player> waitingQueue = new ArrayList<>();
    private Map<Player, String> playerTeam = new HashMap<>();
    private boolean gameRunning = false;
    private int redScore = 0;
    private int blueScore = 0;

    // Spawn locations
    private Location lobbySpawn;
    private Location team1Spawn;
    private Location team2Spawn;

    // Bridge zone
    private Location bridgeZoneMin;
    private Location bridgeZoneMax;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        loadSpawns();

        getServer().getPluginManager().registerEvents(new GameListeners(this), this);

        // Register commands
        Objects.requireNonNull(getCommand("setspawnteam")).setExecutor(this);
        Objects.requireNonNull(getCommand("minigamespawn")).setExecutor(this);
        Objects.requireNonNull(getCommand("endgame")).setExecutor(this);
        Objects.requireNonNull(getCommand("join")).setExecutor(this);
        Objects.requireNonNull(getCommand("leave")).setExecutor(this);
        Objects.requireNonNull(getCommand("team")).setExecutor(this);

        getLogger().info("§aBridgeBattle enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("§cBridgeBattle disabled!");
    }

    public static BridgeBattle getInstance() {
        return instance;
    }

    // ==================== SPAWN MANAGEMENT ====================

    private void loadSpawns() {
        FileConfiguration config = getConfig();
        lobbySpawn = loadSpawn("lobby");
        team1Spawn = loadSpawn("team1");
        team2Spawn = loadSpawn("team2");

        // Load bridge zone if exists
        if (config.contains("bridge_zone")) {
            World world = Bukkit.getWorld(config.getString("bridge_zone.world"));
            if (world != null) {
                bridgeZoneMin = new Location(world,
                        config.getDouble("bridge_zone.min.x"),
                        config.getDouble("bridge_zone.min.y"),
                        config.getDouble("bridge_zone.min.z"));
                bridgeZoneMax = new Location(world,
                        config.getDouble("bridge_zone.max.x"),
                        config.getDouble("bridge_zone.max.y"),
                        config.getDouble("bridge_zone.max.z"));
            }
        }
    }

    private Location loadSpawn(String name) {
        FileConfiguration config = getConfig();
        if (!config.contains("spawns." + name)) return null;

        World world = Bukkit.getWorld(config.getString("spawns." + name + ".world"));
        if (world == null) return null;

        return new Location(world,
                config.getDouble("spawns." + name + ".x"),
                config.getDouble("spawns." + name + ".y"),
                config.getDouble("spawns." + name + ".z"),
                (float) config.getDouble("spawns." + name + ".yaw"),
                (float) config.getDouble("spawns." + name + ".pitch"));
    }

    private void saveSpawn(String name, Location loc) {
        FileConfiguration config = getConfig();
        config.set("spawns." + name + ".world", loc.getWorld().getName());
        config.set("spawns." + name + ".x", loc.getX());
        config.set("spawns." + name + ".y", loc.getY());
        config.set("spawns." + name + ".z", loc.getZ());
        config.set("spawns." + name + ".yaw", (double) loc.getYaw());
        config.set("spawns." + name + ".pitch", (double) loc.getPitch());
        saveConfig();
    }

    public void setLobbySpawn(Location location) {
        this.lobbySpawn = location;
        saveSpawn("lobby", location);
    }

    public void setTeamSpawn(int team, Location location) {
        if (team == 1) {
            this.team1Spawn = location;
            saveSpawn("team1", location);
        } else if (team == 2) {
            this.team2Spawn = location;
            saveSpawn("team2", location);
        }
    }

    public Location getLobbySpawn() { return lobbySpawn; }
    public Location getTeamSpawn(int team) { return team == 1 ? team1Spawn : team2Spawn; }
    public boolean isGameRunning() { return gameRunning; }

    // ==================== PLAYER MANAGEMENT ====================

    public void giveNetherStar(Player player) {
        ItemStack netherStar = new ItemStack(Material.NETHER_STAR, 1);
        ItemMeta meta = netherStar.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "Join Game");
        meta.setLore(Arrays.asList(ChatColor.GRAY + "Right click to join the queue!"));
        netherStar.setItemMeta(meta);
        player.getInventory().setItem(0, netherStar);
    }

    public void giveBridgeItems(Player player) {
        // Give bridge blocks
        ItemStack bridgeBlocks = new ItemStack(Material.WHITE_WOOL, 64);
        ItemMeta meta = bridgeBlocks.getItemMeta();
        meta.setDisplayName(ChatColor.AQUA + "Bridge Blocks");
        bridgeBlocks.setItemMeta(meta);
        player.getInventory().setItem(1, bridgeBlocks);

        // Give snowballs for knockback
        ItemStack snowballs = new ItemStack(Material.SNOWBALL, 16);
        ItemMeta snowMeta = snowballs.getItemMeta();
        snowMeta.setDisplayName(ChatColor.WHITE + "Knockback Snowball");
        snowballs.setItemMeta(snowMeta);
        player.getInventory().setItem(2, snowballs);

        // Give knockback sword
        ItemStack sword = new ItemStack(Material.WOODEN_SWORD, 1);
        ItemMeta swordMeta = sword.getItemMeta();
        swordMeta.setDisplayName(ChatColor.GOLD + "Knockback Stick");
        swordMeta.setUnbreakable(true);
        sword.setItemMeta(swordMeta);
        player.getInventory().setItem(0, sword);
    }

    public void joinQueue(Player player) {
        if (gameRunning) {
            player.sendMessage(ChatColor.RED + "Game is already running!");
            return;
        }
        if (waitingQueue.contains(player) || playerTeam.containsKey(player)) {
            player.sendMessage(ChatColor.RED + "You are already in the game!");
            return;
        }
        waitingQueue.add(player);
        player.sendMessage(ChatColor.GREEN + "You joined the queue! Waiting for players...");
        broadcastQueueStatus();
        checkAutoStart();
    }

    public void leaveQueue(Player player) {
        if (waitingQueue.contains(player)) {
            waitingQueue.remove(player);
            player.sendMessage(ChatColor.YELLOW + "You left the queue.");
        } else if (playerTeam.containsKey(player)) {
            leaveTeam(player);
        } else {
            player.sendMessage(ChatColor.RED + "You are not in the queue or on a team!");
        }
        broadcastQueueStatus();
    }

    public void joinTeam(Player player, String team) {
        if (gameRunning) {
            player.sendMessage(ChatColor.RED + "Game already started!");
            return;
        }

        if (team.equalsIgnoreCase("red") && teamRed.size() >= 3) {
            player.sendMessage(ChatColor.RED + "Red team is full! (Max 3 players)");
            return;
        }
        if (team.equalsIgnoreCase("blue") && teamBlue.size() >= 3) {
            player.sendMessage(ChatColor.RED + "Blue team is full! (Max 3 players)");
            return;
        }

        waitingQueue.remove(player);
        if (playerTeam.containsKey(player)) leaveTeam(player);

        if (team.equalsIgnoreCase("red")) {
            teamRed.add(player);
            playerTeam.put(player, "red");
            player.sendMessage(ChatColor.RED + "You joined the Red Team!");
            if (team1Spawn != null) player.teleport(team1Spawn);
        } else if (team.equalsIgnoreCase("blue")) {
            teamBlue.add(player);
            playerTeam.put(player, "blue");
            player.sendMessage(ChatColor.BLUE + "You joined the Blue Team!");
            if (team2Spawn != null) player.teleport(team2Spawn);
        }

        giveTeamArmor(player, team);
        broadcastTeamSizes();
        broadcastQueueStatus();
        checkAutoStart();
    }

    private void giveTeamArmor(Player player, String team) {
        player.getInventory().setHelmet(new ItemStack(team.equals("red") ? Material.RED_WOOL : Material.BLUE_WOOL));
    }

    public void leaveTeam(Player player) {
        if (!playerTeam.containsKey(player)) return;

        String team = playerTeam.get(player);
        if (team.equals("red")) teamRed.remove(player);
        else teamBlue.remove(player);

        playerTeam.remove(player);
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.sendMessage(ChatColor.YELLOW + "You left your team.");

        if (lobbySpawn != null) player.teleport(lobbySpawn);
        giveNetherStar(player);
        broadcastTeamSizes();
    }

    private void broadcastTeamSizes() {
        String message = ChatColor.GOLD + "╔══════════════════════════════╗\n" +
                ChatColor.GOLD + "║     " + ChatColor.RED + "RED: " + teamRed.size() +
                ChatColor.GOLD + "     vs     " + ChatColor.BLUE + "BLUE: " + teamBlue.size() +
                ChatColor.GOLD + "     ║\n" +
                ChatColor.GOLD + "╚══════════════════════════════╝";

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(message);
        }
    }

    private void broadcastQueueStatus() {
        if (!waitingQueue.isEmpty()) {
            String waiting = ChatColor.YELLOW + "Waiting: " + ChatColor.WHITE;
            for (int i = 0; i < waitingQueue.size(); i++) {
                waiting += waitingQueue.get(i).getName();
                if (i < waitingQueue.size() - 1) waiting += ", ";
            }
            Bukkit.broadcastMessage(waiting);
        }
    }

    private void checkAutoStart() {
        if (!gameRunning && teamRed.size() == teamBlue.size() && teamRed.size() >= 1 && teamRed.size() <= 3) {
            startCountdown();
        }
    }

    private void startCountdown() {
        new BukkitRunnable() {
            int countdown = 5;

            @Override
            public void run() {
                if (teamRed.size() != teamBlue.size() || teamRed.size() == 0) {
                    Bukkit.broadcastMessage(ChatColor.RED + "Game start cancelled - teams unbalanced!");
                    this.cancel();
                    return;
                }

                if (countdown <= 0) {
                    startGame();
                    this.cancel();
                } else {
                    Bukkit.broadcastMessage(ChatColor.YELLOW + "Game starting in " + ChatColor.RED + countdown + ChatColor.YELLOW + " seconds!");
                    for (Player p : teamRed) {
                        p.sendTitle(ChatColor.RED + "" + countdown, "Get ready!", 0, 20, 0);
                    }
                    for (Player p : teamBlue) {
                        p.sendTitle(ChatColor.BLUE + "" + countdown, "Get ready!", 0, 20, 0);
                    }
                    countdown--;
                }
            }
        }.runTaskTimer(this, 0L, 20L);
    }

    private void startGame() {
        gameRunning = true;

        // Clear inventories and give game items
        for (Player p : teamRed) {
            p.getInventory().clear();
            p.getInventory().setArmorContents(null);
            giveBridgeItems(p);
            p.sendTitle(ChatColor.RED + "GAME START!", "Build the bridge!", 10, 60, 20);
            p.sendMessage(ChatColor.GREEN + "═══════════════════════════════");
            p.sendMessage(ChatColor.GREEN + "  Build the bridge to the other side!");
            p.sendMessage(ChatColor.GREEN + "  Use snowballs to knock enemies off!");
            p.sendMessage(ChatColor.GREEN + "═══════════════════════════════");
        }

        for (Player p : teamBlue) {
            p.getInventory().clear();
            p.getInventory().setArmorContents(null);
            giveBridgeItems(p);
            p.sendTitle(ChatColor.BLUE + "GAME START!", "Build the bridge!", 10, 60, 20);
            p.sendMessage(ChatColor.GREEN + "═══════════════════════════════");
            p.sendMessage(ChatColor.GREEN + "  Build the bridge to the other side!");
            p.sendMessage(ChatColor.GREEN + "  Use snowballs to knock enemies off!");
            p.sendMessage(ChatColor.GREEN + "═══════════════════════════════");
        }

        Bukkit.broadcastMessage(ChatColor.GREEN + "═══════════════════════════════");
        Bukkit.broadcastMessage(ChatColor.GREEN + "  GAME STARTED! Red vs Blue!");
        Bukkit.broadcastMessage(ChatColor.GREEN + "═══════════════════════════════");
    }

    public void winGame(String team) {
        if (!gameRunning) return;

        gameRunning = false;

        if (team.equals("red")) {
            redScore++;
            Bukkit.broadcastMessage(ChatColor.RED + "═══════════════════════════════");
            Bukkit.broadcastMessage(ChatColor.RED + "  RED TEAM WINS THE ROUND!");
            Bukkit.broadcastMessage(ChatColor.GREEN + "  Score: " + ChatColor.RED + redScore + ChatColor.GREEN + " - " + ChatColor.BLUE + blueScore);
            Bukkit.broadcastMessage(ChatColor.RED + "═══════════════════════════════");
        } else {
            blueScore++;
            Bukkit.broadcastMessage(ChatColor.BLUE + "═══════════════════════════════");
            Bukkit.broadcastMessage(ChatColor.BLUE + "  BLUE TEAM WINS THE ROUND!");
            Bukkit.broadcastMessage(ChatColor.GREEN + "  Score: " + ChatColor.RED + redScore + ChatColor.GREEN + " - " + ChatColor.BLUE + blueScore);
            Bukkit.broadcastMessage(ChatColor.BLUE + "═══════════════════════════════");
        }

        // Check for overall winner
        if (redScore >= 3) {
            Bukkit.broadcastMessage(ChatColor.GOLD + "★★★★★★★★★★★★★★★★★★★★★★★");
            Bukkit.broadcastMessage(ChatColor.GOLD + "  RED TEAM WINS THE MATCH!");
            Bukkit.broadcastMessage(ChatColor.GOLD + "★★★★★★★★★★★★★★★★★★★★★★★");
            redScore = 0;
            blueScore = 0;
        } else if (blueScore >= 3) {
            Bukkit.broadcastMessage(ChatColor.GOLD + "★★★★★★★★★★★★★★★★★★★★★★★");
            Bukkit.broadcastMessage(ChatColor.GOLD + "  BLUE TEAM WINS THE MATCH!");
            Bukkit.broadcastMessage(ChatColor.GOLD + "★★★★★★★★★★★★★★★★★★★★★★★");
            redScore = 0;
            blueScore = 0;
        }

        // Reset after delay
        new BukkitRunnable() {
            @Override
            public void run() {
                resetGame();
            }
        }.runTaskLater(this, 80L); // 4 seconds delay
    }

    private void resetGame() {
        // Clear bridge blocks (you can add bridge clearing logic here)

        // Teleport players back and reset
        for (Player p : teamRed) {
            p.getInventory().clear();
            p.getInventory().setArmorContents(null);
            if (team1Spawn != null) p.teleport(team1Spawn);
            giveNetherStar(p);
            p.sendMessage(ChatColor.YELLOW + "Get ready for the next round!");
        }

        for (Player p : teamBlue) {
            p.getInventory().clear();
            p.getInventory().setArmorContents(null);
            if (team2Spawn != null) p.teleport(team2Spawn);
            giveNetherStar(p);
            p.sendMessage(ChatColor.YELLOW + "Get ready for the next round!");
        }

        gameRunning = false;

        // Auto-start if teams are still balanced
        checkAutoStart();
    }

    public void endGame() {
        gameRunning = false;
        redScore = 0;
        blueScore = 0;

        for (Player p : teamRed) {
            p.getInventory().clear();
            p.getInventory().setArmorContents(null);
            if (lobbySpawn != null) p.teleport(lobbySpawn);
            p.sendMessage(ChatColor.YELLOW + "Game ended! Teleported to lobby.");
            giveNetherStar(p);
        }

        for (Player p : teamBlue) {
            p.getInventory().clear();
            p.getInventory().setArmorContents(null);
            if (lobbySpawn != null) p.teleport(lobbySpawn);
            p.sendMessage(ChatColor.YELLOW + "Game ended! Teleported to lobby.");
            giveNetherStar(p);
        }

        teamRed.clear();
        teamBlue.clear();
        playerTeam.clear();
        waitingQueue.clear();

        for (Player p : Bukkit.getOnlinePlayers()) {
            giveNetherStar(p);
        }

        Bukkit.broadcastMessage(ChatColor.RED + "Game has been forcibly ended by an admin!");
    }

    public String getPlayerTeam(Player player) {
        return playerTeam.get(player);
    }

    public boolean isInBridgeZone(Location loc) {
        if (bridgeZoneMin == null || bridgeZoneMax == null) return true; // Allow if not set
        return loc.getX() >= bridgeZoneMin.getX() && loc.getX() <= bridgeZoneMax.getX() &&
                loc.getY() >= bridgeZoneMin.getY() && loc.getY() <= bridgeZoneMax.getY() &&
                loc.getZ() >= bridgeZoneMin.getZ() && loc.getZ() <= bridgeZoneMax.getZ();
    }

    // ==================== COMMANDS ====================

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this!");
            return true;
        }

        Player p = (Player) sender;

        switch (command.getName().toLowerCase()) {
            case "setspawnteam":
                if (!p.hasPermission("bridge.admin")) { p.sendMessage(ChatColor.RED + "No permission!"); return true; }
                if (args.length != 1) { p.sendMessage(ChatColor.RED + "Usage: /setspawnteam <1|2>"); return true; }
                try {
                    int team = Integer.parseInt(args[0]);
                    if (team == 1 || team == 2) {
                        setTeamSpawn(team, p.getLocation());
                        p.sendMessage(ChatColor.GREEN + "Team " + team + " spawn set at your location!");
                    }
                } catch (NumberFormatException e) { p.sendMessage(ChatColor.RED + "Team must be 1 or 2!"); }
                break;

            case "minigamespawn":
                if (!p.hasPermission("bridge.admin")) { p.sendMessage(ChatColor.RED + "No permission!"); return true; }
                setLobbySpawn(p.getLocation());
                p.sendMessage(ChatColor.GREEN + "Lobby spawn set at your location!");
                break;

            case "endgame":
                if (!p.hasPermission("bridge.admin")) { p.sendMessage(ChatColor.RED + "No permission!"); return true; }
                endGame();
                p.sendMessage(ChatColor.GREEN + "Game ended!");
                break;

            case "join":
                joinQueue(p);
                break;

            case "leave":
                leaveQueue(p);
                break;

            case "team":
                if (args.length < 1) { p.sendMessage(ChatColor.RED + "Usage: /team <red|blue|leave>"); return true; }
                if (args[0].equalsIgnoreCase("red")) joinTeam(p, "red");
                else if (args[0].equalsIgnoreCase("blue")) joinTeam(p, "blue");
                else if (args[0].equalsIgnoreCase("leave")) leaveTeam(p);
                else p.sendMessage(ChatColor.RED + "Invalid! Use red, blue, or leave");
                break;
        }
        return true;
    }
}