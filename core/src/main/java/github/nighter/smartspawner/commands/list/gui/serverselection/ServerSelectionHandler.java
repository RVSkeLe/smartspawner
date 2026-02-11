package github.nighter.smartspawner.commands.list.gui.serverselection;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.commands.list.ListSubCommand;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Handles click events in the server selection GUI.
 */
public class ServerSelectionHandler implements Listener {
    private final SmartSpawner plugin;
    private final ListSubCommand listSubCommand;

    public ServerSelectionHandler(SmartSpawner plugin, ListSubCommand listSubCommand) {
        this.plugin = plugin;
        this.listSubCommand = listSubCommand;
    }

    @EventHandler
    public void onServerSelectionClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof ServerSelectionHolder)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        event.setCancelled(true);

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || !clickedItem.hasItemMeta()) return;

        ItemMeta meta = clickedItem.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) return;

        // Extract server name from display name (strip color codes)
        String serverName = ChatColor.stripColor(meta.getDisplayName());

        if (serverName == null || serverName.isEmpty()) return;

        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);

        // Open world selection for the selected server
        listSubCommand.openWorldSelectionGUIForServer(player, serverName);
    }
}
