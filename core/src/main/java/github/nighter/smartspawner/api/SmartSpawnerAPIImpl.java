package github.nighter.smartspawner.api;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.spawner.item.SpawnerItemFactory;
import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Implementation of the SmartSpawnerAPI interface
 */
public class SmartSpawnerAPIImpl implements SmartSpawnerAPI {

    private final SmartSpawner plugin;
    private final SpawnerItemFactory itemFactory;

    public SmartSpawnerAPIImpl(SmartSpawner plugin) {
        this.plugin = plugin;
        this.itemFactory = new SpawnerItemFactory(plugin);
    }

    @Override
    public ItemStack createSpawnerItem(EntityType entityType) {
        return itemFactory.createSmartSpawnerItem(entityType);
    }

    @Override
    public ItemStack createSpawnerItem(EntityType entityType, int amount) {
        return itemFactory.createSmartSpawnerItem(entityType, amount);
    }

    @Override
    public ItemStack createVanillaSpawnerItem(EntityType entityType) {
        return itemFactory.createVanillaSpawnerItem(entityType);
    }

    @Override
    public ItemStack createVanillaSpawnerItem(EntityType entityType, int amount) {
        return itemFactory.createVanillaSpawnerItem(entityType, amount);
    }

    @Override
    public ItemStack createItemSpawnerItem(Material itemMaterial) {
        return itemFactory.createItemSpawnerItem(itemMaterial);
    }

    @Override
    public ItemStack createItemSpawnerItem(Material itemMaterial, int amount) {
        return itemFactory.createItemSpawnerItem(itemMaterial, amount);
    }

    @Override
    public boolean isSmartSpawner(ItemStack item) {
        if (item == null || item.getType() != Material.SPAWNER || !item.hasItemMeta()) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }

        // A SmartSpawner is a spawner that is NOT vanilla and NOT an item spawner
        return !isVanillaSpawner(item) && !isItemSpawner(item);
    }

    @Override
    public boolean isVanillaSpawner(ItemStack item) {
        if (item == null || item.getType() != Material.SPAWNER || !item.hasItemMeta()) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }

        return meta.getPersistentDataContainer().has(
                new org.bukkit.NamespacedKey(plugin, "vanilla_spawner"),
                org.bukkit.persistence.PersistentDataType.BOOLEAN);
    }

    @Override
    public boolean isItemSpawner(ItemStack item) {
        if (item == null || item.getType() != Material.SPAWNER || !item.hasItemMeta()) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }

        return meta.getPersistentDataContainer().has(
                new org.bukkit.NamespacedKey(plugin, "item_spawner_material"),
                org.bukkit.persistence.PersistentDataType.STRING);
    }

    @Override
    public EntityType getSpawnerEntityType(ItemStack item) {
        if (item == null || item.getType() != Material.SPAWNER || !item.hasItemMeta()) {
            return null;
        }

        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof BlockStateMeta blockMeta)) {
            return null;
        }

        BlockState blockState = blockMeta.getBlockState();
        if (!(blockState instanceof CreatureSpawner cs)) {
            return null;
        }

        return cs.getSpawnedType();
    }

    @Override
    public Material getItemSpawnerMaterial(ItemStack item) {
        if (!isItemSpawner(item)) {
            return null;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }

        String materialName = meta.getPersistentDataContainer().get(
                new org.bukkit.NamespacedKey(plugin, "item_spawner_material"),
                org.bukkit.persistence.PersistentDataType.STRING);

        if (materialName == null) {
            return null;
        }

        try {
            return Material.valueOf(materialName);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}