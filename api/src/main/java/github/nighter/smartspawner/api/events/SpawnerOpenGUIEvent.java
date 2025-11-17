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
 * Called when a player opens a spawner GUI.
 */
@Getter
public class SpawnerOpenGUIEvent extends SpawnerEvent implements Cancellable {

    private static final HandlerList handlers = new HandlerList();

    private final Player player;
    private final EntityType entityType;
    private final boolean refresh;

    @Setter
    private boolean cancelled = false;

    /**
     * Creates a new spawner GUI open event.
     *
     * @param player the player opening the GUI
     * @param location the location of the spawner
     * @param entityType the entity type of the spawner
     * @param stackSize the stack size of the spawner
     * @param refresh whether this is a GUI refresh
     */
    public SpawnerOpenGUIEvent(Player player, Location location, EntityType entityType, int stackSize, boolean refresh) {
        super(location, stackSize);
        this.player = player;
        this.entityType = entityType;
        this.refresh = refresh;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return handlers;
    }

    public static @NotNull HandlerList getHandlerList() {
        return handlers;
    }
}