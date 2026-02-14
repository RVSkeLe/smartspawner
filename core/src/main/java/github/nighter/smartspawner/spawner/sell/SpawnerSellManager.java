package github.nighter.smartspawner.spawner.sell;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.api.events.SpawnerSellEvent;
import github.nighter.smartspawner.language.MessageService;
import github.nighter.smartspawner.spawner.gui.synchronization.SpawnerGuiViewManager;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import github.nighter.smartspawner.spawner.properties.VirtualInventory;
import github.nighter.smartspawner.Scheduler;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class SpawnerSellManager {
    private final SmartSpawner plugin;
    private final MessageService messageService;
    private final SpawnerGuiViewManager spawnerGuiViewManager;

    public SpawnerSellManager(SmartSpawner plugin) {
        this.plugin = plugin;
        this.messageService = plugin.getMessageService();
        this.spawnerGuiViewManager = plugin.getSpawnerGuiViewManager();
    }

    /**
     * Sells all items from the spawner's virtual inventory
     * This method is async-optimized and uses cached sell values for efficiency
     */
    public void sellAllItems(Player player, SpawnerData spawner) {
        // Try to acquire locks in consistent order to prevent deadlocks
        // Always acquire inventoryLock first, then sellLock
        boolean inventoryLockAcquired = spawner.getInventoryLock().tryLock();
        if (!inventoryLockAcquired) {
            messageService.sendMessage(player, "action_in_progress");
            return;
        }

        try {
            boolean sellLockAcquired = spawner.getSellLock().tryLock();
            if (!sellLockAcquired) {
                messageService.sendMessage(player, "action_in_progress");
                return;
            }

            try {
                VirtualInventory virtualInv = spawner.getVirtualInventory();

                // Quick check if there are items to sell
                if (virtualInv.getUsedSlots() == 0) {
                    messageService.sendMessage(player, "no_items");
                    return;
                }
                
                // Recalculate sell value if dirty (should rarely happen)
                if (spawner.isSellValueDirty()) {
                    spawner.recalculateSellValue();
                }

                // Get all items for processing
                Map<VirtualInventory.ItemSignature, Long> consolidatedItems = virtualInv.getConsolidatedItems();

                // Process selling async to avoid blocking main thread
                Scheduler.runTaskAsync(() -> {
                    // Use cached sell value for optimization
                    SellResult result = calculateSellValue(consolidatedItems, spawner);

                    // Store the result in SpawnerData for later access
                    spawner.setLastSellResult(result);

                    // Return to main thread for inventory operations and player interaction
                    Scheduler.runLocationTask(spawner.getSpawnerLocation(), () -> {
                        processSellResult(player, spawner, result);
                    });
                });

            } finally {
                spawner.getSellLock().unlock();
            }
        } finally {
            spawner.getInventoryLock().unlock();
        }
    }


    /**
     * Process the sell result on the main thread
     */
    private void processSellResult(Player player, SpawnerData spawner, SellResult sellResult) {
        // Re-acquire locks in consistent order for final operations
        boolean inventoryLockAcquired = spawner.getInventoryLock().tryLock();
        if (!inventoryLockAcquired) {
            messageService.sendMessage(player, "action_in_progress");
            return;
        }

        try {
            boolean sellLockAcquired = spawner.getSellLock().tryLock();
            if (!sellLockAcquired) {
                messageService.sendMessage(player, "action_in_progress");
                return;
            }

            try {
                VirtualInventory virtualInv = spawner.getVirtualInventory();

                // Double-check that we still have items and they match what we calculated
                if (!sellResult.isSuccessful()) {
                    messageService.sendMessage(player, "no_sellable_items");
                    return;
                }

                // Perform the actual sale
                double amount = sellResult.getTotalValue();
                if(SpawnerSellEvent.getHandlerList().getRegisteredListeners().length != 0) {
                    SpawnerSellEvent event = new SpawnerSellEvent(player, spawner.getSpawnerLocation(), sellResult.getItemsToRemove(), amount);
                    Bukkit.getPluginManager().callEvent(event);
                    if(event.isCancelled()) return;
                    if(event.getMoneyAmount() >= 0) amount = event.getMoneyAmount();
                }
                boolean depositSuccess = plugin.getItemPriceManager().getCurrencyManager()
                        .deposit(amount, player);

                if (!depositSuccess) {
                    messageService.sendMessage(player, "sell_failed");
                    return;
                }

                // Remove sold items from virtual inventory and update sell value
                boolean itemsRemoved = spawner.removeItemsAndUpdateSellValue(sellResult.getItemsToRemove());
                if (!itemsRemoved) {
                    // If items couldn't be removed (race condition), this indicates a critical issue
                    // The money has already been deposited, so we need to log this for investigation
                    plugin.getLogger().warning("Critical: Could not remove all items after depositing money for player " + 
                        player.getName() + " at spawner " + spawner.getSpawnerId() + ". Possible exploit detected.");
                    // Note: Money has already been deposited, so we can't easily roll back without complex transaction handling
                    plugin.getItemPriceManager().getCurrencyManager().withdraw(amount, player);
                    messageService.sendMessage(player, "sell_failed");
                }

                // Update spawner state
                spawner.updateHologramData();

                // Update capacity status if needed
                if (spawner.getIsAtCapacity() &&
                        virtualInv.getUsedSlots() < spawner.getMaxSpawnerLootSlots()) {
                    spawner.setIsAtCapacity(false);
                }

                // Update GUI viewers
                spawnerGuiViewManager.updateSpawnerMenuViewers(spawner);
                // Note: Don't close inventory here - let the confirmation GUI handler reopen the previous GUI
                // player.closeInventory();

                // Send success message
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("amount", plugin.getLanguageManager().formatNumber(sellResult.getItemsSold()));
                placeholders.put("price", plugin.getLanguageManager().formatNumber(amount));
                messageService.sendMessage(player, "sell_success", placeholders);

                // Play UI button click sound
                player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);

                // Mark spawner as modified for saving
                plugin.getSpawnerManager().markSpawnerModified(spawner.getSpawnerId());

                // Update the result as successful after processing
                spawner.markLastSellAsProcessed();

            } finally {
                spawner.getSellLock().unlock();
            }
        } finally {
            spawner.getInventoryLock().unlock();
        }
    }

    /**
     * Calculates the total sell value of items using cached accumulated value
     * This method is optimized to use pre-calculated sell values
     */
    private SellResult calculateSellValue(Map<VirtualInventory.ItemSignature, Long> consolidatedItems,
                                          SpawnerData spawner) {
        // Use the accumulated sell value from spawner (already calculated incrementally)
        double totalValue = spawner.getAccumulatedSellValue();
        long totalItemsSold = 0;
        List<ItemStack> itemsToRemove = new ArrayList<>();

        // We still need to create the items list for removal
        for (Map.Entry<VirtualInventory.ItemSignature, Long> entry : consolidatedItems.entrySet()) {
            ItemStack template = entry.getKey().getTemplate();
            long amount = entry.getValue();
            
            // Count items (we need this even if we skip price calculation)
            totalItemsSold += amount;

            // Create ItemStacks to remove (handle stacking properly)
            long remainingAmount = amount;
            while (remainingAmount > 0) {
                ItemStack stackToRemove = template.clone();
                int stackSize = (int) Math.min(remainingAmount, template.getMaxStackSize());
                stackToRemove.setAmount(stackSize);
                itemsToRemove.add(stackToRemove);
                remainingAmount -= stackSize;
            }
        }

        return new SellResult(totalValue, totalItemsSold, itemsToRemove);
    }
}