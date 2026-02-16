package github.nighter.smartspawner.spawner.gui.storage.ui;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.language.LanguageManager;
import github.nighter.smartspawner.spawner.gui.layout.GuiButton;
import github.nighter.smartspawner.spawner.gui.layout.GuiLayout;
import github.nighter.smartspawner.spawner.gui.layout.GuiLayoutConfig;
import github.nighter.smartspawner.spawner.gui.storage.StoragePageHolder;
import github.nighter.smartspawner.spawner.properties.VirtualInventory;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import github.nighter.smartspawner.Scheduler;
import github.nighter.smartspawner.Scheduler.Task;
import lombok.Getter;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.Bukkit;
import org.bukkit.Material;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SpawnerStorageUI {
    private static final int INVENTORY_SIZE = 54;

    private final SmartSpawner plugin;
    private final LanguageManager languageManager;
    @Getter
    private GuiLayoutConfig layoutConfig;

    // Precomputed buttons to avoid repeated creation
    private final Map<String, ItemStack> staticButtons;

    // Lightweight caches with better eviction strategies
    private final Map<String, ItemStack> navigationButtonCache;
    private final Map<String, ItemStack> pageIndicatorCache;

    // Cache expiry time reduced for more responsive updates
    private static final int MAX_CACHE_SIZE = 100;
    
    // Cache for title format to avoid repeated language lookups
    private String cachedStorageTitleFormat = null;

    // Cleanup task to remove stale entries from caches
    private Task cleanupTask;

    public SpawnerStorageUI(SmartSpawner plugin) {
        this.plugin = plugin;
        this.languageManager = plugin.getLanguageManager();
        this.layoutConfig = plugin.getGuiLayoutConfig();

        // Initialize caches with appropriate initial capacity
        this.staticButtons = new HashMap<>(8);
        this.navigationButtonCache = new ConcurrentHashMap<>(16);
        this.pageIndicatorCache = new ConcurrentHashMap<>(16);

        initializeStaticButtons();
        startCleanupTask();
    }

    public void reload() {
        // Reload layout configuration
        this.layoutConfig = plugin.getGuiLayoutConfig();

        // Clear caches to force reloading of buttons
        navigationButtonCache.clear();
        pageIndicatorCache.clear();
        staticButtons.clear();
        cachedStorageTitleFormat = null;

        // Reinitialize static buttons
        initializeStaticButtons();
    }

    private void initializeStaticButtons() {
        GuiLayout layout = layoutConfig.getCurrentLayout();

        // Create return button
        GuiButton returnButton = layout.getButton("return");
        if (returnButton != null) {
            staticButtons.put("return", createButton(
                    returnButton.getMaterial(),
                    languageManager.getGuiItemName("return_button.name"),
                    languageManager.getGuiItemLoreAsList("return_button.lore")
            ));
        }

        // Create take all button
        GuiButton takeAllButton = layout.getButton("take_all");
        if (takeAllButton != null) {
            staticButtons.put("takeAll", createButton(
                    takeAllButton.getMaterial(),
                    languageManager.getGuiItemName("take_all_button.name"),
                    languageManager.getGuiItemLoreAsList("take_all_button.lore")
            ));
        }

        // Create sort items button
        GuiButton sortItemsButton = layout.getButton("sort_items");
        if (sortItemsButton != null) {
            staticButtons.put("sortItems", createButton(
                    sortItemsButton.getMaterial(),
                    languageManager.getGuiItemName("sort_items_button.name"),
                    languageManager.getGuiItemLoreAsList("sort_items_button.lore")
            ));
        }

        // Create drop page button
        GuiButton dropPageButton = layout.getButton("drop_page");
        if (dropPageButton != null) {
            staticButtons.put("dropPage", createButton(
                    dropPageButton.getMaterial(),
                    languageManager.getGuiItemName("drop_page_button.name"),
                    languageManager.getGuiItemLoreAsList("drop_page_button.lore")
            ));
        }

        // Create item filter button
        GuiButton itemFilterButton = layout.getButton("item_filter");
        if (itemFilterButton != null) {
            staticButtons.put("itemFilter", createButton(
                    itemFilterButton.getMaterial(),
                    languageManager.getGuiItemName("item_filter_button.name"),
                    languageManager.getGuiItemLoreAsList("item_filter_button.lore")
            ));
        }

        // Note: Sell button is created dynamically in updateDynamicButtons() 
        // to show the current total sell price
    }

    /**
     * Gets the formatted title for storage GUI with page information.
     * Uses cached title format pattern for performance.
     * 
     * @param page Current page number
     * @param totalPages Total number of pages
     * @return Formatted title with page information
     */
    private String getStorageTitle(int page, int totalPages) {
        // Cache the title format pattern (not the filled title)
        if (cachedStorageTitleFormat == null) {
            cachedStorageTitleFormat = languageManager.getGuiTitle("gui_title_storage");
        }
        
        // Use placeholder replacement pattern similar to PricesGUI
        return languageManager.getGuiTitle("gui_title_storage", Map.of(
            "current_page", String.valueOf(page),
            "total_pages", String.valueOf(totalPages)
        ));
    }

    public Inventory createStorageInventory(SpawnerData spawner, int page, int totalPages) {
        // Get total pages efficiently
        if (totalPages == -1) {
            totalPages = calculateTotalPages(spawner);
        }

        // Clamp page number to valid range
        page = Math.max(1, Math.min(page, totalPages));

        // Create inventory with title including page info using placeholder-based format
        Inventory pageInv = Bukkit.createInventory(
                new StoragePageHolder(spawner, page, totalPages),
                INVENTORY_SIZE,
                getStorageTitle(page, totalPages)
        );

        // Populate the inventory
        updateDisplay(pageInv, spawner, page, totalPages);
        return pageInv;
    }

    public void updateDisplay(Inventory inventory, SpawnerData spawner, int page, int totalPages) {
        if (!spawner.getInventoryLock().tryLock()) {
            if (plugin.isDebugMode()) {
                plugin.debug("Skipping GUI update - inventory operation in progress for spawner " + spawner.getSpawnerId());
            }
            return;
        }

        if (totalPages == -1) {
            totalPages = calculateTotalPages(spawner);
        }

        // Track both changes and slots that need to be emptied
        Map<Integer, ItemStack> updates = new HashMap<>();
        Set<Integer> slotsToEmpty = new HashSet<>();

        // Clear storage area slots first
        for (int i = 0; i < StoragePageHolder.MAX_ITEMS_PER_PAGE; i++) {
            slotsToEmpty.add(i);
        }

        // Add items from virtual inventory
        addPageItems(updates, slotsToEmpty, spawner, page);

        // Add navigation buttons based on layout
        addNavigationButtons(updates, spawner, page, totalPages);

        // Apply all updates in a batch
        for (int slot : slotsToEmpty) {
            if (!updates.containsKey(slot)) {
                inventory.setItem(slot, null);
            }
        }

        for (Map.Entry<Integer, ItemStack> entry : updates.entrySet()) {
            inventory.setItem(entry.getKey(), entry.getValue());
        }

        // Update hologram if enabled
        if (plugin.getConfig().getBoolean("hologram.enabled", false)) {
            Scheduler.runLocationTask(spawner.getSpawnerLocation(), spawner::updateHologramData);
        }

        // Check if we need to update total pages
        StoragePageHolder holder = (StoragePageHolder) inventory.getHolder(false);
        assert holder != null;
        int oldUsedSlots = holder.getOldUsedSlots();
        int currentUsedSlots = spawner.getVirtualInventory().getUsedSlots();

        // Only recalculate total pages if there's a significant change
        if (oldUsedSlots != currentUsedSlots) {
            int newTotalPages = calculateTotalPages(spawner);
            holder.setTotalPages(newTotalPages);
            holder.updateOldUsedSlots();
        }
    }

    private void addPageItems(Map<Integer, ItemStack> updates, Set<Integer> slotsToEmpty,
                              SpawnerData spawner, int page) {
        try {
            // Get display items directly from VirtualInventory (source of truth)
            VirtualInventory virtualInv = spawner.getVirtualInventory();
            Map<Integer, ItemStack> displayItems = virtualInv.getDisplayInventory();

            if (displayItems.isEmpty()) {
                return;
            }

            // Calculate start index for current page
            int startIndex = (page - 1) * StoragePageHolder.MAX_ITEMS_PER_PAGE;

            // Add items for this page
            for (Map.Entry<Integer, ItemStack> entry : displayItems.entrySet()) {
                int globalIndex = entry.getKey();

                // Check if item belongs on this page
                if (globalIndex >= startIndex && globalIndex < startIndex + StoragePageHolder.MAX_ITEMS_PER_PAGE) {
                    int displaySlot = globalIndex - startIndex;
                    updates.put(displaySlot, entry.getValue());
                    slotsToEmpty.remove(displaySlot);
                }
            }
        } finally {
            spawner.getInventoryLock().unlock();
        }
    }

    private void addNavigationButtons(Map<Integer, ItemStack> updates, SpawnerData spawner, int page, int totalPages) {
        if (totalPages == -1) {
            totalPages = calculateTotalPages(spawner);
        }

        GuiLayout layout = layoutConfig.getCurrentLayout();

        // Add previous page button if not on first page and button is enabled
        if (page > 1 && layout.hasButton("previous_page")) {
            GuiButton prevButton = layout.getButton("previous_page");
            String cacheKey = "prev-" + (page - 1);
            ItemStack prevItem = navigationButtonCache.computeIfAbsent(
                    cacheKey, k -> createNavigationButton("previous", page - 1, prevButton.getMaterial()));
            updates.put(prevButton.getSlot(), prevItem);
        }

        // Add next page button if not on last page and button is enabled
        if (page < totalPages && layout.hasButton("next_page")) {
            GuiButton nextButton = layout.getButton("next_page");
            String cacheKey = "next-" + (page + 1);
            ItemStack nextItem = navigationButtonCache.computeIfAbsent(
                    cacheKey, k -> createNavigationButton("next", page + 1, nextButton.getMaterial()));
            updates.put(nextButton.getSlot(), nextItem);
        }

        // Add take all button if enabled
        if (layout.hasButton("take_all")) {
            GuiButton takeAllButton = layout.getButton("take_all");
            updates.put(takeAllButton.getSlot(), staticButtons.get("takeAll"));
        }

        // Add sort items button if enabled
        if (layout.hasButton("sort_items")) {
            GuiButton sortItemsButton = layout.getButton("sort_items");
            ItemStack sortButton = createSortButton(spawner, sortItemsButton.getMaterial());
            updates.put(sortItemsButton.getSlot(), sortButton);
        }

        // Add drop page button if enabled
        if (layout.hasButton("drop_page")) {
            GuiButton dropPageButton = layout.getButton("drop_page");
            updates.put(dropPageButton.getSlot(), staticButtons.get("dropPage"));
        }

        // Add item filter button if enabled
        if (layout.hasButton("item_filter")) {
            GuiButton itemFilterButton = layout.getButton("item_filter");
            updates.put(itemFilterButton.getSlot(), staticButtons.get("itemFilter"));
        }

        // Add return button if enabled
        if (layout.hasButton("return")) {
            GuiButton returnButton = layout.getButton("return");
            updates.put(returnButton.getSlot(), staticButtons.get("return"));
        }

        // Add sell button if shop integration is available and button is enabled
        if (layout.hasButton("sell_all")) {
            GuiButton sellButton = layout.getButton("sell_all");
            ItemStack sellIndicator = createSellButton(spawner, sellButton.getMaterial());
            updates.put(sellButton.getSlot(), sellIndicator);
        }

        // Add sell and exp button if shop integration is available and button is enabled
        if (layout.hasButton("sell_and_exp")) {
            GuiButton sellAndExpButton = layout.getButton("sell_and_exp");
            ItemStack sellAndExpIndicator = createSellAndExpButton(spawner, sellAndExpButton.getMaterial());
            updates.put(sellAndExpButton.getSlot(), sellAndExpIndicator);
        }
    }

    private int calculateTotalPages(SpawnerData spawner) {
        int usedSlots = spawner.getVirtualInventory().getUsedSlots();
        return Math.max(1, (int) Math.ceil((double) usedSlots / StoragePageHolder.MAX_ITEMS_PER_PAGE));
    }

    private ItemStack createButton(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (!lore.isEmpty()) {
                meta.setLore(lore);
            }
            item.setItemMeta(meta);
        }

        // Hide tooltip for BUNDLE material (prevents showing bundle contents)
        if (material == Material.BUNDLE) {
            github.nighter.smartspawner.nms.VersionInitializer.hideTooltip(item);
        }

        return item;
    }

    private ItemStack createNavigationButton(String type, int targetPage, Material material) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("target_page", String.valueOf(targetPage));

        String buttonName;
        String buttonKey;

        if (type.equals("previous")) {
            buttonKey = "navigation_button_previous";
        } else {
            buttonKey = "navigation_button_next";
        }

        buttonName = languageManager.getGuiItemName(buttonKey + ".name", placeholders);
        String[] buttonLore = languageManager.getGuiItemLore(buttonKey + ".lore", placeholders);

        return createButton(material, buttonName, Arrays.asList(buttonLore));
    }

    private ItemStack createSellButton(SpawnerData spawner, Material material) {
        // Create placeholders for total sell price
        Map<String, String> placeholders = new HashMap<>();
        // Check if sell value needs recalculation before displaying
        if (spawner.isSellValueDirty()) {
            spawner.recalculateSellValue();
        }
        double totalSellPrice = spawner.getAccumulatedSellValue();
        placeholders.put("total_sell_price", languageManager.formatNumber(totalSellPrice));
        
        String name = languageManager.getGuiItemName("sell_button.name", placeholders);
        List<String> lore = languageManager.getGuiItemLoreAsList("sell_button.lore");
        return createButton(material, name, lore);
    }

    private ItemStack createSellAndExpButton(SpawnerData spawner, Material material) {
        // Create placeholders for total sell price
        Map<String, String> placeholders = new HashMap<>();
        // Check if sell value needs recalculation before displaying
        if (spawner.isSellValueDirty()) {
            spawner.recalculateSellValue();
        }
        double totalSellPrice = spawner.getAccumulatedSellValue();
        placeholders.put("total_sell_price", languageManager.formatNumber(totalSellPrice));

        String name = languageManager.getGuiItemName("sell_and_exp_button.name", placeholders);
        List<String> lore = languageManager.getGuiItemLoreAsList("sell_and_exp_button.lore");
        return createButton(material, name, lore);
    }

    private ItemStack createSortButton(SpawnerData spawner, Material material) {
        Map<String, String> placeholders = new HashMap<>();

        // Get current sort item
        Material currentSort = spawner.getPreferredSortItem();

        // Get format strings from configuration
        String selectedItemFormat = languageManager.getGuiItemName("sort_items_button.selected_item");
        String unselectedItemFormat = languageManager.getGuiItemName("sort_items_button.unselected_item");
        String noneText = languageManager.getGuiItemName("sort_items_button.no_item");

        // Get available items from spawner drops
        StringBuilder availableItems = new StringBuilder();
        if (spawner.getLootConfig() != null && spawner.getLootConfig().getAllItems() != null) {
            boolean first = true;
            var sortedLoot = spawner.getLootConfig().getAllItems().stream()
                .sorted(Comparator.comparing(item -> item.material().name()))
                .toList();

            for (var lootItem : sortedLoot) {
                if (!first) availableItems.append("\n");
                String itemName = languageManager.getVanillaItemName(lootItem.material());
                String format = currentSort == lootItem.material() ? selectedItemFormat : unselectedItemFormat;
                
                // Replace {item_name} placeholder in format string
                String formattedItem = format.replace("{item_name}", itemName);
                availableItems.append(formattedItem);
                first = false;
            }
        }

        if (availableItems.isEmpty()) {
            availableItems.append(noneText);
        }

        placeholders.put("available_items", availableItems.toString());

        String name = languageManager.getGuiItemName("sort_items_button.name", placeholders);
        List<String> lore = languageManager.getGuiItemLoreWithMultilinePlaceholders("sort_items_button.lore", placeholders);
        return createButton(material, name, lore);
    }

    private void startCleanupTask() {
        cleanupTask = Scheduler.runTaskTimer(this::cleanupCaches, 20L * 30, 20L * 30); // Run every 30 seconds
    }

    public void cancelTasks() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
            cleanupTask = null;
        }
    }

    private void cleanupCaches() {
        // LRU-like cleanup for navigation buttons
        if (navigationButtonCache.size() > MAX_CACHE_SIZE) {
            int toRemove = navigationButtonCache.size() - (MAX_CACHE_SIZE / 2);
            List<String> keysToRemove = new ArrayList<>(navigationButtonCache.keySet());
            for (int i = 0; i < Math.min(toRemove, keysToRemove.size()); i++) {
                navigationButtonCache.remove(keysToRemove.get(i));
            }
        }

        // LRU-like cleanup for page indicators
        if (pageIndicatorCache.size() > MAX_CACHE_SIZE) {
            int toRemove = pageIndicatorCache.size() - (MAX_CACHE_SIZE / 2);
            List<String> keysToRemove = new ArrayList<>(pageIndicatorCache.keySet());
            for (int i = 0; i < Math.min(toRemove, keysToRemove.size()); i++) {
                pageIndicatorCache.remove(keysToRemove.get(i));
            }
        }
    }

    public void cleanup() {
        navigationButtonCache.clear();
        pageIndicatorCache.clear();
        cachedStorageTitleFormat = null;

        // Cancel scheduled tasks
        cancelTasks();

        // Re-initialize static buttons (just in case language has changed)
        initializeStaticButtons();
    }
}
