package github.nighter.smartspawner.nms;

import org.bukkit.Material;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Wrapper for dynamic Material detection across Minecraft versions.
 * Automatically detects and provides all available materials in the current version.
 */
public class MaterialWrapper {
    public static final Map<String, Material> SUPPORTED_MATERIALS;
    public static final Set<String> AVAILABLE_MATERIAL_NAMES;

    static {
        // Dynamically load all materials from the current Minecraft version
        SUPPORTED_MATERIALS = Arrays.stream(Material.values())
                .collect(Collectors.toMap(Material::name, material -> material));

        AVAILABLE_MATERIAL_NAMES = Arrays.stream(Material.values())
                .map(Material::name)
                .collect(Collectors.toSet());
    }

    public static Material getMaterial(String materialName) {
        if (SUPPORTED_MATERIALS == null) {
            return null;
        }
        return SUPPORTED_MATERIALS.get(materialName.toUpperCase());
    }

    public static boolean isMaterialAvailable(String materialName) {
        if (AVAILABLE_MATERIAL_NAMES == null) {
            return false;
        }
        return AVAILABLE_MATERIAL_NAMES.contains(materialName.toUpperCase());
    }
}