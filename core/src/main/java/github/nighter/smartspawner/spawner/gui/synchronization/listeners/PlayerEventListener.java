package github.nighter.smartspawner.spawner.gui.synchronization.listeners;

import github.nighter.smartspawner.spawner.gui.synchronization.managers.ViewerTrackingManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Listener for player-related events.
 * Handles cleanup when players disconnect.
 */
public class PlayerEventListener implements Listener {

    private final ViewerTrackingManager viewerTrackingManager;

    public PlayerEventListener(ViewerTrackingManager viewerTrackingManager) {
        this.viewerTrackingManager = viewerTrackingManager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        viewerTrackingManager.untrackViewer(event.getPlayer().getUniqueId());
    }
}
