package github.nighter.smartspawner.api.events;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Called when a spawner is broken by a player.
 * This event is cancellable.
 */
@Getter
public class SpawnerPlayerBreakEvent extends SpawnerBreakEvent implements Cancellable {

    private static final HandlerList handlers = new HandlerList();

    private final Player player;

    @Setter
    private boolean cancelled = false;

    /**
     * Creates a new spawner player break event.
     *
     * @param player the player who broke the spawner
     * @param location the location of the broken spawner
     * @param quantity the quantity of the spawner
     */
    public SpawnerPlayerBreakEvent(Player player, Location location, int quantity) {
        super(player, location, quantity);
        this.player = player;
    }


    @Override
    public @NotNull HandlerList getHandlers() {
        return handlers;
    }

    public static @NotNull HandlerList getHandlerList() {
        return handlers;
    }
}