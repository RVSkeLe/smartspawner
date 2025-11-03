package github.nighter.smartspawner.spawner.config;

import github.nighter.smartspawner.SmartSpawner;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerTextures;
import org.bukkit.profile.PlayerProfile;

import java.net.URL;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

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

    public static ItemStack getCustomHead(EntityType entityType) {
        if (entityType == null) {
            return DEFAULT_SPAWNER_BLOCK;
        }
        
        SmartSpawner plugin = SmartSpawner.getInstance();
        if (plugin == null) {
            return DEFAULT_SPAWNER_BLOCK;
        }
        
        SpawnerSettingsConfig settingsConfig = plugin.getSpawnerSettingsConfig();
        if (settingsConfig == null) {
            return DEFAULT_SPAWNER_BLOCK;
        }
        
        // Get material from config
        Material material = settingsConfig.getMaterial(entityType);
        
        // If it's not a player head, return the vanilla head
        if (material != Material.PLAYER_HEAD) {
            return new ItemStack(material);
        }
        
        // Check cache for player heads
        if (HEAD_CACHE.containsKey(entityType)) {
            return HEAD_CACHE.get(entityType).clone();
        }
        
        // Check if we have a custom texture
        if (!settingsConfig.hasCustomTexture(entityType)) {
            return new ItemStack(material);
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
            head.setItemMeta(meta);
            HEAD_CACHE.put(entityType, head.clone());
            return head;
        } catch (Exception e) {
            e.printStackTrace();
            return new ItemStack(material);
        }
    }

    public static void clearCache() {
        HEAD_CACHE.clear();
    }
}