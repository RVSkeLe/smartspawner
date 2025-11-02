package github.nighter.smartspawner.spawner.gui.synchronization.services;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.Scheduler;
import github.nighter.smartspawner.language.LanguageManager;
import github.nighter.smartspawner.spawner.gui.main.SpawnerMenuHolder;
import github.nighter.smartspawner.spawner.gui.synchronization.managers.SlotCacheManager;
import github.nighter.smartspawner.spawner.gui.synchronization.managers.ViewerTrackingManager;
import github.nighter.smartspawner.spawner.gui.synchronization.utils.LootPreGenerationHelper;
import github.nighter.smartspawner.spawner.gui.synchronization.utils.TimerFormatter;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service responsible for managing and updating spawner timers in GUIs.
 * Handles timer calculation, display formatting, and periodic updates for main menu viewers.
 */
public class TimerUpdateService {

    private static final int MAX_PLAYERS_PER_BATCH = 10; // Limit players processed per batch

    private final SmartSpawner plugin;
    private final LanguageManager languageManager;
    private final LootPreGenerationHelper lootHelper;
    private final ViewerTrackingManager viewerTrackingManager;
    private final SlotCacheManager slotCacheManager;

    // Cached status text messages
    private String cachedInactiveText;
    private String cachedFullText;

    // Timer placeholder detection
    private volatile Boolean hasTimerPlaceholders = null;

    // Performance tracking
    private final Map<UUID, Long> lastTimerUpdate = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastTimerValue = new ConcurrentHashMap<>();

    public TimerUpdateService(SmartSpawner plugin, ViewerTrackingManager viewerTrackingManager,
                              SlotCacheManager slotCacheManager) {
        this.plugin = plugin;
        this.languageManager = plugin.getLanguageManager();
        this.lootHelper = new LootPreGenerationHelper(plugin);
        this.viewerTrackingManager = viewerTrackingManager;
        this.slotCacheManager = slotCacheManager;
        initializeCachedStrings();
    }

    /**
     * Initializes cached strings and detects timer placeholder usage.
     */
    private void initializeCachedStrings() {
        cachedInactiveText = languageManager.getGuiItemName("spawner_info_item.lore_inactive");
        cachedFullText = languageManager.getGuiItemName("spawner_info_item.lore_full");
        checkTimerPlaceholderUsage();
    }

    /**
     * Checks if the GUI configuration uses %time% placeholders.
     */
    private void checkTimerPlaceholderUsage() {
        try {
            String[] loreLines = languageManager.getGuiItemLore("spawner_info_item.lore");
            String[] loreNoShopLines = languageManager.getGuiItemLore("spawner_info_item.lore_no_shop");

            boolean hasTimers = false;

            if (loreLines != null) {
                for (String line : loreLines) {
                    if (line != null && line.contains("%time%")) {
                        hasTimers = true;
                        break;
                    }
                }
            }

            if (!hasTimers && loreNoShopLines != null) {
                for (String line : loreNoShopLines) {
                    if (line != null && line.contains("%time%")) {
                        hasTimers = true;
                        break;
                    }
                }
            }

            hasTimerPlaceholders = hasTimers;
        } catch (Exception e) {
            hasTimerPlaceholders = true; // Fallback to enabled if we can't determine
        }
    }

    /**
     * Checks if timer placeholders are enabled.
     *
     * @return true if timer placeholders are used in GUI
     */
    public boolean isTimerPlaceholdersEnabled() {
        return hasTimerPlaceholders == null || hasTimerPlaceholders;
    }

    /**
     * Re-checks timer placeholder usage after configuration reload.
     */
    public void recheckTimerPlaceholders() {
        initializeCachedStrings();
    }

    /**
     * Calculates and returns the timer display string for a spawner.
     *
     * @param spawner The spawner to calculate timer for
     * @param player The player viewing (can be null)
     * @return Formatted timer string
     */
    public String calculateTimerDisplay(SpawnerData spawner, Player player) {
        if (!isTimerPlaceholdersEnabled()) {
            return "";
        }

        if (player != null && player.getGameMode() == GameMode.SPECTATOR) {
            return cachedInactiveText;
        }

        if (spawner.getIsAtCapacity()) {
            return cachedFullText;
        }

        long timeUntilNextSpawn = calculateTimeUntilNextSpawn(spawner);
        
        if (timeUntilNextSpawn == -1) {
            return cachedInactiveText;
        }
        
        return TimerFormatter.formatTime(timeUntilNextSpawn);
    }

