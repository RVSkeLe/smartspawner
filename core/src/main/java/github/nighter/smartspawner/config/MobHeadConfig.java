package github.nighter.smartspawner.config;

import github.nighter.smartspawner.SmartSpawner;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.EnumMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * Manages player-configurable mob head textures for spawner GUI
 */
public class MobHeadConfig {
    private final SmartSpawner plugin;
    private FileConfiguration config;
    private final File configFile;
    
    private Material defaultMaterial;
    private final Map<EntityType, MobHeadData> mobHeadMap = new EnumMap<>(EntityType.class);
    
    public MobHeadConfig(SmartSpawner plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "mob_heads.yml");
    }
    
    /**
     * Load or create the mob heads configuration
     */
    public void load() {
        // Create config file if it doesn't exist
        if (!configFile.exists()) {
            saveDefaultConfig();
        }
        
        // Load the configuration
        config = YamlConfiguration.loadConfiguration(configFile);
        
        // Parse configuration
        parseConfig();
    }
    
    /**
     * Save the default configuration from resources
     */
    private void saveDefaultConfig() {
        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            
            try (InputStream in = plugin.getResource("mob_heads.yml")) {
                if (in != null) {
                    Files.copy(in, configFile.toPath());
                    plugin.getLogger().info("Created default mob_heads.yml configuration");
                } else {
                    plugin.getLogger().warning("Could not find default mob_heads.yml in resources");
                }
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not create mob_heads.yml", e);
        }
    }
    
    /**
     * Parse the configuration and populate the mob head map
     */
    private void parseConfig() {
        mobHeadMap.clear();
        
        // Get default material
        String defaultMaterialName = config.getString("default_material", "SPAWNER");
        try {
            defaultMaterial = Material.valueOf(defaultMaterialName.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid default_material in mob_heads.yml: " + defaultMaterialName + ", using SPAWNER");
            defaultMaterial = Material.SPAWNER;
        }
        
        // Parse mob heads
        ConfigurationSection mobHeadsSection = config.getConfigurationSection("mob_heads");
        if (mobHeadsSection == null) {
            plugin.getLogger().warning("No mob_heads section found in mob_heads.yml");
            return;
        }
        
        for (String entityTypeName : mobHeadsSection.getKeys(false)) {
            try {
                EntityType entityType = EntityType.valueOf(entityTypeName.toUpperCase());
                ConfigurationSection entitySection = mobHeadsSection.getConfigurationSection(entityTypeName);
                
                if (entitySection != null) {
                    String materialName = entitySection.getString("material", "SPAWNER");
                    String customTexture = entitySection.getString("custom_texture");
                    
                    // Validate material
                    Material material;
                    try {
                        material = Material.valueOf(materialName.toUpperCase());
                        if (!material.isItem()) {
                            plugin.getLogger().warning("Material " + materialName + " for " + entityTypeName + " is not an item, using default");
                            material = defaultMaterial;
                        }
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Invalid material " + materialName + " for " + entityTypeName + ", using default");
                        material = defaultMaterial;
                    }
                    
                    // Store mob head data
                    mobHeadMap.put(entityType, new MobHeadData(material, customTexture));
                }
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid entity type in mob_heads.yml: " + entityTypeName);
            }
        }
        
        plugin.getLogger().info("Loaded " + mobHeadMap.size() + " mob head configurations");
    }
    
    /**
     * Get the material for a specific entity type
     * 
     * @param entityType Entity type
     * @return Material to use for the mob head
     */
    public Material getMaterial(EntityType entityType) {
        MobHeadData data = mobHeadMap.get(entityType);
        return data != null ? data.material : defaultMaterial;
    }
    
    /**
     * Get the custom texture for a specific entity type
     * 
     * @param entityType Entity type
     * @return Custom texture Base64 string, or null if not configured
     */
    public String getCustomTexture(EntityType entityType) {
        MobHeadData data = mobHeadMap.get(entityType);
        return data != null ? data.customTexture : null;
    }
    
    /**
     * Check if an entity type has a custom texture configured
     * 
     * @param entityType Entity type
     * @return true if custom texture is configured
     */
    public boolean hasCustomTexture(EntityType entityType) {
        MobHeadData data = mobHeadMap.get(entityType);
        return data != null && data.customTexture != null && !data.customTexture.isEmpty();
    }
    
    /**
     * Get the default material for mobs without custom configuration
     * 
     * @return Default material
     */
    public Material getDefaultMaterial() {
        return defaultMaterial;
    }
    
    /**
     * Reload the configuration
     */
    public void reload() {
        load();
    }
    
    /**
     * Internal class to store mob head data
     */
    private static class MobHeadData {
        final Material material;
        final String customTexture;
        
        MobHeadData(Material material, String customTexture) {
            this.material = material;
            this.customTexture = customTexture;
        }
    }
}
