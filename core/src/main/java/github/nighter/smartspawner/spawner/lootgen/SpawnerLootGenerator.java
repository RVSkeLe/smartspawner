package github.nighter.smartspawner.spawner.lootgen;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.spawner.gui.synchronization.SpawnerGuiViewManager;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import github.nighter.smartspawner.spawner.data.SpawnerManager;
import github.nighter.smartspawner.spawner.properties.VirtualInventory;
import github.nighter.smartspawner.Scheduler;
import github.nighter.smartspawner.spawner.loot.LootItem;

import org.bukkit.*;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class SpawnerLootGenerator {
    private final SmartSpawner plugin;
    private final SpawnerGuiViewManager spawnerGuiViewManager;
    private final SpawnerManager spawnerManager;
    private final Random random;

    public SpawnerLootGenerator(SmartSpawner plugin) {
        this.plugin = plugin;
        this.spawnerGuiViewManager = plugin.getSpawnerGuiViewManager();
        this.spawnerManager = plugin.getSpawnerManager();
        this.random = new Random();
    }

    public void spawnLootToSpawner(SpawnerData spawner) {
        // Try to acquire the lock, but don't block if it's already locked
        // This ensures we don't block the server thread while waiting for the lock
        boolean lockAcquired = spawner.getLootGenerationLock().tryLock();
        if (!lockAcquired) {
            // Lock is already held, which means another loot generation is happening
            // Skip this loot generation cycle
            return;
        }

        try {
            // Acquire dataLock to safely read spawn timing and configuration values
            // Use tryLock with short timeout to avoid blocking
            boolean dataLockAcquired = false;
            try {
                dataLockAcquired = spawner.getDataLock().tryLock(50, java.util.concurrent.TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            
            if (!dataLockAcquired) {
                // dataLock is held (likely stack size change), skip this cycle
                return;
            }
            
            // Declare variables outside the try block so they're accessible in the async lambda
            final long currentTime = System.currentTimeMillis();
            final long spawnTime;
            final EntityType entityType;
            final int minMobs;
            final int maxMobs;
            final String spawnerId;
            final AtomicInteger usedSlots;
            final AtomicInteger maxSlots;
            
            try {
                // Timing is now managed by SpawnerRangeChecker (timer) and SpawnerGuiViewManager (spawn trigger)
                // No need for time check here since spawn is only called when timer expires
                
                // Get exact inventory slot usage
                usedSlots = new AtomicInteger(spawner.getVirtualInventory().getUsedSlots());
                maxSlots = new AtomicInteger(spawner.getMaxSpawnerLootSlots());

                // Check if both inventory and exp are full, only then skip loot generation
                if (usedSlots.get() >= maxSlots.get() && spawner.getSpawnerExp() >= spawner.getMaxStoredExp()) {
                    if (!spawner.getIsAtCapacity()) {
                        spawner.setIsAtCapacity(true);
                    }
                    return; // Skip generation if both exp and inventory are full
                }

                // Important: Store the current values we need for async processing
                entityType = spawner.getEntityType();
                minMobs = spawner.getMinMobs();
                maxMobs = spawner.getMaxMobs();
                spawnerId = spawner.getSpawnerId();
                // Store currentTime to update lastSpawnTime after successful loot addition
                spawnTime = currentTime;
            } finally {
                spawner.getDataLock().unlock();
            }

            // Run heavy calculations async and batch updates using the Scheduler
            Scheduler.runTaskAsync(() -> {
                // Generate loot with full mob count
                LootResult loot = generateLoot(minMobs, maxMobs, spawner);

                // Only proceed if we generated something
                if (loot.getItems().isEmpty() && loot.getExperience() == 0) {
                    return;
                }

                // Switch back to main thread for Bukkit API calls using location-aware scheduling
                Scheduler.runLocationTask(spawner.getSpawnerLocation(), () -> {
                    // Re-acquire the lock for the update phase
                    // This ensures the spawner hasn't been modified (like stack size changes)
                    // between our async calculations and now
                    boolean updateLockAcquired = spawner.getLootGenerationLock().tryLock();
                    if (!updateLockAcquired) {
                        // Lock is held, stack size is changing, skip this update
                        return;
                    }

                    try {
                        // Modified approach: Handle items and exp separately
                        boolean changed = false;

                        // Process experience if there's any to add and not at max
                        if (loot.getExperience() > 0 && spawner.getSpawnerExp() < spawner.getMaxStoredExp()) {
                            int currentExp = spawner.getSpawnerExp();
                            int maxExp = spawner.getMaxStoredExp();
                            int newExp = Math.min(currentExp + loot.getExperience(), maxExp);

                            if (newExp != currentExp) {
                                spawner.setSpawnerExp(newExp);
                                changed = true;
                            }
                        }

                        // Re-check max slots as it could have changed
                        maxSlots.set(spawner.getMaxSpawnerLootSlots());
                        usedSlots.set(spawner.getVirtualInventory().getUsedSlots());

                        // Process items if there are any to add and inventory isn't completely full
                        if (!loot.getItems().isEmpty() && usedSlots.get() < maxSlots.get()) {
                            List<ItemStack> itemsToAdd = new ArrayList<>(loot.getItems());

                            // Get exact calculation of slots with the new items
                            int totalRequiredSlots = calculateRequiredSlots(itemsToAdd, spawner.getVirtualInventory());

                            // If we'll exceed the limit, limit the items we're adding
                            if (totalRequiredSlots > maxSlots.get()) {
                                itemsToAdd = limitItemsToAvailableSlots(itemsToAdd, spawner);
                            }

                            if (!itemsToAdd.isEmpty()) {
                                spawner.addItemsAndUpdateSellValue(itemsToAdd);
                                changed = true;
                            }
                        }

                        if (!changed) {
                            return;
                        }

                        // Update spawn time only after successful loot addition
                        // This prevents skipped spawns when the lock fails
                        // Must acquire dataLock to safely update lastSpawnTime
                        boolean updateDataLockAcquired = spawner.getDataLock().tryLock();
                        if (updateDataLockAcquired) {
                            try {
                                spawner.setLastSpawnTime(spawnTime);
                            } finally {
                                spawner.getDataLock().unlock();
                            }
                        }

                        // Check if spawner is now at capacity and update status if needed
                        spawner.updateCapacityStatus();

                        // Handle GUI updates in batches
                        handleGuiUpdates(spawner);

                        // Mark for saving only once
                        spawnerManager.markSpawnerModified(spawner.getSpawnerId());
                    } finally {
                        spawner.getLootGenerationLock().unlock();
                    }
                });
            });
        } finally {
            spawner.getLootGenerationLock().unlock();
        }
    }

    public LootResult generateLoot(int minMobs, int maxMobs, SpawnerData spawner) {

        int mobCount = random.nextInt(maxMobs - minMobs + 1) + minMobs;
        int totalExperience = spawner.getEntityExperienceValue() * mobCount;

        // Get valid items from the spawner's EntityLootConfig
        List<LootItem> validItems =  spawner.getValidLootItems();

        if (validItems.isEmpty()) {
            return new LootResult(Collections.emptyList(), totalExperience);
        }

        // Use a Map to consolidate identical drops instead of List
        Map<ItemStack, Integer> consolidatedLoot = new HashMap<>();

        // Process mobs in batch rather than individually
        for (LootItem lootItem : validItems) {
            // Calculate the probability for the entire mob batch at once
            int successfulDrops = 0;

            // Calculate binomial distribution - how many mobs will drop this item
            for (int i = 0; i < mobCount; i++) {
                if (random.nextDouble() * 100 <= lootItem.getChance()) {
                    successfulDrops++;
                }
            }

            if (successfulDrops > 0) {
                // Create item just once per loot type
                ItemStack prototype = lootItem.createItemStack(random);
                if (prototype != null) {
                    // Total amount across all mobs
                    int totalAmount = 0;
                    for (int i = 0; i < successfulDrops; i++) {
                        totalAmount += lootItem.generateAmount(random);
                    }

                    if (totalAmount > 0) {
                        // Add to consolidated map
                        consolidatedLoot.merge(prototype, totalAmount, Integer::sum);
                    }
                }
            }
        }

        // Convert consolidated map to item stacks
        List<ItemStack> finalLoot = new ArrayList<>(consolidatedLoot.size());
        for (Map.Entry<ItemStack, Integer> entry : consolidatedLoot.entrySet()) {
            ItemStack item = entry.getKey().clone();
            item.setAmount(Math.min(entry.getValue(), item.getMaxStackSize()));
            finalLoot.add(item);

            // Handle amounts exceeding max stack size
            int remaining = entry.getValue() - item.getMaxStackSize();
            while (remaining > 0) {
                ItemStack extraStack = item.clone();
                extraStack.setAmount(Math.min(remaining, item.getMaxStackSize()));
                finalLoot.add(extraStack);
                remaining -= extraStack.getAmount();
            }
        }

        return new LootResult(finalLoot, totalExperience);
    }

    private List<ItemStack> limitItemsToAvailableSlots(List<ItemStack> items, SpawnerData spawner) {
        VirtualInventory currentInventory = spawner.getVirtualInventory();
        int maxSlots = spawner.getMaxSpawnerLootSlots();

        // If already full, return empty list
        if (currentInventory.getUsedSlots() >= maxSlots) {
            return Collections.emptyList();
        }

        // Create a simulation inventory
        Map<VirtualInventory.ItemSignature, Long> simulatedInventory = new HashMap<>(currentInventory.getConsolidatedItems());
        List<ItemStack> acceptedItems = new ArrayList<>();

        // Sort items by priority (you can change this sorting strategy)
        items.sort(Comparator.comparing(item -> item.getType().name()));

        for (ItemStack item : items) {
            if (item == null || item.getAmount() <= 0) continue;

            // Add to simulation and check slot count
            Map<VirtualInventory.ItemSignature, Long> tempSimulation = new HashMap<>(simulatedInventory);
            VirtualInventory.ItemSignature sig = new VirtualInventory.ItemSignature(item);
            tempSimulation.merge(sig, (long) item.getAmount(), Long::sum);

            // Calculate slots needed
            int slotsNeeded = calculateSlots(tempSimulation);

            // If we still have room, accept this item
            if (slotsNeeded <= maxSlots) {
                acceptedItems.add(item);
                simulatedInventory = tempSimulation; // Update simulation
            } else {
                // Try to accept a partial amount of this item
                int maxStackSize = item.getMaxStackSize();
                long currentAmount = simulatedInventory.getOrDefault(sig, 0L);

                // Calculate how many we can add without exceeding slot limit
                int remainingSlots = maxSlots - calculateSlots(simulatedInventory);
                if (remainingSlots > 0) {
                    // Maximum items we can add in the remaining slots
                    long maxAddAmount = remainingSlots * maxStackSize - (currentAmount % maxStackSize);
                    if (maxAddAmount > 0) {
                        // Create a partial item
                        ItemStack partialItem = item.clone();
                        partialItem.setAmount((int) Math.min(maxAddAmount, item.getAmount()));
                        acceptedItems.add(partialItem);

                        // Update simulation
                        simulatedInventory.merge(sig, (long) partialItem.getAmount(), Long::sum);
                    }
                }

                // We've filled all slots, stop processing
                break;
            }
        }

        return acceptedItems;
    }

    private int calculateSlots(Map<VirtualInventory.ItemSignature, Long> items) {
        // Use a more efficient calculation approach
        return items.entrySet().stream()
                .mapToInt(entry -> {
                    long amount = entry.getValue();
                    int maxStackSize = entry.getKey().getTemplateRef().getMaxStackSize();
                    // Use integer division with ceiling function
                    return (int) ((amount + maxStackSize - 1) / maxStackSize);
                })
                .sum();
    }

    private int calculateRequiredSlots(List<ItemStack> items, VirtualInventory inventory) {
        // Create a temporary map to simulate how items would stack
        Map<VirtualInventory.ItemSignature, Long> simulatedItems = new HashMap<>();

        // First, get existing items if we need to account for them
        if (inventory != null) {
            simulatedItems.putAll(inventory.getConsolidatedItems());
        }

        // Add the new items to our simulation
        for (ItemStack item : items) {
            if (item == null || item.getAmount() <= 0) continue;

            VirtualInventory.ItemSignature sig = new VirtualInventory.ItemSignature(item);
            simulatedItems.merge(sig, (long) item.getAmount(), Long::sum);
        }

        // Calculate exact slots needed
        return calculateSlots(simulatedItems);
    }

    private void handleGuiUpdates(SpawnerData spawner) {
        // Show particles if needed
        if (plugin.getConfig().getBoolean("particle.spawner_generate_loot", true)) {
            Location loc = spawner.getSpawnerLocation();
            World world = loc.getWorld();
            if (world != null) {
                Scheduler.runLocationTask(loc, () -> {
                    world.spawnParticle(Particle.HAPPY_VILLAGER,
                            loc.clone().add(0.5, 0.5, 0.5),
                            10, 0.3, 0.3, 0.3, 0);
                });
            }
        }

        spawnerGuiViewManager.updateSpawnerMenuViewers(spawner);

        if (plugin.getConfig().getBoolean("hologram.enabled", false)) {
            spawner.updateHologramData();
        }
    }
    
    /**
     * Pre-generate loot asynchronously without adding it to the spawner.
     * This improves UX by preparing loot before the timer expires.
     * 
     * @param spawner The spawner to generate loot for
     * @param callback Callback to receive the generated items and experience
     */
    public void preGenerateLoot(SpawnerData spawner, LootGenerationCallback callback) {
        // Acquire lock to prevent concurrent generation
        boolean lockAcquired = spawner.getLootGenerationLock().tryLock();
        if (!lockAcquired) {
            callback.onLootGenerated(Collections.emptyList(), 0);
            return;
        }

        try {
            // Check if spawner is at capacity
            boolean dataLockAcquired = false;
            try {
                dataLockAcquired = spawner.getDataLock().tryLock(50, java.util.concurrent.TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                callback.onLootGenerated(Collections.emptyList(), 0);
                return;
            }
            
            if (!dataLockAcquired) {
                callback.onLootGenerated(Collections.emptyList(), 0);
                return;
            }

            final int minMobs;
            final int maxMobs;
            
            try {
                // Check capacity
                int usedSlots = spawner.getVirtualInventory().getUsedSlots();
                int maxSlots = spawner.getMaxSpawnerLootSlots();
                
                if (usedSlots >= maxSlots && spawner.getSpawnerExp() >= spawner.getMaxStoredExp()) {
                    callback.onLootGenerated(Collections.emptyList(), 0);
                    return;
                }

                minMobs = spawner.getMinMobs();
                maxMobs = spawner.getMaxMobs();
            } finally {
                spawner.getDataLock().unlock();
            }

            // Generate loot asynchronously
            Scheduler.runTaskAsync(() -> {
                LootResult loot = generateLoot(minMobs, maxMobs, spawner);
                
                // Convert to list format for callback
                List<ItemStack> items = new ArrayList<>();
                if (loot.getItems() != null) {
                    items.addAll(loot.getItems());
                }
                
                callback.onLootGenerated(items, loot.getExperience());
            });
        } finally {
            spawner.getLootGenerationLock().unlock();
        }
    }
    
    /**
     * Add pre-generated loot to the spawner immediately.
     * This is called when the timer expires and loot has been pre-generated.
     * 
     * @param spawner The spawner to add loot to
     * @param items Pre-generated items
     * @param experience Pre-generated experience
     */
    public void addPreGeneratedLoot(SpawnerData spawner, List<ItemStack> items, int experience) {
        if (items == null || (items.isEmpty() && experience == 0)) {
            return;
        }

        // Acquire lock
        boolean lockAcquired = spawner.getLootGenerationLock().tryLock();
        if (!lockAcquired) {
            return;
        }

        try {
            boolean dataLockAcquired = false;
            try {
                dataLockAcquired = spawner.getDataLock().tryLock(50, java.util.concurrent.TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            
            if (!dataLockAcquired) {
                return;
            }

            final long spawnTime = System.currentTimeMillis();
            final AtomicInteger usedSlots;
            final AtomicInteger maxSlots;
            
            try {
                usedSlots = new AtomicInteger(spawner.getVirtualInventory().getUsedSlots());
                maxSlots = new AtomicInteger(spawner.getMaxSpawnerLootSlots());
                
                // Recheck capacity (in case it changed since pre-generation)
                if (usedSlots.get() >= maxSlots.get() && spawner.getSpawnerExp() >= spawner.getMaxStoredExp()) {
                    return;
                }
            } finally {
                spawner.getDataLock().unlock();
            }

            // Add items and exp to spawner
            Scheduler.runTaskAsync(() -> {
                boolean changed = false;
                
                // Add experience
                if (experience > 0) {
                    int added = spawner.addExperience(experience);
                    if (added > 0) {
                        changed = true;
                    }
                }

                // Add items
                if (!items.isEmpty()) {
                    List<ItemStack> itemsToAdd = new ArrayList<>();
                    for (ItemStack item : items) {
                        if (item != null && item.getType() != Material.AIR) {
                            itemsToAdd.add(item.clone());
                        }
                    }

                    if (!itemsToAdd.isEmpty()) {
                        spawner.addItemsAndUpdateSellValue(itemsToAdd);
                        changed = true;
                    }
                }

                if (!changed) {
                    return;
                }

                // Update lastSpawnTime after successful addition
                boolean updateLockAcquired = spawner.getDataLock().tryLock();
                if (updateLockAcquired) {
                    try {
                        spawner.setLastSpawnTime(spawnTime);
                    } finally {
                        spawner.getDataLock().unlock();
                    }
                }

                // Update capacity status
                spawner.updateCapacityStatus();

                // Update GUIs
                handleGuiUpdates(spawner);

                // Mark for saving
                spawnerManager.markSpawnerModified(spawner.getSpawnerId());
            });
        } finally {
            spawner.getLootGenerationLock().unlock();
        }
    }
    
    /**
     * Callback interface for pre-generation
     */
    @FunctionalInterface
    public interface LootGenerationCallback {
        void onLootGenerated(List<ItemStack> items, int experience);
    }
}