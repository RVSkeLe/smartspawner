package github.nighter.smartspawner.spawner.gui.synchronization.managers;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.spawner.gui.layout.GuiButton;
import github.nighter.smartspawner.spawner.gui.layout.GuiLayout;

/**
 * Manages caching of GUI slot positions for optimal performance.
 * Slot positions are read from the layout configuration and cached to avoid
 * repeated lookups during GUI updates.
 */
public class SlotCacheManager {

    private final SmartSpawner plugin;

    // Cached slot positions
    private volatile int cachedStorageSlot = -1;
    private volatile int cachedExpSlot = -1;
    private volatile int cachedSpawnerInfoSlot = -1;

    public SlotCacheManager(SmartSpawner plugin) {
        this.plugin = plugin;
        initializeSlotPositions();
    }

    /**
     * Initializes all GUI slot positions from the current layout configuration.
     * This is called during construction and when layout is reloaded.
     */
    public void initializeSlotPositions() {
        GuiLayout layout = plugin.getGuiLayoutConfig().getCurrentMainLayout();
        if (layout == null) {
            cachedStorageSlot = -1;
            cachedExpSlot = -1;
            cachedSpawnerInfoSlot = -1;
            return;
        }

        // Initialize storage slot
        GuiButton storageButton = layout.getButton("storage");
        cachedStorageSlot = storageButton != null ? storageButton.getSlot() : -1;

        // Initialize exp slot
        GuiButton expButton = layout.getButton("exp");
        cachedExpSlot = expButton != null ? expButton.getSlot() : -1;

        // Initialize spawner info slot using the same logic as SpawnerMenuUI
        GuiButton spawnerInfoButton = null;

        // Check for shop integration to determine which button to use
        if (plugin.hasSellIntegration()) {
            spawnerInfoButton = layout.getButton("spawner_info_with_shop");
        }

        if (spawnerInfoButton == null) {
            spawnerInfoButton = layout.getButton("spawner_info_no_shop");
        }

        if (spawnerInfoButton == null) {
            spawnerInfoButton = layout.getButton("spawner_info");
        }

        cachedSpawnerInfoSlot = spawnerInfoButton != null ? spawnerInfoButton.getSlot() : -1;
    }

    /**
     * Gets the cached storage slot position.
     *
     * @return The slot number for the storage button, or -1 if not found
     */
    public int getStorageSlot() {
        return cachedStorageSlot;
    }

    /**
     * Gets the cached exp slot position.
     *
     * @return The slot number for the exp button, or -1 if not found
     */
    public int getExpSlot() {
        return cachedExpSlot;
    }

    /**
     * Gets the cached spawner info slot position.
     *
     * @return The slot number for the spawner info button, or -1 if not found
     */
    public int getSpawnerInfoSlot() {
        return cachedSpawnerInfoSlot;
    }

    /**
     * Clears and re-initializes all cached slot positions.
     * This should be called when the GUI layout configuration is reloaded.
     */
    public void clearAndReinitialize() {
        initializeSlotPositions();
    }
}
