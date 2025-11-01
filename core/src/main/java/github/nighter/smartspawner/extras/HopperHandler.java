package github.nighter.smartspawner.extras;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.Scheduler;
import github.nighter.smartspawner.spawner.gui.synchronization.SpawnerGuiViewManager;
import github.nighter.smartspawner.spawner.data.SpawnerManager;
import github.nighter.smartspawner.spawner.properties.VirtualInventory;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import org.bukkit.*;
import org.bukkit.block.BlockState;
import org.bukkit.block.Hopper;
import org.bukkit.event.Listener;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;

public class HopperHandler implements Listener {
    private final SmartSpawner plugin;
    private final Map<Location, Scheduler.Task> activeHoppers = new ConcurrentHashMap<>();
    private final SpawnerManager spawnerManager;
    private final SpawnerGuiViewManager spawnerGuiViewManager;

    public HopperHandler(SmartSpawner plugin) {
        this.plugin = plugin;
        this.spawnerManager = plugin.getSpawnerManager();
        this.spawnerGuiViewManager = plugin.getSpawnerGuiViewManager();

        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        // Delay the initialization to ensure server is fully loaded
        Scheduler.runTaskLater(() -> {
            if (plugin.getConfig().getBoolean("hopper.enabled", false)) {
                restartAllHoppers();
            }
        }, 40L);
    }

    public void restartAllHoppers() {
        if (!plugin.getConfig().getBoolean("hopper.enabled", false)) return;

        // For each world, we'll schedule a world-specific task
        for (World world : plugin.getServer().getWorlds()) {
            try {
                // Create a location in this world to schedule a region task
                // Using spawn location as it's guaranteed to exist
                Location worldLocation = world.getSpawnLocation();

                Scheduler.runLocationTask(worldLocation, () -> {
                    try {
                        // Now we're on the correct thread for this world region
                        for (Chunk chunk : world.getLoadedChunks()) {
                            processChunkHoppers(chunk);
                        }
                    } catch (Exception e) {
                        plugin.getLogger().log(Level.SEVERE, "Error processing hoppers in world " + world.getName(), e);
                    }
                });
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Error scheduling hopper task for world " + world.getName(), e);
            }
        }
    }

    private void processChunkHoppers(Chunk chunk) {
        if (chunk == null || !chunk.isLoaded()) return;

        Location chunkLoc = new Location(chunk.getWorld(),
                chunk.getX() * 16 + 8, 64, chunk.getZ() * 16 + 8);

        Scheduler.runLocationTask(chunkLoc, () -> {
            try {
                BlockState[] hopperStates = chunk.getTileEntities(block -> block.getType() == Material.HOPPER, false);
                if (hopperStates == null || hopperStates.length == 0) return;

                for (BlockState state : hopperStates) {
                    if (state == null) continue;

                    Block hopperBlock = state.getBlock();
                    if (hopperBlock.getType() != Material.HOPPER) continue;

                    Block aboveBlock = hopperBlock.getRelative(BlockFace.UP);
                    if (aboveBlock.getType() == Material.SPAWNER) {
                        startHopperTask(hopperBlock.getLocation(), aboveBlock.getLocation());
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Error processing hoppers in chunk at " +
                        chunk.getX() + "," + chunk.getZ(), e);
            }
        });
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!plugin.getConfig().getBoolean("hopper.enabled", false)) return;

        Chunk chunk = event.getChunk();
        processChunkHoppers(chunk);
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        if (!plugin.getConfig().getBoolean("hopper.enabled", false)) return;

        Chunk chunk = event.getChunk();
        if (chunk == null) return;

        try {
            BlockState[] hopperStates = chunk.getTileEntities(block -> block.getType() == Material.HOPPER, false);
            if (hopperStates == null || hopperStates.length == 0) return;

            for (BlockState state : hopperStates) {
                if (state == null) continue;
                stopHopperTask(state.getLocation());
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error stopping hoppers in unloading chunk at " +
                    chunk.getX() + "," + chunk.getZ(), e);
        }
    }

