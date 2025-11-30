package github.nighter.smartspawner.spawner.gui.storage;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.language.MessageService;
import github.nighter.smartspawner.spawner.gui.layout.GuiLayoutConfig;
import github.nighter.smartspawner.spawner.gui.storage.filter.FilterConfigUI;
import github.nighter.smartspawner.spawner.gui.storage.ui.SpawnerStorageUI;
import github.nighter.smartspawner.spawner.gui.storage.utils.ItemClickHandler;
import github.nighter.smartspawner.spawner.gui.storage.utils.ItemMoveHelper;
import github.nighter.smartspawner.spawner.gui.storage.utils.ItemMoveResult;
import github.nighter.smartspawner.spawner.gui.main.SpawnerMenuUI;
import github.nighter.smartspawner.spawner.gui.synchronization.SpawnerGuiViewManager;
import github.nighter.smartspawner.spawner.gui.layout.GuiLayout;
import github.nighter.smartspawner.spawner.lootgen.loot.LootItem;
import github.nighter.smartspawner.spawner.data.SpawnerManager;
import github.nighter.smartspawner.spawner.properties.VirtualInventory;
import github.nighter.smartspawner.language.LanguageManager;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import github.nighter.smartspawner.spawner.sell.SpawnerSellManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.util.Vector;
import org.bukkit.World;
import org.bukkit.entity.Item;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class SpawnerStorageAction implements Listener {
    private final SmartSpawner plugin;
    private final LanguageManager languageManager;
    private final SpawnerMenuUI spawnerMenuUI;
    private final SpawnerGuiViewManager spawnerGuiViewManager;
    private final MessageService messageService;
    private final FilterConfigUI filterConfigUI;
    private final SpawnerSellManager spawnerSellManager;
    private final SpawnerManager spawnerManager;

    private static final int INVENTORY_SIZE = 54;
    private static final int STORAGE_SLOTS = 45;

    private record TransferResult(boolean anyItemMoved, boolean inventoryFull, int totalMoved) {}
    private final Map<ClickType, ItemClickHandler> clickHandlers;
    private final Map<UUID, Long> lastItemClickTime = new ConcurrentHashMap<>();
    private final Random random = ThreadLocalRandom.current();
    private static final long CLICK_DELAY_MS = 300;
    private final Random random = new Random();
    private GuiLayout layout;

    public SpawnerStorageAction(SmartSpawner plugin) {
        this.plugin = plugin;
        this.languageManager = plugin.getLanguageManager();
        this.clickHandlers = initializeClickHandlers();
        this.spawnerMenuUI = plugin.getSpawnerMenuUI();
        this.spawnerGuiViewManager = plugin.getSpawnerGuiViewManager();
        this.messageService = plugin.getMessageService();
        this.filterConfigUI = plugin.getFilterConfigUI();
        this.spawnerSellManager = plugin.getSpawnerSellManager();
        this.spawnerManager = plugin.getSpawnerManager();
        loadConfig();
    }

    public void loadConfig() {
        GuiLayoutConfig guiLayoutConfig = plugin.getGuiLayoutConfig();
        layout = guiLayoutConfig.getCurrentLayout();
    }

    private Map<ClickType, ItemClickHandler> initializeClickHandlers() {
        Map<ClickType, ItemClickHandler> handlers = new EnumMap<>(ClickType.class);
        handlers.put(ClickType.RIGHT, (player, inv, slot, item, spawner) ->
                takeSingleItem(player, inv, slot, item, spawner, true));
        handlers.put(ClickType.LEFT, (player, inv, slot, item, spawner) ->
                takeSingleItem(player, inv, slot, item, spawner, false));
        return Collections.unmodifiableMap(handlers);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) ||
                !(event.getInventory().getHolder(false) instanceof StoragePageHolder holder)) {
            return;
        }

        SpawnerData spawner = holder.getSpawnerData();
        int slot = event.getRawSlot();

        // Cancel event immediately to prevent any vanilla behavior
        event.setCancelled(true);

        // CRITICAL: Check if inventoryLock is available before ANY operation
        if (!tryAcquireInventoryLock(spawner, player)) {
            return;
        }

        try {
            // Verify GUI is still valid and synced
            if (!isGuiSyncValid(player, holder, spawner)) {
                player.closeInventory();
                return;
            }

            if (event.getAction() == InventoryAction.DROP_ONE_SLOT ||
                    event.getAction() == InventoryAction.DROP_ALL_SLOT) {

                if (slot >= 0 && slot < STORAGE_SLOTS) {
                    ItemStack clickedItem = event.getCurrentItem();
                    if (clickedItem != null && clickedItem.getType() != Material.AIR) {
                        boolean dropStack = event.getAction() == InventoryAction.DROP_ALL_SLOT;
                        handleItemDrop(player, spawner, event.getInventory(), slot, clickedItem, dropStack);
                        return;
                    }
                }
            }

            if (slot < 0 || slot >= INVENTORY_SIZE) {
                return;
            }

            if (isControlSlot(slot)) {
                handleControlSlotClick(player, slot, holder, spawner, event.getInventory(), layout);
                return;
            }

            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem == null || clickedItem.getType() == Material.AIR) {
                return;
            }

            ItemClickHandler handler = clickHandlers.get(event.getClick());
            if (handler != null) {
                handler.handle(player, event.getInventory(), slot, clickedItem, spawner);
            }
        } finally {
            // CRITICAL: Always release locks in reverse order (LIFO - Last In First Out)
            // This matches the acquisition order and prevents deadlock
            spawner.getInventoryLock().unlock();
            spawner.getLootGenerationLock().unlock();
        }
    }

    private void handleControlSlotClick(Player player, int slot, StoragePageHolder holder,
                                        SpawnerData spawner, Inventory inventory, GuiLayout layout) {
        Optional<String> buttonTypeOpt = layout.getButtonTypeAtSlot(slot);
        if (buttonTypeOpt.isEmpty()) {
            return;
        }

        String buttonType = buttonTypeOpt.get();

        switch (buttonType) {
            case "sort_items":
                handleSortItemsClick(player, spawner, inventory);
                break;
            case "item_filter":
                openFilterConfig(player, spawner);
                break;
            case "previous_page":
                if (holder.getCurrentPage() > 1) {
                    updatePageContent(player, spawner, holder.getCurrentPage() - 1, inventory, true);
                }
                break;
            case "take_all":
                handleTakeAllItems(player, inventory);
                break;
            case "next_page":
                if (holder.getCurrentPage() < holder.getTotalPages()) {
                    updatePageContent(player, spawner, holder.getCurrentPage() + 1, inventory, true);
                }
                break;
            case "drop_page":
                handleDropPageItems(player, spawner, inventory);
                break;
            case "sell_all":
                if (plugin.hasSellIntegration()) {
                    if (!player.hasPermission("smartspawner.sellall")) {
                        messageService.sendMessage(player, "no_permission");
                        return;
                    }
                    if (isClickTooFrequent(player)) {
                        return;
                    }
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                    spawnerSellManager.sellAllItems(player, spawner);
                }
                break;
            case "return":
                openMainMenu(player, spawner);
                break;
        }
    }

    private boolean isControlSlot(int slot) {
        return layout != null && layout.isSlotUsed(slot);
    }

    /**
     * Try to acquire both lootGenerationLock and inventoryLock with timeout to prevent deadlock.
     * CRITICAL: Must acquire locks in consistent order to prevent deadlock:
     * 1. lootGenerationLock (to ensure no loot is being added)
     * 2. inventoryLock (to ensure VirtualInventory consistency)
     *
     * @return true if both locks acquired, false otherwise
     */
    private boolean tryAcquireInventoryLock(SpawnerData spawner, Player player) {
        try {
            // STEP 1: Try to acquire lootGenerationLock first
            // This ensures no loot is currently being generated/added to VirtualInventory
            if (!spawner.getLootGenerationLock().tryLock(50, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                // Loot generation is in progress - must wait for it to complete
                return false;
            }

            // STEP 2: Try to acquire inventoryLock
            try {
                if (!spawner.getInventoryLock().tryLock(50, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                    // Release lootGenerationLock before returning
                    spawner.getLootGenerationLock().unlock();
                    return false;
                }
            } catch (InterruptedException e) {
                // Failed to acquire inventoryLock - release lootGenerationLock
                spawner.getLootGenerationLock().unlock();
                Thread.currentThread().interrupt();
                return false;
            }

            // Both locks acquired successfully
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Validate that GUI is properly synced with VirtualInventory (source of truth).
     * This prevents race conditions where GUI shows stale data.
     *
     * CRITICAL: This is called AFTER acquiring lootGenerationLock, which ensures:
     * 1. No loot is currently being added to VirtualInventory
     * 2. Any pending loot generation has completed
     * 3. GUI updates from loot generation have been dispatched
     */
    private boolean isGuiSyncValid(Player player, StoragePageHolder holder, SpawnerData spawner) {
        // Verify holder belongs to this spawner
        if (!holder.getSpawnerData().getSpawnerId().equals(spawner.getSpawnerId())) {
            plugin.getLogger().warning("GUI sync error: holder spawner mismatch for player " + player.getName());
            return false;
        }

        // Verify current inventory matches holder
        Inventory currentInv = player.getOpenInventory().getTopInventory();
        if (!(currentInv.getHolder(false) instanceof StoragePageHolder currentHolder)) {
            return false;
        }

        if (!currentHolder.getSpawnerData().getSpawnerId().equals(spawner.getSpawnerId())) {
            return false;
        }

        // Verify that holder's cached state matches VirtualInventory
        // This ensures GUI has been updated with latest loot generation
        int actualUsedSlots = spawner.getVirtualInventory().getUsedSlots();
        int cachedUsedSlots = holder.getOldUsedSlots();

        // If there's a significant difference, GUI is out of sync
        // Allow small differences due to concurrent operations
        if (Math.abs(actualUsedSlots - cachedUsedSlots) > StoragePageHolder.MAX_ITEMS_PER_PAGE) {
            if (plugin.isDebugMode()) {
                plugin.debug("GUI out of sync for player " + player.getName() +
                           ": actual=" + actualUsedSlots + ", cached=" + cachedUsedSlots);
            }
            // Trigger a refresh from VirtualInventory
            refreshGuiFromVirtualInventory(player, spawner, currentInv);
            return false;
        }

        // All validations passed - GUI is synced with VirtualInventory (source of truth)
        return true;
    }

    private void handleItemDrop(Player player, SpawnerData spawner, Inventory inventory,
                                int slot, ItemStack item, boolean dropStack) {
        // Note: Lock is already held by caller (onInventoryClick)

        // CRITICAL: Verify item in slot matches what VirtualInventory expects
        ItemStack actualItem = inventory.getItem(slot);
        if (actualItem == null || !actualItem.isSimilar(item) || actualItem.getAmount() != item.getAmount()) {
            // Desync detected - refresh GUI from VirtualInventory (source of truth)
            plugin.getLogger().warning("Item desync detected in slot " + slot + " for player " + player.getName());
            refreshGuiFromVirtualInventory(player, spawner, inventory);
            return;
        }

        int amountToDrop = dropStack ? item.getAmount() : 1;

        ItemStack droppedItem = item.clone();
        droppedItem.setAmount(Math.min(amountToDrop, item.getAmount()));
        List<ItemStack> itemsToRemove = new ArrayList<>();
        itemsToRemove.add(droppedItem);

        // Remove from VirtualInventory FIRST (source of truth)
        spawner.removeItemsAndUpdateSellValue(itemsToRemove);

        int remaining = item.getAmount() - amountToDrop;
        if (remaining <= 0) {
            inventory.setItem(slot, null);
        } else {
            ItemStack remainingItem = item.clone();
            remainingItem.setAmount(remaining);
            inventory.setItem(slot, remainingItem);
        }

        Location playerLoc = player.getLocation();
        World world = player.getWorld();
        UUID playerUUID = player.getUniqueId();

        double yaw = Math.toRadians(playerLoc.getYaw());
        double pitch = Math.toRadians(playerLoc.getPitch());

        double sinYaw = -Math.sin(yaw);
        double cosYaw = Math.cos(yaw);
        double cosPitch = Math.cos(pitch);
        double sinPitch = -Math.sin(pitch);

        Location dropLocation = playerLoc.clone();
        dropLocation.add(sinYaw * 0.3, 1.2, cosYaw * 0.3);
        Item droppedItemWorld = world.dropItem(dropLocation, droppedItem, drop -> {
            drop.setThrower(playerUUID);
            drop.setPickupDelay(40);
        });

        Vector velocity = new Vector(
                sinYaw * cosPitch * 0.3 + (random.nextDouble() - 0.5) * 0.1,
                sinPitch * 0.3 + 0.1 + (random.nextDouble() - 0.5) * 0.1,
                cosYaw * cosPitch * 0.3 + (random.nextDouble() - 0.5) * 0.1
        );
        droppedItemWorld.setVelocity(velocity);

        // Log item drop action
        if (plugin.getSpawnerActionLogger() != null) {
            plugin.getSpawnerActionLogger().log(github.nighter.smartspawner.logging.SpawnerEventType.SPAWNER_ITEM_DROP, builder ->
                    builder.player(player.getName(), player.getUniqueId())
                            .location(spawner.getSpawnerLocation())
                            .entityType(spawner.getEntityType())
                            .metadata("item_type", droppedItem.getType().name())
                            .metadata("amount_dropped", droppedItem.getAmount())
                            .metadata("drop_stack", dropStack)
            );
        }

        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.2f);

        spawner.updateHologramData();

        StoragePageHolder holder = (StoragePageHolder) inventory.getHolder(false);
        if (holder != null) {
            // Only recalculate and update title if page count might have changed
            // This optimization avoids expensive operations on every single item drop
            int oldTotalPages = holder.getTotalPages();
            int newTotalPages = calculateTotalPages(spawner);

            if (oldTotalPages != newTotalPages) {
                // Page count changed - update holder and title
                int currentPage = holder.getCurrentPage();
                int adjustedPage = Math.max(1, Math.min(currentPage, newTotalPages));

                holder.setTotalPages(newTotalPages);
                if (adjustedPage != currentPage) {
                    holder.setCurrentPage(adjustedPage);
                }

                // Update the inventory title to reflect new page count
                updateInventoryTitle(player, spawner, adjustedPage, newTotalPages);
            }

            // CRITICAL: Update oldUsedSlots BEFORE calling updateSpawnerMenuViewers
            // This ensures other viewers get the correct page calculation
            holder.updateOldUsedSlots();

            spawnerGuiViewManager.updateSpawnerMenuViewers(spawner);
            if (!spawner.isInteracted()) {
                spawner.markInteracted();
            }
            if (spawner.getMaxSpawnerLootSlots() > holder.getOldUsedSlots() && spawner.getIsAtCapacity()) {
                spawner.setIsAtCapacity(false);
            }
        }
    }

    private void handleDropPageItems(Player player, SpawnerData spawner, Inventory inventory) {
        // Note: Lock is already held by caller (onInventoryClick via handleControlSlotClick)

        if (isClickTooFrequent(player)) {
            return;
        }

        StoragePageHolder holder = (StoragePageHolder) inventory.getHolder(false);
        if (holder == null) {
            return;
        }

        List<ItemStack> pageItems = new ArrayList<>();
        int itemsFoundCount = 0;

        // CRITICAL: Collect items from GUI display (which should match VirtualInventory)
        for (int i = 0; i < STORAGE_SLOTS; i++) {
            ItemStack item = inventory.getItem(i);
            if (item != null && item.getType() != Material.AIR) {
                pageItems.add(item.clone());
                itemsFoundCount += item.getAmount();
                // Clear GUI slot immediately
                inventory.setItem(i, null);
            }
        }

        if (pageItems.isEmpty()) {
            messageService.sendMessage(player, "no_items_to_drop");
            return;
        }

        final int itemsFound = itemsFoundCount;

        // CRITICAL: Remove from VirtualInventory FIRST (source of truth)
        // This operation is atomic and thread-safe
        spawner.removeItemsAndUpdateSellValue(pageItems);

        dropItemsInDirection(player, pageItems);

        int newTotalPages = calculateTotalPages(spawner);
        if (holder.getCurrentPage() > newTotalPages) {
            holder.setCurrentPage(Math.max(1, newTotalPages));
        }
        holder.setTotalPages(newTotalPages);
        holder.updateOldUsedSlots();

        spawner.updateHologramData();
        spawnerGuiViewManager.updateSpawnerMenuViewers(spawner);

        if (spawner.getMaxSpawnerLootSlots() > holder.getOldUsedSlots() && spawner.getIsAtCapacity()) {
            spawner.setIsAtCapacity(false);
        }
        if (!spawner.isInteracted()) {
            spawner.markInteracted();
        }

        // Log drop page items action
        if (plugin.getSpawnerActionLogger() != null) {
            plugin.getSpawnerActionLogger().log(github.nighter.smartspawner.logging.SpawnerEventType.SPAWNER_DROP_PAGE_ITEMS, builder ->
                    builder.player(player.getName(), player.getUniqueId())
                            .location(spawner.getSpawnerLocation())
                            .entityType(spawner.getEntityType())
                            .metadata("items_dropped", itemsFound)
                            .metadata("page_number", holder.getCurrentPage())
            );
        }

        updatePageContent(player, spawner, holder.getCurrentPage(), inventory, false);
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 0.8f);
    }

    private void dropItemsInDirection(Player player, List<ItemStack> items) {
        if (items.isEmpty()) {
            return;
        }

        Location playerLoc = player.getLocation();
        World world = player.getWorld();
        UUID playerUUID = player.getUniqueId();

        double yaw = Math.toRadians(playerLoc.getYaw());
        double pitch = Math.toRadians(playerLoc.getPitch());

        double sinYaw = -Math.sin(yaw);
        double cosYaw = Math.cos(yaw);
        double cosPitch = Math.cos(pitch);
        double sinPitch = -Math.sin(pitch);

        Location dropLocation = playerLoc.clone();
        dropLocation.add(sinYaw * 0.3, 1.2, cosYaw * 0.3);

        for (ItemStack item : items) {
            Item droppedItem = world.dropItem(dropLocation, item, drop -> {
                drop.setThrower(playerUUID);
                drop.setPickupDelay(40);
            });

            Vector velocity = new Vector(
                    sinYaw * cosPitch * 0.3 + (random.nextDouble() - 0.5) * 0.1,
                    sinPitch * 0.3 + 0.1 + (random.nextDouble() - 0.5) * 0.1,
                    cosYaw * cosPitch * 0.3 + (random.nextDouble() - 0.5) * 0.1
            );

            droppedItem.setVelocity(velocity);
        }
    }


    private void openFilterConfig(Player player, SpawnerData spawner) {
        if (isClickTooFrequent(player)) {
            return;
        }
        filterConfigUI.openFilterConfigGUI(player, spawner);
    }

    private void takeSingleItem(Player player, Inventory sourceInv, int slot, ItemStack item,
                                SpawnerData spawner, boolean singleItem) {
        // Note: Lock is already held by caller (onInventoryClick)

        // CRITICAL: Verify item in slot matches what we expect
        ItemStack actualItem = sourceInv.getItem(slot);
        if (actualItem == null || !actualItem.isSimilar(item) || actualItem.getAmount() != item.getAmount()) {
            // Desync detected - refresh GUI from VirtualInventory
            plugin.getLogger().warning("Item desync detected in takeSingleItem for player " + player.getName());
            refreshGuiFromVirtualInventory(player, spawner, sourceInv);
            return;
        }

        PlayerInventory playerInv = player.getInventory();
        VirtualInventory virtualInv = spawner.getVirtualInventory();

        ItemMoveResult result = ItemMoveHelper.moveItems(
                item,
                singleItem ? 1 : item.getAmount(),
                playerInv,
                virtualInv
        );
        if (result.amountMoved() > 0) {
            // Update GUI slot to match VirtualInventory state
            updateInventorySlot(sourceInv, slot, item, result.amountMoved());

            // CRITICAL: Remove from VirtualInventory (source of truth) - this updates sell value atomically
            spawner.removeItemsAndUpdateSellValue(result.movedItems());

            player.updateInventory();

            spawner.updateHologramData();

            StoragePageHolder holder = (StoragePageHolder) sourceInv.getHolder(false);
            if (holder != null) {
                // Only recalculate and update title if page count might have changed
                // This optimization avoids expensive operations on every single item take
                int oldTotalPages = holder.getTotalPages();
                int newTotalPages = calculateTotalPages(spawner);

                if (oldTotalPages != newTotalPages) {
                    // Page count changed - update holder and title
                    int currentPage = holder.getCurrentPage();
                    int adjustedPage = Math.max(1, Math.min(currentPage, newTotalPages));

                    holder.setTotalPages(newTotalPages);
                    if (adjustedPage != currentPage) {
                        holder.setCurrentPage(adjustedPage);
                    }

                    // Update the inventory title to reflect new page count
                    updateInventoryTitle(player, spawner, adjustedPage, newTotalPages);
                }

                // CRITICAL: Update oldUsedSlots BEFORE calling updateSpawnerMenuViewers
                // This ensures other viewers get the correct page calculation
                holder.updateOldUsedSlots();

                spawnerGuiViewManager.updateSpawnerMenuViewers(spawner);

                if (spawner.getMaxSpawnerLootSlots() > holder.getOldUsedSlots() && spawner.getIsAtCapacity()) {
                    spawner.setIsAtCapacity(false);
                }
            }
        } else {
            messageService.sendMessage(player, "inventory_full");
        }
    }

    private static void updateInventorySlot(Inventory sourceInv, int slot, ItemStack item, int amountMoved) {
        if (amountMoved >= item.getAmount()) {
            sourceInv.setItem(slot, null);
            return;
        }

        ItemStack remaining = item.clone();
        remaining.setAmount(item.getAmount() - amountMoved);
        sourceInv.setItem(slot, remaining);
    }

    private void updatePageContent(Player player, SpawnerData spawner, int newPage, Inventory inventory, boolean uiClickSound) {
        SpawnerStorageUI spawnerStorageUI = plugin.getSpawnerStorageUI();
        StoragePageHolder holder = (StoragePageHolder) inventory.getHolder(false);

        int totalPages = calculateTotalPages(spawner);

        assert holder != null;
        holder.setTotalPages(totalPages);
        holder.setCurrentPage(newPage);
        holder.updateOldUsedSlots();

        spawnerStorageUI.updateDisplay(inventory, spawner, newPage, totalPages);

        updateInventoryTitle(player, spawner, newPage, totalPages);

        if (uiClickSound) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
        }
    }

    private int calculateTotalPages(SpawnerData spawner) {
        int usedSlots = spawner.getVirtualInventory().getUsedSlots();
        return Math.max(1, (int) Math.ceil((double) usedSlots / StoragePageHolder.MAX_ITEMS_PER_PAGE));
    }

    private void updateInventoryTitle(Player player, SpawnerData spawner, int page, int totalPages) {
        String newTitle = languageManager.getGuiTitle("gui_title_storage", Map.of(
                "current_page", String.valueOf(page),
                "total_pages", String.valueOf(totalPages)
        ));

        try {
            player.getOpenInventory().setTitle(newTitle);
        } catch (Exception e) {
            openLootPage(player, spawner, page);
        }
    }

    private boolean isClickTooFrequent(Player player) {
        long now = System.currentTimeMillis();
        long last = lastItemClickTime.getOrDefault(player.getUniqueId(), 0L);
        lastItemClickTime.put(player.getUniqueId(), now);
        return (now - last) < CLICK_DELAY_MS;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        lastItemClickTime.remove(playerId);
    }

    private void openMainMenu(Player player, SpawnerData spawner) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
        if (spawner.isInteracted()){
            spawnerManager.markSpawnerModified(spawner.getSpawnerId());
            spawner.clearInteracted();
        }

        // Check if player is Bedrock and use appropriate menu
        if (isBedrockPlayer(player)) {
            if (plugin.getSpawnerMenuFormUI() != null) {
                plugin.getSpawnerMenuFormUI().openSpawnerForm(player, spawner);
            } else {
                // Fallback to standard GUI if FormUI not available
                spawnerMenuUI.openSpawnerMenu(player, spawner, true);
            }
        } else {
            spawnerMenuUI.openSpawnerMenu(player, spawner, true);
        }
    }

    private boolean isBedrockPlayer(Player player) {
        if (plugin.getIntegrationManager() == null ||
                plugin.getIntegrationManager().getFloodgateHook() == null) {
            return false;
        }
        return plugin.getIntegrationManager().getFloodgateHook().isBedrockPlayer(player);
    }

    private void handleSortItemsClick(Player player, SpawnerData spawner, Inventory inventory) {
        // Note: Lock is already held by caller (onInventoryClick via handleControlSlotClick)

        if (isClickTooFrequent(player)) {
            return;
        }

        // Validate loot config
        if (spawner.getLootConfig() == null || spawner.getLootConfig().getAllItems() == null) {
            return;
        }

        var lootItems = spawner.getLootConfig().getAllItems();
        if (lootItems.isEmpty()) {
            return;
        }

        // Get current sort item
        Material currentSort = spawner.getPreferredSortItem();

        // Build sorted list of available materials
        var sortedLoot = lootItems.stream()
                .map(LootItem::material)
                .distinct() // Remove duplicates if any
                .sorted(Comparator.comparing(Material::name))
                .toList();

        if (sortedLoot.isEmpty()) {
            return;
        }

        // Find next sort item
        Material nextSort;

        if (currentSort == null) {
            // No current sort, select first item
            nextSort = sortedLoot.getFirst();
        } else {
            // Find current item index
            int currentIndex = sortedLoot.indexOf(currentSort);

            if (currentIndex == -1) {
                // Current sort item not in list anymore, reset to first
                nextSort = sortedLoot.getFirst();
            } else {
                // Select next item (wrap around to first if at end)
                int nextIndex = (currentIndex + 1) % sortedLoot.size();
                nextSort = sortedLoot.get(nextIndex);
            }
        }

        // Set new sort preference
        spawner.setPreferredSortItem(nextSort);

        // Mark spawner as modified to save the preference
        if (!spawner.isInteracted()) {
            spawner.markInteracted();
        }
        spawnerManager.queueSpawnerForSaving(spawner.getSpawnerId());

        // CRITICAL: Re-sort VirtualInventory (source of truth)
        // This operation is atomic and thread-safe
        spawner.getVirtualInventory().sortItems(nextSort);

        // Update GUI display to reflect VirtualInventory state
        StoragePageHolder holder = (StoragePageHolder) inventory.getHolder(false);
        if (holder != null) {
            updatePageContent(player, spawner, holder.getCurrentPage(), inventory, false);
        }

        // Play sound and show feedback
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);

        // Log items sort action
        if (plugin.getSpawnerActionLogger() != null) {
            plugin.getSpawnerActionLogger().log(github.nighter.smartspawner.logging.SpawnerEventType.SPAWNER_ITEMS_SORT, builder ->
                    builder.player(player.getName(), player.getUniqueId())
                            .location(spawner.getSpawnerLocation())
                            .entityType(spawner.getEntityType())
                            .metadata("sort_item", nextSort.name())
                            .metadata("previous_sort", currentSort != null ? currentSort.name() : "none")
            );
        }
    }

    private void openLootPage(Player player, SpawnerData spawner, int page) {
        SpawnerStorageUI spawnerStorageUI = plugin.getSpawnerStorageUI();
        int totalPages = calculateTotalPages(spawner);
        final int finalPage = Math.max(1, Math.min(page, totalPages));
        Inventory pageInventory = spawnerStorageUI.createStorageInventory(spawner, finalPage, totalPages);

        // Log storage GUI opening
        if (plugin.getSpawnerActionLogger() != null) {
            plugin.getSpawnerActionLogger().log(github.nighter.smartspawner.logging.SpawnerEventType.SPAWNER_STORAGE_OPEN, builder ->
                    builder.player(player.getName(), player.getUniqueId())
                            .location(spawner.getSpawnerLocation())
                            .entityType(spawner.getEntityType())
                            .metadata("page", finalPage)
                            .metadata("total_pages", totalPages)
            );
        }

        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
        player.openInventory(pageInventory);
    }

    public void handleTakeAllItems(Player player, Inventory sourceInventory) {
        // Note: Lock is already held by caller (onInventoryClick via handleControlSlotClick)

        if (isClickTooFrequent(player)) {
            return;
        }
        StoragePageHolder holder = (StoragePageHolder) sourceInventory.getHolder(false);
        SpawnerData spawner = holder.getSpawnerData();
        VirtualInventory virtualInv = spawner.getVirtualInventory();

        // CRITICAL: Collect items from GUI (which should be synced with VirtualInventory)
        Map<Integer, ItemStack> sourceItems = new HashMap<>();
        for (int i = 0; i < STORAGE_SLOTS; i++) {
            ItemStack item = sourceInventory.getItem(i);
            if (item != null && item.getType() != Material.AIR) {
                sourceItems.put(i, item.clone());
            }
        }

        if (sourceItems.isEmpty()) {
            messageService.sendMessage(player, "no_items_to_take");
            return;
        }

        // Transfer items and update VirtualInventory atomically
        TransferResult result = transferItems(player, sourceInventory, sourceItems, virtualInv);
        sendTransferMessage(player, result);
        player.updateInventory();

        if (result.anyItemMoved) {
            int newTotalPages = calculateTotalPages(spawner);
            int currentPage = holder.getCurrentPage();

            // Clamp current page to valid range (e.g., if on page 6 but only 5 pages remain)
            int adjustedPage = Math.max(1, Math.min(currentPage, newTotalPages));

            // Update holder with new total pages and adjusted current page
            holder.setTotalPages(newTotalPages);
            if (adjustedPage != currentPage) {
                holder.setCurrentPage(adjustedPage);
                // Refresh display to show the correct page content
                SpawnerStorageUI spawnerStorageUI = plugin.getSpawnerStorageUI();
                spawnerStorageUI.updateDisplay(sourceInventory, spawner, adjustedPage, newTotalPages);
            }

            // Update the inventory title to reflect new page count
            updateInventoryTitle(player, spawner, adjustedPage, newTotalPages);

            spawnerGuiViewManager.updateSpawnerMenuViewers(spawner);

            if (spawner.getMaxSpawnerLootSlots() > holder.getOldUsedSlots() && spawner.getIsAtCapacity()) {
                spawner.setIsAtCapacity(false);
            }
            if (!spawner.isInteracted()) {
                spawner.markInteracted();
            }

            // Log take all items action
            if (plugin.getSpawnerActionLogger() != null) {
                int itemsLeft = spawner.getVirtualInventory().getUsedSlots();
                plugin.getSpawnerActionLogger().log(github.nighter.smartspawner.logging.SpawnerEventType.SPAWNER_ITEM_TAKE_ALL, builder ->
                        builder.player(player.getName(), player.getUniqueId())
                                .location(spawner.getSpawnerLocation())
                                .entityType(spawner.getEntityType())
                                .metadata("items_taken", result.totalMoved)
                                .metadata("items_left", itemsLeft)
                );
            }
        }
    }

    private TransferResult transferItems(Player player, Inventory sourceInventory,
                                         Map<Integer, ItemStack> sourceItems, VirtualInventory virtualInv) {
        boolean anyItemMoved = false;
        boolean inventoryFull = false;
        PlayerInventory playerInv = player.getInventory();
        int totalAmountMoved = 0;
        List<ItemStack> itemsToRemove = new ArrayList<>();

        for (Map.Entry<Integer, ItemStack> entry : sourceItems.entrySet()) {
            int sourceSlot = entry.getKey();
            ItemStack itemToMove = entry.getValue();

            int amountToMove = itemToMove.getAmount();
            int amountMoved = 0;

            for (int i = 0; i < 36 && amountToMove > 0; i++) {
                ItemStack targetItem = playerInv.getItem(i);

                if (targetItem == null || targetItem.getType() == Material.AIR) {
                    ItemStack newStack = itemToMove.clone();
                    newStack.setAmount(Math.min(amountToMove, itemToMove.getMaxStackSize()));
                    playerInv.setItem(i, newStack);
                    amountMoved += newStack.getAmount();
                    amountToMove -= newStack.getAmount();
                    anyItemMoved = true;
                }
                else if (targetItem.isSimilar(itemToMove)) {
                    int spaceInStack = targetItem.getMaxStackSize() - targetItem.getAmount();
                    if (spaceInStack > 0) {
                        int addAmount = Math.min(spaceInStack, amountToMove);
                        targetItem.setAmount(targetItem.getAmount() + addAmount);
                        amountMoved += addAmount;
                        amountToMove -= addAmount;
                        anyItemMoved = true;
                    }
                }
            }

            if (amountMoved > 0) {
                totalAmountMoved += amountMoved;

                ItemStack movedItem = itemToMove.clone();
                movedItem.setAmount(amountMoved);
                itemsToRemove.add(movedItem);

                if (amountMoved == itemToMove.getAmount()) {
                    sourceInventory.setItem(sourceSlot, null);
                } else {
                    ItemStack remaining = itemToMove.clone();
                    remaining.setAmount(itemToMove.getAmount() - amountMoved);
                    sourceInventory.setItem(sourceSlot, remaining);
                    inventoryFull = true;
                }
            }

            if (inventoryFull) {
                break;
            }
        }

        // CRITICAL: Update VirtualInventory atomically (source of truth)
        if (!itemsToRemove.isEmpty()) {
            StoragePageHolder holder = (StoragePageHolder) sourceInventory.getHolder(false);
            SpawnerData spawnerData = holder.getSpawnerData();

            // This operation is atomic and updates sell value
            spawnerData.removeItemsAndUpdateSellValue(itemsToRemove);
            spawnerData.updateHologramData();

            // Update holder's cached state
            holder.updateOldUsedSlots();
        }

        return new TransferResult(anyItemMoved, inventoryFull, totalAmountMoved);
    }


    private void sendTransferMessage(Player player, TransferResult result) {
        if (!result.anyItemMoved) {
            messageService.sendMessage(player, "inventory_full");
        } else {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("amount", String.valueOf(result.totalMoved));
            messageService.sendMessage(player, "take_all_items", placeholders);
        }
    }

    private void refreshGuiFromVirtualInventory(Player player, SpawnerData spawner, Inventory inventory) {
        StoragePageHolder holder = (StoragePageHolder) inventory.getHolder(false);
        if (holder == null) {
            return;
        }

        // Recalculate pages from VirtualInventory
        int totalPages = calculateTotalPages(spawner);
        int currentPage = Math.max(1, Math.min(holder.getCurrentPage(), totalPages));

        holder.setTotalPages(totalPages);
        holder.setCurrentPage(currentPage);
        holder.updateOldUsedSlots();

        // Refresh display from VirtualInventory
        SpawnerStorageUI spawnerStorageUI = plugin.getSpawnerStorageUI();
        spawnerStorageUI.updateDisplay(inventory, spawner, currentPage, totalPages);

        // Notify other viewers to sync as well
        spawnerGuiViewManager.updateSpawnerMenuViewers(spawner);

        if (plugin.isDebugMode()) {
            plugin.debug("GUI refreshed from VirtualInventory for player " + player.getName() +
                        " on spawner " + spawner.getSpawnerId());
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof StoragePageHolder)) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof StoragePageHolder holder)) {
            return;
        }


        SpawnerData spawner = holder.getSpawnerData();
        if (spawner.isInteracted()){
            plugin.getSpawnerManager().markSpawnerModified(spawner.getSpawnerId());
            spawner.clearInteracted();
        }
    }
}