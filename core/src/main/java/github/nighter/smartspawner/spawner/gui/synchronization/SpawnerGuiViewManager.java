package github.nighter.smartspawner.spawner.gui.synchronization;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.Scheduler;
import github.nighter.smartspawner.spawner.gui.storage.StoragePageHolder;
import github.nighter.smartspawner.spawner.gui.storage.filter.FilterConfigHolder;
import github.nighter.smartspawner.spawner.gui.synchronization.listeners.InventoryEventListener;
import github.nighter.smartspawner.spawner.gui.synchronization.listeners.PlayerEventListener;
import github.nighter.smartspawner.spawner.gui.synchronization.managers.SlotCacheManager;
import github.nighter.smartspawner.spawner.gui.synchronization.managers.UpdateTaskManager;
import github.nighter.smartspawner.spawner.gui.synchronization.managers.ViewerTrackingManager;
import github.nighter.smartspawner.spawner.gui.synchronization.services.GuiUpdateService;
import github.nighter.smartspawner.spawner.gui.synchronization.services.StorageUpdateService;
import github.nighter.smartspawner.spawner.gui.synchronization.services.TimerUpdateService;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Facade and coordinator for spawner GUI management.
 * Delegates responsibilities to specialized managers, services, and listeners.
 * 
 * <p>This refactored version follows SOLID principles with clear separation of concerns:
 * <ul>
 *   <li>ViewerTrackingManager - tracks which players are viewing which spawners</li>
 *   <li>TimerUpdateService - handles timer calculations and updates</li>
 *   <li>GuiUpdateService - manages GUI item updates (storage, exp, info)</li>
 *   <li>StorageUpdateService - handles storage page transitions</li>
 *   <li>SlotCacheManager - caches GUI slot positions</li>
 *   <li>UpdateTaskManager - manages scheduled update task lifecycle</li>
 *   <li>InventoryEventListener - handles inventory open/close events</li>
 *   <li>PlayerEventListener - handles player quit events</li>
 * </ul>
 * 
 * <p>Thread safety is ensured through:
 * <ul>
 *   <li>ConcurrentHashMap for all shared state</li>
 *   <li>Folia-compliant region-based scheduling</li>
 *   <li>Proper synchronization in critical sections</li>
 * </ul>
 * 
 * <p>Memory leak prevention:
 * <ul>
 *   <li>Proper listener unregistration in cleanup()</li>
 *   <li>Clear references in all cleanup methods</li>
 *   <li>No long-lived Bukkit object references</li>
 * </ul>
 */
public class SpawnerGuiViewManager {

    private final SmartSpawner plugin;
    
    // Managers
    private final ViewerTrackingManager viewerTrackingManager;
    private final SlotCacheManager slotCacheManager;
    private final UpdateTaskManager updateTaskManager;
    
    // Services
    private final TimerUpdateService timerUpdateService;
    private final GuiUpdateService guiUpdateService;
    private final StorageUpdateService storageUpdateService;
    
    // Listeners
    private final InventoryEventListener inventoryEventListener;
    private final PlayerEventListener playerEventListener;

    public SpawnerGuiViewManager(SmartSpawner plugin) {
        this.plugin = plugin;
        
        // Initialize managers
        this.viewerTrackingManager = new ViewerTrackingManager();
        this.slotCacheManager = new SlotCacheManager(plugin);
        this.updateTaskManager = new UpdateTaskManager();
        
        // Initialize services
        this.timerUpdateService = new TimerUpdateService(plugin, viewerTrackingManager, slotCacheManager);
        this.guiUpdateService = new GuiUpdateService(plugin, slotCacheManager);
        this.storageUpdateService = new StorageUpdateService(plugin);
        
        // Initialize listeners
        this.inventoryEventListener = new InventoryEventListener(viewerTrackingManager, this::onViewerAdded);
        this.playerEventListener = new PlayerEventListener(viewerTrackingManager);
        
        // Register event listeners
        Bukkit.getPluginManager().registerEvents(inventoryEventListener, plugin);
        Bukkit.getPluginManager().registerEvents(playerEventListener, plugin);
    }

