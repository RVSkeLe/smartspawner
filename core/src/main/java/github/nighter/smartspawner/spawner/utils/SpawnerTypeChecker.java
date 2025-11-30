package github.nighter.smartspawner.spawner.utils;

import github.nighter.smartspawner.SmartSpawner;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Utility class for distinguishing between vanilla and custom spawners
 */
public class SpawnerTypeChecker {
    private static NamespacedKey VANILLA_SPAWNER_KEY;

    /**
     * Initializes the spawner type checker with plugin instance
     * @param plugin The SmartSpawner plugin instance
     */
    public static void init(SmartSpawner plugin) {
        VANILLA_SPAWNER_KEY = new NamespacedKey(plugin, "vanilla_spawner");
    }

    /**
     * Checks if an item is a vanilla spawner
     * @param item The ItemStack to check
     * @return true if it's a vanilla spawner, false otherwise
     */
    public static boolean isVanillaSpawner(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(
                VANILLA_SPAWNER_KEY, PersistentDataType.BOOLEAN);
    }
}