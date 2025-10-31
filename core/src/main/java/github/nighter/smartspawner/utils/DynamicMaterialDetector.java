package github.nighter.smartspawner.utils;

import org.bukkit.Material;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Dynamically detects valid materials for spawner GUI based on current Minecraft version.
 * Supports automatic detection of:
 * - Player heads
 * - Mob skulls/heads
 * - Spawner blocks
 */
public class DynamicMaterialDetector {
    private static final Set<Material> SPAWNER_INFO_MATERIALS;
    
    static {
        // Auto-detect all head/skull materials from current version
        SPAWNER_INFO_MATERIALS = Arrays.stream(Material.values())
            .filter(material -> {
                String name = material.name();
                return name.equals("PLAYER_HEAD") ||
                       name.equals("SPAWNER") ||
                       name.endsWith("_HEAD") ||
                       name.endsWith("_SKULL");
            })
            .filter(Material::isItem)
            .collect(Collectors.toUnmodifiableSet());
    }
    
    /**
     * Get all valid spawner info materials for the current Minecraft version
     * 
     * @return Unmodifiable set of spawner info materials
     */
    public static Set<Material> getSpawnerInfoMaterials() {
        return SPAWNER_INFO_MATERIALS;
    }
    
    /**
     * Check if a material is a valid spawner info material
     * 
     * @param material Material to check
     * @return true if the material is a valid spawner info material
     */
    public static boolean isValidSpawnerInfoMaterial(Material material) {
        return SPAWNER_INFO_MATERIALS.contains(material);
    }
}
