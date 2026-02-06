package github.nighter.smartspawner.commands.list.gui.management;

import lombok.Getter;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

@Getter
public class SpawnerManagementHolder implements InventoryHolder {
    private final String spawnerId;
    private final String worldName;
    private final int listPage;
    private final String targetServer;

    public SpawnerManagementHolder(String spawnerId, String worldName, int listPage) {
        this(spawnerId, worldName, listPage, null);
    }

    public SpawnerManagementHolder(String spawnerId, String worldName, int listPage, String targetServer) {
        this.spawnerId = spawnerId;
        this.worldName = worldName;
        this.listPage = listPage;
        this.targetServer = targetServer;
    }

    /**
     * Check if this spawner is on a remote server.
     */
    public boolean isRemoteServer() {
        return targetServer != null;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
