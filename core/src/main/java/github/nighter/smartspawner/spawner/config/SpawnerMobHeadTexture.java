package github.nighter.smartspawner.spawner.config;

import github.nighter.smartspawner.SmartSpawner;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerTextures;
import org.bukkit.profile.PlayerProfile;

import java.net.URL;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public class SpawnerMobHeadTexture {
    private static final Map<EntityType, ItemStack> HEAD_CACHE = new EnumMap<>(EntityType.class);
    private static final ItemStack DEFAULT_SPAWNER_BLOCK = new ItemStack(Material.SPAWNER);

    private static boolean isBedrockPlayer(Player player) {
        SmartSpawner plugin = SmartSpawner.getInstance();
        if (plugin == null || plugin.getIntegrationManager() == null || 
            plugin.getIntegrationManager().getFloodgateHook() == null) {
            return false;
        }
        return plugin.getIntegrationManager().getFloodgateHook().isBedrockPlayer(player);
    }

    public static ItemStack getCustomHead(EntityType entityType, Player player) {
        if (entityType == null) {
            return DEFAULT_SPAWNER_BLOCK;
        }
        
        if (isBedrockPlayer(player)) {
            return DEFAULT_SPAWNER_BLOCK;
        }
        
        return getCustomHead(entityType);
    }

    /**
     * Optimized version that accepts a Consumer to modify the ItemMeta directly,
     * avoiding an extra getItemMeta() and setItemMeta() cycle.
     *
     * @param entityType The entity type for the head
     * @param player The player requesting the head
     * @param metaModifier Consumer to modify the ItemMeta (can be null)
     * @return The configured ItemStack
     */
    public static ItemStack getCustomHead(EntityType entityType, Player player, Consumer<ItemMeta> metaModifier) {
        if (entityType == null) {
            ItemStack item = DEFAULT_SPAWNER_BLOCK.clone();
            if (metaModifier != null) {
                item.editMeta(metaModifier);
            }
            return item;
        }

        if (isBedrockPlayer(player)) {
            ItemStack item = DEFAULT_SPAWNER_BLOCK.clone();
            if (metaModifier != null) {
                item.editMeta(metaModifier);
            }
            return item;
        }

        return getCustomHead(entityType, metaModifier);
    }

    public static ItemStack getCustomHead(EntityType entityType) {
        return getCustomHead(entityType, (Consumer<ItemMeta>) null);
    }

    /**
     * Optimized version that accepts a Consumer to modify the ItemMeta directly,
     * avoiding an extra getItemMeta() and setItemMeta() cycle.
     *
     * @param entityType The entity type for the head
     * @param metaModifier Consumer to modify the ItemMeta (can be null)
     * @return The configured ItemStack
     */
    public static ItemStack getCustomHead(EntityType entityType, Consumer<ItemMeta> metaModifier) {
        if (entityType == null) {
            ItemStack item = DEFAULT_SPAWNER_BLOCK.clone();
            if (metaModifier != null) {
                item.editMeta(metaModifier);
            }
            return item;
        }
        
        SmartSpawner plugin = SmartSpawner.getInstance();
        if (plugin == null) {
            ItemStack item = DEFAULT_SPAWNER_BLOCK.clone();
            if (metaModifier != null) {
                item.editMeta(metaModifier);
            }
            return item;
        }
        
        SpawnerSettingsConfig settingsConfig = plugin.getSpawnerSettingsConfig();
        if (settingsConfig == null) {
            ItemStack item = DEFAULT_SPAWNER_BLOCK.clone();
            if (metaModifier != null) {
                item.editMeta(metaModifier);
            }
            return item;
        }
        
        // Get material from config
        Material material = settingsConfig.getMaterial(entityType);
        
        // If it's not a player head, return the vanilla head
        if (material != Material.PLAYER_HEAD) {
            ItemStack item = new ItemStack(material);
            if (metaModifier != null) {
                item.editMeta(metaModifier);
            }
            return item;
        }
        
        // Check cache for player heads
        if (HEAD_CACHE.containsKey(entityType)) {
            ItemStack item = HEAD_CACHE.get(entityType).clone();
            // Apply meta modifier if provided
            if (metaModifier != null) {
                item.editMeta(metaModifier);
            }
            return item;
        }
        
        // Check if we have a custom texture
        if (!settingsConfig.hasCustomTexture(entityType)) {
            ItemStack item = new ItemStack(material);
            if (metaModifier != null) {
                item.editMeta(metaModifier);
            }
            return item;
        }

        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        try {
            String texture = settingsConfig.getCustomTexture(entityType);
            PlayerProfile profile = Bukkit.createPlayerProfile(UUID.randomUUID());
            PlayerTextures textures = profile.getTextures();
            URL url = new URL("http://textures.minecraft.net/texture/" + texture);
            textures.setSkin(url);
            profile.setTextures(textures);
            meta.setOwnerProfile(profile);

            // Apply meta modifier before setting meta (if provided)
            if (metaModifier != null) {
                metaModifier.accept(meta);
            }

            head.setItemMeta(meta);

            // Only cache the head without custom modifications
            if (metaModifier == null) {
                HEAD_CACHE.put(entityType, head.clone());
            }

            return head;
        } catch (Exception e) {
            e.printStackTrace();
            ItemStack item = new ItemStack(material);
            if (metaModifier != null) {
                item.editMeta(metaModifier);
            }
            return item;
        }
    }

    public static void clearCache() {
        HEAD_CACHE.clear();
    }
}