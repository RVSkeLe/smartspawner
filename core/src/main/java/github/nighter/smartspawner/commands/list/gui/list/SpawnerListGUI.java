package github.nighter.smartspawner.commands.list.gui.list;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.commands.list.ListSubCommand;
import github.nighter.smartspawner.commands.list.gui.list.enums.FilterOption;
import github.nighter.smartspawner.commands.list.gui.list.enums.SortOption;
import github.nighter.smartspawner.commands.list.gui.worldselection.WorldSelectionHolder;
import github.nighter.smartspawner.language.LanguageManager;
import github.nighter.smartspawner.language.MessageService;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import github.nighter.smartspawner.spawner.data.SpawnerManager;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class SpawnerListGUI implements Listener {
    private final LanguageManager languageManager;
    private final MessageService messageService;
    private final SpawnerManager spawnerManager;
    private final ListSubCommand listSubCommand;
    private static final Set<Material> SPAWNER_MATERIALS = EnumSet.of(
            Material.PLAYER_HEAD, Material.SPAWNER, Material.ZOMBIE_HEAD,
            Material.SKELETON_SKULL, Material.WITHER_SKELETON_SKULL,
            Material.CREEPER_HEAD, Material.PIGLIN_HEAD
    );
    private static final String patternString = "#([A-Za-z0-9]+)";

    public SpawnerListGUI(SmartSpawner plugin) {
        this.languageManager = plugin.getLanguageManager();
        this.messageService = plugin.getMessageService();
        this.spawnerManager = plugin.getSpawnerManager();
        this.listSubCommand = plugin.getListSubCommand();
    }

    @EventHandler
    public void onWorldSelectionClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof WorldSelectionHolder holder)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        if (!player.hasPermission("smartspawner.command.list")) {
            messageService.sendMessage(player, "no_permission");
            return;
        }
        event.setCancelled(true);

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || !clickedItem.hasItemMeta() || !clickedItem.getItemMeta().hasDisplayName()) return;

        String displayName = ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName());
        String targetServer = holder.getTargetServer();
        boolean isRemote = holder.isRemoteServer();

        // Handle back button for world selection (both local and remote when cross-server is enabled)
        if (clickedItem.getType() == Material.RED_STAINED_GLASS_PANE) {
            // Go back to server selection
            listSubCommand.openServerSelectionGUI(player);
            return;
        }

        // For remote servers, we need to use the async method
        if (isRemote) {
            // Extract world name from display name for remote servers
            // The display name format is "World Name" or similar
            String worldName = extractWorldNameFromDisplay(displayName);
            if (worldName != null) {
                listSubCommand.openSpawnerListGUIForServer(player, targetServer, worldName, 1);
            }
            return;
        }

        // Local server handling (original logic)
        // Check for original layout slots first (for backward compatibility)
        if (event.getSlot() == 11 && displayName.equals(ChatColor.stripColor(languageManager.getGuiTitle("world_buttons.overworld.name")))) {
            listSubCommand.openSpawnerListGUI(player, "world", 1);
            return;
        } else if (event.getSlot() == 13 && displayName.equals(ChatColor.stripColor(languageManager.getGuiTitle("world_buttons.nether.name")))) {
            listSubCommand.openSpawnerListGUI(player, "world_nether", 1);
            return;
        } else if (event.getSlot() == 15 && displayName.equals(ChatColor.stripColor(languageManager.getGuiTitle("world_buttons.end.name")))) {
            listSubCommand.openSpawnerListGUI(player, "world_the_end", 1);
            return;
        }

        // For custom layout or any other slots, determine world by name
        if (displayName.equals(ChatColor.stripColor(languageManager.getGuiTitle("world_buttons.overworld.name")))) {
            listSubCommand.openSpawnerListGUI(player, "world", 1);
        } else if (displayName.equals(ChatColor.stripColor(languageManager.getGuiTitle("world_buttons.nether.name")))) {
            listSubCommand.openSpawnerListGUI(player, "world_nether", 1);
        } else if (displayName.equals(ChatColor.stripColor(languageManager.getGuiTitle("world_buttons.end.name")))) {
            listSubCommand.openSpawnerListGUI(player, "world_the_end", 1);
        } else {
            // For custom worlds, find the matching world
            for (World world : Bukkit.getWorlds()) {
                String worldDisplayName = formatWorldName(world.getName());

                if (spawnerManager.countSpawnersInWorld(world.getName()) > 0 &&
                        displayName.contains(worldDisplayName)) {
                    listSubCommand.openSpawnerListGUI(player, world.getName(), 1);
                    break;
                }
            }
        }
    }

    /**
     * Extract world name from display name for remote servers.
     * Tries common world name patterns.
     */
    private String extractWorldNameFromDisplay(String displayName) {
        // Check if it matches known world display names
        if (displayName.equalsIgnoreCase("Overworld") || displayName.equalsIgnoreCase("World")) {
            return "world";
        } else if (displayName.equalsIgnoreCase("Nether") || displayName.equalsIgnoreCase("The Nether")) {
            return "world_nether";
        } else if (displayName.equalsIgnoreCase("The End") || displayName.equalsIgnoreCase("End")) {
            return "world_the_end";
        }
        // For custom worlds, convert display name back to world name format
        // "My Custom World" -> "my_custom_world"
        return displayName.toLowerCase().replace(' ', '_');
    }

    // Helper method to format world name (same as in listSubCommand)
    private String formatWorldName(String worldName) {
        // Convert something like "my_custom_world" to "My Custom World"
        return Arrays.stream(worldName.replace('_', ' ').split(" "))
                .map(word -> word.substring(0, 1).toUpperCase() + word.substring(1).toLowerCase())
                .collect(Collectors.joining(" "));
    }

    @EventHandler
    public void onSpawnerListClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof SpawnerListHolder holder)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        if (!player.hasPermission("smartspawner.list")) {
            messageService.sendMessage(player, "no_permission");
            return;
        }
        event.setCancelled(true);
        if (event.getCurrentItem() == null) return;

        // Get current state
        String worldName = holder.getWorldName();
        int currentPage = holder.getCurrentPage();
        int totalPages = holder.getTotalPages();
        FilterOption currentFilter = holder.getFilterOption();
        SortOption currentSort = holder.getSortType();
        String targetServer = holder.getTargetServer();
        boolean isRemote = holder.isRemoteServer();

        // For remote servers, filter/sort buttons are disabled
        if (!isRemote) {
            // Handle filter button click
            if (event.getSlot() == 48) {
                // Cycle to next filter option
                FilterOption nextFilter = currentFilter.getNextOption();

                // Save user preference when they change filter
                listSubCommand.saveUserPreference(player, worldName, nextFilter, currentSort);

                listSubCommand.openSpawnerListGUI(player, worldName, 1, nextFilter, currentSort);
                return;
            }

            // Handle sort button click
            if (event.getSlot() == 50) {
                // Cycle to next sort option
                SortOption nextSort = currentSort.getNextOption();

                // Save user preference when they change sort
                listSubCommand.saveUserPreference(player, worldName, currentFilter, nextSort);

                listSubCommand.openSpawnerListGUI(player, worldName, 1, currentFilter, nextSort);
                return;
            }
        }

        // Handle navigation - works for both local and remote
        if (event.getSlot() == 45 && currentPage > 1) {
            // Previous page
            if (isRemote) {
                listSubCommand.openSpawnerListGUIForServer(player, targetServer, worldName, currentPage - 1);
            } else {
                listSubCommand.openSpawnerListGUI(player, worldName, currentPage - 1, currentFilter, currentSort);
            }
            return;
        }

        if (event.getSlot() == 49) {
            // Save preference before going back to world selection (only for local)
            if (!isRemote) {
                listSubCommand.saveUserPreference(player, worldName, currentFilter, currentSort);
            }

            // Back to world selection
            if (isRemote) {
                listSubCommand.openWorldSelectionGUIForServer(player, targetServer);
            } else {
                listSubCommand.openWorldSelectionGUI(player);
            }
            return;
        }

        if (event.getSlot() == 53 && currentPage < totalPages) {
            // Next page
            if (isRemote) {
                listSubCommand.openSpawnerListGUIForServer(player, targetServer, worldName, currentPage + 1);
            } else {
                listSubCommand.openSpawnerListGUI(player, worldName, currentPage + 1, currentFilter, currentSort);
            }
            return;
        }


        // Handle spawner item click (management menu)
        if (isSpawnerItemSlot(event.getSlot()) && isSpawnerItem(event.getCurrentItem())) {
            handleSpawnerItemClick(player, event.getCurrentItem(), holder);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof SpawnerListHolder holder)) return;
        if (!(event.getPlayer() instanceof Player player)) return;

        // Save user preferences when closing the inventory
        String worldName = holder.getWorldName();
        FilterOption currentFilter = holder.getFilterOption();
        SortOption currentSort = holder.getSortType();

        // Save preference when they close the GUI
        listSubCommand.saveUserPreference(player, worldName, currentFilter, currentSort);
    }

    private boolean isSpawnerItemSlot(int slot) {
        // Check if slot is in the spawner display area (first 5 rows, excluding borders)
        return slot < 45;
    }

    private boolean isSpawnerItem(ItemStack item) {
        // Check if item is a spawner or mob head (used for spawner display)
        return item != null && SPAWNER_MATERIALS.contains(item.getType()) &&
                item.hasItemMeta() && item.getItemMeta().hasDisplayName();
    }

    private void handleSpawnerItemClick(Player player, ItemStack item, SpawnerListHolder holder) {
        // Extract spawner ID from the item name
        String displayName = item.getItemMeta().getDisplayName();
        Pattern pattern = Pattern.compile(patternString);
        Matcher matcher = pattern.matcher(displayName);

        if (matcher.find()) {
            String spawnerId = matcher.group(1);
            String targetServer = holder.getTargetServer();
            boolean isRemote = holder.isRemoteServer();

            // For remote servers, spawner data isn't available locally
            if (isRemote) {
                // Open management GUI with remote server context (actions will be disabled)
                listSubCommand.openSpawnerManagementGUI(player, spawnerId,
                    holder.getWorldName(), holder.getCurrentPage(), targetServer);
            } else {
                // Local server - verify spawner exists
                SpawnerData spawner = spawnerManager.getSpawnerById(spawnerId);

                if (spawner != null) {
                    // Open the management GUI
                    listSubCommand.openSpawnerManagementGUI(player, spawnerId,
                        holder.getWorldName(), holder.getCurrentPage(), null);
                } else {
                    messageService.sendMessage(player, "spawner_not_found");
                }
            }
        }
    }
}