    /**
     * Processes timer updates for all main menu viewers.
     * This is called periodically by the update task.
     */
    public void processTimerUpdates() {
        if (!isTimerPlaceholdersEnabled()) {
            return;
        }

        if (!viewerTrackingManager.hasMainMenuViewers()) {
            return;
        }

        long currentTime = System.currentTimeMillis();

        // Group main menu viewers by spawner
        Map<String, List<UUID>> spawnerViewers = new HashMap<>();

        for (Map.Entry<UUID, ViewerTrackingManager.ViewerInfo> entry : 
                viewerTrackingManager.getMainMenuViewers().entrySet()) {
            UUID playerId = entry.getKey();
            ViewerTrackingManager.ViewerInfo viewerInfo = entry.getValue();
            SpawnerData spawner = viewerInfo.getSpawnerData();

            Player player = Bukkit.getPlayer(playerId);
            if (!isValidGuiSession(player)) {
                viewerTrackingManager.untrackViewer(playerId);
                continue;
            }

            // Ensure player has main menu open
            Inventory openInventory = player.getOpenInventory().getTopInventory();
            if (openInventory == null || !(openInventory.getHolder(false) instanceof SpawnerMenuHolder)) {
                viewerTrackingManager.untrackViewer(playerId);
                continue;
            }

            // Skip if updated recently
            Long lastUpdate = lastTimerUpdate.get(playerId);
            if (lastUpdate != null && (currentTime - lastUpdate) < 800) {
                continue;
            }

            spawnerViewers.computeIfAbsent(spawner.getSpawnerId(), k -> new ArrayList<>()).add(playerId);
        }

        int processedPlayers = 0;

        // Process spawners in batches
        for (Map.Entry<String, List<UUID>> spawnerGroup : spawnerViewers.entrySet()) {
            List<UUID> viewers = spawnerGroup.getValue();
            if (viewers.isEmpty()) continue;

            // Get spawner from first viewer
            UUID firstViewer = viewers.get(0);
            ViewerTrackingManager.ViewerInfo viewerInfo = viewerTrackingManager.getMainMenuViewers().get(firstViewer);
            if (viewerInfo == null) continue;

            SpawnerData spawner = viewerInfo.getSpawnerData();

            // Calculate timer once for this spawner
            String newTimerValue = calculateTimerDisplayInternal(spawner);

            // Apply to all viewers
            for (UUID playerId : viewers) {
                if (processedPlayers >= MAX_PLAYERS_PER_BATCH) {
                    break;
                }

                if (!viewerTrackingManager.getMainMenuViewers().containsKey(playerId)) {
                    continue;
                }

                Player player = Bukkit.getPlayer(playerId);
                if (!isValidGuiSession(player)) {
                    viewerTrackingManager.untrackViewer(playerId);
                    continue;
                }

                // Skip if timer unchanged
                String lastValue = lastTimerValue.get(playerId);
                if (lastValue != null && lastValue.equals(newTimerValue)) {
                    continue;
                }

                // Update tracking
                lastTimerUpdate.put(playerId, currentTime);
                lastTimerValue.put(playerId, newTimerValue);
                processedPlayers++;

                // Schedule update on player's thread
                Location playerLocation = player.getLocation();
                if (playerLocation != null) {
                    final String finalTimerValue = newTimerValue;
                    final UUID finalPlayerId = playerId;

                    Scheduler.runLocationTask(playerLocation, () -> {
                        if (!player.isOnline() || !viewerTrackingManager.getMainMenuViewers().containsKey(finalPlayerId)) {
                            return;
                        }

                        Inventory openInventory = player.getOpenInventory().getTopInventory();
                        if (openInventory == null || !(openInventory.getHolder(false) instanceof SpawnerMenuHolder)) {
                            viewerTrackingManager.untrackViewer(finalPlayerId);
                            return;
                        }

                        int spawnerInfoSlot = slotCacheManager.getSpawnerInfoSlot();
                        if (spawnerInfoSlot >= 0) {
                            updateSpawnerInfoItemTimer(openInventory, spawner, finalTimerValue, spawnerInfoSlot);
                            player.updateInventory();
                        }
                    });
                }
            }

            if (processedPlayers >= MAX_PLAYERS_PER_BATCH) {
                break;
            }
        }
    }

