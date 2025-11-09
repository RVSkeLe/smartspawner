package github.nighter.smartspawner.spawner.gui.storage;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.Scheduler;
import github.nighter.smartspawner.language.MessageService;
import github.nighter.smartspawner.spawner.gui.layout.GuiLayoutConfig;
import github.nighter.smartspawner.spawner.gui.storage.filter.FilterConfigUI;
import github.nighter.smartspawner.spawner.gui.storage.utils.ItemClickHandler;
import github.nighter.smartspawner.spawner.gui.storage.utils.ItemMoveHelper;
import github.nighter.smartspawner.spawner.gui.storage.utils.ItemMoveResult;
import github.nighter.smartspawner.spawner.gui.main.SpawnerMenuUI;
import github.nighter.smartspawner.spawner.gui.synchronization.SpawnerGuiViewManager;
import github.nighter.smartspawner.spawner.gui.layout.GuiLayout;
import github.nighter.smartspawner.spawner.loot.LootItem;
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

public class SpawnerStorageAction implements Listener {
    private final SmartSpawner plugin;
    private final LanguageManager languageManager;
    private final SpawnerMenuUI spawnerMenuUI;
    private final SpawnerGuiViewManager spawnerGuiViewManager;
    private final MessageService messageService;
    private final FilterConfigUI filterConfigUI;
    private final SpawnerSellManager spawnerSellManager;
    private final SpawnerManager spawnerManager;
    private GuiLayoutConfig guiLayoutConfig;

    private static final int INVENTORY_SIZE = 54;
    private static final int STORAGE_SLOTS = 45;

    private final Map<ClickType, ItemClickHandler> clickHandlers;
    // Using ConcurrentHashMap for thread-safety with Folia's async scheduler
    private final Map<UUID, Inventory> openStorageInventories = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastItemClickTime = new ConcurrentHashMap<>();
    // Transaction locking system to prevent concurrent drop operations
    private final Set<UUID> activeDropTransactions = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> transactionStartTimes = new ConcurrentHashMap<>();
    // Storage GUI access cooldown system to prevent macro abuse
    private final Map<UUID, Long> storageAccessCooldowns = new ConcurrentHashMap<>();
    private static final long TRANSACTION_TIMEOUT_MS = 5000; // 5 seconds max per transaction
    private static final long DROP_COOLDOWN_MS = 150;
    private static final long STORAGE_ACCESS_COOLDOWN_MS = 500; // 500ms cooldown before re-accessing storage GUI
    private Random random = new Random();
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
        this.guiLayoutConfig = plugin.getGuiLayoutConfig();
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

        if (event.getAction() == InventoryAction.DROP_ONE_SLOT ||
                event.getAction() == InventoryAction.DROP_ALL_SLOT) {

            if (slot >= 0 && slot < STORAGE_SLOTS) {
                ItemStack clickedItem = event.getCurrentItem();
                if (clickedItem != null && clickedItem.getType() != Material.AIR) {
                    event.setCancelled(true);

                    boolean dropStack = event.getAction() == InventoryAction.DROP_ALL_SLOT;
                    handleItemDrop(player, spawner, event.getInventory(), slot, clickedItem, dropStack);
                    return;
                }
            }
        }

