package github.nighter.smartspawner.spawner.utils;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.nms.TextureWrapper;
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

    static {
        TextureWrapper.initializeCommonTextures();
    }

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
        switch (entityType) {
            case ZOMBIE:
                return new ItemStack(Material.ZOMBIE_HEAD);
            case SKELETON:
                return new ItemStack(Material.SKELETON_SKULL);
            case WITHER_SKELETON:
                return new ItemStack(Material.WITHER_SKELETON_SKULL);
            case CREEPER:
                return new ItemStack(Material.CREEPER_HEAD);
            case PIGLIN, PIGLIN_BRUTE:
                return new ItemStack(Material.PIGLIN_HEAD);
        }
        if (isBedrockPlayer(player)) {
            return new ItemStack(Material.SPAWNER);
        }
        if (HEAD_CACHE.containsKey(entityType)) {
            return HEAD_CACHE.get(entityType).clone();
        }
        if (!TextureWrapper.hasTexture(entityType)) {
            return new ItemStack(Material.SPAWNER);
        }
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        try {
            String texture = TextureWrapper.getTexture(entityType);
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
            return createItemStack(Material.SPAWNER);
        }
    }

    public static ItemStack getCustomHead(EntityType entityType) {
        if (entityType == null) {
            return createItemStack(Material.SPAWNER);
        }
        switch (entityType) {
            case ZOMBIE:
                return new ItemStack(Material.ZOMBIE_HEAD);
            case SKELETON:
                return new ItemStack(Material.SKELETON_SKULL);
            case WITHER_SKELETON:
                return new ItemStack(Material.WITHER_SKELETON_SKULL);
            case CREEPER:
                return new ItemStack(Material.CREEPER_HEAD);
            case PIGLIN, PIGLIN_BRUTE:
                return new ItemStack(Material.PIGLIN_HEAD);
        }
        if (HEAD_CACHE.containsKey(entityType)) {
            return HEAD_CACHE.get(entityType).clone();
        }
        if (!TextureWrapper.hasTexture(entityType)) {
            return new ItemStack(Material.SPAWNER);
        }
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        try {
            String texture = TextureWrapper.getTexture(entityType);
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
            return createItemStack(Material.SPAWNER);
        }
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