    /**
     * Forces immediate timer update for spawner state changes.
     *
     * @param spawner The spawner whose state changed
     */
    public void forceStateChangeUpdate(SpawnerData spawner) {
        if (!isTimerPlaceholdersEnabled()) {
            return;
        }

        Set<UUID> mainMenuViewerSet = viewerTrackingManager.getMainMenuViewersForSpawner(spawner.getSpawnerId());
        if (mainMenuViewerSet == null || mainMenuViewerSet.isEmpty()) {
            return;
        }

        // Clear previous values to force refresh
        for (UUID viewerId : mainMenuViewerSet) {
            lastTimerUpdate.remove(viewerId);
            lastTimerValue.remove(viewerId);
        }

        updateMainMenuViewers(spawner);
    }

    /**
     * Forces immediate timer update for a specific player.
     *
     * @param player The player
     * @param spawner The spawner
     */
    public void forceTimerUpdate(Player player, SpawnerData spawner) {
        if (!isTimerPlaceholdersEnabled()) {
            return;
        }

        if (!isValidGuiSession(player)) {
            return;
        }

        Location playerLocation = player.getLocation();
        if (playerLocation == null) {
            return;
        }

        Scheduler.runLocationTask(playerLocation, () -> {
            if (!player.isOnline()) {
                return;
            }

            Inventory openInventory = player.getOpenInventory().getTopInventory();
            if (openInventory == null || !(openInventory.getHolder(false) instanceof SpawnerMenuHolder)) {
                return;
            }

            int spawnerInfoSlot = slotCacheManager.getSpawnerInfoSlot();
            if (spawnerInfoSlot >= 0) {
                String timerValue = calculateTimerDisplayInternal(spawner);
                updateSpawnerInfoItemTimer(openInventory, spawner, timerValue, spawnerInfoSlot);
                player.updateInventory();
            }
        });
    }

    /**
     * Internal method to calculate timer display with loot pre-generation.
     */
    private String calculateTimerDisplayInternal(SpawnerData spawner) {
        if (spawner.getIsAtCapacity()) {
            return cachedFullText;
        }

        long timeUntilNextSpawn = calculateTimeUntilNextSpawn(spawner);
        
        if (timeUntilNextSpawn == -1) {
            return cachedInactiveText;
        }
        
        return TimerFormatter.formatTime(timeUntilNextSpawn);
    }

    /**
     * Updates main menu viewers immediately.
     */
    private void updateMainMenuViewers(SpawnerData spawner) {
        Set<UUID> mainMenuViewerSet = viewerTrackingManager.getMainMenuViewersForSpawner(spawner.getSpawnerId());
        if (mainMenuViewerSet == null || mainMenuViewerSet.isEmpty()) {
            return;
        }

        String timerValue = calculateTimerDisplayInternal(spawner);

        for (UUID viewerId : new HashSet<>(mainMenuViewerSet)) {
            Player viewer = Bukkit.getPlayer(viewerId);
            if (!isValidGuiSession(viewer)) {
                viewerTrackingManager.untrackViewer(viewerId);
                continue;
            }

            Location loc = viewer.getLocation();
            if (loc != null) {
                final String finalTimerValue = timerValue;
                final UUID finalViewerId = viewerId;

                Scheduler.runLocationTask(loc, () -> {
                    if (!viewer.isOnline() || !viewerTrackingManager.getMainMenuViewers().containsKey(finalViewerId)) {
                        return;
                    }

                    Inventory openInv = viewer.getOpenInventory().getTopInventory();
                    if (openInv == null || !(openInv.getHolder(false) instanceof SpawnerMenuHolder)) {
                        viewerTrackingManager.untrackViewer(finalViewerId);
                        return;
                    }

                    updateSpawnerInfoItemTimer(openInv, spawner, finalTimerValue, slotCacheManager.getSpawnerInfoSlot());
                    viewer.updateInventory();
                });
            }
        }
    }

