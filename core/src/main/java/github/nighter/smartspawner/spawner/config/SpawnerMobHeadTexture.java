package github.nighter.smartspawner.spawner.config;

import github.nighter.smartspawner.SmartSpawner;
import io.papermc.paper.datacomponent.DataComponentType;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.TooltipDisplay;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.keys.DataComponentTypeKeys;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerTextures;
import org.bukkit.profile.PlayerProfile;

import java.net.URL;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class SpawnerMobHeadTexture {
    private static final Map<EntityType, ItemStack> HEAD_CACHE = new EnumMap<>(EntityType.class);
    private static final Set<DataComponentType> HIDDEN_TOOLTIP_COMPONENTS = Set.of(
        RegistryAccess.registryAccess().getRegistry(RegistryKey.DATA_COMPONENT_TYPE).get(DataComponentTypeKeys.BLOCK_ENTITY_DATA)
    );

    private static boolean isBedrockPlayer(Player player) {
        SmartSpawner plugin = SmartSpawner.getInstance();
        if (plugin == null || plugin.getIntegrationManager() == null || 
            plugin.getIntegrationManager().getFloodgateHook() == null) {
            return false;
        }
        return plugin.getIntegrationManager().getFloodgateHook().isBedrockPlayer(player);
    }

    private static void hideTooltip(ItemStack item) {
        item.setData(DataComponentTypes.TOOLTIP_DISPLAY, 
            TooltipDisplay.tooltipDisplay().hiddenComponents(HIDDEN_TOOLTIP_COMPONENTS).build());
    }

    public static ItemStack getCustomHead(EntityType entityType, Player player) {
        if (entityType == null) {
            return createItemStack(Material.SPAWNER);
        }
        
        if (isBedrockPlayer(player)) {
            return new ItemStack(getMaterialForEntity(entityType));
        }
        
        return getCustomHead(entityType);
    }

    public static ItemStack getCustomHead(EntityType entityType) {
        if (entityType == null) {
            return createItemStack(Material.SPAWNER);
        }
        
        SmartSpawner plugin = SmartSpawner.getInstance();
        if (plugin == null) {
            return createItemStack(Material.SPAWNER);
        }
        
        MobHeadConfig mobHeadConfig = plugin.getMobHeadConfig();
        if (mobHeadConfig == null) {
            return createItemStack(Material.SPAWNER);
        }
        
        // Get material from config
        Material material = mobHeadConfig.getMaterial(entityType);
        
        // If it's not a player head, return the vanilla head
        if (material != Material.PLAYER_HEAD) {
            return new ItemStack(material);
        }
        
        // Check cache for player heads
        if (HEAD_CACHE.containsKey(entityType)) {
            return HEAD_CACHE.get(entityType).clone();
        }
        
        // Check if we have a custom texture
        if (!mobHeadConfig.hasCustomTexture(entityType)) {
            return new ItemStack(material);
        }
        
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        try {
            String texture = mobHeadConfig.getCustomTexture(entityType);
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
            return createItemStack(material);
        }
    }

    private static Material getMaterialForEntity(EntityType entityType) {
        SmartSpawner plugin = SmartSpawner.getInstance();
        if (plugin != null && plugin.getMobHeadConfig() != null) {
            return plugin.getMobHeadConfig().getMaterial(entityType);
        }
        return Material.SPAWNER;
    }

    public static ItemStack createItemStack(Material material) {
        ItemStack itemStack = new ItemStack(material);
        ItemMeta meta = itemStack.getItemMeta();
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
        itemStack.setItemMeta(meta);
        hideTooltip(itemStack);
        return itemStack;
    }

    public static void clearCache() {
        HEAD_CACHE.clear();
    }
}