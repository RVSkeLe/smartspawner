package github.nighter.smartspawner.api;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;

/**
 * Main API interface for SmartSpawner plugin.
 * This API allows other plugins to interact with SmartSpawner functionality.
 */
public interface SmartSpawnerAPI {

    /**
     * Creates a SmartSpawner item with the specified entity type
     *
     * @param entityType The type of entity this spawner will spawn
     * @return An ItemStack representing the spawner
     */
    ItemStack createSpawnerItem(EntityType entityType);

    /**
     * Creates a SmartSpawner item with the specified entity type and a custom amount
     *
     * @param entityType The type of entity this spawner will spawn
     * @param amount The amount of the item stack
     * @return An ItemStack representing the spawner
     */
    ItemStack createSpawnerItem(EntityType entityType, int amount);

    /**
     * Creates a vanilla spawner item without SmartSpawner features
     *
     * @param entityType The type of entity this spawner will spawn
     * @return An ItemStack representing the vanilla spawner
     */
    ItemStack createVanillaSpawnerItem(EntityType entityType);

    /**
     * Creates a vanilla spawner item without SmartSpawner features
     *
     * @param entityType The type of entity this spawner will spawn
     * @param amount The amount of the item stack
     * @return An ItemStack representing the vanilla spawner
     */
    ItemStack createVanillaSpawnerItem(EntityType entityType, int amount);

    /**
     * Creates an item spawner that spawns items instead of entities
     *
     * @param itemMaterial The material type for the item spawner
     * @return An ItemStack representing the item spawner
     */
    ItemStack createItemSpawnerItem(Material itemMaterial);

    /**
     * Creates an item spawner that spawns items instead of entities
     *
     * @param itemMaterial The material type for the item spawner
     * @param amount The amount of the item stack
     * @return An ItemStack representing the item spawner
     */
    ItemStack createItemSpawnerItem(Material itemMaterial, int amount);

    /**
     * Checks if an ItemStack is a SmartSpawner (custom spawner with SmartSpawner features)
     *
     * @param item The ItemStack to check
     * @return true if the item is a SmartSpawner, false otherwise
     */
    boolean isSmartSpawner(ItemStack item);

    /**
     * Checks if an ItemStack is a vanilla spawner (without SmartSpawner features)
     *
     * @param item The ItemStack to check
     * @return true if the item is a vanilla spawner, false otherwise
     */
    boolean isVanillaSpawner(ItemStack item);

    /**
     * Checks if an ItemStack is an item spawner (spawns items instead of entities)
     *
     * @param item The ItemStack to check
     * @return true if the item is an item spawner, false otherwise
     */
    boolean isItemSpawner(ItemStack item);

    /**
     * Gets the entity type from a spawner item
     *
     * @param item The spawner ItemStack
     * @return The EntityType of the spawner, or null if not a valid spawner
     */
    EntityType getSpawnerEntityType(ItemStack item);

    /**
     * Gets the item material from an item spawner
     *
     * @param item The item spawner ItemStack
     * @return The Material that the item spawner spawns, or null if not a valid item spawner
     */
    Material getItemSpawnerMaterial(ItemStack item);
}