    /**
     * Calculates time until next spawn for GUI display purposes only.
     * Actual loot spawning is handled by SpawnerRangeChecker independently.
     */
    private long calculateTimeUntilNextSpawn(SpawnerData spawner) {
        long cachedDelay = spawner.getCachedSpawnDelay();
        if (cachedDelay == 0) {
            cachedDelay = spawner.getSpawnDelay() * 50L;
            spawner.setCachedSpawnDelay(cachedDelay);
        }

        long currentTime = System.currentTimeMillis();
        long lastSpawnTime = spawner.getLastSpawnTime();
        long timeElapsed = currentTime - lastSpawnTime;

        boolean isSpawnerInactive = !spawner.getSpawnerActive() ||
            (spawner.getSpawnerStop().get() && timeElapsed > cachedDelay * 2);

        if (isSpawnerInactive) {
            spawner.clearPreGeneratedLoot();
            return -1;
        }

        long timeUntilNextSpawn = cachedDelay - timeElapsed;
        timeUntilNextSpawn = Math.max(0, Math.min(timeUntilNextSpawn, cachedDelay));
        
        // Pre-generate loot when timer is low for smooth GUI display
        if (lootHelper.shouldPreGenerateLoot(timeUntilNextSpawn)) {
            lootHelper.preGenerateLoot(spawner);
        }

        return timeUntilNextSpawn;
    }

    /**
     * Updates timer in spawner info item.
     */
    private void updateSpawnerInfoItemTimer(Inventory inventory, SpawnerData spawner, 
                                           String timeDisplay, int spawnerInfoSlot) {
        if (spawnerInfoSlot < 0) {
            return;
        }

        ItemStack spawnerItem = inventory.getItem(spawnerInfoSlot);
        if (spawnerItem == null || !spawnerItem.hasItemMeta()) {
            return;
        }

        ItemMeta meta = spawnerItem.getItemMeta();
        if (meta == null || !meta.hasLore()) {
            return;
        }

        List<String> lore = meta.getLore();
        if (lore == null) {
            return;
        }

        boolean needsUpdate = false;
        List<String> updatedLore = new ArrayList<>(lore.size());

        for (String line : lore) {
            if (line.contains("%time%")) {
                updatedLore.add(line.replace("%time%", timeDisplay));
                needsUpdate = true;
            } else {
                String updatedLine = updateExistingTimerLine(line, timeDisplay);
                if (!updatedLine.equals(line)) {
                    updatedLore.add(updatedLine);
                    needsUpdate = true;
                } else {
                    updatedLore.add(line);
                }
            }
        }

        if (needsUpdate) {
            meta.setLore(updatedLore);
            spawnerItem.setItemMeta(meta);
            inventory.setItem(spawnerInfoSlot, spawnerItem);
        }
    }

    /**
     * Updates existing timer line by replacing old value with new.
     */
    private String updateExistingTimerLine(String line, String newTimeDisplay) {
        String strippedLine = ChatColor.stripColor(line);
        String strippedNewDisplay = ChatColor.stripColor(newTimeDisplay);

        if (strippedLine.matches(".*\\d{2}:\\d{2}.*") ||
            strippedLine.contains(ChatColor.stripColor(cachedInactiveText)) ||
            strippedLine.contains(ChatColor.stripColor(cachedFullText))) {

            String updatedLine = line.replaceAll("\\d{2}:\\d{2}", newTimeDisplay);
            if (!updatedLine.equals(line)) {
                return updatedLine;
            }

            String strippedCachedInactive = ChatColor.stripColor(cachedInactiveText);
            String strippedCachedFull = ChatColor.stripColor(cachedFullText);

            if (strippedLine.contains(strippedCachedInactive)) {
                return line.replace(cachedInactiveText, newTimeDisplay);
            } else if (strippedLine.contains(strippedCachedFull)) {
                return line.replace(cachedFullText, newTimeDisplay);
            }
        }

        if (strippedNewDisplay.equals(ChatColor.stripColor(cachedInactiveText)) ||
            strippedNewDisplay.equals(ChatColor.stripColor(cachedFullText))) {
            if (strippedLine.matches(".*\\d{2}:\\d{2}.*")) {
                return line.replaceAll("\\d{2}:\\d{2}", newTimeDisplay);
            }
        }

        return line;
    }

    private boolean isValidGuiSession(Player player) {
        return player != null && player.isOnline();
    }

    /**
     * Clears performance tracking for a player.
     *
     * @param playerId The player's UUID
     */
    public void clearPlayerTracking(UUID playerId) {
        lastTimerUpdate.remove(playerId);
        lastTimerValue.remove(playerId);
    }

    /**
     * Clears all performance tracking.
     */
    public void clearAllTracking() {
        lastTimerUpdate.clear();
        lastTimerValue.clear();
    }
}
