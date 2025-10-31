package github.nighter.smartspawner.v1_21;

import github.nighter.smartspawner.nms.MaterialWrapper;
import org.bukkit.Material;

import java.util.Arrays;
import java.util.stream.Collectors;

public class MaterialInitializer {
    public static void init() {
        MaterialWrapper.SUPPORTED_MATERIALS = Arrays.stream(Material.values())
                .collect(Collectors.toMap(Material::name, material -> material));

        MaterialWrapper.AVAILABLE_MATERIAL_NAMES = Arrays.stream(Material.values())
                .map(Material::name)
                .collect(Collectors.toSet());
    }
}