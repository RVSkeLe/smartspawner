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

        // OPTIMIZATION: Find buttons by ACTION instead of name to support new slot-based format
        int storageSlot = -1;
        int expSlot = -1;
        int spawnerInfoSlot = -1;

        boolean hasShopIntegration = plugin.hasSellIntegration();

        for (GuiButton button : layout.getAllButtons().values()) {
            if (!button.isEnabled()) {
                continue;
            }

            // Get any action from button
            String action = getAnyActionFromButton(button);
            if (action == null) continue;

            // Find button slots by their actions
            switch (action) {
                case "open_storage":
                    storageSlot = button.getSlot();
                    break;
                case "collect_exp":
                    expSlot = button.getSlot();
                    break;
                case "open_stacker":
                case "sell_and_exp":
                case "none":
                    // Spawner info button - check condition
                    if (!button.hasCondition()) {
                        // No condition = fallback button
                        if (spawnerInfoSlot == -1) {
                            spawnerInfoSlot = button.getSlot();
                        }
                    } else {
                        // Check if condition matches
                        String condition = button.getCondition();
                        if (("shop_integration".equals(condition) && hasShopIntegration) ||
                            ("no_shop_integration".equals(condition) && !hasShopIntegration)) {
                            spawnerInfoSlot = button.getSlot();
                        }
                    }
                    break;
            }
        }

        cachedStorageSlot = storageSlot;
        cachedExpSlot = expSlot;
        cachedSpawnerInfoSlot = spawnerInfoSlot;
    }

    /**
     * Get any action from button - checks click, left_click, right_click
     * OPTIMIZATION: Return first found action
     */
    private String getAnyActionFromButton(GuiButton button) {
        // Check click type actions in priority order
        var actions = button.getActions();
        if (actions == null || actions.isEmpty()) {
            return null;
        }

        // Check in priority: click -> left_click -> right_click
        String action = actions.get("click");
        if (action != null && !action.isEmpty()) {
            return action;
        }

        action = actions.get("left_click");
        if (action != null && !action.isEmpty()) {
            return action;
        }

        action = actions.get("right_click");
        if (action != null && !action.isEmpty()) {
            return action;
        }

        return null;
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
