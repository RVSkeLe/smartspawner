package github.nighter.smartspawner.spawner.loot;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.hooks.economy.ItemPriceManager;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionType;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class EntityLootRegistry {
    private final SmartSpawner plugin;
    private FileConfiguration lootConfig;
    private final Map<String, EntityLootConfig> entityLootConfigs;
    private ItemPriceManager priceManager;
    private final Map<Material, Double> cachedPrices;
    private final Set<Material> loadedMaterials;

    public EntityLootRegistry(SmartSpawner plugin, ItemPriceManager priceManager) {
        this.plugin = plugin;
        this.entityLootConfigs = new ConcurrentHashMap<>();
        this.priceManager = priceManager;
        this.cachedPrices = new ConcurrentHashMap<>();
        this.loadedMaterials = new HashSet<>();
        setupLootConfigFile();
        loadConfigurations();
    }

    protected void setupLootConfigFile() {
        File lootConfigFile = new File(plugin.getDataFolder(), "mob_drops.yml");
        if (!lootConfigFile.exists()) {
            plugin.saveResource("mob_drops.yml", false);
        }
        lootConfig = YamlConfiguration.loadConfiguration(lootConfigFile);
    }

    public void loadConfigurations() {
        entityLootConfigs.clear();
        cachedPrices.clear();
        loadedMaterials.clear();

        for (String entityName : lootConfig.getKeys(false)) {
            if (entityName.startsWith("#")) {
                continue;
            }

            // Validate entity type exists in current version
            EntityType entityType;
            try {
                entityType = EntityType.valueOf(entityName.toUpperCase());
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Skip entity type '" + entityName + "' is invalid in mob_heads.yml or not available in server version " + plugin.getServer().getBukkitVersion());
                continue;
            }

            ConfigurationSection entitySection = lootConfig.getConfigurationSection(entityName);
            if (entitySection == null) continue;

            int experience = entitySection.getInt("experience", 0);
            List<LootItem> items = new ArrayList<>();

            ConfigurationSection lootSection = entitySection.getConfigurationSection("loot");
            if (lootSection != null) {
                for (String itemKey : lootSection.getKeys(false)) {
                    ConfigurationSection itemSection = lootSection.getConfigurationSection(itemKey);
                    if (itemSection == null) continue;

                    try {
                        // Try to get the material by name
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

                        double sellPrice = getSellPrice(material);

                        Integer minDurability = null;
                        Integer maxDurability = null;
                        if (itemSection.contains("durability")) {
                            String[] durabilities = itemSection.getString("durability").split("-");
                            minDurability = Integer.parseInt(durabilities[0]);
                            maxDurability = Integer.parseInt(durabilities.length > 1 ? durabilities[1] : durabilities[0]);
                        }

                        PotionType potionType = null;

                        // Modern potion handling - only for tipped arrows
                        if (material == Material.TIPPED_ARROW && itemSection.contains("potion_type")) {
                            String potionTypeName = itemSection.getString("potion_type");
                            if (potionTypeName != null) {
                                try {
                                    potionType = PotionType.valueOf(potionTypeName.toUpperCase());
                                } catch (IllegalArgumentException e) {
                                    plugin.getLogger().warning("Invalid potion type '" + potionTypeName +
                                            "' for entity " + entityName + ". Available types: " +
                                            java.util.Arrays.toString(PotionType.values()));
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

        priceManager.debugPricesForMaterials(loadedMaterials);
    }

    private double getSellPrice(Material material) {
        return cachedPrices.computeIfAbsent(material, priceManager::getPrice);
    }

    public EntityLootConfig getLootConfig(EntityType entityType) {
        if (entityType == null || entityType == EntityType.UNKNOWN) {
            return null;
        }
        return entityLootConfigs.get(entityType.name().toLowerCase());
    }

    public Set<Material> getLoadedMaterials() {
        return new HashSet<>(loadedMaterials);
    }

    public void reload() {
        this.priceManager = plugin.getItemPriceManager();
        setupLootConfigFile();
        loadConfigurations();
    }
}