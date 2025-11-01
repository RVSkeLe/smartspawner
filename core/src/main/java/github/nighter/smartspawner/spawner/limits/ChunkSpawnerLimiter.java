package github.nighter.smartspawner.spawner.limits;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import github.nighter.smartspawner.spawner.data.SpawnerManager;
import lombok.Getter;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public class ChunkSpawnerLimiter {
    private final SmartSpawner plugin;
    private final SpawnerManager spawnerManager;
    private final Map<String, Integer> chunkSpawnerCount;

    @Getter
    private int maxSpawnersPerChunk;
    @Getter
    private boolean limitsEnabled;

    public ChunkSpawnerLimiter(SmartSpawner plugin) {
        this.plugin = plugin;
        this.spawnerManager = plugin.getSpawnerManager();
        this.chunkSpawnerCount = new ConcurrentHashMap<>();

        // Load configuration
        this.maxSpawnersPerChunk = plugin.getConfig().getInt("spawner_limits.max_per_chunk", -1);
        this.limitsEnabled = maxSpawnersPerChunk > 0;

        // Initialize chunk counts if limits are enabled
        if (limitsEnabled) {
            initializeChunkCounts();
        }
    }

    private void initializeChunkCounts() {
        chunkSpawnerCount.clear();

        for (SpawnerData spawner : spawnerManager.getAllSpawners()) {
            Location location = spawner.getSpawnerLocation();
            if (location != null && location.getWorld() != null) {
                String chunkKey = getChunkKey(location);
                int stackSize = spawner.getStackSize();
                chunkSpawnerCount.merge(chunkKey, stackSize, Integer::sum);
            }
        }

        plugin.debug("Initialized chunk spawner counts for " + chunkSpawnerCount.size() + " chunks");
    }

    public boolean canPlaceSpawner(Player player, Location location) {
        if (!limitsEnabled) {
            return true;
        }

        // Check bypass permission
        if (player.hasPermission("smartspawner.limits.bypass")) {
            return true;
        }

        String chunkKey = getChunkKey(location);
        int currentCount = chunkSpawnerCount.getOrDefault(chunkKey, 0);

        return currentCount < maxSpawnersPerChunk;
    }

    public boolean canStackSpawner(Player player, Location location, int stackAmount) {
        if (!limitsEnabled) {
            return true;
        }

        // Check bypass permission
        if (player.hasPermission("smartspawner.limits.bypass")) {
            return true;
        }

        String chunkKey = getChunkKey(location);
        int currentCount = chunkSpawnerCount.getOrDefault(chunkKey, 0);

        return (currentCount + stackAmount) <= maxSpawnersPerChunk;
    }

    public void registerSpawnerPlacement(Location location, int stackSize) {
        if (!limitsEnabled) {
            return;
        }

        String chunkKey = getChunkKey(location);
        chunkSpawnerCount.merge(chunkKey, stackSize, Integer::sum);

//        plugin.debug("Registered spawner placement in chunk " + chunkKey +
//                " (stack: " + stackSize + ", total: " + chunkSpawnerCount.get(chunkKey) + ")");
    }

    public void registerSpawnerStack(Location location, int stackIncrease) {
        if (!limitsEnabled) {
            return;
        }

        String chunkKey = getChunkKey(location);
        chunkSpawnerCount.merge(chunkKey, stackIncrease, Integer::sum);

//        plugin.debug("Registered spawner stack in chunk " + chunkKey +
//                " (increase: " + stackIncrease + ", total: " + chunkSpawnerCount.get(chunkKey) + ")");
    }

    public void unregisterSpawner(Location location, int stackSize) {
        if (!limitsEnabled) {
            return;
        }

        String chunkKey = getChunkKey(location);
        int currentCount = chunkSpawnerCount.getOrDefault(chunkKey, 0);
        int newCount = Math.max(0, currentCount - stackSize);

        if (newCount == 0) {
            chunkSpawnerCount.remove(chunkKey);
        } else {
            chunkSpawnerCount.put(chunkKey, newCount);
        }

//        plugin.debug("Unregistered spawner from chunk " + chunkKey +
//                " (removed: " + stackSize + ", remaining: " + newCount + ")");
    }

    public int getChunkSpawnerCount(Location location) {
        if (!limitsEnabled) {
            return 0;
        }

        String chunkKey = getChunkKey(location);
        return chunkSpawnerCount.getOrDefault(chunkKey, 0);
    }

    private String getChunkKey(Location location) {
        if (location.getWorld() == null) {
            throw new IllegalArgumentException("Location world cannot be null");
        }

        Chunk chunk = location.getChunk();
        return location.getWorld().getName() + ":" + chunk.getX() + ":" + chunk.getZ();
    }

    public void reloadConfig() {
        // Load configuration
        this.maxSpawnersPerChunk = plugin.getConfig().getInt("spawner_limits.max_per_chunk", -1);
        this.limitsEnabled = maxSpawnersPerChunk > 0;

        if (limitsEnabled) {
            initializeChunkCounts();
        }
    }

    public String getDebugInfo(Location location) {
        if (!limitsEnabled) {
            return "Chunk limits disabled";
        }

        String chunkKey = getChunkKey(location);
        int currentCount = chunkSpawnerCount.getOrDefault(chunkKey, 0);

        return String.format("Chunk %s: %d/%d spawners",
                chunkKey, currentCount, maxSpawnersPerChunk);
    }
}