package github.nighter.smartspawner.spawner.config;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.spawner.loot.EntityLootConfig;
import github.nighter.smartspawner.spawner.loot.LootItem;
import github.nighter.smartspawner.updates.ConfigUpdater;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.potion.PotionType;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Manages the merged spawner settings configuration that combines mob drops and head textures
 */
public class SpawnerSettingsConfig {
    private final SmartSpawner plugin;
    private FileConfiguration config;
    private final File configFile;
    private static final String CONFIG_VERSION_KEY = "config_version";
    private static final String CURRENT_CONFIG_VERSION = "1.0.0";
    
    // Mob head data
    private Material defaultMaterial;
    private final Map<EntityType, MobHeadData> mobHeadMap = new EnumMap<>(EntityType.class);
    
    // Loot data
    private final Map<String, EntityLootConfig> entityLootConfigs = new ConcurrentHashMap<>();
    private final Set<Material> loadedMaterials = new HashSet<>();
    
    public SpawnerSettingsConfig(SmartSpawner plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "spawners_settings.yml");
    }
    
    /**
     * Load or create the spawners settings configuration
     */
    public void load() {
        // Check and migrate from old config files if needed
        migrateFromOldConfigs();
        
        // Create config file if it doesn't exist
        if (!configFile.exists()) {
            saveDefaultConfig();
        }
        
        // Check and update config version
        checkAndUpdateConfig();
        
        // Load the configuration
        config = YamlConfiguration.loadConfiguration(configFile);
        
        // Parse configuration
        parseConfig();
    }
    
    /**
     * Migrate from old mob_drops.yml and mob_heads.yml if they exist
     */
    private void migrateFromOldConfigs() {
        File oldDropsFile = new File(plugin.getDataFolder(), "mob_drops.yml");
        File oldHeadsFile = new File(plugin.getDataFolder(), "mob_heads.yml");
        
        // If spawners_settings.yml already exists, no migration needed
        if (configFile.exists()) {
            return;
        }
        
        // If neither old file exists, no migration needed
        if (!oldDropsFile.exists() && !oldHeadsFile.exists()) {
            return;
        }
        
        plugin.getLogger().info("Migrating from old mob_drops.yml and mob_heads.yml to spawners_settings.yml...");
        
        try {
            // Load old configs
            FileConfiguration dropsConfig = oldDropsFile.exists() ? 
                YamlConfiguration.loadConfiguration(oldDropsFile) : null;
            FileConfiguration headsConfig = oldHeadsFile.exists() ? 
                YamlConfiguration.loadConfiguration(oldHeadsFile) : null;
            
            // Create new merged config
            FileConfiguration newConfig = new YamlConfiguration();
            newConfig.set(CONFIG_VERSION_KEY, CURRENT_CONFIG_VERSION);
            
            // Set default material
            if (headsConfig != null) {
                newConfig.set("default_material", headsConfig.getString("default_material", "SPAWNER"));
            } else {
                newConfig.set("default_material", "SPAWNER");
            }
            
            // Get all mob types
            Set<String> allMobs = new HashSet<>();
            if (dropsConfig != null) {
                allMobs.addAll(dropsConfig.getKeys(false));
            }
            if (headsConfig != null) {
                ConfigurationSection mobHeadsSection = headsConfig.getConfigurationSection("mob_heads");
                if (mobHeadsSection != null) {
                    allMobs.addAll(mobHeadsSection.getKeys(false));
                }
            }
            
            // Merge data for each mob
            for (String mob : allMobs) {
                // Copy drops data
                if (dropsConfig != null && dropsConfig.contains(mob)) {
                    ConfigurationSection mobSection = dropsConfig.getConfigurationSection(mob);
                    if (mobSection != null) {
                        for (String key : mobSection.getKeys(false)) {
                            newConfig.set(mob + "." + key, mobSection.get(key));
                        }
                    }
                }
                
                // Copy head texture data
                if (headsConfig != null) {
                    ConfigurationSection headSection = headsConfig.getConfigurationSection("mob_heads." + mob);
                    if (headSection != null) {
                        newConfig.set(mob + ".head_texture.material", headSection.getString("material"));
                        newConfig.set(mob + ".head_texture.custom_texture", headSection.getString("custom_texture"));
                    }
                }
            }
            
            // Save the new config
            newConfig.save(configFile);
            
            // Backup old files
            if (oldDropsFile.exists()) {
                File backup = new File(plugin.getDataFolder(), "mob_drops.yml.old");
                Files.move(oldDropsFile.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
                plugin.getLogger().info("Backed up mob_drops.yml to mob_drops.yml.old");
            }
            if (oldHeadsFile.exists()) {
                File backup = new File(plugin.getDataFolder(), "mob_heads.yml.old");
                Files.move(oldHeadsFile.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
                plugin.getLogger().info("Backed up mob_heads.yml to mob_heads.yml.old");
            }
            
            plugin.getLogger().info("Migration completed successfully!");
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to migrate old configs", e);
        }
    }
    
    /**
     * Check if config needs updating and update if necessary
     */
    private void checkAndUpdateConfig() {
        if (!configFile.exists()) {
            return;
        }
        
        FileConfiguration currentConfig = YamlConfiguration.loadConfiguration(configFile);
        String configVersion = currentConfig.getString(CONFIG_VERSION_KEY, "0.0.0");
        
        if (configVersion.equals(CURRENT_CONFIG_VERSION)) {
            return;
        }
        
        plugin.getLogger().info("Updating spawners_settings.yml from version " + configVersion + " to " + CURRENT_CONFIG_VERSION);
        
        try {
            // Create backup
            File backupFile = new File(plugin.getDataFolder(), "spawners_settings_backup_" + configVersion + ".yml");
            Files.copy(configFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            plugin.getLogger().info("Backup created at " + backupFile.getName());
            
            // Get user values
            Map<String, Object> userValues = new HashMap<>();
            for (String key : currentConfig.getKeys(true)) {
                if (!currentConfig.isConfigurationSection(key)) {
                    userValues.put(key, currentConfig.get(key));
                }
            }
            
            // Load default config
            saveDefaultConfig();
            FileConfiguration newConfig = YamlConfiguration.loadConfiguration(configFile);
            newConfig.set(CONFIG_VERSION_KEY, CURRENT_CONFIG_VERSION);
            
            // Apply user values
            for (Map.Entry<String, Object> entry : userValues.entrySet()) {
                String path = entry.getKey();
                if (!path.equals(CONFIG_VERSION_KEY) && newConfig.contains(path)) {
                    newConfig.set(path, entry.getValue());
                }
            }
            
            newConfig.save(configFile);
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to update spawners_settings.yml", e);
        }
    }
    
    /**
     * Save the default configuration from resources
     */
    private void saveDefaultConfig() {
        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            
            try (InputStream in = plugin.getResource("spawners_settings.yml")) {
                if (in != null) {
                    Files.copy(in, configFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    plugin.getLogger().info("Created default spawners_settings.yml configuration");
                } else {
                    plugin.getLogger().warning("Could not find default spawners_settings.yml in resources");
                }
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not create spawners_settings.yml", e);
        }
    }
    
    /**
     * Parse the configuration and populate both mob head and loot data
     */
    private void parseConfig() {
        mobHeadMap.clear();
        entityLootConfigs.clear();
        loadedMaterials.clear();
        
        // Get default material
        String defaultMaterialName = config.getString("default_material", "SPAWNER");
        try {
            defaultMaterial = Material.valueOf(defaultMaterialName.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid default_material in spawners_settings.yml: " + defaultMaterialName + ", using SPAWNER");
            defaultMaterial = Material.SPAWNER;
        }
        
        // Parse each mob's configuration
        for (String entityName : config.getKeys(false)) {
            // Skip special keys
            if (entityName.equals(CONFIG_VERSION_KEY) || entityName.equals("default_material")) {
                continue;
            }
            
            // Validate entity type
            EntityType entityType;
            try {
                entityType = EntityType.valueOf(entityName.toUpperCase());
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Entity type '" + entityName + "' is invalid or not available in server version " + plugin.getServer().getBukkitVersion());
                continue;
            }
            
            ConfigurationSection entitySection = config.getConfigurationSection(entityName);
            if (entitySection == null) continue;
            
            // Parse head texture data
            parseHeadTexture(entityType, entitySection);
            
            // Parse loot data
            parseLootData(entityName, entitySection);
        }
    }
    
    /**
     * Parse head texture configuration for an entity
     */
    private void parseHeadTexture(EntityType entityType, ConfigurationSection entitySection) {
        ConfigurationSection headSection = entitySection.getConfigurationSection("head_texture");
        if (headSection == null) {
            return;
        }
        
        String materialName = headSection.getString("material", "SPAWNER");
        String customTexture = headSection.getString("custom_texture");
        
        // Validate material
        Material material;
        try {
            material = Material.valueOf(materialName.toUpperCase());
            if (!material.isItem()) {
                plugin.getLogger().warning("Material " + materialName + " for " + entityType + " is not an item, using default");
                material = defaultMaterial;
            }
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid material " + materialName + " for " + entityType + ", using default");
            material = defaultMaterial;
        }
        
        // Store mob head data
        mobHeadMap.put(entityType, new MobHeadData(material, customTexture));
    }
    
    /**
     * Parse loot configuration for an entity
     */
    private void parseLootData(String entityName, ConfigurationSection entitySection) {
        int experience = entitySection.getInt("experience", 0);
        List<LootItem> items = new ArrayList<>();
        
        // Cache price manager reference for better performance
        ItemPriceManager priceManager = plugin.getItemPriceManager();
        
        ConfigurationSection lootSection = entitySection.getConfigurationSection("loot");
        if (lootSection != null) {
            for (String itemKey : lootSection.getKeys(false)) {
                ConfigurationSection itemSection = lootSection.getConfigurationSection(itemKey);
                if (itemSection == null) continue;
                
                try {
                    // Get the material
                    Material material;
                    try {
                        material = Material.valueOf(itemKey.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        material = null;
                    }
                    
                    if (material == null) {
                        plugin.getLogger().warning("Material '" + itemKey + "' is not available in server version " +
                                plugin.getServer().getBukkitVersion() + " - skipping for entity " + entityName);
                        continue;
                    }
                    
                    loadedMaterials.add(material);
                    
                    String[] amounts = itemSection.getString("amount", "1-1").split("-");
                    int minAmount = Integer.parseInt(amounts[0]);
                    int maxAmount = Integer.parseInt(amounts.length > 1 ? amounts[1] : amounts[0]);
                    double chance = itemSection.getDouble("chance", 100.0);
                    
                    double sellPrice = 0.0;
                    if (priceManager != null) {
                        sellPrice = priceManager.getPrice(material);
                    }
                    
                    Integer minDurability = null;
                    Integer maxDurability = null;
                    if (itemSection.contains("durability")) {
                        String[] durabilities = itemSection.getString("durability").split("-");
                        minDurability = Integer.parseInt(durabilities[0]);
                        maxDurability = Integer.parseInt(durabilities.length > 1 ? durabilities[1] : durabilities[0]);
                    }
                    
                    PotionType potionType = null;
                    if (material == Material.TIPPED_ARROW && itemSection.contains("potion_type")) {
                        String potionTypeName = itemSection.getString("potion_type");
                        if (potionTypeName != null) {
                            try {
                                potionType = PotionType.valueOf(potionTypeName.toUpperCase());
                            } catch (IllegalArgumentException e) {
                                plugin.getLogger().warning("Invalid potion type '" + potionTypeName +
                                        "' for entity " + entityName);
                                continue;
                            }
                        }
                    }
                    
                    items.add(new LootItem(material, minAmount, maxAmount, chance,
                            minDurability, maxDurability, potionType, sellPrice));
                    
                } catch (Exception e) {
                    plugin.getLogger().warning("Error processing material '" + itemKey + "' for entity " + entityName + ": " + e.getMessage());
                }
            }
        }
        
        entityLootConfigs.put(entityName.toLowerCase(), new EntityLootConfig(experience, items));
    }
    
    // ===== Mob Head Methods =====
    
    /**
     * Get the material for a specific entity type
     */
    public Material getMaterial(EntityType entityType) {
        MobHeadData data = mobHeadMap.get(entityType);
        return data != null ? data.material : defaultMaterial;
    }
    
    /**
     * Get the custom texture for a specific entity type
     */
    public String getCustomTexture(EntityType entityType) {
        MobHeadData data = mobHeadMap.get(entityType);
        return data != null ? data.customTexture : null;
    }
    
    /**
     * Check if an entity type has a custom texture configured
     */
    public boolean hasCustomTexture(EntityType entityType) {
        MobHeadData data = mobHeadMap.get(entityType);
        return data != null && data.customTexture != null && !data.customTexture.isEmpty();
    }
    
    // ===== Loot Methods =====
    
    /**
     * Get loot configuration for an entity type
     */
    public EntityLootConfig getLootConfig(EntityType entityType) {
        if (entityType == null || entityType == EntityType.UNKNOWN) {
            return null;
        }
        return entityLootConfigs.get(entityType.name().toLowerCase());
    }
    
    /**
     * Get all loaded materials
     */
    public Set<Material> getLoadedMaterials() {
        return new HashSet<>(loadedMaterials);
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
