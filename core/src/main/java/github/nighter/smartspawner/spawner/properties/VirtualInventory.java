package github.nighter.smartspawner.spawner.properties;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import lombok.Getter;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class VirtualInventory {
    private final Map<ItemSignature, Long> consolidatedItems;
    @Getter private int maxSlots;
    // Cache sorted entries to avoid resorting when display isn't changing
    private List<Map.Entry<ItemSignature, Long>> sortedEntriesCache;
    private org.bukkit.Material preferredSortMaterial;

    public VirtualInventory(int maxSlots) {
        this.maxSlots = maxSlots;
        this.consolidatedItems = new ConcurrentHashMap<>();
        this.sortedEntriesCache = null;
        this.preferredSortMaterial = null;
    }

    public static ItemSignature getSignature(ItemStack item) {
        return new ItemSignature(item);
    }

    /*
     * FAST PATH
     * Used for loading already-consolidated storage data.
     */
    public void addItem(ItemStack item, long amount) {
        if (item == null || amount <= 0) {
            return;
        }

        ItemSignature signature = getSignature(item);

        consolidatedItems.merge(signature, amount, Long::sum);

        sortedEntriesCache = null;
    }

    /*
     * Bulk insert for physical item stacks.
     */
    public void addItems(List<ItemStack> items) {
        if (items.isEmpty()) {
            return;
        }

        Map<ItemSignature, Long> itemBatch = new HashMap<>(items.size());

        for (ItemStack item : items) {
            if (item == null) {
                continue;
            }

            int amount = item.getAmount();

            if (amount <= 0) {
                continue;
            }

            ItemSignature signature = getSignature(item);

            itemBatch.merge(signature, (long) amount, Long::sum);
        }

        if (itemBatch.isEmpty()) {
            return;
        }

        for (Map.Entry<ItemSignature, Long> entry : itemBatch.entrySet()) {
            consolidatedItems.merge(entry.getKey(), entry.getValue(), Long::sum);
        }

        sortedEntriesCache = null;
    }

    public boolean removeItems(List<ItemStack> items) {
        if (items.isEmpty()) {
            return true;
        }

        Map<ItemSignature, Long> toRemove = new HashMap<>();

        for (ItemStack item : items) {
            if (item == null) {
                continue;
            }

            int amount = item.getAmount();

            if (amount <= 0) {
                continue;
            }

            ItemSignature signature = getSignature(item);

            toRemove.merge(signature, (long) amount, Long::sum);
        }

        if (toRemove.isEmpty()) {
            return true;
        }

        for (Map.Entry<ItemSignature, Long> entry : toRemove.entrySet()) {
            long currentAmount = consolidatedItems.getOrDefault(entry.getKey(), 0L);

            if (currentAmount < entry.getValue()) {
                return false;
            }
        }

        for (Map.Entry<ItemSignature, Long> entry : toRemove.entrySet()) {
            ItemSignature signature = entry.getKey();
            long amountToRemove = entry.getValue();

            consolidatedItems.computeIfPresent(signature, (key, current) -> {
                long remaining = current - amountToRemove;
                return remaining <= 0 ? null : remaining;
            });
        }

        sortedEntriesCache = null;

        return true;
    }

    public Int2ObjectMap<ItemStack> getDisplayPage(int page, int pageSize) {
        if (pageSize <= 0) {
            return Int2ObjectMaps.emptyMap();
        }

        int safePage = Math.max(1, page);
        int startSlot = (safePage - 1) * pageSize;
        return buildDisplaySection(startSlot, pageSize);
    }

    public Int2ObjectMap<ItemStack> getDisplayRange(int startSlot, int maxResults) {
        return buildDisplaySection(startSlot, maxResults);
    }

    public Map<ItemSignature, Long> getConsolidatedItems() {
        return new HashMap<>(consolidatedItems);
    }

    public int getUsedSlots() {
        if (consolidatedItems.isEmpty()) {
            return 0;
        }

        // Quick estimate - not perfectly accurate but avoids full rebuilds
        int estimatedSlots = 0;
        for (Map.Entry<ItemSignature, Long> entry : consolidatedItems.entrySet()) {
            long amount = entry.getValue();
            int maxStackSize = entry.getKey().getMaxStackSize();
            estimatedSlots += (int) Math.ceil((double) amount / maxStackSize);
            if (estimatedSlots >= maxSlots) {
                return maxSlots; // Cap at max slots
            }
        }
        return estimatedSlots;
    }

    /**
     * Sorts items with the specified material type prioritized first.
     * This method optimizes by only invalidating caches when necessary.
     * 
     * @param preferredMaterial The material to sort first, or null for no preference
     */
    public void sortItems(org.bukkit.Material preferredMaterial) {
        // Store the preferred material for future cache rebuilds
        this.preferredSortMaterial = preferredMaterial;
        
        // Clear the sorted cache to force re-sorting with new preference
        this.sortedEntriesCache = null;
        
        // Only proceed if we have items to sort
        if (consolidatedItems.isEmpty()) {
            return;
        }
        
        // Generate new sorted entries with preference
        if (preferredMaterial != null) {
            this.sortedEntriesCache = consolidatedItems.entrySet().stream()
                .sorted((e1, e2) -> {
                    // Use getTemplateRef() to avoid cloning - we only need to read the type
                    boolean e1Preferred = e1.getKey().getMaterial() == preferredMaterial;
                    boolean e2Preferred = e2.getKey().getMaterial() == preferredMaterial;

                    if (e1Preferred && !e2Preferred) return -1;
                    if (!e1Preferred && e2Preferred) return 1;
                    
                    // Both preferred or both not preferred, sort by material name
                    return e1.getKey().getMaterialName().compareTo(e2.getKey().getMaterialName());
                })
                .collect(java.util.stream.Collectors.toList());
        } else {
            // No preference, sort alphabetically by material name
            this.sortedEntriesCache = consolidatedItems.entrySet().stream()
                .sorted(Comparator.comparing(e -> e.getKey().getMaterialName()))
                .collect(java.util.stream.Collectors.toList());
        }
    }

    private Int2ObjectMap<ItemStack> buildDisplaySection(int startSlot, int maxResults) {
        if (maxResults <= 0 || startSlot >= maxSlots) {
            return Int2ObjectMaps.emptyMap();
        }

        if (consolidatedItems.isEmpty()) {
            return Int2ObjectMaps.emptyMap();
        }

        int safeStart = Math.max(0, startSlot);
        int sectionLimit = Math.min(maxResults, maxSlots - safeStart);
        if (sectionLimit <= 0) {
            return Int2ObjectMaps.emptyMap();
        }

        Int2ObjectOpenHashMap<ItemStack> section = new Int2ObjectOpenHashMap<>(Math.min(sectionLimit, 45));
        List<Map.Entry<ItemSignature, Long>> sortedEntries = getSortedEntries();

        int currentGlobalSlot = 0;
        int relativeSlot = 0;

        for (Map.Entry<ItemSignature, Long> entry : sortedEntries) {
            if (relativeSlot >= sectionLimit || currentGlobalSlot >= maxSlots) {
                break;
            }

            ItemSignature sig = entry.getKey();
            int maxStackSize = sig.getMaxStackSize();
            if (maxStackSize <= 0) {
                continue;
            }

            long totalAmount = entry.getValue();
            int stacksForEntry = (int) Math.min(
                    Integer.MAX_VALUE,
                    (totalAmount + maxStackSize - 1L) / maxStackSize
            );

            if (currentGlobalSlot + stacksForEntry <= safeStart) {
                currentGlobalSlot += stacksForEntry;
                continue;
            }

            int stacksToSkip = Math.max(0, safeStart - currentGlobalSlot);
            long remainingAmount = totalAmount - ((long) stacksToSkip * maxStackSize);
            currentGlobalSlot += stacksToSkip;

            ItemStack templateItem = sig.getTemplateRef();
            while (remainingAmount > 0 && relativeSlot < sectionLimit && currentGlobalSlot < maxSlots) {
                ItemStack displayItem = templateItem.clone();
                displayItem.setAmount((int) Math.min(remainingAmount, maxStackSize));
                section.put(relativeSlot++, displayItem);

                remainingAmount -= maxStackSize;
                currentGlobalSlot++;
            }
        }

        return Int2ObjectMaps.unmodifiable(section);
    }

    private List<Map.Entry<ItemSignature, Long>> getSortedEntries() {
        if (sortedEntriesCache == null) {
            sortedEntriesCache = new ArrayList<>(consolidatedItems.entrySet());
            sortEntries(sortedEntriesCache);
        }
        return sortedEntriesCache;
    }

    private void sortEntries(List<Map.Entry<ItemSignature, Long>> entries) {
        if (preferredSortMaterial != null) {
            entries.sort((e1, e2) -> {
                boolean e1Preferred = e1.getKey().getMaterial() == preferredSortMaterial;
                boolean e2Preferred = e2.getKey().getMaterial() == preferredSortMaterial;

                if (e1Preferred && !e2Preferred) return -1;
                if (!e1Preferred && e2Preferred) return 1;

                return e1.getKey().getMaterialName().compareTo(e2.getKey().getMaterialName());
            });
            return;
        }

        entries.sort(Comparator.comparing(e -> e.getKey().getMaterialName()));
    }
}
