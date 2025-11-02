package github.nighter.smartspawner.spawner.gui.synchronization.services;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.Scheduler;
import github.nighter.smartspawner.language.LanguageManager;
import github.nighter.smartspawner.spawner.gui.main.SpawnerMenuHolder;
import github.nighter.smartspawner.spawner.gui.main.SpawnerMenuUI;
import github.nighter.smartspawner.spawner.gui.synchronization.ItemUpdater;
import github.nighter.smartspawner.spawner.gui.synchronization.managers.SlotCacheManager;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service responsible for updating GUI items (storage, exp, spawner info).
 * Handles batched updates and item synchronization with thread safety.
 */
public class GuiUpdateService {

    // Update flags - using bit flags for efficient state tracking
    public static final int UPDATE_CHEST = 1;
    public static final int UPDATE_INFO = 2;
    public static final int UPDATE_EXP = 4;
    public static final int UPDATE_ALL = UPDATE_CHEST | UPDATE_INFO | UPDATE_EXP;

    private final SmartSpawner plugin;
    private final LanguageManager languageManager;
    private final SpawnerMenuUI spawnerMenuUI;
    private final SlotCacheManager slotCacheManager;

    // Batched update tracking
    private final Set<UUID> pendingUpdates = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Integer> updateFlags = new ConcurrentHashMap<>();

    public GuiUpdateService(SmartSpawner plugin, SlotCacheManager slotCacheManager) {
        this.plugin = plugin;
        this.languageManager = plugin.getLanguageManager();
        this.spawnerMenuUI = plugin.getSpawnerMenuUI();
        this.slotCacheManager = slotCacheManager;
    }

    /**
     * Schedules a GUI update for a player.
     *
     * @param playerId The player's UUID
     * @param flags Update flags indicating which items to update
     */
    public void scheduleUpdate(UUID playerId, int flags) {
        pendingUpdates.add(playerId);
        updateFlags.put(playerId, flags);
    }

