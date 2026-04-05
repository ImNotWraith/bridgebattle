package org.minigame.minigame;

import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.*;

import java.util.*;

public class BridgeBattle extends JavaPlugin {

    private static BridgeBattle instance;
    private BridgeBattleGUI gameMenu;

    // Game states
    public enum GameState {
        WAITING, COUNTDOWN, ACTIVE, ENDING
    }

    public enum Team {
        RED, BLUE, NONE
    }

    private GameState gameState = GameState.WAITING;

    // Teams
    private List<Player> redTeam = new ArrayList<>();
    private List<Player> blueTeam = new ArrayList<>();
    private Map<Player, Team> playerTeam = new HashMap<>();
    private Map<Player, Integer> playerKills = new HashMap<>();

    // Game stats
    private int redScore = 0;
    private int blueScore = 0;
    private int roundTimeLeft = 180;
    private BukkitRunnable gameTimer;
    private BukkitRunnable countdownTimer;
    private int countdownSeconds = 10;

    // Spawn locations
    private Location lobbySpawn;
    private Location redSpawn;
    private Location blueSpawn;

    // Bridge zone
    private Location bridgeZoneMin;
    private Location bridgeZoneMax;

    // Chest Locations
    private List<Location> chestLocations = new ArrayList<>();
    private Map<Location, Team> capturedChests = new HashMap<>();

    // Combat Items
    private Map<UUID, Integer> bowLevels = new HashMap<>();

    // Generators
    private List<Location> diamondGens = new ArrayList<>();
    private List<Location> emeraldGens = new ArrayList<>();
    private List<Location> goldGens = new ArrayList<>();

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        loadSpawns();
        loadBridgeZone();
        loadGenerators();

        this.gameMenu = new BridgeBattleGUI(this);
        getServer().getPluginManager().registerEvents(this.gameMenu, this);