    /**
     * Called when a viewer is added to potentially start the update task.
     */
    private void onViewerAdded() {
        if (!updateTaskManager.isRunning() && viewerTrackingManager.hasAnyViewers()) {
            updateTaskManager.startTask(this::processPeriodicUpdates);
        }
    }

    /**
     * Periodic update processing called by the update task.
     * Handles both timer updates and batched GUI updates.
     */
    private void processPeriodicUpdates() {
        // Process batched GUI updates
        guiUpdateService.processPendingUpdates(
            viewerTrackingManager::getViewerInfo,
            this::cleanupViewer
        );
        
        // Process timer updates
        timerUpdateService.processTimerUpdates();
        
        // Stop task if no viewers remain
        if (!viewerTrackingManager.hasAnyViewers()) {
            updateTaskManager.stopTask();
        }
    }

    /**
     * Cleanup helper to remove viewer tracking and pending updates.
     */
    private void cleanupViewer(UUID playerId) {
        viewerTrackingManager.untrackViewer(playerId);
        guiUpdateService.clearPlayerUpdates(playerId);
        timerUpdateService.clearPlayerTracking(playerId);
    }

    // ===============================================================
    //                      Public API Methods
    // ===============================================================

    /**
     * Checks if timer placeholders are enabled in the GUI configuration.
     */
    public boolean isTimerPlaceholdersEnabled() {
        return timerUpdateService.isTimerPlaceholdersEnabled();
    }

    /**
     * Calculates the timer display value for a spawner.
     */
    public String calculateTimerDisplay(SpawnerData spawner) {
        return timerUpdateService.calculateTimerDisplay(spawner, null);
    }

    /**
     * Calculates the timer display string for a spawner with player context.
     */
    public String calculateTimerDisplay(SpawnerData spawner, Player player) {
        return timerUpdateService.calculateTimerDisplay(spawner, player);
    }

    /**
     * Re-checks timer placeholder usage after configuration reload.
     */
    public void recheckTimerPlaceholders() {
        timerUpdateService.recheckTimerPlaceholders();
    }

    /**
     * Clears all cached slot positions and re-initializes them.
     * Called when GUI layout configuration is reloaded.
     */
    public void clearSlotCache() {
        slotCacheManager.clearAndReinitialize();
    }

    /**
     * Gets all players viewing a specific spawner.
     */
    public Set<Player> getViewers(String spawnerId) {
        return viewerTrackingManager.getViewers(spawnerId);
    }

    /**
     * Checks if a spawner has any viewers.
     */
    public boolean hasViewers(SpawnerData spawner) {
        return viewerTrackingManager.hasViewers(spawner);
    }

    /**
     * Clears all tracked GUIs and associated data.
     */
    public void clearAllTrackedGuis() {
        viewerTrackingManager.clearAll();
        guiUpdateService.clearAllPendingUpdates();
        timerUpdateService.clearAllTracking();
    }

    /**
     * Forces immediate timer update when spawner state changes.
     */
    public void forceStateChangeUpdate(SpawnerData spawner) {
        timerUpdateService.forceStateChangeUpdate(spawner);
    }

    /**
     * Forces immediate timer update for a specific player.
     */
    public void forceTimerUpdate(Player player, SpawnerData spawner) {
        timerUpdateService.forceTimerUpdate(player, spawner);
    }

