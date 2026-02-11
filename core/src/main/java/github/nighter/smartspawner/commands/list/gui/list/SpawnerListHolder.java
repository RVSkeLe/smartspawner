package github.nighter.smartspawner.commands.list.gui.list;

import github.nighter.smartspawner.commands.list.gui.list.enums.FilterOption;
import github.nighter.smartspawner.commands.list.gui.list.enums.SortOption;
import lombok.Getter;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

@Getter
public class SpawnerListHolder implements InventoryHolder {
    private final int currentPage;
    private final int totalPages;
    private final String worldName;
    private final FilterOption filterOption;
    private final SortOption sortType;
    private final String targetServer;

    public SpawnerListHolder(int currentPage, int totalPages, String worldName,
                             FilterOption filterOption, SortOption sortType) {
        this(currentPage, totalPages, worldName, filterOption, sortType, null);
    }

    public SpawnerListHolder(int currentPage, int totalPages, String worldName,
                             FilterOption filterOption, SortOption sortType, String targetServer) {
        this.currentPage = currentPage;
        this.totalPages = totalPages;
        this.worldName = worldName;
        this.filterOption = filterOption;
        this.sortType = sortType;
        this.targetServer = targetServer;
    }

    /**
     * Check if this list is showing spawners from a remote server.
     */
    public boolean isRemoteServer() {
        return targetServer != null;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