    public void cleanup() {
        try {
            List<Scheduler.Task> tasks = new ArrayList<>(activeHoppers.values());
            activeHoppers.clear();
            
            for (Scheduler.Task task : tasks) {
                if (task != null) {
                    try {
                        task.cancel();
                    } catch (Exception e) {
                        plugin.getLogger().log(Level.WARNING, "Error cancelling hopper task during cleanup", e);
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error during hopper handler cleanup", e);
        }
    }

    @EventHandler
    public void onHopperPlace(BlockPlaceEvent event) {
        if (!plugin.getConfig().getBoolean("hopper.enabled", false)) return;
        if (event.getBlockPlaced().getType() != Material.HOPPER) return;

        Block above = event.getBlockPlaced().getRelative(BlockFace.UP);
        if (above.getType() == Material.SPAWNER) {
            startHopperTask(event.getBlockPlaced().getLocation(), above.getLocation());
        }
    }

    @EventHandler
    public void onHopperBreak(BlockBreakEvent event) {
        if (event.getBlock().getType() == Material.HOPPER) {
            stopHopperTask(event.getBlock().getLocation());
        }
    }

    public void startHopperTask(Location hopperLoc, Location spawnerLoc) {
        if (!plugin.getConfig().getBoolean("hopper.enabled", false)) return;
        if (hopperLoc == null || spawnerLoc == null) return;

        stopHopperTask(hopperLoc);

        Runnable hopperRunnable = () -> {
            try {
                if (!isValidSetup(hopperLoc, spawnerLoc)) {
                    stopHopperTask(hopperLoc);
                    return;
                }
                transferItems(hopperLoc, spawnerLoc);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Error in hopper task at " + hopperLoc, e);
            }
        };

        try {
            Scheduler.Task task = Scheduler.runLocationTaskTimer(
                    hopperLoc,
                    hopperRunnable,
                    0L,
                    plugin.getTimeFromConfig("hopper.check_delay", "3s")
            );

            if (task != null && !task.isCancelled()) {
                activeHoppers.put(hopperLoc, task);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to start hopper task at " + hopperLoc, e);
        }
    }

    private boolean isValidSetup(Location hopperLoc, Location spawnerLoc) {
        Block hopper = hopperLoc.getBlock();
        Block spawner = spawnerLoc.getBlock();

        return hopper.getType() == Material.HOPPER &&
                spawner.getType() == Material.SPAWNER &&
                hopper.getRelative(BlockFace.UP).equals(spawner);
    }

    public void stopHopperTask(Location hopperLoc) {
        if (hopperLoc == null) return;
        
        Scheduler.Task task = activeHoppers.remove(hopperLoc);
        if (task != null) {
            try {
                task.cancel();
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Error cancelling hopper task at " + hopperLoc, e);
            }
        }
    }

    public void restartHopperForSpawner(Location spawnerLoc) {
        if (!plugin.getConfig().getBoolean("hopper.enabled", false)) return;
        if (spawnerLoc == null) return;

        Scheduler.runLocationTask(spawnerLoc, () -> {
            try {
                Block spawnerBlock = spawnerLoc.getBlock();
                if (spawnerBlock.getType() != Material.SPAWNER) return;

                Block hopperBlock = spawnerBlock.getRelative(BlockFace.DOWN);
                if (hopperBlock.getType() != Material.HOPPER) return;

                Location hopperLoc = hopperBlock.getLocation();

                startHopperTask(hopperLoc, spawnerLoc);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Error restarting hopper for spawner at " + spawnerLoc, e);
            }
        });
    }

    private void transferItems(Location hopperLoc, Location spawnerLoc) {
        if (hopperLoc == null || spawnerLoc == null) return;

        SpawnerData spawner = spawnerManager.getSpawnerByLocation(spawnerLoc);
        if (spawner == null) return;

        ReentrantLock lock = spawner.getInventoryLock();
        if (!lock.tryLock()) return;

        try {
            Block hopperBlock = hopperLoc.getBlock();
            if (hopperBlock.getType() != Material.HOPPER) {
                stopHopperTask(hopperLoc);
                return;
            }

            VirtualInventory virtualInv = spawner.getVirtualInventory();
            if (virtualInv == null) return;

            Hopper hopper = (Hopper) hopperBlock.getState(false);
            if (hopper == null) return;

            int itemsPerTransfer = plugin.getConfig().getInt("hopper.stack_per_transfer", 5);
            int transferred = 0;
            boolean inventoryChanged = false;

            Map<Integer, ItemStack> displayItems = virtualInv.getDisplayInventory();
            if (displayItems == null || displayItems.isEmpty()) return;

            List<ItemStack> itemsToRemove = new ArrayList<>();

            for (Map.Entry<Integer, ItemStack> entry : displayItems.entrySet()) {
                if (transferred >= itemsPerTransfer) break;

                ItemStack item = entry.getValue();
                if (item == null || item.getType() == Material.AIR) continue;

                ItemStack[] hopperContents = hopper.getInventory().getContents();
                for (int i = 0; i < hopperContents.length; i++) {
                    if (transferred >= itemsPerTransfer) break;

                    ItemStack hopperItem = hopperContents[i];
                    if (hopperItem == null || hopperItem.getType() == Material.AIR) {
                        hopper.getInventory().setItem(i, item.clone());
                        itemsToRemove.add(item);
                        transferred++;
                        inventoryChanged = true;
                        break;
                    } else if (hopperItem.isSimilar(item) &&
                            hopperItem.getAmount() < hopperItem.getMaxStackSize()) {
                        int space = hopperItem.getMaxStackSize() - hopperItem.getAmount();
                        int toTransfer = Math.min(space, item.getAmount());

                        hopperItem.setAmount(hopperItem.getAmount() + toTransfer);

                        ItemStack toRemove = item.clone();
                        toRemove.setAmount(toTransfer);
                        itemsToRemove.add(toRemove);

                        transferred++;
                        inventoryChanged = true;
                        break;
                    }
                }
            }

            if (!itemsToRemove.isEmpty()) {
                spawner.removeItemsAndUpdateSellValue(itemsToRemove);
            }

            if (inventoryChanged) {
                updateOpenGuis(spawner);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error transferring items from spawner to hopper at " + hopperLoc, e);
        } finally {
            lock.unlock();
        }
    }

    private void updateOpenGuis(SpawnerData spawner) {
        // Use location-based scheduling for batch updates
        try {
            Scheduler.runLocationTaskLater(spawner.getSpawnerLocation(), () -> {
                try {
                    spawnerGuiViewManager.updateSpawnerMenuViewers(spawner);
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Error updating GUIs for spawner", e);
                }
            }, 2L);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error scheduling GUI update task", e);
        }
    }
}
