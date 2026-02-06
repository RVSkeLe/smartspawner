package github.nighter.smartspawner.commands.list.gui.adminstacker;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.commands.list.gui.management.SpawnerManagementGUI;
import github.nighter.smartspawner.language.MessageService;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import github.nighter.smartspawner.spawner.data.SpawnerManager;
import github.nighter.smartspawner.spawner.data.database.SpawnerDatabaseHandler;
import github.nighter.smartspawner.spawner.data.storage.SpawnerStorage;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.util.HashMap;
import java.util.Map;

/**
 * Handler for admin stacker GUI interactions
 */
public class AdminStackerHandler implements Listener {
    private static final int[] DECREASE_SLOTS = {9, 10, 11};
    private static final int[] INCREASE_SLOTS = {17, 16, 15};
    private static final int SPAWNER_INFO_SLOT = 13;
    private static final int BACK_SLOT = 22;
    private static final int[] STACK_AMOUNTS = {64, 10, 1};

    private final SmartSpawner plugin;
    private final MessageService messageService;
    private final SpawnerManager spawnerManager;
    private final SpawnerManagementGUI managementGUI;

    public AdminStackerHandler(SmartSpawner plugin, SpawnerManagementGUI managementGUI) {
        this.plugin = plugin;
        this.messageService = plugin.getMessageService();
        this.spawnerManager = plugin.getSpawnerManager();
        this.managementGUI = managementGUI;
    }

    @EventHandler
    public void onAdminStackerClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof AdminStackerHolder holder)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        event.setCancelled(true);
        if (event.getCurrentItem() == null) return;

        SpawnerData spawner = holder.getSpawnerData();
        String worldName = holder.getWorldName();
        int listPage = holder.getListPage();

        if (spawner == null) {
            messageService.sendMessage(player, "spawner_not_found");
            return;
        }

        int slot = event.getSlot();
        handleClick(player, spawner, worldName, listPage, slot);
    }

    private void handleClick(Player player, SpawnerData spawner, String worldName, int listPage, int slot) {
        if (slot == BACK_SLOT) {
            // Return to management GUI
            managementGUI.openManagementMenu(player, spawner.getSpawnerId(), worldName, listPage);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            return;
        }

        if (slot == SPAWNER_INFO_SLOT) {
            // Do nothing for info slot
            return;
        }

        // Check if it's a decrease slot
        for (int i = 0; i < DECREASE_SLOTS.length; i++) {
            if (slot == DECREASE_SLOTS[i]) {
                handleStackChange(player, spawner, worldName, listPage, -STACK_AMOUNTS[i]);
                return;
            }
        }

        // Check if it's an increase slot
        for (int i = 0; i < INCREASE_SLOTS.length; i++) {
            if (slot == INCREASE_SLOTS[i]) {
                handleStackChange(player, spawner, worldName, listPage, STACK_AMOUNTS[i]);
                return;
            }
        }
    }

    private void handleStackChange(Player player, SpawnerData spawner, String worldName, int listPage, int change) {
        if (!player.hasPermission("smartspawner.stack")) {
            messageService.sendMessage(player, "no_permission");
            return;
        }

        int newStackSize = spawner.getStackSize() + change;

        // Ensure stack size is within valid bounds
        if (newStackSize < 1) {
            newStackSize = 1;
        } else if (newStackSize > spawner.getMaxStackSize()) {
            newStackSize = spawner.getMaxStackSize();
            Map<String, String> placeholders = new HashMap<>(2);
            placeholders.put("max", String.valueOf(newStackSize));
            messageService.sendMessage(player, "spawner_stack_full", placeholders);
        }

        // Update the spawner stack size
        spawner.setStackSize(newStackSize);

        // Mark spawner as modified for database save
        spawnerManager.markSpawnerModified(spawner.getSpawnerId());

        // Track interaction
        spawner.updateLastInteractedPlayer(player.getName());
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);

        // Refresh the GUI to show updated values
        AdminStackerUI adminStackerUI = new AdminStackerUI(plugin);
        adminStackerUI.openAdminStackerGui(player, spawner, worldName, listPage);
    }

    // ===== Remote Admin Stacker Handler =====

    @EventHandler
    public void onRemoteAdminStackerClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof RemoteAdminStackerHolder holder)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        event.setCancelled(true);
        if (event.getCurrentItem() == null) return;

        int slot = event.getSlot();
        handleRemoteClick(player, holder, event.getInventory(), slot);
    }

    private void handleRemoteClick(Player player, RemoteAdminStackerHolder holder, Inventory inventory, int slot) {
        if (slot == BACK_SLOT) {
            // Save changes to database and return to management GUI
            saveRemoteStackChanges(player, holder);
            return;
        }

        if (slot == SPAWNER_INFO_SLOT) {
            // Do nothing for info slot
            return;
        }

        // Check if it's a decrease slot
        for (int i = 0; i < DECREASE_SLOTS.length; i++) {
            if (slot == DECREASE_SLOTS[i]) {
                handleRemoteStackChange(player, holder, inventory, -STACK_AMOUNTS[i]);
                return;
            }
        }

        // Check if it's an increase slot
        for (int i = 0; i < INCREASE_SLOTS.length; i++) {
            if (slot == INCREASE_SLOTS[i]) {
                handleRemoteStackChange(player, holder, inventory, STACK_AMOUNTS[i]);
                return;
            }
        }
    }

    private void handleRemoteStackChange(Player player, RemoteAdminStackerHolder holder,
                                          Inventory inventory, int change) {
        if (!player.hasPermission("smartspawner.stack")) {
            messageService.sendMessage(player, "no_permission");
            return;
        }

        // Adjust the stack size in the holder (not saved yet)
        holder.adjustStackSize(change);

        // Play feedback sound
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);

        // Refresh the GUI to show updated values
        AdminStackerUI adminStackerUI = new AdminStackerUI(plugin);
        adminStackerUI.refreshRemoteStackerGui(inventory, holder);
    }

    private void saveRemoteStackChanges(Player player, RemoteAdminStackerHolder holder) {
        SpawnerStorage storage = plugin.getSpawnerStorage();
        if (!(storage instanceof SpawnerDatabaseHandler dbHandler)) {
            messageService.sendMessage(player, "database_error");
            return;
        }

        String targetServer = holder.getTargetServer();
        String spawnerId = holder.getSpawnerId();
        int newStackSize = holder.getCurrentStackSize();
        int originalSize = holder.getSpawnerData().getStackSize();

        // Only save if changed
        if (newStackSize != originalSize) {
            player.sendMessage("§eSaving stack size changes...");

            dbHandler.updateRemoteSpawnerStackSizeAsync(targetServer, spawnerId, newStackSize, success -> {
                if (success) {
                    Map<String, String> placeholders = new HashMap<>();
                    placeholders.put("old", String.valueOf(originalSize));
                    placeholders.put("new", String.valueOf(newStackSize));
                    player.sendMessage("§aStack size updated from " + originalSize + " to " + newStackSize);
                    player.sendMessage("§e[Note] Changes will sync to " + targetServer + " on next refresh.");
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                } else {
                    player.sendMessage("§cFailed to update stack size. Spawner may have been removed.");
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                }

                // Return to management GUI
                managementGUI.openManagementMenu(player, spawnerId, holder.getWorldName(),
                        holder.getListPage(), targetServer);
            });
        } else {
            // No changes, just go back
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            managementGUI.openManagementMenu(player, spawnerId, holder.getWorldName(),
                    holder.getListPage(), targetServer);
        }
    }
}
