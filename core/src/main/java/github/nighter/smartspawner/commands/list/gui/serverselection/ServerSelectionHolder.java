package github.nighter.smartspawner.commands.list.gui.serverselection;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Inventory holder for the server selection GUI.
 * Used when sync_across_servers is enabled to select which server's spawners to view.
 */
public class ServerSelectionHolder implements InventoryHolder {

    @Override
    public Inventory getInventory() {
        return null;
    }
}
