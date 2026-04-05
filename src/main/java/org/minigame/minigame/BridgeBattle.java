package org.minigame.minigame;

import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
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

    public enum GameState { WAITING, COUNTDOWN, ACTIVE, ENDING }
    public enum Team { RED, BLUE, NONE }

    private GameState gameState = GameState.WAITING;
    private int currentRound = 1;

    private List<Player> redTeam = new ArrayList<>();
    private List<Player> blueTeam = new ArrayList<>();
    private Map<Player, Team> playerTeam = new HashMap<>();
    private Map<Player, Integer> playerKills = new HashMap<>();
    private int redScore = 0;
    private int blueScore = 0;

    private int matchSeconds = 0;
    private BukkitRunnable matchTimerTask;
    private BukkitRunnable countdownTimer;
    private int countdownSeconds = 10;

    private Location lobbySpawn, redSpawn, blueSpawn;
    private Location bridgeZoneMin, bridgeZoneMax;
    private List<Location> chestLocations = new ArrayList<>();
    private Map<Location, Team> capturedChests = new HashMap<>();
    private List<Location> goldGens = new ArrayList<>();
    private List<Location> diamondGens = new ArrayList<>();
    private List<Location> emeraldGens = new ArrayList<>();
    private Map<Location, ArmorStand> genHolograms = new HashMap<>();

    private Map<UUID, Integer> bowLevels = new HashMap<>();
    private Set<Location> playerPlacedBlocks = new HashSet<>();

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        gameMenu = new BridgeBattleGUI(this);

        loadSpawns();
        loadBridgeZone();
        loadChests();
        loadGenerators();
        refreshHolograms();

        getServer().getPluginManager().registerEvents(new GameListener(), this);
        getServer().getPluginManager().registerEvents(new GameShop(), this);

        getLogger().info("§aBridgeBattle Enabled!");
    }

    @Override
    public void onDisable() {
        for (ArmorStand holo : genHolograms.values()) {
            holo.remove();
        }
        genHolograms.clear();
    }

    // ==================== GETTERS ====================

    public static BridgeBattle getInstance() { return instance; }
    public GameState getGameState() { return gameState; }
    public List<Location> getChestLocations() { return chestLocations; }
    public Map<Location, Team> getCapturedChests() { return capturedChests; }
    public List<Player> getAllPlayers() {
        List<Player> l = new ArrayList<>(redTeam);
        l.addAll(blueTeam);
        return l;
    }
    public Team getPlayerTeam(Player p) { return playerTeam.getOrDefault(p, Team.NONE); }
    public int getTotalPlayers() { return redTeam.size() + blueTeam.size(); }
    public Set<Location> getPlayerPlacedBlocks() { return playerPlacedBlocks; }
    public int getBowLevel(Player p) { return bowLevels.getOrDefault(p.getUniqueId(), 1); }
    public void setBowLevel(Player p, int l) { bowLevels.put(p.getUniqueId(), l); }

    public Location getSpawn(String type) {
        return switch (type.toLowerCase()) {
            case "red" -> redSpawn;
            case "blue" -> blueSpawn;
            default -> lobbySpawn;
        };
    }

    public double getVoidLevel() {
        // Returns the config value, or defaults to -65.0 if you forget to add it
        return getConfig().getDouble("game.void-level", -65.0);
    }

    // ==================== COMBAT & POINTS ====================

    public void addKill(Player player) {
        playerKills.put(player, playerKills.getOrDefault(player, 0) + 1);
        updateScoreboard();
    }

    public void claimChest(Location loc, Team team) {
        if (capturedChests.containsKey(loc)) return;
        capturedChests.put(loc, team);

        long count = capturedChests.values().stream().filter(t -> t == team).count();
        if (count >= 2) {
            winRound(team);
        } else {
            updateScoreboard();
        }
    }

    public void handleChestCapture(Location chestLoc, Player player) {
        Team team = getPlayerTeam(player);
        if (team == Team.NONE || gameState != GameState.ACTIVE) return;

        if (capturedChests.containsKey(chestLoc)) {
            player.sendMessage("§cThis chest has already been captured!");
            return;
        }

        // Capture the chest
        capturedChests.put(chestLoc, team);

        // Broadcast it
        String teamName = (team == Team.RED) ? "§cRED" : "§bBLUE";
        Bukkit.broadcastMessage(teamName + " §ecaptured a chest!");

        // Check if team has 2 out of 3 chests
        long count = capturedChests.values().stream().filter(t -> t == team).count();
        if (count >= 2) {
            winRound(team);
        } else {
            updateScoreboard();
        }
    }

    // ==================== MATCH TIMER ====================

    private void startMatchTimer() {
        matchSeconds = 0;
        if (matchTimerTask != null) matchTimerTask.cancel();
        matchTimerTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (gameState != GameState.ACTIVE) { this.cancel(); return; }
                matchSeconds++;
                updateScoreboard();
            }
        };
        matchTimerTask.runTaskTimer(this, 0L, 20L);
    }

    private String formatMatchTime(int totalSeconds) {
        return String.format("%d:%02d", totalSeconds / 60, totalSeconds % 60);
    }

    // ==================== CORE MATCH LOGIC ====================

    public void startGame() {
        gameState = GameState.ACTIVE;
        currentRound = 1;
        redScore = 0;
        blueScore = 0;
        resetRoundData(false);
        startMatchTimer();
        startGenerators();
        Bukkit.broadcastMessage("§a§lMatch Started! First team to 2 rounds wins.");
    }

    public void winRound(Team team) {
        if (team == Team.RED) redScore++; else blueScore++;

        if (redScore >= 2 || blueScore >= 2) {
            Bukkit.broadcastMessage("§6§l" + team.name() + " WINS THE MATCH!");
            endGame();
        } else {
            currentRound++;
            resetRoundData(true);
        }
    }

    private void resetRoundData(boolean midGame) {
        capturedChests.clear();
        playerPlacedBlocks.clear();
        clearBridgeWool();

        for (Player p : getAllPlayers()) {
            p.getInventory().clear();
            p.teleport(getPlayerTeam(p) == Team.RED ? redSpawn : blueSpawn);
            p.setHealth(20);
            p.setFoodLevel(20);
            giveGameItems(p);
            if (midGame) p.sendTitle("§eROUND " + currentRound, "§7Chests have reset!", 10, 40, 10);
        }
    }

    private void clearBridgeWool() {
        if (bridgeZoneMin == null || bridgeZoneMax == null) return;
        World w = bridgeZoneMin.getWorld();
        int minX = Math.min(bridgeZoneMin.getBlockX(), bridgeZoneMax.getBlockX());
        int maxX = Math.max(bridgeZoneMin.getBlockX(), bridgeZoneMax.getBlockX());
        int minY = Math.min(bridgeZoneMin.getBlockY(), bridgeZoneMax.getBlockY());
        int maxY = Math.max(bridgeZoneMin.getBlockY(), bridgeZoneMax.getBlockY());
        int minZ = Math.min(bridgeZoneMin.getBlockZ(), bridgeZoneMax.getBlockZ());
        int maxZ = Math.max(bridgeZoneMin.getBlockZ(), bridgeZoneMax.getBlockZ());

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (w.getBlockAt(x, y, z).getType().name().contains("WOOL")) {
                        w.getBlockAt(x, y, z).setType(Material.AIR);
                    }
                }
            }
        }
    }

    public void endGame() {
        gameState = GameState.WAITING;
        if (matchTimerTask != null) matchTimerTask.cancel();
        if (countdownTimer != null) countdownTimer.cancel();

        for (Player p : getAllPlayers()) {
            if (lobbySpawn != null) p.teleport(lobbySpawn);
            p.getInventory().clear();
            giveLobbyItems(p);
        }

        redTeam.clear();
        blueTeam.clear();
        playerTeam.clear();
        playerKills.clear();
        updateScoreboard();
    }

    // ==================== LOBBY LOGIC ====================

    public void joinGame(Player player) {
        if (gameState != GameState.WAITING && gameState != GameState.COUNTDOWN) {
            player.sendMessage("§cGame in progress!");
            return;
        }

        if (playerTeam.containsKey(player)) return;

        Team team = (redTeam.size() <= blueTeam.size()) ? Team.RED : Team.BLUE;
        if (team == Team.RED) redTeam.add(player); else blueTeam.add(player);

        playerTeam.put(player, team);
        playerKills.put(player, 0);

        if (lobbySpawn != null) player.teleport(lobbySpawn);
        giveLobbyItems(player);
        updateScoreboard();

        Bukkit.broadcastMessage("§a" + player.getName() + " joined! (" + getTotalPlayers() + "/min 2)");
        checkStartGame();
    }

    public void leaveGame(Player p) {
        if (playerTeam.get(p) == Team.RED) redTeam.remove(p);
        else blueTeam.remove(p);

        playerTeam.remove(p);
        playerKills.remove(p);
        bowLevels.remove(p.getUniqueId());

        p.getInventory().clear();
        if (lobbySpawn != null) p.teleport(lobbySpawn);
        giveLobbyItems(p);
        updateScoreboard();

        if (gameState == GameState.ACTIVE && (redTeam.isEmpty() || blueTeam.isEmpty())) {
            endGame();
        } else {
            checkStartGame();
        }
    }

    public void checkStartGame() {
        if (getTotalPlayers() >= 2 && gameState == GameState.WAITING) {
            startCountdown();
        } else if (getTotalPlayers() < 2 && gameState == GameState.COUNTDOWN) {
            cancelCountdown();
        }
    }

    private void startCountdown() {
        gameState = GameState.COUNTDOWN;
        countdownSeconds = 10;

        if (countdownTimer != null) countdownTimer.cancel();

        countdownTimer = new BukkitRunnable() {
            @Override
            public void run() {
                if (getTotalPlayers() < 2) {
                    cancelCountdown();
                    this.cancel();
                    return;
                }
                if (countdownSeconds <= 0) {
                    startGame();
                    this.cancel();
                    return;
                }
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.sendActionBar("§eStarting in §c" + countdownSeconds + "§es...");
                    if (countdownSeconds <= 3) {
                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 2f);
                    }
                }
                countdownSeconds--;
                updateScoreboard();
            }
        };
        countdownTimer.runTaskTimer(this, 0L, 20L);
    }

    private void cancelCountdown() {
        if (countdownTimer != null) countdownTimer.cancel();
        gameState = GameState.WAITING;
        Bukkit.broadcastMessage("§cStart cancelled - need at least 2 players.");
        updateScoreboard();
    }

    // ==================== INVENTORIES ====================

    public void giveGameItems(Player p) {
        p.getInventory().clear();
        p.getInventory().setArmorContents(null);
        p.getInventory().setHelmet(new ItemStack(getPlayerTeam(p) == Team.RED ? Material.RED_WOOL : Material.BLUE_WOOL));
    }

    public void giveLobbyItems(Player p) {
        p.getInventory().clear();
        p.getInventory().setArmorContents(null);
        ItemStack join = new ItemStack(Material.NETHER_STAR);
        ItemMeta m = join.getItemMeta();
        m.setDisplayName("§6Join Match");
        join.setItemMeta(m);
        p.getInventory().setItem(0, join);
    }

    // ==================== COMMAND HANDLER ====================

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;
        if (args.length == 0 || command.getName().equalsIgnoreCase("b")) {
            openGameMenu(p);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "join" -> openGameMenu(p);
            case "leave" -> leaveGame(p);
            case "start" -> { if (p.hasPermission("bridge.admin")) startGame(); }
            case "stop" -> { if (p.hasPermission("bridge.admin")) endGame(); }
            case "setspawn" -> {
                if (!p.hasPermission("bridge.admin")) return true;
                if (args.length < 2) { p.sendMessage("§cUsage: /bridge setspawn <lobby|red|blue>"); return true; }
                setSpawn(args[1], p.getLocation());
                p.sendMessage("§aSpawn " + args[1] + " set!");
            }
            case "setgen" -> {
                if (!p.hasPermission("bridge.admin")) return true;
                if (args.length < 2) { p.sendMessage("§cUsage: /bridge setgen <gold|diamond|emerald>"); return true; }
                setupGenerator(p, args[1].toLowerCase());
            }
            case "removegen" -> {
                if (!p.hasPermission("bridge.admin")) return true;
                removeNearestGenerator(p);
            }
            case "setbridgezone" -> {
                if (!p.hasPermission("bridge.admin")) return true;
                if (args.length < 2) { p.sendMessage("§cUsage: /bridge setbridgezone <min|max>"); return true; }
                setBridgeZone(p, args[1].toLowerCase());
            }
            case "setchest" -> {
                if (!p.hasPermission("bridge.admin")) return true;
                if (args.length < 2) { p.sendMessage("§cUsage: /bridge setchest <1|2|3>"); return true; }
                org.bukkit.block.Block target = p.getTargetBlockExact(5);
                if (target == null || target.getType() != Material.CHEST) {
                    p.sendMessage("§cYou must be looking at a Chest!");
                    return true;
                }
                setGameChest(Integer.parseInt(args[1]), target.getLocation());
                p.sendMessage("§aChest goal " + args[1] + " set!");
            }
            case "spawnshop" -> {
                if (!p.hasPermission("bridge.admin")) return true;
                Villager v = p.getWorld().spawn(p.getLocation(), Villager.class);
                v.setCustomName("§6§lBATTLE SHOP");
                v.setCustomNameVisible(true);
                v.setAI(false);
                v.setInvulnerable(true);
                v.setSilent(true);
                p.sendMessage("§aShop spawned.");
            }
            case "removeshop" -> {
                if (!p.hasPermission("bridge.admin")) return true;
                p.getNearbyEntities(5, 5, 5).stream()
                        .filter(e -> e instanceof Villager && e.getCustomName() != null && e.getCustomName().contains("SHOP"))
                        .forEach(Entity::remove);
                p.sendMessage("§cShops removed.");
            }
            default -> p.sendMessage("§cUnknown command.");
        }
        return true;
    }

    public void openGameMenu(Player p) {
        if (gameMenu == null) gameMenu = new BridgeBattleGUI(this);
        p.openInventory(gameMenu.getInventory());
    }

    // ==================== SCOREBOARD ====================

    public void updateScoreboard() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            updateScoreboard(p);
        }
    }

    public void updateScoreboard(Player player) {
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective obj = board.registerNewObjective("bridge", "dummy", "§6§l⚔ BRIDGE BATTLE ⚔");
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        obj.getScore("§fRound: §b" + (gameState == GameState.WAITING ? "0" : currentRound) + "/2").setScore(10);
        obj.getScore("§fTime: §e" + formatMatchTime(matchSeconds)).setScore(9);
        obj.getScore("§7 ").setScore(8);
        obj.getScore("§cRed Score: §f" + redScore).setScore(7);
        obj.getScore("§bBlue Score: §f" + blueScore).setScore(6);

        String status = switch (gameState) {
            case WAITING -> "§eWaiting...";
            case COUNTDOWN -> "§6Start: " + countdownSeconds + "s";
            case ACTIVE -> "§a§lLIVE!";
            default -> "§7---";
        };
        obj.getScore("§8 ").setScore(5);
        obj.getScore("§fStatus: " + status).setScore(4);
        obj.getScore("§9 ").setScore(3);
        obj.getScore("§eplay.unknownrealm.com").setScore(2);

        player.setScoreboard(board);
    }

    // ==================== CONFIG LOGIC ====================

    private void loadSpawns() {
        lobbySpawn = loadLoc("lobby");
        redSpawn = loadLoc("red");
        blueSpawn = loadLoc("blue");
    }

    private Location loadLoc(String path) {
        FileConfiguration c = getConfig();
        if (!c.contains("spawns." + path)) return null;
        World w = Bukkit.getWorld(c.getString("spawns." + path + ".world"));
        if (w == null) return null;
        return new Location(w, c.getDouble("spawns."+path+".x"), c.getDouble("spawns."+path+".y"), c.getDouble("spawns."+path+".z"));
    }

    public void setSpawn(String type, Location loc) {
        FileConfiguration c = getConfig();
        c.set("spawns."+type+".world", loc.getWorld().getName());
        c.set("spawns."+type+".x", loc.getX());
        c.set("spawns."+type+".y", loc.getY());
        c.set("spawns."+type+".z", loc.getZ());
        saveConfig();
        loadSpawns();
    }

    private void loadBridgeZone() {
        FileConfiguration c = getConfig();
        if (!c.contains("bridge_zone")) return;
        World w = Bukkit.getWorld(c.getString("bridge_zone.world"));
        if (w == null) return;
        bridgeZoneMin = new Location(w, c.getDouble("bridge_zone.min.x"), c.getDouble("bridge_zone.min.y"), c.getDouble("bridge_zone.min.z"));
        bridgeZoneMax = new Location(w, c.getDouble("bridge_zone.max.x"), c.getDouble("bridge_zone.max.y"), c.getDouble("bridge_zone.max.z"));
    }

    private void setBridgeZone(Player p, String type) {
        FileConfiguration c = getConfig();
        c.set("bridge_zone.world", p.getWorld().getName());
        c.set("bridge_zone."+type+".x", p.getLocation().getX());
        c.set("bridge_zone."+type+".y", p.getLocation().getY());
        c.set("bridge_zone."+type+".z", p.getLocation().getZ());
        saveConfig();
        loadBridgeZone();
        p.sendMessage("§aBridge zone " + type + " set!");
    }

    private void loadChests() {
        chestLocations.clear();
        for (int i = 1; i <= 3; i++) {
            if (getConfig().contains("chests.c" + i)) {
                World w = Bukkit.getWorld(getConfig().getString("chests.c" + i + ".world"));
                if (w != null) {
                    chestLocations.add(new Location(w, getConfig().getDouble("chests.c"+i+".x"), getConfig().getDouble("chests.c"+i+".y"), getConfig().getDouble("chests.c"+i+".z")));
                }
            }
        }
    }

    public void setGameChest(int id, Location loc) {
        getConfig().set("chests.c"+id+".world", loc.getWorld().getName());
        getConfig().set("chests.c"+id+".x", loc.getBlockX());
        getConfig().set("chests.c"+id+".y", loc.getBlockY());
        getConfig().set("chests.c"+id+".z", loc.getBlockZ());
        saveConfig();
        loadChests();
    }

    // ==================== GENERATORS & HOLOGRAMS ====================

    private void loadGenerators() {
        goldGens = parseGen("gold");
        diamondGens = parseGen("diamond");
        emeraldGens = parseGen("emerald");
    }

    private List<Location> parseGen(String type) {
        List<Location> locs = new ArrayList<>();
        for (String s : getConfig().getStringList("generators." + type)) {
            String[] p = s.split(",");
            World w = Bukkit.getWorld(p[0]);
            if (w != null) locs.add(new Location(w, Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3])));
        }
        return locs;
    }

    private void setupGenerator(Player p, String type) {
        Location loc = p.getLocation().getBlock().getLocation();
        List<String> list = getConfig().getStringList("generators." + type);
        list.add(loc.getWorld().getName() + "," + loc.getX() + "," + loc.getY() + "," + loc.getZ());
        getConfig().set("generators." + type, list);
        saveConfig();
        loadGenerators();
        refreshHolograms();
        p.sendMessage("§a" + type.toUpperCase() + " generator set!");
    }

    public void removeNearestGenerator(Player p) {
        Location pLoc = p.getLocation();
        Location toRem = null;
        String type = "";
        double best = 3.0;

        for (String t : Arrays.asList("gold", "diamond", "emerald")) {
            for (Location l : parseGen(t)) {
                if (l.distance(pLoc) < best) {
                    best = l.distance(pLoc);
                    toRem = l;
                    type = t;
                }
            }
        }

        if (toRem != null) {
            List<String> list = getConfig().getStringList("generators." + type);
            list.remove(toRem.getWorld().getName() + "," + toRem.getX() + "," + toRem.getY() + "," + toRem.getZ());
            getConfig().set("generators." + type, list);
            saveConfig();
            loadGenerators();
            refreshHolograms();
            p.sendMessage("§cRemoved nearest " + type.toUpperCase() + " generator.");
        } else {
            p.sendMessage("§cNo generator found nearby.");
        }
    }

    public void refreshHolograms() {
        for (ArmorStand holo : genHolograms.values()) holo.remove();
        genHolograms.clear();
        for (Location loc : goldGens) createGenHologram(loc, Material.GOLD_INGOT, "§6§lGOLD");
        for (Location loc : diamondGens) createGenHologram(loc, Material.DIAMOND, "§b§lDIAMOND");
        for (Location loc : emeraldGens) createGenHologram(loc, Material.EMERALD, "§a§lEMERALD");
    }

    public void createGenHologram(Location loc, Material mat, String name) {
        ArmorStand holo = loc.getWorld().spawn(loc.clone().add(0.5, 1.2, 0.5), ArmorStand.class);
        holo.setVisible(false);
        holo.setMarker(true);
        holo.setGravity(false);
        holo.setCustomNameVisible(true);
        if (holo.getEquipment() != null) holo.getEquipment().setHelmet(new ItemStack(mat));
        genHolograms.put(loc, holo);
    }

    public void startGenerators() {
        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (gameState != GameState.ACTIVE) return;
                ticks += 20;

                // Gold (1s), Diamond (30s), Emerald (60s)
                spawnAt(goldGens, Material.GOLD_INGOT, ticks, 20);
                spawnAt(diamondGens, Material.DIAMOND, ticks, 600);
                spawnAt(emeraldGens, Material.EMERALD, ticks, 1200);
            }
        }.runTaskTimer(this, 0L, 20L);
    }

    private void spawnAt(List<Location> locs, Material mat, int ticks, int interval) {
        if (ticks % interval != 0) return;
        for (Location loc : locs) {
            loc.getWorld().dropItemNaturally(loc.clone().add(0.5, 1, 0.5), new ItemStack(mat));
        }
    }
}