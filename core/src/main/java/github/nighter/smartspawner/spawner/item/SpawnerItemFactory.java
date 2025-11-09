package github.nighter.smartspawner.spawner.item;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.api.events.SpawnerItemCreateEvent;
import github.nighter.smartspawner.language.LanguageManager;
import github.nighter.smartspawner.nms.VersionInitializer;
import github.nighter.smartspawner.spawner.loot.EntityLootConfig;
import github.nighter.smartspawner.spawner.loot.LootItem;
import org.bukkit.Bukkit;
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

public class SpawnerItemFactory {

    private static final long CACHE_EXPIRY_TIME_MS = TimeUnit.MINUTES.toMillis(30);
    private static final int MAX_CACHE_SIZE = 100;

    private final SmartSpawner plugin;
    private final LanguageManager languageManager;
    private static NamespacedKey VANILLA_SPAWNER_KEY;
    private final Map<EntityType, ItemStack> spawnerItemCache = new HashMap<>();
    private final Map<EntityType, Long> cacheTimestamps = new HashMap<>();
    private long lastCacheCleanup = System.currentTimeMillis();

    public SpawnerItemFactory(SmartSpawner plugin) {
        this.plugin = plugin;
        this.languageManager = plugin.getLanguageManager();
        VANILLA_SPAWNER_KEY = new NamespacedKey(plugin, "vanilla_spawner");
    }

    public void reload() {
        clearAllCaches();
    }

    public void clearAllCaches() {
        spawnerItemCache.clear();
        cacheTimestamps.clear();
        lastCacheCleanup = System.currentTimeMillis();
    }

