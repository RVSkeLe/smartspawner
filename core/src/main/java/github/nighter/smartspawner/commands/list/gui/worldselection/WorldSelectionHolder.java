package github.nighter.smartspawner.commands.list.gui.worldselection;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Inventory holder for the world selection GUI.
 * Optionally stores a target server name for cross-server viewing.
 */
public class WorldSelectionHolder implements InventoryHolder {
    private final String targetServer;

    /**
     * Create a world selection holder for local server.
     */
    public WorldSelectionHolder() {
        this.targetServer = null;
    }

    /**
     * Create a world selection holder for a specific server.
     * @param targetServer The server name to view worlds from
     */
    public WorldSelectionHolder(String targetServer) {
        this.targetServer = targetServer;
    }

    /**
     * Get the target server name.
     * @return The server name, or null if viewing local server
     */
    public String getTargetServer() {
        return targetServer;
    }

    /**
     * Check if this is viewing a remote server.
     * @return true if viewing a remote server
     */
    public boolean isRemoteServer() {
        return targetServer != null;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
