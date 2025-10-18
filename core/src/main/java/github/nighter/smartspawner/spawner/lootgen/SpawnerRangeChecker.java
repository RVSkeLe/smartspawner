package github.nighter.smartspawner.spawner.lootgen;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.spawner.properties.SpawnerManager;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import github.nighter.smartspawner.Scheduler;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SpawnerRangeChecker {
    private static final long CHECK_INTERVAL = 20L; // 1 second in ticks
    private final SmartSpawner plugin;
    private final SpawnerManager spawnerManager;
    private final SpawnerLootGenerator spawnerLootGenerator;
    private final Map<String, Scheduler.Task> spawnerTasks;

    public SpawnerRangeChecker(SmartSpawner plugin) {
        this.plugin = plugin;
        this.spawnerManager = plugin.getSpawnerManager();
        this.spawnerLootGenerator = plugin.getSpawnerLootGenerator();
        this.spawnerTasks = new ConcurrentHashMap<>();
        initializeRangeCheckTask();
    }

    private void initializeRangeCheckTask() {
        // Using the global scheduler, but only for coordinating region-specific checks
        Scheduler.runTaskTimer(() ->
                        spawnerManager.getAllSpawners().forEach(this::scheduleRegionSpecificCheck),
                CHECK_INTERVAL, CHECK_INTERVAL);
    }

    private void scheduleRegionSpecificCheck(SpawnerData spawner) {
        Location spawnerLoc = spawner.getSpawnerLocation();
        World world = spawnerLoc.getWorld();
        if (world == null) return;

        // Schedule the actual entity checking in the correct region
        Scheduler.runLocationTask(spawnerLoc, () -> {
            boolean playerFound = isPlayerInRange(spawner, spawnerLoc, world);
            boolean shouldStop = !playerFound;

            if (spawner.getSpawnerStop().get() != shouldStop) {
                spawner.getSpawnerStop().set(shouldStop);
                handleSpawnerStateChange(spawner, shouldStop);
            }
        });
    }

    private boolean isPlayerInRange(SpawnerData spawner, Location spawnerLoc, World world) {
        int range = spawner.getSpawnerRange();
        double rangeSquared = range * range;

        // In Folia, we're now running this in the correct region thread,
        // so we can safely check for nearby entities
        Collection<Player> nearbyPlayers = world.getNearbyPlayers(spawnerLoc, range, range, range);

        for (Player player : nearbyPlayers) {
            if (player.getLocation().distanceSquared(spawnerLoc) <= rangeSquared) {
                return true;
            }
        }
        return false;
    }

    private void handleSpawnerStateChange(SpawnerData spawner, boolean shouldStop) {
        if (spawnerManager.isGhostSpawner(spawner)) {
            spawnerManager.removeGhostSpawner(spawner.getSpawnerId());
            return;
        }

        if (!shouldStop) {
            activateSpawner(spawner);
        } else {
            deactivateSpawner(spawner);
        }

        // Force GUI update when spawner state changes
        if (plugin.getSpawnerGuiViewManager().hasViewers(spawner)) {
            plugin.getSpawnerGuiViewManager().forceStateChangeUpdate(spawner);
        }
    }

    public void activateSpawner(SpawnerData spawner) {
        startSpawnerTask(spawner);
        //plugin.debug("Spawner " + spawner.getSpawnerId() + " activated - Player in range");
    }

    private void deactivateSpawner(SpawnerData spawner) {
        stopSpawnerTask(spawner);
        //plugin.debug("Spawner " + spawner.getSpawnerId() + " deactivated - No players in range");
    }

    private void startSpawnerTask(SpawnerData spawner) {
        stopSpawnerTask(spawner);

        // Set lastSpawnTime to current time to start countdown immediately
        // This ensures timer shows full delay countdown when spawner activates
        long currentTime = System.currentTimeMillis();
        spawner.setLastSpawnTime(currentTime);

        Scheduler.Task task = Scheduler.runTaskTimer(() -> {
            if (!spawner.getSpawnerStop().get()) {
                spawnerLootGenerator.spawnLootToSpawner(spawner);
            }
        }, spawner.getSpawnDelay(), spawner.getSpawnDelay()); // Start after one delay period

        spawnerTasks.put(spawner.getSpawnerId(), task);

        // Immediately update any open GUIs to show the countdown
        if (plugin.getSpawnerGuiViewManager().hasViewers(spawner)) {
            plugin.getSpawnerGuiViewManager().updateSpawnerMenuViewers(spawner);
        }
    }

    public void stopSpawnerTask(SpawnerData spawner) {
        Scheduler.Task task = spawnerTasks.remove(spawner.getSpawnerId());
        if (task != null) {
            task.cancel();
        }
    }

    public void cleanup() {
        spawnerTasks.values().forEach(Scheduler.Task::cancel);
        spawnerTasks.clear();
    }
}