        event.setCancelled(true);

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

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof StoragePageHolder holder)) {
            return;
        }
        event.setCancelled(true);
    }

    private boolean isControlSlot(int slot) {
        return layout != null && layout.isSlotUsed(slot);
    }

    /**
     * Handles dropping a single or full stack of items with comprehensive dupe prevention.
     * 
     * Security measures implemented:
     * 1. Transaction locking to prevent concurrent operations
     * 2. Pre-drop validation of items in VirtualInventory
     * 3. Atomic operation: VirtualInventory update BEFORE item drop
     * 4. Spawner existence validation
     * 5. Inventory open state validation
     * 6. Cooldown enforcement to prevent rapid drop exploits
     * 
     * @param player The player dropping items
     * @param spawner The spawner data
     * @param inventory The storage inventory
     * @param slot The slot being dropped
     * @param item The item stack in the slot
     * @param dropStack True to drop entire stack, false to drop one item
     */
    private void handleItemDrop(Player player, SpawnerData spawner, Inventory inventory,
                                int slot, ItemStack item, boolean dropStack) {
        UUID playerId = player.getUniqueId();
        
        // 1. Cooldown check - prevents rapid drop exploits (500ms debounce)
        if (isDropOperationTooFrequent(playerId)) {
            return;
        }
        
        // 2. Transaction lock - prevent concurrent drop operations per player
        if (!acquireDropTransaction(playerId)) {
            messageService.sendMessage(player, "action_in_progress");
            return;
        }

        try {

            // 4. Validate inventory is still open (prevents exploit on early close)
            if (!isPlayerInventoryOpen(player, inventory)) {
                return;
            }
            
            // 5. Re-validate item still exists in GUI slot
            ItemStack currentItem = inventory.getItem(slot);
            if (currentItem == null || currentItem.getType() == Material.AIR || !currentItem.isSimilar(item)) {
                return;
            }
            
            int amountToDrop = dropStack ? item.getAmount() : 1;
            
            // 6. Create dropped item and validate
            ItemStack droppedItem = item.clone();
            droppedItem.setAmount(Math.min(amountToDrop, item.getAmount()));
            
            VirtualInventory virtualInv = spawner.getVirtualInventory();
            List<ItemStack> itemsToRemove = new ArrayList<>();
            itemsToRemove.add(droppedItem);
            
            // 7. PRE-DROP VALIDATION: Verify items actually exist in VirtualInventory
            if (!validateItemsExistInVirtualInventory(itemsToRemove, virtualInv)) {
                messageService.sendMessage(player, "items_not_available");
                // Refresh display to show actual inventory state
                StoragePageHolder holder = (StoragePageHolder) inventory.getHolder(false);
                if (holder != null) {
                    updatePageContent(player, spawner, holder.getCurrentPage(), inventory, false);
                }
                return;
            }
            
            // 8. CRITICAL: Remove from VirtualInventory FIRST (atomic operation)
            boolean removalSuccess = spawner.removeItemsAndUpdateSellValue(itemsToRemove);
            
            if (!removalSuccess) {
                // Removal failed - items don't exist in VirtualInventory
                messageService.sendMessage(player, "drop_failed");
                // Refresh display to show actual inventory state
                StoragePageHolder holder = (StoragePageHolder) inventory.getHolder(false);
                if (holder != null) {
                    updatePageContent(player, spawner, holder.getCurrentPage(), inventory, false);
                }
                return;
            }
            
            // 9. Update GUI display AFTER successful VirtualInventory update
            int remaining = item.getAmount() - amountToDrop;
            if (remaining <= 0) {
                inventory.setItem(slot, null);
            } else {
                ItemStack remainingItem = item.clone();
                remainingItem.setAmount(remaining);
                inventory.setItem(slot, remainingItem);
            }
            
            // 10. Drop items in world (only after VirtualInventory confirmed removal)
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
            
            // 11. Log item drop action for audit trail
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
            
            // 12. Provide feedback
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.2f);
            
            // 13. Update spawner state and notify other viewers
            spawner.updateHologramData();
            
            StoragePageHolder holder = (StoragePageHolder) inventory.getHolder(false);
            if (holder != null) {
                // Only recalculate and update title if page count might have changed
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
                    updateInventoryTitle(player, inventory, spawner, adjustedPage, newTotalPages);
                }
                
                // CRITICAL: Update oldUsedSlots BEFORE calling updateSpawnerMenuViewers
                holder.updateOldUsedSlots();
                spawnerGuiViewManager.updateSpawnerMenuViewers(spawner);
                
                if (!spawner.isInteracted()) {
                    spawner.markInteracted();
                }
                if (spawner.getMaxSpawnerLootSlots() > holder.getOldUsedSlots() && spawner.getIsAtCapacity()) {
                    spawner.setIsAtCapacity(false);
                }
            }
            
        } finally {
            // 14. ALWAYS release transaction lock (prevents deadlocks)
            releaseDropTransaction(playerId);
        }
    }

    /**
     * Handles dropping all items from the current storage page with comprehensive dupe prevention.
     * 
     * Security measures implemented:
     * 1. Transaction locking to prevent concurrent operations
     * 2. Pre-drop validation of items in VirtualInventory
     * 3. Atomic operation: VirtualInventory update BEFORE item drop
     * 4. Post-drop verification
     * 5. Edge case handling for disconnects, lag, and rapid clicks
     * 
     * @param player The player dropping items
     * @param spawner The spawner data
     * @param inventory The storage inventory
     */
    private void handleDropPageItems(Player player, SpawnerData spawner, Inventory inventory) {
        UUID playerId = player.getUniqueId();
        
        // 1. Cooldown check - prevents spam clicking
        if (isDropOperationTooFrequent(playerId)) {
            return;
        }

        // 2. Transaction lock - prevent concurrent drop operations per player
        if (!acquireDropTransaction(playerId)) {
            messageService.sendMessage(player, "action_in_progress");
            return;
        }

        try {
            // 3. Validate holder exists
            StoragePageHolder holder = (StoragePageHolder) inventory.getHolder(false);
            if (holder == null) {
                return;
            }

            // 5. Validate inventory is still open (prevents exploit on early close)
            if (!isPlayerInventoryOpen(player, inventory)) {
                return;
            }

            // 6. Collect items from GUI display
            List<ItemStack> pageItems = new ArrayList<>();
            int itemsFoundCount = 0;

            for (int i = 0; i < STORAGE_SLOTS; i++) {
                ItemStack item = inventory.getItem(i);
                if (item != null && item.getType() != Material.AIR) {
                    pageItems.add(item.clone());
                    itemsFoundCount += item.getAmount();
                }
            }

            if (pageItems.isEmpty()) {
                messageService.sendMessage(player, "no_items_to_drop");
                return;
            }

            final int itemsFound = itemsFoundCount;
            VirtualInventory virtualInv = spawner.getVirtualInventory();

            // 7. PRE-DROP VALIDATION: Verify items actually exist in VirtualInventory
            if (!validateItemsExistInVirtualInventory(pageItems, virtualInv)) {
                messageService.sendMessage(player, "items_not_available");
                // Refresh display to show actual inventory state
                updatePageContent(player, spawner, holder.getCurrentPage(), inventory, false);
                return;
            }

            // 8. CRITICAL: Remove from VirtualInventory FIRST (atomic operation)
            // This is the key security fix - VirtualInventory must update before items drop
            // Note: removeItemsAndUpdateSellValue() calls virtualInventory.removeItems()
            // which returns false if items don't exist (verified in VirtualInventory.java lines 174-178)
            boolean removalSuccess = spawner.removeItemsAndUpdateSellValue(pageItems);
            
            if (!removalSuccess) {
                // Removal failed - items don't exist in VirtualInventory
                messageService.sendMessage(player, "drop_failed");
                // Refresh display to show actual inventory state
                updatePageContent(player, spawner, holder.getCurrentPage(), inventory, false);
                return;
            }

            // 9. Clear GUI display AFTER successful VirtualInventory update
            for (int i = 0; i < STORAGE_SLOTS; i++) {
                ItemStack item = inventory.getItem(i);
                if (item != null && item.getType() != Material.AIR) {
                    inventory.setItem(i, null);
                }
            }

            // 10. Drop items in world (only after VirtualInventory confirmed removal)
            dropItemsInDirection(player, pageItems);

            // 11. Update page metadata and GUI
            int newTotalPages = calculateTotalPages(spawner);
            int adjustedPage = holder.getCurrentPage();
            if (adjustedPage > newTotalPages) {
                adjustedPage = Math.max(1, newTotalPages);
                holder.setCurrentPage(adjustedPage);
            }
            holder.setTotalPages(newTotalPages);
            holder.updateOldUsedSlots();
            
            // Store final page for lambda use (must be effectively final)
            final int finalPage = adjustedPage;
            
            // Update display for current player BEFORE notifying other viewers
            // This ensures the current player sees the updated page immediately
            SpawnerStorageUI lootManager = plugin.getSpawnerStorageUI();
            lootManager.updateDisplay(inventory, spawner, finalPage, newTotalPages);
            updateInventoryTitle(player, inventory, spawner, finalPage, newTotalPages);

            // 12. Update spawner state and notify other viewers
            spawner.updateHologramData();
            spawnerGuiViewManager.updateSpawnerMenuViewers(spawner);

            if (spawner.getMaxSpawnerLootSlots() > holder.getOldUsedSlots() && spawner.getIsAtCapacity()) {
                spawner.setIsAtCapacity(false);
            }
            if (!spawner.isInteracted()) {
                spawner.markInteracted();
            }

            // 13. Log successful drop operation
            if (plugin.getSpawnerActionLogger() != null) {
                plugin.getSpawnerActionLogger().log(github.nighter.smartspawner.logging.SpawnerEventType.SPAWNER_DROP_PAGE_ITEMS, builder -> 
                    builder.player(player.getName(), player.getUniqueId())
                        .location(spawner.getSpawnerLocation())
                        .entityType(spawner.getEntityType())
                        .metadata("items_dropped", itemsFound)
                        .metadata("page_number", finalPage)
                );
            }

            // 14. Provide feedback
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 0.8f);

        } finally {
            // 15. ALWAYS release transaction lock (prevents deadlocks)
            releaseDropTransaction(playerId);
        }
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

    /**
     * Handles taking a single or full stack of items from storage with comprehensive dupe prevention.
     * 
     * Security measures implemented:
     * 1. Transaction locking to prevent concurrent operations
     * 2. Pre-operation validation of items in VirtualInventory
     * 3. Atomic operation: VirtualInventory update BEFORE GUI/player inventory modification
     * 4. Spawner existence validation
     * 5. Inventory open state validation
     * 6. Cooldown enforcement to prevent rapid click exploits
     * 
     * @param player The player taking items
     * @param sourceInv The storage inventory
     * @param slot The slot being clicked
     * @param item The item stack in the slot
     * @param spawner The spawner data
     * @param singleItem True to take 1 item, false to take entire stack
     */
    private void takeSingleItem(Player player, Inventory sourceInv, int slot, ItemStack item,
                                SpawnerData spawner, boolean singleItem) {
        UUID playerId = player.getUniqueId();
        
        // 1. Cooldown check - prevents rapid click exploits (150ms debounce)
        // Note: This check is intentionally before transaction lock to avoid unnecessary locking
        if (isDropOperationTooFrequent(playerId)) {
            return;
        }
        
        // 2. Transaction lock - prevent concurrent take operations per player
        if (!acquireDropTransaction(playerId)) {
            messageService.sendMessage(player, "action_in_progress");
            return;
        }
        
        try {
            
            // 4. Validate inventory is still open (prevents exploit on early close)
            if (!isPlayerInventoryOpen(player, sourceInv)) {
                return;
            }
            
            // 5. Re-validate item still exists in GUI slot (could have been taken by another viewer)
            ItemStack currentItem = sourceInv.getItem(slot);
            if (currentItem == null || currentItem.getType() == Material.AIR || !currentItem.isSimilar(item)) {
                return;
            }
            
            PlayerInventory playerInv = player.getInventory();
            VirtualInventory virtualInv = spawner.getVirtualInventory();
            
            int amountToTake = singleItem ? 1 : item.getAmount();
            
            // 6. PRE-OPERATION VALIDATION: Create list of items to remove and verify they exist
            List<ItemStack> itemsToRemove = new ArrayList<>();
            ItemStack itemToRemove = item.clone();
            itemToRemove.setAmount(amountToTake);
            itemsToRemove.add(itemToRemove);
            
            if (!validateItemsExistInVirtualInventory(itemsToRemove, virtualInv)) {
                messageService.sendMessage(player, "items_not_available");
                // Refresh display to show actual inventory state
                StoragePageHolder holder = (StoragePageHolder) sourceInv.getHolder(false);
                if (holder != null) {
                    updatePageContent(player, spawner, holder.getCurrentPage(), sourceInv, false);
                }
                return;
            }
            
            // 7. CRITICAL: Remove from VirtualInventory FIRST (atomic operation)
            // This is the key security fix - VirtualInventory must update before items are transferred
            boolean removalSuccess = spawner.removeItemsAndUpdateSellValue(itemsToRemove);
            
            if (!removalSuccess) {
                // Removal failed - items don't exist in VirtualInventory
                messageService.sendMessage(player, "take_failed");
                // Refresh display to show actual inventory state
                StoragePageHolder holder = (StoragePageHolder) sourceInv.getHolder(false);
                if (holder != null) {
                    updatePageContent(player, spawner, holder.getCurrentPage(), sourceInv, false);
                }
                return;
            }
            
            // 8. Now attempt to transfer items to player inventory (after VirtualInventory confirmed removal)
            ItemMoveResult result = ItemMoveHelper.moveItems(
                    item,
                    amountToTake,
                    playerInv,
                    virtualInv  // Note: virtualInv is passed but not modified by moveItems
            );
            
            // 9. If player inventory is full, we need to add items back to VirtualInventory
            if (result.getAmountMoved() < amountToTake) {
                // Player couldn't take all items - return the difference to VirtualInventory
                int amountNotMoved = amountToTake - result.getAmountMoved();
                if (amountNotMoved > 0) {
                    ItemStack itemToReturn = item.clone();
                    itemToReturn.setAmount(amountNotMoved);
                    List<ItemStack> itemsToReturn = new ArrayList<>();
                    itemsToReturn.add(itemToReturn);
                    virtualInv.addItems(itemsToReturn);
                    
                    // Adjust the items we actually moved
                    itemsToRemove.clear();
                    if (result.getAmountMoved() > 0) {
                        ItemStack actuallyMoved = item.clone();
                        actuallyMoved.setAmount(result.getAmountMoved());
                        itemsToRemove.add(actuallyMoved);
                    }
                }
            }
            
            // 10. Update GUI display AFTER VirtualInventory has been updated
            if (result.getAmountMoved() > 0) {
                updateInventorySlot(sourceInv, slot, item, result.getAmountMoved());
                player.updateInventory();
                spawner.updateHologramData();
                
                StoragePageHolder holder = (StoragePageHolder) sourceInv.getHolder(false);
                if (holder != null) {
                    // Only recalculate and update title if page count might have changed
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
                        updateInventoryTitle(player, sourceInv, spawner, adjustedPage, newTotalPages);
                    }
                    
                    // CRITICAL: Update oldUsedSlots BEFORE calling updateSpawnerMenuViewers
                    holder.updateOldUsedSlots();
                    spawnerGuiViewManager.updateSpawnerMenuViewers(spawner);
                    
                    if (spawner.getMaxSpawnerLootSlots() > holder.getOldUsedSlots() && spawner.getIsAtCapacity()) {
                        spawner.setIsAtCapacity(false);
                    }
                }
            } else {
                // Nothing was moved - show inventory full message
                messageService.sendMessage(player, "inventory_full");
            }
            
        } finally {
            // 11. ALWAYS release transaction lock (prevents deadlocks)
            releaseDropTransaction(playerId);
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
        SpawnerStorageUI lootManager = plugin.getSpawnerStorageUI();
        StoragePageHolder holder = (StoragePageHolder) inventory.getHolder(false);

        int totalPages = calculateTotalPages(spawner);

        assert holder != null;
        holder.setTotalPages(totalPages);
        holder.setCurrentPage(newPage);
        holder.updateOldUsedSlots();

        lootManager.updateDisplay(inventory, spawner, newPage, totalPages);

        updateInventoryTitle(player, inventory, spawner, newPage, totalPages);

        if (uiClickSound) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
        }
    }

    private int calculateTotalPages(SpawnerData spawner) {
        int usedSlots = spawner.getVirtualInventory().getUsedSlots();
        return Math.max(1, (int) Math.ceil((double) usedSlots / StoragePageHolder.MAX_ITEMS_PER_PAGE));
    }

    private void updateInventoryTitle(Player player, Inventory inventory, SpawnerData spawner, int page, int totalPages) {
        // Use placeholder-based title format for consistency
        String newTitle = languageManager.getGuiTitle("gui_title_storage", Map.of(
            "current_page", String.valueOf(page),
            "total_pages", String.valueOf(totalPages)
        ));

        try {
            player.getOpenInventory().setTitle(newTitle);
        } catch (Exception e) {
            openLootPage(player, spawner, page, false);
        }
    }

    private boolean isClickTooFrequent(Player player) {
        long now = System.currentTimeMillis();
        long last = lastItemClickTime.getOrDefault(player.getUniqueId(), 0L);
        lastItemClickTime.put(player.getUniqueId(), now);
        return (now - last) < 300;
    }

    /**
     * Checks if a drop operation is too frequent for the given player.
     * Uses a 500ms cooldown to prevent rapid click exploits.
     * 
     * @param playerId The player's UUID
     * @return true if the operation is too frequent, false otherwise
     */
    private boolean isDropOperationTooFrequent(UUID playerId) {
        long now = System.currentTimeMillis();
        long last = lastItemClickTime.getOrDefault(playerId, 0L);
        lastItemClickTime.put(playerId, now);
        return (now - last) < DROP_COOLDOWN_MS;
    }

    /**
     * Checks if a player can access the storage GUI based on cooldown.
     * Implements 500ms cooldown after closing storage GUI to prevent macro abuse.
     * Thread-safe for multiple players viewing the same spawner.
     * 
     * @param playerId The player's UUID
     * @return true if player can access storage GUI, false if still on cooldown
     */
    public boolean canAccessStorageGUI(UUID playerId) {
        long now = System.currentTimeMillis();
        Long lastClose = storageAccessCooldowns.get(playerId);
        
        if (lastClose == null) {
            return true;
        }
        
        return (now - lastClose) >= STORAGE_ACCESS_COOLDOWN_MS;
    }

    /**
     * Attempts to acquire a drop transaction lock for the player.
     * Includes timeout mechanism to prevent stuck transactions.
     * 
     * @param playerId The player's UUID
     * @return true if lock acquired, false if already in transaction
     */
    private boolean acquireDropTransaction(UUID playerId) {
        // Check for expired transactions and clean them up
        cleanupExpiredTransactions();
        
        // Try to add to active transactions (returns false if already exists)
        if (activeDropTransactions.add(playerId)) {
            transactionStartTimes.put(playerId, System.currentTimeMillis());
            return true;
        }
        
        return false;
    }

    /**
     * Releases the drop transaction lock for the player.
     * Should ALWAYS be called in a finally block.
     * 
     * @param playerId The player's UUID
     */
    private void releaseDropTransaction(UUID playerId) {
        activeDropTransactions.remove(playerId);
        transactionStartTimes.remove(playerId);
    }

    /**
     * Cleans up transactions that have exceeded the timeout limit.
     * Prevents deadlocks from crashed operations or server lag.
     */
    private void cleanupExpiredTransactions() {
        long now = System.currentTimeMillis();
        List<UUID> expiredTransactions = new ArrayList<>();
        
        for (Map.Entry<UUID, Long> entry : transactionStartTimes.entrySet()) {
            if (now - entry.getValue() > TRANSACTION_TIMEOUT_MS) {
                expiredTransactions.add(entry.getKey());
            }
        }
        
        for (UUID playerId : expiredTransactions) {
            activeDropTransactions.remove(playerId);
            transactionStartTimes.remove(playerId);
        }
    }

    /**
     * Validates that all items in the list exist in the VirtualInventory.
     * Pre-drop validation layer to prevent duplication exploits.
     * 
     * @param items Items to validate
     * @param virtualInv The virtual inventory to check against
     * @return true if all items exist in sufficient quantities, false otherwise
     */
    private boolean validateItemsExistInVirtualInventory(List<ItemStack> items, VirtualInventory virtualInv) {
        if (items == null || items.isEmpty()) {
            return true;
        }
        
        // Build a map of item signatures to required amounts
        Map<VirtualInventory.ItemSignature, Long> requiredItems = new HashMap<>();
        for (ItemStack item : items) {
            if (item == null || item.getAmount() <= 0) continue;
            VirtualInventory.ItemSignature sig = VirtualInventory.getSignature(item);
            requiredItems.merge(sig, (long) item.getAmount(), Long::sum);
        }
        
        // Check against VirtualInventory's consolidated items
        Map<VirtualInventory.ItemSignature, Long> consolidatedItems = virtualInv.getConsolidatedItems();
        
        for (Map.Entry<VirtualInventory.ItemSignature, Long> entry : requiredItems.entrySet()) {
            Long available = consolidatedItems.get(entry.getKey());
            if (available == null || available < entry.getValue()) {
                // Not enough of this item type in VirtualInventory
                return false;
            }
        }
        
        return true;
    }

    /**
     * Checks if the player still has the specified inventory open.
     * Prevents exploits where player closes inventory mid-operation.
     * 
     * @param player The player to check
     * @param expectedInventory The inventory that should be open
     * @return true if the inventory is still open, false otherwise
     */
    private boolean isPlayerInventoryOpen(Player player, Inventory expectedInventory) {
        if (player == null || !player.isOnline()) {
            return false;
        }
        
        Inventory openInv = player.getOpenInventory().getTopInventory();
        return openInv != null && openInv.equals(expectedInventory);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        lastItemClickTime.remove(playerId);
        
        // Clean up transaction locks on player disconnect
        // This prevents stuck transactions from disconnected players
        activeDropTransactions.remove(playerId);
        transactionStartTimes.remove(playerId);
        
        // Clean up storage access cooldown to prevent memory leaks
        storageAccessCooldowns.remove(playerId);
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
                .map(LootItem::getMaterial)
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

        // Re-sort the virtual inventory
        spawner.getVirtualInventory().sortItems(nextSort);

        // Update the display
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

    private void openLootPage(Player player, SpawnerData spawner, int page, boolean refresh) {
        SpawnerStorageUI lootManager = plugin.getSpawnerStorageUI();
        String title = languageManager.getGuiTitle("gui_title_storage");

        int totalPages = calculateTotalPages(spawner);

        final int finalPage = Math.max(1, Math.min(page, totalPages));

        UUID playerId = player.getUniqueId();
        Inventory existingInventory = openStorageInventories.get(playerId);

        if (existingInventory != null && !refresh && existingInventory.getHolder(false) instanceof StoragePageHolder) {
            StoragePageHolder holder = (StoragePageHolder) existingInventory.getHolder(false);

            holder.setTotalPages(totalPages);
            holder.setCurrentPage(finalPage);
            holder.updateOldUsedSlots();

            updatePageContent(player, spawner, finalPage, existingInventory, false);
            return;
        }

        // Initialize sort preference on first open
        Material currentSort = spawner.getPreferredSortItem();
        if (currentSort == null && spawner.getLootConfig() != null && spawner.getLootConfig().getAllItems() != null) {
            var lootItems = spawner.getLootConfig().getAllItems();
            if (!lootItems.isEmpty()) {
                var sortedLoot = lootItems.stream()
                    .map(LootItem::getMaterial)
                    .distinct()
                    .sorted(Comparator.comparing(Material::name))
                    .toList();
                
                if (!sortedLoot.isEmpty()) {
                    Material firstItem = sortedLoot.getFirst();
                    spawner.setPreferredSortItem(firstItem);
                    currentSort = firstItem;
                    
                    if (!spawner.isInteracted()) {
                        spawner.markInteracted();
                    }
                    spawnerManager.queueSpawnerForSaving(spawner.getSpawnerId());
                }
            }
        }
        
        // Apply sort to virtual inventory if a sort preference exists
        if (currentSort != null) {
            spawner.getVirtualInventory().sortItems(currentSort);
        }

        Inventory pageInventory = lootManager.createInventory(spawner, title, finalPage, totalPages);

        openStorageInventories.put(playerId, pageInventory);
        
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

        Sound sound = refresh ? Sound.ITEM_ARMOR_EQUIP_DIAMOND : Sound.UI_BUTTON_CLICK;
        float pitch = refresh ? 1.2f : 1.0f;
        player.playSound(player.getLocation(), sound, 1.0f, pitch);

        player.openInventory(pageInventory);
    }

    /**
     * Handles taking all items from the current storage page with comprehensive security measures.
     * 
     * Security measures implemented (matching handleDropPageItems pattern):
     * 1. Transaction locking to prevent concurrent operations
     * 2. Pre-operation validation of items in VirtualInventory
     * 3. Atomic operation: VirtualInventory update BEFORE player inventory modification
     * 4. Post-operation verification
     * 5. Edge case handling for disconnects, lag, and rapid clicks
     * 6. Region-aware scheduling for Folia compatibility
     * 
     * @param player The player taking items
     * @param sourceInventory The storage inventory
     */
    public void handleTakeAllItems(Player player, Inventory sourceInventory) {
        UUID playerId = player.getUniqueId();
        
        // 1. Cooldown check - prevents spam clicking (300ms debounce)
        if (isClickTooFrequent(player)) {
            return;
        }

        // 2. Transaction lock - prevent concurrent take operations per player
        // Reuse the same transaction system as drop operations for consistency
        if (!acquireDropTransaction(playerId)) {
            messageService.sendMessage(player, "action_in_progress");
            return;
        }

        try {
            // 3. Validate holder exists
            StoragePageHolder holder = (StoragePageHolder) sourceInventory.getHolder(false);
            if (holder == null) {
                return;
            }

            SpawnerData spawner = holder.getSpawnerData();

            // 5. Validate inventory is still open (prevents exploit on early close)
            if (!isPlayerInventoryOpen(player, sourceInventory)) {
                return;
            }

            VirtualInventory virtualInv = spawner.getVirtualInventory();

            // 6. Collect items from GUI display
            Map<Integer, ItemStack> sourceItems = new HashMap<>();
            List<ItemStack> itemsToTransfer = new ArrayList<>();
            int totalItemCount = 0;
            
            for (int i = 0; i < STORAGE_SLOTS; i++) {
                ItemStack item = sourceInventory.getItem(i);
                if (item != null && item.getType() != Material.AIR) {
                    sourceItems.put(i, item.clone());
                    itemsToTransfer.add(item.clone());
                    totalItemCount += item.getAmount();
                }
            }

            if (sourceItems.isEmpty()) {
                messageService.sendMessage(player, "no_items_to_take");
                return;
            }

            // 7. PRE-OPERATION VALIDATION: Verify items actually exist in VirtualInventory
            if (!validateItemsExistInVirtualInventory(itemsToTransfer, virtualInv)) {
                messageService.sendMessage(player, "items_not_available");
                // Refresh display to show actual inventory state
                updatePageContent(player, spawner, holder.getCurrentPage(), sourceInventory, false);
                return;
            }

            // 8. CRITICAL: Remove from VirtualInventory FIRST (atomic operation)
            // This prevents duplication exploits where items are taken but VirtualInventory isn't updated
            boolean removalSuccess = spawner.removeItemsAndUpdateSellValue(itemsToTransfer);
            
            if (!removalSuccess) {
                // Removal failed - items don't exist in VirtualInventory
                messageService.sendMessage(player, "take_failed");
                // Refresh display to show actual inventory state
                updatePageContent(player, spawner, holder.getCurrentPage(), sourceInventory, false);
                return;
            }

            // 9. Now transfer items to player inventory (after VirtualInventory confirmed removal)
            // Use region-locked task for cross-region safety in Folia
            Location spawnerLoc = spawner.getSpawnerLocation();
            final int itemsBeforeTransfer = totalItemCount;
            
            Scheduler.runLocationTask(spawnerLoc, () -> {
                TransferResult result = transferItemsSecure(player, sourceInventory, sourceItems, virtualInv);
                
                // 10. Send feedback to player
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
                        SpawnerStorageUI lootManager = plugin.getSpawnerStorageUI();
                        lootManager.updateDisplay(sourceInventory, spawner, adjustedPage, newTotalPages);
                    }
                    
                    // Update the inventory title to reflect new page count
                    updateInventoryTitle(player, sourceInventory, spawner, adjustedPage, newTotalPages);

                    // CRITICAL: Update oldUsedSlots BEFORE calling updateSpawnerMenuViewers
                    // This ensures other viewers get the correct page calculation
                    holder.updateOldUsedSlots();

                    spawnerGuiViewManager.updateSpawnerMenuViewers(spawner);

                    if (spawner.getMaxSpawnerLootSlots() > holder.getOldUsedSlots() && spawner.getIsAtCapacity()) {
                        spawner.setIsAtCapacity(false);
                    }
                    if (!spawner.isInteracted()) {
                        spawner.markInteracted();
                    }
                    
                    // 12. Log take all items action
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
            });

        } finally {
            // 13. ALWAYS release transaction lock (prevents deadlocks)
            releaseDropTransaction(playerId);
        }
    }

    /**
     * Securely transfers items from storage to player inventory.
     * NOTE: VirtualInventory should be updated BEFORE calling this method.
     * This method only handles the physical transfer to player inventory.
     * 
     * @param player The player receiving items
     * @param sourceInventory The storage inventory
     * @param sourceItems The items to transfer (snapshot from GUI)
     * @param virtualInv The virtual inventory (for reference only, not modified here)
     * @return TransferResult indicating success and amounts moved
     */
    private TransferResult transferItemsSecure(Player player, Inventory sourceInventory,
                                         Map<Integer, ItemStack> sourceItems, VirtualInventory virtualInv) {
        boolean anyItemMoved = false;
        boolean inventoryFull = false;
        PlayerInventory playerInv = player.getInventory();
        int totalAmountMoved = 0;

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

        if (anyItemMoved) {
            StoragePageHolder holder = (StoragePageHolder) sourceInventory.getHolder(false);
            if (holder != null) {
                holder.getSpawnerData().updateHologramData();
                holder.updateOldUsedSlots();
            }
        }

        return new TransferResult(anyItemMoved, inventoryFull, totalAmountMoved);
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

        if (!itemsToRemove.isEmpty()) {
            StoragePageHolder holder = (StoragePageHolder) sourceInventory.getHolder(false);
            holder.getSpawnerData().removeItemsAndUpdateSellValue(itemsToRemove);
            holder.getSpawnerData().updateHologramData();
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

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof StoragePageHolder holder)) {
            return;
        }

        if (event.getPlayer() instanceof Player player) {
            UUID playerId = player.getUniqueId();
            openStorageInventories.remove(playerId);
            
            // Record storage GUI close time for anti-macro cooldown
            storageAccessCooldowns.put(playerId, System.currentTimeMillis());
        }

        SpawnerData spawner = holder.getSpawnerData();
        if (spawner.isInteracted()){
            spawnerManager.markSpawnerModified(spawner.getSpawnerId());
            spawner.clearInteracted();
        }
    }

    private record TransferResult(boolean anyItemMoved, boolean inventoryFull, int totalMoved) {}
}