    private void cleanupCacheIfNeeded() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastCacheCleanup < TimeUnit.MINUTES.toMillis(1)) {
            return;
        }
        lastCacheCleanup = currentTime;
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
     * Creates a smart spawner item with custom features and loot tables.
     * This method is an alias for createCustomSpawnerItem for backward compatibility.
     *
     * @param entityType The entity type for the spawner
     * @return The created spawner ItemStack
     * @deprecated Use {@link #createCustomSpawnerItem(EntityType)} for clearer naming
     */
    @Deprecated
    public ItemStack createSmartSpawnerItem(EntityType entityType) {
        return createCustomSpawnerItem(entityType, 1);
    }

    /**
     * Creates a smart spawner item with custom features and loot tables.
     * This method is an alias for createCustomSpawnerItem for backward compatibility.
     *
     * @param entityType The entity type for the spawner
     * @param amount The amount of spawner items to create
     * @return The created spawner ItemStack
     * @deprecated Use {@link #createCustomSpawnerItem(EntityType, int)} for clearer naming
     */
    @Deprecated
    public ItemStack createSmartSpawnerItem(EntityType entityType, int amount) {
        return createCustomSpawnerItem(entityType, amount);
    }

    /**
     * Creates a custom spawner item with SmartSpawner features and loot tables.
     * Fires a SpawnerItemCreateEvent that can be cancelled or modified by listeners.
     *
     * @param entityType The entity type for the spawner
     * @return The created spawner ItemStack, or null if the event was cancelled
     */
    public ItemStack createCustomSpawnerItem(EntityType entityType) {
        return createCustomSpawnerItem(entityType, 1);
    }

    /**
     * Creates a custom spawner item with SmartSpawner features and loot tables.
     * Fires a SpawnerItemCreateEvent that can be cancelled or modified by listeners.
     *
     * @param entityType The entity type for the spawner
     * @param amount The amount of spawner items to create
     * @return The created spawner ItemStack, or null if the event was cancelled
     */
    public ItemStack createCustomSpawnerItem(EntityType entityType, int amount) {
        cleanupCacheIfNeeded();
        if (amount == 1) {
            ItemStack cachedItem = spawnerItemCache.get(entityType);
            if (cachedItem != null) {
                return cachedItem.clone();
            }
        }

        ItemStack spawner = new ItemStack(Material.SPAWNER, amount);
        ItemMeta meta = spawner.getItemMeta();
        if (meta != null && entityType != null && entityType != EntityType.UNKNOWN) {
            if (meta instanceof BlockStateMeta blockMeta) {
                BlockState blockState = blockMeta.getBlockState();
                if (blockState instanceof CreatureSpawner cs) {
                    cs.setSpawnedType(entityType);
                    blockMeta.setBlockState(cs);
                }
            }
            String entityTypeName = languageManager.getFormattedMobName(entityType);
            String entityTypeNameSmallCaps = languageManager.getSmallCaps(entityTypeName);
            EntityLootConfig lootConfig = plugin.getSpawnerSettingsConfig().getLootConfig(entityType);
            List<LootItem> lootItems = lootConfig != null ? lootConfig.getAllItems() : Collections.emptyList();
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("entity", entityTypeName);
            placeholders.put("ᴇɴᴛɪᴛʏ", entityTypeNameSmallCaps);
            placeholders.put("exp", String.valueOf(lootConfig != null ? lootConfig.getExperience() : 0));
            List<LootItem> sortedLootItems = new ArrayList<>(lootItems);
            sortedLootItems.sort(Comparator.comparing(item -> item.getMaterial().name()));
            if (!sortedLootItems.isEmpty()) {
                String lootFormat = languageManager.getItemName("custom_item.spawner.loot_items", placeholders);
                StringBuilder lootItemsBuilder = new StringBuilder();
                for (LootItem item : sortedLootItems) {
                    String itemName = languageManager.getVanillaItemName(item.getMaterial());
                    String itemNameSmallCaps = languageManager.getSmallCaps(itemName);
                    String amountRange = item.getMinAmount() == item.getMaxAmount() ?
                            String.valueOf(item.getMinAmount()) :
                            item.getMinAmount() + "-" + item.getMaxAmount();
                    String chance = String.format("%.1f", item.getChance());
                    Map<String, String> itemPlaceholders = new HashMap<>(placeholders);
                    itemPlaceholders.put("item_name", itemName);
                    itemPlaceholders.put("ɪᴛᴇᴍ_ɴᴀᴍᴇ", itemNameSmallCaps);
                    itemPlaceholders.put("amount", amountRange);
                    itemPlaceholders.put("chance", chance);
                    String formattedItem = languageManager.applyPlaceholdersAndColors(lootFormat, itemPlaceholders);
                    lootItemsBuilder.append(formattedItem).append("\n");
                }
                if (!lootItemsBuilder.isEmpty()) {
                    lootItemsBuilder.setLength(lootItemsBuilder.length() - 1);
                }
                placeholders.put("loot_items", lootItemsBuilder.toString());
            } else {
                placeholders.put("loot_items", languageManager.getItemName("custom_item.spawner.loot_items_empty", placeholders));
            }
            String displayName = languageManager.getItemName("custom_item.spawner.name", placeholders);
            meta.setDisplayName(displayName);
            List<String> lore = languageManager.getItemLoreWithMultilinePlaceholders("custom_item.spawner.lore", placeholders);
            if (lore != null && !lore.isEmpty()) {
                meta.setLore(lore);
            }
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
            spawner.setItemMeta(meta);
        }
        VersionInitializer.hideTooltip(spawner);
        if (amount == 1) {
            spawnerItemCache.put(entityType, spawner.clone());
            cacheTimestamps.put(entityType, System.currentTimeMillis());
            if (spawnerItemCache.size() > MAX_CACHE_SIZE) {
                EntityType oldestEntity = null;
                long oldestTime = Long.MAX_VALUE;
                for (Map.Entry<EntityType, Long> entry : cacheTimestamps.entrySet()) {
                    if (entry.getValue() < oldestTime) {
                        oldestTime = entry.getValue();
                        oldestEntity = entry.getKey();
                    }
                }
                if (oldestEntity != null) {
                    spawnerItemCache.remove(oldestEntity);
                    cacheTimestamps.remove(oldestEntity);
                }
            }
        }
        
        // Fire SpawnerItemCreateEvent
        SpawnerItemCreateEvent event = new SpawnerItemCreateEvent(
            SpawnerItemCreateEvent.SpawnerType.SMART_SPAWNER,
            entityType,
            amount,
            spawner
        );
        Bukkit.getPluginManager().callEvent(event);
        
        if (event.isCancelled()) {
            return null;
        }
        
        return event.getResult();
    }

    /**
     * Creates a vanilla spawner item without custom features.
     * Fires a SpawnerItemCreateEvent that can be cancelled or modified by listeners.
     *
     * @param entityType The entity type for the spawner
     * @return The created vanilla spawner ItemStack, or null if the event was cancelled
     */
    public ItemStack createVanillaSpawnerItem(EntityType entityType) {
        return createVanillaSpawnerItem(entityType, 1);
    }

    /**
     * Creates a vanilla spawner item without custom features.
     * Fires a SpawnerItemCreateEvent that can be cancelled or modified by listeners.
     *
     * @param entityType The entity type for the spawner
     * @param amount The amount of spawner items to create
     * @return The created vanilla spawner ItemStack, or null if the event was cancelled
     */
    public ItemStack createVanillaSpawnerItem(EntityType entityType, int amount) {
        ItemStack spawner = new ItemStack(Material.SPAWNER, amount);
        ItemMeta meta = spawner.getItemMeta();
        if (meta != null && entityType != null && entityType != EntityType.UNKNOWN) {
            if (meta instanceof BlockStateMeta blockMeta) {
                BlockState blockState = blockMeta.getBlockState();
                if (blockState instanceof CreatureSpawner cs) {
                    cs.setSpawnedType(entityType);
                    blockMeta.setBlockState(cs);
                }
            }
            String entityTypeName = languageManager.getFormattedMobName(entityType);
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("entity", entityTypeName);
            placeholders.put("ᴇɴᴛɪᴛʏ", languageManager.getSmallCaps(entityTypeName));
            String displayName = languageManager.getItemName("custom_item.vanilla_spawner.name", placeholders);
            if (displayName != null && !displayName.isEmpty() && !displayName.equals("custom_item.vanilla_spawner.name")) {
                meta.setDisplayName(displayName);
            }
            List<String> lore = languageManager.getItemLoreWithMultilinePlaceholders("custom_item.vanilla_spawner.lore", placeholders);
            if (lore != null && !lore.isEmpty()) {
                meta.setLore(lore);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
                VersionInitializer.hideTooltip(spawner);
            }
            meta.getPersistentDataContainer().set(
                    VANILLA_SPAWNER_KEY,
                    PersistentDataType.BOOLEAN,
                    true
            );
            spawner.setItemMeta(meta);
        }
        
        // Fire SpawnerItemCreateEvent
        SpawnerItemCreateEvent event = new SpawnerItemCreateEvent(
            SpawnerItemCreateEvent.SpawnerType.VANILLA_SPAWNER,
            entityType,
            amount,
            spawner
        );
        Bukkit.getPluginManager().callEvent(event);
        
        if (event.isCancelled()) {
            return null;
        }
        
        return event.getResult();
    }

    /**
     * Creates an item spawner item that spawns items instead of entities.
     * Fires a SpawnerItemCreateEvent that can be cancelled or modified by listeners.
     *
     * @param itemMaterial The material type for the item spawner
     * @return The created item spawner ItemStack, or null if the event was cancelled
     */
    public ItemStack createItemSpawnerItem(Material itemMaterial) {
        return createItemSpawnerItem(itemMaterial, 1);
    }

    /**
     * Creates an item spawner item that spawns items instead of entities.
     * Fires a SpawnerItemCreateEvent that can be cancelled or modified by listeners.
     *
     * @param itemMaterial The material type for the item spawner
     * @param amount The amount of spawner items to create
     * @return The created item spawner ItemStack, or null if the event was cancelled
     */
    public ItemStack createItemSpawnerItem(Material itemMaterial, int amount) {
        ItemStack spawner = new ItemStack(Material.SPAWNER, amount);
        ItemMeta meta = spawner.getItemMeta();
        if (meta != null && itemMaterial != null) {
            if (meta instanceof BlockStateMeta blockMeta) {
                BlockState blockState = blockMeta.getBlockState();
                if (blockState instanceof CreatureSpawner cs) {
                    // Set to ITEM type for item spawners
                    cs.setSpawnedType(EntityType.ITEM);
                    blockMeta.setBlockState(cs);
                }
            }
            
            String itemName = languageManager.getVanillaItemName(itemMaterial);
            String itemNameSmallCaps = languageManager.getSmallCaps(itemName);
            
            // Get loot config for this item spawner
            EntityLootConfig lootConfig = plugin.getItemSpawnerSettingsConfig().getLootConfig(itemMaterial);
            List<LootItem> lootItems = lootConfig != null ? lootConfig.getAllItems() : Collections.emptyList();
            
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("entity", itemName);
            placeholders.put("ᴇɴᴛɪᴛʏ", itemNameSmallCaps);
            placeholders.put("exp", String.valueOf(lootConfig != null ? lootConfig.getExperience() : 0));
            
            // Build loot items list similar to regular spawners
            List<LootItem> sortedLootItems = new ArrayList<>(lootItems);
            sortedLootItems.sort(Comparator.comparing(item -> item.getMaterial().name()));
            if (!sortedLootItems.isEmpty()) {
                String lootFormat = languageManager.getItemName("custom_item.item_spawner.loot_items", placeholders);
                StringBuilder lootItemsBuilder = new StringBuilder();
                for (LootItem item : sortedLootItems) {
                    String lootItemName = languageManager.getVanillaItemName(item.getMaterial());
                    String lootItemNameSmallCaps = languageManager.getSmallCaps(lootItemName);
                    String amountRange = item.getMinAmount() == item.getMaxAmount() ?
                            String.valueOf(item.getMinAmount()) :
                            item.getMinAmount() + "-" + item.getMaxAmount();
                    String chance = String.format("%.1f", item.getChance());
                    Map<String, String> itemPlaceholders = new HashMap<>(placeholders);
                    itemPlaceholders.put("item_name", lootItemName);
                    itemPlaceholders.put("ɪᴛᴇᴍ_ɴᴀᴍᴇ", lootItemNameSmallCaps);
                    itemPlaceholders.put("amount", amountRange);
                    itemPlaceholders.put("chance", chance);
                    String formattedItem = languageManager.applyPlaceholdersAndColors(lootFormat, itemPlaceholders);
                    lootItemsBuilder.append(formattedItem).append("\n");
                }
                if (!lootItemsBuilder.isEmpty()) {
                    lootItemsBuilder.setLength(lootItemsBuilder.length() - 1);
                }
                placeholders.put("loot_items", lootItemsBuilder.toString());
            } else {
                placeholders.put("loot_items", languageManager.getItemName("custom_item.item_spawner.loot_items_empty", placeholders));
            }
            
            String displayName = languageManager.getItemName("custom_item.item_spawner.name", placeholders);
            if (displayName == null || displayName.isEmpty() || displayName.equals("custom_item.item_spawner.name")) {
                // Fallback to a generic name if not configured
                displayName = "§6" + itemName + " Spawner";
            }
            meta.setDisplayName(displayName);
            
            List<String> lore = languageManager.getItemLoreWithMultilinePlaceholders("custom_item.item_spawner.lore", placeholders);
            if (lore != null && !lore.isEmpty()) {
                meta.setLore(lore);
            }
            
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
            
            // Store the item material in persistent data
            meta.getPersistentDataContainer().set(
                    new NamespacedKey(plugin, "item_spawner_material"),
                    PersistentDataType.STRING,
                    itemMaterial.name()
            );
            
            spawner.setItemMeta(meta);
        }
        VersionInitializer.hideTooltip(spawner);
        
        // Fire SpawnerItemCreateEvent
        SpawnerItemCreateEvent event = new SpawnerItemCreateEvent(
            itemMaterial,
            amount,
            spawner
        );
        Bukkit.getPluginManager().callEvent(event);
        
        if (event.isCancelled()) {
            return null;
        }
        
        return event.getResult();
    }
}