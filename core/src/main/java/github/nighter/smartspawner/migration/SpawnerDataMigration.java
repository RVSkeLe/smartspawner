package github.nighter.smartspawner.migration;

import github.nighter.smartspawner.SmartSpawner;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class SpawnerDataMigration {
    private final SmartSpawner plugin;
    private final File dataFolder;
    private static final String DATA_FILE = "spawners_data.yml";
    private static final String BACKUP_FILE = "spawners_data_backup.yml";
    private static final String MIGRATION_FLAG = "data_version";
    private final int CURRENT_VERSION;

    public SpawnerDataMigration(SmartSpawner plugin) {
        this.plugin = plugin;
        this.dataFolder = plugin.getDataFolder();
        this.CURRENT_VERSION = plugin.getDATA_VERSION();
    }

    public boolean checkAndMigrateData() {
        File dataFile = new File(dataFolder, DATA_FILE);

        if (!dataFile.exists()) {
            plugin.getLogger().info("Data file does not exist. No migration needed.");
            return false;
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(dataFile);

        // First, try to validate if the current format works
        boolean needsMigration;
        try {
            needsMigration = false;

            // Check if data_version exists and compare with current version
            int dataVersion = config.getInt(MIGRATION_FLAG, 0);
            
            if (dataVersion == 0) {
                // data_version doesn't exist, check the structure to determine if migration is needed
                plugin.getLogger().info("No data_version found. Checking data structure...");
                needsMigration = true;
            } else if (dataVersion < CURRENT_VERSION) {
                // data_version exists but is outdated
                plugin.getLogger().info("Data version " + dataVersion + " is outdated. Current version is " + CURRENT_VERSION + ".");
                needsMigration = true;
            }

            // If version check indicates migration is needed, validate by checking structure
            if (needsMigration && config.contains("spawners")) {
                // Double-check by validating the spawners section structure
                boolean hasNewFormat = true;
                ConfigurationSection spawnersSection = config.getConfigurationSection("spawners");
                if (spawnersSection != null) {
                    for (String spawnerId : spawnersSection.getKeys(false)) {
                        String spawnerPath = "spawners." + spawnerId;
                        // Check if the spawner data is in the new format
                        if (!config.contains(spawnerPath + ".location") ||
                                !config.contains(spawnerPath + ".settings") ||
                                !config.contains(spawnerPath + ".inventory")) {
                            hasNewFormat = false;
                            break;
                        }
                        
                        // Also check if settings string has the correct number of fields for version 3
                        String settingsString = config.getString(spawnerPath + ".settings");
                        if (settingsString != null) {
                            String[] settings = settingsString.split(",");
                            // Version 3 requires 13 fields (including maxStackSize and isAtCapacity)
                            if (settings.length < 13) {
                                hasNewFormat = false;
                                break;
                            }
                        }
                    }
                }
                
                // If structure is already in new format, just update version
                if (hasNewFormat) {
                    plugin.getLogger().info("Data structure is already in current format. Updating version flag...");
                    config.set(MIGRATION_FLAG, CURRENT_VERSION);
                    try {
                        config.save(dataFile);
                    } catch (IOException e) {
                        plugin.getLogger().warning("Could not save data_version flag: " + e.getMessage());
                    }
                    needsMigration = false;
                }
            }

            if (!needsMigration) {
                //plugin.getLogger().info("Data format is up to date.");
                return false;
            }

        } catch (Exception e) {
            plugin.getLogger().warning("Error validating current data format: " + e.getMessage());
            needsMigration = true;
        }

        // If we reach here, we need to migrate the data
        plugin.getLogger().info("Starting data migration process...");

        try {
            if (!createBackup(dataFile)) {
                plugin.getLogger().severe("Failed to create backup. Migration aborted.");
                return false;
            }

            boolean success = migrateData(config, dataFile);

            if (success) {
                return true;
            } else {
                restoreFromBackup(dataFile);
                return false;
            }

        } catch (Exception e) {
            plugin.getLogger().severe("Error during data migration: " + e.getMessage());
            e.printStackTrace();
            restoreFromBackup(dataFile);
            return false;
        }
    }

    private boolean createBackup(File sourceFile) {
        try {
            File backupFile = new File(dataFolder, BACKUP_FILE);
            Files.copy(sourceFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            plugin.getLogger().info("Backup created successfully at: " + backupFile.getPath());
            return true;
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to create backup: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private void restoreFromBackup(File dataFile) {
        try {
            File backupFile = new File(dataFolder, BACKUP_FILE);
            if (backupFile.exists()) {
                Files.copy(backupFile.toPath(), dataFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                plugin.getLogger().info("Data restored from backup.");
            }
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to restore from backup: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private boolean migrateData(FileConfiguration oldConfig, File dataFile) {
        try {
            int oldVersion = oldConfig.getInt(MIGRATION_FLAG, 1);
            
            // Check if this is a version 2 to version 3 migration
            if (oldVersion == 2 && oldConfig.contains("spawners")) {
                plugin.getLogger().info("Migrating from version 2 to version 3...");
                return migrateVersion2ToVersion3(oldConfig, dataFile);
            }
            
            // Otherwise, handle old format to new format migration
            // Create new data file
            FileConfiguration newConfig = new YamlConfiguration();

            // Copy data version flag
            newConfig.set(MIGRATION_FLAG, CURRENT_VERSION);

            // Convert old data to new format
            SpawnerDataConverter converter = new SpawnerDataConverter(plugin, oldConfig, newConfig);
            converter.convertData();

            // Save new data to file
            newConfig.save(dataFile);

            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to migrate data: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    private boolean migrateVersion2ToVersion3(FileConfiguration config, File dataFile) {
        try {
            ConfigurationSection spawnersSection = config.getConfigurationSection("spawners");
            if (spawnersSection == null) {
                plugin.getLogger().warning("No spawners section found in version 2 data");
                return false;
            }
            
            int migratedCount = 0;
            for (String spawnerId : spawnersSection.getKeys(false)) {
                String settingsPath = "spawners." + spawnerId + ".settings";
                String settingsString = config.getString(settingsPath);
                
                if (settingsString != null) {
                    String[] settings = settingsString.split(",");
                    
                    // Version 2 has 11 fields, version 3 has 13 fields
                    if (settings.length == 11) {
                        // Migrate version 2 (11 fields) to version 3 (13 fields)
                        // Version 2: exp,active,range,stop,delay,maxLoot,maxExp,minMobs,maxMobs,stack,lastSpawnTime
                        // Version 3: exp,active,range,stop,delay,maxLoot,maxExp,minMobs,maxMobs,stack,maxStack,lastSpawnTime,isAtCapacity
                        
                        // Get the default maxStackSize from config
                        int defaultMaxStackSize = plugin.getConfig().getInt("spawner.max_stack_size", 10);
                        
                        // Build new settings string with maxStackSize inserted at position 10
                        String newSettings = String.format("%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%d,%s,%b",
                                settings[0],  // spawnerExp
                                settings[1],  // spawnerActive
                                settings[2],  // spawnerRange
                                settings[3],  // spawnerStop
                                settings[4],  // spawnDelay
                                settings[5],  // maxSpawnerLootSlots
                                settings[6],  // maxStoredExp
                                settings[7],  // minMobs
                                settings[8],  // maxMobs
                                settings[9],  // stackSize
                                defaultMaxStackSize,  // maxStackSize (NEW)
                                settings[10], // lastSpawnTime
                                false         // isAtCapacity (NEW)
                        );
                        
                        config.set(settingsPath, newSettings);
                        migratedCount++;
                    }
                }
            }
            
            // Update version flag
            config.set(MIGRATION_FLAG, CURRENT_VERSION);
            
            // Save the updated config
            config.save(dataFile);
            
            plugin.getLogger().info("Successfully migrated " + migratedCount + " spawners from version 2 to version 3");
            return true;
            
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to migrate from version 2 to version 3: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}