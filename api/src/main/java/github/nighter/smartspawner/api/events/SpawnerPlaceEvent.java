package github.nighter.smartspawner.api.events;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Called when a spawner is placed by a player.
 */
@Getter
public class SpawnerPlaceEvent extends SpawnerEvent implements Cancellable {

    private static final HandlerList handlers = new HandlerList();

    private final Player player;
    private final EntityType entityType;

    @Setter
    private boolean cancelled = false;

    /**
     * Creates a new spawner place event.
     *
     * @param player the player who placed the spawner
     * @param location the location where the spawner was placed
     * @param entityType the entity type of the spawner
     * @param quantity the quantity of the spawner
     */
    public SpawnerPlaceEvent(Player player, Location location, EntityType entityType, int quantity) {
        super(location, quantity);
        this.player = player;
        this.entityType = entityType;
    }


    @Override
    public @NotNull HandlerList getHandlers() {
        return handlers;
    }

    public static @NotNull HandlerList getHandlerList() {
        return handlers;
    }
}