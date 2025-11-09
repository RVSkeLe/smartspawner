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
}