    /**
     * Processes all pending GUI updates.
     * Called periodically by the update task.
     *
     * @param viewerInfoGetter Function to get viewer info
     * @param untrackViewer Consumer to untrack a viewer
     */
    public void processPendingUpdates(
            java.util.function.Function<UUID, ?> viewerInfoGetter,
            java.util.function.Consumer<UUID> untrackViewer) {
        
        if (pendingUpdates.isEmpty()) {
            return;
        }

        Set<UUID> currentUpdates = new HashSet<>(pendingUpdates);
        pendingUpdates.clear();

        for (UUID playerId : currentUpdates) {
            Player player = org.bukkit.Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                untrackViewer.accept(playerId);
                updateFlags.remove(playerId);
                continue;
            }

            Object info = viewerInfoGetter.apply(playerId);
            if (info == null) {
                updateFlags.remove(playerId);
                continue;
            }

            int flags = updateFlags.getOrDefault(playerId, UPDATE_ALL);
            updateFlags.remove(playerId);

            Location loc = player.getLocation();
            if (loc != null) {
                final int finalFlags = flags;
                
                // Extract spawner data from viewer info using reflection-like approach
                SpawnerData spawner = extractSpawnerData(info);
                if (spawner == null) {
                    continue;
                }

                Scheduler.runLocationTask(loc, () -> {
                    if (!player.isOnline()) {
                        return;
                    }

                    Inventory openInv = player.getOpenInventory().getTopInventory();
                    if (openInv == null || !(openInv.getHolder(false) instanceof SpawnerMenuHolder)) {
                        return;
                    }

                    processInventoryUpdate(player, openInv, spawner, finalFlags);
                });
            }
        }
    }

    /**
     * Processes inventory update for a specific player.
     */
    private void processInventoryUpdate(Player player, Inventory inventory, SpawnerData spawner, int flags) {
        boolean needsUpdate = false;

        if ((flags & UPDATE_CHEST) != 0) {
            int storageSlot = slotCacheManager.getStorageSlot();
            if (storageSlot >= 0) {
                updateChestItem(inventory, spawner, storageSlot);
                needsUpdate = true;
            }
        }

        if ((flags & UPDATE_INFO) != 0) {
            int spawnerInfoSlot = slotCacheManager.getSpawnerInfoSlot();
            if (spawnerInfoSlot >= 0) {
                updateSpawnerInfoItem(inventory, spawner, player, spawnerInfoSlot);
                needsUpdate = true;
            }
        }

        if ((flags & UPDATE_EXP) != 0) {
            int expSlot = slotCacheManager.getExpSlot();
            if (expSlot >= 0) {
                updateExpItem(inventory, spawner, expSlot);
                needsUpdate = true;
            }
        }

        if (needsUpdate) {
            player.updateInventory();
        }
    }

    /**
     * Updates the chest/storage item in inventory.
     */
    private void updateChestItem(Inventory inventory, SpawnerData spawner, int storageSlot) {
        if (storageSlot < 0) {
            return;
        }

        ItemStack currentChestItem = inventory.getItem(storageSlot);
        if (currentChestItem == null || !currentChestItem.hasItemMeta()) {
            return;
        }

        ItemStack newChestItem = spawnerMenuUI.createLootStorageItem(spawner);

        if (!ItemUpdater.areItemsEqual(currentChestItem, newChestItem)) {
            inventory.setItem(storageSlot, newChestItem);
        }
    }

    /**
     * Updates the exp item in inventory.
     */
    private void updateExpItem(Inventory inventory, SpawnerData spawner, int expSlot) {
        if (expSlot < 0) {
            return;
        }

        ItemStack currentExpItem = inventory.getItem(expSlot);
        if (currentExpItem == null || !currentExpItem.hasItemMeta()) {
            return;
        }

        ItemStack newExpItem = spawnerMenuUI.createExpItem(spawner);

        if (!ItemUpdater.areItemsEqual(currentExpItem, newExpItem)) {
            inventory.setItem(expSlot, newExpItem);
        }
    }

    /**
     * Updates the spawner info item in inventory.
     */
    private void updateSpawnerInfoItem(Inventory inventory, SpawnerData spawner, Player player, int spawnerInfoSlot) {
        if (spawnerInfoSlot < 0) {
            return;
        }

        ItemStack currentSpawnerItem = inventory.getItem(spawnerInfoSlot);
        if (currentSpawnerItem == null || !currentSpawnerItem.hasItemMeta()) {
            return;
        }

        ItemStack newSpawnerItem = spawnerMenuUI.createSpawnerInfoItem(player, spawner);

        if (!ItemUpdater.areItemsEqual(currentSpawnerItem, newSpawnerItem)) {
            preserveTimerInfo(currentSpawnerItem, newSpawnerItem);
            inventory.setItem(spawnerInfoSlot, newSpawnerItem);
        }
    }

    /**
     * Preserves timer information when updating spawner info item.
     */
    private void preserveTimerInfo(ItemStack currentItem, ItemStack newItem) {
        ItemMeta currentMeta = currentItem.getItemMeta();
        ItemMeta newMeta = newItem.getItemMeta();

        if (currentMeta == null || !currentMeta.hasLore() || newMeta == null || !newMeta.hasLore()) {
            return;
        }

        List<String> currentLore = currentMeta.getLore();
        List<String> newLore = newMeta.getLore();

        if (currentLore == null || newLore == null) {
            return;
        }

        // Find timer line in new lore
        int newTimerLineIndex = -1;
        for (int i = 0; i < newLore.size(); i++) {
            if (newLore.get(i).contains("%time%")) {
                newTimerLineIndex = i;
                break;
            }
        }

        if (newTimerLineIndex == -1) {
            return;
        }

        // Extract timer value from current lore
        if (newTimerLineIndex < currentLore.size()) {
            String currentLine = currentLore.get(newTimerLineIndex);
            String newLine = newLore.get(newTimerLineIndex);

            if (!currentLine.contains("%time%") && newLine.contains("%time%")) {
                String currentTimerValue = extractTimerValue(currentLine, newLine);
                
                if (currentTimerValue != null && !currentTimerValue.isEmpty()) {
                    Map<String, String> timerPlaceholder = Collections.singletonMap("time", currentTimerValue);
                    List<String> updatedLore = new ArrayList<>(newLore.size());

                    for (String line : newLore) {
                        updatedLore.add(languageManager.applyOnlyPlaceholders(line, timerPlaceholder));
                    }

                    newMeta.setLore(updatedLore);
                    newItem.setItemMeta(newMeta);
                }
            }
        }
    }

    /**
     * Extracts timer value from current line using template matching.
     */
    private String extractTimerValue(String currentLine, String newLine) {
        String newLineTemplate = newLine.replace("%time%", "TIMER_PLACEHOLDER");
        String cleanNewTemplate = ChatColor.stripColor(newLineTemplate);
        String cleanCurrentLine = ChatColor.stripColor(currentLine);

        int placeholderIndex = cleanNewTemplate.indexOf("TIMER_PLACEHOLDER");
        if (placeholderIndex >= 0 && cleanCurrentLine.length() >= placeholderIndex) {
            String beforePlaceholder = cleanNewTemplate.substring(0, placeholderIndex);
            String afterPlaceholder = cleanNewTemplate.substring(placeholderIndex + "TIMER_PLACEHOLDER".length());

            if (cleanCurrentLine.startsWith(beforePlaceholder) && cleanCurrentLine.endsWith(afterPlaceholder)) {
                int startIndex = beforePlaceholder.length();
                int endIndex = cleanCurrentLine.length() - afterPlaceholder.length();
                if (endIndex > startIndex) {
                    return cleanCurrentLine.substring(startIndex, endIndex).trim();
                }
            }
        }

        return null;
    }

    /**
     * Extracts SpawnerData from viewer info object.
     */
    private SpawnerData extractSpawnerData(Object info) {
        if (info instanceof github.nighter.smartspawner.spawner.gui.synchronization.managers.ViewerTrackingManager.ViewerInfo viewerInfo) {
            return viewerInfo.getSpawnerData();
        }
        return null;
    }

    /**
     * Clears all pending updates.
     */
    public void clearAllPendingUpdates() {
        pendingUpdates.clear();
        updateFlags.clear();
    }

    /**
     * Clears pending updates for a specific player.
     *
     * @param playerId The player's UUID
     */
    public void clearPlayerUpdates(UUID playerId) {
        pendingUpdates.remove(playerId);
        updateFlags.remove(playerId);
    }
}
