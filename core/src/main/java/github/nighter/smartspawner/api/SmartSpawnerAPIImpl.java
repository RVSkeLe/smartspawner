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
}