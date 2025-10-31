package github.nighter.smartspawner.spawner.item;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.language.LanguageManager;
import github.nighter.smartspawner.spawner.loot.EntityLootConfig;
import github.nighter.smartspawner.spawner.loot.EntityLootRegistry;
import github.nighter.smartspawner.spawner.loot.LootItem;
import io.papermc.paper.datacomponent.DataComponentType;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.TooltipDisplay;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.keys.DataComponentTypeKeys;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.BlockState;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Factory class for creating spawner items with optimized caching
 */
public class SpawnerItemFactory {

    private final SmartSpawner plugin;
    private final LanguageManager languageManager;
    private EntityLootRegistry entityLootRegistry;

    private static NamespacedKey VANILLA_SPAWNER_KEY;
    // private static NamespacedKey SMART_SPAWNER_KEY;
    private final Map<EntityType, ItemStack> spawnerItemCache = new HashMap<>();
    private final Map<EntityType, Long> cacheTimestamps = new HashMap<>();

    // Cache configuration
    private static final long CACHE_EXPIRY_TIME_MS = TimeUnit.MINUTES.toMillis(30); // Cache expires after 30 minutes
    private static final int MAX_CACHE_SIZE = 100; // Maximum number of cached spawner items

    private long lastCacheCleanup = System.currentTimeMillis();

    public SpawnerItemFactory(SmartSpawner plugin) {
        this.plugin = plugin;
        this.languageManager = plugin.getLanguageManager();
        this.entityLootRegistry = plugin.getEntityLootRegistry();
        VANILLA_SPAWNER_KEY = new NamespacedKey(plugin, "vanilla_spawner");
        // SMART_SPAWNER_KEY = new NamespacedKey(plugin, "smart_spawner");
    }

    public void reload() {
        this.entityLootRegistry = plugin.getEntityLootRegistry();
        // Clear caches on reload
        clearAllCaches();
    }

    /**
     * Clears all caches in the factory
     */
    public void clearAllCaches() {
        spawnerItemCache.clear();
        cacheTimestamps.clear();
        lastCacheCleanup = System.currentTimeMillis();
    }

