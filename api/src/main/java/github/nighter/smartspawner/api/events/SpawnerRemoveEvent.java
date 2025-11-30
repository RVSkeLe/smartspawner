package github.nighter.smartspawner.api.events;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Called when spawners are unstacked/removed from the stacker GUI.
 */
@Getter
public class SpawnerRemoveEvent extends SpawnerEvent implements Cancellable {

    private static final HandlerList handlers = new HandlerList();

    private final Player player;
    private final int changeAmount;

    @Setter
    private boolean cancelled = false;

    /**
     * Creates a new spawner remove event.
     *
     * @param player the player who removed spawners
     * @param location the location of the spawner
     * @param newQuantity the new quantity after removal
     * @param changeAmount the amount of spawners removed
     */
    public SpawnerRemoveEvent(Player player, Location location, int newQuantity, int changeAmount) {
        super(location, newQuantity);
        this.player = player;
        this.changeAmount = changeAmount;
    }


    @Override
    public @NotNull HandlerList getHandlers() {
        return handlers;
    }

    public static @NotNull HandlerList getHandlerList() {
        return handlers;
    }
}