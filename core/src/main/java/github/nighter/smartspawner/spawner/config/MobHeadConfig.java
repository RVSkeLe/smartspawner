package github.nighter.smartspawner.spawner.config;

import github.nighter.smartspawner.SmartSpawner;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;

/**
 * Legacy wrapper for mob head configuration
 * Delegates to SpawnerSettingsConfig for backward compatibility
 */
public class MobHeadConfig {
    private final SmartSpawner plugin;
    
    public MobHeadConfig(SmartSpawner plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Load is now a no-op as SpawnerSettingsConfig handles this
     */
    public void load() {
        // Delegated to SpawnerSettingsConfig
    }
    
    /**
     * Get the material for a specific entity type
     */
    public Material getMaterial(EntityType entityType) {
        if (plugin.getSpawnerSettingsConfig() != null) {
            return plugin.getSpawnerSettingsConfig().getMaterial(entityType);
        }
        return Material.SPAWNER;
    }
    
    /**
     * Get the custom texture for a specific entity type
     */
    public String getCustomTexture(EntityType entityType) {
        if (plugin.getSpawnerSettingsConfig() != null) {
            return plugin.getSpawnerSettingsConfig().getCustomTexture(entityType);
        }
        return null;
    }
    
    /**
     * Check if an entity type has a custom texture configured
     */
    public boolean hasCustomTexture(EntityType entityType) {
        if (plugin.getSpawnerSettingsConfig() != null) {
            return plugin.getSpawnerSettingsConfig().hasCustomTexture(entityType);
        }
        return false;
    }

    /**
     * Reload is now a no-op as SpawnerSettingsConfig handles this
     */
    public void reload() {
        // Delegated to SpawnerSettingsConfig
    }
}