    /**
     * Updates all viewers of a spawner menu.
     * Main entry point for triggering GUI updates from external classes.
     */
    public void updateSpawnerMenuViewers(SpawnerData spawner) {
        Set<UUID> viewers = viewerTrackingManager.getViewerIds(spawner.getSpawnerId());
        if (viewers == null || viewers.isEmpty()) {
            return;
        }

        // Invalidate caches
        if (plugin.getSpawnerMenuUI() != null) {
            plugin.getSpawnerMenuUI().invalidateSpawnerCache(spawner.getSpawnerId());
        }

        if (plugin.getSpawnerMenuFormUI() != null) {
            plugin.getSpawnerMenuFormUI().invalidateSpawnerCache(spawner.getSpawnerId());
        }

        int viewerCount = viewers.size();
        if (viewerCount > 10) {
            plugin.debug(viewerCount + " spawner menu viewers to update for " + spawner.getSpawnerId() + " (batch update)");
        }

        // Schedule updates for all viewers
        for (UUID viewerId : viewers) {
            Player viewer = Bukkit.getPlayer(viewerId);
            if (viewer == null || !viewer.isOnline()) {
                cleanupViewer(viewerId);
                continue;
            }

            // Schedule batched GUI update for main menu viewers
            guiUpdateService.scheduleUpdate(viewerId, GuiUpdateService.UPDATE_ALL);

            // Handle storage page updates - calculate pages on the correct thread
            Inventory openInv = viewer.getOpenInventory().getTopInventory();
            if (openInv != null && openInv.getHolder(false) instanceof StoragePageHolder) {
                // Schedule storage update - page calculation happens inside
                Location loc = viewer.getLocation();
                if (loc != null) {
                    Scheduler.runLocationTask(loc, () -> {
                        if (!viewer.isOnline()) {
                            return;
                        }

                        Inventory inv = viewer.getOpenInventory().getTopInventory();
                        if (inv != null && inv.getHolder(false) instanceof StoragePageHolder) {
                            StoragePageHolder holder = (StoragePageHolder) inv.getHolder(false);
                            int oldPages = storageUpdateService.calculateTotalPages(holder.getOldUsedSlots());
                            int newPages = storageUpdateService.calculateTotalPages(spawner.getVirtualInventory().getUsedSlots());

                            storageUpdateService.processStorageUpdateDirect(viewer, inv, spawner, holder, oldPages, newPages);
                        }
                    });
                }
            }
        }
    }

    /**
     * Updates spawner menu GUI for a specific player.
     * Schedules a batched update instead of immediate processing.
     */
    public void updateSpawnerMenuGui(Player player, SpawnerData spawner, boolean forceUpdate) {
        guiUpdateService.scheduleUpdate(player.getUniqueId(), GuiUpdateService.UPDATE_ALL);
    }

    /**
     * Closes all viewer inventories for a specific spawner.
     * Includes filter GUI viewers to prevent duplication exploits.
     */
    public void closeAllViewersInventory(SpawnerData spawner) {
        String spawnerId = spawner.getSpawnerId();
        Set<Player> viewers = getViewers(spawnerId);
        
        if (!viewers.isEmpty()) {
            for (Player viewer : viewers) {
                if (viewer != null && viewer.isOnline()) {
                    viewer.closeInventory();
                }
            }
        }

        // Force close filter GUI viewers to prevent duplication exploits
        Set<UUID> filterViewers = viewerTrackingManager.getFilterViewersForSpawner(spawnerId);
        if (filterViewers != null && !filterViewers.isEmpty()) {
            Set<UUID> filterViewersCopy = new HashSet<>(filterViewers);
            for (UUID viewerId : filterViewersCopy) {
                Player viewer = Bukkit.getPlayer(viewerId);
                if (viewer != null && viewer.isOnline()) {
                    Inventory openInventory = viewer.getOpenInventory().getTopInventory();
                    if (openInventory != null && openInventory.getHolder(false) instanceof FilterConfigHolder filterHolder) {
                        if (filterHolder.getSpawnerData().getSpawnerId().equals(spawnerId)) {
                            viewer.closeInventory();
                        }
                    }
                }
            }
        }

        // Check for stacker viewers if that functionality exists
        if (plugin.getSpawnerStackerHandler() != null) {
            plugin.getSpawnerStackerHandler().closeAllViewersInventory(spawnerId);
        }
    }

    /**
     * Cleanup method to be called on plugin disable.
     * Stops the update task and clears all tracking data.
     * Unregisters event listeners to prevent memory leaks.
     */
    public void cleanup() {
        // Stop update task
        updateTaskManager.stopTask();
        
        // Clear all tracking
        clearAllTrackedGuis();
        
        // Unregister listeners to prevent memory leaks
        org.bukkit.event.HandlerList.unregisterAll(inventoryEventListener);
        org.bukkit.event.HandlerList.unregisterAll(playerEventListener);
    }
}