    /**
     * Clean expired cache entries if needed
     */
    private void cleanupCacheIfNeeded() {
        long currentTime = System.currentTimeMillis();

        // Only clean up once every minute to avoid performance overhead
        if (currentTime - lastCacheCleanup < TimeUnit.MINUTES.toMillis(1)) {
            return;
        }

        // Mark this cleanup
        lastCacheCleanup = currentTime;

        // Remove expired entries
        Iterator<Map.Entry<EntityType, Long>> iterator = cacheTimestamps.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<EntityType, Long> entry = iterator.next();
            if (currentTime - entry.getValue() > CACHE_EXPIRY_TIME_MS) {
                EntityType type = entry.getKey();
                spawnerItemCache.remove(type);
                iterator.remove();
            }
        }
    }

    /**
     * Creates a spawner item for the given entity type
     */
    public ItemStack createSpawnerItem(EntityType entityType) {
        return createSpawnerItem(entityType, 1);
    }

    /**
     * Creates a spawner item for the given entity type and amount
     */
    public ItemStack createSpawnerItem(EntityType entityType, int amount) {
        // Check if we need to clean up the cache
        cleanupCacheIfNeeded();

        // For single-item spawners, we can use the cache
        if (amount == 1) {
            ItemStack cachedItem = spawnerItemCache.get(entityType);

            if (cachedItem != null) {
                // Return a clone of the cached item to prevent modification of the cached instance
                return cachedItem.clone();
            }
        }

        ItemStack spawner = new ItemStack(Material.SPAWNER, amount);
        ItemMeta meta = spawner.getItemMeta();

        if (meta != null && entityType != null && entityType != EntityType.UNKNOWN) {
            // Apply block state for the spawner
            if (meta instanceof BlockStateMeta blockMeta) {
                BlockState blockState = blockMeta.getBlockState();

                if (blockState instanceof CreatureSpawner cs) {
                    cs.setSpawnedType(entityType);
                    blockMeta.setBlockState(cs);
                }
            }

            // Get entity name - now using LanguageManager's cached method
            String entityTypeName = languageManager.getFormattedMobName(entityType);
            // Use LanguageManager's small caps method
            String entityTypeNameSmallCaps = languageManager.getSmallCaps(entityTypeName);

            EntityLootConfig lootConfig = entityLootRegistry.getLootConfig(entityType);
            List<LootItem> lootItems = lootConfig != null ? lootConfig.getAllItems() : Collections.emptyList();

            // Create placeholders map with both regular and small caps entity names
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("entity", entityTypeName);
            placeholders.put("ᴇɴᴛɪᴛʏ", entityTypeNameSmallCaps);
            placeholders.put("exp", String.valueOf(lootConfig != null ? lootConfig.getExperience() : 0));

            // Sort the loot items for consistent ordering
            List<LootItem> sortedLootItems = new ArrayList<>(lootItems);
            // Sort by material name to ensure consistent order
            sortedLootItems.sort(Comparator.comparing(item -> item.getMaterial().name()));
            // Sort by material name in reverse order (Z to A)

            if (!sortedLootItems.isEmpty()) {
                // Get the custom loot format from the config
                String lootFormat = languageManager.getItemName("custom_item.spawner.loot_items", placeholders);

                StringBuilder lootItemsBuilder = new StringBuilder();
                for (LootItem item : sortedLootItems) {
                    // Use LanguageManager's cached method
                    String itemName = languageManager.getVanillaItemName(item.getMaterial());
                    // Use LanguageManager's small caps method
                    String itemNameSmallCaps = languageManager.getSmallCaps(itemName);

                    String amountRange = item.getMinAmount() == item.getMaxAmount() ?
                            String.valueOf(item.getMinAmount()) :
                            item.getMinAmount() + "-" + item.getMaxAmount();
                    String chance = String.format("%.1f", item.getChance());

                    // Create placeholders specific to this item with both regular and small caps versions
                    Map<String, String> itemPlaceholders = new HashMap<>(placeholders);
                    itemPlaceholders.put("item_name", itemName);
                    itemPlaceholders.put("ɪᴛᴇᴍ_ɴᴀᴍᴇ", itemNameSmallCaps);
                    itemPlaceholders.put("amount", amountRange);
                    itemPlaceholders.put("chance", chance);

                    // Apply the custom format for each item using the language manager
                    String formattedItem = languageManager.applyPlaceholdersAndColors(lootFormat, itemPlaceholders);

                    lootItemsBuilder.append(formattedItem).append("\n");
                }

                // Remove the last newline character
                if (!lootItemsBuilder.isEmpty()) {
                    lootItemsBuilder.setLength(lootItemsBuilder.length() - 1);
                }
                placeholders.put("loot_items", lootItemsBuilder.toString());
            } else {
                // If no loot items, set a default message
                placeholders.put("loot_items", languageManager.getItemName("custom_item.spawner.loot_items_empty", placeholders));
            }

            // Get the localized name and lore using enhanced placeholder system
            String displayName = languageManager.getItemName("custom_item.spawner.name", placeholders);
            meta.setDisplayName(displayName);

            // Use our method to handle multi-line placeholders
            List<String> lore = languageManager.getItemLoreWithMultilinePlaceholders("custom_item.spawner.lore", placeholders);
            if (lore != null && !lore.isEmpty()) {
                meta.setLore(lore);
            }

            // Hide enchants and attributes
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);

            // Add hidden tag to identify as smart spawner
//            meta.getPersistentDataContainer().set(
//                    SMART_SPAWNER_KEY,
//                    PersistentDataType.BOOLEAN,
//                    true
//            );
            spawner.setItemMeta(meta);
        }

        // Cache the item for future use (only for single items)
        if (amount == 1) {
            spawnerItemCache.put(entityType, spawner.clone());
            cacheTimestamps.put(entityType, System.currentTimeMillis());

            // Manage cache size
            if (spawnerItemCache.size() > MAX_CACHE_SIZE) {
                // Find oldest entry
                EntityType oldestEntity = null;
                long oldestTime = Long.MAX_VALUE;

                for (Map.Entry<EntityType, Long> entry : cacheTimestamps.entrySet()) {
                    if (entry.getValue() < oldestTime) {
                        oldestTime = entry.getValue();
                        oldestEntity = entry.getKey();
                    }
                }

                // Remove oldest entry if found
                if (oldestEntity != null) {
                    spawnerItemCache.remove(oldestEntity);
                    cacheTimestamps.remove(oldestEntity);
                }
            }
        }
        Set<DataComponentType> hiddenComponent = new HashSet<>();
        hiddenComponent.add(RegistryAccess.registryAccess().getRegistry(RegistryKey.DATA_COMPONENT_TYPE).get(DataComponentTypeKeys.BLOCK_ENTITY_DATA));
        spawner.setData(DataComponentTypes.TOOLTIP_DISPLAY, TooltipDisplay.tooltipDisplay().hiddenComponents(hiddenComponent).build());
        return spawner;
    }

    public ItemStack createVanillaSpawnerItem(EntityType entityType) {
        return createVanillaSpawnerItem(entityType, 1);
    }

    public ItemStack createVanillaSpawnerItem(EntityType entityType, int amount) {
        ItemStack spawner = new ItemStack(Material.SPAWNER, amount);
        ItemMeta meta = spawner.getItemMeta();

        if (meta != null && entityType != null && entityType != EntityType.UNKNOWN) {
            // Apply block state for the spawner
            if (meta instanceof BlockStateMeta blockMeta) {
                BlockState blockState = blockMeta.getBlockState();

                if (blockState instanceof CreatureSpawner cs) {
                    cs.setSpawnedType(entityType);
                    blockMeta.setBlockState(cs);
                }
            }

            // Get entity name
            String entityTypeName = languageManager.getFormattedMobName(entityType);

            // Create placeholders map with entity name
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("entity", entityTypeName);
            placeholders.put("ᴇɴᴛɪᴛʏ", languageManager.getSmallCaps(entityTypeName));

            // Get vanilla-specific display name from config
            String displayName = languageManager.getItemName("custom_item.vanilla_spawner.name", placeholders);

            // Only set display name if it's not null or empty
            if (displayName != null && !displayName.isEmpty() && !displayName.equals("custom_item.vanilla_spawner.name")) {
                meta.setDisplayName(displayName);
            }

            // Get vanilla-specific lore from config
            List<String> lore = languageManager.getItemLoreWithMultilinePlaceholders("custom_item.vanilla_spawner.lore", placeholders);

            // Only set lore if it's not null or empty
            if (lore != null && !lore.isEmpty()) {
                meta.setLore(lore);

                // If we have custom lore, then also add the item flags to hide attributes
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
            }

            // Add hidden tag to identify as vanilla spawner
            meta.getPersistentDataContainer().set(
                    VANILLA_SPAWNER_KEY,
                    PersistentDataType.BOOLEAN,
                    true
            );

            spawner.setItemMeta(meta);
        }
        Set<DataComponentType> hiddenComponent = new HashSet<>();
        hiddenComponent.add(RegistryAccess.registryAccess().getRegistry(RegistryKey.DATA_COMPONENT_TYPE).get(DataComponentTypeKeys.BLOCK_ENTITY_DATA));
        spawner.setData(DataComponentTypes.TOOLTIP_DISPLAY, TooltipDisplay.tooltipDisplay().hiddenComponents(hiddenComponent).build());
        return spawner;
    }
}