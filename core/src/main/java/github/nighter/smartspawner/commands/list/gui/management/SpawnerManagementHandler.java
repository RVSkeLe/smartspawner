package github.nighter.smartspawner.commands.list.gui.management;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.commands.list.gui.adminstacker.AdminStackerUI;
import github.nighter.smartspawner.commands.list.ListSubCommand;
import github.nighter.smartspawner.commands.list.gui.list.enums.FilterOption;
import github.nighter.smartspawner.commands.list.gui.list.enums.SortOption;
import github.nighter.smartspawner.language.MessageService;
import github.nighter.smartspawner.spawner.gui.main.SpawnerMenuUI;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import github.nighter.smartspawner.spawner.data.SpawnerManager;
import github.nighter.smartspawner.spawner.data.storage.SpawnerStorage;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

public class SpawnerManagementHandler implements Listener {
    private final SmartSpawner plugin;
    private final MessageService messageService;
    private final SpawnerManager spawnerManager;
    private final SpawnerStorage spawnerStorage;
    private final ListSubCommand listSubCommand;
    private final SpawnerMenuUI spawnerMenuUI;
    private final AdminStackerUI adminStackerUI;

    public SpawnerManagementHandler(SmartSpawner plugin, ListSubCommand listSubCommand) {
        this.plugin = plugin;
        this.messageService = plugin.getMessageService();
        this.spawnerManager = plugin.getSpawnerManager();
        this.spawnerStorage = plugin.getSpawnerStorage();
        this.listSubCommand = listSubCommand;
        this.spawnerMenuUI = plugin.getSpawnerMenuUI();
        this.adminStackerUI = new AdminStackerUI(plugin);
    }

    @EventHandler
    public void onSpawnerManagementClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof SpawnerManagementHolder holder)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        event.setCancelled(true);
        if (event.getCurrentItem() == null) return;

        String spawnerId = holder.getSpawnerId();
        String worldName = holder.getWorldName();
        int listPage = holder.getListPage();
        String targetServer = holder.getTargetServer();
        boolean isRemote = holder.isRemoteServer();

        int slot = event.getSlot();

        // Handle back button - works for both local and remote
        if (slot == 26) {
            handleBack(player, worldName, listPage, targetServer);
            return;
        }

        // For remote servers, all other actions are disabled
        if (isRemote) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        // Local spawner actions
        SpawnerData spawner = spawnerManager.getSpawnerById(spawnerId);
        if (spawner == null) {
            messageService.sendMessage(player, "spawner_not_found");
            return;
        }

        switch (slot) {
            case 10 -> handleTeleport(player, spawner);
            case 12 -> handleOpenSpawner(player, spawner);
            case 14 -> handleStackManagement(player, spawner, worldName, listPage);
            case 16 -> handleRemoveSpawner(player, spawner, worldName, listPage);
        }
    }

    private void handleTeleport(Player player, SpawnerData spawner) {
        Location loc = spawner.getSpawnerLocation().clone().add(0.5, 1, 0.5);
        player.teleportAsync(loc);
        messageService.sendMessage(player, "teleported_to_spawner");
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
        player.closeInventory();
    }

    private void handleOpenSpawner(Player player, SpawnerData spawner) {
        // Check if player is Bedrock and use appropriate menu
        if (isBedrockPlayer(player)) {
            if (plugin.getSpawnerMenuFormUI() != null) {
                plugin.getSpawnerMenuFormUI().openSpawnerForm(player, spawner);
            } else {
                // Fallback to standard GUI if FormUI not available
                spawnerMenuUI.openSpawnerMenu(player, spawner, false);
            }
        } else {
            spawnerMenuUI.openSpawnerMenu(player, spawner, false);
        }
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
    }

    private void handleStackManagement(Player player, SpawnerData spawner, String worldName, int listPage) {
        if (!player.hasPermission("smartspawner.stack")) {
            messageService.sendMessage(player, "no_permission");
            return;
        }

        adminStackerUI.openAdminStackerGui(player, spawner, worldName, listPage);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
    }

    private void handleRemoveSpawner(Player player, SpawnerData spawner, String worldName, int listPage) {
        // Remove the spawner block and data
        Location loc = spawner.getSpawnerLocation();
        plugin.getSpawnerGuiViewManager().closeAllViewersInventory(spawner);
        String spawnerId = spawner.getSpawnerId();
        spawner.getSpawnerStop().set(true);
        if (loc.getBlock().getType() == Material.SPAWNER) {
            loc.getBlock().setType(Material.AIR);
        }

        // Remove from manager and save
        spawnerManager.removeSpawner(spawnerId);
        spawnerStorage.markSpawnerDeleted(spawnerId);
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("id", spawner.getSpawnerId());
        messageService.sendMessage(player, "spawner_management.removed", placeholders);
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);

        // Return to spawner list
        handleBack(player, worldName, listPage, null);
    }

    private void handleBack(Player player, String worldName, int listPage, String targetServer) {
        // Get the user's current preferences for filter and sort
        FilterOption filter = FilterOption.ALL; // Default
        SortOption sort = SortOption.DEFAULT; // Default

        // Try to get saved preferences
        try {
            filter = listSubCommand.getUserFilter(player, worldName);
            sort = listSubCommand.getUserSort(player, worldName);
        } catch (Exception ignored) {
            // Use defaults if loading fails
        }

        // Check if going back to a remote server's spawner list
        if (targetServer != null && !targetServer.equals(listSubCommand.getCurrentServerName())) {
            listSubCommand.openSpawnerListGUIForServer(player, targetServer, worldName, listPage);
        } else {
            listSubCommand.openSpawnerListGUI(player, worldName, listPage, filter, sort);
        }
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
    }

    private boolean isBedrockPlayer(Player player) {
        if (plugin.getIntegrationManager() == null || 
            plugin.getIntegrationManager().getFloodgateHook() == null) {
            return false;
        }
        return plugin.getIntegrationManager().getFloodgateHook().isBedrockPlayer(player);
    }
}
