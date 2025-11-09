package github.nighter.smartspawner.api.events;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * SpawnerItemCreateEvent is called when a spawner item is being created programmatically.
 * This event covers all types of spawner items: smart spawners, vanilla spawners, and item spawners.
 */
public class SpawnerItemCreateEvent extends Event implements Cancellable {
    
    /**
     * Enum representing the type of spawner being created.
     */
    public enum SpawnerType {
        /** Smart spawner with custom features and loot tables */
        SMART_SPAWNER,
        /** Vanilla spawner without custom features */
        VANILLA_SPAWNER,
        /** Item spawner that spawns items instead of entities */
        ITEM_SPAWNER
    }
    
    @Getter
    private final SpawnerType spawnerType;
    
    @Getter
    private final EntityType entityType;
    
    @Getter
    private final Material itemMaterial;
    
    @Getter
    private final int amount;
    
    @Getter
    @Setter
    private ItemStack result;
    
    private boolean cancelled = false;
    
    private static final HandlerList handlers = new HandlerList();
    
    /**
     * Constructor for smart and vanilla spawners (entity-based).
     *
     * @param spawnerType The type of spawner being created.
     * @param entityType The entity type of the spawner.
     * @param amount The amount of spawners to create.
     * @param result The resulting ItemStack (can be modified by listeners).
     */
    public SpawnerItemCreateEvent(SpawnerType spawnerType, EntityType entityType, int amount, ItemStack result) {
        this.spawnerType = spawnerType;
        this.entityType = entityType;
        this.itemMaterial = null;
        this.amount = amount;
        this.result = result;
    }
    
    /**
     * Constructor for item spawners (material-based).
     *
     * @param itemMaterial The material type of the item spawner.
     * @param amount The amount of spawners to create.
     * @param result The resulting ItemStack (can be modified by listeners).
     */
    public SpawnerItemCreateEvent(Material itemMaterial, int amount, ItemStack result) {
        this.spawnerType = SpawnerType.ITEM_SPAWNER;
        this.entityType = null;
        this.itemMaterial = itemMaterial;
        this.amount = amount;
        this.result = result;
    }
    
    /**
     * @return Whether this event is for a smart spawner.
     */
    public boolean isSmartSpawner() {
        return spawnerType == SpawnerType.SMART_SPAWNER;
    }
    
    /**
     * @return Whether this event is for a vanilla spawner.
     */
    public boolean isVanillaSpawner() {
        return spawnerType == SpawnerType.VANILLA_SPAWNER;
    }
    
    /**
     * @return Whether this event is for an item spawner.
     */
    public boolean isItemSpawner() {
        return spawnerType == SpawnerType.ITEM_SPAWNER;
    }
    
    @Override
    public boolean isCancelled() {
        return cancelled;
    }
    
    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }
    
    @Override
    public @NotNull HandlerList getHandlers() {
        return handlers;
    }
    
    public static @NotNull HandlerList getHandlerList() {
        return handlers;
    }
}
