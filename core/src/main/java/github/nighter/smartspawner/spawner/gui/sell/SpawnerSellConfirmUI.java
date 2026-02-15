package github.nighter.smartspawner.spawner.gui.sell;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.language.LanguageManager;
import github.nighter.smartspawner.nms.VersionInitializer;
import github.nighter.smartspawner.spawner.config.SpawnerMobHeadTexture;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class SpawnerSellConfirmUI {
    private static final int GUI_SIZE = 27;
    private static final int SPAWNER_INFO_SLOT = 13;
    private static final int CANCEL_SLOT = 10;
    private static final int CONFIRM_SLOT = 16;

    private final SmartSpawner plugin;
    private final LanguageManager languageManager;

    public SpawnerSellConfirmUI(SmartSpawner plugin) {
        this.plugin = plugin;
        this.languageManager = plugin.getLanguageManager();
    }

    public enum PreviousGui {
        MAIN_MENU,
        STORAGE
    }

    public void openSellConfirmGui(Player player, SpawnerData spawner, PreviousGui previousGui, boolean collectExp) {
        if (player == null || spawner == null) {
            return;
        }

        // Check if there are items to sell before opening
        if (spawner.getVirtualInventory().getUsedSlots() == 0) {
            plugin.getMessageService().sendMessage(player, "no_items");
            return;
        }

        // Mark spawner as interacted to lock state during transaction
        spawner.markInteracted();

        // Cache title - no placeholders needed
        String title = languageManager.getGuiTitle("gui_title_sell_confirm", null);
        Inventory gui = Bukkit.createInventory(new SpawnerSellConfirmHolder(spawner, previousGui, collectExp), GUI_SIZE, title);

        populateSellConfirmGui(gui, player, spawner, collectExp);

        player.openInventory(gui);
    }

    private void populateSellConfirmGui(Inventory gui, Player player, SpawnerData spawner, boolean collectExp) {
        // OPTIMIZATION: Create placeholders once and reuse for all buttons
        Map<String, String> placeholders = createPlaceholders(spawner, collectExp);

        // Add cancel button (RED_STAINED_GLASS_PANE) at slot 10
        gui.setItem(CANCEL_SLOT, createCancelButton(placeholders));

        // Add confirm button (LIME_STAINED_GLASS_PANE) at slot 16
        gui.setItem(CONFIRM_SLOT, createConfirmButton(placeholders, collectExp));

        // Add spawner info in the center
        gui.setItem(SPAWNER_INFO_SLOT, createSpawnerInfoButton(player, placeholders));
    }

    private ItemStack createCancelButton(Map<String, String> placeholders) {
        String name = languageManager.getGuiItemName("button_sell_cancel.name", placeholders);
        String[] lore = languageManager.getGuiItemLore("button_sell_cancel.lore", placeholders);
        return createButton(Material.RED_STAINED_GLASS_PANE, name, lore);
    }

    private ItemStack createConfirmButton(Map<String, String> placeholders, boolean collectExp) {
        // Use different button key based on whether exp is collected
        String buttonKey = collectExp ? "button_sell_confirm_with_exp" : "button_sell_confirm";
        String name = languageManager.getGuiItemName(buttonKey + ".name", placeholders);
        String[] lore = languageManager.getGuiItemLore(buttonKey + ".lore", placeholders);
        return createButton(Material.LIME_STAINED_GLASS_PANE, name, lore);
    }

    private ItemStack createSpawnerInfoButton(Player player, Map<String, String> placeholders) {
        // OPTIMIZATION: Reuse placeholders passed from parent

        // Prepare the meta modifier consumer
        Consumer<ItemMeta> metaModifier = meta -> {
            // Set display name
            meta.setDisplayName(languageManager.getGuiItemName("button_sell_info.name", placeholders));

            // Get and set lore
            String[] lore = languageManager.getGuiItemLore("button_sell_info.lore", placeholders);
            if (lore != null && lore.length > 0) {
                meta.setLore(Arrays.asList(lore));
            }
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
        };

        ItemStack spawnerItem;

        // OPTIMIZATION: Get cached spawner type from placeholders
        if (placeholders.containsKey("spawnedItem")) {
            spawnerItem = SpawnerMobHeadTexture.getItemSpawnerHead(
                    Material.valueOf(placeholders.get("spawnedItem")), player, metaModifier);
        } else {
            spawnerItem = SpawnerMobHeadTexture.getCustomHead(
                    org.bukkit.entity.EntityType.valueOf(placeholders.get("entityType")),
                    player, metaModifier);
        }

        if (spawnerItem.getType() == Material.SPAWNER) {
            VersionInitializer.hideTooltip(spawnerItem);
        }

        return spawnerItem;
    }

    private Map<String, String> createPlaceholders(SpawnerData spawner, boolean collectExp) {
        // OPTIMIZATION: Calculate initial capacity to avoid HashMap resizing
        Map<String, String> placeholders = new HashMap<>(8);

        // OPTIMIZATION: Get entity name once and cache
        String entityName;
        boolean isItemSpawner = spawner.isItemSpawner();

        if (isItemSpawner) {
            Material spawnedItem = spawner.getSpawnedItemMaterial();
            entityName = languageManager.getVanillaItemName(spawnedItem);
            placeholders.put("spawnedItem", spawnedItem.name());
        } else {
            org.bukkit.entity.EntityType entityType = spawner.getEntityType();
            entityName = languageManager.getFormattedMobName(entityType);
            placeholders.put("entityType", entityType.name());
        }

        placeholders.put("entity", entityName);
        placeholders.put("ᴇɴᴛɪᴛʏ", languageManager.getSmallCaps(entityName));

        // OPTIMIZATION: Check sell value dirty only once
        if (spawner.isSellValueDirty()) {
            spawner.recalculateSellValue();
        }

        // OPTIMIZATION: Get all values in single pass
        double totalSellPrice = spawner.getAccumulatedSellValue();
        int currentItems = spawner.getVirtualInventory().getUsedSlots();

        placeholders.put("total_sell_price", languageManager.formatNumber(totalSellPrice));
        placeholders.put("current_items", Integer.toString(currentItems));

        // OPTIMIZATION: Only get exp if needed (for button with exp)
        if (collectExp) {
            placeholders.put("current_exp", Integer.toString(spawner.getSpawnerExp()));
        }

        return placeholders;
    }

    private ItemStack createButton(Material material, String name, String[] lore) {
        ItemStack button = new ItemStack(material);
        ItemMeta meta = button.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null && lore.length > 0) {
                meta.setLore(Arrays.asList(lore));
            }
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
            button.setItemMeta(meta);
        }
        VersionInitializer.hideTooltip(button);
        return button;
    }
}