        getServer().getPluginManager().registerEvents(new GameShop(), this);
        getServer().getPluginManager().registerEvents(new GameListener(), this);
        getLogger().info(ChatColor.GREEN + "BridgeBattle enabled with GUI!");
    }

    // Helper to open the menu
    public void openGameMenu(Player player) {
        player.openInventory(gameMenu.getInventory());
    }

    @Override
    public void onDisable() {
        // Optional: Clean up items near generators on shutdown
        for (Location loc : goldGens) {
            loc.getWorld().getNearbyEntities(loc, 2, 2, 2).forEach(entity -> {
                if (entity instanceof org.bukkit.entity.Item) entity.remove();
            });
        }
        getLogger().info(ChatColor.RED + "BridgeBattle disabled!");
    }

    public static BridgeBattle getInstance() {
        return instance;
    }

    // ==================== SPAWN MANAGEMENT ====================

    private void loadSpawns() {
        FileConfiguration config = getConfig();
        lobbySpawn = loadSpawn("lobby");
        redSpawn = loadSpawn("red");
        blueSpawn = loadSpawn("blue");
    }

    private void loadBridgeZone() {
        FileConfiguration config = getConfig();
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

    private void loadChests() {
        chestLocations.clear();
        capturedChests.clear();
        FileConfiguration config = getConfig();
        for (int i = 1; i <= 3; i++) {
            if (config.contains("chests.c" + i)) {
                World w = Bukkit.getWorld(config.getString("chests.c" + i + ".world"));
                Location loc = new Location(w,
                        config.getDouble("chests.c" + i + ".x"),
                        config.getDouble("chests.c" + i + ".y"),
                        config.getDouble("chests.c" + i + ".z"));
                chestLocations.add(loc);
            }
        }
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

    // Helper to set a chest location
    public void setGameChest(int index, Location loc) {
        if (index < 1 || index > 3) return;

        FileConfiguration config = getConfig();
        config.set("chests.c" + index + ".world", loc.getWorld().getName());
        config.set("chests.c" + index + ".x", loc.getBlockX());
        config.set("chests.c" + index + ".y", loc.getBlockY());
        config.set("chests.c" + index + ".z", loc.getBlockZ());
        saveConfig();

        // Refresh the list
        loadChests();
    }

    public void setSpawn(String type, Location location) {
        switch (type.toLowerCase()) {
            case "lobby":
                lobbySpawn = location;
                saveSpawn("lobby", location);
                break;
            case "red":
                redSpawn = location;
                saveSpawn("red", location);
                break;
            case "blue":
                blueSpawn = location;
                saveSpawn("blue", location);
                break;
        }
    }

    public Location getSpawn(String type) {
        switch (type.toLowerCase()) {
            case "lobby": return lobbySpawn;
            case "red": return redSpawn;
            case "blue": return blueSpawn;
            default: return null;
        }
    }

    public boolean isInBridgeZone(Location loc) {
        if (bridgeZoneMin == null || bridgeZoneMax == null) return true;
        return loc.getX() >= bridgeZoneMin.getX() && loc.getX() <= bridgeZoneMax.getX() &&
                loc.getY() >= bridgeZoneMin.getY() && loc.getY() <= bridgeZoneMax.getY() &&
                loc.getZ() >= bridgeZoneMin.getZ() && loc.getZ() <= bridgeZoneMax.getZ();
    }

    // ==================== PLAYER MANAGEMENT ====================

    public void joinGame(Player player) {
        if (gameState != GameState.WAITING) {
            player.sendMessage(ChatColor.RED + "Game already in progress!");
            return;
        }

        if (playerTeam.containsKey(player)) {
            player.sendMessage(ChatColor.RED + "You're already in the game!");
            return;
        }

        // Auto-balance teams
        Team team = getSmallestTeam();

        if (team == Team.RED) {
            redTeam.add(player);
            playerTeam.put(player, Team.RED);
            playerKills.put(player, 0);
            player.sendMessage(ChatColor.GREEN + "You joined the " + ChatColor.RED + "RED TEAM!");
            if (redSpawn != null) player.teleport(redSpawn);
        } else if (team == Team.BLUE) {
            blueTeam.add(player);
            playerTeam.put(player, Team.BLUE);
            playerKills.put(player, 0);
            player.sendMessage(ChatColor.GREEN + "You joined the " + ChatColor.BLUE + "BLUE TEAM!");
            if (blueSpawn != null) player.teleport(blueSpawn);
        } else {
            player.sendMessage(ChatColor.RED + "Game is full!");
            return;
        }

        // Give game items
        giveGameItems(player);

        // Update scoreboard
        updateScoreboard();

        // Broadcast
        Bukkit.broadcastMessage(ChatColor.GREEN + player.getName() + " joined the game! (" + getTotalPlayers() + "/" + getConfig().getInt("game.max-players") + ")");

        // Check if we can start
        checkStartGame();
    }

    public void leaveGame(Player player) {
        Team team = playerTeam.get(player);

        if (team == Team.RED) {
            redTeam.remove(player);
        } else if (team == Team.BLUE) {
            blueTeam.remove(player);
        }

        playerTeam.remove(player);
        playerKills.remove(player);
        bowLevels.remove(player.getUniqueId());

        // Clear inventory
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);

        // Give lobby items
        giveLobbyItems(player);

        // Teleport to lobby
        if (lobbySpawn != null) {
            player.teleport(lobbySpawn);
        }

        player.sendMessage(ChatColor.YELLOW + "You left the game!");

        // Update scoreboard for remaining players
        for (Player p : getAllPlayers()) {
            updateScoreboard(p);
        }

        // If game is active and teams become empty, end game
        if (gameState == GameState.ACTIVE && (redTeam.isEmpty() || blueTeam.isEmpty())) {
            endGame();
        }

        // If waiting and players left, cancel countdown
        if (gameState == GameState.COUNTDOWN && getTotalPlayers() < getConfig().getInt("game.min-players")) {
            cancelCountdown();
        }
    }

    public void giveLobbyItems(Player player) {
        player.getInventory().clear();

        ItemStack joinItem = new ItemStack(Material.NETHER_STAR, 1);
        ItemMeta meta = joinItem.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "Join Game");
        meta.setLore(Arrays.asList(ChatColor.GRAY + "Right click to join the game!"));
        joinItem.setItemMeta(meta);
        player.getInventory().setItem(0, joinItem);
    }

    private Team getSmallestTeam() {
        int maxPerTeam = getConfig().getInt("game.players-per-team", 3);
        if (redTeam.size() < maxPerTeam && redTeam.size() <= blueTeam.size()) {
            return Team.RED;
        } else if (blueTeam.size() < maxPerTeam) {
            return Team.BLUE;
        }
        return Team.NONE;
    }

    private int getTotalPlayers() {
        return redTeam.size() + blueTeam.size();
    }

    private void checkStartGame() {
        if (gameState != GameState.WAITING) return;

        int total = getTotalPlayers();
        int min = getConfig().getInt("game.min-players", 2);

        if (total >= min) {
            startCountdown();
        }
    }

    private void loadGenerators() {
        FileConfiguration config = getConfig();
        goldGens = parseLocations(config.getStringList("generators.gold"));
        diamondGens = parseLocations(config.getStringList("generators.diamond"));
        emeraldGens = parseLocations(config.getStringList("generators.emerald"));
    }

    private List<Location> parseLocations(List<String> list) {
        List<Location> locs = new ArrayList<>();
        for (String s : list) {
            String[] parts = s.split(",");
            World w = Bukkit.getWorld(parts[0]);
            if (w != null) {
                locs.add(new Location(w, Double.parseDouble(parts[1]), Double.parseDouble(parts[2]), Double.parseDouble(parts[3])));
            }
        }
        return locs;
    }

    // Returns the list of the 3 chest locations
    public List<Location> getChestLocations() {
        return chestLocations;
    }

    // Returns the map of which team has captured which chest
    public Map<Location, Team> getCapturedChests() {
        return capturedChests;
    }

    // Logic to check for a winner based on 2/3 chests
    public void claimChest(Location loc, Team team) {
        capturedChests.put(loc, team);

        long redCount = capturedChests.values().stream().filter(t -> t == Team.RED).count();
        long blueCount = capturedChests.values().stream().filter(t -> t == Team.BLUE).count();

        if (redCount >= 2) {
            winRound(Team.RED);
        } else if (blueCount >= 2) {
            winRound(Team.BLUE);
        }
    }

    // ==================== CHEST CAPTURE =======================

    // Add this to your BridgeBattle class
    public void handleChestCapture(Location chestLoc, Player player) {
        Team team = getPlayerTeam(player);
        if (team == Team.NONE) return;

        // Check if this chest was already captured this round
        if (getCapturedChests().containsKey(chestLoc)) {
            player.sendMessage(ChatColor.RED + "This chest has already been claimed!");
            return;
        }

        // Capture the chest
        getCapturedChests().put(chestLoc, team);

        // Broadcast the capture
        String teamName = (team == Team.RED) ? ChatColor.RED + "RED" : ChatColor.BLUE + "BLUE";
        Bukkit.broadcastMessage(teamName + ChatColor.YELLOW + " has captured a chest! (" +
                getTeamCapturedCount(team) + "/2 needed to win)");

        // Visual Feedback
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1f, 1f);
        player.sendTitle("", teamName + ChatColor.WHITE + " captured a point!", 10, 40, 10);

        // Check if the team reached 2 points
        if (getTeamCapturedCount(team) >= 2) {
            winRound(team);
        }
    }

    // Helper to count how many chests a specific team has
    private int getTeamCapturedCount(Team team) {
        int count = 0;
        for (Team t : getCapturedChests().values()) {
            if (t == team) count++;
        }
        return count;
    }

    // ==================== GAME START / COUNTDOWN / GENERATOR START ====================

    private void startCountdown() {
        gameState = GameState.COUNTDOWN;
        countdownSeconds = getConfig().getInt("game.countdown-seconds", 10);

        Bukkit.broadcastMessage(ChatColor.GREEN + "═══════════════════════════════");
        Bukkit.broadcastMessage(ChatColor.GREEN + "  Enough players! Game starting!");
        Bukkit.broadcastMessage(ChatColor.GREEN + "═══════════════════════════════");

        countdownTimer = new BukkitRunnable() {
            @Override
            public void run() {
                if (gameState != GameState.COUNTDOWN) {
                    this.cancel();
                    return;
                }

                if (countdownSeconds <= 0) {
                    startGame();
                    this.cancel();
                    return;
                }

                if (countdownSeconds <= 10 || countdownSeconds % 5 == 0) {
                    for (Player player : getAllPlayers()) {
                        player.sendTitle(ChatColor.YELLOW + "" + countdownSeconds, "Get ready!", 0, 20, 0);
                        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                    }
                    Bukkit.broadcastMessage(ChatColor.YELLOW + "Game starting in " + ChatColor.RED + countdownSeconds + ChatColor.YELLOW + " seconds!");
                }

                countdownSeconds--;
            }
        };

        countdownTimer.runTaskTimer(this, 0L, 20L);
    }

    private void cancelCountdown() {
        if (countdownTimer != null) {
            countdownTimer.cancel();
        }
        gameState = GameState.WAITING;
        Bukkit.broadcastMessage(ChatColor.RED + "Game start cancelled - not enough players!");
    }

    private void startGame() {
        gameState = GameState.ACTIVE;
        roundTimeLeft = getConfig().getInt("game.round-time-seconds", 180);

        // Teleport players to team spawns and give items
        for (Player player : redTeam) {
            if (redSpawn != null) player.teleport(redSpawn);
            giveGameItems(player);
            player.sendTitle(ChatColor.RED + "GAME START!", "Build the bridge!", 10, 60, 20);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        }

        for (Player player : blueTeam) {
            if (blueSpawn != null) player.teleport(blueSpawn);
            giveGameItems(player);
            player.sendTitle(ChatColor.BLUE + "GAME START!", "Build the bridge!", 10, 60, 20);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        }

        // Starts the generator
        startGenerators();

        // Start game timer
        startGameTimer();

        // Update scoreboard
        for (Player player : getAllPlayers()) {
            updateScoreboard(player);
        }

        Bukkit.broadcastMessage(ChatColor.AQUA + "Generators have been activated!");

        // Broadcast start message
        Bukkit.broadcastMessage(ChatColor.GREEN + "═══════════════════════════════");
        Bukkit.broadcastMessage(ChatColor.GREEN + "  GAME STARTED!");
        Bukkit.broadcastMessage(ChatColor.RED + "  RED: " + redTeam.size() + ChatColor.GREEN + " vs " + ChatColor.BLUE + blueTeam.size());
        Bukkit.broadcastMessage(ChatColor.GREEN + "═══════════════════════════════");
    }

    public void startGenerators() {
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (getGameState() != GameState.ACTIVE) {
                    this.cancel();
                    return;
                }

                ticks += 20; // 1 second has passed

                // 1. GOLD GENERATORS (Every 1 Second)
                for (Location loc : goldGens) {
                    // THE CHECK LINE GOES HERE:
                    if (loc.getWorld().getNearbyEntities(loc, 1, 1, 1).stream()
                            .noneMatch(e -> e.getType() == org.bukkit.entity.EntityType.DROPPED_ITEM)) {
                        loc.getWorld().dropItemNaturally(loc.clone().add(0.5, 1, 0.5), new ItemStack(Material.GOLD_INGOT));
                    }
                }

                // 2. DIAMOND GENERATORS (Every 30 Seconds)
                if (ticks % 600 == 0) {
                    for (Location loc : diamondGens) {
                        // THE CHECK LINE GOES HERE:
                        if (loc.getWorld().getNearbyEntities(loc, 1, 1, 1).stream()
                                .noneMatch(e -> e.getType() == org.bukkit.entity.EntityType.DROPPED_ITEM)) {
                            loc.getWorld().dropItemNaturally(loc.clone().add(0.5, 1, 0.5), new ItemStack(Material.DIAMOND));
                        }
                    }
                }

                // 3. EMERALD GENERATORS (Every 60 Seconds)
                if (ticks % 1200 == 0) {
                    for (Location loc : emeraldGens) {
                        // THE CHECK LINE GOES HERE:
                        if (loc.getWorld().getNearbyEntities(loc, 1, 1, 1).stream()
                                .noneMatch(e -> e.getType() == org.bukkit.entity.EntityType.DROPPED_ITEM)) {
                            loc.getWorld().dropItemNaturally(loc.clone().add(0.5, 1, 0.5), new ItemStack(Material.EMERALD));
                        }
                    }
                }
            }
        }.runTaskTimer(this, 0L, 20L);
    }

    private void startGameTimer() {
        gameTimer = new BukkitRunnable() {
            @Override
            public void run() {
                if (gameState != GameState.ACTIVE) {
                    this.cancel();
                    return;
                }

                if (roundTimeLeft <= 0) {
                    roundTimeUp();
                    this.cancel();
                    return;
                }

                // Update action bar every second
                for (Player player : getAllPlayers()) {
                    player.sendActionBar(ChatColor.GOLD + "Time: " + formatTime(roundTimeLeft) +
                            ChatColor.WHITE + " | " + ChatColor.RED + redScore + ChatColor.WHITE + " - " +
                            ChatColor.BLUE + blueScore);
                }

                roundTimeLeft--;
            }
        };
        gameTimer.runTaskTimer(this, 0L, 20L);
    }

    private String formatTime(int seconds) {
        int minutes = seconds / 60;
        int secs = seconds % 60;
        return String.format("%d:%02d", minutes, secs);
    }

    private void roundTimeUp() {
        Bukkit.broadcastMessage(ChatColor.YELLOW + "Time's up! Round ended!");
        resetRound();
    }

    // ==================== GAME ITEMS ====================

    private void giveGameItems(Player player) {
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);

        // Bridge blocks
        ItemStack blocks = new ItemStack(Material.WHITE_WOOL, 64);
        ItemMeta blockMeta = blocks.getItemMeta();
        blockMeta.setDisplayName(ChatColor.AQUA + "Bridge Blocks");
        blocks.setItemMeta(blockMeta);
        player.getInventory().setItem(1, blocks);

        // Snowballs
        ItemStack snowballs = new ItemStack(Material.SNOWBALL, 32);
        ItemMeta snowMeta = snowballs.getItemMeta();
        snowMeta.setDisplayName(ChatColor.WHITE + "Knockback Snowball");
        snowballs.setItemMeta(snowMeta);
        player.getInventory().setItem(2, snowballs);

        // Knockback sword
        ItemStack sword = new ItemStack(Material.WOODEN_SWORD, 1);
        ItemMeta swordMeta = sword.getItemMeta();
        swordMeta.setDisplayName(ChatColor.GOLD + "Knockback Sword");
        swordMeta.setUnbreakable(true);
        sword.setItemMeta(swordMeta);
        player.getInventory().setItem(0, sword);

        // Team armor
        Team team = playerTeam.get(player);
        if (team == Team.RED) {
            player.getInventory().setHelmet(new ItemStack(Material.RED_WOOL));
        } else {
            player.getInventory().setHelmet(new ItemStack(Material.BLUE_WOOL));
        }
    }

    // ==================== KILL MANAGEMENT ====================

    public void addKill(Player player) {
        int kills = playerKills.getOrDefault(player, 0) + 1;
        playerKills.put(player, kills);

        // Update scoreboard for all players
        for (Player p : getAllPlayers()) {
            updateScoreboard(p);
        }

        // Play sound
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
    }

    public int getKills(Player player) {
        return playerKills.getOrDefault(player, 0);
    }

    // ==================== SCOREBOARD ====================

    public void updateScoreboard() {
        for (Player player : getAllPlayers()) {
            updateScoreboard(player);
        }
    }

    public void updateScoreboard(Player player) {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        Scoreboard board = manager.getNewScoreboard();
        Objective obj = board.registerNewObjective("bridge", "dummy", ChatColor.GOLD + "⚔ Bridge Battle ⚔");
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        // Team scores
        obj.getScore(ChatColor.RED + "Red Team: " + redScore).setScore(5);
        obj.getScore(ChatColor.BLUE + "Blue Team: " + blueScore).setScore(4);
        obj.getScore(ChatColor.GRAY + "─────────────").setScore(3);

        // Player kills
        obj.getScore(ChatColor.YELLOW + "Top Kills:").setScore(2);

        // Get top 3 killers
        List<Map.Entry<Player, Integer>> sortedKills = new ArrayList<>(playerKills.entrySet());
        sortedKills.sort((a, b) -> b.getValue().compareTo(a.getValue()));

        int rank = 1;
        for (int i = 0; i < Math.min(3, sortedKills.size()); i++) {
            Map.Entry<Player, Integer> entry = sortedKills.get(i);
            String name = entry.getKey().getName();
            if (name.length() > 12) name = name.substring(0, 12);
            obj.getScore(ChatColor.WHITE + "#" + rank + " " + name + ": " + entry.getValue()).setScore(2 - rank);
            rank++;
        }

        // Game state
        String state = "";
        switch (gameState) {
            case WAITING:
                state = ChatColor.YELLOW + "Waiting...";
                break;
            case COUNTDOWN:
                state = ChatColor.YELLOW + "Starting: " + countdownSeconds;
                break;
            case ACTIVE:
                state = ChatColor.GREEN + "LIVE!";
                break;
            case ENDING:
                state = ChatColor.RED + "Ending...";
                break;
        }
        obj.getScore(ChatColor.GRAY + "─────────────").setScore(-1);
        obj.getScore(ChatColor.AQUA + "Status: " + state).setScore(-2);

        player.setScoreboard(board);
    }

    // ==================== ROUND MANAGEMENT ====================

    public void winRound(Team team) {
        if (gameState != GameState.ACTIVE) return;

        if (team == Team.RED) {
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

        // Play win sound
        for (Player player : getAllPlayers()) {
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        }

        int roundsToWin = getConfig().getInt("game.rounds-to-win", 3);

        if (redScore >= roundsToWin) {
            Bukkit.broadcastMessage(ChatColor.GOLD + "★★★★★★★★★★★★★★★★★★★★★★★");
            Bukkit.broadcastMessage(ChatColor.GOLD + "  RED TEAM WINS THE MATCH!");
            Bukkit.broadcastMessage(ChatColor.GOLD + "★★★★★★★★★★★★★★★★★★★★★★★");
            endGame();
        } else if (blueScore >= roundsToWin) {
            Bukkit.broadcastMessage(ChatColor.GOLD + "★★★★★★★★★★★★★★★★★★★★★★★");
            Bukkit.broadcastMessage(ChatColor.GOLD + "  BLUE TEAM WINS THE MATCH!");
            Bukkit.broadcastMessage(ChatColor.GOLD + "★★★★★★★★★★★★★★★★★★★★★★★");
            endGame();
        } else {
            // Reset round after delay
            new BukkitRunnable() {
                @Override
                public void run() {
                    resetRound();
                }
            }.runTaskLater(this, 80L);
        }
    }

    private void resetRound() {
        // Clear all bridge blocks (scan and remove white wool in bridge zone)
        if (bridgeZoneMin != null && bridgeZoneMax != null) {
            for (int x = (int) bridgeZoneMin.getX(); x <= (int) bridgeZoneMax.getX(); x++) {
                for (int y = (int) bridgeZoneMin.getY(); y <= (int) bridgeZoneMax.getY(); y++) {
                    for (int z = (int) bridgeZoneMin.getZ(); z <= (int) bridgeZoneMax.getZ(); z++) {
                        Location loc = new Location(bridgeZoneMin.getWorld(), x, y, z);
                        if (loc.getBlock().getType() == Material.WHITE_WOOL) {
                            loc.getBlock().setType(Material.AIR);
                        }
                    }
                }
            }
        }

        // Reset player positions and inventory
        for (Player player : redTeam) {
            player.getInventory().clear();
            player.getInventory().setArmorContents(null);
            if (redSpawn != null) player.teleport(redSpawn);
            player.setHealth(20);
            player.setFoodLevel(20);
            giveGameItems(player);
            player.sendMessage(ChatColor.YELLOW + "Next round starting!");
        }

        for (Player player : blueTeam) {
            player.getInventory().clear();
            player.getInventory().setArmorContents(null);
            if (blueSpawn != null) player.teleport(blueSpawn);
            player.setHealth(20);
            player.setFoodLevel(20);
            giveGameItems(player);
            player.sendMessage(ChatColor.YELLOW + "Next round starting!");
        }

        for (Location loc : chestLocations) {
            loc.clone().add(0, 1, 0).getBlock().setType(Material.AIR);
        }

        // Reset timer
        if (gameTimer != null) gameTimer.cancel();
        roundTimeLeft = getConfig().getInt("game.round-time-seconds", 180);
        startGameTimer();

        // Update scoreboard
        updateScoreboard();
        capturedChests.clear();

        // Clear the capture data so chests can be opened again
        getCapturedChests().clear();

        Bukkit.broadcastMessage(ChatColor.GOLD + "The 3 chests have been reset! First to 2 wins!");

        // Brief countdown for next round
        new BukkitRunnable() {
            int count = 5;
            @Override
            public void run() {
                if (count <= 0) {
                    Bukkit.broadcastMessage(ChatColor.GREEN + "Round started!");
                    this.cancel();
                    return;
                }
                for (Player player : getAllPlayers()) {
                    player.sendTitle(ChatColor.YELLOW + "" + count, "Next round!", 0, 20, 0);
                }
                count--;
            }
        }.runTaskTimer(this, 0L, 20L);
    }

    public void endGame() {
        if (gameTimer != null) gameTimer.cancel();
        if (countdownTimer != null) countdownTimer.cancel();

        gameState = GameState.WAITING;
        redScore = 0;
        blueScore = 0;

        for (Player player : getAllPlayers()) {
            player.getInventory().clear();
            player.getInventory().setArmorContents(null);
            if (lobbySpawn != null) player.teleport(lobbySpawn);
            giveLobbyItems(player);
            player.sendMessage(ChatColor.YELLOW + "Game ended! Use /bridge join to play again!");
        }

        redTeam.clear();
        blueTeam.clear();
        playerTeam.clear();
        playerKills.clear();
        bowLevels.clear();

        // Update scoreboard for any remaining players
        for (Player player : Bukkit.getOnlinePlayers()) {
            updateScoreboard(player);
        }
    }

    // ==================== UTILITIES ====================

    public List<Player> getAllPlayers() {
        List<Player> all = new ArrayList<>();
        all.addAll(redTeam);
        all.addAll(blueTeam);
        return all;
    }

    public Team getPlayerTeam(Player player) {
        return playerTeam.getOrDefault(player, Team.NONE);
    }

    public GameState getGameState() {
        return gameState;
    }

    public int getBowLevel(Player player) {
        return bowLevels.getOrDefault(player.getUniqueId(), 1); // Default is Level 1
    }

    public void setBowLevel(Player player, int level) {
        bowLevels.put(player.getUniqueId(), level);
    }

    // ==================== COMMANDS ====================

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) return true;
        Player p = (Player) sender;

        // Change /b to open the GUI instead of auto-joining
        if (command.getName().equalsIgnoreCase("b")) {
            openGameMenu(p);
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("join")) {
            openGameMenu(p); // Open GUI when they type /bridge join
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "join":
                joinGame(p);
                break;

            case "leave":
                leaveGame(p);
                break;

            case "start":
                if (!p.hasPermission("bridge.admin")) {
                    p.sendMessage(ChatColor.RED + "No permission!");
                    return true;
                }
                if (getTotalPlayers() >= 2) {
                    if (countdownTimer != null) countdownTimer.cancel();
                    startGame();
                } else {
                    p.sendMessage(ChatColor.RED + "Not enough players!");
                }
                break;

            case "end":
                if (!p.hasPermission("bridge.admin")) {
                    p.sendMessage(ChatColor.RED + "No permission!");
                    return true;
                }
                endGame();
                break;

            case "setspawn":
                if (!p.hasPermission("bridge.admin")) {
                    p.sendMessage(ChatColor.RED + "No permission!");
                    return true;
                }
                if (args.length < 2) {
                    p.sendMessage(ChatColor.RED + "Usage: /bridge setspawn <lobby|red|blue>");
                    return true;
                }
                setSpawn(args[1], p.getLocation());
                p.sendMessage(ChatColor.GREEN + "Spawn " + args[1] + " set!");
                break;

            case "setbridgezone":
                if (!p.hasPermission("bridge.admin")) {
                    p.sendMessage(ChatColor.RED + "No permission!");
                    return true;
                }
                if (args.length < 2) {
                    p.sendMessage(ChatColor.RED + "Usage: /bridge setbridgezone <min|max>");
                    return true;
                }
                FileConfiguration config = getConfig();
                World w = p.getWorld();
                if (args[1].equalsIgnoreCase("min")) {
                    config.set("bridge_zone.world", w.getName());
                    config.set("bridge_zone.min.x", p.getLocation().getX());
                    config.set("bridge_zone.min.y", p.getLocation().getY());
                    config.set("bridge_zone.min.z", p.getLocation().getZ());
                    p.sendMessage(ChatColor.GREEN + "Bridge zone MIN corner set!");
                } else if (args[1].equalsIgnoreCase("max")) {
                    config.set("bridge_zone.max.x", p.getLocation().getX());
                    config.set("bridge_zone.max.y", p.getLocation().getY());
                    config.set("bridge_zone.max.z", p.getLocation().getZ());
                    p.sendMessage(ChatColor.GREEN + "Bridge zone MAX corner set!");
                }
                saveConfig();
                loadBridgeZone();
                break;

            case "setchest":
                if (!p.hasPermission("bridge.admin")) {
                    p.sendMessage(ChatColor.RED + "No permission!");
                    return true;
                }

                if (args.length < 2) {
                    p.sendMessage(ChatColor.RED + "Usage: /bridge setchest <1|2|3>");
                    return true;
                }

                try {
                    int id = Integer.parseInt(args[1]);
                    if (id < 1 || id > 3) {
                        p.sendMessage(ChatColor.RED + "Use chest number 1, 2, or 3!");
                        return true;
                    }

                    // Get the block the player is LOOKING at
                    org.bukkit.block.Block targetBlock = p.getTargetBlockExact(5);

                    if (targetBlock == null || targetBlock.getType() != Material.CHEST) {
                        p.sendMessage(ChatColor.RED + "You must be looking at a Chest to set it!");
                        return true;
                    }

                    Location loc = targetBlock.getLocation();
                    setGameChest(id, loc); // This saves it to your config

                    p.sendMessage(ChatColor.GOLD + "Successfully set " + ChatColor.YELLOW + "Game Chest #" + id);
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 2f);

                } catch (NumberFormatException ex) {
                    p.sendMessage(ChatColor.RED + "Invalid number!");
                }
                return true;

            case "setgen":
                if (!p.hasPermission("bridge.admin")) return true;
                if (args.length < 2) {
                    p.sendMessage(ChatColor.RED + "Usage: /bridge setgen <gold|diamond|emerald>");
                    return true;
                }

                String type = args[1].toLowerCase();
                Location loc = p.getLocation().getBlock().getLocation(); // Center of the block

                FileConfiguration configGenerator = getConfig();
                String path = "generators." + type;
                List<String> serializedLocs = configGenerator.getStringList(path);

                // Save as "world,x,y,z" string
                serializedLocs.add(loc.getWorld().getName() + "," + loc.getX() + "," + loc.getY() + "," + loc.getZ());
                configGenerator.set(path, serializedLocs);
                saveConfig();

                // Add to active lists immediately
                if (type.equals("gold")) goldGens.add(loc);
                else if (type.equals("diamond")) diamondGens.add(loc);
                else if (type.equals("emerald")) emeraldGens.add(loc);

                p.sendMessage(ChatColor.GREEN + type.toUpperCase() + " generator set at your location!");
                break;

            case "spawnshop":
                if (!p.hasPermission("bridge.admin")) return true;
                Villager shopkeeper = p.getWorld().spawn(p.getLocation(), Villager.class);
                shopkeeper.setCustomName(ChatColor.GOLD + "§lGAME SHOP");
                shopkeeper.setCustomNameVisible(true);
                shopkeeper.setAI(false); // Keeps him stationary
                shopkeeper.setInvulnerable(true);
                p.sendMessage(ChatColor.GREEN + "Shopkeeper spawned!");
                break;

            case "top":
                showTopKillers(p);
                break;

            default:
                sendHelp(p);
                break;
        }

        return true;
    }

    private void showTopKillers(Player player) {
        player.sendMessage(ChatColor.GOLD + "══════ Top Killers ══════");

        List<Map.Entry<Player, Integer>> sortedKills = new ArrayList<>(playerKills.entrySet());
        sortedKills.sort((a, b) -> b.getValue().compareTo(a.getValue()));

        int rank = 1;
        for (Map.Entry<Player, Integer> entry : sortedKills) {
            String team = playerTeam.get(entry.getKey()) == Team.RED ? ChatColor.RED + "RED" : ChatColor.BLUE + "BLUE";
            player.sendMessage(ChatColor.YELLOW + "#" + rank + " " + team + ChatColor.WHITE + " " + entry.getKey().getName() + ": " + ChatColor.GREEN + entry.getValue() + " kills");
            rank++;
            if (rank > 10) break;
        }

        if (sortedKills.isEmpty()) {
            player.sendMessage(ChatColor.GRAY + "No kills yet!");
        }

        player.sendMessage(ChatColor.GOLD + "═══════════════════════════");
    }

    private void sendHelp(Player p) {
        p.sendMessage(ChatColor.GOLD + "══════ Bridge Battle Help ══════");
        p.sendMessage(ChatColor.YELLOW + "/bridge join" + ChatColor.WHITE + " - Join the game");
        p.sendMessage(ChatColor.YELLOW + "/bridge leave" + ChatColor.WHITE + " - Leave the game");
        p.sendMessage(ChatColor.YELLOW + "/bridge top" + ChatColor.WHITE + " - Show top killers");
        p.sendMessage(ChatColor.YELLOW + "/b" + ChatColor.WHITE + " - Quick join");
        if (p.hasPermission("bridge.admin")) {
            p.sendMessage(ChatColor.YELLOW + "/bridge start" + ChatColor.WHITE + " - Force start");
            p.sendMessage(ChatColor.YELLOW + "/bridge end" + ChatColor.WHITE + " - Force end");
            p.sendMessage(ChatColor.YELLOW + "/bridge setspawn <lobby|red|blue>" + ChatColor.WHITE + " - Set spawns");
            p.sendMessage(ChatColor.YELLOW + "/bridge setbridgezone <min|max>" + ChatColor.WHITE + " - Set bridge area");
        }
        p.sendMessage(ChatColor.GOLD + "═══════════════════════════════");
    }
}