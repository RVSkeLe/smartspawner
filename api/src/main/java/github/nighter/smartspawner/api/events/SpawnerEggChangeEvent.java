package github.nighter.smartspawner.api.events;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Called when a spawner's entity type is changed using a spawn egg.
 */
@Getter
public class SpawnerEggChangeEvent extends Event implements Cancellable {

    private static final HandlerList handlers = new HandlerList();

    private final Player player;
    private final Location location;
    private final EntityType oldEntityType;
    private final EntityType newEntityType;

    @Setter
    private boolean cancelled = false;

    /**
     * Creates a new spawner egg change event.
     *
     * @param player the player who changed the spawner
     * @param location the location of the spawner
     * @param oldEntityType the previous entity type
     * @param newEntityType the new entity type
     */
    public SpawnerEggChangeEvent(Player player, Location location, EntityType oldEntityType, EntityType newEntityType) {
        this.player = player;
        this.location = location;
        this.oldEntityType = oldEntityType;
        this.newEntityType = newEntityType;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return handlers;
    }

    public static @NotNull HandlerList getHandlerList() {
        return handlers;
    }
}