package github.nighter.smartspawner.spawner.gui.sell;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.InventoryHolder;

public class SpawnerSellConfirmListener implements Listener {
    private static final int CANCEL_SLOT = 10;
    private static final int CONFIRM_SLOT = 16;

    private final SmartSpawner plugin;

    public SpawnerSellConfirmListener(SmartSpawner plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof SpawnerSellConfirmHolder)) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        SpawnerSellConfirmHolder confirmHolder = (SpawnerSellConfirmHolder) holder;
        SpawnerData spawner = confirmHolder.getSpawnerData();

        if (spawner == null) {
            player.closeInventory();
            return;
        }

        int slot = event.getRawSlot();

        // Check if clicked on cancel button
        if (slot == CANCEL_SLOT) {
            handleCancel(player, spawner, confirmHolder.getPreviousGui());
            return;
        }

        // Check if clicked on confirm button
        if (slot == CONFIRM_SLOT) {
            handleConfirm(player, spawner, confirmHolder.getPreviousGui(), confirmHolder.isCollectExp());
            return;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof SpawnerSellConfirmHolder)) {
            return;
        }

        // Cleanup or additional actions when GUI is closed can be added here if needed
    }

    private void handleCancel(Player player, SpawnerData spawner, SpawnerSellConfirmUI.PreviousGui previousGui) {
        // Play sound instead of sending message
        player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);

        // Reopen previous GUI
        reopenPreviousGui(player, spawner, previousGui);
    }

    private void handleConfirm(Player player, SpawnerData spawner, SpawnerSellConfirmUI.PreviousGui previousGui, boolean collectExp) {
        // Collect exp if requested
        if (collectExp) {
            plugin.getSpawnerMenuAction().handleExpBottleClick(player, spawner, true);
        }

        // Trigger the actual sell operation
        plugin.getSpawnerSellManager().sellAllItems(player, spawner);

        // Schedule GUI reopening after sell completes (1 tick delay to ensure sell process finishes)
        github.nighter.smartspawner.Scheduler.runTask(() -> {
            reopenPreviousGui(player, spawner, previousGui);
        });
    }

    private void reopenPreviousGui(Player player, SpawnerData spawner, SpawnerSellConfirmUI.PreviousGui previousGui) {
        // Check if player is Bedrock
        boolean isBedrockPlayer = isBedrockPlayer(player);

        switch (previousGui) {
            case MAIN_MENU:
                if (isBedrockPlayer && plugin.getSpawnerMenuFormUI() != null) {
                    // Reopen FormUI for Bedrock players
                    plugin.getSpawnerMenuFormUI().openSpawnerForm(player, spawner);
                } else {
                    // Reopen standard GUI for Java players
                    plugin.getSpawnerMenuUI().openSpawnerMenu(player, spawner, true);
                }
                break;
            case STORAGE:
                // Storage GUI works the same for both Java and Bedrock
                org.bukkit.inventory.Inventory storageInventory = plugin.getSpawnerStorageUI()
                        .createStorageInventory(spawner, 1, -1);
                player.openInventory(storageInventory);
                break;
        }
    }

    private boolean isBedrockPlayer(Player player) {
        if (plugin.getIntegrationManager() == null ||
            plugin.getIntegrationManager().getFloodgateHook() == null) {
            return false;
        }
        return plugin.getIntegrationManager().getFloodgateHook().isBedrockPlayer(player);
    }
}


