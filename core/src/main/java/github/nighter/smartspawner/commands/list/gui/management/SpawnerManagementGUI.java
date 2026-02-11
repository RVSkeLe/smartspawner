package github.nighter.smartspawner.commands.list.gui.management;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.nms.VersionInitializer;
import github.nighter.smartspawner.language.LanguageManager;
import github.nighter.smartspawner.language.MessageService;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import github.nighter.smartspawner.spawner.data.SpawnerManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SpawnerManagementGUI {
    private static final int INVENTORY_SIZE = 27;
    private static final int TELEPORT_SLOT = 10;
    private static final int OPEN_SPAWNER_SLOT = 12;
    private static final int STACK_SLOT = 14;
    private static final int REMOVE_SLOT = 16;
    private static final int BACK_SLOT = 26;

    private final SmartSpawner plugin;
    private final LanguageManager languageManager;
    private final MessageService messageService;
    private final SpawnerManager spawnerManager;

    public SpawnerManagementGUI(SmartSpawner plugin) {
        this.plugin = plugin;
        this.languageManager = plugin.getLanguageManager();
        this.messageService = plugin.getMessageService();
        this.spawnerManager = plugin.getSpawnerManager();
    }

    /**
     * Open management menu for a local spawner.
     */
    public void openManagementMenu(Player player, String spawnerId, String worldName, int listPage) {
        openManagementMenu(player, spawnerId, worldName, listPage, null);
    }

    /**
     * Open management menu with optional remote server context.
     */
    public void openManagementMenu(Player player, String spawnerId, String worldName, int listPage, String targetServer) {
        boolean isRemote = targetServer != null && !targetServer.equals(getCurrentServerName());

        // For local spawners, verify it exists
        if (!isRemote) {
            SpawnerData spawner = spawnerManager.getSpawnerById(spawnerId);
            if (spawner == null) {
                messageService.sendMessage(player, "spawner_not_found");
                return;
            }
        }

        String title = languageManager.getGuiTitle("spawner_management.title");
        Inventory inv = Bukkit.createInventory(
            new SpawnerManagementHolder(spawnerId, worldName, listPage, targetServer),
            INVENTORY_SIZE, title
        );
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);

        // Teleport button - disabled for remote servers (can't teleport cross-server)
        if (isRemote) {
            createDisabledTeleportItem(inv, TELEPORT_SLOT, targetServer);
        } else {
            createActionItem(inv, TELEPORT_SLOT, "spawner_management.teleport", Material.ENDER_PEARL);
        }

        // Open spawner info button - enabled for both local and remote
        // For remote: shows spawner info from database
        // For local: opens the actual spawner menu
        if (isRemote) {
            createRemoteActionItem(inv, OPEN_SPAWNER_SLOT, "spawner_management.open_spawner", Material.ENDER_EYE, "View Info");
        } else {
            createActionItem(inv, OPEN_SPAWNER_SLOT, "spawner_management.open_spawner", Material.ENDER_EYE);
        }

        // Stack button - enabled for both local and remote
        // For remote: updates stack size in database
        if (isRemote) {
            createRemoteActionItem(inv, STACK_SLOT, "spawner_management.stack", Material.SPAWNER, "Edit Stack Size");
        } else {
            createActionItem(inv, STACK_SLOT, "spawner_management.stack", Material.SPAWNER);
        }

        // Remove button - enabled for both local and remote
        // For remote: removes from database (physical block remains until target server syncs)
        if (isRemote) {
            createRemoteActionItem(inv, REMOVE_SLOT, "spawner_management.remove", Material.BARRIER, "Remove from DB");
        } else {
            createActionItem(inv, REMOVE_SLOT, "spawner_management.remove", Material.BARRIER);
        }

        createActionItem(inv, BACK_SLOT, "spawner_management.back", Material.RED_STAINED_GLASS_PANE);
        player.openInventory(inv);
    }

    private void createActionItem(Inventory inv, int slot, String langKey, Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(languageManager.getGuiItemName(langKey + ".name"));
            List<String> lore = Arrays.asList(languageManager.getGuiItemLore(langKey + ".lore"));
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
            item.setItemMeta(meta);
        }
        if (item.getType() == Material.SPAWNER) VersionInitializer.hideTooltip(item);
        inv.setItem(slot, item);
    }

    private void createDisabledTeleportItem(Inventory inv, int slot, String serverName) {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.RED + "Teleport Disabled");
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Must be on the same server");
            lore.add(ChatColor.GRAY + "to teleport to this spawner.");
            lore.add("");
            lore.add(ChatColor.DARK_GRAY + "Spawner Server: " + ChatColor.WHITE + serverName);
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
            item.setItemMeta(meta);
        }
        inv.setItem(slot, item);
    }

    private void createDisabledActionItem(Inventory inv, int slot, String langKey, Material originalMaterial, String reason) {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String name = languageManager.getGuiItemName(langKey + ".name");
            meta.setDisplayName(ChatColor.GRAY + ChatColor.stripColor(name) + " (Disabled)");
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.RED + "Not available for remote servers");
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
            item.setItemMeta(meta);
        }
        inv.setItem(slot, item);
    }

    private void createRemoteActionItem(Inventory inv, int slot, String langKey, Material material, String action) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(languageManager.getGuiItemName(langKey + ".name"));
            List<String> lore = new ArrayList<>(Arrays.asList(languageManager.getGuiItemLore(langKey + ".lore")));
            lore.add("");
            lore.add(ChatColor.YELLOW + "Remote Server Action");
            lore.add(ChatColor.GRAY + "Changes are saved to database.");
            lore.add(ChatColor.GRAY + "Target server will sync on next refresh.");
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
            item.setItemMeta(meta);
        }
        if (item.getType() == Material.SPAWNER) VersionInitializer.hideTooltip(item);
        inv.setItem(slot, item);
    }

    private String getCurrentServerName() {
        return plugin.getConfig().getString("database.server_name", "server1");
    }
}
