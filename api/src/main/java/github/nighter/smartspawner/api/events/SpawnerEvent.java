package github.nighter.smartspawner.api.events;

import lombok.Getter;
import org.bukkit.Location;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Base event class for all spawner-related events.
 */
@Getter
public abstract class SpawnerEvent extends Event {

    private static final HandlerList handlers = new HandlerList();

    private final Location location;
    private final int quantity;

    /**
     * Creates a new spawner event.
     *
     * @param location the location of the spawner
     * @param quantity the quantity/stack size of the spawner
     */
    protected SpawnerEvent(Location location, int quantity) {
        this.location = location;
        this.quantity = quantity;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return handlers;
    }

    public static @NotNull HandlerList getHandlerList() {
        return handlers;
    }
}

