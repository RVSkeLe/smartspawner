package github.nighter.smartspawner.spawner.lootgen;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.spawner.data.SpawnerManager;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import github.nighter.smartspawner.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SpawnerRangeChecker {
    private static final long CHECK_INTERVAL = 20L; // 1 second in ticks
    private final SmartSpawner plugin;
    private final SpawnerManager spawnerManager;
    private final SpawnerLootGenerator spawnerLootGenerator;
    private final Map<String, Scheduler.Task> spawnerTasks;
    private final ExecutorService executor;

    public SpawnerRangeChecker(SmartSpawner plugin) {
        this.plugin = plugin;
        this.spawnerManager = plugin.getSpawnerManager();
        this.spawnerLootGenerator = plugin.getSpawnerLootGenerator();
        this.spawnerTasks = new ConcurrentHashMap<>();
        this.executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "SmartSpawner-RangeCheck"));
        initializeRangeCheckTask();
    }

    private void initializeRangeCheckTask() {
        // Using the global scheduler, but only for coordinating region-specific checks
        Scheduler.runTaskTimer(this::scheduleRegionSpecificCheck, CHECK_INTERVAL, CHECK_INTERVAL);
    }

    private void scheduleRegionSpecificCheck() {
        PlayerRangeWrapper[] rangePlayers = getRangePlayers();

        this.executor.execute(() -> {
            final List<SpawnerData> allSpawners = spawnerManager.getAllSpawners();

            final RangeMath rangeCheck = new RangeMath(rangePlayers, allSpawners);
            final boolean[] spawnersPlayerFound = rangeCheck.getActiveSpawners();

            for (int i = 0; i < spawnersPlayerFound.length; i++) {
                final boolean expectedStop = !spawnersPlayerFound[i];
                final SpawnerData sd = allSpawners.get(i);
                final String spawnerId = sd.getSpawnerId();

                // Atomically update spawner stop flag only if it has changed
                if (sd.getSpawnerStop().compareAndSet(!expectedStop, expectedStop)) {
                    // Schedule main-thread task for actual state change
                    Scheduler.runLocationTask(sd.getSpawnerLocation(), () -> {
                        if (!isSpawnerValid(sd)) {
                            cleanupRemovedSpawner(spawnerId);
                            return;
                        }

                        // Double-check atomic boolean before applying
                        if (sd.getSpawnerStop().get() == expectedStop) {
                            handleSpawnerStateChange(sd, expectedStop);
                        }
                    });
                }
            }
        });
    }

    private PlayerRangeWrapper[] getRangePlayers() {
        final Collection<? extends Player> onlinePlayers = Bukkit.getOnlinePlayers();
        final PlayerRangeWrapper[] rangePlayers = new PlayerRangeWrapper[onlinePlayers.size()];
        int i = 0;

        for (Player p : onlinePlayers) {

            boolean conditions = p.isConnected() && !p.isDead()
                    && p.getGameMode() != GameMode.SPECTATOR;

            // Store data in wrapper for faster access
            rangePlayers[i++] = new PlayerRangeWrapper(p.getWorld().getUID(),
                    p.getX(), p.getY(), p.getZ(),
                    conditions
            );
        }

        return rangePlayers;
    }

    private boolean isSpawnerValid(SpawnerData spawner) {
        // Check 1: Still in manager?
        SpawnerData current = spawnerManager.getSpawnerById(spawner.getSpawnerId());
        if (current == null) {
            return false;
        }

        // Check 2: Same instance? (prevents processing stale copies)
        if (current != spawner) {
            return false;
        }

        // Check 3: Location still valid?
        Location loc = spawner.getSpawnerLocation();
        return loc != null && loc.getWorld() != null;
    }

    private void cleanupRemovedSpawner(String spawnerId) {
        Scheduler.Task task = spawnerTasks.remove(spawnerId);
        if (task != null) {
            task.cancel();
        }
    }

    private void handleSpawnerStateChange(SpawnerData spawner, boolean shouldStop) {
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
        deactivateSpawner(spawner);
        plugin.getLogger().info("Activating spawner " + spawner.getSpawnerId() + " for loot spawning.");

        // Set lastSpawnTime to current time to start countdown immediately
        // This ensures timer shows full delay countdown when spawner activates
        long currentTime = System.currentTimeMillis();
        spawner.setLastSpawnTime(currentTime);

        // Timer doesn't spawn loot directly - it just runs periodically to keep the spawner active
        // SpawnerGuiViewManager will check the timer and trigger spawns via SpawnerLootGenerator
        Scheduler.Task task = Scheduler.runTaskTimer(() -> {
            // Task just keeps the spawner active - actual spawning logic moved to GUI manager
            if (spawner.getSpawnerStop().get()) {
                plugin.getLogger().info("Spawner " + spawner.getSpawnerId() + " is stopped, timer running but no spawn");
            }
        }, spawner.getSpawnDelay(), spawner.getSpawnDelay());

        spawnerTasks.put(spawner.getSpawnerId(), task);

        // Immediately update any open GUIs to show the countdown
        if (plugin.getSpawnerGuiViewManager().hasViewers(spawner)) {
            plugin.getSpawnerGuiViewManager().updateSpawnerMenuViewers(spawner);
        }
    }

    public void deactivateSpawner(SpawnerData spawner) {
        Scheduler.Task task = spawnerTasks.remove(spawner.getSpawnerId());
        if (task != null) {
            task.cancel();
        }
    }

    public void cleanup() {
        spawnerTasks.values().forEach(Scheduler.Task::cancel);
        spawnerTasks.clear();

        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
